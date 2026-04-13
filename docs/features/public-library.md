# public-library.md - NoteLib Feature Context

## Goal

Public Library is the public discovery surface for shared notes. It should feel like a real discovery page — not just a flat list — where students can find high-quality notes, browse by subject or engagement signals, and decide whether to copy a note into their workspace.

Routes:

- canonical: `/public/library`
- authenticated shell entry: `/library/public`

## Landing Page Preview

Landing page should visually reinforce Public Library as a real discovery surface, not just describe it in copy.

- Use `public/landing/feature-public-library.jpg` in the landing page Public Library section.
- Use a responsive two-column feature layout on desktop with supporting copy on the left and the screenshot preview on the right.
- On mobile, stack the content with text first and the screenshot second.
- The section should use:
  - eyebrow `Public Library`
  - title `Explore notes worth studying`
  - supporting copy `Browse public notes by subject, discover popular topics, and copy useful notes into your own library to study them your way.`
  - lightweight support bullets for discovery, copying, and turning saved notes into summaries, concepts, and quizzes
- Present the screenshot inside a framed product-preview container with rounded corners, subtle border, background surface, and constrained height so it feels polished without dominating the section.
- An optional `Live product preview` label may sit above the screenshot when it helps anchor the image as real UI.

## Discovery Mode vs Filter Mode

Public Library has two display modes:

**Discovery mode** (default, no active search/filters/sort changes):
- 🔥 Featured Notes — top 3 by weighted engagement score
- 📈 Most Popular — top 5 by copies, then views (excluding Featured notes)
- 🆕 Recently Added — top 5 by createdAt (excluding Featured and Popular)
- each section includes `View More`, which opens a focused section view on the same page (`?view=featured|popular|recent`)
- subjects and popular tags remain available in the always-visible top browsing rails instead of a separate discovery block

**Filter mode** (when any search, filter, or sort is active):
- Standard sorted/filtered list of all matching public notes

Switching from discovery to filter mode:
- Typing in search → filter mode
- Selecting any filter (Course, Learner Level, Subject, Tags, Source) → filter mode
- Changing sort from Newest → filter mode
- Clicking a subject chip or tag chip in the top rails → applies filter → filter mode

## Featured Notes Ranking

Featured notes are ranked using a weighted composite score computed entirely from existing engagement signals:

```
score = (viewCount × 0.4) + (copyCount × 0.5) + (shareCount × 0.1)
```

- Tiebreak: newest `createdAt` first
- Discovery-home limit: 3 notes in `Featured Notes`
- No AI required — pure signal-based ranking

## Deduplication Across Sections

Sections never repeat the same note:
- Featured: top 3 by score from all public notes
- Most Popular: top 5 by copies from notes NOT in Featured
- Recently Added: top 5 by createdAt from notes NOT in Featured or Popular

## Layout

When no active filters (discovery mode):

1. `Search` toolbar + `Filter` + `Sort` controls (always visible)
2. one-line horizontal `Subjects` rail with `All` and `+ More`
3. one-line horizontal `Popular Tags` rail with limited chips and `+ More`
4. 🔥 Featured Notes section (hidden if empty)
5. 📈 Most Popular section (hidden if empty)
6. 🆕 Recently Added section (hidden if empty)

When a section-specific `View More` action is opened:

1. `Search` toolbar remains visible at the top
2. the page shows a focused section header and `Back to Discovery`
3. only that section's full ranked list is rendered
4. active search/filter/sort still falls back to the normal full results view

When filters active (filter mode):

1. `Search` toolbar + browsing rails + filter summary + `Filter` + `Sort` controls
2. Sorted/filtered note list (or empty state)

On mobile, `Filter` and `Sort` should open a bottom-sheet or modal instead of staying always visible.
Density improvements should come from tighter section limits and focused section views, not by stripping metadata from cards.

## Filters

Public Library filters:

- `Course / Program`
- `Learner Level` when public note-owner metadata is available
- `Subject`
- `Tags`
- `By You`
- `Official`
- `Community`

Public Library browsing rails:

