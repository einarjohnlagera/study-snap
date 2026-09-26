# Degree Study Journeys — Stage 1 Architecture & Product UX Recommendation

**Status:** **APPROVED ARCHITECTURE — decision-complete feature plan.** Nothing implemented. This document is the authoritative handoff artifact for a later Release Implementor session; see §21.
**Date:** 2026-09-25 (Stage 1 audit) · **2026-09-26 (owner decisions applied, plan finalized)**
**Repo baseline:** `study-snap` @ `main` `a76db9bc` (v0.159.0 released)
**Inputs:** the team's consultation document ("NoteLib Study Journey UX / Architecture Review"), a 5-pass cold-context repo audit with every claim anchored to `file:line`, a Product/UX strategist review, and the owner's decision set of 2026-09-26.
**Audience:** a product-thinking reader who has NOT read the repo. Section 2 is written to stand alone.

> **Reading note.** The consultation document was treated as a hypothesis to falsify, not as approved architecture. This brief **agrees with its central instinct** (Degree is not another collection level) but **rejects two of its supporting assumptions** — see §1.3 and §6 — and **relocates** its Academic Term proposal (§5). Those three disagreements are the load-bearing content here, and all three have now been accepted by the owner.
>
> **Status of decisions.** Every recommendation in this document has been ruled on. Subject Plan reuse (§6), no Degree landing page in Phase A (§11), no Degree progress ever (§9.5), deferred learner customization (§8), required compact Subject cards (§12.2), and ADR-003 as Phase A0 (§18) are **owner-approved**. The one open verification item (individual Subject adoption, §7.2) has been **closed by direct code reading**. There are no remaining architectural questions — §22 records what, if anything, is still open.

---

## 1. EXECUTIVE VERDICT

### 1.1 Is the Degree → Year → Subject → Section → Note mental model correct?

**Yes as a *learner mental model*. No as a *persistence model*.** All five levels are real to the learner and should all be visible in navigation. Only two of them are, or should be, persisted collection rows.

### 1.2 Which levels should be persisted learning entities?

| Level | Persisted as | Why |
|---|---|---|
| **Degree** (BS Computer Science) | **Nothing in Phase A.** A small catalog/discovery entity in Phase C. Never a `NoteCollection`. | It owns no learning state, no progress, no adoption record, no notes. Persisting it as a collection would force a 3rd hierarchy level (see §2.3) for zero behavioral gain. |
| **Year** (BSCS 1st Year) | **Yes — a root `NoteCollection`** (what the code calls a Goal / what the product calls a Review Set or Study Plan). | It is the adoption unit, the ownership unit, the progress-rollup unit, the publication unit. It owns real state. This is the *existing* root level — no new concept. |
| **Subject** (Programming Fundamentals I) | **Yes — a child `NoteCollection`.** | It is the unit that holds Notes, carries per-subject progress, and is the learner's working surface. This is the *existing* child level — no new concept. |
| **Section** (Control Flow) | **No — stays a computed grouping.** | It is already a free-text `label` string on the Note-membership row, grouped client-side. Nothing in the product needs a Section to be addressable, adoptable, or progress-bearing. Keeping it computed is 100% consistent with current behavior — it is not a change. |
| **Note** | **Yes — already the canonical knowledge unit.** | Unchanged. |

### 1.3 Is a new Degree Journey concept needed?

**Not for Phase A. Yes eventually, and it must be a new lightweight entity — not a `NoteCollection`.**

This is the first place this brief **diverges from the consultation document's framing**. §5 of that document treats "should BSCS be a Goal above Year Goals?" as a taste question about avoiding "arbitrary recursive nesting." The repo says it is not a taste question:

> The 2-level cap is **not schema-enforced** — the parent FK is recursive and unbounded. It is enforced **in application code, in three independent places**: (1) two service-layer validators, (2) the frontend's binary Goal-view-vs-leaf-view render switch, (3) the adoption and Official-update code, which iterate exactly one level of children.

A 3rd persisted level does not "add recursion" — it **breaks three unrelated subsystems simultaneously**, including the adoption/update engine that is currently the most mature and most reusable asset in this area. The consultation's instinct (option C) is correct; the reason is stronger than it realized, and the answer is therefore not "prefer not to" but **"do not."**

### 1.4 Is Academic Term a hierarchy level or placement/grouping?

**Placement/grouping — and specifically, placement that belongs on the child Subject Plan row itself.** Not a collection, not a join table, not a new entity, not free-floating metadata. See §5, which corrects the consultation document's §7 open question ("on Subject Plan / on Goal→Subject relationship / lightweight entity / metadata") by pointing out that **there is no Goal→Subject relationship row** — the parent link is a scalar column on the child. Term therefore has exactly one correct home.

### 1.5 Can current NoteCollection primitives remain the learning foundation?

**Yes, with very little change.** BSCS Year 1 is *already* expressible today as a published root collection with child Subject Plans holding canonical Notes grouped by section labels — publishable, adoptable, updatable, progress-bearing, with zero schema change. The **only thing genuinely missing for a correct BSCS Year 1 is semester grouping**, which is two additive nullable columns and a conditional render.

### 1.6 One-line verdict

> **Ship BSCS Year 1 on the existing 2-level primitive, add Term as two nullable placement columns on the child Subject Plan, ship term grouping and compact Subject cards together as one coherent Year-page UX, keep Degree editorial in Phase A and a small non-collection catalog entity in Phase C, never roll a Degree percentage up, and explicitly abandon "one canonical Subject Plan shared across Degree Journeys" — the shared canonical artifact is the Note, not the Subject Plan.**

### 1.7 The three product doctrines this plan commits to

> **Notes are reusable knowledge. Subject Plans are curriculum-shaped learning arrangements of that knowledge.** (§6)

> **Degree shows the journey. Years show learning progress.** (§9.5)

> **Higher levels communicate overall learning state. Lower levels surface the next actionable work.** (§14)

---

## 2. CURRENT REPO ARCHITECTURE

*(This section assumes no repo knowledge. It is the ground the rest of the brief stands on.)*

### 2.1 Three facts that overturn the usual assumptions

A reader coming to this codebase fresh would guess three things wrong. Getting these right makes everything below legible:

1. **"Goal" and "Subject Plan" are the same entity, in the same table, with no discriminator column.** There is exactly one class, `NoteCollectionEntity` (`entity/NoteCollectionEntity.java:26`), backed by one table, `note_collections`. Whether a row is a "Goal / Review Set / Study Plan" or a "Subject Plan / Unit" is determined **purely by its position in the tree** — a row with no parent is a Goal; a row with a parent is a Subject Plan. There is no `type`, `kind`, or `profile` column. The only enum on the entity is `CollectionVisibility {PRIVATE, PUBLIC}`.

2. **"Section" is not an entity anywhere.** No Section table, class, repository, or ID exists in the backend. What the product calls a Section is a nullable free-text `label` column (`VARCHAR(120)`) on the Note-membership join row (`NoteCollectionItemEntity:30-31`). The frontend produces sections by grouping membership rows on the normalized label string (`study-plan-builder-page-client.tsx:133-165`). Sections have no ID, no ordering column of their own, and no server-side progress aggregation. **They are computed groupings, not addressable rows.**

3. **The 2-level depth limit is enforced by application code, not by the schema.** `parent_collection_id` is a self-referential FK with no depth constraint (`V83__collection_parent_hierarchy.sql`). Depth is capped at exactly 2 by three independent mechanisms:
   - `validateParentCanAcceptChild` (`NoteCollectionService.java:1794-1801`) rejects nesting under a collection that already has a parent, and under one that already holds Notes.
   - `validateChildCanBeNested` (`:1803-1807`) rejects giving a child collection children of its own.
   - The frontend's `isGoalView` / `isTopLevelGoal` switch (`collection-detail-page-client.tsx:1855, 2952`) branches the **entire collection page render** on "has children" vs "is leaf" — a binary, with no third branch.
   - The adoption and Official-update code iterates exactly one level of children.

   **Consequence: a 3rd persisted level is not a schema change. It is a change to two validators, the page-render switch, and the adoption/update engine.**

### 2.2 The hierarchy, as it actually is

```
note_collections  (one table, self-referential parent FK)
  root row      parent_collection_id = NULL   → rendered as "Goal / Review Set / Study Plan"
    child row   parent_collection_id = <root> → rendered as "Subject Plan / Unit"
                (a child may not have children; a parent with Notes may not take children)

note_collection_items  (join: collection ↔ note)
  UNIQUE (collection_id, note_id)     ← scoped per collection, NOT globally per note
  label     VARCHAR(120) NULL          ← this is the "Section"
  position  INT                        ← ordering of notes within the collection
  + provenance snapshot columns (sourceLabelAtSync, sourcePositionAtSync, sourceSyncedAt, publishedAt)
```

### 2.3 Reuse asymmetry — the single most important structural fact for this decision

- **A canonical Note can already belong to many collections at once.** Uniqueness is `(collection_id, note_id)`, not global on `note_id`, and only a non-unique lookup index exists on `note_id` alone (`V114`). Many-to-many Note placement is **already supported, today, with no new mechanism.**
- **A collection cannot.** Parenthood is a single scalar `parent_collection_id` column on the child. **There is no multi-parent placement for collections, and `parent_collection_id` cannot be repurposed for it.**

This asymmetry is why "canonical Note reused across curricula" is a solved problem and "canonical Subject Plan reused across Degree Journeys" is not. It drives §6.

### 2.4 Adoption, provenance, and Official updates (more mature than the consultation assumed)

- **Adoption is deep copy, not reference.** `adopt()` (`NoteCollectionService.java:1031-1061`) copies a published Subject Plan and its Notes into a new row owned by the adopter. `adoptGoal()` (`:1063-1186`) copies a Goal by calling `adopt()` per published child and re-parenting each copy. Every adopter gets an independent copy; two learners adopting the same source never collide.
- **Provenance is tracked at both grains.** Collection level: `sourcePlanId` (unique per `(owner_user_id, source_plan_id)`), plus `sourceTitleAtSync`, `sourceParentIdAtSync`, `sourcePositionAtSync`, `sourceSyncedAt`. Item level: `sourceLabelAtSync`, `sourcePositionAtSync`, `sourceSyncedAt`, `publishedAt`.
- **Official update is additive-merge only, and already per-child.** `getSourceUpdate()` / `applySourceUpdate()` (`:1188-1342`) only ever *adds* new Subject Plans and new Note placements; it never removes or overwrites a learner's rows. Each addition is applied in its own try/catch. **"Add one new Subject to an already-adopted Year" is literally the pattern in production today.** If the source stops being public, the copy detaches (`SOURCE_DETACHED`) rather than erroring.
- **"Official" is structural, not a flag.** A publishable review-set root is one where `sourcePlanId == NULL` AND `parentCollectionId == NULL` AND visibility is PUBLIC (`assertOfficialReviewSetRoot`, `:1874-1879`). There is no `isOfficial` boolean. Source rows and adopted copies share the same table and columns, differentiated by usage.
- **Learner customization exists at Note grain only.** `NoteCollectionItemRemovalEntity` tombstones a Note the learner removed from an adopted plan, keyed `(adoptedCollectionId, sourcePlanId, sourceNoteId)`, and is consulted at merge time so re-sync does not resurrect it. **There is no equivalent at whole-Subject-Plan grain** — no removal, move, or reorder tombstone for a child collection exists anywhere.

### 2.5 Progress

- **`ConceptHealthEntity`** keyed `(userId, studyPackId, concept)`, `concept` is free text (concepts in different packs are unrelated). Three readiness states only: `NOT_STARTED → DUE → MASTERED`, with `isDue` going stale after 3 days. No partial state.
- **Mastery % is `round(mastered * 100 / total)`, and returns literal `0` when `total == 0`** (`ProgressReportService:625-630`). The consultation's "0% ready / 0 due / 312 not started" complaint is the formula's actual output, not a copy bug.
- **Rollup chain today:** ConceptHealth → per-Note counts (server, batched) → **per-Section sums (client-side only**, `aggregateSectionReadiness`, keyed by the free-text label) → per-Subject-Plan sums (server) → per-Goal sums (server, `getGoal:401-430`, a **naive unconditional additive sum** across direct children).
- **The Goal sum has no concept of a missing child.** Today every child returned is by definition adopted, so nothing has ever had to express "this child is absent from the total."
- **Batching generalizes, if used correctly.** Every level uses a `Map<groupId, Collection<…>>` batch fetch. One more level extends cleanly — *provided* it batches by group id rather than calling the per-Goal method once per year, which would reintroduce the N+1 the current code deliberately avoids.
- **A "suppress misleading 0%" precedent already exists.** The Section header readiness badge renders only when `total > 0` (`collection-detail-page-client.tsx:301-305`) — the UI already declines to show "0% · 0 due" for untouched content.
- **Two progress vocabularies already coexist.** Concept-level readiness (mastered / due / not started) *and* a cruder `NoteCollectionProgressResponse` (`totalNotes`, `notesWithStudyPack`, `notesPracticed`), computed side by side. Any recommendation must pick one for learner-facing rollup rather than adding a third.

### 2.6 Frontend routes and components

