# subscriptions-and-usage-limits.md - NoteLib Feature Context

## Goal

Support freemium usage control and recurring Premium subscriptions with webhook-driven lifecycle sync.

## Plan behavior

### Free
- 10 Study Packs per month
- OCR (20/month by default, backend-configurable)
- File uploads available
- Library and Public Library access
- Quick Review
- Challenge Quiz (5/month)
- Weak concepts remain visible after quiz/review completion
- Adaptive Practice is Premium-only
- Difficulty selection is Premium-only
- Soft paywall messaging for Premium-only features and Study Pack limit blocks

### Premium
- 100 Study Packs per month
- Challenge Quiz (50/month)
- Adaptive Practice (30/month)
- OCR (100/month by default, backend-configurable)
- Difficulty selection
- Priority AI
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

## Billing history in Settings

- `Settings -> Plan & Billing` should show a subscription summary card above payment history.
- The summary card should display:
  - `currentPlan`
  - `subscriptionStatus`
  - `billingType`
  - renewal date when Premium is active
  - end date and non-renewal messaging when `cancelAtPeriodEnd=true`
- Payment history is sourced from `payment_transactions` and sorted newest first.
- Transaction descriptions should stay user-friendly:
  - `Premium Monthly`
  - `Premium Yearly`
  - `Subscription Renewal`
  - `Failed payment`
- Empty state copy:
  - title: `No billing history yet`
  - body: `Your payment history will appear here once you subscribe to Premium.`

## Soft paywall UX

- Free users should not be redirected immediately into payment when they click Premium-only quiz actions.
- During the current pre-launch billing phase, upgrade CTAs open a reusable `Premium is coming soon` modal instead of checkout.
- The coming-soon modal should list:
  - `Challenge Quiz`
  - `Adaptive Practice`
  - `Weak Concept Training`
  - `Higher monthly limits`
- Modal actions are:
  - `Join Waitlist`
  - `Maybe Later`
- Joining the waitlist calls `POST /api/premium/waitlist`.
- Waitlist joins are idempotent per authenticated user and should return:
  - `You're on the list! We'll notify you when Premium launches.`
- Challenge Quiz, Adaptive Practice, Study Pack limit blocks, Settings billing, pricing CTAs, dashboard upgrade cards, and near-limit banners should all route through this pre-launch waitlist flow.
- At `80%` of the Free Study Pack limit, show a non-blocking warning banner on:
  - Dashboard
  - Note Detail
  - Study Pack generation/editor surfaces

## Pricing page and upgrade positioning

- NoteLib pricing copy should position Premium as an exam preparation and mastery plan, not only as an AI upgrade.
- Pricing page hero copy:
  - title: `Study smarter. Pass exams faster.`
  - subtitle: `Turn your notes into summaries, quizzes, and reviewers in seconds.`
  - identity line: `NoteLib is a notes library and review tool in one — you build your own reviewers, then turn them into summaries, key concepts, and practice quizzes.`
  - actions: `Start Free` and `Upgrade to Premium`
- Pricing page must display localized pricing from `GET /api/billing/pricing`.
- Until payments are enabled, pricing CTAs should open the Premium waitlist modal rather than redirect directly into checkout.
- Pricing page should compare Free vs Premium clearly for student workflows:
  - Free: core NoteLib workflow, `10` Study Packs/month, Quick Review, `5` Challenge Quizzes/month, Weak concepts
  - Premium: heavier exam-review workflow, `100` Study Packs/month, `50` Challenge Quizzes/month, `30` Adaptive Practice sessions/month, Difficulty selection, Priority AI generation
- Dashboard should show a Free-only upgrade card with Premium exam-prep messaging and the same waitlist modal entry point.

## Billing architecture

- Controller/service layer is provider-agnostic (`BillingService` interface).
- Active runtime provider is `PAYMONGO`.
- Backend is the single source of truth for Premium pricing, region resolution, voucher eligibility, and PayMongo plan selection.
- Frontend pricing surfaces must read pricing from `GET /api/billing/pricing` and must not hardcode subscription amounts.
- Premium checkout plumbing may remain in place behind the provider abstraction, but the current user-facing conversion flow is waitlist-first until payment launch is enabled.

## Premium waitlist

- Waitlist persistence lives in `premium_waitlist`.
- Stored fields:
  - `id`
  - `user_id`
  - `email`
  - `created_at`
- Only one waitlist row is allowed per user.
- Admin reporting should surface the waitlist total as a core Premium-interest metric.

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

## Billing history API

- `GET /api/billing/history` returns subscription summary data plus payment history.
- Response contract:
  - `currentPlan`
  - `subscriptionStatus`
  - `billingType`
  - `currentPeriodStart`
  - `currentPeriodEnd`
  - `cancelAtPeriodEnd`
  - `cancellationEffectiveAt`
  - `transactions[]`
- Each transaction item includes:
  - `id`
  - `date`
  - `description`
  - `amount`
  - `currency`
  - `status`
  - `provider`
  - `providerReferenceId`

## Centralized plan API

- `GET /api/me/plan` is the frontend source of truth for plan limits, usage, remaining counts, and feature flags.
- Response contract includes:
  - `plan`
  - `limits.studyPacksPerMonth`
  - `limits.challengeQuizzesPerMonth`
  - `limits.adaptivePracticePerMonth`
  - `limits.ocrPerMonth`
  - `usage.studyPacksUsed`
  - `usage.challengeQuizzesUsed`
  - `usage.adaptivePracticeUsed`
  - `usage.ocrUsed`
  - `remaining.studyPacksRemaining`
  - `remaining.challengeQuizzesRemaining`
  - `remaining.adaptivePracticeRemaining`
  - `remaining.ocrRemaining`
  - `features.adaptivePracticeAvailable`
  - `features.difficultySelectionAvailable`
  - `features.fileUploadAvailable`
  - `features.ocrAvailable`
- Frontend gating and usage UI should rely on this API instead of hardcoded limits or Premium flags.
- `usageCycle.startsAt` and `usageCycle.endsAt` define the current billing-period window used for quota reset messaging.

## Settings usage UI

- `Settings -> Plan & Billing -> Monthly Usage` should render progress bars instead of plain counters.
- Free users see:
  - `Study Packs`
  - `Challenge Quiz`
- Premium users also see:
  - `Adaptive Practice`
- OCR usage remains hidden from the Settings UI even though it is still tracked and enforced in backend.
- Progress bar colors:
  - `0-60%`: blue
  - `60-85%`: orange
  - `85-100%`: red
- When a visible limit is reached:
  - show `You've reached your limit for this cycle.`
  - show the reset date from `usageCycle.endsAt`
  - show `Upgrade to Premium` for Free users only

## Usage reset windows

- Quotas do not reset on calendar month boundaries.
- Free users reset monthly from the account creation date anchor.
- Premium users reset from the active subscription billing window.
- Persisted `user_usage.period_start` and `user_usage.period_end` define the active quota cycle for:
  - Study Packs
  - Challenge Quiz
  - Adaptive Practice
  - OCR

## OCR display and limit UX

- Landing/pricing surfaces should not show numeric OCR quotas.
  - Free: `Limited`
  - Premium: `Higher Limits`
- Settings should not show OCR usage counters even though OCR usage is tracked internally.
- When OCR quota is exhausted in note import:
  - Free users see an `OCR limit reached` modal with an upgrade path
  - Premium users see an `OCR limit reached` modal that explains reset happens on the next billing date

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
