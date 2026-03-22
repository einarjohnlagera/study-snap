# study-library.md - NoteLib Feature Context

## Goal

Library surfaces make NoteLib a reusable workspace:

- **My Library** for owned notes (Draft and Study Pack Ready)
- **Public Library** for discoverable public notes from other users

Core learning loop:

New Note -> Save (Draft) -> Generate Study Pack (Study Pack Ready) -> Review -> Improve -> Make a Copy -> Repeat

## My Library

My Library contains all notes owned by the current user:

- includes `PRIVATE` and `PUBLIC` notes
- cards show note title, subject, tags, content preview, state, and updated date
- supports search, subject filter, tag filter, sorting, and pagination
- opening a card navigates to the unified Note Detail page
- card interaction is consistent with Dashboard/Public Library:
  - click card to open note
  - use top-right card menu for tertiary actions (`Make a Copy`, `Delete`)

State badges:

- `Draft`
- `Study Pack Ready`

Detail actions:

- `Generate Study Pack`
- `Make a Copy`
- `Make Public` / `Make Private`
- quick-review and advanced quiz actions when Study Pack Ready

Note Detail edit behavior:

- Draft notes: `Edit` routes to full note editor (content + OCR)
- Study Pack Ready notes: `Edit` stays on Note Detail and enables inline metadata edit (`title`, `subject`, `tags`) only
- During inline metadata edit mode, share/visibility/study actions are hidden until `Cancel` or `Save`

## Public Library

Public Library lists notes where:

- `visibility = PUBLIC`
- owner is not the current user

Capabilities:

- search, subject filter, and tag filter
- open Public Note Detail (read-only)
- `Copy to My Library`

## Copy Rules

Both flows use the same copy rules:

- **Make a Copy** (own note)
- **Copy to My Library** (public note)

Copy includes only:

- `title`
- `subject`
- `tags`
- `content`

Copy does not include:

- generated summary
- key concepts
- quizzes
- performance history
- quiz sessions
- generated timestamps

Result:

- new Draft note owned by the current user
- redirect to unified Note Detail page

## Dashboard Guardrails

Dashboard remains guidance-first and non-destructive:

- Continue Studying
- Today's Focus
- Recent Activity
- Performance Snapshot
- `New Note` primary action

Do not add delete actions to Dashboard.

## Terminology

Use:

- `My Library`
- `Public Library`
- `New Note`
- `Make a Copy`
- `Copy to My Library`
- `Generate Study Pack`

## Dialog Consistency

- Use the shared app modal component for confirmation and share dialogs.
- Do not use browser-native confirm/alert dialogs for library or note detail actions.
