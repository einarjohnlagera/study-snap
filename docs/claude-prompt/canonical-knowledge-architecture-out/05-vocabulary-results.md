# Vocabulary Audit Results — production, 2026-08-03

`03-course-program-vocabulary.sql` run against production 2026-08-03. **These are the §9 pre-Step-1 baselines and cannot be re-taken once `notes.domain_context` exists.** Recorded here for that reason.

Three of `01`'s estimates change as a result. Two of `03`'s own queries turned out to measure the wrong thing; `04-vocabulary-followups.sql` corrects them.

---

## Headline

| | Result | vs. `01`'s estimate |
|---|---|---|
| Note-side distinct values (A) | **27** | Better — `01` §1.2 feared open-ended soup |
| User-side distinct values (B) | **16** (5 not present note-side) | Union = **32** |
| Character-level collisions (C) | **ZERO** | **Estimate was wrong.** No typo-variant reconciliation exists |
| Non-ASCII values (D) | 5, all consistently spelled | The en-dash is real but not a duplication source |
| Duplicate-content ratio (E) | **0.00%** — 886 official public notes, 0 duplicate groups | As §2.10 predicted — but see "Baseline A is too weak" below |
| Subjects spanning ≥2 programs (F) | **11** | Sharing pattern is already real, empirically |
| Published Review Sets (G) | 27, avg 8.6 direct notes | **Wrong unit** — see below |

Total notes carrying a `course_program`: ~4,280. Official public notes: 886 — up from the 697 recorded 2026-07-28 in the ROADMAP, consistent with active Civil Engineering authoring in the interim.

---

## Revision 1 — Step 2 is much cheaper than scoped

`01` §1.2 said the catalog build could be "a half-day or a two-week job" and that over ~60 values would need "its own release with curator time budgeted." The answer is **32 values in the union with zero character-level collisions.**

**Step 2 is plausibly foldable into Step 1's release** rather than needing its own. That is a real downgrade of the plan's second-biggest cost estimate.

**But the reconciliation work is different in kind, not absent.** Query C only detects collisions after collapsing case and punctuation — it cannot see *semantic* duplicates, and there are several genuine judgment calls in the 32:

- `Bsed` (1 user) — almost certainly BSEd → `Education`
- `Computer Science` (3 users, 0 notes) / `Information Technology` (74 notes) / `Software Engineering` (4 notes) — three catalog entries for an overlapping space, and Query F shows *Computer Science* as a **subject** spanning IT and Software Engineering
- `Engineering` (2 notes) — a **family**, not a program
- `Biology` (1 note) — a **subject** sitting in the program field
- `Professional / Board Exam Review` (5 notes, 13 users), `Civil Service` (7 notes), `Self Study / Personal Learning` (1 user) — **activities or goals**, not programs

## Revision 2 — the field is already conflating four different kinds of thing

The single strongest empirical argument for the four-axis split, straight out of Query A:

| What it actually is | Values | Notes |
|---|---|---|
| Degree program | Education, Nursing, Architecture, Accountancy, Civil Engineering, Information Technology, Pharmacy, Electrical/Mechanical Engineering, Physical Therapy, Law, Medicine, Criminology, Psychology, Business Administration, Aviation | most of 4,280 |
| **Learner level / K-12 track** | Junior High (24), High School (11), Grade School (3), Senior High – STEM (4), – ABM (4), – HUMSS (3) | **49** |
| Program family | Engineering | 2 |
| Subject | Biology | 1 |
| Activity / goal | Professional / Board Exam Review, Civil Service, Self Study / Personal Learning | 12 |

**~49 notes already carry a learner level in the program column.** That upgrades Step 4 (`notes.learner_level`) from "a new capability" to "rescuing data that is in the wrong column today," and gives its backfill a concrete source. It also means these 49 notes currently feed a *learner level* into the prompt's "Domain constraint: treat the course/program above as the authoritative academic domain" line — which is a live, if minor, generation-quality bug today, independent of this whole initiative.

## The motivating case (owner, 2026-08-03) — read this before the evidence below

**The trigger is anticipatory, not retrospective.** Under the Civil Engineering Review Set there is an **Engineering Mathematics** subject plan, and the next authoring step is to create topic notes for the **Algebra** subject inside it. The owner stopped before creating them — not because duplication already hurt, but because those exact Algebra notes will be needed by Mechanical, Electrical, Electronics, Computer, Industrial, Chemical, Mining, Agricultural, Geodetic, and Sanitary Engineering, and authoring them under a single-program model commits to duplicating them ten more times.

So the decision point is **"do not create the duplication in the first place,"** taken at the moment of authoring. That is a stronger position than fixing duplication after the fact, and it is why the work is being done now rather than after Civil Engineering ships.

