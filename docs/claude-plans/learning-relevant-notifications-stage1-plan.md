# Stage 1 Audit — Learning-Relevant In-App Notifications

**Async Completion (Subproblem A) + Learning Continuity (Subproblem B)**

**Status:** Audit and plan only. Nothing implemented, no migration written, no Codex prompt written.
**Date:** 2026-09-24. Repo state: branch `fix/v0.158.0-run-monthly-zone-pin`; `v0.158.0 — Reading the Evidence` In Progress.
**Production reads:** READ-ONLY `SELECT`s against `notelib-db-prod` (`dpg-d6tvb8fkijhs73fda4m0-a`) per `CLAUDE.md`. No writes executed, and none proposed — this plan needs **no migration at all**.

> **Two findings below invalidate the shape this brief assumes.** If you read nothing else, read **§1.1** (a "your Study Packs are ready" notification is **not implementable** for bulk generation from existing state — the count it would use means something else) and **§7.1** (there is **no** canonical next-action resolver; there are **four**, and they already disagree).

**⚠️ Re-verified 2026-09-24 by the coordinating session, independent of the drafting agent: the five most load-bearing claims (createdCount incrementing past a swallowed dispatch exception; `RetentionEmailType.UNFINISHED_NOTE` declared/configured/zero production usages; the four login/notification production numbers; the 3-modes-vs-1-mode resolver split; the `DUE_CONCEPTS_REVIEW`-only render gate) were each independently re-read against the actual code and a fresh production query — all five matched exactly.** Per the audit's own §13.12 warning, every number is a same-day snapshot and should be re-read again before it reaches a kickoff or a Codex prompt.

*(Durable copy also written to the session scratchpad at `…/scratchpad/audit.md`, 763 lines. Per repo convention this should land in `docs/claude-plans/` with a `ROADMAP.md` Backlog Index row in the same commit — see §12; I did not write into the project, since this is audit-only.)*

---

## 0. Prior art: two documents govern this terrain, and one already rejected both halves

1. **`docs/claude-plans/attention-notifications-email-expansion-stage1.md`** (2026-09-08, 1,252 lines) rejected both halves by name:
   - §21.1: *"Every Study Pack generation, every ordinary self-edit | **Self-caused; the user is already looking at it.**"*
   - §20 Stage H / §21.1: *"**Delayed learning re-engagement** (in-app) | ⚠️ **Rejected on measured evidence, not taste.**"*
2. **`docs/claude-plans/retention-communication-channel-doctrine-final-plan.md` §B** carries a channel-role doctrine that file describes as *"Adopted verbatim as owner-set doctrine, **binding** on this plan and on future retention/communication work until explicitly revised."*

**This audit narrows one and confirms the other:**

- The Study Pack rejection is **amended, not reversed.** It evaluated *single-note, per-generation* work and remains correct there. It never evaluated **bulk**, where the product deliberately redirects the learner *away*. That is the genuine gap — though §1.1 shows it is a **failure-reporting** gap, not a readiness gap.
- The continuity rejection is **confirmed and strengthened**, with an architectural reason the earlier pass did not have (§7.1).

Every claim below is anchored to code I read or a production read taken today.

---

## 1. Executive verdict

### Is expanding notifications for async completion justified?

**YES — narrowly, for batch operations only, and not in the shape the brief expects.** All three single-note flows are rejected. The two bulk flows qualify, but they qualify **differently**, because only one of them can observe what the brief wants to announce.

The concrete, currently-shipped awareness loss that justifies the work:

> **A bulk generation batch's only failure report is a consume-once receipt read by a poller that lives on `/library` for at most ~5 minutes. A learner who navigates away before it settles never learns which topics failed — and the evidence is then deleted by a 24-hour TTL sweep.**

`bulk_generation_result` is written **once**, at batch end, in `NoteBulkGenerationService.processBatch`'s `finally` (`backend/src/main/java/com/studysnap/backend/service/NoteBulkGenerationService.java:247-274`). There is no in-progress row, so the endpoint 404s until the batch ends (`BulkGenerationResultService.java:64`). `consumeResult` **deletes the row in the same transaction as the read** (`BulkGenerationResultService.java:61-68`, delete at `:66`), TTL 24 h (`:22`). The only consumer is the Library poller (`frontend/app/library/page.tsx:861-1009`), bounded by `LIBRARY_GENERATION_POLL_MAX_TICKS = 100` at a 3-second interval — **~5 minutes** (`frontend/lib/study-pack-generation.ts:9,17,19`). The product itself sends the learner to `/library` via a `sessionStorage` flash, then lets them go anywhere.

That is a real NEEDS_ATTENTION signal being silently destroyed today.

**⚠️ And the notification must CARRY the failed topic strings, not link to them — because there is nowhere to link.** A topic that failed at note creation **never became a note**, and the same is true of a quota-blocked topic. So no `notes` row exists for it, no Library filter or route can surface it, and the only record anywhere is `failed_topics` / `quota_blocked_topics` inside the receipt that `consumeResult` deletes (`BulkGenerationResultService.java:66`) or the sweep removes. Server logs are not a learner surface. **A notification that says "open your library to see which" would land the learner on a normal `/library` showing nothing, while the topic strings are gone for good** — shipping a notification whose entire justification is "this information is lost" without carrying that information. The topics go in `body`, copied at fan-out (§5.4, §5.6).

### ⚠️ 1.1 But a "12 Study Packs are ready" notification is NOT implementable for bulk generation, and this is the audit's most important correction

**`bulk_generation_result.created_count` counts NOTE creation, not Study Pack completion.** Traced (and independently re-verified against the code, 2026-09-24):

- `processItem` creates the note, then calls `studyPackService.startAsyncGenerationFromNote(...)` at `NoteBulkGenerationService.java:349-357` inside a `try` whose `catch (RuntimeException)` at `:358-367` **only logs** and still returns the note id at `:368`.
- The caller increments `createdCount` at `:214` regardless.
- Even when dispatch succeeds it is fire-and-forget: each item's own `dispatchAfterCommit` queues a **separate** task onto the same `studyPackGenerationTaskExecutor` that `processBatch` is itself occupying — the documented self-starvation at `config/AppConfig.java:139-149`.

**So at the moment the `finally` runs, the Study Packs are queued, not ready.** A notification saying "12 Study Packs are ready" fired from there would be **false**, and a note whose pack fails minutes later is already counted as created.

**And there is no way to observe true batch readiness from existing state**, because `bulk_generation_result` stores **topics, not note ids** — there is no durable link from a bulk generation batch to the notes it produced (columns verified in production: `subject, course_program, target_profile_type, make_public, requested_count, created_count, failed_topics, quota_blocked_topics, domain_context, learner_level, collection_id, failed_topic_reasons`). Nothing joins a completed pack back to its batch.

**Consequence for the plan:** bulk generation gets a **NEEDS_ATTENTION-only** notification — the half of the receipt that *is* accurate at that moment and the half that is currently lost. It gets **no READY notification**. §5.3 explains why manufacturing one is the job-management platform the spec forbids.

**Bulk regeneration is different and this asymmetry is the plan's core shape.** Its per-item verdict is read from **persisted `notes.status`** via `awaitTerminalStatus` (`NoteBulkRegenerationService.java:520`, impl `:596-610`) — the codebase's own anti-drift rule (*"The item verdict comes from persisted `notes.status`, never from 'the call did not throw'"*, `docs/features/bulk-regeneration.md:50`). So `regenerated` genuinely means the Study Pack was regenerated, and bulk regeneration **can** carry an honest READY notification.

> **One flow observes real completion; the other does not. The plan follows that fact rather than papering over it.**

### Is Learning Continuity justified conceptually?

**Conceptually yes; in this repo, right now, NO — DEFER.** On an architectural finding, not a denominator:

> **There is no canonical "next action" resolver. There are four parallel ones, they already disagree about what "unfinished" means, and a continuity notification would be the fifth.**

`DashboardService.getContinueStudyingRecommendation` sees **3 of 4 modes** (`DashboardService.java:1057-1062`); `DashboardService.getTodayFocus` sees **1** (`:338-344`). Neither sees `LONG_EXAM`; nothing sees `PAUSED`. Two overlapping vocabularies (`ContinueStudyingReason`, 5 values; `TodayFocusType`, 7) both contain a `RESUME_REVIEW` with no mapping between them. Details and the three dead-code consequences in §7.1.

**And the cheaper fix already exists, unshipped:** the backend already computes the exact copy this feature wants — `"You left off on Question N of M in \"<title>\""` at `DashboardService.java:367-368` — and the frontend **throws it away**, because `frontend/app/dashboard/page.tsx:861` renders `TodayFocusCard` only when `type === "DUE_CONCEPTS_REVIEW"`. Rendering already-computed server logic is strictly cheaper than a notification producer, has no staleness, no episode bookkeeping and no badge.

Reinforced by measured reach: **2 users logged in within 48 hours, 6 within 7 days, 43 `LOGIN` events in 30 days — against 407 active accounts** (§7.4).

### Should they ship together or separately?

**Separately. Release A is self-contained and migration-free. Release B is a DEFER with written re-open triggers, not a queued release.** Per spec §19, Release A does not wait.

### One frame for how much to spend

**Measured channel engagement is worse than the figure circulating in the repo, and it decayed within two days of being written down.** 1 read out of **463** delivered notifications; a 406-row announcement fan-out landed 2026-09-22 and after two days **not one recipient has read or dismissed it** (§2.6).

---

## 2. Current notification architecture (repo truth, traced today)

### 2.1 Schema — and why this plan needs no migration

`backend/src/main/resources/db/migration/V139__notifications.sql`:

| Column | Type | Note |
|---|---|---|
| `id` | UUID PK | |
| `recipient_user_id` | UUID NOT NULL | no FK |
| `type` | **VARCHAR(64)** NOT NULL | enum name as a **string** |
| `dedup_key` | VARCHAR(255) NOT NULL | `"<TYPE>:<discriminator>"` |
| `title` | VARCHAR(255) NOT NULL | **copied at fan-out, never re-read** |
| `body` | VARCHAR(1000) | **copied at fan-out** |
| `cta_label` | VARCHAR(64) | rendered since `v0.156.0` |
| `cta_path` | VARCHAR(512) | validated on write, on deliver, and on render |
| `announcement_id` | UUID | provenance + lifecycle only |
| `created_at` / `read_at` / `dismissed_at` | TIMESTAMPTZ | |

Entity: `entity/NotificationEntity.java:21-58`.

**Three indexes, total:** the `V139` UNIQUE `(recipient_user_id, dedup_key)` — which *is* the delivery guarantee — the `V139` `(recipient_user_id, read_at)`, and `V143__notifications_inbox_index.sql`'s partial `(recipient_user_id, created_at DESC) WHERE dismissed_at IS NULL`.

**`type` is a VARCHAR and `NotificationCategory` is never persisted, so adding a type and a category is a Java-only change.** `dedup_key` at 255 chars comfortably holds the longest proposed key (~72).

### 2.2 Delivery — the unique index, not a service check

`service/NotificationService.java:32-60`. `deliver` builds the row, `saveAndFlush`es, and **catches `DataIntegrityViolationException`**, returning the existing row (`:54-59`). A duplicate delivery is a successful no-op. Meters: `notification.delivered` tagged by type (`:52`), `notification.dedup_conflict` (`:55`).

`dedupKey(type, discriminator)` → `type.name() + ":" + discriminator` (`:112-114`); discriminator is a `String` since `v0.134.0`.

**⚠️ `deliver` must never run inside an ambient transaction** — the violation would mark the transaction rollback-only and take the whole fan-out down. **Verified clear for both Release A call sites:** `NoteBulkGenerationService` has **zero** `@Transactional` annotations, and `NoteBulkRegenerationService`'s only mention is its class javadoc at `:67` stating it is deliberately not transactional (JPA's identity map would make `awaitTerminalStatus` spin to timeout, and it must not hold a JDBC connection across an item's two LLM calls). Both `processBatch` methods run on an executor via a lambda, so no proxy applies.

`deliver` also re-validates `ctaPath` (`:46`) — a chokepoint placed there deliberately *for the next producer*, which is this one.

### 2.3 Categories, types, and the badge — and the one structural gap

