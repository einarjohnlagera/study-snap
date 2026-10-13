# BSCS Year 1 — Readiness Statement and Handoff (2026-10-01)

Final shaping pass after owner approval of D-27, D-35 and D-36. **FACT** = verified against the files or the repository; **INFERENCE** = my reading; **PROPOSAL** = a recommendation.

## 1. Readiness interpretation (stated before renaming)

**INFERENCE.** A shaping TSV is *implementation-ready* when it deterministically specifies what should be authored, reused and placed. It does not need every execution checkpoint closed. By that test:

- **Placement is determined.** Every row has a plan, section, order, title, Subject, Domain Context, Applicable Programs, status and (default Year) academic term. The 10 plans are in teaching order and the Programming progression has 0 prerequisite violations.
- **Reuse is determined.** The 45 original Reuse rows were re-checked against live production on 2026-10-01 (section 3). The 7 unverified candidates are **authored NEW**, the safe deterministic state; none is silently promoted. If a curator later confirms one, a single row changes from New to Reuse.
- **One conditional Reuse row, with a defined outcome either way.** D-08 ("Financial Mathematics in Everyday Life") is the only Reuse row the owner approved conditionally. The owner's authored-NEW default does not apply to it, because the owner approved it for reuse and prohibited a duplicate. If the curator rejects it, the row is **held out (Excluded)**, not authored New. So the specification is deterministic whether or not the curator confirms.
- **Domain Context is determined.** 145 rows `COMPUTING` (the 136 rows the owner resolved under the ratified rule, plus 9 new Programming notes from the approved splits and additions, classed by the same two-test rule), 73 `GENERAL_EDUCATION`; Precalculus `(unset)` with exactly one program, legal under D-33.
- **No open owner decision changes the target corpus.** D-27, D-35 and D-36 are approved; the Subject shelves and GE rule are approved; the one remaining wording item (D-29, the Year description) is a builder argument, not a TSV column.

The curator and pre-publication checks (section 5) verify and execute; none changes what the TSV specifies. **Decision: the `NOT-IMPLEMENTATION-READY` marker is removed from both filenames**, and the word "preliminary" with it.

## 2. Final counts (FACT, computed from the TSVs)

| | Default Year (`bscs-year1-shaping.tsv`) | Precalculus (`bscs-precalculus-preparatory-shaping.tsv`) |
|---|---|---|
| Subject Plans | **10** (5 First Semester, 5 Second Semester) | 1 standalone root |
| Note placements | **218** | **31** |
| Reuse / New / Excluded | **50 / 163 / 5** | 0 / 31 / 0 |
| Domain Context | COMPUTING 145, GENERAL_EDUCATION 73 | (unset) 31 |
| Academic term | constant per plan; all 10 plans assigned | no column (a root has no placement) |

Total authoring (New rows): **194** (163 default + 31 Precalculus). The five R4 pilot Notes were recorded as Reuse on 2026-10-06 because they already exist in production (D-40).

**Programming.** Computer Programming 1 = **42** notes; Computer Programming 2 = **31**. **N 31 · S 42** (P1: N 20, S 22; P2: N 11, S 20). 64 original notes, 7 splits, 6 M rows resolved without a split, 2 approved additions.

## 3. Validation (FACT, run on the final files)

