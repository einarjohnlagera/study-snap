# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this project is

**NoteLib** (rebranded from StudySnap — db/package names still use `studysnap`) is a notes-first study workspace. Users capture notes, generate AI-powered Study Packs, and practice with quizzes. Database schema uses the old name; do not rename unless explicitly asked.

Current version: **v0.13.0** — see `RELEASES.md` for in-progress scope, `docs/product/ROADMAP.md` for sequencing.

## Source-of-truth docs (read before implementing anything)

- `AGENTS.md` — implementation rules, anti-drift constraints, code quality rules. Always check this first.
- `docs/product/ROADMAP.md` — what's in scope for the current release and future phases
- `docs/product/SPEC.md` — canonical product behavior
- `docs/product/EXAM_MODES.md` — quiz mode hierarchy (locked contract; exactly 5 modes)
- `docs/features/<feature>.md` — per-feature behavior rules (48 files; check before changing any feature)
- `RELEASES.md` — every completed and in-progress change; always update when shipping work
- `GPT_CONTEXT.md` — version-stamped snapshot of the full product state; useful to understand where things stand

## Task routing

- **UX decisions, product questions, architecture tradeoffs, doc writing** → handle directly
- **Feature implementation, refactors, tests** → produce a structured Codex prompt using `docs/skills/codex-prompt-generator.md`, then the user submits it to Codex
- Never blur the line: if the user wants a Codex prompt, write one — don't start implementing instead

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

LLM models are config-driven: `LLM_MODEL_FREE` (default: `gpt-4.1-mini`) and `LLM_MODEL_PREMIUM` (default: `gpt-4.1`).

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

### Quiz session model

All quiz modes (Quick Review, Challenge Quiz, Adaptive Practice, Board Exam) share a single `QuickReviewSessionEntity`. The mode is stored as `QuickReviewSessionMode` enum. Session state (question list, selected choices, timer, difficulty) is stored as a JSONB `sessionState` column. `QuizSessionStateUtils` owns all reads/writes to that JSON — do not manipulate the JSON directly in service code.

Challenge Quiz is the only mode with progressive generation: starts at 5 questions, adds 5 at a time up to 20 via `ChallengeQuizService.generateMoreQuestions()`. Board Exam Mode is exempt from progressive generation — always a fixed set.

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

NoteLib does not overwrite generated content. The "Make a Copy" model is enforced product-wide: copy includes user-authored fields (title, courseProgram, subject, tags, content); copy excludes all AI/generated fields (summary, key concepts, quizzes, session history). Never add a regenerate-in-place flow.

---

## Key conventions

**Backend:**
- Throw named exception subclasses (e.g. `NoteNotFoundException`) rather than `new AppException(...)` inline. Create a new subclass if none exists.
- Repeated string literals in the same class must be extracted to `private static final` constants.
- `assertThatThrownBy` lambdas must contain exactly one method call (Sonar S5778).
- Do not use `Math.clamp` — use `Math.min(max, Math.max(min, value))` instead.

**Frontend:**
- Upgrade CTAs always go through `getUpgradeCtas(currentPlan)` from `src/config/plans.ts`. Never hardcode upgrade copy.
- Analytics events use the `AnalyticsEventType` Java enum (and matching frontend constant). Add to the enum before firing new events.
- Public note pages must not persist anonymous session state. No session is created until the user is authenticated.

**Commits:**
```
type: concise subject
- bullet of high-signal change
- bullet of high-signal change
```

Always update `RELEASES.md` with a bullet under the current version section when shipping any change.
