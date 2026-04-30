ALTER TABLE payment_transactions
    ADD COLUMN IF NOT EXISTS billing_cycle VARCHAR(16),
    ADD COLUMN IF NOT EXISTS original_amount NUMERIC(14, 2),
    ADD COLUMN IF NOT EXISTS discount_amount NUMERIC(14, 2),
    ADD COLUMN IF NOT EXISTS voucher_id UUID REFERENCES discount_vouchers(id) ON DELETE SET NULL;

UPDATE payment_transactions
SET billing_cycle = 'MONTHLY'
WHERE billing_cycle IS NULL;

UPDATE payment_transactions
SET original_amount = amount
WHERE original_amount IS NULL;

UPDATE payment_transactions
SET discount_amount = 0
WHERE discount_amount IS NULL;

ALTER TABLE payment_transactions
    ALTER COLUMN billing_cycle SET NOT NULL,
    ALTER COLUMN original_amount SET NOT NULL,
    ALTER COLUMN discount_amount SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_payment_transactions_pending_cycle_lookup
    ON payment_transactions(user_id, provider, plan_type, billing_cycle, status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_payment_transactions_voucher_id
    ON payment_transactions(voucher_id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_voucher_redemptions_payment_transaction
    ON voucher_redemptions(payment_transaction_id)
    WHERE payment_transaction_id IS NOT NULL;
