# NoteLib Product Specification

Rebrand note: StudySnap has been rebranded to NoteLib. Database schema/table names remain unchanged unless explicitly requested.

Current documentation baseline: `v0.6.0 - Landing Revamp & Positioning (In Progress)`

Current in-progress release: `v0.6.0 - Landing Revamp & Positioning`

## Product Overview

NoteLib is a notes library and study workspace that helps students, board exam reviewees, and teachers organize notes, then turn those notes into summaries, key concepts, and quizzes when review starts.

The goal is to support active recall and repeated practice through a calm, iterative learning workflow built around summaries, key concepts, quiz practice, weak concepts, and adaptive review.

## Core Concept

Note-first model:

- Note is the main entity.
- Study Pack is the AI-generated enhancement of a Note.
- Users first save Notes, then generate Study Packs from those Notes.

Note states:

- `Draft` (no AI-generated content yet)
- `Generating` (Study Pack generation is running in the background)
- `Failed` (last Study Pack generation attempt did not complete)
- `Study Pack Ready` (AI-generated content exists)
- visibility: `PRIVATE` or `PUBLIC`

Generated Study Pack outputs include:

- AI-generated title (optional)
- subject (optional)
- tags (optional)
- summary
- key concepts
- practice quiz
- Challenge Quiz
- Adaptive Practice

## Versioning Model (Copy)

NoteLib does not overwrite existing generated content.

Users make a copy of a Note, edit that copy, and generate a new Study Pack from the copied Note.

Copy behavior:

- Copy includes user-authored fields:
  - title
  - courseProgram
  - subject
  - tags
  - note content
- Copy does not include AI/generated history fields:
  - summary
  - key concepts
  - quizzes
  - performance history
  - quiz sessions

This supports iterative learning and avoids accidental overwrites.

## User Flow

1. User creates or saves a Note.
2. Note is stored in the system.
3. User clicks `Generate Study Pack`.
4. NoteLib redirects the user to Note Detail while generation runs asynchronously.
5. Note Detail shows `Generating`, then either `Study Pack Ready` or `Failed`.
6. User reviews with Quick Review, Challenge Quiz, and Adaptive Practice when the Study Pack is ready.
7. If the user wants to improve the note, they make a copy, edit it, and generate a new Study Pack from the copy.
8. If the note should be shared broadly, user sets visibility to `PUBLIC` and it appears in Public Library.
9. Public notes can be copied into Library as new Draft notes.

## Architecture Overview

High-level model:

- `notes` table stores user-authored fields (`title`, `courseProgram`, `subject`, `content`, `tags`).
- `notes.visibility` controls whether notes are private or listed in Public Library.
- Generated fields are stored and linked to the same Note (`summary`, `key concepts`, `quizzes`).
- Quiz sessions and performance are linked by `noteId`.
- Copy creates a new Draft Note row with copied user-authored fields only.

## Profile Types

Supported profile types:

- `STUDENT`
- `BOARD_EXAM`
- `TEACHER`

Profile type is a personalization setting on `User`.

Profile type affects:

- Dashboard layout and section priority
- CTA behavior
- Labels and wording
- Workflow emphasis
- Recommendations
- Default tab after generation

Profile type does not affect:

- Note ownership
- Study Pack generation pipeline
- Quiz-session persistence
- Activity history
- Weak-concept storage
- Core table structure

## Shared Learning Engine

All users share the same learning engine:

`Note -> Study Pack -> Quiz -> Activity -> Weak Concepts`

Do not create separate note, study-pack, quiz, or activity systems per profile type.

## Product Philosophy

Learning loop:

Capture -> Generate -> Review -> Improve -> Copy -> Repeat

NoteLib is designed to help users iteratively improve understanding, not just generate summaries once.

---

## Key Features

### Public Landing Page

Route: `/`

Purpose:

- explain NoteLib as a notes library first and a Study Pack generator second
- make Public Library visible as a public discovery route
- connect the product to active recall through the Learn page
- keep marketing/SEO messaging aligned with the actual product workflow

Required sections:

- hero
- What Is NoteLib
- how-it-works
- Public Library
- active recall / study method
- pricing teaser
- final CTA

Public navbar:

- `Home`
- `Public Library`
- `Learn`
- `Pricing`
- `Login`
- `Get Started`
- theme toggle on shared public surfaces
- desktop grouping should keep:
  - navigation links together
  - theme toggle in a utility cluster
  - `Login` as the secondary action
  - `Get Started` as the primary CTA
- mobile grouping should keep:
  - theme toggle in the top-header utility cluster beside the menu trigger
  - the opened menu panel focused on nav links, then a divider, then `Login` and `Get Started`
  - no duplicated theme toggle or duplicated visible primary CTA between the header and opened menu

CTA behavior:

- primary CTA: account creation
- secondary CTA: Public Library exploration (`/public/library`)
- demo access may remain available as a supporting public link, but it is not the primary secondary CTA

Hero positioning:

- headline: `Build your own library of notes. Turn them into summaries and quizzes when you're ready to review.`
- supporting text should explain NoteLib as a notes library, study workspace, and review tool rather than a one-shot AI utility
- landing messaging should position NoteLib as:
  - a notes library
  - a long-term study workspace
  - a place where notes become summaries, key concepts, and quizzes
  - a product built around active recall
- landing messaging should avoid positioning NoteLib as only:
  - a quiz generator
  - a summarizer
  - a generic AI tool

Public landing content:

- `What Is NoteLib` should reinforce `Your Notes. Your Library. Your Review Tool.`
- `How It Works` should show:
  - `Create a Note`
  - `Build Your Library`
  - `Generate Study Pack`
  - `Review & Practice`
- Public Library should be promoted as a first-class discovery route with CTA `Browse Public Library`
- Landing page should include a dedicated Public Library feature section that pairs discovery copy with a framed screenshot preview instead of a large standalone image block.
- The visual preview should use `public/landing/feature-public-library.jpg` to show note browsing, subject discovery, and copy-to-library intent with less reliance on explanatory text.
- On desktop, the Public Library section should use a responsive two-column layout with text on the left and the framed screenshot preview on the right; on mobile, it should stack text first and screenshot second.
- The screenshot preview should stay constrained with a max-height, rounded container, subtle border, and soft depth so it feels like a polished product preview rather than a full-width banner.
- Study Method should connect NoteLib to active recall and link to `/learn`
- Final CTA should include:
  - `Get Started`
  - `View Public Library`
