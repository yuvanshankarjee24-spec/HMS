-- V8__events_and_circulars.sql
-- Part A: Events and Circulars (Circulars, Attachments, Targets, Reads/Acks, Versions, RSVPs, Event Reminders)

CREATE TABLE IF NOT EXISTS circulars (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    circular_code VARCHAR(32) NOT NULL UNIQUE,
    type VARCHAR(40) NOT NULL,
    title VARCHAR(200) NOT NULL,
    description TEXT NOT NULL,
    rules_and_regulations TEXT NULL,
    event_date_time DATETIME NULL,
    venue VARCHAR(200) NULL,
    priority VARCHAR(30) NOT NULL DEFAULT 'NORMAL',
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    require_acknowledgement BOOLEAN NOT NULL DEFAULT FALSE,
    target_scope VARCHAR(40) NOT NULL DEFAULT 'ALL',
    hostel_block VARCHAR(100) NULL,
    scheduled_publish_at DATETIME NULL,
    expires_at DATETIME NULL,
    published_at DATETIME NULL,
    version INT NOT NULL DEFAULT 1,
    updated_flag BOOLEAN NOT NULL DEFAULT FALSE,
    created_by_user_id BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (created_by_user_id) REFERENCES users(id) ON DELETE SET NULL,
    INDEX idx_circ_status (status),
    INDEX idx_circ_type (type),
    INDEX idx_circ_priority (priority),
    INDEX idx_circ_created (created_at),
    INDEX idx_circ_sched (scheduled_publish_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS circular_attachments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    circular_id BIGINT NOT NULL,
    file_path VARCHAR(500) NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    file_size BIGINT NOT NULL,
    uploaded_by_user_id BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (circular_id) REFERENCES circulars(id) ON DELETE CASCADE,
    FOREIGN KEY (uploaded_by_user_id) REFERENCES users(id) ON DELETE SET NULL,
    INDEX idx_circ_att_cid (circular_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS circular_targets (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    circular_id BIGINT NOT NULL,
    hostel_block VARCHAR(100) NULL,
    department VARCHAR(100) NULL,
    study_year INT NULL,
    student_id BIGINT NULL,
    FOREIGN KEY (circular_id) REFERENCES circulars(id) ON DELETE CASCADE,
    FOREIGN KEY (student_id) REFERENCES students(id) ON DELETE CASCADE,
    INDEX idx_circ_tgt_cid (circular_id),
    INDEX idx_circ_tgt_stud (student_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS circular_reads (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    circular_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    read_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    acknowledged_at DATETIME NULL,
    reminder_24h_sent BOOLEAN NOT NULL DEFAULT FALSE,
    FOREIGN KEY (circular_id) REFERENCES circulars(id) ON DELETE CASCADE,
    FOREIGN KEY (student_id) REFERENCES students(id) ON DELETE CASCADE,
    UNIQUE KEY uk_circ_read_stud (circular_id, student_id),
    INDEX idx_circ_read_cid (circular_id),
    INDEX idx_circ_read_sid (student_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS circular_versions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    circular_id BIGINT NOT NULL,
    version_number INT NOT NULL,
    title VARCHAR(200) NOT NULL,
    description TEXT NOT NULL,
    rules_and_regulations TEXT NULL,
    edited_by_user_id BIGINT NULL,
    edit_note VARCHAR(500) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (circular_id) REFERENCES circulars(id) ON DELETE CASCADE,
    FOREIGN KEY (edited_by_user_id) REFERENCES users(id) ON DELETE SET NULL,
    INDEX idx_circ_ver_cid (circular_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS event_rsvps (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    circular_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    rsvp_status VARCHAR(30) NOT NULL,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (circular_id) REFERENCES circulars(id) ON DELETE CASCADE,
    FOREIGN KEY (student_id) REFERENCES students(id) ON DELETE CASCADE,
    UNIQUE KEY uk_circ_rsvp_stud (circular_id, student_id),
    INDEX idx_circ_rsvp_cid (circular_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS circular_event_reminders (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    circular_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    reminder_type VARCHAR(30) NOT NULL,
    sent_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (circular_id) REFERENCES circulars(id) ON DELETE CASCADE,
    FOREIGN KEY (student_id) REFERENCES students(id) ON DELETE CASCADE,
    UNIQUE KEY uk_circ_ev_rem (circular_id, student_id, reminder_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Seed sample circular
INSERT INTO circulars (id, circular_code, type, title, description, rules_and_regulations, priority, status, require_acknowledgement, target_scope, hostel_block, published_at, version, updated_flag, created_by_user_id) VALUES
(1, 'CIR-2026-0001', 'REGULATION', 'Hostel Quiet Hours & Biometric Roll Call Notice', 'All hostel residents are reminded to maintain silence in study corridors after 10:00 PM and complete digital attendance before the nightly deadline.', '1. Quiet hours begin strictly at 10:00 PM.\n2. Visitors are not allowed inside residential blocks after 8:00 PM.\n3. Ensure digital attendance is marked before 10:00 PM to keep outpass privileges active.', 'IMPORTANT', 'PUBLISHED', TRUE, 'ALL', 'ALL', CURRENT_TIMESTAMP, 1, FALSE, 2);

INSERT INTO circular_versions (circular_id, version_number, title, description, rules_and_regulations, edited_by_user_id, edit_note) VALUES
(1, 1, 'Hostel Quiet Hours & Biometric Roll Call Notice', 'All hostel residents are reminded to maintain silence in study corridors after 10:00 PM and complete digital attendance before the nightly deadline.', '1. Quiet hours begin strictly at 10:00 PM.\n2. Visitors are not allowed inside residential blocks after 8:00 PM.\n3. Ensure digital attendance is marked before 10:00 PM to keep outpass privileges active.', 2, 'Initial published version');

