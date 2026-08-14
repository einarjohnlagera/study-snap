# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this project is

**NoteLib** (rebranded from StudySnap — db/package names still use `studysnap`) is a notes-first study workspace. Users capture notes, generate AI-powered Study Packs, and practice with quizzes. Database schema uses the old name; do not rename unless explicitly asked.

Current version: **v0.77.0 — Evidence-Gated Weak Concept Recommendation** (Released 2026-08-14, base branch `releases/v0.77.0`). First slice of the ratified *Adaptive Practice as the recommendation engine* direction. `DashboardService.resolveTodayFocusWeakConcepts` resolves weak concepts from **the single latest completed Quick Review** today — the copy literally says *"Your latest Quick Review… showed N weak concepts"* — so one bad quiz triggers a recommendation. It now resolves from `ConceptHealth`'s existing **`incorrect_streak`** against the existing **`TWICE_MISSED_STREAK_THRESHOLD = 2`**, so recommendations follow persistent weakness. **Nothing new is recorded and no second evidence bar is invented.** **WITHIN-PACK ONLY** — cross-pack needs canonical concept identity (`concept` is free text keyed per Study Pack), which is ADR-sized. **⚠️ Standing constraint until 2026-09-12: do NOT remove the Challenge Quiz Adaptive Practice entry point.** This release builds the recommendation and removes nothing, so no gate is overridden. Previous: **v0.76.1 — Adaptive Practice Entry Attribution** (Released 2026-08-14, base branch `releases/v0.76.1`). `ADAPTIVE_PRACTICE_STARTED` now records **where** a session was launched from, so the open **`[CHECKPOINT — due 2026-09-12]`** can answer its secondary question — not just *whether* starts fell, but *where the surviving ones come from*. The recommendation-engine direction ratified 2026-08-14 rests on Dashboard discovery, which was untestable before this. **Seven values plus `direct`**, covering every route that can start a session; the server allowlists them and normalises unknown input, never persisting it verbatim. **It cannot contaminate the read it serves:** the primary metric is a total count, and this is additive metadata with no user-visible behaviour change. **Reuses the `entry` query-param convention** from `challenge-quiz-entry.ts`. **⚠️ STANDING CONSTRAINT, not release-scoped: do NOT remove the Challenge Quiz Adaptive Practice entry point before 2026-09-12.** It is ratified direction, but `v0.74.0` already removed the Quick Review route and a second removal inside the same window confounds that read instead of answering it. It also carries the two direction docs ratified 2026-08-14, retargeted out of a separate `main` PR **because merging to `main` auto-deploys to production** and two merges would mean two interruptions. Previous: **v0.76.0 — Messaging Architecture: The Money Surfaces** (Released 2026-08-14, base branch `releases/v0.76.0`). Second slice of the ratified Messaging Architecture initiative (2026-08-01): *"We sell the learning system. Features simply support that promise."* **Scope is the money surfaces only** — `/pricing` finishes the hierarchy `v0.68.0` started, plus the in-app upgrade prompts; the landing page and Exam Hub upsell are deferred to their own slices. **`FREE.title` is the item the roadmap names as explicitly owed, and the `v0.68.0` revert is its specification:** a candidate was written and reverted there because it was derived from `PLUS`/`PRO` symmetry, which contradicts the ratified `FREE = adopt` placement — if the new title only reads well beside its siblings, it is wrong again. **Positioning only: no pricing or quota changes, product names untouched everywhere, no feature becomes the hero, and `PROFESSIONAL` copy stays unwired** because that profile is enum-only with no feature set behind it. Previous: **v0.75.0 — Authoring by Inference** (Released 2026-08-14). Implements `ADR-001` → *"Authoring populates by inference, not manual classification"*: a curator should confirm inferred note metadata rather than compose three orthogonal axes by hand. **Both of that section's sequenced gates turned out to be already clear** — R4 resolved 2026-08-04, and the *"true first step"* it names (editable authoring metadata on a `STUDY_PACK_READY` note) shipped in `v0.70.0` per `AGENTS.md:1081`; the ADR was stale for four releases and is amended in this release. **Scope is DEPTH ONLY — Domain Context is deliberately excluded and this is binding:** `ADR-001` constraint 1 authorizes an inference chain for `learner_level` alone, and `domain_context IS NULL` is the promotion-backlog marker, so a pre-filled select a curator tabs past would silently convert an observable backlog row into a decided one. **The two legs differ in size:** leg 2 (author profile → depth) merely completes a pattern already shipped beside it — `bulk-generation-page-client.tsx:132` pre-fills `courseProgram` from the profile while `learnerLevel` has no fallback — whereas leg 1 (Review Set → depth) has **no source at all**, since `NoteCollectionEntity` carries no `learnerLevel` and no authoring surface knows its target Review Set. Inference is **always a UI pre-fill, never a server-side default write**. Previous: **v0.74.0 — Quiz Progression** (Released 2026-08-13). Closed a scored assessment whose answer key sat on an adjacent tab: `practice-quiz-card.tsx:25` renders the saved quiz with `revealAnswer`, and Quick Review administers **those same questions** (`note.quiz`) — so a learner can read every answer and then be "assessed" on them, which makes the score meaningless *and* corrupts `ConceptHealth`, locked since v0.37.0 to move only from genuine assessment. The fix is to lock (not hide) the Quiz tab until the learner scores a perfect Quick Review. **Two rationales run in parallel and must stay distinguishable:** *integrity* (don't hand someone the answer key to a test they haven't sat) is satisfied by any completed Quick Review, since the first attempt at each question is the genuine assessment `ConceptHealth` records; the **perfect-score gate is the *progression* layer on top**, and it is the falsifiable half. **Mastery = 5/5, and a perfect score reached through *Redo Mistakes* counts** (owner rulings 2026-08-12) — which is what keeps a perfect-score gate from being a dead end. Challenge Quiz stays open from the start, and that is coherent rather than arbitrary: Challenge generates its own questions, so the answer key cannot spoil it. Brief of record: `docs/claude-plans/next-release-candidates-consultation-prompt.md`. Previous: **v0.73.0 — Onboarding Redesign** (Released 2026-08-12). It went at the funnel's largest single leak — **132 learners, 35.2% of all signups, verify their email and never finish onboarding** — by making the first run explain the product rather than configure an account. **Justified on comprehension, not retention, and that distinction is binding:** activation is already 52.2%, which caps activation-volume work below the retention lever, and the volume hypothesis already failed against a pre-committed rule. Design direction: `docs/claude-plans/onboarding-redesign-ux-review.md`. Previous: **v0.72.1 — Constraint Check** (Released 2026-08-11). It opened on a production read — is the binding constraint retention *rate* or activation *volume*? **The volume hypothesis failed** (activation is already 52.2%, which structurally caps the volume lever below the rate lever), so the Onboarding Intent Router residuals were not taken and the release rescoped to validating the retention metric itself. **That read resized the project's oldest constraint: the "2.4% W1→W2 retention" figure quoted for months counts only days 7–14 after a first Study Pack, and sees 3 of the 11 learners who actually returned — the wider reading is ~7.2%.** Both halves hold: the number understated reality ~3.7×, *and* 141 of 152 activated learners never came back at all. The constraint is mis-sized, not imagined; **do not quote 2.4% as a current figure**. The admin funnel now reports four windows side by side with the strict figure preserved for comparability. Previous: **v0.72.0 — Return Loop** (Released 2026-08-11), which shipped H1+H5 (commitment device + pre-decided return action) under a decision rule pre-committed by an earlier release, and carries two dated checkpoints (proximal **2026-09-10**, distal **2026-11-09** — the latter re-specified to the wider window) recorded in `ROADMAP.md`'s Backlog Index. Before that, **v0.71.2 — Catalog Management** closed the Applicable Programs arc that ran from **v0.71.0** (Release B of `docs/architecture/ADR-001-canonical-knowledge-architecture.md`) through **v0.71.1**. Full shipped scope is in `RELEASES.md`; forward-looking scope and candidates are in `docs/product/ROADMAP.md`.

