# Curator Bulk Regeneration — Stage 1 Architecture & UX Audit

**Status:** STAGE 1 ONLY — no implementation, no migration, no production regeneration.
**Date:** 2026-09-05. **Method:** direct repo audit (frontend/product by the session, backend bulk
infrastructure by a cold agent instructed to read real code). Every claim carries a `file:line`
anchor.
**Depends on:** `docs/claude-plans/note-and-study-pack-regeneration-stage1.md` (Phase 1, owner-ratified).

---

## Final recommendation (§29)

**Yes — curator-selected bulk regeneration is justified and safe to build. It is NOT safe to ship in
the same release as the single-Note primitive, and one reused component must be replaced rather than
inherited.**

Justified: the curator's real workflow is comprehensive Review Sets of dozens to hundreds of Notes.
Regenerating those one at a time is genuinely expensive, and the Library already has the selection
substrate, so the feature is mostly assembly rather than invention.

**Two findings decide the shape:**

1. **⚠️ The existing bulk receipt cannot report a batch this long, and that is arithmetic, not
   opinion.** The client poller gives up after ~5 minutes and then tolerates only ~4 seconds of 404s
   before permanently abandoning the receipt (`lib/study-pack-generation.ts:9,:17,:19,:100-101`;
   `app/library/page.tsx:864-871`), while the receipt row is written **only** in `processBatch`'s
   `finally` (`NoteBulkGenerationService.java:250-265`). Bulk **regeneration** is strictly slower per
   item than bulk generation — **two** LLM calls instead of one — so a 50-Note batch runs well past
   that ceiling. **Reusing the receipt as-is guarantees the curator never learns the outcome of a
   real batch.** §16's suspicion is correct: the ephemeral semantics are insufficient.
2. **⚠️ The batch loop and the per-item Study Pack work share one 2-thread executor, and the loop
   occupies one of those threads for the batch's entire duration.** `StudyPackGenerationTaskDispatcher`
   is qualified to `studyPackGenerationTaskExecutor` (core 2 / max 2 / queue 100,
   `AppConfig.java:75-83`), which is the same pool each item's generation is later dispatched onto
   (`StudyPackService.java:667-669`). This is starvation, not deadlock — but it caps real throughput
   and it is the reason a "hundreds of Notes" batch cannot simply reuse today's dispatch.

**Recommendation: build it as Phase 2, in its own release, after the single-Note primitive has
shipped and run in production.** See §14 for the full reasoning, which is the owner's own ratified
anti-drift rather than caution.

---

## A. Current reusable bulk infrastructure (§28.A)

### What exists

`NoteBulkGenerationService` takes **a list of topic strings and only ever creates Notes**
(`BulkGenerateNotesRequest.topics : List<String>`, `dto/BulkGenerateNotesRequest.java:17`). There is
no path that accepts an existing note id.

Flow: `queueBatch` (`:146-166`) validates, mints a `resultId`, and fires
`taskDispatcher.execute(() -> processBatch(...))`, returning immediately. `processBatch` (`:183-284`)
runs a **sequential `for` loop** with a per-item try/catch, a **500 ms `Thread.sleep` throttle**
between items (`:510-520`, clamped `[0, 5000]`), and writes the receipt in its `finally`.

**Batch cap: 50**, config-backed on both sides — `@Value("${note.bulk-generation.max-topics:50}")`
(`:89`) and `MAX_BULK_GENERATION_TOPICS = 50` (`bulk-generation-page-client.tsx:42`). Neither key is
set in `application.yaml`, so both defaults are live.

**No transaction is held across an LLM call** anywhere on this path — `NoteBulkGenerationService` has
zero `@Transactional` occurrences, and `startAsyncGenerationFromNote` dispatches after commit
(`StudyPackService.java:232`, `:649-662`), with the LLM call at `:688` outside any transaction. That
is the sanctioned two-short-transactions shape and it is directly reusable.

### ⚠️ Three reasons the item-processing half must NOT be reused verbatim

**1. It destroys authored metadata on every item.** `processItem` always passes
`preservedSubject = batch.subject()` (`:350-357`), and subject is required non-blank (`:409-411`), so
`StudyPackService:718` **always** takes the `applyBulkGeneratedMetadataToNote` branch
(`:1107-1116`), which **unconditionally overwrites the note's `title` and `tags`** with LLM output.
On a newly created note that is harmless. **On an existing curator-authored canonical Note it is
exactly what §8 and §25 forbid** — and it would silently rewrite the very titles the canonical-title
doctrine exists to protect. The non-destructive sibling `applyGeneratedMetadataToNote` (`:1090-1105`)
fills only blank fields and is **never reached on this path**.

