# profile.md - NoteLib Feature Context

## Goal

`/profile` is the private editing surface for identity, learning profile, and profile type.

It is not the public-profile sharing surface.

## Current Sections

`/profile` is split into separate save areas:

- top display-name card
- `Identity`
- `Learning Profile`
- `Profile Type`

Each section saves independently.

## Identity

Identity owns:

- `firstName`
- `lastName`
- `displayName`
- `email`

Rules:

- `Save Identity` updates only identity fields
- email changes write `pendingEmail` first
- the active `email` is replaced only after verification

## Learning Profile

Learning Profile owns:

- `learnerLevel`
- `courseProgram`
- `bio`

Current learner-level options:

- `GRADE_SCHOOL`
- `JUNIOR_HIGH`
- `SENIOR_HIGH`
- `COLLEGE`
- `BOARD_EXAM_REVIEW`
- `PROFESSIONAL`
- `PERSONAL_LEARNING`

Rules:

- onboarding collects required `learnerLevel` and required `courseProgram` before users continue to their first Study Pack
- users with legacy `null` learner-level data are rejected on the next Learning Profile save until they set one
- `Save Learning Profile` requires both `learnerLevel` and `courseProgram`
- `bio` remains optional
- `Save Learning Profile` must not update identity or profile type

Current helper-copy meaning:

- `Learner Level` -> personal quiz difficulty and explanation depth for non-teachers; default generated-quiz difficulty for teachers
- `Course / Program` -> `Provides domain context so examples and questions stay relevant to your field.`

Important product rule:

- `learnerLevel` is the primary difficulty and explanation-depth signal for future generations
- `courseProgram` provides domain context only; it is not the direct difficulty control

## Profile Type

For Board Taker profile, an Exam Date field is shown in the Profile Type card and saved independently. Clearing the date removes the dashboard countdown.

## Study Focus

Study Focus is the profile-owned setup surface for progress goals that are not set by the exam hub intent flow.

Stored fields:

- `studyGoal`: legacy/current single goal string. It may contain an exam slug such as `ale` or an older course-program goal.
- `focusSubjects`: subject-level multi-select saved as `users.focus_subjects text[]`.

Rules:

- The Study Focus picker loads subject chips from `GET /subjects?scope=mine`.
- Subject chips come from the user's own AI-inferred note / Study Pack subjects, not course-program suggestions.
- Users can select multiple subjects and save them as `focusSubjects`.
- Saving a non-empty `focusSubjects` list clears `studyGoal`; the two are mutually exclusive when changed from Profile.
- The exam hub intent flow may still set `studyGoal` directly and does not need to touch `focusSubjects`.
- If `studyGoal` already exists, Profile shows the current goal with `Change` and `Clear`; `Change` moves the user into the subject multi-select.
- If `focusSubjects` exists and `studyGoal` is empty, Profile shows `Focusing on:` plus subject pills with `Change` and `Clear`.
- If there are no user subjects yet, Profile shows: `Create some notes first — your subjects will appear here as focus options.`

Profile-type visibility and copy:

- `BOARD_EXAM`: section header `Exam Focus`; copy `Pick the specific subjects you want to track readiness for.`
- `STUDENT`: section header `Study Focus`; copy `Pick the subjects you're preparing for this term.`
- `TEACHER`: section hidden.
- `PARENT`, `PROFESSIONAL`, or null profile type: section header `Study Focus`; copy `Pick a subject to track mastery toward.`

## Learning Profile UX

- `Course / Program` uses the shared combobox pattern also used by note metadata inputs
- suggestions come from curated defaults plus normalized saved values
- users may still type a custom value
- typing filters suggestions in real time
- exact case-insensitive matches should reuse the existing stored display label

## Dashboard entry

The Dashboard learner-level prompt links here using:

- `/profile?from=dashboard#learning-profile`

The Learning Profile card must remain reachable through that hash target.

## Navigation

- App shell sidebar `Profile` opens `/profile` so users land on the private profile-editing surface.
- Avatar dropdown `My Profile` remains the public-profile entry point.
- Guide footer `Switch Profile` CTAs deep-link to `/profile#profile-type`; the Profile Type card owns the `profile-type` hash target and scrolls/focuses into view on load.

## Public Profile Link

`View Public Page` is navigation only.

Public visibility, share actions, and public-profile presentation belong on `/public/profile/{userId}`.
