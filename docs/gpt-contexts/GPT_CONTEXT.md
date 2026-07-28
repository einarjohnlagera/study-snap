# GPT_CONTEXT.md - NoteLib Product Context Handoff

> Paste the block below as your first message in a new GPT chat session.
> Update this file whenever a new version ships or the roadmap shifts significantly.
> Last updated: v0.61.0 (Released) - 2026-07-28

---

Here's the current context for our NoteLib product session. Treat this as a compact product snapshot, not a replacement for the repo docs. For implementation work, always defer to `AGENTS.md`, `RELEASES.md`, `docs/product/ROADMAP.md`, and the relevant `docs/features/*.md`.

---

## App: NoteLib

**What it is:** NoteLib is a notes-first study workspace. Users capture notes, generate Study Packs, practice with quizzes/exams, track readiness, and keep a reusable library of learning material.

**Core loop:** Capture -> Generate -> Review -> Improve -> Make a Copy -> Repeat

**Positioning:** Your notes become your study system (external copy, unchanged). **Internal strategy reframed 2026-07-23** as a "learning OS" (see Company Redefinition section below) — external copy hasn't caught up yet; that gap is tracked in `docs/product/ROADMAP.md`'s Backlog Index as a small, not-urgent item requiring its own conversion-test discipline before changing, same bar already applied to pricing.

**Rebrand note:** The product is NoteLib. Code, package names, and database/schema names still use `studysnap` in many places unless explicitly changed.

**Current baseline:** **No version currently in progress.** `v0.61.0 - Challenge Quiz Quota Increase` shipped and signed off 2026-07-28 (base branch `releases/v0.61.0`, two PRs merged to `main`). It **reclaimed the `v0.61.0` minor-version slot from `Explore Convergence`** (now `v0.62.0`, still gate-blocked) — this item's gate (owner ratification) cleared same-day while Explore's Diagnostic-Read gate remained unmet, the same slot-reclaim pattern that's happened three times already this cycle (see Company Redefinition section below). Shipped: a substantial Challenge Quiz monthly quota increase (FREE 5→20, PLUS 25→100, PRO 50→200) and Challenge Quiz LLM token/cost telemetry (net-new — Challenge Quiz had never recorded token usage before this). A bonus fix also shipped: `ONBOARDING_V2_ABANDONED` was over-firing on every onboarding step transition and leaking with no matching `ONBOARDING_V2_STARTED` on several early-return redirect paths — found investigating this release's own onboarding funnel data, fixed with one shared invariant. `v0.62.0 - Explore Convergence` is the reserved next slot but stays un-kicked-off, gated on the Diagnostic Read (due ~2026-08-06). **Read "Company Redefinition & Where Things Stand Now" below before proposing what comes next — it supersedes the old single-retention-bet framing and is the single most important section in this file right now.**

---

## Retention Is the Proven Constraint (read this before proposing anything)

**The number:** W1→W2 retention is **2.4%** (production read, 2026-07-15; 3 of 127 eligible activated users returned in week 2). This has been the core strategic constraint since v0.32.2 first flagged it (was 5.6% then) — it has **not meaningfully improved** despite three intervening feature releases aimed at it (v0.44.0, v0.46.0, v0.48.0). Free-tier quota was essentially never hit at the old limits (5/25/50/month), which is one reason the owner ratified a large quota increase in v0.61.0 rather than treating quota as the retention lever on its own — see the Current Release note above.

**Diagnosis (two independent Fable sessions converged):** every content-rich retention trigger the product has shipped **default-OFF**, gated behind the exact engagement it's meant to create. The first study session also ends in a psychologically "complete" feeling (Zeigarnik effect) rather than an open loop that pulls the learner back. `v0.48.0` (merged 2026-07-15) shipped the cheap fixes for both (open-loop first-quiz ending, due-concepts digest default-ON) — **both remain UNPROVEN, mechanism shipped, lift not measured.** Do not describe either as a retention win in external-facing copy.

**H1+H5 (commitment device + pre-decided return action) is the pre-committed next retention move if the v0.48.0 cohort re-read is positive-or-ambiguous — still gated, not abandoned.** The re-read needs a cohort that actually experienced the v0.48.0 changes to clear its 14-day W1→W2 window: that window closes **2026-07-29 (tomorrow, as of this update)**. The query is written and ready (`next-priority-new-user-focus-out/02-h1-h5-cohort-recheck-and-cpale-depth.sql` Query 1) but has not yet been run as of this update.

**The 2026-07-24 signup surge reversed the "go straight to Phase 2" plan and inserted a Diagnostic Read + a new Reusable Practice Assets initiative ahead of it — full detail in the Company Redefinition section below, which supersedes the old post-v0.48.0 sequencing.** The retained/churned exam-dated user interview script is still written, ready, zero engineering cost, and still hasn't been run — the one open item on this whole track that can't happen from a keyboard.

**The target habit was redefined 2026-07-28 — read Round 2 through this, not the raw blended 2.4%.** The single W1→W2 calendar-week boolean is retired as the universal yardstick. Segment by whether `UserEntity.examDate` is set (not `profile_type` — a coarser proxy for the same thing). Exam-bound learners (the majority — `BOARD_EXAM` alone is 70.94% of profile-typed accounts, confirmed product-wide, not a surge artifact — see the population-mix finding below) have a naturally episodic arc: signup → sustained practice → sit the exam → legitimately stop. They're scored only once their exam date has passed, on whether they had activity in the final 7 pre-exam days; still-in-flight users are excluded from the denominator entirely rather than penalized on a calendar clock that doesn't match their arc — going quiet only *after* the exam is not churn. Open-ended learners (`STUDENT`, no exam date set, ~27%+) keep the existing W1→W2-style frame, which fits them better. **This does not excuse the existing 0/41 exam-dated-retention finding** (users retaining below their own exam date) — that's disengagement *before* the goal, a real problem under either frame. Expect a small scored group at first (recent signups, PRC-clustered exam dates) — a near-single-digit denominator means "not yet measurable," not a verdict. Full definition: `docs/product/ROADMAP.md`'s "Target-habit definition" Backlog Index row.

