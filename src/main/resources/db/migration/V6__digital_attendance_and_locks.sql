-- V6__digital_attendance_and_locks.sql
-- Digital Attendance Sessions, Records, Idempotent Reminder Logs, Outpass Locking & History

ALTER TABLE students ADD COLUMN is_locked BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE students ADD COLUMN lock_reason VARCHAR(500) NULL;
ALTER TABLE students ADD COLUMN locked_at DATETIME NULL;
ALTER TABLE students ADD COLUMN unlocked_by_user_id BIGINT NULL;
ALTER TABLE students ADD COLUMN unlocked_at DATETIME NULL;
ALTER TABLE students ADD COLUMN unlock_remarks VARCHAR(500) NULL;

ALTER TABLE outpasses ADD COLUMN actual_exit_time DATETIME NULL;

CREATE TABLE IF NOT EXISTS attendance_sessions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    hostel_block VARCHAR(100) NOT NULL DEFAULT 'ALL',
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    active_days VARCHAR(64) NOT NULL DEFAULT 'MON,TUE,WED,THU,FRI,SAT,SUN',
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    reminder_lead_minutes INT NOT NULL DEFAULT 30,
    second_reminder_minutes INT NULL DEFAULT 5,
    geofence_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    hostel_latitude DOUBLE NULL,
    hostel_longitude DOUBLE NULL,
    geofence_radius_meters INT NULL DEFAULT 150,
    created_by_user_id BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (created_by_user_id) REFERENCES users(id) ON DELETE SET NULL,
    INDEX idx_da_sess_enabled (enabled),
    INDEX idx_da_sess_block (hostel_block)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS attendance_records (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    student_id BIGINT NOT NULL,
    session_id BIGINT NOT NULL,
    attendance_date DATE NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    marked_at DATETIME NULL,
    latitude DOUBLE NULL,
    longitude DOUBLE NULL,
    device_info VARCHAR(255) NULL,
    ip_address VARCHAR(64) NULL,
    marked_by_user_id BIGINT NULL,
    remarks VARCHAR(500) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (student_id) REFERENCES students(id) ON DELETE CASCADE,
    FOREIGN KEY (session_id) REFERENCES attendance_sessions(id) ON DELETE CASCADE,
    FOREIGN KEY (marked_by_user_id) REFERENCES users(id) ON DELETE SET NULL,
    UNIQUE KEY uk_da_student_session_date (student_id, session_id, attendance_date),
    INDEX idx_da_rec_student (student_id),
    INDEX idx_da_rec_session (session_id),
    INDEX idx_da_rec_date (attendance_date),
    INDEX idx_da_rec_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS attendance_notifications_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    student_id BIGINT NOT NULL,
    session_id BIGINT NOT NULL,
    attendance_date DATE NOT NULL,
    reminder_type VARCHAR(40) NOT NULL,
    sent_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (student_id) REFERENCES students(id) ON DELETE CASCADE,
    FOREIGN KEY (session_id) REFERENCES attendance_sessions(id) ON DELETE CASCADE,
    UNIQUE KEY uk_da_notif_idempotent (student_id, session_id, attendance_date, reminder_type),
    INDEX idx_da_nlog_date (attendance_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS outpass_lock_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    student_id BIGINT NOT NULL,
    action VARCHAR(30) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    actor_user_id BIGINT NULL,
    session_id BIGINT NULL,
    attendance_date DATE NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (student_id) REFERENCES students(id) ON DELETE CASCADE,
    FOREIGN KEY (actor_user_id) REFERENCES users(id) ON DELETE SET NULL,
    FOREIGN KEY (session_id) REFERENCES attendance_sessions(id) ON DELETE SET NULL,
    INDEX idx_da_lock_hist_student (student_id),
    INDEX idx_da_lock_hist_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Global configurable defaults for Digital Attendance
INSERT INTO system_settings (setting_key, setting_value, description) VALUES
('attendance.reminder_lead_minutes', '30', 'Default reminder lead time in minutes before attendance deadline'),
('attendance.second_reminder_minutes', '5', 'Optional second reminder minutes before deadline (0 to disable)'),
('attendance.geofence_enabled', 'false', 'Default geofence requirement toggle'),
('attendance.hostel_latitude', '11.0698', 'Default hostel latitude for geofencing'),
('attendance.hostel_longitude', '77.0928', 'Default hostel longitude for geofencing'),
('attendance.geofence_radius_meters', '200', 'Default hostel geofence radius in meters');

-- Seed sample attendance session
INSERT INTO attendance_sessions (id, name, hostel_block, start_time, end_time, active_days, enabled, reminder_lead_minutes, second_reminder_minutes, geofence_enabled, hostel_latitude, hostel_longitude, geofence_radius_meters, created_by_user_id) VALUES
(1, 'Night Attendance', 'ALL', '20:30:00', '22:00:00', 'MON,TUE,WED,THU,FRI,SAT,SUN', TRUE, 30, 5, FALSE, 11.0698, 77.0928, 200, 2);