- **One flat route serves both levels:** `app/collections/[id]/page.tsx` renders `CollectionDetailPageClient` for any collection ID; the level is decided at runtime from `parentCollectionId`. **No code path anywhere handles three nested levels** — the 2-level cap is confirmed end-to-end, not just server-side.
- **Section expand/collapse is mostly already built.** Full-row clickable `<button>` header (lines 377-397); collapsed summary row with note count, a 3-title peek, and a conditional readiness badge (lines 301-312); multiple sections open simultaneously via a per-id map. **Only the default seed is wrong**: it is computed once at mount from viewport width (`LARGE_VIEWPORT_MIN_WIDTH = 1024`, lines 3063-3065) — not a live listener, not CSS. Expansion state is ephemeral React state, not persisted.
- **No compact Subject card variant exists.** `GoalDetailView` (lines 1679-1751) is the only child-card renderer: a fixed 2-column grid of full-size cards (title, expandable description, note count, readiness %, progress bar, mastered/due/not-started line). Density for 10-15 subjects is genuinely new work.
- **Terminology is keyed on the *viewer*, not the *artifact*.** `getCollectionLabels(profileType)` (`lib/collection-labels.ts:98-100`) is a clean single source of truth — but it branches on the viewing user's `ProfileType` (STUDENT → "Study Plan"/"Subject Plan"; BOARD_EXAM → "Review Set"; TEACHER → "Lesson Plan"/"Unit"), never on a property of the collection. This is one contained file, but **it is the wrong axis** for an artifact that needs its own vocabulary.
- **Discovery is a flat grid.** Explore / Published Plans render every published Goal as one card in a flat responsive grid. There is no grouping primitive.
- **The builder cannot attach an existing Subject Plan to a different parent — a UI gap, not an API gap.** `handleAddSubject` always creates a fresh child then reparents it. The reparent endpoint (`PATCH /collections/{id}/parent`) exists and is capable; there is simply no picker exposing it for cross-parent reuse.

### 2.7 Adjacent things this decision must not disturb

- **ADR-001's four Note axes** (Subject, Domain Context, Note Learner Level, Applicable Programs) are all **Note-level** metadata. Domain Context is the sole LLM domain constraint; Applicable Programs is discovery-only and never reaches a prompt. Year/Term placement answers a different question at a different grain and is orthogonal — **provided placement never touches the Note.** ADR-001's own discipline applies directly: a new Degree/Year must not imply a new Domain Context, Subject, or Applicable Program.
- **`NoteCollectionEntity.courseProgram`** is a free-text string on the collection, which is a *different* mechanism from the `note_course_program` catalog join that ADR-001's Applicable Programs axis governs. Do not conflate a curriculum-placement concept with either.
- **The curriculum authoring pipeline has no Year/Term column.** The in-flight `bscs-year1-target-shape.tsv` header is `plan_no, subject_plan, plan_description, section, note_title, note_subject, domain_context, applicable_programs, status`. `applicable_programs` is required on every row and the builder refuses a file without it; it is orthogonal to sequencing and **must not be overloaded** to carry Term.
- **Zero backend hits for "semester", "academicTerm", or "degree"** — clean slate, no naming collisions.
- **`docs/features/collections.md`** is the current, up-to-date behavioral bible for this whole area and is the doc that any implementation must reconcile against and update.

---

## 3. MODEL COMPARISON

### Model A — Degree Goal → Year Goal → Subject Plan → Section → Note

Three persisted collection levels.

| | |
|---|---|
| **Pros** | Matches the curriculum's spoken shape 1:1. One uniform mechanism. Degree gets a real page, title, description, and publication for free. |
| **Cons** | **Breaks the 2-level cap in three independent subsystems** (§2.1.3): two validators, the binary Goal-vs-leaf page render, and the adoption/update engine. Forces a decision about what Degree-level *adoption* means, which is a question with no good answer (see §7.3). Forces Degree-level progress rollup, which is the one number that will be misleading (§9.5). Every future level is then cheap to add — the sprawl the consultation rightly fears becomes structurally invited. Creates ambiguity for every existing Review Set: is PNLE now a Year or a Degree? |
| **Verdict** | **Reject.** The cost is three subsystems and the benefit is a page that Model B gets for a fraction of the work. |

### Model B — Degree Journey definition (new small entity) → Year Study Plans → Subject Plans → Section → Note

Degree is a separate, lightweight catalog entity that *references* independently-adoptable Year roots. The learning hierarchy stays 2 levels.

| | |
|---|---|
| **Pros** | Leaves the 2-level cap, adoption engine, update engine and render switch **entirely untouched**. Degree gets a real title, description, slug, and an ordered list of Years — enough for a proper landing page. Degree-level *learner* state is simply never created, so misleading Degree progress is avoided by construction rather than by careful code. Supports progressive year-by-year adoption natively, because Years remain the adoption unit. Existing Review Sets are unaffected — they just belong to no Degree Journey. |
| **Cons** | A new table, new endpoints, a new admin surface, and a new discovery page — real Codex-sized work. Two "container" concepts now exist in the product (Degree Journey and Goal), which needs clear naming discipline. |
| **Verdict** | **Accept — as the end state (Phase C).** |

### Model C — Year Study Plan only, Degree purely presentational/discovery

No Degree persistence at all. "BS Computer Science" exists in copy, naming, and editorial curation only.

| | |
|---|---|
| **Pros** | **Zero new architecture.** BSCS Year 1 ships on the existing primitive, today. Nothing to migrate, nothing to version, nothing to get wrong. |
| **Cons** | No Degree landing page without hardcoding. No curator-visible link between the four Years. Discovery is the existing flat grid, so the four BSCS Years appear as four unrelated cards. Does not scale past one or two degrees. |
| **Verdict** | **Accept — as Phase A**, exactly and only. It is genuinely sufficient to publish and use BSCS Year 1 correctly, which is the stated Phase A bar. |

### Model D — the recommended synthesis (materially different from B and C)

**Model C now, Model B later, plus two things neither the consultation document nor A/B/C state:**

1. **Term is two nullable columns on the child Subject Plan row** — not an entity, not a join, not free-floating metadata (§5).
2. **Subject Plans are *not* canonically shared across Degree Journeys. The canonical shared artifact is the Note.** "Programming Fundamentals I" in BSCS and in BSIT are two thin Subject Plan shells pointing at the *same* canonical Notes (§6). This is a deliberate rejection of the consultation document's §6/§9 assumption, forced by the reuse asymmetry in §2.3.

| | |
|---|---|
| **Pros** | Phase A is a small additive change set with zero new entities. Term generalizes to every real Philippine HEI structure with two columns. Degree progress never needs to be computed, in any phase. Backward compatibility is provable rather than argued. Canonical knowledge is still never duplicated — only ordering shells are. |
| **Cons** | Curators will eventually author near-duplicate Subject Plan shells across degrees, with no discovery tooling to notice ("BSCS already has Programming Fundamentals I"). **Owner-acknowledged as a real, accepted cost**; the future mitigation is a *Copy structure from existing Subject Plan* curator action, evaluated when the second Degree Journey is actually authored — not in Phase A (§6.6, §20 risk 2). |
| **Verdict** | **Recommended.** |

---

## 4. RECOMMENDED DOMAIN MODEL

```
BS COMPUTER SCIENCE
│  ── Phase A: EDITORIAL ONLY (a title, a naming convention, a curator's spreadsheet).
│     Phase C: a small CATALOG ENTITY — degree_journeys(id, slug, title, description,
│     visibility) + degree_journey_years(degree_journey_id, collection_id, position).
│     NEVER a NoteCollection. NEVER carries learner state. NEVER carries progress.
│
├── BSCS 1st Year                                    ◀ PERSISTED ENTITY
│   │  A root NoteCollection (parent_collection_id = NULL).
│   │  Publishable. Adoptable. Owns progress. Owns the learner's copy.
│   │  This is the existing "Goal / Review Set / Study Plan" — unchanged.
│   │
│   ├── ▸ First Semester                             ◀ PRESENTATION GROUPING
│   │   │   Derived at render time by grouping children on term_label,
│   │   │   ordered by term_order. Not a row. Not addressable. No progress entity.
│   │   │
│   │   ├── Introduction to Computing                 ◀ PERSISTED ENTITY + PLACEMENT
│   │   ├── Programming Fundamentals I                   A child NoteCollection.
│   │   └── Mathematics in the Modern World              Placement columns live HERE:
│   │                                                    parent_collection_id (exists),
│   │                                                    term_label (NEW, nullable),
│   │                                                    term_order  (NEW, nullable).
│   └── ▸ Second Semester
│       ├── Programming Fundamentals II
│       └── Discrete Structures I
│
├── BSCS 2nd Year   ◀ another independent root NoteCollection
├── BSCS 3rd Year
└── BSCS 4th Year

Programming Fundamentals I  (a child NoteCollection)
│
├── ▸ PROGRAMMING FOUNDATIONS                        ◀ PRESENTATION GROUPING
│   │   The free-text `label` on each note-membership row, grouped client-side.
│   │   Not a row, not addressable, no ID, no server aggregation. Unchanged.
│   │
│   ├── Note  ◀ CANONICAL ENTITY — may belong to many collections simultaneously
│   └── Note     (already supported today; the only true cross-curriculum reuse unit)
└── ▸ CONTROL FLOW
    └── Note
```

### Classification table

| Thing | Classification | Backing |
|---|---|---|
| Degree Journey | **Editorial (A) → catalog entity (C)** | none → `degree_journeys` + ordered year references |
| Year Study Plan | **Persisted entity** | root `note_collections` row (existing) |
| Subject Plan | **Persisted entity** | child `note_collections` row (existing) |
| Subject's placement in a Year | **Relationship/placement** | `parent_collection_id` (existing) + `term_label`, `term_order` (new, nullable) |
| Academic Term | **Presentation grouping** | derived by grouping children on `term_label` |
| Section | **Presentation grouping** | `note_collection_items.label` (existing, unchanged) |
| Note | **Persisted canonical entity** | `notes` + many-to-many `note_collection_items` (existing) |
| Section / Term / Year / Degree progress | **Derived state** | summed from ConceptHealth; no new mastery entity at any level |
| Degree progress as a single % | **Deliberately does not exist** | see §9.5 |

---

## 5. ACADEMIC TERM MODEL

### 5.1 Where Term lives — and why the consultation's own option list was missing the answer

The consultation document (§7) offers four candidate homes: on the Subject Plan, on the "Goal→Subject relationship", as a lightweight grouping entity, or as metadata. The repo eliminates one of these outright and collapses two others:

> **There is no Goal→Subject relationship row.** A Subject Plan's membership in a Year is a **scalar `parent_collection_id` column on the child collection**. The only join table in this area, `note_collection_items`, joins a **Note to a collection** — the wrong grain entirely for grouping Subjects within a Year.

This has been raised as a possible home for Term ("put it on the placement row"). It is the right instinct at the wrong grain: **the placement row for a Subject-in-a-Year *is the child collection row itself*.** That row is already where placement lives.

Therefore:

> **`term_label VARCHAR(60) NULL` and `term_order SMALLINT NULL` go on `note_collections`, alongside `parent_collection_id`.**

The decisive argument is granularity, not convenience: **Term granularity is exactly co-extensive with parent granularity.** A collection has one parent, therefore one placement, therefore one term. No expressiveness is lost by storing it on the row, and no new row is needed to store it. And if multi-parent collection placement ever becomes necessary (§8.5), `parent`, sibling ordering, and `term` all migrate into that new join table **together**, because they are three facets of one concept — which is exactly the property you want from a field you may have to move later.

### 5.2 Exact semantics per curriculum structure

`term_label` is **free text**, deliberately. `term_order` is a **small integer** and is the only thing the system reasons about. Rendering rule: **group children by `term_label`, order groups by `min(term_order)`, order subjects within a group by the existing sibling ordering (unchanged).**

| Structure | Encoding | Renders as |
|---|---|---|
| **2 semesters** | `("First Semester", 1)`, `("Second Semester", 2)` | Two labelled groups on the Year page, in order. |
| **Trimester** | `("First Trimester", 1)`, `("Second Trimester", 2)`, `("Third Trimester", 3)` | Three groups. No code change — free text absorbs the vocabulary. |
| **4 terms / quarters** | `("Term 1", 1)` … `("Term 4", 4)` | Four groups. |
| **Semesters + summer/intersession** | `("First Semester", 1)`, `("Second Semester", 2)`, `("Summer", 3)` | Three groups; "Summer" carries no special casing and needs none. |
| **No reliable term sequence** | **both columns NULL on every child** | **Flat ungrouped list — i.e. exactly today's Year/Goal page, byte-for-byte.** |
| **Partially placed** (some subjects have a term, some do not) | some children carry values, some are NULL | Labelled term groups first, then a single trailing group explicitly headed **`Term not specified`**. **Not** "Other", **not** "Ungrouped", and **not** an unheadered run of cards — see §12.1. This is a defensive render for a curator-quality defect, not a supported authoring state. |

**The NULL case is the backward-compatibility proof and it is load-bearing** — see §17.

### 5.3 What Term explicitly is not

- Not a collection, not a row, not addressable by URL, not adoptable, not publishable.
- **Not a progress entity.** A term header may *display* a summed number (§9.4); it does not *own* one.
- Not a shared vocabulary across Years. `("First Semester", 1)` on BSCS Year 1 and on BSCS Year 2 are two independent strings that happen to match. No term catalog, no term table, no enum. If a future need arises to rename a term across a whole Year, that is a curator bulk-edit, not a schema change.
- **Not on the Note.** ADR-001 governs the Note's axes and placement is not one of them. Keeping Term on the collection is what makes this decision orthogonal to ADR-001 rather than in tension with it.

