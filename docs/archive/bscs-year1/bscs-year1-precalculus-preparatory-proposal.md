# Precalculus (Algebra and Trigonometry) — Optional Preparatory Plan (proposal, 2026-09-30)

> **Status update 2026-09-30:** D-04, D-26 and D-33 are owner-decided (optional standalone root; course/program unset; Domain Context unset). Shaping file is implementation-ready as of 2026-10-01 (curator and pre-publication checks remain). Proposal only: no production changes, no notes generated, no collections created.

**Owner decision D-04 (2026-09-30):** keep Precalculus as an OPTIONAL/PREPARATORY plan, preserve its coverage and note mapping, do not put it in the default Year 1 root, and use existing product capability rather than inventing an optional-child feature. This document answers *how it is delivered* and preserves all 31 notes.

## 1. Recommendation (PROPOSAL)

Deliver it as a **separate standalone root plan that holds its 31 notes directly** (a parentless collection with Section labels on the note memberships). It is not a child of `BS Computer Science — 1st Year`, and it is not wrapped in a second Year root.

Shaping file: `bscs-precalculus-preparatory-shaping.tsv` — one plan block, **no `academic_term` column** (a root has no placement; the builder treats the single plan block as the leaf root). It builds cleanly (1 plan, 31 rows, 3 bulk-generate batches, one per Subject).

## 2. Why this arrangement (FACT unless marked)

| Question | Evidence | Consequence |
|---|---|---|
| Can a learner adopt one child of a Year on its own? | A standalone `adopt()` of a child exists, but child Subject Plans are **never listed as standalone cards** (`features/collections.md`, Public Plan List and Browse sections), and a never-published child is refused. | A child-only route is not discoverable, so it cannot be "the least disruptive arrangement". |
| Is a root that holds notes directly supported? | ADR-003 decision A: two persisted levels; a row with no parent is a Goal/Year/Review Set. `validateParentCanAcceptChild` rejects nesting under a collection that already holds Notes, i.e. a notes-holding parentless collection is a first-class leaf plan (`features/collections.md`: "deleting a Goal leaves child Subject plans as standalone top-level plans"). | No new capability, no third level, no change to validators or the adoption engine. |
| Can it be published and found? | Leaf publish validates every note is PUBLIC and at least one note exists. Published roots appear in `/collections/published` and in Browse All (alphabetical). | Discoverable without a new surface. |
| Is a Year-root wrapper better? | A Year root needs at least one child Subject Plan; wrapping one plan in a "Year" adds a level of ceremony, a Goal page with one child, and invites a Term. | Rejected: more structure for the same result. |
| Fold it into Discrete Structures I? | Contradicts D-04 (keeps it separate) and puts about 31 notes of optional material in a required course. | Rejected. |

## 3. Consequences to accept (verify each before publishing)

1. **Onboarding can be displaced.** Onboarding, the dashboard and the public library take `plans[0]` of a course/program-scoped list that the backend orders by `updatedAt` descending (`NoteCollectionRepository.findByVisibilityAndCourseProgramAndParentCollectionIdIsNullOrderByUpdatedAtDesc`; `frontend/app/onboarding/page.tsx:1105`). A Precalculus root tagged *Computer Science* would become the BSCS recommendation whenever it was edited more recently than the Year 1 root. **Recommendation D-26: leave the Precalculus root's course/program unset.** Cost: no recommendation slot; it stays reachable in Browse All (alphabetical) and by name in three descriptions. (Course/program is not required to publish a leaf.)
2. **Primary plan.** The first top-level plan a learner adopts becomes their primary plan (`features/collections.md`, Primary Review Set); a later adoption never overwrites it. A learner who adopts the prep course first sees Precalculus as their dashboard primary. Known, unchanged behavior; the description says so.
3. **The link to Discrete Structures I is textual only.** There is no structural link between a root and a child of another root. The prerequisite is named in the Year description, in the Discrete Structures I plan description and in this plan's description (already written into both TSVs). Collection titles and descriptions may carry that framing; Note titles may not.
4. **No term.** The plan carries no Academic Term, consistent with ADR-003 (a root has no placement).
5. **Discrete Structures I stands alone.** Its notes define their own terms and do not depend on Precalculus notes; the algebra it assumes is the CMO prerequisite, not a note.

