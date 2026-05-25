CREATE TABLE IF NOT EXISTS quiz_share_links (
    id UUID PRIMARY KEY,
    generated_quiz_id UUID NOT NULL REFERENCES generated_quizzes(id) ON DELETE CASCADE,
    owner_user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token VARCHAR(32) NOT NULL UNIQUE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_quiz_share_links_generated_quiz_id
    ON quiz_share_links(generated_quiz_id);

CREATE INDEX IF NOT EXISTS idx_quiz_share_links_owner_created_at
    ON quiz_share_links(owner_user_id, created_at);

ALTER TABLE user_usage
    ADD COLUMN quiz_share_links_created INTEGER NOT NULL DEFAULT 0;
