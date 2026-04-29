# billing.md - NoteLib Feature Context

## Billing & Payments

### Current Implementation (`v0.11.0`)

- One-time Premium payment via Xendit hosted invoice checkout
- No recurring subscription billing yet
- Premium activation is webhook-confirmed only
- Frontend success and failure pages are informational only

### Flow

1. User clicks `Upgrade to Premium`.
2. Frontend calls `POST /api/payments/create`.
3. Backend creates a Xendit invoice and returns `checkoutUrl`.
4. Frontend redirects to the hosted Xendit checkout page.
5. Xendit calls `POST /api/webhooks/xendit`.
6. Backend validates the callback token, updates the payment transaction, and activates Premium on `PAID`.

### Endpoints

- `POST /api/payments/create`
- `POST /api/webhooks/xendit`
- `GET /api/billing/pricing`
- `GET /api/billing/history`
- `GET /api/billing/usage`

### Local Test Mode

- Use Xendit test keys.
- Run backend locally.
- Expose the backend with `ngrok http 8080`.
- Register `{ngrok-url}/api/webhooks/xendit` in the Xendit test dashboard.
- Set the dashboard webhook token to the same `XENDIT_WEBHOOK_TOKEN` value used locally.

### Limitations

- No recurring billing yet
- No Premium expiry handling yet
- No self-serve billing portal yet
- Billing history is read-only
