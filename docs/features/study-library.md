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
- note preview comes from original note content
- summary preview comes from generated Study Pack summary, or `No summary available yet.` when the note is still a draft
- supports search, subject filter, tag filter, sorting, and pagination
- opening a card navigates to the unified Note Detail page
- card interaction is consistent with Dashboard/Public Library:
  - click card to open note
  - use top-right card menu for tertiary actions (`Make a Copy`, `Delete`)

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

## Public Library

Public Library lists notes where:

- `visibility = PUBLIC`
- includes the current user's own public notes

Capabilities:

- search, subject filter, and tag filter
- curated discovery sections on the default Public Library page:
  - `Featured Notes`
  - `Most Popular`
  - `Recently Added`
- `Browse by Subject` appears above the curated note sections
- discovery-home limits keep the page scannable:
  - Featured Notes -> 3
  - Most Popular -> 5
  - Recently Added -> 5
- each discovery section includes `View More`, which opens a focused section view on the same page instead of flattening Public Library into a generic list
- open Public Note Detail (read-only)
- `Copy to My Library`
- Public Library cards may expose the copy CTA directly so discovery can flow into copy without opening detail first
- duplicate public copies should resolve to `Already in your library` plus `Open in My Library` instead of creating another draft
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
- `Copy to Library`
- `Generate Study Pack`

## Dialog Consistency

- Use the shared app modal component for confirmation and share dialogs.
- Do not use browser-native confirm/alert dialogs for library or note detail actions.
