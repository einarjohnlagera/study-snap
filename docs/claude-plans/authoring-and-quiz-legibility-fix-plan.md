# Plan — Five authoring & quiz legibility fixes

**Status:** PLAN ONLY — nothing implemented. Written 2026-09-05, revised 2026-09-05 to incorporate
owner product tightening.
**Origin:** five owner reports from real use (screenshots), 2026-09-05.
**Owner tightening:** incorporated in full. Product decisions in §12 of the owner's response are
treated as settled; §§1–5 below now state behaviour, not options.

**⚠️ TWO OWNER PREMISES ARE CONTRADICTED BY THE CODE — see §10.** The owner asked to be told rather
than have different semantics quietly substituted, so §10 is the first section to read. Neither
finding changes a product decision; both change what the plan can truthfully promise.

---

## 1. Challenge Quiz assertion questions

**Decision: BOTH halves ship.** Not deferred to corpus aging.

**Diagnosis (unchanged).** A `Statement N:` splitter already ships and is already live on this
surface (`frontend/components/study-pack/quiz-question-text.tsx`, used at
`app/study-packs/[id]/challenge-quiz/page.tsx:2289`). It separates the statements but **swallows the
trailing interrogative onto the last statement's line**, because `text.split(STATEMENT_RE)` pairs
each label with everything up to the next label (`:46-53`). Upstream cause:
`backend/src/main/resources/prompts/study-pack-v1/challenge-quiz-developer.txt:46` models the whole
pattern inline with no newline instruction.

**Ships:**

1. **Prompt-time** — newline guidance at `:46` so new questions carry real `\n`. The renderer already
   honours newlines (`:14-22`, `:31-38`), so compliant questions bypass the heuristic entirely.
2. **Presentation-time compatibility path** — break a trailing interrogative onto its own line for
   already-generated questions. **Narrow to the known assertion shape:** fires only when a Statement
   label was matched *and* the final segment ends in a sentence-final `?` *and* the preceding
   statement body is non-empty.

**Rationale for shipping (2), recorded:** NoteLib never auto-regenerates Study Packs, so malformed
assertion questions persist indefinitely. Aging out is not a real path.

**⚠️ This is a legacy-compatibility rendering path, not a general text-repair framework.** Do not
generalize it, and do not add a second heuristic beside it later without re-deciding.

**⚠️ v0.110.1 lesson preserved: never rewrite stored question content from a display path.**
`QuizQuestionText` takes `text` as a prop and returns nodes — keep it that way. No normalization on
the deserialization path, no mutation of `QuizItem`.

---

## 2. Adaptive Practice scope and header

**Decision: fix the scope truth, then reduce information density.**

**Diagnosis (unchanged).** The backend already picks the right title by scope
(`QuickReviewAdaptivePracticeService:1037`: `sourceCollection == null ? studyPack.getTitle() :
sourceCollection.getTitle()`). But the **note-addressed read at `:183` never passes the collection**
— it calls the 2-arg overload, which hardcodes `sourceCollection = null` (`:1010`) and applies no
scope filter. Because a plan-scoped session is anchored on a primary pack (v0.107.0), reading that
note returns the plan-scoped session **titled with one pack**. That is the screenshot.

### 2a. Backend — scope truth

Make the note-addressed read resolve the collection exactly as the session-addressed read already
does at `:699-704`, reusing the existing public `resolveSourceCollectionId` (`:577`) and
`findByIdAndOwnerUserId`. **Mirror the existing deleted-collection fallback at `:706-713`; do not
duplicate it.**

Resulting contract — **no new scope label is invented**:

| Session scope | Displayed context |
|---|---|
| Plan-scoped | Subject Plan title |
| Note-scoped | Study Pack / Note title |

### 2b. Frontend — presentation hierarchy

Five layers, communicated separately:

