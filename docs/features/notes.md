# notes.md - NoteLib Feature Context

## Goal

Notes are the primary user-authored workspace in NoteLib. Users organize note metadata first, then turn notes into Study Packs for review.

## Key Files

**Backend**
- `backend/src/main/java/com/studysnap/backend/controller/NoteController.java` — `GET /notes` (private list), `POST /notes`, `PUT /notes/{id}`, `DELETE /notes/{id}`, `GET /notes/public` (public filter endpoint); subject/courseProgram suggestion endpoints
- `backend/src/main/java/com/studysnap/backend/service/NoteService.java` — note CRUD, `listMine(userId)`, `listPublic(...)`, visibility change, note copy, subject/courseProgram autocomplete
- `backend/src/main/java/com/studysnap/backend/service/NoteBulkImportService.java` — bulk material import orchestration; one extracted file becomes one `DRAFT` note without Study Pack generation
- `backend/src/main/java/com/studysnap/backend/entity/NoteEntity.java` — note schema: `title`, `content`, `subject`, `courseProgram`, `domainContext`, note-level `learnerLevel`, `tags`, `visibility`, `studyPackStatus`, `targetProfileType`, `ownerUserId`
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

- Note content is **locked** after Study Pack generation (`STUDY_PACK_READY`) — do not re-enable the content editor for ready notes; title, courseProgram, subject, and tags remain editable for everyone, plus audience, Domain Context, and `Authored Depth` for Teacher/Admin authors
- `Generate Study Pack` saves the note then navigates immediately to Note Detail — **never wait** for LLM completion before navigation; generation is always async
- `domainContext` is the authoritative authoring-domain signal for generation; fallback is note `courseProgram`, then profile `courseProgram`
- note-level `learnerLevel` is the authoritative educational-depth signal; fallback is profile `learnerLevel`, then `COLLEGE`
- AI-generated subject must be a reusable academic label with **no topic suffix** — strip anything after `–`, `:`, or `-` before saving (e.g. `Biology – Cell Division` → `Biology`)
- The share modal/gate is the **same everywhere**: public content → share modal directly; private content → confirm-to-make-public modal first; do not invent content-specific flows
- **Official author** is determined by `UserRole.ADMIN` only — `isOfficialAuthor(user)` returns `user != null && user.getRole() == UserRole.ADMIN`; the old email-based `isNoteLibOfficialAccount()` method has been removed; do not recreate it
- **Public author name** resolution order: admin user's `displayName` if set → `"NoteLib"` fallback for admin with no displayName → non-admin user's `displayName` → `firstName` → `"Anonymous learner"`
- Target Audience is **hidden and auto-prefilled** for Student, Board Exam, and Professional profiles; **visible and user-picked** for Teacher/Admin

## Note metadata

### Canonical authoring axes (v0.69.0)

Notes now persist two nullable, author-supplied metadata fields governed by [ADR-001](../architecture/ADR-001-canonical-knowledge-architecture.md):

- `domainContext` records how the note is authored — its authoritative academic or professional domain.
- note-level `learnerLevel` records how deep the note is authored, independently of the owner's profile-level learner level.

Both fields drive generation through `StudyPackGenerationContextResolver`. Teacher and Admin authors can set them in the Note Editor **and correct them afterwards from Note Detail's inline metadata panel**, which is the only editing surface a `STUDY_PACK_READY` note offers from the UI; both selects are optional and load empty for existing null rows. Correcting either axis on a ready note changes only *future* generation — the existing Study Pack is untouched until the user explicitly regenerates. Domain Context controls academic/professional subject matter, terminology, examples, and framing. Note learner level controls authored depth and difficulty. Omitting either field, sending `null`, or sending a blank value stores `NULL`. A null `domainContext` deliberately falls back to the note's program and then the profile program, while a null note learner level falls back to the profile level and then `COLLEGE`.

