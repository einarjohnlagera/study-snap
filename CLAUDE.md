# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## ⚠️ PRODUCTION DATABASE: READ-ONLY, ALWAYS, NO EXCEPTIONS

**Claude executes READ-ONLY queries against the production database. Ever. Only the OWNER executes
writes.** Owner rule, stated 2026-09-04 during `v0.112.0` while connecting the Render MCP server.

This is not a per-task permission that a later session can re-negotiate, and it is **not** softened by
any of the usual justifications: not by a request being urgent, not by an incident being in progress,
not by a fix being "obviously correct", not by the write being small, reversible, or already agreed in
principle, and **not by the owner approving a plan that happens to contain one** — approving a plan is
not approving Claude to execute its writes.

**Read-only means:** `SELECT`, `SHOW`, `EXPLAIN` (without `ANALYZE`, which executes the statement),
and reads of catalog/stats views such as `pg_stat_activity`. **Everything else is the owner's to run** —
`INSERT`, `UPDATE`, `DELETE`, `TRUNCATE`, every DDL statement, every Flyway migration, `VACUUM`,
`REINDEX`, session/database `SET`, `SELECT … FOR UPDATE`, and anything calling a function that writes.

**⚠️ THIS BINDS EVERY ROUTE TO PRODUCTION DATA, NOT JUST A SQL PROMPT** — the Render MCP server (or any
future database MCP/tool), `psql`, a JDBC or script connection, a one-off endpoint, and an admin
surface that performs the write on Claude's behalf. **Routing a write through a tool does not make it
Claude's to run.** The Render MCP server can also modify environment variables and trigger deploys;
those are the same class of action and are the owner's, not Claude's.

**When a write is genuinely needed:** write the exact statement into a `docs/claude-plans/*.sql` file
with the expected row count and how to verify it, hand it to the owner, and stop. That is the whole
protocol — **do not run it and then report it.**

**⚠️ IF A PRODUCTION WRITE HAS ALREADY HAPPENED, SAY SO IMMEDIATELY AND PLAINLY.** An unreported write
to production is far worse than a reported one, and concealment is the failure this rule exists to make
impossible.

## What this project is

**NoteLib** (rebranded from StudySnap — db/package names still use `studysnap`) is a notes-first study workspace. Users capture notes, generate AI-powered Study Packs, and practice with quizzes. Database schema uses the old name; do not rename unless explicitly asked.

