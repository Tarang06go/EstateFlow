# ACR-001 — Audit trail (v1.1)

**Problem.** v1.0 keeps payment voids and cancelled charges as history but records no general trail of who/what changed
important records (agreement creation/termination, payments, activation changes, maintenance status).

**Why v1.0 cannot handle it.** The seven core tables hold current state only (plus void/cancel markers). A trail needs its own
append-only store.

**Change.** One new table, `audit_log`, exactly as pre-approved in Architecture §11 (columns `audit_id`, `user_id` NULL,
`entity_type`, `entity_id`, `action`, `old_value` JSON, `new_value` JSON, `created_at`). `user_id` has no foreign key because
there is no `app_user` table yet (authentication remains a later phase).

**Isolation and compatibility.**
* Delivered as `db/migrations/v1.1_audit_log.sql` (`CREATE TABLE IF NOT EXISTS`, idempotent). The seven v1.0 tables, `schema.sql`
  and BR-01–BR-32 are untouched.
* The application works without the migration: `AuditService` checks once whether the table exists and otherwise records nothing;
  the Audit History screen explains how to enable it.
* Entries are written inside the same transaction as the operation (agreement create/terminate, payment record/void,
  owner/tenant/property activation, maintenance status change), so an operation and its audit entry commit or roll back together.
* Payment void history is unchanged (`voided_at`, `void_reason` remain the source of truth).

**Not audited in v1.1:** owner/tenant/property create and edit, maintenance create/edit, agreement notes. Adding them is a small
additive change (one `audit.record(...)` call each); they were left out to keep this release focused.

**Effort and risk.** Low: one additive table, a read-only screen, a write path guarded by the availability check.
**Rollback.** `DROP TABLE audit_log;` — the application keeps working.

**Alternatives considered.** Database triggers (hidden logic, harder to test, MySQL-specific); logging to a file (not queryable,
not transactional); storing history columns on core tables (would modify the locked schema).
