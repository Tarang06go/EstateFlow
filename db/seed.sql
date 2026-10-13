-- EstateFlow demo data. Run once on an empty `estateflow` database after schema.sql:
--   mysql -u <user> -p estateflow < db/seed.sql
-- Dates are relative to the day the script runs so the demo always shows realistic overdue / expiring data.
-- (The application itself never uses CURDATE(); this one-off script is not application logic.)
USE estateflow;

INSERT INTO owner (full_name, phone, email, address) VALUES
  ('Harpreet Singh', '+919815011111', 'harpreet@example.com', 'Model Town, Patiala'),
  ('Meera Kapoor',   '+919876522222', 'meera@example.com',    'Sector 17, Chandigarh');

INSERT INTO tenant (full_name, phone, email, address, id_proof_type, id_proof_number, emergency_contact_name, emergency_contact_phone) VALUES
  ('Rohit Sharma', '+919810033333', 'rohit@example.com', 'Ludhiana', 'AADHAAR', '2345-6789-0123', 'Anita Sharma', '+919810044444'),
  ('Simran Kaur',  '+919855055555', 'simran@example.com', 'Sangrur',  'PAN',     'ABCDE1234F',     NULL, NULL),
  ('Vikram Mehta', '+919888066666', NULL,                 'Mohali',   NULL,      NULL,             NULL, NULL);

INSERT INTO property (owner_id, name, property_type, address_line, city, state, postal_code, bedrooms, area_sqft, listed_rent) VALUES
  (1, 'Greenview Apartment 2A', 'APARTMENT', '12 Mall Road',        'Patiala',    'Punjab', '147001', 2,  980.00, 18000.00),
  (1, 'Sunrise Villa',          'VILLA',     '45 Model Town',       'Patiala',    'Punjab', '147001', 4, 2400.00, 42000.00),
  (2, 'City Centre Shop 7',     'SHOP',      'Sector 17 Plaza',     'Chandigarh', 'Chandigarh', '160017', NULL, 450.00, 30000.00),
  (2, 'Lake View Flat 5C',      'APARTMENT', 'Sukhna Enclave',      'Chandigarh', 'Chandigarh', '160019', 3, 1300.00, 26000.00);

-- Agreement 1: started 3 months ago, 12 months, rent paid up (payments below)
-- Agreement 2: started 2 months ago, 6 months, nothing paid -> overdue rent
-- Agreement 3: started 11 months ago, 12 months -> ends this month (expiring soon), latest charge unpaid
INSERT INTO rental_agreement (property_id, tenant_id, start_date, duration_months, end_date, monthly_rent, security_deposit, status, notes) VALUES
  (1, 1, DATE_FORMAT(CURDATE() - INTERVAL 3 MONTH,  '%Y-%m-01'), 12, DATE_SUB(DATE_ADD(DATE_FORMAT(CURDATE() - INTERVAL 3 MONTH,  '%Y-%m-01'), INTERVAL 12 MONTH), INTERVAL 1 DAY), 18000.00, 36000.00, 'ACTIVE', 'Demo agreement'),
  (2, 2, DATE_FORMAT(CURDATE() - INTERVAL 2 MONTH,  '%Y-%m-01'),  6, DATE_SUB(DATE_ADD(DATE_FORMAT(CURDATE() - INTERVAL 2 MONTH,  '%Y-%m-01'), INTERVAL  6 MONTH), INTERVAL 1 DAY), 42000.00, 84000.00, 'ACTIVE', NULL),
  (3, 3, DATE_FORMAT(CURDATE() - INTERVAL 11 MONTH, '%Y-%m-01'), 12, DATE_SUB(DATE_ADD(DATE_FORMAT(CURDATE() - INTERVAL 11 MONTH, '%Y-%m-01'), INTERVAL 12 MONTH), INTERVAL 1 DAY), 30000.00, 60000.00, 'ACTIVE', NULL);

-- One rent charge per month (BR-14): period i starts at start_date + i months, due on its start.
INSERT INTO rent_charge (agreement_id, period_start, period_end, due_date, amount_due)
WITH RECURSIVE n (i) AS (SELECT 0 UNION ALL SELECT i + 1 FROM n WHERE i < 119)
SELECT ra.agreement_id,
       DATE_ADD(ra.start_date, INTERVAL n.i MONTH),
       DATE_SUB(DATE_ADD(ra.start_date, INTERVAL n.i + 1 MONTH), INTERVAL 1 DAY),
       DATE_ADD(ra.start_date, INTERVAL n.i MONTH),
       ra.monthly_rent
FROM rental_agreement ra JOIN n ON n.i < ra.duration_months;

-- Agreement 1: every charge due before this month is paid in full by UPI; this month's is half paid.
INSERT INTO payment (charge_id, amount, payment_date, payment_method, reference_no)
SELECT rc.charge_id, rc.amount_due, rc.due_date, 'UPI', CONCAT('UPI-DEMO-', rc.charge_id)
FROM rent_charge rc WHERE rc.agreement_id = 1 AND rc.due_date < DATE_FORMAT(CURDATE(), '%Y-%m-01');
INSERT INTO payment (charge_id, amount, payment_date, payment_method, notes)
SELECT rc.charge_id, rc.amount_due / 2, rc.due_date, 'CASH', 'Partial payment'
FROM rent_charge rc WHERE rc.agreement_id = 1 AND rc.due_date = DATE_FORMAT(CURDATE(), '%Y-%m-01');

-- Agreement 3: all but the two latest due charges are paid by bank transfer.
INSERT INTO payment (charge_id, amount, payment_date, payment_method, reference_no)
SELECT rc.charge_id, rc.amount_due, rc.due_date, 'BANK_TRANSFER', CONCAT('NEFT-DEMO-', rc.charge_id)
FROM rent_charge rc WHERE rc.agreement_id = 3 AND rc.due_date < DATE_FORMAT(CURDATE() - INTERVAL 1 MONTH, '%Y-%m-01');

INSERT INTO maintenance_request (property_id, tenant_id, title, description, priority, status, reported_date, assigned_to) VALUES
  (1, 1, 'Kitchen tap leaking', 'Dripping continuously since last week.', 'MEDIUM', 'OPEN', CURDATE() - INTERVAL 3 DAY, NULL),
  (2, 2, 'Geyser not heating', NULL, 'HIGH', 'IN_PROGRESS', CURDATE() - INTERVAL 6 DAY, 'Local plumber');
