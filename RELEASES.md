# RELEASES.md - NoteLib

## v0.9.0 - Learning Experience & Product Polish (In Progress)

### New Features

- **Dedicated How it Works page** — product walkthrough content now has its own public route at `/how-it-works`:
  - the new page explains the full NoteLib flow with real screenshots for note editing, Study Pack generation, quiz practice, and results review
  - `/how-it-works` also includes the simple 3-step overview, Board Exam Mode highlight, and a closing signup CTA
  - the walkthrough reuses the shared optimized screenshots from `public/landing`
- **Landing page screenshot integration** — the public homepage now shows real product UI instead of abstract product illustrations:
  - hero now uses the real note-editor screenshot to ground the product story immediately
  - the homepage walkthrough has been simplified so the detailed multi-screenshot explanation now lives on `/how-it-works`
  - landing screenshots now share one polished treatment: rounded corners, soft shadows, preserved aspect ratios, and subtle hover scale
  - the public navbar and footer now surface `How it Works` so deeper product guidance is easier to find
- **Theme system refresh** — NoteLib now supports `Light`, `Dark`, and `System` theme modes as the first polish feature of `v0.9.0`:
  - `Settings > Preferences` now includes a dedicated theme selector instead of relying only on a utility toggle
  - `Settings` keeps the always-visible inline `Light` / `Dark` / `System` segmented selector for fast preference changes
  - the shared top-bar theme control now uses a simpler responsive inline pattern:
    - desktop shows a compact always-visible icon-only theme group with tooltips
    - mobile keeps a compact trigger that expands an inline theme panel
  - the top-bar `System` option now uses a monitor-style icon on desktop and a phone-style icon on mobile
  - the mobile top-bar theme control no longer relies on the unstable popup rendering path and now expands cleanly without clipping into the header area
  - the desktop top-bar theme control is now more compact, with tighter icon sizing and spacing while keeping the same theme actions
  - the public header now uses a subtle separator between the theme utility group and the `Login` / `Get Started` actions
  - `System` follows the device `prefers-color-scheme` setting and updates while the app is open
  - theme choice persists locally and also syncs through the existing authenticated theme-preference API when available
  - initial theme classes are applied before the main UI renders to avoid flashing the wrong theme on load
  - theme changes now use subtle color-only transitions for background, text, and borders
- **Motion system foundation** — NoteLib now uses a tighter shared motion language for polish without slowing study flows:
  - shared motion tokens now live in the global CSS layer so durations and easing stay consistent
  - cards and shared buttons now use lightweight surface and pressed-state motion utilities instead of one-off transition values
  - the Challenge Quiz Question Navigator now uses a shared collapse/expand motion pattern instead of abrupt mount/unmount behavior
  - Quick Review and Challenge / Board Exam result-review surfaces now use gentle fade-in entry motion for calmer state changes
  - quiz-critical interactions such as answer selection, timer updates, and question progression intentionally avoid extra motion so focus and responsiveness stay intact
- **Review Answers UX polish** — Challenge Quiz review is now clearer and more learning-focused without changing scoring or quiz generation:
  - Review Answers now starts with a compact summary for correct count, total questions, percentage, performance level, and weak concepts
  - each reviewed question now shows explicit `Correct` / `Incorrect` state plus `Your Answer` and `Correct Answer` summaries before the choice list
  - answer review now supports `All Questions` / `Incorrect Only` filtering alongside per-question explanation toggles and shared `Expand All` / `Collapse All` controls
  - explanation disclosure uses the shared lightweight motion system to avoid abrupt layout jumps
  - Challenge Quiz review now ends with clearer next-step actions such as `Practice Weak Concepts` and `Review Study Pack`
- **Local quiz-generation mock mode** — local development can now exercise quiz UIs without burning real LLM tokens:
  - `QUIZ_GENERATION_MODE=mock` stubs only Challenge Quiz, Adaptive Practice, and Board Exam generation while leaving Study Pack generation on the normal provider path
  - mock mode still preserves normal quiz session creation, `GENERATING` / `IN_PROGRESS` flow, idempotent reuse, result handling, and review-answer compatibility
  - `QUIZ_GENERATION_MOCK_DELAY_MS` can add a small local-only delay to test generation overlays and loading states more realistically
  - production remains on the real quiz-generation path unless the quiz-specific mock flag is explicitly overridden

### Fixes

