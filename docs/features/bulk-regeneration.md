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

## Anti-drift

- **Never reuse `NoteBulkGenerationService.processItem`.** It applies one batch-wide context to every item,
  reports success for items that failed, and overwrites `title` and `tags` through
  `applyBulkGeneratedMetadataToNote` — which would destroy curator-authored canonical titles.
- **The item verdict comes from persisted `notes.status`, never from "the call did not throw."**
  `generateStudyPackFromExistingNoteAsync` catches `Exception`, marks the note `FAILED` and returns
  normally, so an outcome inferred from a clean return reports every async failure as a success.
- **Do not reproduce the outer-catch defect.** `NoteBulkGenerationService:232-246` rewrites every topic as
  failed on an interruption while `created_count` keeps its partial value. A regeneration batch must never
  report a regenerated Note as failed — the curator's only remedy would be to regenerate it again, spending
  quota and replacing good content.
- **Per-Note guards re-run at each item's start.** The preflight verdict is a snapshot and is not
  authoritative; a Note that went `GENERATING` in between takes `BLOCKED` with a reason, never skipped and
  never counted as regenerated.
- **The driver has its own executor** (`bulkRegenerationTaskExecutor`, 2/2/8). It must never run on
  `studyPackGenerationTaskExecutor`, which stays **2/2/100** — raising that is a `v0.112.0` Phase 3 decision.
  `setWaitForTasksToCompleteOnShutdown` is deliberately unset: it runs the entire queue uninterrupted.
- **No bulk lock.** `resolveSourceNoteForGeneration` already throws 409 `NOTE_GENERATION_IN_PROGRESS`, and
  that one per-Note guard covers single-vs-bulk and bulk-vs-bulk.
- **No "review recommended" preflight state.** A Note with a NULL Domain Context and one joined program is
  fully generation-ready, so the state would require judging metadata quality. Deterministic signals only —
  no score, no classifier.
- **Shared-quiz deactivation is `NOTE_AND_STUDY_PACK` only.** Study-Pack-only regeneration does not replace
  the Note content a shared quiz was built from.
- **The receipt is not audit history.** Same 24 h TTL and hourly :45 sweep as `bulk_generation_result`, and
  the same `AccountPurgeService` deletion. It expires on the **batch** clock, so a batch expires atomically
  rather than leaving a receipt with holes in it. Unlike that receipt, reading it is **not** consume-once.
- **`finished` is derived** from "no item is still pending", never a stored end-of-batch flag — a driver
  killed mid-batch writes no end marker.

## Known limitations

- **Nothing sweeps a lost batch.** A driver killed mid-loop leaves `RUNNING`/`PENDING` rows until the TTL.
  `GenerationRecoveryService` heals the stranded *note* at 120 minutes, so the note self-heals while its
  batch row does not; the receipt reports `stale` rather than showing progress that will never advance.
- **`writeItem` is find-then-save** with no lock against the unique `(batch_id, note_id)`. Safe while one
  driver owns a batch. **A retry feature must mint a new batch id or make the write conditional.**
- **Retry-failed is not built.** The receipt exposes `retryableNoteIds` (FAILED only — never `REGENERATED`,
  which would spend quota and replace good content), and the curator re-selects those notes deliberately.
- **A note that is not the caller's** reads `NOT_ELIGIBLE` in preflight and `NOT_RUN` in the driver — the
  same miss under two names.
