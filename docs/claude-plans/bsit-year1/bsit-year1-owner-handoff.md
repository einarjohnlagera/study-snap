Status: Phase B final shaping, updated 2026-11-05 after the independent review of the 29 Discrete Mathematics Notes and the owner's inclusion decision. NOT implementation-ready. Phase C has not begun.

# BSIT Year 1: Phase B final owner handoff

All remaining curriculum decisions (Q8, S-1 to S-3, S-7) are owner-approved and incorporated. Totals below are recomputed from the final TSV and manifest.

## Final validated counts

| Item | Result |
|---|---|
| Subject Plans and terms | **10 plans: First Semester 5, Second Semester 5** |
| TSV rows | **219** |
| Total Note placements (excluding Excluded) | **211** |
| Reuse | **211** |
| New | **0** (the Counting Note was authored by the owner on 2026-11-05 from the draft body, id `7564b66c-9dd2-424f-b569-274ec6dd6d20`, and is now Reuse) |
| Excluded | **8** (5 inherited from BSCS, 3 BSIT-specific, final) |
| Counts before the Counting Note was created | 219 rows / 210 Reuse / 1 New / 8 Excluded (matched your expected figures); now 219 / 211 / 0 / 8 |

**Verification tiers over the 211 Reuse placements:**

| Tier | Count |
|---|---|
| Content-verified (full body read by this session; includes the new Counting Note) | 6 |
| Metadata-ready (bodies not re-inspected for BSIT) | 176 |
| Reviewed, owner-approved for tagging (read in full by this session and by an independent GPT blind review; the owner included all 29, including the 3 GPT recommended holding) | 29 |

211 Reuse rows are **not** 211 fully content-verified Notes. Only 6 are.

## Update 2026-11-05: the marker was removed
- By owner decision the shaping TSV and workbook were renamed `bsit-year1-shaping.tsv` and `bsit-year1-study-plan-target-shape.xlsx` (no `preliminary`, no `NOT-IMPLEMENTATION-READY`), the BSCS pattern (BSCS D-38). Rationale: every row has a determined placement, status, Subject, Domain Context and Applicable Programs; what remains is verification and the collection build, not specification. Older text in this folder that names the old file names or says "not implementation-ready" predates this.

