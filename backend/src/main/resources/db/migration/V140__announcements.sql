CREATE TABLE announcements (
    id UUID PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    body VARCHAR(1000) NOT NULL,
    cta_label VARCHAR(64),
    cta_path VARCHAR(512),
    audience VARCHAR(32) NOT NULL,
    audience_value VARCHAR(64),
    status VARCHAR(16) NOT NULL,
    published_at TIMESTAMPTZ,
    expires_at TIMESTAMPTZ,
    created_by_user_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

-- The admin list reads newest-first; plain CREATE INDEX, never CONCURRENTLY, because Flyway runs
-- migrations inside a transaction and CONCURRENTLY cannot.
CREATE INDEX idx_announcements_status_created_at
    ON announcements(status, created_at DESC);
