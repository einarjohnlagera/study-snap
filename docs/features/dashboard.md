# dashboard.md - NoteLib Feature Context

Dashboard one-time guidance uses `pickActiveGuidance()` as a single-slot picker. Post-completion topic reminders, the Teacher introduction, and the returning-user spaced-review rhythm rule compete by priority so they never stack on one visit.

## Goal

Dashboard is a guidance surface, not a management screen. It should help users decide what to do next in the note -> Study Pack -> quiz loop.

## Key Files

**Backend**
- `backend/src/main/java/com/studysnap/backend/controller/DashboardController.java` — dashboard endpoints: `/dashboard/overview`, `/dashboard/continue-studying`, `/dashboard/focus-areas`, `/dashboard/weekly-activity`, `/dashboard/performance-summary`
- `backend/src/main/java/com/studysnap/backend/service/DashboardService.java` — business logic for all sections; resolves continue-studying recommendation, weak concepts, weekly activity, `courseProgram` context

**Frontend**
- `frontend/app/dashboard/page.tsx` — main dashboard client; `SupportedDashboardProfileType` branching (STUDENT / BOARD_EXAM / TEACHER / PROFESSIONAL); all section composition per profile type
- `frontend/app/dashboard/dashboard-primary-collection-hero.tsx` — condensed Primary Review Set identity, current-step, action, readiness card and its loading skeleton
- `frontend/app/dashboard/continue-spotlight.tsx` — Continue Studying card
- `frontend/app/dashboard/dashboard-focus-areas-card.tsx` — Focus Areas / weak concepts section
- `frontend/app/dashboard/today-focus-card.tsx` — top-priority Today Focus card for resumable reviews and due concepts
- `frontend/app/dashboard/study-pack-grid.tsx` — Recent Notes grid
- `frontend/lib/api.ts` — `getDashboardOverview()`, `getContinueStudyingRecommendation()`, `getTodayFocus()`

## Anti-drift Notes

- Dashboard sections are **profile-type branched** inside `page.tsx` using `resolveDashboardProfileType()`; do not add profile checks inside individual section components
- Continue Studying must use the `resumeType` label from the backend directly — do not infer mode labels on the frontend
- Focus Areas shows `Revisit Note` for Free/Plus users when Adaptive Practice is gated; only show the upgrade prompt when no source note is resolvable
- The Community Notes section (v0.21.0) uses `GET /notes/public?courseProgram=<value>&size=4` directly — no new dashboard backend endpoint

## Primary Review Set hero

When `GET /auth/me` returns a `primaryCollectionId`, Dashboard adds a condensed Primary Review Set hero above its existing cards and sections. It follows the Review Set detail hierarchy at summary depth: identity (profile-aware Primary Study Plan / Primary Review Set / Primary Lesson Plan label and title), a current-step summary, one `Continue Studying` action, and overall readiness percentage.

- Dashboard resolves the card through the existing `getMe()` → `primaryCollectionId` → `getCollectionGoal(primaryCollectionId)` read path; it adds no Dashboard endpoint, write, AI call, quota, or mastery calculation.
- The identity/title and `Continue Studying` action both open `/collections/{id}`. The detail page remains the owner of per-note next-action resolution, so this summary does not duplicate that more detailed logic.
- While the primary collection read is pending, Dashboard shows a hero-shaped skeleton rather than an empty gap or the goal-prompt fallback.
- If the read fails or the primary reference no longer resolves, the hero slot falls back to the existing `GoalPromptBanner` / `DashboardGoalCard` behavior. Learners with no `primaryCollectionId` stay on that existing fallback path unchanged.
- This is additive: Continue Studying, Focus Areas, Today Focus, Recent Notes, Community Notes, Usage / Progress, and the valid-primary continue presentation remain below the hero. The no-primary Study Plan presentation now points to `/explore`; valid-primary continue mode still duplicates this hero lower on the page as a documented v0.67.0 Known Limitation.

## Current Personalization Prompt

After onboarding completes, Dashboard may show a lightweight learner-level prompt near the top.

Current prompt copy:

