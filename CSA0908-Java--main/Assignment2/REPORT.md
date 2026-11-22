# CSA09 – Programming in Java – Assignment 2 (CO2)
## Strings, Regular Expressions, Collections, Generics, Comparable/Comparator

| | |
|---|---|
| **Name** | Pranayaa S |
| **Register Number** | 192524110 |
| **Repository** | CSA09-192524110-Assignment2 |
| **Java Version** | 21 (Eclipse Temurin) |

Measured numbers are in each folder's `output.txt`; the tables below give the analysis.

---

## Q1 – String, StringBuilder or StringBuffer? (6 Marks) – `Q1/TicketBuilderBenchmark.java`

### Prediction (before implementation)
- `String` is **immutable**: `s = s + x` copies the whole existing string into a new object every time. For n fields of average length L, total characters copied ≈ L(1 + 2 + … + n) = **O(n²·L)**, and ~2 intermediate objects per field.
- `StringBuilder` appends into a growable char array (capacity doubles) → amortised **O(n·L)** total, one final String.
- `StringBuffer` = same algorithm but every method is `synchronized` → slightly slower when used by one thread.
- For a short ticket (8 fields) the difference is negligible (microseconds); it becomes large for 50 fields × 10,000 tickets and dramatic for 2,000-field tickets.

### Workloads
| Workload | Tickets × fields | Purpose |
|---|---|---|
| W1 | 1 × 8 | short ticket – shows difference is negligible |
| W2 | 10,000 × 50 | realistic bulk generation |
| W3 (own) | 100 × 2,000 | very long ticket – exposes O(n²) growth |

Measures: `System.nanoTime()` averaged over 5 runs after JIT warm-up; estimated intermediate objects; heap used before/after.

### Comparison with prediction
Measured: W1 – all ≈ 0.01–0.08 ms (negligible); W2 – `+` 38.6 ms vs StringBuilder 7.8 ms (≈5×); W3 – `+` 585 ms vs StringBuilder 4.5 ms (≈130×), with ~174 MB of garbage vs 3 MB. The growth matches O(n²). StringBuilder and StringBuffer are close (uncontended locks are cheap). Shared-log test: StringBuilder length 79,389 (characters lost), StringBuffer 80,000.

### Design decision
- **Per-ticket text:** `StringBuilder` – the builder is a local variable used by one thread, so synchronization is wasted work.
- **Shared booking log:** `StringBuffer` (or better, a thread-safe queue/logger). The program's experiment appends 80,000 characters from 8 threads: `StringBuffer` length is always 80,000; `StringBuilder` loses characters or throws `ArrayIndexOutOfBoundsException`.

### Alternative concept and complexity
- `String.join(",", fields)` / `StringJoiner` – internally use a builder sized once → O(n·L) time, O(n·L) space, cleanest code when all fields are known.
- `String.format()` – parses the format string every call → O(n·L) but with a large constant; good for fixed layouts, not loops.
- Repeated `+`: O(n²·L) characters copied, O(n) garbage objects (each up to n·L chars). StringBuilder: O(n·L) time, O(n·L) final space + at most 2× capacity slack.

---

## Q2 – When Is ArrayList an Appropriate Engineering Choice? (4 Marks) – `Q2/PlaylistBenchmark.java`

Experiments for 1,000 / 10,000 / 100,000 songs, 2,000 operations each: add at end, insert+remove at front, insert+remove in the middle, get k-th (random), iterate.

### Complexity table
| Operation | ArrayList | LinkedList | ArrayDeque | Plain array (manual shift) |
|---|---|---|---|---|
| add at end | O(1) amortised | O(1) | O(1) amortised | O(1) if space, O(n) resize |
| insert at front | O(n) shift | O(1) | O(1) | O(n) |
| insert in middle | O(n) shift (fast `System.arraycopy`) | O(n) to walk + O(1) link | not supported | O(n) |
| get k-th | **O(1)** | O(n) walk | not supported (no index) | O(1) |
| iterate | O(n), cache friendly | O(n), pointer chasing | O(n) | O(n) |
| space per element | 1 reference slot (4–8 B) + ≤50 % slack | node object ≈ 24–32 B (prev, next, item, header) | 1 slot | 1 slot, fixed size |

