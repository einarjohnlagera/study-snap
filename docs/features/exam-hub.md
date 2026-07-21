# Exam Hub Feature

## Goal

Exam hubs give board-exam communities a public destination that collects relevant NoteLib public notes by exam context. They are acquisition and discovery surfaces, not a new content model.

## Routes

- `/exam` — static public index for the launch exams.
- `/exam/ale` — Architect Licensure Examination (ALE).
- `/exam/pnle` — Philippine Nurse Licensure Examination (PNLE).
- `/exam/let` — Licensure Examination for Teachers (LET).

Unknown slugs return `notFound()`.

## Wave 1 Set

| Slug | Exam | Included `courseProgram` values |
|---|---|---|
| `ale` | Architect Licensure Examination (ALE) | `Architecture` |
| `pnle` | Philippine Nurse Licensure Examination (PNLE) | `Nursing`, `Medical – Surgical Nursing` |
| `let` | Licensure Examination for Teachers (LET) | `Education` |

The mapping lives in `frontend/lib/exam-hub-config.ts`. Keep curation aliases there as the single frontend source of truth. Do not scatter exam-to-course mappings in route components or UI copy.

## Access Rules

- Exam hubs are public and anonymous-accessible.
- Reading and browsing exam hub notes must not require login.
- There is no profile-type gate. Student, Board Exam, Teacher, Admin, Professional, and anonymous visitors can view the same public exam pages.
- Conversion gates live only on actions such as signup, copy, and quiz flows.
- Exam hubs are curated views over existing public notes. They do not create a new entity, table, content type, or backend endpoint.

## Data Source

Exam hubs reuse existing public-note infrastructure:

- `GET /notes/public` for public note list data.
- Frontend server helpers for ISR-backed fetches.
- `public-library-discovery` for featured, popular, and recent sections.
- Existing public note cards and canonical public note paths.

Filtering is by `courseProgram`, matched case-insensitively against the configured aliases.

## Conversion

Anonymous CTA:

- Label: `Start preparing for the {short exam name}`.
- Destination: `/auth?mode=signup&intent=exam&exam={slug}`.
- The auth page persists the exam slug in the short-lived `notelib-exam-intent` cookie so Track 2 can consume it later.
- The same CTA appears in a zero-note hub state beside the Public Library link. It remains a normal signup link if browser cookie persistence is unavailable.

Authenticated CTA:

- Label: `Browse {courseProgram} Notes`.
- Destination: filtered Public Library URL, using the existing `courseProgram` query slug.

The current feature only preserves exam context. It does not pre-fill goals or change onboarding logic.

## Product Value Strip

Every hub renders a one-paragraph "what NoteLib does for your {exam} review" strip below the header, regardless of note count — exam hubs previously sold the community's notes but never explained the product. It states plainly that NoteLib turns notes into summaries, key concepts, and quiz practice, and tracks weak areas before exam day. No new data source; it's static copy parameterized by `exam.shortName`.

The zero-note empty state additionally links to the matching Learn category (`Read Board Exam study guides` → `/learn#board-exams`, via `buildLearnCategoryPath("board-exams")`) alongside the existing intent-preserving signup CTA and the Public Library link — closing the previously-unbuilt connection between the two purpose-built board-exam surfaces.

## Exam Goals

Track 2 lets authenticated users confirm one study goal using the existing nullable `users.study_goal` field.
Its scope is generalized:

- `goalType = "EXAM"` when `studyGoal` is a configured exam slug (`ale`, `pnle`, `let`).
- `goalType = "SUBJECT"` when `studyGoal` is any other non-blank `courseProgram` value.
- Clearing the goal stores `null`.

`PUT /users/profile/goal` accepts `null` or any non-blank goal string up to 100 characters. Known exam slugs keep
the existing exam flow. Unknown non-slug strings are treated as subject/course-program goals, even if the user has
not created matching notes yet.

Goal suggestion on Dashboard follows this order:

