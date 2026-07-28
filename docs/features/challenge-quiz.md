# challenge-quiz.md - NoteLib Feature Context

> **Mode identity is locked in `docs/product/EXAM_MODES.md`.** Challenge Quiz is *practice with stakes* — flexible, progressive, user-controlled. It is **not** a sit-down exam. Long Exam Mode and Board Exam Mode cover those needs. The progressive generation, `+5 Questions`, early-submit, and inline learner-level adjustment are core to Challenge Quiz's identity and must be preserved.

## Goal

Challenge Quiz is the timed, exam-style quiz mode built from a Study Pack-ready note.

It is separate from Quick Review:

- separate generation flow
- separate session history
- stricter timing and setup
- result screen designed around next-step practice

It is also separate from Board Exam Mode and Long Exam Mode:

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

- Skips mode-selection entirely; lands directly on the Challenge Quiz setup step — **except** when a live in-progress session is found, in which case the Teacher is routed through mode-selection too, so the Resume/Start Fresh prompt (below) is never skipped (v0.60.1)

**Professional:**

- `Certification Review` — Challenge Quiz engine with professional-facing label
- `Full Practice Exam` — Long Exam engine with professional-facing label
- `Interview Practice` — Adaptive Practice sub-mode tile for Professional users; Free/Plus users can open setup and see the Interview Practice paywall from the Start CTA

Study Plan / Review Set detail can launch the learner's profile-appropriate premium exam via `collectionId`: Student → Long Exam, Board Exam → Board Exam setup, Professional → Interview Practice. Each prescreen restricts additional-note selection to quiz-ready notes from that plan and preselects only up to the mode's existing cap; Teacher remains on the DOCX Exam Builder path.

### Long Exam backend (v0.13.0)

- Long Exam Mode backend session support is shipped: `LONG_EXAM` sessions use the shared quiz-session table, generate and commit a fixed question set before the session begins, support `PAUSED` resume state, and return a mastery report on completion
- Frontend activation can wire `Start Long Exam` to the dedicated `/long-exam` endpoints when the UI is ready

## Current plan gating

- Challenge Quiz is available on Free, Plus, and Pro with rolling monthly limits of `20`, `100`, and `200` sessions respectively
- Board Exam Mode is Pro-only
- Board Exam Mode consumes the shared Challenge Quiz monthly budget and also has a dedicated Board Exam hard cap (`10` source-note units / month; default configurable)
- Board Exam quota is deducted **per source note** at session start — a 3-note session costs 3 quota units
- Free and Plus users who choose Board Exam Mode can open the setup screen first; the Pro paywall fires from the Begin/Unlock Board Exam Start CTA so the strict exam flow is visible before the upgrade ask
- Study Plan / Review Set launches deep-link into Board Exam setup and keep the additional Study Pack picker scoped to plan notes only; the Board Exam source-note cap and quota rules do not change
- Board Exam quota exhaustion remains separate from Pro-only feature gating
- monthly quiz-limit exhaustion is separate from Pro-only feature gating

## Generation and session behavior

