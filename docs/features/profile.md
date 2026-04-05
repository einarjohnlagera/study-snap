# profile.md - NoteLib Feature Context

## Goal

`Profile` is the private account surface for identity, learning-profile metadata, and profile type.

It is not the place for public-profile visibility or sharing controls.

## Sections

`/profile` should stay split into:

- top Display Name card
- `Identity`
- `Learning Profile`
- `Profile Type`

## Identity

Identity owns:

- `firstName`
- `lastName`
- `displayName`
- `email`

Save behavior:

- `Save Identity` only writes identity fields
- email changes write `pendingEmail` first and require verification before replacing `email`

## Learning Profile

Learning Profile owns:

- required `learnerLevel` during onboarding
- required `courseProgram` when completing onboarding or saving the Learning Profile card
- optional `bio`

Current learner-level options:

- `GRADE_SCHOOL`
- `JUNIOR_HIGH`
- `SENIOR_HIGH`
- `COLLEGE`
- `BOARD_EXAM_REVIEW`
- `PROFESSIONAL`
- `PERSONAL_LEARNING`

Rules:

- existing users may still have `null` learner metadata until they update it
- `courseProgram` accepts typed custom values but must be present when the Learning Profile card is saved
- helper text for `courseProgram` changes with `learnerLevel`
- autocomplete filters in real time and prioritizes existing prefix matches before contains matches and before the custom action
- combobox-style fields should reuse the Note Editor `Subject` input-plus-suggestions pattern
- `Save Learning Profile` should not change identity or profile type

## Public Profile Link

`View Public Page` is navigation only.

Public-page controls such as `Share Profile` and public/private visibility belong on `/public/profile/{userId}`.