Current version: **v0.130.0 — Notification Inbox** (Released 2026-09-07, kicked off 2026-09-07, base branch `releases/v0.130.0`, **cut from `releases/v0.129.0` rather than `main`** because `v0.129.0`'s release PR #1340 is blocked by the `main` ruleset's `require_extra_approval_for_unattributed_changes` parameter — the `v0.120.0`/`v0.111.0` precedent, and **not** a reason to bypass: `--admin` is an OWNER action). **SCOPE IS STAGES 3 AND 4** of `docs/claude-plans/in-app-notifications-and-review-set-adoption-signals-stage1.md`. **(3)** the `notifications` substrate — **a UNIQUE index on `(recipient_user_id, dedup_key)` IS the idempotency guarantee, not a service check that can race** — plus inbox, read/dismiss, actionable count, bell in the existing header, polling, mobile sheet, deep-link contract. **(4)** Admin What's New — `announcements` with `DRAFT → PUBLISHED → ENDED`, **content IMMUTABLE after publish** (to correct copy: end it and publish a replacement), audience `EVERYONE`/`PROFILE_TYPE`/`PLAN_TYPE`, fan-out, optional expiry, internal-only CTA. **⚠️ Title/body/CTA are COPIED AT FAN-OUT and never re-read** — `announcement_id` is provenance, NOT live content inheritance. **⚠️ PAIRED BECAUSE STAGE 3 ALONE SHIPS AN EMPTY INBOX, AND PRODUCTION SAYS SO: 1 ACCEPTED relationship, 1 PENDING invitation, ZERO note shares.** Stage 4 is the only producer that generates content on demand. **⚠️⚠️ STAGE 5 IS DEFERRED ON CONTAMINATION, NOT EFFORT — DO NOT BUILD ANY STAGE 5 EVENT BEFORE 2026-09-19.** Its headline event surfaces a pending connection request to the invitee, which is exactly the nudge that converts PENDING into ACCEPTED — **and `ACCEPTED` is the kill criterion of `[CHECKPOINT — due 2026-09-19]`, whose denominator is ONE.** One notification-caused acceptance would double the metric and the checkpoint would be reading the notification rather than demand. **Stage 6 stays BLOCKED on §8's drift-signature dedup decision; Stage 7 is out of scope.** **⚠️ ANTI-DRIFT: NO Learning Connections work. NO Stage 5/6/7 events** — no connection-request notification, no note-share notification, no Review Set update sweep, no Assignment. **Do NOT fabricate a recipient for a quiz share link. Do NOT notify on a Note becoming PUBLIC.** **⚠️ POLL, DO NOT STREAM** (no WebSocket/SSE exists; `lib/study-pack-generation.ts` is the pattern). **⚠️ Never one feature query per notification row.** **⚠️ Announcements NEVER inflate the numeric badge, and NEVER render a literal `0`.** **⚠️ Do NOT mark-all-read on panel open. Dismissal must never alter product state. Do NOT add an authoritative `resolved` column.** **⚠️ Announcement targeting is EDITORIAL, NEVER authorization** — entitlement stays with `FeatureGateService`. **⚠️ NO external or protocol-relative CTA URLs.** **⚠️ NO push/email/SMS/quiet hours/preferences centre; NO announcement funnels or CTR dashboards; NO fifth mobile bottom tab; do NOT build on `analytics_events`** (telemetry by declaration). **⚠️ No quota, entitlement or pricing change; onboarding untouched.** **VERIFICATION: ONE SCOPED COLD AGENT** — a new cross-user delivery surface whose only duplicate-prevention is a single unique index. **⚠️⚠️ EVERY NEW ENDPOINT OWES ONE TEST THAT ISSUES A REAL REQUEST — `MockMvc` with `.contentType(MediaType.APPLICATION_JSON)` and a body.** `v0.119.0` is the measured precedent: both of that feature's JSON POSTs sent no `Content-Type`, Spring rejected every request **before the controller was entered**, and it could not make one successful request while 2,182 tests passed. **A direct controller-method call is NOT a substitute — it bypasses content negotiation and passes under the defect by construction.** **⚠️ PRE-DECLARED GUARDS: (6)** the same event twice leaves **exactly one row — assert the DATABASE**; **(7)** an edit to a PUBLISHED announcement is refused and delivered rows are unchanged; **(8)** one unread Announcement with zero actionable renders **no numeric badge at all**; **(9)** accepting a request through the normal flow leaves the notification row untouched; **(10)** a page of N notifications issues a **bounded** number of queries — **assert the query count, not the rendered rows.** **⚠️ CARRIED LESSONS: confirm a mutation is PRESENT before trusting a green suite** (twice-burned), and **anchor every quoted defect to the CURRENT file at kickoff** — `v0.128.0` opened on an item that had already shipped nine days earlier. **Routing: CODEX** — multi-system, two migrations. **⚠️ Call `advisor()` BEFORE writing the prompt.** Full scope in `RELEASES.md`.

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
- `docs/curriculum/` — **read this BEFORE any work on a Review Set curriculum** (building a new board-exam review set, reshaping an existing one, or planning what to author into one). There is an established pipeline and it should not be re-invented. **Step 1 — run `docs/curriculum/review-set-reshape-read.sql` against production** (read-only, six queries): Q0 identifies the review sets, Q1/Q2 give the target set's current shape, Q3 gives a comprehensive set as the benchmark, **Q4 lists notes already tagged for the program but NOT yet in the set — the ready-to-add pool**, Q5 maps the overlap, Q6 gives the exact catalog program names. **Its results ARE the strategist's input** — that is the list of existing notes to hand over. **Step 2 — paste those results with `docs/gpt-contexts/REVIEW_SET_SHAPING_CONTEXT.md`**, the module that makes the strategist emit a machine-readable TSV. **Step 3 — save the TSV block verbatim and run `build_review_set_workbook.py`**, which turns it into the working `.xlsx` the curator builds from. **⚠️ Never hand-transcribe a strategist's proposal into rows** — that is the step the TSV exists to remove, and it is unreviewable once done. `review-set-workbook-spec.md` carries the sheet contract; edit the `.tsv` and regenerate, never the workbook.

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

