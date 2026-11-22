import java.sql.*;

/**
 * B2 - Unsafe (string-concatenated) vs PreparedStatement login on a LOCAL TEST database.
 * Usage: java -cp .;mysql-connector-j.jar LoginCheck jdbc:mysql://localhost:3306/transport_test USER PASSWORD
 */
public class LoginCheck {

    static boolean unsafeLogin(Connection c, String id, String pwd) throws SQLException {
        String sql = "SELECT * FROM users WHERE id='" + id + "' AND pwd='" + pwd + "'";
        System.out.println("    SQL sent: " + sql);
        try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery(sql)) {
            return rs.next();
        }
    }

    static boolean safeLogin(Connection c, String id, String pwd) throws SQLException {
        String sql = "SELECT 1 FROM users WHERE id = ? AND pwd = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, id);          // sent as data, never parsed as SQL
            ps.setString(2, pwd);
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        }
    }

    public static void main(String[] args) throws SQLException {
        if (args.length != 3) {
            System.out.println("Usage: java LoginCheck <jdbcUrl> <user> <password>");
            return;
        }
        String[][] inputs = {
                {"clerk1", "secret123", "valid login"},
                {"clerk1", "wrong", "wrong password"},
                {"clerk1", "' OR '1'='1", "tautology in password"},
                {"admin' -- ", "anything", "comment out password check"},
        };
        try (Connection c = DriverManager.getConnection(args[0], args[1], args[2])) {
            System.out.printf("%-28s %-10s %-10s%n", "Input", "Unsafe", "Prepared");
            for (String[] in : inputs) {
                System.out.println("  " + in[2] + ": id=[" + in[0] + "] pwd=[" + in[1] + "]");
                boolean u;
                try { u = unsafeLogin(c, in[0], in[1]); } catch (SQLException e) { u = false; }
                boolean p = safeLogin(c, in[0], in[1]);
                System.out.printf("%-28s %-10s %-10s%n", in[2], u ? "ACCEPTED" : "rejected", p ? "ACCEPTED" : "rejected");
            }
        }
    }
}