**2. It resolves ONE context for the whole batch.** `processBatch` builds a single
`StudyPackGenerationContext` (`:192-207`) and passes it as `generationContextOverride`, which makes
`startAsyncGenerationFromNote` **skip per-note resolution** (`StudyPackService.java:205-207`).
Correct for "N topics under one authoring decision"; **structurally wrong for §8**, which requires
each Note to resolve its own Subject, Domain Context, Depth and program independently.

**3. It reports success for items that failed.** `processItem` swallows a
`startAsyncGenerationFromNote` exception (`:358-367`) and still returns `note.id()` (`:368`), so the
item increments `created_count`, never appears in `failed_topics`, and leaves a Note with no Study
Pack. A rate-limit 429 lands here (see §D).

### ⚠️ Correction to one reuse blocker — no new LLM method is needed

The cold agent reported a third blocker: `LlmStudyPackService` has no method that rewrites existing
content. **The code reading is right and the conclusion does not follow.** Phase 1's locked contract
(§5, ratified) is that **the Note's current title is the topic**, and regeneration produces the Note
as if authored today — it does **not** edit the old body. So `generateNoteFromTopic(title, context)`
is precisely the right method, already used by `NoteGenerationService.generateFromTopic:61`.

**Recording this because a later session reading "no rewrite method exists" would otherwise build a
second prompt architecture that Phase 1 anti-drift forbids.**

### Verdict

| Component | Reuse? |
|---|---|
| Async dispatch + `resultId` handle | **Yes** |
| Sequential loop + throttle pacing | **Yes**, but see §D on the executor |
| `BulkGenerationFailureReasonNormalizer` | **Yes** — per-item `(topic, code, reason)` |
| Two-short-transactions discipline | **Yes** |
| In-place Study Pack reuse (`saveStudyPack` → `findByNoteId`) | **Yes** — already correct |
| `GENERATING` stamp + 120-min recovery sweeper | **Yes**, with the refund caveat in §J |
| `processItem` metadata application | **NO — actively destructive** |
| Batch-level context resolution | **NO — must be per-note** |
| Receipt as a completion channel | **NO — cannot deliver at this batch length** |
| Frontend `sessionStorage` progress model | **NO — see §H** |
| `AdminStudyPackService.regenerateOfficialSummaries` | **NO — holds a transaction across the LLM call** (`AdminStudyPackTransactionHelper.java:35,:67`); explicitly not a model to copy |

---

## B. Best entry surface (§28.B, §2)

**The Library selection mode — not the Subject Plan Builder.**

**§2's premise is false: the Builder has no multi-select and no bulk actions.** Its only
`Set<string>` selection belongs to the *Add notes* picker
(`study-plan-builder-page-client.tsx:1016`); remove and move are strictly per-note
(`handleRemoveLeafNote(noteId)` `:1705`).

**The Library already has the whole substrate, and it is already curator-gated:**

- `selectionMode` with a shared selection toolbar, **already parameterized by intent** —
  `type LibrarySelectionIntent = "collection" | "combined-quiz"` (`app/library/page.tsx:82`). A third
  intent is the designed extension point, and the toolbar is documented as *"shared by plan creation
  and teacher exam selection"* (`docs/features/library.md:73`).
- **Select-all across active filters**, resolved server-side via `GET /notes/library/ids`
  (`NoteController.java:608`) with `MAX_LIBRARY_SELECT_ALL_RESULTS = 1000` (`NoteService.java:145`)
  and an explicit truncation toast.
- **Eligibility partitioning at selection time already exists** — non-quiz-ready notes render a
  disabled checkbox with *"Generate a quiz first"* guidance (`library.md:74-75`), and the toolbar
  computes `selectedQuizReadyCount` / `selectedUnresolvedCount` against the selection
  (`page.tsx:1209-1214`) with an aggregate line: *"Only quiz-ready notes will be added to the exam."*
  **That is §6's Ready/Blocked model and §7's aggregate consequence line, already working.**
- Selection mode is **teacher/admin only** (`library.md:71`), matching §1's curator-only scope.