- `Subjects` stays single-select with `All` as the default
- `Popular Tags` stays multi-select and should use OR logic within the tag group
- both rails should stay on one horizontal scroll line instead of wrapping
- each rail should end with `+ More` when the inline list is truncated
- `+ More` opens the shared selector surface:
  - subjects -> searchable single-select list
  - tags -> searchable multi-select list with selected tags surfaced at the top
  - mobile -> bottom sheet
  - desktop -> modal/sheet
  - actions -> `Apply`, `Clear`

## Backend Subject Filtering

`GET /notes/public` accepts an optional `?subject=` query parameter for server-side subject filtering:

- Case-insensitive match via `SubjectNormalizationUtils.normalizeForLookup`
- Applied after fetching all public notes, before sort
- Frontend currently performs client-side filtering; the `listPublicNotes({ subject })` API function supports passing subject for backend filtering when needed

## Sorting

Public Library sort options:

- `Newest`
- `Most Copied`
- `Most Viewed`
- `Title A-Z`

## Note Cards

Public Library note cards reuse the shared note-card layout:

- TOP ROW: Subject badge (blue) + Course/Program badge (neutral/gray) — above title
- Title
- Study Pack Ready badge (green) — below title when applicable
- Quality badges (High Quality, Popular) — below title alongside state badge
- `Note Preview` (compact, line-clamped)
- `Summary Preview` (compact, line-clamped)
- limited Tags (`3-4` visible plus overflow count)
- subtle metrics row for `views` and `copies` when available
- featured content should remain visually special through stronger section framing instead of being flattened into a plain list

Interaction rules:

- whole card opens the canonical public note route
- keep the card itself clickable for navigation
- Public Library cards may include one inline secondary CTA at the bottom-right: `Save`
- the inline save CTA must stop card navigation when clicked
- the CTA should stay subtle:
  - icon + short label
  - outline / ghost weight
  - never full width
- guest clicks on `Save` should open an auth prompt modal instead of redirecting immediately
- if the viewer already copied that note, replace the save CTA with muted `Saved`
- copied state should be conveyed by the available action, not by an extra `In Library` badge
- a successful card copy should show a confirmation modal with:
  - `View Note`
  - `Start Review`
- `Start Review` is the primary CTA in the modal
- `View Note` is the secondary CTA
- `View Note` replaces generic `Open` wording for copied-note navigation
- `Start Review` may route through copied-note generation first when the copied note is still a draft
- modal body copy should stay short:
  - `You can start reviewing now or come back later from your library.`
- modal/sheet header should feel success-oriented but minimal:
  - subtle check-style success indicator
  - stronger title hierarchy
- desktop should use a modal with a visible top-right close button
- mobile should use a dismissible bottom sheet instead of a centered modal
- desktop should right-align actions in the order `View Note`, `Start Review`
- mobile should stack full-width actions with the primary CTA visually first
- modal/sheet actions should use clean spacing and subtle depth so the surface feels polished without becoming heavy
- avoid generic navigation button labels like `Open` on Public Library cards; card click already owns detail navigation
- card footer should align author metadata left and save state/action right
- do not place share/generate/delete/edit actions inside Public Library cards
- use public note detail for the rest of the actions instead

Discovery guidance:

- prioritize original note preview over generated summary when scanning cards
- keep `views` and `copies` subtle so they help note selection without turning into badge clutter
- use `Newest`, `Most Copied`, `Most Viewed`, and `Title A-Z` as student-friendly discovery labels
- preserve card richness while improving scalability: limit the number of cards shown per section first, then offer `View More`

## Public Note Detail

Public note detail should help visitors evaluate both the generated study outputs and the original source note.

Rules:

- keep `Summary` as the default tab
- use `Summary`, `Key Concepts`, `Quiz`, and `Full Notes`
- include a subtle `View Full Notes →` CTA inside the `Summary` view so visitors can quickly inspect the original note
- `Full Notes` should render the complete original note body so visitors can judge whether the note is worth copying
- keep the page read-only and copy-first; tabs are for review, not management
- non-owner primary CTA should use `Copy to My Library`
