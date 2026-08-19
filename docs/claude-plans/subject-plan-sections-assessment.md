# Sections inside a Subject Plan — architecture assessment

**Status:** assessment only, not approved, nothing implemented.
**Date:** 2026-08-18. **Method:** two cold-context agents, non-overlapping scope (backend model/API/adoption; frontend Builder/learner UX), both instructed to read real code and challenge the brief. They converged independently on the same root cause.

---

## 0. Headline — this feature already exists; one control is missing and one guard is absent

**Sections ship today, end to end:** the per-item `label` field, Builder authoring (rename, reorder, move-between, drag), learner rendering as collapsible cards with note counts and a `N% · M due` readiness pill, and documentation in `docs/features/collections.md`.

**What is missing is a way to create the second section, plus a guard on the first.** The brief asked for Sections to be built. They do not need building.

---

## 0b. Recovery — RUN 2026-08-18, and what it settled

The Engineering Mathematics plan was recovered with `docs/claude-plans/engineering-mathematics-section-recovery.sql`.

- **Step 0 (backup)** captured all 77 rows before any write. The table has `created_at` only — no `updated_at`, no history, no `@Version` — so this was the only rollback path.
- **Step 1 (preview)** confirmed `notes.subject` held the intended nine-way split exactly: Engineering Economics 11 · Differential Calculus 10 · Algebra 9 · Integral Calculus 9 · Analytic Geometry 8 · Trigonometry 8 · Numerical Methods 8 · Probability and Statistics 7 · Differential Equations 7 — summing to 77.
- It also found **eight of nine subjects contiguous, and one not**: Trigonometry holds 7 notes at positions 9–15 plus **one stray at position 76**, added after its original batch and sitting after Engineering Economics.
- **Step 2 (the write) ran and worked.** The nine sections are recovered, in curriculum order. Grouping is by label and section order derives from first appearance, so the split range did not affect either.
- **Step 2b (position normalization) was deliberately NOT run.** The stray note remains at position 76. Consequence: `PostSessionNextStepService` walks position order, so a learner progressing sequentially reaches that Trigonometry note **last**, after Engineering Economics, rather than with its section-mates. The fix is one drag in the Builder, which rewrites positions and self-heals — not worth a 77-row write.

### What this settled for the release

1. **The subject → section mapping is demonstrated, not assumed.** It held exactly on 77 real notes. That justifies both directions of the same rule: bulk generation carrying the batch subject into the section on write, and a retroactive *"Set sections from note subjects"* Builder action.
2. **The authoring workflow question is answered by the data.** The owner generates per area, in curriculum order, each batch landing contiguously. That is **notes-first, not skeleton-first** — so **empty sections are not needed**, and the label model is sufficient. **No new table, no migration.** This was the single input the two cold agents said would decide the model, and it decided it against the explicit-entity option.
3. **A residual worth noting, not designing around:** a note added after its batch lands at the tail. Section-aware bulk generation would still give it the right section, just the wrong position — the same one-drag fix.

## 1. Current implementation

**Persistence.** `note_collection_items.label` — `VARCHAR(120)`, nullable, per item, plus `position INTEGER NOT NULL`. Introduced in `V72` with the first Note Collections commit, where it was a per-item **caption** (`"Week 1"` / `"Week 2"` in the tests), never a grouping key. Grouping was retrofitted **frontend-only**; `git log -S"label"` on `NoteCollectionService.java` returns two commits, neither of them about sections. `docs/features/collections.md:61` — *"the backend does not interpret them"* — is TRUE against code.

**Backend write paths (exhaustive, 2):** `setOrder:1074` (the only user-facing writer) and `buildAdoptedItems:1838` (adoption copy). Plus `buildItems:1818`, which hard-sets `null`.
**Backend read paths (exhaustive, 3):** item response mapping, adoption copy, validation. **No backend feature reads `label`** — not next-step, readiness, export, analytics, or prompts.

**Grouping is derived, client-side, in two places that do NOT agree** — corrected 2026-08-18 by the UX pressure test: `collection-detail-page-client.tsx:142-177` (learner) and `study-plan-builder-page-client.tsx:111-129` (Builder). Blank label → a synthetic `"Ungrouped"` bucket. **But the two diverge, and the Builder is the side violating the documented contract:**

