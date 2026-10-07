CREATE TABLE goal_adoption_jobs (
    id uuid PRIMARY KEY,
    goal_id uuid NOT NULL UNIQUE REFERENCES note_collections(id) ON DELETE CASCADE,
    owner_user_id uuid NOT NULL,
    source_goal_id uuid NOT NULL,
    source_child_ids jsonb NOT NULL,
    status varchar(16) NOT NULL CHECK (status IN ('PENDING', 'RUNNING', 'COMPLETED', 'FAILED')),
    run_token uuid,
    processed_subject_count integer NOT NULL DEFAULT 0,
    adopted_subject_count integer NOT NULL DEFAULT 0,
    skipped_subject_count integer NOT NULL DEFAULT 0,
    total_notes_copied integer NOT NULL DEFAULT 0,
    total_notes_skipped integer NOT NULL DEFAULT 0,
    updated_at timestamptz NOT NULL DEFAULT now(),
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_goal_adoption_jobs_recovery ON goal_adoption_jobs (status, updated_at);
CREATE INDEX idx_goal_adoption_jobs_owner ON goal_adoption_jobs (goal_id, owner_user_id);
