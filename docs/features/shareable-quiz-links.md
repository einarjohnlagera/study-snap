# shareable-quiz-links.md - NoteLib Feature Context

## Goal

Shareable Student Quiz Links let Teacher and Admin users distribute generated quizzes through a public `/quiz/{token}` URL. Students can take the quiz without creating an account, see server-validated results at the end, and then sign up to keep studying.

## Teacher Flow

Teachers generate a quiz from a note, review it on the quiz preview page, then use the `Share Quiz` section to create a student link.

Rules:

- link generation is available only to Teacher-profile users and Admins
- share links are created from `GeneratedQuizEntity`
- each link uses a 16-character URL-safe token stored in `quiz_share_links`
- teachers can toggle a link on or off from the preview page
- inactive or missing links return `404` from the public quiz endpoint

## Student Anonymous Play

Students open `/quiz/{token}` without authentication.

Rules:

- the initial public quiz response includes `question`, `choices`, and `concept`
- the initial response must not include `correctIndex` or `explanation`
- no `QuickReviewSessionEntity` or score/session rows are created
- answers stay client-side until the student submits the quiz
- `/api/quiz/share/{token}/results` accepts the answer indexes and returns the score, correct indexes, and explanations for review
- the results screen prompts signup instead of persisting anonymous performance

This keeps public play lightweight and avoids creating student-owned history before authentication.

## Quotas

Shareable quiz links are plan-gated per billing month:

- Free: `3` links / month
- Plus: `10` links / month
- Pro: unlimited

Runtime tracking uses `user_usage.quiz_share_links_created`. The teacher preview page should show a teacher-framed upgrade nudge when the quota is exhausted and must use `getUpgradeCtas(currentPlan, { profileType: "TEACHER" })`.

## Deferred

The following are intentionally out of scope for v0.16.0:

- response tracking
- teacher response summaries
- anonymous or authenticated score persistence for shared-link plays
- classroom roster or student identity collection
- quiz-session history entries for shared-link plays
