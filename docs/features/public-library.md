# public-library.md - NoteLib Feature Context

## Goal

Public Library is the public discovery surface for shared notes. It should feel like a real discovery page — not just a flat list — where students can find high-quality notes, browse by subject or engagement signals, and decide whether to copy a note into their workspace.

Routes:

- canonical: `/public/library`
- authenticated shell entry: `/library/public`

## Discovery Mode vs Filter Mode

Public Library has two display modes:

**Discovery mode** (default, no active search/filters/sort changes):
- 📚 Browse by Subject — clickable subject chips sorted by note count (top 8)
- 🔥 Featured Notes — top 3 by weighted engagement score
- 📈 Most Popular — top 5 by copies, then views (excluding Featured notes)
- 🆕 Recently Added — top 5 by createdAt (excluding Featured and Popular)
- each section includes `View More`, which opens a focused section view on the same page (`?view=featured|popular|recent`)

**Filter mode** (when any search, filter, or sort is active):
- Standard sorted/filtered list of all matching public notes

Switching from discovery to filter mode:
- Typing in search → filter mode
- Selecting any filter (Course, Learner Level, Subject, Tags, Source) → filter mode
- Changing sort from Newest → filter mode
- Clicking a Browse by Subject chip → applies subject filter → filter mode

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
2. 📚 Browse by Subject chips (hidden if no subjects, max 8)
3. 🔥 Featured Notes section (hidden if empty)
4. 📈 Most Popular section (hidden if empty)
5. 🆕 Recently Added section (hidden if empty)

When a section-specific `View More` action is opened:

1. `Search` toolbar remains visible at the top
2. the page shows a focused section header and `Back to Discovery`
3. only that section's full ranked list is rendered
4. active search/filter/sort still falls back to the normal full results view

When filters active (filter mode):

1. `Search` toolbar + filter summary + `Filter` + `Sort` controls
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
- `Note Preview`
- `Summary Preview`
- Tags
- subtle metrics row for `views` and `copies` when available
- featured content should remain visually special through stronger section framing instead of being flattened into a plain list

Interaction rules:

- whole card opens the canonical public note route
- keep the card itself clickable for navigation
- Public Library cards may include one inline secondary CTA at the bottom: `Copy to My Library`
- the inline copy CTA must stop card navigation when clicked
- if the viewer already copied that note, replace the copy CTA with:
  - `Already in your library`
  - optional `View Note`
- a successful card copy should show a confirmation modal with:
  - `Continue`
  - `View Note`
  - `Start Review`
- `Start Review` is the primary CTA in the modal
- `View Note` replaces generic `Open` wording for copied-note navigation
- `Start Review` may route through copied-note generation first when the copied note is still a draft
- avoid generic navigation button labels like `Open` on Public Library cards; card click already owns detail navigation
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
