-- =====================================================================
-- Smart Hostel Management System - Idempotent Consolidated Sample Seed
-- Default password for newly seeded users: Password@123 (BCrypt $2a$10)
-- Safe to re-run repeatedly; uses INSERT IGNORE / NOT EXISTS guards.
-- All DATETIME timestamps stored in UTC; UI renders in Asia/Kolkata.
-- =====================================================================

-- BCrypt hash for 'Password@123'
SET @SEED_PWD = '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy';

-- 1. Core Hostels
INSERT IGNORE INTO hostels (code, name, address, is_active, created_at, updated_at) VALUES
('MAIN', 'Main Block', 'Campus North Wing', TRUE, UTC_TIMESTAMP(), UTC_TIMESTAMP()),
('FL1',  'Floor 1',    'Main Block - First Floor', TRUE, UTC_TIMESTAMP(), UTC_TIMESTAMP()),
('FL2',  'Floor 2',    'Main Block - Second Floor', TRUE, UTC_TIMESTAMP(), UTC_TIMESTAMP());

-- 2. Rooms & Beds
INSERT IGNORE INTO rooms (room_number, floor, capacity, status, hostel_id, created_at)
SELECT 'S-101', 1, 2, 'active', h.id, UTC_TIMESTAMP() FROM hostels h WHERE h.code = 'FL1';
INSERT IGNORE INTO rooms (room_number, floor, capacity, status, hostel_id, created_at)
SELECT 'S-102', 1, 2, 'active', h.id, UTC_TIMESTAMP() FROM hostels h WHERE h.code = 'FL1';
INSERT IGNORE INTO rooms (room_number, floor, capacity, status, hostel_id, created_at)
SELECT 'S-201', 2, 2, 'active', h.id, UTC_TIMESTAMP() FROM hostels h WHERE h.code = 'FL2';

INSERT INTO beds (room_id, bed_number, created_at)
SELECT r.id, n.num, UTC_TIMESTAMP()
FROM rooms r
CROSS JOIN (SELECT 1 AS num UNION ALL SELECT 2 AS num) n
WHERE r.room_number IN ('S-101', 'S-102', 'S-201')
  AND NOT EXISTS (SELECT 1 FROM beds b WHERE b.room_id = r.id AND b.bed_number = n.num);

-- 3. Users: 1 Admin, 2 Wardens, 1 Security, 3 Workers, 5 Students
INSERT IGNORE INTO users (email, register_number, name, phone, password_hash, role, status, is_active, must_change_password, created_at) VALUES
('seed.admin@hostel.edu',    NULL,        'Dr. Admin Rao',      '9000000001', @SEED_PWD, 'ADMIN',    'active', TRUE, FALSE, UTC_TIMESTAMP()),
('seed.warden1@hostel.edu',  NULL,        'Warden Ramesh',      '9000000002', @SEED_PWD, 'WARDEN',   'active', TRUE, FALSE, UTC_TIMESTAMP()),
('seed.warden2@hostel.edu',  NULL,        'Warden Kavitha',     '9000000003', @SEED_PWD, 'WARDEN',   'active', TRUE, FALSE, UTC_TIMESTAMP()),
('seed.security@hostel.edu', NULL,        'Gate Security Mani', '9000000004', @SEED_PWD, 'SECURITY', 'active', TRUE, FALSE, UTC_TIMESTAMP()),
('seed.worker1@hostel.edu',  NULL,        'Plumber Muthu',      '9000000005', @SEED_PWD, 'WORKER',   'active', TRUE, FALSE, UTC_TIMESTAMP()),
('seed.worker2@hostel.edu',  NULL,        'Electrician Selvam', '9000000006', @SEED_PWD, 'WORKER',   'active', TRUE, FALSE, UTC_TIMESTAMP()),
('seed.worker3@hostel.edu',  NULL,        'Carpenter Kumar',    '9000000007', @SEED_PWD, 'WORKER',   'active', TRUE, FALSE, UTC_TIMESTAMP()),
('seed.stu1@hostel.edu',     '24SEED001', 'Ananya Sundar',      '9800000001', @SEED_PWD, 'STUDENT',  'active', TRUE, FALSE, UTC_TIMESTAMP()),
('seed.stu2@hostel.edu',     '24SEED002', 'Karthik Raja',       '9800000002', @SEED_PWD, 'STUDENT',  'active', TRUE, FALSE, UTC_TIMESTAMP()),
('seed.stu3@hostel.edu',     '24SEED003', 'Priya Dharshini',    '9800000003', @SEED_PWD, 'STUDENT',  'active', TRUE, FALSE, UTC_TIMESTAMP()),
('seed.stu4@hostel.edu',     '24SEED004', 'Vikram Aditya',      '9800000004', @SEED_PWD, 'STUDENT',  'active', TRUE, FALSE, UTC_TIMESTAMP()),
('seed.stu5@hostel.edu',     '24SEED005', 'Divya Bharathi',     '9800000005', @SEED_PWD, 'STUDENT',  'active', TRUE, FALSE, UTC_TIMESTAMP());

