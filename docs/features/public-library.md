# public-library.md - NoteLib Feature Context

## Goal

Public Library is the public discovery surface for shared notes. It should feel like a real discovery page — not just a flat list — where students can find high-quality notes, browse by subject or engagement signals, and decide whether to copy a note into their workspace.

Routes:

- canonical list route for signed-in and signed-out users: `/public/library`
- canonical public note detail route: `/public/library/{subject}/{slug}`
- canonical public creator/profile route: `/public/creator/{username}`
- legacy compatibility redirects:
  - `/library/public` -> `/public/library`
  - `/public/library/{subject}` -> `/public/library?subject={subject}`
  - `/public/profile/{userId}` remains compatible for existing public-profile links

Shareable filter URLs:

- `/public/library?subject=history`
- `/public/library?tag=mexican-history`
- `/public/library?search=cinco`
- `/public/library?audience=student`
- `/public/library?courseProgram=nursing`
- `/public/library?sort=recent`
- filters may be combined, for example:
  - `/public/library?subject=history&tag=mexican-history&search=cinco`
- the Public Library list page exposes `Share this list`, which copies the current canonical filtered URL instead of a stale local-only filter state
- the list-share action is most useful on smaller screens; desktop may keep the page itself shareable without giving the button primary visual weight

## Key Files

**Backend**
- `backend/src/main/java/com/studysnap/backend/controller/NoteController.java` — `GET /notes/public` (filter endpoint), `GET /notes/public/{id}`, `POST /notes/public/{id}/like`, `GET /notes/public/seo/{subject}/{slug}`
- `backend/src/main/java/com/studysnap/backend/service/NoteService.java` — `listPublic(viewerUserId, search, sort, subject, tags, courseProgram, creator, audience)`, `getPublicById`, `togglePublicNoteLike`
- `backend/src/main/java/com/studysnap/backend/repository/NoteRepository.java` — JPQL public note query with multi-param filtering
- `backend/src/main/java/com/studysnap/backend/util/PublicNotesScoringUtils.java` — discovery score formula (`viewCount + copyCount×3 + likeCount×2`) with 30-day age decay; `computeScore(note, now)`

**Frontend**
- `frontend/components/notes/public-library-page-client.tsx` — main public library client; filter state; discovery/filter mode switching; section rendering (Featured / Popular / Recent)
- `frontend/app/public/library/page.tsx` — server component entry; passes `searchParams` to client
- `frontend/app/public/library/[subject]/page.tsx` — SEO subject landing page (server-rendered, ISR 300s, `generateStaticParams`)
- `frontend/lib/public-library-url.ts` — canonical URL model: `PublicLibraryUrlFilters` type, `buildPublicLibraryUrl()`, `parsePublicLibraryFilters()`; single source of truth for public library URL construction
- `frontend/lib/public-library-discovery.ts` — frontend discovery scoring and section deduplication helpers
- `frontend/lib/api.ts` — `listPublicNotes(params?)`, `getPublicNote(id)`, `togglePublicNoteLike(id)`

## Anti-drift Notes

- Public Library back-navigation to a filtered state uses `sessionStorage` (key: `notelib_public_library_return_url`) — not `?ref=` — because public note URLs are canonical SEO slugs that must not be polluted with navigation state
- Discovery mode and filter mode are **mutually exclusive** — any active filter/search/sort switches to filter mode and hides the Featured / Popular / Recent sections
- Audience filter uses `note.targetProfileType`, never the creator's `user.profileType`
- The `creator` filter (v0.21.0) uses `username`, not `userId` or `displayName`
- Do not implement "Trending this week" without windowed backend fields (`recentCopyCount`, `recentLikeCount`) — lifetime totals on recent notes is a different signal; see section H under Planned Improvements
- Anonymous quiz sessions must not create `QuickReviewSessionEntity` rows — no backend session state until the user authenticates

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
- Changing the audience rail (`All`, `Student`, `Board Taker`) reloads Public Library for that note audience and updates the shareable URL

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
- canonical base route `/public/library` means `All`; the UI should only apply an audience filter when `?audience=` is present — **do not apply a profile-based default audience on fresh visit**
- audience filtering must use `note.targetProfileType`, never the creator's `user.profileType`
- `Subjects` stays single-select with `All` as the default
- `Popular Tags` stays multi-select and should use OR logic within the tag group
- both rails should stay on one horizontal scroll line instead of wrapping
- the subject rail may keep `+ More` when the inline subject list is truncated
- tag browsing must always stay reachable even when only a few popular tags are visible; expose a separate action such as `Browse all` / `Browse tags` instead of relying on a disappearing tag chip
- `+ More` opens the shared selector surface:
  - subjects -> searchable single-select list
  - tags -> searchable multi-select list with selected tags surfaced at the top
  - mobile -> bottom sheet
  - desktop -> modal/sheet
  - actions -> `Apply`, `Clear`

