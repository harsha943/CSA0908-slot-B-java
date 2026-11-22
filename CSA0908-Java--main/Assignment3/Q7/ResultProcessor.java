import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Q7 - Concurrent, fault-tolerant semester result processor.
 * ExecutorService + Callable + Future; ConcurrentHashMap for results;
 * each file's failure is captured in its own Future so the run continues.
 * Usage: java ResultProcessor [numberOfFiles]   (default 10000)
 */
public class ResultProcessor {

    record Result(String regNo, int total, String grade) {}

    static class CorruptFileException extends Exception {
        CorruptFileException(String m) { super(m); }
    }

    /** File format: regNo,mark1,mark2,mark3,mark4,mark5 */
    static Result process(Path file) throws IOException, CorruptFileException {
        String line = Files.readString(file).trim();          // NoSuchFileException if missing
        String[] p = line.split(",");
        if (p.length != 6) throw new CorruptFileException(file.getFileName() + ": expected 6 fields");
        int total = 0;
        for (int i = 1; i < 6; i++) {
            int m;
            try { m = Integer.parseInt(p[i].trim()); }
            catch (NumberFormatException e) { throw new CorruptFileException(file.getFileName() + ": bad mark '" + p[i] + "'"); }
            if (m < 0 || m > 100) throw new CorruptFileException(file.getFileName() + ": mark out of range " + m);
            total += m;
        }
        // simulate CPU work for validation / grade computation
        double x = 0; for (int k = 0; k < 20_000; k++) x += Math.sqrt(k);
        String grade = total >= 450 ? "S" : total >= 400 ? "A" : total >= 350 ? "B" : total >= 250 ? "C" : "F";
        return new Result(p[0], total, grade);
    }

    static Map<String, Integer> run(List<Path> files, int threads, Map<String, Result> results,
                                    Map<String, String> failures) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        Map<Path, Future<Result>> futures = new LinkedHashMap<>();
        for (Path f : files) futures.put(f, pool.submit(() -> process(f)));
        pool.shutdown();

        AtomicInteger ok = new AtomicInteger(), bad = new AtomicInteger();
        for (Map.Entry<Path, Future<Result>> e : futures.entrySet()) {
            try {
                Result r = e.getValue().get();
                results.put(r.regNo(), r);
                ok.incrementAndGet();
            } catch (ExecutionException ex) {                 // one bad file never stops the run
                failures.put(e.getKey().getFileName().toString(),
                        ex.getCause().getClass().getSimpleName() + ": " + ex.getCause().getMessage());
                bad.incrementAndGet();
            }
        }
        pool.awaitTermination(1, TimeUnit.MINUTES);
        return Map.of("ok", ok.get(), "failed", bad.get());
    }

    public static void main(String[] args) throws Exception {
        int n = args.length > 0 ? Integer.parseInt(args[0]) : 10_000;
        Path dir = Files.createTempDirectory("scores");
        List<Path> files = new ArrayList<>();
        Random r = new Random(5);
        int expectedBad = 0;
        for (int i = 0; i < n; i++) {
            Path f = dir.resolve("s" + i + ".csv");
            files.add(f);
            String reg = "1923" + String.format("%05d", i);
            if (i % 500 == 7) { expectedBad++; continue; }                         // missing file
            if (i % 500 == 13) { Files.writeString(f, reg + ",90,abc,70,60,50"); expectedBad++; continue; }
            if (i % 500 == 21) { Files.writeString(f, reg + ",90,80"); expectedBad++; continue; }
            StringBuilder sb = new StringBuilder(reg);
            for (int k = 0; k < 5; k++) sb.append(',').append(40 + r.nextInt(61));
            Files.writeString(f, sb.toString());
        }

        // untimed warm-up pass: JIT compilation + OS file cache / antivirus scan of the new files
        run(files, 4, new ConcurrentHashMap<>(), new ConcurrentHashMap<>());

        System.out.printf("Files: %,d (injected bad: %d)%n", n, expectedBad);
        System.out.printf("%8s %10s %8s %8s%n", "threads", "time ms", "ok", "failed");
        Map<String, String> failures = null;
        for (int t : new int[]{1, 2, 4, 8}) {
            Map<String, Result> results = new ConcurrentHashMap<>();
            failures = new ConcurrentHashMap<>();
            long t0 = System.nanoTime();
            Map<String, Integer> s = run(files, t, results, failures);
            System.out.printf("%8d %10d %8d %8d %s%n", t, (System.nanoTime() - t0) / 1_000_000,
                    s.get("ok"), s.get("failed"),
                    s.get("ok") + s.get("failed") == n && s.get("failed") == expectedBad ? "summary correct" : "MISMATCH");
        }
        System.out.println("\nSample failures:");
        failures.entrySet().stream().limit(6).forEach(e -> System.out.println("  " + e.getKey() + " -> " + e.getValue()));
        System.out.println("CPU cores available: " + Runtime.getRuntime().availableProcessors());
    }
}
