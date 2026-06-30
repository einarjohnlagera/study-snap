# GPT_CONTEXT.md - NoteLib Product Context Handoff

> Paste the block below as your first message in a new GPT chat session.
> Update this file whenever a new version ships or the roadmap shifts significantly.
> Last updated: v0.33.0 in progress - 2026-06-26

---

Here's the current context for our NoteLib product session. Treat this as a compact product snapshot, not a replacement for the repo docs. For implementation work, always defer to `AGENTS.md`, `RELEASES.md`, `docs/product/ROADMAP.md`, and the relevant `docs/features/*.md`.

---

## App: NoteLib

**What it is:** NoteLib is a notes-first study workspace. Users capture notes, generate Study Packs, practice with quizzes/exams, track readiness, and keep a reusable library of learning material.

**Core loop:** Capture -> Generate -> Review -> Improve -> Make a Copy -> Repeat

**Positioning:** Your notes become your study system.

**Rebrand note:** The product is NoteLib. Code, package names, and database/schema names still use `studysnap` in many places unless explicitly changed.

**Current baseline:** `v0.33.0 - Study Plans as a Retention Engine` is in progress. Latest released baseline is `v0.32.2 - Conversion Diagnosis & Quota Honesty`.

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
- **Study Plans / Collections** organize owned notes. Published admin plans can be adopted into a user's private library as snapshot copies.
- **ConceptHealth** is the recency spine for readiness and Progress: `lastCorrectAt`, `lastIncorrectAt`, due/not-due classification, and struggling state.
- **Quiz sessions** share `quick_review_sessions` with mode stored as enum and session state in JSONB.
- **Generated teacher quizzes** use `generatedQuiz`, not student quiz sessions.

Versioning rule:

- Never auto-regenerate generated content.
- Regeneration is explicit, user-confirmed, owner-only, and updates the existing Study Pack in place.
- Owner self-copy copies authored note fields only.
- Public-note copy is the exception: when the public source has a Study Pack, the copy includes that Study Pack and arrives `STUDY_PACK_READY`.

---

## Profile Types

| Profile | Current focus | Important rules |
|---|---|---|
| Student | Notes, Study Packs, Quick Review, Challenge Quiz, Adaptive Practice, Long Exam | Target Audience hidden; backend saves `STUDENT`. |
| Board Exam / Exam Reviewer | Exam-date context, Board Exam Mode, readiness for licensure-style prep | Target Audience hidden; backend saves `BOARD_TAKER`. |
| Teacher | Quiz generation, preview, DOCX export, Exam Builder | Teacher flow uses `generatedQuiz` only; never reuse student quiz sessions for preview. |
| Professional | Certification review, Long Exam as Full Practice Exam, Interview Practice | Target Audience hidden; backend saves `PROFESSIONAL`. |
| Parent | Enum exists, no real product implementation | Do not propose implementation without parent-child relationship design. |

Onboarding is active for verified users. It collects profile type, study goal, input method, Study Pack generation, completion, learner level, and course/program. Backend content-creating mutations must enforce profile setup for the legacy completed-but-null profile cohort through `ProfileSetupRequiredException`.

---

## Quiz / Practice Mode Contract

The product has a locked hierarchy of five top-level modes:

1. **Quick Review** - all plans, saved questions.
2. **Challenge Quiz** - all plans with quota, progressive generation up to 20 questions.
3. **Adaptive Practice** - Plus/Pro practice targeting weak concepts.
4. **Long Exam** - Pro exam mode, fixed long-form practice, supports multi-note sources.
5. **Board Exam** - Pro high-stakes exam simulation for Exam Reviewer profile.

Professional **Interview Practice** is a sub-mode of Adaptive Practice, not a sixth top-level mode.

Rules:

- Do not add a sixth top-level mode without updating `docs/product/EXAM_MODES.md` and roadmap/spec docs together.
- Premium exam paywalls fire from Start CTAs after setup/prescreen, not from card click.
- Study Plan premium-exam launches carry `collectionId` and scope additional-note pickers to quiz-ready notes in that plan.

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

Upgrade CTA rule:

- Use `getUpgradeCtas(currentPlan)` from `frontend/src/config/plans.ts`.
- Free -> primary `Upgrade to Plus`, secondary `Go Pro`.
- Plus -> primary `Upgrade to Pro`.
- Pro -> no upgrade CTA.

---

## Current Release: v0.33.0 - Study Plans as a Retention Engine

Retention diagnosis from v0.32.2: users activate but do not return. W1->W2 retention was about 5.6%, recent cohorts near 0%. The current release focuses on one scoped retention lever: Study Plans as trackable readiness journeys.

Track A shipped:

- Study Plan metadata-save is decoupled from publishing. Course/program and description persist independently; Publish remains gated by existing validation.
- Library selection-create collects collection description.
- Recommended published plans surface on `/collections`, scoped to the learner's own course/program, using the Dashboard recommended-plan pattern.

Track B shipped:

- Owner-scoped plan readiness endpoint: `GET /collections/{id}/readiness`.
- Dedicated plan readiness route: `/collections/[id]/readiness`, reached by a "Check readiness" CTA.
- Shared `ReadinessSummary` component: inline SVG ring + CSS progress bars, no chart library.
- `PLAN_READINESS_VIEWED` analytics event fires once after successful readiness load.
- Private Note Detail shows a compact per-note readiness rollup for ready notes with key concepts.
- Free users see the readiness signal and per-concept readiness status; Plus/Pro keep per-concept review timing.