**Full backlog, current status, and exactly what un-parks each item lives in `docs/product/ROADMAP.md`'s Backlog Index table (~55 rows) — check it before proposing or resurfacing anything.** See "Roadmap Candidates: Gated & Ungated" below for a synthesized, status-grouped view of that same table, restricted to items still actually open (shipped/resolved rows are dropped from that view — check `RELEASES.md` for those). Do not propose roadmap items from partial memory of past sessions; the index is the current source of truth.

---

## Company Redefinition & Where Things Stand Now (read this before proposing what comes next)

**The strategic redefinition (2026-07-23).** The owner encountered `boardready.ph` (a simpler PH board-exam-review competitor with strong traction) and worked with GPT toward a company-level reframe: NoteLib as a **"learning OS"** — a learner's own notes become curriculum, curation turns that curriculum into a compounding reusable asset, AI is the machinery behind the curtain, never the thing a learner is asked to trust directly. A 6-session Fable plan produced a full design synthesizing this onto already-shipped architecture (Companion, Goal→Subject hierarchy, ConceptHealth, `ExamQuestionPool`) — `docs/claude-prompt/company-redefinition-out/01-09`. **`v1.0.0` is explicitly reserved for later**, tagged only once this redefinition's user-visible core is live and the product succeeds.

**Where each of the 4 phases actually stands today:**
- **Phase 1 (practice-first activation onboarding) — shipped as `v0.57.0`.** A `BOARD_EXAM` learner with a depth-qualifying Official Review Set skips note-authoring entirely, adopts in one tap, lands on the set's detail page. Zero LLM call on this path.
- **Diagnostic Read — ratified, Round 1 run 2026-07-24/25, re-read due after 2026-08-06, not yet run.** A real-time signup surge (~29 signups on 2026-07-23 alone) reversed the "go straight to Phase 2" decision made hours earlier that same day. Round 1 was inconclusive by construction (the surge cohort's 14-day window hadn't closed) but found one real, durable signal: a **chronic ~50% onboarding non-completion rate across recent signups generally** — not surge-specific, the surge day actually completed onboarding *better* than baseline. Full results: `company-redefinition-out/08-diagnostic-read-methodology.md`.
- **Reusable Practice Assets & the Return Loop — shipped as `v0.58.0`.** Turned on Board/Long Exam's dormant per-user question pool, extended the same per-user pattern to Challenge Quiz, added a "redo what you missed" surface reusing existing `ConceptHealth`/weak-concept machinery. Framed as a retention primitive (a learner who never gets a second crack at a missed question has no spaced-repetition mechanic to retain against), not a cost play — no token/dollar metering existed anywhere in the system before this.
- **Phase 2 (IA / Explore convergence) — split, re-gated 2026-07-24, half shipped.** `v0.59.0` (Dashboard hero → Primary Review Set card, Progress promotion) shipped once Reusable Practice Assets shipped. `Explore Convergence` (new nav item, segmented Review-Sets/Notes control, Exam Hub additive official-set check) stays gated on the Diagnostic Read showing a discovery problem specifically — still un-kicked-off, now numbered **`v0.62.0`** after three slot reclaims by unrelated work whose own gates cleared first (`v0.60.0` Shared Official Pool Foundation, then `v0.61.0` Challenge Quiz Quota Increase, both ratified/gate-cleared same-day work, not Diagnostic-Read-gated).
- **Phase 3a (cross-user Challenge Quiz template pooling) — shipped as `v0.60.0`.** Retargeted from the original Long/Board Exam pool design after confirming those modes are PRO-gated with ~zero production load; real gate evidence (two Official Review Sets with 23 and 9 concentrated recent adopters) cleared the proposed adoption-volume gate on real data. **Phase 3b** (curator-side pool expansion) stays parked on its own unresolved review-queue dependency.
- **Phase 4 (packaging/terminology)** — drafted, needs an explicit owner decision on its source doc's own "§4 Owner must decide" section. No engineering dependency on anything else, free to move anytime once ratified.

**Since Phase 3a shipped, three more Challenge Quiz patch releases closed real bugs and shaped the mode further — all released, all off `v0.60.0`'s line, none consuming a minor-version slot:** `v0.60.1` (5-bug fix pass found exercising the new template-sharing feature in production — executor saturation, missing shuffle, an inert difficulty selector removed entirely, abandoned sessions silently auto-submitted, a non-functional Redo Missed Questions button); `v0.60.2` (3 narrower known-limitations closed: claim-release transaction isolation, an expiry-vs-completion lock race, a missing Resume/Start-Fresh prompt at one entry point); `v0.60.3` (adaptive initial question count, a Redo-Missed-Questions session-matching fix, an incomplete-submission guard — a 4th scoped item, onboarding coverage-gap capture for `STUDENT` profiles, was **deferred out at signoff** when its gate query found real `STUDENT` presence in the surge cohort, 2/29 = 6.9%, failing the required "effectively zero" bar; it now tracks as its own version-less Backlog Index row, gated on the Diagnostic Read closing).

**Knowledge Impact (creator-recognition dashboard, "your notes helped N learners") — ratified by the owner 2026-07-28, despite its own data gate failing.** GPT originally proposed this off the platform's aggregate engagement growth. A CTO evaluation (`company-redefinition-out/09-knowledge-impact.md`) found ~232 of ~235 public notes were official/admin-curated even then; the actual gate query (finally run 2026-07-28) confirmed and sharpened that: **3 distinct non-official public-note creators** (un-park threshold was order-of-magnitude 20-30+), **697 official vs. 4 community public notes (99.4% official)**, community engagement share ~1.8% of views/~0.07% of copies, and a community-publish rate that's gone to **zero in 2026-07** despite the signup surge. The owner ratified proceeding anyway: near-zero publishing may exist *because* creators get nothing back for it — a chicken-and-egg counter the original CTO evaluation already named as a live, unresolved branch, not a new argument invented to override the data. Not yet scoped — `09`'s "Answers to the memo's 12 questions" is the design brief once it is (passive/pull dashboard, retrospective + aggregate framing, nothing comparative/ranked/real-time, private-to-the-creator).

