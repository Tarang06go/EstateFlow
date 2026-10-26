-- EstateFlow v1.8: opt-in, per-Windows-user/device encrypted face templates.
-- Requires v1.2 app_user. Additive only. Never applied automatically by the application.
-- BACK UP FIRST: mysqldump -u <user> -p estateflow > estateflow_backup_before_v1.8.sql
-- APPLY: mysql -u <user> -p estateflow < db/migrations/v1.8_face_enrollment.sql
-- ROLLBACK: DROP TABLE user_face_enrollment; this permanently removes all enrollments and consent metadata.
CREATE TABLE IF NOT EXISTS user_face_enrollment (
  user_id BIGINT NOT NULL,
  device_id CHAR(36) NOT NULL,
  template_ciphertext VARBINARY(8192) NOT NULL,
  nonce BINARY(12) NOT NULL,
  key_version SMALLINT UNSIGNED NOT NULL,
  template_version SMALLINT UNSIGNED NOT NULL,
  model_version VARCHAR(40) NOT NULL,
  consent_version VARCHAR(32) NOT NULL,
  consented_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  enabled TINYINT(1) NOT NULL DEFAULT 0,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (user_id, device_id),
  CONSTRAINT fk_face_enrollment_user FOREIGN KEY (user_id) REFERENCES app_user(user_id) ON DELETE CASCADE,
  CONSTRAINT chk_face_enrollment_versions CHECK (key_version > 0 AND template_version > 0),
  CONSTRAINT chk_face_enrollment_enabled CHECK (enabled IN (0, 1))
) ENGINE=InnoDB;
