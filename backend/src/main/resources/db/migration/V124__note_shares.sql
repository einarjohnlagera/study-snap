CREATE TABLE note_shares (
    id               uuid PRIMARY KEY,
    note_id          uuid NOT NULL REFERENCES notes(id) ON DELETE CASCADE,
    owner_user_id    uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    grantee_user_id  uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    relationship_id  uuid NOT NULL REFERENCES linked_learner_relationships(id) ON DELETE CASCADE,
    created_at       timestamptz NOT NULL,
    revoked_at       timestamptz,
    CONSTRAINT ck_note_shares_not_self CHECK (owner_user_id <> grantee_user_id)
);

CREATE UNIQUE INDEX ux_note_shares_live
    ON note_shares (note_id, grantee_user_id) WHERE revoked_at IS NULL;

CREATE INDEX idx_note_shares_grantee
    ON note_shares (grantee_user_id, created_at DESC) WHERE revoked_at IS NULL;

CREATE INDEX idx_note_shares_note ON note_shares (note_id) WHERE revoked_at IS NULL;
