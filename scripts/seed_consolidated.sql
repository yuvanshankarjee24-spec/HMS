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