**Pricing/quota debate (raised 2026-07-25, resolved 2026-07-27/28).** The owner argued current pricing (PLUS ₱179/PRO ₱249, FREE capped at 5 Challenge Quizzes/month) can't convert PH students against `boardready.ph`'s reported ₱99 one-time/~unlimited practice access, and proposed pooling-funded quota loosening plus a price cut. After analysis (NoteLib's plans are already one-time passes, not subscriptions, so the "recurring vs. one-time" framing didn't actually hold; pooling buys generosity, not a price cut — two different levers; FREE quota being "never hit" doesn't discriminate between "quota is genuinely sufficient" and "users disengage before ever approaching it") and a three-way consensus (Claude + independent Fable + independent GPT second opinion, ~90% aligned) to not cut prices without pulling the already-instrumented paywall funnel first, the owner **ratified a resolution 2026-07-28: raise the Challenge Quiz monthly quota substantially now (shipping as `v0.61.0`, see Current Release above), defer the price decrease until paywall conversion / onboarding retention / post-increase usage data exists.** A literal daily-reset quota model (also proposed) was explicitly rejected in favor of a bigger monthly ceiling — real study behavior is bursty around exams, and a daily reset would force the product to dictate the learner's study schedule.

**v0.61.0 shipped both items 2026-07-28** — quota increase and LLM telemetry, both audited (backend 1285 tests, frontend 1544 tests, clean). One documentation-precision correction made at signoff, not a functional bug: Board Exam sessions draw from both their own dedicated 10-unit cap and the shared Challenge Quiz counter this release raised — the dedicated cap remains the binding constraint in every realistic path, so this release strictly improved Board Exam's effective headroom rather than changing its own limit. One real finding logged as a Known Limitation rather than silently dropped: the new telemetry has no reader yet (no baseline, threshold, or query), and by design stays null for bank/template-served sessions — a naive "mostly null" read could be mistaken for low spend rather than most sessions never calling the LLM. Next step: a read of the columns ~30 days post-deploy.

**Profile-type population mix resolved 2026-07-28 (production query, not just the surge cohort):** `BOARD_EXAM` is 70.94% of profile-typed accounts vs. `STUDENT` 27.09% — and this predates the surge (63.3%/34.0% pre-surge), so it's not a surge-only skew. This confirms the `BOARD_EXAM`-only practice-first adopt-a-Review-Set fast-path reaches the majority segment, not a minority carve-out. Separately, 40.1% of all accounts have `profile_type` still NULL, corroborating the Diagnostic Read's ~50% onboarding non-completion finding via an independent measurement path. A same-day funnel re-check (re-running Round 1's own Query 7/Query 8) found onboarding completion trending up (50.4%→58.46%) with no known causal driver — encouraging but not a confirmed trend — and surfaced the `ONBOARDING_V2_ABANDONED` bug fixed in v0.61.0 above.

**Decided firmly, not open for re-litigation:** Adaptive Practice stays **out** of any pooling scope — its entire value is personalized reactivity to one learner's own misses.

---

## Tech Stack

| Layer | Stack |
|---|---|
| Frontend | Next.js App Router, React 19, TypeScript, Tailwind, shadcn-style UI |
| Backend | Spring Boot, Java 21, PostgreSQL, Flyway |
| Auth | JWT, refresh tokens, Google OAuth, email verification |
| Payments | Xendit hosted checkout, webhook-confirmed access only |
| AI/OCR | OpenAI for notes/Study Packs/quizzes, Google Cloud Vision for OCR |
| Analytics | Shared analytics event model; backend persists after commit through async executor |

---

## Product Model

- **Note** is the primary entity. State: `DRAFT`, `GENERATING`, `FAILED`, `STUDY_PACK_READY`. Visibility: `PRIVATE`, `PUBLIC`.
- **Study Pack** is generated content attached to a Note: summary, key concepts, quiz, metadata suggestions, and downstream quiz/exam entry points.
- **Note Collections (Study Plans / Review Sets)** organize owned notes into an ordered, curated unit, with an optional one-level Goal -> Subject hierarchy. This is the product's primary retention lever — see the dedicated vision section below.
- **Learning Companion** is a persisted, curator-authored guidance layer (JSONB) on top-level Review Sets — the "premium guided learning experience" layer riding on top of the Study Plan journey. See the dedicated vision section below.
- **ConceptHealth** is the recency spine for readiness and Progress: `lastCorrectAt`, `lastIncorrectAt`, due/not-due classification, and struggling state. This is the *only* mastery-integrity signal in the app, and it's locked (since v0.37.0) to move only from genuine assessment — see Quiz / Practice Mode Contract.
- **Quiz sessions** share `quick_review_sessions` with mode stored as enum and session state in JSONB. Question **format** (MCQ, True/False, Multi-Select, Matching, Identification, Enumeration) is a separate axis from mode.
- **Flashcards and Memorization** are non-scored review surfaces that sit entirely outside the quiz-session engine — no session row, no `ConceptHealth` write, ever.
- **Generated teacher quizzes** use `generatedQuiz`, not student quiz sessions.

Versioning rule:

- Never auto-regenerate generated content.
- Regeneration is explicit, user-confirmed, owner-only, and updates the existing Study Pack in place.
- Owner self-copy copies authored note fields only.
- Public-note copy is the exception: when the public source has a Study Pack, the copy includes that Study Pack and arrives `STUDY_PACK_READY`.

---

## Note Collections (Study Plans / Review Sets): Vision & Locked Rules

Profile-aware terminology — "Study Plan" (Student / Board Taker), "Lesson Plan" (Teacher), "Review Set" (Professional) — all the same underlying `NoteCollection` entity, labeled through `getCollectionLabels(profileType)`.

**The vision, in one line:** a Note Collection is not a folder — it is a trackable **readiness journey**, and it is the product's primary retention lever (chosen for this role in v0.33.0 when W1→W2 was ~5.6%: give a learner a number that only moves by returning to practice, and a credible zero-notes on-ramp via curated adoptable plans).

**Locked structure and rules:**
- A top-level **Goal** can contain child **Subject** collections through `parent_collection_id` — exactly two levels, no arbitrary depth, no per-module mastery, cycles impossible.
- **Adoption** (admin-published collections) is free, idempotent, makes no AI call, creates a private snapshot copy — source edits never sync into adopted copies. Recursive Goal adopt copies every child Subject plan and note in one action.
- **Readiness** derives entirely from existing `ConceptHealth`/`ProgressReportService` — no new mastery signal, ever — but is deliberately *not* shown everywhere: dedicated plan-detail/`/progress` surfaces show it, execution rows/list cards/published-plan cards/public source plans deliberately do not (list-level mastery display was tried and rolled back — it created role confusion between "browsing" and "monitoring"). Vocabulary is locked: `ready / mastered / due / not started`.
- **Mastery integrity is protected:** Quick Review, Flashcards, and Memorization are locked to never write `ConceptHealth` — only genuine assessment (Challenge Quiz, Adaptive Practice, Long Exam, Board Exam, Interview Practice, Identification, Enumeration) can move the readiness number.
- The **Builder** (`/collections/{id}/builder`) is the single authoring canvas for both Goal and leaf plans — deliberately not a study/monitoring surface, no readiness ring on the Builder itself.
- **Primary Review Set + Weekly Countdown (v0.40.0+):** a nullable `primaryCollectionId` (top-level Goal only) plus optional `target_completion_date` and `studyDaysPerWeek` drive a derived — never stored — weekly countdown (`weeksRemaining`/`conceptsRemaining`/`todaysConceptBudget`). No adaptive/AI scheduling, streaks, or calendar integration — pure read-time derivation from existing readiness + date math.
- **Review Set Detail (v0.41.1)** is composed Identity → Current Journey → Primary Action → Readiness → Guidance (Companion) → Subject Plans/Notes — "what should I do next, in this Review Set," while staying collection-scoped (cross-journey "which set" stays Dashboard's job).

**Intentionally still parked:** standalone adoption of a single child Subject plan (unresolved re-parenting interaction with `adoptGoal`'s idempotency check) — not worth solving without a real discovery need.

---

## Learning Companion: Vision & Locked Rules

**The organizing insight:** Review Centers aren't valuable because they provide PDFs or quizzes — they're valuable because they provide **guidance** (structure, direction, pacing, coaching, confidence). The Companion is NoteLib's guidance layer riding on top of Notes (knowledge) + Study Packs (learning engine) + Review Sets (journey).

**Success criterion:** *"Every Official Review Set should feel like a premium guided learning experience rather than a collection of notes."* Not feature count, not revenue.

**Content model:** a single nullable JSONB column, `note_collections.companion` — 1:1 with a top-level collection only. Five long-form sections (Overview, Study Strategy, Common Mistakes, FAQ, Resources) plus an atomic `mentorTips` array (each tip has its own identity, an optional curator-tagged linked action, and an optional deterministic surfacing condition — never inferred at render time, never adaptive/LLM-driven selection). **No runtime LLM call to serve a Companion** — authored once, served static, zero per-view cost.

**Curation, never generation (locked, clarified not reversed in v0.42.0):** a learner never receives an auto-generated plan or tip. ADMIN-only `Generate Companion` produces a **draft only** — the curator must review, edit, and click Save/Publish, in every path including Mentor Tips. Official-author-only today; FREE for all learners, zero paid uplift on the Companion itself by design.

**Coach vs. Companion, the locked split:**
- **Coach (dynamic).** Reacts to the learner: continue-where-you-left-off, pacing, readiness, due concepts, resolved next action. This is `TodaysFocusCard` — zero new cost, just naming what already existed.
- **Companion (timeless).** Authored, does not react to daily progress. Mindset, expectations, common mistakes, practical advice — reads like mentor advice, not reference material. `CompanionDisplayCard` collapses by default on every viewport behind "View Full Guide."
- **Curriculum.** Subject Plans → Notes → Practice. Unaffected by this split.

**Result-Screen Companion Bridge (v0.55.0):** Quick Review, Challenge Quiz (both branches), and Adaptive Practice result screens show a labeled excerpt of the primary Review Set's Common Mistakes/Study Strategy — curator-published content only, no generation, no mid-exam coaching.

**Monetization philosophy (long-term principle, not a repricing of today's plans):** FREE = static guidance (the Companion itself). PLUS = interaction (future, gated — Ask Companion, grounded Q&A reusing the Interview Practice cost-control template). PRO = personalization (future, gated — genuinely adaptive/learning-pattern-driven guidance selection, explicitly not deterministic rule reordering, which is the FREE-tier precedent). Neither future tier is scoped to a version yet.

**Future, gated, not yet scoped:** AI-generated Review Sets (curator pipeline, gated on an explicit go-decision); Ask Companion (PLUS); Personalized/Adaptive guidance (PRO). See "Roadmap Candidates" below.

---

## Profile Types

| Profile | Current focus | Important rules |
|---|---|---|
| Student | Notes, Study Packs, Quick Review, Challenge Quiz, Adaptive Practice, Long Exam | Target Audience hidden; backend saves `STUDENT`. |
| Board Exam / Exam Reviewer | Exam-date context, Board Exam Mode, readiness for licensure-style prep | Target Audience hidden; backend saves `BOARD_TAKER`. |
| Teacher | Quiz generation, preview, DOCX export, Exam Builder | Teacher flow uses `generatedQuiz` only; never reuse student quiz sessions for preview. No adoption surface — Flashcards/Memorization and the Dashboard adoption nudge are hidden for Teacher. |
| Professional | Certification review, Long Exam as Full Practice Exam, Interview Practice | Target Audience hidden; backend saves `PROFESSIONAL`. |
| Parent | Enum exists, no real product implementation | Do not propose implementation without parent-child relationship design. |

Onboarding is active for verified users. It collects profile type, study goal, input method, Study Pack generation, completion, learner level, and course/program. This flow is **locked** — do not redesign it. Backend content-creating mutations must enforce profile setup for the legacy completed-but-null profile cohort through `ProfileSetupRequiredException`.

---

## Quiz / Practice Mode Contract

The product has a locked hierarchy of five top-level modes:

1. **Quick Review** - all plans, saved questions, review-only (never writes `ConceptHealth`).
2. **Challenge Quiz** - all plans with quota, progressive generation up to 20 questions per session.
3. **Adaptive Practice** - Plus/Pro practice targeting weak concepts.
4. **Long Exam** - Pro exam mode, fixed long-form practice, supports multi-note sources.
5. **Board Exam** - Pro high-stakes exam simulation for Exam Reviewer profile.

Professional **Interview Practice** is a sub-mode of Adaptive Practice, not a sixth top-level mode.

Rules:

- Do not add a sixth top-level mode without updating `docs/product/EXAM_MODES.md` and roadmap/spec docs together.
- Premium exam paywalls fire from Start CTAs after setup/prescreen, not from card click.
- Study Plan premium-exam launches carry `collectionId` and scope additional-note pickers to quiz-ready notes in that plan.

### Question formats (a separate axis from modes)

Within the five modes, individual questions carry a `questionFormat`: `MCQ`, `TRUE_FALSE`, `MULTI_SELECT`, `MATCHING`, plus two free-text formats:

- **Identification** — fill-in-the-blank / name-the-term. Scored deterministically against a generation-time `acceptableAnswers[]` list — no per-submission LLM call.
- **Enumeration** — name every item in a 2–5 item set. Scored all-or-nothing via exhaustive bipartite matching against `acceptableAnswerGroups[]` — no partial credit.

Both formats are Challenge Quiz-only for now, and both are **ungated across every plan tier** — a deliberate stance: question-format variety is a learning-quality dimension, not a monetization lever. Monetization stays in mode-level and quota-level gates.

### Non-engine review surfaces

**Flashcards** and **Memorization** are free, non-scored review surfaces outside the quiz-session engine — no timer, no submit, and critically, **never write `ConceptHealth`**.

- **Flashcards** — flips each `keyConcepts` entry against its matching `quiz[].explanation` as a self-review deck. No new AI call.
- **Memorization** — Flashcards' matching logic plus a real spaced-repetition schedule (simplified SM-2), stored in a separate `memorization_cards` entity, firewalled from `ConceptHealth` by design.

Both sit on the Note Detail Key Concepts tab, hidden from Teacher profile, free for every plan.

---

## Plans, Pricing, and Payments

Runtime entitlement source of truth is the backend subscription model and `GET /api/me/plan`.

- Plans: Free, Plus, Pro. Checkout: Xendit hosted checkout via backend `POST /api/payments/create`. Paid access is granted only by validated webhook-confirmed payments.
- Pricing is backend-owned; frontend pricing surfaces use billing/pricing APIs and shared plan config.
- **Paid plans are one-time, time-boxed passes in UI copy, not auto-renewing subscriptions.** This is a load-bearing fact — it's why the "recurring vs. one-time" framing in competitor comparisons doesn't actually apply to NoteLib.
- Cancellation is scheduled for period end; paid access remains active until then.
- Do not add plan flags to `users`. Do not change prices, quota numbers, pass durations, billing, or checkout mechanics as part of readiness/UX work unless explicitly scoped.
- New question *formats* are explicitly kept out of plan-gating (see Quiz / Practice Mode Contract) — a considered exception, not an oversight.
- **Quota numbers currently live in two independent places that must move together:** backend enforcement defaults (`application.yaml`) and frontend marketing/display copy (`frontend/lib/pricing-config.ts`'s `pricingConfig` object) — the latter is not derived from the former. A quota change that only touches one will silently desync marketing copy from actual enforcement.
- **Pricing itself stays unchanged as of `v0.61.0`** — see the Company Redefinition section above for the full resolution (quota raised now, price deferred pending paywall/retention/usage data). Do not propose a price change as if this is still an open debate; it's been resolved for now, revisit only once that data exists.

Upgrade CTA rule:

- Use `getUpgradeCtas(currentPlan)` from `frontend/src/config/plans.ts`.
- Free -> primary `Upgrade to Plus`, secondary `Go Pro`.
- Plus -> primary `Upgrade to Pro`.
- Pro -> no upgrade CTA.

---

## Previous Releases (condensed — see `RELEASES.md` and `docs/releases/*.md` for full detail)

**v0.61.0 - Challenge Quiz Quota Increase (Released 2026-07-28, two PRs).** Monthly quota FREE 5→20, PLUS 25→100, PRO 50→200 — uniform 4x, preserves the 1:5:10 tier ratio, reuses existing rolling-billing-period tracking. Challenge Quiz LLM token/cost telemetry — wires the same token-extraction machinery Study Pack generation already uses into the Challenge Quiz path, persisted on `quick_review_sessions`, accumulated across a session including "+5" growth; Board Exam, Long Exam, and Adaptive Practice out of scope. Bonus: the `ONBOARDING_V2_ABANDONED` instrumentation fix (see Company Redefinition section above). Price decrease deliberately deferred, not part of this release. Full detail: `docs/releases/v0.61.0.md`, `docs/product/ROADMAP.md`'s own "v0.61.0" section.

**v0.60.x line (Challenge Quiz, all Released, all patch releases off `v0.60.0`, none consuming a minor-version slot):** `v0.60.3` (adaptive question count, Redo Missed Questions session-matching fix, incomplete-submission guard — bundled into one Codex prompt/PR, audited before commit, caught and fixed a real race condition in the redo-session provenance marker); `v0.60.2` (3 known-limitations closed: claim-release transaction isolation, an expiry/completion lock race, a missing Resume/Start-Fresh prompt); `v0.60.1` (5-bug fix pass on the newly-shipped template-sharing feature: executor saturation, a whole-array shuffle that never existed, an inert difficulty selector removed entirely, abandoned-session auto-submit, a non-functional Redo Missed Questions button); `v0.60.0` (Shared Official Pool Foundation — Phase 3a, Challenge Quiz question template sharing across users adopting the same Official content).

**v0.57.0 → v0.59.0 (Company Redefinition Phases 1-2, Reusable Practice):** `v0.59.0` Dashboard & Progress Reorg (Primary Review Set condensed card on Dashboard, Progress promoted to first-class nav); `v0.58.0` Reusable Practice Assets & the Return Loop (per-user pooling turned on for Board/Long Exam, extended to Challenge Quiz, "redo what you missed"); `v0.57.0` Practice-First Activation Onboarding (Phase 1 — see Company Redefinition section above for all three).

**v0.51.0 → v0.56.0 (retention-pivot posture — new-user acquisition + real-time friction capture, not cold outreach to churned users):** `v0.56.0` weak-concept explanation links on quiz results; `v0.55.0` Result-Screen Companion Bridge; `v0.54.1` public note copy correctness fixes; `v0.54.0` CPALE Exam Hub (Wave 2); `v0.53.0` SEO discoverability (Exam Hub depth, organic attribution); `v0.52.0`/`v0.52.1` proactive in-app feedback prompts at first-quiz and early-lifecycle moments (the pivot-defining release — cold outreach to churned users was judged unlikely to work, so the posture shifted to *"we're not chasing our previous users anymore, we're now chasing new users to retain"*); `v0.51.0`/`v0.51.1` read-path performance pass (Private/Public Library, Collection detail, Dashboard).

**v0.39.0 → v0.50.4 (mobile nav, flexible review formats, retention/conversion UX audits, Companion MVP through Mentor Tips):** mobile bottom tab bar (`v0.50.0`+ polish); Identification/Enumeration question formats and Flashcards/Memorization non-engine review surfaces (`v0.39.0`); the full Learning Companion build-out `v0.41.0`→`v0.43.1` (see dedicated vision section above); a multi-release conversion/retention UX audit (`v0.44.0`→`v0.47.1`) that established retention as the proven constraint over conversion.

**v0.31.0 → v0.38.0 and earlier:** the Note Collections adoption model, Goal/Subject hierarchy, Builder, and readiness-as-retention-lever arc (see dedicated vision section above); read-path optimization; production memory-incident response; premium exam paywall/pricing-as-one-time-pass groundwork.

---

## Roadmap Candidates: Gated & Ungated

Synthesized from `docs/product/ROADMAP.md`'s Backlog Index (~55 rows) — the authoritative table. **Shipped/resolved/superseded rows are dropped from this view entirely** (check `RELEASES.md` for those) — this section is restricted to what's actually still open, grouped by status. **Treat the Backlog Index itself as the source of truth if the two ever disagree.**

### Active now — no gate, just not yet done
- **User interviews (retained + churned exam-dated).** Script written, zero engineering cost. The one item on the entire retention track that can't happen from a keyboard.
- **P1 (Google Search Console setup) and P3 (exam-named Learn guides).** Non-engineering — P1 needs domain access, P3 needs a human curator.
- **Knowledge Impact.** Ratified 2026-07-28 despite a failed data gate (see Company Redefinition section above) — needs a scoping pass before it can kick off. Design brief already exists in `company-redefinition-out/09-knowledge-impact.md`. **Checkpoint committed 2026-07-28:** re-run the gate query 30-60 days after this ships — a continued-zero or still-collapsed community-publish rate settles the "can we create creators" hypothesis for good, not a reason to keep waiting. The only trend data available now (publish rate 1→3→0 across May/June/July, zero in the highest-signup month on record) argues against the hypothesis rather than being neutral.

### Gated — condition is close or partially cleared
- **Explore Convergence (`v0.62.0`).** Gated on the Diagnostic Read showing a discovery problem specifically. Re-read due after 2026-08-06, not yet run.
- **Onboarding coverage-gap capture (deferred out of `v0.60.3`).** Design done, unchanged, still valid. Gated on the Diagnostic Read closing (~2026-08-06) AND a clean re-run of its gate query (`STUDENT`-profile presence in the surge cohort must read effectively zero — it currently doesn't, 2/29). **Further from clearing as of 2026-07-28's population-mix query:** `STUDENT` is 27.09% of profile-typed accounts product-wide, not a surge-only artifact — this isn't a narrow surge-cohort question anymore.
- **Retention H1+H5.** Gate: v0.48.0 cohort re-read positive-or-ambiguous. Window closes 2026-07-29 (tomorrow, as of this update) — not yet run.
- **Wave 2 Exam Hubs beyond CPALE** (Civil/Electrical/Mechanical Engineering, Pharmacy, Physical Therapy, Civil Service Exam). Each needs its own production depth check (~25-30 notes); CPALE cleared this, the rest haven't been checked.
- **Price decrease.** Deferred as of `v0.61.0` — needs paywall conversion / onboarding retention / post-quota-increase usage data before revisiting.
- **P7 (exam quick-facts block per hub)** — wait for GSC (P1) first. **P8 (off-page community presence)** — non-engineering, needs an owner to do outreach. **L2 (earned-depth pathway for non-exam subjects)** — double-gated on depth + post-GSC organic-impression data.
- **F9/F10 (client-side caching, denormalized engagement counts)** — pending production evidence that hasn't been checked.
- **Study Effectiveness remaining candidates** (Note Detail tab-order, Study Pack scope surfacing, Adaptive Practice per-question rationale tag, Review Set Detail layout pass, twice-missed-concept re-explanation, Plus-tier review-timing-gate instrumentation). One item from this batch already shipped as `v0.56.0`; the rest are unscoped.
- **Parent Readiness Digest.** Conditional on the H1 read being positive AND an explicit shape decision.
- **Offline Study Pack access.** Heavy mobile usage confirmed (~75%); PDF export volume ruled out as the offline-fallback signal; needs either offline-fallback hit-rate instrumentation (doesn't exist) or a direct interview signal.
- **Bulk Quiz Generation & Teacher-Flow Polish.** Auto-schedules once ≥5 active teacher accounts exist.

### Held indefinitely — behind the retention constraint clearing
- **App Shape Core** (Companion Live Milestones, Concept-to-Note Back-Annotation, "Struggle Map") and **App Shape Polish stragglers.**
- **Photo Capture of handwritten notes.** Next recommended Core-Feature-sized bet once the retention loop is actually proven (W1→W2 lift confirmed), not before.
- **Smart Review Planning (Internal Curator, 7 fully-architected docs in `docs/claude-prompt/fable-out/`).** The single largest piece of paused planning material in the repo. Triple-gated: interviews confirm content-gap churn AND a manual coverage sprint proves lift AND hand-curation saturates.
- **Manual Official-coverage sprint** (hand-curate ALE/PNLE/LET/CPALE gaps). Conditional on interviews surfacing "no content for my exam" as a real churn reason.
- **Listen Mode / Bilingual UI / Study Buddy.** Low priority, gated on an interview signal. Study Buddy specifically confirmed lowest — a pairing mechanic multiplies churn risk at 2.4% W1→W2 rather than countering it.
- **PDF export surfacing.** Do not build — near-zero usage (1 export, ever).
- **Conversion-audit deferred pair** (adoption-count social proof, "Trending this week"). Held on windowed backend engagement counts that don't exist.

### Parked — needs an explicit product go-decision, not a data gate
- **AI-generated Review Sets.** Curator pipeline (public notes → suggest Subject Plans → generate Companion → human review → publish). Effectively closed/ruled out — re-affirmed by the locked curation-never-generation architecture.
- **Runtime Companion — Ask Companion (PLUS) and Personalization (PRO).** Gated on an explicit go-decision on top of the persisted Companion (exists since v0.41.0); Personalization additionally blocked by an open Primary-Review-Set-vs-Study/Exam-Focus philosophy question.
- **Review-Set-Centric Navigation** (Official catalog as the scalable replacement for hand-built Exam Hub pages). Direction only — Phase 2's Explore Convergence is a bounded step toward this, not the full thing.
- **Deeper plan nesting (3+ level hierarchy).** Feasible but a real project (cycle detection, recursive readiness rollup) — nice-to-have, no gate stated.
- **Note Detail readiness as its own tab.** Blocked on a mobile tab-overflow design pass.
- **Legacy "Future Directions" block** (pre-v0.20 items). Explicitly flagged stale — needs a fresh audit before anything in it is trusted.

---

## Core Feature Surfaces

### Navigation (App Shell)

Three coexisting navigation surfaces: **desktop sidebar** (full nav), **mobile hamburger drawer** (full nav on mobile), **mobile bottom tab bar** (persistent 4-tab subset — Dashboard, Library, Review Sets, Public Library — icon+text, below the `md` breakpoint, auto-hides during exam focus/active assessment). Progress stays drawer/sidebar-only, deliberately not a 5th tab. Do not add a 5th tab or expand the tab bar's scope without checking `RELEASES.md` v0.50.0's anti-drift notes first.

### Landing / Public

- Marketing positioning is notes-library-first: notes -> summaries -> quizzes -> review.
- Public nav exposes Home, Public Library, Learn, Pricing, Login, Get Started. Public Library is accessible without login.
- Public legal routes: `/privacy`, `/terms`. Contact email: `support@mail.notelib.app`.
- Branding uses the NL monogram for navbar/app shell/favicon and full logo for marketing headers/footers.

### Library and Notes

- Library is the authenticated note workspace. Notes can be private or public.
- Note creation/generation must respect profile setup, target audience defaults, and Study Pack usage rules.
- Async generation saves the note first, marks it `GENERATING`, redirects to Note Detail, and lets Note Detail poll. Failed generation preserves note content and exposes Retry Generate.
- Note Detail is the owner study hub: summary, key concepts, quiz, full notes, practice actions, recent sessions, readiness signal, Flashcards/Memorization entry points. Key Concepts entries sort by readiness (struggling → due → not-started → mastered) once ConceptHealth loads.
- Quiz result screens carry two authored/derived guidance surfaces: a `CompanionResultBridgeCard` excerpting the primary Review Set's Companion content, and deep-links from missed/weak concepts to their matching Key Concepts explanation. Both are same-session learning aids, not retention/return mechanics.

### Public Notes and Profiles

- Public note detail is read-only and separate from private Note Detail.
- Public note actions copy/create private owned notes first; private study actions never run against a public source note.
- Public Profile is `/public/creator/{username}` canonical, `/public/profile/{userId}` legacy-compatible.
- Profile Settings (`/profile`) is private editing; Public Profile owns visibility and sharing.

### Note Collections (Study Plans / Review Sets)

See the dedicated vision section above. Quick reference: a collection is a top-level **Goal** or a **Subject** (child of a Goal, or standalone) — exactly two levels. Published/admin collections are source plans; adoption creates owned snapshot copies. Recommended plans surface course/program-scoped on Dashboard and `/collections`; `/collections/published` is the full browse surface. The Builder is the single authoring canvas. Plan detail execution rows show action/status, not mastery — dedicated readiness detail lives at `/progress?collectionId={id}`. Top-level Goal detail renders `TodaysFocusCard` (Coach) → Progress (readiness + countdown) → collapsed `CompanionDisplayCard` ("View Full Guide").

### Progress and Readiness

- `/progress` is available to all plans, the canonical subject-level detail surface, including plan-scoped readiness via `?collectionId={id}`. Reads ConceptHealth only.
- Subjects group by Study Pack subject; blank/null subject is `Other`.
- Classification: mastered (recent correct signal), due (stale correct signal), not started (no correct signal), struggling (latest incorrect newer than correct).
- Goal milestones are fixed read-time checkpoints, not persisted. Note and plan readiness reuse this spine.

### Settings, Account, and Email

- Settings order: Preferences, Plan & Billing, Account. Preferences include Learning Style (`engagementMode`) and Study Reminders.
- Account deletion is soft-delete first, purge later. Data export is owner-only, excludes secrets/analytics/billing.

### Admin

- Admin Dashboard is internal, read-only v1, ADMIN-only — overview, billing, engagement, public-content growth, recent upgrades, failed payments, feedback.
- Feedback submissions persist `message`, authenticated `userId`, `email`, and current page URL.

---

## Non-Negotiable Rules

- Do not reuse student quiz session logic for teacher preview.
- Do not auto-regenerate generated content.
- Do not grant paid access from frontend logic or redirect callbacks.
- Do not hardcode backend checkout pricing.
- Do not add new chart libraries for readiness.
- Do not add batch/progress infrastructure except the documented v0.29.1 terminal bulk-generation receipt.
- Do not expose Study Plan mastery/readiness on execution rows, list cards, published-plan cards, or public source plans.
- Do not expose per-concept review timing to Free users on Note Detail.
- Do not move Learning Style or reminder preferences into Profile.
- Do not label nav as "Account Settings"; use "Settings".
- Do not let Quick Review, Flashcards, or Memorization write `ConceptHealth` — only genuine assessment modes/formats do.
- Do not plan-gate question *formats* — format variety is a learning-quality dimension, not a monetization lever. Gate modes/workflows/quotas instead.
- Do not nest Note Collections beyond two levels; no per-module mastery.
- Do not redesign the locked `/onboarding` flow.
- Do not let a learner receive an auto-generated Companion/Mentor Tip — curator-facing AI-assist only, mandatory human review before publish, never autonomous.
- Do not serve the Companion via a per-view/runtime LLM call — authored once, served static.
- Do not reorder the five authored Companion sections or infer a Mentor Tip's linked action at render time.
- Do not make Mentor Tip/Companion surfacing adaptive or LLM-driven — deterministic date/progress rules only; that tier is reserved for the gated PRO Personalization candidate.
- Do not let a surfacing condition permanently hide a Mentor Tip — "View Full Guide" always lists every authored tip.
- Do not backfill a reminder-email preference default change onto existing users — new-signup defaults apply only at account creation.
- Do not propose or re-surface roadmap candidates without checking `docs/product/ROADMAP.md`'s Backlog Index first.
- Do not treat quota/pricing numbers as living in one place — backend enforcement (`application.yaml`) and frontend marketing copy (`frontend/lib/pricing-config.ts`) are independent and must move together.
- Use `globalThis`, not `window`, in frontend code.
- Backend exceptions should be named `AppException` subclasses, not inline raw `new AppException(...)`.
- Repeated logic-bearing strings should be constants. Java range clamps should use `Math.clamp`.

---

## How We Work Together

- GPT: product thinking, roadmap decisions, UX philosophy, feature scoping, architecture tradeoffs, implementation prompt drafting.
- Claude Code: implementation prompt drafting, doc writing, code review, small direct changes.
- Codex: standard implementation, multi-system changes, refactors, tests.

For implementation tasks, GPT output should usually be a structured Codex prompt. Use the repo skills:

- `docs/skills/roadmap-feature-audit.md` before scoping roadmap/feature work.
- `docs/skills/codex-prompt-generator.md` before writing Codex prompts.
- `docs/skills/ux-product-review.md` for UX/product review.
- `docs/skills/release-doc-alignment.md` after shipping code/doc changes.

Always check `docs/codex-prompts/` for an existing prompt before writing a new one.

---

## Prompt Format To Preserve

Long mode for new features, backend changes, multi-system work, or behavior/documentation updates:

```markdown
Prompt mode: Long

Use the following docs as the source of truth:
- AGENTS.md
- docs/product/ROADMAP.md
- docs/features/[feature].md

## TASK
## GOAL
## CONTEXT
## REQUIRED CHANGES
## TESTING
## DOCUMENTATION
## CLEANUP
## ACCEPTANCE CRITERIA
## OUTPUT
```

Short mode for small UI polish, narrow bug fixes, and incremental follow-ups:

```markdown
Prompt mode: Short

Use the following docs as the source of truth:
- AGENTS.md
- docs/features/[feature].md

## TASK
## GOAL
## CHANGES
## ACCEPTANCE CRITERIA
## OUTPUT
```

Prompt rules:

- `CONTEXT` is the most important section.
- Paste the relevant anti-drift rules from `AGENTS.md`.
- `DOCUMENTATION` always includes `RELEASES.md` when behavior changes.
- Acceptance criteria must be checkable.
- Mention whether the task should avoid quota, billing, pricing, checkout, AI, schema, or new infrastructure changes.

---

## Model / Effort Recommendations

| Task type | Tool | Effort |
|---|---|---|
| UX / product review | Claude Sonnet | Standard |
| Architecture discussion | Claude Sonnet or Opus | High |
| Roadmap / doc review | Claude Sonnet | Standard |
| Prompt drafting | Claude Sonnet | Standard |
| Standard feature implementation | Codex | Medium |
| Multi-system or ambiguous implementation | Codex | High |
| Refactor / cleanup / migration | Codex | Medium |
| Tests for agreed behavior | Codex | Low / Medium |

---

## Key Source-of-Truth Docs

- `AGENTS.md` - implementation rules and anti-drift constraints
- `CLAUDE.md` - Claude Code routing, commands, and commit rules
- `RELEASES.md` - current and historical release state
- `docs/product/ROADMAP.md` - current release sequencing and future scope; its Backlog Index table is the authoritative list of every open candidate, its status, and its gate condition
- `docs/product/SPEC.md` - canonical product behavior
- `docs/product/EXAM_MODES.md` - locked quiz mode hierarchy, question formats, and non-engine review surfaces
- `docs/product/PLANS.md` - plan tiers and quotas
- `docs/features/companion.md` - Learning Companion / Coach / Mentor Tips behavior rules
- `docs/features/` - per-feature behavior rules
- `docs/codex-prompts/` - ready prompts for active work
- `docs/releases/` - per-version release notes
- `docs/skills/` - reusable AI workflow guidance

---

Context loaded. What are we working on?