-- 4. Staff Profiles, Worker Profiles, Warden Assignments
INSERT INTO staff_profiles (user_id, full_name, phone, designation, created_at)
SELECT u.id, u.name, u.phone,
       CASE u.role WHEN 'ADMIN' THEN 'Chief Hostel Administrator' WHEN 'WARDEN' THEN 'Hostel Warden' ELSE 'Gate Security Officer' END,
       UTC_TIMESTAMP()
FROM users u
WHERE u.email IN ('seed.admin@hostel.edu', 'seed.warden1@hostel.edu', 'seed.warden2@hostel.edu', 'seed.security@hostel.edu')
  AND NOT EXISTS (SELECT 1 FROM staff_profiles sp WHERE sp.user_id = u.id);

INSERT INTO warden_assignments (warden_user_id, hostel_id, is_primary, active, assigned_at)
SELECT u.id, h.id, TRUE, TRUE, UTC_TIMESTAMP()
FROM users u JOIN hostels h
  ON (u.email = 'seed.warden1@hostel.edu' AND h.code = 'FL1')
  OR (u.email = 'seed.warden2@hostel.edu' AND h.code = 'FL2')
WHERE NOT EXISTS (SELECT 1 FROM warden_assignments wa WHERE wa.warden_user_id = u.id AND wa.hostel_id = h.id);

INSERT INTO worker_profiles (user_id, full_name, phone, specialization, active, created_at)
SELECT u.id, u.name, u.phone,
       CASE u.email
         WHEN 'seed.worker1@hostel.edu' THEN 'PLUMBING'
         WHEN 'seed.worker2@hostel.edu' THEN 'ELECTRICAL'
         ELSE 'CARPENTRY'
       END,
       TRUE, UTC_TIMESTAMP()
FROM users u
WHERE u.email LIKE 'seed.worker%@hostel.edu'
  AND NOT EXISTS (SELECT 1 FROM worker_profiles wp WHERE wp.user_id = u.id);

-- 5. Student Profiles + Bed Assignments
INSERT INTO students (user_id, full_name, register_number, college_email, phone, department, `year`, hostel_id, room_id, guardian_name, guardian_phone, guardian_relation, is_active, created_at)
SELECT u.id, u.name, u.register_number, u.email, u.phone,
       CASE WHEN u.register_number IN ('24SEED001','24SEED002') THEN 'CSE' ELSE 'ECE' END,
       2,
       (SELECT r.hostel_id FROM rooms r WHERE r.room_number = 'S-101' LIMIT 1),
       (SELECT r.id FROM rooms r WHERE r.room_number = 'S-101' LIMIT 1),
       CONCAT('Parent of ', u.name), '9900000099', 'Parent', TRUE, UTC_TIMESTAMP()
FROM users u
WHERE u.email LIKE 'seed.stu%@hostel.edu'
  AND NOT EXISTS (SELECT 1 FROM students s WHERE s.user_id = u.id);

-- 6. Sample Outpass & Outpass Scan Log
INSERT INTO outpasses (student_id, pass_type, departure_date_time, return_date_time, reason, destination, emergency_contact, status, decided_by_user_id, decided_at, actual_exit_time, created_at)
SELECT s.id, 'DAY_PASS', UTC_TIMESTAMP(), DATE_ADD(UTC_TIMESTAMP(), INTERVAL 4 HOUR),
       'Medical checkup (Seed Sample)', 'PSG Hospital', '9900000099', 'out',
       (SELECT id FROM users WHERE email = 'seed.warden1@hostel.edu' LIMIT 1),
       UTC_TIMESTAMP(), UTC_TIMESTAMP(), UTC_TIMESTAMP()
