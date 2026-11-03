Status: Phase B final shaping, 2026-11-03. Checklist for the curator. NOT implementation-ready. Nothing here has been done by the Strategist unless stated.

# BSIT Year 1: curator verification checklist

Exact Note ids for every placement are in `bsit-year1-reuse-id-manifest.tsv` (columns: plan, section, title, `note_id`, status, bucket, proposed change). **Reuse by id, never by title.** Eight title-matching copies exist for each reused GE title; the manifest id is the copy that sits in the BSCS Year 1 plans.

## A. Before the collection is built
- [ ] **Re-read production state.** Everything in the manifest is a snapshot from 2026-11-03. Confirm the ids still exist and are in the BSCS plans.
- [ ] **Pin canonical identity for the 50 reused GE titles.** Confirm the manifest id is the intended copy (the BSCS plan membership, highest collection count, Information Technology tag where present). Do not merge, delete or edit the other seven copies.
- [ ] **Run the GE metadata SQL** (`bsit-year1-ge-metadata-correction.sql`) after STEP 0 guards pass: 3 Notes, 6 rows. Verify per the file.
- [x] Q8 (Counting Note) was decided by the owner: approved. Authoring is a later, separately authorized step (`bsit-year1-counting-note-authoring-brief.md`).

## B. Discrete Mathematics: the 29 conditional Notes (owner-approved rubric)
Record every review in `bsit-year1-curator-verification-manifest-conditional-29.tsv` (all review columns are blank; no review has been performed). **No Information Technology tag may be added to any of the 29 before individual curator approval.**

**A. Canonical equivalence.** The complete Note body transfers unchanged to a BSIT learner; its framing does not depend on BSCS-specific academic objectives; the explanation suits introductory college Discrete Mathematics; the Note needs no rewriting.
**B. Prerequisites and depth.** A learner with foundational mathematics and introductory programming can follow it; advanced CS theory is not assumed; formal verification and loop invariants are not prerequisites; proof rigor is proportionate; algorithm or programming examples are illustrative, not required knowledge.
**C. Assessment treatment.** Inspect the existing Study Pack (each source Note has exactly one): questions assess the Note's intended knowledge, suit a first-year BSIT learner, need no CS-specific assumptions, and have academically correct explanations. Reuse must not require altering the canonical Note or its assessment treatment.
**D. Disposition.** PASS (suitable unchanged; approve for the curator's proposed Information Technology update) · HOLD (uncertain or a specific problem; no tag yet; document the issue) · REPLACE / REINSTATE (the course needs knowledge the candidate does not provide; recommend a targeted new Note or reconsidering an excluded Note, with the coverage and prerequisite rationale).
**E. Record per Note.** Exact production id, canonical title, Subject, Domain Context, Authored Depth, Applicable Programs, full-body result, Study Pack result or explicit absence, disposition, reviewer and date, brief justification, proposed metadata change.

- [ ] All 29 reviewed and recorded. Do not promote any to content-verified because of the earlier screening.
- [ ] Confirm the two Strategist-read Notes (Boolean Algebra and Digital Logic; Mathematical Induction) as part of the same pass.
- [ ] Check that no retained proof Note depends on loop invariants as a prerequisite (Proof by Contradiction, Proof by Cases and Counterexamples, Divisibility and Greatest Common Divisors mention invariants in passing).
- [ ] Only after PASS decisions: approve a separate SQL adding Information Technology to exactly those Notes.
- [ ] Obtain CHED MS101 outcomes or one BSIT Discrete Mathematics syllabus, or record the evidence limitation.

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
