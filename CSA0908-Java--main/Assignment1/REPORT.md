# CSA09 – Programming in Java – Assignment 1 (CO1)
## Design, Implement and Validate an Object-Oriented Model for a Campus Smart-Parking System

| | |
|---|---|
| **Name** | Pranayaa S |
| **Register Number** | 192524110 |
| **Repository** | CSA09-192524110-Assignment1 |
| **Java Version** | 21 (Eclipse Temurin) |

---

## TASK 1 – Understand the Problem and Identify the Objects (4 Marks)

### Inputs and outputs
| Inputs | Outputs |
|---|---|
| Vehicle registration number, vehicle type (two-wheeler / car / EV), user type (student / staff / visitor), pass status | Allocated floor and slot, or "facility full" |
| Entry time, exit time (or duration in minutes) | Parking ticket with a unique ticket number |
| kWh consumed (EVs on charging slots) | Parking fee + charging fee on exit |
| Facility configuration (floors, slots per floor) | Total collection for the day, current occupancy |

### Nouns → candidate classes, verbs → methods
| Noun (class) | Why it is needed (responsibility) | Verbs (methods) |
|---|---|---|
| `ParkingFacility` / `ParkingLot` | Owns the floors and slots; the only object that knows which slot is free, so allocation rules live in one place | `allocate()`, `release()`, `enter()`, `exit()` |
| `Floor` / slot array | Groups slots; floors can have different sizes | `findFreeSlot()` |
| `Vehicle` (abstract) | Holds registration number and owner; the fee depends on the type of vehicle, so each type must be able to compute its own fee | `calculateFee()`, `ratePerHour()` |
| `TwoWheeler`, `Car`, `ElectricCar` | Specialise rate; `ElectricCar` adds charging | `ratePerHour()`, `chargingFee()` |
| `ParkingTicket` | Records *who* is *where* since *when*; needed to compute the fee on exit and to guarantee unique numbers | constructor, getters |
| `User` / `UserType` | Decides free vs paid parking (pass holders) | `hasValidPass()` |
| `Pass` (interface) | Monthly pass, later one-day pass — a capability, not a kind of vehicle | `coversToday()` |

### Business rules
1. A slot can hold at most one vehicle at a time.
2. A ticket number is never reused (global static counter).
3. The same registration number cannot be parked twice at the same time.
4. Duration is never negative; every started hour is charged in full.
5. Students and staff with a valid monthly pass park free; visitors always pay.
6. EV charging is billed separately at ₹12 per kWh.
7. A daily maximum caps the parking fee for each 24-hour period.
8. Release of a slot that is invalid or already free is rejected.

### Assumptions
- Fee slabs: two-wheeler ₹10/h, car ₹30/h, daily maximum = 8 × hourly rate.
- Any free slot can take any vehicle type (charging handled per kWh, not by slot type).
- Times are on the same campus clock; a stay over midnight is handled using minutes.
- Facility configurations supported: 1 × 20 → 3 × 50 → 5 × 100 (and uneven floors in Task 12).

### Alternative concept – parallel arrays
```java
String[] regNo = new String[N]; String[] type = new String[N]; long[] entryTime = new long[N];
int find(String r) { for (int i = 0; i < count; i++) if (regNo[i].equals(r)) return i; return -1; }
```
| | Objects (`Vehicle`) | Parallel String arrays |
|---|---|---|
| Find by reg. no. | O(n) in an array, O(1) average with `HashMap<String,Vehicle>` | O(n) scan |
| Add a new attribute | Add one field to one class | Add a new array everywhere, keep every index in step; easy to desynchronise |
| Space per vehicle | object header (~12–16 B) + fields | same field data, no header – slightly smaller |
| Type safety | Compiler checks types, behaviour lives with data | Everything is a String; fee logic becomes `if(type[i].equals("CAR"))` chains |

The object design costs a few bytes per vehicle but removes whole classes of index bugs.

