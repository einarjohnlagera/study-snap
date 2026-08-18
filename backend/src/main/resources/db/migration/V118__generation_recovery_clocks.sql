ALTER TABLE exam_question_pool
    ADD COLUMN generation_status_at TIMESTAMPTZ;

ALTER TABLE notes
    ADD COLUMN generation_enqueued_at TIMESTAMPTZ;

DO $$
DECLARE
    seeded_pool_count INTEGER;
BEGIN
    -- Pool rows are reused, so created_at is not a generation clock. Seed from deploy time to
    -- protect live work; genuinely stuck rows become eligible exactly one configured bound later.
    UPDATE exam_question_pool
       SET generation_status_at = now()
     WHERE generation_status IN ('PENDING', 'GENERATING')
       AND generation_status_at IS NULL;

    GET DIAGNOSTICS seeded_pool_count = ROW_COUNT;
    RAISE NOTICE 'V118: seeded generation_status_at with deploy time for % non-terminal exam question pools',
        seeded_pool_count;
END $$;

CREATE INDEX idx_exam_question_pool_generation_recovery
    ON exam_question_pool(generation_status, generation_status_at)
    WHERE generation_status IN ('PENDING', 'GENERATING');

CREATE INDEX idx_long_exam_session_generation_recovery
    ON quick_review_sessions(created_at)
    WHERE status = 'GENERATING' AND session_mode = 'LONG_EXAM';

CREATE INDEX idx_note_generation_recovery
    ON notes(generation_enqueued_at)
    WHERE status = 'GENERATING' AND generation_enqueued_at IS NOT NULL;
