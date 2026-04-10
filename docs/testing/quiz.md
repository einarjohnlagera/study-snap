# quiz.md - Testing Notes

Verify these cases for quiz surfaces (see also: `OpenAiLlmStudyPackServiceTest`):

## Post-quiz UX rules (all flows)

- result screens do not contain a "Note" button — navigation is handled by `← Back to Note` link below action buttons
- `← Back to Note` link is separate from the action button group, not inside it
- Quick Review: after selecting a confidence level, option buttons are replaced by a single confidence badge (🟢 Confident, 🟡 Improving, or 🔴 Needs Practice)
- Quick Review: when the user misses concepts and Adaptive Practice is locked/unavailable, `Practice Again` is the primary action and locked weak-practice messaging remains secondary
- Adaptive Practice: "Generate New Set" is the primary button on the result screen; "Note" button is absent
- Adaptive Practice: if a completed set has no attached weak-area labels, the result screen shows a clear empty targeted-weak-areas message and still offers `Generate New Set` plus `Review Answers`
- Challenge Quiz: "Practice Weak Concepts" is primary when weak concepts exist, followed by "Start Another Challenge" and "Review Answers"
- Challenge Quiz: if no weak concepts are identified, `Start Another Challenge` becomes the primary result action and the no-weak-concepts copy explains the next step
- empty or blocked quiz edge states use a `← Back to Note` text link, not a `Back to Note` button
- Quick Review, Challenge Quiz, and Adaptive Practice result screens expose `Review Answers` as a learning/review action, not a retry or new-quiz action
- `Review Answers` shows each question with the user's selected answer, the correct answer, the explanation, and the concept label from the quiz item
- incorrect selected answers are visually distinct from the correct answer; selected-correct answers show both `Your answer` and `Correct answer`
- Review Answers Previous / Next navigation preserves the current reviewed question and does not reset answer state
- the optional `Incorrect only` filter shows only missed questions and does not reorder or reshuffle choices
- Review Answers must be backed by stored quiz/session data so completed-session review/history can reuse the same structure later

## Exam Mode behavior

- Challenge Quiz start screen shows two explicit mode options: `Challenge Quiz` and `Board Exam Mode`
- entering Challenge Quiz does not auto-start generation; the user must choose a mode first and then land on a mode-specific prescreen
- Premium users who choose `Challenge Quiz` see `Challenge Quiz Setup` before generation with visible difficulty controls
- Free users who choose `Challenge Quiz` also see `Challenge Quiz Setup` before generation, but only as a recommended-difficulty summary with subtle Premium upsell copy
- `Challenge Quiz Setup` shows timer, question-count, and attempt-usage sections in addition to the mode-specific difficulty section
- Board Exam Mode is available on both Free and Premium plans and still consumes the standard Challenge Quiz quota
- Board Exam Mode shows a dedicated `Board Exam Setup` confirmation state with `Cancel` and `Start Exam`
- Board Exam Setup includes:
  - focused exam simulation description
  - strict timed session summary
  - rules for no navigation, results after completion, and leaving counts as submission
  - attempt-usage summary
- tapping `Start Exam` first opens the `Start Board Exam Mode?` confirmation modal and generation does not begin until the user confirms
- Board Exam Mode never shows difficulty selection in the UI and always uses mixed difficulty with the fixed Board Exam question count
- Free users can still enter Board Exam Mode without a separate Premium lock or dead-end disabled controls
- no correctness indication shown during the answering phase (no green/red, no "Correct"/"Incorrect" labels)
- selected choice shows neutral exam-style highlight only
- question-number navigation during Board Exam Mode shows current/answered/unanswered states only and never correctness
- Question Navigator is collapsible:
  - Challenge Quiz defaults to expanded on desktop and collapsed on mobile
  - Board Exam Mode defaults to collapsed on both desktop and mobile
  - expanding/collapsing does not break direct question jumps or answered/current indicators
