-- v0.116.0 -- additive Official Review Set updates.
--
-- Source-at-sync facts are ordinary typed columns rather than an opaque JSON snapshot. The learner's
-- current title, label and position are deliberately NOT part of this baseline: drift compares the
-- source fact saved here with the source fact now, so learner customization can never be mistaken for
-- an upstream change.
ALTER TABLE note_collections
    ADD COLUMN source_title_at_sync VARCHAR(150),
    ADD COLUMN source_parent_id_at_sync UUID,
    ADD COLUMN source_position_at_sync INTEGER,
    ADD COLUMN source_synced_at TIMESTAMPTZ;

ALTER TABLE note_collection_items
    ADD COLUMN source_label_at_sync VARCHAR(120),
    ADD COLUMN source_position_at_sync INTEGER,
    ADD COLUMN source_synced_at TIMESTAMPTZ;

ALTER TABLE note_collections
    ADD CONSTRAINT ck_note_collections_source_position_at_sync_non_negative
        CHECK (source_position_at_sync IS NULL OR source_position_at_sync >= 0);

ALTER TABLE note_collection_items
    ADD CONSTRAINT ck_note_collection_items_source_position_at_sync_non_negative
        CHECK (source_position_at_sync IS NULL OR source_position_at_sync >= 0);

-- A tombstone has no FK to either source row. Source deletion is a supported state, and retaining the
-- composite source identity is what prevents an intentionally removed placement from being recreated.
-- There is intentionally no surrogate placement id: durable placement identity is
-- (source Subject Plan, source Note), scoped to the adopted collection.
CREATE TABLE note_collection_item_removals (
    adopted_collection_id UUID NOT NULL
        REFERENCES note_collections(id) ON DELETE CASCADE,
    source_plan_id UUID NOT NULL,
    source_note_id UUID NOT NULL,
    removed_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (adopted_collection_id, source_plan_id, source_note_id)
);

CREATE INDEX idx_note_collection_item_removals_source_placement
    ON note_collection_item_removals(source_plan_id, source_note_id);

-- Existing adoption baselines can only record source facts that are still reconstructable today.
-- Missing/deleted source rows remain NULL instead of fabricating a relationship. The tombstone table
-- intentionally receives no backfill: the measured production population contains zero removals.
UPDATE note_collections adopted
SET source_title_at_sync = source.title,
    source_parent_id_at_sync = source.parent_collection_id,
    source_position_at_sync = source.sibling_position,
    source_synced_at = adopted.created_at
FROM note_collections source
WHERE adopted.source_plan_id = source.id;

UPDATE note_collection_items adopted_item
SET source_label_at_sync = source_item.label,
    source_position_at_sync = source_item.position,
    source_synced_at = adopted_collection.created_at
FROM note_collections adopted_collection,
     notes copied_note,
     note_collection_items source_item
WHERE adopted_item.collection_id = adopted_collection.id
  AND adopted_collection.source_plan_id IS NOT NULL
  AND copied_note.id = adopted_item.note_id
  AND source_item.collection_id = adopted_collection.source_plan_id
  AND source_item.note_id = COALESCE(copied_note.copied_from_note_id, copied_note.source_note_id);
