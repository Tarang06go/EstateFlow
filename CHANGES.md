# EstateFlow - Phase 4 changes (Security Deposits and Advanced Lease Management)

Contains only new and modified files, relative to the Phase 3 project state (Phase 1 ZIP + Phase 2 and Phase 3 change ZIPs applied).

## How to apply
1. Back up your project folder (or commit it to Git first).
2. Unzip over your project root (the `EstateFlow/` folder). It overwrites the 19 modified files listed below (this `CHANGES.md` replaces the Phase 3 one) and adds the new ones.
3. **Database (manual; nothing runs automatically, and the application never executes migration files).** Back up, then apply the migration to your real database yourself:
   ```
   mysqldump -u <user> -p estateflow > estateflow_backup_before_v1.5.sql
   mysql -u <user> -p estateflow < db/migrations/v1.5_deposits_leases.sql
   ```
   Adds three tables (`security_deposit`, `deposit_transaction`, `lease_change`) and one nullable column on `rental_agreement`
   (`renewed_from_agreement_id`, with a foreign key and a unique key). No existing row is changed. Safe to run twice (the second run prints
   "already present"). Until it is applied, everything else works as before and the new screens/buttons say the migration is needed.
4. **Rollback** (permanently deletes all deposit and lease-change records, so export them first:
   `mysqldump -u <user> -p estateflow security_deposit deposit_transaction lease_change > deposits_backup.sql`):
   ```
   DROP TABLE lease_change;
   DROP TABLE deposit_transaction;
   DROP TABLE security_deposit;
   ALTER TABLE rental_agreement DROP FOREIGN KEY fk_agreement_renewed_from, DROP INDEX uq_agreement_renewed_from, DROP COLUMN renewed_from_agreement_id;
   ```
   Renewed agreements remain ordinary agreements; only the link between them is lost.
5. Integration tests: apply the same migration to `estateflow_test` (never `estateflow`), then `mvn verify -Pintegration`.
6. Build and test: `mvn clean verify` (not run by me, see below).

## New files
| File | Purpose |
|:---|:---|
| `db/migrations/v1.5_deposits_leases.sql` | The three tables and the renewal link column; CHECK constraints (amounts > 0, types, methods, deduction needs a reason, void needs a reason), `UNIQUE(agreement_id)` so there is one deposit per agreement. Idempotent, documented, with rollback. |
| `model/DepositTransaction.java`, `model/LeaseChange.java`, `model/enums/DepositType.java`, `model/enums/DepositStatus.java` | Ledger entry (immutable; voided, never edited), lease history entry, entry types (collection, deduction, refund, carry-in/out), derived status (not collected, held, settled). |
| `dto/DepositTotals.java`, `DepositRow.java`, `DepositActivityRow.java`, `DepositReport.java`, `LeaseAttentionRow.java` | Balances (original, deductions, refunds, carried out, refundable balance), per-agreement position, period activity, deposit report with grouping totals, attention-list row. |
| `dao/DepositDao.java`, `dao/jdbc/JdbcDepositDao.java` | Ledger persistence: one deposit per agreement, row lock for balance checks, append-only entries with void, positions and period activity. |
| `dao/LeaseDao.java`, `dao/jdbc/JdbcLeaseDao.java` | Renewal link, term extension, lease history, ended-leases-needing-attention query. |
| `service/DepositService.java` | Collect, deduct, refund, void with all rules (see README), audit with actor in the same transaction; liability and period report; grouping by agreement/tenant/property. Reads need VIEW_DATA, changes need RECORD_PAYMENTS (VIEWER read-only). |
| `service/LeaseRenewalService.java` | Renew (new linked agreement, optional deposit carry-over by ledger transfer), extend (same rent, extra periods), eligibility reasons, history, expiry windows (1-365 days), attention list. Changes need MANAGE_RECORDS. |
| `controller/DepositsController.java`, `DepositDetailController.java`, `DepositFormController.java`, `DepositVoidController.java`, `LeaseRenewController.java` | Deposits screen (KPIs, held deposits by agreement/tenant/property, period activity), per-agreement deposit ledger dialog, collect/deduct/refund form with confirmations, void dialog, renew/extend dialog with live date preview and confirmation. |
| `resources/fxml/deposits.fxml`, `deposit-detail.fxml`, `deposit-form.fxml`, `deposit-void.fxml`, `lease-renew.fxml` | Layouts in the existing green/cream style. |
| `src/test/.../service/DepositServiceTest.java` | 13 unit tests: collection/deduction/refund rules, validation and boundaries, over-deduct/over-refund, voids, authorization (ADMIN/MANAGER/VIEWER/signed-out), audit attribution, audit failure, report and grouping, empty data. |
| `src/test/.../service/LeaseRenewalServiceTest.java` | 13 unit tests: renewal dates/links/audit/lease history, ineligible agreements, 30-day grace boundary, overlap, validation, duplicate renewal, deposit carry-over and its limits, extension and limits, authorization, audit failure, missing migration, expiry window arguments. |
| `src/test/.../service/DepositLeaseIntegrationTest.java` | 16 real-MySQL tests (test DB only): deposit cap and one-deposit-per-agreement, deductions/refunds/partial/full/over-spend, refund timing and date rules, voids, VIEWER/MANAGER with real logins and audit actor, rollback on audit failure, deposits do not change rent income/expenses/NOI, report/liability/grouping, empty database, renewal with deposit carry-over (original untouched, charges, link, status, history, audit), ineligible/overlapping renewals, input validation, extension (charges kept, history), extension limits and last-day boundary, inclusive 30/31-day expiry windows and renewed leases dropping out, ended-leases-needing-attention. Skips itself if v1.5 is missing. |
| `src/test/.../support/FakeDepositDao.java` | In-memory ledger for unit tests. |

