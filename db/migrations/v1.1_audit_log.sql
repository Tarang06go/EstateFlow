-- EstateFlow v1.1 migration: audit trail (pre-approved additive table, architecture Section 11 / ACR-001).
-- Adds ONE table. The seven v1.0 tables are not touched. Safe to run more than once.
-- BACK UP FIRST:   mysqldump -u <user> -p estateflow > estateflow_backup_before_v1.1.sql
-- APPLY:           mysql -u <user> -p estateflow < db/migrations/v1.1_audit_log.sql
-- ROLLBACK:        DROP TABLE audit_log;   (the application then simply stops recording audit entries)
-- The application works without this migration: auditing is skipped and the Audit History screen explains why.
CREATE TABLE IF NOT EXISTS audit_log (
  audit_id    BIGINT       NOT NULL AUTO_INCREMENT,
  user_id     BIGINT       NULL COMMENT 'reserved for the future app_user table; NULL while the app has no login',
  entity_type VARCHAR(30)  NOT NULL,
  entity_id   BIGINT       NOT NULL,
  action      VARCHAR(30)  NOT NULL,
  old_value   JSON         NULL,
  new_value   JSON         NULL,
  created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (audit_id),
  INDEX idx_audit_entity (entity_type, entity_id),
  INDEX idx_audit_created (created_at)
) ENGINE=InnoDB;
