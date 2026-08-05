# library.md - NoteLib Feature Context

## Goal

Library surfaces make NoteLib a reusable note-first workspace with a clear split between private work and public discovery.

## Key Files

**Backend**
- `backend/src/main/java/com/studysnap/backend/controller/NoteController.java` — `GET /notes` (private list), `POST /notes`, `PUT /notes/{id}`, `DELETE /notes/{id}`, `GET /notes/public` (public filter endpoint)
- `backend/src/main/java/com/studysnap/backend/service/NoteService.java` — `listMine(userId)`, `listPublic(...)`, note CRUD, subject/courseProgram autocomplete queries
- `backend/src/main/java/com/studysnap/backend/repository/NoteRepository.java` — JPQL queries for private and public note lists; subject/courseProgram suggestion queries

**Frontend**
- `frontend/app/library/page.tsx` — private library route; filter state via URL params (`q`, `subject`, `cp`, `tags`, `status`, `sort`); `?ref=` back-navigation encoding
- `frontend/app/library/exam-builder/page.tsx` — Teacher/Admin exam builder; multi-note checkbox selection, section management, DOCX export
- `frontend/components/notes/library-toolbar.tsx` — filter bar: search, subject rail, tags rail, readiness chips, sort
- `frontend/components/notes/library-sheet-modal.tsx` — "More Filters" bottom sheet/modal (Course/Program, additional filters)
- `frontend/components/notes/shared-note-card.tsx` — shared note card layout reused across library, public library, and dashboard
- `frontend/lib/api.ts` — `listNotes()` (private list), `listPublicNotes(params?)`, `createNote()`, `updateNote()`, `deleteNote()`

## Anti-drift Notes

- Private library filter state lives in URL params — do not use `sessionStorage` or local state for filter values (the `?ref=` pattern for back-navigation is a separate concern from filter state)
- The three filter-pruning effects (subject, courseProgram, tags) are gated by `loading` state; they must not run on initial mount before items are fetched (would wipe URL-restored selections)
- `Quiz Ready` filter and badge are **Teacher/Admin only** — hidden for Student and Board Taker library views
- Saved filters (v0.21.0) are backend-persisted in `user_library_filters`; no localStorage fallback; available to all plan tiers

## Library

Route:

- `/library`

Responsibility:

- private workspace for the current user's notes

Library contains:

- `PRIVATE` notes
- `PUBLIC` notes owned by the current user
- Draft and Study Pack Ready notes

Shared note-card layout:

- subtle `courseProgram` line when available
- title
- private-library visibility icon near the title when relevant
- subject badge
- Study Pack status badge
- private Library cards with a ready Study Pack also show a compact scope readout inline in the card's badge row (alongside the Study Pack status badge and, when applicable, the "Quiz Ready" pill — not on its own separate line): non-zero concept count, non-zero quiz-question count, and a rough `~N min` review estimate. It's omitted for DRAFT, GENERATING, FAILED, and empty-pack states — including a note mid-regeneration, whose prior pack's counts stay attached to the response even though status has flipped away from ready; public/discovery Library cards do not show it.
- `Note Preview`
- `Summary Preview`
- tags
- subtle discovery metrics row (`views`, `copies`) when a library-style surface exposes them

Interaction:

- whole card opens Note Detail
- note cards stay preview/navigation only
- note actions belong in Note Detail
- Teacher/Admin Library adds a teacher-only `Select` mode instead of changing default card behavior:
  - each note shows a checkbox while selection mode is active
  - the selection toolbar (shared by plan creation and teacher exam selection) has a `Select all` / `Deselect all` toggle scoped to the active filters; it resolves the complete matching id set through `GET /notes/library/ids` instead of selecting only the loaded page, with a stable 1,000-id cap and an explicit toast when the result is truncated. Deselect-all clears that resolved filtered set. Because selected ids can extend beyond loaded note cards, the teacher exam flow labels its quiz-ready count as covering only the selected notes currently loaded and suggests loading more or narrowing the filters when some selections are unresolved (v0.51.0).
  - only notes with a stored `generatedQuiz` can be selected for exam export
  - non-quiz-ready notes stay visible but show a disabled checkbox plus `Generate a quiz first` guidance
  - selected notes open `Exam Builder`, where teachers can:
    - start from `Start Blank` with one `Untitled section`, or choose a structured template
    - organize notes into editable sections such as `Section A`, `Section B`, and `Section C`
    - reorder sections with a drag handle
    - drag notes within a section or move notes across sections
    - rebalance pooled quiz questions with either:
      - `Even Balance` to spread questions equally across all sections
      - `Smart Balance` to balance question counts and spread topic diversity across sections, using each section's learning intent as a guide
    - review a per-section question-count breakdown in the footer before export
    - keep `Move up` / `Move down` note controls as the accessibility fallback
    - remove notes or delete sections before export
    - export one combined DOCX exam that preserves section order and exact question grouping

