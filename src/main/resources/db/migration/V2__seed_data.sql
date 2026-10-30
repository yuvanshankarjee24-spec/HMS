-- V2__seed_data.sql
-- Seed Initial Demo Data for Smart Hostel Management System
-- Password for all seed users is: Hostel@123
-- Hash: $2a$10$DakqzPWIKvUUrXmrlcjDc.md7ijkoXVmVUFO2BBujSaBR.9.nWZdq

-- 1. Insert Staff & Admin Users
INSERT INTO users (id, email, register_number, password_hash, role, status, must_change_password, failed_attempts) VALUES
(1, 'admin@hostel.edu', NULL, '$2a$10$DakqzPWIKvUUrXmrlcjDc.md7ijkoXVmVUFO2BBujSaBR.9.nWZdq', 'ADMIN', 'ACTIVE', FALSE, 0),
(2, 'warden@hostel.edu', NULL, '$2a$10$DakqzPWIKvUUrXmrlcjDc.md7ijkoXVmVUFO2BBujSaBR.9.nWZdq', 'WARDEN', 'ACTIVE', FALSE, 0),
(3, 'security@hostel.edu', NULL, '$2a$10$DakqzPWIKvUUrXmrlcjDc.md7ijkoXVmVUFO2BBujSaBR.9.nWZdq', 'SECURITY', 'ACTIVE', FALSE, 0),
(4, 'cleaner1@hostel.edu', NULL, '$2a$10$DakqzPWIKvUUrXmrlcjDc.md7ijkoXVmVUFO2BBujSaBR.9.nWZdq', 'CLEANING_WORKER', 'ACTIVE', FALSE, 0),
(5, 'cleaner2@hostel.edu', NULL, '$2a$10$DakqzPWIKvUUrXmrlcjDc.md7ijkoXVmVUFO2BBujSaBR.9.nWZdq', 'CLEANING_WORKER', 'ACTIVE', FALSE, 0),
(6, 'maintenance@hostel.edu', NULL, '$2a$10$DakqzPWIKvUUrXmrlcjDc.md7ijkoXVmVUFO2BBujSaBR.9.nWZdq', 'MAINTENANCE_STAFF', 'ACTIVE', FALSE, 0),
(7, 'mess@hostel.edu', NULL, '$2a$10$DakqzPWIKvUUrXmrlcjDc.md7ijkoXVmVUFO2BBujSaBR.9.nWZdq', 'MESS_STAFF', 'ACTIVE', FALSE, 0),
(8, 'vending@hostel.edu', NULL, '$2a$10$DakqzPWIKvUUrXmrlcjDc.md7ijkoXVmVUFO2BBujSaBR.9.nWZdq', 'VENDING_MANAGER', 'ACTIVE', FALSE, 0);

-- 2. Staff Profiles
INSERT INTO staff_profiles (id, user_id, full_name, phone, designation) VALUES
(1, 1, 'Dr. Sarah Jenkins', '+91 9840112233', 'Chief Hostel Administrator'),
(2, 2, 'Prof. Ramesh Kulkarni', '+91 9840223344', 'Chief Warden'),
(3, 3, 'Devraj Singh', '+91 9840334455', 'Head Security Guard'),
(4, 4, 'Lakshmi Ammal', '+91 9840445566', 'Sanitation Lead'),
(5, 5, 'Murugan Velu', '+91 9840556677', 'Wing Cleaning Worker'),
(6, 6, 'Gopalakrishnan N', '+91 9840667788', 'Senior Electrician & Maintenance'),
(7, 7, 'Suresh Kumar', '+91 9840778899', 'Mess In-Charge'),
(8, 8, 'Vikram Malhotra', '+91 9840889900', 'Vending Operations Supervisor');

-- 3. Insert 10 Demo Student Users
INSERT INTO users (id, email, register_number, password_hash, role, status, must_change_password, failed_attempts) VALUES
(9,  'student1@college.edu',  'REG202601', '$2a$10$DakqzPWIKvUUrXmrlcjDc.md7ijkoXVmVUFO2BBujSaBR.9.nWZdq', 'STUDENT', 'ACTIVE', FALSE, 0),
(10, 'student2@college.edu',  'REG202602', '$2a$10$DakqzPWIKvUUrXmrlcjDc.md7ijkoXVmVUFO2BBujSaBR.9.nWZdq', 'STUDENT', 'ACTIVE', FALSE, 0),
(11, 'student3@college.edu',  'REG202603', '$2a$10$DakqzPWIKvUUrXmrlcjDc.md7ijkoXVmVUFO2BBujSaBR.9.nWZdq', 'STUDENT', 'ACTIVE', FALSE, 0),
(12, 'student4@college.edu',  'REG202604', '$2a$10$DakqzPWIKvUUrXmrlcjDc.md7ijkoXVmVUFO2BBujSaBR.9.nWZdq', 'STUDENT', 'ACTIVE', FALSE, 0),
(13, 'student5@college.edu',  'REG202605', '$2a$10$DakqzPWIKvUUrXmrlcjDc.md7ijkoXVmVUFO2BBujSaBR.9.nWZdq', 'STUDENT', 'ACTIVE', FALSE, 0),
(14, 'student6@college.edu',  'REG202606', '$2a$10$DakqzPWIKvUUrXmrlcjDc.md7ijkoXVmVUFO2BBujSaBR.9.nWZdq', 'STUDENT', 'ACTIVE', FALSE, 0),
(15, 'student7@college.edu',  'REG202607', '$2a$10$DakqzPWIKvUUrXmrlcjDc.md7ijkoXVmVUFO2BBujSaBR.9.nWZdq', 'STUDENT', 'ACTIVE', FALSE, 0),
(16, 'student8@college.edu',  'REG202608', '$2a$10$DakqzPWIKvUUrXmrlcjDc.md7ijkoXVmVUFO2BBujSaBR.9.nWZdq', 'STUDENT', 'ACTIVE', FALSE, 0),
(17, 'student9@college.edu',  'REG202609', '$2a$10$DakqzPWIKvUUrXmrlcjDc.md7ijkoXVmVUFO2BBujSaBR.9.nWZdq', 'STUDENT', 'ACTIVE', FALSE, 0),
(18, 'student10@college.edu', 'REG202610', '$2a$10$DakqzPWIKvUUrXmrlcjDc.md7ijkoXVmVUFO2BBujSaBR.9.nWZdq', 'STUDENT', 'ACTIVE', FALSE, 0);

