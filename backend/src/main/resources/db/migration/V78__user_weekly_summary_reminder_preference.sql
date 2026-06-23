ALTER TABLE users
    ADD COLUMN weekly_summary_reminders_enabled BOOLEAN NOT NULL DEFAULT FALSE;
