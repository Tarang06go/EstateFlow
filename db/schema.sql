CREATE DATABASE IF NOT EXISTS estateflow
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE estateflow;

CREATE TABLE owner (
                       owner_id BIGINT NOT NULL AUTO_INCREMENT,
                       full_name VARCHAR(100) NOT NULL,
                       phone VARCHAR(20) NOT NULL,
                       email VARCHAR(120) NULL,
                       address VARCHAR(255) NULL,
                       is_active TINYINT(1) NOT NULL DEFAULT 1,
                       created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                       PRIMARY KEY (owner_id),
                       UNIQUE KEY uq_owner_phone (phone)
) ENGINE=InnoDB;

CREATE TABLE tenant (
                        tenant_id BIGINT NOT NULL AUTO_INCREMENT,
                        full_name VARCHAR(100) NOT NULL,
                        phone VARCHAR(20) NOT NULL,
                        email VARCHAR(120) NULL,
                        address VARCHAR(255) NULL,
                        id_proof_type VARCHAR(30) NULL,
                        id_proof_number VARCHAR(50) NULL,
                        emergency_contact_name VARCHAR(100) NULL,
                        emergency_contact_phone VARCHAR(20) NULL,
                        is_active TINYINT(1) NOT NULL DEFAULT 1,
                        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        PRIMARY KEY (tenant_id),
                        UNIQUE KEY uq_tenant_phone (phone),
                        UNIQUE KEY uq_tenant_id_proof (id_proof_type, id_proof_number),
                        CONSTRAINT ck_tenant_id_proof_pair CHECK (
                            (id_proof_type IS NULL AND id_proof_number IS NULL)
                                OR (id_proof_type IS NOT NULL AND id_proof_number IS NOT NULL))
) ENGINE=InnoDB;
CREATE TABLE property (
                          property_id BIGINT NOT NULL AUTO_INCREMENT,
                          owner_id BIGINT NOT NULL,
                          name VARCHAR(100) NOT NULL,
                          property_type VARCHAR(20) NOT NULL,
                          address_line VARCHAR(255) NOT NULL,
                          city VARCHAR(60) NOT NULL,
                          state VARCHAR(60) NULL,
                          postal_code VARCHAR(12) NULL,
                          bedrooms TINYINT UNSIGNED NULL,
                          area_sqft DECIMAL(9,2) NULL,
                          listed_rent DECIMAL(12,2) NOT NULL,
                          is_active TINYINT(1) NOT NULL DEFAULT 1,
                          created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                          PRIMARY KEY (property_id),
                          CONSTRAINT fk_property_owner FOREIGN KEY (owner_id)
                              REFERENCES owner (owner_id) ON DELETE RESTRICT,
                          CONSTRAINT ck_property_rent CHECK (listed_rent >= 0),
                          INDEX idx_property_owner (owner_id)
) ENGINE=InnoDB;
CREATE TABLE rental_agreement (
                                  agreement_id BIGINT NOT NULL AUTO_INCREMENT,
                                  property_id BIGINT NOT NULL,
                                  tenant_id BIGINT NOT NULL,
                                  start_date DATE NOT NULL,
                                  duration_months SMALLINT NOT NULL,
                                  end_date DATE NOT NULL,
                                  monthly_rent DECIMAL(12,2) NOT NULL,
                                  security_deposit DECIMAL(12,2) NOT NULL DEFAULT 0.00,
                                  status VARCHAR(15) NOT NULL DEFAULT 'ACTIVE',
                                  termination_date DATE NULL,
                                  notes VARCHAR(500) NULL,
-- Single database-level definition of the agreement's effective end (BR-12).
-- Never written by INSERT/UPDATE; MySQL maintains it.
                                  effective_end_date DATE GENERATED ALWAYS AS (COALESCE(termination_date, end_date)) STORED,
                                  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                  PRIMARY KEY (agreement_id),
                                  CONSTRAINT fk_agreement_property FOREIGN KEY (property_id)
                                      REFERENCES property (property_id) ON DELETE RESTRICT,
                                  CONSTRAINT fk_agreement_tenant FOREIGN KEY (tenant_id)
                                      REFERENCES tenant (tenant_id) ON DELETE RESTRICT,
                                  CONSTRAINT ck_agreement_dates CHECK (end_date > start_date),
                                  CONSTRAINT ck_agreement_months CHECK (duration_months BETWEEN 1 AND 120),
                                  CONSTRAINT ck_agreement_rent CHECK (monthly_rent > 0),
                                  CONSTRAINT ck_agreement_deposit CHECK (security_deposit >= 0),
                                  CONSTRAINT ck_agreement_status CHECK (status IN ('ACTIVE','TERMINATED')),
                                  CONSTRAINT ck_agreement_termination CHECK (
                                      (status = 'ACTIVE' AND termination_date IS NULL)
                                          OR (status = 'TERMINATED' AND termination_date IS NOT NULL
                                          AND termination_date >= start_date AND termination_date < end_date)),
                                  INDEX idx_agreement_property_period (property_id, start_date, effective_end_date),
                                  INDEX idx_agreement_tenant (tenant_id),
                                  INDEX idx_agreement_effective_end (effective_end_date)
) ENGINE=InnoDB;

