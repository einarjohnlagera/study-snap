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
- A verified-perfect completion announces `🔓 Quiz Unlocked` beside the result. This is an announcement only: it adds no Quiz-tab link or competing action.
- `QUICK_REVIEW_MASTERED` records whether perfection happened on the first pass or after the retry round. `STUDY_PACK_QUIZ_UNLOCKED` records only the first transition into mastery for that learner and Study Pack.

## Result screen

Current Quick Review result behavior is intentionally simplified around one server-resolved next step.

For a learner's first-ever completed quiz — Quick Review or Challenge Quiz, determined from activity history — an incomplete concept set uses an open-loop header instead of `Your results`: `N of M concepts secured`, followed by `The rest are best reviewed tomorrow — you're not done yet.` The standard framing remains for perfect first scores, sessions without tagged concepts, and all later completions. This changes only the result framing: scores, concept-health behavior, and next-step CTAs stay unchanged.

Primary CTA rules:

> Challenge Quiz promotion now occurs at the same verified-perfect mastery moment as the Quiz unlock. The earlier >= 4/5 experiment and its validation obligation remain recorded in [`docs/product/CHALLENGE_QUIZ_ADOPTION.md`](../product/CHALLENGE_QUIZ_ADOPTION.md).

- after completion, the page fetches `GET /study-packs/{studyPackId}/next-step`
- the shared `<PostSessionNextStep>` component renders the dominant next action
- any non-mastered Quick Review, including exactly one miss, prioritizes `Retry Incorrect Questions` and keeps Challenge Quiz available as a secondary action
- only a Quick Review that satisfies the shared server mastery predicate advances primarily to `Take a Challenge`
- **the predicate is historical, not per-session, and that is deliberate:** it asks whether *any* completed session for this `(learner, Study Pack)` was verified-perfect at the current quiz size. So a learner who mastered a pack earlier keeps the Challenge promotion even after a weaker session today — mastery is sticky until the quiz itself changes. This replaced a per-session `>= 4/5` rule, so the promotion and the Quiz-tab unlock now read the same signal rather than two that can disagree
- genuine weak concepts may keep `Practice Weak Concepts` reachable as a secondary action after mastery, but never replace Challenge Quiz as the primary action
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

**Quick Review DOES record to `ConceptHealth`.** Corrected 2026-08-12 — this section had described the opposite for a month and was wrong.

- On completion, `QuickReviewSessionService` calls `conceptHealthService.recordCorrectAnswers(...)` and `recordIncorrectAnswers(...)` with concepts derived from the **persisted selections**, server-side, via `QuizSessionReviewUtils.computeConceptBreakdownForStoredSelections`. `QuickReviewConceptHealthIntegrationTest` pins this.
- **History, because this axis reversed twice and the doc only tracked the first move.** `01bc89e5` (2026-06-04) fed `ConceptHealth` from Quick Review; `8c7a4821` (2026-07-03) excluded it and rewrote this section; **`6d054bad` (2026-07-11) deliberately restored the write** — *"previously wrote nothing, leaving Free-tier's primary quiz mode with no durable spaced-repetition signal"* — but did not restore this section. The stale text survived until `v0.74.0`.
- **This is load-bearing for `v0.74.0`, not trivia.** That release locks the Quiz tab precisely because the tab is an answer key to the questions Quick Review administers, and Quick Review's score therefore reaches the one signal the product treats as mastery-integrity-bearing. **Do not "restore" the exclusion on the strength of an old document** — it would silently undo `6d054bad` and remove the justification `v0.74.0` was built on.
- Genuine weak-area secondary recommendations read shared `ConceptHealth`, which is fed by Quick Review alongside Challenge Quiz, Adaptive Practice, Long Exam, Board Exam, and Interview Practice.

## Review history

- completed Quick Review sessions are excluded from the visible Note Detail "Recent Sessions" history list (`QuizSessionHistoryService.listRecentSessions` filters out the `QUICK_REVIEW` mode) — Quick Review is a refresh mechanic, not a session worth revisiting in a permanent history list.
- the underlying session row still persists exactly as before: `lastSessionCompletedAt` aggregation (`findLatestSessionCompletedAtByNoteIds`), "Continue where you left off," and practiced-status tracking are unaffected — only the visible history *list* excludes Quick Review, not the data.
- the dedicated session-review page (direct-URL access to a specific completed session's answers) is unaffected and still reachable for a Quick Review session — this is a separate concern from list visibility.
- session review exports use stored session data only
