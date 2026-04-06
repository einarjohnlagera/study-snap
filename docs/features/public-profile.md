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
- `totalShares` when the profile's notes have real share activity
- `totalViews` when the profile's notes have real view activity
- list of public notes only

Portfolio polish:

- Public Profile should feel like a lightweight learning portfolio, not a social feed.
- Header metrics stay compact and should use real values only.
- Derive learning-focus summary text from the user's real public-note subjects/course-programs when available.
- A `Featured note` callout may appear when a real public note has copy/share/view signal; do not fake one.

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
- may see `Share Profile` when the profile is public

Navigation:

- use a `Back` button that calls browser/app history back
- place the `Back` button above the Public Profile header card
- do not hardcode Public Profile back navigation to Library or Public Library

## Share Profile Behavior

`Share Profile` must use the same share modal pattern used for note sharing:

- modal title: `Share this profile`
- labeled field: `Shareable URL`
- buttons: `Close` and `Copy Link`
- `Copy Link` copies the URL to the clipboard and shows `Link copied` feedback inline

If the profile is public:
- Clicking `Share Profile` opens the share modal directly.

If the profile is private and the owner is viewing:
- Clicking `Share Profile` opens a confirm modal: `This profile is private`
- The confirm modal offers `Cancel` and `Make Public & Share`
- `Make Public & Share` sets the profile to public and then opens the share modal

Private profiles are not directly shareable. The share modal must not be opened for private content without the owner confirming the visibility change first.

Non-owners only see Share Profile when the profile is already public (they cannot reach the page if it is private).

## Public Identity Rules

- always use `displayName` as the public identity
- never show email on public profile or public note surfaces
- official badge is backend-derived only
- hide learner-level and course/program rows entirely when those values are empty

## Notes

- Public Profile remains a learning profile, not a social-media profile.
- Note cards stay preview/navigation-focused; note actions live in Note Detail.
- Public Profile note cards must keep using the shared note-card component and remain action-free for both owners and non-owners.
