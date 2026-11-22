# CSA09 – Programming in Java – Assignment 3 (CO3)
## Exception Handling and Multithreading (Pair Work)

| | |
|---|---|
| **Student A** | Pranayaa S – 192524110 |
| **Student B** | `<PARTNER NAME – REGISTER NUMBER>` |
| **Repository** | CSA09-192524110-Assignment3 |
| **Java Version** | 21 (Eclipse Temurin) |

> Pair-work note: both students worked on every question. Sections marked **Student A** / **Student B** show the individual responsibility for each question. Replace the Student B line with your partner's details.

---

## Q1 – Handling Failure in a Bank ATM Simulation (6 Marks) – `Q1/AtmSimulation.java`

### Shared work – failure points
| # | Failure point | Exception | Kind | Caught in |
|---|---|---|---|---|
| 1 | Card-reader file missing | `FileNotFoundException` | checked | `catch (FileNotFoundException)` |
| 2 | Card file unreadable | `IOException` | checked | `catch (IOException)` |
| 3 | Card data not numeric | `NumberFormatException` | unchecked | multi-catch |
| 4 | Letters typed for the amount | `InputMismatchException` | unchecked | multi-catch |
| 5 | Negative / non-multiple-of-100 amount | `IllegalArgumentException` (thrown by us) | unchecked | `catch (IllegalArgumentException)` |
| 6 | Amount > balance | `InsufficientFundsException` (user-defined) | checked | `catch (InsufficientFundsException)` |
| 7 | Out of memory / stack overflow | `OutOfMemoryError`, `StackOverflowError` | Error | not caught – cannot be recovered |

### Student A – Exception design
```
Throwable
├── Error ─────────────── OutOfMemoryError, StackOverflowError   (not handled)
└── Exception
    ├── IOException ───── FileNotFoundException                  (checked)
    ├── InsufficientFundsException (ours)                        (checked)
    └── RuntimeException                                         (unchecked)
        ├── IllegalArgumentException ── NumberFormatException
        └── NoSuchElementException ──── InputMismatchException
```
- **Catch order:** narrower before broader – `FileNotFoundException` before `IOException`, and `NumberFormatException` (in the multi-catch) before its parent `IllegalArgumentException`. Reversing makes the narrow block unreachable → compile error.
- **finally** guarantees the Scanner is closed and "card returned" is printed on every path – success, any caught exception, or an uncaught one.

### Student B – Testing and tracing
| Input | Path / catch block | finally ran? |
|---|---|---|
| `2000` | success, dispensed | yes |
| `abc` | multi-catch (InputMismatchException) | yes |
| `-500` | IllegalArgumentException | yes |
| `50000` | InsufficientFundsException | yes |
| missing card file | FileNotFoundException | yes |
| card `12AB` | multi-catch (NumberFormatException) | yes |

Uncaught exception (`new Scanner("").nextInt()`):
```
Exception in thread "main" java.util.NoSuchElementException     ← type of exception
    at java.base/java.util.Scanner.throwFor(Scanner.java:...)    ← where it was thrown (deepest frame)
    at java.base/java.util.Scanner.next(Scanner.java:...)
    at java.base/java.util.Scanner.nextInt(Scanner.java:...)
    at AtmSimulation.main(AtmSimulation.java:...)                ← our line that called it
```
Each line is one stack frame, innermost first. Placing `catch (IOException e)` before `catch (FileNotFoundException e)` gives *"exception FileNotFoundException has already been caught"* (compile error).

### Joint design question
A single `catch (Exception e)` is acceptable only at the **outermost boundary** (e.g. to log and show "Transaction failed" so the ATM never crashes). Lost: the ability to give a specific message, to recover differently (retry card read vs ask for a new amount), and it silently swallows programming bugs (NullPointerException) that should be fixed.

