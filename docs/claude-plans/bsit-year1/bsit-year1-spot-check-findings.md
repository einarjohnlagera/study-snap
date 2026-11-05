Status: Spot-check result and owner decision, 2026-11-05. Curriculum review record. NOT implementation-ready. Edits to production are prepared as SQL for the owner; none has been made by Claude.

# BSIT Year 1: spot-check of the metadata-ready Notes (35 of 176)

## What was done
- A text search over all 176 metadata-ready bodies: no "computer science student/major/program", "BSCS" or teacher framing; no C++ in any of the 31 language-neutral programming Notes; Python, Java or JavaScript appear in 6 Notes only as illustrative examples.
- A risk-weighted sample of 35 (12 Notes using asymptotic notation, 6 C++-specific, 6 computing-flavoured Mathematics in the Modern World, 7 computing-audience Introduction to Computing, 4 GE Notes touching technology or workplace writing), reviewed blind by GPT. Result: `bsit-year1-spot-check-manifest-35-GPT-BLIND-RESULT.tsv` (33 PASS, 2 HOLD). Pack: `bsit-year1-spot-check-pack-35-of-176-BLIND.md`.
- GPT's reasoning was specific on the 12 notation-related rows and identical boilerplate on the 22 others; treat the 22 as weak evidence. It also said "Big-O is not defined" on several Notes without noticing the upstream definition below.

## Finding: Theta is used but never defined
Checked across all 205 Note bodies (176 metadata-ready + 29 Discrete Mathematics):
- **Big-O** is defined once, in "Algorithms and Their Properties" (Computer Programming 1, early in the plan). Notes that use only Big-O come after it and are covered upstream.
- **Theta (a tight bound) is defined nowhere.** Four Notes use it: Nested Loops (quiz Q3), Recursive Problem Solving (Q4), Sequences and Summations (Discrete Mathematics, Q5) and Elementary Sorting (body only). The two HOLDs and the earlier Discrete Mathematics Note 22 concern are the same gap.
- The same Note also defines "precondition and postcondition", which resolves the earlier worry that the Discrete Mathematics quizzes used undefined vocabulary: the term is taught upstream in Computer Programming 1.

## Owner decision (2026-11-05): option b
Add one Quick Recall bullet defining Theta to "Algorithms and Their Properties" (id `583c82dc-fc5b-4088-ae21-dd474a787be3`). Prepared as `bsit-year1-theta-definition-note-edit.sql`: guarded by id, title, Domain Context, md5 of the current body and the exact updated_at; appends one bullet; no Study Pack regeneration; rollback included; verified read-only against production that the guard matches and that the rollback restores md5 `023dd664…`. **Not run.** It is a canonical Note shared with BSCS, so the change affects both journeys (it only adds a definition).

## Consequences once the SQL is run
- The Theta gap closes for all four Notes. The two GPT HOLDs (Nested Loops, Recursive Problem Solving) become PASS on this ground; no tag or plan change is needed because they were already in the plan.
- Remaining open from the spot-check: none. The 141 unsampled Notes are covered only by the text screen.
- The note-level limitation recorded for Sequences and Summations (Discrete Mathematics) is closed by the same edit.
