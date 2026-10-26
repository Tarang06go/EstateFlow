# EstateFlow — Property & Rental Management (desktop)

Java 25, JavaFX 25, MySQL 8.4 LTS, plain JDBC (DAO pattern), Maven. Architecture: `docs/4.EstateFlow_Architecture.pdf` (Revision 4, locked).

## Features
Owners, tenants, properties (derived occupancy) · rental agreements with generated monthly rent charges ·
partial payments, voiding with reason, receipts · overdue rent and expiring leases · maintenance requests with a status workflow ·
dashboard · CSV exports (payments, overdue rent, occupancy, maintenance).

## Setup
1. Install JDK 25, Maven 3.9+, and MySQL 8.4 LTS.
2. For a fresh, empty database, apply `db/schema.sql`, followed by migrations `v1.1` through `v1.8` in order. Full command sequence, dedicated-account permissions, and optional demo-data guidance are in `docs/RELEASE_AND_BACKUP.md`. Do not seed an existing database.
3. Copy `db/db.properties.example` to `%USERPROFILE%\.estateflow\db.properties` and enter the connection details privately. Do not put credentials in the repository.
4. Run `mvn javafx:run` for development. The Windows app-image packaging and clean install instructions are in `docs/RELEASE_AND_BACKUP.md`.

## Tests
* Unit tests (no database): `mvn test` (includes the authentication, permission and service-boundary tests)
* MySQL integration + race tests: create `estateflow_test` as described in `db/test-schema-setup.md`, copy `db/db-test.properties.example` to `db-test.properties`, then apply BOTH migrations to `estateflow_test` (`mysql -u root -p estateflow_test < db/migrations/v1.2_app_user.sql`) and run `mvn verify -Pintegration`.
  The harness refuses any schema whose name does not end in `_test` and never reads `db.properties`.
* Coverage report: `mvn verify` writes `target/site/jacoco`.

## Layout
`model` · `dao` (interfaces) · `dao/jdbc` · `db` (connections/transactions) · `service` + `service/rules` · `controller` (+ `resources/fxml`, `css`) · `report` · `util` · `exception` · `config`.
Rules: controllers call services through `UiTask` only; services never see JDBC; one log point (`ErrorHandler`).

## Status
See `PROGRESS.md`, `REQUIREMENTS_MATRIX.md` and `docs/AI_HANDOVER.md` for what is verified and what is not.

## Documents and inspections (Phase 5)
Documents and property inspection workflows are implemented. Review `docs/PHASE_5_DOCUMENTS_INSPECTIONS.md` for storage and backup details. Apply `db/migrations/v1.6_documents_inspections.sql` manually after backing up; application startup never applies migrations.

## Vendors and work orders (Phase 6)
Vendor records and work-order workflows build on the existing maintenance requests and Expenses ledger. Review `docs/PHASE_6_VENDORS_WORK_ORDERS.md` for the NOI linkage and rollback details. After applying v1.6, apply `db/migrations/v1.7_vendors_work_orders.sql` manually; startup never applies migrations.

## v1.1 additions

Dashboard charts · property detail tabs (agreements, payments, maintenance) · global search + agreement/payment filters ·
PDF receipts and PDF/CSV reports · rent reminders · maintenance analytics · audit history.

Optional database step for audit history (idempotent, adds one table, back up first):
`mysql -u <user> -p estateflow < db/migrations/v1.1_audit_log.sql`. Without it the app works and simply records no audit entries.
New setting: `rent.reminder.days` (default 7) in `src/main/resources/config/application.properties`.

## Authentication and roles (Phase 1)

EstateFlow now requires sign-in. Everything below is single-user-at-a-time desktop behaviour: no web sessions, no multi-tenant features.

