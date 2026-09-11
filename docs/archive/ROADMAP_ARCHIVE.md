# ROADMAP_ARCHIVE.md — NoteLib

Archived per-version retrospective sections from `docs/product/ROADMAP.md` — every shipped
version's pre-ship Scope/Anti-drift/Completed-so-far detail, moved here 2026-07-10 so
`ROADMAP.md` itself stays purely forward-looking (current baseline, backlogs, open
candidates, future directions). This is a MOVE, not a delete: content is preserved verbatim
except for a handful of stale "Status: In Progress" labels on long-since-shipped v0.17.0/
v0.19.0/v0.21.0 sections, corrected in the move, and a zero-content duplicate `## v0.17.0`
stub heading (no body — the real section immediately follows it in this same file), dropped.

`RELEASES.md` (plus `docs/archive/RELEASES_ARCHIVE.md` for its own older versions) is the
changelog of record for what shipped — these ROADMAP retrospectives duplicate that at the
planning-narrative layer (Scope / Anti-drift / "Completed so far" framing) rather than the
changelog layer. `ROADMAP.md` keeps a one-line-per-version index at each original position.

---

**Kicked off 2026-09-09, signed off 2026-09-09.** `v0.135.0 — Update Signal` is **Released** on `releases/v0.135.0` (PRs #1354, #1355), cut from `main` after `v0.134.0` merged as #1353 and tagged `eac429a4`. **Stage D of `docs/claude-plans/attention-notifications-email-expansion-stage1.md` — the notification substrate's FIRST REAL PRODUCER.** **⚠️⚠️ THE BINDING DESIGN IS THE ADDENDUM'S §2, NOT THE AUDIT'S §17.2: the audit's proposed key `REVIEW_SET_UPDATE:<adoptedCollectionId>` is a PERMANENT SILENT SUPPRESSION BUG** — under the unique index it delivers exactly ONE notification per learner per set, ever, because `deliver` catches the violation and returns the OLD row, `dismissed_at` is not in the index, and retention frees the key only after 90 days AND a read/dismiss, so **a learner who never opens the bell is suppressed forever.** **The corrected key is SOURCE-SIDE: `REVIEW_SET_UPDATE:<sourceCollectionId>:<lastUpdatePublishedAt epochMilli>`, and it needs NO migration** — `lastUpdatePublishedAt` already advances only when `unpublishedChanges` is true, so re-press idempotency is inherited for free. **SCOPE: (1)** `REVIEW_SET_UPDATE` + `LEARNING_SYSTEM`; **(2)** the producer, firing from the `publishReviewSetUpdate` CALL SITE and deep-linking `/collections/{adoptedCollectionId}`; **(3)** episode suppression as a batch filter between audience resolution and fan-out; **(4)** retiring the transitional `ACTION_REQUIRED`. **⚠️ ANTI-DRIFT: NEVER fire on raw source drift; the TRIGGER IS THE CALL SITE, NOT THE STAMP** (`markReviewSetUpdatePublished` has TWO call sites and the other one fires on a set's first-ever publication); **NO COUNT IN THE COPY** (the single open row is the learner's only signal across N publishes, so *"3 new notes"* is false by the second); **NO `deliver()` inside a transaction (R9)** — and **do NOT delete either R9 guard, they cover different angles**; suppression must NOT live in the editorial `AnnouncementAudienceResolver`; **NO email** (D3, R1 unresolved); **reuse `notificationFanOutExecutor`, never resize it (R5/R13)**; **NO Learning Connections work — `[CHECKPOINT — due 2026-09-19]`, denominator ONE.** **⚠️ THE AUDIT'S NUMBERS ARE STALE AND THE PROMPT MUST NOT REPEAT THEM: it says "≤30 recipients" but LET now has 42 adopters; and EXACTLY ONE update publish exists in the product's history (2026-09-09 01:50:47), with four of five source roots never having published one at all. That read WEAKENED the case for shipping suppression now and the owner took it anyway — a judgement call made with the counter-evidence in view, NOT an obvious necessity.** **✅ DECISIONS A1 (dismiss-without-apply DOES re-notify) and A2 (suppression ships with the producer) are SETTLED 2026-09-09.** **⚠️ ONE DECISION OPEN: does this release DELETE `ACTION_REQUIRED`? Deleting the type strands its CATEGORY with no producer — the same defect the audit opens with — and D2 would want that category back. Decide before the Codex prompt.** **⚠️ INHERITED: `v0.134.0` DEPLOYED at 2026-09-09 03:53:07, so its `[CHECKPOINT — due deploy + 1 day]` is 2026-09-10; proximal (a) is ALREADY ANSWERED (`idx_notifications_inbox` live with the exact definition), proximal (b) still owed, and its distal tier stays NOT-YET-MEASURABLE until an announcement is ever published.** Verification: prior is **one scoped cold agent** — this adds a producer to a fan-out path that has never run, and the last two releases each shipped a test that passed for a reason unrelated to its change. **Routing: CODEX.** Full scope and anti-drift in `RELEASES.md`.

**Kicked off 2026-09-09, signed off 2026-09-09.** `v0.134.0 — Notification Foundations` is **Released** on `releases/v0.134.0` (PRs #1350, #1351, #1352), cut from `main` after `v0.133.0` merged as #1349 and tagged. Source: `docs/claude-plans/attention-notifications-email-expansion-stage1.md` (Stage A audit). **Scope is Stage B items 1–3 + 5 ONLY, and it is DDL-FREE** — the taxonomy split (`NotificationType` becomes producer-level identity, a derived `NotificationCategory` carries badge policy), a `dedupKey` widened to take a String discriminator, an `actionable`/`category` field on the response so the frontend stops comparing to the `"ANNOUNCEMENT"` literal, and retention that expires non-actionable unread rows. **⚠️⚠️ IT SHIPS NO USER-VISIBLE CHANGE AND THAT IS DELIBERATE — do not read it as stalled.** The numeric badge **has never rendered in production and could not have**: `countActionableUnread` filters on `{ACTION_REQUIRED}` and **zero code paths produce that type**. **⚠️ THE ENTIRE ARGUMENT IS THE ROW COUNT: `notifications` is EMPTY in production — RE-VERIFIED READ-ONLY 2026-09-09 AT KICKOFF (0 notifications, 0 announcements, 0 dedup keys), not carried over from the audit — so items 1–3 are pure Java and item 5 is one JPQL predicate — and every one of them becomes a data migration with a backfill and a reconciliation the day the first real notification lands.** That window closes silently; nothing announces it. **⚠️⚠️ SPLIT INTO TWO PARTS 2026-09-09. PART 1 (items 1–3 + 5) MERGED AS PR #1350; the DDL-free rule applied to it ALONE.** Item 4 was excluded because `V141` and `V142` were unrun — **BOTH RAN 2026-09-08** (read-only verification of `flyway_schema_history` on 2026-09-09: V141 12:23, V142 15:47, both `success = true`), **so the single stated reason expired and the owner elected to complete ALL of Stage B. PART 2 = items 4, 6 and 7.** **⚠️⚠️ ITEM 6 IS BIGGER THAN THE AUDIT'S §18 SAYS, AND THE GAP IS AN ADMIN SURFACE: `AnnouncementPublishResponse` returns `delivered`/`skipped` computed SYNCHRONOUSLY, `admin/announcements/page.tsx:182-184` renders them, and 22 backend assertions depend on them — async delivery makes those numbers unknowable at response time.** Agreed design: resolve the audience synchronously, hand the list to a core-1/max-2 executor, return `(announcement, recipientCount, queued)`, and change the admin copy to say delivery is *in progress* while preserving the retry affordance (discard-with-log, D6). **⚠️ R9 — fan-out must stay OUTSIDE any transaction — is the invariant most likely to break silently.** **⚠️ THE VERIFICATION TRAP IS NAMED IN THE RELEASE ITSELF: the audit's requirement that "behaviour must be identical after items 1–3" is the `v0.116.0`/`v0.117.0` silent-no-op shape** — `actionableTypes() == {ACTION_REQUIRED}` passes before *and* after, so at least one assertion must be one that **cannot pass against `main`**, and the category **partition** test is it. **⚠️ AND THE AUDIT'S "Verification the first producer owes" SECTION IS A STAGE D LIST, NOT THIS RELEASE'S — seven of its ten items presuppose a Review Set update producer that is not shipping.** **⚠️ NO new enum values without a producer** (that recreates the exact defect the audit opens with); **`ACTION_REQUIRED` is TRANSITIONAL and its javadoc must say so**; **`NotificationCategory` stays derived in Java, never a stored column**; **no `deliver()` call from inside a transaction** (it is deliberately non-transactional so a dedup-index violation cannot take a fan-out down, and item 2 edits the key that catch depends on). **⚠️ NO Learning Connections work — `[CHECKPOINT — due 2026-09-19]` is ten days out, denominator ONE. NO `frontend/app/onboarding` work — `[CHECKPOINT — due 2026-09-11]` is two days out.** **⚠️ R1 IS A LIVE PRODUCTION FINDING THIS RELEASE DOES NOT FIX: the 100/day email cap is breached at 156–159 observed, and it gates `INACTIVITY` only** — indexed in the Backlog Index, blocking every future email producer. Verification: Part 1 shipped on **a single `advisor()` call**; **⚠️ PART 2 IS RE-DECIDED TO ONE SCOPED COLD AGENT framed as falsification** — three gate triggers fire (a defect introduced and fixed in-session, async ordering, and a second change to the same shared methods). **⚠️ THE RE-DECISION IS THE POINT: `v0.133.0` was tiered at one call, gained a write endpoint, ran one cold agent, and had three of seven named claims refuted.** **Routing: CODEX** (backend enum + service + JPQL + DTO, plus frontend). Full scope and anti-drift in `RELEASES.md`.

**Kicked off 2026-09-08.** `v0.132.0 — Publication Boundary` is **RELEASED** on `releases/v0.132.0`, **cut from `docs/planning-publication-boundary-and-plan-corrections`** so that branch's audit commit rides in via the release PR. **Scope is slices P1 and P2** of `docs/claude-plans/official-review-set-update-publication-boundary.md`. **THE GAP IS TOTAL: there is exactly ONE source state, so every curator edit is instantly learner-facing** — adopters are immediately "behind" and new adopters and the public see unfinished curriculum work. **⚠️ THE FIX IS A PER-ROW PUBLICATION STAMP, NOT A SNAPSHOT ARCHITECTURE**, and that works because `applySourceUpdate` is additive-only (`NoteCollectionService:2050` — *"THIS RELEASE REPORTS AND NEVER APPLIES"*), so the boundary only has to gate what becomes visible as an ADDITION. **P1** is the foundation (3 columns + backfill, filtering drift/adoption/`getPublic` to published rows, an atomic idempotent publish endpoint — one migration); **P2** is the curator UX. **⚠️ THEY SHIP TOGETHER DELIBERATELY: P1 alone creates an endpoint with no way to press it while silently stopping every edit from reaching learners — the `v0.130.0` empty-inbox shape.** **⚠️ P3 (the Review Set update notification, formerly Stage 6) IS DEFERRED ON SIZE, and this audit UNBLOCKS it — §7 supersedes the notification plan's §8 drift-signature dedup, which must NOT be implemented.** **⚠️ THREE OWNER DECISIONS ARE OPEN, and decision 1 (accept the narrowed public-view contract) SIZES THE ARCHITECTURE — settle it before the Codex prompt.** **⚠️ NO Learning Connections work: `[CHECKPOINT — due 2026-09-19]` is eleven days out, denominator ONE.** Verification is **at least one scoped cold agent, decided at kickoff**: this moves a visibility boundary and carries a backfill over production rows. Full scope and anti-drift in `RELEASES.md`.

**Kicked off 2026-09-07.** `v0.131.0 — Inbox Polish` is **Released** on `releases/v0.131.0`, cut from `main` after `v0.130.0` merged as #1344 and tagged `3cec79bb`. **Three frontend fixes against the SHIPPED `v0.130.0` inbox**, from `docs/claude-plans/notification-inbox-polish.md` (owner report, 2026-09-07): close the desktop panel on outside click and on `Escape` and drop the `Close` button; make the bell a **toggle** rather than a re-open-and-refetch; and hide the bell while a learner is taking a quiz. **⚠️ FRONTEND ONLY — no backend, no migration, no contract change.** **⚠️⚠️ CORRECTED SAME DAY — THE KICKOFF FINDING WAS WRONG.** It claimed Quick Review's running branch has no in-page exit; it has a *Leave Quiz* button (`:1101`) that the audit missed by grepping for links rather than for `requestLeave`. **All four surfaces needing focus mode already have a running-state exit, so NO exit work is owed**, and **the shared quiz must be DROPPED from the list — `app-shell.tsx:593` returns early for `/quiz/`, so it renders no header, the bell is already absent, and the hook would be a SILENT NO-OP.** Superseded claim: **Quick Review's RUNNING branch has NO in-page exit** — its four `BackLink`s all sit in non-running branches — **and focus mode hides the entire header, so applying it as written would TRAP the learner.** Owner decision the same day: **audit all five surfaces' running branches and add an exit where missing BEFORE the hook goes in.** **⚠️ A `BackLink` elsewhere in the file does not satisfy this — that is what made Quick Review look safe.** **⚠️ Do NOT let the outside-click handler treat the bell as "outside"** (it would fight the toggle), **do NOT stop loading on open**, and **do NOT add a second dismissal handler to the mobile `AppModal` path**. **⚠️ NO Learning Connections work — `[CHECKPOINT — due 2026-09-19]` is twelve days out and its denominator is ONE.** **⚠️ §8's drift-signature dedup is SUPERSEDED and must not be implemented.** Verification is a single `advisor()` call. Full scope and anti-drift in `RELEASES.md`.

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

## v0.40.0 - Weekly Study Plan (Exam Countdown) + Primary Review Set (released)

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
- **Post-adopt guidance nudge (frontend).** Reuse the existing `pickActiveGuidance()` tip system to suggest the adopter set their own target date — same discoverability pattern shipped in v0.39.1 for adoption itself, no new tooltip mechanism.

### Phasing within v0.40.0 (recommended split)

- **Phase 1:** Primary Review Set foundation + per-Goal target date (with the never-copy-on-adopt/self-copy rule) + user-level `studyDaysPerWeek` + the simple total-remaining ÷ remaining-scheduled-days computation with the due-as-floor rule + the three surfaces + the Progress default-view change. This delivers the "what do I do today / am I on track" answer at low risk.
- **Phase 2 (decided at v0.40.0 signoff: slipped to v0.40.1):** the weighted largest-remainder subject distribution and day-of-week interleaving (Mon: Site Analysis + Foundations; Tue: …) — the parts most likely to need iteration. v0.40.0 ships Phase 1's simple total-remaining ÷ remaining-scheduled-days computation only; Phase 2 is gated on Phase 1 proving the countdown mechanic actually gets used, and is picked up as additional scope in `v0.40.1` alongside that release's existing Public Review Set Reachability work (see below).

Anti-drift: no new top-level entity — the weekly plan is a derived, read-time view; the only new persisted state is the `primaryCollectionId` reference, the `target_completion_date` column, and the user-level `studyDaysPerWeek`. No change to `UserEntity.examDate` or the Dashboard board-exam countdown it already drives. Target date is Goal-level only, never on a child Subject plan. No hardcoded profile-specific "Review" vocabulary anywhere the Primary concept is surfaced — must resolve through `getCollectionLabels`. No adaptive/AI-driven scheduling, streaks, or calendar integration. **No nav rename, no Exam Hub change, no Explore page, no Progress full-redesign in this release** — those are recorded in the deferred "Review-Set-Centric Navigation" section and gated on this release proving the Primary concept out in real usage first.

### Completed in v0.40.0 so far

- **Primary Review Set backend foundation.** Added nullable `users.primary_collection_id`, `PUT /collections/{id}/primary`, `DELETE /collections/{id}/primary`, and `primaryCollectionId` on `GET /auth/me`. Backend invariant maintenance now clears invalid primaries and auto-sets the single owned top-level Goal after create, standalone adopt, first-time Goal adopt, top-level delete, and parent changes. Frontend consumption, target dates, user study intensity, and scheduler derivation remain separate follow-up work.
- **Target completion date + study intensity backend.** Added nullable `note_collections.target_completion_date` (top-level Goals only, set via `updateMetadata` PATCH-omit-preserves, cleared via dedicated `DELETE /collections/{id}/target-date`) and nullable `users.study_days_per_week` (`PUT /users/profile/study-days-per-week`, full-replace, 1-7 validated). Neither field is ever copied on adopt or self-copy.
- **Weekly countdown derivation backend.** `GET /collections/{id}/goal` computes `weeksRemaining`, `conceptsRemaining`, `todaysConceptBudget` from target date + study intensity + the existing readiness rollup (due concepts as floor, new-concept pacing fills the rest). Missing intensity defaults to 7 days/week for the math only; only a missing target date hides the countdown.
- **Dashboard primary CTA (frontend).** `DashboardStudyPlanSection` branches on `primaryCollectionId`, showing the owned primary Goal/plan directly (CTA always opens it, never adopts) instead of the course/program-matched recommendation, with a profile-aware "Primary ..." heading via a new `CollectionLabels.primarySingular` field. Falls back to the existing recommendation flow if the primary reference isn't found. Wired into both Dashboard call sites only.
- **Target-date + intensity input (frontend).** `EditCollectionModal` gained the target-date and study-intensity fields, shown only for top-level Goals. Target date shares the metadata PATCH to set, uses the dedicated clear endpoint to unset. Study intensity is sourced/saved separately (`getMe()`/`updateStudyDaysPerWeek`) since it's user-level, not a collection field.
- **Post-adopt target-date guidance (frontend).** Goal adoption now records a session-scoped just-adopted flag and the Goal detail page consumes it once to show a `GuidanceTip` only for freshly adopted dateless Goals. The CTA opens the existing edit modal's target-date field; no primary-setting UI or API call was added.
- **Goal detail "This Week" section (frontend).** `GoalWeeklyCountdownCard` renders above `ReadinessSummary` on the Goal page, hidden when no target date is set; editing the target date refetches the Goal view so the countdown doesn't go stale. Scoped limitation: only shows once a Goal has at least one child Subject plan (matches the page's existing Goal-vs-leaf branching).
- **Review Sets list page primary CTA (frontend).** `/collections` wires `primaryCollectionId` into its `DashboardStudyPlanSection` call site, same treatment as Dashboard. Onboarding's call site is untouched (no owned collections yet during onboarding). The list page's own empty-state augmentation is separate v0.40.1 scope.
- **Progress primary default (frontend).** `/progress` applies the Primary Review Set as a one-time default when no explicit `?collectionId=` is present, using `GET /collections/{id}/goal` for the top-level Goal aggregate instead of leaf-plan readiness. Explicit leaf selections still use the existing `GET /collections/{id}/readiness` path, and `All subjects` stays reachable from the picker after the default resolves.

### Parked (not scoped for this release)

- **Cross-course note reusability.** Its own proposal doc already recommends deferral; revisit only once cross-program overlap is a demonstrated broad-audience maintenance concern, not a single curator's one-off friction. A much cheaper interim exists if the underlying curation pain needs addressing sooner: multi-tag a note/subject with secondary course/programs for discovery, or improve the course/program picker UX during curation — without touching the one-note-one-program architecture.
- **Per-Subject (child-level) target dates.** Only the parent Goal carries a target date in v1; a child Subject plan's contribution is limited to feeding the parent's concepts-remaining count, matching the existing `courseProgram` cascade precedent (parent-level field, children just feed the rollup).
- **Multi-Goal scheduler orchestration.** v0.40.0 assumes one primary drives the coach; simultaneously scheduling across several active dated Goals is deferred until the single-primary model is validated.
- **Manual "Set as primary" UI (resolved in v0.40.1).** This parked follow-up shipped as a collection-detail-page overflow action plus a `Primary` badge, using the existing `PUT`/`DELETE /collections/{id}/primary` endpoints. Eligibility follows the backend rule exactly: any owned top-level collection (`parentCollectionId == null`) can be primary, including childless leaf plans; child Subject plans are excluded.

---

## v0.40.1 - Public Review Set Reachability (released)

Base branch for this release: `releases/v0.40.1` (released). Origin: surfaced while pressure-testing a broader "Review Set discovery redesign" proposal against the Public Library philosophy ("discover knowledge" vs. "follow a structured journey") — the full redesign (Recommended/Trending/Recently-Added/Browse-All ranking) was rejected as premature and overlapping with the deferred Explore-page direction below, but one piece of it survived: a **verified, narrow inconsistency**, not a redesign.

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

### Also targeted for v0.40.1: weekly scheduling Phase 2

Decided at v0.40.0 signoff: the weighted largest-remainder subject distribution and day-of-week interleaving deferred out of v0.40.0 (see that release's Phasing section above) is picked up in `v0.40.1` as additional scope alongside Public Review Set Reachability, not deferred further. Still gated on Phase 1's simple countdown proving useful in real usage before implementation starts; not yet broken into its own scoped bullet list.

---

## v0.41.1 - Review Set Detail Page: This-Set Study Dashboard (released)

Base branch for this release: `releases/v0.41.1` (released). Origin: a product proposal arguing the Review Set detail page still behaves like a collection details screen even though, after Primary Review Set, Weekly Study Plan, Readiness, and Companion, Review Sets have become the learner's primary study surface. Advances a narrow, frontend-only slice of the deferred "Review-Set-Centric Navigation" direction below — specifically the detail-page hierarchy, not the nav/Dashboard reorg, which stays gated on Primary proving out in real usage.

**Scope decision (deliberate, not the proposal's original framing):** this page answers *"what should I do next, in this Review Set?"* — it stays 100% collection-scoped. It does **not** become the learner's cross-journey home; that job stays with `/dashboard`. This is why the built-but-unwired `TodayFocusCard`/`MasterySnapshotCard` (both user-scoped) are explicitly out of scope here — wiring them in would blur this page's job with Dashboard's.

**Core finding: re-composition, not new capability.** Every building block the proposal asked for already exists — `ReadinessSummary`, `GoalWeeklyCountdownCard`, `NextInPlanCard`, `ContinuePlanBanner`, "Next mastery steps", the Primary badge, and `CompanionDisplayCard` (shipped v0.41.0, live on the page today — not a future card, contrary to how the originating proposal framed it). The work is reordering, restyling, and consolidating, in one file (`collection-detail-page-client.tsx`, both the Goal view and Leaf view branches).

- **Information hierarchy reorder.** Identity → Current Journey (weekly countdown) → Primary Action → Readiness → Guidance (Companion) → Subject Plans/Notes → Supporting info, replacing today's metadata-forward hero. Goal view (the true learner-home branch, with Subject Plans + countdown) gets full treatment; Leaf view (note-holding sets) mirrors it at lower ambition.
- **Single resolved primary CTA.** Consolidates three competing next-action surfaces (`NextInPlanCard`, `ContinuePlanBanner`, "Next mastery steps") into one "Continue" action. Resolved **free-tier-first** via the existing `getNextPlanAction`/`continueAction` logic — the retention thesis is about the FREE cohort returning, so the primary path cannot be PLUS/PRO-only. Due-concept review (PLUS/PRO, gated by `canViewConceptHealth`) becomes enrichment inside the Readiness card, not a competing hero button. The terminal exam CTA (e.g. "Take the Board Exam") is a periodic learner checkpoint, distinct from both the primary action and from authoring — demoted in visual weight, not hidden in the admin zone.
- **Badge philosophy.** Identity/status may be badges (Adopted vs Created-by-you; Primary becomes a card-level accent treatment instead of a pill). Metadata (notes-ready count, hours, course/program, Subject Plan count) becomes supporting text, never a badge.
- **Author/Admin zone separation.** Publish, Build, Edit, Manage Companion, Set/Remove primary, Delete consolidated into one visually distinct zone, out of the learner CTA row (today the admin Publish pill sits in the same badge row as learner status badges, and Edit/Primary/Delete/Manage-Companion share a `⋯` menu with learner actions).
- **Companion placement.** The already-shipped `CompanionDisplayCard` gets a durable home in the new Guidance tier — positioned so v0.42.0's Resources section and later Ask Companion slot in without another redesign.

Anti-drift: no backend change, no new endpoint, no new persisted state; does not wire the unwired, user-scoped `TodayFocusCard`/`MasterySnapshotCard`; no day-level scheduler (weekly plan stays a `todaysConceptBudget` number + countdown — Phase 2 weighted-distribution/interleaving work remains separately deferred, see v0.40.0/v0.40.1 above); no nav/Dashboard change, no Ask Companion, no Resources, no Achievements, no new chart library; publish validation and feature-gate/billing mechanics unchanged; all labels continue to resolve through `getCollectionLabels` (no hardcoded "Review" strings).

**Follow-up shipped in the same version:** a second UX review, once the detail-page reorder was live, found the `/collections` list card was the one remaining surface using the old Primary pill badge. Migrated it to the same card-level accent treatment as the detail hero (`frontend/app/collections/collections-page-client.tsx`), confirmed the card's existing identity/state/metadata tiering (from two prior "badge hierarchy" polish passes) already matched the requested hierarchy, and documented the badge-classification rule (identity vs. state vs. metadata; metadata is never a badge) in `docs/features/collections.md` for future Guided Learning additions to check against.

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

Base branch: `releases/v0.33.1`. The study-plan line. v0.33.0 shipped the readiness *lever*; the validation pull (`docs/archive/journey-validation-pulls.md`) showed the plan-adoption retention bet was never testable — **4 adoptions / ~153 users, 1 goal (Accountancy) with a ready plan, 0 post-adopt returns**. The binding constraint is **curated-plan coverage.** v0.33.1 leads with coverage plus two UX-clarity polish items, and was **deliberately expanded** to include **Study Plan Hierarchy Phase 1** (one level of Goal → Subject nesting) so curated plans can be built *with levels* from the start — a schema change that makes v0.33.1 a large patch (accepted). Hierarchy Phases 2–4 stay a future, validation-gated initiative.

**Curated Plan Coverage (headline — content/curation ops first).**
- Run the follow-up inventory query (in `journey-validation-pulls.md`): does seeded public *note* content exist for the real top goals (just not assembled into plans) → cheap **assembly/curation ops**; or is there little content → a **seeding** job (Bulk Generation) first.
- Target: ≥1 complete, credible, Study-Pack-ready curated plan per goal the **actual** public learners have (let the inventory say where they are — do not assume ALE/PNLE/LET).
- Content/curation work, not new architecture. No new endpoint, model, or AI synthesis.

**Study Plan Hierarchy — Phase 1 (Goal → Subject plans; backend + UI) — shipped.** Added `parent_collection_id` (self-referential, **2-level enforced**: parent must be top-level, child must have no children — cycles impossible); a Goal detail page listing child Subject plans with each child's readiness plus a cheap **Goal % = Σ child.mastered / Σ child.total** (sums per-child counts, so **no cross-subject concept re-dedup**); the owned-plans list shows top-level only. Added the single-canvas **Study Plan Builder** at `/collections/{id}/builder`: Goal = canvas, Subject plans = draggable/collapsible sections, notes = cards that can be added, reordered, removed, or moved across Subjects. The builder replaces the scattered nest/unnest detail-menu curation path and orchestrates existing collection endpoints; the only new backend capability is sibling ordering (`sibling_position` + `PUT /collections/{id}/children/order`). Modules stay as label-sections and are off the builder canvas. Reuses the `NoteCollection` model, `getReadiness`, `ProgressReportService`, `ReadinessSummary`, dnd-kit, and `getCollectionLabels` (no `ProfileType` branching). **Deferred** to later phases: recursive adopt-the-whole-Goal, per-module %, metadata/est-time/difficulty, arbitrary depth, direct notes on a Goal. This is a **deliberate, scoped reversal** of the "no parent/child collections" rule (two levels only). Architecture audit: `docs/archive/STUDY_PLAN_HIERARCHY_PLAN.md`; Codex prompt: `docs/codex-prompts/v0.33.1-study-plan-hierarchy-phase1.md`.

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

- **Curation, never generation.** Sequencing is **human/admin curation over existing seeded content**, not AI-synthesized per user. This stays on the right side of the standing "never generate curriculum" rule. The forbidden version is *"auto-generate a personalized plan"* — do not build that here. **Clarified (not reversed) under the Guided Learning Initiative, below:** this rule is learner-facing only — a learner never gets an auto-generated plan. Curator-facing AI-assisted authoring of Official Review Sets/Companions, with mandatory human review before publish, is a documented exception (see "Guided Learning Initiative" → "Documented rule clarification").
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

Theme: v0.32.1 surfaced the premium exams and reframed pricing, but conversion was still ~0 of ~153 verified users. **Thread 3 (the funnel diagnosis) ran first and re-scoped this release.** The real, un-conflicted constraint is **near-zero W1→W2 retention (5.6%, recent cohorts ~0%)** — users activate (68.6%) and engage once (58.8%), then don't return. (An initial read flagged a "broken checkout" from "6 upgrade clicks → 0 `CHECKOUT_INITIATED`"; that was a **metric-inception artifact** — `CHECKOUT_INITIATED` was added in v0.31.2 while `UPGRADE_CLICKED` is far older, and a live upgrade reached the real Xendit invoice. Checkout works.) Free is **not** too generous. Full data: `docs/archive/conversion-funnel-finding.md`. **Anti-drift: do not raise exam quota numbers** — quota size is not the constraint; retention is.

### Thread 3 — Conversion funnel diagnosis *(done)*

Read prod `/admin/funnel` (`AdminFunnelService`): activation 68.6% (105/153), value loop 58.8%, free-quota-hit 0.0% (study-pack-only), W1→W2 retention 5.6%. Corrected reading: checkout is **not** broken (the "6 → 0" is a metric-inception mismatch — see finding doc); the real constraint is retention. Output: `docs/archive/conversion-funnel-finding.md`. Drives the priorities below.

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

**Status: Released**

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

**Status: Released**

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

**Status: Released**

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



---

## Second archive pass — 2026-09-07 (`v0.126.0`)

The sections below moved out of `docs/product/ROADMAP.md` on 2026-09-07, resuming the convention
the 2026-07-10 pass established and that had lapsed for 85 releases. **A MOVE, not a delete —
content is preserved verbatim.** ⚠️ They are ordered NEWEST-FIRST, as they were in `ROADMAP.md`,
whereas the sections above this divider are oldest-first; nothing was re-sorted, because
re-ordering 60 sections would have obscured the verbatim guarantee this file exists to make.

## v0.85.0 — Domain Signal Integrity (Released, base branch `releases/v0.85.0`)

**⚠️ Status corrected at the `v0.97.0` kickoff, 2026-08-29** — this heading read *In Progress* for **eleven releases**, found by `docs/claude-plans/claude-docs-staleness-audit.md`. **⚠️ The per-version prose sections below stop here, at `v0.85.0`, while the project is on `v0.97.0`.** The **Current Release Baseline** and the **Backlog Index** above are current and are the authoritative surfaces; these per-version sections lapsed rather than being discontinued by decision. **Nothing below is maintained — read it as history, and do not treat a missing section as a missing release.** Whether to resume or formally retire the practice is an open one-line decision, indexed with the audit that found it.

Kicked off 2026-08-17, cut from `main` after `v0.84.0` merged and deployed. Origin: the **Domain Context Catalog** proposal (2026-08-17), which a cold-context agent assessed and recommended declining — and which, while auditing the enum's real responsibilities, surfaced a defect much larger than itself. Assessment: `docs/claude-plans/domain-context-catalog-assessment.md`. Sizing: `docs/claude-plans/quantitative-context-coverage-read.sql`.

### Planned scope

1. **A declared per-value quantitative property**, replacing the substring guess. Not a longer keyword list — that keeps guessing. Must not remove guidance from any value that already matches.
2. **Domain Context descriptions beside the authoring select** — copy only, zero schema. The subject lists already exist in `canonical-knowledge-architecture-out/08-…:56-67` and appear nowhere in the product.
3. **Two `ADR-001` factual corrections** — the "single badge" and "vocabulary learners see" claims are both false.

### Explicitly out of scope — anti-drift

- **The Domain Context Catalog, admin CRUD, Domain Categories, prompt-hint fields, and any new enum value** including `General Engineering`. Declined on evidence 2026-08-17.
- **Changing what an existing value resolves to.** `effectiveAuthoringDomain` still returns `getLabel()` — the label is the prompt payload, so changing it would rewrite what past generations were told.
- **The enum stays an enum**, so `@Enumerated(EnumType.STRING)` and the three `Enum.valueOf` projection paths are untouched.
- **Any migration.**

## v0.84.0 — Public Explore (Released, base branch `releases/v0.84.0`)

Kicked off 2026-08-17, cut from `main` after `v0.83.2` merged. **Slice C of Discovery System Stage 0** — see `docs/claude-plans/discovery-system-stage-0-scoping.md`. Slice A shipped in `v0.83.2`; Slice B was dissolved at that release's kickoff.

### Planned scope

1. **Render `/explore` for anonymous visitors** — remove the client-side gate; there is no `middleware.ts`, so this is a component change.
2. **A generalized discovery-intent cookie** mirroring `lib/exam-intent.ts`. **Not the `redirect` param**, which signup drops.
3. **Canonical, OG and structured data for `/explore`**, including the canonical direction against `/public/library`'s existing `CollectionPage` JSON-LD.
4. **Anonymous Review Sets tab: full catalog, Adopt gated at click** (owner decision).
5. **`robots.ts`** per the owner decision, with the atomicity condition recorded.

### Explicitly out of scope — anti-drift

- **Redirecting `/public/library`.** Stage 3, doctrine-blocked by `AGENTS.md`'s Explore Navigation Rule pending its amendment. Subject and note-detail pages are never-redirect regardless.
- **Backend permit changes.** Slice A already granted what the facets need.
- **Removing Exam Hubs from top nav** — Explore has no exam-aware browsing mode yet.
- **A viewer-type analytics dimension** — already derivable via `user_id IS NULL`.
- **Any change to `EXPLORE_VIEWED`'s firing condition or metadata**, and no new events.
- **Any migration.**

## v0.83.2 — Anonymous Discovery Access (Released, base branch `releases/v0.83.2`)

Kicked off 2026-08-17, cut from `main` after `v0.83.1` merged. **Slice A of Discovery System Stage 0** — see `docs/claude-plans/discovery-system-stage-0-scoping.md`.

### Planned scope

1. **Permit anonymous `GET /subjects` and `GET /course-programs`** — two `permitAll()` additions mirroring the existing `/tags` rule. Both controllers already gate `scope=mine` themselves, so this grants only what they were written to serve. **Needs an anonymous `scope=mine` 401 test**, which is what proves the widening did not overreach.
2. **Remove the `/exam` BackLink** — `/exam` is top-level in the marketing nav and footer, not a sub-page of Public Library.

### Explicitly out of scope — anti-drift

- **Making `/explore` anonymous.** It keeps its client-side gate. No canonical/OG/structured data, no discovery-intent cookie — those are Slice C.
- **A viewer-type analytics dimension.** Rescoped at kickoff: already derivable via `user_id IS NULL`. Building one would duplicate existing data.
- **Slice C**, blocked on the Review Sets tab and `robots.ts` owner decisions.
- **Stages 1–3.** Stage 3 also stays doctrine-blocked by `AGENTS.md`'s Explore Navigation Rule pending its amendment.
- **Any migration**, and any change to existing permit rules.

## v0.83.1 — Note Creation Integrity (Released, base branch `releases/v0.83.1`)

Kicked off 2026-08-17, cut from `main` after `v0.83.0` merged and deployed the same day.

Fixes two `NoteEntity` insert paths that never set the `NOT NULL` `target_profile_type` column, 500ing `/study` paste-text, image upload, confirm-text and share-remix **after** the LLM call has billed quota. Pre-existing since 2026-03-21; surfaced by `v0.83.0`'s cold-context pressure test.

### Planned scope

1. **Set the value on both paths** — lift the owner-profile mapping to `NoteTargetProfileType.forOwnerProfile(ProfileType)`, with `NoteService` delegating so there is one definition.
2. **Tests that would have caught it** — assert the **persisted** value through the real paths, including a `BOARD_EXAM` profile where a constant and a derivation diverge.

### Explicitly out of scope — anti-drift

- **Any migration**, and specifically **no `DEFAULT` on `notes.target_profile_type`** — it would mask the bug class rather than fix it, and the column is phase-4 material gated on `[CHECKPOINT — due 2026-09-16]`.
- **A hardcoded constant** in place of the profile derivation. `SPEC.md:129` documents the contract; a constant falsifies a line corrected during `v0.83.0` signoff.
- Any change to `NOT NULL`, the CHECK constraint, the index, or the enum's values.
- Anything Discovery-System-related. That is scoped separately and needs its own kickoff.

## v0.83.0 — Target Audience Removal (Phase 2) (Released, base branch `releases/v0.83.0`)

Kicked off 2026-08-17, cut from `main` after `v0.82.0` merged and deployed the same day.

**Step 2 of the revised Target Audience retirement.** `v0.81.0` was Step 0 (`V115` widened the Challenge bank key so an authoring correction at scale stops failing sessions); `v0.82.0` was Step 1 (`V117` backfilled Authored Depth onto 819 curator notes). This release executes **phases 2 and 3 of the already-ratified `ADR-001` amendment** — it is not a new decision, and the amendment reducing five axes to four was ratified 2026-08-16.

### Planned scope

1. **Remove the Public Library discovery surface** — the `target_profile_type` WHERE clause in `PublicLibraryRepositoryImpl`, the `PublicLibraryFilterCriteria` field, the filter chips, and `?audience=` parsing/building in `public-library-url.ts`.
2. **Remove authoring and display** — bulk generation dropdown, note editor field, note detail display, the four DTO fields, onboarding's `mapProfileTypeToNoteTargetProfile` write, and the private Library's unused projection.
3. **Documentation** — `ADR-001`, `docs/features/notes.md`, `docs/features/public-library.md`, `SPEC.md`, GPT context modules, re-read against the **final** code state rather than per-PR.
4. **Replace the retired discovery facet with Authored Depth** — tolerant `?level=` filtering on `notes.learner_level`, with chips populated only from distinct non-null depths present on public notes.

### Authored Depth replacement decision — resolved 2026-08-17

The production read found 120 curator-owned public notes formerly classified as `STUDENT`: 26 `JUNIOR_HIGH`, 11 `SENIOR_HIGH`, 3 `GRADE_SCHOOL`, and 80 with NULL Authored Depth. Owner decision: ship the replacement filter before signoff. It restores the cross-program depth-filter mechanism but not full coverage; the 80 NULL-depth notes remain outside every depth-filtered result until curators classify them. This does not revive the killed `V118` backfill and writes no depth.

### Explicitly out of scope — anti-drift

- **Dropping `notes.target_profile_type`.** It is `V117`'s input and `[CHECKPOINT — due 2026-09-16]` cannot run without it. Phase 4 waits on that report. Same for `bulk_generation_result.target_profile_type` and the `NoteTargetProfileType` enum.
- **Any migration.** None ships in this release.
- **Re-gating Phase 2** on `[CHECKPOINT — due 2026-09-13]`. Ungated 2026-08-16 by owner call; the confound is recorded, not waited on.
- **A `V118` `STUDENT` depth backfill.** Audited in `v0.82.0`, zero eligible notes. Do not re-propose.
- **Any runtime use of Target Audience as a depth fallback.** Migration evidence only.
- **`V117`'s written rows.** Untouched by this release.

## v0.82.0 — Authored Depth Backfill (Released, base branch `releases/v0.82.0`)

Kicked off 2026-08-16. Theme: give curator notes the depth they were always meant to carry, so the axis that will replace Target Audience actually holds information.

### Why now — the prerequisite is genuinely cleared

The revised sequence put a hard Step 0 in front of any depth backfill: a mass authoring correction would strand Challenge bank rows whose keys could be regenerated into a collision that failed the learner's session. **`v0.81.0` shipped `V115`, which widens the bank uniqueness key to include `learner_level`** — so a regenerated question at the new level coexists with the preserved old-level row instead of colliding. Stranded rows remain unreachable; that is degradation, not failure.

### What is ratified, and what is deliberately not the reason

The amendment reduces `ADR-001`'s five axes to four. **The justification is that Target Audience is absent from every prompt and its access-control purpose was never implemented — not that Course / Program predicts it.** That correlation (~99.3%) is an artifact of a board-heavy catalog and would break the moment general-student or professional content grew. A second opinion rejected it as grounds for an irreversible step, correctly.

### Explicitly out of scope

- **Removing Target Audience** — no column drop, no field removal, no authoring change. This ratifies direction; removal is later phases.
- **The Public Library audience filter** — out of scope *for `v0.82.0`*. ⚠️ **The stated reason is SUPERSEDED and must not be re-derived as a live gate:** this read "Phase 2 changes discovery inside `[CHECKPOINT — due 2026-09-13]`'s window," but Phase 2 was **deliberately ungated from that checkpoint on 2026-08-16 (owner call)** — the audience chip has been a secondary filter since `v0.79.0` made course/program primary, so the Explore confound is recorded rather than waited on. Phase 2 ships in **`v0.83.0`**.
- **Learner-owned notes** (4,645). Private, feed no filter, and a depth write there is an authoring decision their author never made.
- **Mapping `STUDENT`** to any depth. It spans four.
- **Any runtime use of Target Audience as a depth fallback.** Migration evidence only, one time.

## v0.81.0 — Challenge Bank Integrity (Released, base branch `releases/v0.81.0`)

Kicked off 2026-08-15. Theme: a learner's banked Challenge questions should survive an authoring correction, and a bank write should never break the session it belongs to.

### Why these two defects, and why now

Both were recorded as `v0.70.0` Known Limitations on 2026-08-04 by `#986`'s pre-commit audit, as an interaction between two items in the same release. Both are real and user-facing on their own: one can hand a learner a question they have already answered, the other makes a documented guarantee false. Neither has been fixed in eleven releases.

**What surfaces them now is the Target Audience retirement direction.** Its revised sequence requires backfilling Authored Depth onto ~905 curator-owned notes — "an authoring correction, at scale," which is verbatim the condition defect (1)'s own record names as making it unbounded. **This release is Step 0 of that sequence and is a hard prerequisite for it.**

### Explicitly out of scope

- **All Target Audience code.** The `ADR-001` amendment is ratified as of 2026-08-16, but it records direction and gates only — it does **not** authorize field, filter, or schema removal. This release changes no Target Audience code.
- **The depth backfill itself** — gated on ratification and on the Information Technology audit.
- Wholesale deletion of existing bank rows; `ADR-001` rule 2 preserves them.
- Challenge Quiz surfacing or CTA behaviour — `[CHECKPOINT — due 2026-10-15]` reads that.

### Shipped

- `V115` preserved all bank rows and widened question uniqueness to `(user, Study Pack, question, learner level)` with PostgreSQL nulls-not-distinct semantics. Authored-depth corrections no longer collide with stranded rows at the previous level; level-scoped reads deliberately remain unchanged.
- Generated bank persistence now writes a single batch inside its warning catch, joining the caller's transaction. Flush-time collisions and other row failures are absorbed without skipping later rows or breaking the Challenge session, making the documented best-effort contract true.

## v0.80.0 — Instrumentation Integrity (Released, base branch `releases/v0.80.0`)

Kicked off 2026-08-15. Theme: the events four September decisions rest on should actually arrive.

### Why this, and why it cannot wait

**Verified in code at kickoff, not inferred:** `trackAnalyticsEvent` (`frontend/lib/api.ts:2844`) uses a raw `fetch` with `buildAuthHeaders()`; `fetchWithAuth` (`:2164`) refreshes and retries on 401; analytics does neither and swallows the failure at `:2854`. Access tokens live **15 minutes** (`application.yaml:428`).

**The bias is the point.** A uniformly lossy pipeline adds noise. This one drops the *click* half of a ratio more often than the *impression* half, because impressions fire immediately after a data load while clicks can follow an idle page. Both `[CHECKPOINT — due 2026-09-14]` rows read an impression→click rate, so the defect does not blur those answers — it tilts them.

**Timing inverts the usual caution.** The standing rule against touching instrumentation inside a live window assumes the change alters what gets counted. This alters only whether a counted thing arrives. `v0.78.0`'s window opened 2026-08-15; shipping now leaves ~29 of its 30 days clean, and deferring leaves none. **Waiting is the option that damages the read.**

### Explicitly out of scope

- Any change to what an event means, when it fires, or what metadata it carries. New events, removed events, changed firing conditions — all out.
- Rewriting existing catalog rows. The normalization fix applies to creates going forward; rewriting live rows would change filter chips users already see.
- Migrations.
- The onboarding catalog-first follow-up, still gated on `[CHECKPOINT — due 2026-09-11]`.

### Carried caveat, to be annotated before its read

`v0.74.0`'s `[CHECKPOINT — due 2026-09-12]` secondary metric (`STUDY_PACK_QUIZ_TAB_OPENED_AFTER_UNLOCK`) is frontend-fired and its window opened 2026-08-13, so roughly 3 of 30 days will sit on the old delivery behaviour. Its **primary** metric is backend-fired and unaffected. Record this on that row before the read rather than discovering it in September.

## v0.79.0 — Catalog-First Vocabulary (Released, base branch `releases/v0.79.0`)

Kicked off 2026-08-15. Theme: the course/program a learner picks should be one the product can actually act on.

### Why this, and why now

`v0.78.0` built the machinery to recommend a program-matched Study Plan, then its own production reads showed **60 of the 189 planless learners cannot be matched at all — because of vocabulary, not content or features.** 15 sit on `Professional / Board Exam Review` (a study *context*, deliberately absent from the catalog), 3 on `Computer Science`, 2 on `Software Engineering`, plus free-typed values like `High School` (7). `v0.78.0`'s checkpoint kill criterion names this population by hand; this release is the first move against it.

**Every other substantial candidate is checkpoint-gated until 2026-09-11 through 09-14.** This one is not, provided onboarding is left alone.

### The defect, verified in code at kickoff — do not re-derive

Two independent sources of truth that drifted: `COURSE_PROGRAM_SUGGESTIONS` (`frontend/lib/learning-profile.ts`) offers **31** hardcoded values; `V106__course_program_catalog.sql` seeds **21** canonical rows; **16 overlap.** The proposal's audit logged this as C8/C9 on 2026-08-11; it was re-verified against the code at this kickoff rather than taken on trust. `GET /course-program-catalog` is already `USER`-readable (`CourseProgramCatalogController:28`), so no new endpoint is required.

### This is the counter-proposal, not the amendment

`docs/claude-plans/course-program-canonical-catalog-proposal.md` records an owner proposal to lock the field to the catalog with a *"Request Program"* queue, **and** a counter-argument that free text is currently the demand mechanism which built the 21-row catalog in the first place. The doc's recommendation is to ship the softer fix first — catalog-first suggestions plus constraining public discovery — and lock down later only *"with evidence the softer fix failed, and with the 4,480-note migration designed rather than discovered."* **Shipping this release does not ratify the amendment.** It is the evidence-gathering step before that decision.

### Explicitly out of scope

- The `ADR-001` amendment: locking the field, the request queue, and whether blank is a valid learner program.
- Any migration or rewrite of the 4,480 existing free-text learner notes, including the 15 on `Professional / Board Exam Review`.
- Seeding, renaming, or retiring any catalog row — the catalog is read-only here.
- **Onboarding's copy of the course/program field**, deferred on value asymmetry rather than blanket caution: reordering a dropdown's suggestions loses almost nothing by waiting 27 days, whereas `[CHECKPOINT — due 2026-09-11]`'s onboarding-completion read against a 62.4% baseline cannot be re-run. A follow-up applies the same change after that date.

## v0.78.0 — Post-Mastery Next Step (Released, base branch `releases/v0.78.0`)

Kicked off 2026-08-14. Theme: a learner who has just mastered a pack should be told **what to study next**, not left on a screen whose secondary slot is empty.

### Why this, and why now

Of the three candidates with live Backlog Index rows, this is the only one whose gate is clear today. **Adaptive Practice slice 2** is either cross-pack — which needs canonical concept identity and is ADR-sized — or means making the dashboard the *primary* route by demoting an entry point, which the standing **2026-09-12** constraint forbids. **Support Another Learner**'s own gate demands a pre-scoping audit ("does Phase 1 already exist?") plus an unresolved consent model and minors/DPA question. **Post-mastery (a)** is the row the index itself describes as unblocked and needing only a scoping pass.

### Leg (a) only — and the a/b split is the scoping decision, not a detail

The backlog row is explicit that **two features hide inside one sentence**. Leg (b), "a similar note," needs subject/tag/program overlap or embeddings — that is the **Discovery System**, already in the index and deliberately deferred until `[CHECKPOINT — due 2026-09-13]` (Explore engagement) reports. The row's instruction is to fold (b) into that initiative rather than build it standalone, for the same attributability reason that defers the Discovery System itself. **Leg (b) is out of scope and stays out.**

### Three premises verified in code at kickoff — do not re-derive

The row's claims were written 2026-08-13 and all three were checked before scoping, because every blocking finding in recent pressure tests came from an unverified "X already does Y":

1. **The mastered branch carries no secondary action.** `PostSessionNextStepService.resolveQuickReviewNextStep` passes `null` as `secondaryAction` (`PostSessionNextStepService.java:215`). The slot is empty by construction — `v0.74.0` removed Adaptive Practice from that screen in the same release that added the mastered branch.
2. **`TodayFocusType.STUDY_SUGGESTION` has exactly one call site** — `DashboardService.java:188`, the generic empty-state fallback. Reusing the type here **widens** it; the Dashboard behaviour must not change.
3. **There is no next-note query anywhere.** `NoteCollectionItemRepository` has `findByCollectionIdOrderByPositionAsc` and nothing resembling "next".

A fourth check, made while drafting the Codex prompt, **resized the release: it is backend-only.** `post-session-next-step.tsx:137` already renders `response.secondaryAction` generically, and its absence branch already renders nothing — so filling the slot needs no frontend change and "no next item" needs no empty state.

### What "next" means — settled at kickoff, owner-decided

**Lowest `NoteCollectionItemEntity.position` in the plan that the learner has not practiced.** Study Plans already carry explicit ordering (`position`), and `NoteCollectionService.toProgressResponse` already defines practiced as **`lastSessionCompletedAt != null`**. Reusing that definition is the substance of the decision, not a shortcut: a suggestion derived from a *different* notion of done could name a note the plan's own progress counter already counts as complete. **Strict-next-position** was rejected for exactly that (it suggests notes already finished); **readiness-weighted** was rejected because it overlaps the weak-concept recommendation `v0.77.0` just shipped and would create a competing resolver.

When a note belongs to several plans, resolution is the user's **Primary Review Set** (`users.primary_collection_id`) if it contains the note, else the most recently updated containing collection. Deterministic, never arbitrary.

### Explicitly out of scope

- Leg (b), similarity, embeddings — gated behind `[CHECKPOINT — due 2026-09-13]`.
- Any change to `STUDY_SUGGESTION`'s Dashboard empty-state behaviour.
- A **9th** "what's next" resolver — the branch goes inside `PostSessionNextStepService`, one of the 8 the Companion Guidance Doctrine pressure test already counted.
- Any Adaptive Practice entry-point change, in either direction, before **2026-09-12**.
- The admin LaTeX backfill, dropped deliberately in `v0.74.0`.

### Scope expanded 2026-08-15 — sized against production, and the code falsified the first two plans

Owner direction: put everything mastery-related in this release rather than kicking off and signing off small ones. Full evidence chain is in `RELEASES.md` under *Scope expanded 2026-08-15*; the load-bearing numbers are **69% of packed-note learners (189 of 274) have nothing in a plan**, so the shipped suggestion can never fire for them, and of those **106 already have a published plan for their exact program**.

**Two plans were proposed and discarded against the code before anything was built**, which is why this row exists:

1. *"Add a `courseProgram` filter to Explore and deep-link the Dashboard pointer into it."* **Already shipped.** `resolveExploreTab` defaults to `review-sets`, and that tab's `PublishedPlansPageClient` auto-filters to the learner's own `courseProgram`.
2. *"Add a program pointer to the post-mastery slot."* **Falsified by (1).** The 106 already have that exact route from the Dashboard, see it, and have not adopted — so the gap is **conversion, not discovery**, and a second generic pointer is the medicine already failing.

What ships instead is **specificity plus measurement**: the Dashboard names one matched plan again, the post-mastery slot offers the same plan when the note is in no plan, and both are instrumented because `ExplorePointerCard` fires **no analytics today** — nobody can currently say whether the pointer is unseen, unclicked, or abandoned after the click.

**This amends the `v0.67.0` "Explore Owns Discovery" convergence** (`docs/claude-plans/v0.67.0-explore-owns-discovery-ia.md`). That decision removed Dashboard discovery because the Dashboard duplicated Explore's *grid*. **One named plan with a reason is a recommendation, not a duplicated browse surface**, and the ratified *Adaptive Practice as the recommendation engine* direction puts recommendation on the Dashboard. Explore keeps browse. **This is not a licence to restore the grid.**

### Folded in — the ~23 raw-LaTeX packs (owner-executed)

Owner-selected into this release at kickoff. **`[EFFORT]`, curator time rather than engineering**, and not urgent because `lib/math-normalization.ts` repairs the text at display time — nothing is broken on screen; this is about the stored text. **Re-run `docs/claude-plans/v0.74.0-latex-affected-notes.sql` against production before regenerating anything**: the renderer fix may already make some `NEEDS_FIX` rows read correctly, which would shrink or empty the list. Hand-regenerate only the survivors.

## v0.76.1 — Adaptive Practice Entry Attribution (Released, base branch `releases/v0.76.1`)

Full Planned Scope and anti-drift rules are in `RELEASES.md`. This section records why a patch was the right shape.

### Why a patch, and why now rather than in the next feature release

The work is one metadata field. What makes it urgent is a **date**: the `[CHECKPOINT — due 2026-09-12]` reads on that day, and the field only has value if it has been collecting data for a meaningful window beforehand. Folding it into a larger release would couple a deadline to work that has none.

**The gap it closes is one this project has already paid for.** The Challenge Quiz adoption read (c) sat **NOT MEASURABLE for months** — `CHALLENGE_QUIZ_STARTED` could not separate *seen-and-ignored* from *never-reached*, and the read only closed once `v0.74.0` shipped impression and click events. `ADAPTIVE_PRACTICE_STARTED` is in exactly that position today, and the due date is 29 days away.

### Why it cannot contaminate the read it serves

The checkpoint's **primary** metric is a total count of starts per active learner, compared post-deploy against the equivalent pre-deploy window. This release adds a metadata field to an existing event and changes no user-visible behaviour, so that count is untouched. It only makes the **secondary** question — where the surviving starts originate — answerable.

### Why the direction docs ride along

`main` **auto-deploys to production on merge.** The two ratified direction docs and the re-specified checkpoint remedy were originally opened as a separate PR to `main`; merging that separately would have meant a second production deploy purely for documentation, interrupting learners mid-session for no user-facing benefit. They are retargeted into this release so there is **one merge and one deploy**.

### The constraint this release must not break

**Do not remove the Challenge Quiz Adaptive Practice entry point in this release.** It is ratified direction, but `v0.74.0` already removed the Quick Review route and the checkpoint measuring it does not read until 2026-09-12. A second removal inside the same window confounds the two and destroys the read rather than answering it.

## v0.76.0 — Messaging Architecture: The Money Surfaces (Released, base branch `releases/v0.76.0`)

Second slice of the ratified Messaging Architecture initiative (Backlog Index row, ratified 2026-08-01). Full Planned Scope and anti-drift rules are in `RELEASES.md`; this section records the scoping decision.

### Why the money surfaces, and why not everything at once

The ratified row is explicit that this is **incremental**: *"Each remaining surface needs its own scoping pass and its own `/kickoff`."* The remaining surfaces are the pricing page, the in-app paywall/upgrade prompts, the landing page, and the Exam Hub upsell.

**Pricing + in-app upgrade are one slice because they answer the same question — *should I pay?*** The landing page answers *what is this?* and the Exam Hub upsell answers *why this mode?*. Bundling them would produce a copy sweep with no single audience decision to test against.

### `FREE.title` — the revert is the specification

`v0.68.0` shipped the hero, supporting paragraph and the `PLUS`/`PRO` taglines, then **wrote an outcome-framed `FREE.title` and reverted it.** The recorded reason is the constraint on this release: the candidate had been derived from **consistency with its siblings**, which contradicts the ratified tier placement of **`FREE = adopt`** (adopt an Official Review Set and study it). Deriving it from `PLUS`/`PRO` symmetry a second time reproduces the same error.

The test to apply: **if the new title only reads well beside `PLUS` and `PRO`, it is wrong.** It has to be true of what FREE actually offers under the ladder.

### The sharpest contradiction on the page

`PLAN_COMPARISON_ROWS`' "Best for" row currently reads *"Light review and trying the core study loop"* — feature vocabulary, sitting directly beneath a hero promising a learning system. It is the most visible remaining mismatch and is why the comparison table is in scope alongside the titles.

### A constraint carried from `v0.68.0`, not rediscovered

`FREE.description` was rebalanced at that signoff **for card-alignment reasons, not positioning**. Length parity across the three plan cards is a real layout constraint — a prior release had a regression whose true cause was `description` length drift across four card renderers, not title length. Revisiting the descriptions here is deliberate, but the parity constraint travels with it.

## v0.75.0 — Authoring by Inference (Released, base branch `releases/v0.75.0`)

Implements `ADR-001` → *"Authoring populates by inference, not manual classification"* (direction, added 2026-08-04). Full Planned Scope and anti-drift rules are in `RELEASES.md`; this section records the kickoff findings and the one open design call.

### Both of the ADR's sequenced gates were already clear — verified at kickoff 2026-08-13

`ADR-001`'s sequencing subsection blocked this work on two things, and neither still holds (the heading now reads *"Sequencing — BOTH GATES CLEARED"*, amended by this release):

- **R4 resolved 2026-08-04**, passing on all three steps, recorded in `ADR-001` → *R4 verification* and in that item's own Backlog Index row.
- **Editability shipped in `v0.70.0`.** The ADR states *"authoring metadata is not editable once a note reaches `STUDY_PACK_READY`, so making those fields editable is the true first step."* `AGENTS.md:1081` records the opposite: on a `STUDY_PACK_READY` note, Edit stays on Note Detail and Teacher/Admin authors may edit Target Audience, Domain Context, and Note Learner Level. `private-note-detail-page-client.tsx:1445-1452` opens that inline editor for any non-draft note.

**The ADR text has been stale for four releases**, and an ADR outranks a feature doc — so a stale gate inside one blocks work that is in fact unblocked. Item 6 amends it; The Backlog Index's *Authoring by inference* row carries the identical staleness and is corrected in the same pass.

### The two legs are not the same size

**Leg 2 — author profile → depth — is implementable today and completes an existing convention.** `bulk-generation-page-client.tsx:132` already pre-fills `courseProgram` from the author's profile. `learnerLevel` (`:109`) is restored only from a saved stash, with no profile fallback; `/notes/new` initialises `learnerLevel: ""` (`note-editor-page-client.tsx:163`) with no pre-fill at all. Depth is the one axis left out of a pattern already shipped beside it. Every account has a non-null `learnerLevel` after onboarding, so the fallback always resolves.

**Leg 1 — Review Set → depth — has no source, and needs a trigger as well as a column.**

- `NoteCollectionEntity.java:45` carries `courseProgram` and no `learnerLevel`.
- No plan-template entity carries one: `sourcePlanId`'s only level-adjacent sibling, `OfficialStudyPlanWishlistEntity:27`, has `courseProgram` and no depth.
- **No authoring surface knows its target Review Set.** `/notes/new` accepts only `mode` and `source`, every entry point into it passes neither, and bulk-generate has no collection field.

So *"a CE Board Review set is board-depth by construction"* — the ADR's own justification for admitting a curation container as a legitimate depth source — is a human reading of a title today, not a stored fact any code can read.

### Item 3 — RESOLVED at scoping, 2026-08-13

**The decision: add an optional Review Set selector to bulk-generate, so the Review Set is known *before* the notes exist and leg 1 fires as a genuine pre-fill.** The kickoff framed this as "pre-fill at creation vs. align-on-add," and that framing collapsed once the real workflow was traced.

#### Why the original two options were not symmetric

**Every authoring flow in this product is author-first, add-second. Verified at scoping:**

- `/notes/new` accepts only `mode` and `source`, and no entry point passes a collection.
- **Bulk-generate has no collection field and `router.push("/library")` on submit** (`bulk-generation-page-client.tsx:307`) — the notes land in the Library unattached, and the author then adds them to the Review Set by hand.
- The collection detail page's *"Add notes"* picker (`collection-detail-page-client.tsx:2136`) selects from **existing** notes via `addCollectionItems`.
- The Study Plan builder assembles existing notes; it authors nothing.

So "pre-fill at creation" was never a wiring choice — **there is no context to read.** It is a missing input, and the fix is to ask for it in the one surface where bulk authoring actually happens.

#### Why align-on-add is rejected, and the reason is not aesthetic

Aligning depth when a note *joins* a Review Set mutates notes that may already be generated — which **makes authoring corrections routine, the exact condition the Backlog Index's *Challenge bank orphans* row names as making that bug unbounded.** That row is currently sized at 5 of 6,235 rows precisely because corrections are rare today. Confirmed in code at scoping: `uq_challenge_quiz_question_bank_user_pack_key` is `(user_id, study_pack_id, question_key)` and **excludes `learner_level`** (`V96__challenge_quiz_question_bank.sql:12`), while the claim index includes it (`:17`) — so a stranded row is unclaimable but still occupies the unique key, and the LLM can regenerate straight into a constraint violation. **This release must not be the thing that makes that unbounded.**

Option C escapes it cleanly: bulk-generate queues async generation with `learnerLevel` in the request, so **no bank rows exist yet and the orphan risk is structurally zero, not merely small.**

#### Binding consequence for item 1 — create surfaces ONLY

**Item 1's profile pre-fill lands on `/notes/new` and bulk-generate only. It must NOT be added to the inline metadata editor** on `private-note-detail-page-client.tsx`, which edits already-generated notes. Doing so would re-introduce exactly the row-187 exposure align-on-add was rejected for, through a different door. Stated here because the two surfaces are adjacent and a reasonable implementer would otherwise treat them as one.

#### Scoped to bulk-generate, not `/notes/new`

The CE Review Set pain is **bulk** authoring. A single-note author can use `Add to Review Set` immediately after saving, so shipping the selector on both surfaces doubles the trigger surface for the same benefit. **One surface this release**; revisit only if single-note authoring shows the same friction.

#### The inheritance rule

**Depth resolves to the nearest ancestor with a non-null `learner_level`, walking up `parentCollectionId`.**

- **Bound the walk** — `parentCollectionId` is a plain FK with no cycle constraint, so the walk needs a depth cap rather than trusting the data.
- **No ancestor carrying a level means NO pre-fill — never `COLLEGE`.** Falling through to a hardcoded default is precisely the confidently-wrong-stored-value failure `ADR-001` constraint 2 names, and the profile fallback is leg 2's job, not leg 1's.
- **This is not a read-time expansion.** `ADR-001:58` forbids resolving a *family into programs* in any filter, facet, badge, or search predicate. Computing a form default at pre-fill time is not that, and the resulting value is stored explicitly on the note because a human saved the form. Recorded so a reviewer does not flag it as a violation.

#### Membership is written at completion, per successfully-generated note

**Not at queue time.** `processBatch` wraps each topic in its own try/catch (`NoteBulkGenerationService.java:199-215`), so **partial failure is the normal case** — some topics succeed, some fail, some are quota-blocked. Writing membership at queue time would create collection rows pointing at notes that never generate.

Two consequences for whoever implements this:

- `processItem` currently returns `void` (`:265`) and must return the created note's id so the successful ones can be added.
- The collection id must be carried through `BulkGenerateNotesRequest` into the async path, and **the add must be idempotent against the existing `resultId`** — a retry must not double-insert membership rows.

**Authorization:** the selector must offer only Review Sets the author owns, and the membership write must re-check ownership server-side rather than trusting the submitted id.

## v0.74.0 — Quiz Progression (Released, base branch `releases/v0.74.0`)

Kicked off 2026-08-12, cut from `main` after `v0.73.0` merged and deployed. Brief of record: `docs/claude-plans/next-release-candidates-consultation-prompt.md`. **The product-UX second opinion is IN, not pending** — do not re-send the brief for consultation despite its filename.

**Why this release exists.** `practice-quiz-card.tsx:25` renders the Study Pack's saved quiz with `revealAnswer` — questions *and* answers. Quick Review administers **those same questions** (`note.quiz`). A learner can read every answer, then sit the test on the same items. That makes the score meaningless *and* corrupts `ConceptHealth` (`QuickReviewSessionService.java:219-230`), the app's only mastery-integrity signal, locked since v0.37.0 to move only from genuine assessment. **Locking the easier artifact while leaving Challenge Quiz open is coherent, not arbitrary:** Challenge writes its own questions, so the answer key cannot spoil it.

**Two rationales, different standing — keep them distinguishable at implementation time.** *Integrity* (don't hand someone the answer key to a test they haven't sat) is **code-verified** and satisfied by any completed Quick Review, since the first attempt at each question is the genuine assessment. The **perfect-score gate is the *progression* layer on top**, and is the falsifiable half.

**Scope — all seven items ship.** Owner ruling 2026-08-12: nothing parked, no item waits on a date. (1) Lock — not hide — the Quiz tab until mastery, copy naming the real condition and pointing a 4/5 learner at Redo Mistakes. (2) Challenge Quiz open from the start. (3) 🔓 Quiz Unlocked celebration, **announcement only, no competing CTA**. (4) Promote Challenge to primary after mastery, moving `PostSessionNextStepService`'s threshold back from `>= 4/5`. (5) Replace *Finish Review* with *Review the Notes* — **completes the session, then navigates**. (6) Curator exemption. (7) Six analytics events **plus two folded in from the checkpoint below**, with first-pass vs. after-retry perfect carried in the payload.

**Mastery = 5/5, and *Redo Mistakes* counts** — both owner rulings, 2026-08-12. The second half is what keeps a perfect-score gate from being a dead end.

**Quick Review sessions are load-bearing — do not remove them.** Checked at kickoff after a reasonable contrary premise. `completeSession` writes `ConceptHealth` and records `COMPLETED_QUICK_REVIEW` (feeding Recent Sessions and `lastSessionCompletedAt`); the frontend fires `QUICK_REVIEW_COMPLETED` in the same block; progress persists mid-review for resume. **And this release's own gate is a completed-session fact** — 5/5 has no other record, so with no session nothing could ever unlock.

**The 2026-09-30 checkpoint — cost paid, not absorbed.** Item 4 moves the threshold that checkpoint measures (5/5 → 4/5, shipped `becc70ba` 2026-06-16), closing its after-window. **Reads (a) and (b) must be run BEFORE deploy** — the Backlog Index already records both as MEASURABLE NOW, with read (a) explicitly not improving by waiting, and a ~2-month after-window already exists. **Read (c) — the only one that could falsify *"motivation, not placement"* — is now measurable:** item 7 shipped the missing post-session Challenge CTA impression and click events with the originating quiz mode. That also stops item 4 repeating the original failure of shipping a threshold blind.

**`[CHECKPOINT — due 2026-09-12]`** — pre-declared at kickoff, **date set at signoff to deploy+30** (deploy 2026-08-13). **Metric:** among learners starting Quick Review post-deploy, the fraction reaching 5/5 (first pass or via Redo) — the unlock rate. **Secondary:** Quiz-tab-opened-after-unlock, testing whether the reward is wanted, not merely reachable. **Kill criterion:** a low unlock rate means the gate is a wall, not a progression — **relax it to "any completed Quick Review,"** which this release's own reasoning establishes is sufficient for integrity; do not iterate on lock copy instead. **Denominator clause:** too small to read at the due date is itself the finding, not grounds to extend.

### Folded in 2026-08-12 — math notation renders as raw LaTeX (owner-reported)

**The defect.** Quiz choices display literal `\frac{(y₂-y₁)}{(x₂-x₁)}` and `\sqrt{x}`, backslashes and all, instead of a fraction and a radical. Owner-reported from the **Practice Quiz surface — the same Quiz tab this release is locking**, which is what earns it a place here rather than in a later cleanup: gating a tab behind a perfect score while its formulas are unreadable makes the gate harder to pass for a reason that has nothing to do with knowing the material.

**Root cause, verified in code and environment-independent.** The frontend has a full KaTeX pipeline, but it only activates **inside delimiters** — `$…$`, `\(…\)`, `$$…$$`, `\[…\]` (`quiz-working-solution.tsx:17-26`). Everything else renders as plain text. **No prompt in the repo tells the model to emit delimiters** — all 19 files in `prompts/study-pack-v1/` were checked and not one mentions math, LaTeX, KaTeX, or delimiters. So the model freelances *inconsistently within a single quiz*: Unicode subscripts (which display fine by accident), bare carets (`x^2`, literal), and bare LaTeX (`\frac`, literal). One local row proves it sometimes emits `\(\frac{…}\)` correctly on its own — the behaviour is unreliable, not absent.

**Not the cause, ruled out:** `QuizValidationUtils.sanitizeChoiceText` only strips leading "A." labels and normalises whitespace; it never touches LaTeX.

**Production sizing, run 2026-08-12 — this is what re-scoped the work.** Of **5,472 study packs**: **13** contain LaTeX command words, **2** are already `\(…\)`-delimited, **12** contain `$`, **12** contain bare carets. Assuming zero overlap, the worst case is **~23 affected packs — roughly 0.4%**. **The defect is real and worth fixing; the affected population is very small.**

**Also checked, and it is fine:** the `$` currency false-positive risk is already guarded — `isInlineDollarOpen` requires a non-space after `$`, and the close-scan rejects a `$` preceded by whitespace or an operator, so `$100 and $200` falls through as plain text rather than being swallowed into KaTeX.

**Scope — three parts, and they are independent.**
- **A — Prompts (root cause, new content only).** Add one math-formatting rule to the ~9 generating developer prompts: wrap all math in `$…$`, never bare, never Unicode sub/superscripts. Cheap, and the highest-leverage part, since the model already does this correctly some of the time.
- **B — Renderer (fixes everything already stored, at display time, with zero data risk).** A **conservative** normaliser that wraps only an allowlist of constructs (`\frac`, `\sqrt`, `\sum`, `x^2`, `_{…}`) and **never a bare backslash** — Windows paths, literal `\n`, and chemistry notation must pass through untouched. KaTeX already runs `throwOnError: false`, so a bad wrap degrades to a visible error string rather than crashing; that is a safety net, not a licence to be greedy. **Part B alone fixes every case the owner reported.**
- **C — Admin backfill (deterministic, no LLM), fields limited to the quiz JSON: `question`, `choices`, `answer`, `explanation`, `workingSolution`** (owner-confirmed 2026-08-12). Modeled on the existing `POST /admin/study-packs/repair-malformed-quizzes`, but **it must NOT reuse that endpoint's regeneration approach** — that one calls the LLM per pack, which here would cost a call per pack, silently replace questions learners are actively studying, and violate `CLAUDE.md`'s "never auto-regenerate" rule.

**Part C is DROPPED — owner decision 2026-08-12, on the sizing above.** Only **A and B ship**. The owner regenerates the affected notes by hand instead, using `docs/claude-plans/v0.74.0-latex-affected-notes.sql`, which lists them per note with a `verdict` column (`NEEDS_FIX` / `MIXED_CHECK_IT` / `LIKELY_OK`) plus a companion query showing the offending text.

**Why dropping it is the right call, recorded so it is not re-proposed without new evidence.** Part C needed a new endpoint, a Java normaliser, in-progress-session guards, and idempotency, to repair **~23 packs that Part B already fixes on screen**. It also recreated the exact hazard PR 1's audit caught: a normaliser in Java plus a normaliser in TypeScript is **two implementations of one rule**, and PR 1's PL/pgSQL scorer diverging from `QuizItem` is what would have locked every learner out of the Quiz tab. Manual regeneration at this volume costs less than the machinery, and — unlike an admin sweep — it keeps regeneration an explicit per-note act, which is what `CLAUDE.md`'s never-auto-regenerate rule asks for anyway.

**If it is ever revived** (a much larger affected population would be the trigger), these were the non-negotiables: a **shared input→expected fixture asserted by BOTH the Java and TypeScript suites**; normalise `answer` alongside `choices`, because `QuizItem.resolveCorrectIndex` falls back to matching the `answer` string against choice **text** and rewriting one without the other sends `correctIndex` to null — which now also breaks this release's mastery gate; skip packs with an in-progress Quick Review; and be idempotent, since running twice must not produce `$$x$$`.

**Outstanding input, blocking item 1's copy only:** run `docs/claude-plans/v0.74.0-quiz-length-check.sql` **against production**. Quick Review takes `totalQuestions` straight from the stored row with no slicing (`QuickReviewSessionService.java:98`), and the exactly-5 validation only landed 2026-03-18 (`c78ee9f1`) — older packs and their remixes/copies were never subject to it. A single row of 5 makes "Score 5/5" exact; anything else forces length-agnostic wording.

## v0.73.0 — Onboarding Redesign (Released, base branch `releases/v0.73.0`)

Kicked off 2026-08-11, cut from `main` after `v0.72.1` merged and deployed.

### Why this, and why now

**132 learners — 35.2% of all signups — verify their email and never finish onboarding.** Largest single drop in the funnel (375 → 366 verified → 234 onboarded → 195 activated), and invisible until `v0.72.1`'s activation read surfaced it.

**Justified on comprehension, NOT retention — and the distinction is binding.** `v0.72.1` measured activation at **52.2%**, which structurally caps activation-volume work below the retention lever regardless of the retention rate; the volume hypothesis was tested against a pre-committed rule and failed. This release stands on *132 people meet the product and leave before understanding it*. **Recorded plainly: this is the third justification offered for overlapping onboarding work** — retention, then activation volume, now comprehension — and the owner weighed that knowingly rather than letting it pass silently.

### Planned Scope

**Revised 2026-08-11 after an owner design pass**, which changed the governing principle from *restyle the wizard* to **"the learner is telling NoteLib their learning story, not configuring settings — every screen answers one question."** Ten items, full detail in `RELEASES.md`.

**The structural change: one *required* question per screen, 5 screens → 8.** (Exam takers also see an optional exam-date field on Screen 3 — a secondary control, deliberately kept; see below.) Today step 2 asks three questions at once and step 3 asks two. Target flow: profile type → course/program → learner level → first intent → input method → the note itself → generating → done. **Auto-advance applies only to closed-set choices**; a typed combobox and free text keep an explicit Continue, because the system cannot know when a learner has finished typing. **Actual length rises; perceived effort should fall** — the tradeoff was stated and the owner chose it.

**Two changes worth surfacing here rather than burying in `RELEASES.md`:**
- ~~**The exam-date question is removed from onboarding**~~ — **decided against 2026-08-12 and closed.** The duplication is real, but the post-session prompt lives on session-completion screens and therefore reaches a strictly smaller population than onboarding does. Removing it would leave exam-bound learners who never complete a session with no exam date, killing the board-exam countdown and degrading the exam-date segmentation the *Target-habit definition* row depends on. The field is optional, so it was never a second required question.
- **Scope item 1 (delete the three-CTA completion screen) is DROPPED** by owner ruling. Verified while scoping it: that screen is the note-creating flow's ending only, and **nothing in onboarding navigates to Quick Review today**, so the owner's actual requirement was already true and is now written as an anti-drift rule instead of built.

Also: step 1 story cards with tap-to-advance and a selection beat; step 2 as question-plus-placeholders (**not** sentence-form, owner-rejected for mobile wrapping and validation); step 3 outcome copy with real note counts; the fallback as invitation; a typography and rhythm pass, mobile-first at 360px; **C8 and C9 folded in rather than scoped twice**; and step-level instrumentation, which stops being optional once the flow goes from 5 screens to 8.

**Pre-declared at kickoff:** this ships on a falsifiable belief, so it **will owe a `[CHECKPOINT]`** at signoff — onboarding completion rate against the **62.4%** baseline, made readable per step by the instrumentation item. The signoff gate must not close without it.

### Explicitly out of scope

- **Universal onboarding.** Step 4's two generation endpoints both call `requireEmailVerified`, so running onboarding pre-verification would 403 exactly where it promises the first Study Pack. The counter-proposal — move the ask to step 4, from the door to the payoff — is a follow-on. Verification loses 9 learners; onboarding loses 132.
- **Auto-starting Quick Review.** Owner decision, reaffirmed.
- **Adding steps.** Shorter at the same step count.
- **Any `ADR-001` / Applicable Programs change.**

## v0.72.1 — Constraint Check (Released, base branch `releases/v0.72.1`)

Kicked off 2026-08-11, cut from `main` after `v0.72.0` merged and deployed the same day. A patch on `v0.72.0`'s line, not a new minor: it continues the retention question rather than opening a new one.

### Why this, and why now

`v0.72.0` shipped H1+H5 under a pre-committed rule and, in the same document, recorded the question that outlives it: only **185** users have ever generated a first Study Pack and are old enough to measure, and **3** have ever returned in week 2. Moving retention from 2% to 4% against a ~31-user monthly activated cohort is roughly **+0.6 returning users per month**. That was deliberately not used to avoid the commitment then — the rule was written when the answer was unknown, and re-litigating it after the fact is what the rule existed to prevent. It is the right question to ask *now*, on evidence, which is what this release does.

**Corroborated by an independent path:** 38.7% of accounts (141/364) never complete onboarding, tracking the ~40% recorded 2026-07-28 by a different query. A return-cadence habit cannot be built by users who never finish onboarding.

### Planned Scope

1. **Run the activation read** — owner action, production, read-only. Query written and syntax-validated against the real schema: **`docs/claude-plans/v0.72.1-activation-read.sql`** (five queries; the file's own header carries the caveats, and its closing block states the decision rule *before* the result is known). Where signups drop off between account creation and a first Study Pack, and whether the ~31/month activated cohort is capped by onboarding or downstream. **RUN 2026-08-11; results recorded in `RELEASES.md`** per this release's own rule. **All five queries are now discharged, Query 5 included**, so the file is a finished *release artifact* covered by this version's own section and needs no Backlog Index row of its own — the condition recorded here before signoff ("if Query 5 is still unrun at signoff, it needs its own row") was checked at signoff and did not fire.
2. **Onboarding Intent Router residuals**, conditional on the read — C8, C9, M13, M15, M16, already clustered on one Backlog Index row and carried since `v0.71.0`. **NOT TAKEN** — see below.

### The read ran 2026-08-11 and the volume hypothesis FAILED — this release rescoped

Full numbers, corrections and caveats are in `RELEASES.md`. The short version: the largest drop **is** at onboarding (**132 users, 35.2%** of all signups verify their email and never complete it), and that still does not make volume the lever. Because `returning = activated × rate` makes both levers multiplicative, the comparison is **rate-independent** — the volume ceiling beats doubling retention only if activation is below **50%**, and activation is **52.2%**. Even the optimistic "every signup activates" ceiling (1.95 returning users/month) lands below simply doubling the rate (2.03), and the pre-committed condition required the *half-rate sensitivity* to clear that bar; it came in at 1.48.

**Disclosed, not acted on:** the margin is 2.2 points, and monthly activation runs 65% → 39% → 33% across June/July/August. Re-cutting the verdict on recent months only would move it to "did not settle it." That is an owner call — making it silently in either direction is exactly what the pre-committed rule exists to prevent.

**The residuals are not invalidated.** They stay carried on their own row with a live cost; what changed is only that they can no longer be justified *as a retention intervention*.

### The rescope: is 2.4% a retention fact, or an artifact of its window?

`v0.72.1` rescoped to **Query 5** of the same file — vary the retention window holding everything else fixed. **RUN 2026-08-11. Both halves are true: the window is a material artifact, and the constraint is still real.** Of the **11** users who ever returned after day 1, the shipped days 7–14 window sees **3 — 27%**; 4 return in days 2–7 before it opens, 4 only after day 14. So the number this roadmap has deferred to undercounts by roughly **3.7×** — and the loosest reading is still **7.24%**, meaning 141 of 152 activated users never came back at all. **Mis-sized, not imagined.** The small-n objection does not touch this: the exclusion of days 2–7 and day 15+ is definitional arithmetic, so what is uncertain is the *size* of the undercount, never its existence. **The rate-vs-volume verdict above is unaffected** — it was rate-independent.

### The build half: a defect in shipped code

**Not a documentation problem.** The window is baked into a live admin metric: `AnalyticsEventRepository.RETENTION_WINDOW_START_DAYS`/`_END_DAYS` → `AdminFunnelService.getRetentionCohortMetrics` → `frontend/app/admin/funnel/page.tsx:313`. That is where "2.4%" came from. The fix **reports multiple windows rather than swapping one number for another** — a lone replacement rate would be quoted in isolation exactly as the old one was — and must handle the coupling where widening the window also widens the `first_pack_at <= now - END_DAYS` eligibility filter and interacts with `RETENTION_COHORT_WEEK_LIMIT = 8`. **The exam-date segmentation from the Target-habit row is deliberately NOT folded in:** that row itself says the windowed read stays reasonable for open-ended learners, and flags the exam-bound metric's scored group as likely single-digit today. Backend + frontend, so it goes to Codex per task routing.

### Explicitly out of scope

- **A sixth quiz mode.** The five-mode contract is closed.
- **Any change to the Applicable Programs model**, including the parked learner free-text amendment and the Overlapping-representations decision.
- **Restoring the bare curator role check** on the three note-authoring paths — that is the B0 activation-blocking regression, and this release works directly on the onboarding flow.

## v0.72.0 — Return Loop (Released, base branch `releases/v0.72.0`)

Kicked off 2026-08-11, cut from `main` after `v0.71.2` merged and deployed. Targets W1→W2 retention (**2.4%**, unmoved across releases and named in `GPT_CONTEXT.md` as the single biggest constraint).

### Why this, and why now

Three consecutive releases went into the Applicable Programs arc, which was correct — it was a live authoring blocker, now cleared and validated in production. But that arc improved what can be *authored*, not what makes a learner *come back*. The constraint the whole roadmap defers to has not been touched.

**The trigger is not a judgement call.** An earlier release pre-committed to a decision rule and the evidence window closed 2026-07-29; the kickoff gate scan found it unrun 13 days later. That is precisely the drift step 8 exists to catch, and it is why this release opens on a read.

### Planned Scope

1. **Run the `v0.48.0` cohort re-read** — owner action, production, read-only. The query exists: `docs/claude-prompt/next-priority-new-user-focus-out/02-h1-h5-cohort-recheck-and-cpale-depth.sql`, Query 1. **Record the result**, don't just cite the query.
2. **H1 — commitment device**, conditional on the read.
3. **H5 — pre-decided return action**, conditional on the read.

**H1 and H5 ship together or not at all** — the pre-committed rule names them as a pair, and each is weaker alone.

### If the read is clearly negative

The rule says do not ship, and this release rescopes rather than proceeding on sunk reasoning. Recorded fallback: the **CPALE Exam Hub**, smaller and the fourth instance of a thrice-shipped shape, gated on its own depth-count check. `v0.71.0` set the precedent for opening a version whose scope is honestly blocked.

### Explicitly out of scope

- **A sixth quiz mode.** The five-mode contract is closed.
- **Feeding self-review into readiness.** Mastery comes only from graded assessment; a return nudge is not a mastery signal.
- **Any change to the Applicable Programs model**, including the parked learner free-text amendment.

## v0.71.2 — Catalog Management (Released, base branch `releases/v0.71.2`)

Owner-scoped 2026-08-11 from a production blocker, and deliberately kept on the Applicable Programs line rather than opened as a feature version: it completes the authoring loop Release B started. **Unlike `v0.71.1`, this is new capability, not deferred-findings cleanup** — write its anti-drift accordingly and do not copy the patch-release framing.

### The blocker, stated concretely

Civil Engineering notes are being authored in production now, with Algebra as the motivating case — the exact example `ADR-001` was written around. The next step is making that one note serve the other engineering programs, **and there is no way to do it.** The catalog holds **3** engineering programs (Civil, Electrical, Mechanical) against the ~11 Philippine engineering boards `ADR-001` cites as the motivating scale — it states that count but deliberately does not enumerate them, treating 8-vs-11 as an unsettled curriculum question rather than an architecture decision.

### Audit findings, verified in code 2026-08-11 — do not re-derive

1. **Learner free-text authoring works and is unaffected.** The learner control is `CourseProgramCombobox` with `allowCustom=true`; a typed value lands in `notes.course_program`. Nothing here changes it.
2. **The curator control is catalog-only by design**, not by oversight: `applicable-programs-combobox.tsx` sets `allowCustom={false}`. That is the two-mode model working.
3. **No catalog-creation capability exists anywhere.** `CourseProgramCatalogController` exposes only `list()`; `CourseProgramCatalogRepository` has no write method. **A migration is the only way to add a program today.** This is a missing capability, not a missing button.

### Rejected: "let curators type a new program in Bulk Generate, like note creation does"

The cheapest-looking option, and it buys the wrong thing. **A curator's free text is not merely unsupported — it is discarded**: `NoteService.create` does `entity.setCourseProgram(curator ? null : …)` and `NoteBulkGenerationService` sets `courseProgramText = isTeacherOrAdmin ? null : …`. Making it work means storing it, and then the note carries **one** free-text program and **no join rows**.

That is fatal to the actual goal: Algebra needs to serve *many* programs, and a free-text string holds exactly one. The option hands back the one-note-per-program duplication Release B exists to remove, on the very note that motivated the architecture. The variant where typed text auto-creates a catalog row is worse — every typo becomes a permanent public shelf, and it quietly reverses the ratified rule that the catalog is curator-approved.

### Shipped scope

- **Admin catalog management, add-only (backend + frontend).** Shipped one Admin-only `POST`, near-match lookup, and a form on the existing Admin surface, plus explicit confirmed inline creation from the shared Applicable Programs picker. Existing-family assignment makes a new member participate in family expansion; normalized duplicates are a named 409 rather than a database 500.
- ~~**A one-time seed of the PRC engineering boards.**~~ **REMOVED 2026-08-11 by the `v0.71.1` pressure test — it contradicted a ratified ruling and cited the ADR as if it supported it.** `ADR-001:74` reads: *"**Do not pre-seed a program vocabulary.** Seeding every PRC engineering program at once is premature expansion and is explicitly rejected. **Catalog growth is incremental and demand-driven by authoring:** a curator judging that a canonical note is applicable to a program is the trigger to add that program."* The removed bullet also claimed the seed was *"bounded to the boards `ADR-001` names"* — **the ADR names no such list.** It states a count twice and explicitly treats it as unsettled (*"whether `Engineering Sciences` spans 8 or 11 engineering programs is a curriculum fact, not an architecture decision"*), so there was nothing to bound a seed to.
  - **What replaces it is already in the ADR's own sentence:** the curator adds each program at the moment a note needs it. That is what the admin CRUD above delivers, and it is what will actually happen — the Civil Engineering authoring in flight is precisely the trigger the rule describes.

### Explicitly out of scope

- **"Every course/program in the world" as a data patch.** `ADR-001` rejected pre-seeding as premature and that stands. A seeded-but-unused program is invisible to learners by construction (every learner-facing list derives from *notes*, not the catalog), so the cost is not user-facing — it is an unbounded, unowned vocabulary that accumulates near-duplicates. Adding the 11 that are needed beats adding 500 that are not.
- **Letting a learner's free text reach the catalog.** `ADR-001` → *Representation authority* forbids it, and nothing here reopens that.

### Kickoff decisions — settled

- **Add-only shipped.** Rename and delete remain separate decisions with their URL and FK consequences unchanged.
- **The Applicable Programs control includes inline creation for Admins.** It is a separate confirmed action with near-match visibility, never `allowCustom` or implicit creation.

## v0.71.1 — Applicable Programs Follow-ups (Released, base branch `releases/v0.71.1`)

Kicked off 2026-08-10, cut from `main` after `v0.71.0` merged and deployed. A patch release, not Release C: it clears the findings `v0.71.0` signed off with rather than extending `ADR-001`. Full scope and anti-drift in `RELEASES.md`.

### Why a patch release rather than folding these into the next feature version

`v0.71.0` signed off with four findings explicitly marked *needs a decision, not a patch*, and twenty Medium/Low findings carried as `v0.71.1` candidates. Both sets already have Backlog Index rows, so neither is at risk of being lost — the reason to spend a version on them now is different: **items (1), (2) and (4) are shapes where an ordinary user action re-creates a condition a migration just deleted, or where a surface half-knows about the join.** Those get harder to reason about once more surfaces read the join, not easier. Item (3) is a different case entirely and is here only because it is cheap and adjacent — it is not user-reachable. The `v0.67.1` precedent applies directly — that release opened scoped at three items and finished at seven, because a Known-Limitations list is not a scoping pass.

### Planned Scope

**Group 1 — the four deferred architectural findings.** Each carries an open question; the kickoff ruling (2026-08-10) is **scope now, decide per-PR** — the decision is made when the branch is cut and recorded in `ADR-001` or the feature doc in the same PR as the fix, never left implicit in a service.

1. **`NoteApplicableProgramsService.replace` can recreate the `V108` class post-deploy. SHIPPED 2026-08-11 — ruling in `ADR-001` → *Curation authority*.** **An Applicable Program row may only be authored onto a note its author owns**; `ADMIN` grants catalog-curation authority, not authority over other users' notes. **Ownership, not visibility** — the visibility-scoped alternative fails on an ordinary sequence (learner's note is public → admin curates → learner flips it private), because gating the write moment does not constrain the resulting state, and closing that would need the `source` column this ADR rejected. **Production sizing ran before deciding and made it cheap:** 0 rows in the admin-authored shape (no cleanup migration needed) and *no* non-admin-owned note carries a join row at all, so the restriction costs nothing measurable. The Official Library is unaffected because official notes are `ADMIN`-owned. The admin curator page now pages only the requesting admin's own writable notes and no longer renders the redundant owner-email column.
2. **A learner sees inherited programs on their own note that they cannot edit. SHIPPED 2026-08-10 — ruling in `ADR-001`.** *"No learner-facing Applicable Programs UI"* governs authoring **controls**, not provenance **display**: inherited programs render read-only with provenance, and the learner's own field is not required on a shadowed note. The backend exposes one authoritative `shadowed = (joinRowCount >= 1) && (joinRowCount == 1 || domainContext != null)` result (corrected at the pressure test); Note Detail and the Note Editor consume it without recomputing it. Provenance remains on note surfaces rather than cards, the sentence is conditioned on `copiedFromNoteId`, and F4 plus L12 shipped inside this item. **The M2 sequencing claim recorded here was corrected 2026-08-11 — see `ADR-001`.** It said the ordering was load-bearing because M2's fix would surface programs on learner cards automatically. It would not: library cards read `listLibraryPage`, whose native query has carried the join since slice 2, while M2 concerns `listMine`, which no card consumes. Cards already show the neutral summary this ruling requires. The ordering was a correct instinct on a wrong mechanism, and nothing depended on it.
3. **`NoteBulkGenerationService` still uses the bare curator predicate. SHIPPED 2026-08-11.** Now guards on `onboardingCompletedAt` before the role check, matching the two paths corrected in `adfa797f`. Not UI-reachable, so no user-visible change — the value is that `CLAUDE.md` states this as a rule and a rule with a live in-repo exception decays. `CLAUDE.md` corrected: it still named this service as carrying the bare form. **Group 1 is complete.**
4. **AI-suggestion apply dead-ends on a copy of a curated note. SHIPPED inside item 2** — `resolveRequestedCourseProgram` no longer throws when the target note is shadowed, closing the same defect for both `applySuggestions` paths. **The four findings are three branches.**

**Group 2 — COMPLETE 2026-08-11.** Contracts that lie about themselves (M2 — `listMine` moved onto the same native select the Library page uses, dead JPQL projection deleted; M1 — `@JsonAlias` for the wire rename; M10 — the pre-checkout save stops swallowing its exception), consumers no longer blind to the join (M3, M4 — via one shared `NoteEffectivePrograms` helper, not three implementations), copy and control defects (M11, L1, L3 — **L12 moved into group 1 item 2**, whose read-only names block is its fix). **L4 and L5 are measured and dispositioned rather than patched:** L4's EXPLAIN finally ran and shows the slug fallback needs an *expression* index, not the plain one the finding proposed — a larger call, measured locally only, so no migration ships here; L5 diverges only on four catalog names no test uses. Per-item detail in `RELEASES.md`; `file:line` for all twenty in `docs/claude-findings/v0.71.0-pre-signoff-pressure-test.md`.

### Completed in v0.71.1 so far

- **Group 2 sweep (M1, M3, M4, M10, M11, L1, L3)** — contracts, join-blind consumers, copy and control defects; L4's EXPLAIN run and recorded, L5 and L9 dispositioned as not-taken. Shipped `29edf191`.
- **Pre-signoff full pressure test (2026-08-11)** — four independent agents, then `advisor`. Every blocking finding traced to this session's own work, including a wrong shadowing predicate propagated into four documents and a test that asserted it. All fixed or recorded; see `RELEASES.md`.
- **M2 (Group 2)** — `GET /notes` now returns the joined Applicable Programs it always advertised; `listMine` shares the Library page's native select instead of a JPQL projection that could not express the aggregate, and the dead projection is deleted. Scoping it surfaced a record correction: the item 2 sequencing claim rested on a mechanism that does not exist — cards read `listLibraryPage` and have shown joined programs since slice 2.
- **Group 1 item 3** — the curator onboarding guard now covers all three authoring paths; `NoteBulkGenerationService` was the last holdout. The suite's own `mockUser` helper had never set `onboardingCompletedAt`, so every curator test was silently running against a mid-onboarding user — corrected, with two tests added.
- **Group 1 item 1** — Applicable Program writes now require owner-and-curator authority, unauthorized notes remain concealed as not found, and the Admin Dashboard pages only the requesting admin's notes. The admin-or-owner read path remains unchanged; no migration or schema change was needed.
- **Group 1 item 2 (including F4 and L12)** — shipped the backend-owned shadow predicate, optional personal Course / Program on shadowed notes, and read-only program-name provenance on Note Detail and the Note Editor. Curator authoring, M2's list projection, cards, generation inputs, and schema remain unchanged.

### Explicitly out of scope

- **C8, C9, M13, M15, M16** — the same onboarding-vocabulary and guard question in five forms. They belong to the Onboarding Intent Router row, and `v0.71.0` already ruled that constraining onboarding to the catalog is the wrong fix. If this release signs off without them they are **re-recorded, not closed**.
- **M12** (does `effectiveAuthoringDomain` need a final fallback) and **M9** (stale cached `profileType`/`role`) — both may be *answered* here; neither may be patched around. M12 in particular is an ADR-level question, and a patch release is how that kind of decision gets made by accident.
- **Retiring the `notes.course_program` legacy-string fallback** — `v0.71.0` recorded it as a separate, unscheduled decision. It stays unscheduled.
- **L6** (not UI-reachable), **L7** (matches 0 notes in production), and **L8** — the two dead `V106` FK columns. L8 is not a free cleanup: the only obvious fix is a `DROP COLUMN`, which `v0.71.0` declined on irreversibility grounds and which this release's "migrations stay additive" rule forbids. Recorded, not scheduled.
- **Any catalog seed migration, and any change to the four Program Family rulings.**

## v0.71.0 — Applicable Programs (Released, base branch `releases/v0.71.0`)

Kicked off 2026-08-04, cut from `main` after `v0.70.0` merged and deployed. Opens **Release B of `ADR-001`**: `note_course_program` turns applicability into a many-to-many fact, so one canonical Algebra note surfaces under every engineering program that needs it rather than being duplicated per program. Release A closed with `v0.70.0`.

### Planned Scope — three slices, split along the irreversibility boundary

Sequenced 2026-08-04 in `18-release-b-slice-sequence.md`. **Only slice 3 is gated** — the initial "blocked on applicability" framing read ADR-001's gate more broadly than the ADR does. It requires curator verification *"before **family-expansion defaults** are set"*, not before the join table, the backfill, or per-note curator additions.

**Grew to five slices.** Slice 4 (Single Program Axis) was added 2026-08-05 and has shipped. **Slice 5 (Onboarding Intent Router) was added 2026-08-06** after the pre-signoff pressure test, by owner ruling — see the Backlog Index row and `docs/claude-plans/onboarding-activation-and-intent-router.md`. Slice 5 is a *consequence* of this release rather than new ambition: ADR-001 Release B invalidated onboarding's core assumptions, and the pressure test found **B0**, an activation-blocking regression this release introduced. Its B0 repair lands first and independently so signoff is never blocked behind the redesign; the Intent Router itself is gated on an owner-run production audit of Official Review Set program vocabulary.

**The pre-signoff pressure test ran 2026-08-06 and its findings are OPEN** — five blockers, 9 High, 16 Medium, 12 Low, in `docs/claude-findings/v0.71.0-pre-signoff-pressure-test.md`. Per `CLAUDE.md` each must be fixed or recorded in `RELEASES.md` as a Known Limitation before this version can close. Two of the blockers (B1 `copyNote` FK violation, B2 frozen learner programs) have a **fix-order dependency on each other**, and B2 additionally has an open owner decision attached; do not fix either in isolation.

1. **`note_course_program` + 1:1 backfill + admin write surface — shipped.** Additive, reversible, gated on nothing, and it delivers the ADR's actual purpose: one canonical note applicable to many programs.
2. **Read paths move to join/`EXISTS` — shipped.** Filters, facets, badges, and Public Library search are join-first with a legacy-string fallback for notes with no join rows. This corrected shape preserves excluded-value results and shareable slugs while allowing multi-program discovery; retiring the fallback is unscheduled.
3. **Program Family expansion — shipped.** The shared authoring control derives families from the existing catalog and unconditionally unions every member into the explicit selection for trimming before save. No backend, migration, endpoint, catalog seed, curriculum conditioning, or read-time family expansion was added. Four owner rulings below.

**The gate on slice 3 is CLEARED — owner rulings, 2026-08-05.** It previously read: *applicability groupings unverified against current PRC board syllabi — `[EFFORT]`, not `[EVIDENCE]`.* It was cleared by **narrowing the question rather than answering it as posed**. The recorded gate asked whether `Engineering Sciences` spans 8 or 11 engineering programs, but the catalog holds **3** engineering programs and one family; the "11" was an early doc reasoning about Philippine engineering education generally. More decisively, the rulings make expansion **unconditional**, so no subject→program mapping is needed and the syllabus question stops gating anything.

Four rulings, none of which Slice 3 may re-litigate:

1. **The catalog represents *valid applicability*, not curriculum coverage.** It answers "who can legitimately study this note?" — Review Sets communicate completeness. **The catalog still follows curriculum; what changed is what "follows" means.** A program does not need a complete Official Review Set to earn an entry — it earns one once **legitimate canonical notes are applicable to it**. **Do not pre-seed every PRC engineering program**; that is premature expansion and is rejected. Catalog growth is **incremental and demand-driven by authoring**, which refines rather than reverses the `v0.70.0` *follow-not-lead* posture and leaves the `Computer Science` / `Software Engineering` rulings standing. **Practical effect on slice 3: no catalog seed migration ships with it** — the current 21 programs stay until authoring demands more.
2. **Program Families stay intentionally dumb** — an authoring shortcut, never a curriculum engine. No hidden inference, no read-time applicability, no curriculum intelligence.
3. **Expansion fills in all family members.** No curated subsets. An author trims what does not apply.
4. **Expansion is never subject-conditioned** — explicitly rejected. That would quietly make Program Families a second curriculum taxonomy and permanently couple Subject knowledge to applicability rules, re-coupling the axes ADR-001 separated.

**Governing principle, now binding in ADR-001:** Program Families are a **productivity feature, not a curriculum feature.** They are deliberately allowed to over-select, because the Note's explicit Applicable Programs are always the source of truth. Maintaining curriculum rules inside Program Families is the tripwire that says the feature has exceeded its responsibility.

#### Design direction — Programs and Review Sets answer different questions (ratified 2026-08-05, NOT in slice 3)

| Surface | Answers | Role |
|---|---|---|
| **Program** (Applicable Programs) | *"What notes are applicable to me?"* | discovery |
| **Review Set** | *"What is my complete learning journey?"* | curriculum completeness |

Keeping these distinct is what lets the catalog grow on applicability without implying coverage. **Coverage is emergent, not declared:** every learner-facing program list (facets, filter dropdowns, search) derives from *notes* rather than the catalog, so a program with no applicable notes is invisible to learners and the catalog is effectively author-facing. The residual risk is a **thin** shelf, not an empty one — a program carrying a handful of shared foundational notes reads as a curriculum without being one.

**The direction:** communicate coverage **at the Program level**, when a learner browses a Program with no dedicated Official Review Set yet. Conceptually — *"This Program currently contains shared foundational notes. A dedicated Official Review Set is still being developed."*

**Explicitly rejected:** per-note coverage indicators, and any new coverage metadata system. The completeness signal already exists — it is the Review Set — so this is a messaging affordance, not a new axis.

**Production sizing, 2026-08-06 (`25-query-a-production-results.md`) — the trigger already exists.** Six catalog programs hold **≤2 notes** (Business Administration 1, Aviation 1, Psychology 1, Medicine 2, Criminology 2, Law 2) and two more hold zero, while the top four (Education 1845, Architecture 990, Nursing 929, Accountancy 606) are **92%** of all catalogued notes. So thin shelves are a present condition, not a future risk. Sharper still: the `Engineering` family holds Civil Engineering 214, Electrical Engineering 8, Mechanical Engineering 7 — meaning the **first real curator use of the slice 3 family shortcut is also the moment two thin shelves become visible to learners**. That is correct ADR behaviour (author once, serve many), and it argues for shipping this message before heavy family use rather than after.

**Status: design direction for the learner experience, deliberately not scoped into slice 3.** It needs its own scoping pass; it becomes live the moment the catalog grows past the programs that have real Review Sets behind them, which under ruling 1 is authoring-driven rather than scheduled.

**R4 still did not settle applicability** — it validated the Domain Context *value set*. That caveat stands; it is simply no longer load-bearing, because unconditional expansion needs no per-subject applicability answer.

### What ADR-001 warns about, and this release must respect

- **Not reversible** once filters and badges read the join — rollback needs a migration. A knowing failure of the bootstrap test's clause 2, accepted as a multi-release commitment.
- Filter and search move to join/`EXISTS` semantics on a **hot paginated path** that already needed a dedicated performance release (`v0.51.0`).
- **Facet counts will sum above the note total** — correct behavior, needs a UI affordance, not a fix.
- **Program Families expand at save time**, never inferred at read time.
- Review Sets keep composing notes freely; a Review Set's course/program stays a curation label.

### Carried forward

Both `v0.70.0` Known Limitations, plus the regeneration-variance finding from R4 — each now has its own Backlog Index row rather than living only in a released version's prose.

## v0.70.0 — Canonical Knowledge Completion (Released, base branch `releases/v0.70.0`)

**Kicked off 2026-08-04.** Completes ADR-001 Release A. See `RELEASES.md` v0.70.0 for full Planned Scope and anti-drift rules, and `15-vocabulary-and-impact-results.md` for the production reads that unblocked the two deferred items.

### Planned Scope

_All planned scope has shipped. Remaining before signoff: R4, the two `#986` Known Limitations dispositioned, and the full pre-signoff pressure test._

### Completed in v0.70.0 so far

1. **Authoring metadata editable on `STUDY_PACK_READY` notes, including the detail-page inline panel** — Domain Context and Note Learner Level are correctable after generation while content stays locked.
2. **Pool/bank learner-level re-keying** — persisted quiz reuse now keys on the note's effective curriculum level, with no pre-stamping migration.
3. **`course_programs` catalog + `program_families`** — V106 seeds the audited 21-program catalog and one Engineering family, adds nullable note/user FKs without rewriting legacy strings, and makes catalog names authoritative for Exam Hub lookups with fail-open literals.
4. **`10-…sql` cleanup plus two `AGENTS.md` blocks that were actively misdirecting Codex prompts** — `31f602f4`.

### Carried over unchanged

**R4 — ~~`[CHECKPOINT — due 2026-08-18]`~~ RESOLVED 2026-08-04, before the due date** (`ADR-001` → *R4 verification — RESOLVED 2026-08-04. The 8-value set is not amended*). It passed on all three steps against production once `v0.70.0` deployed, and **the bulk-authoring block that stood across three releases was lifted at the `v0.71.0` kickoff.** The sentence here previously read *"bulk authoring does not begin until step 2 passes"* and was never updated — corrected 2026-08-11, when real bulk authoring in production made the stale gate visible. The live-looking checkpoint marker is struck through deliberately: kickoff step 9 scans for `[CHECKPOINT — due …]` rows, and a resolved obligation wearing a live marker is a false positive waiting for someone to act on it. PR 6b and the authoring-by-inference direction are no longer gated behind R4.

### Resolved catalog decisions

The owner ruled 2026-08-04 to seed `Information Technology` and leave `Computer Science` / `Software Engineering` outside the catalog with null FKs. On 2026-08-05 the owner also ruled to seed all three Senior High strands and remove the zero-match `Medical – Surgical Nursing` subject-area alias from PNLE. `Bsed` -> `Education` is the only literal non-exact FK mapping; every legacy string remains unchanged.

## v0.69.0 — Canonical Knowledge Foundation (Released, base branch `releases/v0.69.0`)

**Kicked off 2026-08-03.** Release A of ADR-001 — see the "Canonical Knowledge Architecture" section directly below for the full sequencing, success metric, and deferral list, and `RELEASES.md` v0.69.0 for Planned Scope and anti-drift rules.

### Planned Scope

1. **`notes.domain_context`** — curated closed 8-value set; replaces `course_program` as the LLM's authoritative domain constraint. Resolver, `StudyPackGenerationContext`, `buildGenerationContextBlock`, `buildSubjectSuggestionGuidanceBlock`, `isQuantitativeContext`, the `{COURSE_PROGRAM}` → `{DOMAIN_CONTEXT}` placeholder rename, admin authoring, Bulk Generate.
2. **`course_programs` catalog + `program_families`** — nullable FK alongside the existing strings, nothing reads the FK yet; retires `ExamGoalConfig`/`exam-hub-config.ts`'s hand-synced name lists.
3. **`notes.learner_level`** — plus the mechanical backfill of the 49 K-12-level-in-program notes, which today feed a grade level into the authoritative-academic-domain prompt line.
4. **Question pool / bank re-keying** — own PR: `exam_question_pool.learner_level` and `challenge_quiz_question_bank.learner_level` move off *user* level, with an explicit existing-rows policy.
5. **Subject-equals-context admin nudge**, and the **`AGENTS.md`/`CLAUDE.md` doc corrections** folded in (see below).

### Documentation corrections folded into kickoff

Both were found by asking whether a fresh session could safely continue this work, and both would have actively misled one:

- **`AGENTS.md` Course/Program UI rules contradicted the ratified ADR.** The rules stated `courseProgram` is "the top-level note-classification shelf above `subject` and `tags`" and that `notes.courseProgram`/`users.courseProgram` "remain persisted string fields; do not add a `course_programs` table unless explicitly requested." `CLAUDE.md` designates `AGENTS.md` as the anti-drift source to "always check first," so a session or Codex prompt reading it would have found an explicit prohibition on exactly what this release ships — with the live risk that a later session "fixes" the schema back to match the rule.
- **`CLAUDE.md`'s source-of-truth list omitted `docs/architecture/` entirely** — `ARCHITECTURE.md`, `DATA_MODEL.md`, and now `ADR-001` were all absent from the list a session is told to read "before implementing anything." The Backlog Index row was the only thing pointing at the binding architecture record, which is a backstop, not a design.

### Locked legacy-data decisions (ratified 2026-08-03 — do not reopen in a later PR)

Both are recorded in `docs/architecture/ADR-001-canonical-knowledge-architecture.md` under "Legacy-data policy," and the PR-level consequences are in `09-release-a-pr-sequence.md` (PR 4 and PR 6). Summarised here so PR scoping does not have to find them:

1. **Ambiguous legacy values are resolved per-record by content, never by blanket mapping.** The case is `course_program = 'High School'` (11 notes, all official public, none in a collection). `LearnerLevel` already separates `JUNIOR_HIGH` from `SENIOR_HIGH`, so the legacy label is less precise than the taxonomy replacing it. Each note is classified from its **actual curriculum and content**, not from the old label; notes that cannot be classified confidently stay **unclassified** (NULL `learner_level`, **NULL `domain_context`**, `course_program` retained so they are never left with no classification) and flagged for admin review; and **no `HIGH_SCHOOL` enum value may be added** to preserve the ambiguity — that would migrate the imprecision permanently into the new taxonomy. This makes PR 4 a human-review pass plus an explicit note-ID → level mapping, not a single SQL `UPDATE` — the review query is `10-high-school-classification.sql`, and it runs against **production** (the local dev DB is a different, much smaller dataset). **PR 4 is split into 4a (the 27 pure-level notes, unblocked) and 4b (the remaining 22), and neither clears `course_program`** — see ADR-001's second corollary: clearing is cosmetic once a Domain Context is set, irreversible, and would activate a live editor defect that silently submits the editing admin's own profile program onto a null-program note. The what-counts-as-a-program call moves to PR 5. `visibility` is **not** flipped: withdrawing live official public content is not authorized. **The NULL `domain_context` above is a corollary added 2026-08-03 while scoping PR 4**, not a new decision: `StudyPackGenerationContextResolver:122-140` resolves the domain as `domainContext` → `courseProgram` but the level as `noteLearnerLevel` → user level → `COLLEGE`, never reading `courseProgram`. Backfilling `GENERAL_EDUCATION` onto an unclassified note would therefore evict `'High School'` from the prompt while supplying no level — leaving static content with no curriculum-level line at all (it reads the note level directly, with no reader fallback) and quizzes resolving to the reader's level, `COLLEGE` by default. Retaining `course_program` only preserves a classification if nothing overrides it.

2. **Existing generated assets are preserved, but their semantic reach does not widen automatically.** Principle: *preserve existing assets, but do not expand their semantic reach until their compatibility has been deliberately reclassified.* Existing `exam_question_pool` / `challenge_quiz_question_bank` rows stay reusable for their **original source Note**; Domain Context is backfilled from the source Note only where deterministic; legacy `course_program` is **not** evidence of cross-program reusability; rows stay **source-note-scoped** until a PR explicitly re-keys and audits compatibility; rows with no confidently resolved Domain Context remain usable only via their existing narrow path or are excluded from shared retrieval; and there is **no deletion, no destructive regeneration, and no bulk retirement**. This deliberately rejects both options originally offered (null the rows, or force one invalidation pass). **It does not restrict `v0.60.0`'s Official template sharing**, which is cross-user but already same-source-note via `copiedFromNoteId`.

### Descoped 2026-08-04 — PR 5 and PR 6 move to v0.70.0

Items 2 and 4 of the Planned Scope above did not ship. Both are blocked on production reads this branch cannot perform, not on engineering time:

- **PR 5 (`course_programs` catalog + `program_families`)** needs the exact 32 program strings. `05-vocabulary-results.md` never enumerates them — the 5 user-side-only values are counted but unnamed, and prose loses exact bytes such as the U+2013 in the Senior High labels. Query written: `11-program-vocabulary-seed.sql`. Fully independent of everything else; it can land first in v0.70.0.
- **PR 6 (pool/bank re-keying)** needs `12-pool-bank-relevel-impact.sql` to size the refresh wave, and needs R4, because its Domain Context backfill onto pool rows is invalidated by a changed value set. Scoping also found a read/write divergence `09` does not mention (`PostSessionNextStepService:80` passes the reader's level while the five `ChallengeQuizService` write sites would move to the note's), which must be fixed in the same PR or Redo Missed Questions disagrees with itself.

Recorded as an explicit deferral rather than dropped: both remain in `RELEASES.md` Planned Scope tagged `[DEFERRED to v0.70.0]`.

### Verification

**⚠️ ~~Restructured 2026-08-04 into a post-deploy `[CHECKPOINT — due 2026-08-18]`~~ — RESOLVED 2026-08-04; see the corrected note under *Carried over unchanged* above and `ADR-001:266`. Retained below as the record of why it could not run before signoff.** The R4 generate-and-diff check could not run before signoff: it needs the columns and authoring fields live in production, production runs `main`, and `main` is at V101 with all 28 release commits on the release branch — so signoff is what deploys it. **Bulk authoring still must not begin until R4 step 2 passes.** Original note follows.

**⚠️ Owed, not done as of 2026-08-03: the R4 generate-and-diff check for PRs 2–3.** Automated checks all pass, but the one risk tests cannot cover — a broader Domain Context (`Engineering Mathematics`) producing vaguer content than the `course_program` it replaced (`Civil Engineering`) — has not been verified against real generated output. It is an owner action through the UI, now possible since PR 3 shipped the authoring fields. **Do it before bulk authoring and before PRs 4–7:** if content drifts generic, the fix amends ADR-001's ratified 8-value set, which is cheap now and expensive once later PRs and a body of authored notes depend on those values. Full steps in `RELEASES.md` v0.69.0 under "Verification owed."

Migration numbers start at **V102** (V102 and V103 are now taken; PR 4 continues from **V104**) — the numeric max is `V101__concept_health_incorrect_streak.sql`, *not* V99; a lexical `ls` sorts `V9__`/`V90__`–`V99__` after `V100__`, so always derive it numerically. `v0.47.1` was a migration-collision hotfix; check concurrent branches before claiming a number.

Baselines for the success metric were taken **before** this release (they are unrecoverable afterward) and live in `docs/claude-prompt/canonical-knowledge-architecture-out/05-vocabulary-results.md`.

## Canonical Knowledge Architecture (ratified 2026-08-03 — Release A kicked off as `v0.69.0`, Release B follows)

**Why now, in the owner's own terms.** Under the Civil Engineering Review Set's **Engineering Mathematics** subject plan, the next authoring step was creating topic notes for the **Algebra** subject. That work was stopped deliberately: those same Algebra notes will be needed by Mechanical, Electrical, Electronics, Computer, Industrial, Chemical, Mining, Agricultural, Geodetic, and Sanitary Engineering, and authoring them under a single-program model commits to duplicating them ten more times. The initiative exists to **avoid creating the duplication in the first place**, decided at the moment of authoring — not to clean it up afterward. Every finding below is supporting evidence for that decision, not the decision's origin.


Full deliverable: `docs/claude-prompt/canonical-knowledge-architecture-out/01-architecture-critique-and-migration-plan.md` (critique, gaps, risks, alternatives, recommended architecture, 14-item migration inventory) and `docs/architecture/ADR-001-canonical-knowledge-architecture.md` (draft ADR, moves to `docs/architecture/ADR-001-canonical-knowledge-architecture.md` on ratification only). Backlog Index row above carries the audit findings. This section is the sequencing reference.

**The problem.** `notes.course_program` is one free-text `VARCHAR(120)` carrying five responsibilities with incompatible cardinality: the LLM's authoritative domain constraint (needs exactly one value), plus the private Library facet, the Public Library filter and search predicate, the Exam Hub mapping key, and the note card badge (all want many). Invisible across four Official programs with little shared content; a hard blocker at Civil Engineering, where one Algebra subject applies to eleven engineering programs.

**The decision.** Notes model canonical knowledge; programs describe applicability. Four axes, one owner each — Subject (*what*), Domain Context (*how it's authored* — the domain constraint), Note Learner Level (*how deep*), Applicable Programs (*where it appears*, discovery only, never reaches a prompt) — plus a ruling on the existing `notes.target_profile_type` (*who it's for*, never depth).

**Sequencing — REVISED 2026-08-03 after the production vocabulary audit. Two releases, not four steps in four slots.** (`01` §5.4 still carries the original four-step framing and its per-step detail; this is the operative sequence.)

**Release A — Steps 1 + 2 + 4 together.** All additive nullable columns/tables, all reversible, and Steps 1 and 4 edit the *same method* (`OpenAiLlmStudyPackService.buildGenerationContextBlock`, `:1529-1549`), so splitting them means knowingly shipping one fix while leaving its twin bug in place.

1. **`notes.domain_context`** — resolver + three prompt-builder changes (`buildGenerationContextBlock`, `buildSubjectSuggestionGuidanceBlock`, `isQuantitativeContext`), admin authoring + Bulk Generate expose it, normal users unaffected via the fallback chain. No read-path, filter, badge, or URL change. **This alone is the curriculum-authoring unblock**: Review Sets already compose notes by explicit reference, so a curator can author one canonical "Engineering Foundation / Algebra" note and add it to eleven engineering Review Sets before any join table exists.
2. **`course_programs` catalog + `program_families`** — folded in; the audit found only 32 union values with zero character-level collisions, so this is a curated seed plus ~6 semantic judgment calls, not its own release. Nullable FK alongside the existing strings, nothing reads it yet. Retires `ExamGoalConfig`/`exam-hub-config.ts`'s hand-synced program-name lists and their documented en-dash fragility.
4. **`notes.learner_level`** — folded in, and it fixes a live bug rather than adding a nicety: **49 notes currently hold a K-12 grade level in `course_program`, which feeds a grade level into the prompt line that says "treat the course/program above as the authoritative academic domain."** Backfill is mechanical for exactly six values, and all 49 are in zero collections. The pool/bank re-keying (`exam_question_pool.learner_level`, `challenge_quiz_question_bank.learner_level` moving off user level, plus the existing-rows policy) is the one genuinely separable piece — its own PR inside this release, not its own release.

**Release B (multi-release) — Step 3, `note_course_program` + read paths.** The expensive, **irreversible** step, now justified on evidence rather than held as a bet. Four filter/facet/search sites, badge change, Exam Hub tie-break, admin multi-select + family expansion, copy rule, analytics. Decide the perf approach (denormalized `program_slugs text[]` + GIN vs. covering index on the join) *before* writing queries — this is the path `v0.51.0` already needed a dedicated performance release for. Keep `notes.course_program` written in parallel for one full release as the rollback path.

**Success metric — ratified wording, 2026-08-03:**

> **"Eliminates duplication of shared knowledge as NoteLib expands to more programs."**

Comprehensive Review Sets, faster curriculum expansion, and lower maintenance cost are **downstream benefits, not the architectural guarantee.** The earlier phrasing ("significantly reduces the effort required to build comprehensive Official Review Sets") was retired at ratification because it over-promised: this architecture removes the *multiplier* on every program after the first, but it does not reduce the ~60% of any program's content that is program-specific, and it does not help assembly — Review Sets compose by explicit reference, deliberately. Reaching several hundred notes per Review Set is an authoring-volume and assembly problem this architecture does not address, and the old wording would have read as a failure when Civil Engineering still took real work to finish.

**Baselines taken 2026-08-03, before Release A.** (A) **Duplicate-content ratio: 0.00% by exact title+subject across 886 official public notes — and this metric is now known to be too weak to trust.** Query J found a semantic duplicate it cannot see (`Stress and Strain in Strength of Materials` / `Stress, Strain, and Material Strength`, Civil vs. Mechanical Engineering). Never cite the 0.00% as evidence of no duplication. (B) **4 comprehensive Official Review Sets averaging ~58 notes** — CPALE 74, PNLE 63, ALE 52, LET 43, by hierarchy rollup — against a target of several hundred. The "avg 8.6" figure from the first pass was per-subject-child and must not be quoted. (C) Curator-hours per published Review Set: still manually logged, still owed. Leading indicator after Release A: notes whose Domain Context is shared by Review Sets of ≥2 distinct programs. **The originally-proposed **RETIRED CHECKPOINT** (was due 2027-02-01; token deliberately de-fanged so the kickoff step-9 scan does not flag it) is retired** — it asked whether cross-program reuse would ever materialize, and Query J answered yes before it was needed.

**Deferred to make room, and what was deliberately *not* deferred.** Deferrable (building, no dated obligation): Retention H1+H5, the remaining Messaging Architecture surfaces, Company Redefinition Phase 4 items 5 and 7. **Not deferrable** — dated *measurement* obligations, cheap to run, and exactly what kickoff checklist steps 8–9 exist to catch: the Diagnostic Read Round 2 (due ~2026-08-06, re-cut by the 2026-07-28 target-habit segmentation — deferring loses the cohort window and it cannot be run late with the same meaning) and the Knowledge Impact `[CHECKPOINT — due 2026-09-11]`. **Reprioritizing defers building, never measuring.**

**Nothing here is authorized for implementation until ratified**, and each step needs its own `/kickoff` regardless.

## Company Redefinition Roadmap — Phase Detail

Full detail from Fable's capstone synthesis, `docs/claude-prompt/company-redefinition-out/06-unified-roadmap.md` (read in full, not just its "Decisions carried forward" block). This section exists so reprioritization discussions (e.g. with product/UX) have one canonical reference instead of six separate planning docs to reconcile against — **treat this section, not conversational recall, as the source of truth for what Fable actually designed.** Update it in the same commit as any reprioritization decision so it never drifts from what's actually agreed.

**Resequenced 2026-07-24 — see `company-redefinition-out/07-reprioritization.md` for the full reasoning.** A real-time signup surge (~15 signups in one evening; hundreds of verified users now; LET the strongest acquisition channel) landed alongside a product/UX realization that Challenge Quiz's always-fresh AI questions might be better treated as a reusable, improving asset than disposable output. That combination is significant enough to reorder what comes right after Phase 1, **reversing the 2026-07-23 decision to proceed straight to Phase 2 (v0.58.0)** made earlier that same day. The reasoning (verified against the actual quiz-generation code, cross-checked by an independent advisor pass and an independent Fable session): the realization is correct but was framed as a cost problem when its real value is a **retention primitive** — and it is not the same thing as Phase 3 below (see that section's callout). The new sequence:

1. **Phase 1** — shipped, as before.
2. **Diagnostic Read** (new, inserted here) — read the surge cohort before committing another build cycle.
3. **Reusable Practice Assets & the Return Loop** (new initiative, inserted here, ahead of Phase 2) — the reframed realization, done as a retention play, not a cost play.
4. **Phase 2** — re-gated: Progress now explicitly depends on Reusable Practice Assets existing; Explore is now contingent on the diagnostic read showing a discovery problem specifically (see Phase 2 below).
5. **Phase 3, Phase 4** — unchanged, still parked at their original gates. Phase 3 in particular should **not** be accelerated just because the realization put "question pooling" top of mind — see its callout below for why it's a different thing.

**Phase order as originally designed (cost/risk-based, not arbitrary; superseded above only where noted):** Phase 1 was cheapest/most reversible and instruments its own validation read. Phase 2 was meant to be the v2 layer, building the convergence surface Phase 3 needs something to pool from. Phase 3 is highest one-time engineering cost, sequenced late so adoption volume justifies it before building. Phase 4 is a business decision with **no engineering dependency on 1–3 at all** — it is explicitly free to move earlier the moment the owner ratifies its terms; it's sequenced last purely by convention, not by cost or risk.

### Phase 1 — Practice-first activation onboarding branch
**Status: Shipped, v0.57.0 (2026-07-23).** Source: `company-redefinition-out/02-activation-onboarding.md`.
- What shipped: `BOARD_EXAM` learners with a depth-qualifying Official Review Set skip note-authoring/generation, adopt the set in one tap, land on its detail page. No qualifying set → unchanged 5-step flow.
- Gate to enter: none (first phase, produces the evidence later phases were meant to consume).
- Validation: pre/post W1→W2 retention on the *same covered course/program tracks* (not naive cross-track A/B, since create-first vs. practice-first cohorts are otherwise confounded with covered-vs-uncovered tracks). Floor ~30 completed onboardings/arm for a directional read, ~75+/arm for decision-grade. **Not pulled yet** — needs a 14-day window after the last onboarding in the intake window; see `docs/releases/v0.57.0.md` Known Limitations. The 2026-07-24 signup surge is a candidate cohort for this read — see Diagnostic Read below.
- **SUPERSEDED IN PART, 2026-08-06 — Phase 1 is being generalized by `v0.71.0` slice 5 (Onboarding Intent Router).** Recorded here rather than only in the Backlog Index, per this section's own rule that it must be updated in the same commit as any reprioritization. What changes: the `BOARD_EXAM`-only gate (`onboarding/page.tsx:723-726`) opens to every profile type, so a `STUDENT` with a qualifying Study Plan can also adopt instead of authoring — roughly doubling Branch A's addressable population given `BOARD_EXAM` is 70.94% and `STUDENT` 27.09% of profile-typed accounts, though only where content actually exists. The silent create-first fallback is replaced by an explicit first-intent step whose copy resolves against the learner's program before selection, plus an honest "Coming soon for {Program}" state. What does **not** change: the depth-qualifying predicate (`itemCount > 0 && readyCount > 0`), one-tap adoption, and landing on the set's detail page rather than cold-dropping a new learner into a quiz — a live-testing finding recorded in a 5-line comment at `onboarding/page.tsx:816-821` and explicitly preserved. **Consequence for this section's own validation read — RESOLVED 2026-08-07.** Round 2 was pulled against the pre-slice-5 cohort before deploy, exactly as this note required. The create-first vs practice-first comparison came back **inconclusive** (0 of 33 vs 1 of 18), so the window closed without a verdict rather than being lost unmeasured — the question was asked and answered "insufficient data". **Slice 5 is therefore no longer gated on it.** Do not re-run that comparison: the group ceases to exist at deploy, and `18-diagnostic-read-round2-results.md` is its final record. Open question §12.5 in the onboarding plan is closed by this.

### Diagnostic Read — read the surge cohort before building again (new, 2026-07-24)
**Status: ROUND 2 RUN 2026-08-07 — the read is no longer owed, and it found something larger than it went looking for.** Full results: `company-redefinition-out/18-diagnostic-read-round2-results.md`; queries: `17-diagnostic-read-round2.sql`. **W1→W2 retention is 0.91% (1 of 110), and 0 of 74 open-ended learners returned at all** — a real denominator, not a small-n artefact, and below the 2.4%/127 this whole redefinition was launched to address. Instrumentation was verified healthy *before* concluding (16,903 analytics events across 172 users, most recent same-day), because 1-of-110 is extreme enough to suspect a broken pipe rather than broken retention; it is not broken. The exam-bound segment is **1 of 6 and not measurable** — 51 of 57 exam-bound users are `in_flight` with exam dates still ahead, so that segment carries real signal only later. **The create-first vs practice-first comparison came back inconclusive** (0 of 33 vs 1 of 18 — one user separates the arms) and its window is now permanently closed, which **discharges the gate on `v0.71.0` slice 5**. **What this does not say:** nothing here validates the Intent Router, and reading it as support would be reading a hypothesis into a null result. **What it does say:** the problem is not which door a learner picks at signup — it is that nothing brings them back afterwards. **A return trigger (exam-date-anchored reminders, a queued next session) is now the highest-evidence gap on this roadmap and outranks further entry-point work.** Do not fold it into `v0.71.0`.

**Round 1 history — retained.** Ratified (2026-07-24). Round 1 run 2026-07-24/25 — inconclusive by construction. The surge cohort's own 14-day eligibility window hasn't closed yet, so no retention verdict exists. One real, unrelated finding survived: a chronic ~50% onboarding non-completion rate across recent signups generally (not surge-specific — the surge day completed onboarding better than baseline, not worse). **Corroborated 2026-07-28** via an independent measurement path: `15-profile-type-population-mix.sql` found 40.1% of all accounts and 27.4% of the surge-and-after window still have `profile_type` NULL, consistent with a large share of signups never completing onboarding — see the "Profile-type population mix" Backlog Index row above for the full numbers. **Funnel re-check, same day:** re-running Round 1's own Query 7/Query 8 shows non-surge completion up from 50.4% to 58.46% (Query 7, field-based — stands on its own) and the event-based completion ratio up from 66.7% to 76.5% (Query 8). **Correction, same day, after code investigation:** Query 8's ratio is not a trustworthy second signal — `ONBOARDING_V2_STARTED` is gated behind an async `getMe()` round trip while `ONBOARDING_V2_COMPLETED` isn't, so the ratio can move independent of any real funnel change. Also found the real cause of the `ONBOARDING_V2_ABANDONED` > `ONBOARDING_V2_STARTED` anomaly — not a window-boundary artifact as first suspected, but two compounding frontend bugs (an over-fire on every step transition, plus several early-return redirects that leak `ABANDONED` with no matching `STARTED`) — see the "ONBOARDING_V2_ABANDONED instrumentation bug" Backlog Index row below and `08-diagnostic-read-methodology.md`'s "Results — Onboarding funnel re-check" section for full detail. Full results and what was retracted as over-read: `company-redefinition-out/08-diagnostic-read-methodology.md` "Results — Round 1." Concrete methodology and runnable queries: `08-diagnostic-read-queries.sql`. Source: `company-redefinition-out/07-reprioritization.md`.
- **Scoping found the existing W1→W2 definition needs a fix, not just a re-run:** it anchors "activated" on `STUDY_PACK_GENERATED`, which never fires for a practice-first adopter (copies an already-generated Study Pack, no LLM call) — every practice-first-onboarded learner was invisible to the old read. `08`'s queries report a signup-anchored read (primary, path-agnostic) alongside a widened activation-anchored read (historical comparability) side by side. See `08-diagnostic-read-methodology.md` for the full reasoning.
- Three prior retention fixes (v0.44.0, v0.46.0, v0.48.0) each shipped on a different hypothesis without moving W1→W2 — that pattern is a diagnosis gap, not evidence that the next feature will be the one that works.
- The 2026-07-24 surge (LET/Facebook-driven) is the best real research asset available: read where the funnel actually breaks (do signups complete a first session? return at all? segment by exam-date proximity/prep-cycle rather than a flat weekly boolean), using instrumentation Phase 1 already emits. A handful of direct interviews with reachable new signups is in scope here too.
- Add a crude cost-per-active-user (no token accounting needed — see the Reusable Practice initiative below for why none exists today).
- **Three hypotheses to actually test here, not assume:** (a) discovery problem — the value exists but exam-dated users don't reach it before bouncing; (b) value problem — they reach it and it isn't worth a second visit; (c) lifecycle-metric mismatch — board-exam prep is episodic (cram → sit the exam → legitimately done), so weekly retention may be structurally low regardless of feature quality. Note **(c) is in tension with the existing 0% exam-dated-retention finding** (0/41, retaining *below* their own exam date) — that fact leans toward (a) or (b), so (c) should be tested, not adopted by default.
- **Segmentation refinement (2026-07-28):** the surge is Facebook/LET-driven and adoption is concentrated on 2 specific Official Review Sets people land on directly — that's evidence about *pre-signup acquisition-channel* discovery, a different thing from the *post-signup in-app* discovery Explore Convergence's own gate actually tests (can a signed-up user find/reach value once inside the app, including on a cold return visit with no direct link). Make sure this read's segmentation explicitly compares retention for Facebook-direct-landing users against organic in-app-browsing users — if both retain equally poorly, that's evidence Explore's fix doesn't touch the real mechanism regardless of what the top-line discovery verdict says. Add this split now if the read as scoped doesn't already capture it — cheap, and the window is closing.
- **Read this against the target-habit definition, not the raw W1→W2 boolean alone — defined 2026-07-28, see the "Target-habit definition" Backlog Index row above.** Concretely: re-cut Round 2 by whether `UserEntity.examDate` is set. For exam-dated users, score only those whose exam date has already passed (activity in their final 7 pre-exam days), excluding still-in-flight users from the denominator entirely rather than reading one blended percentage. Expect a small scored group at first — treat a near-single-digit denominator as "not yet measurable," not a verdict.
- **Why this sits ahead of Phase 2:** reorganizing navigation mid-surge would pollute the exact funnel data this read needs to stay clean.
- Gate to enter: none — cheap, reversible, unblocks the sequencing decision below it.

### Reusable Practice Assets & the Return Loop — new initiative (2026-07-24)
**Status: Ratified (2026-07-24), shipped as `v0.58.0 - Reusable Practice Assets & the Return Loop` (Released).** Source: `company-redefinition-out/07-reprioritization.md`. **This is not Phase 3** — see the callout in that section for the precise distinction.
- **What it is:** of the 5 quiz modes, Quick Review already replays a stored quiz (zero LLM per session — already the target state). Board Exam Mode and Long Exam have **per-user** question pooling already built in `ExamQuestionPoolService`, but it ships **dormant** — `examPoolPrewarmEnabled=false` by default, so no pool row is ever created and both regenerate fresh every session today. Challenge Quiz regenerates fresh on start *and on every "give me more" click*, with no reuse of any kind. Adaptive Practice regenerates fresh by design (personalized to one learner's own misses) and should stay that way — it is not in scope here.
- **A previously untracked finding:** Challenge Quiz's "give me more" path calls the LLM every click and is **completely unmetered** — no quota decrement at all, unlike every other generation path in the product. Reframe this as an **engagement signal to harvest** (persist those questions as a durable, revisitable set) rather than a leak to simply cap.
- **What ships:** turn on the existing per-user pool for Board/Long Exam; extend the same per-user pattern to Challenge Quiz (a mode it currently isn't wired to at all); persist generated questions as an owned, revisitable set instead of discarding them each session; add a "redo what you missed" surface reusing the existing weak-concept/`ConceptHealth` machinery (v0.56.0's explanation links, readiness scoring) rather than inventing new mastery signals.
- **Why it's a retention play, not a cost play:** at current scale, aggregate LLM cost is small (quota is essentially never hit) and there is no token/dollar metering anywhere to optimize against — so "AI cost" is the weakest argument for this. The strong argument is pedagogical: a learner who never gets a second crack at the specific question they missed has no spaced-repetition mechanic to retain against, and the product has already locked "curation, never generation" as an identity while 4 of 5 quiz modes currently regenerate-every-session — this closes that gap.
- **Why it's sequenced here:** most of the machinery already exists (stored Quick Review quiz, dormant per-user pool, `ConceptHealth`, weak-concept links) — it is cheap relative to Phase 3. It also **unblocks Phase 2's Progress promotion** below, which has nothing stable to show progress against if quizzes regenerate every time.
- Gate to enter: Phase 1 shipped (done). Does not depend on the Diagnostic Read's outcome — it helps under all three hypotheses above. Ratified and kicked off 2026-07-24 as `v0.58.0`.

### Phase 2 — IA / Explore convergence
**Status: both chunks Released.** `v0.59.0` chunk Released; `v0.67.0 — Explore Convergence` chunk kicked off 2026-07-30 via explicit owner gate override, shipped the same day, and signed off 2026-07-31 — see below. Source: `company-redefinition-out/03-information-architecture.md`.

**Gate override, ratified by the owner 2026-07-30.** The Explore chunk's stated gate — proceed only if the Diagnostic Read indicates a discovery problem — was, and remains, unmet at kickoff time (Round 2 not due until after 2026-08-06). The owner explicitly chose to override rather than wait, the same shape of decision as Knowledge Impact's and Challenge Quiz Quota Increase's ratified overrides above — recorded as an explicit ratification, not a silent gate-clear. A `[CHECKPOINT — due 2026-09-13]` was committed at the same time: a new `AnalyticsEventType` on Explore nav engagement ships inside `v0.67.0` itself, then gets checked against pre-launch baseline ~30-45 days later. Kill criterion: flat-to-negligible Explore-attributed engagement against baseline settles that the discovery gap wasn't the retention lever worth iterating on further without new evidence. Worth keeping on record, not treated as a blocker: the 2026-07-24 surge motivating renewed interest in this gate is Facebook/LET-direct-landing traffic (pre-signup acquisition-channel discovery), a different mechanism from the post-signup in-app discovery this release actually addresses (see the segmentation-refinement bullet under "Diagnostic Read" above) — if that segment's own retention doesn't move regardless of this release shipping, Explore didn't touch the real mechanism, and the eventual Diagnostic Read re-read is what will surface that, not this checkpoint alone. **Mid-release scope addition (2026-07-30):** `/explore` now owns authenticated content discovery; Collections becomes a pure workspace first, followed by a separately sequenced Dashboard-pointer PR. The checkpoint analysis must segment pointer-originated `EXPLORE_VIEWED` events using `source` metadata from direct/nav views rather than treating all Explore page views as one comparable cohort.
- What ships: authenticated nav becomes `Dashboard / {profile-aware Collections label} / Library / Explore / Progress` (not the literal string "My Reviews" — that text never existed in code; see the Current Release Baseline note). Explore is a new nav item (not a replacement canonical content URL) compositing the existing Official Review Set catalog (`/collections/published`) and `/public/library` behind a segmented control, plus a pointer to the Exam Hub index. Progress was already promoted from sub-page to first-class nav item in `v0.59.0` and is unchanged here. `/exam/[slug]` gains one additive check: resolve the hub's `courseProgram`(s) against published Official Review Sets — a match adds a preview+adopt path. Library stays untouched and structurally separate from Collections.
- Reuses almost nothing from fable-out (built on already-shipped mainline machinery instead: v0.41.1 Primary-card hierarchy, `PlanPicker` + `?collectionId=`, `getCollectionLabels`, the copy funnel's `redirectTo` param).
- One flagged-not-resolved recommendation: adopting an Official Review Set with no existing Primary sets it as Primary — does not resolve the still-open Primary-Review-Set-vs-Study/Exam-Focus question.
- **Two release-sized chunks, by design (different risk profiles), now separately gated:**
  - `v0.59.0 — Dashboard & Progress Reorg` (originally `v0.59.0`, shifted to `v0.60.0` when Reusable Practice Assets claimed `v0.58.0`, then reclaimed `v0.59.0` on 2026-07-24 once its own gate cleared before Explore's — see Current Release Baseline note): Dashboard hero → Primary Review Set condensed card, Progress promotion, the adopt-sets-Primary default. **Gate (new): proceed only once Reusable Practice Assets & the Return Loop has shipped** — there is no stable progress to promote to a top-level nav item until quiz content stops regenerating every session. Also still touches default states on pages users already rely on daily — do not fold into the same release as the chunk above. **Gate satisfied 2026-07-24 (v0.58.0 shipped); shipped and signed off as `v0.59.0` (Released).**
  - `v0.67.0 — Explore Convergence` (originally `v0.58.0`, shifted to `v0.59.0` when Reusable Practice Assets claimed `v0.58.0`, then to `v0.60.0` when Dashboard & Progress Reorg reclaimed `v0.59.0`, then to `v0.61.0` when Shared Official Pool Foundation reclaimed `v0.60.0`, then to `v0.62.0` when Challenge Quiz Quota Increase reclaimed `v0.61.0` on 2026-07-28, then to `v0.63.0` when Knowledge Impact reclaimed `v0.62.0` the same day, then to `v0.64.0` when Ask Companion reclaimed `v0.63.0` on 2026-07-29, then to `v0.65.0` when Add to Review Set reclaimed `v0.64.0` the same day, then to `v0.66.0` when Study Effectiveness Polish reclaimed `v0.65.0` the same day, then to `v0.67.0` when Challenge Quiz Result Clarity reclaimed `v0.66.0` on 2026-07-30 (all unrelated ratified work, not Diagnostic-Read-gated items — see Current Release Baseline note) — see Current Release Baseline note): new Explore nav item, segmented Review-Sets/Notes control, Exam Hub additive official-set check. **Gate (new): proceed only if the Diagnostic Read above indicates a discovery problem** (exam-dated users bouncing before reaching value) — otherwise this is a discovery bet the read didn't support. **Gate explicitly overridden by owner ratification 2026-07-30 — kicked off as `v0.67.0` the same day, gate remains formally unmet (see the override note above and Current Release Baseline). No runtime gate-check logic is added to the codebase — the override is a documentation-level decision only.** Shipped 2026-07-30, signed off 2026-07-31 after a full pre-signoff pressure test (release-wide surface area, 3 PRs) found and fixed one checkpoint-measurability gap and logged nine further findings as Known Limitations — see `RELEASES.md` v0.67.0.
- Producing more Official Review Sets remains bottlenecked on the separate, still-unscoped Curator pipeline (Smart Review Planning) — Phase 2 does not solve this; Phase 3's authoring slice hits the same gap.

### Phase 3 — Cross-user question pool + bounded reusable-object model
**Status: 3a ratified, kicked off, and shipped 2026-07-24 as `v0.60.0` (Released; reclaimed from Explore Convergence, which renumbers to `v0.61.0`) — its proposed gate cleared on real adoption data, not on "we already do per-user pooling."** Retargeted the same day from the original exam-pool design to Challenge Quiz Official template sharing — see `RELEASES.md` v0.60.0. 3b stays drafted, not ratified, parked on its own unrelated review-queue dependency (see below). Source: `company-redefinition-out/04-reusable-assets-and-reviewer.md`.
- **3a gate evidence (2026-07-24):** `company-redefinition-out/10-phase3-adoption-concurrency-check.sql`, run against production. Two shared Official Review Sets show real, concentrated, currently-active adoption: `LET Comprehensive Review` (Education Goal, 4 children) at 23 distinct adopters, 22 of which landed in the last ~24-48h (an active surge, not historical build-up); `PNLE Core Nursing Review` (Nursing Goal, 7 children) at 9 distinct adopters, 7 in the last 14 days. Denominator context: only 4 distinct Goals have ever been adopted at all, and these two account for ~84% of all goal-adopters (32 of ~38) — this is genuine concentration, not a flat spread. Absolute scale is still modest (23 and 9 people) and the LET surge's track record is only ~1-2 days old at ratification time — the owner weighed this explicitly and chose to proceed rather than wait for a longer trend confirmation.
- **Callout: this is not the same thing as "Reusable Practice Assets & the Return Loop" above, even though both involve `ExamQuestionPool`.** Phase 3 is **cross-user** sharing — many different learners who adopt the same Official content drawing from *one* pool. The new initiative above is **per-user, cross-session** reuse — one learner's own repeat practice reusing their own previously-generated questions instead of regenerating. Phase 3 needs the resolver + child table below; the new initiative needs neither. Don't let "we're already doing pooling" become an argument to pull Phase 3 forward — its own gate (adoption volume) is unrelated to and unmet regardless of the new initiative shipping.
- Foundation slice (3a), **retargeted 2026-07-24**: originally scoped as a `resolvePoolKey(studyPackId)` step inside `ExamQuestionPoolService` (Long/Board Exam pools). Retargeted after confirming Long/Board Exam is PRO-gated with ~zero production load (`StudySnapProperties.isLongExamAvailable`/`resolveMonthlyBoardExamLimit` both return 0/false off PRO) — the gate evidence's adopters are FREE-tier and almost certainly hitting Challenge Quiz instead. Diagnostics (one-off production scripts, run then not retained in the repo) confirmed the Official source study packs exist and are generated, but have zero existing Challenge Quiz bank rows — so an eager seed is additive, not redundant. New design: eager-seed a Challenge Quiz question template once per Official study pack (triggered on note-becomes-Official and on Study-Pack-generation-if-already-Official, plus a one-time backfill for already-published content), and have the claim path copy fresh per-user rows from that template instead of calling the LLM, falling through to live generation only for any remaining shortfall. No schema change to `challenge_quiz_question_bank` — copies are ordinary per-user rows, so `claimIncorrectQuestions`/"redo what you missed" is unaffected. See `RELEASES.md` v0.60.0 for full scope and the reconciliation with the existing "do not pre-generate Challenge Quiz" ruling below.
- Authoring slice (3b): curator-side pool expansion, batches land pending-review before READY, reusing the review-queue shape from the parked Smart Review Planning docs.
- Bounded object model: of 8 proposed fields, 5 need zero new work; Flashcards stays derived; Difficulty is cut (already covered by `DIFFICULTY_SELECTION`). **The 3a resolver + child table is the only genuinely new build in the whole model.**
- Reviewer decision: label-only, no new entity — `getCollectionLabels("BOARD_EXAM")` already returns "Review Set"; "Reviewer" ships as a relabel.
- **Real dependency, not smoothed over:** 3b's review-queue mechanism does not exist in the codebase today. It either waits for the Smart Review Planning Curator pipeline to ship, or needs its own small standalone queue as net-new Phase-3 scope. 3a has no such dependency.
- Proposed gate (not stated by the owner, proposed by Fable): don't kick off 3a until adoption telemetry (already emitted by Phase 1/2's adopt/copy paths) shows a shared Official Review Set with enough concurrent adopters that duplicated per-owner pool generation is a measurable cost, not hypothetical. **Gate satisfied 2026-07-24** — see the gate evidence bullet above. 3b's own gate (the review-queue dependency) is untouched by this and remains unmet.
- **Release chunks:** `v0.60.0 — Shared Official Pool Foundation` (3a only, retargeted to Challenge Quiz Official template sharing, no review-queue dependency, shipped standalone) is **Released** — see its section below; a later TBD release for 3b once the review-queue question is resolved; the Reviewer relabel can ship independently in either chunk and has not been assigned to either yet.

### Phase 4 — Packaging / terminology delta
**Status: Ratified 2026-07-31 (§4 items 1, 2, 3, 6); partially shipped in `v0.68.0`. Unaffected by the 2026-07-24 resequencing.** Source: `company-redefinition-out/05-packaging-and-terminology.md`. **Corrected 2026-08-01 at `v0.68.0` signoff:** this section still read "Drafted, not ratified — needs an explicit owner decision before scoping" and still described the rename as future work, contradicting both the Current Release Baseline and the Backlog Index row. Commit `6201eaee`, titled "correct Phase 4 status," corrected only line 9 and the Backlog Index row and left this section stale — the same partial-sweep failure the release documented elsewhere. **Shipped in `v0.68.0`:** item 6 (the rename, plus two consistency batches), item 8 (the retry-grammar fix), and item 4's first slice as the widened Messaging Architecture initiative. **Still open:** items 5 and 7's implementation substance, and item 9 (out of scope by `05`'s own constraint).
- What ships (ratified 2026-07-31; the rename shipped in `v0.68.0`): Creator (bring-your-own-notes) and Curated Learning (adopt Official Review Sets) stay **one product** on the existing FREE/PLUS/PRO ladder — a messaging distinction, not a pricing fork. No new SKU. Terminology delta, top item: rename "Generate Note"/"Generate a note" → "Create a Note"/"Draft a Note" (freeform AI-authored prose with no source note shouldn't borrow "Generate"'s differentiator language); "Generate Study Pack"/"Generate Quiz"/"Regenerate Quiz" keep the generation-flavored verb since that names the real differentiator.
- Directly reuses `fable-out/05`'s already-recommended tier placement (FREE=adopt, PLUS=conversational assembly, PRO=adaptive planning) and its recommendation that adopting Official Review Sets stays free and unmetered at every tier.
- **Owner-must-decide gate:** source doc `05` carries a formally-headed "§4. Owner must decide" section — the only one of the six Fable docs with one. Phase 4 could not be scoped for `/kickoff` until those items were actually decided, not just acknowledged. **Satisfied 2026-07-31 for items 1, 2, 3, 6** — those were explicitly decided by the owner, and item 6 was kicked off as `v0.68.0` the same day. Items 5, 7, and 9 remain undecided, so any further Phase 4 scoping is still gated on them.
- **No engineering dependency on Phases 1–3 — the one phase free to move earlier if ratified sooner.** The terminology-rename slice specifically is small enough (copy/label change, no new infra) to be a direct Claude-Code frontend change per this repo's task-routing table, and could ride along inside any other release's polish bucket once ratified, rather than needing its own release.
- **Illustrative release chunk (only if tracked standalone):** `vX.Y.Z — Terminology & Packaging Cleanup`.

### Dependency spine (resequenced 2026-07-24)
Phase 1 (shipped) → Diagnostic Read (ratified; Round 1 already run) → Reusable Practice Assets & the Return Loop (ratified, **shipped as `v0.58.0`**, gated only on Phase 1 having shipped) → Phase 2, split: `v0.59.0` Progress **shipped (Released)**, gate satisfied by Reusable Practice having shipped; `v0.67.0` Explore (renumbered 8 times as unrelated ratified work reclaimed each intervening slot — see Current Release Baseline for the full chain) gated on the Diagnostic Read showing a discovery problem specifically, **gate explicitly overridden by owner ratification 2026-07-30, kicked off as `v0.67.0` the same day (In Progress)** → Phase 3a **gate satisfied 2026-07-24 on real adoption-concurrency data, retargeted to Challenge Quiz Official template sharing, shipped and signed off as `v0.60.0` (Released)**; Phase 3b remains separately gated on its own unresolved review-queue dependency, unaffected by 3a's ratification. **Phase 4 has no dependency on any of the above and floats freely on owner ratification timing** — this is the lever to pull if reprioritizing without new engineering risk.

### Backlog Index rows this roadmap supersedes or folds into
- **Smart Review Planning (Internal Curator, 7 docs)** — partially folded, not closed. Phase 3 carves out and re-gates only the bounded-object-model/cross-user-pool/Reviewer-relabel slice; the curriculum-authoring pipeline (templates, matcher, gap-fill queue, Plan-My-Review wizard) stays exactly as Parked, same original gate.
- **AI-generated Review Sets / Runtime Companion** — splits. "AI-generated Review Sets" is effectively closed/ruled out by the locked curation-never-generation architecture this whole effort re-affirms. "Runtime Companion / Ask Companion / Personalization" is untouched and stays Parked separately.
- **Review-Set-Centric Navigation** — its drafted navigation shape was reached on 2026-07-30 through Progress promotion, the profile-aware Collections label, and Explore convergence. Library remains a separate, non-Review-Set-organized concern by design; the still-open Primary-vs-Study/Exam-Focus philosophy question is not implied resolved.
- **Product-language row** — no standalone row exists; Phase 4 is where the terminology-delta content now lives (extends `fable-out/06`'s rename map, and reverses its blanket "keep Generate Note, not touched" stance — **for the onboarding/editor/demo action *and* for the `PLAN_COMPARISON_ROWS` label "Topic note generation"**, both renamed in `v0.68.0`, with a new argument for why). **Corrected 2026-08-01:** this line previously scoped the reversal to "onboarding specifically," which understated it — `06` marked the plans row **keep** too, and cited it as an example of already-correct outcome naming in its own policy section. A future session executing `06` as source-of-truth would therefore have restored `Topic note generation` and silently reverted `v0.68.0`. `06` now carries an inline status note plus annotations on all four affected spots (two "keep" verdicts, a policy-section example, and the forward-looking "Consistency guard for future copy" that had blessed "Generate a note" — corrected at signoff from "both affected entries", which undercounted it in the same way `06` itself had undercounted); the rest of its map (the unexecuted "AI" de-emphasis rename — `ai-suggestion-modal.tsx`, "AI Critique" → "Answer Critique", Help/Learn mechanism copy) is unaffected and still needs its own scoping pass and `/kickoff`. That remaining work is the actual substance of §4 item 7 and is **not** folded into `v0.68.0`.

### Nothing here is authorized for implementation until ratified
Each phase and each new item above needs the owner's explicit ratification before its own `/kickoff` — this applies equally to all of them, not just the ones with a stated behavioral/adoption gate. Phase 2's original behavioral-read gate was explicitly overridden by the owner on 2026-07-23, and that same "proceed to Phase 2 next" decision was itself reversed on 2026-07-24 in light of the signup surge and the reusable-assets realization — both are ratification decisions in their own right, not bypasses of the ratification requirement itself. Phase 3a's proposed adoption-volume gate was evaluated against real production data (not waved through on signup enthusiasm alone) and ratified by the owner 2026-07-24 — see the gate evidence bullet in the Phase 3 section above.

## v0.66.2 — Card Surface Token Fix (Released)

**Kicked off 2026-07-30.** Patch release off `v0.66.1`'s line — see Current Release Baseline above.

**Signed off 2026-07-30.** Shipped on a single PR (`fix/bg-card-token`, PR #947) — one two-line CSS change, but with a 23-site blast radius, so the owner requested an independent Codex audit rather than the default single-`advisor()` pass. That audit is the reason this section's "false override claim" correction exists (see the paragraphs above) — caught and fixed before signoff, not left as a known limitation. `tsc`, lint, and the full Jest suite (157/157) were clean throughout; the `getComputedStyle` verification (not just screenshots) is what actually resolved the conflict between the original visual read and the audit's static-analysis claim. Known Limitations: none.

**Origin.** The `bg-card` no-op finding logged in the Backlog Index during `v0.66.0`'s pressure test (no `--color-card` token registered in `globals.css`, so the utility silently drops everywhere it's used). Picked as the next small, ungated candidate after a fresh scan of the Backlog Index found nothing else ready outside Diagnostic-Read-gated items and large multi-doc initiatives.

**Re-verification found the original finding undercounted, 2026-07-30.** A fresh grep found 21 usages across 10 files, not the 9 originally logged — `long-exam/page.tsx` (8 call sites) and `challenge-quiz/page.tsx` (5) were missed the first time. This meaningfully changed the risk profile: the real blast radius is core exam-mode UI (Long Exam and Challenge Quiz's active-question cards, Board Exam's question card), not a handful of peripheral cards. Corrected in the Backlog Index row before scoping further.

**Token value chosen from real-browser evidence, not picked blind — but the first pass of that evidence was itself wrong.** Two candidates existed: alias `--card` to `--surface-alt` (an explicitly elevated tone) or to `--background` (making the current accidental appearance the intentional one). Both were wired into `globals.css` temporarily and rendered via a throwaway preview route reproducing three real patterns verbatim from source, in both themes. Visual inspection of the screenshots was read as: `--surface-alt` made the `Card` + `bg-card` override pattern (`challenge-quiz/page.tsx:2090`, `long-exam/page.tsx:1090`) render identically to a default `Card`, while `--background` preserved a visible distinction. **This visual read was wrong** — a subsequent Codex audit (requested by the owner as an independent pressure test) found that `cn()` has no `tailwind-merge` and the compiled stylesheet's cascade order, not JSX class order, decides equal-specificity ties; `.bg-card` compiles before `.bg-surface-alt`, so `bg-surface-alt` wins on both patterns regardless of what `--card` aliases to. A direct `getComputedStyle` check (not another screenshot) confirmed it: both the "override" `Card` and a plain `Card` compute to the identical `rgb(249, 250, 251)` — the visible difference in the earlier screenshot came from the override's different border color (`border-foreground/15` vs. the default `border-border`), not a different fill, and was misread as a fill difference.

**The token choice survived the correction; the stated reason for it didn't.** The `getComputedStyle` check also confirmed the *other* piece of evidence still holds: `ScoreReveal`'s nested standalone panel (a plain `bg-card` div with no competing `bg-surface-alt` class on the same element, sitting inside a parent `Card`) computes to `rgb(255, 255, 255)` — visibly distinct from its container's `rgb(249, 250, 251)`. That's real, unambiguous separation, unaffected by the cascade-order issue since there's no second background class on that element to compete with. `--card: var(--background)` remained the right choice on that narrower, now-verified basis; the "preserves the Board Exam override" rationale was removed from `RELEASES.md` rather than left standing. **Lesson applied:** a visual screenshot comparison can be fooled by an adjacent, unrelated style difference (here, the border color) when the actual claim is about a specific CSS property; a `getComputedStyle` check on the specific property is the deciding test, not the screenshot.

**One incidental, genuine visual change surfaced by the same audit:** `quiz-generation-overlay.tsx`'s modal panel (previously transparent against a blurred `bg-background/85` scrim) is now a solid, legible panel — checked in both themes via a real render; a positive change, not a regression, but the original scoping had incorrectly implied every usage was visually inert.

**Anti-drift:** CSS token registration only — no component logic, className, or markup changes at any of the 21 call sites; nothing about how `Card`'s own default `bg-surface-alt` works changes.

## v0.66.1 — Goal Detail Due-Concept Signal (Released)

**Kicked off 2026-07-30.** Patch release off `v0.66.0`'s line — see Current Release Baseline above.

**Signed off 2026-07-30.** Shipped on a single PR (`fix/goal-detail-due-concept-signal`, PR #945) — two small files, one concept, well under the full-pressure-test threshold. `tsc --noEmit` and lint clean, full Jest suite 157/157 unaffected (no existing test coverage for this page-client file to update), and the due-color change was verified in a real browser render in both light and dark mode via a throwaway preview route (deleted before commit, never merged). A single `advisor()` check at signoff found and fixed one doc-consistency gap in this section's own draft — see Current Release Baseline above — not a code finding. Known Limitations: none.

**Origin.** Surfaced while discussing what's next after `v0.66.0`'s signoff. The Post-v0.40.0 Polish Backlog already named a candidate — "Overdue color-warning on child Subject-plan cards" — flagged there as cheap and using existing data, but explicitly requiring "a conscious sign-off, not an automatic yes" since a warning color leans toward the "monitoring" framing the locked collection-detail anti-drift rule pushes back on.

**Research before scoping, 2026-07-30.** Read `frontend/app/collections/[id]/collection-detail-page-client.tsx`'s `GoalDetailView` directly rather than trust the backlog note's framing alone. Two findings: (1) `AGENTS.md:142`'s "execution rows" phrase specifically means per-note practice-status rows (`getNoteExecutionStatus`, same file) — a different UI element than the Goal → child Subject-plan summary cards this candidate targets, so the rule's literal terms don't crisply cover this surface. (2) More materially: this exact card already shows `child.overallReadinessPercentage` plus a progress bar (lines 1201-1217), shipped 2026-06-30 with the original Goal → Subject hierarchy feature — a real mastery-percentage display on a collection-detail surface, which `docs/features/collections.md:481` documents as excepted only for the dedicated `/progress?collectionId={id}` route. No RELEASES.md/ROADMAP.md/archive trail ratifies this as a deliberate second exception; it appears to be an undocumented gap between the shipped surface and the written rule, not a fabricated one.

**Scope, agreed with the owner 2026-07-30:** fold both into this one patch release rather than treat them separately — see `RELEASES.md` v0.66.1 for the two Planned Scope items (doc fix + due-color addition). The due-color addition is scoped as a small, consistent extension of a card that already carries a mastery signal, not a fresh departure into "monitoring dashboard" territory; presence-based (`dueConcepts > 0`) rather than a magnitude threshold, to avoid inventing a new threshold system.

**Anti-drift:** no change to how `dueConcepts` or `overallReadinessPercentage` are computed, no new backend field, no new threshold constant; the doc fix corrects `AGENTS.md`/`collections.md` to match already-shipped behavior, it does not relax the underlying no-mastery rule for any other surface (collection list cards, published-plan cards, public source plans, and per-note execution rows are unaffected).

## v0.66.0 — Challenge Quiz Result Clarity (Released)

**Kicked off 2026-07-30.** Reclaims the `v0.66.0` slot from `Explore Convergence` (renumbered to `v0.67.0`) — see Current Release Baseline above.

**Signed off 2026-07-30.** Shipped on a single PR (`feat/challenge-quiz-result-consolidation`, PR #943) — frontend-only, single concept, well under this doc's own full-pressure-test threshold. The owner requested an independent Opus review anyway (fresh agent, no inherited context, instructed to read the real diff rather than trust a summary) plus a real-browser check rather than the default single-`advisor()` pass. That review confirmed the additive `ScoreReveal` API change left Board Exam Mode and Long Exam Mode byte-identical, found no data loss or NaN/divide-by-zero risk, and surfaced two real gaps fixed before signoff: the "Answered Accuracy vs. Overall Completion Score" explainer sat two paragraphs below the metrics it explains (moved adjacent to the reveal), and the secondary metric's `aria-label` was on a `<p>`, a role ARIA doesn't guarantee gets announced (removed rather than left as misleading dead code). A third flagged concern — `ScoreReveal` nesting inside the standard result screen's existing outer `Card`, read from the diff alone as a possible "card-in-a-card" — was checked with an actual rendered screenshot (light and dark) and did not hold up; no structural change was made. Known Limitations: none.

**Origin.** Logged as a Known Limitation while signing off `v0.65.0`'s card-accretion layout pass: that pass's Codex prompt explicitly scoped only the post-session guidance cards (`PostSessionNextStep`/`GoalNudgeCard`/`WeeklyPacingEchoCard`/`CompanionResultBridgeCard`/`TwiceMissedAskCompanionCard`), not the score/outcome area above them, and a real-browser visual audit flagged that outcome area as the reason Challenge Quiz's result screen was the weakest of the three modes (Adaptive Practice's was rated strongest). Chosen over the other identified next-release candidate — resolving the Primary Review Set vs. Study/Exam Focus philosophy question — which stays open in the Post-v0.40.0 Polish Backlog for a future release.

**Scope, verified against current code 2026-07-30:** Standard Challenge Quiz mode's result branch (`frontend/app/study-packs/[id]/challenge-quiz/page.tsx`, ~line 2548-2639) stacks three separate, partially-duplicated visual blocks — a raw score `<div>` (percentage, correct count, duration, message, performance badge), a "Score Summary" `Card` re-displaying the same Correct/Answered/Percentage numbers in a grid, and a "Concept Breakdown" `Card`. Board Exam Mode's own result branch in the same file (~line 2331-2379) already converged on a cleaner pattern: the shared `ScoreReveal` component (`frontend/components/exam-mode/score-reveal.tsx`) plus one Concept Breakdown card. `ScoreReveal` needs a third `challenge-quiz` tone for standard mode. **Dual-metric decision resolved before implementation:** keep Answered Accuracy primary and extend `ScoreReveal` with an optional subordinate `secondaryMetric` for Overall Completion Score when questions remain unanswered; Board Exam and Long Exam omit the additive prop and remain unchanged.

**Anti-drift:** presentation-only, no score calculation or concept-breakdown data changes; Quick Review and Adaptive Practice result screens are explicitly not in scope (the Known Limitation named Challenge Quiz specifically); Board Exam Mode's branch is the reference pattern to converge toward, not to modify.

## v0.65.0 — Study Effectiveness Polish (Released)

**Kicked off 2026-07-30.** Reclaims the `v0.65.0` slot from `Explore Convergence` (renumbered to `v0.66.0`) — see Current Release Baseline above.

**Signed off 2026-07-30.** Shipped on four PRs (#938 collapsed-Companion teaser, #939 Study Pack scope surfacing, #940 card-accretion layout pass, #941 Adaptive Practice rationale tag), each individually audited (checklist + build/test) before merge, plus a real-browser visual review (#940) that found and fixed two presentation bugs no automated check could catch. Per this doc's own pre-signoff gating rule, checked whether the release crossed the full-pressure-test threshold: 4 PRs (below the 6+ bar), no single concept spanning 3+ surfaces, and two same-file overlaps (`collection-detail-page-client.tsx` between #938/#940, `adaptive-practice/page.tsx` between #940/#941) confirmed to land in disjoint rendering regions, not a shared method — so a single `advisor()` summary was the right depth, not the full multi-agent pressure test. That check flagged one real thing to verify (whether the new rationale pill row could render on the completed/result branch, given how close its hunk sat to #940's result-screen changes in the same file) and one process gap (the merged-`releases/v0.65.0`-branch build/test hadn't actually run yet — each PR was only verified pre-merge, on its own branch). Both resolved before signoff: confirmed by direct code inspection that the rationale row renders only in the active-question branch, never the result branch; then ran `./mvnw clean install`, `tsc --noEmit`, and the full Jest suite on the merged branch — all green (157/157 suites, 1586/1586 tests). Known Limitations: two, both logged below, neither fixed this release (Challenge Quiz's outcome-area accretion — out of this release's scope; the rationale pill row's own layout on narrow viewports/matching groups — owner opted to verify in-browser directly rather than route through a separate visual-review pass).

**Origin.** The 2026-07-22 Fable consultation (`docs/claude-prompt/study-effectiveness-out/01-study-effectiveness-ui-pricing.md`) named six Flow/UI Polish candidates plus four Pricing Fit findings, explicitly scoped to exclude retention-trigger mechanics. Two items shipped already, in earlier releases: Flow #1 (link missed/weak concepts to their explanation) as `v0.56.0`; Flow #6 (on-demand re-explanation) as the twice-missed-concept → Ask Companion feature in `v0.63.0`. Pricing #1 (Difficulty Selection to Plus) is dead — that selector was removed entirely in `v0.60.1`. That left six remaining candidates in the Backlog Index row, marked `[EFFORT — authorizable today, no data dependency]` — this release scopes and ships the ones still real.

**Scoping-pass verification against current code, 2026-07-30 (not the 2026-07-22 snapshot — v0.56.0 through v0.64.0 shipped in the interim):**
- **Note Detail tab-order mismatch — already resolved, struck from scope.** `docs/features/note-detail.md:125` locks the current tab order as a deliberate decision "not to be reopened without a fresh product decision," and documents the shipped remedy: a `GuidanceTip` nudge on the Quiz tab pointing back to Full Notes (`quizFullNotesNudgeGuidance`, `frontend/components/notes/private-note-detail-page-client.tsx:2352`). Nothing to build.
- **Key Concepts readiness sort — already shipped, struck from scope.** `sortedKeyConceptEntries` (`private-note-detail-page-client.tsx:907-919`) already sorts by `getConceptSortRank(readinessStatus, isStruggling)`, due/struggling first, generation-order as tiebreak.
- The remaining four are real and make up this release's scope (below).

**Planned Scope, verified against current code:**
- **Study Pack scope surfacing (backend + frontend).** No note-list card or the Summary tab currently shows concept count, quiz length, or review time. `StudyPackEntity.keyConcepts`/`.quiz` (`backend/.../entity/StudyPackEntity.java:58-63`) already carry this data for the Summary tab — free `.size()` there. The note-list response DTO does not currently carry these counts for the card view; needs a small backend addition. Owner explicitly chose both surfaces over Summary-tab-only, accepting the backend DTO touch.
- **Card-accretion layout pass (frontend only, Codex).** Confirmed worse than the 2026-07-22 audit described — the stack has grown since, not shrunk. Current Review Set Detail Goal-view stack: Hero → `TodaysFocusCard` (mentor tip now merged inside) → `ReadinessSummary` (with countdown) → post-adopt `GuidanceTip` (conditional) → `CompanionDisplayCard` → `AskCompanionPanel` (conditional, new in `v0.63.0`). Adaptive Practice result screen: score → weak-areas → `PostSessionNextStep` → `GoalNudgeCard` (conditional, new) → `WeeklyPacingEchoCard` → `CompanionResultBridgeCard` → `TwiceMissedAskCompanionCard` (conditional, new in `v0.63.0`) → action buttons → `BackLink` → conditional answer review/feedback panels. Quick Review and Challenge Quiz result screens follow the same component set and structure. Presentation-only reorder/consolidation, no logic change, but touches the detail page plus 3 result-screen variants — routed to Codex per this project's task-routing table (>5 files in practice once all three result-screen variants are touched).
- **Collapsed-Companion teaser (frontend only, Claude Code direct).** `CompanionDisplayCard` (`frontend/app/collections/[id]/collection-detail-page-client.tsx:1064-1149`) renders literally nothing — not even a preview line — when collapsed. One component, one-line overview excerpt added to the collapsed branch. The collapse-by-default decision itself (documented Coach vs. Companion doctrine) is unchanged.
- **Adaptive Practice per-question rationale tag (backend + frontend, Codex).** `QuizItem` (`backend/.../dto/QuizItem.java`) deliberately remains free of selection provenance because that fact is relative to one session, not intrinsic generated content. Selection happens in `QuickReviewAdaptivePracticeService`'s weak/due-concept merge logic; capture due-only / weak-only / both before the merge loses that distinction, then persist and return a parallel index-aligned reason array beside the quiz in session state. Render it per question during the active session (e.g. "Reviewing: Ohm's Law — missed last time"). The largest item this release; genuine backend + frontend, not polish-only.

**Anti-drift verified, not assumed:** none of the four touch `EXAM_MODES.md`'s locked no-mid-exam-coaching rule (`EXAM_MODES.md:323`, which names Board Exam and Long Exam specifically) — the rationale tag is static deterministic metadata computed at selection time, not an LLM call, so it doesn't cross into "interactive AI"; the layout pass removes no card and changes no logic, presentation only; Companion's collapse-by-default choice stays, only the collapsed-state emptiness is fixed.

## v0.64.0 — Add to Review Set (Released)

**Kicked off 2026-07-29,** surfaced while discussing v0.63.0 Ask Companion's own signoff. Reclaims the `v0.64.0` slot from `Explore Convergence` (renumbered to `v0.65.0`) — see Current Release Baseline above.

**Signed off 2026-07-29.** Shipped on a single PR (`feat/add-to-review-set`, PR #935) — frontend-only feature plus docs, no shared-method collisions across PRs, so per this doc's own pre-signoff gating rule this needed a single `advisor()` check rather than the full multi-agent pressure test. That check flagged one real gap: `AddToCollectionModal`'s `itemNoun` prop defaulted to `"imported draft"` (preserving Bulk Import's exact prior copy), meaning a future third caller that forgot to pass it would silently inherit import-specific wording. Fixed before signoff — `itemNoun` is now a required prop with no default, and Bulk Import's call site passes `"imported draft"` explicitly. No other findings; Known Limitations: none.

**A second architectural question arrived mid-release** (before this item's own implementation began): whether "Ask Companion" should evolve into a cross-cutting "Companion" guidance system spanning Dashboard/Review Set/Progress/Readiness. Pressure-tested with Opus, resolved as *considered and narrowed* (doctrine adopted, literal merge rejected) — full writeup in the Backlog Index row "Companion Guidance Doctrine" above. Confirmed orthogonal to this release; "Add to Review Set" and the doc corrections below shipped exactly as originally kicked off.

**Origin.** Before signing off v0.63.0, the owner asked whether Companion should really stay Review-Set-only, given users who never adopt a Study Plan/Review Set get no guidance layer at all. Claude's first pass: ship v0.63.0 as scoped, treat this as a separate question. The owner took it to GPT for a second opinion; GPT reframed it as "what role do Notes play in the Learning OS" and proposed keeping Companion permanently Review-Set-only while giving Note Detail "transition features" (add-to-Review-Set, browse Official sets covering this topic, create-a-Review-Set-from-this-subject) so note-only users aren't left out of the guidance layer. The owner asked for an Opus pressure test of that proposal before acting on it.

**What the pressure test changed.** GPT's conclusion (no Companion on Notes) held up; its reasoning and proposed shape didn't, fully:
- **Corrected the framing.** Companion isn't "Review-Set-scoped" — it's admin-authored-Official-collection-scoped. `NoteCollectionService.setCompanion`/`clearCompanion`/`generateCompanion` all call `assertAdmin(user)` before touching `collection.companion` — a learner-created Review Set can never have one, because nobody curated it. That's a structural fact, not a permanent philosophical stance about curriculum-vs-note altitude — so "permanently Review-Set-only" is dropped in favor of "the **authored** Companion stays scoped to curated Official collections"; Personalization (PRO, still Parked not ruled out) is the mechanism that could eventually reach a no-Review-Set learner some other way.
- **Rejected the "create a Review Set from this note" bridge as a guidance entry point.** Same `assertAdmin` fact means a user-created collection gets no Companion and doesn't clear Ask Companion's renderable-content eligibility either — that bridge routes a learner to the guidance layer's front door and hands them nothing.
- **Rejected a Note Detail recommendation/nudge entirely.** At the time, `DashboardStudyPlanSection` supplied the "adopt/continue an Official set" recommendation on Onboarding, Dashboard, and Collections. Corrected 2026-07-30: the "Explore Owns Discovery" addition removes that component from Collections, and Library's `GuidanceTip` is note-creation guidance, not set-adoption guidance. A Note Detail version still does not belong on the one surface (`docs/features/guidance.md`'s own rule) meant to stay focused; authenticated discovery now points to Explore instead of being duplicated there.
- **Rejected "browse Official Review Sets covering this topic" on Note Detail.** Needs subject-level matching that doesn't exist (`NoteCollectionController.listPublic` matches `courseProgram` only) — placed next to one specific note, a courseProgram-only match implicitly claims relevance it can't back, the same false-precision shape already logged as a Known Limitation on v0.63.0's twice-missed CTA. This idea is also already scoped inside `v0.66.0` Explore Convergence's own additive official-set check — building a second, uncoordinated version now duplicates work whose gate clears in about a week.
- **Folded the "no-Review-Set learner's guidance surface" question into the existing, still-open Primary-Review-Set-vs-Study/Exam-Focus philosophy question** (Post-v0.40.0 Polish Backlog) rather than opening a new roadmap item — that question already commits to Study/Exam Focus as the load-bearing no-Goal fallback, so answering it a second time here would just recreate an uncoordinated second answer. See that Backlog row for the amendment.

**What actually ships: "Add to {Review Set}" on Note Detail (frontend only).** The one piece of GPT's proposal that survives — a genuine capability gap (today, adding an already-existing note to a collection requires leaving Note Detail, going to Library, entering selection mode, and re-finding the note), user-*initiated* so it can never nag, and claims nothing about coverage/relevance so it carries none of the false-precision risk above. Justified as a friction fix on its own — no adoption-rate gate needed. Cheap because the component already exists: `AddImportedDraftsModal` in `frontend/components/notes/bulk-import-page-client.tsx` (private, file-local, `noteIds`-driven) already lists collections, adds via `addCollectionItems`, and creates-new via `createCollection` — extract it into a shared component and mount it from Note Detail with the single viewed note. No backend changes.

Anti-drift: no Companion content on standalone Notes, ever; no Note Detail nudge/recommendation surface; no subject-level Official-Review-Set matching (belongs inside `v0.66.0` Explore Convergence if ever built, gated the same way); no new Backlog Index row for the guidance-surface question — folded into the existing Primary-vs-Focus row.

## v0.63.0 — Ask Companion (Released)

**Kicked off 2026-07-29.** Ratified by the owner the same day v0.62.0 merged to `main` — see the "AI-generated Review Sets / Runtime Companion" Backlog Index row above for the ratification history. **Reclaims the `v0.63.0` slot from `Explore Convergence`** (renumbered to `v0.64.0`) — this item's gate (owner ratification) cleared 2026-07-29 while Explore's Diagnostic Read gate is still unmet, matching this doc's established precedent (`v0.58.0`, `v0.59.0`, `v0.60.0`, `v0.61.0`, `v0.62.0` each changed hands the same way — this is the sixth).

Design brief and original tiering rationale: "Future, gated — Runtime Companion (Ask Companion, Personalization)" section below. That section covered two features; **this ratification is Ask Companion (PLUS) only.** Personalization (PRO) is explicitly not in scope — it carries a second, separate gate (the open Primary-Review-Set-vs-Study/Exam-Focus philosophy question, Post-v0.40.0 Polish Backlog above) that Ask Companion does not share, since Ask Companion's only dependency was the persisted Companion existing (shipped v0.41.0/v0.42.0, confirmed live in the codebase).

**v1 scope, signed off by the owner at kickoff:**
- Grounded Q&A over a top-level Review Set's authored Companion content (`note_collections.companion`) — retrieval over static curator-authored text, not generation.
- Access: PLUS and PRO (not FREE). PRO does not lose anything PLUS gains.
- Scope boundary: only available on a collection whose `companion` has renderable authored content — exactly mirrors `CompanionDisplayCard`'s existing gate (hidden when Companion is null or only empty draft fields). No content to ground answers in otherwise.
- Monthly quota: 20 sessions/user. Higher than Interview Practice's PRO-tier 10/month since grounded retrieval is cheaper than generative simulation.
- Per-session cap: 6 turns. New mechanism — Interview Practice is a fixed-question-set flow with no existing open-ended turn-cap code to reuse; only the feature-gate/quota/rate-limit/cheap-model *pattern* carries over, not literal code.
- Model: CRITIQUE tier (`gpt-4.1-mini` default), same cost class as Interview Practice's feedback generation.
- Reuses the existing `AiRateLimitService` per-minute rate-limit pattern.

**Implemented in the v0.63.0 release worktree.** The shipped shape uses a dedicated `ask_companion_sessions` aggregate with JSONB question/answer turn history (not quiz-session persistence), one partial-unique ACTIVE session per user/collection, rolling-period usage in `user_usage`, three authenticated endpoints, and a collection-detail-only chat panel. See `docs/features/ask-companion.md` and `RELEASES.md` for the runtime contract and shipped summary.

Anti-drift: no mid-exam coaching (`EXAM_MODES.md`'s locked interactive-AI constraint is unaffected — this is a Companion-detail-page feature, not a quiz-session feature); grounded retrieval only, no free-form generation; does not touch Personalization, which stays gated separately.

### Item 2 — Twice-missed concept → Ask Companion, added 2026-07-29

**Scoped same day, after Ask Companion shipped.** Source: the Study Effectiveness backlog's "on-demand re-explanation" candidate (`study-effectiveness-out/01-study-effectiveness-ui-pricing.md` Flow #6), which explicitly deferred itself pending Ask Companion existing — "decide which surface owns 'I still don't get it' before scoping either." That decision is now made: Ask Companion owns it, no separate re-explanation mechanism gets built.

**Implemented in the v0.63.0 release worktree.** The three scoped completion responses now carry only the concepts whose persisted consecutive incorrect streak reached the threshold. Their existing result-page Primary Review Set resolver feeds one shared CTA component; the paid deep link initializes a collection-detail draft without starting a conversation or consuming quota.

**Verified before scoping, not assumed:**
- The collection-resolution problem is already solved. `CompanionResultBridgeCard` (rendered today on Adaptive Practice, Challenge Quiz, and Quick Review result screens) already resolves `getMe()` → `primaryCollectionId` → `getCollectionGoal()`, and the product already accepts that this doesn't guarantee the just-completed note belongs to the Primary collection (`docs/features/collections.md`'s Countdown & Pacing section). This item reuses that exact resolver and tradeoff — no new note→collection resolution path needed.
- "Missed twice" is not a signal that exists anywhere today. `ConceptHealthEntity` stores only `lastCorrectAt`/`lastIncorrectAt` timestamps, no incorrect count. This needs new backend tracking, which is why this item is backend + frontend, not a frontend-only deep-link.

**v1 scope, signed off by the owner:**
- Trigger: a new `incorrectStreak` counter on `ConceptHealthEntity`, incremented on a wrong answer and reset to 0 on a right answer — a *consecutive* miss streak, not a cumulative all-time count, so a concept the learner later masters stops qualifying. Fires the CTA at a streak of 2.
- Scope: the same three result screens `CompanionResultBridgeCard` already appears on. Does not extend to Long Exam, Board Exam, or Interview Practice — avoids reopening `EXAM_MODES.md`'s feedback-free/no-mid-exam-coaching question for those modes.
- Eligibility and CTA behavior:
  - FREE: shows the same upgrade nudge `AskCompanionPanel` already uses (`getUpgradeCtas(currentPlan, "ask-companion")`), regardless of whether an eligible collection exists — a deliberate choice, since this is a high-intent moment worth pitching even without a confirmed destination.
  - PLUS/PRO with an eligible `primaryCollectionId` (set, and its Companion has renderable content): CTA navigates to Review Set Detail with the Ask Companion panel's draft pre-filled (e.g. "Can you explain {concept} a different way?") — the learner still has to send it; it does not auto-fire and does not silently consume quota.
  - PLUS/PRO without an eligible collection: nothing renders. Not a plan gate, so an upgrade pitch would be wrong here.

**Release-shape note:** this is the second PR in `v0.63.0` to touch `AskCompanionService`'s session-start flow (Item 1 built it, this item calls into it) — crosses this doc's own "2+ PRs touching the same shared method" pressure-test trigger, so this release needs the full multi-agent pressure test at signoff, not a single `advisor()` summary.

Anti-drift: same as Item 1 — no mid-exam coaching, grounded retrieval only, does not touch Personalization.

**Pre-signoff pressure test, 2026-07-29 — Opus, three parallel agents, five findings fixed, seven logged as Known Limitations.** Requested explicitly by the owner given the release's size and the two-PRs-touching-one-shared-method trigger (see the "Release-shape note" above). Findings fixed, all on PR #933:
- **Critical, fixed:** `OpenAiAskCompanionLlmService` sent prior assistant-turn history to OpenAI's Responses API with content type `input_text`, which the API only accepts on `system`/`developer`/`user` roles — assistant-role content requires `output_text`/`refusal`. Independently confirmed against OpenAI's own documented error message. Every Ask Companion conversation would have failed starting on its second question, breaking the feature's entire multi-turn premise. Fixed by making the content type role-conditional; a new test asserts the serialized payload shape for both roles.
- **Fixed:** `AskCompanionService`'s session-start response re-read monthly usage after the native `@Modifying` quota increment in the same transaction; Hibernate's first-level cache returned the pre-increment entity, so the displayed `usedThisMonth` was always one behind actual. Fixed by passing the known post-increment count explicitly instead of re-querying — verified by direct code reading (the mocked unit test proves the arithmetic, not the Hibernate cache behavior itself, which isn't reproducible against a mocked repository).
- **Fixed:** the collection-detail page resolved `currentPlan` from a cookie-cached `authUser.planType`, while the three result pages already resolve it from a freshly-fetched usage summary — a learner who just upgraded or downgraded could see a deep link built for the wrong plan. Now resolves the same fresh way as the result pages.
- **Fixed:** a quiz item with no concept tag normalizes to a placeholder label (`"Unknown"` in the shared review-utils normalizer, `"Uncategorized"` in Challenge Quiz's own — two independent normalizers, not unified) that could reach `twiceMissedConcepts` and render as "Can you explain Unknown a different way?" Card now skips placeholder labels and falls through to the next real concept.
- **Fixed, pre-existing drift, not this release's regression:** `EXAM_MODES.md` stated "Quick Review does not write `ConceptHealth`," but `git log -S` confirmed Quick Review has written it (`lastCorrectAt`/`lastIncorrectAt`, feeding due-state/mastery/Overall Readiness) since a deliberate 2026-07-11 change — the doc was simply never updated. This pressure test is what surfaced the drift, so it's corrected here even though the underlying behavior predates v0.63.0 by weeks.

Known Limitations logged, not fixed this release (full reasoning in `RELEASES.md` v0.63.0): the twice-missed CTA can target the wrong Review Set and burn quota on a guaranteed refusal (materially worse than `CompanionResultBridgeCard`'s zero-cost version of the same tradeoff); `incorrect_streak` has no mode discriminator, so Board Exam/Long Exam/Interview Practice still write the counter the exposing modes read; the `V101` migration has no backfill for learners already mid-streak; the two placeholder-concept normalizers remain un-unified (only guarded at the CTA's last mile); the `?askCompanionDraft=` query param is never cleared from the URL; no scroll-to/highlight on deep-link arrival; and the session-row pessimistic lock in `askQuestion` is held for the full LLM call duration.

## v0.62.0 — Knowledge Impact (Released)

**Kicked off 2026-07-28.** Ratified by the owner despite its own data gate failing (3 non-official public-note creators against a 20-30+ un-park threshold) — see the "Knowledge Impact" Backlog Index row above for the full ratification reasoning and the 2026-07-28 bootstrap-test check that validated proceeding anyway (this is the worked example the gate-types taxonomy above was built around). **Reclaims the `v0.62.0` slot from `Explore Convergence`** (renumbered to `v0.63.0`) — this item's gate cleared 2026-07-28 while Explore's Diagnostic Read gate is still unmet, matching this doc's established precedent (`v0.58.0`, `v0.59.0`, `v0.60.0`, `v0.61.0` each changed hands the same way).

**Scoped 2026-07-28.** Four items, all verified against the actual codebase (not assumed from the design brief): (1) the official-author predicate fix, now confirmed as a **three-way** inconsistency, not two; (2) the Knowledge Impact dashboard itself — a new authenticated, self-only endpoint plus a frontend entry point, since none exists today; (3) the conditional-rate `AnalyticsEventType` pair the 2026-09-11 checkpoint depends on; (4) the optional opt-in digest — scoping recommended deferring this given the 3-creator audience and added migration/scheduling surface, but the **owner chose to include it in this release** during the scoping review, so it ships alongside the other three. `docs/claude-prompt/company-redefinition-out/09-knowledge-impact.md`'s "Answers to the memo's 12 questions" and "Risks to avoid, if/when this is un-parked" sections remain the design brief this scoping implements: passive/pull, retrospective + aggregate framing ("helped," never "ranked"), weight downstream signal over raw view/copy counts, nothing comparative/ranked/real-time/identifying across creators, private-to-the-creator-themselves, no streaks/badges/leaderboards.

**Item 1 implemented 2026-07-28.** The owner confirmed the production official account already holds `role = ADMIN`, so the predicate fix landed as a confirmed no-op today (see the "Shipped" bullet in `RELEASES.md`) — pure future-proofing, no reclassification, no backfill decision needed. `docs/features/public-library.md` and `docs/features/challenge-quiz.md` updated to describe the corrected classification rule.

**Items 2+3 implemented 2026-07-28.** The release worktree now has the authenticated self-only `GET /creator-impact/me` boundary, creator-level and per-note distinct-learner aggregates that require a completed downstream session, and the owner-only `Your Impact` section on the public profile page, plus the `KNOWLEDGE_IMPACT_DASHBOARD_VIEWED` / idempotent `PUBLIC_NOTE_PUBLISHED` event pair. Audit finding corrected before commit: Codex's first pass gated the pre-existing "View Public Page" link on `/profile` behind `hasPublicNotes`, which regressed that link from unconditional to hidden for any account without a public note (losing the only path to the owner-only visibility toggle on that page) — reverted to unconditional, since that pre-existing link already satisfied the "discoverable entry point" requirement and never needed gating. Public profile response shapes and the existing visitor-visible views/copies/shares block remain unchanged. Item 4's optional digest remained separate from that work.

**Item 4 implemented 2026-07-29.** Creators with public notes now have an off-by-default `Knowledge Impact digest` toggle in the existing Email Preferences panel. A new monthly retention dispatch counts distinct learners with completed downstream quiz sessions in the trailing 30 days through a separate windowed query, skips zero-count creators, and reuses the existing 30-day cooldown, per-recipient failure isolation, templates, send log, and unsubscribe category flow. The cumulative dashboard query and response, the Item 3 analytics events, and the official-author predicate are unchanged.

**Pre-signoff pressure test, 2026-07-29 — one critical finding, fixed.** Three Opus agents independently audited the backend, frontend, and git/branch state after all four items merged. Two real findings:
- **Critical, fixed:** all three "learners helped" queries (`NoteRepository.countDistinctLearnersHelpedBySourceNoteIds`/`countDistinctLearnersHelpedByCreatorUserId`/`countDistinctLearnersHelpedByCreatorUserIdSince`) gated only on `session.completedAt is not null`, with no `session.status` check. `LongExamService.forfeitSession` (line ~525) and `InterviewPracticeService.forfeitSession` (line ~399) both set `completedAt` on forfeit, not just on genuine completion — so a learner who copied a public note, started a Long Exam or Interview Practice session on the copy, and forfeited it was counted as "helped" on the dashboard, in the headline, and could trigger a real Item 4 digest email. This directly undermined the release's own core design principle (weight genuine downstream signal, not raw activity). Fixed by adding `and session.status = QuickReviewSessionStatus.COMPLETED` to all three queries — verified as a genuine regression by confirming the two new forfeit-specific tests fail without the fix and pass with it. Minimal, contained fix: only the three new queries changed, no pre-existing forfeit behavior touched.
- **Minor, fixed:** the Settings page's new digest toggle and the public profile page's dashboard both call the same `GET /creator-impact/me`, but had divergent failure UX — the profile page shows "Could not load your impact" with Retry, while Settings silently hid the toggle on any transient failure with no error or retry affordance. Brought in line: Settings now shows an explicit "Couldn't check your public notes" notice with a Retry button in the same failure case.
- **Also verified clean:** account-deletion/purge interaction with the digest recipient query, Item 1 × Items 2+3 interaction (zero shared surface), scheduler collision risk (daily/weekly/monthly crons don't overlap, distinct cooldown types), and the entire git/branch state from the earlier tracking-misconfiguration incident (all 6 checks passed — `main` still exactly where the "leave it" decision left it, release branch is a clean linear history, sign-off merge will be conflict-free).

### Context

Verified while scoping: this feature has almost no infrastructure gap. `NoteEntity` already carries `copiedFromNoteId`/`copiedFromUserId`/`copiedFromPublic` (currently lines 64/67/73) recording exactly which public note a copy came from, and `QuickReviewSessionEntity` already carries `noteId` directly (currently line 36) — so "did a copy of this note lead to a real study session" is a plain join across two already-managed entities, the same unmapped-join pattern `AnalyticsEventRepository.countPublicNoteEventsByTypeAndNoteIds` (currently lines 280-292) already uses between `AnalyticsEventEntity` and `NoteEntity`. No new tables. Confirmed only one attribution path exists to reason about: every "quiz yourself on this note" CTA on a public note (`PublicSeoCopyCta`, `frontend/components/notes/public-seo-copy-cta.tsx`, `action="quickReview"`) calls `copyNote(...)` first and redirects to the copy — there is no code path that starts a `quick_review_sessions` row against someone else's original note directly. Also confirmed: v0.60.0's Official Challenge Quiz template pooling only ever seeds from **official**-authored notes (`OfficialChallengeQuizTemplateService.isEligibleOfficialTemplate`, currently line 256) — community-authored notes are never eligible source material for pooling, so a community creator's downstream sessions are never laundered through the shared template bank and stay fully traceable back through the copy chain.

### Item 1 — Official-author predicate fix

**Classification: small, direct implementation (Claude Code), no Codex prompt.**

Confirmed **three**, not two, independent copies of "is this user an official author," and they disagree:
- `PublicProfileService.isOfficialAuthor(UserEntity)` (currently lines 196-198) — the reference form: `role == ADMIN OR email == OFFICIAL_AUTHOR_EMAIL` (constant at line 40, `einar.lagera@gmail.com`).
- `PublicLibraryRepositoryImpl.officialAuthorPredicate()` (currently lines 320-329) — SQL, checks `role = 'ADMIN'` only. Feeds the Public Library's OFFICIAL/COMMUNITY source filter (`appendSourceFilter`, currently lines 284-318) — **user-visible**, not internal-only.
- `OfficialChallengeQuizTemplateService.isOfficialAuthor(UUID)` (currently lines 267-271) — Java, but also checks `role == ADMIN` only, matching the SQL form rather than the reference form. Gates `isEligibleOfficialTemplate` (line 256) and `resolveTemplate` (line 108) — i.e. **whether a note is eligible to seed the shared Official Challenge Quiz template pool at all.**

Fix: bring both the SQL and the template-service checks in line with `PublicProfileService`'s reference form (role OR official email), not just the SQL one the original gate-query note called out.
- `PublicLibraryRepositoryImpl.officialAuthorPredicate()`: add the email check to the SQL, bound as a parameter (`:officialAuthorEmail`) rather than a literal, consistent with how `:deletedUserId` is already conditionally bound in `appendSourceFilter` (currently lines 296-298).
- `OfficialChallengeQuizTemplateService.isOfficialAuthor(UUID)`: add `|| OFFICIAL_AUTHOR_EMAIL.equalsIgnoreCase(user.getEmail())` to the existing role check.
- Do not fully consolidate the three copies into one shared source of truth in this pass — flag it as a follow-up hardening item (a fourth copy is exactly how this drifted in the first place), but keep this fix minimal and reviewable.

**Blast radius — verify before shipping, not just fixing the code:** if the owner's own account (`einar.lagera@gmail.com`) does not currently have `role = ADMIN` in production, this fix is a live behavior change, not just future-proofing: (a) the owner's personally-authored public notes reclassify from COMMUNITY to OFFICIAL in the Public Library filter — changes what a COMMUNITY-source-filtered visitor sees today; (b) those same notes become newly eligible for Official Challenge Quiz template seeding, which may need the same kind of manual backfill trigger v0.60.0 needed for already-published Official content. **Specifically worth checking, not just asserting:** the 2026-07-28 gate query (`14-knowledge-impact-gate-check.sql`, which already used the wider Java-form definition) found only 4 community notes total — if today's narrower SQL predicate is why some of those 4 are misclassified as community, the Public Library's COMMUNITY filter could return a visibly smaller result set (in the limit, empty) once fixed. That's a user-visible before/after the owner should see and decide on, not something to silently let Codex change. Check the account's actual `role` in production before implementation — this determines whether the fix is a no-op today (already `ADMIN`) or an active reclassification (needs the RELEASES.md bullet and a note in `docs/features/public-library.md`, plus a decision on backfill).

Files: `backend/src/main/java/com/studysnap/backend/repository/PublicLibraryRepositoryImpl.java`, `backend/src/main/java/com/studysnap/backend/service/OfficialChallengeQuizTemplateService.java`.

Sequencing: land first — Item 2's "who counts as a creator" framing and Item 1's fix should agree from the start, rather than shipping the dashboard against the still-inconsistent predicate and re-deriving numbers later.

### Item 2 — Knowledge Impact dashboard (backend + frontend)

**Classification: medium — write a Codex prompt.** New endpoint, new service, new frontend surface plus an entry point that doesn't exist today.

**Surface decision, made during scoping:** do **not** extend `PublicProfileResponse`/`PublicProfileController` (currently `@RequestMapping("/public")`, `permitAll()` at `SecurityConfig.java` line 49, servable to anonymous visitors). Owner-private aggregates have no business living in a response shape that's cacheable/servable without auth — one caching mistake away from leaking. Instead: a new controller outside `/public/**`, which `SecurityConfig`'s existing `anyRequest().authenticated()` (line 57) protects by default with zero new security config.
- New `CreatorImpactController` (`@RequestMapping("/creator-impact")`), single endpoint `GET /creator-impact/me`, resolves the creator from `@AuthenticationPrincipal` only — no path parameter, cannot be pointed at another user's id.
- New `CreatorImpactService`, separate from `PublicProfileService` to keep the privacy boundary structural, not just conventional.
- New repository query (`NoteRepository`, alongside the existing `countCopiedPublicNotesBySourceNoteIds` at line 211): per source note, `count(distinct copy.ownerUserId)` where a copy (`copiedFromNoteId = sourceNote.id and copiedFromPublic = true`) has at least one `QuickReviewSessionEntity` row with `noteId = copy.id and completedAt is not null` — an unmapped join across `NoteEntity` and `QuickReviewSessionEntity`, same pattern already in use elsewhere in this codebase. A second, creator-scoped query (not summed from the per-note one) gives the true headline "N distinct learners helped" across all of a creator's notes — state explicitly in the UI copy that the per-note breakdown can sum higher than the headline (a learner who copied two of the same creator's notes), so it doesn't read as a bug the first time someone notices.
- Response: headline distinct-learners-helped count, plus a per-note breakdown (title, distinct learners, with raw views/copies shown but visually de-emphasized per the design brief's "weight downstream signal over raw counts").
- **Decision, stated explicitly for Codex, corrected during scoping review:** "helped" requires `completedAt is not null` on the downstream session, not merely a row existing. Verified why the weaker bar fails: `QuickReviewSessionService.startSession` (currently lines 72-113) writes the `quick_review_sessions` row immediately at session start, before any question is answered, and the "quiz yourself" CTA (`PublicSeoCopyCta`) copies the note and redirects straight into that same start flow — so "a session row exists" is nearly equivalent to "clicked the CTA," which collapses the metric back into a copy count with extra steps and defeats the brief's "weight downstream signal over raw counts" requirement. Requiring completion is the honest bar for "helped."

**Entry point — verified there isn't one today, this is in scope, not optional:** `buildPublicProfilePath` (`frontend/lib/public-note-path.ts` line 49) and the `/public/profile/[userId]` page exist, and the page already gates owner-only affordances behind `isOwner` (`public-profile-page-client.tsx` line 108, e.g. the public-profile-visibility toggle at line 476). But grepping the whole frontend for a link to a user's *own* profile from any authenticated surface (`/profile`, `/settings`, dashboard) turns up nothing — today a creator can only reach this page by already knowing their own `userId`/username. Add a discoverable "View your public profile" (or equivalent) link from the existing `/profile` account page (`frontend/app/profile/page.tsx`) gated on `publicNotesCount > 0` or `publicProfileVisible`; exact placement/copy is an implementation decision, not a scoping one. The "Your Impact" section itself renders on the existing `/public/profile/[userId]` page, gated by the existing `isOwner` boolean, below the current public stats block (which stays as-is, unchanged, still visible to all visitors).
- Fire a new `KNOWLEDGE_IMPACT_DASHBOARD_VIEWED` event via the existing `AnalyticsPageViewTracker` (`frontend/components/analytics/page-view-tracker.tsx`) when the owner-only Impact section mounts — same pattern as `PUBLISHED_PLANS_VIEWED`. Not fired for non-owner visitors to the same page.

Files: new `CreatorImpactController.java`, `CreatorImpactService.java`, `CreatorImpactResponse.java` DTO, a new `NoteLearnersHelpedProjection` (mirroring `NoteCopyCountProjection`); `NoteRepository.java` (new query); `AnalyticsEventType.java` (new value); `frontend/lib/api.ts` (new `getCreatorImpact()` call + type), `frontend/components/public/public-profile-page-client.tsx` (new owner-gated section), `frontend/app/profile/page.tsx` (new entry-point link).

### Item 3 — Conditional-rate analytics event

**Classification: small, direct implementation (Claude Code) — but land in the same PR as Item 2, not separately, since the dashboard-view event is what this item measures.**

Two new `AnalyticsEventType` values needed — neither exists today:
- `KNOWLEDGE_IMPACT_DASHBOARD_VIEWED` — added as part of Item 2 above.
- `PUBLIC_NOTE_PUBLISHED` — does not exist in any form today. `NoteService.updateVisibility` (currently lines 376-388) sets `NoteVisibility` on a note and saves it, but tracks no analytics event at all. Fix: capture the note's visibility before the mutation, and after saving, call `analyticsService.trackEvent(ownerUserId, AnalyticsEventType.PUBLIC_NOTE_PUBLISHED, noteId, metadata)` (same `AnalyticsService.trackEvent` signature already used at `NoteService.java` line 296 for `PUBLIC_NOTE_COPIED`) **only when** the prior visibility was not `PUBLIC` and the new one is — a re-save of an already-public note must not look like a fresh "publish" for rate purposes. This is a generically useful hygiene event beyond Knowledge Impact (the product currently has no record of when a note became public, only `updatedAt`, which any unrelated edit also touches) — not namespaced to this feature.

Both additive `enum` values, no migration — `analytics_events.event_type` is a plain `VARCHAR(64)` with no check constraint (`V25__analytics_events.sql`), confirmed by reading the migration directly.

**The actual checkpoint query, written now** (per this doc's own gate-types rule that a checkpoint must be operable at its stated date, not designed later):

```sql
-- Conditional publish-after-view rate. Only counts a view whose 30-day window has
-- already closed (created_at <= now() - 30 days) so an in-flight view isn't scored
-- as a non-republish. Safe to run any time on/after 2026-08-31 for full coverage,
-- and again at the 2026-09-11 checkpoint itself.
select
  count(distinct viewed.user_id)                                            as creators_who_viewed,
  count(distinct case when republished.user_id is not null
                       then viewed.user_id end)                             as creators_who_republished
from (
  select user_id, min(created_at) as first_viewed_at
  from analytics_events
  where event_type = 'KNOWLEDGE_IMPACT_DASHBOARD_VIEWED'
    and created_at <= now() - interval '30 days'
  group by user_id
) viewed
left join analytics_events republished
  on republished.user_id = viewed.user_id
 and republished.event_type = 'PUBLIC_NOTE_PUBLISHED'
 and republished.created_at > viewed.first_viewed_at
 and republished.created_at <= viewed.first_viewed_at + interval '30 days';
```

N = 30 days, chosen so the window reliably closes before the 2026-09-11 checkpoint even for a view that happens shortly after this release ships (late July/early August + 30 days closes by early September). **Honest limit, stated up front so it doesn't get argued in September:** on a 3-creator base, this rate's denominator (`creators_who_viewed`) is likely to be single-digit or zero for a long time — this event pair explains *mechanism* (did anyone even look), it does not replace the checkpoint's actual kill criterion, which stays the raw community-publish-rate trend already on record in the Backlog Index (1 → 3 → 0, May/June/July). Both numbers should be reported together at the checkpoint, not this rate alone.

Files: `AnalyticsEventType.java`, `NoteService.java` (`updateVisibility`), `frontend/lib/api.ts` (the `AnalyticsEventType` union, currently starting line 478, must gain both new values or `trackAnalyticsEvent` calls referencing them won't type-check).

### Item 4 — Optional impact digest (backend + frontend)

**Scoping recommended deferring this item — real surface area (a migration, a new preference field, a new scheduled job) to reach an audience of 3 people with no read on it yet, while the ratification's non-negotiable items are only the dashboard and the conditional-rate event.** The owner reviewed that tradeoff during scoping and chose to include it in this release anyway — scoped in full below, not treated as a lesser afterthought because of the recommendation.

**Classification: small-to-medium — write a Codex prompt.** Touches a migration, an entity, two DTOs, a service, a new scheduled job, and a frontend preferences toggle — past the isolated 1-3-file bar.

Design, reusing the existing Email Preferences category system exactly as it works today, never a new notification channel:
- New nullable-at-the-column-level, opt-in (`DEFAULT false`) `knowledge_impact_digest_reminders_enabled` boolean column via a new migration `V99__add_knowledge_impact_digest_preference.sql` (next available after `V98__add_llm_usage_to_quiz_sessions.sql`). Default **false**, never default-on — this doc's own standing lesson from the due-concepts digest.
- `UserEntity`: new `Boolean knowledgeImpactDigestRemindersEnabled` field, alongside the existing four reminder booleans (currently lines 106-115).
- `UpdateEmailPreferencesRequest` and `MeResponse` (which already carry the sibling `weeklySummaryRemindersEnabled`/`dueConceptsDigestRemindersEnabled` fields) both gain the new field; `AuthService.updateEmailPreferences` (currently lines 435-441) sets it alongside the existing four.
- Frontend: the existing email-preferences panel (wherever the four current reminder toggles render) gains a fifth toggle, shown only for an account with ≥1 public note — surfacing an opt-in toggle for a digest that account can never receive anything meaningful from is confusing, not opt-in-friendly.
- New scheduled dispatch, modeled directly on `RetentionEmailScheduler`/`RetentionService`'s existing weekly path (`RetentionEmailScheduler.runWeekly`, currently lines 29-34; `RetentionService.sendWeeklySummaryEmails`/`dispatchWeeklySummaryEmails`, currently lines 178/410): **monthly**, not weekly — the design brief allows either, and monthly gives more signal per email at a 3-creator scale, matching its own "low-frequency" instruction. New `@Scheduled` cron property (e.g. `studysnap.retention.knowledge-impact-digest-monthly-cron`), reusing `CreatorImpactService`'s aggregate query from Item 2.
- **Send-skip rule, a deliberate design decision, not an afterthought:** only send to a creator whose distinct-learners-helped count, counted over a trailing-30-day window (not cumulative-to-date), is greater than zero. Sending "0 new learners this month" repeatedly to a near-single-digit creator base is exactly the pity-metric risk the design brief names, and repeats this doc's own `v0.48.0` digest-lift mistake in spirit (a dispatch nobody wanted or read). No new state/event needed for this — a windowed variant of Item 2's own query, not a persisted "last sent" timestamp.

Files: new migration, `UserEntity.java`, `UpdateEmailPreferencesRequest.java`, `MeResponse.java`, `AuthService.java` (`updateEmailPreferences`), a new scheduled job class (or an addition to `RetentionEmailScheduler`/`RetentionService`), the frontend email-preferences component, `application.yaml` (new cron property).

Sequencing: depends on Item 2's `CreatorImpactService` query existing first; land after Items 1-3.

### Overall Sequencing

Item 1 first (predicate fix). Item 2 and Item 3 together in one Codex prompt/PR (the dashboard's view event and the publish event are one measurable unit — shipping one without the other repeats the exact unmeasurable-checkpoint mistake this doc's gate-types section already named). Item 4 last, as its own Codex prompt/PR, since its extra migration/scheduling surface is unrelated to the checkpoint mechanics of Items 2-3 and shouldn't complicate that audit.

### Verification

- **Item 1:** confirm the production `einar.lagera@gmail.com` account's actual `role` before/after; confirm Public Library COMMUNITY-filtered results and Official Challenge Quiz template eligibility change only for that account's notes, nothing else.
- **Item 2:** as a creator with ≥1 public note and ≥1 downstream copy with a *completed* session, confirm the dashboard shows a distinct-learner count that matches a manual query; separately confirm a copy whose session was only started (never completed) does **not** count, to guard against the metric silently regressing to a copy count; confirm a visitor other than the owner never sees the section and `GET /creator-impact/me` 401s when unauthenticated; confirm the new entry-point link only appears when the account has public notes.
- **Item 3:** confirm `PUBLIC_NOTE_PUBLISHED` fires once on a private→public transition and not on a public→public re-save; confirm `KNOWLEDGE_IMPACT_DASHBOARD_VIEWED` fires once per owner page load of the Impact section; run the checkpoint query above against a hand-seeded local case to confirm it returns the expected 0/1 shape before relying on it in September.
- **Item 4:** confirm the toggle defaults to off for existing and new accounts; confirm a creator with zero new completed downstream sessions in the trailing 30 days receives no email that cycle; confirm a creator with ≥1 new one does, with a count matching a manual query; confirm the toggle is hidden/inert for an account with zero public notes.
- Full regression before PR handoff for commit: `./mvnw clean install` (backend) and `npm test` + `tsc --noEmit` (frontend), per this project's standing build-before-commit rule.

## v0.61.0 — Challenge Quiz Quota Increase (Released)

**Scoped 2026-07-28.** Two items ratified by the owner the same day, overriding this doc's own prior data/instrumentation gates for the same underlying proposal — see the "Challenge Quiz daily quota... price decrease" Backlog Index row for the full ratification reasoning and history, including why a genuinely-daily model was rejected in favor of a bigger monthly ceiling. **Reclaims the `v0.61.0` slot from `Explore Convergence`** — this item's gate (owner ratification) cleared 2026-07-28, while Explore's gate (the Diagnostic Read showing a discovery problem) is still unmet, following this doc's own established precedent for reclaiming a minor-version slot when a different item's gate clears first (`v0.58.0`, `v0.59.0`, and `v0.60.0` each changed hands the same way). Explore Convergence renumbered to `v0.62.0` at the time, now `v0.63.0` — see the Current Release Baseline note and Phase 2 section below.

**Implemented 2026-07-28.** Both items are complete in the release worktree: backend/frontend quota truth is aligned at FREE 20 / PLUS 100 / PRO 200, and Challenge Quiz real-LLM shortfalls now persist cumulative model/token usage on `quick_review_sessions` while pooled-only sessions remain null. Release signoff remains pending.

### Context

Two items:
1. **Monthly quota increase.** Challenge Quiz's monthly limits (FREE 5, PLUS 25, PRO 50) go to FREE 20, PLUS 100, PRO 200 — a uniform 4x across all three tiers, keeping today's 1:5:10 tier ratio intact. Owner's philosophy, stated explicitly: a paying learner should feel "I can just study" instead of tracking a quota, and real study behavior is bursty (exam-week crunch), not evenly spread across a month — a monthly ceiling respects that shape better than a daily one would. Reuses the existing rolling-billing-period `UserUsageEntity` tracking exactly as it works today; no new infrastructure, no migration for this item.
2. **Challenge Quiz LLM token/cost telemetry.** Folded into this release's scope, not treated as a precondition or a separate gate — raising the ceiling this much with zero cost visibility today would mean a real cost blowup is only visible after the invoice. This makes this release's own effect on spend observable rather than blind, without blocking the quota change on building it first.

Pricing (the other half of the original held Backlog Index item) is explicitly deferred, not part of this release — the owner wants to observe paywall conversion, onboarding retention, and usage after the quota increase lands before revisiting price, rather than move both levers at once and lose the ability to attribute effect to either one.

### Item 1 — Monthly quota increase (FREE 20 / PLUS 100 / PRO 200)

**Classification: small, direct implementation (Claude Code, no Codex prompt needed) in isolation** — but see Overall Sequencing below for why it's proposed to ride along in the same Codex prompt as Item 2 instead.

Two sources of truth carry these numbers today, confirmed by direct file read, and both must move together or the marketing copy and the actually-enforced limit will disagree:
- **Backend enforcement:** `backend/src/main/resources/application.yaml` (currently lines 103-105) — `free-monthly-challenge-quiz-limit: ${FREE_MONTHLY_CHALLENGE_QUIZ_LIMIT:5}`, `plus-monthly-challenge-quiz-limit: ${PLUS_MONTHLY_CHALLENGE_QUIZ_LIMIT:25}`, `pro-monthly-challenge-quiz-limit: ${PRO_MONTHLY_CHALLENGE_QUIZ_LIMIT:${PREMIUM_MONTHLY_CHALLENGE_QUIZ_LIMIT:50}}`, read via `StudySnapProperties.resolveMonthlyChallengeQuizLimit` (currently line 150). This is what `ChallengeQuizService.assertChallengeQuizQuotaAvailable` actually enforces.
- **Frontend marketing/display copy:** `frontend/lib/pricing-config.ts`'s `pricingConfig` object (currently lines 9, 19, 29 — `challengeQuizzesPerMonth: 5/25/50` for free/plus/pro) — a **separate, independently-maintained static constant, not derived from the backend config at all.** Confirmed consumers of this same shared object: `frontend/src/config/plans.ts` (pricing-card copy, e.g. "Practice more often with 25 quizzes each month"), `frontend/app/settings/page.tsx`, `frontend/app/dashboard/dashboard-monthly-usage-card.tsx`, `frontend/components/notes/private-note-detail-page-client.tsx`, `frontend/components/notes/generated-quiz-preview-page-client.tsx` — one edit to `pricing-config.ts` propagates to all of them since they all read the same object.

Fix:
- Update the three env-var default values in `application.yaml` (lines 103-105) from 5/25/50 to 20/100/200. These stay env-var overridable in prod without a redeploy if the numbers ever need a fast adjustment.
- Update `frontend/lib/pricing-config.ts`'s `challengeQuizzesPerMonth` values (lines 9, 19, 29) to match exactly.
- Do not touch any other quota field in either file (study packs, adaptive practice, exports, etc.) — this item is Challenge Quiz only.

Files: `backend/src/main/resources/application.yaml`, `frontend/lib/pricing-config.ts`; update `frontend/app/pricing/page.test.tsx` (currently asserts "5 Quizzes / month", "25 quizzes each month", "50 quizzes each month" — lines 85-87) and any other test asserting the old numbers.

Sequencing: independent of Item 2, land in either order.

### Item 2 — Challenge Quiz LLM token/cost telemetry

**Classification: small-to-medium — write a Codex prompt.** Touches an interface, two implementations, a migration, and service-layer wiring — past the isolated 1-3-file bar even though the change itself is narrow and low-risk.

Confirmed mechanism: `OpenAiLlmStudyPackService` already extracts real per-call token usage for Study Pack generation via `extractUsageMetadata(responseJson, fallbackModel)` (currently line 562), returning a `UsageMetadata(modelUsed, inputTokens, outputTokens, ...)` record (currently line 2602), persisted onto `StudyPackEntity`'s existing `modelUsed`/`inputTokens`/`outputTokens`/`cachedInputTokens` columns (currently lines 72-81). Challenge Quiz's generation path never calls this helper: `generateChallengeQuiz`/`generateMoreChallengeQuiz` (currently lines 1692, 1721) funnel through the shared private `generateQuizWithSchemaOnce` (currently line 2063), which reads `response.payload()` off the returned `JsonSchemaResponse<PromptGeneratedQuiz>` (currently line 2596 — `record JsonSchemaResponse<T>(T payload, JsonNode responseJson)`) but never touches `response.responseJson()` — the raw data `extractUsageMetadata` needs is already sitting right there, just discarded. `ChallengeQuizService` calls this LLM path from two sites: `startSession`'s bank/template shortfall generation (currently line 302) and `generateMoreQuestions`'s "+5" shortfall generation (currently line 679) — both are real LLM calls only when the bank/template claim doesn't fully cover the requested count; a fully bank-sourced session or batch makes no LLM call at all and should record no usage for that call.

Fix:
- Extend `generateQuizWithSchemaOnce` (or a thin wrapper around it) to also call the existing `extractUsageMetadata(response.responseJson(), model)` and return it alongside the quiz items — a return-shape change on `LlmStudyPackService.generateChallengeQuiz`/`generateMoreChallengeQuiz` (interface), both implementations (`OpenAiLlmStudyPackService`, `StubLlmStudyPackService`), and `QuizGenerationService`'s wrapping methods. The stub/mock path (`MockQuizGenerationUtils`, used by `StubLlmStudyPackService`) never calls the LLM, so it returns null/zero usage — nothing to report.
- Add nullable `model_used` (varchar), `input_tokens`, `output_tokens`, `cached_input_tokens` (integer) columns to `quick_review_sessions` via a new Flyway migration (`V98__`, next available after `V97__add_quota_exempt_to_quiz_sessions.sql`) — mirroring `StudyPackEntity`'s existing column shape exactly, not inventing a new one.
- In `ChallengeQuizService`, accumulate these onto the session at both call sites: `startSession`'s initial shortfall (currently ~line 302, before `markSessionReady`) sets the session's usage columns for the first time; `generateMoreQuestions`'s "+5" shortfall (currently ~line 679) **adds to the existing values rather than overwriting them**, so the columns represent the session's cumulative real-LLM cost across its whole lifetime, not just the initial call. A session that never needs the LLM (fully bank/template-sourced, at start and on every "+5") keeps these columns null — that itself is a meaningful signal (pooling is working), not a gap to backfill.
- Board Exam Mode, Long Exam, and Adaptive Practice are explicitly out of scope — this is Challenge Quiz only, matching the original held-item row's "at minimum for Challenge Quiz" framing. Do not extend to other modes in this pass.
- This is read-only telemetry, not a gate or a limiter — nothing about session start/completion/quota enforcement changes. A failure to extract usage (e.g. an unexpected response shape) must not block or fail the generation itself — only skip recording usage for that call.

Files: `backend/src/main/java/com/studysnap/backend/service/LlmStudyPackService.java`, `backend/src/main/java/com/studysnap/backend/service/impl/OpenAiLlmStudyPackService.java`, `backend/src/main/java/com/studysnap/backend/service/impl/StubLlmStudyPackService.java`, `backend/src/main/java/com/studysnap/backend/service/QuizGenerationService.java`, `backend/src/main/java/com/studysnap/backend/service/ChallengeQuizService.java`, `backend/src/main/java/com/studysnap/backend/entity/QuickReviewSessionEntity.java`, new migration file, `ChallengeQuizServiceTest.java`.

Sequencing: independent of Item 1, land in either order.

### Overall Sequencing

Both items are independent — no required order. Item 1 is small enough to implement directly (Claude Code), but proposed here to ride along inside the same Codex prompt as Item 2 instead of shipping as a separate PR — matches this session's own established pattern for v0.60.3 (bundling several small items into one prompt/PR to run the mandatory post-delivery audit once instead of twice) and keeps the quota-number change and its own observability landing atomically, rather than a window where the ceiling is already raised but usage still isn't being captured.

### Verification

- **Item 1:** confirm a new FREE/PLUS/PRO account's `monthlyLimit` in `ChallengeQuizStartResponse` reads 20/100/200; confirm the pricing page displays the updated numbers.
- **Item 2:** start a Challenge Quiz session that requires real LLM generation (a private note with no bank/template coverage) and confirm the session's `input_tokens`/`output_tokens`/`model_used` are populated; start a session fully served from the bank/template (an Official-content study pack) and confirm these columns stay null; trigger a "+5 More" that hits the LLM and confirm the values accumulate rather than reset.
- Full regression before PR handoff for commit: `./mvnw clean install` (backend) and `npm test` + `tsc --noEmit` (frontend), per this project's standing build-before-commit rule.

## v0.60.3 — Challenge Quiz Shaping (Released, base branch `releases/v0.60.3`)

**Scoped 2026-07-27, kicked off 2026-07-28.** All four root causes below were confirmed by direct file read in the same session this was scoped — re-verify file:line anchors first if code has shifted since. Patch release off `v0.60.2`, following this project's existing patch-release convention — does not consume the `v0.61.0` minor-version slot (still reserved for Explore Convergence, still gated on the Diagnostic Read re-read). Theme broadened from pure Challenge Quiz shaping to also include Item 4 (onboarding), added the same day after a wider pricing/quota/growth discussion narrowed to two low-risk, additive pieces worth shipping now while the rest (a daily Challenge Quiz quota model, a price decrease) was explicitly held pending LLM cost instrumentation that does not exist yet — see this doc's Backlog Index row for the full held-items reasoning. **Items 1-3 are clear to branch and implement now. Item 4 stays gated at its own kickoff** (see Item 4's sequencing-risk paragraph below) — do not start Item 4's branch until the prod cohort query comes back clean.

### Context

Four items:

1. **Adaptive initial question count.** Reopens the `v0.60.0`-era "start with more than 5 questions" idea, previously rejected as a cost argument (see the Backlog Index row's superseded verdict). Reopened on a different, accepted basis: reducing "+5 Questions" mid-session click friction and improving pacing/commitment — mode-wide, not Official-content-only, which resolves the original provenance-inconsistency objection.
2. **Redo Missed Questions silently returns the wrong session.** Surfaced by direct user testing: clicking "Redo Missed Questions" returned the full original question set, not just the missed ones. Traced to the already-documented v0.58.0 known limitation ("a redo-missed request can silently resume an unrelated in-progress session… matches only on session type, not sub-mode"), deferred twice already (logged in v0.58.0, explicitly declined in v0.60.1 as "widening blast radius for uncertain benefit"). Verified via direct read that this — not a claim-query bug — is the actual mechanism (see Item 2 below).
3. **No guard against submitting with unanswered questions.** A distinct, unrelated gap raised in the same conversation: `handleSubmit` finalizes immediately on a manual Submit click with zero awareness of how many questions are still unanswered. Not a substitute for Item 2 — Redo Missed is a post-session drill loop over questions answered *wrong*; this is an in-session guard over questions left *blank*. Both are worth doing; neither replaces the other.
4. **Onboarding coverage-gap capture.** Unrelated to Challenge Quiz — raised in the same conversation as part of a broader growth discussion. When a Student's course/program has no matching Official Review Set at onboarding, that moment currently produces no signal at all; capturing it cheaply tells the product what to curate next, and doubles as a lower-risk substitute for the discussion's community-authorship growth bet (see Item 4 below for why that distinction matters).

### Item 1 — Adaptive initial question count (10/12/15) — Shipped, see `RELEASES.md`

**Classification: small, direct implementation (Claude Code, no Codex prompt needed).** Single file, ~3 line change.

`ChallengeQuizService.resolveGenerationProfile` (currently ~line 846) already computes a score-adaptive `ChallengeGenerationProfile(questionCount, difficulty)` — `LOW_SCORE_QUESTION_COUNT=10` / `MID_SCORE_QUESTION_COUNT=12` / `HIGH_SCORE_QUESTION_COUNT=15` (currently lines 137-139), based on the learner's last Quick Review score, same profile already used for Challenge's post-v0.60.1 score-based difficulty fallback. Confirmed via grep: `.questionCount()` is called **nowhere** in this file — only `.difficulty()` is ever read from the returned profile. Every Challenge session start hardcodes `INITIAL_CHALLENGE_QUIZ_COUNT=5` instead (currently line 204, the non-Board-Exam branch of `startSession`'s `quizCount` ternary).

Fix:
- In `startSession`'s `quizCount` ternary (currently lines 202-204), replace the flat `INITIAL_CHALLENGE_QUIZ_COUNT` fallback with `profile.questionCount()` for the Challenge (non-Board-Exam) branch only.
- **Do not touch the Board Exam branch** (`resolveBoardExamQuestionCount(boardExamSourceCount)`, same ternary) — Board Exam Mode stays exactly as it is today, fixed count, no adaptive behavior. This is an explicit exclusion, not an oversight — say so in the PR description.
- **`startRedoMissedSession`'s own count stays fixed, not adaptive.** It independently references `INITIAL_CHALLENGE_QUIZ_COUNT` (currently line 406) as the `count` argument to `claimIncorrectQuestions` — this is a separate call site, unaffected by the `quizCount` change above unless explicitly touched. Recommend leaving it fixed: Redo Missed is a smaller, targeted catch-up loop, not a full new session, and scaling it up would work against its own purpose. Confirm this reasoning still holds at implementation time rather than assuming symmetry with Item 1.
- `MAX_CHALLENGE_QUIZ_QUESTIONS=20` and `GENERATE_MORE_BATCH_SIZE=5` are unchanged — `generateMoreQuestions`'s existing `Math.min(GENERATE_MORE_BATCH_SIZE, MAX_CHALLENGE_QUIZ_QUESTIONS - existingQuiz.size())` clamp already handles a shorter final "+5" batch regardless of starting count, so a session starting at 15 still gets exactly one clamped batch to reach 20, no extra logic needed.

Files: `backend/src/main/java/com/studysnap/backend/service/ChallengeQuizService.java`, `ChallengeQuizServiceTest.java` (test: a session starts with 10/12/15 questions matching the resolved profile instead of a flat 5, for each score band; a regression test that Board Exam's question count is unaffected; a regression test that `startRedoMissedSession` still requests a fixed count).

Sequencing: fully independent, land anytime.

### Item 2 — Redo Missed Questions returns the wrong session — Shipped, see `RELEASES.md`

**Classification: small-to-medium, direct implementation (Claude Code, no Codex prompt needed) — same flavor as v0.60.2 Item 2's lock-recheck fix.** Confined to `ChallengeQuizService.java` + its test.

Confirmed mechanism: `startRedoMissedSession` (currently lines 377-414) calls the exact same `resolveExistingChallengeSession(userId, studyPackId, studyPack, planType)` (currently line 382) that `startSession` uses — this resolver matches on `userId + studyPackId + sessionMode=CHALLENGE` only, with no notion of *which* sub-flow the caller wants. Any lingering `GENERATING`/`IN_PROGRESS` Challenge session on that study pack — an ordinary session, not a redo — gets returned verbatim, full original question set included, before the method ever reaches `claimIncorrectQuestions` (currently line 401). This reproduces the reported bug exactly whenever a stale ordinary session exists on the same study pack. Independently verified this is the actual and only mechanism: `ChallengeQuizQuestionBankRepository.findIncorrectClaimableForUpdate` (currently lines 30-46) correctly filters `question.lastKnownOutcome = :outcome and question.claimedSessionId is null` — the claim query itself is clean, so a wrong-question-set result can only come from the session-resolution layer returning the wrong session, not from the claim logic returning the wrong questions.

**Design (revised after second-opinion review):** the original draft proposed reusing `session.setQuotaExempt(true)` (currently line 411, set in exactly one place in this file — `startRedoMissedSession` itself) as the disambiguating signal. A second-opinion review flagged this as conflating two unrelated concerns — billing exemption and session provenance — under a field name that already reads as "billing consequence," not "which sub-flow created this session." Confirmed on inspection: the field genuinely is named for its billing effect, not its origin, so the concern holds; the original draft optimized for "no schema change" without stress-testing the semantics hard enough.

Fix instead: add a dedicated `sessionState` JSONB marker mirroring the existing `QuizSessionStateUtils.withPoolSourced` pattern (already used for Board Exam's pool-sourcing flag in this file and in `LongExamService.java`) — e.g. `QuizSessionStateUtils.withRedoMissedSource(sessionState, true)`, read via a paired `isRedoMissedSource(sessionState)` accessor. This keeps the "no new column" property the original draft was after, without the semantic conflation.

**`quotaExempt` is not replaced — it stays, and `startRedoMissedSession` sets both fields.** They serve two different consumers reading two different facts, and conflating them back together would reintroduce the same bug in a new shape:
- `quotaExempt` (currently line 411) → read by `countChallengeQuizUsedThisMonth` to exclude the session from the monthly quota count. A billing decision, untouched by this fix.
- The new `isRedoMissedSource` sessionState marker → read only by `resolveExistingChallengeSession`'s new provenance check below, to decide whether a found in-progress session is itself a prior redo-missed session. A session-identity decision, unrelated to billing.

**An implementer must set both fields, not swap one for the other.** If the marker replaces `quotaExempt` instead of joining it, redo sessions silently start counting against the monthly quota — a regression, not a refactor. Say this explicitly in the PR description, not just in code comments.

Fix:
- Give `resolveExistingChallengeSession` a way to require the candidate carry the `isRedoMissedSource` marker (e.g. an added boolean parameter, or a second private variant) — `startSession` passes/behaves as today (no requirement), `startRedoMissedSession` passes the new requirement.
- If `startRedoMissedSession` finds a candidate that is `IN_PROGRESS`/`GENERATING` but does **not** carry the marker (i.e. a stale ordinary session, not a stale redo session): do not return it. Since clicking "Redo Missed Questions" is itself a deliberate, explicit user action — unlike the generic entry-point ambiguity v0.60.1's Resume/Start Fresh prompt exists for — there's no ambiguity to resolve with a prompt here. Forfeit the stale ordinary session (mirror the existing `markSessionForfeited` + save + claims-release pattern already used elsewhere in this class) and proceed to start the fresh redo-missed session, rather than silently resuming it or running two `IN_PROGRESS` rows for the same user+study pack+mode side by side (which would corrupt `findTopByUserIdAndStudyPackIdAndSessionModeAndStatusInOrderByCreatedAtDesc`'s "top" semantics for later lookups).
- If the candidate **does** carry the marker (a genuinely stale prior redo-missed session), keep today's behavior — resume it, matching how any other in-progress session is treated.
- Do not touch `startSession`'s behavior or its call to `resolveExistingChallengeSession` — this fix is scoped to the redo-missed entry point only.

**Backfill note — state this consequence up front, don't let it surface as a surprise during audit:** sessions created before this ships have no `isRedoMissedSource` marker in their `sessionState`. If a pre-existing, still-in-progress redo-missed session is encountered after this ships, it reads as an ordinary session and gets forfeited rather than resumed on the next Redo Missed click. Accepted: one-time, low-volume (only affects sessions mid-flight at deploy time), and self-healing — the forfeited session is immediately replaced by a fresh, correctly-scoped one, no data loss.

Files: `backend/src/main/java/com/studysnap/backend/service/ChallengeQuizService.java`, `ChallengeQuizServiceTest.java` (test: a stale ordinary session is forfeited, not resumed, when `startRedoMissedSession` is called, and the fresh redo session starts correctly with only missed questions; a stale prior redo session — carrying the marker — is still resumed as today; `startSession`'s own resumption behavior is unchanged — regression guard; a new redo-missed session has both `quotaExempt=true` and the `isRedoMissedSource` marker set, and still excludes from `countChallengeQuizUsedThisMonth` — regression guard against the swap-not-add failure mode above; abandon a normal session → click Redo Missed → verify a *new* redo session starts, not the stale one, per the second-opinion review's suggested regression scenario).

Sequencing: independent of Item 1. Touches the same file as Item 1 but a different method — land in either order.

### Item 3 — Incomplete-submission guard on manual Submit — Shipped, see `RELEASES.md`

**Classification: small, direct implementation (Claude Code, no Codex prompt needed).** Frontend-only, one file.

Confirmed: `handleSubmit` (currently `frontend/app/study-packs/[id]/challenge-quiz/page.tsx` line 1063) calls `finalizeChallengeSession` immediately with no unanswered-question check, for both the manual Submit buttons (currently lines 2232, 2253) and the timeout-triggered auto-submit path. The result screen already computes and displays an "unanswered questions counted as incomplete" distinction (currently ~line 2498, `Overall Completion Score`), so the underlying answered-vs-unanswered signal already exists in the data model — this is a presentation/flow gap, not a missing data source.

Fix:
- On a **manual** Submit click only (not on `timeoutTriggered=true` — a timeout should still auto-submit exactly as today; that behavior is intentional and out of scope), check whether any question is currently unanswered.
- If none are unanswered: submit immediately, unchanged from today.
- If some are unanswered: show a confirmation before finalizing — "You have N unanswered question(s)" with a choice to go back (dismiss the prompt and return to the first unanswered question) or submit anyway. Reuse this page's existing modal patterns rather than a native `confirm()` — `AppModal` is already imported and used here, and `useQuizSessionGuard`'s existing `requestLeave`/`LeaveQuizModal` pair is a directly analogous "guard before a risky action" pattern already proven on this same page; prefer extending that shape over inventing a new one.

Files: `frontend/app/study-packs/[id]/challenge-quiz/page.tsx`, its test file (test: manual submit with all questions answered proceeds unchanged; manual submit with unanswered questions shows the guard and blocks immediate finalization; "submit anyway" finalizes with the unanswered questions counted as incomplete, matching existing scoring behavior; timeout-triggered auto-submit is completely unaffected by this change).

Sequencing: fully independent of Items 1 and 2, different file entirely.

### Item 4 — Onboarding coverage-gap capture

**Deferred out of v0.60.3 (2026-07-28) — design below is unchanged and still valid, it just didn't ship in this release.** The gate query (see "Gate query run 2026-07-28" note further down this section) found real `STUDENT`-profile presence in the signup-surge cohort, failing the effectively-zero bar required to proceed while the Diagnostic Read is mid-measurement. v0.60.3 shipped its other three items and was signed off without this one; this item now tracks as its own Backlog Index row, gated on the Diagnostic Read closing (~2026-08-06) and a clean re-run of the gate query — see that row for the current status, don't treat this section as describing shipped behavior.

**Classification: small-to-medium, direct implementation (Claude Code, no Codex prompt needed).** Frontend-only for the check/capture UI, reuses the existing feedback backend pipeline as-is (no new endpoint, table, or migration).

Confirmed mechanism: `frontend/app/onboarding/page.tsx`'s `handleContinueFromStepTwo` (currently lines 713-739) is the only place the practice-first coverage check runs. Its very first branch — `if (profileType !== "BOARD_EXAM") { goToStep(3); return; }` (currently line 718) — means `STUDENT`, `TEACHER`, and `PROFESSIONAL` never call `listCourseProgramStudyPlans` at all; they always land on the unchanged Step 3 Input Method choice with zero awareness of whether matching content exists. For `BOARD_EXAM`, the check runs, and on a **miss** (`matchingPlan` null, or `itemCount`/`readyCount` not both positive, currently lines 726-732) it silently falls through to normal Step 3 too — a real gap is discovered and then thrown away, for every profile type, today.

**Explicit scope decision — read this before implementing:** this item adds coverage-gap *capture* for `STUDENT` specifically. It does **not** extend `BOARD_EXAM`'s practice-first *adoption* behavior (skip note-authoring, land directly on an adopted Review Set) to `STUDENT` — that would be a materially bigger change to the dominant profile type's onboarding funnel, decided separately if ever, not bundled in here. `TEACHER` and `PROFESSIONAL` are out of scope too; only what was actually asked for.

Fix:
- Add a `STUDENT`-scoped variant of the existing coverage check (reuse `listCourseProgramStudyPlans`, the same call `BOARD_EXAM` already makes) inside `handleContinueFromStepTwo`, run alongside (not instead of) the existing `BOARD_EXAM` branch. On a miss — no qualifying plan for the learner's `courseProgram` — show a small, dismissible, non-blocking capture prompt before continuing to Step 3: "We don't have content for {courseProgram} yet — tell us what you need" with a free-text field and a Skip option. On a match, or after skip/submit, continue to the unchanged Step 3 exactly as today — this item never blocks reaching a first Study Pack, matching this doc's own stated onboarding philosophy ("get the user to a real first Study Pack... not collect every preference up front").
- Submit through the existing `POST /feedback` pipeline (`FeedbackEntity` — `userId`, `email`, `message`, `pageUrl`, `status`), tagged with a clear `source`/context value (e.g. `onboarding_coverage_gap`) for funnel attribution, matching the existing source-tagging convention documented in `docs/features/pricing.md`'s Upgrade-surface analytics section. No new table, no structured curriculum/grade-level fields — this project's own precedent (`Structured quiz feedback questions` in the Backlog Index, superseded in favor of the simpler free-text app-wide approach) argues against inventing a multi-field form here.
- Fail open on error exactly like the existing `BOARD_EXAM` check (currently lines 733-735) — a lookup failure must never block onboarding.

**Sequencing risk, not a blocker — resolve with a data check before kickoff, not a judgment call.** This touches the onboarding funnel while the Diagnostic Read re-read (due after 2026-08-06) is mid-measurement. The Diagnostic Read's own text warns against exactly this ("reorganizing navigation mid-surge would pollute the exact funnel data this read needs to stay clean"). This item is scoped to `STUDENT`, and the surge cohort the read is measuring skews Board-Exam/exam-dated — but "skews toward" is not the same as "excludes," and that gap is exactly where the risk lives.

Resolution path: run a prod query on the signup-surge cohort's `profileType` composition within the measurement window, rather than proceeding on the skew assumption. **Pass bar is strict, not a judgment call:** the cohort must show effectively zero `STUDENT`-profile accounts in the window for this item to proceed now. "Mostly Board Exam" or "skews Board Exam" is not a pass — any meaningful `STUDENT` presence means the funnel overlap the Diagnostic Read warns about is real, not hypothetical, and this item waits unconditionally until the read closes (~2026-08-06), regardless of how small that presence looks. Requires prod `DB_USER` access this assistant does not have — the query must be run and its result reported before kickoff, not inferred from prior segment-mix assumptions.

**Gate query run 2026-07-28 (owner-run, `docs/claude-prompt/company-redefinition-out/13-item4-profiletype-surge-check.sql`) — FAILS the bar.** Surge day itself (2026-07-23, n=29): `BOARD_EXAM` 17 (58.6%), no `profile_type` yet 10 (34.5%), **`STUDENT` 2 (6.9%)**. Wider open-measurement window (all signups since 2026-07-23, n=73): 53 have a `profile_type` set, 20 (27.4%) still don't. 6.9% STUDENT presence on the surge day is real, not noise, and the 27.4%-still-NULL share in the wider window means the true STUDENT share could be higher once those finish onboarding, not lower — so this doesn't round down to "effectively zero" under any reading of the stated bar. **Item 4 stays parked; do not kick off until the Diagnostic Read closes (~2026-08-06) and the re-read queries (`08-diagnostic-read-queries.sql`, Queries 2-5) actually run.** Re-run this same gate query at that point before kickoff — don't reuse this 2026-07-28 result, since the window composition will have shifted.

Files: `frontend/app/onboarding/page.tsx`, its test file (test: a `STUDENT` with no matching plan sees the capture prompt and can skip or submit, either way reaching Step 3 unchanged; a `STUDENT` with a matching plan never sees the prompt; `BOARD_EXAM`'s existing practice-first behavior is completely unaffected — regression guard; a lookup failure fails open exactly like the existing `BOARD_EXAM` catch block).

Sequencing: fully independent of Items 1-3, different part of the app entirely. Gate kickoff on the sequencing-risk confirmation above.

### Overall Sequencing

All four items are independent — no required order, can land as separate PRs. Item 4 additionally needs an explicit go/no-go on onboarding-funnel timing before kickoff (see Item 4's sequencing risk).

### Verification

- **Item 1:** start Challenge Quiz sessions across all three score bands and confirm initial question count matches 10/12/15; confirm Board Exam's count is unaffected; confirm Redo Missed still requests a fixed count.
- **Item 2:** manually reproduce the original bug — leave an ordinary Challenge session abandoned mid-session, then click Redo Missed Questions from a completed session on the same study pack, and confirm a fresh redo session starts (missed questions only), not the stale ordinary one.
- **Item 3:** manually leave questions unanswered and click Submit; confirm the guard appears, "go back" returns to an unanswered question, and "submit anyway" finalizes with the same scoring behavior as today.
- **Item 4:** onboard as a Student with a course/program that has no published Official Review Set and confirm the capture prompt appears and is skippable; onboard as a Student with a covered course/program and confirm no prompt appears; confirm a submitted note is visible via the existing `/admin` feedback view tagged with the `onboarding_coverage_gap` source.
- Full regression before PR handoff for commit: `./mvnw clean install` (backend) and `npm test` + `tsc --noEmit` (frontend), per this project's standing build-before-commit rule.

## v0.60.2 — Challenge Quiz Known-Limitations Cleanup (Released, base branch `releases/v0.60.2`)

**Kicked off 2026-07-26.** All three root causes below were confirmed by direct file read in the same session that kicked this off — re-verify file:line anchors first if code has shifted since.

### Context

v0.60.1's own signoff logged three narrow Known Limitations, deliberately deferred at the time as out of scope for that patch release: a claim-release rollback that can leave Challenge Quiz bank rows stuck claimed after a mid-batch generation failure (Item 1), an expiry-vs-completion race that can silently overwrite a genuinely completed session back to forfeited (Item 2), and a Resume/Start Fresh prompt that never appears when a live session is found via the collection/Review Set premium-exam launch, unlike the note detail page's Challenge Quiz card (Item 3). None of the three depends on a product decision, a data pull, or a time-gated cohort read — unlike every other Backlog Index candidate reviewed the same day this was kicked off, which is why this was picked as the one thing genuinely ready to build right now.

Routing note: per `CLAUDE.md`'s task-routing table, all three items are isolated 1–2 file bug fixes with a clear root cause — the table's own "Claude Code, implement directly" lane. This release routes them to Codex anyway, as a deliberate one-off choice to conserve Claude usage while Codex capacity was available; the audit-before-commit step below is not optional given that tradeoff.

### Item 1 — Claim-release rollback under `@Transactional` — Shipped, see `RELEASES.md`

**Classification: small, direct implementation (no Codex prompt needed in ordinary circumstances) — routed to Codex per the Context note above.**

Confirmed: `ChallengeQuizQuestionBankService.releaseClaims` (line 221) has no `@Transactional` annotation of its own today. `ChallengeQuizService.generateMoreQuestions`'s catch block (lines 702–705) calls it on a generation failure, then re-throws. Because `generateMoreQuestions` runs under the class-level `@Transactional` on `ChallengeQuizService` (line 70), that re-throw rolls back the entire transaction — including `releaseClaims`'s own writes, which exist specifically to undo the claim on failure. Affected bank rows stay claimed until they naturally expire instead of being freed immediately.

Fix: add `@Transactional(propagation = Propagation.REQUIRES_NEW)` directly on `ChallengeQuizQuestionBankService.releaseClaims`. Since `ChallengeQuizQuestionBankService` is a distinct Spring bean from `ChallengeQuizService` (not a self-invocation), Spring's proxy-based AOP intercepts the call correctly — the new transaction commits independently of the caller's eventual rollback. `releaseClaims` already wraps its body in its own try/catch that swallows `RuntimeException` (never re-throws), so this is a pure addition with no new failure surface. This also affects `releaseClaims`'s other two call sites (`forfeitSession` line 614, `forfeitExpiredSession` line 931) — both already treat the release as best-effort/non-atomic with the session save (each already in its own try/catch), so running it in its own transaction doesn't change or regress their existing behavior.

Files: `backend/src/main/java/com/studysnap/backend/service/ChallengeQuizQuestionBankService.java`, `ChallengeQuizQuestionBankServiceTest.java` or `ChallengeQuizServiceTest.java` (test that a mid-batch generation failure still results in released claims even though the outer transaction rolled back).

Sequencing: fully independent, land anytime.

### Item 2 — Expiry-vs-completion lock race — Shipped, see `RELEASES.md`

**Classification: small, direct implementation (no Codex prompt needed in ordinary circumstances) — routed to Codex per the Context note above, with a mandatory recheck-after-lock step (see below).**

Confirmed: `resolveExistingChallengeSession` (line 879) reads the candidate session via `findTopByUserIdAndStudyPackIdAndSessionModeAndStatusInOrderByCreatedAtDesc` — no lock. `completeSession` (line 513) and `generateMoreQuestions` (line 619) both instead call `findChallengeSessionForUpdateOrThrow`, which uses `findByIdAndUserIdAndSessionModeForUpdate` (`QuickReviewSessionRepository.java` line 78-86, `@Lock(LockModeType.PESSIMISTIC_WRITE)`). If a `completeSession` request and a `resolveExistingChallengeSession`-triggered expiry forfeit land close enough together, the forfeit's unlocked read-then-write can commit after the completion's, overwriting `COMPLETED` back to `FORFEITED` and hiding a genuine finished attempt from Recent Sessions.

Fix: in `resolveExistingChallengeSession`, after the existing unlocked "top" lookup identifies a candidate session id, re-fetch that exact same id through the existing `findByIdAndUserIdAndSessionModeForUpdate` (the same locked query `completeSession`/`generateMoreQuestions` already use) before making any forfeit decision. **Mandatory: re-check the locked session's status is still `IN_PROGRESS` after the lock is acquired** — if a concurrent `completeSession` already committed and changed it to `COMPLETED` while this method was waiting for the lock, do not forfeit it; treat it the same as any other non-resumable session (return `Optional.empty()`, matching the existing behavior for an already-forfeited session). Only proceed to the existing expiry check + `forfeitExpiredSession` call if the locked re-read still shows `IN_PROGRESS`.

**Why this doesn't introduce new deadlock risk (verified, not assumed):** every code path that locks this session row — `completeSession`, `generateMoreQuestions`, and now `resolveExistingChallengeSession` — acquires exactly one lock, on the same single row, via the same query shape (`id` + `userId` + `sessionMode`). A deadlock requires two transactions each holding a resource the other is waiting for, in conflicting orders across two or more resources; single-resource, single-order contention only ever produces a lock *wait*, not a cycle. Do not add any additional lock acquisition (e.g. on bank rows) while holding this session lock without re-verifying this reasoning still holds.

Files: `backend/src/main/java/com/studysnap/backend/service/ChallengeQuizService.java` (`resolveExistingChallengeSession`), `ChallengeQuizServiceTest.java` (test: a session forfeited via this path after a concurrent completion already landed must not overwrite `COMPLETED`; a genuinely expired, still-`IN_PROGRESS` session is still forfeited as before).

Sequencing: independent of Items 1 and 3. Same file as Item 1's test suite target but a different method — no merge conflict expected.

### Item 3 — Resume/Start Fresh missing at the collection premium-exam entry point — Shipped, see `RELEASES.md`

**Classification: small, direct implementation (no Codex prompt needed in ordinary circumstances) — routed to Codex per the Context note above.**

Confirmed: `collection-detail-page-client.tsx`'s `openCollectionPremiumExam` (lines 3103–3120) builds `const params = new URLSearchParams({ collectionId })` and, for the Challenge Quiz branch (line 3119), pushes to `/study-packs/${primaryExamStudyPackId}/challenge-quiz?${params.toString()}` with no `entry` param. The target page (`frontend/app/study-packs/[id]/challenge-quiz/page.tsx`) already reads `CHALLENGE_QUIZ_ENTRY_QUERY_PARAM` via `searchParams.get(...)` (confirmed present, route-agnostic) and only shows the Resume/Start Fresh prompt when that param equals `CHALLENGE_QUIZ_MODE_SELECTION_ENTRY` (`isModeSelectionChallengeQuizEntry`, from `frontend/lib/challenge-quiz-entry.ts`) — the same constants `buildChallengeQuizHref` already uses for the note detail page's Challenge Quiz card. Without it, a live session found through this entry point is silently auto-resumed with no choice offered, same underlying gap v0.60.1's TEACHER-profile fix closed for a different entry point.

Fix: in the Challenge Quiz branch of `openCollectionPremiumExam` only (not the `long_exam`/`interview` branches above it — they don't read this param and are out of scope), append `entry=mode-selection` using the existing exported constants (`CHALLENGE_QUIZ_ENTRY_QUERY_PARAM`, `CHALLENGE_QUIZ_MODE_SELECTION_ENTRY` from `frontend/lib/challenge-quiz-entry.ts`) to the `URLSearchParams` before the `router.push` at line 3119.

Files: `frontend/app/collections/[id]/collection-detail-page-client.tsx`, its test file (test: clicking the premium-exam CTA when `terminalAction.mode` resolves to Challenge Quiz navigates with `entry=mode-selection` in the query string).

Sequencing: fully independent, frontend-only, land anytime.

### Overall Sequencing

All three items are independent — no required order, can land as one PR or three. Given the size (3 small, unrelated fixes), one combined Codex prompt covering all three is appropriate rather than three separate prompts.

### Verification

- **Item 1:** trigger a mid-batch `generateMoreQuestions` failure (e.g. force `NotEnoughNewQuestionsException`) and confirm the claimed bank rows are released (`claimedSessionId` cleared) even though the request itself errors.
- **Item 2:** a concurrency test (or a manually sequenced test using the two locked queries) proving a session already moved to `COMPLETED` by `completeSession` is never overwritten to `FORFEITED` by a subsequent `resolveExistingChallengeSession` call; a separate test proving a genuinely expired, still-`IN_PROGRESS` session is still forfeited exactly as before this change.
- **Item 3:** manually reproduce — have a live, non-expired Challenge Quiz session, trigger the collection/Review Set premium-exam CTA, and confirm the Resume/Start Fresh prompt appears instead of silent auto-resume.
- Full regression before PR handoff for commit: `./mvnw clean install` (backend) and `npm test` + `tsc --noEmit` (frontend), per this project's standing build-before-commit rule.

## v0.60.1 — Challenge Quiz Fix Pass (Released, base branch `releases/v0.60.1`)

**Kicked off 2026-07-25.** The plan below was fully designed and verified before kickoff — treat it as ground truth for implementation, re-verifying file:line anchors first since code may have shifted since 2026-07-25.

### Context

v0.60.0 shipped Official Challenge Quiz Template Sharing (a pre-generated, shared question pool for admin-authored "Official" content). Attempting to run the one-time production backfill immediately surfaced a real infra bug (Item 1), and using the resulting feature surfaced four more pre-existing (or newly-exposed) Challenge Quiz issues that predate v0.60.0 but were only now noticed in live testing: no whole-array shuffle ever existed (Item 2), difficulty selection has been silently inert since v0.58.0's per-user question bank shipped (Item 3 — resolved via product decision to remove the feature rather than fix the underlying architecture mismatch), abandoned sessions get silently resumed and immediately auto-submitted as "time's up" (Item 4), and "Redo Missed Questions" has never actually worked because of a same-route navigation bug (Item 5).

All five root causes below are confirmed via direct code reading (not inferred) across three rounds of research: three parallel Explore agents, a scope-verification pass, and a Plan agent whose two safety-critical claims (index-keyed answer maps; existing timer/forfeit helpers) were independently re-verified by direct file reads in this same session. This is a patch release off `v0.60.0` (following this project's existing patch-release convention, e.g. v0.51.1, v0.52.1, v0.54.1).

Two decisions were made by the product owner before this plan was finalized — do not re-litigate them without a new reason:
- **Difficulty (Item 3): remove the manual Easy/Medium/Hard selector from Challenge Quiz entirely**, rather than re-engineer the bank/template claim path to respect it. Reasoning: doesn't fit the reusable-pool architecture, Adaptive Practice already covers personalized difficulty better, and its pedagogical value as a manual picker (vs. a historical Plus/Pro differentiator) is doubtful. Board Exam Mode is unaffected — it already uses a fixed `DIFFICULTY_MIXED` value and never touches this gate.
- **Abandoned sessions (Item 4): reuse the existing Long Exam pattern** (expiry detection + "Resume / Start Fresh" prompt, on the setup page's existing load-time fetch) rather than build new card-level pre-check logic that no quiz mode in this app has today.

### Item 1 — Executor saturation (`TaskRejectedException` on backfill) — Shipped, see `RELEASES.md`

**Classification: small, direct implementation (no Codex prompt needed).**

`OfficialChallengeQuizTemplateService.queueBackfill()` fires ~50 seed tasks in a tight loop via `dispatchSeedAfterCommit` → `studyPackGenerationTaskExecutor` (`AppConfig.java`, pool 3/6, queue 100, default `AbortPolicy`) — the same executor live, user-facing Study Pack generation uses (`StudyPackService.java:650-652`). This is a real production-risk bug: it can delay/reject actual user requests, and did reject its own tasks (confirmed stack trace: 106 tasks in flight against 6+100 capacity).

Fix:
- Switch `dispatchSeedAfterCommit` (used by both `queueBackfill` and the per-note `queueSeedIfEligible` eager hooks) to the existing `llmParallelTaskExecutor` bean (`AppConfig.java`, pool 4/8, queue 50) — the pool this codebase already uses for bulk LLM fan-out, via `@Qualifier("llmParallelTaskExecutor")`, exactly matching `AdminStudyPackService`'s existing pattern for its own bulk admin actions (regenerate-summaries, repair-malformed-quizzes). Move both call sites, not just the backfill — template seeding is background LLM work by nature and should never contend with live generation.
- Wrap the dispatch in `try/catch (RejectedExecutionException)`, matching `AdminStudyPackService`'s existing guard style.
- Extend `AdminSeedOfficialChallengeQuizTemplatesResponse` from `(queued, skipped)` to `(queued, skipped, rejected)` so a rejected dispatch is reported distinctly rather than silently inflating `queued`.
- No additional throttling/batching needed — the 4/8/50 pool's bounded max size is itself the concurrency limiter, consistent with how `AdminStudyPackService` already relies on it.

Files: `backend/src/main/java/com/studysnap/backend/service/OfficialChallengeQuizTemplateService.java`, `backend/src/main/java/com/studysnap/backend/dto/AdminSeedOfficialChallengeQuizTemplatesResponse.java`, `OfficialChallengeQuizTemplateServiceTest.java`.

Sequencing: fully independent — land first, in parallel with everything else. **The v0.60.0 production backfill still has not successfully completed — re-running it is blocked on this fix landing.**

### Item 2 — Whole-array shuffle — Shipped, see `RELEASES.md`

**Classification: small, direct implementation — but with a data-safety constraint that must be respected.**

**Shipped with one refinement beyond this plan:** Challenge Quiz can include a MATCHING block (2–4 consecutive questions sharing a `questionGroup`, confirmed in `challenge-quiz-developer.txt`), which the frontend (`lib/quiz.ts`) groups by scanning the array for adjacency. A flat `Collections.shuffle` on the whole list — as originally planned below — would silently scatter a MATCHING block apart and break that grouping. The shipped fix shuffles at the block level instead: MATCHING runs are treated as one atomic unit that moves together, everything else shuffles freely. This was caught by re-verifying the plan against current code before implementing, not by re-deriving the plan itself — the injection points and data-safety constraint below are otherwise exactly as planned.

Confirmed: no shuffle exists anywhere in the Challenge Quiz path (grepped for `shuffle`/`Random`/`nextInt` across `ChallengeQuizService.java`, `ChallengeQuizQuestionBankService.java`, `OfficialChallengeQuizTemplateService.java`, `QuizSessionStateUtils.java`, `ChallengeQuizQuestionBankRepository.java` — zero hits). `persistGeneratedQuestions` stamps one shared `generatedAt` per batch (`ChallengeQuizQuestionBankService.java:94,112`), and `findClaimableForUpdate` orders strictly `generatedAt asc` with no tiebreaker (`ChallengeQuizQuestionBankRepository.java:14-22`) — so batches (original 5, each "+5") always sort as fixed blocks, forever, across every future session.

**Critical safety finding (independently verified by direct file read against `QuizSessionStateUtils.java`, lines 84/108/137/161):** selected-answer maps (`selectedChoices`, `selectedMultiChoices`, `selectedIdentificationAnswers`, `selectedEnumerationAnswers`) are keyed by **array index** (`String.valueOf(questionIndex)`), not question identity. Reshuffling an in-progress session's array would silently remap recorded answers to the wrong questions — this constrains the fix to one safe injection point. Do not skip re-verifying this before implementing if any of the surrounding code has changed since 2026-07-25.

Fix — matches the product owner's actual ask precisely (their examples were about a "second try" / "succeeding challenge quiz," i.e. a new session, not the "+5" growth within one active session):
- Shuffle the fully assembled `challengeQuiz` list (banked + template + freshly generated) once, at **initial session start** (`ChallengeQuizService.java`, Challenge branch of `startSession`, assembled ~lines 277-313), before `markSessionReady` — the one point where no answers exist yet, so a whole-array shuffle is safe. Every new session presents its accumulated pool in a fresh order.
- Do **not** reshuffle the whole array in `generateMoreQuestions`/"+5" (~lines 614-706) — that would corrupt already-recorded index-keyed answers. Instead, shuffle only the newly-added batch before appending it via `QuizSessionStateUtils.appendQuizItems`, so growth still avoids fixed-order artifacts without touching existing indices.
- Skip the shuffle for Board Exam (leave its existing ordering/behavior untouched — out of scope, avoid an unintended regression).
- Apply the same initial-shuffle treatment to `startRedoMissedSession`'s assembly (same `markSessionReady` call, ~line 405), since it's the same safe assembly point.

Files: `backend/src/main/java/com/studysnap/backend/service/ChallengeQuizService.java`, `ChallengeQuizServiceTest.java` (use a seeded/injectable RNG or a copied-list assertion so order can be tested deterministically).

Sequencing: same file as Item 3 (adjacent methods) — land **after** Item 3 to avoid merge churn. Logically independent of it.

### Item 3 — Remove manual difficulty selection from Challenge Quiz — Shipped, see `RELEASES.md`

**Classification: large — write a Codex prompt, don't implement directly.** Multi-system (backend enum/config/DTOs/service + frontend page + pricing/paywall/marketing copy + two test suites), well past the 5-file/100-LOC threshold for direct work, and pricing-adjacent copy in this project gets deliberate handling. Use `docs/skills/codex-prompt-generator.md`'s Long template.

Full REMOVE / KEEP scope was verified via a dedicated research pass and is ground truth for the Codex prompt's REQUIRED CHANGES section — do not re-derive it, just transcribe it into the prompt (re-verify line numbers first, since code may have shifted since 2026-07-25):

**Remove:** `resolveSelectedDifficulty`'s Challenge branch (`ChallengeQuizService.java:1108-1116`, keep the Board Exam branch at 1105-1106 which returns `DIFFICULTY_MIXED`), `resolveGenerationProfile`'s manual-selection path (844-845), `isDifficultySelectionAvailable` helper (1649-1650) and its two response-flag uses (1094, 1669), `selectedDifficulty` plumbing in `startSession` (177, 200); backend `Feature.DIFFICULTY_SELECTION` enum value (delete outright — it has exactly one exhaustive switch, `FeatureGateService.hasFeatureAccess` lines 46-52, so deletion is compile-clean once that `case` at line 48 is removed) and every reference (`FeatureGateService.java:48`, `StudySnapProperties.java:241-246` and its `difficultySelectionProOnly` config, `application.yaml:130`'s `difficulty-selection-pro-only` key, `MePlanService.java:104`, `BillingUsageService.java:35`, `NoteService.java:1399`'s `NoteResponse.difficultySelectionAvailable` field, `entity/Feature.java:10-12`'s access-denied copy, `dto/ChallengeQuizStartResponse.java:19`'s matching field); frontend selector block only (`frontend/app/study-packs/[id]/challenge-quiz/page.tsx:1689-1712` — the file is shared with Board Exam setup, do not remove the whole file) plus `canChooseChallengeDifficulty` (line 1285) and its consumption sites (158, 160, 1125, 1284); all pricing/marketing/paywall copy referencing difficulty selection as a Plus/Pro benefit: `frontend/src/config/plans.ts` (lines 56, 92, 171-177, 219, 304-309, 352-357), `frontend/app/page.tsx:143`, `frontend/app/dashboard/free-plan-upgrade-card.tsx:19`, `frontend/app/billing/success/page.tsx:60`, `frontend/components/billing/pricing-plans-section.tsx:363`, `frontend/components/help/quiz-modes-guide.tsx:29`, `frontend/lib/paywall-content.ts` (`DIFFICULTY_SELECTION_LOCKED`, lines 12,41,94-95,219-223), `frontend/components/billing/paywall-modal.tsx:53,122-123`.

**Keep — do not touch:** Board Exam's fixed `DIFFICULTY_MIXED` path (`resolveGenerationProfile` lines 841-842), the score-based automatic Challenge difficulty fallback (lines 847-868, last Quick Review score → EASY/DEFAULT/HARD) which becomes the sole Challenge difficulty path after removal, `resolveQuestionCountForDifficulty` (897-904), `extractDifficulty` (1188-1197), `buildInitialSessionState` (1145-1159). `startRedoMissedSession` (line 388) already passes `null` difficulty and is unaffected.

Remove (don't just skip) the two difficulty-specific tests that assert on the removed behavior (`frontend/app/study-packs/[id]/challenge-quiz/page.test.tsx` lines 435 and 1118; `ChallengeQuizServiceTest.java`'s `startSession_throwsTypedExceptionForInvalidDifficulty` line 1956); trim/update the remaining ~58 difficulty references across both test suites, including fixture-only touches in `pricing/page.test.tsx:95`, `settings/page.test.tsx:170,208`, `paywall-modal.test.tsx:87`, `NoteServiceTest.java:132`; add a regression test proving Board Exam's fixed-MIXED path is untouched, and one proving the score-based fallback is now the only Challenge difficulty source.

Land as one atomic backend+frontend PR (single monorepo release, no cross-service contract window to protect). Run `/audit-diff` after delivery, then update `docs/features/challenge-quiz.md` and check `docs/product/ROADMAP.md`'s own Backlog Index row for "Difficulty Selection moved Pro→Plus" (a related, now-superseded pending pricing item — this removal resolves it, update or close that row too).

Sequencing: land **first** among the three items touching `challenge-quiz/page.tsx` (Items 3, 4, 5) — it simplifies the file for the other two and reduces merge churn.

### Item 4 — Abandoned sessions silently resumed and auto-submitted — Shipped, see `RELEASES.md`

**Shipped refinement:** expiry handling now runs in both layers documented below: the frontend read-path check prevents a stale fetched session from entering the running timer, while `resolveExistingChallengeSession` auto-forfeits expired explicit-start sessions. Server-side Challenge-mode cleanup also releases live bank claims; Board Exam follows the same expiry and prompt behavior without claim release.

**Classification: large — write a Codex prompt, don't implement directly.** Multi-system (backend service-logic change + new frontend UI states ported from an existing pattern).

Confirmed mechanism: `resolveExistingChallengeSession` (`ChallengeQuizService.java:871-895`) resumes any `IN_PROGRESS`+non-empty-quiz session unconditionally — no timer/expiration check exists anywhere server-side (confirmed: no entity column, no expiration comparison logic anywhere in the backend; the timer is only ever read back to echo to the client). The frontend's setup page (`frontend/app/study-packs/[id]/challenge-quiz/page.tsx`) already fetches this stale session on load (`getInProgressChallengeQuizSession`, line 779) but discards it on the `entry=mode-selection` path (lines 782-802 — the exact flow from the note detail page's Challenge Quiz card); on Start Quiz, the resumed session's deadline (`timerStartedAtEpochSeconds + timeLimitSeconds`, both stamped at the ORIGINAL session's start) is already in the past, so `applyStartedSession` (lines 597-690) computes 0 seconds remaining and the running-timer effect (lines 1016-1038) immediately auto-submits as a timeout — the reported "suddenly ends."

Fix has two layers:
- **Server-side (the actual root fix):** `resolveExistingChallengeSession` is the single choke point every start path flows through — client-side detection alone can't fully close this, since the non-mode-selection entry path (page.tsx lines 804-808) resumes stale sessions directly with no discard step at all. Add expiration awareness there: compute the deadline from the session's already-stored `timerStartedAtEpochSeconds`/`timeLimitSeconds` (via the existing `extractTimeLimitSeconds` line 1199 / `extractTimerStartedAtEpochSeconds` line 1210 helpers — confirmed present, no schema change needed); if expired, auto-forfeit via the existing `markSessionForfeited` (line 1636, already used elsewhere in this same method) and return empty instead of resumable.
- **Client-side (UX, ported from Long Exam):** add `isChallengeQuizSessionExpired`, mirroring Long Exam's `isLongExamSessionExpired` (`frontend/app/notes/[id]/long-exam/page.tsx:112-122`) using the same already-shared `frontend/lib/challenge-quiz-timer.ts` helpers (`resolveDeadlineEpochSeconds`, `resolveRemainingSecondsFromDeadline`). In the setup page's `entry=mode-selection` branch, replace the current outright-discard with: expired → forfeit (mirroring Long Exam's on-load forfeit at lines 359-368) and show a clean prestart; not expired → show a "Resume / Start Fresh" prompt (port Long Exam's UI at lines 856-877) instead of silently resuming or silently discarding.

**Deferred, not bundled:** the separate, already-documented v0.58.0 limitation ("A redo-missed request can silently resume an unrelated in-progress session… matches only on session type, not on the specific sub-mode requested" — `docs/releases/v0.58.0.md:34`, still live in the code) is a related but separable concern. Fixing it here would widen blast radius for uncertain benefit — leave it as a tracked, still-open known limitation.

Files: `backend/src/main/java/com/studysnap/backend/service/ChallengeQuizService.java` (`resolveExistingChallengeSession`), `frontend/app/study-packs/[id]/challenge-quiz/page.tsx` (the `entry=mode-selection` load branch, new Resume/Start Fresh UI), reference-only `frontend/app/notes/[id]/long-exam/page.tsx` and `frontend/lib/challenge-quiz-timer.ts`.

Sequencing: land after Item 3 (shared file). Land before/combined with Item 5 (shares the same method and page region).

### Item 5 — "Redo Missed Questions" does nothing — Shipped, see `RELEASES.md`

**Shipped:** the query entry is preserved in state while its URL parameter is stripped, resets a stale result/session to prestart, then starts the dedicated redo endpoint. The fewer-than-three-missed conflict now renders the backend message on a clean, retryable prestart.

**Classification: small, direct implementation (no Codex prompt needed).** Frontend-only, one file, mirrors an existing in-file pattern.

Confirmed mechanism: the button (`frontend/components/study-pack/post-session-next-step.tsx:71,83`) is a same-route, query-only `<Link>` to `?entry=redo-missed` that doesn't remount the page — so the redo-missed effect's guards (`page.tsx:1169-1182`, `phase !== "prestart"` and `challengeSession?.sessionId` already set) short-circuit and `startRedoMissedChallengeQuizSession` is never called. The user just sees the previous session's own already-rendered result (confirmed: the displayed percentages are `computeStatistics`'s real numbers for the prior partially-answered session, not a stale unrelated session or an auto-submit artifact). No quota is charged (the request never fires, and the backend path is quota-exempt by design regardless — `session.setQuotaExempt(true)`, `ChallengeQuizService.java:406`, no usage-increment call in `startRedoMissedSession` at all).

Fix — mirror the sibling `entry=mode-selection` pattern already in the same file (`sharedModeSelectionEntryRequested` state flag at lines 456-461 + `router.replace` to strip the query param at lines 463-470):
- Add a surviving state flag (`redoMissedEntryRequested`) set from the query param, then strip the param via `router.replace`. The start effect must key off this surviving flag, not the live query param, or stripping the query kills the trigger.
- Add a one-shot reset: when the flag is set and the page is on a stale screen (`complete`/existing session/result), reset to a clean prestart (reuse the existing `handleRetry` reset block at lines 1184-1199), then let the now-correctly-keyed effect fire the redo-missed start.
- Add the missing error state: confirmed **zero** existing frontend handling of `NOT_ENOUGH_MISSED_CHALLENGE_QUESTIONS` (thrown by `claimIncorrectQuestions` when fewer than `MINIMUM_REDO_MISSED_QUESTIONS`=3 are eligible) — this path has apparently never successfully run in production, since the button itself never worked. Add an explicit branch in `handleStartChallenge`'s catch (lines 1139-1150) that keeps the user on a clean prestart screen with the backend's ready user-facing message ("Answer at least 3 Challenge Quiz questions incorrectly before redoing missed questions."), rather than an unhandled raw error, and ensure the retrigger flag/ref are cleared so the user can retry a normal start.

Files: `frontend/app/study-packs/[id]/challenge-quiz/page.tsx`, its test file.

Sequencing: land after Item 4 (shared file region) — consider combining into one PR with Item 4 ("Challenge Quiz session entry hardening") since both touch entry-handling and can share review context.

### Overall Sequencing

1. **PR A — Item 1** (executor fix). Independent, land anytime, can go first. Unblocks re-running the still-incomplete v0.60.0 production backfill. **Shipped.**
2. **PR B — Item 3** (difficulty removal, Codex prompt). Land early to reduce conflict churn for C/D.
3. **PR C — Item 2** (shuffle). Rebase on B. **Shipped**, block-aware (see Item 2's note above).
4. **PR D — Items 4 + 5 combined** (session entry hardening: expiration/forfeit + redo-missed retrigger; Item 4's portion is the Codex prompt, Item 5 folds in as a small direct addition). **Shipped.**

After each Codex-scoped PR (B, D): run `/audit-diff` before committing, per this project's standard Codex-delivery process. Update `RELEASES.md` v0.60.1 and `docs/features/challenge-quiz.md` as each item ships (Item 3 in particular changes documented, user-visible behavior — Plus/Pro plan comparison copy needs updating too, see Item 3's Files list).

### Verification

- **Item 1:** trigger the backfill again (small eligible-note set first) and confirm seed tasks run on `llm-parallel-*` threads (not `study-pack-generation-*`), and that a forced-saturation scenario produces a `rejected` count in the response rather than a thrown exception.
- **Item 2:** unit test with a seeded/deterministic shuffle asserting (a) a fresh session's presented order differs from strict `generatedAt` order across a 10-question accumulated pool, (b) a "+5" mid-session growth preserves existing question-index-to-answer mappings (the corruption-prevention regression test — this is the one that must not be skipped), (c) Board Exam ordering is unchanged.
- **Item 3:** run both test suites after removal; manually confirm Board Exam's difficulty is still fixed-MIXED and unaffected; confirm the Challenge Quiz Setup screen no longer shows a difficulty selector for any plan; confirm pricing/paywall pages no longer reference difficulty selection.
- **Item 4:** manually reproduce the original bug (start a session, abandon it past its timer window, start a new one) and confirm either a clean prestart or a "Resume / Start Fresh" prompt appears — never an instant auto-submit. Add a backend test for stale-session auto-forfeit and a frontend test for the prompt/forfeit-on-load paths.
- **Item 5:** manually complete a Challenge Quiz session with ≥3 incorrect answers, click "Redo Missed Questions," and confirm a real new quiz starts (not the stale result screen); separately test the `<3` missed case shows the new error message on a clean prestart.
- Full regression before every PR handoff for commit: `./mvnw clean install` (backend) and `npm test` + `tsc --noEmit` (frontend), per this project's standing build-before-commit rule.

## v0.60.0 - Shared Official Pool Foundation (Released, base branch `releases/v0.60.0`)

Origin: Phase 3a (`docs/claude-prompt/company-redefinition-out/04-reusable-assets-and-reviewer.md`), the foundation slice of Phase 3's cross-user question pool. Ratified and kicked off 2026-07-24 after its proposed adoption-concurrency gate cleared on real production data — see "Company Redefinition Roadmap — Phase Detail" above, Phase 3 section, for the evidence. Reclaimed the `v0.60.0` slot from Explore Convergence the same day, since this chunk was ratified on real data while Explore's gate remains unmet — Explore Convergence renumbers to `v0.61.0`. Explicitly not the same thing as v0.58.0's Reusable Practice Assets & the Return Loop (per-user, cross-session reuse) — this is cross-user sharing across different learners who adopted the same Official content. 3b (curator-side pool expansion) is not part of this release and remains gated on its own unresolved review-queue dependency.

**Retargeted 2026-07-24, same day as kickoff.** The original scope (`04`'s design) targeted `ExamQuestionPoolService` — Long/Board Exam pools. Checking the actual plan gate found Long/Board Exam is PRO-only (`isLongExamAvailable`/`resolveMonthlyBoardExamLimit` both gate on `PlanType.PRO`), and production has ~zero PRO users (per v0.58.0's own framing) — so the gate evidence's 23-25 LET / 9 PNLE adopters, all FREE-tier, cannot be exercising that subsystem. They're almost certainly hitting Challenge Quiz instead (FREE-tier, no LLM-cost metering on "give me more"). Two diagnostic scripts (run once against production, not retained in the repo) confirmed the retarget is buildable: the first attempt was inconclusive — a join-direction false alarm, resolved by checking `V17__study_pack_note_link.sql` — and the corrected version was conclusive: every child note under both Official plans has a `GENERATED` Study Pack and `PUBLIC` visibility, and zero existing `challenge_quiz_question_bank` rows. That second fact matters: `ChallengeQuizQuestionBankEntity` fuses question content with per-user state (`last_known_outcome`, `claimed_session_id`) on one row, unlike the exam pool's separated content/tracking — so a direct port of `resolvePoolKey` would have required migrating live "redo what you missed" state off shared rows. Copy-on-claim from an eagerly-seeded template avoids that entirely: every user still gets ordinary per-user rows, just sourced by copy instead of a fresh LLM call when a template exists.

### Planned Scope

**Official template seeding.** Eager seed at two hook points: `NoteService.updateVisibility()` (note transitions to `PUBLIC`, owner is Official (`isOfficialAuthor`), Study Pack already exists) and the existing `StudyPackService` call sites where `examQuestionPoolService.initiatePool(...)` fires (Study Pack generated for a note that's already `PUBLIC` and Official-owned) — covers both orderings. Seeding calls the existing `quizGenerationService.generateChallengeQuiz(...)` + `persistGeneratedQuestions(...)`, storing the result under the Official author's own `userId`/`studyPackId` — no schema change. A one-time backfill job seeds already-published Official content predating this release (confirmed scope: LET's 4 child plans / 43 notes, PNLE's children).

**Copy-on-claim.** When a Challenge Quiz claim against the calling user's own bank falls short, resolve the caller's `studyPackId` to an Official source via the existing note-lineage hop (`StudyPackEntity.noteId` → `NoteEntity.copiedFromNoteId`/`sourceNoteId` → source note → its Study Pack) and the role-based `isOfficialAuthor` check. If the resolved template has content the caller hasn't already received, copy it into fresh rows under the caller's own identity instead of calling the LLM; fall through to live generation for any remaining shortfall. Private, non-Official bank rows are fully unchanged.

Reconciles with, not a reversal of, the existing "do not pre-generate Challenge Quiz" ruling below (line ~747) — see `RELEASES.md` v0.60.0 for the distinction (one user's own progressive-batch pre-warm vs. a shared Official template seeded once). See `docs/claude-prompt/company-redefinition-out/04-reusable-assets-and-reviewer.md` for the original bounded-object-model design context; the Reviewer label-only relabel is not assigned to this release and remains a separate, independently-shippable decision.

## v0.59.0 - Dashboard & Progress Reorg (Released, base branch `releases/v0.59.0`)

Origin: second of Phase 2's two separately-gated chunks (`docs/claude-prompt/company-redefinition-out/03-information-architecture.md` §3), kicked off 2026-07-24 now that its gate — Reusable Practice Assets & the Return Loop shipped — is satisfied. Reclaimed the `v0.59.0` slot from Explore Convergence the same day, since this chunk's gate cleared first. See "Company Redefinition Roadmap — Phase Detail" above, Phase 2 section, for full design and the chunk split. Explicitly not the Explore Convergence chunk (now `v0.67.0`, renumbered 8 times total — see the Current Release Baseline note above), which was kicked off 2026-07-30 via an explicit owner gate override, not a cleared gate.

### Planned Scope

See `RELEASES.md` v0.59.0 for scope and anti-drift rules.

## v0.58.0 - Reusable Practice Assets & the Return Loop (Released, base branch `releases/v0.58.0`)

Origin: ratified by the owner 2026-07-24, inserted ahead of Phase 2 in the Company Redefinition resequencing (`docs/claude-prompt/company-redefinition-out/07-reprioritization.md`) — not Phase 3 (cross-user pooling of Official content stays parked at its own adoption-volume gate, unaffected). See "Company Redefinition Roadmap — Phase Detail" above, "Reusable Practice Assets & the Return Loop" subsection, for full design. Claims the `v0.58.0` slot previously earmarked for Phase 2's Explore Convergence chunk; see the Current Release Baseline note above for the resulting renumber.

### Planned Scope

See `RELEASES.md` v0.58.0 for scope and anti-drift rules.

## v0.57.0 - Practice-First Activation Onboarding (Released, base branch `releases/v0.57.0`)

Origin: Phase 1 of the Company Redefinition roadmap (`docs/claude-prompt/company-redefinition-out/06-unified-roadmap.md`) — the cheapest, most reversible, dependency-free phase of the boardready-model re-architecture, ratified by the owner 2026-07-23. Fully designed in `docs/claude-prompt/company-redefinition-out/02-activation-onboarding.md`. Explicitly picked to enter first because it produces the behavioral read (pre/post W1→W2 retention on the same covered course/program tracks) that was intended to gate Phase 2 — **the owner chose to kick off Phase 2 (v0.58.0, Explore Convergence) before that read comes back (decision: 2026-07-23); see the Backlog Index row above.** The read itself has not been pulled yet (needs a ~14-day window on both sides of the ship date) and remains an open follow-up regardless of Phase 2 proceeding.

### Planned Scope

See `RELEASES.md` v0.57.0 for scope and anti-drift rules.

## v0.56.0 - Weak-Concept Explanation Links (Released, base branch `releases/v0.56.0`)

Origin: Fable's top pick from the 2026-07-22 study-effectiveness session (`docs/claude-prompt/study-effectiveness-out/01-study-effectiveness-ui-pricing.md` §1 item 1) — explicitly scoped to exclude retention-trigger mechanics and Smart Review Planning. Picked as the cheapest, most direct answer to "does the product help you learn right now, in this session" — no dependency on the in-flight retention/acquisition experiments.

### Planned Scope

See `RELEASES.md` v0.56.0 for scope and anti-drift rules.

## v0.55.0 - Result-Screen Companion Bridge (Released, base branch `releases/v0.55.0`)

Origin: the only genuinely unblocked candidate from the App Shape planning session (`docs/claude-prompt/app-shape-out/01-app-shape-features.md` item 4) — its premise problem was resolved 2026-07-22 by reusing the v0.46.0 Weekly Pacing Echo's `primaryCollectionId` pattern instead of building a note→collection reverse lookup. Every other App Shape Core/Polish candidate remains gated on the retention constraint clearing or its own unresolved scoping question — this release does not touch those.

### Planned Scope

See `RELEASES.md` v0.55.0 for the Companion Bridge scope and anti-drift rules.

## v0.54.1 - Public Note Copy Correctness Fixes (Released, base branch `releases/v0.54.1`)

Origin: a self-inflicted-by-timing edge case surfaced during v0.50.3 signoff follow-up (no session doc) — `NoteService.copyNote()`'s existing-copy branch returns a prior copy as-is even if it predates the source's Study Pack becoming ready, so a user who copies before the source pack exists and retries later keeps getting the stale pack-less draft forever. Isolated, root-caused, Claude-Code-direct-sized fix — no Codex prompt needed.

### Planned Scope

See `RELEASES.md` v0.54.1 for the fix scope and anti-drift rules.

## v0.54.0 - CPALE Exam Hub (Wave 2) (Released, base branch `releases/v0.54.0`)

Origin: `next-priority-new-user-focus-out/01-next-priority-new-user-focus.md` (run 2026-07-21) confirmed the CPALE hub fits the "new users to retain" acquisition posture and recommended it as the smaller, lower-stakes build once its depth gate cleared (reuses an already-shipped 3x pattern, unlike H1+H5 which remains gated on the overdue v0.48.0 cohort re-read — see the Backlog Index row below). The depth-count gate (Query 2 in `next-priority-new-user-focus-out/02-h1-h5-cohort-recheck-and-cpale-depth.sql`) has since been confirmed against production, clearing the ~25-30-note Wave 2 bar.

### Planned Scope

See `RELEASES.md` v0.54.0 for the CPALE hub scope and anti-drift rules.

## v0.53.0 - SEO Discoverability: Exam Hub Depth & Organic Attribution (Released, base branch `releases/v0.53.0`)

Origin: the organic-search strategy session (`docs/claude-prompt/seo-strategy-out/01-seo-strategy.md`, run 2026-07-17 via Fable) diagnosed why "free PNLE notes"-style searches don't find NoteLib and ranked fixable gaps. After the product priority explicitly shifted to acquiring and retaining *new* users rather than re-engaging past churned ones, this picks up the code-shippable, gate-free remainder of that plan (P4/P5/P6) — pure acquisition work, no experiment-cohort or interview dependency.

### Planned Scope

All three scoped P4/P5/P6 items are shipped; see `RELEASES.md` v0.53.0 for the exam-hub depth, `ItemList`, and organic-landing attribution decisions and anti-drift rules.

## v0.52.1 - Early-Lifecycle Feedback Signals (Released, base branch `releases/v0.52.1`)

Origin: a follow-on to v0.52.0's proactive feedback prompts. The product owner initially floated this as quiz-result-specific copy, then corrected scope twice mid-session: first to app-wide, then explicitly to target *new* users specifically ("we're not chasing our previous users anymore, we're now chasing new users to retain") rather than winning back already-churned users — a question the existing `retention-diagnosis-session-plan.md` interview track owns instead, and which in-app prompts structurally cannot answer regardless. A Fable design session (`docs/claude-prompt/app-wide-feedback-signals-out/01-app-wide-feedback-signals.md`) scoped the three placements shipped here, explicitly rejecting a fuller build as the same "more listening infrastructure on an already-tiny population" anti-pattern the retention diagnosis flagged, in favor of a small, mostly-zero-backend slice.

### Planned Scope

See `RELEASES.md` v0.52.1 for the three placements (Public Library browse-without-adopt, first non-onboarding Study Pack generation, second-ever completed quiz) and their anti-drift rules.

## v0.52.0 - Proactive In-App Feedback Prompts (Released, base branch `releases/v0.52.0`)

Origin: a direct pivot away from the retention-diagnosis interview plan (`docs/claude-prompt/retention-diagnosis-session-plan.md`), after the v0.48.0 cohort-read gate came back with too weak a signal to act on and cold email outreach to churned users was judged unlikely to work (precedent: failed-payment recovery emails got zero response). Rather than chasing a hard-to-reach churned cohort, this surfaces the app's existing, under-used `SendFeedbackWidget`/`QuizFeedbackPanel` pipeline proactively to *current* users at two moments, so future cohorts' friction is caught in real time instead of inferred later. Retention H1 + H5 (`docs/product/ROADMAP.md` Backlog Index) stays exactly where it was — this doesn't resolve or replace that gate, it's a separate, forward-looking track.

**Scope:**
- First-quiz-ever feedback prompt (backend + frontend).
- Return-after-inactivity feedback prompt (backend + frontend).
- Mid-release addition (2026-07-20): feedback modal restyle + Admin detail view (frontend only), plus an optional screenshot attachment on feedback (backend + frontend) — see `docs/claude-prompt/feedback-system-polish-session-plan.md`.

Anti-drift: reuses the existing `POST /feedback` pipeline as-is. The one exception is the mid-release addition's new `feedback_image` table, explicitly justified by a read-path constraint (see `RELEASES.md`) — no other new storage, no rating/NPS data model. No changes to the existing 3-day inactivity email or its cooldown logic. No outreach to already-churned users. Full scope in `RELEASES.md`.

## v0.51.1 - Dashboard Stage-1 Limit Wiring (Released, base branch `releases/v0.51.1`)

Origin: F2's follow-up item from `v0.51.0 - Read-Path Performance Pass II`, deliberately deferred during F2's own implementation because Dashboard Stage-1's `totalNotes`, `hasCompletedSession`, and Challenge-CTA resolution all depended on the full unbounded note array at the time. Now that F2's bounded `limit` param exists and is unused, this closes the gap.

**Scope (shipped):**
- Dashboard Stage-1 bounded fetch (backend + frontend).

Anti-drift: read-path performance only, following F1/F2/F6's precedent from v0.51.0 — no change to what Dashboard displays or which note Continue Studying / Challenge Quiz routes to. No new caching infrastructure. No changes to quiz-session data model, mastery/readiness computation, or profile-type branching logic. Full scope in `RELEASES.md`.

## v0.51.0 - Read-Path Performance Pass II (Released, base branch `releases/v0.51.0`)

Origin: user-reported production slowness on Private Library, Public Library, Note Collection detail, and the Dashboard. Diagnosed via 4 parallel direct-codebase investigations (one per page) plus a Fable planning session (`docs/claude-prompt/production-performance-audit-out/01-production-performance-audit.md`), grounded against the prior `v0.38.0 - Read-Path Optimization Pass` precedent. Scoped in full via explicit user decision: F9/F10 left parked rather than included; F8 (real server-side Public Library pagination) built directly rather than F3's cap-and-load-more stopgap.

**Scope (shipped):**
- F1 — Dashboard-overview lean projections + bounding (Codex).
- F2 — `NoteService.listMine` lean projection + optional `limit` param (Codex).
- F4 — Private Library poller narrowing (Claude Code direct).
- F5 — Collection detail waterfall flattening + 2 verification tasks (Codex, likely crosses the ~50 LOC threshold).
- F6 — Dashboard Stage 2 batched fan-out endpoint (Codex).
- F7 — Real backend pagination for Private Library (Codex).
- F8 — Server-side filtering + full pagination UX for Public Library (Codex).

Anti-drift: read-path performance only; byte-identical responses for F1/F2/F4/F5/F6; F7/F8 are explicit UX/architecture changes, not disguised backend swaps; no new caching infrastructure; no quiz-session/mastery/readiness/profile-branching changes. Full scope in `RELEASES.md`.

## v0.50.4 - Exam Hub Discovery Polish (Released, base branch `releases/v0.50.4`)

Origin: direct user production testing of the "Quiz yourself"/"Add to Library" copy-as-is fix (confirmed working correctly on a fresh account — the apparent regression was stale test-account data hitting the copy idempotency guard, not a code bug) surfaced the duplicate Exam Hub link as a separate, real finding; bundled with P2 (vocabulary pass) from the SEO strategy Fable session (`docs/claude-prompt/seo-strategy-out/01-seo-strategy.md`) run the same day. Mid-release scope addition: after direct production testing showed NoteLib not surfacing for exam-adjacent searches, the vocabulary pass was extended to every Public Library subject page (not just Exam Hub), paired with a defensive subject-page indexation gate (`SUBJECT_PAGE_INDEX_THRESHOLD = 6`) informed by a production content-depth inventory.

**Scope (shipped):**
- Collapsed the duplicate "Browse {Hub} hub →" link on public note detail (Claude Code direct).
- "Free reviewer" vocabulary pass on Exam Hub pages — SEO candidate P2 (Claude Code direct).
- Extended the same vocabulary pass to all Public Library subject pages (Claude Code direct).
- Subject-page indexation gate — sitemap + `robots: noindex` below the depth threshold, so thin subject pages stop being served to search engines while staying fully reachable in-app (Claude Code direct).

Anti-drift: no mass-generated AI content; no pricing/paywall/quota changes; no Wave 2 Exam Hub expansion; the indexation gate changes search-engine visibility only, never page reachability or access. Full scope in `RELEASES.md`.

## v0.50.3 - Public Note Copy Flow & Related-Notes Consistency (Released, base branch `releases/v0.50.3`)

Origin: direct user testing of the public note detail page surfaced two findings, bundled into one Fable session (`docs/claude-prompt/public-note-copy-and-related-links-out/01-copy-flow-and-link-consistency.md`) since both live on the same page. (1) "Quiz yourself on this note" sometimes forces a full Study Pack regeneration whose AI-suggestion modal gets cut off by a real race condition — investigation confirmed the bug and found the backend already supports a free, synchronous copy-as-is path that should be the only behavior for this CTA; Fable confirmed copy-as-is over regeneration as the right default and recommended skipping the modal on this flow entirely rather than just fixing its timing. (2) The two related-notes sections' "see all" links wrap on mobile for longer subject/course names and use inconsistent grid column counts on desktop, both flagged directly from user screenshots.

**Scope:**
- Part A (Codex): copy-as-is only for the "Quiz yourself" CTA, skip the AI-suggestion modal on this flow, gate the CTA server-side on the source having a ready Study Pack, fix the navigation-vs-modal race as an ordering guarantee.
- Part B (Claude Code direct): shorten both related-notes "see all" links to `See all →` (with `aria-label`s), collapse the subject section's grid to match the course/program section's 2 columns.

Anti-drift: no new personalization/regeneration capability (the existing copied-pack regenerate hint already covers it); `Browse {Hub} hub →` stays untouched. Full scope in `RELEASES.md`.

## v0.50.2 - Note Card Content Consistency (Released, base branch `releases/v0.50.2`)

Origin: direct user report that the public note detail page's two related-notes sections ("More {Course/Program} notes" vs. "More in {Subject}") render inconsistently — traced to shipping drift (built six weeks apart, never reconciled) rather than a deliberate design choice. Broadened into a Fable card-content-strategy session (`docs/claude-prompt/note-preview-vs-summary-out/01-card-content-strategy.md`) after the user questioned whether the standing "prioritize note preview over summary" rule still holds now that a growing share of notes are AI-authored via `Generate Note` rather than user-written.

**Scope (Phase 1 only — Phase 2 origin-tracking stays parked, see Backlog Index):**
- Single-excerpt cascade on all four note-card surfaces (public note detail's two related-notes sections, Public Library grid, private Library grid): note preview if non-empty and long enough, else a labeled "Summary" fallback, else no excerpt block — never both stacked.
- Migrate the bespoke "More {Course/Program} notes" card to the shared `SharedNoteCard` component, matching "More in {Subject}" exactly (sections may still differ in query/count, never in card template).
- Rewrite the documented card-content rule in `docs/features/public-library.md` (and any other feature doc citing it) from a human-authorship rationale to a source-object rationale ("the note is the source, the summary is a fallback preview of a derivative") — the old rationale is factually broken now that note origin can't be verified, but the underlying priority (note preview first) still holds for an unrelated reason.
- Frontend-only, no schema/backend change. Fits the Claude-Code-direct lane (isolated, well-scoped, ≤ a few files) rather than needing a Codex prompt — confirm at kickoff.

Anti-drift: no origin-aware rendering (Phase 2 is explicitly rejected for card display even once instrumentation exists — see the Fable output's "Explicit rejections" §3–4); no change to Featured-ranking eligibility criteria; no change to note visibility/ownership/copy-adopt rules; no change to `Generate Note` itself.

Patch version, not minor — this is a consistency/polish fix to something that already exists, not new planned feature work, matching the v0.45.1/v0.45.2/v0.50.1 patch precedent.

## v0.50.1 - Mobile UI Polish (Released, base branch `releases/v0.50.1`)

Origin: direct user report of five UI polish items after using v0.50.0 in practice — three are fast-follow refinements to the tab bar itself (icon-only on focus pages, filter-retaining links, a show/hide preference), two are small, unrelated pre-existing issues (Review Set description truncation, Progress page milestone empty state). Routed entirely through Codex prompts this release (user's token-budget choice, mirroring v0.47.0's per-item Codex-prompt pattern) rather than the direct-Claude-Code lane the routing table would otherwise put most of these in. Full scope in `RELEASES.md`.

## v0.50.0 - Mobile Bottom Tab Bar (Released, base branch `releases/v0.50.0`)

Origin: Fable App Shape proposal (`docs/claude-prompt/app-shape-out/02-app-like-ui.md`), gated on device-mix evidence. Un-parked 2026-07-15 by a production pull showing ~75% mobile vs. 25% desktop by distinct users. Scoping confirmed the original proposal's coordination concern (a "sticky Continue bar") never shipped; two other real, currently-shipped bottom-of-viewport elements (`AddToHomeScreenNudge`, the floating "Send Feedback" launcher) need coordination instead. Full scope in `RELEASES.md`.

Mid-release addition (2026-07-15): the 3 held instrumentation pulls from `retention-diagnosis-session-plan.md`'s Strategy checkpoint (UTM/referral tracking, offline-fallback hit rate, browse-without-adopt tracking), folded in after an explicit decision to instrument rather than opened as their own version. See `RELEASES.md`.

## v0.49.0 - Progress Page: Private Library Links (Released, base branch `releases/v0.49.0`)

Origin: surfaced during the v0.49.0 scoping pass as one of two candidates from the "Post-v0.40.0 Polish Backlog" for a small orthogonal release to fill the interim window while v0.48.0's retention experiments accrue cohort data. The other candidate (Adopted badge on Review Sets) turned out to already be shipped in v0.40.1 — that roadmap entry was stale and has been corrected above. Full scope in `RELEASES.md`.

## v0.48.0 - Retention Experiment: Open Loop & Digest Trigger (Released, base branch `releases/v0.48.0`)

Origin: `docs/claude-prompt/retention-diagnosis-session-plan.md`'s "Recommended v0.48.0 scope" — two Fable sessions (growth/retention diagnosis + consumer psychology) converged on dead trigger infrastructure and no open loop at first-session end as co-dominant retention causes, sharpened by real production data pulls (week-1 depth ≈ W2 retention magnitude; exam-dated users retained *worse* under status quo, since nothing currently acts on the field). A pre-kickoff Resend open/click check (domain-wide, not `INACTIVITY`-specific — no per-type tagging exists to decompose it) found sub-1% click-through, which didn't kill either experiment but reweighted the digest trigger fix to include CTA/content work rather than a bare default flip. Full scope in `RELEASES.md`.

## v0.47.1 - V82 Migration Collision Hotfix (Released, base branch `releases/v0.47.1`)

Origin: production deploy has failed since `v0.46.0` merged with `Found more than one migration with version 82`. Root cause: a rebase artifact — `releases/v0.46.0` was cut from `main` before `v0.45.2` existed and later rebased onto latest `main`, but the due-concepts-digest migration kept its version number from the older base instead of picking up the number the newer `main` had already taken. Full scope in `RELEASES.md`.

## v0.47.0 - Conversion Audit Tier 4: Cleanup Batch (Released, base branch `releases/v0.47.0`)

Origin: Tier 4 of `docs/claude-prompt/conversion-audit-prioritized-backlog.md` (items 37–55, "low impact, cheap cleanups," explicitly meant to be batched together). Item 52 already shipped in v0.46.0; items 47 and 50 (already fixed in prior releases) were dropped during pre-scoping verification. Item 37 was also initially dropped (its assumed cookie-consumption mechanism didn't fit Learn's category-keyed guides), then folded back in at a smaller scope once the rest of the release shipped, reusing item 43's query-param intent pattern instead. Routed through Codex prompts per-item this release (token-budget choice), not the direct-Claude-Code lane the routing table would otherwise put every item in. Full scope in `RELEASES.md`.

## v0.46.0 - Retention Depth: Due-Concepts Digest & Exam Pacing (Released, base branch `releases/v0.46.0`)

Origin: Ideas 1 and 5 from the "New Capability Ideation" Fable session (`docs/claude-prompt/new-capability-out/01-new-capability-ideation.md`), the two ranked highest of three recommended for a real scoping pass. Both are retention-themed, continuing v0.44.0's data-driven thesis that retention (not top-of-funnel) is the proven constraint. Full scope in `RELEASES.md`.

## v0.45.2 - Public Plan Preview Rollup Fix (Released, base branch `releases/v0.45.2`)

Origin: the "Preview this plan" panel on public plan cards was found still showing "0 of 0 notes practice-ready" for Goal collections during v0.46.0 kickoff research — v0.45.1's rollup fix touched `list()`/`listPublic()` but never `getPublic()`, the third code path backing this panel. Cut from `main` as its own patch, independent of the concurrent `v0.46.0` branch. Full scope in `RELEASES.md`.

## v0.45.1 - Study Plan Collection Fixes (Released, base branch `releases/v0.45.1`)

Origin: three pre-existing bugs surfaced by direct user report and confirmed via Explore-agent investigation plus independent Opus and Fable consultations during v0.45.0's pre-signoff review — deliberately deferred to their own release rather than folded into v0.45.0 late. Full scope in `RELEASES.md`.

## v0.45.0 - Conversion Audit Tier 3 — Landing, Pricing & Discovery Polish (Released, base branch `releases/v0.45.0`)

Origin: the same 7-session conversion/retention UX audit that drove v0.44.0, Tier 3 of `docs/claude-prompt/conversion-audit-prioritized-backlog.md` (items #19–36, "medium impact, lower urgency"). Unlike v0.44.0's Tier 1/2, every item here routes to direct Claude Code implementation per `CLAUDE.md`'s task-routing table (copy/composition/UI polish, no new backend/infra) except the note-detail related-notes module, whose routing depends on whether a new query is needed. Full scope in `RELEASES.md`.

## v0.44.0 - Conversion & Retention Polish (Released, base branch `releases/v0.44.0`)

Origin: a 7-session conversion/retention UX audit (`docs/claude-prompt/conversion-audit-out/`, consolidated and tiered in `docs/claude-prompt/conversion-audit-prioritized-backlog.md`), run against a real prior data-driven finding (`docs/archive/conversion-funnel-finding.md`, v0.32.2) that identified retention — not top-of-funnel or onboarding — as the proven constraint. This release shipped the audit's Tier 1 (high-impact, low-effort) items plus one verified backend gap, then folded in Tier 2 (items 12–18) as a second slice rather than closing and reopening a new version. Full scope in `RELEASES.md`.

Two things surfaced by the same audit are explicitly **not** in this release's scope:
- **The `adaptivePracticeProOnly` pricing-copy-vs-runtime-gate divergence — resolved, no code change needed.** `StudySnapProperties.resolveMonthlyAdaptivePracticeLimit` has an `adaptivePracticeProOnly` kill-switch that, if `true`, would zero Free/Plus regardless of their configured 3/10 limits; it defaults `false` in both `application.yaml` and `application-prod.yaml`, and the owner confirmed production also runs with it unset/`false`. Marketing copy (3/10/30 across Free/Plus/Pro) was correct all along — `subscriptions-and-usage-limits.md`'s stale "Adaptive Practice unavailable"/"currently unavailable in runtime" lines and its "Pricing-surface note" mismatch disclaimer were corrected to match. No release scope required.
- **"Smart Review Planning."** A much larger, separate initiative (curriculum-driven Review Set auto-assembly) exists as paused planning material in `docs/claude-prompt/fable-out/` — architecture, matching/coverage, admin workflow, student UX, monetization recommendation, terminology audit, and a phased technical roadmap are all drafted but nothing is scoped or kicked off. Unrelated to this release; mentioned here only so it isn't confused with the conversion-audit work. **Current status and gate condition: see the Backlog Index above** — this sat unindexed for ~5 release cycles until a 2026-07-15 checkpoint surfaced it; don't let this historical mention be the only place it's tracked again.

## Retention Root-Cause Diagnosis (candidate, not yet scoped — diagnosis only, no implementation)

Not a version — no release branch, no implementation scope, and explicitly not intended to become one without a further, deliberate scoping pass. Origin: production W1→W2 retention read at **2.4%** (3 of 127 eligible activated users, 2026-07-15), against an earlier 5.6% read (v0.32.2) that first flagged retention as "the real constraint" — the level has not meaningfully improved despite two intervening releases touching retention surfaces (v0.44.0, v0.46.0). Two independent Fable consultations (growth/retention lens and consumer-psychology/behavioral-economics lens, run with no shared context) converged on the same core diagnosis: the retention infrastructure that exists (due-concepts digest, weak-concept nudge, weekly summary) ships default-OFF and is gated behind the exact engagement it's meant to create; the first session ends in psychological completion rather than an open loop; exam date is the strongest retention primitive in the product but is optional and Board-exam-only; and single-serving-utility risk is real for anchor-less casual users but not for exam-dated reviewees, whose job is inherently longitudinal. Pricing was independently ruled out by both sessions as a current bottleneck (Free quota is essentially never hit). Full diagnosis, ranked root causes, ruled-out causes, recommended data pulls (week-1 depth, an exam-date natural experiment, acquisition-source segmentation — all queries against existing data, no code), and candidate directions (all explicitly contingent on those data pulls, none release-ready) are in `docs/claude-prompt/retention-diagnosis-session-plan.md`, with the two full raw sessions in `docs/claude-prompt/retention-diagnosis-out/`. **Data pulls run 2026-07-15**: week-1 depth came back at 3.88% (near the same magnitude as W1→W2 itself, ruling out the "session 1 is fine, only week 2's trigger is broken" reading — the two root causes are co-dominant, not one ahead of the other); the exam-date natural experiment found exam-dated users retained no better under the status quo (0/35 vs 3/94 for non-exam-dated, small-sample), which strengthens the *active* commitment-device candidate direction over the passive one; acquisition-source segmentation remains genuinely unanswerable (no UTM/referral tracking exists in the schema). The recommended v0.48.0 scope (trigger fix + open-loop session ending, both cheap and independently testable) was confirmed and shipped — see `v0.48.0` above. **Post-shipment strategy checkpoint (2026-07-15):** two Fable consultations, run after v0.48.0 merged, prioritized what comes next — full detail in the session-plan file's "Strategy checkpoint" section. Headline: talk to actual retained/churned users now (cheap, non-confounding, more informative at this scale than further cohort analysis); pull UTM/referral tracking and device mix in the same pass; pre-committed decision rule for the v0.48.0 read (any positive-or-ambiguous signal → ship H1 + H5 together as one release, not sequentially, since a 2-week cohort is too small to cleanly resolve); Unified Next-Step Resolver reframed as H5's infrastructure, not standalone app-shape work; the exam-date-users-retained-worse finding may indicate a value problem, not just a trigger problem — unresolved until the interviews happen. **Interim-window analytics pulls run 2026-07-22** (`retention-diagnosis-out/04-interim-window-queries.sql`, run manually against production by the product owner — not by Claude, which lacks the prod `DB_USER`): device mix by distinct user is ~75% mobile / 25% desktop (209 mobile vs. 69 desktop, 1545 vs. 1700 tokens) — reconfirms, doesn't change, the device-mix read already used to un-park the v0.50.0 mobile tab bar. PDF export volume is unchanged at exactly 1 export, ever, by 1 user (2026-07-11) — further reinforces the existing "Parked — do not build" call on PDF export surfacing (see below), no new signal. Official Review Set coverage: exactly 4 course programs have a published (visibility=PUBLIC, top-level) Review Set at all — Accountancy (74 notes), Architecture (52 notes), Education (43 notes), Nursing (63 notes) — a 1:1 match with the four shipped Exam Hub programs (CPALE/ALE/LET/PNLE respectively). Each has real, non-trivial depth (43–74 notes), which is a mild prior *against* the strongest form of the content-gap-churn hypothesis for those four exams specifically — but zero official coverage exists for any board exam outside those four, which the interviews should probe if churned exam-dated users skew toward other boards. The same pass also re-ran the original exam-date natural experiment (`retention-diagnosis-out/03-data-pull-queries.sql` Query 2) at a larger, later sample: 0/41 (0%) exam-dated vs. 3/106 (2.83%) non-exam-dated — consistent with the original 2026-07-15 read (0/35 vs. 3/94), same direction, larger n. This is a general-population reconfirmation of the underlying hypothesis, not the H1+H5 kickoff gate itself — that gate is still the post-v0.48.0 cohort read, unreachable until 2026-07-29 (see Backlog Index above). None of these four results change any status or gate on their own; they narrow what the interviews still need to answer (mainly: value vs. discovery for exam-dated users, and coverage outside the four existing hubs) rather than resolving anything standalone.

## Post-v0.44.0 Conversion Audit Backlog (candidate, not yet scoped)

Not a version — no release branch, no implementation scope yet. Tier 2 (7 items) folded into `v0.44.0`; Tier 3 (18 items) is now scoped as `v0.45.0` (see above). Tier 4 (19 items, "low impact, cheap cleanups") remains here from the same 7-session conversion/retention UX audit — full detail, impact/effort ratings, and Claude Code/Codex routing per item in `docs/claude-prompt/conversion-audit-prioritized-backlog.md`. **Item 52 (echo Weekly Countdown pacing at quiz-session completion) is now scoped into `v0.46.0`** (2026-07-14, pulled in as a Concept Flashcards replacement — see `RELEASES.md`). **16 of the remaining 18 items are now scoped into `v0.47.0`** (2026-07-14 — see `RELEASES.md`); items 47 and 50 (both already fixed in prior releases) were dropped during v0.47.0's pre-scoping verification and remain closed candidates here. Item 37 was also initially dropped (its assumed cookie/goal-banner mechanism doesn't fit Learn's category-keyed guides) but was folded back into `v0.47.0` at a smaller scope once the rest of the release shipped, reusing the query-param intent pattern from item 43 instead. Also includes two explicitly deferred items (adoption-count social proof on Public Library plan cards; "Trending this week," blocked on windowed backend counts that don't exist yet). Pull from here when scoping what comes after v0.45.0 — do not re-run the audit or re-derive this list from scratch.

## App Shape, App-Like UI & Companion Authenticity (candidate, not yet scoped)

Not a version — no release branch, no implementation scope yet. Deliberately **not** conversion-related (unlike the two backlogs above) — three independent questions run through Fable about product shape and experience quality: (1) what features could deepen how Notes/Study Packs/Review Sets/Companion/Progress compose as one system, (2) how the five highest-traffic pages (Note Detail, Review Sets list, Review Set detail, Private Library, Public Library) could read as an app rather than a website, and (3) how Learning Companion content (both curator-written and the v0.42.0 AI-assist draft) could be grounded in real aggregate learner-experience data instead of reading as generic AI output. Full prompts, Fable's raw output, and a classified synthesis (Core Feature / Polish / Future Enhancement, plus explicit out-of-scope guardrails) are in `docs/claude-prompt/app-shape-session-plan.md`. **4 of the 7 Polish items are now scoped into `v0.46.0`** (2026-07-14 scope broadening, see `RELEASES.md`): shared note-card press feedback, skeleton-first initial load (Note Detail only — Review Sets list already had it), sticky search/filter toolbar, and collapse-by-default Note Detail sections. **3 items were pulled in and then dropped the same day** after direct investigation found they were misclassified as Polish — the Review Set filter facet needs a note→collection association that doesn't exist anywhere in the note-list API; the Result-Screen Companion Bridge has a premise problem (Companion content belongs to Collections, not Study Packs — resolve that scoping question before any future attempt); the Review Set feedback digest needs new schema (`FeedbackEntity` is a flat, unscoped global table today) and its paired staleness flag has no target (no "Struggle Map" evidence panel exists anywhere yet to attach it to — that panel is itself one of the still-unscoped Core Feature candidates below). All three remain here as candidates, correctly re-classified as needing real scoping passes, not quick fold-ins. The Core Feature candidates (Companion Live Milestones, Unified Next-Step Resolver, Concept-to-Note Back-Annotation, mobile bottom tab bar, Companion "Struggle Map" evidence panel) remain unscoped — nothing there is kicked off yet, and the Companion-authenticity work still has its named prerequisite (an adoption-provenance link, confirmed cheap on inspection) needing an explicit go/no-go before it's picked up. **Re-prioritized 2026-07-15** against the retention-root-cause diagnosis (see above): Live Milestones, Back-Annotation, and Struggle Map held indefinitely — none touch the proven retention constraint. Unified Next-Step Resolver reframed as infrastructure for the deferred H5 retention direction, not standalone scope — pick up only alongside H5. Mobile tab bar conditional on the device-mix data pull queued as part of the same checkpoint. Full reasoning in `docs/claude-prompt/app-shape-session-plan.md`'s correction note.

## New Capability Ideation (candidate, not yet scoped)

Not a version — no release branch, no implementation scope yet. Distinct from the three backlogs above: not conversion polish, not the composition/app-shape work, not the curriculum-auto-assembly bet — a single open-ended Fable pass asking what capability areas NoteLib has **no version of at all today**. 11 ideas classified via `docs/skills/roadmap-feature-audit.md`'s tiers (5 Core Feature, 4 Future Enhancement, 2 Low-Priority), plus 9 explicit rejections (no 6th quiz mode, no learner-to-learner content exchange bypassing curation, no auto-regeneration, no leaderboards, no freetext taxonomy). Top-ranked candidates: a due-concepts email digest and an Exam Date Countdown/paced-review feature over owned content only (explicitly not Smart Review Planning) — **both scoped into `v0.46.0`, see above**. **Second correction (2026-07-14, load-bearing):** Concept Flashcards (Idea 8) was briefly considered as a third v0.46.0 fold-in but turned out to already be a fully shipped feature (`docs/features/flashcards.md`, including a public preview) — Fable's write-up wrongly listed it as an area with no existing version. Dropped from consideration; do not re-propose it without checking `docs/features/flashcards.md` first. Photo capture of handwritten notes (Idea 6) remains the next recommended-but-unscoped idea — Core Feature-sized (new image upload + vision-extraction infrastructure), not a quick addition; **independently re-endorsed 2026-07-15** as the next Core-Feature bet, explicitly gated on the retention loop existing first (see below), not before. Full detail, corrections, and reasoning in `docs/claude-prompt/new-capability-session-plan.md` and `docs/claude-prompt/new-capability-out/01-new-capability-ideation.md`. **First correction, already applied and load-bearing:** the original top idea assumed NoteLib has no out-of-app re-engagement channel — false, a real retention-email system already ships (`docs/features/retention-emails.md`); the idea survives as a much cheaper addition to that system, not new infrastructure. Read the correction notes in the output file before scoping, not just the original Fable text. **Third correction (2026-07-15), the remaining 8 ideas re-evaluated against the retention diagnosis:** Idea 4 (Parent Readiness Digest) promoted to a conditional retention candidate, gated on the v0.48.0/H1 read — reframed as an external accountability trigger, not persona-completeness work; Idea 9 (Offline Study Pack Access)'s cost was overstated — a service worker and offline fallback already exist in the codebase (`frontend/public/sw.js`, `offline.html`), so remaining work is content-layer, not platform-layer, though it needs its own evidence leg beyond device mix (PDF export volume, offline-fallback hit rate) before it's a real trigger condition; Ideas 2 and 3 (teacher shared-results probe, class groups) folded into the Bulk Quiz Generation trigger condition below rather than tracked separately; Ideas 7 (Listen Mode) and 10 (Bilingual UI) unchanged, stay low; Idea 11 (Study Buddy) confirmed lowest — at 2.4% W1→W2, a pairing mechanic multiplies churn risk rather than countering it. Three items (Ideas 2, 4, 9) flagged as needing a real product decision before they're scopeable, not just a priority slot — full detail in `docs/claude-prompt/retention-diagnosis-session-plan.md`'s "Strategy checkpoint" Session B.

## Archived releases

Full scope for the following shipped versions moved to `docs/archive/ROADMAP_ARCHIVE.md` (see `RELEASES.md` for each changelog entry):

- `v0.35.0` - Mobile-First Builder
- `v0.36.0` - Readiness/Progress Merge
- `v0.36.3` - OCR Fast-Follow: Messaging & Feedback
- `v0.36.2` - OCR Disable Hotfix
- `v0.36.1` - Post-Release Fixes
- `v0.38.0` - Read-Path Optimization Pass
- `v0.37.4` - Idle GC & Metaspace Ceiling Hotfix
- `v0.37.3` - Study Plan Read-Path Memory Optimization
- `v0.37.2` - Plan Data Integrity Hotfix
- `v0.37.1` - Native Memory Hotfix
- `v0.37.0` - Readiness-First Plans & Mastery Integrity
- `v0.39.0` - Flexible Review Methods
- `v0.39.1` - Study Plan Builder Polish
- `v0.39.2` - Public Library Learning Experience
- `v0.40.0` - Weekly Study Plan (Exam Countdown) + Primary Review Set
- `v0.40.1` - Public Review Set Reachability

## Post-v0.40.0 Polish Backlog (7 items from live usage — candidates, not yet scoped)

Not a version — no release branch, no implementation scope yet. Surfaced by the user while using their own v0.40.0 release; captured here so the ideas don't drift/disappear before they're deliberately scoped. Research below was done via direct code trace (some Explore-agent verification hit a session limit and returned nothing — items are marked CONFIRMED where traced directly vs. PARTIAL where grounded but incomplete).

**Fold into v0.40.1 (no new item needed):**
- **Misleading "No curated Review Sets for X yet" empty state.** `dashboard-study-plan-section.tsx:150` shows this regardless of how many Review Sets the user already owns — it means "no *official curated* set for your track," not "you have none," but reads like the latter. Single-string copy fix on the exact same empty-state card v0.40.1 already rewires (see v0.40.1's "Rewire the existing empty state" bullet above) — fold in, don't ship standalone.

**Open philosophy question — blocks any related scoping until decided:**
- **Primary Review Set vs. the older Study/Exam Focus mechanism (`studyGoal`/`focusSubjects`, `docs/features/profile.md:85-108`) are unreconciled.** CONFIRMED via `progress-report-client.tsx`: on Progress they're already mutually exclusive at the view level (Primary, once set, fully supersedes the old goalSummary/milestones UI) — but the Profile page has **zero awareness of Primary Review Set** (no `primaryCollectionId` reference anywhere in `frontend/app/profile/`), so it still shows only the old per-subject Exam Focus picker, independently editable, with no cross-reference to the Primary the user separately set. Two "what am I working toward" fields, coincidentally similar-looking values, no link between them.
  - PARTIAL: whether `studyGoal`/`focusSubjects` also drive Dashboard, onboarding, or the exam-hub intent flow beyond Progress was not verified — don't assume "Primary wins" holds everywhere.
  - Recommended direction (lowest-drift, matches what Progress's code already does): Primary Review Set becomes the canonical surface; Study/Exam Focus is kept but reframed as the explicit fallback for users with no Goal, and Profile actually shows the Primary. Not purely additive — Study Focus is load-bearing as the no-Goal fallback, so this redefines what it means. Needs a deliberate product decision (user is taking this to GPT) before it becomes a roadmap item.
  - **Widened 2026-07-29, folded in rather than opened as a new item:** while discussing v0.63.0 Ask Companion's signoff, the owner asked whether the guidance layer (Companion) should ever reach users who never adopt a Review Set. A GPT second opinion, pressure-tested with Opus (see `v0.64.0 — Add to Review Set` section below), concluded Companion should not — but that the no-Review-Set learner's guidance surface is exactly what this open question's own "Study/Exam Focus as the load-bearing no-Goal fallback" direction already claims to own. Answering it a second time from the Note Detail side would recreate the same two-uncoordinated-answers problem this question exists to fix.
  - **Widened again 2026-07-29, same conversation:** a further GPT exchange proposed evolving "Ask Companion" into a cross-cutting "Companion" guidance *system* powering Dashboard/Review Set/Progress/Readiness with one voice. Pressure-tested with Opus (see the "Companion Guidance Doctrine" Backlog Index row below for the full resolution) — the taxonomy was adopted, the literal system-merge was rejected, and a unified "what should I do next" answer was found to require one canonical answer to "what am I working toward," which this question already owns. **This question now has three things waiting on its resolution** (Personalization's gate, the no-Review-Set guidance surface, and the Companion Guidance Doctrine's Phase 1) — a stronger argument for prioritizing the decision sooner rather than later.

**Cheap, independent candidates (sequence after the philosophy question above, since it touches the same Profile/Progress neighborhood):**
- ~~Show "Adopted" vs. "Created by you" on a Review Set~~ — **already shipped, v0.40.1** (`AdoptedBadge` in `collections-page-client.tsx`, equivalent treatment in `collection-detail-page-client.tsx`, both test-covered). This roadmap entry was stale — corrected 2026-07-15 while scoping v0.49.0, which briefly re-surfaced it as a candidate before direct code inspection found it already live. Showing the *original author's name* (like Public Notes' `authorDisplayName`) remains a real, separate, larger item — needs new backend exposure (no owner/author/official field on collection responses today) and a real edge case (source since deleted/private, no author left to show).
- **Overdue color-warning on child Subject-plan cards.** `child.dueConcepts` is already rendered as plain text on the Goal detail page (`collection-detail-page-client.tsx:660`) — no color coding today. Cheap, uses existing data. Flag: a warning *color* leans toward the "monitoring" framing the locked list/execution-row anti-drift rule pushes back on; defensible only because this is the Goal's own detail surface, not a list/browse card — needs a conscious sign-off, not an automatic yes. (Distinct from Phase 2 weighted-allocation scheduling above, which remains separately deferred and ungated.)
- ~~Make Progress's per-subject "Concept Mastery" cards link to Private Library filtered by subject~~ — **scoped into `v0.49.0`**, see below.

---

## Guided Learning Initiative (Companion) — v0.41.0 and beyond

Not a version — no release branch yet, planned to kick off after v0.40.1. Origin: a product realization surfaced across several planning discussions — Review Centers are not valuable because they provide PDFs or quizzes, they are valuable because they provide **guidance** (structure, direction, pacing, coaching, confidence). NoteLib has the knowledge layer (Notes), the learning engine (Study Packs), and the journey (Review Sets) — but no **guidance layer** riding on top of the journey. This does not turn NoteLib into an online Review Center; it adds one new layer while keeping Notes as the source of truth and Subject Plans strictly academic (no "Exam Strategies"/"FAQs" masquerading as Subject Plans).

**Success criterion (the north star every phase below is judged against):** *"Every Official Review Set should feel like a premium guided learning experience rather than a collection of notes."* Not feature count, not revenue.

**The organizing insight:** of the topics discussed (Companion, AI-assisted authoring, Companion regeneration, AI-generated Review Sets, creation/adoption/discovery UX, Public Library integration, runtime AI, monetization, profile-aware terminology), only **one is a genuinely new concept** — the **Learning Companion**, a persisted, curator-authored, profile-aware, statically-served guidance layer on a top-level Review Set. Confirmed by codebase audit: no entity carries authored narrative content on a collection today (guidance today is client-side ephemeral tips only, `frontend/lib/guidance-engine.ts` — no stored content). Everything else in the discussion is either an authoring enabler for the Companion, already on the roadmap under a different name, a deferred premium tier, or a cross-cutting naming constraint:

- **Review Set creation/adoption UX** — already shipped (Builder Canvas, adopt/adopt-goal) plus already-planned refinements (Post-v0.40.0 Polish Backlog's "Adopted" badge). Not new scope here.
- **Review Set discovery** — already scoped narrowly in `v0.40.1` (Browse All) and further gated in the deferred "Review-Set-Centric Navigation" section below. This initiative did not reopen that. **Superseded 2026-07-30:** the `v0.67.0` "Explore Owns Discovery" scope addition reopens navigation-level ownership now that Explore and Primary Review Set infrastructure are live; it does not add Note Detail recommendations, fuzzy matching, or new catalog data.
- **Public Library integration** — already shipped (v0.39.2 Flashcards/Memorization preview) plus the same deferred Explore-convergence direction below.
- **Runtime AI / personalization** — deferred premium tiers, gated on the Companion existing first (see Monetization below); does **not** violate the locked "no interactive AI / no mid-exam coaching" constraint in `EXAM_MODES.md` because the Companion MVP is authored static content, not a chatbot.
- **Profile-aware terminology** — a constraint, not a feature: any "Companion" label resolves through `getCollectionLabels` (candidate new field, e.g. `companionSingular`), same as `primarySingular`.

### v0.41.0 — Learning Companion (MVP), released (base branch `releases/v0.41.0`)

- **Persisted Companion content model.** A JSONB column on the top-level `note_collections` row (not a new table) — lowest-drift, mirrors the existing `sessionState` JSONB precedent, and copies naturally with the row on adopt. Promotable to its own table later if it grows. Companion is 1:1 with a top-level collection only (mirrors the `targetCompletionDate`/primary constraint — rejected on child Subject Plans, same `400` pattern as existing hierarchy validation).
- **Four sections only, deliberately small:** Overview, Study Strategy, Common Mistakes, FAQ. **Study Timeline and Final Checklist are explicitly deferred and must NOT be static prose** — when built (v0.42.0+) they link the already-shipped, already-free **live** features (the v0.40.0 weekly countdown and readiness), never re-author them. Resources/Updates sections deferred to v0.42.0.
- **Manual authoring only in v0.41.0** — no AI generation yet; there are few Official sets today so the authoring burden is trivial, and this de-risks the content model before layering LLM on top.
- **Official Companion authoring only (MVP scope decision, not a permanent limitation).** Only the NoteLib official author can author a Companion in v1; architecture stays open to any top-level-Goal owner later.
- **Publishes with the Review Set** — hooks into the existing `updateVisibility`/`publishChildCollections` publish cascade.
- **Travels on adopt, per the locked snapshot-copy rule (v0.31.0, below).** Companion is added to `persistAdoptedGoal`'s copied set (the way `targetCompletionDate` is explicitly *excluded* — Companion is the opposite, it should travel, like a linked Study Pack does on note copy). Source edits do **not** propagate to existing adopters (same as notes today). Owner self-copy **excludes** the Companion (same category as the existing generated-content self-copy exclusion).
- **FREE for all learners.** Zero paid uplift by design in v0.41.0 — this is an activation/retention bet, consistent with the success criterion being about experience quality, not revenue.

Anti-drift: no runtime LLM call to serve a Companion (authored once, served static — zero per-view cost); no new top-level entity; no change to the 5-mode quiz contract; no change to `UserEntity`; Companion label resolves through `getCollectionLabels`.

## Archived releases

`v0.41.1 - Review Set Detail Page: This-Set Study Dashboard` shipped; full scope moved to `docs/archive/ROADMAP_ARCHIVE.md` (see `RELEASES.md` for the changelog entry).

## Post-v0.41.0 Polish Backlog (candidate, not yet scoped)

Not a version — no release branch, no implementation scope yet. Surfaced by the user while using their own v0.41.0 release.

- **App-wide CRUD success-toast feedback.** Currently a Review Set-scoped pass (edit details, set/clear primary, Companion save/clear, create, delete) reuses the existing `ToastMessage` component (`frontend/components/ui/toast-message.tsx`) with local-state + `setTimeout` auto-dismiss, matching the pattern already used in 10 other files (profile, study, admin, etc.). Extending this to every mutating action across the whole app is a separate, larger initiative — the current pattern is per-page local state with no shared queue, so app-wide rollout needs a `useToast()` provider/hook first (concurrent toasts aren't handled today). Gate: scope and design the shared provider before starting; don't replicate local-state toasts file-by-file at app-wide scale.

### v0.42.0 — AI-assisted Companion authoring + regeneration, released (base branch `releases/v0.42.0`)

- **Curator workflow:** `Generate Companion` (per section or all) → LLM draft → **mandatory human review and edit** → `Publish`. Publishing is never autonomous. Reuses the existing OpenAI service + PREMIUM/CRITIQUE model tiers — no new LLM infra.
- **Granular per-section regeneration** (Overview / Strategy / FAQ / Checklist independently, not an all-or-nothing regenerate) plus a **"Companion may be outdated"** staleness signal when the set's structure changes — a lightweight stored structure snapshot (child count / note ids / concept count) compared on read, no new job infra.
- Adds the Resources section and the Timeline/Checklist live-feature embeds deferred from v0.41.0.

### v0.42.1 — Companion & Progress Polish, released (base branch `releases/v0.42.1`)

Small UX fixes surfaced from using v0.42.0 in practice, frontend-only, no new features:

- Merge the Review Set detail page's readiness card and its "View full progress"/"Review due concepts" row into one card — they're already documented as the same Readiness tier (see `docs/features/collections.md`), this just makes the layout match.
- Fix `/progress?collectionId={id}`'s backlink: it always showed "Dashboard" regardless of entry point; now returns to the originating collection when reached via that collection's "View full progress" link.
- Considered and declined: turning collection cards' course/program metadata into a badge — stays plain text per the existing badge-classification rule (identity/state get badges, metadata does not).

### v0.43.0 — Companion "Coach Experience", Released (base branch `releases/v0.43.0`)

Origin: a product proposal to make the Review Set detail page *feel* like a coach talking to the learner rather than a set of labeled CMS fields ("Overview", "Study Strategy", "Common Mistakes", "FAQ"). Pressure-tested via architecture review before scoping.

**Finding: the proposal's own mockup conflates three different operations — only one is genuinely "same content, new presentation":**
- **(a) Relabel/re-voice authored sections** (e.g. "Overview" → "🗺️ What this covers") — same curator text, friendlier frame, order preserved. Not generation (no per-learner synthesis, same author, same review-before-publish gate); "Curation, never generation" is not implicated. (Shipped headings stay descriptive of the section's content rather than becoming a generic greeting — an earlier "👋 Welcome back" draft for Overview was rejected mid-build for exactly that reason; see the "Coach vs. Companion" refinement below.)
- **(b) Reorder/prioritize authored sections by learner context** (e.g. surface Common Mistakes first when readiness is low) — **explicitly deferred**, not part of the near-term slice. Two problems: it collides with the monetization line below (adaptive prioritization is named PRO value), and it breaks curator-authored narrative flow — Overview → Strategy → Mistakes → FAQ assumes that reading order, and later sections can reference earlier ones.
- **(c) Coach-voice composition of already-shipped live signals** — target-date pacing, the resolved next action (`getNextPlanAction`), readiness/due-concepts (`ReadinessSummary`/ConceptHealth), and terminal exam/builder actions. None of this is Companion content; it's v0.41.1's dashboard signals, already FREE, just not yet wearing a coach voice.

**Near-term buildable slice = (a) + (c).** A static coach-label mapping (same shape as `getCollectionLabels`) over `CompanionDisplayCard`, order-preserving, plus a conversational frame composed from already-loaded live signals, positioned above Progress and the authored Companion (which stays a stable, unreordered narrative). Frontend-only, no new backend call, no new persisted state — same architectural class as `pickActiveGuidance` (`frontend/lib/guidance-engine.ts`) and `getNextPlanAction`, both deterministic and frontend-only already. Shipped as `TodaysFocusCard`, which merges the former countdown/primary-action/coach-intro surfaces into one Coach card, while Progress owns the countdown summary and Companion remains reference material.

**(b) stays deferred, reserved for future PRO personalization** (see "Future, gated — Runtime Companion" below). If ever picked up, the FREE-deterministic/PRO-adaptive line already drawn there applies: rule-based deterministic reordering could ship FREE like the weekly countdown did, but genuinely adaptive/learning-pattern/LLM-driven selection is the PRO differentiator — not a re-paywalled version of deterministic logic.

**Planned Scope:**
- Coach-voice terminology mapping over `CompanionDisplayCard` (order-preserving, no reordering).
- `TodaysFocusCard` in the Coach tier, above Progress and the authored Companion, driven by the already-resolved primary action (`getNextPlanAction`) plus target-date pacing and existing quick/terminal actions.
- Short curator-authoring guidance note in `docs/features/companion.md`.
- **"View Full Guide" collapse (added mid-release, see philosophy refinement below).** `CompanionDisplayCard`'s five sections stop rendering inline; they move behind a "View Full Guide" disclosure, below Today's Focus and Progress. Frontend-only, no new data, no new persisted state — same guardrails as the rest of this release.

Anti-drift: no reordering of authored Companion sections (the narrative-flow reason for this holds for today's long-form-paragraph content model — see the "Coach vs. Companion" refinement below for why that reasoning changes if content ever becomes atomic tips); no generation; no new backend, endpoint, or persisted state; does not reopen Timeline/Checklist as authored prose (stays live-feature embeds per v0.42.0); labels continue through `getCollectionLabels`.

### Coach vs. Companion, formalized (mid-release philosophy refinement)

Surfaced from using the shipped relabel + intro in practice: swapping section headings for coach-voice copy didn't fix the actual complaint. Five long-form paragraphs stacked under friendlier labels still reads as an article, not an app. The heading-copy lever is exhausted; the real lever is disclosure and interaction, not vocabulary.

**The organizing split, going forward:**
- **Coach (dynamic).** Reacts to the learner: continue-where-you-left-off, target-date pacing, readiness, due concepts, resolved next action, and existing terminal actions. This is not a new concept — it's naming what already exists (`TodaysFocusCard` plus `ReadinessSummary`). Zero new cost.
- **Companion (timeless).** Authored, does not react to daily progress. Teaches how to approach the curriculum — mindset, expectations, common mistakes, practical advice. Should read like mentor advice, not reference material.
- **Curriculum.** Subject Plans → Notes → Practice. Unchanged, not part of this discussion.

**A correction to this release's own anti-drift reasoning, recorded so it isn't re-derived wrong later:** the (b) reordering objection above cites two reasons — a PRO-monetization collision and a narrative-flow break. The monetization citation is imprecise in isolation: only *learning-pattern/LLM-informed* selection is the PRO differentiator (see Monetization philosophy, below); deterministic, rule-based selection (a date threshold, a progress count) is FREE-safe by the same precedent as the weekly countdown. The narrative-flow reason is the one that actually holds — and only because today's Companion is five long-form paragraphs where later sections can presume earlier ones were read. That reasoning is specific to *this* content shape, not a permanent rule; see v0.43.1 below for why it changes if sections become atomic tips.

**The verdict, split by cost:**
- **Cheap (this release, frontend-only):** the Coach/Companion naming above (free, just clarity), the `TodaysFocusCard` → Progress → Companion hierarchy, and the "View Full Guide" collapse (Planned Scope, above). Together these are the actual fix for "feels like documentation" — leading with what already exists and demoting the long-form article to reference material, reachable but not the first thing shown.
- **Expensive (a distinct initiative, not polish):** atomic, individually-surfaceable "Mentor Tips" with rotation and action-linking. This needs a new content shape — `CompanionContent`'s five long-form fields can't be "surfaced as a moment" without truncating curator intent. Scoped separately below as v0.43.1, since it is not frontend-only and does not fit this release's guardrails.

### v0.43.1 — Companion Mentor Tips, Released (base branch `releases/v0.43.1`)

Origin: continuation of the philosophy refinement above. Once the Companion is reachable via "View Full Guide" rather than rendered inline, the next question is whether the *authored* content itself can participate in the experience the way the Coach cluster already does — small, individually-surfaced, action-linked moments instead of an article to read start to finish.

**Why this is a distinct version, not a v0.43.0 fast-follow in the polish sense** (unlike v0.40.1, v0.41.1, v0.42.1, which were frontend-only fast-follows on their preceding `.0`): this needs a real content-model change, not a presentation change.

- **Content model.** `CompanionContent`'s five long-form markdown fields (`overview`, `studyStrategy`, `commonMistakes`, `resources`, `faq[]`) do not support an individually-rotatable, individually-linkable tip. A tip needs its own identity, optional linked action, and optional surfacing condition — a new entity/DTO shape, not a frontend read of existing fields. This is the fulcrum: everything past this point is backend + authoring-UI scope, which is why it cannot fold into v0.43.0.
- **Authoring.** The authoring modal, v0.42.0's per-section AI-assist, and the structure-staleness snapshot all need to extend to the new shape. Mandatory human review before publish still applies — no change to "Curation, never generation" or "publishing is never autonomous."
- **Action-linking is curator-tagged, not inferred.** `TodaysFocusCard` already links *Coach* signals to actions — that part exists. Linking a curator's *authored* tip to an action (e.g. "you still have due concepts" → Review due concepts) must be a field the curator sets when authoring the tip, not something inferred at render time — inferring it would require a per-view LLM call, which v0.41.0 explicitly ruled out ("authored once, served static — zero per-view cost"). This constraint and the cheap/compliant path are the same path: deterministic, curator-tagged linking.
- **Surfacing stays deterministic.** "Show this tip within 2 weeks of the exam date" or "after N subjects completed" are date/progress rules, not learning-pattern inference — FREE-safe by the weekly-countdown precedent, not a PRO feature. Nothing here requires the adaptive/LLM-driven selection that Monetization philosophy (below) reserves for PRO.
- **"View Full Guide" stays a permanent escape hatch.** Whatever rotation/surfacing logic ships, the full authored Companion must remain reachable regardless of which triggers have fired — a learner should never permanently miss a curator's warning because its surfacing condition never happened to trigger for them.
- **Volume caveat.** Per the original Companion MVP scoping, there are still few Official Review Sets today. A "show another tip" affordance over a two-tip guide will feel hollow — this feature's perceived value scales with authored tip volume, which curators have not yet been asked to produce at this grain. Worth an explicit go/no-go check against actual authored-content volume before or during kickoff, not an assumption.

**Go/no-go check, done at kickoff (2026-07-10):** dev DB query found only 1 PUBLIC/Official top-level Review Set carrying an authored Companion (2 companions total across 7 top-level collections; the other sits on a PRIVATE collection). Decision: proceed anyway — dev/local volume is not necessarily representative of prod, and the content-model/authoring-UI work has standalone value independent of how many tips exist on day one. Recorded here so this isn't re-litigated as a fresh concern mid-release. Full scope in `RELEASES.md`.

**Scope broadened mid-release (2026-07-10):** a pre-signoff "tighten these new features" audit surfaced a real trust bug — Pro-only paywalls (Board Exam Mode, Long Exam, Difficulty Selection, Interview Practice) let a Free/Plus user select and pay for Plus without unlocking the feature — plus two lower-stakes gaps: no paywall upsell existed for the Plus/Pro-gated per-concept review-timing detail, and the Help Center had no coverage for this cycle's Companion/Coach/Mentor Tips or Primary Review Set/target-date pacing features. All three landed as additional `v0.43.1` Shipped bullets rather than a separate version, per the same mid-release-fix precedent as v0.42.0's `setCompanion` null-content guard. Full detail in `RELEASES.md`.

### Documented rule clarification (not a reversal) — enables AI-assisted authoring

The standing "Curation, never generation" rule (see v0.31.0 below, "the forbidden version is *auto-generate a personalized plan* — do not build that here") is **clarified, not reversed**, to make v0.42.0 possible:

- **Learner-facing: unchanged.** Curation over generation — a learner never gets an auto-generated plan.
- **Curator-facing (new, scoped to Official Review Sets/Companions): AI-assisted authoring, with mandatory human review before publish.**
- **Publishing: never autonomous**, in either case.

This is the same category of deliberate, written rule refinement as v0.33.0's Progress-separation reversal — recorded here so it is never mistaken for scope creep or relitigated later.

### Future, gated — AI-generated Review Sets

Curator pipeline: public notes → suggest Subject Plans → map notes → generate Companion → human review → publish. A separate, larger initiative — gated on v0.42.0's authoring-assist pipeline proving out (this reuses that pipeline rather than building a second one) and on the rule clarification above. Not scoped to a version yet.

### Future, gated — Runtime Companion (Ask Companion, Personalization)

- **Ask Companion (PLUS). Ratified 2026-07-29, kicked off as `v0.63.0` — see this doc's own "v0.63.0 — Ask Companion" section above for the signed-off v1 scope (quota, turn cap, model, access tier).** Grounded Q&A over the authored Companion content — cheap, bounded, reuses the existing Interview Practice cost-control template (feature gate + monthly quota + per-minute `AiRateLimitService` + the cheaper CRITIQUE model + capped turns), the only existing runtime/interactive LLM feature in the product today. Deliberately placed at PLUS (not PRO, where Interview Practice's PREMIUM-model generative simulation lives) because it is grounded retrieval over static content, not generation — a documented, deliberate ladder repositioning.
- **Personalized/Adaptive guidance (PRO). Not ratified — explicitly excluded from the 2026-07-29 Ask Companion decision, stays Parked.** Must be genuinely adaptive (learning-pattern/LLM-driven) — **not** the existing deterministic v0.40.0 weekly countdown re-labeled with a price tag, which already shipped FREE.
- Both gated on the persisted Companion existing — **satisfied** (shipped v0.41.0/v0.42.0, confirmed live in the codebase), which is what made Ask Companion decision-ready. Personalization is additionally blocked by the open Primary-Review-Set-vs-Study/Exam-Focus philosophy question (Post-v0.40.0 Polish Backlog, above) — that question is about Profile/Progress, not the Companion, so it did not gate v0.41.0/v0.42.0 and does not gate Ask Companion, but it does still gate Personalization specifically.

### Monetization philosophy (long-term principle, established here for future features to follow)

Codifies what is already the de facto model in this codebase (readiness was ungated to FREE in v0.33.0 as "access not billing"; interaction-heavy features already sit at PRO) rather than redesigning pricing:

- **FREE — static guidance.** The Companion itself. Near-zero marginal cost (authored once, served static), high perceived value — the activation/retention driver and the conversion hook for paid interaction/personalization. Not a giveaway; a funnel.
- **PLUS — interaction.** Ask Companion. Gives PLUS its first genuinely distinct capability (today PLUS is quota-only) — strengthens PLUS's reason to exist.
- **PRO — personalization.** Genuinely adaptive guidance, not a re-paywalled version of something already free. **Not any prioritization** — deterministic, rule-based reordering (e.g. "surface Common Mistakes first when readiness is low," see Companion "Coach Experience" candidate above) follows the same FREE precedent as the v0.40.0 weekly countdown. PRO's prioritization must specifically be adaptive/learning-pattern/LLM-informed selection. Much of that adaptive value is derivable from existing ConceptHealth with no per-query LLM for the underlying signal — high margin; only the conversational/adaptive selection logic itself carries recurring cost, controlled via the Interview Practice template.
- Applies consistently across profiles (Student/Exam-taker/Teacher/Professional) because it gates capabilities, not content, and all labels route through `getCollectionLabels`.
- No price/quota/checkout change from this principle alone — it governs how *future* features (Ask Companion, Personalization) get tiered when they're built, not a repricing of today's plans.

---

## Review-Set-Centric Navigation (navigation shape reached 2026-07-30)

Originally captured as a future direction rather than a release. **Gate satisfied 2026-07-30:** the Primary Review Set concept proved useful through the shipped Dashboard, Progress, and Collections flows. Progress promotion shipped in `v0.59.0`; `v0.67.0` then reached the drafted navigation shape with a profile-aware Collections label and Explore as the shared discovery front door. The same-day "Explore Owns Discovery" addition makes that ownership explicit without absorbing Library or deleting the standalone public catalog routes.

**Earlier partial exception (v0.41.1, released):** the Review Set detail-page hierarchy (Identity → Current Journey → Primary Action → Readiness → Guidance → Subject Plans/Notes) and the matching `/collections` list-card Primary treatment shipped as a narrow, frontend-only re-composition — see the "v0.41.1" section above. The later `v0.59.0` and `v0.67.0` releases advanced the Dashboard/Progress/Explore navigation parts that were still gated then.

Origin: structural realization that Review Sets have become NoteLib's primary study experience, not a secondary feature alongside Public Library/subjects. When the product was designed, users mostly discovered notes through the Public Library, so Dashboard, Progress, and Exam Hub were all built subject-first. That assumption no longer holds now that Official Review Sets, Subject Plans, smart progression, readiness, and (v0.40.0) weekly plans exist.

Direction, as stated by the user:

- **Official Review Set catalog as the scalable replacement for hand-built per-profession pages.** Publishing another Official Review Set should require no new frontend, versus adding an Exam Hub page per profession (Civil/Electrical/Mechanical Engineering, Nutrition, Midwifery, …).
- **Public Library preserved as a distinct discovery path.** "I want to browse notes" (Public Library) and "I want to study for an exam" (Review Sets) are two valid entry points to the same notes; the Public Library is not absorbed or removed.
- **Dashboard and Progress reorganized around the Primary Review Set** instead of subject/course-program. Dashboard asks "what review are you preparing for?"; Progress answers "what's happening with my primary study journey?" Subject mastery still exists — it becomes a facet of the Review Set rather than the primary navigation.
- **Reached nav shape:** Dashboard / profile-aware `getCollectionLabels().navLabel` / Library / Explore / Progress, where Explore houses the Official Review Set catalog and Public Library. No literal "My Reviews" label was introduced.

Corrections recorded from the codebase research behind this (so they aren't relitigated later):

- **Exam Hub is not the maintenance burden the original framing assumed.** It is a single dynamic `/exam/[slug]` route over a small hardcoded config (`frontend/lib/exam-hub-config.ts`), and it does a distinct job an authenticated catalog does *not* replace: **anonymous / SEO acquisition** (organic search → signup; authenticated visitors route into a filtered Public Library). The recommended resolution is **convergence, not deletion** — an Explore surface that houses the Official Review Set catalog + Public Library, and lets the existing `/exam/[slug]` SEO pages deep-link into a matching Official Review Set once one exists. Retiring the SEO surface would cost organic acquisition.
- **Naming stays profile-aware.** Any "Primary Review" / "My Reviews" language must resolve through the existing `getCollectionLabels` pattern, not ship as hardcoded universal copy — the concept is profile-agnostic; the label is not (Study Plan / Review Set / Lesson Plan / Collection).
- **Progress reorg is a default-view change, not a re-scoping.** The `PlanPicker` + `?collectionId=` machinery already exists; a future reorg defaults it to the primary and must keep the all-subjects rollup reachable so notes outside any Review Set aren't orphaned, and must not undo the v0.36.0 Progress/Readiness unification.

---

## Note Detail readiness as its own tab (candidate)

Idea surfaced while fixing v0.36.1's Note Detail readiness placement: instead of showing the readiness summary inline (currently just before Performance Overview on every tab), give it its own tab alongside Summary / Key Concepts / Quiz / Full Notes.

Blocker: the current 4-tab bar already fills the width of a standard iPhone viewport exactly. Adding a 5th tab would overflow and needs a scroll/overflow affordance (e.g. an arrow or horizontal scroll) so the added tab isn't silently hidden on mobile — that affordance needs its own design pass, not a fast-follow. Not scoped into any release yet.

---

## Archived releases

Full scope for the following shipped versions moved to `docs/archive/ROADMAP_ARCHIVE.md` (see `RELEASES.md` for each changelog entry):

- `v0.33.0` - Study Plans as a Retention Engine
- `v0.33.1` - Study Plan polish & Curated Plan Coverage
- `v0.33.2` - Plan Detail Redesign (view/edit split)
- `v0.33.4` - Builder Surface Clarity
- `v0.33.3` - Recursive Goal Adopt

## Deeper plan nesting — study-plan-within-a-study-plan (candidate, nice-to-have)

The 2-level Goal → Subject model is intentionally constrained. Going to 3+ levels is **feasible but a real project, not a constraint flip** (`parent_collection_id` is self-referential so the *column* supports depth, but every shipped invariant assumes 2): N-level needs real ancestor-walk **cycle detection** (today 2-level makes cycles impossible by construction); **recursive readiness rollup** (today sums *direct* children; the no-cross-subject-dedup rule gets thornier each level); **adopt-recursion**; per-level `sibling_position`; and a tree/breadcrumb builder UX. Genuinely nice-to-have, later — see `docs/archive/STUDY_PLAN_HIERARCHY_PLAN.md`.

---

## Archived releases

Full scope for the following shipped versions moved to `docs/archive/ROADMAP_ARCHIVE.md` (see `RELEASES.md` for each changelog entry):

- `v0.34.0` - Journey: Goal-First Study Experience
- `v0.29.0` - Bulk Generation & Generation-Context Correctness
- `v0.29.1` - Bulk Generation Polish
- `v0.30.1` - Copy Flow Polish
- `v0.30.0` - Readiness Signals
- `v0.31.0` - Adoptable Study Plans (v1)
- `v0.31.1` - Adoptable Study Plans Discovery & Status
- `v0.31.2` - Analytics Integrity & Funnel Visibility
- `v0.32.2` - Conversion Diagnosis & Quota Honesty
- `v0.32.1` - Monetization Surfacing & Pricing Clarity
- `v0.32.0` - Account & Communication Controls

## Bulk Quiz Generation & Teacher-Flow Polish (candidate, gated on teacher users; version number TBD — do not confuse with the shipped v0.35.0 - Mobile-First Builder)

Theme: reduce the friction of turning material into quizzes. Builds on the v0.27.0 collections spine and the v0.29.0 bulk-generation foundation. **Deferred (was v0.33.0, before that v0.32.0, earlier v0.31.0, before that v0.30.0, originally v0.29.0)** — we have no teacher users yet, so this only schedules once a teacher cohort exists; it may slip further. (v0.33.0 was repurposed for the Study Plans retention work above.) **Honest remainder after v0.29.0:** v0.29.0 builds the shared batch-orchestration + quota foundation for bulk *content* (note + Study Pack) generation from topics; this release extends that to **collection-level bulk *quiz* generation over existing notes** plus async quiz generation, and bundles three teacher-flow quiz-preview polish fixes. Make quiz generation async (like the Study Pack pipeline), then add a collection-level bulk action that batches the universal per-note pipeline.

**Formalized as an explicit trigger condition, 2026-07-15** (post-v0.48.0 Fable strategy checkpoint, `docs/claude-prompt/retention-diagnosis-session-plan.md`): the 5-consecutive-deferral pattern was confirmed as correct — teachers can already generate quizzes per-note, so missing bulk-batching isn't what's blocking teacher adoption; zero teacher users is a positioning/distribution problem, not a feature gap — but re-litigating the same decision every release cycle is real waste. This now **auto-schedules once ≥5 active teacher accounts exist**, and is out of per-release scoping consideration until that threshold is hit. **New Capability Ideation's Idea 2 (teacher shared-quiz-results probe) and Idea 3 (class groups) fold into this same trigger, not tracked as separate candidates** — when the threshold fires, Idea 2 (cheapest demand test) ships first, and its result decides between this item and Idea 3.

Locked direction:

- **The universal spine is preserved.** Every profile still generates a Study Pack before a quiz — this is intentional, not a funnel to remove (consistency across easy profile switching + uniform quota). Bulk solves the friction by *batching* the same pipeline, never by forking a profile-specific shortcut.
- **Profile-aware framing, not a fork.** Teacher emphasizes quizzes, Student study packs, via the existing Study/Exam Focus copy mechanism — never per-profile pipeline branches or hardcoded `if (TEACHER)` checks.
- **No new quota category, no collection-level AI synthesis.** Each note spends one existing per-note credit per artifact; bulk is a fan-out of per-note generation, not a synthesized collection document (Option B stays deferred).
- **Bulk is explicit, not an import side-effect.** One deliberate user click for the batch; preserves the explicit-generation rule. DOCX export and the multi-note Exam Builder stay Teacher/Admin-only; the old share-link profile restriction was superseded by `v0.89.0`, which makes individual generated quiz links available to any onboarded owner.

Scope:

- **Teacher quiz-preview polish** — move the ⋯ context menu to the top-right of the note title; remove the redundant "Correct Answer" panel (the choice already shows a ✓ Correct badge + highlight, no a11y loss); render the question stem through `QuizQuestionText` so `Statement N:` lines break onto separate lines (the teacher preview is the only quiz view rendering raw stem text).
- **Async quiz generation** — mirror the Note → Study Pack pipeline: status field (`GENERATING` / `READY` / `FAILED`), task-executor enqueue, frontend polling. `GeneratedQuizService.generate()` is currently synchronous. Prerequisite for bulk.
- **Collection-level bulk generation** — batch the universal per-note pipeline across a collection. The hard part is **quota-aware partial execution**: generate as many as quota allows, report completed vs. blocked, upsell — never fail the whole batch.

---


---

## Archived `Current Release Baseline` paragraphs — `v0.122.0` and earlier

**Moved here 2026-09-07 at the `v0.128.0` signoff.** These are the per-version paragraphs that lived in
`docs/product/ROADMAP.md`'s `## Current Release Baseline` section, which had reached **246,685 characters
naming 602 version references** — structurally the same unguarded growth the `v0.126.0` pass removed from
`CLAUDE.md`. Live `ROADMAP.md` now keeps the current version plus the last five, per the signoff rule.

**⚠️ THIS IS A MOVE, NOT A DELETE — content is verbatim and byte-for-byte identical to what was removed.**
`RELEASES.md` and `docs/archive/RELEASES_ARCHIVE.md` remain the changelog of record; these paragraphs are
the planning-narrative layer beside it.

**Previous: `v0.122.0 — Shared Quiz Discoverability` is RELEASED** on `releases/v0.122.0`, cut from `main` after `v0.121.0` merged and tagged.

**⚠️ IT EXISTS TO MAKE `v0.121.0`'s OWN CHECKPOINT ANSWERABLE — A DEPENDENCY, NOT A THEME.** `[CHECKPOINT — due 2026-10-07]` asks whether the shared-quiz capability has been **promoted at all**, and its kill criterion states that without promotion the distal row (`[CHECKPOINT — due 2026-12-04]`, carrying `v0.121.0`'s entire thesis) is **RE-DATED, never answered.**

**⚠️ SLICE 5 WAS THE ALTERNATIVE AND WAS DECLINED ON EVIDENCE (owner, 2026-09-06).** A read-only production query returned **1 `linked_learner_relationships` row, 1 `ACCEPTED`, 2 invitations, 3 live grants** — so slice 5 would have enriched a **supporter** surface for a population of one, and added a **second** unpromoted surface inside the checkpoint's window.

**⚠️ THE SCOPE LINE IS SET BY A DATED READ, NOT BY TASTE: `[CHECKPOINT — due 2026-09-19]` IS THIRTEEN DAYS OUT AND ITS KILL CRITERION KEYS ON `ACCEPTED`, so promoting Learning Connections now would contaminate the one number deciding whether that arc continues.** This release promotes **shared quizzes ONLY**; Learning Connections promotion unblocks after `2026-09-19`.

**⚠️ NOT `v0.109.0`'s STALE-GUIDE SHAPE — but the kickoff's *"Help Center is already correct"* claim was HALF WRONG and is corrected here rather than left standing.** `export-sharing-guide.tsx:18` was correct. **`learning-connections-guide.tsx:35` — the very line the kickoff cited as evidence — read *"From a note's actions menu, choose Quiz for someone"*, but that menu item is gated `!isTeacherMode`, so it instructed teachers to use a control they do not have.** Found by **sweeping by surface** at signoff (the file was not in the release diff), fixed to name both controls, and pinned. **⚠️ The kickoff's conclusion still holds — no guide REWRITE was needed — but "verified" had been asserted from a line number rather than from the sentence.** **The measured gap is in-app announcement and marketing:** eleven guidance tips and **none** mention sharing a quiz; `app/page.tsx` names Board Exam, Interview Practice and Adaptive Practice and shared quizzes **zero** times. **The entry point works — nothing needs wiring.**

**SCOPE: (1)** a one-time note-detail tip through `pickActiveGuidance()`, modelled verbatim on `assessment-covers-whole-plan` including its comment, **with NO action button**; **(2)** the landing and how-it-works copy names the capability. **⚠️ ANTI-DRIFT: no second entry point; no Learning Connections promotion (including NOT moving it from `secondaryNav` to `mainNav`); no behaviour change; no migration; no quota or meter change; `frontend/app/onboarding` STAYS FROZEN (`[CHECKPOINT — due 2026-09-11]`, FIVE DAYS OUT); do NOT change `QUIZ_SHARE_LINK_OPENED`/`QUIZ_SHARE_LINK_COMPLETED` firing conditions, which `[CHECKPOINT — due 2026-12-04]` reads.** **VERIFICATION: a single `advisor()` call.** **Routing: CLAUDE CODE inline.** Full scope and anti-drift rules in `RELEASES.md`.

**Previously — kicked off and signed off 2026-09-06.** `v0.121.0 — Shared Quiz Recipient Experience` is **RELEASED** on `releases/v0.121.0`, cut from `main` after `v0.120.0` merged and tagged. **SLICES 1-4 of `docs/claude-plans/shared-quiz-recipient-experience-plan.md`** — its **§8 supersedes** the companion `docs/claude-plans/supporter-progress-visibility-audit.md` where they differ.

**⚠️ THE SEQUENCING SECTION BLOCKED THIS ON AN OWNER CONVERSATION AND THAT CONVERSATION HAPPENED AT KICKOFF — §1's three contradictions are RESOLVED and must not be re-opened mid-implementation.** (A) combined quizzes have no source-note identity; (B) shared quiz results are never recorded, and recording stays OUT of scope; (C) no readiness history exists, so a trend is impossible.

**⚠️ THE JUSTIFICATION IS FIX-BEFORE-PROMOTE, AND IT IS MEASURED RATHER THAN ASSUMED.** A read-only production query on 2026-09-06 returned **8 `generated_quizzes` from ONE distinct generator** (2026-04-17 → 2026-09-05), **1 `quiz_share_links` row** (active, single-note, source note `PUBLIC`) and **0 `combined_quizzes`** — the last being **uninformative**, since `v0.110.0` shipped that capability two days earlier. So the §2 defects are confirmed in code and reachable but are harming no population yet: **the `v0.109.0` shape, where a read on an unpromoted capability measures DISCOVERABILITY, not demand.** **⚠️ CONSEQUENCE OWED AT SIGNOFF: a checkpoint on recipient behaviour has a denominator near ZERO and needs the TWO-TIER design — a proximal rate carrying the kill criterion, plus a distal outcome whose denominator clause makes *not yet measurable* a RE-DATE rather than a verdict.** **⚠️ This is not an argument for skipping the release; fixing a recipient path before promoting it is the correct order.**

**⚠️ SLICE 1 WAS PROVEN REACHABLE AT KICKOFF RATHER THAN INFERRED FROM THE DTO** — `teacher-quiz-developer.txt:23-26` explicitly instructs the model to emit a MATCHING block, `generated_quizzes` is populated through that very prompt (`GeneratedQuizService:126`), `PublicQuizItem` drops `questionGroup`, and the recipient page branches on `isMultiSelect` alone. **This matters because `v0.116.0` and `v0.117.0` each shipped a guard whose subject could never fire.**

**⚠️ OWNER RULING 2026-09-06 ON SLICE 4, RECORDED AS A CAPABILITY RULE RATHER THAN A PRODUCT DISTINCTION: continue-learning may surface a source ONLY when there is DURABLE source-Note identity AND the source is legitimately accessible to the recipient at read/result time; NEVER infer identity from a copied title.** `generated_quizzes.note_id` qualifies, `CombinedQuizSection` does not. **Implement it as a provenance capability — `sourceNotes` is a LIST populated server-side from `visibility == PUBLIC` sources only, so a combined quiz yields an EMPTY list with no `isCombined` branch anywhere.** **⚠️ Private sources are omitted entirely — not counted, not hinted, not placeheld — and NO sharer-facing warning copy is added (owner, explicitly). Combined source identity is a Known limitation and a follow-up.**

**⚠️ SLICE 5 (Learning Connection integration) IS ITS OWN FOLLOW-UP RELEASE.** Full scope, anti-drift and pre-declared guards: `RELEASES.md`.

Previous: **Kicked off and signed off 2026-09-06.** `v0.120.0 — Canonical Note Title Integrity` is **RELEASED** on `releases/v0.120.0`. **⚠️ CUT FROM `docs/next-sequencing`, NOT FROM `main`** — PR #1300 is blocked by a repository ruleset (`require_extra_approval_for_unattributed_changes`, ruleset `17016892`, read via the API and **not** probed with a push), so merging needs an approval or `--admin`, and **bypassing a branch protection is the owner's action.** The sequencing commit rides into `main` through this release's PR instead, as `v0.111.0` did; **expect #1300 to be closed unmerged.** **⚠️ THIS ALSO CORRECTS A STALE `CLAUDE.md` CLAIM** — that file has said since `v0.103.0` that main's protection was *"BY CONVENTION"* and the enforcement claim *"FALSE"*; a ruleset now exists, and `CLAUDE.md`'s own instruction to correct the line back rather than delete it is discharged in the kickoff commit.

**THE DEFECT, REPRODUCED AND OWNER-REPORTED:** a curator entered `Site Grading Principles` in Bulk Generate and the persisted Note title became `Site Grading Principles in Civil Engineering`, with Civil Engineering **not** among the four selected Applicable Programs. Governed by `docs/claude-plans/canonical-note-title-generation-integrity-stage1.md` — **EXECUTE ITS DECISIONS, DO NOT RE-DERIVE ITS SETTLED FACTS.** **⚠️ THE CAUSE IS NEITHER SUSPECT: not context contamination (§3) and not a missing prompt rule (§4 — `developer.txt:32-38` already carries the correct doctrine and was violated anyway).** It is **one unconditional mutation**: bulk creates the Note with the curator's topic (`NoteBulkGenerationService:322`) and then overwrites it with the STUDY PACK's title at `StudyPackService:1301`, because `:350-356` always passes a non-blank `preservedSubject`. Its sibling `applyGeneratedMetadataToNote` never touches the title, **so bulk is the anomaly and the fix DELETES a mutation rather than adding logic.** **⚠️ ORDERED FIRST ON AN ORDERING ARGUMENT, NOT SEVERITY:** `v0.118.0`'s locked contract is *"the Note's current title is the topic"*, so a contaminated title **seeds every future regeneration** of that note — fix titles before regenerating the canonical library, or the ALE pass is redone. **⚠️ EXISTING NOTES ARE SAFE** — `NoteBulkRegenerationService:570` passes `preservedSubject = null` behind a load-bearing comment; the exposure is authoring NEW notes.

**THREE OWNER DECISIONS TAKEN AT KICKOFF (2026-09-06), two AGAINST the audit's recommendation and marked as such.** **(a) The curator topic always wins** — the core fix — **PLUS an opt-in title suggestion**, taken against the audit's *no suggestion action*. **⚠️ IT IS AFFORDABLE BECAUSE IT IS MOSTLY BUILT: `private-note-detail-page-client.tsx:534-560` already offers the generated title through `AiSuggestionModal`, and the generated title is ALREADY PERSISTED (`StudyPackEntity:46`, exposed by `StudyPackResponse.title`) — so NO MIGRATION.** What is missing is the bulk case, and **it cannot reuse the modal**: the mechanism is armed only from the note detail page (`:876`, `:1372`), bulk never arms it, and **a 50-item batch cannot show 50 modals** — so the bulk affordance is **persistent and non-modal**, derived from `note.title !== studyPack.title` rather than from bulk provenance. **(b) Keep LLM tags, falling back to the curator topic** (the audit's recommendation) — **⚠️ narrower than it reads: `resolveTags:1075` uses `fallbackTitle` ONLY when the LLM returned no tags, and `resolveTags` itself is unchanged, so the audit's escalation trigger does not fire.** **(c) The narrowed title-input contract is DEFERRED** — the owner first took it in, and **reading `developer.txt:6` showed there is no title-producing call to narrow**: title is field 1 of the single JSON schema the whole generation returns. Narrowing forks into an **extra LLM call on every generation** or **narrowing body generation too** — which would strip `domainContext`, *the sole LLM domain constraint* per `ADR-001`. **⚠️ Deferred with that finding attached so it is not re-derived; see its Backlog row.**

**⚠️ ANTI-DRIFT:** do NOT add Course/Program suffixes by default or infer a title from Applicable Programs, Review Set, Subject Plan or Domain Context; **do NOT remove legitimate disciplinary qualifiers** (a banned-substring filter breaks every right-hand case in the audit's §8 table); do NOT weaken `developer.txt:32-38`; **do NOT mass-rename existing notes or regenerate Study Packs to repair titles** — the existing debt is unmeasured by decision and its sizing read is the owner's to run; **no migration, no new endpoint, no quota/entitlement/meter change; `frontend/app/onboarding` STAYS FROZEN** (`[CHECKPOINT — due 2026-09-11]`, 5 days out). **VERIFICATION: a single `advisor()` call on the diff**, escalating to one scoped cold agent if the suggestion grows a backend endpoint or provenance field. **⚠️ PRE-DECLARED GUARDS: assert the PERSISTED title, not the create call — `NoteBulkGenerationService:322` is already correct today, so a creation-time assertion passes under the defect; and the fixture's generated title MUST DIFFER from the topic.** **Routing: CLAUDE CODE inline.** Full scope in `RELEASES.md`.

**Previously — kicked off and signed off 2026-09-06.** `v0.119.1 — Public Catalog Bounds` is **RELEASED** on `releases/v0.119.1`, cut from `main` after `v0.119.0` merged and tagged. **A PATCH closing the THIRD OCCURRENCE OF ONE SHAPE** — a latent unbounded fetch over the public catalog crossing a threshold as data grows (2026-09-01 build failure, 2026-09-04 outage, 2026-09-05 outage). Governed by `docs/claude-plans/2026-09-05-public-catalog-unbounded-read-fix-plan.md`. `GET /notes/public` is **anonymous** and does work proportional to the **whole catalog** (**1,442** notes, up from ~950 in August), holding a pooled connection for all of it; the health check then starves on the same pool and the platform restarts a process whose only problem is that it is busy. **⚠️ THE HOLD IS INSIDE THE TRANSACTION — NOT open-in-view.** **ALL THREE LEGS (owner):** (B) the two callers that never needed a catalog move to `count(*)`+`LIMIT`; (A) bound the server-side work, including the **DEFAULT `RECOMMENDED`** sort which takes the unbounded path even when paginated; (C) the health group **excludes `db`**. **⚠️ TWO OWNER DECISIONS STATED RATHER THAN ABSORBED: `sort=recent` for the related-notes call is a VISIBLE product change on ~250 SEO pages, and excluding `db` also keeps an instance alive serving errors when the database is genuinely gone.** **⚠️ Do NOT raise `maximum-pool-size` — already raised 10→20 and the same failure recurred at 20.** **VERIFICATION: one scoped cold agent for Leg A; `advisor()` for B and C.** Full scope and anti-drift in `RELEASES.md`.

**Previously — signed off 2026-09-06.** `v0.119.0 — Curator Bulk Regeneration` is **RELEASED** on `releases/v0.119.0`, cut from `main` after `v0.118.0` merged and tagged. **All five slices shipped (B1-B5, including retry), plus a production diagnosis folded in and the full three-agent pressure test's findings closed.** **⚠️ THE PRESSURE TEST FOUND SIX CONFIRMED DEFECTS AND STILL MISSED THE ONE THE OWNER HIT FIRST** — both JSON POSTs sent no `Content-Type`, so the feature could not make a single successful request while 2,182 frontend tests passed. **`CLAUDE.md`'s verification tiers now record that every tier reads code and none exercises transport**, and a new endpoint owes one real-request test at every tier. **⚠️ TWO CHECKPOINTS OWED AND WRITTEN (`2026-10-06`): curator adoption + whether a TEACHER's allowance survives one real batch, and whether the receipt's 24 h TTL is too short. Instrumentation (`BULK_REGENERATION_STARTED`) was added AT SIGNOFF because the gate caught that no durable signal existed at all** — the per-item record is a receipt with a 24 h TTL, and nothing else distinguishes a bulk-regenerated note from a single-note one. **PHASE 2 OF THE REGENERATION ARC**, governed by `docs/claude-plans/curator-bulk-regeneration-stage1.md` — **EXECUTE ITS DECISIONS, DO NOT RE-DERIVE THEM.** **⚠️ IT DELIBERATELY OVERRIDES ITS OWN PLAN'S §14 SEQUENCING, AND THE OVERRIDE IS RECORDED RATHER THAN ROUTED AROUND.** §14 requires Phase 1 to have **deployed** and been **observed on real canonical Notes** before Phase 2 opens; `v0.118.0` was tagged minutes before this kickoff and has been observed on none. **The owner overrode it 2026-09-05 for a stated reason that is not impatience: updating the ALE Review Set is BLOCKED on bulk regeneration**, so the sequencing cost is being paid by stalled curator work. **⚠️ A later session reading §14 in isolation must come here first.** **⚠️ ACCEPTED RESIDUAL: B1's per-item job record was meant to be shaped by observed Phase 1 behaviour and will instead be shaped by Phase 1's code and tests — if production contradicts that design, it is a RE-OPEN, not a surprise.** **⚠️ OWNER DECISION 1 TAKEN AT KICKOFF AGAINST THE PLAN'S RECOMMENDATION, AND IT MOVES THE VERIFICATION TIER: ADMIN **AND TEACHER** curators, not ADMIN only.** A single action now spends metered units on a PAID account — up to 50 note-generation and 50 Study Pack units — which is a **money-semantics change** and fires the full three-agent trigger the plan's escalation clause names. **⚠️ THE SUB-DECISION IS ANSWERED: TEACHERs are METERED NORMALLY under the settled block-and-reduce policy, and the ADMIN-only quota BYPASS IS NOT WIDENED** — a TEACHER's batch is capped by their remaining allowance, the preflight must say so before they commit, and **no entitlement, plan-tier or limit changes.** Decisions 2 and 3 take the plan's recommendations: **batch cap 50 under its own config key**, and **no cancellation in v1** — do not offer a control implying in-flight LLM work stops. **⚠️ TWO BLOCKERS MUST BE SOLVED AND BOTH ARE ARITHMETIC: the existing receipt CANNOT report a batch this long and must be REPLACED, not inherited; and the batch loop occupies one of only TWO threads on the same executor each item is dispatched onto — do NOT raise that 2/2 bound (`[CHECKPOINT — due 2026-10-04]`), give the driver its own executor.** **⚠️ Do NOT reuse `applyBulkGeneratedMetadataToNote` — it overwrites `title` and `tags` unconditionally and would DESTROY curator-authored canonical titles.** **SCOPE: B1** job record + the release's only migration, driver, per-item guards, continue-on-failure, outer-catch fix; **B2** preflight; **B3** Library selection intent; **B4** confirmation modal; **B5** progress, receipt, retry. **⚠️ NOT REACHABLE until B3-B5 land.** **⚠️ THE `v0.118.0` PRODUCTION OUTAGE FIX IS NEXT AND IS NOT IN THIS RELEASE.** **VERIFICATION: FULL THREE-AGENT COLD PRESSURE TEST in isolated `git worktree`s.** **Routing: CODEX for B1-B2; Claude Code inline for B3-B5.**

**Previously:** **Kicked off and signed off 2026-09-05.** `v0.118.0 — Note and Study Pack Regeneration` is **RELEASED** on `releases/v0.118.0`, cut from `main` after `v0.117.0` merged and tagged. **ALL FIVE SLICES SHIPPED** across PRs #1283 (backend + share links), #1284 (scope selector, guidance, protections), #1285 (falsification findings) and #1286 (suggestion rule). **⚠️ THE SCOPED COLD FALSIFICATION AGENT RAN AND ITS HEADLINE FINDING WAS OUTSIDE THE STATED SCOPE, THE FIFTH RELEASE RUNNING WHERE THAT HAS BEEN TRUE:** quiz mastery is DERIVED and regeneration preserves `study_packs.id` by design, so a session that mastered the OLD quiz kept matching the NEW one — leaving the Quiz tab unlocked, **with its answer key**, on questions the learner had never seen, and Quick Review then writing `ConceptHealth` from them. That is the defect `v0.74.0` shipped to close, reopened by a side door and widened here. Fixed by requiring the mastering session to be no older than `notes.generation_enqueued_at`; **`study_packs.updated_at` was rejected as the discriminator because `updateTags`, `updateMetadata` and share-link creation all bump it without touching the quiz.** **⚠️ THE SAME ROOT CAUSE HAS AN UNFIXED LEG, RECORDED AS A KNOWN LIMITATION AND A BACKLOG ROW: the exam question pool and the Challenge question bank also key on the preserved `study_packs.id` and are never invalidated on content change.** **PHASE 1 ONLY — THE SINGLE-NOTE PRIMITIVE**, governed by `docs/claude-plans/note-and-study-pack-regeneration-stage1.md`, **owner-ratified 2026-09-05 (§20) — EXECUTE ITS DECISIONS, DO NOT RE-DERIVE THEM.** **⚠️ THE AUDIT'S HEADLINE IS THAT THE FEATURE IS SMALLER THAN THE BRIEF ASSUMED, ON ONE FACT: `NoteGenerationService.generateFromTopic` DOES NOT CREATE A NOTE** — it returns a content string that the frontend saves through the ordinary create path, so the brief's headline prohibition (*do not delete and recreate the Note*) guards a risk the code does not have. **SCOPE:** (1) content and Study Pack regenerate as ONE operation preserving `notes.id`, `notes.created_at`, `study_packs.id` and the `note_collection_items` row with its `position` and `label`; (2) **a FAILED Study Pack generation leaves the ORIGINAL content and ORIGINAL pack in place** — a half-regenerated note is the failure mode this exists to prevent; (3) both meters unchanged on failure, asserted on PERSISTED counters. **⚠️ ANTI-DRIFT: CURATOR BULK REGENERATION IS NOT IN THIS RELEASE AND MUST NOT BE FOLDED IN** — `docs/claude-plans/curator-bulk-regeneration-stage1.md` §29 rules it *NOT safe to ship in the same release as the single-Note primitive*, on two grounds that are arithmetic rather than opinion: the existing bulk receipt **cannot report a batch this long** (the poller abandons after ~5 minutes, and regeneration is strictly slower per item — TWO LLM calls, not one — so a 50-note batch guarantees the curator never learns the outcome), and **the batch loop occupies one of only TWO threads on the same executor each item's generation is dispatched onto.** **⚠️ Do NOT build a concept-migration engine** (stale `ConceptHealth` rows are already inert), no rename matching, no canonical concept identity. **⚠️ NO MIGRATION** — the audit states none is required; if one appears needed the scope is wrong. **No pricing, entitlement, plan-gate, mode or sub-mode change; onboarding frozen pending live dated reads through 2026-09-19; no analytics-event change; Slices 4-5 and `v0.112.0` Phase 3 stay OUT.** **Verification: ONE SCOPED COLD AGENT framed as falsification** — trigger is **MONEY/QUOTA SEMANTICS, one operation charging TWO meters** — **with pre-declared guards that a FAILED generation leaves both the original content and the original pack intact (a succeeding fixture proves nothing), that both meters are unchanged on failure (assert PERSISTED counters, never the call), and that identity survives a success.** **Routing: CODEX.**

**Kicked off and signed off 2026-09-05.** `v0.117.0 — Authoring and Quiz Legibility` is **Released** on `releases/v0.117.0`, cut from `main` after `v0.116.0` merged and tagged. **⚠️ THE FIRST RELEASE IN SOME TIME WHOSE EVIDENCE IS OWNER OBSERVATION RATHER THAN INFERENCE — five issues reported from real use with screenshots on 2026-09-05, audited against code, tightened by the owner, then run through a GPT tightening pass.** Plan: `docs/claude-plans/authoring-and-quiz-legibility-fix-plan.md`; **⚠️ READ ITS §10 FIRST — two owner premises are contradicted by the code, reported rather than silently reconciled.** **⚠️ THIS IS "RELEASE A" OF THAT PLAN'S OWN SPLIT: item 5 (deferred save-order) IS DELIBERATELY HELD BACK**, because items 4 and 5 both touch persistence and shipping them together would change immediate section assignment AND deferred order state in ways that are hard to falsify. **SCOPE:** (1) a Challenge Quiz assertion question stops reading as one run-on statement — the `Statement N:` splitter swallows the trailing interrogative onto the last statement, fixed at BOTH the presentation seam and the upstream prompt, **presentation only, never mutating stored text** (`v0.110.1`); (2) a plan-scoped Adaptive Practice session stops presenting itself as a single note — the backend already picks the right title by scope, but **the note-addressed read calls a 2-arg overload that hardcodes `sourceCollection = null`**, so a plan-scoped session anchored on a primary pack returns titled with one pack (**that is the screenshot**), then the header's density is reduced; (3) the section drag preview stops swallowing the page (`closestCenter` compares rect centres, wrong for a tall block; overlay renders full size) — **the overlay must NOT regrow**; (4) picking an existing section commits immediately, because the `editing`-gated debounced writer makes **a dropdown selection ride the same blur path as free typing** — **the writer and its comment STAY** (`v0.88.0`, reproduced defect recorded in it); (5) **adopting a Goal stops erasing a learner's own exam date** — `NoteCollectionService:918` NULLs `targetCompletionDate` on a PRE-EXISTING standalone adoption, **learner data loss, folded in deliberately after being held out of `v0.116.0` so that release's pressure test stayed aimed at update idempotence.** **⚠️ ANTI-DRIFT: item 5 of the plan is NOT in this release; no numeric pending-change count; do NOT change `ADAPTIVE_PRACTICE_STARTED`'s fields or firing conditions (item 2 changes a RESPONSE TITLE, not an event) nor `BOARD_EXAM_STARTED`/`QUIZ_SHARE_LINK_CREATED`; onboarding STAYS FROZEN (verified: none of the five reach it) with twelve dated reads 2026-09-10 → 2026-09-17; no migration, no new mode or sub-mode, no quota/entitlement/meter change, no `ProfileType` gate; Slices 4-5 and `v0.112.0` Phase 3 both stay OUT, gated on `2026-10-05` and `2026-10-04`.** **Verification: ONE SCOPED COLD AGENT framed as falsification** — the trigger is item 2 crossing backend and frontend on a surface two dated checkpoints measure — **with pre-declared guards that each name the fixture which would pass under the defect: a trailing interrogative AFTER the last `Statement N:` label, a plan-scoped session read through the NOTE-ADDRESSED route, and a pre-existing adoption that ALREADY carries a learner-set date.** **⚠️ CARRIED LESSON FROM `v0.116.0`: a guard whose fixture cannot occur in production proves nothing — that release's item 4 was mutation-verified against a hand-set state adoption could never produce and shipped as an observable no-op. Reach each subject the way production reaches it.** **Routing: SPLIT — items 1, 3, 4, 5 CLAUDE CODE inline; item 2 CODEX.** **⚠️ SIGNOFF VERDICT: ALL FIVE SHIPPED, AND ITEM 2's PLANNED PREMISE WAS FALSIFIED DURING IMPLEMENTATION — recorded, not reconciled.** *"Anchored on a primary pack (`v0.107.0`)"* was true through `v0.112.x` and is FALSE since `V133`: `buildGeneratingSession` sets BOTH note anchors to NULL for every collection session, so a post-migration plan-scoped row cannot be returned by a pack-keyed lookup at all. **The reachable population is PRE-`V133` LEGACY ROWS** — pack-anchored, column NULL, plan id only in `session_state.sourceCollectionId`. **⚠️ THAT CHANGED THE FIXTURE, NOT THE FIX, AND IT IS THE `v0.116.0` LESSON REPEATING: `validateAnchor` rejects a pack-anchored row that also carries the column, so the obvious column-shaped fixture would have tested a state no path produces.** The plan named `:183` alone; `:624` had the identical defect and is fixed with it. **⚠️ ITEM 2 WAS IMPLEMENTED BY A COLD AGENT AND AUDITED HERE — the stale premise, the `validateAnchor` predicate and the resolver's column-then-JSONB order were each verified against code, and the resolver was INDEPENDENTLY MUTATED rather than trusting the agent's own report: three named tests die, which is what proves the fixtures are legacy-shaped.** **⚠️ NO CHECKPOINT OWED — answered, not skipped: the evidence PRECEDED the work in all five cases.** **⚠️ THE DRIFT GATE FOUND TWO STALE DOC CLAIMS, NEITHER IN THE DIFF.** **Merged state: 2140 backend tests, 2152 frontend, `tsc` clean.**

**Kicked off and signed off 2026-09-05.** `v0.116.0 — Additive Review Set Updates` is **Released** on `releases/v0.116.0`, cut from `main` after `v0.115.0` merged and deployed. **SLICE 2 of the Comprehensive Review Set Overhaul** — architecture in `docs/claude-plans/v0.115.0-stage1-master-architecture-reconciliation.md` §8. **⚠️ IT RUNS NOW BECAUSE SLICE 1 HAS DEPLOYED, AND THAT ORDER WAS A CORRECTNESS CONSTRAINT: 100% of adopted placements' source notes carry program join rows against 11.8% of existing copies, and Slice 2 mints learner notes THROUGH `copyNote`, so running it first would have grown the mis-shelving population ~8x in one batch.** **⚠️ LIVE PRODUCT DEBT, MEASURED NOT PROJECTED: 92 learners · 523 adopted collections · 4,374 placements, with 364 source placements and 68 whole Subject Plans ALREADY STRANDED — 100% from upstream growth, 0% from learner removal — and 10 no-op re-adopt attempts by 4 distinct users.** `adopt()` bails before `copySourceItems`, so re-adoption is a hard no-op and learners asking get silently nothing. **SCOPE:** (1) upstream ADDITIONS apply — new notes, Subject Plans, placements — while **rename, reorder, retire and move are SURFACE-ONLY**, because until source-at-sync provenance exists the system **cannot distinguish learner customization from upstream change**; (2) five provenance facts per placement, of which **source section and source position at sync are the genuinely unreconstructable ones**, plus a last-sync timestamp and a learner-removal tombstone — **if they live in one JSON snapshot its schema is defined BEFORE implementation**; (3) **`Detached from source`** becomes a real resolver state (two such adoptions exist today) — plan stays usable, no update offered, **no source relationship fabricated**; (4) `companionMayBeOutdated:1193` stops returning `false` for every non-`ADMIN`, so a learner finally sees the staleness signal that already exists — **folded in as COHERENCE, being the same surface-don't-apply contract as item 1.** **⚠️ ANTI-DRIFT: ADDITIVE ONLY — an update must never replace a learner note body, Study Pack, edit, `ConceptHealth`, or quiz/session history; the invariant is "learner study history is preserved", NOT the withdrawn "the numerator never shrinks"; no synchronization engine, no event sourcing, no revision numbers; the tombstone ships with an EMPTY backfill and that is not grounds to omit it; Slice 1 is NOT re-opened; Slices 0A/0B are owner-executed and are not a gate; no quota/entitlement/meter change; onboarding frozen until 2026-09-11.** **⚠️ IDEMPOTENCE IS SETTLED AT THE STORAGE LAYER (`note_collection_items` carries `UNIQUE (collection_id, note_id)`, so placement identity IS `(source Subject Plan, source Note)` — no new column, no title matching) AND UNPROVEN AT THE APPLICATION LAYER: `persistAdoptedPlan:1460` returns `alreadyAdoptedResponse` and never re-reaches the insert, so re-adopt short-circuits and proves nothing — the update path does not exist yet, so its idempotence must be DESIGNED IN, not discovered.** **Verification: FULL THREE-AGENT COLD PRESSURE TEST in isolated `git worktree`s** — migration plus production-data semantics across 92 learners, firing the gate's triggers twice over — **with pre-declared guards that a SECOND application adds nothing and disturbs no learner reordering, that an EDITED learner note and its history are byte-identical after a pass, and that a detached adoption resolves and offers no update.** **⚠️ CARRIED LESSON: `v0.115.0` found a bare `role == ADMIN` substitution for `isCurator` SURVIVING ALL 99 TESTS in `NoteServiceTest`; item 4 touches that predicate class, so PIN BOTH LEGS.** **⚠️ DEPLOY-SPLIT CAVEAT RECORDED BEFORE THE READ: `[CHECKPOINT — due 2026-09-18]` reads Render `http_request_count` by `statusCode`, and this release adds request paths plus a migration — if it deploys first, that read is confounded and must say so.** **Routing: CODEX.** **⚠️ SIGNOFF VERDICT: ALL FOUR SCOPE ITEMS SHIPPED, BUT ITEM 4 HAD TO BE REBUILT AFTER THE PRESSURE TEST PROVED THE FIRST VERSION DELIVERED NOTHING — recorded rather than left standing in the Shipped list.** Widening the curator predicate was observably a no-op: adoption copied `companion` but never the structure snapshot, so `companionMayBeOutdated` returned `false` at its FIRST guard for all 523 adopted collections (82 with a Companion, ZERO with a snapshot), **and the tests that "proved" it hand-set the snapshot — a mutant dying against a state adoption cannot produce.** Adoption now stamps a baseline from the LEARNER's own structure, with a read-only learner notice (informational by owner decision, since regeneration is ADMIN-only). **⚠️ THE THREE-AGENT COLD PRESSURE TEST FOUND TWO BLOCKING DEFECTS THE IMPLEMENTATION AUDIT MISSED, AND ALL THREE AGENTS CONVERGED INDEPENDENTLY ON ONE LINE:** `inspectSourceUpdate` chose which of the LEARNER's plans to examine from the SOURCE's shape, so a curator restructure duplicated the learner's whole Review Set — **four CPALE learners were already in that state**, and one Apply would have re-placed 19 held notes and built a collection shape the page cannot render, making their own notes vanish; and an NPE on a page-load path that `V134` armed on 92 rows, swallowed by the frontend so the feature would have silently disappeared. **⚠️ THE RELEASE'S OWN TEST WAS THE FIRST DEFECT'S SCENARIO WITH THE ADOPTED ROOT STUBBED TO `List.of()` — that empty stub was the only thing hiding it.** Also fixed: an Apply permanently erased the surface-only signals it was meant to preserve. **8 mutants applied across the release, 8 killed by named tests. 2136 backend tests, 2147 frontend, `tsc` clean.** **⚠️ FOUR DOC SURFACES WERE FALSE AND NONE WERE IN THE DIFF, including two written by the `v0.115.0` signoff itself.**

**Kicked off and signed off 2026-09-05.** `v0.115.0 — Learner Publication Authority` is **Released** on `releases/v0.115.0`, cut from `main` after `v0.114.0` merged and tagged. **SLICE 1 of the Comprehensive Review Set Overhaul** — full architecture in `docs/claude-plans/v0.115.0-stage1-master-architecture-reconciliation.md`, from three cold-agent audits revised after owner/Product-UX tightening. **The audit set and the `ADR-001` corrections ride in this release** (PRs #1272, #1273), because they are the evidence the scope rests on. **SCOPE:** at the `PRIVATE → PUBLIC` transition for **learner-owned** notes, clear the copied `note_course_program` rows in `NoteService.updateVisibility` — the sole path to non-`PRIVATE`, which already computes the predicate for analytics. **Clearing UN-SHADOWS the learner's own field, so authority is TRANSFERRED rather than merely revoked**; a null string stays null (public but unshelved); plus a companion fix so an omitted `courseProgramText` retains the stored value. **⚠️ THE ORDER IS A CORRECTNESS CONSTRAINT, MEASURED NOT INFERRED: 100% of adopted placements' source notes carry join rows against 11.8% of existing copies, so Slice 2 — which mints learner notes THROUGH `copyNote` — would grow the affected population ~8x if it ran first. Do NOT reverse.** **⚠️ The hazard is REAL AND UNREALISED: 536 copies carry rows, ZERO are public** — it closes a live mechanism before it fires. **⚠️ And the defect is that the learner CANNOT edit, not that they do not** — shadowing is unconditional at one row and the editor requires `isOwner && isCurator`, so this is a capability gap. **⚠️ ANTI-DRIFT: no public-discovery redesign, no new visibility state, clearing restricted to NON-CURATORS or it destroys the catalog, no provenance column, no backfill, no migration, options D and C stay REJECTED (`ADR-001`, 2026-09-05) with their two rejections NOT collapsed, Slice 2 NOT started, Slices 0A/0B are owner-executed and are not a gate, onboarding frozen.** **Verification: a single `advisor()` call** — one method, zero-row blast radius — **with a pre-declared guard that a CURATOR publishing KEEPS its rows while a learner publishing a copy LOSES them.** **Routing: CLAUDE CODE inline.** **⚠️ SIGNOFF VERDICT: ALL THREE SCOPE ITEMS SHIPPED, AND THE VERIFICATION ROUND PRODUCED A FINDING BIGGER THAN THE FEATURE.** The five original guards all passed while a **catalog-destroying mutation went green**: replacing `CuratorAuthoringPredicate.isCurator` with a bare `role == ADMIN` check **survived all 99 tests in `NoteServiceTest`** — the ADMIN fixture still kept its rows, the learner fixture still lost theirs, both guards satisfied — while **every TEACHER-owned curated note would have had its authored applicability stripped on publication.** Most of the public catalog is curator-authored. Closed by a TEACHER-leg guard and **recorded rather than quietly fixed**, because it is this repo's recurring shape: a fixture can satisfy a guard without exercising the branch that matters. **⚠️ THE PRE-DECLARED ESCALATION TRIGGER WAS CHECKED, NOT ASSUMED, AND DID NOT FIRE** — every note `setVisibility` writer sets `PRIVATE` except the one inside `updateVisibility`; no native `UPDATE` touches `notes.visibility`; and the one bulk publisher (`NoteBulkGenerationService:337`) routes **through** this method as a correctly-excluded curator surface. **⚠️ THE CLEARING WAS CONFIRMED TO ACTUALLY DELETE**, which seven mocked guards structurally cannot show. **`./mvnw clean install` → BUILD SUCCESS, 2120 tests, 0 failures.** **⚠️ ONE CHECKPOINT OWED AND WRITTEN: `[CHECKPOINT — due 2026-10-05]`** — the claim that clearing *transfers* authority rather than removing representation is a **bootstrap argument from mechanism**, not a measured outcome.

**Kicked off and signed off 2026-09-04.** `v0.114.0 — Connection Evidence` is **Released** on `releases/v0.114.0`, cut from `main` after `v0.113.1` merged and tagged. **⚠️ IT EXISTS BECAUSE THE KICKOFF SCAN FOUND A CHECKPOINT THAT WOULD HAVE COME DUE UNANSWERABLE — not because the work was next in a queue.** `[CHECKPOINT — due 2026-10-04]` is named in `v0.112.0`'s anti-drift as **the deciding input for Phase 3 and the reason Phase 3 is deferred rather than built**, and it is 30 days out. **Phase 1 shipped in full** (verified by reading `application.yaml` at kickoff: leak detection 60 s, pool 20, connection timeout 5 s). **Phase 2 did not:** logging the effective `hibernate.connection.handling_mode` returns **zero matches** across `backend/src/main/java` and `backend/src/main/resources`, and `spring.jpa.open-in-view` is still unset — while the comment at `application.yaml:21` calls leak detection *"the instrument Phase 2 reads to settle the open-in-view question"*. So the checkpoint has a date, a kill criterion and a row, and **no working metric.** **SCOPE:** (1) log the EFFECTIVE handling mode from the `EntityManagerFactory` at startup (**primary** — leak logs say a connection was held and by whom, never whether it stayed bound *after commit*, which is the actual question); (2) sample Hikari's `active` count against a commit-early/serialize-slow request (**confirms only**); (3) run the seven read-only falsification queries; (4) **read Render request-rate data for 05:55–05:57 UTC** — **all five §5 queries read WRITE tables, so none can detect an anonymous READ burst**, and the only path the log names as starved is the `permitAll`, unpaginated `NoteCollectionService.listPublic`. **⚠️ ANTI-DRIFT: do NOT set `open-in-view: false` here** (real blast radius; wants staging, never a direct production edit); **do NOT start Phase 3**; no migration, no quota change, onboarding frozen, no analytics-field change; **PgBouncer is not the fix**; **production DB stays read-only**. **Verification: a single `advisor()` call**, escalating to one cold agent only if item 1 contradicts `v0.112.0`'s premise. **Routing: Claude Code inline.** **⚠️ PREMISE CORRECTED DURING IMPLEMENTATION, 2026-09-04 — TWO OF THE FOUR ITEMS WERE ALREADY DELIVERED, AND THE KICKOFF'S OWN GREP IS WHY IT MISSED THEM.** *"Zero matches across `backend/src/main/java` and `backend/src/main/resources`"* is true; the conclusion *"Phase 2 did not ship"* is false. **Phase 2's measurement shipped in `v0.112.0` under `src/test/java`** (commit `3ff8ec35`) and **item 4 shipped as §11 of the outage finding** (commit `a58096df`) — **the evidence was never in `src/main`, so a grep scoped there could not find it.** **⚠️ WHAT REMAINED IS REAL AND WAS BUILT: item 1's PRODUCTION half** — the existing test runs against the H2 profile that `src/test/resources/application.yaml` shadows `src/main` with entirely, and `DataSourcePoolContractTest`'s overlay guard covered `spring.datasource.` **only**, so a `spring.jpa.` key in the prod overlay would have moved production's connection lifetime with the whole suite green — **and item 3, which was genuinely unrun and produced this release's finding.** **⚠️ The escalation trigger did NOT fire: the reading CONFIRMS `HOLD`.** Recorded rather than swapped silently, per the same correction made mid-`v0.113.0`. **⚠️ SIGNOFF VERDICT: THE RELEASE DELIVERED ITS INSTRUMENT AND ITS READ, AND THE READ POINTS AWAY FROM PHASE 3.** §5's queries ran at last (§12 of the finding): the write side is **empty on a widened window** — zero sessions 05:00–05:56, zero notes enqueued 04:00–05:56 — so **§3's root cause, the one Phase 3 exists to fix, is now positively unsupported.** **⚠️ AND §11's REFUTATION OF THE READ BURST IS WITHDRAWN**, on a table §11 never consulted: Q2 shows **ONE note hit sixteen times in seventy-eight seconds**, against a baseline of **1–2 per HOUR across eleven days**, beginning **~15 s BEFORE** the saturation signature. **⚠️ NOT confirmed and DIRECTION NOT settled** — a stalled client reloading produces a similar shape, and Q2 is a **floor** since ISR and crawlers leave no row. **Phase 3 is therefore aimed at the hypothesis the data now argues against, and `[CHECKPOINT — due 2026-10-04]` must settle that rather than an argument doing it.**

**Kicked off and signed off 2026-09-04.** `v0.113.1 — Anchoring Hardening` is **Released** on `releases/v0.113.1`, cut from `main` after `v0.113.0` merged, tagged and **deployed to production**. **A PATCH closing the residuals `v0.113.0`'s three-agent pressure test found and deliberately left.** **⚠️ IT EXISTS BECAUSE THE MAJOR ROADMAP ITEMS ARE TIME-GATED, NOT BECAUSE THESE RESIDUALS ARE URGENT — recorded so it is not read as priority order:** Canonical Concept Identity's own Phase 0 sizing read came back **against** building it now (**Q5 = 0.4%**, 6 user-concept pairs across 5 users; **10,361** distinct authored concepts kill the `ADR-001` curated-catalog analogy), `v0.112.0` Phase 3 is gated on `[CHECKPOINT — due 2026-10-04]` plus an OSIV decision, and **twelve dated reads land between 2026-09-10 and 2026-09-17** — so this release is deliberately scoped to touch none of the surfaces they measure. **SCOPE:** (1) collapse the anchor contract to ONE authority (the `CHECK`, the entity `@PrePersist` validator and `assertValidAnchor` encode it at three strengths, the service copy being a redundant weaker one); (2) a legacy plan-scoped session whose collection was deleted stops 404ing on the session-addressed route while the note-addressed route resumes it fine; (3) completion cannot brick a plan — the fail-loud throw rolls back an already-`COMPLETED` session and the sweeper does not cover `ADAPTIVE`; (4) the nine H2 fixtures gain the constraints they omit, so a test cannot persist a shape the database rejects; (5) the harness matches production's **PostgreSQL 18** instead of pinning 16. **⚠️ ANTI-DRIFT: no migration, no behaviour change to what `v0.113.0` shipped **EXCEPT items 2 and 3, which are named exceptions rather than an oversight — each RESTORES a path `v0.113.0` closed by accident** (a completion that bricked the plan; a legacy session-addressed read that 404'd), and item 1 narrows only a shape the entity already rejected, onboarding frozen, no analytics-field change, no quota change, and `v0.112.0` Phase 3 stays out.** **Verification: a single `advisor()` call**, with a pre-declared guard that a partial and a both-anchors shape must both still be rejected while an anchorless terminal row stays permitted. **Routing: CLAUDE CODE inline.** Full scope in `RELEASES.md`.

**Kicked off and signed off 2026-09-04.** `v0.113.0 — Session Anchoring` is **Released** on `releases/v0.113.0`, cut from `main` after `v0.112.0` merged, tagged and **deployed to production**. **⚠️ IT DELIBERATELY OVERRIDES THREE RECORDED RULINGS, AND THE OVERRIDE IS WRITTEN DOWN RATHER THAN ROUTED AROUND** — `v0.105.0` (*"No migration, no index change, no lookup change"*), `v0.107.0` (**"REJECTED WITH REASONS, DO NOT RE-PROPOSE"**) and `v0.110.0` (*"has no forcing slice any more"*). **None of the three is a technical objection**; every one objects to taking the migration WITHOUT a forcing reason, and `v0.110.0` parked it explicitly as an owner decision. **The owner took it 2026-09-04, and the reason is verification budget rather than product.** **⚠️ The shape `v0.107.0` rejected is not the shape this builds** — it rejected adding a nullable scope discriminator TO THE EXISTING INDEX KEY, which genuinely breaks one-active-per-pack; this release leaves that key alone and adds a SEPARATE index for the new scope. **THE PROBLEM: `quick_review_sessions` anchors every session on a `(study_pack_id, note_id)` pair that is `NOT NULL` on both columns** (`V4:4`, `V19:161-162`), with `V41`'s two partial unique indexes enforcing one active session per anchor — **while THREE session types share the `ADAPTIVE` discriminator**, so plan-scoped Adaptive Practice must borrow a primary pack it does not conceptually have and then contends with any note-scoped session on it. **⚠️ THE PRIMARY HAZARD IS NOT THE MIGRATION — AND THE KICKOFF'S FIGURE WAS CORRECTED BEFORE IMPLEMENTATION: it said 52 session-scoped `getStudyPackId()` call sites across NINE services; the exact enumeration is 54 ACROSS SIX** (re-derived independently by a cold agent). **`ConceptHealthService`, `ExamQuestionPoolService` and `ProgressReportService` have ZERO session-scoped call sites** — they read their own entities' `study_pack_id` — so the kickoff's named "sharp ones" were a misclassification. **The hazard lives at the CALLER SEAM**, sharpest at `QuickReviewAdaptivePracticeService:797`, where the fallback default of `parseSourceStudyPackId` becomes null and an unstamped item would attribute to nothing. **`note_id` also goes nullable, adding 4 sites across 2 services and surfacing `QuizSessionHistoryService`, a seventh service the `getStudyPackId()` sweep cannot reach.** **The migration is the small half; the sweep is the large one, stated at kickoff.** **⚠️ THE SEMANTIC CONSEQUENCE IS A PRODUCT CHANGE: afterwards a learner may hold an active plan-scoped AND an active note-scoped session on a pack inside that plan at the same time.** Today they collide and one silently resumes the other — the `v0.107.0` limitation whose worse leg makes plan scope look broken in production. **That contention ending IS the release, and no copy may describe it as a regression.** **⚠️ EXISTING ROWS ARE LEFT ALONE — no backfill, no inferred `source_collection_id`.** **⚠️ CANONICAL CONCEPT IDENTITY IS NOT THIS RELEASE** — its Phase 0 sizing read ran 2026-09-04 and **Q5 returned 0.4%** (6 user-concept pairs across 5 users), while Q2's **10,361** distinct authored concepts kill the curated-catalog analogy the item rested on; it is **rescoped with a re-read trigger, not cancelled.** **Verification: FULL THREE-AGENT COLD PRESSURE TEST in isolated `git worktree`s, declared not inherited** — migration plus production-data semantics on the one table every quiz mode shares — **with one agent pointed at the call sites, not at the migration.** **Routing: CODEX.** **⚠️ DELIVERED, AND THE PRESSURE TEST CHANGED THE RELEASE RATHER THAN RATIFYING IT — three cold agents in isolated worktrees returned FOUR BLOCKING findings, two convergent across agents.** The sharpest was invisible to the suite: **the Dashboard Continue card silently dropped `entry=dashboard-continue` for NOTE-scoped Adaptive resumes**, because `DashboardService` supplies a session id on the pack-anchored branch too and the new session route gated on that id alone — a direct violation of this release's own anti-drift on `ADAPTIVE_PRACTICE_STARTED`, and **`[CHECKPOINT — due 2026-09-12]` reads exactly that field.** It passed because the fixture omitted `sessionId`, exercising a branch production never takes. **⚠️ TWO FINDINGS WERE OWNER DECISIONS AND WERE SCOPED IN RATHER THAN DEFERRED (owner, 2026-09-04): the collection FK is now `ON DELETE SET NULL`** — the kickoff's justification for the cascade was FALSIFIED, since `user_id` has cascaded since `V4:3`, so the cascade added only a second user-facing deletion trigger that destroyed COMPLETED history while the `ConceptHealth` rows it produced survived — **and a plan-scoped completion now CREDITS EVERY NOTE IT SAMPLED**, reusing the existing `sourceNoteRefs` two-pass path rather than inventing a second mechanism, so Study Plan progress advances. The weak-concept retention email was fixed alongside them, matching on the SOURCE-PACK STAMP so two packs' identically-named concepts stay distinct. Full scope in `RELEASES.md`.

**Kicked off 2026-09-04.** `v0.112.0 — Connection Pool Integrity` is **RELEASED** (signed off 2026-09-04) on `releases/v0.112.0`, cut from `main` after `v0.111.0` merged and tagged. **⚠️ CLOSED AS PHASES 1 AND 2; PHASE 3 IS DEFERRED ON EVIDENCE, WHICH IS THE RELEASE'S OWN FINDING RATHER THAN AN OMISSION** — Phase 2 measured that the connection OUTLIVES the transaction (`hibernate.connection.handling_mode` is `DELAYED_ACQUISITION_AND_HOLD`, set unconditionally by Spring), so Phase 3 alone cannot fix the exhaustion; and the Render read established that **§3's cause is NOT confirmed** — the pool was fully checked out while **the database was idle**, under **0–9 requests/min**, which fits an unconsidered **connection LEAK** at least as well as the LLM-hold hypothesis Phase 3 addresses. **Phase 3 is gated on the leak-detection output this release ships.** **⚠️ REPOINTED AT ITS OWN KICKOFF, AND THE SWAP WAS NOT ROUTINE:** it opened as *Canonical Concept Identity* — the ADR-sized item six releases had deferred by name — and **kickoff step 8's Backlog Index scan surfaced two UNTRACKED files dated the same day: a PRODUCTION OUTAGE and its diagnosis.** The owner ruled to swap. **⚠️ Concept identity is NOT cancelled — it becomes `v0.113.0` and loses nothing by moving, since its Phase 0 sizing read is owner-executed and runs in parallel.** **⚠️ THE SCAN IS WHAT CAUGHT THIS, WHICH IS THE PROCESS WORKING** — the diagnosis file's §10 says it needs a row, and notes the PREVIOUS incident file in that directory never got one: **the same failure mode, twice.** **WHAT HAPPENED: the pool was exhausted and the HEALTH CHECK THEN STARVED ON THE SAME POOL** — at 05:55:14 UTC Hikari reported `total=10, active=10, idle=0, waiting=15`, `DataSourceHealthIndicator` queued behind the same 30 s acquisition timeout, failed at 30,002 ms, and Render **RESTARTED** the instance. **⚠️ CORRECTED AT SIGNOFF FROM RENDER'S OWN RECORDS: it was RESTARTED, not replaced — the deploy history shows the last deploy finished 03:09:44 and the next began 06:24:18, so THERE WAS NO DEPLOY at 05:56; the owner confirmed no manual restart; and *killed for failing a health check* remains an INFERENCE, since no platform line attributes the restart to anything.** *Nothing recovered on its own* is **NOT established** — the evidence leans mildly against it. **⚠️ EVERY CLAIM RE-VERIFIED IN CODE AT KICKOFF:** there is **NO HikariCP configuration anywhere**, so `total=10` is Hikari's **default** and **nobody chose 10**; `server.tomcat.threads.max: 25` makes the ratio **25 request threads against 10 connections**, and `active=10 + waiting=15 = 25` is exactly the thread cap; `ChallengeQuizService` is class-level `@Transactional` (`:84`) with the LLM **inside** (`:449`) at a **180 s** read timeout against a **30 s** acquisition timeout; `spring.jpa.open-in-view` is **unset**, defaulting to `true`. **FOURTEEN paths hold a JDBC connection across an OpenAI call, so SEVEN concurrent generations exhaust the pool. Render was never the constraint** — it allows ≥100 connections on every plan. **⚠️ THE CONSEQUENCE THE DIAGNOSIS DOES NOT SEE, FOUND AT KICKOFF AND VERIFIED IN CODE: TWELVE quota-increment sites sit INSIDE the six class-level `@Transactional` boundaries Phase 3 would move**, and **every one rolls back automatically on a generation failure today — an accident of ordering plus the transaction**, recorded twice already (`v0.106.0`: *"Board Exam's quota safety is an accident of its transaction"*; `v0.107.0`: *"the refund question opens ONLY if the transaction boundary moves, so do NOT move it"*). **Phase 3 IS that move, on six services at once, so relocating the LLM call without extending quota reversal silently converts twelve auto-reversing charges into PERMANENT-ON-FAILURE charges — a MONEY-SEMANTICS CHANGE HIDING INSIDE A PERFORMANCE FIX.** **⚠️ AND ONLY THREE OF THE TWELVE HAVE ANY REVERSAL TO EXTEND, VERIFIED BY OPENING `UserUsageService` RATHER THAN INFERRED FROM THE PRECEDENT: `UserUsageRepository` exposes exactly TWO decrements (long exam, board exam), so NINE SITES ACROSS FIVE METERS NEED NEW REVERSAL — `challenge_quiz`, `adaptive_quiz`, `multi_note`, `interview_practice`, `study_pack`. "Extend the existing machinery" is five repository methods and five service methods, not a call-site change; this RESIZES Phase 3 and is recorded at kickoff.** **⚠️ PHASE 1 → 2 → 3 IS A DEPENDENCY, NOT CAUTION.** **Phase 1** (config only, inline): `leak-detection-threshold: 60000` **first**, a pool raise **bounded by `N ≤ (max_connections − reserved) / 2`** because Render runs both instances during a deploy, `connection-timeout: 5000` (**a deliberate trade — it buys staying up with user-visible errors**), plus two one-line executor/timeout changes. **Phase 2**: `open-in-view` defaults to `true`, and **if the connection follows the EntityManager then Phase 3 can land, look correct, and not fix the exhaustion** — **settle it EMPIRICALLY from Phase 1's leak detection, never by argument**, and run the five read-only falsification queries. **Phase 3** (CODEX): the **two-short-transactions** shape, which **already exists in this repo three times and must not be re-invented**, applied in exposure order, with `InterviewPracticeService` additionally **ceasing to hold `findByIdAndOwnerUserIdForUpdate` across the LLM call**. **⚠️ "Dispatches after commit" ≠ "does not hold a connection"** — `LongExamService:317` wraps `execute(...)` around the LLM call at `:1151`, and **Hikari does not care which thread.** **⚠️ TWO RECORDED LANDMINES, BOTH OF WHICH BROKE PRODUCTION WHILE EVERY TEST PASSED:** an afterCommit restructuring broke every Board Exam start (`MockitoExtension` has no transaction manager, so tests took the inline fallback), and `v0.81.0`'s `REQUIRES_NEW` broke every Challenge start on FK visibility. **⚠️ ANTI-DRIFT: every relocated path carries its quota reversal, reusing the existing machinery — no second reversal shape; no entitlement/plan-tier/limit/meter change; PgBouncer is NOT the fix and breaks session-level advisory locks; do NOT touch `ExamQuestionPoolService`'s pool refresh, which is ALREADY CORRECT and is a reference implementation here; the OCR finding is RETRACTED (Vision is disabled in production) and must not be re-derived as a defect; no migration; onboarding frozen; do not change what `BOARD_EXAM_STARTED`/`ADAPTIVE_PRACTICE_STARTED`/`QUIZ_SHARE_LINK_CREATED` record.** **VERIFICATION: FULL THREE-AGENT COLD PRESSURE TEST IN ISOLATED `git worktree`s**, with a **pre-declared guard aimed at the landmine rather than the feature: a test asserting the new transaction shape MUST FAIL under `MockitoExtension`'s inline fallback**, and a second at the money end: **a generation that FAILS after the boundary moves must leave usage UNCHANGED** — a succeeding fixture passes under both the defect and the fix. **Routing: SPLIT — Phase 1 CLAUDE CODE inline, Phase 3 CODEX.** Full scope in `RELEASES.md`.

**Kicked off and signed off 2026-09-04.** `v0.111.0 — Multidisciplinary Domain Context` is **Released** on `releases/v0.111.0`. **SHIPPED: three Domain Context values (8 → 11), all `quantitative = false`, on the owner's OPTION A decision** — Phase 2 recommended Option B and the owner chose A with the one-live-program fact stated. **⚠️ `ARCHITECTURE` was rejected AGAIN on the naming rule; what shipped is three TREATMENT-based values, not a program identity promoted to a context.** **⚠️ CUT FROM `docs/correct-architecture-domain-context-verdict`, NOT FROM `main` — an exception ruled by the owner 2026-09-04** so the corrected Architecture verdict and the `v0.111.0` handoff ride in this release; **PR #1260 is closed unmerged** and that commit reaches `main` through this release's PR. `main` was at `v0.110.2`, merged and tagged, with no other divergence, and all seven version references were in sync before the bump. **⚠️ A REPORTED, REPRODUCED OWNER BLOCKER ON THE ALE REVIEW SET, NOT A HYPOTHETICAL:** a note with Subject *Architectural Design* and two programs — `Architecture` + `Architectural Engineering` — **cannot be generated at all**, every premise re-verified by reading code at kickoff: `StudyPackGenerationContextResolver.resolveCourseProgram:143` returns the joined catalog name **only when `joinedPrograms.size() == 1`**, `assertGenerationReady:33-39` throws `MultiProgramDomainContextRequiredException` on 2+ programs with a null Domain Context, and **no existing value honestly fits** (`ENGINEERING_SCIENCES` is shared engineering knowledge, `CIVIL_ENGINEERING` is civil-specific, `PROFESSIONAL_PRACTICE_AND_REGULATION` is codes and ethics). **⚠️ `CLAUDE.md`'s "PROVABLE NO-OP" VERDICT IS TRUE FOR SINGLE-PROGRAM NOTES AND IRRELEVANT TO MULTI-PROGRAM ONES — do NOT re-reject `ARCHITECTURE` on it**, which is exactly the criterion the expansion bar names: *Automatic cannot express it in the relevant multi-program cases*. **⚠️ THE CURATOR-COPY BLOCKER IS CLOSED — all three fixes verified against code — so Phase 1, the targeted pass over the 215 `(unset)` ALE rows, is curator work with NO CODE and can run NOW, in parallel.** **GOVERNED BY `docs/claude-plans/ale-let-review-set-unblock-and-domain-context-evidence-plan.md` — execute its phases, do NOT re-derive its conclusions, read its *Superseded assumptions* table first.** **⚠️ PHASE 0 → 1 → 2 → 3, and NO IMPLEMENTATION BEFORE PHASE 3 APPROVAL.** **⚠️ PHASES 0 AND 1 BOTH COMPLETED 2026-09-04 — the live catalog is 41 programs (not 21) and the ALE pass moved 83 rows off `(unset)`, leaving 39 `UNSET_VALID` and 93 `CONTEXT_GAP`. Of those 93, EIGHT are blocked today and 85 block once three missing programs enter the catalog; report both numbers, never one.** Superseded — Phase 2 was blocked on two owner-executed inputs: **Phase 0.3's LIVE catalog capture** (⚠️ load-bearing, not bookkeeping — the recorded denominator of 21 is STALE, production holds at least 22, and computing the `ADR-001` ratio from 21 repeats the error the ADR was corrected for on 2026-08-31) and **Phase 1's corrected ALE distribution plus residue.** **SCOPE SHAPE, NOT VALUES — which enum values ship is Phase 2's output and Phase 3's owner decision, and naming candidates here would pre-commit the answer exactly as the shaping module did to produce the 215 unset rows:** enum values · labels · `quantitative` flags · `domain-context.ts` descriptions · `REVIEW_SET_SHAPING_CONTEXT.md` rules · the `ADR-001` amendment · tests. **⚠️ NO MIGRATION** — `notes.domain_context` is `VARCHAR(64)` with no CHECK (`V102:2`, verified). **⚠️ ONE RELEASE (owner, because it blocks the ALE build)** — splitting it ships a taxonomy whose curator-facing descriptions and strategist rules lag the enum. **⚠️ TWO `ADR-001` OBLIGATIONS, NOT ONE: the failure-condition amendment argued against the LIVE catalog count, AND clause (b)'s explicit owner decision in the revision log; clause (a)'s ~10-note floor is a Phase 2 proof-table bar.** **⚠️ ANTI-DRIFT: every new value defaults `quantitative = false`** — the one irreversible thing, since `true` is permanent per note and two values were delivered `true` and corrected; **do NOT extend `QUANTITATIVE_KEYWORDS`; remove NO existing value** (removal can lock existing multi-program rows); **borrow real curriculum vocabulary and never invent** (`GENERAL_ENGINEERING`, `Built Environment`, `Health Sciences`, `Computing` were name-rejected — **name rejection ≠ family rejection**); **program-stripping is an EMERGENCY WORKAROUND ONLY and every use is logged as Phase 2 input; the three zero-usage values (`NURSING`, `ACCOUNTANCY`, `PROFESSIONAL_EDUCATION`) must have their explanation STATED, not resolved by reasoning; no second rubric — R4's exists; the taxonomy stays single-valued, closed and non-admin-editable; no quota/meter change; no `ProfileType` gate; `frontend/app/onboarding` STAYS FROZEN** — verified, not assumed: its only Domain Context reference is `domainContext: null` passed as a payload field, and **TWELVE dated reads fall between `2026-09-10` and `2026-09-17`.** **VERIFICATION IS DECLARED CONDITIONALLY ON A PHASE 3 DECISION: a single `advisor()` call if every new value ships `quantitative = false`; ONE SCOPED COLD AGENT framed as falsification if any ships `true`**, with a pre-declared guard pinning the multi-program case end to end — a single-program fixture passes under both the defect and the fix and proves nothing. **⚠️ `[CHECKPOINT — due 2026-09-28]` is RETIRED by this release, not confounded — a choice recorded now, with Phase 5's query re-specified before it runs.** **Routing: CLAUDE CODE inline.** Full scope in `RELEASES.md`.

**Kicked off and signed off 2026-09-04.** `v0.110.2 — Shared Link Integrity` is **Released** on `releases/v0.110.2`, cut from `main` after `v0.110.1` merged and tagged. **A PATCH fixing a LIVE DEFECT found during `v0.110.0`'s item 4 audit and carried unfixed through TWO releases.** **⚠️ RE-VERIFIED AGAINST `main` AT KICKOFF:** `GeneratedQuizService.generate:139` reuses the existing entity and `:152` overwrites its questions — **the row id never changes** — while `quiz_share_links.generated_quiz_id` points at it and `GeneratedQuizService` references share links **zero times**. **Regenerating an already-shared quiz therefore mutates the quiz a recipient may be mid-way through: a changed question count 400s them on submit, and an UNCHANGED count silently grades them against questions they never saw.** **⚠️ The silent case is the worse one and is reachable by every non-`TEACHER`, who always gets 10 questions.** **⚠️ It is the exact hazard `v0.110.0` rejected refs to avoid — the snapshot design removed it for COMBINED quizzes and never for single-note ones.** **Scope:** (1) regeneration turns a live share link OFF so the recipient meets the existing *"no longer active"* screen instead of a wrong score; (2) the owner is told before and after. **⚠️ ANTI-DRIFT: deactivate, never delete** (deleting spends share-link quota); **no snapshot here** (that is a migration plus a table, recorded as the larger alternative); **do not weaken `uq_generated_quizzes_note_id`**; **no quota/meter change; no `ProfileType` gate; onboarding frozen and TWELVE dated reads fall between `2026-09-10` and `2026-09-17`.** **Verification: a single `advisor()` call**, with a pre-declared guard whose fixture must regenerate the SAME NUMBER of questions — a count-changing fixture fails with or without the fix. **Routing: CLAUDE CODE inline.** Full scope in `RELEASES.md`.

**Kicked off and signed off 2026-09-04.** `v0.110.1 — Quiz Text Integrity` is **Released** on `releases/v0.110.1`, cut from `main` after `v0.110.0` merged and tagged. **A PATCH — both items are corrective.** **⚠️ THE NUMBER WAS CORRECTED AT KICKOFF: `v0.111.1` was proposed, but all eight patches in this repo are a `.1`/`.2` of a `.0` that EXISTS, and `v0.111.0` does not — a `.1` without its base would permanently imply a release that never shipped.** **⚠️ ITEM 1 IS THE ONLY CRITICAL FROM `v0.110.0`'s TWO-AGENT PRESSURE TEST — pre-existing, product-wide, and REPRODUCED rather than inferred:** `QuizItem`'s `@JsonCreator` routes into the sanitizing constructor and `QuizValidationUtils.sanitizeChoiceTexts` is **NOT idempotent**, so the leading-label strip runs at generation AND on every deserialization. `"A. B. Smith"` stores as `"B. Smith"` and reads back `"Smith"`; `"B. D.C. generator"` reads back `"C. generator"`, then `"generator"`. **⚠️ IT COMPOUNDS ON EVERY READ, so waiting is not neutral** — and it spans every table holding a `QuizItem` in every mode, on a board-exam catalog where `A.C.`/`D.C.` and biology binomials sit inside `[A-Da-d]`. **SCOPE: (1)** a stored choice survives being read — **⚠️ the GENERATION-time sanitizer STAYS; the defect is WHERE it runs, not WHAT it does**; **(2)** a supporter can find and revoke a combined quiz they already shared, closing an owner-control gap whose own index (`idx_combined_quizzes_owner_created_at`) is built and unqueried, **and which is owed BEFORE `[CHECKPOINT — due 2026-10-04]` because it suppresses that read's signal.** **⚠️ ANTI-DRIFT: already-corrupted rows are UNRECOVERABLE — the stripped text is gone, so no guessing backfill and no re-prefixing; no migration, no new field, no re-serialization pass; item 2 adds FIND and REVOKE, never EDIT; no quota/meter change; no `ProfileType` gate; onboarding stays frozen; do not touch the analytics fields four dated checkpoints read.** **Verification: ONE SCOPED COLD AGENT framed as falsification**, with a pre-declared byte-identical round-trip guard whose fixture must use an ALREADY-LABEL-LOOKING value. Full scope in `RELEASES.md`.

**Kicked off 2026-09-03, signed off 2026-09-04.** `v0.110.0 — Supporter Combined Quiz` is **Released** on `releases/v0.110.0`, cut from `main` after `v0.109.0` merged and tagged. **Slice 6 of the approved assessment sequence — the LAST one.** **⚠️ SCOPE AMENDED THE DAY IT OPENED, after a cold audit found THREE OF THE KICKOFF'S FOUR CLAIMS WRONG — it opened at three items and shipped FIVE.** **ALL FIVE SHIPPED:** (1) shared quizzes grade MULTI_SELECT correctly — a live defect where a fully correct answer scored ZERO and a partly correct one scored full marks, in effectively every shared quiz, invisible to all ten existing tests; (2) a Plus/Pro supporter who picked 30 questions and silently received 10 is now refused rather than clamped — **and the same gate removed an upgrade prompt selling Plus for a profile-gated capability no plan grants**; (3) the guidance tip stops advertising a `TEACHER`-gated flow to everyone; (4) `combined_quizzes` (`V132`), an immutable snapshot with **no FK to `notes`**, plus an exclusive target arc on `quiz_share_links`; (5) a supporter-reachable Library picker and a share surface outside `/notes/[id]`. **⚠️ VERIFICATION WAS TWO COLD AGENTS IN ISOLATED WORKTREES, one falsifying claims and one pointed at the BLIND SPOT — the tier was stated, not inherited.** All five claims survived falsification; the blind-spot agent found **four surviving mutants and a product-wide JSONB leak THIS RELEASE INTRODUCED**, all fixed before signoff. **⚠️ TWO OPEN ITEMS CARRY FORWARD AND ARE NOT DEFECTS OF THIS RELEASE'S SCOPE: a CRITICAL pre-existing choice-text corruption that COMPOUNDS ON EVERY READ product-wide (`"D.C. generator"` → `"C. generator"` → `"generator"`, reproduced at signoff), and NO in-product path back to an existing combined quiz or its live share link — an owner-control gap whose own index (`idx_combined_quizzes_owner_created_at`) is built and unqueried.** Full scope, findings and Known limitations in `RELEASES.md`.

**Kicked off and signed off 2026-09-03.** `v0.109.0 — Assessment Discoverability` is **Released** on `releases/v0.109.0`, cut from `main` after `v0.108.0` merged and tagged. **⚠️ TRIGGERED BY AN OWNER OBSERVATION, NOT A DEFECT REPORT (2026-09-03):** *"we don't yet announce that the existing plans were updated to be much comprehensive."* Acting on it, **both `2026-10-06` assessment checkpoints were LIFTED to `[EFFORT]` gates rather than re-dated** — a read on an unannounced capability measures **discoverability, not demand**, and `v0.107.0`'s kill criterion would have fired on evidence it was never designed to weigh. **This release is what un-lifts them.** **⚠️ THE GAP IS COMMUNICATION, NOT WIRING, and the obvious reading is wrong:** `v0.103.0`'s defect was a capability UNREACHABLE through the UI; this is not that. The CTAs exist and work. **Do NOT "fix" reachability — there is nothing to wire.** **⚠️ THE CORE IS VERIFIED AND CONCRETE: `board-exam-guide.tsx` contains "Review Set" ZERO times** — the guide for Board Exam does not mention what a Board Exam is now built from — and still advises *"one note per topic"*, which is now the SMALLEST possible Board Exam. `quiz-modes-guide.tsx` is equally stale. **This is the sweep-by-surface rule failing in the direction it always fails: a file that explains a feature is exactly the file that never changes when the feature does.** **Scope:** (1) the Help Center guides describe the product that exists, every claim anchored to code; (2) the capability is announced where learners already are — **the SURFACE is a decision owed at prompt time**, and a one-time tip must go through `pickActiveGuidance()`; (3) re-arm both lifted checkpoints dated from THIS deploy, **re-using their preserved reads and kill criteria verbatim**. **⚠️ ANTI-DRIFT: NO BEHAVIOUR CHANGE — this release changes what the product SAYS, not what it does. Do NOT add a second entry point; that would confound the very reads item 3 re-arms. No new mode or sub-mode; do NOT change `BOARD_EXAM_STARTED` or `ADAPTIVE_PRACTICE_STARTED` fields (both lifted checkpoints own them); slice 6 NOT started; onboarding stays frozen (`[CHECKPOINT — due 2026-09-11]`, 8 days out); marketing and pricing copy are OUT per `v0.76.0`.** **⚠️ VERIFICATION RISK WORTH NAMING: a copy release has no failing test to catch a false claim** — the guides went stale for four releases precisely because nothing executes them. Anchor each claim to code and prefer a pinned string over unchecked prose. **Verification: a single `advisor()` call. Routing: CLAUDE CODE inline.** Full scope in `RELEASES.md`.

**Kicked off and signed off 2026-09-03.** `v0.108.0 — Session Identity` is **Released** on `releases/v0.108.0`, cut from `main` after `v0.107.0` merged and tagged. **⚠️ DELIBERATELY SMALL, on measured grounds:** `v0.107.0` opened at three items, closed at five plus a **four-batch remediation**, and its three-agent pressure test found the feature's central mechanism had **never fired in production**. This release is scoped to what that release recorded and did not fix — **no migration, no new capability.** **⚠️ THE HEADLINE ITEM IS NOT PURELY INHERITED — `v0.107.0` MADE IT WORSE.** `DashboardService.findInProgressSessionsByRecency:990-1002` queries `(userId, ADAPTIVE, IN_PROGRESS)` with **no sub-mode filter**, and `continue-spotlight.tsx:55-58` routes it to `/notes/{id}/adaptive-practice`, so an in-progress Interview Practice session has always surfaced as an Adaptive Practice *Continue Studying* card. **Before `v0.107.0` that card rendered the interview session; item 4's guard now makes the adaptive read REFUSE it, so the card is a DEAD END this project created.** **Scope:** (1) the Continue card stops offering another mode's session; (2) `PostSessionNextStepService:160` stops giving an interview session Adaptive's copy; (3) eligibility resolution stops materializing every DONE pack — **verified viable WITHOUT touching the shared sampler** by reusing the existing `findProgressViewsByNoteIdIn` projection and filtering in Java, **⚠️ a REDUCTION not a bound**, and **⚠️ do NOT add a new projection query** (`StudyPackRepository:151-158` records a reproduced `ConverterNotFoundException` trap); (4) decide the unconsumed in-progress endpoint's fate rather than carrying it a second release. **⚠️ ANTI-DRIFT: NO migration — the session-anchoring migration stays deferred to slice 6 and is TEMPTING here precisely because it would dissolve the shared-discriminator problem outright; no new mode or sub-mode; slice 6 NOT started; the plan/note collision stays a named Known limitation; entitlements unchanged; `frontend/app/onboarding` stays frozen (`[CHECKPOINT — due 2026-09-11]` is 8 days out); do NOT change `ADAPTIVE_PRACTICE_STARTED`'s fields, which two checkpoints read.** **Verification: a single `advisor()` call**, chosen not inherited — no permission substrate, no cross-user read, no money semantics, no migration. **Routing: CLAUDE CODE inline.** Full scope in `RELEASES.md`.

**Kicked off and signed off 2026-09-03.** `v0.107.0 — Curriculum-Scale Remediation` is **Released** on `releases/v0.107.0`, cut from `main` after `v0.106.0` merged, tagged and **DEPLOYED TO PRODUCTION**. **Slice 5 of the approved assessment sequence** (`docs/claude-plans/assessment-architecture-audit.md` §13), **folded with the provenance debt slice 5 depends on.** **⚠️ THE FOLD IS AN EXPLICIT OWNER DECISION (2026-09-03) TAKEN AGAINST A RECOMMENDATION TO SHIP THE FIX ALONE, and the cost was stated before it was accepted:** the recommendation was two small items (the Interview Practice fix plus a docs-only checkpoint correction); the owner chose to add slice 5, which **forces the FULL THREE-AGENT COLD PRESSURE TEST IN ISOLATED `git worktree`s (~490k tokens)** — the same fold shape that made `v0.105.0` the most expensive release this project has run. The reasoning that makes it coherent is recorded so it is not re-derived: **slice 5 exists ONLY to consume per-source evidence, and Interview Practice is the last mode still corrupting that evidence**, so the two are one dependency chain rather than two adjacent features. **⚠️ THE FIX IS PROSPECTIVE AND SLICE 5 READS THE STORE IT DOES NOT REPAIR — ACCEPTED, NOT OVERLOOKED.** Verified against precedent rather than assumed: `v0.104.0` shipped **no backfill**, so `ConceptHealth` rows written by past multi-source Interview Practice sessions stay over-attributed. That is the direct argument for landing the fix **now** rather than deferring it a fourth time. **⚠️ THE DEFECT WAS RE-VERIFIED BY READING CODE AT KICKOFF AND IS SMALLER THAN THREE RELEASES OF HANDOFFS IMPLIED:** `recordConceptsForSourcePacks:270-298` broadcasts one concept list to every source pack filtered only by that pack's own `getKeyConcepts()`, and `generateQuizForSources:573-604` stamps nothing — **but `QuizSessionReviewUtils.computeKeyConceptBreakdownBySourceStudyPack` (`:173`) ALREADY EXISTS** and is already used by `LongExamService:508` with identical `keyConcept` semantics, so this is one stamp plus a near-verbatim port of `LongExamService:550-600` — **no new util, no new API, no DTO change, no migration.** **⚠️ A CORRECTNESS TRAP THE MULTI-PACK FIXTURE CANNOT CATCH:** Interview Practice calls the **3-arg** `computeFullyCorrectKeyConcepts(quiz, selectedChoices, Map.of())` where that `Map.of()` is `selectedMultiChoices`; the breakdown helper takes **5**, so a wrong mapping silently changes **what counts as correct** while appearing to change only attribution, and both packs move together. **⚠️ PRE-DECLARED GUARD: a SINGLE-SOURCE session must write byte-identical `ConceptHealth` before and after.** **Scope:** (1) per-source attribution in Interview Practice, with `resolveSourceStudyPackIds` **demoted to fallback-only** (left ambiguous, both run — aggregation PLUS broadcast — which is **worse than today**); (2) Subject-Plan- and Review-Set-scoped Adaptive Practice remediation, **existing Note-scoped Adaptive Practice UNCHANGED**; (3) docs-only re-specification of `[CHECKPOINT — due 2026-09-12]`'s **QUERY**, **a DEPENDENCY of item 2 rather than a third feature.** **⚠️ ITEM 3 WAS MIS-SPECIFIED AT THIS RELEASE'S OWN KICKOFF AND IS CORRECTED: the remedy was ALREADY re-specified 2026-08-14** (the checkpoint row carries it struck through); the kickoff read the **direction** row, whose *"must be re-specified now"* warning was written before the obligation was discharged and never updated. **What is actually owed is a re-specified QUERY: the metric is a BARE COUNT of `ADAPTIVE_PRACTICE_STARTED` per active learner that does NOT read entry metadata, so item 2's new start path would silently inflate it and the checkpoint would report *starts held* while the NEW surface did the work.** Filter by `entry` (which `v0.76.1` added for exactly this). **⚠️ ENTRY POINT DECIDED (owner, 2026-09-03): BOTH the collection detail page AND the dashboard, sharing one backend path.** The collection page already computes `due = sum(dueConceptCount)` (`collection-detail-page-client.tsx:224`) — **summation across packs, not concept merging** — and already offers a per-note *Review due concepts* CTA, so plan scope is the plan-level sibling of an existing CTA. The dashboard is in scope **because the ratified remedy names it** (*"the dashboard recommendation must carry that discovery load"*), not for symmetry; `DashboardFocusAreasResponse` carries one `practiceNoteId` today, so a contract change and a *which plan* ranking rule are priced in. **⚠️ ROUTE IS COLLECTION-ADDRESSED, forced by the anchor decision: a `?collectionId=` param on the note-addressed route would make the FRONTEND supply the anchor and reintroduce the drift hazard — do not use it.** **⚠️ `entry` (where) and `sourceScope` (what) stay SEPARATE fields; do not mint one entry value per scope. Sections are NOT a third scope.** **⚠️ QUOTA DECIDED AT KICKOFF (owner, 2026-09-03): ONE `adaptive_quiz_generations` unit per session regardless of scope or pack count, charged AFTER successful generation, generation staying SYNCHRONOUS INSIDE the transaction.** Both precedents charge one (`BOARD_EXAM_QUOTA_UNITS_PER_SESSION = 1` for a 30-question Review-Set Board Exam; `LongExamService:272` once for a multi-source exam), and per-pack charging is **rejected** — it changes what a unit buys and charges more for identical ≤10-question output. **⚠️ The no-refund property is an ACCIDENT OF ORDERING PLUS THE TRANSACTION** (`incrementAdaptiveQuizGeneration:220` runs after `markSessionReady` inside a class-level `@Transactional`) — **the same shape `v0.106.0` found in Board Exam. Do NOT copy the refund machinery here, and do NOT move the transaction boundary; the refund question opens only if it moves.** **⚠️ THE TRANSACTION DECISION IS VALID ONLY UNDER A BOUND, which is a PRECONDITION rather than a residual:** `InterviewPracticeService` fans out per source inside its own transaction, but bounded by a handful of learner-picked notes — **a Subject Plan is ~77 notes and a Review Set ~550, so that precedent licenses N≈3, never N≈77.** **Bound the sampled pack count by reusing `LongExamPlanSourceSampler`; if it cannot be bounded, the transaction must move and the refund question reopens.** **⚠️ A SECOND, EASILY-MISSED BOUND: `focusConcepts` is UNBOUNDED** — `resolveAdaptiveFocus` unions due and weak concepts with no cap and passes them **into the prompt**, while `resolveAdaptiveQuestionCount` (5/7/10) caps only the OUTPUT; bound it explicitly for plan scope and **do NOT change `resolveAdaptiveQuestionCount`**, whose lack of a source-count input already satisfies `v0.102.0`'s rule for free. **⚠️ The unlocked quota read is a PRE-EXISTING double-spend hole across DIFFERENT anchors — named, not introduced, not fixed here, and must NOT be "fixed" with `findByIdForUpdate`**, which is safe in `ChallengeQuizService` only because Board Exam generation left the transaction; here it would hold a `PESSIMISTIC_WRITE` across the LLM call. **⚠️ CONCEPT AGGREGATION DECIDED (owner, 2026-09-03): TWO entries, each STAMPED WITH ITS SOURCE PACK; never merge by concept string.** The repo already answers it — `getDueConceptsByStudyPackIds` returns per-pack unmerged lists (four existing callers), `DashboardService:400` builds `TodayFocusConceptResponse(concept, noteId, noteTitle)`, and `today-focus-card.tsx:62,65` renders the note title beside every concept, so **the product has never shown a bare concept name at multi-pack scope** and two same-named rows disambiguate visually rather than reading as a bug. Merging would be the cross-pack identity claim locked ADR-sized and OUT, and would be unfixable later without a migration. **⚠️ NECESSARY BUT NOT SUFFICIENT — THE COLLAPSE POINT IS A DATA STRUCTURE, the `v0.104.0` lesson on a new surface: `resolveAdaptiveFocus` returns a `LinkedHashSet<String>` of BARE concept names, harmless for one pack and silently merging the moment plan scope reuses it. The plan-scoped focus structure must be keyed by `(studyPackId, concept)`, and the pinning fixture must use the SAME concept string in two packs** — a fixture with different names passes under the merge. **⚠️ Count aggregation is unaffected** (`collection-detail-page-client.tsx:224` sums `dueConceptCount`; summing counts makes no identity claim) — **do not "fix" it to dedupe by name.** **⚠️ Dedup must accumulate ACROSS packs** (two packs weak on the same concept in one session is the highest-collision case), following `InterviewPracticeService.generateQuizForSources` and excluding each pack's own saved quiz, as `v0.106.0` shipped. **⚠️ "Which pack do we surface" already has an answer — `DashboardService:400` takes the first pack with due concepts over `findByOwnerUserIdOrderByCreatedAtDescIdDesc`, a deterministic total order (verified). Do NOT build a weakness-ranking engine; that is the recommendation engine's unratified scope.** **⚠️ ANTI-DRIFT: cross-pack canonical concept identity stays ADR-sized and OUT — plan-scoped remediation AGGREGATES over packs, never MERGES concepts across them; no new mode or sub-mode; viewing never writes `ConceptHealth`; entitlements unchanged; no backfill; the Challenge Quiz Adaptive Practice entry point is NOT removed; **the session anchor is DECIDED AT KICKOFF (owner, 2026-09-03) — the handoff's "§15 not needed" was right in conclusion but UNDERIVED.** Verified against schema: `study_pack_id` AND `note_id` are both `NOT NULL` and **`V41` recreates both active-session unique indexes as `WHERE status IN ('GENERATING','IN_PROGRESS')`**, so a still-GENERATING session already blocks; `ADAPTIVE` is a shared mode. **DECISION: the session stays anchored on a primary pack — the plan's LOWEST-POSITION eligible pack, since slice 5's dashboard-driven entry has no caller to supply one — and `sourceCollectionId` widens the SOURCE set only. No migration, no index change, no column**, matching `v0.105.0` and `v0.106.0`. **⚠️ `position` IS MUTABLE, so the resume must be resolved from a `(user, mode, status)` list filtered in Java on the recorded collection id, NEVER by recomputing the anchor** — recomputing would orphan a session and permit two active plan-scoped sessions; **no JSONB predicate (`v0.103.0` rejected it)**. **⚠️ ACCEPTED COST, ratified and shipping as a NAMED Known limitation with BOTH directions pinned:** a plan-scoped session active on pack X makes a note-scoped request on X resume the plan session, and vice versa — the reverse being the worse symptom. **Board Exam ↔ Challenge already ships this exact collision.** **The anti-drift guarantee is narrowed accordingly: note-scoped Adaptive Practice is not re-pointed, replaced or changed in behaviour, but MAY CONTEND for its anchor pack.** **⚠️ "No migration" is NOT "no contract change" — `generateAdaptiveQuiz` takes raw params with no DTO;** `frontend/app/onboarding` stays frozen until 2026-09-11.** **Routing: CODEX.** Full scope in `RELEASES.md`.

**Kicked off 2026-09-02, signed off 2026-09-03.** `v0.106.0 — Board Exam Review Set Identity` is **Released** on `releases/v0.106.0`, cut from `main` after `v0.105.0` merged, tagged and **DEPLOYED TO PRODUCTION**. **⚠️ THE DEPLOY, NOT THE MERGE, WAS THE GATE (owner, 2026-09-02).** `v0.105.0` reworked exam generation, added a pessimistic lock inside the Long Exam generation transaction and changed quota semantics on a paid path, and it carried a recorded residual; slice 4 operates on the **same machinery**, so branching it off the unmerged release branch was declined in favour of waiting for production evidence. **Slice 4 of the approved assessment sequence** (§8, §13). **⚠️ BOARD EXAM IS TODAY WHAT THE ANTI-DRIFT RULES FORBID BUILDING, re-verified at kickoff:** `MAX_ADDITIONAL_BOARD_EXAM_SOURCE_COUNT = 2` (`ChallengeQuizService:173`) makes the largest possible Board Exam **3 notes**, and `min(12 × sourceCount, 30)` caps it at **30 questions** — a rounding error against a ~550-note Review Set. **⚠️ THE FINDING THAT SHAPES THIS RELEASE IS NOT IN THE AUDIT, AND IT CHANGES THE SHAPE OF THE PREREQUISITE.** The audit lists *"move generation off the transaction"* but does not say what that **removes**. Verified by reading both services: `ChallengeQuizService.startSession` is a plain **`@Transactional`** (`:201`) with the Board Exam charge **INSIDE it** (`:403`) alongside synchronous generation, while `LongExamService.startSession` is **`@Transactional(NOT_SUPPORTED)`** (`:161`), dispatches after commit and charges outside. **So Board Exam's quota safety is an ACCIDENT OF ITS TRANSACTION — a generation failure rolls the charge back automatically, which is the only reason it has never needed a refund path. Moving generation off the transaction DESTROYS that implicit rollback.** **⚠️ The prerequisite is therefore THREE changes that ship together or not at all: (1) generation off the transaction; (2) the quota REVERSAL `v0.105.0` built for Long Exam, extended to Board Exam; (3) the RESURRECTION RACE GUARD extended with it. Shipping (1) alone reintroduces in Board Exam BOTH defects `v0.105.0` just fixed.** **Scope:** whole Review Set as eligible syllabus (Review Sets **are** hierarchical — `NoteCollectionEntity.parentCollectionId:69`); **stratify across Subject Plans then sample within, REUSING `v0.105.0`'s sampler** (**⚠️ strata, never WEIGHTS**); generation off the transaction **with** refund and race guard; and a **CONFIGURABLE** target item count (**⚠️ not a permanent universal constant**). **⚠️ CLAIM REPRESENTATIVE COVERAGE, NEVER OFFICIAL FIDELITY** — no board blueprint metadata exists and the scoring "domains" are free-text concept strings; **do not invent weighting, format mix or timing norms.** **⚠️ Board Exam stays MCQ-ONLY and does NOT become a session mode; `EXAM_MODES.md` stays a locked five-mode contract; entitlements unchanged.** **⚠️ Do NOT touch Long Exam's sampler, thresholds or refund path beyond extending them** — its production behaviour is the evidence slice 5 will read. **⚠️ OUT: cross-pack concept identity, format-weighted `ConceptHealth`, `InterviewPracticeService`'s over-attribution (STILL OWED before slice 5), the seven carried `v0.103.0` limitations, and the session-anchoring migration.** **⚠️ `[CHECKPOINT — due 2026-09-11]` is 9 days out; no onboarding code.** **Verification: FULL THREE-AGENT COLD PRESSURE TEST IN ISOLATED `git worktree`s — the isolation is NOT optional, and one agent must be pointed at the BLIND SPOT rather than the feature.** **Routing: CODEX.** Full scope in `RELEASES.md`.

**Signed off 2026-09-02.** `v0.105.0 — Curriculum-Scale Exams` is **Released** (kicked off the same day) on `releases/v0.105.0`, cut from `main` after `v0.104.0` merged and tagged. **⚠️ IT FOLDS SLICES 2 AND 3 BY EXPLICIT OWNER DECISION, OVERRIDING A KICKOFF RECOMMENDATION TO SHIP SLICE 2 ALONE.** The reasoning is recorded because it is sound rather than merely accepted: **slice 3's *representative curriculum coverage* IS slice 2's sampler applied**, and slice 3 is the one slice with a **hard dependency** on slice 2 — so building the sampler and re-opening it one release later is real waste, and of the available folds this is the one that respects the dependency order. **⚠️ THE COST IS STATED RATHER THAN ABSORBED: this fires the FULL THREE-AGENT COLD PRESSURE TEST (~490k tokens).** Slice 2 alone carries **two** independent triggers — production-data semantics **and** money/quota semantics — and slice 3 adds a question format to a scored assessment; the audit's §13 says *"at least four releases"*, which this makes three. **⚠️ THE DEFECT: THE SCOPE/COUNT CHAIN RUNS BACKWARDS, RE-VERIFIED AT KICKOFF BY READING CODE.** `ExamSourceLimitResolver.resolveMaxSourceNotes` is literally `questionCount / MIN_QUESTIONS_PER_SOURCE` (`:16-18`, constant **3**), and `questionCount` derives from the learner's **LEVEL** (20/25/30), never from scope — so the product reads **A ← B ← C**, exactly inverted, and **an eligible pool A does not exist as a concept anywhere.** A 77-note Subject Plan has **~69 notes that cannot appear in its own exam**; a 550-note Review Set, ~540. **⚠️ NOTHING BREAKS AT SCALE, AND THAT IS THE PROBLEM** — no prompt explosion, no timeout, because the cap discards the curriculum before generation ever sees it: **the architecture degrades by ignoring the syllabus rather than by failing loudly.** **Slice 2:** pool A explicit and **uncapped**; sample B = `min(|A|, C/3)` by **coverage buckets → spread → sample within → deterministic per-session randomization** (**⚠️ NOT `unpracticed-first` — rejected, because Long Exam is an assessment, not a recommendation engine; Sections help SPREAD coverage but are NEVER weights**); **C unchanged**; `ExamSourceLimitResolver` changes **meaning, not formula**, and stays the **one** place it lives; even split **within** the sampled set; anchor on the **sampled** primary; and **generation resilience coupled in**. **⚠️ THE QUOTA MECHANISM IS SHARPER THAN THE AUDIT STATES, and this changes the fix:** generation is dispatched **after commit, asynchronously** (`:266`) while the quota increment runs **synchronously** (`:254`), so quota is charged in a **different execution context** and a failure at `:310` leaves the learner **charged, with a `FAILED` session and no refund path** — *"move the increment later"* is therefore **not** the fix; it must move into the async success path or be reversed on failure. Owner decision 3 binds: a shorter valid exam **only** above a defined minimum assembly/coverage threshold, otherwise **fail without consuming quota.** **Slice 3:** Subject Plan **and** Student Study Plan sources (**the same entity — do not invent a distinction**); representative coverage via slice 2's sampler (**do not build a second one**); and **Identification, which is PLUMBING** — verified at kickoff that `LongExamProgressRequest` carries only three fields, `long-exam-developer.txt` has **zero** identification references against the Challenge prompt's **nine**, and the 6-arg `isAnswerCorrect` already exists — so one DTO field, two existing session keys, one overload swap, and **prompt rules COPIED verbatim**, because `QuizValidationUtils:48`'s notation guard was added after a reproduced production failure. **⚠️ NO True/False (deferred); no format-weighted `ConceptHealth`; no LLM semantic grading; BOARD EXAM IS SLICE 4 AND OUT; entitlements UNCHANGED.** **⚠️ STILL OWED AND NOT IN SCOPE: the `InterviewPracticeService` third instance (a live defect, must land before slice 5) and the seven carried `v0.103.0` limitations.** **⚠️ `[CHECKPOINT — due 2026-09-11]` is 9 days out; no onboarding code.** **Routing: CODEX.** **⚠️ SIGNOFF OUTCOME — THE MOST EXPENSIVE RELEASE THIS PROJECT HAS RUN, AND THE FOLD IS A MATERIAL CAUSE.** Two Codex passes delivered correct implementation and a fraction of the specified tests; the full three-agent cold pressure test then ran **TWICE**. Round one found **four live defects**: a learner could obtain a **free, usable exam** (the sweeper refunded a session mid-generation and the async completion resurrected it); **Identification could never fire**, because the Long Exam schema forbade it — half of slice 3 shipped dead; **repeat plan launches silently became single-note exams** while still reporting `sourceScope=plan`, the field a dated checkpoint reads; and **the branch shipped a red frontend test** with a picker whose selection was discarded. Round two, in **isolated worktrees**, found a **second copy helper re-running the non-idempotent sanitizer** — the same defect class as `v0.104.0`'s worst, corrupting choice text across three modes — and **a write race introduced by one of my own fixes**, removing an in-flight-save gate on a **server-graded** path. **⚠️ THE ROOT CAUSE WAS SINGULAR AND IS THE LESSON: guards were repeatedly written ONE LAYER BELOW the defect they named** — a schema test calling the builder with hardcoded booleans instead of the derivation that was broken; a race test pinning the read but not the lock; a `never()` assertion satisfied by a missing stub. **~18 mutants survived across the two rounds; all are now killed with named tests.** **⚠️ TWO OF MY OWN RECORDED CLAIMS WERE FALSE AND ARE CORRECTED IN `RELEASES.md`:** the reserved-before-charged window is NOT a "clamped no-op" (the clamp bounds at zero, not at the correct value, so it can refund a charge never made), and the phantom-`selectedChoices` consequence never reproduced. **⚠️ METHODOLOGY: round one ran three mutating agents in ONE working tree and they corrupted each other's builds — mutating agents need isolated `git worktree`s, and round two proved it.** **⚠️ RESIDUAL, RECORDED NOT CLAIMED: the commit closing round two's findings was not itself cold-reviewed** (each change in it was mutation-verified individually), and **the two frontend Identification-input fixes remain unpinned** — reverting either leaves the frontend suite green. **⚠️ NO CHECKPOINT OWED, ANSWERED NOT SKIPPED:** §0 of the assessment plan records no product or checkpoint gate on this initiative, and nothing shipped ahead of evidence. Plan-tier entitlements unchanged. Full scope in `RELEASES.md`.

**Signed off 2026-09-02.** `v0.104.0 — Assessment Source Provenance` is **Released** (kicked off the same day) on `releases/v0.104.0`, cut from `main` after `v0.103.0` merged and tagged. **Slice 1 of the approved assessment sequence** (`docs/claude-plans/assessment-architecture-audit.md` §13), and **the owner moved it AHEAD of curriculum-scale sampling on 2026-09-02** — representative sampling would push far more cross-source evidence through a mis-attributing writer, making a known defect worse exactly when more data flows through it. **⚠️ TWO INSTANCES, FAILING IN OPPOSITE DIRECTIONS, both re-verified at kickoff by reading code rather than the audit:** `LongExamService.recordConceptsForSourcePacks:494-521` **OVER-attributes** (the same concept list is written to every source pack, filtered only by that pack's own `getKeyConcepts()` — and sources from one Subject Plan share vocabulary **by construction**, so this is the normal case); `ChallengeQuizService.completeSession:674,680` **UNDER-attributes** (it writes to `saved.getStudyPackId()`, the primary only, so a multi-note Challenge lands every concept from all six sources on the note the learner started from). `QuizItem` carries no source-pack field, which is why neither can do better. **⚠️ THE CHALLENGE INSTANCE IS NEW AS OF `v0.103.0` — that release created a second instance of the defect this slice exists to fix.** **⚠️ THE RELEASE'S CENTRAL FINDING IS NOT IN THE AUDIT AND CHANGES THE SCOPE: the stamp is necessary and NOT SUFFICIENT.** `QuizSessionReviewUtils.computeConceptBreakdown:85` keys its counters by `normalizeConcept(item.concept())` **alone**, collapsing every source into one map before the correct/missed lists are computed — so if Note A got *Shear Force* right and Note B missed it, both packs are recorded incorrect. **A per-item stamp changes NOTHING downstream on its own**, and shipping only the stamp yields a green build with `ConceptHealth` still wrong in both directions — **precisely the "fix that looked correct and changed nothing" shape that has cost three releases running.** The release is therefore **two coupled changes**: provenance on the item **and** per-source aggregation on the write path. **⚠️ AND THE INDEX TRAP IS BINDING, NOT ADVISORY:** `computeConceptBreakdown` passes a **positional** index into `isAnswerCorrect`, and the selection maps are keyed by **absolute index in the session quiz array** — so filtering to one pack's items and reusing the helper on the sublist silently reads the **wrong learner answers** for every item after the first. One pass over the full list, bucketing by `(sourceStudyPackId, concept)`. **⚠️ THE FIELD GOES ON `QuizItem`, NOT A PARALLEL PER-INDEX ARRAY, and the repo settles it:** `ChallengeQuizService:391` shuffles the merged multi-source quiz **after** the merge and `:522` re-shuffles the redo round, so a per-index array would be **scrambled by a shuffle that already ships**; provenance must travel with the item. Dedup is pure normalized-string matching and never calls `QuizItem.equals`, so that objection is dead. **⚠️ GATING TAXONOMY (§0), kept apart: NO product or checkpoint gate on this initiative; engineering pre-signoff verification PRESERVED IN FULL; plan-tier entitlements UNCHANGED as a separate monetization contract — "nothing is gated" is never licence to add, widen or remove a subscription gate.** **⚠️ Out of scope: cross-pack canonical concept identity (ADR-sized), format-weighted `ConceptHealth`, curriculum-pool sampling / coverage blueprint / generation resilience (slice 2, with resilience COUPLED INTO it), True/False (deferred) and Identification in Long Exam (slice 3).** **⚠️ Quick Review and Adaptive Practice are single-source and stay untouched — do NOT refactor them into the new helper.** **⚠️ `[CHECKPOINT — due 2026-09-11]` is 9 DAYS OUT and `frontend/app/onboarding` is frozen; this slice is backend-and-evidence work and does not approach it.** **Verification tier: ONE SCOPED COLD AGENT framed as falsification — the trigger is that this changes what production evidence means. A discriminating fixture is PRE-DECLARED (two packs, one shared concept, NON-UNIFORM answers — a uniform fixture passes under the index bug).** **Routing: CODEX.** **⚠️ SIGNOFF OUTCOME: ALL FIVE PLANNED ITEMS SHIPPED, AND THE PRE-SIGNOFF COLD AGENT DISPROVED TWO CLAIMS AND FOUND THREE SURVIVING MUTANTS — all fixed before signoff.** **The worst was invisible to 1920 passing tests and had nothing to do with provenance:** `withSourceStudyPackId` routed through the sanitizing constructor, and `QuizValidationUtils.sanitizeChoiceText` strips a leading choice label with `replaceFirst` and is **NOT idempotent** — `"A. B. Smith"` → `"B. Smith"` → `"Smith"` — while `extractQuiz` calls that copy on **every deserialized item**, so quiz choice text was being corrupted on **every session load in every mode**, author initials first. **Second: the unknown-concept label diverged** (`Uncategorized` in `ChallengeQuizService`, `Unknown` in `QuizSessionReviewUtils`), and since `ConceptHealth` is keyed `(user_id, study_pack_id, concept)` the label IS the row identity — it would have forked the row and orphaned the accumulated streak. **Third: the new matching-group guard hard-failed LEGITIMATE multi-note sessions**, because `challenge-quiz-developer.txt` tells every generation to label its block `group-1` and sources are appended back to back; *fail loudly* was the wrong instruction and the fix is at the cause — the block scan now breaks on the source stamp. **Fourth: the ownership guard had ZERO executed coverage**, erased by this release's own `lenient()` stub returning a pack for any id — **the third release running where a check shipped behind a mock that made it untestable.** **⚠️ NO CHECKPOINT OWED, and this is ANSWERED rather than skipped:** §0 of the approved assessment plan records no product or checkpoint gate on this initiative, and nothing here shipped ahead of evidence — every item repairs a defect verified in code. Engineering pre-signoff verification was preserved in full; plan-tier entitlements are unchanged. Full scope in `RELEASES.md`.

**Signed off 2026-09-02.** `v0.103.0 — Mixed Retrieval for Free and Plus` is **Released**. **Kicked off and reshaped the same day**, after an assessment architecture audit superseded its "Slice 3" framing and made it **slice 0**: making an already-built capability reachable. **⚠️ THE PRE-SIGNOFF COLD AGENT FALSIFIED TWO OF SEVEN CLAIMS AND FOUND A DEFECT MY OWN CAP DECISION CAUSED: `+5 More Questions` WAS DETERMINISTICALLY DEAD ON EVERY MULTI-NOTE SESSION, AFTER PAYING FOR AN LLM CALL** — at 18 questions the headroom is 2, below the minimum viable batch, so it generated, threw, and the frontend swallowed the failure. It also falsified a javadoc written in the same release. **⚠️ AND THE USER-ROW LOCK WAS UNCONDITIONAL, HELD ACROSS LLM GENERATION** on every Challenge and Board Exam start, serializing an account's quiz starts and blocking two other services that write the same row. **⚠️ It also had ZERO coverage — the agent deleted it and all 1910 tests passed.** Both fixed and mutation-pinned. **⚠️ Three defects in three releases have now been "a fix that looked correct and changed nothing" or "a guard nothing exercised" — the recurring failure is verifying by reading rather than by breaking.** **⚠️ NO CHECKPOINT OWED, and this is answered rather than skipped:** §0 of the approved assessment plan states the initiative carries no product or checkpoint gate, and nothing here shipped ahead of evidence — the reachability fix repairs a demonstrated defect and the cap replaces arithmetic leakage with a decision. **Engineering pre-signoff verification was preserved in full; plan-tier entitlements are unchanged.** Originally kicked off on `releases/v0.103.0`. **⚠️ AN ASSESSMENT ARCHITECTURE AUDIT (`docs/claude-plans/assessment-architecture-audit.md`) SUPERSEDED THE "Slice 3" FRAMING: this release is now slice 0 of a seven-slice sequence, and its job is making an already-built capability REACHABLE.** The delivered multi-note Challenge was unreachable on every plan — the prestart derives its cap from a session object that is null at that point, and PRO never reaches the picker — **UI wiring, not architecture; the server side is correct.** **⚠️ The Plus cap of 4 is rejected as arithmetic leakage, and ~10 is UNREACHABLE:** the 20-question Challenge ceiling bounds sources at 6, so the owner ruled **18 questions → 6 sources** rather than lifting a ceiling `+5` depends on. **⚠️ Next release is SOURCE PROVENANCE, deliberately ahead of curriculum sampling.** **⚠️ Gating: none on this initiative; engineering verification preserved; entitlements unchanged.** Originally kicked off on `releases/v0.103.0`, cut from `main` after `v0.102.0` merged and tagged. **Slice 3 of the Review Sets audit, and the one slice with a HARD dependency on the last release** (§S2-D) — its Plus tier IS plan-sourced assessment, which only began working in `v0.102.0`. **⚠️ THE DEFECT IS A PRICING ONE AND SHARPER THAN "Free lacks a feature": PLUS HAS IDENTICAL MIXED-RETRIEVAL CAPABILITY TO FREE — NONE.** `isLongExamAvailable` is `longExamAvailableForPro && plan == PRO`, verified at kickoff, so Plus buys nothing on this axis. **⚠️ And the plan's own CTA is already dishonest about it:** `resolvePlanPremiumExamMode` resolves on `profileType` ALONE and never takes plan, while the paywall fires on plan — so Free and Plus learners are offered a concluding action they cannot use, and `PARENT` gets none. **⚠️ OWNER DECISIONS 2026-09-02:** the allowance is a **SUB-LIMIT INSIDE THE EXISTING METER** (~2 Free, ~10 Plus, config-backed, **no new meter, no migration, no column**), rejecting both "no separate limit" (a Free learner could run 20 multi-note sessions a month) and Plus-only (it drops the ladder's *experience the principle* rung); and the source cap is **Free 3 flat, Plus level-derived 6/8/10**, so **Plus versus Pro is modes and allowance, never an artificial note count** — a flat 5 for Plus was rejected as reintroducing exactly the arbitrary constant `v0.102.0` replaced. **⚠️ ONE §S2-X4 OBLIGATION IS ALREADY DISCHARGED — do NOT re-do it.** Slice 3 was predicted to falsify Slice 1's meter copy; the owner pre-empted that with the *one durable wording* ruling, and `v0.101.0`'s mode-agnostic description stays true when multi-note ships. A test pins that it names no mode. **⚠️ Engine decided and NOT a new mode (§S2-X2):** the Board Exam pattern, a `mode` string on the `CHALLENGE` discriminator. **⚠️ `EXAM_MODES.md` stays a locked five-mode contract; Pro is not weakened; Challenge Quiz length is unchanged.** **⚠️ `[CHECKPOINT — due 2026-09-11]` is 9 DAYS OUT — the closest the onboarding freeze has been to its date. No code under `frontend/app/onboarding`.** **Verification tier: ONE SCOPED COLD AGENT — the trigger is money/quota semantics, named outright by the gate.** **⚠️ ROUTING CORRECTION: this release goes to CODEX.** `v0.102.0` was implemented inline at 19 files and ~838 insertions, hitting three "→ Codex" rows at once; the routing call was made at plan time and never re-evaluated as scope grew. Full scope in `RELEASES.md`.

**Signed off 2026-09-02.** `v0.102.0 — Plan-Sourced Assessment` is **Released**. **All five items shipped, plus Board Exam, which was NOT in the plan and had to be.** **⚠️ THE RELEASE TURNED OUT TO BE A LIVE DEFECT FIX, NOT A NEW CAPABILITY** — the frontend had always pre-selected a plan's notes regardless of subject while the backend refused them, so the plan CTA told learners their own plan's notes *"must share the same subject."* **⚠️ THE PRE-SIGNOFF COLD AGENT FALSIFIED ONE OF FIVE CLAIMS AND FOUND THE RELEASE'S WORST DEFECT IN ITS OWN TEST COVERAGE:** deleting the collection-ownership check passed **1894 tests, zero failures**. **The mechanism is the lesson and it generalizes: that exact mutant had been run and KILLED earlier in the release, while the logic was inlined. Extracting it to a collaborator — the correct refactor — moved it behind a `@Mock` and silently deleted verified coverage. A pre-extraction mutation kill DOES NOT CARRY.** Two further defects fixed: `sourceCount` was **always 0** on `CHALLENGE_QUIZ_COMPLETED` (and a test PINNED the 0, so the fix would have read as a regression), and **`sourceScope` recorded the client's CLAIM rather than the verified outcome — the exact field this release's checkpoint reads, settable by the caller.** Claims 1, 2, 3 and 5 held under the agent's own mutation testing. **Ninth test-that-claims-more-than-it-proves in six releases, second this release.** **⚠️ A `[CHECKPOINT — due 2026-09-28]` IS OWED AND WRITTEN, joining the existing batch** — owner ruled 2026-09-02, reversing nothing, since `v0.101.0`'s no-checkpoint ruling was scoped to that release. **Originally kicked off** on `releases/v0.102.0`, cut from `main` after `v0.101.0` merged and tagged. **Slice 2 of the Review Sets audit, and the audit calls it the single highest-leverage change in the whole brief** — it is at once the assessment gap (§9), the retention loop (§25), the supporter gap (§15) and the Free-tier question (§16). **The gap: the Study Plan is first-class everywhere EXCEPT the one place that would prove its value — you cannot assess against it.** Verified at kickoff rather than inherited: `LongExamStartRequest` carries `difficulty` and `additionalStudyPackIds` and nothing else, so no collection id exists anywhere in the contract. **⚠️ THE KICKOFF'S FRAMING WAS RETRACTED THE SAME DAY, BEFORE IMPLEMENTATION:** it claimed plan-sourcing was *"genuinely new"* from the DTO carrying no collection id — **true fact, false conclusion.** The frontend already selects sources by collection MEMBERSHIP with no subject filter (`collection-exam.ts:44-53`), already pre-selects them when `?collectionId=` is present (`long-exam/page.tsx:319-336`), and the plan's own CTA already routes with it. **THE BACKEND REJECTS IT** (`LongExamService:842`). **So this is a LIVE DEFECT ON A PAID PATH and the behaviour is self-contradicting: a PRO learner presses the plan's CTA, the product pre-selects that plan's notes, and Start returns "source notes must be owned by you and share the same subject" — the product refusing a selection it made itself.** That reframes item 1 from *build it* to *stop refusing it*, and it needs a regression test pinning a mixed-subject plan-sourced start SUCCEEDING. **⚠️ The server must not trust a client-supplied `sourceCollectionId`** — the gate is caller-owns-collection AND every additional pack's note is a live member, or a validation rule becomes an opt-out flag. **⚠️ Interview Practice is NOT a parallel defect — checked, it enforces no subject rule at all.** **⚠️ THE SOURCE PREDICATE IS PLAN MEMBERSHIP AND THE OWNER MARKED IT "NOT TUNABLE AT ALL"** — `notes.subject` is free text matched by `trim().toLowerCase()`, so it already rejects notes a learner deliberately put in the same plan (§G3a); this replaces a wrong predicate rather than relaxing a rule. **⚠️ OWNER DECISION 2026-09-01 ON THE CAP, WHICH IS NOT A FREE CONSTANT:** `MIN_QUESTIONS_PER_SOURCE = 3` is checked against `questionCount / sourceCount`, and `questionCount` derives from the learner's LEVEL (20/25/30), not their selection — so the real ceiling is **6 / 8 / 10** and **a College learner, the default level, fails at 9.** The cap ships as `floor(questionCount / 3)` surfaced before starting, with the engine unchanged; scaling questionCount and lowering the per-source minimum were both rejected, the latter because per-note evidence is what Slice 4 reads. **⚠️ Slice 3 is NOT folded in** — §S2-D records it as a HARD dependency on this release, and folding would put a quota change in the same diff as a new source path. **⚠️ Whole-Review-Set sourcing stays DEFERRED BY DECISION**, because it overlaps Board Exam Mode and the two are decided together. **⚠️ The onboarding freeze is NOT engaged, verified not assumed:** every `BOARD_EXAM` match under `app/onboarding/` is `ProfileType.BOARD_EXAM`, not Board Exam **mode**, and the directory holds no Long Exam reference. **Verification tier: ONE SCOPED COLD AGENT framed as falsification** — the release changes what a scored assessment is built from, but adds no permission substrate, no cross-user read and no quota change. **⚠️ A `[CHECKPOINT]` is likely owed at signoff — `v0.101.0`'s no-checkpoint ruling was scoped to that release and does NOT carry.** Full scope in `RELEASES.md`.

**Signed off 2026-09-01.** `v0.101.0 — Language and Observability` is **Released**. **All three planned items shipped in one PR (#1216), and the release is deliberately three items** — CLAUDE.md's own guidance is that three-to-four items keeps the verification tier at a single `advisor()` call, and it held: the pre-implementation call caught the pricing-constant fork, the pre-commit call caught a carried flag that the sweep itself had made un-greppable. **⚠️ FOUR AUDIT CLAIMS WERE FALSIFIED AGAINST CODE WHILE IMPLEMENTING, each of which would have shipped wrong if trusted:** the Domain Context explainer was three sites not six; `AnalyticsEventType` was at 129 values not 108; the *"one string, six sites"* row spanned TWO string variants, the second still carrying "AI" at three further toast sites and found only by re-auditing every remaining occurrence; and `AI_QUIZZES_USAGE_LABEL` fed four pricing strings. **⚠️ NO CHECKPOINT IS OWED AND THIS IS ANSWERED, NOT SKIPPED (owner ruled 2026-09-01):** nothing shipped ahead of its own evidence — the copy corrects inaccuracies that exist today, and `NOTE_ADDED_TO_COLLECTION` is the instrumentation that makes a later read possible rather than a bet on one. Its fire site was verified emitting at `NoteCollectionService:991`, outside the enum, per the gate's own instrumentation rule. **Originally kicked off** on `releases/v0.101.0`, cut from `main` after `v0.100.0` merged and tagged. **Slice 1 of the Review Sets audit — three items: the quiz meter's label and description, the Category A copy sweep, and `NOTE_ADDED_TO_COLLECTION`.** It ships first because **instrumentation should predate behaviour change**; Slices 2 and 3 change what a Review Set can do, and measuring that afterwards measures nothing. **⚠️ NO GATES AND NO CHECKPOINTS (owner, 2026-09-01)** — nothing here ships ahead of its own evidence, so the signoff gate is answered *"none owed, owner ruled"* rather than skipped. **⚠️ TWO KICKOFF FINDINGS CHANGED THE SHAPE OF ITEM 1, both by reading code the handoff described rather than trusting it.** **(a) `AI_QUIZZES_USAGE_LABEL` is interpolated into FOUR `src/config/plans.ts` pricing strings**, so the audit's simple rename would have **silently rewritten public pricing copy** — a money surface edited as a side effect of a Settings fix, against `v0.76.0`'s doctrine. **The owner ruled the constant is SPLIT:** the meter reads `Quiz generations`, pricing reads `generated quizzes`; both lose "AI", neither reads as the other's surface. **(b) The candidate description carried into this release was FALSIFIABLE BY A ROW ON THE SAME SCREEN** — *"Long Exam, Adaptive Practice and Interview Practice have their own allowances"* sits directly above a **Board Exam** meter row, and Board Exam spends **both** meters. **The shipped wording discloses that double-spend instead of enumerating allowances, and is mode-agnostic so §S2-X4's Slice 3 falsification cannot reach it.** **⚠️ `[CHECKPOINT — due 2026-09-11]` is NOT engaged and this is verified, not assumed** — `frontend/app/onboarding/` contains **zero `AI` strings**, so no copy item can reach the frozen path. **⚠️ `AnalyticsEventType` is at 129 values, not the 108 the audit records** — corrected here rather than carried forward. Full scope in `RELEASES.md`.

**Signed off 2026-09-01.** `v0.100.0 — Domain Context Resolution` is **Released**. **NINE items — five at kickoff, one added and reduced the same day, one folded because the PRODUCTION BUILD WAS FAILING ON EVERY BRANCH, and two test-only carries.** **⚠️ THE SIGNOFF COLD AGENT FALSIFIED ONE OF ITS FIVE CLAIMS, AND IT WAS THE ONE THE CLAIM EXISTED FOR:** item 7 traded a failing build for a **silently 95%-truncated sitemap** — `PUBLIC_NOTES_MAX_SIZE` clamps `pageSize` to 50, so a request for 250 came back short with `hasMore=true` and the loop's length-based stop exited after page zero, returning **50 notes of ~950**. **⚠️ And the regression test written to prove otherwise was ITSELF vacuous, caught by mutating rather than reading** — it mocked a 250-item page the backend cannot produce, and its first replacement used a page size equal to the request, so the bad break still passed. **Sixth test-that-claims-more-than-it-proves this release, second that was ours.** Three further confirmed findings fixed: `GeneratedQuizService` reached an LLM prompt with **no readiness gate** (item 3 newly exposed every note-reading path, silently); admin copy claiming programs are edited *"without changing their generation context"*, **false for a note on Automatic**; and the `general` slug, the one filter the server cannot express. **Claims 1-4 held under mutation.** **⚠️ EVERY DELIVERY THIS RELEASE CARRIED A DEFECT PAST ITS OWN TESTS — five for five** — a stronger blind-spot signal than the gate currently escalates on, recorded for the next tier decision.  **⚠️ `v1.0.0` STAYS RESERVED and the minor rolls to THREE DIGITS** — the reservation (owner, 2026-07-23) is **conjunctive**, *live core AND product success*, and both halves are unmet, since the Stage 2 slices are recorded as *targeting* `v1.0.0`+. **SEVEN items — five at kickoff, one added and reduced 2026-09-01, and one FOLDED IN 2026-09-01 because the PRODUCTION FRONTEND BUILD IS FAILING ON EVERY BRANCH.** Item 7: `/notes/public` crossed Next.js's **hard 2 MB data-cache limit** (2,579,045 bytes), so ~250 static pages each re-fetch the full catalog until the backend saturates. **⚠️ Not a `v0.99.0` regression — data growth crossing a threshold a latent unbounded fetch always had, so reverting fixes nothing.** **⚠️ The fix needs no slug→label inversion** — the backend already matches on a slug normalized identically to the frontend's, so passing the slug is behaviour-preserving by construction. **⚠️ THE A–F IMPLEMENTATION AUDIT RAN 2026-09-01 AND RE-SHAPED THE RELEASE** (`docs/claude-plans/v0.100.0-domain-context-implementation-audit.md`): only **three** save-time sites move, since two enforcement points are **already at generation time**; and **`StudyPackService.startAsyncGenerationFromNote` — the primary path — VALIDATES NOTHING**, so removing the save-time block without adding a gate there **deletes the invariant rather than moving it**, silently. **⚠️ THE TAXONOMY QUESTIONS ARE SETTLED AND ARE NOT RE-OPENED** — no `ARCHITECTURE`, no catch-all, no removals; **the work is resolver + UX + mental model.** **⚠️ THE RELEASE IS FORCED BY A DEADLINE THAT CLOSES ON ITS OWN, not by theme preference:** Domain Context authoring emits **zero analytics**, nothing records what `Automatic` *would have* resolved, so the override signal — the strongest one the September read has — **cannot be reconstructed retroactively at all**, and `[CHECKPOINT — due 2026-09-28]` is 27 days out. **(1)** Decisions 1 and 4, **COUPLED** — `Automatic — use note context` **plus** removing the profile fallback for canonical curated generation; **⚠️ shipping the copy alone makes the label wrong again in a new way**, and **⚠️ learner personalization is NOT removed**. **(2)** Decision 2 — show the **effective** writing domain as derived state, **⚠️ no new persisted field and never a frontend re-implementation of precedence**. **(3)** Decision 6 — **⚠️ OWNER RULED 2026-09-01 to MOVE the existing save-time block to generation time, not except it**, taking the larger of the two options: *note validity ≠ generation readiness*, across four write paths and an existing error contract. **(4)** Decision 11 — one authoring event capturing *(what `Automatic` resolved, what was persisted)*; **⚠️ this is the item with the deadline**. **(5)** Decisions 3, 12, 13 — the copy carrying the mental model. **(6) ⚠️ ADDED THEN REDUCED THE SAME DAY, 2026-09-01 — a REGRESSION GUARD, test and comment only.** It was scoped in as a behaviour fix on the claim that a learner practising a curator's public pack inherits their own profile program — **that claim was MINE and it was FALSE.** Every quiz/exam path fetches the pack via `findByIdAndOwnerUserId`, so caller == owner by construction; the three sites that could diverge already pass the **owner**; and a learner cannot reach a curator's pack at all, because the public-note copy path mints a copy they own. **What ships is the guard**, because nothing in the build pins those three sites — a later "simplification" to `callerId` would CREATE the leak and ship green. **⚠️ Caught by the `advisor()` call BEFORE the Codex prompt was written, and cost a document edit instead of a release.** **⚠️ ITEM 3 RELAXES A SAVE-TIME INVARIANT, creating a production data state that has never existed** — a note with 2+ Applicable Programs and a NULL Domain Context — so the audit's Q6 invariant check changes meaning and must be updated in the same release. **⚠️ Tier RE-EVALUATED TWICE and HOLDS at one scoped cold agent** — first when the Decision 6 ruling widened the release, then again when item 6 was added. **⚠️ The cross-owner read considered for item 6 is WITHDRAWN** — the release contains no cross-user read at all. **⚠️ The literal item-3 escalation trigger DID fire and is recorded as fired** — it resolves to no escalation because the count moved for the opposite reason to the one it anticipated: two sites were already correct. New triggers replace the spent one. Previous: **Signed off 2026-08-31.** `v0.99.0 — Connection Completeness` is **Released**. **FOUR items — three scoped, one folded — and every `v0.98.0` Known limitation is closed.** **⚠️ THE SCOPED COLD AGENT FOUND THREE OF ITS FOUR CONFIRMED DEFECTS INSIDE ITEM 3'S OWN GUARD** — the test written to stop guards from looking present and doing nothing. It pinned the annotation default while `application.yaml` declares 8 of the 10 keys and **wins**; it scanned one package, so a bare-literal cron placed elsewhere passed **both** assertions at once; and its disablement check was an unanchored leaf substring a **comment** could satisfy, which armed the data-deleting purge job for every test run. **A fourth: nothing pinned the onboarding gate above the WRITE-ONCE birth-year write** — moving it below `persistBirthYear` left all 57 tests green, burning a permanent value for an invitation that was then rejected. All four fixed, each mutant re-killed with the killing test named. **⚠️ AND ONE FINDING WAS THE SIGNOFF SESSION'S OWN:** the `BP 344` correction cited the ratified spec as saying 344; **it says 334**, and a `RELEASES.md` misquote had been cited as proof of the spec. The change stands on the curriculum sources; the citation was circular and is corrected in place. **Kicked off 2026-08-31 with three items, all closing `v0.98.0` Known limitations.** **⚠️ FIFTH CONSECUTIVE RELEASE ON THE CONNECTION SURFACE, named at kickoff rather than reached by drift:** the wider backlog is checkpoint-gated until `2026-09-11` and `2026-09-16`, so this release chooses from what is un-gated. **The owner chose to finish the surface (2026-08-31)** on the reasoning that two half-built properties are worse than either finishing or never starting them. **(1)** An expiry stays visible after a **paused or backlogged sweep** — retention is keyed on the deadline, and `v0.98.0`'s own batch bound and pause hook create backlogs, so a late-swept request vanishes instantly and is **never shown as expired**. **⚠️ OWNER DECISION: a separate `expired_at` column**, so `expires_at` keeps meaning **the deadline for every status** — the table already carries a distinct terminal timestamp per status. **(2)** The onboarding bar reaches `invite()` and `accept()`, which still pass for a brand-new account — **the property is currently HALF TRUE, which reads as enforced and is not**. **(3)** A test pins the three configurable crons to their production defaults. **⚠️ ITEM 1 NEEDS THE THIRD AMENDMENT to the `v0.95.0` column prohibition** — raised explicitly; it **adds a new fact rather than reinterpreting an existing one**, and none of the three dated reads is affected. **⚠️ V130's BACKFILL IS THE TRAP: set `expired_at` from `expires_at` for `EXPIRED` ROWS ONLY** — `v0.97.0` got that shape wrong twice, both times by writing a timestamp onto rows that should have had none. **⚠️ Item 2 rejects the same two live cohorts, known in advance for the third release running, so the `/onboarding` self-heal ships WITH the gates.** Previous: **Signed off 2026-08-31.** `v0.98.0 — Connection Consistency` is **Released**. **NINE items — five scoped, four folded before the pressure test — and EVERY remaining `v0.97.0` Known limitation is now closed.** The email path requires finished onboarding like the link path; terminal connections fall out of the list on a retention window rather than accumulating; `V129` heals grants on relationships terminated before `v0.97.0`; a learner's sibling provisional declarations are cleared on promotion; the sweep is bounded and configurable; every scheduled job is disableable in tests; `DATA_MODEL.md`'s linked-learner section is current for the first time since `v0.89.1`; and two long-owed questions were answered rather than carried. **⚠️ THE PRESSURE TEST'S WORST FINDING WAS OURS AND WAS INVISIBLE TO A GREEN BUILD: a test had silently stopped running** — a helper inserted between `@Test` and its method moved the annotation onto a private static helper, so the class reported *Tests run: 0* while the suite passed. **A NEW variant of the failure mode this release already knew about** — not a test passing for the wrong reason, but a test not existing — found only because the agent EXECUTED tests rather than reading source. **⚠️ Also: the claim *"every scheduled job is disabled in tests"* was made one commit before it was true**, omitting five already-placeholder-driven jobs including one that DELETES DATA. Previously: **Kicked off 2026-08-30.** **Five items, ALL of them `v0.97.0` Known limitations or Backlog rows — nothing new is un-gated, and the ceiling is deliberate.** `v0.97.0` shipped nine items and paid the full three-agent pressure test **plus** a re-audit that found two of its own fixes weaker than claimed; five keeps the tier at **one scoped cold agent**. The through-line is consistency: **(1)** the **email invitation path** now requires finished onboarding too, since it forms the identical relationship as a link redemption behind a lower bar — **⚠️ owner-ruled 2026-08-30 to tighten rather than document the asymmetry**, and **⚠️ it WILL newly reject live accounts, known before writing the code this time**, because the frontend gates on `needsOnboarding()` and two cohorts sit in that gap, so **the `/onboarding` self-heal is part of the item**; **(2)** terminal `REVOKED`/`EXPIRED` cards become dismissible — **⚠️ whether dismissal is per-viewer state or a persisted field is a DATA MODEL decision owed before any code**; **(3)** a migration heals grant rows on relationships terminated before `v0.97.0`, **display-only and terminal-statuses-only, never the consent pause**; **(4)** the sweep's operational gaps — no `LIMIT`, no cron config hook — **with no change to what expires or when**; **(5)** `DATA_MODEL.md`'s linked-learner section, frozen at `v0.89.1` and missing four tables. **⚠️ `[CHECKPOINT — due 2026-09-11]` is 12 DAYS OUT and item 1 is onboarding-ADJACENT: no code lands under `frontend/app/onboarding`.** Previous: **Signed off 2026-08-30.** `v0.97.0 — Connection Lifecycle` is **Released**. **NINE items — everything un-gated on the Learning Connections surface**, after the owner lifted every gate at kickoff. Unconfirmed requests **expire** via a new `EXPIRED` terminal status; the provisional birth year reaches the **data export**; supporter onboarding is **defined and implemented**; terminal transitions **cut grant rows**; `requireVerifiedOnboarded` **actually requires onboarding**; a **two-thread real-row harness** pins the provisional-row invariant; invitation-link endpoint coverage goes **1 of 5 → 5 of 5**; the `docs/claude-*` cleanup ran at step 8; and the curated title rule reaches **both** title-emitting prompts. **⚠️ THE FULL THREE-AGENT PRESSURE TEST EARNED ITS COST, and the accounting is worth keeping: fourteen findings, then three more from a scoped RE-AUDIT of the fixes — and TWO OF THOSE THREE WERE FIXES THAT WERE WEAKER THAN CLAIMED.** `V128`'s backfill re-opened, at migration time, the pause-vs-expiry defect already fixed at runtime; the first repair (`greatest(created_at, now())`) merely **delayed** the termination by 30 days, so the migration now backfills **nothing** — the NULL *is* the protection. The claim *"nothing was open"* on item 5 was **wrong**: the frontend gates on `needsOnboarding()`, not the server value, so two account classes are newly rejected. And the landing page had asserted *"once they accept, you can see…"* **falsely for five releases**, kept alive by a feature-doc instruction telling each author to preserve it. **Previously:** **Kicked off 2026-08-29.** `v0.97.0` was **In Progress** with **seven items — everything un-gated on the Learning Connections surface**, the theme again acting as the scope filter after the owner asked to fold widely. **(1)** Unconfirmed requests **expire**, via a new `EXPIRED` terminal status; **(2)** the provisional birth year appears in the **account data export**; **(3)** supporter onboarding gets its **definition step, docs only**; **(4)** terminal transitions **cut grant rows**, one rule for revoke and expiry; **(5)** `requireVerifiedOnboarded` **actually requires onboarding**; **(6)** a **two-thread real-row harness** for the provisional-row invariant; **(7)** invitation-link endpoint security coverage **1 of 5 → 5 of 5**. **⚠️ TWO KICKOFF FINDINGS:** `v0.96.0`'s signoff **owed two checkpoints and wrote one** — the Domain Context calibration read had no date, so step 9 could never scan it; written here. And **item 1 collides with `[CHECKPOINT — due 2026-09-26]`, whose kill criterion names expiry as its own response and whose metric is a `COUNT(*)` on the rows the sweep deletes** — cleared only by arithmetic: a **≥30-day TTL dated from relationship `created_at`** puts the earliest expiry at 2026-09-28, after the read, which makes the TTL bound an **anti-drift rule, not a default**. Previous: **Signed off 2026-08-29.** `v0.96.0 — Authoring Integrity` is **Released**. **Five items, one folded mid-release**, all about what the product generates, names and shows. **(1)** Study Plan Builder reordering is deferred until an explicit **Save order** — dragging performs no network call, and non-drag mutations flush pending order rather than discarding it. **(2)** Summary renders maths via `remark-math` as a **tokenizer only**, through the **existing** KaTeX configuration — `rehype-katex` deliberately unused so the product keeps one math config. **(3)** Curated titles **name the knowledge, not the curriculum container**, as one unconditional rule in `developer.txt`, which had no title rules at all. **(4)** Domain Context doctrine recorded **without amending `ADR-001`** — the coarsest-context rule stands and the water cases became worked examples of it. **(5)** LaTeX commands stop being eaten by JSON parsing: `\times` written with one backslash is a **valid JSON escape**, so it was silently destroyed into `imes` — and because the mangled text is *shorter*, it passed every length check and was persisted. **⚠️ THE THEME WAS THE SCOPE FILTER, and it held**: three connection privacy/lifecycle items were kept out and remain `v0.97.0`. **⚠️ Two kickoff 'gates' turned out not to be gates** — onboarding already rendered maths, and the title read was a missing fact rather than a bar. Owes `[CHECKPOINT — due 2026-09-28]`, which **shares its trigger** with the Domain Context calibration read. Previous: `v0.95.1 — Rendering and Reorder Fixes` is **Released**.

`v0.100.0 — Domain Context Resolution` (on `releases/v0.100.0`, cut from `main` after `v0.99.0` merged and tagged) is **Released** — kicked off and signed off 2026-09-01. **SIX items — five at kickoff, one added and reduced the same day.** Full scope, traps and anti-drift: `RELEASES.md`. **⚠️ The constraints that are dated-read constraints rather than preferences: no code under `frontend/app/onboarding`** (`[CHECKPOINT — due 2026-09-11]`, 10 days out — verified at kickoff that the directory contains **zero** domain/AI strings, so no copy item can reach it), **and no taxonomy change of any kind** (`[CHECKPOINT — due 2026-09-28]`, whose subject is exactly the zero-usage question). **⚠️ `ARCHITECTURE` PASSED the governance floor and the exclusion trap and is STILL a provable no-op** — a value's entire generation payload is its label string plus one `quantitative` boolean, and a single-program Architecture note already sends `Domain: Architecture`. **"Architecture didn't qualify" is the WRONG takeaway and costs a release to re-derive.** **⚠️ ITEM 6 WAS ADDED AND THEN REDUCED THE SAME DAY (2026-09-01) TO A REGRESSION GUARD — test and comment only.** It was scoped in on the claim that a learner practising a **curator's public pack** inherits their own profile course program as the authoring domain. **THAT CLAIM WAS FALSE and is retracted at full strength in the audit — do NOT re-derive it.** Every quiz/exam path fetches the pack via `findByIdAndOwnerUserId`, so **caller == owner by construction**; `AdminStudyPackTransactionHelper:147`, `OfficialChallengeQuizTemplateService:221` and `ExamQuestionPoolService:151` **already pass the OWNER**; and a learner cannot reach a curator's pack at all, because `NoteService:405-411` mints a **copy they own**. **⚠️ `buildFromStudyPackFallback:159` MUST NOT BE "FIXED"** — it fires only for a pack the caller OWNS whose note is unreachable, where their own profile is correct. **What ships is the guard**, because nothing in the build pins those three sites and a later "simplification" to `callerId` would CREATE the leak and ship green. **⚠️ The cross-owner read is WITHDRAWN — this release contains no cross-user read at all.** **⚠️ A `[CHECKPOINT]` is owed as a DEPLOY-SPLIT CAVEAT joining `2026-09-28`, not as a new date** — this release changes the resolver inside that read's own window while adding the instrumentation the read depends on, so it spans two behaviours.

`v0.99.0 — Connection Completeness` (on `releases/v0.99.0`, cut from `main` after `v0.98.0` merged and tagged) is **Released** — kicked off and signed off 2026-08-31. **Four items.** Full scope, traps and anti-drift: `RELEASES.md`. **⚠️ The two constraints that are dated-read constraints rather than preferences: no code under `frontend/app/onboarding`** (`[CHECKPOINT — due 2026-09-11]`, 11 days out) **and no change to what expires, when, or from which clock.** **⚠️ `expires_at` must not be overwritten, re-purposed or backfilled — `v0.97.0` got that wrong twice**, and a NULL there is the entire mechanism protecting a consent-paused relationship.

`v0.98.0 — Connection Consistency` (on `releases/v0.98.0`, cut from `main` after `v0.97.0` merged and tagged) is **Released** — kicked off 2026-08-30, signed off 2026-08-31. **Nine items: five scoped, four folded BEFORE the pressure test** so the audited code is the shipped code. **The tier held at one scoped cold agent throughout**, because the folded items fired no new trigger — which is the contrast with `v0.97.0`, where nine items cost the full three-agent test plus a re-audit. Full scope, traps and anti-drift: `RELEASES.md`. **⚠️ The two constraints that are dated-read constraints rather than preferences: no code under `frontend/app/onboarding` (`[CHECKPOINT — due 2026-09-11]`, 12 days out), and no change to what expires or from which clock** (`[CHECKPOINT — due 2026-09-26]` and `[CHECKPOINT — due 2026-10-13]`). **⚠️ Item 1 tightens an authorization gate and is KNOWN in advance to reject two live account cohorts** — the `v0.97.0` pressure test identified them — so the `/onboarding` self-heal ships with it, not after.

`v0.97.0 — Connection Lifecycle` (on `releases/v0.97.0`, cut from `main` after `v0.96.0` merged and tagged) is **Released** — kicked off 2026-08-29, signed off 2026-08-30. **Nine items shipped** after the owner lifted every gate at kickoff. Scope, traps and anti-drift in full: `RELEASES.md`. **⚠️ The two load-bearing constraints, stated here because they are dated-read constraints rather than implementation preferences: the request TTL must be ≥30 days and dated from relationship `created_at`** (a shorter TTL or a different clock destroys `[CHECKPOINT — due 2026-09-26]`, whose metric is a `COUNT(*)` on `linked_learner_provisional_birth_years` — exactly the rows the sweep deletes), **and grant rows are cut on TERMINAL statuses only, never on the `ACCEPTED → PENDING` consent pause**, which `v0.93.0` made survivable by design. **⚠️ Item 4 is INERT by construction** (`requireGrant` demands `ACCEPTED`) and **item 5 TIGHTENS an existing gate** — neither is an authorization-boundary move, which is why seven items still resolve to **one scoped cold agent** rather than a full pressure test. **⚠️ AMENDED 2026-08-29, AFTER KICKOFF — OWNER LIFTED ALL GATES: NINE items, and the pre-signoff tier moves to the FULL three-agent cold-context test** (~490k tokens, `v0.93.0`'s measured cost). Item 9 applies the `v0.96.0` title rule to `note-generation-developer.txt` — the **same** rule, never a second formulation — and item 3 grows from a definition step into definition **plus implementation**. **⚠️ THE ONBOARDING READ IS PROTECTED EVEN THOUGH ITS GATE IS LIFTED, and this is the load-bearing distinction: `[CHECKPOINT — due 2026-09-11]` is a live measurement window with 13 days left, so editing the onboarding FLOW would DESTROY it, not confound it.** The code says both are possible — `app/linked-learners/invite/[token]/page.tsx` **traverses** `/onboarding` by carrying an opaque token in a cookie and never edits it — so supporter onboarding is built on the connection and redemption surfaces and **`app/onboarding/page.tsx` stays untouched.** **⚠️ If the definition concludes the flow MUST be edited, that is a NEW decision to bring back explicitly; it must not be taken mid-implementation.** Previously: **supporter onboarding was the DEFINITION step only, docs, no code** — it is undefined, not gate-blocked, and no code lands under `frontend/app/onboarding`.

`v0.96.0 — Authoring Integrity` (on `releases/v0.96.0`, cut from `main` after `v0.95.1` merged and tagged) is **Released** — kicked off and signed off 2026-08-29. **Five items shipped, one folded mid-release.** **(1) Deferred *Save order* model** — dragging performs no network call; **⚠️ three verified traps**: `refreshBuilder` calls `setLeafItems` from 13 call sites so non-drag mutations must **flush, never discard**; there is no "section order", only per-item positions, so the dirty check needs a **last-saved baseline**; and a 500ms debounced combobox writer fires on a timer a leave guard never sees. **(2) Summary renders maths** — **⚠️ the naive fix is wrong**, `_` is markdown emphasis; add `remark-math` for **tokenization only**, rendering through the **existing KaTeX setup**. **(3) Curated titles name the knowledge, not the curriculum container** — one unconditional rule in `developer.txt`; **⚠️ positive semantic wording only**, and `note-generation-developer.txt` stays OUT until the §5 read runs. **(4) Domain Context doctrine, docs only** — **⚠️ `ADR-001` is NOT amended.** **⚠️ A `[CHECKPOINT]` is owed for item 3 and SHARES ITS TRIGGER with the Domain Context calibration checkpoint; its metric is non-authoritative, so the two-stage rule is stated before the read.** Pre-declared: a single `advisor()` call on the diff.

`v0.95.1 — Rendering and Reorder Fixes` (on `releases/v0.95.1`, cut from `main` after `v0.95.0` merged and tagged) is **Released** — kicked off and signed off 2026-08-29. **Four items shipped, two folded mid-release.** **Two items, both frontend, both owner-reported.** **⚠️ Item 1 is a RENDERING gap and must not be used to close `v0.86.0-note-item-limit-mismatch.md`'s corrupted-escape item.** **⚠️ Item 2 is MITIGATION only.** **⚠️ `components/ui/summary-markdown.tsx` is OUT OF BOUNDS this release** — `_` is markdown emphasis so Summary math needs `remark-math` tokenization, and onboarding renders that component. **No backend change, no migration, no analytics event.** **Pre-declared: a single `advisor()` call on the diff**, and **no `[CHECKPOINT]` is owed** — both items fix a reported defect rather than shipping ahead of evidence.

`v0.95.0 — Redemption Integrity` (on `releases/v0.95.0`, cut from `main` after `v0.94.0` merged and tagged) is **Released** — kicked off and signed off 2026-08-29. **Seven items shipped** (three folded mid-release). **Four planned items.** **(1) DEFER THE BIRTH-YEAR WRITE UNTIL THE RELATIONSHIP IS CONFIRMED** (owner ruling 2026-08-29). Redemption stops writing `users.birth_year`; the redeemer's declared year is held provisionally in a **new side table keyed by RELATIONSHIP ID** and promoted only when the creator confirms. **⚠️ Holding it on `linked_learner_invitation_links` is UNBUILDABLE and must not be re-proposed** — `redeem()` makes that row terminal and `linked_learner_relationships` carries no link id, so consent and `accept()` have no path back to it. **⚠️ The deferral is from `users.birth_year` ONLY, not from the consent machinery** — `accept()` throws when the learner's year is null and the caller is not the learner, so deferring it out of reach would leave a supporter permanently unable to confirm a link they created. **⚠️ Guardian consent must keep working end to end for a link-redeemed minor while `PENDING` — that is the acceptance test for this item, not a side condition.** **⚠️ `users.birth_year` stays account-global and WRITE-ONCE** — this changes *when* it is written, never that it is written once, and `v0.89.1`'s correction path is untouched. **⚠️ Birth year is still collected from the LEARNER, never from the inviter**, which `AGENTS.md` states and a test pins. **⚠️ The email-keyed path is NOT changed.** **(2) THE LINK SURFACES STOP REPORTING STATE THEY CANNOT SEE** — the live-links list never refetches, so **Copy and Revoke can act on a dead link** and Copy hands out a token the server will reject; reloading after a successful redemption reads as a dead link; and the paused banner claims sharing will *"resume"* on never-accepted `PENDING` rows that link redemption newly creates, where it never started. **⚠️ The frontend genuinely cannot distinguish "granted, now paused" from "never granted"** (the DTO zeroes `*SharedWithMe` on a non-`ACCEPTED` row), so copy describes STATUS and must not guess at access — the rule `v0.94.0` applied to `linked-learner-status.ts` and `SharingPanel`. **(3) THE CONNECTION ACCESSIBILITY SET** — `birth-year-input` steppers nested inside `<label>` in three of four call sites, a raw checkbox where the shared `Checkbox` component is used elsewhere on the same page, disabled toggles left in the tab order. **(4) THE "QUIZ FOR SOMEONE" MODAL DISCLOSES THAT QUIZZES ARE AI-GENERATED** — copy only; **⚠️ no quota, limit, counter or metering change**, and the Challenge Quiz **mode name** stays distinct from the quota **label**, which a regression test pins. **⚠️ Standing rules unchanged:** acceptance stays load-bearing and redemption must become *less* consequential, never more; guardian consent is not bypassable by link and `v0.94.0`'s fail-closed unknown-age behaviour stays fail-closed; no account-existence oracle, and the single not-found contract for unknown/revoked/expired/redeemed tokens is preserved; absence of a live grant means no access; the progress read stays unidirectional; every read re-verifies `ACCEPTED`; invitations stay one-at-a-time; no relationship-type column, no new profile type, nothing gated on `ProfileType`; `NoteVisibility` stays `PRIVATE | PUBLIC`; no endpoint accepts a learner user id; no public people search. **⚠️ Do NOT touch `app/onboarding/page.tsx` or the signup → verify-email → onboarding path** — `[CHECKPOINT — due 2026-09-11]`'s **✅ DISCHARGED 2026-09-07 — THE READ WAS TAKEN FOUR DAYS EARLY AND THE FREEZE IS LIFTED (owner). 393 signups all-time, 249 completed, 63.4% against the 62.4% baseline — essentially flat. ⚠️ THE DECIDING FACT IS THE DENOMINATOR, NOT THE RATE: the post-`v0.73.0` cohort is EIGHTEEN signups (15 completed, 83.3%), and at ~0.7 signups/day the 2026-09-11 date would have added about three more. 83.3% on n=18 is noise, so THIS CHECKPOINT WAS NEVER GOING TO BE ANSWERABLE ON ITS OWN DATE — the freeze was protecting a read that cannot be taken, which is the underpowered-denominator failure this Index's own doctrine names. ⚠️ Do NOT re-freeze onboarding for it, and do NOT quote 62.4% or 83.3% as current.** 62.4% baseline cannot be re-run. **⚠️ Deprioritized deliberately, not forgotten:** `revoke()` not revoking grant rows (recorded inert) and the 1-of-5 endpoint/cross-mode coverage items both stay in `v0.94.0`'s Known limitations. **Pre-declared at kickoff:** one scoped cold agent framed as falsification (single trigger — a privacy boundary and guardian consent; no permission substrate, no new cross-user read, no money/quota semantics), the permanence of `RELEASES.md`'s `### Planned Scope` heading, a `[CHECKPOINT]` row owed for item 1, real-row tests for any predicate deciding whether a provisional year is promoted, and sequencing the pressure test's agents after `v0.94.0`'s false finding from running a read-only auditor beside a mutation pass.

`v0.94.0 — Connection Experience` (on `releases/v0.94.0`, cut from `main` after `v0.93.0` merged and tagged) is **Released** — kicked off 2026-08-28, signed off 2026-08-29. **All FIVE Planned Scope items shipped** (two folded in mid-release), and a FULL cold-agent pressure test found no authorization bypass. **Phase 4 of the ratified Learning Connections direction.** **⚠️ Phase 4 is a SINGLE LINE in the plan** — *"shareable invitation links, supporter onboarding, connection management"* — **so it was audited against code before scoping rather than trusted**, the method `v0.89.0` used on Phase 1, and the audit is the reason this release is three items rather than a vague three headings. **Finding 1: supporter onboarding is BLOCKED** — onboarding is frozen until `[CHECKPOINT — due 2026-09-11]`, 14 days out, so it is OUT as sequencing and becomes a clean `v0.95.0`. **Finding 2: "resend an invitation" is HALF-BUILT and the built half is the hard half** — there is no resend endpoint anywhere (verified across all of `backend/src/main/java` and `frontend/lib/api.ts`), **but re-inviting the same address already RE-ARMS the expired row by updating `expires_at`**, guarded by `LinkedLearnerInvitationReArmTest`. What is missing is that nobody is told: `listInvitations` filters on `findByInviterUserIdAndStatusAndExpiresAtAfter`, so **an expired invitation SILENTLY VANISHES from the inviter's list** — they cannot tell whether the person declined, ignored it, or the clock ran out — and `LinkedLearnerInvitationResponse` carries no `expiresAt` for any surface to render. **Finding 3: shareable links are a security design, not a feature.** **⚠️ Raised as a deferral; the owner ruled to scope it now (2026-08-28)**, accepting three constraints as anti-drift: a link **bypasses email-keying**, the exact mechanism `v0.90.0` shipped to close the account-existence oracle; whoever holds it would otherwise become the counterparty, cutting against `v0.89.0`'s *acceptance is load-bearing* ruling; and it must still route a minor through guardian consent, since birth year is collected at link time from the learner. **Item 1 fixes a carried `v0.93.0` defect rather than deferring it twice:** two cold agents independently found that `LinkedLearnerProgressResponse.engagement` is byte-for-byte the payload the `ACTIVITY` scope gates, so a live `PROGRESS` grant makes the learner's activity toggle **inert**. **⚠️ Owner chose SPLIT (2026-08-28)** — remove `engagement` from the progress payload — over coupling the toggles or fixing the copy, because it is the only option in which turning activity off actually does something. **⚠️ A supporter holding only `PROGRESS` therefore loses streaks and study days; that is intended.** **⚠️ Invitations stay ONE-AT-A-TIME** — a link delivers one relationship, the quiz share link remains the many-recipient mechanism. **⚠️ No public people search**, excluded from Phase 4 by the plan. **⚠️ Do not change what `linked_learner_invitations` means** — `[CHECKPOINT — due 2026-10-13]` reads it, and making expiry visible may itself change the acceptance rate it measures, so item 3 owes a deploy-split caveat recorded before that read. **Pre-declared: a FULL cold-agent pressure test**, because the release fires two of the gate's triggers — a link-based authentication path AND a permission-payload change. Full scope and anti-drift rules in `RELEASES.md`.

`v0.93.0 — Progress Refinement` (on `releases/v0.93.0`, cut from `main` after `v0.92.0` merged) is **Released** — kicked off 2026-08-27, signed off 2026-08-28. **All TEN Planned Scope items shipped** (four folded in mid-release). **Phase 3 of the ratified Learning Connections direction.** `v0.92.0` shipped the permission substrate and used one of its two scopes; Phase 3 uses the other. **⚠️ The justification is a finding `v0.92.0`'s own cold-agent pressure test recorded and was FORBIDDEN to fix**, because that release reserved the helper for this one: `LinkedLearnerProgressService.getProgress` gates on `requireAcceptedLearnerId` alone — **no grant check** — and returns `getStudyEngagement(learnerUserId)` verbatim, the **identical four fields** Phase 2 put behind a grant, plus a superset (mastery, readiness, collection progress). **So the Phase 2 activity toggle is a real control that does not bound what a supporter sees.** Re-verified at this kickoff: `LinkedLearnerReadAuthorizationService:20-25` is one `ACCEPTED` check plus one `caller == supporter` check, and that is the entire gate on the product's only cross-user read. Not a `v0.92.0` regression — the service is unchanged `v0.89.0` code. **Scope:** `PUT /linked-learners/{id}/grants/progress` with the grant service scope-parameterized behind both paths and `PUT /grants/activity` kept working; `requireAcceptedLearnerId` reimplemented over `requireGrant(..., PROGRESS)`; the per-scope permission UI; three progress funnel events; and two carried `v0.92.0` limitations. **⚠️ DECIDED AT KICKOFF: the progress toggle renders on the LEARNER side only and the endpoint enforces it** — the read is unidirectional, so a `PROGRESS` grant written `supporter → learner` is a row nothing can consume; **granting `PROGRESS` requires the caller to be the learner**, which makes `progressSharedWithMe` true only for a supporter, so ***View progress* gates on it alone** with no residual `callerRole` clause. *Activity is mutual; progress runs learner → supporter.* **⚠️ NO MIGRATION and no new scope value** — `V125` ships `scope IN ('ACTIVITY','PROGRESS')`, which is why Phase 2 shipped both. **⚠️ THE READ STAYS UNIDIRECTIONAL, and this is the trap in the reimplementation:** `requireGrant` returns the *other party*, which is the **supporter** when the caller is a learner, so a thin passthrough would silently ship "a learner reads their supporter's mastery" with the display name mislabelled. §4's per-direction table describes the grant **row**, not the endpoint. **Keep the caller-is-supporter assertion explicit.** **⚠️ THE BREAKING SEMANTIC, STATED PLAINLY: an `ACCEPTED` relationship no longer implies progress access** — that is the release, not a side effect. **⚠️ NO BACKFILL granting `PROGRESS` to existing `ACCEPTED` relationships**: production was empty on 2026-08-26 so it would be free, and it is forbidden anyway, because it converts an implicit rule into explicit consent nobody gave. **⚠️ PROGRESS CONTENT IS UNCHANGED** — this permissions an existing payload and adds no field; Phase 5's comparison, scores, leaderboards, rings, feed, reactions and people search all stay out, and Phase 4 is the next release. **⚠️ One carried limitation is now BLOCKING rather than inert:** `toResponse` computes the grant fields with no relationship-status filter, and Phase 3 breaks both premises that made it inert — the DTO becomes the UI's access rule, and the reachable case is not terminal `REVOKED` but **`PENDING`**, since a `v0.89.1` birth-year correction pauses the relationship while the live grant row survives by design. **⚠️ The filter has a DIRECTION and adding it blindly inverts the lie:** `*SharedByMe` reflects the ROW (the caller's standing act of sharing, and what resumes), `*SharedWithMe` reflects ACTUAL ACCESS and filters on `ACCEPTED`, and a paused relationship renders as paused rather than as off. **⚠️ Pre-declared at kickoff:** a FULL cold-agent pre-signoff pressure test (shared authorization code, and it flips the only cross-user read from a role check to a permission check); the permanence of `RELEASES.md`'s `### Planned Scope` heading; the falsified `learning-connections-guide.tsx:8` Phase 3 instruction, named now so the `v0.92.0` doc-comment defect cannot happen twice; and a `[CHECKPOINT]` row carrying the deploy-split caveat, because Phase 3 changes the progress surface inside `[CHECKPOINT — due 2026-09-26]`'s window. Full scope and anti-drift rules in `RELEASES.md`.

`v0.92.0 — Activity Sharing` (on `releases/v0.92.0`, cut from `main` after `v0.91.0` merged and tagged) is **Released** — kicked off and signed off 2026-08-27. **All ELEVEN Planned Scope items shipped** (five folded in mid-release, one of them a defect this release created, plus a live `v0.91.0` production 500 found by the owner). **Phase 2 of the ratified Learning Connections direction**, plus the quota-legibility item carried out of `v0.91.0`. **Item 1 — Phase 2:** a `linked_learner_grants` table (`relationship_id, from_user_id, to_user_id, scope, granted_at, revoked_at`), `scope IN ('ACTIVITY','PROGRESS')`, live-row unique index on `(relationship_id, from_user_id, scope)`; a `requireGrant(caller, relationshipId, scope)` check; directional opt-in UI; and a momentum view. **⚠️ Ship the table with BOTH scopes and use only `ACTIVITY`** — Phase 3 uses `PROGRESS`, and this is the plan's only cross-phase coupling, so it is one migration, not two. **⚠️ Absence of a live grant means NO ACCESS** — accepting a connection grants nothing, it creates the *capacity* to grant. **⚠️ Sharing is DIRECTIONAL and never reciprocal by default**: A→B activity ON with B→A OFF must be representable. **⚠️ NO NEW MEASUREMENT** — `ActivityType.MEANINGFUL_STUDY_ACTIVITIES` already defines what *studied* means and deliberately excludes `OPENED_STUDY_PACK`; `UserActivityEventEntity` rows, streaks and study-days already exist, so Phase 2 is a **permissioned projection of data already written**. **⚠️ Do NOT touch `LinkedLearnerReadAuthorizationService.requireAcceptedLearnerId`** — it hardcodes caller-is-supporter and **Phase 3** reimplements it over `requireGrant(..., PROGRESS)`; rewriting it here changes who can read progress in a release that is not about progress. **⚠️ Guardian consent must be re-asserted inside the grant check**, or `v0.89.1`'s gate quietly reopens — a consent lapse must cut activity as well as progress. **⚠️ No relationship-type column** (`GUARDIAN | TUTOR | PARTNER`): permissions define the relationship, and a type column would invite gating on it — the `ProfileType` mistake `v0.89.0` exists to correct. **⚠️ Explicitly OUT and each needing its own decision (Phase 5): mastery, scores, leaderboards, comparison between people, activity rings, social feed, reactions, public people search.** **Item 2 — quota legibility, a CARRY:** it was folded into `v0.91.0` on 2026-08-27, never built, and **missed by that release's signoff gate**, so it is written into Planned Scope at kickoff rather than carried in conversation. Two verified disclosure defects, neither of which changes any limit: `GeneratedQuizService.assertQuizCreditAvailable` spends `user_usage.challenge_quiz_generations` for a quiz made **for someone else** while every surface labels that meter *Challenge Quiz*; and `QuizShareLimitService.assertShareLinkQuotaNotExceeded` has **exactly one call site — link creation** — so a Free user pays the LLM cost for a 4th, 5th and 6th quiz before learning none can be shared. **⚠️ DISCLOSURE ONLY — no limit, counter or metering change, and no second counter.** **⚠️ Do NOT move the share-link check into the generation path**: generating without sharing is legitimate, so surface the cap earlier, do not apply it earlier. **⚠️ The quota LABEL and the Challenge Quiz MODE name are different strings**, pinned by a regression test. `MePlanResponse` must carry the share-link limit; it does not today. **⚠️ PHASES ARE SEQUENCED, NOT EVIDENCE-GATED (owner, 2026-08-27).** `linked_learner_relationships` was **empty in production on 2026-08-26**; `v0.91.0` fixed the two reasons nobody could discover the capability, but adoption may still be zero. **That is not a reason to gate Phase 2 — the owner has ruled twice — but it is a reason not to describe Phase 2's value as proven.** `[CHECKPOINT — due 2026-09-26]` and `[CHECKPOINT — due 2026-09-19]` are observational and gate nothing here. **Pre-declared at kickoff: a FULL cold-agent pre-signoff pressure test**, because Phase 2 introduces a permission substrate touching shared authorization code and `v0.91.0` found its blocking defect in code that session had itself written and reviewed. **Also pre-declared: the `### Planned Scope` heading in `RELEASES.md` is permanent and delivery appends to `### Shipped`** — `v0.91.0`'s scope miss happened because delivery renamed that heading and the gate ran from memory. Full scope and anti-drift rules in `RELEASES.md`.

`v0.91.0 — Shared Learning Material` (on `releases/v0.91.0`, cut from `main` after `v0.90.0` merged) is **Released** — kicked off and signed off 2026-08-27. **Phase 1 of the ratified Learning Connections direction** (owner, 2026-08-27), and the release exists because of one audited fact: **sharing a note today means making it PUBLIC.** `NoteVisibility` has two values, and the Share action on a private note tells the owner they must publish it first — so a parent giving their child one Study Pack must publish it into Explore for everyone. Scope: a `note_shares` grant table, share/unshare and `shared-with-me` APIs, authorized recipient reads for the note **and** the Study Pack, the three-option *"Who can access this note?"* control, a *"Shared with you"* Library section, Copy to my Library, and five funnel events. **⚠️ `NoteVisibility` STAYS `PRIVATE | PUBLIC` — do NOT add a `SHARED` value:** every read path is `findByIdAndOwnerUserId` so the enum grants nobody anything, `AccountPurgeService` retains `PUBLIC` and deletes `PRIVATE` so a `SHARED` note would **survive the purge of a deleted account while staying readable by its recipients**, and the enum has 42 usages across 24 files. A shared note stays `PRIVATE` and is therefore excluded from Explore and included in purge **by default rather than by 24 correct decisions**. **⚠️ The three-way choice is DERIVED, never stored.** **⚠️ Selecting *Private* revokes every live share** (owner decision) while *Public* revokes none. **⚠️ Connecting shares NOTHING** — a relationship creates the capacity to grant, and nothing is ever reciprocal by default. **⚠️ The owner's mastery, scores and practice history never travel with shared material.** **⚠️ The recipient pack read must not call `recordActivity` with the owner's id.** **⚠️ Every read re-verifies `ACCEPTED`**, so a revoke or a `v0.89.1` birth-year correction cuts access immediately. **⚠️ The PHASES ARE SEQUENCED, NOT EVIDENCE-GATED (owner, 2026-08-27)** — with zero connections in production a gating read returns nothing, so gating would be an indefinite stop; checkpoint rows are still recorded but are observational and block nothing. **⚠️ Do not change what `linked_learner_relationships` or `linked_learner_invitations` mean** — two dated checkpoints read them. Full audit and five-phase plan: `docs/claude-plans/learning-connections-phase-plan.md`.

`v0.90.0 — Invitation Integrity` (on `releases/v0.90.0`, cut from `main` after `v0.89.1` merged) is **Released** — kicked off 2026-08-20, signed off 2026-08-26. Executes the surfacing decision ratified 2026-08-20 plus the security half of the invitation model. **⚠️ Checking the ratified list against code before scoping found the product ALREADY complies with half of it** — supporter surfaces are gated on an accepted connection (`supported-learners-card.tsx:10, :27-28`) and the nav item is already unconditional (`app-shell.tsx:408`) — **so those two are recorded as needing no work, not as scope.** Real scope: **(1)** move *"Quiz for someone"* out of the Study Pack practice row into the existing note-actions menu, because it is a support/share action and beside *Start Quick Review* it risks becoming an avoidance path on the surface retention is decided; **⚠️ it stays available to everyone, gated on nothing, because a shared-quiz recipient needs no account and no relationship.** **(2)** **email-keyed invitations** — store the invitation against the typed address rather than a resolved user id, which closes the account-existence oracle (today an unknown address writes no row while a real one writes a `PENDING` row visible in the inviter's list) and unlocks inviting someone who has not signed up yet. **(3)** a proper `BackLink` on the supporter progress sub-page, a recorded `v0.89.0` UI-standards violation. **⚠️ Invitations stay ONE-AT-A-TIME by principle — the quiz LINK is the many-recipient mechanism — and email-keying must NOT be used as a reason to add multi-recipient invites.** **⚠️ Do not change what `linked_learner_relationships` means:** `[CHECKPOINT — due 2026-09-19]` reads it, and an unresolved email-keyed invitation is not an accepted connection. Full scope in `RELEASES.md`.

`v0.89.1 — Birth Year Correction` (on `releases/v0.89.1`, cut from `main` after `v0.89.0` merged) is **Released** — kicked off and signed off 2026-08-19/20. All four planned items shipped. **No checkpoint is owed** — it changes no measurement and must not change what `linked_learner_relationships` means, which `[CHECKPOINT — due 2026-09-19]` reads. **`users.birth_year` is account-global and WRITE-ONCE with no correction path**: `LinkedLearnerService:203` is the only writer in the codebase and all three read sites guard on `== null`, so the first value ever recorded is permanent. A learner who declares an adult year — **by mistake, or coached by a supporter who wants the consent step gone** — permanently disables guardian consent for **every future supporter link they ever form**, not just the one being negotiated. Found by `v0.89.0`'s cold pressure test and recorded there rather than fixed in scope. **⚠️ The load-bearing half is item 2, not item 1:** a downward correction must re-evaluate existing links, because an `ACCEPTED` link without consent on a learner who turns out to be a minor is exactly the state the gate exists to prevent, reached by a route the gate does not currently watch. **⚠️ It reverts those links to `PENDING`, never REVOKED** — the gate is about consent, not about ending a connection both people agreed to. **⚠️ Do NOT move birth year into signup, onboarding or profile editing** — `v0.89.0` collects it at link time only and has a test asserting signup and profile leave it null, and onboarding stays frozen until `[CHECKPOINT — due 2026-09-11]`. **⚠️ Only the learner may correct their own year**; letting a supporter do it would hand the gate to the party it exists to check. **⚠️ Do NOT retain a history of declared values** — it is a minor's personal data, and `attested_at` on the consent record already reconstructs "consent was attested while we believed they were older". The threshold number stays owner-owned pending counsel. Full scope in `RELEASES.md`.

`v0.89.0 — Support Another Learner` (on `releases/v0.89.0`, cut from `docs/ungate-reads` so the `v0.86.0` sweeper verdict and two production reads ship inside the release PR; PRs #1121 and #1122 closed unmerged) is **Released** — kicked off 2026-08-19 as *Regeneration Integrity*, **rescoped the same day**, widened to Phases 2 and 3, signed off 2026-08-19. **⚠️ The cold pressure test found FIVE blocking-class defects in code that had shipped green through four merged PRs**, the worst being that the release's headline capability was unreachable in the UI for its entire target population — the backend gate was correct and delivered zero user-visible value. All fixed and mutation-verified. Owes `[CHECKPOINT — due 2026-09-19]`. **⚠️ THE RESCOPE REASON MATTERS AND MUST NOT BE MISREAD: the original item 1 was a live-probe gate — reproduce the regeneration defect before fixing it — and it was deferred for want of owner time. The hypothesis was NOT tested and NOT killed.** This is *not* the `v0.87.0` case, where a probe ran and falsified a premise; **nothing was learned about regeneration**, and its row stays open at full strength. The harness is preserved at `docs/claude-plans/v0.89.0-regeneration-reproduction.md` so the next attempt starts where this one stopped. **The replacement was ready because its gate was a DECISION, not effort** — *"does Phase 1 already largely exist? Audit that before scoping"* — and **the audit ran 2026-08-19 and found that it does.** Generating a quiz for someone else is **ungated**; `/quiz/share/**` is `permitAll` (`SecurityConfig:63`), so the recipient needs **no account**; `QuizShareLimitService` already meters share links per plan (FREE 3 / PLUS 10 / PRO unlimited). **The single blocker is `QuizShareLinkService.requireTeacherOrAdmin:160-166`.** **⚠️ It is an AXIS ERROR, which is what makes it a defect rather than a missing feature:** `ProfileType` answers *"how do YOU learn?"*, not *"may you help someone?"* — so a parent helping a child must **misrepresent their own learning profile**, and `ProfileType` drives dashboard emphasis, quiz-mode availability and generation behaviour, meaning the workaround degrades the helper's own product. **⚠️ Phase 1 records NO relationship** — no linking, permissions model or cross-user read, which are Phases 2–3. **⚠️ Consent, minors and DPA do not apply and must not be built here.** **⚠️ Do not justify on retention** — the supporter's return loop is the Phase 3 progress view, **which this release now also contains.** **⚠️ WIDENED 2026-08-19 TO PHASES 2 AND 3 at owner request** — Phase 2 alone would have ended the release with still no progress view, repeating the gap Phase 1 left. **Phase 3 items 6–7 now ship:** one relationship-id authorization helper gates the aggregate progress projection, and an additive Dashboard section gives supporter-only accounts a useful home without hiding a learner's own workspace. **⚠️ Phase 3 is the product's FIRST cross-user read; every read path today is owner-scoped, so this is an authorization model, not charts.** **⚠️ Linking is invite + accept in BOTH directions, revocable either side — acceptance is load-bearing**, since without it anyone could claim a relationship by knowing an email address. **⚠️ Age is collected AT LINK TIME, never at signup** (onboarding is under measurement until `[CHECKPOINT — due 2026-09-11]`), with guardian consent below a threshold that is **NOT decided** — the number is a legal question pending counsel, so build it as **configuration, not a literal**, and if a default must ship make it the most protective value. **⚠️ THE PRIVACY LINE IS ABSOLUTE: a supporter sees readiness, progress and quiz performance, NEVER the learner's notes** — this protects the core loop, because learners who suspect notes are visible write less honestly. **The first projection chooses aggregate counts and states only, excluding concept names, subjects and all note/Study Pack/collection titles.** **⚠️ Every cross-user read must verify an `ACCEPTED` relationship, not merely that a row exists; revocation cuts the read immediately.** **⚠️ Viewing must never write `ConceptHealth`** — it has moved only from genuine assessment since `v0.37.0`, and a view that touches it would corrupt the learner's signal with someone else's activity. **No sub-accounts, no shared quota or subscription, no supporter `ProfileType`, no signup/onboarding fields.** Full scope and anti-drift rules in `RELEASES.md`.

`v0.88.0 — Section Authoring` (on `releases/v0.88.0`, **cut from `docs/subject-plan-sections-assessment` rather than from `main`** so the approved plan ships inside the release PR; PR #1116 closed unmerged) is **Released** — kicked off 2026-08-18, signed off 2026-08-19. **All eight planned items shipped**, plus a full cold-context pressure test that found **five defects in code already merged through three green PRs** — including `Group by subject` bypassing the case-snap the release exists to enforce, and a reserved-name collision that made two identically-titled learner cards display the same wrong readiness number. All fixed and mutation-verified before signoff. Owes `[CHECKPOINT — due 2026-09-19]`. **⚠️ The framing "build Sections" is wrong, and that is the release's central finding: Sections already ship end to end.** They are derived client-side from the existing per-item `note_collection_items.label` — learner collapsible cards with counts and a `N% · M due` readiness pill, Builder rename/reorder/move-between/drag, and `docs/features/collections.md`. **What is missing is a way to create the SECOND section, a guard on the first, and any path by which notes arrive pre-sectioned.** Scope is the seven-item list in `docs/claude-plans/subject-plan-sections-assessment.md` — two cold-context agents on non-overlapping halves plus a cold UX pressure test that revised it twice. **⚠️ The model decision is CLOSED on production evidence: no migration, no `note_collection_sections` table, no new entity, no third collection level.** The curator's own 77-note plan proved the workflow is **notes-first**, so empty sections — the one thing labels cannot express — are not needed; once bulk generation assigns a section, generating the Algebra batch *creates* the Algebra section as a side effect, in generation order. **The incident is reproduced and confirmed empirically** (`SELECT label, count(*)` → one row, `Algebra, 77`): a fresh plan is all `label = null` → one derived `Ungrouped` section; the Builder renders every header editable with no special case (`:541`) and `handleRenameLeafSection` (`:1309-1319`) relabels every matching item, so **one blur event stamped a single label onto all 77 notes**, after which the Move dropdown ("all sections except mine") is empty and a second section can never be created. **The learner surface guards exactly this** (`:277`, `:3022`) — same bug class, guarded in the file the curator does not reach. **⚠️ The whole-release-only defect: items 2 and 3 are each correct alone and wrong together.** The ported combobox carries local state plus a debounced auto-save and `LeafSortableNoteCard` is keyed by note id, stable across `refreshBuilder` — so after a rename mass-relabels a section, every card writes its stale value back, **silently reverting the rename one note at a time**. Key it `${noteId}:${item.label ?? ""}`. **⚠️ Bulk generation's section is an EDITABLE field pre-filled from the batch subject**, never a hidden coupling — a silent one leaves no override against the degenerate "every batch is Engineering Mathematics" case. **⚠️ Desktop collapse is load-bearing:** collapse is `sm:hidden` / `hidden sm:block` today, so on desktop a 77-note plan is uncollapsible — and because `addItems` appends at `max(position)+1`, **section order IS generation order**, so repairing it means dragging a header across exactly that page. **⚠️ Do NOT re-add "section destination in Add Notes"** — cut after the pressure test disproved its cost estimate. **⚠️ Sequencing owed to the curator, not code:** `persistAdoptedPlan:1398-1402` returns early on an existing `sourcePlanId`, so **re-adopt is a hard no-op** — finish sectioning before publishing or promoting the CE Goal. Full scope and anti-drift rules in `RELEASES.md`.

`v0.87.0 — Failure Attribution` (on `releases/v0.87.0`, cut from `main` after `v0.86.0` merged and deployed 2026-08-18) is **Released** — kicked off, **rescoped** and signed off 2026-08-18. It opened as *Published Bounds* and **rescoped on its own pre-committed gate** when a live 14-topic probe across all eight domains came back 14/14 clean: the three unpublished word bounds are real but do not bite. It shipped per-topic failure attribution instead — Bulk Generate now records **why** each topic failed, closing the gap that had two investigations resolving by guesswork. **⚠️ The instrument is EPHEMERAL: the receipt is deleted on read and expires in 24 hours, so this cannot be queried retrospectively** — the durable record is the server log. Full scope in `RELEASES.md`.

`v0.86.0 — Generation Recovery` (on `releases/v0.86.0`, cut from `main` after `v0.85.0` merged and deployed 2026-08-18) is **Released** — kicked off and signed off 2026-08-18. **All four planned items shipped** (the fourth was folded in mid-release at owner request, unblocking Review Set building). **A cold-context pressure test — two agents, non-overlapping halves — independently found the same blocking NPE in the recovery interlock, which every existing test passed over, plus four tests passing for the wrong reason.** All fixed and mutation-verified; see `RELEASES.md`. **⚠️ SCOPE WAS INVERTED BY THE PRODUCTION READ the same day.** It was kicked off against stuck *notes*; `docs/claude-plans/v0.86.0-stuck-generation-sizing.sql` returned **zero** stuck notes and found the damage on the two surfaces the kickoff had declared out of scope: ****37** stuck `exam_question_pool` rows (18 `GENERATING` + 19 `PENDING`, corrected 2026-08-18)** (oldest 2026-07-02, degrading every exam start on those packs to on-demand LLM generation) and **1 Long Exam session stuck since 2026-05-19** (a hard permanent block — `LongExamService:169` hands that learner back the stuck session instead of creating a new one). **The note half ships but is PROSPECTIVE and must not be called a backlog.** **The mechanism is the finding that makes the fix generalise:** all three surfaces self-heal a *running* task via a full `catch`, but `SimpleClientHttpRequestFactory` uses `HttpURLConnection`, whose socket reads ignore `Thread.interrupt()` — so `shutdownNow()` never unwinds the thread, JVM exit kills it, and the catch never runs. A note is one LLM call; a pool is a `generateLongExamParallel` fan-out. Wider window, more stuck rows. **Pools are primary and cheapest: recovery already exists** (`sampleQuestions:94` calls `refreshPool` on `FAILED`) and only the transition *into* `FAILED` is missing. **⚠️ `PENDING` is as terminal as `GENERATING`** — a pool killed while queued never reaches `GENERATING`, which is why the first query undercounted. **⚠️ `created_at` cannot be the pool clock** (rows are reused; a re-initiated pool has an ancient `created_at`), so a stamp is written at all four status-write sites; **but `created_at` IS safe for sessions**, since `buildGeneratingSession` always constructs a new row. **⚠️ `LONG_EXAM` only** — Challenge sessions need `releaseClaims` and would leak bank claims if swept, so they, Adaptive and Interview are Known Limitations. **⚠️ No auto-regeneration, no shutdown-drain change, no `REQUIRES_NEW`, no startup sweep.** Full scope and anti-drift rules in `RELEASES.md`.

`v0.85.0 — Domain Signal Integrity` (on `releases/v0.85.0`, cut from `main` after `v0.84.0` merged and deployed 2026-08-17) is **Released** — kicked off 2026-08-17, signed off 2026-08-18. **All three planned items shipped, plus a follow-up PR from the cold-context pressure test.** **⚠️ Its production impact is PROSPECTIVE: it changes the prompt for ZERO notes today** — verified, not estimated. The declared property fires only on a non-null `domain_context`, every enum value in production use already matched the old scan on its own label, and the four values that newly opt in have no notes. The 463 close by curator classification (`docs/claude-plans/v0.85.0-domain-context-classification.sql`), not by more code. Owes `[CHECKPOINT — due 2026-09-17]`. **The one signal that shapes how the AI writes is guessed from a name instead of declared, and it is wrong for roughly half the public catalog.** `isQuantitativeContext` substring-matches a haystack against 49 English keywords across 7 call sites; six later quiz/exam sites include concepts and summary, while the **463 of 956** production figure models initial Study Pack generation's domain + subject + tags call. The haystack leads with the Domain Context's display label and one keyword is `engineering`, so **coverage tracks the program's NAME**: programs named "Engineering" are 0% missing (214 notes), every other named program is 69% missing (**463 of 670**). The failing subjects are the computational ones — Nursing `Pharmacology`, Accountancy `Income Tax` / `Taxation` / `Budgeting` / `PPE`, Architecture `Structural Components`. **⚠️ Fix is a DECLARED per-value property, not a longer keyword list.** **⚠️ This release does NOT build the Domain Context Catalog** that surfaced the defect — it was **declined on production evidence** (12.7% classification, 4 of 8 values ever used, `ACCOUNTANCY`/`NURSING` at zero against 286 unclassified notes). Full scope and anti-drift rules in `RELEASES.md`.

`v0.84.0 — Public Explore` (on `releases/v0.84.0`, cut from `main` after `v0.83.2` merged 2026-08-17) is **Released** — kicked off and signed off 2026-08-17. **Slice C of Discovery System Stage 0: `/explore` is now anonymous, and Stage 0 is complete end to end** (Slice A in `v0.83.2`, Slice B dissolved, Slice C here). **Stages 1 and 2 were folded in after the first signoff** — the marketing nav names Explore and Exam Hub's outbound links point at it — so the release opens Explore *and* directs people to it. Plus three folds closing this release's own known limitations. Owes `[CHECKPOINT — due 2026-09-16]`, whose premise and kill criterion were **revised with that folding**: it now asks whether anonymous discovery is a lever at all, rather than whether a public-but-unlinked page is enough. Both blocking owner decisions taken 2026-08-17 — the anonymous Review Sets tab shows the **full published catalog with Adopt gated at click** (a structurally different anonymous page would forfeit the reason for choosing the `/explore` URL at all), and **`robots.ts` keeps `/explore` unindexed while incomplete**, a decision that binds only if the release is split across deploys, since `main`-merge is the deploy. **Implementation complete:** `/explore` now renders identically for visitors and members, anonymous Adopt uses a one-shot discovery-intent cookie through onboarding, anonymous labels use `STUDENT` vocabulary, and Explore is self-canonical with a distinct plans-plus-notes `CollectionPage` while Public Library remains self-canonical for notes. `robots.ts` stayed unchanged because the complete surface ships atomically. Stage 3 stays doctrine-blocked; nothing here redirects anything. Full scope and anti-drift rules in `RELEASES.md`.

`v0.83.2 — Anonymous Discovery Access` (on `releases/v0.83.2`, cut from `main` after `v0.83.1` merged 2026-08-17) is **Released** — kicked off and signed off 2026-08-17. **Slice A of Discovery System Stage 0** (`docs/claude-plans/discovery-system-stage-0-scoping.md`): two live bugs on anonymous public surfaces, both required regardless of which fork that initiative takes. **(1)** `GET /subjects` and `GET /course-programs` appear zero times in `SecurityConfig`, which ends `.anyRequest().authenticated()`, while both controllers explicitly serve anonymous `scope=public` — so an anonymous visitor at `/public/library` sees Tags and Authored Depth populated beside **empty** Subject and Course/Program, the 401s swallowed by `Promise.allSettled`. **⚠️ A security-config widening; it needs an anonymous `scope=mine` 401 test, not just a `scope=public` 200.** **(2)** Remove the miscategorised `/exam/page.tsx` BackLink — removal, not repointing. **⚠️ Slice B was RESCOPED at kickoff and is not engineering work:** anonymous-vs-authenticated is already derivable via `user_id IS NULL`, since `AnalyticsController` passes a null user on a `permitAll` endpoint with a nullable column. What was missing is an analysis convention; do not build a parallel dimension. **Slice C is not in this release** and stays blocked on the Review Sets tab and `robots.ts` decisions. Full scope and anti-drift rules in `RELEASES.md`.

`v0.83.1 — Note Creation Integrity` (on `releases/v0.83.1`, cut from `main` after `v0.83.0` merged and deployed 2026-08-17) is **Released** — kicked off and signed off 2026-08-17. **A patch for a live production 500 that predates the release which found it**, plus two folded `v0.83.0` Known Limitations (`?level=` slug tolerance and the deferred cosmetic cleanup) and the Discovery System Stage 0 scoping pass. **Pre-signoff check was ONE cold agent, not three — the full gate did not fire** (2 PRs, zero files touched by more than one commit) and it came back clean, having verified the 13 fixture deletions empirically by restoring them all and re-running. **No checkpoint is owed:** nothing shipped ahead of its evidence. `StudyPackService.createGeneratedNote` and `ShareService.createRemixedNote` never set `target_profile_type`, which is `NOT NULL` with no default — **verified empirically against real PostgreSQL**. Reachable via `/study` paste-text, image upload, confirm-text and share-remix, and **`/study` is live from the Dashboard Today Focus card**. **⚠️ Quota is spent and the LLM call completes before the insert fails**, so the learner pays for a generation and receives an error. Dates to 2026-03-21; found by `v0.83.0`'s pressure test and recorded there rather than fixed in scope. **⚠️ The value must be DERIVED from the owner's profile, not a constant** — `SPEC.md:129` documents that contract. **No migration, and specifically no `DEFAULT` on the column** — that would mask the bug class rather than fix it. Full scope and anti-drift rules in `RELEASES.md`.

`v0.83.0 — Target Audience Removal (Phase 2)` (on `releases/v0.83.0`, cut from `main` after `v0.82.0` merged and deployed 2026-08-17) is **Released** — kicked off and signed off 2026-08-17. **All four planned items shipped**, plus a fourth item added mid-release (the Authored Depth replacement filter) and a three-agent cold-context pressure test. **Step 2 of the revised Target Audience retirement**, executing phases 2 and 3 of the already-ratified `ADR-001` amendment: remove the authoring field, the Public Library filter chips, and the `?audience=` parameter. **⚠️ `notes.target_profile_type` is NOT dropped** — it is the input `V117` derived depth from, and `[CHECKPOINT — due 2026-09-16]`'s kill criterion cannot run without it, so the column drop (phase 4) waits on that report. **No migration ships.** **⚠️ Phase 2 stays UNGATED from `[CHECKPOINT — due 2026-09-13]`** (owner call 2026-08-16 — the audience chip is secondary since `v0.79.0`, so the Explore confound is recorded rather than waited on); do not re-gate it from the superseded four-phase plan. **⚠️ Do not re-propose a `V118` `STUDENT` backfill** — audited in `v0.82.0`, zero eligible notes. **One open decision carried into scope:** removing the audience filter leaves student material with an empty depth filter because `STUDENT` depth is deliberately NULL — a real product regression whose size the repo currently contradicts itself on, sized by a query in `docs/claude-plans/v0.82.0-post-deploy-narrowing.sql` before it is accepted or fixed. Full scope and anti-drift rules in `RELEASES.md`.

`v0.82.0 — Authored Depth Backfill` (on `releases/v0.82.0`, cut from `main` after `v0.81.0` merged and deployed) is **Released** — kicked off and signed off 2026-08-16, merged and deployed 2026-08-17. **All three planned items shipped, plus three folds.** **⚠️ Owed NOW, immediately post-deploy:** the step-2 narrowing query, runnable as `docs/claude-plans/v0.82.0-post-deploy-narrowing.sql` — **expected 819 rows**. The pre-merge capture ran and was exported to `docs/claude-plans/v0.82.0-curator-depth-backfill-population.csv` (828 ids, verified), but the *narrowed* set is the reversal key and `[CHECKPOINT — due 2026-09-16]`'s denominator, and both die without it. Deploy timestamp for that checkpoint's read (b): `2026-08-17 08:29:14 +08`. **A fourth item was audited and killed:** a `V118` extending the backfill to the `STUDENT` cohort returned zero eligible notes (curators had already authored those depths by hand), and that audit is what found a **second `BOARD_TAKER` mis-tag outside Information Technology**, which widened `V117`'s exclusion into a denylist before merge. **The Backlog Index checkpoints were consolidated from nine September dates to two**, and **Target Audience Phase 2 was ungated from `2026-09-13`** (owner call) — the column drop still waits on `2026-09-16`. **Step 1 of the revised Target Audience retirement; `v0.81.0` was Step 0 and cleared it** — `V115`'s widened bank key means a depth correction no longer produces a session-failing collision. Ratifies the `ADR-001` amendment reducing five note metadata axes to **four**: *Target Audience has no long-term architectural responsibility and should be retired, but only after its information is migrated into Authored Depth and its live discovery contract is replaced.* **The "Course / Program predicts audience ~99.3%" argument is explicitly NOT the justification** — correlation from a board-heavy catalog, not equivalence. **Backfill is curator-scoped by production data:** of 5,550 affected notes, 4,645 are learner-owned (private, feed no filter, and writing depth there asserts an authoring decision their author never made) and 905 are curator-owned, which is essentially the 945 public notes the filter reads. Safe mappings only; **`STUDENT` stays NULL.** **Target Audience is NOT removed here**, and the Public Library filter waits on `[CHECKPOINT — due 2026-09-13]`. Full scope and anti-drift rules in `RELEASES.md`.

`v0.81.0 — Challenge Bank Integrity` (on `releases/v0.81.0`, cut from `main` after `v0.80.0` merged and deployed) is **Released** — kicked off 2026-08-15, signed off 2026-08-16. **Two `v0.70.0` Known Limitations, open since 2026-08-04, fixed on their own merits and as Step 0 of the Target Audience retirement.** (1) `uq_challenge_quiz_question_bank_user_pack_key` **excludes `learner_level`** (`V96:12`), so a depth correction strands bank rows whose keys are absent from `disallowedQuestionKeys` — **the LLM can regenerate a question the learner has already seen**; measured at 5 of 6,235 but *"unbounded once authoring corrections become routine."* (2) `persistGeneratedQuestions:118`'s `saveAll` flushes **at commit, outside its own catch**, so `challenge-quiz.md`'s *"best-effort and never blocks a Challenge session"* is **already false**. **The link to the roadmap: the Target Audience direction needs a depth backfill onto ~905 curator notes — an authoring correction at scale, exactly the condition defect (1) names.** **Only defect (1) was fully closed.** `V115` widens the key and `V116` stamps the unclaimable rows; the best-effort persistence guarantee is **PARTIAL** — a concurrent same-level duplicate can still fail a session, and the `REQUIRES_NEW` isolation meant to close that broke every Challenge start on an FK to an uncommitted session row and was reverted. No Target Audience code changes here; this release only unblocks that direction. Owes `[CHECKPOINT — due 2026-09-15]`. **No next version is kicked off.** Full scope and anti-drift rules in `RELEASES.md`.

`v0.80.0 — Instrumentation Integrity` (on `releases/v0.80.0`, cut from `main` after `v0.79.0` merged and deployed) is **Released** — kicked off and signed off 2026-08-15. **`trackAnalyticsEvent` posts with a raw `fetch` while every product call goes through `fetchWithAuth`'s refresh-and-retry**, so a 401 on a 15-minute access token silently discards the event while the learner's action succeeds. **The loss is biased, not random:** impressions fire right after a data load with a fresh token; clicks on an idle page do not — so **impression→click rates read systematically low**, which is the exact metric behind both `[CHECKPOINT — due 2026-09-14]` rows. **Ships now precisely because a window is live:** `v0.78.0`'s opened 2026-08-15, so fixing immediately leaves ~29 of 30 days clean where deferring leaves none. Also normalizes catalog names on create (`v0.79.0` made the catalog the sole public chip source, taking that exposure from partial to total) and closes four weak tests recorded as `v0.79.0` limitations. Owes `[CHECKPOINT — due 2026-09-14]`, because **the loss was inferred from a code path and never measured** — its kill criterion strikes the delivery caveats if volume proves unchanged. **No next version is kicked off.** Full scope and anti-drift rules in `RELEASES.md`.

`v0.79.0 — Catalog-First Vocabulary` (on `releases/v0.79.0`, cut from `main` after `v0.78.0` merged and deployed) is **Released** — kicked off and signed off 2026-08-15. **The counter-proposal from `course-program-canonical-catalog-proposal.md`, NOT the `ADR-001` amendment** — that amendment stays PROPOSED and unratified, and the proposal's own recommendation is to try the softer fix first. **The defect is two sources of truth that drifted:** a hardcoded 31-value `COURSE_PROGRAM_SUGGESTIONS` list against a 21-row canonical catalog, **only 16 overlapping**, so a learner picking a non-overlapping value gets a `course_program` that matches no published plan and `v0.78.0`'s recommendation can never fire for them. **Production sizes it: 60 of the 189 planless learners are blocked by vocabulary, not content or features.** Fix is catalog-first suggestions (free text still allowed, no migration) plus stopping off-catalog values from minting public filter chips, **instrumented** so the off-catalog rate for newly authored values is observable — with a pre-deploy baseline captured before merge, which is the step `v0.78.0` learned the hard way. **Onboarding is deliberately excluded** to protect `[CHECKPOINT — due 2026-09-11]`, and the pre-deploy baseline shows that is where the value is — learner notes are already 0.6% off-catalog while learner profiles are 13.9%, so **this release is the machinery and the measurement, and the onboarding follow-up is the intervention** (its own Backlog Index row). Owes `[CHECKPOINT — due 2026-09-14]` proximal / `2026-10-15` distal. **No next version is kicked off.** Full scope and anti-drift rules in `RELEASES.md`.

`v0.78.0 — Post-Mastery Next Step` (on `releases/v0.78.0`, cut from `main` after `v0.77.0` merged and deployed) is **Released** — kicked off 2026-08-14, signed off 2026-08-15. **It opened as leg (a) of the "Post-mastery next step" backlog row and grew mid-flight on production evidence:** once a learner masters a pack, the result screen advances to `Take a Challenge` and its **secondary slot is empty** — verified in code at kickoff, `PostSessionNextStepService.java:215` passes `null`. This fills it with the learner's **next Study Plan item**. **"Next" = lowest `NoteCollectionItemEntity.position` the learner has not practiced**, reusing `toProgressResponse`'s existing `lastSessionCompletedAt != null` definition so the suggestion cannot contradict the progress bar beside it. **Leg (b) ("a similar note") is explicitly OUT** — similarity is the Discovery System, gated behind `[CHECKPOINT — due 2026-09-13]`, and building a second recommender beside it would fragment both. **No entry point moves**, so the standing 2026-09-12 Challenge Quiz constraint is untouched. **The LaTeX chore did NOT ship** — owner-executed curator work that was not performed, carried forward with its Backlog Index row intact rather than counted as done. **The release also owes `[CHECKPOINT — due 2026-09-14]`** on whether a named recommendation converts where a generic pointer did not. **No next version is kicked off.** Full scope and anti-drift rules in `RELEASES.md`.

`v0.77.0 — Evidence-Gated Weak Concept Recommendation` (on `releases/v0.77.0`, cut from `main` after `v0.76.1` merged and deployed) is the previous released version — kicked off and signed off 2026-08-14. **No next version is kicked off.** **First slice of the ratified *Adaptive Practice as the recommendation engine* direction.** The Dashboard's weak-concept focus currently resolves from **the single latest completed Quick Review** — the copy says *"Your latest Quick Review… showed N weak concepts"* — so one bad quiz produces a recommendation. This resolves it from `ConceptHealth`'s existing **`incorrect_streak`** against the existing **`TWICE_MISSED_STREAK_THRESHOLD`**, so the recommendation follows persistent weakness instead. **Buildable now because the signal already exists** — nothing new is recorded, and no second evidence bar is invented. **WITHIN-PACK ONLY:** cross-pack (*"this keeps coming back everywhere"*) needs canonical concept identity, which is ADR-sized and out of scope. **No entry point is removed**, so the standing 2026-09-12 constraint is respected without deferring anything. Full scope in `RELEASES.md`.

`v0.76.1 — Adaptive Practice Entry Attribution` (on `releases/v0.76.1`, cut from `main` after `v0.76.0` merged and deployed) is the previous released version — kicked off and signed off 2026-08-14. A patch that records **where** an Adaptive Practice session was started from, so the open **`[CHECKPOINT — due 2026-09-12]`** can answer the question that matters. Today `ADAPTIVE_PRACTICE_STARTED` carries no entry-point attribution, so the read can measure *whether* starts fell but not *where the surviving ones come from* — and the ratified recommendation-engine direction rests on Dashboard discovery, which is therefore currently untestable. **Same failure shape as the Challenge Quiz read (c)**, which sat NOT MEASURABLE for months for exactly this reason; shipping the field now rather than discovering the gap on the due date is why this is a patch. **It cannot contaminate the read it serves** — the primary metric is a total count, and this is additive metadata with no user-visible behaviour change. **It also carries the two product-direction docs ratified 2026-08-14** and the re-specified checkpoint remedy, retargeted out of a separate `main` PR **because merging to `main` auto-deploys to production** — two merges would mean two deploys and two interruptions for active learners. Full scope in `RELEASES.md`.

`v0.76.0 — Messaging Architecture: The Money Surfaces` (on `releases/v0.76.0`, cut from `main` after `v0.75.0` merged and deployed) is the second-previous released version — kicked off and signed off 2026-08-14. It is the **second slice** of the ratified, deliberately incremental Messaging Architecture initiative (see that Backlog Index row): `/pricing` finishes the hierarchy `v0.68.0` started, and the in-app upgrade prompts adopt the same voice. **Scope is the money surfaces only** — the landing page and Exam Hub upsell are real remaining surfaces but answer a different question than *"should I pay"*, and each needs its own scoping pass. **The one item the roadmap names as explicitly owed is `FREE.title`**, and the `v0.68.0` revert is its specification: an outcome-framed candidate was written and reverted there because it had been derived from `PLUS`/`PRO` symmetry, which contradicts the ratified `FREE=adopt` placement. **Positioning only — no pricing or quota changes, and product names stay untouched everywhere.** Full scope and anti-drift rules in `RELEASES.md`.

**Chosen because nothing else was both un-gated and collision-free.** The earliest live checkpoint is **2026-09-10**, 27 days out, so the stronger learner-facing candidates all sit inside measurement windows: the post-mastery next step collides with `[CHECKPOINT — due 2026-09-12]`'s secondary metric, and the Onboarding Intent Router residuals would change onboarding inside `[CHECKPOINT — due 2026-09-11]`'s window. Both remain deferred **on measurement grounds, not merit**. The alternative recommendation on the table was non-engineering: **author the Civil Engineering Engineering-Mathematics notes** that `v0.69.0`–`v0.75.0` exist to unblock. That is not superseded by this release and can proceed in parallel — this is a copy-led slice that does not compete for curator time.

`v0.75.0 — Authoring by Inference` (on `releases/v0.75.0`, cut from `main` after `v0.74.0` merged and deployed) is the second-previous released version — kicked off 2026-08-13, signed off and deployed 2026-08-14. It implements `ADR-001` → *"Authoring populates by inference, not manual classification"*, whose two sequenced gates both turned out to be already clear: **R4 resolved 2026-08-04**, and the *"true first step"* that section names — making authoring metadata editable on a `STUDY_PACK_READY` note — **shipped in `v0.70.0`** (`AGENTS.md:1081`). The ADR text was stale for four releases and is corrected in this release rather than left to be re-derived. **Scope is DEPTH ONLY: Domain Context is deliberately excluded**, because `ADR-001` authorizes an inference chain for `learner_level` alone and `domain_context IS NULL` is the promotion-backlog marker a pre-fill would silently destroy. **The two legs are not the same size** — leg 2 (author profile → depth) is implementable today and merely completes a pattern already shipped beside it at `bulk-generation-page-client.tsx:132`, while leg 1 (Review Set → depth) has **no source at all**: `NoteCollectionEntity` carries no `learnerLevel`, and no authoring surface knows its target Review Set. Leg 1 is therefore a column *and* a trigger, which is what makes this a release rather than a patch. **No checkpoint collision** — entirely curator-side, and after the pressure test that is literally true rather than merely intended: the depth pre-fill initially fired for **every** profile while the control renders for curators only, so learners silently persisted an invisible, unclearable value. It is now gated to curators, which **narrows item 1** — learners get no depth pre-fill and keep resolving through the profile fallback exactly as before. **No `[CHECKPOINT]` is owed, decided explicitly:** nothing shipped ahead of its evidence (both gates were verified clear, not overridden), this implements a ratified ADR direction rather than a bet, and no event exists that would measure "curators keep the inferred depth" — so a dated row would be decorative, which the gate's own rules forbid. Full scope, the scope-completeness audit, and the checkpoint reasoning are in `RELEASES.md`; the ten pressure-test findings and their dispositions are in `docs/claude-findings/v0.75.0-pre-signoff-pressure-test.md`.

`v0.74.0 — Quiz Progression` (on `releases/v0.74.0`, cut from `main` after `v0.73.0` merged and deployed) is the second-previous released version — kicked off 2026-08-12, signed off 2026-08-13. It closes a scored assessment whose answer key sits on an adjacent tab: `practice-quiz-card.tsx:25` renders the saved quiz with `revealAnswer`, and Quick Review administers **those same questions**, which makes its score meaningless *and* corrupts `ConceptHealth`. **All seven scope items ship — owner ruling 2026-08-12: nothing is parked, no item waits on a date.** That decision closes the after-window of the open `[CHECKPOINT — due 2026-09-30]`, and the cost is being paid rather than absorbed: reads (a) and (b) are **run pre-deploy** (both already recorded as MEASURABLE NOW, and read (a) explicitly does not improve by waiting), and read (c) — previously NOT MEASURABLE — is **unblocked by this release**, since item 7 folds in the missing post-session Challenge CTA impression and click events. **It carries `[CHECKPOINT — due 2026-09-12]` on the unlock rate, plus a second on the Adaptive Practice route removal**, because only the *progression* half of the rationale is falsifiable — the *integrity* half is code-verified. **Item 5 additionally carries a cost against `v0.72.0`'s `[CHECKPOINT — due 2026-09-10]`** — see `RELEASES.md`; it was found after the lifecycle ruling and should be raised once before that item is implemented. Full scope in `RELEASES.md`.

`v0.73.0 — Onboarding Redesign` (on `releases/v0.73.0`, cut from `main` after `v0.72.1` merged and deployed) is the second-previous released version — kicked off 2026-08-11, signed off and deployed 2026-08-12. It went at the funnel's largest single leak: **132 learners, 35.2% of all signups**, verify their email and never finish onboarding. Justified on **comprehension, not retention** — activation is already 52.2%, which caps volume work below the retention lever. Eight of ten planned items shipped; **item 3 (removing the exam-date question) was never built, and was then decided against on review** — it is closed, not carried. It carries a `[CHECKPOINT — due 2026-09-11]` on onboarding completion against the 62.4% baseline. Full scope, the pressure-test findings, and five carried Known Limitations are in `RELEASES.md`.

`v0.72.0 — Return Loop` (on `releases/v0.72.0`, cut from `main` after `v0.71.2` merged and deployed) is the previous released version — kicked off and signed off 2026-08-11, merged and deployed the same day. **It shipped H1+H5 on an ambiguous read, so it carries two dated checkpoints** (proximal due **2026-09-10**, distal due **2026-11-09**) recorded in the Backlog Index row below — added at this kickoff, because signoff closed without them. It went at the project's oldest and least-moved constraint, **W1→W2 retention at 2.4%**, rather than at another authoring or discovery surface. **It opens on a read, not a build:** an earlier release pre-committed to *"if the signal is positive or ambiguous (not clearly negative), ship H1+H5"*, the window closed **2026-07-29**, and this kickoff's gate scan found the read had gone unrun for 13 days. Scope, the rule, and the recorded fallback if the read is clearly negative are in `RELEASES.md`.

Previous: `v0.71.2 — Catalog Management` (on `releases/v0.71.2`, cut from `main` after `v0.71.1` merged and deployed) is **Released** — kicked off and signed off 2026-08-11. **No next version is kicked off.** It added the ability to grow the `course_programs` catalog from inside the product, which is the mechanism `ADR-001` already names and the thing currently blocking one canonical Algebra note from serving every engineering program it applies to. **New capability, not deferred-findings cleanup** — its anti-drift is written fresh in `RELEASES.md` rather than inherited from `v0.71.1`. Scope, audit findings and the two open decisions are in the `v0.71.2` section in `docs/archive/ROADMAP_ARCHIVE.md`.

Previous: `v0.71.1 — Applicable Programs Follow-ups` (on `releases/v0.71.1`, cut from `main` after `v0.71.0` merged and deployed) is **Released** — kicked off 2026-08-10, signed off 2026-08-11. **Next planned: `v0.71.2 — Catalog Management`** (scoped below, not yet kicked off). A patch release that cleared what `v0.71.0` deferred rather than fixed: four architectural findings its signoff pressure test marked *needs a decision, not a patch*, plus a triaged subset of the twenty Medium/Low findings carried as `v0.71.1` candidates. **It extends `ADR-001` not at all** — the one architectural output is a decision on how curator-authored Applicable Programs and a learner's own note interact, a question `v0.71.0` opened and did not close. The join-first read with legacy-string fallback stays; retiring `notes.course_program` remains unscheduled. **C8, C9, M13, M15 and M16 stay out of scope and stay carried** — all five are onboarding-vocabulary findings belonging to the Onboarding Intent Router work, and this release must not read as having absorbed them. Backlog Index rows: the `v0.71.1` deferral row and the Medium/Low carry row below.

Previous: `v0.71.0 — Applicable Programs` (on `releases/v0.71.0`, cut from `main` after `v0.70.0` merged and deployed) is **Released** — kicked off 2026-08-04, signed off 2026-08-10, merged and deployed. It opens **Release B of `ADR-001`**: `note_course_program` makes applicability a many-to-many fact, so one canonical note surfaces under every program that needs it. Release B is materially riskier than Release A — irreversible once filters and badges read the join, and it moves the Library/Explore filter and search onto join/`EXISTS` semantics on a hot paginated path. **Scoped into three slices along the irreversibility boundary** (`18-release-b-slice-sequence.md`), correcting the initial read that the whole release was blocked. **All three slices are now shipped:** Slice 1 added storage and write surfaces, Slice 2 moved discovery reads onto the join-first fallback semantics, and Slice 3 added unconditional all-members Program Family expansion to the shared authoring control without backend or catalog changes. R4 did *not* settle applicability; the family gate was cleared by removing curriculum conditioning from expansion, not by inferring applicability from R4.

Previous: `v0.70.0 — Canonical Knowledge Completion` (on `releases/v0.70.0`, cut from `main` after `v0.69.0` merged and deployed) is **Released** — kicked off 2026-08-04, signed off 2026-08-04. It completes Release A of `ADR-001`: the two Planned Scope items `v0.69.0` deferred (the `course_programs` catalog + `program_families`, and the question pool/bank learner-level re-keying) both shipped once their production reads were done, alongside the two authoring-surface gaps found in the first hour of real use — authoring metadata is now correctable after generation, which is what unblocks R4 step 1. Previous baseline: `v0.69.0 — Canonical Knowledge Foundation`, **Released and deployed 2026-08-04**, which shipped Domain Context, note-level learner depth, and the 49-note legacy backfill.

**Signed off 2026-08-04 with all four Planned Scope items shipped and no deferrals.** The `course_programs` catalog seeds 21 curator-approved programs from a two-stage production vocabulary read; the pool/bank re-keying moved persisted quiz reuse off the reader's level onto the note's. **The R4 generate-and-diff verification was a ~~`[CHECKPOINT — due 2026-08-18]`~~** carried forward from `v0.69.0` — **RESOLVED 2026-08-04**, before its due date, once `v0.70.0` deployed (`ADR-001:266`). What follows described the state at `v0.70.0` signoff and is left as the historical record: **Bulk authoring must not begin until R4 step 2 passes.** A full pre-signoff pressure test (four inventory agents synthesised through `advisor`) found four defects, all fixed before signoff; the sharpest were a permanently-memoised fail-open fallback that would have made the new catalog invisible until redeploy, and a Target Audience rewrite on a live Public Library filter — both interactions between separately-reviewed PRs. Two Known Limitations are carried forward, documented rather than fixed.

Why this reclaimed the slot: authoring of the Civil Engineering Review Set's Engineering Mathematics subject plan was deliberately halted, because creating those Algebra topic notes under a one-program-per-note model would commit the library to duplicating them for ten further engineering programs. A production vocabulary audit run at ratification closed every data gate — 32 distinct program values with **zero** character-level collisions (so the catalog is cheap), and duplication confirmed already begun at n=2 programs via `Strength of Materials`. See the "Canonical Knowledge Architecture" section in `docs/archive/ROADMAP_ARCHIVE.md`, its Backlog Index row, and `RELEASES.md` v0.69.0 for full Planned Scope and anti-drift rules.

Previous: `v0.68.0 — Topic Note Rename` (on `releases/v0.68.0`, cut from `main` after `v0.67.1` merged) is **Released** (signed off 2026-08-01) — kicked off 2026-07-31, Company Redefinition Phase 4's ratified §4 item 6 (see the Company Redefinition Backlog Index row below and `05-packaging-and-terminology.md`): rename the bare-prompt "Generate Note" action to "Create a Note," reserving "Generate" for Study Pack/Quiz generation, the feature that actually transforms a learner's own notes. **Five further ungated items were folded in after kickoff** as each turned out to need no additional owner decision: §4 item 8's "Retry Generate" grammar fix, the Companion Guidance Doctrine's docs-only text, the Messaging Architecture's first slice (2026-08-01, see its own Backlog Index row), and — on explicit owner review the same day, after the first three had shipped — the two consistency batches the rename's deliberately-narrow scope had left behind (`plans.ts`'s pricing-table/upgrade-highlight strings, and the topic-note quota's user-facing vocabulary moving to "topic note"). See `RELEASES.md` v0.68.0 for full Planned Scope, including the explicit scope amendment the last two required. **Signed off 2026-08-01 after a full pre-signoff pressure test** (five parallel review agents over all 41 changed files, gated by `plans.ts` being touched by two separate PRs *and* the release reaching four merged PRs). It found ~30 findings, all fixed or documented: the highest-impact was a backend 422 quota message rendering the retired vocabulary into the *same* `role="alert"` element as its renamed frontend twin (fixed — a second scope amendment, making this the release's only backend code change); five surfaces inside the renamed action's own flow that still said "Generated note"; a layout regression whose real cause was plan-`description` length drift across four card renderers, not the title lengths an earlier check had cleared; a vacuous test assertion that had never worked; a pre-existing library-poller flake, diagnosed and fixed; six docs still naming `Retry Generate` after that fix shipped with no doc updates at all; and four miscounts in `RELEASES.md`'s own v0.68.0 entries. Twelve pre-existing items are recorded as Known Limitations and tagged `v0.68.1` candidates.

`v0.67.1 — Explore Convergence Follow-ups` (on `releases/v0.67.1`, cut from `main` after `v0.67.0` merged) is the previous released version — kicked off 2026-07-31 to fix three `v0.67.1`-candidate findings logged as Known Limitations at `v0.67.0` signoff, but grew mid-release to seven: the original scoping pass had itself missed a fourth item `v0.67.0` had explicitly tagged `Candidate for v0.67.1` (`PublishedPlansPageClient`'s unreachable `?ref=` back-link), a dark-mode contrast bug on Explore's own segmented control was found from a live owner screenshot and folded in, and an explicit owner review of the remaining `v0.67.0` Known Limitations list surfaced two further pre-existing bugs worth a quick fix (`server-public-study-plans.ts`'s type-cast crash risk, `DashboardCommunityNotesSection`'s unslugified Explore filter link). Shipped across 5 PRs. Before signoff, this release crossed this project's own full-pressure-test gate (5 merged PRs, and `explore-page-client.tsx` touched by two separate PRs) — four parallel review agents plus one fact-checking RELEASES.md's own claims against the real repo found and fixed two further gaps (a matching `Array.isArray()` guard missed on the Exam Hub component, a focus-ring/fill-color collision the dark-mode fix introduced) and logged two minor test-coverage gaps rather than dropping them, with every other claim under review independently verified true. Signed off 2026-07-31. See `RELEASES.md` v0.67.1 for full Shipped scope and Known Limitations, `docs/releases/v0.67.1.md` for the release notes.

`v0.67.0 — Explore Convergence` (on `releases/v0.67.0`, cut from `main` after `v0.66.2` merged) is the second-previous released version — kicked off 2026-07-30 via an explicit owner gate override, not a cleared gate (this release's stated gate, "proceed only if the Diagnostic Read indicates a discovery problem," remained unmet at kickoff and remains unmet today — Round 2 of the read is still due after 2026-08-06), shipped the same day, and signed off 2026-07-31. Delivered a new authenticated `Explore` nav item compositing the existing Official Review Set catalog (`/collections/published`) and public notes library (`/public/library`) behind a segmented control, plus an additive Exam Hub official-set preview/adopt path; a same-day mid-release scope addition ("Explore Owns Discovery," ratified 2026-07-30) made `/explore` the single authenticated owner of content discovery, converting `/collections` to a pure workspace and Dashboard's discovery surfaces into bounded pointers. Implementation delivered by Codex, audited against the real diff by an independent fresh-context Opus review before merge (found and fixed a missing `EXPLORE_VIEWED` event that would have made the checkpoint below unmeasurable, two doc-accuracy corrections, three minor cleanups). Before signoff, this release's release-wide surface area (nav, `/explore`, Collections, Dashboard, Exam Hub, and both frontend/backend analytics contracts across 3 PRs) cleared this project's own full-pressure-test bar: four parallel review agents found one gap that would have silently broken the checkpoint's own pointer-vs-direct segmentation (adopt/preview analytics events weren't carrying pointer-origin metadata that page-view events already had) — fixed via PR #952 before signing off — plus nine further findings logged as Known Limitations rather than dropped, four of which became `v0.67.1` above (not three — the original `v0.67.1` scoping missed one of its own four tagged candidates; see `v0.67.1`'s entry above). See `RELEASES.md` v0.67.0 for full Shipped scope and Known Limitations, `docs/releases/v0.67.0.md` for the release notes, and the "Phase 2 — IA / Explore convergence" section in `docs/archive/ROADMAP_ARCHIVE.md` for the full ratification/override history. A dated `[CHECKPOINT — due 2026-09-13]` stays open, unaffected by either signoff since: re-check Explore-driven engagement against pre-launch baseline 30-45 days post-ship. **⚠️ BASELINE DEGRADED BY `v0.78.0`, recorded 2026-08-15 BEFORE the read.** `v0.78.0` restores a named plan recommendation to the Dashboard, which previously pointed into Explore for that job — so Explore-referred traffic changes for a reason unrelated to Explore's own pull, in both directions (fewer pointer referrals, but adopt clicks that never route through Explore at all). **The pre/post comparison is no longer clean and should not be read as one.** The decision to spend it was deliberate and the reason is recorded: **69% of packed-note learners cannot see the mastery suggestion at all, and 106 of them have a matching plan they have never adopted** — closing that outranks a baseline comparison. **What replaces it:** `v0.78.0` instruments the recommendation itself (impression + click on both surfaces, tagged `named-plan` vs `generic-pointer`), so the successor read is **the named recommendation's own impression→click rate**, measured directly rather than inferred from Explore traffic. **⚠️ It is NOT "does the named plan convert better than the pointer", and that correction was made at the pre-commit audit rather than discovered in September.** The two never render to the same learner — a named plan requires a program match, and the pointer renders precisely when there is none — so their audiences are disjoint (the pointer group is the no-program and no-coverage cohort). A pre/post-deploy comparison cannot rescue it either, because the pointer emitted **no events at all** before this release, so no baseline exists. **The `generic-pointer` events are therefore worth collecting on their own terms** — they finally size how many learners fall through to no match, which nothing has ever measured — but they are not a control group and must not be reported as one. A same-day documentation pass (2026-07-31) separately pressure-tested and reframed a not-yet-ratified proposal to extend Explore toward anonymous/public discovery under a named "Discovery System" concept — parked in the Backlog Index below (`Discovery System — Public Front Door`), gated on two owner decisions (including sequencing against this same checkpoint) before any `/kickoff`.

`v0.66.2 — Card Surface Token Fix` (on `releases/v0.66.2`, cut from `main` after `v0.66.1` merged) is the second-previous released version — kicked off, shipped, and signed off 2026-07-30. Patch release off `v0.66.1`'s line — did not consume a minor-version slot; `v0.67.0 — Explore Convergence` (above) is the release that finally claimed that reserved slot, after 8 reclaims by unrelated smaller releases. Scoped while discussing what's next after `v0.66.1`'s signoff: the `bg-card` no-op finding logged during `v0.66.0`'s pressure test, re-verified and corrected twice before shipping (21 usages across 10 files at first re-check, then 23 usages / 9 files / 22 rendered elements on a stricter recount). Shipped on a single PR (`fix/bg-card-token`, PR #947) — an owner-requested independent Codex audit (a separate agent, no inherited context) caught a real reasoning error before signoff: the original scoping claimed two `Card`+`bg-card` call sites (`challenge-quiz/page.tsx:2090`, `long-exam/page.tsx:1090`) were a deliberate override the fix preserved, but `cn()` has no `tailwind-merge` and compiled-CSS cascade order (not JSX class order) decides equal-specificity ties, so `bg-surface-alt` wins regardless — confirmed via `getComputedStyle` (not another screenshot) after the earlier visual read was found to have been fooled by a border-color difference, not an actual fill difference. The token choice (`--card: var(--background)`) survived on separate, verified nested-panel evidence; the false claim was removed rather than left standing. See `RELEASES.md` v0.66.2 for full Shipped scope, `docs/releases/v0.66.2.md` for the release notes, and this doc's own "v0.66.2 — Card Surface Token Fix" section in `docs/archive/ROADMAP_ARCHIVE.md` for full technical detail.

`v0.66.1 — Goal Detail Due-Concept Signal` (on `releases/v0.66.1`, cut from `main` after `v0.66.0` merged) is the second-previous released version — kicked off, shipped, and signed off 2026-07-30. Patch release off `v0.66.0`'s line — does not consume a minor-version slot; `v0.67.0 — Explore Convergence` remains the next candidate slot, still gated on the Diagnostic Read (due after 2026-08-06). Scoped 2026-07-30 while discussing what's next after `v0.66.0`'s signoff: a small, ungated Post-v0.40.0 Polish Backlog candidate (overdue color-warning on Goal-detail child Subject-plan cards), plus a doc-only fix for an anti-drift gap found while scoping it (`AGENTS.md:142` and `docs/features/collections.md:481` now name the card's pre-existing `overallReadinessPercentage`/progress-bar display as a second deliberate no-mastery-rule exception). Shipped on a single PR (`fix/goal-detail-due-concept-signal`, PR #945) — verified with `tsc`/lint/full Jest suite plus a real-browser render in both themes; below this doc's own full-pressure-test threshold, so a single `advisor()` check was the right depth. That check found one real doc-consistency gap in this section's own draft (a "signed off, no issues" claim written before the check had actually run, and a stale "previous released version" label on `v0.66.0`'s own entry once this one took that slot) — fixed before signoff, not a finding about the shipped code. See `RELEASES.md` v0.66.1 for full Shipped scope, `docs/releases/v0.66.1.md` for the release notes, and this doc's own "v0.66.1 — Goal Detail Due-Concept Signal" section in `docs/archive/ROADMAP_ARCHIVE.md` for full technical detail.

`v0.66.0 — Challenge Quiz Result Clarity` (on `releases/v0.66.0`, cut from `main` after v0.65.0 merged) is the second-previous released version — kicked off, shipped, and signed off 2026-07-30, all same day. Reclaimed the `v0.66.0` minor-version slot from `Explore Convergence` (renumbered to `v0.67.0`), since this item is small, ungated, and ready now, while Explore's Diagnostic Read gate remains unmet (due after 2026-08-06) — the seventh time a slot has changed hands this way, following `v0.60.0`, `v0.61.0`, `v0.62.0`, `v0.63.0`, `v0.64.0`, and `v0.65.0`. Closed the "Challenge Quiz's outcome area still reads as an accreted card stack" Known Limitation logged while signing off `v0.65.0` — consolidated standard Challenge Quiz mode's result branch onto the same `ScoreReveal`-based pattern Board Exam Mode's own result branch already used (PR #943), followed by an owner-requested independent Opus review (fresh agent, no inherited context) plus a real-browser check that found and fixed two presentation gaps (helper text separated from the metrics it explains; an ineffective `aria-label` on a non-naming-capable element) and confirmed one flagged concern (a nested-card visual read) did not hold up once actually rendered. The performance pill's styling changed as a deliberate side effect of adopting the shared component (outline-only, matching Board Exam Mode, chosen over filled hue badges for dark-mode legibility) — confirmed with the owner before signoff. See `RELEASES.md` v0.66.0 for full Shipped scope, `docs/releases/v0.66.0.md` for the release notes, and this doc's own "v0.66.0 — Challenge Quiz Result Clarity" section in `docs/archive/ROADMAP_ARCHIVE.md` for full technical detail. `v0.67.0 — Explore Convergence` remains the next candidate slot, still gated on the Diagnostic Read (due after 2026-08-06) — not yet cleared. See the Phase 2 section and dependency spine below.

`v0.65.0 — Study Effectiveness Polish` (on `releases/v0.65.0`, cut from `main` after v0.64.0 merged) is the second-previous released version — kicked off, shipped, and signed off 2026-07-30, all same day. Reclaimed the `v0.65.0` minor-version slot from `Explore Convergence` (renumbered at the time to `v0.66.0`, now `v0.67.0` after `Challenge Quiz Result Clarity` reclaimed `v0.66.0` the same way), since this item is small, ungated, and ready now, while Explore's Diagnostic Read gate remains unmet — the sixth time a slot has changed hands this way, following `v0.60.0`, `v0.61.0`, `v0.62.0`, `v0.63.0`, and `v0.64.0`. Closed out the remaining, still-valid candidates from the 2026-07-22 Study Effectiveness/UI Polish/Pricing Fit consultation: Study Pack scope surfacing (PR #939), card-accretion layout pass on Review Set Detail and quiz result screens (PR #940), collapsed-Companion teaser (PR #938), and Adaptive Practice per-question rationale tag (PR #941). Gated to a single `advisor()` pre-signoff check per this doc's own gating rule (4 PRs, file overlaps confirmed disjoint-region not shared-method), which confirmed the merged-branch build/test/tsc pass and found no additional issues beyond what each PR's own audit had already fixed. See `RELEASES.md` v0.65.0 for full Shipped scope and Known Limitations, `docs/releases/v0.65.0.md` for the release notes, and this doc's own "v0.65.0 — Study Effectiveness Polish" section in `docs/archive/ROADMAP_ARCHIVE.md` for full technical detail.

`v0.64.0 — Add to Review Set` (on `releases/v0.64.0`, cut from `main` after v0.63.0 merged) is the second-previous released version — kicked off, shipped, and signed off 2026-07-29, all same day. Reclaimed the `v0.64.0` minor-version slot from `Explore Convergence` (renumbered at the time to `v0.65.0`, now `v0.67.0` after two further reclaims), since this item is small, ungated, and ready now, while Explore's Diagnostic Read gate remains unmet — the fifth time a slot has changed hands this way, following `v0.60.0`, `v0.61.0`, `v0.62.0`, and `v0.63.0`. Surfaced while discussing v0.63.0's Companion signoff; one item shipped (Add to Review Set + doc corrections, PR #935), gated to a single `advisor()` pre-signoff check per this doc's own gating rule (single PR, frontend-only, no shared-method collisions), which found and fixed one real gap (`itemNoun` default removed, made required). A second, larger architectural question arrived mid-release (whether "Ask Companion" should become a cross-cutting "Companion" guidance system) and was resolved separately — considered and narrowed, not rejected, recorded as its own Backlog Index row ("Companion Guidance Doctrine") rather than folded into this release, since it's phased future work, not something this release shipped. See `RELEASES.md` v0.64.0 for full Shipped scope, `docs/releases/v0.64.0.md` for the release notes, and this doc's own "v0.64.0 — Add to Review Set" section in `docs/archive/ROADMAP_ARCHIVE.md` for full technical detail.

`v0.61.0 - Challenge Quiz Quota Increase` (on `releases/v0.61.0`, cut from `main` after v0.60.3 merged) is the previous released version — kicked off, shipped, and signed off 2026-07-28. Reclaimed the `v0.61.0` minor-version slot from `Explore Convergence` (renumbered at the time to `v0.62.0`, now `v0.63.0` after Knowledge Impact reclaimed `v0.62.0` the same way), since this item's gate (owner ratification) cleared 2026-07-28 while Explore's gate remains unmet. Two items shipped: a substantial Challenge Quiz monthly quota increase (FREE 5→20, PLUS 25→100, PRO 50→200, PR #922) and Challenge Quiz LLM token/cost telemetry (same PR) — both ratified by the owner 2026-07-28, overriding this doc's own prior data/instrumentation gates. A bonus fix also shipped in this release: the `ONBOARDING_V2_ABANDONED` over-firing/leak-path bug found while investigating this release's own onboarding funnel re-check (PR #921). See the "Challenge Quiz quota increase" and "`ONBOARDING_V2_ABANDONED` instrumentation bug" Backlog Index rows for full ratification/fix history, `RELEASES.md` v0.61.0 for full Shipped scope and Known Limitations, and this doc's own "v0.61.0 — Challenge Quiz Quota Increase" section in `docs/archive/ROADMAP_ARCHIVE.md` for full technical detail. A price decrease (the other half of the original held proposal) stays explicitly deferred, not part of this release.

`v0.60.3 - Challenge Quiz Shaping` (on `releases/v0.60.3`, cut from `main` after v0.60.2 merged, and after the pre-kickoff scoping/second-opinion work on `docs/v0.60.3-challenge-quiz-shaping-scoping` merged the same way) is the previous released version — kicked off 2026-07-28, shipped and signed off 2026-07-28. **All three of its items (adaptive initial question count, the Redo Missed Questions session-matching fix, the incomplete-submission guard) shipped via PR #918 (routed to Codex as a bundled prompt/single-PR, audited before commit).** A fourth item scoped alongside these three (onboarding coverage-gap capture) was **deferred out of this release**, not shipped: its own kickoff gate required a prod query confirming effectively-zero `STUDENT`-profile presence in the Diagnostic Read's signup-surge measurement cohort, to avoid contaminating that read's funnel data mid-measurement. **Query run 2026-07-28 — failed the bar** (surge day n=29: `STUDENT` 2/6.9%, not effectively zero). Rather than leave the release open for the ~1-2 weeks until the Diagnostic Read closes, v0.60.3 was retitled (dropping "& Onboarding Coverage Capture") and signed off on its three shipped items; the deferred item's design stays valid and now lives as its own Backlog Index row, gated on the Diagnostic Read closing (~2026-08-06) and a clean re-run of the gate query. See `RELEASES.md` v0.60.3 for full Shipped Scope, and this doc's own "v0.60.3 — Challenge Quiz Shaping" section in `docs/archive/ROADMAP_ARCHIVE.md` for full technical detail on the shipped items and the deferred item's still-valid design, including the second-opinion review that shaped the final Item 2 and Item 4 designs. Third patch release off `v0.60.0`'s line, so it did not consume a minor-version slot.

`v0.60.2 - Challenge Quiz Known-Limitations Cleanup` is the previous released version (on `releases/v0.60.2`, cut from `main` after v0.60.1 merged) — a 3-item cleanup of narrow, ungated concurrency and entry-point gaps logged as v0.60.1's own Known Limitations: a claim-release rollback that could leave Challenge Quiz bank rows stuck claimed after a mid-batch generation failure (fixed via `REQUIRES_NEW` on `releaseClaims`), an expiry-vs-completion lock race that could silently overwrite a genuinely completed session back to forfeited (fixed via a locked re-fetch and status recheck before forfeiting), and a missing Resume/Start Fresh prompt at the collection/Review Set premium-exam entry point (fixed, `entry=mode-selection` now carried on that navigation). Kicked off and shipped same-day, 2026-07-26; routed to Codex as a deliberate one-off token-conservation choice rather than this project's normal "isolated bug fix, Claude Code direct" lane, then audited before commit per this project's standard Codex-delivery process — one accepted test-coverage gap logged (Item 1's new test verifies the `REQUIRES_NEW` annotation is declared, not behaviorally that a release survives an outer rollback, since no existing integration test touches `challenge_quiz_question_bank`). See `RELEASES.md` v0.60.2 for full Shipped detail, and its own "v0.60.2 — Challenge Quiz Known-Limitations Cleanup" section in `docs/archive/ROADMAP_ARCHIVE.md` for the original technical plan. Second patch release off `v0.60.0`/`v0.60.1`'s line, so it did not consume a minor-version slot — `v0.61.0 - Explore Convergence` remains the next minor-version slot, still gated on the Diagnostic Read re-read (due after 2026-08-06).

`v0.60.1 - Challenge Quiz Fix Pass` is the previous released version (on `releases/v0.60.1`, cut from `main` after v0.60.0 merged) — a 5-item bug-fix pass on Challenge Quiz, discovered while exercising v0.60.0's Official template sharing in production: an executor-saturation bug blocking the backfill, a never-implemented whole-array shuffle (MATCHING-block-aware), a silently-inert manual difficulty selector (removed entirely rather than fixed, per product decision), abandoned sessions silently resumed and auto-submitted as timed-out, and a non-functional "Redo Missed Questions" button. A same-day pre-signoff pressure test found and fixed three additional refinements and logged the three known limitations v0.60.2 above went on to close. See `RELEASES.md` v0.60.1 for full scope.

`v0.60.0 - Shared Official Pool Foundation` is the previous released version (on `releases/v0.60.0`, cut from `main` after v0.59.0 merged) — Phase 3a (cross-user question pool foundation), ratified, kicked off, and shipped 2026-07-24 after production adoption-concurrency data cleared its proposed gate (see "Company Redefinition Roadmap — Phase Detail" below, Phase 3 section, for the evidence and full scope). **Retargeted 2026-07-24, same day as kickoff, from the originally-scoped exam-pool (`ExamQuestionPoolService`) design to Challenge Quiz Official template sharing** — the gate evidence's adopters are FREE-tier and Long/Board Exam pools are PRO-gated with ~zero production load; see `RELEASES.md` v0.60.0 for the retargeted scope, the diagnostic trail, and known limitations (the backfill for already-published Official content needs one manual admin trigger post-deploy — new content self-seeds automatically). Reclaimed the `v0.60.0` slot from `Explore Convergence` the same day, since this chunk was ratified and kicked off on real data while Explore's gate remains unmet — Explore Convergence renumbers to `v0.61.0`.

`v0.59.0 - Dashboard & Progress Reorg` is the previous released version (on `releases/v0.59.0`, cut from `main` after v0.58.0 merged) — the second of Phase 2's two separately-gated chunks, kicked off and shipped now that its gate (Reusable Practice Assets & the Return Loop shipped) was satisfied. Reclaimed the `v0.59.0` slot from `Explore Convergence` on 2026-07-24, because this chunk's gate cleared and Explore's hadn't. See "Company Redefinition Roadmap — Phase Detail" below, Phase 2 section, for full scope and the chunk split. `Explore Convergence` itself has since been renumbered twice more — see this note's own history above for the live current numbering.

`v0.58.0 - Reusable Practice Assets & the Return Loop` is the previous released version (on `releases/v0.58.0`, cut from `main` after the 2026-07-24 ratification docs merged) — the Diagnostic Read + Reusable Practice Assets sequence ratified by the owner 2026-07-24; see "Company Redefinition Roadmap — Phase Detail" below, "Reusable Practice Assets & the Return Loop" subsection, for full scope. **This claimed the `v0.58.0` slot previously earmarked for `Explore Convergence`** — Phase 2's two chunks shifted to `v0.59.0` (Explore) and `v0.60.0` (Dashboard & Progress Reorg) at that point; Phase 3's illustrative `Shared Official Pool Foundation` chunk shifts to `v0.61.0`. **Those chunks have since swapped slots twice more as each one's own gate cleared or didn't** — see the Current Release Baseline note above for the live numbering, not the specific numbers in this historical entry. See the Backlog Index row below for the full decision history.

`v0.57.0 - Practice-First Activation Onboarding` is the previous released version (on `releases/v0.57.0`, cut from `main` after v0.56.0 merged) — Phase 1 of the Company Redefinition roadmap (see "Company Redefinition (Boardready-Model Re-Architecture, 6 docs)" in the Backlog Index below, and `docs/claude-prompt/company-redefinition-out/06-unified-roadmap.md`). See its section in `docs/archive/ROADMAP_ARCHIVE.md`.

`v0.56.0 - Weak-Concept Explanation Links` is the previous released version (on `releases/v0.56.0`, cut from `main` after v0.55.0 merged) — see its section in `docs/archive/ROADMAP_ARCHIVE.md`.

`v0.55.0 - Result-Screen Companion Bridge` is the previous released version — see its section in `docs/archive/ROADMAP_ARCHIVE.md`.

`v0.54.1 - Public Note Copy Correctness Fixes` is the previous released version — see its section in `docs/archive/ROADMAP_ARCHIVE.md`.

`v0.54.0 - CPALE Exam Hub (Wave 2)` is the previous released version — see its section in `docs/archive/ROADMAP_ARCHIVE.md`.

`v0.53.0 - SEO Discoverability: Exam Hub Depth & Organic Attribution` is the previous released version — see its section in `docs/archive/ROADMAP_ARCHIVE.md`.

`v0.52.1 - Early-Lifecycle Feedback Signals` is the previous released version — see its section in `docs/archive/ROADMAP_ARCHIVE.md`.

`v0.52.0 - Proactive In-App Feedback Prompts` is the previous released version — see its section in `docs/archive/ROADMAP_ARCHIVE.md`.

`v0.51.1 - Dashboard Stage-1 Limit Wiring` is the previous released version — see its section in `docs/archive/ROADMAP_ARCHIVE.md`.

`v0.51.0 - Read-Path Performance Pass II` is the previous released version before that — see its section in `docs/archive/ROADMAP_ARCHIVE.md`.

`v0.50.4 - Exam Hub Discovery Polish` is the previous released version — see its section in `docs/archive/ROADMAP_ARCHIVE.md`.

`v0.50.3 - Public Note Copy Flow & Related-Notes Consistency` is the previous released version — see its section in `docs/archive/ROADMAP_ARCHIVE.md`.

`v0.50.2 - Note Card Content Consistency` is the previous released version — see its section in `docs/archive/ROADMAP_ARCHIVE.md`.

`v0.50.1 - Mobile UI Polish` is the version before that (on `releases/v0.50.1`, cut from `main` after v0.50.0 merged) — a fast-follow patch batch: tab bar refinements (filter-retaining Library/Public Library links, a backend-persisted show/hide preference — the icon-only variant was implemented then reverted after a consumer-psychology review) plus two small unrelated pre-existing UI issues (Review Set description truncation, Progress page milestone empty state/Concept Mastery subject-row rework) surfaced by direct user report. Not retention-flavored. See `RELEASES.md`.

`v0.50.0 - Mobile Bottom Tab Bar` is the version before that (on `releases/v0.50.0`, cut from `main` after v0.49.0 merged) — a persistent 4-tab mobile bottom navigation bar (Dashboard, Library, Review Sets, Public Library), gated on device-mix evidence from the App Shape Fable proposal and un-parked by a 2026-07-15 production pull showing ~75% mobile usage by distinct users. Navigation-shape work, not a retention experiment — orthogonal to the concurrently-accruing v0.48.0 cohort read. Mid-release scope addition: the 3 held instrumentation pulls (UTM/referral tracking, offline-fallback hit rate, browse-without-adopt tracking) folded in after an explicit 2026-07-15 decision to instrument — analytics-collection only, no new UI surfaced from the data yet, so it doesn't confound the concurrently-accruing v0.48.0 cohort read either. Also shipped a same-day sitemap fix (Exam Hub pages), found while scoping a separate SEO strategy question. See `RELEASES.md`.

`v0.49.0 - Progress Page: Private Library Links` is the previous released version (on `releases/v0.49.0`, cut from `main` after v0.48.0 merged) — a small, deliberately non-retention-flavored fix: Progress page's per-subject links and its "weakest subject" CTA now point at the learner's private Library instead of the public one. Scoped to fill the interim window while the v0.48.0 retention experiment cohort accrues enough data for a read, without confounding that read. See `RELEASES.md`.

`v0.48.0 - Retention Experiment: Open Loop & Digest Trigger` is the previous released version (on `releases/v0.48.0`, cut from `main` after v0.47.1 merged) — two independent retention experiments testing the co-dominant causes from the retention root-cause diagnosis: an open-loop first-quiz ending (frontend) and a due-concepts digest default-ON trigger fix with CTA/content work (backend + frontend). Both are unproven experiments, not confirmed wins — lift is not yet measured. See `RELEASES.md`.

`v0.47.1 - V82 Migration Collision Hotfix` is the previous released version (on `releases/v0.47.1`, cut from `main` after v0.47.0 merged) — a single-file fix for a duplicate Flyway migration version that had blocked every production deploy since `v0.46.0` merged. No schema or behavior change, renumbers a never-applied migration only. See `RELEASES.md`.

`v0.47.0 - Conversion Audit Tier 4: Cleanup Batch` is the previous released version (on `releases/v0.47.0`, cut from `main` after v0.46.0 merged) — 16 low-impact, cheap-cleanup items from the conversion/retention UX audit backlog's Tier 4, shipped across 6 Codex-prompted PRs (Landing & Pricing, Public Note Detail, Discovery & Library, Onboarding copy, Doc hygiene, Learn signup-intent). Item 37 was initially dropped, then folded back in at a smaller scope once its original cookie-based mechanism proved a poor fit. No new backend entities, migrations, or endpoints. See `RELEASES.md`.

`v0.46.0 - Retention Depth: Due-Concepts Digest & Exam Pacing` is the previous released version (on `releases/v0.46.0`, cut from `main` **before** `v0.45.2` existed, now rebased onto latest `main`) — two Fable-sourced new-capability ideas (`docs/claude-prompt/new-capability-out/01-new-capability-ideation.md`, Ideas 1 and 5): a weekly due-concepts email digest as a new type on the existing `RetentionEmailScheduler`, and an exam-date pacing plan scheduling the learner's owned content only (explicitly not Smart Review Planning). Scope broadened mid-release with a session-completion pacing echo and a 4-item app-feel polish batch (a 5th, a sticky search/filter toolbar, shipped then was reverted the same day after a user-reported mobile regression). See `RELEASES.md`.

`v0.45.2 - Public Plan Preview Rollup Fix` is the previous released version (on `releases/v0.45.2`) — a fix to the one remaining gap in v0.45.1's Goal-collection rollup fix: the plan-preview panel's `getPublic()` endpoint, a third code path v0.45.1 never touched, plus a note-list cap (the rollup fix now surfaces every note, and some production plans have 40-52) so the preview panel stays bounded. No new backend entities, migrations, or pricing/quota changes. See `RELEASES.md`.

`v0.45.1 - Study Plan Collection Fixes` is the previous released version before that (on `releases/v0.45.1`) — three pre-existing collection/discovery defects surfaced during v0.45.0 pre-signoff review: a Goal-collection note-count rollup that always showed 0 when notes lived only on child Subject Plans, a Published Plans backlink that ignored how the user actually arrived, and no path back to the official-plan catalog once a primary study plan was set. Also fixed a dark-mode contrast bug on the `Official` plan badge, found post-merge. No new backend entities, migrations, or pricing/quota changes. See `RELEASES.md`.

`v0.45.0 - Conversion Audit Tier 3 — Landing, Pricing & Discovery Polish` is the previous released version before that (on `releases/v0.45.0`) — 18 medium-impact, lower-urgency items (#19–36) from the same 7-session conversion/retention UX audit that drove v0.44.0, shipped across 6 thematic PRs (landing, pricing, note detail, public plan card, onboarding/Dashboard guidance, Public Library) plus two post-merge follow-ups and one regression-fix bundle. See `RELEASES.md`.

`v0.44.0 - Conversion & Retention Polish` is the previous released version before that (on `releases/v0.44.0`) — Tier 1 findings from the same audit shipped in full, plus a verified backend fix for Quick Review's missing ConceptHealth tracking and a mid-release copy-on-signup onboarding-intercept fix (both pressure-tested and closed), plus Tier 2 of the same audit backlog (7 items, folded in as a second slice rather than opening a new version). A whole-release pre-signoff pressure test found no functional defects; three items are logged as known, unfixed limitations rather than blockers — see `RELEASES.md`. The "AI-generated Review Sets" candidate (see "Future, gated — AI-generated Review Sets" below) remains under product/UX discussion and has not been scoped or kicked off; a much larger, separate "Smart Review Planning" exploration exists as paused planning material in `docs/claude-prompt/fable-out/` and is not yet scoped to any release either — see the Backlog Index above for its current gate condition.

`v0.43.1 - Companion Mentor Tips` is the previous released version before that (on `releases/v0.43.1`).

`v0.43.0 - Companion Coach Experience` is the previous released version before that (on `releases/v0.43.0`).

`v0.42.1 - Companion & Progress Polish` is the previous released version (on `releases/v0.42.1`); `v0.42.0 - AI-assisted Companion authoring + regeneration` before that.

`v0.41.1 - Review Set Detail Page: This-Set Study Dashboard` is the previous released version (on `releases/v0.41.1`).

Older released versions (`v0.41.0` and earlier, back to `v0.11.0`) are summarized in `docs/archive/ROADMAP_ARCHIVE.md`'s index and in full in `RELEASES.md` / `docs/archive/RELEASES_ARCHIVE.md` / `docs/releases/vX.Y.Z.md`.

## Current Release Baseline entries moved from `ROADMAP.md`, 2026-09-07 (`v0.130.0` signoff)

Moved verbatim at the `v0.130.0` signoff, when the live `## Current Release Baseline` named eight
versions against a documented design of *current + the last five*. **A MOVE, NOT A DELETE** — live +
archive together still hold every entry byte-for-byte.

**Kicked off 2026-09-06.** `v0.124.0 — Collection Path Performance` is **Released** on `releases/v0.124.0`, cut from `main` after `v0.123.0` merged and tagged.

**⚠️ IT OVERRIDES ITS OWN GATE BY EXPLICIT OWNER DECISION (2026-09-06), RECORDED RATHER THAN ROUTED AROUND.** The performance-audit Backlog row says *"re-read after `v0.123.0` deploys"* before taking sequencing 4, and **`v0.123.0` has not been observed in production.** A later session reading that row in isolation must come here first. **⚠️ Accepted residual: sized from the audit's STATIC read, so if `v0.123.0`'s lazy note list already removed most of the felt cost, the benefit is smaller than the arithmetic implies.**

**THE DEFECT IS ARITHMETIC:** `refreshBuilder` issues **one `getCollection` per child Subject Plan**, so a 20-plan Review Set is **21 requests / ~147 queries for one page**. **⚠️ Concurrency is the sharp edge, not latency — the pool is 20 and `v0.112.0` documents exhaustion as a live failure mode.**

**⚠️ SHAPE DECIDED AT KICKOFF, AND THE OBVIOUS OPTION IS WRONG: `getCollectionGoal` has SEVEN frontend consumers and six need no child items**, so widening `GoalCollectionDetailResponse` would inflate the Dashboard and three exam prestart paths to serve the builder alone. **A SEPARATE BATCH READ; the goal response is not widened.**

**⚠️ The dated-read cluster is FOUR DAYS OUT (`2026-09-10` → `2026-09-19`, twelve reads) and the Goal builder path is measured by none of them** — which is what makes this release compatible with the window. **⚠️ `frontend/app/onboarding` stays frozen: the freeze lifts when the `2026-09-11` READ IS TAKEN, not when the date passes.** Full scope and anti-drift in `RELEASES.md`.

**Previous: `v0.123.0 — Collection Builder Integrity` is RELEASED** on `releases/v0.123.0`, cut from `main` after `v0.122.0` merged and tagged.

**⚠️ IT OPENS ON TWO UNTRACKED FILES KICKOFF STEP 8 SURFACED, NOT ON A ROADMAP QUEUE — and both point at the SAME surface, which is why they are one release rather than two.** An owner-reported incident (`docs/claude-findings/2026-09-06-study-plan-builder-section-label-refresh-loop.md`) and `docs/claude-plans/note-collection-page-performance-audit.md`. **Both were unindexed; both get Backlog Index rows in this kickoff commit.**

**THE DEFECT:** `/collections/{id}/builder` wedges in an **unbounded write→refresh loop** after a section label is set to a long pasted value. `LeafSortableNoteCard`'s auto-save effect (`:445-455`) compares `item.label` against a **locally normalised** form of its own input, while the value reaching the server is chosen by a **different** normalisation in `handleLeafLabelChange` (`:1646`) — so when they disagree the guard never clears, with **no attempt cap, no backoff, no failure short-circuit**. **⚠️ Mechanics proven from code; INGRESS UNRESOLVED** — the row was deleted before it could be read and the Render MCP was unreachable, so nothing rests on production data.

**⚠️ THE TWO DEFECTS COMPOUND: the loop's DAMAGE MULTIPLIER IS THE AUDIT'S LEVER 1.** Each iteration calls `refreshBuilder`, refetching the curator's **entire unbounded note list**, so a wedged page issues a heavy `listNotes()` every ~500 ms–1 s from every affected client — **a client-driven load amplifier against the backend the 2026-09-05 outage showed has no headroom** — and **adoption propagates it**, since an uncollapsed label on a published source plan wedges every learner who adopts it.

**⚠️ VERIFICATION IS ONE SCOPED COLD AGENT, AND THE TIER IS A CONSEQUENCE OF AN OWNER DECISION RATHER THAN INHERITED: the recommendation was FOUR items (the loop fix plus audit sequencing 1 and 2) and the owner took SEVEN**, which fires the gate's named trigger that two or more changes touch the same shared method. Full scope and anti-drift in `RELEASES.md`.

**Kicked off 2026-09-07.** `v0.125.0 — Bounded Reads` is **Released** on `releases/v0.125.0`, cut from `main` after `v0.124.0` merged and tagged. **Two items, both the last named instances of a shape that has caused THREE production events:** `OfficialChallengeQuizTemplateService.queueBackfill:81` (the last full-catalog entity load — plus TWO per-note queries the Backlog row does not mention, ~2,884 for one admin action) and server-side note search, which is the stated prerequisite for bounding the note picker. **⚠️ SCOPE IS SET BY A DATED CLUSTER: `[CHECKPOINT — due 2026-09-10]` is THREE days out and `[CHECKPOINT — due 2026-09-11]` FOUR, so the substantial onboarding work is BLOCKED FOR FOUR MORE DAYS and is next, not now.** **⚠️ Search ships with or before the bound, never a bound alone — `v0.123.0` declined the bound precisely because client-side filtering makes notes beyond a limit UNADDABLE.**

**Kicked off 2026-09-07.** `v0.126.0 — Context Budget` is **Released** on `releases/v0.126.0`, cut from `main` after `v0.125.0` merged and tagged. **Five items, all docs and `.claude/commands`, NO product code.** It **resumes** the archiving convention `docs/archive/README.md` already documents and that was applied once on 2026-07-10 — **85 releases ago** — and lapsed because neither kickoff nor signoff carries an archiving step. `RELEASES.md` holds **116 sections** against a documented design of *current + last few versions*, and `CLAUDE.md` carried **one 322,329-character line** (~80k tokens, ~90% of the file) on every session. **⚠️ Item 5 is what stops it lapsing again; without it items 1-4 regrow.** **⚠️ Items 3 and 4 delete from the anti-drift documents — every governing rule must survive, only shipped-release narrative goes. Backlog Index pruning (item 6) and the AGENTS.md architecture split (item 7) are BOTH EXCLUDED.**

**Kicked off 2026-09-07.** `v0.127.0 — Failure Attribution and Learner Dates` is **Released** on `releases/v0.127.0`, cut from `main` after `v0.126.0` merged and tagged. **Two real defects, chosen because they need expensive verification while that is still cheap** — the Max window closes 2026-09-10 and what it buys is cold-agent review, so the ~140k-token Backlog Index work is deliberately left for later. **(1)** `NoteCollectionService:778` NULLs a learner's own `targetCompletionDate` on reparent, unrecoverably — while the sibling path at `:1005` already PROMOTES that date to the Goal. **(2)** a production regeneration failure left ZERO database trace; needs the migration that parked it.

**Kicked off 2026-09-07.** `v0.128.0 — Onboarding Unfrozen` is **Released** on `releases/v0.128.0`, cut from `main` after `v0.127.0` merged and tagged. **⚠️ THE ONBOARDING FREEZE IS LIFTED (owner, 2026-09-07) AND THE READ IT PROTECTED WAS TAKEN FIRST: 393 signups all-time, 249 completed, 63.4% against a 62.4% baseline.** **⚠️ The finding that discharges `[CHECKPOINT — due 2026-09-11]` is the DENOMINATOR: the post-`v0.73.0` cohort is EIGHTEEN signups, so 83.3% is noise and the checkpoint was never answerable on its date.** Scope is only what the freeze blocked: Summary maths rendering (`remark-math` for tokenization, existing KaTeX for render) and catalog-first suggestions in onboarding.

**Kicked off 2026-09-07.** `v0.129.0 — Adoption Signal` is **Released** on `releases/v0.129.0`, cut from `main` after `v0.128.0` merged. **Stage 2 of `docs/claude-plans/in-app-notifications-and-review-set-adoption-signals-stage1.md` — the one stage that does not depend on notifications existing**, and which that document explicitly instructs must not be folded into the notification work. Scope: show how many learners have adopted an Official Review Set, on Explore cards and Review Set detail. **⚠️ ONE INDEX IS THE ONLY SCHEMA CHANGE** — the count needs no `DISTINCT` because `V76`'s partial UNIQUE index on `(owner_user_id, source_plan_id)` already makes one-learner-one-adoption a database invariant; the new index exists only because that one leads on `owner_user_id` and so cannot serve a `source_plan_id` filter. **Both of §10's production reads were RUN 2026-09-07 read-only rather than deferred to the owner: provenance completeness is 91.2%, so §10's "wait if materially low" does NOT trigger; curator self-copies are zero.** **⚠️ THE READ FOUND A DISPLAY PROBLEM THE PLAN COULD NOT HAVE ANTICIPATED: adopting a Goal fans out to its children, so `LET Comprehensive Review` and each of its four children all read 40, and `PNLE` and its seven children all read 15 — the counts are CORRECT and must never be summed, but they read as duplicated; and the fan-out is NOT uniform (`CPALE` parent 8 vs children 4), so it cannot be special-cased away.** **⚠️⚠️ CORRECTED 2026-09-07 BEFORE ANY CODE WAS WRITTEN — THE FAN-OUT IS REAL IN THE DATA BUT INERT ON BOTH SURFACES IN SCOPE, AND THE FIRST WORDING OVERSTATED IT.** `listPublic` calls `findByVisibilityAndParentCollectionIdIsNullOrderByUpdatedAtDesc`, so **Explore renders TOP-LEVEL collections only** — children are fetched solely to roll up item counts and are never cards. `getPublic`'s `toPublicDetailResponse` returns a `childCount` **number**, not child summaries, so the public detail page has no per-child list to hang a count on. **⚠️ THEREFORE THERE IS NO "duplicated down a Goal's subject list" TO FIX IN THIS RELEASE — do NOT build a suppression rule, a roll-up, or a parent/child display heuristic for a problem no surface currently exhibits.** The fact is kept because it goes live the instant any surface renders children with counts; it is not a `v0.129.0` deliverable. **✅ The small-count threshold was DECIDED by the owner 2026-09-07: 5** — show the count at 5+ adopters, omit it entirely below that (no range, no "fewer than 5"). Display policy only; the queried count stays exact. **⚠️ AND THE THRESHOLD IS CURRENTLY INERT TOO, WHICH IS THE OTHER HALF OF THE SAME CORRECTION: every top-level PUBLIC set is ALREADY ≥8** — LET 40, ALE 30, PNLE 15, CPALE 8, and those four are the entire Explore surface. **The `4`s and `1`s are all CHILDREN**, and the `1`s are PRIVATE. So threshold 5 hides **nothing today** — it is a DEFENSIVE policy for the first small set that gets published top-level, not an active filter. **⚠️ Do NOT claim it "hides nine sets"** — that counted rows the surfaces never render. **⚠️ Not to be re-derived from a later read — it is a decision, not a computed value.** **⚠️ NO Learning Connections work — `[CHECKPOINT — due 2026-09-19]` is twelve days out.** Verification is a single `advisor()` call, per the plan's own Verification section. Full scope and anti-drift in `RELEASES.md`.

**Kicked off 2026-09-07.** `v0.130.0 — Notification Inbox` is **Released** on `releases/v0.130.0`, **cut from `releases/v0.129.0` rather than `main`** because `v0.129.0`'s release PR #1340 is blocked by the `main` ruleset's `require_extra_approval_for_unattributed_changes` parameter (the `v0.120.0`/`v0.111.0` precedent). Scope is **Stages 3 and 4** of `docs/claude-plans/in-app-notifications-and-review-set-adoption-signals-stage1.md`: the `notifications` substrate (unique `(recipient_user_id, dedup_key)` as the idempotency guarantee, inbox, read/dismiss, actionable count, bell, polling, mobile sheet) plus Admin What's New (`announcements`, immutable after publish, fan-out, internal-only CTA, excluded from the numeric badge). **⚠️ PAIRED BECAUSE STAGE 3 ALONE SHIPS AN EMPTY INBOX — production has 1 ACCEPTED relationship, 1 PENDING invitation and ZERO note shares**, so Stage 4 is the only producer that generates content on demand. **⚠️ STAGE 5 IS DEFERRED ON CONTAMINATION, NOT EFFORT: it would surface pending connection requests and so move `ACCEPTED`, the very metric `[CHECKPOINT — due 2026-09-19]` kills the arc on — and that denominator is ONE.** Stage 6 stays blocked on §8's drift-signature decision; Stage 7 is out of scope. **⚠️ VERIFICATION WAS UPGRADED AT SIGNOFF from the pledged one scoped cold agent to the FULL THREE-AGENT TEST**, with `v0.129.0` folded in as a third partition because it had been signed off without one. **Every defect it found was invisible to a green suite** — the badge counted dismissed-unread rows forever, the poll could never refresh its token so the badge froze after 15 minutes idle, a purged account kept its notifications, and `app-shell.test.tsx` omitted the poll from its mock so the badge path ran in no test. **Three were in the released `v0.129.0`.** All fixed; the fan-out connection hold is documented and owes `[CHECKPOINT — due 2026-10-07]`. Full scope, findings and anti-drift in `RELEASES.md`.

**Kicked off 2026-09-08, signed off 2026-09-08.** `v0.133.0 — Education Family` is **Released** on `releases/v0.133.0`, cut from `main` after `v0.132.0` merged as #1347 and tagged. Source: `docs/claude-plans/program-family-generalization-and-education-family.md`. **⚠️⚠️ THE BRIEF'S CENTRAL PREMISE WAS FALSE AND THE AUDIT DISPROVED IT: there is no Engineering-specific authoring shortcut, so there was nothing to generalize.** The applicable-programs combobox derives families DYNAMICALLY from the catalog and holds no Engineering literal; it read *"Add all 18 Engineering programs"* only because Engineering was the only family with members. **So the release is a DATA change plus one admin surface, and the combobox was NOT modified.** **Shipped:** `V142` (seeds the `Education` family, assigns the EXISTING `Education` row, **RENAMES** `Special Needs Education – Generalist` → `Special Needs Education` keeping its `id`, and adds six programs — all eight members carry `exam_goal_slug = 'let'`, taking the family's LET-tagged programs from ONE to EIGHT); helper-text copy polish; three multi-family combobox guards (**every prior fixture held exactly ONE family, so a component that ignored which family was clicked would have shipped green**); and a **create-family** admin surface, so a new family no longer costs a migration. **⚠️ ONE PLANNED ITEM WAS NOT BUILT BECAUSE IT ALREADY EXISTED** — the admin family *selector* has been in `admin-course-program-catalog-section.tsx` since `9e77f412` (`v0.100.0`); the audit concluded it was missing by reading a 40-line shell. The create-family surface was substituted for it. **✅ THE PRECONDITION READ RAN 2026-09-08 and verified the audit's warning: Engineering has EIGHTEEN programs against `V106`'s THREE seeds**, so a repo-only duplicate audit would have been unsound. No collisions, and ZERO Notes linked to the rename target — **but decision 1's condition was NOT met: one `users.course_program` row holds the old literal.** Nothing breaks (it is consumed as free text, never resolved by name); it is handed to the owner as `docs/claude-plans/v0.133.0-owner-profile-string-update.sql`, **to run in the SAME maintenance step as `V142`, not before it.** **⚠️ `V142` IS UNRUN AND QUEUES BEHIND `V141` (`v0.132.0`), so the release's headline is unverified in production** — `[CHECKPOINT — due deploy + 1 day]` and `docs/claude-plans/v0.133.0-education-family-post-deploy-read.sql` exist for exactly that, and both this checkpoint and `v0.132.0`'s `[CHECKPOINT — due 2026-09-22]` must be RE-DATED if the deploy slips. **Verification: `advisor()` plus one scoped cold agent** — the tier was re-decided when the shape changed, because folding the create-family surface added a WRITE ENDPOINT and `V142` changes production catalog semantics. **The agent refuted THREE of the session's seven named claims and all three were fixed before signoff:** the new repository SQL was executed by NO test (that class is structurally invisible to the `@Query`-reflecting PostgreSQL harness), an added exception had zero test references, and **`"nothing resolves a course_program by name"` was FALSE** — `bulk-generation-page-client.tsx:290` matches the catalog by name against the free-text profile field, so the one stale account loses Bulk Generate auto-selection after the rename. That correction reached the owner-run `.sql` and strengthens the recommendation to run it. Full scope and anti-drift in `RELEASES.md`.

## Backlog Index — archived rows (moved 2026-09-11, at the pre-`v0.142.0` docs-cleanup pass)

Six rows moved out of `docs/product/ROADMAP.md`'s Backlog Index, verbatim, content-identical to
their live originals. Criterion applied — per `docs/claude-plans/context-doc-token-reduction-plan.md`
item 6, deliberately deferred at `v0.126.0` as needing per-row judgment rather than a script: a row
qualifies ONLY if (a) it carries no live `[CHECKPOINT — due …]` tag anywhere in the row, (b) it names
no `docs/claude-plans/`, `docs/claude-findings/`, `docs/claude-prompt/*-out/` or `docs/curriculum/`
file that still exists on disk (kickoff step 8's invariant requires those to stay indexed live), and
(c) its Status cell states an unambiguous shipped/resolved/discharged outcome with a specific version
and date, with no "still open", "remains", "needs its own release", or similar unresolved clause
anywhere in the row.

238 candidate rows were read; 14 survived the mechanical filter above; 6 survived a full read of
every cell (the other 8 kept live — reasons: an unfired future re-open condition, a "PARTLY CLOSED"
row genuinely still open with two unfixed legs, an explicit "the open question survives the closure
and is NOT discharged with it" clause, a Gate cell's own trailing text asserting current-tense "live
correctness defect, not a candidate", a row still carrying four binding constraints referenced by
future authoring work, and a row gating a named future decision on family-expansion defaults).

Verified before this move: `grep -c "\[CHECKPOINT — due"` on the live file was identical before and
after (132), confirming no live gate was touched. Every file in the four planning-artifact
directories still has a live row (checked against kickoff step 8's invariant). This is a MOVE, not a
delete — content below is byte-identical to what was removed from the live table.

| Item | Source | Status | Gate (what un-parks it) | Last reviewed |
|---|---|---|---|---|
| **`adoptGoal`'s reparent branch NULLs `targetCompletionDate` — a learner loses their own exam date** | `v0.115.0` Stage 1 adoption audit, 2026-09-05 | **⚠️ SHIPPED — CORRECTED BY THE `v0.138.0` VERIFICATION PASS, 2026-09-09.** Fixed in **`v0.127.0` (`f7269474`, 2026-09-07)**, two days before this release opened. `NoteCollectionService.java:864-883` now **promotes** the learner's date onto the parent before clearing it on the child — earliest date wins, because a completion target is a deadline and the nearest one binds; a parent that already has its own date keeps it. ⚠️ The `setTargetCompletionDate(null)` on the child **stays and is not the defect**: the comment at `:870-874` says so explicitly and warns against "simplifying" it away, because a child still carrying a top-level-only field resurfaces stale data if it is later detached via `updateParent(null)`. **Superseded text kept for the record:** **⚠️ STILL OPEN AND DELIBERATELY NOT FOLDED INTO `v0.116.0` — stated so the omission is a decision, not an oversight.** It sits in the adoption path Slice 2 edits, which is exactly why it looks foldable; but it is a **learner-data-loss defect on a DIFFERENT axis** (a date the learner set for themselves) from Slice 2's contract (upstream additions), and folding it would put a data-loss fix inside a release whose pressure test is aimed at update idempotence. **It is the natural first item of a `v0.116.1` or the next patch.** Found outside the brief's scope. **⚠️ CLOSED — VERIFIED AGAINST CODE 2026-09-07 (`v0.128.0` scan). This row read "OPEN — reachable now" until today and was stale: it SHIPPED as `v0.127.0` item 1.** `NoteCollectionService.java:786-794` now **promotes** the child's date to the parent Goal, earliest date winning — the rule the adoption path already applied, so the defect was that the two paths disagreed. **⚠️ THE NULL ON THE CHILD STAYS AND IS NOT THE DEFECT** (the code says so at `:781`): it stops a nested collection resurfacing a stale top-level date on detach. **⚠️ Do not re-open this as "the NULL is still there" — that is the fix working.** | A learner who sets a target completion date and later adopts a **parent Goal** has that date silently NULLed by the reparent branch. **⚠️ The date is learner-entered and is not recoverable** — nothing else records it. **Gate: `[EFFORT]`, small.** Belongs to no slice; do not fold it into the adoption-update work merely because it is nearby, and do not let it wait on that work either. | 2026-09-09 |
| **~~⚠️ Interview Practice over-attributes ConceptHealth to every source pack — the THIRD live instance of the `v0.104.0` defect~~ — ✅ CLOSED in `v0.107.0` item 1 (2026-09-03)** | Found during the `v0.104.0` diff audit, 2026-09-02, and independently confirmed by that release's pre-signoff cold agent, which also verified **no fourth instance exists** (every `ConceptHealth` write site outside `ConceptHealthService` was enumerated). | **~~OPEN — a LIVE production defect~~ — ✅ SHIPPED in `v0.107.0` as item 1. ⚠️ STATUS CORRECTED AT THE `v0.114.0` KICKOFF, 2026-09-04, AND THE STALENESS IS THE FINDING: this row read *"OPEN — a LIVE production defect"* for TWO RELEASES after the fix landed.** Verified by reading code rather than trusting the row: `InterviewPracticeService:234` now calls `QuizSessionReviewUtils.computeKeyConceptBreakdownBySourceStudyPack`, the per-source helper, and `resolveSourceStudyPackIds` survives ONLY at `:296` behind a stamped-id check whose own comment reads *"Only unstamped pre-release or in-flight items retain the historical broadcast fallback"* — exactly the demotion `v0.107.0` required. **⚠️ THIS IS THE SAME CLASS OF ERROR THAT MIS-SCOPED `v0.107.0` ITEM 3** (a warning row left standing after its obligation was discharged), which is why the correction is written down rather than quietly swapped. **The historical text follows.** `InterviewPracticeService.recordConceptsForSourcePacks:270-298` is **byte-for-byte the same shape** as the Long Exam loop `v0.104.0` replaced: it iterates `resolveSourceStudyPackIds(session)` and writes the **same concept list to every source pack**, filtered only by that pack's own `getKeyConcepts()`. Interview Practice is genuinely multi-source (`InterviewSourceNoteRef`, `additionalNoteIds`), so it over-attributes on every multi-source session **today, and did so before `v0.104.0` existed.** **⚠️ IT PARTIALLY UNDERCUTS `v0.104.0`'s OWN PURPOSE:** the audit's §32 goal reads `ConceptHealth` written by **all** modes, so subject-level reporting stays contaminated — Long Exam and Board Exam are now trustworthy, the store as a whole is not. **⚠️ Deferred for RELEASE SIZE, not because it is blocked:** fixing it needs the same two coupled changes in a third service (stamp its generation path, then aggregate per source), and folding a third service into a release that already changed what production evidence means was judged the worse trade. | **~~[DEFECT]~~ ✅ RESOLVED `v0.107.0`.** Stamped at the generation seam, aggregated per source, `resolveSourceStudyPackIds` demoted to an unstamped-item fallback. **⚠️ Rows written BEFORE the deploy remain over-attributed — no backfill, matching `v0.104.0`'s precedent.** Originally: **⚠️ SCOPED 2026-09-03 after THREE releases of deferral**, folded with slice 5 because slice 5 exists only to consume the evidence this defect corrupts. **⚠️ RE-VERIFIED BY READING CODE AT THAT KICKOFF, AND THE ROW UNDERSTATED HOW CHEAP IT IS — plausibly why it was deferred three times:** `QuizSessionReviewUtils.computeKeyConceptBreakdownBySourceStudyPack` (`:173`) **already exists** and is already used by `LongExamService:508` with identical `keyConcept` semantics, so the fix needs **no new util, no new API, no DTO change and no migration** — one stamp at the `generateQuizForSources:573-604` merge seam plus a near-verbatim port of `LongExamService:550-600`. **⚠️ `resolveSourceStudyPackIds` MUST BE DEMOTED TO FALLBACK-ONLY; left ambiguous, aggregation AND broadcast both run, which is WORSE THAN TODAY and the obvious fixture does not catch it.** **⚠️ AND A CORRECTNESS TRAP THE MULTI-PACK FIXTURE CANNOT CATCH:** Interview Practice calls the **3-arg** `computeFullyCorrectKeyConcepts(quiz, selectedChoices, Map.of())` where that `Map.of()` is `selectedMultiChoices`, while the breakdown helper takes **5** — a wrong mapping silently changes **what counts as correct** while appearing to change only attribution, and both packs move together. **Pre-declared guard: a SINGLE-SOURCE session must write byte-identical `ConceptHealth` before and after.** **⚠️ THE FIX IS PROSPECTIVE — `v0.104.0` shipped NO backfill, so already-written contaminated rows stay contaminated and slice 5 reads them.** | 2026-09-03 |
| **Construction subjects — delete + re-generate under the corrected Domain Context (UNRUN, IRREVERSIBLE at step 4)** | Written by the owner late in the `v0.96.0` cycle; **it arrived with no Backlog row and gained one at the `v0.97.0` kickoff, 2026-08-29 — which is exactly what step 8 exists to catch.** Runnable file: `docs/claude-plans/construction-subjects-domain-context-regeneration.sql`, **committed in that same kickoff commit.** | **RESOLVED 2026-08-30 — RUN, and the runnable file has been deleted as spent.** Step 4 deleted the 23 notes (Cost Engineering 8, Management 7, Scheduling 8) and they were re-generated under the corrected Domain Context. **⚠️ Scope was CORRECTED before running:** `Construction Materials` was in the original list and was removed — it carries `domain_context = NULL` (not `PROFESSIONAL_PRACTICE_AND_REGULATION`) at `BOARD_EXAM_REVIEW`, authored 2026-05-24, a different batch three months before the mistake, so it was never mis-calibrated. Originally: three subjects in the Civil Engineering Review Set were bulk-generated with `domain_context = PROFESSIONAL_PRACTICE_AND_REGULATION` where `ENGINEERING_SCIENCES` was intended. **⚠️ The wrong value reached BOTH generation stages** — the note body (`note-generation-developer.txt`) and the Study Pack (`developer.txt`) — so **per-note Study Pack regeneration CANNOT fix it**: it would re-read the wrong-domain note body. Delete + re-run bulk generation repairs body, title, tags and quiz together. **⚠️ SCOPE WAS ALREADY CORRECTED ONCE BY ITS OWN STEP 1 READ, and the correction must not be undone: `Construction Materials` is NOT affected and is removed from every statement** — it carries `domain_context = NULL` (so its authoring domain fell back to the course program and was never mis-calibrated) and `learner_level = BOARD_EXAM_REVIEW`, authored 2026-05-24, three months before the mistake. **Deleting its 11 notes would be pure loss. Do not re-add it.** | **Owner work, run in order, and steps 0–3 must be SAVED before step 4 — step 4 is irreversible.** **⚠️ TRAP 1: the topic list is not stored anywhere** — `bulk_generation_result` keeps only `failed_topics` and `quota_blocked_topics`, and the curator's typed topic was overwritten by the Study Pack write-back, so **the current note titles are the only surviving proxy**; step 2 exports them and that output must be saved **outside the database** first. **⚠️ TRAP 2: deleting notes does NOT delete the Study Pack** — `fk_study_packs_note_id` is `ON DELETE SET NULL`, not `CASCADE`, so packs first and notes second, the order `NoteService.deleteById:471-473` uses. **⚠️ TRAP 3: `notes.subject` is free text and IS copied onto learner rows.** **⚠️ Read the file's own header before running anything; this row summarises it and does not replace it.** | 2026-08-29 |
| **Catalog Management — no way to add a Course / Program without a migration** | Owner-raised 2026-08-11 from a live production blocker while authoring Civil Engineering notes; audit findings and the rejected alternative are in the `v0.71.2` section in `docs/archive/ROADMAP_ARCHIVE.md` | **SHIPPED in `v0.71.2`.** Admins can add one catalog program from the Admin dashboard or through an explicit confirmed action in the shared Applicable Programs picker. Creation supports an existing family or none, warns on near matches, rejects normalized duplicates with a named 409, and leaves learner free-text authoring unchanged. No seed, migration, rename, delete, or new-family flow shipped. | Release signoff remains; implementation and docs are complete. | 2026-08-11 |
| **Challenge bank orphans on a learner-level correction, and its write path is not actually best-effort** | `v0.70.0` Known Limitations, found by `#986`'s pre-commit audit as an interaction between two items in the same release | **SHIPPED in `v0.81.0`.** `V115` preserves stranded rows and widens the unique key to `(user_id, study_pack_id, question_key, learner_level)` with `UNIQUE NULLS NOT DISTINCT`, so a regenerated question at a corrected depth coexists with its old-level row without opening a nullable-level duplicate loophole. `persistGeneratedQuestions` now writes a single batch inside its warning catch, joining the caller's transaction, so the flush-time constraint violation that previously escaped at commit is absorbed and later rows are still attempted. Reads remain level-scoped by design; this does not deduplicate questions across curriculum levels or delete old rows | none — shipped; Target Audience changes and any depth backfill remain separately gated and out of scope | 2026-08-04 |
| **Note editor silently submits the editing user's own profile Course / Program onto a note whose `course_program` is null** — the field renders empty while a different value is sent | Found 2026-08-03 while scoping `v0.69.0` PR 4, checking whether ADR-001's "clear `course_program` once the level moves out of it" step was safe to execute. Root cause verified by direct code read of `frontend/components/notes/note-editor-page-client.tsx`, not inferred from the feature doc | **~~Open, not scoped~~ — RESOLVED, and it had been resolved for 16 days before anyone noticed.** Fixed by `c34b6116` (2026-08-03), shipped in `v0.69.0`. `note-editor-page-client.tsx` now resolves `isEditMode ? normalizeOptional(draft.courseProgram) : …`, so edit mode reads the note's own value only and a missing one falls through to the validation prompt rather than being silently classified. **⚠️ This row's `Last reviewed` was 2026-08-04 — one day AFTER the fix — and it survived six releases still reading "Open, not scoped".** Recorded because the review ritual bumps `Last reviewed` without re-reading a row's Status against code, so a shipped item can keep advertising itself as a candidate indefinitely. Found 2026-08-19 while picking the next release; it was the leading candidate until the code disproved the row. Original text follows. **Open, not scoped.** `:269` calls `setProfileCourseProgram(me.courseProgram ?? "")` **unconditionally** — the `isEditMode` guard at `:270` gates only the *draft* prefill, not the profile state it reads from. So when editing a note with a null `course_program`, `:592`'s `normalizeOptional(draft.courseProgram) ?? normalizeOptional(profileCourseProgram)` resolves to the **editor's own profile program**, and `:620` writes it into the update payload — while the visible Course / Program input stays empty, because the draft was deliberately not prefilled in edit mode. The user sees a blank field and saves a value they never chose, with no toast and no validation stop (`:611` only fires when the resolved value is *also* empty). Live today for any null-program note; 26 such notes in the local dev dataset, production count not taken. **This is why ADR-001's second corollary defers the clearing step entirely** — PR 4 would have created 38 more of these on official public notes, so the ADR declines to clear rather than making the backfill depend on this being fixed first. The fix is small but carries a judgment call this row does not pre-empt: either drop the profile fallback in edit mode (create-only prefill, which is what `:270` already implies was intended), or keep it and make it honest by prefilling the draft so the submitted value is the displayed one. The second preserves current behavior; the first matches ADR-001's direction of travel, since a profile program silently entering note metadata is precisely the free-text contamination the ADR exists to remove. **RESOLVED 2026-08-03 — owner chose the first: drop the profile fallback in edit mode.** Profile context may pre-fill during creation only; it must not populate or alter metadata when editing an existing note. A note with no course/program now hits the normal validation prompt, which is the intended failure mode ("ask the author to classify the note", not "silently write profile metadata onto the note"). Shipped in `v0.69.0` with a regression test asserting `updateNote` is not called and the profile value is not submitted; verified to fail without the fix. Topic generation still uses the profile program as a *generation input* only, never persisted, and is deliberately unchanged | none — shipped | 2026-08-04 |
