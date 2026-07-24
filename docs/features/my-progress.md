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

Adopted study plans need no special Progress path. Adoption snapshot-copies public notes and linked Study Packs into owned notes, so all practice in an adopted plan writes the same owned-note ConceptHealth signals as any manually created Study Plan.

Aggregation path:

`concept_health.studyPackId -> study_packs.subject + study_packs.keyConcepts`

The subject label comes from the Study Pack. Packs with a null or blank subject are grouped under `Other`.

ConceptHealth write sources:

- Challenge Quiz and Adaptive Practice record fully-correct concepts to `lastCorrectAt` and missed concepts to `lastIncorrectAt`.
- Long Exam and Board Exam record fully-correct and missed concepts on normal completion only when the effective recording concept exactly matches a source Study Pack's `keyConcepts`.
- Interview Practice records the same exact-match correct and missed ConceptHealth signals across its primary and additional source Study Packs.
- Quick Review does not record to `ConceptHealth`; it is a refresh-only review mechanic and cannot move mastery, due-state, or `Overall Readiness`.
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

## Plan / Goal Readiness Cross-Reference

`GET /collections/{id}/readiness` reuses the same ConceptHealth spine and `ProgressReportService` classification for a single owned collection's Study Packs. For the same concept set, plan readiness must match `/me/progress`: mastered, due, not started, and `masteryPercentage` use the same rules and thresholds.

Plan readiness now lives inside the canonical `/progress` route as the plan-scoped view at `/progress?collectionId={id}`. The in-page picker is the primary way to switch between `All subjects` and one saved leaf/Subject plan, and the plan detail `Check readiness` CTA deep-links to the same query-param view. The frontend still calls `GET /collections/{id}/readiness` for the scoped payload; only the rendering location moved.

When `/progress` loads with no explicit `?collectionId=`, the frontend resolves `primaryCollectionId` from `GET /auth/me`. If a Primary Review Set exists, the page applies it as a one-time default selection; if profile loading fails or no primary exists, the page stays on the all-subjects rollup. Once that first resolution is complete, the picker owns navigation normally, so choosing `All subjects` pushes `/progress` and must not snap back to primary.

Primary Review Set ids are always top-level collections, never child Subject plans. The primary-default path therefore uses `GET /collections/{id}/goal` (`getCollectionGoal`) and renders the aggregate readiness fields from `GoalCollectionDetailResponse`: `overallReadinessPercentage`, `totalConcepts`, `masteredConcepts`, `dueConcepts`, and `notPracticedConcepts`. That response rolls up child Subject plans for a Goal and, for a childless top-level plan, computes the same fields from its own direct items using the shared readiness classification path. Explicit leaf/Subject selections still use `GET /collections/{id}/readiness`.

When no `collectionId` is selected, `/progress` remains the canonical all-subject detail surface for owned Study Packs, goals, milestones, weakest-subject routing, and study-focus guidance. When a `collectionId` is selected, `/progress` renders only the scoped readiness data supported by `PlanReadinessResponse`: overall readiness and per-subject readiness.

Study Pack generation status (`notesWithStudyPack`/`totalNotes`) is not shown as a standing stat on either view — it is a content/authoring status, not a mastery or practice signal, and duplicates the `N/N notes ready` badge already on the plan detail Hero card (see `docs/features/collections.md`). The plan-scoped view shows it only as a conditional caveat when `notesWithStudyPack < totalNotes` (`N of M notes in this {plan} don't have a Study Pack yet, so they aren't reflected below`, linking to `/collections/{id}`), explaining why readiness may look artificially complete for a partially-generated plan. The caveat disappears once every note has a Study Pack.

The Goal-scoped primary default does not show the missing-Study-Pack caveat, because `GoalCollectionDetailResponse` does not include `totalNotes` or `notesWithStudyPack`.

## Note Readiness Cross-Reference

Private Note Detail reuses the same ConceptHealth recency spine for a compact per-note readiness signal. It does not replace `/progress`: Note Detail shows the high-traffic signal for one note (`% ready`, mastered count, due count, and not-started count), while `/progress` remains the canonical subject-level detail surface.

