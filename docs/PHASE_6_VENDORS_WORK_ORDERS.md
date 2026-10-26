# Phase 6 — Vendors and work orders

## Workflow

Vendors have a service category, contact information and active/inactive state. Work orders belong to a property and can link to an existing maintenance request. Orders capture scope, priority, due date, estimate, assigned vendor, actual cost and completion notes. The workflow validates draft → assigned → in progress → completed, with cancellation from draft or assigned/in-progress. Closed orders stay in history. Filters cover property, vendor, status, priority, due-date range and overdue work.

The maintenance-request module remains the source for reported maintenance issues. Work orders coordinate vendor work against those requests and do not create a second maintenance-request record.

## Expenses and NOI

Work-order actual cost is operational tracking and is not independently added to NOI. To count the final cost, record it once in the existing Expenses screen. Link that expense to the same maintenance request when the work order has one, then link the expense ID to the completed work order. The service checks property/request compatibility and matches the actual amount. The existing expense rules and unique active maintenance-expense constraint remain the authority for profitability; a linked expense replaces a resolved request's recorded cost so NOI does not count the repair twice.

## Manual database migration

Back up first. Apply `db/migrations/v1.7_vendors_work_orders.sql` manually after v1.1-v1.6, including the Phase 5 migration. The migration adds vendor/work-order tables and a work-order link to document metadata. EstateFlow never applies migrations on startup.

Rollback must export vendor, work-order and document metadata first. The migration header gives the exact reverse order. Dropping the work-order table removes vendor assignments and work-order history. Dropping the `property_document.work_order_id` relationship also removes those document links; the files, document rows, and linked Expense rows are retained. Restore the schema backup to recover the work-order data and links.

## Verification status

Unit suite: `mvn test` on Java 25.0.4; 244 tests passed, 0 failures, 0 skipped. A MySQL integration test is included and will only run against a configured schema ending in `_test`; it verifies canonical expense linking and NOI double-count prevention. It was not run here because the v1.7 migration has not been applied to a confirmed test schema. No database was changed. Display-based UI testing remains unverified.
