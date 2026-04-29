# SPEC.md - Billing Architecture Addendum

## Payments – Xendit Integration

- Payment provider: `XENDIT`
- Checkout style: hosted invoice checkout
- Premium activation source of truth: webhook only

### Flow

1. User clicks an upgrade CTA.
2. Frontend calls `POST /api/payments/create`.
3. Backend creates a Xendit invoice and stores a pending `payment_transactions` row.
4. Backend returns `checkoutUrl`.
5. Frontend redirects the user to the hosted Xendit invoice page.
6. Xendit sends `POST /api/webhooks/xendit`.
7. Backend validates `x-callback-token`, applies idempotency, updates payment status, and activates Premium when status is `PAID`.

### Endpoints

- `POST /api/payments/create`
- `POST /api/webhooks/xendit`

### Safety Rules

- Frontend must never mark a user Premium directly.
- `/billing/success` and `/billing/failed` are informational pages only.
- Webhooks must validate `x-callback-token` against `XENDIT_WEBHOOK_TOKEN`.
- Duplicate webhook deliveries must be acknowledged without reapplying Premium.

### Local Test Mode

- Use Xendit test credentials.
- Run backend locally.
- Expose the backend with `ngrok http 8080`.
- Configure the public Xendit webhook endpoint to `{ngrok-url}/api/webhooks/xendit`.
- Use the same `XENDIT_WEBHOOK_TOKEN` value in local env and in the Xendit dashboard test webhook config.
