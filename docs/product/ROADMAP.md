# ROADMAP.md - NoteLib

Rebrand note: StudySnap has been rebranded to NoteLib while preserving current database schema naming unless explicitly changed.

Goal: evolve NoteLib from a one-shot generator into a reusable note-first study workspace.

## Current Release Baseline

`v0.40.0 - Weekly Study Plan (Exam Countdown) + Primary Review Set` is the in-progress version (on `releases/v0.40.0`).

`v0.39.2 - Public Library Learning Experience` is the previous released version (on `releases/v0.39.2`).

`v0.39.0 - Flexible Review Methods` is the previous released version (on `releases/v0.39.0`).

`v0.38.0 - Read-Path Optimization Pass` is the previous released version (on `releases/v0.38.0`).

`v0.37.4 - Idle GC & Metaspace Ceiling Hotfix` is the previous released version (on `releases/v0.37.4`).

`v0.37.3 - Study Plan Read-Path Memory Optimization` is the previous released version (on `releases/v0.37.3`).

`v0.37.2 - Plan Data Integrity Hotfix` is the previous released version (on `releases/v0.37.2`).

`v0.37.1 - Native Memory Hotfix` is the previous released version (on `releases/v0.37.1`).

`v0.37.0 - Readiness-First Plans & Mastery Integrity` is the previous released version (on `releases/v0.37.0`).

`v0.36.3 - OCR Fast-Follow: Messaging & Feedback` is the previous released version (on `releases/v0.36.3`).

`v0.36.2 - OCR Disable Hotfix` is the previous released version (on `releases/v0.36.2`).

`v0.36.1 - Post-Release Fixes` is the previous released version (on `releases/v0.36.1`).

`v0.36.0 - Readiness/Progress Merge` is the previous released version (on `releases/v0.36.0`).

`v0.35.0 - Mobile-First Builder` is the previous released version (on `releases/v0.35.0`).

`v0.34.0 - Journey: Goal-First Study Experience` is the previous released version (on `releases/v0.34.0`).

`v0.33.4 - Builder Surface Clarity` is the previous released version (on `releases/v0.33.4`).

`v0.33.3 - Recursive Goal Adopt` is the previous released version (on `releases/v0.33.3`).

`v0.33.2 - Plan Detail Redesign (view/edit split)` is the previous released version (on `releases/v0.33.2`).

`v0.33.1 - Study Plan polish & Curated Plan Coverage` is the previous released version (on `releases/v0.33.1`).

`v0.33.0 - Study Plans as a Retention Engine` is the previous baseline (on `releases/v0.33.0`).

`v0.32.2 - Conversion Diagnosis & Quota Honesty` is the previous baseline.

`v0.32.1 - Monetization Surfacing & Pricing Clarity` is the release before that.

`v0.32.0 - Account & Communication Controls` is the release before that.

`v0.31.2 - Analytics Integrity & Funnel Visibility` is the release before that.

`v0.31.1 - Adoptable Study Plans Discovery & Status` is the release before that.

`v0.29.1 - Bulk Generation Polish` is the release before that.

`v0.29.0 - Bulk Generation & Generation-Context Correctness` is the release before that.

`v0.28.0 - Feature Discoverability & Activation` is the release before that.

Older milestone labels below are preserved as planning history only. They are not the current in-progress release.

---

## v0.35.0 - Mobile-First Builder (released)

Base branch: `releases/v0.35.0`.

Theme: the leaf plan builder is unusable on mobile — text-only Up/Down/Move/Remove controls are cramped on narrow viewports, and sections produce a multi-thousand-pixel scroll. This release makes the builder compact and functional on all screen sizes, and fixes a section drag visual bug where only the section header travels instead of the whole group.

Scope:
- **Section drag overlay fix (frontend).** `DragOverlay` for `leaf-section` drag renders a proper section clone (header + note list), not a title pill.
- **Mobile icon buttons (frontend).** Up/Down/Move/Remove become icon-only on mobile (`sm:` and up retains text labels). `aria-label` on all icon buttons.
- **Mobile collapsible sections (frontend).** Sections collapse by default on mobile to reduce scroll. Tap header to expand. Desktop always-expanded.

Anti-drift: frontend-only; no new endpoint, migration, or AI call; drag handles retained; Up/Down retained alongside drag for touch reorder.

---

## v0.36.0 - Readiness/Progress Merge (released)

Base branch: `releases/v0.36.0`.

Theme: unify "Progress" and "Readiness" into a single, coherent surface. Today the product has a standalone Progress page (`/me/progress`) and a plan Readiness sub-route (`/collections/{id}/readiness`) that express the same underlying signal with different vocabularies and entry points — confusing to users and inconsistent in value proposition. v0.36.0 merges these into one canonical surface.

Scope (to be refined at kickoff):
- **Unified vocabulary.** One set of labels (`ready / mastered / due / not started`) used everywhere — note detail, plan header, plan readiness, progress dashboard.
- **Progress page consolidation.** Merge the standalone `/progress` readiness dashboard with the plan-level readiness sub-route. Single canonical entry point with cross-plan and plan-scoped views via an in-page picker.
- **Navigation continuity.** Keep the nav label as "Progress" and keep the page header as "My Progress" while the surface gains plan-scoped readiness.
- **No new mastery signal.** Still derived from existing `ConceptHealth` / `ProgressReportService` — no new field, AI call, or stored signal.

Anti-drift: Free gate stays as-is (signal free, timing detail PLUS/PRO); no billing/quota/price/checkout change; no new chart library; all derived from existing concept health data.

---

## v0.36.3 - OCR Fast-Follow: Messaging & Feedback (released)

Base branch: `releases/v0.36.3`.

Theme: complete the OCR-disable story from v0.36.2 — fix a swallowed error message, give all three OCR-gated flows a consistent "temporarily unavailable" state, and add a lightweight feedback ask (do users want OCR back?).

Scope:
- **Fix swallowed error message.** `note-editor-page-client.tsx`'s upload handler only recognizes two hardcoded messages; anything else (including `OCR_DISABLED`) collapses into a generic failure. Add `isOcrDisabledError` and a dedicated branch.
- **Consistent disabled-state UI across 3 touchpoints.** New Note/Edit Note upload, bulk import, and photo-to-Study-Pack quick capture each show the same distinguishable "OCR temporarily unavailable" state.
- **Feedback capture.** A "Yes, I'd like this back" affordance firing a new `AnalyticsEventType` value — no new endpoint or table.

Anti-drift: no change to the v0.36.2 kill-switch itself; only additive analytics enum value(s) on the backend.

---

## v0.36.2 - OCR Disable Hotfix (released)

Base branch: `releases/v0.36.2`.

Theme: production incident response. The backend was repeatedly OOM-killed on Render's 512MB Starter instance. Root-caused to Google Vision OCR: a fresh gRPC `ImageAnnotatorClient` is constructed per call, and the scanned-PDF fallback calls it once per page (up to 30x per import) — native/off-heap memory churns faster than it's reclaimed, and the Dockerfile's JVM flags leave no native-memory headroom, so the container's cgroup SIGKILLs the process before the JVM can log an OutOfMemoryError. Pre-revenue, so disabling OCR (a paid Google API) also cuts real cost.

Scope:
- **Kill-switch (backend).** Disable all three Google Vision OCR call sites (`NoteTextExtractionService.extractFromImage`, `NoteTextExtractionService.extractFromPdfViaOcr`, `StudyPackService.createFromImage`) behind one env-configurable flag. Native-text PDF extraction and `.txt` upload are unaffected.
- **Typed failure state (backend).** Callers hitting a disabled OCR path get a distinct, typed error instead of a raw failure.
- **Fast-follow (not in this hotfix):** a Codex-built polished experience — clear "OCR temporarily unavailable" messaging in the upload flow plus a feedback-capture mechanism (ask users if they want OCR back).

Anti-drift: kill-switch only, no redesign; no new endpoint, entity, or billing/quota change beyond gating the OCR call.

---

## v0.36.1 - Post-Release Fixes (released)

Base branch: `releases/v0.36.1`.

Theme: fast-follow patch fixing three issues found in a post-signoff audit of v0.36.0 — a reachable dead-end in the Builder's Goal-creation entry point for already-nested Subject plans, "due for review" vocabulary drift on two dashboard entry points into `/progress`, stale `AGENTS.md` documentation left over from the readiness-route merge — plus a second round of UX fixes surfaced while using v0.36.0 in practice.

