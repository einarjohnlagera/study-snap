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
- available on Free and Premium with plan-based monthly Challenge Quiz limits
- icon: trophy
- generated separately from Quick Review
- learner-level aware, defaulting to `College` when missing
- question count adapts by recent Quick Review performance
- should not repeat the Study Pack / Quick Review question set
- the start screen must disable difficulty controls and the Start button immediately after `Start Quiz` is clicked.
- duplicate starts must be prevented while the start request is in flight.
- while Challenge Quiz LLM generation is in progress, the page shows a full-screen generation overlay and blocks page interaction/navigation until the backend returns `IN_PROGRESS` or `FAILED`.
- quantitative notes may produce computation or formula-based questions
- explanations should be tutor-style and step-based for computation questions

#### Board Exam Mode

- Board Exam Mode is the strict exam-simulation presentation of the Challenge Quiz engine.
- Board Exam Mode is available to all users from the Challenge Quiz screen and uses the same Challenge Quiz credit/quota rules as the standard Challenge Quiz flow.
- The Challenge Quiz start screen now presents two explicit mode cards:
  - `Challenge Quiz` for flexible timed review
  - `Board Exam Mode` for stricter exam simulation
- Entering `Challenge Quiz` no longer auto-starts generation; users first choose a mode, then review a mode-specific prescreen before generation begins.
- `Challenge Quiz` branches by plan:
  - Premium users go to `Challenge Quiz Setup`, can choose `easy`, `medium`, or `hard`, then start the quiz from that prescreen.
  - Free users also go to `Challenge Quiz Setup`, see the recommended `Medium` difficulty plus a subtle `Choose difficulty (Premium)` upsell, then start from the same prescreen.
- `Challenge Quiz Setup` should show:
  - difficulty section
  - timer summary: `10 minutes. Timer runs until submission or expiration.`
  - question-count summary
  - attempt-usage summary: `Consumes 1 Challenge Quiz attempt.`
  - primary action: `Start Quiz`
- Board Exam Mode uses a dedicated pre-exam confirmation/setup state with:
  - `Board Exam Setup`
  - concise description block
  - timer summary
  - rules summary
  - attempt-usage summary
  - `Cancel` and `Start Exam`
- Board Exam Setup must explicitly explain:
  - this is a focused exam simulation with mixed difficulty
  - strict timed session
  - no navigation during the exam
  - results shown only after completion
  - leaving counts as submission
- Tapping `Start Exam` opens a confirmation modal before generation starts:
  - title: `Start Board Exam Mode?`
  - message: the user is about to start a board exam simulation, results are delayed until the end, and navigation will be limited during the exam
  - actions: `Cancel`, `Start Exam`
- Board Exam Mode always skips difficulty selection in the UI and uses internally controlled mixed difficulty (`12` questions) for the current rollout stage.
- Difficulty selection remains Premium-gated for `Challenge Quiz`; when available, question count is derived from the selected difficulty:
  - easy -> 10 questions
  - medium -> 12 questions
  - hard -> 15 questions
- If Challenge Quiz difficulty selection is not available, question count and difficulty remain auto-selected from recent performance.
- Board Exam Mode may request browser fullscreen/focus mode on start as a best-effort enhancement, but the session must still work if the browser denies fullscreen.
- Board Exam Mode uses the same Challenge Quiz session persistence, usage limits, idempotency, timer, answer storage, session guard, and Review Answers flow.
- Board Exam Mode is persisted as an explicit Challenge Quiz session mode value (`board_exam`) so refresh/reload resumes the same exam presentation rather than falling back to the practice view.

#### Exam Mode Behavior

- no correctness feedback during answering — answer first, see results later
- standard Challenge Quiz keeps a lighter practice-oriented answering UI
- Board Exam Mode uses a more neutral, formal answering UI:
  - neutral selected-state styling
  - formal timer/progress header
  - reduced color emphasis
- Board Exam running state must reinforce context explicitly:
  - top-page header copy indicating `Board Exam Mode`
  - small `Exam in progress` framing
  - reminder that limited navigation is intentional, not a broken UI state
- Board Exam Mode may show a one-time, dismissible focus tip per user explaining that distractions are hidden intentionally to simulate a real test environment
- hint text: "Answers are graded only after submission."
- In-progress quiz layout should stay mobile-first and focused:
  - compact sticky top bar with leave action, mode label, and timer/progress
  - question section as the primary focus, starting with `Question X of Y`
  - Question Navigator below the choices for Challenge Quiz / Board Exam only
  - sticky bottom action bar on mobile so `Next`, `Previous`, or `Submit` stays reachable
