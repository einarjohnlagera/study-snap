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
- a user- or teacher-shareable object in v1

Admin-published collections are the v0.31.0 exception: an admin can publish a collection as an adoptable study plan over already-public notes. Learners do not study the source plan directly; adopting creates a private snapshot copy in their own library.

**Study Plans vs saved library filters (do not consolidate).** A Study Plan is a *durable, ordered, named organizer* — the canonical way a learner groups notes by unit/grade level/preference. A saved library filter is a *transient quick lens* (a stored search/filter combo) over the whole library. They serve different jobs and both are intentionally kept: filters are how a learner narrows the library (including while assembling a plan from selection); the plan is the resulting durable grouping.

Fields:

- `id`
- `ownerUserId`
- `title`
- optional `description`
- `visibility` (`PRIVATE` by default, `PUBLIC` only for admin-published plans)
- optional `courseProgram`
- optional `sourcePlanId` on adopted personal plans
- ordered `items`
- `createdAt`
- `updatedAt`

Each item stores:

- `noteId`
- optional `label`
- `position`
- `createdAt`

Item labels are neutral backend data. The Teacher Exam Builder frontend uses them as initial section/week/topic names, but the backend does not interpret them.

## Profile-Aware Terminal Actions

The backend API must not branch on `ProfileType`.

Profile-aware presentation is a frontend responsibility. The backend responses stay neutral: `title`, `description`, `items`.

| Profile | Frontend label | Primary terminal action |
|---|---|---|
| `TEACHER` | `Lesson Plan` | `Build Exam` → combined sectioned DOCX + shareable quiz links through Exam Builder |
| `STUDENT` | `Study Plan` | `Take the Long Exam` → Long Exam setup |
| `BOARD_EXAM` | `Review Set` | `Take the Board Exam` → Board Exam setup |
| `PROFESSIONAL` | `Collection` | `Start Interview Practice` → Interview Practice setup |
| `PARENT` | `Collection` | No terminal action |

The non-teacher premium-exam mapping is owned by `resolvePlanPremiumExamMode` in `frontend/lib/exam-mode-visibility.ts`, and the profile-aware CTA labels live in `getCollectionTerminalAction`. Do not hardcode profile checks in collection UI components.

Premium-exam eligibility differs from the Teacher Exam Builder: Long/Board/Interview generate their own questions at start, so a note only needs a **ready Study Pack** (`canIncludeCollectionItemInPremiumExam` = `STUDY_PACK_READY`) — a pre-generated quiz is **not** required. The Teacher Exam Builder still requires a generated quiz (`canIncludeCollectionItemInExam` = `generatedQuizId`) because it exports that quiz.

The Study Plan premium-exam launch carries `collectionId` in the URL, not a caller-provided note list. Each exam prescreen fetches the collection, intersects its Study Pack-ready items with the user's Study Pack-ready notes, scopes the additional-notes picker to that plan set, and pre-selects up to the existing per-exam cap:

- Long Exam: primary note route plus up to 3 additional Study Pack ids
- Board Exam: primary Study Pack route plus up to 2 additional Study Pack ids
- Interview Practice: primary note route plus up to 2 additional note ids

If the collection cannot be loaded from a prescreen, the exam falls back to its normal single-note/same-subject setup. The Teacher Exam Builder path is unchanged and still receives the collection id plus quiz-ready note ids.

On a plan launch (`collectionId` present) the prescreen back link returns to the originating plan (`/collections/{collectionId}`) using the profile-aware label from `getCollectionLabels` (`Study Plan` / `Review Set` / `Collection`) rather than "← Note", and the additional-notes picker reads "Add up to N more notes from this plan" (the primary note stays implicit as "Built from …"; the footer total confirms all plan notes are included). Without `collectionId`, the back link and picker copy are unchanged.

Before launching, if one or more exam-eligible (Study Pack-ready) plan notes have not been practiced (`lastSessionCompletedAt === null`), the premium-exam CTA surfaces a soft advisory modal ("Review before the exam?") with `Review first` (stay on the plan) and `Start the exam anyway` (proceed). It is a recommendation, never a block, and routes straight through when every eligible note is already practiced. There is no persistence — it re-evaluates on each launch. The Teacher Exam Builder CTA is unaffected.

