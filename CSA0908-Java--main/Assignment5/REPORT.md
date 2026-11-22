# CSA09 – Programming in Java – Assignment 5 (CO5)
## Data Persistence Engineering – Files and Databases

| | |
|---|---|
| **Name** | Pranayaa S |
| **Register Number** | 192524110 |
| **Repository** | CSA09-192524110-Assignment5 |
| **Java Version** | 21 (Eclipse Temurin) |

---

# PART A – Persistence Design & Pseudocode (25 Marks)

## TASK A1 – Design a File Format (7 Marks)

### A1.1 Record layouts
**(a) Delimited text** – one pass per line, UTF-8, field separator `|`:
```
passId|regNo|name|route|boardingStop|validTill|amount
P1001|192311001|Arun|12|Main Gate|2027-05-31|4500.0
```
- Field order fixed as above; dates ISO `yyyy-MM-dd`; amount with `.` decimal.
- A delimiter inside a field is escaped: `|` → `\|`, `\` → `\\` (e.g. `Divya \| Rao`). The reader splits only on unescaped `|`. (CSV alternative: quote the field `"Divya, Rao"` and double inner quotes.)

**(b) Binary (`DataOutputStream`)**
| Field | Method | Size |
|---|---|---|
| passId | `writeUTF` | 2-byte length + bytes |
| regNo | `writeUTF` | 2 + 9 |
| name | `writeUTF` | 2 + len (UTF length prefix, so any character incl. delimiter is safe) |
| route | `writeInt` | 4 |
| boardingStop | `writeUTF` | 2 + len |
| validTill | `writeLong` (epoch day) | 8 |
| amount | `writeDouble` | 8 |
Each record is preceded by a 4-byte magic/record marker (`0x50415353` "PASS") so a reader can detect corruption.

### A1.2 Operations (text design)
```
ALGORITHM AddPass(p)
BEGIN
   line ← join("|", escape(p.passId), escape(p.regNo), escape(p.name), p.route,
                escape(p.stop), p.validTill, p.amount)
   OPEN BufferedWriter(FileWriter(file, APPEND))         // buffered: one OS write per buffer
   WRITE line; WRITE newline
   CLOSE (try-with-resources)
END

ALGORITHM FindPass(id)
BEGIN
   IF file does not exist THEN RETURN NOT_FOUND
   OPEN BufferedReader(FileReader(file))                 // line-oriented reading
   lineNo ← 0
   WHILE (line ← readLine()) ≠ EOF DO
      lineNo ← lineNo + 1
      rec ← Parse(line)                                  // NULL if damaged
      IF rec = NULL THEN LOG "damaged line " + lineNo; CONTINUE
      IF rec.passId = id THEN RETURN rec
   END WHILE
   RETURN NOT_FOUND
END

ALGORITHM ListByRoute(r)
BEGIN
   result ← empty list
   OPEN BufferedReader(FileReader(file))
   FOR EACH line DO
      rec ← Parse(line)
      IF rec ≠ NULL AND rec.route = r THEN append rec to result
   END FOR
   RETURN result
END
```
Stream choice: character streams (`FileReader/FileWriter`) because the file is text; wrapped in `Buffered*` to avoid one system call per character and to get `readLine()/newLine()`. Byte streams (`FileInputStream/FileOutputStream` + `Data*`) are used only for the binary layout.

### A1.3 Damaged data
- **Damaged line:** `Parse` checks field count = 7, numeric route/amount, valid date; on failure the line is skipped and reported with its line number – the search continues.
- **Missing file:** `find` returns NOT_FOUND / `list` returns empty; `add` creates the file (`CREATE, APPEND`). Startup logs a warning.
- **Incomplete write (power failure):** every record ends with a newline; a last line without newline (or with wrong field count) is treated as damaged. For full safety: write the new file to `passes.tmp`, `fsync`, then atomically rename (`Files.move(..., ATOMIC_MOVE)`); keep a checksum per line (e.g. CRC32 as an 8th field) to detect torn writes.

### Alternative – RandomAccessFile fixed-length records
Fixed sizes, e.g. passId 8 + regNo 9 + name 40 + route 4 + stop 30 + date 8 + amount 8 = 107 bytes. Record i at `seek(i × 107)`.
- Find by **record number** O(1); find by **pass ID** O(1) only if the ID maps to the record number (P1000+i) or with an index; otherwise still O(n).
- Text scan: O(n).
- Wasted space: padding of short names/stops – e.g. average name 12 chars wastes 28 bytes/record; ~35 % of the file for 8,000 passes (≈ 300 KB wasted). Long names are truncated.

## TASK A2 – Don't Read What You Don't Need (5 Marks)

### A2.1 Prediction
`FileInputStream.read()` with no buffer makes **one native call per byte**: 50 MB = 52,428,800 calls. Each call crosses JNI / the OS (hundreds of ns), so the program takes tens of seconds, dominated by call overhead, not by disk speed.

### A2.2 Redesign
```
ALGORITHM CountLines(file)
BEGIN
   buf ← byte[65536]; lines ← 0
   OPEN FileInputStream(file)
   WHILE (n ← read(buf)) ≠ -1 DO          // one OS call per 64 KB
      FOR i ← 0 TO n-1 DO IF buf[i] = '\n' THEN lines ← lines + 1
   END WHILE
   RETURN lines
