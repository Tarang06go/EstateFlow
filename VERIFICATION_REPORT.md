# Verification report — EstateFlow v1.1 patch

Statements below are limited to what was actually executed. "Not verified" means exactly that.

## Environment used
JDK 25.0.2 (`javac --release 25`), JUnit Platform console launcher 1.9.x (Jupiter 5.10), Mockito 2.23 (with
`-Dnet.bytebuddy.experimental=true`), MySQL **8.0.46** server, MariaDB Connector/J **2.7.6**, Debian JavaFX **11** jars,
poppler/qpdf for PDF checks. **Maven was not run**: the build environment cannot reach Maven Central, so `mvn test/verify`
was not executed and the pinned versions in `pom.xml` (JavaFX 25, Mockito 5, MySQL 8.4, Connector/J 26.7.0, JaCoCo) were not exercised.
`pom.xml` is unchanged by this patch.

## Executed and passed
| Check | Result |
|:---|:---|
| Unit tests (no database), `--exclude-tag integration` | **115 passed, 0 failed** |
| MySQL integration + race tests, JVM zone Asia/Kolkata | **44 passed, 0 failed** |
| Same integration suite, JVM zone America/New_York | **44 passed, 0 failed** |
| Integration suite on a database **without** the v1.1 migration (audit_log dropped) | 43 passed, 1 aborted (the audit test self-skips via an assumption), **0 failed** |
| Migration applied twice in a row on `estateflow_test` | succeeded both times (idempotent) |
| Patch overlaid on a clean copy of the delivered v1.0 (`EstateFlow_Updated.zip`), compiled, then tested | compiled; **115/115 unit** and **44/44 integration** passed, 0 aborted (migration present) |
| FXML <-> controller cross-check script (25 FXML files: every fx:id has a field, every field exists, every `onAction` handler exists, every referenced FXML file exists) | 0 problems |
| PDF files (receipt, 120-row table report) validated with `qpdf --check`, `pdfinfo`, `pdftotext`; rendered pages inspected | no errors; text extracted correctly; 5 pages for 120 rows |
| Layering/logging greps (`log.error` only in ErrorHandler, no printStackTrace/System.out, no javafx/db/java.sql in services, no commit/rollback/close in DAOs) | 0 violations on the final v1.1 code (also 0 uses of `CURDATE()`/`NOW()` in application SQL, 0 empty catch blocks) |

The v1.0 business-rule tests (BR-01 to BR-32), transaction tests and the two-thread race tests (overlap, overpayment) are part of the
counts above and still pass; the audit write happens inside the same transaction, and a dedicated test proves a failing audit insert
rolls the payment/agreement back.

## New tests
`V11ServicesTest`, `AuditServiceTest`, `PdfReportsTest` (unit); `V11IntegrationTest` (MySQL: search caps and receipt-number search, agreement and payment
filters, reminder correctness/no duplicates/cancelled+paid excluded, chart and analytics aggregation excluding voided payments,
empty database, property details, audit entries and JSON validity, audit-failure rollback, PDF/CSV exports).

## NOT verified
* **The JavaFX UI was never launched** (no display in the build environment). New screens, charts rendering, dialog sizing, CSS
  appearance, the sidebar search box, double-click on search results and file dialogs are untested beyond compiling and the static FXML check.
* Maven build, `mvn javafx:run`, JaCoCo coverage, JavaFX 25, Mockito 5, **MySQL 8.4 and mysql-connector-j** (tests ran on MySQL 8.0.46 + MariaDB driver).
* PDFs were checked with qpdf/poppler only (not Acrobat, browsers or printers). Standard PDF fonts have no rupee sign, so PDFs show "Rs.".
* Windows/macOS save-dialog behaviour (extension is appended when missing, by code, not tested).

## Incomplete / known limits
* Audit coverage: agreement create/terminate, payment record/void, owner/tenant/property activation changes and maintenance status
  changes are audited. Creating/editing owners, tenants, properties, maintenance details and agreement notes is **not** (ACR-001).
* No audit user identity (the app has no login); `user_id` stays NULL.
* Global search shows at most 15 matches per category; only property rows open a detail dialog. Payment/agreement search results are read-only lists.
* Reminders are in-app only (no email/SMS by design); the window is `rent.reminder.days`.
* Dashboard charts always show the last six calendar months.

## Manual UI checklist (please run once after applying the patch)
1. Dashboard opens with cards and three chart panels; with an empty database each chart shows its empty-state message.
2. Properties -> select a property -> Details: Overview, Agreements, Payments, Maintenance tabs fill in.
3. Type 2+ characters in the sidebar search -> results tabs show counts; double-click a property row opens its details.
4. Agreements: filter by status and search text. Payments: filter by dates/status/text; save a receipt as `.pdf` and as `.txt`.
5. Rent Reminders: overdue (red), due today (amber) and upcoming (blue) rows; Export to PDF and CSV.
6. Maintenance -> Analytics: cards, cost chart, top properties; Reports: pick PDF/CSV and export each report.
7. After applying the migration: record a payment, void it, open Audit History and see both entries; before the migration the screen explains how to enable it.
