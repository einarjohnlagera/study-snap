ALTER TABLE users
    ADD COLUMN review_days TEXT[],
    ADD COLUMN review_commitment_prompted_at TIMESTAMPTZ;