**⚠️ One gap: the Library cannot filter by Review Set membership.** Its filter axes are subject,
course/program and tags (`page.tsx:579-581`) — there is no collection filter. Since the motivating
workflow is rebuilding a *specific* Review Set, **a collection-membership filter is owed as part of
this feature.** It is one filter axis on a surface that already has three, and it is independently
useful.

**Recommendation:** add `"regenerate"` to `LibrarySelectionIntent`, plus a collection filter. Do not
build multi-select into the Builder.

---

## C. Batch orchestration recommendation (§28.C, §9)

**Atomic unit stays one Note + its Study Pack** — Phase 1's commit shape, unchanged: assert both
quotas → resolve context **from that Note** → LLM 1 (content) → LLM 2 (Study Pack) → one commit
writing both artifacts and both usage records.

**Batch policy: continue on failure.** Item 12 failing must not roll back items 1–11 nor stop 13+.
The existing loop already does this per item (`:209-231`); what must change is the outer catch at
`:232-246`, which on any escape **clears the partial lists and marks every topic failed while
`created_count` keeps its partial value** — an internally inconsistent receipt. **A regeneration
batch must never report a successfully regenerated Note as failed**, because the curator's only
remedy is to regenerate it again, spending quota and replacing good content.

**Job state must be durable and resumable.** This is the one genuinely new component (§H).

---

## D. Concurrency recommendation (§28.D, §11, §23, §24)

**⚠️ The self-feeding executor is the blocking scaling finding.** `StudyPackGenerationTaskDispatcher`
(`service/StudyPackGenerationTaskDispatcher.java:12`) is qualified to
`studyPackGenerationTaskExecutor` — **core 2 / max 2 / queue 100** (`AppConfig.java:75-83`) — which is
the same pool each item's Study Pack generation is dispatched onto
(`StudyPackService.java:667-669`). So the batch loop permanently holds **1 of 2** threads for the
whole batch, and a 50-item batch enqueues up to 50 more tasks onto the same 100-deep queue. Two
concurrent batches approach the ceiling.

**No rejection policy is configured anywhere** (`setRejectedExecutionHandler` / `CallerRunsPolicy`:
zero hits repo-wide), so Spring's default `AbortPolicy` applies and a full queue throws
`RejectedExecutionException`.

**⚠️ `studyPackGenerationTaskExecutor` sets neither `setWaitForTasksToCompleteOnShutdown` nor
`setAwaitTerminationSeconds`** (unlike `analyticsTaskExecutor`, `:44-46`). Because `main`
auto-deploys on merge, a deploy mid-batch interrupts the `Thread.sleep`, raises
`IllegalStateException` (`:516-518`), hits the outer catch, and **marks every topic failed including
those already completed.** Routinely reachable.

**⚠️ Do NOT raise the 2/2 bound to fix this.** `AppConfig:52-72` documents that it is justified by
connection-hold duration and that changing it is a `v0.112.0` Phase 3 decision, gated on
`[CHECKPOINT — due 2026-10-04]`.

**Recommendation:** run the batch **driver** on a separate small executor so it does not consume
generation capacity, keep per-item work sequential with the existing throttle, and treat the 2/2 pool
as the fixed generation budget. Do not fan out per Note.

**Per-Note locking (§23, §24): the existing guard already does exactly what is asked.** A Note's
status becomes `GENERATING` at the moment its own regeneration starts and clears on
`GENERATED`/`FAILED`, and `resolveSourceNoteForGeneration` throws **409
`NOTE_GENERATION_IN_PROGRESS`** for a note already `GENERATING` (`StudyPackService.java:628-635`).
That is one canonical per-Note guard covering single-vs-bulk and bulk-vs-bulk. **Do not build a
separate bulk lock.** Locking is naturally at the point an individual Note begins, never batch-wide
— §24 satisfied for free.

**Editing (§24):** Phase 1 blocks `PUT /notes/{id}` while a Note is `GENERATING`. In a batch, only
the Note currently being regenerated is locked; the rest stay editable until their turn. **An edit
landing after preflight but before that Note's turn is a real, narrow race** — see §J row 8.

---

## E. Quota policy audit (§28.E, §12)

**Current enforcement.** The bypass is `enforceLimits`, set purely from role at the controller:
`boolean enforceLimits = user.role() != UserRole.ADMIN;` (`NoteController.java:185`).

