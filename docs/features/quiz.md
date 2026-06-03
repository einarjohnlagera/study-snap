# quiz.md - NoteLib Feature Context

> **Mode hierarchy and identity contracts** are defined in `docs/product/EXAM_MODES.md`. This file documents shared rules that apply across all quiz modes (ownership, generation lock, leave behavior, learner-level resolution). For per-mode identity (Challenge ≠ Board, Long Exam audience, premium positioning), refer to `EXAM_MODES.md`.

## Goal

Quiz features turn a Study Pack-ready note into active-recall practice without breaking the note-first ownership model.

Shared ownership rule:

- generated quiz content belongs to `noteId`
- Quick Review, Challenge Quiz, Adaptive Practice, Long Exam, and Board Exam sessions are note-scoped

## Current quiz modes

### Quick Review

- lightweight review mode
- available on Free, Plus, and Pro
- uses the base Study Pack quiz
- immediate answer feedback
- one retry round for incorrect questions

### Challenge Quiz

- timed quiz mode
- available on Free, Plus, and Pro with plan-based monthly limits
- generated separately from Quick Review
- uses the shared mode-selection entry

### Board Exam Mode

- strict exam-simulation presentation of the Challenge Quiz engine
- Pro-only at entry
- consumes the shared Challenge Quiz monthly budget and also has a dedicated Board Exam hard cap (`10` source-note units / month by default)
- quota is deducted per source note at session start: 1 unit for a single-note exam, 2 units for a two-note exam, and 3 units for a three-note exam
- generated question count scales with source count: `min(12 * sourceCount, 30)` so single-note stays at 12, two notes generate 24, and three notes cap at 30
- selected from the same mode-selection screen
- keeps separate framing and stricter setup, but still uses note-owned session persistence

### Adaptive Practice

- weak-area follow-up mode
- Plus = 10 sessions / month, Pro = 30 sessions / month (per `PLANS.md`)
- generated separately from Quick Review and Challenge Quiz
- targets weak concepts from the latest Quick Review or Challenge Quiz, plus due concepts from `concept_health`
- due threshold is fixed at 3 days: `last_correct_at` missing or 3+ days old is due
- due concepts are merged ahead of weak concepts at generation time; due-only sessions can generate even when the latest review has no weak concepts
- Key Concepts tab shows due-status badges for Plus/Pro users only, using per-user per-study-pack concept health

### Long Exam Mode

- Student-facing long-form exam mode; identity contract in `docs/product/EXAM_MODES.md`
- Pro-only at launch, using the shared `LONG_EXAM` session discriminator
- quota-limited separately from Challenge Quiz (`12` source-note units / month by default)
- quota is deducted per source note at session start: `additionalStudyPackIds.size() + 1`
- fixed question set generated at start (not progressive)
- prestart supports one primary note plus up to 3 additional same-subject Study Pack-ready notes
- multi-note generation stores source refs in session JSONB and distributes the resolved question count proportionally across sources
- mastery-report result screen includes coverage, weak domains, suggested next step, and source attribution when multiple notes are covered

## Learner-level context

All quiz generation should keep using the existing learner-level system.

Rules:

- learner level remains the primary difficulty and explanation-depth driver
- course/program remains a contextual/domain hint
- Challenge Quiz, Board Exam, and Adaptive Practice resolve Course/Program from the source note first, then fall back to the user's profile Course/Program
- Board Exam Mode keeps its fixed exam-style UX; it should not use progressive generation, but its LLM context still follows the same note-first Course/Program rule
- when learner level is missing, quiz generation falls back to the existing default behavior

Current learner-level adjustment UI:

- Quick Review result page
- Challenge Quiz result page

Current save toast:

- `Learner level updated. Future Study Packs and quizzes will match this level.`

## Result-screen rule

Result screens should guide the next action instead of presenting many equal-weight controls.

Current pattern:

- one dominant primary CTA
- answer review and other controls stay secondary
- `← Back to Note` sits below the action group instead of becoming a primary button

Quick Review:

- `Practice Weak Areas` when weak concepts exist and Adaptive Practice is available
- `Take Another Challenge` after strong/perfect results
- `Practice Again` otherwise

