# collections.md - NoteLib Feature Context

## Goal

Collections give any learner a saved, named, ordered grouping of their own existing notes.

A collection is the reusable container behind v0.27.0 organization workflows:

- a unit
- a study plan
- a review set
- a lesson-plan-shaped note playlist for future Teacher terminal actions

The backend entity is universal and profile-agnostic. It does not decide labels, CTAs, or profile-specific terminal actions.

## Core Model

A `NoteCollection` is a playlist over existing notes.

It is not:

- a new content type
- an AI-synthesized document
- a Study Pack
- a quota bucket
- a public/shareable object in v1

Fields:

- `id`
- `ownerUserId`
- `title`
- optional `description`
- ordered `items`
- `createdAt`
- `updatedAt`

Each item stores:

- `noteId`
- optional `label`
- `position`
- `createdAt`

Item labels are neutral backend data. A future Teacher UI may use labels as section/week/topic names, but the backend does not interpret them.

## Profile-Agnostic Spine

The backend API must not branch on `ProfileType`.

Profile-aware presentation is a future frontend responsibility:

| Profile | Future frontend label | Future primary terminal action |
|---|---|---|
| `TEACHER` | `Lesson Plan` | Combined sectioned DOCX + shareable quiz links through Exam Builder |
| `STUDENT` | `Study Plan` | Study the set; generate or review per note |
| `BOARD_EXAM` | `Review Set` | Practice across the set through existing multi-note exam flows |
| `PROFESSIONAL` | `Collection` | Study the set; generate or review per note |

Backend responses stay neutral: `title`, `description`, `items`.

## Ownership Rules

Collections are owner-private in v1.

- A collection can contain only notes owned by the requesting user.
- Adding or ordering a note that does not exist or is not owned by the caller returns `NoteNotFoundException` / `404`.
- A note may belong to multiple collections.
- A note may appear at most once in a single collection.
- Adding an already-present note is idempotent and silently skipped.
- Deleting a collection deletes only the collection and item rows.
- Deleting a note removes that note's collection item rows through the `note_collection_items.note_id` FK cascade.
- Deleting a collection must never delete notes.

## Generation And Quota Rules

Collections add no AI behavior.

- No collection-level Study Pack generation.
- No collection-level quiz generation.
- No LLM call is made by collection CRUD.
- No new usage or quota category exists for collections.
- Existing Study Pack and quiz generation stay per-note and keep their existing quota rules.

## API Surface

All endpoints are authenticated and available to `USER` and `ADMIN` roles.

Base path: `/collections`

### List Collections

`GET /collections`

Returns lightweight summaries ordered by `updatedAt desc`.

Response item:

- `id`
- `title`
- `description`
- `itemCount`
- `createdAt`
- `updatedAt`

### Create Collection

`POST /collections`

Request:

- `title` required, trimmed, max `150`
- `description` optional
- `noteIds` optional list of existing owned note IDs

Behavior:

- validates all note IDs before writing
- dedupes repeated note IDs while preserving request order
- appends items starting at position `0`
- returns full detail with ordered items

### Get Collection

`GET /collections/{id}`

Returns full detail with ordered items.

Item response is intentionally lean and private-owner focused:

- `noteId`
- `label`
- `position`
- `title`
- `subject`
- `courseProgram`
- `studyPackStatus`
- `generatedQuizId`
- `updatedAt`

`studyPackStatus` uses the same note readiness rule as the Note API:

- note `GENERATED` -> `STUDY_PACK_READY`
- note `GENERATING` -> `GENERATING`
- note `FAILED` -> `FAILED`
- no linked Study Pack -> `DRAFT`
- linked Study Pack otherwise -> `STUDY_PACK_READY`

### Update Metadata

`PATCH /collections/{id}`

Request:

- `title` optional, but if present it must be non-blank and max `150`
- `description` optional and nullable

Behavior:

- updates collection metadata
- bumps `updatedAt`
- returns full detail

### Delete Collection

`DELETE /collections/{id}`

Behavior:

- deletes the collection and its item rows
- never deletes referenced notes
- returns `204`

### Add Items

`POST /collections/{id}/items`

Request:

- `noteIds`

Behavior:

- validates every note is owned by the caller before writing
- dedupes the request list
- skips notes already present in the collection
- appends new notes after the current highest position
- bumps `updatedAt`
- returns full detail

### Remove Item

`DELETE /collections/{id}/items/{noteId}`

Behavior:

- removes the matching item
- compacts remaining item positions so positions stay contiguous
- bumps `updatedAt`
- returns `204`

### Set Order

`PUT /collections/{id}/items/order`

Request:

- `items: [{ noteId, label? }]`

Behavior:

- submitted note set must exactly equal the current collection note set
- cannot add or remove membership; use Add Items or Remove Item for that
- rewrites item positions from submitted order
- applies trimmed nullable labels
- rejects labels over `120` characters
- bumps `updatedAt`
- returns full detail

## Error States

- Collection not found or not owned by caller -> `CollectionNotFoundException` / `404`.
- Malformed collection path UUID -> `CollectionNotFoundException` / `404`.
- Blank title on create or update -> `InvalidCollectionRequestException` / `400`.
- Title over `150` characters -> `InvalidCollectionRequestException` / `400`.
- Label over `120` characters -> `InvalidCollectionRequestException` / `400`.
- Missing/null item note ID -> `InvalidCollectionRequestException` / `400`.
- Referenced note does not exist or is not owned by caller -> `NoteNotFoundException` / `404`.
- Remove item for a note not in the collection -> `CollectionItemNotFoundException` / `404`.
- `setOrder` adds, drops, or duplicates a note -> `InvalidCollectionRequestException` / `400`.

## Out Of Scope

Do not add these under the collection CRUD spine unless explicitly scoped later:

- frontend collection UI
- profile-aware labels or CTAs in the backend
- teacher Exam Builder wiring
- DOCX/shareable quiz-link generation from collections
- collection-level AI synthesis
- bulk generate across a collection
- public/shareable collections
- lesson-plan document parsing
