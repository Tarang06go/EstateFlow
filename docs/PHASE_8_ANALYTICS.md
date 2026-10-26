# Phase 8 — Advanced analytics and financial reporting

## Screen and exports

Open **Advanced Analytics** from Insights. Select a date range, refresh, and export the report as CSV or PDF. The screen combines figures from the existing profitability, reporting, maintenance, lease, and work-order services. The report query uses the shared transaction manager so the database reads share one snapshot.

## Metric definitions

* **Rent collection rate:** valid payments credited to live rent charges whose due dates are inside the selected period, divided by the total amount billed on those same charges. Payment dates can fall outside the period. Cancelled charges and voided payments are excluded. Outstanding is the current unpaid balance on those charges. This is a charge-matched measure, not same-month cash receipts divided by same-month billing.
* **Arrears aging:** current balances on live charges past due, grouped into 1–30, 31–60, 61–90, and over-90-day bands. Only valid payments reduce the balance.
* **Occupancy:** occupied active properties divided by all active properties as of today. Inactive properties are excluded from the denominator.
* **Lease exposure:** agreements ending within the configured lease warning window, grouped by effective end month; monthly rent is the current contractual amount at risk, not a predicted loss.
* **Maintenance:** uses the existing maintenance summary, which reports request status and recorded costs. Vendor performance uses completed work orders whose completion timestamps fall in the selected period. On-time rate uses only orders with a due date; average completion days is based on recorded creation and completion dates. Work-order actual costs are operational aggregates and do not create a second NOI expense.
* **Actual cash flow:** reuses the existing profitability report. Rent receipts use payment date; operating expenses use expense date plus the existing unlinked resolved-maintenance rule. Rent charged is shown separately by due date. NOI remains rent received minus operating expenses and is not total profit.

## Forecast assumptions

The six future monthly estimates use the average monthly cash rent received and operating expenses across the selected actual period, then hold both averages flat. The forecast starts after the current calendar month. It assumes no rent growth, lease changes, vacancies, known work orders, or unusual future costs, so it is a simple run-rate illustration rather than a prediction or valuation. No values are invented from external data.

## Database and verification

Phase 8 adds no migration. Vendor performance is omitted when v1.7 is unavailable. Guarded integration coverage in `ExpenseProfitabilityIntegrationTest` checks charge-matched collection and arrears SQL against the test database; `VendorWorkOrderIntegrationTest` covers completion-period vendor aggregates after v1.7. Those integration tests were not run because no test database target was confirmed and migrations were not applied.

`AdvancedAnalyticsServiceTest` covers authorization, date-range validation, occupancy, arrears zero-filling, lease exposure, forecast calculation, and CSV/PDF output. The full `mvn test` run on Java 25.0.4 passed 258 tests, with 0 failures and 0 skipped. FXML parsed as XML; real-display UI testing remains outstanding.