### Alternative – no exceptions, error codes
```java
if (!sc.hasNextInt()) return -1;  int a = sc.nextInt();  if (a <= 0) return -2;  if (a > bal) return -3;
```
Every caller must check every code → more `if`s, easy to ignore. Time: returning a code is O(1) and almost free; creating an exception captures a stack trace – O(depth) time and space (~µs). Exceptions are for *exceptional* paths, so the cost is acceptable.

---

## Q2 – User-Defined Exceptions and Propagation (7 Marks) – `Q2/StorageDemo.java`, `Q2/ExamPortal.java`

### Student A – Hierarchy and propagation
```
Exception
└── PortalException (checked)
    ├── ExamWindowClosedException  – expected business rule → checked: caller must tell the student
    ├── NotRegisteredException     – checked
    ├── FileTooLargeException      – checked
    └── StorageFailureException    – checked, wraps IOException (cause)
RuntimeException
└── InvalidSubmissionError         – unchecked: programming bug (null id), should not be caught routinely
```
Propagation of a storage failure:
```
StorageLayer.write()   IOException thrown → caught → throw new StorageFailureException(msg, e)
      ↓ throws
ExamService.submit()   declares "throws PortalException" – does not handle
      ↓ throws
Controller.handle()    catch (StorageFailureException) → "Server problem, please retry."
```
The student sees a friendly message; the cause ("No space left on device") is logged for the administrator.

### Student B – Scenarios and chaining
| Scenario | Exception | Student sees |
|---|---|---|
| valid | – | Submission accepted |
| not registered | NotRegisteredException | Rejected: … not registered |
| 900 KB file | FileTooLargeException | Rejected: File 900 KB exceeds 500 KB |
| disk full | StorageFailureException (cause IOException) | Server problem, please retry |
| after deadline | ExamWindowClosedException | Rejected: Exam window closed |

The chained stack trace shows `Caused by: java.io.IOException: No space left on device`. Re-thrown **without** the cause, `getCause()` is `null` and the "Caused by" section disappears – the root reason and its line number are lost.

### Complete the Code – output
```
Saved answer01.pdf
Error: Could not store answer02.pdf
Cause: Disk quota exceeded
```
If the IOException were not passed to the constructor, `e.getCause()` would be `null` and the Cause line would throw a **NullPointerException**.

### Joint conclusion
A `SubmissionStorageException` speaks the language of the portal (what failed: storing a submission), hides implementation details (files vs database), lets the UI decide a meaningful response, and still keeps the IOException as its cause for debugging. A raw IOException leaks low-level details and couples the UI to the storage technology.

### Alternative – unchecked custom exceptions
Signatures lose `throws ...`; callers are not forced to handle them (less boilerplate, more risk of forgetting). Cost is the same: creating any Throwable fills the stack trace – O(stack depth) time and memory; a chained exception holds its own trace **plus** the cause's trace (memory ≈ sum of both).

---

## Q3 – Resource Safety, Debugging and Robust Code (7 Marks) – `Q3/SalesReport.java`

### Shared work
Both versions produce the identical report for good input (`same report? true`).

### Student A – Debugging and experiment
- Breakpoint on `double amt = Double.parseDouble(...)` in `manualVersion`: stepping into the corrupt line `sugar,ABC` throws, control jumps to the outer `finally` (which closes only the writer) – `r.close()` is **skipped**.
- Failing input: store4.csv with line `sugar,ABC`, inserted as the 2nd file.
- Files left open (counted by `TrackedReader`): **manual = 1, try-with-resources = 0**.
- Logging: `LOG.warning("Corrupt data in store4.csv: For input string: \"ABC\"")` records the failing file.

### Student B – Design and correctness
- Resources: one `BufferedWriter` (summary file) and one `BufferedReader` per store file – each must be closed on every path.
- Manual version leaks because `close()` is an ordinary statement after code that can throw; only the writer is in `finally`.
- try-with-resources calls `close()` automatically when the block exits normally or by exception, in **reverse order of opening** (reader before writer).
- **Suppressed exception:** if the body throws and `close()` also throws, the close exception is attached to the primary one with `addSuppressed` (program output: `Primary: body failed, suppressed: [IllegalStateException: close failed]`).