Shared list controls:

- order is always `Search`, `Filter`, `Sort`, then notes list
- the `Search` field shows an inline clear (`×`) button when it has text, mirroring the combobox clear affordance; clicking it runs the normal search-change path (debounced server filtering and URL sync for the private library, debounced URL sync for the public library), not just a textbox reset. It is distinct from the filter panel's `Clear all`, which resets every active filter. Search inputs use a 16px font on mobile to avoid iOS Safari focus-zoom.
- on mobile, `Filter` and `Sort` open shared bottom-sheet/modal controls instead of staying always visible

Private Library filters:

- `Course / Program`
- `Subject`
- `Tags`
- `Draft`
- `Study Pack Ready`
- `Quiz Ready` only for Teacher profile browsing and exam-export preparation
- `Public`
- `Private`

Private Library filter presentation:

- `Subject`, `Tags`, and readiness filters use horizontal scroll rails with a right-edge fade affordance.
- overflow actions use `Browse all` text links instead of chip-styled `+ More` controls.
- `Course / Program` is available from the `More Filters` sheet as a single-select filter with search.
- selecting a course/program shows an active dot on `More Filters` and an active summary badge.

Readiness visibility:

- The library Filter readiness row is ordered `All`, `Draft`, `Quiz Ready`, `Study Pack Ready`; `Quiz Ready` appears only for Teacher/Admin contexts.
- `Draft` matches notes with `studyPackStatus = DRAFT` so users can find saved notes that have not generated a Study Pack yet.
- `Study Pack Ready` is the learner-facing readiness signal and remains visible for Student, Board Taker, and Teacher profiles.
- `Quiz Ready` is a Teacher/exam-export workflow signal. Hide the `Quiz Ready` badge and filter for Student and Board Taker Library browsing.
- If the active profile changes while `Quiz Ready` is selected, reset the hidden filter instead of leaving users with an invisible active filter.
- Public Library does not expose `Quiz Ready` because public discovery should focus on notes, summaries, Quick Check, and copy/share flows.
- Exam Builder still uses generated-quiz data internally for note selection, question counts, disabled states, and DOCX export.

Private Library filter persistence:

- Filter state is reflected in URL query params (`q`, `subject`, `cp`, `tags`, `status`, `sort`); state is initialized from the URL on mount so deep links and browser back/forward work correctly.
- When navigating from a note card to Note Detail, the current filter URL is encoded as a `?ref=` param on the note URL (e.g. `/notes/{id}?from=library&ref=%2Flibrary%3Ftags%3DReview`). The note detail "← Library" back link reads `ref` to restore the exact filtered state.
- The `?ref=` approach is used instead of `sessionStorage` because Next.js Router Cache keys by URL; sessionStorage state would be restored from cache when revisiting the same note within the cache TTL, breaking the back link for different filter contexts.
- Subject, course/program, and tag pruning uses the whole-library `GET /notes/library/filter-options` response rather than the current page. The effects wait for a successful options response, so URL-restored filters survive initial loading and an options-endpoint failure does not silently clear them.

Private Library sorting:

- `Recently Updated`
- `Recently Reviewed`
- `Newest`
- `Title (A-Z)`
- `Title (Z-A)`
- `Oldest`

Private Library pagination and filtering:

