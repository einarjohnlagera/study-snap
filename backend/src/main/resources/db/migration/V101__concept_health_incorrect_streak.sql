ALTER TABLE concept_health
    ADD COLUMN incorrect_streak INTEGER NOT NULL DEFAULT 0;

ALTER TABLE concept_health
    ADD CONSTRAINT chk_concept_health_incorrect_streak_nonnegative
        CHECK (incorrect_streak >= 0);