### Operating boundary (answer)
ArrayList is a reasonable choice when the dominant operations are **append, index access (jump to k-th song) and iteration** – which is how a playlist is mostly used. Middle inserts are O(n) but `arraycopy` is so fast that ArrayList even beats LinkedList for middle inserts at these sizes (LinkedList must walk n/2 nodes). I would stop recommending it when **front insert/remove dominates on large lists** (≥ ~10⁴ items with frequent front operations), because every front operation shifts the whole array.

### Changed condition – "Up Next" queue
Adds at both ends and removes from the front → a **deque**. `ArrayDeque` gives O(1) at both ends with a compact circular array and no node allocation; it was the fastest in the measurement, LinkedList second, ArrayList worst (O(n) per front op). **Recommendation:** keep `ArrayList` for the playlist itself, use `ArrayDeque` for "Up Next".

---

## Q3 – Engineer the hashCode–equals Contract (6 Marks) – `Q3/Attendance.java`, `Q3/HashContractExperiments.java`

### Tests with predictions
| Test | Design | Predicted size | Reason | Observed |
|---|---|---|---|---|
| 1 | Two students; one *same reference* added twice | 2 | same object → same identity hash → found | 2 |
| 2 | Same student scanned at 3 gates (3 new objects) | **3** | `hashCode()` not overridden → identity hash differs → different buckets → `equals()` never called | 3 |
| 3a (own) | Correct class, change `regNo` after insertion | size 1 but `contains` = false, `remove` = false | object stays in the bucket of the old hash; lookup searches new bucket | as predicted |
| 3b (own) | `TreeSet` with comparator on *name* only, two different students named Kumar | 1 | TreeSet uses `compare()==0` as equality, not `equals()` → inconsistent with equals loses a student | 1 |

### Inquiry
- **Why duplicates appear:** `HashSet.add` computes `hashCode()` → bucket index; only objects in the *same bucket* are compared with `equals()`. `Object.hashCode()` is identity-based, so equal students land in different buckets and are never compared.
- **How HashSet locates an element:** `index = (h ^ h>>>16) & (capacity-1)`; then walk the bucket (list or tree) comparing hash and `equals()`.
- **Mutating a hashed field:** the element is now in the "wrong" bucket; it can't be found or removed – a memory leak and duplicate risk. Hash keys must be immutable (make `regNo` `final`).
- **Correction:** `@Override public int hashCode() { return regNo.hashCode(); }` (or `Objects.hash(regNo)`) – uses exactly the fields used by `equals()`. Verification: `Attendance` prints **Unique students: 3** before the fix and **Unique students: 2** after.

### Alternative concept
| Structure | Time per scan | Space for n students |
|---|---|---|
| `HashSet<Student>` (fixed) | O(1) average | O(n) – entry + table |
| `TreeSet<Student>` with `Comparator.comparing(regNo)` | O(log n) | O(n) – red-black node per entry |
| `HashMap<String,Student>` keyed by regNo (`putIfAbsent`) | O(1) average | O(n); does not depend on Student's equals/hashCode at all |

---

## Q4 – Choosing a Map for a Pharmacy Inventory (6 Marks) – `Q4/PharmacyInventory.java`

### Design reasoning (before implementation)
| Map | put | get | iteration order | Code-order report | Insertion-order report |
|---|---|---|---|---|---|
| HashMap | O(1) avg | O(1) avg | unspecified | needs copy + sort O(n log n) | **impossible** without extra data |
| TreeMap | O(log n) | O(log n) | sorted by key | free (O(n)) | impossible without extra data |
| LinkedHashMap | O(1) avg | O(1) avg | insertion order | copy + sort O(n log n) | free (O(n)) |

Tested at 1,000 / 50,000 / 500,000 medicines (random insertion order); 100,000 lookups each; time for both reports; order verified programmatically ("OK"/"WRONG").

