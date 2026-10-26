-- EstateFlow demo data. Run once on an EMPTY `estateflow` database after schema.sql:
--   mysql -u <user> -p estateflow < db/seed.sql
-- Dates are relative to the day the script runs, so the dashboard always shows realistic overdue, expiring and
-- historical data. (The application itself never uses CURDATE(); this one-off script is not application logic.)
USE estateflow;

INSERT INTO owner (full_name, phone, email, address) VALUES
  ('Harpreet Singh', '+919815011111', 'harpreet@example.com', 'Model Town, Patiala'),
  ('Meera Kapoor',   '+919876522222', 'meera@example.com',    'Sector 17, Chandigarh'),
  ('Gurdeep Sandhu', '+919814033444', 'gurdeep@example.com',  'Civil Lines, Ludhiana');

INSERT INTO tenant (full_name, phone, email, address, id_proof_type, id_proof_number, emergency_contact_name, emergency_contact_phone) VALUES
  ('Rohit Sharma',    '+919810033333', 'rohit@example.com',  'Ludhiana', 'AADHAAR', '2345-6789-0123', 'Anita Sharma', '+919810044444'),
  ('Simran Kaur',     '+919855055555', 'simran@example.com', 'Sangrur',  'PAN',     'ABCDE1234F',     NULL, NULL),
  ('Vikram Mehta',    '+919888066666', NULL,                 'Mohali',   NULL,      NULL,             NULL, NULL),
  ('Anita Desai',     '+919811177777', 'anita.d@example.com','Jalandhar','AADHAAR', '3456-7890-1234', 'Raj Desai', '+919811188888'),
  ('Karan Malhotra',  '+919822299999', 'karan.m@example.com','Amritsar', 'PASSPORT','N1234567',       NULL, NULL),
  ('Priya Nair',      '+919833300000', 'priya.n@example.com','Patiala',  'PAN',     'FGHIJ5678K',     NULL, NULL),
  ('Arjun Bedi',      '+919844411111', NULL,                 'Chandigarh', NULL,    NULL,             NULL, NULL);

INSERT INTO property (owner_id, name, property_type, address_line, city, state, postal_code, bedrooms, area_sqft, listed_rent) VALUES
  (1, 'Greenview Apartment 2A',  'APARTMENT', '12 Mall Road',            'Patiala',    'Punjab',     '147001', 2,  980.00, 18000.00),
  (1, 'Sunrise Villa',           'VILLA',     '45 Model Town',           'Patiala',    'Punjab',     '147001', 4, 2400.00, 42000.00),
  (2, 'City Centre Shop 7',      'SHOP',      'Sector 17 Plaza',         'Chandigarh', 'Chandigarh', '160017', NULL, 450.00, 30000.00),
  (2, 'Lake View Flat 5C',       'APARTMENT', 'Sukhna Enclave',          'Chandigarh', 'Chandigarh', '160019', 3, 1300.00, 26000.00),
  (3, 'Orchid Residency 101',    'APARTMENT', '8 Civil Lines',           'Ludhiana',   'Punjab',     '141001', 2, 1050.00, 22000.00),
  (3, 'Orchid Residency 102',    'APARTMENT', '8 Civil Lines',           'Ludhiana',   'Punjab',     '141001', 2, 1050.00, 19500.00),
  (1, 'Model Town House 14',     'HOUSE',     '14 Model Town Extension', 'Patiala',    'Punjab',     '147001', 3, 1800.00, 33000.00),
  (2, 'Sector 22 Office Suite',  'OFFICE',    'SCO 114, Sector 22',      'Chandigarh', 'Chandigarh', '160022', NULL, 800.00, 35000.00),
  (3, 'Hillview Studio',         'APARTMENT', '3 Hill Road',             'Ludhiana',   'Punjab',     '141002', 1,  520.00, 12000.00);

