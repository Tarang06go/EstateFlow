# EstateFlow — Requirements-to-Implementation Matrix

Status legend: **DONE+TESTED** = implemented and covered by tests that were actually executed (see docs/AI_HANDOVER.md for the exact runs);
**DONE (UI unverified)** = implemented and compiled, but the JavaFX screens have not been launched in this environment;
**PARTIAL** / **NOT VERIFIED** as stated.

| Req | Behavior (Architecture §8) | Implementation | Tests | Status |
|:---|:---|:---|:---|:---|
| BR-01 | Unique phones, phone normalization, ID-proof pair + uniqueness | `Validators.normalizePhone`, `OwnerService`, `TenantService`, DB unique keys + `ck_tenant_id_proof_pair`, `DuplicateKeyException` translation | `PartyServicesTest`, `JdbcDaoIntegrationTest`, `ServiceFlowIntegrationTest` | DONE+TESTED |
| BR-02 | Owner deactivation blocked by active properties; reactivation any time | `OwnerService`, `PropertyDao.countActiveByOwner` | `PartyServicesTest`, `ServiceFlowIntegrationTest` | DONE+TESTED |
| BR-03 | Tenant deactivation blocked by open agreement | `TenantService`, `AgreementDao.existsOpenForTenant` | `PartyServicesTest`, `ServiceFlowIntegrationTest` | DONE+TESTED |
| BR-04 | Property deactivation blocked by open agreement; reactivation needs active owner | `PropertyService` | `PartyServicesTest`, `ServiceFlowIntegrationTest` | DONE+TESTED |
| BR-05 | Only active owners/tenants/properties used | `PropertyService`, `AgreementService` | `PartyServicesTest`, `AgreementServiceTest`, `ServiceFlowIntegrationTest` | DONE+TESTED |
| BR-10 | Agreement inputs and validation, past start allowed | `AgreementService.create` | `AgreementServiceTest` | DONE+TESTED |
| BR-11 | `end_date = start + months - 1 day` | `RentScheduleGenerator.endDate` | `AgreementServiceTest` | DONE+TESTED |
| BR-12 | Effective period (Java + generated column agree) | `RentalAgreement`, `rental_agreement.effective_end_date` | `RentalAgreementLogicTest`, `JdbcDaoIntegrationTest` | DONE+TESTED |
| BR-13 | No overlap, checked under property row lock | `AgreementService.create`, `PropertyDao.findByIdForUpdate`, `AgreementDao.findOverlapping` | `AgreementServiceTest` (lock order), `ConcurrencyIntegrationTest` (5× two-thread race) | DONE+TESTED |
| BR-14 | One charge per month, same transaction | `RentScheduleGenerator`, `AgreementService` | `AgreementServiceTest`, `ServiceFlowIntegrationTest` (rollback test) | DONE+TESTED |
| BR-15 | Termination rules, cancellation of later charges | `AgreementService.terminate`, `RentChargeDao.cancelAfter` | `AgreementServiceTest`, `ServiceFlowIntegrationTest` | DONE+TESTED |
| BR-16 | Only notes editable | `AgreementService.updateNotes`, `JdbcAgreementDao.update` | `AgreementServiceTest` | DONE+TESTED |
| BR-17 | Occupancy derived from effective period | `JdbcReportingDao`, `PropertyDao.findVacantOn` | `JdbcDaoIntegrationTest`, `ServiceFlowIntegrationTest` | DONE+TESTED |
| BR-20 | Payable = live and due; no advance payments | `RentCharge.isPayable`, `PaymentService` | `PaymentServiceTest`, `ServiceFlowIntegrationTest` | DONE+TESTED |
| BR-21 | Amount rules, atomic overpayment check under charge lock | `PaymentService`, `JdbcRentChargeDao.findByIdForUpdate` | `PaymentServiceTest`, `ConcurrencyIntegrationTest` (4-thread race) | DONE+TESTED |
| BR-22 | Payment date not in future | `PaymentService` | `PaymentServiceTest` | DONE+TESTED |
| BR-23 | Partial payments | `PaymentService` | `PaymentServiceTest`, `ServiceFlowIntegrationTest` | DONE+TESTED |
| BR-24 | Arrears on expired/terminated agreements payable | `PaymentService` | `PaymentServiceTest` | DONE+TESTED |
| BR-25 | Void with reason, conditional update, no receipt for void | `PaymentService.voidPayment`, `PaymentDao.markVoided`, `ReceiptService` | `PaymentServiceTest`, `JdbcDaoIntegrationTest`, `ServiceFlowIntegrationTest` | DONE+TESTED |
| BR-26 | Today from injected `Clock` | all services; dates bound as SQL parameters (no `CURDATE()` in app SQL) | fixed-clock tests throughout | DONE+TESTED |
| BR-27 | Configurable warning window | `AppConfig`, `AgreementService`, `DashboardService` | `AgreementServiceTest`, `ServiceFlowIntegrationTest` | DONE+TESTED |
| BR-30 | Maintenance only on active property; tenant must have agreement history; reported date not future | `MaintenanceService.open` | `MaintenanceReportingServicesTest`, `ServiceFlowIntegrationTest` | DONE+TESTED |
| BR-31 | Workflow transitions; resolved_date invariant (service + schema) | `MaintenanceService`, `MaintenanceStatus`, `ck_maint_resolution` | `MaintenanceReportingServicesTest`, `JdbcDaoIntegrationTest` | DONE+TESTED |
| BR-32 | Cost only while IN_PROGRESS/RESOLVED | `MaintenanceService` | `MaintenanceReportingServicesTest` | DONE+TESTED |

