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

## Deferred

The following are intentionally out of scope for v0.16.0:

- response tracking
- recipient response summaries
- anonymous or authenticated score persistence for shared-link plays
- recipient identity collection
- quiz-session history entries for shared-link plays
