# BSCS Year 1 — Authoring Batch Plan (proposal, 2026-10-01)

> **Proposal for owner approval. Nothing here creates, generates or publishes anything.** Generating Notes and Study Packs is a production write and is the owner's. Generated from `bscs-year1-shaping.tsv` and the Precalculus TSV; the working sheet is `bscs-year1-authoring-plan.xlsx`.

**Scope.** 194 New notes (163 default Year + 31 Precalculus). The 50 Reuse and 5 Excluded rows need no authoring (the 40 reused general-education notes need the tag and curator checks only). **The five pilot Notes are recorded as Reuse (2026-10-06):** they already exist in production, so they are added to their plans by id and not generated again.

## 1. What the R4 pilot changes about the plan (FACT)

- **Bulk Generate writes ~2,000-character outlines from the title alone** and cannot carry scope constraints (no instruction field). Its output is accurate but shallow, and in the pilot it failed the D-27 guardrail on the one scope-sensitive Note.
- **Bulk Generate applies one Subject, one Domain Context and one program set per batch** (the request has `subject`, `domainContext`, `courseProgramIds`, plus optional `collectionId` and one `sectionLabel`).
- The pilot produced five-program sets on every Note instead of the TSV set. **Verify Applicable Programs at creation for every batch.**
- Tags are auto-generated, and computation guidance can depend on them. Review `Programming Fundamentals` packs for guidance-dependent quality.

## 2. Four authoring paths (PROPOSAL)

| Path | Meaning | Notes | Applies to |
|---|---|---|---|
| **A** | Bulk Generate, then curator review of the Study Pack (unsupported claims, wrong quiz answers) | 124 | Overview and definitional notes: Introduction to Computing, Discrete Mathematics, general-education additions, Precalculus, networking, web, security |
| **A-N** | As A, plus the **N-note acceptance check**: no language named, any code is pseudocode, every answer valid in all four languages | 30 | The 31 neutral programming notes, less Algorithms and Their Properties (pilot Note, already exists) |
| **B** | Bulk Generate, then **curator edit** to add C++17 specifics (standard, `-std=c++17 -Wall -Wextra`, an executable example) | 29 | Reference-language notes whose scope the title carries well enough |
| **C** | **Curator-authored body**, then generate the Study Pack | 11 | Scope-sensitive notes (13, less Building and Running a C++ Program and Pointers and References in C++, which exist as pilot Notes): the memory section, the build and testing notes, and anything at risk of an STL or build-system survey |

Path C notes (13 in the corpus; 11 still to create, the two pilot Notes marked * already exist): Building Reusable Modules in C++; Building and Running a C++ Program*; Common Memory Errors in C++; Debugging C++ Programs with a Debugger; Dynamic Memory Allocation in C++; Exceptions in C++; Growable Arrays with std::vector in C++; Linked List Operations in C++; Memory Layout: Stack and Heap in C++; Pointers and References in C++*; Testing a C++ Program with assert and Test Drivers; Unit Testing in C++; Using Standard Libraries in C++. **Authored bodies already exist and compile for the five pilot Notes** (`claude-findings/r4-pilot/note-A` to `note-E`; two of them, C and D, are Path C) and those Notes are **already in production**, and **drafts of the other eleven Path C bodies are in `authored-bodies/`**; none of this is in production.

**Why C is limited to 13.** Authoring all 42 reference-language notes would be heavy; the pilot showed the generator handles plain C++ facts correctly and fails on *scope*, so the notes where scope is the point are Path C and the rest are Path B.

**Path C feasibility (checked in the code, 2026-10-06):** the manual Note request carries all the TSV metadata, there is no cap on the Note body, and Study Pack generation rejects (does not truncate) text over `max-notes-chars` (default 12,000; the production value was not read). All 16 authored bodies are at most 9,756 characters. **The model sees the body with all whitespace collapsed to single spaces**, which flattens code; the 2026-10-06 re-run on authored Notes B, C and D showed no visible harm in three samples (`claude-findings/r4-pilot/r4-pilot-report.md` section 13). The R4 pilot recommendation is **safe to scale with curator convention**. Details: `authored-bodies/README.md`.

## 3. Phases (follows D-12; each phase ends with an R4 sample check before the next)

| Phase | Plans | New notes | Gate before the next phase |
|---|---|---|---|
| 1 | Introduction to Computing; Computer Programming 1 | 77 | Re-score a sample of 5 notes (2 N, 2 S, 1 overview) against the pilot dimensions |
| 2 | Discrete Structures I; Computer Programming 2 | 63 | Re-score the memory section (Path C) and 3 Discrete Mathematics notes |
| 3 | Mathematics in the Modern World; The Contemporary World; Understanding the Self; Purposive Communication (Art Appreciation and Readings in Philippine History need none) | 23 | Spot-check the 5 notes that carry the six Education programs |
| 4 | Precalculus (separate root) | 31 | Curator reads the 5 inspection candidates (they are authored New until then) |

