-- =====================================================================
-- Verification Script: Room and Bed Capacity (100 Rooms, 400 Beds)
-- Checks:
-- 1. Count of rooms = 100
-- 2. Count of beds = 400
-- 3. No room over 4 students
-- 4. No student with two active allocations
-- 5. No bed used twice
-- 6. No orphan allocations
-- =====================================================================

-- 1. Total Rooms Count Check (Expected: 100)
SELECT
    COUNT(*) AS total_rooms,
    CASE WHEN COUNT(*) = 100 THEN 'PASSED' ELSE 'FAILED' END AS check_100_rooms
FROM rooms
WHERE CAST(room_number AS SIGNED) BETWEEN 101 AND 200;

-- 2. Total Beds Count Check (Expected: 400)
SELECT
    COUNT(*) AS total_beds,
    CASE WHEN COUNT(*) = 400 THEN 'PASSED' ELSE 'FAILED' END AS check_400_beds
FROM beds b
JOIN rooms r ON b.room_id = r.id
WHERE CAST(r.room_number AS SIGNED) BETWEEN 101 AND 200;

-- 3. No Room Over 4 Students (Max occupied <= 4)
SELECT
    r.room_number,
    COUNT(b.student_id) AS occupied_students,
    CASE WHEN COUNT(b.student_id) <= 4 THEN 'PASSED' ELSE 'VIOLATION' END AS capacity_check
FROM rooms r
LEFT JOIN beds b ON r.id = b.room_id AND b.student_id IS NOT NULL
GROUP BY r.id, r.room_number
HAVING COUNT(b.student_id) > 4;

-- 4. No Student with Two Active Allocations
SELECT
    ra.student_id,
    COUNT(*) AS active_allocations_count,
    'VIOLATION: Student has multiple active allocations' AS error_description
FROM room_allocations ra
WHERE ra.status = 'active'
GROUP BY ra.student_id
HAVING COUNT(*) > 1;

-- 5. No Bed Used Twice
SELECT
    b.id AS bed_id,
    COUNT(DISTINCT ra.student_id) AS active_students_in_bed,
    'VIOLATION: Multiple active students assigned to same bed' AS error_description
FROM beds b
JOIN room_allocations ra ON b.id = ra.bed_id AND ra.status = 'active'
GROUP BY b.id
HAVING COUNT(DISTINCT ra.student_id) > 1;

-- 6. No Orphan Allocations (Allocation exists but student or room does not)
SELECT
    ra.id AS orphan_allocation_id,
    ra.student_id,
    ra.room_id,
    ra.bed_id
FROM room_allocations ra
LEFT JOIN students s ON ra.student_id = s.id
LEFT JOIN rooms r ON ra.room_id = r.id
LEFT JOIN beds b ON ra.bed_id = b.id
WHERE s.id IS NULL OR r.id IS NULL OR b.id IS NULL;
