# shareable-quiz-links.md - NoteLib Feature Context

## Goal

Shareable Quiz Links let any onboarded user give a generated quiz to someone through a public `/quiz/{token}` URL. The recipient can take the quiz without creating an account, see server-validated results at the end, and then sign up to keep studying.

## Sharing Flow

An onboarded user generates a quiz from a note, reviews it on the quiz preview page, then uses the `Share Quiz` section to create a public link for the person they are helping.

Rules:

- link generation is available to any onboarded user; it is not gated by profile type
- share links are created from `GeneratedQuizEntity`
- each link uses a 16-character URL-safe token stored in `quiz_share_links`
- the quiz owner can toggle a link on or off from the preview page
- inactive or missing links return `404` from the public quiz endpoint

## Anonymous Recipient Play

Recipients open `/quiz/{token}` without authentication.

Rules:

- the initial public quiz response includes `question`, `choices`, and `concept`
- the initial response must not include `correctIndex` or `explanation`
- no `QuickReviewSessionEntity` or score/session rows are created
- answers stay client-side until the recipient submits the quiz
- `/api/quiz/share/{token}/results` accepts the answer indexes and returns the score, correct indexes, and explanations for review
- the results screen prompts signup instead of persisting anonymous performance

This keeps public play lightweight and avoids creating student-owned history before authentication.

## Quotas

Shareable quiz links are plan-gated per billing month:

- Free: `3` links / month
- Plus: `10` links / month
- Pro: unlimited

Runtime tracking uses `user_usage.quiz_share_links_created`. The quiz preview page shows neutral upgrade guidance when the quota is exhausted and resolves its actions through `getUpgradeCtas(currentPlan)`.

### ⚠️ Generating the quiz is metered separately from sharing it

**Two different meters apply, and exhausting either one blocks a different step.** The limits above cover creating the *link*. Producing the quiz that a link points at is a real LLM call and is metered on its own:

- **It draws down the user-facing “Quiz generations” allowance.** `GeneratedQuizService.assertQuizCreditAvailable` reads `user_usage.challenge_quiz_generations` against `resolveMonthlyChallengeQuizLimit` — **Free `20` / Plus `100` / Pro `200`** per month — and `incrementChallengeQuizGeneration` increments that same counter on success. **⚠️ Making a quiz for someone therefore consumes the same monthly allowance as the user's own Challenge Quiz generations.** Exhausting it raises `MonthlyQuizCreditLimitReachedException`.
- **It consumes an AI rate-limit slot** under its own scope, `"generated-quiz"` (`GeneratedQuizService:53`), which is separate from the share-link quota and from other AI scopes.

The shared meter is disclosed before generation as **“Quiz generations”**, described as “Quiz sessions we generate for you, plus quizzes you make for someone. Board Exam sessions also count against their own allowance.” The same pre-generation view shows share links remaining. If links are exhausted, generation remains available for export or later sharing; `QuizShareLimitService` still enforces the share-link cap only when a link is created. No separate generation counter exists, and no quota behaviour changed.

**Question count is a separate axis again.** `resolveQuestionCount` returns the default `10` for every non-`TEACHER` profile regardless of plan; only a `TEACHER` may choose another count, and a `TEACHER` on `FREE` is refused any non-default value with `QuestionCountNotAllowedForPlanException`.

### The shared quiz is generated, never reused

The quiz behind a share link is a **fresh LLM generation** stored in `GeneratedQuizEntity`. It is not the Study Pack quiz that Quick Review administers (`note.quiz`), not the Challenge Quiz bank, and not an exam question pool.

That separation is deliberate and load-bearing in both directions:

- the shared quiz carries its own **Target Level** and question count, so it can be aimed at the recipient's depth rather than the owner's;
- regeneration passes the previous shared quiz's questions as `disallowedQuestions`, so giving the same person a second quiz yields new questions;
- **⚠️ reusing `note.quiz` would hand the recipient the exact questions the owner is assessed on** — the answer-key exposure `v0.74.0` locked the Quiz tab to prevent. Do not "optimise" this path by reading from the Study Pack quiz.

## Deferred

The following are intentionally out of scope for v0.16.0:

- response tracking
- recipient response summaries
- anonymous or authenticated score persistence for shared-link plays
- recipient identity collection
- quiz-session history entries for shared-link plays
