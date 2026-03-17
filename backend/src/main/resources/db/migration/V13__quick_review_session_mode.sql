ALTER TABLE quick_review_sessions
    ADD COLUMN IF NOT EXISTS session_mode VARCHAR(32);

UPDATE quick_review_sessions
SET session_mode = 'QUICK_REVIEW'
WHERE session_mode IS NULL;

ALTER TABLE quick_review_sessions
    ALTER COLUMN session_mode SET NOT NULL;

DROP INDEX IF EXISTS idx_quick_review_sessions_one_in_progress;

CREATE UNIQUE INDEX IF NOT EXISTS idx_quick_review_sessions_one_in_progress
    ON quick_review_sessions(user_id, study_pack_id, session_mode)
    WHERE status = 'IN_PROGRESS';

CREATE INDEX IF NOT EXISTS idx_quick_review_sessions_user_mode_created
    ON quick_review_sessions(user_id, session_mode, created_at DESC);
