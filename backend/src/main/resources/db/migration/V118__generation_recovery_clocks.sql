ALTER TABLE exam_question_pool
    ADD COLUMN generation_status_at TIMESTAMPTZ;

ALTER TABLE notes
    ADD COLUMN generation_enqueued_at TIMESTAMPTZ;

DO $$
DECLARE
    seeded_pool_count INTEGER;
    seeded_note_count INTEGER;
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

    -- Notes get the same treatment, for the same reason. Production sizing found zero stuck notes,
    -- but that was measured BEFORE this deploy — and the deploy itself is precisely the event that
    -- strands in-flight generation, because the generation executor takes no drain on shutdown. A
    -- note left GENERATING by this very deploy would keep a NULL clock, and the sweep predicate
    -- requires `generation_enqueued_at < cutoff`, which NULL never satisfies: unrecoverable forever,
    -- while warning every ten minutes. Seeding from deploy time makes it eligible one bound later,
    -- exactly as it does for pools. Expected row count in production: 0.
    UPDATE notes
       SET generation_enqueued_at = now()
     WHERE status = 'GENERATING'
       AND generation_enqueued_at IS NULL;

    GET DIAGNOSTICS seeded_note_count = ROW_COUNT;
    RAISE NOTICE 'V118: seeded generation_enqueued_at with deploy time for % notes still GENERATING',
        seeded_note_count;
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
