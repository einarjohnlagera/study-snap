CREATE TABLE IF NOT EXISTS quick_review_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    study_pack_id UUID NOT NULL REFERENCES study_packs(id) ON DELETE CASCADE,
    total_questions INTEGER NOT NULL,
    correct_answers INTEGER,
    score_percentage NUMERIC(5, 2),
    retry_count INTEGER,
    duration_seconds INTEGER,
    session_metadata JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_quick_review_sessions_user_study_pack_completed
    ON quick_review_sessions(user_id, study_pack_id, completed_at DESC);

CREATE INDEX IF NOT EXISTS idx_quick_review_sessions_study_pack_created
    ON quick_review_sessions(study_pack_id, created_at DESC);