| | ADMIN | TEACHER curator | Learner |
|---|---|---|---|
| Batch pre-flight (note quota) | **bypassed** (`:168-181`) | enforced | enforced |
| Per-item note-generation assert + increment | **bypassed** — `generateAdminContent` skips `NoteGenerationService` entirely (`:400-403`) | enforced | enforced |
| Study Pack quota + increment | **bypassed** (`StudyPackService:203-205`, `:611-613`) | enforced | enforced |
| AI rate limit (`"study-pack"`) | **bypassed** (`:206-208`) | enforced | enforced |

**⚠️ Note the asymmetry, which matters here:** the bypass is **ADMIN only**, while authoring
elsewhere uses `CuratorAuthoringPredicate.isCurator` = `TEACHER || ADMIN`. A TEACHER curator
regenerating 50 Notes would spend **50 note-generation units and 50 Study Pack units.**

**§12's explicit question — block, run partially, or require reduction — already has a repo answer:
require reduction.** The bulk form caps input at `min(50, topic notes remaining)`
(`bulk-generation-page-client.tsx:127`) and says *"You have N topic notes left this cycle. Remove X
topics to continue."* (`:312-314`); the backend re-checks and rejects an over-quota batch with **422
before dispatching any work**, with a message stating how many rows to remove
(`docs/features/bulk-generation.md:67`). Study Pack shortfall is the **soft** floor (a confirmation),
note generation the **hard** one.

**Recommendation for v1: reuse this policy verbatim and change no entitlement.** Keep the ADMIN-only
bypass exactly as it is — the motivating workflow (canonical Review Set rebuilding) is ADMIN-owned,
so it is already covered without touching entitlements.

**⚠️ Owner decision owed (§M.1):** whether a **TEACHER curator** may run bulk regeneration under
metered quota, or whether v1 is ADMIN-only. Recommendation: **ADMIN-only in v1** — it needs no quota
decision at all, and extending the bypass to TEACHER would be a real entitlement change that §27
places out of scope.

**⚠️ Pre-existing, named not fixed:** `getNoteGenerationsRemaining` is an unlocked read
(`MePlanService.java:116-122`), so two concurrent batches can both pass pre-flight.

---

## F. Preflight model (§28.F, §6)

**Deterministic states only. No metadata-quality score, no classifier, no AI judgement.**

| State | Deterministic signal | Anchor |
|---|---|---|
| **Blocked — already generating** | `note.status == GENERATING` | `StudyPackService:628-635` |
| **Blocked — multi-program, no Domain Context** | `domainContext == null` AND `note_course_program` count > 1 | `StudyPackGenerationContextResolver.assertGenerationReady:33-39` |
| **Blocked — empty content** (Study Pack scope only) | blank note content | `normalizeRequiredContent`, `NoteService:1410-1418` |
| **Blocked — no course/program resolvable** (learner-owned) | resolver falls through to null | `CourseProgramSelectionRequiredException` |
| **Blocked — over quota** | selection exceeds remaining units | §E |
| **Not eligible — not owned by caller** | `findByIdAndOwnerUserId` misses | `StudyPackService:625-626` |

**⚠️ "Review recommended" is OMITTED.** §6 permits it only on a deterministic, trustworthy signal.
The audit found none: a Note with a `NULL` Domain Context and one program is **fully generation-ready**
(the resolver falls back to the single joined program), so "missing metadata" is not a blocker, and
calling it "review recommended" would require judging metadata quality — which §6 and §27 forbid.
**Omitted deliberately, with the reason recorded.**

**Preflight must be a real backend read, not a client guess.** The Library already resolves selection
ids server-side (`GET /notes/library/ids`), so the natural shape is a companion endpoint that takes
note ids plus the chosen scope and returns per-Note state — reusing exactly the guards above rather
than reimplementing them client-side, which would drift.

---

## G. Public / shared consequence model (§28.G, §13, §14)

Both counts are **cheap and exact**, computed from data already keyed on note id:

- **Public Notes affected** — `count(visibility == PUBLIC)` over the selection. Already on the note
  list payload.
- **Shared quizzes to be deactivated** — one indexed read over `generated_quizzes` /
  `quiz_share_links` for the selected note ids. `generated_quizzes` carries
  `uq_generated_quizzes_note_id` (one row per note), so the count is exact, not an estimate.

**Aggregate at batch level; never render N warning cards.** Per-Note detail lives behind a
disclosure, matching the existing "Only quiz-ready notes will be added" pattern.

