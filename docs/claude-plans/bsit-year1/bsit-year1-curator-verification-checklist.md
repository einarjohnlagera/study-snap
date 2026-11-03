Status: Phase B draft, 2026-11-03. Checklist for the curator. NOT implementation-ready. Nothing here has been done by the Strategist unless stated.

# BSIT Year 1: curator verification checklist

Exact Note ids for every placement are in `bsit-year1-reuse-id-manifest.tsv` (columns: plan, section, title, `note_id`, status, bucket, proposed change). **Reuse by id, never by title.** Eight title-matching copies exist for each reused GE title; the manifest id is the copy that sits in the BSCS Year 1 plans.

## A. Before the collection is built
- [ ] **Re-read production state.** Everything in the manifest is a snapshot from 2026-11-03. Confirm the ids still exist and are in the BSCS plans.
- [ ] **Pin canonical identity for the 50 reused GE titles.** Confirm the manifest id is the intended copy (the BSCS plan membership, highest collection count, Information Technology tag where present). Do not merge, delete or edit the other seven copies.
- [ ] **Run the GE metadata SQL** (`bsit-year1-ge-metadata-correction.sql`) after STEP 0 guards pass: 3 Notes, 6 rows. Verify per the file.
- [ ] **Decide Q8 (Counting Note)** before the Discrete Mathematics plan is finalized.

## B. Discrete Mathematics (29 conditional Notes + 2 verified)
- [ ] Read the 29 conditional bodies in full for program-neutral framing and an appropriate level of rigor. They were screened by keyword and sentence-level read only. List: every `Conditional Reuse` row of the manifest.
- [ ] Confirm the two verified Notes (Boolean Algebra and Digital Logic; Mathematical Induction).
- [ ] Confirm or reverse the three held-out Notes (Strong Induction; Proving Program Correctness; Logic in Programming and Software Specifications). A reversal is one TSV row.
- [ ] Check that no retained proof Note depends on loop invariants as a prerequisite (Proof by Contradiction, Proof by Cases and Counterexamples, Divisibility, Greatest Common Divisors mention invariants in passing).
- [ ] Only after the above: approve the separate SQL adding Information Technology to the confirmed Notes.
- [ ] Obtain CHED MS101 outcomes or one BSIT Discrete Mathematics syllabus to check coverage (assessment section 4).

## C. Programming (73 Notes, Q1)
- [ ] Spot-check the 42 C++-specific Notes for suitability to a BSIT learner (framing, depth, assessment treatment). Reuse is approved in principle; suitability is a content check.
- [ ] Confirm the 31 neutral Notes contain no C++ drift (BSCS D-35 acceptance check).

## D. Metadata-ready Notes (176)
- [ ] Spot-check at least 20, weighted to Mathematics in the Modern World Notes with computing flavour (Codes and Data Transmission, Cryptography, Graphs, Modular Arithmetic) and Introduction to Computing Notes written for a CS audience (Professional Societies, Analyzing Organizational Computing Solutions).
- [ ] Confirm each has explicit Domain Context and `COLLEGE` Authored Depth (read: all do).

## E. Study Packs
- [ ] Every one of the 213 source Notes has exactly one Study Pack (count read). Check that each pack is current and healthy. Reuse must not trigger regeneration; do not regenerate unless the owner confirms.

## F. Collection build (when authorized later)
- [ ] Create the fresh root `🖥️ BS Information Technology — 1st Year` and ten Subject Plans (First Semester 5, Second Semester 5).
- [ ] **Set `term_label` and `term_order` on each plan.** They are NULL on every BSCS plan in production; do not assume a default.
- [ ] Do not touch the BSCS root or its legacy 25-item plan.

## G. Source checks
- [ ] Spot-check the CHED figures against the official CMO 25 PDF (the evidence used an OCR mirror).
- [ ] Optional: read more BSIT curricula (BatStateU, LPU, PCU were unreadable).
