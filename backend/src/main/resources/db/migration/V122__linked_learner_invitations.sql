-- Email-keyed invitations. Before this, an invite to an address with no account wrote NOTHING while
-- an invite to a real account wrote a PENDING relationship row visible in the inviter's own list —
-- so any authenticated user could test whether an email had an account by inviting it and reading
-- their list. The generic response did not help, because the observable STATE differed.
--
-- An invitation is now keyed on the typed address and is always written, so there is no branch on
-- account existence to observe. It also lets someone invite a person who has not signed up yet.
--
-- ⚠️ Relationships are created ONLY on acceptance. linked_learner_relationships therefore keeps its
-- meaning, which matters because [CHECKPOINT — due 2026-09-19] counts ACCEPTED rows there. An
-- unresolved invitation is NOT a connection and must never be counted as one.
CREATE TABLE linked_learner_invitations (
    id uuid PRIMARY KEY,
    inviter_user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    invited_email varchar(320) NOT NULL,
    inviter_role varchar(16) NOT NULL,
    status varchar(16) NOT NULL,
    created_at timestamptz NOT NULL,
    accepted_at timestamptz,
    revoked_at timestamptz,
    CONSTRAINT ck_linked_learner_invitation_status CHECK (status IN ('PENDING', 'ACCEPTED', 'REVOKED')),
    CONSTRAINT ck_linked_learner_invitation_role CHECK (inviter_role IN ('SUPPORTER', 'LEARNER')),
    CONSTRAINT ck_linked_learner_invitation_email_lower CHECK (invited_email = lower(invited_email))
);

-- One live invitation per inviter+address. A REVOKED or ACCEPTED row must not block a fresh invite.
CREATE UNIQUE INDEX ux_linked_learner_invitation_live
    ON linked_learner_invitations (inviter_user_id, invited_email)
    WHERE status = 'PENDING';

-- Incoming lookup is by address: a recipient pulls their invitations on login, so no signup hook
-- is needed and an invitation can predate the account entirely.
CREATE INDEX idx_linked_learner_invitation_email
    ON linked_learner_invitations (invited_email, status);

CREATE INDEX idx_linked_learner_invitation_inviter
    ON linked_learner_invitations (inviter_user_id, created_at DESC);
