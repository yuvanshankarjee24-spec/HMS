-- V11__link_modules_integrity.sql
-- Link all modules via Core FKs, shared Notifications/Audit/Attachments/QR tables, CHECK constraints & indexes

-- 1. Outpass Scan Logs & Dedicated Outpass Locks
CREATE TABLE IF NOT EXISTS outpass_scan_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    outpass_id BIGINT NOT NULL,
    scanned_by_user_id BIGINT NOT NULL,
    scan_action VARCHAR(30) NOT NULL,
    scan_result VARCHAR(50) NOT NULL,
    notes VARCHAR(500) NULL,
    scanned_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_osl_outpass FOREIGN KEY (outpass_id) REFERENCES outpasses(id) ON DELETE RESTRICT,
    CONSTRAINT fk_osl_scanned_by FOREIGN KEY (scanned_by_user_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT chk_osl_action CHECK (scan_action IN ('exit', 'entry', 'denied')),
    INDEX idx_osl_outpass (outpass_id),
    INDEX idx_osl_scanned_by (scanned_by_user_id),
    INDEX idx_osl_scanned_at (scanned_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO outpass_scan_logs (outpass_id, scanned_by_user_id, scan_action, scan_result, notes, scanned_at)
SELECT o.id,
       COALESCE((SELECT u.id FROM users u WHERE u.role = 'SECURITY' ORDER BY u.id LIMIT 1), 1),
       'exit', 'ALLOWED', 'Backfilled historical exit scan', o.actual_exit_time
FROM outpasses o
WHERE o.actual_exit_time IS NOT NULL;

CREATE TABLE IF NOT EXISTS outpass_locks (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    student_id BIGINT NOT NULL,
    outpass_id BIGINT NULL,
    attendance_session_id BIGINT NULL,
    attendance_date DATE NULL,
    reason VARCHAR(500) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    locked_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    unlocked_by_user_id BIGINT NULL,
    unlocked_at DATETIME NULL,
    unlock_remarks VARCHAR(500) NULL,
    CONSTRAINT fk_ol_student FOREIGN KEY (student_id) REFERENCES students(id) ON DELETE RESTRICT,
    CONSTRAINT fk_ol_outpass FOREIGN KEY (outpass_id) REFERENCES outpasses(id) ON DELETE RESTRICT,
    CONSTRAINT fk_ol_session FOREIGN KEY (attendance_session_id) REFERENCES attendance_sessions(id) ON DELETE RESTRICT,
    CONSTRAINT fk_ol_unlocked_by FOREIGN KEY (unlocked_by_user_id) REFERENCES users(id) ON DELETE RESTRICT,
    INDEX idx_ol_student_active (student_id, is_active),
    INDEX idx_ol_locked_at (locked_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO outpass_locks (student_id, reason, is_active, locked_at, unlocked_by_user_id, unlocked_at, unlock_remarks)
SELECT s.id, COALESCE(s.lock_reason, 'Missed attendance lock'), s.is_locked, COALESCE(s.locked_at, CURRENT_TIMESTAMP),
       s.unlocked_by_user_id, s.unlocked_at, s.unlock_remarks
FROM students s
WHERE s.is_locked = TRUE OR s.locked_at IS NOT NULL;

-- 2. Link module tables to Core hostels(id) and rooms(id)
ALTER TABLE attendance_sessions ADD COLUMN hostel_id BIGINT NULL;
UPDATE attendance_sessions SET hostel_id = (
    SELECT h.id FROM hostels h WHERE LOWER(h.name) = LOWER(attendance_sessions.hostel_block) LIMIT 1
);
ALTER TABLE attendance_sessions ADD CONSTRAINT fk_att_sess_hostel FOREIGN KEY (hostel_id) REFERENCES hostels(id) ON DELETE RESTRICT;
CREATE INDEX idx_att_sess_hostel ON attendance_sessions (hostel_id);

ALTER TABLE maintenance_requests ADD COLUMN hostel_id BIGINT NULL;
ALTER TABLE maintenance_requests ADD COLUMN room_id BIGINT NULL;
UPDATE maintenance_requests SET
    hostel_id = COALESCE(
        (SELECT h.id FROM hostels h WHERE LOWER(h.name) = LOWER(maintenance_requests.hostel_block) LIMIT 1),
        (SELECT s.hostel_id FROM students s WHERE s.id = maintenance_requests.student_id)
    ),
    room_id = COALESCE(
        (SELECT r.id FROM rooms r WHERE r.room_number = maintenance_requests.room_number LIMIT 1),
        (SELECT s.room_id FROM students s WHERE s.id = maintenance_requests.student_id)
    );
ALTER TABLE maintenance_requests ADD CONSTRAINT fk_maint_hostel FOREIGN KEY (hostel_id) REFERENCES hostels(id) ON DELETE RESTRICT;
ALTER TABLE maintenance_requests ADD CONSTRAINT fk_maint_room FOREIGN KEY (room_id) REFERENCES rooms(id) ON DELETE RESTRICT;
CREATE INDEX idx_maint_hostel ON maintenance_requests (hostel_id);
CREATE INDEX idx_maint_room ON maintenance_requests (room_id);

ALTER TABLE parcels ADD COLUMN hostel_id BIGINT NULL;
UPDATE parcels SET hostel_id = COALESCE(
    (SELECT h.id FROM hostels h WHERE LOWER(h.name) = LOWER(parcels.hostel_block) LIMIT 1),
    (SELECT s.hostel_id FROM students s WHERE s.id = parcels.student_id)
);
ALTER TABLE parcels ADD CONSTRAINT fk_prc_hostel FOREIGN KEY (hostel_id) REFERENCES hostels(id) ON DELETE RESTRICT;
CREATE INDEX idx_prc_hostel ON parcels (hostel_id);
CREATE INDEX idx_prc_received_at ON parcels (received_at);

ALTER TABLE circulars ADD COLUMN hostel_id BIGINT NULL;
UPDATE circulars SET hostel_id = (
    SELECT h.id FROM hostels h WHERE LOWER(h.name) = LOWER(circulars.hostel_block) LIMIT 1
);
ALTER TABLE circulars ADD CONSTRAINT fk_circ_hostel FOREIGN KEY (hostel_id) REFERENCES hostels(id) ON DELETE RESTRICT;
CREATE INDEX idx_circ_hostel ON circulars (hostel_id);

ALTER TABLE circular_targets ADD COLUMN hostel_id BIGINT NULL;
UPDATE circular_targets SET hostel_id = (
    SELECT h.id FROM hostels h WHERE LOWER(h.name) = LOWER(circular_targets.hostel_block) LIMIT 1
);
ALTER TABLE circular_targets ADD CONSTRAINT fk_circ_tgt_hostel FOREIGN KEY (hostel_id) REFERENCES hostels(id) ON DELETE RESTRICT;

ALTER TABLE mess_tokens ADD COLUMN hostel_id BIGINT NULL;
UPDATE mess_tokens SET hostel_id = COALESCE(
    (SELECT h.id FROM hostels h WHERE LOWER(h.name) = LOWER(mess_tokens.hostel_block) LIMIT 1),
    (SELECT s.hostel_id FROM students s WHERE s.id = mess_tokens.student_id)
);
ALTER TABLE mess_tokens ADD CONSTRAINT fk_mess_tkn_hostel FOREIGN KEY (hostel_id) REFERENCES hostels(id) ON DELETE RESTRICT;
CREATE INDEX idx_mess_tkn_hostel ON mess_tokens (hostel_id);
CREATE INDEX idx_mess_tkn_created ON mess_tokens (created_at);
CREATE INDEX idx_outpasses_return_dt ON outpasses (return_date_time);

-- 3. Extend Shared Notifications & Audit Logs
ALTER TABLE notifications ADD COLUMN type VARCHAR(50) NULL;
ALTER TABLE notifications ADD COLUMN module VARCHAR(50) NULL;
ALTER TABLE notifications ADD COLUMN link VARCHAR(255) NULL;
UPDATE notifications SET
    type = LOWER(COALESCE(reference_type, 'general')),
    module = UPPER(COALESCE(reference_type, 'SYSTEM'));
CREATE INDEX idx_notif_mod_ref ON notifications (module, reference_id);

ALTER TABLE audit_logs ADD COLUMN module VARCHAR(50) NULL;
ALTER TABLE audit_logs ADD COLUMN reference_id BIGINT NULL;
ALTER TABLE audit_logs ADD COLUMN old_value VARCHAR(500) NULL;
ALTER TABLE audit_logs ADD COLUMN new_value VARCHAR(500) NULL;
CREATE INDEX idx_audit_mod_ref ON audit_logs (module, reference_id);

-- 4. Shared File Attachments & Shared QR Tokens tables (with lossless backfill from legacy tables)
CREATE TABLE IF NOT EXISTS file_attachments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    module VARCHAR(40) NOT NULL,
    entity_id BIGINT NOT NULL,
    file_path VARCHAR(500) NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    file_size BIGINT NOT NULL,
    attachment_type VARCHAR(30) NULL,
    uploaded_by_user_id BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_fatt_uploaded_by FOREIGN KEY (uploaded_by_user_id) REFERENCES users(id) ON DELETE RESTRICT,
    INDEX idx_fatt_mod_ent (module, entity_id),
    INDEX idx_fatt_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO file_attachments (module, entity_id, file_path, original_filename, content_type, file_size, attachment_type, uploaded_by_user_id, created_at)
SELECT 'MAINTENANCE', ra.request_id, ra.file_path, ra.original_filename, ra.content_type, ra.file_size, ra.attachment_type, ra.uploaded_by_user_id, ra.created_at
FROM request_attachments ra;

INSERT INTO file_attachments (module, entity_id, file_path, original_filename, content_type, file_size, attachment_type, uploaded_by_user_id, created_at)
SELECT 'PARCEL', pa.parcel_id, pa.file_path, pa.original_filename, pa.content_type, pa.file_size, 'PARCEL_PHOTO', pa.uploaded_by_user_id, pa.created_at
FROM parcel_attachments pa;

INSERT INTO file_attachments (module, entity_id, file_path, original_filename, content_type, file_size, attachment_type, uploaded_by_user_id, created_at)
SELECT 'CIRCULAR', ca.circular_id, ca.file_path, ca.original_filename, ca.content_type, ca.file_size, 'CIRCULAR_ATTACHMENT', ca.uploaded_by_user_id, ca.created_at
FROM circular_attachments ca;

CREATE TABLE IF NOT EXISTS qr_tokens (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    module VARCHAR(30) NOT NULL,
    entity_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    qr_token VARCHAR(255) NOT NULL UNIQUE,
    pickup_code VARCHAR(12) NOT NULL UNIQUE,
    used BOOLEAN NOT NULL DEFAULT FALSE,
    closed BOOLEAN NOT NULL DEFAULT FALSE,
    used_at DATETIME NULL,
    expires_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_qrt_mod_ent UNIQUE (module, entity_id),
    CONSTRAINT fk_qrt_student FOREIGN KEY (student_id) REFERENCES students(id) ON DELETE RESTRICT,
    INDEX idx_qrt_token (qr_token),
    INDEX idx_qrt_pickup (pickup_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO qr_tokens (module, entity_id, student_id, qr_token, pickup_code, used, closed, used_at, expires_at, created_at)
SELECT 'PARCEL', pqt.parcel_id, pqt.student_id, pqt.qr_token, pqt.pickup_code, pqt.used, FALSE, pqt.used_at, pqt.expires_at, pqt.created_at
FROM parcel_qr_tokens pqt;

INSERT INTO qr_tokens (module, entity_id, student_id, qr_token, pickup_code, used, closed, used_at, expires_at, created_at)
SELECT 'MESS', tq.token_id, tq.student_id, tq.qr_token, tq.pickup_code, tq.used, tq.closed, tq.used_at, NULL, tq.created_at
FROM token_qr tq;

-- 5. Normalize all status columns to lowercase_snake_case + enforce CHECK constraints
ALTER TABLE users MODIFY COLUMN status VARCHAR(30) NOT NULL DEFAULT 'active';
UPDATE users SET status = LOWER(status);
ALTER TABLE users ADD CONSTRAINT chk_users_status CHECK (status IN ('active', 'locked', 'disabled'));

ALTER TABLE rooms MODIFY COLUMN status VARCHAR(30) NOT NULL DEFAULT 'active';
UPDATE rooms SET status = LOWER(status);
ALTER TABLE rooms ADD CONSTRAINT chk_rooms_status CHECK (status IN ('active', 'maintenance'));

ALTER TABLE room_requests MODIFY COLUMN status VARCHAR(30) NOT NULL DEFAULT 'pending';
UPDATE room_requests SET status = LOWER(status);
ALTER TABLE room_requests ADD CONSTRAINT chk_room_req_status CHECK (status IN ('pending', 'approved', 'rejected', 'cancelled'));

UPDATE outpasses SET status = LOWER(status);
ALTER TABLE outpasses ALTER COLUMN status SET DEFAULT 'pending';
ALTER TABLE outpasses ADD CONSTRAINT chk_outpasses_status CHECK (status IN ('pending', 'approved', 'out', 'returned', 'rejected', 'expired'));

UPDATE attendances SET status = LOWER(status);
ALTER TABLE attendances ALTER COLUMN status SET DEFAULT 'present';
ALTER TABLE attendances ADD CONSTRAINT chk_att_status CHECK (status IN ('present', 'absent', 'on_outpass', 'late'));

UPDATE attendance_records SET status = LOWER(status);
ALTER TABLE attendance_records ALTER COLUMN status SET DEFAULT 'pending';
ALTER TABLE attendance_records ADD CONSTRAINT chk_da_rec_status CHECK (status IN ('pending', 'marked', 'missed', 'on_outpass', 'manual_present'));

UPDATE maintenance_requests SET status = LOWER(status);
ALTER TABLE maintenance_requests ALTER COLUMN status SET DEFAULT 'pending';
ALTER TABLE maintenance_requests ADD CONSTRAINT chk_maint_status CHECK (status IN ('pending', 'assigned', 'in_progress', 'on_hold', 'completed', 'resolved', 'reopened', 'rejected', 'cancelled'));

UPDATE parcels SET status = LOWER(status);
ALTER TABLE parcels ALTER COLUMN status SET DEFAULT 'received';
ALTER TABLE parcels ADD CONSTRAINT chk_parcels_status CHECK (status IN ('received', 'notified', 'delivered', 'returned_to_sender', 'unclaimed', 'cancelled'));

UPDATE circulars SET status = LOWER(status);
ALTER TABLE circulars ALTER COLUMN status SET DEFAULT 'draft';
ALTER TABLE circulars ADD CONSTRAINT chk_circulars_status CHECK (status IN ('draft', 'scheduled', 'published', 'archived'));

UPDATE mess_tokens SET status = LOWER(status);
ALTER TABLE mess_tokens ALTER COLUMN status SET DEFAULT 'booked';
ALTER TABLE mess_tokens ADD CONSTRAINT chk_mess_tokens_status CHECK (status IN ('booked', 'qr_active', 'delivered', 'partially_delivered', 'cancelled', 'expired'));