### Joint engineering decision
try-with-resources is not enough when: a failure in one file should not abort the whole report (need per-file catch and continue), the output must be atomic (write to a temp file then `Files.move` with ATOMIC_MOVE), data is corrupt (validation), or the program must retry or report partial results.

### Alternative
| Version | Processing time for f files | Max handles open at failure |
|---|---|---|
| try-with-resources | O(total lines) | 1 reader + 1 writer, then 0 |
| explicit close in finally (nested per file) | O(total lines) | same if written correctly – but verbose and error-prone |
| no closing | O(total lines) | grows to f (one per file per run) → "Too many open files" |

---

## Q4 – Thread Creation and Life Cycle (7 Marks) – `Q4/ScannerStations.java`

### Student A – Thread design
- `extends Thread`: simple, but uses up the single superclass and mixes the task with the thread. `implements Runnable`: task separate from thread, can extend another class, can be given to an executor – **preferred**.
- Life cycle: **NEW** → `start()` → **RUNNABLE** → `sleep()` → **TIMED_WAITING** → RUNNABLE → (waiting for the `synchronized addToTotal` lock → **BLOCKED**) → **TERMINATED**. The program prints NEW before start and TERMINATED after join.
- `run()` instead of `start()`: the "Direct-run" station prints `on main` – the code runs in the calling thread, no new thread.
- `join()` makes `main` wait until each station terminates, so the total is printed only after all are done.

### Student B – Behavioural experiments
- 10 runs: completion order of stations varies run-to-run (record in `Q4/output.txt` runs).
- Priorities (MAX for Station-1, MIN for Station-4) are only hints to the OS scheduler – on Windows/Linux the order rarely changes because the threads mostly sleep.
- Daemon (`java ScannerStations daemon`): the JVM exits as soon as `main` ends; the daemon station is killed without finishing.
- Explanation: the OS scheduler decides which runnable thread runs on which core and for how long; sleep durations are random.

### Joint conclusion
Thread scheduling is non-deterministic (core count, OS load, timer resolution), so output order varies. A correct program must rely only on explicit synchronization (`join`, locks, queues), never on observed timing.

### Alternative – ExecutorService
```java
ExecutorService pool = Executors.newFixedThreadPool(p);
for (Batch b : batches) pool.submit(new StationTask(b)); pool.shutdown(); pool.awaitTermination(...);
```
Completion ≈ ⌈n/p⌉ batch-times in both designs; the pool reuses p threads (memory p × ~1 MB stack) instead of creating one thread per task, and offers Futures and clean shutdown.

---

## Q5 – Race Conditions in Cinema Seat Booking (6 Marks) – `Q5/SeatBooking.java`, `Q5/BookingStrategies.java`

### Complete the Code
Unsynchronized: "Seats sold" can exceed 10 – in `BookingStrategies` (10 seats, 10,000 bookings, 16 threads) the unsafe version sold 14, 16, 11, 10, 10… over 10 runs (up to 6 double bookings). Synchronized: always `Seats sold: 10`.

### Student A – Producing the race
Test: 10 threads per seat, released simultaneously, with `Thread.yield()` between check and set. Interleaving:
```
T1: if (!booked[3])  → true
T2: if (!booked[3])  → true          ← both saw the seat free
T1: booked[3] = true; bookingsMade++
T2: booked[3] = true; bookingsMade++  ← seat 3 sold twice
```
Critical section: the check `if (!booked[seat])` **and** the update `booked[seat]=true; bookingsMade++` (also `bookingsMade++` alone is read-modify-write and races).

