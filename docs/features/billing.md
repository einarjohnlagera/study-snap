# billing.md - NoteLib Feature Context

## Billing & Payments

### Current Implementation (`v0.11.0`)

- Premium upgrades use Xendit hosted invoice checkout
- Checkout supports manual `Monthly` and `Annual` Premium payments
- Monthly checkout grants `30` days of Premium access
- Annual checkout grants `365` days of Premium access
- Renewal is manual; there is no recurring billing yet
- Checkout pricing is loaded from backend billing config
- Automatic intro offers and discounts use `discount_vouchers`
- Voucher redemption history is written only after a successful `PAID` webhook
- Premium activation is webhook-confirmed only
- Frontend success and failure pages are informational only

### Flow

1. User clicks `Upgrade to Premium`.
2. Frontend sends `POST /api/payments/create` with the selected billing cycle and an optional safe internal `returnUrl`.
3. Backend validates the user, loads region pricing from config, and resolves any eligible automatic voucher before checkout creation.
4. Backend checks for an unexpired pending Xendit invoice for the same user, plan, billing cycle, final amount, and voucher state.
5. If a reusable pending invoice exists, backend returns its existing `checkoutUrl` instead of creating another pending row.
6. Otherwise backend creates a Xendit invoice, stores a pending `payment_transactions` row, and returns `checkoutUrl`.
7. Frontend redirects to the hosted Xendit checkout page.
8. Xendit redirects the user to `/billing/success` or `/billing/failed` after checkout.
9. Xendit calls `POST /api/webhooks/xendit`.
10. Backend validates the callback token, applies idempotency, updates the payment transaction, activates or extends Premium only on `PAID`, and records voucher redemption only after payment confirmation.

### Endpoints

- `POST /api/payments/create`
- `POST /api/webhooks/xendit`
- `GET /api/billing/pricing`
- `GET /api/billing/history`
- `GET /api/billing/usage`

### Checkout Behavior

- The Xendit invoice amount uses the configured final major-currency amount expected by Xendit.
- Backend payment code must not hardcode checkout amounts; changing billing config changes checkout amount.
- Repeated upgrade clicks reuse the same pending checkout only when cycle, final amount, and voucher state still match.
- If pricing or voucher eligibility changes for that cycle, backend marks the stale pending transaction failed and creates a fresh checkout.
- Monthly pending checkout does not block Annual checkout, and Annual does not block Monthly.

### Return URL Support

- Upgrade flows may include a safe internal `returnUrl`, such as:
  - `/dashboard`
  - `/library`
  - `/notes/new`
  - `/notes/{id}`
- Backend only accepts internal paths that start with `/`.
- External URLs and protocol-relative redirects are rejected.
- Success and failure redirects preserve the `returnUrl`.
- Success prefers `Continue where you left off` for interrupted study flows such as notes, quizzes, and review.
- Success falls back to `Go to Dashboard` when the upgrade started from Settings/Billing or when no usable `returnUrl` was provided.

### Premium Access Model

- Current billing model is manual renewal, not auto-renewal.
- `subscriptions` is the only source of truth for plan state and Premium access.
- `subscriptions` preserves billing history; users may have many historical rows over time.
- Only one `ACTIVE` subscription row should exist per user at a time.
- A successful Monthly `PAID` webhook expires the current active non-Premium subscription when needed, then creates or extends active `PREMIUM` access for `30` days.
- A successful Annual `PAID` webhook creates or extends active `PREMIUM` access for `365` days.
- If the user already has an active Premium subscription, the same active Premium row is extended instead of creating a duplicate active Premium row.
- A user counts as Premium only while an active `PREMIUM` subscription exists and `(end_at IS NULL OR end_at > now())`.
- When Premium expires, the lifecycle falls back to an active `FREE` subscription record.
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
