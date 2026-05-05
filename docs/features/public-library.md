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
- 🔥 Featured Notes — top 3 study-ready notes by quality + engagement
- 📈 Most Popular — top 5 notes meeting the social-proof threshold, ordered by copies then views (excluding Featured notes)
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
- Changing the audience rail (`All`, `Student`, `Board Taker`, `Teacher`) reloads Public Library for that note audience

## Ranking Audit Summary

Current audit findings:

- views and copies are already persisted and available in both frontend and backend ranking paths
- Recent already uses `createdAt DESC`
- discovery ranking existed in both frontend and backend, but the formulas were inconsistent before this alignment
- the Public Library page already had clean section-deduping worth preserving

## Ranking Philosophy

- Featured = quality + engagement
- Popular = social proof
- Recent = freshness
- evaluation should stay lightweight and trustworthy: simple signals > complex social systems

## Featured Notes Ranking

Featured notes are selected only from notes that are actually worth studying now:

- `visibility = PUBLIC`
- `studyPackStatus = STUDY_PACK_READY`
- meaningful summary preview exists
- quiz/generated study content exists
- note preview is not empty

Featured notes are then ranked using a simple engagement score:

```text
score = viewCount + (copyCount * 3) + (likeCount * 2)
```

- Tiebreaks:
  - `copyCount DESC`
  - `viewCount DESC`
  - `createdAt DESC`
- Discovery-home limit: 3 notes in `Featured Notes`

## Most Popular Ranking

Popular is a thresholded social-proof section, not just a generic sort.

A note qualifies as Popular when:

- `viewCount >= 20`
- or `copyCount >= 3`

Popular ordering:

- `copyCount DESC`
- `viewCount DESC`
- `likeCount DESC`
- `createdAt DESC`

The Popular badge uses the same threshold so the card label and the section logic stay aligned.

## Like System

Public Library uses likes as the lightweight evaluation layer for public notes.

- authenticated users can like or unlike a public note
- one user = one like per note
- likes should stay anonymous; do not show usernames, follower patterns, or comments
- guests tapping like should see an auth prompt modal instead of a silent failure
- note cards should show the heart count subtly near the existing views/copies metrics
- a note may show `❤️ Well liked` when `likeCount >= 10`

## Recently Added Ranking

Recent remains intentionally simple:

- `createdAt DESC`

## Deduplication Across Sections

Sections never repeat the same note:
- Featured: top 3 eligible notes by score from all public notes
- Most Popular: top 5 threshold-qualified notes from notes NOT in Featured
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

- `Audience` / note target profile
- `Course / Program`
- `Learner Level` when public note-owner metadata is available
- `Subject`
- `Tags`
- `By You`
- `Official`
- `Community`

Public Library browsing rails:

- `Audience` is note-owned and uses:
  - `All`
  - `Student`
  - `Board Taker`
- default audience:
  - `Student` -> `Student`
  - `Board Taker` -> `Board Taker`
  - `Teacher` -> `All`
  - guest -> `All`
- audience filtering must use `note.targetProfileType`, never the creator's `user.profileType`
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

`GET /notes/public` also accepts an optional `?targetProfileType=` query parameter:

- supported values: `STUDENT`, `BOARD_TAKER`
- when omitted, backend returns all public notes
- when present, backend returns only notes where `note.targetProfileType` matches the selected category

## Empty state

If the selected audience category has no matching notes and no other filters are active:

- show `No notes available for this category yet.`
- show `View all notes`

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
- Quality badges (High Quality, Well liked, Popular) — below title alongside state badge
- `Note Preview` (compact, line-clamped)
- `Summary Preview` (compact, line-clamped)
- limited Tags (`3-4` visible plus overflow count)
- subtle metrics row for `views`, `copies`, and `likes`, with the heart control staying visually secondary
- featured content should remain visually special through stronger section framing instead of being flattened into a plain list

Interaction rules:

- whole card opens the canonical public note route
- keep the card itself clickable for navigation
- Public Library cards may include one inline secondary CTA at the bottom-right: `Save`
- Public Library cards may also include a subtle inline heart toggle in the metrics row
- the inline save CTA must stop card navigation when clicked
- the inline heart toggle must stop card navigation when clicked
- the CTA should stay subtle:
  - icon + short label
  - outline / ghost weight
  - never full width
- guest clicks on `Save` should open an auth prompt modal instead of redirecting immediately
- guest clicks on the heart toggle should open an auth prompt modal instead of silently failing
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

### Creator Identity Trust Fix (planned for v0.12.0)

