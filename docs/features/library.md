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

- subtle `courseProgram` line when available
- title
- private-library visibility icon near the title when relevant
- subject badge
- copy count when available
- Study Pack status badge
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

- `Course / Program`
- `Subject`
- `Tags`
- `Study Pack Ready`
- `Draft`
- `Public`
- `Private`

Private Library sorting:

- `Recently Updated`
- `Recently Reviewed`
- `Newest`
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

- `Course / Program`
- `Learner Level`
- `Subject`
- `Tags`
- `By You`
- `Official`
- `Community`

Growth behavior:

- Public Library should encourage copying and studying, not only browsing.
- Discovery sorting should help users find useful community notes faster:
  - `Newest`
  - `Most Copied`
  - `Most Shared`
  - `Title (A-Z)`

Metadata behavior shared with note authoring:

- Library subject filters depend on the same persisted `notes.subject` values used by the Note Editor autocomplete.
- When a user saves a custom subject on a note, that subject becomes available in later `Subject` suggestions and filters.
- AI-generated subjects should remain specific enough to be useful filters, ideally in a reusable academic format such as `Primary field – subtopic`.
- Notes now persist optional per-note `courseProgram` metadata so future library filters can group notes more accurately without relying only on the user's profile default.

## Public Profile

Public Profile is not a private library surface.

- route: `/public/profile/{userId}`
- purpose: public showcase only
- note cards reuse the same shared layout
- note cards stay action-free and open the canonical public note route
