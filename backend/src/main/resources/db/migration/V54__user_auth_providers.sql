ALTER TABLE users
    ALTER COLUMN password_hash DROP NOT NULL;

CREATE TABLE IF NOT EXISTS user_auth_providers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    provider VARCHAR(32) NOT NULL,
    provider_user_id VARCHAR(255) NOT NULL,
    provider_email VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_user_auth_providers_provider_subject
    ON user_auth_providers (provider, provider_user_id);

CREATE UNIQUE INDEX IF NOT EXISTS ux_user_auth_providers_user_provider
    ON user_auth_providers (user_id, provider);

CREATE INDEX IF NOT EXISTS idx_user_auth_providers_user_id
    ON user_auth_providers (user_id);
