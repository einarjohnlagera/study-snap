# notes.md - NoteLib Feature Context

## Goal

Notes are the primary user-authored workspace in NoteLib. Users organize note metadata first, then turn notes into Study Packs for review.

## Note metadata

Current note-authoring fields:

- `title` (optional)
- `courseProgram` (optional, defaults from the user's profile on new notes)
- `subject` (optional)
- `tags` (optional)
- `content` (required before save/generate)

Rules:

- `courseProgram` belongs to the note once saved and can differ from the profile default.
- `subject` uses autocomplete from persisted note subjects.
- users can still type a custom subject directly.
- a saved custom subject becomes available in future subject suggestions after the note is persisted.
- AI subject suggestions should stay specific and library-friendly:
  - prefer reusable academic labels such as `Biology – Cell Division` or `Software Engineering – Data Structures`
  - avoid broad umbrella labels such as `Medicine`, `Engineering`, `Education`, `Law`, or `Business` when the note supports a narrower subject
- learner level, course/program, current subject, and tags may be passed into Study Pack generation to improve subject suggestion quality without changing the note form flow.
- tags stay optional and should include helper guidance rather than mandatory validation pressure.

## Create and edit behavior

Create mode:

- route: `/notes/new`
- actions: `Save`, `Generate Study Pack`

Edit mode for draft notes:

- route: `/notes/{id}/edit`
- actions: `Save Changes`, `Cancel`, `Generate Study Pack`

Edit mode for Study Pack Ready notes:

- route: `/notes/{id}/edit`
- editable metadata: `title`, `courseProgram`, `subject`, `tags`
- locked field: `content`
- helper text: `Note content cannot be edited after generating a Study Pack. You can still update the title, course/program, subject, and tags.`
- actions: `Save Changes`, `Cancel`, `Make a Copy`

## AI metadata suggestions

After Study Pack generation, the shared AI suggestion modal should let users decide field by field:

- `Title` -> `Keep My Title` or `Use AI Title`
- `Subject` -> `Keep My Subject` or `Use AI Subject`
- `Tags` -> `Keep My Tags`, `Merge Tags`, or `Use AI Tags`

Rules:

- never silently overwrite user-entered `title` or `subject`
- default to `Merge Tags` when the note already has tags
- default to `Use AI Tags` when the note has no tags yet
- use the same suggestion flow from both `Create Note` and `Note Detail`
- present the choices in a compact review modal, not a wall of buttons
- compare `Your` value versus `AI` value for each field
- show tags as chips, not inside long action labels
- include a live preview of the final metadata before applying changes
