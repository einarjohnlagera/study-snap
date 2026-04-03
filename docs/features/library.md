# library.md - NoteLib Feature Context

## Goal

Library surfaces make NoteLib a reusable note-first workspace with a clear split between private work and public discovery.

## Library

Route:

- `/library`

Responsibility:

- private workspace for the current user's notes

Library contains:

- `PRIVATE` notes
- `PUBLIC` notes owned by the current user
- Draft and Study Pack Ready notes

Shared note-card layout:

- subject badge
- copy count when available
- title
- `Note Preview`
- `Summary Preview`
- tags

Interaction:

- whole card opens Note Detail
- tertiary actions stay in the card menu
- Library card menu options are:
  - `Edit`
  - `Delete`
  - `Make a Copy`
  - `Share`

## Public Library

Route:

- `/library/public` in the authenticated app shell
- canonical public discovery route is `/public/library`

Responsibility:

- discovery of public notes from you, other creators, and official NoteLib content

Public Library cards use the same shared preview layout and whole-card interaction as Library.

Growth behavior:

- Public Library should encourage copying and studying, not only browsing.
- Discovery sorting should help users find useful community notes faster:
  - `Newest`
  - `Most Copied`
  - `Most Shared`
  - `Most Viewed`

## Public Profile

Public Profile is not a private library surface.

- route: `/public/profile/{userId}`
- purpose: public showcase only
- note cards reuse the same shared layout
- owner-view Public Profile may show an owned-note card menu with:
  - `Delete`
  - `Make Private`
  - `Make a Copy`
- other viewers must not see a note-card menu on Public Profile