## Backend Filtering + URL Sync

`GET /notes/public` is the backend filter source for shareable Public Library URLs.

Supported query params:

- `search`
- `subject`
- `tag` (repeatable)
- `audience`
- `courseProgram`
- `creator` (username — filters to a single creator's public notes)
- `size` (optional integer, clamped to 1-50 when present — limits result count; omitted means uncapped)
- `sort`

Behavior:

- frontend filter state must hydrate from the URL query params on first render
- filter changes must update the canonical `/public/library?...` URL
- search typing should use local input state plus debounced URL sync instead of replacing the route on every keypress
- debounced search sync should use `router.replace(..., { scroll: false })` so typing stays smooth, focus stays in the input, and browser history does not gain one entry per character
- chip/filter/sort interactions should update the canonical URL immediately while preserving scroll position
- `Share this list` must copy the same canonical `/public/library?...` URL the page is currently using
- direct opens of a filtered URL must restore the same selected filters in the UI
- backend filtering is combinable and returns only `PUBLIC` notes
- search is case-insensitive
- subject, tags, and course/program use normalized slug values in the URL
- clearing filters should return to `/public/library`
- tag and subject selector search inputs must keep focus while typing; modal rerenders must not move focus to the close button or other controls

## Course/Program Helper CTA

A dismissible discovery hint shown above the note list when no `courseProgram` filter is active and no creator filter is set:

- Text: `Studying for a specific exam or program? Browse notes by Course or Program.`
- Action: `Browse by Course/Program` — opens the filter sheet
- Dismiss button (X) hides the card and stores dismissal in `sessionStorage` (key: `notelib_public_library_cp_cta_dismissed`); it reappears on a new browsing session
- Hidden when `?courseProgram=` or `?creator=` is already present in the URL
- Do not show while the note list is loading

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

### Creator Identity Trust Fix

Public Library is becoming an acquisition and sharing surface, so creator identity must stay readable **and** unambiguous.

- `displayName` is the presentation label, not the unique creator identity
- `username` is the unique public identity / handle and must be URL-safe
- public note cards and public note detail must not rely on `displayName` alone when duplicate names exist
- use `username` for creator links and disambiguation
- suggested display:
  - `By Einar`
  - when a username exists: `By Einar · @einarjohn`
- public creator links should use `/public/creator/{username}`, not `displayName`
- never expose email addresses or raw private user IDs on public surfaces
- existing `/public/profile/{userId}` links must keep working through compatibility while new links prefer username-based creator URLs

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
4. **Quick Check / mini quiz preview** — up to 3 questions, interactive, client-side only, no account required
5. **Summary and Key Concepts** — the generated study outputs
6. **Full quiz or gated continuation** — gate behind login after the preview experience
7. **Soft conversion CTA** — `Turn your own notes into something like this`
8. **Post-answer CTA** — after the visitor answers the Quick Check question, show a small follow-up block such as `Want more practice like this?` with `Create your own Study Pack` and `Copy to My Library`
9. **Copy / create CTA** — keep the stronger actions after value is shown, not above the note
10. **Share action** — always visible regardless of auth state, but secondary to the note and follow-up CTA

### Lateral discovery from the public note detail page

Tags and subject on the public note SEO page must link back to the filtered Public Library so a guest can continue browsing without needing an account.

- each tag chip in the header links to `/public/library?tag={slug}` — uses `slugifyPublicLibraryFilterValue` before putting the value in the URL
- the subject badge in the author line links to `/public/library?subject={slug}`
- do not change `SubjectBadge` itself; wrap it in a `<Link>` at the call site
- the `PublicLibraryBackLink` component intentionally returns `null` for unauthenticated visitors — for social/deep-arrival traffic, surfacing the auth-only breadcrumb is worse than nothing; clickable tags and subject cover the lateral-discovery need instead
- do not add a sticky or persistent "Back to Library" link for guests; rely on tag/subject links and the global nav

### Mini quiz preview rules

- expose up to 3 quiz questions without requiring login
- allow public visitors to select an answer and see correct/incorrect feedback
- do not create a quiz session row for anonymous users — all state is client-side only
- do not persist score, progress, or session data for unauthenticated users
- after the visitor answers, show a small CTA block that invites the next step without replacing the note
- use Quick Check copy such as `Quick check: see what you remember from the summary.`
- after signup/login, route the user toward copying the note or creating their own Study Pack — not back to the same mini preview

### CTA ordering

The conversion path must feel earned, not forced:

1. visitor lands → sees topic hook and subject
2. visitor tries mini quiz → sees value
3. visitor sees summary/key concepts → understands depth
4. soft CTA appears → `Turn your own notes into something like this`
5. post-answer CTA appears only after interaction
6. copy/create CTA follows → available without leading the page

Never show `Copy to My Library` or `Create your own Study Pack` as the first or only visible CTA on page load.

### Authenticated user view

Signed-in users see the full experience:

- stacked `Summary`, `Key Concepts`, `Quick Check`, and `Full Notes` sections, with notes still primary
- `Create your own Study Pack` as the visitor-facing generation CTA
- `Copy to My Library` as the library/save CTA
- `Share this note` always visible as a secondary action
- `View Full Notes →` CTA inside the Summary section that deep-links to `#full-notes`
- the public note detail page should mount the shared App Router hash-scroll pattern so direct `#full-notes` visits auto-scroll after the page content mounts

### Copy-and-generate funnel handoff (new-signup path)

When a guest clicks "Create your own Study Pack" on a public note, the intent is preserved through signup via URL query params:

1. Guest clicks CTA → redirected to `/signup?redirectTo=/public/library/{subject}/{slug}?copy=1&intent=generate`
2. After signup, the app returns to the public note page with `?copy=1&intent=generate`
3. `PublicSeoCopyCta` auto-runs `copyNote` then redirects to `/notes/{copiedId}?copied=1&generate=1`
4. The private note detail page reads `generate=1` and calls `handleGenerate`

**Email verification pending state:**

If the new user has not yet verified their email when `handleGenerate` runs:

- do not silently fail — the generate intent would be permanently lost
- set a `pendingPublicCopyGenerate` state flag instead of dropping the intent
- show a dismissible amber banner: `Your note is saved — one step left` with body copy `Verify your email to generate your Study Pack. Once verified, use the button below to start.`
- include a manual `Try again` button that re-invokes `handleGenerate` — do not promise automatic retry, since email verification may happen in a different tab
- if `studysnap-auth-change` fires in the same tab (e.g., the verification link opens in the same browser session), the pending flag triggers an auto-retry via a `useEffect` watching `isEmailVerified && pendingPublicCopyGenerate`
- the banner is dismissible with an `X` button; dismissing clears `pendingPublicCopyGenerate`

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
- obvious mojibake or trust-breaking rendering issues such as broken apostrophes, mangled quotes, or malformed character sequences

Note: formatting improvements apply to how generated content is displayed on public note pages. They do not change the underlying storage format or the authenticated note detail view unless explicitly specified.

## Planned Improvements

Items identified during the May 2026 conversion funnel audit, in priority order.

### C — "Continue learning" block after mini quiz completes (medium effort)

When the guest finishes the Quick Check, show 2–3 related public notes (same subject, exclude current) as compact cards alongside the existing two CTAs. Uses existing subject metadata; no backend change required.

- place the block inside the `PublicMiniQuizPreview` completion card, after the existing CTAs
- limit to 2–3 compact cards to avoid overwhelming the completion moment
- if no related notes exist, show nothing rather than a generic "explore more" link

### D — (resolved) Consolidate auth-prompt patterns

All three guest auth surfaces now use the same dual `Log In` / `Sign Up` AppModal pattern:
- `PublicLibraryCopyAction` → AppModal (unchanged)
- `PublicLibraryLikeAction` → AppModal (unchanged)
- `PublicSeoCopyCta` → AppModal (was: direct push to `/signup` or `/login`, now opens modal)

The `guestAuthMode` prop has been removed from `PublicSeoCopyCta`. Modal title: "Save this note". Redirect URLs carry the copy intent query params so auto-copy fires after auth.

### E — (resolved in docs) Mini quiz preview count

`MAX_PREVIEW_QUESTIONS = 3` in `public-mini-quiz-preview.tsx`. Spec updated to "up to 3" to match implementation.

### F — (resolved) Remove dead code

- `components/notes/public-note-detail-tabbed-content.tsx` — deleted. Was not imported anywhere; superseded by the stacked-card SEO page layout.
- `app/notes/public/[id]/page.tsx` and `app/public/notes/[id]/page.tsx` — kept as backward-compatibility redirect routes. Cannot safely remove without server analytics confirming zero live inbound links (social shares, search-indexed URLs).

### G — Time-decayed Featured score (resolved)

`computeDiscoveryScore` (frontend) and `computeScore` (backend `PublicNotesScoringUtils`) now apply an age-decay factor: `score = baseScore × max(0.1, 1 / (1 + daysSince / 30))`. Notes halve in ranking weight every 30 days; the floor of 10× prevents very old high-engagement notes from fully disappearing. Both frontend and backend accept a `now` parameter for deterministic testing. Tiebreaks (copies desc → views desc → createdAt desc) still apply when decay factors are equal (same-age notes).

### H — "Trending this week" section (blocked — needs backend windowed counts)

`NoteListItemResponse` has no windowed engagement fields (`recentCopyCount`, `recentLikeCount`, etc.). Implementing a true 7-day trending signal requires backend support: either per-event timestamps queryable as a rolling aggregate, or precomputed windowed counts persisted alongside the note. Do not ship under a "Trending this week" label without real windowed data — lifetime totals on recent notes is not the same signal. Revisit when backend adds windowed count fields.

### I — Practice-mode preview teaser on public note detail (resolved)

A non-interactive "Practice modes available once you copy this note" block showing Challenge Quiz, Adaptive Practice, and Board Exam Mode as teaser cards. Implemented as `PublicPracticeModeTeaser` (static server component, no "use client"), placed after Full Notes and before Ownership Actions, gated on `!isDraft`. Board Exam Mode carries a Pro chip; the other two modes are shown as freely available.

### J — Subject landing pages (shipped in v0.14.0)

The `/public/library/[subject]` route is now a server-rendered subject landing page instead of a redirect. It ships with per-subject metadata, CollectionPage structured data, ISR at `300` seconds, and `generateStaticParams()` from `getServerPublicSubjects()`.

Final implementation:

- static, server-rendered discovery page; no client filters or anonymous session state
- subject label resolves from matching note metadata with a slug fallback
- three ranked sections: Featured Notes, Most Popular, Recently Added
- section ordering and deduplication reuse the existing discovery helpers: Featured first, Popular excludes Featured, Recent excludes both
- note cards link to canonical `/public/library/[subject]/[slug]` detail pages
- empty subjects show a simple "No notes yet" state linking back to the Public Library

## Limit-Reached Fallback (planned)

When a user hits their Study Pack limit, the limit modal should offer the Public Library as an alternative path (in addition to the dynamic upgrade CTAs):

- **Title**: "You've reached your limit"
- **Message**: "Explore existing Study Packs while waiting or upgrade for more access."
- **Actions**:
  - `Browse Public Library` — navigates to `/public/library`. If the user's last note has a topic/subject, pre-fill it as a search query (`?search=`).
  - Dynamic upgrade CTA from `getUpgradeCtas(currentPlan)` — navigates to `/settings?section=plans`.

This fallback turns the limit hit into a *content-discovery* moment rather than a hard wall, especially valuable for Free users early in their study journey.