## Source-of-truth docs (read before implementing anything)

- `AGENTS.md` — implementation rules, anti-drift constraints, code quality rules. Always check this first.
- `docs/architecture/ADR-NNN-*.md` — **binding architecture decision records; read any that touch what you are changing.** `ADR-001-canonical-knowledge-architecture.md` (Accepted 2026-08-03) governs the Note metadata axes: Subject (*what*), **Domain Context** (*how it is authored* — the sole LLM domain constraint), **Note Learner Level** (*how deep*), **Applicable Programs** (*where it appears* — discovery only, never reaches a prompt), and Target Audience (*who* — discovery only, never depth). An ADR outranks a feature doc where they disagree.
- `docs/architecture/ARCHITECTURE.md` and `docs/architecture/DATA_MODEL.md` — system shape and schema
- `docs/product/ROADMAP.md` — what's in scope for the current release and future phases
- `docs/product/SPEC.md` — canonical product behavior
- `docs/product/EXAM_MODES.md` — quiz mode hierarchy (locked contract; exactly 5 modes)
- `docs/features/<feature>.md` — per-feature behavior rules (48 files; **read before changing any feature, update after shipping any behavioral change** — updating `RELEASES.md` alone is not enough)
- `RELEASES.md` — every completed and in-progress change; always update when shipping work
- `docs/gpt-contexts/GPT_CONTEXT.md` — version-stamped snapshot of the full product state; useful to understand where things stand

