# RELEASES.md - NoteLib

## v0.50.4 - Exam Hub Discovery Polish

**Status: In Progress**

Theme: two small discovery/SEO fixes on the public Exam Hub surface — a duplicated link and a vocabulary mismatch against how Filipino board-exam takers actually search — surfaced by direct user testing and a Fable SEO strategy session. Patch release, not minor — polish to something that already exists, not new planned feature work, matching the v0.45.1/v0.45.2/v0.50.1/v0.50.2/v0.50.3 patch precedent.

### Planned Scope

- **Collapse duplicate "Browse {Hub} hub →" link (frontend).** When a public note's course/program maps to an Exam Hub, the page shows both a callout banner ("Preparing for the PNLE? ... Browse PNLE hub →") and the "More {Course/Program} notes" section header linking to the identical `/exam/{slug}` destination with identical wording. The section header link is repointed to the plain course/program-filtered Public Library view (the same behavior it already has when no Exam Hub applies) instead of duplicating the hub link — the callout banner stays the only hub link, and the section link becomes a distinct "See all in this course/program" destination, restoring consistent `See all →` wording.
- **"Free reviewer" vocabulary pass on Exam Hub pages (frontend).** Exam hub titles, meta descriptions, H1 subline, and value-strip copy are rewritten to include "free" and "reviewer" — the terms Filipino board-exam takers actually search ("free PNLE reviewer") — instead of only "notes," while staying inside the notes-library identity (no "AI tool" framing). Sourced from a Fable SEO strategy session (`docs/claude-prompt/seo-strategy-out/01-seo-strategy.md`, candidate P2) that diagnosed this vocabulary mismatch as the largest fixable gap in why NoteLib doesn't surface for exam-named searches. Confined to `exam-hub-config.ts` + `app/exam/[slug]/page.tsx` copy — no new page, no new architecture.

Anti-drift: no new quiz modes; no pricing/paywall/quota/conversion-funnel changes; no mass-generated AI landing-page content ("curation, never generation" stays locked); no Wave 2 Exam Hub expansion (explicitly deferred, see Backlog Index); public note visibility/ownership rules untouched.

### Shipped

_(nothing yet)_

## v0.50.3 - Public Note Copy Flow & Related-Notes Consistency

**Status: Released**

Theme: fix a real race-condition bug and a product-design mismatch in the public note "Quiz yourself on this note" copy flow, plus two small consistency fixes on the same page's related-notes sections — all surfaced by direct user testing, following a Fable session (`docs/claude-prompt/public-note-copy-and-related-links-out/01-copy-flow-and-link-consistency.md`) that confirmed the diagnosis and recommended the fix shape. Patch release, not minor — bug fixes and consistency polish to something that already exists, not new planned feature work, matching the v0.45.1/v0.45.2/v0.50.1/v0.50.2 patch precedent.

### Planned Scope

