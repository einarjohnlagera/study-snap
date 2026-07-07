# GPT_CONTEXT.md - NoteLib Product Context Handoff

> Paste the block below as your first message in a new GPT chat session.
> Update this file whenever a new version ships or the roadmap shifts significantly.
> Last updated: v0.40.0 (in progress) - 2026-07-07

---

Here's the current context for our NoteLib product session. Treat this as a compact product snapshot, not a replacement for the repo docs. For implementation work, always defer to `AGENTS.md`, `RELEASES.md`, `docs/product/ROADMAP.md`, and the relevant `docs/features/*.md`.

---

## App: NoteLib

**What it is:** NoteLib is a notes-first study workspace. Users capture notes, generate Study Packs, practice with quizzes/exams, track readiness, and keep a reusable library of learning material.

**Core loop:** Capture -> Generate -> Review -> Improve -> Make a Copy -> Repeat

**Positioning:** Your notes become your study system.

**Rebrand note:** The product is NoteLib. Code, package names, and database/schema names still use `studysnap` in many places unless explicitly changed.

**Current baseline:** `v0.40.0 - Weekly Study Plan (Exam Countdown) + Primary Review Set` is in progress. Previous released baseline is `v0.39.2 - Public Library Learning Experience`.

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

## Current Release: v0.40.0 - Weekly Study Plan (Exam Countdown) + Primary Review Set

**Status: In Progress.** Turns readiness from a static number into an ongoing weekly cadence — the direct next chapter of the retention thesis validated since v0.33.0 (a number that only moves by returning), aimed at the exam-taker conversion/monetization segment. Full scope and phasing in `docs/product/ROADMAP.md`.

**Core idea:** the top-level **Goal** collection (see Note Collections vision above — the container in the locked Goal→Subject hierarchy) is being elevated from "just a folder of Subjects" to a first-class trackable object with its own countdown state. Nothing about the 2-level hierarchy itself changed — new fields were added *onto* the existing Goal object, not a new entity.

Shipped so far:

- **Primary Review Set backend foundation.** Nullable `users.primary_collection_id`, only owned top-level Goals can be primary. Auto-sets when exactly one top-level Goal is owned (create/adopt/adopt-goal paths reassert this); clears safely on delete. **No manual "Set as primary" UI exists yet** — this only matters once a learner owns a *second* top-level Goal (auto-set deliberately doesn't touch an existing primary), and today there's no way to switch it. Known, scoped gap — not yet decided whether it gets its own small follow-up item.
- **Target completion date + study intensity (backend).** Nullable `note_collections.target_completion_date` (top-level Goals only, decoupled from `UserEntity.examDate`), nullable `users.study_days_per_week` (1-7, user-level not per-Goal). Neither is ever copied on adopt/self-copy — a freshly adopted Goal always starts dateless.
- **Weekly countdown derivation (backend).** `GET /collections/{id}/goal` now derives `weeksRemaining`/`conceptsRemaining`/`todaysConceptBudget` from target date + study intensity + the existing readiness rollup — no stored per-week schedule, no LLM, due concepts act as a floor. Phase 1 uses simple division; the weighted largest-remainder subject allocation is Phase 2 (may slip out of this release).
- **Frontend surfaces:** Dashboard primary CTA (branches on `primaryCollectionId`), target-date + study-intensity inputs on `EditCollectionModal` (Goal-only), Goal detail "This Week" countdown card (hidden entirely with no target date), Review Sets list page primary CTA.
- **In progress:** post-adopt guidance nudge (reusing `pickActiveGuidance()`/`GuidanceTip` to suggest setting a target date right after adopting a Goal — scoped to target-date only, dropping the original "optionally set as primary" half since that's redundant on first adopt and has no UI to point at yet) and the `/progress` default-view change (defaulting to the Primary Review Set's scoped view via the existing `PlanPicker` mechanism).

Anti-drift: no new top-level entity; no change to `UserEntity.examDate` or the board-exam countdown; no adaptive/AI scheduling, streaks, or calendar integration; no nav rename, no Exam Hub change, no Explore page, no Progress full-redesign this release.

**Explicitly separate, planned as its own release:** `v0.40.1 - Public Review Set Reachability` (not yet kicked off) fixes a different, narrower gap — a learner with no course/program set can't browse *all* PUBLIC top-level Review Sets today even though the backend already supports an unfiltered query. This is a discoverability/browse fix, unrelated to Primary Review Set's manual-designation gap above.

---

## Previous Release: v0.39.2 - Public Library Learning Experience

**Status: Released.** Connected the Public Library discovery layer to the signed-in workspace's richer review methods — surfaced Flashcards and Memorization on public note detail so anonymous visitors experience enough of the study system to want to continue. Shipped:

- **Flashcards Preview (frontend).** `PublicFlashcardsPreview`, capped at 3 cards, tap-to-reveal, fully client-side, no backend change — reuses `keyConcepts` + `quiz[].explanation` already in the public note detail response.
- **Memorization teaser (frontend).** Static educational section (copy + illustration) explaining spaced repetition — no per-note content, no scheduling logic, no state for anonymous users.
- Existing CTA hierarchy (`Quiz yourself on this note` primary, `Create your own Study Pack` / `Copy to My Library` secondary) preserved unchanged; both new sections are additive.

---

## Recent Release Context (condensed — see `RELEASES.md` for full detail)

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
- `docs/product/ROADMAP.md` - current release sequencing and future scope
- `docs/product/SPEC.md` - canonical product behavior
- `docs/product/EXAM_MODES.md` - locked quiz mode hierarchy, question formats, and non-engine review surfaces
- `docs/product/PLANS.md` - plan tiers and quotas
- `docs/features/` - per-feature behavior rules
- `docs/codex-prompts/` - ready prompts for active work
- `docs/releases/` - per-version release notes
- `docs/skills/` - reusable AI workflow guidance

---

Context loaded. What are we working on?
