ALTER TABLE user_usage
    ADD COLUMN IF NOT EXISTS interview_practice_used_this_month INTEGER NOT NULL DEFAULT 0;
