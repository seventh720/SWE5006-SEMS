ALTER TABLE users
    ADD COLUMN profile_real_name VARCHAR(100),
    ADD COLUMN profile_email VARCHAR(255),
    ADD COLUMN profile_phone VARCHAR(30),
    ADD COLUMN profile_student_id VARCHAR(100),
    ADD COLUMN profile_passport_number VARCHAR(100);

ALTER TABLE events
    ADD COLUMN require_student_id BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN require_passport BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN custom_field_label VARCHAR(100);

ALTER TABLE bookings
    ADD COLUMN attendee_student_id VARCHAR(100),
    ADD COLUMN attendee_passport_number VARCHAR(100),
    ADD COLUMN custom_field_label VARCHAR(100),
    ADD COLUMN attendee_custom_answer VARCHAR(500);

-- Preserve earlier generic document requirements and order snapshots.
UPDATE events SET custom_field_label = 'Identity document number (student ID, passport or other)'
WHERE require_document = TRUE;
UPDATE bookings SET custom_field_label = 'Identity document number (student ID, passport or other)',
    attendee_custom_answer = attendee_document_type || ': ' || attendee_document_number
WHERE attendee_document_number IS NOT NULL;
