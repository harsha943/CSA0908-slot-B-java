# CSA09-192524110-Assignment3 – Exceptions and Multithreading (Pair work)

- **Student A:** Pranayaa S – 192524110
- **Student B:** `<PARTNER NAME – REGISTER NUMBER>`
- **Java Version:** 21 (Eclipse Temurin)
- **Report:** [REPORT.md](REPORT.md) (individual sections labelled Student A / Student B)

Each folder contains the Java source and `output.txt` (the actual program output).

| Q | Program | Compile & run (inside the folder) | Result |
|---|---|---|---|
| Q1 | AtmSimulation.java | `javac *.java` → `java AtmSimulation test` | 6 failure paths, finally always runs, stack trace of an uncaught exception |
| Q2 | StorageDemo.java (Complete the Code), ExamPortal.java | `java StorageDemo` / `java ExamPortal` | `Saved answer01.pdf / Error: Could not store answer02.pdf / Cause: Disk quota exceeded`; chained trace |
| Q3 | SalesReport.java | `java SalesReport` | Manual close leaks 1 file, try-with-resources leaks 0; suppressed exception |
| Q4 | ScannerStations.java | `java ScannerStations` (`daemon` for the daemon test) | Thread vs Runnable, states NEW → TERMINATED, join |
| Q5 | SeatBooking.java (Complete the Code), BookingStrategies.java | `java SeatBooking` / `java BookingStrategies` | `Seats sold: 10`; race reproduced; 5 strategies timed |
| Q6 | TokenCounter.java (Complete the Code), WalletTransfer.java | `java TokenCounter` / `java WalletTransfer [deadlock]` | Sum 210; deadlock detected vs lock-ordering fix |
| Q7 | ResultProcessor.java | `java ResultProcessor` | 10,000 files with 60 injected failures, 1/2/4/8 threads |
