-- V3__attendance_and_outpass.sql
-- Active Attendance & Outpass Management for Smart Hostel Portal

CREATE TABLE IF NOT EXISTS outpasses (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    student_id BIGINT NOT NULL,
    pass_type VARCHAR(50) NOT NULL,
    departure_date_time DATETIME NOT NULL,
    return_date_time DATETIME NOT NULL,
    reason TEXT NOT NULL,
    destination VARCHAR(255) NULL,
    emergency_phone VARCHAR(50) NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    warden_remarks VARCHAR(255) NULL,
    decided_by_user_id BIGINT NULL,
    decided_at DATETIME NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (student_id) REFERENCES students(id) ON DELETE CASCADE,
    FOREIGN KEY (decided_by_user_id) REFERENCES users(id) ON DELETE SET NULL,
    INDEX idx_outpasses_student (student_id),
    INDEX idx_outpasses_status (status),
    INDEX idx_outpasses_departure (departure_date_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS attendances (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    student_id BIGINT NOT NULL,
    attendance_date DATE NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PRESENT',
    marked_by_user_id BIGINT NULL,
    remarks VARCHAR(255) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (student_id) REFERENCES students(id) ON DELETE CASCADE,
    FOREIGN KEY (marked_by_user_id) REFERENCES users(id) ON DELETE SET NULL,
    UNIQUE KEY uk_student_attendance_date (student_id, attendance_date),
    INDEX idx_attendances_date (attendance_date),
    INDEX idx_attendances_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Seed initial Outpass requests
INSERT INTO outpasses (id, student_id, pass_type, departure_date_time, return_date_time, reason, destination, emergency_phone, status, warden_remarks, decided_by_user_id, decided_at) VALUES
(1, 1, 'WEEKEND_PASS', '2026-10-31 09:00:00', '2026-11-02 18:00:00', 'Visiting hometown for family function', 'Chennai', '+91 9840123456', 'PENDING', NULL, NULL, NULL),
(2, 2, 'DAY_PASS', '2026-10-31 10:00:00', '2026-10-31 19:00:00', 'Academic seminar and library reference at central campus', 'Anna Central Library', '+91 9444123456', 'PENDING', NULL, NULL, NULL),
(3, 5, 'EMERGENCY_PASS', '2026-10-29 14:00:00', '2026-10-31 20:00:00', 'Medical consultation and dentist checkup', 'Apollo Hospital, Greams Road', '+91 9789123456', 'APPROVED', 'Approved by Chief Warden. Carry student ID card.', 2, '2026-10-29 15:00:00');

-- Seed Today Attendance records for allocated students
INSERT INTO attendances (student_id, attendance_date, status, marked_by_user_id, remarks) VALUES
(1, CURRENT_DATE, 'PRESENT', 2, 'Biometric check-in recorded'),
(2, CURRENT_DATE, 'PRESENT', 2, 'Regular attendance'),
(3, CURRENT_DATE, 'PRESENT', 2, 'Regular attendance'),
(4, CURRENT_DATE, 'PRESENT', 2, 'Regular attendance'),
(5, CURRENT_DATE, 'ON_OUTPASS', 2, 'Approved Emergency Outpass #3'),
(6, CURRENT_DATE, 'ABSENT', 2, 'Unexcused roll call absence'),
(7, CURRENT_DATE, 'PRESENT', 2, 'Regular attendance');
