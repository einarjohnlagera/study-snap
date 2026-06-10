ALTER TABLE users
    ADD COLUMN focus_subjects TEXT[] NOT NULL DEFAULT '{}'::TEXT[];
