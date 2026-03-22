# shareable-study-packs.md - NoteLib Feature Context

## Goal

Support public discovery and safe reuse of learning content while keeping the note-first model.

## Public Surfaces

- Public Library route: `/library/public`
- Public Note Detail route: `/public/notes/{id}`
- Token share route (existing): `/p/{token}`

Private note share UX:

- If a note is `PRIVATE`, clicking `Share` shows a confirmation modal.
- Confirm action: `Make Public & Share`
- On success, show a share-link modal with copy action.

## Public Note Rules

- public list includes only notes where `visibility=PUBLIC`
- owner notes are excluded from Public Library listing
- public detail is read-only
- public detail shows: title, subject, tags, summary, key concepts, practice quiz
- public detail hides: challenge/adaptive/performance/edit controls

## Copy Flow

Public content can be copied into My Library using:

- `Copy to My Library`

Copy behavior:

- copy only `title`, `subject`, `tags`, `content`
- do not copy generated outputs or performance/session history
- result is a new Draft note in current user ownership
- copy must not trigger new LLM generation

## Security and Privacy

- public pages must not expose raw uploaded image data
- avoid exposing private-only note metadata
- non-public note endpoints remain authenticated
