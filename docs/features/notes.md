# notes.md - NoteLib Feature Context

## Goal

Notes are the primary user-authored workspace in NoteLib. Users organize note metadata first, then turn notes into Study Packs for review.

## Key Files

**Backend**
- `backend/src/main/java/com/studysnap/backend/controller/NoteController.java` — `GET /notes` (private list), `POST /notes`, `PUT /notes/{id}`, `DELETE /notes/{id}`, `GET /notes/public` (public filter endpoint); subject/courseProgram suggestion endpoints
- `backend/src/main/java/com/studysnap/backend/service/NoteService.java` — note CRUD, `listMine(userId)`, `listPublic(...)`, visibility change, note copy, subject/courseProgram autocomplete
- `backend/src/main/java/com/studysnap/backend/service/NoteBulkImportService.java` — bulk material import orchestration; one extracted file becomes one `DRAFT` note without Study Pack generation
- `backend/src/main/java/com/studysnap/backend/entity/NoteEntity.java` — note schema: `title`, `content`, `subject`, `courseProgram`, `tags`, `visibility`, `studyPackStatus`, `targetProfileType`, `ownerUserId`
- `backend/src/main/java/com/studysnap/backend/repository/NoteRepository.java` — JPQL queries for private/public note lists, subject/courseProgram suggestion queries

**Frontend**
- `frontend/app/notes/new/page.tsx` — Create Note route (write / generate-from-topic / import start options)
- `frontend/app/notes/import/page.tsx` — profile-agnostic bulk import route; creates one reviewable `DRAFT` per successful file without generation
- `frontend/app/notes/[id]/page.tsx` — Note Detail route (server component entry)
- `frontend/app/notes/[id]/edit/page.tsx` — Edit Note route
- `frontend/components/notes/note-editor-page-client.tsx` — shared editor client for create and edit modes
- `frontend/components/notes/private-note-detail-page-client.tsx` — Note Detail client; Study Pack status polling; AI suggestion modal trigger; quiz mode entry points
- `frontend/components/notes/ai-suggestion-modal.tsx` — post-generation AI metadata suggestions (title / subject / tags)
- `frontend/lib/api.ts` — `listNotes()`, `createNote()`, `updateNote()`, `deleteNote()`, `updateNoteVisibility()`, `copyNote()`

## Anti-drift Notes

- Note content is **locked** after Study Pack generation (`STUDY_PACK_READY`) — do not re-enable the content editor for ready notes; only title, courseProgram, subject, and tags remain editable
- `Generate Study Pack` saves the note then navigates immediately to Note Detail — **never wait** for LLM completion before navigation; generation is always async
- `courseProgram` on the note is the **authoritative** source for generation context; user profile `courseProgram` is fallback only when the note field is blank
- AI-generated subject must be a reusable academic label with **no topic suffix** — strip anything after `–`, `:`, or `-` before saving (e.g. `Biology – Cell Division` → `Biology`)
- The share modal/gate is the **same everywhere**: public content → share modal directly; private content → confirm-to-make-public modal first; do not invent content-specific flows
- **Official author** is determined by `UserRole.ADMIN` only — `isOfficialAuthor(user)` returns `user != null && user.getRole() == UserRole.ADMIN`; the old email-based `isNoteLibOfficialAccount()` method has been removed; do not recreate it
- **Public author name** resolution order: admin user's `displayName` if set → `"NoteLib"` fallback for admin with no displayName → non-admin user's `displayName` → `firstName` → `"Anonymous learner"`
- Target Audience is **hidden and auto-prefilled** for Student, Board Exam, and Professional profiles; **visible and user-picked** for Teacher/Admin

## Note metadata

Current note-authoring fields:

