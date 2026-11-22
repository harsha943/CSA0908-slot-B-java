import java.time.LocalDateTime;

/**
 * Task 4 - Classes, objects and constructors:
 * default, parameterised, overloaded, chained (this(...)) and copy constructors.
 */
class ParkingTicket {
    private static int nextNumber = 1000;      // shared across all tickets (Task 5)

    private final int ticketNo;
    private final String vehicleNo;
    private final int floor;
    private final int slot;
    private final LocalDateTime entryTime;
    private final boolean duplicate;

    // default constructor -> chains to the full constructor
    ParkingTicket() {
        this("UNKNOWN", 0, 0);
    }

    // overloaded: entry time defaults to now
    ParkingTicket(String vehicleNo, int floor, int slot) {
        this(vehicleNo, floor, slot, LocalDateTime.now());
    }

    // full parameterised constructor - the only place validation happens
    ParkingTicket(String vehicleNo, int floor, int slot, LocalDateTime entryTime) {
        if (vehicleNo == null || vehicleNo.isBlank())
            throw new IllegalArgumentException("vehicle number required");
        if (floor < 0 || slot < 0)
            throw new IllegalArgumentException("invalid slot position");
        this.ticketNo = nextNumber++;
        this.vehicleNo = vehicleNo;
        this.floor = floor;
        this.slot = slot;
        this.entryTime = entryTime;
        this.duplicate = false;
    }

    // copy constructor - re-issues a lost ticket keeping the SAME ticket number
    ParkingTicket(ParkingTicket original) {
        this.ticketNo = original.ticketNo;
        this.vehicleNo = original.vehicleNo;
        this.floor = original.floor;
        this.slot = original.slot;
        this.entryTime = original.entryTime;
        this.duplicate = true;
    }

    @Override public String toString() {
        return "Ticket#" + ticketNo + (duplicate ? " (DUPLICATE)" : "") + " " + vehicleNo
                + " F" + floor + "-S" + slot + " in " + entryTime.toLocalTime().withNano(0);
    }
}

public class TicketDemo {
    public static void main(String[] args) {
        ParkingTicket t1 = new ParkingTicket();
        ParkingTicket t2 = new ParkingTicket("TN01AB1111", 0, 3);
        ParkingTicket t3 = new ParkingTicket("TN02CD2222", 1, 7,
                LocalDateTime.now().minusHours(2));
        ParkingTicket copy = new ParkingTicket(t2);

        System.out.println(t1);
        System.out.println(t2);
        System.out.println(t3);
        System.out.println(copy);
        System.out.println("t2 == copy ? " + (t2 == copy) + "  (different heap objects)");

        try {
            new ParkingTicket("", 0, 0);
        } catch (IllegalArgumentException e) {
            System.out.println("Rejected invalid ticket: " + e.getMessage());
        }
    }
}
