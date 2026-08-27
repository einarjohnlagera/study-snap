CREATE TABLE linked_learner_grants (
    id              uuid PRIMARY KEY,
    relationship_id uuid NOT NULL REFERENCES linked_learner_relationships(id) ON DELETE CASCADE,
    from_user_id    uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    to_user_id      uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    scope           varchar(16) NOT NULL,
    granted_at      timestamptz NOT NULL,
    revoked_at      timestamptz,
    CONSTRAINT ck_linked_learner_grants_not_self CHECK (from_user_id <> to_user_id),
    CONSTRAINT ck_linked_learner_grants_scope CHECK (scope IN ('ACTIVITY', 'PROGRESS'))
);

CREATE UNIQUE INDEX ux_linked_learner_grants_live
    ON linked_learner_grants (relationship_id, from_user_id, scope) WHERE revoked_at IS NULL;

CREATE INDEX idx_linked_learner_grants_to_user
    ON linked_learner_grants (to_user_id, granted_at DESC) WHERE revoked_at IS NULL;
