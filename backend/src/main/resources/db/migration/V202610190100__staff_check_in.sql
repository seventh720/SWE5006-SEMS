CREATE TABLE event_staff_assignments (
    event_id UUID NOT NULL REFERENCES events(id) ON DELETE CASCADE,
    staff_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    PRIMARY KEY (event_id, staff_id)
);
CREATE INDEX idx_event_staff_staff ON event_staff_assignments(staff_id);
CREATE TABLE ticket_check_in_records (
    id UUID PRIMARY KEY,
    ticket_id UUID NOT NULL UNIQUE REFERENCES issued_tickets(id),
    event_id UUID NOT NULL REFERENCES events(id),
    booking_id UUID NOT NULL REFERENCES bookings(id),
    operator_id UUID NOT NULL REFERENCES users(id),
    checked_in_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_check_in_records_event ON ticket_check_in_records(event_id, checked_in_at DESC);
