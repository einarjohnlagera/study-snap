Status: Phase B final shaping, updated 2026-11-05 after the independent review and the owner's inclusion decision. Counts are recomputed from the final TSV and the manifest from `docs/curriculum/bsit-year1-preliminary-shaping.NOT-IMPLEMENTATION-READY.tsv` and the exact-ID manifest. NOT implementation-ready.

# BSIT Year 1: canonical reuse impact report

## 1. Headline
210 of the 211 BSIT placements are **existing production Notes**. The one New placement is the approved Counting Note (Q8), which has an authoring brief and is not created. **That is a statement about status, not about verification:** only 5 of the 210 have been read in full by this session. The honest quality tiers are below.

## 2. Counts (no double counting)

| Measure | Count |
|---|---|
| Subject Plans | 10 (First Semester 5, Second Semester 5) |
| TSV rows | 219 |
| **Total Note placements** (excluding Excluded) | **211** |
| TSV status: Reuse | 210 |
| TSV status: New | 1 (Counting, approved Q8) |
| TSV status: Excluded | 8 (5 inherited from BSCS, 3 BSIT-specific) |

Verification tiers over the 210 Reuse placements (every placement is in exactly one tier; the tier names changed on 2026-11-05):

| Tier | Count | Meaning |
|---|---|---|
| **Content-verified Reuse** | **5** | Full body read by this session, found program-neutral: 3 GE Notes (Emerging Technologies and Their Social Impacts; Spanish Colonization and Transformation of Philippine Society; Philippine Revolution and the First Philippine Republic) and 2 DS Notes (Boolean Algebra and Digital Logic; Mathematical Induction) |
| **Metadata-ready Reuse** | **176** | Information Technology already in Applicable Programs; Domain Context explicit; `COLLEGE` depth; one Study Pack; authored or reviewed as shared in BSCS under the per-row two-test rule. **Bodies not re-inspected for BSIT.** Includes all 42 C++-specific Notes, now resolved by Q1 |
| **Reviewed, owner-approved for tagging** | **29** | Discrete Structures I Notes read in full by this session (bodies and Study Packs) and by an independent GPT blind review (26 PASS, 3 HOLD recommended: 14, 18, 22). The owner, as curator, approved including all of them on 2026-11-05 (rule: include if it adds value). Tag SQL RUN by the owner 2026-11-05 and verified read-only. Known limitations are recorded in the SQL and the decision log |
| New | 1 | the approved Counting Note; not authored |
| Excluded | 8 | |

Check: 5 + 176 + 29 = 210 Reuse; plus 1 New = 211 placements.

**Reuse percentage:** 99.5% of placements (210 of 211) are Reuse by status. Of the 210 Reuse placements, 2% are content-verified (Strategist full read), 84% metadata-ready and 14% reviewed and owner-approved. I would not quote the headline without these qualifiers, and the curriculum was not shaped to raise it: three BSCS Notes were held out and one New Note was added.

## 3. Phase A buckets reconciled
Phase A: 134 metadata-ready, 42 language-dependent, 37 applicability/treatment candidates, 5 excluded.

| Phase A bucket | After owner approvals |
|---|---|
| 134 metadata-ready | 134 metadata-ready (unchanged) |
| 42 language-dependent | 42 metadata-ready (Q1 resolved the language) → combined with the 134 = 176 |
| 37 candidates (34 DS + 3 GE) | 3 GE → content-verified; 2 DS → content-verified; 29 DS → reviewed and owner-approved; 3 DS → excluded (3 + 2 + 29 + 3 = 37) |
| 5 excluded | 5 excluded, plus 3 new BSIT exclusions = 8 |
| (none) | 1 New: the approved Counting Note |

## 4. Where the Notes come from
- BSCS-authored computing Notes: **137** (140 minus the 3 held out)
- BSCS-authored GE Notes: **23**
- Pilot computing Notes recorded as Reuse in BSCS (D-40): **5**
- Legacy GE Notes reused by BSCS: **45**
- Total 137 + 23 + 5 + 45 = 210

**Duplicate Notes avoided:** up to 210 that BSIT would otherwise author. (The Counting Note is shared knowledge: BSCS learners can find it via the Computer Science tag, but it is not added to the BSCS collection.) Existing Notes reused: 160 BSCS-authored, 5 pilot, 45 legacy GE.

## 5. Exact Applicable Programs changes proposed (none executed)

| Notes | Add | Result | Gate |
|---|---|---|---|
| 3 GE Notes (ids in the SQL) | Computer Science, Information Technology | +6 association rows | Owner runs `bsit-year1-ge-metadata-correction.sql` |
| 2 DS Notes, content-verified | Information Technology | +2 | Included in the same SQL family; the 2 are among the 5 content-verified Notes shown in the TSV |
| 29 DS Notes, reviewed | Information Technology | +29 | Owner runs `bsit-year1-dm-it-applicability.sql` (STEP 0 guards already re-read: 29 of 29 match, none yet tagged) |
| 3 held-out DS Notes | none | 0 | n/a |

**The TSV and workbook show current production programs on the 29 conditional rows; their proposed `+Information Technology` lives only in the manifest's `proposed_change` column.** The TSV shows the proposed state only for the 3 GE Notes (Q3, gated by the SQL) and the 2 content-verified DS Notes.

Notes with Applicable Programs updates: **34** (the New Counting Note is created with its programs and is not an update). Association rows: **37** (6 + 2 + 29). Nothing is removed. Domain Context and Authored Depth unchanged. Adding programs affects discovery only, never prompts or Study Packs (ADR-001).

## 6. Notes requiring content inspection
- (done) the 29 DS Notes: read in full by this session and independently by GPT; no further read is required before tagging, only your SQL run.
- A spot check of at least 20 of the 176 metadata-ready Notes, weighted to the C++ S Notes (Q1 relies on their suitability) and the Mathematics in the Modern World Notes with computing flavour (Codes, Cryptography, Graphs).
- The 5 content-verified Notes still need curator confirmation as part of the normal pass.

## 7. Cross-program impact
- **GE Notes:** 3 are shared with the LET General Education plan. Adding programs there widens discovery and changes nothing for Education learners.
- **DS Notes:** adding Information Technology widens discovery for IT learners; BSCS and Computer Engineering are unchanged.
- **Holding Notes out of BSIT** affects BSIT only.
- BSCS is untouched by everything in this work, including its root and legacy plan.

## 8. Not verified
Study Pack quality or freshness; the bodies of 205 of 210 Notes; whether eight title-matching duplicate copies carry different content; BSIT Discrete Mathematics learning outcomes (CHED MS101).
