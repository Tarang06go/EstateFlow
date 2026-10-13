# CHANGELOG — EstateFlow v1.1 (patch over the delivered v1.0)

Base: the project exactly as delivered in `EstateFlow_Updated.zip` (v1.0). Feature letters refer to the v1.1 request: A property details, B dashboard, C search/filters, D PDF, E reminders, F maintenance analytics, G audit.

## Summary

* 38 files added, 48 files modified, 0 files deleted or renamed.
* `pom.xml` is **unchanged** (no new dependency: PDFs use a small built-in writer).
* `db/schema.sql` is **unchanged**; the only database change is the optional `db/migrations/v1.1_audit_log.sql`.
* Business rules BR-01 to BR-32 are unchanged; their tests still pass.

## Added files (38)

* `db/migrations/v1.1_audit_log.sql` — [G] Isolated, idempotent v1.1 migration: adds the audit_log table only
* `docs/change-requests/ACR-001-audit-log.md` — [G] ACR-001: records why and how the audit table is added
* `src/main/java/com/estateflow/controller/AuditController.java` — [G] Audit History screen (read-only)
* `src/main/java/com/estateflow/controller/MaintenanceAnalyticsController.java` — [F] Maintenance analytics screen and DTOs
* `src/main/java/com/estateflow/controller/PaymentsController.java` — [C] Searchable payments screen (date, status, text) with receipt output
* `src/main/java/com/estateflow/controller/RentRemindersController.java` — [E] In-app rent reminders (overdue, due today, upcoming)
* `src/main/java/com/estateflow/controller/SearchController.java` — [C] Global search (capped per type)
* `src/main/java/com/estateflow/dao/AuditDao.java` — [G] Audit trail: DAO contract, JDBC implementation, service, DTO
* `src/main/java/com/estateflow/dao/jdbc/JdbcAuditDao.java` — [G] Audit trail: DAO contract, JDBC implementation, service, DTO
* `src/main/java/com/estateflow/dto/AuditEntry.java` — [G] Audit trail: DAO contract, JDBC implementation, service, DTO
* `src/main/java/com/estateflow/dto/DashboardCharts.java` — [B] Dashboard charts (rent per month, occupancy, maintenance cost), zero-filled with empty states
* `src/main/java/com/estateflow/dto/MaintenanceSummary.java` — [F] Maintenance analytics screen and DTOs
* `src/main/java/com/estateflow/dto/MonthlyAmount.java` — [B] Dashboard charts (rent per month, occupancy, maintenance cost), zero-filled with empty states
* `src/main/java/com/estateflow/dto/PropertyCost.java` — [F] Maintenance analytics screen and DTOs
* `src/main/java/com/estateflow/dto/PropertyDetails.java` — [A] Property details with agreement/payment/maintenance history tabs
* `src/main/java/com/estateflow/dto/RentReminderRow.java` — [E] In-app rent reminders (overdue, due today, upcoming)
* `src/main/java/com/estateflow/dto/SearchResults.java` — [C] Global search (capped per type)
* `src/main/java/com/estateflow/model/enums/AgreementFilter.java` — [C] Agreement status filter and search
* `src/main/java/com/estateflow/report/RentRemindersReport.java` — [D/E] CSV/PDF format choice; rent reminders report definition
* `src/main/java/com/estateflow/report/ReportFormat.java` — [D/E] CSV/PDF format choice; rent reminders report definition
* `src/main/java/com/estateflow/report/pdf/PdfReceiptWriter.java` — [D] Dependency-free PDF engine, receipt layout, paginated table report, and their tests
* `src/main/java/com/estateflow/report/pdf/PdfTableReport.java` — [D] Dependency-free PDF engine, receipt layout, paginated table report, and their tests
* `src/main/java/com/estateflow/report/pdf/SimplePdf.java` — [D] Dependency-free PDF engine, receipt layout, paginated table report, and their tests
* `src/main/java/com/estateflow/service/AuditService.java` — [G] Audit trail: DAO contract, JDBC implementation, service, DTO
* `src/main/java/com/estateflow/service/RentReminderService.java` — [E] In-app rent reminders (overdue, due today, upcoming)
* `src/main/java/com/estateflow/service/SearchService.java` — [C] Global search (capped per type)
* `src/main/java/com/estateflow/util/FileDialogs.java` — [D] Shared save dialogs: PDF default, CSV/TXT alternatives
* `src/main/resources/fxml/audit.fxml` — [G] Audit History screen (read-only)
* `src/main/resources/fxml/maintenance-analytics.fxml` — [F] Maintenance analytics screen and DTOs
* `src/main/resources/fxml/payments.fxml` — [C] Searchable payments screen (date, status, text) with receipt output
* `src/main/resources/fxml/reminders.fxml` — [E] In-app rent reminders (overdue, due today, upcoming)
* `src/main/resources/fxml/search.fxml` — [C] Global search (capped per type)
* `src/test/java/com/estateflow/report/PdfReportsTest.java` — [D] Dependency-free PDF engine, receipt layout, paginated table report, and their tests
* `src/test/java/com/estateflow/report/PdfTableReportAccess.java` — [-] supporting change
* `src/test/java/com/estateflow/report/SimplePdfAccess.java` — [-] supporting change
* `src/test/java/com/estateflow/service/AuditServiceTest.java` — [G] Audit trail: DAO contract, JDBC implementation, service, DTO
* `src/test/java/com/estateflow/service/V11IntegrationTest.java` — [tests] Updated for new constructor parameters / new behaviour
* `src/test/java/com/estateflow/service/V11ServicesTest.java` — [tests] Updated for new constructor parameters / new behaviour

