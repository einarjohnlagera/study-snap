# challenge-quiz.md - NoteLib Feature Context

> **Mode identity is locked in `docs/product/EXAM_MODES.md`.** Challenge Quiz is *practice with stakes* — flexible, progressive, user-controlled. It is **not** a sit-down exam. Long Exam Mode and Board Exam Mode cover those needs. The progressive generation, `+5 Questions`, early-submit, and inline learner-level adjustment are core to Challenge Quiz's identity and must be preserved.

## Goal

Challenge Quiz is the timed, exam-style quiz mode built from a Study Pack-ready note.

It is separate from Quick Review:

- separate generation flow
- separate session history
- stricter timing and setup
- result screen designed around next-step practice

It is also separate from Board Exam Mode and (planned) Long Exam Mode:

- Challenge Quiz is flexible and progressive; Long Exam is fixed and pause-friendly; Board Exam is a strict simulation with reduced UI chrome and no progressive controls
- per-mode identity contracts are in `docs/product/EXAM_MODES.md`

## Entry flow

Student and Board Taker enter through a shared mode-selection screen. Profile type determines which cards appear and which mode is emphasized. Source of truth: `lib/exam-mode-visibility.ts`.

### Profile-aware mode cards (v0.12.0)

**Student:**

- `Challenge Quiz` — Recommended
- `Long Exam Mode` — Coming Soon (card visible; tapping it shows a coming-soon screen with "Long Exam — Coming Soon" disabled; "Choose another mode" returns to mode-selection)
- Escape-hatch line shown: *"Preparing for boards? Switch your profile in Settings to enable Board Exam Mode."*

**Board Taker:**

- `Board Exam Mode` — Recommended (Pro gate shown for Free/Plus users)
- `Challenge Quiz` — listed as alternate

**Teacher:**

- Skips mode-selection entirely; lands directly on the Challenge Quiz setup step

### Planned (v0.13+, per `EXAM_MODES.md`)

- Long Exam Mode backend session support: fixed question set, pause-friendly, mastery-report result screen; "Start Long Exam" becomes active

## Current plan gating

- Challenge Quiz is available on Free, Plus, and Pro with monthly limits
- Board Exam Mode is Pro-only
- Free and Plus users who choose Board Exam Mode must hit the shared Pro upsell flow
- monthly quiz-limit exhaustion is separate from Pro-only feature gating

## Generation and session behavior

- Challenge Quiz is LLM-generated
- start flow must be idempotent
- an existing `GENERATING` or `IN_PROGRESS` session must be reused instead of creating duplicates
- active generation uses the shared generation lock and recovery flow
- Challenge mode starts with **5 questions** (`INITIAL_CHALLENGE_QUIZ_COUNT = 5`)
- Board Exam Mode generates based on the user's learner profile (10–15 questions) and does not use progressive generation

## Progressive Quiz Generation (Challenge mode only)

Challenge mode supports on-demand question batching within a live session:

- **Initial count**: 5 questions
- **Batch size**: +5 questions per request
- **Maximum**: 20 questions per session (`MAX_CHALLENGE_QUIZ_QUESTIONS = 20`)
- Backend minimum for a valid batch: 3 unique new questions after dedup (`MIN_NEW_QUESTIONS_AFTER_DEDUP = 3`)
- Users see a `+5 Questions` / `Adding...` button in the action bar at the last answered question (when under max and `noMoreQuestions` is false)
- Generates via `POST /challenge-quiz/sessions/{sessionId}/generate-more`; the response is `GenerateMoreChallengeQuizResponse { newQuestions, totalQuestions }`
- Backend uses `QuizDeduplicationUtils.uniqueQuestions()` to deduplicate by normalized question text against all existing session questions
- If fewer than 3 unique new questions survive dedup, backend returns `NOT_ENOUGH_NEW_QUESTIONS` (HTTP 409); frontend treats this as `noMoreQuestions = true`, not an error state
- New questions are appended to the session JSONB state via `QuizSessionStateUtils.appendQuizItems()`; no schema changes required
- Board Exam Mode is exempt — no generate-more button; fixed question count for the session

## Action bar and button labels

**Challenge mode (non-Board Exam):**

- At the last question, the action bar renders:
  - `+5 Questions` / `Adding...` — visible when `totalQuestions < MAX_SESSION_QUESTIONS` and `noMoreQuestions` is false
  - `Complete Quiz` — submits the session and navigates to the result screen
- An action hint appears above the buttons at the last question: `What would you like to do next?`

**Board Exam Mode:**

- Retains the original submit button label (`submitButtonLabel`), unchanged by progressive quiz work

## Scoring

- Score is based on **answered questions** (`selectedChoices.size()`), not the total questions in the session
- `computeStatistics()` uses `selectedChoices.size()` as `totalQuestions` when the user has answered at least one question; falls back to `quiz.size()` only when nothing is answered
- Result screen copy: `{correctAnswers} of {totalQuestions} answered correctly`
- Score Summary column header: `Answered` (not `Total`)
- This allows users to finish early and receive a fair score based only on what they attempted

## UX microcopy

Running state (Challenge mode only):

- Banner at top of quiz: `Start with 5 questions. Generate more as you go (up to 20).`
- Below choice list at last question: `You can finish anytime. Score is based on answered questions.`
- Action bar hint at last question: `What would you like to do next?`
- Toast on successful generate-more: `5 more questions added!` (auto-clears after 3 seconds)
- When `noMoreQuestions = true`, the `+5 Questions` button is hidden; no toast shown

## Result screen

Current result screen sections:

1. score summary
2. concept breakdown
3. weak concepts
4. primary next action
5. secondary actions

Primary CTA rules:

- weak concepts exist -> `Practice Weak Concepts`
- no weak concepts -> retry / next challenge becomes primary

Secondary actions:

- `Review Answers`
- `← Back to Note`

If Adaptive Practice is not available for the user, the result flow should not silently route away; it should respect the current gated path.

## Learner level control

Challenge Quiz result screens expose the same learner-level adjustment control used by Quick Review.

Current save toast:

- `Learner level updated. Future Study Packs and quizzes will match this level.`

## Review and export

- completed sessions remain note-owned
- answer review uses the shared review layout
- review/export must use persisted session data only

## Leave quiz guard

- `useQuizSessionGuard` intercepts navigation (document click, popstate, beforeunload) when the quiz is active
- `onBeforeRouteLeave` and `onConfirmLeave` passed to `useQuizSessionGuard` must be stable references (memoized via `useCallback`) — timer-driven re-renders fire every second and will cause `LeaveQuizModal` to unmount/remount on every tick if these are inline functions
- `onConfirmLeave` reads session state from `challengeSessionRef.current` (not `challengeSession` state) to keep its deps stable while still seeing the latest session value
- Board Exam Mode confirm leave submits the session (`finalizeChallengeSession`) and counts it as completed
- Standard Challenge mode confirm leave calls `forfeitChallengeQuizSession`