> **Regenerate 24 Notes — Notes + Study Packs**
> 19 ready · 3 blocked · 2 already generating
> 16 public Notes will be updated · 7 active shared quizzes will be turned off
> Existing learner copies won't change.

**⚠️ Shared-quiz deactivation applies only to the `Notes + Study Packs` scope**, matching Phase 1:
Study-Pack-only regeneration does not replace the Note content the shared quiz was built from.

---

## H. Receipt / retry architecture (§28.H, §16, §17, §19)

### The existing receipt cannot be reused as the completion channel

`bulk_generation_result` (`V73`, extended by `V74`/`V103`/`V113`/`V119`) is written **once**, in
`processBatch`'s `finally` (`:250-265`) — never updated. It is:

- **consume-once** — `consumeResult` reads under `PESSIMISTIC_WRITE` and **deletes the row**
  (`BulkGenerationResultService:61-68`);
- **24 h TTL** — `deleteExpiredReceipts`, swept hourly at :45
  (`jobs/BulkGenerationResultCleanupJob.java:18`);
- **failure-only** — it stores `failed_topics`, `failed_topic_reasons`, `quota_blocked_topics` and an
  integer `created_count`. **`createdNoteIds` is accumulated in memory and never persisted** (`:188`,
  `:213`, used only at `:248`), so the receipt cannot say *which* items succeeded;
- **keyed by topic string**, with the frontend deduping "last wins" (`page.tsx:1455-1457`).

**⚠️ And it is undeliverable at this batch length.** The client poller stops after ~5 min
(`study-pack-generation.ts:19`) and then tolerates ~4 s of 404s (`:100-101`) before abandoning the
`resultId` (`page.tsx:867-869`). Bulk regeneration runs **two** LLM calls per item plus the 500 ms
throttle, so a 50-Note batch is far past 5 minutes. **The row is written only at the end — so the
batch completes and the receipt is never read.** Additionally, **nothing sweeps a lost batch**: a
`processBatch` thread killed mid-loop never reaches its `finally`, so no row is ever written at all.

### Recommendation — the one genuinely new component

**A per-item job record, written as each item resolves**, not a terminal blob:
`(batch_id, note_id, scope, state, reason, share_link_deactivated, updated_at)` with `state ∈
{PENDING, RUNNING, REGENERATED, BLOCKED, FAILED, NOT_RUN}`.

This is the smallest thing that satisfies §16, §17 and §19 **simultaneously**, and each is otherwise
unachievable:

- **§19 progress** becomes a real read (`12 of 24 completed`) instead of inferring from note status;
- **§19 navigate-away** works, because state is server-side rather than in `sessionStorage`;
- **§17 retry** becomes "re-run the items whose state is `FAILED`", by **note id** — no stash, no
  re-submitted form, and successful items are structurally excluded;
- **§16 per-Note outcomes** are available, including which share links were deactivated;
- a killed batch is **recoverable**, because items already resolved are already recorded.

**Reuse `BulkGenerationFailureReasonNormalizer`** (`(topic, code, reason)` → keyed by note id
instead of topic string, which also fixes the duplicate-topic collision).

**⚠️ This is the one place a migration is owed**, and it is why bulk cannot be a thin wrapper.

**Do not build permanent audit history** (§16): apply the same 24 h TTL and the same
`AccountPurgeService` deletion the existing receipt already has.

### Retry (§17)

**Retry failed items only, by note id.** Never re-run `REGENERATED` items — that would spend quota
and replace good content. Do not auto-retry. `BLOCKED` items are not retried until their blocking
condition changes; surface the reason so the curator can fix it (e.g. set a Domain Context) and
re-select deliberately.

**⚠️ Two defects in the existing retry not to reproduce:** `quotaBlockedTopics` are silently dropped
from the retry stash (`page.tsx:1439` passes only `failedTopics`), and `sectionLabel` is dropped
because the receipt has no such column — the same class of bug the `collectionId` comment at
`BulkGenerationResultEntity:49-53` records as already fixed once.

---

## I. Mobile UX (§28.I, §20)

**Selection → preflight → progress → result, all on existing primitives.**