CREATE TABLE rent_charge (
                             charge_id BIGINT NOT NULL AUTO_INCREMENT,
                             agreement_id BIGINT NOT NULL,
                             period_start DATE NOT NULL,
                             period_end DATE NOT NULL,
                             due_date DATE NOT NULL,
                             amount_due DECIMAL(12,2) NOT NULL,
                             cancelled_at TIMESTAMP NULL,
                             PRIMARY KEY (charge_id),
                             CONSTRAINT fk_charge_agreement FOREIGN KEY (agreement_id)
                                 REFERENCES rental_agreement (agreement_id) ON DELETE RESTRICT,
                             CONSTRAINT uq_charge_period UNIQUE (agreement_id, period_start),
                             CONSTRAINT ck_charge_amount CHECK (amount_due > 0),
                             CONSTRAINT ck_charge_period CHECK (period_end > period_start),
                             INDEX idx_charge_due_date (due_date)
) ENGINE=InnoDB;
CREATE TABLE payment (
                         payment_id BIGINT NOT NULL AUTO_INCREMENT,
                         charge_id BIGINT NOT NULL,
                         amount DECIMAL(12,2) NOT NULL,
                         payment_date DATE NOT NULL,
                         payment_method VARCHAR(20) NOT NULL,
                         reference_no VARCHAR(60) NULL,
                         notes VARCHAR(255) NULL,
                         voided_at TIMESTAMP NULL,
                         void_reason VARCHAR(255) NULL,
                         created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                         PRIMARY KEY (payment_id),
                         CONSTRAINT fk_payment_charge FOREIGN KEY (charge_id)
                             REFERENCES rent_charge (charge_id) ON DELETE RESTRICT,
                         CONSTRAINT ck_payment_amount CHECK (amount > 0),
                         CONSTRAINT ck_payment_method CHECK (payment_method IN
                                                             ('CASH','UPI','BANK_TRANSFER','CHEQUE','CARD')),
                         CONSTRAINT ck_payment_void CHECK (
                             (voided_at IS NULL AND void_reason IS NULL)
                                 OR (voided_at IS NOT NULL AND void_reason IS NOT NULL)),
                         INDEX idx_payment_charge (charge_id),
                         INDEX idx_payment_date (payment_date)
) ENGINE=InnoDB;
CREATE TABLE maintenance_request (
                                     request_id BIGINT NOT NULL AUTO_INCREMENT,
                                     property_id BIGINT NOT NULL,
                                     tenant_id BIGINT NULL,
                                     title VARCHAR(120) NOT NULL,
                                     description TEXT NULL,
                                     priority VARCHAR(10) NOT NULL DEFAULT 'MEDIUM',
                                     status VARCHAR(15) NOT NULL DEFAULT 'OPEN',
                                     reported_date DATE NOT NULL,
                                     resolved_date DATE NULL,
                                     assigned_to VARCHAR(100) NULL,
                                     cost DECIMAL(12,2) NULL,
                                     created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                     updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                                         ON UPDATE CURRENT_TIMESTAMP,
                                     PRIMARY KEY (request_id),
                                     CONSTRAINT fk_maint_property FOREIGN KEY (property_id)
                                         REFERENCES property (property_id) ON DELETE RESTRICT,
                                     CONSTRAINT fk_maint_tenant FOREIGN KEY (tenant_id)
                                         REFERENCES tenant (tenant_id) ON DELETE SET NULL,
                                     CONSTRAINT ck_maint_priority CHECK (priority IN ('LOW','MEDIUM','HIGH','URGENT')),
                                     CONSTRAINT ck_maint_status CHECK (status IN
                                                                       ('OPEN','IN_PROGRESS','RESOLVED','CANCELLED')),
                                     CONSTRAINT ck_maint_resolution CHECK (
                                         (status = 'RESOLVED' AND resolved_date IS NOT NULL AND resolved_date >= reported_date)
                                             OR (status <> 'RESOLVED' AND resolved_date IS NULL)),
                                     CONSTRAINT ck_maint_cost CHECK (cost IS NULL OR cost >= 0),
                                     INDEX idx_maint_status (status),
                                     INDEX idx_maint_property (property_id)
) ENGINE=InnoDB;