# subscriptions-and-usage-limits.md - NoteLib Feature Context

## Goal

Support freemium usage control and recurring Premium subscriptions with webhook-driven lifecycle sync.

## Plan behavior

### Free
- 5 Study Packs per month
- My Library and Public Library access
- Quick Review
- Soft paywall messaging for Premium-only quiz modes and Study Pack limit blocks

### Premium
- 100 Study Packs per month
- Challenge Quiz (50/month)
- Adaptive Practice (50/month)
- Weak Concept Detection

## Premium cancellation

- Canceling Premium is a confirm-first flow in `Settings`.
- Cancellation is scheduled for the end of the current billing period, not immediate.
- Premium features remain active until the stored billing-period end date.
- After the period ends, subscription lifecycle logic downgrades the account to Free.
- Canceling Premium does not remove notes or generated Study Packs from the user library.
- Optional cancellation feedback can be stored:
  - `cancellationReason`
  - `cancellationFeedback`

## Soft paywall UX

- Free users should not be redirected immediately to `Settings` when they click Premium-only quiz actions.
- Clicking `Challenge Quiz` as a Free user should open a reusable `AppModal`-based paywall first.
- Challenge Quiz modal copy:
  - title: `Unlock Exam Mode`
  - body: `Challenge Quiz simulates a real exam and helps you test your knowledge without seeing answers immediately. Perfect for exam preparation.`
  - actions: `Maybe Later` and `Upgrade to Premium`
- Clicking `Adaptive Practice` as a Free user should open a reusable paywall first.
- Adaptive Practice modal copy:
  - title: `Focus on Your Weak Topics`
  - body: `Adaptive Practice creates quizzes based on the topics you got wrong so you can improve faster and focus on weak areas.`
  - actions: `Maybe Later` and `Upgrade to Premium`
- If a Free user reaches the monthly Study Pack generation limit, show a limit-reached paywall modal first.
- Limit modal copy:
  - title: `You've reached your monthly limit`
  - body explains Free includes `5` Study Pack generations/month and Premium unlocks more usage plus Premium quiz modes
  - actions: `OK` and `Upgrade to Premium`
- Only the explicit `Upgrade to Premium` action should navigate to `Settings` billing.
- At `80%` of the Free Study Pack limit, show a non-blocking warning banner on:
  - Dashboard
  - Note Detail
  - Study Pack generation/editor surfaces

## Pricing page and upgrade positioning

- NoteLib pricing copy should position Premium as an exam preparation and mastery plan, not only as an AI upgrade.
- Pricing page hero copy:
  - title: `Study smarter. Pass exams faster.`
  - subtitle: `Turn your notes into summaries, quizzes, and reviewers in seconds.`
  - actions: `Start Free` and `Upgrade to Premium`
- Pricing page must display localized pricing from `GET /api/billing/pricing`.
- Pricing page should compare Free vs Premium clearly for student workflows:
  - Free: Create Notes, Save Notes, `5` Study Packs/month, Quick Review, Public Library Access
  - Premium: Everything in Free, `100` Study Packs/month, Challenge Quiz, Adaptive Practice, Priority AI generation
- Dashboard should show a Free-only upgrade card with Premium exam-prep messaging and a redirect to `Settings` billing.

## Billing architecture

- Controller/service layer is provider-agnostic (`BillingService` interface).
- Active runtime provider is `PAYMONGO`.
- Backend is the single source of truth for Premium pricing, region resolution, voucher eligibility, and PayMongo plan selection.
- Frontend pricing surfaces must read pricing from `GET /api/billing/pricing` and must not hardcode subscription amounts.

## Regional pricing

- Region detection uses the `CF-IPCountry` request header.
- `PricingRegionResolver` maps country codes into pricing regions:
  - `PH -> PH`
  - `US -> US`
  - `GB -> GB`
  - EU member countries -> `EU`
  - `AU -> AU`
  - `CA -> CA`
  - `SG -> SG`
  - `IN -> IN`
  - fallback -> `US`
- Backend pricing config is stored per region and includes:
  - `currency`
  - `monthlyPrice`
  - `yearlyPrice`
  - `paymongoMonthlyPlanId`
  - `paymongoYearlyPlanId`
  - `paymongoIntroMonthlyPlanId` (optional)
  - `paymongoIntroYearlyPlanId` (optional)
  - `isActive`

## Voucher and promotion rules

- Intro pricing is implemented as voucher logic, not a boolean on `User`.
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
- Voucher eligibility checks:
  - voucher is active
  - current time is within `validFrom` and `validUntil`
  - redemption limit is not exhausted
  - region, billing cycle, plan, and currency match
  - if `newSubscribersOnly=true`, the user has no prior Premium subscription and has not redeemed that voucher before
- Automatic intro promos use `requiresCode=false`.
- Future promo codes use the same voucher system with `requiresCode=true`.

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
- Pricing page, upgrade modal, and Settings billing UI must all use this API response.

## PayMongo plan selection

- Checkout plan selection is backend-driven.
- When creating a subscription, backend chooses the PayMongo plan ID using:
  - resolved pricing region
  - selected billing cycle
  - eligible automatic or code-based voucher
- If an eligible voucher is applied and the region config has a matching intro plan ID, backend uses that intro PayMongo plan ID.
- Otherwise backend uses the standard monthly or yearly PayMongo plan ID for the region.
- Successful subscription activation records a voucher redemption tied to the user, subscription, and payment transaction.

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
- Store processed provider events in `webhook_events` and skip duplicate `event_id` values.

## Lifecycle jobs

- `SubscriptionExpiryJob` (daily):
  - finds active Premium subscriptions past `endAt`
  - marks them expired and downgrades users to Free
  - scheduled cancellations use the existing billing-period end date for downgrade timing
- `BillingUsageResetJob` (daily):
  - ensures each user has a usage row for the current billing period
  - free users follow calendar month windows
  - premium users follow their subscription billing window (`startAt` to `endAt`)
