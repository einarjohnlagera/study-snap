# notes.md - NoteLib Feature Context

## Goal

Notes are the primary user-authored workspace in NoteLib. Users organize note metadata first, then turn notes into Study Packs for review.

## Key Files

**Backend**
- `backend/src/main/java/com/studysnap/backend/controller/NoteController.java` — `GET /notes` (private list), `POST /notes`, `PUT /notes/{id}`, `DELETE /notes/{id}`, `GET /notes/public` (public filter endpoint); subject/courseProgram suggestion endpoints
- `backend/src/main/java/com/studysnap/backend/service/NoteService.java` — note CRUD, `listMine(userId)`, `listPublic(...)`, visibility change, note copy, subject/courseProgram autocomplete
- `backend/src/main/java/com/studysnap/backend/entity/NoteEntity.java` — note schema: `title`, `content`, `subject`, `courseProgram`, `tags`, `visibility`, `studyPackStatus`, `targetProfileType`, `ownerUserId`
- `backend/src/main/java/com/studysnap/backend/repository/NoteRepository.java` — JPQL queries for private/public note lists, subject/courseProgram suggestion queries

**Frontend**
- `frontend/app/notes/new/page.tsx` — Create Note route (write / generate-from-topic / import start options)
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
- `learnerLevel` and `courseProgram` are required before saving or generating a Study Pack; the frontend validates and reveals the metadata section with a toast if either is missing.
- `learnerLevel` and `courseProgram` are pre-filled from the user's profile so the required validation rarely blocks users who completed onboarding.
- learner level, course/program, current subject, and tags may be passed into Study Pack generation to improve subject suggestion quality without changing the note form flow.
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
- note metadata fields (`title`, `subject`, `courseProgram`, `tags`, and teacher/admin `Who is this note for?`) stay available in the collapsed `Add details` section by default so first-time note creation stays focused on content.
- Target Audience is required on every note. For Student, Board Exam, and Professional profiles the field is hidden and auto-prefilled from profile type at save time (Student -> Student, Board Exam -> Board Taker, Professional -> Professional). For Teacher and Admin profiles the field is visible and user-picked, with all audience values selectable.
- create mode should keep a subtle inline prompt near the primary actions so users can reveal `Add details` without turning the page back into a long form.
- `Generate Study Pack` first saves the note, queues Study Pack generation, then redirects immediately to Note Detail with the requested default tab.
- the editor must not wait for the LLM request to finish before navigation.
- `Generate Note` creates a structured first draft from a topic with clear sections (`Overview`, `Core Concepts`, `Key Details`, optional `Examples`) and should avoid meta filler or instructional language.
- `Generate Note` must build its request from the current Create Note form state at submit time. The selected draft Course / Program is authoritative for the first generated note; the profile Course / Program is fallback only when the draft field is blank.
- topic note generation is plan-gated separately from Study Pack generation and OCR.

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
- because generation is asynchronous from the Note Editor, the suggestion modal appears from Note Detail after the queued Study Pack becomes ready.
