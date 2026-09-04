ALTER TABLE quick_review_sessions
    ALTER COLUMN study_pack_id DROP NOT NULL,
    ALTER COLUMN note_id DROP NOT NULL,
    ADD COLUMN source_collection_id UUID
        REFERENCES note_collections(id) ON DELETE CASCADE;

ALTER TABLE quick_review_sessions
    ADD CONSTRAINT chk_quick_review_sessions_anchor
        CHECK (
            (study_pack_id IS NOT NULL AND note_id IS NOT NULL)
            OR source_collection_id IS NOT NULL
        ) NOT VALID;

ALTER TABLE quick_review_sessions
    VALIDATE CONSTRAINT chk_quick_review_sessions_anchor;

DROP INDEX IF EXISTS idx_quick_review_sessions_one_active_generation;
DROP INDEX IF EXISTS idx_quick_review_sessions_one_active_generation_note;

CREATE UNIQUE INDEX idx_quick_review_sessions_one_active_generation
    ON quick_review_sessions(user_id, study_pack_id, session_mode)
    WHERE status IN ('GENERATING', 'IN_PROGRESS')
      AND study_pack_id IS NOT NULL;

CREATE UNIQUE INDEX idx_quick_review_sessions_one_active_generation_note
    ON quick_review_sessions(user_id, note_id, session_mode)
    WHERE status IN ('GENERATING', 'IN_PROGRESS')
      AND note_id IS NOT NULL;

CREATE UNIQUE INDEX idx_quick_review_sessions_one_active_generation_collection
    ON quick_review_sessions(user_id, source_collection_id, session_mode)
    WHERE status IN ('GENERATING', 'IN_PROGRESS')
      AND source_collection_id IS NOT NULL;

CREATE INDEX idx_quick_review_sessions_source_collection
    ON quick_review_sessions(source_collection_id);
