import java.io.*;
import java.nio.file.*;
import java.util.Random;

/**
 * A2 / B1.3 - Count lines unbuffered (1 read() call per byte) vs buffered,
 * on generated files of 1 MB, 10 MB and 50 MB.
 */
public class LineCounter {

    static long countUnbuffered(Path p) throws IOException {
        long lines = 0;
        try (FileInputStream in = new FileInputStream(p.toFile())) {
            int b;
            while ((b = in.read()) != -1) if (b == '\n') lines++;    // one system call per byte
        }
        return lines;
    }

    static long countBufferedStream(Path p) throws IOException {
        long lines = 0;
        try (BufferedInputStream in = new BufferedInputStream(new FileInputStream(p.toFile()), 64 * 1024)) {
            int b;
            while ((b = in.read()) != -1) if (b == '\n') lines++;    // read() served from memory buffer
        }
        return lines;
    }

    static long countByteArray(Path p) throws IOException {
        long lines = 0;
        byte[] buf = new byte[64 * 1024];
        try (FileInputStream in = new FileInputStream(p.toFile())) {
            int n;
            while ((n = in.read(buf)) != -1)
                for (int i = 0; i < n; i++) if (buf[i] == '\n') lines++;
        }
        return lines;
    }

    static long countReadAllLines(Path p) throws IOException {
        return Files.readAllLines(p).size();                          // whole file in memory
    }

    static Path makeFile(int mb) throws IOException {
        Path p = Files.createTempFile("attendance" + mb, ".log");
        Random r = new Random(1);
        try (BufferedWriter w = Files.newBufferedWriter(p)) {
            long written = 0, target = mb * 1024L * 1024L;
            while (written < target) {
                String line = "1923" + (10000 + r.nextInt(90000)) + ",2026-11-21,08:" + (10 + r.nextInt(50)) + ",PRESENT";
                w.write(line); w.newLine();
                written += line.length() + System.lineSeparator().length();
            }
        }
        return p;
    }

    public static void main(String[] args) throws IOException {
        boolean skipSlow = args.length > 0 && args[0].equals("fast");
        System.out.printf("%6s %10s %14s %14s %14s %14s%n", "size", "lines", "unbuffered ms", "buffered ms", "byte[] ms", "readAll ms");
        for (int mb : new int[]{1, 10, 50}) {
            Path p = makeFile(mb);
            long t0 = System.nanoTime();
            long l1 = (skipSlow && mb > 1) ? -1 : countUnbuffered(p);
            long t1 = System.nanoTime();
            long l2 = countBufferedStream(p);
            long t2 = System.nanoTime();
            long l3 = countByteArray(p);
            long t3 = System.nanoTime();
            long l4 = countReadAllLines(p);
            long t4 = System.nanoTime();
            System.out.printf("%4dMB %,10d %14s %14.1f %14.1f %14.1f%s%n", mb, l2,
                    l1 < 0 ? "skipped" : String.format("%.1f", (t1 - t0) / 1e6),
                    (t2 - t1) / 1e6, (t3 - t2) / 1e6, (t4 - t3) / 1e6,
                    (l1 >= 0 && l1 != l2) || l2 != l3 || l3 != l4 ? "  MISMATCH" : "");
            Files.delete(p);
        }
        System.out.println("Unbuffered read() calls = file size in bytes (e.g. ~52 million for 50 MB).");
    }
}
