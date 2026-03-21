ALTER TABLE notes
    ADD COLUMN IF NOT EXISTS copied_from_note_id UUID;

ALTER TABLE notes
    ADD COLUMN IF NOT EXISTS copied_from_user_id UUID;

ALTER TABLE notes
    ADD COLUMN IF NOT EXISTS copied_from_title TEXT;

ALTER TABLE notes
    ADD COLUMN IF NOT EXISTS copied_from_public BOOLEAN;

ALTER TABLE notes
    ADD COLUMN IF NOT EXISTS copied_at TIMESTAMPTZ;

UPDATE notes
SET copied_from_public = FALSE
WHERE copied_from_public IS NULL;

ALTER TABLE notes
    ALTER COLUMN copied_from_public SET DEFAULT FALSE;

ALTER TABLE notes
    ALTER COLUMN copied_from_public SET NOT NULL;

DO
$$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_notes_copied_from_note_id'
    ) THEN
        ALTER TABLE notes
            ADD CONSTRAINT fk_notes_copied_from_note_id
                FOREIGN KEY (copied_from_note_id) REFERENCES notes(id) ON DELETE SET NULL;
    END IF;
END
$$;

DO
$$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_notes_copied_from_user_id'
    ) THEN
        ALTER TABLE notes
            ADD CONSTRAINT fk_notes_copied_from_user_id
                FOREIGN KEY (copied_from_user_id) REFERENCES users(id) ON DELETE SET NULL;
    END IF;
END
$$;

CREATE INDEX IF NOT EXISTS idx_notes_copied_from_note_id
    ON notes(copied_from_note_id);

CREATE INDEX IF NOT EXISTS idx_notes_copied_from_user_id
    ON notes(copied_from_user_id);
