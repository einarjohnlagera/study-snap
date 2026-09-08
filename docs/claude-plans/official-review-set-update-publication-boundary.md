# Official Review Set Update Publication Boundary — Audit & Plan

**Status:** AUDIT COMPLETE — **implementation plan below, not implemented.**
**Date:** 2026-09-07. Every claim carries a `file:line` anchor against the current tree
(post-`v0.129.0`, mid-`v0.130.0`).
**Related:** `in-app-notifications-and-review-set-adoption-signals-stage1.md` — **§7 below supersedes
that plan's §8 blocker.**

## Central contracts (preserved verbatim)

> **Editing an Official Review Set is not publishing an Official Review Set update.**
> **Curators decide when accumulated curriculum changes become learner-facing.**
> **Source changed != published update available.**
> **Notifications may announce a published update; they must never define whether the update exists.**
> **Publish update makes changes available for learner review. It does not force learner synchronization.**
> **Curriculum update != Note content overwrite.**
> **One learner remains one adopter across every curriculum update.**
> **New adopters should not accidentally receive unfinished curator work.**

---

## 1. Executive judgment

**The gap is real and total: there is exactly ONE source state. No working/published separation
exists anywhere.** But the fix is **proportional, not a snapshot architecture** — because of one
structural fact the code states outright:

```java
// THIS RELEASE REPORTS AND NEVER APPLIES.
```
`NoteCollectionService:2050`

**`applySourceUpdate` is additive-only.** `MOVED` changes are *detected and reported*, never applied;
only `ADDED_NOTE` and `ADDED_SUBJECT_PLAN` are applied. **So the publication boundary only has to gate
what becomes visible as an addition** — which a per-row publication stamp does exactly, with no
snapshots, no versions, no history.

**Three pieces of good news:**

1. **Nothing has been mis-wired yet (§28).** `NotificationService` has **zero** references to source
   updates or drift — the Review Set update notification was correctly left unbuilt. **No correction
   is owed to shipped code.**
2. **The notification foundation needs no new enum value.** `NotificationType` is coarse —
   `ANNOUNCEMENT(false)` / `ACTION_REQUIRED(true)` — so a Review Set update slots in as
   `ACTION_REQUIRED`.
3. **⚠️ This audit dissolves the previous plan's blocker.** That plan's §8 needed a *drift signature*
   because `source_synced_at` does not advance reliably. **With a real publication event, dedup no
   longer keys on the sync baseline at all** (§7). The drift-signature workaround is withdrawn.

**⚠️ One contract cannot be fully honoured proportionally: the public view (§12 / Test 11).** See §6 —
additions can be hidden, removals and reorders cannot, and closing that would require the snapshot
architecture §33 tells us to stop before building. **Recommendation: narrow the contract, do not
build snapshots.**

---

## 2. §29.A — Current behaviour, verified

