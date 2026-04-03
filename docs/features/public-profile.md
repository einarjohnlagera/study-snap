# public-profile.md - NoteLib Feature Context

## Goal

Public Profile is the public showcase surface for one creator's public identity and public notes.

Route:

- `/public/profile/{userId}`

Related APIs:

- `GET /api/public/profile/{userId}`
- `PUT /api/users/profile/public-visibility` (owner only)

## What Public Profile Shows

- `displayName`
- `bio` or `This user hasn't added a bio yet.`
- optional `learnerLevel`
- optional `courseProgram`
- avatar/initials
- `profileType`
- `Official` badge when the backend marks the creator as official
- subjects derived from public notes
- `publicNotesCount`
- `totalCopies`
- list of public notes only

Public profile note cards reuse the shared note-card layout:

- subject badge
- copy count when available
- title
- `Note Preview`
- `Summary Preview`
- tags

Cards are whole-card links to the canonical public note route.
Cards stay action-free for both owners and non-owners; note management happens in Note Detail instead.

## Visibility

- Public Profile visibility is controlled by `users.public_profile_visible`
- owner can toggle `Public` / `Private` from the Public Profile header
- if the profile is private:
  - owner can still open the page
  - non-owners see `This profile is private.`

## Owner Controls

Owner-only controls belong on Public Profile, not on `/profile`:

- `Edit Profile`
- `Share Profile`
- visibility badge/dropdown

Non-owners:

- must not see `Edit Profile`
- must not see the visibility toggle
- may see `Share Profile`

Navigation:

- use a `Back` button that calls browser/app history back
- place the `Back` button above the Public Profile header card
- do not hardcode Public Profile back navigation to Library or Public Library

## Public Identity Rules

- always use `displayName` as the public identity
- never show email on public profile or public note surfaces
- official badge is backend-derived only
- hide learner-level and course/program rows entirely when those values are empty

## Notes

- Public Profile remains a learning profile, not a social-media profile.
- Note cards stay preview/navigation-focused; note actions live in Note Detail.
