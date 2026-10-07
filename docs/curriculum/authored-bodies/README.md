# Curator-authored bodies for the Path C notes (drafts, 2026-10-06)

> **Drafts for owner review. Nothing here is in production.** Pasting a body into a Note, setting its metadata and generating its Study Pack are owner actions (`CLAUDE.md`: production writes are owner-only). Each file is the Note body; the title and metadata are in the table. Metadata comes from `bscs-year1-shaping.tsv` exactly.

**Count correction.** Path C has **13** notes. Two (Pointers and References in C++; Building and Running a C++ Program) were authored in the pilot (`r4-pilot/note-D`, `note-C`). These **11** are the rest. An earlier message said "eight"; that was wrong.

| # | Title | Subject | Plan | Section | Domain Context | Applicable Programs | Authored Depth | Words | File |
|---|---|---|---|---|---|---|---|---|---|
| 1 | Memory Layout: Stack and Heap in C++ | Programming Fundamentals | Computer Programming 2 | Memory, Pointers and References | COMPUTING | Computer Science, Information Technology, Information Systems | COLLEGE | 1,278 | `pc-01-memory-layout-stack-and-heap-in-cpp.md` |
| 2 | Dynamic Memory Allocation in C++ | Programming Fundamentals | Computer Programming 2 | Memory, Pointers and References | COMPUTING | Computer Science, Information Technology, Information Systems | COLLEGE | 1,179 | `pc-02-dynamic-memory-allocation-in-cpp.md` |
| 3 | Common Memory Errors in C++ | Programming Fundamentals | Computer Programming 2 | Memory, Pointers and References | COMPUTING | Computer Science, Information Technology, Information Systems | COLLEGE | 1,188 | `pc-03-common-memory-errors-in-cpp.md` |
| 4 | Linked List Operations in C++ | Data Structures and Algorithms | Computer Programming 2 | Linked Structures | COMPUTING | Computer Science, Information Technology, Information Systems | COLLEGE | 1,386 | `pc-04-linked-list-operations-in-cpp.md` |
| 5 | Growable Arrays with std::vector in C++ | Programming Fundamentals | Computer Programming 2 | Arrays | COMPUTING | Computer Science, Information Technology, Information Systems | COLLEGE | 1,132 | `pc-05-growable-arrays-with-std-vector-in-cpp.md` |
| 6 | Using Standard Libraries in C++ | Programming Fundamentals | Computer Programming 2 | Standard Libraries and Modules | COMPUTING | Computer Science, Information Technology, Information Systems | COLLEGE | 1,118 | `pc-06-using-standard-libraries-in-cpp.md` |
| 7 | Building Reusable Modules in C++ | Programming Fundamentals | Computer Programming 2 | Standard Libraries and Modules | COMPUTING | Computer Science, Information Technology, Information Systems | COLLEGE | 1,075 | `pc-07-building-reusable-modules-in-cpp.md` |
| 8 | Testing a C++ Program with assert and Test Drivers | Programming Fundamentals | Computer Programming 1 | Testing, Debugging and Code Quality | COMPUTING | Computer Science, Information Technology, Information Systems | COLLEGE | 1,012 | `pc-08-testing-a-cpp-program-with-assert-and-test-drivers.md` |
| 9 | Unit Testing in C++ | Programming Fundamentals | Computer Programming 2 | Program Quality | COMPUTING | Computer Science, Information Technology, Information Systems | COLLEGE | 1,198 | `pc-09-unit-testing-in-cpp.md` |
| 10 | Exceptions in C++ | Programming Fundamentals | Computer Programming 2 | Exceptions and Robust Programming | COMPUTING | Computer Science, Information Technology, Information Systems | COLLEGE | 1,131 | `pc-10-exceptions-in-cpp.md` |
| 11 | Debugging C++ Programs with a Debugger | Programming Fundamentals | Computer Programming 1 | Testing, Debugging and Code Quality | COMPUTING | Computer Science, Information Technology, Information Systems | COLLEGE | 1,273 | `pc-11-debugging-cpp-programs-with-a-debugger.md` |

## Path C feasibility: can the authored route carry these bodies? (FACT from the code, 2026-10-06)