This matters for how the evidence below is read: the `Strength of Materials` finding is **corroborating evidence that the pattern is real and has already begun**, not the reason for the initiative. Do not present it as the motivating case.

It also constrains the taxonomy (see `06-domain-context-taxonomy.md`): the authoring frame in play is `subject = Algebra` inside a collection named `Engineering Mathematics`, which is the shape the Domain Context value set has to serve.

## RESOLVED (Round 2, `04` run 2026-08-03) — the duplication has already begun; Step 3 is justified

`04`'s Query J answers the (a)/(b) question below **affirmatively for (b): latent duplication already exists**, independently of the motivating case above, and in the same engineering space.

**The smoking gun** — `Strength of Materials`, exact-title match returns 0 across both programs (compact form: `Civil Engineering, 10, 0` / `Mechanical Engineering, 1, 0`), yet:

| Program | Title |
|---|---|
| Civil Engineering | **Stress and Strain in Strength of Materials** |
| Mechanical Engineering | **Stress, Strain, and Material Strength** |

Same knowledge. Two notes. Two programs. Invisible to Baseline A. Civil Engineering's other nine SoM notes (Axial Loading, Beam Deflection, Combined Stresses, Flexural Stress, Pressure Vessels, Shear Force & Bending Moment, Shear Stress Distribution, Columns, Torsion) are the canonical shared-engineering-subject set — every one of them will need a Mechanical / Electrical / Electronics twin under the current model.

**Baseline A's 0.00% is confirmed an artifact of exact-title matching. Do not cite it as evidence of no duplication.** The honest baseline is: zero *exact* duplicates, at least one *semantic* duplicate already present in the two-program engineering case, with the pattern set to multiply by program count.

Consequences for `01`:

- **Step 3 no longer needs the `[CHECKPOINT — due 2027-02-01]`.** The question it was meant to answer in six months is answered now, with data. Retire the checkpoint; Step 3 is justified on evidence.
- The owner has separately confirmed (2026-08-03) that the Civil Engineering Review Set is **mid-build and blocked by exactly this**, which closes the "Open — assembly status" question below. Query I corroborates: of 197 CE official public notes, **95 are in collections (94 across 4 PRIVATE collections, 1 public) and 102 are in none** — a build in progress, not an abandoned or already-finished one.

**Secondary finding, out of scope but worth logging: intra-program duplication from bulk generation.** Within Nursing-Pharmacology alone, Query J shows near-duplicate pairs under the *same* program — "High Alert Medications in Nursing Pharmacology" vs "High Alert Medications in Nursing Practice", and "Comparison of Common Anticoagulants in Nursing Pharmacology" vs "Pharmacology and Clinical Application of Anticoagulants in Nursing Practice". That is a Bulk Generate de-duplication concern, unrelated to this architecture, and should get its own Backlog Index row rather than being folded in here.

**Also worth noting: Nursing-Pharmacology is correctly program-specific, not duplicated.** All 15 titles are nursing-framed ("…in Clinical Nursing Practice", "Medication Administration Rights in Nursing", "Safe Medication Practices in Nursing"), while Pharmacy's single note is generic ("Antibiotics: Mechanism of Action and Resistance"). This is the authoring rule working correctly — the treatment genuinely differs, so separate notes are right. It is a useful counter-example proving the rule discriminates rather than collapsing everything.

## Revision 4 — four of the eleven "cross-program" subjects are actually level artifacts

Cross-referencing Query F against the level-values finding above changes what Query F means:

| Subject | Spans | Actually |
|---|---|---|
| Physics | High School, Senior High – ABM, – STEM | **all learner levels** |
| Algebra | Junior High, Senior High – STEM | **all learner levels** |
| Environmental Science | Junior High, Senior High – STEM | **all learner levels** |
| Science | Grade School, High School | **all learner levels** |
| Biology | Education, High School, Junior High, Nursing | mixed (2 levels, 2 programs) |
| Earth Science | Aviation, High School | mixed |
| Pharmacology, Strength of Materials, Computer Science, Psychology, Civil Engineering | — | genuine cross-**program** |

**Four of eleven are the same subject at different levels, not cross-program sharing at all** — and Query J confirms the mechanism directly: "Photosynthesis Process and Importance" (High School) vs "Photosynthesis and Cellular Respiration in Plants" (Junior High) is one subject at two depths, currently expressed by abusing the program field.

**Note this includes Algebra — the proposal's own headline example.** Today's Algebra spread is a *level* artifact (Junior High + Senior High – STEM), not the eleven-engineering-programs case. That case is genuinely forward-looking and remains unaddressed by anything shipped. Both readings point to the same action, but via different axes: the level half is fixed by Step 4, the program half by Step 3. This is additional support for merging Steps 1/2/4 into one release rather than treating Note Learner Level as a later nicety.