### Student B – Comparing strategies (10,000 bookings, 16 threads)
| Strategy | Lock | Notes |
|---|---|---|
| synchronized method | intrinsic lock of `this` | simplest; locks validation too |
| synchronized block | intrinsic lock of a private `lock` object | only the critical section is locked → shorter hold time |
| AtomicBoolean CAS | none (lock-free) | per-seat, no contention between different seats |
| ReentrantLock | explicit lock | tryLock/timeouts/fairness possible |
| ConcurrentHashMap.putIfAbsent | striped internal locks/CAS | scales, dynamic seat set |
Measured (10,000 bookings): synchronized method 16 ms, synchronized block 15 ms, AtomicBoolean 8 ms, ReentrantLock 5 ms, ConcurrentHashMap 9 ms – all sold exactly 10. **Static synchronization** (`static synchronized`) locks the `Class` object – needed only if shared data is static (e.g. a cinema-wide ticket counter across all screen objects).

### Joint decision – 20 screens
One `SeatBooking` object per screen (independent locks), per-seat CAS (`AtomicBoolean[]` or `putIfAbsent`) for booking, so customers on different seats/screens never wait for each other. Synchronized block is acceptable if bookings are infrequent.

### Alternative complexity
| Approach | Time per booking under contention | Extra space for s seats |
|---|---|---|
| synchronized | O(1) + waiting for the single lock (serialised) | 0 |
| AtomicBoolean[] CAS | O(1), retries only on the same seat | s objects (~16 B each) |
| ReentrantLock | O(1) + queueing | 1 lock object |
| ConcurrentHashMap | O(1) average | entry per booked seat |

---

## Q6 – Deadlock and Inter-Thread Communication (7 Marks) – `Q6/TokenCounter.java`, `Q6/WalletTransfer.java`

### Complete the Code
```
Sum of tokens collected: 210 (expected 210)
```
**while → if:** with two consumers, `notifyAll()` wakes both when one token is added; with `if`, the second consumer doesn't re-check `count == 0` and takes from an empty buffer → wrong token (0), `count` becomes −1, sum wrong or exceptions. Spurious wake-ups cause the same. `while` re-checks the condition after every wake-up.

### Student A – Producer–consumer
- `wait()` must be inside `synchronized` (it releases the monitor it must own – otherwise `IllegalMonitorStateException`) and inside a loop (condition may be false again when the thread runs).
- `notify()` vs `notifyAll()`: with one producer and several consumers, `notify()` might wake another consumer instead of the producer → everyone waits (lost wake-up). Our design uses `notifyAll()`.
- `count` stays within 0…5 (the loop conditions guarantee it).
- `sleep()` keeps the lock and just pauses; `wait()` releases the lock and waits for a notification.