`entity/NotificationCategory.java:13-39` — **two values, one boolean**: `ANNOUNCEMENT(false)`, `LEARNING_SYSTEM(true)`.
`entity/NotificationType.java:8-9` — **two values**: `ANNOUNCEMENT`, `REVIEW_SET_UPDATE`, each delegating to a category.

**⚠️ THE GAP: one boolean decides TWO independent policies, hard-wired as exact complements.**
- `badgeEligibleCategories()` = flag true (`:29-33`)
- `retentionExpirableCategories()` = flag **`!`**true (`:35-39`)

`test/.../entity/NotificationCategoryTest.java` pins the complement in **four** tests, two with an explicit XOR: `:12-21` (every category in exactly one set), `:53-63` (every type in exactly one set), plus `:36-42`, `:44-51`.

**Consequence:** a class cannot be both badge-eligible **and** retention-expirable. Today's two producers never needed that. **An async-completion notification needs exactly that combination** — a genuine event the learner requested (wants a badge) that is inherently ephemeral (wants to expire). This is the one real infrastructure decision in Release A, and it is the owner's (§9.2).

### 2.4 Queries — badge and inbox move together

`repository/NotificationRepository.java`: `findVisibleInbox` (`:38-58`), `countActionableUnread` (`:76-97`), `findRecipientsWithUndismissedEpisode` (`:107-119`) — **the only existing "already-nudged" suppression primitive in the codebase** — and `deleteExpiredBefore` (`:121-132`), `deleteByRecipientUserId` (`:139`).

The announcement-lifecycle subquery on the badge query is **mirroring, not a live fix**; the doc is explicit that *"a change to either query is a change to both."* Release A's new type carries no `announcement_id`, so the mirror stays inert — worth stating in a prompt so nobody "simplifies" it.

### 2.5 API, polling, interaction — and the verified absence of presence infrastructure

`controller/NotificationController.java`: `GET /notifications?limit=50` (`:27-33`, clamped 1..100 at `NotificationService.java:23,71`), `GET /notifications/unread-count` (`:35-38`), `POST /{id}/read` (`:40-46`), `POST /{id}/dismiss` (`:48-54`). Every read and mutation scopes through `findByIdAndRecipientUserId`; a cross-user attempt is **NOT-FOUND, never forbidden** (`:116-119`).

`NotificationResponse` carries `actionable`, derived **server-side** from the category (`:125`), and deliberately **not** `category`.

**Polling, not streaming.** `frontend/components/app-shell.tsx:26` — `NOTIFICATION_UNREAD_POLL_INTERVAL_MS = 60_000`; interval `:310`, `visibilitychange` `:315`.

**Independently verified — this is load-bearing for §6:** a grep for `WebSocket|SseEmitter|EventSource|text/event-stream|@EnableAsync` across `backend/src/main/java`, `frontend/lib`, `frontend/components`, `frontend/app` returns exactly **one** hit, a *comment* at `app-shell.tsx:278` saying no such infrastructure exists. A grep for `lastSeenAt|last_seen|presence|isOnline` in the backend returns one unrelated hit in `GoogleVisionOcrService`. **There is also no `@Async` anywhere in the backend** — every async path is an explicit `TaskExecutor.execute`.

**One notification = one tappable object** (`components/notifications/notification-inbox.tsx`, 319 lines). The body is the primary activation target; with a safe `ctaPath` it is a real `Link` that marks read → closes the panel → navigates; otherwise a `button` that marks read and leaves the panel open. `ctaLabel` renders as a **non-interactive `<span>` inside** that same element (`v0.156.0`) — never a second interactive element. Dismiss is a sibling `h-11 w-11` button outside the body's hit area. **Release A does not need to touch this file and must not regress it.**

### 2.6 ⚠️ Measured engagement — the circulating figure is stale; report both numbers

`docs/claude-plans/actionable-announcements-campaign-feedback-stage1-plan.md:39` states *"1 of 57 delivered notifications has ever been read (1.8%)"*. Per `CLAUDE.md`'s snapshot rule I re-read it. **It decayed in two days:**

| Type | Delivered | Read | Dismissed | First | Last |
|---|---|---|---|---|---|
| `ANNOUNCEMENT` | **406** | **0** | 0 | 2026-09-22T15:25:47Z | 2026-09-22T15:25:49Z |
| `REVIEW_SET_UPDATE` | **57** | **1** | 0 | 2026-09-10T09:02Z | 2026-09-14T13:41Z |
| **Total** | **463** | **1** | **0** | | |

**Both figures are true and answer different questions:**
- **1.8% (1/57)** — click-through floor for the **badge-eligible** type. The relevant denominator for a badge-eligible completion notification.
- **0.22% (1/463)** — all-types, dragged down by 406 announcement rows that **never badged** by design.

**Neither is an impression rate.** `read_at` is set *only* on explicit body activation, and the panel never marks-all-read on open; there is no inbox-open or row-impression event anywhere in the codebase. The true view-through rate is unmeasured and could be materially higher.

### 2.7 Retention and its documented permanence

`service/jobs/NotificationCleanupJob.java:20` — hourly at :15 (`0 15 * * * *`); window 90 days (`config/StudySnapProperties.java:515-518`).

An **unread, undismissed, actionable** row is **retained indefinitely** — the deliberate `v0.134.0` behaviour after an `EVERYONE` announcement left 396 immortal rows. **Production shows the shape: 56 of 57 `REVIEW_SET_UPDATE` rows are unread, undismissed, and by design never expire.** This is exactly the trap a badge-eligible completion type would fall into under the current single flag.

### 2.8 Existing producers — exactly two, verified by grep

```
grep -rn "NotificationDelivery|notificationService.deliver" backend/src/main/java
→ AnnouncementService.java:229
→ ReviewSetUpdateNotificationService.java:70
```

Nothing else constructs a delivery. Both fan out on `notificationFanOutExecutor` — `config/AppConfig.java:86-94`, **core 1 / max 2**, queue 100, explicit `AbortPolicy` so dispatch can detect saturation. That sizing is a documented hard ceiling after two pool-exhaustion outages.

### 2.9 Tests — what they do and do not establish

| File | Lines |
|---|---|
| `test/.../service/ReviewSetUpdateNotificationIntegrationTest.java` | 431 |
| `test/.../service/NotificationServiceIntegrationTest.java` | 221 |
| `test/.../controller/NotificationControllerTest.java` | 112 |
| `test/.../service/ReviewSetUpdateNotificationServiceTest.java` | 100 |
| `test/.../entity/NotificationCategoryTest.java` | 70 |
| `frontend/components/notifications/notification-inbox.test.tsx`, `frontend/lib/api-notifications.test.ts` | — |

**The real-request rule is already satisfied.** `NotificationControllerTest` uses `MockMvcBuilders.standaloneSetup` and issues real requests with `.contentType(MediaType.APPLICATION_JSON)` (`:31,49-67`) — a real `DispatcherServlet`, so content negotiation executes. Release A adds no endpoint.

**But the suite still cannot tell you a producer works.** The prior audit's §6.1 named this and it holds: assertions are built by calling `deliver(...)` with a hand-built `NotificationDelivery` — `CLAUDE.md`'s named fixture anti-pattern (*"a fixture hand-built a state no code path can produce"*). `ReviewSetUpdateNotificationIntegrationTest` is the counter-example to imitate (§11).

---

## 3. Async workflow inventory

Every dispatch site, grepped rather than assumed:

```
NoteBulkGenerationService.java:159          NoteBulkRegenerationService.java:276
StudyPackGenerationTaskDispatcher.java:18   NoteBulkRegenerationTaskDispatcher.java:26
OfficialChallengeQuizTemplateService.java:407   AnalyticsEventListener.java:43
AnnouncementService.java:163                ReviewSetUpdateNotificationListener.java:28
```

**Executors** (`config/AppConfig.java`): `analyticsTaskExecutor` 1/2/500 (`:61`), `notificationFanOutExecutor` 1/2/100 (`:86`), `studyPackGenerationTaskExecutor` 2/2/100 (`:125`, max==core deliberately, sized against the JDBC pool), `bulkRegenerationTaskExecutor` 2/2/8 (`:163`), `llmParallelTaskExecutor` 4/8/50 (`:177`). All `@Scheduled` jobs share one `TaskScheduler` of **2 threads** (`:41-45`).

**`@Scheduled` jobs** (`service/jobs/`): `GenerationRecoveryJob` (`:18`, `0 */10 * * * *`), `BulkGenerationResultCleanupJob` (`:25`, `0 45 * * * *`, sweeps **both** bulk receipts), `NotificationCleanupJob` (`:20`, `0 15 * * * *`), `AccountPurgeScheduler`, `BillingUsageResetJob`, `SubscriptionExpiryJob`, `SubscriptionExpiryEmailScheduler`, `RetentionEmailScheduler` (daily/weekly/monthly), `LinkedLearnerRequestExpiryJob` + `Worker`.

**Status enums:** `entity/NoteStatus.java:3-7` = `DRAFT, GENERATING, FAILED, GENERATED`. **⚠️ `STUDY_PACK_READY` is a projection label, not a DB value** — `service/NoteStudyPackStatusResolver.java:10-35` maps `NoteStatus` → the four user-facing strings. Production confirms: `notes.status` holds only `GENERATED` (8,667) and `DRAFT` (22), with **zero** `GENERATING` and **zero** `FAILED`. Any prompt grepping for `STUDY_PACK_READY` in SQL will find nothing.

**V136 failure columns have exactly one writer:** `markNoteGenerationFailed` at `StudyPackService.java:1317-1328` (the only three `setGenerationFailure*` calls in the tree, `:1323-1325`). Never cleared on later success; `generation_failed_at` is the disambiguator because a retry bumps `updated_at`.

**⚠️ Two naming corrections to repo docs, both found here:**
1. **`CLAUDE.md` names `NoteService.startAsyncGenerationFromNote()`.** That method does not exist — `NoteService` contains **no async dispatch at all**. The real entry point is **`StudyPackService.startAsyncGenerationFromNote`** (`:195-245`), and the worker is `StudyPackService.generateStudyPackFromExistingNoteAsync` (`:805-989`).
2. Verified negatives for §7: there is **no `TodaysFocus`** (the real name is `TodayFocus`, singular), **no `Coach`/`CoachService` of any kind**, **no `DashboardRecommendationService`**, **no `RecommendationService`**, and no `"continue where you left off"` string anywhere.

### The inventory

