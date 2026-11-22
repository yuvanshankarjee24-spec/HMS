-- V14__down.sql
-- Rollback for V14__enhanced_room_requests.sql

DROP TABLE IF EXISTS room_request_status_history;

ALTER TABLE room_requests DROP COLUMN IF EXISTS allotted_bed_id;
ALTER TABLE room_requests DROP COLUMN IF EXISTS allotted_room_id;
ALTER TABLE room_requests DROP COLUMN IF EXISTS preferred_roommates;
ALTER TABLE room_requests DROP COLUMN IF EXISTS preferred_room_type;
ALTER TABLE room_requests DROP COLUMN IF EXISTS request_code;
