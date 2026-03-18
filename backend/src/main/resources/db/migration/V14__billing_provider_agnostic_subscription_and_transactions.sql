ALTER TABLE subscriptions
    ADD COLUMN IF NOT EXISTS billing_type VARCHAR(32) NOT NULL DEFAULT 'NONE',
    ADD COLUMN IF NOT EXISTS provider VARCHAR(32) NOT NULL DEFAULT 'NONE',
    ADD COLUMN IF NOT EXISTS provider_customer_id VARCHAR(128),
    ADD COLUMN IF NOT EXISTS provider_subscription_id VARCHAR(128);

UPDATE subscriptions
SET provider = 'STRIPE'
WHERE provider = 'NONE'
  AND stripe_customer_id IS NOT NULL;

UPDATE subscriptions
SET provider_customer_id = stripe_customer_id
WHERE provider_customer_id IS NULL
  AND stripe_customer_id IS NOT NULL;

UPDATE subscriptions
SET provider_subscription_id = stripe_subscription_id
WHERE provider_subscription_id IS NULL
  AND stripe_subscription_id IS NOT NULL;

UPDATE subscriptions
SET billing_type = 'SUBSCRIPTION'
WHERE billing_type = 'NONE'
  AND provider = 'STRIPE'
  AND (provider_subscription_id IS NOT NULL OR provider_customer_id IS NOT NULL);

CREATE INDEX IF NOT EXISTS idx_subscriptions_provider_customer
    ON subscriptions(provider, provider_customer_id)
    WHERE provider_customer_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_subscriptions_provider_subscription_id
    ON subscriptions(provider, provider_subscription_id)
    WHERE provider_subscription_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS payment_transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    provider VARCHAR(32) NOT NULL,
    billing_type VARCHAR(32) NOT NULL,
    plan_type VARCHAR(32) NOT NULL,
    amount NUMERIC(14, 2) NOT NULL,
    currency VARCHAR(16) NOT NULL,
    status VARCHAR(32) NOT NULL,
    provider_reference_id VARCHAR(191) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_payment_transactions_user_created_at
    ON payment_transactions(user_id, created_at DESC);

CREATE UNIQUE INDEX IF NOT EXISTS uq_payment_transactions_provider_reference
    ON payment_transactions(provider, provider_reference_id);
