# Fix plan — Bulk Regenerate wedges on a stale batch id (2026-09-12)

**Written by:** Prod Investigator session, 2026-09-12. **For:** the implementing session.
**Evidence:** `docs/claude-findings/2026-09-12-bulk-regeneration-modal-wedged-stale-batch-id.md` —
read it first; §5 there explains why the existing guard does not already cover this.

**Status: NOT STARTED.** No code changed by the investigating session.

⚠️ **The owner is currently blocked and has a workaround**: close and reopen the tab, or
`sessionStorage.removeItem("notelib-bulk-regeneration-batch")`. That unblocks them; it does not fix
the defect, and the next expired batch reproduces it.

---

## 1. Settled facts — do NOT re-derive

All references `frontend/components/library/bulk-regenerate-modal.tsx` unless stated.

1. `:78` seeds `batchId` from `sessionStorage` key `notelib-bulk-regeneration-batch` (`:30`, `:43`).
2. `:483` — `{batchId ? renderProgress() : renderPreflight()}`. **Any stored batchId hides the control
   that starts a regeneration.** This is the user-visible failure.
3. `:367-369` — no receipt yet ⇒ `Starting…`.
4. `:130-138` — the poll's `catch {}` swallows **all** failures, including a permanent 404.
5. `:126` — stop condition is `receipt?.finished || receipt?.stale`; **both require a 200**, so a 404
   never stops the 3-second poll (`POLL_INTERVAL_MS = 3000`, `:21`).
6. Receipts have a **24 h TTL** — `RECEIPT_TTL_HOURS = 24`
   (`backend/.../NoteBulkRegenerationReceiptService.java:34`, swept at `:125`). The production table is
   currently **empty**, so every stored id older than 24 h 404s.
7. The 404 is thrown at `NoteBulkRegenerationReceiptService:55` and is **deliberate** — its comment
   records that unknown / someone-else's / expired are intentionally indistinguishable.
8. `queueBatch` writes PENDING rows **synchronously before returning** (`NoteBulkRegenerationService.java:238-243`),
   so "no rows" reliably means "no batch was started". ⚠️ Do not change that ordering as part of this.

---

## 2. Leg A — treat an unknown batch as TERMINAL, not transient (the fix)

The poll must distinguish **"this batch is gone"** (permanent) from **"that read failed"** (transient).
On the permanent case it must: stop polling, clear the stored id, and return the modal to preflight so
the curator can start a real batch.

⚠️ **The backend already supplies the user-facing words** — *"That regeneration batch is no longer
available."* Surface those rather than inventing new copy.

**Two implementation shapes; §5.1 is the decision.**
- **A1 — discriminate on status.** Catch the 404 specifically (`ApiRequestError.status === 404`, the
  pattern already used at `frontend/app/collections/[id]/builder/…` and in `lib/api.ts` callers) and
  treat only that as terminal. Everything else stays transient. **Recommended.**
- **A2 — bound the retries.** Cap consecutive failures and give up after N. ⚠️ **Weaker**: it delays
  rather than fixes, it would also abandon a genuinely transient outage, and it leaves the stored id in
  place. The repo has this pattern for pollers (`LIBRARY_GENERATION_POLL_MAX_TICKS = 100`) but it is a
  backstop, not a diagnosis.

⚠️ **A1 and A2 are not exclusive** — a bound is a reasonable belt-and-braces addition *after* the 404
is handled. Do not ship A2 alone.

## 3. Leg B — an escape hatch that does not depend on the poll

Even with Leg A, the modal has **no way to abandon a batch view**: `Close` dismisses the dialog but
`:78` re-seeds from storage on the next mount. A `Start a new batch` / `Dismiss this batch` action that
clears the stored id would have let the owner self-serve today without DevTools.

⚠️ **Independently useful and independently shippable** — it covers every future "the receipt is
unreachable for some reason we have not thought of", which is the class this incident belongs to.

## 4. Explicit non-fixes — do not re-propose

- ⚠️ **Do NOT extend `RECEIPT_TTL_HOURS`.** 24 h is a deliberate retention choice. A longer TTL moves
  the threshold and leaves the wedge intact for anything past it — the same "move the threshold"
  mistake recorded against the public-catalog fetches.
- ⚠️ **Do NOT remove the `sessionStorage` persistence.** It is deliberate: it lets a curator navigate
  away and come back to a running batch. Deleting it would regress that on purpose.
- ⚠️ **Do NOT make the `catch` rethrow everything.** Its comment is correct — a transient read failure
  must not kill the view of a live batch. The fix is to *discriminate*, not to remove the guard.
