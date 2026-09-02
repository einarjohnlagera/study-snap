-- A separate counter for Challenge Quiz sessions that draw from more than one note.
--
-- WHY. Challenge Quiz remains the same mode and its shared generation meter remains unchanged, but
-- v0.103.0 lets Free and Plus learners practise across a verified Study Plan. This counter enforces
-- the smaller per-plan monthly allowance for that capability without trying to infer it from JSON
-- session state (a PostgreSQL-specific predicate the repository tests could not exercise).
ALTER TABLE user_usage
    ADD COLUMN multi_note_generations integer DEFAULT 0 NOT NULL;

ALTER TABLE user_usage
    ADD CONSTRAINT ck_user_usage_multi_note_generations_non_negative
    CHECK (multi_note_generations >= 0);