Also: **all 49 level-in-program notes are in zero collections** (Query I second part — Junior High 24→0, High School 11→0, Grade School 3→0, Senior High tracks →0). They are not curriculum content, which makes their backfill low-risk.

## Correction — Baseline B restated from Query H

Query H's hierarchy rollup resolves the structure cleanly. There are **4 published root Review Sets**, each an exam-level shell with 4–7 subject children — 23 children + 4 roots = the 27 Query G returned:

| Comprehensive Review Set | Direct | **Rollup** | Children |
|---|---|---|---|
| 📊 CPALE Comprehensive Review | 0 | **74** | 7 |
| 🩺 PNLE Core Nursing Review | 0 | **63** | 7 |
| 🏛️ Architect Licensure Examination Review | 0 | **52** | 5 |
| 👩‍🏫 LET Comprehensive Review | 0 | **43** | 4 |

**§9 Baseline B, restated: 4 comprehensive Official Review Sets averaging ~58 notes (range 43–74)**, against the stated target of several hundred. The earlier "avg 8.6 notes" figure was per-subject-child and should not be quoted. The 19 program-blank sets from `03`'s Query G are these children — they leave `course_program` blank because the root carries it.

**A general assembly backlog also exists**, visible in Query I's second part and not specific to Civil Engineering: Education 146 official public notes → 43 in a collection / 103 in none; Accountancy 154 → 74/80; Nursing 130 → 61/69; Architecture 90 → 51/39; Information Technology 72 → 56/16. Logged as an observation only — not scoped here, and not a reason to delay this initiative.

## Revision 3 — Baseline A (0.00%) is too weak to settle Step 3

Query E found **0** duplicate title+subject groups. Query F simultaneously found **11 subjects spanning 2+ programs**:

| Subject | Programs | Notes |
|---|---|---|
| **Pharmacology** | Nursing, Pharmacy | **109** |
| Biology | Education, High School, Junior High, Nursing | 11 |
| **Strength of Materials** | **Civil Engineering, Mechanical Engineering** | **11** |
| Physics | High School, Senior High – ABM, – STEM | 5 |
| Algebra | Junior High, Senior High – STEM | 6 |
| Computer Science | Information Technology, Software Engineering | 5 |
| Environmental Science | Junior High, Senior High – STEM | 6 |
| Earth Science, Science, Psychology, Civil Engineering | (2 each) | 2–3 each |

Both results being true means shared subjects exist while no two notes share a title. Two readings, and exact-title matching cannot distinguish them:

- **(a) Benign** — Nursing-Pharmacology genuinely covers different topics than Pharmacy-Pharmacology. Step 3's value is discovery-only, as `01` §0 argues.
- **(b) Latent duplication** — the same topics under differently-worded titles ("Beta Blockers" vs "Beta-Adrenergic Antagonists"). Duplication already exists at scale, 0.00% is an artifact, and Step 3 is worth more than the plan credits.

**Do not read 0.00% as "no duplication exists."** `04`'s Query J was written to settle this by listing the titles.

**It has since been run — the answer is (b).** See the RESOLVED section at the top of this document: `Strength of Materials` carries the same knowledge as two notes under Civil and Mechanical Engineering, and exact-title matching cannot see it. This subsection is retained as the reasoning that led to the query, not as an open question.

## Correction — Baseline B measured the wrong unit

`03`'s Query G counted **direct** membership in `note_collection_items` only. `note_collections.parent_collection_id` has existed since **V83**, and the four exam-level Review Sets returned exactly 0 notes each — while being the only four with a `course_program` set:

- 🩺 PNLE Core Nursing Review (Nursing) — 0
- 📊 CPALE Comprehensive Review (Accountancy) — 0
- 🏛️ Architect Licensure Examination Review (Architecture) — 0
- 👩‍🏫 LET Comprehensive Review (Education) — 0

Those are almost certainly assembly shells whose notes live in child collections. So "avg 8.6 notes" is the **per-subject-Review-Set** figure, not the per-comprehensive-Review-Set figure the initiative's premise is about. `04`'s Query H fixes the unit; Baseline B should be restated from its output.

## Finding — 19 of 27 published Review Sets have no `course_program` at all

Of the 27 published Review Sets, only **8** carry a `course_program`: four Accountancy subject sets (Financial Accounting & Reporting, Taxation, Auditing, Fundamentals of Accounting) and the four zero-note exam shells. The other **19 are program-blank**, and are plainly program-identifiable by title alone:

