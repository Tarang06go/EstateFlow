# EstateFlow — AI handover (exact state of this delivery)

## What exists
* **Domain/services/DAOs/JDBC/JavaFX** all implemented per `docs/4.EstateFlow_Architecture.pdf` (Rev 4). 20 FXML screens/dialogs, 22 controllers, 9 services, 8 JDBC DAOs.
* Schema is unchanged (`db/schema.sql`); `db/seed.sql` now contains real demo data (verified to load on an empty schema).

## What was actually run (and where)
The sandbox had **no Maven Central access**, so Maven was **not run**. Instead the code was compiled with `javac --release 25` (JDK 25.0.2) and tested with the JUnit Platform console launcher.

| Run | Result |
|:---|:---|
| Unit tests (`--exclude-tag integration`) | **90 passed, 0 failed** |
| MySQL integration + race tests (`--include-tag integration`), JVM zone Asia/Kolkata and America/New_York | **35 passed, 0 failed** in each zone |
| Mutation check: `FOR UPDATE` removed from `JdbcRentChargeDao`/`JdbcPropertyDao` | both race tests **failed** as expected (proves locks matter) |
| FXML ↔ controller cross-check script (ids, `onAction` handlers) | 20 files, 0 problems |
| Layer/logging grep checks (`log.error`, `printStackTrace`, `System.out`, javafx/db/java.sql in services, commit/rollback/close in DAOs, DAO in controllers) | clean |

Environment differences from the spec (results may differ on the target stack):
* DB server was **MySQL 8.0.46** (Ubuntu package), not 8.4; driver was **MariaDB Connector/J 2.7.6**, not mysql-connector-j 26.7.0. The pom pins the spec versions; Connector/J-specific URL options (`useAffectedRows=false`, `connectionTimeZone=UTC`…) were **not exercised**. Run `mvn verify -Pintegration` on the real stack.
* JavaFX compile used Debian's **JavaFX 11** jars, Mockito **2.23** with `-Dnet.bytebuddy.experimental=true`. The pom pins JavaFX 25.0.4 / Mockito 5.24.0 with the Mockito Java agent.
* **The JavaFX UI has never been launched** (no display). Treat all screens as compile-verified only.
* JaCoCo was not run; coverage is unmeasured.

## Decisions / deviations from the letter of the spec
* `TransactionManager`: generic `inTransaction(TransactionalWork<T>)`; the void variant is named `runInTransaction(VoidWork)` (a same-named overload makes lambdas ambiguous).
* `BaseJdbcDao`/`RowMapper` were written directly (no longhand first DAO step).
* `PropertyDao.findRows` was dropped: joined occupancy rows live only in `ReportingDao.findOccupancyRows` (no duplicated query). `AgreementDao.findEndingBetween` returns `ExpiringLeaseRow` join rows; `AgreementDao.findAllRows` returns `AgreementListRow`.
* `RentalAgreement.effectiveStatus` was corrected to Section 5.4 (a terminated agreement still inside its notice period is ACTIVE/EXPIRING_SOON; TERMINATED only after the termination date). Test added.
* `Main.java` replaced by `Launcher.java`; `MainTest` rewritten accordingly.
* `MaintenanceService`: cancelling a request that has a cost is refused (BR-32 read literally); clear the cost first.
* Transactions use `ConnectionHandle` borrowed/owned semantics exactly as in §3.4; commit/rollback failures were provoked in tests by killing the connection server-side (the driver skips commit/rollback when it believes no transaction is open, so closing the connection did not fail them).
* Timestamps (`voided_at`, `cancelled_at`) are written/read as UTC `LocalDateTime`; the session zone is set to UTC on every connection; round trip verified under two JVM zones.

## Not done / next actions
1. On a machine with Maven Central: `mvn verify`, then `mvn verify -Pintegration` (see `db/test-schema-setup.md`). Fix any Connector/J 26 / JavaFX 25 / JaCoCo surprises.
2. Launch with `mvn javafx:run` and walk the manual checklist: add owner → tenant → property → agreement (check preview) → record partial payment → overpay refused → void and re-record → overdue list → terminate → maintenance lifecycle → exports/receipt → dashboard.
3. Add screenshots and an ER diagram to `docs/` (Phase 7), measure JaCoCo (target ≥ 70% on service, rules, model, report), tag `v1.0`.
4. Known UI gaps: property detail shows agreement history only; no keyboard-shortcut polish; empty-state hints are minimal.

## Secrets
`db.properties` and `db-test.properties` are git-ignored and excluded from the ZIP; only `*.example` files ship.

## v1.1 update

See `docs/change-requests/ACR-001-audit-log.md` and the v1.1 patch's `VERIFICATION_REPORT.md`. Key design choices: PDFs are produced by
a small dependency-free writer (`report/pdf`) because no PDF library could be resolved or validated in the build environment (PDF
text uses "Rs." since the standard fonts have no rupee sign); service constructors gained `TransactionManager` / `AuditService`
parameters (Owner, Tenant, Property, Maintenance) and `AuditService` (Agreement, Payment); `PropertyDao.findRows` was replaced by
`ReportingDao.searchPropertyRows` (capped).
