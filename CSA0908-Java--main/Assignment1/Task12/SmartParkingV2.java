import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Task 12 - Day-4 design challenge (MODIFY decision).
 * Pricing is moved into a PricingRule abstraction so new vehicle types and rules
 * are added by writing NEW classes, not by editing tested ones.
 * New: EBike (flat fee), DeliveryVan (double car rate 9 AM-5 PM), one-day pass,
 * 5 floors with different slot counts (jagged array).
 */
interface PricingRule {
    double fee(LocalTime in, int minutes);
}

class HourlyRule implements PricingRule {
    private final double rate;
    HourlyRule(double rate) { this.rate = rate; }
    public double fee(LocalTime in, int minutes) { return ((minutes + 59) / 60) * rate; }
}

class FlatRule implements PricingRule {
    private final double amount;
    FlatRule(double amount) { this.amount = amount; }
    public double fee(LocalTime in, int minutes) { return minutes == 0 ? 0 : amount; }
}

/** Double rate for every started hour that begins between 09:00 and 17:00. */
class PeakHourRule implements PricingRule {
    private final double base;
    PeakHourRule(double base) { this.base = base; }
    public double fee(LocalTime in, int minutes) {
        int hours = (minutes + 59) / 60;
        double total = 0;
        LocalTime t = in;
        for (int h = 0; h < hours; h++) {
            boolean peak = !t.isBefore(LocalTime.of(9, 0)) && t.isBefore(LocalTime.of(17, 0));
            total += peak ? base * 2 : base;
            t = t.plusHours(1);
        }
        return total;
    }
}

interface ParkingPass { boolean coversToday(); }

class MonthlyPass implements ParkingPass { public boolean coversToday() { return true; } }

class OneDayPass implements ParkingPass {
    private final java.time.LocalDate day;
    OneDayPass(java.time.LocalDate day) { this.day = day; }
    public boolean coversToday() { return day.equals(java.time.LocalDate.now()); }
}

abstract class Vehicle2 {
    private final String regNo;
    private ParkingPass pass;
    protected Vehicle2(String regNo) { this.regNo = regNo; }
    String getRegNo() { return regNo; }
    void setPass(ParkingPass p) { this.pass = p; }
    protected abstract PricingRule rule();
    final double calculateFee(LocalTime in, int minutes) {
        if (pass != null && pass.coversToday()) return 0;
        return rule().fee(in, minutes);
    }
}

class TwoWheeler2 extends Vehicle2 {
    TwoWheeler2(String r) { super(r); }
    protected PricingRule rule() { return new HourlyRule(10); }
}
class Car2 extends Vehicle2 {
    Car2(String r) { super(r); }
    protected PricingRule rule() { return new HourlyRule(30); }
}
// ---- NEW classes only: existing ones untouched ----
class EBike extends Vehicle2 {
    EBike(String r) { super(r); }
    protected PricingRule rule() { return new FlatRule(20); }
}
class DeliveryVan extends Vehicle2 {
    DeliveryVan(String r) { super(r); }
    protected PricingRule rule() { return new PeakHourRule(30); }
}

public class SmartParkingV2 {
    public static void main(String[] args) {
        int[] slotsPerFloor = {40, 60, 50, 80, 30};             // 5 floors, different sizes
        Vehicle2[][] floors = new Vehicle2[slotsPerFloor.length][];
        for (int f = 0; f < floors.length; f++) floors[f] = new Vehicle2[slotsPerFloor[f]];
        int total = 0;
        for (Vehicle2[] fl : floors) total += fl.length;
        System.out.println("Floors: " + floors.length + ", total slots: " + total);

        Vehicle2 visitor = new Car2("TN10V1");
        visitor.setPass(new OneDayPass(java.time.LocalDate.now()));

        List<Object[]> tests = new ArrayList<>();
        tests.add(new Object[]{new EBike("TN11E1"), LocalTime.of(10, 0), 300, 20.0});
        tests.add(new Object[]{new EBike("TN11E2"), LocalTime.of(10, 0), 0, 0.0});
        tests.add(new Object[]{new DeliveryVan("TN12D1"), LocalTime.of(10, 0), 120, 120.0}); // 2 peak hours
        tests.add(new Object[]{new DeliveryVan("TN12D2"), LocalTime.of(16, 0), 120, 90.0});  // 1 peak + 1 off
        tests.add(new Object[]{new DeliveryVan("TN12D3"), LocalTime.of(7, 0), 60, 30.0});    // off-peak
        tests.add(new Object[]{new Car2("TN13C1"), LocalTime.of(10, 0), 130, 90.0});
        tests.add(new Object[]{visitor, LocalTime.of(10, 0), 300, 0.0});                    // one-day pass

        int ok = 0;
        for (Object[] t : tests) {
            Vehicle2 v = (Vehicle2) t[0];
            double fee = v.calculateFee((LocalTime) t[1], (Integer) t[2]);
            boolean pass = fee == (Double) t[3];
            if (pass) ok++;
            System.out.printf("%-12s %-7s in %s for %3d min -> expected %6.1f actual %6.1f %s%n",
                    v.getClass().getSimpleName(), v.getRegNo(), t[1], t[2], t[3], fee, pass ? "PASS" : "FAIL");
        }
        System.out.println(ok + "/" + tests.size() + " tests passed");
    }
}
