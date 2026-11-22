import java.util.HashMap;
import java.util.Map;

/**
 * Task 6-11 - Complete Campus Smart-Parking system with tests.
 * Inheritance, overriding, polymorphism (overloading + dynamic dispatch),
 * abstraction (abstract class + interfaces), encapsulation (private + validation).
 */
interface Chargeable { double chargingFee(int kWh); }
interface Pass { boolean isValid(); }

enum UserType { STUDENT, STAFF, VISITOR }

abstract class Vehicle {
    private final String regNo;
    private final UserType owner;
    private final boolean hasMonthlyPass;

    protected Vehicle(String regNo, UserType owner, boolean hasMonthlyPass) {
        if (regNo == null || !regNo.matches("[A-Z]{2}\\d{2}[A-Z]{0,2}\\d{1,4}"))
            throw new IllegalArgumentException("Invalid registration number: " + regNo);
        this.regNo = regNo;
        this.owner = owner;
        this.hasMonthlyPass = hasMonthlyPass;
    }

    public final String getRegNo() { return regNo; }
    public UserType getOwner() { return owner; }

    protected abstract double ratePerHour();
    protected double dailyMax() { return ratePerHour() * 8; }

    /** Overloaded (compile-time polymorphism): minutes. */
    public double calculateFee(int minutes) {
        if (minutes < 0) throw new IllegalArgumentException("Negative duration");
        if (hasMonthlyPass && owner != UserType.VISITOR) return 0;   // pass holders park free
        if (minutes == 0) return 0;
        int days = minutes / (24 * 60);
        int rest = minutes % (24 * 60);
        int hours = (rest + 59) / 60;                                 // every started hour
        double fee = days * dailyMax() + Math.min(hours * ratePerHour(), dailyMax());
        return fee;
    }

    /** Overloaded: entry and exit time. */
    public double calculateFee(java.time.LocalTime in, java.time.LocalTime out) {
        return calculateFee((int) java.time.Duration.between(in, out).toMinutes());
    }

    @Override public boolean equals(Object o) {
        return o instanceof Vehicle v && regNo.equals(v.regNo);
    }
    @Override public int hashCode() { return regNo.hashCode(); }
    @Override public String toString() { return getClass().getSimpleName() + "[" + regNo + "]"; }
}

class TwoWheeler extends Vehicle {
    TwoWheeler(String r, UserType u, boolean p) { super(r, u, p); }
    @Override protected double ratePerHour() { return 10; }
}

class Car extends Vehicle {
    Car(String r, UserType u, boolean p) { super(r, u, p); }
    @Override protected double ratePerHour() { return 30; }
}

final class ElectricCar extends Car implements Chargeable {
    private final int kWh;
    ElectricCar(String r, UserType u, boolean p, int kWh) {
        super(r, u, p);
        System.out.println("  [ctor] ElectricCar body runs last for " + r);
        this.kWh = kWh;
    }
    @Override public double chargingFee(int units) { return 12.0 * units; }
    int getKWh() { return kWh; }
}

final class ParkingTicket {
    private static int counter = 1;
    private final int ticketNo;
    private final Vehicle vehicle;
    private final int floor, slot;

    ParkingTicket(Vehicle v, int floor, int slot) {
        this.ticketNo = counter++;
        this.vehicle = v; this.floor = floor; this.slot = slot;
    }
    int getTicketNo() { return ticketNo; }
    Vehicle getVehicle() { return vehicle; }
    int getFloor() { return floor; }
    int getSlot() { return slot; }
}

class ParkingLot {
    private final Vehicle[][] slots;                          // jagged array
    private final Map<String, ParkingTicket> active = new HashMap<>();
    private double collection = 0;

    ParkingLot(int... slotsPerFloor) {
        slots = new Vehicle[slotsPerFloor.length][];
        for (int f = 0; f < slotsPerFloor.length; f++) slots[f] = new Vehicle[slotsPerFloor[f]];
    }

