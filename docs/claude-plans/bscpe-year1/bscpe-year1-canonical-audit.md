Status: SUPERSEDED BY THE SIGNED-OFF SHAPE (kept as the evidence record) -- originally DRAFT canonical and feasibility audit for reconciliation, 2026-11-12. **Read-only: no Note, metadata, Subject Plan, collection or Study Pack was created or changed; no SQL write was run; committed on a docs branch for the Release Manager session (not pushed by this role).** Written by the Claude Note Strategist for the GPT Note Strategist. Source of the per-Note rows: `bscpe-year1-canonical-audit.tsv`. Reproducing reads: `bscpe-year1-audit-read.sql`.

# BSCpE Year 1: canonical inventory and feasibility audit

## 0. What was and was not verified (read this first)

| Item | State |
|---|---|
| Production library (`notelib-db-prod`) | **Read live 2026-11-12, `SELECT` only.** Subject, Domain Context, Authored Depth, visibility, Study Pack count and the Computer Engineering tag verified for all 198 shared-journey Notes by id; titles, ids, depth and body length for the calculus, mechanics, circuits, data-structures and professional-practice candidates. |
| Note bodies | **Read in full for 4 Notes** (two calculus, one integration, Ohm's law). **Pattern-checked server-side for the 42 Computer Programming 1 Notes** (corrected pattern, 2026-11-12; no content transferred). Every other status is **metadata and title level**; the TSV column `read_level` says which. |
| Applicable Programs | Only the **Computer Engineering** tag was checked per Note (the full program set was read for the 23 calculus Notes only). |
| CHED CMO 87 and CMO 86 (s. 2017) | **UNVERIFIED.** Both PDFs on `ched.gov.ph` returned HTTP 403 to an automated fetch. No statement below is taken from the CMOs. |
| University of the East BSCpE curriculum | **Read** (`apps.ue.edu.ph`, HTTP 200). Total units only; lecture/laboratory shown as separate courses; **no prerequisites shown**. |
| Philippine Christian University curriculum | **UNVERIFIED.** The page returned only a "verifying your request" interstitial. |

`REUSE` below means: **metadata verified live and the Note is already reused unchanged by BSCS, BSIT and BSIS; body fit for Computer Engineering not re-read.** It is not a claim that every body was inspected.

## 1. Proposed Subject Plan inventory (12 candidates, plus gaps)

UE Year 1 cross-check in the last two columns (UE only; the CMOs and PCU are unverified).

| # | Candidate | In UE Year 1? | Canonical coverage found | Verdict |
|---|---|---|---|---|
| 1 | Computer Engineering as a Discipline | Yes, S1, 1 unit | None specific. 16 CONDITIONAL candidates from Introduction to Computing, Discrete Mathematics and engineering professional practice | **Mostly NEW** (5 candidate areas) |
| 2 | Programming Logic and Design | Yes, S1, 2 units | Computer Programming 1 plan: 42 Notes, `Programming Fundamentals`, COMPUTING, COLLEGE. **20 are language-neutral by title and body; 22 name C++ in the title and mention it in the body** (CORRECTED 2026-11-12: an earlier pattern check was faulty and reported 13 and 29) | **REUSE the 20, CONDITIONAL the 22 C++-specific ones**; plan is large for a 2-unit course |
| 3 | Calculus 1 | Yes, S1, 3 units | 12 Differential Calculus Notes (+1 formula sheet), all tagged Computer Engineering | **CONDITIONAL** (depth metadata, duplicates, outline depth); 3 gap areas |
| 4 | Calculus 2 | Yes, S2, 3 units | 11 Integral Calculus Notes, all tagged Computer Engineering | **CONDITIONAL**; 3 gap areas |
| 5 | Chemistry for Engineers | Yes, S1, 3 + 1 lab | **None** | **NEW** (6 candidate areas) |
| 6 | Physics for Engineers | Yes, S2, 3 + 1 lab | Only statics/dynamics from the Civil Engineering review set (20 candidates) and one junior-high Note | **Mostly NEW** (7 areas); 20 CONDITIONAL |
| 7 | Mathematics in the Modern World | Yes, S2 | 21 shared Notes (+4 excluded) | **REUSE** |
| 8 | Understanding the Self | Yes, S1 | 8 shared Notes (+1 excluded) | **REUSE** |
| 9 | The Contemporary World | **No** | 10 shared Notes | **REUSE if kept**; varies by institution |
| 10 | Computer-Aided Drafting | **No** | **None** | **NEW** (5 areas); lab/software-heavy; placement unresolved |
| 11 | Discrete Mathematics | Yes, S2, 3 units | 35 Notes (32 + 3 CS-only), 33 already tagged Computer Engineering | **REUSE** |
| 12 | Fundamentals of Electrical Circuits | Yes, S2, 3 + 1 lab (code NEE 2103) | One 605-character formula sheet | **NEW** (7 areas) |

**Foundations in UE Year 1 that are not on the candidate list** (for the Strategist to decide, none is a CMO-verified requirement):
- **Data Structures and Algorithms** (S2, 2 units): the 6 `Data Structures and Algorithms` Notes (COMPUTING, COLLEGE, all tagged Computer Engineering) sit inside the **Computer Programming 2 plan (31 Notes, 30 already tagged Computer Engineering)**, which also covers arrays, strings, pointers and memory, recursion and files. Searching, sorting, linked lists and informal efficiency are present; stacks, queues, trees and hashing are not.
- **Database Management System** (S2, 1 unit): library has 17 `Database Systems` Notes, **Domain Context and Authored Depth both unset**, one program each.
- Not Notes-scope: PATHFIT 1 and 2 (PE) and "Literacy/Civic Welfare/Military Science 1 and 2" (3 units each, the NSTP-equivalent) are present and stay provisionally excluded as the brief says.
- Precalculus readiness: Algebra, Trigonometry and Analytic Geometry Notes exist in the library tagged to engineering programs (read in the Computer Engineering survey); whether a preparatory plan exists in production was not checked. UE does not show it in Year 1.

## 2. Canonical reuse matches with ids

Ids are in the TSV (one row per Note). Summary of what the library holds, read 2026-11-12:

| Source | Notes | Computer Engineering tag | Metadata |
|---|---|---|---|
| Discrete Mathematics (BSIT plan 7) | 32 + 3 | 33 of 35 tagged; untagged: `7564b66c` Counting, `edeb5f50` Propositions | COMPUTING, COLLEGE |
| Mathematics in the Modern World | 21 | 14 tagged, 7 not (`3222217b`, `2ed3f9cd`, `93eda784`, `16a6536a`, `404ee436`, `7ffd35e3`, `fd5727d4`) | GENERAL_EDUCATION, COLLEGE |
| Understanding the Self | 8 | 2 tagged, 6 not | GENERAL_EDUCATION, COLLEGE |
| The Contemporary World | 10 | 8 tagged; untagged `45133a32` Global Citizenship, `8ca37142` Global Media | GENERAL_EDUCATION, COLLEGE |
| Computer Programming 1 | 42 | **0 of 42 tagged** | COMPUTING, COLLEGE |
| Computer Programming 2 (holds the 6 DSA Notes) | 31 | 30 tagged | COMPUTING, COLLEGE |
| Introduction to Computing | 39 | **0 of 39 tagged** | COMPUTING, COLLEGE (one GENERAL_EDUCATION) |
| Differential + Integral Calculus | 12 + 11 (+1 formula sheet) | all 23 tagged | ENGINEERING_MATHEMATICS; **Authored Depth unset on 15, BOARD_EXAM_REVIEW on 8**. **Held by the public CELE Comprehensive Review, Engineering Mathematics plan: 14 of the 15 unset and 5 of the 8 board-depth Notes (group-level read)** |
| Engineering Mechanics | 52 | none tagged | ENGINEERING_SCIENCES; depth unset on 35, COLLEGE on 16, board review on 1. **51 of 52 held by the public CELE Comprehensive Review, Engineering Mechanics plan** |
| Circuit Theory | 1 | none | DC unset, BOARD_EXAM_REVIEW, 1 program |

All 198 shared Notes read are PUBLIC with exactly one Study Pack.

## 3. Missing Note topics (NEW) and candidate learning objectives

**These are gaps in the library, in candidate wording for the GPT Note Strategist to confirm or replace; they are not read from the CMO.** 36 candidate areas in the TSV (`NO MATCH` rows): Calculus 1 (3), Calculus 2 (3), Chemistry for Engineers (6), Physics for Engineers (7), Electrical Circuits (7), Computer-Aided Drafting (5), Computer Engineering as a Discipline (5). The main ones:
- **Chemistry for Engineers: no canonical Note exists** (only incidental title matches in nursing and databases).
- **Electrical circuits: no circuit-analysis content** (Kirchhoff, node and mesh, network theorems, RC/RL, phasors).
- **Physics: no kinematics, rotational motion, waves, electricity and magnetism** at college depth.
- **Drafting: nothing.**
- Calculus: transcendental derivatives, linear approximation, Mean Value Theorem and L'Hopital; improper integrals, and series if in scope.

## 4. Conditional matches and the precise reason

| Group | Reason |
|---|---|
| Calculus (23) | (a) **Authored Depth unset (15) or BOARD_EXAM_REVIEW (8)**, not COLLEGE; (b) **topic duplicates across the two depths** (read: `Fundamentals of Derivatives` substantially repeats `Derivatives and Differentiation Rules`; title-level pairs also exist for limits, derivative applications and integration applications; four board-review Notes have no counterpart: partial derivatives, definite, indefinite and multiple integrals); (c) **outline depth: 1,800 to 2,800 characters, correct but with rules only and no worked examples** (read: Derivatives and Techniques of Integration) |
| Computer Programming 1 C++ Notes (22) | Title and body are C++-specific (compiler, debugger, `cout`/`cin`-level content); fit depends on whether "Programming Logic and Design" is language-neutral. **Correction: 7 Notes earlier flagged as "body mentions C++" (for example `What Programming Is and How Programs Run`, `Tracing and Dry Runs`, `Functions and Procedural Abstraction`) are language-neutral; the flag came from a faulty pattern.** |
| Physics candidates (20 of the 52 mechanics Notes) | Civil Engineering statics orientation; depth unset; fit as first-year physics unread; about 30 other mechanics Notes are structural and marked EXCLUDE |
| Computer Engineering as a Discipline (16) | Hardware and digital-systems foundations from Introduction to Computing (COMPUTING, not tagged Computer Engineering) and four licensure-review practice Notes; fit depends on how the orientation course is scoped |
| Discrete Mathematics (3) | `Logic in Programming and Software Specifications`, `Strong Induction`, `Proving Program Correctness` were excluded from BSIT Year 1 on scope, are tagged Computer Engineering and present in BSCS |
| MMW `2ed3f9cd` | Logic and truth tables overlap Discrete Mathematics |
| Ohm's Law Note `acd1073a` | Read: correct but a 605-character formula sheet outside the standard structure; likely superseded by a NEW Note |

## 5. Recommended semester arrangement (PROVISIONAL, from the UE cross-check only)

- **First semester:** Computer Engineering as a Discipline, Programming Logic and Design, Calculus 1, Chemistry for Engineers, Understanding the Self.
- **Second semester:** Calculus 2, Physics for Engineers, Discrete Mathematics, Fundamentals of Electrical Circuits, Mathematics in the Modern World.
- **Unplaced:** Computer-Aided Drafting and The Contemporary World (neither is in UE Year 1); Data Structures and Algorithms and Database Management (UE S2) if the Strategist keeps them.
- **Prerequisites:** none are shown by UE, and the CMOs were not read, so none is verified. By dependency logic Calculus 2 follows Calculus 1, and Physics and Circuits follow or accompany Calculus 1; that is an inference, not a source. UE codes Circuits NEE 2103 yet places it in Year 1, which suggests institution-specific placement.
- Second semester is dense in UE (five technical courses); the BSCS and BSIT journeys placed Mathematics in the Modern World in the first semester.

## 6. Material taxonomy and metadata concerns

1. **Calculus Authored Depth, and why it is a placement decision, not a relabel.** 15 Notes are unset and 8 are BOARD_EXAM_REVIEW. Authored Depth is one value per canonical Note across every collection that holds it, and these Notes are held by the **public CELE Comprehensive Review** (Engineering Mathematics plan), where BOARD_EXAM_REVIEW is correct. So: **the 8 board-depth Notes stay as they are and are included or skipped in a Year 1 plan, never relabelled to COLLEGE.** The 15 unset-depth Notes could be set to COLLEGE (owner-run SQL, no Study Pack regeneration, playbook section 11), but that also changes how they appear in the CELE set and in the Public Library depth filters, so it needs the owner to weigh that set. The same applies to the unset-depth mechanics Notes.
2. **Domain Context for the NEW subjects.** The vocabulary is closed (ADR-001). Existing values that could fit are `ENGINEERING_SCIENCES` (physics, circuits, chemistry) and `ENGINEERING_MATHEMATICS`; no electrical or computer-engineering value exists, and a new value is an owner architectural decision. **`COMPUTING` is not automatic for engineering Notes** (ADR-001's two tests apply: is computation itself the object of study?). Programming, discrete logic and data structures already pass; circuits, physics and chemistry do not.
3. **Tagging Computer Engineering is discovery-only and is a per-Note decision.** 107 of the 198 shared Notes are not tagged: all 42 Computer Programming 1 and all 39 Introduction to Computing Notes, 7 of 21 MMW, 6 of 8 Understanding the Self, 8 of 12 Purposive Communication, and the Networking, Security and Web Notes. Useful across programs does not mean tag now.
4. **Adding a second program needs a Domain Context first.** `Database Systems` (17), `Computer Networking` (17), `Circuit Theory`, `Electrical Engineering` and `Electrical Machines`/`Safety` Notes have an unset Domain Context with exactly one program; tagging Computer Engineering would make two, so a Domain Context must be set before generation (ADR-001).
5. **Title discipline suffixes.** Many calculus and mechanics titles end "in Engineering Mathematics" or "in Engineering Mechanics" (the `v0.120.0` title doctrine: a discipline name belongs only when it is part of the knowledge). Not blocking; a curator choice.
6. **A possible factual error in a candidate:** the Note titled "Republic Act 8981: Philippine Professionalization Act for Engineers" is tagged Computer Engineering; **RA 8981 is recalled as the PRC Modernization Act of 2000.** Recalled from memory, not verified; the Strategist should check before any reuse.
7. **Generation capability, not metadata:** the existing calculus, mechanics and most Notes here are the generator's outline-depth Notes (about 2,000 characters). New engineering Notes (circuits, physics, chemistry) will be of that shape unless a scope field exists; playbook section 6 recommends a measured pilot first.

## 7. Decisions requiring academic reconciliation

1. **Primary reference.** The CMOs were not readable. Can the owner supply the PDFs, or will the Strategist confirm the Year 1 list and course names from them?
2. **Scope of Physics for Engineers:** mechanics only, or with electricity and magnetism, waves and thermal physics? Lecture and laboratory are separate courses in UE; are laboratory courses excluded from NoteLib?
3. **Calculus:** reuse the 15 unset-depth Notes (after the owner decides whether to set them to COLLEGE, given they live in the CELE review set), skip the board-review Notes or include them as they are, and which gap areas (series, improper integrals) are Year 1?
4. **Computer-Aided Drafting:** in Year 1 at all, and can lecture Notes serve a drawing and software course?
5. **Data Structures and Algorithms and Database Management:** in Year 1 (UE S2) or later? Computer Programming 2 already exists and is mostly tagged Computer Engineering; Database Systems has 17 Notes with unset Domain Context and depth.
6. **General education:** which of Mathematics in the Modern World, Understanding the Self and The Contemporary World stay; UE Year 1 shows only the first two.
7. **Programming Logic and Design:** the 20 language-neutral Computer Programming 1 Notes (18 read in full on 2026-11-12, 2 pattern-checked), or the whole C++ plan (42)?
8. **Domain Context** for Chemistry, Physics, Circuits and Drafting: `ENGINEERING_SCIENCES`, or a new value (owner decision)?
9. **Tagging policy** for the 107 untagged shared Notes, and which of the 36 candidate gap areas are in Year 1.

## 8. Production read footprint (for the release session's kill attribution)

16 read-only `SELECT`s ran against `notelib-db-prod` on 2026-11-12 (one more was rejected for a syntax error and did not run), one at a time and none repeated, through the Render MCP. Every one was aggregate or narrow; body text was transferred for 4 Notes only. No write, no Render environment or deploy action. Queries are in `bscpe-year1-audit-read.sql`.

## 9. Files and relay

Working files are untracked in `study-snap-bsis` under `docs/claude-plans/bscpe-year1/`: this report, the per-Note TSV and the SQL pack. When committed they owe a Backlog Index row (the Release Implementor writes it). The GPT Note Strategist cannot read the worktree; relay sections 1, 3, 4 and 7 of this file, plus the TSV if the Strategist needs ids.
