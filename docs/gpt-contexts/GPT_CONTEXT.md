# GPT_CONTEXT.md - NoteLib Product Context Handoff

> Paste the block below as your first message in a new GPT chat session.
> Update this file whenever a new version ships or the roadmap shifts significantly.
> Last updated: v0.56.0 (merged, pending signoff) - 2026-07-23

---

Here's the current context for our NoteLib product session. Treat this as a compact product snapshot, not a replacement for the repo docs. For implementation work, always defer to `AGENTS.md`, `RELEASES.md`, `docs/product/ROADMAP.md`, and the relevant `docs/features/*.md`.

---

## App: NoteLib

**What it is:** NoteLib is a notes-first study workspace. Users capture notes, generate Study Packs, practice with quizzes/exams, track readiness, and keep a reusable library of learning material.

**Core loop:** Capture -> Generate -> Review -> Improve -> Make a Copy -> Repeat

**Positioning:** Your notes become your study system.

**Rebrand note:** The product is NoteLib. Code, package names, and database/schema names still use `studysnap` in many places unless explicitly changed.

**Current baseline:** `v0.55.0 - Result-Screen Companion Bridge` is the most recently **released** version. `v0.56.0 - Weak-Concept Explanation Links` has all planned scope merged onto `releases/v0.56.0` and is **pending signoff** (not yet released, not yet on `main`) as of this update — treat it as functionally live for planning purposes but don't describe it as shipped externally until signoff closes. See "Current Release: v0.56.0" below. The product's strategic posture shifted mid-cycle (2026-07-20/21) from a single retention-bet gate to a broader "acquire and retain *new* users" posture — see "Retention Is the Proven Constraint" below, it's been substantially rewritten and should be read before proposing anything.

---

## Retention Is the Proven Constraint — and the Strategic Posture Shifted Mid-Cycle (read this before proposing anything)

**The number:** W1→W2 retention is **2.4%** (production read, 2026-07-15; 3 of 127 eligible activated users returned in week 2). This has been the core strategic constraint since v0.32.2 first flagged it (was 5.6% then) — it has **not meaningfully improved** despite three intervening feature releases aimed at it (v0.44.0, v0.46.0, v0.48.0). Free-tier quota is essentially never hit, so pricing is independently ruled out as the bottleneck.

**The diagnosis (two independent Fable sessions, growth/retention lens + consumer-psychology lens, converged):** every content-rich retention trigger the product has (due-concepts digest, weak-concept nudge, weekly summary) shipped **default-OFF**, gated behind the exact engagement it's meant to create. Separately, the first study session ends in a psychologically "complete" feeling (Zeigarnik effect) rather than an open loop that pulls the learner back.

**`v0.48.0` (merged 2026-07-15) tested the cheap fixes:** an open-loop first-quiz ending ("N of M concepts secured — best reviewed tomorrow" instead of a terminal score screen) and a due-concepts digest default-ON trigger fix. **Both remain UNPROVEN — mechanism shipped, lift not measured.** Do not describe either as a retention win in external-facing copy.

**The strategic pivot (2026-07-20/21) — read this before assuming "everything gates on the v0.48.0 cohort read":** the v0.48.0 read came back too weak to act on cleanly, and cold email outreach to already-churned users was judged unlikely to work (precedent: failed-payment recovery emails got zero response). Rather than wait on that one gate, the product owner explicitly repositioned the whole track — *"we're not chasing our previous users anymore, we're now chasing new users to retain"* — and the four releases since (`v0.52.0`, `v0.52.1`, `v0.53.0`, `v0.54.0`) all ship from that posture instead of from the retention-bet queue:
- **Real-time friction capture from current users**, replacing cold outreach to churned ones: proactive in-app feedback prompts at first-quiz-ever and return-after-inactivity moments (`v0.52.0`), plus three more early-lifecycle placements aimed specifically at new users in their first sessions — Public Library browse-without-adopting, first non-onboarding Study Pack generation, second-ever completed quiz (`v0.52.1`).
- **New-user acquisition**, not re-engagement: organic-search discoverability fixes (`v0.53.0`) and a fourth Exam Hub, CPALE (`v0.54.0`), both explicitly scoped under the "new users to retain" posture per a dedicated sequencing session (`next-priority-new-user-focus-out/01-next-priority-new-user-focus.md`, 2026-07-21).
- **`v0.55.0`/`v0.56.0` are a third, distinct lane** — study-effectiveness/in-session learning-quality work (Companion excerpts and weak-concept explanation links on quiz result screens), explicitly scoped to answer "does the product help you learn right now, in this session," independent of both the acquisition work and the still-gated retention bet.

**Retention H1+H5 (commitment device + pre-decided return action) is still the pre-committed next move if data ever clears its gate — but that gate remains unresolved, not abandoned.** The rule from before the pivot still stands: any positive-or-ambiguous signal on a v0.48.0 cohort re-read triggers H1+H5 as one combined release (cohort size is too small to attribute single-variable tests separately). The re-read is **not yet reachable, let alone run**: the 14-day W1→W2 window for a cohort that actually experienced the v0.48.0 changes doesn't close until 2026-07-29 — still 6 days out as of this update (2026-07-23) — though the query is already written and ready (`next-priority-new-user-focus-out/02-h1-h5-cohort-recheck-and-cpale-depth.sql` Query 1).

**The retained/churned exam-dated user interview script is written and ready to run, zero engineering cost, and still hasn't been run as of this update.** It's the one open item on this whole track that can't happen from a keyboard — repeatedly displaced by codeable work across five releases now.

**2026-07-22 interim-window production pulls** (run manually by the product owner, not by Claude — no prod DB access): device mix reconfirms ~75% mobile / 25% desktop (no new signal, already used to un-park `v0.50.0`'s tab bar); PDF export usage is unchanged at exactly 1 export ever (reinforces "parked, do not build" on PDF export surfacing); exactly 4 course/programs have a published top-level Official Review Set — Accountancy (74 notes), Architecture (52), Education (43), Nursing (63) — a 1:1 match with the 4 shipped Exam Hubs (CPALE/ALE/LET/PNLE), each with real depth, which is mild evidence *against* the strongest form of "no content for my exam" as a churn cause for those four specifically, though zero official coverage exists for any board exam outside them; the exam-date natural experiment reconfirmed at a larger, later sample — 0/41 (0%) exam-dated vs. 3/106 (2.83%) non-exam-dated, same direction as the original 0/35 vs. 3/94 read. **Still unresolved:** whether exam-dated users retaining worse is a *value* problem (they tried it and it wasn't worth a second visit) or a *discovery* problem (nothing surfaces the value that exists) — the interviews above are what's meant to resolve this, and they still haven't run.

