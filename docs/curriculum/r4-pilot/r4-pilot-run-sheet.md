# R4 Authoring Pilot — Run Sheet and Report (2026-10-01)

**Status (updated 2026-10-01): the owner ran the production steps; results are in `r4-pilot-report.md`. The pilot ran only partly (4 of 5 Notes, generator-written bodies, programs differ from the TSV).** The text below was written before execution.

**Original status: PREPARED, NOT EXECUTED. The production steps are the owner's.** `CLAUDE.md` makes production read-only for Claude "ever, no exceptions", and says the rule binds every route (a UI, a tool, an admin surface), "not softened by ... the owner approving a plan that happens to contain" a write. Creating Notes and generating Study Packs are production writes. The repo's own R4 runbook (Step 5) says the same: "an owner action through the production UI, never Claude's to run."

**What Claude did (read-only):** the full preflight below, and authored the five canonical Notes as files, compiled-checked and scanned. **What remains:** the owner creates the five Notes and generates their Study Packs through the normal path; Claude then reads the results with `SELECT` and completes sections 6 to 13 of the report. Nothing has been created, generated, published or changed.

---

## 1. Production preflight (read-only, 2026-10-01)

| Check | Result | Verdict |
|---|---|---|
| Deployed version | Render `notelib-backend-prod` live on `edc91fb4` (`v0.164.0 — Computing`, deployed 2026-09-30 13:55:56Z) | `COMPUTING` is deployed |
| Exact five titles, any owner, case-insensitive | **0 matches** | No duplicate |
| Near titles (`algorithm`, `pointer`, `proposition`, `C++`) across 10,547 notes | **0 / 0 / 0 / 0** | No near-duplicate |
| Subject `Computer Science` | exists, 8 notes | Existing shelf |
| Subject `Programming Fundamentals`, `Discrete Mathematics` | 0 notes each | New shelves, approved (D-14); Subject is free text, so typing the value creates the shelf |
| Applicable Programs catalog | Computer Science, Information Technology, Information Systems, Software Engineering all active | Matches the TSV |
| Domain Context values in use | 12 ratified values present; **`COMPUTING` used by 0 notes** | Expected before authoring; enum shipped |
| Authored Depth (`notes.learner_level`) values | `COLLEGE` present (2,540 notes) | Valid |
| Metadata vs the shaping TSV (`bscs-year1-shaping.tsv`) | **No discrepancy** for all five | Proceed |

## 2. The five Notes and exact metadata

Source of truth: `bscs-year1-shaping.tsv`. Content files are in this folder.

| # | Title | Subject | Domain Context | Applicable Programs | Authored Depth | Class | Content file |
|---|---|---|---|---|---|---|---|
| A | The Computing Disciplines: Computer Science, Information Technology, Information Systems, Software Engineering and Computer Engineering | Computer Science | Computing | Computer Science, Information Technology, Information Systems | COLLEGE | n/a | `note-A-computing-disciplines.md` |
| B | Algorithms and Their Properties | Programming Fundamentals | Computing | Computer Science, Information Technology, Information Systems | COLLEGE | **N** | `note-B-algorithms-and-their-properties.md` |
| C | Building and Running a C++ Program | Programming Fundamentals | Computing | Computer Science, Information Technology, Information Systems | COLLEGE | **S** | `note-C-building-and-running-a-cpp-program.md` |
| D | Pointers and References in C++ | Programming Fundamentals | Computing | Computer Science, Information Technology, Information Systems | COLLEGE | **S** | `note-D-pointers-and-references-in-cpp.md` |
| E | Propositions and Logical Connectives | Discrete Mathematics | Computing | **Computer Science only** | COLLEGE | n/a | `note-E-propositions-and-logical-connectives.md` |

**Note E** keeps Applicable Programs at Computer Science only; do not add IT, IS or Software Engineering because Domain Context is Computing. **Notes A to D** carry three programs, so Domain Context is mandatory and there is no unset fallback arm (per the runbook, do not strip programs to force a comparison).

## 3. Authoring checks already run on the content files (FACT)

| File | Words | Brand or curriculum terms | Language tokens |
|---|---|---|---|
| A | 1,004 | none | not applicable |
| B (N) | 1,207 | none | **0** (no C++, Python, Java, `#include`, `std::`) |
| C (S) | 1,115 | none | C++17 throughout, as intended |
| D (S) | 1,270 | none | C++17 throughout, as intended |
| E | 1,271 | none | 0 |

