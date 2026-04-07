# public-library.md - Testing Notes

## Discovery sections (covered in `PublicLibraryPageClient` component tests)

- 🔥 Featured Notes section appears when no search, filter, or sort change is active
- 📈 Most Popular section appears when notes remain after Featured deduplication
- 🆕 Recently Added section appears when notes remain after Featured + Popular deduplication
- 📚 Browse by Subject chip row appears when any public note has a non-null subject
- Sections are hidden when a search query is entered, any filter is selected, or sort changes from Newest
- Clicking a subject chip sets the Subject filter and switches to filter mode (sections hidden)
- Most Popular and Recently Added are not shown when all notes fit within the Featured section limit (≤ 6 notes)

## Ranking utility (covered in `public-library-discovery.test.ts`)

- `computeDiscoveryScore` weights copies (0.5), views (0.4), shares (0.1); treats null counts as 0
- `getFeaturedNotes` sorts by score descending; tiebreaks by newest createdAt
- `getFeaturedNotes` limits output to DISCOVERY_SECTION_LIMIT (6)
- `getPopularNotes` sorts by copy count, then view count, then newest; respects limit
- `getRecentNotes` sorts by createdAt descending; respects limit
- `getBrowseSubjects` returns normalized unique subjects sorted by note count desc then alphabetically
- `getBrowseSubjects` normalizes dash formatting ("Biology-Cell Division" → "Biology – Cell Division")
- `getBrowseSubjects` excludes notes with null or blank subjects
- `getBrowseSubjects` limits to BROWSE_SUBJECTS_LIMIT (12)
- `excludeById` removes specified notes by id; no-ops on empty inputs

## Existing filter/sort behavior

- Sort: Newest, Most Copied, Most Viewed, Title A-Z (from the sort sheet)
- Filters: Course/Program, Learner Level, Subject, Tags, Source (By You / Official / Community)
- Changing sort or applying any filter switches from discovery mode to filter mode
- Active filter pills shown in toolbar; "Clear all" resets all filters and returns to discovery mode
- Empty state shown when filter mode produces no matches

## Note cards

- Whole card is clickable (navigates to public note detail)
- No action buttons inside cards
- Views and copies shown when > 0
- Author badge: "By You" (green) / "By NoteLib" with Official chip (blue) / community author (muted)
- Official badge visible only for isOfficialAuthor=true notes

## Quality badges (covered in `note-quality-badges.test.ts`)

- ⭐ **High Quality**: shown when `copyCount >= 5 AND viewCount >= 10`
- 🔥 **Popular**: shown when `copyCount >= 10 OR viewCount >= 20`; suppressed when High Quality is already shown
- 🆕 **New**: shown when note was created within the last 7 days
- At most 2 badges are shown per card — High Quality or Popular occupies the first slot; New may occupy the second
- Notes with zero counts show no quality badges
- Notes with null or undefined counts are treated the same as zero
- Quality badges appear on: Public Library, Public Profile, public subject pages
- Quality badges do NOT appear on private Library cards (no public engagement metrics)
- Badge conditions tested: zero counts, null counts, threshold boundaries (exact and ± 1), combinations, max-2 cap, label correctness

## Mobile

- Filter and Sort open bottom-sheet modals, not inline dropdowns
- Card grid switches from 2 columns (desktop) to single column (mobile)
- Browse by Subject chips wrap and remain tappable on narrow screens
