CREATE TABLE notifications (
    id UUID PRIMARY KEY,
    recipient_user_id UUID NOT NULL,
    type VARCHAR(64) NOT NULL,
    dedup_key VARCHAR(255) NOT NULL,
    title VARCHAR(255) NOT NULL,
    body VARCHAR(1000),
    cta_label VARCHAR(64),
    cta_path VARCHAR(512),
    announcement_id UUID,
    created_at TIMESTAMPTZ NOT NULL,
    read_at TIMESTAMPTZ,
    dismissed_at TIMESTAMPTZ
);

CREATE UNIQUE INDEX idx_notifications_recipient_dedup
    ON notifications(recipient_user_id, dedup_key);

CREATE INDEX idx_notifications_recipient_unread
    ON notifications(recipient_user_id, read_at);
