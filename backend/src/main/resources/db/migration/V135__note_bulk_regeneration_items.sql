-- v0.119.0 -- curator bulk regeneration, slice B1.
--
-- THE ONE MIGRATION THIS FEATURE OWES. Bulk regeneration cannot reuse `bulk_generation_result`:
-- that receipt is written ONCE in processBatch's finally, is consume-once (read deletes the row) and
-- is keyed by TOPIC STRING. A regeneration batch runs two LLM calls per item, so a 50-note batch
-- runs far past the client poller's ~5 minute ceiling and the terminal-blob receipt is never read.
-- This table is the opposite shape: ONE ROW PER ITEM, written as that item resolves.
--
-- ⚠️ NO FOREIGN KEY TO notes. A note deleted mid-batch must still leave a readable row (NOT_RUN,
-- failure matrix row 7); a cascade would erase the very fact the receipt exists to report.
--
-- ⚠️ batch_created_at IS THE TTL CLOCK, NOT updated_at. Sweeping on updated_at would expire a long
-- batch's early items while its late items survive, leaving the curator a receipt with holes in it.
-- Every row in a batch carries the same batch_created_at, so a batch expires atomically -- under the
-- SAME 24 h TTL and the same hourly :45 sweep the existing receipt already uses. This is deliberately
-- NOT permanent audit history.
CREATE TABLE note_bulk_regeneration_item (
    id uuid PRIMARY KEY,
    batch_id uuid NOT NULL,
    owner_user_id uuid NOT NULL,
    note_id uuid NOT NULL,
    scope text NOT NULL,
    state text NOT NULL,
    reason_code text,
    reason text,
    share_link_deactivated boolean NOT NULL DEFAULT false,
    batch_created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT uq_note_bulk_regeneration_item_batch_note UNIQUE (batch_id, note_id),
    CONSTRAINT ck_note_bulk_regeneration_item_scope
        CHECK (scope IN ('STUDY_PACK', 'NOTE_AND_STUDY_PACK')),
    CONSTRAINT ck_note_bulk_regeneration_item_state
        CHECK (state IN ('PENDING', 'RUNNING', 'REGENERATED', 'BLOCKED', 'FAILED', 'NOT_RUN'))
);

CREATE INDEX idx_note_bulk_regeneration_item_batch_id
    ON note_bulk_regeneration_item(batch_id);

CREATE INDEX idx_note_bulk_regeneration_item_owner_user_id
    ON note_bulk_regeneration_item(owner_user_id);

CREATE INDEX idx_note_bulk_regeneration_item_batch_created_at
    ON note_bulk_regeneration_item(batch_created_at);
