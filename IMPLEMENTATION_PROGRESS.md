# EstateFlow - Implementation Progress (roadmap phases)

| Phase | Scope | Status |
|:---|:---|:---|
| 0 | Baseline verification | Not run as a separate phase (see PROGRESS_REPORT.md for the v1.2 state) |
| 1 | Authentication and role-based access control | **IMPLEMENTED - awaiting your review** |
| 2 | Audit history and activity tracking | **IMPLEMENTED - awaiting your review** |
| 3 | Expense management and property profitability | **IMPLEMENTED - awaiting your review** |
| 4 | Security deposits and advanced lease management | **IMPLEMENTED - awaiting your review** |
| 5 | Document management and property inspections | **IMPLEMENTED - unit verified; integration/UI verification pending** |
| 6 | Maintenance vendors and work orders | **IMPLEMENTED - unit verified; integration/UI verification pending** |
| 7 | In-app notifications and reminders | **IMPLEMENTED - unit verified; delivery and display verification pending** |
| 8 | Advanced analytics and financial reporting | **IMPLEMENTED - unit verified; MySQL/display verification pending** |
| 9 | Optional property management assistant | **IMPLEMENTED - unit verified; UI verification pending** |
| 10 | Packaging, backup, recovery and release hardening | **IMPLEMENTED - release package verified; database/display smoke checks pending** |
| 11 | Advanced rent collection insights | **IMPLEMENTED - unit verified; MySQL integration pending** |
| 12 | Optional facial verification | **SAFELY DISABLED - no biometric provider or storage; password login unchanged** |
| 13 | Final packaging, backup/restore, security and release hardening | **IMPLEMENTED - 1.2.0 release built; isolated integration/operational checks pending** |

## Phase 1 summary
* New table `app_user` via `db/migrations/v1.2_app_user.sql` (additive, idempotent, no data touched, no default account).
* PBKDF2-HMAC-SHA256 hashing (`security/PasswordHasher`), password/username policy, `Session` + `Authorizer`, roles ADMIN / MANAGER / VIEWER.
* Authorization enforced at the service boundary in all 12 existing services; `AuthService` (sign-in, sign-out, first-admin setup) and `UserManagementService` (admin only).
* UI: sign-in / first-run setup screen, sign-out and signed-in user in the sidebar, Users screen (admin only), write controls hidden for roles that cannot use them, navigator blocks protected screens without a session.
* Existing services gained a trailing `Authorizer` constructor argument; existing tests were updated to pass a signed-in test authorizer.
* The dashboard shows "recent activity" only to roles that may view the audit history.

## Verification
See the Phase 1 delivery report. `mvn clean verify` was attempted with Java 25 but cannot run where Maven Central is unreachable (it stops resolving `jacoco-maven-plugin`). Tests were compiled with `javac --release 25` and run with the JUnit Platform console launcher: 162/162 unit, 50/50 integration (`estateflow_test`), 38/38 headless JavaFX smoke checks. Run `mvn clean verify` and `mvn verify -Pintegration` on your machine to confirm.

## Known limitations
* No failed-login throttling, idle timeout or self-service password change.
* Settings are local display preferences, so the Viewer restriction on them is enforced in the UI only.
* Review pass: `V11IntegrationTest` (audit entries) was a stale test, not a bug. The earlier v1.2 redesign made property and maintenance creation write `CREATED` audit entries; the test still expected v1.1 behaviour. Its two assertions were updated; application behaviour is unchanged.
* Review pass also added: navigator refusal of write dialogs for read-only roles, and an upper bound on the stored PBKDF2 iteration count.

## Phase 2 summary
* Migration `db/migrations/v1.3_audit_actor.sql`: `audit_log.actor_username` plus indexes (additive, idempotent, applied manually). `audit_log.user_id` (from v1.1) now holds the acting user's id.
* `AuditService` takes the actor from the session (never from callers), adds filtered `search`, hides account events from non-administrators, and has a best-effort write path for sign-in events.
* New audit events: user create / role change / (de)activation / password reset / first-admin setup, sign-in, sign-out, failed sign-in.
* Activity History screen: "By" column, user filter, record-type filter (USER for administrators only), notice when the migration is missing.
* Verified (javac + JUnit console, not Maven): 177/177 unit, 56/56 integration on `estateflow_test` (MySQL 8.0 on port 3307), 42/42 headless JavaFX smoke checks.
* Unverified: `mvn clean verify` (Maven Central unreachable in the build environment); real-display look of the screen; MySQL 8.4 / Connector/J / JavaFX 25 (tested with MySQL 8.0, MariaDB driver, JavaFX 11).

