CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash TEXT NOT NULL,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    display_name VARCHAR(100),
    country_code VARCHAR(8),
    profile_type VARCHAR(32),
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    last_login_at TIMESTAMPTZ,
    email_verified_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS subscriptions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    plan_type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    start_at TIMESTAMPTZ NOT NULL,
    end_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_subscriptions_user_id ON subscriptions(user_id);
CREATE INDEX IF NOT EXISTS idx_subscriptions_user_status ON subscriptions(user_id, status);

CREATE TABLE IF NOT EXISTS study_packs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_user_id UUID NOT NULL REFERENCES users(id),
    anon_id VARCHAR(255),
    input_type VARCHAR(16) NOT NULL,
    title TEXT NOT NULL,
    summary TEXT NOT NULL,
    key_concepts JSONB NOT NULL,
    quiz JSONB NOT NULL,
    ocr_confidence NUMERIC(5, 4),
    model_tier VARCHAR(16) NOT NULL DEFAULT 'FREE',
    model_used VARCHAR(64) NOT NULL,
    input_tokens INTEGER,
    output_tokens INTEGER,
    cached_input_tokens INTEGER,
    estimated_cost NUMERIC(12, 6),
    status VARCHAR(32) NOT NULL DEFAULT 'DONE',
    error_code VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    tags TEXT[] NOT NULL DEFAULT '{}'
);

CREATE INDEX IF NOT EXISTS idx_study_packs_owner_user_created_at ON study_packs(owner_user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_study_packs_anon_id ON study_packs(anon_id);
CREATE INDEX IF NOT EXISTS idx_study_packs_created_at ON study_packs(created_at DESC);

CREATE TABLE IF NOT EXISTS study_pack_drafts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_user_id UUID REFERENCES users(id) ON DELETE CASCADE,
    anon_id VARCHAR(255),
    extracted_text TEXT NOT NULL,
    ocr_confidence NUMERIC(5, 4) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_study_pack_drafts_owner_user_id ON study_pack_drafts(owner_user_id);
CREATE INDEX IF NOT EXISTS idx_study_pack_drafts_anon_id ON study_pack_drafts(anon_id);
CREATE INDEX IF NOT EXISTS idx_study_pack_drafts_expires_at ON study_pack_drafts(expires_at);

CREATE TABLE IF NOT EXISTS share_links (
    token VARCHAR(128) PRIMARY KEY,
    study_pack_id UUID NOT NULL REFERENCES study_packs(id) ON DELETE CASCADE,
    is_public BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMPTZ,
    view_count INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_share_links_study_pack_id ON share_links(study_pack_id);
