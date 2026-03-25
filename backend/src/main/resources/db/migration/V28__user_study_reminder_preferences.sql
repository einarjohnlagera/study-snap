ALTER TABLE users
    ADD COLUMN inactivity_reminders_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN weak_concept_reminders_enabled BOOLEAN NOT NULL DEFAULT FALSE;
