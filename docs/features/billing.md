# billing.md - NoteLib Feature Context

## Billing & Payments

### Current Implementation (`v0.11.0`)

- Premium upgrades use Xendit hosted invoice checkout
- Each successful payment grants `30` days of Premium access
- Renewal is manual; there is no recurring billing yet
- Premium activation is webhook-confirmed only
- Frontend success and failure pages are informational only

### Flow

1. User clicks `Upgrade to Premium`.
2. Frontend sends `POST /api/payments/create` with an optional safe internal `returnUrl`.
3. Backend validates the user, rejects already-active Premium users, and checks for an unexpired pending Xendit invoice.
4. If a reusable pending invoice exists, backend returns its existing `checkoutUrl` instead of creating another pending row.
5. Otherwise backend creates a Xendit invoice, stores a pending `payment_transactions` row, and returns `checkoutUrl`.
6. Frontend redirects to the hosted Xendit checkout page.
7. Xendit redirects the user to `/billing/success` or `/billing/failed` after checkout.
8. Xendit calls `POST /api/webhooks/xendit`.
9. Backend validates the callback token, applies idempotency, updates the payment transaction, and activates Premium only on `PAID`.

### Endpoints

- `POST /api/payments/create`
- `POST /api/webhooks/xendit`
- `GET /api/billing/pricing`
- `GET /api/billing/history`
- `GET /api/billing/usage`

### Checkout Behavior

- The Xendit invoice amount uses the real major-currency PHP value expected by Xendit.
- Premium monthly checkout should display `PHP 249.00`, not `PHP 24,900.00`.
- Repeated upgrade clicks reuse the same pending checkout when the existing invoice has not expired.
- If the pending invoice has expired, backend marks that transaction failed and creates a fresh checkout.

### Return URL Support

- Upgrade flows may include a safe internal `returnUrl`, such as:
  - `/dashboard`
  - `/library`
  - `/notes/new`
  - `/notes/{id}`
- Backend only accepts internal paths that start with `/`.
- External URLs and protocol-relative redirects are rejected.
- Success and failure redirects preserve the `returnUrl` so users can continue where they left off after checkout.

### Premium Access Model

- Current billing model is manual renewal, not auto-renewal.
- A successful `PAID` webhook sets:
  - `premiumActivatedAt`
  - `premiumExpiresAt = activatedAt + 30 days`
- A user counts as Premium only while `premiumExpiresAt` is still in the future.
- Redirecting to `/billing/success` does not grant Premium access by itself.

### Draft Preservation During Upgrade

- When the paywall is triggered from New Note creation, frontend attempts to save the note before checkout.
- If save succeeds, checkout returns users to the saved note edit route.
- If save fails, frontend preserves the draft locally and restores it when the user returns from billing.

### Local Test Mode

- Use Xendit test keys.
- Run backend locally.
- Expose the backend with `ngrok http 8080`.
- Register `{ngrok-url}/api/webhooks/xendit` in the Xendit test dashboard.
- Set the dashboard webhook token to the same `XENDIT_WEBHOOK_TOKEN` value used locally.
- Use the Xendit hosted test checkout to simulate `PAID`, `FAILED`, or `EXPIRED` invoice outcomes.

### Limitations

- No recurring billing yet
- No self-serve cancellation flow yet
- No self-serve billing portal yet
- No invoice download or receipt UI yet
- Billing history is read-only