---

## 6. SUBJECT PLAN REUSE — **APPROVED; disagrees with the consultation document**

> **OWNER DECISION (2026-09-26): APPROVED.** Canonical Notes are the reusable knowledge unit. Subject Plans are thin, curriculum-specific organization/placement shells. BSCS "Programming Fundamentals I" and a future BSIT "Programming Fundamentals I" may be separate shells referencing many of the same canonical Notes. **No multi-parent Subject Plans. No `collection_placements`. Canonical Subject Plan sharing is not a requirement of this feature.** The curator cost is acknowledged and accepted; the mitigation is future work (§6.6).

### 6.1 The consultation's assumption

§6 and §9 assume that "Programming Fundamentals I" is a **canonical Subject Plan** that can *participate in* BSCS, BSIT, and Software Engineering, with placement held separately — the same way a canonical Note participates in many Subject Plans.

### 6.2 Why that is false today, and why it should stay false in v1

The repo's reuse asymmetry (§2.3) is decisive:

- **Notes** are joined to collections through a join table whose uniqueness is `(collection_id, note_id)` — **many-to-many, already working, no new mechanism.**
- **Collections** are joined to their parent by a **single scalar column**. A Subject Plan has exactly one parent, forever. **Multi-parent collection placement does not exist and `parent_collection_id` cannot be adapted to provide it.**

Making a Subject Plan genuinely shareable across two Degree Journeys therefore requires a net-new `collection_placements` join table, migration of every existing parent link onto it, and rewrites of the validators, the render switch, the adoption engine, and the update engine — all of which currently read parenthood as a scalar. That is a larger change than everything else in this brief combined, and BSCS Year 1 does not need it.

### 6.3 The recommended reuse model

> **The canonical, never-duplicated artifact is the NOTE. A Subject Plan is a thin, per-journey ordering-and-labelling shell over canonical Notes.**

Concretely, when BSIT launches:

```
BSCS Year 1  ──child──▶  "Programming Fundamentals I"  ─┐
                          (shell: title, description,   │
                           term placement, section       ├──▶ the SAME canonical Notes
                           labels, note ordering)        │    (many-to-many, already works)
BSIT Year 1  ──child──▶  "Programming Fundamentals I"  ─┘
                          (a second, independent shell)
```

This fully honors the consultation's own stated principle — *"a canonical Note should not be duplicated merely because it appears in multiple curricula"* — because **no Note is duplicated.** What is duplicated is a row holding a title, a description, an ordering, and a set of section labels. That is placement data, and the consultation's §9 explicitly wants placement separated from canonical knowledge. **This model separates them; a shared Subject Plan would actually *merge* them**, because section labels and note ordering are curriculum-specific and BSIT may legitimately want a different section breakdown of the same material.

### 6.4 Reuse across Years within one degree, and with Review Sets

- **Across Years in one degree:** same as above. "Programming Fundamentals II" in Year 1 Term 2 is a child of the Year 1 root, full stop. A subject never spans two Years.
- **With board Review Sets:** the same Notes can already sit in both a BSCS Subject Plan and a PNLE-style Review Set subject, today, with no change. This is the strongest existing argument for the model: **cross-context reuse at the Note grain is already the system's working answer, and it is the grain that matters pedagogically.**
- **The discovery/applicability axis is already handled elsewhere.** ADR-001's Applicable Programs (`note_course_program`) already answers "which programs does this Note appear in" for discovery purposes. Do not build a second answer to that question under the banner of Subject Plan reuse.

### 6.5 The settled position

**Canonical Subject Plans reused across Degree Journeys: NO in v1, and probably NO permanently.** Revisit only if curator duplication cost becomes measurably painful across 3+ degrees — at which point the fix is the `collection_placements` join, scoped as its own release, not squeezed into a Degree Journey release.

### 6.6 The accepted cost, and its FUTURE mitigation

The price of §6.3 is real and should not be talked around: **a curator authoring BSIT has no way to see that BSCS already contains a "Programming Fundamentals I" shell with a worked-out section breakdown and note set.** The knowledge is not duplicated; the *authoring effort* is.

**Do NOT add fuzzy matching, duplicate detection, or any similarity infrastructure to Phase A.** The recorded mitigation — to be **evaluated when the second Degree Journey is actually authored**, and not before — is a curator action of the shape:

> **Copy structure from existing Subject Plan**

Conceptually this would let a curator start a new, independent Subject Plan shell from an existing shell's section structure, Note placements, Note ordering, and possibly description, while creating an **independent destination Subject Plan**.

Two constraints on it, and deliberately nothing further:

- **It is not Phase A scope**, and nothing in Phase A should be shaped to accommodate it.
- **It implies no live synchronization between the shells.** It would be a one-time seeding action; the two shells diverge freely afterwards, which §20 risk 3 argues is often *correct*.

Implementation details are deliberately left unspecified — specifying them now would be guessing at a workflow no curator has yet performed.

---

## 7. ADOPTION MODEL

### 7.1 Year adoption — **primary, and already built**

`adoptGoal()` deep-copies a Year root by adopting each published child and re-parenting the copies. The learner gets an independently owned Year Study Plan with full provenance (`sourcePlanId`, unique per `(owner_user_id, source_plan_id)`, plus per-item source snapshots). **Nothing new is required for Phase A**, except that the Year-copy operation must carry `term_label` / `term_order` across to the copies — a field addition inside existing copy routines, not new machinery.

### 7.1a There are THREE child-copy sites, not two — a gap found and closed during Feature Planning, 2026-09-26

The plan originally named `adopt()` / `adoptGoal()` as the places that must carry `term_label` / `term_order` onto a copy. That undercounts by one, verified directly in code:

