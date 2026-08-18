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

**Grouping is derived, client-side, in two places** that agree: `collection-detail-page-client.tsx:142-177` (learner) and `study-plan-builder-page-client.tsx:111-129` (Builder). Blank label → a synthetic `"Ungrouped"` bucket.

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
