CREATE TABLE IF NOT EXISTS generated_quizzes (
    id UUID PRIMARY KEY,
    owner_user_id UUID NOT NULL,
    note_id UUID NOT NULL REFERENCES notes(id) ON DELETE CASCADE,
    questions JSONB NOT NULL,
    generated_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_generated_quizzes_note_id
    ON generated_quizzes(note_id);

CREATE INDEX IF NOT EXISTS idx_generated_quizzes_owner_generated_at
    ON generated_quizzes(owner_user_id, generated_at DESC);
