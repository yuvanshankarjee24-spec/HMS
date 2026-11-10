-- V5__maintenance_requests.sql
-- Maintenance Requests, Worker Profiles, Attachments, Status History, Ratings & Notifications

CREATE TABLE IF NOT EXISTS worker_profiles (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE,
    employee_id VARCHAR(50) NOT NULL UNIQUE,
    full_name VARCHAR(150) NOT NULL,
    phone VARCHAR(20) NOT NULL,
    worker_type VARCHAR(50) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_worker_type (worker_type),
    INDEX idx_worker_active (active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS maintenance_requests (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    request_code VARCHAR(32) NOT NULL UNIQUE,
    student_id BIGINT NOT NULL,
    category VARCHAR(50) NOT NULL,
    title VARCHAR(150) NOT NULL,
    description TEXT NOT NULL,
    hostel_block VARCHAR(100) NOT NULL,
    room_number VARCHAR(50) NOT NULL,
    priority VARCHAR(30) NOT NULL DEFAULT 'MEDIUM',
    preferred_time_slot VARCHAR(100) NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    assigned_worker_id BIGINT NULL,
    warden_instruction VARCHAR(500) NULL,
    expected_completion_date DATE NULL,
    rejection_reason VARCHAR(500) NULL,
    on_hold_reason VARCHAR(500) NULL,
    worker_remarks VARCHAR(500) NULL,
    reopen_reason VARCHAR(500) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at DATETIME NULL,
    resolved_at DATETIME NULL,
    FOREIGN KEY (student_id) REFERENCES students(id) ON DELETE CASCADE,
    FOREIGN KEY (assigned_worker_id) REFERENCES worker_profiles(id) ON DELETE SET NULL,
    INDEX idx_maint_student (student_id),
    INDEX idx_maint_status (status),
    INDEX idx_maint_category (category),
    INDEX idx_maint_priority (priority),
    INDEX idx_maint_worker (assigned_worker_id),
    INDEX idx_maint_block_room (hostel_block, room_number),
    INDEX idx_maint_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS request_attachments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    request_id BIGINT NOT NULL,
    file_path VARCHAR(500) NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    file_size BIGINT NOT NULL,
    attachment_type VARCHAR(30) NOT NULL,
    uploaded_by_user_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (request_id) REFERENCES maintenance_requests(id) ON DELETE CASCADE,
    FOREIGN KEY (uploaded_by_user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_attach_request (request_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS request_status_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    request_id BIGINT NOT NULL,
    old_status VARCHAR(30) NULL,
    new_status VARCHAR(30) NOT NULL,
    changed_by_user_id BIGINT NOT NULL,
    remarks VARCHAR(500) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (request_id) REFERENCES maintenance_requests(id) ON DELETE CASCADE,
    FOREIGN KEY (changed_by_user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_req_history_req (request_id),
    INDEX idx_req_history_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS request_ratings (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    request_id BIGINT NOT NULL UNIQUE,
    student_id BIGINT NOT NULL,
    worker_id BIGINT NULL,
    rating INT NOT NULL,
    feedback VARCHAR(500) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (request_id) REFERENCES maintenance_requests(id) ON DELETE CASCADE,
    FOREIGN KEY (student_id) REFERENCES students(id) ON DELETE CASCADE,
    FOREIGN KEY (worker_id) REFERENCES worker_profiles(id) ON DELETE SET NULL,
    INDEX idx_rating_worker (worker_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS notifications (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    title VARCHAR(150) NOT NULL,
    message VARCHAR(500) NOT NULL,
    reference_type VARCHAR(50) NULL,
    reference_id BIGINT NULL,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_notif_user_read (user_id, is_read),
    INDEX idx_notif_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Seed Worker users (one for each skill type; password: Hostel@123)
INSERT INTO users (id, email, register_number, password_hash, role, status, must_change_password, failed_attempts) VALUES
(19, 'cleaner.worker@hostel.edu',     NULL, '$2a$10$DakqzPWIKvUUrXmrlcjDc.md7ijkoXVmVUFO2BBujSaBR.9.nWZdq', 'WORKER', 'ACTIVE', FALSE, 0),
(20, 'electrician.worker@hostel.edu', NULL, '$2a$10$DakqzPWIKvUUrXmrlcjDc.md7ijkoXVmVUFO2BBujSaBR.9.nWZdq', 'WORKER', 'ACTIVE', FALSE, 0),
(21, 'plumber.worker@hostel.edu',     NULL, '$2a$10$DakqzPWIKvUUrXmrlcjDc.md7ijkoXVmVUFO2BBujSaBR.9.nWZdq', 'WORKER', 'ACTIVE', FALSE, 0),
(22, 'carpenter.worker@hostel.edu',   NULL, '$2a$10$DakqzPWIKvUUrXmrlcjDc.md7ijkoXVmVUFO2BBujSaBR.9.nWZdq', 'WORKER', 'ACTIVE', FALSE, 0),
(23, 'wifi.worker@hostel.edu',        NULL, '$2a$10$DakqzPWIKvUUrXmrlcjDc.md7ijkoXVmVUFO2BBujSaBR.9.nWZdq', 'WORKER', 'ACTIVE', FALSE, 0),
(24, 'general.worker@hostel.edu',     NULL, '$2a$10$DakqzPWIKvUUrXmrlcjDc.md7ijkoXVmVUFO2BBujSaBR.9.nWZdq', 'WORKER', 'ACTIVE', FALSE, 0);

INSERT INTO worker_profiles (id, user_id, employee_id, full_name, phone, worker_type, active) VALUES
(1, 19, 'WRK-1001', 'Selvi Natarajan',  '+91 9840101001', 'CLEANING',   TRUE),
(2, 20, 'WRK-1002', 'Karthik Rajan',    '+91 9840101002', 'ELECTRICAL', TRUE),
(3, 21, 'WRK-1003', 'Mani Sundaram',    '+91 9840101003', 'PLUMBING',   TRUE),
(4, 22, 'WRK-1004', 'Senthil Kumar',    '+91 9840101004', 'CARPENTRY',  TRUE),
(5, 23, 'WRK-1005', 'Praveen IT Tech',  '+91 9840101005', 'IT_NETWORK', TRUE),
(6, 24, 'WRK-1006', 'Balaji Moorthy',   '+91 9840101006', 'GENERAL',    TRUE);
