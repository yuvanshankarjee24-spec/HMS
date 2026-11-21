-- V13__room_and_bed_capacity_400.sql
-- Configure 100 rooms, 4 students per room, exactly 400 beds
-- Support room_allocations history, bed labels A-D, and row-level capacity integrity

-- 1. Ensure PSG iTech Hostel exists
INSERT INTO hostels (code, name, address, is_active, created_at, updated_at)
SELECT 'PSG_ITECH', 'PSG iTech Hostel', 'PSG iTech Campus, Neelambur, Coimbatore', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM hostels WHERE code = 'PSG_ITECH' OR name = 'PSG iTech Hostel');

UPDATE hostels SET name = 'PSG iTech Hostel' WHERE code = 'MAIN' AND NOT EXISTS (SELECT 1 FROM hostels WHERE code = 'PSG_ITECH');

-- 2. Extend rooms table
ALTER TABLE rooms ADD COLUMN IF NOT EXISTS is_active BOOLEAN NOT NULL DEFAULT TRUE;
UPDATE rooms SET capacity = 4, status = 'active', is_active = TRUE;

-- 3. Extend beds table with bed_label and status
ALTER TABLE beds ADD COLUMN IF NOT EXISTS bed_label VARCHAR(10) NULL;
ALTER TABLE beds ADD COLUMN IF NOT EXISTS status VARCHAR(30) NOT NULL DEFAULT 'available';

-- 4. Create room_allocations table
CREATE TABLE IF NOT EXISTS room_allocations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    student_id BIGINT NOT NULL,
    room_id BIGINT NOT NULL,
    bed_id BIGINT NOT NULL,
    allocated_by BIGINT NOT NULL,
    allocated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    vacated_at TIMESTAMP NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'active',
    remarks VARCHAR(255) NULL,
    CONSTRAINT fk_ra_student FOREIGN KEY (student_id) REFERENCES students(id) ON DELETE RESTRICT,
    CONSTRAINT fk_ra_room FOREIGN KEY (room_id) REFERENCES rooms(id) ON DELETE RESTRICT,
    CONSTRAINT fk_ra_bed FOREIGN KEY (bed_id) REFERENCES beds(id) ON DELETE RESTRICT,
    CONSTRAINT fk_ra_allocated_by FOREIGN KEY (allocated_by) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT chk_ra_status CHECK (status IN ('active', 'vacated', 'transferred'))
);

CREATE INDEX IF NOT EXISTS idx_ra_student_status ON room_allocations (student_id, status);
CREATE INDEX IF NOT EXISTS idx_ra_room_status ON room_allocations (room_id, status);
CREATE INDEX IF NOT EXISTS idx_ra_bed_status ON room_allocations (bed_id, status);
CREATE INDEX IF NOT EXISTS idx_beds_room_status ON beds (room_id, status);
CREATE INDEX IF NOT EXISTS idx_rooms_hostel_id ON rooms (hostel_id);

-- 5. Seed exactly 100 rooms (101 to 200) with capacity 4
-- Compute floors: 101-125 (Floor 1), 126-150 (Floor 2), 151-175 (Floor 3), 176-200 (Floor 4)
INSERT INTO rooms (room_number, floor, capacity, status, is_active, hostel_id, created_at)
SELECT
    CAST(100 + (t.d * 10) + u.d + 1 AS VARCHAR(10)),
    CASE
        WHEN (t.d * 10 + u.d + 1) <= 25 THEN 1
        WHEN (t.d * 10 + u.d + 1) <= 50 THEN 2
        WHEN (t.d * 10 + u.d + 1) <= 75 THEN 3
        ELSE 4
    END,
    4,
    'active',
    TRUE,
    (SELECT h.id FROM hostels h WHERE h.code = 'PSG_ITECH' OR h.code = 'MAIN' ORDER BY h.id ASC LIMIT 1),
    CURRENT_TIMESTAMP
FROM (SELECT 0 AS d UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
      UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) t
CROSS JOIN (SELECT 0 AS d UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
            UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) u
WHERE NOT EXISTS (
    SELECT 1 FROM rooms r WHERE r.room_number = CAST(100 + (t.d * 10) + u.d + 1 AS VARCHAR(10))
);

-- Ensure all 100 rooms (101 to 200) have capacity = 4 and link to the main hostel
UPDATE rooms SET
    capacity = 4,
    status = 'active',
    is_active = TRUE,
    hostel_id = COALESCE(hostel_id, (SELECT h.id FROM hostels h WHERE h.code = 'PSG_ITECH' OR h.code = 'MAIN' ORDER BY h.id ASC LIMIT 1))
WHERE CAST(room_number AS SIGNED) BETWEEN 101 AND 200;

-- 6. Seed exactly 4 beds (A, B, C, D) per room for all 100 rooms (400 beds in total)
INSERT INTO beds (room_id, bed_number, bed_label, status, created_at)
SELECT
    r.id,
    bdef.num,
    bdef.lbl,
    'available',
    CURRENT_TIMESTAMP
FROM rooms r
CROSS JOIN (
    SELECT 1 AS num, 'A' AS lbl UNION ALL
    SELECT 2 AS num, 'B' AS lbl UNION ALL
    SELECT 3 AS num, 'C' AS lbl UNION ALL
    SELECT 4 AS num, 'D' AS lbl
) bdef
WHERE CAST(r.room_number AS SIGNED) BETWEEN 101 AND 200
  AND NOT EXISTS (
      SELECT 1 FROM beds b WHERE b.room_id = r.id AND (b.bed_label = bdef.lbl OR b.bed_number = bdef.num)
  );

-- Update bed labels and status for existing beds in rooms 101..200
UPDATE beds SET
    bed_label = CASE bed_number
        WHEN 1 THEN 'A'
        WHEN 2 THEN 'B'
        WHEN 3 THEN 'C'
        WHEN 4 THEN 'D'
        ELSE CONCAT('B', bed_number)
    END,
    status = CASE WHEN student_id IS NOT NULL THEN 'occupied' ELSE 'available' END
WHERE bed_label IS NULL OR status IS NULL OR status = '';
