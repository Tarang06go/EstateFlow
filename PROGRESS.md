# EstateFlow — Progress

| Phase | Scope | Status |
|:---|:---|:---|
| 0 Foundations | Maven pom with exact pins, schema, config examples, tests | DONE (Maven itself not run here) |
| 1 Domain | Enums, exceptions, models, validators, money util | DONE+TESTED |
| 2 Services | All services with Mockito tests (BR-01…BR-32) | DONE+TESTED |
| 3 JDBC | `DatabaseManager`, `ConnectionHandle`, `JdbcTransactionManager`, `BaseJdbcDao`, 8 DAOs, guarded MySQL tests, race tests | DONE+TESTED against MySQL 8.0.46 with the MariaDB driver (see handover) |
| 4 JavaFX shell + master data | App start, navigator, `UiTask`, `ErrorHandler`, owners/tenants/properties | DONE (UI never launched) |
| 5 Workflows | Agreements, ledger, payments, void, overdue, expiring, maintenance | DONE (UI never launched) |
| 6 Dashboard / exports / receipts | Dashboard, 4 CSV reports, text receipts | DONE (UI never launched; export logic tested) |
| 7 Hardening / release | Screenshots, ER diagram, coverage report, Maven run, tag v1.0 | NOT DONE |

Test runs recorded in `docs/AI_HANDOVER.md`. Next concrete action: run `mvn verify` and `mvn verify -Pintegration` on a machine with Maven Central, then launch the app with `mvn javafx:run` and walk through the manual checklist in the handover.

## v1.1 (patch)

Features A–G are implemented. Backend, SQL and PDF output were executed against MySQL 8.0.46 (see docs/AI_HANDOVER.md and the
patch VERIFICATION_REPORT.md for exact results). The new JavaFX screens (dashboard charts, property tabs, search, payments,
rent reminders, maintenance analytics, audit history) compile and pass a static FXML/controller cross-check but have **not been
launched** in the build environment. Maven was not run there. Next action: apply the patch, run `mvn verify` and
`mvn verify -Pintegration`, apply `db/migrations/v1.1_audit_log.sql`, then walk through the manual checklist in VERIFICATION_REPORT.md.
