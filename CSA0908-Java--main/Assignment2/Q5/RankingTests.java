import java.util.*;

/**
 * Q5 - Generic ranking component tests: ties at every level, and a data set
 * designed to break faulty comparators (subtraction overflow, missing tie-breaker).
 */
public class RankingTests {

    interface Rankable { int points(); int goalDiff(); String name(); }

    record TeamR(String name, int points, int goalDiff) implements Rankable {}
    record Player(String name, int points, int goalDiff) implements Rankable {}

    /** Bounded generic: works for any Rankable (teams, players, colleges...). */
    static class RankBoard<T extends Rankable> {
        private final List<T> items = new ArrayList<>();
        static <R extends Rankable> Comparator<R> standard() {
            return Comparator.<R>comparingInt(Rankable::points).reversed()
                    .thenComparing(Comparator.<R>comparingInt(Rankable::goalDiff).reversed())
                    .thenComparing(Rankable::name);
        }
        void addAll(List<? extends T> more) { items.addAll(more); }   // wildcard: accept subtypes
        List<T> ranking() { List<T> c = new ArrayList<>(items); c.sort(standard()); return c; }
    }

    // FAULTY 1: subtraction overflows for large/negative values
    static final Comparator<TeamR> SUBTRACT = (a, b) -> b.points() - a.points();
    // FAULTY 2: ignores the name tie-breaker -> order depends on input order
    static final Comparator<TeamR> NO_NAME = Comparator.comparingInt(TeamR::points).reversed()
            .thenComparing(Comparator.comparingInt(TeamR::goalDiff).reversed());

    public static void main(String[] args) {
        List<TeamR> ties = List.of(
                new TeamR("Zebras", 9, 4), new TeamR("Ants", 9, 4), new TeamR("Lions", 9, 6),
                new TeamR("Bears", 6, 2), new TeamR("Crows", 6, 2), new TeamR("Owls", 6, 5));
        RankBoard<TeamR> b = new RankBoard<>();
        b.addAll(ties);
        System.out.println("Ties data set : " + b.ranking());

        // Adversarial: extreme values break subtraction
        List<TeamR> evil = new ArrayList<>(List.of(
                new TeamR("Max", Integer.MAX_VALUE, 0), new TeamR("Min", Integer.MIN_VALUE, 0),
                new TeamR("Zero", 0, 0)));
        List<TeamR> s1 = new ArrayList<>(evil); s1.sort(SUBTRACT);
        List<TeamR> s2 = new ArrayList<>(evil); s2.sort(RankBoard.standard());
        System.out.println("Subtraction comparator (faulty): " + s1);
        System.out.println("comparingInt comparator (correct): " + s2);

        List<TeamR> order1 = new ArrayList<>(List.of(new TeamR("B", 5, 1), new TeamR("A", 5, 1)));
        List<TeamR> order2 = new ArrayList<>(List.of(new TeamR("A", 5, 1), new TeamR("B", 5, 1)));
        order1.sort(NO_NAME); order2.sort(NO_NAME);
        System.out.println("Missing tie-breaker gives input-dependent order: " + order1 + " vs " + order2);

        // Reuse for players
        RankBoard<Player> pb = new RankBoard<>();
        pb.addAll(List.of(new Player("Ravi", 12, 3), new Player("Anu", 12, 3), new Player("Sam", 15, 0)));
        System.out.println("Players       : " + pb.ranking());

        // Alternative: three stable sorts (name, then goalDiff, then points)
        List<TeamR> three = new ArrayList<>(ties);
        three.sort(Comparator.comparing(TeamR::name));
        three.sort(Comparator.comparingInt(TeamR::goalDiff).reversed());
        three.sort(Comparator.comparingInt(TeamR::points).reversed());
        System.out.println("3 stable sorts: " + three + "  same? " + three.equals(b.ranking()));
    }
}
