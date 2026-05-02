# profile-settings.md - NoteLib Feature Context

## Goal

`/profile` is the private editor for identity, learning-profile fields, and profile type.

Public profile controls stay on `/public/profile/{userId}`.

## Public vs private boundary

- `/profile` = private account editing
- `/public/profile/{userId}` = public identity page

`/profile` does **not** own:

- public/private visibility
- share controls
- public portfolio presentation

## Navigation model

Authenticated app navigation should keep this terminology consistent:

- avatar dropdown:
  - `My Profile` -> `/public/profile/{userId}`
  - `Settings` -> `/settings`
  - `Sign Out`
- sidebar account section:
  - `Profile` -> `/public/profile/{userId}`
  - `Settings` -> `/settings`

`Profile` means the public identity page.
`Settings` means account/app settings.

## Learning Profile behavior

- `Learner Level` and `Course / Program` both use the shared suggestion/combobox interaction pattern
- `Course / Program` helper text changes with the selected `Learner Level`
- `Save Learning Profile` requires both fields and shows inline validation when either is missing
- `Save Identity` and `Save Profile Type` still work independently when Learning Profile fields are incomplete

## Dashboard return behavior

When `/profile` is opened from the learner-level prompt on Dashboard:

- the route uses `/profile?from=dashboard#learning-profile`
- the top back link should return to `Dashboard`

When opened normally:

- the back link returns to the user's public profile page