## Modified files (complete files, not snippets) (48)

* `PROGRESS.md` — [docs] Status documents updated with the v1.1 state
* `README.md` — [docs] Status documents updated with the v1.1 state
* `REQUIREMENTS_MATRIX.md` — [docs] Status documents updated with the v1.1 state
* `docs/AI_HANDOVER.md` — [docs] Status documents updated with the v1.1 state
* `src/main/java/com/estateflow/EstateFlowApp.java` — [UI] Navigation, global search box, minimum window size, consistent theme
* `src/main/java/com/estateflow/config/AppConfig.java` — [A-G] Wiring of new services; new setting rent.reminder.days
* `src/main/java/com/estateflow/config/AppContext.java` — [A-G] Wiring of new services; new setting rent.reminder.days
* `src/main/java/com/estateflow/controller/AgreementListController.java` — [C] Agreement status filter and search
* `src/main/java/com/estateflow/controller/DashboardController.java` — [B] Dashboard charts (rent per month, occupancy, maintenance cost), zero-filled with empty states
* `src/main/java/com/estateflow/controller/MainController.java` — [UI] Navigation, global search box, minimum window size, consistent theme
* `src/main/java/com/estateflow/controller/MaintenanceListController.java` — [F] Analytics entry point and empty-state text
* `src/main/java/com/estateflow/controller/OverdueRentController.java` — [D] Export/receipt dialogs now offer PDF
* `src/main/java/com/estateflow/controller/PropertyDetailController.java` — [A] Property details with agreement/payment/maintenance history tabs
* `src/main/java/com/estateflow/controller/RentLedgerController.java` — [D] Export/receipt dialogs now offer PDF
* `src/main/java/com/estateflow/controller/ReportsController.java` — [D/E] Format selector and rent-reminder export
* `src/main/java/com/estateflow/dao/AgreementDao.java` — [A/C] Filtered agreement rows, rows per property
* `src/main/java/com/estateflow/dao/OwnerDao.java` — [C] Capped search(text, limit) used by global search
* `src/main/java/com/estateflow/dao/PropertyDao.java` — [C] Capped property search; property list now uses ReportingDao rows
* `src/main/java/com/estateflow/dao/ReportingDao.java` — [A-F] New read-only queries: history, search, charts, reminders, analytics (shared row mappers)
* `src/main/java/com/estateflow/dao/TenantDao.java` — [C] Capped search(text, limit) used by global search
* `src/main/java/com/estateflow/dao/jdbc/JdbcAgreementDao.java` — [A/C] Filtered agreement rows, rows per property
* `src/main/java/com/estateflow/dao/jdbc/JdbcOwnerDao.java` — [C] Capped search(text, limit) used by global search
* `src/main/java/com/estateflow/dao/jdbc/JdbcPropertyDao.java` — [C] Capped property search; property list now uses ReportingDao rows
* `src/main/java/com/estateflow/dao/jdbc/JdbcReportingDao.java` — [A-F] New read-only queries: history, search, charts, reminders, analytics (shared row mappers)
* `src/main/java/com/estateflow/dao/jdbc/JdbcTenantDao.java` — [C] Capped search(text, limit) used by global search
* `src/main/java/com/estateflow/service/AgreementService.java` — [G] Audit entries written inside the operation transaction; capped searches/details/summary added
* `src/main/java/com/estateflow/service/DashboardService.java` — [B] Dashboard charts (rent per month, occupancy, maintenance cost), zero-filled with empty states
* `src/main/java/com/estateflow/service/MaintenanceService.java` — [G] Audit entries written inside the operation transaction; capped searches/details/summary added
* `src/main/java/com/estateflow/service/OwnerService.java` — [G] Audit entries written inside the operation transaction; capped searches/details/summary added
* `src/main/java/com/estateflow/service/PaymentService.java` — [G] Audit entries written inside the operation transaction; capped searches/details/summary added
* `src/main/java/com/estateflow/service/PropertyService.java` — [G] Audit entries written inside the operation transaction; capped searches/details/summary added
* `src/main/java/com/estateflow/service/ReceiptService.java` — [D] Format-aware (CSV/PDF) exports; PDF receipts chosen by .pdf extension (CSV and .txt unchanged)
* `src/main/java/com/estateflow/service/ReportService.java` — [D] Format-aware (CSV/PDF) exports; PDF receipts chosen by .pdf extension (CSV and .txt unchanged)
* `src/main/java/com/estateflow/service/TenantService.java` — [G] Audit entries written inside the operation transaction; capped searches/details/summary added
* `src/main/resources/config/application.properties` — [A-G] Wiring of new services; new setting rent.reminder.days
* `src/main/resources/css/estateflow.css` — [UI] Navigation, global search box, minimum window size, consistent theme
* `src/main/resources/fxml/agreements.fxml` — [C] Agreement status filter and search
* `src/main/resources/fxml/dashboard.fxml` — [B] Dashboard charts (rent per month, occupancy, maintenance cost), zero-filled with empty states
* `src/main/resources/fxml/main.fxml` — [UI] Navigation, global search box, minimum window size, consistent theme
* `src/main/resources/fxml/maintenance.fxml` — [F] Analytics entry point and empty-state text
* `src/main/resources/fxml/property-detail.fxml` — [A] Property details with agreement/payment/maintenance history tabs
* `src/main/resources/fxml/reports.fxml` — [D/E] Format selector and rent-reminder export
* `src/test/java/com/estateflow/service/AgreementServiceTest.java` — [G] Audit entries written inside the operation transaction; capped searches/details/summary added
* `src/test/java/com/estateflow/service/MaintenanceReportingServicesTest.java` — [tests] Updated for new constructor parameters / new behaviour
* `src/test/java/com/estateflow/service/PartyServicesTest.java` — [tests] Updated for new constructor parameters / new behaviour
* `src/test/java/com/estateflow/service/PaymentServiceTest.java` — [G] Audit entries written inside the operation transaction; capped searches/details/summary added
* `src/test/java/com/estateflow/service/ServiceFlowIntegrationTest.java` — [tests] Updated for new constructor parameters / new behaviour
* `src/test/java/com/estateflow/support/TestDatabase.java` — [G] Test harness also truncates audit_log when the table exists (still refuses non-_test schemas)

## Deleted files (0)

None.


## Documentation files shipped only with this patch

* `PATCH_README.md`, `CHANGELOG.md`, `VERIFICATION_REPORT.md` (move into `docs/` if you prefer; they are not used by the build).