## Task routing

| Task type | Agent | Rule |
|---|---|---|
| UX decisions, product questions, architecture tradeoffs, doc writing | **Claude Code** — handle directly | Core Claude Code responsibility |
| Frontend-only additions ≤ ~50 LOC, no new infrastructure (e.g. add a `GuidanceTip`, fix a prop, add a CSS class) | **Claude Code** — implement directly | Too small to justify a Codex prompt; cheaper to do inline |
| Isolated bug fixes in 1–3 files with a clear root cause | **Claude Code** — implement directly | Same — prompt overhead exceeds the fix cost |
| New features touching backend (new endpoint, migration, service logic) | **Codex** — write prompt first | Anti-drift rules in `AGENTS.md` must be enforced across files |
| Multi-system changes (frontend + backend together) | **Codex** — write prompt first | Scope requires full codebase context |
| Refactors or additions touching > 5 files or > ~100 LOC | **Codex** — write prompt first | Too large for reliable inline work |
| Tests for agreed behavior | **Codex** — write prompt first | Already the rule |

**Tiebreaker:** if the change requires reading `AGENTS.md` anti-drift rules and applying them across many files, use Codex. If it's dropping a known component in a known slot with no logic changes, Claude Code does it directly.

- Always check `docs/codex-prompts/` for an existing prompt before writing a new one — if one exists for the current release item, use it directly
- Always use `docs/skills/codex-prompt-generator.md` when writing a Codex prompt
- Never blur the line: if the user wants a Codex prompt, write one — don't start implementing instead
- Never implement automatically when the user asks for a prompt
- **After Codex delivers:** always ask Claude to audit the diff before committing — error states, transactions, idempotency, and load-on-refresh are the most common gaps

---

## Development commands

### Local database

```bash
# Start PostgreSQL only (preferred for local dev — backend runs in IDE)
docker compose up postgres -d

# Start everything including backend
docker compose up -d
```

