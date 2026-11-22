import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Q5 - Reproduce the race and compare synchronization strategies on 10,000 bookings.
 */
public class BookingStrategies {

    interface Booker { boolean book(int seat); int sold(); }

    /** Seat check-then-set is NOT atomic; tickets issued are counted atomically so every double sale is visible. */
    static class Unsafe implements Booker {
        final boolean[] b; final AtomicInteger ticketsIssued = new AtomicInteger();
        Unsafe(int n) { b = new boolean[n]; }
        public boolean book(int s) {
            if (!b[s]) { Thread.yield(); b[s] = true; ticketsIssued.incrementAndGet(); return true; }
            return false;
        }
        public int sold() { return ticketsIssued.get(); }
    }

    static class SyncMethod implements Booker {
        final boolean[] b; int sold;
        SyncMethod(int n) { b = new boolean[n]; }
        public synchronized boolean book(int s) {
            if (!b[s]) { b[s] = true; sold++; return true; }
            return false;
        }
        public synchronized int sold() { return sold; }
    }

    static class SyncBlock implements Booker {
        final boolean[] b; int sold;
        final Object lock = new Object();
        SyncBlock(int n) { b = new boolean[n]; }
        public boolean book(int s) {
            if (s < 0 || s >= b.length) return false;        // validation outside the lock
            synchronized (lock) {                            // only the critical section is locked
                if (b[s]) return false;
                b[s] = true; sold++;
            }
            return true;
        }
        public int sold() { synchronized (lock) { return sold; } }
    }

    static class Atomic implements Booker {
        final AtomicBoolean[] b; final AtomicInteger sold = new AtomicInteger();
        Atomic(int n) { b = new AtomicBoolean[n]; for (int i = 0; i < n; i++) b[i] = new AtomicBoolean(); }
        public boolean book(int s) {
            if (b[s].compareAndSet(false, true)) { sold.incrementAndGet(); return true; }
            return false;
        }
        public int sold() { return sold.get(); }
    }

    static class Locked implements Booker {
        final boolean[] b; int sold; final ReentrantLock lock = new ReentrantLock();
        Locked(int n) { b = new boolean[n]; }
        public boolean book(int s) {
            lock.lock();
            try { if (b[s]) return false; b[s] = true; sold++; return true; }
            finally { lock.unlock(); }
        }
        public int sold() { return sold; }
    }

    static class MapBased implements Booker {
        final ConcurrentHashMap<Integer, Boolean> m = new ConcurrentHashMap<>();
        public boolean book(int s) { return m.putIfAbsent(s, Boolean.TRUE) == null; }
        public int sold() { return m.size(); }
    }

    static int[] run(Booker bk, int seats, int bookings, int threads) throws InterruptedException {
        ExecutorService ex = Executors.newFixedThreadPool(threads);
        CountDownLatch go = new CountDownLatch(1);
        long t0 = System.nanoTime();
        for (int i = 0; i < bookings; i++) {
            final int seat = i % seats;
            ex.submit(() -> { try { go.await(); } catch (InterruptedException e) { return; } bk.book(seat); });
        }
        go.countDown();
        ex.shutdown();
        ex.awaitTermination(1, TimeUnit.MINUTES);
        return new int[]{bk.sold(), (int) ((System.nanoTime() - t0) / 1_000_000)};
    }

    public static void main(String[] args) throws InterruptedException {
        int seats = 10, bookings = 10_000, threads = 16;   // 1,000 users compete for every seat

        System.out.println("Race reproduction (unsafe), 10 runs, " + seats + " seats:");
        for (int r = 1; r <= 10; r++) {
            int[] res = run(new Unsafe(seats), seats, bookings, threads);
            System.out.println("  run " + r + ": sold " + res[0] + " -> double bookings " + (res[0] - seats));
        }

        System.out.printf("%n%-22s %6s %8s%n", "Strategy", "sold", "ms");
        String[] names = {"synchronized method", "synchronized block", "AtomicBoolean CAS", "ReentrantLock", "ConcurrentHashMap"};
        for (int i = 0; i < names.length; i++) {
            Booker b = switch (i) {
                case 0 -> new SyncMethod(seats);
                case 1 -> new SyncBlock(seats);
                case 2 -> new Atomic(seats);
                case 3 -> new Locked(seats);
                default -> new MapBased();
            };
            int[] res = run(b, seats, bookings, threads);
            System.out.printf("%-22s %6d %8d%n", names[i], res[0], res[1]);
        }
    }
}