- Public Library must remain accessible without login and must not be marketed as a Premium-only feature

SEO and social metadata:

- title: `NoteLib — Build your notes library and turn notes into quizzes`
- description: `NoteLib is a notes library where you can organize notes and turn them into summaries, key concepts, and practice quizzes to review more effectively.`
- canonical URL: `https://www.notelib.app`
- Open Graph / Twitter metadata should reuse the same positioning and point to `/og-image.png`
- `og-image.png` should reflect the notes-library headline and supporting study-workspace message used on the landing page

### Branding and Logo Usage

Brand assets:

- `notelib-logo-monogram.png`
- `notelib-logo-full-light.svg`
- `notelib-logo-full-dark.svg`
- `notelib-logo-icon.svg`
- `og-image.png`

Usage rules:

- use the NL monogram for:
  - public navbar logo
  - authenticated app-shell logo
  - mobile header logo
  - favicon
  - apple-touch icon
- use the full logo for:
  - landing hero
  - public footer
  - Learn header
  - Pricing header
  - other marketing/public page headers
  - Open Graph image branding
- use the product icon for:
  - feature illustrations
  - marketing visual accents
  - Open Graph illustration
- do not use the product icon as the navbar logo or favicon

Favicon requirements:

- `/favicon.ico`
- `/favicon-16x16.png`
- `/favicon-32x32.png`
- `/apple-touch-icon.png`
- `/favicon-192x192.png`
- `/favicon-512x512.png`
- `/site.webmanifest`

### Study Pack Generation

- Input modes: pasted notes text or uploaded image notes (OCR)
- Output: title, summary, key concepts, quiz questions, metadata (`subject`, `tags`)
- OCR upload is part of Note authoring (Create/Edit Note) and populates Note `content` for manual review.
- OCR upload does not auto-save and does not auto-generate.
- Note Editor should keep `Generate` as the primary action:
  - desktop: sticky top actions plus repeated bottom actions
  - mobile: fixed floating primary generate CTA
  - `Save` stays secondary
- Note Editor route modes must stay distinct:
  - `/notes/new` -> create mode with `Save` plus `Generate`
  - `/notes/{id}/edit` for Draft notes -> edit mode with `Save Changes`, `Cancel`, and `Generate`
  - `/notes/{id}/edit` for Study Pack Ready notes -> metadata edit mode with `Save Changes`, `Cancel`, and `Make a Copy`
- Existing notes on `/notes/{id}/edit` must render `Edit Note` copy, not the create-note title/description.
- Note Editor metadata fields are `title`, `courseProgram`, `subject`, `tags`, and `content`.
- Metadata hierarchy should stay:
  - `courseProgram` -> top-level academic track or domain
  - `subject` -> reusable academic topic for grouping/filtering
  - `tags` -> fine-grained keywords
- New notes default `courseProgram` from the user's profile, but it remains editable per note.
- Course / Program suggestions should come from curated defaults plus normalized saved values returned by `GET /api/course-programs?scope=mine`.
- Course / Program autocomplete must filter suggestions in real time while the user types.
- Matching should be case-insensitive, trim leading/trailing spaces before comparison, allow partial matches, and rank results as:
  - exact match
  - prefix matches
  - contains matches
- Typing into Course / Program should not keep showing the full unfiltered list.
- When the typed value exactly matches an existing suggestion case-insensitively, the field should reuse the existing saved label instead of preserving a duplicate case variant.
- Users may still type a custom course/program value, and the saved value should become reusable in later autocomplete/filter flows.
- Existing matching suggestions should appear before the custom `Use "..."` action so reuse is encouraged without blocking custom values.
- Course / Program saves should normalize whitespace and dash formatting so equivalent values such as `Senior High-STEM` and `Senior High – STEM` collapse into one reusable suggestion/filter label when possible.
- Course / Program reuse checks should be case-insensitive while keeping a readable display label.
- Note metadata helper text for Course / Program should adapt to the user's saved `learnerLevel` so note authors see examples that match their study stage.
- When a note already has a Study Pack, Note Editor keeps `title`, `courseProgram`, `subject`, and `tags` editable but locks `content` with the helper:
  - `Note content cannot be edited after generating a Study Pack. You can still update the title, course/program, subject, and tags.`
- Generate button copy should stay short and may vary by `profileType` without changing backend generation:
  - `STUDENT` -> `Generate`
  - `BOARD_EXAM` -> `Practice`
  - `TEACHER` -> `Create Quiz`
- The longer explanation belongs in helper text below the primary generate button:
  - `STUDENT` -> `Creates summary, key concepts, and quiz.`
  - `BOARD_EXAM` -> `Generates a new quiz from your material.`
  - `TEACHER` -> `Generates quiz questions from your material.`
- Default behavior after generation stays on the same unified note route:
  - `STUDENT` -> open `tab=summary`
  - `BOARD_EXAM` -> open `tab=quiz`
  - `TEACHER` -> open `tab=quiz`
- Generate saves the note first, marks it `Generating`, starts background generation, and redirects immediately to Note Detail.
- Note Detail polls lightly while generation is active and stops when the status is `Study Pack Ready` or `Failed`.
- Failed generation shows a friendly recovery state with `Retry Generate`; the note content remains saved.
- Entry modes reuse the same note pipeline:
  - `/notes/new` -> normal note creation
  - `/notes/new?mode=quiz` -> quiz-first flow
  - `/notes/new?source=paste` -> paste-material flow
  - `/notes/new?source=upload` -> upload-material flow
- Demo mode must not call real generation pipeline, persist data, or consume usage
- Unverified users are blocked from generation with structured `403`:
  - `code=EMAIL_VERIFICATION_REQUIRED`
  - `action=RESEND_VERIFICATION`
- Unverified users are also blocked from OCR upload in Create/Edit Note.

### Library

Library is the user's private workspace for managing and revisiting their own notes (Draft and Study Pack Ready).

Users can:

- view their saved notes
- search by title and tags in real time
- filter by subject (single select, `All subjects` default)
- filter by tags (multi-select OR matching)
- combine search + subject + tag filters on the loaded note list
- sort by `Recently Updated`, `Recently Reviewed`, `Newest`, `Title (A-Z)`, `Title (Z-A)`, and `Oldest`
- keep the primary filter UI inline above the note grid with:
  - search first
  - subject chips second
  - a compact `Popular Tags` rail third
