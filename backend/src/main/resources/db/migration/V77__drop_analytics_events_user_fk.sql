-- Analytics telemetry intentionally has no hard FK to users. Orphaned user_id values are acceptable,
-- so the previous ON DELETE SET NULL relationship is intentionally removed while keeping the column and index.
ALTER TABLE analytics_events
    DROP CONSTRAINT IF EXISTS analytics_events_user_id_fkey;
