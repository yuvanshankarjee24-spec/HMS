-- =====================================================================
-- Smart Hostel Management System - Consolidated DB Verification Script
-- Purpose: Run before & after V10/V11 migrations to confirm zero data loss,
-- zero orphan foreign keys, consistent lowercase statuses, and full sync.
-- =====================================================================

-- 1. Row Count Summary (Core + All Modules + Shared Tables)
SELECT 'users' AS table_name, COUNT(*) AS row_count FROM users
UNION ALL SELECT 'hostels', COUNT(*) FROM hostels
UNION ALL SELECT 'warden_assignments', COUNT(*) FROM warden_assignments
UNION ALL SELECT 'rooms', COUNT(*) FROM rooms
UNION ALL SELECT 'beds', COUNT(*) FROM beds
UNION ALL SELECT 'students', COUNT(*) FROM students
UNION ALL SELECT 'staff_profiles', COUNT(*) FROM staff_profiles
UNION ALL SELECT 'worker_profiles', COUNT(*) FROM worker_profiles
UNION ALL SELECT 'outpasses', COUNT(*) FROM outpasses
UNION ALL SELECT 'outpass_scan_logs', COUNT(*) FROM outpass_scan_logs
UNION ALL SELECT 'outpass_locks', COUNT(*) FROM outpass_locks
UNION ALL SELECT 'attendance_sessions', COUNT(*) FROM attendance_sessions
UNION ALL SELECT 'attendance_records', COUNT(*) FROM attendance_records
UNION ALL SELECT 'maintenance_requests', COUNT(*) FROM maintenance_requests
UNION ALL SELECT 'request_attachments', COUNT(*) FROM request_attachments
UNION ALL SELECT 'parcels', COUNT(*) FROM parcels
UNION ALL SELECT 'parcel_qr_tokens', COUNT(*) FROM parcel_qr_tokens
UNION ALL SELECT 'parcel_attachments', COUNT(*) FROM parcel_attachments
UNION ALL SELECT 'circulars', COUNT(*) FROM circulars
UNION ALL SELECT 'circular_attachments', COUNT(*) FROM circular_attachments
UNION ALL SELECT 'circular_targets', COUNT(*) FROM circular_targets
UNION ALL SELECT 'circular_reads', COUNT(*) FROM circular_reads
UNION ALL SELECT 'event_rsvps', COUNT(*) FROM event_rsvps
UNION ALL SELECT 'food_items', COUNT(*) FROM food_items
UNION ALL SELECT 'mess_tokens', COUNT(*) FROM mess_tokens
UNION ALL SELECT 'token_qr', COUNT(*) FROM token_qr
UNION ALL SELECT 'file_attachments', COUNT(*) FROM file_attachments
UNION ALL SELECT 'qr_tokens', COUNT(*) FROM qr_tokens
UNION ALL SELECT 'notifications', COUNT(*) FROM notifications
UNION ALL SELECT 'audit_logs', COUNT(*) FROM audit_logs;

-- 2. Backfill Completeness Checks (expected 0 discrepancies)
SELECT
    (SELECT COUNT(*) FROM request_attachments)
  + (SELECT COUNT(*) FROM parcel_attachments)
  + (SELECT COUNT(*) FROM circular_attachments) AS legacy_attachment_total,
    (SELECT COUNT(*) FROM file_attachments) AS unified_attachment_total;

SELECT
    (SELECT COUNT(*) FROM parcel_qr_tokens)
  + (SELECT COUNT(*) FROM token_qr) AS legacy_qr_total,
    (SELECT COUNT(*) FROM qr_tokens) AS unified_qr_total;

-- 3. Orphan Foreign-Key Integrity Checks (every count MUST equal 0)
SELECT 'students_without_user' AS check_name, COUNT(*) AS orphan_count
FROM students s LEFT JOIN users u ON s.user_id = u.id WHERE u.id IS NULL
UNION ALL
SELECT 'students_without_hostel', COUNT(*)
FROM students s WHERE s.hostel_id IS NULL
UNION ALL
SELECT 'students_bed_room_mismatch', COUNT(*)
FROM beds b JOIN students s ON b.student_id = s.id WHERE s.room_id IS NULL OR s.room_id <> b.room_id
UNION ALL
SELECT 'rooms_without_hostel', COUNT(*)
FROM rooms r WHERE r.hostel_id IS NULL
UNION ALL
SELECT 'outpasses_orphan_student', COUNT(*)
FROM outpasses o LEFT JOIN students s ON o.student_id = s.id WHERE s.id IS NULL
UNION ALL
SELECT 'outpass_scan_logs_orphan_outpass', COUNT(*)
FROM outpass_scan_logs l LEFT JOIN outpasses o ON l.outpass_id = o.id WHERE o.id IS NULL
UNION ALL
SELECT 'outpass_locks_orphan_student', COUNT(*)
FROM outpass_locks l LEFT JOIN students s ON l.student_id = s.id WHERE s.id IS NULL
UNION ALL
SELECT 'attendance_records_orphan_student', COUNT(*)
FROM attendance_records ar LEFT JOIN students s ON ar.student_id = s.id WHERE s.id IS NULL
UNION ALL
SELECT 'maintenance_orphan_student', COUNT(*)
FROM maintenance_requests m LEFT JOIN students s ON m.student_id = s.id WHERE s.id IS NULL
UNION ALL
SELECT 'parcels_orphan_student', COUNT(*)
FROM parcels p LEFT JOIN students s ON p.student_id = s.id WHERE s.id IS NULL
UNION ALL
SELECT 'mess_tokens_orphan_student', COUNT(*)
FROM mess_tokens mt LEFT JOIN students s ON mt.student_id = s.id WHERE s.id IS NULL
UNION ALL
SELECT 'notifications_orphan_user', COUNT(*)
FROM notifications n LEFT JOIN users u ON n.user_id = u.id WHERE u.id IS NULL;

-- 4. Standardized Status Value Verification (every count MUST equal 0)
SELECT 'users_uppercase_status' AS check_name, COUNT(*) AS bad_rows
FROM users WHERE status <> LOWER(status)
UNION ALL SELECT 'rooms_uppercase_status', COUNT(*) FROM rooms WHERE status <> LOWER(status)
UNION ALL SELECT 'room_requests_uppercase_status', COUNT(*) FROM room_requests WHERE status <> LOWER(status)
UNION ALL SELECT 'outpasses_uppercase_status', COUNT(*) FROM outpasses WHERE status <> LOWER(status)
UNION ALL SELECT 'attendance_records_uppercase_status', COUNT(*) FROM attendance_records WHERE status <> LOWER(status)
UNION ALL SELECT 'maintenance_requests_uppercase_status', COUNT(*) FROM maintenance_requests WHERE status <> LOWER(status)
UNION ALL SELECT 'parcels_uppercase_status', COUNT(*) FROM parcels WHERE status <> LOWER(status)
UNION ALL SELECT 'circulars_uppercase_status', COUNT(*) FROM circulars WHERE status <> LOWER(status)
UNION ALL SELECT 'mess_tokens_uppercase_status', COUNT(*) FROM mess_tokens WHERE status <> LOWER(status);