The legacy level-in-program cleanup is complete without rewriting any retained `courseProgram` label. V104 classified the 27 pure-level `Grade School` and `Junior High` notes as `GENERAL_EDUCATION` plus `GRADE_SCHOOL` or `JUNIOR_HIGH`. V105 applied the curator's content-based classification to four ambiguous `High School` notes: three now carry `GENERAL_EDUCATION` + `JUNIOR_HIGH`, and one carries `GENERAL_EDUCATION` + `SENIOR_HIGH`. Six `High School` notes remain deliberately unclassified with both axes `NULL` because their content has no defensible K-12 grade anchor; their legacy `courseProgram` remains their only classification and continues through the fallback chain. The 11 Senior High strand notes carry `SENIOR_HIGH` with no Domain Context, preserving STEM, ABM, or HUMSS as the effective authoring domain through the same fallback.

Static note and Study Pack content uses the effective domain plus the note's authored level, never the reader's level when a note level exists. Quizzes and exams keep the note level as their curriculum floor; a lower reader level may soften wording or add support but cannot lower the curriculum, while a higher reader level cannot raise the note's difficulty. The Course / Program(s) list is discovery metadata and never reaches prompts.

### Course / Program(s) (v0.71.0 Slice 4)

Course / Program(s) is one discovery axis, never a primary program plus a second overlay. Learners use exactly one free-text personal program in `notes.course_program`; Teacher/Admin curators choose one or many catalog programs in `note_course_program`. Discovery remains join-first with the personal-string fallback for notes with no join rows.

