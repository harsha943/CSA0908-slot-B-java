import java.io.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

/**
 * A4 - What should be serialized? Hostel -> Rooms -> Residents.
 * password and the live database Connection are transient; serialVersionUID is fixed
 * so that adding a field later does not make old files unreadable.
 */
public class HostelSerialization {

    static class Resident implements Serializable {
        private static final long serialVersionUID = 1L;   // keep constant across compatible versions
        final String name;
        final String regNo;
        transient String password;                          // secret: never written to disk
        transient java.sql.Connection db;                   // not serializable, meaningless after restart
        String phone;                                       // field added in version 2: old files -> null

        Resident(String name, String regNo, String password) {
            this.name = name; this.regNo = regNo; this.password = password;
        }
        @Override public String toString() {
            return name + "(" + regNo + ", pwd=" + password + ", phone=" + phone + ")";
        }
    }

    static class Room implements Serializable {
        private static final long serialVersionUID = 1L;
        final int number;
        final List<Resident> residents = new ArrayList<>();
        Room(int number) { this.number = number; }
        @Override public String toString() { return "Room " + number + " " + residents; }
    }

    static class Hostel implements Serializable {
        private static final long serialVersionUID = 1L;
        final String name;
        final List<Room> rooms = new ArrayList<>();
        Hostel(String name) { this.name = name; }
    }

    public static void main(String[] args) throws Exception {
        Hostel h = new Hostel("Vaigai Hostel");
        Resident shared = new Resident("Arun", "192311001", "secret");
        Room r1 = new Room(101), r2 = new Room(102);
        r1.residents.add(shared);
        r1.residents.add(new Resident("Kumar", "192311003", "pass1"));
        r2.residents.add(new Resident("Ravi", "192311004", "pass2"));
        r2.residents.add(shared);                           // same object referenced twice
        h.rooms.add(r1); h.rooms.add(r2);

        Path f = Files.createTempFile("hostel", ".ser");
        try (ObjectOutputStream out = new ObjectOutputStream(new BufferedOutputStream(Files.newOutputStream(f)))) {
            out.writeObject(h);                             // whole object graph is written
        }
        System.out.println("Serialized size: " + Files.size(f) + " bytes");

        Hostel back;
        try (ObjectInputStream in = new ObjectInputStream(new BufferedInputStream(Files.newInputStream(f)))) {
            back = (Hostel) in.readObject();
        }
        back.rooms.forEach(System.out::println);
        System.out.println("Shared resident still one object after reload? "
                + (back.rooms.get(0).residents.get(0) == back.rooms.get(1).residents.get(1)));

        // CSV alternative for comparison
        Path csv = Files.createTempFile("hostel", ".csv");
        List<String> lines = new ArrayList<>(List.of("room,name,regNo,phone"));
        for (Room r : h.rooms) for (Resident res : r.residents)
            lines.add(r.number + "," + res.name + "," + res.regNo + "," + (res.phone == null ? "" : res.phone));
        Files.write(csv, lines);
        System.out.println("CSV size: " + Files.size(csv) + " bytes");

        Files.delete(f); Files.delete(csv);
    }
}
