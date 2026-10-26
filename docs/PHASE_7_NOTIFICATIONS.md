# Phase 7 — In-app notifications

## What is included

The Notifications screen builds a read-only list on demand from current records:

* Unpaid rent rows from `RentReminderService`, using the configured rent reminder window.
* Agreements ending within the configured lease warning period.
* Overdue work orders, when the v1.7 work-order schema is available.

Rows have stable source keys (`RENT:<chargeId>`, `LEASE:<agreementId>`, and `WORK_ORDER:<id>`); duplicate keys are collapsed. Severity and due date determine the display order. The screen offers navigation to the related rent ledger, agreement, or work-order list.

The notification query accepts the full configured lease warning range (0–3650 days). The Expiring Leases screen keeps its separate 1–365-day period selector.

## Generated versus delivered

The list is recomputed when opened or refreshed. Each row is marked **Generated**. There is no persisted notification queue, delivery status, scheduler, retry mechanism, or sender. EstateFlow does not send email, SMS, push, or desktop alerts. A refresh may therefore show a different list after source data or local preferences change. Inspections are not included because there is no inspection schedule date to use.

## Preferences

ADMIN and MANAGER can enable or disable the three categories in Settings. Defaults are enabled. Preferences are stored locally in `~/.estateflow/preferences.properties` (Windows: `%USERPROFILE%\.estateflow\preferences.properties`) and do not modify the database. Existing preference files without these keys retain the enabled defaults; malformed values also fall back safely.

## Compatibility and verification

There is no Phase 7 database migration. If v1.7 is not installed, the work-order category is omitted while rent and lease reminders continue to work. Unit coverage is in `NotificationServiceTest` and `UserPreferencesTest`.

Verification: `mvn test` on Java 25.0.4 passed 253 tests with 0 failures and 0 skipped. The database integration profile was not run and no database was changed. A visual check on a real JavaFX display remains outstanding.