## Ownership Rules

Collections remain owner-private by default.

- A collection can contain only notes owned by the requesting user.
- Adding or ordering a note that does not exist or is not owned by the caller returns `NoteNotFoundException` / `404`.
- A note may belong to multiple collections.
- A note may appear at most once in a single collection.
- Adding an already-present note is idempotent and silently skipped.
- Deleting a collection deletes only the collection and item rows.
- Deleting a note removes that note's collection item rows through the `note_collection_items.note_id` FK cascade.
- Deleting a collection must never delete notes.
- Existing owner-scoped endpoints must use `findByIdAndOwnerUserId` semantics and must not expose private collections to other users.

Admin-published plans intentionally lift the read boundary only through public endpoints:

- `visibility=PUBLIC` collections are world-readable through `/collections/public`.
- `visibility=PRIVATE` collections are never returned by public endpoints and return `404` on public detail.
- Publishing is admin-only and requires a non-empty collection where every item note is already `PUBLIC`.
- Unpublishing returns the source collection to `PRIVATE`; adopted personal plans are unaffected.
- User/teacher-authored collection sharing remains deferred.

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
- `visibility`
- `courseProgram`
- `sourcePlanId`
- `itemCount`
- `notesPracticed`
- `createdAt`
- `updatedAt`

`notesPracticed` is included so the owned `/collections` list can show a lightweight execution-status badge without opening every plan. It is derived from the same practice definition as the detail rollup: a note counts as practiced when its latest completed quiz-session timestamp resolves to non-null (`lastSessionCompletedAt != null`). `itemCount` remains the total-note count; do not add a redundant `totalNotes` field to the summary DTO.

Owned-list status labels are frontend-derived from `notesPracticed` and `itemCount`:

- `Not started` — `notesPracticed == 0`, including empty plans where `itemCount == 0`
- `In progress` — `0 < notesPracticed < itemCount`
- `Completed` — `itemCount > 0 && notesPracticed >= itemCount`

This is execution status only: it answers whether the learner has practiced the plan's notes. It is not ConceptHealth mastery and must not add percentages, milestones, streaks, weakest-subject routing, or progress bars to collection list cards. Mastery remains owned by My Progress. The status badge is shown only on the authenticated user's owned `/collections` list and is not shown on `/collections/published` or public study-plan cards, where viewer-specific practice status has no meaning.

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
- `lastSessionCompletedAt`
- `dueConceptCount`
- `dueConcepts` (up to 3 ordered names for display)
- `updatedAt`

`studyPackStatus` uses the same note readiness rule as the Note API:

- note `GENERATED` -> `STUDY_PACK_READY`
- note `GENERATING` -> `GENERATING`
- note `FAILED` -> `FAILED`
- no linked Study Pack -> `DRAFT`
- linked Study Pack otherwise -> `STUDY_PACK_READY`

The detail response also includes a read-only `progress` summary:

- `totalNotes` — number of notes in the collection
- `notesWithStudyPack` — items whose resolved `studyPackStatus` is `STUDY_PACK_READY`
- `notesPracticed` — items whose `lastSessionCompletedAt` is not null

`lastSessionCompletedAt` uses the same batched per-note completed-session source as the private Library note list. It covers completed supported quiz modes, including participating notes from multi-note sessions, without issuing one query per collection item. If session history cannot be resolved, item timestamps degrade to null and the notes count as not practiced rather than failing the collection response.

The detail progress rollup is computed only for the collection detail response from the item data already assembled for that request. Collection list cards stay lightweight: they receive only `itemCount` plus the summary `notesPracticed` execution count and derive the three-label badge client-side.

The rollup is profile-agnostic and presentation-neutral. Frontend profile labels still come only from `getCollectionLabels`; the backend returns the same counts for Study Plans, Review Sets, Lesson Plans, and Collections. It adds no persisted progress field, generated content, AI call, or quota category.

