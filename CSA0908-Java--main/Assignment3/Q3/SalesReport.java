import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.logging.Logger;

/**
 * Q3 - Daily sales report: manual close() in finally vs try-with-resources.
 * Each store file holds lines "item,amount". A corrupt line makes the manual version
 * leak the reader it was using; try-with-resources always closes.
 */
public class SalesReport {
    static final Logger LOG = Logger.getLogger("SalesReport");
    static int openHandles = 0;            // instrumentation: how many readers are open right now

    /** Reader that counts open/close so leaks are visible. */
    static class TrackedReader extends BufferedReader {
        final String name;
        boolean closed;
        TrackedReader(Path p) throws IOException {
            super(Files.newBufferedReader(p));
            name = p.getFileName().toString();
            openHandles++;
        }
        @Override public void close() throws IOException {
            if (!closed) { closed = true; openHandles--; }
            super.close();
        }
    }

    // ---------- Version 1: manual close (buggy) ----------
    static double manualVersion(List<Path> stores, Path out) throws IOException {
        double total = 0;
        BufferedWriter w = Files.newBufferedWriter(out);
        try {
            for (Path p : stores) {
                TrackedReader r = new TrackedReader(p);
                String line;
                while ((line = r.readLine()) != null) {
                    double amt = Double.parseDouble(line.split(",")[1]);   // may throw -> r never closed
                    total += amt;
                }
                r.close();                                                  // skipped on exception: LEAK
                w.write(p.getFileName() + " ok\n");
            }
            w.write("TOTAL " + total + "\n");
        } finally {
            w.close();
        }
        return total;
    }

    // ---------- Version 2: try-with-resources ----------
    static double twrVersion(List<Path> stores, Path out) throws IOException {
        double total = 0;
        try (BufferedWriter w = Files.newBufferedWriter(out)) {
            for (Path p : stores) {
                try (TrackedReader r = new TrackedReader(p)) {             // closed automatically, reverse order
                    String line;
                    while ((line = r.readLine()) != null) total += Double.parseDouble(line.split(",")[1]);
                } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
                    LOG.warning("Corrupt data in " + p.getFileName() + ": " + e.getMessage());
                    throw e;
                }
                w.write(p.getFileName() + " ok\n");
            }
            w.write("TOTAL " + total + "\n");
        }
        return total;
    }

    /** Resource whose close() also fails -> suppressed exception demo. */
    static class FailingResource implements AutoCloseable {
        @Override public void close() { throw new IllegalStateException("close failed"); }
    }

    public static void main(String[] args) throws IOException {
        Path dir = Files.createTempDirectory("stores");
        List<Path> good = new ArrayList<>();
        for (int s = 1; s <= 3; s++) {
            Path p = dir.resolve("store" + s + ".csv");
            Files.write(p, List.of("soap," + (10 * s), "rice," + (100 * s)));
            good.add(p);
        }
        Path bad = dir.resolve("store4.csv");
        Files.write(bad, List.of("oil,50", "sugar,ABC"));      // corrupt line
        List<Path> withBad = new ArrayList<>(good);
        withBad.add(1, bad);

        Path out1 = dir.resolve("report_manual.txt"), out2 = dir.resolve("report_twr.txt");
        System.out.println("Good input: manual=" + manualVersion(good, out1) + "  twr=" + twrVersion(good, out2)
                + "  same report? " + Files.readString(out1).equals(Files.readString(out2)));

        for (int i = 0; i < 2; i++) {
            openHandles = 0;
            try {
                if (i == 0) manualVersion(withBad, out1); else twrVersion(withBad, out2);
            } catch (RuntimeException e) {
                System.out.println((i == 0 ? "Manual" : "TWR   ") + " version failed (" + e.getClass().getSimpleName()
                        + "), files left open: " + openHandles);
            }
        }

        try (FailingResource fr = new FailingResource()) {
            throw new RuntimeException("body failed");
        } catch (RuntimeException e) {
            System.out.println("Primary: " + e.getMessage() + ", suppressed: "
                    + Arrays.toString(e.getSuppressed()));
        }
    }
}
