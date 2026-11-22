import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Task 3 - Alternative concept: free-slot count per floor + stack of free slots.
 * allocate() and release() become O(F) / O(1) instead of O(F x S).
 */
public class ParkingFacilityStack {
    private final String[][] slots;
    private final Deque<Integer>[] free;   // free slot numbers per floor

    @SuppressWarnings("unchecked")
    public ParkingFacilityStack(int[] slotsPerFloor) {
        slots = new String[slotsPerFloor.length][];
        free = new Deque[slotsPerFloor.length];
        for (int f = 0; f < slotsPerFloor.length; f++) {
            slots[f] = new String[slotsPerFloor[f]];
            free[f] = new ArrayDeque<>();
            for (int s = slotsPerFloor[f] - 1; s >= 0; s--) free[f].push(s);
        }
    }

    public int[] allocate(String vehicleNo) {
        for (int f = 0; f < free.length; f++) {         // O(F)
            if (!free[f].isEmpty()) {
                int s = free[f].pop();                   // O(1)
                slots[f][s] = vehicleNo;
                return new int[]{f, s};
            }
        }
        return null;
    }

    public boolean release(int floor, int slot) {
        if (floor < 0 || floor >= slots.length) return false;
        if (slot < 0 || slot >= slots[floor].length || slots[floor][slot] == null) return false;
        slots[floor][slot] = null;
        free[floor].push(slot);                          // O(1)
        return true;
    }

    public static void main(String[] args) {
        int floors = 5, perFloor = 100;
        int[] spec = new int[floors];
        java.util.Arrays.fill(spec, perFloor);

        ParkingFacility scan = new ParkingFacility(spec);
        ParkingFacilityStack stack = new ParkingFacilityStack(spec);
        int n = floors * perFloor;

        long t0 = System.nanoTime();
        for (int i = 0; i < n; i++) scan.allocate("V" + i);
        long t1 = System.nanoTime();
        for (int i = 0; i < n; i++) stack.allocate("V" + i);
        long t2 = System.nanoTime();

        System.out.printf("Linear scan allocate x%d : %.3f ms%n", n, (t1 - t0) / 1e6);
        System.out.printf("Stack-based allocate x%d : %.3f ms%n", n, (t2 - t1) / 1e6);
    }
}
