# quiz.md - NoteLib Feature Context

> **Mode hierarchy and identity contracts** are defined in `docs/product/EXAM_MODES.md`. This file documents shared rules that apply across all quiz modes (ownership, generation lock, leave behavior, learner-level resolution). For per-mode identity (Challenge ≠ Board, Long Exam audience, premium positioning), refer to `EXAM_MODES.md`.

## Goal

Quiz features turn a Study Pack-ready note into active-recall practice without breaking the note-first ownership model.

Shared ownership rule:

- generated quiz content belongs to `noteId`
- Quick Review, Challenge Quiz, Adaptive Practice, Long Exam, and Board Exam sessions are note-scoped

## Shared Quick Review mastery predicate

Study Pack Quick Review mastery has one server-owned definition: for a `(user, Study Pack)`, there must be a completed `QUICK_REVIEW` session whose server-derived `verifiedCorrectAnswers` equals the Study Pack's current quiz size, and that size must be greater than zero. The verified score comes from persisted cumulative selections, so a perfect result reached through `Redo Mistakes` qualifies. Client-reported totals are not part of the predicate, other quiz modes cannot confer it, and a copied Study Pack starts with no mastery for its new owner.

Regeneration compares historical sessions with the current quiz size. A quiz-size change may therefore remove mastery until the learner completes the new question set perfectly.

**`verifiedCorrectAnswers` is not uniformly server-derived, and code must not assume it is.** Sessions completed **after** the `v0.74.0` migration are server-derived. Sessions completed **before** it were **grandfathered from the client-reported `correct_answers`**, because re-scoring in SQL would mean re-implementing answer resolution against raw JSONB and bypassing `QuizItem`'s `@JsonCreator` — where `correctIndex` is actually resolved, including the answer-as-letter case that generated quizzes rely on (`correctIndex` is absent from `schema.json`; `"answer"` is a letter per `developer.txt:15`). Getting that wrong locks existing learners out of a tab they already use, so the pre-deploy population is trusted once instead. **Do not "fix" this by adding a SQL scorer** — any re-derivation must go through `QuizItem`.

The v0.74.0 Quiz-tab lock built on this signal is a **UX affordance, not a security control**. Quick Review scores in the client, and the saved Study Pack quiz—including its answers—is already present in the client payload. Server-derived verification removes accidental divergence between the progression gate and the persisted-selection evaluation used by the completion and `ConceptHealth` path; it does not make the gate tamper-proof, and v0.74.0 does not claim that it does.

## Note Detail Quiz-tab progression lock

- On private Note Detail, the Quiz tab stays visible, clickable, and keyboard reachable before mastery. Selecting or deep-linking to it renders an instructional lock panel instead of mounting the answer-revealing practice quiz.
- The panel names the actual perfect-score condition using the Study Pack's current question count, falls back to length-agnostic wording when that count is unavailable, and explains that `Redo Mistakes` can still produce the qualifying perfect score.
- The panel starts the note's existing Quick Review flow and can return the learner to Summary. Challenge Quiz remains available independently and is not gated or reordered.
- Teachers and admins are curator-exempt and may inspect the saved quiz without mastery. Their bypass does not emit an unlock-open event.
- For an unlocked non-curator, opening the tab emits `STUDY_PACK_QUIZ_TAB_OPENED_AFTER_UNLOCK` once for that tab open; locked and empty-quiz views do not emit it.
- This lock covers **only private Note Detail**. The public share page and Study Pack generation-results view continue to reveal saved answers deliberately. Those accepted exceptions, plus the answers already present in the client payload, are why this remains a UX progression affordance rather than a security boundary.

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
- Pro-only at Start CTA; Free and Plus users may open the setup screen before the upgrade ask
- consumes the shared Challenge Quiz monthly budget and also has a dedicated Board Exam hard cap (`10` sessions / month by default)
- quota is deducted per session at exam start: 1 unit per Board Exam regardless of note count; the source count still drives question-count scaling and multi-note generation
- generated question count scales with source count: `min(12 * sourceCount, 30)` so single-note stays at 12, two notes generate 24, and three notes cap at 30
- selected from the same mode-selection screen
- Board Exam can also launch from a Study Plan / Review Set through `collectionId`; the setup opens directly, additional Study Pack choices are restricted to quiz-ready notes in that plan, and the existing 2-additional-note cap is unchanged
- keeps separate framing and stricter setup, but still uses note-owned session persistence