- The same Teacher/Admin gate controls catalog vocabulary and cardinality: curators get catalog-only multi-select; learners get one free-text combobox.
- **The field keeps its original position in the main metadata grid for both modes** — it is not a new field, it is the existing Course / Program gaining several values, so it must not move into the gated "Generation & discovery" fieldset for curators. Moving it there misfiles a discovery field as generation metadata and disorients returning authors. Only the control differs between modes; the slot, label, and required marker do not.
- **"Add details" stays collapsed by default on note creation, and the field order inside it is Title → Course / Program(s) → Subject** (owner ruling). Capture first, organize second: writing the note is the primary focus, and the panel's collapsed state is what most authors ever see. Subject sits *below* Course / Program(s) because the natural sequence is name it, place it, then classify it further.
- **The collapsed summary reflects state, not rules.** It previously read "Course / Program is required. Title, subject, and tags are optional." — which, now that Course / Program(s) pre-fills from the profile, usually told authors to do something already done. It now shows what is set and spends its remaining words on Subject (`Accountancy · Add a Subject to improve organization and your Study Pack.`), so the one field with real upside gets taught without anyone expanding the panel.
- **Subject's helper sells the benefit rather than marking it optional**: it reaches the prompt through `StudyPackGenerationContext`, so filling it in genuinely improves the Study Pack. Title's helper says the opposite — leave it blank and the AI writes one — because Title is an *output* of generation, not an input.
- **Subject is never AI-suggested during note creation.** Suggestions stay post-generation (the AI suggestion modal), which avoids an LLM call before the author has even asked for one. Do not add live subject inference to the editor.
- **A curator's Course / Program(s) pre-fills from their profile on a new note**, matching what the single-valued field always did. It needs both the profile value and the catalog (to map a name onto an id), so it lives in its own effect rather than either fetch. It is only ever a pre-fill: it never runs in edit mode, never once the author has touched the selection, and silently does nothing when the profile value has no catalog match — a learner-side free-text value like `Software Engineering` simply leaves the field empty rather than guessing.
- The curator multi-select suppresses `CourseProgramCombobox`'s default helper text (`helperText={null}`). That default reads "Choose or type…", which is wrong wherever `allowCustom` is false, and the curator field already carries its own explanation above the input.
- **The catalog is writable only through an explicit Admin action (`v0.71.2`).** When an Admin types a name with no catalog match, the shared Applicable Programs picker checks and surfaces near matches, then offers a separate confirmed “Add … to the catalog” action. `allowCustom` remains `false`; creation never happens on blur, Enter alone, or as a side effect of selection. Teachers can curate note applicability from the shared catalog but cannot mutate the shared catalog, and learner free-text authoring remains unchanged.
- Catalog creation accepts an existing Program Family or no family. Assigning the new program to a family is what makes it participate in that family’s unconditional authoring expansion; a null-family program remains individually selectable but is not inferred into any family. New-family creation is not part of note authoring.
- Duplicate names are compared case- and whitespace-insensitively. Exact duplicates return a named conflict and offer the existing program for selection; near matches appear before creation. A failed inline create preserves both the typed candidate and every existing selection so the Admin can correct or retry without reconstructing the note metadata.
- Domain Context stays visible from the start. It is optional with one program, but required above one program because the AI needs one authoritative academic domain; a program list is never sent to a prompt. Both note save and program-set save reject either direction of an invalid change with a teaching message.
- **Every authoring surface reads the program axis that its author actually edits.** For a curator that is the catalog selection (`applicableProgramIds`); the learner free-text field is empty for them by design, because the backend nulls it. A surface that reads the learner field for a curator falls back to the *profile* program and reports a value the note does not have — the Note Editor sticky bar did exactly this, claiming "Tailored for: {profile program}" with zero programs selected. The sticky bar and the "Add details" nag derive from **one** shared value so they cannot contradict each other; two independent reads of the same fact is what let one say "done" while the other stayed silent.
- **Multi-program Domain Context is pre-validated on every surface that can reach it**, not just enforced by the API: Note Editor Save, Create a Note, Note Detail, Bulk Generate, and the admin Applicable Programs screen. A surface that reports this rule must also *reveal* the Domain Context control — naming a field that sits inside a collapsed accordion is a dead end. The admin screen has no such control at all, so it explains where the field lives instead. **A failed save never resets the author's selection**; restoring the persisted set discards every new pick and forces a curator to re-choose them before they can retry.
- **The invariant is validated against the program set in effect *after* the save, and that set has a different source per author.** For a curator the request **is** the new set — `PUT /notes/{id}` reconciles join rows from it — so the request is what gets validated. For a learner the request carries no programs at all while the stored join rows survive the save untouched, so the **stored rows** are what gets validated. Reading the request for both authors leaves the rule unenforceable on learners, who are the only authors who can reach the violating state (by copying a curated multi-program note and clearing Domain Context, which their UI hides). Reading stored rows for both is equally wrong in the other direction: it blocks a curator's legal reduction from several programs to one and admits an illegal one-to-many expansion. `POST /notes` needs no stored-row lookup — the note does not exist yet, and a learner create writes no join rows.
- **The depth control is labelled `Authored Depth`** (`v0.75.0` item 4, `ADR-001` constraint 4). The axis remains `notes.learner_level` and is still called Note Learner Level in `ADR-001` and the API contract — **the rename is copy-only.** `Intended Audience` was unavailable, since `notes.target_profile_type` already owns that concept in the same form.
- **On note CREATE, Authored Depth pre-fills from the author's own profile level** (`v0.75.0` item 1, `ADR-001`'s weak fallback leg). So "Automatic — based on the reader" is **no longer the initial state on create** — it remains a real, selectable null state, and blank still saves as null. **The pre-fill is create-only and never applies when editing an existing note:** a depth change on an already-generated note strands its Challenge-bank rows at the old level, which is the same hazard that made `v0.75.0` reject align-on-add. Domain Context has no authorized inference source and is never pre-filled.
- **Authoring copy names the outcome, never the mechanism.** The Domain Context and Authored Depth empty options read "Automatic — based on the program" and "Automatic — based on the reader", not "Use … fallback". "Fallback" is implementation vocabulary and no teacher thinks in fallbacks; the underlying resolution chain is unchanged. The enclosing fieldset is "Generation & discovery" rather than "Authoring metadata", because it names what the group does rather than the data's category.
- The shared control derives Program Families from `course_programs.program_family_id`. A family action unconditionally adds every member to the current selection without removing hand-picked programs, then shows the explicit removable chips immediately so the author can trim before saving. Subject, Domain Context, and learner level never condition expansion.
- Family expansion is an authoring pre-fill only. The family itself is not persisted or inferred back from a complete member set, and discovery never expands a family at read time; the saved `note_course_program` rows remain the sole applicability truth.
- Curator note saves carry the full catalog id set and reconcile join rows transactionally. `PUT /notes/{id}/applicable-programs` remains the dedicated full-set save path; unknown ids are rejected before any row changes, retrying the same set is idempotent, and it also enforces the multi-program Domain Context invariant.
- **Applicable Program authoring authority is owner-scoped (`v0.71.1`, ratified 2026-08-11).** A row may only be authored by a curator (`ADMIN` or `TEACHER`) onto a note they own. Ownership alone never grants a learner authoring authority, and `ADMIN` never grants authority over another user's note. Read access remains deliberately broader — an admin or the note owner may read its programs — so read-only learner provenance continues to work.
- The program **list** is discovery metadata and never enters a generation request or prompt. The resolver may use the name of exactly one joined catalog program after Domain Context, before falling back to the personal note string and then the profile string; it never resolves a list.
- **A learner-authored note carries no mechanically derived Applicable Program rows, and being served by the personal-string fallback is its canonical shape — not a degraded one.** A learner's free-text Course / Program lives only in `notes.course_program` and is never materialized into a `note_course_program` row; `V108` removed the rows `V107`'s unfiltered backfill derived from learner-owned non-copy notes, so on **that** shape a learner's edit to their program stays effective on both discovery and generation instead of losing to a join row they cannot reach. **Rows inherited by copying a curated note are preserved** — they were authored by a curator, and copy inheritance is the only legitimate route to a join row on a learner-owned note. Read semantics never consult ownership: joined programs first when they exist, otherwise the personal string.
  - **The effectiveness claim above is scoped to learner-authored non-copy notes, and does not extend to copies (corrected 2026-08-10).** On a copy of a curated note the learner's personal string is **shadowed** — unreadable on every path — because discovery is join-first and generation reads the string only through `effectiveAuthoringDomain`, which loses to the inherited Domain Context at 2+ programs and to the joined catalog name at exactly one. The condition is `shadowed = (joinRowCount >= 1) && (joinRowCount == 1 || domainContext != null)` — the string is shadowed only when **both** readers ignore it. **Corrected 2026-08-11:** the original `(joinRowCount == 1) || (domainContext != null)` dropped the discovery term and wrongly reported shadowed at **zero** join rows, where the string is the only value discovery has. **Do not simplify it to "has join rows"** either; that depends on an invariant no write path enforces. `v0.71.1` does not require the personal field on a shadowed note.
  - **Shipped copy contract for the read-only block (settled 2026-08-10, `v0.71.1`).** On a shadowed note the learner sees the program **names**, middot-separated, under the existing `COURSE / PROGRAM(S)` label — never a count, and never a control:

    ```
    COURSE / PROGRAM(S)
    Civil Engineering · Mechanical Engineering · Electrical Engineering

    Set by the note this was copied from. Your own course or program is on your profile.
    ```

    The second sentence is **conditioned on `copiedFromNoteId`**: when it is absent (an admin-curated non-copy note) the names render alone rather than claiming a copy that did not happen. Middot separation is deliberate — it reads as one metadata value, where chips would read as interactive, which is the exact confusion this block exists to remove.
  - **Cards keep the neutral summary and carry no provenance:** one program renders its name, several render `Applies to N programs`. Note Detail already carries the note-level *"Copied from … in Public Library."* line, so provenance belongs there; a card has no copy indicator and would have to assert *how* the rows arrived, which is not safe to claim while admin curation of a learner's note is unresolved. **`Applies to` is the single phrasing** — Note Detail's `Applicable to` count is replaced by the names block, so the divergence disappears rather than being reconciled.
  - **The names block makes the card's own promise true.** `shared-note-card.tsx` justifies its count with *"The full read-only list lives on Note Detail"*; before `v0.71.1`, Note Detail also showed a count and the names rendered only inside the curator-gated combobox, so for a learner the full list existed nowhere.
  - **A learner may be shown the Applicable Programs their note carries, read-only, with their provenance; they may never author, add, remove or edit them (ratified 2026-08-10, `ADR-001`).** The earlier flat "no learner-facing Applicable Programs UI" was about authoring **controls** — `v0.71.1` provides the permitted read-only provenance display so a learner can explain why their own note is filed where it is. Still forbidden and unchanged: any learner-facing control that edits programs, any derivation of a row from a learner's personal string, the `source` provenance column, and re-derivation or clearing of rows on a learner save.
