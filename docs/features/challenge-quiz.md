# challenge-quiz.md - NoteLib Feature Context

## Goal

Challenge Quiz is the timed, exam-style quiz mode built from a Study Pack-ready note.

It is separate from Quick Review:

- separate generation flow
- separate session history
- stricter timing and setup
- result screen designed around next-step practice

## Entry flow

Student and Board Taker both enter through the same shared mode-selection screen first.

Visible modes:

- `Challenge Quiz`
- `Board Exam Mode`

Default emphasis:

- `Student` -> Challenge Quiz
- `Board Taker` -> Board Exam Mode

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
