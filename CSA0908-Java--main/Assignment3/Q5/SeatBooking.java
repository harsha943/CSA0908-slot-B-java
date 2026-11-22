public class SeatBooking {
    private final boolean[] booked;
    private int bookingsMade = 0;
    SeatBooking(int seats) { booked = new boolean[seats]; }

    // TODO 1 (completed): check-then-book made atomic with the intrinsic lock of 'this'.
    // (For the "before" run, remove the word synchronized and run 10 times.)
    public synchronized boolean book(int seat) {
        if (!booked[seat]) {
            Thread.yield();             // widens the race window when unsynchronized
            booked[seat] = true;
            bookingsMade++;
            return true;
        }
        return false;
    }

    public static void main(String[] args) throws InterruptedException {
        SeatBooking screen = new SeatBooking(10);
        Thread[] users = new Thread[100];
        for (int i = 0; i < users.length; i++) {
            final int seat = i % 10; // 10 users compete for every seat
            users[i] = new Thread(() -> screen.book(seat));
            users[i].start();
        }
        for (Thread t : users) t.join();
        System.out.println("Seats sold: " + screen.bookingsMade);
    }
}
