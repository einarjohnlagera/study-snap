# Study Plan Hierarchy — Architecture Plan & Audit

Operationalizes `STUDY_PLAN_ARCHITECTURE_V2.md` (the vision). This is the implementation/architecture audit; Codex builds from phased prompts derived here. Claude audits each diff before commit.

## 0. Decision gate (read first) — appetite before architecture

The design below is sound and reusable. **The risk is timing, not design.** Every signal this session points one way: **4 adoptions across ~153 users, 0 post-adopt returns, 0 payers, 0.0% free-quota-hit, and the coverage inventory query is still unrun.** The binding constraint is *whether anyone wants curated plans at all* — not the plan architecture. Pouring a multi-release nested build (migration + nest/unnest + recursive readiness + recursive adopt + metadata UX) into the most ambitious architecture yet, before a single curated plan exists, repeats the trap named all release: committing to a large architectural answer before the last experiment reports. ("Plenty of Codex tokens" is the wrong axis — the cost is building a hierarchy nobody has shown they'll climb, not tokens.)

**The cheap test is already shipped.** A single curated *flat* "PNLE Mastery" plan today already gives ~most of the v2 progress *view*: **overall readiness → per-subject bars** (the readiness page groups by note `subject`) → **module sections** on detail — with ~no new code (it is the curation work already planned). What it does *not* give: each Subject as a separately-navigable / separately-adoptable sub-plan. **That gap is exactly what's worth validating before building.**

**Recommended sequence:**
1. **Curate-first (v0.33.1, already planned).** Build 1–2 real flat plans per top goal; ship the cheap hierarchy *view*. Measure adoption + return.
2. **Only if appetite shows:** build true nesting — and even then start with the **thin Phase 1** (model + set/clear parent + a Goal page listing child plans showing each child's *existing* readiness as-is, no subtree aggregation). Defer Phases 2–4.

"Done now" realistically = **this plan captured + (optionally) the thin Phase-1 slice**, not the 4-phase initiative. The deep hierarchy's clear win is **exam-takers** (per the vision); students/parents get it via labels but will use it shallowly — more reason to validate on the exam cohort first.

## 1. Current-state audit (what PROD v0.33.0 already has)

| v2 Level | In prod | Mechanism |
|---|---|---|
| L1 Goal (umbrella) | ❌ | none — collections are flat (no `parent_collection_id`) |
| L2 Subject plan (own readiness) | ⚠️ partial | a standalone flat collection + plan readiness (`/collections/[id]/readiness`, overall + per-subject), but it can't be a child of a Goal and there's no goal-level rollup |
| L3 Module (sections) | ✅ | label-derived sections within one collection (v0.33.0 + v0.33.1 per-section reorder) |
| L4 Note | ✅ | `note_collection_items` |
| L5 Study Pack | ✅ | unchanged |
| L6 Practice → ConceptHealth → Progress → readiness | ✅ | unchanged |

**The single missing primitive is collection nesting (L1→L2).** Modules already exist as sections; the bottom four levels are done. So we build the hierarchy *on top of* a working base.

**Two locked rules this initiative consciously revisits** (record the reversals where they live):
1. *"No nested/umbrella plans"* (`collections.md`, roadmap) — this initiative adds exactly that, deliberately.
2. *"No mastery/readiness on section/execution rows or headers"* — v2 wants readiness at **every** level incl. per-module and a recursive Goal rollup. Module-level % is a deliberate reversal (phase-gated below).

## 2. Core design decision — the tree primitive

**Recommendation: a self-referential `parent_collection_id` (UUID, nullable) on `note_collections`.** The collection *is* the tree node; reuse all existing collection machinery (items, readiness, adopt, ownership, labels). Modules stay as label-sections within a leaf collection (already shipped).

- A **Goal** = a collection whose children are Subject plans (`parent_collection_id IS NULL` at the top).
- A **Subject plan** = a collection with `parent_collection_id = goal.id`, holding note items + sections (modules).
- A **Note** = an item in a Subject plan.

Why this over the alternatives:
- **vs. a separate `Goal` entity / `goal_label` tag (lighter):** a lightweight tag groups flat plans under one level only and makes the Goal a second-class object (no own metadata, not adoptable as a unit, not curatable as a real plan). `parent_collection_id` keeps a Goal a *first-class collection* (title, description, course_program, publish, adopt) for ~one column. Rejected the tag as too limiting for v2's "Goal is a curated umbrella with metadata."
- **vs. full arbitrary-depth recursion:** the column *allows* depth, but we **constrain the first implementation to two collection levels (Goal → Subject) + sections**, because (a) v2's concrete examples are exactly that, (b) it bounds the recursive readiness/adopt cost, and (c) the model doesn't preclude deeper later. **Decision needed: enforce max depth = 2 via validation, or leave unbounded?** (Lean: enforce for now; relax when a real 3-level need appears.)

**Flexibility / profile coverage (the explicit requirement — not exam-takers only):** the primitive is profile-neutral; only *terminology* differs, via `getCollectionLabels` extended to expose a Goal-level and Subject-level label per profile:

| Profile | Goal (L1) | Subject (L2) | Module (L3, section) |
|---|---|---|---|
| BOARD_EXAM | Exam (PNLE Mastery) | Subject (Pharmacology) | Module |
| STUDENT | Goal / Course (Biology Finals) | Unit (Cell Biology) | Topic |
| PROFESSIONAL | Track (Java Interview Prep) | Skill (Concurrency) | Module |
| TEACHER | Course (Grade 10 Science) | Unit | Section |
| PARENT | Collection | Collection | Section |

Same tree, different labels — no per-profile model fork, no backend `ProfileType` branching (keeps the existing rule).

## 3. Readiness / Progress rollup (recursive, derived)

- A node's readiness = `ProgressReportService` over the **union of all notes in its subtree** (own items + descendants), reusing the existing ConceptHealth recency spine and classification — still **derived, not stored**, still matches `/me/progress` for the same concepts. No new mastery signal, no persisted field.
- **Goal readiness** = aggregate over all descendant Subject plans' notes.
- **Subject readiness** = today's plan readiness (its own notes).
- **Module readiness** (per-section %) = the deliberate reversal of the no-mastery-on-headers rule — **phase 2+, opt-in**, because it changes a locked rule and adds per-section computation.
- Reuse the existing `ReadinessSummary` component recursively; the per-subject bars on a Goal page = each child Subject's overall %.
- **Goal-rollup fork (decide before building):** re-running concept classification over the whole subtree **dedups same-named concepts across different subjects** (`ProgressReportService` dedups by concept name) — e.g. "Assessment" in two subjects collapses into one. Options: (a) **weighted roll-up of child Subject percentages** (clean; no cross-subject conflation) vs (b) re-dedup over all subtree notes (matches `/me/progress` but conflates). **Lean (a).**

## 4. Adoption (recursive)

Adopting a **Goal** copies the goal + its child Subject plans + their notes/packs, preserving the parent links **and** section labels (single-item label copy already works in `copySourceItems`). Reuse `copyNote(..., includeStudyPack=true)` per note (idempotent), build the tree in one owner-scoped transaction per child, per-item isolation (one bad note never sinks the adopt). Re-adopt idempotency keys on `(owner_user_id, source_plan_id)` per node, as today.

## 5. Back-compat & migration

- Add `parent_collection_id UUID NULL REFERENCES note_collections(id) ON DELETE …` (decide cascade vs set-null — lean **set-null** so deleting a Goal doesn't nuke adopted Subject plans; or block deleting a non-empty Goal).
- **Every existing flat plan is unchanged**: `parent_collection_id = NULL`, no children → renders exactly as today. Nesting is additive/opt-in.
- Existing endpoints (`findByIdAndOwnerUserId`, list, readiness, adopt) keep working on leaf collections.

## 6. Phasing (each phase = a Codex prompt; Claude audits the diff)

1. **Model + nesting + Goal view — delivers the v2 layering (Goal → Subject plans).** `parent_collection_id` migration; set/clear-parent endpoint (owner-scoped, **2-level enforced**: parent must be top-level, child must have no children — so cycles are impossible); the UI affordance to nest a plan under a Goal (so plans can be **curated hierarchically**); a Goal detail page that lists its child Subject plans, each showing its **existing** plan-readiness number, plus a **cheap Goal-level readiness**. **Goal % = Σ(child.masteredConcepts) / Σ(child.totalConcepts)** — i.e. a weighted average of child Subject percentages; it sums per-child counts (each child already deduped within itself), so it does **not** re-dedup across subjects (no "Assessment"-collapse). The main `/collections` list shows **top-level** collections only (`parent_collection_id IS NULL`); nested Subjects are reached via their Goal page. This gives the structure + navigation + the headline "LET Mastery 45%". **Delivers:** the GPT layering (LET Mastery → 3 Subject plans, each navigable with readiness) + the Goal %. **Defers:** recursive adopt (P3), per-module % (P2), metadata/est-time/difficulty (P4), arbitrary depth, direct notes on a Goal. Existing flat plans untouched.
2. **Deeper readiness.** Per-module (per-section) readiness % (the deliberate reversal of the no-mastery-on-headers rule, opt-in); any true subtree aggregation beyond the weighted Goal % if a 3rd level ever lands.
3. **Adoption of a Goal** (recursive copy, per-item isolation, re-adopt idempotency).
4. **Metadata + hierarchical UX polish.** Module/note counts, (optional) estimated study time / difficulty, the goal→subject→module navigation, profile-aware Goal/Subject labels.

**Decision (2026-06):** Phase 1 ships **in v0.33.1** (folded into the study-plan release so curation can start with levels in place — accepted that this makes v0.33.1 a large "patch"). Phases 2–4 remain a future, validation-gated initiative (the "Journey hierarchy"): only invest after curating 1–2 real hierarchical Goals and measuring adoption/return.

## 7. Open questions to resolve before Codex (Phase 1)

1. **Max depth:** enforce 2 collection levels (Goal→Subject), or allow arbitrary depth now? (Lean: enforce 2.)
2. **Mixed nodes:** may a node hold *both* child collections and its own note items? (Lean: allow — readiness rolls up own+children — but UX-guide toward clean Goal/Subject split.)
3. **Delete semantics** for a Goal with children: block, set-null children to standalone, or cascade? (Lean: set-null / block; never cascade-delete child plans.)
4. **Sequencing vs v0.33.1 Curated Plan Coverage — THE decision, not a minor one (see §0):** curate-first (validate appetite with the cheap already-shipped view) vs build nesting now. **Strong lean: curate-first; build nesting only if appetite shows, and even then start with the thin Phase 1.** Coverage isn't blocked on nesting; Goals wrap existing plans cheaply once the column exists.
5. **Module-level readiness:** in scope (reversing the locked rule) or deferred? (Lean: defer to Phase 2 opt-in.)

## 8. Anti-drift for this initiative

Reuse the collection model + `ProgressReportService` + `ReadinessSummary` + `copyNote`; **no** new mastery signal, persisted readiness field, AI synthesis, or per-profile pipeline fork; terminology via `getCollectionLabels` only (no `ProfileType` branching in services); readiness stays **derived** and matches `/me/progress`; readiness stays **Free** (decided). The two conscious reversals (nested collections; module-level readiness) are recorded where the original rules live, scoped to this initiative.
