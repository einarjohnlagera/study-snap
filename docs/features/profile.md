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

- onboarding does **not** collect these fields
- existing users may still have `null` learner metadata until they update the card
- `Save Learning Profile` requires both `learnerLevel` and `courseProgram`
- `bio` remains optional
- `Save Learning Profile` must not update identity or profile type

Current helper-copy meaning:

- `Learner Level` -> `Controls difficulty, explanation depth, and quiz complexity.`
- `Course / Program` -> `Provides domain context so examples and questions stay relevant to your field.`

Important product rule:

- `learnerLevel` is the primary difficulty and explanation-depth signal for future generations
- `courseProgram` provides domain context only; it is not the direct difficulty control

## Profile Type

For Board Taker profile, an Exam Date field is shown in the Profile Type card and saved independently. Clearing the date removes the dashboard countdown.

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

## Public Profile Link

`View Public Page` is navigation only.

Public visibility, share actions, and public-profile presentation belong on `/public/profile/{userId}`.
