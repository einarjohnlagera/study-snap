# dashboard.md - NoteLib Feature Context

## Goal

Dashboard is a guidance surface, not a management screen. It should help users decide what to do next in the note -> Study Pack -> quiz loop.

## Key Files

**Backend**
- `backend/src/main/java/com/studysnap/backend/controller/DashboardController.java` — dashboard endpoints: `/dashboard/overview`, `/dashboard/continue-studying`, `/dashboard/focus-areas`, `/dashboard/weekly-activity`, `/dashboard/performance-summary`
- `backend/src/main/java/com/studysnap/backend/service/DashboardService.java` — business logic for all sections; resolves continue-studying recommendation, weak concepts, weekly activity, `courseProgram` context

**Frontend**
- `frontend/app/dashboard/page.tsx` — main dashboard client; `SupportedDashboardProfileType` branching (STUDENT / BOARD_EXAM / TEACHER / PROFESSIONAL); all section composition per profile type
- `frontend/app/dashboard/continue-spotlight.tsx` — Continue Studying card
- `frontend/app/dashboard/dashboard-focus-areas-card.tsx` — Focus Areas / weak concepts section
- `frontend/app/dashboard/study-pack-grid.tsx` — Recent Notes grid
- `frontend/lib/api.ts` — `getDashboardOverview()`, `getContinueStudyingRecommendation()`, `getTodayFocus()`

## Anti-drift Notes

- Dashboard sections are **profile-type branched** inside `page.tsx` using `resolveDashboardProfileType()`; do not add profile checks inside individual section components
- Continue Studying must use the `resumeType` label from the backend directly — do not infer mode labels on the frontend
- Focus Areas shows `Revisit Note` for Free/Plus users when Adaptive Practice is gated; only show the upgrade prompt when no source note is resolvable
- The Community Notes section (v0.21.0) uses `GET /notes/public?courseProgram=<value>&size=4` directly — no new dashboard backend endpoint

## Current Personalization Prompt

After onboarding completes, Dashboard may show a lightweight learner-level prompt near the top.

Current prompt copy:

- title: `Too easy or too hard?`
- body: `Set your learner level so future quizzes match your study stage.`
- CTA: `Adjust level`

Behavior:

- dismissible
- dismissal stored per user in frontend storage
- CTA navigates to `/profile?from=dashboard#learning-profile`
- when opened from Dashboard, `/profile` shows a `Dashboard` back link instead of the public-profile back link

This prompt is for learner level only. It does not move learning style or reminder preferences back into onboarding.

## Profile-specific priorities

### Student

Dashboard should prioritize:

- `Continue Studying`
- `Focus Areas`
- `Recent Notes`
- `Quick Review`
- `Usage / Progress`

### Board Taker

Dashboard should prioritize:

- `Exam Countdown`
- `Start Board Exam`
- `Weak Areas`
- `Adaptive Practice`
- `Usage / Progress`

If Adaptive Practice quota is exhausted for the current plan, the CTA should open the shared upgrade flow with "upgrade for more sessions" framing instead of navigating to a dead-end route.

### Teacher

Dashboard should prioritize:

- `Create Teaching Material`
- `Recent Notes`
- `Recently Generated Quizzes`
- `Ready to Export`
- `Teacher Help / Tips`

Teacher sections should stay quiz-preview and export oriented, not student-session oriented.

## Continue Studying

Endpoint:

- `GET /dashboard/continue-studying`

Required payload shape for the UI:

- `noteId`
- `noteTitle`
- `subject`
- optional `courseProgram`
- `resumeType`
- `currentQuestionIndex`
- `totalQuestions`
- `lastReviewedAt`
- optional `summaryPreview`

Current label mapping:

- `QUICK_REVIEW` -> `Resume Quick Review`
- `CHALLENGE` -> `Resume Challenge Quiz`
- `ADAPTIVE` -> `Resume Adaptive Practice`

Rules:

- use the backend `resumeType` label directly
- do not make extra frontend fetches just to label the card
- keep the card note-first so the user knows which note they are returning to

