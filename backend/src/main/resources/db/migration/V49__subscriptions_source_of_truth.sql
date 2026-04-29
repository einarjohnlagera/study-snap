ALTER TABLE payment_transactions
    ADD COLUMN IF NOT EXISTS subscription_id UUID REFERENCES subscriptions(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_payment_transactions_subscription_id
    ON payment_transactions(subscription_id)
    WHERE subscription_id IS NOT NULL;

ALTER TABLE users
    DROP COLUMN IF EXISTS is_premium,
    DROP COLUMN IF EXISTS premium_activated_at,
    DROP COLUMN IF EXISTS premium_expires_at;
