CREATE TABLE IF NOT EXISTS suppressed_email (
    address VARCHAR(320) PRIMARY KEY,
    reason VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

UPDATE users
SET inactivity_reminders_enabled = TRUE
WHERE inactivity_reminders_enabled = FALSE;
