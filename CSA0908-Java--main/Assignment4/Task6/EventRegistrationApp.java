import java.awt.*;
import java.io.*;
import java.sql.*;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;

/**
 * Task 6 - Campus Event Registration desktop application.
 * Layers: UI (Swing) -> RegistrationService (validation, duplicates) -> RegistrationDao (JDBC).
 * Database work runs in SwingWorker so the window stays responsive.
 *
 * Run with MySQL (driver jar on classpath, schema from event_registration.sql):
 *   java -cp .;mysql-connector-j.jar EventRegistrationApp jdbc:mysql://localhost:3306/campus_events USER PASSWORD
 * Run without arguments to use an in-memory store (for a quick demo).
 */
public class EventRegistrationApp {

    // ================= model =================
    record Registration(String regNo, String name, String email, String event) {}

    // ================= data-access layer =================
    interface RegistrationDao {
        List<String> events() throws SQLException;
        boolean exists(String regNo, String event) throws SQLException;
        void insert(Registration r) throws SQLException;
        List<Registration> find(String eventOrNull, int limit, int offset) throws SQLException;
    }

    static class JdbcRegistrationDao implements RegistrationDao {
        private final String url, user, pwd;
        JdbcRegistrationDao(String url, String user, String pwd) { this.url = url; this.user = user; this.pwd = pwd; }
        private Connection open() throws SQLException { return DriverManager.getConnection(url, user, pwd); }

        public List<String> events() throws SQLException {
            List<String> out = new ArrayList<>();
            try (Connection c = open(); Statement s = c.createStatement();
                 ResultSet rs = s.executeQuery("SELECT name FROM events ORDER BY name")) {
                while (rs.next()) out.add(rs.getString(1));
            }
            return out;
        }

        public boolean exists(String regNo, String event) throws SQLException {
            String sql = "SELECT 1 FROM registrations r JOIN events e ON r.event_id = e.event_id "
                    + "WHERE r.student_reg = ? AND e.name = ?";
            try (Connection c = open(); PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, regNo); ps.setString(2, event);
                try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
            }
        }

