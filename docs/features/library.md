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
- note cards stay preview/navigation only
- note actions belong in Note Detail

Shared list controls:

- order is always `Search`, `Filter`, `Sort`, then notes list
- on mobile, `Filter` and `Sort` open shared bottom-sheet/modal controls instead of staying always visible

Private Library filters:

- `Subject`
- `Tags`
- `Study Pack Ready`
- `Draft`
- `Public`
- `Private`

Private Library sorting:

- `Recently Updated`
- `Recently Reviewed`
- `Recently Generated`
- `Title (A-Z)`
- `Title (Z-A)`
- `Oldest`

## Public Library

Route:

- `/library/public` in the authenticated app shell
- canonical public discovery route is `/public/library`

Responsibility:

- discovery of public notes from you, other creators, and official NoteLib content

Public Library cards use the same shared preview layout and whole-card interaction as Library.

Public Library controls:

- keep the same `Search`, `Filter`, `Sort`, notes-list structure as Library
- use the same mobile filter/sort sheet behavior as Library

Public Library filters:

- `Subject`
- `Tags`
- `By You`
- `Official`

Growth behavior:

- Public Library should encourage copying and studying, not only browsing.
- Discovery sorting should help users find useful community notes faster:
  - `Newest`
  - `Most Copied`
  - `Most Shared`
  - `Title (A-Z)`

## Public Profile

Public Profile is not a private library surface.

- route: `/public/profile/{userId}`
- purpose: public showcase only
- note cards reuse the same shared layout
- note cards stay action-free and open the canonical public note route
