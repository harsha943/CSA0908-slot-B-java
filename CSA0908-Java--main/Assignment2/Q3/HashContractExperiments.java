import java.util.*;

/**
 * Q3 - Experiments on the equals/hashCode contract.
 */
public class HashContractExperiments {

    /** Junior developer's class: equals overridden, hashCode NOT overridden. */
    static class BrokenStudent {
        String regNo; String name;
        BrokenStudent(String r, String n) { regNo = r; name = n; }
        @Override public boolean equals(Object o) {
            return o instanceof BrokenStudent b && regNo.equals(b.regNo);
        }
    }

    /** Correct class but with a mutable key field. */
    static class MutableStudent {
        String regNo;
        MutableStudent(String r) { regNo = r; }
        @Override public boolean equals(Object o) { return o instanceof MutableStudent m && regNo.equals(m.regNo); }
        @Override public int hashCode() { return regNo.hashCode(); }
    }

    record Named(String regNo, String name) {}

    public static void main(String[] args) {
        // Test 1: every scan is a different student -> appears correct
        Set<BrokenStudent> t1 = new HashSet<>();
        BrokenStudent a = new BrokenStudent("101", "Arun");
        t1.add(a); t1.add(new BrokenStudent("102", "Divya")); t1.add(a); // same reference re-added
        System.out.println("Test 1 predicted 2, actual " + t1.size());

        // Test 2: same student scanned at 3 gates (3 new objects) -> duplicates
        Set<BrokenStudent> t2 = new HashSet<>();
        for (int gate = 1; gate <= 3; gate++) t2.add(new BrokenStudent("101", "Arun"));
        System.out.println("Test 2 predicted 3 (identity hash differs), actual " + t2.size());

        // Test 3a: mutate a hash field after insertion
        Set<MutableStudent> t3 = new HashSet<>();
        MutableStudent m = new MutableStudent("201");
        t3.add(m);
        m.regNo = "999";
        System.out.println("Test 3a after mutation: contains(m)=" + t3.contains(m)
                + ", size=" + t3.size() + ", remove(m)=" + t3.remove(m) + "  (object is lost in wrong bucket)");

        // Test 3b: TreeSet whose comparator is inconsistent with equals (compares name only)
        Set<Named> t4 = new TreeSet<>(Comparator.comparing(Named::name));
        t4.add(new Named("301", "Kumar"));
        t4.add(new Named("302", "Kumar"));    // different student, same name
        System.out.println("Test 3b TreeSet by name predicted 1 (wrongly merged), actual " + t4.size());

        // Alternative: HashMap keyed by regNo and TreeSet by regNo
        Map<String, String> byReg = new HashMap<>();
        Set<Named> tree = new TreeSet<>(Comparator.comparing(Named::regNo));
        String[][] scans = {{"101", "Arun"}, {"102", "Divya"}, {"101", "Arun"}, {"103", "Lata"}};
        for (String[] s : scans) { byReg.putIfAbsent(s[0], s[1]); tree.add(new Named(s[0], s[1])); }
        System.out.println("HashMap<String,Student> unique = " + byReg.size()
                + ", TreeSet(by regNo) unique = " + tree.size());

        // timing: n scans
        int n = 200_000;
        long s0 = System.nanoTime();
        Set<String> hs = new HashSet<>();
        for (int i = 0; i < n; i++) hs.add("REG" + (i % 50_000));
        long s1 = System.nanoTime();
        Set<String> ts = new TreeSet<>();
        for (int i = 0; i < n; i++) ts.add("REG" + (i % 50_000));
        long s2 = System.nanoTime();
        System.out.printf("%,d scans: HashSet %.2f ms, TreeSet %.2f ms%n", n, (s1 - s0) / 1e6, (s2 - s1) / 1e6);
    }
}
