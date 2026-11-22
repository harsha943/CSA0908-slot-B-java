# CSA09 – Programming in Java – Assignment 4 (CO4)
## Event-Driven GUI Design

| | |
|---|---|
| **Name** | Pranayaa S |
| **Register Number** | 192524110 |
| **Repository** | CSA09-192524110-Assignment4 |
| **Java Version** | 21 (Eclipse Temurin) |

> GUI programs open windows; screenshots must be taken while running them (see README for commands). Each screenshot should be named after the test it shows, e.g. `Task4/screenshots/after_restore.png`.

---

## TASK 1 – GUI Design Review (6 Marks)

### 1.1 Diagnosis – deficiencies in `FeeWindow`
| # | Deficiency | What the user experiences / what goes wrong |
|---|---|---|
| 1 | All logic (parsing, SQL, waiting) inside `actionPerformed` – no separation of UI and logic | Cannot test payment without a window; any change touches the GUI class |
| 2 | `e.getActionCommand() == "Pay"` compares references, not text | Works only by luck of string interning; fails if the label is built at runtime or localised – Pay does nothing / acts as Clear |
| 3 | No validation: `Double.parseDouble("")` / letters | `NumberFormatException` on the EDT – nothing happens, stack trace in console, user gets no message; negative amounts accepted |
| 4 | Database call and `Thread.sleep(3000)` on the event-dispatch thread | Window freezes for 3+ s – buttons don't respond, window doesn't repaint ("Not responding") |
| 5 | Feedback only via `System.out.println("Paid")` | User sees nothing in the window |
| 6 | Receipt button falls into the `else` branch | Clicking Receipt **clears the form** instead of showing a receipt |
| 7 | SQL built by string concatenation | SQL injection through the register-number field (`x'); DROP TABLE fees; --`) |
| 8 | No `WindowListener`; connection never closed | Close button does nothing (AWT Frame); each payment leaks a DB connection |
| 9 | Checked `SQLException` and `InterruptedException` not handled | Does not even compile |

### 1.2 Repaired design – `Task1/FeeWindow.java`
```
FeeWindow (Frame)              FeeService
 - regNo, amount: TextField     + validate(regNo, amountText): String|null
 - pay, clear, receipt: Button  + pay(regNo, amount): Receipt   (PreparedStatement, background thread)
 - status: Label                + lastReceipt(regNo): Receipt
 - onPay(), onReceipt()  ──uses──▶
```
- One listener per button (lambdas), window closing via `WindowAdapter`.
- Validation in the service → error shown in the status label.
- Payment runs on a background thread; results are posted back with `EventQueue.invokeLater`; Pay is disabled while processing.
- SQL in the service uses `PreparedStatement` in try-with-resources.

### 1.3 Design defence (≤ 100 words)
The window now only reads fields and displays messages, so `FeeService` can be unit-tested without a GUI: validation rules, payment and receipts are ordinary methods. Each button has its own small handler, so changing Receipt cannot break Pay. Database access is in one class using parameterised queries, so security and connection handling are fixed in one place. Long work runs off the event thread, so the window stays responsive. Replacing AWT with Swing or the database with a web service changes only one side.

### Alternative concept
| | One listener + if–else | One listener per button / Action |
|---|---|---|
| Dispatch time, k buttons | up to k string comparisons – O(k) | O(1) – the source calls its own listener |
| Space | 1 listener object | k small listener objects (lambdas ~16 B each) |
| Maintenance | all buttons share one method | isolated, reusable (`Action` can also drive a menu item and a shortcut) |

---

## TASK 2 – Hospital Outpatient Appointment Desk (8 Marks)

### 2.1 Event model
| Event source | Event object | Listener interface | Action |
|---|---|---|---|
| Patient ID `JTextField` (Enter) | `ActionEvent` | `ActionListener` | validate ID, move focus to department |
| Department `JComboBox` | `ItemEvent` / `ActionEvent` | `ItemListener` | refresh doctor list |
| Doctor `JComboBox` | `ItemEvent` | `ItemListener` | refresh free time slots |
| Time-slot `JList` | `ListSelectionEvent` | `ListSelectionListener` | enable Book button |
| Book `JButton` | `ActionEvent` | `ActionListener` | validate and book; show confirmation/error |
| Clear `JButton` | `ActionEvent` | `ActionListener` | reset form, bookingInProgress = false |
| Frame | `WindowEvent` | `WindowListener` (`WindowAdapter`) | `windowClosing`: confirm if a booking is in progress |
| Any input field | `DocumentEvent` | `DocumentListener` | set bookingInProgress = true |