# Run all tests (requires Docker: NativeQueryPostgresIntegrationTest starts a
# PostgreSQL 16 container, applies the real Flyway migrations and PREPAREs every
# native query. Opt out only with -Dnativequery.pg.skip=true, which leaves
# PostgreSQL SQL and the migration set unverified.)
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

**`main` IS ENFORCED BY A REPOSITORY RULESET — re-corrected 2026-09-06 at the `v0.120.0` kickoff, and the history is kept because this line has now been wrong in BOTH directions.** It originally read *"Direct pushes to `main` are blocked by a repository ruleset"*; that was corrected to *"protected BY CONVENTION … the enforcement claim was FALSE"* on 2026-09-02, after a direct push succeeded during the `v0.103.0` cycle. **A ruleset now exists and enforces this** — ruleset `17016892`, carrying `update`, `creation`, `deletion`, `non_fast_forward` and `pull_request` rules. **This was discovered because PR #1300 could not be merged at the `v0.120.0` kickoff**, blocked by its `require_extra_approval_for_unattributed_changes` parameter (`required_approving_review_count` is 0, so it is NOT a review requirement). **⚠️ READ THE RULESET VIA THE GITHUB API (`gh api repos/<owner>/<repo>/rules/branches/main`), NEVER BY TESTING A PUSH** — the old instruction to correct this line rather than delete it is hereby discharged, and the prohibition on probing survives it. **⚠️ THE RULE WAS ALWAYS BINDING AND STILL IS:** all changes — including docs-only changes — go on a feature or docs branch and merge via pull request. **Never commit or push directly to `main`.** **⚠️ AND A BLOCKED MERGE IS NOT CLAUDE'S TO FORCE: `--admin` bypasses a branch protection and is an OWNER action.** **⚠️ But do NOT infer from a block that bypassing is the path** — that inference was made and corrected at the `v0.120.0` kickoff. Report what the ruleset actually says, name the parameter that blocked it, and leave the merge path to the owner; for #1300 the block was `require_extra_approval_for_unattributed_changes` with `required_approving_review_count` at 0, so **attribution, not bypass, is the likely fix.** When a PR is blocked, say so and offer the alternatives — `v0.120.0` cut its release branch from the blocked branch so the commit rides in via the release PR, as `v0.111.0` did. The two release-management exceptions below (kickoff and signoff commit directly on `releases/vX.Y.Z`) are unaffected.

**Release-management commits go directly on the release branch.** Release **kickoff** (opening a version) and release **sign-off** (closing / marking a version Released) are committed straight to `releases/vX.Y.Z` — do **not** create a separate sub-branch or PR for them. Feature and fix work still goes on its own branch and is merged into `releases/vX.Y.Z` via PR. (`main` stays protected; only the release branch receives these direct release-management commits.)

**Small documentation-only fixes may also go directly on the release branch, no branch/PR.** Scope: doc files only (`.md`, no `.java`/`.ts`/`.tsx`/migration/config/test changes), typically touching one file, correcting something rather than introducing new scope (a version-drift correction, a stale tracker line, a doc/copy fix, adding a process note or rule). Branch+PR overhead isn't worth it at this size. Anything touching actual code — even a genuine one-line bug fix — still goes on its own branch and PR regardless of size, since code changes should go through the normal review/CI path. Still wait for an explicit "commit it" before committing, per the rule above — this only changes *where* the commit lands, not whether to ask first.