- ⚠️ **Do NOT change the 404 contract** at `NoteBulkRegenerationReceiptService:55`. Collapsing
  unknown / not-yours / expired is a deliberate information-disclosure decision. ⚠️ **In particular do
  not add a distinguishable "expired" status code** — that leaks batch existence to a non-owner.
- ⚠️ **Do NOT touch `queueBatch`'s write-before-return ordering** (§1.8) — it is what makes "no rows"
  diagnostic.
- ⚠️ **Do NOT fold in the `INVALID_REFRESH_TOKEN` 401s** (findings §7). Unproven relation; folding it
  changes the verification tier.

## 5. Decisions owed BEFORE implementation

1. **A1, or A1 + a retry bound?** Recommend **A1 alone first** — it is the actual defect, and the bound
   adds a second mechanism whose absence is not what broke today.
2. **On a terminal 404, return to preflight silently or with a notice?** Recommend a notice carrying the
   server's own sentence, so the curator understands why the dialog reset rather than suspecting the
   click was lost.
3. **Ship Leg B now or defer?** It is small and prevents the DevTools-or-nothing situation the owner hit
   today. Owner's call on scope.

## 6. Pre declared guards

⚠️ This repo has shipped two silent no-ops whose tell was identical: **a diff changed behaviour while
touching no test that runs it.** These are written so a fixture cannot pass under both the defect and
the fix.

- **⚠️ THE DISCRIMINATING GUARD — a test that mocks a SUCCESSFUL receipt passes under both the defect
  and the fix and proves nothing.** The fixture must make `getBulkRegenerationReceipt` **reject with a
  404-shaped error**, then assert (a) polling **stops** — advance timers and assert no further call —
  (b) the stored key is **cleared**, and (c) the **preflight view is rendered**, i.e. the start control
  is reachable again.
- **Transient-path regression guard:** a **non-404** rejection must still be swallowed and retried —
  assert a second call happens on the next tick. Without this, a fix that rethrows everything passes
  the guard above while regressing the behaviour the `catch` exists for.
- **⚠️ Mount-from-storage guard:** seed `sessionStorage` with an id **before** mount and assert the
  modal opens in the progress view. That is the step that made the owner's dialog unstartable, and it is
  the one a test written only around the poll will miss.
- **Leg B guard:** with a stored id present, the dismiss action clears the key **and** the next mount
  shows preflight.
- ⚠️ **Reach the API the way production does.** `lib/api-*.test.ts` pins request/response shape; a
  component test that mocks `lib/api` wholesale proves nothing about how a 404 actually surfaces.
  **`v0.119.0` shipped a bulk feature whose every POST was rejected before the controller while 2,182
  component tests passed, because they mocked `lib/api` wholesale** — and that was *this* feature area.
  Confirm what `getBulkRegenerationReceipt` really throws on a 404 rather than assuming its shape.

## 7. ⚠️ Version and branch

**`v0.144.0 — No Backdoor Left` is open** (kicked off 2026-09-13, base `releases/v0.144.0`), scoped to
**one item** — admin repair paths bypassing exam-pool invalidation — with an explicit anti-drift list.
**This work is outside that scope and must not be folded into it.**

**Recommendation:** finish `v0.144.0` as scoped, then open **`v0.145.0`** carrying Legs A and B. This
is not an emergency — the owner has a working workaround (close the tab) — so it does not justify
disturbing an open release.

⚠️ If the owner would rather ship it immediately, the honest trade is: `v0.144.0` is a one-file backend
fix at a single-`advisor()` tier, and adding a frontend item makes the release two-surface. Say that
before folding, per the release-size rule.

**Routing: CLAUDE CODE inline** — frontend only, one file, clear root cause, existing patterns
(`ApiRequestError.status` checks and `MAX_TICKS` backstops both already exist in the tree). Well under
the Codex threshold.

**Verification tier: one `advisor()` call on the diff.** No authorization, quota, money or
production-data semantics change; no migration; no new endpoint, so nothing here owes a real-request
`MockMvc` test.

## 8. Obligations at signoff

- **Add Backlog Index rows** for the findings file and this plan.
- **Update `docs/features/`** for whatever documents bulk regeneration — the modal's recovery behaviour
  is user-visible.
- ⚠️ **If only Leg A ships**, record in `RELEASES.md` that a curator still cannot abandon a batch view
  from the UI when the receipt is unreachable for any reason other than a 404.