### 1. Apply the database migration (existing databases)
Back up first, then add the one new table. Existing owner/tenant/property/agreement/payment/maintenance/audit data is not touched; the script is safe to run twice and creates no account and no password.
```
mysqldump -u <user> -p estateflow > estateflow_backup_before_v1.2.sql
mysql -u <user> -p estateflow < db/migrations/v1.2_app_user.sql
```
Rollback: `DROP TABLE app_user;` (the app then refuses to start until the table exists). New installs: run `db/schema.sql`, then the migrations.
If the table is missing the sign-in screen says so and tells you which script to run.

### 2. First administrator
Nothing is preset. On first start with no active administrator the sign-in screen switches to **Set up EstateFlow**: choose a username and a password (at least 10 characters with a letter and a digit, not containing the username). That creates the first ADMIN and signs you in. The setup option disappears as soon as an active administrator exists. The password is hashed immediately and never stored or logged in plaintext.
If every administrator were ever deactivated by editing the database directly, the setup screen simply appears again, so you cannot be locked out. Forgotten administrator password: another administrator resets it in **Users**; if there is none, deactivate the admin row in MySQL (`UPDATE app_user SET is_active = 0 WHERE role = 'ADMIN';`) so the setup screen appears and create a new one.

### 3. Signing in and out
Enter username and password (show/hide toggle available). Every failure (unknown user, wrong password, deactivated account) shows the same message, "Invalid username or password." **Sign out** is at the bottom of the sidebar; it clears the session and returns to the sign-in screen.

### 4. Roles
| | ADMIN | MANAGER | VIEWER |
|:---|:---:|:---:|:---:|
| View dashboard, records, searches, reports/exports, receipts | yes | yes | yes |
| Create/edit/deactivate owners, tenants, properties, agreements, maintenance | yes | yes | no |
| Record and void payments | yes | yes | no |
| Activity History | yes | yes | no |
| Settings (display preferences) | yes | yes | no |
| Users: create, change role, activate/deactivate, reset password | yes | no | no |

The mapping lives in one place: `model/enums/Role.java` (roles) and `security/Permission.java` (permissions).

### 5. User management (administrators)
**Users** in the sidebar: list accounts with status and last sign-in, add a user, change a role, activate/deactivate, reset a password. Rules: usernames are unique (case-insensitive); the last active administrator cannot be deactivated or demoted; you cannot deactivate or change the role of your own account; a password reset sets a new password chosen by the administrator (the old one is never shown or recoverable); password hashes are never displayed.

### 6. Security notes
* Passwords: PBKDF2-HMAC-SHA256, 600,000 iterations, random 16-byte salt per password, constant-time comparison (JDK built-in, no new dependency).
* Enforcement is in the **services**: every service method checks the current session's role (`security/Authorizer`) before touching a DAO, so unauthorized calls are rejected even if the UI is bypassed. Hidden buttons are a convenience only. `SceneNavigator` also refuses to load any screen except sign-in without a session.
* Sessions are in memory for the running application and end at sign-out or exit. There is no idle timeout and no failed-attempt lockout yet.
* No credentials are in source control. `db.properties` and `db-test.properties` stay git-ignored.

### 7. Phase 1 test commands
* Unit tests: `mvn test` (new: `PasswordHasherTest`, `PasswordPolicyTest`, `RolePermissionTest`, `AuthServiceTest`, `UserManagementServiceTest`, `ServiceAuthorizationTest`).
* Real-MySQL tests (test database only): `mvn verify -Pintegration` (new: `AuthIntegrationTest`). They truncate `app_user` in `estateflow_test` and refuse any schema not ending in `_test`.

## Audit history and activity tracking (Phase 2)

Every important change is recorded in `audit_log` with **who** did it (user id and username), **what** (action, record type, record number), **when** (UTC) and the **previous / new values**. Entries cannot be edited or deleted from the application.

