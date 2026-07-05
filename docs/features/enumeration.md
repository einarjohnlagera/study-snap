# enumeration.md - NoteLib Feature Context

## Goal

Enumeration is a free-text question format inside Challenge Quiz, alongside Identification.

It asks learners to name every item in a well-defined, countable set (e.g. "Name the three branches of government"), giving a richer active-recall check than a single-blank Identification question while keeping the existing Challenge Quiz session, scoring, and mastery flow.

## Availability

- available on Free, Plus, and Pro
- ungated; no `FeatureGateService` check
- Challenge Quiz only for now
- private authenticated Study Pack / Note Challenge Quiz flow

There is no question-format picker. Enumeration appears automatically in Challenge Quiz's generated mix when a concept has a well-defined, countable set of 2-5 distinct named items.

## Entry Point

Learners enter through the existing Challenge Quiz flow:

Study Pack-ready note
-> Challenge Quiz
-> generated question mix
-> Enumeration question
-> type N answers (N = required item count for that question)
-> complete Challenge Quiz
-> result screen, concept breakdown, weak concepts, and answer review

Enumeration is not a new quiz mode and does not add a new `quizSession` discriminator.

## Data Source And Generation Rule

Challenge Quiz generation may emit `questionFormat: "ENUMERATION"` when a concept has a well-defined, countable set of 2-5 distinct named items (branches, steps, types, categories, causes, stages, parts) that are each independently nameable and unambiguous. It is not used when items overlap, when order matters, or when there are more than 5 required items.

Enumeration items use:

- `choices: []`
- `answer: null`
- `correctIndices: null`
- `acceptableAnswers: null`
- `acceptableAnswerGroups: string[][]`

`acceptableAnswerGroups` holds one array per required item (2-5 arrays total). Each inner array holds 1-2 short synonym/variant strings for that one required item, with the primary answer first. The number of inner arrays **is** the required answer count — there is no separate count field.

## Scoring

Enumeration is scored **all-or-nothing** — every required item must be matched for the question to count correct. There is no partial credit.

Matching is order-independent: the learner does not need to enter items in any particular sequence. Scoring uses exhaustive bipartite matching between the submitted answers and the required groups (backend: `QuizSessionReviewUtils.isAnswerCorrect`; frontend preview: `isEnumerationAnswerCorrect` in `lib/quiz.ts`) rather than a naive first-match-greedy assignment, so a correct answer is never wrongly rejected when synonym groups overlap (e.g. group A = {"x","y"}, group B = {"x"}, submitted ["x","y"] correctly matches via x->B, y->A even though a greedy x->A assignment would dead-end).

The submitted text and each acceptable answer are normalized by:

- trimming leading/trailing whitespace
- lowercasing
- collapsing internal whitespace

There is no per-submission LLM call, semantic grading call, or AI repair flow.

Blank slots, a submitted-answer count that doesn't match the required count, or a partially-correct set of answers all score the question incorrect — not a crash, and not partial credit. If an Enumeration item has no acceptable answer groups, it safely scores as incorrect instead of crashing.

## ConceptHealth Behavior

Enumeration writes `ConceptHealth` through the existing Challenge Quiz completion path.

Fully correct concepts are recorded as correct answers, and concepts with misses are recorded as incorrect answers, matching MCQ / TRUE_FALSE / MULTI_SELECT / MATCHING / IDENTIFICATION behavior.

## Session State

Typed answers persist in the existing session JSONB state under `selectedEnumerationAnswers`, keyed by question index. Each value is a fixed-length array of length N (N = required item count), using `""` for any box left blank, so a partially-filled question round-trips correctly across a session reload.

The existing Challenge Quiz progress endpoint saves this map; no new endpoint is introduced.

## Out Of Scope

- partial credit / per-item scoring
- Long Exam
- Board Exam
- Quick Review
- Adaptive Practice
- question-type picker UI
- Plus/Pro-only gating
- new plan entry
- new quiz mode
- new session discriminator
- per-submission LLM grading
- user-configurable required item count
