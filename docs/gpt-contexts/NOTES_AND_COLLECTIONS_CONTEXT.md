# NOTES_AND_COLLECTIONS_CONTEXT.md - NoteLib Structural Handoff

> Paste the block below as context when discussing Library / note-organization design with GPT.
> Scope: how a single Note is structured, how its taxonomy fields are sourced, what Bulk Generate
> accepts, and how notes are grouped into collections today (Study Plans, Exam Hub, Public Library
> subject pages). This is a structural snapshot, not a proposal — it describes what exists now so a
> design conversation starts from real constraints instead of assumptions.
> Update this file when the Note schema, taxonomy model, or collection model changes.
> Last updated: v0.50.3, 2026-07-17

---

## 1. What a Note is

Backend: `NoteEntity` (`backend/src/main/java/com/studysnap/backend/entity/NoteEntity.java`).

| Field | Type | Notes |
|---|---|---|
| `title` | String | |
| `content` | String | the note body itself — this is the source of truth users write |
| `subject` | String, ≤64 chars | free text, see taxonomy section below |
| `courseProgram` | String, ≤120 chars | free text, see taxonomy section below |
| `tags` | `text[]` | free-form tag list |
| `status` | enum `NoteStatus` | `DRAFT` / `GENERATING` / `FAILED` / `GENERATED` |
| `visibility` | enum `NoteVisibility` | `PRIVATE` / `PUBLIC` |
| `targetProfileType` | enum `NoteTargetProfileType` | `STUDENT` / `BOARD_TAKER` / `PROFESSIONAL` |
| `sourceNoteId` | FK | set when a note was generated from another note |
| `copiedFromNoteId` / `copiedFromUserId` / `copiedFromTitle` / `copiedFromPublic` / `copiedAt` | | provenance when this note is a copy of a public note |
| `createdAt` / `updatedAt` | | |

**There is no `learnerLevel` field on a Note.** Learner level is a property of the *user's profile*, not the note — it's read at generation/quiz time, never stored per-note. Do not propose adding it to Note without understanding why it was kept off (see `docs/product/SPEC.md` generation-context resolver rule).