1. **Mode** — `Adaptive Practice`
2. **Scope** — the title from the table above
3. **Compact weakness summary** — e.g. `14 weak concepts across 4 notes`
4. **Source grouping** — concepts grouped by source Study Pack / Note
5. **Progressive disclosure** — a compact first view; not every concept from every source expanded

Illustrative shape (**not locked copy**):

```
Adaptive Practice
Structural Engineering

14 weak concepts across 4 notes

Structural Analysis — 5 concepts
Shear force · Bending moment · Influence lines

Reinforced Concrete — 4 concepts
Flexural strength · Development length · …

+ 2 more notes                                   [ Show all concepts ]
```

**Binding product rule: this is a practice-entry surface, not a Progress report.** Show enough for
the learner to understand why this practice exists; do not make them read a diagnostic inventory
before they can start.

**No contract change needed.** `AdaptivePracticeFocusConceptResponse(concept, sourceStudyPackId,
sourceTitle, selectionReason)` already carries source identity per concept.

**⚠️ Do NOT merge identical concept strings across Study Packs.** Grouping stays source-aware —
merging would assert cross-pack canonical concept identity, which is ADR-sized and explicitly out
(v0.107.0). The current comma-joined line already violates this; fixing it discharges the invariant.

---

## 3. Section drag-and-drop

**Decision: compact the drag preview aggressively, and keep the collision fix.**

**Diagnosis (unchanged).** Two causes in
`frontend/app/collections/[id]/builder/study-plan-builder-page-client.tsx`:

1. `collisionDetection={closestCenter}` (`:2261`) compares rect *centers* — wrong for a
   variable-height sortable list.
2. **The overlay renders the section fully expanded with every note** (`:2311-2318`). A 12-note
   section is several hundred px tall against ~90px targets, so its center sits far below the cursor
   and upward targets never win — exactly the reported asymmetry.

**Ships:**

- `closestCenter` → `closestCorners` at `:2261`.
- **Overlay reduced to section title + note count only.** The count already exists at `:2304-2307`.

**⚠️ Explicitly rejected: "first 3 notes + N more".** Preview height *is* the diagnosed failure;
a smaller oversized preview reintroduces the same geometry problem. The overlay's only job is to
answer *"what section am I moving?"* — title gives identity, count gives scope, and the underlying
list already shows contents.

**Shared-context caveat, verified:** there are two `DndContext`s — `:2259` (leaf builder: sections
**and** notes) and `:2457` (subject level). The change lands on `:2259`, which sections and notes
share, so **note dragging must be exercised too**.

---

## 4. Section combobox commit

**Decision: existing option = immediate commit. Free-text stays protected.**

**Diagnosis (refined).** The debounced writer at `~:440-454` is gated on `editing` (focus/blur), so
**a dropdown selection rides the same blur path as typing**. Its comment records a reproduced defect
— *typing "Week", pausing, then " 1" created a section called "Week" and dropped the rest* — and
CLAUDE.md forbids removing the writer or its comment.

The gate conflates two different acts:

| Act | State at the moment of the act | Commit path |
|---|---|---|
| Typing a new name | Provisional — mid-keystroke is genuinely incomplete | **Unchanged**: blur + 500ms debounce |
| Choosing an existing option | **Final at the click** — nothing partial to protect | **New**: immediate |

**Ships:** a separate commit path on the combobox's option-select callback that calls
`onLabelChange` directly, bypassing the `useEffect` debounce. No Enter, no click-elsewhere. The
existing effect and its comment are untouched.

**Card relocation is correct and stays.** The note moves into the selected section immediately; do
not artificially hold it in the old section to preserve cursor position.

**Mitigation: brief destination highlight on the moved note card.** No elaborate long-distance
movement animation unless implementation testing shows a clear need. The goal is only to answer
*"did my change happen, and where did the note go?"*

**⚠️ Interaction with item 5 — see §10, Finding A.** Immediate commit makes an existing flush
behaviour reachable in one click instead of requiring a blur.

---