- initial load requests page 0 with 20 notes from `GET /notes/library`; search, filter, and sort changes use the existing 400ms URL-sync debounce to replace that page with fresh server-filtered results
- `Load more` requests and appends the next server page; note objects are not re-filtered, re-sorted, or sliced from an unbounded browser array
- the lean, unfiltered `GET /notes/status` poll remains the discovery signal for newly materialized bulk rows; when it detects growth or a generating-status resolution, the enriched refresh is limited to the currently loaded filtered window, capped at 100 notes
- the summary line reports the backend `totalMatching` value and labels it as matching the active filters when applicable

### Stats Strip

The private Library shows a compact subject stats strip inside the filter card, between the filter controls and the note list.

The strip is **faceted** over notes matching every active filter **except** subject. The frontend reads it from `GET /notes/library/subject-stats` alongside page-0 filter refreshes; the endpoint returns the top 6 subjects, the summed remainder, and the filtered total. Its subject buckets use the same normalized subject → course/program → `General` fallback as the Library filter, so the browser no longer needs the full note array to compute the strip.

Behavior:

- subject key uses the same `getLibrarySubject` resolution as the rest of the Library (subject → course/program → fallback), so chip clicks always match the subject filter
- `topSubjects`: distinct subjects in the filtered set, sorted by count descending, capped at 6
- `Other {n}`: summed count for subjects beyond the top 6 (non-clickable)
- show only when the filtered set has `>= 5` notes and `>= 2` distinct subjects
- hide while the Library is loading
- hide when a subject filter is already active
- render one clickable chip per top subject as `{subject} {count}`; clicking applies the existing private Library `subject` URL param

## Saved Filters

Users can save a named snapshot of the current private library filter state and re-apply it with one click.

Storage: backend-persisted in a `user_library_filters` table (`id`, `user_id`, `name`, `filter_state` JSONB, `created_at`).

`filter_state` stores: `{ search?, subject?, courseProgram?, tags?, status?, sort? }` — the same params as the private library URL model.

Interaction:

- "Save filter" button is visible in the filter bar when at least one filter is active
- Clicking opens a name input dialog; submitting saves to the backend via `POST /library-filters`
- Saved filters are accessible from a dropdown or list in the filter bar; clicking one applies all its params to the current filter state
- Each saved filter row has a trash/delete icon; deleting calls `DELETE /library-filters/{id}`
- `GET /library-filters` loads all saved filters for the current user on mount

Rules:

- Available to all plan tiers (no gating for v1)
- Scope is private library only; public library saved filters are deferred
- Saved filter names have a max length of 100 characters
- Applying a saved filter replaces all current filter params with the saved state (does not merge)

## Public Library

Route:

- `/public/library` as the canonical Public Library route in both authenticated and anonymous flows
- canonical public discovery route is `/public/library`

Responsibility:

- discovery of public notes from you, other creators, and official NoteLib content

Public Library cards use the same shared preview layout and whole-card interaction as Library.

Public Library controls:

- keep the same `Search`, `Filter`, `Sort`, notes-list structure as Library
- use the same mobile filter/sort sheet behavior as Library

Public Library filters:

- `Course / Program`
- `Learner Level`
- `Subject`
- `Tags`
- `By You`
- `Official`
- `Community`

Public Library filter presentation:

- `Course / Program` is an inline chip row between `Subject` and `Tags`; it uses the same URL-synced `courseProgram` query param as the public library filter state.
- public filter rails use a right-edge fade affordance and `Browse all` text links for overflow.
- `More Filters` is reserved for source filters (`By You`, `Official`, `Community`).

Public Library search responsiveness:

- Search is backend-driven (the corpus is server-paginated, so the full result set cannot be filtered client-side). Typing debounces ~250ms, then syncs the term to the URL, which drives the fetch.
- The fetch is **stale-while-revalidate**: the full skeleton only shows on the *initial* load (`loading && !hasLoadedOnce`). On every subsequent search/filter refetch the previous results stay mounted — the search box keeps focus and the list does not collapse to skeletons — with a small `Searching…` indicator next to the result count. A refetch failure that still has stale results shows an inline `Couldn't refresh results` note instead of replacing the page with the error card.
- Already-loaded items are also narrowed client-side by the live query (`filteredItems`) for instant feedback while the authoritative backend result is in flight. The URL sync is retained because it powers refresh-persistence, deep-linking, and sharing a pre-filtered view — not only sharing.
- The search input hydrates from the URL only on genuine external changes (initial load, back/forward), keyed on the search term alone and guarded against the echo of the component's own debounced write (`lastSyncedSearchRef`). It must **not** be reset from the URL when a refetch resolves (which changes the available subject/tag lists) — doing so clobbers characters typed after the debounce fired and drops keystrokes during fast typing.

