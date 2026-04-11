# shareable-study-packs.md - NoteLib Feature Context

## Goal

Support public discovery and safe reuse of learning content while keeping the note-first model.

## Public Surfaces

- Public Library app route: `/library/public`
- Public Library canonical SEO route: `/public/library`
- Public Note Detail route: `/public/library/{subject}/{slug}`
- Legacy public note route: `/public/notes/{id}` -> redirects to canonical SEO route
- Token share route (existing): `/p/{token}`

Private note share UX:

- If a note is `PRIVATE`, clicking `Share` shows a confirmation modal.
- Confirm action: `Make Public & Share`
- On success, show a share-link modal with copy action.

## Public Note Rules

- public list includes only notes where `visibility=PUBLIC`
- public list includes the current user's own public notes as well as community and official NoteLib notes
- public detail is read-only
- public detail is accessible without login and should remain indexable
- public detail shows: title, subject, tags, summary, key concepts, practice quiz, author attribution
- public detail hides: challenge/adaptive/performance/edit controls
- private notes must never resolve on the public SEO route
- `sitemap.xml` must include only canonical public library routes, not authenticated library routes

## Copy Flow

Public content can be copied into Library using:

- `Copy to My Library`

Copy behavior:

- copy only `title`, `subject`, `tags`, `content`
- do not copy generated outputs or performance/session history
- result is a new Draft note in current user ownership
- repeated copies of the same public note by the same user should reuse the existing copied note instead of creating duplicates
- copy must not trigger new LLM generation
- preserve public-source attribution with `copiedFromNoteId` and `copiedFromUserId`

## Security and Privacy

- public pages must not expose raw uploaded image data
- avoid exposing private-only note metadata
- non-public note endpoints remain authenticated