- **Public note "Quiz yourself" copy-as-is flow (backend + frontend, routed through Codex).** Make copy-as-is (the existing synchronous, zero-LLM Study Pack deep-copy) the *only* behavior for this CTA — never set `generate=1` on the quick-review redirect when the copy already returned a ready Study Pack. Skip the AI-suggested-metadata modal entirely on this flow (nothing gets regenerated, so there's nothing to reconcile — title/subject/tags already carry over verbatim, same as every other copy action). Gate the CTA server-side on the source note actually having a ready Study Pack, so the rare fallback (source lacks one) becomes a genuine edge case rather than a silent auto-generation path — if still reached, land the copier on their new note's normal not-yet-generated state with the standard explicit Generate CTA, never auto-generate. Fix the underlying race condition (two effects both reacting to "Study Pack ready" — one navigates synchronously, the other needs an extra network round-trip before it can open the modal) as an ordering guarantee, not a timing patch. No new personalization mechanism — the existing copied-pack "regenerate for your level" hint already covers that need without blocking the first quiz question.
- **Related-notes link wording and grid consistency (frontend).** Public note detail's two related-notes sections ("More {Course/Program} notes", "More in {Subject}") both get their generic "see all" link shortened to `See all →` (sentence case, same wording on mobile and desktop, with a full `aria-label` added since the shortened text becomes screen-reader-identical between the two sections) — fixes mobile wrapping on longer subject/course names. `Browse {Hub} hub →` (the Exam Hub case) stays untouched — a deliberately different, more curated destination. The subject section's grid collapses from `sm:grid-cols-2 lg:grid-cols-3` to `sm:grid-cols-2`, matching the course/program section — same shared card, same page, no reason for two column counts.

Anti-drift: no new quiz modes, no touching the locked five-mode `EXAM_MODES.md` contract; no new personalization/regeneration capability built (the existing regenerate hint already covers it); no change to note visibility, ownership, or copy/adopt data model beyond the generation-skip described above; `Browse {Hub} hub →` wording and destination stay untouched.

### Shipped

- **Public-note Quiz Yourself copy-as-is flow (frontend).** The ready-only Quiz Yourself CTAs now deep-copy the public source Study Pack and enter Quick Review with `?copied=1&startQuickReview=1`, never an LLM regeneration or metadata-reconciliation modal. If a source pack disappears before the copy completes, the copied note safely opens in its ordinary not-yet-generated state with the existing manual Generate action and no dangling auto-redirect. The underlying generated-metadata/automatic-navigation race is also ordered correctly: a real generated Study Pack's metadata decision resolves before any immediate next action can navigate away.
- **Related-notes link wording and grid consistency (frontend).** Both related-notes sections on public note detail now share the `See all →` link label (with a full `aria-label` for accessibility, since the visible text is now identical between them), and the subject section's grid collapses to `sm:grid-cols-2` to match the course/program section. `Browse {Hub} hub →` is untouched.
- **Consolidated GPT context handoff docs (repo organization, not user-facing).** Moved `GPT_CONTEXT.md` from the repo root into `docs/gpt-contexts/`, alongside the existing marketing/social GPT context docs, and added a new `NOTES_AND_COLLECTIONS_CONTEXT.md` structural handoff (Note fields, subject/courseProgram taxonomy, Bulk Generate metadata, Note Collections vs. query-filtered groupings) to the same directory, with a `README.md` index.

## v0.50.2 - Note Card Content Consistency

**Status: Released**

Theme: reconcile inconsistent note-card content across four surfaces (public note detail's two related-notes sections, Public Library grid, private Library grid) into one rule, following a Fable card-content-strategy session (`docs/claude-prompt/note-preview-vs-summary-out/01-card-content-strategy.md`) prompted by a direct user report that two related-notes sections on the public note detail page render differently for no real reason. Patch release, not minor — a consistency/polish fix to something that already exists, not new planned feature work, matching the v0.45.1/v0.45.2/v0.50.1 patch precedent.

### Planned Scope

- **Single-excerpt card cascade (frontend).** Every note card across the four surfaces shows exactly one preview excerpt instead of the current inconsistent mix: note preview if the note body is non-empty and clears a minimum length, else a labeled "Summary" fallback (Study Pack summary excerpt), else no excerpt block at all. Removes the stacked "NOTE PREVIEW" / "SUMMARY PREVIEW" dual-section layout currently shown on `SharedNoteCard` (private Library grid, Public Library grid, public note detail's "More in {Subject}" section).
- **Migrate the bespoke "More {Course/Program} notes" card (frontend).** The public note detail page's older, hand-rolled related-notes card (shipped 2026-06-02, before `SharedNoteCard` matured) is replaced with the shared component, using identical card content to "More in {Subject}" — the two sections may still differ in query/count, never in template.
- **Rewrite the documented card-content rule (docs).** `docs/features/public-library.md`'s "prioritize original note preview over generated summary" rule is rewritten from a human-authorship rationale (no longer verifiable — a growing share of notes are AI-authored via the shipped `Generate Note` feature) to a source-object rationale: the note is the source, the summary is a fallback preview of a derivative. The priority (note preview first) is unchanged; only the reasoning behind it is corrected.

Anti-drift: no origin-aware card rendering — the Fable session explicitly rejected this even if origin tracking existed, since a `Generate Note` draft gets edited and creation-time provenance isn't current-authorship truth; no new database column, migration, or analytics event (Phase 2 origin tracking stays parked, gated on a separate explicit product go-decision, not part of this release); no change to Featured-ranking eligibility criteria (the "non-empty note preview" gate is untouched); no change to note visibility, ownership, or copy/adopt rules; no change to `Generate Note` itself — it remains a shipped, sanctioned feature.

### Shipped

- **Single-excerpt card cascade (frontend).** `SharedNoteCard` now resolves one preview excerpt via a cascade (note preview if ≥40 chars trimmed, else a "Summary"-labeled fallback, else no excerpt block) instead of always stacking both a "NOTE PREVIEW" and "SUMMARY PREVIEW" section. Replaced the `notePreviewLines`/`summaryPreviewLines`/`showPreviewLabels` props with a single `previewLines` prop; all five consumers (private Library, Dashboard community notes, public note detail's "More in {Subject}", public profile notes, Public Library grid) updated accordingly.
- **Migrated the bespoke "More {Course/Program} notes" card (frontend).** Public note detail's older hand-rolled related-notes card now renders via `SharedNoteCard`, matching "More in {Subject}"'s content exactly; the two sections differ only in query/count. This section had zero prior test coverage — added tests covering the rendered cards and the no-course/program omission case, plus fixed a missing `getServerPublicNotesByCourseProgram`/`getServerPublicNotesBySubjectSlug` mock reset that would have let state leak between tests.
- **Rewrote the documented card-content rule (docs).** `docs/features/public-library.md`'s "prioritize original note preview" rule now states the source-object rationale instead of the no-longer-verifiable human-authorship one; explicitly documents the minimum-length threshold and warns against reintroducing either the dual-preview layout or origin-aware rendering.
- **Migrated the subject-landing page's card too (frontend).** `/public/library/{subject}`'s `SubjectNoteCard` — discovered mid-implementation as a third, independent bespoke card using `summaryPreview || contentPreview` (the opposite priority order from the documented rule) — now renders via `SharedNoteCard`, same single-excerpt cascade as the other four surfaces. Kept this page's existing author/Official-badge footer as-is (no copy/like actions added — this page has no client-side interactivity today and adding any wasn't part of this fix); dropped the custom subject/course-program chip markup in favor of the shared `SubjectBadge` component. Added a test locking in the cascade (note preview wins when long enough; a too-short note body falls back to the labeled summary excerpt).
- **Migrated the Exam Hub page's card too (frontend).** `/exam/{slug}`'s `ExamNoteCard` was a near-identical copy of the subject-landing page's pre-fix bespoke card (same `summaryPreview || contentPreview` priority), apparently copy-pasted before that page was migrated. Now renders via `SharedNoteCard`, same treatment as the subject-landing page migration; added the same cascade-locking test. All six note-card surfaces now share one content rule.
- **Consistent related-notes section link labels (frontend).** Public note detail's "More {Course/Program} notes" section linked out as `View all →` while "More in {Subject}" said `See all in {Subject} →` — same job, different wording, flagged directly from a user screenshot. The course/program section's fallback link (shown when the course/program doesn't map to an Exam Hub) now reads `See all in {Course/Program} →`, matching the subject section exactly. Left the Exam Hub case (`Browse {Hub} hub →`) unchanged — that's a genuinely different, more curated destination, worth naming explicitly. Added a test covering that branch, since none existed before; it also surfaced (not fixed, not asked for) that the page already shows a same-destination "Browse {Hub} hub →" callout banner directly above this section when an Exam Hub match exists, so the label now appears twice in a row pointing at the same URL — noted in the Backlog Index as a possible follow-up, not addressed here.
- **Filter-preserving "← Public Library" back-link on related-notes cards (frontend).** `PublicLibraryBackLink` already restored the last filtered Public Library view from a `sessionStorage` key, but that key was only ever written by clicking a card from the main Public Library grid itself — so opening a note from a related-notes card (either related-notes section on public note detail, or the subject-landing/Exam Hub pages' own note grids) lost the filtered context, flagged directly from a user screenshot. A new shared `PublicLibraryReturnLink` client component (all four surfaces are Server Components, so this needed its own small client wrapper) now saves the equivalent filtered URL before navigating: the subject-landing page's own canonical path for subject-scoped cards, and a `courseProgram`-filtered *Public Library* URL (never the Exam Hub URL, even when one exists — the back-link is labeled "Public Library" and shouldn't silently land somewhere else) for course/program-scoped cards, using each note's own `courseProgram` on the Exam Hub page since a hub can span more than one. Consolidated the `notelib_public_library_return_url` sessionStorage key, previously duplicated as a raw string literal across three files, into one shared constant + `savePublicLibraryReturnUrl()` helper in `lib/public-library-url.ts`. Added a dedicated test for the new component plus a click-through test on each of the four surfaces confirming the correct return URL is saved.
- **Pre-signoff pressure test (Codex, `docs/codex-prompts/v0.50.2-pre-signoff-pressure-test.md`).** Full-release review against the risk profile flagged above — `SharedNoteCard` and the return-link mechanism were each touched by multiple PRs across this release. Three verified findings, all fixed:
  - **A seventh related-notes surface had been missed.** The public mini-quiz preview's post-answer "More from {subject}" teaser cards (`public-mini-quiz-preview.tsx`) independently reimplemented the old, backwards priority (`summaryPreview ?? contentPreview`) and additionally never fell back to the note preview when the summary was an empty string (`??` only falls through on `null`/`undefined`, not `""`). Now reuses the same `resolveCardExcerpt()` cascade (exported from `shared-note-card.tsx` rather than reimplemented a fourth time) and the same `PublicLibraryReturnLink` mechanism, saving the subject-landing page as the return URL. Kept the compact single-line teaser markup as-is — no tags/badges/metrics chrome, matches the surface's intentionally minimal shape. Added dedicated tests for the cascade (including the empty-string edge case that caused the bug) and the return-link save.
  - **`docs/features/public-library.md` self-contradicted.** Its "## Note Cards" section still listed `Note Preview` and `Summary Preview` as two separate rows, left over from before the single-excerpt cascade rewrite of the "Discovery guidance" section elsewhere in the same file. Corrected to describe the single-excerpt cascade.
  - **`SharedNoteCard` had zero dedicated unit tests**, despite being the one piece of logic all seven surfaces now depend on — coverage was scattered and incidental across consumer-page tests, and the note-detail page's own tests mock `SharedNoteCard` entirely, so its "cascade" tests there never exercised the real logic. Added `shared-note-card.test.tsx`: exhaustive `resolveCardExcerpt()` coverage (note-wins, empty-string/null/undefined fallback, the 40-char boundary on both sides, whitespace-collapse-before-measuring) plus component-level rendering tests (label visibility, tag overflow, metrics row, title fallback).

## v0.50.1 - Mobile UI Polish

**Status: Released**

Theme: fast-follow polish batch, mostly refining the v0.50.0 tab bar itself plus two small unrelated pre-existing UI issues surfaced by direct user report. Not retention-flavored, not a new feature — a patch release, same pattern as v0.45.1's batched pre-existing-bug fixes.

### Planned Scope

- **Tab bar refinements (frontend + backend).** Three related changes to the mobile bottom tab bar shipped in v0.50.0: (1) an icon-only rendering variant on Quiz Result and Note Detail — pages the user experiences as focus/review moments even though they were never covered by the existing `useBottomViewportClaim` footer-conflict mechanism; (2) the Library/Public Library tab links restore the last filtered view when a filter context exists, reusing the existing `?ref=` pattern (private) and `notelib_public_library_return_url` sessionStorage key (public) instead of always linking to the bare path; (3) a backend-persisted user preference to hide the tab bar entirely, composing with (1) — preference OFF means the bar never renders on any page, ON (default) keeps today's per-page behavior.
- **Review Set description truncation (frontend).** The Goal detail header description (`line-clamp-3`, no expand affordance) and the matching child Subject Plan card truncation (`line-clamp-2`) get a "Read more" expand/collapse, so a long curated description isn't silently cut off with no way to read the rest.
- **Progress page milestone empty state (frontend).** At zero progress, `GoalMilestonesCard` renders every milestone as an identical gray dot and a 0%-width progress bar with no differentiation visible without scrolling to the one ring-highlighted "next" milestone — reads as broken rather than as a real empty state. Add a legible "Next: {milestone label}" summary line so the state is self-explanatory without relying on subtle ring styling. (Follow-up, same user report: the milestone fix didn't address the actual "broken UI" complaint — see the Concept Mastery subject row fix below.)
- **Progress page Concept Mastery subject rows too large (frontend).** The "Concept Mastery" list reused the shared `ReadinessBar` card (built for Collection Detail / Note Detail, where only a handful of subjects render) for a list that can hold 15+ subjects on this page. Each roomy card (~130px, `items-start` header with a two-line title block beside a single-line status word) made the section balloon and read as visually unbalanced. Added a page-local, denser `SubjectMasteryRow` (title and status inline on one `items-center` row, thinner padding, stats collapsed to one caption line) used only on `/progress`; the shared `ReadinessBar` is untouched elsewhere.

Anti-drift: no change to `useBottomViewportClaim`'s existing footer-conflict behavior on quiz-*taking* screens (Challenge Quiz, Quick Review, Long Exam) — the new icon-only mode is a distinct, additive "focus" reason, not a repurposing of that mechanism; no new sessionStorage-based filter store for Private Library (the locked rule is URL-params-only) — the fix reads the existing `?ref=` pattern, it doesn't introduce a new one; no change to Public Library's existing `sessionStorage` return-key mechanism, only a new reader of it; no change to the Mobile Button Rule's icon+text default for the tab bar's normal (non-focus-page) state.

### Shipped

- **Progress milestone next-state summary (frontend).** Goal Milestones now states the next checkpoint in text above the existing marker grid (or `All milestones reached` at completion), making zero-progress states legible without changing the dots or progress bar.
- **Progress page Concept Mastery subject rows (frontend).** Replaced the shared, roomy `ReadinessBar` card with a page-local `SubjectMasteryRow` for the `/progress` Concept Mastery list: subject name and readiness status now sit on one centered line instead of a top-aligned mismatch between a two-line title and a single-line status word, with stats collapsed into one caption line and thinner padding — cuts a 15-subject list from ~2,200px to ~1,400px. First pass (density only) didn't resolve the reported "cards not aligned" complaint; a second pass removed `ReadinessBar`'s colored `border-l-4` accent stripe, which the cards above (`GoalMilestonesCard`/`NextStudyCard`) never had — the stripe's visual weight, not any actual pixel offset, is what read as misaligned. A third pass fixed a real regression the compaction introduced: dropping the original title-block's `min-w-0` wrapper meant the row's flex/grid items refused to shrink below their content's intrinsic width, so `truncate` never engaged on longer subject names and the whole Concept Mastery section grew wider than the viewport (confirmed via a user screenshot circling the overflow past the header bar). A fourth pass, after a Fable UX-affordance review (`docs/claude-prompt/progress-subject-row-clickability-out/01-affordance-review.md`), fixed a real usability regression the plain-card approach introduced: a flat gray card with only a `hover:opacity-80` cue has no resting-state signifier that it's clickable, and hover never fires on the ~75% of sessions that are mobile/touch. `SubjectMasteryRow` now applies the app's existing `getBrowsingCardClassName()` clickable-card utility (already used by the Library note-card grid) to the wrapping `Link` itself — not the inner content — so the raised white surface, border/shadow, press-scale, and focus ring all attach to the actual clickable element, plus a small muted trailing `ChevronRight` as a platform-conventional disclosure indicator. `min-w-0` now lives on the `Link` and the subject heading; status urgency stays color-coded through the status text itself, with no accent stripe reintroduced. A fifth pass fixed a layout bug the chevron introduced: the header row's `justify-between` had three direct children (title, status, chevron), which spaces every adjacent pair evenly — so the status label floated at the row's horizontal midpoint instead of sitting next to the chevron, landing at a different x-position on every row depending on subject-name length (again caught via user screenshot). Status and chevron are now grouped in their own nested flex child, so the pair moves as one unit pinned to the right edge. `ReadinessBar` itself is unchanged and still used on Collection Detail and Note Detail, where subject counts are small.
- **Review Set description expansion (frontend).** Goal headers and child Subject Plan cards now offer `Read more` only when their existing clamped curator description overflows, with a local `Show less` collapse control; short descriptions and all other clamped surfaces remain unchanged.
- **Mobile tab-bar refinements (frontend + backend).** Library/Public Library tabs retain their existing filtered return context (reusing the `?ref=` pattern and `notelib_public_library_return_url` sessionStorage key); Settings now persists a default-on `Show mobile navigation bar` preference that can suppress the bar everywhere. The icon-only compact variant for Note Detail and standard Challenge Quiz results was implemented, then reverted before release on a consumer-psychology review (`docs/claude-prompt/tab-bar-icon-labels-out/01-consumer-psychology.md`): it kept the bar's full footprint and all four tap targets while only stripping labels, paying a recognition/accessibility cost (labels used `display:none`, dropping the accessible name) for a chrome-reduction benefit it structurally couldn't deliver. The tab bar keeps its labels everywhere; quiz-taking screens still fully hide it via the pre-existing `useBottomViewportClaim` mechanism, unchanged. Pre-signoff pressure test flagged a plausible regression in the preference gate (`app-shell.tsx`'s `shouldShowMobileBottomTabs` reads component state, not the raw API response) — traced through and confirmed the state-construction layer already normalizes `mobileTabBarEnabled` via `!== false` before it reaches that check, so accounts predating the field correctly default to shown; added a regression test locking that in, since no test previously covered the `undefined`-from-server case.

## v0.50.0 - Mobile Bottom Tab Bar

**Status: Released**

Theme: add a persistent mobile bottom tab bar (Dashboard / Library / Review Sets / Public Library) to the authenticated app shell. Originally gated by a Fable App Shape proposal on evidence of a mobile-majority user base; a 2026-07-15 production device-mix pull found ~75% mobile vs. 25% desktop by distinct users, meeting that gate. Navigation-shape work, not a retention experiment — orthogonal to the concurrently-accruing v0.48.0 cohort read. Mid-release scope addition (2026-07-15): the 3 held instrumentation pulls from `retention-diagnosis-session-plan.md`'s Strategy checkpoint, folded in after an explicit decision to instrument rather than opened as their own version — analytics-collection only, no new UI surfaced from the data, so it doesn't confound the v0.48.0 cohort read either.

### Planned Scope

- **Mobile bottom tab bar (frontend).** Exactly 4 tabs (Dashboard, Library, Review Sets, Public Library), shown only below the `md` breakpoint, icon + text per the Mobile Button Rule. Coordinates with `AddToHomeScreenNudge`; the feedback control is mounted as a header icon rather than a floating launcher. The tab bar is hidden whenever exam focus or an active assessment claims the bottom of the viewport, so no two fixed-bottom elements stack.
- **UTM/referral tracking (backend + frontend).** No column or table captures acquisition source anywhere today. Capture UTM params (and referrer, where present) at signup and persist them against the account, so the diagnosis's acquisition-source pull becomes answerable.
- **Offline-fallback hit rate (frontend).** `frontend/public/sw.js` and `offline.html` already serve a fallback page on failed navigation but report nothing back. Fire an analytics event when the fallback actually serves, so real offline-usage volume becomes measurable (feeds Idea 9's evidence gate).
- **Browse-without-adopt: browse-side tracking (frontend).** `published-plans-page-client.tsx` (the official-plan-catalog page) fires zero analytics events today. Add a new `AnalyticsEventType` for viewing this page — do not substitute `EXAM_HUB_VIEWED`, which tracks a different funnel step (public-note discovery, not the Review Set catalog). The adopt side already exists (`STUDY_PLAN_ADOPTED` / `source_plan_id`); this closes the browse side so "browsed and found nothing" becomes distinguishable from "didn't browse at all."

Anti-drift: no change to desktop sidebar or mobile hamburger drawer contents/behavior; no 5th tab (Progress stays drawer/sidebar-only); no new global safe-area CSS variable (inline Tailwind arbitrary values only, matching existing usage); no coordination work for the "sticky Continue bar" from the original Fable proposal — it never shipped and isn't real code. Instrumentation additions are collection-only: no new dashboard, report, or admin view consumes this data in this release — that's the separate analytics-read work already queued. No behavior change to the existing `offline.html` fallback logic or the plan-adoption flow, only new events/columns.

### Shipped

- **Persistent mobile bottom navigation (frontend).** Authenticated mobile users now have icon-and-text tabs for Dashboard, Library, profile-aware collection navigation, and Public Library; desktop sidebar and the mobile hamburger drawer remain unchanged. The bar is suppressed for exam-focus and active assessment/review screens, while the home-screen-install nudge moves above its safe-area-aware height. The live feedback control was confirmed to be header-mounted, not a floating bottom launcher.
- **Retention-diagnosis collection points (backend + frontend).** New users now retain first-touch UTM/referrer fields from either signup path without later-login overwrites; the service-worker fallback records a best-effort anonymous `OFFLINE_FALLBACK_SERVED` event; and `/collections/published` emits `PUBLISHED_PLANS_VIEWED` once per view. These events are collection-only and have no new reporting surface yet.
- **Sitemap fix: Exam Hub pages now indexable (frontend).** `/exam`, `/exam/ale`, `/exam/pnle`, and `/exam/let` — the pages already built for exam-named search intent and already emitting `CollectionPage` structured data — were entirely missing from `sitemap.ts`, invisible to the sitemap Google actually crawls. Found while scoping an SEO strategy question; fixed directly since it's a same-day, isolated, zero-risk addition, independent of the broader SEO strategy work still pending a Fable session (see ROADMAP.md's Backlog Index).

Known limitations: `AddToHomeScreenNudge` (mounted outside `ExamFocusProvider` in the root layout) applies its raised offset unconditionally rather than only when the tab bar is actually rendered — on routes without the tab bar (public/marketing pages, active quiz sessions) it floats with a small unnecessary gap. Cosmetic only, no overlap. `docs/testing/mobile-ui.md` still describes a "floating Send Feedback launcher" that this audit confirmed is actually a header-mounted icon — pre-existing drift, not introduced here, left for a follow-up doc pass.

## v0.49.0 - Progress Page: Private Library Links

**Status: Released**

Theme: fix Progress page navigation to point at the learner's own private Library instead of the public one, for actions that are inherently personal ("study this next"). Orthogonal to the v0.48.0 retention experiments — deliberately not retention-flavored, so it doesn't confound the cohort read currently accruing on those two changes.

### Planned Scope

- **Per-subject Concept Mastery rows become links (frontend).** `progress-report-client.tsx`'s per-subject `ReadinessBar` rows are currently plain, non-interactive cards. Wrap them to link to `/library?subject={subject}` (private Library, filtered), scoped locally to this call site — not a change to the shared `ReadinessBar`/`ReadinessSummary` component's default behavior, since it's reused elsewhere (Collection Detail, Note Detail) without a link today.
- **"Weakest subject" CTA reconciled to the private Library (frontend).** `NextStudyCard`'s both branches currently link to `/public/library` for what's meant to be a personal "study this next" action: the `SUBJECT_FOCUS` branch (`?subject=`) and the course-program fallback branch (`?courseProgram=`). Both corrected to their private-Library equivalents (`/library?subject=`, `/library?cp=`).

Anti-drift: no backend change — both fixes reuse data and URL params Private Library already supports; no change to Public Library, which remains the correct destination for discovery-oriented flows elsewhere in the app.

### Shipped

- **Progress page now links to the private Library (frontend).** Per-subject Concept Mastery rows are now links to `/library?subject={subject}`, wrapped locally without changing the shared `ReadinessBar` component's default (unlinked) behavior anywhere else it's used. `NextStudyCard`'s "study this next" CTA (both the `SUBJECT_FOCUS` and course-program-fallback branches) now points at `/library` instead of `/public/library`, with copy corrected to match ("Study {subject} in your Library" instead of "Browse {subject} notes in the community") — the original copy and destination were a discovery-surface mismatch for what's meant to be a personal action.

## v0.48.0 - Retention Experiment: Open Loop & Digest Trigger

**Status: Released**

Theme: test the two co-dominant causes identified in the retention root-cause diagnosis (`docs/claude-prompt/retention-diagnosis-session-plan.md`) — dead trigger infrastructure and no open loop at first-session end — with two independently cheap, independently measurable experiments against the same cohort methodology used in the diagnosis.

Pre-kickoff data check (2026-07-17): pulled Resend's domain-wide open/click rates (`mail.notelib.app`, 639 emails, ~Jul 1–17) as a cheap sanity check before committing scope — 22.22% open, 0.94% click. This is domain-wide, not `INACTIVITY`-specific (no per-email-type tagging exists in `ResendEmailService` to decompose it, and the blend likely includes high-open transactional mail like verification/reset), so it does not answer the original gate question precisely. The `INACTIVITY` email's subject line is static and un-personalized ("Continue your study pack 📚"), so the exact number remains cheaply obtainable later via Resend's email log if needed — not pursued further here. What the domain-wide number does establish reliably even blended: sub-1% click-through is weak by any reasonable read, which is a real risk to the email-dependent experiment and the reason it's scoped to include CTA/content work below rather than a bare default-flip.

### Planned Scope

- **Open-loop session ending (frontend).** Lead experiment — channel-independent, so unaffected by the email-engagement risk above. End the first quiz on an explicit incomplete state ("2 of 9 concepts secured — the rest are best reviewed tomorrow") instead of a terminal score, testing the completion/narrative-gap hypothesis from the consumer-psychology diagnosis (Zeigarnik effect) directly.
- **Due-concepts digest trigger fix (backend + frontend).** Flip `due_concepts_digest_reminders_enabled` to default-ON for new signups only (existing users' explicit-FALSE state is untouched — no retroactive opt-in, no spam to a population that never chose this). Given the weak domain-wide click-through, this is scoped as more than a flag flip: review and strengthen the digest email's CTA/content as part of this work, not a bare default change.

Anti-drift: no new backend entity; reuses the existing `DUE_CONCEPTS_DIGEST` retention email type and `due_concepts_digest_reminders_enabled` column from `v0.46.0` (no new migration expected — a `DEFAULT` value change, if needed, does not require a new schema column); no new quiz mode, mastery signal, or scoring change — the open-loop ending changes only how the *existing* result is framed, not how it's computed; no commitment-device or pre-decided-return-action work (H1/H5 from the diagnosis stay explicitly deferred pending these two results); no retroactive email-preference changes to existing users.

### Shipped

- **Open-loop first-quiz ending (backend + frontend).** A learner whose first completed Quick Review or Challenge Quiz has missed tagged concepts now sees an `N of M concepts secured` header and a tomorrow-review prompt in Quick Review; perfect first scores, untagged quizzes, and returning learners retain `Your results`. The secured count is derived from the ungated client-side answer state so it stays plan-independent, while `QUICK_REVIEW_OPEN_LOOP_SHOWN` records only the rendered experiment exposure.
- **Due-concepts digest trigger and CTA (backend).** New email/password and Google signups now begin with the existing digest preference enabled, while every existing persisted preference stays unchanged and the weekly eligibility/cooldown rules remain intact. The digest now uses an action-oriented Dashboard button with a raw-link fallback; it still reaches only learners who have later accrued due concepts (`dueConceptCount > 0`), so it cannot move week-one return for someone who bounces after session one — that remains the open-loop experiment's job.

## v0.47.1 - V82 Migration Collision Hotfix

**Status: Released**

Theme: unblock production deploy, which has been failing since `v0.46.0` merged.

### Planned Scope

- **Fix duplicate Flyway migration version (backend).** `V82__email_budget_and_suppression.sql` (merged to `main` 2026-06-24, already applied in every real environment including prod) and `V82__user_due_concepts_digest_reminder_preference.sql` (introduced by the due-concepts-digest feature, `releases/v0.46.0`) both claimed version 82 — a rebase artifact: the `v0.46.0` branch was cut from `main` before `v0.45.2` existed and later rebased onto latest `main`, but the digest migration kept its version number from the older base instead of picking up the number already taken by the newer merge. Flyway refuses to start when it finds two migrations at the same version, which has blocked every deploy attempt since. Fix: renumber the never-applied digest migration to `V92` (the next free version after the existing highest, `V91`) — zero schema dependency either direction, confirmed via the local dev DB's actual `flyway_schema_history` (V82 = `email_budget_and_suppression` already recorded there; the digest migration has never successfully run anywhere, including locally).

Anti-drift: no schema/behavior change — this renumbers a migration file only, content unchanged.

### Shipped

- **Renumbered the never-applied due-concepts-digest migration from `V82` to `V92` (backend).** Resolves the Flyway startup collision that has blocked every production deploy since `v0.46.0` merged. Verified against the local dev DB's `flyway_schema_history` (ground truth, not assumption) that `V82__email_budget_and_suppression.sql` is the one already applied everywhere and the digest migration had never run anywhere — so the rename carries zero schema risk in either direction. Verified via a full backend test suite run and a real Spring Boot application-context startup against a persistent local DB (the same startup path Flyway validates against in production, which a mocked-DB test suite would not exercise). **Deploy note:** if a prior deploy attempt left a stale build artifact (`target/classes`) with the old duplicate-version `V82` file baked in, do a clean build (`./mvnw clean install`) before deploying — a non-clean build can still surface the old collision even after this fix.

## v0.47.0 - Conversion Audit Tier 4: Cleanup Batch

**Status: Released**

Theme: close out the conversion/retention UX audit backlog (`docs/claude-prompt/conversion-audit-prioritized-backlog.md`) with its Tier 4 items — low-impact, cheap cleanups explicitly meant to be batched together rather than shipped as standalone releases. Item 52 from this tier already shipped in v0.46.0.

Pre-scoping verification (2026-07-14, two passes) found the backlog's original routing needed three corrections: item 47 (Goal card sub-line label check) and item 50 (teacher-conditional Learner Level label qualifier) were both already fixed in prior releases (item 50 shipped in v0.45.0 — `frontend/app/onboarding/page.tsx:928` already has the qualifier) — both dropped, nothing to ship. Item 37 (carry Learn-article intent into signup) was initially dropped too — its originally-assumed mechanism (the exam-intent cookie + `goal-prompt-banner.tsx`'s exam-slug matching) doesn't fit, since Learn guides are category-keyed, not exam-slug-keyed. **Folded back in (2026-07-14) at a smaller scope** once all other items shipped: instead of the cookie/banner mechanism, it reuses the query-param intent pattern already shipped in this same release for item 43 (copy-note signup) — no cookie, no `goal-prompt-banner.tsx` changes. Full verification detail in the scoping conversation; source backlog unaffected. Also noted: item 48's onboarding-adopt-card component (`dashboard-study-plan-section.tsx`) is shared across 4 call sites (onboarding, Dashboard x2, Collections) — its "supplementary" reframing must be scoped to the onboarding context only (e.g. a context prop), not a blanket copy edit across all four.

Unlike prior releases, this batch routes through Codex prompts per-item rather than direct Claude Code implementation, despite every item being individually small enough for direct implementation under `CLAUDE.md`'s routing table — a deliberate token-budget choice for this release, not a routing-rule change.

### Planned Scope

All items have shipped — see Shipped below.

Anti-drift: no new backend entity, migration, or endpoint anywhere in this batch — every item confirmed frontend/doc-only during pre-scoping verification; no pricing/quota changes; item 37's original cookie/goal-banner mechanism and item 54's "High Quality" threshold redefinition stay explicitly out of scope (documented, not changed).

### Shipped

- **Learn signup-intent polish (frontend).** Learn article signup CTAs now carry a `learn` query intent through the existing signup/auth handoff, so the auth interstitial restates the visitor's reason for joining with free-study-guide copy. The existing public-note `copy-note` intent keeps priority, while unrecognized or absent intents retain the generic signup copy; no cookie or goal-banner logic changed.
- **Landing & Pricing polish (frontend).** Tightened mobile padding/spacing on the hero and pricing-preview sections; harmonized step language between the landing loop section and `/how-it-works` instead of duplicating it; retitled the "AI Critique" Learn guide to make clear it's part of Interview Practice, not a standalone feature; added a region note under the pricing display, reusing the existing single-currency-per-region resolver (no ambiguity bug — this was purely a missing note, not a broken currency display).
- **Discovery & Library polish (frontend).** Unified "adopted" vocabulary on `Adopted` across Collections, Collection Detail, and Dashboard (dropping the redundant "In your library" chip); Dashboard's Matching Study Plan section now shows a guidance card prompting the user to set their courseProgram instead of silently rendering nothing; documented the in-app subject filter vs. subject-landing-page split as intentional; Course/Program now renders as plain metadata text instead of a badge on shared note cards (Subject badge unchanged); documented the "High Quality" badge's existing thresholds (≥5 copies and ≥10 views) rather than changing them.
- **Public Note Detail polish (frontend).** Added a client-side author mini-card on public notes using the existing public-profile reads, with a name-only fallback for private or unavailable profiles; visible `Home → Public Library → Subject → Note` breadcrumbs plus `BreadcrumbList` JSON-LD; copy-note signup intent now reaches the auth interstitial and restates “save this note to your library”; and non-owner public profiles now close with a creator-filtered Public Library browse link. No new backend DTO, endpoint, or schema surface.
- **Onboarding copy polish (frontend).** The shared plan-adopt card now receives onboarding-only supplementary framing through an explicit `context="onboarding"` prop, leaving Dashboard and Collections copy unchanged. The Dashboard learner-level prompt now invites adjustments instead of implying a required value is missing, and Step 4 confirms the newly generated Study Pack is saved to the learner’s library alongside its existing back-navigation notice.
- **Doc hygiene (docs only).** `seo.md` now documents subject landing pages' `CollectionPage` structured data; `public-library.md`'s More Filters modal order corrected to match the actual current filter set (added the existing `Study Pack Ready` toggle, removed the inaccurate `Learner Level` claim — Learner Level is not a Public Library filter); documented that filter mode has no pagination today (single page load of matching results).

## v0.46.0 - Retention Depth: Due-Concepts Digest & Exam Pacing

**Status: Released**

Theme: deepen retention past v0.44.0's conversion-audit-driven fixes with two Fable-sourced new-capability ideas from the "New Capability Ideation" session (`docs/claude-prompt/new-capability-out/01-new-capability-ideation.md`) — an out-of-app due-concepts email digest and an owned-content exam-date pacing plan. Both reuse existing infrastructure rather than build new pipelines.

**Scope broadened (2026-07-14):** the original two-item scope shipped fast enough that the release read as too small on its own; folded in the rest of the Fable-sourced backlog material to round it into a real marketing-moment release — originally Idea 8 (Concept Flashcards) plus the full 7-item Polish list from the separate "App Shape, App-Like UI & Companion Authenticity" session (`docs/claude-prompt/app-shape-session-plan.md`). **Correction (same day, #1):** Concept Flashcards turned out to already be a fully shipped feature (`docs/features/flashcards.md` — unscored flip-through, Key Concepts tab entry point, no session/scoring; there's even an existing public preview) — Fable's write-up wrongly listed it as unbuilt. Dropped and replaced with item 52 from the existing Tier 4 conversion-audit backlog (`docs/claude-prompt/conversion-audit-prioritized-backlog.md`), a different but complementary and already-well-scoped retention item: echoing Weekly Countdown/This-Week pacing at quiz-session completion. **Correction (same day, #2):** direct investigation found 3 of the 7 App Shape Polish items were misclassified — they need real new backend surface area, not a polish-tier edit: the Review Set filter facet needs a note→collection association that doesn't exist anywhere in the note-list API; the Result-Screen Companion Bridge has a premise problem (Companion content belongs to Collections, not Study Packs, and no note→collection reverse lookup exists to resolve which collection's Companion to show); the feedback/issue-report digest needs new schema (`FeedbackEntity` is a flat, unscoped global table with no note/collection link), and its paired staleness flag has no target at all (the "Struggle Map" evidence panel it was meant to attach to doesn't exist anywhere in the codebase). All three dropped back to their backlogs as properly-scoped future work rather than folded in as "polish." Idea 6 (Photo Capture) was considered and explicitly excluded — it's Core Feature-sized (new image upload + vision-extraction infrastructure), not a fold-in.

### Planned Scope

All items have shipped — see Shipped below.

Anti-drift: no 6th quiz mode; no auto-assembled or gap-filling review plan (owned content only, explicitly not Smart Review Planning); no freetext exam identity; no per-learner runtime personalization beyond the existing PRO tier design; no pricing, paywall, or quota numbers introduced by this release (tier *direction* only, per Fable's own guardrails); no image-capture/vision-extraction infrastructure (Idea 6, explicitly excluded from this fold-in); no new backend entity, migration, or endpoint (the 5-item polish batch is entirely frontend-only, reusing existing data). Full source material in `docs/claude-prompt/new-capability-out/01-new-capability-ideation.md`, `docs/claude-prompt/app-shape-session-plan.md`, and `docs/claude-prompt/conversion-audit-prioritized-backlog.md`.

### Shipped

- **Weekly Due-Concepts Email Digest (backend + frontend).** Added `DUE_CONCEPTS_DIGEST` to the existing weekly retention pipeline. Opted-in users with due concepts across owned Study Packs receive a Dashboard Today Focus link, total due count, and up to three highest-count Study Pack titles; empty and cooldown-blocked candidates are skipped. The new preference defaults off, has a 7-day configurable cooldown, is editable in Settings, and uses the isolated `DUE_CONCEPTS_DIGEST` unsubscribe category.
- **Board Exam Dashboard pacing (backend + frontend).** The profile exam-date field and basic countdown were already shipped; this adds only a nullable Dashboard overview pacing result for BOARD_EXAM users with a future exam date and owned due concepts. It sums due concepts across all owned Study Packs through `ConceptHealthService`, rounds them into a daily target, and renders that target in the existing Dashboard countdown card. Empty, past/today, and unavailable states retain the prior countdown fallback. Review Set/Goal detail remains deliberately untouched and keeps its separate collection target-date pacing.
- **App-feel polish batch (frontend, 4 items shipped, 1 reverted — no new backend entity/migration/endpoint).**
  - Echo Weekly Countdown pacing at session completion — when the learner has a Primary Review Set with a target date, the result screen (`PostSessionNextStep`, inline in all three session-mode pages) adds one line of This-Week pacing echo, following the same general-nudge pattern as the existing `GoalNudgeCard`. Reuses `GET /collections/{id}/goal`'s already-computed `weeksRemaining` — no new backend call shape.
  - Shared note-card press feedback — `motion-pressable` applied to every note-card wrapper that had none (Dashboard community notes, public note detail's "More in Subject" grid, public profile's note grid); Private/Public Library's existing bespoke press-scale (`getBrowsingCardClassName`, used by 5 unrelated surfaces) was left untouched rather than risk a wider blast radius.
  - Skeleton-first initial load for Note Detail — reuses the existing `StudyPackGeneratingCard` skeleton (parameterized eyebrow/heading/hint) instead of plain "Loading note..." text. Review Sets list already had this.
  - Collapse-by-default Note Detail sections — Recent Sessions and the ready-state Performance Overview stats now collapse by default (empty/generating/failed states stay always-visible, nothing to hide), replicating `CompanionDisplayCard`'s "View Full Guide" toggle shape.
  - **Reverted the same day:** sticky search/filter toolbar on Private Library and Public Library. The implementation stuck the *entire* toolbar Card (search, More Filters/Sort, note count, Saved filters, active-filter chips, and the full Subject-chip row) rather than a compact single row — on mobile this consumed most of the viewport while scrolling, squeezing note results into a sliver. User-reported via screenshot; a follow-up Fable consult recommended abolishing rather than narrowing it to a compact row, since Library search is a session-start action (search/filter re-renders the list from the top, so a sticky input's positional benefit is rarely exercised while scrolling results) — unlike the good sticky-bar precedent elsewhere in the app (quiz top bars), which earn their pixels by showing continuously-relevant live session state. Reverted to the pre-change plain (non-sticky) toolbar Card on both pages.

---

## v0.45.2 - Public Plan Preview Rollup Fix

**Status: Released**

Theme: fix the last remaining gap in v0.45.1's Goal-collection rollup fix — a third code path (`getPublic()`, backing the "Preview this plan" panel on public plan cards) that v0.45.1's fix never touched, still showing "0 of 0 notes practice-ready" for a Goal even when its children hold real, ready notes.

### Planned Scope

All items have shipped — see Shipped below.

Anti-drift: no new backend entity, migration, or endpoint — read-only aggregate fix reusing v0.45.1's existing `findByParentCollectionIdIn` repository method.

### Shipped

- **Public plan preview rollup fix (backend).** `NoteCollectionService.getPublic()` (backing `GET /collections/public/{id}`, the plan-preview panel on `PublicStudyPlanCard`) now flattens items across a Goal and its children — reusing v0.45.1's `findByParentCollectionIdIn` unfiltered (no child-visibility re-filter, avoiding the same pitfall v0.45.1 had to guard against) plus a new batched `NoteCollectionItemRepository.findByCollectionIdInOrderByCollectionIdAscPositionAsc`. `progress`/`readyCount` are unchanged downstream, already correctly derived from `items`. Childless collections are unaffected.
- **Plan preview note-list cap (frontend).** With the rollup fix now surfacing every note (some production plans have 40-52), the uncapped preview list made the expanded card unbounded and broke grid alignment with sibling cards. The preview note list now caps at 5, showing all notes when the total is 6 or fewer (never hiding just one item behind an affordance) and a muted "+ N more notes" line otherwise. The "Preview this plan" toggle also now shows the total note count ("Preview this plan · 42 notes") so the cap doesn't read as concealment.

---

## v0.45.1 - Study Plan Collection Fixes

**Status: Released**

Theme: fix three pre-existing collection/discovery defects surfaced during v0.45.0 pre-signoff review — a Goal-collection note-count rollup that always shows 0 when notes live only on child Subject Plans, a Published Plans backlink that ignores how the user actually arrived, and a dead end where users with a primary study plan have no path back to the full official-plan catalog.

### Planned Scope

All three items have shipped — see Shipped below.

Anti-drift: no new backend entities, migrations, pricing tiers, or quota changes; the rollup fix added one new repository method and no new endpoint. All three items were routed to Codex per the user's explicit token-budget preference this release, superseding this release's original Claude-Code-direct routing call for items 2 and 3.

### Shipped

- **Goal-collection note-count/readyCount rollup fix (backend + frontend).** Added the anonymous-safe `NoteCollectionRepository.findByParentCollectionIdIn(List<UUID>)` lookup. `NoteCollectionService.list()` and `listPublic()` now fetch all direct children in one additional query, run their existing item/ready batch loaders once across the top-level-plus-child union, and sum each child's counts into its Goal without a child visibility filter. Dashboard now shows the returned Goal note total alongside its Subject Plan count instead of suppressing the formerly misleading zero.
- **Published Plans backlink and persistent catalog-browse entry point (frontend).** `/collections/published`'s back link now reads an allowlisted `?ref=` query param (`/dashboard`, `/public/library`, `/collections` and their sub-paths) to return to wherever the user actually came from, falling back to today's Dashboard/Public Library default otherwise; the allowlist stays specific-prefix so it can't be used as an open redirect. The four existing links into that page (`dashboard/page.tsx` both call sites, `collections-page-client.tsx`, `dashboard-empty.tsx`) now send their own path as `ref`. Separately, `/collections` now shows an always-visible "Browse official plans" link in its header regardless of whether the user has a primary study plan set — the Dashboard recommendation card's existing primary-plan suppression (`docs/features/collections.md:124`) is unchanged.
- **Official badge dark-mode contrast fix (frontend).** `PublicStudyPlanCard`'s `Official` badge used a solid `bg-blue-100`/`dark:bg-blue-950/50` fill that broke against dark theme's card background, reading as an ill-fitting dark navy blob. Switched to the same border + opacity-tinted background pattern (`border-blue-500/30 bg-blue-500/10`) already used by every other identity badge in the app (`Primary`, note-ownership, readiness-stat chips), which blends correctly in both themes.

---

## v0.45.0 - Conversion Audit Tier 3 — Landing, Pricing & Discovery Polish

**Status: Released**

Theme: ship Tier 3 of the same 7-session conversion/retention UX audit that drove v0.44.0 (`docs/claude-prompt/conversion-audit-out/`, consolidated in `docs/claude-prompt/conversion-audit-prioritized-backlog.md`) — 18 medium-impact, lower-urgency items (#19–36), all copy/composition/UI polish with no new backend entities, migrations, or pricing/quota changes.

### Planned Scope

All 6 thematic PRs (landing, pricing, note detail, public plan card, onboarding/Dashboard guidance, Public Library) plus the two post-merge follow-ups have shipped — see Shipped below.

Anti-drift: no new backend entities, migrations, endpoints, pricing tiers, or quota changes. All items route to direct Claude Code implementation per `CLAUDE.md`'s task-routing table.

### Shipped

- **Onboarding and Dashboard guidance polish (frontend).** Teacher onboarding now labels Learner Level as the default quiz difficulty. Dashboard guidance is unified through the single-slot `pickActiveGuidance` picker: a post-completion topic reminder takes precedence, teacher introduction remains in the same slot, and returning learners receive a spaced-review rhythm tip. The previously scoped Professional/Board-Taker Course/Program helper examples were deliberately not changed because they had already shipped.
- **Returning-user Dashboard composition follow-up (frontend).** Completed the remaining Tier 3 Dashboard item by moving the existing Quick Review card below Usage / Progress after the already-loaded `hasCompletedSession` signal is true; first-time Student and Professional dashboards retain their existing Quick Review-before-usage order. No cards, requests, or guidance rules changed.
- **Public note detail polish (frontend).** The non-owner copy flow now presents one quiz-first primary action plus `Add to Library` and share secondaries, removing the competing editable-draft CTA. The detail page adds a canonical creator-filtered `More from {Display Name}` route without a backend endpoint. (The sibling `More in {Subject}` module was scoped in this same PR but not delivered — see the post-merge audit fix bundle and the follow-up bullet below.)
- **Public note related-notes follow-up (frontend).** Completed the previously missed `More in {Subject}` half of the v0.45.0 discovery module. It queries the existing subject-filtered public-notes endpoint, removes the current note, renders up to three shared public-note cards only when at least two related notes remain, and links to the canonical subject landing page; empty, thin, and failed results stay silent.
- **Public Library filter polish (frontend).** Generic no-results recovery now offers `Remove last filter` alongside a full clear, and the More Filters sheet adds a reversible `Study Pack Ready` toggle. The toggle filters the complete already-loaded public result set client-side by the existing study-pack status, so no endpoint, pagination, or schema change was needed.
- **Pricing trust polish (frontend).** Plus and Pro bullets now lead with what regular study and exam-prep learners can accomplish, the comparison table adds a `Best for` choice row, and pricing answers documented pass-end, manual-renewal, and exceptional-refund questions (payment methods intentionally remain omitted because no policy is documented). The generic Free upgrade fallback now consistently uses `Upgrade to …`, while context-specific and renewal CTAs stay intentionally value-framed. Near-limit banners retain their quotas and exhausted states but frame remaining generic credits as continued monthly progress.
- **Public plan card polish (frontend).** Public-plan cards now show an `Official` identity badge because the public list only contains admin-published plans, and their Start/Continue CTA now explains that adoption opens a private, editable library copy. No backend field or behavior changed.
- **Post-merge audit fix bundle (frontend).** The 5 Tier 3 PRs above were merged without the usual per-PR `/audit-diff` gate; a full post-merge audit (5 parallel agents, each PR's diff checked against its own prompt's acceptance criteria, plus a firsthand `npm test`/`tsc` run) found the branch's test suite was actually red. Fixed: the near-limit banner's reframed copy (pricing PR) had a wider blast radius than that PR's own audit checked — two other consumers' tests (`note-editor-page-client.test.tsx`, `bulk-generation-page-client.test.tsx`) asserted the old string and were failing; the public note detail PR quietly removed a third CTA (`Copy Study Pack`, never asked for in its prompt) without updating the pre-existing test asserting it, also failing. Both fixed by updating the stale assertions to the new, intentional copy/UI rather than reverting either change. Also fixed: `note-detail.md`/`public-notes.md` had drifted to claim a same-subject "More in {Subject}" related-notes module exists — it was never built, only the "More from {Display Name}" link shipped; docs corrected to describe the actual shipped state. And the Public Library filter PR's tag-browser sub-modal committed tag changes without updating `lastChangedFilter`, so "Remove last filter" could target the wrong filter after a tag change made through that specific path — fixed. The "More in {Subject}" module and Tier 3's returning-user Dashboard card re-weighting (onboarding/Dashboard guidance PR, item d) were both confirmed never built during this audit; tracked as immediate follow-up PRs, not deferred.
- **Landing page conversion polish (frontend).** Exam hubs now show a one-paragraph product-value strip on every hub (not just empty ones), and the zero-note empty state adds a link to the matching Learn category alongside the existing intent-preserving signup CTA and Public Library link. The landing differentiation table was rewritten from four abstract contrasts to three concrete, felt ones (e.g. "gone when the chat resets" vs "saved in your library, reusable"). Demo access (`/demo`, previously unlinked from any public surface) is now a required hero CTA and a public nav item, not optional fine print. A new landing FAQ section (4 pre-signup objections) emits `FAQPage` JSON-LD built from the same data that renders the visible cards, and Learn articles — previously the only public SEO surface without structured data — now emit `Article` JSON-LD. No backend changes; corrected a pre-existing doc-drift in `landing.md`'s public-nav list along the way (it was missing `Exam Hubs`, already live since v0.44.0).

---

## v0.44.0 - Conversion & Retention Polish

**Status: Released**

Theme: ship the highest-confidence findings from a 7-session conversion/retention UX audit (`docs/claude-prompt/conversion-audit-out/`, consolidated in `docs/claude-prompt/conversion-audit-prioritized-backlog.md`) — closing a verified backend gap in return-visit progress tracking, plus the audit's Tier 1 (high-impact, low-effort) UI/UX items across landing, pricing, public notes, onboarding, and Public Library.

**Scope broadened mid-release (2026-07-12):** a follow-up diagnosis of the actual dominant acquisition channel (public-note links shared into Facebook study groups, not the landing page — `docs/claude-prompt/facebook-entry-funnel-out/01-facebook-entry-funnel.md`) found and confirmed, via direct code trace, that every copy-on-signup user is redirected to `/onboarding` before ever seeing their copied note or its auto-launched Quick Review — a universal defect in the core conversion mechanic, not a channel-specific edge case. Folded into this release rather than deferred, per the same precedent as v0.43.1's pre-signoff trust-bug fix.

**Scope broadened again (2026-07-12):** Tier 1 shipped in full (see Shipped below); rather than closing the release, Tier 2 of the same conversion-audit backlog (`docs/claude-prompt/conversion-audit-prioritized-backlog.md`, items 12–18) is folded in as this release's next slice instead of opening a new version:

- 12. Real social-proof strip (live counts or named-exam focus line)
- 13. Pass-expiry renewal prompt (expiry-approaching notice + one-tap renewal CTA)
- 14. Pre-adopt plan preview (read-only, existing `GET /collections/public/{id}`)
- 15. Practice-readiness metadata line on public plan cards ("N of M notes practice-ready")
- 16. Due concepts surfaced in the Dashboard's top "what now" slot
- 17. Show due-concept signals to Free users, action stays gated
- 18. Course/Program discoverability (chips/rail) + fix search-scope coverage

### Planned Scope (Tier 2 addition)

Items 12–18 have shipped below. The pass-expiry reminder uses the existing `premiumEndsAt` response and hosted checkout flow; no new backend endpoint or trigger was needed.

### Shipped

- **In-app pass-expiry renewal notice (frontend).** Active Plus and Pro users now see a Settings renewal notice in the same 7-day (6–8 days) and 1-day expiry windows as the existing email cadence. It uses the already-loaded `premiumEndsAt`, is separately dismissible per pass and stage, and sends the configured current-tier CTA through the existing hosted checkout handler. Copy stays explicitly one-time-pass and no-auto-charge; no backend trigger was added.
- **Landing live social proof (frontend).** The landing page now displays the real public-note total from the existing unauthenticated `GET /notes/public?size=1` response directly below the Hero. The five-minute server-side fetch renders a focused-review usage line only when the live count is valid; unavailable data is omitted rather than estimated, and no testimonial or capability claim was added.
- **Due-concepts Today Focus and Free signal visibility (backend + frontend).** Dashboard's Today Focus card now surfaces real, deterministic due concepts when no session is resumable, each concept linked to its source note. Every plan can see accurate due/mastery status on the Key Concepts tab instead of a due concept falling back to `Not started`; the card keeps the established Adaptive Practice / Revisit Note / locked-action split. This is visibility only: Adaptive Practice feature access, quotas, and paid action gates are unchanged. Audit found `TodayFocusCard` had never actually been wired into the live Dashboard before this PR despite having five resolver states — reviving all five as delivered would have duplicated the existing Continue Studying and Focus Areas sections, which independently compute the same "resumable session" and "weak concepts" signals. Scoped the Dashboard render to the `DUE_CONCEPTS_REVIEW` type only, the one genuinely new signal (see Known limitations).
- **Public Library Course / Program discoverability (frontend).** The existing Course / Program helper card now shows the six highest-count canonical programs as one-tap chips, applying the same slugged filter URL as the full filter sheet. Public-note search already covers program and tag values, so vague exam queries such as `PNLE` find program-matched notes without a redundant backend predicate.
- **Public Study Plan pre-adopt preview (frontend).** Every public plan card now offers an optional, unauthenticated read-only preview from the existing `GET /collections/public/{id}` endpoint, showing the actual available note titles, subjects/sections, course/program, estimated study time, and live practice-ready ratio before adoption. A fetch failure is explicit and retryable without disabling the existing Start/Continue action; empty source items are stated plainly.
- **Public Study Plan practice-readiness metadata (backend + frontend).** Public plan list and detail responses now return a live `readyCount`, calculated with the existing `STUDY_PACK_READY` resolver. `PublicStudyPlanCard`, used on the Published Plans page (`/collections/published`, both the matched and Browse All sections), shows `{readyCount} of {itemCount} notes practice-ready` as metadata—not a badge—so zero, partial, and fully ready inventory is visible before adoption; older cached list responses retain their existing item-count display. Dashboard's own plan section (`DashboardStudyPlanSection`) is a separate, hand-rolled card that does not consume `readyCount` — it was never in scope for this line and does not show the ratio; pre-signoff pressure test caught this release note overclaiming that surface, corrected here rather than expanding scope this late.
- **Copy-on-signup onboarding-intercept fix (mid-release scope addition, frontend).** Fixed the confirmed universal defect where a newly verified public-note copier was redirected to `/onboarding` before their copied note or auto-launched Quick Review could render. Successful copy-on-signup now carries a distinct local completion marker that preserves the copied-note landing, then collects Profile Type, Learner Level, Course / Program, and optional Board Exam date through a dismissible Dashboard card; normal onboarding and the legacy profile-type re-prompt remain unchanged, and unavailable marker storage falls back to the existing redirect. A pre-signoff pressure test (multiple Explore agents + `advisor()`, per `CLAUDE.md`'s release-shape gate) found the initial fix only covered the email-verification signup path — the independently implemented Google OAuth signup path (`app/auth/page.tsx`) never set the new marker, so Google-signup copiers hit the identical intercept bug. Confirmed the same root cause applies there (the OAuth path also persists the user to local auth state before navigating) and closed the gap with the same marker call, plus a new regression test covering the Google signup + copy-intent flow.
- **Pricing trust-copy and quota-surface analytics (frontend).** Added the shared no-auto-charge reassurance directly below paid pricing-card, paywall, and Settings CTAs while retaining the broader footer trust context. Near-limit quota banners and the shared Study Pack-limit modal now emit one-shot, source-tagged view events plus source-tagged upgrade clicks, including distinct Study Pack, note-generation, and OCR entry surfaces; existing Pricing, Settings, and paywall tracking sources remain unchanged.
- **Landing hero, audience links, and Exam Hub conversion path (frontend).** Aligned the hero and canonical metadata with NoteLib's notes-library-to-quizzes promise; clarified the nearby Board Exam Mode callout as a timed full-exam simulation without misrepresenting the Study Pack screenshot. Target User panels now link to their matching Learn categories (with Board Exam also linking to Exam Hubs), and zero-note Exam Hubs preserve their established signup intent path beside the Public Library fallback.
- **ConceptHealth tracking for Quick Review (backend).** Completed Quick Review sessions now derive fully-correct and missed concepts from the base Study Pack quiz plus persisted selections, then write the same per-user, per-Study-Pack `concept_health` records used by Challenge Quiz and Adaptive Practice. Completion remains state-guarded and transactional, so a duplicate completion does not write twice and a ConceptHealth failure rolls back the completed session.
- **Public note and onboarding conversion hooks (frontend).** Quick Check completion now uses an outcome-framed prompt that reuses the established copy-intent flow without creating anonymous progress; ready public notes explain that their visible study tools came from the source note. Onboarding Step 5 now names the learner's topic when available, sets a return expectation, makes `Open your Study Pack` the clear primary action, and keeps Dashboard as a quiet secondary path.
- **Public Library Recommended ranking and Official-plan bridge (frontend).** Filter mode now defaults to the existing decay-adjusted discovery score when no sort is specified, while explicit Newest, copies, views, and title sorts retain canonical URLs. A Course / Program filter now silently checks the existing public-plan list and links to Official Study Plans only when a matching plan exists.

**Known limitations (from the pre-signoff pressure test, not fixed in this release):**
- Dashboard's `NearLimitBanner` renders with no `onUpgrade` handler, so PLUS/PRO users who hit the near-limit warning have no upgrade CTA on that surface. Confirmed pre-existing (introduced in `0fb0c5be`, an ancestor of this release branch) rather than a regression from any v0.44.0 PR — tracked as a conversion-audit backlog item, not fixed here.
- The pressure test's Dashboard-composition agent flagged a theoretical simultaneous render of `DashboardPersonalizationPrompt` and the new lightweight profile-completion prompt (both soliciting Learner Level). Traced the marker's full lifecycle (one set site, one clear site, clear always paired with `learnerLevel` being written in the same submit call) and found no real user flow reaches that state — only a hand-nulled test fixture does. Not fixed; noted here in case a future change to either prompt's gating condition makes it reachable.
- `DashboardService.getTodayFocus()` and `getContinueStudyingRecommendation()` are two independent, never-reconciled resolvers that both compute "resume the latest in-progress Quick Review session" as their top priority; `resolveTodayFocusWeakConcepts()` (from the latest Quick Review) and Focus Areas' weak-concepts card (from Challenge Quiz) similarly overlap in purpose from different data sources. Mitigated for this release by only rendering `TodayFocusCard` for its one new state (`DUE_CONCEPTS_REVIEW`); the other four states remain live in the resolver but unrendered. Not fixed — the duplicate resolvers should eventually be reconciled into one, but that's a larger refactor than this release's scope.
- The backend's `isDue` definition (used by `resolveTodayFocusDueConcepts`) treats a concept with no `lastCorrectAt` as immediately due, not just spaced-repetition-elapsed — a long-standing definition already used by Adaptive Practice generation, but this release is the first time it reaches a Dashboard card copy string ("N concepts are due for review"). A first-run or copy-on-signup user can see review-language for material they've never opened, and it now outranks the `STUDY_SUGGESTION` "Start your first review" fallback. Pressure-test finding; explicit product call was to ship the copy as-is rather than special-case the never-studied state, since it's the existing backend semantics newly surfaced rather than a new inconsistency. Revisit copy if it proves confusing in practice.

---

## v0.43.1 - Companion Mentor Tips

**Status: Released**

Theme: let the *authored* Companion content participate in the Coach experience the way live signals already do — small, individually-surfaceable, action-linked "Mentor Tips" instead of an article to read start to finish. A real content-model change (backend + authoring-UI), unlike v0.43.0's frontend-only fast-follows.

### Planned Scope

- **Content model (backend).** New Mentor Tip shape with its own identity, optional linked action, and optional deterministic surfacing condition — `CompanionContent`'s existing five long-form markdown fields cannot be individually surfaced without a new entity/DTO shape.
- **Authoring (backend + frontend).** Extend the authoring modal, v0.42.0's per-section AI-assist, and the structure-staleness snapshot to cover Mentor Tips. Mandatory human review before publish, same as every other Companion write path.
- **Action-linking (backend + frontend).** Curator-tagged, not inferred — the curator sets the linked action (e.g. "Review due concepts") when authoring the tip. No per-view LLM call.
- **Deterministic surfacing (backend + frontend).** Date/progress-rule triggers only (e.g. "within 2 weeks of exam date," "after N subjects completed") — no learning-pattern/LLM-driven selection.
- **"View Full Guide" stays reachable regardless of trigger state (frontend).** A learner must never permanently miss a tip because its surfacing condition never fired for them.

Anti-drift: "Curation, never generation" unchanged — learner never receives an auto-generated tip; publishing stays non-autonomous; surfacing logic must stay deterministic/rule-based, not adaptive/LLM-driven (that tier is reserved for the gated PRO Personalization candidate in `docs/product/ROADMAP.md`); no change to the existing five Companion sections' content or order.

**Known low-volume caveat, checked at kickoff, not assumed:** dev DB shows only 1 PUBLIC/Official top-level Review Set currently carrying an authored Companion (2 companions total across 7 top-level collections; the other sits on a PRIVATE collection). `docs/product/ROADMAP.md`'s v0.43.1 candidate section flagged this explicitly as a go/no-go check. Decision: proceed — this is dev/local data that may not reflect prod authored volume, and the content-model/authoring-UI work has standalone value even before curators have built up tip inventory.

### Shipped

- **Mentor Tips content model.** `CompanionContent` now carries `mentorTips` inside the existing `note_collections.companion` JSONB payload. No new table, migration, endpoint, persisted learner state, progress signal, or Companion eligibility rule was added.
- **Curator authoring and validation.** ADMIN Companion authoring can add, edit, remove, and save Mentor Tips with a fixed linked action (`None`, `Continue Studying`, `Review Due Concepts`, terminal exam/builder action) plus an optional deterministic surfacing condition. Invalid negative thresholds are rejected through the existing inline modal error path.
- **AI draft extension, not auto-publish.** Per-section Companion generation now supports `MENTOR_TIPS`; the LLM returns draft tip title/body only, with `linkedAction=NONE` and `surfacingCondition=null`. Curators still review, configure, and save through the existing full-replacement Companion write path.
- **Deterministic Coach surfacing.** The collection detail page selects at most one eligible Mentor Tip in authored order and renders it near `TodaysFocusCard`, resolving its linked action against the already-computed primary action, due-concept review link, or terminal action. No per-view LLM call or extra fetch.
- **Full guide escape hatch.** `CompanionDisplayCard` now lists all authored Mentor Tips as a sixth expanded guide section after Resources, regardless of whether each tip is currently eligible to surface near Today's Focus. Existing five-section order is unchanged.
- **Staleness/adopt parity.** Companion structure staleness remains child/note-membership-only, so Mentor Tip text/config changes do not mark content outdated. Cross-owner adopt/copy carries Mentor Tips the same way it carries the rest of Companion content.
- **Fix: Pro-only paywalls no longer offer dead-end Plus checkout.** Board Exam Mode, Long Exam, Difficulty Selection, and Interview Practice paywalls now map to Pro-specific CTA labels and also gate the Plus plan card's selectability plus `startCheckout` itself. This closes the trust bug where fixing only the label/context layer still left the visible Plus card clickable for features Plus does not unlock.
- **Fix: review-timing upsell no longer opens the Adaptive Practice paywall.** Free users tapping the Note Detail review-timing upgrade row now see dedicated "See your review timing" copy with Plus as the primary path and Pro still selectable, matching the actual Plus/Pro entitlement split.
- **Help Center coverage for current review-set guidance.** Added a Learning Companion guide covering Companion, Today's Focus, Mentor Tips, the collapsed Full Guide, and official-author workflow. The Study Plans & Collections guide now also explains Primary Review Sets and target-date / Weekly Countdown pacing.
- **Fix: stale "This Week" references caught at pre-signoff audit.** The new Study Plans & Collections Help copy and `docs/features/collections.md` still described the standalone weekly-countdown card that v0.43.0 removed. Both now describe the actual current behavior (countdown line inside the Progress card, daily budget inside `TodaysFocusCard`'s coaching sentence); `docs/features/collections.md`'s page-hierarchy description was also updated to match the v0.43.0 Coach/Progress/Companion order. No behavior changed — documentation only.

---

## v0.43.0 - Companion Coach Experience

**Status: Released**

Theme: give the Review Set detail page a coach-voice presentation layer over the Companion and an already-loaded live signal (whether a primary action remains) — frontend-only, no new engine, no new backend.

### Planned Scope

- **Coach-voice terminology mapping (frontend).** A static section-key → coach label/icon map, same shape as `getCollectionLabels`. Applied in `CompanionDisplayCard`; strictly order-preserving — renders sections in the curator's authored sequence, no reordering.
- **Coach-voice composition of existing live signals (frontend).** `TodaysFocusCard` merges the already-resolved primary action (`primaryStudyAction`/`getNextPlanAction`), target-date pacing, due-concept review, and terminal exam/builder actions into one Coach surface above Progress and the authored Companion. No new data fetch.
- **Curator-authoring guidance note (docs).** Short addition to `docs/features/companion.md` so authored prose and the new coach framing don't visually fight each other.
- **"View Full Guide" collapse (added mid-release, frontend).** `CompanionDisplayCard`'s five sections stop rendering inline on the collection detail page; they move behind a "View Full Guide" disclosure below Today's Focus and Progress. No new data, no new persisted state. See `docs/product/ROADMAP.md`'s "Coach vs. Companion" refinement for why.

Anti-drift: no reordering of authored Companion sections (narrative-flow reason — today's long-form sections assume reading order; this is specific to the current content shape, see `docs/product/ROADMAP.md`'s v0.43.1 candidate section); no generation — relabeling is not synthesis, "Curation, never generation" stays locked; no new backend, endpoint, or persisted state; does not reopen Timeline/Checklist as authored prose (stays live-feature embeds per v0.42.0); no Ask Companion, no Personalization, no nav/Dashboard change; labels continue through `getCollectionLabels`.

### Shipped

- **Coach-voice terminology mapping.** `COMPANION_COACH_HEADINGS` in `collection-detail-page-client.tsx` maps each of the five Companion sections to a coach-voice heading (Overview → "🗺️ What this covers", Study Strategy → "🧭 How to study this", Common Mistakes → "⚠️ Avoid these traps", FAQ → "💬 Common questions", Resources → "📎 Extra resources"). Order and authored text unchanged.
- **`TodaysFocusCard` Coach hierarchy.** Replaced `PrimaryActionCard` and `CompanionCoachIntro` with one top-of-page Coach card that answers "what should I do next?" in one glance: primary action title, `Continue Studying`, deterministic pacing/encouragement copy, and Quick Actions for due-concept review plus the existing terminal exam/builder action. Quick Actions render even when no primary note action remains, preserving exam/builder access in caught-up states. Coach encouragement is no longer gated on authored Companion content; missing/empty Companion hides only the Companion card.
- **Deleted `GoalWeeklyCountdownCard`; redistributed pacing data.** Weeks remaining and concepts remaining now render in a generic compact `ReadinessSummary` `countdown` slot on the Progress card when a target date exists. Today's concept budget now appears only in `TodaysFocusCard`'s pacing sentence when the same target-date state is present.
- **Progress/Guidance placement.** `ReadinessCardFooter` now keeps only `View full progress`; `Review Due Concepts` moved into Today's Focus Quick Actions. The post-adopt target-date `GuidanceTip` keeps its existing condition, dismissal, analytics, and edit-modal action, but now renders directly after Progress in the Goal branch.
- **"View Full Guide" collapse.** `CompanionDisplayCard` now renders collapsed by default (regardless of viewport) — its header always shows, its five sections only render once expanded. Local component state, not persisted. Toggle is a light inline text+chevron affordance (no border/background), not a bordered pill button — a labeled pill competed with the title for width and forced its own line on mobile; caught from a screenshot review and fixed the same round, keeping the text label (not a bare icon) since the Companion's value isn't self-evident the way the note-list sections' collapse targets are.
- **Expanded Companion reskin.** Expanded guide sections now render as icon-led sub-panels with restrained tinting while preserving the authored Overview → Study Strategy → Common Mistakes → FAQ → Resources order and `SummaryMarkdown` bodies.
- **Fix: Quick Actions didn't read as buttons.** `Review Due Concepts` had its button chrome explicitly stripped (`border-0 bg-transparent shadow-none`, blue text) into a bare link, and the terminal exam/builder action used `variant="ghost"` — both read as plain text, not clickable actions. Both now use the existing `variant="outline"` treatment (the design system's own secondary-button style) so they read as buttons while staying visually subordinate to the filled `Continue Studying` primary. Dropped the `ChevronRight` separator between them — breadcrumb-style separators read as connected label fragments, not two independent buttons.
- **Fix: Quick Actions read as two mismatched buttons on mobile.** Both were content-width, so on narrow screens they wrapped to separate lines with different, misaligned widths. Continue Studying and both Quick Actions are now `w-full sm:w-auto` — full-bleed, matching-width rows stacked on mobile (the native action-sheet pattern, same mobile-column/desktop-row shape already used elsewhere on this page), reverting to today's side-by-side content-width layout from `sm:` up. A `grid-cols-2` equal-width layout was considered and rejected: either Quick Action can be independently absent, so a fixed 2-column grid would regularly render a half-width button next to an empty cell, and the exam action's explanatory subtext would wrap into 3–4 cramped lines in a half-width cell instead of reading as a clean full-width footnote.
- **Fix: Review Set/Study Plan detail title crumpled into a word-per-line column on narrow screens.** `PlanHeroCard`'s title and actions (Published badge, icon buttons) sat in one `flex-wrap` row; the title column's `min-w-0 flex-1` let the browser shrink it toward zero width to keep the `shrink-0` actions column on the same row, instead of wrapping actions below — `min-w-0` on a flex-grow item suppresses the wrap. Now stacks vertically by default (title full-width, actions below) and switches to the original side-by-side row from `sm:` up. Unrelated to Companion; surfaced from the same screenshot review.
- **Anti-drift confirmed.** This reorganization is frontend-only: no backend endpoint, persisted state, migration, Companion content-model change, section reorder, or Companion authoring/generation change.

---

## v0.42.1 - Companion & Progress Polish

**Status: Released**

Theme: small UX fixes surfaced from using v0.42.0 in practice — no new features, no backend changes.

### Planned Scope

- **Merge Readiness card and "View full progress" row (frontend).** On the Review Set detail page, the readiness card and the "View full progress"/"Review due concepts" row currently render as two visually separate elements even though they're the same Readiness tier (already documented as such in `docs/features/collections.md`). Add an optional `footer` slot to the shared `ReadinessSummary` component so both actions render inside the same card.
- **Fix Progress page backlink for scoped views (frontend).** `/progress?collectionId={id}` always shows a "Dashboard" backlink regardless of how the page was reached. When reached via a specific collection's "View full progress" link, the backlink should return to that collection instead.

Anti-drift: no backend change, no new endpoint, no new persisted state; course/program stays plain text on collection cards/detail (metadata, not identity/state, per `docs/features/collections.md`'s badge-classification rule) — considered and declined, not silently skipped; unscoped `/progress` (reached from nav, no `collectionId`) keeps its existing "Dashboard" backlink, unchanged.

### Shipped

- **Merged Readiness card and "View full progress" row (frontend).** `ReadinessSummary` gains an optional `footer` slot rendered inside its existing card; the Review Set detail page passes "View full progress"/"Review due concepts" through it instead of a separate stacked box.
- **Fixed `/progress?collectionId={id}` backlink (frontend).** Now returns to the originating collection (profile-aware label) when reached via that collection's "View full progress" link, instead of always showing "Dashboard" — keyed off the URL's `collectionId` param, not live selection state, so a plain nav-in defaulting to the Primary Review Set still shows "Dashboard".
- **Considered and declined:** course/program as a badge on collection cards/detail — stays plain text per the existing badge-classification rule (metadata is never a badge).

---

## v0.42.0 - AI-assisted Companion authoring + regeneration

**Status: Released**

Theme: give curators an LLM-assisted first draft for Learning Companion content, with mandatory human review before publish — proving out the "Curation, never generation" rule's curator-facing clarification from v0.41.0's design work, while keeping the learner-facing guarantee (no auto-generated plans) unchanged.

### Planned Scope

- **Curator workflow (backend + frontend).** `Generate Companion` action (per section or all four) → LLM draft → mandatory human review and edit in the existing authoring modal → `Publish`. Publishing is never autonomous. Reuses the existing OpenAI service and PREMIUM/CRITIQUE model tiers — no new LLM infrastructure.
- **Granular per-section regeneration (backend + frontend).** Overview / Study Strategy / Common Mistakes / FAQ regenerate independently, not an all-or-nothing action.
- **Staleness signal (backend + frontend).** A "Companion may be outdated" indicator when the set's structure changes since authoring — a lightweight stored structure snapshot (child count / note ids / concept count) compared on read, no new job infrastructure.
- **Resources section (backend + frontend).** Adds the Resources section deferred from v0.41.0's four-section MVP.
- **Timeline/Checklist live-feature embeds (frontend).** Deferred from v0.41.0; when built here, these must link the already-shipped live weekly countdown (v0.40.0) and readiness features — never re-authored as static prose.

Anti-drift: learner-facing behavior is unchanged — curation over generation, a learner never receives an auto-generated plan; the curator-facing AI-assist is new but scoped to Official Review Set Companions only, per the documented rule clarification in `docs/product/ROADMAP.md`'s "Guided Learning Initiative" section; publishing stays non-autonomous in every path; no new LLM infrastructure (reuse the existing OpenAI service); no new job infrastructure for the staleness signal; Timeline/Checklist must link live features, never re-author them.

### Shipped

- **AI-assisted Companion draft generation.** Added ADMIN-only per-section and all-section Companion draft generation in the existing authoring modal, backed by a stateless `POST /collections/{id}/companion/generate` endpoint and the existing OpenAI service's PREMIUM model tier. Drafts populate local form state only; curators must still review/edit and click Save before anything persists. The same action generating an already-authored section is the granular per-section regeneration the "Planned Scope" bullet above called for — no separate regenerate endpoint was needed.
- **Companion staleness signal.** Added an ADMIN-only "Companion may be outdated" authoring signal on Review Set detail, backed by a nullable `note_collections.companion_structure_snapshot` captured only when Companion is saved and compared inline on the existing Goal detail read. Known limitations: v1 compares only member count plus sorted child/note ids; it does not compare note body edits or concept counts because the existing concept-count pipeline is per-user progress work, not a cheap structural signal.
- **Companion Resources section.** Added a fifth manual-only Resources field to Companion content so curators can author markdown links and references without mixing them into strategy prose. Resources saves through the existing Companion Save path, renders after FAQ with the shared markdown renderer, and is deliberately excluded from Companion generation and staleness comparisons.
- **Timeline/Checklist live-feature embeds — satisfied by placement, no code change.** v0.41.1's information-hierarchy reorder already positions `CompanionDisplayCard` immediately below the live weekly countdown (`GoalWeeklyCountdownCard`) and readiness (`ReadinessSummary`) cards, in that order, on both the Goal and Leaf view branches. A jump-link from inside Companion would point at a card one scroll away, and a duplicate live-data recap would re-render the same numbers a card apart on the same screen — neither adds value beyond what the existing adjacency already provides. The "link the live features, never re-author them as static prose" intent is satisfied by that placement; no separate embed was built.
- **Fix: `setCompanion` null-content guard.** Surfaced by this release's pre-signoff pressure test (three-PR cross-cutting audit, since the curator-generation, staleness-signal, and Resources-section PRs all touched the same shared Companion save path). A `PUT /collections/{id}/companion` call with a null body previously still computed and persisted a non-null `companionStructureSnapshot` while leaving `companion` null — inconsistent with the file's own "clear both together" convention used everywhere else. `setCompanion` now rejects a null body before touching the collection, matching every other request-DTO mutator in `NoteCollectionService`.

---

## v0.41.1 - Review Set Detail Page: This-Set Study Dashboard

**Status: Released**

Theme: re-compose the Review Set detail page so it orients the learner ("what should I do next, in this Review Set?") instead of introducing the set as a collection screen — a frontend-only re-composition of already-shipped pieces (Readiness, Weekly Countdown, Companion, next-action surfaces), scoped to this Review Set only (the cross-set "which set" job stays with `/dashboard`). See `docs/product/ROADMAP.md`'s "Review-Set-Centric Navigation" section for the design rationale this release advances (detail-page slice only).

### Planned Scope

_(shipped below)_

Anti-drift: no backend change, no new endpoint, no new persisted state; does not wire the unwired, user-scoped `TodayFocusCard`/`MasterySnapshotCard` (wrong scope for a this-set page); no day-level scheduler ("today's schedule" stays a `todaysConceptBudget` number + countdown, Phase 2 weekly work stays deferred); no nav/Dashboard change; no Ask Companion, Resources, or Achievements; no feature-gate/billing change; publish validation unchanged.

### Shipped

- **Information hierarchy reorder.** Goal and Leaf detail now render as Identity → Current Journey → Primary Action → Readiness → Guidance → Subject Plans/Notes → Progress. There is no separate "Supporting info" page section — the hero itself carries both the metadata line and authoring chrome (see below), so nothing about managing the collection occupies its own tier in the learner's scroll path.
- **Single primary action.** The previous next-card, continue banner, and mastery-step competition now resolve into one free-tier-safe `Continue` action. The terminal exam CTA (e.g. `Take the Board Exam`) renders stacked below `Continue`, using the muted `ghost` button style, so it reads as a periodic checkpoint rather than a same-weight peer action.
- **Badge cleanup.** Primary is now a hero accent (border + indicator), Adopted remains an identity badge, and course/program, estimated hours, and notes-ready/Subject-Plan-count collapse into one muted metadata line beneath the title (e.g. `Nursing · 4/6 notes ready · ~3 hrs`).
- **Authoring controls as compact hero chrome.** Publish, Build, Edit, Manage Companion, Set/Remove primary, and Delete are grouped into a small `⋯` menu + Build button in the hero's top-right corner, next to the title — not a dedicated body card. This was refined once post-implementation: the first pass built a full "Manage this Review Set" card at the bottom of the page, which a follow-up UX review flagged as having over-corrected from "admin controls interleaved in the hero" to "admin controls interrupting the learner's scroll." The fix keeps controls present but visually minimal wherever they sit — the design rule going forward is "helps the learner study today → learning flow; changes the collection itself → compact chrome," now recorded in `docs/features/collections.md`.
- **Companion placement.** Existing Companion display renders directly after Readiness in the Guidance tier, without changing Companion content or authoring behavior.
- **Test coverage closed.** Added a dedicated test for the Goal view's Primary Action fallback (resolves to the first child Subject Plan when there's no continue/next-note action) and a BOARD_EXAM-profile assertion on the hero eyebrow/back-link, closing two gaps the original Codex prompt asked for but didn't fully cover.
- **`/collections` list card Primary treatment.** Follow-up UX review found the list card's Primary badge was the one surface left on the old filled-pill treatment after the detail hero moved to a card-level accent (this morning's reorder). Mirrored the same treatment here: Primary now renders as a left-accent border + tinted background on the card, with plain caption text (Star icon, no pill), matching `PlanHeroCard` exactly. Confirmed the card's existing identity/state/metadata tiering (from the two prior "badge hierarchy" polish passes) already matched the requested hierarchy — no reorder needed, only Primary's visual form changed. Documented the badge-classification rule (identity vs. state vs. metadata; metadata is never a badge) in `docs/features/collections.md` so future Guided Learning additions (Companion indicator, Weekly Plan glance, Readiness) get checked against it before reaching for a pill, without pre-building placeholders for identity/state data (Official, Community, Archived) that doesn't exist in the backend yet.

---

## v0.41.0 - Learning Companion (MVP)

**Status: Released**

Theme: add a persisted, curator-authored guidance layer on top of Official Review Sets — the missing piece between "a collection of notes" and a premium guided learning experience. See `docs/product/ROADMAP.md`'s "Guided Learning Initiative (Companion)" section for the full design rationale.

### Planned Scope

- **Persisted Companion content model (backend).** A JSONB column on the top-level `note_collections` row (not a new table), mirroring the existing `sessionState` JSONB precedent. 1:1 with a top-level collection only — rejected on child Subject Plans with the same `400` pattern as the existing `targetCompletionDate`/primary hierarchy validation.
- **Four sections only: Overview, Study Strategy, Common Mistakes, FAQ.** Study Timeline and Final Checklist are explicitly deferred to v0.42.0+ and must never be static prose when built — they link the already-shipped live weekly countdown/readiness features instead of re-authoring them.
- **Manual authoring only, Official Companions only (frontend + backend).** No AI generation yet. Only the NoteLib official author can author a Companion in this version.
- **Publish/adopt integration (backend).** Publishes with the Review Set via the existing `updateVisibility`/`publishChildCollections` cascade. Travels on adopt (added to `persistAdoptedGoal`'s copied set, the opposite of `targetCompletionDate`'s exclusion). Owner self-copy excludes the Companion, same category as the existing generated-content exclusion.
- **FREE for all learners.** Zero paid uplift by design — an activation/retention bet.

Anti-drift: no runtime LLM call to serve a Companion (authored once, served static); no new top-level entity; no change to the 5-mode quiz contract; no change to `UserEntity`; Companion label resolves through `getCollectionLabels` (new `companionSingular` field, same pattern as `primarySingular`).

### Shipped

- **Learning Companion backend content model.** Added nullable `note_collections.companion` JSONB plus `CompanionContent` / `CompanionFaqItem` DTO records, surfaced Companion on existing collection detail reads (`GET /collections/{id}` and `GET /collections/{id}/goal`), and added ADMIN-only `PUT`/`DELETE /collections/{id}/companion` write endpoints for top-level collections (`parentCollectionId == null`, not child count). Adopt now copies Companion on genuine cross-owner Review Set adoption and excludes it on same-owner self-copy; publish cascade remains row-local with no child Companion logic.
- **Learning Companion authoring UI.** Added an ADMIN-only `Manage Companion` action on eligible top-level Review Sets with a modal for Overview, Study Strategy, Common Mistakes, and FAQ authoring, wired to the existing full-replace and clear endpoints.
- **Learning Companion learner display.** Collection detail now renders authored Companion guidance for top-level Review Sets in both Goal and leaf views, using the shared markdown prose renderer and skipping empty draft sections. This completes v0.41.0's planned Companion scope: content model, official authoring UI, and learner-facing display.
- **Companion terminology fix (pre-signoff pressure test finding).** All "Companion" copy on the collection detail page now resolves through a new `companionSingular` field on `getCollectionLabels`, per the kickoff's own anti-drift rule, instead of hardcoded literal strings. Also fixed one adjacent hardcoded "Review Set" in the Companion editor's description text. No visible copy change today (the value is `"Companion"` across every profile), but closes a real cross-PR consistency gap a per-PR review wouldn't catch.
- **Review Set badge hierarchy (frontend polish).** `Primary`/`Adopted` identity badges moved next to the title (detail page hero) or directly under it (list page cards), out of the flattened row they previously shared with the notes count and the execution-status badge. List cards now stack cleanly: title → identity badges → status badge → notes-or-plans count + last-updated at the bottom. courseProgram/estimated-hours/Published stay in the existing top eyebrow row on the detail page — no conflict since they're a different tier from Primary/Adopted. Also fixed a related grid-height bug: `/collections` list cards now stretch to a uniform height per row (`h-full` on the card, matching the pre-existing `flex flex-col justify-between` internal layout) so the notes-count/last-updated footer stays pinned to the bottom of every card regardless of title length or badge count, instead of each card sizing independently and producing a visibly ragged row.
- **Review Set success-toast feedback (frontend polish).** Editing details, setting/removing primary, and saving/removing a Companion now show a success toast on the collection detail page, reusing the existing `ToastMessage` component and the same local-state pattern already used elsewhere in the app. Deleting a collection shows the toast on the `/collections` list page after navigating back, via a new one-shot `sessionStorage` flash notice (mirroring the existing `just-adopted-notice`/`study-plan-skipped-notice` pattern). Scoped to Review Set actions only; app-wide CRUD feedback is tracked separately in `docs/product/ROADMAP.md`'s Post-v0.41.0 Polish Backlog.
- **Pre-signoff pressure test fixes.** A full whole-release audit (two source-reading agents covering every backend/frontend file touched this release, since 4 different PRs touched the same `collection-detail-page-client.tsx`) found two real issues, both fixed: (1) `adoptGoal`'s inline child-reparenting loop didn't null out `targetCompletionDate`/`companion` the way `updateParent()` already does for the identical operation — currently unreachable because a child never has a Companion to begin with, but now defended the same way at both reparenting call sites so a future change to the adopt/copy path can't silently leak one; (2) the success-toast timers on both `/collections` and the collection detail page used bare `setTimeout`/`clearTimeout` instead of `globalThis.setTimeout`/`globalThis.clearTimeout`, contradicting this file's own established convention and CLAUDE.md's `globalThis` rule — fixed at all call sites. A third suspected issue (list-page flash toast never auto-dismissing) was checked against source and found to be a false positive — the 4-second auto-dismiss effect was already present and working.

---

## Archived releases (v0.40.1 and earlier)

Full detail for every version below moved to `docs/archive/RELEASES_ARCHIVE.md` on
2026-07-10 to keep this file lean and readable in one pass. Condensed per-version summaries
(user-facing, no implementation detail) also exist at `docs/releases/vX.Y.Z.md` for each.

- `v0.40.1` - Public Review Set Reachability
- `v0.40.0` - Weekly Study Plan (Exam Countdown) + Primary Review Set
- `v0.39.2` - Public Library Learning Experience
- `v0.39.1` - Study Plan Builder Polish
- `v0.39.0` - Flexible Review Methods
- `v0.38.0` - Read-Path Optimization Pass
- `v0.37.4` - Idle GC & Metaspace Ceiling Hotfix
- `v0.37.3` - Study Plan Read-Path Memory Optimization
- `v0.37.2` - Plan Data Integrity Hotfix
- `v0.37.1` - Native Memory Hotfix
- `v0.37.0` - Readiness-First Plans & Mastery Integrity
- `v0.36.3` - OCR Fast-Follow: Messaging & Feedback
- `v0.36.2` - OCR Disable Hotfix
- `v0.36.1` - Post-Release Fixes
- `v0.36.0` - Readiness/Progress Merge
- `v0.35.0` - Mobile-First Builder
- `v0.34.0` - Journey: Goal-First Study Experience
- `v0.33.4` - Builder Surface Clarity
- `v0.33.3` - Recursive Goal Adopt
- `v0.33.2` - Plan Detail Redesign (view/edit split)
- `v0.33.1` - Study Plan polish & Curated Plan Coverage
- `v0.33.0` - Study Plans as a Retention Engine
- `v0.32.2` - Conversion Diagnosis & Quota Honesty
- `v0.32.1` - Monetization Surfacing & Pricing Clarity
- `v0.32.0` - Account & Communication Controls
- `v0.31.2` - Analytics Integrity & Funnel Visibility
- `v0.31.1` - Adoptable Study Plans Discovery & Status
- `v0.31.0` - Adoptable Study Plans
- `v0.30.1` - Copy Flow Polish
- `v0.30.0` - Readiness Signals
- `v0.29.1` - Bulk Generation Polish
- `v0.29.0` - Bulk Generation
- `v0.28.0` - Feature Discoverability & Activation
- `v0.27.0` - Material Import & Collections
- `v0.26.1` - Guidance System
- `v0.26.0` - Exam Depth
- `v0.25.1` - Polish & Quick Review Fixes
- `v0.25.0` - Exam Capture & Goal Setting
- `v0.24.1` - Content Moderation Hotfix
- `v0.24.0` - Guided Learning
- `v0.23.1` - Quiz Format Fix
- `v0.23.0` - From Readers to Learners
- `v0.22.0` - Course & Subject Discovery
- `v0.21.0` - Personalized Discovery & Library Organization
- `v0.20.0` - Conversion & Re-engagement
- `v0.19.0` - Multi-Note Depth & Simulation Parity
- `v0.18.0` - Profile Completeness & Communication
- `v0.17.0` - Quiz Quality & Depth
- `v0.16.0` - Conversion & Growth
- `v0.15.2` - UX Cleanup & Bug Fixes
- `v0.15.1` - Teacher Power Features
- `v0.15.0` - Premium Mode Uplift + Cost-Control Quota Refactor
- `v0.14.0` - Grow the Surface, Deepen the Practice
- `v0.13.0` - Complete the Promise, Reach New Audiences
- `v0.12.0` - Learning Experience, Discovery, and Retention
- `v0.11.0` - Learning Flow Foundation
- `v0.10.1` - Landing & Pricing Conversion Polish
- `v0.10.0` - Profile Type System & Teacher Flow Phase 1
- `v0.9.0` - Learning Experience & Product Polish
- `v0.8.0` - Board Exam Mode + Public Library Discovery System
- `v0.7.0` - Learning & Metadata Foundation
- `v0.6.0` - Landing Revamp & Positioning
- `v0.5.0` - Public Profiles & Public Notes
- `v0.4.0` - Profile-Based Experience & UX