---

## TASK 2 – Choose the Data Representation (3 Marks)

| Field | Type | Reason |
|---|---|---|
| Slot number, floor number | `int` | small whole numbers, used as array indices |
| Vehicle number | `String` | alphanumeric (TN01AB1234) |
| Entry time | `LocalDateTime` (or `long` epoch millis) | date + time; `Duration.between` gives minutes |
| Duration | `int` minutes | whole minutes, max ~ 525,600/year fits in `int` |
| Fee | `double` here (₹ with paise); `BigDecimal` in production | fractional rupees; `BigDecimal` avoids binary rounding |
| Ticket number | `int` (static counter) | unique, sequential |

**Floors and slots:** a **jagged array** `String[][] slots = new String[floors][]` with `slots[f] = new String[slotsPerFloor[f]]`. It allows every floor to have a different size (needed by Task 12) and gives O(1) access to `slots[f][s]`.

**Casting risk:** `int hours = minutes / 60;` gives 2 for 130 minutes (integer division truncates) → fee ₹60 instead of ₹90. Similarly `(int)(minutes / 60.0)` truncates. The design uses integer ceiling `(minutes + 59) / 60`, which needs no floating point and cannot lose precision. Program: `Task02/CastingDemo.java`:

| minutes | wrong (int div) | wrong (cast) | correct (ceil) |
|---|---|---|---|
| 59 | 0.0 | 0.0 | 30.0 |
| 61 | 30.0 | 30.0 | 60.0 |
| 130 | 60.0 | 60.0 | 90.0 |

**Input:** `Scanner(System.in)` for interactive entry/exit; command-line arguments (`args[0]` minutes, `args[1]` rate) for scripted tests.

### Alternative concept – 1-D array / `ArrayList<String[]>`
- 1-D: `index = floor × slotsPerFloor + slot` → O(1) locate, but only works when every floor has the **same** size. With uneven floors you need a prefix-sum array `start[f]` → `index = start[f] + slot`, still O(1), extra O(F) space.
- `ArrayList<String[]>`: one `String[]` per floor; floors can be added at runtime, O(1) locate (`list.get(f)[s]`), extra object per floor.
- Space: all three are O(total slots); jagged and ArrayList add one array header per floor.

---

## TASK 3 – Design the Control Logic (4 Marks)

### Pseudocode – Allocate slot
```
ALGORITHM AllocateSlot
INPUT  : slots[0..F-1][ ], vehicleNo
OUTPUT : (floor, slot) or NONE
BEGIN
   FOR f ← 0 TO F-1 DO
      FOR s ← 0 TO length(slots[f]) - 1 DO
         IF slots[f][s] = EMPTY THEN
            slots[f][s] ← vehicleNo
            RETURN (f, s)                 // stop at first free slot
         END IF
      END FOR
   END FOR
   RETURN NONE                            // facility full
END
```

### Pseudocode – Compute fee
```
ALGORITHM ComputeFee
INPUT  : minutes, ratePerHour, hasPass, userType
OUTPUT : fee
BEGIN
   IF minutes < 0 THEN RAISE InvalidDuration
   IF hasPass AND userType ≠ VISITOR THEN RETURN 0
   IF minutes = 0 THEN RETURN 0
   days  ← minutes DIV 1440
   rest  ← minutes MOD 1440
   hours ← (rest + 59) DIV 60             // every started hour
   dailyMax ← 8 × ratePerHour
   fee ← days × dailyMax + MIN(hours × ratePerHour, dailyMax)
   RETURN fee
END
```
- `return` ends the search as soon as a slot is found (no wasted iterations); `continue` is used in validation loops to skip invalid records; `break` exits a floor loop when a floor is known full (free-count = 0).
- When no slot is free, `allocate()` returns `null` and the caller prints "Facility full".