- Existing `studyGoal` from `GET /auth/me` / `GET /me` suppresses the banner.
- Valid `notelib-exam-intent` cookie value suggests that exam first.
- If no valid cookie exists, the frontend infers from `courseProgram` using `getExamSlugForCourseProgram()`.
- If no exam slug resolves and the user is not `BOARD_EXAM`, a non-exam profile with `courseProgram` sees a softer
  subject-focus banner: `Track your progress in {courseProgram}. Set it as your study focus.`

Dashboard goal actions:

- Exam path: `Set as my goal` calls `PUT /users/profile/goal`, clears the intent cookie, fires `STUDY_GOAL_SET`, and hides the banner without a full reload.
- Subject path: `Set as my focus` calls the same endpoint with the raw `courseProgram`, fires `STUDY_GOAL_SET`, and hides the banner without a full reload.
- `Dismiss` clears the intent cookie, fires `STUDY_GOAL_DISMISSED`, and hides the banner without changing the stored goal.
- Goal setting is available to all authenticated profile types; there is no Exam Reviewer-only gate.

When a goal is already set, Dashboard replaces the prompt slot with `DashboardGoalCard` instead of leaving the area
empty. The card loads independently from `GET /me/goal`, which reuses `ProgressReportService.buildGoalNudge()` and
returns nullable `GoalNudgeResponse` data. Dashboard shows a skeleton while this request is pending; if the request
fails or the backend cannot compute the summary, the slot collapses quietly and the rest of Dashboard remains usable.

`DashboardGoalCard` shows:

- `{goalName} Goal`.
- `{goalLabel}`.
- Overall mastery percentage.
- Due goal-concept count, or `All caught up — keep practicing!` when `dueConcepts == 0`.

Dashboard card CTAs:

- `EXAM` goals: `Browse {goalName} notes →` links to `/exam/{studyGoal}`.
- `SUBJECT` goals: `Browse {goalName} notes →` links to `/public/library?courseProgram={studyGoal}`.
- Secondary `View full progress →` always links to `/progress`.

When a goal is set, `/progress` adds a goal summary above the normal subject list. It does not filter the subject
list; all subject progress remains visible. `GoalSummaryResponse` includes `goalType`, `goalName`, and `goalLabel`
so the UI can render both paths without exam-specific DTO fields.

Goal summary computation uses the same mastery aggregation for both paths:

- `EXAM` goals resolve matching `courseProgram` values through `ExamGoalConfig.getCoursePrograms(studyGoal)`.
- `SUBJECT` goals use the raw `studyGoal` value as a single `courseProgram` filter.
- Users with a goal but no matching Study Packs still receive a `0%` goal summary so the confirmed goal remains visible.

The progress report also returns:

- `weakestGoalSubject`, computed from goal-relevant Study Packs by the largest `notPracticedConcepts + dueConcepts` count.
- `userCoursePrograms`, the authenticated user's distinct non-null note `courseProgram` values, ordered alphabetically.

The frontend shows `weakestGoalSubject` in a "What to study next" card:

- `EXAM` goals link to `/exam/{studyGoal}`.
- `SUBJECT` goals link to `/public/library?courseProgram={studyGoal}` so users can browse community notes for that focus area.

Users without a goal can still open `/progress`; Dashboard always shows `View full progress report →`. The progress
page is a read-only mastery view — it does not offer goal-setting UI. Users without a goal see only the subject
mastery cards and a prompt to set a goal from Profile settings.

Goal setting lives in two places only:
- **Dashboard `GoalPromptBanner`**: one-time nudge (exam-intent cookie first, then `courseProgram` fallback); dismissed per session.
- **Profile settings Study Focus card** (`/profile#study-focus`): inline chip picker for all profile types; shows current goal with Change/Clear buttons, or the chip picker when no goal is set or when editing. "Change goal" link on the Progress `GoalSummaryHeader` navigates here.

Post-quiz goal nudges reinforce goals after off-goal practice:

