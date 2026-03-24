ALTER TABLE users
    ADD COLUMN IF NOT EXISTS onboarding_completed_at TIMESTAMPTZ;

UPDATE users
SET onboarding_completed_at = COALESCE(updated_at, email_verified_at, created_at)
WHERE onboarding_completed_at IS NULL
  AND email_verified_at IS NOT NULL
  AND profile_type IS NOT NULL;
