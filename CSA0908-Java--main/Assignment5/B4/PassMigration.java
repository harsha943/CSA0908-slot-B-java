import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.*;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * B4 - Migrate the existing pass file into the database in batches inside one transaction,
 * reporting lines that fail validation, then inspect the database with DatabaseMetaData.
 * Usage: java -cp .;mysql-connector-j.jar PassMigration passes.csv jdbc:mysql://localhost:3306/transport_test USER PASSWORD
 * File format: passId,regNo,name,route,amount
 */
public class PassMigration {

    public static void main(String[] args) throws Exception {
        if (args.length != 4) {
            System.out.println("Usage: java PassMigration <passFile> <jdbcUrl> <user> <password>");
            return;
        }
        Path file = Path.of(args[0]);
        List<String[]> valid = new ArrayList<>();
        List<String> failed = new ArrayList<>();

        try (BufferedReader r = Files.newBufferedReader(file)) {
            String line; int n = 0;
            while ((line = r.readLine()) != null) {
                n++;
                String reason = validate(line);
                if (reason == null) valid.add(line.split(",", -1));
                else failed.add("line " + n + " [" + line + "] -> " + reason);
            }
        } catch (NoSuchFileException e) {
            System.out.println("Pass file not found: " + file);
            return;
        }

        try (Connection c = DriverManager.getConnection(args[1], args[2], args[3])) {
            long t0 = System.nanoTime();
            int loaded = loadInBatches(c, valid, 1000);
            System.out.printf("Migrated %,d records in %d ms; %d line(s) failed validation%n",
                    loaded, (System.nanoTime() - t0) / 1_000_000, failed.size());
            failed.forEach(f -> System.out.println("  FAILED " + f));

            DatabaseMetaData md = c.getMetaData();
            System.out.println("\nDatabase product : " + md.getDatabaseProductName() + " " + md.getDatabaseProductVersion());
            System.out.println("Driver           : " + md.getDriverName() + " " + md.getDriverVersion());
            System.out.println("JDBC version     : " + md.getJDBCMajorVersion() + "." + md.getJDBCMinorVersion());
            System.out.println("Supports batch   : " + md.supportsBatchUpdates());
            System.out.println("Tables:");
            try (ResultSet rs = md.getTables(c.getCatalog(), null, "%", new String[]{"TABLE"})) {
                while (rs.next()) System.out.println("  " + rs.getString("TABLE_NAME"));
            }
        }
    }

    static String validate(String line) {
        if (line.isBlank()) return "empty line";
        String[] f = line.split(",", -1);
        if (f.length != 5) return "expected 5 fields, found " + f.length;
        if (!f[0].matches("P\\d+")) return "bad pass id";
        if (!f[1].matches("\\d{9}")) return "register number must be 9 digits";
        if (f[2].isBlank()) return "missing name";
        try { Integer.parseInt(f[3]); } catch (NumberFormatException e) { return "route not numeric"; }
        try { if (Double.parseDouble(f[4]) < 0) return "negative amount"; }
        catch (NumberFormatException e) { return "amount not numeric"; }
        return null;
    }

    static int loadInBatches(Connection c, List<String[]> rows, int batchSize) throws SQLException {
        String sql = "INSERT INTO bus_pass(pass_id, reg_no, name, route_no, amount) VALUES (?,?,?,?,?) "
                + "ON DUPLICATE KEY UPDATE reg_no=VALUES(reg_no), name=VALUES(name), "
                + "route_no=VALUES(route_no), amount=VALUES(amount)";
        boolean old = c.getAutoCommit();
        c.setAutoCommit(false);
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            int n = 0;
            for (String[] p : rows) {
                ps.setString(1, p[0]); ps.setString(2, p[1]); ps.setString(3, p[2]);
                ps.setInt(4, Integer.parseInt(p[3])); ps.setDouble(5, Double.parseDouble(p[4]));
                ps.addBatch();
                if (++n % batchSize == 0) ps.executeBatch();
            }
            ps.executeBatch();
            c.commit();
            return n;
        } catch (SQLException | RuntimeException e) {
            c.rollback();
            throw e;
        } finally {
            c.setAutoCommit(old);
        }
    }
}