| Workflow | Async mechanism | Completion observable? | Existing completion UX | Notification candidate? | Why |
|---|---|---|---|---|---|
| **1. Single Study Pack generation** — `POST /notes/{id}/generate` (`NoteController.java:313-324`) → `StudyPackService.startAsyncGenerationFromNote:195-245` → `dispatchAfterCommit:766-778` → `studyPackGenerationTaskExecutor` | Executor + lambda; worker is **private** so the class-level `@Transactional` at `:78` does not apply | **Yes — the cleanest chokepoint in the repo.** Success funnels through the commit transaction `:860-939` (`markNoteGenerated:892`); failure through `catch` `:960-988` | Frontend redirects to Note Detail, which polls every **3 s** (`frontend/lib/study-pack-generation.ts:9`) | **REJECT** | Fails qualification Q4 outright: the learner is already looking, by product design. Highest-volume action in the product — **2,894 `STUDY_PACK_GENERATED` events, 200 distinct users** (prod). A row per completion is the single change most likely to make the bell an engagement feed. Prior audit §21.1 stands. |
| **2. Single Study Pack regeneration** — `POST /notes/{id}/regenerate` scope `STUDY_PACK` → `NoteController.java:380` calls **the same service method** as #1 | Same | Same chokepoint | Same Note Detail polling | **REJECT** | Not a distinct workflow — literally the same call (`NoteController.java:360-361` says so). |
| **3. Note + Study Pack regeneration** — scope `NOTE_AND_STUDY_PACK` → `startAsyncNoteAndStudyPackRegeneration` (`:265-267`, `:284-356`); two LLM calls (`:823-839`, `:856-859`), then ONE commit | Same executor, same private worker | Yes — one transaction with a status interlock first (`:861-876`) | Same Note Detail polling | **REJECT** | Same surface. Being longer is the only argument, and §6 shows duration does not answer the question. |
| **4. Bulk Study Pack generation** — `POST /notes/bulk-generate` (`NoteController.java:280-290`) → `queueBatch:146-166` → `processBatch:183-284` on `studyPackGenerationTaskExecutor` | Executor + lambda; **zero** `@Transactional` in the class; dispatch is immediate, not after-commit | **Note creation: yes**, at the `finally` `:247-274`. **Study Pack readiness: NO** — see §1.1 | Deliberate redirect away: `sessionStorage` flash → `/library` + toast. Poller ≤~5 min. Failure banner needs the **consume-once** receipt | **✅ SHIP — NEEDS_ATTENTION only** | The lost-failure-report gap is real (§1). A READY notification is **not implementable**: `created_count` counts notes, the pack dispatch exception is swallowed at `:358-367`, and no column links a batch to its notes (§1.1, §5.3). |
| **5. Bulk regeneration** — `POST /notes/bulk-regenerate` (`NoteController.java:268-278`) → `queueBatch:221-279` → `processBatch:397-444` on `bulkRegenerationTaskExecutor` | Executor + lambda; deliberately **not** `@Transactional` (`:66-70`) | **Yes, genuinely** — per-item verdict read from **persisted `notes.status`** via `awaitTerminalStatus:596-610`. Clean completion reaches `:439-443` with `regenerated`/`blocked`/`failed` in hand; the interrupt path `:420-437` **returns without reaching it** | Modal polls while mounted; `batchId` in `sessionStorage` (`bulk-regenerate-modal.tsx:35,44`); receipt is **not** consume-once; `finished` is **derived at read time** (`NoteBulkRegenerationReceiptService.java:78`) | **✅ SHIP — READY + NEEDS_ATTENTION** | Same "learner may leave" property, **and its counts mean what they say.** Denominator caveat in §13. |
| **6. Long Exam generation** — `POST /long-exam/study-packs/{id}/start` → `LongExamService` accept `:255-291`, `afterCommit` dispatch `:293-310`, worker `generateLongExamAsync:315-380` on `studyPackGenerationTaskExecutor`; nested `llmParallelTaskExecutor` fan-out at `:1151-1160` | Executor, **genuinely async** | Yes — `markSessionReady:363` / `failLongExamSession:378` | Learner waits on the exam screen, polling `GET /long-exam/sessions/{id}`; **`useExamFocusMode` hides the whole header and the bell** on that surface | **REJECT** | The learner is watching, **and the bell is not rendered there** — a notification would be invisible when created and stale when seen. (Note: the whole generation runs *inside* the transaction, `:317-375` — the documented reason that pool is pinned at 2.) |
| **7. Board Exam generation** — `ChallengeQuizService` dispatch `:575-595`, worker `generateBoardExamAsync:597-661` | Executor, `afterCommit` | Yes — `markSessionReady:632` / `failBoardExamSession:659` | Same as #6 | **REJECT** | Same reasons. |
| **8. Exam question pool warming** — `ExamQuestionPoolService.generatePoolAsync:147-221`; String statuses `PENDING/GENERATING/READY/FAILED` (`:42-45`) + `generation_status_at` supersession guard (`:194-201`) | Executor | Yes, per pool row (`:204` READY / `:215` FAILED) | **None** — invisible warm-up | **REJECT** | **Fails qualification A: the learner never requested it.** A post-commit side effect of #1 (`StudyPackService.java:956`). Notifying would expose backend machinery — the §5 anti-pattern exactly. |
| **9. Official Challenge Quiz template seed** — `OfficialChallengeQuizTemplateService:405-414` on `llmParallelTaskExecutor`; a rejected seed is logged and silently dropped (`:408-412`) | Executor | Yes | None | **REJECT** | System/curator content work; no addressee. |
| **10. Admin bulk content repair** — `AdminStudyPackService:35-62`, `:64-81`, `CompletableFuture.runAsync` on `llmParallelTaskExecutor`; progress in an **in-memory** `RegenerationProgressTracker` | `CompletableFuture` | Partly (in-memory only) | Admin-only progress UI | **REJECT** | Not learner-facing. |
| **11. Analytics persistence** (`AnalyticsEventListener:43`), **notification fan-out** (`AnnouncementService:163`, `ReviewSetUpdateNotificationListener:28`) | Executors | Logs only | n/a | **REJECT** | Not learner-facing / notifying about notifications. |
| **12. Account data export** — `AccountDataExportService:47` | **None — `@Transactional(readOnly=true)`, synchronous** | n/a | Synchronous download | **REJECT (not async)** | The spec flagged export as plausible. In this repo it is not asynchronous. A genuine candidate **only if** it ever becomes async. |
| **13. Also verified synchronous, not async** — OCR (`StudyPackService.createFromImage:358-421`, `ocrService.extractText` at `:370`), note text extraction, note-generation-from-topic (`POST /notes/generate`), bulk **import** (`POST /notes/import-batch`), interview practice, combined quizzes, flashcards/memorization, Ask Companion, generated quiz, email sending (`ResendEmailService:50`, synchronous on whatever thread calls it) | — | n/a | Inline | **REJECT (not async)** | No deferred completion to announce. |
| **14. Stranded-generation recovery sweep** — `GenerationRecoveryJob:18` → `GenerationRecoveryService.recoverStaleNotes:103-138` → `notes.status = FAILED` with code `GENERATION_INTERRUPTED`, past **`noteBoundMinutes = 120`** (`StudySnapProperties.java:510`) | `@Scheduled` | Yes | **None.** The learner left hours ago | **DEFER on denominator — the one genuinely novel single-note candidate** | The only single-note case where the learner *cannot* be watching. Passes C cleanly. **But production holds zero `GENERATING` and zero `FAILED` notes and only 9 lifetime `generation_failed_at` stamps**, and the feature doc calls the sweep prospective with zero stuck notes found. Re-open on the first non-zero sweep count. |

**Automatic retry, accurately stated.** There is **no** retry framework — no `spring-retry`, no `@Retryable`, no backoff. The **one** automatic retry in the system is a same-call LLM re-ask on invalid output, `MAX_INVALID_OUTPUT_ATTEMPTS = 2` (`service/impl/OpenAiLlmStudyPackService.java:102`, loop `:2564-2575`). It resolves inside a single generation attempt and never produces an observable FAILED→success transition, so the spec's *"do not notify for invisible internal retries that later succeed"* constraint is satisfied **by construction**. Everything else is learner- or curator-initiated. Worth recording so a future change cannot quietly violate it.

### ⚠️ Three counting traps the copy must not walk into

1. **`created_count` over-reports** (§1.1): it counts note creation, with the pack-dispatch exception swallowed at `:358-367`.
2. **Bulk generation's outer catch inflates failures without deflating successes.** `processBatch:232-246` clears `failedTopics`/`failedTopicReasons`/`quotaBlockedTopics` and refills `failedTopics` with **every** topic, while `createdCount` keeps its partial value. So `created = 5`, `failed = 12`, `requested = 12` is reachable — **the two sum to more than requested.** `NoteBulkRegenerationService.java:59-65` names this as a defect it deliberately does not reproduce.
3. **Bulk regeneration's counters can sum to LESS than requested.** `processBatch:413-418` increments only on `REGENERATED`/`BLOCKED`/`FAILED`; the `default ->` branch at `:417` deliberately counts nothing for `NOT_RUN` or a timed-out `RUNNING`.

**Rule this forces:** state only the number that is trustworthy in its own flow, and never a reconciled "N of M". Detail belongs to the receipt, which is the owning feature's job.

---

## 4. Proposed notification classification

| Event | Class | Persist? | Badge? | Destination | Dedup / batch semantics |
|---|---|---|---|---|---|
| **Admin "What's New" announcement** (`AnnouncementService:229`) — existing | **CHANGED** (editorial) | Yes | **No** — `ANNOUNCEMENT(false)` | admin-authored `cta_path`, validated 3× | `ANNOUNCEMENT:<announcementId>`; re-publish is a top-up |
| **Official Review Set update** (`ReviewSetUpdateNotificationService:70`) — existing | **CHANGED** | Yes | **Yes** — `LEARNING_SYSTEM(true)` | `/collections/{adoptedCollectionId}` | `REVIEW_SET_UPDATE:<sourceCollectionId>:<publishedAtEpochMilli>`; behind-episode suppression; curator self-adoption excluded |
| **Bulk generation batch had failures or quota blocks** — NEW | **NEEDS_ATTENTION** | Yes | **Yes** (§9.2 decision) | `/library` (a safe landing page, **not** the information source — the body carries the failed topic strings, because a failed topic never became a note) | `BULK_GENERATION_INCOMPLETE:<resultId>`. **One row per batch. Created only when `failedTopics` or `quotaBlockedTopics` is non-empty — a fully successful batch is SILENT.** |
| **Bulk regeneration batch complete** — NEW | **READY**, or **NEEDS_ATTENTION** when anything did not succeed | Yes | **Yes** (same decision) | `/library` | `BULK_REGENERATION_COMPLETE:<batchId>`. One row per batch. Retry **mints a new `batchId`** (`NoteBulkRegenerationService:217-218`), so a retry is correctly a new event with a new key — no special casing. |
| Bulk generation batch fully succeeded | **REJECT (deliberately silent)** | — | — | — | Readiness is not observable at the chokepoint (§1.1). A "ready" claim would be false; a "queued" claim is backend machinery. *"A frequently empty bell is acceptable."* |
| Single generation / regeneration / combined regeneration complete | **REJECT** | — | — | — | Learner is on Note Detail polling at 3 s. |
| Single-note terminal failure (learner present) | **REJECT** | — | — | — | Note Detail renders `FAILED` + `Retry Generation`. |
| Stranded-generation sweep marked a note `FAILED` | **NEEDS_ATTENTION — DEFER on denominator** | — | — | would be `/notes/{id}` | Re-open on first non-zero sweep count. |
| Exam pool / Long Exam / Board Exam generation | **REJECT** | — | — | — | Not learner-requested (pool), or learner waiting with the bell hidden (exams). |
| Learning continuation / resume | **CONTINUE — DEFER** | — | — | would be `resumeType` + `sessionId` | §7, §8. |
| Weak concept appeared / N concepts due / score changed / readiness changed | **DOES NOT BELONG IN BELL** | — | — | — | Already current state: `dashboard-focus-areas-card.tsx`, `today-focus-card.tsx`. Dashboard owns "what now". |
| Note like / view / copy, new public note in program | **DOES NOT BELONG IN BELL** | — | — | — | Prior audit §21.1; social-engagement loop the product does not want. |

---

## 5. Async completion plan

**One producer, two triggers, no migration, asymmetric by design.**

### 5.1 Trigger 1 — bulk generation, failures only

**Fire inside `NoteBulkGenerationService.processBatch`'s existing `finally` (`:247-274`), after the `recordResult` try/catch and before the terminal `log.info` at `:275`.** Only when `failedTopics` or `quotaBlockedTopics` is non-empty.

Why there: it is the only place where all four outcome lists and `resultId` are simultaneously in scope; it runs on every exit path; it is already outside any transaction. **`recordResult` is already wrapped in its own log-and-swallow `try/catch` at `:266-274` — wrap the notification call the same way.** A notification failure must never affect a batch that already generated notes; the idiom is right there in the block being edited.

**Recipient:** `ownerUserId`. Fan-out is **1**, so no executor and no queue is involved — a genuine simplification over both existing producers.

### 5.2 Trigger 2 — bulk regeneration, full completion

**Fire at the terminal `log.info` in `NoteBulkRegenerationService.processBatch` (`:439-443`).**

**⚠️ Deliberately NOT in a `finally`.** `:64-65` forbids adding a terminal `finally` that touches item state (*"adding one would recreate the defect exactly"*), and the interrupt path at `:420-437` returns early on purpose so a killed driver writes no end marker. **A batch killed mid-flight therefore produces no notification, and that is correct** — the receipt reports `stale`, and saying "your batch is done" when the driver died would be a lie. State this in any prompt, because "wrap it in a finally for robustness" is the obvious wrong instinct.

### 5.3 ⚠️ What it would cost to give bulk generation a READY notification, and why not to

To announce true readiness you need to know when the **last** pack in a batch finished. That requires: (a) a durable batch→note link (a `batch_id` on `notes`, or note ids on the receipt — a migration), (b) a per-generation-completion check asking *"am I the last in my batch?"* inside the single-note success path, which is the hottest path in the product (2,894 events), and (c) a decision about batches where some packs never resolve.