| | Learner (`:146`) | Builder (`:118`) |
|---|---|---|
| Unsectioned bucket | collected separately, **always pushed last** | **interleaved** at its first note's position |
| No labels at all | `hasSections === false` → flat list, no heading | one section headed `Ungrouped`, **with an editable input** |

`collections.md:71` documents the trailing-bucket contract and `:73` documents "cross-section drag is a no-op" — the Builder breaks both (`handleLeafDragEnd:1629` allows cross-section drag). **The Builder is a preview surface, so a curator cannot currently trust that what they arrange is what the learner sees.**

**API.** `PUT /collections/{id}/items/order` accepts `[{noteId, label}]`. **Multiple distinct labels with stable ordering are authorable today with zero backend change** — that is exactly what the Builders do.

**Adoption.** Label and relative order survive **every** path — plan adopt, Goal adopt (delegates per child), and idempotent re-adopt. Pinned by test at `NoteCollectionServiceTest.java:3036`.

---

## 2. Root cause — the `ALGEBRA · 77 notes` state is the only state the Builder can produce

1. A fresh plan has every item at `label = null` → one derived section named `Ungrouped`.
2. The Builder renders **every** section header as an editable input, `Ungrouped` included, with no special case (`builder:541`).
3. `handleRenameLeafSection` (`builder:1309-1319`) relabels every item whose derived section name equals the old name. With `oldName = "Ungrouped"` that matches **every unlabeled item** — all 77 — in one `PUT`.
4. Now no `Ungrouped` bucket remains and no second section exists, so the per-note "Move" dropdown ("all sections except mine") is **empty**. A second section can never be created.

**The sibling surface guards exactly this and the Builder does not.** `collection-detail-page-client.tsx:277` computes `isUngrouped` and renders a non-editable `<span>`; `:3022` early-returns on rename. Same bug class, guarded in one file, unguarded in the file the curator actually reaches.

**Why no create control exists anywhere reachable.** The only affordance that could ever create a section from nothing is the per-note Section combobox on the detail page — and it is **dead code**: `collection-detail-page-client.tsx:2647` is `const [organizeMode] = useState(false)` with no setter. `docs/features/collections.md:862` records the removal as intentional; it removed the product's only section-creation control **without replacing it in the Builder**.

**⚠️ The state is reversible and needs no migration.** Typing `Ungrouped` back into the Builder's section header maps to a null label and resets all 77 items. Undiscoverable, but it works — and it works *because* of the same missing guard.

**✅ CONFIRMED EMPIRICALLY, 2026-08-18.** The owner ran the query below against the Engineering Mathematics plan: it returned exactly one row, **`Algebra, 77`** — a single non-null label on every item. That is only producible by the mass-rename path above. The root cause is established, not inferred, and the intended shape (`Algebra 9`, `Trigonometry 8`, …) was never authored in the first place — it was overwritten in one blur event.

The query that confirmed it:

```sql
SELECT label, count(*) FROM note_collection_items
WHERE collection_id = '<engineering-mathematics-plan-id>'
GROUP BY label ORDER BY 2 DESC;
```
Result: `Algebra, 77`. Confirmed.

**Open recovery question, being read now:** `applyBulkGeneratedMetadataToNote:1072` writes `note.setSubject(normalizeSubject(preservedSubject))` — the curator's **batch subject**, not the LLM's suggestion — and `NoteCollectionItemResponse.subject` already exposes it. So if each area was generated as its own batch, the correct section for all 77 notes is **already stored on the notes**, and recovery is one mechanical `label := subject` pass rather than 77 manual assignments. If instead every batch ran as "Engineering Mathematics", the signal is degenerate and recovery is manual under any model. This changes recovery cost and the value of a "Set sections from note subjects" Builder action — it does **not** change the model recommendation.

---

## 3. ⚠️ The finding that actually blocks the workflow — bulk generation cannot assign a section

`BulkGenerateNotesRequest` has no section field. `NoteBulkGenerationService.addCreatedNotesToCollection:371-393` calls `addGeneratedItems(collectionId, ownerUserId, noteIds)` — **no label parameter** — and `buildItems:1818` hard-sets `label = null`, appending at the tail.