| Check | Result |
|---|---|
| Builder, default Year | 10 plans · 218 rows · Excluded=5 · New=163 · Reuse=50; 11 bulk-generate batches |
| Builder, Precalculus | 1 plans · 31 rows · New=31; 3 bulk-generate batches |
| Applicable Programs catalog-exact (against the audit's catalog values) | pass |
| Multi-program rows all carry a real Domain Context; `(unset)` only on a single program | pass |
| Duplicate titles across both TSVs | none |
| Academic term constant per plan; Precalculus has no term column | pass |
| Sections contiguous and non-empty in every plan | pass |
| **Live production read, 2026-10-01 (SELECT only):** all 50 Reuse and Excluded notes exist with the exact title, Subject and `PUBLIC` visibility (0 mismatches) | pass |
| **Live production read, 2026-10-01 (SELECT only):** none of the 199 New titles (as of that read; five of them were later created as the R4 pilot Notes and are now Reuse) exists in production under any owner (case-insensitive) | pass |
| Every section in every plan is served by at least one learning outcome; each outcome's note count recomputed from the final TSV | pass |
| No Year, Semester or BSCS branding in any title | pass |
| TSV and workbook parity (title, Domain Context, programs, status) | pass, 218 rows |
| Every learning outcome appears verbatim in the journey or Precalculus document; every note title appears in its document | pass |
| Programming progression | 101 prerequisite edges, 0 violations; all 9 owner milestones pass |

**Evidence note (FACT).** The two live reads replace the earlier claims that rested on the 2026-09-29 audit; the audit is still the source for Applicable Programs catalog values. **Caveat.** A clean builder run does not validate Domain Context *legality at generation* beyond the single-program rule it checks; the R4 pilot is the generation-quality checkpoint.

## 4. Ordering changes reported

1. **Input Validation** (both notes) moved after Iteration, into its own section.
2. **Tracing and Dry Runs** moved next to the variable-state notes.

No other reordering. Both are real prerequisite problems.

## 5. Remaining actions (none blocks the shaping files)

**Curator verification (before publication or reuse):**
- Read in full the 7 inspection candidates (default Year: Network Security Essentials: Firewalls and VPNs, Artificial Intelligence and Human Judgment; Precalculus: Rational Expressions, Unit Circle, Oblique Triangles, Ellipses, Hyperbolas). Until then they are authored NEW.
- Add the Computer Science tag to the 40 reused notes that lack it.
- Confirm D-08: my full-content read of "Financial Mathematics in Everyday Life" passed; amortization is named, not derived.
- Confirm the Introduction to Computing language-levels note covers compilers and interpreters.
- Overlap checks at authoring: Modular Program Design vs Functions and Procedural Abstraction; Boolean Conditions vs the Discrete Structures I logic notes; Defensive Programming vs Discrete Structures I program-correctness; Unit Testing in C++ vs Designing Test Cases.
- Confirm the Year description wording (D-29) when the collection is created.
- Re-query the Subject and program catalogs before the first authoring batch (they decay).

**Pre-publication and generation checks:**
- **R4 pilot** on five varied Computing notes, with the **language-drift** check (three N, two S) and the unmeasured quantitative-guidance nuance for Subjects without a keyword.
- Spot-check CHED figures against the official PDFs (CMO 25 and 20 were read from an OCR mirror and secondary sources).
- Retrieve the CHED syllabus outlines for the five GE courses whose outlines were not obtained, before authoring the nine GE additions.
- Apply the memory guardrail when the memory, allocation and linked-list notes are authored and reviewed.

## 6. Does anything still prevent authoring from beginning?

**No genuine blocker (INFERENCE).** Authoring can begin on the 163 default-Year New notes and, separately, the optional Precalculus plan. **Suggested order (PROPOSAL):** Introduction to Computing and Computer Programming 1, then Discrete Structures I and Computer Programming 2, then the general-education additions, Precalculus last; run the R4 pilot on the first Computing notes before bulk generation. Two cautions: the 40 reused notes need the tag before publication (placement is not gated on Applicable Programs, so it does not block building the Year), and metered generation plus a single curator make this about 199 notes of work.

## 7. Repository citations to be aware of (FACT)

`docs/architecture/ADR-001-canonical-knowledge-architecture.md` (the Computing amendment) and two Workstream A plans cite the old path `bscs-year1-preliminary-shaping.NOT-IMPLEMENTATION-READY.tsv`, and ADR-001 states its figures as "136 of the file's 209" rows. Those were true of the file at ratification and describe the earlier state. I did not edit them; the owner may want the citation updated or annotated in a docs branch.

**Checkpoint denominator shift (FACT).** ADR-001's Computing checkpoint cites a known single-program baseline of 34 of 136 rows (about 25%). The default TSV now has 145 `COMPUTING` rows, so the baseline is 34 of 145 (about 23%): all 9 new Programming notes carry three programs. The ROADMAP checkpoint row uses the older denominator; updating it is the owner's call.

## 8. Files

All in `docs/curriculum/`: `bscs-year1-shaping.tsv`, `bscs-precalculus-preparatory-shaping.tsv`, `bscs-year1-curriculum-coverage.xlsx` (regenerated, never hand-edited), `bscs-year1-decision-log.md`, `bscs-year1-proposed-study-journey.md`, `bscs-year1-programming-nsm-classification.md`, `bscs-year1-programming-language-assessment.md`, `bscs-year1-precalculus-preparatory-proposal.md`, `bscs-year1-reuse-and-metadata-assessment.md`, and this file.

**No production writes, no Note or Study Pack generation, no collection creation or publication, no code or deployment, no new product fields.** The Learning Goal / Preparation Goal workstream remains parked.
