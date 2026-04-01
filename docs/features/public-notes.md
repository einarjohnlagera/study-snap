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

- `/library/public`

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
- `Share`

Non-owner actions:

- `Make a Copy`
- `Share`

Public note detail must not expose edit, delete, generation, or quiz actions.

## Copy Rules

Copying a public note:

- creates a new Draft note in the current user's Library
- copies `title`, `subject`, `tags`, and `content`
- does not copy generated outputs or quiz/performance history
- preserves attribution through `copiedFromNoteId` and `copiedFromUserId`
