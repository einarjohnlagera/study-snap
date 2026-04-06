# profile-settings.md - NoteLib Feature Context

## Goal

`/profile` is the private account/settings surface for identity and learning-profile inputs.

## Responsibilities

- edit identity fields
- edit learning-profile fields such as `learnerLevel`, `courseProgram`, and `bio`
- edit profile type
- link out to `View Public Page`

## Public Profile Boundary

- `/profile` does not own public-profile visibility, sharing, or portfolio presentation.
- Public-profile controls remain on `/public/profile/{userId}`.
- Public Profile is the public learning-portfolio surface, while `/profile` remains the private settings/editor surface.
- The avatar dropdown in the authenticated app shell provides quick navigation to:
  - `My Profile` → `/public/profile/{userId}` (public identity page)
  - `Settings` → `/settings` (account and app settings)
  - `Sign Out`
- The sidebar Account section uses the same mental model:
  - `Profile` → `/public/profile/{userId}` (public identity page)
  - `Settings` → `/settings`
- `Profile` and `Settings` in the sidebar map to the same routes as the avatar dropdown.
- This separation ensures users can easily access their public identity without conflating it with account settings.

## Course / Program

- `Course / Program` uses the same suggestion-combobox pattern as Note Editor.
- Suggestions should come from curated defaults plus normalized saved values from the user's notes/profile.
- Users may still type a custom value.
- Typing filters suggestions in real time with case-insensitive prefix-first, then contains matching.
- Existing suggestions should appear before the custom `Use "..."` action so reuse is encouraged.
- Helper text changes with `Learner Level` so the field examples stay meaningful for grade school, high school, college, board-exam, professional, and personal-learning users.
- `Save Learning Profile` requires both `Learner Level` and `Course / Program` and shows inline validation when either is missing.
