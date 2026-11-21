-- =====================================================================
-- Smart Hostel Management System - 100 Rooms & 400 Beds Seed Script
-- Creates PSG iTech Hostel, 100 Rooms (101 to 200), 400 Beds (A, B, C, D)
-- Idempotent: safe to run multiple times without duplicating data
-- =====================================================================

-- 1. Ensure PSG iTech Hostel exists
INSERT INTO hostels (code, name, address, is_active, created_at, updated_at)
SELECT 'PSG_ITECH', 'PSG iTech Hostel', 'PSG iTech Campus, Neelambur, Coimbatore', TRUE, UTC_TIMESTAMP(), UTC_TIMESTAMP()
WHERE NOT EXISTS (SELECT 1 FROM hostels WHERE code = 'PSG_ITECH' OR name = 'PSG iTech Hostel');

UPDATE hostels SET name = 'PSG iTech Hostel' WHERE code = 'MAIN' AND NOT EXISTS (SELECT 1 FROM hostels WHERE code = 'PSG_ITECH');

-- 2. Seed 100 Rooms (101 to 200), Capacity 4
INSERT INTO rooms (room_number, floor, capacity, status, is_active, hostel_id, created_at)
SELECT
    CAST(100 + (t.d * 10) + u.d + 1 AS VARCHAR(10)),
    CASE
        WHEN (t.d * 10 + u.d + 1) <= 25 THEN 1
        WHEN (t.d * 10 + u.d + 1) <= 50 THEN 2
        WHEN (t.d * 10 + u.d + 1) <= 75 THEN 3
        ELSE 4
    END,
    4,
    'active',
    TRUE,
    (SELECT h.id FROM hostels h WHERE h.code = 'PSG_ITECH' OR h.code = 'MAIN' ORDER BY h.id ASC LIMIT 1),
    UTC_TIMESTAMP()
FROM (SELECT 0 AS d UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
      UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) t
CROSS JOIN (SELECT 0 AS d UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
            UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) u
WHERE NOT EXISTS (
    SELECT 1 FROM rooms r WHERE r.room_number = CAST(100 + (t.d * 10) + u.d + 1 AS VARCHAR(10))
);

UPDATE rooms SET
    capacity = 4,
    status = 'active',
    is_active = TRUE,
    hostel_id = COALESCE(hostel_id, (SELECT h.id FROM hostels h WHERE h.code = 'PSG_ITECH' OR h.code = 'MAIN' ORDER BY h.id ASC LIMIT 1))
WHERE CAST(room_number AS SIGNED) BETWEEN 101 AND 200;

-- 3. Seed 4 Beds (A, B, C, D) per Room = 400 Beds
INSERT INTO beds (room_id, bed_number, bed_label, status, created_at)
SELECT
    r.id,
    bdef.num,
    bdef.lbl,
    'available',
    UTC_TIMESTAMP()
FROM rooms r
CROSS JOIN (
    SELECT 1 AS num, 'A' AS lbl UNION ALL
    SELECT 2 AS num, 'B' AS lbl UNION ALL
    SELECT 3 AS num, 'C' AS lbl UNION ALL
    SELECT 4 AS num, 'D' AS lbl
) bdef
WHERE CAST(r.room_number AS SIGNED) BETWEEN 101 AND 200
  AND NOT EXISTS (
      SELECT 1 FROM beds b WHERE b.room_id = r.id AND (b.bed_label = bdef.lbl OR b.bed_number = bdef.num)
  );

UPDATE beds SET
    bed_label = CASE bed_number
        WHEN 1 THEN 'A'
        WHEN 2 THEN 'B'
        WHEN 3 THEN 'C'
        WHEN 4 THEN 'D'
        ELSE CONCAT('B', bed_number)
    END,
    status = CASE WHEN student_id IS NOT NULL THEN 'occupied' ELSE 'available' END
WHERE bed_label IS NULL OR status IS NULL OR status = '';
