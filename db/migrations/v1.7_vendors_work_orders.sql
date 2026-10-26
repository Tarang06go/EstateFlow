-- EstateFlow v1.7 (Phase 6): maintenance vendors and work orders.
-- Additive only. Requires migrations v1.1-v1.6. Apply manually after backup; never run at startup.
-- Apply: mysql -u <user> -p estateflow < db/migrations/v1.7_vendors_work_orders.sql
-- Rollback (export first):
--   ALTER TABLE property_document DROP FOREIGN KEY fk_document_work_order, DROP CHECK ck_document_link;
--   ALTER TABLE property_document DROP COLUMN work_order_id;
--   ALTER TABLE property_document ADD CONSTRAINT ck_document_link CHECK ((property_id IS NOT NULL) + (agreement_id IS NOT NULL) + (tenant_id IS NOT NULL) + (maintenance_request_id IS NOT NULL) >= 1);
--   DROP TABLE work_order; DROP TABLE maintenance_vendor;
-- Rollback permanently removes vendor and work-order history. Also removes the work_order_id document link and
-- work_order.expense_id link; linked expense records and stored files themselves are retained.

CREATE TABLE IF NOT EXISTS maintenance_vendor (
  vendor_id BIGINT NOT NULL AUTO_INCREMENT,
  name VARCHAR(120) NOT NULL,
  service_category VARCHAR(30) NOT NULL,
  phone VARCHAR(30) NULL,
  email VARCHAR(120) NULL,
  address VARCHAR(255) NULL,
  notes VARCHAR(500) NULL,
  is_active TINYINT(1) NOT NULL DEFAULT 1,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (vendor_id),
  CONSTRAINT ck_vendor_category CHECK (service_category IN ('PLUMBING','ELECTRICAL','HVAC','GENERAL_REPAIRS','CLEANING','LANDSCAPING','PEST_CONTROL','SECURITY','OTHER')),
  INDEX idx_vendor_active_category (is_active, service_category)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS work_order (
  work_order_id BIGINT NOT NULL AUTO_INCREMENT,
  property_id BIGINT NOT NULL,
  maintenance_request_id BIGINT NULL,
  vendor_id BIGINT NULL,
  expense_id BIGINT NULL,
  title VARCHAR(120) NOT NULL,
  scope VARCHAR(1000) NULL,
  priority VARCHAR(10) NOT NULL DEFAULT 'MEDIUM',
  due_date DATE NULL,
  status VARCHAR(12) NOT NULL DEFAULT 'DRAFT',
  estimated_cost DECIMAL(12,2) NULL,
  actual_cost DECIMAL(12,2) NULL,
  completion_notes VARCHAR(1000) NULL,
  created_by BIGINT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  completed_at TIMESTAMP NULL,
  PRIMARY KEY (work_order_id),
  CONSTRAINT fk_work_order_property FOREIGN KEY (property_id) REFERENCES property(property_id) ON DELETE RESTRICT,
  CONSTRAINT fk_work_order_request FOREIGN KEY (maintenance_request_id) REFERENCES maintenance_request(request_id) ON DELETE RESTRICT,
  CONSTRAINT fk_work_order_vendor FOREIGN KEY (vendor_id) REFERENCES maintenance_vendor(vendor_id) ON DELETE RESTRICT,
  CONSTRAINT fk_work_order_expense FOREIGN KEY (expense_id) REFERENCES expense(expense_id) ON DELETE RESTRICT,
  CONSTRAINT uq_work_order_expense UNIQUE (expense_id),
  CONSTRAINT ck_work_order_priority CHECK (priority IN ('LOW','MEDIUM','HIGH','URGENT')),
  CONSTRAINT ck_work_order_status CHECK (status IN ('DRAFT','ASSIGNED','IN_PROGRESS','COMPLETED','CANCELLED')),
  CONSTRAINT ck_work_order_costs CHECK ((estimated_cost IS NULL OR estimated_cost >= 0) AND (actual_cost IS NULL OR actual_cost >= 0)),
  INDEX idx_work_order_property_status (property_id, status),
  INDEX idx_work_order_vendor_status (vendor_id, status),
  INDEX idx_work_order_due (due_date, status),
  INDEX idx_work_order_request (maintenance_request_id)
) ENGINE=InnoDB;

-- Phase 5 document records can reference a work order. The conditional DDL keeps reapplication safe.
SET @has_col := (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='property_document' AND column_name='work_order_id');
SET @ddl := IF(@has_col=0,'ALTER TABLE property_document ADD COLUMN work_order_id BIGINT NULL AFTER maintenance_request_id','SELECT ''work_order_id already present''');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @has_fk := (SELECT COUNT(*) FROM information_schema.table_constraints WHERE constraint_schema=DATABASE() AND table_name='property_document' AND constraint_name='fk_document_work_order');
SET @ddl := IF(@has_fk=0,'ALTER TABLE property_document ADD CONSTRAINT fk_document_work_order FOREIGN KEY (work_order_id) REFERENCES work_order(work_order_id) ON DELETE RESTRICT','SELECT ''fk_document_work_order already present''');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @has_check := (SELECT COUNT(*) FROM information_schema.table_constraints WHERE constraint_schema=DATABASE() AND table_name='property_document' AND constraint_name='ck_document_link');
SET @ddl := IF(@has_check=1,'ALTER TABLE property_document DROP CHECK ck_document_link','SELECT ''ck_document_link already updated''');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl := 'ALTER TABLE property_document ADD CONSTRAINT ck_document_link CHECK ((property_id IS NOT NULL) + (agreement_id IS NOT NULL) + (tenant_id IS NOT NULL) + (maintenance_request_id IS NOT NULL) + (work_order_id IS NOT NULL) >= 1)';
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
