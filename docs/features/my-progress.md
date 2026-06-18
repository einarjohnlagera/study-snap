# My Progress

## Purpose

My Progress gives learners a subject-level view of ConceptHealth mastery across all owned Study Packs. It answers, "Where am I strongest, and what still needs review?" using the same per-concept recency spine that powers due-concept selection, plus a weakness signal for concepts the learner most recently missed.

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
    "goalType": "SUBJECT_FOCUS",
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

`goalSummary.goalType` values:

- `EXAM`: `studyGoal` is an exam slug such as `ale`, `pnle`, or `let`.
- `SUBJECT`: `studyGoal` is a course-program goal from the older Profile picker flow.
- `SUBJECT_FOCUS`: `studyGoal` is null and `focusSubjects` contains one or more saved subjects.

Goal priority:

1. Exam slug in `studyGoal`.
2. Course-program value in `studyGoal`.
3. Subject-level `focusSubjects`.
4. No goal summary when both are empty or no selected focus subject matches tracked Study Packs.

## Data Source

The report is ConceptHealth-only. It does not read quiz-session `conceptBreakdown`, accuracy percentages, weak-concept lists, or dashboard Focus Areas data.

Aggregation path:

`concept_health.studyPackId -> study_packs.subject + study_packs.keyConcepts`

The subject label comes from the Study Pack. Packs with a null or blank subject are grouped under `Other`.

ConceptHealth write sources:

- Quick Review, Challenge Quiz, and Adaptive Practice record fully-correct concepts to `lastCorrectAt` and missed concepts to `lastIncorrectAt`.
- Long Exam and Board Exam record fully-correct and missed concepts on normal completion only when the effective recording concept exactly matches a source Study Pack's `keyConcepts`.
- Interview Practice records the same exact-match correct and missed ConceptHealth signals across its primary and additional source Study Packs.
- New Long Exam and Interview Practice questions carry a separate `keyConcept` field that is schema-constrained to the source pack's key concepts. Recording uses that field first.
- Legacy sessions and pre-warmed pool questions with no `keyConcept` fall back to the existing free-form `concept` field.
- Free-form exam labels that do not match a source pack key concept are dropped before writing, so Progress never depends on invisible orphan ConceptHealth rows.
- Forfeit paths do not record correct or missed concepts.

## Aggregation Rules

- Include all Study Packs owned by the authenticated user.
- Exclude packs where `keyConcepts` is null or empty.
- Group qualifying packs by Study Pack subject; null or blank subjects use `Other`.
- Within each subject, deduplicate key concepts by normalized name: trim whitespace and compare case-insensitively.
- Preserve subjects with no ConceptHealth rows; these show `0%` mastery and all concepts as not started.
- A concept is mastered when any matching ConceptHealth row has `lastCorrectAt` within the current due threshold.
- A concept is due when it has a non-null `lastCorrectAt` but every matching row is stale according to `ConceptHealthService.isDue`.
- A concept is not practiced when there is no matching row or every matching row has `lastCorrectAt == null`.
- A concept is struggling when `lastIncorrectAt != null` and either `lastCorrectAt == null` or `lastIncorrectAt` is newer than `lastCorrectAt`.
- A later fully-correct session updates `lastCorrectAt`, so struggling self-clears without a separate reset or mastery state.
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
- `What to study next` routes `SUBJECT_FOCUS` goals to `/public/library?subject={weakestGoalSubject}` when a weakest subject exists, otherwise `/public/library`.
- A per-subject card with mastery percentage, progress bar, and counts for mastered, due, and not started concepts.
- Empty state: `No study packs with concepts yet. Generate a Study Pack to start tracking your progress.`
- No-goal actionable state: when `goalSummary` is null but subject progress exists, show `Set your study focus`, up to 5 weakest subject chips linking to `/profile#study-focus`, and an `Or set from Profile →` link.
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

## Subject Focus Goals

Subject focus goals are combined rollups over the selected subjects in `focusSubjects`.

Rules:

- They are computed only when `studyGoal` is null and `focusSubjects` is non-empty.
- Matching uses normalized subject lookup values, not generated syllabus or AI target lists.
- `goalName` and `goalLabel` are the single subject name when one subject is selected.
- For multiple subjects, `goalName` and `goalLabel` are `{N} subjects in focus`.
- `studyGoal` in the response is a comma-joined display string of the selected subjects.
- Concept counts and `weakestGoalSubject` use the same ConceptHealth aggregation path as other goal summaries.
- If the selected subjects no longer match any tracked Study Packs, `goalSummary` is null and the Progress page shows the no-goal actionable state.

## Out Of Scope

- No per-concept drill-down.
- No per-subject milestone breakdown.
- No persisted milestone state.
- No independent goal tracks per subject.
- No plan gating.
- No session-accuracy aggregation.
- No rolling accuracy counters, mastery scores, backfills, or separate weakness store.