### 2.2 Component hierarchy
```
JFrame "Outpatient Appointment Desk"            (BorderLayout)
├── JLabel title                                  NORTH
├── JPanel formPanel                              CENTER  (GridLayout 4×2)
│   ├── JLabel "Patient ID"     ── JTextField patientId
│   ├── JLabel "Department"     ── JComboBox<String> department
│   ├── JLabel "Doctor"         ── JComboBox<Doctor> doctor
│   └── JLabel "Time slot"      ── JScrollPane → JList<LocalTime> slots
└── JPanel southPanel                              SOUTH  (BorderLayout)
    ├── JPanel buttons (FlowLayout) ── JButton book, JButton clear
    └── JLabel status
JOptionPane (confirmation / error / close confirmation dialogs)
```

### 2.3 Handler pseudocode
```
ALGORITHM OnDepartmentChanged
BEGIN
   dept ← department.selectedItem
   doctor.removeAllItems()
   slots.clear()
   IF dept = NONE THEN RETURN
   FOR EACH d IN doctorsByDept.get(dept) DO      // Map loaded once at start-up
      doctor.addItem(d)
   END FOR
   status ← "Select a doctor"
END

ALGORITHM OnBook
BEGIN
   id ← trim(patientId.text)
   IF id does not match "P[0-9]{6}" THEN show error "Invalid patient ID"; RETURN
   IF department, doctor or slot not selected THEN show error "Complete all fields"; RETURN
   IF slot already booked for doctor THEN show error "Slot taken"; RETURN
   run in background: appointmentService.book(id, doctor, slot)
   ON SUCCESS: show "Booked: id with doctor at slot"; bookingInProgress ← false; clear form
   ON FAILURE: show error message
END
```

### 2.4 Event trace
```
main → SwingUtilities.invokeLater → constructor (components created, listeners registered)
setVisible(true) → windowOpened → windowActivated → paint
... user events: actionPerformed / itemStateChanged / valueChanged ...
close button → windowClosing → (confirm dialog) → dispose() → windowDeactivated → windowClosed
```
Applet comparison: `init()` ≈ constructor, `start()` ≈ windowOpened/activated, `paint()` ≈ paint, `stop()` ≈ windowDeactivated/iconified, `destroy()` ≈ windowClosed. Applets are driven by the browser; a frame by the user and the window manager.

### Alternative concept
| | Query DB on each department change | Load `Map<String, List<Doctor>>` once |
|---|---|---|
| Time per change | O(network + query) ≈ ms, on a background thread | O(1) lookup + O(m_d) to fill the combo |
| Memory | O(m_d) for the current department | O(d + m) for all departments |
| Freshness | always current | needs refresh if doctors change |

---

## TASK 3 – Campus Library Search Screen (9 Marks) – `Task3/LibrarySearch.java`

### 3.1 Layout managers
| Container | Layout | Reason (resizing) |
|---|---|---|
| Frame | `BorderLayout` | CENTER region receives all extra space → results area grows; NORTH/SOUTH keep preferred height |
| top panel | `GridLayout(2,1)` | stacks title and search row with equal height |
| search row | `FlowLayout(LEFT)` | components keep preferred size; no stretching of the text field / button |
| results | `TextArea` in CENTER | grows both ways |
| bottom panel | `BorderLayout` | buttons in CENTER, status in SOUTH |
| buttons | `FlowLayout(CENTER)` | buttons keep their size, stay centred |
CardLayout is not needed (single view); it would suit switching between "search" and "details" screens.

### 3.2 Nested containers
```
Frame [BorderLayout]
├── NORTH  Panel top [GridLayout 2×1]
│          ├── Label title
│          └── Panel searchRow [FlowLayout LEFT]: Label, TextField, Choice, Button Search
├── CENTER TextArea results
└── SOUTH  Panel bottom [BorderLayout]
           ├── CENTER Panel buttons [FlowLayout]: Prev, Next, Reserve
           └── SOUTH  Label status
```

