CREATE TABLE notifications (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    type VARCHAR(40) NOT NULL CHECK (type IN ('STAFF_ASSIGNED', 'STAFF_REVOKED', 'BOOKING_CONFIRMED',
        'BOOKING_CANCELLED', 'REGISTRATION_OPENING_SOON', 'EVENT_UPDATED', 'EVENT_CANCELLED')),
    title VARCHAR(250) NOT NULL,
    message TEXT NOT NULL,
    event_id UUID,
    booking_id UUID,
    ticket_type_id UUID,
    source_key VARCHAR(250) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    read_at TIMESTAMPTZ,
    CONSTRAINT uq_notification_source UNIQUE (user_id, type, source_key)
);
CREATE INDEX idx_notifications_user ON notifications(user_id, created_at DESC, id DESC);
CREATE INDEX idx_notifications_unread ON notifications(user_id) WHERE read_at IS NULL;
