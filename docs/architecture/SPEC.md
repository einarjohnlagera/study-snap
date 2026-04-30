# SPEC.md - Billing Architecture Addendum

## Payments – Xendit Integration

- Payment provider: `XENDIT`
- Checkout style: hosted invoice checkout
- Paid activation source of truth: webhook-confirmed subscription state only
- Current paid model: manual renewal with `30`-day Monthly access for `PLUS` and `PRO`, plus `365`-day Annual access for `PRO`
- `subscriptions` is the only entitlement source of truth

### Flow

1. User clicks an upgrade CTA.
2. Frontend calls `POST /api/payments/create` with the selected `planType`, selected billing cycle, and an optional safe internal `returnUrl`.
3. Backend validates the user, resolves config-backed regional pricing, and applies any eligible automatic voucher before invoice creation.
4. Backend checks for an existing unexpired pending Xendit transaction for the same user, plan, billing cycle, final amount, and voucher state.
5. If a reusable pending transaction exists, backend returns its stored `checkoutUrl`.
6. Otherwise backend creates a Xendit invoice through `POST /v2/invoices`, stores a pending `payment_transactions` row, and returns `checkoutUrl`.
7. Frontend redirects the user to the hosted Xendit invoice page.
8. Xendit redirects the user to `/billing/success` or `/billing/failed`, preserving the validated `returnUrl` when present.
9. Xendit sends `POST /api/webhooks/xendit`.
10. Backend validates `x-callback-token`, applies idempotency, marks the payment transaction, updates `subscriptions` for the selected paid plan when status is `PAID`, and writes voucher redemption history only after payment confirmation.

### Endpoints

- `POST /api/payments/create`
- `POST /api/webhooks/xendit`

### Invoice Creation Notes

- Xendit invoice creation uses the hosted Invoice API (`POST /v2/invoices`).
- The amount sent to Xendit must be the config-driven major-currency amount expected by the API.
- Current supported plans are `PLUS` and `PRO`.
- Current supported manual billing cycles are `MONTHLY` for `PLUS` and `PRO`, plus `YEARLY` for `PRO`.
- Backend stores:
  - Xendit `external_id`
  - selected `planType`
  - selected `billing_cycle`
  - `original_amount`
  - `discount_amount`
  - final charged `amount`
  - optional applied `voucher_id`
  - hosted `checkoutUrl`
  - invoice expiry timestamp when available
  - optional `subscription_id` link on the payment transaction after webhook activation

### Return URL Rules

- `returnUrl` is allowed only for internal app paths.
- Valid examples:
  - `/dashboard`
  - `/library`
  - `/notes/new`
  - `/notes/{id}`
- Invalid examples:
  - `https://evil.example`
  - `//evil.example`
- Backend, not frontend, validates redirect safety before embedding it in Xendit success and failure URLs.
- Success CTA routing is context-aware:
  - interrupted product flows -> `Continue where you left off`
  - Settings/Billing origins or missing `returnUrl` -> `Go to Dashboard`

### Safety Rules

- Frontend must never mark a user paid directly.
- `/billing/success` and `/billing/failed` are informational pages only.
- Webhooks must validate `x-callback-token` against `XENDIT_WEBHOOK_TOKEN`.
- Duplicate webhook deliveries must be acknowledged without reapplying paid access.
- Duplicate `PAID` webhook deliveries must not extend access repeatedly for the same transaction.
- Voucher redemptions must only be created after a validated `PAID` webhook.
- Unknown `external_id` webhook payloads should be logged and acknowledged safely.
- Payment-flow documentation must be updated whenever checkout, webhook, or paid-plan expiry behavior changes.

### Paid Access Window

- Paid access begins only after a validated `PAID` webhook.
- `subscriptions` preserves billing history; users may have multiple historical rows.
- Only one `ACTIVE` subscription row should exist per user at a time.
- If the user has no active matching paid subscription, the webhook ends the current active row when needed and creates a new active `PLUS` or `PRO` row in `subscriptions`.
- If the user already has an active subscription for the purchased paid plan, the webhook extends that active row instead of creating a duplicate active row.
- A user counts as paid only while `plan_type IN (PLUS, PRO)`, `status = ACTIVE`, and `(end_at IS NULL OR end_at > now())`.
- Paid-plan expiry falls back to an active `FREE` subscription record through lifecycle handling.
- Manual renewals extend `end_at` from `max(current_end_at, now)`.
- Manual renewals may be Monthly or Yearly where configured; there is no recurring renewal job yet.

### Local Test Mode

- Use Xendit test credentials.
- Run backend locally.
- Expose the backend with `ngrok http 8080`.
- Configure the public Xendit webhook endpoint to `{ngrok-url}/api/webhooks/xendit`.
- Use the same `XENDIT_WEBHOOK_TOKEN` value in local env and in the Xendit dashboard test webhook config.
- Use Xendit test checkout pages to verify:
  - pending checkout reuse
  - `PAID` webhook activation
  - `FAILED` / `EXPIRED` webhook handling
  - safe return to the original NoteLib route after checkout
