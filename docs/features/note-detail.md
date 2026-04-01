# note-detail.md - NoteLib Feature Context

## Goal

Note Detail is the unified owner view for one note and its generated Study Pack state.

Route:

- `/notes/{id}`

## Private Note Detail

Private Note Detail owns:

- note metadata
- note content
- generation state
- share/visibility controls
- quiz entry points
- performance overview

Draft note actions:

- `Edit` -> full editor
- `Generate Study Pack`
- `Make a Copy`
- `Share`

Study Pack Ready actions:

- `Edit` -> inline metadata edit only
- `Start Quick Review`
- `Challenge Quiz`
- `Adaptive Practice` when weak concepts exist
- `Make a Copy`
- `Share`

## Summary / Quiz Tabs

`Summary` and `Quiz` are view tabs, not action buttons.

Rules:

- place tabs below `Note Content`
- active tab uses underline-style navigation
- desktop shows icon + text
- mobile shows icon only with an accessible label
- switching tabs updates the note view without a full page reload
- preserve query-string state such as `?tab=quiz`

## Public Note Detail

Public note detail is a separate public/read-only surface.

- canonical route: `/public/library/{subject}/{slug}`
- owner sees `Open Note` and `Share`
- non-owner sees `Make a Copy` and `Share`
- do not expose private editing or study actions there
