# Curator Bulk Regeneration

Shipped in `v0.119.0`. Governed by `docs/claude-plans/curator-bulk-regeneration-stage1.md` — that plan
carries the reasoning; this file carries the behaviour.

## Key files

- `backend/.../service/NoteBulkRegenerationService.java` — the batch driver
- `backend/.../service/NoteRegenerationReadinessService.java` — the **single** per-Note guard, shared by
  preflight and the driver
- `backend/.../service/NoteRegenerationPreflightService.java` — disclosure (`POST /notes/regenerate/preflight`)
- `backend/.../service/NoteBulkRegenerationReceiptService.java` — receipt read + 24 h TTL sweep
- `backend/.../service/BulkRegenerationAccessGuard.java` — the curator gate
- `backend/.../db/migration/V135__note_bulk_regeneration_items.sql` — the only migration this feature owes
- `frontend/components/library/bulk-regenerate-modal.tsx` — preflight, confirmation, progress and receipt
- `frontend/app/library/page.tsx` — the `regenerate` selection intent

## Behaviour

- **Completion notification.** A run that reaches the normal end of `processBatch` delivers exactly one
  `BULK_REGENERATION_COMPLETE` notification linking to `/library`. Its copy reports only requested and
  regenerated counts already owned by the driver; blocked, failed, timed-out, and never-run items are
  simply not updated. The copy says only that existing Study Packs still work: an item that timed out while still running may yet succeed, so the copy never claims they are unchanged.
- **Interrupted runs do not notify.** `BatchInterruptedException` returns before delivery. There is no
  terminal `finally`: the receipt reports such a run as stale, and calling it complete would be false.
- **Bulk-only rationale.** A curator can stay in the regenerate modal, which polls the batch receipt every three
  seconds, or leave it; the notification is the signal for the curator who left. Single-note generation keeps
  the learner on a detail page that polls every three seconds, so it needs no duplicate signal.

- **Who.** Curators only: ADMIN by role, or TEACHER by profile, past onboarding — `CuratorAuthoringPredicate`.
  **The endpoints' `@PreAuthorize` cannot express this** (`hasAnyRole('USER','ADMIN')` is satisfied by every
  authenticated account), so `BulkRegenerationAccessGuard` enforces it on **both** the batch and the
  preflight. Gating only the batch would leave a disclosure surface wider than the capability.
- **Scope.** `STUDY_PACK` or `NOTE_AND_STUDY_PACK`, the same two Phase 1 offers. The modal always opens on
  `STUDY_PACK`; the reset comes from the caller unmounting the component, never from an effect.
- **Atomic unit is one Note.** Each item is Phase 1's commit: both quotas asserted, context resolved from
  **that** Note, two LLM calls, one commit writing both artifacts and both usage records.
- **Continue on failure.** One item failing never rolls back earlier items nor stops later ones.
- **Cap 50**, config-backed under `note.bulk-regeneration.max-notes` — its **own** key, so tuning it never
  moves the bulk *generation* cap. There is no one-click whole-Review-Set regeneration; a 500-note set
  exceeds the cap tenfold, and regenerating in deliberate passes is the intended outcome.
- **Metering.** A TEACHER curator is metered normally under block-and-reduce; the ADMIN bypass is the same
  `role() != ADMIN` expression bulk generation already uses and is **not** widened. An over-quota selection
  is rejected **422 before dispatch**, carrying how many notes to remove.
- **No cancellation.** Already-dispatched LLM calls cannot be stopped, so no control implies they can be.
- **Retry re-runs the FAILED items as a NEW batch** (`POST /notes/bulk-regenerate/{batchId}/retry`).
  **⚠️ It takes a BATCH ID, never a note list** — the server derives which items failed, so "only the
  failed ones" is a server guarantee rather than a client convention on a path that spends metered
  units. It routes through `queueBatch`, so a retry re-runs the curator gate, the pre-dispatch 422 and
  every per-item readiness guard, and inherits the original batch's scope.

## Anti-drift

- **Never reuse `NoteBulkGenerationService.processItem`.** It applies one batch-wide context to every item,
  reports success for items that failed, and rewrites `tags` through
  `applyBulkGeneratedMetadataToNote`. **⚠️ It also overwrote `title` until `v0.120.0` removed that line — so
  the "would destroy curator-authored canonical titles" hazard is now closed AT ITS SOURCE rather than only
  by this avoidance rule.** The rule stands anyway on its other two grounds (one batch-wide context, and
  success reported for failed items); do not read the title fix as licence to reuse `processItem`.
- **The item verdict comes from persisted `notes.status`, never from "the call did not throw."**
  `generateStudyPackFromExistingNoteAsync` catches `Exception`, marks the note `FAILED` and returns
  normally, so an outcome inferred from a clean return reports every async failure as a success.
- **Do not reproduce the outer-catch defect.** `NoteBulkGenerationService:232-246` rewrites every topic as
  failed on an interruption while `created_count` keeps its partial value. A regeneration batch must never
  report a regenerated Note as failed — the curator's only remedy would be to regenerate it again, spending
  quota and replacing good content.