A Study Pack (`StudyPackEntity`) is generated *from* a note and carries its own copy of `title`, `summary`, `subject`, `keyConcepts`, and quiz data. It is 1:1 with a note once generated and is updated in-place on regeneration — never versioned as a separate row (see CLAUDE.md's "Versioning rule").

Canonical doc: `docs/features/notes.md`.

## 2. Taxonomy: `subject` and `courseProgram`

Both are **free text with suggestions, not a fixed enum or lookup table** — this is a deliberate, twice-enforced convention.

- Suggestions are served live from *actual usage in the DB*, not a static list: `GET /course-programs?scope=public|mine` (`NoteService.listPublicCoursePrograms()` / `listMineCoursePrograms()`) and the equivalent subject endpoints. The suggestion list is "what other notes already use," not a curated taxonomy.
- Normalization only, no constraint: `SubjectNormalizationUtils` / `CourseProgramNormalizationUtils` collapse whitespace and standardize dashes. Any string within the length limit is valid.
- Frontend always presents these as a **combobox with `allowCustom=true`** (`components/metadata/course-program-combobox.tsx`, `components/notes/subject-combobox.tsx`) — pick a suggestion or type your own, never a plain text input and never a locked dropdown. This pattern has regressed twice (Bulk Generate, then the Adoptable Study Plans publish card) and is now a standing rule: any new form touching these fields must reuse the shared combobox component.

**One fixed-list exception:** Exam Hub. `ExamGoalConfig.java` (backend) and `frontend/lib/exam-hub-config.ts` hardcode exactly 3 exam slugs — `ALE` (Architecture), `PNLE` (Nursing / Medical-Surgical Nursing), `LET` (Education) — each mapping to an allowed list of `courseProgram` strings. This powers Exam Hub landing pages only; it does not constrain what a user can type into `courseProgram` generally, it just decides which exam hub (if any) a given `courseProgram` value routes into.

## 3. Bulk Generate metadata

`BulkGenerateNotesRequest` (backend DTO) — one note generated per topic:

| Field | Required | Notes |
|---|---|---|
| `subject` | yes, ≤160 chars | one subject for the whole batch |
| `topics` | yes, list | one note per topic |
| `courseProgram` | no, ≤160 chars | optional, applies to the whole batch |
| `targetProfileType` | no, enum | optional, applies to the whole batch |
| `makePublic` | no, boolean | |

Notably **not** bulk-settable: `tags`, learner level (learner level is never note-level, see §1). Bulk Generate sets taxonomy once per batch, not per note — every note in a batch shares the same `subject`/`courseProgram`.

Canonical doc: `docs/features/bulk-generation.md`.

## 4. Note Collections (product name: "Study Plan")

This is the one real *membership-based* grouping of notes in the system — a durable, ordered, many-to-many structure, distinct from a filtered/queried view.

- **Entity:** `NoteCollectionEntity` — `title`, `description`, `visibility` (`PRIVATE`/`PUBLIC`, only admin-published plans go `PUBLIC`), `courseProgram`, `estimatedStudyHours`, `targetCompletionDate`, `companion`/`companionStructureSnapshot` (Learning Companion JSONB), `sourcePlanId` (adoption lineage), `parentCollectionId` + `siblingPosition`.
- **Membership:** explicit join table `NoteCollectionItemEntity` (`note_collection_items`) — `collectionId`, `noteId`, optional `label` (free text, frontend-interpreted only; backend treats it as opaque), `position` (int — the sole order source of truth). A note's membership in a collection is a row, not a query result.
- **Sections within a plan** are a frontend-only grouping by matching `label` string — there is no separate section entity.
- **Two-level nesting only:** a top-level **Goal** collection can contain child **Subject** plans via `parentCollectionId` (v0.33.1, deliberately scoped). Deeper nesting, per-module mastery/readiness, and arbitrary curriculum metadata are explicitly out of scope.
- **Adoptable Study Plans:** an admin can publish a collection as an adoptable plan over already-public notes (v0.31.0 exception to an earlier "no independently adoptable sub-plans" rule). Learners don't study the source plan directly — adopting creates a **private snapshot copy** into the learner's own library, reusing the public-note-copy spine with `includeStudyPack=true`. The published source plan stays a snapshot source, never a live link.
- A collection is explicitly documented as **not** a new content type, not AI-synthesized, not a Study Pack, and not a quota bucket (`docs/features/collections.md`, 939 lines — the deep-dive doc, read before proposing anything collection-shaped).

## 5. Other note groupings (query-filtered, not membership-based)

These look like "collections" in the UI but are **not** backed by a join table — they're a live query filtered by a taxonomy field, recomputed on every load:

- **Public Library subject pages** (`/public/library/{subject}`) — all public notes where `subject` matches, no stored membership.
- **Exam Hub** (`/exam/{slug}`) — all public notes where `courseProgram` is in that slug's configured allow-list (see §2's fixed-list exception). Curated at the *taxonomy* level, not by hand-picking notes.
- **Private Library filters / Saved Filters** — a saved query (subject, courseProgram, tag, etc.), re-run each time, not a snapshot of specific notes. Kept intentionally distinct from Study Plans, which *are* a snapshot (`docs/features/collections.md` calls this out explicitly).

## 6. Canonical docs for a deeper dive

- `docs/features/notes.md` — Note lifecycle, fields, create/edit behavior
- `docs/features/collections.md` — Study Plans / NoteCollection, the most thorough doc of this set
- `docs/features/bulk-generation.md` — Bulk Generate contract
- `docs/features/exam-hub.md` — Exam Hub grouping
- `docs/features/library.md` — private Library page (list/filter/stats surface over notes)
- `docs/features/public-library.md`, `docs/features/public-notes.md` — public discovery surfaces