END
```
(Or wrap in `BufferedInputStream` with a 64 KB buffer.) OS calls fall from 52 million to 50 MB / 64 KB ≈ **800**.

### A2.3 When buffering does not help
- Reading in large chunks already (`read(byte[1 MB])`) – a buffer adds a needless copy.
- A single small read (a 20-byte config file).
- When the bottleneck is elsewhere (network/disk throughput or heavy per-record processing).

### Alternative – `Files.readAllLines()`
Time O(file size) like buffering, but memory **O(file size)** – 50 MB of bytes become ~100+ MB of String objects (UTF-16 + object headers) vs O(64 KB) for a buffer. Appropriate for small files that are needed whole; streaming is appropriate for large files or when only a count is needed.

## TASK A3 – Discover the Data-Access Design Pattern (5 Marks)

### A3.1 Common pattern
| Repeated in StudentDB, CourseDB, MarksDB | Different |
|---|---|
| get connection (URL, user, password) | SQL text |
| create (Prepared)Statement | parameter values |
| execute query/update | how a ResultSet row becomes an object (Student/Course/Marks) |
| loop over ResultSet | table and key names |
| close ResultSet, statement, connection; handle SQLException | |

### A3.2 Generic interface – `A3/StudentDao.java`
```java
interface Dao<T, K> {
    void insert(T t); Optional<T> findById(K id); List<T> findAll();
    boolean update(T t); boolean delete(K id);
}
abstract class JdbcDao<T,K> implements Dao<T,K> {   // template: connection, prepare, execute, close
    protected abstract T map(ResultSet rs);          // the only per-table part
}
class StudentDao extends JdbcDao<Student, String> { ... SQL + map() ... }
```

### A3.3 Object–table mapping
| Student field | Column | Type |
|---|---|---|
| regNo | reg_no | CHAR(9) **PRIMARY KEY** (= K in `Dao<Student,String>`) |
| name | name | VARCHAR(60) |
| dept | dept | VARCHAR(30) |
| cgpa | cgpa | DECIMAL(4,2) |

### Alternative – SQL inside service/GUI classes
Renaming a column means finding every SQL string that uses it – possibly in many GUI and service classes (p places) vs exactly one DAO. Time and space per operation are the same (the SQL executed is identical; the DAO adds one method call, negligible next to the network round trip).

## TASK A4 – What Should Be Serialized? (4 Marks) – `A4/HostelSerialization.java`

### A4.1 What to persist
- `transient String password` – secrets must not be written in readable form to disk.
- `transient Connection db` – `Connection` is not `Serializable` (would throw `NotSerializableException`) and a socket to a database means nothing after restart; reconnect after loading.
- Serializing `Hostel` writes the **whole object graph**: the hostel, its list, every Room, every Resident reachable from it. Each object is written once; a second reference to the same Resident is written as a back-reference, so shared objects remain shared after loading (program prints `true`). Transient fields come back as default values (`null`).

### A4.2 Plan for change
Adding `phone`:
- **Without** an explicit `serialVersionUID`, the compiler-generated UID changes when the class changes → reading an old file throws `InvalidClassException: local class incompatible`.
- **With** a fixed `serialVersionUID = 1L`, the old file loads; the new field `phone` is set to its default (`null`); removed fields are ignored. Change the UID only for incompatible changes (field type changed, class hierarchy changed).

### Alternative – CSV / JSON
| | Java serialization | CSV/JSON |
|---|---|---|
| File size | larger (class descriptors, but binary values) | smaller for simple data; readable |
| Save/load n residents | O(n), reflection-based | O(n), simple string building/parsing |
| New field | works with fixed UID; default value | CSV: add column + header, old rows lack it → default; JSON: missing key → default, very tolerant |
| Other | Java only; security risk deserializing untrusted data | any language, safe, diff-able |

## TASK A5 – Design for Correctness and Safety (4 Marks)

### A5.1 Transaction pseudocode
```
ALGORITHM TransferWallet(reg, amount = 500)
BEGIN
   con ← getConnection(); old ← con.autoCommit; con.setAutoCommit(FALSE)
   TRY
      ps1 ← "UPDATE wallet SET balance = balance - ? WHERE student_reg = ? AND wallet_type = 'HOSTEL' AND balance >= ?"
      rows ← ps1.execute(amount, reg, amount)
      IF rows ≠ 1 THEN RAISE InsufficientBalance
      ps2 ← "UPDATE wallet SET balance = balance + ? WHERE student_reg = ? AND wallet_type = 'CANTEEN'"
      rows ← ps2.execute(amount, reg)
      IF rows ≠ 1 THEN RAISE WalletNotFound
      con.commit()                                // both changes become permanent together
   CATCH any error
      con.rollback()                              // neither change survives
      RAISE error
   FINALLY
      con.setAutoCommit(old); close statements and connection
