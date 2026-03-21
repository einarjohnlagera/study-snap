ALTER TABLE notes
    ADD COLUMN IF NOT EXISTS status VARCHAR(16);

UPDATE notes n
SET status = 'GENERATED'
WHERE status IS NULL
  AND EXISTS (
      SELECT 1
      FROM study_packs sp
      WHERE sp.note_id = n.id
  );

UPDATE notes
SET status = 'DRAFT'
WHERE status IS NULL;

ALTER TABLE notes
    ALTER COLUMN status SET NOT NULL;

ALTER TABLE notes
    ALTER COLUMN status SET DEFAULT 'DRAFT';

DO
$$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'ck_notes_status'
    ) THEN
        ALTER TABLE notes
            ADD CONSTRAINT ck_notes_status
                CHECK (status IN ('DRAFT', 'GENERATED'));
    END IF;
END
$$;

ALTER TABLE notes
    ADD COLUMN IF NOT EXISTS source_note_id UUID;

DO
$$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_notes_source_note_id'
    ) THEN
        ALTER TABLE notes
            ADD CONSTRAINT fk_notes_source_note_id
                FOREIGN KEY (source_note_id) REFERENCES notes(id) ON DELETE SET NULL;
    END IF;
END
$$;

CREATE INDEX IF NOT EXISTS idx_notes_source_note_id
    ON notes(source_note_id);

DO
$$
DECLARE
    rec RECORD;
    created_note_id UUID;
    generated_content TEXT;
BEGIN
    FOR rec IN
        SELECT id,
               owner_user_id,
               title,
               subject,
               tags,
               source_text,
               summary,
               created_at,
               updated_at
        FROM study_packs
        WHERE note_id IS NULL
    LOOP
        generated_content := COALESCE(NULLIF(rec.source_text, ''), NULLIF(rec.summary, ''), 'Imported Study Pack content');

        INSERT INTO notes (
            id,
            owner_user_id,
            title,
            subject,
            tags,
            content,
            status,
            visibility,
            created_at,
            updated_at
        )
        VALUES (
            gen_random_uuid(),
            rec.owner_user_id,
            rec.title,
            rec.subject,
            COALESCE(rec.tags, '{}'::text[]),
            generated_content,
            'GENERATED',
            'PRIVATE',
            rec.created_at,
            COALESCE(rec.updated_at, rec.created_at)
        )
        RETURNING id INTO created_note_id;

        UPDATE study_packs
        SET note_id = created_note_id
        WHERE id = rec.id;
    END LOOP;
END
$$;

DO
$$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_study_packs_note_id'
    ) THEN
        ALTER TABLE study_packs
            DROP CONSTRAINT fk_study_packs_note_id;
    END IF;
END
$$;

ALTER TABLE study_packs
    ALTER COLUMN note_id SET NOT NULL;

ALTER TABLE study_packs
    ADD CONSTRAINT fk_study_packs_note_id
        FOREIGN KEY (note_id) REFERENCES notes(id) ON DELETE CASCADE;

DROP INDEX IF EXISTS uq_study_packs_note_id;

CREATE UNIQUE INDEX IF NOT EXISTS uq_study_packs_note_id
    ON study_packs(note_id);

UPDATE notes n
SET status = 'GENERATED'
WHERE EXISTS (
    SELECT 1
    FROM study_packs sp
    WHERE sp.note_id = n.id
);

UPDATE notes
SET status = 'DRAFT'
WHERE status IS NULL;

ALTER TABLE quick_review_sessions
    ADD COLUMN IF NOT EXISTS note_id UUID;

UPDATE quick_review_sessions q
SET note_id = sp.note_id
FROM study_packs sp
WHERE q.note_id IS NULL
  AND q.study_pack_id = sp.id;

ALTER TABLE quick_review_sessions
    ALTER COLUMN note_id SET NOT NULL;

DO
$$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_quick_review_sessions_note_id'
    ) THEN
        ALTER TABLE quick_review_sessions
            ADD CONSTRAINT fk_quick_review_sessions_note_id
                FOREIGN KEY (note_id) REFERENCES notes(id) ON DELETE CASCADE;
    END IF;
END
$$;

CREATE INDEX IF NOT EXISTS idx_quick_review_sessions_note_created
    ON quick_review_sessions(note_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_quick_review_sessions_user_note_mode_completed
    ON quick_review_sessions(user_id, note_id, session_mode, completed_at DESC);

CREATE UNIQUE INDEX IF NOT EXISTS idx_quick_review_sessions_one_in_progress_note
    ON quick_review_sessions(user_id, note_id, session_mode)
    WHERE status = 'IN_PROGRESS';

ALTER TABLE user_activity_events
    ADD COLUMN IF NOT EXISTS note_id UUID;

UPDATE user_activity_events ua
SET note_id = sp.note_id
FROM study_packs sp
WHERE ua.note_id IS NULL
  AND ua.study_pack_id = sp.id;

DO
$$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_user_activity_events_note_id'
    ) THEN
        ALTER TABLE user_activity_events
            ADD CONSTRAINT fk_user_activity_events_note_id
                FOREIGN KEY (note_id) REFERENCES notes(id) ON DELETE SET NULL;
    END IF;
END
$$;

CREATE INDEX IF NOT EXISTS idx_user_activity_events_note_created_at
    ON user_activity_events(note_id, created_at DESC);