    ParkingTicket enter(Vehicle v) {
        if (active.containsKey(v.getRegNo()))
            throw new IllegalStateException("Duplicate registration already parked: " + v.getRegNo());
        for (int f = 0; f < slots.length; f++)
            for (int s = 0; s < slots[f].length; s++)
                if (slots[f][s] == null) {
                    slots[f][s] = v;
                    ParkingTicket t = new ParkingTicket(v, f, s);
                    active.put(v.getRegNo(), t);
                    return t;
                }
        return null;                                          // full
    }

    double exit(String regNo, int minutes) {
        ParkingTicket t = active.remove(regNo);
        if (t == null) throw new IllegalArgumentException("No active ticket for " + regNo);
        slots[t.getFloor()][t.getSlot()] = null;
        Vehicle v = t.getVehicle();
        double fee = v.calculateFee(minutes);                 // dynamic dispatch
        if (v instanceof ElectricCar ec) fee += ec.chargingFee(ec.getKWh());
        collection += fee;
        return fee;
    }

    boolean release(int floor, int slot) {
        if (floor < 0 || floor >= slots.length || slot < 0 || slot >= slots[floor].length) return false;
        if (slots[floor][slot] == null) return false;
        active.remove(slots[floor][slot].getRegNo());
        slots[floor][slot] = null;
        return true;
    }

    double getCollection() { return collection; }
}

public class SmartParking {
    static int pass = 0, fail = 0;

    static void check(String name, Object expected, Object actual) {
        boolean ok = String.valueOf(expected).equals(String.valueOf(actual));
        if (ok) pass++; else fail++;
        System.out.printf("%-45s expected=%-10s actual=%-10s %s%n", name, expected, actual, ok ? "PASS" : "FAIL");
    }

    static void expectThrows(String name, Runnable r) {
        try { r.run(); check(name, "exception", "none"); }
        catch (RuntimeException e) { check(name, "exception", "exception"); }
    }

    public static void main(String[] args) {
        System.out.println("Constructor trace for ElectricCar:");
        Vehicle ev = new ElectricCar("TN03C3", UserType.VISITOR, false, 10);

        ParkingLot lot = new ParkingLot(2, 1);                 // 3 slots
        Vehicle bike = new TwoWheeler("TN01A1", UserType.VISITOR, false);
        Vehicle car  = new Car("TN02B2", UserType.VISITOR, false);
        Vehicle staff = new Car("TN09S9", UserType.STAFF, true);

        System.out.println("\n--- Normal operation ---");
        check("bike gets F0-S0", "0-0", slotOf(lot.enter(bike)));
        check("car gets F0-S1", "0-1", slotOf(lot.enter(car)));
        check("EV gets last free slot F1-S0", "1-0", slotOf(lot.enter(ev)));
        check("lot full returns null", null, lot.enter(staff));
        check("bike 130 min fee", 30.0, lot.exit("TN01A1", 130));
        check("car 60 min (slab boundary) fee", 30.0, lot.exit("TN02B2", 60));
        check("EV 130 min + 10 kWh", 210.0, lot.exit("TN03C3", 130));

        System.out.println("\n--- Boundary cases ---");
        check("zero-minute parking", 0.0, car.calculateFee(0));
        check("61 minutes = 2 hours", 60.0, car.calculateFee(61));
        check("daily max cap (10 h car)", 240.0, car.calculateFee(600));
        check("staff with pass parks free", 0.0, staff.calculateFee(300));

        System.out.println("\n--- Invalid cases ---");
        lot.enter(car);
        expectThrows("duplicate registration", () -> lot.enter(new Car("TN02B2", UserType.VISITOR, false)));
        expectThrows("invalid reg number", () -> new Car("12345", UserType.VISITOR, false));
        expectThrows("negative duration", () -> car.calculateFee(-5));
        check("invalid slot release", false, lot.release(9, 0));
        expectThrows("exit unknown vehicle", () -> lot.exit("TN77Z7", 10));

        System.out.println("\n--- Day collection (hand-calculated: 30 + 30 + 210 = 270) ---");
        check("total collection", 270.0, lot.getCollection());

        System.out.println("\nPassed " + pass + ", failed " + fail);
    }

    static String slotOf(ParkingTicket t) { return t == null ? "null" : t.getFloor() + "-" + t.getSlot(); }
}