## Phase 3 summary
* Migration `db/migrations/v1.4_expense.sql`: new `expense` table (soft delete, optional maintenance link, one active expense per request). Applied manually.
* `ExpenseService` (validation, soft delete, audit with actor, duplicate and double-entry guards; VIEWER read-only) and `ProfitabilityService` (cash-basis rent received vs charged, expenses, NOI, category / property / monthly breakdowns, CSV/PDF export, single-snapshot reads).
* Screens: Expenses (filters, add/edit/delete dialogs), Profitability (KPIs, trend, category and property charts, export); dashboard shows this month's NOI.
* Maintenance costs are never counted twice: a resolved request's own cost counts only when no active expense is linked to it.
* Verified (javac + JUnit console, not Maven): 205/205 unit, 67/67 integration on `estateflow_test` (MySQL 8.0, port 3307), 59/59 headless JavaFX smoke checks; the new SQL tests were mutation-checked.
* Unverified: `mvn clean verify` (Maven Central unreachable in the build environment); MySQL 8.4 / Connector/J / JavaFX 25; a look at the screens on a real display.

## Phase 4 summary
* Migration `db/migrations/v1.5_deposits_leases.sql`: `security_deposit`, `deposit_transaction`, `lease_change`, and `rental_agreement.renewed_from_agreement_id`. Additive, idempotent, applied manually.
* `DepositService`: append-only deposit ledger (collect, deduct, refund, void), balance checks under a row lock, duplicate-deposit prevention, liability and period reports; deposits never enter rent income or NOI.
* `LeaseRenewalService`: renew (new linked agreement, optional deposit carry-over by ledger transfer), extend (same rent, extra rent periods), lease history, expiry window (30/60/90/180 days), ended-leases-needing-attention list.
* New derived status RENEWED; renewed leases drop out of the expiring/expired lists; agreement queries stay unchanged until the migration is applied.
* Screens: Deposits, deposit detail/entry/void dialogs, renew/extend dialog, agreement detail (deposit, renewal link, lease history), expiring-leases with periods and attention list; dashboard deposit-liability card.
* Verified (javac + JUnit console, not Maven): 231/231 unit, 83/83 integration on `estateflow_test` (MySQL 8.0, port 3307), 83/83 headless JavaFX smoke checks; the key SQL/rules were mutation-checked.
* Unverified: `mvn clean verify` (Maven Central unreachable in the build environment); MySQL 8.4 / Connector/J / JavaFX 25; a look at the screens on a real display.

## Phase 5 summary
* Added manual migration `db/migrations/v1.6_documents_inspections.sql` for document metadata, inspections and structured findings; no startup migration or database was run.
* Added authorized document upload/search/open/replacement/archive workflows with generated storage names, PDF/PNG/JPEG signature checks, a 20 MiB streaming limit, symlink refusal, private local storage permissions where supported, and audit entries.
* Added property inspection drafts, structured observations and conditions, immutable completion/history, and creation/linkage of existing maintenance requests for poor/urgent findings.
* Added Documents and Inspections screens. Backup/recovery and migration rollback instructions are in `docs/PHASE_5_DOCUMENTS_INSPECTIONS.md`.
* Verified with `mvn test` on Java 25.0.4: 238 tests passed, 0 failures, 0 skipped. MySQL integration and display-based UI testing remain unverified; the migration was not applied.

## Phase 6 summary
* Added manual v1.7 migration `db/migrations/v1.7_vendors_work_orders.sql` for vendors and work orders, with a nullable document-to-work-order relation. No migration was applied.
* Added vendor categories and active status, validated vendor forms, and a work-order lifecycle with property/request/vendor links, due/priority/cost fields, search filters and overdue sorting.
* Work-order actual costs are informational. A completed order can link to one existing matching Expense record; NOI continues to use the canonical expense and existing maintenance de-duplication query.
* Added a guarded MySQL integration test that requires v1.4/v1.6/v1.7 on `estateflow_test`; extended test cleanup to include optional new tables only when present.
* Verified with `mvn test` on Java 25.0.4: 244 passed, 0 failed, 0 skipped. Integration and display-based UI checks remain unverified.

## Phase 7 summary
* Added an on-demand Notifications screen combining current unpaid-rent reminders, leases within the configured warning period, and overdue work orders when the v1.7 schema is available. Each record has a stable key and duplicate rows are collapsed.
* Notifications are computed from existing records each time the screen opens or refreshes. They are labeled **Generated**; the application does not persist delivery state or send email, SMS, push, or desktop alerts. No inspection reminders are fabricated because inspections have no scheduled date.
* Added local per-user display preferences for rent, lease, and overdue work-order categories. Existing preference files fall back safely to all enabled.
* Added a lease-warning query that honors the full configured 0–3650 day range while preserving the existing 1–365-day selector for the Expiring Leases screen.
* Verified with `mvn test` on Java 25.0.4: 253 tests passed, 0 failures, 0 skipped. MySQL integration and real-display verification remain unverified; no migration was required or applied.