| Question | Finding | Source |
|---|---|---|
| Can a manually created Note carry the TSV metadata? | **Yes.** The upsert request carries `domainContext`, `learnerLevel`, `courseProgramIds` (curators use ids), `tags`, `subject`, `content`. Whether each field is exposed in the new-note form was not checked; the owner knows the form. | `UpsertNoteRequest.java`; `frontend/lib/api.ts:192-196` |
| Is there a cap on the Note body itself? | **No cap** on `content` beyond not-blank. | `UpsertNoteRequest.java:27` |
| Is there a cap at Study Pack generation? | **Yes: it rejects, it does not truncate.** If the whitespace-collapsed body exceeds `max-notes-chars` (default **12,000**; set by `MAX_NOTES_CHARS_FREE`), generation fails with `NOTES_TOO_LONG`. **The production value of that variable was not read**, so the default is the assumption. | `StudyPackService.java:647-662`; `application.yaml:166` |
| Do these bodies fit? | **All 16 authored bodies (11 here, 5 pilot) are at or under 9,756 characters**, the largest being `pc-04`. Collapsing whitespace only shortens them. | measured |
| What does the model actually see? | **A single line.** `normalizeAndValidateText` collapses every run of whitespace to one space before the model sees the note (the code comment calls this right for the LLM input), while the stored Note keeps its structure. **For code, this flattens line breaks, so a `//` comment can appear to run into the next statement.** Prose is unaffected. Untested: no code-bearing body has been through generation yet. | `StudyPackService.java:212, 847-857` |

**Consequence and convention (PROPOSAL).** The authored route works mechanically. The open risk is the flattened-code effect on the code-heavy bodies (`pc-01` to `pc-11` and the pilot Notes C and D). **The pilot re-run on Notes C and D is the test.** If their Study Packs misread the code, the cheapest curator convention is to write explanations as prose next to each code block and use `/* ... */` instead of `//` inside code; that needs no product change. I have not made that edit, because it costs idiom and has no evidence yet.

## Verification run (FACT, 2026-10-06)

- **32 C++ programs** were extracted from the 11 bodies. **30 compile and run standalone** with `c++ -std=c++17 -Wall -Wextra -Werror` (the eight deliberate-error examples in `pc-03` were compiled without `-Werror`). **Two are multi-file and cannot compile alone:** the `pc-07` module `main.cpp` and the `pc-09` test runner. Built with their partner files as the Notes describe them, the module prints `6`, `1`, `0` and the runner prints `4 of 4 checks passed`; compiling `main.cpp` alone fails at link time, as the Note says.
- The printed output of every other program was compared with what its Note states, and matches.
- **Corrected during verification:** the exceptions example used `"12x"`, which `std::stoi` does not reject (it returns 12); it now uses `"abc"`, and the Note says what `"12x"` does. **Also corrected after review:** `pc-02` and `pc-03` did not state C++17 (they do now), and `pc-06` used an iterator variable via `auto`, now rewritten inline.
- **Not verified on this machine:** the address-sanitizer reports and the `gdb`/`lldb` sessions. The sanitizer binaries and the debugger hung here (a macOS permission or crash-reporter wait), so those are described from the tools' documented behaviour and marked illustrative. A curator with a working toolchain should run them once.
- **Scans:** no curriculum branding or NoteLib references; every body states C++17; no use of `auto`, iterator variables, classes, templates, smart-pointer code, `std::map` or build-system files. The scan covers those patterns only; the curator should still read each body against the approved subset.
- **Generation results (2026-10-06):** three authored bodies have now been through Study Pack generation (Notes B, C and D from the pilot; see `r4-pilot/r4-pilot-report.md` section 13) and all three passed, including the D-27 guardrail. **None of these 11 drafts has been through generation yet**; they should each get a curator pass and the per-phase R4 sample.

## Scope and guardrail checks (D-27, C++17 subset)

- **The memory group (`pc-01` to `pc-04`)** presents raw allocation as a **teaching instrument** and states, in each note, the safer alternatives: `std::vector`, `std::string`, RAII and smart pointers (named, not taught). `pc-02` is explicitly "not a recommendation to write `new` and `delete`"; `pc-04` ends by preferring `std::vector` or standard containers.
- **No pointer arithmetic, no classes, no templates, no build systems, no STL survey.** `pc-05` covers `std::vector` only; `pc-06` is "not a tour" and names `std::sort` and `std::find` as the only algorithms; `pc-07` uses plain command-line compilation and says build tools are out of scope.
- **Overlap watch for the curator:** `pc-08` (assert) and `pc-09` (unit testing) are deliberately sequential, not duplicates; `pc-01` and `pc-02` both explain heap versus stack briefly, with `pc-01` the full treatment.
