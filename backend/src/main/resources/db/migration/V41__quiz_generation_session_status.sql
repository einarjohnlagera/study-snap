DROP INDEX IF EXISTS idx_quick_review_sessions_one_in_progress;
DROP INDEX IF EXISTS idx_quick_review_sessions_one_in_progress_note;

CREATE UNIQUE INDEX IF NOT EXISTS idx_quick_review_sessions_one_active_generation
    ON quick_review_sessions(user_id, study_pack_id, session_mode)
    WHERE status IN ('GENERATING', 'IN_PROGRESS');

CREATE UNIQUE INDEX IF NOT EXISTS idx_quick_review_sessions_one_active_generation_note
    ON quick_review_sessions(user_id, note_id, session_mode)
    WHERE status IN ('GENERATING', 'IN_PROGRESS');