- **Selection** reuses the Library toolbar, which already stacks (`flex flex-col gap-3 sm:flex-row`).
  **⚠️ One real ergonomic finding: the note checkbox is a bare 16×16 px input** (`h-4 w-4`,
  `page.tsx:2026-2032`) rendered as card trailing content, well under a comfortable tap target. Fine
  for occasional exam selection; **poor for a workflow whose premise is selecting dozens of Notes on
  mobile.** Recommend enlarging the hit area (padded wrapper) rather than redesigning the card.
- **Preflight** is an `AppModal`, which already renders as a **bottom sheet on mobile**
  (`app-modal.tsx:163`) with `max-h-[85dvh]`, a scrollable body and a non-scrolling header. Show the
  aggregate summary only; blocked items behind a disclosure. **No per-Note table on mobile.**
- **Scope selection** reuses Phase 1's two-card selector, stacked vertically, with single-choice
  semantics.
- **Progress** is counts, not a table: `12 of 24 · 1 failed · 2 skipped`.
- **Result** is the same counts plus a disclosure listing failed/blocked items with reasons and a
  single **Retry failed** action.

---

## J. Failure-state matrix (§22)

Per-Note state assumes Phase 1's atomic commit (nothing partial is ever written for one Note).

| # | Failure | Per-Note persisted | Batch state | Quota | Retry |
|---|---|---|---|---|---|
| 1 | Note generation fails | unchanged — old Note + old pack | continues | none charged | retryable |
| 2 | Study Pack generation fails | unchanged — old Note + old pack | continues | none charged | retryable |
| 3 | Commit fails | unchanged (rollback) | continues | none charged | retryable |
| 4 | Quota exhausted mid-batch | remaining items `BLOCKED` | continues, then completes | spent units stand | after reset/upgrade |
| 5 | **App instance restarts** | completed Notes stand; in-flight Note left `GENERATING` | **batch stops; items recorded so far survive** | in-flight charges nothing | resume from `PENDING` |
| 6 | Client navigates away / closes tab | unaffected | **continues server-side** | unchanged | n/a |
| 7 | Note deleted mid-batch | skipped | continues | none | `NOT_RUN` |
| 8 | **Note edited after preflight, before its turn** | regenerated from the **edited** content's metadata | continues | charged | n/a — see below |
| 9 | Another regeneration starts on the same Note | **409 `NOTE_GENERATION_IN_PROGRESS`** | continues | none | retryable |
| 10 | Share-link deactivation fails | Note + pack committed; link **still live** | continues | charged | **must be recorded, not swallowed** |
| 11 | Receipt/job persistence fails | Note + pack committed | continues | charged | item shows as unknown |
| 12 | Visibility changed mid-batch | regenerated; new visibility respected | continues | charged | n/a |

**⚠️ Row 5 is the reason §H's per-item record is not optional.** Today a restart mid-batch writes
**no receipt at all** and leaves the curator with no record of what completed. Recovery of the
stranded Note itself is covered — `GenerationRecoveryService` sweeps notes `GENERATING` for >120 min
by `generation_enqueued_at` — **but it does not refund note-generation quota** (unlike the Long Exam
and Board Exam writers, `GenerationRecoveryRowWriter:122-155`).

**⚠️ Row 8 is a genuine narrow race and must be stated.** Phase 1 locks a Note only when *its own*
regeneration begins, so an edit between preflight and that Note's turn succeeds. The outcome is not
corruption — the Note is regenerated from its current title and metadata — but **preflight's "ready"
verdict can be stale**. Recommendation: **re-run the per-Note guards at the moment each item starts**,
never trust the preflight snapshot; treat a now-blocked Note as `BLOCKED` rather than forcing it.

**⚠️ Row 10 must not follow the existing swallow pattern.** `processItem` currently swallows a failed
`startAsyncGenerationFromNote` and still reports success (`:358-368`). A failed share-link
deactivation leaves a live link serving replaced content — **record it as a per-item warning.**

---

## K. Large-batch limits (§28.K, §21)

**A cap is needed, and it does not have to be invented — two already exist:**

- **50 per batch**, config-backed on both sides (`note.bulk-generation.max-topics:50`,
  `MAX_BULK_GENERATION_TOPICS = 50`);
- **1,000 ids** for select-all-across-filters (`MAX_LIBRARY_SELECT_ALL_RESULTS = 1000`).

**Recommendation: keep 50 for v1**, config-backed under its own key so it can be tuned
independently of bulk *generation*. Derived from infrastructure, not taste: the generation budget is
a **2-thread** pool the batch driver itself draws from, each regeneration item is **two** LLM calls,
and the 500 ms throttle applies between items. A 50-Note batch is already a multi-minute operation.

