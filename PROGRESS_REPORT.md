# PROGRESS REPORT - EstateFlow v1.2 redesign (work in progress, NOT finished)

Snapshot of the project exactly as it stood when you asked me to stop. Baseline for "changed" = the v1.1 project (after the v1.1 patch).

## Verification status (honest)
| Check | Result |
|:---|:---|
| Compile (`javac --release 25`, all main + test sources) | Succeeded after the last Java change (only CSS was edited afterwards). |
| Unit tests | 122 passed, 0 failed, but run BEFORE the UI/shell/dashboard/settings/detail-dialog work. **Not re-run since.** |
| MySQL integration tests | Last run: 44 passed (v1.1). **NOT re-run after the v1.2 backend changes** (audit now also written on owner/tenant/property/maintenance create+update, new service constructors, new SQL queries, rewritten seed). Some of these queries have no integration test yet. |
| FXML/controller cross-check script | 28 FXML files, 0 problems (ran before the last CSS edits). |
| Maven build | **Never run** (no Maven Central access in the build environment). |
| App launched / screens rendered | Rendered with real data via a sandbox harness (JavaFX 11 on JDK 21 under Xvfb): **only the Dashboard has been viewed**. All other screens, dialogs, Settings, profiles and the new Reports page are compiled but **visually unreviewed**. JavaFX 25 was not used. |
| New seed (`db/seed.sql`) | Loaded successfully into a scratch database (84 charges, 43 payments, 8 agreements, 3 cancelled charges). Not tested against MySQL 8.4. |

## What has actually been changed
* Design system: new `estateflow.css` (green/lime palette, buttons, inputs, cards, badges, tables, tabs, charts, toasts, dialogs); bundled Inter font (Regular, Bold + OFL licence); vector icon set (`Icons`), logo mark (`LogoMark`) and app icon PNGs; PDF logo and green PDF branding.
* Shell: new `main.fxml` + `MainController` (grouped icon navigation, active-item highlight, global search, busy bar, toasts, version footer); `SceneNavigator` (screen listener, toast), `UiTask` (busy counter), `AlertUtil` (themed dialogs), `TableUtil` (auto-aligned money columns, status badges), `FileDialogs`.
* Dashboard rebuilt: KPI cards, portfolio strip, billed-vs-collected chart, occupancy chart, action lists (overdue, ending soon, vacant, open maintenance), recent activity, maintenance cost chart, quick actions, 3/6/12-month period. Backed by `DashboardService.overview` and new `ReportingDao` queries.
* New screens/dialogs: Settings (persisted display preferences: currency symbol, date format, dashboard period), Owner profile, Tenant profile (ID numbers masked), expanded Reports page (agreements, expiring leases added).
* Backend: `UserPreferences`, `UiFormat`, `ActivityText`, `Masking`, owner/tenant `details`, audit entries for owner/tenant/property/maintenance create+update and agreement notes (names only, no contact data), `ReportService` agreement/expiring exports, richer demo seed.
* Other screens: only mechanically restyled (new CSS classes, page headers/subtitles, preference-aware money/date formatting, toast after saves). Not individually polished.
* Unit tests added: `V12ServicesTest` (7 groups). Existing tests updated for new constructors.

## Not done / known gaps
* No `ActivityText`/`Masking`/`TableUtil` unit tests yet; no integration tests for the v1.2 queries (expected rent, vacant list, owner/tenant profile queries, open-maintenance ordering) or for audit-on-create/update.
* Screens other than the Dashboard not reviewed visually; dialogs/forms not restyled beyond CSS; no inline form validation (validation still shown in a dialog); no pagination; no CSV import; no breadcrumbs.
* `docs/` not updated: no audit document, changelog, setup guide or testing report for v1.2; README/PROGRESS/REQUIREMENTS_MATRIX still describe v1.1.
* Sidebar uses the fallback colour #91DC69 (the reference image never arrived). Change `-ef-sidebar` at the top of `estateflow.css`.
* The sandbox render harness (`/tmp/shots`) is not part of the project.
* Sandbox database credentials were excluded from the ZIP (`db-test.properties` removed).

## Files created or modified since the v1.1 baseline
### Created (26)
* `src/main/java/com/estateflow/config/UserPreferences.java`
* `src/main/java/com/estateflow/controller/OwnerDetailController.java`
* `src/main/java/com/estateflow/controller/SettingsController.java`
* `src/main/java/com/estateflow/controller/TenantDetailController.java`
* `src/main/java/com/estateflow/dto/DashboardOverview.java`
* `src/main/java/com/estateflow/dto/OwnerDetails.java`
* `src/main/java/com/estateflow/dto/TenantDetails.java`
* `src/main/java/com/estateflow/report/AgreementsReport.java`
* `src/main/java/com/estateflow/report/ExpiringLeasesReport.java`
* `src/main/java/com/estateflow/report/pdf/PdfLogo.java`
* `src/main/java/com/estateflow/util/ActivityText.java`
* `src/main/java/com/estateflow/util/Icons.java`
* `src/main/java/com/estateflow/util/LogoMark.java`
* `src/main/java/com/estateflow/util/Masking.java`
* `src/main/java/com/estateflow/util/UiFormat.java`
* `src/main/resources/fonts/Inter-Bold.otf`
* `src/main/resources/fonts/Inter-OFL.txt`
* `src/main/resources/fonts/Inter-Regular.otf`
* `src/main/resources/fxml/owner-detail.fxml`
* `src/main/resources/fxml/settings.fxml`
* `src/main/resources/fxml/tenant-detail.fxml`
* `src/main/resources/images/app-icon-16.png`
* `src/main/resources/images/app-icon-256.png`
* `src/main/resources/images/app-icon-32.png`
* `src/main/resources/images/app-icon-64.png`
* `src/test/java/com/estateflow/service/V12ServicesTest.java`