- **Public Library copy-flow cleanup** — public note copying now behaves consistently across discovery and detail surfaces:
  - Public Library cards now use `Copy to My Library` directly and switch to `Already in your library` plus optional `View Note` when that source note was already copied
  - repeated copies of the same public note by the same user now reuse the existing copied note instead of creating duplicate drafts
  - successful public copies now use a shorter action hierarchy: `Continue`, `View Note`, and primary `Start Review`
  - copied private notes now show `Copied from {title} in Public Library.` attribution when source metadata exists
- **Private Note Detail action cleanup** — secondary note-management actions no longer compete with study actions inside the note header:
  - inline `Edit`, `Delete`, `Make a Copy`, and `Share` controls were consolidated into a single top-right `⋯` context menu
  - the `⋯` trigger is now anchored to the card corner on both mobile and desktop instead of sitting inside the metadata flow
  - the note detail card now keeps primary study actions visually dominant while still exposing full note management
  - the shared menu works on both mobile and desktop, closes on outside click, and avoids the old multi-row utility-button clutter
- **Feedback launcher sticky-CTA conflict fix** — the floating `Send Feedback` button is now hidden on routes with sticky or fixed bottom primary actions, including Note Editor and in-progress quiz flows, so it no longer overlaps `Generate`, `Next`, `Submit`, or similar bottom CTAs on mobile.
- **Feedback UX cleanup for core study flows** — feedback entry is now more intentional across learning surfaces:
  - core authenticated learning routes now use a subtle header feedback icon instead of the floating launcher
  - the floating launcher remains only on safe non-critical authenticated pages such as Dashboard, Library, Public Library, and Settings
  - quiz result screens now ask `Was this quiz helpful?` with lightweight `Yes` and `Give Feedback` actions before the deeper review feedback panel
- **Mobile note header overflow fix** — long private note titles no longer push `Edit` / `Delete` outside the header card on small screens:
  - mobile Note Detail now stacks the title above the action row
  - `Edit` and `Delete` stay inline again from `sm` upward so desktop layout remains unchanged

## v0.8.0 – Board Exam Mode + Public Library Discovery System (In Progress)

### New Features

- **Pricing page + Premium positioning** — `/pricing` now presents a cleaner two-plan comparison for the current pre-launch stage:
  - Free and Premium are the only visible plans
  - Free highlights `10` Study Packs/month, `5` Challenge Quizzes/month, AI Summary + Key Concepts, Weak Concepts tracking, and `Board Exam Mode (Free for limited time)`
  - Premium highlights higher limits, Adaptive Practice, Difficulty selection, and Board Exam Mode
  - the comparison table now focuses on the core study features users actually choose between
  - upgrade CTAs still route into the Premium waitlist flow instead of payment
- **Post-Quiz UX Polish** — Unified quiz result UX across Quick Review, Challenge Quiz, and Adaptive Practice:
  - Removed all "Note" buttons from quiz screens; replaced with `← Back to Note` text link placed **below** action buttons (not grouped with them)
  - Quick Review confidence feedback: selecting a confidence level now replaces the option buttons with a styled badge — `🟢 Confident` (HIGH), `🟡 Improving` (MEDIUM), `🔴 Needs Practice` (LOW); "Thanks for the feedback." text removed
  - Adaptive Practice result screen: "Generate New Set" is now the primary action; "Note" button removed
  - Challenge Quiz result screen: "Practice Weak Concepts" is now primary (when present); "Start Another Challenge" and "Review Answers" are secondary; "Note" button removed
  - Review Answers now uses a shared learning-focused layout across Quick Review, Challenge Quiz, and Adaptive Practice with selected/correct answer badges, concept chips, visible explanations, Previous/Next navigation, and an `Incorrect only` filter for missed questions
  - Final result-flow alignment pass: Quick Review now promotes `Practice Again` when weak practice is locked, Challenge Quiz promotes `Start Another Challenge` when no weak concepts exist, Adaptive Practice shows a clear empty targeted-weak-areas state, and quiz edge states use text-link `← Back to Note` navigation instead of navigation buttons
  - Adaptive Practice `completionMessage` upgraded to use `mapPerformanceLevel` 4-tier thresholds (Excellent / Good / Fair / Needs Improvement) instead of a 2-tier check
  - Error cards in Adaptive Practice (error, premiumLocked, prestart) no longer have a redundant "Note" button — the persistent `← Note` BackLink at the page header handles navigation
