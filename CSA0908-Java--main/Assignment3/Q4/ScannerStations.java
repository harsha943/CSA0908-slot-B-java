/**
 * Q4 - Parallel answer-script scanning: extends Thread vs implements Runnable,
 * start/sleep/join/isAlive, run() vs start(), priorities and daemon threads.
 * Usage: java ScannerStations [daemon]
 */
public class ScannerStations {

    static int total = 0;
    static synchronized void addToTotal(int n) { total += n; }

    /** Style 1: extend Thread. */
    static class StationThread extends Thread {
        private final int scripts;
        StationThread(String name, int scripts) { super(name); this.scripts = scripts; }
        @Override public void run() {
            for (int i = 0; i < scripts; i++) {
                try { Thread.sleep(5 + (int) (Math.random() * 10)); }      // TIMED_WAITING
                catch (InterruptedException e) { return; }
            }
            addToTotal(scripts);
            System.out.println("  " + getName() + " finished " + scripts + " scripts on " + Thread.currentThread().getName());
        }
    }

    /** Style 2: implement Runnable (class stays free to extend something else). */
    static class StationTask implements Runnable {
        private final int scripts;
        StationTask(int scripts) { this.scripts = scripts; }
        @Override public void run() {
            for (int i = 0; i < scripts; i++) {
                try { Thread.sleep(5 + (int) (Math.random() * 10)); }
                catch (InterruptedException e) { return; }
            }
            addToTotal(scripts);
            System.out.println("  " + Thread.currentThread().getName() + " finished " + scripts + " scripts");
        }
    }

    public static void main(String[] args) throws InterruptedException {
        System.out.println("run() vs start():");
        new StationThread("Direct-run", 1).run();      // executes on 'main' - no new thread
        total = 0;

        System.out.println("\nStyle 1 - extends Thread:");
        StationThread[] s = new StationThread[4];
        for (int i = 0; i < 4; i++) s[i] = new StationThread("Station-" + (i + 1), 10 + i * 5);
        System.out.println("  state before start: " + s[0].getState());                // NEW
        s[0].setPriority(Thread.MAX_PRIORITY);
        s[3].setPriority(Thread.MIN_PRIORITY);
        for (StationThread t : s) t.start();
        System.out.println("  Station-1 alive? " + s[0].isAlive() + ", state " + s[0].getState());
        for (StationThread t : s) t.join();                                            // wait for all
        System.out.println("  after join: alive? " + s[0].isAlive() + ", state " + s[0].getState()); // TERMINATED
        System.out.println("  Total scanned = " + total);

        total = 0;
        System.out.println("\nStyle 2 - implements Runnable:");
        Thread[] r = new Thread[4];
        for (int i = 0; i < 4; i++) { r[i] = new Thread(new StationTask(10 + i * 5), "Runner-" + (i + 1)); r[i].start(); }
        for (Thread t : r) t.join();
        System.out.println("  Total scanned = " + total);

        if (args.length > 0 && args[0].equals("daemon")) {
            Thread d = new Thread(new StationTask(1000), "Daemon-Station");
            d.setDaemon(true);
            d.start();
            System.out.println("\nDaemon station started; main ends now -> JVM exits without waiting for it.");
        }
    }
}
