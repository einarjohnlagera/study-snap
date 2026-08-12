# quick-review.md - NoteLib Feature Context

## Goal

Quick Review is the lightweight, low-pressure quiz mode for a Study Pack-ready note.

It should feel:

- fast
- repeatable
- encouraging
- clearly secondary to Challenge Quiz for exam-style practice

## Availability

- available on Free, Plus, and Pro
- uses the base Study Pack quiz instead of a separate LLM-generated session

## Core flow

Study Pack-ready note
-> Quick Review
-> answer feedback (see CTA rules below)
-> one retry round for incorrect questions
-> result screen
-> session history / next-step guidance

## CTA behavior per question type

Single-select and matching questions:
- answer reveals immediately on selection
- explanation shows immediately
- button label: "Next" (or "Finish Quick Review" / "Finish Retry" on last question)

Multi-select questions (MULTI_SELECT format):
- choices remain in a selectable state until user explicitly submits
- button label: "Submit" (disabled until at least one choice is selected)
- clicking "Submit" reveals answer highlight and explanation
- button then changes to "Next" / "Finish Quick Review" / "Finish Retry"
- this two-step pattern applies regardless of question position

## Session model

Quick Review uses its own session model:

- one in-progress session per user per note
- completed sessions remain reviewable from Note Detail
- session history stays note-owned

## Verified score and Study Pack quiz mastery

- Quick Review still stores the client-reported `correctAnswers`, `totalQuestions`, and score percentage for the existing results and history UI.
- Completion additionally stores a nullable server-derived `verifiedCorrectAnswers`. It reuses the persisted cumulative selection map and the shared per-question breakdown scorer; it does not trust the reported score for mastery.
- Retry answers overwrite the same persisted question index, so a learner who corrects every miss through the retry round ends with a verified perfect score and qualifies for mastery. **The button is labelled `Retry Incorrect Questions`** (`quick-review/page.tsx`). The `v0.74.0` brief and its owner rulings call this "Redo Mistakes" — that name exists in planning documents only and **must never be used in learner-facing copy**; Challenge Quiz separately has a `Redo Missed Questions` action, which is a different thing.
- A learner has mastered a Study Pack's Quick Review only when one of their own completed `QUICK_REVIEW` sessions has `verifiedCorrectAnswers` equal to the Study Pack's current non-empty quiz size. A reported 4/5 does not qualify, and non-Quick-Review modes never qualify.
- Mastery is per learner and per Study Pack. Copies and remixes do not inherit the source owner's session history or mastery.
- Regeneration deliberately re-evaluates old sessions against the regenerated Study Pack's current quiz size. If the question count changes, an earlier perfect session may stop qualifying because it describes a different question set.
- `quizMastered` and nullable `quizMasteredAt` are server-resolved response fields. A mastery lookup failure fails closed to `false` without hiding the Study Pack.
- This foundation does not lock or render anything by itself; the Quiz-tab progression UI is separate v0.74.0 work.

## Result screen

Current Quick Review result behavior is intentionally simplified around one server-resolved next step.

For a learner's first-ever completed quiz — Quick Review or Challenge Quiz, determined from activity history — an incomplete concept set uses an open-loop header instead of `Your results`: `N of M concepts secured`, followed by `The rest are best reviewed tomorrow — you're not done yet.` The standard framing remains for perfect first scores, sessions without tagged concepts, and all later completions. This changes only the result framing: scores, concept-health behavior, and next-step CTAs stay unchanged.

Primary CTA rules:

> **Why Challenge Quiz is the primary action here at >= 4/5** (rather than only at 5/5, and rather than the entry point being moved or enlarged) is a recorded product decision — see [`docs/product/CHALLENGE_QUIZ_ADOPTION.md`](../product/CHALLENGE_QUIZ_ADOPTION.md). The rules below remain the source of truth for the behavior; that document explains the reasoning and carries the validation reads that were deferred.

- after completion, the page fetches `GET /study-packs/{studyPackId}/next-step`
- the shared `<PostSessionNextStep>` component renders the dominant next action
- a Quick Review with two or more missed concepts prioritizes `Retry Incorrect Questions` and keeps Challenge Quiz available as a secondary action
- a strong-majority Quick Review (at most one missed concept, i.e. >= 4/5) advances primarily to `Take a Challenge`; when there is a single miss, `Retry Incorrect Questions` is kept as a secondary action so the missed question is not lost
- a Quick Review with no misses advances primarily to `Take a Challenge`
- genuine weak concepts may keep `Practice Weak Concepts` reachable as a secondary action after a strong Quick Review, but never replace Challenge Quiz as the primary action
- fallback UI keeps the previous weak-area / challenge / retry guidance when the next-step fetch fails

Secondary actions:

- `Review Answers`
- optional Pro upsell when the weak-area action is locked
- `← Back to Note`

## Confidence and learner level

Quick Review keeps these as secondary actions on the result screen:

- confidence feedback
- learner-level adjustment pills

Current learner-level save toast:

- `Learner level updated. Future Study Packs and quizzes will match this level.`

Meaning:

- learner-level changes affect future generations and future quiz difficulty
- they do not regenerate the current Study Pack

## Weak concepts and Adaptive Practice

- weak concepts are visible to all plans after completion
- post-session genuine weakness is the capped union of reviewed concepts that have decayed due and concepts actually missed in the completed session
- never-reviewed concepts are `not started`, not genuine weakness, and do not trigger a weak-area recommendation
- Adaptive Practice is available to Free users up to 3 sessions / month, then opens the shared upgrade flow for more sessions
- when the shared next-step response recommends Adaptive Practice but quota is exhausted, the component shows a plan-aware upgrade CTA instead of routing into the limit wall
- this remains inside the locked `EXAM_MODES.md` hierarchy: Quick Review still guides into Challenge Quiz or Adaptive Practice; weak-area practice is demoted after a strong result, not removed

## ConceptHealth

- Quick Review does not record to `ConceptHealth`.
- Completing Quick Review must not update `lastCorrectAt`, `lastIncorrectAt`, mastery, due-state, struggling state, note readiness, plan readiness, or `Overall Readiness`.
- Quick Review remains a refresh-only mechanic: it keeps immediate right/wrong feedback, one retry round for incorrect questions, result-screen missed-concept copy, and session history from its own session data.
- `Retry Incorrect Questions` / `Take a Challenge` next-step behavior uses the completed session's stored `weakConcepts` metadata, not a `ConceptHealth` write from that same Quick Review session.
- Genuine weak-area secondary recommendations may still read shared `ConceptHealth`, but that spine is fed by assessment modes only: Challenge Quiz, Adaptive Practice, Long Exam, Board Exam, and Interview Practice.
- Existing `ConceptHealth` rows influenced by older Quick Review completions are not backfilled or deleted; they naturally decay once no assessment mode refreshes them.

## Review history

- completed Quick Review sessions are excluded from the visible Note Detail "Recent Sessions" history list (`QuizSessionHistoryService.listRecentSessions` filters out the `QUICK_REVIEW` mode) — Quick Review is a refresh mechanic, not a session worth revisiting in a permanent history list.
- the underlying session row still persists exactly as before: `lastSessionCompletedAt` aggregation (`findLatestSessionCompletedAtByNoteIds`), "Continue where you left off," and practiced-status tracking are unaffected — only the visible history *list* excludes Quick Review, not the data.
- the dedicated session-review page (direct-URL access to a specific completed session's answers) is unaffected and still reachable for a Quick Review session — this is a separate concern from list visibility.
- session review exports use stored session data only
