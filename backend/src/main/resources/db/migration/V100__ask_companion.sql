ALTER TABLE user_usage
    ADD COLUMN IF NOT EXISTS ask_companion_used_this_month INTEGER NOT NULL DEFAULT 0;

ALTER TABLE user_usage
    ADD CONSTRAINT ck_user_usage_ask_companion_non_negative
    CHECK (ask_companion_used_this_month >= 0);

CREATE TABLE ask_companion_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    collection_id UUID NOT NULL REFERENCES note_collections(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    status VARCHAR(16) NOT NULL,
    turn_count INTEGER NOT NULL DEFAULT 0,
    turns JSONB NOT NULL DEFAULT '[]'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ended_at TIMESTAMPTZ,
    CONSTRAINT ck_ask_companion_session_status CHECK (status IN ('ACTIVE', 'ENDED')),
    CONSTRAINT ck_ask_companion_turn_count CHECK (turn_count BETWEEN 0 AND 6)
);

CREATE INDEX idx_ask_companion_sessions_user_collection_created
    ON ask_companion_sessions(user_id, collection_id, created_at DESC);

CREATE UNIQUE INDEX uq_ask_companion_active_session
    ON ask_companion_sessions(user_id, collection_id)
    WHERE status = 'ACTIVE';
