# BSCS Year 1 — Refinement Handoff to ChatGPT (2026-09-30)

> **Status update 2026-09-30 (final decision pass):** Workstream A is CLOSED (v0.164.0); the default TSV now carries `COMPUTING` on 136 rows and the Precalculus TSV is `(unset)` (D-33). D-07, D-08, D-11, D-14, D-17, D-21, D-26 are owner-approved. The programming reference-language recommendation is restated against the final corpus in `bscs-year1-programming-language-assessment.md` and `bscs-year1-programming-nsm-classification.md`; the counts and Workstream A statements below are historical where they differ.

Targeted refinement of the 2026-09-29 proposal after four owner decisions. **Supersedes `bscs-year1-curriculum-chatgpt-handoff.md`.** No production writes, no notes generated, no collections created; production was read with SELECT only (Subject catalog, 19 candidate notes).
Tags: **FACT** verified evidence · **INFERENCE** academic interpretation · **PROPOSAL** recommended NoteLib decision.

**Readiness:** both TSVs are marked NOT IMPLEMENTATION READY. The curriculum is ready for final shaping once the dependencies in section 7 close; none of them requires redesign.

## 1. What changed after the owner's decisions

| Decision | What changed |
|---|---|
| **D-01** fresh root | Root is `BS Computer Science — 1st Year`; the old private collection is preserved untouched. TSV uses Reuse, not Existing. |
| **D-04** Precalculus optional | Removed from the default Year; now its own standalone plan and TSV. All 31 notes and the research are kept. |
| **D-05** language approach | Approach approved (neutral concepts + one reference language, not pseudocode-only). **Language not approved.** All 64 Programming 1 and 2 notes classified; language-specific titles held. |
| **Scope** | PE and NSTP explicitly excluded; scope statement drafted for the Year description. |
| D-03 | Semester arrangement accepted as a NoteLib reference, unchanged. |

## 2. Updated counts

- **FACT — default Year:** **10 Subject Plans** (5 First Semester, 5 Second Semester), **209 note placements: 45 Reuse · 159 New · 5 Excluded**. 45 distinct existing notes reused, 40 need only the Computer Science tag.
- **FACT — Precalculus:** 1 standalone plan, 31 placements, all New. Total authoring 190 (unchanged: 159 + 31).
- First Semester: Introduction to Computing · Computer Programming 1 · Mathematics in the Modern World · The Contemporary World · Art Appreciation.
- Second Semester: Computer Programming 2 · Discrete Structures I · Understanding the Self · Readings in Philippine History · Purposive Communication.

## 3. How optional Precalculus is delivered (PROPOSAL, D-25)

A **separate standalone public root that holds its 31 notes directly** (five sections). Existing capability only: a parentless collection holding notes is a first-class leaf plan, adopts through `adopt()`, and is listed in Browse All. Child plans are never listed as standalone cards, so a child-only route would not be discoverable. Its TSV has no `academic_term` column (a root has no placement).

**Consequences the owner should accept (FACT, from code and `features/collections.md`):**
1. Onboarding, the dashboard and the public library take `plans[0]` of a course/program list ordered by `updatedAt` desc. A Precalculus root tagged Computer Science could displace the Year 1 root as the BSCS recommendation. **Recommend leaving its course/program unset (D-26).**
2. The first top-level plan a learner adopts becomes their primary plan; adopting the prep course first makes it their dashboard primary.
3. The link to Discrete Structures I is textual only: named in the Year, Discrete Structures I and Precalculus descriptions. Discrete Structures I stands alone.

## 4. Recommended reference language (PROPOSAL, D-27): **C++**

- **FACT:** none of Antique, AICS, Cebu or Palawan names a Year 1 language; Cebu lists Java and Python only as electives. So the choice is NoteLib's.
- **FACT (hand count over the 64 planned titles, same test for all four, three tiers):** hard breaks (a CHED outcome or an owner-named criterion the language cannot honestly teach): **Python 7** (including the CMO "static versus dynamic implementations" outcome), **Java 3**, **C 0**, **C++ 0**. Retitleable wording/tooling gaps: Python 4, Java 1, **C 3**, **C++ 1**. C++ over C for native exceptions, `std::string` and `std::vector`, and OOP continuity; C is the leaner alternative.
- **Cost, stated plainly:** C++ is the hardest to teach a beginner. Mitigation is a stated C++17 subset (in the assessment). Python remains the friendliest; choosing it means reshaping 7 hard-break titles plus 4 retitleable. Fallbacks: C, then Java.
- **Classification (INFERENCE):** Programming 1: 13 neutral · 17 reference-language · 5 split. Programming 2: 7 · 15 · 7. A note is neutral only if every claim and quiz answer is correct in all four languages (for example `-7 / 2` is -3 in C, C++ and Java and -4 in Python). Programming 2 clarifications for pointers, references, dynamic allocation, structures and linked lists are tabulated in the assessment.

## 5. New reuse and metadata discoveries