- active Board Exam pages visibly reinforce context with `Board Exam Mode`, `Exam in progress`, and copy explaining that limited navigation is intentional
- the one-time Board Exam focus tip can be dismissed and does not reappear for the same user after dismissal
- after `Start Exam` / `Start Quiz` is clicked, difficulty buttons are disabled immediately
- after `Start Exam` / `Start Quiz` is clicked, the Start button is disabled immediately and shows starting/loading copy
- double-clicking Start does not create duplicate Challenge Quiz start requests
- after `Start Exam` / `Start Quiz` is clicked, a full-screen generation overlay appears and page interaction is blocked until generation resolves
- Challenge Quiz generation refresh/status checks reuse existing `GENERATING` or `IN_PROGRESS` sessions and do not call the LLM again
- if Challenge Quiz generation returns `FAILED`, the page shows a retryable failure state rather than starting an active quiz
- "Answers are graded only after submission." hint is visible during the quiz
- Previous / Next navigation maintains all previously selected answers
- Submit button appears only on the last question; Previous is disabled on the first question
- timer resumes from persisted session state after refresh/reload instead of resetting
- Board Exam timer warning state appears with less than 3 minutes remaining, and urgent state appears in the final minute
- timer display is derived from persisted timing instead of trusting a purely local decrement counter
- timer auto-submits when it hits 00:00 and shows "Time ran out." message on the Board Exam result screen
- timer expiry in Board Exam Mode also shows the transient `Time's up. Submitting your exam...` state before the result screen renders
- if timeout submission fails, answer changes and question navigation stay locked and the page does not retry auto-submit on every later tick
- if manual submit is already in flight when the timer expires, only one completion request is sent
- choosing `Leave Exam` during Board Exam Mode opens `Leave exam?` with `Stay` and `Submit & Leave`
- confirming `Submit & Leave` completes the current Board Exam once and then exits

## Result screen

- score summary shows correct count, total questions, and percentage (e.g. 7/10 · 70%)
- performance badge maps correctly: 90–100 → Excellent, 75–89 → Good, 50–74 → Fair, 0–49 → Needs Improvement
- concept breakdown lists per-concept accuracy with correct/total and percentage
- weak concepts section lists only concepts with accuracy < 60%
- "Practice Weak Concepts" button is visible only when weak concepts exist; links to Adaptive Practice
- "Review Answers" toggle reveals full answer review with correct/incorrect highlights
- answer review uses the same stable choice order as the answering phase
- result screen is readable on mobile with sections stacking cleanly
- Board Exam result framing stays distinct from standard Challenge Quiz result framing while still reusing the shared Review Answers flow
- Board Exam result uses formal wording such as `Exam Result` and `Performance`