### Student B – Deadlock
- Reliable deadlock: `java WalletTransfer deadlock` – T1 locks A then B, T2 locks B then A, with a 50 ms sleep between → both BLOCKED forever (detected after 2 s).
- Four conditions present: **mutual exclusion** (synchronized), **hold and wait** (holds one wallet, waits for other), **no preemption** (locks can't be taken away), **circular wait** (T1→B held by T2, T2→A held by T1).
- Prevention: always lock the wallet with the **smaller id first**. All threads acquire locks in the same global order, so a cycle in the wait-for graph is impossible (breaks circular wait). Total balance stays 2000.

### Joint conclusion
Both are about threads waiting for each other. Deadlock: progress is guaranteed by a lock order (no cycle). Producer–consumer: progress is guaranteed by `wait` releasing the lock and `notifyAll` waking every waiter so whoever can proceed does.

### Alternative
- `ArrayBlockingQueue<Integer>(5)`: `put`/`take` O(1), blocking built in, no hand-written wait/notify – far less error-prone.
- `tryLock(timeout)`: transfer O(1) when uncontended; on timeout release and retry → no deadlock, but possible livelock/retries. Space: one lock object per wallet. The library-based designs are less error-prone.

---

## Q7 – Concurrent, Fault-Tolerant Processor (10 Marks) – `Q7/ResultProcessor.java`

### Stage 1 – Interpretation (joint)
- Parallel: reading + validating + grading each file is independent. Shared: the results map and the success/failure summary.
- Failures: missing file (`NoSuchFileException`), wrong field count, non-numeric mark, mark out of range (`CorruptFileException`), unexpected runtime error.
- Requirements: R1 every file is processed exactly once; R2 one bad file never stops the run; R3 summary ok + failed = total files; R4 each failure names the file and reason; R5 10,000 files finish within a few seconds on a 4-core PC.

### Stage 2 – Design (Student A)
```
ResultProcessor
  + process(Path) : Result  throws IOException, CorruptFileException   (Callable body)
  + run(files, threads, results, failures)
Result(regNo, total, grade)
CorruptFileException extends Exception
```
```
ALGORITHM ProcessAll(files, p)
   pool ← FixedThreadPool(p)
   FOR EACH f IN files DO futures[f] ← pool.submit(Callable: process(f)) END FOR
   pool.shutdown()
   FOR EACH (f, fut) IN futures DO
      TRY  r ← fut.get();  results.put(r.regNo, r);  ok ← ok + 1
      CATCH ExecutionException e  failures.put(f, cause(e));  bad ← bad + 1
   END FOR
   RETURN (ok, bad)
```
`ConcurrentHashMap` for results/failures – thread-safe without a global lock. Each task's exception is stored inside its `Future`, so it surfaces only at `get()` and never kills other tasks.

### Stage 3 – Implementation and measurement (Student B)
Injected: every 500th file +7 missing, +13 bad mark, +21 wrong field count → 60 bad files in 10,000. Measured (after an untimed warm-up): 1 thread 1419 ms, 2 threads 1018 ms, 4 threads 1336 ms, 8 threads 1482 ms; every run reported 9,940 ok + 60 failed = 10,000 (**summary correct**). Unhandled failures recorded: none.

### Stage 4 – Defence (joint)
Measurements show the job is **I/O-bound**: each file needs ~20 µs of CPU but a file open/read on Windows costs more, so the best time was with **2 threads** and more threads were slower even on a 16-core PC – they all wait on the same disk/file-system (and antivirus) and add scheduling overhead. Recommended: a small pool (2–4) for this workload; ≈ number of cores only when per-file computation dominates. Completeness: every file has exactly one Future, and each Future ends in either `results` or `failures`, so ok + failed = n is checked. Limitation: all Futures are kept in memory and results are not persisted incrementally – a JVM crash loses the run.

### Alternative
| Approach | Total time | Memory (threads) | Failure handling |
|---|---|---|---|
| ExecutorService(p) | ≈ n/p × t | p stacks (~1 MB each) | per-Future, isolated |
| `files.parallelStream()` | ≈ n/cores × t (common ForkJoinPool) | cores stacks | an exception in the lambda aborts the whole stream unless caught inside |
| One thread per file | no faster than cores allow; heavy creation overhead | 10,000 stacks (~10 GB reserved) – may fail with OOM | each thread separate; summary needs extra sync |

---

## Individual Contribution Record
| Question | Student A – Primary | Student B – Primary | Joint |
|---|---|---|---|
| Q1 | Exception hierarchy, catch order | Test inputs, stack trace | Single-catch question |
| Q2 | Hierarchy diagram, propagation | Scenarios, chaining evidence | Storage exception conclusion |
| Q3 | Debugging, leak count, logging | Resource analysis, suppressed exceptions | Robustness decision |
| Q4 | Thread design, life cycle | Run-order, priority, daemon experiments | Output-order conclusion |
| Q5 | Race reproduction, interleaving | Strategy timing, intrinsic locks | 20-screen strategy |
| Q6 | Producer–consumer | Deadlock & lock ordering | Progress conclusion |
| Q7 | Stage 2 design | Stage 3 implementation & measurement | Stages 1 and 4 |
