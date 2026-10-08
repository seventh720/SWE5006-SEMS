ALTER TABLE events ADD COLUMN illustration VARCHAR(20) NOT NULL DEFAULT 'GENERAL';
ALTER TABLE events ADD CONSTRAINT ck_events_illustration CHECK (illustration IN ('GENERAL', 'TECH', 'MUSIC', 'SPORT', 'ART', 'SOCIAL'));
ALTER TABLE users ADD COLUMN avatar_data TEXT;