- Quick Review, Challenge Quiz, and Adaptive Practice each use distinct icons
- Quick Review questions stay lightweight and aligned with the learner level
- Challenge Quiz feels exam-style and does not repeat the base Quick Review question set
- Adaptive Practice stays focused on weak concepts instead of drifting into unrelated topics
- quantitative notes can produce computation questions with useful step-based explanations
- displayed `A` / `B` / `C` / `D` labels match the displayed choice order only and are not embedded in stored choice strings
- generated and legacy choice strings with prefixes such as `A. Encapsulation` or `B) Abstraction` are sanitized to plain choice text before storage/rendering
- choice shuffling preserves correctness for Quick Review, Challenge Quiz, and Adaptive Practice
- the same in-progress session does not reshuffle choices differently on re-render
- result and review states still map the selected displayed answer back to the canonical correct choice
- legacy payloads that still expose answer text, answer letters, `answerIndex`, or prefixed selected-choice strings normalize correctly on load
- active Quick Review, Challenge Quiz, and Adaptive Practice sessions show `Leave Quiz`
- app route clicks, browser back, and refresh/reload attempts are blocked or warned while a quiz session is active
- the shared `Leave quiz?` modal offers `Stay` and `Leave Quiz`
- confirming `Leave Quiz` marks the session `FORFEITED`, does not set `completedAt`, and does not record a completed quiz result
- Challenge Quiz and Adaptive Practice forfeits do not refund quiz credits or decrement usage counters
- Quick Review load/reload does not loop, repeatedly redirect, or repeatedly call the note/session start APIs.
- Quick Review does not use the LLM-generation overlay or LLM-generation navigation lock; only the active-session `Leave Quiz` guard applies after a Quick Review session starts.
- Adaptive Practice page load checks existing session state without triggering new LLM generation.
- after `Start Adaptive Practice` or `Generate New Set` is clicked, a full-screen `Generating your quiz...` overlay appears and page interaction is blocked until generation resolves
- Adaptive Practice double-click start attempts create only one generation request from the frontend.
- backend Challenge Quiz and Adaptive Practice start tests cover `GENERATING` session reuse without a second LLM call.
- backend Challenge Quiz and Adaptive Practice status tests should cover `GENERATING` -> poll, `IN_PROGRESS` -> resume, and `FAILED` -> retry.
- local quiz UI testing may enable `QUIZ_GENERATION_MODE=mock` so Challenge Quiz, Board Exam Mode, and Adaptive Practice generate without calling the real LLM.
- when `QUIZ_GENERATION_MODE=mock`, verify:
  - the backend logs that quiz generation mock mode is active
  - Challenge Quiz / Board Exam still create normal sessions and results
  - Adaptive Practice still uses weak-concept-driven session flow
  - no Study Pack or other non-quiz LLM flow changes behavior unintentionally
- if loading overlays need extra time in local QA, set `QUIZ_GENERATION_MOCK_DELAY_MS` to a small value such as `600` and verify the same generation lock/guard states still appear
- Note Detail `Summary` / `Quiz` controls render as tabs, not buttons
- active Note Detail tab updates with underline state and `aria-selected`
- `?tab=quiz` opens the quiz view directly
- switching tabs preserves the same note route and updates query state without full reload
- desktop quiz actions show icon + text
- mobile quiz actions keep accessible labels
- paywall/plan gating still applies to Premium-only quiz flows where configured

## `lib/challenge-quiz-results.ts` unit tests (covered in `challenge-quiz-results.test.ts`)

- `computeScore` — all correct, all wrong, mixed, unanswered, single question, empty quiz
- `mapPerformanceLevel` — boundary values at 90, 75, 50, 49, 0
- `computeConceptBreakdown` — grouping, per-concept accuracy, Unknown fallback, alphabetical sort, empty quiz
- `computeWeakConcepts` — below/at/above threshold, custom threshold, empty breakdown
- end-to-end integration: mixed session → correct score + Fair level + correct weak concepts identified

## Concept validation and repair (backend unit tests)

These cases are covered in `OpenAiLlmStudyPackServiceTest`:

- valid 1-word concept (`NADH`) passes
- valid 2-word concept (`Ohm's Law`) passes
- whitespace-only concept fails with `LLM_INVALID_OUTPUT`
- null concept fails with `LLM_INVALID_OUTPUT`
- concept with repeated spaces (`ATP  production`) normalizes to `ATP production` and passes
- 5-word concept with filler prefix (`Relationship between voltage and current`) is repaired to `voltage and current` and passes
- 5-word concept without filler prefix (`Electrical power using Ohms Law`) is repaired by truncating to first 4 words and passes
- overly long subject (`Electrical Engineering – Voltage Current Resistance and Power`) is repaired to `Electrical Engineering – Voltage Current Resistance` and passes
- whitespace-only subject fails with `LLM_INVALID_OUTPUT`
- technical subjects within word limit (`Electrical Engineering – Ohm's Law`, `Mathematics – Calculus`, `Physics – Electrical Power`) pass
- Ohm's Law regression scenario: full study pack with electrical engineering subject and 1-2 word concepts succeeds end-to-end