Scope:
- **Nested Subject plan dead-end fix.** Gate the Builder's `Add Subject Plan` control on `collection.parentCollectionId === null` in addition to the existing `leafItems.length === 0` check.
- **Vocabulary fix.** `dashboard-goal-card.tsx` and `goal-nudge-card.tsx`: `due for review` → `due`. `% mastered` on these cards is unchanged (correct per the locked vocabulary set and consistent with `/progress`'s own goal-level header).
- **Documentation accuracy.** Fix `AGENTS.md`'s stale "Study Plan Readiness Rule" `PLAN_READINESS_VIEWED` wording and stale documentation baseline line.
- **Subject plan BackLink.** Nested Subject plan detail pages now back-link to their parent Goal instead of the flat `/collections` list.
- **Builder responsiveness.** Subject-block row breakpoint aligned so it no longer squeezes at intermediate viewport widths where the persistent sidebar reduces available content width.
- **Refresh relocated.** Moved from the Builder page header into the Add-notes modal, where its actual purpose (pulling in newly created notes) applies; header keeps a single primary action (`Add Subject Plan`).
- **Plan Hero layout reuse.** Goal detail header now reuses the leaf-plan `PlanHeroCard` (badges-above-title) instead of a separate `PageHeader`-based layout.
- **Note Detail readiness repositioned.** Moved from above all tab content to just before Performance Overview (still shown on all tabs).
- **Builder action-row wrapping on tablet widths.** The breakpoint fix alone didn't fix real device screenshots (iPad Air vs. iPad Mini) showing an orphaned single-button wrap. Reorder controls (`Move up`/`Move down`, `Up`/`Down`) moved into a compact icon cluster next to the drag handle; action rows fixed at two items so they never wrap at any width.

Anti-drift: bug-fix/UX-polish patch only; no new endpoint, field, or mastery signal; scope is the nine items above only.

---

## v0.38.0 - Read-Path Optimization Pass (released)

Base branch: `releases/v0.38.0`.

Theme: extend the v0.37.3 projection discipline across the remaining hot read/list endpoints. Explicitly a latency/efficiency pass, not a memory-idle fix — the idle-footprint problem was resolved by upgrading the Render instance to 2GB. An audit of the six highest-traffic areas (note collections, private/public library, progress, note detail, quizzes) found several paths still materializing full entities (with the large `quiz`/`source_text`/`questions`/`content`/`sessionState` columns) where only a handful of fields are needed.

### Planned Scope

- **Session-history read path (backend, P1).** `QuizSessionHistoryService.findCompletedSessions` loads every completed session (full `sessionState` JSONB, no LIMIT) then filters in memory — and backs three hot paths (collections list, collection detail, quiz recent-sessions). Push mode/note filtering to the DB; stop loading `sessionState` for single-note modes.
- **Collection detail + public detail projections (backend, P2).** `toItemResponses`/`toPublicItemResponses` still load full `StudyPackEntity` + full `GeneratedQuizEntity` (only the id is used) + full `NoteEntity` (content unused). Route through `findProgressViewsByNoteIdIn` + lean note/generated-quiz projections.
- **Private library list projection (backend, P3).** `StudyPackService.listMine` loads the full `quiz` JSONB just to call `.size()` and never reads `source_text`. Projection with `jsonb_array_length(quiz)`.
- **Goal per-child readiness batch (backend, P4).** Batch `getGoal`'s per-child `getReadiness` N+1 (deferred from v0.37.3). Sequenced last; may slip to a follow-up.

Anti-drift: no schema, endpoint, or DTO change — byte-identical responses. Every new interface projection requires a real-Hibernate test (the v0.37.3 projection-detection footgun). Note detail (`getById`), generated-quiz `getByNoteId`, and the already-optimized progress report are out of scope — they legitimately need full payloads. Sequencing driven by `http.server.requests` p95 data, not inferred frequency.

### Unplanned follow-ups (backlog, not scheduled)

- **Persist `study_packs.question_count` (denormalize the quiz length).** P3 ships the library-list quiz count via a Postgres-native `jsonb_array_length(quiz)` query, which (a) still forces Postgres to read/parse the TOAST'd `quiz` JSONB per row and (b) can't be exercised by the H2 test harness (documented gap). A persisted `int question_count` column would eliminate the residual server-side TOAST read, make the whole read path pure-JPQL (fully H2-testable), and become indexable/reusable (sort/filter/analytics by length). Cost is a migration + backfill + write-path sync on every quiz mutation (generation, regeneration, Challenge Quiz progressive add) — a new drift/bug surface — so it only earns its keep if the library list shows up in slow-query logs, or question count is needed in more than one place. Revisit then; not worth the write-path maintenance at current scale.

---

## v0.37.4 - Idle GC & Metaspace Ceiling Hotfix (released)

Base branch: `releases/v0.37.4`.

Theme: continuation of the production memory-restart investigation (v0.37.1). Live actuator sampling showed container RSS plateauing near 95-97% within minutes of boot, before the bulk of request traffic even arrived, while JVM-tracked heap (committed ~207MB vs used ~99MB) and nonheap (~171MB, fully reconciled across Metaspace/Compressed Class Space/CodeHeaps) together only accounted for ~394MB of the 512MB limit — leaving a ~93MB gap the JVM can't see. Two concrete findings drove this round: G1 wasn't returning idle-committed-but-unused heap to the OS, and `MaxMetaspaceSize` was unset entirely (`jvm.memory.max` reported `-1`).

### Planned Scope

- **Force G1 to release idle-committed heap (Dockerfile).** `G1PeriodicGCInterval=30000` + `G1PeriodicGCInvokesConcurrent` — a regular, concurrent (non-pausing) opportunity to uncommit unused heap. This is the actual fix candidate; the other two flags are safety/instrumentation.
- **Cap Metaspace (Dockerfile).** `MaxMetaspaceSize=200m` — a tripwire, not a remediation. Converts any future runaway into a logged `OutOfMemoryError: Metaspace` instead of a silent SIGKILL.
- **Enable Native Memory Tracking (Dockerfile).** `NativeMemoryTracking=summary` — cheap now, avoids a second redeploy later if `jcmd <pid> VM.native_memory summary` is needed to break down the native gap.

Anti-drift: no Java code change, no schema/endpoint/DTO change — Dockerfile `JAVA_OPTS` env var only. Framed explicitly as an experiment: if the post-deploy RSS plateau drops, the gap was reclaimable heap slack; if it stays pinned near 95%, the gap is native (thread stacks, GC bookkeeping, or Netty/gRPC off-heap from `GoogleVisionOcrService`'s per-call `ImageAnnotatorClient` creation), and that becomes the next thread to pull.

---

## v0.37.3 - Study Plan Read-Path Memory Optimization (released)

Base branch: `releases/v0.37.3`.

Theme: cut heap allocation on Study Plan read endpoints — the recurring production memory-restart investigation traced part of the climb to `getReadiness`/`getNoteConceptCounts`/`getGoal` fully materializing `StudyPackEntity` (including its two largest columns, `quiz` and `source_text` jsonb) just to read `keyConcepts`, plus a per-study-pack N+1 on concept health. Full brief: `docs/codex-prompts/v0.37.3-studyplan-readpath-memory.md`.

### Planned Scope

- **Projection query for the read path (backend).** Lightweight projection for `StudyPackRepository.findByNoteIdIn(...)` exposing only `id, noteId, ownerUserId, subject, keyConcepts, status`; route the readiness/concept-count/goal read paths through it instead of the full entity.
- **Batch the concept-health N+1 (backend).** Replace the per-pack loop in `ProgressReportService.collectReviewTimesByConceptKey` with the existing batched `findByUserIdAndStudyPackIdIn`.
- **Count query instead of count-by-load (backend).** `NoteCollectionService.getGoal`'s item count currently loads and sizes a list; replace with a `count(...)` query.

Anti-drift: no schema, endpoint, or DTO change — API responses stay byte-identical. `getGoal`'s per-child readiness fan-out restructure is deferred (P1 already makes each fan-out call cheap, so restructuring it is a latency concern, not a memory one).

Audit finding: the delivered projection initially returned `StudyPackProgressView` directly, which `StudyPackEntity` (the managed domain type) also implements — a combination that silently breaks Spring Data's projection detection and 500s at call time, invisible to Mockito-mocked unit tests. Fixed with a marker sub-interface (`StudyPackProgressProjection`) used only as the query return type, plus a new real-Hibernate repository test to catch this class of bug going forward. See `RELEASES.md` for full detail.

---

## v0.37.2 - Plan Data Integrity Hotfix (released)

Base branch: `releases/v0.37.2`.

Theme: two Study Plan correctness bugs found in production, shipped as a hotfix ahead of the v0.38.0 read-path memory optimization.

### Planned Scope

- **Fix partial-update clobber on collection metadata (backend).** `NoteCollectionService.updateMetadata` overwrote `description`, `courseProgram`, and `estimatedStudyHours` unconditionally while guarding only `title`; the Goal Builder's rename (a `{ title }`-only PATCH) therefore wiped those fields silently. Guard each optional field with a null check (PATCH semantics); an explicit empty string still clears a field. Audit sibling PATCH endpoints for the same shape.
- **Plan visibility / course-program filtering — investigated, no defect found.** A FREE account reported seeing plans outside its own course/program. Traced the full read path: `list` is strictly owner-scoped, `listPublic` only ever returns PUBLIC collections, and adoption always stamps `ownerUserId = copier`. Confirmed not a cross-user leak or scoping bug; no change shipped.

Anti-drift: no schema change, no new endpoint — read/write correctness only. Study Plan read-path memory optimization is deferred to v0.37.3.

---

## v0.37.1 - Native Memory Hotfix (released)

Base branch: `releases/v0.37.1`.

Theme: production incident response, same shape as v0.36.2 but a different root cause. Repeated silent restarts on the 512MB Render Starter instance with no JVM-level `OutOfMemoryError` ever logged — meaning the growth is native/off-heap memory outside JVM heap accounting entirely, not the OCR gRPC-client leak from before (already ruled out: that client is now properly closed via try-with-resources, and the triggering request was topic-based bulk note+Study Pack generation, which never touches OCR). Leading hypothesis: `eclipse-temurin:21-jre` is glibc-based (Debian), and glibc's per-thread malloc arenas fragment and are never returned to the OS — a well-documented cause of exactly this symptom (slow all-day RSS climb, silent SIGKILL, zero JVM OOM log) on containerized JVMs running multiple thread pools (Tomcat, `studyPackGenerationTaskExecutor`, `llmParallelTaskExecutor`).

### Planned Scope

- **Cap glibc malloc arenas (Dockerfile).** `MALLOC_ARENA_MAX=2` — zero code change, fully reversible via env var, standard mitigation for this exact JVM-in-small-container profile.
- **Expose actuator metrics (backend config).** `management.endpoints.web.exposure.include: health,metrics` — `jvm.memory.used`/`max` tagged heap vs. nonheap, GC pause, thread counts, reachable at `GET /actuator/metrics/{name}`. Stays authenticated-only (not `permitAll`), so this doesn't newly expose anything publicly. Purpose: next time, diagnose heap-vs-native from live metrics instead of inferring it from whether an OOM log line exists.

Anti-drift: no Java code change, no new dependency — Dockerfile env var + actuator exposure config only.

---

## v0.37.0 - Readiness-First Plans & Mastery Integrity (released)

Base branch: `releases/v0.37.0`.

Theme: make plan-level readiness the headline of the (leaf) Study Plan page instead of a click-through, and protect the mastery signal the readiness surface depends on so it can't be satisfied for free by unlimited Quick Review grinding. Origin: user email-campaign feedback plus a reflection on the shipped Study Plan work. A broader pitch (flexible review methods reusing a Study Pack, disposable Quick Review sessions) surfaced alongside this — the parts that survived a pressure test are in scope below; the rest is explicitly deferred (see bottom of this section) rather than folded in.

**Monetization context:** at the time of this planning, there are **zero paying Pro users** and a **0% free-quota-hit rate**; v0.32.1/v0.32.2 already diagnosed the conversion problem as *surfacing* — premium exams (Board Exam, Long Exam, Difficulty selection) aren't reaching users, not that Free is too generous. That said, one concrete free-generosity mechanism was real before v0.37.0: **Quick Review is unlimited on Free** (`subscriptions-and-usage-limits.md` — only Challenge Quiz is quota-capped) and wrote to `ConceptHealth` exactly like every paid mode, so a free user could grind unlimited Quick Review to 100% "Overall Readiness" without touching a quota-gated or Pro-gated mode. Putting readiness front and center on the plan page (this release) made that path more visible, so this release closes it (Quick Review scope below) and adds a direct upsell moment on the readiness surface itself.

### Scope

- **Inline `ReadinessSummary` on leaf plan detail**, reusing the existing `GET /collections/{id}/readiness` endpoint and `ProgressReportService` — no new field, endpoint, or mastery signal. Placed between `PlanHeroCard` and the existing execution-progress summary, matching the order Goal detail already uses (shipped v0.34.0–v0.36.1). The "notes practiced" execution bar stays as a distinct row alongside readiness; it is not replaced or merged into mastery.
- **"Review due concepts" entry point** on plan detail, next to `ReadinessSummary`, routing to the same next-step logic `PostSessionNextStep` already uses (no new routing model). Today acting on due/weak concepts means separately finding a note and starting Quick Review/Adaptive Practice from there.
- **Pro-gated CTA on the readiness surface**, extending the v0.32.1 "surface premium exams as paywall moments" pattern: pair the free readiness % with a paid, formal way to prove it (e.g. "Prove it under Board Exam conditions" / "Test full mastery with Long Exam"). **Must be profile-aware and driven by the existing `terminalAction` / `resolvePlanPremiumExamMode` mapping** — Student -> Long Exam, Board Taker -> Board Exam, Professional -> Interview Practice; never a blanket "Board Exam" CTA regardless of profile. No new plan tier, no new quota category — reuses existing premium-exam entitlement checks.
- **Quick Review stops writing to `ConceptHealth`.** Locked decision: Quick Review must never move mastery, due-state, or `Overall Readiness`. It keeps its own in-session feedback (immediate right/wrong, "you missed N concepts this round," retry-incorrect) since that's ephemeral/session-local, not `ConceptHealth`-based — **verify this assumption at implementation time**; if the retry-incorrect flow turns out to read from `ConceptHealth` rather than in-memory session results, it needs a non-`ConceptHealth` data path so in-session feedback keeps working. Backend change is a mode-based exclusion in the shared session-completion write path for the `QUICK_REVIEW` discriminator — not a new aggregate, table, or signal.
  - **No backfill/migration on existing data.** `ConceptHealthEntity` has no field recording which mode wrote a value (`(user_id, study_pack_id, concept) → last_correct_at, last_incorrect_at`, overwritten in place by whichever mode last touched it) — there is no way to isolate and delete just the rows a past Quick Review session influenced without also erasing legitimate mastery earned via Challenge Quiz/Adaptive Practice/Long Exam/Board Exam. Explicitly decided: let it decay naturally — any concept whose mastered status is currently held up only by a past Quick Review session will fall to "due" on its own once the due-threshold passes with nothing to refresh it, and from then on only real assessment activity can re-master it.
  - Docs to update as part of this change: `my-progress.md` ("ConceptHealth write sources" — remove Quick Review), `quick-review.md` ("ConceptHealth" section — rewrite), `dashboard-recommendation.md` (confirm no Focus Areas / next-step logic depends on Quick Review's ConceptHealth writes), `EXAM_MODES.md` (Quick Review identity boundary line).

Anti-drift: reuses existing `ConceptHealth` / `ProgressReportService` / `GET /collections/{id}/readiness` and existing premium-exam entitlement checks only. No new mastery signal, no new persisted field, no data migration/backfill, no billing/quota/price change.

### Deferred to a later release (not v0.37.0)

- **Flexible review methods over one Study Pack** (Flashcards, Identification, Enumeration, real spaced-repetition Memorization) — scoped as its own candidate release below (v0.38.0), not folded into v0.37.0.

Quick Review session disposability is resolved above (hide from session-history UI, keep the row — see "Shipped"/pending item). The broader review-vs-assessment taxonomy for future methods is resolved as part of the v0.38.0 scoping below, not left as a separate open question.

---

## v0.39.0 - Flexible Review Methods (released)

Base branch: `releases/v0.39.0`. Kicked off after v0.38.0 signoff (repo convention: signoff of the current version precedes kickoff of the next). Scoped here so the four sub-features don't drift before implementation.

Theme: let a Study Pack be reviewed through more than Multiple Choice — Flashcards and real spaced-repetition Memorization as review-only methods, Identification and Enumeration as new scored assessment formats — while keeping the review-vs-assessment mastery boundary this whole effort is built around.

**This is two independent dependency chains, not four parallel items:**

| Chain | Order | Why |
|---|---|---|
| A — Review methods | Flashcards → Memorization | Memorization is Flashcards plus spaced-repetition scheduling and self-grading on top; it cannot be built first. |
| B — Assessment formats | Identification → Enumeration | Enumeration reuses Identification's free-text-answer field, LLM prompt work, and validation infrastructure, plus its own harder partial-credit/order-independence logic on top. |

Chains A and B don't depend on each other — sequencing between them (e.g. Flashcards → Identification → Memorization → Enumeration, roughly increasing complexity) is a prioritization call to make at kickoff, not a hard technical constraint.

### Review-vs-assessment classification (locked, resolves the deferred taxonomy question)

| Method | Family | ConceptHealth on completion? | Fits the quiz-session engine? |
|---|---|---|---|
| Flashcards | Review | No — same rule as Quick Review, never moves mastery | No — no timer, no submit, no scoring; a new non-scored surface, not a mode |
| Memorization | Review | No — same rule as Quick Review | No — new non-scored surface; adds its own SRS scheduling state (see below), not ConceptHealth |
| Identification | Assessment | Yes — writes mastery like Challenge Quiz/Adaptive Practice | Yes — new question *format* on the existing engine, not a new mode |
| Enumeration | Assessment | Yes — same as Identification | Yes — new question *format* on the existing engine, not a new mode |

### Scope

- **Flashcards.** New non-scored frontend surface over Study Pack content. `keyConcepts` is a flat `List<String>` with no paired definition today — reuse `QuizItem.explanation` for the matching `concept` as the card's "back" where a quiz question exists for that concept; fall back to a "no definition yet" state where it doesn't, rather than triggering new AI generation. No `ConceptHealth` write on flip/self-assessment.
- **Memorization.** Flashcards' surface plus a real spaced-repetition schedule: graduating review intervals per concept (correct → interval grows; incorrect → interval resets), driven by a **new, separate entity** — not new `ConceptHealth` columns. This is a third concept-level signal alongside `ConceptHealth` (mastery, assessment-only) and Quick Review's ephemeral per-session data; **firewall it explicitly in docs and code** so a future change doesn't wire SRS scheduling into readiness/mastery and silently break the v0.37.0 mastery-integrity lock. Self-graded (e.g. "again/hard/good/easy"), not scored.
- **Identification.** New question format: fill-in-the-blank / name-the-term. Needs a new free-text answer field on `QuizItem` (today only `choices`/`correctIndex`/`correctIndices` exist), new LLM prompt work to generate checkable short answers, new answer-validation logic (exact vs. fuzzy match — decide at kickoff), and a new frontend text-input question component. Writes `ConceptHealth` on completion like Challenge Quiz.
- **Enumeration.** New question format: list N items in a category. Builds on Identification's free-text field/validation/prompt work; adds partial-credit and order-independent set-matching semantics (decide exact scoring rule at kickoff) and a multi-item entry UI. Writes `ConceptHealth` on completion like Challenge Quiz.
- **`EXAM_MODES.md` update required before any of this reaches a Codex prompt** — add Identification/Enumeration as new question formats on the existing engine and Flashcards/Memorization as new non-scored surfaces, explicitly *not* a 6th/7th mode, per the doc's own "update this document before adding a sixth mode" rule.

Anti-drift: Identification/Enumeration reuse the existing quiz-session engine and `ConceptHealth` write path (parameterized like every other assessment mode) — no new session discriminator. Flashcards/Memorization are new surfaces outside the quiz-session engine entirely — no attempt to force them through `QuickReviewSessionEntity`. Memorization's SRS state is a new, separate entity — never a `ConceptHealth` field, never counted toward mastery or `Overall Readiness`.

---

## v0.39.1 - Study Plan Builder Polish (released)

Base branch: `releases/v0.39.1`. Kicked off after v0.39.0 (Flexible Review Methods) signoff. Surfaced while using the Study Plan Builder in practice; two sub-themes, both Study Plan/note-collection polish rather than new architecture. The course/program cascade fix (Theme A) is a live correctness bug but was assessed as not urgent enough to warrant a standalone hotfix — it ships bundled in this batch.

### Theme A — Subject metadata completeness

Today, metadata set at the Goal level doesn't reliably reach its child Subject plans.

- **Description field on Add Subject Plan.** The Builder's `AddSubjectModal` only collects a title; `createCollection` already accepts `description` (the top-level "New Review Set" modal already uses it). Add the same textarea here and thread it through `handleAddSubject(title, description)`. Frontend-only, no backend change — separate component from `CreateCollectionModal` (the two diverge in behavior: optimistic re-parenting vs. navigate-after-create), so add the field directly rather than force a shared component.
- **Course/program does not cascade from parent to children.** Confirmed bug: `NoteCollectionService.publishChildCollections` cascades `visibility = PUBLIC` to children on publish but never touches `courseProgram`, and `updateMetadata` only ever updates the single collection it's called on. A dev-DB audit found zero existing parent/child pairs with intentionally-different `courseProgram` values, so a cascade is safe in practice — but given this codebase already shipped a real hotfix for `updateMetadata` silently clobbering fields (v0.37.2), implement this as "cascade to children only when their `courseProgram` is currently blank," not a blind overwrite. Belongs in `updateMetadata` (covers editing course/program after publish too, not just at the initial publish moment). **This is a correctness bug on already-published content** (blank child `courseProgram` breaks the course/program-scoped public discovery from v0.33.0) — candidate for a standalone hotfix ahead of this batch rather than waiting for v0.39.1, decide at kickoff.

### Theme B — Adoption model

- **Cold-start adoption discoverability (reframed from "should we auto-generate review sets").** Raised alongside a comparison to competitor apps that auto-generate review decks. Auto-generation was rejected: NoteLib's differentiator is that review material comes from the user's *own* notes, not pre-made content — adopting a curated plan is correctly positioned as the **cold-start on-ramp** for zero-notes users, not the core loop, and shouldn't be second-guessed on that basis. The sharper question worth scoping here is whether adoption is **discoverable and frictionless enough** for that cold-start moment — a UX/discovery audit, not a new generation feature.

### Parked (not scoped for this candidate)

- **Adopting a single child Subject plan standalone.** Backend technically permits it today (`adopt()` has no parent-child guard), so this is a pure UX-surfacing question, not a backend change. Not building it yet: the value it would add (grabbing one Subject without its parent Goal) is already served by copying individual public notes, and there's an unresolved interaction — `adopt()` never sets `parentCollectionId`, so a standalone-adopted child lands as a top-level orphan copy; if the user later adopts the parent Goal, `adoptGoal`'s idempotency check finds that existing copy by `sourcePlanId` and **silently re-parents it** into the newly-adopted Goal (no duplicate created, but a surprising structural change with no user confirmation). Revisit only if a real discovery need shows up — resolve the re-parenting UX before shipping.

---

## v0.39.2 - Public Library Learning Experience (released)

Base branch: `releases/v0.39.2`. Origin: a design discussion on how to surface Flexible Review Methods (Flashcards, Memorization — shipped v0.39.0) to anonymous visitors on public note detail, where they're entirely undiscoverable today. Theme: connect the Public Library discovery layer to the signed-in workspace's richer review methods, so anonymous visitors experience enough of the study system to want to continue, without weakening the discovery/workspace distinction.

Validated against an existing, documented precedent rather than proposed cold: `PublicMiniQuizPreview` already runs a capped (3-question), client-side-only, no-persistence quiz teaser on public notes today, and `docs/features/public-library.md`'s "Mini quiz preview rules" section explicitly sanctions this exact pattern ("up to 3 questions, interactive, client-side only, no account required") as the carved-out exception to "public note detail must not run an inline full quiz." This release applies the same sanctioned pattern to a second review method rather than inventing a new exception.

### Planned Scope

- **Flashcards Preview (frontend).** New `PublicFlashcardsPreview` component mirroring `PublicMiniQuizPreview`'s exact shape: capped at 3 cards (matching the existing `MAX_PREVIEW_QUESTIONS` convention, not an open-ended count), tap-to-reveal, fully client-side state, no backend calls, no persistence. Reuses `keyConcepts` + matching `QuizItem.explanation` pairs already present in the unauthenticated public note detail response — **zero backend change required**, since the full Study Pack content is already exposed in that response today (only the UI currently chooses to render a subset). Rendered directly after the existing Quick Check mini-quiz preview.
- **Memorization teaser (frontend).** Static, purely educational section (copy + timeline illustration) explaining spaced-repetition scheduling. No per-note content, no scheduling logic, and no state of any kind for anonymous users — messaging only, never a working preview. A dedicated "try it" experience was explicitly rejected: Memorization's whole value is scheduling that reveals itself over days/return visits, which a single anonymous session can't demo (one card flip looks identical to Flashcards).
- **Existing CTA hierarchy preserved.** The current single primary CTA (`Quiz yourself on this note`) plus secondary actions (`Create your own Study Pack`, `Copy to My Library`) are not restructured or renamed in this release. Flashcards/Memorization CTAs are additive secondary cards alongside the existing ones, not a replacement of the primary/secondary hierarchy — a full CTA-row redesign is a separate, deliberate decision, not a side effect of this feature.

Anti-drift: no backend changes; no new persisted state for anonymous users; no live SRS/scheduling logic exposed pre-signup; preview cap matches the existing `MAX_PREVIEW_QUESTIONS` precedent; does not touch the copy-on-signup cookie flow or the existing conversion CTA hierarchy.

---

## v0.40.0 - Weekly Study Plan (Exam Countdown) + Primary Review Set

Base branch for this release: `releases/v0.40.0` (kicked off). Origin: `docs/claude-prompt/auto-weekly-study-plan.txt` weighed directly against `docs/claude-prompt/cross-course-note-reusability.txt` as the next major release, then expanded by a design review that identified the **Primary Review Set** as the missing foundation the scheduler needs. Cross-course was ruled out — its own doc explicitly says "Do NOT implement this proposal today," and the trigger for reconsidering it (one curator's friction assigning a single IT plan across overlapping CS/COE programs) is a narrow curation-side pain, not the broad-audience "meaningful maintenance concern" the doc sets as its own revisit bar. It's also a real architecture change (note↔course/program is load-bearing for AI generation context, Public Library, search/filter, Study Plan org) with much higher blast radius than this release's read-time view over already-shipped data. Theme: turn readiness from a static number into an ongoing weekly cadence — the direct next chapter of the retention thesis validated since v0.33.0 (a number that only moves by returning), aimed squarely at the exam-taker conversion/monetization segment.

**Why Primary Review Set is in this release, not deferred:** the scheduler needs one unambiguous answer to "which Goal drives the Dashboard / Today / Weekly view?" when a learner owns several dated Goals (multi-Goal ownership is a locked design pillar — that's why the target date lives on the Goal, not the profile). No primary/active-collection concept exists anywhere today (confirmed by codebase audit); the Dashboard currently picks `publicPlans[0]` (top course/program match) implicitly. An **explicit** user-set primary (not "nearest exam date" — that alternative was considered and rejected for its failure modes: two close exams, wanting to focus on a later exam, non-dated goals) gives the scheduler, Dashboard, and Progress a single source of truth, and is the foundation the longer-term navigation vision (see the deferred "Review-Set-Centric Navigation" section below) builds on.

### Scope

- **Primary Review Set (new, foundational).** A new nullable user-level reference (e.g. `primaryCollectionId` on the user) the learner explicitly sets via a lightweight "Set as primary" action. **Profile-agnostic as a concept, but any surfaced label must resolve through the existing `getCollectionLabels` resolver** ("Primary Study Plan" / "Primary Review Set" / "Primary Lesson Plan" / "Primary Collection") — never a hardcoded "Review" string, per the locked anti-drift rule in `collections.md` / `CLAUDE.md`. Edge cases the concept must handle explicitly:
  - No primary set and zero owned top-level Goals → no primary UI; Dashboard keeps today's top-course-program-match fallback (no regression).
  - Exactly one owned top-level Goal → auto-set it as primary (don't force an extra step for the common case); user can still change it.
  - Primary deleted → reference clears, falls back to the "no primary" behavior above.
  - Primary set but no target date → the weekly countdown/scheduler card is hidden gracefully (same null-safe degradation as the target-date field itself); the primary still drives the Dashboard/Progress default view.
  - Profiles with no natural "exam" framing (TEACHER, PROFESSIONAL) → the primary still drives Dashboard/Progress default view; the countdown/scheduler is conditional on a target date being set on that primary, not on profile type.
- **Per-Goal target completion date (backend).** New nullable `note_collections.target_completion_date` column, settable only on top-level Goals (`parentCollectionId == null`) — mirrors the existing `courseProgram`/`estimatedStudyHours` PATCH-semantics pattern in `updateMetadata` (omit preserves, explicit clear supported). Rejecting the set on a non-top-level collection returns `400`, consistent with existing hierarchy validation. Deliberately **decoupled from `UserEntity.examDate`** — the existing profile-level board-exam date stays exactly what it is today (Dashboard-only), unrelated to this field. A learner can have multiple Goals, each with its own optional target date; this also makes the feature available to every profile type (not just `BOARD_EXAM`), since a STUDENT's "Semester 1 Finals" Goal has a real deadline too.
- **Study intensity on the user, not the Goal (backend).** `studyDaysPerWeek` is a **user/profile-level** capacity attribute, not a Goal field — study capacity is a person attribute; putting it on the Goal would tell a learner with two dated Goals to "study every day" for each independently, which is incoherent since capacity is shared across whatever they study.
- **Smart dating on adopt/copy (backend).** `adoptGoal` and the self-copy path must never carry a source's `targetCompletionDate` onto the created copy — always reset to null on both adopt and self-copy, same category as the existing generated-content self-copy exclusion in the versioning rule. A curator's or a previous owner's deadline means nothing to the new owner. No auto-guessed default date either (e.g. "8 weeks from today") — null stays null until the user deliberately sets one; this preserves the graceful no-target-date-means-no-weekly-section degradation and avoids fabricating a deadline the user never gave.
- **Weekly countdown derivation (backend).** New derived fields on `GET /collections/{id}/goal` computing weeks-remaining, concepts-remaining, and today's concept budget, from the existing readiness fields (`totalConcepts`, `masteredConcepts`, `dueConcepts`, `notPracticedConcepts`) plus the target date and `studyDaysPerWeek`. Returns null/absent when no target date is set. No stored per-week schedule entity — **rebalancing after a missed day is not a feature, it falls out of recomputing `remaining concepts ÷ remaining scheduled days` fresh on every request.** No new mastery signal, no new stored progress field, no AI call — pure derivation like the existing Goal readiness rollup.
- **Scheduling algorithm (decided; deterministic, no LLM).**
  - Largest-remainder (Hamilton's) method for weighted proportional concept allocation across subjects by concept count — exact totals, no rounding drift.
  - Due concepts act as a **floor** subtracted from the day's budget first; new-concept learning fills the remainder. Implementation note for the feature doc: due-concept count can *rise* with elapsed time even for a diligent learner (it tracks review timing, not inactivity) — this is correct behavior, not a scheduler bug, and must be documented so it isn't later mistaken for one.
  - "Learn → Practice → Review → Master" ships as **presentation-layer labels** over the existing concept classification (not-started/due/mastered/struggling) — not a new signal and not an enforced sequential gate.
- **Three surfaces (frontend).** Dashboard primary CTA (driven by the Primary Review Set), Review Set (Goal) detail (new "This Week" section above the existing `ReadinessSummary`), and the Review Sets list page (replacing/augmenting the empty "Recommended Review Sets" state).
- **Progress default-view change (frontend, scoped narrowly).** When a Primary Review Set exists, `/progress` defaults to that collection's scoped readiness view — reusing the `PlanPicker` / `?collectionId=` mechanism that **already exists** (no new endpoint). The all-subjects rollup must stay reachable from the picker; this must not orphan mastery for notes that live outside any Review Set (the Public Library "browse and study any note" path is preserved). This does not undo the v0.36.0 Progress/Readiness unification — it changes the default view, not the surface.
- **Target-date + intensity input (frontend).** Goal-only optional date field on the create/edit metadata surface (hidden for child Subject plan editing); the study-intensity question asked at the same moment as the target date (one screen, sane default if skipped), reading/writing the user-level `studyDaysPerWeek`.
- **Post-adopt guidance nudge (frontend).** Reuse the existing `pickActiveGuidance()` tip system to suggest the adopter set their own target date (and optionally set the adopted plan as primary) — same discoverability pattern shipped in v0.39.1 for adoption itself, no new tooltip mechanism.

### Phasing within v0.40.0 (recommended split)

- **Phase 1:** Primary Review Set foundation + per-Goal target date (with the never-copy-on-adopt/self-copy rule) + user-level `studyDaysPerWeek` + the simple total-remaining ÷ remaining-scheduled-days computation with the due-as-floor rule + the three surfaces + the Progress default-view change. This delivers the "what do I do today / am I on track" answer at low risk.
- **Phase 2 (fast-follow, may slip out of v0.40.0):** the weighted largest-remainder subject distribution and day-of-week interleaving (Mon: Site Analysis + Foundations; Tue: …) — the parts most likely to need iteration, gated on Phase 1 proving the countdown mechanic actually gets used.

Anti-drift: no new top-level entity — the weekly plan is a derived, read-time view; the only new persisted state is the `primaryCollectionId` reference, the `target_completion_date` column, and the user-level `studyDaysPerWeek`. No change to `UserEntity.examDate` or the Dashboard board-exam countdown it already drives. Target date is Goal-level only, never on a child Subject plan. No hardcoded profile-specific "Review" vocabulary anywhere the Primary concept is surfaced — must resolve through `getCollectionLabels`. No adaptive/AI-driven scheduling, streaks, or calendar integration. **No nav rename, no Exam Hub change, no Explore page, no Progress full-redesign in this release** — those are recorded in the deferred "Review-Set-Centric Navigation" section and gated on this release proving the Primary concept out in real usage first.

### Completed in v0.40.0 so far

- **Primary Review Set backend foundation.** Added nullable `users.primary_collection_id`, `PUT /collections/{id}/primary`, `DELETE /collections/{id}/primary`, and `primaryCollectionId` on `GET /auth/me`. Backend invariant maintenance now clears invalid primaries and auto-sets the single owned top-level Goal after create, standalone adopt, first-time Goal adopt, top-level delete, and parent changes. Frontend consumption, target dates, user study intensity, and scheduler derivation remain separate follow-up work.
- **Target completion date + study intensity backend.** Added nullable `note_collections.target_completion_date` (top-level Goals only, set via `updateMetadata` PATCH-omit-preserves, cleared via dedicated `DELETE /collections/{id}/target-date`) and nullable `users.study_days_per_week` (`PUT /users/profile/study-days-per-week`, full-replace, 1-7 validated). Neither field is ever copied on adopt or self-copy.
- **Weekly countdown derivation backend.** `GET /collections/{id}/goal` computes `weeksRemaining`, `conceptsRemaining`, `todaysConceptBudget` from target date + study intensity + the existing readiness rollup (due concepts as floor, new-concept pacing fills the rest). Missing intensity defaults to 7 days/week for the math only; only a missing target date hides the countdown.
- **Dashboard primary CTA (frontend).** `DashboardStudyPlanSection` branches on `primaryCollectionId`, showing the owned primary Goal/plan directly (CTA always opens it, never adopts) instead of the course/program-matched recommendation, with a profile-aware "Primary ..." heading via a new `CollectionLabels.primarySingular` field. Falls back to the existing recommendation flow if the primary reference isn't found. Wired into both Dashboard call sites only.
- **Target-date + intensity input (frontend).** `EditCollectionModal` gained the target-date and study-intensity fields, shown only for top-level Goals. Target date shares the metadata PATCH to set, uses the dedicated clear endpoint to unset. Study intensity is sourced/saved separately (`getMe()`/`updateStudyDaysPerWeek`) since it's user-level, not a collection field.
- **Goal detail "This Week" section (frontend).** `GoalWeeklyCountdownCard` renders above `ReadinessSummary` on the Goal page, hidden when no target date is set; editing the target date refetches the Goal view so the countdown doesn't go stale. Scoped limitation: only shows once a Goal has at least one child Subject plan (matches the page's existing Goal-vs-leaf branching). Remaining frontend work: Review Sets list page, Progress default-view change, post-adopt nudge.

### Parked (not scoped for this release)

- **Cross-course note reusability.** Its own proposal doc already recommends deferral; revisit only once cross-program overlap is a demonstrated broad-audience maintenance concern, not a single curator's one-off friction. A much cheaper interim exists if the underlying curation pain needs addressing sooner: multi-tag a note/subject with secondary course/programs for discovery, or improve the course/program picker UX during curation — without touching the one-note-one-program architecture.
- **Per-Subject (child-level) target dates.** Only the parent Goal carries a target date in v1; a child Subject plan's contribution is limited to feeding the parent's concepts-remaining count, matching the existing `courseProgram` cascade precedent (parent-level field, children just feed the rollup).
- **Multi-Goal scheduler orchestration.** v0.40.0 assumes one primary drives the coach; simultaneously scheduling across several active dated Goals is deferred until the single-primary model is validated.

---

## v0.40.1 - Public Review Set Reachability (planned)

Base branch for this release: `releases/v0.40.1` (not yet kicked off). Origin: surfaced while pressure-testing a broader "Review Set discovery redesign" proposal against the Public Library philosophy ("discover knowledge" vs. "follow a structured journey") — the full redesign (Recommended/Trending/Recently-Added/Browse-All ranking) was rejected as premature and overlapping with the deferred Explore-page direction below, but one piece of it survived: a **verified, narrow inconsistency**, not a redesign.

**Verified finding:** `NoteCollectionService.listPublic` already supports `courseProgram == null` and returns every PUBLIC top-level collection unfiltered — the backend capability exists today. But no frontend surface ever calls it that way: both `/collections/published` and the Dashboard's `DashboardStudyPlanSection` widget always pass the user's own course/program, and when a user has none set, the UI shows a dead-end empty state instead of browsing everything. A Nursing student cannot reach an Architecture Official Review Set through any UI path today, even though it is technically PUBLIC. Once a curator marks something PUBLIC, a second system-level gate (course/program match) on top of that undermines what "public" means — the same publish-vs-surface expectation notes already get in Public Library. Commitment happens at *adopt*, not at *browse*, so hiding options at browse time isn't justified by Review Sets being a higher-commitment object than a note.

### Scope

- **Recommended section (unchanged).** Keep today's course/program-scoped result as the default, personalized entry point — preserves the "official set built for your track" trust signal and serves the exam-taker segment's need for direct relevance.
- **Browse All Official Review Sets (frontend, dedicated page only).** New section on `/collections/published` below Recommended, calling the existing `listPublicStudyPlans({})` (no course/program filter) and reusing existing Review Set cards. **Dashboard does not get this section inline** — `DashboardStudyPlanSection` is a glance widget, not a browse surface; Dashboard instead gets a "Browse all Review Sets" link pointing to the dedicated page's Browse All section.
- **Deterministic default order.** Browse All must ship with some default sort (alphabetical, or grouped by course/program header) — an unsorted wall of cards past ~a dozen Official Review Sets defeats the point. This is not a ranking/recommendation engine, just a sane default.
- **Rewire the existing empty state, don't build a new one.** `DashboardStudyPlanSection` already has a `browseWhenEmpty`-gated empty-state card ("No curated Review Sets for X yet") used on the `/collections` page today. The fix is wiring that existing card's CTA to the unfiltered Browse All call, not a parallel component.
- **Duplication between Recommended and Browse All is expected**, not a bug — standard featured-row + full-catalog pattern (a course/program match legitimately appears in both).

### Explicitly out of scope

Search/filter-component reuse from Public Library (verify feasibility separately, ship Browse All as a flat list first), Trending, Recently Added, Popular, Community Review Sets, any ranking/recommendation engine, Explore page, nav redesign, Dashboard redesign. All remain future roadmap items (see the deferred "Review-Set-Centric Navigation" section below, which this release deliberately does not advance).

### Sequencing note (deliberate, recorded to avoid drift)

v0.40.0's own frontend scope already includes "the Review Sets list page (replacing/augmenting the empty 'Recommended Review Sets' state)" — the **same empty state** this release rewires. The user chose not to change v0.40.0's planned scope to fold this in; v0.40.0 proceeds as originally scoped (including its own empty-state work), and v0.40.1 follows immediately after as a v0.39.1-style polish fast-follow that reworks that same empty state a second time. This is a known, accepted cost (same surface touched in two consecutive releases) — not an oversight.

Anti-drift: no backend changes required (the unfiltered endpoint already exists); no new data model, no new persisted state; pagination is a known future ceiling once the Official Review Set catalog reaches the hundreds, not addressed here.

---

## Review-Set-Centric Navigation (deferred future direction — not scoped to any release)

Not a version — no release branch, no implementation scope yet. Captured here so we stop designing around abstractions (chiefly Exam Hub) that may no longer be the right shape, per the user's explicit request to lock the long-term architecture without building it all now. **Gate on the Primary Review Set concept (shipping in v0.40.0) proving useful in real usage before committing any of this.**

Origin: structural realization that Review Sets have become NoteLib's primary study experience, not a secondary feature alongside Public Library/subjects. When the product was designed, users mostly discovered notes through the Public Library, so Dashboard, Progress, and Exam Hub were all built subject-first. That assumption no longer holds now that Official Review Sets, Subject Plans, smart progression, readiness, and (v0.40.0) weekly plans exist.

Direction, as stated by the user:

- **Official Review Set catalog as the scalable replacement for hand-built per-profession pages.** Publishing another Official Review Set should require no new frontend, versus adding an Exam Hub page per profession (Civil/Electrical/Mechanical Engineering, Nutrition, Midwifery, …).
- **Public Library preserved as a distinct discovery path.** "I want to browse notes" (Public Library) and "I want to study for an exam" (Review Sets) are two valid entry points to the same notes; the Public Library is not absorbed or removed.
- **Dashboard and Progress reorganized around the Primary Review Set** instead of subject/course-program. Dashboard asks "what review are you preparing for?"; Progress answers "what's happening with my primary study journey?" Subject mastery still exists — it becomes a facet of the Review Set rather than the primary navigation.
- **Eventual nav shape:** Dashboard / My Reviews / Library / Explore / Progress, where Explore houses the Official Review Set catalog + Public Library + trending/community.

Corrections recorded from the codebase research behind this (so they aren't relitigated later):

- **Exam Hub is not the maintenance burden the original framing assumed.** It is a single dynamic `/exam/[slug]` route over a small hardcoded config (`frontend/lib/exam-hub-config.ts`), and it does a distinct job an authenticated catalog does *not* replace: **anonymous / SEO acquisition** (organic search → signup; authenticated visitors route into a filtered Public Library). The recommended resolution is **convergence, not deletion** — an Explore surface that houses the Official Review Set catalog + Public Library, and lets the existing `/exam/[slug]` SEO pages deep-link into a matching Official Review Set once one exists. Retiring the SEO surface would cost organic acquisition.
- **Naming stays profile-aware.** Any "Primary Review" / "My Reviews" language must resolve through the existing `getCollectionLabels` pattern, not ship as hardcoded universal copy — the concept is profile-agnostic; the label is not (Study Plan / Review Set / Lesson Plan / Collection).
- **Progress reorg is a default-view change, not a re-scoping.** The `PlanPicker` + `?collectionId=` machinery already exists; a future reorg defaults it to the primary and must keep the all-subjects rollup reachable so notes outside any Review Set aren't orphaned, and must not undo the v0.36.0 Progress/Readiness unification.

---

## Note Detail readiness as its own tab (candidate)

Idea surfaced while fixing v0.36.1's Note Detail readiness placement: instead of showing the readiness summary inline (currently just before Performance Overview on every tab), give it its own tab alongside Summary / Key Concepts / Quiz / Full Notes.

Blocker: the current 4-tab bar already fills the width of a standard iPhone viewport exactly. Adding a 5th tab would overflow and needs a scroll/overflow affordance (e.g. an arrow or horizontal scroll) so the added tab isn't silently hidden on mobile — that affordance needs its own design pass, not a fast-follow. Not scoped into any release yet.

---

## v0.33.0 - Study Plans as a Retention Engine (released)

Base branch for this release: `releases/v0.33.0`.

Theme: the constraint carried over from v0.32.2 is **near-zero W1→W2 retention (5.6%, recent cohorts ~0%)** — users activate (68.6%) and engage once (58.8%), then don't return; the longest observed streak is ~2 days. The diagnosis from v0.32.2 said: ship **one scoped retention lever**, not broad re-engagement infrastructure. The lever here is the **Study Plan as a trackable readiness journey**: today a plan is a static, ordered folder you adopt once (a one-time act with weak retention legs), with no signal of how ready you are or whether returning moves anything. This release gives a plan a **readiness number that only goes up when you come back to practice**, and clears the publish/discovery friction so curated plans actually reach the learners they're for.

Why now (over teacher-flow / bulk quiz): we still have **no teacher cohort**, so that work defers again (v0.34.0 candidate). The leverage is the students and exam-takers we *do* have — readiness gives them a reason to return, which is the un-conflicted constraint.

Two tracks, deliberately sequenced so the retention bet (B) is the headline and the activation polish (A) supports it:

### Track A — Study Plan publish & discovery polish (activation)

Friction found while seeding curated plans: publishing is clunky and silently drops metadata.

- **Decouple metadata-save from publishing.** The backend already has separate endpoints — `updateMetadata` (title / description / course-program) and `updateVisibility` (publish, which validates notes). The publish modal's `handlePublish` validates notes *before* persisting course/program, so when publish fails (private/empty notes) the typed course/program is discarded; the create flow also drops description in some paths. Fix (frontend sequencing): **always persist metadata first / independently** (auto-save on blur or an always-available "Save details" action), and treat **Publish** as a separate gated action with a clear blocker message. Do **not** weaken publish validation — it still requires every note public + at least one note; it only stops throwing away metadata.
- **Surface recommended plans on the user's own Study Plans page (`/collections`).** Reuse the existing Dashboard "Recommended {plural}" section (the recommended card + `See all N` link → `/collections/published`) rather than tabs — tabs hide discovery behind a click and add chrome to a near-empty page. Scoped to the learner's **own course/program only** (an all-programs browse is what Public Library does for *notes*; here it'd be noise). Place the learner's own plans first (workspace), recommended below — or recommended-first when they have zero owned plans (discovery-first when empty).

### Track B — Readiness as a retention signal (headline)

The reason-to-return. A learner studying for CPALE wants to see "how ready am I, and what's due," and watch that number move as they practice. An audit (2026-06) found readiness already half-exists: `/me/progress` is a full subject-mastery dashboard **already available to all plans**, and note detail already shows per-concept "due for review" badges — **but gated to PLUS/PRO**, so the non-returning Free cohort can't see its own return trigger. The plan scope is the only one genuinely missing. So Track B is not "build a dashboard" — it is **place one ConceptHealth-derived readiness signal where it has reach**.

**Organizing principle — signal vs. detail:**

- **Signal** = a compact, glanceable summary ("62% ready · 3 due"). Goes at high-traffic touchpoints. In v0.33.0: the **per-note rollup** on note detail (data is already fetched there). _Dashboard plan-card + plan-list badges are a deliberate fast-follow → v0.34.0._
- **Detail** = the full breakdown (overall ring + per-subject/per-concept bars). Goes on dedicated surfaces: the new **plan readiness sub-route** `/collections/[id]/readiness` (reached by a "Check readiness" CTA on plan detail, **not** a tab, **not** inline on the execution rows) and `/me/progress` (already built — not rebuilt; cross-linked).

In v0.33.0 (confirmed with Claude — split into two Codex prompts: ① shared component + plan sub-route, ② note rollup + Free-gate):

- **Plan readiness sub-route** — owner-scoped `GET /collections/{id}/readiness`: overall readiness % + per-subject `SubjectProgressEntry[]` scoped to the plan's notes, rendered with the existing CSS progress-bar pattern (no chart library) + an inline SVG ring.
- **Per-note readiness rollup** on note detail — a compact "% ready · X/Y mastered · N due" computed from the already-fetched `conceptHealth`.
- **Free-gate change (deliberate, value-ladder-preserving)** — ungate the note readiness **signal** to Free (rollup + which concepts are due); keep the per-concept review-**timing** detail ("Due — 3d ago") PLUS/PRO. This aligns note detail with `/me/progress` (already Free) — it removes an inconsistency, and gives the Free cohort the return trigger. It is an **access/value-ladder change, not a billing change**.
- **Shared component + unified vocabulary** — one reusable `ReadinessSummary` consumed by the plan surface and the note rollup, and one readiness language (`ready / mastered / due / not started`) reconciled across note, plan, and Progress.

Locked direction:

- **Deliberate reversal of "plans don't duplicate Progress."** `docs/features/collections.md` states twice that no mastery %, weakest-subject, or readiness belongs on a Study Plan. This release **consciously revisits** that — but on the **dedicated readiness sub-route only**, never on the plan **execution-detail rows** (the action / next-step list keeps its no-mastery rule). Record the reversal in `collections.md` when shipping.
- **Readiness is derived, not stored.** Reuse the ConceptHealth recency spine; **no new mastery signal, no new persisted progress field on collections, no new generated content, no new AI/LLM call.** Aggregation only, and plan/note readiness must **match `/me/progress`** for the same concepts (reuse `ProgressReportService` classification + `masteryPercentage`, extract shared logic — no new thresholds).
- **Free-gate is access, not billing.** Ungating the readiness signal touches the existing `FeatureGateService` / `canViewConceptHealth` path only. **No price, quota, pass-duration, or checkout mechanics change** (those stay locked). The per-concept timing detail stays PLUS/PRO.
- **No new chart dependency; charts express readiness, never a standalone vanity dashboard.** Reuse the `progress-report-client.tsx` bar pattern + inline SVG.
- **Discovery stays course/program-scoped** (not all-programs); surface via the existing Dashboard "Recommended" pattern (not tabs).
- **No per-profile pipeline fork.**

Status:

- **Track A** shipped on `feature/study-plan-publish-discovery-polish`.
- **Track B Prompt 1** shipped: owner-scoped plan readiness endpoint, dedicated `/collections/[id]/readiness` route, shared `ReadinessSummary`, and `PLAN_READINESS_VIEWED`.
- **Track B Prompt 2** shipped: note-detail readiness rollup plus the Free-gate signal/detail split.

Scope:

- **Track A:** publish/create metadata decouple (frontend); recommended-plans section on `/collections` (frontend, reuse the Dashboard component). _(shipped on `feature/study-plan-publish-discovery-polish`)_
- **Track B (two Codex prompts):** ① backend plan-readiness aggregation (over ConceptHealth scoped to a collection's notes) + the plan sub-route + the shared `ReadinessSummary` component; ② per-note rollup on note detail + the Free-gate signal/detail split. Multi-system — Codex prompts, then Claude audits the diff before commit.

Anti-drift: readiness reuses ConceptHealth (no new signal/field/AI, matches `/me/progress`); the Progress-separation reversal is scoped to the dedicated sub-route and recorded in `collections.md`; the Free-gate is an access change, not a billing/quota/price/checkout change; discovery is course/program-scoped; Track A must not weaken publish validation; no new chart library. Out of scope / deferred: **dashboard + plan-list readiness badges (signal reach) → v0.34.0**; teacher bulk-quiz & teacher-flow polish (v0.34.0 candidate, gated on teacher users); live-link / shared-progress plans (different architecture — belongs with teacher-flow).

---

## v0.33.1 - Study Plan polish & Curated Plan Coverage (released)

Base branch: `releases/v0.33.1`. The study-plan line. v0.33.0 shipped the readiness *lever*; the validation pull (`docs/product/journey-validation-pulls.md`) showed the plan-adoption retention bet was never testable — **4 adoptions / ~153 users, 1 goal (Accountancy) with a ready plan, 0 post-adopt returns**. The binding constraint is **curated-plan coverage.** v0.33.1 leads with coverage plus two UX-clarity polish items, and was **deliberately expanded** to include **Study Plan Hierarchy Phase 1** (one level of Goal → Subject nesting) so curated plans can be built *with levels* from the start — a schema change that makes v0.33.1 a large patch (accepted). Hierarchy Phases 2–4 stay a future, validation-gated initiative.

**Curated Plan Coverage (headline — content/curation ops first).**
- Run the follow-up inventory query (in `journey-validation-pulls.md`): does seeded public *note* content exist for the real top goals (just not assembled into plans) → cheap **assembly/curation ops**; or is there little content → a **seeding** job (Bulk Generation) first.
- Target: ≥1 complete, credible, Study-Pack-ready curated plan per goal the **actual** public learners have (let the inventory say where they are — do not assume ALE/PNLE/LET).
- Content/curation work, not new architecture. No new endpoint, model, or AI synthesis.

**Study Plan Hierarchy — Phase 1 (Goal → Subject plans; backend + UI) — shipped.** Added `parent_collection_id` (self-referential, **2-level enforced**: parent must be top-level, child must have no children — cycles impossible); a Goal detail page listing child Subject plans with each child's readiness plus a cheap **Goal % = Σ child.mastered / Σ child.total** (sums per-child counts, so **no cross-subject concept re-dedup**); the owned-plans list shows top-level only. Added the single-canvas **Study Plan Builder** at `/collections/{id}/builder`: Goal = canvas, Subject plans = draggable/collapsible sections, notes = cards that can be added, reordered, removed, or moved across Subjects. The builder replaces the scattered nest/unnest detail-menu curation path and orchestrates existing collection endpoints; the only new backend capability is sibling ordering (`sibling_position` + `PUT /collections/{id}/children/order`). Modules stay as label-sections and are off the builder canvas. Reuses the `NoteCollection` model, `getReadiness`, `ProgressReportService`, `ReadinessSummary`, dnd-kit, and `getCollectionLabels` (no `ProfileType` branching). **Deferred** to later phases: recursive adopt-the-whole-Goal, per-module %, metadata/est-time/difficulty, arbitrary depth, direct notes on a Goal. This is a **deliberate, scoped reversal** of the "no parent/child collections" rule (two levels only). Architecture audit: `docs/product/STUDY_PLAN_HIERARCHY_PLAN.md`; Codex prompt: `docs/codex-prompts/v0.33.1-study-plan-hierarchy-phase1.md`.

**Recommended card — already-owned state (frontend).** The `/collections` Recommended section can show a re-adopt CTA for a plan the learner already has. Adopted copies are already handled ("Continue this plan" via a `sourcePlanId` match); the gap is the **owned-source** case — an admin/curator viewing their *own published* plan sees "Start this plan", which would self-adopt a redundant copy. Fix: do not offer a re-adopt CTA for a plan the user already owns or adopted (detect owned-source, not just adopted). **Filtering out** already-owned plans (falling back to the existing empty state) is conceptually cleaner than a dead badged card; an "In your library" badge + "Open" CTA is acceptable. Correctness (no self-adopt) > presentation.

**Section drag refinement (frontend).** v0.33.0's label-derived sections use a single drag context spanning all sections, so cross-section drag is confusing (non-destructive: the `label` wins on regroup; dragging to the top can reorder sections via min-position). Move up/down and within-section reorder are correct. Fix: scope drag-and-drop **per section** (a `SortableContext` per section), or disable cross-section drag while sections are active. Sections stay label-derived; display order stays min-position-derived.

Anti-drift: readiness stays Free (decided, not revisited — see the Journey candidate); readiness derived and matches `/me/progress`; the Goal % sums child counts (no cross-subject concept re-dedup); the nesting reversal is **scoped to 2 levels** (Goal → Subject) — no arbitrary depth, no recursive adopt, no per-module mastery this release; sections stay label-derived (no section entity, no mastery on rows/headers); no `ProfileType` branching in services; no quota / billing / price / checkout change; no new chart library; no AI synthesis.

---

## v0.33.2 - Plan Detail Redesign (view/edit split) (released)

Base branch: `releases/v0.33.2`. The v0.33.x series is the study-plan line — each minor tightens it. v0.33.1 shipped hierarchy + curated content; v0.33.2 fixes the plan detail on mobile. A real 29-note curated plan (LET Professional Education Mastery) exposed the gap: the page is a wall of edit chrome — SECTION combobox, Move up/down, and Remove on every single note card — making it unusable on mobile. Gate (curate one real plan first) is now lifted. v0.33.3 (recursive Goal adopt) follows.

**Plan detail mobile redesign (frontend, Codex prompt):**
- **Collapsible section cards** — each label-derived section becomes a card with a collapse toggle. Collapsed state = section header + note count; expanded = note rows. Section cards make boundaries clear and allow users to focus on one section at a time. **Default collapse state: expanded on desktop (wide viewport), collapsed on mobile** — so desktop users see content up front while mobile users get a focused one-section-at-a-time view. Use a responsive breakpoint check, not a separate layout; the interaction model is the same on both.
- **View mode by default** — opening a plan shows a clean read view. SECTION combobox, Move up/down, and Remove are hidden by default; revealed via an organize/edit mode toggle. The toggle is *not* a third parallel editor — the Builder at `/collections/{id}/builder` handles Goal-level curation (drag notes across Subject plans); this toggle handles leaf-plan organization (reorder/section-assign within a plan).
- **No readiness on section headers.** Sections stay label-derived. If a grouping needs its own readiness it should be a child Subject plan (the Goal → Subject model already shipped). This is a confirmed locked rule — do not reverse it.

**Subject metadata normalization on LET curated plan (data ops, no code):**
- Normalize inconsistent `subject` field variants on the 29-note LET plan so the readiness page groups cleanly. Cleanup confined to that plan's notes; no impact on other plans or global Progress.

**Study Pack subject source fix (backend, `StudyPackService`):**
- Root cause of readiness grouping drift: when a Study Pack is generated, the LLM freely invents a `subject` string, and `normalizeSubject()` only catches exact case/whitespace variants — not semantic synonyms ("Assessment" vs "Assessment of Learning"). Fix: prefer `note.subject` over the LLM-generated subject when one already exists on the note. One-line change in `StudyPackService` at the `setSubject(...)` call. If the note has no subject, fall back to the LLM value (same as today). Prevents future drift on all curated plans without touching the data model.

**Readiness grouping by note subject (backend, `ProgressReportService`):**
- `ProgressReportService.resolveSubject()` currently reads `studyPack.subject` — the LLM-generated string that may drift. Fix: resolve the grouping key from the note's own `subject` field (fetched in one batch query per grouping call, no N+1), falling back to `studyPack.subject` only when the note has none. Combined with the `StudyPackService` source fix, this heals historical drift on all existing plans without requiring per-plan data patches.

**Feature doc update (`docs/features/`):**
- Document the note-wins subject resolution rule: `note.subject` is authoritative; LLM subject is fallback when note has none. Update readiness grouping description to reflect the `ProgressReportService` change.

Anti-drift: sections stay label-derived (no mastery on headers); readiness stays Free, derived, matches `/me/progress`; no new chart library; no quota / billing / price / checkout change; recursive Goal adopt is deferred to v0.33.3.

---

## v0.33.4 - Builder Surface Clarity (released)

Base branch: `releases/v0.33.4`. Focused frontend-only polish. The Goal Builder (`/collections/{id}/builder`) is an authoring surface, not a study surface — the top-level `ReadinessSummary` ring is the study/monitoring signal that doesn't belong there. Per-module stats inside each Subject plan header ("4 notes · 0% ready · 0/34 mastered") are retained as inline curation feedback.

**Remove `ReadinessSummary` ring from Builder page (frontend-only).** The `<ReadinessSummary>` at the Builder page root is the same component used on the Goal detail page (`GoalDetailView`) — moving it to the detail page is not new work, it just stops rendering on the builder canvas. No backend changes; no change to the plan detail page, leaf-plan organize mode, or any data endpoints.

Anti-drift: readiness continues to appear on Goal detail via `GoalDetailView`; per-module stats in Subject plan header rows stay; no new component, endpoint, or migration. Builder-for-leaf-plans (replacing the leaf-plan organize mode entirely with a Builder canvas) is explicitly scoped to v0.34.0 — do not include it here.

---

## v0.33.3 - Recursive Goal Adopt (released)

The curated-plan experience is only complete when a learner can adopt a Goal and get all its child Subject plans and notes in one action. Today, adopting a Goal is not supported — only leaf plans can be adopted — so a curator building a multi-Subject goal plan cannot surface it as a single adoptable unit.

**Recursive Goal adopt:**
- Adopting a Goal recursively copies all child Subject plans and their notes into the learner's library.
- Consistent with the Study Pack copy-and-edit principle: adopted notes are the learner's own copies and can be edited, removed, or reorganized.
- Open question (to resolve with advisor before the Codex prompt): should adopted-Goal copies allow note removal and reorganization immediately on adopt, or should they be locked read-only with an "unlock to edit" step? The Study Pack precedent (copy + edit freely) points toward unrestricted edits, but the curated-plan integrity question is different in character — a curator may want to signal "these notes belong together."
- Unlocks the Goal → Subject readiness view working end-to-end: once a learner adopts a Goal, each child Subject plan shows its own readiness bar in the Goal detail view (already ships from v0.33.1).

Anti-drift: 2-level max (Goal → Subject) — no 3rd level this release; adopt stays Free; adopted plan readiness reuses `ProgressReportService` (same as owned plans); no new mastery signal or stored field.

---

## Deeper plan nesting — study-plan-within-a-study-plan (candidate, nice-to-have)

The 2-level Goal → Subject model is intentionally constrained. Going to 3+ levels is **feasible but a real project, not a constraint flip** (`parent_collection_id` is self-referential so the *column* supports depth, but every shipped invariant assumes 2): N-level needs real ancestor-walk **cycle detection** (today 2-level makes cycles impossible by construction); **recursive readiness rollup** (today sums *direct* children; the no-cross-subject-dedup rule gets thornier each level); **adopt-recursion**; per-level `sibling_position`; and a tree/breadcrumb builder UX. Genuinely nice-to-have, later — see `docs/product/STUDY_PLAN_HIERARCHY_PLAN.md`.

---

## v0.34.0 - Journey: Goal-First Study Experience (released)

Base branch: `releases/v0.34.0`. Transform the study plan detail from a note list into a guided study surface. Every piece reuses shipped infrastructure — no new AI, no new mastery signal, no new quiz model. Composition over rewrite.

**Scope (decided with Claude, 2026-07):**
- **Section readiness on plan detail.** Reverse the v0.33.x locked rule: section cards show readiness % and concepts due. Backend adds per-note concept health counts to the plan detail response; frontend aggregates by section label client-side. Lazy-loaded post-initial-render. No new mastery signal.
- **Estimated study time.** Optional `estimatedStudyHours` on the collection entity (Flyway migration). Curator-entered, carries over on adopt, never blocks adopt or quiz.
- **Plan Hero.** Plan title, description, course/program, and estimated time as a visual hero card on the plan detail. Frontend-only.
- **"Continue where you left off".** Last-studied note = `argmax(lastSessionCompletedAt)` — already in the plan detail response. Adaptive CTA drives to that note's next action. Zero new backend.
- **Builder for leaf plans.** Extend `/collections/{id}/builder` to handle single-plan (leaf) context: notes as draggable cards with section-label assignment. Eliminates the inline organize mode toggle from leaf plan detail pages. Existing endpoints only — no new API.

Locked direction (carried forward from prior sessions):
- **Curation-match, never AI synthesis.** "NoteLib chooses the notes" = admin-curated plan matched to learner's goal/course-program — not an algorithm assembling a per-user plan.
- **Composition, not a rewrite.** Journey repositions shipped parts, not a new model/endpoint/pipeline.
- **Unify readiness onto Progress — one surface, not two (decided 2026-06).** Plan readiness sub-route becomes a per-plan/goal lens into Progress, not a separate surface. Do **not** redirect "Check readiness" → global Progress (regresses scoping).
- **Monetization is mid-journey, not at completion.** Adopt stays Free. Conversion moment is goal-commitment + exam-date urgency + premium walls hit *during* the journey.
- **Readiness stays Free.** It is the return trigger; clawing it back is a trust hit with no revenue upside at 0.0% quota-hit rate.
- **Retention pull = goal-gradient (a defined finish line), not streaks.** Legitimate and pressure-free.
- **Mastery stays per-study-pack; no reset.** No concept-name mastery transfer across adopted duplicates.

Anti-drift: section readiness reuses `ConceptHealth` / `ProgressReportService` — no new signal, field, or AI call; `estimatedStudyHours` always optional; Builder leaf canvas uses existing collection endpoints only; no new chart library; readiness Free; adopt Free; no quota / billing / price / checkout changes; 2-level hierarchy max enforced.

---

## v0.29.0 - Bulk Generation & Generation-Context Correctness (released)

Base branch for this release: `releases/v0.29.0`.

This release has three workstreams: **(1) Bulk Generation** (admin content seeding), **(2) Generation-context correctness** (level content by course/program, not learner level), and **(3) Profile-type integrity + onboarding enforcement** (every account that drives generation must have a profile type; onboarding must be a real server-side boundary). Profile-type integrity was pulled forward from v0.30.0.

Theme: kill the one-note-at-a-time tax on seeding study content. An admin enters one subject and a list of topics, and the system generates a note **and** its Study Pack for each topic, unattended. Built admin-first to seed our exam-prep buckets (ALE/PNLE/LET), but architected as a normal Library capability behind a **role gate** — opening it to all users later is a gate-flip, not a rebuild.

Why now (over Readiness Signals): seeding public content one note at a time is the active operational bottleneck, and the building blocks already exist — `NoteGenerationService.generateFromTopic` (note content from a topic), the async Study Pack pipeline, and the per-user quota services. This is mostly **orchestration** (high leverage, low risk), and more seeded content makes Readiness Signals (now v0.30.0) more valuable when it ships.

Locked direction:

- **No new job/progress infrastructure.** Each topic is generated content-first into a real note (note content is required, so the row appears once content-gen completes), then runs Study-Pack-gen on the **existing executors** (`studyPackGenerationTaskExecutor`). Progress and load-on-refresh come from the real note rows plus the `studyPackStatus` field the Library list already carries (`GENERATING → READY/FAILED`) — no batch-job entity, no progress table, no new status enum.
- **Per-item isolation.** One bad topic never fails the batch — try/catch per iteration, mirroring the `/notes/import-batch` pattern from v0.27.0.
- **Throttled fan-out.** Submit generation chains to the existing executor with throttling so a 40-topic batch never saturates the pool or trips LLM rate limits. This is the one genuinely new dispatch concern — throttling, not infrastructure.
- **Role-gated in Library, not a separate `/admin` surface.** Entry lives in the Library Create split-button, shown only to ADMIN; the flow is a dedicated `/library/bulk-generate` route with Note-Create-aligned metadata and discrete topic rows. Opening to all later = relax the gate.
- **Quota check built now; ADMIN bypasses.** Per-user quota enforcement is wired through the existing quota services so the all-users path is real, not a stub — ADMIN role bypasses it. Deferred: the "quota ran out mid-batch" partial-execution messaging (admin never hits it).
- **Reuse existing creation + context paths.** Note creation goes through `NoteService.create(UpsertNoteRequest)`; course/program and best-effort exam-pool context go through the shared resolver. The note's dedicated `subject` field = the batch subject (it beats the AI subject); title and tags stay AI (from the Study Pack write-back). No per-profile pipeline fork.

Scope:

- **Bulk-generate endpoint** — a role-gated endpoint that, per topic, creates a DRAFT note, kicks the content-gen → Study-Pack-gen chain on the existing executors (throttled, per-item isolated), applies resolved subject/course/audience metadata, and honors a per-batch Public toggle.
- **`/library/bulk-generate` admin page** — enter one Subject plus topic rows, use the compact profile-aware `Course / Program · Target Audience` grid plus Public toggle, and submit; results surface as real notes in the Library that resolve `GENERATING → READY`.
- **Quota wiring** — per-user pre-flight quota check (admin-bypassed) plus a cost/count preview before the batch starts.

Anti-drift: no new job/progress entity; reuse `NoteGenerationService`, the async Study Pack pipeline, `NoteService.create`, and the existing quota services; the admin gate is role-based and removable; no per-profile pipeline branches. This is bulk *content* (note + Study Pack) generation from topics — **distinct from** the v0.33.0 collection-level bulk *quiz* generation over existing notes.

### Workstream 2 — Generation-context correctness (learner level → course/program)

Content generation must be leveled by **course/program**, not learner level, so notes and Study Packs are correct for everyone who copies them — shared content can't depend on a per-user attribute.

Locked direction:

- Strip `{LEARNER_LEVEL}` / `{LEARNER_LEVEL_GUIDANCE}` from the **content** prompts only — `note-generation-developer.txt` and the study-pack `developer.txt`. Course/program is already injected; rely on it for depth + terminology. The embedded study-pack quiz follows the same rule (it is content + a dedup source, not a per-taker quiz).
- **Keep learner level in the quiz/exam prompts** (Quick Review, Challenge, Adaptive, Long Exam, Board Exam, Interview, Teacher) and in the exam-question pool — those adapt to the *taker*, re-resolved per session.
- Learner level is **no longer required** to generate a note from a topic or a Study Pack. It stays a best-effort context field (from profile) only to pre-warm the exam pool; per-taker correctness is preserved by `sampleQuestions`' `sameLearnerLevel` gate + on-demand fallback.
- **Remove the now-vestigial Learner Level field** from the bulk-generate form (it only fed content leveling). Bulk's exam-pool pre-warm uses the admin's profile level, best-effort.
- No prod data migration: new content is course/program-leveled; existing content is untouched (regeneration optional, deferred). Copy/long-exam already serve takers their own level — no copy-time regeneration.

### Workstream 3 — Profile-type integrity + onboarding enforcement

Every account that drives generation/personalization must have a profile type, and onboarding must be a real boundary (today it is enforced only by client-side per-page guards).

Locked direction:

- **Re-prompt, never silent-default.** A wrong default mis-personalizes invisibly; null is at least detectable. Gate on `profile_type` (not just `onboarding_completed_at`, which misses legacy completed-but-null rows): a user missing a profile type gets one focused, blocking prompt — ask only what is missing (don't re-run full onboarding for someone who only lacks a profile type; send truly-not-onboarded users through full onboarding).
- **Close the bypass with server-side enforcement.** Onboarding is currently enforced only by per-page client guards (no middleware, no backend gate), so an authenticated-but-not-onboarded user can mutate via direct API. Add server-side enforcement so key mutations (note + Study Pack creation/generation) require a completed profile. The client prompt alone is bypassable.
- All readers already treat null `profile_type` as STUDENT/non-teacher (no crashes) — this is a personalization-quality + integrity fix, not a crash fix. The cohort is bounded (legacy rows + abandoned onboarding); new users cannot reach completed-but-null because `completeOnboarding` requires a profile type.

---

## v0.29.1 - Bulk Generation Polish (released)

Base branch for this release: `releases/v0.29.1`.

Theme: follow-up polish on the v0.29.0 bulk-generation flow, deferred to keep v0.29.0 scoped.

- **Partial-outcome reporting for bulk generation.** Shipped as a bounded terminal-outcome receipt: `POST /notes/bulk-generate` returns a `resultId`, the worker writes one owner-scoped `bulk_generation_result` receipt at batch completion with requested/created counts, exact failed topic strings, and quota-blocked topic strings, and `GET /notes/bulk-generate/results/{id}` returns then deletes it. The Library keeps the immediate `Queued N notes` toast, waits for the existing auto-refresh poller to settle, then reads the receipt and shows a dismissible banner only when content-generation failures or note-generation quota blocks occurred. `Retry these` pre-fills `/library/bulk-generate` only for genuine generation failures. This is the one v0.29.1 relaxation of the v0.29.0 no-progress-infrastructure rule: it is terminal, write-once/read-once, and expires after 24h; it is not a batch-job entity, live progress table, per-item status row, or new status enum.

- **Open Bulk Generation to all users (gate-flip + quota-aware failure UX + discoverability).** Shipped: Bulk Generation is now available from the Library Create menu and `/library/bulk-generate` for authenticated, onboarded users; `POST /notes/bulk-generate` and `GET .../results/{id}` use the same `hasAnyRole('USER','ADMIN')` gate as other note endpoints. Non-admins keep the existing per-user note-generation quota path (`enforceLimits = role != ADMIN`), while ADMIN still bypasses. The receipt now distinguishes generation failures from note-generation quota blocks, the Library banner retries only generation failures and routes quota blocks through `getUpgradeCtas(currentPlan)`, the bulk page shows remaining note-generation quota when available, the single Note Create Generate-from-topic panel links to bulk generation, and Help includes `/help#bulk-generate`. Out of scope remains unchanged: no quota limit changes, no teacher-specific bulk flow, and no bulk *quiz* generation.

---

## v0.30.1 - Copy Flow Polish (released)

Base branch for this release: `releases/v0.30.1`.

Theme: very few users copy notes from the Public Library. A small, frontend-only UX pass to reduce that friction — clearer labeling, a post-copy modal that showcases the note instead of shortcutting it, and an editable-draft option for users who want to adapt content. This is a polish patch, not a feature release; the bigger activation bet (curated, adoptable plans) is v0.31.0.

Locked direction:

- **Rename for clarity.** The card action becomes `Add to Library` (note + Study Pack), replacing `Save` — which read as a bookmark next to the like (heart). Name the destination, not just a short verb; the icon changes from the save/bookmark glyph to a copy/library glyph.
- **Modal showcases the note.** The post-copy success modal leads with `View Note` (the hub where the full Study Pack and every quiz/exam mode live) and **removes the Quick Review quick-action** (it under-utilized the note). Body copy states the payoff (editable copy + quizzable Study Pack that feeds Progress).
- **One action on the card; the fork on detail.** The grid card keeps a single primary copy action on every breakpoint (no dropdown — bad for touch). The public note **detail** page carries the secondary `Copy as editable draft` (`copyNote(id, { includeStudyPack: false })` → Draft, no Study Pack, content stays editable) stacked under the primary.
- **No backend, no new infra.** Reuse the existing `copyNote` `includeStudyPack` param and the existing `PUBLIC_NOTE_COPY_CLICKED` / `PUBLIC_NOTE_COPIED` analytics (the labeling change is measurable today). No enum, quota, entity, or endpoint changes.

Scope:

- **Card relabel** — `Save`/`Saved` → `Add to Library`/`In Library`, copy/library icon, auth-modal title updated.
- **Copy success modal** — `View Note` as the sole primary action (Quick Review removed); value-stating body copy; the duplicate close-button bug fixed (`AppModal` already renders its own close; the modal no longer passes a second one).
- **Editable-draft on detail** — secondary `Copy as editable draft` action on the public note detail page, stacked under the primary.

Out of scope: backend changes, analytics enum additions (existing events suffice), a top-of-funnel "note opened from grid" event (optional follow-up), and Quick Look preview (a phase-2 bet only if measurement shows evaluation friction is the binding constraint).

---

## v0.30.0 - Readiness Signals (released)

Base branch for this release: `releases/v0.30.0`.

Theme: make Progress an **honest, complete readiness picture** for our actual users — students and exam-takers. The gap: practice in the exam modes never moves the Progress page. (Profile-type integrity was pulled forward into v0.29.0.)

Why later (after Bulk Generation): seeding content was the active bottleneck, so Bulk Generation took v0.29.0. The leverage here is still the students and exam-takers we *do* have — they need to trust that Progress reflects everything they've practiced. Teacher-flow polish and bulk *quiz* generation remain deferred to v0.33.0 (still no teacher users).

Locked direction:

- **Read-time fallbacks stay; fix the source.** Today only Quick Review, Challenge, and Adaptive Practice write `ConceptHealth` (via `recordCorrectAnswers`), and `ConceptHealth` is the **only** thing the Progress page reads. Long Exam, Board Exam, and Interview Practice produce rich per-session reports (`LongExamMasteryReportResponse` domain breakdown, `InterviewReadinessReportResponse` gaps) that are **ephemeral** (`sessionMetadata` JSON) and never persist — so an exam-taker can grind Board Exams and see a flat Progress page. Wire these results into `ConceptHealth` so they count.
- **Two write-paths, not three.** Board Exam *is* `LONG_EXAM` session mode (no separate enum) and runs through `LongExamService`; Interview Practice runs through `InterviewPracticeService`. So the recording work lives in those two services, mirroring the existing `recordCorrectAnswers` contract — no new entity, no new quota, no new artifact.
- **Reconcile two "mastery" grains.** Long Exam reports LLM-tagged **domain**-level mastery; Progress is built on per-**concept** `ConceptHealth`. The mapping (domain/result → concept records) is the hard part and must be designed before writing — don't invent a parallel mastery store. Shipped in two passes: (1) record fully-correct concepts intersected with the source pack's `keyConcepts` (drop non-matches, no orphan rows); (2) constrain exam generation to tag each question with a separate, schema-enforced `keyConcept` drawn from the source pack's key concepts, leaving the report-facing `concept` label untouched, with a read-time fallback to `concept` for legacy/pool questions.
- **Weakness counts too, uniformly.** `ConceptHealth` is freshness-only (`lastCorrectAt`), so a concept a user keeps *failing* looks identical to one never practiced. Add a `lastIncorrectAt` weakness signal that **mirrors** the `recordCorrectAnswers` contract (one nullable column, no parallel mastery store) and record missed concepts on completion across **all six** practice modes — Quick Review, Challenge, Adaptive, Long Exam, Board Exam, Interview — not exam-only. Recording weakness for exams alone would create the exact drift this release exists to kill. Progress surfaces a struggling state derived from the two timestamps (latest event wins; self-clears on a clean retake).

Scope:

- **Exam-mode results feed Progress** *(shipped)* — `LongExamService` (Long + Board) and `InterviewPracticeService` record concept-level signals into `ConceptHealth` on session completion, intersected with the source pack's `keyConcepts`.
- **Source-constrained key concepts** *(shipped)* — Long Exam and Interview question generation emit a schema-enforced per-question `keyConcept` from the source pack's key concepts; recording prefers it and falls back to the free-form `concept` for legacy/pool questions.
- **Weakness signal across all modes** *(shipped)* — `lastIncorrectAt` recorded on completion in every practice mode; Progress shows a distinct struggling indicator (Note Detail "Needs work").

Teacher-flow polish and bulk *quiz* generation move to v0.33.0 (still no teacher users); v0.31.0 is now Adoptable Study Plans (v1). No readiness work is deferred.

---

## v0.31.0 - Adoptable Study Plans (v1, released)

Base branch for this release: `releases/v0.31.0`.

Theme: most learners don't want to assemble a study plan note-by-note — they want a ready-made, structured plan for their goal (a LET taker wants a LET reviewer plan, not a pile of filtered notes). Observed behavior: most users only add their own notes; the pre-filtered Dashboard helps them *find* exam-relevant public notes but still leaves them to self-assemble. This release lets a learner **adopt** a curated, ordered study plan in one tap.

Why now (over teacher-flow): this is the payoff of the two prior bets — **Bulk Generation** seeds the public exam content (ALE/PNLE/LET) and **Readiness Signals** make practice count. Adopt → practice → Progress reflects it. It serves the students/exam-takers we already have. Teacher-flow polish and bulk *quiz* generation are deferred to v0.33.0 (we still have no teacher users, so it is fine to defer).

The discriminating constraint that fixes the design: the **entire learning loop — generation, `ConceptHealth`, Progress, "Next in this plan," and the v0.30.0 exam→Progress recording — runs on *owned* notes.** A plan that merely *links* public notes is inert (it cannot feed Progress). Therefore **adopt = copy, not reference.**

Locked direction:

- **Curation, never generation.** Sequencing is **human/admin curation over existing seeded content**, not AI-synthesized per user. This stays on the right side of the standing "never generate curriculum" rule. The forbidden version is *"auto-generate a personalized plan"* — do not build that here.
- **Adopt = snapshot copy.** "Start this plan" copies the curated notes (with their linked Study Packs) into the user's library and creates a personal Study Plan (collection) in the curated order. After adoption it is fully the user's own — later edits to the curated source plan do **not** propagate (point-in-time snapshot). Reuse the existing public-note copy path `NoteService.copyNote(id, ownerUserId, includeStudyPack=true)`, which already copies the linked Study Pack and is idempotent (re-copy returns the existing copy). The adopt *output* is a normal `NoteCollection` (Study Plan) using `getCollectionLabels` for profile-aware naming.
- **Quota: adopt is free, like copy.** Adoption bills nothing — it is a copy, not a generation. Billing happens only later, on the existing paths: Study Pack **regenerate** (if the user chooses to), and per-mode quota on **Challenge Quiz** and **Exam** when they practice. Do not let adoption become an accidental paywall.
- **Copy volume: light, isolated, no new infra.** Adopting copies N notes + N study packs (cheap DB copies, **no LLM** — a plan is adoptable only when all its notes already have Study Packs). Wrap the copy fan-out in **per-item isolation** (one bad note never sinks the adopt). Do **not** reuse or add async/bulk-generation job infrastructure — a curated plan is bounded, so a single request with per-item try/catch is sufficient.
- **Re-adopt rule.** `copyNote` is idempotent at the note level (re-copy returns the existing copy). The only open piece is the collection-level rule (skip already-owned notes vs. create a fresh plan) — decide at build time; do not duplicate notes.
- **Depends on seeded public content.** This requires curated public notes per bucket (ALE/PNLE/LET) — the exact content Bulk Generation exists to seed. v1 targets those buckets only.

Scope (v1 cut):

- **Curated source plans (admin)** — an admin-owned, ordered representation of a study plan over already-**public** seeded notes. `NoteCollection` today is owner-private and over owned notes only, so the curated *source* needs an admin/public representation (admin-owned public collection variant, or config). The build needs a short design pass on this source shape; the adopt *output* is a normal user collection. No new content type, no AI synthesis.
- **Surface + adopt** — present the relevant curated plan(s) on the Dashboard/onboarding for the user's exam, with a one-tap "Start this plan" that performs the snapshot copy (notes + linked Study Packs) and creates the personal Study Plan in curated order, then routes into the existing plan/loop.

Deferred — explicitly, together (do not smuggle into v1):

- **Live-link / shared-progress plans.** The "original owner keeps the plan; adopters get their own progress on shared content; owner pushes updates" model is a **different architecture**, not a setting — it requires letting users practice/track progress on study packs they do **not** own, relaxing the `findByIdAndOwnerUserId` ownership gate across every read/practice path plus per-user progress overlays on shared content. That is the **teacher-monitoring / LMS shape**, which belongs with teacher-flow (v0.33.0, gated on having teacher users). **Do not offer both own-copy and link-copy** — that doubles the surface for two architectures.
- **User/teacher-authored plan sharing of *private* notes.** Snapshot copy relies on the public-note copy path, which requires the notes to be public. A teacher sharing their *private* curated notes to students has no copy path today (it would need a share-grant mechanism). So teacher→student sharing of private content is a separate deferred piece even in snapshot form.

Anti-drift: reuse `copyNote(..., includeStudyPack=true)`, the `NoteCollection` model, and `getCollectionLabels`; no new quota category; no async/bulk-generation infra; no AI curriculum synthesis; no relaxation of note/study-pack ownership checks; v1 is admin-curated plans over public seeded notes only.

---

## v0.31.1 - Adoptable Study Plans Discovery & Status (released)

Base branch for this release: `releases/v0.31.1`.

Theme: v0.31.0 ships the adopt mechanics but discovery is intentionally minimal — published plans surface **only** on the Dashboard, only the **top match** for the learner's profile course/program, and there is no onboarding entry or plan-completion signal. These are the natural follow-ups once the core loop is validated. Small, additive, no new architecture.

Candidates:

- **Onboarding surface for published plans.** v0.31.0's locked scope named "Dashboard/onboarding" but only the Dashboard card is wired (`DashboardStudyPlanSection`). Add the same one-tap adopt card to onboarding so a new learner can start a curated plan immediately. Reuse `listPublicStudyPlans({ courseProgram })` + `adoptStudyPlan`; no new endpoint.
- **Browse / multi-match published plans.** The Dashboard shows only `publicPlans[0]`, so publishing several plans for the same course/program hides all but one. Add a lightweight way to see all published plans for the learner's course/program (a "browse plans" surface or "see more"). The public list endpoint (`GET /collections/public?courseProgram=`) already returns the full set; this is a frontend listing surface (Public Library is for *notes*, not plans).
- **Plan progress/status badge.** Add a **Not started / In progress / Completed** badge on the Study Plans **list** (`/collections`), derived from practiced-vs-total notes. This is *execution status*, not mastery — no percentage, milestones, or streaks (those stay on Progress). Requires adding progress counts to `NoteCollectionSummary` (the list DTO carries only `itemCount` today); the detail page already shows the full rollup, so no detail change needed.
- **Bulk-tool quota awareness & gating.** Bulk generate folds the note-generation quota into the Topics cap (`X / min(50, remaining)`), hard-caps adding/queueing at the remaining note generations (with a backend submit-time reject as the stale-tab safety net), and soft-confirms when topics exceed remaining Study Packs (notes still get content as drafts). Bulk import surfaces remaining OCR/image-scan quota (it creates Draft notes only — no Study Pack, OCR consumed only for images/scanned PDFs). Both reuse a generalized `NearLimitBanner`. No new quota category — purely surfacing/gating existing `/api/me/plan` values.

Anti-drift: no new quota category, no async infra, no AI synthesis, no relaxation of ownership checks; the status badge must not duplicate Progress's mastery surface (no %/milestones/streaks on collections); bulk-tool gating only reads existing `/api/me/plan` remaining values, never recomputing quota from local lists.

---

## v0.31.2 - Analytics Integrity & Funnel Visibility (released)

Base branch for this release: `releases/v0.31.2`.

Theme: the funnel and admin dashboards already exist and the core loop is healthy (as of 2026-06-21: ~140 users, activation 67.2%, value-loop 57%, 306 packs/week). The real gaps are **data integrity in analytics** and **visibility into retention + where monetization leaks** — not the product loop itself. Small, additive, mostly backend; no new product surface for users.

Scope:

- **Fix `analytics_events` FK violation (lost SIGNUP analytics).** Recurring prod WARN: `analytics_events_user_id_fkey` violated on `eventType=SIGNUP`. Root cause: `AuthService` fires `analyticsService.trackEvent(saved.getId(), SIGNUP, …)` right after `userRepository.save(user)`, but `trackEvent` dispatches to an async executor (`analytics-1`) whose own transaction often commits **before** the signup transaction commits → the user row isn't visible → FK fails. `persistEvent` catches it (non-fatal), but the SIGNUP / SIGNUP_COMPLETED / EMAIL_VERIFICATION_SENT events are **silently dropped, so signup-based funnel metrics undercount**. Fix direction: telemetry should not hard-FK to `users` — **drop the FK** (migration; keep `user_id` nullable + indexed) and/or fire analytics **after commit** (`@TransactionalEventListener(AFTER_COMMIT)`). FK defined in `V25__analytics_events.sql` (`user_id UUID REFERENCES users(id) ON DELETE SET NULL`). This is the only item actively degrading data quality right now → natural first slice.
- **Analytics event audit.** Cross-check every `AnalyticsEventType` value against its fire site: flag events no longer emitted (or belonging to removed features), confirm the admin funnel/summary queries reference current events, and verify no events are silently dropping (the FK bug above is Exhibit A).
- **Retention + monetization-conversion visibility.** `AdminFunnelService` reports activation, value-loop, and paywall conversion but has **no cross-week retention cohort** (W1 activated → W2 returned) — add it. Also add **upgrade-click → checkout → paid** drop-off instrumentation (the gap between `UPGRADE_CLICKED` and `SUBSCRIPTION_STARTED`). Context driving this: monetization conversion — not engagement — is the leak (free quota hit rate 0.0%, ~10 upgrade clicks → 0 conversions; audience is PH licensure-exam takers, so investigate GCash/Maya/OTC checkout prominence and exam-readiness-framed pricing rather than a generic monthly sub). Learn via 1:1s with the upgrade-clickers, not a mass "what's missing" survey (the loop is not broken).

Anti-drift: analytics/telemetry must be resilient (never fail or drop on referential timing); no PII added to event metadata; admin-only surfaces; no change to the universal learning loop.

---

## v0.32.2 - Conversion Diagnosis & Quota Honesty (released)

Base branch for this release: `releases/v0.32.2`. Patch after v0.32.1; mostly frontend + analysis.

Theme: v0.32.1 surfaced the premium exams and reframed pricing, but conversion was still ~0 of ~153 verified users. **Thread 3 (the funnel diagnosis) ran first and re-scoped this release.** The real, un-conflicted constraint is **near-zero W1→W2 retention (5.6%, recent cohorts ~0%)** — users activate (68.6%) and engage once (58.8%), then don't return. (An initial read flagged a "broken checkout" from "6 upgrade clicks → 0 `CHECKOUT_INITIATED`"; that was a **metric-inception artifact** — `CHECKOUT_INITIATED` was added in v0.31.2 while `UPGRADE_CLICKED` is far older, and a live upgrade reached the real Xendit invoice. Checkout works.) Free is **not** too generous. Full data: `docs/product/conversion-funnel-finding.md`. **Anti-drift: do not raise exam quota numbers** — quota size is not the constraint; retention is.

### Thread 3 — Conversion funnel diagnosis *(done)*

Read prod `/admin/funnel` (`AdminFunnelService`): activation 68.6% (105/153), value loop 58.8%, free-quota-hit 0.0% (study-pack-only), W1→W2 retention 5.6%. Corrected reading: checkout is **not** broken (the "6 → 0" is a metric-inception mismatch — see finding doc); the real constraint is retention. Output: `docs/product/conversion-funnel-finding.md`. Drives the priorities below.

> **Scope discipline:** this release ballooned past a patch. Thread 1 (re-engagement) is **the** priority; Threads 2–4 are secondary/low-effort and ship only if they don't delay Thread 1; Plus-tier stays deferred. Resist adding more.

### Thread 1 — Turn on the re-engagement loop *(THE priority — a measured test, not a declared fix)*

**Diagnosis:** the retention loop already exists (`RetentionEmailScheduler` → `RetentionService`, daily inactivity/weak-concept emails, EmailLog dedup + real-inactivity gating) and the return reason exists (due/weak concepts), but `AuthService.signup` creates every user with all reminder flags `false`, so the loop is **dark**. Reminders were defaulted off deliberately — Resend FREE tier is 100 emails/day and verification must not be starved.

**Lever (build, then measure W1→W2 lift — this is *a* cause and the cheapest test, not proven to be *the* cause):**
- **Budget-aware re-engagement sender** — a per-day send budget (config-driven, default 100) with a generous **transactional reserve** (default ~40) so verification always sends first; re-engagement drains only the remainder, counted from `email_logs`, with un-sent candidates rolling to the next daily run. No queue/outbox — guard on the existing daily dispatch. Prompt: `docs/codex-prompts/v0.32.2-budget-aware-reengagement.md`.
- **Signup default flip — inactivity only** (`inactivityRemindersEnabled=true` for new users; weak-concept / weekly-summary / marketing stay opt-in). **Verify the inactivity template renders end-to-end before going live** — it has been a dark path.
- **Bounce suppression** — a Resend bounce/complaint webhook populates a suppression list; all sends skip suppressed addresses (protects budget + sender reputation). Plus a **frontend signup email-typo suggestion** ("did you mean gmail.com?") to stop typo'd domains (e.g. `0gmail.com`) from bouncing — frontend-only, Claude-direct.
- **Existing-user backfill — decided: backfill all to ON.** A migration sets `inactivity_reminders_enabled=true` for all existing users (other categories untouched). Low risk: the flag always defaulted `false`, so no one deliberately turned it off; unsubscribe remains the opt-out, and the budget guard ramps them over days. Now in the Codex prompt.

### Thread 2 — Close instrumentation gaps *(shipped)*

(a) The funnel compared all-time metrics with different inception dates (`UPGRADE_CLICKED` old vs `CHECKOUT_INITIATED` since v0.31.2), which produced the false "broken checkout." Admin Conversion Funnel now defaults event-based stages to a common 30-day window, with 7 / 30 / 90 / all-time options and `windowDays` / `windowStartedAt` response metadata. (b) `getQuotaHitMetrics` no longer measures only study-pack quota: it now reports current-period Free hits by quota type plus an "any quota hit" aggregate, with 0-limit Free-unavailable types excluded from their own denominators.

### Thread 3 — Quota honesty via per-session deduction *(shipped)*

The Pro card's "Long Exam (12 sessions)" / "Board Exam (10 sessions)" were really per-source-note units (`additionalStudyPackIds.size() + 1`; a 3-note exam cost 3). Rather than relabel to the less-generous "source-note units", the deduction now runs per session: **1 unit per exam** (Long + Board), regardless of note count — so "12 / 10 sessions" is literally true *and* more generous, and the prescreen quota copy says "uses 1 of N remaining". The source count still drives question-count + generation; **the numbers (12/10) and the monthly reset stay the same.** This is a deliberate quota-**mechanic** change (the release's anti-drift line is updated to allow exactly this and nothing more).

Rationale (advisor-checked): at zero payers, marginal generation cost is second-order — fixed infra ÷ 0 revenue is the constraint — and per-session is simpler, more generous, and honest-by-construction. The accepted tradeoff is that per-session removes the per-note cost governor (users will tend to max notes per exam); that's fine now (more notes = more value) and re-tunable later. **Hedge: revisit if multi-note volume drives cost once we have payers.** Rejected: per-week quotas (would confound the running re-engagement retention experiment and throttle bursty exam-cramming). After this, leave the quota numbers alone until the churn replies + retention experiment produce signal.

### Thread 4 — Plan-launch prescreen polish *(shipped)*

"Choose another mode" is hidden on the Long Exam, Interview Practice, and Board Exam (board-exam-setup) prescreens when launched from a Study Plan (`collectionId` present) — there is no mode grid to return to in that flow, and the back link already routes to the plan. Note-launched flows are unchanged. Frontend-only.

### Deferred — Plus-tier reason-to-exist

Plus has zero exam access (a dead tier for exam-prep). Deferred until checkout works and there is real conversion data to justify any tier change — pointless to reposition a paid tier no one can currently buy.

### Parked strategic question — audience / ICP

Bigger than any email or pricing mechanic: **are the ~153 users the people who would ever pay?** At 0 paying / 5.6% retention, mechanics-tuning on the wrong base won't convert. Not a code change — a product-discovery question (who are they, did they come for exam prep or one-off note help). Keep on the table so the release doesn't become tuning on a base that may be the wrong base.

## v0.32.1 - Monetization Surfacing & Pricing Clarity (released)

Base branch for this release: `releases/v0.32.1`. Patch after v0.32.0; mostly frontend.

Theme: the monetization leak is **conversion, not engagement** (prod ~2026-06: free-quota hit rate 0.0%, ~10 `UPGRADE_CLICKED` → 0 `SUBSCRIPTION_STARTED`). Two failure points: almost nobody **reaches** a paywall (premium exams aren't surfaced), and the few who click **drop at checkout** (the pricing surface reads like a recurring sub when plans are actually one-time passes). This release attacks the *surfacing/desire* half — surface the premium exams as paywall moments and clarify the pricing model — while the *checkout* half is measured by the v0.31.2 `CHECKOUT_INITIATED` funnel. **Honest scope note:** these lift exposure + desire (top-of-funnel); they feed more clicks into a checkout that still converts ~0, so they must be paired with reading the checkout-conversion funnel, not sold as the conversion fix on their own.

### Thread 1 — Premium-exam paywall at the Start CTA (shipped)

Free and Plus users clicking a premium exam (Long Exam / Board Exam / Interview Practice) can open the exam prescreen and **see its strength**, then hit the paywall at the **Start CTA** — not be blocked at card-click. This reverts the prior card-click paywall behavior that hid the value.

- Shipped as frontend-mostly: `PaywallModal` fires from the Start CTA for the three premium exams. Mode-selection cards still show the Pro badge, Start CTAs read as unlock actions for non-Pro users, and backend `FeatureGate` remains defense-in-depth.

### Thread 2 — "Exam this plan" for non-teachers (Study Plan → premium exam) (shipped)

Study Plan detail now launches **the profile's premium exam mode over the plan's notes**, pre-selected and capped at the existing per-exam note limit (a thin layer over the existing multi-note flow — the backend already supports multi-note for all three modes: `LongExamService` covers Long + Board, `InterviewPracticeService.resolveAdditionalNoteIds` covers Interview). Profile → mode mapping is driven by `resolvePlanPremiumExamMode` in `exam-mode-visibility.ts` (no hardcoded profile checks):

- Student → Long Exam · BOARD_EXAM → Board Exam (fixed set) · Professional → Interview Practice · Teacher → unchanged (keeps the DOCX Exam Builder) · profiles with no premium mode (e.g. PARENT) → CTA hidden.
- Shipped as frontend-only: a profile-aware CTA (`Take the Long Exam` / `Take the Board Exam` / `Start Interview Practice`) routes with `collectionId`; each prescreen intersects that plan with the user's Study Pack-ready notes, scopes the picker to plan notes only, and pre-selects up to the existing cap. Eligibility is Study Pack readiness only (not a pre-generated quiz). Pro-gated users land on the Thread 1 Start-CTA paywall after seeing the plan-scoped setup.

**Deferred polish within this release (candidates):**

- **Plan-launch back link target** *(shipped)* — plan-launched Long/Board/Interview prescreens route their back link to `/collections/{collectionId}` with the profile-aware label (`Study Plan` / `Review Set` / `Collection`) instead of "← Note".
- **Plan note selection count + scoping copy** *(shipped, reword approach)* — the plan-scoped picker now reads "Add up to N more notes from this plan" instead of "from this subject"; the primary note stays implicit ("Built from …") and the footer total ("3 notes · 25 questions") confirms all plan notes are included.
- **"Review first" advisory modal** *(shipped)* — launching a plan's premium exam when one or more exam-eligible plan notes are unpracticed (`lastSessionCompletedAt === null`) surfaces a soft advisory ("Review before the exam?") with `Review first` / `Start the exam anyway`. Recommendation only — never blocks; routes straight through when all eligible notes are practiced. No persistence (re-evaluated each launch).

### Thread 3 — Pricing copy reframe (one-time pass clarity) (shipped)

The paid plans are **one-time, time-boxed passes with no auto-charge**, but the surface read like a recurring subscription — "/month" prices, intro "first month, then …/month" copy, a "Manual renewal" footer, and redundant CTAs. **Copy + card layout only — no billing/quota mechanics changed.**

Shipped: prices now read as duration (`₱X / 1 month` · `₱X / 3 months` · `₱X / 1 year`); intro reframed to a first-pass discount (`₱149 for your first 1-month pass · ₱179 after`); per-month quotas kept with the clarifier "usage limits refresh each month during your pass"; one-time-payment / never-auto-charged / library-permanence / desktop-and-mobile-web reassurances centralized in `plans.ts`; the Pro card collapsed to one hero `Get Pro — ₱X / 3 months` (the 3-month exam pass) plus a small `Also available: 1 month · 1 year` line; FAQ gained "Will I be charged again?". Settings plans cards unified the `Monthly / Annual` toggle + separate exam-pass button into one 3-segment pass-length selector (`1 month · 3 months · 1 year`) with state-aware `Save N%` badges on the 3-month and 1-year passes, driving a single price + CTA. The 90-day exam pass is displayed as "3 months" and an active pass's billing status reads "3-Month Pass". Reframed across the pricing page, landing pricing sections, settings plans cards, and the paywall modal; settings billing-status copy for an active pass stays accurate.

**Deferred polish within this release (candidate):** mobile pricing-cards layout — explore a horizontal scroll-snap carousel (with a peek of the next card + dot affordance, condensed cards) as an alternative to the current vertical stack. Decision so far: keep vertical for the feature-heavy marketing cards (comparison-friendly, no discoverability/two-axis-scroll risk); a condensed scroll-snap is more defensible for the Settings cards. Prototype before committing.

- **Lead with the model, not the price:** each paid card headline states one-time payment · N days full access · never auto-charged.
- **Remove subscription cues:** replace the "Manual renewal" footer with one-time wording ("we never auto-charge — grab a pass again only when your next exam is near").
- **Reassure on data permanence:** notes, Study Packs, and progress stay in the library even after a pass ends.
- **All-access:** "Full access on web and mobile."
- **Fix the card UI:** one CTA per card (e.g. "Get Pro — ₱599 / 90 days"); remove the duplicate "Go Pro" / "Go Pro — 90-Day Exam Pass" buttons and the redundant header line.
- **Reconcile the "/month" tension honestly:** quotas reset monthly (`BillingUsageResetJob`), so a 90-day pass spans ~3 monthly refreshes — say "usage limits refresh each month during your pass" rather than reading as a monthly sub.

Anti-drift: all upgrade/pricing copy goes through `getUpgradeCtas(currentPlan)` in `src/config/plans.ts` (never hardcode in cards); premium-exam visibility goes through `exam-mode-visibility.ts` (no hardcoded profile checks); paywall copy is action-aware via `PaywallModal`/`resolvePaywallAction`. **No mechanics change** — quotas, pass durations, prices, billing periods, and exam-generation paths are untouched (per-pass limits or new multi-note generation are explicitly NOT in scope). Suggested split into separate slices: Thread 1 (small revert) and Thread 3 (copy) are quick; Thread 2 is a real feature — prompt it on its own.

---

## v0.32.0 - Account & Communication Controls (released)

Base branch for this release: `releases/v0.32.0`. v0.32.0 was previously slated for teacher-flow / bulk quiz — that work is deferred to v0.33.0 (no teacher cohort yet); this major release is a privacy / account-control / communication-preferences theme. The "additional candidates to consider" below are not yet scope-locked — prune/confirm them before building each slice.

Theme: give users real control over their account and the email we send them, and close the associated privacy/compliance gaps. Today there is no account-deletion path, no unsubscribe link on recurring email, and email preferences are split across an ad-hoc "Study Reminders" card. This release consolidates account and communication controls into a coherent, compliant surface (GDPR right-to-erasure + portability; CAN-SPAM/GDPR one-click unsubscribe). Mostly additive; some new endpoints + a destructive account-deletion flow that needs careful, transactional handling.

Scope:

- **Account deletion (right to erasure).** A user-initiated delete with explicit confirmation, removing or anonymizing the account and owned data (notes, Study Packs, quiz/review sessions, collections, usage, auth providers, tokens). Decide hard-delete vs anonymize per table; `analytics_events.user_id` is already FK-free (v0.31.2) so orphaned ids are fine — do not delete telemetry rows on account deletion. Must be transactional and idempotent, invalidate sessions/refresh tokens, and be clearly irreversible in the UI. Consider a short soft-delete/grace window vs immediate purge.
- **Data export / "Download my data" (portability).** Let a user export their own content (notes, Study Packs, sessions summary) as a downloadable file. Pairs with deletion to round out the privacy story. Owner-only, no PII beyond the user's own data.
- **Email/communication preferences center + Settings redesign.** Replace the single "Study Reminders" card with a dedicated **Email Preferences** section that lists every optional email type (inactivity, weak-concept, weekly summary, future marketing/re-engagement) with per-type toggles, clearly separating **transactional** email (verification, password reset, receipts — always sent, shown as informational/non-toggleable) from **optional** email. **Design is Claude's lane** (information design of the preferences surface); per-toggle wiring reuses the existing reminder-flag pattern. Includes the **weekly-summary opt-in toggle** below.
- **Weekly-summary opt-in flag.** Add `weekly_summary_reminders_enabled` (mirrors `inactivity_reminders_enabled` / `weak_concept_reminders_enabled` from `V28`) gating `RetentionService.findWeeklySummaryUsers`. **Decision (2026-06): default OFF (opt-in)** — column `NOT NULL DEFAULT FALSE`, existing users backfilled to disabled, new users created disabled; this immediately cuts the Sunday blast ~90% under the Resend cap, and users re-enable in the preferences center. 1:1 mirror of the existing reminder pattern. Codex prompt drafted: `docs/codex-prompts/weekly-summary-opt-out.md`.
- **Tokenized one-click unsubscribe link (for optional emails).** Add an unsubscribe footer link to retention/marketing emails backed by a signed/opaque per-user token and an unauthenticated unsubscribe endpoint that flips the relevant preference without login. No PII in the token/link. Transactional emails are not unsubscribable. Closes the CAN-SPAM/GDPR one-click-unsubscribe gap; pairs with the preferences center.
- **Email deliverability hardening (right-sized; mostly ops).** As of 2026-06 prod is on Resend's free tier (verify: ~100/day **and** ~3,000/month, ~2 req/s) with 142 users; all email types share one pool, so the Sunday `WEEKLY_SUMMARY` blast can exhaust the daily cap and then make verification / password-reset throw `BAD_GATEWAY` for the rest of the evening. **Primary fix is operational: upgrade the Resend tier (~$20/mo, ~50k/mo) — it moots the cap and the need for a priority queue; do not build a pending-email outbox to dodge ~$20/mo.** Immediate zero-code stopgap: `RETENTION_WEEKLY_CRON=-`. In-scope code hardening: retry-on-429 (or pace the blast under the rate limit) in `ResendEmailService` so a transactional email landing during a blast isn't dropped. A persistent priority outbox stays deferred unless volume genuinely approaches a paid limit; design rule if ever built — transactional sends immediately and is never gated, retention tolerates ~1 day and is dropped after ~1 week.

Additional candidates to consider for the theme (prune at kickoff):

- **Change email address** (with re-verification of the new address).
- **Account deactivation** (reversible soft-disable) as a lighter alternative to full deletion.
- **Marketing-consent capture** at signup + a clear transactional-vs-marketing taxonomy for all email types (also informs which emails get the unsubscribe link).
- **Surface "weekly summary exists"** one-time nudge so the opt-in default OFF doesn't make the channel invisible (optional; only if we want to keep weekly reach).

Anti-drift: reuse the existing reminder-preference pattern (entity flag + repository finder + `updateStudyReminders` + Settings card) — do not build a new preferences framework; no PII in unsubscribe tokens/links; account deletion must be transactional, idempotent, and never delete FK-free telemetry rows; do not build an email outbox/queue unless volume genuinely approaches the provider limit; no change to the universal learning loop.

---

## v0.35.0 (candidate, gated on teacher users) - Bulk Quiz Generation & Teacher-Flow Polish

Theme: reduce the friction of turning material into quizzes. Builds on the v0.27.0 collections spine and the v0.29.0 bulk-generation foundation. **Deferred (was v0.33.0, before that v0.32.0, earlier v0.31.0, before that v0.30.0, originally v0.29.0)** — we have no teacher users yet, so this only schedules once a teacher cohort exists; it may slip further. (v0.33.0 was repurposed for the Study Plans retention work above.) **Honest remainder after v0.29.0:** v0.29.0 builds the shared batch-orchestration + quota foundation for bulk *content* (note + Study Pack) generation from topics; this release extends that to **collection-level bulk *quiz* generation over existing notes** plus async quiz generation, and bundles three teacher-flow quiz-preview polish fixes. Make quiz generation async (like the Study Pack pipeline), then add a collection-level bulk action that batches the universal per-note pipeline.

Locked direction:

- **The universal spine is preserved.** Every profile still generates a Study Pack before a quiz — this is intentional, not a funnel to remove (consistency across easy profile switching + uniform quota). Bulk solves the friction by *batching* the same pipeline, never by forking a profile-specific shortcut.
- **Profile-aware framing, not a fork.** Teacher emphasizes quizzes, Student study packs, via the existing Study/Exam Focus copy mechanism — never per-profile pipeline branches or hardcoded `if (TEACHER)` checks.
- **No new quota category, no collection-level AI synthesis.** Each note spends one existing per-note credit per artifact; bulk is a fan-out of per-note generation, not a synthesized collection document (Option B stays deferred).
- **Bulk is explicit, not an import side-effect.** One deliberate user click for the batch; preserves the explicit-generation rule. DOCX export and shareable quiz links stay Teacher/Admin only.

Scope:

- **Teacher quiz-preview polish** — move the ⋯ context menu to the top-right of the note title; remove the redundant "Correct Answer" panel (the choice already shows a ✓ Correct badge + highlight, no a11y loss); render the question stem through `QuizQuestionText` so `Statement N:` lines break onto separate lines (the teacher preview is the only quiz view rendering raw stem text).
- **Async quiz generation** — mirror the Note → Study Pack pipeline: status field (`GENERATING` / `READY` / `FAILED`), task-executor enqueue, frontend polling. `GeneratedQuizService.generate()` is currently synchronous. Prerequisite for bulk.
- **Collection-level bulk generation** — batch the universal per-note pipeline across a collection. The hard part is **quota-aware partial execution**: generate as many as quota allows, report completed vs. blocked, upsell — never fail the whole batch.

---

## v0.28.0 - Feature Discoverability & Activation

**Status: Released**

Base branch for this release: `releases/v0.28.0`.

Theme: close the gap between **signup conversion** (strong) and **feature activation** (weak). Observed symptoms: quiz-session **export is unused**, **Challenge Quiz is underused**, and new surfaces like **Study Plans** need adoption. The headline insight is that this is an *activation* problem, not a *docs* problem — quiz-session export is **already documented in Help** ("Export & Sharing") and still goes unused, which proves pull-docs do not drive discovery. The fix is **in-flow push** through the systems we already have, not new help pages.

Locked direction:

- **Contextual nudges are the primary lever (push).** Reuse the existing `GuidanceTip` / `pickActiveGuidance` one-time-tip system (`lib/guidance-engine.ts`, `lib/guidance.ts`) — **do not build a new tips framework.** Surface each underused feature at its moment of relevance:
  - **Export** → one-time tip on the quiz **review screen** ("Export this review as PDF to study offline / share"). _(shipped)_
  - **Study Plans** → one-time tip once a user has *N* notes ("Group related notes into a Study Plan"). _(shipped)_
  - **Challenge Quiz** → no dedicated tip. The Quick Review completion screen already drives it via `PostSessionNextStep` + a fallback "Take Another Challenge" CTA (context-aware and not one-time), so a tip would be redundant — Challenge Quiz adoption is left to the Dashboard-recommendation lever below.
- **Smarter Dashboard recommendation, not a static promo.** Strengthen the existing `ContinueSpotlight` / `continueStudying` recommendation to push underused modes when contextually appropriate (e.g., a user with quiz-ready notes who hasn't tried Challenge Quiz). A permanent top-of-Dashboard "Try Challenge Quiz" banner was **explicitly rejected** — banner-blindness, and the Dashboard already recommends Challenge Quiz.
- **Help reference completeness (table-stakes pull).** Add the missing **Study Plans / Collections** Help topic and audit Help for other gaps. Necessary for completeness, but not the adoption driver — bundled here, not shipped separately.
- **Instrument the adoption funnel.** An activation initiative without measurement is guessing. Track tip impression → click → feature use via the existing `AnalyticsEventType` enum (e.g., around `CHALLENGE_QUIZ_STARTED`) so we can tell what moves the needle.
- **Study Plan progress rollup** (see v0.27.0 → Deferred) pairs with this theme — it turns a plan from a folder into a *trackable unit* through detail-only, read-only aggregation of Study Pack readiness and completed-practice signals. _(shipped)_
- **Optional / later bet:** an onboarding-style **activation checklist** ("Create a note ✓ · Generate a Study Pack ✓ · Take a Challenge Quiz ☐ · Export a review ☐") to drive multi-feature activation.

Anti-drift: one-time, dismissible, contextual tips only — route every new tip through `pickActiveGuidance` (do not add ad-hoc one-time tips); no new infrastructure; add any new analytics events to the `AnalyticsEventType` enum (Java + frontend) before firing.

---

## v0.27.0 - Material Import & Collections

**Status: Released**

Theme: lower the cold-start barrier for getting existing study material *into* NoteLib, and let any learner group notes into a reusable, ordered **collection**. The trigger was preparing the app for teachers (we have none yet, and want the teacher path to be effortless before we recruit them) — but every capability here is built profile-agnostic at the core, so students, board exam reviewers, and professionals get the same import-and-organize speed. The teacher-specific payoff (combined exam packet + shareable links) is a profile-aware terminal action layered on a universal spine, not a separate system.

### Why this release

Today a new user — teacher or otherwise — who already has material (lecture notes, reviewers, textbook chapters, handouts) must create notes one file at a time, and has no way to group related notes into a unit they can return to. Two gaps:

1. **Import is single-file.** A teacher with a unit's worth of material, or a student with a semester of lecture notes, must repeat the import flow per file. The OCR/import pipeline already handles any one file well; the friction is purely the one-at-a-time loop.
2. **There is no way to group notes into a reusable, ordered set.** Exam Builder (teacher/admin) already combines multiple notes into a sectioned DOCX *ad hoc*, but the selection is throwaway — nothing is saved, named, or reusable. Students and board reviewers have no grouping concept at all beyond filters.

### Design principle (anti-drift — governs the whole release)

**One universal spine, profile-aware framing. A collection is a playlist over existing notes — never an AI-synthesized document.**

- **Bulk import and collections are profile-agnostic.** No profile gate on either. The teacher payoff is an *additional* terminal action, not a fork of the data model.
- **A `NoteCollection` is a saved, named, ordered grouping of existing notes** — title, optional description, ordered note references with an optional per-item label (week / topic / section). It is *not* a new content type and carries no generated content of its own.
- **No collection-level AI generation, no new quota category.** Study Pack and Quiz generation stay per-note on existing quotas. A collection never synthesizes across its notes (Option B from the prior "Lesson Plan for Teachers" planning stays deferred — risk of low-quality synthesis and higher LLM cost with no proven demand).
- **Notes stay independently owned and editable.** A note may belong to multiple collections; deleting a collection never deletes its notes; one note can appear in many collections.
- **Profile-aware label + terminal action follow the existing Study Focus / Exam Focus pattern** (one mechanism, profile-typed copy) — do not fork the entity per profile:

  | Profile | Collection label | Primary terminal action |
  |---|---|---|
  | TEACHER | "Lesson Plan" | Combined sectioned DOCX (Exam Builder) + shareable student quiz links |
  | STUDENT | "Study Plan" | Study the set; generate a quiz per note on existing quota |
  | BOARD_EXAM | "Review Set" | Practice across the set (feeds existing multi-note Long/Board Exam) |
  | PROFESSIONAL | "Collection" | Study the set; generate per-note |

- **Uploading a lesson-plan *document* as quiz source is explicitly out of scope.** A lesson plan is a teaching scaffold (objectives, activities, standards) that *references* content rather than containing it — it is a weak quiz source. The quizzable material is the teacher's notes/handouts, which bulk import + per-note generation already serve. If a lesson plan / syllabus is ever used, it is as a *structure outline* (section labels), never as the content the quiz is generated from. Deferred until a real teacher asks.

### Track 1 — Bulk material import (universal, P0)

Let a user select multiple files at once; each file becomes one note (`DRAFT`). Reuse the existing per-file OCR/import pipeline unchanged — this is a batch wrapper and an import UX change, not a new ingestion path.

- Available to **all** profile types; no gate.
- Each imported file → one `NoteEntity` (`DRAFT`), titled from filename/heading, ready for the normal Generate flow.
- Per-file success/failure surfaced individually (one bad file must not fail the batch); failed files are skippable/retryable.
- No automatic Study Pack generation on import — import creates notes only; generation stays an explicit, quota-consuming user action (preserves the "never auto-regenerate / explicit generation" rule).

**Routing:** multi-file import touches the upload pipeline + note creation across several files → **Codex prompt**.

### Track 2 — Note Collections (universal entity, profile-aware framing, P1)

New lightweight grouping entity with profile-typed presentation.

- New `NoteCollection` entity + ordering join table (migration): `id`, `user_id` (owner), `title`, `description?`, and an ordered list of `(note_id, label?)` items.
- CRUD: create, rename, reorder items, add/remove notes, delete collection (owner-only; never cascades to notes).
- Collections surface in the app shell for every profile; label and the primary CTA resolve from profile type per the table above (reuse the profile-aware copy mechanism, do not hardcode profile checks in components — mirror `exam-mode-visibility.ts` / Study Focus framing).
- No new quota category; no collection-level generation.

**Routing:** new entity + migration + endpoints → **Codex prompt**. Profile-aware labels/CTAs and the collection list UI shell are Claude Code-sized once the API exists.

### Track 3 — Teacher terminal path (profile-specific, builds on shipped infra)

Wire a Collection into the existing **Exam Builder** so a teacher converts a saved Lesson Plan into a sectioned DOCX packet + shareable student quiz links in a couple of clicks — instead of re-selecting notes each time.

- Collection items pre-populate Exam Builder sections (the per-item label seeds the section title).
- Everything downstream is already built: sectioned DOCX, answer keys, 1–3 anti-cheat versions, `/quiz/[token]` shareable links. This track is wiring, not new export logic.
- DOCX export and shareable-link generation stay **Teacher/Admin only** (unchanged plan/profile gating).

**Routing:** mostly wiring an existing builder to a new data source → Claude Code, pending the Track 2 entity.

### Track 4 — Profile-aware first-run / activation (cross-profile)

Make the empty state teach the loop for *that* profile instead of a generic "create a note."

- Teacher: *Bring your unit's material → Generate quizzes → Export or share to students.*
- Student: *Bring your notes → Generate a Study Pack → Study & quiz yourself.*
- Board exam: *Bring your reviewers → Generate → Practice & track mastery.*
- Reuse the existing `GuidanceTip` / onboarding surfaces — no new tips framework (`pickActiveGuidance()` stays the single entry point).

**Routing:** frontend empty-state + copy, profile-gated via existing mechanism → Claude Code.

### Deferred

- **Study Plan progress rollup (next-release lead candidate)** — let a Study Plan surface *aggregate* progress over its own notes (e.g., "4 of 6 notes have Study Packs · avg mastery 62% · 3 weak concepts across this plan"), turning a plan from a folder into a trackable unit. This is **read-only aggregation of existing per-note signals** — not collection-level AI, not a new quota, not mastery *generation*; the underlying tracking stays the Study Goal / Progress system. Out of scope for v0.27.0 (needs a backend rollup); flagged as the top candidate for the next release. Keeps the "a collection is a playlist" rule — the plan displays progress, it does not own a new mastery model.
- **Lesson-plan / syllabus document parsing as quiz source** — scaffold-vs-source problem above; revisit only if a real teacher requests it.
- **Collection-level AI synthesis (Option B)** — one synthesized document across all notes in a collection; deferred for quality/cost reasons, unchanged from prior planning.
- **Collection sharing / public collections** — a collection is owner-private in v1; shareable collections (a "course pack" a teacher publishes) is a later bet once collections see use.
- **Per-profile structured presets beyond Exam Builder's existing ones.**

### Task routing summary

- **Codex:** Track 1 (multi-file import), Track 2 (`NoteCollection` entity + migration + endpoints). Both cross the new-infrastructure / multi-file thresholds.
- **Claude Code:** Track 3 (wire collection → Exam Builder), Track 4 (profile-aware empty states), and the profile-aware label/CTA layer on the collection UI once the API exists.

### Anti-drift notes

- A collection is a **playlist over existing notes** — never a synthesized document and never a new content type. No generation at the collection level.
- **No new quota category** — generation stays per-note on existing Study Pack / quiz quotas.
- Profile-aware label/CTA via the existing profile-typed copy mechanism (like Study Focus → Exam Focus) — **do not hardcode profile checks in components** and do not fork the entity per profile.
- Bulk import creates notes only — **never auto-generates** Study Packs (preserves the explicit-generation rule).
- DOCX export and shareable quiz links stay **Teacher/Admin only** — bulk import and collections being universal does not widen those gates.
- Deleting a collection must never delete its notes; a note may belong to multiple collections.
- Use `globalThis` for browser globals; add any new analytics events to the `AnalyticsEventType` enum (Java + frontend) before firing.

---

## v0.26.1 - Guidance System

**Status: Released**

Theme: make NoteLib's most useful — but least self-explanatory — features teach themselves. The Goal / Study Focus / Milestones loop and the Exam Hubs shipped in v0.25–v0.26 with strong mechanics but no in-app explanation. This release builds a reusable guidance mechanism and applies it to those two highest-pain gaps. Guidance only — no behavioral or feature changes (in particular, no term-reset feature: current behavior is documented honestly).

### Scope

**Mechanism (reusable):**
- Deep-linkable Help guides via URL hash (`/help#<guide-id>`); hash over query param to avoid a Next.js `useSearchParams` Suspense build de-opt.
- Inline "gist + How this works →" pattern: a one-sentence inline explanation co-located with a complex feature, plus a persistent deep-link into the relevant Help guide. Reference-grade and re-readable — distinct from the one-time dismissible `GuidanceTip` (kept for "this feature exists" discovery nudges only).

**Two highest-pain gaps:**
1. **Progress & Study Focus guide** — Goals, Study Focus (subject multi-select), Milestones, mastery calculation, and the honest new-term answer. Profile-type aware (TEACHER has no Study Focus; BOARD_EXAM = "Exam Focus"; STUDENT = "Study Focus"). Inline gist + deep-link on the Progress Milestones card and the Profile Study Focus section.
2. **Exam Hubs guide** — what `/exam/ale|pnle|let` are, how they curate public notes, and how to reach them.

### Deferred to v0.26.2+

Guidance coverage for the remaining surfaces (quiz modes, study packs, export & sharing, exam-cycle pass, post-quiz nudges). The mechanism built here extends to them later; this release intentionally scopes to the two confirmed gaps.

### Task routing

- **Claude Code:** authors both Help guide components (info-design), the Help-page hash deep-link, and the two inline gist + link placements (Progress Milestones card, Profile Study Focus section).
- **Codex:** only if inline-link placement sprawls beyond those two known slots (dashboard / library / note-detail), in which case the placement pass crosses the >5-file / >100-LOC threshold and gets a Codex prompt.

---

## v0.26.0 - Exam Depth

**Status: Released**

Theme: expand the exam capture surface with wave-2 exam hubs, deepen the goal progression loop with mastery-threshold milestones, and give board exam takers a pricing commitment that matches how they actually prep — a 90-day exam-cycle pass. All three reinforce the same user: a board exam taker preparing for a specific cycle.

### Track 1 — Exam Hub Surface

Four pieces, all Claude Code–sized (no Codex prompt needed):

**1. `/exam` index redesign**

Restyle the exam hub index cards to match the Help page card pattern: icon badge (top-left) + title/description beside it + "Browse [Exam] notes →" link at the bottom (ArrowRight icon). Icon map defined locally in the page (not in `exam-hub-config.ts`):
- ALE → `PenTool`
- PNLE → `Heart`
- LET → `GraduationCap`
- Wave-2 additions get a relevant icon when added.

**2. Landing page exam hub entry**

Add a prominent exam hubs entry point to the marketing landing page (`/app/page.tsx`) so `/exam` is discoverable without scrolling to the footer. Exact placement and form TBD at implementation time.

**3. Progress page link fix**

`NextStudyCard` in `progress-report-client.tsx` only routes to `/exam/[slug]` when `goalType === "EXAM"`. Goals set from the Profile chip picker are `SUBJECT` type — so Architecture → public library instead of `/exam/ale`. Fix: call `getExamSlugForCourseProgram(goalSummary.studyGoal)` as a fallback; if it returns a slug, route to `/exam/[slug]` regardless of `goalType`.

**4. Wave-2 exam hubs**

Extend `/exam/[slug]` to the next exam tier. The v0.25.0 page template reuses unchanged; implementation is new entries in `frontend/lib/exam-hub-config.ts`.

**Content gate (required before launching any wave-2 hub):** 20+ public notes per exam. High School/SHS excluded — not licensure exams. Partial launch allowed (add each hub independently as it clears the threshold). Currently deferred — no wave-2 candidate meets the threshold.

| Exam | `courseProgram` mapping | Status |
|---|---|---|
| **CPALE** | Accountancy | Verify current count |
| **Engineering** | Civil / Electrical / Mechanical Engineering | Verify current count per discipline |
| **Pharmacy** | Pharmacy | Verify current count |
| **Physical Therapy** | Physical Therapy | Verify current count |
| **CSE** | Civil Service / Computer Science | Verify current count |

### Track 2 — Mastery-Threshold Milestones

Deepen the `/progress` goal view beyond the v0.25.0 shipped goal summary and next-study suggestion. Add visible milestone markers tied to mastery thresholds (e.g. "70% of Pharmacology concepts mastered", "All key concepts reviewed at least once") inside the goal progress section.

**Anti-drift (locked from v0.24.0/v0.25.0):** milestones must be derived from `ConceptHealth` mastery data — never a generated syllabus, never a progression system not rooted in actual quiz performance.

Backend aggregation required → Codex prompt.

### Track 3 — Exam-Cycle Pass (Pro Season Pass)

New 90-day Pro access tier targeted at board exam takers committing to a specific prep cycle.

- **Price:** ₱599 PH (vs ₱747 for 3× monthly — a meaningful seasonal discount for the review window)
- **Duration:** 90 days from purchase date (`endAt`-based, same as existing PREPAID grants)
- **Access:** full Pro entitlements for the duration
- **Monthly quotas:** still apply and reset monthly regardless of billing cycle (`BillingUsageResetJob` is billing-cycle-agnostic; LLM cost exposure stays bounded at 100 packs/month)

Implementation lift is small — the system is already PREPAID with `endAt`-based grants:
- New `EXAM_CYCLE` value in `BillingCycle` enum
- New pricing config entry in `application.yaml` (`duration-days: 90`, `amount: 599`)
- New checkout option in frontend billing UI
- Xendit integration unchanged (creates fresh invoices, not subscription objects)

### Track 4 — Subject-Level Focus & Profile-Aware "What's Next"

Deepen the goal-setting and progress loop by letting learners set focus at the *subject* level (e.g. "History of Architecture", "Pharmacology") rather than the broad course-program level — removing the confusing redundancy with the Learning Profile section and giving the Progress report a more precise target.

**Why this fits v0.26.0:** subject infrastructure already fully exists (`NoteEntity.subject` is AI-inferred, `SubjectProgressEntry` is already returned and rendered on the Progress page, `/subjects?scope=mine` endpoint already live). This track surfaces it through the Study Focus UX and makes it multi-select.

**Key design decisions:**
- **New `focusSubjects text[]` column** (V71 migration) alongside the existing `studyGoal` text column — they are not merged.
- **Mutual exclusivity from the Profile UI** — setting subjects via the picker clears `studyGoal`; the exam hub intent flow still sets `studyGoal` directly (unchanged).
- **Goal priority** in `ProgressReportService`: `studyGoal` (exam slug or course program) takes precedence; `focusSubjects` is used as goal source only when `studyGoal` is null.
- **Combined rollup model** — multi-subject focus aggregates mastery across all selected subjects into one goal summary (no independent per-subject goal tracks).
- **Profile-type-adaptive framing** — hidden for TEACHER; "Exam Focus" for BOARD_EXAM; "Study Focus — subjects you're preparing for this term" for STUDENT.
- **No K-12 curated subject lists** — the Progress page reflects only what the user has notes for; prescribing subjects they haven't studied yet (from a DepEd/PRC reference list) is deferred to v0.27.

**Retention hypothesis being tested:** learners who don't know what to study next churn. The actionable empty state (weakest subjects surfaced as one-click chips) and the `weakestGoalSubject` CTA in `NextStudyCard` test that hypothesis without a curated curriculum.

Codex prompt: `docs/codex-prompts/v0.26.0-subject-focus-multi-select.md`

---

## v0.25.1 - Polish & Quick Review Fixes

**Status: Released**

Theme: targeted polish pass covering Quick Review multi-select UX (two-step Submit/reveal flow), Public Library filter hierarchy and cascading (For → Course/Program → Subjects → Tags → Source), quiz question newline rendering, profile Study Focus chip cap, and minor label/layering issues.

---

## v0.25.0 - Exam Capture & Goal Setting

**Status: Released**

Theme: turn the marketing traffic NoteLib already earns into signed-up, activated learners — give the exam communities we post into (PNLE, LET, ALE, and beyond) a destination that says *"here's everything for your board exam,"* and give every new learner a **goal** that turns the progress report into a place they're trying to reach. Two tracks, one funnel: **exam page → signup → goal → progress toward goal → back to the community notes.**

### Why this release

The funnel leak is unambiguous and unchanged from v0.23.0 — it has only grown more lopsided as the marketing engine works:

| Signal | Value | Read |
|---|---|---|
| Public note views | 2,613 | The acquisition engine works — the ALE / PNLE / LET community posts drive real traffic |
| Public copies | 5 | Almost none of that traffic converts to any account action |
| Total users | 29 | The entire registered base is a rounding error next to the traffic |
| Activation rate | 55.6% (15 of 27) | Healthy — when users verify, most generate a pack |
| Median days to first pack | 0 days | When they activate, they do it immediately |
| Value loop closure | 50.0% (8 of 16) | Acceptable — half who generate a pack quiz within 7 days |
| Free quota hit rate | 0.0% (of 25) | Nobody is near a limit — quota tuning is a no-op |
| Paywall conversion | 0.0% (of 6) | Downstream and tiny; not the leak |

~2,613 anonymous readers produced ~29 accounts (≈1%). The middle of the funnel is healthy; the leak is purely **capture**. Today the destination for a marketing post is a *single public note* with only "Quiz yourself" and "Copy" CTAs — there is no exam-level entry point that frames the full body of relevant notes, and no destination for a new signup to aim at once they're in.

The two tracks attack the same funnel at adjacent points: Track 1 captures the reader; Track 2 gives the new account a reason to come back and a path back into the community notes.

> **Sequencing note (deliberate, owner-approved):** the v0.24.0 roadmap gated Goal + Milestones on *"P0/P1 demonstrably lifting retention first."* That data does not exist yet — the v0.24.0 learning loop shipped days ago. We are building Track 2 **ahead of that gate on purpose**, because the goal is the activation hook that makes Track 1's capture worth more, not an isolated retention bet. If v0.24.0 retention data comes back weak, revisit the depth of Track 2 before investing further.

### Design principle (anti-drift — governs both tracks)

**An exam is not a new entity, and a goal is not a curriculum. Both are curated/derived views over data the app already stores.**

- **An exam landing page** is a curated view over existing public notes, keyed on `courseProgram` + subject — *not* a new table, content type, or note kind. Reuse the `/public/library/[subject]` server-rendered pattern (SSR, `revalidate`, `buildPageMetadata`, structured data, featured/popular/recent discovery sections).
- **`courseProgram` is free text, but production values are clean and canonical** (confirmed by the data audit below). Exam pages resolve through a **config alias map** (clean slug → the `courseProgram` value[s] for that exam, with rollups), never naive exact-match — and never a new entity.
- **A goal** is **suggested** from the learner's `courseProgram`/subjects — or the exam they arrived through — and confirmed by the user. Never blank-slate goal entry, never a generated syllabus. Progress and milestones are **derived** from `ConceptHealth` mastery, e.g. "70% of Pharmacology concepts mastered," not "Lesson 3."
- **The app stays universal, and exam pages are ungated.** Exam takers get a tailored discovery + goal surface; the generic public library, dashboard, and general-learner flows are unchanged. `/exam/[slug]` pages are **public and anonymous-accessible** (that is the SEO/capture value) and reachable by users of any profile type — there is **no Exam Reviewer profile gate**. Profile type drives in-app emphasis only, never access to a public library view. The *only* place profile interacts is **at signup**: arriving via an exam page may **suggest** an Exam Reviewer profile + a goal for that exam — suggested and confirmable, never forced.
- **Friction-free anonymous browsing stays** (carried from v0.23.0): conversion gates live only on *actions* (quiz, copy), never on reading or filtering an exam page.

#### Track 1 — Exam Capture (P0)

**1. Exam landing pages**
   - Server-rendered exam hub pages at `/exam/[slug]` — wave-1 launch set `ale`, `pnle`, `let` (wave-2 exams in a later release; see resolved decisions) — curated from public notes via the `courseProgram` config alias map.
   - SEO metadata + structured data per exam; featured / popular / recent discovery sections reusing existing `public-library-discovery` infra; an exam-context header (what the exam covers, who it's for).
   - Gives each marketing post a rich, browsable destination instead of a single note, and stands alone as an SEO surface ("free PNLE reviewer notes").

**2. Exam-aware conversion CTA + signup**
   - Primary "Start preparing for the [Exam]" CTA on the exam page → signup → land in the curated exam set.
   - Reuse the v0.23.0 quiz-first capture plumbing (copy-intent cookie, signup `redirect`, auto-start). Carry the exam context through signup so onboarding pre-selects/derives the goal (the bridge into Track 2).

**3. Curation layer (config alias map) — scope resolved by the data audit**
   - The mapping from exam slug → the `courseProgram` value(s) that constitute it (with rollups, e.g. `pnle → ["Nursing", "Medical – Surgical Nursing"]`).
   - **Resolved: a config alias map is sufficient for launch — no admin-tagging build in P0.** The production audit found `courseProgram` values are clean and canonical (not messy free-text variants), and the marketed exams map to the three largest buckets. Admin normalization (onboarding new exams, folding stragglers) is a deferred fast-follow, not a P0 blocker.

#### Track 2 — Goal + Milestones (P1) — promoted from the v0.24.0 Phase 3 deferral

**4. Set your goal**
   - Suggested from the learner's subjects / `courseProgram` (or the exam they arrived through); the user confirms. Strictly mastery-derived; no generated curriculum.

**5. Progress report becomes a destination**
   - Reframe the existing v0.24.0 `/progress` report as "progress toward [goal/exam]": % toward mastery of the track, mastery-threshold milestones. Extend, do not rebuild.

**6. Next-best-subject suggestion**
   - Reuse `courseProgram` public-note discovery to point at community content for the learner's gaps — closing the loop back into the public library and the copy/quiz flywheel.

**7. Post-quiz goal nudge** *(pending)*
   - After completing a quiz session on a subject that does not match the user's study goal, surface a nudge on the results screen: "Your [Goal] subject has X concepts due — study that next?" One-tap link to a Quick Review on the weakest goal concept.
   - Frontend-only addition to the quiz results/completion screen. No new endpoint needed if `weakestGoalSubject` is already in the session response; otherwise a small extension to the session summary.
   - Rationale: closes the tightest feedback loop — the user just practiced and is still in a learning mindset; the nudge is immediately actionable. Higher behavior-change value than a passive Dashboard widget.

**8. Dashboard goal card** *(pending, lower priority than #7)*
   - Replace the one-time `GoalPromptBanner` (which dismisses forever) with a persistent compact goal card on the Dashboard showing: goal name, current mastery %, and a "Study weakest concept" CTA.
   - Turns the goal from a setup step into a daily destination — the Dashboard becomes goal-aware rather than goal-agnostic after the first session.
   - Requires a lightweight goal summary available at Dashboard load time. Options: (a) extend `GET /auth/me` with mastery snapshot, or (b) a new `GET /users/goal/summary` endpoint. Option (b) is preferred — keeps `/auth/me` lean and lets the Dashboard fetch it independently (can be deferred or skeleton-loaded).

### Resolved decisions

- **Multi-subject study goal — explicitly out of scope.** Users set a single focus subject. The full Progress page already shows mastery across all subjects (the "see everything" view); the goal summary is the "my target" view — those are different jobs. A goal that covers everything is not a goal. Exam goals (ALE, PNLE, LET) already aggregate multiple `courseProgram` values behind one slug, giving board exam students multi-subject coverage without fragmenting the goal concept.

### Kickoff audit & data decisions

**Data audit (DONE).** 27 distinct `courseProgram` values across ~211 public notes — **clean and canonical**, not messy free-text variants. `courseProgram` is a *field of study*, not an exam name, so the mapping is **board exam → the program(s) whose graduates sit it.** The marketed channels are the three largest buckets:

| Exam (marketing channel) | `courseProgram` | Public notes |
|---|---|---|
| **ALE** (Architect Licensure) | Architecture | 46 |
| **PNLE** (Nurse Licensure) | Nursing (+ Medical – Surgical Nursing) | 44 |
| **LET** (Licensure Exam for Teachers) | Education | 31 |

Resolved from the audit:

- **Launch set — wave 1 = ALE / PNLE / LET** (121 / 211 notes, 57%). Wave-2 candidates (≥7 notes: Accountancy → CPALE, Civil / Electrical / Mechanical Engineering, Pharmacy, Physical Therapy, Civil Service → CSE) ship in a later release. Too thin to launch (≤3): Criminology, Law, Medicine, Computer Science, Psychology. Academic-level values (High School, Senior High – STEM/HUMSS/ABM, Grade School) are **not** board exams — they belong to the universal/general-learner library, not this track.
- **Curation — config alias map only; no admin-tagging build in P0** (data is clean enough; see Track 1 item 3). Admin normalization is a deferred fast-follow.
- **Route — `/exam/[slug]`** (`/exam/ale`, `/exam/pnle`, `/exam/let`), consistent with the profile vocabulary. The page is a board-exam *discovery hub*, distinct from the *Board Exam Mode* / *Long Exam Mode* quiz modes — keep on-page/component naming clear of the quiz-mode terms.

---

## v0.24.0 - Guided Learning

**Status: Released**

Theme: turn NoteLib from a tool you *operate* into a study companion that shows **direction and progress** — finally closing the learning loop (study → assess → see gaps → targeted next action → repeat) the product has promised but never visibly delivered.

### Why this release

The dead-end is real: a learner copies a note, runs a Challenge Quiz, aces it — and then doesn't know what to review next. Exam reviewers feel this most. But an audit found the loop's halves **already exist and just aren't surfaced or closed at the right moments**:

- `Today's Focus` card (`TodayFocusType`: RESUME_REVIEW / RETRY_REVIEW / REVIEW_PACK / PRACTICE_WEAK_CONCEPT / STUDY_SUGGESTION) already computes a next action.
- `Focus Areas` card already shows per-concept accuracy bars.
- `ConceptHealthEntity` already tracks per-user/per-concept mastery (spaced-repetition: `lastCorrectAt`, `isDue`, `daysSinceReview`).
- Adaptive Practice already acts on weak concepts.

What's missing: the loop isn't **closed at the post-quiz moment**, the act-on-weakness step is **paywalled** for free users, there is **no consolidated progress report by subject/topic**, and there is **no goal/destination** to orient progress.

### Design principle (anti-drift — governs the entire learning loop)

**Build on the concept-mastery spine that already exists; never build a content/curriculum system.**
- **Goals** = an existing `courseProgram` / subject set that NoteLib **suggests** and the user confirms — never a blank-slate goal entry, never a generated syllabus.
- **Progress and milestones** are **derived** from `ConceptHealth` mastery + `courseProgram` — e.g. "70% of Pharmacology concepts mastered," not "Lesson 3: The Nephron."
- NoteLib must never generate topics, lessons, or curriculum content. The loop organizes the learner's own notes + community notes by their own performance data.

#### P0 — Close the loop (Phase 1)

**1. Post-session next-step handoff**
   - At the quiz/session **results screen**, surface the session's weakest concepts + a one-tap next action (re-review missed, practice weak concepts). Close the loop *in-context*, not only on a dashboard the learner may not revisit.
   - Reuse `ConceptHealth` + the existing `TodayFocusType` actions; no new content.

**2. Free Adaptive Practice allowance** *(✅ shipped)*
   - Give the FREE tier a small monthly Adaptive Practice allowance so the act-on-weakness step isn't a locked door. Without this the loop dies at the diagnosis for most users.
   - Shipped as `3` Free sessions / month with quota-driven `FeatureGateService` availability, plan/pricing/landing copy updates, and over-quota paywall copy that says upgrade for more sessions rather than Adaptive Practice requires Pro.

#### P1 — Progress report (Phase 2)

**3. "My Progress" view**
   - Aggregate `ConceptHealth` **by subject/topic**: mastery %, strong vs struggling, due-for-review counts. The subject/topic awareness learners ask for, built on data already stored.
   - Backend: one aggregation query (concept → note → subject). Frontend: a progress/report view (extend `Focus Areas` into a full page).

#### P2 — Supporting

**4. Full study-pack copying**
   - Copying a public note copies its generated content (summary, key concepts, quiz) for **instant value**, with an optional "tailor to my level" regenerate. Still excludes session history + concept health (those are personal).
   - Feeds the loop with content to study toward a goal. Depends on the v0.23.1 quiz-format fix being live (so copies don't propagate malformed questions). Reverses the long-standing "copy excludes generated fields" rule for public-note copies — a deliberate, documented change.

**5. Guardian demand test**
   - A "Parents & Guardians — coming soon" CTA on the landing page firing a `GUARDIAN_INTEREST` analytics event; a signal-only waitlist, NOT the Guardian flow. Pre-commit a build/no-build threshold. (Full spec: prior planning.)

#### Phase 3: Goal + milestones — promoted to v0.25.0

Originally gated on P0/P1 demonstrably lifting retention first; **promoted into v0.25.0 Track 2 ahead of that gate** as a deliberate, owner-approved decision (see the v0.25.0 sequencing note). Full spec now lives under v0.25.0.
- "Set your goal" — **suggested** from the learner's subjects / `courseProgram`, user confirms.
- Goal turns the progress report into a destination: % toward mastery of the track, mastery-threshold milestones, and a "next best subject to study" suggestion that reuses `courseProgram` public-note discovery to point at content for the gaps.
- Strictly mastery-derived; no generated curriculum.

---

## v0.23.0 - From Readers to Learners

**Status: Released**

Theme: convert the public library's anonymous reading traffic into signed-up, activated users. The acquisition engine already works — what's missing is the capture step that turns a reader into a learner.

### Why this release

Production funnel data (as of release kickoff) makes the leak unambiguous:

| Signal | Value | Read |
|---|---|---|
| Public note views | 2,149 | The SEO / public-library acquisition engine works — real traffic is landing |
| Public copies | 1 | Almost none of that traffic converts to any account action |
| Verified users | 21 | The entire registered base is a rounding error next to the traffic |
| Activation rate | 57.1% | Healthy — when users sign up, most generate a pack |
| Median days to first pack | 0 days | When they activate, they do it immediately |
| Value loop closure | 58.3% | Healthy — most who generate a pack start a quiz within 7 days |
| Free quota hit rate | 0.0% | No free user is near any limit — quota changes are a no-op |
| Paywall conversion | 0.0% (of 5) | Downstream and tiny; not the leak |

The middle of the funnel (activation → value loop) is healthy. The problem is purely **capture**: ~2,149 anonymous readers produced ~21 accounts (≈0.05%). The only conversion point offered to a first-time reader today is "Copy this note" — a library-management action a visitor neither has context for nor wants yet. The natural first action for someone reading a study note is *"quiz me on this,"* not *"file this away."*

If even 5% of public-note viewers became signups, the registered base would grow ~5x — which dwarfs any quota or paywall tuning. This release targets that single leak.

Design constraint carried over from v0.22.0: **friction-free anonymous browsing stays.** No interstitials or sign-up walls on *reading* or *filtering*. The conversion gate lives only on *actions* (quiz, copy, like) — consistent with the existing "login gate on write actions only" rule.

#### P0 — Capture the public traffic (core theme)

> **Scope correction (post-audit):** a code audit at the start of P0 found the capture *plumbing* was already built — copy-intent cookie, signup `redirect` param, `?copy=1` auto-copy on return, and `generate=1`+`startQuickReview=1` auto-generate/auto-start Quick Review all existed. Google OAuth is popup-based (`ux_mode: "popup"`), so there is **no redirect round-trip to preserve** — original item 3 was a non-problem and is dropped. Email verification is **not** gated on generate/quiz endpoints, so the instant-quiz promise holds. The real gap was purely CTA framing, which is honored by the existing note-first / SEO rule (see `docs/features/public-notes.md`).

**1. Quiz-first conversion CTA (✅ shipped)**

   Reframe the public note detail conversion from "Copy" to "Test yourself," without changing the note-first page hierarchy (SEO preserved).

   - "Quiz yourself on this note" is now the primary CTA on the conversion card and the mini-quiz completion screen; copy/generate demoted to secondary
   - Routes through the existing copy → instant Quick Review flow (anonymous tap → free signup → auto-copy → auto-generate → auto-start Quick Review)
   - Added `PUBLIC_NOTE_QUIZ_YOURSELF_CLICKED` analytics event to measure CTA lift
   - Parameterized `PublicSeoCopyCta` (`action`, `analyticsEvent`, `authModalTitle/Body`, `variant`); no new quiz infrastructure

**2. Anonymous mini-quiz taste (already present; CTA reframed)**

   The `public-mini-quiz-preview` component already lets anonymous readers answer up to 3 sample questions, then surfaces the conversion CTA on completion. The completion CTA was reframed to quiz-first as part of item 1. Free-question count left at 3 deliberately — more free content supports the dwell-time / SEO that drives views; do not choke it.

**3. ~~Preserve intent through signup / OAuth~~ (dropped — non-problem)**

   Google auth is popup-based, so the page URL and copy-intent cookie never leave during auth. No redirect-intent work needed.

#### P1 — Grow what's working

**4. Public-note share & SEO polish (✅ shipped)**

   Widen the top of the funnel by leaning into the discovery that already converts to views (the top public note has 156 views; Nursing is the leading subject).

   - ✅ Dynamic per-note Open Graph share card (`opengraph-image.tsx`): title, subject, `{N} practice questions · Quiz yourself`. `generateMetadata` drops the static default so the file-convention card wins; Twitter falls back to the same `og:image`.
   - ✅ Canonical SEO paths + structured metadata verified — Article JSON-LD was **already present** on note pages (`buildArticleStructuredData`) and CollectionPage JSON-LD on subject pages; OG/Twitter/canonical already complete via `buildPageMetadata`. Only the dynamic share image was missing.
   - Frontend-only; no backend data changes. Verified via production build (`next/og` compiles under `--webpack`).

#### P2 — Low-cost generosity (deprioritized)

**5. Free topic-note-generation 5 → 10 (✅ shipped)**

   Raised `freeMonthlyNoteGenerationLimit` from 5 to 10 (Java default + `application.yaml`).

   - **Not a conversion lever** — production free-quota hit rate is 0.0%, so no current user is constrained by the cap
   - Shipped only as cheap activation goodwill / headroom insurance; config-only change, no frontend or test changes (limits read dynamically; pricing copy is "Limited")
   - Free Adaptive Practice was explicitly considered and **declined** for this release — it doesn't address the capture constraint, erodes the paid differentiator while we're building conversion, and is a packaging change (plans/pricing/landing cascade), not a quota tweak. See Deferred below.

#### Deferred — revisit after capture improves

- **Free Adaptive Practice allowance** — a retention lever, but current value-loop closure (58.3%) and Adaptive Practice usage (2 sessions) do not justify prioritizing it now. Revisit once the registered base grows and retention becomes the binding constraint.

---

## v0.20.0 - Conversion & Re-engagement

**Status: Released**

Theme: bring inactive users back and close account security gaps — re-engagement campaigns, forgot/change password, richer AI summaries, and public profile polish.

### ✅ Shipped

- **Re-engagement campaign (Admin)** — one-time admin email blast targeting users inactive 30+ days, segmented by profile type.
- **Quiz header polish** — note title in Quick Review and Challenge Quiz top bars; Long Exam sources banner for multi-note sessions.
- **Study Pack summary enrichment** — AI summary now includes optional markdown comparison tables and a Common Misconceptions paragraph; frontend renders via `react-markdown` + `remark-gfm` across all surfaces.
- **Teacher In-App Guided Tips** — five one-time contextual tips covering dashboard intro, note content quality, Generate Quiz modal, library multi-note checkboxes, and DOCX export. All use existing `GuidanceTip` + `hasSeenTip()` system; confirmed already in codebase prior to v0.20.0 planning.
- **Profile-Aware Landing Page** — `ProfileLearningSection` with interactive profile tabs (Students / Exam Reviewers / Teachers / Professionals), per-profile taglines, steps, mode chips, and screenshots. `HowItWorksSection` and `ProfileShowcaseSection` already replaced; confirmed in codebase prior to v0.20.0 planning.

### 🔲 Pending Codex

1. **Forgot Password + Change Password** — closes the re-engagement loop: users receiving re-engagement emails must be able to get back in even if they've forgotten their password, and password-auth users should be able to rotate it once they're back. Two scopes:
   - **Forgot password flow**: token generation, reset email, `/forgot-password` and `/reset-password` pages. No backend endpoint or frontend page currently exists.
   - **Change password**: update-password endpoint (current password verification + new password), form in the Profile page sign-in methods section. `passwordEnabled` field already in `SignInMethodsResponse`; no action exists yet.
   - Delete Account (stub in Settings as "Coming Soon") deferred to v0.21.0 — lower urgency for this theme.

2. **Post-signup copy-note → instant quiz flow** — new signups from a public note page skip onboarding and land directly in a Quick Review on the copied note. Requires `copyIntent` param surviving OAuth redirect. Codex prompt to be written when tokens are available.

### 🔲 Deferred (Study Pack section improvements — items 2 & 4)

These were scoped during v0.20.0 planning but blocked by a v0.18.0 constraint:

| Item | Status | Reason |
|---|---|---|
| Common Misconceptions | ✅ Shipped (v0.20.0) | Embedded in summary via markdown — near-prompt-only |
| Comparison Tables | ✅ Shipped (v0.20.0) | Same — markdown rendering in summary enables this |
| Richer Quick Recall | ❌ Deferred | Would change `keyConcepts` string format — `conceptHealthByName` in v0.18.0 keys on exact concept strings; changing them orphans all existing health records |
| Concept Relationships | ❌ Deferred | Same constraint — any format change to `keyConcepts` breaks concept health tracking |

Richer Quick Recall and Concept Relationships need a dedicated `keyConcepts` migration strategy (version the health records or re-key on concept ID instead of string) before they can ship safely.

---

## v0.21.0 - Personalized Discovery & Library Organization

**Status: In Progress**

Theme: surface community notes relevant to each user's study track and let them save and reuse their own filter shortcuts — making the app feel personal from day one.

### Why this release

Three gaps appeared after v0.20.0:

1. **The Dashboard feels generic for exam reviewers** — users studying for a specific exam (PNLE, NMAT, board exams) have no fast path to community notes for their track. The public library already supports `courseProgram` filtering; surfacing it on the Dashboard turns an existing inventory into a personalized discovery feature with near-zero backend work.

2. **Private library filters are manual every time** — v0.18.0 shipped URL-based filter persistence, but users who study across multiple subjects still re-apply the same filter combinations on every session. Named saved filters close the loop without requiring note reorganization.

3. **Public profiles cap at 8 notes with no escape path** — a prolific creator has no "see all" link. A `creator` filter on the public library — already designed to be the canonical discovery place — fixes this with a single backend query addition and one frontend link.

### Primary focus

1. **Public Library creator filter + profile "View all" link** *(deferred from v0.20.0)*

   Add `creator` (username) as a query param to `GET /notes/public`. Once the backend filter exists, add a "View all X notes →" link to the public profile page (visible only when `publicNotesCount > 8`) that navigates to `/public/library?creator=<username>`.

   - Backend: join `users.username = :creator` on the existing public note query; `creator` is optional and combinable with other params
   - Frontend: add `PUBLIC_LIBRARY_CREATOR_QUERY_PARAM = "creator"` to `public-library-url.ts`; update `PublicLibraryUrlFilters` type, `buildPublicLibraryUrl`, and `parsePublicLibraryFilters`
   - Public Library UI: when `?creator=` is present, show an active "By @username" filter badge; clearing it removes the param
   - Public profile page: "View all X notes →" link rendered when `profile.publicNotesCount > 8`; link builds to `/public/library?creator=<username>`
   - Codex prompt: `docs/codex-prompts/v0.21.0-creator-filter-view-all.md`

2. **Remove Learning Focus subject badges from public profile** *(deferred from v0.20.0, blocked on item 1)*

   Remove the subject badge list (`subjects.map(SubjectBadge)`) from the Learning Focus section of `public-profile-page-client.tsx`. Keep the `learningFocusSummary` sentence. The "View all notes →" creator filter link replaces badge-based subject browsing. Frontend-only; handled by Claude Code after item 1 commits.

3. **Community Notes dashboard section** *(new)*

   New section on the Dashboard visible to all profile types, placed below Recent Notes. Title: "Notes for [CourseProgram]" (e.g. "Notes for PNLE"). Shows up to 4 public notes from `GET /notes/public?courseProgram=<value>&size=4`. Footer link: "See all in Public Library →" navigates to `/public/library?courseProgram=<value>`.

   - Visible to STUDENT, BOARD_EXAM, TEACHER, and PROFESSIONAL profiles
   - When `courseProgram` is set and matching notes exist: show up to 4 cards
   - When `courseProgram` is set but no matching public notes exist: hide the section entirely
   - When `courseProgram` is not set: render a placeholder card with a modal CTA — "Set your Course/Program to see notes tailored for your review track" with "Go to Learner Profile" (primary, `/profile#learning-profile`) + "Cancel" (secondary)
   - Requires adding an optional `size` param (max 50, default 20) to `GET /notes/public`
   - Note cards reuse the shared public library card layout
   - Codex prompt: `docs/codex-prompts/v0.21.0-course-program-dashboard.md`

4. **Saved Filters for private library** *(new)*

   Users can save a named snapshot of the current private library filter state and re-apply it with one click. Backend-persisted from the start.

   - New migration `V68__user_library_filters.sql`: table `user_library_filters` with `id` (UUID PK), `user_id` (FK → users), `name` (VARCHAR 100), `filter_state` (JSONB), `created_at` (TIMESTAMPTZ)
   - `filter_state` shape: `{ search?, subject?, courseProgram?, tags?, status?, sort? }` — mirrors private library URL params
   - Endpoints: `GET /library-filters` (list user's saved filters), `POST /library-filters` (create), `DELETE /library-filters/{id}` (delete, owner-only)
   - Frontend: "Save filter" button in the filter bar visible when at least one filter is active; opens a name input dialog on click; submitting calls the backend
   - Saved filters accessible from a dropdown or list in the filter bar; clicking applies all params; trash icon deletes
   - Scope: private library only; public library saved filters deferred
   - Codex prompt: `docs/codex-prompts/v0.21.0-saved-library-filters.md`

5. **Admin funnel metrics page** *(new — conversion visibility)*

   New admin-only `/admin/funnel` page showing the five most critical funnel health numbers. All queries run against existing tables — no new event tracking or analytics SDK required.

   | Metric | What it reveals |
   |---|---|
   | Signup → first Study Pack (% + median days) | Activation rate — are users reaching the core value? |
   | Notes with 0 Study Packs after 7 days | "Stuck before generation" pool |
   | Free quota hit rate | Are free users even reaching the paywall? |
   | Paywall seen → upgrade (%) | Is the paywall converting at all? |
   | Study Pack generated → quiz started within 7 days | Are users closing the value loop? |

   - Display as daily and weekly aggregates
   - No new migrations — derive metrics from `users`, `notes`, `study_packs`, `quiz_sessions`, `user_usage` tables
   - Codex prompt: `docs/codex-prompts/v0.21.0-admin-funnel-metrics.md`

6. **Admin summary re-generation** *(new — official content backfill)*

   One-time (but idempotent) endpoint to backfill enriched summaries for admin-owned study packs that pre-date the v0.20.0 enrichment format.

   - `POST /admin/study-packs/regenerate-summaries` — targets packs owned by `UserRole.ADMIN` users whose `summary` does not yet contain `|`
   - Async via `llmParallelTaskExecutor`; returns `{ queued: N, skipped: N }` immediately
   - Updates only `study_packs.summary` — quiz, key concepts, tags untouched
   - No quota deduction; idempotent (already-enriched packs are skipped on re-run)
   - Codex prompt: `docs/codex-prompts/v0.21.0-admin-regenerate-summaries.md`

### Shipped in this release (Claude Code)

- **Official author detection made role-based** — removed hardcoded email constant from `NoteService`; `isOfficialAuthor()` now checks `UserRole.ADMIN` only; admin's `displayName` drives the "By X" label on public notes
- **Summary word limit raised to 350** — `MAX_SUMMARY_WORDS` and `developer.txt` prompt both updated; fixes validation rejections for enriched summaries
- **Profile Identity helper text** — plain-language helper text added for Display Name and Username fields on `/profile`

### Implementation stances

- `GET /notes/public?creator=<username>` joins `users.username` on the existing query — no new endpoint, no new entity
- Community Notes section calls the existing public library endpoint directly from the frontend — only backend change is an optional `size` param on `GET /notes/public`
- `user_library_filters` is a simple user-owned table; no plan gating for v1 (all plans can use saved filters)
- No localStorage fallback for saved filters — backend-persisted from the start
- Subject badge removal is frontend-only and safe to do inline after the creator filter ships

### Anti-drift notes

- `creator` filter uses `username`, not `userId` or `displayName`; existing public note canonical URLs (`/public/library/{subject}/{slug}`) are unchanged
- Community Notes section does not create a new page or route — it links to the existing `/public/library?courseProgram=<value>` URL
- No changes to note generation, quiz sessions, or Study Pack flows in this release
- Saved filters are plan-agnostic for v1; do not add gating without an explicit plan rules update to `docs/product/PLANS.md`
- Use `globalThis` instead of `window`/`self`/`global` for all new browser globals in frontend code (ESLint enforces this)
- Analytics events use the `AnalyticsEventType` enum in both Java and TypeScript — add new values before firing events
- Official author is now `UserRole.ADMIN` — do not recreate email-based checks; `isNoteLibOfficialAccount()` has been removed
- Admin summary re-generation uses `llmParallelTaskExecutor` only — never `studyPackGenerationTaskExecutor`

### Sequencing

Items 1, 3, 4, 5, and 6 Codex prompts are independent and can be queued simultaneously. Item 2 is handled by Claude Code immediately after item 1 commits.

---

## v0.22.0 - Course & Subject Discovery

**Status: Released**

Theme: make Course/Program and Subject the primary discovery axes across the public library, private library, and public profiles — removing the profile-type audience gate, surfacing subject breakdowns as interactive filter shortcuts, and closing a session reliability bug that caused unexpected sign-outs under concurrent API load.

### Why this release

Four gaps remain after v0.21.0:

1. **The audience pre-filter in the public library creates the wrong boundaries** — a nursing student with a STUDENT profile misses notes tagged for BOARD_TAKER even when the content is directly relevant. In the Philippine exam prep context especially, "Student" and "Exam Reviewer" overlap almost entirely. The pre-filter hides content rather than surfacing it.

2. **Anonymous and first-time visitors have no guided path to their content** — users who land on the public library from a shared link or search engine see everything at once. There's no prompt to tell them that filtering by course/program (PNLE, NMAT, etc.) is the fastest path to relevant notes. The filter exists but is invisible to users who don't know to look for it.

3. **Private libraries and public profiles lack a coverage view** — a user with 50 notes has no way to see their own distribution at a glance, and visitors to a public profile can't gauge a creator's depth or breadth. Note stats and library counts close both gaps.

4. **A concurrent refresh race condition causes unexpected sign-outs** — when multiple API calls fire simultaneously with an expired access token, each independently attempts a token refresh. The first succeeds and revokes the old refresh token; the second sends the now-revoked token, gets rejected, and triggers `handleUnauthorizedSession()` — signing the user out mid-session. This also makes the session-expiry redirect hit-or-miss.

### Prioritized items

#### P0 — Fix first (reliability bug)

**1. Fix concurrent token refresh race condition**

   Deduplicate simultaneous refresh attempts in `fetchWithAuth` using a module-level shared promise. When a refresh is already in progress, all concurrent callers wait on the same promise rather than each independently sending the refresh token.

   - Add `let refreshPromise: Promise<boolean> | null = null` to `api.ts`
   - Wrap `tryRefreshAccessToken` so concurrent callers coalesce on the same in-flight request
   - Clear the shared promise in a `finally` block so the next expiry cycle can refresh again
   - Also bump default `JWT_REFRESH_TOKEN_DAYS` from 1 → 7 in `application.yaml` (the 1-day default is too aggressive for a study app; users who open the app on day 2 may be forced to log in again)
   - Frontend-only except for the config change

#### P1 — Core theme (ship together)

**2. Remove the audience pre-filter from the Public Library**

   Stop using `targetProfileType` as the default gate in `GET /notes/public`. Default the public library view to "All" for every profile type, including Teacher. The audience filter remains available as an optional manual filter, but is no longer applied automatically on page load.

   - Remove the profile-type → `NoteTargetProfileType` pre-filter mapping from the frontend public library page
   - When `?audience=` param is absent, render the full public note list (same as "All" behavior today)
   - The `targetProfileType` badge on note cards stays; the field on note creation stays for Teachers
   - `courseProgram` + `subject` + `tags` become the primary browse signals
   - Frontend-only

**3. Course/Program helper CTA in the Public Library**

   A dismissible banner shown above the note list when no `courseProgram` filter is active. Surfaces the Course/Program filter to users who don't know it exists.

   Copy: *"Studying for a specific exam or program?"* → **[Browse by Course/Program]**

   Behavior:
   - Clicking opens the filter sheet (or inline filter on desktop) and focuses the Course/Program field
   - Dismissed per session via `sessionStorage` (anonymous users) or until a `courseProgram` filter is applied
   - Hidden when `?courseProgram=` is already active in the URL
   - For signed-in users with `courseProgram` set in their profile: smarter variant — *"See notes for [CourseProgram] →"* — that pre-fills the filter directly
   - Frontend-only

#### P2 — High value, independent

**4. "More in [CourseProgram]" section on public note detail pages**

   When a user opens a public note with a `courseProgram` set, show 3–4 related public notes at the bottom. Calls the existing `GET /notes/public?courseProgram=<value>&size=4` — no new endpoint needed.

   - Visible to both anonymous and signed-in users
   - Hidden when the note has no `courseProgram`
   - Section title: *"More notes for [CourseProgram]"*
   - Frontend-only

**5. Note count display in private and public library**

   Show a total note count in both library views so users can gauge the community's growth and their own library size.

   - **Private library**: total = `allNotes.length` (already loaded client-side); shown as "X notes" above or inline with the filter bar. No backend change.
   - **Public library**: requires a `total` field on the `GET /notes/public` response. Wrap the existing plain-array response in `{ items: NoteListItemResponse[], total: number }` where `total` reflects the untruncated filtered count. Frontend updates all callers of `listPublicNotes` to handle the new shape.
   - When filters are active, show "X of Y notes" (e.g., "43 of 177 notes")

**6. Meaningful empty state when courseProgram filter returns no results**

   Replace the generic empty state with a content-creation hook when a `courseProgram` filter is active and returns zero notes.

   Copy: *"No [CourseProgram] notes shared yet."* with *"Got notes? Share them with the community."* — CTA to `/notes/new` for signed-in users, `/auth` for anonymous.

   - Only when `?courseProgram=` is active and the list is empty
   - Frontend-only

#### P3 — Backend work, higher effort

**7. Note stats strip in the private library**

   A compact subject breakdown shown above the note list when the user has enough notes. Shows subject chips with counts — e.g., `Biology 12 · Physics 8 · Chemistry 3`. Clicking a chip applies the subject filter.

   - New `GET /notes/stats` endpoint: note counts grouped by `subject`, `courseProgram`, and `studyPackStatus`
   - Strip renders top subjects by count; "Other" chip if more than 5 subjects
   - Shown only when the user has ≥ 2 subjects and ≥ 5 total notes

**8. Public profile polish — note stats and subject links**

   Enrich the public profile with creator-level stats from their public notes. Replaces the static subject badge list (removed in v0.21.0).

   - **Header count line**: "X notes across Y subjects"
   - **Subject chips**: top subjects by public note count, each linking to `/public/library?creator=<username>&subject=<subject>`
   - **"Most active in" line**: top 2–3 subjects by count

   Backend: add `notesBySubject` and aggregate counts to `GET /public/profile/{username}` — no new endpoint. Derived from public notes only.

   Depends on v0.21.0 creator filter (`GET /notes/public?creator=`) being merged first.

#### P4 — Polish & fixes

**9. Statement 1 / Statement 2 quiz question formatting**

   Multi-statement questions (e.g. "Statement 1: … Statement 2: … Which is correct?") currently render as a single dense paragraph. Detect the `Statement N:` pattern in the quiz question renderer and display each statement on its own labeled line for readability.

   - Frontend-only; one component change in the shared question renderer
   - No data model or prompt changes

**10. Matching group prompt quality fix**

   Board Exam and Long Exam MATCHING questions are frequently demoted to MCQ because the LLM generates inconsistent choices across questions in a group (`reason=different_choices`). Strengthen the prompt constraint to require that all questions in a MATCHING group share identical choices.

   - Prompt file change only (Long Exam `developer.txt`)
   - No backend or frontend changes

#### Design constraint (applies to all P1–P2 items)

**11. Friction-free anonymous browsing**

   The public library and public note detail pages are fully explorable without an account. No sign-up prompts, no login gates on browsing or filtering, no interstitials. The only login gate is on write actions (copying a note, liking).

   Do not add any "sign up to see more" banners, soft-gates, or conversion prompts anywhere in the public library or public note detail flow when implementing items 2–6 above.

### Implementation stances

- Item 1 (race condition fix) is frontend-only except for bumping `JWT_REFRESH_TOKEN_DAYS` in `application.yaml`
- Items 2, 3, 4, 6, 9 are frontend-only; no backend changes
- Item 5 requires wrapping the `GET /notes/public` response — a small breaking change to the array response type; all existing callers must be updated
- Item 7 requires a new `GET /notes/stats` backend endpoint (authenticated) and one new frontend component
- Item 8 requires extending the `GET /public/profile/{username}` response — no new endpoint
- Item 10 is a prompt file change only
- `targetProfileType` badge on note cards is unchanged
- Teacher note creation flow is unchanged

### Anti-drift notes

- Do not remove `targetProfileType` from the public note API response — it is still used for the badge on note cards and as an optional manual filter
- The `?audience=all` URL param behavior (v0.18.0) remains valid; the pre-filter removal makes it redundant but harmless
- The helper CTA must not appear when `?courseProgram=` is already active
- Use `sessionStorage` for CTA dismissal — not `localStorage`
- Note stats on the public profile are derived from **public notes only** — never expose private note counts on a public-facing page
- Item 8 subject chips depend on the v0.21.0 `creator` filter param being merged first
- The concurrent refresh fix must coalesce on a single in-flight promise — do not use a mutex lock or queue

### Sequencing

Item 1 (race condition) ships first — it's a standing bug. Items 2 and 3 ship together (core theme). Items 4, 5, and 6 are independent of each other and can be Codex-prompted separately or batched. Item 7 and 8 are the heavier backend items and can be deferred to the second half of the release. Items 9 and 10 are small enough to handle inline (Claude Code) without a Codex prompt.

---

## v0.18.0 - Profile Completeness & Communication

**Status: Released**

Theme: complete the Professional profile experience, fix communication gaps (subscription expiry notifications, outdated email templates, spam folder guidance), add KaTeX math rendering for computational working solutions, and introduce concept-level spaced repetition signals in Adaptive Practice.

### Why this release

Three things create friction or trust gaps for existing users:

1. **Professional users discover Interview Practice by accident** — it's accessible but not prominently surfaced from the dashboard or as a primary CTA after Professional profile selection. The profile type exists and the mode works, but the flow doesn't connect them.
2. **Subscription expiry is silent** — users lose access without warning, assume the product is broken, and churn instead of renewing. A single pre-expiry email is the highest-leverage retention touch we haven't shipped yet.
3. **Emails are stale** — templates haven't been updated in multiple releases. Outdated copy erodes trust; missing spam-folder guidance causes verification failures that block new users before they ever log in.

Additionally, engineering and sciences users see plain-text working solutions when their notes deserve proper formula rendering, and the Adaptive Practice loop lacks a temporal signal to bring users back to concepts they haven't reviewed recently.

### Primary focus

1. **Professional profile dashboard polish** — make Interview Practice the primary CTA on the Professional dashboard; update onboarding step after Professional profile selection to introduce Interview Practice by name; ensure the note detail view surfaces Interview Practice prominently for Professional users.

2. **Subscription expiry notification email** — send an automated email 7 days before a user's plan expires with clear renewal CTA; send a second reminder 1 day before; send a post-expiry "your access has ended" email with a re-subscribe link. No auto-renewal — manual renewal model stays.

3. **Email template audit and polish** — audit every transactional email (welcome, verification, study pack generated, password reset, subscription confirmation, expiry notices); update stale copy to reflect current product naming (NoteLib, not StudySnap); add spam folder guidance to the verification email ("Can't find this email? Check your Spam or Promotions folder").

4. **KaTeX math rendering (Pro)** — replace the plain-text working solution panel with proper LaTeX rendering for `COMPUTATIONAL`-type questions; add KaTeX as a frontend dependency; update LLM prompts for engineering/sciences modes to generate LaTeX-formatted working solutions; keep plain-text fallback for non-LaTeX content.

5. **Concept-level spaced repetition signals** — track the last time each key concept was answered correctly per user per study pack; surface a "Due for review" signal on concepts not seen in 3+ days; Adaptive Practice mode surfaces these due concepts preferentially; visible in a lightweight "Concept health" view on the study pack detail page.

6. **Parent profile** — needs product definition before implementation. Placeholder: understand what parents do in NoteLib (monitor child's study activity? create notes on behalf of children?). Defer implementation until the use case is defined; remove PARENT from visible onboarding options for now to avoid confusion.

### Implementation stances

- KaTeX: add as a scoped dependency (`react-katex` or `katex` direct); render only in the working solution panel, not in question or choice text
- Spaced repetition: lightweight SM-2-inspired signal only — no full algorithm; a simple "last correct answer date per concept" is sufficient for v1
- Subscription expiry emails: backend scheduled job (Spring `@Scheduled`); no new email service — use existing Mailgun/SendGrid integration
- Parent profile: do NOT implement until the user flow is defined; remove from profile type selection if it shows a blank experience
- Professional dashboard: UI-only change — no new backend endpoints; Interview Practice is already accessible, just needs better surfacing

### Anti-drift notes

- Do not change the subscription billing model (no auto-charge); expiry emails are notification-only
- KaTeX rendering must not affect non-computational question text — scope it only to `workingSolution` display
- Spaced repetition data must be per-user per-study-pack — do not mix concept health across different notes
- The five quiz modes remain unchanged; spaced repetition is a signal layer on top of Adaptive Practice, not a new mode

---

## v0.19.0 - Multi-Note Depth & Simulation Parity

**Status: In Progress**

Theme: complete the multi-note story across all premium simulation modes. Board Exam is the last mode without multi-note support — Long Exam has had it since v0.14.0. This is the highest-priority shipping target for the Facebook group audience, where board exam reviewers are the primary demographic.

### Why this release

Board exam reviewers studying across multiple subject areas need to simulate full-coverage exams — not just single-topic ones. Multi-note Long Exam shipped in v0.14.0 and proved the pattern is sound. Multi-note Board Exam completes the simulation parity story.

The Facebook study groups driving organic growth are dominated by board exam reviewers. Multi-note Board Exam is the one feature most likely to generate word-of-mouth there.

### Primary focus

1. **Multi-note Board Exam (Pro)** — Pro users can span a Board Exam session across up to 3 same-subject notes, mirroring the multi-note Long Exam feature exactly.

   - Prestart screen gets a "Span this exam across more notes" section (same-subject filter, same note-picker row style as Long Exam)
   - Questions split proportionally across selected notes by source; source refs stored in session JSONB
   - Generation: live at session start (no pre-generated pool rethink needed for v1 multi-note; the pool is scoped to the combined source set)
   - Existing single-note Board Exam flow unchanged for users who pick only one note
   - Empty-state hint on single-note prestart: "Create another note with the same subject to unlock multi-note exam mode" (mirrors the Long Exam hint from v0.17.0)
   - Backend follows the same pattern as `LongExamService` for multi-note source merging and question pool allocation
   - Pro-only, with Board Exam quota charged per source note and the monthly cap raised for normal single-note headroom
   - Subject constraint enforced at the picker level (same-subject filter); cross-domain Board Exam is out of scope for v1

2. **Admin analytics subject drift fix** — "Top Subjects by Study Pack" currently groups on the study pack's own `subject` column, which was set at generation time and never updated. If the user later adds or changes the note's subject, the pack's subject lags. Fix: join through `NoteEntity` to use the current note subject instead of the stored pack subject for the top-subjects aggregation.

### Completed in v0.19.0 so far

- **Multi-note Board Exam (Pro)** — shipped multi-source Board Exam support for up to 3 same-subject notes with the existing `BOARD_EXAM` discriminator, same quota/category, fixed question cap redistribution, `sessionState.sourceNoteRefs`, per-source live Board Exam generation, and in-session source attribution.

### Implementation stances

- Multi-note Board Exam must reuse the existing `BOARD_EXAM` session discriminator and generation pipeline; no new mode, no new quota category
- Source-note references in session JSONB follow the existing Long Exam pattern (`sourceNoteIds`, `sourceNoteQuestionCounts`)
- Board Exam stays feedback-free during the session — multi-note does not change the exam-simulation identity contract
- Admin subject metric fix changes only the repository query — no entity change, no migration

### Anti-drift notes

- Do not skip the same-subject constraint for v1 (cross-domain Board Exam is a separate design question)
- Multi-note Board Exam scales question count by source count (`min(12 * sourceCount, 30)`) so wider simulations get more coverage while staying capped at a 30-minute exam
- The five quiz modes remain unchanged; multi-note is a configuration of an existing mode, not a new mode

---

## v0.17.0 - Quiz Quality & Depth

**Status: Released**

---

## v0.16.0 - Conversion & Growth

**Status: Released**

Theme: close the gap between social traffic and signed-up users; make teachers a natural distribution channel through student-facing quiz sharing; ensure the mobile web experience doesn't lose social visitors before they reach value.

### Why this release

NoteLib has a healthy feature set but weak top-of-funnel conversion. The primary distribution channel is Facebook — posts in student and board exam groups linking to public notes. Four problems block that funnel today:

1. Social traffic is almost entirely mobile; the web app isn't installable and some flows feel cramped on small screens.
2. New users who sign up from a public note land in an empty library with nothing to do — the note they came from isn't there, and the quiz flow they started doesn't continue.
3. Teachers have no way to share a quiz with students digitally. Every teacher who generates a quiz is a potential distribution channel for 30–50 student signups — but only if sharing exists.
4. The landing page shows a generic "How it works" loop that doesn't speak to teachers or board exam reviewers specifically — visitors can't immediately see their own workflow.

This release addresses all four, in order of impact.

### Primary focus

1. **Shareable Student Quiz Links (Teacher feature)**

   Teachers generate a quiz → receive a shareable `/quiz/[token]` link → students open it in-browser → take the quiz without needing an account first → prompted to sign up at the end to save their score and access their own notes. Teacher sees a basic response summary (score distribution, who answered among authenticated users).

   - This is the highest-leverage conversion feature: each teacher who adopts it drives 30–50 new signups per class
   - Anonymous session — no score persistence until the student signs up; no anonymous session state stored beyond the current browser session
   - Quiz link is tied to a specific `generatedQuiz`; the teacher controls whether sharing is on or off
   - Teacher-profile only; student-profile users cannot generate shareable quiz links
   - Free teachers: limited shareable links per month (TBD based on cost math); Plus/Pro: higher or unlimited

2. **Post-signup copy-note → instant quiz flow**

   When a new user signs up from a public note page, route them directly into a quiz session on the note they came from — the copied note is already in their library, Study Pack is already generating, and the first Quick Review starts immediately. Remove the "empty library" drop-off entirely.

   - Requires a `copyIntent` param surviving the OAuth / email signup redirect
   - On successful signup, backend copies the public note to the new user's library and triggers Study Pack generation
   - Frontend routes directly to the quiz session, not the library
   - No change to the existing copy flow for already-authenticated users
   - Users who sign up directly (not from a public note) see onboarding first, then library; users coming from a public note skip onboarding and go straight to the quiz

3. **Profile-Aware Learning Loop (Landing page)**

   Replace the current static "How it works" + "Who It's For" sections with a single interactive section. Visitors click their profile type (Students / Exam Reviewers / Teachers / Professionals) and see the exact learning loop for that role — screenshot, description, mode chips, and step-by-step workflow.

   - Replaces `HowItWorksSection` (generic 5-step loop) and `ProfileShowcaseSection` (static cards) with one merged `ProfileLearningSection` client component
   - Profile tabs at top; selected tab drives screenshot, description, mode chips, and learning loop steps below
   - Default selection: Students
   - Per-profile step data and tagline (e.g., "Create - Generate - Preview - Export - Share" for Teachers)
   - Teacher step 5 is "Share" — intentionally previews the Shareable Quiz Links feature shipping in this release
   - A simplified 3-step overview ("Capture → Generate → Practice") replaces the generic loop in the hero area so the top of the page still has a quick pitch

   Per-profile learning loops:

   | Profile | Tagline | Steps |
   |---|---|---|
   | Students | Create - Understand - Practice - Challenge - Improve | Create, Understand, Practice (Quick Review), Challenge (Challenge Quiz / Long Exam), Improve (Adaptive Practice) |
   | Exam Reviewers | Create - Understand - Practice - Simulate - Improve | Create, Understand, Practice (Quick Review / Challenge Quiz), Simulate (Board Exam), Improve (Adaptive Practice) |
   | Teachers | Create - Generate - Preview - Export - Share | Create lesson note, Generate quiz, Preview & refine questions, Export DOCX, Share quiz link to students |
   | Professionals | Create - Understand - Practice - Critique - Report | Create, Understand, Practice (scenario MCQ), Critique (AI feedback per answer), Interview Readiness Report |

4. **Teacher In-App Guided Tips**

   Add five one-time contextual guidance tips for teachers at the moments where the Teacher workflow is most confusing. Uses the existing `GuidanceTip` component + `hasSeenTip()` localStorage system — no new infrastructure.

   | Moment | Message |
   |---|---|
   | First Teacher dashboard visit | "NoteLib turns your lesson notes into ready-to-use quiz drafts. Start by creating a note with your lesson content." |
   | First note creation (teacher) | "The more detail in your notes, the better the quiz questions. Paste a full lesson outline, not just bullet headers." |
   | Generate Quiz modal (teacher, first time) | "You can select multiple notes to build a quiz from a full unit — use the note checkboxes in your library first." |
   | Multi-note checkboxes (library, teacher, first time) | "Select multiple notes with the checkboxes, then use 'Generate Quiz' from the toolbar." |
   | First DOCX export button encounter | "Download as DOCX and open in Word or Google Docs — format it your way before distributing to students." |

   - All tips gated by `user.profileType === 'TEACHER'`
   - Each tip has a unique `tipId`; once dismissed, never shown again (localStorage)
   - Do not add new tips without going through `pickActiveGuidance()` on pages that already use priority-ranked rules; inline `GuidanceTip` is acceptable for one-off contextual placements

5. **PWA / Mobile Web Polish**

   Make the web app installable from mobile browsers and ensure the core conversion funnel (public note → Quick Check → signup → first quiz) is thumb-friendly.

   - Add PWA manifest and service worker with an offline shell for the app routes
   - Add an "Add to Home Screen" nudge for returning mobile visitors who haven't installed
   - Fix iOS Safari viewport zoom on input focus: all inputs and textareas must have `font-size: 16px` minimum — iOS zooms when font-size < 16px, hiding modal action buttons in some flows
   - Audit and fix touch targets, modal scroll behavior, and text sizing on the public note, Quick Check, signup, and dashboard flows
   - No full native app — PWA covers the gap without the 2–3 month rebuild cost

6. **Consistent Paywall UI**

   Unify the look of all quota-limit messages across the app. Currently the study pack generation limit and the note creation limit surface as visually inconsistent banners. Define a single paywall template (icon, title, reset date, upgrade CTA) and apply it to all quota surfaces.

   - All quota-limit states (study pack, note creation, quiz generation) must render the same component/layout
   - Upgrade CTA always routes through `getUpgradeCtas(currentPlan)` — no hardcoded copy
   - Reset date must be accurate and formatted consistently

8. **Send Feedback button consistency fix** ✅

   The "Send Feedback" button appeared as a floating bottom-right button on some pages (Dashboard, Library, Settings) and as a navbar icon on others — inconsistent and the navbar-triggered modal was rendering clipped to the header area due to `backdrop-filter` creating a CSS containing block for `position: fixed` children.

   - `AppModal` now wraps its overlay in `ReactDOM.createPortal(..., document.body)` so it always escapes any containing block ancestor, regardless of where it is mounted
   - Floating `SendFeedbackWidget` removed; header icon renders consistently on all authenticated pages
   - Removed `shouldShowFloatingFeedbackWidget` / `shouldShowHeaderFeedbackWidget` routing functions from `app-shell.tsx`

7. **Social proof on landing**

   Add real-time (or cached) aggregate counts and one or two genuine student/teacher testimonials to the landing page. Students and teachers trust peer validation; a note count and a real quote move the needle more than a feature list.

   - Cached backend aggregate: note count, user count, completed quiz session count
   - "Join X students already studying on NoteLib" stat line in the hero or beneath the CTA
   - 1–2 short testimonial quotes sourced from real users (manual, not generated)
   - No fabricated numbers or aspirational counts — use real figures only

### Implementation stances

- Shareable quiz links must not persist anonymous session state — no new session rows until the student authenticates; the quiz UI is client-side-only during the anonymous play
- `copyIntent` redirect must survive both Google OAuth and email/password signup flows; implement as a short-lived server-side token or a signed cookie, not a plain query param that gets dropped on OAuth redirect
- PWA service worker must not cache API responses or auth state — static assets only; do not cache quiz or note data
- Social proof counts must be cached (5-minute TTL acceptable) — do not query live on every landing page load
- Shareable quiz link quota for Free teachers is a plan rules change; update `docs/product/PLANS.md` before implementing the gate
- Profile-aware learning loop is a pure frontend change — `"use client"` component with local tab state; no backend involvement
- iOS zoom fix is a CSS-only change; do not add `user-scalable=no` to the viewport meta tag (accessibility regression)

### Anti-drift notes

- Shareable quiz links are a teacher-only feature — do not expose link generation on student note detail
- Anonymous quiz sessions must not create `QuickReviewSessionEntity` rows — no backend session until the student signs up
- PWA scope is limited to the conversion funnel; do not invest in offline-capable quiz sessions or background sync in v1
- Testimonials must be real; do not generate or invent them
- Profile-aware landing section merges two existing sections — do not keep both the old `HowItWorksSection` and the new merged section; remove the old one
- Teacher guided tips use existing `GuidanceTip` component — do not build a new tips framework

### Sequencing

Recommend shipping in this order:
1. Mobile viewport zoom fix + consistent paywall UI (CSS + component fix, fastest wins, unblocks mobile users immediately)
2. Teacher in-app guided tips (low scope, unblocks active teacher sales)
3. Profile-aware learning loop on landing (frontend-only landing redesign)
4. Post-signup copy-note → instant quiz + onboarding flow redesign (full-stack, highest funnel impact)
5. Shareable Student Quiz Links (full-stack; teacher-side first, student anonymous play second)
6. Social proof on landing (fastest to ship once real numbers are confirmed)
7. PWA / Mobile Polish (broadest scope; run in parallel with the above or ship last)

---

## v0.17.0 - Quiz Quality & Depth

**Status: In Progress**

Theme: close the gap between NoteLib-generated quizzes and what students encounter in actual Philippine board and licensure exams — fix known generation quality bugs, add realistic question framing variety, and lay the groundwork for computational questions in engineering and sciences.

### Why this release

Three quality gaps surfaced in v0.16.0 user testing:

1. **Choices appearing in explanation text** — the LLM occasionally echoes the answer choices inside the `explanation` field (e.g., "The correct answer is A. Civil Engineering Fundamentals. (A) Civil Engineering... (B) Mathematics..."). Happens most on notes that contain answer choices in the source text (copied from reviewers). Prompt fix.
2. **Board exam and challenge quiz questions sharing identical distractors** — a cluster of related questions can end up with the exact same four choices, which never happens in real Philippine board exams outside of deliberate matching-type blocks. Prompt constraint fix.
3. **All questions use the same framing** — every question starts with "Which of the following..." — real licensure exams vary framing extensively: "All of the following are true EXCEPT...", "Which is NOT correct?", "Which best describes...?" etc. Prompt improvement.

Additionally, engineering users need computational questions with worked solutions and formula-based distractors — a larger feature requiring math rendering (KaTeX/MathJax) and schema changes.

### Primary focus

1. **Quiz Generation Quality Fixes** — prompt-only changes, no schema or UI changes

   - **Fix: choices in explanation text** — add prompt instruction: "In the explanation field, explain WHY the answer is correct. Do not repeat, list, or reference the answer choices by letter or text."
   - **Fix: repeating distractors across questions** — add prompt constraint: "Each question must have a fully independent set of four distractors. No two questions in this quiz may share the same set of answer choices."
   - **Improvement: plausible numerical distractors for formula-heavy notes** — when a note contains formulas or unit-based quantities, generate distractor choices that are plausible numerical values (wrong by a predictable error — wrong formula applied, wrong unit conversion) rather than conceptually unrelated terms.

   These three are deployable as standalone prompt hotfixes and do not need to wait for the full v0.17.0 feature scope.

2. **Question Framing Variety** — prompt improvement only; no schema changes; 4-choice MCQ format is preserved

   Instruct the LLM to vary question framing across a quiz set instead of defaulting to "Which of the following...?" every time. Target mix (not enforced per-question, just as a distribution instruction):
   - "Which of the following is TRUE?" (standard, ~40%)
   - "Which of the following is NOT correct?" or "All of the following are true EXCEPT..." (~25%)
   - "Which best describes X?" or "What is the primary purpose of X?" (~20%)
   - Assertion-style: "Statement 1: ... Statement 2: ... Which is correct?" (~15%)

   Applies to Challenge Quiz, Quick Review, Board Exam, and Long Exam generation prompts.

   Out of scope: true/false 2-choice and multi-select formats — those require schema changes (see item 4).

3. **Computational Quiz Mode** — new feature requiring schema + frontend changes; Pro-only at launch

   Math-based questions with numerical answer choices and step-by-step worked solutions in the explanation. Designed for engineering, sciences, and finance notes.

   - **Schema**: add optional `questionType: "CONCEPTUAL" | "COMPUTATIONAL"` to `QuizItem`; `COMPUTATIONAL` questions include a `workingSolution` string in the explanation (displayed in a distinct block)
   - **LLM**: engineering/math prompt persona asks for computation-based questions when the note contains formulas, quantities, or unit conversions; answer choices are plausible numerical values with different units or rounding errors; explanation shows step-by-step derivation
   - **Frontend**: render `workingSolution` in a code-block-style panel below the explanation; integrate KaTeX or render plain-text math in a fixed-width font as a v1 approximation (full LaTeX rendering is v2)
   - **Verification note**: LLM arithmetic is unreliable; v1 does not validate correctness — a disclaimer ("AI-generated — verify calculations") appears on computational questions; active verification (running math through a solver) is post-v1
   - Gated by: engineering/math note detection (heuristic: subject or tags contain engineering/math signals, or note content contains `=` and units)

4. **Additional Question Format Types** — requires schema + UI changes

   - **True/False standalone** — 2-choice questions (`["True", "False"]`); requires `choices` to be variable-length or a separate `questionFormat` field; scoring unchanged
   - ~~**Multi-select**~~ ✅ — "Select all that apply" shipped as `MULTI_SELECT` with `correctIndices`, all-or-nothing v1 scoring, and `correctIndex` preserved as a legacy fallback; available on all plans in every quiz mode except Board Exam
   - ~~**Matching type**~~ ✅ — deliberate shared-choice block shipped as `MATCHING` with `questionGroup`, shared option rendering, group-aware shuffle, and per-item single-correct scoring; available on all plans in every quiz mode except Board Exam
   - Remaining implementation order: True/False polish / audit as needed

### Known Generation Reliability Issues (lower priority, v0.17.0)

- **Invalid key concepts schema mismatch** — intermittent generation failure surfaced as "The study pack service returned invalid key concepts"; occurs when the LLM returns the key concepts array with an unexpected field shape (wrong field name, missing required field, extra fields, or partial JSON); current behavior is a hard failure requiring the user to retry; confirmed intermittent in production (3 retries before success in one known case); fix approach: defensive JSON parsing with field coercion or a single automatic backend retry before surfacing the error; prompt schema reinforcement likely sufficient; no schema changes required; lower priority than the quiz quality prompt fixes but should ship within v0.17.0

### Implementation stances

- Quality fixes (items 1–2) are prompt changes in `backend/src/main/resources/prompts/` — no DB migration, no entity change; deployable as hotfixes
- Computational quiz is gated to subjects where it adds value — do not generate computation questions for history, law, or social-science notes
- Do not add KaTeX as a full dependency for v1 computational questions; a fixed-width text block for the working solution is an acceptable v1 approximation
- Question framing variety must not change the `QuizItem` schema — it is purely a generation instruction
- Additional format types (True/False, multi-select) require `EXAM_MODES.md` review before implementation — they affect scoring logic in all five quiz modes
- Exactly five quiz modes remain in v0.17.0; question types and question formats are orthogonal to the mode hierarchy

### Anti-drift notes

- Do not add computational questions to Board Exam Mode until the Philippine board exam format is confirmed to include them (most PH licensure MCQ sections are conceptual)
- Do not add `user-scalable=no` to viewport meta tag when fixing iOS zoom — accessibility regression
- True/False standalone questions change the choice-count assumption in all quiz UIs; audit every surface that renders `choices.map(...)` before shipping
- Multi-select changes the scoring contract; `correctIndex` callers must be audited before `correctIndices` is introduced
- Matching type is the most complex format addition; do not bundle with True/False in the same prompt
- Invalid key concepts fix must not silently discard key concepts; coerce or retry, do not hide partial data loss

---

## v0.15.2 - UX Cleanup & Bug Fixes

**Status: Released**

Theme: post-Teacher-Power-Features polish pass focused on long-standing UI/UX bugs and rough edges across notes, library, profile navigation, help guides, and quiz session surfaces. No new features — sharper defaults and accurate state.

Primary focus:

1. **Quiz session display correctness** — Recent Sessions chip on Note Detail renders the actual quiz mode (Quick Review / Challenge Quiz / Adaptive Practice / Long Exam / Board Exam / Interview Practice) instead of always showing "Challenge Quiz"; library card "Not reviewed yet" timestamp updates after any mode completion, not just Quick Review; multi-note Long Exam sessions surface on every participating note with a "spans N notes" sublabel.

2. **Copy and navigation polish** — Edit Note drops the Import Notes uploader (belongs to Create Note); app shell Profile sidebar redirects to Profile Settings (avatar "My Profile" stays as the public-profile entry); Board Exam Guide no longer recommends Long Exam (Student-only mode) and footer "Switch Profile" CTA deep-links to the Profile Type section; Student / Teacher / Professional guides show profile-aware "Switch Profile" footer CTAs that hide on the user's own profile guide; share-note modal auto-copies the URL on open and shows a "Copied" success pill.

3. **Library Draft filter** — new `Draft` chip in the library Filter row for users parking notes while waiting for monthly Study Pack quota reset.

4. **Target Audience cleanup** — Create Note "Who is this note for?" keeps hidden auto-prefill for Student / Board Exam / Professional profiles and fixes Professional notes so they save with the Professional audience instead of Student; Teacher/Admin keeps a visible required picker with Professional as a selectable audience.

### Implementation stances

- All v0.15.2 items are polish or bugfix — no new persisted columns beyond minor backend support for `lastSessionCompletedAt` aggregation; no plan-gated features
- Quiz session display fixes derive `lastSessionCompletedAt` server-side from existing session tables — no new "last activity" column
- `getQuizSessionModeLabel` becomes the single source of truth for mode → label mapping; do not inline labels anywhere
- Multi-note Long Exam display is driven by the session's participant set, not the note
- Target Audience stays required. Student / Board Exam / Professional keep hidden profile-based auto-prefill; Teacher/Admin keep a visible required picker.

### Anti-drift notes

- Do not touch `QuickReviewSessionEntity` schema or session-state JSONB layout
- Do not redesign Recent Sessions card visuals — chip text, sublabel text, and inclusion criteria are the only changes
- Do not change Dashboard / Mastery Report / Score Report aggregation; only the library card label and Recent Sessions list widen
- Target Audience visibility stays profile-aware; only the Professional default and Teacher/Admin selectable audience list change. Course / Program field and helper are untouched
- Codex prompts for this scope live at `docs/codex-prompts/v0152-fix-quiz-session-display.md`, `docs/codex-prompts/v0152-polish-copy-and-nav.md`, and `docs/codex-prompts/v0152-library-filter-and-target-audience.md`

### Sequencing

The three prompts are independent and can ship in any order. Recommended order is:
1. `v0152-polish-copy-and-nav.md` (lowest risk, fastest verification)
2. `v0152-fix-quiz-session-display.md` (backend + frontend; verify multi-note Long Exam case carefully)
3. `v0152-library-filter-and-target-audience.md` (small additive enum change)

---

## v0.15.1 - Teacher Power Features

**Status: Released**

Theme: extend the teacher quiz-authoring workflow with concrete controls that turn it into a complete classroom tool, building on the v0.15.0 teacher flow polish and plan accessibility foundation. Target audience: Filipino teachers who need a practical, affordable tool for quiz and exam preparation.

Primary focus:

1. ~~**Question count control on Generate Quiz**~~ ✅ — let teachers choose 10 / 20 / 30 questions per generated quiz. Plus+ Teacher unlocks 20 and 30; Free Teacher fixed at 10. Honest upsell because higher counts directly increase LLM token cost.

2. ~~**Custom DOCX header**~~ ✅ — teacher profile carries an optional `schoolName` field that appears at the top of every DOCX export. Per-export modal can add class/section name and toggle date inclusion. Eliminates the manual edit-in-Word step before printing or filing exam packets.

3. ~~**Multiple exam versions (A/B/C)**~~ ✅ — single DOCX export with 2 or 3 deterministically shuffled versions for anti-cheating in classroom settings. Plus+ Teacher only. Choice order also shuffled per version; answer keys reflect shuffled positions. Same exam + same versionCount produces identical bytes (deterministic).

Shipped refactor:

- ~~**Per-note learner-level removal**~~ ✅ — Study Pack generation now resolves learner level from the owner profile, Public Library no longer treats notes as learner-level-filterable artifacts, and Teacher Generate Quiz adds an optional per-generation Target Level override for class-specific quiz difficulty.
- ~~**Required learner-level teacher reframe**~~ ✅ — onboarding and profile validation guarantee a profile learner level for new and updated profiles, and Teacher Generate Quiz requires and pre-fills its Target Level override from the note's latest generation or profile fallback.

### Implementation stances

- All three features are gated by Teacher profile type; non-Teacher profiles see no UI surface for them
- All three preserve the `generatedQuiz` ownership model from v0.15.0 — no LLM call at export time for header rendering or version shuffling
- Plus-gate enforcement for question count happens BEFORE the LLM call to avoid wasted tokens on rejected requests
- Backend exception classes follow the existing plan-gated-action pattern (e.g., `QuestionCountNotAllowedForPlanException`, `MultipleExamVersionsNotAllowedForPlanException`)

### Anti-drift notes

- Multiple-version shuffle is a deterministic algorithm, not AI — do not market or icon-decorate as "AI-powered"
- DOCX header limited to one school name line + one class name line + one date line; no multi-line address, no logo, no branding (v1 scope)
- Question count restricted to the set {10, 20, 30}; no slider, no custom values, no values outside this set
- Versions limited to {1, 2, 3}; do not extend beyond 3 in v1
- DOCX export must continue to use stored `generatedQuiz` data only — header and shuffling are local rendering

### Sequencing

v0.15.1 must NOT ship before v0.15.0 because:
- Question count control's "Plus unlocks 20/30 questions" upsell copy depends on the teacher-aware `getUpgradeCtas` variant introduced in v0.15.0 (Teacher Plan Accessibility)
- Multiple exam versions reuses the per-export Plus paywall pattern established in v0.15.0

Within v0.15.1, the three features can ship in any order or in parallel — they are mostly orthogonal.

---

## v0.15.0 - Premium Mode Uplift + Cost-Control Quota Refactor

**Status: Released**

Theme: make Long Exam and Board Exam feel premium, not just gated behind a paywall, and close the unbounded-LLM-cost gap on uncapped modes. This is a margin fix framed as a UX uplift, not a feature add.

Primary focus:

1. **Premium feel for Long Exam and Board Exam** — improve the paid-mode experience without adding AI coaching or changing the locked simulation identity.

   - Stronger pre-session framing: pre-flight presentation, expected duration, and "this is not a quiz" cues
   - Stronger post-session presentation: score report layout polish, domain-coverage visualization, and suggested-next-step framing
   - Possible visual differentiation: distinct top-bar treatment, calm color palette, and larger result-page typography
   - Constraint: Board Exam stays feedback-free during the session; Long Exam stays forfeit-only with no mid-exam coaching, as locked in `docs/product/EXAM_MODES.md`

2. **Cost-control quota refactor** — replace the current "Pro = effectively unlimited" Long Exam and Board Exam state with explicit per-mode caps.

   | Mode | Current Pro state | Proposed v0.15.0 cap |
   |---|---|---|
   | Challenge Quiz | 50/mo | 50/mo (unchanged — already cheap per session, do not trim) |
   | Adaptive Practice | 30/mo | 30/mo (unchanged) |
   | Long Exam | uncapped (gated by Pro plan only) | 10/mo |
   | Board Exam | uncapped (gated by Pro plan only) | 5/mo (highest LLM cost per session) |
   | Interview Practice | 10/mo | 10/mo (unchanged) |

   Specific numbers are runtime config and must be tuned against actual usage data from v0.14.0 once captured. Do not lower Pro Study Pack quota (100/mo) or Pro Challenge Quiz quota (50/mo) without usage evidence — those are existing value the user is paying for.

3. **Interview Practice evolution post-v0.14.0** — review what to do next only after Interview Practice v1 has run for at least one billing cycle.

   - **Multi-note Interview Practice (smart context aggregation)** — generate from the base note plus related notes that share `courseProgram` and at least one tag; cap at 2–3 sibling notes to manage prompt size and per-session cost
   - **Structured interview templates by role/job family** — consider opinionated section breakdowns such as Backend Engineer = PL fundamentals + DB + Behavioral only if v1 usage data shows demand
   - **Open-ended / conversational evaluation** — only consider if MC + critique format hits its ceiling and Pro users explicitly ask for it
   - **Profile / role enrichment** — design separately before capturing target role on the user profile
   - **Interview Practice tier promotion to Plus** — only if v0.14.0 usage data justifies the LLM cost; current `gpt-4.1` generation + `gpt-4.1-mini` critique split is what makes Pro-only economically viable

4. **Professional profile surface updates** — Interview Practice shipped in v0.14.0 but was not surfaced on the landing page, learn page, or help center. Close the gap so the feature is discoverable to the audience it was built for.

   - **Landing page** — add "Professionals" to the `targetUsers` section alongside Students, Exam Reviewers, and Teachers; update the "how it works" step copy to acknowledge interview simulation as a distinct mode
   - **Help center** — add a "Professional Guide" help card that explains the Interview Practice workflow: note → scenario MCQs → AI critique → Interview Readiness Report
   - **Learn page** — add a "professionals" category with 2–3 guides: how to use NoteLib for interview prep, how to practice with scenario-based questions, how to read the Interview Readiness Report

6. **Authenticated user redirect on public/marketing pages** — when a signed-in user navigates to the landing page (`/`), pricing page, or other public marketing pages, the app currently renders those pages as if the user were anonymous (no auth-aware nav state, no redirect). The expected behavior is: redirect authenticated users from pure marketing pages directly to `/dashboard`, so they are never dropped back into a conversion funnel they have already passed through. Public content pages (`/public/library`, `/public/library/[subject]/[slug]`) are exempt — they are genuinely useful to signed-in users and should not redirect. Implementation approach: server component auth check on the landing page and pricing page routes; if a valid session cookie is present, return `redirect('/dashboard')` before rendering; no client-side redirect to avoid flash of landing content.

5. **Teacher flow polish** — make the teacher Generate → View → Export loop feel like a first-class product, not a functional prototype. Target audience: Filipino teachers who need a practical, affordable tool for quiz and exam preparation.

   - **Exam Builder UX audit and polish** — the current Exam Builder (note selection, section management, balance controls) works but is dense; identify and fix the specific friction points without a full rebuild; improve the note selection flow, make section reordering more intuitive, and reduce cognitive load on the balance step
   - **Quiz Preview layout** — stronger question display, correct answer and explanation more clearly distinguished, Export CTA as the dominant action in the header (not buried); read-only feel should communicate "this is your exam, ready to hand out"
   - **Teacher dashboard emphasis** — "Ready to Export" and "Recently Generated Quizzes" should be the first thing a teacher sees, not secondary cards below Continue Studying; Create Teaching Material CTA should be prominent and direct
   - **Teacher-specific empty states and guided first run** — new Teacher users land in a blank library with no guidance; add a first-run banner that explains the Generate → View → Export loop in plain language, linking directly to "Create a note" so teachers aren't lost
   - **Teacher plan accessibility** — exports are the terminal action for teachers, not quiz sessions; evaluate giving Teacher-profile Plus users higher or unlimited DOCX export limits, since capping exports at 15/mo directly blocks their primary workflow; the goal is to be genuinely useful to teachers who cannot afford the Pro tier, especially in the Philippine context where Pro pricing is proportionally high relative to teacher salaries; this requires a plan rules change and a `docs/product/PLANS.md` update before implementation

### Implementation stances

- New explicit per-mode quotas should live on `UserUsageEntity` and `StudySnapProperties`; reset by `BillingUsageResetJob`
- Existing uncapped Long Exam and Board Exam behavior is a margin risk; caps are the fix, not coaching
- Use honest user-facing framing: "Each mode now has its own monthly cap so you can see exactly what your plan includes"; avoid framing as a quota reduction
- Surface per-mode usage in Settings -> Plan & Billing alongside the existing counters
- Keep Board Exam feedback-free during the session and keep Long Exam forfeit-only with no mid-exam coaching
- Re-validate cost math against actual rates and usage before finalizing cap values
- Teacher flow polish must not change the `generatedQuiz` ownership model or route teachers into student session logic; all teacher preview uses `generatedQuiz` only
- Teacher plan accessibility decision must be made before any billing rule changes are implemented; do not change plan limits without a reviewed `PLANS.md` update
- Authenticated redirect must be server-side only (no client-side flash); public content pages (`/public/library/**`) are explicitly excluded from redirect behavior — only pure marketing pages (`/`, `/pricing`, `/learn`) redirect to `/dashboard`

### Cost math reference

- Pro revenue: roughly $4.50 blended (PH ₱249 + USD $4.99)
- Worst-case current LLM cost per Pro user/mo when every quota is maxed and Long/Board remain uncapped: roughly $4.83, creating negative margin on heavy users
- Worst-case post-cap: roughly $0.94 saved on Long/Board, restoring healthier margin
- Realistic-usage cost: roughly $1.50/mo, with caps protecting the worst case without affecting most users

---

## v0.14.0 - Grow the Surface, Deepen the Practice

**Status: Released**

Theme: expand organic reach through subject SEO pages, unlock professional-audience depth with Interview Practice, extend Long Exam to span multiple notes, and close out the quiz generation performance work deferred from v0.13.0.

Primary focus:

1. ~~**Subject landing pages (SEO)**~~ ✅ — server-rendered `/public/library/[subject]` pages with per-subject metadata, decay-ranked sections, and static generation shipped in v0.14.0.

2. ~~**Faster quiz generation**~~ ✅ — Board Exam dedicated simulation prompts, async Long Exam generation, and parallel LLM calls with sequential fallback shipped in v0.14.0.

3. ~~**Interview Practice Mode (Professional Profile)**~~ ✅ — shipped in v0.14.0 as an Adaptive Practice sub-mode (`ADAPTIVE` discriminator, `subMode: "INTERVIEW"` in session JSONB); 5-mode contract preserved; Pro-only, 10/month dedicated quota; `gpt-4.1-mini` critique + `gpt-4.1` generation; Interview Readiness Report result. Full spec in `docs/features/professional-profile.md`.

4. ~~**Multi-note Long Exam**~~ ✅ — shipped in v0.14.0; Pro users can add up to 3 same-subject notes to one Long Exam, with source refs stored in session JSONB and questions split proportionally by source.

5. ~~**Stale docs cleanup**~~ ✅ — removed 17 stale/legacy files; merged AI generation spec and overflow menu rules into active docs shipped in v0.14.0.

### Implementation stances

- Subject landing pages must be server-rendered; do not implement as a client-rendered filter redirect
- Interview Practice must reuse the `ADAPTIVE` engine discriminator and carry sub-mode identity in session JSONB (`subMode: "INTERVIEW"`). Do not introduce a new `QuickReviewSessionMode` enum value. Do not add a 6th mode. Do not introduce new persistence aggregates.
- Interview Practice must use `gpt-4.1-mini` for per-answer critique calls and `gpt-4.1` for generation. Do not unify on the premium model — the cost split is the launch viability case.
- Interview Practice quota is dedicated (10/month Pro-only) and tracked separately from Adaptive Practice / Challenge Quiz quotas. Do not double-charge other quotas.
- Multi-note Long Exam must reuse the existing session lifecycle; no new persistence aggregate
- Faster generation changes must be gated behind findings; do not optimize speculatively

---

## v0.13.0 - Complete the Promise, Reach New Audiences

**Status: Released**

Theme: ship the modes that were already promised (Long Exam), open NoteLib to a second audience (Professional Profile), improve organic discovery through SEO, and close out infrastructure research items deferred from v0.12.0.

Primary focus:

1. **Long Exam Mode v1 (Student-facing, Pro-only)** — backend session support, fixed long-form generation (not progressive), forfeit-only leave, mastery report result screen; single-note at launch; shared Advanced Exam quota bucket with Board Exam Mode

   - Backend: `LONG_EXAM` discriminator on `QuickReviewSessionMode`; question set generated and committed in full before the session starts; mastery report data stored in session state JSONB; reuses existing session lifecycle (`GENERATING → IN_PROGRESS → COMPLETED / FORFEITED / FAILED`) and generation lock
   - Frontend: setup confirmation screen with expected duration; fixed progress indicator (no `+5 Questions` control); Board Exam-style top bar with server-anchored countdown timer (90s/question); leave = forfeit — no pause/resume option exposed to the user (anti-procrastination principle, matches Board Exam behavior); mastery report result screen (coverage, weak domains, suggested next step, inline learner-level pill allowed)
   - Access: Pro-only at launch; single note; multi-note deferred to v0.14.0+
   - Profile visibility: Student profile (primary emphasis), Board Taker profile (secondary, less ceremony than Board Exam); hidden from Professional profile

2. **Professional Profile activation** — `PROFESSIONAL` profile type is no longer `Coming Soon`; users can select it in onboarding and profile settings; profile-aware mode label overrides and professional-framed dashboard

   - Backend: no new entities; `PROFESSIONAL` enum already existed
   - Frontend: `lib/exam-mode-visibility.ts` updated so Professional profile shows `Certification Review` (Challenge Quiz) and `Full Practice Exam` (Long Exam); Board Exam hidden; professional dashboard framing; Professional option in onboarding with profile icon; learner level grouped picker shows "Recommended for Professionals"; labels are display-only — engine discriminators (`CHALLENGE`, `LONG_EXAM`) unchanged
   - Access: All plans (same access rules as Student)
   - **Interview Practice Mode deferred to v0.14.0+** — requires a conversational AI evaluation engine not present in the current quiz architecture; see `docs/features/professional-profile.md`

3. **Faster quiz generation** — promote from research-only (v0.12.0 deferred) to research → implement; profile current LLM latency end-to-end (prompt build, API call, JSON parse, DB write); evaluate streaming responses to unblock frontend earlier, model selection (`gpt-4.1-mini` for quiz generation), and early session creation; implement the approach that findings support; frontend may gain a generation progress indicator if streaming is adopted

4. **Subject landing pages (SEO)** — proper server-rendered `/public/library/[subject]` landing pages replacing the current redirect to the filtered library; static `<title>` and `<meta description>` per subject; server-rendered note cards ranked by decay scoring; sitemap update to include subject pages; deferred from v0.12.0 (ROADMAP item J)

5. **Proration / recomputation design doc** — design how mid-cycle plan changes (upgrade and downgrade) recompute Study Pack and quiz quotas; output: a design doc under `docs/product/`; no implementation until the design is reviewed; deferred from v0.12.0

6. **Stale docs cleanup** — audit `docs/` for files still referencing v0.11.0 or earlier resolved items; update or remove

### Implementation stances

- Professional Profile must not fork entity tables — all profiles share the same Note/StudyPack/Session model
- Long Exam backend must reuse the existing session lifecycle and generation lock; no new persistence aggregate
- Subject landing pages must be server-rendered; do not implement as a client-rendered filter redirect
- No proration implementation until the design doc is reviewed and approved
- Exactly five quiz-flavored modes exist: Quick Review, Challenge Quiz, Adaptive Practice, Long Exam, Board Exam; adding a sixth requires updating `docs/product/EXAM_MODES.md` and this roadmap together

---

## v0.12.0 - Learning Experience, Discovery, and Retention

**Status: Released**

Current phase emphasis:

- improve user conversion and the first-study / first-quiz experience before expanding monetization work
- keep Progressive Challenge Quiz generation as the active quiz-flow optimization path
- treat Board Exam Mode optimization as a separate follow-up after the core quiz flow is more stable

Primary focus:

1. **Public Library public note conversion** *(top priority)* — public notes are shareable but currently function as app detail screens rather than learning pages; a visitor who arrives from a Facebook or social link should immediately understand the topic, see why NoteLib helps them study, interact lightly with the content, and know what to do next without being hard-gated before value is shown

   - add a short topic hook below the note title that anchors the learning angle for visitors
   - add a Quick Check / mini quiz preview section: expose 1–2 questions to public users without requiring login
   - gate continuation of the full quiz, score persistence, and Study Pack generation behind signup/login
   - after signup, route the user toward creating or copying a Study Pack so they land in the product with a clear goal
   - add a soft conversion CTA: `Turn your own notes into something like this`
   - reorder primary CTAs so copy/generate actions appear after the visitor has seen learning value
   - keep `Share` always visible; keep `Copy to My Library` available for signed-in users
   - improve generated note formatting: shorter sections, clearer headings, key-fact blocks, quick recall blocks, less dense paragraphs — so public pages read like a study reviewer, not a raw LLM dump
   - public mini quiz answers must not be persisted for anonymous users; no session is created until the user is authenticated

   Acceptance criteria:
   - a visitor without an account can open a public note, understand the topic, and answer 1–2 questions
   - signup gate appears only after value is shown — not on page load
   - CTA does not feel aggressive or interrupt the reading experience
   - the page works well for Facebook/social sharing use cases
   - no implementation changes to the core Study Pack generation or session flows for authenticated users

2. **Learner Level + Course/Program UX refinement** — quiz generation prompts use saved learner level for difficulty and explanation depth; Course/Program autocomplete suggestions are narrowed by the active subject context; helper text on Learning Profile adapts to the selected learner level; no new onboarding steps
3. **Conversion funnel optimization** — plan-aware CTAs via `getUpgradeCtas(currentPlan)` on all paywall and limit surfaces; post-quiz upgrade nudge on Quick Review and Challenge Quiz result screens; analytics funnel events queryable from the admin dashboard
4. **Proration / recomputation design** — design mid-cycle plan changes (upgrade and downgrade) so quota is recalculated correctly; do not implement until design is approved; document in `docs/architecture/ARCHITECTURE.md`
5. **Retention loops** — continue-studying prompts on Dashboard for users who have recent unfinished sessions; weak-concept reminder emails on a backend schedule; near-limit banners surface reset date and upgrade CTA
6. **Backend Public Library filtering + shareable URLs** — move subject, tags, course/program, search, and audience filters onto the canonical `/public/library` query-param model so students can bookmark and share collections without duplicate public-library routes
7. **Library organization guidance for students** — in-app guidance explains how subjects and Course/Program organize the private Library as it grows; reuse the existing `GuidanceTip` system and add one-time contextual tips at natural growth milestones
8. **Social login — Google first** — add Google OAuth as an alternative to email-and-password login and signup; no other providers until Google is stable
9. **Faster quiz generation investigation** — profile current LLM latency end-to-end for quiz generation; prototype streaming or early session-creation patterns; document findings and a recommended approach in `docs/architecture/` before any implementation
10. **Profile-aware mode selection + Long Exam coming-soon** — mode-selection screen now profile-aware (Students see Challenge Quiz + Long Exam; Board Takers see Challenge Quiz + Board Exam; Teachers skip to challenge setup); Long Exam card and setup screen live as a coming-soon placeholder so mode identity is established; `lib/exam-mode-visibility.ts` added as the single source of truth; accelerated from Medium Priority after doc planning landed
11. **Board Exam premium UX polish (presentation-only)** — pre-flight setup, score-report-style result framing, fullscreen behavior, and removal of the inline learner-level pill on the result screen so Board Exam Mode reads as a simulation and not a "longer Challenge Quiz"; no engine changes; details in `docs/product/EXAM_MODES.md`
12. **Adaptive Practice tier reconciliation** — `PLANS.md` is the canonical source (Plus = 10 / mo, Pro = 30 / mo); align `docs/features/adaptive-practice.md`, `docs/features/quiz.md`, `docs/PROJECT_CONTEXT.md`, and runtime gating to match before any Long Exam monetization work begins

### High Priority (Current Phase)

- **Public creator identity disambiguation** — stop relying on `displayName` alone on Public Library cards and public note detail; use or introduce a stable public creator identifier (username / handle when available, otherwise a generated public slug), keep `displayName` for readability, show handle/slug when disambiguation is needed, and preserve existing public links through compatibility or redirect handling

### Medium Priority (Next Phase)

- **Board Exam Mode optimization** — improve generation speed, explore partial or progressive loading only if it preserves the exam-like experience, and keep progressive generation out of Board Exam Mode for now; identity contract is locked in `docs/product/EXAM_MODES.md`
- **Long Exam Mode v1 (Student-facing, Pro-only)** — backend session support, fixed long-form generation, pause/resume, mastery report result screen; Pro gating and shared Advanced Exam quota
- ~~**Onboarding/profile type icon polish**~~ ✅ shipped in v0.13.0 — emoji icons added to all four active profile type cards in onboarding

### Future Guidance System Expansion (Post-v0.12.0)

The guidance engine introduced in v0.12.0 is intentionally minimal. Future iterations can extend it without changing the `GuidanceTip` component or `guidance.ts` persistence layer:

- **Note editor inline guidance** — contextual tips inside the note editor when `subject` or tags are blank after the first save; use the engine's `condition()` callback to check field state at render time
- **Cooldown-aware rules** — add an optional `cooldownMs` field to `GuidanceRule`; `pickActiveGuidance()` can skip rules shown within the cooldown window using a separate last-shown timestamp key in localStorage
- **Dashboard contextual tips** — tips tied to study-gap detection (e.g., user hasn't quizzed in 7 days) using the same engine pattern; conditions read from dashboard data already loaded on the page
- **Profile completion nudge** — tip on the Dashboard or Profile page when `courseProgram` is unset after the first Study Pack is generated

### Product Direction Note

Board Exam Mode is intentionally kept as a fixed, exam-style experience. Optimization will be handled separately after core quiz flow and conversion improvements are stabilized.

Implementation stances:

- public note pages must teach first, then convert — do not hard-gate visitors before they see value; mini quiz preview is a lightweight surface, not a full session; no anonymous session state is persisted
- generated note formatting should prioritize scannability and study usefulness; prefer short sections, clear headings, key-fact blocks, and exam-friendly wording over long paragraph dumps
- keep Learner Level and Course/Program as separate concerns — Learner Level controls difficulty/style; Course/Program controls domain context — do not merge them
- ~~do not add learner level to onboarding~~ — **reversed in v0.13.0**: onboarding step 2 now collects learner level and course/program directly; Dashboard prompt remains for users who skip onboarding step 2 or completed onboarding before this change
- social login must be an alternative, not a replacement; existing email accounts must continue to work
- quiz latency investigation is research-only in v0.12.0; no production latency changes without findings
- Long Exam Mode is design-only in v0.12.0; canonical mode-hierarchy and identity contract live in `docs/product/EXAM_MODES.md`; no implementation until the spec is reviewed
- exactly five quiz-flavored modes exist: Quick Review, Challenge Quiz, Adaptive Practice, Long Exam, Board Exam; adding a sixth requires updating `docs/product/EXAM_MODES.md` and this roadmap together

### Completed in v0.12.0 so far

- **Public Note Quick Check — multi-question preview** — evolved the single-question Quick Check into a sequential multi-question experience (up to 3 preview questions drawn from the Study Pack quiz); added a progress indicator (`1 / 3`) so visitors know where they are; after submitting each answer, improved feedback microcopy (✅ Correct!, 🧠 Nice work!, Almost there.) and a "Next Question →" button appear before advancing; the final question transitions to a lightweight completion state ("🎉 Quick Check Complete") with CTAs to copy and start practicing; no backend changes, no new AI generation, fallback-safe when fewer than 3 questions exist; notes-first layout preserved — Quick Check remains below Summary and Key Concepts
- **Public note detail engagement polish** — refined the public-note learning hook with a safe fallback; updated Quick Check to feel like a lightweight learning prompt instead of a demo widget; added a post-answer CTA that nudges visitors toward creating or copying their own Study Pack only after value is shown; tightened public-note CTA wording and Full Notes readability without changing quiz/session logic
- **Public Library canonical routing + shareable filters** — consolidated public browsing around `/public/library`; turned `/library/public` and `/public/library/{subject}` into compatibility redirects; synced subject, tag, search, course/program, audience, and sort filters to shareable query params so direct filtered URLs restore the same UI state
- **Public Library URL-filter UX polish** — stabilized the main search with debounced URL sync, preserved scroll position on filter changes, kept tag browsing reachable through a dedicated `Browse all` action, and fixed selector-modal search focus so typing no longer jumps to the close button
- **Public Creator Identity / Attribution** — added unique public usernames as stable handles; public attribution now keeps `displayName` for readability while using `@username` and `/public/creator/{username}` for disambiguation and future creator pages; legacy `/public/profile/{userId}` links remain compatible
- **Social login — Google first** — added Google OAuth login/signup as an alternative to email/password; verified Google emails link to existing accounts instead of creating duplicates; Profile shows connected sign-in methods; Apple/Facebook/GitHub remain out of scope
- **Study Pack metadata correctness** — locked note-level `courseProgram` as the Study Pack generation source of truth with profile fallback only when the note has no course/program saved; fixed normal note-owned generation so AI metadata suggestions stay transient until apply; removed duplicate AI tag suggestions when user tags already overlap; kept onboarding's explicit auto-apply exception for empty metadata fields
- **Quiz metadata context consistency** — Challenge Quiz, Board Exam, and Adaptive Practice now use the same generation-context resolver as Study Pack generation: note-level `courseProgram` first, profile `courseProgram` fallback, and user-level `learnerLevel` for difficulty/style
- **Generate from Topic Course/Program source-of-truth fix** — first generation now reads the current Create Note Course / Program at submit time and sends it immediately; profile Course / Program remains fallback only when no draft value is selected
- **Quiz Ready badge accuracy** — made private Library `Quiz Ready` badges and filters profile-aware: Teacher users keep them for exam-export workflows, while Student and Board Taker users see learner-facing Study Pack readiness only
- **Progressive Challenge Quiz generation** — Challenge mode starts with 5 questions; users generate +5 more from the last question, up to 20 per session; `POST /challenge-quiz/sessions/{sessionId}/generate-more` endpoint; `GenerateMoreChallengeQuizResponse` DTO; `NotEnoughNewQuestionsException` with `NOT_ENOUGH_NEW_QUESTIONS` code; `QuizDeduplicationUtils.uniqueQuestions()` post-generation dedup; `QuizSessionStateUtils.appendQuizItems()` JSONB append; Board Exam Mode is exempt
- **Progressive quiz scoring** — score computed from answered questions (`selectedChoices.size()`) instead of fixed total; result screen shows `{correct} of {answered} answered correctly`; Score Summary column labeled `Answered`
- **Challenge Quiz UX refinements** — `Complete Quiz` replaces `Submit Challenge Quiz`; `+5 Questions` / `Adding...` button at last question; microcopy banner at quiz top; progression-aware hint at last question (`"Good start — want to keep going?"` at 5 q, `"10 questions in — push to 15?"` at 10 q, `"Almost there — finish with all 20?"` at 15 q, `"You've answered all {n} questions — ready to submit?"` at cap); generate-more toast updated to `"Challenge extended to {n} questions"` / `"Full challenge unlocked: 20 questions"`; `noMoreQuestions` state hides `+5 Questions` silently
- **Leave Quiz modal stability fix** — `onBeforeRouteLeave` and `onConfirmLeave` memoized via `useCallback` in `page.tsx`; `onConfirmLeave` reads from `challengeSessionRef.current` to avoid stale closures; prevents `LeaveQuizModal` from unmounting/remounting on every timer tick
- **Analytics enum completeness fix** — added missing `QUICK_REVIEW_COMPLETED`, `CHALLENGE_QUIZ_COMPLETED`, `ADAPTIVE_PRACTICE_COMPLETED`, and `ONBOARDING_V2_CTA_GO_TO_SAVED_NOTE` to `AnalyticsEventType` Java enum to resolve `HttpMessageNotReadableException` on quiz completion events
- **Conversion funnel + quiz UX refinement pass** — `PaywallModal` plan cards selectable with ring highlight, single `Continue with [Plan]` footer CTA, PRO-user calm message instead of disabled cards; `StudyPackLimitModal` trimmed to primary CTA + `Maybe Later` for FREE/PLUS and a single `Got It` for PRO; `getUpgradeCtas` extended with optional `UpgradeCtaContext` for context-aware copy (`"Get More Study Packs"`, `"Unlock Adaptive Practice"`); Quick Review result adds guidance text and renames `Practice Again` → `Retry Quick Review`; Dashboard and Library empty states updated to guided copy
- **Retention loop — continue studying + focus areas** — Continue Studying session priority reordered to Challenge Quiz → Adaptive Practice → Quick Review; Continue Studying body copy is mode-aware (`"You left off on Question 4 of 10 in your Challenge Quiz."`); Focus Areas free-tier fallback: Free/Plus users see `"Revisit Note"` when weak concepts exist but Adaptive Practice is locked, instead of only an upgrade prompt; `MEANINGFUL_STUDY_ACTIVITIES` constant deduplicated to `ActivityType.MEANINGFUL_STUDY_ACTIVITIES`
- **Guidance Foundation System** — minimal guidance engine (`lib/guidance-engine.ts`) with `GuidanceRule` type and `pickActiveGuidance()` function; two contextual library tips at natural growth milestones (notes 1–3 and notes ≥ 5); Dashboard personalization prompt bug fixed (suppressed when learner level already set); prompt repositioned after primary study action for all three profile types
- **Profile-aware mode selection + Long Exam coming-soon** — `lib/exam-mode-visibility.ts` is the single source of truth for which modes appear per profile; Students see Challenge Quiz + Long Exam (coming-soon); Board Takers see Challenge Quiz + Board Exam; Teachers skip to challenge setup directly; cross-profile escape hatch guides Students toward Board Exam via profile switch; Long Exam mode card and coming-soon setup screen live with disabled CTA; backend session logic ships in v0.13.0
- **Board Exam premium UX polish (presentation-only)** — pre-flight setup screen replaced with a simulation-framing checklist ("Begin Board Exam", 5 pre-flight items); result screen subtitle changed to "Score Report"; inline learner-level pill hidden on Board Exam result (`!isBoardExamMode` guard); `PostSuccessUpgradeNudge` hidden on Board Exam result; no engine changes
- **Adaptive Practice tier reconciliation** — `StudySnapProperties` defaults corrected (`adaptivePracticeProOnly=false`, `plusMonthlyAdaptivePracticeLimit=10`); `application.yaml` default updated; `docs/features/adaptive-practice.md`, `docs/features/quiz.md`, and `docs/PROJECT_CONTEXT.md` now all reflect Plus = 10 / mo, Pro = 30 / mo; Open Discrepancy #1 in `EXAM_MODES.md` closed
- **Learner Level grouped picker on quiz result screens** — Quick Review and Challenge Quiz result screens now render learner level chips in two profile-aware groups (Recommended / Other Learning Styles) via `getGroupedLearnerLevels(viewerProfileType)`; `viewerProfileType` state added to Quick Review and synced via auth listener; profile page combobox (already grouped) unchanged
- **Public Library conversion funnel polish (recommendations A–G)** — related-notes block in quiz completion card ("More from {Subject}", up to 3 engagement-ranked notes from same subject, server-side fetch via shared 5-min cache); auth-prompt consolidation into `AppModal` pattern with copy-intent redirect URLs (`guestAuthMode` prop removed from all callers); dead tabbed-content component deleted; `PublicPracticeModeTeaser` placed after Full Notes on public note detail (Challenge Quiz + Adaptive Practice free, Board Exam Mode Pro chip, gated on `!isDraft`); time-decayed Featured score (30-day half-life, 10% floor) applied in both `computeDiscoveryScore` (frontend) and `computeScore` (backend) with synchronized formulas and testable `now` parameter; recommendation H blocked pending backend windowed count fields; recommendation J (subject landing pages) deferred

## v0.11.0 — Completed

Completed in `v0.11.0`:

- learning loop positioning across the landing page and product messaging
- onboarding flow redesign: experience-first 5-step flow that ends with a generated Study Pack
- Generate Note from topic available in both onboarding and Create Note
- Create Note UX improvements with write vs generate entry options
- Xendit payment integration with hosted checkout and webhook-confirmed activation
- Xendit payment hardening:
  - correct PHP invoice amount handling
  - pending checkout reuse instead of duplicate pending payments
  - config-driven Monthly and Annual manual checkout amounts
  - automatic intro-offer and voucher application during checkout
  - voucher redemption persistence only after successful `PAID` webhook
  - safe internal `returnUrl` support back to the interrupted page
  - success-page routing that returns Settings/Billing upgrades to Dashboard and paywall upgrades to the interrupted flow
  - polished billing success and failed result pages
  - manual-renewal expiry windows after Monthly (`30` days) and Annual (`365` days) payments
  - subscriptions-table source of truth for plan state, active-subscription history preservation, and webhook-driven renewal extension
- Free / Plus / Pro multi-plan billing model replacing the legacy single-tier paid plan
- Settings Plan & Billing redesign: billing cycle toggle + 3-column plan cards (Free, Plus, Pro)
- pricing system unification through a shared frontend plan config used by landing, pricing, and settings surfaces
- conversion-focused paywall redesign with context-aware copy, autosave-before-checkout, and resume-after-upgrade flow restoration
- documentation context cleanup so product, architecture, and feature docs match the current Free / Plus / Pro, Xendit, onboarding, and paywall behavior
- legacy billing-provider runtime removal and local ngrok-based webhook testing support
- copy alignment around `Generate Study Pack`
- activation improvement: users leave onboarding with real content, not an empty dashboard
- content moderation: `ContentModerationService` with token-based dictionary matching at note title, Study Pack topic, and note content creation boundaries; English and Filipino banned-word dictionaries; 52 tests
- plan-aware upgrade CTAs: `getUpgradeCtas(currentPlan)` helper in `frontend/src/config/plans.ts`; upgrade surfaces route to `/settings?section=plans` instead of `/pricing`
- Settings `?section=plans` auto-scroll and highlight ring on the Plan & Billing card
- post-quiz `PostSuccessUpgradeNudge` on Quick Review and Challenge Quiz result screens with plan-aware CTAs and sessionStorage dismissal
- analytics funnel events: `QUICK_REVIEW_COMPLETED`, `CHALLENGE_QUIZ_COMPLETED`, `ADAPTIVE_PRACTICE_COMPLETED`, `ONBOARDING_V2_CTA_GO_TO_SAVED_NOTE` added to `AnalyticsEventType`
- onboarding Study Pack limit handling: bumps to Step 5 with `studyPackLimitReached` flag; shows `NearLimitBanner` and note-navigation CTAs; fires `completeOnboarding` via existing useEffect

### v0.6.0 - Landing Revamp & Positioning

Primary focus:

- Landing-page messaging revamp that positions NoteLib as a notes library and study workspace first
- Public Library promotion as a top-level public discovery route
- Learn-page integration for the active-recall study method
- Public navbar alignment across landing, learn, pricing, login, and Public Library
- SEO title, meta description, and Open Graph metadata alignment with the new positioning
- Open Graph image refresh to match the new messaging before the release is cut
- Landing pricing section updated to Free / Plus / Pro cards with intro offer pricing and "Manual renewal. No automatic charges." footer
- Demo page redesigned as a 5-step interactive flow (choose start → input → generated note → Study Pack CTA → Study Pack results) using static prebuilt content only — no backend or LLM calls
- Landing hero repositioned around exam-readiness: "Turn your notes into exam-ready study materials in seconds"
- "Why NoteLib" section updated with 3 benefit cards (Built for studying, Learn from your weak points, From notes to mastery)
- Demo enhanced with interactive per-question quiz (select before reveal), exam context copy, and post-quiz conversion CTA
- Pricing cards updated with plan descriptions tied to learner stage; export feature description added; Plus includes Adaptive Practice (10/month)
- Product positioning principles added to AGENTS.md: learning-outcome framing, demo as conversion driver, clear plan progression

Implementation stance:

- position NoteLib as an exam-focused study tool, not a generic AI utility
- hero and pricing copy must frame features in terms of learning outcomes
- demo must feel like a guided experience that creates an "aha moment" before the CTA
- Free → Plus → Pro should feel like natural progression for a growing student
- treat Public Library as a public growth and discovery feature, not a paid feature
- keep public marketing pages accessible without login
- align landing, SEO, and README messaging around the same product identity before `v0.6.0` is tagged

### v0.7.0 - Learning & Metadata Foundation

Primary focus:

- Learner Level on the user profile and onboarding
- required Learning Profile `Course / Program` plus optional per-note `Course / Program` metadata
- note-level `courseProgram` metadata with profile-defaulted note creation
- stronger note metadata quality through subject autocomplete, saved custom subjects, and tag guidance
- field-level AI metadata suggestions so users keep final control of title, subject, and tags
- a dedicated `Learning Profile` card on private Profile
- richer Public Profile identity with learner-level/course context when provided
- generation-context plumbing so future quiz prompts can use learner metadata safely

Implementation stance:

- keep learner metadata on the existing `users` aggregate instead of creating profile-type-specific tables
- keep note-level `Course / Program` optional while requiring it for onboarding and later Learning Profile saves
- prepare smarter quiz generation by passing learner metadata through backend generation context before prompt behavior changes
- improve library/public-profile structure over time without changing note ownership or page responsibilities

### v0.8.0 - Board Exam Mode

Primary focus:

- Async Study Pack generation handoff from Note Editor to Note Detail
- Graceful Study Pack generation failure and retry recovery
- Quiz start integrity locks for exam-like Challenge Quiz starts
- Exam Countdown
- Exam Readiness Score
- Study Plan
- Mock Exam Mode
- Performance Analytics

Implementation stance:

- keep Board Exam Mode on the same shared note-first engine
- do not fork entities or tables by profile type
- use the existing `Note -> Study Pack -> Quiz -> Activity -> Weak Concepts` pipeline
- emphasize exam-prep presentation, recommendations, and analytics without merging page responsibilities

## Current Product Shape

Navigation:

- Main:
  - Dashboard
  - Library
  - Public Library
- Account:
  - Profile
  - Settings
  - Admin (admins only)

Core routes:

- `/dashboard`
- `/library`
- `/public/library`
- `/notes/{id}`
- `/notes/{id}/sessions/{sessionId}` for session review
- `/public/library/{subject}`
- `/public/library/{subject}/{slug}`
- `/public/profile/{userId}`

Current session-review UX:

- desktop and mobile both open the same dedicated session-review page from `Recent Sessions`
- Note Detail stays the entry point for history, while the dedicated review page owns focused answer review

## Future Directions

### Background Quiz Pre-Generation (de-scoped from v0.17.0)

Challenge Quiz was the original pooling candidate but was de-scoped: its progressive generation model (5 → 10 → 15 → 20 questions on demand) already limits the initial wait to a single 5-question LLM call, so pre-generation adds LLM cost without a meaningful UX benefit. Additionally, pre-generating the initial batch and then generating extensions on demand would introduce concurrency waste (pool generation and on-demand generation could race for the same note).

Revisit for a different mode if a cold-start bottleneck is confirmed by usage data — e.g., a mode with a fixed long question set where the full generation is the bottleneck (Long Exam already uses the `ExamQuestionPoolService` pool mechanism for this). Do not pre-generate Challenge Quiz.

### Exam-mode work (planned)

- **Multi-note Long Exam** — shipped in v0.14.0
- **Board Exam advanced result analytics** — promoted into the active v0.15.0 premium-mode result presentation scope
- **Multi-note Board Exam** — planned for v0.19.0; see v0.19.0 section above for full spec
- **Long Exam tier promotion to Plus** — only if usage data justifies the LLM cost; not part of the current v0.15.0 cap refactor unless the cost review supports it
- **Planning-only** — cross-profile mode unlock (Students opting into Board Exam without changing profile); curated exam decks / cohort content (Pro+); cross-profile journey (Student → Board Taker upgrade flow with continuity)

### Premium mode uplift + cost-control quota refactor

Promoted to the active `v0.15.0 - Premium Mode Uplift + Cost-Control Quota Refactor` section above.

### Interview Practice evolution

Initial evaluation is promoted to the active v0.15.0 section. The following remain unsequenced follow-up directions and should not be committed until Interview Practice v1 has usage data from at least one billing cycle.

- **Multi-note Interview Practice (smart context aggregation)** — generate from the base note plus related notes that share `courseProgram` and at least one tag; cap at 2–3 sibling notes to manage prompt size and per-session cost; the dashboard entry already selects the most relevant note, so multi-note adds breadth across a topic, not a replacement for that smart selection; do not implement until v1 usage data confirms users want wider scenario coverage than a single note provides
- **Structured interview templates by role/job family** — opinionated section breakdowns (e.g. Backend Engineer = PL fundamentals + DB + Behavioral); requires either a curated role taxonomy or a user-defined template builder; do not build until v1 usage data shows real demand and the section-aware generation prompt's limitations are observed
- **Open-ended / conversational evaluation** — replace MC structure with free-text answers and AI rubric scoring; architecturally heavy (new session schema, new evaluation pipeline, new result model); only consider if MC + critique format hits its ceiling and Pro users explicitly ask for it
- **Profile / role enrichment** — capture target role explicitly on the user profile (instead of inferring from notes) to drive better generation context; bigger architectural decision; do not bundle with any of the above — design separately
- **Interview Practice tier promotion to Plus** — only if v0.14.0 usage data justifies the LLM cost; current model split (gpt-4.1 generation + gpt-4.1-mini critique) is what makes Pro-only economically viable, and lowering the tier requires re-running that math

### Lesson Plan for Teachers (future, unsequenced)

A lightweight collection entity grouping an ordered set of notes into a lesson plan for teacher-profile users. No AI synthesis at the plan level — a lesson plan is a playlist, not a synthesized document.

- New `LessonPlan` entity: title, description, ordered list of note references with optional week/topic labels per item
- No new AI generation at plan creation; Study Pack and Quiz generation still happen per-note using existing quotas — no new quota category
- Teacher dashboard gains a "Lesson Plans" section alongside the library; notes remain individually owned and independently editable
- DOCX export from a lesson plan produces a multi-section packet: one quiz section per note in lesson-plan order
- "Generate Quiz for Lesson Plan" toolbar action generates quizzes for each note in sequence, consuming the teacher's existing quiz quota per note
- Requires backend: new `LessonPlan` entity + ordering join table; frontend: lesson plan creation UI + DOCX multi-section export
- Do not implement Option B (multi-note AI synthesis across all notes in the plan) in v1 — risk of lower-quality synthesis and significantly higher LLM cost per plan; Option A (collection model) delivers the organization and sequencing value teachers need without new AI spend

### Study Pack Section Improvements (future, unsequenced)

The current Study Pack format (Overview / Key Idea / Core Details / Why It Matters / Quick Recall) is consistent but template-locked — every note produces the same five sections regardless of subject or content type. Planned improvements in order of effort:

- **Common Misconceptions section** — names what students typically get wrong about the topic; high quiz-prep value; prompt-only, no schema change; hotfix-deployable
- **Richer Quick Recall** — expand term-definition pairs to include a memory hook (analogy, mnemonic, or visual cue); prompt-only; hotfix-deployable
- **Comparison tables** — when the note contrasts two or more concepts, generate a structured markdown table instead of parallel bullet lists; prompt-only; hotfix-deployable
- **Concept relationships** — prerequisite chains ("Understand X before this") and contrast pointers ("Different from Y because..."); prompt-only; hotfix-deployable
- **Subject-adaptive section templates** — STEM notes get an Equations + Variables block and a Worked Example; humanities notes get a Timeline or Key Arguments section; may require a `sectionType` field addition to the key concept schema if the current JSONB storage cannot accommodate variable section shapes; requires a design pass before implementation

The first four improvements are prompt-only and can ship as hotfixes without schema changes. Subject-adaptive templates require a design review on the key concept schema before implementation and should not be bundled with the prompt-only items.

### Public Library Discovery — Future Items

- **Trending this week section (H)** — a new discovery section above Featured showing notes gaining traction in the last 7 days; blocked on backend: `NoteListItemResponse` has no windowed engagement fields; requires `recentCopyCount` / `recentLikeCount` or a precomputed rolling 7-day aggregate before this section can be built correctly; do not implement under a "Trending" label using lifetime totals — the signal would be misleading
- **Subject landing pages (J)** — moved to v0.14.0 scope; `/public/library/[subject]` proper server-rendered landing pages with per-subject metadata and decay-ranked note cards

Potential expansion areas after `v0.8.0`:

- richer note workspace
- deeper progress insights from quiz history
- board-exam-specific recommendations and weak-area planning
- optional public-profile enhancements such as followers, likes, and creator bios
- optional snapshot/history tables if product value is proven

### Billing Improvements (Future)

- **Exam-cycle pass** — promoted to v0.26.0 Track 3. See v0.26.0 section.
- recurring subscription support
- coupon-code entry UI
- cancel subscription flow
- billing portal / self-serve billing management
- automatic renewal
- invoices / receipts UI
- billing history UI improvements
- plan switching and downgrade flows
- provider-managed recurring billing via `provider_subscription_id`

### Account Management (Future)

- forgot password flow
- change password from Settings
- email verification and account-security improvements beyond the current launch flow

### Connected Account Management / Auth Provider Management (Future)

Future improvements for users with multiple sign-in methods. The Google OAuth foundation (account linking, `email_verified` guard, Profile sign-in method status) is in place; these are follow-on improvements that require their own design pass before implementation.

Potential scope:

- unlink Google account safely — must prevent lockout when Google is the only sign-in method
- add/change password for Google-only users — allow switching to or adding email/password without forcing a full re-signup
- multiple provider support — Apple, Microsoft, etc.; do not add until Google is stable and provider abstraction is reviewed
- connected account security UX — notify users by email when a new provider is linked to their account
- account recovery flows for social-login users — what happens when Google revokes access or the associated email changes
- provider conflict resolution UI — surface a clear choice when a Google email matches an existing email/password account
- recent login/session visibility — show sign-in history and active sessions in Settings for security-aware users
- email change flow with connected providers — changing the NoteLib email when a Google account is linked needs careful sequencing

Implementation notes for future reference:

- the provider abstraction should live in a shared auth provider layer, not scattered across login and signup flows
- lockout prevention: never allow unlinking the only auth method unless an alternative is confirmed first
- Google-only users have no password; the "add password" flow must go through a secure email-based credential creation path
- do not add more providers until the connected-account management UX is designed; adding providers without management UX creates a support burden

### Public Library persona filtering (roadmap)

Planned for a future release after the mode system matures:

- persona-based note recommendations in Public Library discovery (same-profile notes ranked higher)
- cross-profile discovery still allowed so learners can find materials outside their profile type
- filtering UI: optional "Relevant to me" toggle that uses the current user's profile mode for ranking
- implementation must remain additive — no ranking change without the toggle enabled
- do not build until there are enough public notes per profile type to make filtering meaningful

## Known UX Fixes (cross-cutting, no version gate)

These are correctness fixes that ship as soon as they are ready and are not held to a version milestone.

- ~~**AI suggestion modal survives navigation**~~ ✅ — fixed in v0.15.1 branch: `awaitingGeneratedMetadataSuggestionRef` was in-memory-only and reset on component remount; navigating away mid-generation and returning silently skipped the AI title/subject/tags modal; replaced with a `sessionStorage` key (`notelib-awaiting-suggestion:{noteId}`) that is set when generation starts and cleared after the modal fires; `loadDetail` re-arms the ref from storage when returning to a still-generating note so the polling effect can still trigger the modal on completion.

## Product Learning Loop

Capture -> Generate -> Review -> Improve -> Copy -> Repeat

Roadmap decisions should reinforce this loop rather than one-time output generation.

## Legacy planning context

Older phase-by-phase roadmap details are preserved in `/docs/legacy/ROADMAP.md`.
