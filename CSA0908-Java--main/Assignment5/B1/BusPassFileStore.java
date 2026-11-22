import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;

/**
 * B1 - Full bus-pass text-file store (add / find / list by route) with validation,
 * tests (normal, missing ID, damaged line), and growth measurement 1,000 -> 100,000 records.
 *
 * Record format (one line, '|' delimited, fields escaped):
 *   passId|regNo|name|route|stop|validTill(yyyy-mm-dd)|amount
 * A '|' or '\' inside a field is written as '\|' or '\\'.
 */
public class BusPassFileStore {

    record Pass(String passId, String regNo, String name, int route, String stop, LocalDate validTill, double amount) {}

    private final Path file;
    final List<String> damaged = new ArrayList<>();

    BusPassFileStore(Path file) { this.file = file; }

    static String esc(String s) { return s.replace("\\", "\\\\").replace("|", "\\|"); }

    static List<String> split(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '\\' && i + 1 < line.length()) cur.append(line.charAt(++i));
            else if (c == '|') { out.add(cur.toString()); cur.setLength(0); }
            else cur.append(c);
        }
        out.add(cur.toString());
        return out;
    }

    /** Parses a line, or returns null and records why if the line is damaged. */
    Pass parse(String line, int lineNo) {
        if (line.isBlank()) { damaged.add("line " + lineNo + ": empty"); return null; }
        List<String> f = split(line);
        if (f.size() != 7) { damaged.add("line " + lineNo + ": " + f.size() + " fields"); return null; }
        try {
            return new Pass(f.get(0), f.get(1), f.get(2), Integer.parseInt(f.get(3)), f.get(4),
                    LocalDate.parse(f.get(5)), Double.parseDouble(f.get(6)));
        } catch (RuntimeException e) {
            damaged.add("line " + lineNo + ": " + e.getClass().getSimpleName());
            return null;
        }
    }

    void add(Pass p) throws IOException {
        if (p.amount() < 0) throw new IllegalArgumentException("negative amount");
        String line = String.join("|", esc(p.passId()), esc(p.regNo()), esc(p.name()),
                String.valueOf(p.route()), esc(p.stop()), p.validTill().toString(), String.valueOf(p.amount()));
        try (BufferedWriter w = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            w.write(line);
            w.newLine();                        // a line without a newline at EOF = incomplete write
        }
    }

    Pass find(String passId) throws IOException {
        if (!Files.exists(file)) return null;
        try (BufferedReader r = Files.newBufferedReader(file)) {
            String line; int n = 0;
            while ((line = r.readLine()) != null) {
                Pass p = parse(line, ++n);
                if (p != null && p.passId().equals(passId)) return p;
            }
        }
        return null;
    }

    List<Pass> listByRoute(int route) throws IOException {
        List<Pass> out = new ArrayList<>();
        if (!Files.exists(file)) return out;
        try (BufferedReader r = Files.newBufferedReader(file)) {
            String line; int n = 0;
            while ((line = r.readLine()) != null) {
                Pass p = parse(line, ++n);
                if (p != null && p.route() == route) out.add(p);
            }
        }
        return out;
    }

    static void bulkWrite(Path f, int n) throws IOException {
        try (BufferedWriter w = Files.newBufferedWriter(f)) {
            for (int i = 0; i < n; i++) {
                w.write("P" + i + "|1923" + String.format("%05d", i) + "|Student " + i + "|" + (i % 20)
                        + "|Stop " + (i % 50) + "|2027-05-31|" + (3000 + i % 2000));
                w.newLine();
            }
        }
    }

    public static void main(String[] args) throws IOException {
        Path f = Files.createTempFile("buspass", ".txt");
        BusPassFileStore s = new BusPassFileStore(f);
        s.add(new Pass("P1", "192311001", "Arun", 12, "Main Gate", LocalDate.of(2027, 5, 31), 4500));
        s.add(new Pass("P2", "192311002", "Divya | Rao", 7, "Bus Stand", LocalDate.of(2027, 5, 31), 3800));
        Files.writeString(f, "P3|BROKEN\n\nP4|192311004|Kumar|12|Gate|2027-05-31|abc\n", StandardOpenOption.APPEND);
        s.add(new Pass("P5", "192311005", "Lata", 12, "Library", LocalDate.of(2027, 5, 31), 4100));

        System.out.println("Test                         | Result");
        System.out.println("find P2 (name contains '|')  | " + s.find("P2"));
        System.out.println("find P99 (does not exist)    | " + s.find("P99"));
        System.out.println("find P3 (damaged line)       | " + s.find("P3"));
        System.out.println("list route 12                | " + s.listByRoute(12).stream().map(Pass::passId).toList());
        System.out.println("damaged lines detected       | " + new LinkedHashSet<>(s.damaged));
        System.out.println("missing file find            | " + new BusPassFileStore(Path.of("no_such_file.txt")).find("P1"));
        Files.delete(f);

        System.out.println("\nGrowth: time to find the LAST pass (worst case linear scan)");
        for (int n : new int[]{1_000, 1_000, 10_000, 50_000, 100_000}) {
            Path g = Files.createTempFile("growth", ".txt");
            bulkWrite(g, n);
            BusPassFileStore gs = new BusPassFileStore(g);
            long t0 = System.nanoTime();
            for (int k = 0; k < 5; k++) gs.find("P" + (n - 1));
            System.out.printf("  %,8d records: %8.2f ms per find%n", n, (System.nanoTime() - t0) / 5e6);
            Files.delete(g);
        }
    }
}
