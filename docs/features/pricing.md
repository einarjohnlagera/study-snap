# pricing.md - NoteLib Feature Context

## Goal

Keep pricing surfaces consistent, trustworthy, and easy to update.

Pricing UI should explain the Free -> Plus -> Pro path clearly, while checkout amounts and eligibility stay backend-owned.

## Source of truth

Frontend pricing copy is centralized in:

- `frontend/src/config/plans.ts`

That shared config owns:

- plan names
- descriptions
- CTA labels
- feature lists
- comparison-table rows

Checkout pricing is **not** hardcoded in the UI.

Backend pricing is resolved through billing APIs and pricing services for:

- region
- currency
- intro-offer eligibility
- actual checkout amount

## Current pricing page

Route:

- `/pricing`

Current structure:

1. hero
2. Free / Plus / Pro pricing cards
3. plan comparison table
4. regional pricing block
5. FAQ

Current CTA labels on the pricing page:

- Free -> `Get Started Free`
- Plus -> `Get Plus — <price>` (single CTA; Plus has only the 1-month pass)
- Pro -> hero `Get Pro — <price> / 90 days` (the 90-day Exam Pass), with a small `Also available: 1 month · 1 year` line linking to those checkouts. One visual CTA per card; the duplicate `Go Pro` / `Go Pro Yearly` / `Go Pro — 90-Day Exam Pass` buttons were removed.

Important:

- the public landing page still uses `Start for Free`
- the full pricing page uses `Get Started Free`

## One-time pass framing (v0.32.1)

Plus and Pro are **one-time, time-boxed passes with no auto-charge**, not recurring subscriptions. Pricing copy must reflect this:

- **Price reads as duration, not billing frequency.** Cycles render as `₱X / 1 month`, `₱X / 90 days`, `₱X / 1 year` — never `₱X/month` as a recurrence. Centralized in `getBillingCyclePriceLabel` / `getExamCyclePriceLabel` (`lib/billing-pricing.ts`); the EXAM_CYCLE duration comes from `durationDays`, never a literal.
- **Quota stays monthly, with a clarifier.** Per-month limits remain accurate; cards carry `Usage limits refresh each month during your pass.` so "one-time pass" and "monthly limits" do not read as contradictory.
- **Intro pricing is a first-pass discount, not a recurrence.** Render as `₱149 for your first 1-month pass · ₱179 after` — never `first month, then ₱179/month`.
- **Reassurances** live in shared constants in `plans.ts` (`PASS_MODEL_TAGLINE`, `PASS_QUOTA_REFRESH_NOTE`, `PASS_DATA_PERMANENCE_NOTE`, `PASS_ALL_ACCESS_NOTE`, `PASS_NO_AUTO_CHARGE_FOOTER`): one-time payment · never auto-charged; notes, Study Packs, and progress stay in the library after a pass ends; full access on desktop and mobile web (responsive web — there is no native app).
- **Settings billing-status copy stays accurate** for an active pass (e.g. `Valid until …`, `Won't auto-renew`); only pre-purchase/marketing strings were reframed. No billing, quota, pass-duration, price, or checkout mechanics changed.
- **Settings pass-length selector.** The Settings plans cards use one 3-segment selector (`1 month · 90 days · 1 year`, segments rendered by availability) instead of a `Monthly / Annual` toggle plus a separate exam-pass button. The selected length drives `effectiveProCycle`, a single Pro price line, and one CTA — `Get Pro` for the 1-month pass (the long monthly-intro string stays out of the button) and `Get Pro — <price>` for the 90-day / 1-year passes. Plus remains a 1-month pass only.

## Current plan messaging

### Free

- `10` Study Packs / month
- `5` Quizzes / month
- `3` Adaptive Practice sessions / month
- `2` exports / month
- `Summary + Key Concepts`

### Plus

- `₱149 for your first 1-month pass · ₱179 after` in PH when intro pricing is eligible
- positioned as the regular-study tier
- `50` Study Packs / month
- `25` Quizzes / month
- `15` exports / month
- higher note-generation limits

### Pro

- `₱199 for your first 1-month pass · ₱249 after` in PH when intro pricing is eligible
- `₱1,999 / 1 year` in PH for the annual pass; `₱599 / 90 days` for the 90-day Exam Pass (hero CTA)
- positioned as the exam-prep tier
- `100` Study Packs / month
- `50` Quizzes / month
- unlimited exports
- difficulty selection
- Board Exam Mode
- Adaptive Practice: Free `3` sessions / month, Plus `10`, Pro `30`

## Important implementation note

Pricing surfaces currently market Plus and Pro through the shared plan config.

System behavior is still enforced from backend plan rules and `GET /api/me/plan`.

Current enforcement truth:

- Board Exam Mode is Pro-only
- Difficulty selection is Pro-only
- Adaptive Practice is quota-gated by plan: Free `3` sessions / month, Plus `10`, Pro `30`

If pricing copy and backend feature gates diverge, backend gating remains the behavior source of truth until the product intentionally changes it.

## Regional pricing

Pricing surfaces keep PHP visible for Xendit clarity and also show international USD display pricing.

Visible reviewer-safe display values currently come from the shared pricing config when API data has not resolved yet.

## Messaging rules

- Free should feel useful, not broken
- Plus should feel like the practical step-up for regular study
- Pro should feel like the strongest exam-prep and mastery tier
- pricing surfaces should stay student-friendly and avoid aggressive billing language
- trust copy should reinforce one-time passes (never auto-charged), monthly usage refresh during a pass, library data permanence, and hosted checkout