- Challenge Quiz is LLM-generated
- start flow must be idempotent
- an existing `GENERATING` or non-expired `IN_PROGRESS` session is reused instead of creating duplicates; expired in-progress Challenge Quiz and Board Exam sessions are auto-forfeited before a new start can proceed, and expired Challenge Quiz cleanup releases its bank claims
- when the mode-selection entry (`?entry=mode-selection`) finds a live in-progress Challenge Quiz or Board Exam session, it offers **Resume** or **Start Fresh** rather than silently discarding progress; expired sessions return to a clean prestart and are never resumed into an immediate timeout. The note detail Challenge Quiz card and collection/Review Set premium-exam launch both add this entry marker, so their explicit "start" intent gets the same choice. This prompt is forced onto the mode-selection step even for profiles (Teacher) whose initial step otherwise skips it, so a live session is never silently re-entered without the choice (v0.60.1, collection launch aligned in v0.60.2). Direct/bookmarked URLs, page refresh, and Dashboard's Continue widget still auto-resume a live non-expired session with no prompt by design — that is deliberate "continue where you left off" behavior.
- `+5 Questions` (`generateMoreQuestions`) checks the session's own expiry before extending `timeLimitSeconds`; an already-expired session is forfeited and rejected instead of having its deadline pushed back into the future (v0.60.1)
- active generation uses the shared generation lock and recovery flow
- Standard Challenge mode starts with a score-adaptive question count from the learner's latest completed Quick Review on the same Study Pack: below 50 → 10 questions, 50–79 → 12 questions, and 80 or above → 15 questions; no prior score starts with 12. Redo Missed Questions remains fixed at up to 5 claimed questions.
- Challenge mode's assembled question pool (banked + Official template + freshly generated) is shuffled once at initial session start (and at `startRedoMissedSession`'s assembly), so batches never present in fixed `generatedAt` order. A MATCHING block (2–4 consecutive questions sharing a `questionGroup`) always shuffles as one contiguous unit — never split apart — since the frontend (`lib/quiz.ts`) groups them by scanning for adjacency. `+5 Questions` shuffles only the newly-appended batch, never the whole array, to avoid remapping already-recorded index-keyed answers (`selectedChoices` et al. are keyed by array index, not question identity). Board Exam Mode's ordering is unaffected (v0.60.1).
- Each real-LLM Challenge Quiz shortfall records the response model plus input, output, and cached-input token usage on the session. Successful `+5 Questions` calls add to the existing values so the row represents cumulative session usage; sessions fully served from the per-user bank or Official template keep all usage columns null. Missing or malformed usage metadata is ignored without blocking the quiz. Board Exam Mode, Long Exam, and Adaptive Practice do not participate in this telemetry.
- Challenge mode has no user-facing difficulty selector. Its difficulty is fully automatic and comes only from the latest completed Quick Review score on the same Study Pack: below 50 → Easy, 50–79 → Default/Medium, 80 or above → Hard; no prior score also uses Default/Medium.
- Board Exam Mode question count scales with source count: `min(12 × sourceCount, 30)` — single-note: 12, two-note: 24, three-note: 30
- Board Exam Mode does not use progressive generation; question count is fixed at session start
- Board Exam Mode's separate difficulty behavior is unchanged: it does not expose a selector and remains fixed at `DIFFICULTY_MIXED` to preserve exam-simulation framing

### Per-user question bank (v0.58.0)

- Challenge Quiz persists each newly LLM-generated question in an owned, per-user, per-Study-Pack bank. It never shares questions between users or Study Packs.
- On both session start and `+5 Questions`, Challenge Quiz claims eligible banked questions before calling the LLM, then generates only the shortfall. A banked question must match the learner's current learner level and is deduplicated against the live session with `QuizDeduplicationUtils`.
- Claims use a pessimistic lock and stay attached to the in-progress session, preventing two concurrent starts or add-more requests from receiving the same banked question. Completion records the question's last known correct, incorrect, or unanswered outcome and releases the claim; forfeiture and generation failure also release claims.
- Bank persistence is best-effort: a bank read/write failure falls back to the existing fresh-generation path and never blocks a Challenge session. Adaptive Practice remains intentionally always-fresh and does not use this bank.
- When at least three eligible bank entries were last answered `INCORRECT`, the Challenge result handoff offers **Redo Missed Questions**. It starts a normal `CHALLENGE` session sourced only from those same-learner-level claimed questions, with no LLM call and no Challenge quota consumption to *start*. The same-route handoff resets the completed result and reliably starts the redo session; if fewer than three entries remain eligible, it returns to a clean prestart with the backend's message. A bank failure or a claim race fails closed rather than starting a partial redo. Completion still records `ConceptHealth` through the ordinary Challenge path; this is an entry point, not a new quiz mode or sub-mode. The frontend's one-shot retrigger guard resets on every start outcome (success or error), so requesting Redo Missed Questions again later in the same page session (e.g. after redoing once, then missing questions again) reliably fires a second time instead of silently no-op'ing (v0.60.1). Redo session matching uses a dedicated provenance marker: a marked prior redo session still resumes, while an unrelated active Challenge session on the same Study Pack is forfeited and replaced with a fresh missed-only session instead of silently returning its original full question set (v0.60.3).
- `+5 Questions` on a redo-missed session is not quota-exempt-aware: it falls through to normal LLM generation like any other Challenge session, so the zero-cost guarantee applies only to the session's start, not to any later add-more request. Whether to block `+5 Questions` on redo-missed sessions is an open product question, not yet decided.

