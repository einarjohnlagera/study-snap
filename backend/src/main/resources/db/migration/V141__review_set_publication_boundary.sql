ALTER TABLE note_collection_items ADD COLUMN published_at TIMESTAMPTZ;
ALTER TABLE note_collections ADD COLUMN published_at TIMESTAMPTZ;
ALTER TABLE note_collections ADD COLUMN last_update_published_at TIMESTAMPTZ;

-- Every row that existed before this boundary is already part of the published curriculum.
-- This deliberately includes adopted copies: their stamp has no source-side meaning, but means no
-- pre-existing learner row can disappear if a later read is accidentally narrowed.
UPDATE note_collections
SET published_at = created_at
WHERE published_at IS NULL;

UPDATE note_collection_items i
SET published_at = c.created_at
FROM note_collections c
WHERE i.collection_id = c.id
  AND i.published_at IS NULL;

-- This is a curator finalization marker only for existing public source roots.  Keep the original
-- creation time so a future public "Updated" surface does not report this deployment date.
UPDATE note_collections
SET last_update_published_at = created_at
WHERE visibility = 'PUBLIC'
  AND parent_collection_id IS NULL
  AND source_plan_id IS NULL
  AND last_update_published_at IS NULL;
