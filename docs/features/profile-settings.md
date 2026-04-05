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

## Course / Program

- `Course / Program` uses the same suggestion-combobox pattern as Note Editor.
- Suggestions should come from curated defaults plus normalized saved values from the user's notes/profile.
- Users may still type a custom value.