- remove redundant in-card quiz labels during the active question flow when the top bar already provides the context
- user navigates freely between questions (Previous / Next) and can change answers until submission
- user may jump by question number through the neutral Question Navigator; answered/current states must not reveal correctness
- the Question Navigator is collapsible to reduce clutter:
  - Challenge Quiz: expanded by default on desktop, collapsed by default on mobile
  - Board Exam Mode: collapsed by default on both desktop and mobile for a more focused exam feel
  - the collapsed summary shows current question position plus answered count and can be expanded on demand without losing navigator functionality
- Quick Review shares the same focused mobile rhythm but stays simpler:
  - no timer emphasis
  - no Question Navigator
  - compact top bar plus sticky bottom `Next` / `Finish` action during active questions
- Feedback visibility during quiz flows is context-aware:
  - active quiz states do not show the floating `Send Feedback` launcher so it cannot overlap choices, navigator, or next-step controls
  - completed result and `Review Answers` states show an inline feedback panel instead
  - the inline panel may offer quick actions such as `Report Question`, `Confusing Explanation`, and `Something is wrong`, all routed through the same feedback modal
- quiz auto-submits if the timer reaches zero; user can also submit manually from the last question
- if the timer expires during Board Exam Mode, show `Time's up. Submitting your exam...` while submission finishes
- timer source of truth is persisted session timing: `timerStartedAtEpochSeconds + timeLimitSeconds`
- the visible countdown is always re-derived from persisted timing so refresh/reload does not reset or extend the exam
- Board Exam timer warning states:
  - normal -> more than 3 minutes remaining
  - warning -> 3:00 to 1:01 remaining
  - urgent -> 1:00 to 0:01 remaining
  - expired -> 0:00
- once the timer reaches zero, Board Exam locks answer changes and question navigation immediately
- timeout auto-submit must fire at most once per expiry event; if that submission fails, the page may offer explicit retry submission but must not silently auto-submit on every subsequent tick
- explanations are hidden during the quiz and only appear in the Answer Review section on the result screen
- Board Exam leave handling is stricter than standard Challenge Quiz:
  - leave modal title: `Leave exam?`
  - message: `Your progress will be submitted and counted as complete.`
  - actions: `Stay`, `Submit & Leave`
  - confirming the leave submits the current exam once, then exits to the target route

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
6. **CTAs**:
   - Challenge Quiz: "Practice Weak Concepts", "Start Another Challenge", `← Back to Note`
   - Board Exam Mode: "Practice Weak Concepts", "Take Another Board Exam", `← Back to Note`
- Board Exam result framing should stay more formal than standard Challenge Quiz:
  - section label remains tied to `Board Exam Mode`
  - main heading uses `Exam Result`
  - summary copy refers to `performance` rather than casual encouragement while still leading into recovery actions

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
- Adaptive Practice must not trigger LLM generation on page load. The page may load existing `GENERATING`, `IN_PROGRESS`, or `FAILED` state, but new generation starts only after the visible `Start Adaptive Practice` / `Generate New Set` action.
- while Adaptive Practice LLM generation is in progress, the page shows a full-screen generation overlay and blocks page interaction/navigation until the backend returns `IN_PROGRESS` or `FAILED`.
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
- Backend quiz normalization strips hardcoded leading choice labels such as `A. `, `B) `, `c. `, and `D) ` from generated and legacy choice text before validation/storage.
- Frontend choice rendering also strips legacy prefixes defensively so the UI never shows doubled labels such as `A. A. Encapsulation`.
- Quiz sessions must persist selected canonical choice indexes, not answer text or letters.
- Choice shuffling must preserve correctness by keeping selections and correct answers mapped to canonical choice indexes, not displayed letters.
- Displayed choice order must remain stable for the same question/session once review starts.

## LLM Generation Lock and Idempotency

- Quick Review is excluded from LLM generation locks because it uses the base Study Pack quiz and should remain lightweight. Do not add the full-screen generation overlay or LLM-generation navigation lock to Quick Review.
- Challenge Quiz and Adaptive Practice are LLM-generated and must reserve a `GENERATING` session before calling the LLM.
- If a Challenge Quiz or Adaptive Practice start request finds an active `GENERATING` session, it returns that session and must not call the LLM again.
- If it finds an `IN_PROGRESS` session with quiz payload, it returns that ready active quiz and must not call the LLM again.
- If the latest observed session is `FAILED`, the UI shows a friendly retry state and a new start request may retry generation.
- `IN_PROGRESS` is the ready/active quiz state for Challenge Quiz and Adaptive Practice; there is no separate persisted `READY` status in the current model.
- Challenge Quiz and Adaptive Practice refresh/reload recovery uses the in-progress status endpoint: `GENERATING` continues polling, `IN_PROGRESS` resumes the quiz, and `FAILED` allows retry.
- While LLM generation is active, the frontend disables all quiz controls, blocks app link clicks and browser back navigation, and uses the browser-native refresh warning.

