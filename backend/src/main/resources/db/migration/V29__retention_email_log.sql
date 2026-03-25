CREATE TABLE email_log (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    email_type VARCHAR(32) NOT NULL,
    sent_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_email_log_user_type_sent_at
    ON email_log (user_id, email_type, sent_at DESC);

CREATE INDEX idx_email_log_sent_at
    ON email_log (sent_at DESC);
