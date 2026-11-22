import java.util.*;

/**
 * Q7 - Diagnose, redesign and validate.
 *
 * Diagnosis (from Q5): Leaderboard.ranking() copies the list and re-sorts it every time
 * it is called. During a live meet the board is refreshed after EVERY match result, so
 * u updates on n teams cost O(u * n log n) and create u full list copies.
 *
 * Objective: keep teams permanently ordered so that one result update costs O(log n)
 * and reading the top-k costs O(k), without re-sorting and without breaking ordering.
 *
 * Redesign: TreeSet ordered by (points desc, goalDiff desc, name asc) + HashMap name->Team.
 * An update removes the team (O(log n)), replaces it with a new immutable value and
 * re-inserts it (O(log n)). Team objects are immutable so the TreeSet is never corrupted
 * by mutating a key in place (the hashCode/compareTo lesson from Q3).
 */
public class LeaderboardRedesign {

    record Team(String name, int points, int goalDiff) {}

    static final Comparator<Team> RANK = Comparator.comparingInt(Team::points).reversed()
            .thenComparing(Comparator.comparingInt(Team::goalDiff).reversed())
            .thenComparing(Team::name);

    /** ORIGINAL: list + full sort on every read. */
    static class SortingBoard {
        final Map<String, Team> teams = new HashMap<>();
        void update(String name, int pts, int gd) {
            Team t = teams.get(name);
            teams.put(name, new Team(name, t.points() + pts, t.goalDiff() + gd));
        }
        List<Team> top(int k) {
            List<Team> copy = new ArrayList<>(teams.values());
            copy.sort(RANK);
            return copy.subList(0, Math.min(k, copy.size()));
        }
    }

    /** REDESIGN: always-ordered TreeSet + index. */
    static class OrderedBoard {
        final Map<String, Team> index = new HashMap<>();
        final TreeSet<Team> order = new TreeSet<>(RANK);
        void add(Team t) { index.put(t.name(), t); order.add(t); }
        void update(String name, int pts, int gd) {
            Team old = index.get(name);
            order.remove(old);                                     // O(log n)
            Team nw = new Team(name, old.points() + pts, old.goalDiff() + gd);
            index.put(name, nw);
            order.add(nw);                                         // O(log n)
        }
        List<Team> top(int k) {
            List<Team> out = new ArrayList<>(k);
            for (Team t : order) { if (out.size() == k) break; out.add(t); }
            return out;
        }
    }

    public static void main(String[] args) {
        System.out.println("Prediction: original grows ~ n log n per refresh, redesign ~ log n.");
        System.out.printf("%n%8s %10s %16s %16s %8s%n", "teams", "updates", "original (ms)", "redesign (ms)", "same?");
        for (int n : new int[]{1_000, 1_000, 10_000, 50_000}) {
            int updates = 2_000;
            SortingBoard a = new SortingBoard();
            OrderedBoard b = new OrderedBoard();
            for (int i = 0; i < n; i++) {
                Team t = new Team("T" + i, 0, 0);
                a.teams.put(t.name(), t);
                b.add(t);
            }
            Random r1 = new Random(9), r2 = new Random(9);
            List<Team> lastA = null, lastB = null;

            long t0 = System.nanoTime();
            for (int u = 0; u < updates; u++) {
                a.update("T" + r1.nextInt(n), r1.nextInt(4), r1.nextInt(7) - 3);
                lastA = a.top(10);                                  // refresh after every match
            }
            long t1 = System.nanoTime();
            for (int u = 0; u < updates; u++) {
                b.update("T" + r2.nextInt(n), r2.nextInt(4), r2.nextInt(7) - 3);
                lastB = b.top(10);
            }
            long t2 = System.nanoTime();
            System.out.printf("%,8d %,10d %16.2f %16.2f %8s%n", n, updates,
                    (t1 - t0) / 1e6, (t2 - t1) / 1e6, lastA.equals(lastB));
        }
        System.out.println("\nTrade-off: TreeSet uses one tree node (~40 bytes) per team plus the HashMap index;"
                + " for a single end-of-day ranking the original one-shot sort is simpler and as fast.");
    }
}
