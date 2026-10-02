# R4 Authoring Pilot — Report (2026-10-01)

**Method.** Everything below was read from production with `SELECT` only. Claude created and generated nothing. **FACT** = read from production or the code; **INFERENCE** = my reading.

## 0. The result in one paragraph

**The pilot did not run as specified, so the final recommendation is withheld (section 11).** Four of the five Notes existed at first; **"Pointers and References in C++" was created afterwards and is evaluated in section 12: it fails the D-27 guardrail.** The four that exist are **not the Notes I authored**: their bodies are the generator's standard ~2,000-character outlines written from the title, so the pilot measured the *bulk path*, not authored canonical Notes. And **all four carry five Applicable Programs** instead of the shaping TSV's set. Within those limits, the generator behaved well on the architecture questions (Computing legality, framing, neutral-versus-C++), with real findings on quiz quality, depth and formatting.

## 1. Production preflight (before the pilot)

Clean, and recorded in `r4-pilot-run-sheet.md` §1: `COMPUTING` live on `edc91fb4`; none of the five titles existed; Subjects, programs and `COLLEGE` depth matched the TSV.

## 2. What exists in production (FACT)

| # | Note | Note ID | Study Pack ID |
|---|---|---|---|
| A | The Computing Disciplines: … | `9b81844a-209c-426a-89e9-0f110620b9f8` | `aa6a4f29-457b-491f-aad9-257c301b81fe` |
| B | Algorithms and Their Properties | `583c82dc-fc5b-4088-ae21-dd474a787be3` | `e611a789-50fb-4cb3-b048-7b273013b20e` |
| C | Building and Running a C++ Program | `bb34133d-7427-46ca-a8de-b0364cc2236a` | `4b0dd898-c930-4aa6-884d-d0e954805528` |
| D | Pointers and References in C++ | `f79dc811-f532-453e-a68f-f0f96f3f209c` (created later, 13:04Z) | `c28b601f-ed39-4fb1-a498-1057e3f542c1` |
| E | Propositions and Logical Connectives | `edeb5f50-6a28-4dd9-a622-8682d15cdae4` | `d41b0897-431b-4af9-8086-c6e47ba5af63` |

No duplicates (one note per title). Search for any note created today with "pointer", "reference" or "C++" in the title finds only Note C.

## 3. Exact metadata versus the shaping TSV

| Note | Subject | Domain Context | Authored Depth | Visibility | Applicable Programs **in production** | **TSV says** | Match |
|---|---|---|---|---|---|---|---|
| A | Computer Science | COMPUTING | COLLEGE | PRIVATE | CE, CS, IS, IT, **SE** | CS, IT, IS | **No** (+Computer Engineering, +Software Engineering) |
| B | Programming Fundamentals | COMPUTING | COLLEGE | PRIVATE | CE, CS, IS, IT, SE | CS, IT, IS | **No** (same two extra) |
| C | Programming Fundamentals | COMPUTING | COLLEGE | PRIVATE | CE, CS, IS, IT, SE | CS, IT, IS | **No** (same two extra) |
| D | Programming Fundamentals | COMPUTING | COLLEGE | PRIVATE | CE, CS, IS, IT, SE | CS, IT, IS | **No** (same two extra) |
| E | Discrete Mathematics | COMPUTING | COLLEGE | PRIVATE | CE, CS, IS, IT, SE | **Computer Science only** | **No** (+4 programs) |

**Failure origin: Applicable Programs, set at creation.** I checked the code: nothing adds programs when Computing is selected, so this came from the creation step. **Note E is the direct violation of the instruction** not to add IT, IS or Software Engineering because Domain Context is Computing, so **the Domain Context versus Applicable Programs separation test was not validly run.** Do not fix this by changing Domain Context.

Tags (FACT, auto-generated, not curator-entered): A *Computing Disciplines, Software Development, IT Management, Hardware Design, System Integration*; B *Algorithms, Programming Fundamentals, Algorithm Efficiency, Problem Solving, Software Reliability*; C *C++ Programming, Compilation, Linking, Executable, Debugging*; E *Logical Connectives, Propositions, Truth Tables, Logical Reasoning, Computing Foundations*.

## 4. The deviation that matters most: the Notes are not the authored Notes

**FACT.** The production Note bodies are 2,173, 1,915, 1,777 and 1,808 characters in the generator's standard shape (📘 Overview, 🧠 Key Idea, ⚔️ Core Details, 🎯 Why It Matters, 🧠 Quick Recall), written from the title. The Notes I authored are 1,000–1,270 words each (roughly 7,000–9,000 characters) and were not used. All four have status `GENERATED`, and the Study Packs were produced by `gpt-4.1-mini-2025-04-14` (tier label PREMIUM).

