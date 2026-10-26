-- EstateFlow v1.3 migration (Phase 2): who did it, for the audit trail.
-- Adds ONE nullable column (audit_log.actor_username) and two indexes. Existing audit rows and all other
-- tables are NOT changed; old rows simply show no user. Safe to run more than once. Nothing runs automatically.
-- audit_log.user_id (already present since v1.1) now holds app_user.user_id of the signed-in user.
--
-- REQUIRES: db/migrations/v1.1_audit_log.sql (audit_log) and v1.2_app_user.sql (app_user) already applied.
-- BACK UP FIRST:  mysqldump -u <user> -p estateflow > estateflow_backup_before_v1.3.sql
-- APPLY:          mysql -u <user> -p estateflow < db/migrations/v1.3_audit_actor.sql
-- ROLLBACK:       ALTER TABLE audit_log DROP INDEX idx_audit_user, DROP INDEX idx_audit_actor, DROP COLUMN actor_username;
--
-- Until applied, the application keeps working and keeps writing audit entries, but without the user.
-- No foreign key on user_id on purpose: audit history must outlive any account change.

SET @has_col := (SELECT COUNT(*) FROM information_schema.columns
                 WHERE table_schema = DATABASE() AND table_name = 'audit_log' AND column_name = 'actor_username');
SET @ddl := IF(@has_col = 0,
  'ALTER TABLE audit_log ADD COLUMN actor_username VARCHAR(50) NULL COMMENT ''username of the signed-in user at the time; NULL for system/setup events and rows from before v1.3'' AFTER user_id',
  'SELECT ''actor_username already present''');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_idx := (SELECT COUNT(*) FROM information_schema.statistics
                 WHERE table_schema = DATABASE() AND table_name = 'audit_log' AND index_name = 'idx_audit_user');
SET @ddl := IF(@has_idx = 0, 'ALTER TABLE audit_log ADD INDEX idx_audit_user (user_id)', 'SELECT ''idx_audit_user already present''');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_idx := (SELECT COUNT(*) FROM information_schema.statistics
                 WHERE table_schema = DATABASE() AND table_name = 'audit_log' AND index_name = 'idx_audit_actor');
SET @ddl := IF(@has_idx = 0, 'ALTER TABLE audit_log ADD INDEX idx_audit_actor (actor_username)', 'SELECT ''idx_audit_actor already present''');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
