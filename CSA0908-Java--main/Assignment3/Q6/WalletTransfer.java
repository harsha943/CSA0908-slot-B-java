/**
 * Q6 Part A - Hostel wallet transfers: a version that deadlocks and a version that cannot
 * (consistent lock ordering by account id).
 * Usage: java WalletTransfer          (safe ordered version)
 *        java WalletTransfer deadlock (reproduces the deadlock; detected after 2 s)
 */
public class WalletTransfer {

    static class Wallet {
        final int id; int balance;
        Wallet(int id, int balance) { this.id = id; this.balance = balance; }
    }

    /** DEADLOCK-PRONE: locks 'from' first, then 'to'. */
    static void unsafeTransfer(Wallet from, Wallet to, int amt) {
        synchronized (from) {
            sleep(50);                                   // makes the interleaving reliable
            synchronized (to) {
                from.balance -= amt; to.balance += amt;
            }
        }
    }

    /** SAFE: always lock the wallet with the smaller id first. */
    static void safeTransfer(Wallet from, Wallet to, int amt) {
        Wallet first = from.id < to.id ? from : to;
        Wallet second = from.id < to.id ? to : from;
        synchronized (first) {
            sleep(50);
            synchronized (second) {
                if (from.balance >= amt) { from.balance -= amt; to.balance += amt; }
            }
        }
    }

    static void sleep(long ms) { try { Thread.sleep(ms); } catch (InterruptedException ignored) {} }

    public static void main(String[] args) throws InterruptedException {
        boolean deadlock = args.length > 0 && args[0].equals("deadlock");
        Wallet a = new Wallet(1, 1000), b = new Wallet(2, 1000);

        Thread t1 = new Thread(() -> { for (int i = 0; i < 5; i++)
            if (deadlock) unsafeTransfer(a, b, 100); else safeTransfer(a, b, 100); }, "A->B");
        Thread t2 = new Thread(() -> { for (int i = 0; i < 5; i++)
            if (deadlock) unsafeTransfer(b, a, 50); else safeTransfer(b, a, 50); }, "B->A");
        t1.setDaemon(true); t2.setDaemon(true);   // let the JVM exit if they deadlock
        t1.start(); t2.start();
        t1.join(2000); t2.join(2000);

        if (t1.isAlive() || t2.isAlive()) {
            System.out.println("DEADLOCK detected: " + t1.getName() + " is " + t1.getState()
                    + ", " + t2.getName() + " is " + t2.getState());
            System.out.println("Conditions: mutual exclusion, hold-and-wait, no preemption, circular wait.");
        } else {
            System.out.println("Completed. A=" + a.balance + " B=" + b.balance
                    + " total=" + (a.balance + b.balance) + " (expected 2000)");
        }
    }
}
