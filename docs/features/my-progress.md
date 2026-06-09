# My Progress

## Purpose

My Progress gives learners a subject-level view of ConceptHealth mastery across all owned Study Packs. It answers, "Where am I strongest, and what still needs review?" using the same per-concept recency spine that powers due-concept selection.

The report is available to all plans. It is not gated by Adaptive Practice entitlement.

## Endpoint

`GET /me/progress`

Returns:

```json
{
  "subjects": [
    {
      "subject": "Pharmacology",
      "totalConcepts": 10,
      "masteredConcepts": 7,
      "dueConcepts": 2,
      "notPracticedConcepts": 1,
      "masteryPercentage": 70
    }
  ],
  "goalSummary": {
    "studyGoal": "Pharmacology",
    "goalType": "SUBJECT",
    "goalName": "Pharmacology",
    "goalLabel": "Pharmacology",
    "masteryPercentage": 70,
    "masteredConcepts": 7,
    "totalConcepts": 10,
    "notPracticedConcepts": 1,
    "weakestGoalSubject": "Cardiovascular Pharmacology"
  }
}
```

The response is `200` with an empty `subjects` list when the user has no owned Study Packs with key concepts.

## Data Source

The report is ConceptHealth-only. It does not read quiz-session `conceptBreakdown`, accuracy percentages, weak-concept lists, or dashboard Focus Areas data.

Aggregation path:

`concept_health.studyPackId -> study_packs.subject + study_packs.keyConcepts`

The subject label comes from the Study Pack. Packs with a null or blank subject are grouped under `Other`.

## Aggregation Rules

- Include all Study Packs owned by the authenticated user.
- Exclude packs where `keyConcepts` is null or empty.
- Group qualifying packs by Study Pack subject; null or blank subjects use `Other`.
- Within each subject, deduplicate key concepts by normalized name: trim whitespace and compare case-insensitively.
- Preserve subjects with no ConceptHealth rows; these show `0%` mastery and all concepts as not started.
- A concept is mastered when any matching ConceptHealth row has `lastCorrectAt` within the current due threshold.
- A concept is due when it has a non-null `lastCorrectAt` but every matching row is stale according to `ConceptHealthService.isDue`.
- A concept is not practiced when there is no matching row or every matching row has `lastCorrectAt == null`.
- `masteryPercentage = round(masteredConcepts * 100 / totalConcepts)`.

The due threshold stays owned by `ConceptHealthService`; do not hardcode the day count in progress-report code.

## Sort Order

Subjects sort weakest-first:

1. `masteryPercentage` ascending.
2. Subject name ascending, case-insensitive, for ties.
3. `Other` always last regardless of mastery percentage.

## Frontend

Route: `/progress`

The page renders:

- Header: `My Progress`
- Subtitle: `Concept mastery across your subjects, based on your recent practice.`
- Goal summary header when a study goal is set.
- Goal milestone card between the goal summary header and `What to study next` card when `goalSummary.totalConcepts > 0`.
- A per-subject card with mastery percentage, progress bar, and counts for mastered, due, and not started concepts.
- Empty state: `No study packs with concepts yet. Generate a Study Pack to start tracking your progress.`
- Inline load failure state: `Could not load your progress report. Try refreshing.`
- Back link to `/dashboard`.

The dashboard Focus Areas card links to `/progress` when it has concept rows. The card's existing weak-concept logic and action CTA remain unchanged.

## Goal Milestones

Goal milestones are fixed checkpoints computed from `goalSummary` only. They are not persisted and do not require a separate endpoint, service method, generated syllabus, AI target list, or curriculum progression model.

The milestone card renders only when:

- `goalSummary` is present.
- `goalSummary.totalConcepts > 0`.

Milestones render in this order:

1. `First concept mastered` — `masteredConcepts >= 1`
2. `25% mastered` — `masteryPercentage >= 25`
3. `All concepts reviewed` — `notPracticedConcepts === 0 && totalConcepts > 0`
4. `50% mastered` — `masteryPercentage >= 50`
5. `70% mastered` — `masteryPercentage >= 70`
6. `All concepts mastered` — `masteryPercentage >= 100`

The first unreached milestone is the active next target. Completed milestones use a filled marker, the next target uses an outlined marker with a ring highlight, and future milestones use muted styling. The milestone progress bar fills by reached checkpoint count out of the six fixed milestones.

## Out Of Scope

- No per-concept drill-down.
- No per-subject milestone breakdown.
- No persisted milestone state.
- No goal setting changes.
- No next-subject recommendation changes.
- No plan gating.
- No session-accuracy aggregation.
- No changes to ConceptHealth recording or quiz completion flows.
