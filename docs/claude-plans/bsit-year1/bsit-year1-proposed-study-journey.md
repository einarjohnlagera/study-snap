Status: Phase B final shaping, 2026-11-03 (owner decisions Q1 to Q8, S-1 to S-3, S-7 incorporated). Preliminary curriculum shaping. NOT implementation-ready. No production writes, Note generation, Study Pack generation, collection creation or publication is authorized or has occurred. Source of truth for placements: `docs/curriculum/bsit-year1-preliminary-shaping.NOT-IMPLEMENTATION-READY.tsv`; counts below are read from it.

# Proposed Study Journey: 🖥️ BS Information Technology — 1st Year

Owner decisions Q1 to Q8 and the final dispositions S-1 to S-3, S-7 (2026-11-03) are applied. Evidence and reasoning for the curriculum are in `bsit-year1-phase-a-research-and-reuse-audit.md`; this document is the proposed journey.

## Year root

**Title:** `🖥️ BS Information Technology — 1st Year` (a fresh root; the BSCS root and its title are untouched).

**Description (owner-approved, final):**

> A knowledge-focused Study Journey covering selected first-year subjects in a Philippine BS Information Technology degree, including computing, mathematics, and General Education.
>
> It follows a NoteLib reference curriculum rather than any single university's prospectus. Course names, semester placement, and sequence may differ across institutions.
>
> Physical Education and NSTP, while included in many degree programs, fall outside this Notes-based journey because they primarily involve physical activities, community service, or practical participation.

Hierarchy: Root → 10 Subject Plans → Sections → canonical Note memberships. Academic Term is Subject Plan metadata. No third level.

## Term distribution (5 plus 5, Q7)

| Term | Subject Plans |
|---|---|
| First Semester (5) | Introduction to Computing · Computer Programming 1 · Mathematics in the Modern World · The Contemporary World · Art Appreciation |
| Second Semester (5) | Computer Programming 2 · Discrete Mathematics · Understanding the Self · Readings in Philippine History · Purposive Communication |

This is a NoteLib reference arrangement, not a CHED requirement. **The Contemporary World stays in the First Semester by owner decision; 3 of the 5 surveyed BSIT curricula (Silliman, EVSU, MSU Maguindanao) place it in the Second Semester**, and Understanding the Self leans Second as well (3 of 5), which this arrangement matches. Terms were not moved to match BSCS or to match any majority.

## What each plan holds

Counts are Reuse placements (and held-out Excluded rows). Every placement is an existing production Note; see the exact-ID manifest.

