# 2026-09-06 — Study Plan Builder: a section label can wedge the page in an unbounded write→refresh loop

## Status: diagnosis only. No code changed. No release opened.

**Reported** by the owner 2026-09-06: `/collections/85078db3-…/builder` "keeps on refreshing" after a
section name was accidentally set to a long pasted value. **Self-resolved** by deleting the note that
carried the label — which is itself evidence: removing one card stopped it, so the driver was a
per-card effect and not a page-level poller, an auth loop, or ISR.

Verified by reading `frontend/app/collections/[id]/builder/study-plan-builder-page-client.tsx`
(2551 lines) and `frontend/lib/collection-labels.ts` at `releases/v0.122.0`. Line numbers below were
re-checked at that HEAD; the builder file was unchanged from when the outage was reported.

⚠️ **The offending row was deleted before it could be read, and the Render MCP server was
unreachable throughout (`ENOTFOUND mcp.render.com`), so nothing here rests on production data.**
The loop mechanics are proven from code. The *ingress* — how the triggering value came to exist — is
**UNRESOLVED**, and is marked as such in §4 rather than guessed.

---

## 1. The structural defect (one sentence)

`LeafSortableNoteCard`'s auto-save effect decides whether a write is still pending by comparing
`item.label` against a **locally normalised** form of its own input, but the value that actually
reaches the server is chosen by a **different** normalisation in `handleLeafLabelChange` — so when
those two disagree the guard never clears, and there is **no attempt cap, no backoff, and no failure
short-circuit** to stop it retrying.

The effect (`:445-455`, **VERIFIED**):

```js
useEffect(() => {
  if (disabled || editing) return;
  const nextLabel = labelValue.trim().replaceAll(/\s+/g, " ");   // collapses internal whitespace
  if ((item.label ?? "") === nextLabel) return;                  // …but compares to the RAW label
  const handle = globalThis.setTimeout(() => onLabelChange(item.noteId, nextLabel), 500);
  return () => globalThis.clearTimeout(handle);
}, [disabled, editing, item.label, item.noteId, labelValue, onLabelChange]);
```

⚠️ **`onLabelChange` is `handleLeafLabelChange`, declared as a plain arrow function at `:1646` and
therefore re-created on every render** — it is not `useCallback`-wrapped. It sits in the dependency
array, so **this effect re-runs on every render of the page**, not only when the label changes. That
is what converts a stale guard into a live loop: each refresh re-renders, which re-runs the effect,
which re-arms the 500 ms write.

---

## 2. Loop A — the write is REJECTED (label longer than the limit)

1. Effect computes `nextLabel` and schedules `onLabelChange`.
2. `handleLeafLabelChange` (`:1646-1657`) → `moveLeafNote` → `persistLeafItems` (`:1514`).
3. `setCollectionItemOrder` is rejected by the backend: `NoteCollectionService.validateOptionalLabel`
   throws `"Collection item label must be 120 characters or fewer."` when `label.length() > 120`.
   ⚠️ **It rejects; it does not truncate** — and `note_collection_items.label` is `VARCHAR(120)`
   (`V72:17`).
4. `persistLeafItems` catches and calls `recoverLeafAfterFailure` (`:1368-1378`), which restores
   `previousItems`, sets an inline error, and **calls `refreshBuilder()`**.
5. `refreshBuilder` (`:1278`) refetches the collection **and `listNotes()` — the user's entire note
   list** — then sets state. Re-render.
6. `labelValue` still holds the over-long text (`useState` seeds once, at mount, `:433`), and
   `item.label` was just restored to its old value, so the guard is still false. **Back to step 1.**

**Never converges**: the retry is unconditional, and the thing that would end it — the write
succeeding — is exactly what cannot happen.

## 3. Loop B — the write SUCCEEDS but is a no-op (label differs from its collapsed form)

This one needs the case-snap to see it:

```js
const exactExistingName = leafSections.map(s => s.name)
  .find(name => name !== UNGROUPED_SECTION_NAME
             && normalizeSectionValue(name) === normalizeSectionValue(trimmedLabel));   // :1652
const targetSectionName = exactExistingName ?? (trimmedLabel || UNGROUPED_SECTION_NAME); // :1655
```

`normalizeSectionValue` is `trim → collapse whitespace → lowercase`
(`lib/collection-labels.ts:137-139`, **VERIFIED**). So if the stored label is
`"Planning␣␣␣␣␣Development"` and the effect requests the collapsed `"Planning␣Development"`, the two
**normalise identically** — the snap fires and `targetSectionName` becomes the **raw, uncollapsed**
label. The write sends the label back **unchanged**, the server stores what it already had,
`refreshBuilder()` returns the raw value, and the effect's guard is still false.

⚠️ **The two normalisations undo each other.** The card wants whitespace collapsed; the case-snap
insists on the existing spelling. Neither is wrong in isolation, and the snap is deliberate — it
exists to stop lookalike sections (`"Cash  and Receivables"` beside `"cash and receivables"`), a
defect `handleSetSectionsFromSubjects:1662-1664` documents by name.

**Never converges**, for the opposite reason to Loop A: every write succeeds and changes nothing.

