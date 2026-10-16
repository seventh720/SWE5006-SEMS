-- Composite key prevents a ticket from referring to an order in another event.
ALTER TABLE bookings ADD CONSTRAINT uq_booking_event UNIQUE (id, event_id);
CREATE TABLE issued_tickets (
    id UUID PRIMARY KEY,
    booking_id UUID NOT NULL,
    event_id UUID NOT NULL,
    ordinal INTEGER NOT NULL CHECK (ordinal BETWEEN 1 AND 10),
    status VARCHAR(20) NOT NULL CHECK (status IN ('VALID', 'CHECKED_IN', 'VOID')),
    checked_in_at TIMESTAMPTZ,
    voided_at TIMESTAMPTZ,
    CONSTRAINT fk_ticket_booking FOREIGN KEY (booking_id, event_id) REFERENCES bookings(id, event_id),
    CONSTRAINT uq_ticket_ordinal UNIQUE (booking_id, ordinal),
    CONSTRAINT ck_ticket_state CHECK (
        (status = 'VALID' AND checked_in_at IS NULL AND voided_at IS NULL) OR
        (status = 'CHECKED_IN' AND checked_in_at IS NOT NULL AND voided_at IS NULL) OR
        (status = 'VOID' AND voided_at IS NOT NULL)
    )
);
CREATE INDEX idx_issued_tickets_event ON issued_tickets(event_id);