### Official template source (v0.60.0)

- A currently `PUBLIC` Study Pack owned by an Official author (`role = ADMIN` **or** the reserved official account's email, excluding the deleted-user sentinel — aligned v0.62.0 with `PublicProfileService.isOfficialAuthor`'s reference form; previously admin-role only) receives one canonical Challenge Quiz template of up to 20 questions after the public-note or Study-Pack write commits. An admin-only idempotent backfill can seed already-published Official content; template generation failures are logged and never affect publishing or Study Pack generation.
- Template seed dispatch (both the eager per-note hook and the admin backfill) runs on the shared bulk LLM fan-out pool (`llmParallelTaskExecutor`), never on the live Study Pack generation pool, so a large backfill can't delay or reject real user-facing generation requests (v0.60.1 fix — it originally shared the live-generation pool and could saturate it). The admin backfill response (`AdminSeedOfficialChallengeQuizTemplatesResponse`) reports `queued`/`skipped`/`rejected` counts; a task rejected due to a full queue is logged and counted as `rejected` rather than silently dropped or thrown as an error.
- When an adopter's ordinary Challenge start or `+5 Questions` claim has a shortfall, the bank resolves exactly one lineage hop through the adopter note's `copiedFromNoteId`. It uses a template only when that immediate parent is still currently `PUBLIC` and Official-owned; it never follows `sourceNoteId` or chases further copy hops.
- Template rows are read-only and are never shared as a learner's live bank rows. Eligible unseen template questions are copied into fresh rows under the learner's own user id and copied Study Pack id, claimed by the live session, tagged with the learner's current level, and initialized as `UNANSWERED`. This preserves each learner's own outcomes and Redo Missed Questions history.
- The source read intentionally ignores the Official author's learner level. The copied row uses the learner's own current level so ordinary same-level bank claims continue to work later. Any template shortfall still uses the existing LLM path; private/non-Official Study Packs, Adaptive Practice, quotas, and the redo-missed claim path are unchanged.

### AI Generation Spec

- Challenge mode uses `challenge-quiz-*.txt` prompts for flexible practice with stakes.
- Board Exam uses `board-exam-*.txt` prompts for high-stakes licensure / certification simulation framing.
- Do not route Board Exam through `generateChallengeQuiz()`; it must call the dedicated Board Exam generation method while keeping the fixed question count and no-progressive-generation contract.

### Question formats

Challenge Quiz can generate:

- `MCQ`
- `TRUE_FALSE`
- `MULTI_SELECT`
- `MATCHING`
- `IDENTIFICATION`
- `ENUMERATION`

`IDENTIFICATION` is a free-text active-recall format scored through deterministic `acceptableAnswers[]` matching. It is Challenge Quiz-only for now and has no per-submission LLM call. See `docs/features/identification.md`.

`ENUMERATION` is a multi-slot free-text format (2-5 required items) scored all-or-nothing via exhaustive bipartite matching against `acceptableAnswerGroups[]`. Same Challenge Quiz-only scope and no per-submission LLM call. See `docs/features/enumeration.md`.

## Progressive Quiz Generation (Challenge mode only)

Challenge mode supports on-demand question batching within a live session:

- **Initial count**: 10, 12, or 15 questions from the learner's latest Quick Review score band (below 50, 50–79, or 80+); 12 with no prior score
- **Batch size**: +5 questions per request
- **Maximum**: 20 questions per session (`MAX_CHALLENGE_QUIZ_QUESTIONS = 20`)
- Backend minimum for a valid batch: 3 unique new questions after dedup (`MIN_NEW_QUESTIONS_AFTER_DEDUP = 3`)
- Users see a `+5 Questions` / `Adding...` button in the action bar at the last answered question (when under max and `noMoreQuestions` is false)
- Generates via `POST /challenge-quiz/sessions/{sessionId}/generate-more`; the response is `GenerateMoreChallengeQuizResponse { newQuestions, totalQuestions }`
- Backend first reuses eligible per-user banked questions, then uses `QuizDeduplicationUtils.uniqueQuestions()` to deduplicate any generated shortfall by normalized question text against all existing session questions
- If fewer than 3 unique new questions survive dedup, backend returns `NOT_ENOUGH_NEW_QUESTIONS` (HTTP 409); frontend treats this as `noMoreQuestions = true`, not an error state
- New questions are appended to the session JSONB state via `QuizSessionStateUtils.appendQuizItems()`; no schema changes required
- Board Exam Mode is exempt — no generate-more button; fixed question count for the session