### Apply the migration (manual, never automatic)
Back up first, then run it against your real database yourself. It adds one nullable column and two indexes; existing audit rows are kept and simply show no user. Safe to run twice. Requires v1.1 and v1.2 to be applied.
```
mysqldump -u <user> -p estateflow > estateflow_backup_before_v1.3.sql
mysql -u <user> -p estateflow < db/migrations/v1.3_audit_actor.sql
```
Rollback: `ALTER TABLE audit_log DROP INDEX idx_audit_user, DROP INDEX idx_audit_actor, DROP COLUMN actor_username;`
Until it is applied, EstateFlow keeps working and keeps writing entries, but without the user; the Activity History screen says so.

### What is recorded
* Owners, tenants, properties, agreements, payments (record and void) and maintenance: as before, now with the signed-in user.
* Accounts (new): user created, role changed, deactivated / reactivated, password reset (no password material is ever stored), first administrator set up.
* Sign-in activity (new): signed in, signed out, and failed sign-in for an existing account (the reason is visible to administrators only).
* EstateFlow has no hard deletes; "delete" is deactivation, which is recorded.

### Who can see it
ADMIN and MANAGER can open **Activity History** (filter by record type, record number and user). Account and sign-in events (type USER) are visible to ADMIN only. VIEWER has no access. This is enforced in `AuditService`, not just in the screen.

### Failure behaviour
A change and its audit entry are written in one transaction: if the audit entry cannot be written, the change is rolled back, so nothing changes unaudited. Sign-in, sign-out and failed-sign-in entries are best effort: a failure to record them is logged and never blocks signing in or out.

### Phase 2 tests
Unit: `AuditActorTest` (`mvn test`). Real MySQL, test database only: `AuditActorIntegrationTest` (`mvn verify -Pintegration`, after applying v1.1, v1.2 and v1.3 to `estateflow_test`; the tests skip themselves if v1.3 is missing).

## Expenses and property profitability (Phase 3)

### Apply the migration (manual, never automatic)
Back up first, then run it against your real database yourself. It adds ONE new table, `expense`; nothing existing is touched; safe to run twice. Until it is applied the Expenses and Profitability screens say so and everything else keeps working.
```
mysqldump -u <user> -p estateflow > estateflow_backup_before_v1.4.sql
mysql -u <user> -p estateflow < db/migrations/v1.4_expense.sql
```
Rollback: `DROP TABLE expense;` (this removes every recorded expense; export it first with `mysqldump -u <user> -p estateflow expense > expense_backup.sql`).

### Expenses
**Expenses** (sidebar, Financials) lists costs per property with filters for property, category and date range, and an optional "Show deleted". Categories: Maintenance, Utilities, Insurance, Property tax, Repairs, Management fees, Other. Each expense has amount (greater than zero, at most 2 decimals), date (not in the future, not before 2000), category, description, payment method and an optional reference number.
* ADMIN and MANAGER can add, edit and delete; VIEWER can only look. Enforced in `ExpenseService` (`MANAGE_RECORDS` / `VIEW_DATA`), not just by hiding buttons.
* **Delete is a soft delete**: the row stays, flagged with who/when (audit log) and a required reason; it stops counting everywhere and can be shown with "Show deleted".
* Every create, edit and delete is audited with the acting user, in the same transaction as the change.
* An identical expense (same property, category, amount, date and description) is refused as a likely double entry.
* **Maintenance link.** An expense can be linked to the maintenance request it paid for (same property; Maintenance or Repairs category). At most one active expense can be linked to a request (enforced by a database constraint), so a repair cannot be entered twice.

### Profitability
**Profitability** (sidebar, Insights) shows, for a chosen period and optionally one property: rent received, rent charged, operating expenses, NOI, a monthly income/expense/NOI trend, expense breakdown by category, and a property-by-property comparison; plus a CSV/PDF export of the property table. The dashboard also shows this month's NOI.

