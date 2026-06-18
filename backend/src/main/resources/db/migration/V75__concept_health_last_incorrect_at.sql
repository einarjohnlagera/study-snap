ALTER TABLE concept_health
    ALTER COLUMN last_correct_at DROP NOT NULL,
    ADD COLUMN last_incorrect_at timestamptz NULL;