-- Agreements (all start on the 1st of a month, so rent falls due on the 1st):
--  1 paid up, current month half paid     2 nothing paid -> overdue          3 ends this month, last two unpaid
--  4 current month unpaid                  5 last month part-paid            6 current month unpaid
--  7 terminated two months ago (later charges cancelled)                     8 starts next month (upcoming)
INSERT INTO rental_agreement (property_id, tenant_id, start_date, duration_months, end_date, monthly_rent, security_deposit, status, termination_date, notes)
SELECT p, t, s, m, DATE_SUB(DATE_ADD(s, INTERVAL m MONTH), INTERVAL 1 DAY), rent, dep, st, term, note FROM (
  SELECT 1 p, 1 t, DATE_FORMAT(CURDATE() - INTERVAL 5 MONTH,  '%Y-%m-01') s, 12 m, 18000.00 rent, 36000.00 dep, 'ACTIVE' st, NULL term, 'Demo agreement' note UNION ALL
  SELECT 2, 2, DATE_FORMAT(CURDATE() - INTERVAL 2 MONTH,  '%Y-%m-01'),  6, 42000.00, 84000.00, 'ACTIVE', NULL, NULL UNION ALL
  SELECT 3, 3, DATE_FORMAT(CURDATE() - INTERVAL 11 MONTH, '%Y-%m-01'), 12, 30000.00, 60000.00, 'ACTIVE', NULL, NULL UNION ALL
  SELECT 5, 4, DATE_FORMAT(CURDATE() - INTERVAL 8 MONTH,  '%Y-%m-01'), 12, 22000.00, 44000.00, 'ACTIVE', NULL, NULL UNION ALL
  SELECT 6, 5, DATE_FORMAT(CURDATE() - INTERVAL 4 MONTH,  '%Y-%m-01'),  6, 19500.00, 39000.00, 'ACTIVE', NULL, NULL UNION ALL
  SELECT 8, 6, DATE_FORMAT(CURDATE() - INTERVAL 6 MONTH,  '%Y-%m-01'), 12, 35000.00, 70000.00, 'ACTIVE', NULL, 'Office lease, GST invoice monthly' UNION ALL
  SELECT 7, 7, DATE_FORMAT(CURDATE() - INTERVAL 10 MONTH, '%Y-%m-01'), 12, 33000.00, 66000.00, 'TERMINATED', LAST_DAY(CURDATE() - INTERVAL 2 MONTH), 'Tenant relocated' UNION ALL
  SELECT 4, 1, DATE_FORMAT(CURDATE() + INTERVAL 1 MONTH,  '%Y-%m-01'), 12, 26000.00, 52000.00, 'ACTIVE', NULL, 'Starts next month'
) a;

-- One rent charge per month (BR-14): period i starts at start_date + i months and is due on its first day.
INSERT INTO rent_charge (agreement_id, period_start, period_end, due_date, amount_due)
WITH RECURSIVE n (i) AS (SELECT 0 UNION ALL SELECT i + 1 FROM n WHERE i < 119)
SELECT ra.agreement_id,
       DATE_ADD(ra.start_date, INTERVAL n.i MONTH),
       DATE_SUB(DATE_ADD(ra.start_date, INTERVAL n.i + 1 MONTH), INTERVAL 1 DAY),
       DATE_ADD(ra.start_date, INTERVAL n.i MONTH),
       ra.monthly_rent
FROM rental_agreement ra JOIN n ON n.i < ra.duration_months;

-- Agreement 7 was terminated: charges for periods after the termination date are cancelled (BR-15).
UPDATE rent_charge rc JOIN rental_agreement ra ON ra.agreement_id = rc.agreement_id
   SET rc.cancelled_at = UTC_TIMESTAMP()
 WHERE ra.agreement_id = 7 AND rc.period_start > ra.termination_date;