**That is a job-management platform, which the spec forbids and which §18 lists as out of scope.** The smallest honest architecture is therefore **no new architecture**: report what is observable (failures), stay silent on what is not (readiness). The learner already has a live, accurate readiness surface — the Library's own `GENERATING` badges and its poller.

### 5.4 Copy direction — trustworthy numbers only

**Bulk generation (NEEDS_ATTENTION only):**

**The body carries the topic strings themselves** — it is the only surviving record (§1). This is the copy-at-fan-out idiom the repo already uses for announcements, and it makes the row self-contained, which is the only form that outlives the receipt.

| Outcome | Title (direction) | Body (direction) |
|---|---|---|
| Some topics failed | `"3 topics couldn't be generated"` | the topic strings, comma-separated, truncated per the budget below |
| Only quota blocks | `"3 topics need more monthly capacity"` | the topic strings; plus that the others were created |
| Both | `"Some topics couldn't be generated"` | both groups, labelled separately, no counts reconciled |
| Nothing failed | **no notification at all** | — |

**⚠️ The truncation budget is a correctness constraint, not cosmetics.** `body` is `VARCHAR(1000)` (`V139`) and `MAX_TOPIC_LENGTH = 160` (`NoteBulkGenerationService.java:49`) against a 50-topic cap, so the worst case overflows badly. Build the body up to a fixed character budget (**~850 chars, leaving headroom**) and append `"and N more"`. Real topics are note titles and far shorter, so in practice several fit.

**⚠️⚠️ AND AN OVERFLOW WOULD BE MISDIAGNOSED, WHICH IS WHY THE BUDGET MUST BE ENFORCED IN CODE RATHER THAN TRUSTED.** A value exceeding `VARCHAR(1000)` raises `DataIntegrityViolationException` — **the same exception `deliver` catches as a duplicate delivery** (`NotificationService.java:54-59`). It would increment the `notification.dedup_conflict` meter, fail the `findByRecipientUserIdAndDedupKey` lookup, and rethrow via `orElseThrow`. So a too-long body surfaces as a phantom dedup conflict rather than a length error. **A test must pin a 50-topic, max-length-topic batch and assert the persisted body length is within the column bound.**

**⚠️ No upgrade CTA in the notification.** `getUpgradeCtas(currentPlan)` is the single source of upgrade copy (`CLAUDE.md`), and the Library banner already owns it.

**Bulk regeneration (READY / NEEDS_ATTENTION):**

| Outcome | Title (direction) |
|---|---|
| All regenerated | `"12 Study Packs have been updated"` |
| One | `"Your Study Pack has been updated"` |
| Partial | `"10 Study Packs have been updated"` + body naming that some were skipped or failed |
| None | `"We couldn't update your Study Packs"` |

**Forbidden vocabulary, to be carried verbatim into any prompt:** *background job, generation task, queue, batch, worker, dispatch, async, AI generation task, task succeeded, processing complete.* Also forbidden: guilt, urgency, streak language, exclamation marks.

### 5.5 ⚠️ Failure copy must preserve artifact-first semantics — and this is code-verified, not doctrine

A failed regeneration leaves the learner's existing Study Pack **fully intact and usable**. Traced four ways:
1. `saveStudyPack` is an **update-in-place** (`StudyPackService.java:696-704`); there is no delete-then-insert on this path.
2. It is called **only inside** the commit transaction (`:882`), after both LLM calls already succeeded.
3. The failure `catch` (`:960-988`) touches **only** the `notes` row — `markNoteGenerationFailed` writes `notes.status` and the three V136 columns and nothing else (`:1317-1328`). No study-pack write, no `StudyPackStatus.FAILED`, no `errorCode`.
4. Share-link deactivation (`:919`) and both `refreshPool` calls (`:909-912`) are *inside* that transaction, so a failure deactivates nothing and invalidates no pool.

**So the copy must say "couldn't update", never anything implying breakage.** The spec's required distinction — *"we couldn't create the requested result"* vs *"your existing learning material is broken"* — maps onto a verified code property, and the second statement would be **false**.

### 5.6 Retry: promise nothing that will not be there

**⚠️ For bulk generation the retry affordance is gone by the time a notification is read.** `failedTopics` is the retry contract, it lives only in `bulk_generation_result`, and `consumeResult` deletes the row on read (`BulkGenerationResultService.java:66`) with a 24 h TTL behind it. A learner tapping a two-day-old notification lands on a `/library` with **no banner and no `Retry these` button**.

**This is exactly why the body must carry the topic strings** (§1, §5.4). The notification is not a pointer to the receipt — it is the **replacement** for it, and the only record that survives. The learner can re-enter those topics into Bulk Generate manually; that is a worse experience than one-click `Retry these`, and it is infinitely better than the current outcome, which is never learning the topics failed at all.

**What the copy must still NOT do: promise a retry.** No *"try again"*, no *"retry these"*, no CTA implying a one-click path, because that path exists only while the receipt does. Name the topics, say they could not be generated, and stop.

**Do not** make the receipt non-consume-once to serve the notification, and **do not** add a retry endpoint keyed on the notification. Both change another feature's shipped contract to serve awareness, inverting the doctrine. If one-click retry from a notification is ever wanted, that is its own release with its own decision about where the topic list durably lives.

**Bulk regeneration differs:** its receipt is not consume-once, so `POST /notes/bulk-regenerate/{batchId}/retry` genuinely survives 24 h. But the modal seeds `batchId` from **`sessionStorage`** (per-tab), so a fresh tab will not reopen it. Prefer neutral copy for both.

---

## 6. Suppression analysis

### 6.1 Can the architecture know whether the learner is looking? No — and it should not learn how

Verified absences (§2.5): no WebSocket, no SSE, no `@Async`/`@EnableAsync`, no presence or `last_seen` column, no inbox-open or impression event. The only client→server presence signal is the 60-second unread-count poll, which says nothing about which page the learner is on.

Every mechanism that could answer the question is on the spec's own forbidden list. **Reliable presence-based suppression is both expensive and fragile here, and I recommend against it unambiguously.**

### 6.2 The four candidate policies

| Policy | Verdict |
|---|---|
| **Always notify async completion** | **Rejected.** A row behind each of ~2,894 `STUDY_PACK_GENERATED` events across 200 users — the change most likely to make the bell an engagement feed. |
| **Notify only jobs exceeding a duration threshold** | **Rejected.** No start/end pair exists at either bulk chokepoint (`generation_enqueued_at` is per-note, not per-batch), so it needs new state — and **duration does not answer the question.** A learner can sit through a 40-second generation and walk away from a 4-second one. It is a proxy for presence that does not correlate with presence. |
| **Notify only bulk operations** | **✅ RECOMMENDED.** |
| **Create server-side, auto-resolve when the destination is consumed** | **Rejected on doctrine.** An authoritative `resolved` column is forbidden outright, and rows are marked read *"INDIVIDUALLY, only when their body is explicitly activated."* Auto-read on destination visit crosses that line and needs cross-feature plumbing plus a new column or endpoint — the opposite of smallest. |

### 6.3 The recommended policy

> **Scope-based suppression: only batch operations produce completion notifications, and bulk generation produces one only when something failed. No runtime suppression check, no presence signal, no threshold, no auto-resolution.**

It is robust precisely because it is **not a detection mechanism** — it removes the redundant-notification problem *by construction*:

- Single-note flows are where the learner is demonstrably watching (the product redirects them to a polling page). Rejecting them eliminates the entire class with **zero new code**.
- Bulk flows are where the product itself sends the learner away.
- Bulk generation's success half is silent anyway, because readiness is not observable (§1.1) — so the highest-volume redundant case disappears for free.

There is no state to get wrong, no race, nothing to keep in sync.

**Accepted residual, named rather than engineered away:** a learner who stays on `/library` until the poller settles may see both the failure banner and the badge (badge up to 60 s later). One duplicated fact, one session. The spec explicitly warns against building infrastructure "merely to suppress a harmless completion notification", and this is that case. The banner is the live surface; the notification is the durable record.

---

## 7. Continuity feasibility

### 7.1 ⚠️ Canonical destination — there is NO canonical resolver. There are FOUR, and they disagree

This is the finding the spec's §12 asks for, and it is the opposite of what a first pass suggests.

| # | Resolver | Scope of "unfinished" | Vocabulary |
|---|---|---|---|
| **1** | `DashboardService.getContinueStudyingRecommendation` (`:105-178`); 5-tier ladder; unfinished scan `findInProgressSessionsByRecency` (`:1057-1076`) | **3 of 4 modes** — `QUICK_REVIEW, CHALLENGE, ADAPTIVE` (`:1059-1061`), status `IN_PROGRESS` only, **mode priority beats recency** (`IN_PROGRESS_MODE_PRIORITY`, `:1051-1055`) | `ContinueStudyingReason` (5 values) |
| **2** | `DashboardService.getTodayFocus` (`:180-209`); 4-tier ladder; `resolveTodayFocusInProgress` (`:338-399`) | **1 mode** — `QUICK_REVIEW` only (`:339-344`) | `TodayFocusType` (7 values) |
| **3** | `PostSessionNextStepService.getNextStep` (`:88-120`, 646 lines, 17 collaborators) | Post-session only; requires a `studyPackId`; **never emits `RESUME_REVIEW`** | reuses `TodayFocusType` |
| **4** | `DashboardService.buildFocusAreas` (`:848-871`) | partial; its own javadoc `:8-15` disclaims being a ranking model | `DashboardFocusAreasResponse` |

**They are parallel and demonstrably inconsistent:**
- **Two surfaces on the same page disagree about what "unfinished" means.** An in-progress Challenge Quiz appears in Continue Studying and is **invisible** to Today's Focus.
- **Neither covers `LONG_EXAM`. Nothing covers `PAUSED`.**
- **Two overlapping vocabularies**, both containing a `RESUME_REVIEW`, with **no mapping** between them.
- **No shared helper** — each builds its own `determineResumeState` branch; `SUB_MODE_INTERVIEW` is duplicated at `DashboardService.java:74` and `PostSessionNextStepService.java:53`.

> **A continuity notification would therefore be the FIFTH independent next-action computation — the exact architectural anti-pattern the spec §12 forbids — unless Resolver 1's `findInProgressSessionsByRecency` + `resolveResumeType` + `determineResumeState` are first extracted into a shared component. That extraction is a load-bearing prerequisite, and it is not small.**

**Three dead-code consequences, each verified, and the first is the cheap win:**
1. **`frontend/app/dashboard/page.tsx:861` renders `TodayFocusCard` only when `type === "DUE_CONCEPTS_REVIEW"`.** So Resolver 2's `RESUME_REVIEW` and `RETRY_REVIEW` branches are computed on **every** dashboard load and **never rendered** — including the exact copy this feature wants: `"You left off on Question N of M in \"<title>\""` (`DashboardService.java:367-368`) and `"You still have N questions to review in \"<title>\""` (`:383-385`), already shaped as a `title`/`message`/`actionLabel` triple that maps 1:1 onto `notifications.title`/`body`/`cta_label`.
2. `frontend/lib/api.ts:253` declares `LONG_EXAM` as a resume type; `ContinueStudyingResumeType` never emits it → `continue-spotlight.tsx:52-58,120-121` is dead, and Long Exams never appear in Continue Studying.
3. `ContinueStudyingResumeType`'s own javadoc records a shipped bug: Interview Practice shares the `ADAPTIVE` session discriminator, so *"a resume type derived from the session MODE alone cannot distinguish it"* — which once produced a card routing to a page that refused the session. **Any producer re-deriving resume type from `session_mode` reproduces that bug.**

Resolver 1 remains the right thing to build **on**, if this ever ships: `GET /dashboard/continue-studying` returns exactly one destination with `resumeType`, `resumeState`, `sessionId` and progress counters. But **every resolver signature is `(UUID userId, …)` and `DashboardService` is `@Transactional(readOnly = true)` at `:69` — there is no cross-user or batch variant, so a scheduler would loop users**, and there is **no `(user_id, status)` index** on `quick_review_sessions` to make "who has an unfinished session" cheap (table is small today).

### 7.2 Resumable activity — the population barely exists, and the large adjacent state is a trap

All modes share `quick_review_sessions`. `entity/QuickReviewSessionStatus.java:3-9` = `GENERATING, FAILED, IN_PROGRESS, PAUSED, COMPLETED, FORFEITED`.

