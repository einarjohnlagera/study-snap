CREATE INDEX idx_note_collections_source_plan_id
    ON note_collections(source_plan_id)
    WHERE source_plan_id IS NOT NULL;
