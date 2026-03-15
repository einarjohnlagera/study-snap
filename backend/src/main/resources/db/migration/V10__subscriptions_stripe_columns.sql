ALTER TABLE subscriptions
    ADD COLUMN IF NOT EXISTS stripe_customer_id VARCHAR(128),
    ADD COLUMN IF NOT EXISTS stripe_subscription_id VARCHAR(128);

CREATE INDEX IF NOT EXISTS idx_subscriptions_stripe_customer_id ON subscriptions(stripe_customer_id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_subscriptions_stripe_subscription_id
    ON subscriptions(stripe_subscription_id)
    WHERE stripe_subscription_id IS NOT NULL;
