# dashboard-recommendation.md - NoteLib Feature Context

## Goal

Dashboard recommendations should guide users to the best next study action in a note-first workflow.

Dashboard is guidance-only:

- no destructive actions
- no note deletion
- primary path is continuation and review

## Product Context

- Notes are primary records.
- Study Pack content is generated state attached to a Note.
- Recommendation payloads should resolve actions through `noteId`.
- Any legacy `studyPackId` field is compatibility-only.

## Dashboard Sections (Current)

1. Greeting
2. Resume Study
3. Performance Summary
4. Focus Areas
5. This Week
6. This Month
7. Recent Notes
8. View All in Library (`/library`)

## Resume Study

Endpoint:

- `GET /dashboard/continue-studying`

Behavior:

- Reuses the existing continue-studying recommendation logic
- Keeps resume and retry actions routed through `noteId`
- Returns note metadata in the same payload so the card can show `noteTitle`, `subject`, optional `courseProgram`, and `resumeType` without an extra frontend request

## Dashboard Overview

Endpoint:

- `GET /dashboard/overview`

Response includes:

- `performanceSummary`
- `focusAreas`
- `weeklyActivity`

Guardrails:

- Use persisted quiz session and activity-event data only
- Do not call LLM services
- Use Challenge Quiz concept breakdown data for strongest/weakest concept and weak-area accuracy
- Weekly activity should come from activity logs

## Performance Summary

Display:

- average quiz score
- total quizzes taken
- study packs created
- strongest concept
- weakest concept

Data sources:

- completed Quick Review sessions
- completed Challenge Quiz sessions
- persisted concept accuracy from Challenge Quiz session metadata
- existing Study Pack records for created count

## Focus Areas

Display:

- top 3 weak concepts
- concept accuracy percentage
- progress bar

Behavior:

- Premium users get `Practice Weak Concepts`
- Free users get `Unlock Adaptive Practice` and the shared Premium paywall modal

## This Week

Display:

- study packs created
- quizzes taken
- adaptive sessions
- study days

Data source:

- activity logs only

## This Month

Display:

- Study Packs usage
- Challenge Quiz usage
- Adaptive Practice usage for Premium only

Guardrails:

- do not show OCR usage

## Today's Focus

Endpoint:

- `GET /dashboard/today-focus`

Recommended response shape:

```json
{
  "type": "RESUME_REVIEW | RETRY_REVIEW | PRACTICE_WEAK_CONCEPT | REVIEW_NOTE | STUDY_SUGGESTION",
  "noteId": "uuid-or-null",
  "title": "string",
  "message": "string",
  "actionLabel": "string"
}
```

Priority order:

1. Resume unfinished review
2. Retry incorrect questions
3. Practice weak concepts (Premium only)
4. Review a specific note
5. Study suggestion when no notes exist

Routing guidance:

- `RESUME_REVIEW` -> Note Detail Quick Review path
- `RETRY_REVIEW` -> Note Detail Quick Review path
- `PRACTICE_WEAK_CONCEPT` -> Note Detail Adaptive Practice path
- `REVIEW_NOTE` -> Note Detail path
- `STUDY_SUGGESTION` -> New Note flow

## Mastery Snapshot

Data source:

- completed Quick Review sessions only

Metrics:

- average recent score
- best recent score
- notes reviewed

Empty state:

- prompt user to complete first Quick Review

## Recommendation Guardrails

- Keep copy calm and non-judgmental.
- Keep actions explicit (`Resume Review`, `Practice Weak Areas`, `Open Note`).
- For Free users, do not emit adaptive-practice recommendations as actionable starts.
- Keep recommendation logic non-blocking; dashboard still renders if recommendation data is unavailable.