### Scalability inquiry
- **Acceptable lookup time:** < 1 µs per lookup (a pharmacist does hundreds per day; even 1 ms would be invisible, so all maps pass – the decision is about the reports and growth).
- **Deployment:** **LinkedHashMap** – O(1) lookups like HashMap, the insertion-order report is free, and the once-a-day code-order report costs one O(n log n) sort (milliseconds even at 500,000). If the code-order report were needed constantly, TreeMap would be preferred.
- Remaining cost: String hashing of the code on each `get`, and the evening sort.
- **Hashtable** is not preferred: every method is `synchronized` (slow single-threaded, and still not safe for compound operations), no null keys/values, legacy `Enumeration`; use `HashMap` or `ConcurrentHashMap`.

### Alternative – sorted array + `Arrays.binarySearch`
Lookup O(log n); insertion O(n) (shift) – fine for a static list, poor for frequent additions; code-order report free; space n references, no entry objects (smallest). Compared: HashMap O(1)/O(1)/O(n log n) report; TreeMap O(log n)/O(log n)/O(n) report.

---

## Q5 – Derive a Generic Ranking Component from a Principle (7 Marks) – `Q5/LeaderboardDemo.java`, `Q5/RankingTests.java`

### Design derived from the principle
- **Comparator objects instead of `Comparable<Team>`:** natural ordering of a team is ambiguous (by name? by points?) and the same component must rank players, colleges and quiz teams with different rules. Separate comparators keep each ranking rule outside the data class and allow many rules.
- Multi-field: `Comparator.comparingInt(points).reversed().thenComparing(comparingInt(goalDiff).reversed()).thenComparing(name)`.
- Generic: `class Leaderboard<T>` with `Comparator<? super T>` (a comparator for a supertype can rank subtypes); bounded `RankBoard<T extends Rankable>` for any type with points/goalDiff/name.
- Wildcard: `addAll(List<? extends T>)` accepts a `List<SubTeam>`.

### Complete the Code output
```
[Eagles(9,6), Bisons(9,4), Panthers(9,4), Cobras(6,1)]
```
**Bisons above Panthers:** equal points (9) and equal goal difference (4), so the third key – name A–Z – decides; "Bisons" < "Panthers".

### Tests
1. Ties at each level (equal points, equal points + GD) – order resolved by GD then name.
2. Adversarial: `Integer.MAX_VALUE`, `Integer.MIN_VALUE`, 0 – the subtraction comparator `b.points - a.points` overflows and produces a wrong order; `comparingInt` is correct.
3. Missing tie-breaker: two teams equal in points and GD – result depends on input order (comparator says "equal" for different teams).

### Correctness argument
Each key comparison (`Integer.compare`, `String.compareTo`) is a total order: antisymmetric, transitive, and consistent. A lexicographic combination of total orders is a total order: if A>B and B>C, take the first key where they differ – transitivity holds key by key. Including the unique name as the final key means two teams compare equal **only if** they are the same team, satisfying "equal items compare as equal". Subtraction breaks the principle because overflow makes `compare` non-transitive.

### Alternative concept
| Approach | Sorts | Extra space | Reuse |
|---|---|---|---|
| Comparator chain (used) | 1 × O(n log n) | O(n) for the copy (TimSort ≤ n/2 temp) | high – pass a different comparator |
| `Team implements Comparable` | 1 × O(n log n) | same | low – one fixed order per class |
| 3 stable sorts (name, GD, points) | 3 × O(n log n) | same per sort | medium; correct only because TimSort is stable |

---

## Q6 – Priority, Order and Fairness in a Queue (8 Marks) – `Q6/Triage.java`, `Q6/TriageExperiments.java`

### Design
- A plain FIFO `Queue` serves by arrival only – a critical patient arriving after ten minor ones would wait.
- `PriorityQueue` with comparator `severity ASC, then arrival ASC` (a binary min-heap) always returns the smallest key with `poll()`.
- Arrival order among equal severities: a monotonically increasing `arrival` counter is the tie-breaker (PriorityQueue is **not** stable by itself).
- Next patient: `poll()`; waiting list display: copy to a list and sort with the same comparator (or poll a copy).

### Complete the Code output
```
Meena(S1) Kumar(S1) Lata(S2) Ravi(S3) John(S3) Asha(S5)
```
The counter gives Meena 1 and Kumar 4, so among S1 patients Meena (earlier) comes first; same for Ravi (0) before John (2).