END
```

### A5.2 Injection analysis
With `id = clerk1` and `pwd = ' OR '1'='1` the SQL becomes
```sql
SELECT * FROM users WHERE id='clerk1' AND pwd='' OR '1'='1'
```
`AND` binds tighter than `OR`, so the condition is `(…) OR TRUE` → every row is returned → login succeeds. With `id = admin' -- ` the rest of the query (the password check) becomes a comment.

Safe redesign:
```java
PreparedStatement ps = con.prepareStatement("SELECT 1 FROM users WHERE id = ? AND pwd = ?");
ps.setString(1, id); ps.setString(2, pwd);
```
The SQL is compiled before the values are supplied; values are always treated as data, never as SQL. (In production store a salted hash, not the password.)

### Alternative – two independent updates without a transaction
If the program crashes after the debit and before the credit, ₹500 disappears from the hostel wallet and never reaches the canteen – the database is inconsistent. Time: the transaction performs one commit (one log flush) instead of two autocommits, so it costs about the same or less, while holding row locks slightly longer; it removes the correctness risk entirely.

---

# PART B – Implementation & Experimental Investigation (25 Marks)

## TASK B1 – Implement and Instrument the File Store (7 Marks)
Programs: `B1/PassStore.java` (Complete the Code), `B1/BusPassFileStore.java` (full store + tests + growth), `B1/LineCounter.java` (buffering).

### Complete the Code output
```
[P1003, 192311003, Divya, 7, 3800.0]
null
```
**Why P1002 doesn't stop the search:** its line has only 2 fields, so it fails the "exactly 5 fields" check and is skipped with `continue`; the loop keeps reading, and P1002 is never returned because no *valid* record has that ID.

### B1.1 Validation
| Test | What it verifies | Result |
|---|---|---|
| find P2 (name contains `|`) | escaping keeps the record intact | found, name "Divya \| Rao" |
| find P99 | missing ID returns null without error | null |
| find P3 (damaged line) | damaged lines are skipped, not returned | null |
| list route 12 | listing ignores damaged P4 | [P1, P5] |
| missing file | no exception | null |
(See `B1/output.txt`.)

### B1.2 Comparison with Part A
Same structure as the pseudocode. Changes: (1) the simple `split("|")` was replaced by a custom splitter because of escaping; (2) damaged lines are collected in a list for reporting instead of only logged; (3) `CREATE, APPEND` open options handle the missing file on add.

### B1.3 Buffering measurement
Files of 1, 10 and 50 MB; unbuffered `read()` vs `BufferedInputStream` vs `byte[]` buffer vs `readAllLines`. Prediction from A2.1 (unbuffered ≈ 1 call/byte, tens of seconds for 50 MB; buffered ≈ 100× faster). Results: `B1/output.txt`.

### B1.4 Growth
Time to find the **last** pass grows linearly from 1,000 to 100,000 records (each `find` re-reads and parses the whole file: O(n)).

### Alternative – HashMap index at start-up
Load once: O(n) time, O(n) memory (~200 B per pass incl. strings). q searches: O(n + q) vs O(q × n) for repeated scans. For q ≥ a few searches the index wins; it must be updated on every `add` and costs memory proportional to the file.

## TASK B2 – Engineer an Adversarial Input (5 Marks) – `B2/LoginCheck.java`, `B2/DamagedFileTest.java`
| Input | Unsafe (Statement) | PreparedStatement | Reveals |
|---|---|---|---|
| clerk1 / secret123 | accepted | accepted | normal |
| clerk1 / wrong | rejected | rejected | normal |
| clerk1 / `' OR '1'='1` | **ACCEPTED** | rejected | user input is executed as SQL |
| `admin' -- ` / anything | **ACCEPTED** | rejected | comments can delete conditions |

