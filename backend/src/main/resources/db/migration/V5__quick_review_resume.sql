ALTER TABLE quick_review_sessions
    ADD COLUMN IF NOT EXISTS status VARCHAR(32),
    ADD COLUMN IF NOT EXISTS current_question_index INTEGER,
    ADD COLUMN IF NOT EXISTS current_round VARCHAR(16),
    ADD COLUMN IF NOT EXISTS session_state JSONB;

UPDATE quick_review_sessions
SET status = CASE
    WHEN completed_at IS NULL THEN 'IN_PROGRESS'
    ELSE 'COMPLETED'
END
WHERE status IS NULL;

UPDATE quick_review_sessions
SET current_question_index = 0
WHERE current_question_index IS NULL;

UPDATE quick_review_sessions
SET current_round = 'INITIAL'
WHERE current_round IS NULL;

ALTER TABLE quick_review_sessions
    ALTER COLUMN status SET NOT NULL,
    ALTER COLUMN current_question_index SET NOT NULL,
    ALTER COLUMN current_round SET NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS idx_quick_review_sessions_one_in_progress
    ON quick_review_sessions(user_id, study_pack_id)
    WHERE status = 'IN_PROGRESS';
