# subscriptions-and-usage-limits.md - NoteLib Feature Context

## Goal

Support freemium usage control and recurring Premium subscriptions with webhook-driven lifecycle sync.

## Plan behavior

### Free
- 5 Study Packs per month
- My Library and Public Library access
- Quick Review

### Premium
- 100 Study Packs per month
- Challenge Quiz (50/month)
- Adaptive Practice (50/month)
- Weak Concept Detection

## Billing architecture

- Controller/service layer is provider-agnostic (`BillingService` interface).
- Active runtime provider is `PAYMONGO`.
- Premium recurring plans:
  - `MONTHLY` (configured by `PAYMONGO_MONTHLY_PLAN_ID`)
  - `YEARLY` (configured by `PAYMONGO_YEARLY_PLAN_ID`)

## Webhook lifecycle events

Backend handles:

- `subscription.activated`
- `subscription.invoice.paid`
- `subscription.invoice.payment_failed`
- `subscription.past_due`
- `subscription.unpaid`
- `subscription.updated`

Webhook processing maps to:

- `SubscriptionService` for activate/downgrade transitions
- `PaymentTransactionService` for payment attempt recording

## Idempotency and safety

- Duplicate webhook delivery is expected.
- Use provider reference IDs to avoid duplicate transaction inserts.
- Keep webhook processing fast and deterministic.
- Webhook registration is managed outside application runtime.