- **Every C++ example in C and D was compiled** with `c++ -std=c++17 -Wall -Wextra`: all build with no warnings, and their outputs match what the Notes state (for example `Sum: 7`; 10, 10, 25, `7 25`, 30; and 7). The dangling-pointer example is flagged by the compiler, as the Note says.
- **Subset compliance (C, D):** no templates, no `new`/`delete`, no pointer arithmetic, no smart-pointer teaching (named only), no build systems beyond one source file, no STL beyond `iostream`. Note D names `std::string` and `std::vector` as preferred types without using them.
- **Guardrail (D):** it establishes address, indirection, pointer versus reference, `nullptr`, dereferencing, lifetime and dangling, and the link to later linked structures; it does **not** teach dynamic allocation; it states that raw pointers are not the default modern solution.

## 4. Owner steps (through the production UI; nothing here is Claude's)

1. For each Note, create it with the title, content and metadata in section 2. Use **Authored Depth = COLLEGE** (labelled Note Learner Level in the editor). Leave visibility **private** until review; this pilot does not publish.
2. **Record the tags each Note actually carries.** The computation-guidance scan reads Subject and tags (section 5), so tags are part of the experiment. Do not add keyword-bearing tags to coax behaviour.
3. Verify the metadata on the saved Note matches the table (especially Domain Context = Computing and the Applicable Programs set).
4. Generate each Study Pack through the **normal path** (the standard Generate Study Pack action). Do not regenerate, edit or rewrite a generated pack before evaluation.
5. Tell Claude "pilot generated". Claude runs the read-only captures in section 7 and completes the report.

## 5. Computation-guidance prediction (from code; NOT yet measured)

**Correction (2026-10-01):** the claim below that `questionType` is an "observable proxy" for guidance firing is WRONG. The schema allows the model to emit CONCEPTUAL unprompted (pack C did). The reliable method is to replay the scan on the label, Subject and tags, as the report does.

**FACT (code):** `generateStudyPack` builds its prompt with `isQuantitativeContext(context, List.of(), null)` (`OpenAiLlmStudyPackService.java:314-318`), so on the normal path the scan sees only the **authoring domain label, the Subject and the tags**. With Domain Context set, the label is "Computing", which contains no keyword (`computing` does not contain `compute`). When guidance fires, the quiz items carry a `questionType` of COMPUTATIONAL or CONCEPTUAL; otherwise it is null. ~~So `questionType` is an observable proxy for whether guidance fired.~~ (withdrawn; see correction above)

| Note | Subject text | Keyword hit from Subject | Prediction (if no keyword tags) |
|---|---|---|---|
| A | Computer Science | "compute" inside "computer" | fires |
| B | Programming Fundamentals | none | **does not fire** (actual: fired, because the auto-generated tag "Algorithms" contains `algorithm`) |
| C | Programming Fundamentals | none | **does not fire** |
| D | Programming Fundamentals | none | **does not fire** |
| E | Discrete Mathematics | "math", "mathematics" | fires |

