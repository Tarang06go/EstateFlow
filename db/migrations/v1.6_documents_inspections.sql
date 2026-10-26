-- EstateFlow v1.6 (Phase 5): document metadata and property inspections.
-- Additive only. Apply manually after backing up the database. Never run at application startup.
-- Requires the base schema and migrations v1.1-v1.5.
--
-- Apply: mysql -u <user> -p estateflow < db/migrations/v1.6_documents_inspections.sql
-- Rollback removes the metadata, inspection records and links, but does not remove stored files.
-- Export these tables first; after DROP TABLE, restore them from that export before using the old app.
--   DROP TABLE inspection_finding;
--   DROP TABLE inspection;
--   DROP TABLE property_document;

CREATE TABLE IF NOT EXISTS property_document (
  document_id BIGINT NOT NULL AUTO_INCREMENT,
  property_id BIGINT NULL,
  agreement_id BIGINT NULL,
  tenant_id BIGINT NULL,
  maintenance_request_id BIGINT NULL,
  category VARCHAR(24) NOT NULL,
  display_name VARCHAR(180) NOT NULL,
  storage_name CHAR(36) NOT NULL,
  media_type VARCHAR(80) NOT NULL,
  file_size BIGINT NOT NULL,
  uploaded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  uploaded_by BIGINT NULL,
  archived_at TIMESTAMP NULL,
  replaced_by_document_id BIGINT NULL,
  PRIMARY KEY (document_id),
  UNIQUE KEY uq_document_storage_name (storage_name),
  CONSTRAINT fk_document_property FOREIGN KEY (property_id) REFERENCES property(property_id) ON DELETE RESTRICT,
  CONSTRAINT fk_document_agreement FOREIGN KEY (agreement_id) REFERENCES rental_agreement(agreement_id) ON DELETE RESTRICT,
  CONSTRAINT fk_document_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(tenant_id) ON DELETE RESTRICT,
  CONSTRAINT fk_document_maintenance FOREIGN KEY (maintenance_request_id) REFERENCES maintenance_request(request_id) ON DELETE RESTRICT,
  CONSTRAINT fk_document_replacement FOREIGN KEY (replaced_by_document_id) REFERENCES property_document(document_id) ON DELETE RESTRICT,
  CONSTRAINT ck_document_link CHECK ((property_id IS NOT NULL) + (agreement_id IS NOT NULL) + (tenant_id IS NOT NULL) + (maintenance_request_id IS NOT NULL) >= 1),
  CONSTRAINT ck_document_category CHECK (category IN ('LEASE','IDENTITY','PROPERTY','INSPECTION','MAINTENANCE','OTHER')),
  CONSTRAINT ck_document_size CHECK (file_size BETWEEN 1 AND 20971520),
  INDEX idx_document_property (property_id, archived_at),
  INDEX idx_document_agreement (agreement_id, archived_at),
  INDEX idx_document_tenant (tenant_id, archived_at),
  INDEX idx_document_maintenance (maintenance_request_id, archived_at)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS inspection (
  inspection_id BIGINT NOT NULL AUTO_INCREMENT,
  property_id BIGINT NOT NULL,
  agreement_id BIGINT NULL,
  inspection_date DATE NOT NULL,
  inspection_type VARCHAR(16) NOT NULL,
  status VARCHAR(12) NOT NULL DEFAULT 'DRAFT',
  summary VARCHAR(500) NULL,
  created_by BIGINT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  completed_at TIMESTAMP NULL,
  PRIMARY KEY (inspection_id),
  CONSTRAINT fk_inspection_property FOREIGN KEY (property_id) REFERENCES property(property_id) ON DELETE RESTRICT,
  CONSTRAINT fk_inspection_agreement FOREIGN KEY (agreement_id) REFERENCES rental_agreement(agreement_id) ON DELETE RESTRICT,
  CONSTRAINT ck_inspection_type CHECK (inspection_type IN ('MOVE_IN','ROUTINE','MOVE_OUT','FOLLOW_UP')),
  CONSTRAINT ck_inspection_status CHECK (status IN ('DRAFT','COMPLETED')),
  INDEX idx_inspection_property_date (property_id, inspection_date)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS inspection_finding (
  finding_id BIGINT NOT NULL AUTO_INCREMENT,
  inspection_id BIGINT NOT NULL,
  area VARCHAR(80) NOT NULL,
  condition_rating VARCHAR(12) NOT NULL,
  observation VARCHAR(500) NOT NULL,
  follow_up_action VARCHAR(500) NULL,
  maintenance_request_id BIGINT NULL,
  PRIMARY KEY (finding_id),
  CONSTRAINT fk_finding_inspection FOREIGN KEY (inspection_id) REFERENCES inspection(inspection_id) ON DELETE RESTRICT,
  CONSTRAINT fk_finding_maintenance FOREIGN KEY (maintenance_request_id) REFERENCES maintenance_request(request_id) ON DELETE RESTRICT,
  CONSTRAINT ck_finding_condition CHECK (condition_rating IN ('GOOD','FAIR','POOR','URGENT')),
  INDEX idx_finding_inspection (inspection_id)
) ENGINE=InnoDB;