## Architecture / Definition-of-Done items

| Item | Status | Evidence |
|:---|:---|:---|
| Connection ownership (`ConnectionHandle`, `DatabaseManager.acquire`, `JdbcTransactionManager`) | DONE+TESTED | `TransactionIntegrationTest` (8 tests: borrowed/owned, nested join, checked+unchecked rollback, suppressed rollback failure, commit failure → `DataAccessException`, binding cleared) |
| Race tests for overlap + overpayment | DONE+TESTED | `ConcurrencyIntegrationTest`; mutation check (removing `FOR UPDATE`) made both fail |
| Guarded integration-test schema (`_test`) | DONE+TESTED | `TestDatabase`, `TestDatabaseGuardTest` |
| Exception policy, single logging point | DONE | `log.error` only in `ErrorHandler`; grep-checked |
| Layer rules (no javafx/db/java.sql in services; no commit/rollback/close in DAOs; no DAO in controllers) | DONE | grep-checked |
| All seven tables via plain JDBC DAOs | DONE+TESTED | `JdbcDaoIntegrationTest` |
| JavaFX screens (owners, tenants, properties + detail, agreements + terminate, rent ledger + payment + void + receipt, overdue, expiring, maintenance, reports, dashboard) | DONE (UI unverified) | compiled against JavaFX; FXML ↔ controller ids/handlers cross-checked by script; **never launched** |
| No DB calls on the FX thread | DONE (by construction) | every service call goes through `UiTask`; not runtime-verified |
| CSV reports (4) and text receipts | DONE+TESTED (service level) | `MaintenanceReportingServicesTest`, `ServiceFlowIntegrationTest` |
| Maven build / `mvn verify` | **NOT RUN** | no Maven Central access in the build environment; verified with `javac` + JUnit console launcher instead |
| JaCoCo ≥ 70% on service, rules, model, report | **NOT MEASURED** | JaCoCo configured in pom; report not generated here |
| Screenshots, ER diagram in `docs/` | **NOT DONE** | needs a running app / diagram tool |
| Property detail screen | PARTIAL | read-only detail + agreement history (no maintenance history) |

## v1.1 additions

| Feature | Implementation | Tests | Status |
|:---|:---|:---|:---|
| A. Property details and history (owner, current tenant/agreement, agreements, payments, maintenance, outstanding rent, maintenance cost) | `PropertyService.details`, `PropertyDetailController`, `ReportingDao.findPaymentRowsForProperty` / `findMaintenanceRowsForProperty` / `outstandingForProperty` | `V11ServicesTest`, `V11IntegrationTest.propertyDetails_realRecords` | DONE+TESTED (service/SQL); dialog UI unverified |
| B. Dashboard charts (rent collected, occupancy, maintenance cost; zero-filled, empty states) | `DashboardService.charts`, `MonthlyAmount.fill`, `DashboardController` | `V11ServicesTest`, `V11IntegrationTest.chartsAndAnalytics_useRealRecords`, `emptyDatabase_isHandled` | DONE+TESTED (data); chart rendering unverified |
| C. Search and filters (global search, agreement status filter, payments screen filters) | `SearchService`, `AgreementDao.searchRows`, `ReportingDao.searchPaymentRows` / `searchPropertyRows`, capped `search(text, limit)` DAO methods | `V11ServicesTest`, `V11IntegrationTest` | DONE+TESTED (queries); screens unverified |
| D. PDF receipts and reports | dependency-free `SimplePdf`, `PdfReceiptWriter`, `PdfTableReport`; `ReportFormat`; CSV and text receipts unchanged | `PdfReportsTest`, `V11IntegrationTest.pdfAndCsvExports`; files also checked with qpdf and poppler | DONE+TESTED |
| E. Rent reminders (overdue / due today / upcoming, one row per unpaid live charge) | `RentReminderService`, `ReportingDao.findRentReminders`, `RentRemindersController`, config `rent.reminder.days` | `V11ServicesTest`, `V11IntegrationTest.rentReminders_noDuplicatesNoStaleEntries` | DONE+TESTED (data); screen unverified |
| F. Maintenance analytics (status, priority, costs, average resolution days, cost per property/month) | `MaintenanceService.summary`, `ReportingDao.maintenanceSummary`, `MaintenanceAnalyticsController` | `V11ServicesTest`, `V11IntegrationTest` | DONE+TESTED (data); screen unverified |
| G. Audit history (isolated migration, transactional writes) | `db/migrations/v1.1_audit_log.sql`, `AuditDao`, `JdbcAuditDao`, `AuditService`, `AuditController`, `docs/change-requests/ACR-001-audit-log.md` | `AuditServiceTest`, `V11IntegrationTest.audit_recordsKeyOperations`, `auditFailure_rollsBackOperation` | DONE+TESTED (backend); screen unverified; partial coverage of operations (see ACR-001) |
