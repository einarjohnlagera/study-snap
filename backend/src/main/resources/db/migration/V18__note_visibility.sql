ALTER TABLE notes
    ADD COLUMN IF NOT EXISTS visibility VARCHAR(16);

UPDATE notes
SET visibility = 'PRIVATE'
WHERE visibility IS NULL;

ALTER TABLE notes
    ALTER COLUMN visibility SET NOT NULL;

ALTER TABLE notes
    ALTER COLUMN visibility SET DEFAULT 'PRIVATE';

CREATE INDEX IF NOT EXISTS idx_notes_visibility_updated_at
    ON notes(visibility, updated_at DESC);