### Adaptive Practice

- weak-area follow-up mode
- Free = 3 sessions / month, Plus = 10 sessions / month, Pro = 30 sessions / month (per `PLANS.md`)
- generated separately from Quick Review and Challenge Quiz
- targets weak concepts from the latest Quick Review or Challenge Quiz, plus due concepts from `concept_health`
- due threshold is fixed at 3 days: `last_correct_at` missing or 3+ days old is due
- due concepts are merged ahead of weak concepts at generation time; due-only sessions can generate even when the latest review has no weak concepts
- Key Concepts tab shows due/mastery status badges for every plan using per-user, per-Study-Pack concept health; detailed elapsed-time timing and struggling-concept copy remain on the existing paid timing path

### Long Exam Mode

- Student-facing long-form exam mode; identity contract in `docs/product/EXAM_MODES.md`
- Pro-only at Start CTA, using the shared `LONG_EXAM` session discriminator; Free and Plus users may open the prestart setup before the upgrade ask
- quota-limited separately from Challenge Quiz (`12` sessions / month by default)
- quota is deducted per session at exam start: 1 unit per Long Exam regardless of note count; the source count still drives source refs, question distribution, and multi-note generation
- fixed question set generated at start (not progressive)
- prestart supports one primary note plus up to 3 additional same-subject Study Pack-ready notes
- Long Exam can also launch from a Study Plan through `collectionId`; plan launch replaces the same-subject default picker with quiz-ready notes from that plan only and pre-selects up to the existing 3-additional-note cap
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

Result screens should guide the next action through the shared ConceptHealth-driven post-session handoff instead of each mode computing bespoke CTAs from the current session only.

Shared pattern:

- complete the session first
- fetch `GET /study-packs/{studyPackId}/next-step`
- render the shared `<PostSessionNextStep>` component when a response is available
- keep existing fallback CTA UI available when the fetch fails or returns no usable response
- answer review and other controls stay secondary
- `← Back to Note` sits below the action group instead of becoming a primary button
- Weak/missed concepts on the Quick Review, Adaptive Practice, Challenge Quiz, and Board Exam Mode result screens link to the matching source-note Key Concepts explanation when the trimmed, case-insensitive concept key exists there. The link preserves the existing Note Detail `?tab=key-concepts` route and anchors to that concept; unmatched strings remain plain text rather than linking to a missing explanation.
- Long Exam and Interview Practice result/report screens are not part of this weak-concept deep-link behavior.

Server resolution priority:

1. `PRACTICE_WEAK_CONCEPT` — due concepts from `ConceptHealthService.getDueConcepts(...)` route to Adaptive Practice. After Challenge Quiz, eligible banked misses can appear as the secondary `Redo Missed Questions` action.
2. `REDO_MISSED_QUESTIONS` — when the latest completed session is Challenge Quiz, no genuine weak concepts remain, and at least three owned bank questions at the note's effective curriculum level have last outcome `INCORRECT`; starts an LLM-free, quota-exempt ordinary Challenge session from those questions. Availability counting and claiming use the same resolver-derived level.
3. `RETRY_REVIEW` — only when no concepts are due, the latest completed session has weak concepts, and the completed mode is Quick Review. Its `Retry Incorrect Questions` label and plain Quick Review route are unchanged; it is distinct from Challenge Quiz's `Redo Missed Questions`.
4. `REVIEW_PACK` — no due concepts and nothing retryable; route to Challenge Quiz or the source note.

Quota rule:

- when the server recommends `PRACTICE_WEAK_CONCEPT` and the learner has exhausted Adaptive Practice quota, `<PostSessionNextStep>` shows the targeted concepts plus the plan-aware upgrade CTA instead of routing into a limit wall

