ALTER TABLE events ADD COLUMN registration_closes_at TIMESTAMPTZ;

-- Existing events keep accepting bookings until their start time.
UPDATE events SET registration_closes_at = starts_at;

ALTER TABLE events ADD CONSTRAINT ck_events_registration_deadline
    CHECK (registration_closes_at IS NULL OR registration_closes_at <= starts_at);
