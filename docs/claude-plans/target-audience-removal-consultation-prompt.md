# Consultation prompt — second opinion on removing the Target Audience metadata axis

**Send to GPT. Companion to `target-audience-removal-proposal.md`.** The reviewer has no repository access, so everything needed is restated below. **Nothing has been implemented.**

---

## Your task

We are considering permanently removing a note metadata field from a production learning product. An internal audit concluded it is safe to remove. **We want you to try to find the reason that conclusion is wrong.** Please do not simply validate it.

We are specifically not interested in hypothetical future features. We want real architectural, product, or data dependencies — or a demonstration that our evidence does not support our conclusion.

## The product

**NoteLib** — a notes-first study workspace for Philippine learners, heavily weighted toward licensure/board-exam review (nursing, accountancy, education, the engineering disciplines) plus some general students. Users write or adopt notes; an LLM generates a "Study Pack" (summary + quizzes) from each note; learners practise against those quizzes.

## The field under review

**Target Audience** — `enum { STUDENT, BOARD_TAKER, PROFESSIONAL }`, stored per note.

It originally existed to (1) categorise notes by audience and (2) prevent learners from seeing notes intended for a different audience.

## The current metadata model

A note today carries five axes:

| Axis | Responsibility |
|---|---|
| **Subject** | What the note is about |
| **Course / Program(s)** | Who should discover it (Nursing, Accountancy, Civil Engineering…) — many-to-many |
| **Domain Context** | Which academic domain the LLM authors within — the *only* domain signal reaching a prompt |
| **Authored Depth** | How deeply to write it — `GRADE_SCHOOL, JUNIOR_HIGH, SENIOR_HIGH, COLLEGE, BOARD_EXAM_REVIEW, PROFESSIONAL, PERSONAL_LEARNING` |
| **Target Audience** | The field under review |

## Audit findings (all verified directly in the codebase)

1. **Not used in AI generation at all.** Absent from every prompt template and from the generation-context resolver. It does not influence Study Pack, quiz, explanation, or adaptive generation.
2. **Its access-control purpose was never implemented.** The public-library audience filter defaults to "All" and nothing anywhere restricts note visibility by audience. Purpose (2) above does not exist in code.
3. **It IS still a live discovery filter** — a WHERE clause in the public library query, user-facing filter chips, and a shareable `?audience=` URL parameter. Removing it is a user-visible change.
4. **It is a curator authoring field** and is displayed on private note detail. Onboarding sets it from the learner's profile type.
5. No permissions, analytics, reporting, progress, notification, or recommendation dependency was found.

## Production data

**Coverage:** 5,587 notes carry a Target Audience. **5,538 of them (99.1%) have NO Authored Depth** — Authored Depth is optional and most notes inherit it through a fallback chain instead of storing their own.

**Public notes with a Target Audience — 945 total: 823 `BOARD_TAKER`, 122 `STUDENT`, 0 `PROFESSIONAL`.**

Distribution by Course / Program:

| Program group | BOARD_TAKER | STUDENT |
|---|---|---|
| Licensure programs (Civil Engineering 254, Accountancy 153, Education 146, Nursing 131, Architecture 90, ~10 further engineering fields, Pharmacy, Civil Service) | ~1,073 | ~7 |
| Values that are actually academic *levels* (Junior High 24, High School 9, Senior High strands 10, Grade School 3) | 1 | 43 |
| **Information Technology** | **9** | **63** |

## Two arguments we considered

**Argument A — "Authored Depth subsumes it."** The enums map cleanly: `BOARD_TAKER → BOARD_EXAM_REVIEW`, `PROFESSIONAL → PROFESSIONAL`, `STUDENT →` the four school levels. Depth is a strict refinement.

**We rejected A**, because Depth is null on 99.1% of notes. Removing Audience on this basis would delete the populated axis and leave the superior one empty.

**Argument B — "Course / Program predicts it."** Outside Information Technology, audience is ~99.3% predictable from program: licensure programs are uniformly `BOARD_TAKER`, academic-level values uniformly `STUDENT`, and `PROFESSIONAL` is entirely unused.

**We accepted B** and concluded the axis carries essentially no independent information.

## What we believe we lose

- A working cross-program filter (945 public notes → 122 for "Student"). Course / Program cannot express "student-level content across several programs." The axis that *should* serve this is Authored Depth, which is unpopulated.
- Shareable `?audience=` URLs degrade to unfiltered views.
- After the column is dropped, audience becomes inferable-but-not-recorded.

## Questions we want you to answer

1. **Is Argument B sound, or are we confusing correlation with redundancy?** Program predicts audience today because our content is overwhelmingly board-exam material. Does that generalise, or are we removing an axis that would start carrying information the moment the content mix changes — for example if we grew general-student or professional/CPD content?
2. **`PROFESSIONAL` is unused across all 945 public notes.** Is "an enum value nobody uses" evidence the axis is redundant, or evidence of an unserved segment we would be permanently designing away?
3. **Information Technology is the one program with a real mix (9 board / 63 student).** We assumed mis-tagging because IT has no Philippine licensure board. Is there a reading where that mix is the *correct* behaviour and the uniform programs are the anomaly?
4. **We rejected Argument A on data (Depth is 99% null).** Is the better move to *populate Depth first* and remove Audience afterwards, rather than removing now and relying on program inference?
5. **Sequencing:** is there a defensible order other than ours (amend the architecture decision → remove discovery surface → remove authoring → drop columns), given we want each step revertible and the irreversible one last?
6. **What are we not asking?** If you see a dependency, a migration hazard, or a product consequence our audit missed, that is the most valuable thing you can return.

## What a useful answer looks like

A clear verdict (proceed / proceed with changes / do not proceed), the single strongest argument *against* our conclusion even if you ultimately agree with it, and any question above where you think our framing is wrong. Concrete reasoning over reassurance.