**Consequence (INFERENCE).** The brief required "real course-quality canonical Notes ... do not write short outline Notes." What was tested is the **bulk-generation path**, which is arguably the more relevant test for scaling if the corpus will be bulk-generated, but it is **not** the test of authored canonical Notes. These two paths produce different depth, and the owner needs to choose which one the corpus will use.

## 5. Study Pack findings and scorecard

Status key: **P** PASS · **PE** PASS WITH CURATOR EDIT · **FG** FAIL — GENERATION BEHAVIOR · **FS** FAIL — SOURCE NOTE · **FM** FAIL — METADATA / CONTEXT. Dimensions: A Domain fidelity · B Canonicality · C Depth · D N/S fidelity · E Computing prompt behaviour · F Assessment quality · G Hallucination / scope drift.

| Note | A | B | C | D | E | F | G | Metadata |
|---|---|---|---|---|---|---|---|---|
| A Computing Disciplines | P | PE | **FS** | n/a | P | PE | P | **FM** |
| B Algorithms (N) | P | PE | **FS** | **P** | P (latent, see §8) | **FG** | **FG** | **FM** |
| C Building and Running (S) | P | PE | **FS** | PE | P | PE | **FG** | **FM** |
| D Pointers and References (S) | PE | **FS** | **FS** | PE; **guardrail FS** | P | PE | **FS** | **FM** | |
| E Propositions | P | PE | **FS** | n/a | P | PE | P | **FM** |

### Findings, each with its origin

1. **Depth (all four): FAIL — SOURCE NOTE.** The source outlines are definitional only: no worked example, no misconception section, no tracing, no code. Note E defines four connectives and names a fifth, with no truth tables, no exclusive or, no precedence and no translation. **Origin: canonical Note content (title-generated).** This is a source problem, not a Study Pack problem; the packs faithfully inherit it.
2. **Quiz defect, Note B (FAIL — GENERATION BEHAVIOR).** Question 4 ("All of the following are properties of an algorithm EXCEPT") gives *Randomness* as the answer, with the explanation that "algorithms require definitive, deterministic steps." That is **unsupported by the Note and misleading**: randomized algorithms are a standard class, and definiteness does not mean determinism. **Origin: Study Pack generation introducing an unsupported claim.** Question 5 ("every algorithm must have input and produce output") is contestable, since the standard definition allows zero inputs; that part originates in the source outline, which presents input and output as mandatory.
3. **Source conflation, Note B.** The generated outline lists *efficiency* beside correctness, finiteness and definiteness as a defining property and omits *effectiveness*. **Origin: canonical Note content.**
4. **Formatting defect, Note C (FAIL — GENERATION BEHAVIOR).** The Summary renders shell commands as math: `$g++\ program.cpp\ -o\ program$`, `$./program$`, `$-g$`. That will display as malformed math. **Origin: Study Pack generation.**
5. **S fidelity, Note C.** The Study Pack uses C++ naturally and correctly (`.cpp`, `g++`, object files, linking, `-g`), but **neither the Note nor the pack says C++17**, there is no `-std=c++17` or `-Wall`, and there is no executable example. The source outline is generic "GNU C++". **Origin: canonical Note content.** Whether an S Note "receives C++17 treatment" cannot be shown when the Note itself never asks for it.
6. **Subject leaks into titles.** The generator appended the Subject to the pack title and to the first line of the body: "Algorithms and Their Properties **in Programming Fundamentals**", "Propositions and Logical Connectives **in Discrete Mathematics**". The Note titles themselves are clean. **Origin: generation behaviour.** It conflicts with knowledge-first titling wherever the pack title is shown.
7. **Scope drift, Note E (minor).** Key Concepts include *Logical Equivalence* and *Formal Verification*, which appear only in the Note's "Why It Matters" bullets and are never defined. **Origin: Study Pack generation reading past the Note's core content.**

### Quiz quality (20 questions across four packs)

All four packs are five multiple-choice questions, mostly definitional recall, with a repeated "Statement 1 / Statement 2" item in B, C and E. **One clear error (B, Q4) and one contestable item (B, Q5) in 20.** No question was unanswerable from its Note. Distractors are plausible. There are almost no scenario, tracing or application questions, which follows from the shallow source Notes. **This is not a systematic correctness problem**, so it does not trigger the stop condition, but it is a depth problem.