## Update 2026-11-05: the 29 conditional Notes
- Read in full by this session and independently by a GPT blind review (recommendation: 26 PASS, 3 HOLD: Notes 14, 18, 22). I corrected one of my own pre-read concerns (Note 8's soundness; the Quick Recall defines it).
- **Owner (curator) decision: include and tag all 29**, rule "include them if they give value to the study plan". The 29 rows in the TSV and workbook now show `+Information Technology`; they are no longer "conditional".
- New file `bsit-year1-dm-it-applicability.sql` (RUN by the owner 2026-11-05 08:12:46Z, verified; see the production-state section below): exact ids and titles, idempotent, rollback included, STEP 0 guards re-read against production (29 of 29 match, none tagged). Note 9 is a separate statement you can strike.
- Known limitations (not fixed, a separate decision because BSCS uses these canonical Notes): Note 22's Q5 uses Theta notation the Note never defines; Notes 14 and 18 carry intro-plus depth; Notes 8, 9 and 24 carry moderate extra depth.
- Verification tiers are now: 6 content-verified (incl. the Counting Note), 29 reviewed and owner-approved, 176 metadata-ready (211 Reuse).

## Production state after the owner's SQL runs (verified read-only, 2026-11-05)
- **GE:** the 3 Notes now have 8 programs each (Computer Science and Information Technology added; the six Education programs kept).
- **Discrete Mathematics:** 31 of the 34 Discrete Structures I Notes carry Information Technology (the 29 in `bsit-year1-dm-it-applicability.sql` plus Boolean Algebra and Digital Logic and Mathematical Induction from `bsit-year1-dm-it-applicability-2-notes.sql`). The 3 held-out Notes (Strong Induction; Proving Program Correctness; Logic in Programming and Software Specifications) are untagged and untouched.
- **Across the 213 Notes in the BSCS Year:** 210 carry Information Technology; the 3 without it are exactly the held-out ones. Production now matches the BSIT shaping TSV on Applicable Programs.
- All three SQL files are now headed RUN and verified, with their commit timestamps (GE 08:10:56Z, 29 Notes 08:12:46Z, 2 Notes 08:16:31Z, all 2026-11-05, read from `note_course_program.created_at`). They are idempotent and kept as a record: do not re-run. Any remaining "prepared, not run" wording in this folder is stale.

## Update 2026-11-05: the Counting Note exists
- Created by the owner from `authored-bodies/counting-sum-and-product-rules-permutations-and-combinations.md` at 10:08:38Z. Id `7564b66c-9dd2-424f-b569-274ec6dd6d20`. Verified read-only: Subject Discrete Mathematics, Domain Context COMPUTING, Authored Depth COLLEGE, Applicable Programs Computer Science and Information Technology (exactly the TSV), body length 3,552 (identical to the draft), 1 Study Pack, in 0 collections, 1 Note with that title.
- **Study Pack review (5 items, against the body):** all five items are correct and supported by the body (sum rule, product rule with 2^5=32, permutations when order matters, the NOT-correct item on combinations, and repetition). No quiz item tests untaught material. Coverage note: none asks for a numeric C(n,r) or P(n,r) computation; that is a limitation, not a defect.
- **Open:** visibility is PRIVATE; the 34 existing Discrete Mathematics Notes are PUBLIC. Set it to PUBLIC before it joins a public plan.
- The shaping TSV row changed from New to Reuse with this id (D-40 precedent); workbook regenerated; TSV-to-workbook parity passed.

## Decisions closed
- **Q8:** one New Note, "Counting: Sum and Product Rules, Permutations and Combinations", in a `Counting Principles` section after Functions and Sequences. Shared computing knowledge; not added to the BSCS Year. Not a CHED claim (MS101 outcomes unreadable).
- **Q8 other:** no Notes for trees, recurrence relations or discrete probability (coverage considerations only).
- **S-1, S-2, S-3:** Strong Induction; Proving Program Correctness: Assertions and Loop Invariants; Logic in Programming and Software Specifications stay Excluded for BSIT Year 1. BSCS Notes unchanged.
- **S-7:** the Year description and the Discrete Mathematics plan description are final and identical across the TSV, workbook, journey and decision log.
- **Root title:** used with a space, `🖥️ BS Information Technology — 1st Year`, matching the existing TSV and builder convention and decision Q6. Your message wrote it without the space; no second variant exists. Tell me if you want the unspaced form.

## Validation results (all run against the final files)
- PASS headers and column order identical to the BSCS shaping TSV.
- PASS 219 rows = 211 Reuse / 0 New / 8 Excluded (re-run 2026-11-05 after the Counting Note was created).
- PASS no new Subject or Domain Context values; Applicable Programs use catalog values, none blank, serialized alphabetically.
- PASS one name, term and description per plan; terms 5 plus 5.
- PASS sections contiguous and nonempty; Counting Principles sits after Functions and Sequences and before Proof Techniques; held-out section last.
- PASS no duplicate placements; no BSIT, year or semester branding in titles.
- PASS manifest: 219 rows, 210 unique Note ids, titles equal production titles by id (exact), and manifest and TSV agree row for row.
- PASS (re-run 2026-11-05) 34 Reuse rows differ from production by additions only: the 3 GE Notes (+Computer Science, +Information Technology) and 31 Discrete Mathematics Notes (+Information Technology). Nothing is removed.
- PASS BSCS files unchanged (git diff against HEAD clean).
- PASS TSV-to-workbook parity: all 219 rows match on plan, title, status, Subject, Domain Context and Applicable Programs. Workbook regenerated by the builder, Domain Context sheet raises no flags.
- **Exceptions:** none open. Not verifiable here: Study Pack quality; the bodies of 205 of 210 Notes; CHED MS101 outcomes.

## Files changed in this pass
Updated: the TSV and generated workbook (`docs/curriculum/bsit-year1-shaping.tsv` and `bsit-year1-study-plan-target-shape.xlsx`), the proposed journey, decision log, reuse impact report, Discrete Mathematics assessment, curator checklist, reuse-ID manifest, and this handoff. New: `bsit-year1-counting-note-authoring-brief.md` and `bsit-year1-curator-verification-manifest-conditional-29.tsv` (all review columns blank; no review has been performed). Uncommitted in the `docs/bsit-year1` worktree on top of `018b4c7d`.

## Remaining curator actions
1. (Done 2026-11-05) The 29 Notes were reviewed twice; you approved including all of them. Remaining: none beyond the tag SQL run. Known limitations are recorded: Note 22's Q5 uses Theta notation the Note never defines; Notes 14 and 18 carry intro-plus depth; Notes 8, 9 and 24 carry moderate extra depth. Editing a canonical Note is a separate decision because BSCS uses them.
2. Exact-ID confirmation of reused placements (manifest ids; eight title-matching copies exist per GE title).
3. Study Pack check: each of the 213 source Notes has exactly one Study Pack; currency and suitability are unread.
4. Spot-check at least 20 of the 176 metadata-ready Notes, weighted to C++ Notes and computing-flavoured Mathematics in the Modern World Notes.

## Remaining technical dependencies
1. (Done 2026-11-05) GE metadata SQL run by the owner and verified read-only; kept as a record, do not re-run.
2. (Done 2026-11-05) `bsit-year1-dm-it-applicability.sql` (29 Notes) and `bsit-year1-dm-it-applicability-2-notes.sql` (2 Notes) run by the owner and verified read-only; kept as records, do not re-run.
3. Academic Term: stored on `note_collections.term_label` and `term_order`, **NULL on all ten BSCS plans**. BSIT terms must be set at build time.
4. Official CHED MS101 outcomes, or a documented evidence limitation. Also check CHED figures against the official PDF (OCR source).
5. Authoring the Counting Note (a later, separately authorized step) and its Study Pack.
6. The BSCS legacy-plan cleanup observation (25-item plan beside the ten Subject Plans) is separate and is not a BSIT blocker.
7. Backlog Index row: the Release Manager writes it.

## Recommended next controlled step
The metadata SQLs are done (see above). Next, each with its own go-ahead: authoring the Counting Note and building the collection, plus the 20-Note spot check.
