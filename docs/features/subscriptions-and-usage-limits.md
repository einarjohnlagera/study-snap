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

Challenge Quiz has no manual difficulty selector (removed v0.60.1) on any plan; difficulty is fully automatic from the last Quick Review score.

The generation allowance is user-facing as **“AI quizzes”** on every usage meter, the pricing page and the quiz-generation dialog. **⚠️ The explanatory line “Challenge Quiz sessions and quizzes you make for someone” appears on the Dashboard usage card ONLY** — `UsageMetric` in Settings takes no description prop, so the Settings meter shows the bare label. Do not restate the description as appearing everywhere the label does. The Challenge Quiz mode keeps its product name; only usage-meter and pricing labels use the broader quota name. Both jobs continue to spend the single `challenge_quiz_generations` counter.

### Free

- `10` Study Packs / month
- `20` AI quizzes / month
- `3` shareable quiz links / month
- topic note generation: backend-configured Free limit (`5` by default)
- OCR: backend-configured Free limit (`20` by default)
- exports: `2` / month
- Quick Review available
- Challenge Quiz available
- weak concepts visible after quiz completion
- Adaptive Practice available and quota-limited (`3` / month by default)
- Board Exam Mode unavailable

### Plus

- `50` Study Packs / month
- `100` AI quizzes / month
- `10` shareable quiz links / month
- topic note generation: backend-configured Plus limit (`25` by default)
- OCR: backend-configured Plus limit (`50` by default)
- exports: `15` / month
- higher usage limits than Free
- Adaptive Practice available and quota-limited (`10` / month by default)
- Board Exam Mode unavailable

### Pro

- `100` Study Packs / month
- `200` AI quizzes / month
- unlimited shareable quiz links
- Board Exam Mode uses the shared AI-quiz budget and has a dedicated `10` source-note units / month hard cap; quota is deducted per source note (a 3-note session costs 3 units)
- topic note generation: backend-configured Pro limit (`100` by default)
- OCR: backend-configured Pro limit (`100` by default)
- exports: unlimited
- Adaptive Practice available and quota-limited (`30` / month by default)
- Long Exam available and quota-limited (`12` source-note units / month by default; quota is deducted per source note)
- Board Exam Mode available

For actual behavior and gating decisions:

- use backend plan limits and feature flags
- treat `GET /api/me/plan` as the frontend contract

`GET /api/me/plan` includes the share-link monthly limit, used count and remaining count; unlimited uses the same `null` representation as other unlimited limits. The “Quiz for someone” dialog displays both AI quizzes remaining and share links remaining before generation. An exhausted share-link allowance is informational there: generation and export remain valid, while the existing link-creation path remains the only enforcement point.

## Concept due and mastery signals

Due and mastery status is visible to every plan tier, including Free. The concept-health response exposes the minimum `lastCorrectAt` value needed for the client to distinguish `Due`, `Mastered`, and `Not started`; it must not turn a due concept into a misleading not-started state for Free users.

This is signal visibility only. It does not grant Adaptive Practice, consume or change any quota, or alter `Feature.ADAPTIVE_QUIZ` / weak-concept action checks. Detailed elapsed-time review copy, incorrect-answer history, and struggling-concept details remain available only on the existing paid timing path.

## Study Pack limit UX

- remaining Study Packs come from backend usage calculations
- when remaining reaches `2` or `1`, show the near-limit warning banner
- when remaining reaches `0`, keep `Generate Study Pack` clickable and show the appropriate limit/paywall modal on click
- quota increments only after a successful Study Pack is persisted
- saving a note, failed generation, or failed retry must not consume Study Pack quota

## Topic note generation and OCR

Topic note generation and OCR are distinct monthly quotas from Study Packs.

Long Exam and Board Exam quotas are distinct monthly counters:

- Long Exam is Pro-only and consumes `longExamUsed` per source note (not per session) — a 3-note Long Exam deducts 3 units; quota is checked and incremented only after successful session generation starts
- Board Exam is Pro-only, consumes the shared Challenge Quiz budget, and also consumes `boardExamUsed` per source note — a 3-note Board Exam deducts 3 units
- Board Exam must be blocked when either the Challenge Quiz budget is exhausted or the Board Exam hard cap is exhausted
- Active sessions can always be resumed regardless of quota state; quota is only checked when starting a new session
- Challenge Quiz, Adaptive Practice, Interview Practice, Study Pack, topic note generation, OCR, and export quotas remain separate from these counters unless explicitly stated above

When topic note generation is exhausted:

- Free users hit the upgrade path
- paid users get reset-date messaging instead of a billing redirect
- user-facing copy calls the metered unit a **topic note**, never a "note generation" or a "note draft" (v0.68.0) — "Draft" is already a distinct user-visible state derived from `studyPackStatus === "DRAFT"`, and a hand-written note is a Draft that consumes no topic-note quota. `topic note generation` remains the internal name of the quota mechanism (and of every backend field: `noteGenRemaining`, `noteGenerationsRemaining`, the `note-generation-limit` CTA context, and the `GENERATE_NOTE_LIMIT` / `GENERATE_NOTE` analytics identifiers) — the rename is user-facing copy only

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

## Pass expiry reminders

The existing daily expiry email cadence uses a 7-day stage (6–8 days remaining) and a 1-day stage (24 hours remaining). Settings mirrors those same stages in-app using the already-loaded `premiumEndsAt` value; it does not introduce a backend trigger or client-side plan inference.

Only active Plus and Pro passes with a future end time qualify. The renewal CTA starts the existing checkout flow for the current plan and preserves the one-time-pass model. Each stage is separately dismissible for that pass end time, so a dismissed 7-day notice does not hide the 1-day reminder.

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
