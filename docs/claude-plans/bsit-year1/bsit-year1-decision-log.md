Status: Phase B final shaping, 2026-11-03. NOT implementation-ready. Owner approvals below were given on 2026-11-03: Q1 to Q7 in the Phase B authorization, and Q8, S-1 to S-3 and S-7 in the final decisions message.

# BSIT Year 1: decision log

## A. Inherited from established architecture (not reopened)

| Ref | Decision | Source |
|---|---|---|
| ADR-001 | Note metadata axes: Subject, Domain Context, Authored Depth, Applicable Programs (discovery only, never reaches a prompt) | `docs/architecture/ADR-001-canonical-knowledge-architecture.md` |
| ADR-003 | Two collection levels (Year root → Subject Plan); Sections are membership labels; Academic Term is Subject Plan metadata | `docs/architecture/ADR-003-curriculum-placement-and-hierarchy-depth.md` |
| BSCS D-09 | PE and NSTP are an explicit scope exclusion | `bscs-year1-decision-log.md` |
| BSCS D-13 | `COMPUTING` Domain Context ratified; per-Note two-test rule | same |
| BSCS D-21 | GE Applicable Programs decided per Note | same |
| BSCS D-35 | N/S convention: neutral titles for neutral Notes; C++ in the title for C++ Notes. No reference-language field, no C++ Domain Context | same |
| BSCS D-40 | The five R4 pilot Notes are Reuse | same |
| Shaping spec | TSV status vocabulary is Reuse, New, Excluded. Every row carries `applicable_programs`. TSV is the source of truth; the workbook is generated | `docs/curriculum/review-set-workbook-spec.md` |

## B. Owner decisions for BSIT (approved 2026-11-03)

| # | Decision | Applied how |
|---|---|---|
| Q1 | Reference language is the same C++17 restricted teaching subset as BSCS | All 73 programming Notes (31 N, 42 S) are Reuse placements; no duplication; no language field |
| Q2 | Discrete Mathematics: Option B, conditionally | 31 of 34 DS I Notes are Reuse; 3 held out; IT added only where content-verified and individually approved by a curator (Section D) |
| Q3 | Prepare (not run) a metadata correction for 3 GE Notes | `bsit-year1-ge-metadata-correction.sql`; exact ids; idempotent; rollback included |
| Q4 | Web fundamentals stays in Year 2 | No Web plan; the 4 web Notes remain in Introduction to Computing |
| Q5 | Separate BSIT root | Fresh root; BSCS root untouched; reuse is by Note |
| Q6 | Root title `🖥️ BS Information Technology — 1st Year` | Used verbatim as the builder title |
| Q7 | Keep 5 plus 5; The Contemporary World stays First Semester | Documented against the 3-of-5 contrary evidence |

## C. Final dispositions (owner-approved 2026-11-03)

| # | Decision | Status |
|---|---|---|
| Q8 | Add exactly one New Note, "Counting: Sum and Product Rules, Permutations and Combinations" (Discrete Mathematics, COMPUTING, COLLEGE, Applicable Programs Computer Science and Information Technology, Second Semester), in a new section `Counting Principles` after Functions and Sequences. Shared computing knowledge; **not** added to the BSCS Year. An approved NoteLib curriculum choice, not a CHED requirement (MS101 outcomes unreadable; no syllabus inspected). Authoring brief only; the Note is not created | APPROVED |
| Q8 (other) | No separate Notes for trees and basic graph terminology, recurrence relations or discrete probability. Recorded as coverage considerations, revisited only on stronger evidence | APPROVED |
| S-1 | Strong Induction stays Excluded for BSIT Year 1; BSCS Note unchanged | APPROVED |
| S-2 | Proving Program Correctness: Assertions and Loop Invariants stays Excluded; BSCS Note unchanged | APPROVED |
| S-3 | Logic in Programming and Software Specifications stays Excluded; no lighter replacement without a demonstrated need; BSCS Note unchanged | APPROVED |
| S-4 | Boolean Algebra and Digital Logic and Mathematical Induction stay in the plan | Retained (Strategist proposal, not contested) |
| S-6/S-7 | Discrete Mathematics plan description (final): "Build the mathematical foundations of computing through propositional and predicate logic, rules of inference, Boolean algebra, sets, relations, functions, sequences, integers and divisibility, elementary counting principles, and introductory proof techniques including mathematical induction. The emphasis is on understanding discrete mathematical concepts and reasoning that support further study in information technology." | APPROVED, applied identically in TSV, workbook and journey |
| S-7 | Year description (final): see the journey document | APPROVED |
| Root title | Used with a space after the emoji, `🖥️ BS Information Technology — 1st Year`, matching the existing TSV and builder convention and decision Q6. The approved message wrote it without the space; no second variant was created | Recorded convention |

The exclusions apply only to the BSIT Year 1 journey. They do not invalidate the Notes or change their canonical metadata, and they are not reinstated to improve the Reuse percentage.

## D. Metadata changes proposed (none executed)

| Change | Notes | Gate |
|---|---|---|
| + Computer Science, + Information Technology | 3 GE Notes | Owner runs the SQL after STEP 0 guards |
| + Information Technology | 2 DS Notes fully read (Boolean Algebra and Digital Logic, Mathematical Induction) | Curator confirmation, then a separate SQL |
| + Information Technology | 29 DS Notes screened but not fully read | **No tag before individual curator approval (PASS)**. Record per `bsit-year1-curator-verification-manifest-conditional-29.tsv` |
| none | 3 held-out DS Notes | n/a |
| none | the new Counting Note (it is created with Computer Science and Information Technology at authoring, so it is not an update) | n/a |

No existing association is removed. Domain Context and Authored Depth are not changed anywhere.

## E. Curator verification tasks
See `bsit-year1-curator-verification-checklist.md` (includes the owner-approved rubric) and `bsit-year1-curator-verification-manifest-conditional-29.tsv` (all reviews blank; none has been performed).

## F. Technical dependencies
1. **Language:** Q1 depends on the BSCS C++17 convention remaining the operative one (D-35). The 42 S Notes carry executable C++.
2. **Canonical identity:** reuse is by exact Note id from the BSCS Year memberships (manifest). Eight title-matching copies exist per reused GE title; none may be merged, deleted or modified in this work.
3. **Academic Term:** stored as `note_collections.term_label` and `term_order`. Both are NULL on every BSCS Subject Plan in production (read 2026-11-03), so BSIT terms must be set when its collections are built.
4. **Study Packs:** each of the 213 source Notes has exactly one Study Pack (count only). Currency and quality were not read. Reuse does not regenerate packs.
5. **Release Implementor:** the working set under `docs/claude-plans/bsit-year1/` needs a Backlog Index row. This session does not write one (sole-writer rule).

## G. Recorded observations (verification or cleanup, not decisions)
- The public BSCS root holds a legacy 25-item plan beside the ten Subject Plans, and is titled "🌱 BSCS Year 1 — Computing & College Foundations".
- Three reused GE Notes in the BSCS Year lack Computer Science (a BSCS-side gap).
- CHED text is OCR; the official PDF has not been checked.
- The recorded production state is a snapshot from 2026-11-03; re-read before use.
