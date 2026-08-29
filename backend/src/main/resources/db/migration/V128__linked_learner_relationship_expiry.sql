ALTER TABLE linked_learner_relationships
    ADD COLUMN expires_at timestamptz;

-- The first possible production relationship predates this migration. Backfill from created_at,
-- never migration/deploy time, so every request gets the same 30-day minimum window and the
-- 2026-09-26 provisional-row checkpoint remains answerable.
UPDATE linked_learner_relationships
   SET expires_at = created_at + interval '30 days'
 WHERE status = 'PENDING';

ALTER TABLE linked_learner_relationships
    DROP CONSTRAINT ck_linked_learner_status;

ALTER TABLE linked_learner_relationships
    ADD CONSTRAINT ck_linked_learner_status
        CHECK (status IN ('PENDING', 'ACCEPTED', 'REVOKED', 'EXPIRED'));

-- The invitation table has its own status constraint and deliberately remains
-- PENDING | ACCEPTED | REVOKED. Invitation expiry is expressed by its expires_at column.
CREATE INDEX idx_linked_learner_pending_expiry
    ON linked_learner_relationships (expires_at, id)
    WHERE status = 'PENDING' AND expires_at IS NOT NULL;