- The existing `GET /study-packs/{id}/next-step` response may include nullable `goalNudge`.
- `goalNudge` is returned only when the authenticated user has a study goal and the completed session's linked note
  `courseProgram` does not match that goal.
- `EXAM` goal matching uses `ExamGoalConfig.getCoursePrograms(studyGoal)`.
- `SUBJECT` goal matching uses direct `courseProgram == studyGoal` equality.
- No nudge is returned when the user has no goal, the current note matches the goal, the Study Pack has no linked note,
  or the nudge computation is unavailable.
- The nudge shows `{goalName} Goal`, `{goalLabel}`, overall mastery percentage, and due goal-concept count.
- The CTA is `View {goalName} progress →` and links to `/progress`.
- v1 surfaces this card below `PostSessionNextStep` on Quick Review, Challenge Quiz, Board Exam Mode score reveal,
  and Adaptive Practice result screens. Long Exam and public quiz flows are excluded.

## Progress Signals

Completing Long Exam, Board Exam, or Interview Practice records concept-level signals into `ConceptHealth`, which is the only store `/progress` reads. The write records fully-correct concepts to `lastCorrectAt` and missed concepts to `lastIncorrectAt`, then intersects those concepts with each source Study Pack's `keyConcepts` before persisting. New Long Exam and Interview Practice questions carry a separate `keyConcept` for Progress recording; it is distinct from the report-facing `concept`, which remains the free-form domain/readiness label used in exam reports. Legacy questions without `keyConcept` fall back to `concept`. Concepts that do not exactly match a source pack key concept are ignored, and missing or unreadable source packs are skipped so completing the exam still returns its report. Forfeit paths do not record either signal.

## Analytics

Exam hub analytics events are frontend-fired and non-blocking:

- `EXAM_HUB_VIEWED` with metadata `{ slug }`.
- `EXAM_HUB_CTA_CLICKED` with metadata `{ slug, destination }`.
- `STUDY_GOAL_SET` with metadata `{ studyGoal }`.
- `STUDY_GOAL_DISMISSED` with metadata `{ studyGoal }`.
- `DASHBOARD_GOAL_CARD_VIEWED` with metadata `{ studyGoal }`.
- `DASHBOARD_GOAL_CARD_CTA_CLICKED` with metadata `{ studyGoal, destination }`.

Event names must exist in both the frontend `AnalyticsEventType` union and backend `AnalyticsEventType` enum before use.

## SEO

Each exam hub is server-rendered with:

- `revalidate = 300`.
- Per-exam title and meta description.
- Canonical URL under `/exam/{slug}`.
- CollectionPage structured data, including an `ItemList` (`mainEntity`) of every member note's name and canonical URL — not just the visible Featured/Popular/Recent cards — so the structured data proves the hub's real depth even where the page caps visible cards.
- Featured, popular, and recent discovery sections that suppress empty headers, plus a "Browse by Subject" section (real per-subject note counts linking to the matching subject-landing pages) and a "More {Exam} Notes" section listing every remaining note not already shown above — together these make every note in a hub's course/program bucket reachable on-page, not just the first 18.

Zero-note exam states should honestly say: `No {Exam Name} notes have been shared yet.` They must offer the existing intent-preserving `Start preparing for the {short exam name}` signup CTA as the primary path, alongside a secondary link to the main Public Library and a tertiary link to the matching Learn category (see Product Value Strip above) — a dead end with no signup path is the worst outcome on an acquisition page, and Wave 2 exams will hit this state often, not rarely.

## Wave 2 Candidates

Deferred candidates from the v0.25.0 audit:

- CPALE / Accountancy.
- Civil Engineering.
- Electrical Engineering.
- Mechanical Engineering.
- Pharmacy.
- Physical Therapy.
- Civil Service Exam.

Wave 2 should still use the config alias map first. Admin normalization is a separate fast-follow and should not be introduced as a prerequisite unless production data becomes messy enough to require it.