- **Education** — Educational Psychology (14), Assessment of Learning (11), Curriculum Development (9), Professional Education (9)
- **Architecture** — National Building Code (13), Building Technology (11), Architectural Design (10), Building Utilities (9), Site Planning (9)
- **Nursing** — Medical-Surgical Nursing (13), Fluid/Electrolyte/Acid-Base (10), Psychiatric Nursing (9), Fundamentals of Nursing (9), Pharmacology (9), Pediatric Nursing (7), Maternal and Child Nursing (6)
- **Accountancy** — Management Advisory Services (12), Regulatory Framework & Business Law (10), Financial Management (8)

This is a live signal in its own right: **the curation layer barely uses `course_program` today.** 70% of published Review Sets leave it blank and rely on title and hierarchy instead. That weakens the implicit assumption that program-based discovery is load-bearing right now — and correspondingly weakens Step 3's urgency relative to Step 1's.

## Open — Civil Engineering is the largest official program; assembly status unconfirmed

Not something `01` anticipated, and it bears directly on sequencing.

| Program | Official public notes | Published Review Sets carrying that program |
|---|---|---|
| **Civil Engineering** | **197** | **0** |
| Accountancy | 154 | 4 (+ the CPALE shell) |
| Education | 146 | 0 (+ the LET shell) — but 4 program-blank sets are Education content |
| Nursing | 130 | 0 (+ the PNLE shell) — but 7 program-blank sets are Nursing content |
| Architecture | 90 | 0 (+ the ALE shell) — but 5 program-blank sets are Architecture content |

**Civil Engineering is already the largest official public program in the library** — 197 official public notes across 24 subjects. It is also not an Exam Hub slug (`ExamGoalConfig` covers only ale/pnle/let/cpale), so those notes are discoverable today only through Public Library subject browse.

**ANSWERED (2026-08-03) — the CE Review Set is mid-build, and this architecture is what is blocking it.** Confirmed directly by the owner, and corroborated by Query I: of the 197 CE official public notes, **94 sit across 4 PRIVATE collections, 1 in a public collection, and 102 in no collection at all.** That is a build in progress, held short of publication — not an abandoned effort, and not one already finished.

An earlier draft of this document floated the opposite reading (that authoring was done and assembly was the real gap, so the schema work could wait). **That reading was wrong and is withdrawn.** The blocker is not assembly capacity; it is that continuing to assemble under the current model means committing to a duplicated Algebra / Physics / Strength of Materials set for every subsequent engineering program. Query J's Civil-vs-Mechanical `Stress and Strain` pair is that commitment already beginning to be made.

The duplication cost does not land at some future program #2 — it has already landed, at n=2 programs, with nine more engineering programs queued behind it.

---

## Net effect on `01`'s recommendation

| Step | Change |
|---|---|
| 1 — `notes.domain_context` | **Unchanged, better supported.** The field demonstrably conflates program / level / family / subject / activity |
| 2 — catalog + families | **Cheaper.** 32 values, 0 collisions — foldable into Step 1's release. ~6 semantic judgment calls remain |
| 3 — `note_course_program` | **Justified on evidence. Retire the `[CHECKPOINT — due 2027-02-01]`** — Query J answered its question now, affirmatively. Still the expensive, irreversible step, still multi-release, but no longer a bet |
| 4 — `notes.learner_level` | **Merge into Step 1's release** — see below. No longer a separate step |
| — | Two new items, neither in scope here, each needing its own Backlog Index row: **Bulk Generate intra-program de-duplication** (near-duplicate titles within one program) and the **general assembly backlog** (~330 official public notes across 5 programs in no collection) |

### Steps 1 and 4 should be one release, not two

`01` §5.4 sequenced these separately. The data says merge them:

- Both are additive nullable columns on `notes`, both reversible.
- Both change the same method — `OpenAiLlmStudyPackService.buildGenerationContextBlock` (`:1529-1549`).
- **49 notes currently feed a K-12 grade level into the line that reads "treat the course/program above as the authoritative academic domain … Do not blend in material from unrelated disciplines."** That is not a latent design flaw waiting on Step 4; it is an active generation-quality bug affecting real notes right now, in the exact code Step 1 already touches. Splitting the releases means knowingly shipping a fix that leaves its sibling bug in place.
- The backfill is mechanical for precisely these six values: `Junior High`, `High School`, `Grade School`, `Senior High – STEM`, `– ABM`, `– HUMSS` → `notes.learner_level`, clearing `course_program`.

The one genuinely separable piece is the pool/bank re-keying (`01` §2.3) — `exam_question_pool.learner_level` and `challenge_quiz_question_bank.learner_level` moving off user level, plus the existing-rows policy. That can be its own PR inside the same release rather than its own release.

With Step 2 also foldable (32 values, 0 collisions), the realistic shape is **one release covering Steps 1 + 2 + 4**, and Step 3 gated separately on Query J's answer.
