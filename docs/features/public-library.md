# public-library.md - NoteLib Feature Context

## Goal

Public Library is the public discovery surface for shared notes.

Routes:

- canonical: `/public/library`
- authenticated shell entry: `/library/public`

## Layout

Public Library should use the same top-level list structure as private Library:

1. `Search`
2. `Filter`
3. `Sort`
4. notes list

On mobile, `Filter` and `Sort` should open a bottom-sheet or modal instead of staying always visible.

## Filters

Public Library filters:

- `Subject`
- `Tags`
- `By You`
- `Official`

## Sorting

Public Library sort options:

- `Newest`
- `Most Copied`
- `Most Shared`
- `Title (A-Z)`

## Note Cards

Public Library note cards reuse the shared note-card layout:

- subject badge
- copy count when available
- title
- `Note Preview`
- `Summary Preview`
- tags

Interaction rules:

- whole card opens the canonical public note route
- cards are preview/navigation only
- do not place copy/share/generate/delete/edit actions inside the card
- use public note detail for actions instead