**What to measure:** whether B (tracing Euclid's algorithm), C (predicting program output) and D (predicting values through a pointer) get materially weaker computational or tracing questions without guidance than A and E get with it. Per the owner's instruction, **do not change prompts, keywords or the `quantitative` flag**; measure first.

## 6. Evaluation protocol

**Dimensions per Note:** A Domain fidelity · B Canonicality · C Depth · D N/S fidelity · E Computing prompt behaviour · F Assessment quality · G Hallucination and scope drift.
**Status per dimension:** PASS · PASS WITH CURATOR EDIT · FAIL — GENERATION BEHAVIOR · FAIL — SOURCE NOTE · FAIL — METADATA / CONTEXT. No single numeric score.
**Failure origin (name one per finding):** canonical Note content · Domain Context · Subject · Applicable Programs · Authored Depth · generation prompt behaviour · N/S convention · Study Pack generation itself. A problem is fixed at its origin only.

**Watch items (runbook Step 5, scored per Note):** (1) inappropriate language-specific assumptions; (2) source code forced into conceptual material; (3) over-generic computer-literacy treatment; (4) CS-specific framing bleeding into shared material; (5) failure to treat computational or discrete reasoning as rigorous.

**N/S scan (mechanical):** Study Pack text for Note B is searched for `C++`, `Python`, `Java`, `JavaScript`, `#include`, `std::`, `cout`, `int main`, `nullptr`, `g++`, and any fenced code with a language tag; every hit is listed and judged. Notes C and D are checked for C++17 correctness and for anything outside the approved subset.

**Quiz checks per question:** answerable from the Note; one defensible answer; plausible distractors; tests understanding rather than wording recall; no unsupported assumption; N/S behaviour preserved. **Hallucination scan:** every claim in the Summary and Key Concepts is traced to the source Note; unsupported, overly advanced, language-specific-where-neutral, incorrect or misleading claims are listed.

**Stop conditions** (stop and report, do not patch around): consistently wrong Computing framing; N Note materially drifting into C++; S Note without usable C++ treatment; an unexpected generation-legality problem from Domain Context; systematic quiz correctness problems; a computation-guidance regression that is meaningful; or Note content that must be distorted to coax the generator.

**A/B arm:** none run. Note E is single-program, so an unset-versus-Computing comparison is possible, but it needs a second production write (an unset twin) and Note E's Subject fires the guidance regardless; the runbook makes the arm optional. Notes A to D have no fallback arm by design.

## 7. Read-only capture queries (Claude runs these after "pilot generated")

```sql
-- Notes: ids and exact metadata
SELECT n.id::text, n.title, n.subject, n.domain_context, n.learner_level, n.visibility, n.status,
       n.tags, n.created_at,
       (SELECT string_agg(cp.name, ', ' ORDER BY cp.name)
          FROM note_course_program x JOIN course_programs cp ON cp.id = x.course_program_id
         WHERE x.note_id = n.id) AS programs
FROM notes n
WHERE lower(n.title) IN (
  'the computing disciplines: computer science, information technology, information systems, software engineering and computer engineering',
  'algorithms and their properties', 'building and running a c++ program',
  'pointers and references in c++', 'propositions and logical connectives')
ORDER BY n.created_at;

-- Study Packs for those notes
WITH pilot AS (
  SELECT id FROM notes WHERE lower(title) IN (
    'the computing disciplines: computer science, information technology, information systems, software engineering and computer engineering',
    'algorithms and their properties', 'building and running a c++ program',
    'pointers and references in c++', 'propositions and logical connectives')
)
SELECT sp.id::text, sp.note_id::text, sp.status, sp.error_code, sp.model_tier, sp.model_used,
       sp.input_tokens, sp.output_tokens, sp.subject, sp.tags, sp.title, sp.summary,
       sp.key_concepts, sp.quiz, sp.created_at
FROM study_packs sp
WHERE sp.note_id IN (SELECT id FROM pilot)
ORDER BY sp.created_at;
```

**Known limit (FACT):** no application log line records the authoring domain or whether computation guidance fired, and `study_packs` stores no prompt. Domain behaviour is therefore diagnosed from the generated text, the `questionType` proxy and the code path in section 5, not from a log.

## 8. Report (to be completed after execution)

| # | Item | Status |
|---|---|---|
| 1 | Production preflight | **Done** (section 1) |
| 2 | Five canonical Note IDs | Pending owner creation |
| 3 | Exact metadata per Note | Specified (section 2); verify after creation |
| 4 | Study Pack IDs | Pending generation |
| 5 | Content summary per Note | Authored; summaries below |
| 6 to 10 | R4 evaluation; N/S, Computing and computation-guidance findings; quiz quality | Pending generation |
| 11 | Curator edits required | Pending evaluation |
| 12 | Architecture or generation issues | None found in preflight; one limitation (no domain logging) |
| 13 | Final recommendation | **Not issued.** Evaluation has not run. |

**Content summaries.** **A** presents the five computing disciplines (Computer Science, Computer Engineering, Software Engineering, Information Systems, Information Technology) by central question and emphasis, their shared foundation, a one-problem-five-contributions example and common misunderstandings. **B** defines an algorithm and its properties (input, output, definiteness, finiteness, effectiveness, correctness) with traced examples (finding the largest item, Euclid's algorithm), the three building blocks, representations, algorithm versus program, and correctness versus efficiency, in neutral pseudocode only. **C** follows C++17 source to executable (preprocess, compile, link), the `g++ -std=c++17 -Wall -Wextra` command, the edit-compile-run cycle, errors versus warnings versus runtime problems and common mistakes. **D** teaches addresses, pointers, `nullptr`, dereferencing, references, pointer versus reference, indirection through function parameters, lifetime and dangling, and modern guidance to prefer values, references and library types. **E** teaches propositions, truth values, atomic and compound propositions, the six connectives with truth tables, a worked truth table, precedence and English-to-logic translation.

**Do not begin the next batch.** The owner reviews the completed report first.