Challenge Quiz:

- `Practice Weak Concepts` when weak concepts exist
- otherwise retry / another challenge becomes the main next action

Adaptive Practice:

- `Generate New Set` remains the primary next action

## Confidence and feedback

Quick Review keeps confidence input as a secondary result-screen action.

Feedback rules:

- result screens may show inline helpfulness / feedback actions
- floating feedback launchers should stay off focused quiz flows

## Question formats

Shared quiz items support these active formats:

- `MCQ` — 4 choices, exactly 1 correct index
- `TRUE_FALSE` — `["True", "False"]`, exactly 1 correct index
- `MULTI_SELECT` — 4 choices, `correctIndices` contains 2–3 correct indexes
- `MATCHING` — 2–4 consecutive single-correct items share the same 4-choice option set through `questionGroup`

True/False rules:

- `TRUE_FALSE` is only for a single declarative statement that the learner judges true or false.
- Any stem that asks the learner to choose — "Which is correct?", "Which of the following...", "Which statement...", "Which one...", or "Which of these..." — must be `MCQ`, not `TRUE_FALSE`.
- Multi-statement assertion items such as `Statement 1: ... Statement 2: ... Which is correct?` must be `MCQ` with four choices: `Both statements are correct`, `Only Statement 1 is correct`, `Only Statement 2 is correct`, and `Neither statement is correct`.
- "All of the following ... except" stems are also MCQ-intent stems and must not use `TRUE_FALSE`.
- Backend generation validation rejects any effectively true/false item whose stem matches those MCQ-intent patterns via `QuizValidationUtils.isFormatStemMismatch(...)`, forcing the existing LLM retry path instead of storing malformed quiz data.

Multi-select rules:

- available on all plans
- allowed in Quick Review, Challenge Quiz, Adaptive Practice, Long Exam, and Teacher Quiz
- not allowed in Board Exam Mode; Board Exam remains single-correct MCQ
- generated at most 1–2 times per quiz batch when a concept has multiple defining attributes, clauses, properties, or category members
- scored all-or-nothing in v1: the selected set must exactly match `correctIndices`; subsets and supersets are incorrect
- v1 does not support 4-of-4 correct or "all of the above"
- `correctIndex` remains populated with the first correct index as a legacy fallback for sharing, export, and review utilities that expect a single answer

Matching rules:

- available on all plans
- allowed in Quick Review, Challenge Quiz, Adaptive Practice, Long Exam, and Teacher Quiz
- not allowed in Board Exam Mode; Board Exam remains single-correct standalone MCQ
- generated at most once per quiz batch when notes contain named items that can be matched to distinct labels, descriptions, laws, steps, or categories
- each matching item uses `questionFormat: "MATCHING"` and the same non-displayed `questionGroup` id, such as `group-1`
- matching groups must be 2–4 consecutive items; non-consecutive items with the same `questionGroup` render as standalone MCQ
- all items in a matching group must have identical `choices` arrays in the same order
- each item in the group keeps its own single `correctIndex`; duplicate correct indexes in a group are invalid
- backend validation demotes malformed matching groups to standalone MCQ by clearing `questionGroup` instead of failing the whole Study Pack
- frontend display renders the shared A/B/C/D option set once above the group, then each item gets its own letter selection row
- scoring is unchanged: each matching item is independently correct or incorrect using exact single-choice selection

Computational working solution rules:

- `COMPUTATIONAL` questions may include `workingSolution` with formula, substitution, and final-result steps
- `workingSolution` math expressions use LaTeX delimiters: inline `$...$` and display `$$...$$`
- the frontend renders `workingSolution` math with KaTeX inside the working-solution panel only
- question text, choices, and explanations remain plain text and do not receive KaTeX rendering
- plain-text working solutions remain supported as a fallback for older generated content

## Admin Repair Tooling

`POST /admin/study-packs/repair-malformed-quizzes` is an admin-only maintenance endpoint for existing stored quizzes with the True/False format mismatch.

Rules:

