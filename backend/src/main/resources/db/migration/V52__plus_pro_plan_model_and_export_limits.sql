ALTER TABLE user_usage
    ADD COLUMN IF NOT EXISTS exports_count INTEGER NOT NULL DEFAULT 0;

ALTER TABLE payment_transactions
    ADD COLUMN IF NOT EXISTS access_duration_days INTEGER;

UPDATE payment_transactions
SET access_duration_days = CASE
    WHEN billing_cycle = 'YEARLY' THEN 365
    ELSE 30
END
WHERE access_duration_days IS NULL;

ALTER TABLE payment_transactions
    ALTER COLUMN access_duration_days SET NOT NULL;

UPDATE subscriptions
SET plan_type = 'PRO'
WHERE plan_type = 'PREMIUM';

UPDATE payment_transactions
SET plan_type = 'PRO'
WHERE plan_type = 'PREMIUM';

UPDATE discount_vouchers
SET plan_scope = 'PRO'
WHERE plan_scope = 'PREMIUM';

UPDATE discount_vouchers
SET name = 'PH Pro Intro Monthly',
    description = 'First-time Pro intro price for Philippines.',
    updated_at = NOW()
WHERE code = 'INTRO-PH-MONTHLY';

INSERT INTO discount_vouchers (
    id, code, name, description, discount_type, discount_value, currency,
    billing_cycle_scope, plan_scope, region_scope, new_subscribers_only,
    requires_code, max_redemptions, is_active, created_at, updated_at
) VALUES (
    '10000000-0000-0000-0000-000000000009',
    'INTRO-PH-PLUS-MONTHLY',
    'PH Plus Intro Monthly',
    'First-time Plus intro price for Philippines.',
    'OVERRIDE_PRICE',
    149.00,
    'PHP',
    'MONTHLY',
    'PLUS',
    'PH',
    TRUE,
    FALSE,
    NULL,
    TRUE,
    NOW(),
    NOW()
)
ON CONFLICT (code) DO NOTHING;
