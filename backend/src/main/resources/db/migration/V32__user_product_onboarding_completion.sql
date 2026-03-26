ALTER TABLE users
ADD COLUMN IF NOT EXISTS product_onboarding_completed_at TIMESTAMPTZ NULL;