### Accounting assumptions
1. **Cash basis.** *Rent received* = valid (not voided) payments by **payment date**. *Rent charged* = non-cancelled rent charges by **due date**, shown only for comparison; the two differ whenever rent is paid late or early.
2. **Only rent is income.** Every payment belongs to a rent charge, so deposits and other non-rent money are not income. Security deposits do not exist in EstateFlow yet (Phase 4); when added they must be stored outside `payment` so this exclusion keeps holding. Voided payments are excluded.
3. **Expenses** = active expenses by **expense date** (deleted ones excluded) **plus** the cost of maintenance requests that are *Resolved* (by resolved date) and have **no active expense linked**. If you link an expense, that expense replaces the request's own cost, so the repair is counted once. Costs on in-progress requests are not yet counted. (The older Maintenance Analytics screen counts in-progress and resolved costs and so can differ.)
4. **NOI** = rent received - operating expenses. All expense categories are operating costs. NOI excludes financing (loan interest and repayments), income tax, depreciation and capital improvements; do not record those as expenses (the data model has no separate flag for them). NOI is not total profit.
5. Both ends of the period are inclusive. Amounts are in one currency, rounded to 2 decimals. A property appears in the comparison if it is active or had activity in the period.
6. All figures for one report are read in a single database transaction (one consistent snapshot).

### Phase 3 tests
Unit: `ExpenseServiceTest`, `ProfitabilityServiceTest` (`mvn test`). Real MySQL, test database only: `ExpenseProfitabilityIntegrationTest` (`mvn verify -Pintegration`, after applying v1.1-v1.4 to `estateflow_test`; skips itself if v1.4 is missing).

## Security deposits and lease management (Phase 4)

### Apply the migration (manual, never automatic)
Back up first, then run it against your real database yourself. It adds three tables (`security_deposit`, `deposit_transaction`, `lease_change`) and one nullable column on `rental_agreement` (`renewed_from_agreement_id`, with a foreign key and unique key). No existing row is changed. Safe to run twice. Requires the base schema; v1.1-v1.4 should be applied. Until it is applied everything else keeps working: the Deposits screen and the renew/extend buttons say the migration is needed, and the agreement queries behave exactly as before.
```
mysqldump -u <user> -p estateflow > estateflow_backup_before_v1.5.sql
mysql -u <user> -p estateflow < db/migrations/v1.5_deposits_leases.sql
```
Rollback (this permanently deletes all deposit and lease-change records, so export them first with `mysqldump -u <user> -p estateflow security_deposit deposit_transaction lease_change > deposits_backup.sql`):
```
DROP TABLE lease_change;
DROP TABLE deposit_transaction;
DROP TABLE security_deposit;
ALTER TABLE rental_agreement DROP FOREIGN KEY fk_agreement_renewed_from, DROP INDEX uq_agreement_renewed_from, DROP COLUMN renewed_from_agreement_id;
```

### Security deposits
`rental_agreement.security_deposit` is still the amount **agreed in the lease**. The money actually held is a ledger: one deposit per agreement (enforced by the database) with entries for **collections, deductions, refunds** and renewal **carry-overs**. Open it from **Deposits** (sidebar) or **Agreement > Security deposit...**.
* **Balance** = collected + carried in - deductions - refunds - carried out, ignoring voided entries. "Original amount" = collected + carried in.
* Rules: amounts above zero with at most 2 decimals; dates not in the future and not before 2000; deductions and refunds can never exceed the balance (checked under a row lock, so two people cannot over-spend it); collections cannot exceed the deposit agreed in the lease and cannot be recorded on an ended lease; a deduction needs a reason (and may carry a supporting reference); refunds are possible only once the lease has ended or been terminated; entry dates cannot precede the first collection.
* **History is never overwritten or deleted.** A mistake is corrected by **voiding** the entry (reason required); it stays in the ledger, flagged void, and no longer counts. A collection that has already been partly deducted or refunded cannot be voided until the later entries are. Carry-over entries cannot be voided.
* ADMIN and MANAGER can record; VIEWER can only look (`RECORD_PAYMENTS` / `VIEW_DATA`, enforced in `DepositService`). Every action is audited (type DEPOSIT) with the acting user in the same transaction as the change.
* **Deposits are a liability, never income.** They live in their own tables, which the rent-income, expense and NOI queries never read, so deposits cannot enter rent collection totals or NOI. Deductions are not recognised as income in EstateFlow either; if you keep a deduction you may account for it as other income outside EstateFlow.
* **Deposits screen:** refundable liability (all balances held now), collected / deducted / refunded in a chosen period (voids and carry-overs excluded), deposits by agreement, tenant or property, and leases that require a deposit that has not been collected yet ("Not collected"). The dashboard shows the liability.