## 6. N/S fidelity (D-35)

- **N (Note B): PASS.** FACT: the generated Note and Study Pack contain **no language-specific content**: no C++, Python or Java, no code, no `#include`. A neutral title produced neutral content, with Subject "Programming Fundamentals" and Domain Context Computing, and no language field. This is the strongest positive result for D-35.
- **S (Note C): PASS WITH CURATOR EDIT.** Correct, natural C++ for the facts it states; needs the C++17 standard, `-std=c++17 -Wall -Wextra`, an executable example and the edit-compile-run cycle added. No new metadata field or C++ Domain Context was needed to get C++ treatment, since the title alone steered it.
- **The D-27 guardrail (Note D) was not tested.**

## 7. Computing Domain Context findings

- **Legality:** FACT: generation succeeded for all four with `COMPUTING` and five programs; no legality problem.
- **Framing:** PASS. No engineering, business, teacher-education or generic General Education framing. Note A treats the five disciplines evenly and does not collapse into Computer Science only.
- **Not tested:** the separation of Domain Context from Applicable Programs (section 3).

## 8. Computation-guidance findings (the known nuance)

**Correction first.** In the run sheet I called each quiz item's `questionType` an "observable proxy" for whether guidance fired. **That was wrong.** The schema lets the model emit `CONCEPTUAL` unprompted, and Note C shows it. I have corrected the run sheet.

**Measured instead by replaying the code's own scan.** On the normal Study Pack path the scan sees only the authoring-domain label ("Computing"), the Subject and the tags (`OpenAiLlmStudyPackService.java:314-318`). Replaying it on the four Notes' actual inputs with the code's real keyword list (45 plain, 7 anchored):

| Note | Fires? | Because |
|---|---|---|
| A | Yes | Subject "Computer Science" contains `compute` |
| B | Yes, **only by luck** | the auto-generated tag "Algorithms" contains `algorithm`; the Subject "Programming Fundamentals" has no keyword |
| C | **No** | no keyword in Subject or tags |
| E | Yes | Subject "Discrete Mathematics" contains `math` |

**Finding.** The nuance is real and **currently masked**: Note B reached computation guidance only because the model happened to tag it "Algorithms". Had the tags differed, a core algorithm Note would have generated without it. **No meaningful quality regression was observed in this sample**: Note C has nothing computational to ask, and Note E received guidance but its quiz is still definitional, so the guidance did not visibly change the output. Per instruction, no prompt, keyword or flag was changed. The risk to track at scale is **dependence on auto-generated tags** for "Programming Fundamentals" Notes.

## 9. Curator edits I believe are required

1. **Fix Applicable Programs on all four Notes** to the TSV (A, B, C: Computer Science, Information Technology, Information Systems; E: Computer Science only).
2. **Create "Pointers and References in C++"** and generate its pack, then evaluate it against the D-27 guardrail.
3. **Decide the content path** (section 11), then for any Note kept as authored canonical content, replace the generated body with the authored text and regenerate the Study Pack.
4. **Note B:** if the generated body is kept, correct the efficiency/effectiveness conflation and the "must have input" claim; the Study Pack's randomness question must be removed or regenerated.
5. **Note C:** add the C++17 standard, the `-std=c++17 -Wall -Wextra` command and an executable example; regenerate to clear the LaTeX-formatted commands.
6. **Pack titles:** review whether the appended "in <Subject>" is acceptable.

## 10. Architecture and generation issues discovered

| # | Issue | Origin | Severity |
|---|---|---|---|
| 1 | Generator-written Notes are ~2,000-character outlines, not course-quality Notes | canonical Note content / generation path | **Decision needed** |
| 2 | Study Pack quiz asserts an unsupported, misleading claim (randomness) | Study Pack generation | Quality; one instance |
| 3 | Shell commands rendered as LaTeX math in a Summary | Study Pack generation | Formatting |
| 4 | Subject appended to pack titles and body headings | generation behaviour | Titling policy |
| 5 | Computation guidance depends on auto-generated tags for Subjects without a keyword | generation prompt behaviour | Latent, unmeasured regression |
| 6 | No log records authoring domain or whether guidance fired; `study_packs` stores no prompt | observability | Diagnostics limit |
| 7 | Applicable Programs set to five on all notes | creation step | Metadata |

**No architecture issue was found** in Computing, Subject, N/S or the Domain Context/Applicable Programs *design*: the latter was simply not validly exercised.

## 11. Final recommendation