| Site | `NoteCollectionService.java` | Builds | Must copy `term_label`/`term_order`? |
|---|---|---|---|
| `persistAdoptedPlan` | `:1956-2017` | A single child Subject Plan copy — used directly by `adopt()`, and reused once per child inside `adoptGoal()`'s loop. | **YES — required.** |
| `persistAdoptedGoal` | `:2019-2057` | The Year/**root** copy itself when a whole Goal is adopted. | **NO — must NOT be added here.** A root collection has no parent and therefore no term placement, by the §5.1 design (Term lives only on the child grain). Naively "completing the set" by adding these fields to `persistAdoptedGoal` would be a real but silent no-op at best, and a confusing dead column at worst — call this out explicitly to whoever implements Phase A so it is not "fixed" by mistake. |
| `createSubjectAddition` | `:2476-2515` | A **new** child Subject Plan copy, created when the Official-update additive-merge path (`applySourceUpdate` → `getSourceUpdate`, §7.5) adds a Subject to an **already-adopted** Year that the learner adopted before that Subject existed on the source. Copies title, description, `courseProgram`, `learnerLevel`, `estimatedStudyHours`, and the source-sync fields, field by field — the exact same pattern as `persistAdoptedPlan`, in a separate method. | **YES — required, and currently missing from this plan's own invariant list before this correction.** |

**Why this one matters more than a missed line of code:** without it, a Subject added to an Official Year *after* a learner has already adopted that Year lands on the learner's copy with `term_label = NULL`, sitting beside siblings that do carry a term. Per §12.1, a mixed placed/unplaced Year renders a **`Term not specified`** group and is documented as a curator-quality defect to fix before publishing. Missing this copy site would let the *system itself* manufacture that exact defect on a learner's already-adopted plan, with no curator error involved and no way for the curator to see or fix it (it exists only on the learner's private copy). This is a stronger requirement than "silently loses semester grouping" (the risk already named for `adopt()`/`adoptGoal()`) — it actively produces the one render state the plan calls out as a red flag.

**Required copy sites for Phase A, final list:** `persistAdoptedPlan` (`:1956-2017`) and `createSubjectAddition` (`:2476-2515`). **Explicitly not** `persistAdoptedGoal` (`:2019-2057`).

### 7.2 Subject adoption — **VERIFIED: it already works today, and it is still not Phase A scope**

This was the brief's one open verification item. It was **closed on 2026-09-26 by direct reading of the service and its tests**, and the answer is unambiguous:

> **YES — a child Subject Plan that is currently parented under a Year is adoptable standalone today, through the existing `adopt()` path, with no code change required.**

The evidence, in full, so no later session re-opens it:

- **`adopt()` has no parent check at all.** `NoteCollectionService.adopt()` (`NoteCollectionService.java:1031-1061`) resolves its source purely via `collectionRepository.findByIdAndVisibility(sourceCollectionId, CollectionVisibility.PUBLIC)` (`:1037`). There is no parent-null condition anywhere in the method — visibility is the only gate.
- **The adopted copy is always standalone.** `persistAdoptedPlan` (`:1956` onward) constructs a brand-new entity with no parent set; it never reads or copies the source's `parentCollectionId`. So adopting a parented child yields a parentless personal copy, by construction.
- **Children of a published Year are themselves PUBLIC.** `publishChildCollections` (`:1850-1860`) flips each child's `visibility` to `PUBLIC` when the parent Goal is published. Any published child Subject Plan therefore satisfies `adopt()`'s only gate and is reachable at `POST /collections/{id}/adopt` on **the child's own id**.
- **`assertOfficialReviewSetRoot` is a different gate and was the audit's false lead.** It (`:1874-1879`) is called from exactly two places — `getReviewSetPublicationStatus` (`:740`) and `publishReviewSetUpdate` (`:754`) — both in the curator-only, admin-only **Official-update publish** flow. It is **never** called from `adopt()` or `adoptGoal()`. It governs *who may be an Official publishable root*, not *who may be adopted*.
- **This is not a theoretical reading of the method — it is an existing, named, tested product scenario.** `NoteCollectionServiceTest.java:5017-5068`, test `adoptGoal_reparentsStandaloneAdoptedChildAndSkipsAlreadyNestedChild`, constructs exactly this case: a learner independently adopts a currently-parented child Subject Plan as a standalone personal copy *before* ever adopting the parent Goal, and the test asserts the standalone copy is correctly re-parented when the Goal is later adopted.

**Scope consequence — unchanged by the outcome, and this is the owner's decision:** individual Subject adoption is **NOT Phase A scope**. A freshman's unit of intent is "my first year," not "one subject." Because the capability already exists, **Phase A adds no work to enable it and owes no UI affordance for it.** Record the behavior accurately; do not build toward it.

### 7.3 Whole-Degree adoption — **do not build it**

"Adopt BS Computer Science" would mean adopting four Year Study Plans at once, which gives a first-year student three years of content they cannot use, three progress denominators they will never move, and four copies to keep merged against Official updates. **Recommend: not offered in any phase, even as a convenience.** The Degree page offers per-year actions only. If the owner later wants it, it is a client-side loop over four existing `adoptGoal` calls — no new entity, and that is precisely why it does not need one now.

### 7.4 Degree-level learner-owned state — **none, ever**

**No `DegreeAdoption` record. No learner-owned Degree row. No Degree progress row.** "Which years has this learner adopted?" is already answerable by querying owned collections whose `sourcePlanId` is one of the Degree's referenced Year roots. Introducing a Degree adoption record would create a second source of truth for a question already answerable, and would immediately raise "what happens when a learner adopts Year 2 but not Year 1" — a question that simply does not arise if the record does not exist.

**Progressive adoption falls out for free:** adopt Year 1 today, Year 2 next year, and the Degree page reflects it with no reconciliation, because there was never a Degree-level claim to reconcile against.

### 7.5 Provenance summary

| Question | Answer | Mechanism |
|---|---|---|
| Which official plan is this copy from? | `sourcePlanId` | existing |
| Has the source changed since I adopted? | `getSourceUpdate()` diff | existing |
| Did I remove this Note deliberately? | `NoteCollectionItemRemovalEntity` tombstone | existing |
| Did I remove this whole Subject deliberately? | **nothing** | **gap — Phase D** |
| Did the source stop being official? | `SOURCE_DETACHED` | existing |
| Which Years of this Degree have I adopted? | derived from `sourcePlanId` ∈ Degree's year list | Phase C, no new state |

---

## 8. CUSTOMIZATION MODEL

> **OWNER DECISION (2026-09-26): DEFERRED, pending real adopters and evidence of what learners actually want to customize.** Subject removal, Subject movement between terms, Subject reordering, custom Subjects and conflict resolution are **all out of the first release**. Existing Note-level learner customization is unchanged and needs no work. **Everything in §8.2 onward is future design thinking, deliberately kept documented and deliberately separate from committed scope** — nothing below this line is a commitment, and §8.3's additive-only merge principle is the only part that binds Phase A (by being preserved, not by being built).

### 8.1 v1 (Phase A) — none

The consultation document's own §11 says not to build learner curriculum customization unless the minimum architecture requires it. **It does not.** A learner adopting BSCS Year 1 gets NoteLib's reference sequence and can already remove individual Notes (existing tombstone). That is enough to ship.

### 8.2 What each customization action would actually cost (Phase D)

| Action | Exists? | What it needs |
|---|---|---|
| **Remove a Note from my plan** | ✅ **works today** | nothing — `NoteCollectionItemRemovalEntity` already tombstones it and the merge respects it |
| **Remove a whole Subject from my Year** | ❌ **gap** | a new child-collection-grain tombstone, e.g. `(adoptedParentId, sourcePlanId, sourceChildPlanId)`, consulted by `applySourceUpdate` so re-sync does not resurrect it. **This is the single missing primitive that gates all of §8.** |
| **Add a Subject to my Year** | partial | creating an owned child under an adopted root is mechanically possible; it needs a UI and a rule that learner-authored children are exempt from source-diff logic (`sourcePlanId == NULL` already distinguishes them) |
| **Move a Subject between Terms** | ❌ **gap** | write `term_label`/`term_order` on the learner's own child copy. Cheap **because Term lives on the child row** (§5.1) — a learner move is a two-column update on a row they already own, with no join to reconcile. Needs the same tombstone-adjacent concept: "this child's term was learner-modified, do not overwrite on merge." |
| **Reorder Subjects** | partial | sibling ordering of children already exists; needs a "learner-modified" marker for the same reason |
| **Add my own custom Subject Plan** | partial | same as "add a Subject", plus builder access on an adopted plan |
| **Change term labels** | ❌ | defer indefinitely; low value against the cost of reconciling renamed groups at merge time |

### 8.3 The merge principle to lock in now

> **Official update stays additive-only, forever.** It adds Subjects and Note placements the learner does not have; it never removes, never reorders, never rewrites a field the learner has touched.

This is already the production behavior and it is the single most valuable property to preserve. Every customization action above therefore needs exactly one thing from the merge engine: **a way to say "the learner has an opinion about this child, skip it."** That is one tombstone/marker concept covering remove, move, and reorder — **not three separate mechanisms.** Scoping Phase D around that single primitive is what keeps it from becoming a merge-engine redesign.

### 8.4 A specific conflict to decide before Phase D, not now

If the learner moves Calculus from Term 2 to Term 1, and the Official curriculum *also* moves Calculus to Term 3 — additive-only means the learner keeps Term 1 and is never told. **Recommended future stance: silent learner-wins, plus a passive "the official curriculum changed since you adopted" note on the update preview.** Do not build conflict resolution UI.

### 8.5 Explicitly deferred beyond Phase D

Multi-parent collection placement (`collection_placements`) — the prerequisite for genuinely shared canonical Subject Plans (§6.2). Not needed for any phase in this brief.

---

## 9. PROGRESS MODEL

### 9.1 Source of truth — unchanged

**ConceptHealth remains the only learning evidence. No new mastery signal at any level.** Every number at every level in this brief is a sum of the same three concept states (`NOT_STARTED`, `DUE`, `MASTERED`).

### 9.2 The rollup chain

```
ConceptHealth (userId, studyPackId, concept)         PERSISTED evidence
   │  summed per note
   ▼
Note          mastered / due / not-started counts      server, batched          — exists
   │  summed by grouping on the membership row's free-text label
   ▼
Section       counts + readiness %                     CLIENT-SIDE only         — exists
   │  summed across the collection's notes
   ▼
Subject Plan  counts + readiness %                     server, batched by group — exists
   │  summed by grouping children on term_label
   ▼
Term          counts only, NO readiness %              CLIENT-SIDE only         — NEW, free
   │  summed across all children (existing naive additive sum)
   ▼
Year          counts + readiness %                     server, batched          — exists
   │
   ▼
Degree        ✕ NO ROLLUP IN ANY PHASE                 per-year cards only      — never built
```

### 9.3 Pick one vocabulary

Two progress vocabularies already coexist (§2.5): concept-level readiness, and the cruder `totalNotes / notesWithStudyPack / notesPracticed`. **Recommendation: concept-level readiness is the only learner-facing vocabulary at Section level and above. Do not add a third, and do not surface the crude one on Year/Term/Degree surfaces.**

### 9.4 Term aggregation is free — and that is an argument for it

The Year page already receives per-child readiness (every Subject card renders readiness %, mastered, due, not-started). A term header summing the children **it already has in hand** is client-side arithmetic, structurally identical to the existing `aggregateSectionReadiness`. **Zero backend work, zero new DTO fields, zero new endpoint.** A Term never becomes a mastery entity because nothing persists its number.

**Term header content:**

```
First Semester
5 subjects
```

and once learning has begun:

```
First Semester
5 subjects · 2 in progress
```

**No term percentage. No term progress bar. No term due count.** A term is a scheduling group, and a percentage there invites the learner to read it as an academic grade. **Casing and typography follow the existing design system** — the all-caps rendering used in earlier sketches of this brief was never a semantic requirement and must not be treated as one.

### 9.5 Degree progress — **PERMANENTLY REJECTED, as a product rule**

> **OWNER DECISION (2026-09-26): this is a permanent product rule, not a deferral.** NoteLib never produces a Degree readiness percentage, a Degree progress bar, a Degree completion percentage or state derived from learning progress, or blended Degree-level due/mastered/not-started counts. **A Degree surface may show per-Year state only.** The guard test that prevents an aggregate Degree percentage from being introduced accidentally is retained as a future requirement.

> **Degree shows the journey. Years show learning progress.**

Three independent reasons, all of which remain the justification:

1. **It would be false.** A learner who has adopted only Year 1 and mastered it has completed 100% of what they adopted and 25% of the degree. Neither number is honest; the second is actively discouraging on day one.
2. **The existing rollup cannot express it.** The Goal-level sum is unconditional across whatever children exist, and **nothing in the codebase has ever had to represent "this child is absent from the total."** Partial-adoption-aware rollup is net-new logic at every level it touches.
3. **Refusing to show the number means never writing that logic.** This is the rare case where the correct product answer and the cheapest engineering answer are the same one.

**Instead, the Degree page shows a per-year status chip:** `Not started` / `Adopted — 34% ready` / `Preview`. Four honest local numbers beat one dishonest global one.

*(Implementation note for whoever builds Phase C: fetch all four Years' summaries in **one batched call by group id**. Calling the existing per-Goal method once per year reintroduces the N+1 the current code deliberately avoids.)*

### 9.6 Fixing "0% · 0 due" on untouched content

The precedent already exists: the Section header badge renders only when `total > 0`. **Generalize that one rule upward** — Subject cards, Term headers, and Year summaries show **`Not started`** instead of `0% · 0 due` when the learner has zero concept evidence for that scope. This is a copy/render rule and changes no underlying evidence, exactly as §18 of the consultation requires.

> **VERIFIED NEGATIVE (2026-09-26) — there is no parity, and this is not a no-op.** The audit's caveat has been closed by reading the code. The Section header badge is gated (`collection-detail-page-client.tsx:301-305`, rendered only when `sectionReadiness.total > 0`), but the **Subject child card in `GoalDetailView` (`collection-detail-page-client.tsx:1679-1751`) is gated by nothing**: it unconditionally renders `{overallReadinessPercentage}% ready`, an unconditional `role="progressbar"` bar, and an unconditional `{mastered}/{total} mastered · {due} due · {notPracticed} not started` line. An untouched Subject therefore renders `0% ready`, an empty bar, and `0/0 mastered · 0 due · 0 not started` today.
>
> **Consequence for phasing:** because the compact Subject card is now Phase A (§12.2) and its specified content is *one* progress signal — `18 notes · Not started` or `18 notes · 41% ready` — **the `Not started` rule lands at Subject-card grain inside Phase A, as a property of the new card rather than as a separate copy fix.** Phase B's remaining copy cleanup is correspondingly narrower: the Section summary row and any Year-summary case, not the Subject card.

---

## 10. REVIEW SET vs STUDY JOURNEY

**The consultation's §14 instinct is correct, and it is already how the system works — not a new pattern to invent.**

| | Shared internally | Differs learner-facing |
|---|---|---|
| **Data** | Same table, same columns, same 2-level tree, same adoption engine, same update engine, same progress rollup. The backend stays **neutral: title, description, items.** | — |
| **Vocabulary** | — | "Review Set / Subject" vs "Study Journey / Year / Semester / Subject". |
| **Navigation** | Same `/collections/[id]` route for both levels. | Degree adds one grouping surface above the Year (Phase C). |
| **Adoption** | Identical. | Identical. |
| **Progress** | Identical evidence and formula. | Degree shows per-year chips; a Review Set has no equivalent surface. |
| **Discovery** | Same published-plans store. | Degree Years grouped under a degree card; Review Sets stay flat cards. |
| **Builder** | Same builder, same reparenting. | One new term field on the subject row. |

### 10.1 The terminology leak, precisely scoped

Collection vocabulary is resolved by `getCollectionLabels(profileType)` — keyed on the **viewing user's profile**, never on the artifact. So:

- **This is NOT a Phase A problem.** BSCS students are `STUDENT` profile, which already resolves to "Study Plan / Subject Plan" — correct and non-jarring for a freshman.
- **It bites on the *second* journey.** A `BOARD_EXAM`-profile user browsing or adopting BSCS Year 1 sees "Review Set", and a nursing or education Study Journey (whose learners plausibly carry `BOARD_EXAM`) would be mislabelled throughout.
- **Recommended fix, Phase B/C — a parallel resolver axis, not a rewrite:** extend the signature to `getCollectionLabels(profileType, collection?)` and let an artifact-level kind, when present, win over the viewer profile. `getCollectionLabels` is one contained single-source-of-truth file used everywhere, so this is a signature change plus call-site threading — not scattered logic. **Do not add a full collection-type enum in Phase A**; it is schema + DTO + frontend sweep for a problem Phase A does not have.

### 10.2 Naming

Endorse the consultation's §8 instinct: learner-facing copy should say **"Reference Study Journey"**, not "The Philippine BSCS Curriculum." NoteLib is publishing a well-researched reference sequence, not an accredited program of study, and a school's actual curriculum will differ. Say so in the Degree page's own copy — it is the cheapest possible defense against the false-authority risk in §20.

**But that name belongs to the Degree surface, which Phase A does not ship.** What Phase A may call itself is constrained by §11.1, and the two names must not be used interchangeably.

---

## 11. UX — DEGREE JOURNEY LANDING

> **OWNER DECISION (2026-09-26): a Degree landing page is NOT required for Phase A, and no temporary architecture may be created to fake one.** BSCS Year 1 ships as a useful standalone published Study Plan on the existing collection/discovery model. **The `journey_key` / `journey_order` fallback previously floated in this section is withdrawn and must not be built in any phase** (§16). The real Degree Journey surface remains Phase C, on the small catalog entity.

### 11.1 The product-promise rule — what Phase A is allowed to claim

This is a binding constraint on product, marketing, release notes and in-product copy, not a stylistic preference. **Phase A must NOT be presented learner-facing as though NoteLib already has a complete "BS Computer Science Degree Study Journey."** Until the Degree entity and surface exist, the product is offering exactly one thing:

> **BS Computer Science — 1st Year Study Plan**

The later Degree surface (Phase C) may introduce the broader promise:

> **BS Computer Science Reference Study Journey**

**Record and respect the distinction.** A Year Study Plan is a shippable, honest artifact on its own; a Degree Study Journey is a claim about a four-year path that the shipped model cannot yet back. Product language must not outrun the shipped model.

### 11.2 Phase A

**No Degree page.** BSCS Year 1 is discovered and adopted as a published Study Plan, exactly like existing Review Sets. Ship it, learn from it.

### 11.3 Phase C — the Degree surface

One new route, `/journeys/[slug]`, deliberately small. **The value proposition comes before the variability disclaimer**, and the disclaimer is a quiet qualifier rather than the page's opening statement:

```
BS Computer Science

A guided path through the core subjects and topics commonly studied
across the degree.                                        ◀ value proposition, first

Reference Study Journey
Subject names and sequencing may vary by school.          ◀ variability qualifier, second

┌──────────────────────────────────────────────────────┐
│  1st Year                                            │
│  10 subjects · 2 semesters                           │
│  34% ready                          [ Continue ]     │   ◀ adopted
├──────────────────────────────────────────────────────┤
│  2nd Year                                            │
│  11 subjects · 2 semesters                           │
│  Not started                        [ Preview ]      │   ◀ published, not adopted
├──────────────────────────────────────────────────────┤
│  3rd Year        Coming soon                         │   ◀ referenced, not yet published
├──────────────────────────────────────────────────────┤
│  4th Year        Coming soon                         │
└──────────────────────────────────────────────────────┘
```

**Do not present NoteLib as an accredited or authoritative representation of any specific school's curriculum.** Do not show Degree-level progress of any kind (§9.5).

**Decisions embedded above:**

- **New page, not a reused collection page.** The existing collection page's entire render branches on "has children vs leaf" — a third branch for "is a degree" would be pushing a fourth meaning through a binary switch that is already load-bearing in three subsystems. A separate small route is cheaper and safer.
- **Year cards are independently actionable.** One button per year. **No "Adopt entire degree."**
- **No Degree-level progress bar, no Degree percentage, no Degree completion state** (§9.5).
- **Degree itself has no learner-owned state** — adopted/not is derived per year.
- **"Coming soon" is a first-class state**, because NoteLib will publish Year 1 long before Year 4. The Degree page must be honest about an incomplete journey rather than hiding unbuilt years.
- **Per-Year progress only**, shown as the Year's own readiness. Nothing on this page aggregates across Years.

---

## 12. UX — YEAR STUDY PLAN

The existing Goal page becomes the Year page with **one structural change — conditional term grouping — and one density change: compact Subject cards.** Per the owner's 2026-09-26 decision these are **one coherent Year-page UX and ship together in Phase A.** Do not ship term grouping while knowingly retaining the oversized Subject card that motivated this redesign.

The Year page answers exactly one question:

> **What subjects make up my year, and where am I in each one?**

Detailed learning state belongs deeper, in the Subject Plan.

```
BSCS 1st Year
10 subjects · 2 semesters · 34% ready                    ◀ one summary line, not four metrics

First Semester                                           ◀ term header: no %, no bar, no due count
5 subjects · 2 in progress
┌────────────────────────┐ ┌────────────────────────┐
│ Introduction to        │ │ Programming            │
│ Computing              │ │ Fundamentals I         │
│ 12 notes · Not started │ │ 18 notes · 41% ready   │   ◀ compact card: title, count, ONE signal
└────────────────────────┘ └────────────────────────┘
┌────────────────────────┐ ┌────────────────────────┐
│ Mathematics in the     │ │ Understanding the Self │
│ Modern World           │ │ 9 notes · Not started  │
│ 14 notes · Not started │ │                        │
└────────────────────────┘ └────────────────────────┘

Second Semester
5 subjects
…
```

*(Casing above is illustrative only. **Typography and casing follow the existing design system** — all-caps is not a semantic requirement anywhere in this design.)*

### 12.1 Term grouping, including the partially-placed case

- Render term grouping **only when at least one child has a non-null `term_label`.** All-NULL renders the existing flat grid unchanged — this is the backward-compatibility requirement, and it is **frontend code, not schema** (§17.2).
- Term headers are **static text, not collapsible.** A Year has 2-4 terms; collapsing them hides the whole page behind two taps and buys nothing.
- A term header shows the term name and its subject count, plus an in-progress count once anything is started. **No term percentage, no term progress bar, no term due count** (§9.4). Academic Term is a quiet organizational aid, not another progress surface.

**The three Year states, exhaustively:**

| Child term state | Render |
|---|---|
| **All children NULL** | The existing flat Subject list, **exactly as today, with no Term UI at all.** |
| **All children placed** | Normal term groups, ordered by `min(term_order)`. |
| **Mixed placed + unplaced** | Placed term groups first, then a defensive trailing group explicitly labelled **`Term not specified`**. |

> **OWNER DECISION (2026-09-26): partial assignment is invalid authoring input, and the pipeline enforces it.** Within a curated Study Plan, Academic Term is either unused for all Subject Plans or assigned to all of them. The curriculum builder refuses a plan file with some but not all Subject Plans termed, validated per Study Plan (one plan file is one root), with an error naming the Study Plan and the unassigned Subject Plans. The runtime fallback below is unchanged and remains intentional defense in depth. The Year builder and backend accept partial assignment as a transient authoring state.

**On the mixed case — this reverses the original audit recommendation.** The audit proposed leaving unplaced subjects in a trailing group with *no header at all*. That is wrong: in a page that is otherwise clearly grouped, an unheadered run of cards **reads as a rendering defect**, not as an editorial gap. Label it.

- The label is **`Term not specified`**. **Not** "Other", **not** "Ungrouped" — both name a term that does not exist.
- Treat the mixed state as a **curator-quality issue that should normally be corrected before an Official/Reference Study Plan is published.** The render is defensive, not an endorsed authoring state.
- **Do not invent a Term entity merely to solve this presentation case.**

### 12.2 Compact Subject cards — REQUIRED for BSCS Year 1, in Phase A

> **OWNER DECISION (2026-09-26): APPROVED AND REQUIRED.** The audit's "may ship in Phase A or slip to Phase B" is withdrawn.

The current child card (`GoalDetailView`, `collection-detail-page-client.tsx:1679-1751`) is full-size: title, expandable description, note count, readiness %, progress bar, and a mastered/due/not-started line, in a fixed 2-column grid. At 10-15 subjects across 2 terms that is an enormous scroll, especially on mobile — the worst case in this entire design.

**The compact card's information hierarchy is exactly:**

```
Programming Fundamentals I
18 notes · Not started
```

or

```
Programming Fundamentals I
18 notes · 41% ready
```

**Rules:**
- Title.
- Note count.
- **ONE** progress signal — readiness % **or** `Not started`, never both.
- **No description** on the card; it belongs on the Subject page.
- **No mastered/due/not-started breakdown.**
- No other dashboard-like metrics.
- **A progress bar only if it materially helps** rather than duplicating the text signal. It is not required, and duplicating `41% ready` with a bar beside it is the thing to avoid.
- Grid: `1 column mobile / 2 tablet / 3 desktop`. Ten subjects then occupy ~4 desktop rows instead of 5 tall ones, and mobile becomes a scannable list.

**The product principle — and the semantic that must NOT be introduced:**

> **Journey overview surfaces prioritize scanning; compact Subject cards are appropriate for curriculum-scale subject lists.**

**Do not encode `childCount >= 10 ⇒ compact`**, or any other count threshold, as a product semantic. "10 subjects" is an observation about BSCS Year 1, not a rule.

### 12.3 The density gate — derived, and it is the same condition as term grouping

Two owner constraints have to hold simultaneously: compact cards are **required** for BSCS Year 1, and **all-child-terms-NULL must render exactly as today** (§17.2, protecting five live Review Sets). A count threshold is forbidden. That leaves exactly one signal already present in Phase A scope that distinguishes BSCS Year 1 from PNLE:

> **Density is gated on the same condition as term grouping. If any child has a non-null `term_label`, the Year page renders term groups AND compact Subject cards. If every child term is NULL, it renders today's flat grid of full-size cards, byte-for-byte unchanged.**

This is not a count rule and not an artifact-type fork. It is the literal expression of "term grouping and compact cards are one coherent Year-page UX": **one conditional drives both.** A curriculum-sequenced Year is, by definition, the journey-overview surface the principle above describes; an unsequenced Goal is the existing Review Set surface and is left alone.

*Consequence to be aware of, not a problem: a future Year authored with no terms would get full-size cards. If that ever needs to change, it is a deliberate relaxation of the §17.2 invariant and belongs to whichever release makes it — not to Phase A.*

### 12.4 Scalability check

| Subjects | Behavior |
|---|---|
| 4-8, no terms (existing Review Sets) | full-size cards, no term UI — **completely unchanged** |
| 8-10, termed (BSCS Year 1) | compact cards, 2 term groups — one screen on desktop, a short scroll on mobile |
| 10-15, termed | compact cards, 2-4 term groups — term headers become the primary scanning aid |
| 15+ | still works; if it ever becomes common, that is a signal the Year is really two Years, not a signal to add another level |

---

## 13. UX — SUBJECT PLAN

**Most of what the consultation document asks for in its §17 is already built.** Full-row clickable headers, collapsed summary rows with note count and a 3-title peek, a conditional readiness badge, and multiple sections open independently all exist today. The gap is narrower than it looks.

### 13.1 Recommendations

| Question | Recommendation |
|---|---|
| **Default expansion** | **COLLAPSED at all breakpoints**, with one deterministic exception: if the plan has **exactly one section**, expand it (there is nothing to scan, and a lone collapsed row is pure friction). Rationale — screen width should decide *layout density*, not *how much curriculum the learner must process*. Today's default is seeded once at mount from viewport width; **this is the one-constant change**, not a rebuild. |
| **Summary row content** | Already correct. Keep count + title peek. Apply §9.6: show **`Not started`** rather than `0% · 0 due` when there is no evidence. |
| **Full-row click** | Already implemented. Keep. Verify the button exposes `aria-expanded` and controls the panel by id. |
| **Multiple open at once** | Already supported. Keep — accordion-style single-open would fight a learner comparing two sections. |
| **Mobile/desktop parity** | **Identical behavior.** Only grid density and type scale differ. This is the doctrine in §19. |
| **Persist expansion state** | **No.** State is currently ephemeral and that is fine. Persisting it per-section-per-collection in local storage is real state-management surface for marginal benefit, and it fights the "open at the level you can understand the journey" principle by restoring a deep expansion the learner does not remember making. |
| **"Expand all"** | **Yes, one text button in the section list header**, low-prominence. It is the pressure valve that makes collapsed-by-default safe for power users and for anyone scanning for a topic, and it costs almost nothing. **It toggles:** it reads `Expand all` normally, and reads **`Collapse all`** once every Section is expanded — rather than remaining permanently labelled "Expand all" while doing nothing. |
| **Performance with many notes** | Collapsed-by-default *improves* this — a 312-note plan currently renders every row at mount on desktop. Worth stating as a secondary benefit. |

### 13.2 Explicit call on the paused work

The consultation paused this polish pending architecture. **The architecture does not change it.** The Subject Plan page is identical in a Review Set and in a Degree Study Journey — the same child collection, the same sections, the same notes. **This work can proceed in Phase B independently of Phase A** — and the collapsed-by-default change specifically is a one-constant change to the mount-time seed, which is the smallest item in this entire plan.

---

## 14. PROGRESS VOCABULARY

> **Principle: higher levels communicate overall learning state; lower levels surface the next actionable work. No level invents a metric the level below cannot explain.**

This is the refined form of the doctrine. The earlier phrasing — that *every* level speaks identical wording — was too strong and is corrected here: **lower, detailed, actionable scopes legitimately surface due work**, because that is where the learner can act on it. The general learner state vocabulary remains **`Not started` → `In progress` → `Ready`**, and no level invents a fourth.

| Level | Shows | Does NOT show |
|---|---|---|
| **Degree** | Per-Year state only: `Not started` / `N% ready` / `Coming soon` | **No degree %, no degree progress bar, no completion state, no blended due/mastered/not-started counts** |
| **Year** | An overall readiness summary: `10 subjects · 2 semesters · 34% ready`, or `… · Not started` | No due count, no mastered/total, no notes-practiced count |
| **Term** | Subject count, plus an optional in-progress count: `First Semester` / `5 subjects · 2 in progress` | **No percentage, no progress bar, no due count** — a term is a schedule group, not a grade |
| **Subject** | Note count + ONE signal: `18 notes · 41% ready`, or `12 notes · Not started` | Not the mastered/due/not-started breakdown on the card; that is Subject-page content |
| **Section** | Note count + actionable due state when applicable: `6 notes · Not started`, or `6 notes · 3 due` | No percentage when untouched (the existing suppression rule) |
| **Note** | Its own actionable learning state, and `due` when due | Nothing aggregated |

**Rules that follow:**

1. **`0% · 0 due` never appears anywhere.** Zero evidence renders as **`Not started`** (§9.6).
2. **Due counts appear only where they are actionable** — at Section and Note level, where a tap starts practice. A "due" count on a Year or Term header is anxiety without a next action.
3. **One vocabulary only.** The crude `totalNotes / notesWithStudyPack / notesPracticed` set stays out of all Study Journey surfaces (§9.3).
4. **Percentages appear at exactly two levels: Subject and Year.** Not Term, not Section, not Degree. This deliberately avoids the consultation's own stated anti-pattern of a different metric at every level.

---

## 15. CURATOR UX

### 15.1 What the existing builder already does

A curator can already create a root collection, create child Subject Plans under it, add Notes to a child, and set each Note's section via the free-text `label` field with sections grouped in the builder view. **BSCS Year 1's entire structure is authorable today except term assignment.**

### 15.2 The three gaps, in priority order

1. **Term assignment (Phase A, required).** A term label + order control on each subject row in the Year builder. Recommended UX: a small labelled select on each subject card, populated from terms already used in this Year plus "Add term…" — a combobox over existing values rather than free text. *(This follows the repo's own recurring rule that taxonomy-like fields use a shared combobox, never raw freetext — the field is free-text in the schema for flexibility across HEI structures, but the curator UI should offer existing values first to prevent "First Semester" / "1st Semester" drift within a single Year.)*
2. **Attach an existing Subject Plan to a different parent (Phase C, nice-to-have).** The backend reparent endpoint already exists and is capable; the builder simply never exposes a picker. Useful for restructuring, **not** for cross-degree reuse (§6 rejects that).
3. **Degree Journey composition (Phase C).** A small admin surface: create a journey (slug, title, description), attach published Year roots in order. Deliberately minimal — **this is not a curriculum management system.**

### 15.3 Curriculum pipeline impact

The TSV → workbook pipeline currently has no Year/Term column, and `applicable_programs` is required on every row and **must not be overloaded** for sequencing — it answers a different (discovery) question governed by ADR-001.

Adding an `academic_term` column to the plan file is a **spec + builder change** (`review-set-workbook-spec.md` and `build_review_set_workbook.py`), and this repo has an unusually hard-won rule about exactly this: a column added by hand to a generated workbook has been lost twice, and a hand-edited generated column goes stale in place while still rendering and reconciling. **Therefore: extend the spec and the builder first; never hand-add the column to a workbook.** See §19 for what the strategist does in the meantime.

---

## 16. DATA MODEL / API IMPACT

### Phase A — the complete list

**Schema — one migration, two columns, both nullable, no backfill:**

```sql
-- V<next>__collection_term_placement.sql
ALTER TABLE note_collections ADD COLUMN term_label VARCHAR(60);
ALTER TABLE note_collections ADD COLUMN term_order SMALLINT;
```

No index needed — these are only ever read alongside a parent's children, which is already an indexed access path.

**Entity:** two fields on `NoteCollectionEntity`.

**DTOs:** two **optional/nullable** fields on the collection response DTO and on the collection update request. **Nullable, never required** — this is the API-compatibility requirement (see §17.3).

**Service:**
- `updateCollection` (or a narrow `PATCH /collections/{id}/term` — see below) accepts and persists them.
- **Two copy sites, both required** (§7.1a): `persistAdoptedPlan` (`NoteCollectionService.java:1956-2017`, used by `adopt()` and reused per child by `adoptGoal()`) and `createSubjectAddition` (`:2476-2515`, the Official-update additive-merge path that adds a new Subject to an already-adopted Year). **Explicitly not** `persistAdoptedGoal` (`:2019-2057`) — a root collection has no term placement by design. Missing `persistAdoptedPlan` silently drops semester grouping on initial adoption; missing `createSubjectAddition` is worse — it manufactures, via a system merge with no curator involved, the exact mixed placed/unplaced state §12.1 treats as a curator-quality defect (`Term not specified`) on a learner's own copy.

**Endpoint decision — and there is an existing precedent that cuts the other way, so state the reasoning rather than assume.** The repo already exposes `PATCH /collections/{id}/parent` as a **dedicated narrow endpoint for the sibling placement concern**, which is exactly the shape a `PATCH /collections/{id}/term` would take — and §5.1 argues parent, ordering and term are three facets of one concept, so matching that precedent is genuinely defensible.

**Recommendation nonetheless: extend the existing collection update endpoint with two optional fields.** Reasons: (a) extending an existing form with optional fields is additive in both directions, so frontend and backend may deploy in either order, whereas a new endpoint is a form the old frontend does not know and the new frontend depends on — and this repo has already shipped a release whose feature was dead on arrival because the two sides deployed independently without a stated order; (b) a new endpoint owes its own real-request test and its own deploy-ordering statement, which is real Phase A cost for no capability gain; (c) term is set during authoring alongside title and description, not as a standalone move operation the way reparenting is.

**If the owner prefers matching the `/parent` precedent, that is an acceptable alternative — it then owes one MockMvc test that issues a real request with `.contentType(MediaType.APPLICATION_JSON)` and a body** (a direct handler method call is not a substitute; it passes under content-negotiation defects by construction) **and an explicit deploy-ordering statement in the release notes.**

**Frontend:**
- Year page: group children by `term_label` when any is non-null; render flat otherwise; trailing `Term not specified` group in the mixed case (§12.1).
- Term header component (static text + counts, client-side sum; no %, no bar, no due count).
- Builder: term control per subject row.
- **Compact Subject card variant — required, not optional** (§12.2), gated on the same condition as term grouping (§12.3). Its single progress signal carries the `Not started` rule at Subject-card grain (§9.6).

### Phase B — no schema

Section default-expansion constant; `Expand all` / `Collapse all` toggle; remaining `Not started` copy cleanup (Section summary row and any Year-summary case — **the Subject card is already handled in Phase A**); optionally `getCollectionLabels(profileType, collection?)` signature.

### Phase C — new entity, isolated

```sql
degree_journeys       (id, slug UNIQUE, title, description, visibility, created_at, updated_at)
degree_journey_years  (degree_journey_id FK, collection_id FK, position,
                       PRIMARY KEY (degree_journey_id, collection_id))
```

Endpoints: `GET /journeys/{slug}` (public), plus curator CRUD. New route `/journeys/[slug]`. **No FK from `note_collections` to `degree_journeys`** — the reference points one way only, so a Year root remains a fully standalone artifact and existing Review Sets are untouched by construction.

### Phase D — the one missing primitive

A child-collection-grain removal/modification marker, e.g. `note_collection_child_removals (adopted_parent_id, source_plan_id, source_child_plan_id, …)`, consulted by `applySourceUpdate`. **One primitive covering remove + move + reorder** (§8.3).

### Explicitly NOT in any phase

`collection_placements` multi-parent join; **`journey_key` / `journey_order` columns on `note_collections` (the withdrawn Phase A Degree-page fallback — do not reinvent it, §11)**; a collection `type`/`kind` enum in Phase A; any Degree progress endpoint, DTO field, table, or computed aggregate; any Note-level Year/Term field; any change to `ConceptHealth`; any fuzzy-matching / duplicate-detection / similarity infrastructure for Subject Plans (§6.6).

---

## 17. BACKWARD COMPATIBILITY

**Claim: existing board Review Sets (PNLE, CPALE, ALE, LET, Civil Engineering) and every already-adopted copy require zero migration and exhibit zero behavior change.** Here is the proof, by change:

### 17.1 Schema

Two **nullable** columns added with no default and no backfill. Every existing row has `term_label = NULL` and `term_order = NULL`. No constraint, no index, no data rewrite, no lock of consequence. **Nothing else in the schema changes in Phase A.**

### 17.2 Render — *this is where the real regression risk lives, not in the migration*

The migration is trivially additive; the **frontend** is where an existing Review Set could break. A Year page that groups children **unconditionally** would render PNLE and LET under an empty or `Term not specified` header — a visible regression to five live Review Sets caused by a change that touched none of their data. **Compact cards raise the stakes**: an unconditional density switch would also silently restyle every existing Review Set's subject cards.

> **The binding requirement is therefore a render invariant, not a schema property: when every child of a collection has `term_label = NULL`, the page must render exactly as it does today — no term headers, no `Term not specified` group, no extra wrapper, AND full-size Subject cards, not compact ones. This is the single most important test in Phase A.**

The `Term not specified` group of §12.1 exists **only** for the mixed state (some children placed, some not). It must never appear when every child is NULL — that is the same invariant stated from the other side.

### 17.3 API / DTO

New DTO fields are **optional on request and nullable on response**. No existing field is removed, renamed, or made required. This matters specifically because this repo has been bitten before by an API form change that was breaking in both directions and shipped without a deploy-ordering statement. **Phase A as scoped is purely additive, so frontend and backend may deploy in either order** — state that explicitly in the release notes rather than leaving it unsaid.

### 17.4 Adoption / Official update

Both engines are unchanged in shape. The one modification is that **two** copy sites (`persistAdoptedPlan` and `createSubjectAddition` — §7.1a, not `persistAdoptedGoal`) carry two additional fields; for a source with NULL terms, copying NULL is a no-op. Already-adopted copies of existing Review Sets are entirely unaffected.

### 17.5 "Official" semantics

`assertOfficialReviewSetRoot` and the publication boundary are untouched. A Year Study Plan is a publishable root under **exactly the same rules** as a Review Set root — which is precisely why Phase A needs no new publication concept.

### 17.6 Existing data migration required

**None.** Not in Phase A, B, C, or D. Phase C's Degree Journey references Year roots one-directionally and adds nothing to `note_collections`.

---

## 18. PHASED IMPLEMENTATION PLAN

> **Boundary note.** This section defines *what belongs in which phase* and why. It deliberately does **not** define execution strategy — PR sequencing, Codex-vs-inline routing decisions, verification tier, and release shape are the **Release Implementor's** to decide against the then-current repo state under `CLAUDE.md`. Where this section names a routing *observation*, it is evidence for that decision, not the decision.

### Phase A0 — Architecture documentation (first, and cheap)

**Scope:** write `docs/architecture/ADR-003-curriculum-placement-and-hierarchy-depth.md`, plus any source-of-truth documentation updates required to make the architectural decision durable. ADRs are binding and outrank feature docs; without this, a future session finds a recursive FK, concludes a third level is free, and re-litigates the depth question from first principles.

**ADR-003 must capture at minimum these six decisions:**

| | Decision |
|---|---|
| **A** | NoteLib deliberately supports exactly **TWO** persisted collection levels: Year/Goal root → Subject Plan child. Name all three enforcement sites (§2.1.3). |
| **B** | The recursive database FK **does not imply** that arbitrary hierarchy depth is a supported product capability. |
| **C** | **Additional curriculum labels do not automatically become collection entities.** (Term is the worked example: a real curriculum concept that is deliberately a placement column, not a level.) |
| **D** | Academic placement belongs on the **Subject Plan / child collection placement grain** and **NEVER on the canonical Note** — the ADR-001 boundary. |
| **E** | **Degree Journey is not a `NoteCollection`** and must not become a third collection level. |
| **F** | **Degree Journey carries no learner mastery or progress state** — see §9.5, which is a permanent product rule. |

**Not in this phase:** any application behavior. Phase A0 is documentation only.

### Phase A — Minimum correct BSCS Year 1

**Scope follows the approved product behavior, not an item-count target.** The audit's earlier "deliberately 5 implementation items" cap is withdrawn: adding the required compact-card work makes that count inaccurate, and preserving the number would be preserving the wrong thing. Release shape remains a real lever (`CLAUDE.md`'s release-size rule) and the Release Implementor should weigh it — but not by cutting approved behavior.

1. **Academic Term placement support** — migration adding `term_label VARCHAR(60)` + `term_order SMALLINT` to `note_collections`, both nullable, no backfill.
2. **Persistence / DTO / service / adoption preservation required for Term** — entity fields, nullable-optional DTO fields, service persistence, and **carrying both fields through the two required copy sites** (§7.1a): `persistAdoptedPlan` (initial adopt, and per-child inside `adoptGoal()`) and `createSubjectAddition` (Official-update additive-merge adding a new Subject to an already-adopted Year). **Not** `persistAdoptedGoal` — a root has no term placement.
3. **Curator Term assignment** — a term control per subject row in the Year builder (a combobox over terms already used in that Year, per the repo's taxonomy-field rule; never raw freetext).
4. **Year-page conditional Term grouping** — including the three-state behavior of §12.1 (all-NULL flat / all-placed grouped / mixed with a trailing `Term not specified` group).
5. **Compact Subject cards on the Year page** — §12.2's hierarchy and rules, gated per §12.3. **Required, not optional.**
6. **Curriculum authoring pipeline support for academic term** — an `academic_term` column in `review-set-workbook-spec.md` and `build_review_set_workbook.py`; extend the spec and builder, then regenerate. **Never hand-add the column to a generated workbook** (§15.3).
7. **The required regression / invariant tests below.**

**Critical invariant to preserve:**

> **All child terms NULL ⇒ existing Review Set / Goal rendering remains unchanged** — no term headers, no `Term not specified` group, and full-size (not compact) Subject cards.

**Required tests (not optional):**
- **The all-NULL unchanged-render regression test** — the §17.2 invariant, covering both grouping *and* card density. This is the test that protects five live Review Sets.
- **Academic Term placement survives adoption** — assert `term_label` / `term_order` are carried onto the copy by `adoptGoal`. *(Without this, the most likely Phase A defect ships silently: the source Year keeps its semesters, every adopted copy loses them, and nothing fails.)*
- Persistence round-trip through whichever endpoint form is chosen; **if a new endpoint is added, a MockMvc test issuing a real request with `.contentType(MediaType.APPLICATION_JSON)` and a body** — a direct handler method call is not a substitute.
- Grouping/ordering unit test over mixed NULL and non-NULL children, asserting group order by `min(term_order)` and the trailing `Term not specified` group.

**Backend impact:** one additive migration, one entity, one DTO pair, two service paths (update + copy). No data migration, no backfill, no index. **Extending the existing collection update endpoint with two optional fields is preferred over a new endpoint** — see §16 for the full reasoning and the acceptable alternative, which carries its own test and deploy-ordering obligations.

**Frontend impact:** term grouping render, term header component, compact Subject card variant, builder term control.

**Routing observation (evidence, not decision):** Phase A is a migration plus service logic plus a coordinated backend+frontend change, which sits squarely in `CLAUDE.md`'s "write a Codex prompt first" column. **Phase A0 is documentation and sits in the Claude Code column.**

**Also owed before signoff:** update `docs/features/collections.md` (the up-to-date behavioral bible for this area) and add a Backlog Index row — this area currently has **zero** rows for Degree Study Journey / Year Study Plan / Academic Term / BSCS, so it is genuinely new scope with nothing to reconcile against.

### Phase B — Subject Plan UX polish (no schema)

**Scope:** collapsed-by-default Sections at all breakpoints; the single-Section auto-expand exception; the `Expand all` / `Collapse all` toggle; remaining `Not started` cleanup where applicable; any remaining progress-copy cleanup; **and the artifact-level terminology resolver (`getCollectionLabels(profileType, collection?)`) only if the final plan justifies it — it is not required for Phase A.**

**Dependencies:** none — **independent of Phase A and runnable in parallel or first.**

**Narrowed by Phase A:** the `Not started` rule at Subject-card grain now ships with the compact card in Phase A (§9.6). What remains here is the Section summary row and any Year-summary case.

**Verified, so nobody scopes it as a no-op:** Subject/Goal cards do **not** currently suppress `0%` the way Section headers do — `GoalDetailView` (`collection-detail-page-client.tsx:1679-1751`) renders `% ready`, a progress bar, and the full mastered/due/not-started line unconditionally (§9.6).

**Tests:** section default-state unit test at both viewport seeds; a copy/render test asserting `0% · 0 due` never renders for zero evidence.

### Phase C — Degree Journey surface

**Scope:** the real lightweight Degree Journey catalog entity (`degree_journeys` + `degree_journey_years`); Degree landing and discovery (`GET /journeys/{slug}`, the `/journeys/[slug]` route, curator CRUD); **ordered Year references**; per-Year actions and states; the first-class `Coming soon` state; **no Degree progress; no whole-Degree adoption.** Builder reparent-picker optional.

**Phase C is NOT required to make Phase A legitimate.** Phase A ships a genuinely useful standalone artifact under the §11.1 naming rule; Phase C is what earns the broader "Reference Study Journey" promise.

**Dependencies:** Phase A shipped; at least two Years published (otherwise the page has nothing to group).

**Tests:** batched multi-year summary fetch — **explicitly assert one query, not N**; per-year adopted/not-adopted/unpublished state rendering; **an explicit assertion that no aggregate Degree percentage is produced anywhere** — the permanent guard required by §9.5 against a future session "helpfully" adding one.

**Migration needs:** two new tables. **No change to `note_collections`**, and no FK from `note_collections` into them.

### Phase D — Evidence-driven curriculum customization

**Deferred pending real usage.** Do not commit to it until BSCS Year 1 has real adopters and there is evidence of what learners actually want to customize; every action in it is currently a guess, and guesses are cheap to defer and expensive to unwind.

**Scope when it happens:** the child-collection-grain removal/modification marker (§8.2) and the merge engine consulting it; then remove-Subject, move-between-Terms, reorder, add-own-Subject.

**Dependencies:** Phase A (term fields must exist before they can be moved between) plus real adoption evidence.

**Tests:** the merge-engine tests are the whole job — a removed Subject must not resurrect on re-sync; a moved Subject must not be re-termed by an Official update; additive-only must remain provably additive. This phase changes what an adopted plan *means* and touches the merge engine, which is exactly the invariant-interaction class that per-PR diff review cannot see.

---

## 19. WHAT THE NOTE STRATEGIST CAN CONTINUE NOW

**The consultation's §26 assumption is confirmed, with one refinement on where placement metadata lives in the meantime.**

### 19.1 Continue immediately, unblocked, at full speed

- **Subject → Section → Note authoring for every BSCS Year 1 subject.** These boundaries are architecture-independent: a Subject is a child collection, a Section is a note label, a Note is a Note, under **every** model compared in §3. This is the bulk of the work and none of it is at risk.
- The existing TSV header (`plan_no, subject_plan, plan_description, section, note_title, note_subject, domain_context, applicable_programs, status`) is correct and should be used as-is.
- **`applicable_programs` on every row, no exceptions** — the builder refuses a file without it, the Domain Context aggregation depends on it, and it is the column this pipeline has lost twice.
- **`domain_context` decisions per ADR-001** — and note that Year/Term placement **does not and must not** influence Domain Context, Subject, Learner Level, or Applicable Programs. A subject moving from Year 1 to Year 2 changes nothing about a Note.

### 19.2 Keep separate for now — Year / Term / core-vs-optional

Author Year and Term placement in a **separate editorial file**, keyed on `subject_plan` (a column that already exists in the TSV header), and **leave `bscs-year1-target-shape.tsv` untouched** until the spec + builder extension ships in Phase A. Two-column sketch:

```
subject_plan                        academic_term      term_order
Introduction to Computing           First Semester     1
Programming Fundamentals I          First Semester     1
Programming Fundamentals II         Second Semester    2
```

**Why separate rather than just adding the column now:** whether the builder tolerates an unrecognized column is unverified, and this repo's own hardest-won pipeline rule is that a hand-added column to a generated artifact gets destroyed by regeneration *or*, worse, goes stale in place while still rendering and still reconciling by row count. A separate keyed file is safe under either outcome and merges into the pipeline mechanically once the spec lands. **When the spec extension ships, merge by key and regenerate — never hand-edit the workbook.**

### 19.3 Keep purely editorial in v1 — curriculum variability

"Core vs curriculum-dependent," "some schools teach College Algebra, others Precalculus," "Calculus is Year 1 here and Year 2 there" — **do not encode any of this as product data in v1.** No enum, no flag, no column. NoteLib publishes **one** reference sequence and says so in copy (§10.2). If the variability ever needs to be product data, it will be because learners asked to swap subjects — which is Phase D, and the design will be much better informed then than now.

### 19.4 Do not do yet

- Do not encode placement in Subject Plan titles. **Reject** `"BSCS First Year Semester 2 - Discrete Structures I"`; the title is `"Discrete Structures I"` and placement lives in `term_label`. (Under §6's model the Subject Plan shell is per-journey, so a BSIT shell may legitimately be titled differently — but even then the title should name the subject, never its coordinates.)
- Do not author Years 2-4 ahead of Year 1 shipping. The first published Year will teach more about the right shape than three more spreadsheets will.
- Do not create a Degree-level artifact of any kind in the pipeline. In Phase A the degree is a naming convention.

---

## 20. RISKS / THINGS WE MAY BE OVERLOOKING

| # | Risk | Severity | Assessment & mitigation |
|---|---|---|---|
| 1 | **Hierarchy creep** — a future session adds a 3rd collection level because "the curriculum has another label" | **High** | The 2-level cap is invisible in the schema and enforced in three unrelated places. The next person to want a Degree collection will find a recursive FK and conclude it is free. **Mitigation: ADR-003 (Phase A0), naming all three enforcement sites.** Cheapest high-value item in this brief. |
| 2 | **Duplicate Subject Plan shells across degrees have no discovery tooling** | **Medium** | This is the concrete price of §6's (correct) NO. A curator authoring BSIT has **no way to see** that BSCS already contains "Programming Fundamentals I" with a section breakdown and a note set worth copying. The knowledge is not duplicated, but the *authoring effort* is. **Owner-acknowledged and accepted.** **Mitigation (FUTURE, not Phase A):** a *Copy structure from existing Subject Plan* curator action (§6.6), evaluated when the second Degree Journey is actually authored. **No fuzzy matching, duplicate detection or similarity infrastructure in Phase A.** |
| 3 | **Update divergence between per-journey shells** | **Medium** | If BSCS's and BSIT's "Programming Fundamentals I" shells drift (a Note added to one, not the other), learners in two degrees get different material for the same subject. **Mitigation:** the note-level Applicable Programs axis already exists to answer "which programs should this Note appear in" — use it as the curator's checklist. Accept some drift; it is the cost of per-journey curation and is often *correct*. |
| 4 | **Misleading Degree progress** | **High if built** | Fully mitigated by §9.5 — the number is never computed in any phase. Add the Phase C test asserting no aggregate degree percentage exists, because this is exactly the kind of "helpful" addition a future session makes. |
| 5 | **Term fields silently dropped on adoption** | **Medium, high likelihood** | The most probable Phase A defect: the source Year keeps its semesters, every adopted copy loses them, and nothing fails. **Mitigation: a named required test in Phase A.** |
| 6 | **Existing Review Sets regress via unconditional term grouping** | **Medium** | The risk is in render code, not the migration (§17.2). **Mitigation: the all-NULL flat-render invariant as an explicit test.** |
| 7 | **Terminology leakage** ("Review Set" for a freshman) | **Low in Phase A, Medium later** | Not a Phase A problem — BSCS learners are STUDENT profile and already see "Study Plan". It bites when a journey ships whose learners carry BOARD_EXAM profile. **Mitigation: the resolver-axis change in Phase B/C, and treat it as a gate on the second journey rather than a Phase A blocker.** |
| 8 | **False curriculum authority** | **Medium, reputational** | Philippine HEIs vary substantially; a learner whose school sequences differently may conclude NoteLib is wrong. **Mitigation: "Reference Study Journey" naming and an explicit disclaimer in the Degree page's own copy — §10.2. Cheap and effective.** |
| 9 | **Navigation depth: Degree → Year → Subject → Section → Note is 5 taps to content** | **Medium** | Real, and worsened by collapsed-by-default sections. **Mitigation:** the learner lands on their adopted Year, not the Degree, on every visit after the first — so the practical depth is 3. Verify that "Continue" and any home-surface entry point deep-link to the **Year**, not the Degree. |
| 10 | **Mobile overload on a curriculum-scale Year** | **Medium — now mitigated by scope** | Addressed only by the compact card, which is genuinely new work and was previously the item most likely to be cut for time. **It is now required Phase A scope (§12.2), shipping in the same coherent Year-page change as term grouping.** The residual risk is that it gets cut under schedule pressure; the counter is that shipping term grouping without it knowingly retains the worst case in this whole design — a full-size card list in a single mobile column. |
| 11 | **Learner customization complexity** | **Low now, High if started early** | §8.3's single-primitive framing is what keeps Phase D from becoming a merge-engine redesign. **Mitigation: defer until real adopters exist.** |
| 12 | ~~**The parented-child adoptability question (§7.2) is open**~~ **CLOSED 2026-09-26** | **None** | **Verified by direct code + test reading: a parented child Subject Plan IS adoptable standalone today, with no code change** (§7.2, anchored to `NoteCollectionService.java:1031-1061`, `:1850-1860`, `:1874-1879` and `NoteCollectionServiceTest.java:5017-5068`). The residual risk is the opposite of the original one: a later session may add work to "enable" a capability that already exists. **Phase A adds nothing here and owes no UI affordance.** |
| 13 | **Section-level aggregation is client-side only** | **Low** | Fine at current scale and stated here only so nobody assumes server-side section rollup exists. If a Year page ever needs section-level numbers it would need new server work — it does not, and should not. |
| 14 | **ConceptHealth `concept` is free text with no canonical ID** | **Low, informational** | Concepts do not relate across Study Packs. This means "you already mastered this concept in another subject" is **not** expressible today. Worth knowing before anyone promises cross-subject mastery carry-over in a degree context — it is a much larger change than it sounds and is firmly out of scope. |
| 15 | **This brief could be read as approving the Section UI polish immediately** | **Low** | §13.2 is explicit: the architecture does not change that work, and it can proceed in Phase B. But it is Phase B — Phase A is the scope enumerated in §18 and nothing more. |
| 16 | **A future session re-introduces the withdrawn `journey_key` Degree-page fallback** | **Low** | It is an obvious-looking shortcut and it was in an earlier draft of this very document. **Mitigation: it is named in §16's "explicitly NOT in any phase" list and in §11's owner decision. The Degree surface waits for the Phase C catalog entity.** |
| 17 | **Product language outruns the shipped model** | **Medium, reputational** | Phase A ships a Year, not a Degree. Marketing a "BS Computer Science Degree Study Journey" before the Degree surface exists promises four years of a path that is one year long. **Mitigation: the §11.1 naming rule — "BS Computer Science — 1st Year Study Plan" until the Degree entity/surface exists, "BS Computer Science Reference Study Journey" only afterwards.** |
| 18 | **A later session adds work to "enable" individual Subject adoption, which already works** | **Low** | The original open question (risk 12) inverted on verification. **Mitigation: §7.2 records the capability as existing, tested and out of Phase A scope, with file:line evidence. Phase A adds nothing and owes no UI affordance.** |

---

## 21. RELEASE IMPLEMENTOR HANDOFF

*This section is the self-contained brief for a fresh Release Implementor session. It restates only what execution needs; the reasoning behind every line is in the sections cross-referenced. **The Release Implementor owns execution strategy from here** — release shape, PR sequencing, Codex-vs-inline routing, and verification tier are decided against the then-current repo state under `CLAUDE.md`, not prescribed here.*

### 21.1 Approved product model

- The learner mental model is **Degree → Year → Subject → Section → Note**. **Exactly two of those levels are persisted collections:** the Year (root `NoteCollection`) and the Subject Plan (child `NoteCollection`). (§1.2)
- **Degree is not a collection.** In Phase A it is editorial only — a title and a naming convention. In Phase C it becomes a small non-collection catalog entity that *references* Year roots. (§1.3, §4)
- **Section stays a computed grouping** — the free-text `label` on the note-membership row. Unchanged, not a change. (§2.1.2)
- **Academic Term is placement, not a level:** `term_label VARCHAR(60) NULL` + `term_order SMALLINT NULL` on the child Subject Plan row, beside `parent_collection_id`. Free-text label, integer order; group by label, order groups by `min(term_order)`. (§5)
- **Notes are reusable knowledge; Subject Plans are curriculum-shaped arrangements of that knowledge.** Canonical Notes are already many-to-many with collections. Subject Plans are per-journey shells and are **not** shared across Degree Journeys. (§6)
- **Degree shows the journey; Years show learning progress.** (§9.5)
- **Higher levels communicate overall learning state; lower levels surface the next actionable work.** (§14)
- **Phase A's learner-facing promise is "BS Computer Science — 1st Year Study Plan"**, never a complete Degree Study Journey. (§11.1)

### 21.2 Binding invariants

1. **All child terms NULL ⇒ existing Review Set / Goal rendering is unchanged** — no term headers, no `Term not specified` group, and **full-size, not compact, Subject cards**. This protects five live Review Sets (PNLE, CPALE, ALE, LET, Civil Engineering) and is the single most important thing Phase A must not break. (§17.2)
2. **Academic Term placement survives every copy site that creates a child, not just initial adoption** — `persistAdoptedPlan` (used by `adopt()`, and once per child inside `adoptGoal()`) **and** `createSubjectAddition` (the Official-update additive-merge path that adds a new Subject to an already-adopted Year) must both carry `term_label` and `term_order` onto the copy. **Not** `persistAdoptedGoal` — a root has no term placement. Missing the second site is worse than missing the first: it lets the system itself manufacture, with no curator involved, the mixed placed/unplaced state §12.1 treats as a curator-quality defect. (§7.1a — found during Feature Planning, 2026-09-26, not in the original audit.)
3. **Density is gated on the same condition as term grouping** — any non-null child `term_label` ⇒ term groups **and** compact cards; all-NULL ⇒ today's flat grid of full-size cards. **No count threshold** (`childCount >= 10` is forbidden as a product semantic) and no artifact-type fork. (§12.3)
4. **Official update stays additive-only, forever.** It adds Subjects and Note placements; it never removes, reorders, or overwrites a learner's rows. (§8.3)
5. **No Degree progress, in any phase** — no percentage, progress bar, completion state, or blended due/mastered/not-started counts. Per-Year state only. This is a permanent product rule. (§9.5)
6. **Academic placement never touches the Note.** ADR-001's four Note axes are untouched, and `applicable_programs` must not be overloaded to carry Term. (§2.7, §15.3)
7. **The hierarchy stays at two persisted levels.** A third level is not a schema change — it breaks two validators, the binary Goal-vs-leaf render switch, and the adoption/update engine. (§2.1.3)
8. **Phase A is purely additive in both API directions** — new DTO fields are optional on request and nullable on response, so frontend and backend may deploy in either order. **Say so explicitly in the release notes.** If a new endpoint is added instead, that is no longer true and the release owes a deploy-ordering statement. (§17.3, §16)

### 21.3 Phase A scope

**Phase A0 (documentation, first):** write `docs/architecture/ADR-003-curriculum-placement-and-hierarchy-depth.md` capturing decisions **A–F** exactly as enumerated in §18, plus any source-of-truth doc updates needed to make the decision durable. No application behavior.

**Phase A (implementation):**

1. Academic Term placement support — one additive migration, two nullable columns on `note_collections`, no backfill, no index.
2. Persistence / DTO / service / adoption preservation for Term, **including the copy in both `persistAdoptedPlan` (adopt/adoptGoal) and `createSubjectAddition` (Official-update additive-merge)** — not `persistAdoptedGoal`. (§7.1a)
3. Curator Term assignment in the Year builder — a combobox over terms already used in that Year, never raw freetext.
4. Year-page conditional Term grouping, with the three states of §12.1 (all-NULL flat / all-placed grouped / mixed + trailing `Term not specified`).
5. Compact Subject cards on the Year page — title, note count, **one** progress signal; no description, no breakdown, progress bar only if it materially helps. (§12.2)
6. Curriculum pipeline support for `academic_term` — extend `review-set-workbook-spec.md` and `build_review_set_workbook.py`, then regenerate. **Never hand-add the column to a generated workbook.**
7. The regression/invariant tests in §21.5.

**Also owed before signoff:** update `docs/features/collections.md`, and add a Backlog Index row — this area has **zero** rows today.

### 21.4 Phase A non-goals — explicit

- **No Degree landing page, and no `journey_key` / `journey_order` fallback to fake one.** (§11)
- **No Degree Journey entity, table, endpoint or route** — that is Phase C.
- **No Degree progress of any kind, ever.**
- **No whole-Degree adoption, in any phase.**
- **No individual-Subject-adoption work** — it already works today (§21.6) and needs no UI affordance in Phase A.
- **No learner curriculum customization** — no Subject removal, movement between terms, reordering, custom Subjects, or conflict resolution. Note-level removal already exists and is unchanged. (§8)
- **No multi-parent Subject Plans, no `collection_placements` join.**
- **No fuzzy matching, duplicate detection, or similarity infrastructure** for Subject Plans. The *Copy structure from existing Subject Plan* action is future work to be evaluated at the second Degree Journey. (§6.6)
- **No collection `type` / `kind` enum**, and no change to `getCollectionLabels`'s signature — the terminology axis is Phase B/C at the earliest. (§10.1)
- **No Term entity, no term catalog, no term enum** — including as a way to solve the partially-placed presentation case. (§5.3, §12.1)
- **No Section entity, no server-side section aggregation.**
- **No change to `ConceptHealth`, and no new mastery signal at any level.**
- **No collapsed-by-default / Expand-all section work** — that is Phase B. (§13.2)

### 21.5 Required tests and regression guards

| Guard | Why it is required |
|---|---|
| **All-NULL unchanged-render regression** — a Goal whose children all have NULL terms renders with no term UI **and full-size cards** | The §17.2 invariant. Protects five live Review Sets against a change that touches none of their data. |
| **Term survives `adopt()`/`adoptGoal()`** — assert `term_label` / `term_order` on the adopted copy via `persistAdoptedPlan` | The most likely Phase A defect, and it is silent: the source keeps its semesters, every copy loses them, nothing fails. |
| **Term survives an Official-update additive merge** — assert `term_label` / `term_order` on a Subject added via `createSubjectAddition` to an *already-adopted* Year | Found during Feature Planning, 2026-09-26, not in the original audit. Worse than the adoption-time gap: a missed copy here manufactures the `Term not specified` mixed state (§12.1) on a learner's copy with no curator involved. (§7.1a) |
| **Persistence round-trip** through whichever endpoint form is chosen | Ordinary contract coverage. |
| **Real-request test if a new endpoint is added** — MockMvc with `.contentType(MediaType.APPLICATION_JSON)` and a body | A direct handler method call passes under content-negotiation defects by construction; this repo has shipped exactly that defect. |
| **Grouping/ordering over mixed NULL and non-NULL children** | Asserts group order by `min(term_order)` and the trailing `Term not specified` group — the case §12.1 reversed. |
| *(Phase C, recorded now)* **assert no aggregate Degree percentage is produced anywhere** | §9.5 is a permanent rule; the guard is what stops a future session helpfully adding one. |

### 21.6 Verified current-repo facts implementation must preserve

All anchored; do not re-derive, and do not contradict without re-reading the code.

- **One entity, one table, no discriminator.** `NoteCollectionEntity` (`entity/NoteCollectionEntity.java:26`) backs both Goals and Subject Plans; role is decided purely by `parentCollectionId`. The only enum on it is `CollectionVisibility {PRIVATE, PUBLIC}`.
- **The 2-level cap is application-enforced, not schema-enforced:** `validateParentCanAcceptChild` (`NoteCollectionService.java:1794-1801`), `validateChildCanBeNested` (`:1803-1807`), the frontend `isGoalView` / `isTopLevelGoal` binary render switch (`collection-detail-page-client.tsx:1855, 2952`), and one-level iteration in the adoption/update engine.
- **Note↔collection is already many-to-many:** uniqueness is `(collection_id, note_id)`, not global on `note_id`. **Collection parenthood is a single scalar column** and cannot be adapted to multi-parent.
- **Adoption is deep copy:** `adopt()` (`:1031-1061`), `adoptGoal()` (`:1063-1186`). Official update is additive-merge-only and already per-child: `getSourceUpdate()` / `applySourceUpdate()` (`:1188-1342`).
- **A parented child Subject Plan IS adoptable standalone today, with no code change.** `adopt()` gates only on `findByIdAndVisibility(..., PUBLIC)` (`:1037`) and has no parent check; `persistAdoptedPlan` (`:1956`+) always creates a parentless copy; `publishChildCollections` (`:1850-1860`) makes children PUBLIC when the parent Goal is published; `assertOfficialReviewSetRoot` (`:1874-1879`) is called **only** from `getReviewSetPublicationStatus` (`:740`) and `publishReviewSetUpdate` (`:754`) — the curator-only Official-update publish flow — never from `adopt()` or `adoptGoal()`. Tested as a named scenario at `NoteCollectionServiceTest.java:5017-5068`. (§7.2)
- **There are three field-by-field child-collection builders in this file, not two — found 2026-09-26, verified directly in code.** `persistAdoptedPlan` (`:1956-2017`, initial adopt) and `createSubjectAddition` (`:2476-2515`, Official-update additive-merge new-child path) both **must** carry `term_label`/`term_order`. `persistAdoptedGoal` (`:2019-2057`, the root/Goal copy itself) must **not** — a root has no term placement by design, so adding the fields there would be a silent no-op at best. (§7.1a)
- **Mastery % is `round(mastered * 100 / total)` and returns literal `0` when `total == 0`** (`ProgressReportService:625-630`) — the "0% ready" complaint is the formula, not a copy bug.
- **Section header readiness is already suppressed at zero evidence** (`collection-detail-page-client.tsx:301-305`, `total > 0`), **but the Subject child card is not**: `GoalDetailView` (`:1679-1751`) renders `% ready`, a `role="progressbar"` bar, and the full `mastered / due / not started` line unconditionally, in a fixed `sm:grid-cols-2` grid. The compact card replaces this renderer's output and therefore carries the `Not started` rule at Subject-card grain. (§9.6)
- **Section expand/collapse is mostly built already** — full-row clickable header (`:377-397`), collapsed summary with count and 3-title peek (`:301-312`), multiple sections open at once. **Only the default seed is wrong**: computed once at mount from viewport width (`LARGE_VIEWPORT_MIN_WIDTH = 1024`, `:3063-3065`). Phase B.
- **`PATCH /collections/{id}/parent` already exists and works**; the builder simply exposes no picker for it.
- **Terminology resolves on the viewer, not the artifact:** `getCollectionLabels(profileType)` (`lib/collection-labels.ts:98-100`). Correct for Phase A (BSCS learners are `STUDENT` ⇒ "Study Plan"); wrong axis for a later journey.
- **Zero backend hits for "semester", "academicTerm" or "degree"** — no naming collisions.
- **The pipeline TSV header today is** `plan_no, subject_plan, plan_description, section, note_title, note_subject, domain_context, applicable_programs, status`, and `applicable_programs` is required on every row.

### 21.7 Source-of-truth docs that must stay consistent

- **`docs/architecture/ADR-001-canonical-knowledge-architecture.md`** — the four Note axes. Nothing here may add a fifth or overload one. An ADR outranks a feature doc.
- **`docs/architecture/ADR-003-…`** — to be written in Phase A0; becomes binding for hierarchy depth and placement grain.
- **`docs/features/collections.md`** — the current behavioral bible for this area; must be updated before signoff.
- **`docs/curriculum/review-set-workbook-spec.md`** + `build_review_set_workbook.py` — the `academic_term` column lands here first, and the workbook is regenerated, never hand-edited.
- **`docs/product/ROADMAP.md`** Backlog Index — a row is owed; there are none for this area today.
- **`RELEASES.md`** — plus the additive-deploy-order statement of invariant 8.

### 21.8 Known risks and gotchas

- **The silent adoption defect (invariant 2)** is the highest-likelihood Phase A bug and produces no error. **It has two independent instances, not one** — the initial-adopt copy (`persistAdoptedPlan`) and the Official-update additive-merge copy (`createSubjectAddition`, §7.1a). The second is easier to miss because it fires later, on a different code path, well after the feature has already shipped and looked correct in initial testing.
- **The unconditional-grouping regression (invariant 1)** would restyle five live Review Sets through a change that touches none of their data — and compact cards double the blast radius.
- **The mixed placed/unplaced Year is a curator-quality defect**, not a supported state. The `Term not specified` group is defensive rendering; it should normally be corrected before an Official/Reference plan is published.
- **Curator duplication across degrees is an accepted, unmitigated cost in Phase A** (§6.6, risk 2).
- **Navigation depth** (Degree → Year → Subject → Section → Note) is five taps only on a first visit; verify any "Continue" entry point deep-links to the **Year**, not the Degree. (risk 9)
- **`ConceptHealth.concept` is free text with no canonical ID** — cross-subject mastery carry-over is not expressible and must not be promised. (risk 14)
- **The pipeline has lost a hand-added column twice**, once to regeneration and once by going stale in place while still reconciling by row count. Extend the spec and builder; never hand-edit.
- **Full-text term matching is string equality on a free-text label** — the curator combobox exists to prevent "First Semester" / "1st Semester" drift inside one Year. (§15.2)

### 21.9 Items the Release Implementor decides (not owner decisions)

- **Endpoint form:** extend the existing collection update with two optional fields (**preferred**, and additive in both deploy directions), or add a narrow `PATCH /collections/{id}/term` matching the `/parent` precedent (**acceptable**, and then owes a real-request MockMvc test and a deploy-ordering statement). Full reasoning in §16.
- **Release shape** — whether Phase A0 and Phase A ride in one release or two, PR sequencing, and the verification tier under `CLAUDE.md`'s gate.
- **Whether Phase B runs before, alongside or after Phase A** — it has no dependency on Phase A.

---

## 22. OWNER CHECKPOINT — GENUINELY OPEN ITEMS ONLY

**OWNER DECISIONS REMAINING: NONE.**

All seven items of the previous checkpoint are resolved: Subject Plan reuse **approved** (§6), no Phase A Degree landing page **approved** and the `journey_key` fallback **withdrawn** (§11), Degree progress **permanently rejected** (§9.5), learner customization **deferred** (§8), compact Subject cards **required in Phase A** (§12.2), the individual-Subject-adoption verification **closed — it already works, and stays out of scope** (§7.2), and ADR-003 **approved as Phase A0** (§18).

Everything still undecided is execution strategy, which belongs to the Release Implementor (§21.9).

**DO NOT IMPLEMENT FROM THIS DOCUMENT WITHOUT A RELEASE KICKOFF.** This is a feature plan, not a release.
