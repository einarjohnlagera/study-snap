-- Ensure generic provider columns are populated before removing Stripe-specific columns.
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

DROP INDEX IF EXISTS idx_subscriptions_stripe_customer_id;
DROP INDEX IF EXISTS uq_subscriptions_stripe_subscription_id;

ALTER TABLE subscriptions
    DROP COLUMN IF EXISTS stripe_customer_id,
    DROP COLUMN IF EXISTS stripe_subscription_id;