## Due-concepts digest return path

The due-concepts digest is a trigger into the existing Quick Review mode, not a separate cross-note session or sixth quiz mode. On a dispatch day matching the learner's chosen review days (the digest now dispatches daily; see `retention-emails.md`), the backend selects the owned note whose Study Pack has the most due concepts and links to `/notes/{noteId}/quick-review?source=due-concepts-digest`. If that note cannot be resolved, the link falls back to `/dashboard`; if no concepts are due, no email is sent.

Configured review weekdays are matched in `Asia/Manila`, the retention email budget zone. Null or empty review days preserve the pre-v0.72.0 digest cadence. The Quick Review page records the digest landing and the first submitted answer once each so trigger-to-answer conversion can be measured without changing scoring, ConceptHealth, or readiness.

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

IDENTIFICATION rules — **the answer's form, not the subject, decides validity**:

- IDENTIFICATION answers are graded by **exact normalized string equality** (trim, collapse whitespace, lowercase). There is no maths-, chemistry- or code-aware comparison anywhere in the grader.
- So the answer must be a **term, name, or label a learner can type unambiguously in words**. `MCQ` is the correct format whenever the answer is a **symbolic or notational form** — a mathematical expression or equation, a chemical formula, a code snippet, or a value with units — because those have many equally-correct renderings (`x^2 + y^2`, `x² + y²`, `y² + x²`) and none of them string-match each other.
- **A formula's *name* is a valid answer; the formula *itself* is not.** "Which law states that force equals mass times acceleration?" → `Newton's Second Law` is valid. "Identify the equation for Newton's Second Law" → `F = ma` is not.
- This holds for **every subject**. "Identify the chemical formula for water" and "Identify the expression that reverses a list" are as invalid as the algebraic case.
- `acceptableAnswers[0]` must be the exact thing the stem asks for. **Restating the stem is not an answer** — for "Identify the algebraic expression for the sum of the squares of x and y", `"sum of squares"` merely repeats the question.
- Enforced in two places, deliberately: the Challenge Quiz prompt states the rule, and `QuizValidationUtils.isFormatStemMismatch` rejects the question when the model ignores it. The guard exists because prompt compliance cannot be tested deterministically — this defect shipped, and two independent generations produced the identical bad question.

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
- **Retired in `v0.71.0`:** these two lines used to read *"KaTeX inside the working-solution panel only"* and *"question text, choices, and explanations remain plain text"*. Both are false now — question text, choices, and explanations all render math. See "Inline math in question text" below for the current rule and the full surface list; do not restore the panel-only claim.
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

- `concept_health` stores `user_id`, `study_pack_id`, `concept`, `last_correct_at`, `last_incorrect_at`, and `incorrect_streak`
- Quick Review, Challenge Quiz, and Adaptive Practice completion write correct and missed concepts from their persisted quiz selections; a concept is correct only when every question for that concept is answered correctly
- `incorrect_streak` increments when a missed concept is recorded and resets to `0` when that concept is recorded correct; the scoped result flows return a concept once its updated streak reaches `2` so Ask Companion can own the "I still don't get it" path
- data is scoped per user per study pack; never mix concept health across notes or Study Packs
- v1 due logic is intentionally lightweight: never reviewed or last correct answer 3+ days ago means due
- Adaptive Practice resolves due concepts from the source Study Pack key concepts and merges them before weak concepts
- Key Concepts tab displays due/mastery badges for every plan; this signal never grants Adaptive Practice access

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

## Math rendering in questions and options

Generated questions can contain inline LaTeX — the model emits `\( ... \)` for algebraic prompts, so a Quick Review question would otherwise read literally as `simplify \(\frac{x^3 - 4x^2 + 5x}{x - 2}\)?`.

- `QuizQuestionText` and `QuizChoiceList` render through `renderMathText` (`quiz-working-solution.tsx`), the same KaTeX-backed renderer working solutions already used. It had only ever been wired to working solutions.
- **Every surface that shows a question, an option, or an explanation must go through it.** Live session pages (Quick Review, Challenge Quiz, Adaptive Practice, Long Exam, shared quiz) reach it via `QuizQuestionText`, but the **result and preview** surfaces render question text directly and were missed on the first pass: `practice-quiz-card.tsx`, `generated-quiz-preview-page-client.tsx`, and `quiz-matching-group.tsx`. **Explanations carry math too** and were raw on every surface. When adding a new quiz surface, route question, option, and explanation text through `renderMathText` — a raw `{item.question}` is the bug.
- **The sweep was completed in `v0.71.0`, and the rule above was false until then** — the first pass fixed the components it happened to have open rather than a mapped inventory. Now covered: the shared `quiz-answer-review` **explanation** (one component behind five call sites — quick-review, adaptive-practice, challenge-quiz ×2, session-history review, where the question above it rendered correctly so it read as a rendering bug); **options** on the unauthenticated `/quiz/[token]` page; all four Interview Practice surfaces (question, options, critique rationale, follow-up); the generated-quiz preview's Correct Answer echo; the matching-group **option bank**; Identification and Enumeration "Accepted answer(s)"; the public mini-quiz preview (question, option, explanation — an unauthenticated SEO surface); the onboarding quiz preview; and both demo pages. **The way to extend this is a grep, not a walk through the files you have open.**
- **The "cards, not quiz content" exclusion written here was factually wrong and is retired (`v0.71.0` signoff).** It claimed flashcards and memorization were out of scope because they render cards rather than quiz content. They do not: `buildFlashcardDeck` reads `item.question` and `item.explanation` **straight off the quiz array**, so those surfaces — plus the *unauthenticated* public preview (`public-flashcards-preview.tsx`, `public-mini-quiz-preview.tsx`) — were showing raw `\frac{...}` to readers. All now route through `renderMathText`. **The lesson worth keeping: derive the scope boundary from where the content comes from, not from what the surface is called.** This claim was falsified three times before it was checked against `buildFlashcardDeck`.
- **Still out of scope, and for a reason that survives that check:** learn articles, the Ask Companion guide FAQ (`collection-detail-page-client.tsx`), and the landing-page FAQ. These are authored prose that never touches the quiz array. Whether they *should* render math is a separate product decision.
- **A lone `$` is currency, not math.** `renderMathText` treats `$` as opening a span only when the next character is not whitespace, and closing only when the previous character is not whitespace — the rule markdown math parsers use. Before this, `"What is the cost of $5?"` produced two stray `<span>` wrappers (reintroducing the `break-words` displacement described below) and `"Item A costs $5 and item B costs $10"` rendered the middle of the sentence as italic math with both dollar signs swallowed. Accountancy and Business Administration are seeded programs, so cost questions are routine. **The guard and the tokenizer share one scan** (`findMathSpanFrom`) precisely so they cannot disagree about what counts as math — their disagreement is what produced the stray wrappers.
- **Inline fractions and large operators are promoted to `\displaystyle`.** Inline (text-style) KaTeX renders `\frac` numerator and denominator at roughly 0.7em, so a fraction inside a question stem reads noticeably smaller than the words around it even though `.katex` is already 1.21em. `applyInlineDisplayStyle` prepends `\displaystyle` to inline segments containing a size-collapsing construct (`\frac`, `\binom`, `\sum`, `\prod`, `\int`, `\oint`, `\lim`). **Only those** — a blanket `\displaystyle` would make every inline expression taller for nothing, and raising the `.katex` font size instead would oversize simple variables like `$x$` against body text. Block math is untouched, being display style already. The macro boundary is a negative lookahead for a letter rather than `\b`, because `_` is a word character and `\b` fails after `\sum_`.
- **`renderMathText` returns the raw string untouched when the text contains no math delimiters**, which is the overwhelming majority of questions. That is deliberate: wrapping every plain string in an extra element changes which node `getByText` resolves to and silently relocates styling such as `break-words` off the element callers put it on. Structure is added only where math actually exists.
- Rendering falls back to plain text when a segment fails to parse, so malformed LaTeX degrades to the original markup rather than breaking the page.