---

## 4. ⚠️ Which loop fired on 2026-09-06 is NOT established, and the ingress is UNRESOLVED

The reported value was pasted from what looks like a two-column source (a section name, a long run of
whitespace, then a description truncated mid-word at `"lan"`). Measured: **137 codepoints / 138 UTF-16
units raw**, and **113 / 114 collapsed**.

- **114 is under the 120 limit**, so *that exact string*, once collapsed, would have been **accepted** —
  which argues against Loop A, unless the true pasted text was longer than the copy supplied.
- Loop B needs an **uncollapsed** value already stored. ⚠️ **Every builder write path collapses
  whitespace before sending** — the card effect (`:449`), the combobox (`:535`), the section rename
  (`:1631`) and set-from-subjects (`:1672`) — so the builder **cannot normally create its own
  trigger** through the UI.

⚠️ **The backend does not collapse.** `validateOptionalLabel` trims and length-checks only
(`normalizeOptionalText` is `trim` + blank→null). So an uncollapsed label can be stored by any writer
that is not the builder. **Candidate ingress routes, none confirmed:** Review Set adoption's
`copySourceItems` (copies a source label verbatim), bulk generation assigning a section from
`notes.subject`, a direct `setCollectionItemOrder` call, or an older client.

**Do not close this by picking one.** Both loops are real and independently reachable from the code as
written; the fix in §6 addresses the shared cause rather than either symptom.

---

## 5. Blast radius

- **Per page load, not per user action.** Any curator opening the builder for an affected collection
  gets it, with no click required.
- **Each iteration refetches the entire note list.** `refreshBuilder` calls `listNotes()` unless
  `skipNotes` is passed, and neither the failure path (`:1374`) nor `persistLeafItems` (`:1541`)
  passes it. On a large library that is a heavy request every ~500 ms–1 s, from every affected client.
  ⚠️ This is a **client-driven load amplifier against the same backend** that the 2026-09-05 outage
  showed has no headroom (`docs/claude-findings/2026-09-05-prod-outage-public-catalog-unbounded-read.md`).
- **Silent.** No cap, no toast, no console error on Loop B; Loop A sets an inline error that is
  re-rendered away on each cycle. It presents only as a page that will not stop reloading.
- **Adoption propagates it.** If an uncollapsed label lives on a published source plan, every learner
  who adopts it inherits the wedge.

---

## 6. What a fix has to do (not a plan — see routing below)

1. **Make one normalisation canonical** and apply it on both sides, so the effect's guard compares
   against the form the write path will actually produce. The mismatch is the defect; whitespace is
   only how it surfaces.
2. **Bound the retry.** The library poller already carries this idea —
   `LIBRARY_GENERATION_POLL_MAX_TICKS = 100`, commented *"Absolute backstop so a wedged backend can
   never poll forever."* The builder's auto-save has no equivalent. A failed write must not be retried
   unconditionally.
3. **Memoize `handleLeafLabelChange`** (`:1646`). Re-creating it every render puts a changing
   identity in the effect's dependency array, which is what re-arms the write on every render.
4. ⚠️ **Do NOT remove the case-snap** (`:1652`). It prevents lookalike sections and its removal would
   reintroduce a defect the code documents at `:1662-1664`.
5. ⚠️ **Do NOT "fix" this by collapsing whitespace in the backend.** That silently rewrites stored
   learner data on an unrelated write path, and it would mask ingress rather than close it.

**Pre-declared guard:** a fixture whose label is already single-spaced and under 120 chars **passes
under both the defect and the fix and proves nothing**. The discriminating fixtures are (a) a stored
label containing a double space, asserting the write is issued **at most once**, and (b) a label whose
write is rejected, asserting **no second attempt**. This repo has shipped two silent no-ops whose tell
was a behaviour change with no test exercising it.

**Verification tier:** frontend-only, no contract change, no migration → a single `advisor()` call on
the diff. **Routing: Claude Code inline** — one component, one handler, one shared helper.

---

## 7. Detection query (read-only; run when the Render MCP is reachable)

Finds every label that can drive Loop B, across all collections:

```sql
SELECT i.collection_id, c.title AS plan_title, i.label,
       char_length(i.label) AS label_chars, count(*) AS notes_affected
FROM note_collection_items i
JOIN note_collections c ON c.id = i.collection_id
WHERE i.label IS NOT NULL
  AND i.label <> regexp_replace(btrim(i.label), '\s+', ' ', 'g')
GROUP BY i.collection_id, c.title, i.label
ORDER BY notes_affected DESC;
```

A non-empty result also **answers the §4 ingress question**: any row it returns was written by a path
that does not collapse, and `collection_id` plus the source plan will say which.

---

## 8. Obligations

- This file needs a **Backlog Index row** (kickoff step 8 names `docs/claude-findings/` because
  incident files have gone unindexed there before).
- ⚠️ **`v0.119.0` was Curator Bulk Regeneration and the tree is now at `v0.122.0`.** Bulk paths that
  assign section labels are a candidate ingress route in §4 and should be checked against rule 5
  above before this is closed.
