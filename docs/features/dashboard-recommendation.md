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
2. New Note
3. Today's Focus
4. Recent Notes
5. Mastery Snapshot
6. Your Stats
7. View All in Library (`/library`)

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
