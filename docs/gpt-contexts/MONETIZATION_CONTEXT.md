# NoteLib — Plans, Pricing & Payments

> **Module — not a standalone brief.** Paste `GPT_CONTEXT.md` first; this file assumes it.
> Paste this module when the conversation is about **pricing, plan tiers, paywalls, or checkout**.
> Last updated: v0.101.0 - 2026-09-01 (Released). **`v0.101.0` renamed the generation meter and SPLIT its constant.** The meter reads **`Quiz generations`** and the pricing page reads **`generated quizzes`** — two values, not one, because the shared constant fed four `src/config/plans.ts` strings and a meter fix rewrote public pricing copy. **⚠️ It also corrected a claim this module itself carried: the counter does THREE jobs, not two — Board Exam spends it too, and no copy said so until now.** Previously v0.92.0 - 2026-08-27 (Released). **⚠️ This module had gone SIXTEEN releases stale (v0.76.0) while being pasted into sessions as fact** — restamped at the `v0.92.0` signoff with the monetization-relevant changes from that span. The three paywall-copy rules and the `getUpgradeCtas` rule below are unchanged and still current.

---

## Plans, Pricing, and Payments

Runtime entitlement source of truth is the backend subscription model and `GET /api/me/plan`.

- Plans: Free, Plus, Pro. Checkout: Xendit hosted checkout via backend `POST /api/payments/create`. Paid access is granted only by validated webhook-confirmed payments.
- Pricing is backend-owned; frontend pricing surfaces use billing/pricing APIs and shared plan config.
- **Paid plans are one-time, time-boxed passes in UI copy, not auto-renewing subscriptions.** This is a load-bearing fact — it's why the "recurring vs. one-time" framing in competitor comparisons doesn't actually apply to NoteLib.
- Cancellation is scheduled for period end; paid access remains active until then.
- Do not add plan flags to `users`. Do not change prices, quota numbers, pass durations, billing, or checkout mechanics as part of readiness/UX work unless explicitly scoped.
- New question *formats* are explicitly kept out of plan-gating (see the Quiz / Practice Mode Contract in `QUIZ_AND_PRACTICE_CONTEXT.md`) — a considered exception, not an oversight.
- **Quota numbers currently live in two independent places that must move together:** backend enforcement defaults (`application.yaml`) and frontend marketing/display copy (`frontend/lib/pricing-config.ts`'s `pricingConfig` object) — the latter is not derived from the former. A quota change that only touches one will silently desync marketing copy from actual enforcement.
- **Pricing itself stays unchanged as of `v0.61.0`** — see the Company Redefinition section in `STRATEGY_AND_ROADMAP_CONTEXT.md` for the full resolution (quota raised now, price deferred pending paywall/retention/usage data). Do not propose a price change as if this is still an open debate; it's been resolved for now, revisit only once that data exists.
- **Plan taglines were re-messaged in `v0.68.0`; plan *names* were not.** `PLUS.title` → "Guided learning built around your notes", `PRO.title` → "Your complete learning system" (from "For regular study" / "Best for exam prep"). `name: "Plus"` / `name: "Pro"` are **untouched everywhere** — checkout, Settings, badges, receipts, support — a deliberate decision to avoid a bifurcated vocabulary, the same reasoning that kept Creator/Curated Learning internal-frame-only. **`FREE.title` was the last item owed here and it CLOSED in `v0.76.0` (2026-08-14): it is now "Start with ready-made study material".** The history is worth keeping because it is the standing test for this kind of copy: an outcome-framed candidate was written and reverted in `v0.68.0` for being derived from Plus/Pro consistency, which contradicts the ratified **FREE=adopt** placement; a second candidate ("a complete review") was rejected in `v0.76.0` for over-promising, since **~18% of learners have no Official Review Set for their program** — four published sets cover 179 of 218 program-holding accounts. "Material" is deliberately broader than "a Review Set" and stays true for them.
- **Paywall copy follows three rules ratified in `v0.76.0`** — full contract in `docs/features/pricing.md`. **(1) Upgrade BUTTON labels stay feature-named.** A button fired when a learner clicked Board Exam Mode says `Unlock Board Exam Mode`, because a button states *what the click does*, not *why to care*. Do not propose replacing these with system-level promises. **(2) Paywall HEADLINES split by type:** capability paywalls (nothing was used up) carry the narrative; quota paywalls keep a factual headline, because a learner who just hit a wall needs to know that is why the modal appeared. **(3) `PLAN_CARD_SUBTEXT` describes the TIER, never a feature** — it is keyed on plan type alone and renders on *every* paywall, which is how Adaptive Practice copy ended up on the Interview Practice and Board Exam Mode modals before `v0.76.0` fixed it.
- **The paywall modal renders upgrade *targets* only** (`PlanCard` is typed `"PLUS" | "PRO"`). Free is represented by a single line, not a card — a Free card would look selectable and lead nowhere.
- **Four separate components render plan `title`/`description`** — `/pricing`'s `PricingPlansSection`, the landing page's `SimplePricingSection`, `app/settings/page.tsx`, and `components/billing/paywall-modal.tsx`. `v0.68.0` shipped a real misalignment bug because plan `description` lengths had drifted to 62/96/133 characters, which cannot render at equal line counts in a multi-column card grid. They are now balanced at **88/88/92**. If you propose changing a plan description, keep the three within a few characters of each other, or you will silently break card alignment on four surfaces at once.

### The generation meter is called "Quiz generations" (`v0.101.0`) — and it is ONE counter doing THREE jobs

- **`user_usage.challenge_quiz_generations` is spent by Challenge Quiz, Board Exam AND "Quiz for someone."**
  Free 20 / Plus 100 / Pro 200 per month. **⚠️ `v0.92.0`'s framing of this as TWO jobs was incomplete — Board
  Exam spends a unit on every start and no copy said so until `v0.101.0`.** The only three spend sites are
  `ChallengeQuizService:235` (Board Exam, pooled), `ChallengeQuizService:343` (Challenge Quiz and Board Exam,
  live) and `GeneratedQuizService:158`. **`+5 Questions` spends nothing, so the metered unit is a session or
  quiz CREATED** — never a question and never a generation call.
- **The meters and the quiz-generation dialog label it "Quiz generations"; the pricing page says "generated
  quizzes".** **⚠️ THOSE ARE TWO CONSTANTS ON PURPOSE and re-merging them is a regression a test pins.** They
  were one shared value until `v0.101.0`, interpolated into four `src/config/plans.ts` strings, so renaming the
  meter silently rewrote public pricing copy. The meter names the metered **act**; pricing names a **count**.
- **The description is "Quiz sessions we generate for you, plus quizzes you make for someone. Board Exam
  sessions also count against their own allowance."** **⚠️ Deliberately mode-agnostic** — a later Free/Plus
  multi-note session would ride the Challenge engine and spend this same meter, so naming today's modes goes
  stale by construction. **⚠️ And deliberately NOT a list of who has their own allowance** — Board Exam spends
  BOTH this meter and `board_exam_used_this_month`, and Settings renders a Board Exam row directly beneath it,
  so any such list is falsified by a row the reader is already looking at.
- **⚠️ The quota LABEL and the Challenge Quiz MODE name are deliberately DIFFERENT strings**, pinned by a
  regression test. The mode keeps its product name everywhere it names the mode. **A global find-and-replace
  unifying them is a known failure and must not be proposed.**
- **⚠️ There were THREE vocabularies for this one counter before `v0.92.0`** — the Dashboard said *Challenge
  Quiz*, Settings said *Quiz*, and the API fields say `challengeQuizzes*`. The API field names were deliberately
  NOT renamed (existing contract with test coverage); only user-facing labels changed.
- **⚠️ NO SECOND COUNTER.** A separate meter for quizzes made for someone else is **a pricing decision nobody has
  taken**. Do not propose one as a "clarity" fix — it changes what people pay for.
- **⚠️ Known limitation, live today:** the meters are unified but the **exhaustion copy is not**. The paywall
  headline still says *"quiz generation limit"* and the server message *"monthly quiz credit limit"*, so a user
  who watches a *Quiz generations* meter run out is then told about two differently-named limits.

### Quiz share links are a second, cheaper meter — disclosed early, still enforced late

- **Free 3 / Plus 10 / Pro unlimited per month**, enforced by `QuizShareLimitService` at **exactly one call site:
  link creation.** `GET /api/me/plan` carries the limit, used and remaining counts (added in `v0.92.0`); unlimited
  uses the same `null` representation as the export limits — **do not invent a new sentinel.**
- **⚠️ Do NOT move the share-link check into the generation path.** Generating without sharing is legitimate
  (teacher export, regenerating before sharing). `v0.92.0` surfaced the cap **earlier**; it deliberately did not
  apply it earlier. The distinction is load-bearing: the cheaper limit being enforced last was the defect, and
  disclosure — not enforcement — was the fix.
- **`v0.89.0` removed the `TEACHER` gate on creating a share link.** Anyone can make a quiz for someone; the
  recipient needs **no account and no relationship**. Teachers still exclusively keep DOCX export, multi-version
  exports, question-count control and the Exam Builder.
- **Learning Connections costs nothing and has no plan dimension** — no per-learner charge, no shared or
  transferred quota, no sub-accounts. A supporter generates on their own account against their own allowance.
  `v0.91.0` (note sharing) and `v0.92.0` (activity sharing) added **no** paid surface. Do not propose gating any
  part of Learning Connections by plan without treating it as a new pricing decision.

Upgrade CTA rule:

- Use `getUpgradeCtas(currentPlan)` from `frontend/src/config/plans.ts`.
- Free -> primary `Upgrade to Plus`, secondary `Go Pro`.
- Plus -> primary `Upgrade to Pro`.
- Pro -> no upgrade CTA.

---
