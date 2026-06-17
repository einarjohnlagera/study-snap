ALTER TABLE bulk_generation_result
    ADD COLUMN quota_blocked_topics jsonb NOT NULL DEFAULT '[]'::jsonb;
