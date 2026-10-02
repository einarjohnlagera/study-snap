# R4 Pilot — Fix Plan (2026-10-01) — PLAN ONLY, NOTHING IMPLEMENTED

Source: `r4-pilot-report.md`. Written for the Release Implementor. Claude has changed nothing in production or in code.

## 0. Read this first: who can execute which part

`CLAUDE.md`: **only the owner executes production writes**, and the rule binds every route, including another Claude session and any admin surface. Every item in Part 1 and Part 2 edits production data (Note metadata, Note content, Study Packs), so **the Release Implementor can prepare, script, review and verify, but the owner performs the writes in the production UI.** Part 3 is code and belongs in a normal branch and release. Part 4 is read-only verification.

## 1. Metadata corrections (owner, in the production UI)

All five Notes carry Applicable Programs *Computer Engineering, Computer Science, Information Systems, Information Technology, Software Engineering*. The shaping TSV (`bscs-year1-shaping.tsv`) is the source of truth.

| Note | Id | Remove | Target Applicable Programs |
|---|---|---|---|
| A The Computing Disciplines: … | `9b81844a-209c-426a-89e9-0f110620b9f8` | Computer Engineering, Software Engineering | Computer Science, Information Technology, Information Systems |
| B Algorithms and Their Properties | `583c82dc-fc5b-4088-ae21-dd474a787be3` | Computer Engineering, Software Engineering | Computer Science, Information Technology, Information Systems |
| C Building and Running a C++ Program | `bb34133d-7427-46ca-a8de-b0364cc2236a` | Computer Engineering, Software Engineering | Computer Science, Information Technology, Information Systems |
| D Pointers and References in C++ | `f79dc811-f532-453e-a68f-f0f96f3f209c` | Computer Engineering, Software Engineering | Computer Science, Information Technology, Information Systems |
| E Propositions and Logical Connectives | `edeb5f50-6a28-4dd9-a622-8682d15cdae4` | Computer Engineering, Information Systems, Information Technology, Software Engineering | **Computer Science only** |

**Do not** change Domain Context, Subject or Authored Depth: they are correct on all five. **Origin of the fault: Applicable Programs, set at creation.** First find out how the five-program set got there (a UI default, a copied selection, or a manual choice), because the same cause would repeat on every note in the corpus. If it was a UI default or a remembered selection, that is a product finding to record, not just a data fix.

## 2. Content decision, then content edits (owner decides; owner executes)

**Decision needed first (owner): which content path does each Note use?** You said you usually use Bulk Generate for the notes a collection needs. That path wrote all five Notes as ~2,000-character outlines. Authored bodies already exist in this folder for all five.

My recommendation, labelled as a PROPOSAL:

| Note | Recommendation | Why |
|---|---|---|
| **D** Pointers and References in C++ | **Use the authored body** | Its value is in what it leaves out. A title cannot say "teach the memory model, not manual allocation", and Bulk Generate has no instruction field. The generated Note fails the D-27 guardrail (it teaches `new`/`delete`, calls raw pointers essential, omits `nullptr` and lifetime). |
| **C** Building and Running a C++ Program | **Use the authored body** | The generated outline never says C++17, gives no executable example and no edit-compile-run cycle; its pack also renders shell commands as math. |
| **B** Algorithms and Their Properties | Either; **authored preferred** | The generated outline conflates efficiency with the defining properties and omits effectiveness, and its quiz asserts a wrong claim about randomness. Neutral content itself was fine. |
| **A** Computing Disciplines, **E** Propositions | Either | The generated outlines are accurate but shallow; the choice is a depth decision (outline versus course-quality). |

**If the owner chooses the authored path for a Note:** (1) replace the Note body with the file in this folder; (2) generate a fresh Study Pack once, with explicit confirmation as the versioning rule requires (the pack updates in place and keeps history); (3) do not edit the generated pack. **If the owner keeps a generated body:** the Note B quiz question on randomness must be removed or regenerated, and Note C needs the C++17 detail added.

## 3. Code and generation items (separate branch and release; NOT part of this fix; none is authorized by D-35)

These are findings, not requests. Each needs evidence at scale and an owner decision before becoming scope. The owner instruction for this pilot was to measure, not change.

| # | Finding | Origin | Observed in |
|---|---|---|---|
| G1 | Shell commands rendered as LaTeX math in a Summary (`$g++\ program.cpp\ -o\ program$`) | Study Pack generation | Note C |
| G2 | Subject appended to the Note body's first line and to pack titles ("… in Programming Fundamentals") | generation behaviour | all five |
| G3 | A quiz explanation asserts an unsupported claim (randomness) | Study Pack generation | Note B |
| G4 | Computation guidance depends on auto-generated tags when the Subject has no keyword | generation prompt behaviour (latent) | Note B fired only via the tag "Algorithms" |
| G5 | No log records the authoring domain or whether guidance fired; `study_packs` stores no prompt | observability | all |
| G6 | Bulk Generate cannot carry scope constraints (no instruction field) | product capability | Note D |

G6 is a **convention** question under D-35 ("no new product field is authorized"), so the plan above handles it by choosing the authored path for scope-sensitive Notes rather than adding a field.

## 4. Verification after the owner's changes (read-only; Claude runs on "fixes applied")

1. The five Notes: exact Applicable Programs, Domain Context, Subject, Authored Depth, visibility, content length, one Note per title.
2. For each Note whose body was replaced: a new Study Pack and its text.
3. **Re-score Notes D and C against the guardrail and C++17 requirements** (the two failures that block scale), and Note B for the randomness claim.
4. Confirm no other note was touched: compare Note counts and recent `updated_at` for the owner's notes.

## 5. Proposed convention for the rest of the corpus (for owner approval; informs how you use Bulk Generate)

- **Bulk Generate is acceptable** for Notes whose title fully specifies the knowledge (overview and definitional Notes, most General Education additions) *if* a curator reviews each Study Pack for unsupported claims and checks metadata at creation.
- **Curator-authored bodies** for **scope-sensitive** Notes: the Programming 2 memory section (Pointers and References, Dynamic Memory Allocation, Common Memory Errors, Linked List Operations in C++), the Programming 1 build and testing S Notes, and anything where the D-27 guardrail or the C++17 subset must be enforced.
- **Metadata check at creation for every Note** (Applicable Programs especially).

## 6. What I did not do

No production write, no code change, no new Note, no regeneration. I have not messaged the Release Implementor; say the word and I will send it this plan.
