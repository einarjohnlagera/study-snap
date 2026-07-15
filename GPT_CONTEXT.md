# GPT_CONTEXT.md - NoteLib Product Context Handoff

> Paste the block below as your first message in a new GPT chat session.
> Update this file whenever a new version ships or the roadmap shifts significantly.
> Last updated: v0.49.0 (released) - 2026-07-15

---

Here's the current context for our NoteLib product session. Treat this as a compact product snapshot, not a replacement for the repo docs. For implementation work, always defer to `AGENTS.md`, `RELEASES.md`, `docs/product/ROADMAP.md`, and the relevant `docs/features/*.md`.

---

## App: NoteLib

**What it is:** NoteLib is a notes-first study workspace. Users capture notes, generate Study Packs, practice with quizzes/exams, track readiness, and keep a reusable library of learning material.

**Core loop:** Capture -> Generate -> Review -> Improve -> Make a Copy -> Repeat

**Positioning:** Your notes become your study system.

**Rebrand note:** The product is NoteLib. Code, package names, and database/schema names still use `studysnap` in many places unless explicitly changed.

**Current baseline:** `v0.49.0 - Progress Page: Private Library Links` is the most recently released version. No version is currently in progress. The product is mid-way through a retention-experiment cycle — see the dedicated section below before proposing new roadmap work; it explains why recent releases look small and isolated rather than feature-driven.

---

## Retention Is the Proven Constraint (read this before proposing anything)

**The number:** W1→W2 retention is currently **2.4%** (production read, 2026-07-15; 3 of 127 eligible activated users returned in week 2). This has been the core strategic constraint since v0.32.2 first flagged it (was 5.6% then) — it has **not meaningfully improved** despite two intervening feature releases aimed at it (v0.44.0, v0.46.0). Free-tier quota is essentially never hit, so pricing has been independently ruled out as the current bottleneck.

**The diagnosis (two independent Fable sessions, growth/retention lens + consumer-psychology lens, converged on the same read):** every content-rich retention trigger the product has (due-concepts digest, weak-concept nudge, weekly summary) shipped **default-OFF**, gated behind the exact engagement it's meant to create. Separately, the first study session ends in a psychologically "complete" feeling (a finished task, per the Zeigarnik effect) rather than an open loop that pulls the learner back — nothing in the first-session experience signals that spaced review exists or that forgetting is coming.

**What just shipped to test this (`v0.48.0`, merged 2026-07-15, cohort data now accruing):**
1. **Open-loop first-quiz ending.** A learner's first-ever completed quiz, if concepts were missed, now ends on "N of M concepts secured — the rest are best reviewed tomorrow" instead of a terminal score screen.
2. **Due-concepts digest trigger fix.** New signups now default to the digest email being ON (previously opt-in-only, rarely turned on), with a strengthened CTA (styled button, not a bare link) after production data showed domain-wide email click-through under 1%.

**Both are UNPROVEN.** Mechanism shipped, lift not yet measured — a meaningful cohort read needs roughly two more weeks from the merge date. **Do not describe these as retention wins in any external-facing copy** until a real read confirms it.

**An open, unresolved question worth knowing about before writing anything exam-date-related:** exam-dated users (in theory the most motivated segment — they have a real deadline) actually retained *worse* under the status quo (0/35) than users with no exam date set (3/94, small sample). The comfortable explanation is "nothing currently acts on that field, so it has no behavioral effect either way." The uncomfortable explanation, not yet ruled out, is that even highly-motivated users didn't find the product worth a second visit — a **value** problem, not just a missing-trigger problem. Real user interviews (both retained and churned users) are queued to resolve this but have not been conducted yet as of this update.

**What's next, and the pre-committed decision rule:** any positive-or-ambiguous signal on the v0.48.0 read triggers a combined follow-up release (working name `v0.50.0`) shipping a commitment device (ask the user to commit to a concrete return plan at peak motivation, right after their first quiz) together with a pre-decided return action (so "what do I even do when I come back" is answered in advance) — as one release, not sequential experiments, because the cohort size here is too small to cleanly attribute single-variable tests one at a time.

**Full backlog, current status, and exactly what un-parks each item lives in `docs/product/ROADMAP.md`'s Backlog Index table — check it before proposing or resurfacing anything.** It exists specifically because a large, fully-designed exploration ("Smart Review Planning," a curriculum-driven Review Set curation system) sat forgotten across several release cycles before this table existed. Do not propose roadmap items from partial memory of past sessions; the index is the current source of truth for what's held, what's conditional, and why.

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

## Learning Companion: Vision & Evolution (v0.41.0 → v0.43.1)

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

**Future, gated, not yet scoped:** AI-generated Review Sets (curator pipeline: public notes → suggest Subject Plans → generate Companion → human review → publish — gated on v0.42.0's authoring-assist pipeline proving out); Ask Companion (PLUS); Personalized/Adaptive guidance (PRO).

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

## Current Release: v0.49.0 - Progress Page: Private Library Links (plus v0.48.0's in-flight experiments)

**Status: Released.** No version is currently in progress. See the "Retention Is the Proven Constraint" section above for the strategic context — recent releases are small and isolated on purpose.

**`v0.49.0` (2026-07-15).** A small, deliberately non-retention-flavored fix filling the interim window while the v0.48.0 experiment cohort accrues data: Progress page's per-subject links and its "weakest subject" CTA now point at the learner's own private Library instead of Public Library, with copy corrected to match ("Study X in your Library" instead of "Browse X notes in the community").

**`v0.48.0` (2026-07-15) — the strategically significant one.** Two isolated retention experiments — see the dedicated section above for full detail. Both unproven, cohort data accruing, a read expected roughly two weeks out.

**Next real decision point:** whatever the v0.48.0 read shows. See `docs/product/ROADMAP.md`'s Backlog Index for the full current state of everything queued behind it.

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

## Core Feature Surfaces

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
- Note Detail is the owner study hub: summary, key concepts, quiz, full notes, practice actions, recent sessions, readiness signal, plus (v0.39.0) Flashcards/Memorization entry points on the Key Concepts tab.

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
