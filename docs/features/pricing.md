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
- Plus -> `Choose Plus`
- Pro monthly -> `Go Pro`
- Pro yearly -> `Go Pro Yearly`

Important:

- the public landing page still uses `Start for Free`
- the full pricing page uses `Get Started Free`

## Current plan messaging

### Free

- `10` Study Packs / month
- `5` Quizzes / month
- `2` exports / month
- `Summary + Key Concepts`

### Plus

- `₱149 first month, then ₱179/month` in PH when intro pricing is eligible
- positioned as the regular-study tier
- `50` Study Packs / month
- `25` Quizzes / month
- `15` exports / month
- higher note-generation limits

### Pro

- `₱199 first month, then ₱249/month` in PH when intro pricing is eligible
- `₱1,999/year` in PH for annual checkout
- positioned as the exam-prep tier
- `100` Study Packs / month
- `50` Quizzes / month
- unlimited exports
- difficulty selection
- Board Exam Mode

## Important implementation note

Pricing surfaces currently market Plus and Pro through the shared plan config.

System behavior is still enforced from backend plan rules and `GET /api/me/plan`.

Current enforcement truth:

- Board Exam Mode is Pro-only
- Difficulty selection is Pro-only
- Adaptive Practice access is currently gated as Pro-only in runtime

If pricing copy and backend feature gates diverge, backend gating remains the behavior source of truth until the product intentionally changes it.

## Regional pricing

Pricing surfaces keep PHP visible for Xendit clarity and also show international USD display pricing.

Visible reviewer-safe display values currently come from the shared pricing config when API data has not resolved yet.

## Messaging rules

- Free should feel useful, not broken
- Plus should feel like the practical step-up for regular study
- Pro should feel like the strongest exam-prep and mastery tier
- pricing surfaces should stay student-friendly and avoid aggressive billing language
- trust copy should reinforce manual renewal and hosted checkout
