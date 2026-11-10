-- V7__parcel_management.sql
-- Parcel Management: Parcels, Single-Use QR Tokens, Status History, Attachments, Settings & Seed Data

CREATE TABLE IF NOT EXISTS parcels (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    parcel_code VARCHAR(32) NOT NULL UNIQUE,
    student_id BIGINT NOT NULL,
    hostel_block VARCHAR(100) NOT NULL,
    courier_name VARCHAR(100) NOT NULL,
    tracking_number VARCHAR(100) NULL,
    parcel_type VARCHAR(40) NOT NULL DEFAULT 'MEDIUM',
    description VARCHAR(500) NULL,
    is_cod BOOLEAN NOT NULL DEFAULT FALSE,
    cod_amount DECIMAL(10, 2) NULL,
    cod_payment_pending BOOLEAN NOT NULL DEFAULT FALSE,
    cod_collected BOOLEAN NOT NULL DEFAULT FALSE,
    storage_location VARCHAR(100) NULL,
    status VARCHAR(40) NOT NULL DEFAULT 'RECEIVED',
    received_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    received_by_user_id BIGINT NULL,
    delivered_at DATETIME NULL,
    delivered_by_user_id BIGINT NULL,
    delivery_remarks VARCHAR(500) NULL,
    handover_to_name VARCHAR(150) NULL,
    handover_reason VARCHAR(500) NULL,
    return_or_unclaimed_reason VARCHAR(500) NULL,
    reminder_24h_sent BOOLEAN NOT NULL DEFAULT FALSE,
    reminder_3d_sent BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (student_id) REFERENCES students(id) ON DELETE CASCADE,
    FOREIGN KEY (received_by_user_id) REFERENCES users(id) ON DELETE SET NULL,
    FOREIGN KEY (delivered_by_user_id) REFERENCES users(id) ON DELETE SET NULL,
    INDEX idx_prc_student (student_id),
    INDEX idx_prc_status (status),
    INDEX idx_prc_block (hostel_block),
    INDEX idx_prc_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS parcel_qr_tokens (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    parcel_id BIGINT NOT NULL UNIQUE,
    student_id BIGINT NOT NULL,
    qr_token VARCHAR(255) NOT NULL UNIQUE,
    pickup_code VARCHAR(12) NOT NULL UNIQUE,
    used BOOLEAN NOT NULL DEFAULT FALSE,
    used_at DATETIME NULL,
    expires_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (parcel_id) REFERENCES parcels(id) ON DELETE CASCADE,
    FOREIGN KEY (student_id) REFERENCES students(id) ON DELETE CASCADE,
    INDEX idx_prc_qr_token (qr_token),
    INDEX idx_prc_pickup_code (pickup_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS parcel_status_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    parcel_id BIGINT NOT NULL,
    old_status VARCHAR(40) NULL,
    new_status VARCHAR(40) NOT NULL,
    changed_by_user_id BIGINT NULL,
    remarks VARCHAR(500) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (parcel_id) REFERENCES parcels(id) ON DELETE CASCADE,
    FOREIGN KEY (changed_by_user_id) REFERENCES users(id) ON DELETE SET NULL,
    INDEX idx_prc_hist_parcel (parcel_id),
    INDEX idx_prc_hist_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS parcel_attachments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    parcel_id BIGINT NOT NULL,
    file_path VARCHAR(500) NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    file_size BIGINT NOT NULL,
    uploaded_by_user_id BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (parcel_id) REFERENCES parcels(id) ON DELETE CASCADE,
    FOREIGN KEY (uploaded_by_user_id) REFERENCES users(id) ON DELETE SET NULL,
    INDEX idx_prc_att_parcel (parcel_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO system_settings (setting_key, setting_value, description) VALUES
('parcel.reminder_24h_hours', '24', 'Hours before sending first waiting parcel reminder'),
('parcel.reminder_3d_hours', '72', 'Hours before sending second waiting parcel reminder'),
('parcel.long_pending_days', '7', 'Days after which waiting parcel is flagged Long-pending'),
('parcel.allow_friend_handover', 'true', 'Allow handing parcel to a friend when documented with reason and receiver name');

-- Seed demo parcels for Student 9 (Arjun Menon / REG202609) and Student 1 (Aarav Sharma / REG202601)
INSERT INTO parcels (id, parcel_code, student_id, hostel_block, courier_name, tracking_number, parcel_type, description, is_cod, cod_amount, cod_payment_pending, cod_collected, storage_location, status, received_at, received_by_user_id) VALUES
(1, 'PRC-2026-0001', 9, 'Main Block', 'Amazon', 'AMZ99887766', 'MEDIUM', 'Textbooks & Electronics Accessories', FALSE, NULL, FALSE, FALSE, 'Shelf A-12', 'NOTIFIED', CURRENT_TIMESTAMP, 2),
(2, 'PRC-2026-0002', 9, 'Main Block', 'Flipkart', 'FK44556677', 'COD', 'Study Lamp (Cash on Delivery)', TRUE, 749.00, TRUE, FALSE, 'Shelf B-03', 'NOTIFIED', CURRENT_TIMESTAMP, 2),
(3, 'PRC-2026-0003', 1, 'Floor 1', 'BlueDart', 'BD11223344', 'SMALL', 'Bank Document Envelope', FALSE, NULL, FALSE, FALSE, 'Rack C-01', 'DELIVERED', '2026-11-08 10:00:00', 2);

UPDATE parcels SET delivered_at = '2026-11-09 16:30:00', delivered_by_user_id = 2, delivery_remarks = 'Collected in person' WHERE id = 3;

INSERT INTO parcel_qr_tokens (id, parcel_id, student_id, qr_token, pickup_code, used, used_at) VALUES
(1, 1, 9, 'SEED-PARCEL-TOKEN-2026-0001', '482910', FALSE, NULL),
(2, 2, 9, 'SEED-PARCEL-TOKEN-2026-0002', '739104', FALSE, NULL),
(3, 3, 1, 'SEED-PARCEL-TOKEN-2026-0003', '195420', TRUE, '2026-11-09 16:30:00');

INSERT INTO parcel_status_history (parcel_id, old_status, new_status, changed_by_user_id, remarks) VALUES
(1, NULL, 'RECEIVED', 2, 'Parcel received at warden desk'),
(1, 'RECEIVED', 'NOTIFIED', 2, 'Student notified'),
(2, NULL, 'RECEIVED', 2, 'COD parcel received at warden desk'),
(2, 'RECEIVED', 'NOTIFIED', 2, 'Student notified'),
(3, NULL, 'RECEIVED', 2, 'Parcel received'),
(3, 'RECEIVED', 'DELIVERED', 2, 'Collected in person');
