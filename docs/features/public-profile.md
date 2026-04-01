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
- `profileType`
- `Official` badge when the backend marks the creator as official
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

## Public Identity Rules

- always use `displayName` as the public identity
- never show email on public profile or public note surfaces
- official badge is backend-derived only

## Non-Goals In v1

- followers
- likes
- views
- leaderboards
- bio/about section
