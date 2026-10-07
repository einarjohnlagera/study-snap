# Programming Reference-Language Final Review (approved 2026-10-01)

> **D-27, D-35 and D-36 are OWNER APPROVED (2026-09-30).** This is the record of the approved architecture; the decision is no longer pending. No production writes, no notes generated, no collections created.

## 1. What was approved

- **D-27 — C++17 restricted teaching subset** is the reference programming language. The reason is curricular: Programming 1 and 2 teach language-neutral concepts where knowledge is portable, and C++ is the concrete executable environment for the language-dependent portions. The Year 1 treatment of stack versus heap, pointers and references, dynamic allocation, object and value lifetime, common memory errors, linked structures and static versus dynamic implementations is retained.
- **Guardrail (part of D-27):** manual memory management is a **teaching instrument for understanding the computational model**. It must not become the organizing theme of Programming 2 or turn the course into a survey of C++ complexity, and raw pointers, manual allocation, pointer arithmetic and C-style techniques are **not the default implementation style**. Where appropriate, show the relationship to safer standard-library abstractions, so a learner understands *why* dynamic memory and linked structures work without believing manual memory management is the preferred answer to every problem.
- **D-35 — curation convention, no product change** (section 5).
- **D-36 — the final Programming corpus:** 64 notes become 71 after 7 justified splits, plus two additions; **42 notes in Programming 1, 31 in Programming 2; 31 N and 42 S**. Full table: `bscs-year1-programming-nsm-classification.md`.

## 2. Why the choice was a real decision (record)

Four notes decide the language: Memory Layout: Stack and Heap; Pointers and References; Dynamic Memory Allocation; Common Memory Errors. They are C/C++ mechanics, were not CHED outcomes, and a language-neutral design would not necessarily include them. The owner decided to keep them as a teaching instrument. That one decision determines the language: with it, C and C++ have no hard break and Java and Python have three each; without it, Java would have been the better reference language. Counted under one rule (hard break = no counterpart in the language; adaptation = a counterpart with a different mechanism). The full comparison is in the workbook sheet `Language Assessment`.

| | Python | Java | C | C++17 subset |
|---|---|---|---|---|
| Hard breaks over the 42 S notes | 3 | 3 | 0 | 0 |
| Retitleable or tooling adaptations | many | few | few | unit-test tooling only |
| Burden for a first-year learner | lowest | moderate | high | highest, mitigated by the subset |
| OOP continuity | yes | strong | none | yes |

Sorting and searching (three notes) are present by convention rather than by a CHED Intermediate Programming outcome; they remain introductions and Year 2 extends rather than repeats them.

## 3. The restricted C++17 teaching subset

Curriculum level, not a style guide. Standard C++17, compiled with warnings enabled. The goal is to make the knowledge executable, testable and debuggable, not to teach C++.

**Teach in Programming 1.** `main`, `#include`, statements, comments; `std::`-qualified standard library; `int`, `double`, `char`, `bool`, minimal `std::string` (declare, read, print, length, concatenate), `const`; operators and expressions, `static_cast`, `<cmath>`, `<iomanip>`; `if`/`else`, `switch`, `while`, `do-while`, classic `for`; functions with value, reference and const-reference parameters, scope, recursion; console I/O and stream failure handling; `<cassert>`, test drivers, compiler diagnostics, a debugger.

**Defer to Programming 2.** Fixed-size raw arrays (one- and two-dimensional); `std::string` in depth against C-style arrays; `struct` as a plain record; pointers, `nullptr`, `->`; `new`/`delete` and `new[]`/`delete[]`; a linked list with raw pointers; memory errors observed with an address sanitizer; **`std::vector` as the growable contrast** (the approved addition), range-based `for` over vectors and strings; `<fstream>`, `<sstream>`; exceptions; `std::sort` and `std::find` as library-use examples; headers, source files and include guards; assert-based unit tests.

**Guardrail applied to authoring.** The memory-allocation, memory-error and linked-list notes must show the relationship to `std::vector`, `std::string` and RAII, and **name smart pointers without teaching them**. Raw pointers are used to explain the model, not as the default style elsewhere in the course.

**Exclude from Year 1.** Classes with member functions, constructors and access control; inheritance and polymorphism; authoring templates; explicit iterators; teaching smart pointers; operator overloading; lambdas; move semantics and the rule of five; `std::array`; STL algorithms beyond `sort` and `find`; C-style casts; macros beyond include guards; `goto`; concurrency; undefined-behaviour theory beyond the common-memory-errors note; template metaprogramming.

## 4. Final progression check (deterministic)

**Method.** An explicit table of 101 prerequisite edges across Programming 1 and 2 (each edge a real prerequisite, not a preference), tested against the actual row order in `bscs-year1-shaping.tsv`, plus the owner's nine milestone checks and a scan of titles for excluded C++ features and full Data Structures and Algorithms topics.

