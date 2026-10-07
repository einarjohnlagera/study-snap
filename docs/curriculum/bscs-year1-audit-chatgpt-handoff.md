# BSCS Year 1 — Production Discovery Audit (handoff)

Audit date 2026-09-29. Read-only against production. This is discovery evidence, not a curriculum.
Workbook: bscs-year1-production-discovery-audit.xlsx (14 sheets, 358 rows).
Tags used: [FACT] production data · [INFERENCE] curriculum judgement · [ARCH] architecture observation.

## Population
- [FACT] The canonical library is the curator's 1,997 notes, all PUBLIC. The other 8,231 notes belong to 304 accounts.
  7,985 of them are copies of curator notes, and 246 are other users' own notes (excluded).
- [FACT] Counts taken across all owners are inflated by those copies. Philippine History reads 50 across all owners but is 10 canonical notes.
- [FACT] 358 curator notes were swept in by recall queries (subject, program-tag and title-keyword sweeps).
- Classification: STRONG_REUSE 73 · POSSIBLE_REUSE 44 · METADATA_BLOCKED 66 · PROGRAM_SPECIFIC 29 · WRONG_DEPTH 62 · DUPLICATE_CANDIDATE 2 · OUT_OF_SCOPE 82.

## What production gives us
- [FACT] General Education is the only real asset: 73 STRONG notes across 9 GE courses, all COLLEGE + GENERAL_EDUCATION.
  - Philippine History 10, Purposive Communication 8, Science and Technology 8, Contemporary World 8, Rizal 8.
  - Understanding the Self 6, plus 1 teacher-framed note that is excluded. Ethics 7, Art Appreciation 7, Filipino 7.
  - Mathematics in the Modern World has 4 STRONG notes plus 5 POSSIBLE.
- [FACT] 69 of the STRONG notes need only the Computer Science tag (TAG_ONLY). That tagging is legal today, because they already carry six Education programs and a Domain Context.
- [FACT] Each GE course is only 6–10 notes deep. No area reaches 15 usable notes, so every GE Subject Plan will be thin.
- [FACT] "Science and Technology" is mostly natural-science literacy (earth systems, ecology, life science, matter and energy, scientific method). Only 2 of its 8 notes are directly about science, technology and society.

## Zero coverage
- [FACT] Nothing exists for these areas after subject, tag and title-keyword sweeps of all 1,997 curator notes:
  - Introduction to Computing (2 possible notes, 0 strong)
  - Programming Fundamentals I and II
  - Discrete Structures (logic, sets, relations, proofs, induction, counting, graphs)
  - Linear Algebra, Computer Architecture, Operating Systems, Algorithms, Software Engineering, HCI, Programming Languages
- [FACT] The only trace of discrete math is one bullet in a GE problem-solving note that names direct proof, contradiction and induction.
- [FACT] Only 2 thin data-structure and OOP outlines (~900 chars each) exist. Treat them as AUTHOR_NEW.

## Risky reuse
- **BSIT corpus (72 IT-only notes)**
  - [FACT] Content is BSIT-depth networking, databases, integration and events. Domain Context and Authored Depth are unset on every note. Each carries exactly one program, Information Technology.
  - [FACT] Adding Computer Science makes a note two-program. The server then requires a Domain Context, and none of the 12 ratified values is an honest fit for computing.
  - [FACT] Placing a note in a collection is not gated on Applicable Programs (grep of `NoteCollectionService`). Only tagging and discovery are blocked.
  - [INFERENCE] This content is later-year for BSCS (Networks, Information Management, Applications Development), not Year 1.
  - [FACT] Title suffixes ("...in Information Technology") are cosmetic. Content read shows generic treatment, so those notes stay METADATA_BLOCKED, not PROGRAM_SPECIFIC.
- **Engineering Mathematics**
  - [FACT] Every topic exists in two tiers: a BOARD_EXAM_REVIEW batch (WRONG_DEPTH) and an unset-depth batch.
  - [FACT] 14 of the unset-depth notes are engineering-framed throughout (PROGRAM_SPECIFIC). 35 are POSSIBLE_REUSE (28 moderate framing, 7 light). None is STRONG.
  - [FACT] 58 have no Authored Depth. 10 more are explicitly civil-engineering titles at board-review depth.
  - [FACT] Calculus has 11 possible notes, all engineering-authored with no depth set. There is no first-exposure COLLEGE calculus.
