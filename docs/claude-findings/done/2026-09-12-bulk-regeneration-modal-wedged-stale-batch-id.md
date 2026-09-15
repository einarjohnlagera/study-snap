# 2026-09-12 — Bulk Regenerate is unusable: a stale batch id wedges the modal at "Starting…"

## Status: diagnosis only. No code, config or data changed. No release opened.

**Reported** by the owner 2026-09-13 ~01:03 local (+08) = **2026-09-12 17:03 UTC**: *"I cannot
regenerate in prod"*, with a screenshot of a `Regenerating` dialog reading `Starting…` and offering
only `Close`. Confirmed and root-caused.

⚠️ **THIS IS A FRONTEND STATE DEFECT, NOT A SERVER FAILURE. Regeneration is not broken, and no batch
was attempted.** The server behaved correctly throughout; it was answering a question about a batch
that no longer exists.

Claims are **VERIFIED** (a log line, a query result, or code opened) or **INFERRED**.

---

## 1. The chain — VERIFIED end to end

All line references are `frontend/components/library/bulk-regenerate-modal.tsx` unless stated.

1. **`:78`** — the modal seeds its batch state from browser storage on mount:
   ```js
   const [batchId, setBatchId] = useState<string | null>(() => readStoredBatchId());
   ```
   The key is `notelib-bulk-regeneration-batch` (`:30`) in **`sessionStorage`** (`:43`).
2. **`:483`** — `{batchId ? renderProgress() : renderPreflight()}`. ⚠️ **With any stored batchId the
   preflight view — the one carrying the button that STARTS a regeneration — is unreachable.** This is
   the whole of "I cannot regenerate": the action is not offered, not failing.
3. **`:476`** — the title becomes `Regenerating`, matching the screenshot.
4. **`:367-369`** — `renderProgress()` with no receipt yet returns `Starting…`. Also matching.
5. **`:130-138`** — the poll requests the receipt and **swallows every failure**:
   ```js
   } catch {
     // A transient read failure must not kill the batch view; the next tick retries.
   }
   ```
   So `receipt` stays `null`.
6. **`:126`** — the stop condition is `if (!batchId || receipt?.finished || receipt?.stale) return;`.
   ⚠️ **Both flags require a SUCCESSFUL response.** A 404 sets neither, so the effect never returns
   early and the 3-second interval (`POLL_INTERVAL_MS = 3000`, `:21`) **runs forever**.

**Net effect: a permanently wedged dialog that polls production every 3 seconds and can never leave
the "Starting…" state, while hiding the control that would start real work.**

## 2. Why the stored batch does not exist — VERIFIED

- `note_bulk_regeneration_item` in production is **completely empty** (`SELECT … GROUP BY batch_id`
  returns zero rows). The table exists, so its migration ran.
- Receipts carry a **24-hour TTL**: `RECEIPT_TTL_HOURS = 24`
  (`backend/.../service/NoteBulkRegenerationReceiptService.java:34`), swept by
  `deleteByBatchCreatedAtBefore(now.minusHours(24))` (`:125`).

So **any stored batch id older than 24 hours 404s permanently**, and `sessionStorage` outlives that
window comfortably.

⚠️ Which batch id was stored cannot be recovered — it lives in the owner's browser and is not logged.
**INFERRED** that it came from the 2026-09-09 bulk runs (`action=bulk_regenerate_batch` lines exist
for that date), but the id itself is unrecoverable and it does not change the mechanism.

## 3. The server was correct — and it said so, 40+ times

```
2026-09-12T17:01:23.168Z WARN GlobalExceptionHandler : app_exception
  code=NOTE_BULK_REGENERATION_BATCH_NOT_FOUND status=404
  message=That regeneration batch is no longer available.
```
…repeating at **17:01:26, :29, :32, :35, :38, :41, :44, :47, :50, :53, :56 …** continuously through
17:03:20 — **a clean 3-second cadence that matches `POLL_INTERVAL_MS` exactly.**

⚠️ **The backend's message is already the right words for the user** — *"That regeneration batch is no
longer available."* The frontend receives it and throws it away.

Thrown at `NoteBulkRegenerationReceiptService:55`, whose contract comment notes that *"unknown,
someone else's and expired are indistinguishable"* — a deliberate 404 for all three.

## 4. Ruled out

- **No POST was attempted.** `queueBatch` (`NoteBulkRegenerationService.java:221-280`) writes every
  PENDING row **synchronously at `:238-243`, before minting the response**, so a successful start would
  have left rows in the now-empty table.
- **Not a server-side rejection.** No `app_exception` for the curator gate, quota
  (`BulkNoteRegenerationQuotaExceededException`), onboarding guard or request validation appears in the
  window — only the 404s and an unrelated 401. The POST did not arrive and was not refused.
- **Not a swallowed write.** `writeItem:686-709` catches and logs
  `action=bulk_regenerate_item outcome=failed_to_record`. A text search covering `bulk_regenerate`
  returns **no** such line.
- **Not the 2026-09-10 pool exhaustion.** No `HikariPool` timeout, no health-check failure, no restart
  in the window.
- **Not a deploy skew.** `v0.143.0` is confirmed live on both platforms per `CLAUDE.md`; the 404 is a
  deliberate application response, not a contract mismatch.

## 5. ⚠️ The authors anticipated this class and the guard misses the case that happens

`:120-125` reasons explicitly about it:

> *"`finished` is derived server-side from 'no item is still pending', never a stored end-of-batch flag,
> so a driver killed mid-batch does not leave this polling forever — it comes back `stale` instead."*
> *"⚠️ `stale` stops the poll too: a batch a deploy killed will never advance, so polling it every 3s
> for as long as the modal stays open is pure waste."*

**`stale` is a field on a `200` response.** It handles *a batch that exists but cannot advance*. It
cannot handle *a batch that no longer exists*, because that path returns 404 and lands in the
swallowing `catch`. **The design covered the adjacent case and not the terminal one.**

⚠️ **And the swallow is individually defensible** — a transient read failure genuinely should not kill
the batch view. The defect is that **a permanent 404 and a transient blip are treated identically.**

## 6. Impact and workaround

- **Scope: one browser tab per stored id.** `sessionStorage` is per-tab and per-origin, so it does not
  follow the owner to another tab, another browser or another device. **INFERRED** that this is why it
  reads as "prod is broken" rather than "one tab is stuck".
- **Load: a 3-second poll per wedged tab, indefinitely** — modest, but it is the same
  client-driven-load shape as the 2026-09-06 builder loop, against a backend with a documented
  pool-exhaustion history.
- **Workaround (VERIFIED from the storage mechanics, not from a live test):** close and reopen the tab,
  or run `sessionStorage.removeItem("notelib-bulk-regeneration-batch")`.

## 7. Unrelated, flagged not investigated

`code=INVALID_REFRESH_TOKEN status=401 message=Invalid refresh token.` at 17:01:38 and 17:02:38 —
roughly once a minute, independent of the poll cadence. Worth a look only if the owner is also seeing
unexpected logouts.

## 8. Obligations

- This file needs a **Backlog Index row** (kickoff step 8 names `docs/claude-findings/`).
- Fix plan: `docs/claude-plans/2026-09-12-bulk-regeneration-404-terminal-state-fix-plan.md`.
- ⚠️ **`v0.144.0 — No Backdoor Left` is open and this is OUTSIDE its scope** (one item, admin exam-pool
  invalidation, with an explicit anti-drift list). See §7 of the plan.