**Full backlog, current status, and exactly what un-parks each item lives in `docs/product/ROADMAP.md`'s Backlog Index table — check it before proposing or resurfacing anything**, and see "Fable Roadmap Candidates: Gated & Ungated" below for a synthesized, status-grouped view of that same table. The index exists specifically because a large, fully-designed exploration ("Smart Review Planning," a curriculum-driven Review Set curation system) sat forgotten across several release cycles before this table existed. Do not propose roadmap items from partial memory of past sessions; the index is the current source of truth for what's held, what's conditional, and why.

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
- **Quiz sessions** share `quick_review_sessions` with mode stored as enum and session state in JSONB. Question **format** (MCQ, True/False, Multi-Select, Matching, and — new in v0.39.0 — Identification, Enumeration) is a separate axis from mode.
- **Flashcards and Memorization** (new in v0.39.0) are non-scored review surfaces that sit entirely outside the quiz-session engine — no session row, no `ConceptHealth` write, ever.
- **Generated teacher quizzes** use `generatedQuiz`, not student quiz sessions.

Versioning rule:

- Never auto-regenerate generated content.
- Regeneration is explicit, user-confirmed, owner-only, and updates the existing Study Pack in place.
- Owner self-copy copies authored note fields only.
- Public-note copy is the exception: when the public source has a Study Pack, the copy includes that Study Pack and arrives `STUDY_PACK_READY`.

---

## Note Collections (Study Plans / Review Sets): Vision & Evolution

Profile-aware terminology — "Study Plan" (Student / Board Taker), "Lesson Plan" (Teacher), "Review Set" (Professional) — all the same underlying `NoteCollection` entity, labeled through `getCollectionLabels(profileType)`.

**The vision, in one line:** a Note Collection is not a folder — it is a trackable **readiness journey**, and it is the product's primary retention lever.

**Why this became the retention lever (v0.33.0).** Diagnosis at the time: users activate but don't return — W1→W2 retention was ~5.6%, recent cohorts near 0%, longest observed streak ~2 days. Collections were the natural unit to attack this: give a learner a number (readiness %) that only moves by coming back to practice, and give them a credible reason to start even with zero notes of their own (curated, adoptable plans).

**Structure — a locked two-level hierarchy (v0.33.1+).** A top-level **Goal** collection can contain child **Subject** collections through `parent_collection_id` (self-referential, exactly 2 levels — no arbitrary depth, no per-module mastery, cycles impossible). This is a deliberate, scoped reversal of the original "no parent/child collections" rule, constrained specifically to this one shape.

**Adoption model (v0.31.0, extended through v0.33.3) — the cold-start on-ramp.** Admin-published collections act as public, course/program-targeted curated plans. Adopting is free, idempotent, makes no AI call, and creates a private **snapshot copy** — source edits never sync into adopted copies (`sourcePlanId` is lineage/idempotency only). Recursive Goal adopt (v0.33.3) copies every child Subject plan and its notes in one action. This is meant to be the answer for a learner with nothing of their own yet: adopt a curated plan and start a trackable journey immediately, without writing a single note first.

**Readiness is the headline signal, deliberately not shown everywhere (locked, v0.33.x–v0.37.0).** Readiness derives entirely from existing `ConceptHealth` / `ProgressReportService` — no new mastery signal, no new stored field, ever. But *where* it's allowed to show is tightly scoped: the dedicated plan detail / `/progress` surfaces show it; execution rows, list cards, published-plan cards, and public source plans deliberately do not (list-level mastery display was tried and rolled back — it created role confusion between "browsing" and "monitoring"). Vocabulary is unified and locked: `ready / mastered / due / not started`.

**Mastery integrity is protected, not just displayed (v0.37.0).** Once readiness became the headline number, unlimited free Quick Review grinding could have "satisfied" it for free. Quick Review — and, since v0.39.0, Flashcards/Memorization — are locked to **never write `ConceptHealth`**. Only genuine assessment (Challenge Quiz, Adaptive Practice, Long Exam, Board Exam, Interview Practice, and now Identification/Enumeration) can move the readiness number.

**The Builder is the authoring surface for this hierarchy (v0.33.1–v0.35.0).** `/collections/{id}/builder` is a single canvas for both Goal and leaf plans — add/nest/reorder Subject plans, drag notes between them, mobile-first (collapsible sections, icon-only controls on narrow viewports). The Builder is deliberately **not** a study/monitoring surface — no readiness ring on the Builder itself (v0.33.4 removed one that had crept in); per-module stats in each Subject header are the only inline signal a curator needs.

