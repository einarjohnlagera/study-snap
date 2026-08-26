-- Invitations are keyed to an ADDRESS, not an account (see V122), which means an invitation is a
-- standing offer to whoever controls that address. Without an expiry that offer never lapses: a
-- reassigned school or corporate mailbox hands its new owner the ability to accept a connection
-- someone extended to a different person years earlier. That is inherent to email-keying, so the
-- bound has to be explicit rather than assumed.
--
-- ⚠️ expires_at is a real column rather than a derived created_at + interval. Deriving it would
-- force re-arming an expired invitation to overwrite created_at, and created_at is load-bearing
-- elsewhere: idx_linked_learner_invitation_inviter orders on it, the invitation list displays it,
-- and it is the only record of when an address was FIRST invited. Re-arming must not reset that.
ALTER TABLE linked_learner_invitations
    ADD COLUMN expires_at timestamptz;

-- Backfill every existing row from its own creation time, so no invitation is retroactively
-- expired and none is left unbounded. The interval matches the shipped default TTL.
UPDATE linked_learner_invitations
SET expires_at = created_at + interval '30 days'
WHERE expires_at IS NULL;

ALTER TABLE linked_learner_invitations
    ALTER COLUMN expires_at SET NOT NULL;

-- The recipient's lookup filters on expiry, so it joins the existing address index.
CREATE INDEX idx_linked_learner_invitation_email_live
    ON linked_learner_invitations (invited_email, status, expires_at);
