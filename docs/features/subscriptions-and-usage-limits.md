# subscriptions-and-usage-limits.md - NoteLib Feature Context

## Goal

Document the current plan model, quota enforcement, and hosted-upgrade behavior.

## Source of truth

Plan access and entitlements come from:

- `subscriptions` for active plan state
- `GET /api/me/plan` for frontend-facing limits, usage, remaining counts, and feature flags

Do not derive plan access from:

- frontend pricing copy
- `payment_transactions`
- user-level paid flags

## Current enforced plan behavior

### Free

- `10` Study Packs / month
- `5` Challenge Quizzes / month
- topic note generation: backend-configured Free limit (`5` by default)
- OCR: backend-configured Free limit (`20` by default)
- exports: `2` / month
- Quick Review available
- Challenge Quiz available
- weak concepts visible after quiz completion
- Adaptive Practice unavailable
- Difficulty selection unavailable
- Board Exam Mode unavailable

### Plus

- `50` Study Packs / month
- `25` Challenge Quizzes / month
- topic note generation: backend-configured Plus limit (`25` by default)
- OCR: backend-configured Plus limit (`50` by default)
- exports: `15` / month
- higher usage limits than Free
- Adaptive Practice currently unavailable in runtime
- Difficulty selection unavailable
- Board Exam Mode unavailable

### Pro

- `100` Study Packs / month
- `50` Challenge Quizzes / month
- topic note generation: backend-configured Pro limit (`100` by default)
- OCR: backend-configured Pro limit (`100` by default)
- exports: unlimited
- Adaptive Practice available and quota-limited (`30` / month by default)
- Difficulty selection available
- Board Exam Mode available

## Pricing-surface note

Some pricing surfaces currently position Plus with stronger “regular study” messaging than the backend feature gates provide.

For actual behavior and gating decisions:

- use backend plan limits and feature flags
- treat `GET /api/me/plan` as the frontend contract

## Study Pack limit UX

- remaining Study Packs come from backend usage calculations
- when remaining reaches `2` or `1`, show the near-limit warning banner
- when remaining reaches `0`, keep `Generate Study Pack` clickable and show the appropriate limit/paywall modal on click
- quota increments only after a successful Study Pack is persisted
- saving a note, failed generation, or failed retry must not consume Study Pack quota

## Topic note generation and OCR

Topic note generation and OCR are distinct monthly quotas from Study Packs.

When topic note generation is exhausted:

- Free users hit the upgrade path
- paid users get reset-date messaging instead of a billing redirect

When OCR is exhausted:

- Free users get the upgrade path
- paid users get reset-date messaging

Backend enforcement is mandatory even if the frontend disables or hides actions.

## Checkout and billing flow

- active provider: `XENDIT`
- frontend starts checkout with `POST /api/payments/create`
- backend validates pricing, vouchers, return URL, and pending-checkout reuse
- Xendit webhook confirmation is the only path that activates or extends paid access
- success and failure pages are informational only

## Pending checkout reuse

Pending checkout reuse is allowed only when all of these still match:

- user
- plan
- billing cycle
- final amount
- voucher state
- provider
- invoice not expired

Monthly and yearly checkouts must not block each other incorrectly.

## Usage reset windows

- Free usage resets on the monthly window anchored to account creation
- paid usage resets on the active subscription billing window
- persisted `user_usage.period_start` and `user_usage.period_end` remain the quota-cycle boundaries