The note signal is visible to Free users, matching `/progress` availability. Per-concept review timing remains a PLUS/PRO detail and must not leak through `daysSinceReview`, `lastCorrectAt`, or `lastIncorrectAt` for Free concept-health responses.

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
- An in-page picker with `All subjects` plus leaf/Subject plans from `listCollections()` filtered to `childCount === 0`; selecting a plan updates `/progress?collectionId={id}` without a full reload. If the one-time Primary Review Set default selects a top-level Goal, the picker includes that selected Goal as the current value so its real title displays, but the selectable plan list remains leaf/Subject plans.
- Goal summary header when a study goal is set.
- Goal milestone card between the goal summary header and `What to study next` card when `goalSummary.totalConcepts > 0`.
- `What to study next` routes `SUBJECT_FOCUS` goals to `/library?subject={weakestGoalSubject}` when a weakest subject exists, otherwise `/library`; other goal types without a resolvable exam slug route to `/library?cp={studyGoal}` — the learner's own private Library, not Public Library, since this is a personal "study this next" action, not discovery.
- A per-subject readiness row (`SubjectMasteryRow`, local to `progress-report-client.tsx`) with `X% ready` or `Not started` shown inline with the subject name (`items-center`, same text size, so a long status word never floats disconnected from a taller title block) and a trailing muted `ChevronRight`, plus a one-line caption with total/mastered/due/not-started counts. Each row links to `/library?subject={subject}` (private Library, filtered). The clickable surface itself is the wrapping `Link`, styled with the shared `getBrowsingCardClassName()` utility from `lib/clickable-card.ts` (the same treatment as the Library note-card grid) — not `ReadinessBar`'s accent-bordered `Card`. `SubjectMasteryRow` itself renders a bare fragment; padding, background, border, shadow, hover, and press states all live on the `Link`. This is a denser, purpose-built layout distinct from the shared `ReadinessBar` card used on Collection Detail and Note Detail — this page can list every tracked subject (15+ is common), while those two surfaces show a handful at most.
  - **Why not `ReadinessBar`'s look, and why not a left-accent stripe:** an earlier pass matched `ReadinessBar`'s styling (accent-bordered `Card`, `hover:opacity-80`) and hit two real regressions in sequence — the accent stripe's visual weight read as misaligned against the plain cards above it (`GoalMilestonesCard`/`NextStudyCard`), and once that stripe was simply removed, the resulting flat gray-on-white card with a hover-only cue had no resting-state signifier that it was clickable at all (confirmed by user report; ~75% of sessions are mobile/touch, where `:hover` never fires). `getBrowsingCardClassName()` plus the chevron fixes both: a raised white surface with real border/shadow contrast reads as an actionable object at rest, and the chevron is a platform-conventional disclosure indicator that needs no interaction to be understood. Do not reintroduce a left-accent stripe on this row, and do not rely on `:hover` as the primary clickability signal.
  - **`min-w-0` is required on the `Link`, and the subject `h3`.** Each is a flex or grid item; without an explicit `min-width: 0`, a flex/grid item's default `min-width: auto` refuses to shrink below its content's intrinsic width, so `truncate` never actually engages on a long subject name — the row (and the whole page) grows wider than the viewport instead of clipping text. This exact regression shipped once (long subject names pushed the Concept Mastery section past the header bar) because an earlier compaction pass dropped the `min-w-0` wrapper the original card had around its title block. `page.test.tsx`'s "truncates a very long subject name" test guards this — jsdom can't detect real overflow, so the test only asserts the classes are present, not that layout stays bounded; treat a removal of `min-w-0` from either element as a regression even if that test still passes.
  - **The status label and the chevron must be grouped in their own flex child, not left as siblings of the title under `justify-between`.** The header row has `justify-between`; with only two children (title, status+chevron group) that correctly pins the group to the right edge. Flattening it back to three direct children (title, status, chevron) breaks this: `justify-between` spaces every adjacent pair evenly, so the status label floats at the horizontal midpoint of the remaining space instead of sitting next to the chevron — and since that midpoint depends on the title's rendered width, the status label lands at a different x-position on every row (visibly reported by a user screenshot showing inconsistent alignment down the list). `page.test.tsx` asserts the status label and the chevron share an immediate parent and that parent isn't the `justify-between` row itself.
- Plan-scoped mode (`?collectionId={id}`): overall readiness summary and per-subject readiness from `GET /collections/{id}/readiness`; goal summary, milestones, next-study, and study-focus cards are intentionally absent. A conditional caveat (`N of M notes in this {plan} don't have a Study Pack yet, so they aren't reflected below`, linking to `/collections/{id}`) shows only when `notesWithStudyPack < totalNotes`; there is no standing Study Pack coverage stat.
- Primary Goal-scoped default mode (no explicit `?collectionId=`, primary exists): compact overall readiness from `GET /collections/{id}/goal`; no per-subject breakdown and no missing-Study-Pack caveat because the Goal aggregate response does not expose those fields.
- Empty state: `No study packs with concepts yet. Generate a Study Pack to start tracking your progress.`
- No-goal actionable state: when `goalSummary` is null but subject progress exists, show `Set your study focus`, up to 5 weakest subject chips linking to `/profile#study-focus`, and an `Or set from Profile →` link.
- Inline load failure state: `Could not load your progress report. Try refreshing.`
- Plan-scoped failure state: `Could not load readiness` with retry; missing, deleted, or not-owned plans render the collection not-found state with a link back to `/collections`.
- Back link: when the page is reached via a specific collection's `?collectionId={id}` URL param (e.g. its "View full progress" link), the back link returns to that collection (`/collections/{id}`, profile-aware label via `labels.singular`) instead of `/dashboard`. Keyed off the URL param, not live selection state, so a plain nav-in that defaults to the user's Primary Review Set still shows the `/dashboard` back link.

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

The first unreached milestone is the active next target. A `Next: {milestone label}` summary appears above the marker grid so zero-progress states remain legible without relying on the ring highlight; after every milestone is reached, it reads `All milestones reached`. Completed milestones use a filled marker, the next target uses an outlined marker with a ring highlight, and future milestones use muted styling. The milestone progress bar fills by reached checkpoint count out of the six fixed milestones.

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
