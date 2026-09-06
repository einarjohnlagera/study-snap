# Note Collection & Builder — Performance Audit

**Status:** AUDIT + PLAN ONLY — nothing implemented. Written 2026-09-06.
**Scope:** the journey from opening a Note Collection through editing a section in the Builder.

---

## 1. Executive judgment

**The backend per-request work is already well optimized. The waste is at the request layer — the
same expensive payload is fetched repeatedly, and one write's own response is thrown away.**

Credit where it is due: `toItemResponses` issues **five bulk queries keyed by `noteIdIn`** and has
**no N+1 inside the item loop**, so one collection read costs roughly the same whether the plan holds
5 notes or 500. That is the part that usually goes wrong, and it is right.

**Four levers, ordered by value. Lever 1 alone accounts for most of it.**

---

## 2. The journey, counted

| Step | Requests | Notes |
|---|---|---|
| **Open collection** (`/collections/[id]`) | `getCollection` + **`listNotes()`** + (goal) + (parent `getCollection`) | `:2830-2857` |
| **Open builder** (`/collections/[id]/builder`) | `getCollection` + **`listNotes()`**; for a Goal, `getCollectionGoal` + **one `getCollection` per child** | `refreshBuilder` |
| **Edit one section label** | flush `PUT items/order` (if dirty) + **`PUT items/order`** + `getCollection` + **`listNotes()`** | `persistLeafItems` |

**So a single section edit costs up to four round trips, two of which recompute the same payload and
one of which downloads the curator's entire note library.**

---

## 3. Lever 1 — the unbounded `listNotes()` (biggest, and it repeats)

**`listNotes()` is called with no limit**, and the backend does not impose one:

```java
return noteService.listMine(userId, limit == null ? null : Math.clamp(limit, MIN, MAX));
```
`NoteController.listMine` — **`null` limit means no cap.**

**Each row is heavy.** `NoteListItemResponse` carries **`contentPreview` and `summaryPreview`**
alongside tags, author fields and eight timestamps/counters. **⚠️ Those are the same two fields that
pushed `/notes/public` past Next.js's hard 2 MB data-cache limit in the 2026-08-31 build failure** —
that incident is the documented proof this payload class gets large at curator scale.

**It fires three times in this journey**: collection detail load, builder load, and again after every
non-drag builder mutation.

**And in the Builder its primary consumer is a modal that may never be opened** — `filterPickerNotes`
feeds the *Add notes* picker. The other uses read note metadata that the collection payload already
carries: `handleSetSectionsFromSubjects` reads `item.subject` **off the leaf item**, not off the
global list.

### Recommendation

1. **Lazy-load the picker list.** Fetch on picker open, not on page load. Biggest win, no contract
   change.
2. **Bound it.** Pass an explicit `limit`; the endpoint already clamps when given one.
3. **Longer term:** server-side search for the picker — it currently filters client-side over the
   whole library.

**⚠️ Verify each remaining `noteById` consumer before removing the eager fetch** (`:1031`, `:1738`,
`:1852`). If any needs a note **not** in the collection, that path must trigger the lazy load rather
than read an empty map.

---

## 4. Lever 2 — the write's own response is discarded

```js
await setCollectionItemOrder(collectionId, buildOrderPayload(nextItems));  // returns full detail
lastSavedLeafItemsRef.current = nextItems;
...
await refreshBuilder();          // ⚠️ getCollection() + listNotes() again
```
`persistLeafItems`

**`PUT /collections/{id}/items/order` returns `NoteCollectionDetailResponse`** — byte-for-byte the
payload `getCollection` returns (`NoteCollectionController:322-324`). The client **ignores it**, then
refetches the same thing plus the whole note list.

**For a leaf collection the refresh is entirely redundant:** `refreshBuilder` returns early once
`collectionResult.childCount === 0`, after setting exactly the state the PUT response already
describes.

### Recommendation

- **Consume the PUT response** and drop `refreshBuilder()` on the leaf path.
- **Minimum viable version if that is too invasive:** pass **`skipNotes: true`**. The option already
  exists and `persistLeafItems` simply does not use it — and a section label change **cannot** change
  the note set, which is exactly the condition `refreshBuilder`'s own comment names as safe.

**⚠️ Keep the flush-first behaviour** (`savePendingLeafOrder({ refreshAfter: false })`). It is a
correctness guard: a visible pending order must reach the server before another mutation is applied.
**Do not remove it to save a request.**

