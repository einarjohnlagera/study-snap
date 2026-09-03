CREATE TABLE combined_quizzes (
    id UUID PRIMARY KEY,
    owner_user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title VARCHAR(512) NOT NULL,
    sections JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_combined_quizzes_owner_created_at
    ON combined_quizzes(owner_user_id, created_at DESC);

ALTER TABLE quiz_share_links
    ALTER COLUMN generated_quiz_id DROP NOT NULL,
    ADD COLUMN combined_quiz_id UUID REFERENCES combined_quizzes(id) ON DELETE CASCADE,
    ADD CONSTRAINT chk_quiz_share_links_exactly_one_quiz
        CHECK ((generated_quiz_id IS NOT NULL) <> (combined_quiz_id IS NOT NULL));

CREATE INDEX idx_quiz_share_links_combined_quiz_id
    ON quiz_share_links(combined_quiz_id);
