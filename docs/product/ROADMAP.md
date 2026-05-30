# ROADMAP.md - NoteLib

Rebrand note: StudySnap has been rebranded to NoteLib while preserving current database schema naming unless explicitly changed.

Goal: evolve NoteLib from a one-shot generator into a reusable note-first study workspace.

## Current Release Baseline

`v0.21.0` is the current in-progress release.

`v0.20.0 - Conversion & Re-engagement` is complete and is the previous documentation baseline.

Older milestone labels below are preserved as planning history only. They are not the current in-progress release.

---

## v0.20.0 - Conversion & Re-engagement

**Status: Released**

Theme: bring inactive users back and close account security gaps — re-engagement campaigns, forgot/change password, richer AI summaries, and public profile polish.

### ✅ Shipped

- **Re-engagement campaign (Admin)** — one-time admin email blast targeting users inactive 30+ days, segmented by profile type.
- **Quiz header polish** — note title in Quick Review and Challenge Quiz top bars; Long Exam sources banner for multi-note sessions.
- **Study Pack summary enrichment** — AI summary now includes optional markdown comparison tables and a Common Misconceptions paragraph; frontend renders via `react-markdown` + `remark-gfm` across all surfaces.
- **Teacher In-App Guided Tips** — five one-time contextual tips covering dashboard intro, note content quality, Generate Quiz modal, library multi-note checkboxes, and DOCX export. All use existing `GuidanceTip` + `hasSeenTip()` system; confirmed already in codebase prior to v0.20.0 planning.
- **Profile-Aware Landing Page** — `ProfileLearningSection` with interactive profile tabs (Students / Exam Reviewers / Teachers / Professionals), per-profile taglines, steps, mode chips, and screenshots. `HowItWorksSection` and `ProfileShowcaseSection` already replaced; confirmed in codebase prior to v0.20.0 planning.

### 🔲 Pending Codex

1. **Forgot Password + Change Password** — closes the re-engagement loop: users receiving re-engagement emails must be able to get back in even if they've forgotten their password, and password-auth users should be able to rotate it once they're back. Two scopes:
   - **Forgot password flow**: token generation, reset email, `/forgot-password` and `/reset-password` pages. No backend endpoint or frontend page currently exists.
   - **Change password**: update-password endpoint (current password verification + new password), form in the Profile page sign-in methods section. `passwordEnabled` field already in `SignInMethodsResponse`; no action exists yet.
   - Delete Account (stub in Settings as "Coming Soon") deferred to v0.21.0 — lower urgency for this theme.

2. **Post-signup copy-note → instant quiz flow** — new signups from a public note page skip onboarding and land directly in a Quick Review on the copied note. Requires `copyIntent` param surviving OAuth redirect. Codex prompt to be written when tokens are available.

### 🔲 Deferred (Study Pack section improvements — items 2 & 4)

These were scoped during v0.20.0 planning but blocked by a v0.18.0 constraint:

| Item | Status | Reason |
|---|---|---|
| Common Misconceptions | ✅ Shipped (v0.20.0) | Embedded in summary via markdown — near-prompt-only |
| Comparison Tables | ✅ Shipped (v0.20.0) | Same — markdown rendering in summary enables this |
| Richer Quick Recall | ❌ Deferred | Would change `keyConcepts` string format — `conceptHealthByName` in v0.18.0 keys on exact concept strings; changing them orphans all existing health records |
| Concept Relationships | ❌ Deferred | Same constraint — any format change to `keyConcepts` breaks concept health tracking |

Richer Quick Recall and Concept Relationships need a dedicated `keyConcepts` migration strategy (version the health records or re-key on concept ID instead of string) before they can ship safely.

---

## v0.21.0 - Personalized Discovery & Library Organization

**Status: In Progress**

Theme: surface community notes relevant to each user's study track and let them save and reuse their own filter shortcuts — making the app feel personal from day one.

### Why this release

Three gaps appeared after v0.20.0:

1. **The Dashboard feels generic for exam reviewers** — users studying for a specific exam (PNLE, NMAT, board exams) have no fast path to community notes for their track. The public library already supports `courseProgram` filtering; surfacing it on the Dashboard turns an existing inventory into a personalized discovery feature with near-zero backend work.

2. **Private library filters are manual every time** — v0.18.0 shipped URL-based filter persistence, but users who study across multiple subjects still re-apply the same filter combinations on every session. Named saved filters close the loop without requiring note reorganization.

3. **Public profiles cap at 8 notes with no escape path** — a prolific creator has no "see all" link. A `creator` filter on the public library — already designed to be the canonical discovery place — fixes this with a single backend query addition and one frontend link.

### Primary focus

1. **Public Library creator filter + profile "View all" link** *(deferred from v0.20.0)*

   Add `creator` (username) as a query param to `GET /notes/public`. Once the backend filter exists, add a "View all X notes →" link to the public profile page (visible only when `publicNotesCount > 8`) that navigates to `/public/library?creator=<username>`.

   - Backend: join `users.username = :creator` on the existing public note query; `creator` is optional and combinable with other params
   - Frontend: add `PUBLIC_LIBRARY_CREATOR_QUERY_PARAM = "creator"` to `public-library-url.ts`; update `PublicLibraryUrlFilters` type, `buildPublicLibraryUrl`, and `parsePublicLibraryFilters`
   - Public Library UI: when `?creator=` is present, show an active "By @username" filter badge; clearing it removes the param
   - Public profile page: "View all X notes →" link rendered when `profile.publicNotesCount > 8`; link builds to `/public/library?creator=<username>`
   - Codex prompt: `docs/codex-prompts/v0.21.0-creator-filter-view-all.md`

2. **Remove Learning Focus subject badges from public profile** *(deferred from v0.20.0, blocked on item 1)*

   Remove the subject badge list (`subjects.map(SubjectBadge)`) from the Learning Focus section of `public-profile-page-client.tsx`. Keep the `learningFocusSummary` sentence. The "View all notes →" creator filter link replaces badge-based subject browsing. Frontend-only; handled by Claude Code after item 1 commits.

3. **Community Notes dashboard section** *(new)*

   New section on the Dashboard visible to all profile types, placed below Recent Notes. Title: "Notes for [CourseProgram]" (e.g. "Notes for PNLE"). Shows up to 4 public notes from `GET /notes/public?courseProgram=<value>&size=4`. Footer link: "See all in Public Library →" navigates to `/public/library?courseProgram=<value>`.

   - Visible to STUDENT, BOARD_EXAM, TEACHER, and PROFESSIONAL profiles
   - When `courseProgram` is set and matching notes exist: show up to 4 cards
   - When `courseProgram` is set but no matching public notes exist: hide the section entirely
   - When `courseProgram` is not set: render a placeholder card with a modal CTA — "Set your Course/Program to see notes tailored for your review track" with "Go to Learner Profile" (primary, `/profile#learning-profile`) + "Cancel" (secondary)
   - Requires adding an optional `size` param (max 50, default 20) to `GET /notes/public`
   - Note cards reuse the shared public library card layout
   - Codex prompt: `docs/codex-prompts/v0.21.0-course-program-dashboard.md`

