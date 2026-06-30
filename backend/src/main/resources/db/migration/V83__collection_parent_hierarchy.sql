ALTER TABLE note_collections
    ADD COLUMN parent_collection_id UUID NULL REFERENCES note_collections(id) ON DELETE SET NULL;

CREATE INDEX idx_note_collections_parent_collection_id
    ON note_collections(parent_collection_id);