- **Board Exam Mode (Phase 1)** — Challenge Quiz is now a true exam experience:
  - Challenge Quiz now opens with explicit mode selection between `Challenge Quiz` and `Board Exam Mode` instead of auto-starting or inferring exam mode from Premium-only capabilities
  - Challenge Quiz entry is now split into `Mode Selection` then `Prescreen`, so both `Challenge Quiz` and `Board Exam Mode` explain their setup before generation starts
  - `Challenge Quiz Setup` now shows timer, question-count, and attempt-usage summaries for all users; Premium users get live difficulty controls, while Free users see a recommended `Medium` difficulty plus subtle Premium upsell copy
  - Board Exam Mode is available on both Free and Premium plans and uses the same Challenge Quiz credit/quota rules during the current rollout stage
  - Board Exam Mode now has a formal `Board Exam Setup` prescreen with exam description, strict-timer summary, rule summary, `Cancel`, `Start Exam`, and best-effort fullscreen focus entry
  - Board Exam Mode now explains its distraction-free restrictions before the exam starts, confirms start explicitly, reinforces `Exam in progress` during the session, and uses more formal result framing so hidden navigation does not feel like a bug
  - Board Exam Mode no longer shows difficulty selection in the UI and now always uses mixed difficulty with the fixed exam question count
  - No correctness feedback during answering — answer first, see results later
  - Board Exam answering UI now uses a more neutral, formal presentation than the standard Challenge Quiz screen
  - Board Exam timer is now hardened around persisted session timing, low-time warning states, refresh-safe recovery, and one-shot timeout submission
  - Timer resumes from persisted session state after refresh and auto-submits when time runs out; manual submit remains available from the last question
  - Neutral question-number navigation lets users move through the exam without revealing correctness
  - Result screen keeps shared recovery actions and Review Answers, but now uses more formal Board Exam framing plus `Take Another Board Exam`
  - "Practice Weak Concepts" CTA (→ Adaptive Practice) shown only when weak concepts exist
  - All result statistics are derived from session data only — no LLM calls
- Public note cards in the Public Library, Public Profile, and public subject pages now show **quality indicator badges** (at most 2 per card) to help users quickly identify strong notes:
  - ⭐ **High Quality** — `copyCount >= 5 AND viewCount >= 10`
  - 🔥 **Popular** — `copyCount >= 10 OR viewCount >= 20` (shown only when High Quality is not already displayed)
  - Badges appear below the title; layout is mobile-safe and capped at 2 to avoid clutter. Private Library cards never show quality badges. The "New" badge has been removed.
- **Note card badge layout standardized** across all note-list surfaces (Library, Public Library, Public Profile, subject pages):
  - TOP ROW (above title): Subject badge (blue) + Course/Program badge (neutral/gray)
  - TITLE
  - BELOW TITLE: Study Pack Ready badge (green, only when applicable) + quality badges
  - `SharedNoteCard` props updated: `metaLine` removed, `courseProgram` (string) and `stateBadge` (ReactNode) added
- Public Library now has a **discovery mode** that replaces the flat note list with curated sections when no search or filters are active:
  - 🔥 **Featured Notes** — top 6 notes ranked by a weighted engagement score: `(views × 0.4) + (copies × 0.5) + (shares × 0.1)`. Tiebreak by newest first.
  - 📈 **Most Popular** — top 6 notes by copy count then view count, excluding notes already in Featured.
  - 🆕 **Recently Added** — top 6 newest notes, excluding notes already in Featured or Most Popular.
  - 📚 **Browse by Subject** — clickable chips of all unique subjects sorted by note count. Clicking a chip applies the subject filter and switches to filter mode.
- Sections are deduplication-safe: each note appears in at most one section per page load.
- Discovery mode is automatically hidden when the user types a search query, changes any filter, or selects a non-default sort option — switching seamlessly to the existing filter/sort list.

### Technical Changes

