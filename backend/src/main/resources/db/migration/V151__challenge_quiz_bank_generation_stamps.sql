ALTER TABLE study_packs
    ADD COLUMN generation_stamp BIGINT NOT NULL DEFAULT 0;

-- Existing rows remain available until their pack's next regeneration removes them.
ALTER TABLE challenge_quiz_question_bank
    ADD COLUMN generation_stamp BIGINT;