**§5 is therefore answered: no one-click whole-Review-Set regeneration.** A 500-Note set exceeds the
cap by 10×, which is the correct outcome — the curator regenerates in deliberate passes. Select-all
should select **up to the cap within the active filters**, with an explicit truncation notice
(precedent: the existing select-all truncation toast).

---

## L. Implementation slices (§28.L)

| Slice | Content | Depends on | Route |
|---|---|---|---|
| **B1** | Per-item batch job record + migration; batch driver on its own executor; per-Note guards re-run at item start; continue-on-failure; fix the outer catch so completed items are never reported failed | Phase 1 shipped | **Codex** |
| **B2** | Preflight endpoint: per-Note deterministic states + aggregate public / shared-quiz counts | B1 | **Codex** |
| **B3** | Library `"regenerate"` selection intent + collection-membership filter + enlarged mobile tap target | B2 | Claude Code inline |
| **B4** | Preflight/confirmation modal (scope selector, aggregate consequences, blocked disclosure) | B3 | Claude Code inline |
| **B5** | Progress + result receipt + retry-failed | B1, B4 | Claude Code inline |

**⚠️ B1 owes the only migration in this feature.** If any other slice appears to need one, the scope
is wrong.

**Release boundary:** bulk regeneration must not be reachable until B3–B5 land. B1 and B2 are
inert without an entry point.

---

## M. Genuine owner decisions (§28.M)

1. **Who may run bulk regeneration in v1 — ADMIN only, or TEACHER curators too?**
   **Recommendation: ADMIN only.** The existing bypass is already ADMIN-only
   (`NoteController.java:185`), the motivating canonical Review Sets are ADMIN-owned, and this
   requires **no entitlement or quota change at all**. Extending to TEACHER means either metering
   them (50 notes = 50 + 50 units, likely exceeding their monthly allowance) or widening the bypass,
   which §27 places out of scope.
2. **Batch cap for regeneration — reuse 50, or a different config key?**
   **Recommendation: 50 under its own key**, so tuning it does not move bulk *generation*.
3. **Cancellation (§18) — build it or not?**
   **Recommendation: not in v1, and say so honestly.** Already-dispatched LLM calls cannot be
   stopped; the only truthful meaning is *stop dispatching not-yet-started items*. With a per-item
   job record that is nearly free (mark `PENDING` items `NOT_RUN`), but it is a small addition on top
   of clear progress plus partial completion, which is sufficient. **Do not offer a "Cancel batch"
   control that implies in-flight work stops.**

**Settled by the audit and NOT owner decisions:** entry surface (§B), quota policy for those in scope
(§E — reuse block-and-reduce), preflight states (§F — "review recommended" omitted for lack of a
deterministic signal), locking (§D — the existing per-Note guard), batch cap (§K).

---

## §14. Release sequencing — why this should NOT ship with Phase 1

The owner asked to ship both in one release and invited a challenge. **Recommendation: two releases,
adjacent.**

1. **The owner's own ratified anti-drift already says so.** Phase 1 §21 states a bulk feature "may be
   audited and built later **on top of the verified single-Note primitive**." Shipping together means
   bulk rides an **unverified** primitive.
2. **Blast radius is asymmetric, and that is the decisive argument.** A defect in single-Note
   regeneration damages one Note per invocation, in front of a curator who chose that Note. The same
   defect under a batch damages up to 50 canonical Notes before anyone looks — and **regeneration is
   irreversible**: there is no Study Pack versioning and the previous Note content is not retained.
3. **Verification cost compounds past the stated threshold.** Phase 1 is 5 slices and fires the
   money-semantics trigger on its own (one operation, two meters) → one scoped cold agent. Bulk adds
   a migration, batch orchestration, a preflight contract, a receipt and retry — a second independent
   trigger class. Combined, this plausibly reaches the full three-agent test that `CLAUDE.md` rates as
   "one release in four or five."
4. **Bulk's own scope includes fixing pre-existing defects in shared code** — the outer-catch
   mislabelling, the swallowed per-item failure, the undeliverable receipt. Those edits touch the path
   bulk *generation* uses today, so they want their own verification rather than sharing a diff with a
   brand-new primitive.