## Active Session Lock and Forfeit Rules

- Quick Review, Challenge Quiz, and Adaptive Practice must show a visible `Leave Quiz` action while a quiz session is active.
- Active quiz sessions block navigation away through app links, browser back navigation, and refresh/reload warnings.
- Leaving shows the shared confirmation modal:
  - title: `Leave quiz?`
  - message: `You are currently in an active quiz. Leaving will forfeit your progress.`
  - actions: `Stay` and `Leave Quiz`
- Confirming `Leave Quiz` marks the active session `FORFEITED` and leaves `completedAt` unset.
- `FORFEITED` sessions are not counted as completed attempts and must not appear in completed/recent quiz results.
- Challenge Quiz and Adaptive Practice forfeits do not refund quiz credits because quota is consumed when the generated session starts.
- Quick Review forfeits abandon the lightweight in-progress session without any premium-credit handling.

## Post-Quiz UX Rules (all flows)

These rules apply uniformly across Quick Review, Challenge Quiz, and Adaptive Practice:

### Navigation
- Never use a `Note` **button** for navigation; use a `← Back to Note` text link
- The `← Back to Note` link must be placed **below** action buttons, visually separate from the CTA group
- A `← Note` BackLink is shown at the page header (above the card) when no quiz session is active
- During an active quiz, the header BackLink is replaced by active-session text plus `Leave Quiz`; users must confirm forfeiting before leaving.

### Button hierarchy on result screens
- **Primary**: the most useful next learning step for the result state
  - Quick Review: `Practice Weak Concepts` when available, otherwise `Practice Again` for missed concepts or `Start Challenge Quiz` after a strong/perfect result
  - Challenge Quiz: `Practice Weak Concepts` when weak concepts exist, otherwise `Start Another Challenge`
  - Adaptive Practice: `Generate New Set`
- **Secondary**: learning support and repeat actions that are not the main next step (`Review Answers`, locked `Unlock Practice Weak Concepts`, secondary `Practice Again`, secondary `Start Another Challenge`)
- **Navigation**: `← Back to Note` text link below the button group — never a button
- Edge states such as empty quiz data, monthly limits, or unavailable sessions must still use text-link navigation back to the note, not a `Back to Note` button.
- If no weak concepts are identified, the result screen should say so directly and still provide a clear next step.

### Review Answers
- The result screen must expose a clear `Review Answers` action for post-assessment learning.
- Review Answers uses the same shared review layout across Quick Review, Challenge Quiz, and Adaptive Practice.
- The review layout shows one question at a time with Previous / Next navigation, plus an optional `Incorrect only` filter when mistakes exist.
- Each reviewed question shows:
  - original question number and text
  - concept chip from the stored quiz item
  - answer choices in stable displayed order
  - `Your answer` badge on the selected choice
  - `Correct answer` badge on the correct choice
  - visible `Why this is correct` explanation below the choices
- Correct answers use restrained green styling, incorrect selected answers use restrained red styling, and neutral distractors stay visually quiet.
- Review Answers must use stored quiz/session data (`question`, `choices`, selected canonical choice indexes, `correctIndex`, `explanation`, `concept`) so the same component can later support completed-session history or export without depending on freshly completed page state only.

### Confidence feedback (Quick Review only)
- Once a confidence option is selected, the option buttons are replaced by a styled badge
- HIGH → `🟢 Confident` (green badge)
- MEDIUM → `🟡 Improving` (amber badge)
- LOW → `🔴 Needs Practice` (orange badge)
- "Thanks for the feedback." text is removed; the badge is the sole feedback signal

## v0.8.0 Board Exam Mode (Phase 1)

Board Exam Mode now makes the Challenge Quiz engine feel like a strict board-exam simulation:
- clean answering phase with no correctness hints
- explicit mode selector with both `Challenge Quiz` and `Board Exam Mode`
- entering `Challenge Quiz` now always starts at mode selection instead of auto-generating a session
- Free users can launch `Challenge Quiz` immediately without a disabled difficulty step; Premium users still get the dedicated difficulty setup screen
- Board Exam Mode available to Free and Premium users under the existing Challenge Quiz quota rules
- formal `Board Exam Setup` confirmation screen with `Cancel` and `Start Exam`
- Board Exam Mode skips difficulty selection and uses mixed difficulty during generation
- 10-minute persisted countdown that auto-submits on expiry
- neutral question-number navigator for moving through the exam without revealing correctness
- more formal in-exam UI and result framing than the standard Challenge Quiz presentation
- structured result screen with score, performance level, concept breakdown, and weak concepts
- weak concepts feed into the Adaptive Practice flow
- `lib/challenge-quiz-results.ts` provides pure, independently tested result computation utilities
- Adaptive Practice `completionMessage` uses `mapPerformanceLevel` for consistent 4-tier feedback
