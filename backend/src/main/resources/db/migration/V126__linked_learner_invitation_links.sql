-- Single-use, address-free invitations for Learning Connections. These rows deliberately live
-- outside linked_learner_invitations: an invitation link names no recipient address, and mixing
-- the two would weaken the email invitation's live-row uniqueness and pollute its TTL checkpoint.
CREATE TABLE linked_learner_invitation_links (
    id uuid PRIMARY KEY,
    token varchar(22) NOT NULL,
    creator_user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    creator_role varchar(16) NOT NULL,
    created_at timestamptz NOT NULL,
    expires_at timestamptz NOT NULL,
    revoked_at timestamptz,
    redeemed_at timestamptz,
    redeemed_by_user_id uuid REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT ck_linked_learner_invitation_link_role
        CHECK (creator_role IN ('SUPPORTER', 'LEARNER')),
    CONSTRAINT ck_linked_learner_invitation_link_redemption
        CHECK ((redeemed_at IS NULL) = (redeemed_by_user_id IS NULL)),
    CONSTRAINT ck_linked_learner_invitation_link_self_redemption
        CHECK (redeemed_by_user_id IS NULL OR redeemed_by_user_id <> creator_user_id),
    CONSTRAINT ck_linked_learner_invitation_link_terminal_state
        CHECK (revoked_at IS NULL OR redeemed_at IS NULL)
);

-- Tokens are never deliberately reused, including after terminal state, but the live-row index is
-- the database invariant required by the redemption predicate itself.
CREATE UNIQUE INDEX ux_linked_learner_invitation_link_live_token
    ON linked_learner_invitation_links (token)
    WHERE revoked_at IS NULL AND redeemed_at IS NULL;

CREATE INDEX idx_linked_learner_invitation_link_creator_live
    ON linked_learner_invitation_links (creator_user_id, created_at DESC)
    WHERE revoked_at IS NULL AND redeemed_at IS NULL;
