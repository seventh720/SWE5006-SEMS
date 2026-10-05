CREATE TABLE ticket_types (
                              id UUID PRIMARY KEY,
                              event_id UUID NOT NULL REFERENCES events(id) ON DELETE CASCADE,

                              name VARCHAR(100) NOT NULL
                                  CHECK (length(trim(name)) > 0),

                              price_minor BIGINT NOT NULL
                                  CHECK (price_minor >= 0),

                              currency VARCHAR(3) NOT NULL DEFAULT 'SGD'
                                  CHECK (currency = 'SGD'),

                              quota INTEGER NOT NULL
                                  CHECK (quota > 0),

                              booked_quantity INTEGER NOT NULL DEFAULT 0
                                  CHECK (booked_quantity >= 0),

                              version BIGINT NOT NULL DEFAULT 0,

                              created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                              updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

                              CONSTRAINT ck_ticket_types_booked_within_quota
                                  CHECK (booked_quantity <= quota)
);

CREATE INDEX idx_ticket_types_event
    ON ticket_types (event_id, created_at, id);