## Phase 8 summary
* Added Advanced Analytics with charge-matched paid-to-date collection rate, current arrears aging, active-property occupancy, configured lease-expiry exposure, maintenance summary, and completed-work-order vendor performance.
* Added six-month cash-flow estimates based on the selected period's average actual monthly rent receipts and operating costs. The interface labels them as estimates and documents the flat run-rate assumptions; no financial values are fabricated from outside records.
* Added CSV and PDF exports and guarded MySQL integration coverage for the rent aggregates and vendor performance. No migration was added or applied.
* Verified with `mvn test` on Java 25.0.4: 258 tests passed, 0 failures, 0 skipped. Database integration and real-display UI verification remain outstanding.

## Phase 9 summary
* Added a local, deterministic Property Assistant for rent/arrears, lease expiry, occupancy, maintenance/work orders, and cash-flow summaries. It calls existing authorized services and reports the data source and generation time.
* Questions are length-bounded and not stored. No external AI provider, network connection, API key, or sensitive-data transfer is used. Financial answers reuse report calculations; the forecast remains explicitly labeled as a run-rate estimate.
* Verified with `mvn test` on Java 25.0.4: 262 tests passed, 0 failures, 0 skipped. UI display verification remains outstanding.

## Phase 10 summary
* Added a Windows x64 `jpackage` app-image script that bundles the Java runtime and Maven runtime dependencies without database credentials or local data. The generated app image and `EstateFlow_Windows_1.0.0_Release.zip` were created; the archive contains the launcher, runtime, runtime dependencies, safe configuration example, font license, and setup/recovery guide. No database password, dump, or user documents are included.
* Added user-level database configuration lookup, a separate document-root override for recovery drills, and rolling logs under `%USERPROFILE%\\.estateflow\\logs`.
* Added Windows setup, release, database/document backup, restore-drill, and crash-diagnostic instructions. No database was changed and no migrations are automated.
* `mvn clean verify` could not finish because Windows reported a generated icon file in `target/` locked during Maven clean. Running `mvn verify` without clean succeeded: 266 tests passed, 0 failures, 0 skipped, and the JAR/JaCoCo report were built. MySQL integration and a real-display release smoke test remain pending.

## Phase 11 summary
* Extended the existing Advanced Analytics screen with due-period collection reconciliation, period receipts, earlier-charge allocations, monthly trends, property and permission-gated tenant breakdowns, overdue follow-up, and late settlement history.
* Collection queries use live charges and valid payments; split payments aggregate once per charge, voids/cancellations are excluded, and outstanding values are floored at zero. Definitions and boundaries are documented in `docs/PHASE_11_RENT_COLLECTION_INSIGHTS.md`.
* Added `VIEW_TENANT_INSIGHTS` for Administrator/Manager reports. Viewers receive aggregate/property output only; tenant queries are skipped and follow-up names are redacted. CSV/PDF exports share service authorization.
* Added unit tests and a guarded MySQL integration suite. Full `mvn verify` passed 274 unit tests, 0 failures and 0 skipped. Integration is pending because no explicit `db-test.properties` is present.

## Phase 12 summary
* Added a disabled capability notice in Settings. No face provider, camera capture, matching, liveness, enrollment, consent store, biometric data, or login bypass is present.
* Password login and active-account/role checks remain unchanged. Implementation requirements are listed in `docs/PHASE_12_FACIAL_VERIFICATION.md`.
* Unit test confirms verification stays unavailable without a provider, enrollment, and consent. No physical-camera test applies to this build.

## Phase 13 summary
* Release and backup instructions include explicit disposable restore targets, validation, write coordination, document-store handling, privileges, and biometric-data status. Added 1.2.0 release notes and a Rent Collection Insights database-fix note.
* CSV export neutralizes formula-leading text while preserving negative numeric cells.
* Maven project/package version is 1.2.0. The 1.2.0 verification results and the reproduced late-settlement parameter-binding failure are recorded in the task final report and `docs/DATABASE_FIX_RENT_COLLECTION_INSIGHTS.md`.
* Windows x64 app image was generated from the updated project, not the previous release. MySQL integration, real-display UI, disposable-schema launch, operational restore drill, and physical-camera checks were not performed. The release ZIP was verified and SHA-256 hashed; the final report records the result.
* Added a repeatable release assembly and verification workflow. The verified archive has 343 entries and excludes local configuration, SQL dumps, documents, IDE files, logs, and build output.