- title: `Too easy or too hard?`
- body: `You can adjust your learner level anytime — quizzes will match your new study stage next time you practice.`
- CTA: `Adjust level`

Behavior:

- dismissible
- dismissal stored per user in frontend storage
- CTA navigates to `/profile?from=dashboard#learning-profile`
- when opened from Dashboard, `/profile` shows a `Dashboard` back link instead of the public-profile back link

This prompt is for learner level only. It does not move learning style or reminder preferences back into onboarding.

### Copy-on-signup profile completion prompt

Successful public-note copy-on-signup users may arrive on Dashboard with a pending lightweight profile-completion marker instead of a completed onboarding timestamp. When their Profile Type, Learner Level, or Course / Program remains incomplete, Dashboard shows a separate inline card near the first-run guidance area.

- The card is non-blocking and dismissible; Dashboard actions remain usable.
- It reuses onboarding's profile choices, learner-level options, course/program combobox (`allowCustom=false`), and optional Board Exam date.
- A dismissal lasts for the current day but never clears the pending completion marker, so the card can return on a later visit until saved.
- Submission saves Learning Profile first, then completes onboarding profile metadata. A partial failure retries only the latter.
- This card is only for the copy-on-signup marker cohort; it does not change the normal onboarding wizard or the existing learner-level personalization prompt.

## Profile-specific priorities

### People you support

Dashboard adds a profile-neutral `People you support` card whenever `GET /linked-learners` returns non-revoked relationships where the caller is the supporter. Accepted connections link to the relationship-scoped progress view; pending connections state that acceptance or guardian consent is still required. The card links to `/linked-learners` for management and does not turn supporting someone into a `ProfileType`, replace the caller's own learning Dashboard, or expose learner notes.

### Student

Dashboard should prioritize:

- `Continue Studying`
- `Focus Areas`
- `Recent Notes`
- `Quick Review`
- `Usage / Progress`

For a first-time Student or Professional (`hasCompletedSession === false`), Quick Review stays above Usage / Progress. Once the existing note-list session signal shows a completed session, the Dashboard keeps its Continue Studying and Focus Areas placement and moves the static Quick Review card below Usage / Progress, so returning users encounter their existing progress signals before a generic review entry point. This is composition only: it adds no cards or data requests.

### Board Taker

Dashboard should prioritize:

- `Exam Countdown`
- `Start Board Exam`
- `Weak Areas`
- `Adaptive Practice`

### Board Exam pacing

For BOARD_EXAM users with a future profile-level `examDate`, Dashboard extends the existing countdown with an owned-content pacing line when due concepts exist: the total due concepts across the learner's Study Packs, divided linearly across days remaining and rounded up to a daily target. The total reuses `ConceptHealthService.getDueConceptsByStudyPackIds` across all owned Study Packs; it does not match, generate, or fill missing content, and it is not Smart Review Planning. If the date is today/past, no concepts are due, or the additive calculation is unavailable, Dashboard keeps the existing plain countdown behavior. This Dashboard-only profile-date pacing is deliberately separate from Review Set/Goal detail's collection `targetCompletionDate` pacing; those surfaces must not be coupled.
- `Usage / Progress`

If Adaptive Practice quota is exhausted for the current plan, the CTA should open the shared upgrade flow with "upgrade for more sessions" framing instead of navigating to a dead-end route.

### Teacher

Dashboard should prioritize:

- `Create Teaching Material`
- `Recent Notes`
- `Recently Generated Quizzes`
- `Ready to Export`
- `Teacher Help / Tips`

Teacher sections should stay quiz-preview and export oriented, not student-session oriented.

## Continue Studying

Endpoint:

- `GET /dashboard/continue-studying`

Required payload shape for the UI:

- `noteId`
- `noteTitle`
- `subject`
- optional `courseProgram`
- `resumeType`
- `currentQuestionIndex`
- `totalQuestions`
- `lastReviewedAt`
- optional `summaryPreview`

Current label mapping:

- `QUICK_REVIEW` -> `Resume Quick Review`
- `CHALLENGE` -> `Resume Challenge Quiz`
- `ADAPTIVE` -> `Resume Adaptive Practice`