Always update `RELEASES.md` with a bullet under the current version section when shipping any change.

When closing a release (marking it Released), commit the closure directly on the `releases/vX.Y.Z` branch (no separate branch/PR), and write a release notes file to `docs/releases/v{X.Y.Z}.md` using the Write tool. Follow the structure of existing files there: `# Release Notes: vX.Y.Z — Theme`, `## Release Theme` (one-sentence), `## Key Features` (bold emoji-prefixed titles with bullet points), `## Polish & Fixes` (flat bullet list). Do not output release notes as plain conversation text.

**Before closing a release, decide the right depth of pre-signoff pressure test — do not default to the heaviest option every time.** Per-PR `/audit-diff` is diff-scoped: it cannot see (a) pre-existing code a PR didn't touch that a new feature increases exposure to, or (b) an invariant interaction between two features shipped in *different* PRs that both touch the same shared method. Both classes of bug only surface via a whole-release view, but that view is expensive, so gate it on release shape:
- **Full pressure test** (three cold agents on non-overlapping halves, synthesized via `advisor`) ONLY for a permission substrate, a first-of-kind cross-user read, or a change to money/quota/production-data semantics. Realistically **one release in four or five.** `v0.93.0` qualified and cost **~490k tokens across three agents**; budget for that before choosing it.
- **One scoped cold agent** when any single trigger fires: the release moves an authorization or privacy boundary; two or more PRs touched the same shared method; **delivery introduced a defect the same session then fixed** (a measured blind-spot signal); or money, quota or production-data semantics changed. **Frame it as FALSIFICATION, not open-ended audit** — hand it a tight file list and the specific claims the implementing session made, and ask it to disprove each. That is far cheaper and targets the one thing cold agents are uniquely good at. `model: "sonnet"` is enough for claim-checking; reserve Opus for genuine unknown-unknowns.
- **Otherwise**, a single `advisor()` call summarizing what shipped is enough — cheaper, and still catches anti-drift violations.

**⚠️ EVERY TIER ABOVE READS CODE. NONE OF THEM EXERCISES TRANSPORT, AND A HIGHER TIER DOES NOT CLOSE THAT GAP — measured in `v0.119.0`.** That release ran the FULL three-agent test in isolated worktrees, which found six confirmed defects including two that would have hit the owner first — and it could not see that **both of the feature's JSON POSTs sent no `Content-Type`**, so Spring rejected every request with `HttpMediaTypeNotSupportedException` **before the controller was entered**. The feature could not make one successful request while **2,182 frontend tests passed**. Three things hid it, and each is general: the component tests **mock `lib/api` wholesale**, so nothing executed header construction; the controller tests called the handler **as a method**, which bypasses content negotiation entirely; and all three agents were scoped by feature area, so the frontend/backend seam fell in the gap between their partitions. **The owner found it on first use.**

**So a new endpoint owes ONE test that issues a REAL REQUEST, at every tier including the cheapest.** In this repo that is `MockMvc` with `.contentType(MediaType.APPLICATION_JSON)` and a body — the pattern already exists in `NoteControllerTest`, and a direct method call is **not** a substitute: it passes under the defect by construction. On the client side, `lib/api-*.test.ts` (nine existing files) pins the request shape; a component test that mocks the API layer proves nothing about it. **⚠️ When writing a pressure-test prompt, add the instruction that catches this class: *enumerate every file the release ADDED and name those with no test that executes them.*** `v0.119.0`'s blind-spot agent already had that heuristic and applied it correctly to the one file it was pointed at — the scoping, not the method, is what missed.

