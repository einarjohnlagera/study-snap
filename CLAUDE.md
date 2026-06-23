# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this project is

**NoteLib** (rebranded from StudySnap — db/package names still use `studysnap`) is a notes-first study workspace. Users capture notes, generate AI-powered Study Packs, and practice with quizzes. Database schema uses the old name; do not rename unless explicitly asked.

Current version: **v0.32.0** — see `RELEASES.md` for in-progress scope, `docs/product/ROADMAP.md` for sequencing.

## Active release: v0.32.0 — Account & Communication Controls

Base branch for this release: `releases/v0.32.0`. Give users real control over their account and the email we send, and close the associated privacy/compliance gaps (GDPR right-to-erasure + portability; CAN-SPAM/GDPR one-click unsubscribe). Mostly additive; account deletion is the one destructive flow and needs careful handling. Full scope in `ROADMAP.md`. Locked rules:

- **Account deletion (right to erasure).** User-initiated, explicit-confirmation delete that removes/anonymizes the account and owned data (notes, Study Packs, sessions, collections, usage, auth providers, tokens). Must be transactional, idempotent, invalidate sessions/refresh tokens, and read as irreversible in the UI. `analytics_events.user_id` is already FK-free (v0.31.2) — **do not delete telemetry rows** on account deletion (orphaned ids are fine).
- **Data export / "Download my data" (portability).** Owner-only export of the user's own content (notes, Study Packs, sessions summary) as a downloadable file. No PII beyond the user's own data.
- **Email-preferences center + Settings redesign.** Replace the single "Study Reminders" card with a dedicated Email Preferences section listing every optional email type with per-type toggles, clearly separating **transactional** email (verification, password reset — always sent, non-toggleable) from **optional** email. Design (information design of the surface) is Claude's lane; per-toggle wiring reuses the existing reminder-flag pattern.
- **Weekly-summary opt-in flag.** Add `weekly_summary_reminders_enabled` (mirrors the `V28` reminder flags) gating `RetentionService.findWeeklySummaryUsers`. **Decided: default OFF (opt-in)** — `NOT NULL DEFAULT FALSE`, existing users backfilled disabled, new users created disabled. Codex prompt drafted: `docs/codex-prompts/weekly-summary-opt-out.md`.
- **Tokenized one-click unsubscribe link (optional emails only).** Signed/opaque per-user token + unauthenticated unsubscribe endpoint that flips the relevant preference without login. No PII in the token/link. Transactional emails are not unsubscribable.
- **Email deliverability hardening (mostly ops).** Primary fix is operational: upgrade the Resend tier (moots the daily/monthly cap and any priority queue); **do not build a pending-email outbox.** In-scope code: retry-on-429 (or pace the blast) in `ResendEmailService`. Stopgap: `RETENTION_WEEKLY_CRON=-`.
- **Anti-drift:** reuse the existing reminder-preference pattern (entity flag + repository finder + `updateStudyReminders` + Settings card) — no new preferences framework; no PII in unsubscribe tokens/links; account deletion is transactional/idempotent and never deletes FK-free telemetry; no email outbox/queue; add any new analytics events to the `AnalyticsEventType` enum (Java + frontend) before firing.
- **Scope not yet locked:** the ROADMAP "additional candidates" (change email address, account deactivation, marketing-consent capture, weekly-summary discovery nudge) are **not** committed — confirm before building. Out of scope: teacher-flow / bulk *quiz* generation (v0.33.0); any pending-email outbox/queue.

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

Always update `RELEASES.md` with a bullet under the current version section when shipping any change.

When closing a release (marking it Released), commit the closure directly on the `releases/vX.Y.Z` branch (no separate branch/PR), and write a release notes file to `docs/releases/v{X.Y.Z}.md` using the Write tool. Follow the structure of existing files there: `# Release Notes: vX.Y.Z — Theme`, `## Release Theme` (one-sentence), `## Key Features` (bold emoji-prefixed titles with bullet points), `## Polish & Fixes` (flat bullet list). Do not output release notes as plain conversation text.

**Always kick off a version before any implementation.** The kickoff checklist below is the **first commit** on a new `releases/vX.Y.Z` branch — committed directly to that branch — and must land **before** any feature/fix branch is cut or any code is written for the release. Do not start implementation on a version that has not been kicked off. If you find yourself implementing and the version is not yet opened (no `RELEASES.md` section, version refs not bumped), stop and run the kickoff first.

**Release kickoff checklist** (do this when opening a new version, before the first feature commit; commit these directly on the `releases/vX.Y.Z` branch — no separate branch/PR):
1. Add new version section to `RELEASES.md` and mark prior version Released.
2. Add new version section to `ROADMAP.md` and update "Current Release Baseline".
3. Bump `Current version` in `CLAUDE.md`.
4. Bump `version` in `frontend/package.json` and `backend/pom.xml` to match the new version number.
5. Update documentation baseline and version reference in `AGENTS.md`.
6. Update release baseline line in `README.md`.
7. Write release notes to `docs/releases/v{X.Y.Z}.md` for the version just closed.
