-- V10__down.sql (Rollback V10__core_shared_schema.sql cleanly without data loss)

DROP VIEW IF EXISTS student_profiles;

ALTER TABLE worker_profiles DROP COLUMN deleted_at;

ALTER TABLE students
    DROP FOREIGN KEY fk_students_unlocked_by,
    DROP FOREIGN KEY fk_students_room,
    DROP FOREIGN KEY fk_students_hostel,
    DROP COLUMN deleted_at,
    DROP COLUMN is_active,
    DROP COLUMN guardian_relation,
    DROP COLUMN guardian_phone,
    DROP COLUMN guardian_name,
    DROP COLUMN room_id,
    DROP COLUMN hostel_id;

ALTER TABLE users
    DROP COLUMN deleted_at,
    DROP COLUMN is_active,
    DROP COLUMN phone,
    DROP COLUMN name;

DROP TABLE IF EXISTS warden_assignments;

ALTER TABLE rooms
    DROP FOREIGN KEY fk_rooms_hostel,
    DROP COLUMN hostel_id;

DROP TABLE IF EXISTS hostels;

