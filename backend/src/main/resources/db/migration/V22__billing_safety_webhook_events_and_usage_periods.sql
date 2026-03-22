CREATE TABLE IF NOT EXISTS webhook_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    provider VARCHAR(32) NOT NULL,
    event_id VARCHAR(191) NOT NULL,
    event_type VARCHAR(128),
    processed_at TIMESTAMPTZ,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_webhook_events_provider_event_id
    ON webhook_events(provider, event_id);

CREATE INDEX IF NOT EXISTS idx_webhook_events_provider_created_at
    ON webhook_events(provider, created_at DESC);

ALTER TABLE user_usage
    ADD COLUMN IF NOT EXISTS period_start TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS period_end TIMESTAMPTZ;

UPDATE user_usage
SET period_start = make_timestamptz(year, month, 1, 0, 0, 0, 'UTC')
WHERE period_start IS NULL;

UPDATE user_usage
SET period_end = (period_start + INTERVAL '1 month')
WHERE period_end IS NULL;

ALTER TABLE user_usage
    ALTER COLUMN period_start SET NOT NULL,
    ALTER COLUMN period_end SET NOT NULL;

DROP INDEX IF EXISTS uq_user_usage_user_year_month;

CREATE UNIQUE INDEX IF NOT EXISTS uq_user_usage_user_period_start
    ON user_usage(user_id, period_start);
