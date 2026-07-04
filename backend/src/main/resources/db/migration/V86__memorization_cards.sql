CREATE TABLE memorization_cards (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    study_pack_id UUID NOT NULL REFERENCES study_packs(id) ON DELETE CASCADE,
    concept VARCHAR(500) NOT NULL,
    interval_days INTEGER NOT NULL DEFAULT 0,
    ease_factor NUMERIC(4, 2) NOT NULL DEFAULT 2.50,
    repetitions INTEGER NOT NULL DEFAULT 0,
    due_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_reviewed_at TIMESTAMPTZ NULL,
    last_grade VARCHAR(16) NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_memorization_cards_user_study_pack_concept
        UNIQUE (user_id, study_pack_id, concept),
    CONSTRAINT chk_memorization_cards_last_grade
        CHECK (last_grade IS NULL OR last_grade IN ('AGAIN', 'HARD', 'GOOD', 'EASY')),
    CONSTRAINT chk_memorization_cards_interval_non_negative
        CHECK (interval_days >= 0),
    CONSTRAINT chk_memorization_cards_repetitions_non_negative
        CHECK (repetitions >= 0),
    CONSTRAINT chk_memorization_cards_ease_floor
        CHECK (ease_factor >= 1.30)
);

CREATE INDEX idx_memorization_cards_user_study_pack
    ON memorization_cards (user_id, study_pack_id);

CREATE INDEX idx_memorization_cards_user_study_pack_due_at
    ON memorization_cards (user_id, study_pack_id, due_at);