Public Library filter persistence:

- Filter state is reflected in URL query params (`view`, `search`, `subject`, `tag`, `audience`, `courseProgram`, `sort`); state is initialized from the URL on mount.
- When the user explicitly selects "All" for the audience filter (clearing the profile pre-filter), `?audience=all` is written to the URL. This prevents the profile default audience from re-applying on re-mount or back-navigation.
- When navigating from a note card to a public note detail, the current public library URL is saved to `sessionStorage` under `notelib_public_library_return_url`. The `PublicLibraryBackLink` component reads this on mount and uses it as the back link href, restoring the exact filtered state.
- `sessionStorage` is used (instead of `?ref=`) for public notes because public note URLs are canonical SEO slugs that must not be polluted with navigation state params.

Growth behavior:

- Public Library should encourage copying and studying, not only browsing.
- Discovery sorting should help users find useful community notes faster:
  - `Newest`
  - `Most Copied`
  - `Most Viewed`
  - `Title A-Z`

Metadata behavior shared with note authoring:

- `Course / Program` is the high-level shelf used to group notes by track/domain, while `Subject` remains the more specific academic topic.
- Library course/program filters and facets are join-first: a note matches and counts for every catalog name in `note_course_program`. Only a note with **no** join rows falls back to its persisted `notes.courseProgram` string. The zero-row guard is intentional—a curated set that excludes the authored legacy string must not continue matching that string.
- Facets use `UNION ALL` over mutually exclusive joined and zero-row legacy populations. A multi-program note therefore contributes once to each applicable program, so course/program facet counts can correctly sum above the note total; the More Filters panel states this explicitly.
- Library cards receive ordered Applicable Program names in the paginated list projection and never issue a per-note applicability request. **Cards state reach as a summary, not a list:** one program is named, several render as `Applies to N programs`, and the names themselves live on Note Detail. Notes with no join rows summarise the legacy string the same way.
- **The card's identity row is the Subject badge alone, above the title.** Program names were removed from that row deliberately: beside the Subject badge they read as a second identity, adjacent names had no delimiter (`Accountancy Architecture` scanned as one value), and in a program-filtered view most of the listed names are irrelevant to what the learner just asked for. Any truncated list also has to drop names on alphabetical accident. Do not restore per-name rendering to the card.
- `GET /course-programs?scope=mine` unions joined catalog names with the existing note-derived legacy strings and profile value, then applies the existing normalization and ordering. Because the Note Editor and Note Detail also consume this list, join-only catalog programs intentionally become legacy Course / Program authoring suggestions too; the endpoint is not split into separate discovery and authoring lists.
- When a user saves a custom course/program on a note or in their profile, that value remains available in later course/program suggestions for authenticated authoring flows.
- Equivalent course/program variants should collapse through shared normalization so filters do not split labels only because of case or dash formatting.
- Library subject filters depend on the same persisted `notes.subject` values used by the Note Editor autocomplete.
- When a user saves a custom subject on a note, that subject becomes available in later `Subject` suggestions and filters.
- Equivalent subject variants should collapse through shared normalization so filters do not split labels only because of case or dash formatting.
- AI-generated subjects should stay reusable academic labels; overly broad AI suggestions such as `Engineering`, `Medicine`, `Business`, and `Law` are ignored safely instead of being saved as Library filters.
- Notes now persist optional per-note `courseProgram` metadata so future library filters can group notes more accurately without relying only on the user's profile default.

## Public Profile

Public Profile is not a private library surface.

- route: `/public/profile/{userId}`
- purpose: public showcase only
- note cards reuse the same shared layout
- note cards stay action-free and open the canonical public note route
