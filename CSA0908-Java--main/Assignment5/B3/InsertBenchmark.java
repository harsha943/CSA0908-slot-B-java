import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * B3 - Statement vs PreparedStatement vs batched PreparedStatement,
 * correctness check with ResultSetMetaData, and transaction rollback demo.
 * Usage: java -cp .;mysql-connector-j.jar InsertBenchmark jdbc:mysql://localhost:3306/transport_test?rewriteBatchedStatements=true USER PASSWORD
 */
public class InsertBenchmark {

    static List<String[]> generate(int n, String prefix) {
        List<String[]> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++)
            out.add(new String[]{prefix + i, "1923" + String.format("%05d", i % 100000),
                    "Student " + i, String.valueOf(i % 20), String.valueOf(3000 + i % 2000)});
        return out;
    }

    static void clear(Connection c) throws SQLException {
        try (Statement s = c.createStatement()) { s.executeUpdate("DELETE FROM bus_pass"); }
    }

    static int count(Connection c) throws SQLException {
        try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM bus_pass")) {
            rs.next(); return rs.getInt(1);
        }
    }

    static long viaStatement(Connection c, List<String[]> rows) throws SQLException {
        long t0 = System.nanoTime();
        try (Statement s = c.createStatement()) {
            for (String[] p : rows)
                s.executeUpdate("INSERT INTO bus_pass VALUES ('" + p[0] + "','" + p[1] + "','" + p[2]
                        + "'," + p[3] + "," + p[4] + ")");
        }
        return (System.nanoTime() - t0) / 1_000_000;
    }

    static long viaPrepared(Connection c, List<String[]> rows) throws SQLException {
        long t0 = System.nanoTime();
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO bus_pass VALUES (?,?,?,?,?)")) {
            for (String[] p : rows) {
                ps.setString(1, p[0]); ps.setString(2, p[1]); ps.setString(3, p[2]);
                ps.setInt(4, Integer.parseInt(p[3])); ps.setDouble(5, Double.parseDouble(p[4]));
                ps.executeUpdate();
            }
        }
        return (System.nanoTime() - t0) / 1_000_000;
    }

    static long viaBatch(Connection c, List<String[]> rows) throws SQLException {
        long t0 = System.nanoTime();
        PassLoader.loadAll(c, rows);
        return (System.nanoTime() - t0) / 1_000_000;
    }

    public static void main(String[] args) throws SQLException {
        if (args.length != 3) { System.out.println("Usage: java InsertBenchmark <jdbcUrl> <user> <password>"); return; }
        try (Connection c = DriverManager.getConnection(args[0], args[1], args[2])) {

            // B3.1 correctness on 10 rows + metadata
            clear(c);
            viaBatch(c, generate(10, "S"));
            try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery("SELECT * FROM bus_pass ORDER BY pass_id")) {
                ResultSetMetaData md = rs.getMetaData();
                for (int i = 1; i <= md.getColumnCount(); i++)
                    System.out.printf("%-10s %-10s%n", md.getColumnName(i), md.getColumnTypeName(i));
                while (rs.next()) System.out.println("  " + rs.getString(1) + " " + rs.getString(3) + " " + rs.getBigDecimal(5));
            }

            // B3.2 timing
            System.out.printf("%n%8s %12s %12s %12s%n", "rows", "Statement", "Prepared", "Batch");
            for (int n : new int[]{1_000, 10_000, 100_000}) {
                List<String[]> rows = generate(n, "P");
                clear(c); long a = n > 10_000 && args[0].contains("skipSlow") ? -1 : viaStatement(c, rows);
                clear(c); long b = viaPrepared(c, rows);
                clear(c); long d = viaBatch(c, rows);
                System.out.printf("%,8d %10d ms %10d ms %10d ms%n", n, a, b, d);
            }

            // B3.3 transaction: invalid amount half-way -> nothing remains
            clear(c);
            List<String[]> bad = generate(2_000, "T");
            bad.set(1_000, new String[]{"T1000", "192300000", "Bad", "5", "NOT_A_NUMBER"});
            try {
                PassLoader.loadAll(c, bad);
            } catch (RuntimeException e) {
                System.out.println("\nLoad failed half-way (" + e.getClass().getSimpleName() + "), rows in table after rollback: " + count(c));
            }
        }
    }
}
