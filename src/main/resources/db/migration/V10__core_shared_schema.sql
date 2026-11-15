-- V10__core_shared_schema.sql
-- Consolidate Core Shared Tables (Hostels, Warden Assignments, Users Soft-Delete, Students Shared FKs & View)

CREATE TABLE IF NOT EXISTS hostels (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(32) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL UNIQUE,
    address VARCHAR(255) NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_hostels_active (is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO hostels (code, name, address, is_active) VALUES
('MAIN', 'Main Block', 'PSG iTech Hostel Main Campus', TRUE),
('FL1',  'Floor 1',    'Main Block - First Floor Wing', TRUE),
('FL2',  'Floor 2',    'Main Block - Second Floor Wing', TRUE);

ALTER TABLE rooms ADD COLUMN hostel_id BIGINT NULL;
UPDATE rooms SET hostel_id = COALESCE(
    (SELECT h.id FROM hostels h WHERE h.code = CASE
        WHEN rooms.floor = 1 THEN 'FL1'
        WHEN rooms.floor = 2 THEN 'FL2'
        ELSE 'MAIN'
    END),
    1
);
ALTER TABLE rooms ADD CONSTRAINT fk_rooms_hostel FOREIGN KEY (hostel_id) REFERENCES hostels(id) ON DELETE RESTRICT;
CREATE INDEX idx_rooms_hostel ON rooms (hostel_id);

CREATE TABLE IF NOT EXISTS warden_assignments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    warden_user_id BIGINT NOT NULL,
    hostel_id BIGINT NOT NULL,
    is_primary BOOLEAN NOT NULL DEFAULT TRUE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    assigned_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_warden_hostel UNIQUE (warden_user_id, hostel_id),
    CONSTRAINT fk_wa_warden_user FOREIGN KEY (warden_user_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_wa_hostel FOREIGN KEY (hostel_id) REFERENCES hostels(id) ON DELETE RESTRICT,
    INDEX idx_wa_warden (warden_user_id),
    INDEX idx_wa_hostel (hostel_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Assign default seed warden (id=2 if present) to seeded hostels idempotently
INSERT INTO warden_assignments (warden_user_id, hostel_id, is_primary, active)
SELECT u.id, h.id, CASE WHEN h.code = 'MAIN' THEN TRUE ELSE FALSE END, TRUE
FROM users u CROSS JOIN hostels h
WHERE u.role = 'WARDEN'
  AND NOT EXISTS (
      SELECT 1 FROM warden_assignments wa WHERE wa.warden_user_id = u.id AND wa.hostel_id = h.id
  );

-- Enrich users as the Single Source of Truth for identity & soft-delete lifecycle
ALTER TABLE users ADD COLUMN name VARCHAR(150) NULL;
ALTER TABLE users ADD COLUMN phone VARCHAR(20) NULL;
ALTER TABLE users ADD COLUMN is_active BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE users ADD COLUMN deleted_at DATETIME NULL;
CREATE INDEX idx_users_active_del ON users (is_active, deleted_at);

UPDATE users SET
    name = COALESCE(
        (SELECT s.full_name FROM students s WHERE s.user_id = users.id),
        (SELECT sp.full_name FROM staff_profiles sp WHERE sp.user_id = users.id),
        (SELECT wp.full_name FROM worker_profiles wp WHERE wp.user_id = users.id),
        users.email
    ),
    phone = COALESCE(
        (SELECT s.phone FROM students s WHERE s.user_id = users.id),
        (SELECT sp.phone FROM staff_profiles sp WHERE sp.user_id = users.id),
        (SELECT wp.phone FROM worker_profiles wp WHERE wp.user_id = users.id)
    ),
    is_active = CASE WHEN UPPER(status) = 'DISABLED' THEN FALSE ELSE TRUE END;

-- Enrich students with shared hostel_id / room_id FKs, guardian details, soft-delete & missing FK on unlocked_by_user_id
ALTER TABLE students ADD COLUMN hostel_id BIGINT NULL;
ALTER TABLE students ADD COLUMN room_id BIGINT NULL;
ALTER TABLE students ADD COLUMN guardian_name VARCHAR(150) NULL;
ALTER TABLE students ADD COLUMN guardian_phone VARCHAR(20) NULL;
ALTER TABLE students ADD COLUMN guardian_relation VARCHAR(50) NULL;
ALTER TABLE students ADD COLUMN is_active BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE students ADD COLUMN deleted_at DATETIME NULL;

UPDATE students SET
    room_id = (SELECT b.room_id FROM beds b WHERE b.student_id = students.id),
    hostel_id = COALESCE(
        (SELECT r.hostel_id FROM beds b JOIN rooms r ON b.room_id = r.id WHERE b.student_id = students.id),
        (SELECT h.id FROM hostels h WHERE h.code = 'MAIN')
    );

ALTER TABLE students ADD CONSTRAINT fk_students_hostel FOREIGN KEY (hostel_id) REFERENCES hostels(id) ON DELETE RESTRICT;
ALTER TABLE students ADD CONSTRAINT fk_students_room FOREIGN KEY (room_id) REFERENCES rooms(id) ON DELETE RESTRICT;
ALTER TABLE students ADD CONSTRAINT fk_students_unlocked_by FOREIGN KEY (unlocked_by_user_id) REFERENCES users(id) ON DELETE RESTRICT;
CREATE INDEX idx_students_hostel ON students (hostel_id);
CREATE INDEX idx_students_room ON students (room_id);
CREATE INDEX idx_students_locked ON students (is_locked);
CREATE INDEX idx_students_active_del ON students (is_active, deleted_at);

ALTER TABLE worker_profiles ADD COLUMN deleted_at DATETIME NULL;
CREATE INDEX idx_worker_active_del ON worker_profiles (active, deleted_at);

-- Canonical student_profiles view exposing unified relational schema without breaking existing queries
CREATE OR REPLACE VIEW student_profiles AS
SELECT
    s.id,
    s.user_id,
    s.register_number AS roll_number,
    s.full_name AS name,
    s.college_email AS email,
    s.phone,
    s.department,
    s.`year`,
    s.hostel_id,
    s.room_id,
    s.guardian_name,
    s.guardian_phone,
    s.guardian_relation,
    s.photo_path,
    s.is_locked,
    s.is_active,
    s.deleted_at,
    s.created_at
FROM students s;

