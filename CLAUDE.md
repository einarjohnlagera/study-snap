# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this project is

**NoteLib** (rebranded from StudySnap — db/package names still use `studysnap`) is a notes-first study workspace. Users capture notes, generate AI-powered Study Packs, and practice with quizzes. Database schema uses the old name; do not rename unless explicitly asked.

Current version: **v0.29.0** — see `RELEASES.md` for in-progress scope, `docs/product/ROADMAP.md` for sequencing.

## Active release: v0.29.0 — Bulk Generation & Generation-Context Correctness (anti-drift)

Base branch for this release: `releases/v0.29.0`. Three workstreams: (1) Bulk Generation, (2) Generation-context correctness (content leveled by course/program, not learner level), (3) Profile-type integrity + onboarding enforcement. Full scope in `ROADMAP.md`; locked rules:

**Bulk Generation:**

- **This is orchestration, not new AI.** The building blocks exist: `NoteGenerationService.generateFromTopic` (note content from a topic), the async Study Pack pipeline (`NoteService.startAsyncGenerationFromNote`), `NoteService.create`, and the existing quota services. Compose them — do not write a new generation path or a parallel pipeline.
- **No new job/progress infrastructure.** Each topic is generated content-first into a real note (note content is required, so the row appears once content-gen completes), then runs Study-Pack-gen on the existing executors; progress and load-on-refresh come from the real note rows + the `studyPackStatus` field the Library list already carries (`GENERATING → READY/FAILED`). **Do not add a batch-job entity, a progress table, or a new status enum.**
- **Per-item isolation + throttled fan-out.** One bad topic never fails the batch (try/catch per iteration — the `/notes/import-batch` pattern). Submit chains to the existing `studyPackGenerationTaskExecutor` with throttling so a large batch never saturates the pool or trips LLM rate limits.
- **Role-gated in Library, not `/admin`.** Entry is in the Library Create split-button, shown only to ADMIN; the flow is a dedicated `/library/bulk-generate` route. The gate is role-based and removable — opening it to all users later is a gate-flip, not a rebuild. Do not hardcode admin checks that would block future ungating.
- **Quota check built now; ADMIN bypasses.** Wire per-user quota enforcement through the existing quota services so the all-users path is real — ADMIN bypasses it. Defer only the "quota ran out mid-batch" partial-execution messaging.
- **Subject field = the batch subject; title + tags = AI.** Set the note's dedicated `subject` field from the batch (it beats the AI subject); each topic is only a generation seed, while the AI title and AI tags come from the Study Pack write-back. Do not strip or override AI tags with the subject. No per-profile pipeline fork.
**Generation-context correctness (learner level → course/program):**

- **Content is leveled by course/program, not learner level.** Strip `{LEARNER_LEVEL}` / `{LEARNER_LEVEL_GUIDANCE}` from the content prompts only — `note-generation-developer.txt` and the study-pack `developer.txt` (course/program is already injected). Shared/copied content must not depend on a per-user attribute.
- **Keep learner level in quiz/exam prompts and the exam pool** — those adapt to the *taker*, re-resolved per session. Do not remove `learnerLevel` from `StudyPackGenerationContext` (the exam pool still needs it); just stop using it in the two content prompts. Learner level is no longer *required* to generate a note/Study Pack. Remove the vestigial bulk Learner Level field.

**Profile-type integrity + onboarding enforcement:**

- **Re-prompt, never silent-default.** Gate on `profile_type` (not just `onboarding_completed_at`); a user missing a profile type gets one focused, blocking prompt asking only what's missing. A wrong default mis-personalizes invisibly.
- **Onboarding must be enforced server-side.** It is currently client-side-only (per-page guards, no middleware, no backend gate). Add server-side enforcement on key mutations so it is a real boundary — the client prompt alone is bypassable. New users cannot reach completed-but-null (`completeOnboarding` requires profile type); the null cohort is bounded (legacy + abandoned).

- **Out of scope (do not build without explicit ask):** the v0.31.0 work — collection-level bulk *quiz* generation over existing notes, async quiz generation, and teacher quiz-preview polish; and the v0.30.0 Readiness Signals work (exam-mode results → Progress).

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

**Generation context** (which learner level / course program the AI uses) is resolved in a shared utility: note-level `courseProgram` is always preferred; user profile `courseProgram` is fallback only. Do not bypass this resolver.

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

Always update `RELEASES.md` with a bullet under the current version section when shipping any change.

When closing a release (marking it Released), write a release notes file to `docs/releases/v{X.Y.Z}.md` using the Write tool. Follow the structure of existing files there: `# Release Notes: vX.Y.Z — Theme`, `## Release Theme` (one-sentence), `## Key Features` (bold emoji-prefixed titles with bullet points), `## Polish & Fixes` (flat bullet list). Do not output release notes as plain conversation text.

**Release kickoff checklist** (do this when opening a new version, before the first feature commit):
1. Add new version section to `RELEASES.md` and mark prior version Released.
2. Add new version section to `ROADMAP.md` and update "Current Release Baseline".
3. Bump `Current version` in `CLAUDE.md`.
4. Bump `version` in `frontend/package.json` and `backend/pom.xml` to match the new version number.
5. Update documentation baseline and version reference in `AGENTS.md`.
6. Update release baseline line in `README.md`.
7. Write release notes to `docs/releases/v{X.Y.Z}.md` for the version just closed.