- subject chips should stay on one horizontal scroll line instead of wrapping into multiple rows
- add a `+ More` chip at the end of the subject rail so the full subject list stays accessible without increasing page height
- do not expose the full tag list by default; show only a limited `Popular Tags` set and a `+ More` control
- `+ More` should open the shared selector surface:
  - subjects -> single-select list with search
  - tags -> multi-select list with search
  - mobile -> bottom sheet
  - desktop -> modal/sheet
  - actions -> `Apply`, `Clear`
- selector search inputs should filter their lists in real time
- multi-select tags should use OR logic by default so selecting multiple tags broadens browsing instead of creating false empty states
- tag selector should show currently selected tags in a quick-deselect section near the top
- selector option ordering may prioritize:
  - recently used
  - frequency
  - alphabetical
- derive a temporary fallback subject from existing note metadata when a note has no explicit subject so subject grouping still works
- open by clicking card/title
- start Quick Review for Study Pack Ready notes
- manage note visibility (`Make Public` / `Make Private`)
- make a copy (`Make a Copy`) to create a new Draft version
- create a new note directly from the Library header
- empty-state actions should include:
  - `Create Your First Note`
  - `Try Demo`
- no-result state should show:
  - `No study packs found`
  - `Try adjusting your filters`

### Public Library

Public Library lists notes where `visibility=PUBLIC`, including the current user's own public notes.

Users can:

- browse public notes
- use the curated Public Library discovery page with:
  - `Featured Notes`
  - `Most Popular`
  - `Recently Added`
- keep search first, then lightweight browsing rails for:
  - `Subjects`
  - `Popular Tags`
- keep featured content visually distinct rather than flattening discovery into one generic list
- control density with section-level limits:
  - Featured Notes -> 3
  - Most Popular -> 5
  - Recently Added -> 5
- keep Public Library ranking explainable and stable:
  - Featured = quality + engagement
  - Popular = social proof
  - Recent = freshness
- rank Featured from eligible study-ready public notes only:
  - must be `STUDY_PACK_READY`
  - must have a meaningful summary preview
  - must have quiz/generated study content
  - must have non-empty note preview/content
- Featured score formula:
  - `viewCount + (copyCount * 3)`
- Featured tie-breakers:
  - `copyCount DESC`
  - `viewCount DESC`
  - `createdAt DESC`
- qualify `Most Popular` using real social-proof thresholds instead of a plain sort:
  - `copyCount >= 3` or `viewCount >= 20`
- order `Most Popular` by:
  - `copyCount DESC`
  - `viewCount DESC`
  - `createdAt DESC`
- order `Recently Added` by:
  - `createdAt DESC`
- preserve the existing clean section dedupe:
  - Popular excludes Featured results
  - Recent excludes Featured and Popular results
- use `View More` for each discovery section to open the full section view without losing the Public Library page model
- filter by search, course/program, learner level, subject, tags, and source
- open read-only public note detail
- copy a public note into Library from a subtle inline `Save` CTA on the card
- keep subject chips and popular tags on one horizontal scroll lane instead of wrapping into tall grids
- expose the full subject/tag lists through shared searchable selector surfaces:
  - subjects -> single-select searchable list
  - tags -> multi-select searchable list with selected-tag quick deselect near the top
  - mobile -> bottom sheet
  - desktop -> modal/sheet
  - actions -> `Apply`, `Clear`
- keep tag matching OR-based within the tag group so selecting multiple tags broadens note discovery
- after a successful copy, show a confirmation state with:
  - `View Note`
  - `Start Review`
- `Start Review` is the primary CTA and `View Note` is the secondary CTA
- modal copy should stay concise:
  - `You can start reviewing now or come back later from your library.`
- modal/sheet should use a stronger success hierarchy with a subtle check indicator and larger title treatment
- desktop should use a modal with a visible top-right close button; mobile should use a dismissible bottom sheet
- desktop should right-align actions in the order `View Note`, `Start Review`
- mobile should stack full-width actions with `Start Review` visually first
- modal/sheet actions should keep a clean responsive layout with only `View Note` and `Start Review`, polished spacing, and no overflow
- if the user already copied that public note, show a muted `Saved` action state on the card instead of offering duplicate copies
- guests clicking `Save` should open an auth prompt modal instead of immediately navigating away without context
- Public Library cards should avoid generic `Open` buttons because card click already owns detail navigation
- Public Library copied-state UI should avoid redundant ownership/status badges when the action state already communicates the same information
- Public Library card footers should keep author metadata on the left and the subtle save action on the right for compact mobile scanability
- see source badges on cards:
  - `By You` for their own public notes
  - `By NoteLib` plus an `Official` badge for the official NoteLib account
  - `By {displayName}` for other users' public notes
- Public Library author labels are viewer-relative:
  - if `note.ownerId == currentUser.id` -> `By You`
  - else if the note author is the official NoteLib account -> `By NoteLib` with `Official`
  - else -> `By {displayName}`
- Public Library author labels should link to `/public/profile/{userId}`.
- Public note detail should change the primary action by ownership:
  - owner -> `Open Note`
  - non-owner -> `Copy to My Library`
- Public note detail header should show `Subject • Author` using the same viewer-relative author label.
- Public note detail author label should link to `/public/profile/{userId}`.
- Public subject pages should reuse the existing `/public/library/{subject}` route and show:
  - subject heading
  - descriptive subtitle
  - list of public notes for that subject
  - empty state when a known subject has no notes
- Subject pages should reuse the same Subject badge styling as Public Library cards and note detail headers.
- Library, Public Library, Public Profile, and public subject listing cards should share the same note-card content stack:
  - subtle course/program line when available
  - Title
  - private-library visibility icon when relevant
  - Subject badge
  - Study Pack status badge when relevant
  - `Note Preview` from note content
  - `Summary Preview` from generated Study Pack summary
  - Tags
  - subtle discovery metrics row (`views`, `copies`) when that surface exposes them
- Note-card previews should clamp to 2-3 lines so cards stay visually consistent in a grid.
- `Note Preview` is the primary card preview; `Summary Preview` is secondary supporting context.
- If a note has no generated summary yet, cards should show `No summary available yet.`
- Public/Private card state should use a subtle globe/lock icon when needed instead of another large badge.
- Public Library scalability should come from section limits and focused section views, not from removing subject/tags/previews/engagement metadata from cards.
- Public note detail is read/copy/share only and should not show edit, delete, or study actions.
- Copied private notes should show attribution as `Copied from {title} in Public Library.` with a link back to the original public note when available.
- Public Profiles should use `/public/profile/{userId}` in V1 and show:
  - Display Name
  - Bio (or `This user hasn't added a bio yet.` when blank)
  - optional Learner Level
  - optional Course / Program
  - avatar/initials
  - Profile Type
  - Official badge when the account is official/admin
  - derived public-note subjects
  - Total public notes
  - Total copies across that user's public notes
  - Total shares across that user's public notes when real share data exists
  - Total views across that user's public notes when real view data exists
  - list of that user's public notes with Title, Subject badge, Tags, and Copy count