Collection detail items also expose a read-only weak-area signal from the existing `ConceptHealthService` due-concept model:

- `dueConceptCount` is the full number of due key concepts for the note's Study Pack.
- `dueConcepts` contains the first 3 concepts in the existing deterministic due order.
- Concept health is loaded once for all Study Packs in the collection; the read path must not issue one query per item.
- The signal is populated only when the user has a Plus or Pro plan and the existing `Feature.ADAPTIVE_QUIZ` entitlement is available, matching the Note Detail concept-health surface.
- Free users and notes without a Study Pack receive `0` and an empty list. Lookup failures also degrade to empty weak-area data without failing collection detail.
- The backend remains profile-agnostic and does not branch on `ProfileType`.

### Update Metadata

`PATCH /collections/{id}`

Request:

- `title` optional, but if present it must be non-blank and max `150`
- `description` optional and nullable
- `courseProgram` optional and nullable; normalized with the same course/program normalization used by notes

Behavior:

- updates collection metadata
- bumps `updatedAt`
- returns full detail

### Publish / Unpublish Study Plan

`POST /collections/{id}/visibility`

Admin-only request:

- `visibility`: `PRIVATE` or `PUBLIC`

Behavior:

- publishing validates the collection is non-empty
- publishing validates every item note still has `visibility=PUBLIC`
- invalid publish attempts return `CollectionNotPublishableException` / `400`
- unpublishing to `PRIVATE` is allowed
- returns full detail

### Public Plan List

`GET /collections/public?courseProgram={value}`

Behavior:

- no authentication required
- returns only `visibility=PUBLIC` collections
- optional `courseProgram` filter is normalized before lookup
- private collections are never included

### Public Plan Detail

`GET /collections/public/{id}`

Behavior:

- no authentication required
- returns detail only when the collection is `PUBLIC`
- private or missing collections return `CollectionNotFoundException` / `404`
- stale private/deleted item notes are omitted from the public item payload rather than leaked

### Adopt Study Plan

`POST /collections/{id}/adopt`

Behavior:

- authenticated users can adopt only `PUBLIC` source collections
- if the caller already owns a collection with `sourcePlanId={id}`, the endpoint returns that existing personal plan id instead of creating a duplicate
- otherwise the endpoint iterates source items in saved order and calls `copyNote(noteId, userId, includeStudyPack=true)` for each still-public source note
- each source item is isolated; private, deleted, or otherwise unavailable notes are skipped and counted instead of failing the whole adoption
- the personal collection is `PRIVATE`, keeps `sourcePlanId={source id}`, mirrors the source title/description/courseProgram, and preserves copied item order plus labels
- adoption bills no quota and makes no AI calls
- `sourcePlanId` is lineage/idempotency only; source edits never sync into adopted personal plans
- server analytics fires `STUDY_PLAN_ADOPTED` with `sourcePlanId`, `copiedCount`, `skippedCount`, and `alreadyAdopted`

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
- Private or missing public-plan source/detail -> `CollectionNotFoundException` / `404`.
- Publish empty or any-private-note collection -> `CollectionNotPublishableException` / `400`.
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
- `/collections/[id]` shows one collection, its ordered note items, item labels, and a per-note execution-status hint.
- The per-note hint is a learner practice signal, not exam-readiness: `Needs Study Pack` (no `STUDY_PACK_READY` pack yet) → `Not started` (pack ready, `lastSessionCompletedAt == null`) → `Practiced` (`lastSessionCompletedAt != null`), with transient `Generating` / `Generation failed` states preserved for operational feedback. It deliberately does **not** show `Study Pack ready` / `Quiz ready` (the prior hint): plan-level Study Pack readiness already lives in the Progress rollup, and exam-eligibility (quiz-readiness) is surfaced on the Exam Builder, not here.
- `/collections/[id]` header actions: `Edit` and `Delete` live in a single `⋯` context menu (short labels, mirroring Note Detail); the teacher terminal action (`Build Exam`) sits at the bottom-left of the header card via the `PageHeader` `footer` slot, not crammed into the action row. Admin status is read reactively (SSR-safe).
- Admins see a published/private **status badge that is itself the publish control** (Notion-style): it sits **below the title** (mirroring Note Detail's visibility control), and clicking it (`aria-label="Publish settings"`, gear affordance) opens the publish modal. There is no separate `Publish settings` menu item or `Share` button. The boilerplate header description is omitted when the plan has no author-written description.
- The publish modal (not an inline panel): a Course/Program **combobox locked to known buckets** (`CourseProgramCombobox` with `allowCustom={false}` + `inlineDropdown` so the options panel renders in-flow and is not clipped by the modal's overflow — the plan's existing value is always kept selectable), a single `Publish` (requires a non-empty course/program) / `Unpublish` action, and a `Save` for course/program edits while published. The `X` is the only close affordance (no redundant `Close` button).
- Because adopters copy the plan's notes, the publish modal flags any still-private item notes and offers a one-tap `Make N public` (loops `updateNoteVisibility`); admins also see per-row `Private` badges on plan items. Private status is computed frontend-side by joining plan items against the owner's note list (`listNotes`) — no collection-item DTO change. `Publish` is disabled until every plan note is public, matching the backend rule that publishing requires all item notes `PUBLIC`.
- `/collections/[id]` shows a compact progress summary near the header: Study Packs ready, notes practiced, and a practiced/total progress bar.
- Entitled users see per-note due-concept counts and up to 3 concept names. Free users see no fabricated counts and may see one plan-aware upgrade affordance resolved through `getUpgradeCtas(currentPlan)`.
- A frontend-only `Next in this plan` card derives one action from the already-returned ordered items. It never calls a recommendation endpoint or persists recommendation state.
- The next-action phases are evaluated globally in this order, choosing the first matching note in saved order within each phase:
  1. First note without `STUDY_PACK_READY` -> `Generate Study Pack`.
  2. When all Study Packs are ready, first note with no completed practice -> `Study this note`.
  3. When all notes are practiced, first note with due concepts -> `Review due concepts` for entitled users only.
  4. Otherwise -> `All caught up in this plan`.
- The Next card links to `/notes/{noteId}` with `ref=/collections/{collectionId}` so Note Detail returns to the current plan.
- Empty collections show a neutral no-progress state and never calculate a percentage from `0/0`.
- The detail page loads from `GET /collections/{id}` on mount, so a hard refresh renders the persisted order.
- The detail page can edit metadata, delete the collection, add notes, remove notes, relabel items, and reorder items through the shipped CRUD API.
- Opening a note from the detail page passes `ref=/collections/{id}`, so the note's back link returns to the collection with the profile-aware label (via `getCollectionLabels`) instead of falling back to Library.

Profile-aware labels are resolved only through `frontend/lib/collection-labels.ts`.

### Browse published plans (`/collections/published`)

The Dashboard card surfaces only the top matching published plan, so publishing several plans per course/program hides all but one. `/collections/published` is the lightweight browse surface that lists **all** published plans matched to the learner's course/program (v0.31.1).

- Frontend-only listing. It reuses `GET /collections/public?courseProgram=` (via `listPublicStudyPlans`) plus the user's `GET /collections` to join each plan to an already-adopted personal collection (`sourcePlanId`) — no new endpoint.
- This is a surface for *plans*, not the Public Library (which is for *notes*).
- Each plan renders as a `PublicStudyPlanCard` with `Start this plan` (adopt → `POST /collections/{id}/adopt` → route to the new personal collection) or `Continue this plan` when already adopted. The skipped-note notice uses the shared `lib/study-plan-skipped-notice.ts` key, so the destination collection page shows the same one-time notice as Dashboard/onboarding adoption.
- `courseProgram` and `profileType` come from `getMe()`; labels resolve through `getCollectionLabels`. It is reached via the `See all N {plural}` link on the Dashboard card (shown only when 2+ plans match).
- States: loading skeleton, error + retry, a guidance state when no course/program is set (links to `/profile`), and an empty state when the track has no published plans. `BackLink` returns to the Dashboard.

The Study Plan remains an execution surface for one curated, ordered set. It does not duplicate Progress: no subject mastery percentages, milestones, goals, streaks, or weakest-subject routing belong on collection detail.

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

### Profile-aware terminal actions & Library integration

The shipped Prompt B integrations make collections useful from both entry points:

- The Library header is a split button: primary `New Note` plus a caret menu (`Note` / `Import files` / `{singular}`). There is no standalone `Select` button. Choosing `{singular}` enters Library **selection mode** — filter and multi-select notes, then `Create {singular}` (a title modal → `createCollection`); creating with zero notes (an empty plan) is allowed. This is the universal way to create a plan, leveraging the Library's filters; the Study Plan detail's "Add notes" picker still handles adding notes to an *existing* plan.
- Teachers reach `Build exam` (Exam Builder) from the same selection — both `Create {singular}` and `Build exam` act on the selected notes; no separate Select entry.
- Library selection accepts any owned note, including `DRAFT` notes without a generated quiz. Quiz readiness is not a plan membership requirement (it is only required for `Build exam`).
- The Library teacher-only `Build exam` action remains gated to Teacher/Admin exam workflows. If the selection mixes ready and non-ready notes, the action proceeds with the selected note IDs and the Exam Builder filters to quiz-ready notes; if zero selected notes are quiz-ready, the action is disabled with recovery copy.
- The collection detail terminal action resolves through the profile-aware terminal-action resolver. `TEACHER` receives `Build exam from this Lesson Plan`; all other profiles receive no terminal CTA for now.
- The teacher terminal CTA passes the collection identity through `/library/exam-builder?collectionId={id}` and may also include ordered quiz-ready note IDs as a resilience fallback. The collection ID is the source of truth for initial sectioning.
- Exam Builder fetches the collection and pre-seeds one section per distinct trimmed item label, in first-occurrence order. Only quiz-ready notes are included, notes keep collection position order, and unlabeled quiz-ready notes collapse into one trailing default section. Labels with no quiz-ready notes create no empty section.
- When a collection contains notes without a generated quiz, the Exam Builder no longer drops them silently: it shows an amber `N of M notes excluded — no quiz generated yet` notice listing those note titles, so a teacher can see exactly which notes to generate quizzes for. This is the canonical place for the quiz-readiness blocker (it is not duplicated on the Study Plan detail rows). Amber, not red — a missing quiz is an incomplete state, not an error.
- Collections with only unlabeled quiz-ready notes seed one default section. Teachers can still rename, reorder, add, rebalance, and replace the initial structure with an existing Exam Builder template.
- The terminal CTA keeps the existing partial-readiness hint when some notes are skipped and disables with `Generate a quiz for at least one note to build an exam.` when none are quiz-ready.
- This handoff is frontend-only. DOCX export and shareable quiz links remain Teacher/Admin-only, and no collection-level generation, analytics, quota, backend, or AI behavior is added.
- Student, board-exam, and professional multi-note practice terminal CTAs are deferred. The existing Long Exam flow is same-subject scoped and meters quota per source note, while collections can be cross-subject and mixed-readiness; a collection-level practice action needs a separate product-shape pass.
- `COLLECTION_CREATED` fires server-side from `NoteCollectionService.create(...)` only, with `itemCount` metadata for the number of initial notes. Add-items, update, remove, and reorder do not fire a creation event.

Deferred Prompt B slots:

- Student, board-exam, and professional multi-note practice actions remain a follow-up for the Long Exam same-subject/per-note-quota reason above.

## Out Of Scope

Do not add these under the collection CRUD spine unless explicitly scoped later:

- profile-aware labels or CTAs in the backend
- DOCX/shareable quiz-link generation directly from collections
- collection-level AI synthesis
- bulk generate across a collection
- user/teacher-authored published or shared collections (admin-published plans shipped in v0.31.0; non-admin publishing stays deferred)
- live-link or shared-progress adopted plans
- plan browse directory
- lesson-plan document parsing
