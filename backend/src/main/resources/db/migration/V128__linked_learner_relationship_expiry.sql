ALTER TABLE linked_learner_relationships
    ADD COLUMN expires_at timestamptz;

-- ⚠️ NO BACKFILL, DELIBERATELY. Inherited PENDING rows keep a NULL expires_at.
--
-- This was written twice and both earlier forms were wrong; the reasoning is recorded so a third
-- attempt does not reintroduce them.
--
-- For rows created AFTER this migration, a consent-paused relationship is structurally unexpirable:
-- acceptance clears expires_at, pauseAcceptedForConsent leaves it clear, and markExpiredIfPending
-- requires `expires_at is not null`. The protection IS the NULL.
--
-- An inherited paused row is PENDING with a NULL expires_at only because the column did not exist,
-- and it is indistinguishable from an unconfirmed request: pauseAcceptedForConsent nulls
-- accepted_at too, so nothing on the row records that it was once ACCEPTED.
--   * `created_at + interval '30 days'` gives it a deadline already in the PAST — the first sweep
--     terminates a previously-ACCEPTED connection, cutting grants, with recordGuardianConsent
--     requiring PENDING so the consent repair path is gone.
--   * `greatest(created_at, now()) + interval '30 days'` merely DELAYS that by 30 days. It looks
--     safe and is not: it still sets expires_at, which erases the one thing distinguishing a
--     protected pause from an expirable request, permanently.
--
-- Writing nothing is what makes an inherited row behave exactly like a runtime paused row.
--
-- ⚠️ THE COST, STATED: an unconfirmed request created before this deploy never expires. That
-- population is bounded by the gap between the v0.95.0 deploy (2026-08-29, when provisional rows
-- first became possible) and this one, against a table that was EMPTY in production on 2026-08-26.
-- A later targeted sweep can address any that exist; silently expiring confirmed connections to
-- avoid that is the worse trade.
--
-- ⚠️ The runtime clock is unaffected: createPendingRelationship still sets
-- created_at + request-ttl-days, which is the dated-read constraint.

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