**Most recent polish (v0.39.1) — closing real gaps found in practice, not a planned feature push:**
- `AddSubjectModal` gained a description field (was title-only).
- Fixed a real data-integrity bug: publishing a Goal cascaded `visibility=PUBLIC` to children but never touched `courseProgram`, so newly-published child Subject plans were invisible to course/program-scoped public discovery. `updateMetadata` now cascades a newly-set `courseProgram` to children whose own value is currently blank — never a blind overwrite, same conservative rule as the v0.37.2 clobber-bug fix.
- **Cold-start adoption discoverability audit.** Found the adoption mechanism worked fine end-to-end once found, but nothing in the actual cold-start path (the zero-notes empty state, the Dashboard's recommended-plan card) told a new learner it existed. Fixed both, without touching the locked `/onboarding` flow — the fix lives entirely on Dashboard surfaces every cold-start learner reaches regardless of how they got there.

**What's intentionally still parked:** standalone adoption of a single child Subject plan (there's an unresolved re-parenting interaction with `adoptGoal`'s idempotency check) — not worth solving until there's a real discovery need for it.

**Primary Review Set + Weekly Countdown (v0.40.0, extended v0.40.1) — readiness became an ongoing cadence, not just a static number.** A nullable user-level `primaryCollectionId` (only an owned top-level Goal can be primary; auto-sets when exactly one is owned; self-heals to null on delete) plus a nullable `note_collections.target_completion_date` (top-level Goals only, never copied on adopt/self-copy) and a nullable user-level `studyDaysPerWeek` (1-7) together drive a derived — never stored — weekly countdown: `weeksRemaining` / `conceptsRemaining` / `todaysConceptBudget` on `GET /collections/{id}/goal`, with due concepts as a floor. v0.40.1 added Phase 2: Hamilton largest-remainder allocation across child Subjects plus a deterministic `weeklyFocusByDay` template. Surfaces: Dashboard/`/collections` primary CTA, the Goal detail countdown (now folded into the Progress card's `countdown` slot per v0.43.0, see Learning Companion vision below), and `/progress`'s default view. **No adaptive/AI scheduling, streaks, or calendar integration** — pure read-time derivation from existing readiness + date math.

**Public Review Set Reachability (v0.40.1).** A PUBLIC Official Review Set outside a learner's own course/program was technically public but unreachable through any UI path. Fixed narrowly: `/collections/published` gained a "Browse All Official Review Sets" section below the course/program-scoped Recommended row, calling the already-existing unfiltered `listPublicStudyPlans({})` — no new backend endpoint. The broader Explore/nav redesign (see Review-Set-Centric Navigation, deferred) was explicitly rejected as overkill for this narrow gap.

**Review Set Detail as a study dashboard (v0.41.1).** The detail page was re-composed (frontend-only, no new capability) into Identity → Current Journey → Primary Action → Readiness → Guidance (Companion) → Subject Plans/Notes, replacing a metadata-forward "collection details" layout with one that answers "what should I do next, in this Review Set?" — while staying 100% collection-scoped (cross-journey "which set" stays `/dashboard`'s job). Authoring controls (Publish/Build/Edit/Manage Companion/Set-primary/Delete) collapsed into compact hero chrome so they don't compete with the learner's scroll path.

---

## Learning Companion: Vision & Evolution (v0.41.0 → v0.43.1, plus a v0.55.0 fast-follow)

**The organizing insight:** Review Centers aren't valuable because they provide PDFs or quizzes — they're valuable because they provide **guidance** (structure, direction, pacing, coaching, confidence). NoteLib had the knowledge layer (Notes), the learning engine (Study Packs), and the journey (Review Sets), but no guidance layer riding on top. The Companion is that layer — a persisted, curator-authored, profile-aware, statically-served guidance layer on a top-level Review Set.

**Success criterion (the north star every phase is judged against):** *"Every Official Review Set should feel like a premium guided learning experience rather than a collection of notes."* Not feature count, not revenue.

**Content model (v0.41.0, extended v0.42.0/v0.43.1).** A single nullable JSONB column, `note_collections.companion` — not a new table, mirrors the existing `sessionState` JSONB precedent. 1:1 with a top-level collection only (rejected on child Subject Plans with the same `400` pattern as `targetCompletionDate`/primary). Five long-form sections — Overview, Study Strategy, Common Mistakes, FAQ, Resources (Resources added v0.42.0) — plus, since v0.43.1, an atomic `mentorTips` array living in the same JSONB payload (no new table/migration/endpoint). **No runtime LLM call to serve a Companion** — authored once, served static, zero per-view cost.

**Curation, never generation (locked rule, clarified — not reversed — in v0.42.0).** Learner-facing behavior is unchanged: a learner never receives an auto-generated plan or tip. What changed in v0.42.0 is curator-facing: ADMIN-only `Generate Companion` (per-section or all) calls the existing OpenAI service (PREMIUM tier, no new LLM infra) to produce a **draft only** — the curator must still review, edit, and click Save/Publish. Publishing is never autonomous, in every path, including Mentor Tips (v0.43.1).

**Official-author-only, FREE for all learners.** Only the NoteLib official author can author a Companion (architecture stays open to any top-level-Goal owner later). Zero paid uplift on the Companion itself by design — see Monetization philosophy below.

**Staleness signal (v0.42.0).** A nullable `note_collections.companion_structure_snapshot` (child count + sorted child/note ids) captured on Companion save and compared inline on read — surfaces an ADMIN-only "Companion may be outdated" flag when the set's structure changes. Known v1 limitation: does not compare note body edits or concept counts (concept-count pipeline is per-user progress work, not a cheap structural signal). Mentor Tip text/config changes deliberately do **not** mark structure stale — staleness stays child/note-membership-only.

**Coach vs. Companion, formalized (v0.43.0 mid-release philosophy refinement).** Relabeling section headings to coach-voice copy ("Overview" → "🗺️ What this covers") didn't fix the real complaint — five long-form paragraphs under friendlier labels still reads as an article, not an app. The real split:
- **Coach (dynamic).** Reacts to the learner: continue-where-you-left-off, target-date pacing, readiness, due concepts, resolved next action, terminal actions. Not a new concept — it's naming what already existed (`TodaysFocusCard` + `ReadinessSummary`). Zero new cost.
- **Companion (timeless).** Authored, does not react to daily progress. Teaches how to approach the curriculum — mindset, expectations, common mistakes, practical advice. Should read like mentor advice, not reference material.
- **Curriculum.** Subject Plans → Notes → Practice. Unchanged, not part of this discussion.

Shipped as `TodaysFocusCard` (merges former countdown/primary-action/coach-intro surfaces into one top-of-page Coach card: primary action, `Continue Studying`, pacing sentence, Quick Actions) sitting above Progress (owns the countdown summary) and the authored Companion (reference material). `CompanionDisplayCard` now **collapses by default on every viewport** behind a "View Full Guide" disclosure — sections only render once expanded.

**Mentor Tips (v0.43.1) — the atomic, individually-surfaceable evolution.** Five long-form fields can't be "surfaced as a moment" without truncating curator intent, so this needed a real content-model change (not frontend-only, unlike v0.40.1/v0.41.1/v0.42.1's fast-follow pattern):
- Each tip has its own identity, an optional **curator-tagged** (not inferred) linked action (`None`, `Continue Studying`, `Review Due Concepts`, terminal exam/builder action) — inferring it at render time would require a per-view LLM call, which v0.41.0 explicitly ruled out.
- Optional **deterministic** surfacing condition (date/progress rules, e.g. "within 2 weeks of exam date," "after N subjects completed") — FREE-safe by the weekly-countdown precedent, not adaptive/LLM-driven selection (that tier is reserved for gated PRO Personalization).
- The collection detail page selects at most one eligible tip in authored order near `TodaysFocusCard`; **all** authored tips still list in `CompanionDisplayCard`'s "View Full Guide" as a sixth section, regardless of current eligibility — a learner must never permanently miss a tip because its trigger never fired for them.
- **Known low-volume caveat, checked explicitly at kickoff (2026-07-10), not assumed:** dev DB has only 1 PUBLIC/Official top-level Review Set with an authored Companion (2 companions total across 7 top-level collections). Decision: proceed anyway — dev volume isn't necessarily prod volume, and the content-model/authoring-UI work has standalone value even before curators build up tip inventory.
- **Scope broadened mid-release:** a pre-signoff audit also fixed a real trust bug (Pro-only paywalls — Board Exam, Long Exam, Difficulty Selection, Interview Practice — let a user select/pay for Plus without unlocking the feature), added a dedicated review-timing upsell, and added Help Center coverage for Companion/Coach/Mentor Tips/Primary Review Set.

**Monetization philosophy (long-term principle, established v0.43.1, not a repricing of today's plans):**
- **FREE — static guidance.** The Companion itself (near-zero marginal cost, high perceived value — activation/retention driver and conversion hook).
- **PLUS — interaction.** Ask Companion (future, gated) — grounded Q&A over authored content, reusing the Interview Practice cost-control template (feature gate + quota + rate limit + CRITIQUE model). Deliberately PLUS not PRO: grounded retrieval, not generation.
- **PRO — personalization.** Genuinely adaptive/learning-pattern/LLM-informed guidance selection (future, gated) — explicitly **not** deterministic rule-based reordering, which follows the same FREE precedent as the weekly countdown (a common point of confusion recorded so it isn't re-derived wrong later).
- Both future runtime tiers are gated on the persisted Companion existing; neither is scoped to a version yet.

**Result-Screen Companion Bridge (v0.55.0) — the Companion's guidance reaches beyond the Review Set page for the first time.** Quick Review, Challenge Quiz (both render branches, including Board Exam Mode), and Adaptive Practice result screens now show a labeled excerpt card ("From your Review Set's guide: ...") of the primary Review Set's Common Mistakes (falling back to Study Strategy) right where `WeeklyPacingEchoCard` already renders. Curator-published content only, no generation, no mid-exam coaching — reuses the same `primaryCollectionId` → `getCollectionGoal()` fetch the Echo already made, so this required no new note→collection lookup and no new endpoint. This was explicitly held back through two prior release cycles as an App Shape candidate on exactly that missing-lookup problem — resolving it by reusing an existing pattern is what un-parked it.

**Future, gated, not yet scoped:** AI-generated Review Sets (curator pipeline: public notes → suggest Subject Plans → generate Companion → human review → publish — gated on v0.42.0's authoring-assist pipeline proving out); Ask Companion (PLUS); Personalized/Adaptive guidance (PRO). See "Fable Roadmap Candidates: Gated & Ungated" below for the full current status of these three.

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
2. **Challenge Quiz** - all plans with quota, progressive generation up to 20 questions.
3. **Adaptive Practice** - Plus/Pro practice targeting weak concepts.
4. **Long Exam** - Pro exam mode, fixed long-form practice, supports multi-note sources.
5. **Board Exam** - Pro high-stakes exam simulation for Exam Reviewer profile.

Professional **Interview Practice** is a sub-mode of Adaptive Practice, not a sixth top-level mode.

Rules:

- Do not add a sixth top-level mode without updating `docs/product/EXAM_MODES.md` and roadmap/spec docs together.
- Premium exam paywalls fire from Start CTAs after setup/prescreen, not from card click.
- Study Plan premium-exam launches carry `collectionId` and scope additional-note pickers to quiz-ready notes in that plan.

### Question formats (v0.39.0, a separate axis from modes)

Within the five modes above, individual questions carry a `questionFormat`: `MCQ`, `TRUE_FALSE`, `MULTI_SELECT`, `MATCHING`, and two new free-text formats added in v0.39.0:

- **Identification** — fill-in-the-blank / name-the-term. Scored deterministically against a generation-time `acceptableAnswers[]` list (normalized, case-insensitive) — no per-submission LLM call.
- **Enumeration** — name every item in a well-defined 2–5 item set (e.g. "Name the three branches of government"). Scored **all-or-nothing** via exhaustive bipartite matching against `acceptableAnswerGroups[]` (one synonym group per required item) — deliberately not first-match greedy, which can wrongly reject a valid answer when synonym groups overlap. No partial credit.

Both formats are Challenge Quiz-only for now (Long Exam is a planned fast-follow), and both are **ungated across every plan tier** — a deliberate product stance adopted this release: question-format variety is a learning-quality dimension, not a control/workflow dimension, so it is never a Plus/Pro differentiator. Monetization stays in mode-level and quota-level gates that already exist (Board Exam Pro-only, Adaptive Practice quota tiers) — not in which formats a user can see. Both formats reuse the existing quiz-session engine and `ConceptHealth` write path exactly like MCQ — no new session discriminator, no new mode.

### Non-engine review surfaces (v0.39.0, distinct from both modes and formats)

**Flashcards** and **Memorization** are free, non-scored review surfaces that sit entirely outside the quiz-session engine — no timer, no submit, no `quizSession` discriminator, and critically, **never write `ConceptHealth`** (same rule as Quick Review: review-only surfaces never move mastery, due-state, or Overall Readiness).

- **Flashcards** — flips each `keyConcepts` entry against its matching `quiz[].explanation` (fuzzy concept match) as a self-review deck. No new AI call — reuses existing Study Pack data.
- **Memorization** — Flashcards' matching logic plus a real spaced-repetition schedule (simplified SM-2: Again/Hard/Good/Easy), stored in a new, separate `memorization_cards` entity — firewalled from `ConceptHealth` and `ProgressReportService` by design, not just convention.

Both entry points sit on the Note Detail Key Concepts tab, hidden from Teacher profile, free for every plan.

---

## Plans, Pricing, and Payments

Runtime entitlement source of truth is the backend subscription model and `GET /api/me/plan`.

- Plans: Free, Plus, Pro.
- Checkout: Xendit hosted checkout via backend `POST /api/payments/create`.
- Paid access is granted only by validated webhook-confirmed payments.
- Pricing is backend-owned; frontend pricing surfaces use billing/pricing APIs and shared plan config.
- Paid plans are one-time, time-boxed passes in UI copy, not auto-renewing subscriptions.
- Cancellation is scheduled for period end; paid access remains active until then.
- Do not add plan flags to `users`.
- Do not change prices, quota numbers, pass durations, billing, or checkout mechanics as part of readiness/UX work unless explicitly scoped.
- New question *formats* are explicitly kept out of plan-gating (see Quiz / Practice Mode Contract) — this is a considered exception to "gate new capability," not an oversight.

Upgrade CTA rule:

- Use `getUpgradeCtas(currentPlan)` from `frontend/src/config/plans.ts`.
- Free -> primary `Upgrade to Plus`, secondary `Go Pro`.
- Plus -> primary `Upgrade to Pro`.
- Pro -> no upgrade CTA.

---

## Current Release: v0.56.0 - Weak-Concept Explanation Links

**Status: all planned scope merged onto `releases/v0.56.0`, pending signoff — not yet released, not yet on `main`.** Theme: close the loop from "you missed this" to "here's what it actually means, right now" on quiz result screens. Sourced from Fable's 2026-07-22 study-effectiveness session (`docs/claude-prompt/study-effectiveness-out/01-study-effectiveness-ui-pricing.md` §1 item 1), its top pick across that consultation — same study-effectiveness lane as `v0.55.0`, not the acquisition or retention-bet lanes. Anti-drift: no new schema, no new endpoint, no new generation — pure navigation over data that already exists; not a retention mechanic (same-session learning aid, not a return-trigger).

- **Weak/missed-concept explanation links.** Quick Review, Adaptive Practice, Challenge Quiz, and Board Exam Mode result lists now link weak concepts that match a source-note Key Concepts entry straight to that entry (`?tab=key-concepts#concept-*`, with scroll-and-highlight on Note Detail); unmatched concepts stay plain text.
- **Mid-release addition: Quiz-tab Full Notes nudge + Key Concepts readiness sort.** Two more candidates from the same Fable session, folded in because they touch the same Note Detail area: a one-time `GuidanceTip` nudging a learner who hasn't visited Full Notes yet toward it from the Quiz tab (deliberately a nudge, not a tab reorder — the existing tab order is a locked prior decision); and the Key Concepts tab now sorts entries by readiness (struggling → due → not-started → mastered) once ConceptHealth loads.

See `RELEASES.md` v0.56.0 for full scope and anti-drift detail.

---

## Previous Releases: v0.51.0 → v0.55.0 (condensed — see `RELEASES.md` for full detail)

**v0.55.0 - Result-Screen Companion Bridge.** Brings curator-authored Companion guidance (Common Mistakes, falling back to Study Strategy) to the Quick Review / Challenge Quiz (both branches, including Board Exam Mode) / Adaptive Practice result screens, reusing the exact `primaryCollectionId` → `getCollectionGoal()` pattern the v0.46.0 Weekly Pacing Echo already shipped in the same slot — no new note→collection lookup, no new endpoint. Curator-published content only, no generation, no mid-exam coaching. Resolves the "premise problem" that held this back as an App Shape candidate for two release cycles.

**v0.54.1 - Public Note Copy Correctness Fixes.** Two independent correctness fixes: `NoteService.copyNote()`'s existing-copy branch now backfills a copied Study Pack onto a previously pack-less copy once the source's Study Pack becomes ready (was returning it pack-less forever); the public note "Quiz yourself" CTA is now hidden for the note's own owner, matching the already-documented owner/non-owner action split. A user report of the AI-suggested-metadata modal flashing/bypassing on a self-copy-then-manual-generate flow was investigated but not reproduced or confirmed — logged as a known limitation, not fixed.

**v0.54.0 - CPALE Exam Hub (Wave 2).** A fourth board-exam hub (`/exam/cpale`, Accountancy), reusing the Wave 1 hub template exactly — no route-level code change since `/exam/[slug]` and the sitemap already iterate the hub config. Gated on a production depth-count check clearing the ~25-30 note Wave 2 bar, confirmed 2026-07-21. Other Wave 2 candidates (Civil/Electrical/Mechanical Engineering, Pharmacy, Physical Therapy, Civil Service Exam) each still need their own depth confirmation — not extended to any of them.

**v0.53.0 - SEO Discoverability: Exam Hub Depth & Organic Attribution.** Pure new-user acquisition work from an organic-search strategy session: a "Browse by Subject" breakdown + uncapped "More {Exam} Notes" section on each Exam Hub (previously capped at 18 visible cards); real `ItemList` structured data asserting the hub's actual member notes; a privacy-preserving, aggregate-only `referrerSource` bucket (`google`/`other-search`/`social`/`direct`, no raw URLs) feeding a new Admin organic-landing-attribution panel.

**v0.52.1 - Early-Lifecycle Feedback Signals.** Three targeted proactive feedback prompts for *new* users specifically (not churned-user outreach): Public Library browsed-without-adopting, first non-onboarding Study Pack generation (both frontend-only, reuse the existing `SendFeedbackWidget`/`QuizFeedbackPanel`), and second-ever-completed-quiz difficulty/pacing (needed one new backend signal). A Fable session explicitly rejected a fuller app-wide build as the same "more listening infrastructure on a tiny population" anti-pattern the retention diagnosis already flagged.

**v0.52.0 - Proactive In-App Feedback Prompts.** The pivot-defining release: instead of cold outreach to already-churned users (judged unlikely to work), surfaces the existing feedback pipeline proactively to *current* users at first-quiz-ever and return-after-inactivity moments. Mid-release scope addition: a feedback modal visual redesign, an Admin read-only detail view, and a strictly-optional screenshot attachment (new `feedback_image` table, deliberately not a column on `FeedbackEntity`, to protect the v0.51.0 read-path fix).

**v0.51.0 / v0.51.1 - Read-Path Performance Pass II.** Fixed production slowness on Private Library, Public Library, Collection detail, and Dashboard via lean projections, a two-tier poller, waterfall flattening, a batched Dashboard fan-out endpoint, and real backend pagination for both Private and Public Library (F1–F8, all shipped; F9/F10 client-caching and denormalized-counts deliberately parked pending production evidence). v0.51.1 closed the one deferred follow-up — wiring Dashboard Stage-1's fetch to the bounded `limit` param F2 shipped unused.

---

## Previous Releases: v0.48.0 → v0.50.4 (condensed — see `RELEASES.md` for full detail, and "Retention Is the Proven Constraint" above for v0.48.0's strategic framing)

**v0.50.4 - Exam Hub Discovery Polish.** Collapsed a duplicate "Browse {Hub} hub →" link; a "free reviewer" SEO vocabulary pass across Exam Hub and all Public Library subject pages; a defensive subject-page indexation gate (`SUBJECT_PAGE_INDEX_THRESHOLD = 6`) so the vocabulary pass didn't make thin 1-note pages indexable.

**v0.50.3 - Public Note Copy Flow & Related-Notes Consistency.** Made copy-as-is the only behavior for the public "Quiz yourself" CTA (fixed a real navigation-vs-modal race condition); consistent "See all →" related-notes link wording/grid across sections.

**v0.50.2 - Note Card Content Consistency.** One shared single-excerpt card cascade (note preview, else Study Pack summary fallback, else nothing) across all six note-card surfaces, replacing several years of shipping-drift duplication.

**v0.50.1 - Mobile UI Polish.** Tab-bar fast-follows (filter-retaining Library/Public Library links, a persisted show/hide preference; an icon-only compact variant was implemented then reverted after a consumer-psychology review found it cut the accessible name for no real chrome benefit); Review Set description "Read more" expansion; Progress page milestone/Concept Mastery legibility fixes.

**v0.50.0 - Mobile Bottom Tab Bar.** A persistent 4-tab mobile bottom nav (Dashboard, Library, Review Sets, Public Library), additive to the unchanged desktop sidebar and mobile drawer, gated on a production device-mix pull (~75% mobile). The first real UI-shape change of this cycle, not a retention experiment. See the Navigation note under Core Feature Surfaces.

**v0.49.0 - Progress Page: Private Library Links.** Small non-retention-flavored fix filling the interim window: Progress's per-subject links and "weakest subject" CTA now point at the learner's private Library instead of Public Library.

**v0.48.0 - Retention Experiment: Open Loop & Digest Trigger.** The two unproven retention experiments — full strategic framing is in "Retention Is the Proven Constraint" above, not repeated here.

---

## Previous Releases: v0.44.0 → v0.47.1 (condensed — see `RELEASES.md` for full detail)

**v0.47.1 - V82 Migration Collision Hotfix.** A single-file Flyway migration renumber fixing a duplicate-version collision that had blocked every production deploy since `v0.46.0` merged (a rebase artifact — two migrations independently claimed version 82). No schema or behavior change.

**v0.47.0 - Conversion Audit Tier 4: Cleanup Batch.** 16 low-impact, cheap-cleanup items from the conversion/retention UX audit backlog's Tier 4 (landing/pricing polish, public note detail, discovery/library polish, onboarding copy, doc hygiene, Learn signup-intent), shipped across 6 PRs. No new backend entities, migrations, or endpoints.

**v0.46.0 - Retention Depth: Due-Concepts Digest & Exam Pacing.** The release that introduced the two capabilities v0.48.0 later tuned: a weekly due-concepts email digest (shipped default-OFF — the choice v0.48.0 revisited) and an exam-date pacing plan scheduling the learner's owned content only (explicitly not auto-generated curriculum). Scope broadened mid-release with a session-completion pacing echo and an app-feel polish batch.

**v0.45.0 / v0.45.1 / v0.45.2 - Conversion Audit Tier 3 + collection fixes.** Tier 3 of the same conversion/retention UX audit (18 items — landing, pricing, note detail, public plan card, onboarding/Dashboard guidance, Public Library), plus two fast-follow releases fixing pre-existing collection/discovery defects (Goal-collection note-count rollup, published-plan backlinks, a plan-preview panel gap).

**v0.44.0 - Conversion & Retention Polish.** The release that established retention (not top-of-funnel or conversion) as the proven constraint, shipping Tier 1 and Tier 2 of a 7-session conversion/retention UX audit plus a verified backend fix for Quick Review's missing ConceptHealth tracking. This is the audit whose Tier 3/4 later became v0.45.0 and v0.47.0.

---

## Previous Releases: v0.40.0 → v0.43.1 (condensed — see `RELEASES.md` for full detail)

**v0.43.1 - Companion Mentor Tips.** Small, individually-surfaceable, action-linked Mentor Tips living inside the existing `note_collections.companion` JSONB (no new table), replacing the "read start to finish" article framing with atomic coach-surfaced moments. Shipped alongside a real trust-bug fix: Pro-only paywalls no longer let a user pay for a dead-end Plus checkout.

**v0.43.0 - Companion Coach Experience.** Frontend-only: coach-voice heading map over `CompanionDisplayCard` (order-preserving, no reordering — "Curation, never generation" unaffected); `TodaysFocusCard` merges former countdown/primary-action/coach-intro surfaces into one top-of-page Coach card; `CompanionDisplayCard` collapses by default behind "View Full Guide" on every viewport. Mid-release philosophy refinement formalized the Coach (dynamic)/Companion (timeless) split — see Learning Companion vision above.

**v0.42.1 - Companion & Progress Polish.** Frontend-only UX fixes: merged the Readiness card and "View full progress"/"Review due concepts" row into one card via a `footer` slot; fixed `/progress?collectionId={id}`'s backlink to return to the originating collection instead of always "Dashboard".

**v0.42.0 - AI-assisted Companion authoring + regeneration.** ADMIN `Generate Companion` (per-section or all) → LLM draft → mandatory human review/edit → Publish, reusing the existing OpenAI service (no new LLM infra); granular per-section regeneration; a structure-snapshot staleness signal ("Companion may be outdated"); added the Resources section. Clarified (not reversed) "Curation, never generation": learner-facing behavior unchanged, curator-facing AI-assist is new and scoped to Official Companions only.

**v0.41.1 - Review Set Detail Page: This-Set Study Dashboard.** Frontend-only re-composition into Identity → Current Journey → Primary Action → Readiness → Guidance (Companion) → Subject Plans/Notes; single resolved primary CTA (free-tier-first); authoring controls consolidated into compact hero chrome. Also mirrored the Primary badge treatment onto `/collections` list cards and documented the badge-classification rule (identity/state get badges, metadata never does) in `docs/features/collections.md`.

**v0.41.0 - Learning Companion (MVP).** Shipped the persisted Companion content model, Official-author-only manual authoring UI, and learner-facing display — see the dedicated Learning Companion vision section above for full detail.

**v0.40.1 - Public Review Set Reachability.** `/collections/published` gained a "Browse All Official Review Sets" section using the already-existing unfiltered `listPublic` query (no new backend endpoint); weekly-scheduling Phase 2 (Hamilton largest-remainder allocation + `weeklyFocusByDay`) shipped backend-side; manual "Set/Remove as primary" UI added (closing v0.40.0's known gap); "Adopted" ownership badge added to collection cards/detail.

**v0.40.0 - Weekly Study Plan (Exam Countdown) + Primary Review Set.** Turned readiness into an ongoing weekly cadence: user-level `primaryCollectionId` (Primary Review Set), Goal-only `targetCompletionDate`, user-level `studyDaysPerWeek`, and a derived (never stored) weekly countdown on `GET /collections/{id}/goal`. See Note Collections vision section above for full detail.

---

## Recent Release Context (condensed — see `RELEASES.md` for full detail)

**v0.39.2 - Public Library Learning Experience.** Surfaced Flashcards Preview (capped at 3 cards, client-side) and a static Memorization teaser on public note detail so anonymous visitors experience enough of the study system to want to continue. No backend change.

**v0.39.1 - Study Plan Builder Polish.** Fixed subject-metadata gaps and cold-start adoption discoverability in the Study Plan Builder, surfaced from real usage: description field on Add Subject Plan, a `courseProgram` cascade fix on `updateMetadata` (published child Subject plans could sit invisible to course/program-scoped discovery), and Dashboard-level adoption discoverability fixes (locked `/onboarding` flow untouched). Parked: standalone adoption of a single child Subject plan (unresolved re-parenting interaction with `adoptGoal`'s idempotency check).

**v0.39.0 - Flexible Review Methods.** Let a Study Pack be reviewed through more than Multiple Choice, while preserving the v0.37.0 review-vs-assessment mastery boundary. Chain A — review methods that never write `ConceptHealth`: Flashcards (flip-card deck from existing `keyConcepts`/`quiz[].explanation`, no new AI call) and Memorization (Flashcards' matching plus real spaced repetition, separate `memorization_cards` entity, firewalled from readiness by design). Chain B — assessment formats that write `ConceptHealth` like Challenge Quiz: Identification (free-text fill-in-the-blank, deterministic `acceptableAnswers[]` matching) and Enumeration (free-text "name every item in a 2-5 item set", all-or-nothing bipartite-matching scoring). Both new formats are ungated across every plan tier (format variety is a learning-quality dimension, not a monetization lever) and Challenge Quiz-only for now; neither adds a 6th/7th mode.

**v0.38.0 - Read-Path Optimization Pass.** Latency/DB-payload pass on the hottest read endpoints (session history, collection detail, private library list, Goal per-child readiness) via lean projections instead of full entities. Byte-identical API responses; no schema/endpoint/DTO change.

**Collections/retention arc (v0.31.0 → v0.37.0):** see the dedicated Note Collections vision section above for the full narrative — adoption model, Goal/Subject hierarchy, the Builder, readiness-as-retention-lever, the mastery-integrity lock.

**v0.37.1–v0.37.4 - Production memory incident response.** A string of Render-instance OOM/restart investigations (glibc malloc arena fragmentation, G1 not releasing idle heap, uncapped Metaspace) resolved via Dockerfile JVM flags — no application code change. v0.37.2 also fixed a real data-loss bug (`updateMetadata` clobbering fields on partial PATCH) — the same class of bug the v0.39.1 courseProgram cascade fix was careful not to repeat.

**v0.36.x - OCR incident + Progress/Readiness merge.** Google Vision OCR was temporarily disabled backend-wide after causing production OOM (per-call gRPC client churn); a kill-switch plus honest user messaging shipped as a fast-follow. Separately, `/me/progress` and the plan readiness sub-route merged into one canonical `/progress` surface with unified vocabulary (`ready / mastered / due / not started`).

**v0.32.x and earlier - Monetization, account controls, conversion diagnosis.** Premium exam paywalls moved to Start-CTA moments; pricing reframed as one-time passes; account deletion made reversible-first; the core retention-vs-conversion diagnosis (checkout is not the blocker, retention is) that motivated the whole v0.33.0+ collections push described above.

---

## Fable Roadmap Candidates: Gated & Ungated

Synthesized from `docs/product/ROADMAP.md`'s Backlog Index — the authoritative table (currently ~45 rows). This section groups the same items by status so a UX conversation can reason about them without opening the repo; **treat the Backlog Index itself as the source of truth if the two ever disagree**, and don't resurface anything below without checking it first — it's been actively curated for months specifically so items don't get proposed from partial memory.

### Active now — no gate, just not yet done
- **User interviews (retained + churned exam-dated).** Script written, zero engineering cost. The one item on the entire retention track that can't happen from a keyboard — still not run, see the Retention section above.
- **P1 (Google Search Console setup) and P3 (exam-named Learn guides).** Both non-engineering — P1 needs domain access, P3 needs a human curator. Handed back to the product owner as action items, not code work.

### Gated — condition is close or partially cleared
- **Retention H1+H5 (commitment device + pre-decided return action).** The pre-committed next move if the v0.48.0 cohort re-read is positive-or-ambiguous. Gate: the re-read window doesn't close until 2026-07-29 (6 days out as of this update); query is ready but can't run yet. See the Retention section above — do not scope this without that read.
- **Wave 2 Exam Hubs beyond CPALE** (Civil/Electrical/Mechanical Engineering, Pharmacy, Physical Therapy, Civil Service Exam). Each needs its own production note-count depth check (~25-30 notes) before it's buildable — CPALE cleared this bar in v0.54.0, the rest haven't been checked yet.
- **P7 (exam quick-facts block per hub)** — wait for GSC (P1) to confirm these queries even reach NoteLib before building a recurring editorial-maintenance surface.
- **P8 (off-page community presence / backlinks)** — non-engineering, sequenced after the on-page SEO work (done); needs an owner to actually do outreach.
- **L2 (earned-depth pathway for non-exam subjects)** — double-gated: needs both a subject showing real depth (~15-20+ notes) in a future inventory AND post-GSC data showing organic impressions actually landing there.
- **F9/F10 (client-side caching, denormalized engagement counts)** — parked from the v0.51.0 performance pass, pending production evidence (post-bound-query slow-log signal, refetch pain) that hasn't been checked yet.
- **Study Effectiveness remaining candidates** (Note Detail tab-order/reading-flow, Study Pack scope surfacing, Adaptive Practice per-question rationale tag, Review Set Detail + result-screen layout pass, collapsed-Companion teaser, twice-missed-concept re-explanation, Plus-tier review-timing-gate instrumentation). One item from this same Fable session shipped as `v0.56.0`; the rest are unscoped candidates needing individual scoping passes. **Difficulty Selection Pro→Plus** is its own item in this list — it's a pricing-structure decision (Plus currently has no qualitative differentiator since Free quota is non-binding), not a config flip, and needs explicit product sign-off before it's even scopeable.
- **Parent Readiness Digest (New Capability Idea 4).** Conditional on the H1 read being positive AND an explicit product decision on shape (email-only vs. dashboard).
- **Offline Study Pack access (Idea 9).** "Held, one leg down" — heavy mobile usage is confirmed (~75%), but still needs one more leg: PDF export volume is ruled out (essentially unused, 1 export ever), so it now needs either the offline-fallback hit rate (not yet instrumented) or a direct interview signal.
- **Bulk Quiz Generation & Teacher-Flow Polish** (+ shared-results probe / class-groups ideas folded in). Auto-schedules once ≥5 active teacher accounts exist — deliberately not re-litigated every release cycle before then.

### Held indefinitely — behind the retention constraint clearing
- **App Shape Core** (Companion Live Milestones, Concept-to-Note Back-Annotation, "Struggle Map" evidence panel) and **App Shape Polish stragglers** (sticky-toolbar re-attempt, Review Set filter facet, feedback digest) — none touch the proven retention constraint, held until it clears.
- **Photo Capture of handwritten notes (Idea 6).** The next recommended Core-Feature-sized bet (new image upload + vision-extraction infra) once the retention loop is actually proven (W1→W2 lift confirmed), not before.
- **Smart Review Planning (Internal Curator, 7 fully-architected docs in `docs/claude-prompt/fable-out/`).** The single largest piece of paused planning material in the repo. Triple-gated: interviews confirm content-gap churn AND a manual coverage sprint proves lift AND hand-curation saturates. Do not propose this from memory — read the Backlog Index row first.
- **Manual Official-coverage sprint (hand-curate ALE/PNLE/LET/CPALE gaps).** Conditional on interviews surfacing "no content for my exam" as an actual churn reason.
- **Listen Mode / Bilingual UI / Study Buddy (Ideas 7, 10, 11).** Low priority, gated on an interview signal for language/social/loop demand. Study Buddy specifically confirmed lowest — a pairing mechanic multiplies churn risk at 2.4% W1→W2 rather than countering it.
- **PDF export surfacing.** Parked, do not build — near-zero usage (1 export, ever) and the value-vs-discovery question is currently undeterminable.
- **Conversion-audit deferred pair** (adoption-count social proof, "Trending this week" on Public Library plan cards) — held on windowed backend engagement counts that don't exist yet.

### Parked — needs an explicit product go-decision, not a data gate
- **AI-generated Review Sets.** Curator pipeline: public notes → suggest Subject Plans → generate Companion → human review → publish. Gated on the v0.42.0 authoring-assist pipeline proving out (it does) and an explicit go-decision that hasn't been made.
- **Runtime Companion — Ask Companion (PLUS)** (grounded Q&A over authored Companion content, reusing the Interview Practice cost-control template) **and Personalization (PRO)** (genuinely adaptive/learning-pattern-driven guidance selection — explicitly not deterministic rule reordering, which is the same FREE precedent as the weekly countdown). Both gated on an explicit go-decision on top of the persisted Companion existing (it does, since v0.41.0); Personalization is additionally blocked by an open, unrelated Profile/Progress philosophy question (Primary Review Set vs. the older Study/Exam Focus mechanism, never reconciled).
- **Review-Set-Centric Navigation** (Official Review Set catalog as the scalable replacement for hand-built per-profession Exam Hub pages; Dashboard/Progress reorganized around the Primary Review Set; eventual nav shape Dashboard / My Reviews / Library / Explore / Progress). Direction only, no release scoped, no gate stated beyond "the Primary Review Set concept proving useful in real usage" — which it has been used for since v0.40.0, so this is really just waiting on a deliberate scoping pass.
- **Deeper plan nesting (3+ level Goal→Subject hierarchy).** Feasible but a real project (cycle detection, recursive readiness rollup, adopt-recursion) — nice-to-have, no gate stated.
- **Note Detail readiness as its own tab.** Blocked on a mobile tab-overflow design pass — the current 4-tab bar already fills a standard iPhone viewport exactly.
- **Legacy "Future Directions" block** (exam-mode work, billing, teacher items pre-v0.20 in `ROADMAP.md`). Explicitly flagged stale, largely pre-dates current architecture — needs a fresh audit before anything in it is trusted, not proposed as-is.

---

## Core Feature Surfaces

### Navigation (App Shell) — updated v0.50.0

The authenticated app shell has three coexisting navigation surfaces, not one:

- **Desktop sidebar** — full nav, unchanged by v0.50.0.
- **Mobile hamburger drawer** — full nav on mobile, unchanged by v0.50.0.
- **Mobile bottom tab bar (new, v0.50.0)** — a persistent, always-visible subset (Dashboard, Library, Review Sets, Public Library), icon+text, shown only below the `md` breakpoint. Additive, not a replacement for the drawer — the drawer still exists and still holds everything else (Progress stays drawer/sidebar-only, deliberately not a 5th tab). Auto-hides whenever exam focus or an active assessment/review screen claims the bottom of the viewport, so it never stacks with the home-screen-install nudge or another fixed-bottom UI element.

Do not add a 5th tab or otherwise expand the tab bar's scope without checking `RELEASES.md` v0.50.0's anti-drift notes first — the 4-tab set and the drawer/sidebar split were deliberate.

### Landing / Public

- Marketing positioning is notes-library-first: notes -> summaries -> quizzes -> review.
- Public nav exposes Home, Public Library, Learn, Pricing, Login, Get Started.
- Public Library is accessible without login.
- Public legal routes: `/privacy`, `/terms`.
- Contact email for launch/legal pages: `support@mail.notelib.app`.
- Branding uses the NL monogram for navbar/app shell/favicon and full logo for marketing headers/footers.

### Library and Notes

- Library is the authenticated note workspace.
- Notes can be private or public.
- Note creation/generation must respect profile setup, target audience defaults, and Study Pack usage rules.
- Async generation saves the note first, marks it `GENERATING`, redirects to Note Detail, and lets Note Detail poll.
- Failed generation preserves note content and exposes Retry Generate.
- Note Detail is the owner study hub: summary, key concepts, quiz, full notes, practice actions, recent sessions, readiness signal, plus (v0.39.0) Flashcards/Memorization entry points on the Key Concepts tab. Since v0.56.0, Key Concepts entries sort by readiness (struggling → due → not-started → mastered) once ConceptHealth loads, and the Quiz tab shows a one-time nudge toward Full Notes if the learner hasn't visited it yet this page visit — tab order itself (Summary, Key Concepts, Quiz, Full Notes) stays a locked prior decision, not reopened.
- Quiz result screens (Quick Review, Challenge Quiz including Board Exam Mode, Adaptive Practice, and — for weak-concept links only — Board Exam Mode) now carry two authored/derived guidance surfaces: a `CompanionResultBridgeCard` excerpting the learner's primary Review Set's Common Mistakes/Study Strategy Companion content (v0.55.0), and (v0.56.0) deep-links from missed/weak concepts straight to their matching Key Concepts explanation on the source note. Both are same-session learning aids, not retention/return mechanics.

### Public Notes and Profiles

- Public note detail is read-only and separate from private Note Detail.
- Public note actions copy/create private owned notes first; private study actions never run against a public source note.
- Public Profile is `/public/creator/{username}` canonical, `/public/profile/{userId}` legacy-compatible.
- Profile Settings (`/profile`) is private editing; Public Profile owns visibility and sharing.
- Share behavior uses a shared modal pattern for notes and profiles.

### Note Collections (Study Plans / Review Sets)

See the dedicated vision section above for the full narrative. Quick reference:

- A collection is either a top-level **Goal** or a **Subject** (child of a Goal, or standalone) — exactly two levels, no deeper.
- Published/admin collections are source plans; adoption creates owned snapshot copies (`sourcePlanId` for lineage only, never synced).
- Recommended plans surface course/program-scoped on Dashboard and `/collections`; `/collections/published` is the full browse surface.
- The Builder (`/collections/{id}/builder`) is the single authoring canvas for both Goal and leaf plans.
- Plan detail execution rows show action/status, not mastery. Dedicated readiness detail lives at `/progress?collectionId={id}`.
- Plan premium exams launch with `collectionId` and use only quiz-ready notes from that plan.
- Top-level Goal detail renders, in order: `TodaysFocusCard` (Coach — primary action, pacing, Quick Actions, and at most one eligible Mentor Tip), Progress (readiness + weekly countdown), then the collapsed `CompanionDisplayCard` ("View Full Guide" — Overview/Study Strategy/Common Mistakes/FAQ/Resources/Mentor Tips). See the dedicated Learning Companion vision section above.

### Progress and Readiness

- `/progress` is available to all plans and is the canonical subject-level detail surface, including plan-scoped readiness via `?collectionId={id}`.
- It reads ConceptHealth only, not quiz-session report artifacts.
- Subjects group by Study Pack subject; blank/null subject is `Other`.
- Classification:
  - mastered: recent `lastCorrectAt` within threshold
  - due: stale `lastCorrectAt`
  - not started: no correct signal
  - struggling: latest incorrect signal is newer than correct signal
- Goal milestones are fixed read-time checkpoints, not persisted.
- Note and plan readiness reuse this spine and must not invent separate thresholds.

### Settings, Account, and Email

- Settings order: Preferences, Plan & Billing, Account.
- Preferences include Learning Style (`engagementMode`) and Study Reminders.
- Email Preferences manages optional email categories.
- Account deletion is soft-delete first, purge later.
- Data export is owner-only and excludes secrets, analytics, and billing/financial records.

### Admin

- Admin Dashboard is internal, read-only v1, ADMIN-only.
- It covers overview, billing, engagement, public-content growth, recent upgrades, failed payments, and feedback.
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
- Do not let Quick Review, Flashcards, or Memorization write `ConceptHealth` — only genuine assessment modes/formats do (Challenge Quiz, Adaptive Practice, Long Exam, Board Exam, Interview Practice, Identification, Enumeration).
- Do not plan-gate question *formats* (Identification/Enumeration and any future format) — format variety is a learning-quality dimension, not a monetization lever. Gate modes/workflows/quotas instead.
- Do not nest Note Collections beyond two levels (Goal -> Subject); no per-module mastery.
- Do not redesign the locked `/onboarding` flow (Profile Type -> Study Goal -> Input Method -> Study Pack Generation -> Completion).
- Do not let a learner receive an auto-generated Companion/Mentor Tip — "Curation, never generation" is curator-facing AI-assist only (draft, then mandatory human review before publish); publishing is never autonomous in any path.
- Do not serve the Companion via a per-view/runtime LLM call — authored once, served static, zero per-view cost.
- Do not reorder the five authored Companion sections (Overview/Study Strategy/Common Mistakes/FAQ/Resources) or infer a Mentor Tip's linked action at render time — action-linking is curator-tagged at authoring time only.
- Do not make Mentor Tip/Companion surfacing adaptive or learning-pattern/LLM-driven — deterministic date/progress rules only; that tier is reserved for the gated, not-yet-scoped PRO Personalization candidate.
- Do not let a surfacing condition permanently hide a Mentor Tip — "View Full Guide" must always list every authored tip regardless of current eligibility.
- Do not backfill a reminder-email preference default change onto existing users — new-signup defaults (e.g. the due-concepts digest, default-ON since v0.48.0) apply only at account creation; existing users keep whatever they already had.
- Do not propose or re-surface roadmap candidates without checking `docs/product/ROADMAP.md`'s Backlog Index first — it is the single source of truth for what's held, what's conditional, and why; treat any idea not found there as possibly already decided against.
- Use `globalThis`, not `window`, in frontend code.
- Backend exceptions should be named `AppException` subclasses, not inline raw `new AppException(...)`.
- Repeated logic-bearing strings should be constants.
- Java range clamps should use `Math.clamp`.

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