**Not issued, because the pilot is incomplete.** Note D is missing, so the guardrail was untested; Note E's metadata invalidates the Domain Context versus Applicable Programs test; and the Notes tested are generated outlines, not the authored canonical Notes the brief specified.

**Provisional reading (INFERENCE), clearly not a verdict:** nothing observed points to *STOP — ARCHITECTURE ISSUE* or *STOP — GENERATION CHANGE REQUIRED*. The result is consistent with **SAFE TO SCALE WITH CURATOR CONVENTION**, provided the owner first decides the content path and the three gaps are closed:

1. **Which path will the corpus use?** Bulk generation (fast, outline-depth, shows the defects above) or curator-authored canonical content pasted in (the standard in the brief; costs authoring time). If bulk, expect outline-level depth and a quiz-review step; if authored, the pilot must be re-run on authored text.
2. **Re-run the missing and invalidated parts:** create Note D, correct the programs on A, B, C and E, and (if authored path) replace bodies and regenerate.
3. **Adopt a curator convention** for scale: verify metadata at creation, review N Study Packs for unsupported claims, and check that C++ S Notes state C++17.

## 12. Addendum: Note D, "Pointers and References in C++" (evaluated 2026-10-01)

**FACT.** Created 13:04Z, `GENERATED`, Domain Context COMPUTING, Subject Programming Fundamentals, COLLEGE, PRIVATE, one note with that title. **Applicable Programs are again the five (CE, CS, IS, IT, SE), not the TSV's three.** The body is a 2,343-character generator outline; the Study Pack (`gpt-4.1-mini-2025-04-14`) is `DONE`. Tags: *C++, Memory Management, Pointers, References, Dynamic Allocation*. Computation guidance (replayed): **does not fire**; nothing computational is needed here, so no regression.

**The D-27 guardrail test: FAIL — SOURCE NOTE (origin: canonical Note content generated from the title).** The brief said the Note must not turn into the Dynamic Memory Allocation lesson, must not make raw pointers look like the preferred solution, and must cover address and indirection, `nullptr`, lifetime and the link to later structures.

| Required by the brief | What the generated Note and pack do |
|---|---|
| Not the dynamic-memory lesson | **Violated.** "Both are essential for dynamic memory management"; "Pointers can be used for dynamic memory allocation with operators new and delete"; Quick Recall includes "new allocates, delete frees"; Key Concepts include *Dynamic Memory Allocation* and *new and delete operators*; the summary leads with it. |
| Raw pointers not the preferred solution | **Violated in tone.** "Pointers enable direct memory management ... critical for performance-critical applications, system programming, and interfacing with hardware"; no mention of values, `std::vector`, `std::string` or smart pointers. |
| Address and indirection explained conceptually | Thin: one definition sentence; no example, no memory-model explanation. |
| `nullptr` | Absent ("can be null" only; no `nullptr`, no null-check). |
| Lifetime and dangling | Absent as teaching; "dangling pointers" appears once in a "why it matters" bullet. |
| Link to later dynamic structures | Present as one bullet (linked lists, trees). |
| C++17, executable example | Absent. |
| Accuracy | One misleading claim: "**Both** are essential for dynamic memory management" (references are not used to manage dynamic memory). |

**Other findings.** The first line of the body reads "Pointers and References in C++ Programming Fundamentals" (the Subject was concatenated onto the title), and the pack title became "Pointers and References in C++ Programming", the same Subject-leak pattern as the other Notes. The quiz (5 questions, one multi-select) is correct and answerable; Q5's multi-select (`correctIndices` 0 and 2) matches the Note. It tests recall only.

**What this means (INFERENCE).** This is the one pilot Note whose purpose depends on **scope control**. A title cannot say "teach the memory model, not manual allocation", and **bulk generation has no instruction field** (`BulkGenerateNotesRequest` carries subject, topics, programs, Domain Context, level, collection and section). So for notes whose value is in what they leave out, **title-only bulk generation cannot carry the guardrail**. This is a convention question for D-35, not a reason to change the generator: for these notes the body has to be curator-authored. The authored body already exists (`note-D-pointers-and-references-in-cpp.md`, compiled-checked).

**Updated recommendation status.** Still withheld. The evidence now supports the provisional reading: **SAFE TO SCALE WITH CURATOR CONVENTION**, where the convention must include "scope-sensitive S notes (memory, allocation, linked structures) use curator-authored bodies, not title-only bulk generation." See `r4-pilot-fix-plan.md`.

**Do not begin the next batch.** I have not.
