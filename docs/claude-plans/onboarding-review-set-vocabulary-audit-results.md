# Official Review Set vocabulary audit — RESULTS

**Run against production 2026-08-06** by the owner, using
`docs/claude-plans/onboarding-review-set-vocabulary-audit.sql`. Commissioned by owner decision 4
("audit production first; do not assume; solve the real data we have rather than introducing normalization we
may not need").

**Bottom line: the gate is CLEARED and the resolution layer is not needed.** Program tagging in production is
clean and unambiguous, so the Intent Router can be built on the existing matching behaviour. What *is* needed is
a small guard against future drift — see "What to build instead" below.

---

## Query 1 — vocabulary. The decisive result.

| course_program | plans | matches catalog exact | matches catalog CI | matches exam slug | verdict |
|---|---|---|---|---|---|
| Accountancy | 1 | true | true | false | catalog name (exact) |
| Architecture | 1 | true | true | false | catalog name (exact) |
| Education | 1 | true | true | false | catalog name (exact) |
| Nursing | 1 | true | true | false | catalog name (exact) |

**All four published root collections are tagged with exact catalog names.** No exam slugs, no case drift, no
whitespace drift, no unrecognised values, no mixture.

Three consequences:

1. **No slug→name resolution is needed.** `exam_goal_slug` plays no part in plan matching; it resolves Exam Hub
   program names only. The four slugged catalog rows and the four published plans coincide, but the tag is the
   name, not the slug.
2. **No case-insensitivity layer is needed.** `normalizeForLookup` stays unused. Introducing it would be exactly
   the unnecessary normalization decision 4 warned against.
3. **The earlier "mixed conventions" worry was a fixture artifact, as suspected.** `page.test.tsx:355` uses
   `courseProgram: "LET"` on *both* sides of a mocked call, so it proves nothing about production. The ROADMAP's
   habit of naming the sets by exam ("CPALE 74, PNLE 63, ALE 52, LET 43") is prose shorthand, not the stored tag.

## Query 2 — availability and depth

| course_program | title | items | ready | qualifies |
|---|---|---|---|---|
| Accountancy | CPALE Comprehensive Review | 74 | 74 | true |
| Nursing | PNLE Core Nursing Review | 63 | 63 | true |
| Architecture | Architect Licensure Examination Review | 52 | 52 | true |
| Education | LET Comprehensive Review | 43 | 43 | true |

All four qualify under the practice-first predicate (`itemCount > 0 && readyCount > 0`). Note
`items == ready` **exactly** in every case — 100% of notes in every published set have a generated Study Pack.
The depth predicate is not close to its boundary anywhere, so Branch A will not flicker on marginal content.

## Query 3 — coverage by program

4 of 21 catalog programs have Branch A available (Accountancy, Architecture, Education, Nursing — precisely the
four carrying an `exam_goal_slug`). The remaining 17 fall to the "Coming soon for {Program}" state.

## Query 4 — coverage by USER. This is the number that matters.

218 accounts hold a course/program value. Their distribution:

| Segment | Users | Share | Onboarding branch |
|---|---|---|---|
| **Covered** — Education 58, Accountancy 50, Nursing 40, Architecture 31 | **179** | **82.1%** | Branch A available |
| Catalog program, no plan yet — Medicine 3, Information Technology 2, Business Administration 1, Special Needs Education – Generalist 1, Senior High – ABM 1, Radiologic Technology 1 | 9 | 4.1% | Coming soon |
| **Off-catalog entirely** — Professional / Board Exam Review 14, High School 7, Computer Science 3, Software Engineering 3, Self Study / Personal Learning 1, Bsed 1, Grade School 1 | **30** | **13.8%** | Coming soon |

**This corrects an error in the plan's earlier reasoning.** §1.2 argued from *program count* (4 of 21 covered)
that the "Coming soon" state would be the majority experience. Weighted by actual users it is the opposite:
**Branch A serves ~82% of program-holding users.** Coverage is concentrated in exactly the programs people
actually study. The Coming-soon path is a real minority path (~18%), not the default.

Two secondary findings:

- **The off-catalog population is 30 users (13.8%)** — this is pressure-test finding **C8** sized. Its single
  largest value, `Professional / Board Exam Review` (14 users), is an *activity*, not a program, which the
  `v0.70.0` vocabulary audit already flagged as a semantic judgment call. Any attempt to close the catalog gap by
  adding these as catalog rows would be adding non-programs to a curriculum catalog — the wrong fix.
- `Bsed` appears with **1 user and 0 notes**, consistent with V106's measurement and confirming that V107's
  `Bsed → Education` alias on the *notes* table matches zero rows (pressure-test L7).

## Query 5 — onboarding health baseline (pre-redesign)

| Metric | Value |
|---|---|
| All accounts | 364 |
| `profile_type` NULL | 141 (**38.7%**) |
| Never completed onboarding | 141 |
| `course_program` NULL/blank | 146 |
| **Completed but no course program** | **5** |
| **Completed but no learner level** | **4** |

Two things worth stating plainly:

1. **38.7% never complete onboarding**, closely tracking the ~40% the ROADMAP recorded on 2026-07-28 via an
   independent path. The redesign is aimed at a genuinely broken funnel, not a hypothetical one. **This is the
   pre-change baseline** — re-run Query 5 after slice 5 ships to measure it.
2. **The fire-and-forget learning-context write has actually bitten real users: 5 completed onboarding with no
   course program and 4 with no learner level.** Small, but it confirms the mechanism is live rather than
   theoretical, and those 9 users are in a state the UI can never route them back out of. It also means the
   B0 regression would have compounded on an already-leaking funnel.

---

## What this changes in the plan

| Plan section | Status after the audit |
|---|---|
| §1.1 — "Branch A is not buildable as specified" | **Withdrawn for current data.** Matching works as-is; all production tags are exact catalog names. The four-vocabularies analysis stays true as a *drift risk*, not a present defect. |
| §7-REVISED — audit as blocking prerequisite | **Satisfied. Gate cleared.** The Intent Router is unblocked. |
| §7-REVISED tripwire ("if mixed, re-open sequencing") | **Not triggered.** No mixed conventions. |
| §1.2 — Coming-soon is the majority case | **Corrected.** ~82% of program-holding users are covered; Coming-soon serves ~18%. |
| §12.1 — open question | **Closed by this document.** |

## What to build instead of a resolution layer

Production is clean, but nothing *keeps* it clean — `note_collections.course_program` is unvalidated free text
with no catalog FK, and the admin publish picker is fed by `listCoursePrograms("public")`
(`NoteService.java:1156-1162`), which derives its options from values already present on public **notes**, not
from the catalog. So a curator could publish a plan tagged with any string a public note happens to carry.

**Recommendation — a guard, not a normalizer.** In descending order of cost:

1. **Cheapest and sufficient:** point the admin publish picker at the catalog (`GET /course-program-catalog`)
   instead of `listCoursePrograms("public")`, so a published plan can only be tagged with a catalog name. Purely
   a frontend change to `collection-detail-page-client.tsx:2382-2391`, which already passes `allowCustom={false}`.
2. **Stronger:** add `course_program_id` FK to `note_collections`, mirroring what V106 did for `notes` and
   `users`. Schema change; makes the invariant structural rather than procedural. Probably belongs with any
   future "official" tiering work, not here.
3. **Not recommended:** case-insensitive/slug normalization on the match path. It solves a problem production
   does not have, and it would mask mis-tagged records rather than prevent them — the opposite of decision 4's
   intent.

Option 1 is the recommendation for slice 5. It is small, it removes the drift vector that would have made the
Intent Router unreliable later, and it does not touch the matching path at all.
