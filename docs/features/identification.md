# identification.md - NoteLib Feature Context

## Goal

Identification is a free-text question format inside Challenge Quiz.

It gives learners a more active-recall review path than multiple choice while keeping the existing Challenge Quiz session, scoring, and mastery flow.

## Availability

- available on Free, Plus, and Pro
- ungated; no `FeatureGateService` check
- Challenge Quiz only for now
- private authenticated Study Pack / Note Challenge Quiz flow

There is no question-format picker. Identification appears automatically in Challenge Quiz's generated mix when a concept has one precise, nameable answer.

## Entry Point

Learners enter through the existing Challenge Quiz flow:

Study Pack-ready note
-> Challenge Quiz
-> generated question mix
-> Identification question
-> type answer
-> complete Challenge Quiz
-> result screen, concept breakdown, weak concepts, and answer review

Identification is not a new quiz mode and does not add a new `quizSession` discriminator.

## Data Source And Generation Rule

Challenge Quiz generation may emit `questionFormat: "IDENTIFICATION"` when the answer is a focused term, law, formula name, component, method, person, place, or similar precise value.

Identification items use:

- `choices: []`
- `answer: null`
- `correctIndices: null`
- `acceptableAnswers: string[]`

`acceptableAnswers` should contain 2-3 short synonyms or answer variants, with the primary answer first. Concepts that require a vague explanation, a multi-part response, or subjective judgment should stay as another question format.

## Scoring

Identification is scored deterministically.

The submitted text and each acceptable answer are normalized by:

- trimming leading/trailing whitespace
- lowercasing
- collapsing internal whitespace

If the normalized submitted answer equals any normalized acceptable answer, the question is correct.

There is no per-submission LLM call, semantic grading call, or AI repair flow.

Blank or whitespace-only answers are treated as unanswered and cannot score correct. If an Identification item has no acceptable answers, it safely scores as incorrect instead of crashing.

## ConceptHealth Behavior

Identification writes `ConceptHealth` through the existing Challenge Quiz completion path.

Fully correct concepts are recorded as correct answers, and concepts with misses are recorded as incorrect answers, matching MCQ / TRUE_FALSE / MULTI_SELECT / MATCHING behavior.

## Session State

Typed answers persist in the existing session JSONB state under `selectedIdentificationAnswers`, keyed by question index.

The existing Challenge Quiz progress endpoint saves this map; no new endpoint is introduced.

## Out Of Scope

- Enumeration
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
