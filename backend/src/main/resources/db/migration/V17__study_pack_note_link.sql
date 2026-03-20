ALTER TABLE study_packs
    ADD COLUMN IF NOT EXISTS note_id UUID;

DO
$$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_study_packs_note_id'
    ) THEN
        ALTER TABLE study_packs
            ADD CONSTRAINT fk_study_packs_note_id
                FOREIGN KEY (note_id) REFERENCES notes(id) ON DELETE SET NULL;
    END IF;
END
$$;

CREATE UNIQUE INDEX IF NOT EXISTS uq_study_packs_note_id
    ON study_packs(note_id)
    WHERE note_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_study_packs_owner_note_id
    ON study_packs(owner_user_id, note_id);