Damaged bus-pass file (wrong field count, non-numeric amount, empty line, non-numeric route, negative amount, truncated last line): the store accepts only the 2 valid records and reports the line number and reason for each rejected line – revealing that the original design must validate types and ranges, not just field count.

**Alternative – escaping quotes / regex validation:** each query must remember to escape every value (one forgotten call = vulnerability); escaping rules differ per database and charset (backslash, multibyte tricks); regexes reject legitimate names (O'Brien). Cost O(L) per value – similar to PreparedStatement's binding – but PreparedStatement also caches the parsed plan, so it is both safer and usually faster.

## TASK B3 – Statement vs PreparedStatement vs Batch (8 Marks) – `B3/PassLoader.java`, `B3/InsertBenchmark.java`
Schema: `../bus_pass_schema.sql`.

### B3.1 Correctness
10 records inserted; `ResultSetMetaData` lists `pass_id VARCHAR, reg_no CHAR, name VARCHAR, route_no INT, amount DECIMAL` and the rows are printed in order.

### B3.2 Timing (1,000 / 10,000 / 100,000 rows)
Expected pattern: Statement slowest (SQL parsed every row, string building), PreparedStatement faster (parsed once), batch fastest by 10–100× (one round trip per 1,000 rows; with `rewriteBatchedStatements=true` MySQL sends multi-row INSERTs). Measured times: `B3/output.txt`.

### B3.3 Transaction evidence
An invalid amount at row 1,000 of 2,000 throws `NumberFormatException` → `rollback()` → `rows in table after rollback: 0`. Without the transaction (autocommit on), the first 1,000 rows would remain.

### Complete the Code
TODO 1 sets 5 parameters, `addBatch()`, `executeBatch()` every 1,000 rows; TODO 2 `commit()` and `return n`; TODO 3 `rollback()` and re-throw. *Note:* the given skeleton uses `oldAutoCommit` in `finally` without declaring it, so `boolean oldAutoCommit = con.getAutoCommit();` was added before `setAutoCommit(false)` – otherwise the code does not compile.

### B3.4 Trade-off
(a) Nightly import of 100,000 passes → **PreparedStatement + batch inside one transaction** (fastest, all-or-nothing). (b) Single pass at the counter → **PreparedStatement**, autocommit (one row, immediate visibility, safe from injection). Plain Statement is never used with user data.

### Alternative – multi-row INSERT / LOAD DATA
- Multi-row `INSERT ... VALUES (...),(...),...`: one statement per b rows – similar to rewritten batches; memory O(b) for the SQL string; limited by `max_allowed_packet`.
- `LOAD DATA [LOCAL] INFILE`: server parses the file directly – fastest for n rows (minimal per-row overhead), memory O(1) on the client, but no per-row Java validation and requires the file and server permissions.

## TASK B4 – Final Engineering Decision (5 Marks) – `B4/PassMigration.java`

### B4.1 Migration
Reads the pass file line by line, validates each line (field count, ID format, 9-digit reg. no., numeric route, non-negative amount), loads valid rows with a batched `PreparedStatement` (1,000 per batch) inside one transaction (`ON DUPLICATE KEY UPDATE` makes re-runs safe), rolls back on any database error, and prints a failure report with line numbers.

### B4.2 DatabaseMetaData
Prints product name/version, driver name/version, JDBC version, batch support and the list of tables. Useful when moving to another server: confirms the program is talking to the expected database and version, that required tables exist, and which features (batch updates, transactions) the driver supports – before any data is written.

### B4.3 Production decision – **Relational database**
| Requirement | Text file | Serialization | Database |
|---|---|---|---|
| 100× passes (≈ 800,000/year) | O(n) scan per search – B1.4 shows linear growth | must load whole graph into memory | indexed lookup O(log n) |
| Several counters at once | file locking, lost updates | whole-file rewrite, last writer wins | row locking, transactions |
| Searching (by route, reg no.) | full scan | in-memory only | SQL + indexes |
| Safety | partial writes possible | one corrupt file loses all | ACID, rollback (B3.3), constraints |
| Bulk load speed | – | – | batch inserts measured fastest (B3.2) |
Decision: a **shared MySQL database**, accessed through PreparedStatements/DAOs, batch + transaction for imports.

### Alternative – one serialized file per campus
Search across all passes = deserialize 12 files: O(N) time and O(N) memory per search; space ≈ 12 files + Java serialization overhead. Concurrent counters on the same campus need a lock around load-modify-save of the whole file (one writer at a time, O(campus size) per save); cross-campus queries and consistency are hard. A shared database handles concurrency per row and searches with indexes.