| # | Plan | Placements | Sections | Reuse source | Notes for BSIT |
|---|---|---|---|---|---|
| 1 | 💻 Introduction to Computing | 39 | The Computing Disciplines and Profession 5 · History and Evolution of Computing 4 · Information Representation 6 · Computer Systems: Organization and Architecture 7 · Networks, the Internet and the Web 7 · Computer Security and Malware 5 · Ethics, Society and Emerging Trends 5 | BSCS Introduction to Computing, unchanged | CHED common course with identical outcomes. Already tagged Information Technology (38 of 39; the 39th is the GE Note in the metadata handoff). The 4 web Notes stay here (Q4): no separate Web plan |
| 2 | ⌨️ Computer Programming 1 | 42 | Problem Solving and Algorithms 5 · Program Structure, Variables and Data Types 6 · Expressions and Operators 4 · Input and Output 2 · Selection 3 · Iteration 4 · Input Validation 2 · Functions and Procedural Abstraction 6 · Recursion 2 · Testing, Debugging and Code Quality 8 | BSCS Computer Programming 1 | Reference language C++17 restricted (Q1); 22 S Notes name C++, 20 are language-neutral |
| 3 | 📐 Mathematics in the Modern World | 21 (+4 Excluded) | The Nature of Mathematics 5 · Data Management 5 · Logic 1 · Geometric Designs 1 · Codes and Data Transmission 3 · Optimization 1 · Mathematics of Finance 1 · Social Choice and Fair Division 2 · Graphs and Mathematical Systems 2 | BSCS plan | The same four foundation-mathematics Notes stay held out. BSIT has no ALTRIG prerequisite evidence, so no pointer to the optional Precalculus root is needed |
| 4 | 🌏 The Contemporary World | 9 | Globalization 3 · Global Challenges 3 · Technology and Society 1 · The Philippines and the World 2 | BSCS plan | Placement is a choice (see above) |
| 5 | 🎨 Art Appreciation | 7 | Foundations of Art 3 · Forms and Media 1 · Interpretation and Criticism 1 · History and Heritage 2 | BSCS plan | No majority among the surveyed schools |
| 6 | 🧠 Computer Programming 2 | 31 | Arrays 4 · Searching and Sorting Basics 3 · Strings and Text Processing 3 · Structures and Records 2 · Memory, Pointers and References 4 · Linked Structures 3 · Recursion Revisited 2 · Files and Streams 2 · Exceptions and Robust Programming 3 · Standard Libraries and Modules 2 · Program Quality 3 | BSCS Computer Programming 2 | 20 S Notes name C++, 11 are language-neutral. Includes the 6-Note data-structures piece that the CHED CP2 outcomes require |
| 7 | 🧮 Discrete Mathematics | 32 (31 Reuse + 1 New; +3 Excluded) | Propositional Logic 5 · Predicate Logic 3 · Rules of Inference and Arguments 3 · Sets 4 · Relations 4 · Functions and Sequences 4 · **Counting Principles 1 (New)** · Integers and Divisibility 2 · Proof Techniques 6 | 31 of the 34 BSCS Discrete Structures I Notes, plus one new shared Note | A distinct BSIT plan, not a rename. CHED lists Discrete Mathematics (MS101), not Discrete Structures 1 and 2. Three Notes held out (final, S-1 to S-3). The Counting Note is shared computing knowledge and is not added to the BSCS Year; see `bsit-year1-counting-note-authoring-brief.md` |
| 8 | 🪞 Understanding the Self | 8 (+1 Excluded) | The Self from Various Perspectives 3 · Dimensions of the Self 2 · Managing and Caring for the Self 3 | BSCS plan | The teacher-education Note stays held out |
| 9 | 📜 Readings in Philippine History | 10 | Sources and Interpretation 1 · Precolonial and Spanish Periods 2 · Reform, Revolution and the First Republic 2 · American, Japanese and Postwar Periods 3 · Contemporary Philippines 2 | BSCS plan | 2 of the 10 are in the metadata handoff |
| 10 | 💬 Purposive Communication | 12 | Foundations of Communication 4 · Academic and Professional Communication 3 · Communication in a Global and Digital World 2 · Presentation and Workplace Writing 3 | BSCS plan | No majority among the surveyed schools |

**Total: 211 Note placements (210 Reuse + 1 New) and 8 Excluded rows (219 TSV rows).** Plan descriptions are carried from the BSCS plans except plan 7. Its owner-approved description (identical in the TSV, workbook and every document):

> Build the mathematical foundations of computing through propositional and predicate logic, rules of inference, Boolean algebra, sets, relations, functions, sequences, integers and divisibility, elementary counting principles, and introductory proof techniques including mathematical induction. The emphasis is on understanding discrete mathematical concepts and reasoning that support further study in information technology.

## Section-level coherence (Discrete Mathematics)

Sections run Propositional Logic, Predicate Logic, Rules of Inference, Sets, Relations, Functions and Sequences, Counting Principles, Integers and Divisibility, then Proof Techniques. Sequences and Summations precedes the Induction Note, whose worked example is a summation, so the order is prerequisite-coherent. Coverage gaps are in the Discrete Mathematics assessment.

## What BSIT deliberately does not copy

- **Discrete Structures I as a whole:** three of its Notes (Strong Induction; Proving Program Correctness: Assertions and Loop Invariants; Logic in Programming and Software Specifications) stay Excluded for BSIT Year 1 (owner-approved S-1 to S-3). They are unchanged and remain in BSCS. They are not reinstated to raise the Reuse percentage.
- **Discrete Structures II, Algorithms and Complexity, Automata, Programming Languages:** BSCS-only.
- **Networking, HCI, Information Management, Platform Technologies, Web Systems, Data Structures and Algorithms (as a course):** Year 2 at every school read; assessed with BSIT Year 2, not here.
- **PE, NSTP, Science Technology and Society, Ethics, Rizal, Filipino, Entrepreneurship, Calculus, College Algebra:** institution-specific, activity-based, or Year 2 or later.

## Observations recorded, not acted on

- The public BSCS root holds a legacy 25-item plan beside its ten Subject Plans.
- **`term_label` and `term_order` are NULL on all ten BSCS Subject Plans in production** (read 2026-11-03). Academic Term lives on `note_collections.term_label`/`term_order`. The BSIT plans' terms must be set when the collections are built.
- Every one of the 213 BSCS Year Notes has exactly one Study Pack; their state and currency were not read.
- CHED text is from an OCR mirror and is subject to verification against the official PDF.
