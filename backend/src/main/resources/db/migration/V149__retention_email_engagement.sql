ALTER TABLE email_log
    ADD COLUMN clicked_at TIMESTAMPTZ;

CREATE TABLE email_open_daily_counts (
    event_date DATE PRIMARY KEY,
    open_count BIGINT NOT NULL CHECK (open_count >= 0)
);
