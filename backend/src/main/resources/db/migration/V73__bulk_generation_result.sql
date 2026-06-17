CREATE TABLE bulk_generation_result (
    id uuid PRIMARY KEY,
    owner_user_id uuid NOT NULL,
    subject text NOT NULL,
    course_program text,
    target_profile_type text NOT NULL,
    make_public boolean NOT NULL,
    requested_count integer NOT NULL,
    created_count integer NOT NULL,
    failed_topics jsonb NOT NULL,
    created_at timestamptz NOT NULL
);

CREATE INDEX idx_bulk_generation_result_owner_user_id
    ON bulk_generation_result(owner_user_id);

CREATE INDEX idx_bulk_generation_result_created_at
    ON bulk_generation_result(created_at);