**So every bulk batch lands unlabeled in Ungrouped, and sectioning is only ever a manual post-pass.** This is the workflow that produced the 77 notes, and it is independent of the model choice: even with a perfect Section model, a curator would re-section by hand after every batch.

**The discarded information is precisely the section name.** The curator already types "Algebra" as the batch subject; `NoteBulkGenerationService:322` writes it to `notes.subject`, and `applyBulkGeneratedMetadataToNote:1064` confirms the LLM does not overwrite it. The membership write throws it away.

**This reframes the model question — see §4.**

---

## 4. Recommended model — and the reframe that decides it

Exactly **three** needs a label-only model cannot express (everything else on the brief's list works today or is an endpoint fix):

| Need | Label-only |
|---|---|
| **Empty sections** | **Impossible** — a section exists only as a value on ≥1 item |
| **Curator-controlled section order** | **Not representable** — order is derived from minimum item position |
| **Duplicate names / `Ungrouped` sentinel collision** | Structurally impossible to distinguish; case-sensitive grouping under an `uppercase` header compounds it |

The cold agents' joint conclusion was that this hinges on whether the workflow is **skeleton-first** (name nine areas, then fill them — which label-only cannot do) or **notes-first**.

**The reframe: fixing §3 converts the workflow from skeleton-first to notes-first, and the need for empty sections dissolves.** If Bulk Generate accepts a section name (defaulting to the batch subject already being typed), then generating the Algebra batch *creates* the Algebra section as a side effect. Repeat per area, in curriculum order, and nine sections exist in the right order with no skeleton and no empty state. Section order = order of first appearance = generation order = curriculum order.

**Recommendation: stage it, and do not buy the table yet.**

- **Now:** formalize labels, restore the create control, guard the sentinel, and **make bulk generation section-aware**. No migration, no new entity, two-level rule untouched.
- **Later, only if observed:** if after using it you genuinely need to reorder sections independently or hold a placeholder section, that is the evidence that justifies `note_collection_sections` + `items.section_id`. The cold agent sketched it (one table, one nullable FK, bounded adoption change, explicitly not a `NoteCollection`) and it remains available.

**Why staged rather than decided now:** the two loudest symptoms — the degenerate group and the manual sectioning — are **bug fixes under either model**. Letting them argue for a schema would be buying a table to fix a missing `if` statement. This project's own precedent is `v0.87.0`, which opened against a hypothesis and rescoped when a probe falsified it.

---

## 5. Recommended UX

**Builder (the only authoring surface — `collections.md:855` keeps authoring out of detail).**
Port the existing `SuggestionCombobox` from the dead detail path into `LeafSortableNoteCard`, replacing the "Move" `<select>`: existing section names as suggestions, free-type to create, clear to return to Ungrouped. **~15 lines, zero new state** — because a section exists the instant one note carries the name. "Add Section" would need draft-section state, a local-only rename path, and reconciliation on every refresh: machinery that buys convenience, not capability.

**Learner.** No work. Collapsible cards, counts, readiness pills and title-peek already render.

**Unsectioned notes.** Keep the heading, render only when non-empty (both surfaces already do). It carries a count and readiness pill like every other group, it is the drop target for pulling a note out, and the bucket is deliberately appended last — a headingless trailing run is indistinguishable from the previous section's contents. Consider renaming the *learner-facing* copy from `Ungrouped` to `Other notes`; it must stay one constant, since the string doubles as the sentinel.

---

## 6. Ordering semantics

`position` is a dense integer over the whole collection; the only unique constraint is `(collection_id, note_id)`. `setOrder` rewrites every row on every call. Section order is derived from first appearance and assumes same-label items are contiguous — **an invariant nothing on the server enforces**.

Two real defects here: `findByCollectionIdOrderByPositionAsc` has **no `, item.id asc` tiebreaker** while its sibling does, so ties order nondeterministically; and `collections.md:74` claims a within-section move *"never renumbers another section's positions"*, which is false at the persistence layer (harmless, since relative order is preserved, but the doc describes an invariant the code lacks).

---

## 7. Readiness — nothing new is needed, and this is verified

`GET /collections/{id}/note-concept-counts` **already returns per-note `{totalConcepts, masteredConcepts, dueConcepts, notPracticedConcepts}`**. Section readiness is pure client-side summation (`aggregateSectionReadiness`). **No new persisted signal, no ConceptHealth change, no Section mastery entity** — the constraint in the brief is already satisfied by what ships.

---

## 8. Adoption and backward compatibility

Labels and relative order survive every adoption path, pinned by test. Plans with no labels render as a flat list — unchanged. **No migration is required for the recommended scope.**

Two caveats worth knowing: a **single-note section vanishes silently** on adoption if its note is skipped (private source or copy failure); and `ShareService.createRemixedNote` copies a note, not membership, so remix is not a label path.

---

## 9. Migration

**None for the recommended scope.** Note for any future explicit model: the table has `created_at` only — no `updated_at`, no history, no `@Version` — so prior label values are **unrecoverable**, and a backfill from `DISTINCT label` would seed the Engineering Mathematics plan with one degenerate 77-note section. Re-sectioning those 77 is manual (or `notes.subject`-driven) under **either** model, so that cost must not weigh on the decision.

---

## 10. Risks and edge cases

- **Duplicate names silently merge in the Builder.** The learner surface has an explicit merge-confirmation modal for the identical operation; the surface a curator can reach does not. Structure-loss risk on a 77-note plan.
- **Item 7 inherits the whole-collection write exposure.** "Set sections from note subjects" goes through the same `setOrder` path as every other edit, so it carries the same last-write-wins risk below — a curator running it while a bulk-generation batch is finishing could clobber the batch's new rows. Not a reason to add `@Version` in this release; a reason to disable the action while a batch is in flight, or to refresh immediately before it runs.
- **No optimistic concurrency anywhere** — no `@Version` on either entity, and edits are whole-collection PUTs with a 500 ms debounce. Two tabs, or a debounced label edit racing a running bulk batch, is **last-write-wins over the entire collection**.
- **`maxLength` 150 in the Builder vs 120 in the backend and DDL** — a 121–150 char name 400s with a rollback.
- **`AccountDataExportService.toCollection:110-122` drops `label` entirely** — user-authored data missing from the data export today.
- **Case-sensitive grouping under an `uppercase` header** — `"Algebra"` and `"algebra"` are two sections that render identically.
- **Mobile inconsistency** — learner collapses at 1024px, Builder at 640px, two mechanisms; `defaultSectionExpanded` is computed once with no resize listener.

---

## 11. Recommended release scope

**In (smallest coherent release that unblocks the real workflow):**
1. Guard the synthetic bucket in the Builder — non-editable header, reserved-name check on rename. *This alone prevents recurrence.*
2. Port the Section combobox into the Builder — restores section creation.
3. Merge confirmation on rename-into-an-existing-name, mirroring the learner modal.
4. **Bulk generation accepts a section name**, defaulting to the batch subject (§3) — the item that actually removes the manual pass.
5. `maxLength` 150 → 120.

**Out, explicitly:** `note_collection_sections` table; "Add Section" scaffolding; delete-section UI (rename-to-Ungrouped works); bulk multi-select assign; the export `label` gap; the ordering tiebreaker; optimistic concurrency; mobile breakpoint unification. Each is real; none blocks authoring the review set.

---

## 12. Documentation to update if approved

`docs/features/collections.md` (the create control, the reserved bucket, and the false `:74` renumbering claim), `docs/features/bulk-generation.md` (section assignment), `docs/features/library.md` if the Builder surface changes, `RELEASES.md`.

---

## Recommendation

**Yes — formalize the existing mechanism, but the framing "build Sections" is wrong: Sections already ship, and the release is a create control, a guard, and a section-aware bulk path.** Do not add a table yet. The two symptoms driving this scoping are bug fixes under either model, and buying persistence to fix a missing guard would be paying schema for an `if`. Ship the staged scope, use it to build the Civil Engineering set, and let the need for empty sections or independent ordering prove itself before it is bought.

**Blocking input needed from the owner:** run the §2 query, and confirm whether generating each area as its own bulk batch (which §3 would make section-forming) matches how you actually want to author.

---

# ADDENDUM — Builder at curriculum scale (A–E)

**Added 2026-08-18** after the owner reported five adjacent Builder limitations while continuing to author the CE Review Set. Investigated directly rather than by cold agent: the questions are specific rather than exploratory, and the two prior agents' findings are the context a fresh agent would have to rediscover at full cost.

## The single most important finding in this addendum

**Three of the five proposals are far cheaper than they look, because the machinery already exists**, and one of them changes the release boundary:

| | Assumed to need | Actually needs |
|---|---|---|
| **A** program-scoped picker | server-side filtering, new API | **nothing server-side.** `NoteListItemResponse.applicablePrograms` already ships, and `courseProgram` is already on both the detail and summary collection responses. `filterPickerNotes` (`:141-153`) already filters client-side over title/subject/courseProgram/tags — it simply does not consider `applicablePrograms` |
| **B** section destination in Add Notes | a new selection model | **the picker is already multi-select with a selected tray.** `selectedIds: Set<string>`, `toggleSelected`, `selectedNotes`, and `resultNotes` excludes selected ids so they visibly move out of the results — exactly the `SELECTED (8)` shape proposed. `handleAddLeafNotes(targetId, noteIds)` already takes a batch. B is **one destination control** beside a tray that exists |
| **D** bulk Move to Section | a batch API | **zero new API.** `setOrder` is already whole-set-or-nothing (`validateSubmittedSetMatchesCurrent:1780`), so moving 20 notes is *the same single PUT* as moving one |

**But D's other half is the opposite.** `DELETE /{id}/items/{noteId}` (`NoteCollectionController:275`) is the only removal endpoint, and `removeItem:1041-1054` does a **full position rewrite of the whole collection per call**. Removing 20 notes is 20 round trips, 20 transactions and 20 O(n) rewrites — O(n·m), and **not atomic**: a partial failure leaves the plan half-modified. Bulk Move and bulk Remove are not one feature.

## 1–2. Add Notes discovery, and multi-program representation

**Scope source: the Goal's `courseProgram` is the right one, with one caveat.** It is the level that carries curriculum intent, it is already exposed, and a Subject Plan inherits context from its parent rather than declaring its own audience. **The caveat the owner asked about is real:** a child Subject Plan *also* has a `courseProgram` column, so the two can disagree. Recommendation: **scope from the Goal, ignore the child's value for filtering, and never write either from the picker.** A child with a conflicting value is a data smell to surface later, not a filter input.

**Filtering must be a default, not a constraint.** Hidden-by-default with a visible "Show all programs" escape. A curator legitimately pulls a General Education note into a CE review; a hard filter would make that impossible and would be discovered as a bug.

**Null Goal program → no filtering**, as proposed. Correct, and it is also the backward-compatible path for every existing collection.

**Compact representation.** `Algebra · 11 programs` is right for mobile, with the full list behind a tap. Deriving it needs no request — `applicablePrograms` is already on each row. **Do not render program chips per card**; at 77 rows that is the density problem the owner flagged in B.

**⚠️ Performance, and it is pre-existing:** the Builder calls `listNotes()` with **no limit** (`:1166`, `api.ts:4395`) and filters the entire library client-side. Program filtering makes the *displayed* set smaller but does not reduce the payload. For a curator with thousands of notes this is already the wrong shape — but it is not caused by Sections and should not be fixed under their banner.

## 3. Section assignment during Add Notes

**Recommended, and small.** One destination control beside the existing selected tray: existing sections, `No section`, and — only if it stays one control — `+ Create section`. `handleAddLeafNotes` already receives the batch; it needs to pass a label through the same `setOrder` call it already makes.

**Do not put a section selector on every result card.** The owner is right about density, and the selected-tray pattern already in the picker is the correct home.

**On inline creation:** include it. Without it the curator must leave the picker to create a section and come back, which is the exact round trip B exists to remove. It is a free-text combobox, not a modal — the same primitive recommended for the leaf Builder.

## 4. Sections in the parent Build page

**Real, and confirmed:** `SortableSubjectBlock` already has a `collapsed` prop and renders note cards when expanded (`subject.items.map`, two call sites). A 77-note plan is 77 flat cards there today.

**But it is a second surface, and the curator can do all section work in the leaf Builder.** Recommendation: **defer**, and when it lands, nest the existing leaf section block inside the existing subject block rather than inventing a third collapsible primitive. Note the pre-existing inconsistency to resolve at that point: the learner collapses at 1024px, the leaf Builder at 640px, via two different mechanisms.

## 5–6. Bulk selection and bulk actions

**Bulk Move to Section: cheap, and the natural pair to D's selection model.** One PUT, atomic, no new endpoint.

**Bulk Remove: not cheap, and needs an endpoint** (`DELETE /{id}/items` taking a body, or a `POST /{id}/items/remove`) to be atomic and O(n) rather than O(n·m).

**Move to another Subject Plan: defer.** It is remove-from-A + add-to-B across two collections with no transaction spanning them, and a partial failure loses the note's placement silently. It needs its own design, not a checkbox.

## 7. Desktop and mobile

Selection needs a visible mode toggle on touch (`Select`) rather than long-press, which collides with scroll. The action bar should be a sticky footer on mobile and inline on desktop. The repo already has the ▲▼ button pairs beside every drag handle (`aria-label="Move … up"`), which is the established non-drag path and the pattern bulk actions should extend.

## 8. Accessibility and keyboard

Checkboxes must be real focusable inputs with labels, not click-target divs. The existing ▲▼ buttons are the keyboard-accessible reordering path and must stay. Announce selection count via a live region. **The existing drag handles already carry `aria-label`s** — match that standard rather than lowering it.

## 9. Selection state across search and filter

**Keep selection across query changes — which the picker already does**, and visibly: selected notes are excluded from `resultNotes` and rendered in the tray, so they cannot be lost behind a filter. **Preserve that behaviour if program filtering is added**: a note selected before a filter change must remain selected and visible in the tray even when the filter would now hide it, or the curator loses work silently.

## 10. Confirmation and destructive-action rules

**"Remove" for collection membership, never "Delete".** The leaf Builder already says Remove (`handleRemoveLeafNote`). Bulk removal must state the count and the scope explicitly — *"Remove 12 notes from this Subject Plan? The notes stay in your library."* — because the canonical-deletion fear is exactly what bulk selection amplifies. Section deletion is not destructive to notes (they fall to Unsectioned) and should say so rather than warn.

## 11. API changes for true batch operations

- **Bulk Move to Section: none.** `setOrder` is already whole-set.
- **Bulk Remove: one endpoint**, or accept O(n·m) and non-atomic.
- **Add with section: none** — `addItems` then the existing `setOrder`, or a label on `AddNoteCollectionItemsRequest` to make it one call.
- **Program-filtered picker: none** client-side; a server-side `?programId=` only if payload size forces it.

## 12. Transaction and error behaviour

`setOrder` is `@Transactional` and whole-set, so bulk Move is **already atomic**. The per-note DELETE loop is **not** — this is the strongest technical argument for a batch remove endpoint rather than a client loop.

**⚠️ Correction from the pressure test, and it is in the plan's favour:** the *add* race is **not** silent loss. `validateSubmittedSetMatchesCurrent:1780` rejects a stale `setOrder` with `ORDER_SET_MISMATCH`, so a curator editing while a bulk batch lands gets a visible error and a refresh, not a clobber. **Last-write-wins is real only between two Builder tabs.** That removes an argument against shipping the section-aware and set-from-subjects items beside live Builder editing.

**⚠️ The concurrency exposure widens with bulk operations.** There is **no `@Version` on either entity**, and edits are whole-collection PUTs behind a 500 ms debounce — so two tabs, or a bulk action racing a running bulk-generation batch, is **last-write-wins across the entire collection**. Today that costs one label; with bulk actions it can silently revert a whole reorganisation. If bulk ships, optimistic concurrency stops being optional.

## 13. Performance at 50–100+ notes

Every label edit PUTs all 77 rows; `removeItem` rewrites all positions per call; the picker loads the entire library. None of these is fatal at 77 and all degrade predictably. **The one that degrades worst is bulk remove via a client loop** — quadratic and non-atomic — which is why it is the item that must not ship without its endpoint.

---

# Revised release boundary

**Principle: fix the inflow before building the repair tools.** Most of the pain in this addendum is the cost of *reorganising notes that arrived in the wrong place*. Bulk selection, bulk move and parent-page section editing are all repair machinery. If notes arrive already sectioned, the demand for repair drops sharply — and how sharply is measurable rather than assumable. Building repair first would bake in the assumption that notes will keep arriving unsectioned.

## Necessary to make Sections genuinely usable — the next release

**Revised 2026-08-18 after a cold UX pressure test**, which corroborated the dropped set-from-subjects item as its own lead finding, **disproved one cost estimate in this document**, and found a defect that exists only when two items ship together.

1. **Guard the synthetic `Ungrouped` bucket** — non-editable, and reject it as a typed name. Without this the original incident recurs. *Blocking.*
2. **Port the section combobox into the Builder** — restores section creation. **Two required details, both from the pressure test:**
   - **⚠️ Key the card `${noteId}:${item.label ?? ""}`, not `noteId`.** The ported control carries local `labelValue` state plus a debounced auto-save. `LeafSortableNoteCard` is currently keyed by note id, which is **stable across `refreshBuilder`** — so after item 3's rename mass-relabels a section, every card keeps its stale `labelValue`, the effect sees a difference, and it schedules `onLabelChange(noteId, staleValue)`, **silently reverting the rename one note at a time.** `LeafSectionBlock` is already keyed `${section.id}:${section.name}`; match it. **Items 2 and 3 are each correct alone and wrong together — this is the whole-release-only class the pre-signoff pressure test exists for.**
   - **Snap a typed case-variant onto the existing option.** Picking is already typo-proof (`SuggestionCombobox.matchesOption` normalizes), but free-typing `algebra` beside `Algebra` mints a second section that renders **identically**, because grouping is case-sensitive while the learner header is `uppercase`. Fix in the combobox, not in `buildLeafSections` — case-folding the grouping is the riskier change.
3. **Merge confirmation on rename**, mirroring the learner modal.
4. **`maxLength` 150 → 120.**
5. **Section-aware bulk generation — as an EDITABLE field pre-filled from the batch subject.** *Revised: not a hidden coupling.* A silent subject→section coupling gives the curator no override and no way to prevent the degenerate case this document already identified (every batch run as "Engineering Mathematics"). The pre-fill is free — `batch.subject()` is in hand — and the field costs one input plus a nullable label threaded through `addGeneratedItems` / `addItems` / `buildItems` (which hard-sets `null` at `:1806`).
6. **"Set sections from note subjects" Builder action.** One button, ~10 lines, **no API and no migration**: `leafItems.map(i => ({ ...i, label: i.subject?.trim() || null }))` through the existing `persistLeafItems`. Provably safe on length — `notes.subject` is `VARCHAR(64)` against a 120-char label cap, so it **can never 400**. `NoteCollectionItemResponse.subject` already ships and the frontend already reads it.
7. **Desktop section collapse — ~2 lines, and load-bearing rather than cosmetic.** `LeafSectionBlock`'s collapse is `sm:hidden` / `hidden sm:block`, so it exists **only below 640px**: on desktop, where the curator works, a 77-note plan is an **uncollapsible 77-card page**. This compounds item 5 — because `addItems` appends at `max(position)+1`, section order *is* generation order, and repairing a wrong order means dragging a section header across exactly the page that cannot collapse.

**⚠️ CUT: "section destination in Add Notes" (former item 6).** The pressure test **disproved this document's cost estimate for it.** The addendum claimed `handleAddLeafNotes` "needs to pass a label through the same `setOrder` call it already makes" — **there is no `setOrder` call there**; it calls `addCollectionItems` then `refreshBuilder`. So it needs a second whole-collection PUT after every add, or an API change. `AddNotesModal` also has **two call sites** with different section vocabularies, and the control only helps when an entire added batch belongs to one section — a multi-area add still needs several picker rounds. **Item 6 covers all of those cases for less.**

### Why this list is right, with the interaction count

- **A plan generated after this ships:** every batch lands labelled, contiguous, in generation order. **Repair interactions: zero.** Multi-select would have no work to do — which is why deferring it is correct.
- **Any batch that lands unsectioned** (notes generated before this release, notes added via the picker, a batch where the curator typed the plan name as subject, or any later re-cut): per note the curator scrolls, opens the combobox, types, and waits on a 500 ms debounce → a whole-collection PUT → `refreshBuilder`, which re-fetches the collection **and the entire note library**, with every section disabled meanwhile. **~230 UI actions and 77 serialized, UI-blocking round trips for a 77-note plan.**

**Item 6 collapses that second case from ~230 interactions to one.** That is the whole argument, and it is why multi-select can still wait.

## High-value, belongs beside it — but the release after

- **A. Program-scoped picker + `· N programs`** — improves discovery quality, not Sections usability. Client-side only.
- **C. Sections in the parent Build page** — real, but the leaf Builder covers the authoring need today.
- **D. Multi-select + bulk Move** — cheap on the API, but needs a selection model, an action bar, and confirmation rules. **Its value is measurable only after 5–6 land.**

## Deferred, with reasons

- **Bulk Remove** — until its endpoint exists; a client loop is quadratic and non-atomic.
- **Move notes to another Subject Plan** — cross-collection with no spanning transaction.
- **E. Multi-item drag — recommended against, not merely deferred.** Select → Move already covers it; drag rewrites every position anyway, so multi-drag adds state without reducing writes; and it is poor touch ergonomics on the surface where a 77-note plan hurts most. **No use case in this addendum requires it.**
- **Optimistic concurrency (`@Version`)** — not needed for 1–6, but a prerequisite for bulk actions.
- **Picker pagination / server-side filtering** — pre-existing, unrelated to Sections.

## Owed alongside the release — found by the pressure test, cheap, and easy to lose

- **⚠️ Adoption sequencing guidance, which nothing else in this document covers.** `persistAdoptedPlan:1398-1402` returns `alreadyAdoptedResponse` on any existing `sourcePlanId` — **re-adopt is a hard no-op**. Anyone who adopts the CE Goal before it is sectioned keeps the unsectioned copy **forever**, and under the snapshot rule that is correct behaviour, not a bug. **So: finish sectioning before publishing or promoting the Goal.** This is guidance the release owes the curator, not code.
- **Builder / learner grouping parity.** Make the Builder match the documented contract: unsectioned bucket **pinned last**, non-editable, and suppressed entirely when it is the only group (as the learner already does via `hasSections`). This is most of item 1 anyway.
- **Name the bucket "Not in a section", and do NOT rename it to "Other notes" as this document earlier floated.** The sentinel doubles as the reserved name, so displaying a different string requires a **two-string** reserved guard or a curator can type the displayed name and mint a real section that renders identically — the exact collision that started this assessment. "Not in a section" names the state rather than inventing a category.
- **⚠️ `CollectionLabels` profile-maps every level except this one.** `frontend/lib/collection-labels.ts` maps Goal / Subject Plan / Note per profile — a TEACHER sees Course / **Unit** / Lesson Plan — but carries **no field for the section label**, which is a hardcoded constant in two files. A teacher therefore sees Course → Unit → **Section** → Note, where "section" already means a class cohort. Either add `sectionSingular` alongside the others, or accept the collision **knowingly** rather than by omission.
- **The empty-section placeholder is unreachable dead code.** `LeafSectionBlock:583` renders *"No notes in this section yet. Drag notes here from another section"* — but a derived section cannot be empty. It advertises precisely the capability curators go hunting for and can never reach it. Remove it, or the create control ships beside a promise it cannot keep.
- **⚠️ Deferring bulk Move rests on a measurement that does not exist.** This document says its value is "measurable only after 5–6 land" — but **sectioning emits zero analytics**, so nothing will be measurable. This is the same shape as the `v0.83.0` checkpoint that had to read a column directly because Public Library filtering emitted no events. **Either fire one event on section assignment, or restate the deferral as a judgement rather than a measurement.** Do not leave it claiming evidence it will not have.
- Two one-liners: `AccountDataExportService.toCollection:110` drops `label`, so section structure is missing from the data export; and `defaultSectionExpanded` starts `null`, giving desktop learners a brief collapsed→expanded flash.

## Answer to the framing question

**Has the Builder outgrown its small-collection interaction model?** Partly — but less than the addendum implies. Its *authoring* model is sound; what it lacks is a create control, a guard, and any way for notes to arrive pre-sectioned. Its *bulk-editing* model genuinely has not been built. Fixing inflow first tests whether bulk editing is needed at all, at a fraction of the cost of assuming it is.