- Public Profile should feel like a lightweight learning portfolio:
  - compact header metrics
  - derived learning-focus summary from real public-note subjects/course-programs when reliable
  - optional featured-note callout only when a real public note has copy/share/view signal
- Public Profiles must only include notes where `visibility=PUBLIC`.
- Public Profiles must never show email.
- Public Profile owner controls belong on `/public/profile/{userId}`, not on `/profile`.
- Owner-only Public Profile controls are:
  - `Edit Profile` -> routes to `/profile`
  - `Share Profile`
  - `Public Profile On` / `Public Profile Off`
- Public Profile owner controls should follow the Note Detail header pattern:
  - visibility is shown as a badge/dropdown near the title cluster
  - stats remain in their own section below the identity summary
  - `Share Profile` sits in the lower-right action row of the header card
- Public Profile should use a page-level `Back` button above the header card.
- Do not place the `Back` button inside the Public Profile header card.
- Non-owners must not see `Edit Profile` or the visibility toggle on Public Profile.
- If `publicProfileVisible = false`, non-owners should see `This profile is private.`
- If the user has no public notes, show `This user has no public notes yet.`
- Public Profile note cards should use the same interaction model as Library and Public Library:
  - whole card click opens the public note detail page
  - do not add redundant inline action buttons inside the card body
- Public Profile note cards should use the shared note-card preview layout, including both `Note Preview` and `Summary Preview`.
- Note cards remain preview/navigation surfaces only; note actions belong in Note Detail.
- Owner actions on public note detail may include:
  - `Open Note`
  - `Share`
- Non-owner actions on public note detail may include:
  - `Make a Copy`
  - `Share`
- Private Note Detail owns:
  - `Edit`
  - `Delete`
  - `Generate Study Pack`
- Study surfaces own:
  - `Quick Review`
  - `Challenge Quiz`
  - `Adaptive Practice`
- Action-button behavior should stay consistent across Dashboard, Library, Public Library, Note Detail, Public Note Detail, Profile, Public Profile, Settings, and Admin surfaces:
  - desktop buttons show icon + text
  - important mobile buttons must show icon + text
  - icon-only mobile buttons are reserved for small, already-familiar utility controls
  - visibility controls use badge/dropdown presentation, not large standalone buttons
  - header actions sit top-right and card actions sit bottom-right when present
- Dark-mode outline button styling should remain readable across secondary actions such as `Challenge Quiz`, `Adaptive Practice`, `Make a Copy`, `Share`, and `Edit`:
  - use a lighter dark-mode border than the surrounding card background
  - use near-white text in dark mode
  - keep a visible dark-mode hover fill
  - do not reuse light-mode border contrast assumptions in dark mode
- Navigation icon mapping should stay fixed across desktop and mobile sidebars:
  - `Dashboard` -> `Home`
  - `Library` -> `Book`
  - `Public Library` -> `Globe`
  - `Profile` -> `User`
  - `Settings` -> `Gear`
  - `Admin` -> `Shield`
- Standard action icon mapping should remain consistent:
  - `Edit` -> `Pencil`
  - `Delete` -> `Trash`
  - `Share` -> `Share` / `Link`
  - `Copy` -> `Duplicate`
  - `Open` / `View` -> open / external arrow icon
  - `Public` -> `Globe`
  - `Private` -> `Lock`
  - `Library` -> `Book`
  - `Dashboard` -> `Home`
  - `Profile` -> `User`
  - `Settings` -> `Gear`
  - `Admin` -> `Shield`
  - `Back` -> `Arrow Left`
  - `Save` -> `Disk`
  - `Quick Review` -> `Lightning`
  - `Challenge Quiz` -> `Trophy` / challenge icon
  - `Adaptive Practice` -> `Target` / focused-practice icon
  - `Study Pack` -> `Sparkles` / book icon
- Use outline-style icons only and do not mix filled and outline icon sets on the same product surface.
- Do not use emoji as icons in product UI.
- Quiz entry buttons on Note Detail and other quiz launch surfaces should use distinct icons so users can distinguish fast review, exam challenge, and weak-area practice at a glance.
- Note Detail uses view tabs, not action buttons, for `Summary`, `Key Concepts`, `Quiz`, and `Full Notes`:
  - keep `Summary` as the default tab on private and public note detail
  - use the reading flow `Summary` -> `Full Notes` -> `Key Concepts` -> `Quiz`, while keeping the visual tab order stable
  - use the tab order `Summary` -> `Key Concepts` -> `Quiz` -> `Full Notes`
  - place the tab row below the header/actions and above the selected note/study content
  - use underline-style navigation with muted inactive labels and an active bottom border
  - desktop and mobile tabs show icon + text for major note-view switching
  - tab switches should stay on the same note view and update content without a full page reload
  - tab switches must not reset the page scroll to the top
  - preserve the user's position around the tab content area when switching views
  - changing `?tab=` must not trigger a note-detail refetch or loading-state remount
  - `Full Notes` should render the complete original note body so users can evaluate the source note without entering edit mode
  - the `Summary` view should include a subtle `View Full Notes →` CTA above the summary text to guide users to the source note without overpowering the summary preview

### Display Name And Official Badge

- `users.display_name` is the public author name field.
- `Profile -> Identity` owns:
  - `firstName`
  - `lastName`
  - `displayName`
  - `email`
- `Profile -> Learning Profile` owns:
  - `learnerLevel`
  - `courseProgram`
  - `bio`
- `Learning Profile` combobox-style fields should reuse the same input-plus-suggestions pattern as the Note Editor `Subject` field.
- `Course / Program` in `Learning Profile` must use the same shared autocomplete behavior as Note Editor, Onboarding, and Note Detail metadata edit.
- `Course / Program` helper text should change with `learnerLevel`, with learner-stage-specific examples instead of one generic prompt.
- `Profile` should include:
  - a top Display Name card with avatar, display name, email, and right-aligned `View Public Page` navigation
  - an `Identity` card with `firstName`, `lastName`, `displayName`, and `email`
  - a `Learning Profile` card with required `learnerLevel`, required `courseProgram`, and optional `bio`
  - a `Profile Type` card with the profile-type selector
