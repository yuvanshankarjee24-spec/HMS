-- V9__mess_token_booking.sql
-- Part B: Mess Token Booking (Food Items, Period Config, Menu Schedule, Tokens, Token Items, Single-Use QR, Status History)

CREATE TABLE IF NOT EXISTS food_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(120) NOT NULL UNIQUE,
    category VARCHAR(30) NOT NULL,
    price DECIMAL(10, 2) NOT NULL DEFAULT 0.00,
    photo_path VARCHAR(500) NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_mess_food_active (active),
    INDEX idx_mess_food_cat (category)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS mess_period_config (
    period VARCHAR(30) PRIMARY KEY,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    cutoff_minutes_before INT NOT NULL DEFAULT 120,
    qr_open_minutes_before INT NOT NULL DEFAULT 15,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS menu_schedule (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    menu_date DATE NOT NULL,
    period VARCHAR(30) NOT NULL,
    food_item_id BIGINT NOT NULL,
    daily_stock INT NOT NULL,
    remaining_stock INT NOT NULL,
    max_per_student INT NOT NULL DEFAULT 2,
    is_special_menu BOOLEAN NOT NULL DEFAULT FALSE,
    mess_closed BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (food_item_id) REFERENCES food_items(id) ON DELETE CASCADE,
    UNIQUE KEY uk_mess_sched_date_period_item (menu_date, period, food_item_id),
    INDEX idx_mess_sched_date_period (menu_date, period)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS mess_tokens (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    token_code VARCHAR(32) NOT NULL UNIQUE,
    student_id BIGINT NOT NULL,
    hostel_block VARCHAR(100) NOT NULL DEFAULT 'Main Block',
    menu_date DATE NOT NULL,
    period VARCHAR(30) NOT NULL,
    total_amount DECIMAL(10, 2) NOT NULL DEFAULT 0.00,
    status VARCHAR(30) NOT NULL DEFAULT 'BOOKED',
    delivered_at DATETIME NULL,
    delivered_by_user_id BIGINT NULL,
    delivery_remarks VARCHAR(500) NULL,
    qr_activation_notified BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (student_id) REFERENCES students(id) ON DELETE CASCADE,
    FOREIGN KEY (delivered_by_user_id) REFERENCES users(id) ON DELETE SET NULL,
    INDEX idx_mess_tkn_student (student_id),
    INDEX idx_mess_tkn_date_period (menu_date, period),
    INDEX idx_mess_tkn_status (status),
    INDEX idx_mess_tkn_block (hostel_block)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS mess_token_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    token_id BIGINT NOT NULL,
    menu_schedule_id BIGINT NOT NULL,
    food_item_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    unit_price DECIMAL(10, 2) NOT NULL DEFAULT 0.00,
    delivered_quantity INT NULL,
    undelivered_reason VARCHAR(500) NULL,
    FOREIGN KEY (token_id) REFERENCES mess_tokens(id) ON DELETE CASCADE,
    FOREIGN KEY (menu_schedule_id) REFERENCES menu_schedule(id) ON DELETE CASCADE,
    FOREIGN KEY (food_item_id) REFERENCES food_items(id) ON DELETE CASCADE,
    INDEX idx_mess_titem_token (token_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS token_qr (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    token_id BIGINT NOT NULL UNIQUE,
    student_id BIGINT NOT NULL,
    qr_token VARCHAR(255) NOT NULL UNIQUE,
    pickup_code VARCHAR(12) NOT NULL UNIQUE,
    used BOOLEAN NOT NULL DEFAULT FALSE,
    closed BOOLEAN NOT NULL DEFAULT FALSE,
    used_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (token_id) REFERENCES mess_tokens(id) ON DELETE CASCADE,
    FOREIGN KEY (student_id) REFERENCES students(id) ON DELETE CASCADE,
    INDEX idx_mess_qr_token (qr_token),
    INDEX idx_mess_qr_pickup (pickup_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS token_status_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    token_id BIGINT NOT NULL,
    old_status VARCHAR(30) NULL,
    new_status VARCHAR(30) NOT NULL,
    changed_by_user_id BIGINT NULL,
    remarks VARCHAR(500) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (token_id) REFERENCES mess_tokens(id) ON DELETE CASCADE,
    FOREIGN KEY (changed_by_user_id) REFERENCES users(id) ON DELETE SET NULL,
    INDEX idx_mess_hist_token (token_id),
    INDEX idx_mess_hist_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO system_settings (setting_key, setting_value, description) VALUES
('mess.advance_booking_days', '7', 'Number of upcoming days students can book mess tokens for');

-- Seed the 5 required Food Items
INSERT INTO food_items (id, name, category, price, active) VALUES
(1, 'Boiled Egg',       'EGG',     15.00,  TRUE),
(2, 'Omelette',         'EGG',     25.00,  TRUE),
(3, 'Chicken Gravy',    'NON_VEG', 85.00,  TRUE),
(4, 'Chicken Biryani',  'NON_VEG', 130.00, TRUE),
(5, 'Gobi Manchurian',  'VEG',     75.00,  TRUE);

-- Seed Meal Period Timings
INSERT INTO mess_period_config (period, start_time, end_time, cutoff_minutes_before, qr_open_minutes_before) VALUES
('BREAKFAST', '07:30:00', '09:30:00', 120, 15),
('LUNCH',     '12:30:00', '14:30:00', 120, 15),
('SNACKS',    '16:30:00', '18:00:00', 120, 15),
('DINNER',    '20:00:00', '22:00:00', 120, 15);

