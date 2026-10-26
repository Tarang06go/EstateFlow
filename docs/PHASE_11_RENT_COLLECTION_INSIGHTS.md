# Phase 11 — Advanced rent collection insights

This extends the existing Advanced Analytics screen and its report export path. It reuses the rent-charge/payment ledger and authorizer; it adds no table, migration, stored calculation, or notification state.

## Definitions and reconciliation

- The selected range is inclusive. `Charges due` includes each live `rent_charge` with `due_date` in range, once. There is no rent-charge creation timestamp in the current schema, so this is due-date reporting rather than a claim about when the charge row was created.
- `Collected on selected charges` sums valid (`voided_at IS NULL`) payments applied to those selected charges through the selected end date. This includes earlier payments that reduced the balance. The report caps each charge's applied sum at its charge amount, floors its outstanding at zero, and computes percentage as collected/billed; a zero billed total produces zero percent.
- `Payments received in period` is based on payment date, and includes valid payments on live charges. `Applied to earlier charges` is the portion of those receipts allocated to charges whose due date predates the range. These cash totals are independent of the selected due-period charge total and are intentionally shown separately.
- Trends use charge due month for billed/collected/outstanding and payment month for receipts. First and last calendar months can be partial periods.
- Current arrears are as of the report date and include only past-due, live charges with a positive balance. The inclusive age bands are 1–30, 31–60, 61–90, and over 90 days. A correlated payment sum yields one row per charge, so split payments do not multiply charge counts.
- Follow-up is the current list of overdue balances, ordered oldest first. Use table sorting and the property selector to narrow it.
- Late-settlement history looks at selected-period due dates, capped to the latest 12 months. A charge is in the history only after valid payments fully settle it; the settlement date is the first payment date on which cumulative valid payments reach the charge amount. “Repeated late” means at least three such charges settled after due date among at least three fully settled charges. Smaller samples are shown as insufficient history. This is descriptive and not a credit score or prediction.

## Privacy and export

Administrators and Managers have `VIEW_TENANT_INSIGHTS`. Viewers receive aggregate collection and property results, but the service does not query tenant breakdowns or late-payment behavior for them, and it removes names from follow-up data. CSV and PDF export go through the same authorized service. Existing report access is still required. CSV text is sanitized against spreadsheet formula interpretation.

## Verification

Unit tests cover role gating, date/property validation, empty report exports, and non-disclosure. A MySQL integration suite covers partial and voided payments, date boundaries, earlier-period allocation, property/tenant reconciliation, canceled charges, zero-charge data, and exact arrears boundaries. The final report says whether that guarded suite ran; it may only be run with an explicitly configured `_test` database and required base schema.