DB: `study_snap` on port `5433` (mapped from container's 5432). User: `ss_user`.

### Backend (Spring Boot / Maven)

```bash
cd backend

# Run (requires .env file or env vars — see application.yaml for keys)
./mvnw spring-boot:run

# Build
./mvnw clean package -DskipTests

# Run all tests
./mvnw test

# Run a single test class
./mvnw test -Dtest=QuizSessionStateUtilsTest

# Run a single test method
./mvnw test -Dtest=QuizSessionStateUtilsTest#methodName
```

Backend runs on `http://localhost:8080/api`.

Required env vars (put in `backend/.env`):
- `DB_USER`, `DB_PASSWORD`, `DB_HOST`, `DB_PORT`, `DB_NAME`
- `LLM_API_KEY` (OpenAI key)
- `JWT_SECRET`

LLM models are config-driven: `LLM_MODEL_FREE` (default: `gpt-4.1-mini`), `LLM_MODEL_PREMIUM` (default: `gpt-4.1`), and `LLM_MODEL_CRITIQUE` (default: `gpt-4.1-mini` for short Interview Practice feedback).

### Frontend (Next.js)

```bash
cd frontend

npm install
npm run dev        # http://localhost:3000
npm run build      # production build (uses --webpack flag)
npm run lint       # ESLint
npm test           # Jest (jsdom, runs in-band)

# Run a single test file
npm test -- path/to/file.test.ts

# Run tests matching a pattern
npm test -- --testNamePattern="guidance engine"
```

Path alias: `@/` maps to the frontend root.

---

## Architecture: what requires reading multiple files to understand

### The Note → Study Pack pipeline

The core async flow that touches the most files:

1. User creates a Note (`NoteEntity`, status `DRAFT`)
2. User triggers generation → `NoteService.startAsyncGenerationFromNote()` → marks note `GENERATING` and enqueues work
3. `LlmStudyPackService` (impl: `OpenAiLlmStudyPackService`) builds prompt context from note + user profile, calls OpenAI, validates JSON schema, persists `StudyPackEntity`
4. Note moves to `STUDY_PACK_READY` or `FAILED`
5. Frontend polls the note detail endpoint until status changes

Prompts live in `backend/src/main/resources/prompts/study-pack-v1/`. Each quiz mode has its own `{mode}-developer.txt` + `{mode}-system.txt` pair.

**Generation context** is resolved in `StudyPackGenerationContextResolver`; do not bypass it. Authoring domain resolves note `domainContext` -> exactly one joined catalog program (`note_course_program`) -> note `courseProgram` -> profile `courseProgram`, while curriculum level resolves note `learnerLevel` -> profile `learnerLevel` -> `COLLEGE`. Static note/Study Pack content uses the effective domain plus note-authored level. Quizzes and exams keep that level as the curriculum floor; a lower reader level may soften scaffolding and wording but never lower curriculum, terminology, or difficulty.

LLM fan-out batches run on a dedicated `llmParallelTaskExecutor`; the main `studyPackGenerationTaskExecutor` must not be passed to `generateLongExamParallel`.

Every account is guaranteed to have a non-null `learnerLevel` after onboarding; the teacher Generate Quiz modal's Target Level override pre-fills from the last generation on that note and falls back to the profile level.

### Quiz session model

All quiz modes (Quick Review, Challenge Quiz, Adaptive Practice, Board Exam) share a single `QuickReviewSessionEntity`. The mode is stored as `QuickReviewSessionMode` enum. Session state (question list, selected choices, timer, difficulty) is stored as a JSONB `sessionState` column. `QuizSessionStateUtils` owns all reads/writes to that JSON — do not manipulate the JSON directly in service code.

Challenge Quiz is the only mode with progressive generation: starts at 5 questions, adds 5 at a time up to 20 via `ChallengeQuizService.generateMoreQuestions()`. Board Exam Mode is exempt from progressive generation — always a fixed set.

Recent Sessions and library `lastSessionCompletedAt` aggregate completed activity across the shared session rows; use `getQuizSessionModeLabel` for mode-to-label mapping instead of inlining labels.

### Profile-type branching

Profile type (`ProfileType` enum: `STUDENT`, `BOARD_EXAM`, `TEACHER`, `PARENT`, `PROFESSIONAL`) drives dashboard emphasis, quiz mode availability, and some generation behavior. It does **not** fork entity tables — all profiles share the same Note/StudyPack/Session model.

Teacher detection in services uses: `user.getProfileType() == ProfileType.TEACHER || user.getRole() == UserRole.ADMIN`

**On note-authoring paths that gate on curator status, that check is preceded by an onboarding guard** (`v0.71.0`, completed in `v0.71.1`): `NoteService.isTeacherSelectableOwner`, `NoteGenerationService.isCurator` and `NoteBulkGenerationService.isCurator` all return `false` when `onboardingCompletedAt == null`, *then* apply the role check. Nobody curates during onboarding — the flow has no catalog picker, so a curator-role account reaching a note-authoring path mid-onboarding was asked for `courseProgramIds` no onboarding screen can supply, which made onboarding uncompletable for every ADMIN account. This removes no authority; once onboarding completes the account is a full curator. Do not restore the bare role check on these paths. **All three now carry the guard** — `NoteBulkGenerationService` was the last holdout, recorded as a `v0.71.0` Known Limitation and closed in `v0.71.1` group 1 item 3, so there is no longer an in-repo exception to point at.

`PARENT` and `PROFESSIONAL` exist as enum values with no feature implementation yet.

### Feature gating

`FeatureGateService` is the single source of truth for plan-based access control. Monthly quotas live in `UserUsageEntity` (reset by `BillingUsageResetJob`). The `Feature` enum values are `ADAPTIVE_QUIZ`, `LONG_EXAM_SESSION`, `INTERVIEW_PRACTICE`, `WEAK_CONCEPT_DETECTION` (`DIFFICULTY_SELECTION` removed in v0.60.1 — Challenge Quiz's manual difficulty selector was removed entirely).

Plan tiers: `FREE / PLUS / PRO`. Payments via Xendit hosted checkout (manual renewal, no auto-charge). Webhook-confirmed activation only — never trust frontend-reported payment state.

### Frontend data flow

The frontend uses Next.js App Router with a mix of server components (data fetching) and client components (interactivity). API calls go through `lib/api.ts`, a custom axios-based wrapper. Auth state (JWT + refresh token) is managed via cookies; there is no Redux or Zustand — state is component-local or passed as props.

Quiz mode visibility per profile is controlled by `lib/exam-mode-visibility.ts` — this is the single source of truth for which modes appear per profile type. Do not hardcode profile checks in UI components.

`lib/guidance-engine.ts` controls one-time contextual tip display. Tips are stored in localStorage via `lib/guidance.ts`. Do not add new one-time tips without going through `pickActiveGuidance()`.

### Versioning rule

The Study Pack is the generated version of a note. Never auto-regenerate. Regeneration always requires explicit user confirmation and updates the existing Study Pack in-place so quiz/session history stays linked. Public-note copies include the linked StudyPack when one exists (intentional, documented exception); owner self-copies still copy only user-authored fields and exclude generated content. Do not "fix" either of these rules back to the old copy-only model.

---

## Key conventions

**Backend:**
- Throw named exception subclasses (e.g. `NoteNotFoundException`) rather than `new AppException(...)` inline. Create a new subclass if none exists.
- Repeated string literals in the same class must be extracted to `private static final` constants.
- `assertThatThrownBy` lambdas must contain exactly one method call (Sonar S5778).
- Use `Math.clamp(value, min, max)` for range-clamping (Java 21 standard; Sonar S6877 flags the nested `Math.min/Math.max` form).

**Frontend:**
- Upgrade CTAs always go through `getUpgradeCtas(currentPlan)` from `src/config/plans.ts`. Never hardcode upgrade copy.
- Analytics events use the `AnalyticsEventType` Java enum (and matching frontend constant). Add to the enum before firing new events.
- Public note pages must not persist anonymous session state. No session is created until the user is authenticated.
- Use `globalThis` instead of `window`, `self`, or `global` for browser globals (`globalThis.localStorage`, `globalThis.setTimeout`, `globalThis.addEventListener`, etc.). ESLint enforces this rule; `window` references will fail the lint check.

**Commits:**
```
type: concise subject
- bullet of high-signal change
- bullet of high-signal change
```

**Always present a plan before implementing.** For any non-trivial change, describe what you intend to do and wait for explicit approval before writing code. Do not start implementing while explaining the plan.

**Never commit automatically.** Always present changes and wait for an explicit "commit" instruction from the user before running `git commit`. Do not stage, commit, or push as a side effect of implementing or updating docs.

**Branch protection is enforced on `main`.** Direct pushes to `main` are blocked by a repository ruleset. All changes — including docs-only changes — must go on a feature or docs branch and be merged via pull request. Never commit directly to `main`.

**Release-management commits go directly on the release branch.** Release **kickoff** (opening a version) and release **sign-off** (closing / marking a version Released) are committed straight to `releases/vX.Y.Z` — do **not** create a separate sub-branch or PR for them. Feature and fix work still goes on its own branch and is merged into `releases/vX.Y.Z` via PR. (`main` stays protected; only the release branch receives these direct release-management commits.)

**Small documentation-only fixes may also go directly on the release branch, no branch/PR.** Scope: doc files only (`.md`, no `.java`/`.ts`/`.tsx`/migration/config/test changes), typically touching one file, correcting something rather than introducing new scope (a version-drift correction, a stale tracker line, a doc/copy fix, adding a process note or rule). Branch+PR overhead isn't worth it at this size. Anything touching actual code — even a genuine one-line bug fix — still goes on its own branch and PR regardless of size, since code changes should go through the normal review/CI path. Still wait for an explicit "commit it" before committing, per the rule above — this only changes *where* the commit lands, not whether to ask first.

Always update `RELEASES.md` with a bullet under the current version section when shipping any change.

When closing a release (marking it Released), commit the closure directly on the `releases/vX.Y.Z` branch (no separate branch/PR), and write a release notes file to `docs/releases/v{X.Y.Z}.md` using the Write tool. Follow the structure of existing files there: `# Release Notes: vX.Y.Z — Theme`, `## Release Theme` (one-sentence), `## Key Features` (bold emoji-prefixed titles with bullet points), `## Polish & Fixes` (flat bullet list). Do not output release notes as plain conversation text.

**Before closing a release, decide the right depth of pre-signoff pressure test — do not default to the heaviest option every time.** Per-PR `/audit-diff` is diff-scoped: it cannot see (a) pre-existing code a PR didn't touch that a new feature increases exposure to, or (b) an invariant interaction between two features shipped in *different* PRs that both touch the same shared method. Both classes of bug only surface via a whole-release view, but that view is expensive, so gate it on release shape:
- **Full pressure test** (multiple Explore agents inventorying every backend/frontend file touched this release, synthesized and pressure-tested via `advisor`) when the release has a single concept/entity touching 3+ surfaces (e.g. backend + several frontend consumers), OR roughly 6+ PRs, OR more than one PR touched the same pre-existing shared method/component.
- **Otherwise**, a single `advisor()` call summarizing what shipped is enough — cheaper, and still catches anti-drift violations.
- Fix or explicitly document (in `RELEASES.md`, as a "Known limitations" note) every finding before signing off — never silently drop a finding.
- **When the full pressure test runs, its agents must start cold.** Spawn them with the Agent tool, no inherited context, and instruct them explicitly to read the real code rather than trust any summary — including summaries written by the session spawning them. In `v0.74.0` the two most severe defects were in code that session had written *and* reviewed, and one was actively protected by a test asserting the wrong behaviour; a reviewer carrying that session's context inherits its blind spots along with its knowledge.

**Also before closing: re-read every touched feature doc against the FINAL code state, not against the PR that last edited it.** This is a distinct step from "update `docs/features/<feature>.md` when shipping a behavioral change" — that rule is per-PR, and per-PR is exactly where it fails. A later PR in the same release changes the behaviour again and updates *some* docs but not others; each PR looks correct in isolation, and the drift is only visible once everything has landed. **`v0.74.0` accumulated seven such contradictions in one release** — `quick-review.md` claimed Quick Review does not write `ConceptHealth` (false for a month, and the claim the release's own justification rested on), four more lines in the same file, a `ui-standards.md` violation, and two statements in `quiz.md` that only a cold-context agent found. Cheapest reliable form: for each behavioural claim in a touched feature doc, locate the code that implements it and confirm the claim still describes it; a claim you cannot anchor to code is either stale or was never true.

**Also before closing: if anything in the release shipped ahead of its own evidence, it owes a `[CHECKPOINT — due YYYY-MM-DD]` row in `ROADMAP.md`'s Backlog Index, added in the signoff commit itself.** Full gate — the five required properties, the deploy-relative dating rule, the two-tier design for small denominators, and the verify-the-fallback-is-unbuilt rule — is in `.claude/commands/signoff.md`. **This is the only step that can catch a missing checkpoint:** kickoff step 9 scans for checkpoints that are *overdue*, which cannot detect one that was never written. `v0.72.0` shipped H1+H5 on an explicitly ambiguous read and signed off with none; the gap survived until the next kickoff's gate scan happened to surface it.

**Escalate to a fresh, independently-instructed review for hard-to-find bugs — gate it, don't default to it.** `advisor()` stays the default check before presenting any non-trivial root-cause finding as settled (call it before declaring something done, per its own instructions — the actual failure mode in practice is skipping that checkpoint, not lacking a stronger option). Escalate past `advisor()` to a fresh agent with no inherited context (Agent tool, `model: "opus"`, explicitly told to read the real code/data rather than trust a summary) only when a root-cause claim meets one of these:
- a data/metric relationship that shouldn't be mathematically or logically possible under the stated explanation (e.g. one `COUNT(DISTINCT ...)` exceeding another it should be bounded by);
- the bug class is inherently hard to reason about serially — concurrency, async ordering, effect/component lifecycle timing, migrations, anything where the mechanism has to be traced through indirect state rather than observed directly;
- the fix is about to ship and touches something that already feeds, or will feed, a real product/business decision (e.g. a metric behind a retention read).

A typical isolated bug fix needs neither — direct verification or a single `advisor()` call is enough. This escalation is expensive when used (tens of thousands of tokens, several minutes) — the trigger conditions above are the gate, not general caution.

**Always kick off a version before any implementation.** The kickoff checklist below is the **first commit** on a new `releases/vX.Y.Z` branch — committed directly to that branch — and must land **before** any feature/fix branch is cut or any code is written for the release. Do not start implementation on a version that has not been kicked off. If you find yourself implementing and the version is not yet opened (no `RELEASES.md` section, version refs not bumped), stop and run the kickoff first.

**Release kickoff checklist** (do this when opening a new version, before the first feature commit; commit these directly on the `releases/vX.Y.Z` branch — no separate branch/PR):
1. Add new version section to `RELEASES.md` and mark prior version Released.
2. Add new version section to `ROADMAP.md` and update "Current Release Baseline".
3. Bump `Current version` in `CLAUDE.md`.
4. Bump `version` in `frontend/package.json` and `backend/pom.xml` to match the new version number.
5. Update documentation baseline and version reference in `AGENTS.md`.
6. Update release baseline line in `README.md`.
7. Write release notes to `docs/releases/v{X.Y.Z}.md` for the version just closed.
8. Scan `ROADMAP.md`'s Backlog Index: bump `Last reviewed`, check whether any `Gate` condition became true, and verify every `docs/claude-prompt/*-out/` planning directory **and every `docs/claude-plans/` file** still has a row — this is the only enforced checkpoint against a large planning effort (a multi-document Fable session, a paused exploration) silently going unindexed across release cycles. The `docs/claude-plans/` half was added 2026-08-10 after the `v0.71.1` kickoff found five files there unindexed; the Backlog Index intro carries the narrow *release artifact* exemption that covers finished one-off consultation prompts and sizing queries. Check both directories — the invariant always said "or session-plan file," but the checklist only ever named one of them.
9. Scan the Backlog Index for `[CHECKPOINT — due YYYY-MM-DD]` rows past their due date (see "Gate types" in the Backlog Index intro) — a checkpoint that's overdue and unactioned is the same silent-drift risk step 8 guards against, just for vision-driven work shipped ahead of evidence instead of unindexed planning docs.
