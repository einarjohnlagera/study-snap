# SPEC.md - Billing Architecture Addendum

## Payments – Xendit Integration

- Payment provider: `XENDIT`
- Checkout style: hosted invoice checkout
- Premium activation source of truth: webhook-confirmed subscription state only
- Current Premium model: manual renewal with `30` days of access per successful payment
- `subscriptions` is the only entitlement source of truth

### Flow

1. User clicks an upgrade CTA.
2. Frontend calls `POST /api/payments/create` with an optional safe internal `returnUrl`.
3. Backend validates the user and rejects checkout creation if Premium is already active.
4. Backend checks for an existing unexpired pending Xendit transaction for the same user and plan.
5. If a reusable pending transaction exists, backend returns its stored `checkoutUrl`.
6. Otherwise backend creates a Xendit invoice through `POST /v2/invoices`, stores a pending `payment_transactions` row, and returns `checkoutUrl`.
7. Frontend redirects the user to the hosted Xendit invoice page.
8. Xendit redirects the user to `/billing/success` or `/billing/failed`, preserving the validated `returnUrl` when present.
9. Xendit sends `POST /api/webhooks/xendit`.
10. Backend validates `x-callback-token`, applies idempotency, marks the payment transaction, and creates or extends the active `PREMIUM` subscription when status is `PAID`.

### Endpoints

- `POST /api/payments/create`
- `POST /api/webhooks/xendit`

### Invoice Creation Notes

- Xendit invoice creation uses the hosted Invoice API (`POST /v2/invoices`).
- The amount sent to Xendit must be the actual major-currency PHP amount expected by the API.
- Current Premium monthly checkout amount is `249.00 PHP`.
- Backend stores:
  - Xendit `external_id`
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

### Safety Rules

- Frontend must never mark a user Premium directly.
- `/billing/success` and `/billing/failed` are informational pages only.
- Webhooks must validate `x-callback-token` against `XENDIT_WEBHOOK_TOKEN`.
- Duplicate webhook deliveries must be acknowledged without reapplying Premium.
- Duplicate `PAID` webhook deliveries must not extend access repeatedly for the same transaction.
- Unknown `external_id` webhook payloads should be logged and acknowledged safely.
- Payment-flow documentation must be updated whenever checkout, webhook, or Premium-expiry behavior changes.

### Premium Access Window

- Premium access begins only after a validated `PAID` webhook.
- Webhook activation creates or extends the active `PREMIUM` subscription row in `subscriptions`.
- A user counts as Premium only while `plan_type = PREMIUM`, `status = ACTIVE`, and `(end_at IS NULL OR end_at > now())`.
- Manual renewals extend `end_at` from `max(current_end_at, now)`.
- Manual renewal means the user may start checkout again after expiry; there is no recurring renewal job yet.

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