- The existing `GET /course-programs` scopes now include program names found only through the join while retaining their `List<String>` contract. Because the authoring Course / Program comboboxes share those suggestions, an author may see a catalog program reached only through applicability; that intentional widening does not couple the legacy string to the curated set.

`DomainContext` is a closed architectural enum with exactly eight ratified values:

- `ENGINEERING_MATHEMATICS` — Engineering Mathematics
- `ENGINEERING_SCIENCES` — Engineering Sciences
- `CIVIL_ENGINEERING` — Civil Engineering
- `PROFESSIONAL_PRACTICE_AND_REGULATION` — Professional Practice & Regulation
- `GENERAL_EDUCATION` — General Education
- `PROFESSIONAL_EDUCATION` — Professional Education
- `NURSING` — Nursing
- `ACCOUNTANCY` — Accountancy

Adding or changing a Domain Context is an architecture decision, not routine note authoring. Copies inherit both authoring axes because they are note metadata, not generated content.

**Subject-equals-Domain-Context nudge.** Subject and Domain Context are separate axes, so the same value legitimately appearing in both is not an error — a broad survey note about nursing really is `subject = Nursing`. But it usually signals a subject written too broadly to work as a library shelf, which is why ADR-001 asks for a nudge here. When a note's `subject` exactly equals its selected Domain Context's label (compared case-insensitively and trimmed), the Note Editor shows an inline advisory under the Subject field. Rules:

