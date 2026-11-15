-- V11__down.sql (Rollback V11__link_modules_integrity.sql cleanly without data loss)

ALTER TABLE mess_tokens DROP CHECK chk_mess_tokens_status;
UPDATE mess_tokens SET status = UPPER(status);
ALTER TABLE circulars DROP CHECK chk_circulars_status;
UPDATE circulars SET status = UPPER(status);
ALTER TABLE parcels DROP CHECK chk_parcels_status;
UPDATE parcels SET status = UPPER(status);
ALTER TABLE maintenance_requests DROP CHECK chk_maint_status;
UPDATE maintenance_requests SET status = UPPER(status);
ALTER TABLE attendance_records DROP CHECK chk_da_rec_status;
UPDATE attendance_records SET status = UPPER(status);
ALTER TABLE attendances DROP CHECK chk_att_status;
UPDATE attendances SET status = UPPER(status);
ALTER TABLE outpasses DROP CHECK chk_outpasses_status;
UPDATE outpasses SET status = UPPER(status);
ALTER TABLE room_requests DROP CHECK chk_room_req_status;
UPDATE room_requests SET status = UPPER(status);
ALTER TABLE rooms DROP CHECK chk_rooms_status;
UPDATE rooms SET status = UPPER(status);
ALTER TABLE users DROP CHECK chk_users_status;
UPDATE users SET status = UPPER(status);

DROP TABLE IF EXISTS qr_tokens;
DROP TABLE IF EXISTS file_attachments;

ALTER TABLE audit_logs DROP COLUMN new_value, DROP COLUMN old_value, DROP COLUMN reference_id, DROP COLUMN module;
ALTER TABLE notifications DROP COLUMN link, DROP COLUMN module, DROP COLUMN type;

ALTER TABLE mess_tokens DROP FOREIGN KEY fk_mess_tkn_hostel, DROP COLUMN hostel_id;
ALTER TABLE circular_targets DROP FOREIGN KEY fk_circ_tgt_hostel, DROP COLUMN hostel_id;
ALTER TABLE circulars DROP FOREIGN KEY fk_circ_hostel, DROP COLUMN hostel_id;
ALTER TABLE parcels DROP FOREIGN KEY fk_prc_hostel, DROP COLUMN hostel_id;
ALTER TABLE maintenance_requests DROP FOREIGN KEY fk_maint_room, DROP COLUMN room_id, DROP FOREIGN KEY fk_maint_hostel, DROP COLUMN hostel_id;
ALTER TABLE attendance_sessions DROP FOREIGN KEY fk_att_sess_hostel, DROP COLUMN hostel_id;

DROP TABLE IF EXISTS outpass_locks;
DROP TABLE IF EXISTS outpass_scan_logs;

