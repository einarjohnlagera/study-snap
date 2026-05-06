# public-notes.md - NoteLib Feature Context

## Goal

Public Notes let users discover, read, and copy notes that creators have made public.

Primary surfaces:

- Public Library
- Public subject listing pages
- Public Note Detail
- Public Profile note list

## Public Note Rules

- a note is public only when `visibility=PUBLIC`
- public pages must never show author email
- public author identity comes from `users.display_name` with viewer-relative labeling

Author labels:

- owner viewing own public note -> `By You`
- official NoteLib content -> `By NoteLib` with `Official`
- all other public notes -> `By {Display Name}`

Author labels link to `/public/profile/{userId}`.

## Public Library

Canonical discovery route:

- `/public/library`

App-shell route:

- `/public/library`

Growth behavior:

- Public Library should help users discover useful notes and copy them into their own Library quickly.
- Public Library sorting should support:
  - `Newest`
  - `Most Copied`
  - `Most Shared`
  - `Most Viewed`
- Discovery sorting should use real copy/share/view signals when available.

Canonical note/detail routes:

- `/public/library/{subject}`
- `/public/library/{subject}/{slug}`

## Public Note Cards

Shared public-facing note cards should show:

- subject badge
- copy count when available
- title
- `Note Preview`
- `Summary Preview`
- tags

Interaction rules:

- whole card is clickable
- do not add redundant `Open Note` buttons inside cards

## Public Note Detail

Public note detail is read/copy/share only.

Owner actions:

- `Open Note`
- `Share this note`

Non-owner actions:

- `Create your own Study Pack`
- `Copy to My Library`
- `Share this note`

Public note detail must not expose edit, delete, generation, or quiz actions.
The note stays primary; Quick Check and CTA blocks should support the note rather than turning the page into a quiz-first surface.

Copy-first generation rule:

- `Create your own Study Pack` on a public note should first copy the note into the viewer's Library.
- The viewer then continues generation on their own private note route.
- Public note detail itself stays read-only.

## Copy Rules

Copying a public note:

- creates a new Draft note in the current user's Library
- copies `title`, `subject`, `tags`, and `content`
- does not copy generated outputs or quiz/performance history
- preserves attribution through `copiedFromNoteId` and `copiedFromUserId`