## 4. Curator steps (owner/curator actions; Claude does not perform them)

1. Nothing to replace: the TSV already carries `(unset)` per D-33; re-run the validation battery before publishing.
2. Create the standalone collection, add the notes with their section labels, leave course/program unset, publish (every note must be PUBLIC).
3. Only then add the pointer sentence to the published Year description if the description is not already in place.

## 5. What the plan contains

- **Learning outcomes and sections:** unchanged from the 2026-09-29 proposal (5 sections: Algebraic Foundations 8, Functions and Graphs 7, Trigonometry 8, Analytic Geometry 5, Sequences and Series 3).
- **Subjects:** the former "Precalculus" shelf is rejected (it mirrors the plan title). Notes sit on existing shelves: Algebra 18, Trigonometry 8, Analytic Geometry 5. Bulk Generate therefore runs 3 batches.
- **Domain Context: `(unset)` on all rows (owner direction D-33, 2026-09-30).** This is pure mathematics and explicitly NOT `Computing`; `ENGINEERING_MATHEMATICS` would import the engineering framing that failed the reuse test on 6 of the 12 candidates. Owner direction D-33: do not add another Domain Context for Precalculus now and do not stretch `Engineering Mathematics`. The first version uses the legal single-program fallback: Applicable Programs Computer Science, Domain Context (unset), Authored Depth COLLEGE; the builder confirms `(unset)` on a single-program set is legal, and the computation-guidance scan still fires (it reads the program name, subject and tags). **Standing constraint:** if an exact Precalculus note is later reused unchanged across a second program, the curator must revisit Domain Context before Study Pack generation, because a multi-program unset note is not generation-ready.
- **Applicable Programs:** unchanged from the 2026-09-29 proposal: Computer Science only on all 31 rows. A curator may add other programs that teach the same material (for example Information Technology or Computer Engineering); this is discovery-only and additive.
- **Candidates:** 12 of the 31 notes had a closest existing engineering-authored candidate. Resolution: 7 confirmed new authoring, 5 require curator inspection, 0 confirmed reuse. None is promoted; see `bscs-year1-reuse-and-metadata-assessment.md`.

## 6. Full note list (all 31 preserved)

### P. Precalculus (Algebra and Trigonometry)

*Optional preparatory course in algebra, functions, trigonometry, analytic geometry and sequences and series, covering the algebra and trigonometry preparation that CHED lists as the prerequisite of Discrete Structures 1. It is a separate, standalone plan for learners who need this preparation before BS Computer Science Year 1; many programs do not teach it as a course.*

- **Why Year 1 / delivery:** OPTIONAL PREPARATORY. CMO 25 s.2015 lists Algebra and Trigonometry as the prerequisite of Discrete Structures 1. Antique and Ateneo teach a Precalculus course in Year 1 First Semester; AICS lists the prerequisite without a course; Cebu and Palawan omit it. Owner decision D-04 (2026-09-30): keep it, delivered as a separate standalone plan, not as a child of the default Year 1 root.
- **Prerequisites:** None. Prepares for Discrete Structures I (CMO prerequisite) and later Calculus.
- **Variability:** HIGH: taught as a course at 1 of 4 CMO-25 schools examined. CURRICULUM_DEPENDENT. A learner with STEM senior-high preparation may skip it. Delivered as its own standalone plan so an adopter can ignore it without disturbing the Year.
- **Coverage:** 0 reuse · 31 new · 0 excluded

**Learning outcomes**

- Manipulate algebraic expressions and solve equations, inequalities and systems. — *PROPOSAL (CMO 25 prerequisite)* → Algebraic Foundations
- Analyse functions and their graphs, including polynomial, rational, exponential and logarithmic functions. — *PROPOSAL* → Functions and Graphs
- Apply trigonometric functions, identities and laws to solve triangles and equations. — *PROPOSAL (CMO 25 prerequisite)* → Trigonometry
- Describe lines and conic sections in the coordinate plane. — *PROPOSAL* → Analytic Geometry
- Work with sequences, series and summation notation. — *PROPOSAL* → Sequences and Series