- `Profile` save actions should be section-specific:
  - `Save Identity` only saves identity fields
  - `Save Learning Profile` only saves learner-level, course/program, and bio fields
  - `Save Profile Type` only saves the profile type field
- `Save Learning Profile` validation should stay local to the Learning Profile card and must not block `Save Identity` or `Save Profile Type`.
- `Save Learning Profile` should show inline validation messages when required fields are missing:
  - `Please select your learner level.`
  - `Please select or enter your course / program.`
- `View Public Page` on `/profile` is navigation only and should live in the top Display Name card.
- `Profile` should not own Public Profile sharing or visibility controls.

### Navigation Behavior

- Public Profile should use a `Back` button driven by navigation history (`router.back()`), not a hardcoded return path.
- Arrow glyphs should not be appended to in-app button labels when the action icon already communicates navigation.
- If `displayName` is blank, public author fallback is `firstName`.
- Public pages must never show the user's email address.
- Reserved display names are rejected server-side. The following are reserved case-insensitively:
  - `notelib`
  - `admin`
  - `support`
  - `official`
  - `moderator`
  - `staff`
  - `team`
- Any display name containing `notelib` is also rejected.
- Validation message: `This display name is reserved. Please choose another name.`
- Official/public-profile badge state is backend-driven from the configured official email plus eligible admin accounts.
- The official NoteLib account renders as:
  - author label -> `By NoteLib`
  - badge -> `Official`
- Subject display should stay consistent across Library, Public Library, Private Note Detail, and Public Note Detail:
  - render subject as a reusable badge, not `Subject: ...` text
  - place subject on the same line as the author label on note headers
- Course / Program display should stay subtle:
  - show it as supporting metadata on library cards and note detail headers
  - do not promote it into a large badge that competes with subject/state badges
- Subject persistence and suggestions:
  - `notes.subject` remains the persisted source of truth
  - subject suggestions come from `GET /api/subjects` using distinct existing note subject values
  - the current subject catalog is derived from saved notes, not from a separate `subjects` table
  - Library and Note Editor use the authenticated `mine` subject scope
  - Public Library uses the `public` subject scope
  - users can still type a custom subject and save it directly into `notes.subject`
  - saved custom subjects become future suggestions once the note is persisted
  - subject saves should normalize whitespace and dash formatting so values such as `Biology-Cell Division` and `Biology – Cell Division` collapse into one reusable subject key
  - subject reuse checks should be case-insensitive so equivalent saved subjects resolve to the same autocomplete/filter label when possible
  - AI-generated subjects should be library-friendly academic labels, usually `Primary field – subtopic` when that helps group similar notes
  - avoid broad catch-all labels such as `Medicine`, `Engineering`, `Education`, `Law`, or `Business` when the notes support a narrower academic subject
  - example targets: `Nursing – Pharmacology`, `Biology – Cell Division`, `Criminal Law – Crimes Against Persons`, `Software Engineering – Data Structures`
  - no normalized `subjects` table is required for the current version
- Course / Program persistence and suggestions:
  - `users.courseProgram` is the profile-level default and `notes.courseProgram` is the note-level persisted source of truth
  - the current course/program catalog is derived from saved values, not from a separate `course_programs` table
  - `GET /api/course-programs?scope=mine` returns normalized distinct course/program values from the authenticated user's notes plus their profile default
  - `GET /api/course-programs?scope=public` returns normalized distinct course/program values from public notes
  - users can still type a custom course/program and save it directly into `notes.courseProgram`
  - saved custom course/program values become future suggestions after the note is persisted
  - normalize saved course/program values for whitespace and dash formatting and reuse them case-insensitively when possible
- Library and Public Library should share the same control order:
  - `Search`
  - `Filter`
  - `Sort`
  - notes list
- On mobile, Library and Public Library filters/sort should open a bottom-sheet or modal instead of remaining always visible.
- Private Library filters:
  - `Subject`
  - `Tags`
  - `Study Pack Ready`
  - `Draft`
  - `Public`
  - `Private`
- Public Library filters:
  - `Subject`
  - `Tags`
  - `By You`
  - `Official`
- Private Library sort options:
  - `Recently Updated`
  - `Recently Reviewed`
  - `Recently Generated`
  - `Title (A-Z)`
  - `Title (Z-A)`
  - `Oldest`
- Public Library sort options:
  - `Newest`
  - `Most Copied`
  - `Most Viewed`
  - `Title A-Z`

Dashboard guidance rules:

- Dashboard is non-destructive and guidance-first.
- Deletion is not available from Dashboard.
- Dashboard is personalized by `profileType` presentation only; it must not create separate note, study-pack, quiz, or activity systems.
- `STUDENT` dashboard should prioritize:
  - `Continue Studying`
  - `Weak Concepts`
  - `Recent Notes`
  - `Quick Review`
  - `Usage / Progress`
  - main CTA -> `Continue Studying`
- `Continue Studying` must identify the note being resumed, not only the quiz mode:
  - show note title prominently
  - show subject and course/program when available
  - show progress such as `Question X of Y`
  - show the backend-resolved resume label for `Quick Review`, `Challenge Quiz`, or `Adaptive Practice`
  - keep the resume button routed from the single continue-studying payload; do not add extra frontend fetches to label the card
- `BOARD_EXAM` dashboard should prioritize:
  - `Exam Countdown` when `examDate` exists
  - `Practice Challenge Quiz`
  - `Weak Areas`
  - `Adaptive Practice`
  - `Study Activity This Week`
  - `Usage / Progress`
  - main CTA -> `Practice Challenge Quiz`
- `TEACHER` dashboard should prioritize:
  - `Create Quiz`
  - `Upload / Paste Material`
  - `Recent Materials`
  - `Recently Generated Quizzes`
  - `Activity`
  - `Usage`
  - main CTA -> `Create Quiz`
- Note entry modes may change the initial editor focus and post-generation destination without changing the underlying note pipeline:
  - `/notes/new?mode=quiz` focuses quiz creation and should open note detail with `tab=quiz`
  - `/notes/new?source=paste` focuses pasted material entry and should open note detail with `tab=quiz`
  - `/notes/new?source=upload` focuses the upload panel and should open note detail with `tab=quiz`
  - `/notes/new` remains the normal note-creation flow and should open note detail with `tab=summary`