## 5. Save order — deferred commit, new affordance

**Decision: keep deferred commit; replace the affordance.**

**⚠️ Autosave-per-drop stays rejected.** It raced itself: each drop awaited a save plus a full
refresh, nothing gated dragging meanwhile, so a second drag wrote from a diverging base and was
clobbered when the first refresh landed. Two releases paid to close this.

**The complaint is valid and is a discoverability problem with a long-list trigger.** The controls
at `:2196-2214` already render only while dirty — but they live in a page header that scrolls out of
view. On a 77-note plan the curator works hundreds of pixels below the only affordance that commits
their work.

**Ships: a dirty-state sticky bar.** Shown only while there are pending changes; absent otherwise.

```
Order changes not saved            [ Discard ]  [ Save changes ]
```

*(copy — see §10 Finding B, which recommends a wording change)*

**⚠️ No two equally-prominent Save controls.** The sticky bar becomes the dominant pending-state
affordance. Move `:2196-2214`'s buttons into it; if the header retains anything, it is the status
text only, not a competing primary action.

---

## 6. Dirty-state model (item 5) — explicit

**There is exactly ONE pending state, and it covers order *and* section placement.**

`leafOrdersMatch` (`:118-125`) compares **both** `noteId` sequence **and** `label`:

```ts
left.every((item, index) => (
  item.noteId === right[index]?.noteId && (item.label ?? null) === (right[index]?.label ?? null)
))
```

So a section change made **by dragging a note into another section** is pending state — and the code
counts exactly that, in `pendingDragAssignmentCountRef` (`moveLeafNote`, deferred branch).

| Source of change | Deferred? | In `leafOrderDirty`? |
|---|---|---|
| Drag reorder within a section | **Yes** | Yes |
| Drag a note into another section | **Yes** | Yes (also counted separately) |
| Section pick from the combobox | **No — persists immediately** | No (baseline resets) |
| Section rename, Set-sections-from-subjects | **No — persists immediately** | No (baseline resets) |

**State transitions:**

| Action | Effect |
|---|---|
| Drag (reorder or cross-section) | `leafItems` updated, `leafOrderDirtyRef = true` → bar appears |
| **Save changes** | `persistLeafItems` writes the array; baseline := saved; bar clears |
| **Discard** | `leafItems := lastSavedLeafItemsRef.current`; dirty := false; bar clears (`:1487-1497`) |
| Any immediate-commit mutation (combobox, rename) | **Flushes pending drags to the server**, baseline resets, bar clears — see §10 Finding A |

**Discard reverts the pending drag state to the last persisted baseline.** It never reverts anything
already written to the server. Because immediate-commit mutations reset that baseline, **after a
combobox pick there is no pending order left to discard.**

**Pending-change count: NOT added.** Per owner §8, reordering is not naturally countable. Evidence
surfaced as requested: a deterministic counter **does** exist —
`pendingDragAssignmentCountRef` — but it counts **only drag-induced section reassignments**, not
reorders, so it is not a general "N changes" figure and must not be presented as one.

---

## 7. Navigation away with pending ordering

**Binding contract: a curator must never unknowingly lose deferred ordering changes.**

**⚠️ Protection already exists — this is an upgrade, not new construction.** `:1376-1399` registers,
gated on `leafOrderDirty`:

- a `beforeunload` handler (browser-native dialog), and
- an in-app interceptor on `a[href]` clicks showing
  `globalThis.confirm("Leave without saving this order?")`.

So work is **not** silently lost today. What is missing is the third option.

**Ships — in-app navigation:** replace the two-choice `confirm()` with a three-choice dialog.

| Action | Behaviour |
|---|---|
| **Save and leave** | Persist pending order, then navigate |
| **Discard and leave** | Drop pending order, then navigate |
| **Keep editing** | Cancel navigation |

**Browser refresh / tab close:** keep `beforeunload` as a **warning only**. Browsers render their own
dialog and permit no custom actions and no reliable async save — **do not promise a save path the
browser lifecycle cannot guarantee.**