4. **Saved Filters for private library** *(new)*

   Users can save a named snapshot of the current private library filter state and re-apply it with one click. Backend-persisted from the start.

   - New migration `V68__user_library_filters.sql`: table `user_library_filters` with `id` (UUID PK), `user_id` (FK → users), `name` (VARCHAR 100), `filter_state` (JSONB), `created_at` (TIMESTAMPTZ)
   - `filter_state` shape: `{ search?, subject?, courseProgram?, tags?, status?, sort? }` — mirrors private library URL params
   - Endpoints: `GET /library-filters` (list user's saved filters), `POST /library-filters` (create), `DELETE /library-filters/{id}` (delete, owner-only)
   - Frontend: "Save filter" button in the filter bar visible when at least one filter is active; opens a name input dialog on click; submitting calls the backend
   - Saved filters accessible from a dropdown or list in the filter bar; clicking applies all params; trash icon deletes
   - Scope: private library only; public library saved filters deferred
   - Codex prompt: `docs/codex-prompts/v0.21.0-saved-library-filters.md`

5. **Admin funnel metrics page** *(new — conversion visibility)*

   New admin-only `/admin/funnel` page showing the five most critical funnel health numbers. All queries run against existing tables — no new event tracking or analytics SDK required.

   | Metric | What it reveals |
   |---|---|
   | Signup → first Study Pack (% + median days) | Activation rate — are users reaching the core value? |
   | Notes with 0 Study Packs after 7 days | "Stuck before generation" pool |
   | Free quota hit rate | Are free users even reaching the paywall? |
   | Paywall seen → upgrade (%) | Is the paywall converting at all? |
   | Study Pack generated → quiz started within 7 days | Are users closing the value loop? |

   - Display as daily and weekly aggregates
   - No new migrations — derive metrics from `users`, `notes`, `study_packs`, `quiz_sessions`, `user_usage` tables
   - Codex prompt: `docs/codex-prompts/v0.21.0-admin-funnel-metrics.md`

6. **Admin summary re-generation** *(new — official content backfill)*

   One-time (but idempotent) endpoint to backfill enriched summaries for admin-owned study packs that pre-date the v0.20.0 enrichment format.

   - `POST /admin/study-packs/regenerate-summaries` — targets packs owned by `UserRole.ADMIN` users whose `summary` does not yet contain `|`
   - Async via `llmParallelTaskExecutor`; returns `{ queued: N, skipped: N }` immediately
   - Updates only `study_packs.summary` — quiz, key concepts, tags untouched
   - No quota deduction; idempotent (already-enriched packs are skipped on re-run)
   - Codex prompt: `docs/codex-prompts/v0.21.0-admin-regenerate-summaries.md`

### Shipped in this release (Claude Code)

- **Official author detection made role-based** — removed hardcoded email constant from `NoteService`; `isOfficialAuthor()` now checks `UserRole.ADMIN` only; admin's `displayName` drives the "By X" label on public notes
- **Summary word limit raised to 350** — `MAX_SUMMARY_WORDS` and `developer.txt` prompt both updated; fixes validation rejections for enriched summaries
- **Profile Identity helper text** — plain-language helper text added for Display Name and Username fields on `/profile`

### Implementation stances

- `GET /notes/public?creator=<username>` joins `users.username` on the existing query — no new endpoint, no new entity
- Community Notes section calls the existing public library endpoint directly from the frontend — only backend change is an optional `size` param on `GET /notes/public`
- `user_library_filters` is a simple user-owned table; no plan gating for v1 (all plans can use saved filters)
- No localStorage fallback for saved filters — backend-persisted from the start
- Subject badge removal is frontend-only and safe to do inline after the creator filter ships

### Anti-drift notes

- `creator` filter uses `username`, not `userId` or `displayName`; existing public note canonical URLs (`/public/library/{subject}/{slug}`) are unchanged
- Community Notes section does not create a new page or route — it links to the existing `/public/library?courseProgram=<value>` URL
- No changes to note generation, quiz sessions, or Study Pack flows in this release
- Saved filters are plan-agnostic for v1; do not add gating without an explicit plan rules update to `docs/product/PLANS.md`
- Use `globalThis` instead of `window`/`self`/`global` for all new browser globals in frontend code (ESLint enforces this)
- Analytics events use the `AnalyticsEventType` enum in both Java and TypeScript — add new values before firing events
- Official author is now `UserRole.ADMIN` — do not recreate email-based checks; `isNoteLibOfficialAccount()` has been removed
- Admin summary re-generation uses `llmParallelTaskExecutor` only — never `studyPackGenerationTaskExecutor`

### Sequencing

Items 1, 3, 4, 5, and 6 Codex prompts are independent and can be queued simultaneously. Item 2 is handled by Claude Code immediately after item 1 commits.

---

## v0.22.0 - Course-First Discovery

**Status: Planned**

Theme: make `courseProgram` the primary discovery axis across the public library and dashboard — replacing the profile-type audience gate that creates false boundaries between students and exam reviewers studying the same material.

### Why this release

Two gaps remain after v0.21.0's courseProgram-first dashboard section:

1. **The audience pre-filter in the public library creates the wrong boundaries** — a nursing student with a STUDENT profile misses notes tagged for BOARD_TAKER even when the content is directly relevant. In the Philippine exam prep context especially, "Student" and "Exam Reviewer" overlap almost entirely. The pre-filter hides content rather than surfacing it.

2. **Anonymous and first-time visitors have no guided path to their content** — users who land on the public library from a shared link or search engine see everything at once. There's no prompt to tell them that filtering by course/program (PNLE, NMAT, etc.) is the fastest path to relevant notes. The filter exists but is invisible to users who don't know to look for it.

### Primary focus

1. **Remove the audience pre-filter from the Public Library**

   Stop using `targetProfileType` as the default gate in `GET /notes/public`. Default the public library view to "All" for every profile type, including Teacher. The audience filter remains available as an optional manual filter for users who want to narrow by it, but it is no longer applied automatically on page load.

   - Remove the profile-type → `NoteTargetProfileType` pre-filter mapping from the frontend public library page
   - When `?audience=` param is absent, render the full public note list (same as "All" behavior today)
   - The `targetProfileType` badge on note cards stays; the field on note creation stays for Teachers
   - `courseProgram` + `subject` + `tags` become the primary browse signals

2. **Course/Program helper CTA in the Public Library**

   A dismissible banner shown above the note list when no `courseProgram` filter is active. Surfaces the Course/Program filter to users who don't know it exists.

   Copy: *"Studying for a specific exam or program?"* → **[Browse by Course/Program]**

   Behavior:
   - Clicking opens the filter sheet (or inline filter on desktop) and focuses the Course/Program field
   - Dismissed per session via `sessionStorage` (anonymous users) or until a `courseProgram` filter is applied
   - Hidden when `?courseProgram=` is already active in the URL
   - For signed-in users with `courseProgram` set in their profile: show a smarter variant — *"See notes for [CourseProgram] →"* — that pre-fills the filter directly instead of opening the sheet

   Signed-in users who set `courseProgram` in their profile and already land on the Community Notes dashboard section do not need this prompt; the CTA is primarily for anonymous visitors and signed-in users without a course/program set.

3. **"More in [CourseProgram]" section on public note detail pages**

   When a user opens a public note that has a `courseProgram` set, show 3–4 related public notes with the same courseProgram at the bottom of the page. Calls the existing `GET /notes/public?courseProgram=<value>&size=4` endpoint — no new backend endpoint needed.

   - Visible to both anonymous and signed-in users
   - Hidden when the note has no `courseProgram`
   - Uses the shared public library note card layout
   - Cards link to the canonical public note route
   - Section title: *"More notes for [CourseProgram]"*

   Extends session depth for users arriving from a shared link or search engine — gives them a natural next note to read instead of a dead end.

4. **Meaningful empty state when courseProgram filter returns no results**

   Replace the generic empty state with a content-creation hook when a courseProgram filter is active and returns zero notes.

   Empty state copy: *"No [CourseProgram] notes shared yet."* with a secondary line: *"Got notes? Share them with the community."* — CTA navigates to `/notes/new` for signed-in users, or `/auth` for anonymous users.

   - Only shown when `?courseProgram=` is active and the note list is empty
   - Generic empty state remains for other filter combinations

5. **Friction-free anonymous browsing — no conversion nudges in the library**

   The public library and public note detail pages are fully explorable without an account. No sign-up prompts, no login gates on browsing or filtering, no interstitials. The only login gate is on write actions (copying a note, liking).

   This is a design constraint, not a feature: when implementing items 1–4 above, do not add any "sign up to see more" banners, soft-gates, or conversion prompts anywhere in the public library or public note detail flow.

### Implementation stances

- Audience pre-filter removal is frontend-only — the `targetProfileType` query param on `GET /notes/public` remains valid and functional; we just stop sending it automatically
- All five items are frontend-only; no new backend endpoints or migrations
- Items 3 and 4 reuse the existing `GET /notes/public` endpoint with `courseProgram` + `size` params (the `size` param is added in v0.21.0)
- `targetProfileType` badge on note cards is unchanged
- Teacher note creation flow is unchanged — Teachers still set target audience when creating notes; we just stop using it as a visibility gate on the browse side

### Anti-drift notes

- Do not remove `targetProfileType` from the public note API response — it is still used for the badge on note cards and as an optional manual filter
- The `?audience=all` URL param behavior (introduced in v0.18.0 to prevent profile default re-application) remains valid; the pre-filter removal makes it redundant but harmless
- The helper CTA must not appear when a `courseProgram` filter is already active — check the URL param before rendering
- Use `sessionStorage` for CTA dismissal on the public library (consistent with how the back-nav return URL is stored); do not use `localStorage` for session-scoped UI state
- No sign-up prompts, interstitials, or conversion nudges anywhere in the public library or public note detail — the library is friction-free for anonymous users by design

### Sequencing

All five items are frontend-only. Items 1 and 2 are the core changes and should ship together. Items 3 and 4 are independent additions and can be implemented in the same Codex prompt or separately. Item 5 is a constraint on the others, not a separate implementation task. Write Codex prompts at the start of v0.22.0.

---

## v0.18.0 - Profile Completeness & Communication

**Status: Released**

Theme: complete the Professional profile experience, fix communication gaps (subscription expiry notifications, outdated email templates, spam folder guidance), add KaTeX math rendering for computational working solutions, and introduce concept-level spaced repetition signals in Adaptive Practice.

### Why this release

Three things create friction or trust gaps for existing users:

1. **Professional users discover Interview Practice by accident** — it's accessible but not prominently surfaced from the dashboard or as a primary CTA after Professional profile selection. The profile type exists and the mode works, but the flow doesn't connect them.
2. **Subscription expiry is silent** — users lose access without warning, assume the product is broken, and churn instead of renewing. A single pre-expiry email is the highest-leverage retention touch we haven't shipped yet.
3. **Emails are stale** — templates haven't been updated in multiple releases. Outdated copy erodes trust; missing spam-folder guidance causes verification failures that block new users before they ever log in.

Additionally, engineering and sciences users see plain-text working solutions when their notes deserve proper formula rendering, and the Adaptive Practice loop lacks a temporal signal to bring users back to concepts they haven't reviewed recently.

### Primary focus

1. **Professional profile dashboard polish** — make Interview Practice the primary CTA on the Professional dashboard; update onboarding step after Professional profile selection to introduce Interview Practice by name; ensure the note detail view surfaces Interview Practice prominently for Professional users.

2. **Subscription expiry notification email** — send an automated email 7 days before a user's plan expires with clear renewal CTA; send a second reminder 1 day before; send a post-expiry "your access has ended" email with a re-subscribe link. No auto-renewal — manual renewal model stays.

3. **Email template audit and polish** — audit every transactional email (welcome, verification, study pack generated, password reset, subscription confirmation, expiry notices); update stale copy to reflect current product naming (NoteLib, not StudySnap); add spam folder guidance to the verification email ("Can't find this email? Check your Spam or Promotions folder").

4. **KaTeX math rendering (Pro)** — replace the plain-text working solution panel with proper LaTeX rendering for `COMPUTATIONAL`-type questions; add KaTeX as a frontend dependency; update LLM prompts for engineering/sciences modes to generate LaTeX-formatted working solutions; keep plain-text fallback for non-LaTeX content.

5. **Concept-level spaced repetition signals** — track the last time each key concept was answered correctly per user per study pack; surface a "Due for review" signal on concepts not seen in 3+ days; Adaptive Practice mode surfaces these due concepts preferentially; visible in a lightweight "Concept health" view on the study pack detail page.

6. **Parent profile** — needs product definition before implementation. Placeholder: understand what parents do in NoteLib (monitor child's study activity? create notes on behalf of children?). Defer implementation until the use case is defined; remove PARENT from visible onboarding options for now to avoid confusion.

### Implementation stances

- KaTeX: add as a scoped dependency (`react-katex` or `katex` direct); render only in the working solution panel, not in question or choice text
- Spaced repetition: lightweight SM-2-inspired signal only — no full algorithm; a simple "last correct answer date per concept" is sufficient for v1
- Subscription expiry emails: backend scheduled job (Spring `@Scheduled`); no new email service — use existing Mailgun/SendGrid integration
- Parent profile: do NOT implement until the user flow is defined; remove from profile type selection if it shows a blank experience
- Professional dashboard: UI-only change — no new backend endpoints; Interview Practice is already accessible, just needs better surfacing

### Anti-drift notes

- Do not change the subscription billing model (no auto-charge); expiry emails are notification-only
- KaTeX rendering must not affect non-computational question text — scope it only to `workingSolution` display
- Spaced repetition data must be per-user per-study-pack — do not mix concept health across different notes
- The five quiz modes remain unchanged; spaced repetition is a signal layer on top of Adaptive Practice, not a new mode

---

## v0.19.0 - Multi-Note Depth & Simulation Parity

**Status: In Progress**

Theme: complete the multi-note story across all premium simulation modes. Board Exam is the last mode without multi-note support — Long Exam has had it since v0.14.0. This is the highest-priority shipping target for the Facebook group audience, where board exam reviewers are the primary demographic.

### Why this release

Board exam reviewers studying across multiple subject areas need to simulate full-coverage exams — not just single-topic ones. Multi-note Long Exam shipped in v0.14.0 and proved the pattern is sound. Multi-note Board Exam completes the simulation parity story.

The Facebook study groups driving organic growth are dominated by board exam reviewers. Multi-note Board Exam is the one feature most likely to generate word-of-mouth there.

### Primary focus

1. **Multi-note Board Exam (Pro)** — Pro users can span a Board Exam session across up to 3 same-subject notes, mirroring the multi-note Long Exam feature exactly.

   - Prestart screen gets a "Span this exam across more notes" section (same-subject filter, same note-picker row style as Long Exam)
   - Questions split proportionally across selected notes by source; source refs stored in session JSONB
   - Generation: live at session start (no pre-generated pool rethink needed for v1 multi-note; the pool is scoped to the combined source set)
   - Existing single-note Board Exam flow unchanged for users who pick only one note
   - Empty-state hint on single-note prestart: "Create another note with the same subject to unlock multi-note exam mode" (mirrors the Long Exam hint from v0.17.0)
   - Backend follows the same pattern as `LongExamService` for multi-note source merging and question pool allocation
   - Pro-only, with Board Exam quota charged per source note and the monthly cap raised for normal single-note headroom
   - Subject constraint enforced at the picker level (same-subject filter); cross-domain Board Exam is out of scope for v1

2. **Admin analytics subject drift fix** — "Top Subjects by Study Pack" currently groups on the study pack's own `subject` column, which was set at generation time and never updated. If the user later adds or changes the note's subject, the pack's subject lags. Fix: join through `NoteEntity` to use the current note subject instead of the stored pack subject for the top-subjects aggregation.

### Completed in v0.19.0 so far

- **Multi-note Board Exam (Pro)** — shipped multi-source Board Exam support for up to 3 same-subject notes with the existing `BOARD_EXAM` discriminator, same quota/category, fixed question cap redistribution, `sessionState.sourceNoteRefs`, per-source live Board Exam generation, and in-session source attribution.

### Implementation stances

- Multi-note Board Exam must reuse the existing `BOARD_EXAM` session discriminator and generation pipeline; no new mode, no new quota category
- Source-note references in session JSONB follow the existing Long Exam pattern (`sourceNoteIds`, `sourceNoteQuestionCounts`)
- Board Exam stays feedback-free during the session — multi-note does not change the exam-simulation identity contract
- Admin subject metric fix changes only the repository query — no entity change, no migration

### Anti-drift notes

- Do not skip the same-subject constraint for v1 (cross-domain Board Exam is a separate design question)
- Multi-note Board Exam scales question count by source count (`min(12 * sourceCount, 30)`) so wider simulations get more coverage while staying capped at a 30-minute exam
- The five quiz modes remain unchanged; multi-note is a configuration of an existing mode, not a new mode

---

## v0.17.0 - Quiz Quality & Depth

**Status: Released**

---

## v0.16.0 - Conversion & Growth

**Status: Released**

Theme: close the gap between social traffic and signed-up users; make teachers a natural distribution channel through student-facing quiz sharing; ensure the mobile web experience doesn't lose social visitors before they reach value.

### Why this release

NoteLib has a healthy feature set but weak top-of-funnel conversion. The primary distribution channel is Facebook — posts in student and board exam groups linking to public notes. Four problems block that funnel today:

1. Social traffic is almost entirely mobile; the web app isn't installable and some flows feel cramped on small screens.
2. New users who sign up from a public note land in an empty library with nothing to do — the note they came from isn't there, and the quiz flow they started doesn't continue.
3. Teachers have no way to share a quiz with students digitally. Every teacher who generates a quiz is a potential distribution channel for 30–50 student signups — but only if sharing exists.
4. The landing page shows a generic "How it works" loop that doesn't speak to teachers or board exam reviewers specifically — visitors can't immediately see their own workflow.

This release addresses all four, in order of impact.

### Primary focus

1. **Shareable Student Quiz Links (Teacher feature)**

   Teachers generate a quiz → receive a shareable `/quiz/[token]` link → students open it in-browser → take the quiz without needing an account first → prompted to sign up at the end to save their score and access their own notes. Teacher sees a basic response summary (score distribution, who answered among authenticated users).

   - This is the highest-leverage conversion feature: each teacher who adopts it drives 30–50 new signups per class
   - Anonymous session — no score persistence until the student signs up; no anonymous session state stored beyond the current browser session
   - Quiz link is tied to a specific `generatedQuiz`; the teacher controls whether sharing is on or off
   - Teacher-profile only; student-profile users cannot generate shareable quiz links
   - Free teachers: limited shareable links per month (TBD based on cost math); Plus/Pro: higher or unlimited

2. **Post-signup copy-note → instant quiz flow**

   When a new user signs up from a public note page, route them directly into a quiz session on the note they came from — the copied note is already in their library, Study Pack is already generating, and the first Quick Review starts immediately. Remove the "empty library" drop-off entirely.

   - Requires a `copyIntent` param surviving the OAuth / email signup redirect
   - On successful signup, backend copies the public note to the new user's library and triggers Study Pack generation
   - Frontend routes directly to the quiz session, not the library
   - No change to the existing copy flow for already-authenticated users
   - Users who sign up directly (not from a public note) see onboarding first, then library; users coming from a public note skip onboarding and go straight to the quiz

3. **Profile-Aware Learning Loop (Landing page)**

   Replace the current static "How it works" + "Who It's For" sections with a single interactive section. Visitors click their profile type (Students / Exam Reviewers / Teachers / Professionals) and see the exact learning loop for that role — screenshot, description, mode chips, and step-by-step workflow.

   - Replaces `HowItWorksSection` (generic 5-step loop) and `ProfileShowcaseSection` (static cards) with one merged `ProfileLearningSection` client component
   - Profile tabs at top; selected tab drives screenshot, description, mode chips, and learning loop steps below
   - Default selection: Students
   - Per-profile step data and tagline (e.g., "Create - Generate - Preview - Export - Share" for Teachers)
   - Teacher step 5 is "Share" — intentionally previews the Shareable Quiz Links feature shipping in this release
   - A simplified 3-step overview ("Capture → Generate → Practice") replaces the generic loop in the hero area so the top of the page still has a quick pitch

   Per-profile learning loops:

   | Profile | Tagline | Steps |
   |---|---|---|
   | Students | Create - Understand - Practice - Challenge - Improve | Create, Understand, Practice (Quick Review), Challenge (Challenge Quiz / Long Exam), Improve (Adaptive Practice) |
   | Exam Reviewers | Create - Understand - Practice - Simulate - Improve | Create, Understand, Practice (Quick Review / Challenge Quiz), Simulate (Board Exam), Improve (Adaptive Practice) |
   | Teachers | Create - Generate - Preview - Export - Share | Create lesson note, Generate quiz, Preview & refine questions, Export DOCX, Share quiz link to students |
   | Professionals | Create - Understand - Practice - Critique - Report | Create, Understand, Practice (scenario MCQ), Critique (AI feedback per answer), Interview Readiness Report |

4. **Teacher In-App Guided Tips**

   Add five one-time contextual guidance tips for teachers at the moments where the Teacher workflow is most confusing. Uses the existing `GuidanceTip` component + `hasSeenTip()` localStorage system — no new infrastructure.

   | Moment | Message |
   |---|---|
   | First Teacher dashboard visit | "NoteLib turns your lesson notes into ready-to-use quiz drafts. Start by creating a note with your lesson content." |
   | First note creation (teacher) | "The more detail in your notes, the better the quiz questions. Paste a full lesson outline, not just bullet headers." |
   | Generate Quiz modal (teacher, first time) | "You can select multiple notes to build a quiz from a full unit — use the note checkboxes in your library first." |
   | Multi-note checkboxes (library, teacher, first time) | "Select multiple notes with the checkboxes, then use 'Generate Quiz' from the toolbar." |
   | First DOCX export button encounter | "Download as DOCX and open in Word or Google Docs — format it your way before distributing to students." |

   - All tips gated by `user.profileType === 'TEACHER'`
   - Each tip has a unique `tipId`; once dismissed, never shown again (localStorage)
   - Do not add new tips without going through `pickActiveGuidance()` on pages that already use priority-ranked rules; inline `GuidanceTip` is acceptable for one-off contextual placements

5. **PWA / Mobile Web Polish**

   Make the web app installable from mobile browsers and ensure the core conversion funnel (public note → Quick Check → signup → first quiz) is thumb-friendly.

   - Add PWA manifest and service worker with an offline shell for the app routes
   - Add an "Add to Home Screen" nudge for returning mobile visitors who haven't installed
   - Fix iOS Safari viewport zoom on input focus: all inputs and textareas must have `font-size: 16px` minimum — iOS zooms when font-size < 16px, hiding modal action buttons in some flows
   - Audit and fix touch targets, modal scroll behavior, and text sizing on the public note, Quick Check, signup, and dashboard flows
   - No full native app — PWA covers the gap without the 2–3 month rebuild cost

6. **Consistent Paywall UI**

   Unify the look of all quota-limit messages across the app. Currently the study pack generation limit and the note creation limit surface as visually inconsistent banners. Define a single paywall template (icon, title, reset date, upgrade CTA) and apply it to all quota surfaces.

   - All quota-limit states (study pack, note creation, quiz generation) must render the same component/layout
   - Upgrade CTA always routes through `getUpgradeCtas(currentPlan)` — no hardcoded copy
   - Reset date must be accurate and formatted consistently

8. **Send Feedback button consistency fix** ✅

   The "Send Feedback" button appeared as a floating bottom-right button on some pages (Dashboard, Library, Settings) and as a navbar icon on others — inconsistent and the navbar-triggered modal was rendering clipped to the header area due to `backdrop-filter` creating a CSS containing block for `position: fixed` children.

   - `AppModal` now wraps its overlay in `ReactDOM.createPortal(..., document.body)` so it always escapes any containing block ancestor, regardless of where it is mounted
   - Floating `SendFeedbackWidget` removed; header icon renders consistently on all authenticated pages
   - Removed `shouldShowFloatingFeedbackWidget` / `shouldShowHeaderFeedbackWidget` routing functions from `app-shell.tsx`

7. **Social proof on landing**

   Add real-time (or cached) aggregate counts and one or two genuine student/teacher testimonials to the landing page. Students and teachers trust peer validation; a note count and a real quote move the needle more than a feature list.

   - Cached backend aggregate: note count, user count, completed quiz session count
   - "Join X students already studying on NoteLib" stat line in the hero or beneath the CTA
   - 1–2 short testimonial quotes sourced from real users (manual, not generated)
   - No fabricated numbers or aspirational counts — use real figures only

### Implementation stances

- Shareable quiz links must not persist anonymous session state — no new session rows until the student authenticates; the quiz UI is client-side-only during the anonymous play
- `copyIntent` redirect must survive both Google OAuth and email/password signup flows; implement as a short-lived server-side token or a signed cookie, not a plain query param that gets dropped on OAuth redirect
- PWA service worker must not cache API responses or auth state — static assets only; do not cache quiz or note data
- Social proof counts must be cached (5-minute TTL acceptable) — do not query live on every landing page load
- Shareable quiz link quota for Free teachers is a plan rules change; update `docs/product/PLANS.md` before implementing the gate
- Profile-aware learning loop is a pure frontend change — `"use client"` component with local tab state; no backend involvement
- iOS zoom fix is a CSS-only change; do not add `user-scalable=no` to the viewport meta tag (accessibility regression)

### Anti-drift notes

- Shareable quiz links are a teacher-only feature — do not expose link generation on student note detail
- Anonymous quiz sessions must not create `QuickReviewSessionEntity` rows — no backend session until the student signs up
- PWA scope is limited to the conversion funnel; do not invest in offline-capable quiz sessions or background sync in v1
- Testimonials must be real; do not generate or invent them
- Profile-aware landing section merges two existing sections — do not keep both the old `HowItWorksSection` and the new merged section; remove the old one
- Teacher guided tips use existing `GuidanceTip` component — do not build a new tips framework

### Sequencing

Recommend shipping in this order:
1. Mobile viewport zoom fix + consistent paywall UI (CSS + component fix, fastest wins, unblocks mobile users immediately)
2. Teacher in-app guided tips (low scope, unblocks active teacher sales)
3. Profile-aware learning loop on landing (frontend-only landing redesign)
4. Post-signup copy-note → instant quiz + onboarding flow redesign (full-stack, highest funnel impact)
5. Shareable Student Quiz Links (full-stack; teacher-side first, student anonymous play second)
6. Social proof on landing (fastest to ship once real numbers are confirmed)
7. PWA / Mobile Polish (broadest scope; run in parallel with the above or ship last)

---

## v0.17.0 - Quiz Quality & Depth

**Status: In Progress**

Theme: close the gap between NoteLib-generated quizzes and what students encounter in actual Philippine board and licensure exams — fix known generation quality bugs, add realistic question framing variety, and lay the groundwork for computational questions in engineering and sciences.

### Why this release

Three quality gaps surfaced in v0.16.0 user testing:

1. **Choices appearing in explanation text** — the LLM occasionally echoes the answer choices inside the `explanation` field (e.g., "The correct answer is A. Civil Engineering Fundamentals. (A) Civil Engineering... (B) Mathematics..."). Happens most on notes that contain answer choices in the source text (copied from reviewers). Prompt fix.
2. **Board exam and challenge quiz questions sharing identical distractors** — a cluster of related questions can end up with the exact same four choices, which never happens in real Philippine board exams outside of deliberate matching-type blocks. Prompt constraint fix.
3. **All questions use the same framing** — every question starts with "Which of the following..." — real licensure exams vary framing extensively: "All of the following are true EXCEPT...", "Which is NOT correct?", "Which best describes...?" etc. Prompt improvement.

Additionally, engineering users need computational questions with worked solutions and formula-based distractors — a larger feature requiring math rendering (KaTeX/MathJax) and schema changes.

### Primary focus

1. **Quiz Generation Quality Fixes** — prompt-only changes, no schema or UI changes

   - **Fix: choices in explanation text** — add prompt instruction: "In the explanation field, explain WHY the answer is correct. Do not repeat, list, or reference the answer choices by letter or text."
   - **Fix: repeating distractors across questions** — add prompt constraint: "Each question must have a fully independent set of four distractors. No two questions in this quiz may share the same set of answer choices."
   - **Improvement: plausible numerical distractors for formula-heavy notes** — when a note contains formulas or unit-based quantities, generate distractor choices that are plausible numerical values (wrong by a predictable error — wrong formula applied, wrong unit conversion) rather than conceptually unrelated terms.

   These three are deployable as standalone prompt hotfixes and do not need to wait for the full v0.17.0 feature scope.

2. **Question Framing Variety** — prompt improvement only; no schema changes; 4-choice MCQ format is preserved

   Instruct the LLM to vary question framing across a quiz set instead of defaulting to "Which of the following...?" every time. Target mix (not enforced per-question, just as a distribution instruction):
   - "Which of the following is TRUE?" (standard, ~40%)
   - "Which of the following is NOT correct?" or "All of the following are true EXCEPT..." (~25%)
   - "Which best describes X?" or "What is the primary purpose of X?" (~20%)
   - Assertion-style: "Statement 1: ... Statement 2: ... Which is correct?" (~15%)

   Applies to Challenge Quiz, Quick Review, Board Exam, and Long Exam generation prompts.

   Out of scope: true/false 2-choice and multi-select formats — those require schema changes (see item 4).

3. **Computational Quiz Mode** — new feature requiring schema + frontend changes; Pro-only at launch

   Math-based questions with numerical answer choices and step-by-step worked solutions in the explanation. Designed for engineering, sciences, and finance notes.

   - **Schema**: add optional `questionType: "CONCEPTUAL" | "COMPUTATIONAL"` to `QuizItem`; `COMPUTATIONAL` questions include a `workingSolution` string in the explanation (displayed in a distinct block)
   - **LLM**: engineering/math prompt persona asks for computation-based questions when the note contains formulas, quantities, or unit conversions; answer choices are plausible numerical values with different units or rounding errors; explanation shows step-by-step derivation
   - **Frontend**: render `workingSolution` in a code-block-style panel below the explanation; integrate KaTeX or render plain-text math in a fixed-width font as a v1 approximation (full LaTeX rendering is v2)
   - **Verification note**: LLM arithmetic is unreliable; v1 does not validate correctness — a disclaimer ("AI-generated — verify calculations") appears on computational questions; active verification (running math through a solver) is post-v1
   - Gated by: engineering/math note detection (heuristic: subject or tags contain engineering/math signals, or note content contains `=` and units)

4. **Additional Question Format Types** — requires schema + UI changes

   - **True/False standalone** — 2-choice questions (`["True", "False"]`); requires `choices` to be variable-length or a separate `questionFormat` field; scoring unchanged
   - ~~**Multi-select**~~ ✅ — "Select all that apply" shipped as `MULTI_SELECT` with `correctIndices`, all-or-nothing v1 scoring, and `correctIndex` preserved as a legacy fallback; available on all plans in every quiz mode except Board Exam
   - ~~**Matching type**~~ ✅ — deliberate shared-choice block shipped as `MATCHING` with `questionGroup`, shared option rendering, group-aware shuffle, and per-item single-correct scoring; available on all plans in every quiz mode except Board Exam
   - Remaining implementation order: True/False polish / audit as needed

### Known Generation Reliability Issues (lower priority, v0.17.0)

- **Invalid key concepts schema mismatch** — intermittent generation failure surfaced as "The study pack service returned invalid key concepts"; occurs when the LLM returns the key concepts array with an unexpected field shape (wrong field name, missing required field, extra fields, or partial JSON); current behavior is a hard failure requiring the user to retry; confirmed intermittent in production (3 retries before success in one known case); fix approach: defensive JSON parsing with field coercion or a single automatic backend retry before surfacing the error; prompt schema reinforcement likely sufficient; no schema changes required; lower priority than the quiz quality prompt fixes but should ship within v0.17.0

### Implementation stances

- Quality fixes (items 1–2) are prompt changes in `backend/src/main/resources/prompts/` — no DB migration, no entity change; deployable as hotfixes
- Computational quiz is gated to subjects where it adds value — do not generate computation questions for history, law, or social-science notes
- Do not add KaTeX as a full dependency for v1 computational questions; a fixed-width text block for the working solution is an acceptable v1 approximation
- Question framing variety must not change the `QuizItem` schema — it is purely a generation instruction
- Additional format types (True/False, multi-select) require `EXAM_MODES.md` review before implementation — they affect scoring logic in all five quiz modes
- Exactly five quiz modes remain in v0.17.0; question types and question formats are orthogonal to the mode hierarchy

### Anti-drift notes

- Do not add computational questions to Board Exam Mode until the Philippine board exam format is confirmed to include them (most PH licensure MCQ sections are conceptual)
- Do not add `user-scalable=no` to viewport meta tag when fixing iOS zoom — accessibility regression
- True/False standalone questions change the choice-count assumption in all quiz UIs; audit every surface that renders `choices.map(...)` before shipping
- Multi-select changes the scoring contract; `correctIndex` callers must be audited before `correctIndices` is introduced
- Matching type is the most complex format addition; do not bundle with True/False in the same prompt
- Invalid key concepts fix must not silently discard key concepts; coerce or retry, do not hide partial data loss

---

## v0.15.2 - UX Cleanup & Bug Fixes

**Status: Released**

Theme: post-Teacher-Power-Features polish pass focused on long-standing UI/UX bugs and rough edges across notes, library, profile navigation, help guides, and quiz session surfaces. No new features — sharper defaults and accurate state.

Primary focus:

1. **Quiz session display correctness** — Recent Sessions chip on Note Detail renders the actual quiz mode (Quick Review / Challenge Quiz / Adaptive Practice / Long Exam / Board Exam / Interview Practice) instead of always showing "Challenge Quiz"; library card "Not reviewed yet" timestamp updates after any mode completion, not just Quick Review; multi-note Long Exam sessions surface on every participating note with a "spans N notes" sublabel.

2. **Copy and navigation polish** — Edit Note drops the Import Notes uploader (belongs to Create Note); app shell Profile sidebar redirects to Profile Settings (avatar "My Profile" stays as the public-profile entry); Board Exam Guide no longer recommends Long Exam (Student-only mode) and footer "Switch Profile" CTA deep-links to the Profile Type section; Student / Teacher / Professional guides show profile-aware "Switch Profile" footer CTAs that hide on the user's own profile guide; share-note modal auto-copies the URL on open and shows a "Copied" success pill.

3. **Library Draft filter** — new `Draft` chip in the library Filter row for users parking notes while waiting for monthly Study Pack quota reset.

4. **Target Audience cleanup** — Create Note "Who is this note for?" keeps hidden auto-prefill for Student / Board Exam / Professional profiles and fixes Professional notes so they save with the Professional audience instead of Student; Teacher/Admin keeps a visible required picker with Professional as a selectable audience.

### Implementation stances

- All v0.15.2 items are polish or bugfix — no new persisted columns beyond minor backend support for `lastSessionCompletedAt` aggregation; no plan-gated features
- Quiz session display fixes derive `lastSessionCompletedAt` server-side from existing session tables — no new "last activity" column
- `getQuizSessionModeLabel` becomes the single source of truth for mode → label mapping; do not inline labels anywhere
- Multi-note Long Exam display is driven by the session's participant set, not the note
- Target Audience stays required. Student / Board Exam / Professional keep hidden profile-based auto-prefill; Teacher/Admin keep a visible required picker.

### Anti-drift notes

- Do not touch `QuickReviewSessionEntity` schema or session-state JSONB layout
- Do not redesign Recent Sessions card visuals — chip text, sublabel text, and inclusion criteria are the only changes
- Do not change Dashboard / Mastery Report / Score Report aggregation; only the library card label and Recent Sessions list widen
- Target Audience visibility stays profile-aware; only the Professional default and Teacher/Admin selectable audience list change. Course / Program field and helper are untouched
- Codex prompts for this scope live at `docs/codex-prompts/v0152-fix-quiz-session-display.md`, `docs/codex-prompts/v0152-polish-copy-and-nav.md`, and `docs/codex-prompts/v0152-library-filter-and-target-audience.md`

### Sequencing

The three prompts are independent and can ship in any order. Recommended order is:
1. `v0152-polish-copy-and-nav.md` (lowest risk, fastest verification)
2. `v0152-fix-quiz-session-display.md` (backend + frontend; verify multi-note Long Exam case carefully)
3. `v0152-library-filter-and-target-audience.md` (small additive enum change)

---

## v0.15.1 - Teacher Power Features

**Status: Released**

Theme: extend the teacher quiz-authoring workflow with concrete controls that turn it into a complete classroom tool, building on the v0.15.0 teacher flow polish and plan accessibility foundation. Target audience: Filipino teachers who need a practical, affordable tool for quiz and exam preparation.

Primary focus:

1. ~~**Question count control on Generate Quiz**~~ ✅ — let teachers choose 10 / 20 / 30 questions per generated quiz. Plus+ Teacher unlocks 20 and 30; Free Teacher fixed at 10. Honest upsell because higher counts directly increase LLM token cost.

2. ~~**Custom DOCX header**~~ ✅ — teacher profile carries an optional `schoolName` field that appears at the top of every DOCX export. Per-export modal can add class/section name and toggle date inclusion. Eliminates the manual edit-in-Word step before printing or filing exam packets.

3. ~~**Multiple exam versions (A/B/C)**~~ ✅ — single DOCX export with 2 or 3 deterministically shuffled versions for anti-cheating in classroom settings. Plus+ Teacher only. Choice order also shuffled per version; answer keys reflect shuffled positions. Same exam + same versionCount produces identical bytes (deterministic).

Shipped refactor:

- ~~**Per-note learner-level removal**~~ ✅ — Study Pack generation now resolves learner level from the owner profile, Public Library no longer treats notes as learner-level-filterable artifacts, and Teacher Generate Quiz adds an optional per-generation Target Level override for class-specific quiz difficulty.
- ~~**Required learner-level teacher reframe**~~ ✅ — onboarding and profile validation guarantee a profile learner level for new and updated profiles, and Teacher Generate Quiz requires and pre-fills its Target Level override from the note's latest generation or profile fallback.

### Implementation stances

- All three features are gated by Teacher profile type; non-Teacher profiles see no UI surface for them
- All three preserve the `generatedQuiz` ownership model from v0.15.0 — no LLM call at export time for header rendering or version shuffling
- Plus-gate enforcement for question count happens BEFORE the LLM call to avoid wasted tokens on rejected requests
- Backend exception classes follow the existing plan-gated-action pattern (e.g., `QuestionCountNotAllowedForPlanException`, `MultipleExamVersionsNotAllowedForPlanException`)

### Anti-drift notes

- Multiple-version shuffle is a deterministic algorithm, not AI — do not market or icon-decorate as "AI-powered"
- DOCX header limited to one school name line + one class name line + one date line; no multi-line address, no logo, no branding (v1 scope)
- Question count restricted to the set {10, 20, 30}; no slider, no custom values, no values outside this set
- Versions limited to {1, 2, 3}; do not extend beyond 3 in v1
- DOCX export must continue to use stored `generatedQuiz` data only — header and shuffling are local rendering

### Sequencing

v0.15.1 must NOT ship before v0.15.0 because:
- Question count control's "Plus unlocks 20/30 questions" upsell copy depends on the teacher-aware `getUpgradeCtas` variant introduced in v0.15.0 (Teacher Plan Accessibility)
- Multiple exam versions reuses the per-export Plus paywall pattern established in v0.15.0

Within v0.15.1, the three features can ship in any order or in parallel — they are mostly orthogonal.

---

## v0.15.0 - Premium Mode Uplift + Cost-Control Quota Refactor

**Status: Released**

Theme: make Long Exam and Board Exam feel premium, not just gated behind a paywall, and close the unbounded-LLM-cost gap on uncapped modes. This is a margin fix framed as a UX uplift, not a feature add.

Primary focus:

1. **Premium feel for Long Exam and Board Exam** — improve the paid-mode experience without adding AI coaching or changing the locked simulation identity.

   - Stronger pre-session framing: pre-flight presentation, expected duration, and "this is not a quiz" cues
   - Stronger post-session presentation: score report layout polish, domain-coverage visualization, and suggested-next-step framing
   - Possible visual differentiation: distinct top-bar treatment, calm color palette, and larger result-page typography
   - Constraint: Board Exam stays feedback-free during the session; Long Exam stays forfeit-only with no mid-exam coaching, as locked in `docs/product/EXAM_MODES.md`

2. **Cost-control quota refactor** — replace the current "Pro = effectively unlimited" Long Exam and Board Exam state with explicit per-mode caps.

   | Mode | Current Pro state | Proposed v0.15.0 cap |
   |---|---|---|
   | Challenge Quiz | 50/mo | 50/mo (unchanged — already cheap per session, do not trim) |
   | Adaptive Practice | 30/mo | 30/mo (unchanged) |
   | Long Exam | uncapped (gated by Pro plan only) | 10/mo |
   | Board Exam | uncapped (gated by Pro plan only) | 5/mo (highest LLM cost per session) |
   | Interview Practice | 10/mo | 10/mo (unchanged) |

   Specific numbers are runtime config and must be tuned against actual usage data from v0.14.0 once captured. Do not lower Pro Study Pack quota (100/mo) or Pro Challenge Quiz quota (50/mo) without usage evidence — those are existing value the user is paying for.

3. **Interview Practice evolution post-v0.14.0** — review what to do next only after Interview Practice v1 has run for at least one billing cycle.

   - **Multi-note Interview Practice (smart context aggregation)** — generate from the base note plus related notes that share `courseProgram` and at least one tag; cap at 2–3 sibling notes to manage prompt size and per-session cost
   - **Structured interview templates by role/job family** — consider opinionated section breakdowns such as Backend Engineer = PL fundamentals + DB + Behavioral only if v1 usage data shows demand
   - **Open-ended / conversational evaluation** — only consider if MC + critique format hits its ceiling and Pro users explicitly ask for it
   - **Profile / role enrichment** — design separately before capturing target role on the user profile
   - **Interview Practice tier promotion to Plus** — only if v0.14.0 usage data justifies the LLM cost; current `gpt-4.1` generation + `gpt-4.1-mini` critique split is what makes Pro-only economically viable

4. **Professional profile surface updates** — Interview Practice shipped in v0.14.0 but was not surfaced on the landing page, learn page, or help center. Close the gap so the feature is discoverable to the audience it was built for.

   - **Landing page** — add "Professionals" to the `targetUsers` section alongside Students, Exam Reviewers, and Teachers; update the "how it works" step copy to acknowledge interview simulation as a distinct mode
   - **Help center** — add a "Professional Guide" help card that explains the Interview Practice workflow: note → scenario MCQs → AI critique → Interview Readiness Report
   - **Learn page** — add a "professionals" category with 2–3 guides: how to use NoteLib for interview prep, how to practice with scenario-based questions, how to read the Interview Readiness Report

6. **Authenticated user redirect on public/marketing pages** — when a signed-in user navigates to the landing page (`/`), pricing page, or other public marketing pages, the app currently renders those pages as if the user were anonymous (no auth-aware nav state, no redirect). The expected behavior is: redirect authenticated users from pure marketing pages directly to `/dashboard`, so they are never dropped back into a conversion funnel they have already passed through. Public content pages (`/public/library`, `/public/library/[subject]/[slug]`) are exempt — they are genuinely useful to signed-in users and should not redirect. Implementation approach: server component auth check on the landing page and pricing page routes; if a valid session cookie is present, return `redirect('/dashboard')` before rendering; no client-side redirect to avoid flash of landing content.

5. **Teacher flow polish** — make the teacher Generate → View → Export loop feel like a first-class product, not a functional prototype. Target audience: Filipino teachers who need a practical, affordable tool for quiz and exam preparation.

   - **Exam Builder UX audit and polish** — the current Exam Builder (note selection, section management, balance controls) works but is dense; identify and fix the specific friction points without a full rebuild; improve the note selection flow, make section reordering more intuitive, and reduce cognitive load on the balance step
   - **Quiz Preview layout** — stronger question display, correct answer and explanation more clearly distinguished, Export CTA as the dominant action in the header (not buried); read-only feel should communicate "this is your exam, ready to hand out"
   - **Teacher dashboard emphasis** — "Ready to Export" and "Recently Generated Quizzes" should be the first thing a teacher sees, not secondary cards below Continue Studying; Create Teaching Material CTA should be prominent and direct
   - **Teacher-specific empty states and guided first run** — new Teacher users land in a blank library with no guidance; add a first-run banner that explains the Generate → View → Export loop in plain language, linking directly to "Create a note" so teachers aren't lost
   - **Teacher plan accessibility** — exports are the terminal action for teachers, not quiz sessions; evaluate giving Teacher-profile Plus users higher or unlimited DOCX export limits, since capping exports at 15/mo directly blocks their primary workflow; the goal is to be genuinely useful to teachers who cannot afford the Pro tier, especially in the Philippine context where Pro pricing is proportionally high relative to teacher salaries; this requires a plan rules change and a `docs/product/PLANS.md` update before implementation

### Implementation stances

- New explicit per-mode quotas should live on `UserUsageEntity` and `StudySnapProperties`; reset by `BillingUsageResetJob`
- Existing uncapped Long Exam and Board Exam behavior is a margin risk; caps are the fix, not coaching
- Use honest user-facing framing: "Each mode now has its own monthly cap so you can see exactly what your plan includes"; avoid framing as a quota reduction
- Surface per-mode usage in Settings -> Plan & Billing alongside the existing counters
- Keep Board Exam feedback-free during the session and keep Long Exam forfeit-only with no mid-exam coaching
- Re-validate cost math against actual rates and usage before finalizing cap values
- Teacher flow polish must not change the `generatedQuiz` ownership model or route teachers into student session logic; all teacher preview uses `generatedQuiz` only
- Teacher plan accessibility decision must be made before any billing rule changes are implemented; do not change plan limits without a reviewed `PLANS.md` update
- Authenticated redirect must be server-side only (no client-side flash); public content pages (`/public/library/**`) are explicitly excluded from redirect behavior — only pure marketing pages (`/`, `/pricing`, `/learn`) redirect to `/dashboard`

### Cost math reference

- Pro revenue: roughly $4.50 blended (PH ₱249 + USD $4.99)
- Worst-case current LLM cost per Pro user/mo when every quota is maxed and Long/Board remain uncapped: roughly $4.83, creating negative margin on heavy users
- Worst-case post-cap: roughly $0.94 saved on Long/Board, restoring healthier margin
- Realistic-usage cost: roughly $1.50/mo, with caps protecting the worst case without affecting most users

---

## v0.14.0 - Grow the Surface, Deepen the Practice

**Status: Released**

Theme: expand organic reach through subject SEO pages, unlock professional-audience depth with Interview Practice, extend Long Exam to span multiple notes, and close out the quiz generation performance work deferred from v0.13.0.

Primary focus:

1. ~~**Subject landing pages (SEO)**~~ ✅ — server-rendered `/public/library/[subject]` pages with per-subject metadata, decay-ranked sections, and static generation shipped in v0.14.0.

2. ~~**Faster quiz generation**~~ ✅ — Board Exam dedicated simulation prompts, async Long Exam generation, and parallel LLM calls with sequential fallback shipped in v0.14.0.

3. ~~**Interview Practice Mode (Professional Profile)**~~ ✅ — shipped in v0.14.0 as an Adaptive Practice sub-mode (`ADAPTIVE` discriminator, `subMode: "INTERVIEW"` in session JSONB); 5-mode contract preserved; Pro-only, 10/month dedicated quota; `gpt-4.1-mini` critique + `gpt-4.1` generation; Interview Readiness Report result. Full spec in `docs/features/professional-profile.md`.

4. ~~**Multi-note Long Exam**~~ ✅ — shipped in v0.14.0; Pro users can add up to 3 same-subject notes to one Long Exam, with source refs stored in session JSONB and questions split proportionally by source.

5. ~~**Stale docs cleanup**~~ ✅ — removed 17 stale/legacy files; merged AI generation spec and overflow menu rules into active docs shipped in v0.14.0.

### Implementation stances

- Subject landing pages must be server-rendered; do not implement as a client-rendered filter redirect
- Interview Practice must reuse the `ADAPTIVE` engine discriminator and carry sub-mode identity in session JSONB (`subMode: "INTERVIEW"`). Do not introduce a new `QuickReviewSessionMode` enum value. Do not add a 6th mode. Do not introduce new persistence aggregates.
- Interview Practice must use `gpt-4.1-mini` for per-answer critique calls and `gpt-4.1` for generation. Do not unify on the premium model — the cost split is the launch viability case.
- Interview Practice quota is dedicated (10/month Pro-only) and tracked separately from Adaptive Practice / Challenge Quiz quotas. Do not double-charge other quotas.
- Multi-note Long Exam must reuse the existing session lifecycle; no new persistence aggregate
- Faster generation changes must be gated behind findings; do not optimize speculatively

---

## v0.13.0 - Complete the Promise, Reach New Audiences

**Status: Released**

Theme: ship the modes that were already promised (Long Exam), open NoteLib to a second audience (Professional Profile), improve organic discovery through SEO, and close out infrastructure research items deferred from v0.12.0.

Primary focus:

1. **Long Exam Mode v1 (Student-facing, Pro-only)** — backend session support, fixed long-form generation (not progressive), forfeit-only leave, mastery report result screen; single-note at launch; shared Advanced Exam quota bucket with Board Exam Mode

   - Backend: `LONG_EXAM` discriminator on `QuickReviewSessionMode`; question set generated and committed in full before the session starts; mastery report data stored in session state JSONB; reuses existing session lifecycle (`GENERATING → IN_PROGRESS → COMPLETED / FORFEITED / FAILED`) and generation lock
   - Frontend: setup confirmation screen with expected duration; fixed progress indicator (no `+5 Questions` control); Board Exam-style top bar with server-anchored countdown timer (90s/question); leave = forfeit — no pause/resume option exposed to the user (anti-procrastination principle, matches Board Exam behavior); mastery report result screen (coverage, weak domains, suggested next step, inline learner-level pill allowed)
   - Access: Pro-only at launch; single note; multi-note deferred to v0.14.0+
   - Profile visibility: Student profile (primary emphasis), Board Taker profile (secondary, less ceremony than Board Exam); hidden from Professional profile

2. **Professional Profile activation** — `PROFESSIONAL` profile type is no longer `Coming Soon`; users can select it in onboarding and profile settings; profile-aware mode label overrides and professional-framed dashboard

   - Backend: no new entities; `PROFESSIONAL` enum already existed
   - Frontend: `lib/exam-mode-visibility.ts` updated so Professional profile shows `Certification Review` (Challenge Quiz) and `Full Practice Exam` (Long Exam); Board Exam hidden; professional dashboard framing; Professional option in onboarding with profile icon; learner level grouped picker shows "Recommended for Professionals"; labels are display-only — engine discriminators (`CHALLENGE`, `LONG_EXAM`) unchanged
   - Access: All plans (same access rules as Student)
   - **Interview Practice Mode deferred to v0.14.0+** — requires a conversational AI evaluation engine not present in the current quiz architecture; see `docs/features/professional-profile.md`

3. **Faster quiz generation** — promote from research-only (v0.12.0 deferred) to research → implement; profile current LLM latency end-to-end (prompt build, API call, JSON parse, DB write); evaluate streaming responses to unblock frontend earlier, model selection (`gpt-4.1-mini` for quiz generation), and early session creation; implement the approach that findings support; frontend may gain a generation progress indicator if streaming is adopted

4. **Subject landing pages (SEO)** — proper server-rendered `/public/library/[subject]` landing pages replacing the current redirect to the filtered library; static `<title>` and `<meta description>` per subject; server-rendered note cards ranked by decay scoring; sitemap update to include subject pages; deferred from v0.12.0 (ROADMAP item J)

5. **Proration / recomputation design doc** — design how mid-cycle plan changes (upgrade and downgrade) recompute Study Pack and quiz quotas; output: a design doc under `docs/product/`; no implementation until the design is reviewed; deferred from v0.12.0

6. **Stale docs cleanup** — audit `docs/` for files still referencing v0.11.0 or earlier resolved items; update or remove

### Implementation stances

- Professional Profile must not fork entity tables — all profiles share the same Note/StudyPack/Session model
- Long Exam backend must reuse the existing session lifecycle and generation lock; no new persistence aggregate
- Subject landing pages must be server-rendered; do not implement as a client-rendered filter redirect
- No proration implementation until the design doc is reviewed and approved
- Exactly five quiz-flavored modes exist: Quick Review, Challenge Quiz, Adaptive Practice, Long Exam, Board Exam; adding a sixth requires updating `docs/product/EXAM_MODES.md` and this roadmap together

---

## v0.12.0 - Learning Experience, Discovery, and Retention

**Status: Released**

Current phase emphasis:

- improve user conversion and the first-study / first-quiz experience before expanding monetization work
- keep Progressive Challenge Quiz generation as the active quiz-flow optimization path
- treat Board Exam Mode optimization as a separate follow-up after the core quiz flow is more stable

Primary focus:

1. **Public Library public note conversion** *(top priority)* — public notes are shareable but currently function as app detail screens rather than learning pages; a visitor who arrives from a Facebook or social link should immediately understand the topic, see why NoteLib helps them study, interact lightly with the content, and know what to do next without being hard-gated before value is shown

   - add a short topic hook below the note title that anchors the learning angle for visitors
   - add a Quick Check / mini quiz preview section: expose 1–2 questions to public users without requiring login
   - gate continuation of the full quiz, score persistence, and Study Pack generation behind signup/login
   - after signup, route the user toward creating or copying a Study Pack so they land in the product with a clear goal
   - add a soft conversion CTA: `Turn your own notes into something like this`
   - reorder primary CTAs so copy/generate actions appear after the visitor has seen learning value
   - keep `Share` always visible; keep `Copy to My Library` available for signed-in users
   - improve generated note formatting: shorter sections, clearer headings, key-fact blocks, quick recall blocks, less dense paragraphs — so public pages read like a study reviewer, not a raw LLM dump
   - public mini quiz answers must not be persisted for anonymous users; no session is created until the user is authenticated

   Acceptance criteria:
   - a visitor without an account can open a public note, understand the topic, and answer 1–2 questions
   - signup gate appears only after value is shown — not on page load
   - CTA does not feel aggressive or interrupt the reading experience
   - the page works well for Facebook/social sharing use cases
   - no implementation changes to the core Study Pack generation or session flows for authenticated users

2. **Learner Level + Course/Program UX refinement** — quiz generation prompts use saved learner level for difficulty and explanation depth; Course/Program autocomplete suggestions are narrowed by the active subject context; helper text on Learning Profile adapts to the selected learner level; no new onboarding steps
3. **Conversion funnel optimization** — plan-aware CTAs via `getUpgradeCtas(currentPlan)` on all paywall and limit surfaces; post-quiz upgrade nudge on Quick Review and Challenge Quiz result screens; analytics funnel events queryable from the admin dashboard
4. **Proration / recomputation design** — design mid-cycle plan changes (upgrade and downgrade) so quota is recalculated correctly; do not implement until design is approved; document in `docs/architecture/ARCHITECTURE.md`
5. **Retention loops** — continue-studying prompts on Dashboard for users who have recent unfinished sessions; weak-concept reminder emails on a backend schedule; near-limit banners surface reset date and upgrade CTA
6. **Backend Public Library filtering + shareable URLs** — move subject, tags, course/program, search, and audience filters onto the canonical `/public/library` query-param model so students can bookmark and share collections without duplicate public-library routes
7. **Library organization guidance for students** — in-app guidance explains how subjects and Course/Program organize the private Library as it grows; reuse the existing `GuidanceTip` system and add one-time contextual tips at natural growth milestones
8. **Social login — Google first** — add Google OAuth as an alternative to email-and-password login and signup; no other providers until Google is stable
9. **Faster quiz generation investigation** — profile current LLM latency end-to-end for quiz generation; prototype streaming or early session-creation patterns; document findings and a recommended approach in `docs/architecture/` before any implementation
10. **Profile-aware mode selection + Long Exam coming-soon** — mode-selection screen now profile-aware (Students see Challenge Quiz + Long Exam; Board Takers see Challenge Quiz + Board Exam; Teachers skip to challenge setup); Long Exam card and setup screen live as a coming-soon placeholder so mode identity is established; `lib/exam-mode-visibility.ts` added as the single source of truth; accelerated from Medium Priority after doc planning landed
11. **Board Exam premium UX polish (presentation-only)** — pre-flight setup, score-report-style result framing, fullscreen behavior, and removal of the inline learner-level pill on the result screen so Board Exam Mode reads as a simulation and not a "longer Challenge Quiz"; no engine changes; details in `docs/product/EXAM_MODES.md`
12. **Adaptive Practice tier reconciliation** — `PLANS.md` is the canonical source (Plus = 10 / mo, Pro = 30 / mo); align `docs/features/adaptive-practice.md`, `docs/features/quiz.md`, `docs/PROJECT_CONTEXT.md`, and runtime gating to match before any Long Exam monetization work begins

### High Priority (Current Phase)

- **Public creator identity disambiguation** — stop relying on `displayName` alone on Public Library cards and public note detail; use or introduce a stable public creator identifier (username / handle when available, otherwise a generated public slug), keep `displayName` for readability, show handle/slug when disambiguation is needed, and preserve existing public links through compatibility or redirect handling

### Medium Priority (Next Phase)

- **Board Exam Mode optimization** — improve generation speed, explore partial or progressive loading only if it preserves the exam-like experience, and keep progressive generation out of Board Exam Mode for now; identity contract is locked in `docs/product/EXAM_MODES.md`
- **Long Exam Mode v1 (Student-facing, Pro-only)** — backend session support, fixed long-form generation, pause/resume, mastery report result screen; Pro gating and shared Advanced Exam quota
- ~~**Onboarding/profile type icon polish**~~ ✅ shipped in v0.13.0 — emoji icons added to all four active profile type cards in onboarding

### Future Guidance System Expansion (Post-v0.12.0)

The guidance engine introduced in v0.12.0 is intentionally minimal. Future iterations can extend it without changing the `GuidanceTip` component or `guidance.ts` persistence layer:

- **Note editor inline guidance** — contextual tips inside the note editor when `subject` or tags are blank after the first save; use the engine's `condition()` callback to check field state at render time
- **Cooldown-aware rules** — add an optional `cooldownMs` field to `GuidanceRule`; `pickActiveGuidance()` can skip rules shown within the cooldown window using a separate last-shown timestamp key in localStorage
- **Dashboard contextual tips** — tips tied to study-gap detection (e.g., user hasn't quizzed in 7 days) using the same engine pattern; conditions read from dashboard data already loaded on the page
- **Profile completion nudge** — tip on the Dashboard or Profile page when `courseProgram` is unset after the first Study Pack is generated

### Product Direction Note

Board Exam Mode is intentionally kept as a fixed, exam-style experience. Optimization will be handled separately after core quiz flow and conversion improvements are stabilized.

Implementation stances:

- public note pages must teach first, then convert — do not hard-gate visitors before they see value; mini quiz preview is a lightweight surface, not a full session; no anonymous session state is persisted
- generated note formatting should prioritize scannability and study usefulness; prefer short sections, clear headings, key-fact blocks, and exam-friendly wording over long paragraph dumps
- keep Learner Level and Course/Program as separate concerns — Learner Level controls difficulty/style; Course/Program controls domain context — do not merge them
- ~~do not add learner level to onboarding~~ — **reversed in v0.13.0**: onboarding step 2 now collects learner level and course/program directly; Dashboard prompt remains for users who skip onboarding step 2 or completed onboarding before this change
- social login must be an alternative, not a replacement; existing email accounts must continue to work
- quiz latency investigation is research-only in v0.12.0; no production latency changes without findings
- Long Exam Mode is design-only in v0.12.0; canonical mode-hierarchy and identity contract live in `docs/product/EXAM_MODES.md`; no implementation until the spec is reviewed
- exactly five quiz-flavored modes exist: Quick Review, Challenge Quiz, Adaptive Practice, Long Exam, Board Exam; adding a sixth requires updating `docs/product/EXAM_MODES.md` and this roadmap together

### Completed in v0.12.0 so far

- **Public Note Quick Check — multi-question preview** — evolved the single-question Quick Check into a sequential multi-question experience (up to 3 preview questions drawn from the Study Pack quiz); added a progress indicator (`1 / 3`) so visitors know where they are; after submitting each answer, improved feedback microcopy (✅ Correct!, 🧠 Nice work!, Almost there.) and a "Next Question →" button appear before advancing; the final question transitions to a lightweight completion state ("🎉 Quick Check Complete") with CTAs to copy and start practicing; no backend changes, no new AI generation, fallback-safe when fewer than 3 questions exist; notes-first layout preserved — Quick Check remains below Summary and Key Concepts
- **Public note detail engagement polish** — refined the public-note learning hook with a safe fallback; updated Quick Check to feel like a lightweight learning prompt instead of a demo widget; added a post-answer CTA that nudges visitors toward creating or copying their own Study Pack only after value is shown; tightened public-note CTA wording and Full Notes readability without changing quiz/session logic
- **Public Library canonical routing + shareable filters** — consolidated public browsing around `/public/library`; turned `/library/public` and `/public/library/{subject}` into compatibility redirects; synced subject, tag, search, course/program, audience, and sort filters to shareable query params so direct filtered URLs restore the same UI state
- **Public Library URL-filter UX polish** — stabilized the main search with debounced URL sync, preserved scroll position on filter changes, kept tag browsing reachable through a dedicated `Browse all` action, and fixed selector-modal search focus so typing no longer jumps to the close button
- **Public Creator Identity / Attribution** — added unique public usernames as stable handles; public attribution now keeps `displayName` for readability while using `@username` and `/public/creator/{username}` for disambiguation and future creator pages; legacy `/public/profile/{userId}` links remain compatible
- **Social login — Google first** — added Google OAuth login/signup as an alternative to email/password; verified Google emails link to existing accounts instead of creating duplicates; Profile shows connected sign-in methods; Apple/Facebook/GitHub remain out of scope
- **Study Pack metadata correctness** — locked note-level `courseProgram` as the Study Pack generation source of truth with profile fallback only when the note has no course/program saved; fixed normal note-owned generation so AI metadata suggestions stay transient until apply; removed duplicate AI tag suggestions when user tags already overlap; kept onboarding's explicit auto-apply exception for empty metadata fields
- **Quiz metadata context consistency** — Challenge Quiz, Board Exam, and Adaptive Practice now use the same generation-context resolver as Study Pack generation: note-level `courseProgram` first, profile `courseProgram` fallback, and user-level `learnerLevel` for difficulty/style
- **Generate from Topic Course/Program source-of-truth fix** — first generation now reads the current Create Note Course / Program at submit time and sends it immediately; profile Course / Program remains fallback only when no draft value is selected
- **Quiz Ready badge accuracy** — made private Library `Quiz Ready` badges and filters profile-aware: Teacher users keep them for exam-export workflows, while Student and Board Taker users see learner-facing Study Pack readiness only
- **Progressive Challenge Quiz generation** — Challenge mode starts with 5 questions; users generate +5 more from the last question, up to 20 per session; `POST /challenge-quiz/sessions/{sessionId}/generate-more` endpoint; `GenerateMoreChallengeQuizResponse` DTO; `NotEnoughNewQuestionsException` with `NOT_ENOUGH_NEW_QUESTIONS` code; `QuizDeduplicationUtils.uniqueQuestions()` post-generation dedup; `QuizSessionStateUtils.appendQuizItems()` JSONB append; Board Exam Mode is exempt
- **Progressive quiz scoring** — score computed from answered questions (`selectedChoices.size()`) instead of fixed total; result screen shows `{correct} of {answered} answered correctly`; Score Summary column labeled `Answered`
- **Challenge Quiz UX refinements** — `Complete Quiz` replaces `Submit Challenge Quiz`; `+5 Questions` / `Adding...` button at last question; microcopy banner at quiz top; progression-aware hint at last question (`"Good start — want to keep going?"` at 5 q, `"10 questions in — push to 15?"` at 10 q, `"Almost there — finish with all 20?"` at 15 q, `"You've answered all {n} questions — ready to submit?"` at cap); generate-more toast updated to `"Challenge extended to {n} questions"` / `"Full challenge unlocked: 20 questions"`; `noMoreQuestions` state hides `+5 Questions` silently
- **Leave Quiz modal stability fix** — `onBeforeRouteLeave` and `onConfirmLeave` memoized via `useCallback` in `page.tsx`; `onConfirmLeave` reads from `challengeSessionRef.current` to avoid stale closures; prevents `LeaveQuizModal` from unmounting/remounting on every timer tick
- **Analytics enum completeness fix** — added missing `QUICK_REVIEW_COMPLETED`, `CHALLENGE_QUIZ_COMPLETED`, `ADAPTIVE_PRACTICE_COMPLETED`, and `ONBOARDING_V2_CTA_GO_TO_SAVED_NOTE` to `AnalyticsEventType` Java enum to resolve `HttpMessageNotReadableException` on quiz completion events
- **Conversion funnel + quiz UX refinement pass** — `PaywallModal` plan cards selectable with ring highlight, single `Continue with [Plan]` footer CTA, PRO-user calm message instead of disabled cards; `StudyPackLimitModal` trimmed to primary CTA + `Maybe Later` for FREE/PLUS and a single `Got It` for PRO; `getUpgradeCtas` extended with optional `UpgradeCtaContext` for context-aware copy (`"Get More Study Packs"`, `"Unlock Adaptive Practice"`); Quick Review result adds guidance text and renames `Practice Again` → `Retry Quick Review`; Dashboard and Library empty states updated to guided copy
- **Retention loop — continue studying + focus areas** — Continue Studying session priority reordered to Challenge Quiz → Adaptive Practice → Quick Review; Continue Studying body copy is mode-aware (`"You left off on Question 4 of 10 in your Challenge Quiz."`); Focus Areas free-tier fallback: Free/Plus users see `"Revisit Note"` when weak concepts exist but Adaptive Practice is locked, instead of only an upgrade prompt; `MEANINGFUL_STUDY_ACTIVITIES` constant deduplicated to `ActivityType.MEANINGFUL_STUDY_ACTIVITIES`
- **Guidance Foundation System** — minimal guidance engine (`lib/guidance-engine.ts`) with `GuidanceRule` type and `pickActiveGuidance()` function; two contextual library tips at natural growth milestones (notes 1–3 and notes ≥ 5); Dashboard personalization prompt bug fixed (suppressed when learner level already set); prompt repositioned after primary study action for all three profile types
- **Profile-aware mode selection + Long Exam coming-soon** — `lib/exam-mode-visibility.ts` is the single source of truth for which modes appear per profile; Students see Challenge Quiz + Long Exam (coming-soon); Board Takers see Challenge Quiz + Board Exam; Teachers skip to challenge setup directly; cross-profile escape hatch guides Students toward Board Exam via profile switch; Long Exam mode card and coming-soon setup screen live with disabled CTA; backend session logic ships in v0.13.0
- **Board Exam premium UX polish (presentation-only)** — pre-flight setup screen replaced with a simulation-framing checklist ("Begin Board Exam", 5 pre-flight items); result screen subtitle changed to "Score Report"; inline learner-level pill hidden on Board Exam result (`!isBoardExamMode` guard); `PostSuccessUpgradeNudge` hidden on Board Exam result; no engine changes
- **Adaptive Practice tier reconciliation** — `StudySnapProperties` defaults corrected (`adaptivePracticeProOnly=false`, `plusMonthlyAdaptivePracticeLimit=10`); `application.yaml` default updated; `docs/features/adaptive-practice.md`, `docs/features/quiz.md`, and `docs/PROJECT_CONTEXT.md` now all reflect Plus = 10 / mo, Pro = 30 / mo; Open Discrepancy #1 in `EXAM_MODES.md` closed
- **Learner Level grouped picker on quiz result screens** — Quick Review and Challenge Quiz result screens now render learner level chips in two profile-aware groups (Recommended / Other Learning Styles) via `getGroupedLearnerLevels(viewerProfileType)`; `viewerProfileType` state added to Quick Review and synced via auth listener; profile page combobox (already grouped) unchanged
- **Public Library conversion funnel polish (recommendations A–G)** — related-notes block in quiz completion card ("More from {Subject}", up to 3 engagement-ranked notes from same subject, server-side fetch via shared 5-min cache); auth-prompt consolidation into `AppModal` pattern with copy-intent redirect URLs (`guestAuthMode` prop removed from all callers); dead tabbed-content component deleted; `PublicPracticeModeTeaser` placed after Full Notes on public note detail (Challenge Quiz + Adaptive Practice free, Board Exam Mode Pro chip, gated on `!isDraft`); time-decayed Featured score (30-day half-life, 10% floor) applied in both `computeDiscoveryScore` (frontend) and `computeScore` (backend) with synchronized formulas and testable `now` parameter; recommendation H blocked pending backend windowed count fields; recommendation J (subject landing pages) deferred

## v0.11.0 — Completed

Completed in `v0.11.0`:

- learning loop positioning across the landing page and product messaging
- onboarding flow redesign: experience-first 5-step flow that ends with a generated Study Pack
- Generate Note from topic available in both onboarding and Create Note
- Create Note UX improvements with write vs generate entry options
- Xendit payment integration with hosted checkout and webhook-confirmed activation
- Xendit payment hardening:
  - correct PHP invoice amount handling
  - pending checkout reuse instead of duplicate pending payments
  - config-driven Monthly and Annual manual checkout amounts
  - automatic intro-offer and voucher application during checkout
  - voucher redemption persistence only after successful `PAID` webhook
  - safe internal `returnUrl` support back to the interrupted page
  - success-page routing that returns Settings/Billing upgrades to Dashboard and paywall upgrades to the interrupted flow
  - polished billing success and failed result pages
  - manual-renewal expiry windows after Monthly (`30` days) and Annual (`365` days) payments
  - subscriptions-table source of truth for plan state, active-subscription history preservation, and webhook-driven renewal extension
- Free / Plus / Pro multi-plan billing model replacing the legacy single-tier paid plan
- Settings Plan & Billing redesign: billing cycle toggle + 3-column plan cards (Free, Plus, Pro)
- pricing system unification through a shared frontend plan config used by landing, pricing, and settings surfaces
- conversion-focused paywall redesign with context-aware copy, autosave-before-checkout, and resume-after-upgrade flow restoration
- documentation context cleanup so product, architecture, and feature docs match the current Free / Plus / Pro, Xendit, onboarding, and paywall behavior
- legacy billing-provider runtime removal and local ngrok-based webhook testing support
- copy alignment around `Generate Study Pack`
- activation improvement: users leave onboarding with real content, not an empty dashboard
- content moderation: `ContentModerationService` with token-based dictionary matching at note title, Study Pack topic, and note content creation boundaries; English and Filipino banned-word dictionaries; 52 tests
- plan-aware upgrade CTAs: `getUpgradeCtas(currentPlan)` helper in `frontend/src/config/plans.ts`; upgrade surfaces route to `/settings?section=plans` instead of `/pricing`
- Settings `?section=plans` auto-scroll and highlight ring on the Plan & Billing card
- post-quiz `PostSuccessUpgradeNudge` on Quick Review and Challenge Quiz result screens with plan-aware CTAs and sessionStorage dismissal
- analytics funnel events: `QUICK_REVIEW_COMPLETED`, `CHALLENGE_QUIZ_COMPLETED`, `ADAPTIVE_PRACTICE_COMPLETED`, `ONBOARDING_V2_CTA_GO_TO_SAVED_NOTE` added to `AnalyticsEventType`
- onboarding Study Pack limit handling: bumps to Step 5 with `studyPackLimitReached` flag; shows `NearLimitBanner` and note-navigation CTAs; fires `completeOnboarding` via existing useEffect

### v0.6.0 - Landing Revamp & Positioning

Primary focus:

- Landing-page messaging revamp that positions NoteLib as a notes library and study workspace first
- Public Library promotion as a top-level public discovery route
- Learn-page integration for the active-recall study method
- Public navbar alignment across landing, learn, pricing, login, and Public Library
- SEO title, meta description, and Open Graph metadata alignment with the new positioning
- Open Graph image refresh to match the new messaging before the release is cut
- Landing pricing section updated to Free / Plus / Pro cards with intro offer pricing and "Manual renewal. No automatic charges." footer
- Demo page redesigned as a 5-step interactive flow (choose start → input → generated note → Study Pack CTA → Study Pack results) using static prebuilt content only — no backend or LLM calls
- Landing hero repositioned around exam-readiness: "Turn your notes into exam-ready study materials in seconds"
- "Why NoteLib" section updated with 3 benefit cards (Built for studying, Learn from your weak points, From notes to mastery)
- Demo enhanced with interactive per-question quiz (select before reveal), exam context copy, and post-quiz conversion CTA
- Pricing cards updated with plan descriptions tied to learner stage; export feature description added; Plus includes Adaptive Practice (10/month)
- Product positioning principles added to AGENTS.md: learning-outcome framing, demo as conversion driver, clear plan progression

Implementation stance:

- position NoteLib as an exam-focused study tool, not a generic AI utility
- hero and pricing copy must frame features in terms of learning outcomes
- demo must feel like a guided experience that creates an "aha moment" before the CTA
- Free → Plus → Pro should feel like natural progression for a growing student
- treat Public Library as a public growth and discovery feature, not a paid feature
- keep public marketing pages accessible without login
- align landing, SEO, and README messaging around the same product identity before `v0.6.0` is tagged

### v0.7.0 - Learning & Metadata Foundation

Primary focus:

- Learner Level on the user profile and onboarding
- required Learning Profile `Course / Program` plus optional per-note `Course / Program` metadata
- note-level `courseProgram` metadata with profile-defaulted note creation
- stronger note metadata quality through subject autocomplete, saved custom subjects, and tag guidance
- field-level AI metadata suggestions so users keep final control of title, subject, and tags
- a dedicated `Learning Profile` card on private Profile
- richer Public Profile identity with learner-level/course context when provided
- generation-context plumbing so future quiz prompts can use learner metadata safely

Implementation stance:

- keep learner metadata on the existing `users` aggregate instead of creating profile-type-specific tables
- keep note-level `Course / Program` optional while requiring it for onboarding and later Learning Profile saves
- prepare smarter quiz generation by passing learner metadata through backend generation context before prompt behavior changes
- improve library/public-profile structure over time without changing note ownership or page responsibilities

### v0.8.0 - Board Exam Mode

Primary focus:

- Async Study Pack generation handoff from Note Editor to Note Detail
- Graceful Study Pack generation failure and retry recovery
- Quiz start integrity locks for exam-like Challenge Quiz starts
- Exam Countdown
- Exam Readiness Score
- Study Plan
- Mock Exam Mode
- Performance Analytics

Implementation stance:

- keep Board Exam Mode on the same shared note-first engine
- do not fork entities or tables by profile type
- use the existing `Note -> Study Pack -> Quiz -> Activity -> Weak Concepts` pipeline
- emphasize exam-prep presentation, recommendations, and analytics without merging page responsibilities

## Current Product Shape

Navigation:

- Main:
  - Dashboard
  - Library
  - Public Library
- Account:
  - Profile
  - Settings
  - Admin (admins only)

Core routes:

- `/dashboard`
- `/library`
- `/public/library`
- `/notes/{id}`
- `/notes/{id}/sessions/{sessionId}` for session review
- `/public/library/{subject}`
- `/public/library/{subject}/{slug}`
- `/public/profile/{userId}`

Current session-review UX:

- desktop and mobile both open the same dedicated session-review page from `Recent Sessions`
- Note Detail stays the entry point for history, while the dedicated review page owns focused answer review

## Future Directions

### Background Quiz Pre-Generation (de-scoped from v0.17.0)

Challenge Quiz was the original pooling candidate but was de-scoped: its progressive generation model (5 → 10 → 15 → 20 questions on demand) already limits the initial wait to a single 5-question LLM call, so pre-generation adds LLM cost without a meaningful UX benefit. Additionally, pre-generating the initial batch and then generating extensions on demand would introduce concurrency waste (pool generation and on-demand generation could race for the same note).

Revisit for a different mode if a cold-start bottleneck is confirmed by usage data — e.g., a mode with a fixed long question set where the full generation is the bottleneck (Long Exam already uses the `ExamQuestionPoolService` pool mechanism for this). Do not pre-generate Challenge Quiz.

### Exam-mode work (planned)

- **Multi-note Long Exam** — shipped in v0.14.0
- **Board Exam advanced result analytics** — promoted into the active v0.15.0 premium-mode result presentation scope
- **Multi-note Board Exam** — planned for v0.19.0; see v0.19.0 section above for full spec
- **Long Exam tier promotion to Plus** — only if usage data justifies the LLM cost; not part of the current v0.15.0 cap refactor unless the cost review supports it
- **Planning-only** — cross-profile mode unlock (Students opting into Board Exam without changing profile); curated exam decks / cohort content (Pro+); cross-profile journey (Student → Board Taker upgrade flow with continuity)

### Premium mode uplift + cost-control quota refactor

Promoted to the active `v0.15.0 - Premium Mode Uplift + Cost-Control Quota Refactor` section above.

### Interview Practice evolution

Initial evaluation is promoted to the active v0.15.0 section. The following remain unsequenced follow-up directions and should not be committed until Interview Practice v1 has usage data from at least one billing cycle.

- **Multi-note Interview Practice (smart context aggregation)** — generate from the base note plus related notes that share `courseProgram` and at least one tag; cap at 2–3 sibling notes to manage prompt size and per-session cost; the dashboard entry already selects the most relevant note, so multi-note adds breadth across a topic, not a replacement for that smart selection; do not implement until v1 usage data confirms users want wider scenario coverage than a single note provides
- **Structured interview templates by role/job family** — opinionated section breakdowns (e.g. Backend Engineer = PL fundamentals + DB + Behavioral); requires either a curated role taxonomy or a user-defined template builder; do not build until v1 usage data shows real demand and the section-aware generation prompt's limitations are observed
- **Open-ended / conversational evaluation** — replace MC structure with free-text answers and AI rubric scoring; architecturally heavy (new session schema, new evaluation pipeline, new result model); only consider if MC + critique format hits its ceiling and Pro users explicitly ask for it
- **Profile / role enrichment** — capture target role explicitly on the user profile (instead of inferring from notes) to drive better generation context; bigger architectural decision; do not bundle with any of the above — design separately
- **Interview Practice tier promotion to Plus** — only if v0.14.0 usage data justifies the LLM cost; current model split (gpt-4.1 generation + gpt-4.1-mini critique) is what makes Pro-only economically viable, and lowering the tier requires re-running that math

### Lesson Plan for Teachers (future, unsequenced)

A lightweight collection entity grouping an ordered set of notes into a lesson plan for teacher-profile users. No AI synthesis at the plan level — a lesson plan is a playlist, not a synthesized document.

- New `LessonPlan` entity: title, description, ordered list of note references with optional week/topic labels per item
- No new AI generation at plan creation; Study Pack and Quiz generation still happen per-note using existing quotas — no new quota category
- Teacher dashboard gains a "Lesson Plans" section alongside the library; notes remain individually owned and independently editable
- DOCX export from a lesson plan produces a multi-section packet: one quiz section per note in lesson-plan order
- "Generate Quiz for Lesson Plan" toolbar action generates quizzes for each note in sequence, consuming the teacher's existing quiz quota per note
- Requires backend: new `LessonPlan` entity + ordering join table; frontend: lesson plan creation UI + DOCX multi-section export
- Do not implement Option B (multi-note AI synthesis across all notes in the plan) in v1 — risk of lower-quality synthesis and significantly higher LLM cost per plan; Option A (collection model) delivers the organization and sequencing value teachers need without new AI spend

### Study Pack Section Improvements (future, unsequenced)

The current Study Pack format (Overview / Key Idea / Core Details / Why It Matters / Quick Recall) is consistent but template-locked — every note produces the same five sections regardless of subject or content type. Planned improvements in order of effort:

- **Common Misconceptions section** — names what students typically get wrong about the topic; high quiz-prep value; prompt-only, no schema change; hotfix-deployable
- **Richer Quick Recall** — expand term-definition pairs to include a memory hook (analogy, mnemonic, or visual cue); prompt-only; hotfix-deployable
- **Comparison tables** — when the note contrasts two or more concepts, generate a structured markdown table instead of parallel bullet lists; prompt-only; hotfix-deployable
- **Concept relationships** — prerequisite chains ("Understand X before this") and contrast pointers ("Different from Y because..."); prompt-only; hotfix-deployable
- **Subject-adaptive section templates** — STEM notes get an Equations + Variables block and a Worked Example; humanities notes get a Timeline or Key Arguments section; may require a `sectionType` field addition to the key concept schema if the current JSONB storage cannot accommodate variable section shapes; requires a design pass before implementation

The first four improvements are prompt-only and can ship as hotfixes without schema changes. Subject-adaptive templates require a design review on the key concept schema before implementation and should not be bundled with the prompt-only items.

### Public Library Discovery — Future Items

- **Trending this week section (H)** — a new discovery section above Featured showing notes gaining traction in the last 7 days; blocked on backend: `NoteListItemResponse` has no windowed engagement fields; requires `recentCopyCount` / `recentLikeCount` or a precomputed rolling 7-day aggregate before this section can be built correctly; do not implement under a "Trending" label using lifetime totals — the signal would be misleading
- **Subject landing pages (J)** — moved to v0.14.0 scope; `/public/library/[subject]` proper server-rendered landing pages with per-subject metadata and decay-ranked note cards

Potential expansion areas after `v0.8.0`:

- richer note workspace
- deeper progress insights from quiz history
- board-exam-specific recommendations and weak-area planning
- optional public-profile enhancements such as followers, likes, and creator bios
- optional snapshot/history tables if product value is proven

### Billing Improvements (Future)

- recurring subscription support
- coupon-code entry UI
- cancel subscription flow
- billing portal / self-serve billing management
- automatic renewal
- invoices / receipts UI
- billing history UI improvements
- plan switching and downgrade flows
- provider-managed recurring billing via `provider_subscription_id`

### Account Management (Future)

- forgot password flow
- change password from Settings
- email verification and account-security improvements beyond the current launch flow

### Connected Account Management / Auth Provider Management (Future)

Future improvements for users with multiple sign-in methods. The Google OAuth foundation (account linking, `email_verified` guard, Profile sign-in method status) is in place; these are follow-on improvements that require their own design pass before implementation.

Potential scope:

- unlink Google account safely — must prevent lockout when Google is the only sign-in method
- add/change password for Google-only users — allow switching to or adding email/password without forcing a full re-signup
- multiple provider support — Apple, Microsoft, etc.; do not add until Google is stable and provider abstraction is reviewed
- connected account security UX — notify users by email when a new provider is linked to their account
- account recovery flows for social-login users — what happens when Google revokes access or the associated email changes
- provider conflict resolution UI — surface a clear choice when a Google email matches an existing email/password account
- recent login/session visibility — show sign-in history and active sessions in Settings for security-aware users
- email change flow with connected providers — changing the NoteLib email when a Google account is linked needs careful sequencing

Implementation notes for future reference:

- the provider abstraction should live in a shared auth provider layer, not scattered across login and signup flows
- lockout prevention: never allow unlinking the only auth method unless an alternative is confirmed first
- Google-only users have no password; the "add password" flow must go through a secure email-based credential creation path
- do not add more providers until the connected-account management UX is designed; adding providers without management UX creates a support burden

### Public Library persona filtering (roadmap)

Planned for a future release after the mode system matures:

- persona-based note recommendations in Public Library discovery (same-profile notes ranked higher)
- cross-profile discovery still allowed so learners can find materials outside their profile type
- filtering UI: optional "Relevant to me" toggle that uses the current user's profile mode for ranking
- implementation must remain additive — no ranking change without the toggle enabled
- do not build until there are enough public notes per profile type to make filtering meaningful

## Known UX Fixes (cross-cutting, no version gate)

These are correctness fixes that ship as soon as they are ready and are not held to a version milestone.

- ~~**AI suggestion modal survives navigation**~~ ✅ — fixed in v0.15.1 branch: `awaitingGeneratedMetadataSuggestionRef` was in-memory-only and reset on component remount; navigating away mid-generation and returning silently skipped the AI title/subject/tags modal; replaced with a `sessionStorage` key (`notelib-awaiting-suggestion:{noteId}`) that is set when generation starts and cleared after the modal fires; `loadDetail` re-arms the ref from storage when returning to a still-generating note so the polling effect can still trigger the modal on completion.

## Product Learning Loop

Capture -> Generate -> Review -> Improve -> Copy -> Repeat

Roadmap decisions should reinforce this loop rather than one-time output generation.

## Legacy planning context

Older phase-by-phase roadmap details are preserved in `/docs/legacy/ROADMAP.md`.
