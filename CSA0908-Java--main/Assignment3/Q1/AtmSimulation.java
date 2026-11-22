import java.io.*;
import java.util.InputMismatchException;
import java.util.Scanner;

/**
 * Q1 - Handling failure in an ATM withdrawal: try, multi-catch, finally.
 * Usage: java AtmSimulation            (interactive)
 *        java AtmSimulation test       (runs 6 scripted test inputs)
 */
public class AtmSimulation {

    static class InsufficientFundsException extends Exception {
        InsufficientFundsException(String m) { super(m); }
    }

    static double balance = 10_000;

    /** Reads the account number written on the card from the card-reader file. */
    static String readCard(String cardFile) throws IOException {
        try (BufferedReader r = new BufferedReader(new FileReader(cardFile))) {   // FileNotFoundException
            return r.readLine();
        }
    }

    static void withdraw(String cardFile, String input) {
        Scanner in = new Scanner(input);
        System.out.println("> input: \"" + input.replace("\n", " | ") + "\", card=" + cardFile);
        try {
            String acc = readCard(cardFile);                                     // checked IOException
            long accNo = Long.parseLong(acc.trim());                             // NumberFormatException
            int amount = in.nextInt();                                           // InputMismatchException
            if (amount <= 0) throw new IllegalArgumentException("Amount must be positive");
            if (amount % 100 != 0) throw new IllegalArgumentException("Amount must be a multiple of 100");
            if (amount > balance) throw new InsufficientFundsException("Balance " + balance + " < " + amount);
            balance -= amount;
            System.out.println("  Dispensed Rs " + amount + " from " + accNo + ", balance " + balance);
        } catch (FileNotFoundException e) {                                      // narrower before IOException
            System.out.println("  [catch FileNotFound] Card reader file missing: " + e.getMessage());
        } catch (IOException e) {
            System.out.println("  [catch IOException] Card read error: " + e.getMessage());
        } catch (InputMismatchException | NumberFormatException e) {             // multi-catch
            System.out.println("  [multi-catch] Not a number: " + e.getClass().getSimpleName());
        } catch (IllegalArgumentException e) {
            System.out.println("  [catch IllegalArgument] " + e.getMessage());
        } catch (InsufficientFundsException e) {
            System.out.println("  [catch InsufficientFunds] " + e.getMessage());
        } finally {
            in.close();
            System.out.println("  [finally] session closed, card returned");
        }
    }

    public static void main(String[] args) throws IOException {
        File card = File.createTempFile("card", ".txt");
        try (PrintWriter w = new PrintWriter(card)) { w.println("123456789"); }
        File badCard = File.createTempFile("badcard", ".txt");
        try (PrintWriter w = new PrintWriter(badCard)) { w.println("12AB"); }

        if (args.length > 0 && args[0].equals("test")) {
            withdraw(card.getPath(), "2000");          // valid
            withdraw(card.getPath(), "abc");           // letters
            withdraw(card.getPath(), "-500");          // negative
            withdraw(card.getPath(), "50000");         // more than balance
            withdraw("missing_card.txt", "1000");      // missing file
            withdraw(badCard.getPath(), "1000");       // corrupt card data
            System.out.println("\nUncaught exception demo (stack trace):");
            new Scanner("").nextInt();                 // NoSuchElementException -> stack trace
        } else {
            Scanner sc = new Scanner(System.in);
            System.out.print("Enter amount: ");
            withdraw(card.getPath(), sc.nextLine());
        }
        card.delete(); badCard.delete();
    }
}
