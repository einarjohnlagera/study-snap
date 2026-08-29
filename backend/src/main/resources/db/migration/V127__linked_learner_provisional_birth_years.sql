-- A link redeemer's learner-declared year remains relationship-scoped until the link creator
-- confirms the pending relationship. It is not declaration history: acceptance promotes and
-- deletes it, revocation deletes it, and relationship/account deletion cascades it away.
CREATE TABLE linked_learner_provisional_birth_years (
    relationship_id uuid PRIMARY KEY
        REFERENCES linked_learner_relationships(id) ON DELETE CASCADE,
    birth_year integer NOT NULL,
    declared_at timestamptz NOT NULL,
    CONSTRAINT ck_linked_learner_provisional_birth_year_plausible
        CHECK (birth_year BETWEEN 1900 AND 9999)
);