**⚠️ Known coverage gap, stated rather than discovered later:** the in-app interceptor catches
`a[href]` clicks only. Programmatic `router.push`, browser back/forward, and any navigation from a
control that is not an anchor are **not** covered today. Extending coverage is in scope for Release
B; if it cannot be done within the release, the residual ships as a **named Known limitation**, not
as an unstated gap.

---

## 8. Release split

**Do not ship all five together.**

### Release A — items 1, 2, 3, 4

Correctness and legibility fixes with clear expected behaviour. Item 2 crosses backend and frontend
but is **fundamentally correctness** — a plan-scoped session must not present itself as one note —
and is not deferred merely for being medium-sized.

### Release B — item 5

Item 5 defines a transactional authoring experience: pending state, save, discard, navigation
protection, dirty-state wording, and the relationship between immediately-saved edits and deferred
ordering. It earns its own focused verification.

**Additional reason, from §10:** items 4 and 5 touch related persistence behaviour. Separating them
avoids one release simultaneously changing immediate section assignment **and** deferred order state
in ways that are hard to falsify.

**Routing:** Release A items 1, 3, 4 are **Claude Code inline**; item 2 is **Codex** (two systems).
Release B is **Claude Code inline** unless navigation coverage grows a router-level abstraction.

---

## 9. Verification

### Release A

**Item 1**
- a malformed **one-line** assertion renders statements and the final interrogative separately;
- an already-correct **multiline** question renders unchanged;
- **no stored question text changes** — assert the persisted value, not just the render.

**Item 2**
- a **plan-scoped** session read through the **note-addressed** route displays the Subject Plan scope;
- a **note-scoped** session still displays the Note / Study Pack scope;
- grouped concepts **never cross source-pack boundaries**;
- the compact view stays usable at ~14 concepts across several source packs.

**Item 3**
- **upward** drag of a large section works;
- downward drag still works;
- **note dragging in the shared context does not regress**;
- the overlay stays compact **regardless of section size**.

**Item 4**
- selecting an existing option commits **without blur**;
- free-text typing still follows the protected delayed path;
- **partial free-text names are never persisted mid-keystroke** (regression guard on the recorded
  v0.88.0 defect);
- the moved card gives enough feedback to locate its destination.

**Discriminating-fixture note:** each above fails under the defect and passes under the fix. A
question already containing `\n`, a note-scoped session, a downward drag, or a single-note section
all pass **both** ways and prove nothing.

### Release B

1. reorder one note → dirty state appears;
2. reorder a section → dirty state appears;
3. Save → state persists, sticky bar clears;
4. Discard → pending order returns to last persisted state;
5. navigation with dirty state triggers protection;
6. **Save and leave** persists before navigating;
7. **Discard and leave** does not persist pending order;
8. **Keep editing** stays on the page;
9. **an immediately-persisted section reassignment is not reverted by Discard** — see §10 Finding A
   for how this is satisfied, and why it is satisfied vacuously rather than by isolation;
10. clean-state navigation shows no warning;
11. Save in a clean state is impossible or a no-op;
12. long plans keep the pending-state affordance visible while editing near the bottom.

---

## 10. ⚠️ Where the code contradicts the tightening

Reported rather than silently reconciled, per the owner's instruction.

### Finding A — section assignment is NOT outside the deferred transaction (affects §6, §11-9)

**The premise:** requirement 9 assumes a combobox section pick is persisted **independently**, so
Discard could revert pending order without touching it.

**The code:** `handleLeafLabelChange` (`:1631`) calls `moveLeafNote(..., deferSave = false, ...)`,
whose non-deferred branch calls `persistLeafItems(nextItems, ...)`. `nextItems` is derived from
`leafItems` — **the in-memory pending state, including unsaved drags** — and `persistLeafItems` then
sets `lastSavedLeafItemsRef.current = nextItems` (`:1523`).

