-- EstateFlow v1.4 migration (Phase 3): property expenses.
-- Adds ONE new table, `expense`. Existing tables and data (owner, tenant, property, rental_agreement, rent_charge,
-- payment, maintenance_request, audit_log, app_user) are NOT touched. Safe to run more than once.
-- Nothing runs automatically: you apply it yourself.
--
-- REQUIRES: the base schema (property, maintenance_request). v1.1-v1.3 should already be applied (audit/users).
-- BACK UP FIRST:  mysqldump -u <user> -p estateflow > estateflow_backup_before_v1.4.sql
-- APPLY:          mysql -u <user> -p estateflow < db/migrations/v1.4_expense.sql
-- ROLLBACK:       DROP TABLE expense;
--                 WARNING: this permanently removes every recorded expense. Export or back up first
--                 (mysqldump -u <user> -p estateflow expense > expense_backup.sql). The application then refuses to open
--                 the Expenses and Profitability screens until the table exists again; all other data is unaffected.
--
-- Design notes
--  * Expenses are never physically deleted by the application: deleted_at / delete_reason make a soft delete that stays
--    in the table (and in the audit log).
--  * maintenance_request_id optionally links an expense to the maintenance request it paid for. A generated column plus a
--    UNIQUE key allows at most ONE ACTIVE expense per maintenance request, so the same repair cannot be recorded twice.
--    Profitability counts a resolved request's own cost only when no active expense is linked to it (no double counting).
--  * All categories are operating expenses. Mortgage/loan repayments, interest and capital works are not operating
--    expenses and must not be recorded here (NOI excludes them by definition).
CREATE TABLE IF NOT EXISTS expense (
  expense_id             BIGINT        NOT NULL AUTO_INCREMENT,
  property_id            BIGINT        NOT NULL,
  maintenance_request_id BIGINT        NULL,
  category               VARCHAR(20)   NOT NULL,
  amount                 DECIMAL(12,2) NOT NULL,
  expense_date           DATE          NOT NULL,
  description            VARCHAR(255)  NOT NULL,
  payment_method         VARCHAR(20)   NOT NULL,
  reference_no           VARCHAR(60)   NULL,
  created_at             TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at             TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted_at             TIMESTAMP     NULL,
  delete_reason          VARCHAR(255)  NULL,
  active_maintenance_key BIGINT GENERATED ALWAYS AS (IF(deleted_at IS NULL, maintenance_request_id, NULL)) VIRTUAL,
  PRIMARY KEY (expense_id),
  CONSTRAINT fk_expense_property FOREIGN KEY (property_id) REFERENCES property (property_id) ON DELETE RESTRICT,
  CONSTRAINT fk_expense_maintenance FOREIGN KEY (maintenance_request_id) REFERENCES maintenance_request (request_id) ON DELETE RESTRICT,
  CONSTRAINT ck_expense_category CHECK (category IN
    ('MAINTENANCE','UTILITIES','INSURANCE','PROPERTY_TAX','REPAIRS','MANAGEMENT_FEES','OTHER')),
  CONSTRAINT ck_expense_amount CHECK (amount > 0),
  CONSTRAINT ck_expense_method CHECK (payment_method IN ('CASH','UPI','BANK_TRANSFER','CHEQUE','CARD')),
  CONSTRAINT ck_expense_delete CHECK (
    (deleted_at IS NULL AND delete_reason IS NULL) OR (deleted_at IS NOT NULL AND delete_reason IS NOT NULL)),
  CONSTRAINT uq_expense_active_maintenance UNIQUE (active_maintenance_key),
  INDEX idx_expense_property_date (property_id, expense_date),
  INDEX idx_expense_date (expense_date),
  INDEX idx_expense_category (category)
) ENGINE=InnoDB;
