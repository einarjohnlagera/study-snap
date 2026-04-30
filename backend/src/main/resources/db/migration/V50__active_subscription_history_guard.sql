UPDATE subscriptions
SET status = 'EXPIRED',
    updated_at = NOW()
WHERE status = 'ACTIVE'
  AND end_at IS NOT NULL
  AND end_at <= NOW();

WITH ranked_active_subscriptions AS (
    SELECT id,
           ROW_NUMBER() OVER (
               PARTITION BY user_id
               ORDER BY
                   CASE
                       WHEN start_at <= NOW() AND (end_at IS NULL OR end_at > NOW()) THEN 0
                       WHEN start_at > NOW() THEN 1
                       ELSE 2
                   END,
                   CASE
                       WHEN plan_type = 'PREMIUM' THEN 0
                       ELSE 1
                   END,
                   COALESCE(end_at, TIMESTAMPTZ '9999-12-31 23:59:59+00') DESC,
                   updated_at DESC,
                   created_at DESC,
                   id DESC
           ) AS row_number
    FROM subscriptions
    WHERE status = 'ACTIVE'
)
UPDATE subscriptions AS target
SET status = 'EXPIRED',
    end_at = CASE
        WHEN target.end_at IS NULL OR target.end_at > NOW() THEN NOW()
        ELSE target.end_at
    END,
    updated_at = NOW()
FROM ranked_active_subscriptions AS ranked
WHERE target.id = ranked.id
  AND ranked.row_number > 1;

INSERT INTO subscriptions (
    id,
    user_id,
    plan_type,
    status,
    start_at,
    end_at,
    cancel_at_period_end,
    cancelled_at,
    cancellation_reason,
    cancellation_feedback,
    billing_type,
    provider,
    provider_customer_id,
    provider_subscription_id,
    created_at,
    updated_at
)
SELECT gen_random_uuid(),
       users.id,
       'FREE',
       'ACTIVE',
       NOW(),
       NULL,
       FALSE,
       NULL,
       NULL,
       NULL,
       'NONE',
       'NONE',
       NULL,
       NULL,
       NOW(),
       NOW()
FROM users
WHERE NOT EXISTS (
    SELECT 1
    FROM subscriptions
    WHERE subscriptions.user_id = users.id
      AND subscriptions.status = 'ACTIVE'
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_subscriptions_one_active_per_user
    ON subscriptions (user_id)
    WHERE status = 'ACTIVE';
