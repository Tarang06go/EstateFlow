-- EstateFlow v1.2 migration (Phase 1): authentication and role-based access control.
-- Adds ONE table, app_user. Existing tables and data (owner, tenant, property, rental_agreement,
-- rent_charge, payment, maintenance_request, audit_log) are NOT touched. Safe to run more than once.
--
-- BACK UP FIRST:  mysqldump -u <user> -p estateflow > estateflow_backup_before_v1.2.sql
-- APPLY:          mysql -u <user> -p estateflow < db/migrations/v1.2_app_user.sql
-- ROLLBACK:       DROP TABLE app_user;   (the application will then refuse to start until the table exists again)
--
-- No account is created here and no password is stored in this file. On first start with no active
-- administrator, EstateFlow shows a setup screen where YOU choose the first administrator's credentials.
CREATE TABLE IF NOT EXISTS app_user (
  user_id       BIGINT       NOT NULL AUTO_INCREMENT,
  username      VARCHAR(50)  NOT NULL,
  password_hash VARCHAR(255) NOT NULL COMMENT 'PBKDF2-HMAC-SHA256: pbkdf2_sha256$iterations$salt$hash; never plaintext',
  role          VARCHAR(10)  NOT NULL,
  is_active     TINYINT(1)   NOT NULL DEFAULT 1,
  created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  last_login_at TIMESTAMP    NULL,
  PRIMARY KEY (user_id),
  UNIQUE KEY uq_app_user_username (username),
  CONSTRAINT chk_app_user_role CHECK (role IN ('ADMIN', 'MANAGER', 'VIEWER'))
) ENGINE=InnoDB;