**Result: 101 edges, 0 violations after two ordering changes.**

| Owner check | Result |
|---|---|
| functions precede advanced use | pass |
| references introduced before necessary | pass |
| arrays precede linked structures | pass |
| stack/heap precede dynamic allocation | pass |
| pointers/references precede linked-list implementation | pass |
| test-case design precedes executable testing | pass |
| conceptual debugging precedes tooling | pass |
| first-exposure recursion precedes applied recursion | pass |
| Programming 1 precedes Programming 2 | pass |

**Ordering changes (only where a real prerequisite problem existed):**
1. **Input Validation** (both notes) moved after Iteration into its own section: it needs selection and loops but sat before both.
2. **Tracing and Dry Runs** moved next to the variable-state notes: "Tracing Recursive Calls" and every loop trace need it; it sat at the end of the course.

**No note depends on C++ syntax not yet introduced, given these authoring constraints** (they are scope limits for the author, not ordering changes):
- Examples before the I/O section print with `std::cout` introduced in "Structure of a C++ Program" (the hello-world idiom); "Console Input and Output in C++" explains it.
- "Text Parsing and Simple Pattern Matching in C++" uses only `std::string` operations from the preceding note, not `<sstream>` (introduced later with files and libraries).
- `std::string` in Programming 1 stays minimal; Programming 2 deepens it.

**Data Structures and Algorithms readiness.** Programming 2 introduces arrays, informal sorting and searching, linked lists and the static-versus-dynamic contrast, and contains no stacks, queues, trees, graphs, hashing or formal complexity; Year 2 Data Structures and Algorithms extends rather than repeats.

## 5. D-35 — the approved convention

**Fact (code):** bulk generation carries `subject`, `topics`, programs, `domainContext`, `learnerLevel`, `collectionId`, `sectionLabel`; there is no language field and no backend prompt names a programming language. The title is the only steer.

1. **N notes:** knowledge-first, language-neutral titles; content limited to prose, diagrams, tables and pseudocode. Do **not** add "C++" to an N title because the journey uses C++.
2. **S notes:** C++ is explicit in the title; executable C++17 examples allowed.
3. **M notes:** the final disposition (7 splits, 6 resolved without a split).
4. **Not authorized:** a Reference Language field, a C++ Domain Context, C++ in Applicable Programs, a global C++ Subject, or any programming-language behaviour in `Computing`. Domain Context stays `Computing` for C++-specific notes.
5. **Drift remedy:** if an N note drifts unnecessarily into C++, correct it **at the note-content level** and regenerate its Study Pack; regenerating from the same neutral title would reproduce the drift.
6. **R4 pilot acceptance check:** language drift (three N notes, two S notes), which also measures how much editing the convention costs.

**Does it satisfy D-05 at curriculum level?** Yes. The 42 S notes carry runnable C++, including building and running, reading compiler errors, debugging with a debugger, and executable testing (the approved Programming 1 addition) through unit testing. Individual N notes are pseudocode by design.

## 6. Future reuse

The 31 N notes reuse unchanged across Information Technology, Information Systems and Software Engineering (shared CMO 25 common courses). S notes reuse where a program teaches C++ (Computer Engineering, Software Engineering). Certification, interview and professional journeys can reuse the algorithm, recursion, complexity and testing notes; a learner's later goal never changes Domain Context, and none of that metadata is created here. Applicable Programs on S notes stay Computer Science, Information Technology, Information Systems.

## 7. Remaining items

**Shaping blockers: none.** See `bscs-year1-readiness-and-handoff.md` for the readiness interpretation and the curator and pre-publication actions.

**Checkpoint denominator shift (FACT).** ADR-001's Computing checkpoint cites a known single-program baseline of 34 of 136 rows (about 25%). The default TSV now has 145 `COMPUTING` rows, so the baseline is 34 of 145 (about 23%): all 9 new Programming notes carry three programs. The ROADMAP checkpoint row uses the older denominator; updating it is the owner's call.

## 8. Files changed (documentation and shaping files only)

`bscs-year1-shaping.tsv` and `bscs-precalculus-preparatory-shaping.tsv` (renamed, `NOT-IMPLEMENTATION-READY` removed); `bscs-year1-curriculum-coverage.xlsx` (regenerated from the TSVs); `bscs-year1-decision-log.md`; `bscs-year1-proposed-study-journey.md`; `bscs-year1-programming-nsm-classification.md`; this file; `bscs-year1-readiness-and-handoff.md`; `bscs-year1-precalculus-preparatory-proposal.md`; `bscs-year1-reuse-and-metadata-assessment.md`. **No production writes, no generation, no collections, no code, no new product fields.**
