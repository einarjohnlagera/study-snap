CREATE TABLE IF NOT EXISTS user_activity_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    study_pack_id UUID REFERENCES study_packs(id) ON DELETE SET NULL,
    activity_type VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_user_activity_events_user_created_at
    ON user_activity_events(user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_user_activity_events_study_pack_created_at
    ON user_activity_events(study_pack_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_user_activity_events_activity_type
    ON user_activity_events(activity_type);
