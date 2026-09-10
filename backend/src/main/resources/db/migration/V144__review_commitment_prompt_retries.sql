ALTER TABLE users
    ADD COLUMN review_commitment_prompt_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN review_commitment_last_prompted_at TIMESTAMPTZ NULL;
