ALTER TABLE note_collections
    ADD COLUMN IF NOT EXISTS companion_structure_snapshot JSONB;