### Correctness check (8 patients, hand-worked)
A4 B2 C5 D1 E2 F4 G1 H3 → **D G B E H A F C**. The program matches. Printing `pq` directly shows the internal **heap array**, which only guarantees parent ≤ children, not a sorted order.

### Growth
Prediction: `add` O(log n), `poll` O(log n). Measured at 10⁴, 10⁵, 10⁶: per-operation time grows slowly (roughly with log n, plus cache effects), confirming the prediction.

### Starvation and fair rule
A severity-5 patient is overtaken by every new S1–S4 patient; if severe cases keep arriving, its key is never the minimum → it waits indefinitely (shown: never served within 200 arrivals). **Aging rule:** effective severity = max(1, severity − ⌊waiting time / T⌋) – every T minutes of waiting improves the priority by one level, so every patient is served within a bounded time (shown: Minor is served after a finite number of patients). Implementation note: priorities that change with time require re-heaping (periodic rebuild, or the 5-queue design below where aging moves a patient to the next queue).

### Deployment decision
5,000 patients/day ≈ 1 every 17 s. Each heap operation takes well under 1 µs even for 10⁶ patients – the data structure is never the bottleneck; **yes**, the design can serve a city hospital. The real constraints are persistence, concurrency (several desks → `PriorityBlockingQueue`) and the aging rule.

### Alternative concept
| Design | Admit | Serve next | Space |
|---|---|---|---|
| PriorityQueue (heap) | O(log n) | O(log n) | O(n) array |
| Sorted LinkedList | O(n) find position | O(1) remove head | O(n) nodes (~3× more memory) |
| Array of 5 FIFO queues | **O(1)** | O(1) (check ≤ 5 queues) | O(n) + 5 deques |
Since severity has only 5 levels, the 5-queue design is optimal and naturally keeps arrival order.

---

## Q7 – Diagnose, Redesign and Validate (13 Marks) – `Q7/LeaderboardRedesign.java`

### A. Diagnosis
Selected solution: the **Leaderboard** from Q5. `ranking()` copies the list and re-sorts it on every call. In a live meet the board is refreshed after every match result → u updates on n teams cost **O(u · n log n)** and create u full copies. The growing operation is the sort; the work is repeated because only one team changed between two sorts. The cause: `ArrayList` keeps no order, so order must be rebuilt every time.

### B. Objective
Keep teams permanently ordered: one update ≤ O(log n), top-10 read O(10), no full re-sort, no list copies, identical rankings to the original. Measurable target: ≥ 10× faster for 2,000 updates on 10,000 teams.

### C. Revised design
```
OrderedBoard
  - index : HashMap<String, Team>      name → current immutable Team
  - order : TreeSet<Team>(RANK)        always sorted (red-black tree)
  + update(name, pts, gd):
        old ← index.get(name)           O(1)
        order.remove(old)               O(log n)
        new ← Team(old + pts, old + gd) immutable value
        index.put(name, new); order.add(new)   O(log n)
  + top(k): first k elements of order  O(k)
```
Exploited property: a balanced BST keeps elements sorted on insert. Avoided: repeated O(n log n) sorts and list copies. Extra memory: one tree node + one hash entry per team. New trade-off: Team must be immutable and the comparator must be consistent with equals (the name tie-breaker guarantees this, otherwise the TreeSet would merge teams – the Q3 lesson).

### D. Prediction
Original time per refresh grows ~ n log n (×10 teams ⇒ >×10 time); redesign grows ~ log n (almost flat). Expected improvement: one to three orders of magnitude at 10⁴–5·10⁴ teams.

### E. Validation
Same random update sequence (seed 9) applied to both; the final top-10 lists are compared (`same? true`). Measured for 2,000 updates: 1,000 teams 515 ms → 3.8 ms; 10,000 teams 6,088 ms → 2.5 ms; 50,000 teams 31,623 ms → 3.0 ms; rankings identical (`same? true`).

### F. Defence
The prediction is supported: the original grew ~60× from 1,000 to 50,000 teams while the redesign stayed ~3 ms – a >10,000× improvement at 50,000 teams, far beyond the 10× objective. New costs: more memory per team and more complex update code. The original is still preferable when the ranking is computed **once** (end of tournament) or n is tiny – a single sort is simpler and equally fast.
