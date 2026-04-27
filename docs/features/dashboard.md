# dashboard.md - NoteLib Feature Context

## Goal

Dashboard helps users decide what to do next without turning into a management page.

## Personalization Prompt

After onboarding completes, Dashboard should show a lightweight personalization card near the top of the page.

- title: `Make NoteLib work better for you`
- description: `Set your learning style and reminders in seconds.`
- primary action: `Set Preferences` -> `/settings`
- secondary behavior: dismissible, with dismissal stored per user on the frontend when backend persistence is not required

This prompt is a post-onboarding nudge. Learning Style and Study Reminders should not move back into onboarding.

## Profile-specific priorities

- `Student` dashboard should prioritize `Continue Studying`, `Weak Concepts`, `Recent Notes`, `Quick Review`, and `Usage / Progress`.
- `Board Taker` dashboard should prioritize `Exam Countdown`, `Start Board Exam`, `Weak Areas`, `Adaptive Practice`, and `Usage / Progress`.
- `Teacher` dashboard should prioritize `Create Teaching Material`, `Recent Notes`, `Recently Generated Quizzes`, `Ready to Export`, and `Teacher Help / Tips`.

## Teacher Dashboard

Teacher uses the same note and Study Pack product, but with different intent.

Rules:

- keep the shared note / Study Pack workspace visible through `Recent Notes`
- do not show student analytics sections such as performance overview, recent quiz sessions, weak concepts, or score widgets
- generated-quiz links should open Quiz Preview, not student quiz-session routes
- export remains inside Quiz Preview; Dashboard should only route into preview-ready notes

Teacher sections:

- `Create Teaching Material` -> primary CTA `Create Note`
- `Recent Notes` -> latest notes and Study Packs
- `Recently Generated Quizzes` -> quick links to Quiz Preview pages
  - when no generated quiz exists yet, the empty state should direct teachers to a recent ready note when available; otherwise it should fall back to `Create Note`
- `Ready to Export` -> spotlight for generated quizzes that are ready to open in Quiz Preview
- `Teacher Help / Tips` -> lightweight workflow guidance
- dashboard welcome copy should sound teacher-first in `Teacher` mode and point users toward note -> Study Pack -> Quiz Preview

## Continue Studying

Endpoint:

- `GET /dashboard/continue-studying`

The response should give the dashboard enough information to render the card in one pass.

Required fields:

- `noteId`
- `noteTitle`
- `subject`
- optional `courseProgram`
- `resumeType`
- `currentQuestionIndex`
- `totalQuestions`
- `lastReviewedAt`
- optional `summaryPreview`

Card structure:

- `KEEP IT SHARP`
- status chip such as `In Progress`
- resume label from `resumeType`
- prominent note title
- `Subject • Course / Program` metadata line when available
- progress copy such as `You left off on Question X of Y.`
- `Last reviewed` or `Last opened`
- full-width resume action on mobile

Resume label rules:

- `QUICK_REVIEW` -> `Resume Quick Review`
- `CHALLENGE` -> `Resume Challenge Quiz`
- `ADAPTIVE` -> `Resume Adaptive Practice`

Guardrails:

- do not make extra frontend API calls just to label the card
- keep the card note-first; users should know which note they are returning to immediately
- keep the card compact on mobile
- teacher-specific generated-quiz lookups are allowed when needed to surface Quiz Preview links, but they must stay note-owned and must not create quiz-session side effects