**1. Algebraic Foundations**

- [NEW] Real Numbers and Algebraic Expressions · *Algebra*
- [NEW] Exponents and Radicals · *Algebra*
  - ↳ closest existing: "Exponents and Radicals in Engineering Mathematics" — **CONFIRMED NEW**
- [NEW] Polynomials, Factoring and the Remainder Theorem · *Algebra*
- [NEW] Simplifying Rational Expressions and Solving Rational Equations · *Algebra* — Renamed: the title "Rational Expressions and Equations" already exists in production (Algebra, JUNIOR_HIGH, WRONG_DEPTH).
  - ↳ closest existing: "Fundamentals of Rational Expressions and Equations" — **REQUIRES CURATOR INSPECTION**
- [NEW] Linear Equations and Inequalities · *Algebra*
  - ↳ closest existing: "Linear Equations and Inequalities in Engineering Mathematics" — **CONFIRMED NEW**
- [NEW] Quadratic Equations · *Algebra*
  - ↳ closest existing: "Quadratic Equations in Engineering Mathematics" — **CONFIRMED NEW**
- [NEW] Complex Numbers · *Algebra*
- [NEW] Systems of Linear Equations · *Algebra*

**2. Functions and Graphs**

- [NEW] Functions: Domain, Range and Notation · *Algebra*
- [NEW] Graphs and Transformations of Functions · *Algebra*
- [NEW] Absolute Value and Piecewise Functions · *Algebra*
- [NEW] Combining and Inverting Functions · *Algebra*
- [NEW] Polynomial and Rational Functions · *Algebra*
- [NEW] Exponential Functions · *Algebra*
- [NEW] Logarithmic Functions and Equations · *Algebra*

**3. Trigonometry**

- [NEW] Angles and Radian Measure · *Trigonometry*
- [NEW] Right-Triangle Trigonometry · *Trigonometry*
  - ↳ closest existing: "Fundamental Trigonometric Ratios in Right-Angled Triangles" — **CONFIRMED NEW**
- [NEW] The Unit Circle and Trigonometric Functions · *Trigonometry*
  - ↳ closest existing: "Unit Circle and Standard Angles in Trigonometry" — **REQUIRES CURATOR INSPECTION**
- [NEW] Graphs of Trigonometric Functions · *Trigonometry*
- [NEW] Trigonometric Identities · *Trigonometry*
- [NEW] Solving Trigonometric Equations · *Trigonometry*
- [NEW] Inverse Trigonometric Functions · *Trigonometry*
- [NEW] Laws of Sines and Cosines · *Trigonometry*
  - ↳ closest existing: "Solving Oblique Triangles Using Trigonometric Laws" — **REQUIRES CURATOR INSPECTION**

**4. Analytic Geometry**

- [NEW] Distance, Midpoint and Straight Lines · *Analytic Geometry*
  - ↳ closest existing: "Straight Lines and Slopes in Analytic Geometry" — **CONFIRMED NEW**
- [NEW] Circles · *Analytic Geometry*
- [NEW] Parabolas · *Analytic Geometry*
  - ↳ closest existing: "Parabolas in Analytic Geometry: Properties and Equations" — **CONFIRMED NEW**
- [NEW] Ellipses · *Analytic Geometry*
  - ↳ closest existing: "Ellipses in Analytic Geometry: Properties and Equations" — **REQUIRES CURATOR INSPECTION**
- [NEW] Hyperbolas · *Analytic Geometry*
  - ↳ closest existing: "Properties and Equations of Hyperbolas in Analytic Geometry" — **REQUIRES CURATOR INSPECTION**

**5. Sequences and Series**

- [NEW] Arithmetic and Geometric Sequences · *Algebra*
  - ↳ closest existing: "Fundamentals of Sequences and Series in Engineering Mathematics" — **CONFIRMED NEW**
- [NEW] Series and Summation Notation · *Algebra*
- [NEW] Factorials, Binomial Coefficients and the Binomial Theorem · *Algebra*
