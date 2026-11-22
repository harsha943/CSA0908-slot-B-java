import java.util.*;

/**
 * Q4 - HashMap vs TreeMap vs LinkedHashMap for a pharmacy inventory,
 * plus the sorted-array + binarySearch alternative.
 */
public class PharmacyInventory {
    static final int LOOKUPS = 100_000;

    static String code(int i) { return String.format("MED%06d", i); }

    static List<String> insertionOrder(int n) {
        List<String> codes = new ArrayList<>();
        for (int i = 0; i < n; i++) codes.add(code(i));
        Collections.shuffle(codes, new Random(7));   // medicines added in random code order
        return codes;
    }

    static void test(String name, Map<String, Integer> map, List<String> codes) {
        long t0 = System.nanoTime();
        for (String c : codes) map.put(c, c.hashCode() & 0xFF);
        long t1 = System.nanoTime();

        Random r = new Random(1);
        long sum = 0;
        for (int i = 0; i < LOOKUPS; i++) sum += map.get(codes.get(r.nextInt(codes.size())));
        long t2 = System.nanoTime();

        // Report 1: code order
        List<String> byCode;
        if (map instanceof TreeMap) byCode = new ArrayList<>(map.keySet());
        else { byCode = new ArrayList<>(map.keySet()); Collections.sort(byCode); }   // extra O(n log n)
        long t3 = System.nanoTime();

        // Report 2: insertion order (only LinkedHashMap can produce it from its own state)
        List<String> added = new ArrayList<>(map.keySet());
        long t4 = System.nanoTime();

        boolean codeOk = isSorted(byCode);
        boolean insOk = added.equals(codes);
        System.out.printf("  %-14s put %8.2f ms | 100k get %8.2f ms (%.0f ns/get) | code report %7.2f ms %s | insertion report %6.2f ms %s%n",
                name, (t1 - t0) / 1e6, (t2 - t1) / 1e6, (t2 - t1) / (double) LOOKUPS,
                (t3 - t2) / 1e6, codeOk ? "OK" : "WRONG", (t4 - t3) / 1e6, insOk ? "OK" : "WRONG ORDER");
    }

    static boolean isSorted(List<String> l) {
        for (int i = 1; i < l.size(); i++) if (l.get(i - 1).compareTo(l.get(i)) > 0) return false;
        return true;
    }

    static void sortedArray(List<String> codes) {
        long t0 = System.nanoTime();
        String[] arr = codes.toArray(new String[0]);
        Arrays.sort(arr);
        long t1 = System.nanoTime();
        Random r = new Random(1);
        int found = 0;
        for (int i = 0; i < LOOKUPS; i++)
            if (Arrays.binarySearch(arr, codes.get(r.nextInt(codes.size()))) >= 0) found++;
        long t2 = System.nanoTime();
        System.out.printf("  %-14s sort %7.2f ms | 100k binarySearch %8.2f ms | code report free (already sorted), found=%d%n",
                "SortedArray", (t1 - t0) / 1e6, (t2 - t1) / 1e6, found);
    }

    public static void main(String[] args) {
        for (int n : new int[]{1_000, 1_000, 50_000, 500_000}) {
            System.out.printf("%nMedicines: %,d%n", n);
            List<String> codes = insertionOrder(n);
            test("HashMap", new HashMap<>(), codes);
            test("TreeMap", new TreeMap<>(), codes);
            test("LinkedHashMap", new LinkedHashMap<>(), codes);
            sortedArray(codes);
        }
        System.out.println("\nAcceptable lookup criterion used: < 1 microsecond per lookup.");
    }
}
