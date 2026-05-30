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
- Free and Plus users see the same weak concepts and get a `Revisit Note` link to the source note so they can review material even without Adaptive Practice access
- The upgrade prompt (`Unlock Adaptive Practice`) is shown only when no source note is resolvable

## Community Notes Section

A section visible to all profile types, placed below Recent Notes.

**Section title**: "Notes for [CourseProgram]" — e.g. "Notes for PNLE", "Notes for NMAT"
**Data source**: `GET /notes/public?courseProgram=<value>&size=4` — the same endpoint as the Public Library
**Footer link**: "See all in Public Library →" navigates to `/public/library?courseProgram=<value>`

Behavior by state:

- `courseProgram` is set and matching public notes exist → show up to 4 note cards using the shared public library card layout; clicking a card navigates to the canonical public note detail page
- `courseProgram` is set but no matching public notes exist → hide the section entirely; do not show an empty state
- `courseProgram` is not set → render a placeholder card with a modal CTA:
  - Modal title: "Set your Course/Program"
  - Modal body: "Set your Course/Program to see public notes tailored for your review track."
  - Primary CTA: "Go to Learner Profile" (navigates to `/profile#learning-profile`)
  - Secondary CTA: "Cancel"

Applies to STUDENT, BOARD_EXAM, TEACHER, and PROFESSIONAL profile types.

## First-study guidance

Dashboard may also show first-study guidance for verified users who still have `studyPackCount == 0` and have not completed the separate product-onboarding tracker.

That flow is separate from `/onboarding` and is tracked with `productOnboardingCompletedAt`.
