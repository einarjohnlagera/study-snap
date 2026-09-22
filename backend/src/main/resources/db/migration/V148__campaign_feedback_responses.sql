CREATE TABLE IF NOT EXISTS campaign_feedback_responses (
    id                      UUID PRIMARY KEY,
    user_id                 UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    campaign_id             VARCHAR(64) NOT NULL,
    primary_blockers        TEXT[]      NOT NULL,
    quiz_issues             TEXT[],
    plan_issue              VARCHAR(64),
    missing_feature_text    VARCHAR(200),
    content_subject_text    VARCHAR(200),
    free_text               TEXT,
    created_at              TIMESTAMPTZ NOT NULL
);

CREATE UNIQUE INDEX idx_campaign_feedback_user_campaign
    ON campaign_feedback_responses(user_id, campaign_id);

CREATE INDEX idx_campaign_feedback_campaign_created
    ON campaign_feedback_responses(campaign_id, created_at DESC);
