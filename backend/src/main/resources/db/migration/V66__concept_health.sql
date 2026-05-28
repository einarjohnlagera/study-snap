CREATE TABLE concept_health (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    study_pack_id UUID NOT NULL REFERENCES study_packs(id) ON DELETE CASCADE,
    concept VARCHAR(500) NOT NULL,
    last_correct_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_concept_health_user_study_pack_concept
        UNIQUE (user_id, study_pack_id, concept)
);

CREATE INDEX idx_concept_health_user_study_pack
    ON concept_health (user_id, study_pack_id);
