/**
 * Task 2 - Data representation: integer division / casting risk in fee computation.
 */
public class CastingDemo {

    // WRONG: integer division truncates 130/60 to 2 hours
    static double wrongFee(int minutes, double ratePerHour) {
        int hours = minutes / 60;
        return hours * ratePerHour;
    }

    // WRONG: cast after floating division truncates too
    static double wrongFee2(int minutes, double ratePerHour) {
        return (int) (minutes / 60.0) * ratePerHour;
    }

    // CORRECT: ceiling using integer arithmetic only - every started hour is billed
    static double correctFee(int minutes, double ratePerHour) {
        int hours = (minutes + 59) / 60;
        return hours * ratePerHour;
    }

    public static void main(String[] args) {
        // jagged array: floors with different numbers of slots
        String[][] slots = new String[3][];
        slots[0] = new String[20];
        slots[1] = new String[50];
        slots[2] = new String[35];
        for (int f = 0; f < slots.length; f++)
            System.out.println("Floor " + f + " has " + slots[f].length + " slots");

        int[] tests = {0, 1, 59, 60, 61, 130};
        System.out.println("\nminutes | wrong(int div) | wrong(cast) | correct(ceil)");
        for (int m : tests)
            System.out.printf("%7d | %14.1f | %11.1f | %13.1f%n",
                    m, wrongFee(m, 30), wrongFee2(m, 30), correctFee(m, 30));

        // Scanner or command-line input
        if (args.length == 2) {
            int minutes = Integer.parseInt(args[0]);
            double rate = Double.parseDouble(args[1]);
            System.out.println("\nFee from command line: Rs " + correctFee(minutes, rate));
        }
    }
}
