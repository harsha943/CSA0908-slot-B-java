import java.util.*;

/**
 * Q6 - Correctness check (8 patients), why printing a PriorityQueue is not in order,
 * growth measurement, aging rule against starvation, and the 5-FIFO-queues alternative.
 */
public class TriageExperiments {
    record P(String name, int severity, long arrival) {
        @Override public String toString() { return name + "(S" + severity + ")"; }
    }

    static final Comparator<P> RULE = Comparator.comparingInt(P::severity).thenComparingLong(P::arrival);

    /** Aging: effective severity improves by 1 for every AGE_STEP time units waited. */
    static int effectiveSeverity(P p, long now, long ageStep) {
        long bonus = (now - p.arrival()) / ageStep;
        return (int) Math.max(1, p.severity() - bonus);
    }

    public static void main(String[] args) {
        // Hand-verifiable instance
        String[] names = {"A", "B", "C", "D", "E", "F", "G", "H"};
        int[] sev =      { 4,   2,   5,   1,   2,   4,   1,   3 };
        PriorityQueue<P> pq = new PriorityQueue<>(RULE);
        for (int i = 0; i < names.length; i++) pq.add(new P(names[i], sev[i], i));
        System.out.println("Printing PQ directly (heap array order): " + pq);
        StringBuilder sb = new StringBuilder();
        while (!pq.isEmpty()) sb.append(pq.poll()).append(' ');
        System.out.println("Treatment order : " + sb.toString().trim());
        System.out.println("Hand-worked     : D(S1) G(S1) B(S2) E(S2) H(S3) A(S4) F(S4) C(S5)");

        // Growth
        System.out.println("\nSize        add (ns/op)   poll (ns/op)");
        Random r = new Random(3);
        for (int n : new int[]{10_000, 10_000, 100_000, 1_000_000}) {
            PriorityQueue<P> q = new PriorityQueue<>(RULE);
            long t0 = System.nanoTime();
            for (int i = 0; i < n; i++) q.add(new P("p", 1 + r.nextInt(5), i));
            long t1 = System.nanoTime();
            while (!q.isEmpty()) q.poll();
            long t2 = System.nanoTime();
            System.out.printf("%,10d  %10.1f  %12.1f%n", n, (t1 - t0) / (double) n, (t2 - t1) / (double) n);
        }

        // Alternative: array of five FIFO queues -> O(1) admit and O(1) serve
        @SuppressWarnings("unchecked")
        ArrayDeque<P>[] levels = new ArrayDeque[6];
        for (int s = 1; s <= 5; s++) levels[s] = new ArrayDeque<>();
        for (int i = 0; i < names.length; i++) levels[sev[i]].add(new P(names[i], sev[i], i));
        sb.setLength(0);
        for (int s = 1; s <= 5; s++) while (!levels[s].isEmpty()) sb.append(levels[s].poll()).append(' ');
        System.out.println("\n5 FIFO queues order: " + sb.toString().trim());

        // Starvation demo and aging rule
        long now = 0;
        List<P> waiting = new ArrayList<>();
        waiting.add(new P("Minor", 5, 0));
        int servedBeforeMinorPlain = -1, servedBeforeMinorAged = -1;
        for (int mode = 0; mode < 2; mode++) {
            List<P> w = new ArrayList<>(waiting);
            int served = 0;
            for (now = 1; now <= 200; now++) {
                w.add(new P("Crit" + now, 1 + (int) (now % 2), now));   // severe cases keep arriving
                final long t = now;
                Comparator<P> cmp = mode == 0 ? RULE
                        : Comparator.comparingInt((P p) -> effectiveSeverity(p, t, 10)).thenComparingLong(P::arrival);
                P next = Collections.min(w, cmp);
                w.remove(next);
                if (next.name().equals("Minor")) {
                    if (mode == 0) servedBeforeMinorPlain = served; else servedBeforeMinorAged = served;
                    break;
                }
                served++;
            }
        }
        System.out.println("\nStarvation: plain rule -> Minor served after "
                + (servedBeforeMinorPlain < 0 ? "NEVER (200+ patients)" : servedBeforeMinorPlain + " patients"));
        System.out.println("Aging rule (1 level per 10 time units) -> Minor served after "
                + servedBeforeMinorAged + " patients");

        System.out.println("\n5,000 patients/day = ~0.06 per second; PQ operations take well under 1 microsecond each.");
    }
}