Rules:

- use the backend `resumeType` label directly
- do not make extra frontend fetches just to label the card
- keep the card note-first so the user knows which note they are returning to

## Today Focus

Endpoint:

- `GET /dashboard/today-focus`

Backend priority order (`DashboardService.getTodayFocus()`):

1. resumable Quick Review (`RESUME_REVIEW` / `RETRY_REVIEW`)
2. due concepts (`DUE_CONCEPTS_REVIEW`)
3. weak concepts from the latest Quick Review (`PRACTICE_WEAK_CONCEPT`)
4. a previously opened, newly created, or recently reviewed Study Pack (`REVIEW_PACK`)
5. the first-Study-Pack suggestion (`STUDY_SUGGESTION`)

**The Dashboard only renders the `TodayFocusCard` component for the `DUE_CONCEPTS_REVIEW` type.** The other four types exist in the resolver (some predate this card's Dashboard integration) but are intentionally not surfaced here — `RESUME_REVIEW`/`RETRY_REVIEW` would duplicate the existing Continue Studying section (`getContinueStudyingRecommendation()`, a separate, independently-computed resolver over the same in-progress-session signal), and `PRACTICE_WEAK_CONCEPT` would duplicate Focus Areas. Do not widen the render condition without first reconciling those two independent "resume" implementations and the two "weak concepts" sources — see the dead-code note below.

**The weak-concepts branch resolves from PERSISTENT weakness, not the latest session (`v0.77.0`).** It reads `ConceptHealth` for concepts whose `incorrect_streak` meets `TWICE_MISSED_STREAK_THRESHOLD` — the same bar the twice-missed Ask Companion prompt uses — rather than extracting `weakConcepts` from the most recent completed Quick Review. **A single bad quiz must not produce a recommendation:** that taught the wrong mental model ("the system wants me to take another quiz") and spent quota-limited remediation on weak evidence. The copy names the persistence accordingly.

**Within-pack only.** `concept` is free text keyed per Study Pack with no canonical identity, so the same idea in two packs cannot be related and counts are **never summed across packs** — the branch picks the single pack with the most persistently-weak concepts. Cross-pack recommendation requires concept identity and is deliberately out of scope.

The due-concepts branch uses the existing deterministic `concept_health` threshold across the learner's owned Study Packs. It returns the real due-concept count and each concept's source-note reference. It is available to every plan, including Free; it is a retention signal, not an Adaptive Practice entitlement.

Due-concepts actions reuse the Focus Areas three-way behavior:

- Adaptive Practice available and source note present → `Practice Due Concepts`
- source note present but Adaptive Practice unavailable → `Revisit Note`
- no source note → `Unlock Adaptive Practice` shared paywall action

The card is loaded as a non-critical Dashboard request, so a focus-resolution failure never blocks the rest of Dashboard.

## First-session review commitment

The shared post-session next-step surface asks a learner to schedule their next review after their first completed session. **There is deliberately no profile-type gate** — it appears for every profile while `MeResponse.reviewCommitmentOutstanding` is true. The commitment being collected is the **review days**, and gating those behind an exam date would exclude every `STUDENT` (~27% of profile-typed accounts), since onboarding only collects that date for `BOARD_EXAM`. The surface is its own filter: it exists only after a completed session, so people who never study never see it. **Note that teachers DO reach it** — `exam-mode-visibility.ts` gives `TEACHER` the Challenge Quiz card, and the Challenge result screen renders the prompt from the shared session table with no profile branch. An earlier version of this paragraph claimed teachers never reach it; that was wrong. The no-profile-gate decision stands on its own merits, not on that false reasoning. The **exam-date sub-field** renders only for users who already have an `examDate` or are `BOARD_EXAM`; everyone else sees the review-days picker alone, and the date is never required from them.

The learner chooses review weekdays — confirming or setting an exam date too, where that field applies — or declines with `Not now`. **This endpoint never clears an existing exam date:** it does not own that field, and a learner with no date sends null on every save, so the write is applied only when a date is supplied. Both successful choices resolve the prompt through `PUT /users/review-commitment`; a failed save leaves it outstanding and preserves the form state. Resolution is stored in `users.review_commitment_prompted_at`, never `localStorage`, so the prompt stays resolved across browsers and reloads. Review weekdays remain editable in Settings → Email Preferences.

## Focus Areas

Focus Areas should show weak concepts for all learners when data exists.

Current action behavior:

- Users whose plan allows Adaptive Practice can launch Adaptive Practice from the suggested note while quota remains
- Users without an Adaptive Practice start path still see the same weak concepts and get a `Revisit Note` link to the source note so they can review material
- The upgrade prompt (`Unlock Adaptive Practice`) is shown only when no source note is resolvable
- When weak concepts are present, the card also links to `/progress` with `View full progress report ->` so learners can open the subject-level ConceptHealth report

## Community Notes Section

A section visible to all profile types, placed below Recent Notes.

**Section title**: "Notes for [CourseProgram]" — e.g. "Notes for PNLE", "Notes for NMAT"
**Data source**: `GET /notes/public?courseProgram=<value>&size=4` — the same endpoint as the Public Library
**Footer link**: "See all in Explore →" navigates through `/explore?tab=notes&source=dashboard&courseProgram=<value>`, where `<value>` is slugified via `slugifyPublicLibraryFilterValue()` (v0.67.1 fix — previously the raw, unslugified value, unlike every other `courseProgram` filter link in the app, so the arriving filter chip never displayed and a no-op Filters-modal re-submit silently dropped it).

Behavior by state:

- `courseProgram` is set and matching public notes exist → show up to 4 note cards using the shared public library card layout; clicking a card navigates to the canonical public note detail page
- `courseProgram` is set but no matching public notes exist → hide the section entirely; do not show an empty state
- `courseProgram` is not set → render a placeholder card with a modal CTA:
  - Modal title: "Set your Course/Program"
  - Modal body: "Set your Course/Program to see public notes tailored for your review track."
  - Primary CTA: "Go to Learner Profile" (navigates to `/profile#learning-profile`)
  - Secondary CTA: "Cancel"

Applies to STUDENT, BOARD_EXAM, TEACHER, and PROFESSIONAL profile types.

## Study Plan Section

v0.31.0 adds a learner-facing plan surface for STUDENT, BOARD_EXAM, and PROFESSIONAL dashboards.

`DashboardStudyPlanSection` has `discoveryPresentation?: "full" | "pointer" | "recommendation"`, defaulting to `"full"`.

- Both persistent Dashboard call sites (STUDENT/PROFESSIONAL and BOARD_EXAM) pass `"recommendation"`.
- Onboarding passes no presentation prop, so it retains the full course/program-matched recommendation and adoption flow unchanged.
- `viewAllHref` and `browseWhenEmpty` were removed. Dashboard no longer renders a matching-plan catalog, a `See all N` link, or the cold-start no-match browse card.

Dashboard recommendation behavior:

- A configured primary lookup is still pending → render nothing, preventing a flash of the Explore pointer.
- A valid owned primary resolves → render the existing Primary continue card byte-for-byte, with no adoption call. The `readyCount of itemCount` detail line is scoped to `!usingPrimary`, so recommendation mode does not rewrite the primary card's copy.
- With no primary, resolve the first exact course/program-matched public plan plus owned collections. Both reads are skipped while a configured primary is still resolving or has resolved to a real plan, and the effect is keyed so that a learner with no primary configured fetches exactly once. An unadopted match renders one named recommendation with its title and `readyCount of itemCount notes practice-ready`, reusing the existing Start/adopt path.
- No course/program, no matching public plan, or either read failing → render the existing dashed pointer card to `/explore?source=dashboard`; never link to an empty filtered result.
- An already adopted match is not recommended again; the slot falls through to the Explore pointer rather than rendering empty, so a learner who has engaged with plans keeps a discovery entry point. `suppressPointerWhenNoPrimary` still applies to that fallback, so the zero-note call site renders nothing there — it is the pointer branch, and that flag governs every pointer branch. The valid Primary continue path remains the owned-plan route.
- `suppressPointerWhenNoPrimary` still suppresses the pointer fallback at the STUDENT/PROFESSIONAL zero-note call site; it does not suppress a genuine named match.
- Recommendation and pointer impressions/clicks use `STUDY_PLAN_RECOMMENDATION_IMPRESSION` / `_CLICKED` with `surface=dashboard`, `recommendationType=named-plan|generic-pointer`, and course/program metadata. Impressions fire once per resolved rendered state and analytics failures never affect the card.

Legacy `"pointer"` presentation remains available for bounded callers: it never calls `listPublicStudyPlans` and renders the Explore pointer directly when no primary resolves.

Full presentation behavior remains available to onboarding: it calls `GET /collections/public?courseProgram=<value>` plus the user's `GET /collections`, shows the first matching public plan, detects an already adopted `sourcePlanId`, and preserves its Start/Continue flow, practice-ready metadata, retryable adoption error, and skipped-item destination notice. No matching plan still self-hides, and no configured course/program still shows the existing learner-profile nudge.

The same `DashboardStudyPlanSection` card is also reused on the onboarding completion step (v0.31.1) as a supplementary adopt surface — see `onboarding.md`. There is no separate component or endpoint for that surface.

## First-study guidance

Dashboard may also show first-study guidance for verified users who still have `studyPackCount == 0` and have not completed the separate product-onboarding tracker.

That flow is separate from `/onboarding` and is tracked with `productOnboardingCompletedAt`.

When a user has zero notes, the dashboard renders a profile-aware first-run empty state (`DashboardEmpty`) that teaches the loop for that profile rather than a single generic prompt:

- TEACHER → import lecture material → generate quizzes → group into a Lesson Plan to export/share
- STUDENT → import or create notes → generate a Study Pack → quiz and track weak topics
- BOARD_EXAM → import reviewers → generate Study Packs → practice across a Review Set
- PROFESSIONAL → import or create materials → generate → scenario-based practice

The profile-specific collection term (Lesson Plan / Review Set) is resolved through `lib/collection-labels.ts`, not hardcoded. Every variant surfaces both entry points — `Import files` (`/notes/import`) and `Create a note` (`/notes/new`) — to make the new bulk-import on-ramp the first thing a new user sees.

**Curated-plan adoption link (v0.39.1, repointed v0.67.0).** For STUDENT, BOARD_EXAM, and PROFESSIONAL profiles only (TEACHER has no adoption path), `DashboardEmpty` also renders the same secondary copy — "Or start from a ready-made {Study Plan / Review Set} instead" — but now points to `/explore?source=dashboard`. Explore owns catalog browsing and its fallback states; `DashboardEmpty` does not duplicate matching logic. **The onboarding flow this paragraph used to call "locked" was restructured by `v0.71.0` slice 5** — it is now an intent router (see `docs/features/onboarding.md` for the current shape), and one of its two doors leads to ready-made materials. What survives the restructure is the narrower rule this clause actually protected: **`DashboardEmpty` owns the dashboard's adoption CTA, and onboarding's own routing is not a place to duplicate catalog-matching logic.** Explore owns catalog browsing and its fallback states; neither surface reimplements them.

**No-primary pointer suppressed on the zero-note Dashboard (v0.67.1 fix).** The STUDENT/PROFESSIONAL zero-note branch is the only call site where `DashboardEmpty` (which already carries the curated-plan adoption link above) and `DashboardStudyPlanSection`'s pointer card were both rendering, stacking two near-identical `/explore?source=dashboard` CTAs directly on top of each other. `DashboardStudyPlanSection` now takes `suppressPointerWhenNoPrimary?: boolean`, passed `true` only from that STUDENT/PROFESSIONAL zero-note call site (`totalNoteCount === 0`); when set, the no-primary `ExplorePointerCard` branch renders nothing instead, leaving `DashboardEmpty`'s inline link as the sole CTA. The valid-primary continue card (a user's actual owned Primary Review Set) is a different render branch and is unaffected — it still renders normally even with the flag set. The Board Exam zero-note branch never rendered `DashboardEmpty` and is untouched, so its pointer card call site does not pass this prop.