Production, today:

| Mode | Status | Sessions | Not completed | Stale >48 h | Distinct users stale >48 h |
|---|---|---|---|---|---|
| QUICK_REVIEW | COMPLETED | 358 | 0 | 0 | 0 |
| QUICK_REVIEW | **FORFEITED** | **232** | 232 | 231 | 33 |
| QUICK_REVIEW | **IN_PROGRESS** | **31** | 31 | 30 | 20 |
| CHALLENGE | COMPLETED | 195 | 0 | 0 | 0 |
| CHALLENGE | FORFEITED | 10 | 10 | 10 | 9 |
| CHALLENGE | **IN_PROGRESS** | **6** | 6 | 6 | 6 |
| ADAPTIVE | COMPLETED | 51 | 0 | 0 | 0 |
| ADAPTIVE | **IN_PROGRESS** | **6** | 6 | 6 | 6 |
| ADAPTIVE | FORFEITED / FAILED | 4 / 6 | 2 / 6 | 2 / 6 | 2 / 5 |
| LONG_EXAM | COMPLETED / **IN_PROGRESS** / FORFEITED / FAILED | 2 / **1** / 1 / 1 | | | |
| **PAUSED, any mode** | — | **0** | | | |

1. **`FORFEITED` (244 rows) is a deliberate exit, not a resumable thread** — the state reached through the *Leave Quiz* / *Leave Practice* control that `useExamFocusMode` requires on every focused surface. Resurfacing it would tell a learner to resume what they explicitly abandoned. **It is 5× the resumable population**, so a producer that got this predicate wrong would be 5× too noisy.
2. **`PAUSED` exists in the enum with zero production rows**, and neither dashboard resolver reads it.
3. **The genuinely resumable population is 44 `IN_PROGRESS` rows across ~33 distinct users, 43 of them already stale past 48 hours.** Weekly `IN_PROGRESS` creation over 10 weeks: **7, 3, 4, 2, 4, 0, 1, 2, 0, 1, 1** — a handful a week product-wide, with a backlog that is nearly all months old.

**⚠️ And there is no abandonment timestamp.** `quick_review_sessions` has `created_at` and `completed_at` only — no `started_at`, no `abandoned_at`, no `last_interaction_at`. So **"you left off N hours ago" copy is not derivable today** and would be wrong for any session that spent time in `GENERATING`.

### 7.3 Learning episode — solvable, and one existing half-built concept must be reckoned with first

There is **no** episode concept in the notification model. The nearest primitive is `NotificationRepository.findRecipientsWithUndismissedEpisode` (`:107-119`) — a `dedupKey LIKE '<TYPE>:<id>:%' AND dismissed_at IS NULL` batch query, where **reading does not close the episode; dismissal does.**

**The canonical activity clock is `user_activity_events`, not any `users` column**, and it already encodes the spec's "meaningfully studies":

`entity/ActivityType.java:6-22` — `MEANINGFUL_STUDY_ACTIVITIES` = `{CREATED_STUDY_PACK, STARTED_QUICK_REVIEW, STARTED_ADAPTIVE_PRACTICE, COMPLETED_QUICK_REVIEW, COMPLETED_CHALLENGE_QUIZ, COMPLETED_ADAPTIVE_QUIZ}`. **`OPENED_STUDY_PACK` is deliberately excluded** — merely opening a pack does not reset the inactivity clock. **But there is no `STARTED_CHALLENGE_QUIZ` and nothing for Long Exam at all, so a learner who only takes Long Exams reads as inactive.**

**⚠️ And the repository can only answer a boolean.** `ActivityEventRepository` has `existsByUserIdAndActivityTypeInAndCreatedAtGreaterThanEqual` (`:40-44`) but **no `findTopByUserIdOrderByCreatedAtDesc`** — you can ask *"was this user active since X"*, never *"when was this user last active"*. That shapes any threshold design.

### ⚠️ 7.3a THE FINDING THE OWNER MOST NEEDS: this feature is already declared, already configured, and entirely unbuilt — including its threshold

Verified directly, not taken from a summary:

- **`RetentionEmailType.UNFINISHED_NOTE`** — declared at `backend/src/main/java/com/studysnap/backend/entity/RetentionEmailType.java:9`.
- **`unfinishedNoteDays = 2`** — `config/StudySnapProperties.java:486`.
- **`unfinishedNoteCooldownDays = 3`** — `config/StudySnapProperties.java:487`.
- Both wired with env overrides: `application.yaml:555-556` (`RETENTION_UNFINISHED_NOTE_DAYS`, `RETENTION_UNFINISHED_NOTE_COOLDOWN_DAYS`).
- **Zero production-code usages.** A tree-wide grep for `RetentionEmailType.UNFINISHED_NOTE` returns **only two test references** (`NativeQueryPostgresIntegrationTest.java:262`, `RetentionServiceTest.java:1328`). Nothing reads it, nothing sends it.

**Two things follow, and the second is striking.**

1. **Building a `LEARNING_CONTINUATION` beside a declared-but-unbuilt `UNFINISHED_NOTE` would create two half-features for one job.** Decide what happens to that enum value first — the spec is right that email inactivity and in-app continuity are different jobs, but `UNFINISHED_NOTE` is neither of the four live retention intents; it is *this* job, already named.
2. **⚠️ The threshold this brief asks Claude not to guess is already sitting in config at 2 days — plus a 3-day cooldown — and nothing reads either.** That is a 48-hour threshold with an episode-suppression window beside it, which lands squarely in the spec's own 48–72 h band. **It is not evidence that 48 h is right** (nothing has ever exercised it, so it is a prior guess, not a measurement) — but it does mean an earlier pass already reached the same instinct, encoded it, and never built it. Treat it as a duplicate of the decision, not as its answer.

