# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this project is

**NoteLib** (rebranded from StudySnap — db/package names still use `studysnap`) is a notes-first study workspace. Users capture notes, generate AI-powered Study Packs, and practice with quizzes. Database schema uses the old name; do not rename unless explicitly asked.

Current version: **v0.50.2 — Note Card Content Consistency** (In Progress, base branch `releases/v0.50.2`). Full shipped scope is in `RELEASES.md`; forward-looking scope and candidates are in `docs/product/ROADMAP.md`.

## Source-of-truth docs (read before implementing anything)

- `AGENTS.md` — implementation rules, anti-drift constraints, code quality rules. Always check this first.
- `docs/product/ROADMAP.md` — what's in scope for the current release and future phases
- `docs/product/SPEC.md` — canonical product behavior
- `docs/product/EXAM_MODES.md` — quiz mode hierarchy (locked contract; exactly 5 modes)
- `docs/features/<feature>.md` — per-feature behavior rules (48 files; **read before changing any feature, update after shipping any behavioral change** — updating `RELEASES.md` alone is not enough)
- `RELEASES.md` — every completed and in-progress change; always update when shipping work
- `GPT_CONTEXT.md` — version-stamped snapshot of the full product state; useful to understand where things stand

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

**Generation context** is resolved in a shared utility: note-level `courseProgram` is always preferred and profile `courseProgram` is fallback only. Static note/Study Pack content uses course/program; learner level remains in context for quizzes, exams, and exam-pool pre-warm. Do not bypass this resolver.

LLM fan-out batches run on a dedicated `llmParallelTaskExecutor`; the main `studyPackGenerationTaskExecutor` must not be passed to `generateLongExamParallel`.

Every account is guaranteed to have a non-null `learnerLevel` after onboarding; the teacher Generate Quiz modal's Target Level override pre-fills from the last generation on that note and falls back to the profile level.

### Quiz session model

All quiz modes (Quick Review, Challenge Quiz, Adaptive Practice, Board Exam) share a single `QuickReviewSessionEntity`. The mode is stored as `QuickReviewSessionMode` enum. Session state (question list, selected choices, timer, difficulty) is stored as a JSONB `sessionState` column. `QuizSessionStateUtils` owns all reads/writes to that JSON — do not manipulate the JSON directly in service code.

Challenge Quiz is the only mode with progressive generation: starts at 5 questions, adds 5 at a time up to 20 via `ChallengeQuizService.generateMoreQuestions()`. Board Exam Mode is exempt from progressive generation — always a fixed set.

Recent Sessions and library `lastSessionCompletedAt` aggregate completed activity across the shared session rows; use `getQuizSessionModeLabel` for mode-to-label mapping instead of inlining labels.

### Profile-type branching

Profile type (`ProfileType` enum: `STUDENT`, `BOARD_EXAM`, `TEACHER`, `PARENT`, `PROFESSIONAL`) drives dashboard emphasis, quiz mode availability, and some generation behavior. It does **not** fork entity tables — all profiles share the same Note/StudyPack/Session model.

Teacher detection in services uses: `user.getProfileType() == ProfileType.TEACHER || user.getRole() == UserRole.ADMIN`

`PARENT` and `PROFESSIONAL` exist as enum values with no feature implementation yet.

### Feature gating

`FeatureGateService` is the single source of truth for plan-based access control. Monthly quotas live in `UserUsageEntity` (reset by `BillingUsageResetJob`). The `Feature` enum values are `ADAPTIVE_QUIZ`, `DIFFICULTY_SELECTION`, `WEAK_CONCEPT_DETECTION`.

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

**Always kick off a version before any implementation.** The kickoff checklist below is the **first commit** on a new `releases/vX.Y.Z` branch — committed directly to that branch — and must land **before** any feature/fix branch is cut or any code is written for the release. Do not start implementation on a version that has not been kicked off. If you find yourself implementing and the version is not yet opened (no `RELEASES.md` section, version refs not bumped), stop and run the kickoff first.

**Release kickoff checklist** (do this when opening a new version, before the first feature commit; commit these directly on the `releases/vX.Y.Z` branch — no separate branch/PR):
1. Add new version section to `RELEASES.md` and mark prior version Released.
2. Add new version section to `ROADMAP.md` and update "Current Release Baseline".
3. Bump `Current version` in `CLAUDE.md`.
4. Bump `version` in `frontend/package.json` and `backend/pom.xml` to match the new version number.
5. Update documentation baseline and version reference in `AGENTS.md`.
6. Update release baseline line in `README.md`.
7. Write release notes to `docs/releases/v{X.Y.Z}.md` for the version just closed.
8. Scan `ROADMAP.md`'s Backlog Index: bump `Last reviewed`, check whether any `Gate` condition became true, and verify every `docs/claude-prompt/*-out/` planning directory still has a row — this is the only enforced checkpoint against a large planning effort (a multi-document Fable session, a paused exploration) silently going unindexed across release cycles.
