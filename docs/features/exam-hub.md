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

Authenticated CTA:

- Label: `Browse {courseProgram} Notes`.
- Destination: filtered Public Library URL, using the existing `courseProgram` query slug.

The current feature only preserves exam context. It does not pre-fill goals or change onboarding logic.

## Exam Goals

Track 2 lets authenticated users confirm one exam goal. The backend stores this as nullable `users.exam_goal`
(`ale`, `pnle`, or `let`), separate from `courseProgram`. Clearing the goal stores `null`.

Goal suggestion on Dashboard follows this order:

- Existing `examGoal` from `GET /auth/me` / `GET /me` suppresses the banner.
- Valid `notelib-exam-intent` cookie value suggests that exam first.
- If no valid cookie exists, the frontend infers from `courseProgram` using `getExamSlugForCourseProgram()`.
- Unknown or malformed values render no banner.

Dashboard goal actions:

- `Set as my goal` calls `PUT /users/profile/goal`, clears the intent cookie, fires `EXAM_GOAL_SET`, and hides the banner without a full reload.
- `Dismiss` clears the intent cookie, fires `EXAM_GOAL_DISMISSED`, and hides the banner without changing the stored goal.
- Goal setting is available to all authenticated profile types; there is no Exam Reviewer-only gate.

When an exam goal is set, `/progress` adds a goal summary above the normal subject list. It does not filter the
subject list; all subject progress remains visible. The goal summary is computed from owned Study Packs whose
linked note `courseProgram` matches the configured exam aliases. Users with a goal but no matching Study Packs
still receive a `0%` goal summary so the confirmed goal remains visible.

The progress report also returns `weakestGoalSubject`, computed from the goal-relevant Study Packs by the largest
`notPracticedConcepts + dueConcepts` count. The frontend shows this in a "What to study next" card linked to
`/exam/{examGoal}`. If no weakest subject exists, the card links to the exam hub with generic community-note copy.

Users without a goal can still open `/progress`; Dashboard always shows `View full progress report →`, and progress
pages with subjects but no goal show an `Explore exam hubs to set a goal →` link.

## Analytics

Exam hub analytics events are frontend-fired and non-blocking:

- `EXAM_HUB_VIEWED` with metadata `{ slug }`.
- `EXAM_HUB_CTA_CLICKED` with metadata `{ slug, destination }`.
- `EXAM_GOAL_SET` with metadata `{ examGoal }`.
- `EXAM_GOAL_DISMISSED` with metadata `{ examGoal }`.

Event names must exist in both the frontend `AnalyticsEventType` union and backend `AnalyticsEventType` enum before use.

## SEO

Each exam hub is server-rendered with:

- `revalidate = 300`.
- Per-exam title and meta description.
- Canonical URL under `/exam/{slug}`.
- CollectionPage structured data.
- Featured, popular, and recent discovery sections that suppress empty headers.

Zero-note exam states should show: `No {Exam Name} notes have been shared yet. Check back soon.` with a link to the main Public Library.

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
