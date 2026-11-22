import java.util.*;

/**
 * Q2 - ArrayList vs LinkedList (and ArrayDeque for the "Up Next" queue).
 */
public class PlaylistBenchmark {
    record Song(int id) {}

    static final int OPS = 2_000;

    static long time(Runnable r) {
        long t0 = System.nanoTime();
        r.run();
        return System.nanoTime() - t0;
    }

    static List<Song> fill(List<Song> l, int n) {
        for (int i = 0; i < n; i++) l.add(new Song(i));
        return l;
    }

    static void runSize(int n) {
        System.out.printf("%nPlaylist size %,d (times in ms for %,d ops)%n", n, OPS);
        System.out.printf("%-22s %12s %12s%n", "Operation", "ArrayList", "LinkedList");
        String[] names = {"add at end", "insert/remove front", "insert/remove middle", "get k-th", "iterate whole list"};
        for (int op = 0; op < names.length; op++) {
            double[] res = new double[2];
            for (int k = 0; k < 2; k++) {
                List<Song> l = fill(k == 0 ? new ArrayList<>() : new LinkedList<>(), n);
                Random rnd = new Random(42);
                final int o = op;
                res[k] = time(() -> {
                    switch (o) {
                        case 0 -> { for (int i = 0; i < OPS; i++) l.add(new Song(i)); }
                        case 1 -> { for (int i = 0; i < OPS; i++) { l.add(0, new Song(i)); l.remove(0); } }
                        case 2 -> { for (int i = 0; i < OPS; i++) { l.add(l.size() / 2, new Song(i)); l.remove(l.size() / 2); } }
                        case 3 -> { long s = 0; for (int i = 0; i < OPS; i++) s += l.get(rnd.nextInt(l.size())).id(); }
                        case 4 -> { long s = 0; for (Song x : l) s += x.id(); }
                    }
                }) / 1e6;
            }
            System.out.printf("%-22s %12.3f %12.3f%n", names[op], res[0], res[1]);
        }
    }

    public static void main(String[] args) {
        runSize(1_000);           // warm-up + small
        runSize(1_000);
        runSize(10_000);
        runSize(100_000);

        // Changed condition: "Up Next" queue - add at both ends, remove from front
        int n = 100_000;
        System.out.println("\nUp Next queue: " + n + " addFirst/addLast + pollFirst");
        Deque<Song> dq = new ArrayDeque<>();
        LinkedList<Song> ll = new LinkedList<>();
        List<Song> al = new ArrayList<>();
        System.out.printf("  ArrayDeque : %.3f ms%n", time(() -> {
            for (int i = 0; i < n; i++) { dq.addFirst(new Song(i)); dq.addLast(new Song(i)); dq.pollFirst(); }
        }) / 1e6);
        System.out.printf("  LinkedList : %.3f ms%n", time(() -> {
            for (int i = 0; i < n; i++) { ll.addFirst(new Song(i)); ll.addLast(new Song(i)); ll.pollFirst(); }
        }) / 1e6);
        int m = 20_000;   // ArrayList is O(n) at the front, so fewer ops
        System.out.printf("  ArrayList  : %.3f ms (only %,d ops)%n", time(() -> {
            for (int i = 0; i < m; i++) { al.add(0, new Song(i)); al.add(new Song(i)); al.remove(0); }
        }) / 1e6, m);
    }
}