- Dashboard performance and weak-concept insights must be computed from existing quiz session data only.
- Dashboard must not use LLM calls for statistics or recommendations.
- `Focus Areas` should show the top weak concepts and route Premium users to Adaptive Practice through `noteId`.
- Free users should see the same weak concepts but hit the soft Premium paywall when trying to start Adaptive Practice from Dashboard.
- Board Exam CTA wording may use exam-prep labels such as `Practice Challenge Quiz` and `Weak Areas`, but must still use the same shared note, quiz-session, activity, and usage data.
- Teacher CTA wording may use material and quiz-generation labels, but the underlying workflow remains `Note -> Study Pack -> Quiz`.
- Post-generation note detail should stay on the same unified note route and use `tab=summary` or `tab=quiz` to choose the initial study view rather than creating separate note-detail pages.
- Dashboard monthly usage should show:
  - Study Packs
  - Challenge Quiz
  - Adaptive Practice for Premium only
- OCR usage must stay hidden from the dashboard UI.

### Shareable Study Packs

- Public share links use `/p/{token}`
- Shared pages are read-only and auth-aware
- Share page can show title, summary, key concepts, and quiz preview
- Remix/copy duplicates into current user library and must not call LLM
- Duplicate title resolution:
  - `{Title}`
  - `{Title} (Copy)`
  - `{Title} (Copy 2)`, `{Title} (Copy 3)`, ...
- Success feedback: `Study Pack copied to your library.`

### Navigation

Sidebar groups:

- Main: Dashboard, Library, Public Library
- Account: Profile, Settings

Primary routes:

- `/library` (Library, private workspace)
- `/library/public` (Public Library)
- `/notes/{id}` (Note Detail)
- `/public/library/{subject}` (Public Subject Listing, SEO)
- `/public/library/{subject}/{slug}` (Public Note Detail, read-only, SEO)
- `/public/profile/{userId}` (Public Profile, public/non-canonical V1 route)

Page responsibilities:

- Dashboard = what to do now
- Library = private workspace
- Public Library = discovery
- Public Profile = public showcase
- Profile = identity
- Settings = app preferences

### Quick Review

- Primary quiz mode for a Study Pack-ready Note
- Generated during Study Pack generation and stored on the Note-owned Study Pack payload
- Immediate correctness feedback (`green = correct`, `red = incorrect`)
- Retry incorrect questions once
- Optional confidence feedback (`HIGH`, `MEDIUM`, `LOW`)
- Session history persists for progress tracking
- Quick Review quiz generation rules:
  - exactly 5 questions
  - learner-level aware, defaulting to `College` when the user has no saved learner level
  - optimized for fast concept checks (~30 to 60 seconds per question)
  - focused on definitions, key ideas, and direct understanding rather than exam-style traps
  - quantitative topics may include a simple computation only when clearly supported by the notes
  - raw LLM output may use an `A`/`B`/`C`/`D` answer letter, but canonical stored quiz data must normalize to `question`, `choices`, `correctIndex`, `explanation`, and `concept`
  - runtime quiz rendering must derive `A` / `B` / `C` / `D` from displayed order only; letters are not part of canonical stored data

### Challenge Quiz

- Timed exam-style mode (10 minutes)
- Generated from Study Pack summary + key concepts, plus learner-level and note-context metadata
- Board Exam Mode is the strict exam-simulation presentation of the Challenge Quiz engine and is available as a distinct Challenge mode for both Free and Premium users in the current rollout stage.
- The Challenge Quiz screen presents both `Challenge Quiz` and `Board Exam Mode` as explicit mode choices.
- Board Exam Mode uses a dedicated `Board Exam setup` confirmation state with timer/question/result summary plus `Cancel` and `Start Exam`.
- Board Exam Mode may request fullscreen/focus mode as a best-effort browser enhancement.
- Difficulty selection remains Premium-gated, but Board Exam Mode still runs for Free users with recommended difficulty/question count.
- Difficulty and question count adapt by latest Quick Review score:
  - `<50`: 10 questions, easy-medium
  - `<80`: 12 questions, medium
  - `>=80`: 15 questions, medium-hard
- Reuse existing in-progress session to avoid duplicate LLM calls
- Persist in-progress state (answers, index, timer basis)
- Usage limit: 50/month (separate from Study Pack generation quota)
- Challenge Quiz generation rules:
  - learner-level aware, defaulting to `College` when the user has no saved learner level
  - exam-style and analysis-oriented
  - should not repeat Quick Review questions for the same Study Pack
  - quantitative subjects may include computation, formula-based, and multi-step problem-solving questions
  - explanations should teach like a tutor; computation explanations should show short step-by-step solution flow
  - each generated question must use strict JSON fields: `question`, `choices`, `answer`, `explanation`, `concept`
  - backend and session storage must normalize generated questions to canonical `choices + correctIndex` before grading or rendering

### Adaptive Practice (Premium)

- Generated from Study Pack summary + key concepts + weak concepts, plus learner-level and note-context metadata
- Question count by weak-concept volume:
  - `<=2`: 5
  - `<=4`: 7
  - `>=5`: 10
- Reuse existing in-progress session to avoid duplicate LLM calls
- Usage limit: 50/month (separate from Study Pack generation quota)
- Adaptive Practice generation rules:
  - weak-concept reinforcement only; do not drift into unrelated topics
  - learner-level aware, defaulting to `College` when the user has no saved learner level
  - slightly simpler and more targeted than Challenge Quiz
  - quantitative weak concepts may use focused numerical questions when appropriate
  - explanations should reinforce the concept clearly and step through computations when applicable
  - each generated question must use strict JSON fields: `question`, `choices`, `answer`, `explanation`, `concept`
  - backend and session storage must normalize generated questions to canonical `choices + correctIndex` before grading or rendering

### Quiz Generation Reliability

- Quiz-generation prompts must return valid JSON only, with no markdown or extra prose outside the JSON object.
- Generated quiz items must always include:
  - `question`
  - `choices` (exactly 4)
  - `answer` (`A`, `B`, `C`, or `D`)
  - `explanation`
  - `concept`
- Canonical stored/shared quiz data must normalize to:
  - `question`
  - `choices`
  - `correctIndex`
  - `explanation`
  - `concept`
