ALTER TABLE events ADD COLUMN registration_opens_at TIMESTAMPTZ;
ALTER TABLE events ADD CONSTRAINT ck_events_registration_window
    CHECK (registration_opens_at IS NULL OR registration_opens_at < COALESCE(registration_closes_at, starts_at));

CREATE TABLE pre_registrations (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    event_id UUID NOT NULL REFERENCES events(id),
    ticket_type_id UUID NOT NULL REFERENCES ticket_types(id),
    quantity INTEGER NOT NULL CHECK (quantity BETWEEN 1 AND 10),
    attendee_info JSONB NOT NULL,
    booking_id UUID REFERENCES bookings(id),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_pre_registrations_user_event UNIQUE (user_id, event_id)
);
CREATE INDEX idx_pre_registrations_user_updated ON pre_registrations(user_id, updated_at DESC, id DESC);
