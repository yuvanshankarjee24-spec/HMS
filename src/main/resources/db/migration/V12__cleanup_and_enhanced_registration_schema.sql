-- V12__cleanup_and_enhanced_registration_schema.sql
-- Purge all demo/seeded accounts and dependent sample records
-- Expand students, staff_profiles, and users tables for comprehensive 12-digit registration flow

-- 1. Clear dependent operational and sample logs first
DELETE FROM outpass_scan_logs;
DELETE FROM outpass_locks;
DELETE FROM file_attachments;
DELETE FROM qr_tokens;
DELETE FROM parcel_status_history;
DELETE FROM parcel_qr_tokens;
DELETE FROM parcels;
DELETE FROM outpasses;
DELETE FROM attendances;
DELETE FROM circular_versions;
DELETE FROM circulars;
DELETE FROM warden_assignments;
DELETE FROM allocation_history;
DELETE FROM room_requests;
DELETE FROM allocation_whitelist;

-- 2. Clear beds allocation to avoid FK constraints
UPDATE beds SET student_id = NULL;

-- 3. Clear profiles and users
DELETE FROM worker_profiles;
DELETE FROM staff_profiles;
DELETE FROM students;
DELETE FROM audit_logs;
DELETE FROM users;

-- 4. Extend students table for comprehensive registration data
ALTER TABLE students ADD COLUMN IF NOT EXISTS gender VARCHAR(20) NULL;
ALTER TABLE students ADD COLUMN IF NOT EXISTS date_of_birth DATE NULL;
ALTER TABLE students ADD COLUMN IF NOT EXISTS blood_group VARCHAR(10) NULL;
ALTER TABLE students ADD COLUMN IF NOT EXISTS program_course VARCHAR(100) NULL;
ALTER TABLE students ADD COLUMN IF NOT EXISTS section VARCHAR(10) NULL;
ALTER TABLE students ADD COLUMN IF NOT EXISTS admission_year INT NULL;
ALTER TABLE students ADD COLUMN IF NOT EXISTS address_line1 VARCHAR(255) NULL;
ALTER TABLE students ADD COLUMN IF NOT EXISTS address_line2 VARCHAR(255) NULL;
ALTER TABLE students ADD COLUMN IF NOT EXISTS city VARCHAR(100) NULL;
ALTER TABLE students ADD COLUMN IF NOT EXISTS district VARCHAR(100) NULL;
ALTER TABLE students ADD COLUMN IF NOT EXISTS state VARCHAR(100) NULL;
ALTER TABLE students ADD COLUMN IF NOT EXISTS pincode VARCHAR(10) NULL;
ALTER TABLE students ADD COLUMN IF NOT EXISTS nationality VARCHAR(50) DEFAULT 'Indian';
ALTER TABLE students ADD COLUMN IF NOT EXISTS religion VARCHAR(50) NULL;
ALTER TABLE students ADD COLUMN IF NOT EXISTS community VARCHAR(50) NULL;
ALTER TABLE students ADD COLUMN IF NOT EXISTS aadhaar_encrypted VARCHAR(255) NULL;
ALTER TABLE students ADD COLUMN IF NOT EXISTS father_name VARCHAR(150) NULL;
ALTER TABLE students ADD COLUMN IF NOT EXISTS father_phone VARCHAR(20) NULL;
ALTER TABLE students ADD COLUMN IF NOT EXISTS mother_name VARCHAR(150) NULL;
ALTER TABLE students ADD COLUMN IF NOT EXISTS mother_phone VARCHAR(20) NULL;
ALTER TABLE students ADD COLUMN IF NOT EXISTS emergency_contact VARCHAR(20) NULL;
ALTER TABLE students ADD COLUMN IF NOT EXISTS medical_conditions TEXT NULL;
ALTER TABLE students ADD COLUMN IF NOT EXISTS previous_institution VARCHAR(255) NULL;
ALTER TABLE students ADD COLUMN IF NOT EXISTS guardian_id_proof_path VARCHAR(255) NULL;

-- 5. Extend staff_profiles table
ALTER TABLE staff_profiles ADD COLUMN IF NOT EXISTS roll_number VARCHAR(100) NULL;
ALTER TABLE staff_profiles ADD COLUMN IF NOT EXISTS gender VARCHAR(20) NULL;
ALTER TABLE staff_profiles ADD COLUMN IF NOT EXISTS date_of_birth DATE NULL;
ALTER TABLE staff_profiles ADD COLUMN IF NOT EXISTS email VARCHAR(191) NULL;
ALTER TABLE staff_profiles ADD COLUMN IF NOT EXISTS department VARCHAR(100) NULL;
ALTER TABLE staff_profiles ADD COLUMN IF NOT EXISTS joining_date DATE NULL;
ALTER TABLE staff_profiles ADD COLUMN IF NOT EXISTS address TEXT NULL;
ALTER TABLE staff_profiles ADD COLUMN IF NOT EXISTS emergency_contact VARCHAR(20) NULL;
ALTER TABLE staff_profiles ADD COLUMN IF NOT EXISTS photo_path VARCHAR(255) NULL;

-- 6. Extend users table for temporary password expiry tracking
ALTER TABLE users ADD COLUMN IF NOT EXISTS temp_password_created_at TIMESTAMP NULL;