### Lease renewal and extension
Open an agreement and use **Renew lease...** or **Extend lease...** (ADMIN and MANAGER; `MANAGE_RECORDS`).
* **Renew** creates a NEW agreement that starts the day after the old one ends, with a new length, monthly rent and required deposit, linked to the old one. The old agreement and its rent history are not touched. You can carry the deposit held over to the new lease: this is a ledger transfer (carried out / carried in), not new money, so the liability does not change and it is not counted as a collection.
* **Extend** adds months to an agreement that has not ended, at the same rent; only the new rent periods are added and the previous end date is kept in the lease history. (To change the rent, renew instead.)
* Every renewal and extension records the previous and new dates and rent and the acting user, in the **lease history** (shown on the agreement) and in the audit log.
* Not allowed: terminated agreements; agreements already renewed; agreements that have not started; extending an ended lease (renew it instead); renewing a lease that ended more than 30 days ago (create a new agreement); anything that would overlap another agreement on the same property (BR-13); inactive property or tenant; more than 120 months in total.
* **Statuses.** Active, Expiring soon, Expired and Terminated work as before. **Renewed** is new: a started lease that has a renewal agreement. Renewed leases are no longer reported as expiring or expired. The agreement list has a "Renewed" filter.
* Lease terms were previously editable only for notes (BR-16). They now also change through the dedicated Extend and Renew workflows, which keep the history; nothing else can edit terms.

### Expiry and attention reports
**Expiring leases** now has a period selector (30, 60, 90 or 180 days) and a second list, **Ended leases needing attention**: ended (or terminated early), not renewed, and with rent still owing or a deposit still held.

### Phase 4 tests
Unit: `DepositServiceTest`, `LeaseRenewalServiceTest` (`mvn test`). Real MySQL, test database only: `DepositLeaseIntegrationTest` (`mvn verify -Pintegration`, after applying v1.1-v1.5 to `estateflow_test`; it skips itself if v1.5 is missing).

## In-app notifications (Phase 7)

Open **Notifications** from the sidebar to see current unpaid-rent reminders, agreements ending within the configured lease-warning period, and overdue work orders (when the v1.7 work-order schema is available). Use **Refresh** to recompute the list from saved records, or **Open selected** to navigate to the related work area. Stable keys collapse duplicate source rows.

These are generated reminders, not delivery receipts. EstateFlow does not send email, SMS, push or desktop notifications, and it does not persist a sent/delivered state. The list can change when source records, dates, or category preferences change. There is no inspection reminder because inspections do not currently have a scheduled date.

ADMIN and MANAGER can set the local display preferences in **Settings**. Rent, lease, and overdue work-order categories are enabled by default and stored in `~/.estateflow/preferences.properties` (Windows: `%USERPROFILE%\\.estateflow\\preferences.properties`). They do not change database records. No schema migration is needed for Phase 7; work-order reminders are omitted until v1.7 is available.

### Phase 7 verification
`NotificationServiceTest`, `UserPreferencesTest`, and the configured lease-warning window test run under `mvn test`. The full suite passed on Java 25.0.4: 253 tests, 0 failures, 0 skipped. Database integration and a real-display UI check remain unverified.

## Advanced Analytics (Phase 8)

