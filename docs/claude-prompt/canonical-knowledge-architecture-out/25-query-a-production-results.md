# Query A — production results (run 2026-08-06)

Source: `19-slice-2-facet-equivalence-impact.sql` query A, run against **production** by the owner.

## Headline

**56 of 4,787 notes carrying a course/program sit outside the catalog — 1.17%.**

**Local was off by a factor of ~50 and would have inverted the conclusion.** The local dev DB measured 55 of 92
(60%) outside the catalog, with `Software Engineering` alone at 53. That number appears in `19-*.sql`'s own
preamble, explicitly labelled as not-evidence, and this read is why that labelling mattered.

## Outside the catalog — 56 notes across 8 values

| Value | Notes | Why excluded |
|---|---|---|
| Junior High | 24 | bare school **level**, not a program |
| High School | 10 | bare school level |
| Civil Service | 7 | an activity/goal |
| Professional / Board Exam Review | 5 | an activity/goal |
| Software Engineering | 4 | owner ruling, `v0.70.0` — no real curriculum behind it |
| Grade School | 3 | bare school level |
| Engineering | 2 | a **family** name, not a program |
| Biology | 1 | a **subject**, not a program |

**Every one of the 56 is a deliberate, previously-ruled exclusion. Not one is a typo, a casing drift, or an
accident.** Two corroborations worth noting:

- The 37 bare-level notes (66% of the excluded set) are exactly the population `V104`/`V105` reclassified onto
  `domain_context` + `learner_level`, keeping their `course_program` string per ADR-001's second Legacy-data
  corollary. They are behaving as designed.
- `Software Engineering` at **4 notes** matches the `v0.70.0` vocabulary read exactly
  (`15-vocabulary-and-impact-results.md`), which is a useful check that the catalog seed did not drift.

## In the catalog — 4,731 notes across 19 values

Education 1845, Architecture 990, Nursing 929, Accountancy 606, Civil Engineering 214, Information Technology 74,
Pharmacy 31, Electrical Engineering 8, Mechanical Engineering 7, Physical Therapy 7, Senior High – STEM 4,
Senior High – ABM 4, Senior High – HUMSS 3, Medicine 2, Criminology 2, Law 2, Business Administration 1,
Aviation 1, Psychology 1.

Two of the 21 seeded programs hold **zero** notes.

## What this decides — and what it does not

**It changes no ratified decision.** `notes.course_program` stays regardless: after the two-authoring-modes
ratification it is the **personal-notes program field**, not a legacy column, so it was never awaiting removal.
The retirement Backlog row was withdrawn and the `legacy_course_program` rename dropped before this read landed.

**What it converts from unknown to known:** the Slice 2 legacy-string fallback carries **1.17%** of notes, and
that population is composed entirely of intentional exclusions rather than data debt. The fallback is a small,
well-understood, permanent surface — not a growing liability.

## Two findings this surfaced

### 1. The thin-shelf risk is already real, not future

ADR-001 warned that a program carrying a handful of shared notes "looks like a curriculum without being one" and
called for Program-level coverage messaging. Production says that condition exists **today**, before any family
expansion:

- **six catalog programs hold ≤2 notes** — Business Administration 1, Aviation 1, Psychology 1, Medicine 2,
  Criminology 2, Law 2 — and two more hold zero;
- meanwhile the top four (Education, Architecture, Nursing, Accountancy) are **4,370 of 4,731 notes — 92%**.

The coverage design direction recorded in the ROADMAP now has a measurable trigger rather than a hypothetical one.

### 2. Program Family expansion will visibly create thin shelves on first use

The `Engineering` family has three members, and their note counts are wildly uneven: **Civil Engineering 214,
Electrical Engineering 8, Mechanical Engineering 7.**

So the Slice 3 shortcut — one click, all three members — marks canonical notes applicable to two programs that
are effectively empty. **That is the intended behaviour and the entire point of the ADR** (author once, serve
many). But it means the first real curator use of family expansion is also the moment two thin shelves become
visible to learners, which strengthens the case for shipping the Program-level coverage message before heavy
family use rather than after.

## Queries B–E

Not run. B is a rollup of A and is answered above. C (public-note exposure of excluded values), D (whether
off-catalog values are still accruing — after slice 4 that should only be possible on **learner** notes, since
curators are catalog-only) and E (post-`V107` join-row sanity) remain useful and unrun. **D is the most
interesting of the three**: if any *curated* note gains an off-catalog value after slice 4 deploys, that is a bug.