### 3.4 Resizing test
Screenshots at 600×400, 900×600 and 400×300 (status label prints window and results-area sizes): the results area grows/shrinks, search row and buttons keep their size. At very small widths FlowLayout wraps the search row (acceptable, nothing hidden).

### Alternative
- `GridBagLayout`: one panel with weights (`weighty=1` for results) – same behaviour, fewer containers, more complex constraints code.
- Absolute (`setLayout(null)` + `setBounds`): nothing resizes, components overlap/cut off on other fonts/DPI.
- Layout time is O(c) for c components in all; nested panels add a few container objects (space O(panels)); absolute has no layout cost but no adaptation.

---

## TASK 4 – Event-Handling Forensics (7 Marks) – `Task4/DrawPad.java`

### 4.1 Defect
`mouseclicked` (lower-case c) is a **new method** in the anonymous `MouseAdapter`, not an override of `mouseClicked`. The adapter's empty `mouseClicked` is called instead – nothing happens. The label never changes because the code never calls `status.setText(...)`.

### 4.2 Failing interaction
After fixing the name, click a few dots, then **minimise and restore** (or cover with another window). The dots disappear: they were drawn directly with `getGraphics()`, which paints on the screen once; on restore AWT calls `paint()`, which knows nothing about the dots and repaints the background.

### 4.3 Repair
Store each click in `List<Point>`, update the label, call `repaint()`; `paint(Graphics g)` draws every stored point (`fillOval(p.x-3, p.y-3, 6, 6)`), so the dots survive any repaint.

### 4.4 Prevention
With `@Override` on `mouseclicked`, javac reports: *"method does not override or implement a method from a supertype"* – the typo is caught at compile time.

### Alternative – off-screen BufferedImage
Draw each new dot once onto a `BufferedImage`; `paint()` does `g.drawImage(buffer,0,0,null)`. Repaint time O(W×H) pixel copy (constant w.r.t. n) instead of O(n) ovals; space W×H×4 bytes (e.g. 400×300 = 480 KB) instead of O(n) points (~16–24 B each). Image is better for thousands of strokes; the list is better for few points, undo and resizing.

---

## TASK 5 – Same Screen, Different Event-Handling Styles (7 Marks) – `Task5/ConverterStyles.java`, `Task5/Converter.java`

### 5.1 Four implementations
(a) frame implements `ActionListener`, (b) named inner class `ConvertHandler`, (c) anonymous inner class, (d) lambda – all share `convert()` logic and close via `WindowAdapter`.

### 5.2 Change effort (Enter key + dialog)
| Style | Change for Enter | Change for dialog |
|---|---|---|
| (a) | +1 line `amount.addActionListener(this)` | 1 line in shared `doConvert()` |
| (b) | +1 line (reuse same handler object) | same |
| (c) | had to move the anonymous class into a variable (+3 lines changed) | same |
| (d) | +1 line (store lambda in a variable) | same |

### 5.3 Comparison
| | (a) Frame | (b) Inner class | (c) Anonymous | (d) Lambda |
|---|---|---|---|---|
| Readability | poor with many buttons (if-else on source) | good, named | verbose | best, concise |
| Access to frame fields | direct | direct (outer instance) | direct | direct |
| Reuse | whole frame is the listener | high, can be instantiated anywhere in the frame | low | via variable/method reference |
| Ease of change | must edit the shared method | isolated | isolated | isolated |
For a large application: **lambdas / method references for simple actions and `Action` objects (or named classes) for actions shared by buttons, menus and shortcuts.**

### Complete the Code (Converter, style d)
One lambda `ActionListener onConvert = e -> convert();` registered on both the button and the text field. Valid input `1000` + USD → `12.00 USD`; `abc` → `Invalid amount`.

### Alternative
`this::convert` is identical to the lambda (one object). A shared `AbstractAction` is one object used by button, menu item and key binding – adding a trigger is one line and enable/disable propagates everywhere. Styles (a)–(d) need one registration per trigger; (c) creates a new object per registration if not stored.

---

## TASK 6 – Campus Event Registration Desktop Application (13 Marks) – `Task6/EventRegistrationApp.java`, `Task6/event_registration.sql`

