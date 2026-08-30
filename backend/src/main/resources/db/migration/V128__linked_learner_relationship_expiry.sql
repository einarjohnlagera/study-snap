ALTER TABLE linked_learner_relationships
    ADD COLUMN expires_at timestamptz;

-- ⚠️ greatest(created_at, now()), NOT created_at. Found by the pre-signoff pressure test, and the
-- naive form was a real defect rather than a style point.
--
-- The pause-safety mechanism for NEW rows is that acceptance clears expires_at to NULL and
-- pauseAcceptedForConsent leaves it NULL, so markExpiredIfPending's `expires_at is not null` guard
-- makes a consent-paused relationship structurally unexpirable. That argument does not reach rows
-- this migration inherits: a relationship paused BEFORE V128 is PENDING with a NULL expires_at
-- simply because the column did not exist, which is indistinguishable from an unconfirmed request.
-- Backfilling created_at + 30 days hands such a row a deadline already in the past, and the first
-- sweep terminates a connection that had been ACCEPTED and merely awaited re-consent — EXPIRED is
-- terminal, so the guardian-consent repair path would be gone.
--
-- greatest(created_at, now()) gives every inherited row a full window in which re-acceptance clears
-- the deadline, and prevents a zero-notice mass expiry of legitimate old requests on the first
-- sweep. It also moves the earliest possible expiry LATER, which is strictly safer for the
-- 2026-09-26 provisional-row read, not riskier.
--
-- ⚠️ This governs the MIGRATION only. The runtime clock for new requests stays
-- `created_at + request-ttl-days` in createPendingRelationship, which is the dated-read constraint.
UPDATE linked_learner_relationships
   SET expires_at = greatest(created_at, now()) + interval '30 days'
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
