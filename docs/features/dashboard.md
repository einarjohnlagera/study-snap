# dashboard.md - NoteLib Feature Context

## Goal

Dashboard is a guidance surface, not a management screen. It should help users decide what to do next in the note -> Study Pack -> quiz loop.

## Current Personalization Prompt

After onboarding completes, Dashboard may show a lightweight learner-level prompt near the top.

Current prompt copy:

- title: `Too easy or too hard?`
- body: `Set your learner level so future quizzes match your study stage.`
- CTA: `Adjust level`

Behavior:

- dismissible
- dismissal stored per user in frontend storage
- CTA navigates to `/profile?from=dashboard#learning-profile`
- when opened from Dashboard, `/profile` shows a `Dashboard` back link instead of the public-profile back link

This prompt is for learner level only. It does not move learning style or reminder preferences back into onboarding.

## Profile-specific priorities

### Student

Dashboard should prioritize:

- `Continue Studying`
- `Focus Areas`
- `Recent Notes`
- `Quick Review`
- `Usage / Progress`

### Board Taker

Dashboard should prioritize:

- `Exam Countdown`
- `Start Board Exam`
- `Weak Areas`
- `Adaptive Practice`
- `Usage / Progress`

If Adaptive Practice is locked for the current plan, the CTA should open the shared Pro paywall instead of navigating to a dead-end route.

### Teacher

Dashboard should prioritize:

- `Create Teaching Material`
- `Recent Notes`
- `Recently Generated Quizzes`
- `Ready to Export`
- `Teacher Help / Tips`

Teacher sections should stay quiz-preview and export oriented, not student-session oriented.

## Continue Studying

Endpoint:

- `GET /dashboard/continue-studying`

Required payload shape for the UI:

- `noteId`
- `noteTitle`
- `subject`
- optional `courseProgram`
- `resumeType`
- `currentQuestionIndex`
- `totalQuestions`
- `lastReviewedAt`
- optional `summaryPreview`

Current label mapping:

- `QUICK_REVIEW` -> `Resume Quick Review`
- `CHALLENGE` -> `Resume Challenge Quiz`
- `ADAPTIVE` -> `Resume Adaptive Practice`

Rules:

- use the backend `resumeType` label directly
- do not make extra frontend fetches just to label the card
- keep the card note-first so the user knows which note they are returning to

## Focus Areas

Focus Areas should show weak concepts for all learners when data exists.

Current action behavior:

- Pro users can launch Adaptive Practice from the suggested note
- Free and Plus users see the same weak concepts, but the CTA opens the shared Pro upsell flow

## First-study guidance

Dashboard may also show first-study guidance for verified users who still have `studyPackCount == 0` and have not completed the separate product-onboarding tracker.

That flow is separate from `/onboarding` and is tracked with `productOnboardingCompletedAt`.
