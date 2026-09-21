CREATE TABLE events (
    id UUID PRIMARY KEY,
    organizer_id UUID NOT NULL REFERENCES users(id),
    title VARCHAR(200) NOT NULL CHECK (length(trim(title)) > 0),
    description VARCHAR(10000) NOT NULL CHECK (length(trim(description)) > 0),
    location VARCHAR(500) NOT NULL CHECK (length(trim(location)) > 0),
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at TIMESTAMPTZ NOT NULL,
    capacity INTEGER NOT NULL CHECK (capacity > 0),
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN ('DRAFT', 'PUBLISHED', 'CANCELLED')),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_events_time_range CHECK (starts_at < ends_at)
);

CREATE INDEX idx_events_published_start ON events (starts_at, id) WHERE status = 'PUBLISHED';
CREATE INDEX idx_events_organizer_created ON events (organizer_id, created_at DESC, id DESC);