**The cost of splitting is small and worth naming honestly:** the curator waits one release for batch
relief. But Phase 1 alone already removes the worst of today's workflow — regenerating a canonical
Note in place instead of delete-and-recreate — so the interim is "slower than ideal", not "blocked".

**Recommended sequence:** Phase 1 ships and deploys → its behaviour is observed on real canonical
Notes → Phase 2 opens, with B1's job record informed by what Phase 1 actually did.

---

## Anti-drift checklist

- **⚠️ Do NOT reuse `applyBulkGeneratedMetadataToNote`** — it unconditionally overwrites `title` and
  `tags` and would destroy curator-authored canonical titles (§A).
- **⚠️ Do NOT resolve one generation context for the batch.** Every Note resolves independently (§8).
- **⚠️ Do NOT infer or repair missing metadata to make a Note pass.** Block it with a reason (§8).
- **⚠️ Do NOT build a metadata-quality score, classifier, or AI "is this good enough" judgement** (§6).
- **⚠️ Do NOT build a second Note generation architecture** — the title-as-topic path is the method;
  no "rewrite existing content" LLM call is needed (§A correction).
- **⚠️ Do NOT send Applicable Programs to generation, or display them as generation inputs** (§7).
- **⚠️ Do NOT change Review Set membership, Section, ordering, Subject, Domain Context, Depth or
  Applicable Programs** — bulk changes content only (§25).
- **⚠️ Do NOT propagate to learner copies, or notify copy owners** (§15).
- **⚠️ Do NOT raise `studyPackGenerationTaskExecutor`'s 2/2 bound** — `v0.112.0` Phase 3 decision,
  gated on `[CHECKPOINT — due 2026-10-04]` (§D).
- **⚠️ Do NOT copy `AdminStudyPackService.regenerateOfficialSummaries`** — it holds a transaction
  across an LLM call (§A).
- **⚠️ Do NOT hold a JDBC connection across either LLM call.**
- **⚠️ Do NOT auto-select, auto-detect stale Notes, schedule regeneration, or regenerate on deploy,
  metadata edit, or Review Set update** — selection stays explicitly human (§4).
- **⚠️ Do NOT offer whole-Review-Set one-click regeneration** (§5, §K).
- **⚠️ Do NOT re-run successful items on retry** (§17).
- **⚠️ Do NOT promise "Cancel batch" if in-flight LLM work cannot stop** (§18).
- **⚠️ No pricing change, no new plan gate, no learner bulk regeneration, no content versioning, no
  concept migration, no adopted-Review-Set synchronization** (§27).
- **⚠️ Only B1 may add a migration.**

---

## Verification strategy

**Tier: ONE SCOPED COLD AGENT framed as falsification**, on B1+B2 — the trigger is **production-data
semantics at scale** (irreversible content replacement across up to 50 canonical Notes) plus a
migration. Escalate to the full three-agent test **only if** the quota bypass is extended beyond
ADMIN, which would add money semantics.

**Pre-declared discriminating guards** (each fails under the defect, passes under the fix):

1. **Metadata preservation** — a batch over a Note with an authored `title` and `tags` must leave
   **both byte-identical** afterwards. *This is the §A blocker; a fixture asserting only that content
   changed passes while titles are being destroyed.*
2. **Per-Note context** — a batch of two Notes with **different** Subjects and Domain Contexts must
   generate each against **its own** context. *A single-Note batch, or two Notes sharing metadata,
   passes under the batch-context defect and proves nothing.*
3. **Continue-on-failure and honest reporting** — with item 2 of 3 failing, items 1 and 3 must be
   `REGENERATED` and **item 1 must not be reported failed**. *Directly targets the outer catch.*
4. **Restart survivability** — items resolved before an interrupt must still be readable afterwards.
   *A batch that runs to completion passes under the receipt defect.*
5. **Blocked stays blocked** — a multi-program Note with a null Domain Context must be `BLOCKED` with
   a reason and **must not be regenerated**, and its content must be unchanged.
6. **Retry excludes successes** — retry after a partial batch must re-run only `FAILED` items, with
   quota charged only for those.

**Carried lessons:** mutate and confirm a **named** test fails; read `./mvnw`'s exit status directly;
count executed tests from `target/surefire-reports/*.xml` after cleaning it; run `npm test`; sweep by
**surface**, not by diff; verify "X already does Y" against code before it reaches a prompt; and call
`advisor()` before writing the Codex prompt for B1.
