ALTER TABLE users
    ADD COLUMN IF NOT EXISTS is_premium BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS premium_activated_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS premium_expires_at TIMESTAMPTZ;

WITH latest_active_premium AS (
    SELECT DISTINCT ON (user_id)
        user_id,
        start_at,
        end_at
    FROM subscriptions
    WHERE plan_type = 'PREMIUM'
      AND status = 'ACTIVE'
    ORDER BY user_id, updated_at DESC
)
UPDATE users u
SET is_premium = TRUE,
    premium_activated_at = COALESCE(p.start_at, u.created_at),
    premium_expires_at = p.end_at
FROM latest_active_premium p
WHERE u.id = p.user_id;

UPDATE payment_transactions
SET provider = 'XENDIT'
WHERE provider IN ('PAYMONGO', 'STRIPE');

UPDATE subscriptions
SET provider = 'XENDIT'
WHERE provider IN ('PAYMONGO', 'STRIPE');

UPDATE webhook_events
SET provider = 'XENDIT'
WHERE provider IN ('PAYMONGO', 'STRIPE');