## Action bar and button labels

**Challenge mode (non-Board Exam):**

- At the last question, the action bar renders:
  - `+5 Questions` / `Adding...` — visible when `totalQuestions < MAX_SESSION_QUESTIONS` and `noMoreQuestions` is false
  - `Complete Quiz` — submits the session and navigates to the result screen
- An action hint appears above the buttons at the last question: `What would you like to do next?`
- The action bar is rendered by `StickyAssessmentFooter` (`components/ui/sticky-assessment-footer.tsx`) — fixed to the viewport bottom at all breakpoints with iOS safe-area inset and backdrop blur
- The hint text is passed via the `hint` prop and appears above the buttons

**Board Exam Mode:**

- Uses `variant="board-exam"` on `StickyAssessmentFooter` (slightly different border color)
- Retains the original submit button label (`submitButtonLabel`), unchanged by progressive quiz work

## Scoring

- Score is based on **answered questions** (selected single-choice answers, selected multi-choice answers, nonblank Identification answers, and Enumeration answers with at least one nonblank slot), not the total questions in the session
- `computeStatistics()` uses the answered-question index set as `totalQuestions` when the user has answered at least one question; falls back to `quiz.size()` only when nothing is answered
- This allows users to finish early and receive a fair score based only on what they attempted

### Dual score display (Challenge mode only)

When the user skips questions (`hasUnansweredQuestions = !isBoardExamMode && quiz.length > result.totalQuestions`), the result screen shows two scores side by side:

- **Answered Accuracy** (`result.scorePercentage`) — correct out of answered; primary metric
- **Overall Completion Score** (`Math.round(correctAnswers / totalGenerated * 100)`) — correct out of all generated questions

Helper text explains the distinction. When all questions were answered, only Answered Accuracy is shown (same as before).

**Score Summary** columns when `hasUnansweredQuestions`:
- Correct, Answered Questions, Total Questions, Answered Accuracy (4 columns)

**Score Summary** columns when all answered:
- Correct, Answered Questions, Percentage (3 columns, same layout as before)

## UX microcopy

Running state (Challenge mode only):

- Banner at top of quiz: `Your starting question set adapts to your recent performance. Generate more as you go (up to 20).`
- Below choice list at last question: `You can finish anytime. Score is based on answered questions.`
- Action bar hint at last question: `What would you like to do next?`
- Toast on successful generate-more: `5 more questions added!` (auto-clears after 3 seconds)
- When `noMoreQuestions = true`, the `+5 Questions` button is hidden; no toast shown

## Result screen

Current result screen sections:

1. score summary
2. concept breakdown
3. shared post-session next step
4. secondary actions

Primary CTA rules:

- after completion, the page fetches `GET /study-packs/{studyPackId}/next-step`
- the shared `<PostSessionNextStep>` component renders the dominant next action for Challenge Quiz
- when genuine weak concepts remain, `Practice Weak Concepts` is primary because Challenge Quiz provides the diagnostic signal for targeted Adaptive Practice
- when no genuine weak concepts remain, the primary action stays progression-oriented with another Challenge
- fallback UI keeps the previous weak-concepts card plus retry/practice actions when the next-step fetch fails
- Board Exam Mode and Long Exam do not consume this recommendation service; their report and next-step flows remain mode-owned

Secondary actions:

- `Review Answers`
- `← Back to Note`

If Adaptive Practice quota is exhausted, the shared component shows the targeted concepts plus a plan-aware upgrade CTA instead of routing into the limit wall.

## ConceptHealth

