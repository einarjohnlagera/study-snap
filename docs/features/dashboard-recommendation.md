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

In-progress session priority (when multiple modes have an active session):

1. Challenge Quiz — highest priority; surfaces over any more recently started Quick Review
2. Adaptive Practice
3. Quick Review

Within the same mode, recency is the tiebreaker.

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

- Users with Adaptive Practice access and remaining quota can get `Practice Weak Concepts` → Adaptive Practice on the source note
- When Adaptive Practice is unavailable or quota is exhausted, recommendations should fall back to a safe note-review path or the shared upgrade flow rather than implying the user has no next step
- The `Unlock Adaptive Practice` paywall button appears only when weak concepts exist but no source note can be resolved (`practiceNoteId` is null)

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
- Adaptive Practice usage for plans with a positive monthly Adaptive Practice limit

Guardrails:

- do not show OCR usage

## Today's Focus

Endpoint:

- `GET /dashboard/today-focus`

Recommended response shape:

```json
{
  "type": "RESUME_REVIEW | RETRY_REVIEW | PRACTICE_WEAK_CONCEPT | REVIEW_PACK | STUDY_SUGGESTION",
  "noteId": "uuid-or-null",
  "title": "string",
  "message": "string",
  "actionLabel": "string"
}
```

Priority order:

1. Resume unfinished review
2. Retry incorrect questions
3. Practice weak concepts (plans with Adaptive Practice allowance and remaining quota)
4. Review a specific note
5. Study suggestion when no notes exist

Routing guidance:

- `RESUME_REVIEW` -> Note Detail Quick Review path
- `RETRY_REVIEW` -> Note Detail Quick Review path
- `PRACTICE_WEAK_CONCEPT` -> Note Detail Adaptive Practice path
- `REVIEW_PACK` -> Note Detail path
- `STUDY_SUGGESTION` -> New Note flow

Quota guidance:

- Free users may receive `PRACTICE_WEAK_CONCEPT` while their monthly Adaptive Practice allowance remains
- exhausted quota should route to the quota-aware fallback or upgrade flow, not a dead-end start

## Mastery Snapshot

Data source:

- completed Quick Review sessions only

Metrics:

- average recent score
- best recent score
- notes reviewed

Empty state:

- prompt user to complete first Quick Review

Session-history alignment:

- dashboard recommendations may point users back to Note Detail, but detailed answer review now lives on the dedicated note session-review page reached from `Recent Sessions`
- recommendation logic should continue using persisted quiz-session data only; do not generate review history or concept summaries through LLM calls
- weak-concept continuity should stay aligned with note-level review surfaces so users see the same `< 60%` weak-concept threshold across dashboard follow-ups and session review

## Recommendation Guardrails

- Keep copy calm and non-judgmental.
- Keep actions explicit (`Resume Review`, `Practice Weak Areas`, `Open Note`).
- Free users may receive Adaptive Practice recommendations while their monthly allowance remains; when quota is exhausted, route to the quota-aware fallback or upgrade flow instead of a dead-end start.
- Keep recommendation logic non-blocking; dashboard still renders if recommendation data is unavailable.
