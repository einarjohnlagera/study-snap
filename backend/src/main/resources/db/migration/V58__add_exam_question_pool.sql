CREATE TABLE IF NOT EXISTS exam_question_pool (
    id UUID PRIMARY KEY,
    study_pack_id UUID NOT NULL,
    mode VARCHAR(20) NOT NULL,
    questions JSONB NOT NULL DEFAULT '[]'::jsonb,
    pool_size INTEGER NOT NULL DEFAULT 0,
    generation_status VARCHAR(20) NOT NULL,
    learner_level VARCHAR(50),
    served_question_keys JSONB NOT NULL DEFAULT '[]'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    refreshed_at TIMESTAMPTZ,
    CONSTRAINT fk_exam_question_pool_study_pack
        FOREIGN KEY (study_pack_id)
        REFERENCES study_packs(id)
        ON DELETE CASCADE,
    CONSTRAINT chk_exam_question_pool_mode
        CHECK (mode IN ('LONG_EXAM', 'BOARD_EXAM')),
    CONSTRAINT chk_exam_question_pool_generation_status
        CHECK (generation_status IN ('PENDING', 'GENERATING', 'READY', 'FAILED')),
    CONSTRAINT uq_exam_question_pool_study_pack_mode
        UNIQUE (study_pack_id, mode)
);

CREATE INDEX IF NOT EXISTS idx_exam_question_pool_study_pack
    ON exam_question_pool(study_pack_id);