## Modified files
| File | Change |
|:---|:---|
| `model/RentalAgreement.java` | Renewal links (`renewedFrom`, `renewedBy`); effective status returns RENEWED for a started, non-terminated lease that has a renewal. |
| `model/enums/EffectiveAgreementStatus.java`, `AgreementFilter.java` | New RENEWED status and "Renewed" list filter. |
| `dao/jdbc/JdbcAgreementDao.java` | Loads the renewal links; renewed leases excluded from the expiring and expired queries; uses the old SQL unchanged until the v1.5 column exists. |
| `service` wiring: `config/AppContext.java` | Creates `DepositService` and `LeaseRenewalService` (`deposits()`, `leases()`). |
| `controller/AgreementDetailController.java`, `resources/fxml/agreement-detail.fxml` | Extend / Renew / Security deposit buttons (disabled with the reason when not allowed), renewal link, deposit summary, lease history; extras never break the screen before the migration; notes label updated (terms now change only through Extend/Renew). |
| `controller/ExpiringLeasesController.java`, `resources/fxml/expiring.fxml` | Period selector (30/60/90/180 days) for leases ending soon; second list "Ended leases needing attention" (ended, not renewed, rent owing or deposit held); open-agreement buttons. |
| `controller/MainController.java`, `util/SceneNavigator.java` | Sidebar "Deposits"; screen guards: deposit entry/void dialogs need RECORD_PAYMENTS, renew/extend dialog needs MANAGE_RECORDS. |
| `controller/DashboardController.java` | "Deposits held (liability)" card; skipped silently before the migration. |
| `controller/AuditController.java`, `util/ActivityText.java` | DEPOSIT record type and wording for collected / deduction / refunded / voided / carried / extended / renewed. |
| `src/test/.../support/TestDatabase.java` | Test cleanup also truncates the three v1.5 tables (test database only). |
| `README.md`, `IMPLEMENTATION_PROGRESS.md`, `AGENTS.md`, `CHANGES.md` | Phase 4 documentation and status. |

