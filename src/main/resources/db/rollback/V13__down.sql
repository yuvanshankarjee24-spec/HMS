-- V13__down.sql
-- Rollback for V13__room_and_bed_capacity_400.sql

DROP TABLE IF EXISTS room_allocations;

-- Remove extra beds created for 100 rooms
DELETE FROM beds WHERE bed_number > 4 OR room_id IN (
    SELECT id FROM rooms WHERE CAST(room_number AS SIGNED) BETWEEN 105 AND 200
);

-- Remove rooms 105 to 200
DELETE FROM rooms WHERE CAST(room_number AS SIGNED) BETWEEN 105 AND 200;

ALTER TABLE beds DROP COLUMN IF EXISTS bed_label;
ALTER TABLE beds DROP COLUMN IF EXISTS status;
ALTER TABLE rooms DROP COLUMN IF EXISTS is_active;