- It is **advisory only** — never a validation error, never a save block, and it does not prevent generation. Saving anyway is a supported outcome.
- **Exact match only.** `Nursing Pharmacology` under Domain Context `Nursing` is not flagged; only `Nursing` is. A containment check would fire on legitimate specific subjects.
- It renders only where the Domain Context select renders (Teacher/Admin), and never when Domain Context is blank or the subject is empty.
- It is computed client-side from the already-loaded values — no new API call, no persisted state, and nothing is recorded when an author dismisses it by editing either field.

Current Note Editor input fields:

- `title` (optional)
- `courseProgram` (required before save/generate **unless the note is shadowed** — see the shadowing rule below; pre-filled from the user's profile on create only)
- `subject` (optional)
- `tags` (optional)
- `content` (required before save/generate)
- Teacher/Admin-only `domainContext` (optional; blank uses the program-name fallback)
- Teacher/Admin-only note `learnerLevel` (optional; blank uses the profile level, then `COLLEGE`)

Rules:

- `courseProgram` belongs to the note once saved and can differ from the profile default.
- `courseProgram` remains the legacy program label and fallback authoring context; Domain Context, Note Learner Level, subject, and tags remain independent axes.
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
- `courseProgram` is required before saving or generating a Study Pack when it remains readable; a shadowed note does not require the personal field.
- **The profile's course/program pre-fills the field when CREATING a note only.** Editing an existing non-shadowed note never falls back to the editor's profile: the note's own persisted value is the only source, and a note with no course/program hits the normal "Please complete: Course / Program" prompt asking the author to classify it. **A shadowed note's stored string is left exactly as it is — neither cleared nor replaced by the editor's profile program.** It is unreadable while the join rows exist, but a pre-slice-4 curated note kept its string while `V107` gave it one join row, and `copyNote` carries both onto the copy; destroying that on an unrelated edit would be unrecoverable if the rows were later removed. Falling through to the profile program would be worse still, since the surface renders no field — that is the silent profile-stamping this same section forbids. This is deliberate and is a behavior change from before v0.69.0, where `profileCourseProgram` was consulted in edit mode too — which meant an author editing a null-program note saw an empty field while their own profile value was silently submitted. Profile context may assist creation; it must not become an existing note's persisted metadata without an explicit author decision (ADR-001).
- Topic generation still resolves the profile course/program as a *generation input* when the draft has none. That value is never persisted onto the note, so it is not affected by the rule above.
- Profile `learnerLevel` remains required on completed profiles for quiz/exam personalization and legacy fallback, but it does not override a note's authored learner level.
- Note-from-topic and legacy notes without Domain Context use the resolved program-name fallback as their effective authoring domain until the note-level authoring fields are populated.
- Static generation and quiz/exam generation must tolerate both note-level axes being null and use the documented fallback chains rather than placeholders.
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
    - `Create from topic`
    - `Import notes`
  - optional topic generation panel
  - optional import panel
  - note `Content`
  - `Add details` disclosure
- actions: `Save`, `Generate Study Pack`
- optional topic-first helper: `Create a Note`
- the `Import notes` start option reuses the existing OCR/file-extraction flow and inserts extracted text into the main editor before save or Study Pack generation.
- `/notes/import` is the separate bulk note-creation entry point, reached from the Create-note flow's `Import notes` panel (a "Bulk import multiple files" link). It sends multiple files through `POST /notes/import-batch`, creates one `DRAFT` note per successfully extracted file directly, and never triggers Study Pack generation or LLM calls.
- note metadata fields (`title`, `subject`, `courseProgram`, `tags`, and the Teacher/Admin authoring metadata group) stay available in the collapsed `Add details` section by default so first-time note creation stays focused on content.
- Target Audience is required on every note. For Student, Board Exam, and Professional profiles the field is hidden and auto-prefilled from profile type at save time (Student -> Student, Board Exam -> Board Taker, Professional -> Professional). For Teacher and Admin profiles the field is visible and user-picked, with all audience values selectable.
- Domain Context and Note Learner Level are visible only to Teacher/Admin authors in the product UI, on both authoring surfaces (Note Editor and Note Detail's inline metadata panel). Both are optional single-selects; blank remains a real null state rather than a fabricated default. Because `PUT /notes/{id}` is a full replace, a surface that hides these selects must send the note's stored values back untouched rather than the empty draft — hiding a field must never null it.
- create mode should keep a subtle inline prompt near the primary actions so users can reveal `Add details` without turning the page back into a long form.
- `Generate Study Pack` first saves the note, queues Study Pack generation, then redirects immediately to Note Detail with the requested default tab.
- the editor must not wait for the LLM request to finish before navigation.
- `Create a Note` creates a structured first draft from a topic with clear sections (`Overview`, `Core Concepts`, `Key Details`, optional `Examples`) and should avoid meta filler or instructional language.
- `Create a Note` must build its request from the current Create Note form state at submit time. The selected draft Course / Program is authoritative for the first generated note; the profile Course / Program is fallback only when the draft field is blank.
- `Create a Note` sends a selected Teacher/Admin Domain Context into topic-content generation; when blank, the resolved Course / Program remains the Domain fallback. Note Learner Level is persisted when the draft is saved and then controls Study Pack generation; the topic-generation DTO deliberately has no second level source.
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

- surface: Note Detail's inline metadata panel. `Edit` in the note actions menu opens it in place; it does **not** route to `/notes/{id}/edit` once a Study Pack exists.
- editable metadata: `title`, `courseProgram`, `subject`, `tags`, plus `targetProfileType`, `domainContext`, and `learnerLevel` for Teacher/Admin authors
- locked field: `content`
- helper text: `Note content cannot be edited after generating a Study Pack. You can still update the title, course/program, subject, and tags.` — Teacher/Admin authors get the longer variant naming audience, Domain Context, and Note Learner Level as well
- actions: `Save`, `Cancel`; `Make a Copy` stays in the note actions menu

`/notes/{id}/edit` itself has **no** ready-note guard on either side — `NoteService.update` accepts any status and the editor renders an unlocked content textarea — so the route stays reachable by direct URL and edits content. This is deliberate: it is the escape hatch that made the ADR-001 R4 verification runnable before the inline axes shipped. Do not add a guard without an explicit decision; the content lock is an entry-point convention, not an enforced invariant.

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

### Applicable Programs — the empty state explains itself (C8)

When an author has selected no Applicable Programs **and** their own profile programme is absent from the
shared catalog, the picker names the programme and says it cannot be used here, rather than showing a bare
*"No course programs selected."* A curator in that position has a programme and no way to see why it counts for
nothing on this surface. When the profile programme **is** in the catalog, the plain empty state stands — there
is nothing to explain, the author simply has not picked yet.
