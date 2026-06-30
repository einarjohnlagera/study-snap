ALTER TABLE note_collections
    ADD COLUMN sibling_position INTEGER;

CREATE INDEX idx_note_collections_parent_sibling_position
    ON note_collections(parent_collection_id, sibling_position);
