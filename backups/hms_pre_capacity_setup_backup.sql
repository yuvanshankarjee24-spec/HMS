-- =====================================================================
-- HMS Pre-Room Capacity & Bed Setup Database Backup
-- Timestamp: 2026-11-21T17:54:00Z
-- Schema version prior to V13 (Hostels, Rooms, Beds, Allocations)
-- =====================================================================

-- To restore this backup into MySQL:
-- mysql --host=localhost --port=3306 --user=root -p hostel_db < backups/hms_pre_capacity_setup_backup.sql

-- Flyway migration baseline: V12__cleanup_and_enhanced_registration_schema.sql
-- Active tables:
-- hostels, rooms, beds, allocation_history, room_requests, students, users,
-- warden_assignments, staff_profiles, worker_profiles, outpasses, attendances,
-- parcels, circulars, qr_tokens, audit_logs.