- **GE Mathematics (9 notes)**
  - [FACT] Only 4 pass the "unchanged" test: problem solving and logic, functions and sequences, data interpretation, probability and basic statistics.
  - [FACT] The other 5 are introductory numeracy (arithmetic, fractions, geometry, algebraic expressions, personal finance) and need a curator decision.
  - [FACT] Distinctive Mathematics in the Modern World themes (mathematical language, patterns in nature, voting and apportionment) are absent.

## Metadata problems
- [FACT] Computer Science is tagged on only 16 curator notes, and none is computing content: 8 GE math and 8 Project Management.
- [FACT] Software Engineering is tagged on business-law, architectural-design and building-law notes. Computing tags also sit on audit and accounting notes.
- [FACT] "Fundamental Arithmetic and Number Sense" has Computer Engineering but not Computer Science, unlike its 8 siblings.
- [FACT] Duplicate pairs (survivor first):
  - "Graphical User Interface Events" over "...in Application Development"
  - "ACID Properties in Database Systems" over "Database Transactions and ACID Properties"
  - Near-duplicates: transaction management, event handlers, message queues vs brokers, and one Rizal/Propaganda Movement overlap.
- [FACT] A private BS Computer Science root already exists with a 25-note "Year 1" plan. Its structure is obsolete. It holds Self 6, Philippine History 10 and Mathematics in the Modern World 9, and no computing notes.

## Previous curriculum vs current production
- **Still valid**
  - [FACT] All 33 previously "Reuse" GE notes still exist. 28 are STRONG and 5 are POSSIBLE.
  - [FACT] All 35 previously "New" notes are still absent from production. 10 have only a partial closest candidate (see the compare sheet).
  - [INFERENCE] The math policy still holds: no split by college, and no blanket reuse of engineering math.
- **Changed**
  - [FACT] 5 of 9 Mathematics in the Modern World notes are no longer clean reuse.
  - [FACT] The BSIT material is metadata-gated, not simply "IT-specific".
  - [FACT] Understanding the Self is a 6-note corpus, exactly what the old plan used. Purposive Communication (8 STRONG notes) is available but was never placed.
- **Needs fresh decisions:** Mathematics in the Modern World content, calculus and technical-math placement, and computing Domain Context.

## Architecture observations
- [ARCH] The 12 Domain Context values have no computing tradition. A note tagged only Computer Science can stay unset legally, and "computer science" contains the keyword `compute`, so it still gets computation guidance (`OpenAiLlmStudyPackService.java:176`).
- [ARCH] The wall hits notes shared with a second program, for example Programming Fundamentals also applicable to IT, SE or IS.
- [ARCH] Program suffixes in titles ("...in Information Technology", "...in Engineering Mathematics") conflict with knowledge-first title policy. They are a defect independent of the note's treatment.

## Answers in brief
- **Usable now:** the GE half of Year 1 only. Computing and discrete math need full authoring.
- **Split BSCS by College of Engineering vs Science?** No evidence supports it.
- **Highest-value TAG_ONLY:** 69 GE notes across 9 courses.
- **Largest gaps:** Intro to Computing, Programming Fundamentals I and II, Discrete Structures, and what Mathematics in the Modern World should contain.

## Decisions for ChatGPT and the owner
1. How computing notes get a legal Domain Context. This is an architecture decision, not a curation one.
2. What Mathematics in the Modern World should contain. The 4 clean notes are a base, but the course needs more than the current 9.
3. Whether calculus is Year 1 or Year 2, and reuse versus new COLLEGE authoring.
4. Whether any unset-depth engineering math (35 possible notes) is worth a curator read, or the Journey skips it.
5. Whether to tag the 69 GE notes with Computer Science before the Journey is built.
6. Whether the thin GE courses (6–10 notes) are enough for course-sized Subject Plans, or need supplementing.

## Limitations
- Coverage thresholds (8 and 15) are my heuristic, not a product rule. Use the counts, not the labels.
- GE notes were checked by full-text scan for teacher and remedial framing plus a read of each overview, not read line by line.
- Year 1 relevance labels are inferences from earlier CHED-based research, not production facts.
- No semester assignments were made.
