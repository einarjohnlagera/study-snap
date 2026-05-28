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
- consumes the shared Challenge Quiz monthly budget and also has a dedicated Board Exam hard cap (`5` / month by default)
- selected from the same mode-selection screen
- keeps separate framing and stricter setup, but still uses note-owned session persistence

### Adaptive Practice

- weak-area follow-up mode
- Plus = 10 sessions / month, Pro = 30 sessions / month (per `PLANS.md`)
- generated separately from Quick Review and Challenge Quiz

### Long Exam Mode

- Student-facing long-form exam mode; identity contract in `docs/product/EXAM_MODES.md`
- Pro-only at launch, using the shared `LONG_EXAM` session discriminator
- quota-limited separately from Challenge Quiz (`10` sessions / month by default)
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
