-- =====================================================================
-- MySQL 8+ Companion Script: Enforce ON DELETE RESTRICT on Legacy Cascade FKs
-- Purpose: Legacy migrations V1-V9 defined ON DELETE CASCADE on some child
-- tables. To prevent accidental data loss via SQL DELETE statements, apply
-- this companion script on MySQL production databases after V11 completes.
-- =====================================================================

-- Outpasses -> Students
ALTER TABLE outpasses DROP FOREIGN KEY fk_outpasses_student;
ALTER TABLE outpasses ADD CONSTRAINT fk_outpasses_student
    FOREIGN KEY (student_id) REFERENCES students(id) ON DELETE RESTRICT;

-- Attendance Records -> Students & Sessions
ALTER TABLE attendance_records DROP FOREIGN KEY fk_da_rec_student;
ALTER TABLE attendance_records ADD CONSTRAINT fk_da_rec_student
    FOREIGN KEY (student_id) REFERENCES students(id) ON DELETE RESTRICT;

ALTER TABLE attendance_records DROP FOREIGN KEY fk_da_rec_session;
ALTER TABLE attendance_records ADD CONSTRAINT fk_da_rec_session
    FOREIGN KEY (session_id) REFERENCES attendance_sessions(id) ON DELETE RESTRICT;

-- Maintenance Attachments / History / Ratings -> Requests
ALTER TABLE request_attachments DROP FOREIGN KEY fk_ra_request;
ALTER TABLE request_attachments ADD CONSTRAINT fk_ra_request
    FOREIGN KEY (request_id) REFERENCES maintenance_requests(id) ON DELETE RESTRICT;

ALTER TABLE request_status_history DROP FOREIGN KEY fk_rsh_request;
ALTER TABLE request_status_history ADD CONSTRAINT fk_rsh_request
    FOREIGN KEY (request_id) REFERENCES maintenance_requests(id) ON DELETE RESTRICT;

-- Parcels & QR Tokens -> Students & Parcels
ALTER TABLE parcel_qr_tokens DROP FOREIGN KEY fk_pqt_parcel;
ALTER TABLE parcel_qr_tokens ADD CONSTRAINT fk_pqt_parcel
    FOREIGN KEY (parcel_id) REFERENCES parcels(id) ON DELETE RESTRICT;

ALTER TABLE parcel_status_history DROP FOREIGN KEY fk_psh_parcel;
ALTER TABLE parcel_status_history ADD CONSTRAINT fk_psh_parcel
    FOREIGN KEY (parcel_id) REFERENCES parcels(id) ON DELETE RESTRICT;

-- Mess Token Items / QR / History -> Mess Tokens
ALTER TABLE mess_token_items DROP FOREIGN KEY fk_mti_token;
ALTER TABLE mess_token_items ADD CONSTRAINT fk_mti_token
    FOREIGN KEY (token_id) REFERENCES mess_tokens(id) ON DELETE RESTRICT;

ALTER TABLE token_qr DROP FOREIGN KEY fk_tq_token;
ALTER TABLE token_qr ADD CONSTRAINT fk_tq_token
    FOREIGN KEY (token_id) REFERENCES mess_tokens(id) ON DELETE RESTRICT;

-- Notifications -> Users
ALTER TABLE notifications DROP FOREIGN KEY fk_notif_user;
ALTER TABLE notifications ADD CONSTRAINT fk_notif_user
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT;

