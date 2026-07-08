ALTER TABLE note_collections
    ADD COLUMN IF NOT EXISTS companion JSONB;
