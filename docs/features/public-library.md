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

- `Course / Program`
- `Learner Level` when public note-owner metadata is available
- `Subject`
- `Tags`
- `By You`
- `Official`
- `Community`

## Sorting

Public Library sort options:

- `Newest`
- `Most Copied`
- `Most Viewed`
- `Title A-Z`

## Note Cards

Public Library note cards reuse the shared note-card layout:

- subtle `courseProgram` line when available
- title
- subject badge
- Study Pack status badge when relevant
- `Note Preview`
- `Summary Preview`
- tags
- subtle metrics row for `views` and `copies` when available

Interaction rules:

- whole card opens the canonical public note route
- cards are preview/navigation only
- do not place copy/share/generate/delete/edit actions inside the card
- use public note detail for actions instead

Discovery guidance:

- prioritize original note preview over generated summary when scanning cards
- keep `views` and `copies` subtle so they help note selection without turning into badge clutter
- use `Newest`, `Most Copied`, `Most Viewed`, and `Title A-Z` as student-friendly discovery labels

## Public Note Detail

Public note detail should help visitors evaluate both the generated study outputs and the original source note.

Rules:

- keep `Summary` as the default tab
- use `Summary`, `Key Concepts`, `Quiz`, and `Full Notes`
- include a subtle `View Full Notes →` CTA inside the `Summary` view so visitors can quickly inspect the original note
- `Full Notes` should render the complete original note body so visitors can judge whether the note is worth copying
- keep the page read-only and copy-first; tabs are for review, not management