## Financial correctness
* Deposits are a **liability**. They live in their own tables; the rent-income, expense and NOI queries never read them. A deposit collection, deduction or refund creates no `payment` row and no `expense` row. Verified by an integration test comparing rent received, rent charged, expenses, NOI and monthly income before and after deposit activity.
* No double counting: deposit totals ignore voided entries; renewal carry-overs are ledger transfers (liability unchanged) and are excluded from "collected" and "refunded" in period reports.
* Multi-record operations (collect, deduct, refund, void, renew with carry-over, extend) run in one transaction together with their audit entry; a failed audit write rolls everything back (tested).

## Business rules added
Deposits: DEP-01 nothing collected yet; DEP-02 no collection on an ended lease; DEP-03 collections cannot exceed the agreed deposit; DEP-04 deduction/refund cannot exceed the balance; DEP-05 refund only after the lease ends or is terminated; DEP-06 cannot void a collection already used; DEP-07 carry-overs cannot be voided; DEP-08 already void; DEP-09 date before first collection.
Leases: LR-01 ineligible (terminated, already renewed, not started, ended too long ago / ended when extending); LR-02 already renewed; LR-05 carried deposit larger than the new required deposit; LR-07 over 120 months; BR-13 overlap (existing rule, re-checked with the property locked); BR-05 inactive property/tenant (existing rule).

## Behaviour notes and limitations
* Extension keeps the rent; to change rent, renew. Renewal grace is 30 days after the lease ends (constant in `LeaseRenewalService`).
* A deposit cannot be collected for an agreement whose lease has ended; if the lease lists no deposit amount (0), any amount may be collected.
* Deductions retained by the landlord are not recognised as income in EstateFlow.
* BR-16 (only notes editable) is intentionally relaxed for the dedicated Extend/Renew workflows, which keep history.

## Verification (honest status)
Run with `javac --release 25` and the JUnit Platform console launcher, MySQL 8.0.46 on port 3307, MariaDB JDBC driver, JavaFX 11:
* Unit: 231 / 231 passed (205 existing + 26 new).
* Integration (`estateflow_test` only): 83 / 83 passed, 0 failed, 0 skipped (67 existing + 16 new).
* Headless JavaFX smoke (Xvfb): 83 / 83 checks passed (Deposits, expiry, agreement detail, deposit ledger, renew/extend dialogs, role visibility, blocked dialogs, dashboard cards).
* Mutation checks: removing the over-spend guard, the voided-entry filter, the "renewed leases are not expiring" filter, or the renewal overlap check each made exactly the relevant integration test fail.
* Migration: applied twice to a scratch copy holding your seed data: existing row counts unchanged, second run a no-op, all new objects present.

**Not verified:** `mvn clean verify` (Maven Central is unreachable here; the build stops resolving `jacoco-maven-plugin`), MySQL 8.4 / Connector/J / JavaFX 25, and a visual check of the screens on a real display. The v1.5 rollback script was reviewed but not executed.

## Facial verification 1.3.0 (current change set)

- Added optional local YuNet/SFace face enrollment and matching, AES-GCM encrypted templates, DPAPI-protected per-Windows-profile keys, versioned consent, password reauthentication, local disable, revocation, and cancellation-aware single-use authentication proofs.
- Added manual additive migration `db/migrations/v1.8_face_enrollment.sql` and integrity-pinned model download instructions.
- The app-wide `face.login.enabled` safety gate defaults to `false`; password sign-in remains available. No liveness detection or spoof resistance is claimed.
- Verification: 32 focused facial/authentication/config tests passed, including a real Windows DPAPI protect/unprotect round trip. A native smoke run initialized YuNet/SFace. Full verification ran 293 tests: 292 passed, one existing Windows temp-file rename errored because the sandbox denied it. `mvn clean verify` could not clean while the running app held a target icon open. Physical-camera enrollment, disposable-database migration, and final app-image checks remain unverified. The reported latest post-capture failure was not present in the available logs; stage-coded diagnostics now preserve its cause safely for a retry.
- See `IMPLEMENTATION_GUIDE.md`, `FILE_MANIFEST.md`, and `docs/FACIAL_VERIFICATION_IMPLEMENTATION_STATUS.md` for application and verification details.
