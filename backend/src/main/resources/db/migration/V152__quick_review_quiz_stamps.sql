ALTER TABLE study_packs ADD COLUMN quiz_stamp BIGINT NOT NULL DEFAULT 0;
ALTER TABLE quick_review_sessions ADD COLUMN quiz_stamp_at_creation BIGINT;
