ALTER TABLE users
    ADD COLUMN due_concepts_digest_reminders_enabled BOOLEAN NOT NULL DEFAULT FALSE;
