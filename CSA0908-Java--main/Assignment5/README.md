# CSA09-192524110-Assignment5 – Files and Databases

- **Name:** Pranayaa S
- **Register Number:** 192524110
- **Java Version:** 21 (Eclipse Temurin)
- **Report:** [REPORT.md](REPORT.md) (Part A design + pseudocode, Part B results)
- **SQL script:** [bus_pass_schema.sql](bus_pass_schema.sql) – local test database `transport_test`

| Task | Program | Compile & run (inside the folder) | Result |
|---|---|---|---|
| B1 | PassStore.java (Complete the Code) | `javac *.java` → `java PassStore` | `[P1003, 192311003, Divya, 7, 3800.0]` / `null` |
| B1 | BusPassFileStore.java | `java BusPassFileStore` | add/find/list tests, damaged lines, growth 1k–100k |
| B1 | LineCounter.java | `java LineCounter` | unbuffered vs buffered line counting on 1/10/50 MB |
| B2 | DamagedFileTest.java | `java DamagedFileTest` | adversarial pass file handling |
| B2 | LoginCheck.java (needs MySQL) | `java -cp .;mysql-connector-j.jar LoginCheck jdbc:mysql://localhost:3306/transport_test USER PASS` | injection accepted by unsafe version, rejected by PreparedStatement |
| B3 | PassLoader.java (Complete the Code), InsertBenchmark.java (needs MySQL) | `java -cp .;mysql-connector-j.jar InsertBenchmark "jdbc:mysql://localhost:3306/transport_test?rewriteBatchedStatements=true" USER PASS` | Statement vs Prepared vs Batch timings; rollback leaves 0 rows |
| B4 | PassMigration.java (needs MySQL) | `java -cp .;mysql-connector-j.jar PassMigration passes.csv jdbc:mysql://localhost:3306/transport_test USER PASS` | migration with failure report + DatabaseMetaData |
| A3 | StudentDao.java (needs MySQL) | `java -cp .;mysql-connector-j.jar StudentDao <url> USER PASS` | generic Dao<T,K> CRUD |
| A4 | HostelSerialization.java | `java HostelSerialization` | transient fields, shared object graph, CSV size comparison |

On macOS/Linux use `:` instead of `;` in `-cp`.