Locked v0.33 readiness rules:

- Readiness is derived from ConceptHealth; no new persisted readiness field, no AI call, no generated content.
- Plan readiness must reuse `ProgressReportService` classification and `masteryPercentage`.
- Vocabulary is unified: `ready`, `mastered`, `due`, `not started`.
- Plan readiness belongs only on the dedicated owner-scoped sub-route. Collection execution rows, list cards, published-plan cards, and public source plans keep the no-mastery rule.
- Note readiness signal is Free-visible; per-concept timing (`daysSinceReview`, timestamps, `Due - Nd ago`) remains Plus/Pro.
- Dashboard and plan-list readiness badges are deferred to v0.34.0.
- Teacher bulk-quiz and teacher-flow polish remain deferred until there is a teacher cohort.

---

## Recent Release Context

### v0.32.2 - Conversion Diagnosis & Quota Honesty

- Re-scoped the growth problem: checkout is not the main blocker; retention is.
- Admin funnel got date-windowed metrics and better quota-hit reporting.
- Long Exam and Board Exam deduct quota per session instead of per source note.
- Inactivity reminders became reachable by default and budget-aware.
- Resend suppression handling was added.
- Backend memory/OOM work reduced session JSONB deserialization and adjusted runtime sizing.

### v0.32.1 - Monetization Surfacing & Pricing Clarity

- Premium exam paywalls moved to Start CTAs after setup surfaces.
- Study Plan premium exam CTAs were added.
- Pricing surfaces were reframed around one-time, time-boxed passes.
- Settings plan cards got unified pass-length selector.
- Plan-launched exam prescreens link back to the originating plan and scope additional notes to that plan.

### v0.32.0 - Account & Communication Controls

- Account deletion is reversible first (`PENDING_DELETION`, 30-day grace), then irreversible purge/anonymization.
- Owner-only data export returns a synchronous JSON attachment.
- Email Preferences center manages inactivity, weak-concept, weekly summary, and marketing preferences.
- Tokenized one-click unsubscribe exists for optional emails.
- Transactional emails are not gated by optional preferences.

### v0.31.2 - Analytics Integrity & Funnel Visibility

- Analytics writes publish after transaction commit and persist asynchronously.
- Signup analytics no longer fail on FK timing.
- Analytics event enum and frontend union were audited.
- Admin Funnel includes retention cohorts and upgrade -> checkout -> paid conversion.

### v0.31.1 - Adoptable Study Plans Discovery & Status

- `/collections/published` lists all matching published plans for the learner's course/program.
- Onboarding completion can surface a recommended plan.
- Study Plan list cards show Not started / In progress / Completed execution status.
- Study Plan detail rows show learner-facing execution status and Exam Builder exclusion notices.
- Bulk-generation and bulk-import quota UI became clearer.

### v0.31.0 - Adoptable Study Plans

- Admin-published collections can act as public, course/program-targeted Study Plans.
- Learners adopt plans by snapshot-copying public notes and linked Study Packs into owned notes/collections.
- Adoption is free, idempotent, and does not call AI.
- Published plan metadata and publish UX were tightened.

### v0.30.x - Readiness Signals and Copy Polish

- Progress became the canonical subject-level ConceptHealth dashboard.
- Long Exam, Board Exam, and Interview Practice now write ConceptHealth signals.
- `lastIncorrectAt` added the struggling/weakness signal across practice modes.
- Public note copying was reframed as `Add to Library`; public copies can include Study Packs.

### v0.29.x - Bulk Generation and Generation Context

- Bulk Generation creates notes and Study Packs from topic lists using existing async generation infrastructure.
- Bulk Generation is available to authenticated onboarded users; ADMIN bypasses relevant generation quotas inside the orchestration.
- The bounded `bulk_generation_result` receipt is the only allowed v0.29.1 relaxation of the no batch/progress infrastructure rule.
- Content generation is leveled by course/program, not learner level; learner level remains for quiz/exam personalization.
- Backend onboarding/profile setup enforcement was added for content-creating mutations.

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
- Note Detail is the owner study hub: summary, key concepts, quiz, full notes, practice actions, recent sessions, readiness signal.

### Public Notes and Profiles

- Public note detail is read-only and separate from private Note Detail.
- Public note actions copy/create private owned notes first; private study actions never run against a public source note.
- Public Profile is `/public/creator/{username}` canonical, `/public/profile/{userId}` legacy-compatible.
- Profile Settings (`/profile`) is private editing; Public Profile owns visibility and sharing.
- Share behavior uses a shared modal pattern for notes and profiles.

### Study Plans / Collections

- Owned Study Plans are ordered note collections.
- Published/admin Study Plans are source plans; adoption creates owned snapshot copies.
- Recommended plans are course/program-scoped.
- Plan detail execution rows show action/status, not mastery.
- Dedicated readiness detail lives at `/collections/[id]/readiness`.
- Plan premium exams launch with `collectionId` and use only quiz-ready notes from that plan.

### Progress and Readiness

- `/progress` is available to all plans and is the canonical subject-level detail surface.
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
- `docs/product/EXAM_MODES.md` - locked quiz mode hierarchy
- `docs/product/PLANS.md` - plan tiers and quotas
- `docs/features/` - per-feature behavior rules
- `docs/codex-prompts/` - ready prompts for active work
- `docs/releases/` - per-version release notes
- `docs/skills/` - reusable AI workflow guidance

---

Context loaded. What are we working on?
