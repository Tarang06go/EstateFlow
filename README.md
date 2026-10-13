# EstateFlow — Property & Rental Management (desktop)

Java 25, JavaFX 25, MySQL 8.4 LTS, plain JDBC (DAO pattern), Maven. Architecture: `docs/4.EstateFlow_Architecture.pdf` (Revision 4, locked).

## Features
Owners, tenants, properties (derived occupancy) · rental agreements with generated monthly rent charges ·
partial payments, voiding with reason, receipts · overdue rent and expiring leases · maintenance requests with a status workflow ·
dashboard · CSV exports (payments, overdue rent, occupancy, maintenance).

## Setup
1. Install JDK 25, Maven 3.9+, MySQL 8.4 LTS.
2. Create the schema: `mysql -u root -p < db/schema.sql` (creates database `estateflow`). Optional demo data: `mysql -u root -p estateflow < db/seed.sql`.
3. Copy `db/db.properties.example` to `db.properties` in the project root and enter your credentials (git-ignored).
4. Run: `mvn javafx:run` (start it from the project root so `db.properties` is found; or pass `-Destateflow.db.config=/path/to/db.properties`).

## Tests
* Unit tests (no database): `mvn test`
* MySQL integration + race tests: create `estateflow_test` as described in `db/test-schema-setup.md`, copy `db/db-test.properties.example` to `db-test.properties`, then `mvn verify -Pintegration`.
  The harness refuses any schema whose name does not end in `_test` and never reads `db.properties`.
* Coverage report: `mvn verify` writes `target/site/jacoco`.

## Layout
`model` · `dao` (interfaces) · `dao/jdbc` · `db` (connections/transactions) · `service` + `service/rules` · `controller` (+ `resources/fxml`, `css`) · `report` · `util` · `exception` · `config`.
Rules: controllers call services through `UiTask` only; services never see JDBC; one log point (`ErrorHandler`).

## Status
See `PROGRESS.md`, `REQUIREMENTS_MATRIX.md` and `docs/AI_HANDOVER.md` for what is verified and what is not.

## v1.1 additions

Dashboard charts · property detail tabs (agreements, payments, maintenance) · global search + agreement/payment filters ·
PDF receipts and PDF/CSV reports · rent reminders · maintenance analytics · audit history.

Optional database step for audit history (idempotent, adds one table, back up first):
`mysql -u <user> -p estateflow < db/migrations/v1.1_audit_log.sql`. Without it the app works and simply records no audit entries.
New setting: `rent.reminder.days` (default 7) in `src/main/resources/config/application.properties`.
