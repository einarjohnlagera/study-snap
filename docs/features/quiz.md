# quiz.md - NoteLib Feature Context

## Goal

Quiz features turn a Study Pack-ready note into active-recall practice without creating separate product ownership models.

Shared ownership model:

- generated quiz content belongs to `noteId`
- Quick Review, Challenge Quiz, and Adaptive Practice sessions are note-scoped

## Quiz Modes

### Quick Review

- fast review flow
- available on Free and Premium
- icon: lightning
- source: generated as part of the base Study Pack quiz
- shape: 5 questions, fast concept checks, immediate feedback
- learner-level aware, defaulting to `College` when missing
- quantitative notes may include a simple numerical question only when clearly supported by the notes

### Challenge Quiz

- exam-style challenge mode
- plan-gated through the shared Premium prompt flow
- icon: trophy
- generated separately from Quick Review
- learner-level aware, defaulting to `College` when missing
- question count adapts by recent Quick Review performance
- should not repeat the Study Pack / Quick Review question set
- quantitative notes may produce computation or formula-based questions
- explanations should be tutor-style and step-based for computation questions

#### Exam Mode Behavior

- no correctness feedback during answering — answer first, see results later
- user selects an answer and sees only a neutral visual selection state (blue highlight)
- hint text: "Answers are graded only after submission."
- user navigates freely between questions (Previous / Next) and can change answers until submission
- quiz auto-submits if the timer reaches zero; user can also submit manually from the last question
- explanations are hidden during the quiz and only appear in the Answer Review section on the result screen

#### Result Screen

After submission, the result screen shows:

1. **Score Summary** — correct count, total questions, percentage
2. **Performance Level** (badge):
   - 90–100% → Excellent
   - 75–89% → Good
   - 50–74% → Fair
   - 0–49% → Needs Improvement
3. **Concept Breakdown** — per-concept accuracy (correct / total, percentage)
4. **Weak Concepts** — concepts with accuracy < 60%; used to drive Adaptive Practice
5. **Answer Review** — toggle to reveal all questions with correct/incorrect highlighting
6. **CTAs**: "Practice Weak Concepts" (→ Adaptive Practice), "Start Another Challenge", "Note"

#### Result Computation Rules

- ALL result calculations are derived from quiz session data + stored `question → concept` mapping
- no LLM calls are used for statistics
- `computeScore`, `mapPerformanceLevel`, `computeConceptBreakdown`, and `computeWeakConcepts` are exposed as pure frontend utility functions in `lib/challenge-quiz-results.ts`
- weak concept threshold: accuracy < 60% (`WEAK_CONCEPT_THRESHOLD = 60`)
- questions without a `concept` label are grouped under "Unknown" in the breakdown
- the backend independently computes and stores results; the frontend utility functions exist for testability

### Adaptive Practice

- weak-area follow-up mode
- shown when weak concepts exist and plan allows it
- icon: target
- generated separately from Quick Review
- learner-level aware, defaulting to `College` when missing
- may be slightly easier than Challenge Quiz, but must stay focused on weak concepts only
- quantitative weak concepts may use targeted numerical reinforcement questions
- explanations should reinforce the weak concept clearly and step through computations when relevant

## Quiz concept validation rules

Every quiz item includes a `concept` field — a short topic label for the key idea being tested.

- **Enforced range:** 1 to 4 words (validated after whitespace normalization)
- **Prompt target:** 1 to 3 words — the prompt asks for short labels like `Ohm's Law`, `Electrical Power`, `Current Flow`
- **Repair:** if the LLM returns a concept exceeding 4 words, the backend tries to repair it by:
  1. Stripping common leading filler phrases (`Relationship between`, `Using the`, `The role of`, etc.)
  2. Truncating to the first 4 words if still over the limit
- **Logged on failure:** `requestId`, `field`, `value` (truncated to 80 chars), `reason`
- **Null or blank concepts** fail immediately with `LLM_INVALID_OUTPUT`
- This applies equally to Quick Review, Challenge Quiz, and Adaptive Practice concept fields

## Entry Surfaces

Quiz entry points appear in:

- Private Note Detail
- Dashboard recommendations
- other owner study surfaces derived from the same note

Use distinct icons for each mode and keep the action mapping consistent across pages.

## UI Rules

- quiz-mode launchers are buttons/actions
- `Summary` and `Quiz` on Note Detail are tabs/view navigation
- desktop action buttons show icon + text
- mobile action buttons default to icon-only unless text is needed for clarity

## Current v0.5.0 Scope

- Quick Review
- Challenge Quiz
- Adaptive Practice
- distinct quiz-mode icons
- tab-based `Summary` / `Quiz` switching on Note Detail

## Generation Contract

- Raw generated-quiz JSON format from the LLM:
  - `question`
  - `choices` (exactly 4)
  - `answer` (`A`, `B`, `C`, or `D`)
  - `explanation`
  - `concept`
- No markdown, comments, or extra text outside JSON.
- Quiz explanations should teach, not just state the answer.
- For computation questions, explanations should show short step-by-step solution flow.

## Runtime Contract

- Canonical stored/shared quiz data must normalize to:
  - `question`
  - `choices`
  - `correctIndex`
  - `explanation`
  - `concept`
- `A` / `B` / `C` / `D` are presentation-only labels derived from displayed order.
- Quiz sessions must persist selected canonical choice indexes, not answer text or letters.
- Choice shuffling must preserve correctness and remain stable for the same question/session once review starts.

## v0.8.0 Board Exam Mode (Phase 1)

Challenge Quiz now functions as a full Exam Mode experience:
- clean answering phase with no correctness hints
- structured result screen with score, performance level, concept breakdown, and weak concepts
- weak concepts feed into the Adaptive Practice flow
- `lib/challenge-quiz-results.ts` provides pure, independently tested result computation utilities