**So a combobox section pick writes the curator's pending drag reordering to the server as well, and
clears the dirty state.** This is deliberate and documented: CLAUDE.md records that non-drag
mutations must **"flush, never discard"**.

**Consequence for requirement 9:** it is satisfied **vacuously, not by isolation**. After a section
pick there is no pending order remaining, so Discard has nothing to revert and cannot undo the
assignment. The required outcome holds. The owner's mental model of two independent transactions
does not.

**Recommendation: accept the flush; do not change it.** It is deliberate, it is the safe direction
(pending work is **saved**, never lost), and changing it would re-open the design that fixed the
race. **But the copy must not promise isolation** — see Finding B.

**Interaction with item 4 (why the split in §8 matters):** immediate commit on selection makes this
flush reachable in **one click** instead of requiring a blur. Behaviour is unchanged; frequency and
visibility rise. The observable effect is that the sticky bar disappears at the moment a section is
picked. Benign — the work is saved — but it is the reason items 4 and 5 should not share a release.

### Finding B — "Order changes not saved" under-describes the pending state (affects §6)

**The premise:** ordering is pending while section assignment is immediate, so the bar should say
*order*.

**The code:** `leafOrdersMatch` (`:118-125`) compares `noteId` sequence **and** `label`, and
`pendingDragAssignmentCountRef` exists precisely to count **drag-induced section reassignments**. So
**dragging a note into another section is pending section assignment** — pending state is broader
than "order".

**Both candidate wordings are wrong in opposite directions:**

| Copy | Failure |
|---|---|
| `Unsaved changes` | Over-claims — implies the already-persisted combobox pick is pending |
| `Order changes not saved` | Under-claims — a pending drag can also have moved a note between sections |

**Recommendation:** wording that covers arrangement without implying the combobox pick is pending —
e.g. **`Arrangement not saved`**, or **`Drag changes not saved`** (most precise: everything pending
came from a drag; everything from the combobox is already saved). **Owner's call on copy; the
semantic requirement is that the bar describes drag-originated order *and* placement.**

### Finding C — navigation protection already exists (affects §7)

`:1376-1399` already registers a `beforeunload` handler and an in-app `a[href]` click interceptor
with a `confirm()`, both gated on `leafOrderDirty`. **§7's "current silent loss is unacceptable"
rests on a false premise — work is not silently lost today.** The real gap is that the existing
dialog offers **two** choices where the owner requires **three**, plus the coverage gap named in §7.
This makes §7 smaller than scoped, and it remains required.

---

## 11. Anti-drift

- **⚠️ Do NOT remove the `editing`-gated debounced writer at `~:440-454` or its comment** — v0.88.0
  mechanism, recorded reason, forbidden by CLAUDE.md. Item 4 adds a path beside it.
- **⚠️ Do NOT revert to autosave-per-drop.**
- **⚠️ Do NOT change the flush-on-immediate-mutation behaviour** (Finding A) — deliberate, and the
  safe direction.
- **⚠️ Item 1 must not mutate stored or deserialized text** — presentation only (v0.110.1). It is a
  legacy-compatibility path, not a text-repair framework.
- **⚠️ Item 2 must not merge focus concepts across packs** — group by `sourceTitle` (v0.107.0).
- **⚠️ Item 3's overlay must not regrow** — no "first N notes" preview.
- **⚠️ Do NOT add a numeric pending-change count** (§6).
- **⚠️ Do NOT change `ADAPTIVE_PRACTICE_STARTED`'s fields or firing conditions** — dated checkpoints
  read them. Item 2 changes a response title, not an event.
- **⚠️ No migration, no new mode or sub-mode, no quota/entitlement/meter change, no `ProfileType`
  gate.**
- **⚠️ `frontend/app/onboarding` stays frozen** — none of the five reach it.
- **⚠️ Do NOT broaden beyond these five fixes.**