### Stage 1 – Interpretation
- Users: students (register), committee members (view, filter, export).
- Screens: main window (registration form + registrations table + filter), dialogs (error, confirmation, file chooser, about).
- Data: student reg. no., name, e-mail, event; event list.
- Rules / testable requirements:
  - R1 Reg. no. must be exactly 9 digits, otherwise the error "Register number must be exactly 9 digits" is shown and nothing is saved.
  - R2 Name letters only; e-mail must match a valid pattern.
  - R3 A student registering twice for the same event gets "already registered" and the table still has one row (also enforced by `UNIQUE(student_reg, event_id)`).
  - R4 The window remains responsive (can be moved/resized) while data loads.
  - R5 Filter shows only rows of the chosen event; Export writes exactly the rows shown.

### Stage 2 – Screen and event flow
```
┌ Campus Event Registration ───────────────────────────────────┐
│ File [Export CSV… | Exit]   Help [About]          (JMenuBar)│
│ ┌Register─────────────────────────────────────────────────┐ │
│ │ Reg No [_____] Name [______] Email [_______] Event [▼] [Register]│
│ └──────────────────────────────────────────────────────────┘ │
│ Filter by event [All events ▼]  [< Prev] [Next >]            │
│ ┌ JTable: Reg No | Name | Email | Event ───────────────────┐ │
│ └──────────────────────────────────────────────────────────┘ │
│ status: 12 row(s), page 1                                    │
└──────────────────────────────────────────────────────────────┘
Register ─▶ (invalid) error dialog ─▶ back to form
         ─▶ (valid) save ─▶ status + table reload
File▸Export ─▶ JFileChooser ─▶ CSV written ─▶ status
```

### Stage 3 – Critical decisions
- Input validation: `RegistrationService.register()` (one place, testable).
- Duplicate check: service query `exists()` **and** the database UNIQUE constraint (protects against two clerks registering at the same time).
- Database access: only in `RegistrationDao`, always via `PreparedStatement`.
- Off the EDT: every DB call runs in a `SwingWorker.doInBackground()`; UI updates happen in `done()` on the EDT; the Register button is disabled while saving.

### Stage 4 – Class diagram and pseudocode
```
UI layer                         Service layer                 Data-access layer
MainFrame (JFrame) ───────────▶ RegistrationService ────────▶ «interface» RegistrationDao
 - JTextField regNo,name,email   + register(r): String|null      + events()
 - JComboBox event, filter       + page(event, n, size)          + exists(regNo, event)
 - DefaultTableModel model       + events()                      + insert(r)
 + onRegister(), loadPage(),                                     + find(event, limit, offset)
   onExport()                                                     ▲            ▲
                                                     JdbcRegistrationDao   InMemoryDao
Registration (record: regNo, name, email, event)
```
```
ALGORITHM Register
BEGIN
   r ← Registration(trim fields)
   disable Register button; status ← "Saving..."
   IN BACKGROUND: msg ← service.register(r)
        // validates fields, checks duplicate, inserts with PreparedStatement
   ON EDT: enable button
           IF msg ≠ NULL THEN show error dialog(msg)
           ELSE status ← "Registered"; clear form; LoadPage()
END

ALGORITHM Filter / LoadPage
BEGIN
   ev ← selected filter (NULL for "All events")
   IN BACKGROUND: rows ← dao.find(ev, LIMIT 50, OFFSET page × 50)
   ON EDT: model.setRowCount(0); add each row; status ← count + page
END
```

### Stage 5 – Demonstration
Run with MySQL: `java -cp .;mysql-connector-j.jar EventRegistrationApp jdbc:mysql://localhost:3306/campus_events USER PASSWORD` (schema: `event_registration.sql`). Without arguments it uses an in-memory store so the GUI can be demonstrated anywhere. Tests to screenshot: normal registration; reg. no. `12AB` → error; same student + same event twice → "already registered"; filter by "Dance"; export CSV.

### Alternative – load all vs paging
| | Load all 100,000 rows | Page with LIMIT/OFFSET in SwingWorker |
|---|---|---|
| Load time | O(n) – seconds; if on EDT the window freezes | O(page) per page ≈ ms |
| Memory | O(n) table model (~100,000 × 4 strings ≈ tens of MB) | O(page) ≈ 50 rows |
| Responsiveness | poor (or fine only if in background, still slow first display) | always responsive |
(OFFSET itself is O(offset) in MySQL for deep pages; keyset paging `WHERE reg_id > ?` fixes that.)