FROM students s WHERE s.register_number = '24SEED001'
  AND NOT EXISTS (SELECT 1 FROM outpasses o WHERE o.student_id = s.id AND o.reason = 'Medical checkup (Seed Sample)');

INSERT INTO outpass_scan_logs (outpass_id, scanned_by_user_id, scan_action, scan_result, notes, scanned_at)
SELECT o.id, (SELECT id FROM users WHERE email = 'seed.security@hostel.edu' LIMIT 1),
       'exit', 'ALLOWED', 'Seed gate exit verification', UTC_TIMESTAMP()
FROM outpasses o
WHERE o.reason = 'Medical checkup (Seed Sample)'
  AND NOT EXISTS (SELECT 1 FROM outpass_scan_logs l WHERE l.outpass_id = o.id);

-- 7. Sample Maintenance Request linked to Core hostel/room/worker
INSERT IGNORE INTO maintenance_requests (request_code, student_id, category, title, description, hostel_block, room_number, hostel_id, room_id, priority, status, assigned_worker_id, assigned_by_user_id, assigned_at, created_at, updated_at)
SELECT 'REQ-SEED-0001', s.id, 'PLUMBING', 'Tap leakage in washbasin', 'Constant drip observed since morning.',
       'Floor 1', 'S-101', s.hostel_id, s.room_id, 'MEDIUM', 'assigned',
       (SELECT id FROM worker_profiles WHERE user_id = (SELECT id FROM users WHERE email = 'seed.worker1@hostel.edu' LIMIT 1) LIMIT 1),
       (SELECT id FROM users WHERE email = 'seed.warden1@hostel.edu' LIMIT 1),
       UTC_TIMESTAMP(), UTC_TIMESTAMP(), UTC_TIMESTAMP()
FROM students s WHERE s.register_number = '24SEED002';

-- 8. Sample Parcel + Unified QR Token
INSERT IGNORE INTO parcels (parcel_code, student_id, hostel_block, hostel_id, courier_name, parcel_type, is_cod, status, received_at, received_by_user_id, created_at)
SELECT 'PRC-SEED-0001', s.id, 'Floor 1', s.hostel_id, 'BlueDart', 'BOX', FALSE, 'notified',
       UTC_TIMESTAMP(), (SELECT id FROM users WHERE email = 'seed.warden1@hostel.edu' LIMIT 1), UTC_TIMESTAMP()
FROM students s WHERE s.register_number = '24SEED003';

INSERT IGNORE INTO qr_tokens (module, entity_id, student_id, qr_token, pickup_code, used, closed, created_at)
SELECT 'PARCEL', p.id, p.student_id, 'PRC1.SEED.0001', '818283', FALSE, FALSE, UTC_TIMESTAMP()
FROM parcels p WHERE p.parcel_code = 'PRC-SEED-0001';

-- 9. Sample Published Circular + Notification
INSERT IGNORE INTO circulars (circular_code, type, title, description, rules_and_regulations, priority, status, require_acknowledgement, target_scope, hostel_block, hostel_id, published_at, created_by_user_id, created_at, updated_at)
SELECT 'CIR-SEED-0001', 'CIRCULAR', 'Unified Campus Hostel Guidelines',
       'All students must carry their digital outpass and mess token QR codes.',
       '1. Maintain silence after 10 PM. 2. Mark evening digital attendance on time.',
       'IMPORTANT', 'published', TRUE, 'ALL', 'ALL', NULL,
       UTC_TIMESTAMP(), (SELECT id FROM users WHERE email = 'seed.admin@hostel.edu' LIMIT 1), UTC_TIMESTAMP(), UTC_TIMESTAMP();

INSERT INTO notifications (user_id, type, module, title, message, link, reference_type, reference_id, is_read, created_at)
SELECT u.id, 'info', 'CIRCULAR', 'New Circular: Unified Campus Hostel Guidelines',
       'Please read and acknowledge the updated campus hostel guidelines.',
       '/student.html#circulars', 'CIRCULAR',
       (SELECT id FROM circulars WHERE circular_code = 'CIR-SEED-0001' LIMIT 1),
       FALSE, UTC_TIMESTAMP()
FROM users u WHERE u.email = 'seed.stu1@hostel.edu'
  AND NOT EXISTS (SELECT 1 FROM notifications n WHERE n.user_id = u.id AND n.title = 'New Circular: Unified Campus Hostel Guidelines');