**⚠️ The cheapest checks are the earliest ones, and this is where the yield actually is.** Measured over `v0.93.0`: `advisor()` **before the Codex prompt was written** caught a Flyway/`application.yaml` collision that would have run 125 migrations against H2 and broken ~1,750 tests, a scope contradiction that would have shipped a *View progress* link that 404s, and an ambiguous row-count branch — each for a few thousand tokens, and each preventing work rather than finding it afterwards. **Call `advisor()` before writing any Codex prompt, and again on the diff before commit.** Skipping that checkpoint, not lacking a stronger option, is the failure mode.

**⚠️ BEFORE ANY DEEPER CHECK, ASK WHETHER A TEST EXERCISES THE PATH THE CHANGE TOUCHED — it is free, it is visible in `git diff --stat`, and it would have caught the last two silent no-ops on its own.** `v0.116.0` widened a curator predicate that could never fire, and `v0.117.0` added a callback to a component branch its only consumer never renders. **Both compiled, both passed the entire suite, both were written up as shipped — one with a named user-visible consequence that did not exist — and both were found only by a cold agent.** In each case the tell was the same and cost nothing: **the diff changed a behaviour while touching no test that runs it.** `v0.117.0`'s items 3 and 4 shipped with **zero** changes to a 1020-line builder suite that already covered the neighbouring control. **⚠️ "The suite is green" is evidence only about paths the suite executes**, so a green run on an unexercised change is not weak evidence, it is none. When a diff touches behaviour and no test file beside it moves, either write the guard or say plainly in the release that the item is unverified — the one thing that must not happen is recording it as shipped. **⚠️ AND THE GUARD MUST REACH ITS SUBJECT THE WAY PRODUCTION DOES:** both no-ops had a companion failure where a fixture hand-built a state no code path can produce, which is the same lesson from the other end.

**⚠️ Convert every recurring finding into an automated guard, because a guard is the only check that costs nothing to re-run.** `v0.93.0`'s PostgreSQL harness turned "a human must remember to check native SQL" into "the build fails," and found two live production defects on its first run. **But note what it did NOT do:** `PREPARE` validates syntax, types and `ON CONFLICT` arbiter resolution — never predicate correctness. A cold agent mutated `'ACCEPTED'` → `'PENDING'` in that same release's headline conditional insert, deleted its relationship-id predicate, and **all 1,760 tests passed** because every repository reference in the test tree was a Mockito mock. A guard is only worth what it actually executes; prefer a test that runs the statement against real data over one that merely parses it.

**⚠️ Release SIZE is the biggest lever on verification cost, and it compounds.** Verification scales worse than linearly with items shipped: `v0.92.0` carried eleven items, `v0.93.0` nine across three PRs, and it was the folding that forced the heaviest option. **At three or four items per release the gate above resolves to one `advisor()` call**, each `/audit-diff` stays sharp because the diff is small, and the automated guards carry the rest. When asked to fold more items, say what it does to the verification tier.
- Fix or explicitly document (in `RELEASES.md`, as a "Known limitations" note) every finding before signing off — never silently drop a finding.
- **When the full pressure test runs, its agents must start cold.** Spawn them with the Agent tool, no inherited context, and instruct them explicitly to read the real code rather than trust any summary — including summaries written by the session spawning them. In `v0.74.0` the two most severe defects were in code that session had written *and* reviewed, and one was actively protected by a test asserting the wrong behaviour; a reviewer carrying that session's context inherits its blind spots along with its knowledge.

