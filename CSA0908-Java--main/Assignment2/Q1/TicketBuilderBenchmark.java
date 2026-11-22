import java.util.StringJoiner;

/**
 * Q1 - String vs StringBuilder vs StringBuffer for railway e-ticket generation.
 * Workloads: (1) 1 short ticket, (2) 10,000 tickets x 50 fields, (3) own: 100 tickets x 2,000 fields.
 */
public class TicketBuilderBenchmark {
    static final int RUNS = 5;

    static String[] fields(int n) {
        String[] f = new String[n];
        for (int i = 0; i < n; i++) f[i] = "FIELD" + i + ":VAL" + (i * 7);
        return f;
    }

    static String withPlus(String[] f) {
        String s = "";
        for (String x : f) s = s + x + "|";          // new String each iteration: O(n^2) chars copied
        return s;
    }

    static String withBuilder(String[] f) {
        StringBuilder sb = new StringBuilder();
        for (String x : f) sb.append(x).append('|');
        return sb.toString();
    }

    static String withBuffer(String[] f) {
        StringBuffer sb = new StringBuffer();        // synchronized append
        for (String x : f) sb.append(x).append('|');
        return sb.toString();
    }

    static String withJoiner(String[] f) {
        StringJoiner j = new StringJoiner("|", "", "|");
        for (String x : f) j.add(x);
        return j.toString();
    }

    interface Builder { String build(String[] f); }

    static void measure(String label, Builder b, int tickets, int nFields) {
        String[] f = fields(nFields);
        Runtime rt = Runtime.getRuntime();
        long totalNs = 0, memDelta = 0;
        for (int r = 0; r < RUNS; r++) {
            System.gc();
            long before = rt.totalMemory() - rt.freeMemory();
            long t0 = System.nanoTime();
            for (int t = 0; t < tickets; t++) b.build(f);
            totalNs += System.nanoTime() - t0;
            long after = rt.totalMemory() - rt.freeMemory();
            memDelta += Math.max(0, after - before);
        }
        // '+' creates ~2 intermediate Strings per field; builders create 1 final String
        long intermediates = label.startsWith("String +") ? 2L * nFields * tickets : tickets;
        System.out.printf("  %-16s avg %10.3f ms   ~%,12d String objects   mem +%,d KB%n",
                label, totalNs / 1e6 / RUNS, intermediates, memDelta / RUNS / 1024);
    }

    static void workload(String name, int tickets, int nFields) {
        System.out.println(name + " (" + tickets + " tickets x " + nFields + " fields)");
        measure("String +", TicketBuilderBenchmark::withPlus, tickets, nFields);
        measure("StringBuilder", TicketBuilderBenchmark::withBuilder, tickets, nFields);
        measure("StringBuffer", TicketBuilderBenchmark::withBuffer, tickets, nFields);
        measure("StringJoiner", TicketBuilderBenchmark::withJoiner, tickets, nFields);
        System.out.println();
    }

    public static void main(String[] args) throws InterruptedException {
        // warm-up for JIT
        for (int i = 0; i < 2000; i++) { withPlus(fields(20)); withBuilder(fields(20)); }

        workload("W1 short ticket", 1, 8);
        workload("W2 bulk", 10_000, 50);
        workload("W3 own: very long ticket", 100, 2_000);

        // Design decision: shared booking log appended by several threads
        StringBuilder unsafeLog = new StringBuilder();
        StringBuffer safeLog = new StringBuffer();
        Thread[] th = new Thread[8];
        for (int i = 0; i < th.length; i++) {
            th[i] = new Thread(() -> {
                for (int k = 0; k < 10_000; k++) {
                    safeLog.append('x');
                    try { unsafeLog.append('x'); } catch (RuntimeException e) { /* corrupted internal state */ }
                }
            });
            th[i].start();
        }
        for (Thread t : th) t.join();
        System.out.println("Shared log, expected length 80000");
        System.out.println("  StringBuilder (unsynchronized): " + unsafeLog.length());
        System.out.println("  StringBuffer  (synchronized)  : " + safeLog.length());
    }
}