-- Payments: only charges that are already due can be paid (BR-20); dates never lie in the future (BR-22).
SET @this_month = DATE_FORMAT(CURDATE(), '%Y-%m-01');
INSERT INTO payment (charge_id, amount, payment_date, payment_method, reference_no)
SELECT rc.charge_id, rc.amount_due, LEAST(DATE_ADD(rc.due_date, INTERVAL 2 DAY), CURDATE()),
       CASE rc.agreement_id WHEN 1 THEN 'UPI' WHEN 3 THEN 'BANK_TRANSFER' WHEN 4 THEN 'BANK_TRANSFER' WHEN 5 THEN 'CHEQUE' WHEN 6 THEN 'UPI' ELSE 'CASH' END,
       CONCAT('DEMO-', rc.charge_id)
FROM rent_charge rc
WHERE rc.cancelled_at IS NULL AND rc.due_date <= CURDATE() AND (
      (rc.agreement_id = 1 AND rc.due_date < @this_month)
   OR (rc.agreement_id = 3 AND rc.due_date < @this_month - INTERVAL 1 MONTH)
   OR (rc.agreement_id = 4 AND rc.due_date < @this_month)
   OR (rc.agreement_id = 5 AND rc.due_date < @this_month - INTERVAL 1 MONTH)
   OR (rc.agreement_id = 6 AND rc.due_date < @this_month)
   OR (rc.agreement_id = 7));

-- Partial payments: agreement 1 this month (half), agreement 5 last month (60%).
INSERT INTO payment (charge_id, amount, payment_date, payment_method, notes)
SELECT rc.charge_id, ROUND(rc.amount_due / 2, 2), LEAST(DATE_ADD(rc.due_date, INTERVAL 3 DAY), CURDATE()), 'CASH', 'Partial payment'
FROM rent_charge rc WHERE rc.agreement_id = 1 AND rc.due_date = @this_month AND rc.due_date <= CURDATE();
INSERT INTO payment (charge_id, amount, payment_date, payment_method, notes)
SELECT rc.charge_id, ROUND(rc.amount_due * 0.6, 2), LEAST(DATE_ADD(rc.due_date, INTERVAL 5 DAY), CURDATE()), 'UPI', 'Balance promised next week'
FROM rent_charge rc WHERE rc.agreement_id = 5 AND rc.due_date = @this_month - INTERVAL 1 MONTH;

INSERT INTO maintenance_request (property_id, tenant_id, title, description, priority, status, reported_date, resolved_date, assigned_to, cost) VALUES
  (1, 1, 'Kitchen tap leaking',        'Dripping continuously since last week.',   'MEDIUM', 'OPEN',        CURDATE() - INTERVAL 3 DAY,  NULL, NULL, NULL),
  (2, 2, 'Geyser not heating',         NULL,                                       'HIGH',   'IN_PROGRESS', CURDATE() - INTERVAL 6 DAY,  NULL, 'Local plumber', 2800.00),
  (5, 4, 'Ceiling seepage in bedroom', 'Damp patch after heavy rain.',             'URGENT', 'OPEN',        CURDATE() - INTERVAL 2 DAY,  NULL, NULL, NULL),
  (6, 5, 'Replace door lock',          NULL,                                       'LOW',    'OPEN',        CURDATE() - INTERVAL 12 DAY, NULL, NULL, NULL),
  (3, 3, 'Shutter repair',             'Rolling shutter jams halfway.',            'MEDIUM', 'RESOLVED',    CURDATE() - INTERVAL 70 DAY, CURDATE() - INTERVAL 66 DAY, 'Metalworks Ltd', 6500.00),
  (8, 6, 'AC service',                 'Annual service for two split units.',      'LOW',    'RESOLVED',    CURDATE() - INTERVAL 45 DAY, CURDATE() - INTERVAL 44 DAY, 'CoolCare', 3200.00),
  (1, 1, 'Repaint living room',        NULL,                                       'LOW',    'RESOLVED',    CURDATE() - INTERVAL 28 DAY, CURDATE() - INTERVAL 20 DAY, 'Paint crew', 9800.00),
  (7, NULL, 'Garden wall crack',       'Found during inspection after tenant exit.','MEDIUM', 'IN_PROGRESS', CURDATE() - INTERVAL 15 DAY, NULL, 'Mason', 4500.00);