Public Library is becoming an acquisition and sharing surface, so creator identity must stay readable **and** unambiguous.

- `displayName` is the presentation label, not the unique creator identity
- public note cards and public note detail must not rely on `displayName` alone when duplicate names exist
- use a stable public creator identifier for linking and disambiguation:
  - preferred: username / handle when available
  - fallback direction: generated public slug
- suggested display:
  - `By Einar`
  - when disambiguation is needed or a handle exists: `By Einar · @einarjohn`
- public creator links must use the stable public identifier, not `displayName`
- never expose email addresses or raw private user IDs on public surfaces
- if public creator routing changes in the future, existing public links must keep working through compatibility or redirect handling

Discovery guidance:

- prioritize original note preview over generated summary when scanning cards
- keep `views`, `copies`, and `likes` subtle so they help note selection without turning into badge clutter
- use `Newest`, `Most Copied`, `Most Viewed`, and `Title A-Z` as student-friendly discovery labels
- preserve card richness while improving scalability: limit the number of cards shown per section first, then offer `View More`

## Public Note Detail

Public note pages are shareable learning pages and top-of-funnel acquisition surfaces — not just app detail screens. A visitor who arrives from a Facebook or social link must be able to understand the topic, interact lightly, and decide to act without an account.

### Page Purpose

- teach first, convert second
- do not hard-gate visitors before they see learning value
- the signup gate must appear only after the visitor has experienced something useful

### Recommended page structure

1. **Note title** — clear, topic-specific
2. **Topic hook** — a short 1–2 sentence framing of the learning angle (e.g. `This note covers photosynthesis — the process plants use to make food from sunlight.`)
3. **Tags and subject metadata** — helps the visitor evaluate relevance
4. **Quick Check / mini quiz preview** — 1–2 questions, interactive, client-side only, no account required
5. **Summary and Key Concepts** — the generated study outputs
6. **Full quiz or gated continuation** — gate behind login after the preview experience
7. **Soft conversion CTA** — `Turn your own notes into something like this`
8. **Copy to My Library / Generate CTA** — placed after value is shown, for signed-in users
9. **Share action** — always visible regardless of auth state

### Mini quiz preview rules

- expose 1–2 quiz questions without requiring login
- allow public visitors to select an answer and see correct/incorrect feedback
- do not create a quiz session row for anonymous users — all state is client-side only
- do not persist score, progress, or session data for unauthenticated users
- after the preview, show an inline gate: `Want to practice all questions and track your progress? Create a free account.`
- after signup/login, route the user toward copying the note or creating their own Study Pack — not back to the same mini preview

### CTA ordering

The conversion path must feel earned, not forced:

1. visitor lands → sees topic hook and subject
2. visitor tries mini quiz → sees value
3. visitor sees summary/key concepts → understands depth
4. soft CTA appears → `Turn your own notes into something like this`
5. copy/generate CTA follows → available to signed-in users

Never show `Copy to My Library` or `Generate Study Pack` as the first or only visible CTA on page load.

### Authenticated user view

Signed-in users see the full experience:

- `Summary`, `Key Concepts`, `Quiz`, `Full Notes` tabs
- `Copy to My Library` as the primary non-owner CTA
- `Share` always visible
- `View Full Notes →` CTA inside Summary view

### Generated note formatting for public pages

Public note pages benefit from better-formatted generated content. Prefer:

- shorter section blocks rather than one large paragraph per concept
- clear headings and sub-headings
- key-fact callouts (e.g. `Key fact: ...`)
- quick recall blocks (e.g. `In one sentence: ...`)
- exam-friendly wording: direct, declarative, testable

Avoid:
- long dense paragraph-per-concept output
- LLM filler phrases (`It is important to note that...`, `In summary...`)
- sections without a clear study takeaway

Note: formatting improvements apply to how generated content is displayed on public note pages. They do not change the underlying storage format or the authenticated note detail view unless explicitly specified.

## Limit-Reached Fallback (planned)

When a user hits their Study Pack limit, the limit modal should offer the Public Library as an alternative path (in addition to the dynamic upgrade CTAs):

- **Title**: "You've reached your limit"
- **Message**: "Explore existing Study Packs while waiting or upgrade for more access."
- **Actions**:
  - `Browse Public Library` — navigates to `/library/public`. If the user's last note has a topic/subject, pre-fill it as a search query (`?q=`).
  - Dynamic upgrade CTA from `getUpgradeCtas(currentPlan)` — navigates to `/settings?section=plans`.

This fallback turns the limit hit into a *content-discovery* moment rather than a hard wall, especially valuable for Free users early in their study journey.
