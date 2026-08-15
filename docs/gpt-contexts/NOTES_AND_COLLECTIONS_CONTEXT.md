# NOTES_AND_COLLECTIONS_CONTEXT.md - NoteLib Structural Handoff

> Paste the block below as context when discussing Library / note-organization design with GPT.
> Scope: how a single Note is structured, how its taxonomy fields are sourced, what Bulk Generate
> accepts, and how notes are grouped into collections today (Study Plans, Exam Hub, Public Library
> subject pages). This is a structural snapshot, not a proposal — it describes what exists now so a
> design conversation starts from real constraints instead of assumptions.
> Update this file when the Note schema, taxonomy model, or collection model changes.
> Last updated: v0.78.0 — 2026-08-15. **`v0.78.0` gave `NoteCollectionItemEntity.position` a second
> consumer:** it now orders the post-mastery "next in your plan" suggestion, alongside the collection
> progress definition of practiced (`lastSessionCompletedAt != null`), so a change to item ordering or the
> practice-timestamp contract affects that surface too. The Dashboard also recommends ONE named
> program-matched published plan again (never a grid — Explore still owns browse), amending `v0.67.0`.
> **Production sizing worth carrying into any collections conversation: 69% of learners with a Study Pack
> have nothing in a plan at all**, and plan membership is heavily concentrated in ~85 accounts. Previously
> stamped v0.76.0 — 2026-08-14. **Rewritten for `ADR-001`'s five-axis model**; the
> previous stamp was `v0.50.3` (2026-07-17) and predated Domain Context, note-level learner depth,
> the `course_programs` catalog, and many-to-many applicability. If you are working from a cached
> copy of this file that says a Note has no learner level, or that Course / Program is free text with
> no lookup table, discard it — both statements are now false.

---

## 0. If you are proposing notes or collections, read this section and §2 first

**The single most important change: one note can be applicable to many programs, so do not propose the same note once per program.** An Algebra note authored once for `Engineering Mathematics` surfaces under Civil, Electrical *and* Mechanical Engineering. Proposing "Algebra for Civil Engineering", "Algebra for Mechanical Engineering" as separate notes is the duplication the whole architecture exists to remove. If two proposed notes would differ only by which program they serve, they are **one** note with several Applicable Programs.

### The five axes of a note

Every note carries five independent metadata axes. They are not interchangeable, and two of them **never reach the LLM** — proposing content that depends on them shaping generation is a category error.

| Axis | Answers | Reaches the generation prompt? |
|---|---|---|
| **Subject** | *what* is this about — the library shelf (`Algebra`, `Pharmacology`) | Yes |
| **Domain Context** | *how* it is authored — **the sole LLM domain constraint** | **Yes — this is the one that shapes voice and framing** |
| **Note Learner Level** — labelled **“Authored Depth”** in the UI since `v0.75.0` (copy-only; the column is still `notes.learner_level`) | *how deep* — the curriculum floor | Yes |
| **Applicable Programs** | *where* it appears — one or many catalog programs | **Never.** Discovery only |
| **Target Audience** | *who* it is for | **Never.** Discovery only, and never depth |

### What to give us when you propose a note

1. **Title**
2. **Subject** — specific. If the subject you'd write is the same as the Domain Context, it is too broad to be a useful shelf. `Algebra`, not `Engineering Mathematics`.
3. **Domain Context** — one of the **8 ratified values**, below. This is what makes generated content sound like it belongs to the field.
4. **Note Learner Level** — `GRADE_SCHOOL` · `JUNIOR_HIGH` · `SENIOR_HIGH` · `COLLEGE` · `BOARD_EXAM_REVIEW` · `PROFESSIONAL`. This is the curriculum **floor**: a reader at a lower level gets softer scaffolding and wording, never easier curriculum.
5. **Applicable Programs** — one or more catalog names, below. **Say all of them.** Under-listing recreates the duplication problem; you cannot "add the others later" without a curator editing every note by hand.
6. **Target Audience** — `STUDENT` · `BOARD_TAKER` · `PROFESSIONAL`.

**Hard rule: if a note has more than one Applicable Program, Domain Context is required.** The API rejects the save otherwise (`MultiProgramDomainContextRequiredException`) — a note serving several programs has no single program to infer its authoring domain from, so it must be stated.

### The 8 ratified Domain Context values

`Engineering Mathematics` · `Engineering Sciences` · `Civil Engineering` · `Professional Practice & Regulation` · `General Education` · `Professional Education` · `Nursing` · `Accountancy`