- Quick Review, Challenge Quiz, and Adaptive Practice test suites extended with post-quiz UX tests: no "Note" button on result screens, "← Back to Note" link present, confidence badge rendering (HIGH/MEDIUM/LOW), confidence option buttons hidden after selection, "Generate New Set" as primary on Adaptive Practice result, "Start Challenge Quiz" CTA on perfect Quick Review score, and Review Answers coverage across all quiz modes.
- Added `frontend/components/study-pack/quiz-answer-review.tsx` as the shared Review Answers surface for selected-vs-correct answer states, concept linking, visible explanations, and sequential review navigation. Covered by component tests plus Quick Review, Challenge Quiz, and Adaptive Practice page integration tests.
- Added a shared active quiz session guard for Quick Review, Challenge Quiz, and Adaptive Practice. Active sessions now block app route clicks, browser back navigation, and refresh/reload attempts with a shared `Leave quiz?` confirmation before users can forfeit and leave.
- Added explicit quiz session forfeits with `FORFEITED` status for Quick Review, Challenge Quiz, and Adaptive Practice. Challenge Quiz and Adaptive Practice forfeits do not refund consumed quiz credits and are not marked completed.
- Centralized quiz choice prefix cleanup so generated and legacy payloads strip hardcoded leading labels such as `A. ` and `B) ` before validation/storage; the frontend also strips legacy prefixes defensively before rendering dynamic choice letters.
- Study Pack generation from notes now runs asynchronously: Note Editor saves first and redirects immediately to Note Detail, which shows `GENERATING`, polls lightly until `STUDY_PACK_READY` or `FAILED`, and exposes `Retry Generate` without consuming quota on failed attempts.
- Challenge Quiz start now locks difficulty controls and the Start button immediately to prevent duplicate start requests or difficulty changes while initialization is in flight.
- Quick Review reload/start is now guarded against repeated fetch/redirect initialization loops without adding any LLM-specific lock behavior.
- Challenge Quiz and Adaptive Practice now reserve `GENERATING` sessions before LLM execution, return existing `GENERATING`/`IN_PROGRESS` sessions idempotently, and allow retry after `FAILED` without duplicate LLM calls from double-clicks, refreshes, or multiple tabs.
- Challenge Quiz and Adaptive Practice now show a full-screen `Generating your quiz...` lock during LLM generation, blocking app links, sidebar/header navigation, browser back, and refresh/reload until the session becomes `IN_PROGRESS` or `FAILED`.
- Enhanced quiz generation loading UX for Challenge Quiz and Adaptive Practice with pulsing AI-style dots, clearer personalized-question copy, and calm rotating progress messages while preserving the strict interaction lock.
- Adaptive Practice now checks existing session state on page load and starts new LLM generation only from the visible `Start Adaptive Practice` / `Generate New Set` actions.
- Added `frontend/lib/challenge-quiz-results.ts` with pure result computation utilities: `computeScore`, `mapPerformanceLevel`, `computeConceptBreakdown`, `computeWeakConcepts`, and exported `WEAK_CONCEPT_THRESHOLD = 60`. Challenge Quiz page now uses `computeScore` in `handleSubmit` instead of an inline reduce. Covered by 31 unit tests in `challenge-quiz-results.test.ts` (all-correct, all-wrong, mixed, unanswered, single-question, empty quiz, all 8 performance level boundary values, concept grouping, Unknown fallback, alphabetical sort, weak concept threshold edge cases, end-to-end integration scenarios).
- Added `frontend/lib/note-quality-badges.ts` with `computeQualityBadges` and exported `QUALITY_THRESHOLDS` constants. Added `frontend/components/notes/note-quality-badge.tsx` with the `NoteQualityBadges` component. Covered by 12 unit tests in `note-quality-badges.test.ts` (zero counts, null/undefined, threshold boundaries, High Quality suppresses Popular, label correctness). "New" badge removed — `createdAt` param and `NEW_WITHIN_DAYS` constant removed.
- Refactored `SharedNoteCard`: replaced `metaLine?: ReactNode` with `courseProgram?: string | null` (renders neutral gray badge above title) and added `stateBadge?: ReactNode` (renders Study Pack Ready badge below title). Updated all 4 callers: `public-library-page-client.tsx`, `app/library/page.tsx`, `public-profile-page-client.tsx`, `app/public/library/[subject]/page.tsx`.
- Added `frontend/lib/public-library-discovery.ts` with pure utility functions: `computeDiscoveryScore`, `getFeaturedNotes`, `getPopularNotes`, `getRecentNotes`, `getBrowseSubjects`, `excludeById`. All ranking is client-side using existing data from `listPublicNotes()` — no new backend endpoints required.
- Extracted `PublicNoteCard` sub-component inside `public-library-page-client.tsx` to share card rendering between discovery sections and the main filtered list.
- Added 28 unit tests for discovery utility functions and 5 integration tests for discovery UI behavior in `PublicLibraryPageClient`.
- Added backend scoring support via `GET /notes/public?sort=featured|popular|recent`. Sort is computed dynamically from existing engagement signals — no DB persistence. `featured` uses score `(copies × 0.6) + (views × 0.4)` with newest-first tiebreak; `popular` uses copy count → view count → newest-first; `recent` uses `createdAt` desc. Unknown or missing sort values fall through to the default DB order.
- Added `backend/util/PublicNotesScoringUtils.java` with `computeScore`, `sortByFeatured`, `sortByPopular`, and `sortByRecent`. Covered by 16 unit tests including scoring formula, sort ordering, null-count handling, tiebreaks, and empty dataset cases. Added 7 sort-specific tests to `NoteServiceTest`.
- Added backend subject filtering: `GET /notes/public?subject=<value>` returns only notes whose normalized subject matches (case-insensitive). Applied after fetch, before sort. Frontend `listPublicNotes()` accepts optional `{ subject }` param. Covered by `NoteServiceTest.listPublic_withSubjectFilter_returnsOnlyMatchingNotes`.
- Refactored LLM subject and key-concept sanitization into dedicated utility classes: `SubjectSanitizer` (max 6 words, overly-broad detection, course-program echo detection) and `KeyConceptSanitizer` (max 4 words per concept, filler-prefix stripping). Removed inline private methods from `OpenAiLlmStudyPackService`. Covered by new `SubjectSanitizerTest` and `KeyConceptSanitizerTest` unit test classes.
- Key-concept sanitization now applies to the `keyConcepts` list (not just quiz `concept` fields): overlong concepts are repaired in-place and hard-truncated as a last resort — study pack creation is never blocked by word-count alone.
- Updated LLM subject prompt to use the 3-tier subject strategy: Specific Subject (preferred) > Primary–Subtopic (when context needed) > General Subject (fallback).
- Browse by Subject section repositioned to appear ABOVE Featured/Popular/Recently Added sections in discovery mode. Limit reduced from 12 to 8 subjects.
- **Subject metadata cleanup**: subjects are now domain-only (e.g., `Biology`, `Physics`, `Engineering`). Any combined domain-topic value (`Biology – Cell Division`, `Physics: Ohm's Law`) is automatically stripped to the domain part before saving. Broad single-word domains (`Engineering`, `Medicine`, `Law`, `Business`, `Education`) are now valid and no longer trigger a retry. LLM prompt and subject guidance block updated to request domain-only subjects. Added `SubjectSanitizer.stripSubtopicSuffix` utility method. Covered by new `stripSubtopicSuffix` tests in `SubjectSanitizerTest` and updated `OpenAiLlmStudyPackServiceTest` subject edge cases.

