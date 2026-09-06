# Regeneration failures reported as "generation failed" when the real cause was quota

**Date:** 2026-09-05. **Diagnosed:** 2026-09-06, from Render logs.
**Status:** partially fixed in `v0.119.0`. The residual is stated below, not hidden.

## What the owner saw

~14 canonical *Site Planning* notes were regenerated one at a time. **3–4 failed** with no reason
given. The owner manually retried them and they succeeded.

## What actually happened

The account hit its **monthly note-generation limit** partway through. Nothing was wrong with the
content pipeline, the LLM, or the connection pool.

The same cause surfaced two completely different ways depending on timing:

- Requests that hit the limit **synchronously** returned a clean `403 NOTE_GENERATION_LIMIT_REACHED`
  with a readable message. The log carries several at `2026-09-05T14:58:00–05Z`.
- Requests that had **already passed** the pre-dispatch check and hit it **inside the async worker**
  were caught by `generateStudyPackFromExistingNoteAsync`'s blanket `catch (Exception)`, marked
  `FAILED`, and told the owner nothing.

The recovered stack trace, note `c80852ea-9877-46a2-8773-07ee42b656cc`, failing at 4612 ms where
successes ran 10–27 s:

```
MonthlyNoteGenerationLimitReachedException: You have reached your note generation limit for this billing cycle.
  at NoteGenerationUsageProtectionService.assertQuotaAvailable(:26)
  at NoteGenerationService.generateFromTopic(:71)
  at StudyPackService.generateStudyPackFromExistingNoteAsync(:792)
  at StudyPackService.lambda$startAsyncNoteAndStudyPackRegeneration$2(:300)
```

**Why it split in two:** quota is checked at `StudyPackService:306` before dispatch and **again** at
`generateFromTopic:71` inside the worker, with a 10–27 s LLM call in between. The charge only lands at
commit. Several regenerations fired in quick succession therefore all saw the same remaining unit,
all passed the first check, and the later ones failed the second.

**The metering itself was correct.** Single-note regeneration always meters, even for ADMIN, matching
single-note generation. Only bulk bypasses for ADMIN. Not a defect.

## The evidence nearly did not survive

By the time this was investigated the database held **zero `FAILED` notes** — 6,550 `GENERATED`, 17
`DRAFT`. Regeneration mutates the note in place, so the owner's manual retries overwrote `status`, and
the failure reason was never persisted anywhere. **The entire incident was reconstructable only because
Render logs had not yet rotated.**

This is `v0.87.0` repeating. That release — *Failure Attribution* — exists because bulk generation told
the curator *which* topic failed and never *why*, and its own notes record that the gap "cost two
investigations". Single-note regeneration was never given the equivalent. It has now cost one.

## What `v0.119.0` changed

1. **The regenerate modal shows remaining allowance**, not merely what each scope costs, and refuses a
   scope whose meter is exhausted — gated on the meter *that scope actually spends*, so an exhausted
   note-generation allowance never blocks Study-Pack-only. Hidden entirely until the plan summary
   loads; a guessed "0 left" would be worse than silence.
2. **The bulk driver reports quota exhaustion as `BLOCKED`, not `FAILED`**, so retry does not re-run it
   blindly and spend a unit the curator has not got.

## What was deliberately NOT done

- **A per-item quota pre-check in the bulk driver was written and removed.** Mutation showed it changed
  nothing observable: the primitive's own synchronous `assertQuotaAvailable` already throws on the
  calling thread and the driver's existing catch already records `BLOCKED` with the same code. It cost
  an extra quota read per item and bought nothing.
- **The race is not closed, and cannot be closed cheaply.** Making the pre-dispatch gate binding needs
  either a user-row lock held across the LLM call — which `v0.107.0` forbids by name, because it would
  serialize every quiz start on the account and reintroduce what `v0.112.0` fixed — or reserve-then-
  refund, which breaks `v0.118.0`'s deliberate property that both meters land in one commit, and
  re-opens a refund question declined three times. **A burst can still race. The disclosure narrows the
  window; it does not remove it.**
- **The single-note failure reason is still not persisted.** `notes` has no failure-reason column, and
  adding one is a migration this release's anti-drift forbids. Backlog row below.

## Test coverage, stated honestly

`quotaExhaustedDuringABatchIsReportedAsQuotaRatherThanAsABareFailure` pins the **synchronous** leg —
pre-existing behaviour, guarded against regressing into a bare `FAILED`.

**It does not cover the async leg.** Reaching that deterministically needs quota to vanish between the
primitive's synchronous check and the worker's second assert, a window inside a single item that the
harness cannot open. The driver carries a defensive re-check for it. It is unproven, and this document
says so rather than implying coverage that does not exist.

## Owed

- Persist a regeneration failure reason (needs a migration; the async worker currently discards it).
- Reconsider whether the second, in-worker quota assert should exist at all, given the caller already
  checked — it converts a clean rejection into an opaque failure.