## Focus Areas

Focus Areas should show weak concepts for all learners when data exists.

Current action behavior:

- Users whose plan allows Adaptive Practice can launch Adaptive Practice from the suggested note while quota remains
- Users without an Adaptive Practice start path still see the same weak concepts and get a `Revisit Note` link to the source note so they can review material
- The upgrade prompt (`Unlock Adaptive Practice`) is shown only when no source note is resolvable
- When weak concepts are present, the card also links to `/progress` with `View full progress report ->` so learners can open the subject-level ConceptHealth report

## Community Notes Section

A section visible to all profile types, placed below Recent Notes.

**Section title**: "Notes for [CourseProgram]" — e.g. "Notes for PNLE", "Notes for NMAT"
**Data source**: `GET /notes/public?courseProgram=<value>&size=4` — the same endpoint as the Public Library
**Footer link**: "See all in Public Library →" navigates to `/public/library?courseProgram=<value>`

Behavior by state:

- `courseProgram` is set and matching public notes exist → show up to 4 note cards using the shared public library card layout; clicking a card navigates to the canonical public note detail page
- `courseProgram` is set but no matching public notes exist → hide the section entirely; do not show an empty state
- `courseProgram` is not set → render a placeholder card with a modal CTA:
  - Modal title: "Set your Course/Program"
  - Modal body: "Set your Course/Program to see public notes tailored for your review track."
  - Primary CTA: "Go to Learner Profile" (navigates to `/profile#learning-profile`)
  - Secondary CTA: "Cancel"

Applies to STUDENT, BOARD_EXAM, TEACHER, and PROFESSIONAL profile types.

## Matching Study Plan Section

v0.31.0 adds a learner-facing plan surface for STUDENT, BOARD_EXAM, and PROFESSIONAL dashboards.

**Section title**: `Recommended {collectionLabel.singular}` using `getCollectionLabels(profileType)`
**Data source**: `GET /collections/public?courseProgram=<value>` plus the user's `GET /collections` list to detect an already adopted `sourcePlanId`
**CTA**:

- `Start this plan` when the learner has not adopted the source plan
- `Continue this plan` when a private collection with `sourcePlanId == sourcePlan.id` already exists

Behavior by state:

- `courseProgram` is set and a matching published plan exists → show the first matching plan card with item count and Start/Continue CTA
- no matching plan exists → render nothing; do not show an empty shell
- Start calls `POST /collections/{id}/adopt`, then routes to `/collections/{personalId}`
- if adoption reports skipped items, show a non-blocking notice on the destination collection page
- network errors keep the CTA in place and show an inline retryable error

The section does not create a new Dashboard backend endpoint, quota, AI call, or plan store. Adopted plans are normal private collections, so the existing note -> Study Pack -> practice -> Progress loop handles all downstream work.

The same `DashboardStudyPlanSection` card is also reused on the onboarding completion step (v0.31.1) as a supplementary adopt surface — see `onboarding.md`. There is no separate component or endpoint for that surface.

## First-study guidance

Dashboard may also show first-study guidance for verified users who still have `studyPackCount == 0` and have not completed the separate product-onboarding tracker.

That flow is separate from `/onboarding` and is tracked with `productOnboardingCompletedAt`.

When a user has zero notes, the dashboard renders a profile-aware first-run empty state (`DashboardEmpty`) that teaches the loop for that profile rather than a single generic prompt:

- TEACHER → import lecture material → generate quizzes → group into a Lesson Plan to export/share
- STUDENT → import or create notes → generate a Study Pack → quiz and track weak topics
- BOARD_EXAM → import reviewers → generate Study Packs → practice across a Review Set
- PROFESSIONAL → import or create materials → generate → scenario-based practice

The profile-specific collection term (Lesson Plan / Review Set) is resolved through `lib/collection-labels.ts`, not hardcoded. Every variant surfaces both entry points — `Import files` (`/notes/import`) and `Create a note` (`/notes/new`) — to make the new bulk-import on-ramp the first thing a new user sees.