**Advanced Analytics** in Insights summarizes charge-matched rent collection, current arrears aging, active-property occupancy, leases ending within the configured warning window, maintenance costs, and completed-work-order vendor performance. CSV and PDF exports include actual monthly cash flow and a separate six-month run-rate estimate. The forecast is a flat average of actual monthly rent receipts and operating expenses over the selected date range; it assumes no growth or future changes, and is not a prediction. For definitions and limitations, see `docs/PHASE_8_ANALYTICS.md`. No database migration is required. Vendor metrics require v1.7; otherwise that section is unavailable.

### Phase 8 verification
`AdvancedAnalyticsServiceTest` runs under `mvn test`; guarded MySQL assertions are included with the profitability and work-order integration suites. The full suite passed on Java 25.0.4: 258 tests, 0 failures, 0 skipped. MySQL integration and a real-display UI check remain unverified.

## Property Assistant (Phase 9)

The local **Property Assistant** answers supported questions about rent and arrears, expiring leases, occupancy, maintenance/work orders, and cash-flow trends. It uses deterministic keyword routing and the same authorized services as the screens; it is not connected to a generative AI provider. Questions and answers are transient and are not saved or sent externally. Unsupported questions receive a list of supported topics. See `docs/PHASE_9_ASSISTANT.md` for details.

### Phase 9 verification
`PropertyAssistantServiceTest` covers report-backed answers, lease summaries, input limits, and authorization. The full suite passed on Java 25.0.4: 262 tests, 0 failures, 0 skipped. Real-display UI verification remains outstanding.

## Release and recovery (Phase 10)

Windows x64 app-image packaging is scripted in `scripts/package-windows.ps1`; it bundles the Java runtime and runtime dependencies. Database credentials stay in `%USERPROFILE%\.estateflow\db.properties`, outside the package. Documents and logs also stay under the user profile. Setup, safe backup/restore drills, migration handling, and release checks are in `docs/RELEASE_AND_BACKUP.md`.

### Phase 10 verification
The Windows app image was generated and its launcher, embedded runtime and dependency classpath were checked. `mvn clean verify` stopped during clean because a generated PNG in `target/` was locked; `mvn verify` then succeeded with 266 tests, 0 failures and 0 skipped. No MySQL integration run or real-display database smoke test was performed.

## Rent collection insights (Phase 11)

Advanced Analytics now separates live charges by due date from valid receipts by payment date, shows receipts applied to earlier charges, reconciled property/tenant breakdowns, current arrears aging, follow-up balances, and descriptive late-settlement history. Tenant-level insight rows and exports require `VIEW_TENANT_INSIGHTS` (Administrators and Managers); Viewers get aggregate/property output without tenant details. Definitions are in `docs/PHASE_11_RENT_COLLECTION_INSIGHTS.md`. No migration is required.

## Optional facial verification (Phase 12)

Optional facial verification is implemented with local YuNet/SFace models, Windows DPAPI key protection, consent, reauthentication, rate limits, encrypted templates, and the manual v1.8 migration. The checked-in default is intentionally `face.login.enabled=false`; a local test may explicitly set `-Destateflow.face.login.enabled=true` after completing the documented setup. Facial login has no validated liveness or presentation-attack protection and is not approved as a production authenticator. See `docs/FACIAL_VERIFICATION_IMPLEMENTATION_STATUS.md` and `docs/RELEASE_AND_BACKUP.md`.

## Version 1.2.0 database correction

Rent Collection Insights now binds its late-settlement query parameters correctly; see `docs/DATABASE_FIX_RENT_COLLECTION_INSIGHTS.md`. CSV exports neutralize formula-leading text. No automatic restore or migration is performed.

## Version 1.3.0 and clean Windows setup

The Windows x64 app-image package bundles a Java 25 runtime and native/runtime dependencies. It does not bundle MySQL or the optional face model files. For a clean database installation, manual migration order, configuration, face-login limitations, and backup/restore guidance, follow `docs/RELEASE_AND_BACKUP.md`.
