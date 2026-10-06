ALTER TABLE events
    ADD COLUMN require_real_name BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN require_email BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN require_phone BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN require_document BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE bookings
    ADD COLUMN attendee_real_name VARCHAR(100),
    ADD COLUMN attendee_email VARCHAR(255),
    ADD COLUMN attendee_phone VARCHAR(30),
    ADD COLUMN attendee_document_type VARCHAR(20),
    ADD COLUMN attendee_document_number VARCHAR(100),
    ADD CONSTRAINT ck_bookings_document CHECK (
        (attendee_document_type IS NULL AND attendee_document_number IS NULL)
        OR (attendee_document_type IS NOT NULL AND attendee_document_number IS NOT NULL
            AND attendee_document_type IN ('STUDENT_ID', 'PASSPORT', 'OTHER')));
