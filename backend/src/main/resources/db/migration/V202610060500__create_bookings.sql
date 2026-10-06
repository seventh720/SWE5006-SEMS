ALTER TABLE ticket_types ADD COLUMN sales_started BOOLEAN NOT NULL DEFAULT FALSE;
UPDATE ticket_types SET sales_started = TRUE WHERE booked_quantity > 0;

CREATE TABLE bookings (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    event_id UUID NOT NULL REFERENCES events(id),
    ticket_type_id UUID NOT NULL REFERENCES ticket_types(id),
    request_key VARCHAR(100) NOT NULL CHECK (length(trim(request_key)) > 0),
    quantity INTEGER NOT NULL CHECK (quantity BETWEEN 1 AND 10),
    unit_price_minor BIGINT NOT NULL CHECK (unit_price_minor = 0),
    total_amount_minor BIGINT NOT NULL CHECK (total_amount_minor = unit_price_minor * quantity),
    currency VARCHAR(3) NOT NULL CHECK (currency = 'SGD'),
    event_title VARCHAR(200) NOT NULL,
    event_location VARCHAR(500) NOT NULL,
    event_starts_at TIMESTAMPTZ NOT NULL,
    event_ends_at TIMESTAMPTZ NOT NULL,
    ticket_type_name VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('CONFIRMED', 'CANCELLED')),
    payment_status VARCHAR(20) NOT NULL CHECK (payment_status = 'NOT_REQUIRED'),
    cancellation_reason VARCHAR(30),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_booking_request UNIQUE (user_id, request_key),
    CONSTRAINT ck_booking_cancellation CHECK (
        (status = 'CONFIRMED' AND cancellation_reason IS NULL) OR
        (status = 'CANCELLED' AND cancellation_reason IS NOT NULL
            AND cancellation_reason IN ('ATTENDEE_CANCELLED', 'EVENT_CANCELLED'))
    )
);
CREATE INDEX idx_bookings_user ON bookings(user_id, created_at DESC, id DESC);
CREATE INDEX idx_bookings_event ON bookings(event_id, created_at DESC, id DESC);