**Do not invent a ninth.** `Architecture` is deliberately *not* a Domain Context despite carrying ~837 notes across five subject plans — a program can be among the largest in the library and still not warrant one. Notes in a domain with no ratified value fall back to the program name, which is a supported path, not a failure. Adding a value is a governance decision, not an authoring one.

### The 21 catalog programs

`Education` · `Architecture` · `Nursing` · `Accountancy` · `Civil Engineering` · `Information Technology` · `Pharmacy` · `Electrical Engineering` · `Mechanical Engineering` · `Physical Therapy` · `Senior High – ABM` · `Senior High – STEM` · `Senior High – HUMSS` · `Medicine` · `Criminology` · `Law` · `Aviation` · `Business Administration` · `Psychology` · `Radiologic Technology` · `Special Needs Education – Generalist`

**The catalog follows authoring, it does not lead it.** A program earns an entry once legitimate canonical notes are applicable to it — pre-seeding every PRC program was explicitly rejected as premature. So: if the notes you are proposing genuinely need a program that is not on this list, **say so explicitly as a catalog request**, with what notes would justify it. Do not quietly use a name that isn't there; a non-catalog name is stored as personal free text and will not appear on the program shelves.

**One Program Family exists:** `Engineering` = Civil + Electrical + Mechanical. Selecting the family expands to all three as explicit selections, which the author can then trim. It is a **productivity shortcut, deliberately allowed to over-select** — never a curriculum statement. Applicability is never inferred from a family at read time.

### Programs and Review Sets answer different questions — do not conflate them

| Surface | Answers |
|---|---|
| **Applicable Programs** | *"What notes are applicable to me?"* — a **discovery** surface |
| **Review Set / Study Plan** | *"What is my complete learning journey?"* — the **completeness** signal |

A program shelf is allowed to be partial; it is a filter. A Review Set claims completeness, so proposing one commits to covering its scope. When you propose a **collection**, say which of the two you mean — they have different bars.

---

## 1. What a Note is

Backend: `NoteEntity` (`backend/src/main/java/com/studysnap/backend/entity/NoteEntity.java`).

| Field | Type | Notes |
|---|---|---|
| `title` | String | |
| `content` | String | the note body itself — this is the source of truth users write |
| `subject` | String, ≤64 chars | free text, see taxonomy section below |
| `courseProgram` | String, ≤120 chars | the learner's **personal** free-text program. Since `v0.71.0` this is one of two representations — see §2 |
| `domainContext` | enum, nullable | **the sole LLM domain constraint** (`v0.69.0`). 8 ratified values, see §0 |
| `learnerLevel` | enum, nullable | the note's own authored depth (`v0.69.0`). See correction below |
| `tags` | `text[]` | free-form tag list |
| `status` | enum `NoteStatus` | `DRAFT` / `GENERATING` / `FAILED` / `GENERATED` |
| `visibility` | enum `NoteVisibility` | `PRIVATE` / `PUBLIC` |
| `targetProfileType` | enum `NoteTargetProfileType` | `STUDENT` / `BOARD_TAKER` / `PROFESSIONAL` |
| `sourceNoteId` | FK | set when a note was generated from another note |
| `copiedFromNoteId` / `copiedFromUserId` / `copiedFromTitle` / `copiedFromPublic` / `copiedAt` | | provenance when this note is a copy of a public note |
| `createdAt` / `updatedAt` | | |

**Since `v0.75.0`, depth is PRE-FILLED for curators rather than asked for.** The chain is **Review Set → author profile → explicit override**. A Note Collection now carries its own optional `learnerLevel`, inherited down one level from a Goal to its child plans, and bulk-generate can author straight into a Review Set — pre-filling depth from it and adding the finished notes to it on completion. **Three constraints bind:** it is a **UI pre-fill only, never a server-side default write**; it applies to **curators only**, because the control is not shown to learners, so nothing is written on their behalf; and it **never touches an existing note**, because a depth change on a generated note strands its Challenge-bank rows at the old level. **Domain Context is never inferred** — no source is authorized for it, and `domain_context IS NULL` is the promotion-backlog marker a default would destroy.

**CORRECTED — a Note now DOES own its depth.** This file previously said *"There is no `learnerLevel` field on a Note… learner level is a property of the user's profile, not the note."* That was true until `v0.69.0` and is now false. `notes.learner_level` exists and is authored per note.