### Modified (73)
* `db/seed.sql`
* `src/main/java/com/estateflow/EstateFlowApp.java`
* `src/main/java/com/estateflow/config/AppContext.java`
* `src/main/java/com/estateflow/controller/AbstractController.java`
* `src/main/java/com/estateflow/controller/AgreementDetailController.java`
* `src/main/java/com/estateflow/controller/AgreementFormController.java`
* `src/main/java/com/estateflow/controller/AgreementListController.java`
* `src/main/java/com/estateflow/controller/DashboardController.java`
* `src/main/java/com/estateflow/controller/ExpiringLeasesController.java`
* `src/main/java/com/estateflow/controller/MainController.java`
* `src/main/java/com/estateflow/controller/MaintenanceAnalyticsController.java`
* `src/main/java/com/estateflow/controller/MaintenanceFormController.java`
* `src/main/java/com/estateflow/controller/MaintenanceListController.java`
* `src/main/java/com/estateflow/controller/OverdueRentController.java`
* `src/main/java/com/estateflow/controller/OwnerFormController.java`
* `src/main/java/com/estateflow/controller/OwnerListController.java`
* `src/main/java/com/estateflow/controller/PaymentFormController.java`
* `src/main/java/com/estateflow/controller/PaymentsController.java`
* `src/main/java/com/estateflow/controller/PropertyDetailController.java`
* `src/main/java/com/estateflow/controller/PropertyFormController.java`
* `src/main/java/com/estateflow/controller/PropertyListController.java`
* `src/main/java/com/estateflow/controller/RentLedgerController.java`
* `src/main/java/com/estateflow/controller/RentRemindersController.java`
* `src/main/java/com/estateflow/controller/ReportsController.java`
* `src/main/java/com/estateflow/controller/SearchController.java`
* `src/main/java/com/estateflow/controller/TenantFormController.java`
* `src/main/java/com/estateflow/controller/TenantListController.java`
* `src/main/java/com/estateflow/controller/VoidPaymentController.java`
* `src/main/java/com/estateflow/dao/AgreementDao.java`
* `src/main/java/com/estateflow/dao/ReportingDao.java`
* `src/main/java/com/estateflow/dao/jdbc/JdbcAgreementDao.java`
* `src/main/java/com/estateflow/dao/jdbc/JdbcReportingDao.java`
* `src/main/java/com/estateflow/report/pdf/PdfReceiptWriter.java`
* `src/main/java/com/estateflow/report/pdf/PdfTableReport.java`
* `src/main/java/com/estateflow/report/pdf/SimplePdf.java`
* `src/main/java/com/estateflow/service/AgreementService.java`
* `src/main/java/com/estateflow/service/DashboardService.java`
* `src/main/java/com/estateflow/service/MaintenanceService.java`
* `src/main/java/com/estateflow/service/OwnerService.java`
* `src/main/java/com/estateflow/service/PropertyService.java`
* `src/main/java/com/estateflow/service/ReportService.java`
* `src/main/java/com/estateflow/service/TenantService.java`
* `src/main/java/com/estateflow/util/AlertUtil.java`
* `src/main/java/com/estateflow/util/SceneNavigator.java`
* `src/main/java/com/estateflow/util/TableUtil.java`
* `src/main/java/com/estateflow/util/UiTask.java`
* `src/main/resources/css/estateflow.css`
* `src/main/resources/fxml/agreement-detail.fxml`
* `src/main/resources/fxml/agreement-form.fxml`
* `src/main/resources/fxml/agreements.fxml`
* `src/main/resources/fxml/audit.fxml`
* `src/main/resources/fxml/dashboard.fxml`
* `src/main/resources/fxml/expiring.fxml`
* `src/main/resources/fxml/ledger.fxml`
* `src/main/resources/fxml/main.fxml`
* `src/main/resources/fxml/maintenance-analytics.fxml`
* `src/main/resources/fxml/maintenance-form.fxml`
* `src/main/resources/fxml/maintenance.fxml`
* `src/main/resources/fxml/overdue.fxml`
* `src/main/resources/fxml/owner-form.fxml`
* `src/main/resources/fxml/owners.fxml`
* `src/main/resources/fxml/payment-form.fxml`
* `src/main/resources/fxml/payments.fxml`
* `src/main/resources/fxml/properties.fxml`
* `src/main/resources/fxml/property-detail.fxml`
* `src/main/resources/fxml/property-form.fxml`
* `src/main/resources/fxml/reminders.fxml`
* `src/main/resources/fxml/reports.fxml`
* `src/main/resources/fxml/search.fxml`
* `src/main/resources/fxml/tenant-form.fxml`
* `src/main/resources/fxml/tenants.fxml`
* `src/main/resources/fxml/void-payment.fxml`
* `src/test/java/com/estateflow/service/PartyServicesTest.java`

## Next steps (suggested order)
1. Re-run unit + integration tests; fix anything broken by the constructor/audit changes.
2. Render and review every other screen and dialog; polish forms.
3. Add the missing unit/integration tests listed above.
4. Update README, PROGRESS, REQUIREMENTS_MATRIX and write the changelog, setup guide and testing report.
5. Package the final complete project.
