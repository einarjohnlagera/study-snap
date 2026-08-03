# Content Context — proposed taxonomy

**Draft for owner review. Not ratified.** Companion to `01-architecture-critique-and-migration-plan.md` §5.1 and the production evidence in `05-vocabulary-results.md`.

This is the one design input Release A needs, and it is the highest-stakes decision in the initiative — because Content Context replaces `course_program` in the prompt's domain constraint, and a **vaguer** value is a **weaker** constraint than what it replaces (`01` risk R4). This is the one place the new architecture could make generation quality worse.

---

## 1. What the value has to do

Content Context is substituted into `OpenAiLlmStudyPackService.buildGenerationContextBlock` (`:1535-1542`), where it currently receives `course_program`:

```
Course / Program: {X}
Domain constraint: treat the {X} above as the authoritative academic domain. All content,
terminology, examples, and question framing must belong to that domain. Do not blend in
material from unrelated disciplines.
Content calibration: use the {X} above to set depth, vocabulary, terminology, and examples.
```

So the test for any candidate value is concrete: **would a competent author, given only this value plus a subject, produce the intended note?**

- `Engineering Mathematics` + Algebra → engineering-flavored algebra: unit-aware, calculation-heavy, engineering applications. **Passes.**
- `Engineering Foundation` + Algebra → unclear. Foundation of what, at what treatment? **Fails** — too vague to calibrate vocabulary and examples, and materially weaker than the `Civil Engineering` it replaces.
- `Engineering` + Algebra → a family name, not a domain. **Fails.**

That single test rules out the "Foundation"-style values sketched in the original proposal, in favour of names that are real academic domains.

## 2. The rule for choosing a value

> **Content Context is the coarsest label under which the note's treatment is identical.**

Not the program. Not the subject plan. The level at which curricula genuinely *share* the same treatment.

This is decidable, and it explains why the value set is a deliberate **mix** of shared-bundle names and program names — which is correct, not inconsistent:

| Note | Treatment identical across… | Content Context |
|---|---|---|
| Algebra topics | all 11 engineering programs | `Engineering Mathematics` |
| Strength of Materials | most engineering programs | `Engineering Sciences` |
| Structural Analysis | Civil Engineering only | `Civil Engineering` |
| Building Utilities | Architecture only | `Architecture` |

Note the asymmetry against your existing Review Set structure, and that it is expected: the Architecture Review Set has **five** subject-plan children (National Building Code, Building Technology, Architectural Design, Building Utilities, Site Planning) that all share one Content Context — `Architecture` — because their treatment is Architecture-specific. Whereas `Engineering Mathematics` is *both* a subject plan and a Content Context, because its treatment is shared 11 ways.

**Useful sanity check:** Content Context is never *finer* than a subject plan. If a candidate value is narrower than something you'd name a subject plan, it is a subject, not a context.

## 3. Proposed value set

### Tier 1 — required to unblock the Civil Engineering build

These three are the ones the Algebra decision is waiting on.

| Value | Covers | Applicable Programs (default family expansion) |
|---|---|---|
| **`Engineering Mathematics`** | Algebra, Trigonometry, Analytic Geometry, Calculus, Differential Equations, Probability & Statistics, Engineering Economics | all 11 engineering programs |
| **`Engineering Sciences`** | Strength of Materials, Statics, Dynamics, Fluid Mechanics, Thermodynamics, Engineering Materials | most engineering programs — expand explicitly, do not assume all 11 |
| **`Civil Engineering`** | Structural Analysis & Design, Hydraulics, Geotechnical, Surveying, Transportation, Construction Management | Civil Engineering |

**`Engineering Mathematics` is the value that unblocks today's actual work.** Author the Algebra topic notes once under it, add them to the Engineering Mathematics subject plan of every engineering Review Set, and the ten-fold duplication never happens.

**`Engineering Sciences` is where the Strength of Materials notes belong**, and it resolves the duplication `05`'s Query J found: Civil's ten SoM notes and Mechanical's one collapse to one canonical set.

> **Owner/curator validation required.** The subject lists above are my grouping from your data and general knowledge of Philippine engineering curricula — **I have not verified them against the current PRC board syllabi**, and you should not treat them as authoritative. In particular, whether `Engineering Sciences` is shared by all 11 programs or only a subset is a syllabus question, not an architecture question. Query K below lists Civil Engineering's actual 24 subjects so the Tier-1 split can be checked against real content rather than my assumption.

### Tier 2 — covers existing official content

| Value | Covers | Notes today |
|---|---|---|
| **`General Education`** | Biology, Physics, Chemistry, Science, Earth Science, Environmental Science, Mathematics at pre-college treatment, English, Filipino, Social Studies | absorbs the **49 level-in-program notes** — see §4 |
| **`Professional Education`** | Curriculum Development, Assessment of Learning, Educational Psychology, the Teaching Profession | Education, 1,630 notes; matches your existing subject plan of the same name |
| **`Nursing`** | Med-Surg, Psychiatric, Pediatric, Maternal & Child, Fundamentals, **nursing-framed** Pharmacology | Nursing, 868 |
| **`Health Sciences Foundation`** | Anatomy & Physiology, Biochemistry, Microbiology, **generic** Pharmacology | Pharmacy, PT, Medicine |
| **`Accountancy`** | FAR, Taxation, Auditing, MAS, RFBT, Financial Management | Accountancy, 532 |
| **`Architecture`** | National Building Code, Building Technology, Architectural Design, Building Utilities, Site Planning | Architecture, 837 |
| **`Computing`** | Data Structures, OOP, AI foundations, Software Engineering practice | IT 74 + Software Engineering 4 — resolves the overlap Query F flagged |