- Quiz sessions must store selected canonical choice indexes. Legacy answer text or `answerIndex` payloads may still be normalized on load for backward compatibility.
- Learner level should influence complexity:
  - `Grade School` -> very simple, direct questions
  - `Junior High` / `Senior High` -> concept understanding plus simple problem solving
  - `College` -> deeper understanding and moderate analysis
  - `Board Exam Review` -> exam-style, situational, multi-step reasoning
  - `Professional` -> applied, real-world, case-based
  - `Personal Learning` -> practical and accessible, around a college-foundation baseline
- If learner level is missing, backend prompt construction should default quiz difficulty to `College`.

## Plan Usage Display

- `Settings -> Plan & Billing` shows billing-cycle usage bars instead of raw counters.
- Free users see:
  - `Study Packs`
  - `Challenge Quiz`
- Premium users also see:
  - `Adaptive Practice`
- OCR usage is tracked in backend but hidden from the Settings UI.
- Usage bars use warning colors:
  - `0-60%` normal
  - `60-85%` warning
  - `85-100%` danger
- Usage reset dates are based on the billing cycle, not the calendar month.
- When a Free user hits a visible limit, Settings should show an `Upgrade to Premium` CTA.
- Study Pack enforcement and user-facing remaining counts must come from the same backend-resolved usage calculation.
- Study Pack generation is allowed only when `used < limit` and is blocked when `used >= limit`.
- Study Pack quota increments only after a successful Study Pack is persisted.
- Failed Study Pack generation, note saves, opening generation screens, and failed retries must not consume quota.
- Study Pack near-limit messaging should appear when `studyPacksRemaining <= 2` and should show the actual remaining count with plan-specific monthly-limit copy.
- When `studyPacksRemaining == 0`, `Generate Study Pack` should remain clickable and open the shared monthly-limit modal instead of rendering as a disabled action.

## Study Pack Generation Consistency

- `Create Note` and `Note Detail` are both valid Study Pack generation entry points for draft notes.
- Both generation entry points must use the same metadata-suggestion behavior for AI-generated `title`, `subject`, and `tags`.
- Generated metadata must never silently overwrite user-entered `title` or `subject`.
- Create Note hands off to Note Detail during asynchronous generation; the suggestion modal appears there after the Study Pack becomes ready.
- After generation, users should see the shared suggestion review modal with per-field choices:
  - `Title` -> `Keep My Title` or `Use AI Title`
  - `Subject` -> `Keep My Subject` or `Use AI Subject`
  - `Tags` -> `Keep My Tags`, `Merge Tags`, or `Use AI Tags`
- The AI Suggestions modal should behave like a review-and-decision screen:
  - compact sections for `Title`, `Subject`, and `Tags`
  - comparison of `Yours` vs `AI`
  - radio-button decisions rather than long stacked action buttons
  - tag chips for tag display instead of long button labels
  - live `Preview` of the final metadata outcome
  - primary footer action = `Apply Changes`
  - secondary footer action = `Skip`
- Modal layout requirements:
  - desktop: max width around `640px`, max height `80vh`, internal scroll when needed
  - mobile: full-screen modal with scrollable content and sticky footer
- Default AI suggestion choices should stay user-safe:
  - existing `title` -> default `Keep My Title`
  - existing `subject` -> default `Keep My Subject`
  - existing `tags` -> default `Merge Tags`
  - no existing `tags` -> default `Use AI Tags`
- AI subject suggestions should prefer specific academic library-friendly subjects instead of broad catch-all categories when the notes support that specificity.
- Backend generation context carries `learnerLevel`, `courseProgram`, note `subject`, and note `tags` into Study Pack generation so AI subject suggestions can use learner/course context without changing the existing note-storage model.

### Authentication Session Handling

- Protected routes require auth
- `401` on protected API calls clears auth and redirects to `/login`
- Preserve destination with `redirect` query param
- Session-expired redirects include `reason=session_expired`
- Manual logout should use neutral login messaging and must not show the session-expired warning
- Manual logout intent must suppress late `401` / expired-session redirects from in-flight requests so the login page reason stays neutral
- Logged-out protected-route access may use a neutral auth-required reason and must not show the session-expired warning
- After a successful login, the frontend must route with `router.replace(...)` instead of relying on shell visibility alone.
- Post-login destination order is:
  - verification/onboarding destination first when required
  - explicit `redirect` query destination when present for protected-route access and session-expired recovery
  - `Dashboard` as the fallback
- Query-string state such as `?tab=quiz` must be preserved in redirect restoration.
- Manual login from public pages should land on `Dashboard`, not return to a public marketing/discovery page automatically.
- Login-page messaging should match the auth reason:
  - `reason=session_expired` -> `Your session has expired. Please log in again.`
  - `reason=logged_out` -> neutral logout messaging
  - `reason=auth_required` or no reason -> neutral login prompt
- Auth pages (`/auth`, `/login`, `/signup`) must immediately redirect authenticated users away from the auth form.
- Auth pages must not remain visible once authentication succeeds.
- Users can sign up/login before verification; unverified users are blocked from generation
- Unverified users are also blocked from OCR upload
- Verification email delivery uses provider-agnostic `EmailService`
- Transactional email content uses file-based templates
- Retention emails use Resend-backed delivery with file-based templates and `email_log` cooldown tracking
- User-facing email templates should use first-name personalization when available and fall back to `Hi there,`
- User-facing email templates should share the standard footer:
  - `— NoteLib`
  - `Turn Notes Into Quizzes`
  - `https://notelib.app`
- Retention reminders include:
  - inactive-user reminder after `3` days without meaningful study activity
  - weak-concept reminder after `3` days without follow-up practice on weak Challenge Quiz concepts
  - weekly progress summary every Sunday at `6:00 PM`
- Session-expiry recovery must clear stale local auth state before redirecting to login so a re-login behaves like a fresh auth success.
- First-study product onboarding is separate from preferences onboarding and guides new users through:
  - verify email and see the first-study activation welcome screen
  - create note
  - generate Study Pack
  - start the first Challenge Quiz from the Study Pack success banner
  - review weak concepts after the first quiz result
  - return to Dashboard
- After email verification, first-time users with `studyPackCount == 0` should see a welcome CTA before landing on an empty dashboard:
  - `Create First Note`
  - `Go to Dashboard`
- Dashboard empty state for first-time users should be explicit:
  - title: `You don't have any Study Packs yet`
  - description: `Create a note and generate your first quiz in a few minutes.`
  - primary CTA: `Create Your First Note`
- After the first Study Pack is generated, Note Detail should show a success banner that points the user to `Start Challenge Quiz`.
- After the first Challenge Quiz is completed, the result screen should show a weak-concepts guidance banner with `View Weak Concepts`.