## v0.7.0 - Learning & Metadata Foundation (In Progress)

### New Features

- User profiles now support `Learner Level` plus required `Course / Program` on Learning Profile saves as part of the learning-profile foundation.
- Onboarding now includes a dedicated `Learning Profile` step that collects required learner level, required course/program, and optional bio.
- Notes now support optional per-note `Course / Program`, defaulted from the user's profile and editable per note.
- Note metadata suggestions now use a shared field-level AI review modal for `title`, `subject`, and `tags`.

### Improvements

- Public Profile and Private Profile are now clearly separated in both navigation and purpose. Public Profile (`/public/profile/{userId}`) is the user's shareable learning-portfolio surface. Profile Settings (`/profile`) is the private account editing surface, accessed via `Edit Profile`.
- The avatar dropdown now uses clear, consistent labels: `My Profile` (→ public profile), `Settings` (→ `/settings`), and `Sign Out`. The sidebar Account section uses the same model: `Profile` (→ public profile) and `Settings` (→ `/settings`).
- Terminology is now consistent: **Profile = public identity page. Settings = account/app settings.**
- Share Profile now uses the same modal pattern as note sharing: a modal with title `Share this profile`, a labeled `Shareable URL` field, and `Copy Link` + `Close` buttons. The previous toast/inline-text-only behavior is replaced.
- If a profile is private, clicking Share Profile opens a confirm modal (`This profile is private`) that offers `Make Public & Share` — the same gate used for private note sharing.
- Share behavior is now consistent across notes and profiles: public content opens the share modal directly; private content requires owner confirmation before the share modal appears.
- Private profile confirm message updated to include `and notes` so users understand what becomes visible.
- Private Profile now separates `Identity`, `Learning Profile`, and `Profile Type` into distinct saveable cards.
- Public Profile can now show learner level and course/program when the owner chooses to provide them.
- Public Profile now feels more like a learning portfolio, with compact real metrics for public notes, copies, shares, and views when available.
- Public Profile now derives lightweight learning-focus text from real public-note metadata and can highlight a featured note when usage data exists.
- Learner-level and course/program inputs now reuse the same subject-style combobox UX as the Note Editor `Subject` field.
- Fixed-option learner-level comboboxes now snap back to the last valid saved value if a user types an unsupported option and closes the field.
- Note Editor now includes `Course / Program`, subject autocomplete, optional tags guidance, and the same metadata shape in both create and edit modes.
- Course / Program now behaves like a reusable top-level taxonomy shelf with stronger default suggestions, normalized saved-value reuse, and shared autocomplete across Note Editor, Note Detail metadata edit, Profile, and Onboarding.
- Course / Program autocomplete now filters in real time, ranks exact/prefix/contains matches more cleanly, keeps existing suggestions ahead of the custom action, and reuses existing display labels for exact case-insensitive matches.
- Course / Program helper text now adapts to `Learner Level`, and Profile learning-profile saves now show inline validation when either required field is missing.
- Saved custom subjects now feed future autocomplete suggestions through the existing distinct-subject backend source.
- Subject reuse now normalizes whitespace, dash formatting, and case-insensitive matches so equivalent custom subjects collapse into a cleaner autocomplete/filter catalog without adding a new subjects table.
- AI-generated subjects now use stronger library-specific guidance plus backend validation so overly broad labels like `Engineering` or `Business` are retried before being accepted.
- The AI Suggestions modal now uses a compact review layout with field-by-field comparisons, tag chips, a live preview, and a sticky mobile footer.
- Quiz generation now uses learner-level-aware prompt guidance across Quick Review, Challenge Quiz, and Adaptive Practice, defaulting to college-level when the user has no saved learner level.
- Quantitative notes can now produce computation and problem-solving questions with step-based explanations when the note context supports it.
- Challenge Quiz and Adaptive Practice now require richer explanations and concept labels in their generated quiz payloads.
- Library and Public Library now use richer metadata-driven filtering with course/program support, Public Library learner-level/source filters, and subtler note-card metadata hierarchy with visibility icons instead of extra badges.
- Public Library cards now emphasize the original note preview first and use subtle `views` / `copies` metrics plus `Most Viewed` sorting to help users spot strong notes faster.
- Dashboard `Continue Studying` now shows the actual note title plus subject/course metadata and uses the correct resume label for Quick Review, Challenge Quiz, or Adaptive Practice.
- Private and public note detail now include a `Full Notes` tab so users can inspect the complete original note alongside `Summary`, `Key Concepts`, and `Quiz`.
- The `Summary` view on private and public note detail now includes a subtle `View Full Notes →` CTA so users can jump from AI preview to the original note without losing context.
- Back navigation across all sub-pages now uses a shared `BackLink` component that renders `← {label}` with an arrow icon — small, muted, and link-styled rather than a button. Replaces all previous blue "Back to Library" / "Back to Note" link text and large Back buttons.
- My Profile (owner view) has no back link — it is a main navigation page reachable from the sidebar. Non-owners viewing another user's public profile see `← Public Library` linking explicitly to `/library/public`.
- Note Detail shows `← Library`, quiz pages show `← Note`, Create Note shows `← Library`, Edit Note shows `← Note`, Edit Profile shows `← Profile`, learn articles show `← Learn`. Inline card action buttons use short labels (`Note`, `Library`) without "Back to" prefix.