**Also before closing: re-read every touched feature doc against the FINAL code state, not against the PR that last edited it.** This is a distinct step from "update `docs/features/<feature>.md` when shipping a behavioral change" — that rule is per-PR, and per-PR is exactly where it fails. A later PR in the same release changes the behaviour again and updates *some* docs but not others; each PR looks correct in isolation, and the drift is only visible once everything has landed. **`v0.74.0` accumulated seven such contradictions in one release** — `quick-review.md` claimed Quick Review does not write `ConceptHealth` (false for a month, and the claim the release's own justification rested on), four more lines in the same file, a `ui-standards.md` violation, and two statements in `quiz.md` that only a cold-context agent found. Cheapest reliable form: for each behavioural claim in a touched feature doc, locate the code that implements it and confirm the claim still describes it; a claim you cannot anchor to code is either stale or was never true.

**When a release changes what a user-facing claim MEANS, sweep by SURFACE, not by diff.** The re-read rule above says to check every *touched* feature doc. That is necessary and not sufficient, and the gap has now cost three releases in a row. `v0.93.0` made an `ACCEPTED` relationship stop implying progress access; `frontend/lib/linked-learner-status.ts` still told supporters progress "becomes available once the connection finishes activating" — it shipped false through two releases, because a grep for `"ACCEPTED"` across `app/` and `components/` never reached a file holding prose rather than status literals. `v0.94.0` then removed streaks from the progress payload, and `supported-learners-card.tsx` and `learning-connections-guide.tsx` both went on promising them — **the guide was not in that release's diff at all, and has never once been in a diff when the behaviour it describes changed.** Each time a cold agent found it by reading the surface. Cheapest reliable form: when a release changes a permission, a scope, or what a state means, list every place that *describes* it — help content, empty states, status/copy modules, dashboard cards, marketing — and check those, whether or not they appear in `git diff`. A file that explains a feature is exactly the file that never changes when the feature does.

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
3. **REPLACE** — not bump — the `Current version:` block in `CLAUDE.md` with the new release's. **⚠️ REPLACE, NOT PREPEND: that line reached 322,329 characters (63 chained `Previous:` blocks, ~80,000 tokens, ~90% of the file) because every kickoff prepended and none removed.** Keep the product description above it and the anti-drift of the release you are opening; `RELEASES.md` is the canonical history and the old chain is at `docs/archive/CLAUDE_ARCHIVE.md`.
4. Bump `version` in `frontend/package.json` and `backend/pom.xml` to match the new version number.
5. Update documentation baseline and version reference in `AGENTS.md`.
6. Update release baseline line in `README.md`.
7. Write release notes to `docs/releases/v{X.Y.Z}.md` for the version just closed.
7a. **Archive check — `RELEASES.md` must hold no more than the current version plus the last five.** If it does, MOVE the older sections to `docs/archive/RELEASES_ARCHIVE.md` with a one-line index entry each, and do the same for `ROADMAP.md`'s `## Current Release Baseline` once it exceeds the current plus last five. **⚠️ A MOVE, NOT A DELETE — verify live + archive together still hold every section byte-for-byte.** **⚠️ THIS STEP IS DUPLICATED IN `/signoff` ON PURPOSE: it previously existed ONLY inside the `Current version:` block, which the next kickoff replaces — a self-deleting instruction.**
8. Scan `ROADMAP.md`'s Backlog Index: bump `Last reviewed`, check whether any `Gate` condition became true, and verify every `docs/claude-prompt/*-out/` planning directory, **every `docs/claude-plans/` file AND every `docs/claude-findings/` file** still has a row — this is the only enforced checkpoint against a large planning effort (a multi-document Fable session, a paused exploration) silently going unindexed across release cycles. The `docs/claude-plans/` half was added 2026-08-10 after the `v0.71.1` kickoff found five files there unindexed; the Backlog Index intro carries the narrow *release artifact* exemption that covers finished one-off consultation prompts and sizing queries. Check all three directories — the invariant always said "or session-plan file," but the checklist only ever named some of them. **⚠️ `docs/claude-findings/` was added 2026-09-04 at the `v0.112.0` kickoff, after the scan found a production outage file dated that day AND a second incident file that had gone unindexed through FOUR consecutive kickoff scans — a scan performed exactly as written could not have caught either, because the step did not name the directory they live in.**
9. Scan the Backlog Index for `[CHECKPOINT — due YYYY-MM-DD]` rows past their due date (see "Gate types" in the Backlog Index intro) — a checkpoint that's overdue and unactioned is the same silent-drift risk step 8 guards against, just for vision-driven work shipped ahead of evidence instead of unindexed planning docs.