- scans `study_packs.quiz` across all owners for effectively true/false items whose stems match the same MCQ-intent patterns used by validation
- queues affected packs on `llmParallelTaskExecutor` and returns `{ queued, skipped }` immediately
- regenerates only the `quiz` field through the fixed shared generation and validation path
- does not modify `summary` or `keyConcepts`, preserving summary enrichment and concept-health keys
- skips clean packs idempotently, so re-running the endpoint is safe
- skips packs with an active base Quick Review session because Quick Review uses the stored Study Pack quiz directly rather than snapshotting the quiz at session start
- Challenge Quiz, Adaptive Practice, Long Exam, and Board Exam sessions snapshot generated quiz items in `sessionState`; Teacher `generatedQuiz` creation shares the fixed validation path but is not part of the `study_packs.quiz` repair scope

Concept-level review signals:

- `concept_health` stores `user_id`, `study_pack_id`, `concept`, and `last_correct_at`
- records are written when Adaptive Practice completion includes correctly answered concept names from `QuizItem.concept`
- data is scoped per user per study pack; never mix concept health across notes or Study Packs
- v1 due logic is intentionally lightweight: never reviewed or last correct answer 3+ days ago means due
- Adaptive Practice resolves due concepts from the source Study Pack key concepts and merges them before weak concepts
- Key Concepts tab displays due badges only for users whose plan includes Adaptive Practice

## Generation lock and idempotency

Quick Review:

- does not use the LLM-generation lock flow

Challenge Quiz and Adaptive Practice:

- must reserve and reuse `GENERATING` sessions
- must reuse `IN_PROGRESS` sessions instead of creating duplicates
- may retry only after `FAILED`
- should preserve timing/progress across refresh and recovery

## Leave / forfeit rules

- active quiz sessions show `Leave Quiz`
- app navigation, browser back, and refresh should be guarded while a session is active
- confirmed leaves mark the session `FORFEITED`
- Challenge Quiz and Adaptive Practice forfeits do not refund quota

## Session review

- completed quiz sessions remain note-owned
- Note Detail is the history entry surface
- detailed answer review lives on the dedicated session-review route
- review/export uses stored session data only

## Known generation quality gaps (targeted in v0.17.0)

These are prompt-level defects confirmed in live quizzes. They do not require schema changes and can be deployed as standalone prompt hotfixes before the full v0.17.0 scope ships.

- **Choices in explanation text** — the LLM occasionally includes answer choice letters or full choice text inside the `explanation` field. Triggered most often when the source note itself contains answer choices (e.g., a copied reviewer). Fix: add a generation prompt instruction to explain WHY the correct answer is right without restating or listing the choices.
- **Repeating distractors across questions** — related questions in a quiz can share the same four choices (e.g., four Board Exam questions all using the same four engineering terms). This never occurs in real Philippine licensure MCQ sections. Fix: add a prompt constraint requiring each question to have a fully independent set of distractors.
- **Monotone question framing** — all generated questions default to "Which of the following...?" framing. Real licensure exams vary framing ("All of the following are true EXCEPT...", "Which is NOT correct?"). Fix (v0.17.0 prompt improvement): instruct the LLM to distribute question framing types across the set.

## Planned question format additions (v0.17.0)

Currently all quiz questions use a fixed format: 4 choices, 1 correct index. Planned additions in v0.17.0 require schema and UI changes:

- **Framing variety** (prompt only, no schema change) — NOT/EXCEPT/TRUE framing within the existing 4-choice MCQ
- **Computational questions** — numerical answer choices with step-by-step worked solutions; uses `questionType` plus a `workingSolution` field rendered with KaTeX for LaTeX-formatted math expressions; engineering/sciences notes only
- **True/False 2-choice** — requires variable-length `choices` array or a `questionFormat` discriminator
- **Multi-select** — shipped as `MULTI_SELECT` with `correctIndices` while preserving `correctIndex` as a legacy fallback; all-or-nothing v1 scoring
- **Matching type** — shipped as `MATCHING` with `questionGroup` shared option blocks; each item remains independently scored

Do not implement format additions without a `EXAM_MODES.md` review — they affect scoring logic across all five quiz modes.