### Technical Changes

- Added `users.learner_level` and `users.course_program` with backward-compatible nullable storage for existing users.
- Backend Study Pack generation now prepares learner-level and course/program metadata in generation context for future prompt tuning, alongside note subject and tags.
- Refactored the OpenAI Study Pack service to share request/response/error handling across Study Pack, study-tip, and quiz generation flows, and added direct unit coverage for the refactored service.
- Added `notes.course_program` plus note-service create/update/copy handling so note metadata can diverge from the profile default when needed.
- Added normalized `GET /api/course-programs?scope=mine|public` suggestions backed by saved note/profile course-program values without adding a separate taxonomy table.
- Unified backend quiz-generation contracts onto strict JSON with required `answer`, `explanation`, and `concept` fields for more reliable parsing.

### Fixes

- Manual sign-out no longer reuses a stale protected-page `redirect` on the next login, so same-account and cross-account relogin now return to `Dashboard` instead of leaking back into the previous protected route.
- Restored distinct Note Editor create vs edit behavior so existing notes now render `Edit Note` copy, correct edit-mode actions, and the generated-note content lock without falling back to create-note messaging.
- Quiz validation now uses math-safe choice normalization, catches real blank/duplicate/invalid-choice payloads more accurately, and retries LLM invalid quiz output only once before failing.
- Quiz choice shuffling now preserves answer correctness by normalizing runtime data to canonical `choices + correctIndex`, keeping `A` / `B` / `C` / `D` as UI-only labels, and accepting legacy answer-text session payloads during load.
- Study Pack generation is now significantly more reliable for technical notes (Ohm's Law, electrical engineering, math formulas, science notes). The backend now safely logs failing field values for debugging (`requestId`, `field`, `value` truncated to 80 chars, `reason`) without logging full note content, prompts, or raw LLM output. Before failing on an invalid `quiz[].concept` or `subject`, the backend attempts a repair pass: concepts exceeding 4 words have leading filler phrases stripped (`Relationship between`, `Using the`, etc.) and are truncated to at most 4 words; subjects exceeding 6 words have their subtopic portion truncated to fit. The prompt rules for concept (now explicitly 1–3 words with counter-examples) and subject (now explicitly max 6 words with counter-examples) are tightened to reduce LLM drift. Covered by 10 new unit tests including an Ohm's Law regression scenario.

## v0.6.0 - Landing Revamp & Positioning (In Progress)

### New Features

- Landing page now frames NoteLib as a notes library and study workspace, not just a one-time quiz generator.
- Public marketing navigation now exposes `Home`, `Public Library`, `Learn`, `Pricing`, `Login`, and `Get Started`.
- NoteLib now has a standardized favicon and app-icon set based on the NL monogram for desktop, mobile, and home-screen usage.

### Improvements

- Landing page now uses a tighter high-conversion structure built around:
  - a faster product headline focused on summaries, quizzes, and exam simulations
  - a 3-step `Add notes -> Generate study pack -> Test yourself` explanation
  - dedicated feature coverage for Study Packs, Challenge Quiz, Adaptive Practice, and Board Exam Mode
  - clearer comparison against generic AI tools plus target-user guidance for students, board exam reviewees, and teachers
  - stronger CTA flow with `Start for Free`, `See how it works`, pricing preview, and a clearer closing section
- Challenge Quiz and Board Exam Mode now use a collapsible Question Navigator so mobile quiz screens stay less cluttered:
  - Challenge Quiz defaults to an expanded navigator on desktop and a collapsed summary on mobile
  - Board Exam Mode defaults to a collapsed navigator on both desktop and mobile to keep the exam view more focused
  - the collapsed summary still shows current question position and answered count, and expanding it keeps direct jump navigation intact
- Major action buttons now keep icon + text labels on mobile across the app’s shared action surfaces instead of collapsing to icon-only.
- Profile now supports a short bio on the private identity page, and Public Profile now renders that bio with avatar/initial styling and derived subject chips.
- Public Profile now uses a page-level `Back` action above the header card, based on navigation history instead of a hardcoded return link to Public Library.
- Private Library and Public Library now share the same `Search`, `Filter`, `Sort`, notes-list structure, with mobile-friendly filter/sort sheets instead of always-visible controls.
- Library, Public Library, and Public Profile note cards now stay action-free preview surfaces so note management happens consistently in Note Detail.
- Private Note Detail `Summary` and `Quiz` tabs now keep text labels on mobile for clearer view switching.
- Landing page now positions NoteLib as a notes library and study workspace first, with stronger Public Library and active-recall messaging.
- Public Library is now promoted directly from the landing page as a discovery surface that stays accessible without login.
- The landing page now integrates the Learn / active-recall message so new users understand the study method, not only the generation workflow.
- Learn article pages now use a consistent content-marketing structure with introduction, summary, key concepts, sample practice questions, and a bottom account-creation CTA.
- Landing page SEO title, meta description, and Open Graph metadata now align with the notes-library positioning update.
- Pricing page messaging now frames NoteLib as a notes library plus review workflow, with Free/Premium copy aligned around core note creation and heavier exam review periods.
- Pricing page now includes a `Why Go Premium` section that explains Premium in terms of serious review, practice, and exam preparation rather than only limits.
- Pricing no longer treats Public Library as a paid-plan feature.
- Theme toggle is now available on the shared public navbar and syncs with a persisted user theme preference for authenticated users.
- Navbar and app-shell logos now use the NL monogram, while marketing headers and the public footer use the full NoteLib wordmark.
- The Open Graph image now uses the standardized NoteLib branding, notes-and-lightning illustration, and notes-library messaging.
- Study Pack generation surfaces now use student-friendly monthly-limit banners and plan-specific limit modals for both Free and Premium instead of relying on disabled generate actions.
- Public Library now supports discovery sorting by newest, most copied, most shared, and most viewed.
- Public note detail now uses a stronger copy-first growth CTA for non-owners, including a handoff into their own Library note for generation.

### Fixes

- Auth redirect logic now returns users to interrupted protected pages through explicit redirect intent while sending manual public-page logins to `Dashboard`.
- Login-page auth messaging now distinguishes `session_expired`, `logged_out`, and `auth_required` so manual logout no longer shows the expired-session warning.
- Manual logout now suppresses late expired-session redirects from in-flight protected requests so logout messaging stays neutral.
- Shared public navbar no longer duplicates the theme toggle inside the mobile menu, and public CTA hierarchy now keeps `Get Started` primary, `Login` secondary, and theme as a utility control.
- Study Pack limit enforcement and usage warnings now use the same effective usage calculation so users are no longer told they have credit left while generation is already blocked.
- Free-plan near-limit messaging now shows the actual remaining Study Pack count instead of a generic warning.
- Note Detail generation now applies the same title/subject/tag suggestion flow as Create Note.
- Note Detail tab switching no longer refetches the note or snaps long pages back to the top when `?tab=` changes.
- Mobile Note Editor no longer lets the global `Send Feedback` launcher overlap the primary Generate CTA.
- Library-style note cards no longer mix management menus into preview surfaces, avoiding conflicting card-navigation behavior.

### Technical Changes

- Shared responsive action components now default to mobile icon + text labels, with explicit opt-out reserved for true icon-only utility controls.
- Added shared library toolbar and sheet components so private/public library controls stay consistent across desktop and mobile.
- Added shared brand-asset components for the monogram, full logo, and product icon, plus a local OG-image render pipeline and web manifest for the public icon set.
- Added a shared backend Study Pack usage resolver so plan summary and generation-limit enforcement stay synchronized across services.

## v0.5.0 - Public Profiles & Public Notes

Public Profile:

- Public profile page at `/public/profile/{userId}`
- Public identity uses `displayName`; public pages never show email
- Public profile shows `Profile Type`, public-note stats, and total copies
- Public profile visibility can be turned `On` or `Off`
- Owner-only public-page controls live on Public Profile:
  - `Edit Profile`
  - `Share Profile`
  - Public visibility badge/dropdown
- Non-owners can view/share public profiles only when the profile is public

Public Notes:

- Public notes appear in Public Library and Public Profile
- Public author labels are viewer-relative:
  - `By You`
  - `By NoteLib` with `Official`
  - `By {Display Name}`
- Public author labels link to Public Profile
- Public note detail remains read/copy/share only
- Public note copying preserves attribution to the source note and creator

UI and UX:

- Shared note-card layout across Library, Public Library, Public Profile, and public subject pages
- Whole-card click behavior across library-style note cards
- Removed redundant `Open Note` buttons from public showcase/discovery cards
- Shared cards now show clamped `Note Preview` plus `Summary Preview`
- Private Note Detail now uses underline tabs for `Summary` and `Quiz`
- Icon usage is standardized across navigation and common actions
- Quick Review, Challenge Quiz, and Adaptive Practice use distinct icons
- Action buttons now follow a shared responsive desktop/mobile pattern
- Dark-mode outline buttons use higher-contrast borders, lighter text, and clearer hover states
- Profile page is split into Display Name, Identity, and Profile Type cards with per-section save actions
- Public profile controls were moved off `/profile` and onto the Public Profile page
- Auth recovery now returns users to their interrupted or last visited page after login instead of always forcing `Dashboard`

Documentation baseline:

- `v0.5.0` is the documentation lock point for Public Profiles and Public Notes
- next planned milestone is `v0.6.0 - Landing Revamp & Positioning`

## v0.4.0 - Profile-Based Experience & UX

- Profile identity management
- Email change verification
- Onboarding per profile type
- Personalized dashboards
- Teacher workflow and quiz-first note creation
- Note editor UX improvements across desktop and mobile
- First-time activation flow from verification through first Study Pack and first quiz guidance
