ALTER TABLE users
    ADD COLUMN birth_year smallint;

ALTER TABLE users
    ADD CONSTRAINT ck_users_birth_year_plausible
        CHECK (birth_year IS NULL OR birth_year BETWEEN 1900 AND 9999);

CREATE TABLE linked_learner_relationships (
    id uuid PRIMARY KEY,
    supporter_user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    learner_user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    status varchar(16) NOT NULL,
    initiated_by varchar(16) NOT NULL,
    created_at timestamptz NOT NULL,
    accepted_at timestamptz,
    revoked_at timestamptz,
    CONSTRAINT ck_linked_learner_not_self CHECK (supporter_user_id <> learner_user_id),
    CONSTRAINT ck_linked_learner_status CHECK (status IN ('PENDING', 'ACCEPTED', 'REVOKED')),
    CONSTRAINT ck_linked_learner_initiator CHECK (initiated_by IN ('SUPPORTER', 'LEARNER'))
);

CREATE UNIQUE INDEX ux_linked_learner_live_direction
    ON linked_learner_relationships (supporter_user_id, learner_user_id)
    WHERE status IN ('PENDING', 'ACCEPTED');

CREATE INDEX idx_linked_learner_supporter
    ON linked_learner_relationships (supporter_user_id, created_at DESC);

CREATE INDEX idx_linked_learner_learner
    ON linked_learner_relationships (learner_user_id, created_at DESC);

CREATE TABLE linked_learner_guardian_consents (
    id uuid PRIMARY KEY,
    relationship_id uuid NOT NULL UNIQUE REFERENCES linked_learner_relationships(id) ON DELETE CASCADE,
    learner_user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    attested_by_user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    attested_at timestamptz NOT NULL,
    attestation_version varchar(64) NOT NULL
);