| Question | Answer |
|---|---|
| How do Official Review Set edits work? | Ordinary collection mutations. **No publication concept** — `NoteCollectionEntity` has no `published_at` / `publishedAt` / `last_published` field |
| What is "published" today? | **Only `CollectionVisibility` = `PRIVATE \| PUBLIC`** — that is *discoverability*, not curriculum finalization (§4's suspicion confirmed) |
| What do the public see? | **Live working state.** `getPublic:686` reads items with `findByCollectionIdInOrderByCollectionIdAscPositionAsc` — no publication filter |
| What does a new adopter receive? | **Live working state.** Adoption copies source items read live (`:1765`) |
| How is drift detected? | Compare `source_*_at_sync` baseline against source **now** — so **any** curator edit produces drift immediately |
| Does adoption copy or reference? | **Copies** — learner-owned rows, stamped with `source_*_at_sync` |
| Is a notification wired to drift? | **No** — verified |
| Update banner? | **Exists** — `collection-detail-page-client.tsx` (`sourceUpdate` state, `source-update` mutation kind) |

**So today: curator adds one Note → every adopter is instantly "behind", and every public visitor and
new adopter sees the unfinished work.** That is precisely the defect this audit exists to close.

## 3. §29.B — Gap analysis

**The architecture cannot distinguish working source state from published learner-facing state. At
all.** There is one row set, read live by four consumers: drift, apply, adoption copy, and the public
view. Nothing marks any row as finalized.

**But the gap is narrower than it looks**, because the apply contract is additive-only. The only
learner-facing consequence of an unpublished edit is an addition appearing early — and additions are
row insertions, which a per-row flag can gate.

## 4. §29.C — Minimum architecture

**Three nullable columns and one transaction. No Git, no versions, no snapshots, no history.**

| Column | Table | Meaning |
|---|---|---|
| `published_at` | `note_collection_items` | this item is part of the published curriculum |
| `published_at` | `note_collections` | this child Subject Plan is published |
| `last_update_published_at` | `note_collections` | the curator's finalization marker on the Official root |

**Consumers change by filtering, not by restructuring:**

| Consumer | Change |
|---|---|
| Drift / `getSourceUpdate` | consider only source rows with `published_at IS NOT NULL` |
| Adoption copy | copy only published rows |
| `getPublic` | render only published rows |
| Curator/Admin read | unchanged — sees everything, published or not |

**`Publish update` is one transaction:** stamp `published_at = now` on every unpublished row under the
set, then set `last_update_published_at = now` on the root. **⚠️ Raw `updated_at` is never used as the
boundary** (§6 of the brief).

**Why this is the smallest thing that works:** it reuses the existing additive-sync model rather than
composing against it, needs no new entity, no sequence number, and no per-revision storage. The
"published update" is not an object — it is the set of rows whose stamp is newer than the adopter's
baseline.

## 5. §29.E — New-adopter semantics (answered explicitly)

> **A new adopter receives the latest PUBLISHED curriculum, never the curator's working state.**

**Achieved by the same primitive:** the adoption copy filters to `published_at IS NOT NULL`. **This is
the contract the brief called the most important question, and it is satisfiable without additional
architecture** — because adoption is a copy, and a copy can simply skip unpublished rows.

## 6. ⚠️ §29.F — Public-view semantics (the one partial answer)

> **Preferred contract: public visitors see the latest published state.**
> **Achievable for ADDITIONS. Not achievable for removals or reorders without snapshots.**

| Working change | Hidden from public by the stamp? |
|---|---|
| Note added | **Yes** — `published_at IS NULL`, filtered out |
| Subject Plan added | **Yes** |
| Note removed | **No** — the row is deleted; there is no soft-delete to hide behind |
| Reordered / section renamed | **No** — `position` and `label` are single columns; hiding a working value needs a second column per field |

**Three options, per §33:**

| Option | Cost | Verdict |
|---|---|---|
| **A. Narrow the contract** — public sees published *additions*; removals/reorders appear live | **The three columns above** | **Recommended** |
| B. Dual-column working/published for `position`, `label`, plus soft-delete | Moderate: several columns, every write path forked | Not proportional to the problem |
| C. Full published snapshot of the source | Large: a second row set, or JSONB versions | **The architecture §33 says to stop before** |

**Recommendation: A, stated honestly rather than papered over.** The learner-facing contracts (§11,
§13, §14) are fully satisfied by A; only the public browsing view is partially satisfied.
**⚠️ And the residual is small in practice** — a curator mid-expansion is overwhelmingly *adding*, which
is exactly what A hides. **Do not silently claim Test 11 passes in full.**

## 7. §29.G/H — Eligibility and notification integration

**`Update available` becomes true when all three hold:**

1. the source has rows with `published_at` **newer than** the adopter's `source_synced_at` baseline;
2. those rows are relevant to that adoption;
3. the adopter has not already applied them.

**⚠️ Unpublished rows are invisible to drift, so ordinary edits cannot make anyone eligible** — Tests
1, 2 and 5 pass structurally rather than by convention.

**Notification integration:**

| Concern | Answer |
|---|---|
| Trigger | A scheduled sweep **over sets whose `last_update_published_at` moved since the last sweep** — not a blanket drift sweep. Daily is ample |
| Type | `ACTION_REQUIRED` — no new enum value |
| **Dedup** | **`(recipient, adopted_collection_id)` while a behind episode is open.** Create a notification only if the adopter is behind **and** has no unresolved Review Set update notification for that collection |
| Update B while still behind | **No second notification** (§15) — the episode is already open; the update-review surface shows the latest published state |
| Learner applies, then a new update publishes | Episode closed → **eligible again** (Test 7) |
| Fan-out failure | **Publication already succeeded.** `Update available` is computed from `published_at`, never from notification rows, so the banner is correct regardless. A retry creates the missing row; the dedup key makes it exactly one (Test 13) |

**⚠️ This supersedes the previous plan's drift-signature dedup.** That workaround existed only because
there was no publication event to key on. **With one, `source_synced_at`'s unreliable advancement stops
mattering for dedup** — episode-open state is derived from the notification's own resolution, not from
the sync baseline.

## 8. §29.D — Curator UX

**Placement:** on the Official Review Set detail page, **Admin/curator view only**.
**⚠️ Never on a learner-owned Study Plan** — gate on the existing Official-collection edit
authorization (§20); **create no new role system**.

**Unpublished-changes indicator (§9):** a lightweight state on the same page —
`Published` / `Unpublished changes` — computed as *"any row under this set with `published_at IS
NULL`"*. That is one existence query and it answers the curator's real question: *have my changes
reached adopters yet?*

**Enablement (§19):** enabled **only** when the set is already `PUBLIC` **and** at least one
unpublished row exists. **⚠️ Not enabled by metadata edits, view counts, adoption counts or
timestamps** — the stamp is on curriculum rows only, so those cannot arm it.

**Confirmation copy — counts only where provable (§8 of the brief):**

> **Publish Review Set update?**
> Your latest changes will become available to learners who adopted this Review Set.
> Their personal progress and changes will be preserved.
> *N topics added · M Subject Plans added*
> **[Publish update]** · Keep editing

**⚠️ Additions are countable from the unpublished rows themselves. Removals and "sections
reorganized" are NOT** — there is no removal or reorder provenance on the source. **Show additions
only; if none can be counted, fall back to the plain confirmation.** Do not manufacture the number.

## 9. §29.I — Atomicity and concurrency

**One transaction stamps every unpublished row and the root marker together.** A partial failure
rolls back, so no adopter sees a half-published curriculum (Test 12).

**Idempotency:** stamping is `WHERE published_at IS NULL`, so a repeat is a no-op on already-stamped
rows. **⚠️ Guard the root marker against a double-publish** — take the row lock the codebase already
uses (`findByIdForUpdate`) so two concurrent requests produce one boundary, not two (Test 14).

**⚠️ Notification fan-out must be OUTSIDE the publication transaction** (§21) — dispatch after commit,
using the repo's established `dispatchAfterCommit` pattern. **A notification failure must never roll
back a curriculum publication.**

**Adopter applying while a publish commits:** the apply reads published rows at its own transaction
start; a publish landing after simply leaves the adopter behind again, which is correct.

## 10. §29.J — Migration and backfill

**⚠️ The backfill is the highest-risk step, and getting it wrong makes every adopter instantly behind
or instantly up to date.**

**Rule: stamp every existing source row as PUBLISHED.**

- `UPDATE note_collection_items SET published_at = <collection created_at, or now>` for items under
  Official/public source collections;
- same for source child collections;
- `last_update_published_at = now` on already-public Official roots.

**Effect: nothing changes state on deploy.** Every adopter's eligibility is exactly what it was, and
no set arrives showing "Unpublished changes" for work done before the boundary existed.

**⚠️ Do NOT leave `published_at` NULL and treat NULL as published** — that inverts the semantics the
moment the first row is inserted, and every new addition would be published by default.

## 11. §29.L — Implementation slices

| Slice | Content | Migration | Notes |
|---|---|---|---|
| **P1 — Publication boundary foundation** | 3 columns + backfill; filter drift, adoption copy and `getPublic` to published rows; `Publish update` service + endpoint (atomic, locked, idempotent) | **Yes** | The whole contract lives here |
| **P2 — Curator UX** | `Publish update` action, `Unpublished changes` indicator, confirmation with additions-only counts | No | After P1 |
| **P3 — Notification integration** | scheduled sweep over recently-published sets; `ACTION_REQUIRED`; episode dedup; after-commit dispatch | No | **Was Stage 6; now unblocked** |

**⚠️ P1 and P2 do NOT block the notification work already in flight (§28).** The foundation,
Admin What's New, adoption count, Learning Connection and named Note-share notifications are all
independent and may proceed. **Only the Review Set update notification waits — and it was already
waiting.**

## 12. §30 — Test plan

| # | Test | Passes because |
|---|---|---|
| 1 | Ordinary edit does not notify | unpublished rows are invisible to drift |
| 2 | Ordinary edit creates no `Update available` | same |
| 3 | Publish creates eligibility | stamp moves past the adopter's baseline |
| 4 | Notify once | episode dedup on `(recipient, adopted_collection_id)` |
| 5 | 20 edits then one publish → one episode | eligibility is stamp-driven, not edit-driven |
| 6 | Update B while behind → no second notification | episode already open |
| 7 | Catch up, then a new publish → eligible again | episode closed on apply |
| 8 | **Publish** changes no count; **apply** changes no count *for the set the adopter already holds* | **⚠️ CORRECTED — see below** |
| 9 | Initial publication creates no fake update | no adopters exist to be behind |
| 10 | **New adopter during unpublished edits gets published state** | adoption copy filters on `published_at` |
| 11 | **Public viewer during unpublished edits** | **⚠️ PARTIAL — additions hidden, removals/reorders not (§6)** |
| 12 | Publication failure is not partially visible | one transaction |
| 13 | Notification failure leaves the update published | eligibility never reads notification rows |
| 14 | Double publish → one boundary | `WHERE published_at IS NULL` + row lock |

**⚠️ CORRECTION 2026-09-07 — Test 8 as originally written encoded a FALSE claim, and this document was
the fifth to carry it.** It read *"neither creates a `note_collections` row."* **Applying an update DOES
create rows**: `createSubjectAddition:2307` constructs a `NoteCollectionEntity` and sets its
`sourcePlanId` (`:2323`) for every newly-added child Subject Plan. The true invariant is narrower —
**applying never re-counts an existing adopter against the set they already hold**, while a
newly-added child Subject Plan legitimately gains one adopter of *that child*.

**⚠️ Test 8 must therefore assert BOTH halves, and a LEAF fixture proves neither.** The `v0.129.0`
guard written from the original claim used a leaf fixture, never entered the creating loop, and passed
while the claim was false — a `v0.130.0` pressure test disproved it. **Use a Goal fixture whose
published update adds a child Subject Plan**, and assert (a) the parent set's count is unchanged and
(b) the new child's count is 1.

**⚠️ Test 11 must be written to the narrowed contract, or it will encode a promise the architecture
does not keep.**

**Fixtures that prove nothing (state them in the tests):** a set with **no adopters** passes Tests 1–7
trivially; a publish with **no unpublished rows** passes Test 3 without exercising the stamp; a
**single-item** set passes Test 5 without exercising accumulation.

## 13. Genuine owner decisions

1. **⚠️ Accept the narrowed public-view contract (§6)?** Recommendation: **yes** — additions are what a
   mid-expansion curator produces, and options B and C are disproportionate. **This is the one decision
   that changes the architecture's size.**
2. **Backfill timestamp — `created_at` or `now`?** Recommendation: **the collection's `created_at`**,
   so a future "Updated" date (§24) is not uniformly the deploy date.
3. **Should `last_update_published_at` become the public "Updated" date (§24)?** Recommendation:
   **preserve the capability, ship no UI** — the brief says not to add it without approval, and it is
   the first trustworthy source for it.

## 14. Anti-drift

- **⚠️ Do NOT notify on every Note addition or reorder**, or treat raw drift as a published update.
- **⚠️ Do NOT use `updated_at` as the publication boundary.**
- **⚠️ Do NOT let notification delivery participate in publication atomicity**, or let notification
  rows define eligibility.
- **⚠️ Do NOT mutate learner plans on publish** — publication only makes changes reviewable.
- **⚠️ Do NOT overwrite learner Note content, reset progress, ConceptHealth or quiz history.**
- **⚠️ Do NOT propagate removals to adopters** — the apply path stays additive; `MOVED` stays
  report-only (`:2050`).
- **⚠️ Do NOT re-count an existing adopter on publish or apply.** **Publishing** inserts no collection
  row. **Applying DOES** — `createSubjectAddition:2307` calls `new NoteCollectionEntity()` and stamps
  `setSourcePlanId(sourcePlan.getId())` (`:2323`) for each newly-added child Subject Plan, which
  **legitimately** gains that plan an adopter. The invariant is *one learner stays one adopter of the
  set they already hold* — not *apply creates nothing*.
- **⚠️ Do NOT build Git-like UX, branches, diffs, rollback or semantic version numbers.**
- **⚠️ Do NOT auto-create a What's New Announcement on publish** (§25) — separate systems.
- **⚠️ Do NOT notify non-adopters** (§26).
- **⚠️ Do NOT expose unpublished curator work** to adoption or the public read.
- **⚠️ Do NOT treat NULL `published_at` as published** — the backfill exists to prevent exactly that.
- **⚠️ Do NOT add a new role system** — reuse Official-collection edit authorization.
- **⚠️ Do NOT block the notification foundation, Announcements, adoption count, or the Connection and
  named Note-share events.**
