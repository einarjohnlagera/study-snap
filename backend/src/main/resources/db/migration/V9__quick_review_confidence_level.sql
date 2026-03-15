ALTER TABLE quick_review_sessions
    ADD COLUMN IF NOT EXISTS confidence_level VARCHAR(16);
