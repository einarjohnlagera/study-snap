CREATE TABLE IF NOT EXISTS discount_vouchers (
    id UUID PRIMARY KEY,
    code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(128) NOT NULL,
    description VARCHAR(512),
    discount_type VARCHAR(32) NOT NULL,
    discount_value NUMERIC(14, 2) NOT NULL,
    currency VARCHAR(16) NOT NULL,
    billing_cycle_scope VARCHAR(16) NOT NULL,
    plan_scope VARCHAR(16) NOT NULL,
    region_scope VARCHAR(16) NOT NULL,
    new_subscribers_only BOOLEAN NOT NULL DEFAULT FALSE,
    requires_code BOOLEAN NOT NULL DEFAULT FALSE,
    max_redemptions INTEGER,
    valid_from TIMESTAMPTZ,
    valid_until TIMESTAMPTZ,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_discount_vouchers_active
    ON discount_vouchers(is_active, region_scope, billing_cycle_scope);

CREATE TABLE IF NOT EXISTS voucher_redemptions (
    id UUID PRIMARY KEY,
    voucher_id UUID NOT NULL REFERENCES discount_vouchers(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    subscription_id UUID REFERENCES subscriptions(id) ON DELETE SET NULL,
    payment_transaction_id UUID REFERENCES payment_transactions(id) ON DELETE SET NULL,
    redeemed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    applied_amount NUMERIC(14, 2) NOT NULL,
    currency VARCHAR(16) NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_voucher_redemptions_voucher_user
    ON voucher_redemptions(voucher_id, user_id);

INSERT INTO discount_vouchers (
    id, code, name, description, discount_type, discount_value, currency,
    billing_cycle_scope, plan_scope, region_scope, new_subscribers_only,
    requires_code, max_redemptions, is_active, created_at, updated_at
) VALUES
    ('10000000-0000-0000-0000-000000000001', 'INTRO-PH-MONTHLY', 'PH Intro Monthly', 'First-time Premium intro price for Philippines.', 'OVERRIDE_PRICE', 199.00, 'PHP', 'MONTHLY', 'PREMIUM', 'PH', TRUE, FALSE, NULL, TRUE, NOW(), NOW()),
    ('10000000-0000-0000-0000-000000000002', 'INTRO-US-MONTHLY', 'US Intro Monthly', 'First-time Premium intro price for United States.', 'OVERRIDE_PRICE', 3.99, 'USD', 'MONTHLY', 'PREMIUM', 'US', TRUE, FALSE, NULL, TRUE, NOW(), NOW()),
    ('10000000-0000-0000-0000-000000000003', 'INTRO-GB-MONTHLY', 'GB Intro Monthly', 'First-time Premium intro price for United Kingdom.', 'OVERRIDE_PRICE', 3.49, 'GBP', 'MONTHLY', 'PREMIUM', 'GB', TRUE, FALSE, NULL, TRUE, NOW(), NOW()),
    ('10000000-0000-0000-0000-000000000004', 'INTRO-EU-MONTHLY', 'EU Intro Monthly', 'First-time Premium intro price for Europe.', 'OVERRIDE_PRICE', 3.99, 'EUR', 'MONTHLY', 'PREMIUM', 'EU', TRUE, FALSE, NULL, TRUE, NOW(), NOW()),
    ('10000000-0000-0000-0000-000000000005', 'INTRO-AU-MONTHLY', 'AU Intro Monthly', 'First-time Premium intro price for Australia.', 'OVERRIDE_PRICE', 5.99, 'AUD', 'MONTHLY', 'PREMIUM', 'AU', TRUE, FALSE, NULL, TRUE, NOW(), NOW()),
    ('10000000-0000-0000-0000-000000000006', 'INTRO-CA-MONTHLY', 'CA Intro Monthly', 'First-time Premium intro price for Canada.', 'OVERRIDE_PRICE', 4.99, 'CAD', 'MONTHLY', 'PREMIUM', 'CA', TRUE, FALSE, NULL, TRUE, NOW(), NOW()),
    ('10000000-0000-0000-0000-000000000007', 'INTRO-SG-MONTHLY', 'SG Intro Monthly', 'First-time Premium intro price for Singapore.', 'OVERRIDE_PRICE', 4.99, 'SGD', 'MONTHLY', 'PREMIUM', 'SG', TRUE, FALSE, NULL, TRUE, NOW(), NOW()),
    ('10000000-0000-0000-0000-000000000008', 'INTRO-IN-MONTHLY', 'IN Intro Monthly', 'First-time Premium intro price for India.', 'OVERRIDE_PRICE', 299.00, 'INR', 'MONTHLY', 'PREMIUM', 'IN', TRUE, FALSE, NULL, TRUE, NOW(), NOW())
ON CONFLICT (code) DO NOTHING;
