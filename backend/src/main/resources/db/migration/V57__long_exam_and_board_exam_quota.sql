ALTER TABLE user_usage
    ADD COLUMN IF NOT EXISTS long_exam_used_this_month INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS board_exam_used_this_month INTEGER NOT NULL DEFAULT 0;
