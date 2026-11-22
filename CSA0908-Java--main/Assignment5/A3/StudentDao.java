import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A3 - Generic data-access pattern: Dao<T, K> + an abstract JDBC template that holds the
 * repeated steps (open connection, prepare, execute, map ResultSet, close).
 *
 * Object-table mapping:
 *   Student.regNo   -> student.reg_no   CHAR(9) PRIMARY KEY
 *   Student.name    -> student.name     VARCHAR(60)
 *   Student.dept    -> student.dept     VARCHAR(30)
 *   Student.cgpa    -> student.cgpa     DECIMAL(4,2)
 */
interface Dao<T, K> {
    void insert(T t) throws SQLException;
    Optional<T> findById(K id) throws SQLException;
    List<T> findAll() throws SQLException;
    boolean update(T t) throws SQLException;
    boolean delete(K id) throws SQLException;
}

/** The common steps live here once; subclasses supply only what differs. */
abstract class JdbcDao<T, K> implements Dao<T, K> {
    interface Binder { void bind(PreparedStatement ps) throws SQLException; }

    private final String url, user, pwd;
    protected JdbcDao(String url, String user, String pwd) { this.url = url; this.user = user; this.pwd = pwd; }

    protected abstract T map(ResultSet rs) throws SQLException;        // differs per table

    protected int execute(String sql, Binder b) throws SQLException {
        try (Connection c = DriverManager.getConnection(url, user, pwd);
             PreparedStatement ps = c.prepareStatement(sql)) {
            b.bind(ps);
            return ps.executeUpdate();
        }
    }

    protected List<T> query(String sql, Binder b) throws SQLException {
        List<T> out = new ArrayList<>();
        try (Connection c = DriverManager.getConnection(url, user, pwd);
             PreparedStatement ps = c.prepareStatement(sql)) {
            b.bind(ps);
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) out.add(map(rs)); }
        }
        return out;
    }
}

record Student(String regNo, String name, String dept, double cgpa) {}

public class StudentDao extends JdbcDao<Student, String> {
    public StudentDao(String url, String user, String pwd) { super(url, user, pwd); }

    @Override protected Student map(ResultSet rs) throws SQLException {
        return new Student(rs.getString("reg_no"), rs.getString("name"), rs.getString("dept"), rs.getDouble("cgpa"));
    }

    @Override public void insert(Student s) throws SQLException {
        execute("INSERT INTO student(reg_no, name, dept, cgpa) VALUES (?,?,?,?)", ps -> {
            ps.setString(1, s.regNo()); ps.setString(2, s.name()); ps.setString(3, s.dept()); ps.setDouble(4, s.cgpa());
        });
    }

    @Override public Optional<Student> findById(String id) throws SQLException {
        return query("SELECT * FROM student WHERE reg_no = ?", ps -> ps.setString(1, id)).stream().findFirst();
    }

    @Override public List<Student> findAll() throws SQLException {
        return query("SELECT * FROM student ORDER BY reg_no", ps -> {});
    }

    @Override public boolean update(Student s) throws SQLException {
        return execute("UPDATE student SET name=?, dept=?, cgpa=? WHERE reg_no=?", ps -> {
            ps.setString(1, s.name()); ps.setString(2, s.dept()); ps.setDouble(3, s.cgpa()); ps.setString(4, s.regNo());
        }) == 1;
    }

    @Override public boolean delete(String id) throws SQLException {
        return execute("DELETE FROM student WHERE reg_no=?", ps -> ps.setString(1, id)) == 1;
    }

    /** Usage: java -cp .;mysql-connector-j.jar StudentDao jdbcUrl user password
     *  (needs: CREATE TABLE student(reg_no CHAR(9) PRIMARY KEY, name VARCHAR(60), dept VARCHAR(30), cgpa DECIMAL(4,2))) */
    public static void main(String[] args) throws SQLException {
        if (args.length != 3) { System.out.println("Usage: java StudentDao <jdbcUrl> <user> <password>"); return; }
        Dao<Student, String> dao = new StudentDao(args[0], args[1], args[2]);
        dao.delete("192311001");
        dao.insert(new Student("192311001", "Arun", "CSE", 8.4));
        System.out.println("findById : " + dao.findById("192311001"));
        dao.update(new Student("192311001", "Arun K", "CSE", 8.9));
        System.out.println("after upd: " + dao.findById("192311001"));
        System.out.println("findAll  : " + dao.findAll());
        System.out.println("deleted  : " + dao.delete("192311001"));
    }
}