### Complete the Code – `Task03/ParkingFacility.java`
Expected and actual output:
```
[0, 0]
[0, 1]
[1, 0]
null
true false false
[0, 1]
```

### Alternative concept – free counts + stack of free slots (`Task03/ParkingFacilityStack.java`)
| | Linear scan | Free stack per floor |
|---|---|---|
| allocate() | O(F × S) worst case | O(F) to find a non-empty floor, O(1) pop |
| release() | O(1) | O(1) (push) |
| Extra space | none | O(F × S) integers for the stacks |

---

## TASK 4 – Design Classes, Objects and Constructors (4 Marks)

`Task04/TicketDemo.java`:
```java
ParkingTicket()                                  // default → this("UNKNOWN",0,0)
ParkingTicket(String v, int f, int s)            // overloaded → this(v,f,s,now())
ParkingTicket(String v, int f, int s, LocalDateTime t)   // validates once
ParkingTicket(ParkingTicket original)           // copy: duplicate ticket, same number
```
- **Constructor chaining** (`this(...)`) means validation is written once in the full constructor.
- **`this`** distinguishes field from parameter (`this.floor = floor`).
- **Copy constructor** is needed for a lost ticket: the re-issued ticket keeps the same number and entry time but is marked DUPLICATE; it is a new object (`t2 == copy` is false).
- **Memory:** reference variables (`t1`, `t2`) live in the stack frame of `main`; the ticket objects live on the heap; the static `nextNumber` lives with the class (metaspace).

### Alternative concept
| Approach | Valid object guaranteed? | Cost for n tickets |
|---|---|---|
| Overloaded + chained constructors | Yes – object is valid when constructor returns; fields can be `final` | O(n) time, O(n) space |
| Default constructor + setters | No – object exists half-initialised between calls; fields can't be `final` | O(n·k) calls for k setters, same space |
| Static factory `ParkingTicket.issue(v,f,s)` | Yes; can also cache, return subclasses, have a descriptive name | O(n), same space |

---

## TASK 5 – static Members and the Object Life Cycle (3 Marks)
`Task05/StaticDemo.java`
- `static int ticketCounter` – belongs to the class: every facility must draw from one sequence. As an instance variable each facility would start at 1 and the North and South gates would issue the **same** ticket number (shown in the program output).
- `static final double GST` initialised in a **static block** that runs once when the class is loaded.
- `static double withTax(double)` – a utility that needs no object state.
- **Life cycle:** a `ParkingTicket` is created on entry, referenced from the active-ticket map, removed from the map on exit and returned to the caller for printing; once no variable refers to it, it becomes **eligible for garbage collection** (GC time is decided by the JVM; `System.gc()` is only a request).
- **Passing objects:** Java passes the *reference* by value – `exit()` receives the ticket reference, can read/modify the object, but cannot change the caller's variable.

**Alternative:** counter as instance variable → k facilities hold k counters (O(k) space) and numbers collide; static → one counter (O(1) space) for any k, n tickets. If separate sequences are wanted, prefix the number with the facility code.

---

## TASK 6 – Design the Inheritance Hierarchy (4 Marks)

```
                 «interface»             «interface»
                 Chargeable               Pass
                 +chargingFee(kWh)        +coversToday()
                       ▲
        ┌──────────────┴─────────────┐
        │ «abstract» Vehicle          │
        │ - regNo : String  {final}   │
        │ - owner : UserType          │
        │ # ratePerHour() : double {abstract}
        │ + calculateFee(int) : double│
        │ + calculateFee(LocalTime, LocalTime)
        │ + equals(), hashCode(), toString()
        └──────────────▲──────────────┘
             ┌─────────┴──────────┐
        TwoWheeler               Car
        ratePerHour()=10         ratePerHour()=30
                                  ▲
                             ElectricCar ──implements──▶ Chargeable
                             - kWh : int
                             + chargingFee(kWh) = 12 × kWh
```
- **Hierarchical** inheritance (TwoWheeler and Car from Vehicle) + **multilevel** (ElectricCar → Car → Vehicle).
- Inherited: `regNo`, `getRegNo()`, `calculateFee()`, `equals/hashCode`. Added: `ratePerHour()` implementation, `chargingFee()`, `kWh`.
- `super(regNo, ...)` passes data to the parent constructor; `super.calculateFee()` could reuse parent logic.