- on completion, Challenge Quiz records concepts answered fully correctly in the session to `ConceptHealth`
- on completion, Challenge Quiz also records concepts missed in the session to `ConceptHealth.lastIncorrectAt`
- Board Exam Mode uses the same Challenge completion path and records fully-correct and missed concepts the same way
- a concept is recorded only when its concept breakdown is `correctAnswers == totalQuestions` and `totalQuestions > 0`
- weak or partially correct concepts are recorded as missed, not mastered
- the post-session next-step endpoint reads ConceptHealth after completion, so fully correct concepts can immediately reset due-ness before the next recommendation is resolved
- a later fully-correct Challenge or Board Exam session updates `lastCorrectAt` and clears the struggling state derived from a newer `lastIncorrectAt`
- genuine weak concepts are the capped union of reviewed-and-decayed ConceptHealth entries plus concepts actually missed in the completed Challenge session
- never-reviewed concepts are treated as not started and cannot trigger Adaptive Practice by themselves

## Learner level control

Challenge Quiz result screens expose the same learner-level adjustment control used by Quick Review.

Current save toast:

- `Learner level updated. Future Study Packs and quizzes will match this level.`

## Review and export

- completed sessions remain note-owned
- answer review uses the shared review layout (`QuizAnswerReview`)
- review/export must use persisted session data only
- standalone session review (`NoteSessionReviewPageClient`) renders `QuizAnswerReview` with `stickyNav={true}`, which replaces the inline Prev/Next navigation with a `StickyAssessmentFooter` fixed to the viewport bottom — eliminates layout jitter when explanations expand/collapse
- inline answer review on the challenge quiz result page does NOT use `stickyNav` (inline nav is correct there)

## AI Generation Spec

### Shared JSON contract

All generated quiz payloads must return JSON only with this shape:

```json
{
  "questions": [
    {
      "question": "...",
      "choices": ["A", "B", "C", "D"],
      "answer": "B",
      "explanation": "...",
      "concept": "..."
    }
  ]
}
```

Rules: exactly 4 choices; `answer` must be `A`, `B`, `C`, or `D`; `explanation` and `concept` are required; no markdown, no comments, no prose before or after JSON.

### Learner level guidance

If the user has no saved learner level, default prompt difficulty to `College`.

| Level | Expected behavior |
|---|---|
| `GRADE_SCHOOL` | Very simple definitions and identification; no tricky distractors; no complex computation |
| `JUNIOR_HIGH` | Concept understanding; simple problem solving; basic computation when supported |
| `SENIOR_HIGH` | Concept understanding plus moderate application; simple to moderate computation |
| `COLLEGE` | Deeper concept questions; situational and analytical prompts; moderate computation |
| `BOARD_EXAM_REVIEW` | Exam-style questions; plausible distractors; situational and multi-step reasoning; computation when quantitative |
| `PROFESSIONAL` | Applied or case-based framing; real-world scenarios |
| `PERSONAL_LEARNING` | Practical and accessible; around a college-foundation baseline unless notes suggest otherwise |

### Quantitative / computation guidance

When the note context suggests a quantitative subject (engineering, physics, math, accounting, finance, chemistry, statistics), the prompt should allow computation questions, formula-based questions, word problems, unit conversions, and multi-step calculations when appropriate. Keep them multiple choice; ensure the computed answer matches one choice exactly; explanation should show short step-by-step solution flow.

### Explanation quality

Explanations should sound like a tutor, explain why the correct answer is correct, stay concise but useful, and include short steps for numeric problems. Avoid empty explanations such as `B is correct because it is correct.`

## Leave quiz guard

- `useQuizSessionGuard` intercepts navigation (document click, popstate, beforeunload) when the quiz is active
- `onBeforeRouteLeave` and `onConfirmLeave` passed to `useQuizSessionGuard` must be stable references (memoized via `useCallback`) — timer-driven re-renders fire every second and will cause `LeaveQuizModal` to unmount/remount on every tick if these are inline functions
- `onConfirmLeave` reads session state from `challengeSessionRef.current` (not `challengeSession` state) to keep its deps stable while still seeing the latest session value
- Board Exam Mode confirm leave submits the session (`finalizeChallengeSession`) and counts it as completed
- Standard Challenge mode confirm leave calls `forfeitChallengeQuizSession`

## Incomplete-submission guard

- Manual `Complete Quiz` / `Submit Exam` checks the existing answered-question state before finalizing.
- With unanswered questions, an `AppModal` names the unanswered count and lets the learner go back to the first unanswered question or submit anyway with the existing incomplete-answer scoring behavior.
- This is a submit-time guard, separate from the navigation-away leave guard.
- Timer-triggered auto-submit bypasses this confirmation and continues to finalize immediately.
