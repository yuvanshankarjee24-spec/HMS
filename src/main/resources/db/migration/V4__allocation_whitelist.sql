-- V4__allocation_whitelist.sql
-- Online Room Allocation Whitelist & System Configuration

CREATE TABLE IF NOT EXISTS allocation_whitelist (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    leader_register_number VARCHAR(50) NOT NULL UNIQUE,
    leader_name VARCHAR(100) NULL,
    target_room_number VARCHAR(20) NULL,
    group_size INT NOT NULL DEFAULT 1,
    friend_register_numbers TEXT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'ELIGIBLE',
    allocated_room_number VARCHAR(20) NULL,
    allocated_at DATETIME NULL,
    notes VARCHAR(255) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_whitelist_leader (leader_register_number),
    INDEX idx_whitelist_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS system_settings (
    setting_key VARCHAR(50) PRIMARY KEY,
    setting_value VARCHAR(255) NOT NULL,
    description VARCHAR(255) NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Initialize Online Room Allocation setting to OPEN
INSERT INTO system_settings (setting_key, setting_value, description) VALUES
('ONLINE_ALLOCATION_ROUND', 'OPEN', 'Online room allocation portal status: OPEN or CLOSED')
ON DUPLICATE KEY UPDATE setting_value = 'OPEN';

-- Seed initial Whitelist representative from pre-noted Excel sheet
-- Sanya Kapoor (REG202608) is the designated leader for her group with friend Arjun Menon (REG202609) for Room 104 (Capacity 2)
INSERT INTO allocation_whitelist (leader_register_number, leader_name, target_room_number, group_size, friend_register_numbers, status, notes) VALUES
('REG202608', 'Sanya Kapoor', '104', 2, 'REG202609', 'ELIGIBLE', 'Designated Group Leader from Hostel Excel Allocation Sheet Round 1');
