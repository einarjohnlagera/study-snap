# ADR-003 — Collections have exactly two persisted levels; curriculum placement is a column on the child, never a level and never on the Note

**Status:** **Accepted** — decisions A-F ratified by the owner 2026-09-26 as Phase A0 of `v0.160.0` (`docs/claude-plans/degree-study-journeys-stage1-architecture-audit.md` §18 and §22: "ADR-003 approved as Phase A0"). Documentation only; this ADR changes no application behavior.
**Deciders:** Owner
**Third ADR in this repo.** `ADR-001-canonical-knowledge-architecture.md` governs the Note metadata axes; `ADR-002-quiz-answer-identity-by-text.md` (Proposed) governs quiz answer identity. This one governs the shape of the *collection* hierarchy and where curriculum placement lives. It touches ADR-001 only at a boundary (decision D) and does not amend it.
**Motivating work:** Degree Study Journeys (BS Computer Science Year 1 is the first real multi-semester curriculum), `v0.160.0` Phase A.

### Context

A fresh reader of this codebase is likely to guess three things wrong, and each wrong guess leads to the same bad conclusion, that a third level is cheap:

1. **"Goal" and "Subject Plan" are one entity.** `NoteCollectionEntity` backs one table, `note_collections`, with no `type`/`kind` column. A row with no `parent_collection_id` is a Goal / Review Set / Year; a row with one is a Subject Plan. Role is decided purely by position.
2. **"Section" is not an entity.** It is a nullable free-text `label` on the Note-membership row (`note_collection_items`) and is grouped on the client.
3. **The two-level cap is not in the schema.** `parent_collection_id` is a self-referential foreign key (`V83__collection_parent_hierarchy.sql`) that would happily store any depth. The cap is enforced in application code, in three named places:
   - `NoteCollectionService.validateParentCanAcceptChild` rejects nesting under a collection that already has a parent, and under one that already holds Notes.
   - `NoteCollectionService.validateChildCanBeNested` rejects giving a child collection children of its own.
   - The frontend's `isGoalView` / `isTopLevelGoal` switch in `collection-detail-page-client.tsx` branches the whole collection page on "has children" versus "is leaf", a binary with no third branch.

   The adoption and Official-update engine also iterates exactly one level of children, which makes four mechanisms in practice.

Line numbers for these sites drift and are deliberately not pinned here; grep the names.

A real curriculum, however, has more labels than the model has levels. BSCS Year 1 is *Degree → Year → Semester → Subject → Section → Note*. Without a recorded decision, the next session sees the recursive FK, concludes a Semester level is free, and re-litigates the depth question from first principles.

### Decision

**A. NoteLib deliberately supports exactly TWO persisted collection levels: the Year/Goal root, and the Subject Plan child.** A third level is a designed capability that does not exist, not a schema gap waiting to be filled. Any change to this number must name and change all enforcement sites above together and must supersede this ADR explicitly.

**B. The recursive database foreign key does not imply that arbitrary hierarchy depth is a supported product capability.** The FK's shape is an accident of the `V83` migration; the product contract is decision A. A future session must not treat "the column allows it" as evidence that the code, the render switch, or the adoption engine does.

**C. Additional curriculum labels do not automatically become collection entities.** Academic Term is the worked example: a real curriculum concept that is deliberately two nullable placement columns on the child, `term_label VARCHAR(60)` and `term_order SMALLINT`, and not a level. Term granularity is exactly co-extensive with parent granularity (one parent, therefore one placement, therefore one term), so the child row loses no expressiveness by carrying it. A curriculum label earns an entity only by showing it needs its own identity, ownership, addressability or state; a grouping heading needs none of these. Term is also not a term entity, catalog or enum: free text plus an integer order, grouped by label, groups ordered by `min(term_order)`.

**D. Academic placement belongs on the Subject Plan / child-collection placement grain, and NEVER on the canonical Note.** This is the ADR-001 boundary. A Note is reusable knowledge whose axes are Subject, Domain Context, Note Learner Level, Applicable Programs and Target Audience; where a Note sits in a curriculum is a property of the arrangement, not of the knowledge. Placing a term on the Note would leak one journey's schedule into every other collection that reuses it. `applicable_programs` in particular must never be overloaded to carry a term: it answers *where a Note appears for discovery*, and never reaches a prompt.

**E. A Degree Journey is not a `NoteCollection` and must not become a third collection level.** If a Degree ever becomes a first-class thing (a later, separately-scoped phase), it is a small non-collection catalog entity that *references* Year roots. Subject Plans are per-journey arrangements and are not shared across Degree Journeys; only canonical Notes are.

**F. A Degree Journey carries no learner mastery or progress state.** NoteLib never produces a Degree readiness percentage, progress bar, completion state derived from learning progress, or blended Degree-level due/mastered/not-started counts. A Degree surface may show per-Year state only. This is a permanent product rule and not a deferral: a Degree percentage would be false for a learner who has adopted one Year, and the existing rollup has never had to represent "this child is absent from the total". *Degree shows the journey; Years show learning progress.*

### Consequences

**Enabled.** BSCS-shaped multi-semester Years ship as a column and a render rule, with no new entity and no change to the validators, the page switch, or the adoption engine. Trimester, quarter and summer structures need no code: free text absorbs the vocabulary.

**Costs and limits.**

- **The term must be carried at every child-copy site, and there are two.** A child Subject Plan is built field by field in `persistAdoptedPlan` (serves `adopt()`, and once per child inside `adoptGoal()`) and in the Official-update addition `createSubjectAddition`. A third builder, `persistAdoptedGoal`, copies the root and must NOT carry a term, because a root has no placement. Missing `createSubjectAddition` lets the system itself manufacture a mixed placed/unplaced Year with no curator involved.
- **All child terms NULL means the existing Review Set / Goal rendering is unchanged**: no term headers, no `Term not specified` group, full-size cards. This protects the live Review Sets (PNLE, CPALE, ALE, LET, Civil Engineering). Density (compact cards) is gated on the same condition as grouping, never on a count.
- Term matching is string equality on free text, so the curator control is a combobox over terms already used in that Year, to prevent "First Semester" / "1st Semester" drift.
- Curators duplicate a Subject Plan shell across Degrees; that cost is accepted and unmitigated in Phase A.
- Official update stays additive-only: it adds Subjects and Note placements and never removes, reorders or overwrites a learner's rows.

**Not decided here.** Whether a Degree entity is ever built (Phase C), learner curriculum customization (Phase D), and Subject Plan reuse tooling. Each needs its own scoping and, where it would change decision A or E, a superseding ADR.

### Rejected alternatives

- **A Semester/Term collection level.** Breaks two validators, the binary page switch and the adoption/update engine, for a heading that needs no identity.
- **Term on the Note.** Violates decision D and ADR-001.
- **Term on `applicable_programs`.** Overloads a discovery axis with placement.
- **A term catalog, table or enum.** A curator bulk-edit is the right tool for renaming a term across a Year; a shared vocabulary has no owner.
- **A `journey_key` / `journey_order` fallback to fake a Degree page.** Recreates decision E's violation through columns instead of a level.
