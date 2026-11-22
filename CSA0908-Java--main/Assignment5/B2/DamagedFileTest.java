import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/**
 * B2 - Adversarial bus-pass file: wrong field count, non-numeric amount, empty line,
 * negative amount, missing trailing newline (incomplete write).
 * The store validates each line, skips damaged ones and reports them.
 */
public class DamagedFileTest {

    static String[] validate(String line, int lineNo, List<String> report) {
        if (line.isBlank()) { report.add("line " + lineNo + ": empty line"); return null; }
        String[] f = line.split(",", -1);
        if (f.length != 5) { report.add("line " + lineNo + ": expected 5 fields, found " + f.length); return null; }
        try {
            Integer.parseInt(f[3].trim());
            double amt = Double.parseDouble(f[4].trim());
            if (amt < 0) { report.add("line " + lineNo + ": negative amount"); return null; }
        } catch (NumberFormatException e) {
            report.add("line " + lineNo + ": non-numeric value (" + e.getMessage() + ")");
            return null;
        }
        return f;
    }

    public static void main(String[] args) throws IOException {
        Path f = Files.createTempFile("adversarial", ".csv");
        Files.writeString(f, String.join("\n",
                "P1001,192311001,Arun,12,4500.0",
                "P1002,192311002,Divya,7",                    // wrong number of fields
                "P1003,192311003,Kumar,7,FOUR THOUSAND",      // non-numeric amount
                "",                                            // empty line
                "P1004,192311004,Lata,x9,3000",               // non-numeric route
                "P1005,192311005,Ravi,3,-50",                 // negative amount
                "P1006,192311006,Meena,4,3900.0",
                "P1007,192311007,Jo"));                        // truncated last line, no newline

        List<String> report = new ArrayList<>();
        List<String[]> good = new ArrayList<>();
        int n = 0;
        for (String line : Files.readAllLines(f)) {
            String[] rec = validate(line, ++n, report);
            if (rec != null) good.add(rec);
        }
        System.out.println("Valid records: " + good.size());
        good.forEach(r -> System.out.println("  " + Arrays.toString(r)));
        System.out.println("Rejected lines:");
        report.forEach(r -> System.out.println("  " + r));
        Files.delete(f);
    }
}