-- 4. Student Profiles
INSERT INTO students (id, user_id, full_name, register_number, college_email, phone, department, `year`, photo_path) VALUES
(1,  9,  'Aarav Sharma',   'REG202601', 'student1@college.edu',  '+91 9111000001', 'Computer Science & Engineering', 2, NULL),
(2,  10, 'Diya Patel',     'REG202602', 'student2@college.edu',  '+91 9111000002', 'Electronics & Communication', 2, NULL),
(3,  11, 'Ishaan Verma',   'REG202603', 'student3@college.edu',  '+91 9111000003', 'Mechanical Engineering', 3, NULL),
(4,  12, 'Ananya Iyer',    'REG202604', 'student4@college.edu',  '+91 9111000004', 'Civil Engineering', 3, NULL),
(5,  13, 'Rohan Nair',     'REG202605', 'student5@college.edu',  '+91 9111000005', 'Computer Science & Engineering', 1, NULL),
(6,  14, 'Meera Reddy',    'REG202606', 'student6@college.edu',  '+91 9111000006', 'Information Technology', 1, NULL),
(7,  15, 'Kabir Das',      'REG202607', 'student7@college.edu',  '+91 9111000007', 'Electrical & Electronics', 4, NULL),
(8,  16, 'Sanya Kapoor',   'REG202608', 'student8@college.edu',  '+91 9111000008', 'Computer Science & Engineering', 2, NULL),
(9,  17, 'Arjun Menon',    'REG202609', 'student9@college.edu',  '+91 9111000009', 'Artificial Intelligence & Data Science', 1, NULL),
(10, 18, 'Pooja Hegde',    'REG202610', 'student10@college.edu', '+91 9111000010', 'Electronics & Communication', 1, NULL);

-- 5. Rooms
-- Room 101: Floor 1, Capacity 4, Occupied 4
-- Room 102: Floor 1, Capacity 4, Occupied 2
-- Room 103: Floor 2, Capacity 3, Occupied 1
-- Room 104: Floor 2, Capacity 2, Occupied 0
INSERT INTO rooms (id, room_number, floor, capacity, status) VALUES
(1, '101', 1, 4, 'ACTIVE'),
(2, '102', 1, 4, 'ACTIVE'),
(3, '103', 2, 3, 'ACTIVE'),
(4, '104', 2, 2, 'ACTIVE');

-- 6. Beds
-- Room 101: Beds 1-4 (All occupied: Students 1, 2, 3, 4)
INSERT INTO beds (id, room_id, bed_number, student_id) VALUES
(1,  1, 1, 1),
(2,  1, 2, 2),
(3,  1, 3, 3),
(4,  1, 4, 4),

-- Room 102: Beds 1-4 (Occupied: Students 5, 6; Available: Beds 3, 4)
(5,  2, 1, 5),
(6,  2, 2, 6),
(7,  2, 3, NULL),
(8,  2, 4, NULL),

-- Room 103: Beds 1-3 (Occupied: Student 7; Available: Beds 2, 3)
(9,  3, 1, 7),
(10, 3, 2, NULL),
(11, 3, 3, NULL),

-- Room 104: Beds 1-2 (All Available: Beds 1, 2)
(12, 4, 1, NULL),
(13, 4, 2, NULL);

-- 7. Room Request for Student 8 (PENDING)
INSERT INTO room_requests (id, student_id, preferred_room_id, preferred_floor, note, status, decided_by, decision_note, created_at) VALUES
(1, 8, 2, 1, 'Near study room preferred, ground or 1st floor.', 'PENDING', NULL, NULL, NOW());

-- 8. Allocation History for the 7 initial allocations
INSERT INTO allocation_history (id, student_id, bed_id, action, actor_user_id, created_at) VALUES
(1, 1, 1, 'ALLOCATED', 2, NOW()),
(2, 2, 2, 'ALLOCATED', 2, NOW()),
(3, 3, 3, 'ALLOCATED', 2, NOW()),
(4, 4, 4, 'ALLOCATED', 2, NOW()),
(5, 5, 5, 'ALLOCATED', 2, NOW()),
(6, 6, 6, 'ALLOCATED', 2, NOW()),
(7, 7, 9, 'ALLOCATED', 2, NOW());

-- 9. Initial Audit Log
INSERT INTO audit_logs (id, actor_user_id, action, target, result, created_at) VALUES
(1, 1, 'SYSTEM_INITIALIZATION', 'hostel_db', 'SUCCESS', NOW()),
(2, 2, 'INITIAL_ALLOCATIONS', 'Rooms 101, 102, 103', 'SUCCESS', NOW());