- **FACT — 19 candidates: 0 confirmed reuse · 12 confirmed new · 7 require curator inspection.** Default Year: 5 new, 2 inspection (Network Security Essentials; Human Judgment in AI). Precalculus: 7 new, 5 inspection (Rational Expressions, Unit Circle, Oblique Triangles, Ellipses, Hyperbolas).
- **FACT:** 6 of the 12 engineering-math candidates fail on framing (title suffix "in Engineering Mathematics" and/or an engineering purpose in the Overview and Key Idea). The rest frame engineering only in "why it matters". Check the Overview and Key Idea, not the applications.
- **FACT:** every candidate carries a metadata gate: engineering notes have no Authored Depth and are shared by 12 engineering programs (setting depth is a cross-program write); IT notes have no Domain Context, so adding Computer Science makes them two-program and needs one.
- **FACT — Subject shelves:** `notes.subject` is free text (no catalog table), and the Subject combobox accepts typed values (`components/notes/subject-combobox.tsx`, `allowCustom`; suggestions come from `listSubjects("mine")`, `bulk-generation-page-client.tsx:169`), so a new shelf needs no code change. None of the 8 proposed shelves exists. **3 kept** (Programming Fundamentals, Discrete Mathematics, Data Structures and Algorithms), **5 rejected** (Precalculus folds into Algebra; Data Representation, Computer Organization, Operating Systems and Social Issues and Professional Practice fold into Computer Science).
- **FACT:** near-duplicate Subjects exist in production: "Probability and Statistics" and "Statistics and Probability".
- **PROPOSAL — GE applicability (D-21/D-31):** per-note rule. Base is the non-Education programs carried by sibling notes of the same GE course (Mathematics in the Modern World siblings: CS, IT, IS; other GE siblings: CS); the six Education programs only where anchored in the existing LET plan, because those tags feed the LET reshape read (Q4). Result: 5 of 23 new GE notes carry the six Education programs; 18 do not.
- **INFERENCE:** two proposed networking notes fail reuse by one clause each (HTTP, IP addressing). Re-scoping them would convert two New into inspection candidates; not done, owner's call.
- **Correction:** the 2026-09-29 research doc said Discrete Structures I includes modular arithmetic while placing it in Discrete Structures II; the plan has integers and divisibility only. Fixed.

## 6. Remaining owner approvals

1. **Reference language**, the title rule (S and M titles carry the language) and **how the language reaches generation (D-35)**: there is no language field, so this is an owner choice between four options in the assessment.
2. **The 3 Subject shelves** (D-14).
3. **GE applicability rule** (D-21) and the **Precalculus course/program unset** setting (D-26).
4. **Filipino** in or out (D-07), **note granularity** (D-11), **logic overlap** between Mathematics in the Modern World and Discrete Structures I (D-17), and confirming the single promoted note "Financial Mathematics in Everyday Life" (D-08).
5. **Year scope statement wording** (D-29), drafted in the journey document.

## 7. Remaining Product UX (Workstream A) dependencies

1. Domain Context for every computing row (`PENDING_PRODUCT_UX_DECISION`).
2. **Also for Discrete Mathematics and Precalculus rows.** They are pure mathematics, not computing; ask explicitly for "technical mathematics for non-engineering programs". `ENGINEERING_MATHEMATICS` would import the framing that failed the reuse test. **Existing legal fallback (FACT):** those 65 rows carry exactly one program (Computer Science), so Domain Context may be left unset (`REVIEW_SET_SHAPING_CONTEXT.md`, ADR-001). The computation-guidance scan still fires, because it reads the program name, subject and tags and matches `compute` in "computer science" and `mathematics` in the subject (`OpenAiLlmStudyPackService.java`, `isQuantitativeContext`). **Cost:** the authoring tradition falls back to the program name rather than a classified tradition, debatable for algebra. **Trap:** tagging a second program later makes each row illegal until a Domain Context is set. The other 102 pending rows (Introduction to Computing, Computer Programming 1 and 2) carry three programs, so they need a Domain Context and are not eligible; the 73 general-education rows already carry `GENERAL_EDUCATION`.
3. Whether the two IT-only inspection candidates can carry Computer Science (both need a Domain Context).
4. **Status of Workstream A (FACT):** an untracked recommendation draft exists at `docs/claude-plans/computing-domain-context-decision.md` (marked "Recommendation only. Nothing here is authorized"). It proposes a `Computing` value and maps Discrete Structures to it, but **never mentions Precalculus** (0 hits) and treats generic algebra as `General Education` or `Engineering Sciences`. It is not an approved decision, so the placeholder stays; if it is approved, Precalculus still needs its own answer.
5. Re-validate both TSVs after the placeholder is replaced. A clean builder run today does not validate Domain Context legality; the builder accepts the placeholder.

## 8. Concerns about academic completeness

- General-education plans remain thin (7–12 notes) because existing GE notes are coarse; computing notes are finer (D-11).
- The language decision gates 44 of the 64 programming notes (S and M). The 20 neutral notes are not automatically free to author first either: there is no language field on generation, so the language reaches a note only through its title, Subject or content (D-35, owner choice).
- Reference-language choice is a trade between learner friendliness (Python) and fit to the CMO memory and structures outcomes (C++/C).
- CHED text was read from an OCR mirror and secondary sources; spot-check against the official PDFs before publication.

## 9. Files (all in `docs/curriculum/`)

Attach for ChatGPT: this file; `bscs-year1-proposed-study-journey.md`; `bscs-year1-decision-log.md`; `bscs-year1-precalculus-preparatory-proposal.md`; `bscs-year1-programming-language-assessment.md`; `bscs-year1-reuse-and-metadata-assessment.md`; `bscs-year1-curriculum-coverage.xlsx`; both TSVs (`bscs-year1-shaping.tsv`, `bscs-precalculus-preparatory-shaping.tsv`); `bscs-year1-curriculum-research.md` (sections 1–3 unchanged). Unchanged inputs GPT already has: the audit handoff and workbook.
