# BSCS Year 1 — Curriculum Rebuild: Handoff to ChatGPT

> **SUPERSEDED 2026-09-30 by `bscs-year1-refinement-chatgpt-handoff.md`.** This describes the 11-plan proposal (Precalculus inside the Year, language-neutral pseudocode, 8 new Subject shelves, 240 placements) that the owner has since revised. Kept as history.

Date 2026-09-29. Status: **proposal only. No production changes, no notes generated, no collections created.**
Tags: **FACT** verified evidence · **INFERENCE** academic reading · **PROPOSAL** recommended NoteLib decision.

**Your job:** review this proposal for academic soundness and product fit, then help the owner settle the approval items in section 6. Do not treat the TSV as implementation-ready. It carries the placeholder `PENDING_PRODUCT_UX_DECISION` for Domain Context (Workstream A is unresolved).

## 1. Proposed structure
Two persisted collection levels only: Year Study Plan → Subject Plan. Academic Term is metadata on the Subject Plan, and Section is a label on each note membership.

| Term | Subject Plans |
|---|---|
| First Semester (6) | Introduction to Computing · Computer Programming 1 · Precalculus (Algebra and Trigonometry) · Mathematics in the Modern World · The Contemporary World · Art Appreciation |
| Second Semester (5) | Computer Programming 2 · Discrete Structures I · Understanding the Self · Readings in Philippine History · Purposive Communication |

No Summer term. **11 Subject Plans, 240 note placements: 45 Reuse, 190 New, 5 Excluded.**

## 2. Evidence base
- **FACT:** CMO 25 s.2015 was read from an OCR mirror. CMO 20 s.2013 was read from secondary sources. The official PDFs were unreachable, so figures need a spot-check before publication.
- **FACT:** Universities read in full were Antique, AICS, Cebu and Palawan State, all CMO-25 based. Ateneo and UPLB were read more weakly and treated as evidence of variability, not as the reference. LPU Batangas returned 403.
- **FACT:** CMO 25 names six common courses (Introduction to Computing, Computer Programming 1 and 2, Data Structures and Algorithms, Information Management, Applications Development). It requires Discrete Structures 1 and 2 and one unnamed 3-unit math elective. It does not name Calculus, Linear Algebra or Probability/Statistics as required.
- **FACT:** CHED does not prescribe GE semester placement.
- **INFERENCE:** Term placement is a consensus of four schools, not a national survey.
- **FACT:** All four CMO-25 schools place Introduction to Computing and Programming 1 in Year 1 First Semester, and Programming 2 and Discrete Structures 1 in Second Semester.

## 3. Mathematics decisions (PROPOSAL)
- **GE math:** Mathematics in the Modern World follows the CHED outline. It is not a substitute for algebra, calculus or discrete mathematics.
- **Technical math:** Precalculus is a First Semester bridge, because CMO 25 lists Algebra and Trigonometry as the Discrete Structures 1 prerequisite. Only 1 of 4 CMO-25 schools teaches such a course, so it is flagged curriculum-dependent and is its own droppable plan.
- **CS math:** Discrete Structures I covers logic, proofs, sets, relations, functions and integers. Counting, modular arithmetic, probability with Bayes and recurrences go to Discrete Structures II (Year 2), per the CMO outcomes.
- **Not in Year 1:** Calculus (0 of 4 schools), Linear Algebra, Probability/Statistics.
- **No split by college:** no evidence supports separate journeys by college affiliation.

## 4. Reuse and authoring
- **FACT:** General Education is the only strong production asset (73 STRONG notes). Computing has effectively zero coverage.
- **FACT:** 45 notes are reused (all General Education, unchanged, with exact production IDs). 40 need only the Computer Science tag. 5 are excluded (teacher-framed or otherwise unfit).
- **FACT:** 19 New notes have a closest existing candidate. These are held and not promoted, pending a curator read.
- **PROPOSAL:** Only one POSSIBLE_REUSE note is promoted ("Financial Mathematics in Everyday Life"), and it is logged as D-08 for confirmation.
- **PROPOSAL:** Computing notes are language-neutral (pseudocode). New Authored Depth is COLLEGE.

## 5. Concerns about completeness
- GE plans are thin (7–12 notes each) because existing GE notes are coarse. Computing notes are proposed finer, at roughly 1 note per 1.5–2 hours.
- Language-neutral programming notes cannot teach syntax alone.
- Precalculus is about 31 new notes yet appears at only 1 of 4 schools.
- About 190 new notes is heavy for one curator on metered generation (about 100 per month). The proposed order in D-12 is Introduction to Computing and Programming 1 first.
- CHED syllabus outlines for five GE courses were not retrieved. The 9 new GE additions need a curator check.

## 6. Decisions needing owner approval
1. **D-01 Year root:** create a fresh Year 1 root, or repurpose the private "BS Computer Science" root. The old root's degree-as-collection structure conflicts with ADR-003, so a fresh root is recommended.
2. **D-03 Terms:** the two 2–2 school splits (Philippine History → Second, Contemporary World → First) are a tie-break choice, not evidence.
3. **D-04:** keep Precalculus in Year 1, or drop it (delete plan 3).
4. **D-05:** language-neutral programming, or one declared reference language.
5. **D-07:** Filipino in or out (out proposed).
6. **D-11:** note granularity (do not re-grain existing GE notes).
7. **D-14:** 8 new Subject shelves. Programming Fundamentals, Discrete Mathematics, Precalculus, Data Structures and Algorithms, Computer Organization, Operating Systems, Data Representation and Social Issues and Professional Practice were not present in the library on 2026-09-29. Re-query before approval.
8. **D-17:** accept the logic overlap between Mathematics in the Modern World and Discrete Structures I.
9. **D-21:** new GE notes carry the existing GE corpus's six teacher-education programs plus Computer Science. This exposes BSCS-authored notes to Education learners.
10. **Workstream A (blocking):** the Domain Context for computing and technical-math notes. Replace the placeholder, then re-validate the whole TSV.

## 7. Institution-dependent subjects
Programming language, Precalculus, Calculus, Probability/Statistics, Filipino, every GE course's term, and whether autonomous schools follow the CMO template at all.

## 8. Validation already run
The TSV builds cleanly through `build_review_set_workbook.py` (11 plans, 240 rows). Every reuse row matches an audit note by exact title, Subject and ID. There are no duplicate titles, and no Year/Semester/BSCS branding in titles. The workbook matches the TSV row for row. **A clean builder run does not validate Domain Context legality**, because the builder accepts the placeholder.