**Phase 0 (before any batch):** close the pilot (fix the five pilot Notes' Applicable Programs, decide the content path, re-run the read-back), and settle the open owner items: the Year description wording (D-29), and confirmation of D-08.

## 4. Batches

There are two ways to batch, and they differ in when the collection must exist. **This is an owner choice.**

- **Option 1: batch by Subject (17 batches, no collection needed first).** Generate, then add notes to Subject Plans in the Builder and set Section labels there. Simplest, but the Builder work is manual.
- **Option 2: batch by Section (57 batches, collection and Subject Plans must exist first).** Each batch sets `collectionId` and `sectionLabel`, so notes land in the right plan and section automatically. More batches, far less Builder work, and it needs the private Year root and its 10 Subject Plans created first (see the checklist).

**Recommendation (PROPOSAL): Option 2 for the default Year**, because section placement is the curriculum and typing it per note invites errors; Option 1 is acceptable for the Precalculus root (31 notes, 3 Subjects).

### Option 1: batches by Subject

| Batch | Subject | Domain Context | Applicable Programs | Notes | Paths |
|---|---|---|---|---|---|
| S1 | Computer Science | COMPUTING | Computer Science, Information Technology, Information Systems | 25 | A:25 |
| S2 | Computer Networking | COMPUTING | Computer Science, Information Technology, Information Systems | 3 | A:3 |
| S3 | Web Development | COMPUTING | Computer Science, Information Technology, Information Systems | 4 | A:4 |
| S4 | Information Assurance and Security | COMPUTING | Computer Science, Information Technology, Information Systems | 5 | A:5 |
| S5 | Programming Fundamentals | COMPUTING | Computer Science, Information Technology, Information Systems | 64 | A-N:25, B:29, C:10 |
| S6 | Mathematics | GENERAL_EDUCATION | Computer Science, Information Systems, Information Technology | 13 | A:13 |
| S7 | Mathematics | GENERAL_EDUCATION | Computer Science, Early Childhood Education, Elementary Education, Information Systems, Information Technology, Physical Education, Secondary Education, Special Needs Education, Technical-Vocational Teacher Education | 3 | A:3 |
| S8 | 🌏 The Contemporary World | GENERAL_EDUCATION | Computer Science, Early Childhood Education, Elementary Education, Physical Education, Secondary Education, Special Needs Education, Technical-Vocational Teacher Education | 1 | A:1 |
| S9 | Data Structures and Algorithms | COMPUTING | Computer Science, Information Technology, Information Systems | 6 | A-N:5, C:1 |
| S10 | Discrete Mathematics | COMPUTING | Computer Science | 33 | A:33 |
| S11 | 🪞 Understanding the Self | GENERAL_EDUCATION | Computer Science | 1 | A:1 |
| S12 | 🪞 Understanding the Self | GENERAL_EDUCATION | Computer Science, Early Childhood Education, Elementary Education, Physical Education, Secondary Education, Special Needs Education, Technical-Vocational Teacher Education | 1 | A:1 |
| S13 | 💬 Purposive Communication | GENERAL_EDUCATION | Computer Science | 2 | A:2 |
| S14 | 💬 Purposive Communication | GENERAL_EDUCATION | Computer Science, Information Systems, Information Technology | 2 | A:2 |
| S15 | Algebra | (unset) | Computer Science | 18 | A:18 |
| S16 | Trigonometry | (unset) | Computer Science | 8 | A:8 |
| S17 | Analytic Geometry | (unset) | Computer Science | 5 | A:5 |

Note: a batch is one Subject with one Domain Context and one program set, so a Subject that serves several plans (`Programming Fundamentals` serves two) is still one batch, while the same Subject with a different program set would be a separate batch. That is why there are more batches than Subjects.

**Phase note.** The `Programming Fundamentals` batch (67 notes) spans phases 1 and 2; run it as two sub-batches by phase, or use Option 2, which batches by section and so respects the phases automatically.

### Option 2: batches by Section (see the `Batches by Section` sheet for all of them)

57 batches across 9 plans. Each batch: one Subject Plan, one Section, one Subject, one Domain Context, one program set. The sheet lists the exact values to enter.

## 5. Per-batch checks (every batch)

1. **Before generating:** Subject, Domain Context, Applicable Programs and Authored Depth match the sheet row (the pilot got Applicable Programs wrong five times out of five).
   - **Option 2 only:** Bulk Generate pre-fills the Section field from the Subject. Overwrite it with the section label from the `Batches by Section` sheet before submitting.
2. **Immediately after:** read back the batch (Claude can do this read-only): title exact, one note per title, metadata exact, visibility private.
3. **Study Pack review:** every claim traceable to the Note; no unsupported quiz explanation; no Subject-suffixed pack titles accepted without a decision; shell or code never rendered as math.
4. **N notes only:** no language named, no code except pseudocode.
5. **S notes only:** C++17 stated, only the approved subset used (no classes, templates, smart-pointer teaching, build systems, STL beyond `vector`, `string`, `sort`, `find`).
6. **Programming 2 memory notes:** the D-27 guardrail (a teaching instrument, not the default style; safer alternatives shown).

## 6. Open items that affect batching

- **Generation is metered.** Check the account's Study Pack and note-generation limits before starting; the pilot used 1 note and 1 pack per Note. I have not verified the quota.
- **Visibility:** the collection can only be published when every note in it is PUBLIC. New notes start private; making them public is an owner step at the end, not a batch setting.
- **The 40 reused notes need the Computer Science tag** before publication; the 7 inspection candidates are authored New until a curator reads them.
- **Seven rows came from splits and two from additions:** all are in the sheet with their final titles.