The rule that replaced it: the note's level is the **curriculum floor**. Static note and Study Pack content is calibrated by the effective Domain Context plus the note's authored level — never by the reader's level. Quizzes and exams take both as the floor: a reader below the note's level gets softened wording and more scaffolding, but **never lower curriculum, terminology, or difficulty**. Persisted question pools and the Challenge bank key on the note's effective level, not the reader's, so a reader changing their profile level does not invalidate content authored for a note that carries its own.

**Also new: `note_course_program`**, a join table (`v0.71.0`) holding a note's curated Applicable Programs. It is separate from the `courseProgram` string above; see §2 for which one is read when.

A Study Pack (`StudyPackEntity`) is generated *from* a note and carries its own copy of `title`, `summary`, `subject`, `keyConcepts`, and quiz data. It is 1:1 with a note once generated and is updated in-place on regeneration — never versioned as a separate row (see CLAUDE.md's "Versioning rule").

Canonical doc: `docs/features/notes.md`.

## 2. Taxonomy: `subject`, and the two program representations

**CORRECTED.** This section previously said Course / Program is *"free text with suggestions, not a fixed enum or lookup table."* That is now half true. **`subject` is still free text with live suggestions** — everything below about the combobox convention still holds for it. **Course / Program is now two representations**, and which one a note uses depends on who authored it:

| Shape | `notes.course_program` | `note_course_program` (join) |
|---|---|---|
| **Learner-authored personal note** | the program, free text | no rows |
| **Curator-authored note** | null | one or more catalog rows |
| **Curated note copied by a learner** | as copied | inherited rows, preserved as authored metadata |

**Read semantics are identical for all three and never consult ownership:** joined programs first when they exist, otherwise the personal string. A learner-authored note served by the personal-string fallback is a **canonical, fully supported shape — not a degraded one**.

**For your purposes as a strategist: you are proposing curator-authored notes**, so you are always in row 2 — catalog program names, `courseProgram` null. The free-text row exists for learners writing their own notes and is not a path you should propose into.

**There is no "primary" program any more** (`v0.71.0` slice 4). A single `Course / Program(s)` picker replaced the old two-field model everywhere. Do not propose a primary-plus-secondary structure.

The rest of this section describes the still-current free-text convention, which governs `subject` and the learner's personal program:

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
| `courseProgramIds` | curator: yes | catalog ids, applies to the whole batch (`v0.71.0`) |
| `courseProgramText` | learner: yes | free text, applies to the whole batch |
| `domainContext` | required if >1 program | applies to the whole batch |
| `targetProfileType` | no, enum | optional, applies to the whole batch |
| `makePublic` | no, boolean | |

**CORRECTED:** this table previously listed a single free-text `courseProgram` and said learner level *"is never note-level."* Both changed — see §1 and §2.

Bulk Generate sets taxonomy **once per batch, not per note** — every note in a batch shares the same subject, programs, Domain Context and audience. **This is the main practical constraint on how you propose batches:** notes that need different Applicable Programs or a different Domain Context cannot share a batch. Group your proposals by that boundary, not just by subject. `tags` remain non-bulk-settable.

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
- **Exam Hub** (`/exam/{slug}`) — all public notes whose program is in that slug's configured allow-list (see §2's fixed-list exception). Curated at the *taxonomy* level, not by hand-picking notes. Since `v0.71.0` this resolves through the join first and falls back to the legacy string, like every other discovery read.
- **Private Library filters / Saved Filters** — a saved query (subject, courseProgram, tag, etc.), re-run each time, not a snapshot of specific notes. Kept intentionally distinct from Study Plans, which *are* a snapshot (`docs/features/collections.md` calls this out explicitly).

## 6. One consequence to expect in the UI

**Facet counts can sum above the note total, and that is correct, not a bug.** A note applicable to three programs appears under all three. If you are reasoning about library size or coverage from facet numbers, do not add them up.

## 7. Canonical docs for a deeper dive

- `docs/architecture/ADR-001-canonical-knowledge-architecture.md` — **the binding decision record for everything in §0 and §2.** Read this before proposing anything that touches how notes are classified
- `docs/features/notes.md` — Note lifecycle, fields, create/edit behavior
- `docs/features/collections.md` — Study Plans / NoteCollection, the most thorough doc of this set
- `docs/features/bulk-generation.md` — Bulk Generate contract
- `docs/features/exam-hub.md` — Exam Hub grouping
- `docs/features/library.md` — private Library page (list/filter/stats surface over notes)
- `docs/features/public-library.md`, `docs/features/public-notes.md` — public discovery surfaces
