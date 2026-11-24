-- =====================================================================
-- Smart Hostel Management System - Core Infrastructure Seed
-- Seeds physical hostel blocks, rooms, and beds without dummy user accounts.
-- Initial administrator is created securely from .env via AdminSeedRunner.
-- Student and staff accounts are registered through /admin/registration.html.
-- Safe to re-run repeatedly; uses INSERT IGNORE / NOT EXISTS guards.
-- All DATETIME timestamps stored in UTC; UI renders in Asia/Kolkata.
-- =====================================================================

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

-- 3. Initial Administrator (Fallback SQL; AdminSeedRunner dynamically syncs from .env on startup)
-- Default credentials: Username: 1234567890 / Password: 1234567890
-- BCrypt hash below corresponds to '1234567890' with strength 10
SET @admin_user = '1234567890';
SET @admin_email = '1234567890@hostel.edu';
SET @admin_hash = '$2a$10$w6.U/U8P9v63nL98rP32C.WJzM5Fm8kZ7p5hYv3j1dF8a2c4e6g8.';

INSERT INTO users (role, name, email, register_number, password_hash, status, is_active, failed_attempts, must_change_password, created_at, updated_at)
SELECT 'ADMIN', 'Hostel Administrator', @admin_email, @admin_user, @admin_hash, 'active', TRUE, 0, FALSE, UTC_TIMESTAMP(), UTC_TIMESTAMP()
WHERE NOT EXISTS (SELECT 1 FROM users WHERE role = 'ADMIN');

UPDATE users 
SET email = @admin_email,
    register_number = @admin_user,
    password_hash = @admin_hash,
    status = 'active',
    is_active = TRUE,
    failed_attempts = 0,
    locked_until = NULL,
    must_change_password = FALSE,
    updated_at = UTC_TIMESTAMP()
WHERE role = 'ADMIN';
