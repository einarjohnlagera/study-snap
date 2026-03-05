CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS reviews (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_user_id VARCHAR(255),
    anon_id VARCHAR(255),
    input_type VARCHAR(16) NOT NULL,
    title TEXT NOT NULL,
    summary TEXT NOT NULL,
    key_concepts JSONB NOT NULL,
    quiz JSONB NOT NULL,
    ocr_confidence NUMERIC(5, 4),
    model_tier VARCHAR(16) NOT NULL DEFAULT 'FREE',
    status VARCHAR(32) NOT NULL DEFAULT 'DONE',
    error_code VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_reviews_owner_user_id ON reviews(owner_user_id);
CREATE INDEX IF NOT EXISTS idx_reviews_anon_id ON reviews(anon_id);
CREATE INDEX IF NOT EXISTS idx_reviews_created_at ON reviews(created_at DESC);

CREATE TABLE IF NOT EXISTS review_drafts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_user_id VARCHAR(255),
    anon_id VARCHAR(255),
    extracted_text TEXT NOT NULL,
    ocr_confidence NUMERIC(5, 4) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_review_drafts_owner_user_id ON review_drafts(owner_user_id);
CREATE INDEX IF NOT EXISTS idx_review_drafts_anon_id ON review_drafts(anon_id);
CREATE INDEX IF NOT EXISTS idx_review_drafts_expires_at ON review_drafts(expires_at);

CREATE TABLE IF NOT EXISTS share_links (
    token VARCHAR(128) PRIMARY KEY,
    review_id UUID NOT NULL REFERENCES reviews(id) ON DELETE CASCADE,
    is_public BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMPTZ,
    view_count INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_share_links_review_id ON share_links(review_id);
