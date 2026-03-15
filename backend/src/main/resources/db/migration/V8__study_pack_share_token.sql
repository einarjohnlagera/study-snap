ALTER TABLE study_packs
    ADD COLUMN IF NOT EXISTS share_token VARCHAR(128);

CREATE UNIQUE INDEX IF NOT EXISTS idx_study_packs_share_token
    ON study_packs (share_token)
    WHERE share_token IS NOT NULL;