        public void insert(Registration r) throws SQLException {
            String sql = "INSERT INTO registrations(student_reg, student_name, email, event_id) "
                    + "SELECT ?, ?, ?, event_id FROM events WHERE name = ?";
            try (Connection c = open(); PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, r.regNo()); ps.setString(2, r.name());
                ps.setString(3, r.email()); ps.setString(4, r.event());
                ps.executeUpdate();             // UNIQUE(student_reg, event_id) is the final guard
            }
        }

        public List<Registration> find(String event, int limit, int offset) throws SQLException {
            String sql = "SELECT r.student_reg, r.student_name, r.email, e.name FROM registrations r "
                    + "JOIN events e ON r.event_id = e.event_id "
                    + (event == null ? "" : "WHERE e.name = ? ")
                    + "ORDER BY r.reg_id LIMIT ? OFFSET ?";
            List<Registration> out = new ArrayList<>();
            try (Connection c = open(); PreparedStatement ps = c.prepareStatement(sql)) {
                int i = 1;
                if (event != null) ps.setString(i++, event);
                ps.setInt(i++, limit); ps.setInt(i, offset);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) out.add(new Registration(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4)));
                }
            }
            return out;
        }
    }

    static class InMemoryDao implements RegistrationDao {
        private final List<Registration> rows = Collections.synchronizedList(new ArrayList<>());
        public List<String> events() { return List.of("Coding Contest", "Dance", "Drama", "Music", "Quiz"); }
        public boolean exists(String regNo, String event) {
            synchronized (rows) { return rows.stream().anyMatch(r -> r.regNo().equals(regNo) && r.event().equals(event)); }
        }
        public void insert(Registration r) { rows.add(r); }
        public List<Registration> find(String event, int limit, int offset) {
            synchronized (rows) {
                return rows.stream().filter(r -> event == null || r.event().equals(event))
                        .skip(offset).limit(limit).toList();
            }
        }
    }

    // ================= service layer =================
    static class RegistrationService {
        private final RegistrationDao dao;
        RegistrationService(RegistrationDao dao) { this.dao = dao; }

        /** Returns null on success, otherwise a message for the user. */
        String register(Registration r) throws SQLException {
            if (!r.regNo().matches("\\d{9}")) return "Register number must be exactly 9 digits.";
            if (r.name().isBlank() || !r.name().matches("[A-Za-z .]{2,60}")) return "Name must contain letters only.";
            if (!r.email().matches("[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+")) return "Enter a valid e-mail address.";
            if (r.event() == null) return "Select an event.";
            if (dao.exists(r.regNo(), r.event())) return "Student " + r.regNo() + " is already registered for " + r.event() + ".";
            try {
                dao.insert(r);
            } catch (SQLIntegrityConstraintViolationException e) {
                return "Duplicate registration rejected by database.";
            }
            return null;
        }
        List<Registration> page(String event, int pageNo, int size) throws SQLException {
            return dao.find(event, size, pageNo * size);
        }
        List<String> events() throws SQLException { return dao.events(); }
    }

    // ================= UI layer =================
    static class MainFrame extends JFrame {
        private static final int PAGE = 50;
        private final RegistrationService service;
        private final JTextField regNo = new JTextField(10), name = new JTextField(15), email = new JTextField(18);
        private final JComboBox<String> event = new JComboBox<>();
        private final JComboBox<String> filter = new JComboBox<>();
        private final DefaultTableModel model = new DefaultTableModel(new String[]{"Reg No", "Name", "Email", "Event"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        private final JLabel status = new JLabel("Loading events...");
        private int page = 0;

        MainFrame(RegistrationService service) {
            super("Campus Event Registration");
            this.service = service;

            JMenuBar bar = new JMenuBar();
            JMenu file = new JMenu("File");
            JMenuItem export = new JMenuItem("Export CSV...");
            JMenuItem exit = new JMenuItem("Exit");
            file.add(export); file.addSeparator(); file.add(exit);
            JMenu help = new JMenu("Help");
            JMenuItem about = new JMenuItem("About");
            help.add(about);
            bar.add(file); bar.add(help);
            setJMenuBar(bar);

            JPanel form = new JPanel(new FlowLayout(FlowLayout.LEFT));
            form.setBorder(BorderFactory.createTitledBorder("Register"));
            form.add(new JLabel("Reg No")); form.add(regNo);
            form.add(new JLabel("Name")); form.add(name);
            form.add(new JLabel("Email")); form.add(email);
            form.add(new JLabel("Event")); form.add(event);
            JButton registerBtn = new JButton("Register");
            form.add(registerBtn);

            JPanel filterRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
            JButton prev = new JButton("< Prev"), next = new JButton("Next >");
            filterRow.add(new JLabel("Filter by event")); filterRow.add(filter); filterRow.add(prev); filterRow.add(next);

            JPanel north = new JPanel(new GridLayout(2, 1));
            north.add(form); north.add(filterRow);
            add(north, BorderLayout.NORTH);
            add(new JScrollPane(new JTable(model)), BorderLayout.CENTER);
            add(status, BorderLayout.SOUTH);

            registerBtn.addActionListener(e -> onRegister(registerBtn));
            filter.addActionListener(e -> { page = 0; loadPage(); });
            prev.addActionListener(e -> { if (page > 0) { page--; loadPage(); } });
            next.addActionListener(e -> { page++; loadPage(); });
            export.addActionListener(e -> onExport());
            exit.addActionListener(e -> dispose());
            about.addActionListener(e -> JOptionPane.showMessageDialog(this, "Cultural Committee - Event Registration"));

            setDefaultCloseOperation(DISPOSE_ON_CLOSE);
            setSize(950, 500);
            setLocationRelativeTo(null);
            loadEvents();
        }

        private String selectedFilter() {
            Object f = filter.getSelectedItem();
            return f == null || f.equals("All events") ? null : f.toString();
        }

        private void loadEvents() {
            new SwingWorker<List<String>, Void>() {
                @Override protected List<String> doInBackground() throws Exception { return service.events(); }
                @Override protected void done() {
                    try {
                        filter.addItem("All events");
                        for (String ev : get()) { event.addItem(ev); filter.addItem(ev); }
                        status.setText("Ready");
                    } catch (Exception ex) { error("Could not load events: " + rootMessage(ex)); }
                }
            }.execute();
        }

        private void onRegister(JButton btn) {
            Registration r = new Registration(regNo.getText().trim(), name.getText().trim(),
                    email.getText().trim(), (String) event.getSelectedItem());
            btn.setEnabled(false);
            status.setText("Saving...");
            new SwingWorker<String, Void>() {
                @Override protected String doInBackground() throws Exception { return service.register(r); }
                @Override protected void done() {
                    btn.setEnabled(true);
                    try {
                        String err = get();
                        if (err != null) { error(err); return; }
                        status.setText("Registered " + r.name() + " for " + r.event());
                        regNo.setText(""); name.setText(""); email.setText("");
                        loadPage();
                    } catch (Exception ex) { error("Database error: " + rootMessage(ex)); }
                }
            }.execute();
        }

        private void loadPage() {
            String ev = selectedFilter();
            int p = page;
            status.setText("Loading page " + (p + 1) + "...");
            new SwingWorker<List<Registration>, Void>() {
                @Override protected List<Registration> doInBackground() throws Exception { return service.page(ev, p, PAGE); }
                @Override protected void done() {
                    try {
                        List<Registration> rows = get();
                        if (rows.isEmpty() && page > 0) { page--; status.setText("No more rows"); return; }
                        model.setRowCount(0);
                        for (Registration r : rows) model.addRow(new Object[]{r.regNo(), r.name(), r.email(), r.event()});
                        status.setText(rows.size() + " row(s), page " + (page + 1) + (ev == null ? "" : " - " + ev));
                    } catch (Exception ex) { error("Could not load: " + rootMessage(ex)); }
                }
            }.execute();
        }

        private void onExport() {
            JFileChooser ch = new JFileChooser();
            ch.setSelectedFile(new File("registrations.csv"));
            if (ch.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
            try (PrintWriter w = new PrintWriter(ch.getSelectedFile())) {
                w.println("reg_no,name,email,event");
                for (int i = 0; i < model.getRowCount(); i++)
                    w.println(model.getValueAt(i, 0) + "," + model.getValueAt(i, 1) + ","
                            + model.getValueAt(i, 2) + "," + model.getValueAt(i, 3));
                status.setText("Exported " + model.getRowCount() + " rows to " + ch.getSelectedFile().getName());
            } catch (IOException ex) { error("Export failed: " + ex.getMessage()); }
        }

        private void error(String msg) {
            status.setText(msg);
            JOptionPane.showMessageDialog(this, msg, "Error", JOptionPane.ERROR_MESSAGE);
        }

        private static String rootMessage(Throwable t) {
            while (t.getCause() != null) t = t.getCause();
            return t.getMessage();
        }
    }

    public static void main(String[] args) {
        RegistrationDao dao = args.length == 3
                ? new JdbcRegistrationDao(args[0], args[1], args[2])
                : new InMemoryDao();
        RegistrationService service = new RegistrationService(dao);
        SwingUtilities.invokeLater(() -> new MainFrame(service).setVisible(true));
    }
}
