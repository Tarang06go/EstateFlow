-- EstateFlow v1.5 migration (Phase 4): security deposits and lease renewal/extension.
-- ADDS three tables (security_deposit, deposit_transaction, lease_change) and ONE nullable column on rental_agreement
-- (renewed_from_agreement_id, with a foreign key and a unique key). No existing row is changed or removed.
-- Safe to run more than once. Nothing runs automatically: you apply it yourself.
--
-- REQUIRES: the base schema (rental_agreement, property, tenant). v1.1-v1.4 should already be applied.
-- BACK UP FIRST:  mysqldump -u <user> -p estateflow > estateflow_backup_before_v1.5.sql
-- APPLY:          mysql -u <user> -p estateflow < db/migrations/v1.5_deposits_leases.sql
-- ROLLBACK (in this order; it permanently deletes all deposit and lease-change records, so export them first with
--           mysqldump -u <user> -p estateflow security_deposit deposit_transaction lease_change > deposits_backup.sql):
--     DROP TABLE lease_change;
--     DROP TABLE deposit_transaction;
--     DROP TABLE security_deposit;
--     ALTER TABLE rental_agreement DROP FOREIGN KEY fk_agreement_renewed_from,
--                                  DROP INDEX uq_agreement_renewed_from, DROP COLUMN renewed_from_agreement_id;
--   Renewed agreements stay as ordinary agreements; only the link between them is lost.
--
-- Design notes
--  * rental_agreement.security_deposit is unchanged: it is the deposit AGREED in the lease. Money actually held is the
--    ledger below, one security_deposit per agreement (UNIQUE) and any number of deposit_transaction rows.
--  * The ledger is append-only. A mistake is corrected by voiding a transaction (voided_at, void_reason); rows are never
--    updated or deleted by the application. Balance = collections + carried-in - deductions - refunds - carried-out,
--    ignoring voided rows. Deposits are a LIABILITY: they are never read by the rent-income, expense or NOI queries.
--  * A renewal creates a NEW agreement linked to the old one; the old agreement and its history are untouched.
--    lease_change keeps who changed what (old/new dates and rent) for extensions and renewals.

CREATE TABLE IF NOT EXISTS security_deposit (
  deposit_id   BIGINT    NOT NULL AUTO_INCREMENT,
  agreement_id BIGINT    NOT NULL,
  created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (deposit_id),
  CONSTRAINT fk_deposit_agreement FOREIGN KEY (agreement_id) REFERENCES rental_agreement (agreement_id) ON DELETE RESTRICT,
  CONSTRAINT uq_deposit_agreement UNIQUE (agreement_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS deposit_transaction (
  txn_id               BIGINT        NOT NULL AUTO_INCREMENT,
  deposit_id           BIGINT        NOT NULL,
  txn_type             VARCHAR(12)   NOT NULL,
  amount               DECIMAL(12,2) NOT NULL,
  txn_date             DATE          NOT NULL,
  payment_method       VARCHAR(20)   NULL,
  reference_no         VARCHAR(60)   NULL,
  reason               VARCHAR(255)  NULL,
  notes                VARCHAR(500)  NULL,
  related_agreement_id BIGINT        NULL,
  created_at           TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  voided_at            TIMESTAMP     NULL,
  void_reason          VARCHAR(255)  NULL,
  PRIMARY KEY (txn_id),
  CONSTRAINT fk_deptxn_deposit FOREIGN KEY (deposit_id) REFERENCES security_deposit (deposit_id) ON DELETE RESTRICT,
  CONSTRAINT fk_deptxn_related FOREIGN KEY (related_agreement_id) REFERENCES rental_agreement (agreement_id) ON DELETE RESTRICT,
  CONSTRAINT ck_deptxn_type CHECK (txn_type IN ('COLLECTION','DEDUCTION','REFUND','TRANSFER_IN','TRANSFER_OUT')),
  CONSTRAINT ck_deptxn_amount CHECK (amount > 0),
  CONSTRAINT ck_deptxn_method CHECK (payment_method IS NULL OR payment_method IN ('CASH','UPI','BANK_TRANSFER','CHEQUE','CARD')),
  CONSTRAINT ck_deptxn_reason CHECK (txn_type <> 'DEDUCTION' OR reason IS NOT NULL),
  CONSTRAINT ck_deptxn_void CHECK ((voided_at IS NULL AND void_reason IS NULL) OR (voided_at IS NOT NULL AND void_reason IS NOT NULL)),
  INDEX idx_deptxn_deposit (deposit_id),
  INDEX idx_deptxn_date (txn_date)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS lease_change (
  change_id            BIGINT        NOT NULL AUTO_INCREMENT,
  agreement_id         BIGINT        NOT NULL,
  change_type          VARCHAR(10)   NOT NULL,
  related_agreement_id BIGINT        NULL,
  old_start_date       DATE          NOT NULL,
  old_end_date         DATE          NOT NULL,
  old_monthly_rent     DECIMAL(12,2) NOT NULL,
  new_start_date       DATE          NOT NULL,
  new_end_date         DATE          NOT NULL,
  new_monthly_rent     DECIMAL(12,2) NOT NULL,
  actor_user_id        BIGINT        NULL,
  actor_username       VARCHAR(50)   NULL,
  note                 VARCHAR(255)  NULL,
  created_at           TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (change_id),
  CONSTRAINT fk_leasechange_agreement FOREIGN KEY (agreement_id) REFERENCES rental_agreement (agreement_id) ON DELETE RESTRICT,
  CONSTRAINT fk_leasechange_related FOREIGN KEY (related_agreement_id) REFERENCES rental_agreement (agreement_id) ON DELETE RESTRICT,
  CONSTRAINT ck_leasechange_type CHECK (change_type IN ('EXTENDED','RENEWED')),
  INDEX idx_leasechange_agreement (agreement_id),
  INDEX idx_leasechange_related (related_agreement_id)
) ENGINE=InnoDB;

SET @has_col := (SELECT COUNT(*) FROM information_schema.columns
                 WHERE table_schema = DATABASE() AND table_name = 'rental_agreement' AND column_name = 'renewed_from_agreement_id');
SET @ddl := IF(@has_col = 0,
  'ALTER TABLE rental_agreement ADD COLUMN renewed_from_agreement_id BIGINT NULL COMMENT ''agreement this one renewed; NULL for first leases'', ADD CONSTRAINT fk_agreement_renewed_from FOREIGN KEY (renewed_from_agreement_id) REFERENCES rental_agreement (agreement_id) ON DELETE RESTRICT, ADD CONSTRAINT uq_agreement_renewed_from UNIQUE (renewed_from_agreement_id)',
  'SELECT ''renewed_from_agreement_id already present''');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
