ALTER TABLE payment_transactions
    ADD COLUMN IF NOT EXISTS checkout_url VARCHAR(1000),
    ADD COLUMN IF NOT EXISTS expires_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_payment_transactions_pending_lookup
    ON payment_transactions(user_id, provider, plan_type, status, created_at DESC);
