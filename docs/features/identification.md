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

### The answer may never be a symbolic form (`v0.71.0`)

**An Identification answer must be something a human writes exactly one way.** Scoring is exact normalized string equality — no maths-, chemistry-, or code-aware comparison exists anywhere in the path — so any answer with several equally correct notations is **ungradeable by construction**, not merely hard to grade.

The question that forced this rule shipped as: *"Identify the algebraic expression for the sum of the squares of two variables $x$ and $y$."* with `acceptableAnswers` of `["sum of squares", "x squared plus y squared", ...]`. A learner typing `x^2 + y^2` — the answer the stem asks for, and the one the item's *own explanation* gives — was marked wrong, while echoing the stem back in words was marked right. `x^2 + y^2`, `x² + y²` and `y² + x²` are all correct and none match as text.

Two independent generations produced the identical question for different users from different study packs, and a survey found **no counter-example: every Identification question in local data was defective**. That is why this is stated as a construction rule rather than a prompt nicety.

- **The rule is stated for every subject, not for mathematics** — chemical formulae, code expressions, and logical notation fail the same way. It is written against the *form of the answer*, never against the note's subject.
- **The valid case stays explicit:** a formula's *name* is a fine Identification answer; the formula *itself* is not.
- **Restating the stem as the answer is forbidden**, which is the failure the defective item rewarded.
- **A deterministic guard backs the prompt**, because prompt compliance is not testable and this is: `QuizValidationUtils.isFormatStemMismatch` rejects the item when the model ignores the instruction. It runs in `OpenAiLlmStudyPackService` validation and `AdminStudyPackTransactionHelper`, so it covers **every** generation path app-wide, not just the one where the defect was observed. False-positive tests cover legitimate name/term identification, since over-rejecting would silently delete a valid format.
- **Pre-existing rows were not migrated.** This prevents new defective items; it does not repair old ones.

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