---

## 5. Lever 3 — HTTP N+1 on the Goal path

```js
const goalResult = await getCollectionGoal(collectionId);
const childDetails = await Promise.all(goalResult.children.map((child) => getCollection(child.collectionId)));
```
`refreshBuilder`

**One request per child Subject Plan.** A Review Set with 20 plans is **21 requests**, each running
the ~7-query detail build — so ~147 queries and 21 round trips to render one page. `Promise.all`
makes them concurrent, not cheap, and **⚠️ concurrency here is a connection-pool consideration**: the
pool is 20 (`application.yaml`), and `v0.112.0` documents pool exhaustion as a live production
failure mode.

### Recommendation

Return child detail **with** the goal, or add a batch read. **⚠️ This is a response-contract change**,
so it is the one lever that is not purely internal — price it accordingly and do it after 1 and 2.

**⚠️ Do not "fix" it by raising the pool** — `AppConfig:52-72` records that bound as a `v0.112.0`
Phase 3 decision gated on `[CHECKPOINT — due 2026-10-04]`.

---

## 6. Lever 4 — duplicated work inside the detail page

`collection-detail-page-client.tsx` performs `getCollection` **again** at `:3000`, `:3081`, and
`getCollectionGoal` at `:3002`, `:3083`, `:3645`, `:4010` — several of these follow a mutation that
already returned fresh detail, the same shape as Lever 2.

**Recommendation:** apply the Lever 2 rule uniformly — **after any mutation that returns
`NoteCollectionDetailResponse`, consume the response instead of refetching.** Audit each of the six
sites; some may follow mutations that genuinely invalidate more than the collection.

---

## 7. What is already right — do not "optimize" these

- **⚠️ `toItemResponses` is fully batched** — five bulk queries, no per-item lookups. **Do not
  restructure it.**
- **⚠️ `refreshBuilder`'s `skipNotes` option and its comment must stay** — a recorded fix for a
  measured problem; paths that change the note set must keep passing `skipNotes: false`.
- **⚠️ The deferred-save model stays** (`v0.96.0`) — autosave-per-drop raced itself and was fixed at
  cost. **Reducing requests must not reintroduce it.**
- **⚠️ The `editing`-gated debounce on the section combobox stays** (`v0.88.0`, forbidden by
  `CLAUDE.md` to remove).

---

## 8. Sequencing

| # | Change | Contract change | Expected effect |
|---|---|---|---|
| **1** | `persistLeafItems` passes `skipNotes: true` | No | Removes the heaviest fetch from every section edit — **one line** |
| **2** | Consume the PUT response; drop the leaf refresh | No | Removes a whole round trip per edit |
| **3** | Lazy-load the picker note list | No | Removes the heaviest fetch from both page loads |
| **4** | Batch the Goal children read | **Yes** | Turns 21 requests into 1–2 on large Review Sets |
| **5** | Sweep the detail page's six refetch sites | No | Same rule as 2, applied consistently |

**Start with 1 and 2** — together they are a handful of lines, need no contract change, and remove
most of the cost from the interaction the question names (editing a section).

**Routing:** 1, 2, 3, 5 are **Claude Code inline**; 4 is **Codex** (DTO + service + client).

---

## 9. Verification

**⚠️ Measure before and after — this is a performance claim, and the repo's standing lesson is that a
fix that looks correct can change nothing.** Cheapest honest measurement: browser devtools network
panel on a large real plan, counting **requests and transferred bytes** for (a) opening the
collection, (b) opening the builder, (c) editing one section label.

**Pre-declared guards:**

1. **Note-set paths still refresh.** Add, remove and import must still refetch notes — assert those
   call sites do **not** pass `skipNotes: true`. *A fixture that only edits a label passes under a
   version that broke add/remove.*
2. **Pending order still flushes first.** A pending drag plus a section edit must persist both, in
   order. *Guards Lever 2 against removing the flush.*
3. **Picker still works after lazy loading** — opening it must list notes not in the collection.
4. **Large-plan request count** — editing a section on a plan with many notes must issue **one** write
   and **no** full-library fetch.

**Tier: a single `advisor()` call** for 1, 2, 3, 5 — no permission substrate, no cross-user read, no
money semantics, no migration. **One scoped cold agent if lever 4 ships**, since it changes a
response contract several surfaces read.
