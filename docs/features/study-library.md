# study-library.md - NoteLib Feature Context

## Goal

Library surfaces make NoteLib a reusable workspace:

- **Library** for owned notes (Draft and Study Pack Ready)
- **Public Library** for discoverable public notes from you, other users, and official NoteLib content

Core learning loop:

New Note -> Save (Draft) -> Generate Study Pack (Study Pack Ready) -> Review -> Improve -> Make a Copy -> Repeat

## Library

Library contains all notes owned by the current user:

- includes `PRIVATE` and `PUBLIC` notes
- cards show subject, title, note preview, summary preview, tags, state, and updated date
- if a note has no explicit subject yet, Library should derive a temporary fallback subject from existing metadata so the note still participates in subject grouping
- note preview comes from original note content
- summary preview comes from generated Study Pack summary, or `No summary available yet.` when the note is still a draft
- supports:
  - real-time search over title and tags
  - single-select subject chips with `All` as the default
  - limited `Popular Tags` chips that combine with search and subject
  - full tag multi-select through a progressive-disclosure selector
  - OR logic within the tag group so selecting multiple tags shows notes that match any selected tag
  - sorting and pagination
- Library filter layout should keep:
  - search bar first
  - subject chips second
  - popular tags rail third
- subject and popular-tag chips should use horizontal scroll on one line instead of wrapping into tall chip grids
- Library should not expose the full subject/tag lists by default
- a `+ More` chip should open the full selector:
  - subjects -> searchable single-select list
  - tags -> searchable multi-select list
  - mobile -> bottom sheet
  - desktop -> modal/sheet
  - actions -> `Apply`, `Clear`
- tag selector should surface currently selected tags near the top so users can quickly deselect them
- selector ordering may prioritize recent use first, then frequency, then alphabetical order
- tag OR matching should reduce false `No study packs found` states during normal browsing
- opening a card navigates to the unified Note Detail page
- card interaction is consistent with Dashboard/Public Library:
  - click card to open note
  - use top-right card menu for tertiary actions (`Make a Copy`, `Delete`)
- empty filtered state should show:
  - `No study packs found`
  - `Try adjusting your filters`

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

Note Detail study history:

- Study Pack-ready notes should show `Performance Overview` followed by `Recent Sessions`
- Recent Sessions should combine completed Quick Review and Challenge Quiz attempts for that note
- selecting a session should open a dedicated session-review page on both desktop and mobile
- Note Detail should keep the session list as the entry point into review, but should no longer embed the full session review below the list
- mobile review should favor stacked cards and wrapped text over dense tables so question review stays readable on small screens
- older sessions without full stored quiz detail should still render a graceful fallback summary instead of breaking the note page

## Public Library

Public Library lists notes where:

- `visibility = PUBLIC`
- includes the current user's own public notes

Capabilities:

- search, subject filter, and tag filter
- search remains the primary entry point; subject chips come next; `Popular Tags` stays third
- subject chips and popular tags should stay on one horizontal scroll line instead of wrapping into tall chip grids
- both rails should expose a `+ More` control that opens the shared searchable selector surface
- curated discovery sections on the default Public Library page:
  - `Featured Notes`
  - `Most Popular`
  - `Recently Added`
- discovery-home limits keep the page scannable:
  - Featured Notes -> 3
  - Most Popular -> 5
  - Recently Added -> 5
- each discovery section includes `View More`, which opens a focused section view on the same page instead of flattening Public Library into a generic list
- open Public Note Detail (read-only)
- Public Library cards may expose a subtle inline `Save` CTA directly so discovery can flow into copy without opening detail first
- guests clicking `Save` should see an auth prompt modal instead of an immediate redirect
- duplicate public copies should resolve to a muted `Saved` card action instead of creating another draft
- tag filtering in Public Library should use OR logic within the tag group, the same as private Library
- copy-success feedback should use a short action hierarchy: `View Note` and primary `Start Review`
- cards use the same shared preview stack as Library, including both note preview and summary preview
- author labels use viewer-relative public identity rules (`By You`, `By NoteLib`, `By {Display Name}`)
- Featured Notes should remain visually distinct from the other sections

SEO public detail route:

- canonical path: `/public/library/{subject}/{slug}`
- accessible without login
- indexable for search engines only when the note is `PUBLIC`
- uses semantic content sections for title, summary, key concepts, and quiz preview
- older ID-based public note links may redirect to the canonical SEO route

SEO indexing rules:

- canonical public library index path: `/public/library`
- canonical public subject index paths: `/public/library/{subject}`
- `robots.txt` should allow public crawling while disallowing authenticated app routes such as `/dashboard`, `/library`, `/notes`, `/settings`, `/admin`, and `/api`
- `sitemap.xml` should include:
  - `/`
  - `/privacy`
  - `/terms`
  - `/public/library`
  - all canonical public subject routes under `/public/library/{subject}`
  - all canonical public note detail routes under `/public/library/{subject}/{slug}`
- authenticated/private routes must not appear in the sitemap

Structured data rules:

- `/public/library` should emit JSON-LD `CollectionPage` schema
- `/public/library/{subject}/{slug}` should emit JSON-LD `Article` schema
- use real note title, summary/fallback description, author display name, tags, subject, and `updatedAt` where available
- structured data must align with the page metadata and canonical URL

## Copy Rules

Both flows use the same copy rules:

- **Make a Copy** (own note)
- **Copy to My Library** (public note)

Copy includes only:

- `title`
- `subject`
- `courseProgram`
- `targetProfileType`
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
- if copied from a public note, preserve attribution via `copiedFromNoteId` and `copiedFromUserId`
- copied notes should show `Copied from {title} in Public Library.` on Note Detail when attribution is available

## Note audience assignment

Every note must store who it is written for through `targetProfileType`.

Creation rules:

- `Student` creators auto-save new notes with `targetProfileType = STUDENT`
- `Board Taker` creators auto-save new notes with `targetProfileType = BOARD_TAKER`
- `Teacher` and `Admin` creators must choose `Who is this note for?` before saving or generating
- current teacher/admin audience choices:
  - `Student`
  - `Board Taker`

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

- `Library`
- `Public Library`
- `New Note`
- `Make a Copy`
- `Copy to My Library`
- `Generate Study Pack`

## Dialog Consistency

- Use the shared app modal component for confirmation and share dialogs.
- Do not use browser-native confirm/alert dialogs for library or note detail actions.
