# subscriptions-and-usage-limits.md - NoteLib Feature Context

## Goal

Support freemium usage control and webhook-confirmed Premium upgrades without making paywalls or billing feel heavy.

## Plan behavior

### Free

- 10 Study Packs per month
- Topic note generation (5/month by default, backend-configurable)
- OCR (20/month by default, backend-configurable)
- File uploads available
- Library and Public Library access
- Quick Review
- Challenge Quiz (5/month)
- Weak concepts remain visible after quiz/review completion
- Adaptive Practice is Premium-only
- Difficulty selection is Premium-only

### Premium

- 100 Study Packs per month
- Topic note generation (100/month by default, backend-configurable)
- Challenge Quiz (50/month)
- Adaptive Practice (30/month)
- OCR (100/month by default, backend-configurable)
- Difficulty selection
- Priority AI
- Weak Concept Detection

## Soft paywall UX

- Free users should see a shared explanatory paywall before a Premium-only quiz action or a hard quota block attempts paid conversion.
- The paywall should explain:
  - `Challenge Quiz`
  - `Adaptive Practice`
  - `Weak Concept Training`
  - `Higher monthly limits`
- Modal actions:
  - `Upgrade to Premium`
  - `Maybe Later`
- Verified users who continue should start hosted checkout through `POST /api/payments/create`.
- Study Pack limit blocks should keep `Generate Study Pack` clickable instead of disabling it.
- Topic note generation and OCR stay separately gated from Study Pack generation.

## Topic note generation and OCR gating

- Topic note generation is a distinct monthly quota from Study Packs and OCR.
- When topic note generation quota is exhausted:
  - Free users see the shared Premium/upgrade modal
  - Premium users see a reset-on-next-billing-date modal
- OCR exhaustion follows the same split:
  - Free users see an upgrade path
  - Premium users see a reset-date explanation
- Backend must enforce all limits even when frontend disables actions.

## Pricing and upgrade surfaces

- Pricing page, Settings billing, dashboard upgrade cards, and shared paywall surfaces may start the same hosted checkout flow.
- Frontend pricing surfaces should still read pricing context from `GET /api/billing/pricing`.
- Shared PHP and USD pricing labels may remain in `pricingConfig` so reviewer-safe pricing stays visible before the pricing API resolves.
- Success and failure pages are informational only and must never activate Premium directly.

## Billing architecture

- Active runtime provider is `XENDIT`.
- Backend is the source of truth for pricing, upgrade eligibility, checkout creation, webhook validation, and Premium activation.
- `POST /api/payments/create` creates a hosted Xendit invoice and returns `checkoutUrl`.
- `POST /api/webhooks/xendit` validates `x-callback-token`, applies idempotency through `webhook_events`, updates `payment_transactions`, and activates Premium only when status is `PAID`.
- `premium_waitlist` may remain in the system for legacy reporting, but it is not part of the active checkout flow.

## Hosted checkout flow

1. User clicks `Upgrade to Premium`.
2. Frontend calls `POST /api/payments/create`.
3. Backend creates a Xendit invoice with a unique `external_id`.
4. Backend persists a pending `payment_transactions` row.
5. Frontend redirects to the hosted Xendit checkout URL.
6. Xendit calls `POST /api/webhooks/xendit`.
7. Backend marks the payment transaction `SUCCESS` or `FAILED`.
8. Backend activates Premium only after a validated `PAID` webhook.

## Current billing limitations

- Current implementation is a hosted one-time Premium payment flow.
- Recurring subscriptions are not implemented yet.
- Premium expiry handling is not implemented yet.
- Billing history is read-only.

## Regional pricing

- Region detection uses the `CF-IPCountry` request header.
- `PricingRegionResolver` maps country codes into supported display regions.
- Backend pricing config includes:
  - `currency`
  - `monthlyPrice`
  - `yearlyPrice`
  - `introMonthlyPrice` (optional)
  - `isActive`

## Voucher and promotion rules

- Intro pricing is implemented through voucher logic, not a boolean on `User`.
- Voucher records support:
  - `discountType`
  - `discountValue`
  - `currency`
  - `billingCycleScope`
  - `planScope`
  - `regionScope`
  - `newSubscribersOnly`
  - `requiresCode`
  - redemption limits and validity windows

## Pricing API

- `GET /api/billing/pricing` returns the effective display pricing for the request region.
- Response contract:
  - `region`
  - `currency`
  - `monthlyPrice`
  - `yearlyPrice`
  - `introMonthlyPrice`
  - `hasIntroPromo`
  - `introEligible`

## Billing history API

- `GET /api/billing/history` returns the current plan summary plus payment history.
- Response contract:
  - `currentPlan`
  - `subscriptionStatus`
  - `billingType`
  - `currentPeriodStart`
  - `currentPeriodEnd`
  - `cancelAtPeriodEnd`
  - `cancellationEffectiveAt`
  - `transactions[]`

## Centralized plan API

- `GET /api/me/plan` is the frontend source of truth for plan limits, usage, remaining counts, and feature flags.
- Response contract includes:
  - `plan`
  - `limits.studyPacksPerMonth`
  - `limits.challengeQuizzesPerMonth`
  - `limits.adaptivePracticePerMonth`
  - `limits.ocrPerMonth`
  - `limits.noteGenerationsPerMonth`
  - `usage.studyPacksUsed`
  - `usage.challengeQuizzesUsed`
  - `usage.adaptivePracticeUsed`
  - `usage.ocrUsed`
  - `usage.noteGenerationsUsed`
  - `remaining.studyPacksRemaining`
  - `remaining.challengeQuizzesRemaining`
  - `remaining.adaptivePracticeRemaining`
  - `remaining.ocrRemaining`
  - `remaining.noteGenerationsRemaining`
  - `features.adaptivePracticeAvailable`
  - `features.difficultySelectionAvailable`
  - `features.fileUploadAvailable`
  - `features.ocrAvailable`
- Frontend gating and usage UI should rely on this API instead of local recalculation.

## Settings usage UI

- `Settings -> Plan & Billing -> Monthly Usage` should render progress bars instead of plain counters.
- Free users see:
  - `Study Packs`
  - `Challenge Quiz`
- Premium users also see:
  - `Adaptive Practice`
- OCR usage remains hidden from the Settings UI even though it is still tracked and enforced in backend.

## Usage reset windows

- Quotas do not reset on calendar month boundaries.
- Free users reset monthly from the account creation date anchor.
- Premium usage windows follow the active billing period model returned by backend.
- Persisted `user_usage.period_start` and `user_usage.period_end` define the active quota cycle for:
  - Study Packs
  - Challenge Quiz
  - Adaptive Practice
  - OCR
  - Topic note generation