- **Per-Note guards re-run at each item's start.** The preflight verdict is a snapshot and is not
  authoritative; a Note that went `GENERATING` in between takes `BLOCKED` with a reason, never skipped and
  never counted as regenerated — and the same applies to every preflight consequence count,
  `sharedQuizzesToDeactivate` included: a Note that flips to `BLOCKED` is never dispatched, so its
  content (and its shared quiz) is never replaced and its link is correctly not deactivated, but the
  preflight already counted it. The count can only ever OVERSTATE what a batch deactivates, never
  understate it — the safe direction, since nobody ends up graded against material that was never
  replaced.
- **⚠️ Known limitation, found by the `v0.151.0` signoff cold agent: the per-item receipt's
  `shareLinkDeactivated` flag is a stale prediction, not a fresh read.** `NoteBulkRegenerationService`
  captures `hasLiveShareLink` once, synchronously, before dispatching the item; the async worker that
  actually deactivates a link can run seconds to minutes later. A share link created on that Note's quiz
  inside that window is still deactivated (the deactivation call itself is unconditional), but the
  receipt records `false` — narrow (requires a link created mid-item-processing) and not a regression
  from `v0.151.0`.
- **The driver has its own executor** (`bulkRegenerationTaskExecutor`, 2/2/8). It must never run on
  `studyPackGenerationTaskExecutor`, which stays **2/2/100** — raising that is a `v0.112.0` Phase 3 decision.
  `setWaitForTasksToCompleteOnShutdown` is deliberately unset: it runs the entire queue uninterrupted.
- **No bulk lock.** `resolveSourceNoteForGeneration` already throws 409 `NOTE_GENERATION_IN_PROGRESS`, and
  that one per-Note guard covers single-vs-bulk and bulk-vs-bulk.
- **No "review recommended" preflight state.** A Note with a NULL Domain Context and one joined program is
  fully generation-ready, so the state would require judging metadata quality. Deterministic signals only —
  no score, no classifier.
- **Shared-quiz deactivation happens on either scope** (corrected `v0.151.0` — previously read
  `NOTE_AND_STUDY_PACK` only, which was a real bug, not a documented boundary: `saveStudyPack` replaces
  a Note's shared quiz in place regardless of scope, so a Study-Pack-only batch left a recipient graded
  against replaced material. The preflight count, the confirmation-dialog copy, and the per-item receipt
  flag all reflect this for both scopes now.
- **The receipt is not audit history.** Same 24 h TTL and hourly :45 sweep as `bulk_generation_result`, and
  the same `AccountPurgeService` deletion. It expires on the **batch** clock, so a batch expires atomically
  rather than leaving a receipt with holes in it. Unlike that receipt, reading it is **not** consume-once.
- **`finished` is derived** from "no item is still pending", never a stored end-of-batch flag — a driver
  killed mid-batch writes no end marker.
- **A 404 on the receipt poll is terminal, not transient** (`bulk-regenerate-modal.tsx`, `v0.147.0`). The
  modal seeds `batchId` from `sessionStorage` with no TTL awareness, so a batch whose receipt expired past
  the 24h TTL (or was never valid) still gets polled. Every other poll failure is swallowed and retried
  next tick, but a 404 stops the poll, clears the stored id, and returns to the preflight (start) view
  showing the server's own message — otherwise the curator is wedged on that view with no way back short
  of DevTools or closing the tab. A "Start a new batch" escape hatch (`handleStartNewBatch`) does the same
  reset independent of the poll noticing anything, covering every other reason the receipt might stay
  unreachable.

## Known limitations

- **Nothing sweeps a lost batch.** A driver killed mid-loop leaves `RUNNING`/`PENDING` rows until the TTL.
  `GenerationRecoveryService` heals the stranded *note* at 120 minutes, so the note self-heals while its
  batch row does not; the receipt reports `stale` rather than showing progress that will never advance.
- **`writeItem` is find-then-save** with no lock against the unique `(batch_id, note_id)`. Safe while one
  driver owns a batch, and retry preserves that by minting a new batch id rather than writing into the
  old one. Any future path that writes into an existing batch from a second thread must revisit this.
- **`REGENERATED` is never retried** — it would spend a unit and replace good content with a second
  generation nobody asked for — and neither is `BLOCKED`, which stays blocked until its condition
  changes. There is no auto-retry.
- **⚠️ Retry MUST mint a new batch id, and does.** `writeItem` is find-then-save against the unique
  `(batch_id, note_id)` with no lock, which is safe only while ONE driver owns a batch. Retrying *into*
  the original batch would give one row two writers whenever a timed-out `RUNNING` worker is still
  alive, and the loser's constraint violation is swallowed. A fresh batch id makes that impossible by
  construction. **Do not "simplify" retry to reuse the original batch.**
- **A note that is not the caller's** reads `NOT_ELIGIBLE` in preflight and `NOT_RUN` in the driver — the
  same miss under two names.
