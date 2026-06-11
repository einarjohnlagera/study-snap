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

## Frontend Core UI

The core Collections UI ships as the universal organization surface:

- `/collections` lists the user's saved collections in backend order (`updatedAt desc`).
- `/collections/[id]` shows one collection, its ordered note items, item labels, and note readiness hints.
- The detail page loads from `GET /collections/{id}` on mount, so a hard refresh renders the persisted order.
- The detail page can edit metadata, delete the collection, add notes, remove notes, relabel items, and reorder items through the shipped CRUD API.

Profile-aware labels are resolved only through `frontend/lib/collection-labels.ts`.

| Profile | Singular | Plural / nav |
|---|---|---|
| `TEACHER` | `Lesson Plan` | `Lesson Plans` |
| `STUDENT` | `Study Plan` | `Study Plans` |
| `BOARD_EXAM` | `Review Set` | `Review Sets` |
| `PROFESSIONAL` / default | `Collection` | `Collections` |

Do not hardcode those profile-specific names in page or component code. Components should ask the resolver for `singular`, `plural`, `navLabel`, empty-state copy, and CTA copy.

Core UI behavior:

- The app shell shows the profile-aware Collections nav item directly after Library.
- `/collections` uses the authenticated page header pattern and opens a create modal with title max length `150`.
- `/collections/[id]` uses `BackLink href="/collections"` with the profile-aware plural label.
- Item labels are editable text inputs with max length `120`.
- Reorder uses drag-and-drop plus `Move up` / `Move down` buttons for accessibility.
- Reorder/relabel persists through `PUT /collections/{id}/items/order` with the full ordered item set.
- The in-detail note picker uses the user's own notes from the Library note-list API and excludes notes already in the collection.
- Delete is confirm-gated and must state that deleting a collection does not delete its notes.
- Load failures show retry states; a `404` detail response shows a not-found state with a link back to the collections list.

Deferred Prompt B slots:

- Profile-aware terminal CTAs are intentionally not wired yet.
- Teacher collection -> Exam Builder / DOCX / shareable quiz links remains a follow-up.
- Student or board-exam multi-note practice actions remain a follow-up.
- Library multi-select "Add to collection" remains a follow-up.

## Out Of Scope

Do not add these under the collection CRUD spine unless explicitly scoped later:

- profile-aware labels or CTAs in the backend
- teacher Exam Builder wiring
- DOCX/shareable quiz-link generation from collections
- collection-level AI synthesis
- bulk generate across a collection
- public/shareable collections
- lesson-plan document parsing
