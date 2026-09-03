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

Overall Continue Studying recommendation priority:

1. `RESUME_REVIEW` — resume the highest-priority unfinished session
2. `LOW_SCORE_RECENT` — revisit the weakest recently reviewed Study Pack
3. `SUGGESTED_CHALLENGE` — invite an eligible idle user to try Challenge Quiz
4. `RECENTLY_OPENED` — return to the most recently opened Study Pack
5. `RECENTLY_CREATED` — start with the most recently created Study Pack

`SUGGESTED_CHALLENGE` is returned only when all of these hold:

- the profile is `STUDENT` or `BOARD_EXAM`
- at least one owned Study Pack is quiz-ready and linked to a note
- no Challenge-mode session exists for the user, regardless of whether it was started, completed, failed, or forfeited
- the effective monthly Challenge Quiz usage is below the backend pricing limit for the resolved plan

The target is deterministic: prefer the most recently opened eligible Study Pack, otherwise the most recently created eligible Study Pack. The response uses `resumeType = CHALLENGE`, but the frontend renders try-it copy (`Try Challenge Quiz`) rather than resume copy. Eligibility/query failures fall through to the existing passive recommendation chain and never break Dashboard rendering.

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

- Focus Areas and weak-area recommendations read persisted weak-concept/session signals plus the shared ConceptHealth spine. They do not depend on a same-session Quick Review `ConceptHealth` write; Quick Review's own retry/challenge next step reads session metadata, while ConceptHealth-backed weakness is fed by assessment modes.
- Users with Adaptive Practice access and remaining quota can get `Practice Weak Concepts` → Adaptive Practice on the source note
- **As of `v0.107.0`, a second action sits beside it: `Practice Across This Plan`**, which starts a
  plan-scoped Adaptive Practice session over the whole Subject Plan or Review Set. It renders **only
  when the server resolved a plan** (`focusAreas.practiceCollectionId`); the client never picks one,
  so the button cannot disagree with the session the server would actually start.
- **Which plan is resolved by `v0.78.0`'s existing rule**, not a new one: the learner's Primary Review
  Set when it contains the weakest note, otherwise the most recently updated containing collection
  (`updatedAt desc, id asc` — a deterministic total order). **⚠️ This is deliberately NOT a weakness
  ranking; ordering plans by how weak they are is the recommendation engine's unratified scope.**
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
- **`INTERVIEW_PRACTICE` resume (v0.108.0)** — an in-progress Interview Practice session now
  surfaces as its own *Continue Studying* card routing to `/notes/{id}/interview-practice`, not as an
  Adaptive Practice card. Three session types share the `ADAPTIVE` discriminator, so a resume type
  derived from session MODE alone could not tell them apart. **It is routed rather than hidden — the
  learner has an active session and this is their way back to it.**
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
