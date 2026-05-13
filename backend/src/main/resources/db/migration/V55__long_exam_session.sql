CREATE UNIQUE INDEX IF NOT EXISTS idx_long_exam_sessions_one_active
    ON quick_review_sessions(user_id, study_pack_id)
    WHERE session_mode = 'LONG_EXAM' AND status IN ('IN_PROGRESS', 'PAUSED');