(Also in the tree, unrelated and inconsistent with each other: `RetentionService`'s `retention.inactivityDays = 3` and `ReEngagementCampaignService.INACTIVITY_DAYS = 30`.)

**If continuity ever ships, the clean episode design needs no new architecture:** the **session is the episode.** Dedup on `LEARNING_CONTINUATION:<sessionId>`, so the existing UNIQUE `(recipient_user_id, dedup_key)` index then gives "at most one per session" for free, a new session is by definition a new episode, and dismissal closes it. No new column, no table, no per-user "last nudged" stamp. Episode identity is **not** the blocker; §7.1 and §7.4 are.

### 7.4 Inactivity threshold — and the number that ends the discussion

`users` has **no `last_active_at`** (`entity/UserEntity.java`, all 13 timestamp columns enumerated). The near misses and why each fails: `last_login_at` (`:178-179`) is an auth event, not study activity; **`last_study_date` (`:169-170`) is a `LocalDate`** — day granularity, and it is the streak field beside `current_streak`/`longest_streak`, so it cannot express "left off 4 hours ago"; `updated_at` is bumped by any profile write.

**⚠️ One stale claim corrected.** `retention-communication-channel-doctrine-final-plan.md:141-152` states *"No dedicated login/session-start `AnalyticsEventType` exists (checked — no match)."* **False.** `AnalyticsEventType.LOGIN` exists at `entity/AnalyticsEventType.java:121` and fires at `service/AuthService.java:894`. It gives a real per-login event stream, improving return attribution beyond what that plan assumed.

And the number:

```sql
SELECT count(*) FROM users WHERE status='ACTIVE';                             -- 407
SELECT count(*) FROM users WHERE last_login_at > now() - interval '48 hours'; --   2
SELECT count(*) FROM users WHERE last_login_at > now() - interval '7 days';   --   6
SELECT count(*) FROM analytics_events WHERE event_type='LOGIN'
  AND created_at > now() - interval '30 days';                                --  43
```

**Two logins in 48 hours. Six in 7 days. Forty-three in 30 days. Against 407 active accounts.**

An in-app notification is visible only to someone who logs in, and every one of those people has, by logging in, already done the thing the nudge exists to cause.

**A threshold cannot be chosen responsibly against this.** The spec is right that 24 h is too aggressive and 48–72 h more plausible; **if continuity ever ships, 72 h is the defensible start** — conservative, past a full weekend, clear of the Monday-evening→Tuesday pattern. Recommending a number now would be fitting a parameter to a population of two.

### 7.5 Already-nudged state — none exists in-app

Nothing beyond the `dedupKey` prefix trick (§7.3). Solvable via the session-scoped key; not present today. `email_log` (+ `clicked_at` from `V149`) and `RetentionEmailScheduler`'s cooldowns are **email** dedup, and the spec is right that the two concepts must not be merged.

### 7.6 Resolution — read and dismiss, and deliberately nothing more

`read_at` is awareness, `dismissed_at` is inbox visibility, both idempotent (`NotificationService.java:77-93`). **There is deliberately no `resolved` column and adding one is forbidden.** Deep links must degrade to a "no longer available" state.

So a continuity notification could be read or dismissed and could degrade gracefully — but **it could not automatically stop being false** when the learner resumes by another route. That is inherent to the current model, not a gap to fill.

**⚠️ One more reason the notification would be less trustworthy than the card:** `QuickReviewSessionService.java:175` persists the client-supplied `sessionState` map verbatim (`:121` nulls it). Anything a notification *copies* from `session_state` for Quick Review is ultimately client-authored **and frozen at write time**, whereas the Dashboard card re-derives it live on every load. Copy-at-fan-out — correct everywhere else — is actively worse here.

---

## 8. Continuity recommendation

> ## **DEFER.**

Not narrowed, not shipped. Six grounds, ordered by strength:

1. **It would be the fifth parallel next-action resolver (§7.1).** Four already exist and disagree about what "unfinished" means — 3 modes vs 1, neither covering `LONG_EXAM`, nothing covering `PAUSED`, two vocabularies with no mapping. Building on that without first extracting a shared resolver is exactly what the spec forbids, and the extraction is a real refactor across `DashboardService` (1,200+ lines), not a prerequisite to wave through.
2. **A cheaper, better fix is already built and unshipped.** The backend computes `"You left off on Question N of M in \"<title>\""` (`DashboardService.java:367-368`) on every dashboard load and the frontend discards it (`page.tsx:861`). Rendering already-computed logic beats a notification on every axis: no staleness, no episode bookkeeping, no badge, no new type.
3. **The channel cannot reach the learner the feature targets.** 2 logins/48 h, 6/7 d, 43/30 d against 407 active accounts (§7.4). An in-app nudge is invisible until the learner has already returned.
4. **Measured channel engagement is 1 read out of 463 delivered**, with a 406-row fan-out two days old and entirely untouched (§2.6).
5. **"Resumable" barely exists, and the large adjacent state is a trap.** 44 `IN_PROGRESS` rows / ~33 users, 43 already stale, 0–7 new per week; `FORFEITED` is 244 deliberate exits, 5× larger. **And no abandonment timestamp exists**, so the natural copy is not derivable (§7.2).
6. **A declared-but-unbuilt `RetentionEmailType.UNFINISHED_NOTE` already occupies this job — with a threshold already in config** (§7.3a): the enum value at `RetentionEmailType.java:9`, `unfinishedNoteDays = 2` and `unfinishedNoteCooldownDays = 3` at `StudySnapProperties.java:486-487`, env-overridable at `application.yaml:555-556`, and **zero production usages**. Building a second half-feature beside it is the wrong first move regardless of channel.

Plus converging prior evidence: the 2026-09-08 audit rejected in-app delayed re-engagement on measured grounds (1,639 `INACTIVITY` sends/30 days to 393 enrolled users), and the owner-adopted doctrine classifies `INACTIVITY` as email-only on structural grounds. This audit reaches the same place independently.

**No threshold, trigger, or episode definition is recommended for implementation.** §7.3 records that the clean design remains available, so nothing is lost by waiting.

**What is worth doing instead, and it is not a notification:** ship the dead `RESUME_REVIEW`/`RETRY_REVIEW` branches of `TodayFocusCard`, and reconcile the two resolvers' definitions of "unfinished". That is a Dashboard scoping question for a future release and should not be framed as notification work.

### Re-open triggers, written so this DEFER is not re-litigated from scratch

- Weekly active learners (distinct `LOGIN` events) reaches a level where in-app reach is material — **the owner must set this number**; it is the one input this audit cannot supply.
- The shared-resolver extraction happens for another reason, removing ground 1.
- The `[CHECKPOINT — due 2026-09-27]` retention read fires its kill criterion (`INACTIVITY` click-through under 1%), removing the channel that owns this job. **Note: that reopens continuity as a question, but not as an in-app one** — grounds 1–3 are independent of email's fate.
- Notification click-through rises materially above the 1.8% badge-eligible floor — which Release A will be the first thing to measure.

---

## 9. Notification infrastructure impact

### 9.1 Schema — **none**

**No Flyway migration.** `notifications.type` is `VARCHAR(64)`; `NotificationCategory` is never persisted; `dedup_key` at 255 holds the longest proposed key (~72). No new column, table, or index. Nothing for the owner to run, nothing to reconcile against production data.

### 9.2 ⚠️ The one real decision: badge eligibility and retention are the same boolean

From §2.3: one flag drives both policies, pinned as complements by four tests including two explicit XORs (`NotificationCategoryTest.java:12-21`, `:53-63`).

A completion notification wants **badge-eligible** (a real event the learner requested) **and retention-expirable** (worthless in 90 days). The current flag cannot express that.

| Option | Cost | Consequence |
|---|---|---|
| **(a) New category, non-badging + expirable** | Zero | Never badges. At 1 read/463 and 43 logins/month, **almost nobody would see it** — cheapest and nearly pointless. |
| **(b) Reuse `LEARNING_SYSTEM(true)`** | Zero | **Recreates the defect `v0.134.0` removed.** Production already shows the shape: 56 of 57 `REVIEW_SET_UPDATE` rows unread, undismissed, never expiring. Every learner who runs a bulk batch and ignores the bell keeps a permanent badge. **Reject.** |
| **(c) ✅ Recommended — decouple into two flags** (`badgeEligible`, `retentionExpirable`), then add `ASYNC_RESULT(badgeEligible = true, retentionExpirable = true)` | Java only, **no migration** (nothing is stored). `retentionExpirableCategories()` becomes its own predicate instead of a complement; the two XOR tests must be **rewritten, not deleted** — per-flag assertions plus an explicit assertion that the new category is in **both** sets | Expresses the real policy. Costs a deliberate change to an invariant whose current comment asserts complementarity. |

**This is an owner decision, not Claude's** — it changes a documented invariant two pressure tests have touched.

**If (c) is taken, three things move together:** the category enum and its comment, the two rewritten partition tests, and `docs/features/notifications.md`'s "Categories, types, and the badge" plus "Retention" sections. Leaving the doc asserting complementarity while the code no longer does is exactly the drift `CLAUDE.md`'s re-read rule exists to catch.

**Reversibility is why (c) is safe:** if the badge proves annoying, flipping `badgeEligible` to `false` is a one-boolean change with **no data migration**, because the flag is derived rather than stored. Badge policy is free to revise; schema is not.

### 9.3 Service, API, indexes, polling, cleanup

- **Service:** one new class + two call-site edits. `NotificationService` itself is **unchanged** — `deliver` already accepts everything needed. **No new `TaskExecutor`:** fan-out is 1 recipient, so no queue is involved.
- **API:** **unchanged.** No endpoint, no DTO field. Existing `standaloneSetup` MockMvc coverage already satisfies the real-request rule (§2.9).
- **Indexes:** **unchanged.** New rows are recipient-scoped and read by `findVisibleInbox`, already covered by `idx_notifications_inbox`. Volume is ~1 row per batch, and bulk generation's is ~1 row per *failed* batch.
- **Polling:** **unchanged.** The badge appears on the next 60-second tick — another reason not to over-engineer suppression.
- **Cleanup:** governed entirely by the §9.2 decision. Under (c), completion rows expire at 90 days regardless of read state.
- **Account erasure:** already handled (`deleteByRecipientUserId` from `AccountPurgeService.deletePersonalRows`). Nothing owed.
- **Metrics:** free. `notification.delivered` is already tagged by type (`NotificationService.java:52`), so the new types are observable on day one — and that counter against `read_at` is how Release A measures its own click-through (§8's last re-open trigger).

---

## 10. Edge cases

| # | Case | Behaviour / required handling |
|---|---|---|
| 1 | **Rapid completion** — a 1-topic batch finishes in seconds while the learner is on `/library` | If nothing failed, **no notification at all** (bulk generation is silent on success). If something failed, learner may see banner and badge. **Accepted, not engineered away** (§6.3). |
| 2 | **Duplicate job callback / double dispatch** | The **UNIQUE `(recipient_user_id, dedup_key)`** index is the guarantee; `deliver` catches `DataIntegrityViolationException` and returns the existing row (`:54-59`). **⚠️ Never add an `existsBy…` pre-check** — two concurrent deliveries can both pass it. Owes an explicit test (§11). |
| 3 | **Retry** | Bulk regeneration retry **mints a new `batchId`** (`NoteBulkRegenerationService:217-218`), so it is correctly a new key and a new notification; `REGENERATED` and `BLOCKED` are never retried (`:180-182`). Bulk generation "retry" is a client re-submission → a new `resultId`. Both correct by construction. |
| 4 | **Failed regeneration with a previous valid Study Pack** | The old pack stays fully usable — verified four ways (§5.5). **Copy must say "couldn't update", never imply breakage.** Pin the copy in a test; this is a wording invariant a future edit can silently break. |
| 5 | **Bulk partial success** | One notification, never two, never per item. **⚠️ Never a reconciled "N of M"** — three counting traps in §3, in two opposite directions. |
| 6 | **User deletes a note before completion** | Bulk regeneration records `NOT_RUN` and **has no FK to `notes`** (`V135`: a cascade *"would erase the very fact the receipt exists to report"*). The batch notification carries no per-note link, so it degrades naturally; `/library` always resolves. |
| 7 | **User navigates directly to the result** | Notification stays unread; under §9.2(c) it expires at 90 days. **No auto-resolution** — forbidden (§6.2). |
| 7b | **A failed topic has no note, so `/library` can show nothing about it** | The body carries the topic strings; `/library` is a safe landing page, not the information source (§1, §5.4). |
| 7c | **Body overflow past `VARCHAR(1000)`** | Enforce the ~850-char budget in code. An overflow is a `DataIntegrityViolationException` that `deliver` misreads as a duplicate (§5.4) — a phantom `notification.dedup_conflict` plus a rethrow, not a clear error. |
| 8 | **Learner resumes before a continuity notification fires** | N/A — deferred. Had it shipped, the session-scoped dedup key handles it: a new session is a new episode. |
| 9 | **Stale continuity destination** | N/A — deferred. Recorded because it is the liability that makes deferral cheap: the model has no resolution concept and adding one is forbidden (§7.6), and copied `session_state` is frozen and client-authored (§7.6). |
| 10 | **Multiple unfinished activities** | N/A for Release A (batch identity is 1:1 with the request). For continuity, Resolver 1 already returns exactly **one** answer — so a notification must never re-derive it (§7.1). |
| 11 | **Batch driver killed mid-flight** (`main` auto-deploys on merge, and neither the generation nor the bulk-regeneration pool drains on shutdown — `AppConfig.java:151-155`) | **Bulk regeneration: no notification** — `:420-437` returns before `:439`. **Bulk generation: the `finally` still fires**, and would report the outer catch's inflated failure list (§3 trap 2). **⚠️ This asymmetry must be stated in any prompt**, or someone will "fix" it by adding the `finally` that `:64-65` forbids. |
| 12 | **Notification delivery itself fails** | Wrap in its own log-and-swallow `try/catch`, mirroring `recordResult`'s block at `:266-274`. A notification failure must never affect a batch that already generated notes. |
| 13 | **`recordResult` itself fails** (already swallowed at `:266-274`, leaving the client a permanent 404) | **This is the strongest single case for the notification**: today that failure is invisible. The notification must be attempted **independently** of whether `recordResult` succeeded — so place it after that block, not inside its `try`. |
| 14 | **Zero accepted topics / an all-quota-blocked batch** | Explicit decision the prompt must make rather than leave to the reader. Precedent: an announcement audience resolving to zero *"publishes successfully and delivers nothing."* |

---

## 11. Tests

### 11.1 The invariant that matters

**Start from the product action, never from `deliver()`.** The existing suite hand-constructs `NotificationDelivery` (§2.9) — `CLAUDE.md`'s named fixture anti-pattern, and the prior audit's §6.1 already ruled that the first real producer owes better. `ReviewSetUpdateNotificationIntegrationTest` (431 lines) is the model.

### 11.2 Backend — required

| Layer | Test | Invariant |
|---|---|---|
| Integration | Queue a bulk generation batch **with a failing topic** → run it → assert **exactly one** row with the expected type, `BULK_GENERATION_INCOMPLETE:<resultId>`, `cta_path = /library` | The producer is reached **from the product action** — the test the substrate has never had. |
| Integration | A **fully successful** bulk generation batch produces **zero** rows | The deliberate silence of §4. Asserting only the positive case would let a "ready" notification creep back in. |
| Integration | Same end-to-end for bulk regeneration, keyed on `batchId` | Second trigger reached. |
| Integration | **A 20-item batch produces exactly ONE row, not 20** | The hard product constraint of spec §6. Assert the **count**. |
| Integration | Invoke the producer twice with the same batch identity → still exactly one row, no exception escapes | **Idempotency is the UNIQUE index, not a service pre-check.** Must fail if someone adds an `existsBy…`. |
| Unit | Copy matrix: failures-only / quota-only / both / regeneration all-succeeded / partial / none / **singular** | Pluralisation and the **artifact-first wording** (§5.5). |
| Unit | **The failed topic STRINGS appear in the body** | The release's whole purpose. A test asserting only the count would pass with the information still lost (§1). |
| Unit/Integration | **50 topics at `MAX_TOPIC_LENGTH` (160) → persisted `body` length ≤ 1000** | An overflow raises `DataIntegrityViolationException`, which `deliver` catches as a **duplicate** and rethrows after a failed lookup — surfacing as a phantom `notification.dedup_conflict` rather than a length error (§5.4). This is the test that makes the budget real. |
| Unit | `created + failed > requested` (the outer-catch shape) yields no contradictory copy | Pins §3 trap 2. **Drive the outer catch to build the fixture — do not hand-set the counts.** `CLAUDE.md`'s guard-must-reach-its-subject rule, and this is exactly its case. |
| Unit | Bulk regeneration where `regenerated + blocked + failed < requested` | Pins §3 trap 3. |
| Unit | **A notification failure does not fail the batch**, and **a `recordResult` failure does not suppress the notification** | Pins edge cases 12 and 13. |
| Unit | An **interrupted** bulk regeneration batch produces **no** notification | Pins edge case 11 and protects `:64-65`. |
| Entity | `NotificationCategoryTest` **rewritten** under §9.2(c): per-flag assertions plus an explicit assertion that `ASYNC_RESULT` is in **both** sets | The whole point of decoupling. Deleting the XOR tests without replacing them silently removes the guard. |
| Retention | An unread `ASYNC_RESULT` row past the window **is deleted**, while an unread `REVIEW_SET_UPDATE` **is retained** | The doc requires the retention test to assert **both directions**; asserting only deletion passes with the retention half broken. |
| Native SQL | Nothing new — no new query. `NativeQueryPostgresIntegrationTest` unchanged | Recorded so nobody assumes harness work. |

### 11.3 Frontend — what is and is not owed

**Nothing is strictly required.** No API change, no DTO field, no route, and `notification-inbox.tsx` needs no edit — a new type renders through the existing generic row and `actionable` is already server-derived.

One cheap addition is worth it: a `notification-inbox.test.tsx` fixture for a completion row with a **safe `ctaPath` and a `ctaLabel`**, asserting one interactive element and that activation marks read → closes → navigates. It guards the `v0.156.0` nesting trap against the first non-announcement producer to carry a CTA. **Keep the existing fixture where `actionable` disagrees with `type`** — one where they merely agree proves nothing.

### 11.4 The free check `CLAUDE.md` demands

Release A changes behaviour inside `NoteBulkGenerationService` and `NoteBulkRegenerationService`. **Both have existing suites, and both must show a diff.** If the release ships with no change to either service's tests, that is the exact signature of the `v0.116.0`/`v0.117.0` silent no-ops — and these call sites are single lines inside long methods, which is precisely where a no-op hides. Any prompt must instruct: *enumerate every file the release ADDED and name those with no test that executes them.*

---

## 12. Release slicing

### Release A — Async Completion Notifications (batch only)

**Ship. Self-contained, no migration, no API change.**

| In | Out |
|---|---|
| One new `NotificationCategory` (`ASYNC_RESULT`) per the §9.2 decision | All three single-note flows |
| Two new `NotificationType` values: `BULK_GENERATION_INCOMPLETE`, `BULK_REGENERATION_COMPLETE` | Any READY notification for bulk generation (§1.1, §5.3) |
| One new producer service; copy per §5.4 | Any per-item notification |
| Call site 1: `NoteBulkGenerationService.java:247-274`, inside the `finally`, **after** the `recordResult` block, **only when something failed** | Any presence, websocket, SSE, telemetry or acknowledgement mechanism |
| Call site 2: `NoteBulkRegenerationService.java:439-443`, **not** in a `finally` | Any change to `bulk_generation_result`'s consume-once contract |
| Destination `/library`; dedup on `resultId` / `batchId` | Any new endpoint, DTO field, index, or migration |
| Tests per §11.2 | `notification-inbox.tsx` changes; any retry promise in the copy |
| Docs: `notifications.md`, `bulk-generation.md`, `bulk-regeneration.md` | Continuity in any form; a batch→note link table |

**Footprint:** ~9–11 files — `NotificationType.java`, `NotificationCategory.java`, the new producer, two call sites, `NotificationCategoryTest.java`, 2–3 new/changed test files, three feature docs.

**Optional narrowing:** ship **bulk regeneration only**. Counter-intuitively this is the *stronger* half on correctness — its counts are trustworthy and it can carry a real READY notification — while bulk generation's contribution is a failure report whose audience is uninstrumented. The counter-argument is that bulk generation's gap is the actual awareness loss that justifies the release. **Recommend both**; the choice is genuinely the owner's.

**Verification tier:** by `CLAUDE.md`'s gate — no permission substrate, no cross-user read, no money/quota/production-data semantics change. **A single `advisor()` call on the diff is the right tier**, plus `/audit-diff` if Codex delivers it. Escalate to one scoped falsification agent only if delivery introduces a defect the same session then fixes.

### Release B — Learning Continuity

**DEFERRED. Not scoped, not sequenced, no branch.** Grounds and re-open triggers in §8.

### Backlog obligations this audit creates

Per `CLAUDE.md` kickoff step 8, **this plan file owes a `ROADMAP.md` Backlog Index row, added in the commit that writes it** — not left for the next kickoff. It should record: Release A scoped, awaiting an owner scope pick; Release B deferred with four re-open triggers; the §9.2 flag decision as the open owner decision; and three doc corrections this audit found that other releases own — `CLAUDE.md`'s `NoteService.startAsyncGenerationFromNote` (does not exist), the retention doctrine plan's `AnalyticsEventType.LOGIN` claim (false), and the three dead-code paths in §7.1.

---

## 13. Risks / anti-patterns

**Ordered by how likely each is to make NoteLib feel notification-driven.**

1. **⚠️ Scope creep from "bulk" back to "every generation." THE primary risk.** Single-note generation is the highest-volume learner action (2,894 events, 200 users). The narrowing in §3 is the entire product argument and will read as an arbitrary omission to anyone who did not read this document. **Carry the reason — "the learner is on a page polling at 3 s" — into the prompt verbatim**, not just the exclusion.
2. **⚠️ A "Study Packs are ready" notification creeping into bulk generation.** It is the brief's own headline copy and it is **false at the only observable moment** (§1.1). This is the finding most likely to be lost in translation between this audit and an implementation.
3. **⚠️ The badge becoming a permanent "you have stuff" light.** §9.2 option (b) recreates `v0.134.0`'s defect, and production already shows its shape. Decoupling is the mitigation; flipping one boolean is the escape hatch.
4. **⚠️ Copy stating a contradiction.** Three counting traps, in two opposite directions (§3). "10 of 12 ready, 12 couldn't be generated" is a reachable output of a naive template and is worse than no notification.
5. **⚠️ Promising a retry that no longer exists** (§5.6). The bulk generation receipt is consume-once with a 24 h TTL; "try again" is false the moment the poller has read it, which is most of the time.
6. **⚠️ Implying a failed regeneration broke existing material.** It does not (§5.5), and the wrong word here damages trust in the learning artifacts themselves — worse than any volume problem.
7. **⚠️ Adding a `finally` to `NoteBulkRegenerationService.processBatch`.** `:64-65` forbids it: it would recreate the outer-catch defect and tell a curator to regenerate notes that already succeeded, spending quota and replacing good content.
8. **⚠️ "Solving" suppression with presence.** Every mechanism that would work is forbidden and none exists here (§2.5).
9. **⚠️ Continuity creeping back as "just reuse the resolver."** There is no single resolver to reuse — there are four, and they disagree (§7.1). Easy to build is not worth building.
10. **⚠️ The bell becoming a second Dashboard.** Weak and due concepts already render as current state. Answer any pressure with the doctrine rather than re-litigating.
11. **Pre-existing risks this release touches but does not cause**, recorded so they are not mistaken for regressions: bulk generation's batch loop **self-starves** the 2-thread generation pool (`AppConfig.java:139-149`); `created_count` over-reports (§1.1); `writeItem` swallows persistence failures (`NoteBulkRegenerationService:704-709`) so an item's real state can vanish from the receipt; nothing sweeps a lost batch of either kind.
12. **⚠️ Reading this document's production numbers as durable.** Every figure is a 2026-09-24 snapshot, and one of them (1.8%) decayed within two days of being written down. **Re-read before any of it reaches a prompt, a kickoff or a release note** — each is a `SELECT` and costs nothing.
13. **Denominator honesty, stated because the audit would be dishonest without it.** `BULK_REGENERATION_STARTED` = **48 events, 1 distinct user** (30 days). **There is no `BULK_GENERATION_*` analytics event at all**, so bulk generation volume is not directly measurable — the proxy is strong (**5,028 notes in 60 days across 65 owners, 46 with ≥20, one with 1,274**), but it is a proxy. Release A is justified on the awareness-loss argument, not on reach, and the owner should choose it knowing that.

---

## 14. Owner checkpoint

LEARNING-RELEVANT NOTIFICATIONS — OWNER CHECKPOINT

Async completion notifications:
SHIP — batch operations only, and asymmetrically: bulk regeneration can carry a READY notification, bulk generation cannot. Every single-note flow is REJECTED.

Single Study Pack generation:
REJECT. `POST /notes/{id}/generate` (`NoteController.java:313-324`) redirects the learner to Note Detail, which polls every 3 s (`frontend/lib/study-pack-generation.ts:9`), so the learner is already looking at the result and qualification question 4 fails. It is also the highest-volume action in the product — 2,894 `STUDY_PACK_GENERATED` events across 200 distinct users — making it the one change most likely to turn the bell into an engagement feed. Confirms the 2026-09-08 audit's §21.1 row.

Study Pack regeneration:
REJECT. Scope `STUDY_PACK` calls the same service method as single generation (`NoteController.java:380`, noted at `:360-361`) — literally the same code path and the same Note Detail polling.

Note + Study Pack regeneration:
REJECT. Same surface, same polling. Being longer (two LLM calls) is the only argument for it, and duration-based gating is rejected in the suppression policy because duration does not correlate with whether the learner is watching.

Bulk generation:
SHIP, but NEEDS_ATTENTION ONLY — a notification when topics failed or were quota-blocked, and SILENCE on a fully successful batch. Its body must CARRY the failed topic strings, not link to them: a topic that failed at note creation never became a note, so no `notes` row exists, no Library route or filter can surface it, and the receipt holding the list is deleted on read or swept at 24 h. The notification body is the only record that survives, which makes it the replacement for the receipt rather than a pointer to it. A "your Study Packs are ready" notification is NOT implementable: `bulk_generation_result.created_count` counts NOTE creation, not Study Pack completion (the pack dispatch exception is swallowed at `NoteBulkGenerationService.java:358-367` while the item is still counted at `:214`), and no column links a batch to the notes it produced, so true batch readiness is unobservable. The justification is the failure half: the receipt is consume-once (`BulkGenerationResultService.java:66`), read only by a poller that lives on `/library` for ~5 minutes, and a learner who leaves never learns which topics failed before a 24 h TTL sweep destroys the evidence. Producer fires in `processBatch`'s existing `finally` (`:247-274`), after the `recordResult` block so that a receipt-write failure — today completely invisible — still yields a notification.

Bulk regeneration:
SHIP — READY plus NEEDS_ATTENTION, as the same producer's second trigger at `NoteBulkRegenerationService.processBatch:439-443`, deliberately NOT in a `finally` (`:64-65` forbids it). Its counts are trustworthy because the per-item verdict is read from persisted `notes.status` via `awaitTerminalStatus` (`:520`, impl `:596-610`), so unlike bulk generation it can honestly say Study Packs were updated. Measured audience is 1 user (48 `BULK_REGENERATION_STARTED` events).

Terminal generation failures:
Fold into the SAME batch notification as the failure variant — never a separate row, never one per failed item. Single-note terminal failures are REJECTED (Note Detail already renders `FAILED` plus `Retry Generation`). One genuinely novel candidate is DEFERRED on denominator: a note swept to `FAILED` by `GenerationRecoveryJob` after `noteBoundMinutes` (default 120, `StudySnapProperties.java:510`), where the learner cannot possibly be watching — but production holds zero `GENERATING` and zero `FAILED` notes and only 9 lifetime `generation_failed_at` stamps. Re-open on the first non-zero sweep count.

Other async candidates discovered:
Exam question pool warming (`ExamQuestionPoolService.generatePoolAsync:147-221`) — REJECT, the learner never requested it. Long Exam generation (`LongExamService.generateLongExamAsync:315-380`) and Board Exam generation (`ChallengeQuizService.generateBoardExamAsync:597-661`) — both genuinely async with real terminal states, but REJECT: the learner is waiting on the exam screen AND `useExamFocusMode` hides the header and the bell on those surfaces, so a notification would be invisible when created and stale when seen. Official Challenge Quiz template seed (`OfficialChallengeQuizTemplateService:405-414`) — REJECT, system content work with no addressee. Admin bulk content repair (`AdminStudyPackService:35-81`, in-memory progress) — REJECT, not learner-facing. Analytics persistence and notification fan-out — REJECT. Account data export (`AccountDataExportService:47`) — NOT ASYNC, synchronous and read-only; a genuine candidate only if it ever becomes async. Also verified synchronous, not async: OCR, note text extraction, note-generation-from-topic, bulk IMPORT, interview practice, combined quizzes, flashcards/memorization, Ask Companion, generated quiz, email sending. The `GenerationRecoveryJob` sweep is the deferred candidate above.

Completion notification suppression:
Scope-based, with no runtime check. Only batch operations produce completion notifications, and bulk generation produces one only when something failed. No presence tracking, no websockets, no SSE, no page-view telemetry, no client acknowledgement, no duration threshold, no auto-resolution. This is the smallest robust policy because it removes redundant notifications BY CONSTRUCTION rather than by detection — there is no state to get wrong, no race, nothing to keep in sync — and because bulk generation's success half is silent anyway, the highest-volume redundant case disappears for free. Reliable presence-based suppression is both expensive and fragile here, and verifiably so: no WebSocket, SSE, `@Async`, presence column or impression event exists anywhere in the repo. Accepted residual: a learner still on `/library` when a batch fails may see both the banner and the badge, the badge up to 60 s later. Accept it; the spec itself warns against building infrastructure to suppress a harmless notification.

Bulk notification semantics:
Exactly ONE notification per batch, never one per item. Dedup keys `BULK_GENERATION_INCOMPLETE:<resultId>` and `BULK_REGENERATION_COMPLETE:<batchId>`; both identities are minted pre-dispatch and already returned to the client. Counts AND the failed topic strings are COPIED into title and body at write time and never re-read, because the receipt they came from is deleted or expires — for bulk generation the body is the only surviving record of which topics failed. The body must be built to a fixed character budget (recommend ~850) then "and N more", because `body` is `VARCHAR(1000)` while 50 topics at `MAX_TOPIC_LENGTH = 160` would overflow it, and an overflow raises the same `DataIntegrityViolationException` that `deliver` treats as a duplicate delivery — so it would surface as a phantom `notification.dedup_conflict` rather than a length error. State ONLY the number that is trustworthy in its own flow and NEVER a reconciled "N of M": bulk generation's outer catch can make created plus failed EXCEED requested (`:232-246`), and bulk regeneration's counters can sum to LESS than requested (`:413-418`). Retry is never promised. An interrupted regeneration batch produces no notification, by design; an interrupted generation batch still fires its `finally`, and that asymmetry must be stated so nobody adds the forbidden `finally`.

Learning Continuity:
DEFER.

Continuity trigger:
None recommended for implementation. If it is ever built, the only defensible trigger is a `quick_review_sessions` row in status `IN_PROGRESS` — explicitly NOT `FORFEITED` (244 rows, a deliberate exit through the Leave Quiz control, and 5× larger than the resumable population) and not `PAUSED` (in the enum, zero production rows, read by neither dashboard resolver).

Continuity threshold:
None recommended now. Choosing a number against 2 logins in 48 hours would be fitting a parameter to a population of two. If continuity ever ships, 72 hours is the defensible starting point — conservative, past a full weekend, clear of the Monday-evening-to-Tuesday pattern the spec names. The spec is right that 24 h is too aggressive; do not implement 48 h or 72 h on the strength of this line. TWO further facts the owner should have before setting any number: (a) no abandonment timestamp exists — `quick_review_sessions` has `created_at` and `completed_at` only, no `started_at`, no `abandoned_at`, no `last_interaction_at` — so "you left off N hours ago" copy is NOT derivable today and would be wrong for any session that spent time in `GENERATING`; and (b) a threshold for this exact job is ALREADY IN CONFIG and unread — `unfinishedNoteDays = 2` with `unfinishedNoteCooldownDays = 3` (`StudySnapProperties.java:486-487`, env-overridable at `application.yaml:555-556`), attached to the declared-but-unbuilt `RetentionEmailType.UNFINISHED_NOTE`. That is a 48-hour threshold with an episode cooldown beside it, inside the spec's own 48-72 h band — but nothing has ever exercised it, so it is a prior guess rather than evidence, and it should be read as a duplicate of this decision, not its answer.

Continuity episode definition:
Not implemented. The clean design, available whenever wanted and needing no new architecture: the SESSION is the episode. Dedup on `LEARNING_CONTINUATION:<sessionId>`, so the existing UNIQUE `(recipient_user_id, dedup_key)` index gives at most one per session for free, a new session is by definition a new episode, and dismissal closes it (matching the Review Set producer, where reading does not close an episode and dismissal does). No new column, no table, no per-user last-nudged stamp. Note that the repo's canonical activity clock is `user_activity_events` with `ActivityType.MEANINGFUL_STUDY_ACTIVITIES` (which already excludes `OPENED_STUDY_PACK`), not any `users` column — but it has no `STARTED_CHALLENGE_QUIZ` and nothing for Long Exam, and `ActivityEventRepository` can only answer the boolean "active since X", never "when last active".

Maximum unresolved continuity notifications:
1 (one), if it is ever built — enforced by the unique index on the session-scoped dedup key rather than by a counter or a persisted budget. No formal notification budget is needed or recommended.

Repeated reminders during same episode:
NO. No day-3/day-6/day-9 escalation, no recurring inactivity campaign, no daily nagging, no streak pressure, no artificial urgency.

Canonical resume destination:
THERE IS NO CANONICAL RESOLVER — and this is the primary reason continuity is deferred. FOUR parallel next-action computations exist and they already disagree: `DashboardService.getContinueStudyingRecommendation:105-178` covers 3 of 4 modes (`:1057-1062`, with mode priority beating recency at `:1051-1055`); `DashboardService.getTodayFocus:180-209` covers 1 (`:339-344`); `PostSessionNextStepService.getNextStep:88-120` is post-session only and never emits `RESUME_REVIEW`; `buildFocusAreas:848-871` is partial and disclaims being a ranking model. Neither dashboard resolver covers `LONG_EXAM`; nothing covers `PAUSED`; two vocabularies (`ContinueStudyingReason`, 5 values; `TodayFocusType`, 7 values) both contain a `RESUME_REVIEW` with no mapping between them. A continuity notification would be the FIFTH unless Resolver 1's `findInProgressSessionsByRecency` plus `resolveResumeType` plus `determineResumeState` are first extracted into a shared component — a load-bearing prerequisite, not a small one. If continuity ever ships, Resolver 1 (`GET /dashboard/continue-studying`) is the one to build on, and a producer must NEVER re-derive resume type from `session_mode`, because Interview Practice shares the `ADAPTIVE` discriminator and that is the documented cause of a card that routed to a page refusing the session. Three dead-code findings fall out of this, and the first is a cheaper win than the whole feature: `frontend/app/dashboard/page.tsx:861` renders `TodayFocusCard` only for `DUE_CONCEPTS_REVIEW`, so the backend computes `RESUME_REVIEW`/`RETRY_REVIEW` on every dashboard load — including the exact copy this feature wants, "You left off on Question N of M in ..." at `DashboardService.java:367-368` — and the frontend throws it away.

Badge eligible:
Batch-completion notifications (READY / NEEDS_ATTENTION) — YES, badge-eligible, and they must ALSO be retention-expirable. CHANGED announcements — NO (unchanged). CHANGED Review Set updates — YES (unchanged). CONTINUE — not applicable, deferred; had it shipped the recommendation would be NOT badge-eligible, because a permanent red badge meaning only "you should study" is the outcome to avoid most. NOTE: the badge-plus-expirable combination the completion class needs CANNOT be expressed today — one boolean on `NotificationCategory` drives both policies as exact complements, pinned by four tests including two explicit XORs (`NotificationCategoryTest.java:12-21,53-63`). See the schema-changes field and owner decision 1.

Current notification infrastructure sufficient:
YES for delivery, dedup, scoping, inbox rendering, polling, CTA handling and account erasure — `NotificationService.deliver` needs no change at all, and fan-out is a single recipient so no executor or queue is involved. ONE real gap: `NotificationCategory` derives badge eligibility and retention expirability from a single boolean as exact complements (`entity/NotificationCategory.java:29-39`), so a class cannot be both badge-eligible and expirable, which is exactly what an ephemeral-but-requested completion event needs. Two lesser gaps, recorded rather than fixed: the substrate's tests build every assertion from a hand-constructed `NotificationDelivery` rather than from a product action (the fixture anti-pattern `CLAUDE.md` names twice), and there is no impression or inbox-open instrumentation, so read-rate is a click-through floor rather than an engagement rate. Separately, the BULK side has a real gap this plan deliberately does not close: no durable link exists from a bulk generation batch to the notes it produced, which is why no READY notification is possible there.

Schema changes:
NONE. No Flyway migration. `notifications.type` is `VARCHAR(64)` so a new type is just a string value; `NotificationCategory` is never persisted; `dedup_key` at `VARCHAR(255)` holds the longest proposed key (~72 chars); no new column, table or index. The only structural change is to two Java enums and, under the recommended option, the derivation of one boolean into two — all of it free of stored data, which is exactly why it is cheap now and cheap to reverse later. Deliberately NOT proposed: a `batch_id` on `notes` or note ids on the receipt, which is what a bulk-generation READY notification would require and which is the job-management platform the brief forbids.

Release A:
Async Completion Notifications, batch only. One new `NotificationCategory` (`ASYNC_RESULT`), two new `NotificationType` values (`BULK_GENERATION_INCOMPLETE`, `BULK_REGENERATION_COMPLETE`), one new producer service, two call sites (`NoteBulkGenerationService.java:247-274` inside the existing `finally` and after the `recordResult` block, firing only when something failed; `NoteBulkRegenerationService.java:439-443`, explicitly NOT in a `finally`), destination `/library`, the copy matrix, the §11 tests, and updates to `notifications.md` plus `bulk-generation.md` plus `bulk-regeneration.md`. About 9-11 files, no migration, no API change, no `notification-inbox.tsx` change. Verification tier: a single `advisor()` call on the diff, plus `/audit-diff` if Codex-delivered — it moves no authorization boundary and no money, quota or production-data semantics.

Release B:
DEFERRED — not scoped, not sequenced, no branch, and nothing to wait for. Re-open on any of: (a) weekly active learners reaching a level where in-app reach is material — the owner must set this number, it is the one input this audit cannot supply; (b) the shared-resolver extraction happening for another reason, which removes the primary ground; (c) the `[CHECKPOINT — due 2026-09-27]` retention read firing its kill criterion of `INACTIVITY` click-through under 1%, which would remove the channel currently owning this job, though the resolver and reach grounds are independent of email's fate; (d) notification click-through rising materially above the 1.8% badge-eligible floor, which Release A will be the first thing to measure.

Codex required for eventual implementation:
YES for Release A. It is backend service logic across about 9-11 files with two enum changes, a new producer, two call sites inside long methods in two different batch drivers, and a rewrite of an invariant test — over both the "more than 5 files / ~100 LOC" and the "new features touching backend service logic" rows of `CLAUDE.md`'s routing table. The tiebreaker also applies, because anti-drift rules must be enforced across files: never pre-check before `deliver`, never call `deliver` inside a transaction, never add a `finally` to the regeneration driver, never state a reconciled N-of-M, never promise a retry, never claim readiness bulk generation cannot observe. Per `CLAUDE.md`, call `advisor()` BEFORE the prompt is written and again on the diff. No prompt is written yet, per this audit's scope.

OWNER DECISIONS STILL REQUIRED:
1. Badge/retention flag decoupling — the only blocking decision. Recommend option (c): split `NotificationCategory`'s single boolean into `badgeEligible` and `retentionExpirable`, add `ASYNC_RESULT(true, true)`, and REWRITE (not delete) the two XOR partition tests. Java-only, no migration, and it deliberately changes a documented invariant, which is why it is yours. Option (a) (non-badging) is free but would make the notification nearly invisible at the measured read rate; option (b) (reuse `LEARNING_SYSTEM`) recreates the immortal-row defect `v0.134.0` removed and should be rejected.
2. Ship both bulk triggers in Release A, or one? Recommend both. Note the tension: bulk regeneration is the stronger half on correctness (trustworthy counts, a real READY notification) while bulk generation is the stronger half on justification (its failure report is the information actually being lost).
3. Failure-copy truncation budget. This is NO LONGER a choice about whether to include the failed topic strings — the audit settled that: a failed topic never became a note, so nothing on any learner surface can show it and the receipt that holds it is deleted, which means the notification body is the ONLY surviving record and must carry the strings. The open question is HOW MANY before truncating. Recommend a ~850-character budget then "and N more", against `body`'s `VARCHAR(1000)` and a worst case of 50 topics at `MAX_TOPIC_LENGTH = 160`. Whatever the number, it must be enforced in code and pinned by a test, because an overflow raises the same `DataIntegrityViolationException` that `deliver` treats as a duplicate delivery and would surface as a phantom dedup conflict. The copy must still promise no retry.
4. Zero-accepted-topic or all-quota-blocked batch: deliver nothing, or deliver the failure form?
5. The weekly-active-learner threshold that would re-open continuity (Release B trigger (a)). This audit cannot supply it.
6. What happens to `RetentionEmailType.UNFINISHED_NOTE` (`RetentionEmailType.java:9`), which is already DECLARED, already CONFIGURED — `unfinishedNoteDays = 2`, `unfinishedNoteCooldownDays = 3` at `StudySnapProperties.java:486-487`, env-overridable at `application.yaml:555-556` — and has ZERO production usages (only two test references). Not part of Release A, but a future `LEARNING_CONTINUATION` built beside it would create two half-features for one job, and its 2-day threshold plus 3-day cooldown is a prior, unexercised answer to the very question this brief asks. Answer this before continuity is re-opened, not after.
7. Not decisions, corrections another release owes: `CLAUDE.md` names `NoteService.startAsyncGenerationFromNote()`, which does not exist (`NoteService` has no async dispatch at all; the real entry point is `StudyPackService.startAsyncGenerationFromNote:195-245`); `retention-communication-channel-doctrine-final-plan.md:141-152` claims no login `AnalyticsEventType` exists, but `AnalyticsEventType.LOGIN` does (`:121`, fired at `AuthService.java:894`); and the three dead-code paths named under "Canonical resume destination" are live defects, not stylistic notes.

DO NOT IMPLEMENT YET.