### Preferences Onboarding

Route: `/onboarding`

Preferences onboarding is reused and extended rather than duplicated.

Current onboarding order:

1. `Profile Type`
2. `Learning Profile`
3. `Learning Style`
4. `Study Reminder Frequency`
5. `Exam Date` only when `profileType = BOARD_EXAM`
6. finish and redirect to `Dashboard`

Profile Type options:

- `STUDENT`
- `BOARD_EXAM`
- `TEACHER`

Rules:

- `Learning Profile` collects:
  - required `learnerLevel`
  - required `courseProgram`
  - optional `bio`
- learner metadata inputs should reuse the same combobox pattern as the Note Editor `Subject` field instead of plain browser datalists
- Course / Program uses the same real-time autocomplete filtering and learner-level helper text as the Profile learning-profile card.
- `Learning Style` and `Study Reminder Frequency` remain the existing onboarding steps
- `Exam Date` is conditional and must be skipped for `STUDENT` and `TEACHER`
- onboarding state is loaded from `GET /auth/me`
- onboarding completion is persisted through the existing onboarding completion flow
- users who already completed onboarding should be redirected to `Dashboard`

### Profile

Route: `/profile`

`Profile` owns identity and learner-account fields only.

Identity section:

- `firstName`
- `lastName`
- `displayName`
- `email`
- `Save Identity`

Learning Profile section:

- `learnerLevel`
- required `courseProgram`
- optional `bio`
- separate `Save Learning Profile` action

Profile Type section:

- `profileType`
- separate `Save Profile Type` action

Account information section:

- `Member Since`
- `Plan`
- `Study Packs Created`

`Profile` must not include:

- `Learning Style`
- `Study Reminder Frequency`
- other app-behavior preferences that belong in Settings

Email change flow:

- saving identity updates `firstName` and `lastName` immediately
- if `email` changes, NoteLib stores the new value in `pendingEmail`
- verification is sent to `pendingEmail`
- the UI should tell the user: `Please verify your new email address before it replaces your current email.`
- after verification, `email = pendingEmail`, `pendingEmail = null`, and `emailVerifiedAt` is refreshed
- email changes must never replace the active account email before verification

### Email Templates

Current user-facing template set:

- verification email
- welcome email
- inactivity reminder
- weak concept reminder
- weekly summary
- premium waitlist confirmation

Welcome email requirements:

- position NoteLib as `Turn Notes Into Quizzes`
- Free plan includes:
  - `10` Study Packs per month
  - Quick Review
  - Challenge Quiz with a monthly limit
  - Public Library access
- Premium includes:
  - Adaptive Practice
  - Weak Concept Training
  - Difficulty Selection
  - Higher monthly limits
- welcome copy must not say Challenge Quiz is Premium-only

### Settings Preferences

Route: `/settings`

`Settings > Preferences` remains the only place for:

- `Learning Style`
- `Study Reminders`
- future behavior and reminder preferences

### Plan and Billing

Settings route section: `Plan & Billing`

- show plan (`FREE` or `PREMIUM`)
- support Premium billing cycle selection:
  - `MONTHLY`
  - `YEARLY`
- show usage buckets separately:
  - Study Packs (monthly quota)
  - Challenge Quiz (plan-based monthly quota)
  - Adaptive Practice (Premium-only, plan-based monthly quota)
  - OCR (tracked internally, hidden from user-facing usage UI)
- PayMongo recurring subscription checkout for upgrade
- Billing webhook sync keeps plan state aligned (webhook-driven source of truth)
  - `subscription.activated`
  - `subscription.invoice.paid`
  - `subscription.invoice.payment_failed`
  - `subscription.past_due`
  - `subscription.unpaid`
  - `subscription.updated`
- Premium-gated upgrade prompts should link to `/settings#plan-billing`

Plan limits:

- Free: unlimited notes, 10 Study Packs/month, 5 Challenge Quizzes/month, OCR quota, file uploads, weak concept visibility
- Premium: 100 Study Packs/month, 50 Challenge Quizzes/month, 30 Adaptive Practice sessions/month, higher OCR quota, difficulty selection, priority AI
- Usage windows are billing-cycle-based:
  - Free resets monthly from account creation date
  - Premium resets from the active subscription billing window

---

## Activity Tracking

Track lightweight events such as:

- `CREATED_STUDY_PACK`
- `STARTED_QUICK_REVIEW`
- `COMPLETED_QUICK_REVIEW`
- `COMPLETED_ADAPTIVE_QUIZ`

Events are linked to user + note context and timestamp.

Canonical ownership rule:

- Generated summaries, key concepts, quiz content, and all practice sessions are note-scoped (`noteId`).
- Any legacy `studyPackId` fields are compatibility fields only.

---

## Profile Navigation Model

Two distinct profile surfaces:

- **Public Profile** (`/public/profile/{userId}`) — public identity and learning-portfolio page
  - View-only to non-owners
  - Owner controls: `Edit Profile`, `Share Profile`, visibility toggle
  - Accessible from avatar dropdown (`My Profile`) and sidebar (`Profile`)
- **Profile Settings** (`/profile`) — private account and identity editing page
  - Accessible from the `Edit Profile` button on Public Profile

Avatar dropdown:
- `My Profile` → public profile page
- `Settings` → `/settings` (account and app settings)
- `Sign Out`

Sidebar Account section:
- `Profile` → public profile page
- `Settings` → `/settings`

Terminology rule: **Profile = public identity page. Settings = account/app settings.** Do not mix these.

## Sharing Rules

NoteLib uses one share rule across all content types:

| Content visibility | Share behavior |
|---|---|
| Public | `Share` opens the share modal with `Shareable URL`, `Copy Link`, and `Close` |
| Private | `Share` opens a confirm modal offering `Make Public & Share`; the share modal only opens after owner confirmation |

Note private confirm: `This note is private` / `You need to make this note public before sharing. Anyone with the link will be able to view and copy this note.`

Profile private confirm: `This profile is private` / `You need to make this profile public before sharing. Anyone with the link will be able to view your public profile and notes.`

Share modals across the app must reuse the same `AppModal` component with title, `Shareable URL` field, `Copy Link`, and `Close` layout. Do not use toast-only or inline-only share feedback as the primary share confirmation.

## Non-Goals (Current Scope)

Not included unless explicitly requested:

- spaced repetition scheduling
- full exam simulation grading engine
- heavy analytics dashboards
- classroom/teacher management
- collaborative/family linking features
