# dashboard.md - NoteLib Feature Context

## Goal

Dashboard helps users decide what to do next without turning into a management page.

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