**`Nursing` vs. `Health Sciences Foundation` is the split Query J empirically justified.** Nursing-Pharmacology's 15 notes are genuinely nursing-framed ("Medication Administration Rights in Nursing", "Safe Medication Practices in Nursing") while Pharmacy's single note is generic ("Antibiotics: Mechanism of Action and Resistance"). Two contexts, correctly — and it is the cleanest existing proof that the reuse rule discriminates instead of collapsing everything.

### Tier 3 — do NOT mint contexts yet

`Business Administration` (1), `Law` (2), `Medicine` (2), `Criminology` (2), `Psychology` (1), `Aviation` (1), `Physical Therapy` (7), `Civil Service` (7). Between 1 and 7 notes each. Let them fall back through the resolver chain (`content_context` → `course_program` → user's `course_program`) until real content exists. Minting a context per thin program is how a curated taxonomy degrades back into the free-text vocabulary this initiative is replacing.

**10 values total for Release A.** Small enough to hold in your head, which is itself a design goal.

## 4. How this resolves the audit's loose ends

| Current value | Resolution |
|---|---|
| `Junior High` (24), `High School` (11), `Grade School` (3), `Senior High – STEM` (4) / `– ABM` (4) / `– HUMSS` (3) | **Not programs and not contexts — these are levels.** Content Context `General Education` + `notes.learner_level` = the level. Retires the 49-note abuse and the active prompt bug |
| `Engineering` (2) — a family | `Engineering Mathematics` or `Engineering Sciences` by content |
| `Biology` (1) — a subject | `General Education` |
| `Bsed` (1 user) — an abbreviation | catalog maps to the `Education` **program**; context `Professional Education` |
| `Computer Science` (3 users, 0 notes) / `Information Technology` (74) / `Software Engineering` (4) | all three survive as **programs** in the catalog; one shared context `Computing` |
| `Professional / Board Exam Review` (5 notes, 13 users) | **A goal, not a context.** Belongs on the user (`study_goal`), never on a note. Notes carrying it need reclassifying by content |
| `Self Study / Personal Learning` (1 user) | same — a goal, not a context |

The last two matter beyond bookkeeping: they are the clearest evidence that `course_program` has been absorbing *user intent* as well as content classification. Content Context must not inherit that job.

## 5. Authoring decision procedure

For the curator, in order:

1. **What is the subject?** → `notes.subject` (Algebra). Unchanged from today.
2. **At what depth was this authored?** → `notes.learner_level`. Independent of everything else.
3. **What is the coarsest label under which this treatment is identical?** → `content_context`. If unsure between a shared bundle and a program name, ask: *would a student in a sibling program be served by this exact note, unchanged?* Yes → the shared bundle. No → the program.
4. **Which programs should surface it?** → Applicable Programs, via the family shortcut, expanded to explicit rows.
5. **Does an existing note already satisfy step 3 for this subject?** → **reuse it**; add it to another Review Set instead of authoring. This step is the entire point and belongs in the admin UI as a prompt, not just in a doc.

## 6. Open questions for the owner

1. **Is `Engineering Sciences` shared by all 11 engineering programs, or a subset?** A syllabus question. It determines the default family expansion.
2. **Does `Engineering Mathematics` split by depth** (board-review vs. college) via `learner_level` alone, or do the boards treat them as genuinely different bundles?
3. **`General Education` for K-12 — one context across Grade School → Senior High, distinguished only by `learner_level`?** I have assumed yes. If the treatment differs more than depth does, it needs splitting.
4. **Should Content Context be visible to non-admin users at all?** `01` §5.3 recommends it as the single note-card badge. Confirm — it is the value learners would see most often.
5. **`Civil Service` (7 notes)** — its own context, or `General Education`? Depends whether the exam's treatment differs from gen-ed.

## 7. Validation query — run before ratifying Tier 1

```sql
-- QUERY K — Civil Engineering's actual 24 subjects, by weight.
-- Checks the Tier-1 split (Engineering Mathematics / Engineering Sciences / Civil Engineering)
-- against real content instead of an assumed curriculum grouping. Any subject that does not
-- fall cleanly into one of the three is a gap in the proposed taxonomy.
SELECT
    n.subject,
    COUNT(*)                                        AS notes,
    COUNT(*) FILTER (WHERE n.visibility = 'PUBLIC') AS public_notes
FROM notes n
WHERE n.course_program = 'Civil Engineering'
  AND n.subject IS NOT NULL AND trim(n.subject) <> ''
GROUP BY n.subject
ORDER BY notes DESC;

-- And the same for the three biggest existing programs, to check Tier 2 the same way.
SELECT
    n.course_program,
    n.subject,
    COUNT(*) AS notes
FROM notes n
WHERE n.course_program IN ('Education', 'Nursing', 'Architecture', 'Accountancy')
  AND n.subject IS NOT NULL AND trim(n.subject) <> ''
GROUP BY n.course_program, n.subject
ORDER BY n.course_program, notes DESC;
```
