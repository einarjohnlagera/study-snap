# billing.md - NoteLib Feature Context

## Billing & Payments

### Current Implementation (`v0.11.0`)

- Paid upgrades use Xendit hosted invoice checkout
- Checkout supports manual `Monthly` checkout for `Plus` and `Pro`
- Checkout supports manual `Annual` checkout for `Pro`
- Monthly checkout grants `30` days of paid access
- Annual checkout grants `365` days of paid access
- Renewal is manual; there is no recurring billing yet
- Checkout pricing is loaded from backend billing config
- Automatic intro offers and discounts use `discount_vouchers`
- Voucher redemption history is written only after a successful `PAID` webhook
- Paid-plan activation is webhook-confirmed only
- Frontend success and failure pages are informational only

### Flow

1. User clicks `Choose Plus`, `Go Pro`, `Go Pro Yearly`, or a paywall upgrade CTA.
2. Frontend sends `POST /api/payments/create` with the selected `planType`, selected billing cycle, and an optional safe internal `returnUrl`.
3. Backend validates the user, loads region pricing from config, and resolves any eligible automatic voucher before checkout creation.
4. Backend checks for an unexpired pending Xendit invoice for the same user, plan, billing cycle, final amount, and voucher state.
5. If a reusable pending invoice exists, backend returns its existing `checkoutUrl` instead of creating another pending row.
6. Otherwise backend creates a Xendit invoice, stores a pending `payment_transactions` row, and returns `checkoutUrl`.
7. Frontend redirects to the hosted Xendit checkout page.
8. Xendit redirects the user to `/billing/success` or `/billing/failed` after checkout.
9. Xendit calls `POST /api/webhooks/xendit`.
10. Backend validates the callback token, applies idempotency, updates the payment transaction, activates or extends the selected paid plan only on `PAID`, and records voucher redemption only after payment confirmation.

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

### Paid Access Model

- Current billing model is manual renewal, not auto-renewal.
- `subscriptions` is the only source of truth for plan state and entitlements.
- `subscriptions` preserves billing history; users may have many historical rows over time.
- Only one `ACTIVE` subscription row should exist per user at a time.
- A successful Monthly `PAID` webhook creates or updates active `PLUS` or `PRO` access for `30` days depending on the purchased plan.
- A successful Annual `PAID` webhook currently creates or updates active `PRO` access for `365` days.
- If the user renews the same active paid plan, the same active row is extended instead of creating a duplicate active row.
- If the user switches from one paid plan to another, the current active paid row is ended and a new active row is created for the purchased plan.
- A user counts as paid only while an active `PLUS` or `PRO` subscription exists and `(end_at IS NULL OR end_at > now())`.
- When a paid plan expires, the lifecycle falls back to an active `FREE` subscription record.
- Redirecting to `/billing/success` does not grant paid access by itself.

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

### Plan & Billing Settings UI

The Settings page (`/settings#plan-billing`) surfaces plan selection and billing history in one place:

- **Monthly Usage** — progress bars for Study Packs, Quizzes, Exports, Adaptive Practice, Interview Practice, Long Exam, and Board Exam where available. Displays the limit-reached message inline; no inline upgrade button (the plan cards below are the upgrade path).
- **Billing cycle toggle** — pill toggle between Monthly and Annual. Annual tab shows a savings badge (e.g. "Save 33%") computed from Pro annual vs. 12× monthly. Toggle always renders because Pro has annual pricing in all regions.
- **Plan cards** — three side-by-side cards (Free, Plus, Pro):
  - Current plan card shows a "Current plan" badge and a disabled "Current Plan" button.
  - Non-current paid plan cards show the current checkout CTAs used by the page:
    - `Choose Plus`
    - `Go Pro`
    - `Go Pro Yearly` when annual Pro is selected
  - Pro card shows a "Most popular" badge when the user is not already on Pro.
  - Plus Annual is not yet available; when Annual tab is selected, Plus card shows monthly price with a "Monthly billing only" note and the CTA still sends a MONTHLY checkout.
  - Pro card sends YEARLY checkout when Annual tab is active and Pro annual is available.
  - Active Plus or Pro users see a "Cancel plan" text link below the "Current Plan" button. The link opens the cancellation confirmation modal, which states the specific current-period end date when available. The link is hidden once cancellation is already scheduled; an "Access ends [date]" note appears instead, falling back to "Cancellation scheduled" only when no date is available.
- **Checkout footer** — single-line note with provider, confirmation model, detected region/currency, and a link to `/refund` for cancellation and refund eligibility.
- **Billing History** — subscription summary grid (plan, status, valid-until, billing cycle) and payment transaction table, unchanged from v0.11.0.

### Refunds

- Refunds are admin-initiated only for exceptional billing errors such as duplicate charges or technical payment failures.
- User-facing refund requests happen through `support@mail.notelib.app`; there is no self-serve refund request flow in the app.
- Xendit invoice refunds use the stored `payment_transactions.provider_reference_id` as the Xendit `invoice_id`.
- A successful admin refund marks the transaction `REFUNDED` and sends the user a confirmation email explaining that the refund was submitted to Xendit and may take 5-10 business days to appear on the original payment method.
- Refunds do not immediately remove paid access; paid access continues until the current billing period ends under the manual-renewal cancellation model.

### Post-Success Upgrade Nudge

- `PostSuccessUpgradeNudge` (`frontend/components/billing/post-success-upgrade-nudge.tsx`) is a lightweight inline banner shown on quiz result screens to Free/Plus users after completing a study session.
- Rendered after Quick Review and Challenge Quiz completions when `note.adaptivePracticeAvailable === false` (proxy for non-Pro status).
- Dismissed per-session, per-user via `sessionStorage` key `notelib-post-success-nudge:{trigger}:{userId}`.
- CTA links to `/pricing` and fires an `UPGRADE_CLICKED` analytics event with `source: "post_success_nudge_{trigger}"`.
- Trigger values: `"quick-review"` and `"challenge-quiz"`. Each has its own copy and session key.
- The nudge is purely informational — it does not gate any feature or trigger checkout directly.

### Limitations

- No recurring billing yet
- No self-serve billing portal yet
- No invoice download or receipt UI yet
- Billing history is read-only
- Plus annual plan not yet available (annual toggle sends monthly checkout for Plus)
- Adaptive Practice, Difficulty Selection, and Board Exam Mode are still enforced from backend plan rules; current pricing-surface messaging does not override runtime feature gates
