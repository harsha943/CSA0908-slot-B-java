/**
 * Task 5 - static members and object life cycle.
 * Compares a static ticket counter with an instance counter when two facilities
 * (north and south gates) exist.
 */
class Facility {
    static int globalTicketCounter;          // one copy for the whole program
    static final double GST;
    int instanceTicketCounter;               // one copy per facility -> duplicates!
    final String name;

    static {                                 // static block: runs once when class loads
        globalTicketCounter = 1;
        GST = 0.18;
        System.out.println("[static block] Facility class loaded");
    }

    Facility(String name) { this.name = name; }

    int issueGlobal()   { return globalTicketCounter++; }
    int issueInstance() { return ++instanceTicketCounter; }

    static double withTax(double fee) { return fee + fee * GST; }   // static method
}

public class StaticDemo {
    public static void main(String[] args) {
        Facility north = new Facility("North");
        Facility south = new Facility("South");

        System.out.println("Static counter   : North " + north.issueGlobal()
                + ", South " + south.issueGlobal() + ", North " + north.issueGlobal());
        System.out.println("Instance counter : North " + north.issueInstance()
                + ", South " + south.issueInstance() + "  <- ticket number reused!");

        System.out.println("Fee 100 with tax : " + Facility.withTax(100));

        // object life cycle: ticket becomes unreachable once the vehicle leaves
        Object ticket = new Object();
        System.out.println("Ticket created   : " + ticket.hashCode());
        ticket = null;   // no reference -> eligible for garbage collection
        System.gc();     // only a request; JVM decides when to collect
        System.out.println("Ticket reference cleared; object is eligible for GC");
    }
}
