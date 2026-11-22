-- V14__enhanced_room_requests.sql
-- Enhances room_requests table with request_code, preferred_room_type, preferred_roommates, allotted_room_id, allotted_bed_id
-- Creates room_request_status_history table for auditing lifecycle transitions

ALTER TABLE room_requests ADD COLUMN IF NOT EXISTS request_code VARCHAR(50) NULL;
ALTER TABLE room_requests ADD COLUMN IF NOT EXISTS preferred_room_type VARCHAR(50) NULL;
ALTER TABLE room_requests ADD COLUMN IF NOT EXISTS preferred_roommates VARCHAR(255) NULL;
ALTER TABLE room_requests ADD COLUMN IF NOT EXISTS allotted_room_id BIGINT NULL;
ALTER TABLE room_requests ADD COLUMN IF NOT EXISTS allotted_bed_id BIGINT NULL;

CREATE INDEX idx_rr_request_code ON room_requests (request_code);

CREATE TABLE IF NOT EXISTS room_request_status_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    request_id BIGINT NOT NULL,
    old_status VARCHAR(30) NULL,
    new_status VARCHAR(30) NOT NULL,
    changed_by_user_id BIGINT NULL,
    changed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    remarks TEXT NULL,
    FOREIGN KEY (request_id) REFERENCES room_requests(id) ON DELETE CASCADE,
    FOREIGN KEY (changed_by_user_id) REFERENCES users(id) ON DELETE SET NULL,
    INDEX idx_rr_history_req (request_id)
);