- `title` (optional)
- `learnerLevel` (required before save/generate; pre-filled from the user's profile)
- `courseProgram` (required before save/generate; pre-filled from the user's profile)
- `subject` (optional)
- `tags` (optional)
- `content` (required before save/generate)

Rules:

- `courseProgram` belongs to the note once saved and can differ from the profile default.
- `courseProgram` is the top-level shelf for the note, while `subject` is the more specific academic topic and `tags` are the finer keywords.
- `courseProgram` uses autocomplete from curated defaults plus normalized saved values from the user's notes/profile.
- typing in `courseProgram` filters suggestions in real time instead of keeping the full list visible
- course/program matching is case-insensitive, trims outer spaces before matching, allows partial matches, and ranks prefix matches ahead of contains matches
- existing matching suggestions stay above the custom `Use "..."` action so reuse is easier than creating a variant
- users can still type a custom course/program directly.
- a saved custom course/program becomes available in future course/program suggestions after the note or profile value is persisted.
- course/program saves normalize whitespace plus dash formatting so equivalent values like `Senior High-STEM` and `Senior High – STEM` reuse the same suggestion when possible.
- course/program reuse checks are case-insensitive, but the saved/displayed course/program should keep a readable label format.
- when the typed value exactly matches an existing saved course/program case-insensitively, the field should reuse the existing saved label instead of preserving a duplicate case variant.
- note-level Course / Program helper text should adapt to the user's `learnerLevel` so note metadata examples match their study stage.
- `subject` uses autocomplete from persisted note subjects.
- users can still type a custom subject directly.
- a saved custom subject becomes available in future subject suggestions after the note is persisted.
- subject saves normalize whitespace plus dash formatting so equivalent values like `Biology-Cell Division` and `Biology – Cell Division` reuse the same subject suggestion when possible.
- subject reuse checks are case-insensitive, but the saved/displayed subject should keep a readable academic label format.
- AI-generated subject should be a reusable academic subject or curriculum category — label only, no topic suffix:
  - correct: `Biology`, `Physics`, `Mathematics`, `Computer Science`, `English`, `Filipino`, `Civil Engineering`, `Nursing`, `Accountancy`, `Criminal Law`
  - incorrect: `Biology – Cell Division`, `Physics: Ohm's Law`, `Mathematics – Derivatives`
- Overly broad AI subject suggestions such as `Engineering`, `Medicine`, `Business`, and `Law` are ignored safely and must not fail Study Pack generation.
- Topic-level specificity belongs in tags and key concepts, not in subject
- Subject is a reusable library shelf label — it should group many notes, not describe one note
- The backend strips any subtopic suffix (`Biology – Cell Division` → `Biology`) before saving
- `courseProgram` is required before saving or generating a Study Pack and is pre-filled from the user's profile so validation rarely blocks users who completed onboarding.
- `learnerLevel` remains required on completed profiles for quiz/exam personalization, but it is not a static content-leveling input and a null legacy value must not break note or Study Pack generation.
- Note-from-topic and Study Pack content use note-first `courseProgram` to calibrate depth, vocabulary, terminology, and examples. They must not use learner level for static content.
- `learnerLevel` may remain in generation context for quiz/exam generation and exam-question pool pre-warm; content generation must tolerate a null level.
- tags stay optional and should include helper guidance rather than mandatory validation pressure.

## Shared Share Behavior Rule

NoteLib uses one share behavior across all content types (notes and profiles):

- **Public content**: clicking Share opens the share modal with the shareable URL.
- **Private content**: clicking Share opens a confirm modal that offers to make the content public before sharing. The share modal never opens for private content without owner confirmation first.

This rule applies consistently to both notes and profiles. Do not invent content-specific share flows. The same modal component and the same public/private gate must be used everywhere.

Notes private confirm:
- title: `This note is private`
- body: `You need to make this note public before sharing. Anyone with the link will be able to view and copy this note.`
- buttons: `Cancel` / `Make Public & Share`

Profiles private confirm:
- title: `This profile is private`
- body: `You need to make this profile public before sharing. Anyone with the link will be able to view your public profile and notes.`
- buttons: `Cancel` / `Make Public & Share`

## Create and edit behavior

Create mode:

- route: `/notes/new`
- primary layout order:
  - `Choose how to start`
  - start options:
    - `Write your own note`
    - `Generate from topic`
    - `Import notes`
  - optional topic generation panel
  - optional import panel
  - note `Content`
  - `Add details` disclosure
- actions: `Save`, `Generate Study Pack`
- optional topic-first helper: `Generate Note`
- the `Import notes` start option reuses the existing OCR/file-extraction flow and inserts extracted text into the main editor before save or Study Pack generation.
- `/notes/import` is the separate bulk note-creation entry point, reached from the Create-note flow's `Import notes` panel (a "Bulk import multiple files" link). It sends multiple files through `POST /notes/import-batch`, creates one `DRAFT` note per successfully extracted file directly, and never triggers Study Pack generation or LLM calls.
- note metadata fields (`title`, `subject`, `courseProgram`, `tags`, and teacher/admin `Who is this note for?`) stay available in the collapsed `Add details` section by default so first-time note creation stays focused on content.
- Target Audience is required on every note. For Student, Board Exam, and Professional profiles the field is hidden and auto-prefilled from profile type at save time (Student -> Student, Board Exam -> Board Taker, Professional -> Professional). For Teacher and Admin profiles the field is visible and user-picked, with all audience values selectable.
- create mode should keep a subtle inline prompt near the primary actions so users can reveal `Add details` without turning the page back into a long form.
- `Generate Study Pack` first saves the note, queues Study Pack generation, then redirects immediately to Note Detail with the requested default tab.
- the editor must not wait for the LLM request to finish before navigation.
- `Generate Note` creates a structured first draft from a topic with clear sections (`Overview`, `Core Concepts`, `Key Details`, optional `Examples`) and should avoid meta filler or instructional language.
- `Generate Note` must build its request from the current Create Note form state at submit time. The selected draft Course / Program is authoritative for the first generated note; the profile Course / Program is fallback only when the draft field is blank.
- `Generate Note` calibrates the generated draft from that resolved Course / Program, not from the owner's learner level, so copied notes retain a shared academic depth signal.
- topic note generation is plan-gated separately from Study Pack generation and OCR.

Bulk import behavior:

- route: `/notes/import`
- available to every authenticated, onboarded profile through the Create-note flow's Import notes panel
- accepts multiple uploaded files in one request through `POST /notes/import-batch`
- reuses the existing single-file extraction pipeline once per file
- creates one owned `DRAFT` note per successful file using the extracted text as content and a filename-derived title
- leaves `subject`, `courseProgram`, `tags`, and target audience unset/defaulted for later user review
- records per-file failures in the response while continuing with the remaining files
- does not auto-generate, does not create Study Packs, and does not add a new quota category
- may offer a skippable post-import action to add the created drafts to an existing or new collection; this action is always user-initiated and never automatic
- is not idempotent; submitting the same files again creates new draft notes

Edit mode for draft notes:

- route: `/notes/{id}/edit`
- actions: `Save Changes`, `Cancel`, `Generate Study Pack`
- `Generate Study Pack` saves the latest draft, queues generation, and redirects immediately to Note Detail.

Edit mode for Study Pack Ready notes:

- route: `/notes/{id}/edit`
- editable metadata: `title`, `courseProgram`, `subject`, `tags`
- locked field: `content`
- helper text: `Note content cannot be edited after generating a Study Pack. You can still update the title, course/program, subject, and tags.`
- actions: `Save Changes`, `Cancel`, `Make a Copy`

## AI metadata suggestions

After a user explicitly generates or regenerates a Study Pack, the shared AI suggestion modal should let them decide field by field. Copied public Study Packs that launch Quick Review are not regenerated and must not open this modal. If any future generated flow also requests an immediate next action, that action must wait until the metadata suggestion is resolved so a real generation's reconciliation choice is never skipped.

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
- because generation is asynchronous from the Note Editor, the suggestion modal appears from Note Detail after the queued Study Pack becomes ready.