**Constructor execution trace for `new ElectricCar("TN03C3", ...)`:**
```
1. ElectricCar(...) called → first statement super(r,u,p)
2.   Car(...) called       → first statement super(r,u,p)
3.     Vehicle(...) runs   → validates and sets regNo, owner
4.   Car body runs (nothing extra)
5. ElectricCar body runs   → prints "[ctor] ElectricCar body runs last", sets kWh
```
(Object's constructor runs before step 3.)

**Alternative – composition:** `class Vehicle { VehicleType type; }` with `VehicleType(name, rate)`. Adding a type = adding a data row (no new class, no recompilation) – better when types differ only in rate. Fee time O(1) in both; space: composition shares one `VehicleType` object among all vehicles of that type (+1 reference per vehicle). Inheritance is better when types differ in *behaviour* (EV charging, peak-hour rules).

---

## TASK 7 – Override and Specialise Behaviour (4 Marks)
- Overridden: `ratePerHour()` in every subclass; `chargingFee()` in `ElectricCar`; `toString()`, `equals()`, `hashCode()` in `Vehicle`.
- `@Override` makes the compiler check that a superclass method with the same signature exists (catches typos like `ratePerhour`).
- Rules: same name and parameter list; return type same or covariant; access cannot be narrower (`protected` → `protected`/`public`); cannot throw broader checked exceptions.
- `final`: `regNo` is `final` (identity never changes – required because it is used in `hashCode`); `getRegNo()` is `final`; `ElectricCar` is a `final` class in the full system; constants such as `GST` are `static final`.
- `equals()` compares `regNo`; `hashCode()` returns `regNo.hashCode()` – consistent, so two objects with the same registration are one key in a `HashSet`/`HashMap`.

**Alternative – one method with `switch(type)`:** time per fee O(1) for a jump-table switch (O(k) for if-else chain); every new type needs changes to the switch in `Vehicle` *and* every other switch on type → violates Open/Closed. Overriding: 1 new class, 0 changed classes.

---

## TASK 8 – Apply Polymorphism (4 Marks)
- **Compile-time:** `calculateFee(int minutes)` and `calculateFee(LocalTime in, LocalTime out)` – the compiler picks by argument types.
- **Upcasting:** `Vehicle[] parked = { new TwoWheeler(..), new Car(..), new ElectricCar(..) };`
- **Dynamic dispatch trace** for `v.calculateFee(130)` when `v` refers to an `ElectricCar`:

| Declared type | Actual object | Method executed |
|---|---|---|
| `Vehicle` | `ElectricCar` | `Vehicle.calculateFee` (inherited) → calls `ratePerHour()` → JVM looks in `ElectricCar` (none) → `Car.ratePerHour()` = 30 → 3 h × 30 = 90 |

### Complete the Code – `Task08/FeeDemo.java`
```
TwoWheeler TN01A1 -> Rs 30.0
Car TN02B2 -> Rs 90.0
ElectricCar TN03C3 -> Rs 210.0
true
```
**Why `true`?** `equals()` uses `instanceof Vehicle` and compares only `regNo`, so a `Car` and an `ElectricCar` with the same registration are equal. For this system that is **correct**: a registration number identifies one physical vehicle, so it must not be parked twice regardless of how it was classified. (If type mattered, use `getClass() == o.getClass()`.)

**Alternative – instanceof chains:** `if (v instanceof ElectricCar) ... else if (v instanceof Car) ... ` – up to k checks per call (O(k)) vs O(1) virtual dispatch; every new type edits every chain; order matters (ElectricCar must be tested before Car).

---

## TASK 9 – Introduce Abstraction (4 Marks)
- `Vehicle` is an **abstract class**: it has state (`regNo`), a constructor that validates it, and shared concrete behaviour (`calculateFee`), but no meaningful rate of its own → `ratePerHour()` is abstract.
- `Chargeable` and `Pass` are **interfaces**: capabilities that cut across the hierarchy (an e-bike or a delivery van could also be chargeable; a pass belongs to a user, not to a vehicle).
- A class gets several behaviours by `extends` one class + `implements` many interfaces (`ElectricCar extends Car implements Chargeable`).
- A `default` method is useful for a shared rule, e.g. `default double chargingFeeWithTax(int kWh) { return chargingFee(kWh) * 1.18; }`; a `static` interface method for a helper such as `Pass.none()`.

**Alternative (swap):** `Vehicle` as interface with default methods can no longer hold `regNo` state or have a constructor that validates it – every class repeats the field and validation. `Chargeable` as abstract class prevents `ElectricCar` from extending `Car` (single inheritance). Call time: interface calls (`invokeinterface`) vs class calls (`invokevirtual`) are both O(1) and JIT-inlined; space per object is unchanged.

---

## TASK 10 – Encapsulation through Packages and Access Modifiers (3 Marks)
Package structure:
```
parking.model    Vehicle, TwoWheeler, Car, ElectricCar, ParkingTicket, Chargeable, Pass, UserType
parking.service  ParkingLot (allocation, fees, collection)
parking.app      SmartParking (main, tests)
```
| Member | Modifier | Reason |
|---|---|---|
| fields (`regNo`, `slots`, `collection`) | `private` | only the owning class may change state |
| `ratePerHour()` | `protected` | subclasses override, outside code doesn't call it |
| `calculateFee()`, `getRegNo()`, `enter()`, `exit()` | `public` | the API |
| `ParkingTicket` constructor | package-private | only the service creates tickets |

Validation: the `Vehicle` constructor rejects invalid registration numbers (regex), `calculateFee` rejects negative durations, `release()` rejects invalid slots.

Wrapper/autoboxing: `Map<String, ParkingTicket>`, `List<Integer>` in the free-slot stack. Risk: `Integer a = 1000, b = 1000; a == b` is **false** (only −128…127 are cached) – always compare with `equals()` or unbox.

**Alternative – public fields:** each caller must validate; the same check is repeated in every place that writes the field (p places ⇒ p copies), and a single forgotten check corrupts state. Validation cost is O(1) per update either way – encapsulation costs nothing at runtime.

---

## TASK 11 – Implement, Test and Defend the Design (5 Marks)
Program: `Task11/SmartParking.java` (lot of 2 + 1 slots). Real run: **17 passed, 0 failed** (`Task11/output.txt`).

| # | Test | Expected | Actual |
|---|---|---|---|
| 1 | Bike enters | F0-S0 | PASS (Task11/output.txt) |
| 2 | Car enters | F0-S1 | PASS |
| 3 | EV takes last free slot | F1-S0 | PASS |
| 4 | Lot full | null | PASS |
| 5 | Bike exits after 130 min | ₹30 | PASS |
| 6 | Car exits after exactly 60 min (slab boundary) | ₹30 | PASS |
| 7 | EV 130 min + 10 kWh | ₹210 | PASS |
| 8 | Zero-minute parking | ₹0 | PASS |
| 9 | 61 minutes | ₹60 | PASS |
| 10 | 10 h car (daily cap) | ₹240 | PASS |
| 11 | Staff with pass | ₹0 | PASS |
| 12 | Duplicate registration | exception | PASS |
| 13 | Invalid reg. number | exception | PASS |
| 14 | Negative duration | exception | PASS |
| 15 | Release invalid slot | false | PASS |
| 16 | Exit unknown vehicle | exception | PASS |
| 17 | Day total (hand: 30 + 30 + 210) | ₹270 | PASS |

**Evaluation:** encapsulation – all state private, validated in constructors; inheritance – rates specialised in subclasses; polymorphism – `exit()` has no type checks for parking fees; abstraction – `Vehicle` abstract, `Chargeable` interface. **Limitation:** pricing is still inside the vehicle classes, so a time-based rule (peak hours) would require editing them (fixed in Task 12). Also `exit()` still uses one `instanceof Chargeable` check for the charging fee.

**Alternative – active ticket storage:** array/`ArrayList` → exit lookup O(n) scan; `HashMap<String,ParkingTicket>` → O(1) average (used). Space O(n) in all; HashMap adds ~32–48 B per entry.

---

## TASK 12 – Re-evaluate and Adapt the Design (8 Marks)

### A. Impact analysis
- New: e-bike (flat fee), delivery van (double car rate 9 AM–5 PM – fee now depends on **time of day**, not only duration), visitor one-day pass, 5 floors with different slot counts.
- Affected: fee computation (`calculateFee` only knew minutes), pass handling (only a boolean monthly pass), facility constructor (must accept 5 uneven sizes).
- Uneven floors: a rectangular `String[F][S]` array would waste slots or fail; the jagged array already supports it.
- Must be *modified*: `Vehicle.calculateFee` signature (needs entry time) and pass field (boolean → `ParkingPass`). Everything else is *extended*.

### B. Evaluation of original design
- New vehicle types *can* be added as subclasses (e-bike) – good.
- But pricing rules are spread across subclasses as hard-coded rates; the van's time-window rule doesn't fit `ratePerHour()`.
- Tested classes `TwoWheeler`, `Car` would stay unchanged only if pricing is extracted.

### C. Decision: **MODIFY** (`Task12/SmartParkingV2.java`)
```
«interface» PricingRule { fee(LocalTime in, int minutes) }
   ├── HourlyRule(rate)        – two-wheeler, car
   ├── FlatRule(amount)        – e-bike
   └── PeakHourRule(base)      – delivery van, ×2 between 09:00 and 17:00
«interface» ParkingPass { coversToday() }
   ├── MonthlyPass
   └── OneDayPass(date)
abstract Vehicle2 { rule() : PricingRule; final calculateFee(in, minutes) }
   ├── TwoWheeler2, Car2      (unchanged logic)
   └── EBike, DeliveryVan     (NEW classes only)
Facility: Vehicle2[][] floors = jagged {40, 60, 50, 80, 30}
```

### D. Evidence-based defence
| Test | Expected | Actual |
|---|---|---|
| E-bike 300 min | ₹20 | PASS |
| E-bike 0 min | ₹0 | PASS |
| Van 10:00, 120 min (2 peak h) | ₹120 | PASS |
| Van 16:00, 120 min (1 peak + 1 off) | ₹90 | PASS |
| Van 07:00, 60 min (off-peak) | ₹30 | PASS |
| Car 130 min | ₹90 | PASS |
| Visitor with one-day pass | ₹0 | PASS |

- Classes changed: 1 (`Vehicle` → `Vehicle2` fee method + pass type). Classes added: 3 rules, 2 passes, 2 vehicles. `TwoWheeler`/`Car` logic unchanged.
- Adding another type in future = 1 subclass (+ 1 rule only if the pricing is new).
- Trade-off: one more indirection per fee call (negligible, O(h) for the van's hour loop) and more small classes; gain: pricing rules are testable alone and reusable.

---

## How to compile and run
```
cd Assignment1/Task03 && javac *.java && java ParkingFacility
cd Assignment1/Task08 && javac FeeDemo.java && java FeeDemo
cd Assignment1/Task11 && javac SmartParking.java && java SmartParking
cd Assignment1/Task12 && javac SmartParkingV2.java && java SmartParkingV2
```
