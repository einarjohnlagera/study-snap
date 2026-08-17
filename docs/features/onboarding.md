# onboarding.md - NoteLib Feature Context

Teacher Learner Level copy names the field's role directly: it is the default quiz difficulty for material they generate, while preserving the existing helper and per-quiz override behavior.

## Goal

`/onboarding` is a short activation flow for verified users. Its job is to get the user to a real first Study Pack, not to collect every preference up front.

Current onboarding is intentionally low-friction:

- it happens once after first verified entry
- it ends with a generated Study Pack
- it collects the generation context needed for the first Study Pack, then defers later preference details to Profile and Settings

## Activation Rule

- verified users who have not completed onboarding are routed to `/onboarding`
- verified users who completed onboarding but are missing `profileType` are routed to `/onboarding` for a focused, blocking profile-type prompt only
- public pages and anonymous flows are never blocked by onboarding
- users with both `onboardingCompletedAt` and `profileType` set should be sent to `/dashboard`
- backend content-creating mutations enforce profile setup server-side; client guards improve UX but are not the boundary

## Post-Onboarding Discovery Intent

An anonymous visitor who clicks Adopt on `/explore` carries that action through signup, verification, and onboarding in the short-lived `notelib-discovery-intent` cookie. The cookie stores the published plan id, Goal-vs-leaf shape, and safe Explore tab/query context; it uses `max-age=1800`, `SameSite=Strict`, and `path=/`, matching the established exam-intent lifecycle.

Dashboard is the post-onboarding consumption point. The discovery-intent handoff is mounted **inside Dashboard's loaded branch, not beside the page header** — child effects commit before parent effects, so mounting it above the `loading` gate ran it *before* `requireAuthenticatedOnboardedUser`, burning the cookie for a signed-out visitor (who was then told their session expired) or a not-yet-onboarded one (whose adoption succeeded invisibly). Reaching that branch means the guard has already passed. It reads and clears the discovery cookie before calling the existing authenticated adopt action, then replaces the Dashboard history entry with the adopted collection. **Clearing before the `await` is the entire one-shot guarantee**, because cookie writes are synchronous: a StrictMode double-invoke or a remount re-reads null and returns. Moving that clear after the `await` produces a genuine double adoption. **Navigation is suppressed if the visitor has since navigated away** — compared by start path rather than a mounted-flag, since StrictMode's synthetic unmount is indistinguishable from a real one and a mounted-flag would suppress the legitimate navigation instead. If the source plan disappeared, the learner returns to the saved Explore context with a normal unavailable notice; malformed or partial values are cleared, and blocked cookies do not interrupt signup — **but they are no longer silent.** `setDiscoveryIntentCookie` reads the cookie back rather than trusting the assignment (a blocked cookie jar accepts the write and stores nothing), and returns whether it landed. On failure the visitor is sent to signup with `intent=discovery-adopt-unsaved`, whose copy tells them to pick the plan again instead of implying it is waiting. **The destination is returned by the click handler rather than precomputed as a prop** — whether the write succeeded is only known inside that click, and React state set there is not readable until the next render, so a prop would always be one click stale.

Discovery intent wins if `notelib-exam-intent` is also present: the explicit Adopt click is newer and more specific than an exam-goal suggestion. **The exam cookie is cleared only once the adoption has actually succeeded**, not up front — with no adoption there is no competing action to suppress, and discarding it early cost the visitor a prompt they were entitled to.

## Current Flow

Route: `/onboarding`

Eight screens, with one primary question or terminal state per screen:

1. `Profile Type` — selection, explicit Continue
2. `Course / Program` — typed searchable combobox, explicit Continue
3. `Learner Level` — closed-set `<select>`, explicit Continue
4. `First Intent` — closed-set selection, **tap-to-advance** (Back only, no Continue)
5. Branches on the Screen 4 answer:
   - `Input Method` (built-from-my-own-notes) — closed-set selection, **tap-to-advance** (Back only)
   - `Official Study Plan` / `unavailable-program fallback` (ready-made) — selection plus one explicit action
6. `The Note` — topic or write/paste input, explicit action
7. `Study Pack Generation` — automatic
8. `Completion` — learner chooses the destination

**Screen 3 carries an optional exam-date field for `BOARD_EXAM`, and it stays.** The flow is "one *required*
question per screen"; this is a secondary optional control, not a second question. Removing it was scoped in
`v0.73.0`, never built, and then **decided against on review** — the post-session commitment prompt that
duplicates the ask lives on session-completion screens, so it reaches only learners who finish a session, while
onboarding reaches everyone who gets this far. Deleting it here would leave exam-bound learners who never
complete a session with no exam date at all, which silently disables the board-exam countdown
(`DashboardService.java:294-297`). The later prompt prefills from whatever is captured here, so the duplication
costs the learner nothing. **Do not re-propose removing it without new evidence about the
non-session-completing population.**

**Which screens auto-advance, and why the rule is not uniform.** Tap-to-advance applies only where every option
is a closed-set choice that keeps the learner inside onboarding — Screens 4 and 5's input method. Three
categories are deliberately excluded:

- **Typed input** (Course / Program, the note) never auto-advances on blur, debounce, or catalog selection,
  because only the learner knows when typing is finished.
- **Screen 3's `<select>`** keeps an explicit Continue for a specific reason: re-choosing the value that is
  already selected fires no `change` event. Auto-advancing it stranded any learner who arrived with a value
  already set — by resuming a draft, or simply by pressing Back from Screen 4 — because the only control left
  on the screen was Back. Do not re-introduce auto-advance here.
- **Screen 5's ready-made fallback** is a selection plus an explicit action button, because two of its options
  (`Explore related public notes`, `Go to Dashboard`) leave onboarding entirely. A mis-tap must not eject a
  learner mid-flow. The action button names its consequence: **Continue** when the selection keeps them in
  onboarding, **Finish** when it hands them to the Public Library.

Auto-advance is event-driven from a new selection; landing on an answered screen through Back navigation does
not advance it again. The separate completed-but-null-profile-type repair path remains a single screen and does
not enter this eight-screen flow.

**Screen 5 serves both branches, and the step counter never lies about it.** Choosing ready-made moves to
Screen 5 exactly as the own-notes door does; it is not a sub-state of Screen 4. The counter shows the real step
throughout — including on the adopt screen, where the learner simply finishes onboarding early at Screen 5 of 8.
An earlier build special-cased the adopt screen to display the last step, which read as a bug.

**Screen 3's copy is profile-aware too, and its description is not optional.** `getLearnerLevelScreenCopy`
returns the heading and description per profile type. *"What are you studying?"* has an obvious consequence;
*"what level?"* does not — nothing else on that screen says it governs how hard quizzes are and how deep
explanations go. The typography pass removed this line for everyone **except teachers**, which left the
majority path barer than the minority one; it is restored for all four types. A teacher is asked what level
they *teach*, since for them the answer means default quiz difficulty.

**Screen 2's copy is profile-aware (C9).** `getCourseProgramScreenCopy` in `lib/onboarding-v2.ts` returns the
heading, description and placeholder per profile type. Slice 5 moved profile type to Screen 1, so by the time
this screen renders we know who is being asked — yet the copy stayed byte-identical for all four types, and
under the two-mode authoring model *"What are you studying?"* describes something a `TEACHER` never does here.
Teachers are asked what they teach, exam reviewers what they are reviewing for, professionals what field they
are in. Adding a profile type means adding its copy here, not reusing the student wording.

**Screens carry one idea each (typography pass).** Step headings are `text-xl sm:text-2xl` — `CardTitle` is
already `font-semibold`, so size, not weight, was what made them read heavy. Field labels and "Required."
helper paragraphs are gone from Screens 2 and 3: the heading already asks the question, the control carries its
own accessible name, and required-ness is signalled by a disabled Continue rather than an asterisk. Placeholders
carry an **example** (`e.g. BS Civil Engineering`) rather than restating the control.

**One exception, deliberately kept:** for a `TEACHER`, Screen 3's field means *default quiz difficulty*, not
their own study level. That is information the heading does not give, so it survives as a one-line description
for teachers only. Do not delete it as part of a future copy trim.

**Option cards are stacked vertically on every screen** (Screens 1, 4 and 5), so adding a profile type or a
door does not reflow the layout.

### Step 1 — Profile Type

Options:

- `Student`
- `Board Taker`
- `Teacher`
- `Professional`

This is the only identity-like field collected during onboarding.

### Profile Type Re-prompt

Legacy users may have `onboardingCompletedAt` set while `profileType` is still null.

In that case `/onboarding` renders only Step 1:

- no learner-level or course/program prompt
- no exam-date prompt
- no note or Study Pack generation
- submit uses `POST /auth/onboarding/profile-type`

Do not backfill or silently default `profileType`. The user must choose the correct profile type.

### Copy-on-signup lightweight profile completion

Copy-on-signup is a distinct, narrow alternate path for a newly verified visitor whose public-note copy succeeded before normal onboarding.

- Verification writes a per-user `notelib.lightweight-profile-completion-pending` marker before routing to the copied note's existing Quick Review destination.
- The marker exempts only this cohort from the immediate onboarding redirect, so the copied note and its auto-launched Quick Review render before profile setup interrupts the session.
- The first Dashboard visit shows a dismissible, non-blocking profile-completion card only while the marker is present and `profileType`, `learnerLevel`, or `courseProgram` is missing. It reuses the normal Profile Type choices, learner-level selector, course/program combobox with custom entry disabled, and the optional Board Exam date field.
- Saving calls the existing Learning Profile update first, then `POST /auth/onboarding` for profile type and optional exam date. If the second call fails, the card keeps the saved learning context and retries only profile completion.
- Dismissing the card does not clear the completion marker. Its per-user local dismissal lasts for the current day, so the card can reappear on a later Dashboard visit until completion succeeds.
- On full success the marker is cleared and the local auth cache is refreshed. No new backend endpoint, DTO, or migration is involved.

This does not alter the eight-screen `/onboarding` flow, its order, or the legacy profile-type-only re-prompt for any other cohort. If marker storage is unavailable, the app fails open to the existing `/onboarding` redirect behavior.

### Screen 2 — Course / Program

**CORRECTED 2026-08-06 — this step was previously documented as "Study Goal", with the claim that "goal options
are filtered by the selected profile type." No study-goal selector exists anywhere in the onboarding flow.**
`studyGoal` is a separate profile field written by `PUT /users/profile/goal`, which onboarding never calls. The
step numbering list above carried the same stale name and is corrected with it.

Screen 2 asks for required `Course / Program` through `CourseProgramCombobox`. It remains a searchable catalog
picker with the existing custom-entry behavior, not a plain text input, and keeps an explicit Continue button.

### Screen 3 — Learner Level

The level is **pre-filled from the profile type chosen on Screen 1** via `getDefaultLearnerLevel` — the same
helper the lightweight profile-completion prompt uses, so the two surfaces cannot drift: `BOARD_EXAM` →
`BOARD_EXAM_REVIEW`, `PROFESSIONAL` → `PROFESSIONAL`, `TEACHER` → `PERSONAL_LEARNING`, everything else →
`COLLEGE`. Switching profile type re-defaults, because the previous level was chosen for a different kind of
learner. An existing saved level is never overwritten while the profile type is unchanged.

**This pre-fill is only safe because Screen 3 keeps its Continue button.** A pre-filled `<select>` is exactly
what made this screen a dead end when it auto-advanced: re-choosing the value already selected fires no
`change` event, so the learner had no way forward. Do not pair the pre-fill with auto-advance here.

Screen 3 asks for required `Learner Level` through the existing grouped closed-set selector. Selecting a level
persists the combined learning context and requires an explicit Continue. A persistence failure remains visible and
retryable on this screen. Teacher helper copy frames the field as the default quiz difficulty for quizzes the
teacher generates; non-teacher copy stays focused on the learner's own study material.

Until the separately scoped exam-date removal lands, Board Taker also retains the existing inline optional
`Exam Date` control here; the structural split does not add a ninth screen for it.

**The Screen 2 `Course / Program` must be sent on both Screen 6 note calls** — as `courseProgramText` on `createNote`
and as the `courseProgram` argument to `generateNoteFromTopic`. It is not optional on either. The learner branch
of both `NoteGenerationService.resolveAuthoringContext` and `NoteService.resolveRequestedCourseProgram` throws
`CourseProgramSelectionRequiredException` when the request omits the program and the profile has none, and
onboarding persists the combined learning context only after Screen 3 selection — so the draft value is the source at
Screen 6. Omitting it from either call makes onboarding a dead end: the account receives *"Choose at least one
course or program."* on a screen with no such field, and can never reach the dashboard. The two calls fail
independently — `generateNoteFromTopic` breaks the generate path at the *Generate* button, `createNote` breaks
both paths at *Generate Study Pack* — so fixing one is not enough. Regression coverage:
`app/onboarding/page.test.tsx` asserts both call payloads exactly; `NoteGenerationServiceTest` and
`NoteServiceTest` cover the request-supplies-it / profile-has-none shape on the backend.

**Nobody curates during onboarding.** Both note-authoring entry points treat a user as a **learner** while
`onboardingCompletedAt` is null, regardless of role or profile type — `NoteService.isTeacherSelectableOwner` and
`NoteGenerationService.isCurator` both return false in that window. Onboarding collects personal learning context
and has no catalog picker, so a curator-role account taking the curator branch here would be asked for
`courseProgramIds` that no onboarding screen can supply; before this rule, onboarding was uncompletable for every
ADMIN account. **This grants no less authority than before** — a completed curator account is entirely unchanged,
and scope-guard tests assert that an onboarded ADMIN still authors through the catalog. It mirrors the exemption
`OnboardingGuardService.assertProfileComplete` already makes for mid-onboarding users. Do not "tidy" either
predicate back to a bare role check.

Screen 3 cannot be reached with a blank `Course / Program`: Screen 2's Continue gate controls the only forward
entry, draft hydration fills the field from the profile but never clears it, and the combobox renders only on Screen 2.

#### Practice-first branch

> **`v0.71.0` slice 5 opened this branch to every profile type and put it behind an explicit intent choice.**
> It was `BOARD_EXAM`-only, which made a qualifying Review Set unreachable for `STUDENT` (~27% of
> profile-typed accounts) even when one existed. Eligibility is now
> `draft.intent === "ready_made" && practiceFirstPlan !== null` — availability is resolved for all profile
> types, and **the intent gate matters as much as availability**: a learner who chose "own notes" is never
> shown the adopt screen just because a set happens to exist. Read the Intent Router section below first;
> the paragraphs here describe the adopt screen the branch leads to, not who reaches it.

After a learner selects the required learner level on Screen 3, onboarding checks the
existing published Official Review Sets for that course/program. When the first match has both
`itemCount > 0` and `readyCount > 0`, choosing ready-made materials on Screen 4 opens a `Confirm & Practice` screen.
It confirms the collected course/program (and reuses the optional exam-countdown presentation),
shows the matching official Review Set, and lets the learner adopt it, landing on the adopted Review
Set's detail page (Today's Focus, with Continue Studying one tap away) rather than directly inside a
quiz — a brand-new learner should land somewhere oriented, not cold inside a question. This path has
no note authoring and no AI generation.

The check fails open: no qualifying set, a zero-ready set, or a lookup error continues to the
normal eight-screen create-first path. **The failure mode this protects against is telling a learner that content does not
exist when it does** — so an *unknown* availability result renders no availability line at all, rather than a
negative one.

The header shows `Step 8 of 8` (full progress bar) on this screen, display-only — the underlying
step-machine state stays at 4 so Back and transition logic are unaffected. This screen is the last
one this cohort sees before onboarding completes, so the header should read as the final step
rather than the misleading `Step 4 of 8` a literal step number would otherwise show.

### Screen 4 — First Intent

Users first choose whether to study with ready-made materials or build from their own notes. This is a closed-set
choice and advances from the selection handler. The ready-made branch either opens Confirm & Practice or the
existing honest unavailable state; the own-notes branch advances to Screen 5.

### Screen 5 — Input Method

For the qualifying Board Taker cohort described above, this step does not render: `Confirm &
Practice` replaces the remaining create-first screens, and Screen 8 is not rendered
after adoption. All other learners see the unchanged input-method choices below.

When the ready-made branch has no qualifying Official Study Plan, Screen 5 keeps the existing three exits and
adds the promised third beat: the learner may record interest in an Official Study Plan for the course/program
already stored in the onboarding draft. The action is not another course/program picker and does not render for
an empty or whitespace-only value. It records the trimmed learner text using the shared normalized lookup key,
so case and whitespace variants are one request per learner and program.

Asking confirms in place and does not navigate, complete onboarding, or disable the existing own-notes,
Public Library, or Dashboard choices. A resumed learner who already asked sees the confirmed state instead of
another invitation. A failed request shows an inline error and makes no success claim; every other fallback
route remains usable. This wishlist is a demand signal only: it sends no email or other notification.

Users choose one path:

- `Create a note`
- `Write or paste my own note`

Selection advances immediately to Screen 6. Back from Screen 6 returns here with the answer still selected and
stays here until another selection is made.

### Screen 6 — The Note

`Create a note` path:

- enter a topic
- click `Create a Note`
- NoteLib creates an editable note draft first
- user then clicks `Generate Study Pack →`

`Write or paste my own note` path:

- enter note content directly
- click `Generate Study Pack →`

Important:

- onboarding note generation is single-use and guided
- onboarding does not expose standalone iteration controls like `Create Again`
- note-generation gating still applies here

### Screen 7 — Study Pack Generation

Headline during generation:

- `Building your Study Pack...`

Headline after success:

- `Your Study Pack is ready.`
- `Saved to your library — yours to quiz against anytime.` appears beneath the success message; it complements, rather than replaces, the existing back-navigation notice.

The page previews:

- `Summary`
- `Key Concepts`
- `Quiz Preview`

The note and Study Pack are normal saved library entities, not temporary onboarding-only records.

### Screen 8 — Completion

Headline:

- Uses the learner's onboarding topic when available: `Your {topic} Study Pack is ready. Come back tomorrow to keep building on it.`
- Falls back to the same return-framed message without the topic when the topic is unavailable.

Actions:

- `Open your Study Pack` is the single visually-primary action and keeps the existing fresh-Study-Pack destination.
- `Go to Dashboard` remains functional as a quiet secondary action.

The completion call persists onboarding completion through the existing backend flow and sets `onboardingCompletedAt`.
For the practice-first Board Taker branch, the same call fires from `Start this plan` after adoption
rather than from a rendered Screen 8; a completion failure can retry without re-adopting the plan.

#### Recommended plan adopt card

Below the two actions, the completion step reuses the Dashboard's `DashboardStudyPlanSection` adopt card (`courseProgram` and `profileType` passed from the onboarding draft, plus `context="onboarding"`). It is a supplementary discovery surface — the learner's own freshly-generated Study Pack stays the primary `Open your Study Pack` action. The `context="onboarding"` prop adds a visible "Optional: explore an official {plan} alongside the Study Pack you just created." line reinforcing this; Dashboard and Collections call sites omit the prop and keep their existing copy unchanged.

- The card self-hides when the learner's course/program has no published plan, so most tracks see Screen 8 unchanged.
- For tracks with a published plan, it offers one-tap adopt via the existing `listPublicStudyPlans({ courseProgram })` + `adoptStudyPlan` (no new endpoint).
- Because reaching Screen 8 already persists onboarding completion, tapping `Start this plan` and navigating to the adopted collection does not lose onboarding state.
- The adopted-collection skipped-notice flow works unchanged: the same `sessionStorage` key is read by the collection detail page via `getStudyPlanSkippedNotice`.

## Deferred Personalization

These inputs are **not** collected during onboarding:

- `bio`
- `engagementMode`
- reminder preferences

They are adjusted later through:

- `/profile` -> `Learning Profile`
- `/settings` -> `Preferences`

The Dashboard learner-level follow-up prompt remains a refinement path after onboarding:

- title: `Too easy or too hard?`
- body: `You can adjust your learner level anytime — quizzes will match your new study stage next time you practice.`
- CTA: `Adjust level`
- destination: `/profile?from=dashboard#learning-profile`

The prompt is dismissible and the dismissal is stored per user in frontend storage.

## Persistence

On onboarding completion, backend currently persists:

- `profileType`
- optional `examDate` for `BOARD_EXAM`
- `onboardingCompletedAt`

The following are **not** persisted by onboarding completion itself:

- `bio`
- `engagementMode`
- reminder preferences

**FIXED 2026-08-07 — the original claim is now true, having been false for the whole of this release's history.**
`learnerLevel` and `courseProgram` are persisted from **Screen 3's learner-level selection**, awaited, through the narrow
`PUT /users/profile/learning-context`, and a failure **blocks the step and is shown to the user** while the values
are still on screen and retryable. `profileType` is persisted at **Screen 1**, deliberately fire-and-forget —
`completeOnboarding` re-sends it, so nothing is at risk there; the learning-context values had no second writer,
which is exactly why losing them was permanent. `examDate` is persisted with Screen 3 for `BOARD_EXAM`, and
`completeOnboarding` no longer nulls it. **Do not reinstate a learning-context write at Screen 8** — a second
writer would re-open the hole and could overwrite a value the user has since edited.

The description of the defect is retained below, because it explains why the flow is shaped this way.

**~~CORRECTED 2026-08-06 — this section previously claimed `learnerLevel` and `courseProgram` "are saved before the
user advances through onboarding." That was false.~~** Both are written only at Step 5, *after* `completeOnboarding`
resolves, and the call is **fire-and-forget with a swallowed error** (`onboarding/page.tsx:610-613`, and
identically at `:774-777` on the practice-first path). If it fails — or the user closes the tab in the ~1s window
— `learnerLevel` and `courseProgram` are permanently lost with no user-visible error, while
`onboardingCompletedAt` is already set, so the user is never routed back to supply them again.

**The three defects this section used to record as pending were fixed by `v0.71.0` slice 5, stage 2.** What follows is current behavior.

- **`profileType` persists at Screen 1**, via `POST /auth/onboarding/profile-type`, not at Screen 8.
- **Learning context (`learnerLevel` + `courseProgram`) persists from Screen 3**, through the narrow
  `PUT /users/profile/learning-context`. This replaced a fire-and-forget write that could silently lose a
  user's learner level and program. `CompleteOnboardingRequest` still carries only `profileType` and
  `examDate` — correct now, because the program no longer rides on completion at all.
- **`POST /auth/onboarding` no longer nulls `examDate` for non-`BOARD_EXAM` profile types.** It previously
  wrote `resolveExamDate(request)` unconditionally, so completing onboarding as anything but an exam taker
  destroyed a date the user had already given — and `ROADMAP.md`'s target-habit definition segments retention
  on exactly that field, explicitly *not* on `profileType`. Now covered by tests.

**`OnboardingGuardService`'s mid-onboarding exemption still stands and is now load-bearing for a second
reason** — the curator predicates (`NoteService.isTeacherSelectableOwner`, `NoteGenerationService.isCurator`)
key on `onboardingCompletedAt` too. Do not narrow it. See Server-Side Boundary below.

The practice-first branch uses the same completion persistence from its `Start this plan` action,
then routes to the adopted Review Set's detail page; it intentionally does not render Screen 8 for
that cohort.

### Draft schema and stale resumes

The per-user localStorage draft carries `schemaVersion: 1`. New drafts always write the current version.

- A missing or older version means the stored `currentStep` belongs to an older screen layout and is ignored.
- Compatible answers are merged onto a fresh draft and preserved, including typed course/program and note text.
- Resume starts at the earliest unanswered required screen; later answers never allow an earlier required answer
  such as `profileType` to be skipped.
- When every create-first answer is present, a migrated draft resumes at Screen 6, or Screen 7 when it already has
  a saved `noteId`.
- A current-schema `currentStep` is clamped to `1…8`, and is also moved back when it is beyond an earlier missing
  required answer.
- localStorage save failures are non-blocking; React state remains authoritative for the active session, so an
  auto-advancing choice cannot strand the learner because persistence is unavailable.

## Generation Safety

### Idempotency

Onboarding Study Pack creation must not duplicate notes or Study Packs for the same in-progress onboarding flow.

Current guard:

- `handleStartStudyPack()` checks `draft.noteId`
- if a note already exists, onboarding returns to Screen 7 instead of creating a new note

This protects against:

- repeated clicks
- refresh
- back/forward navigation

### Back-button lock during active generation

While the Study Pack is actively generating:

- the footer `Back` button is removed
- the primary action becomes a disabled status button
- the notice reads: `Your Study Pack is being created. This step can't be undone.`

When generation finishes or fails, normal recovery actions return.

### Retry behavior

If generation fails after the note was created:

- onboarding keeps the saved note
- Screen 7 shows the friendly failure state
- `Retry` reuses the saved note instead of creating a new one

## Metadata Auto-Apply

Onboarding-generated Study Packs reuse the normal backend Study Pack generation flow.

Current metadata behavior:

- onboarding explicitly opts into backend auto-apply when it starts Study Pack generation from a saved note
- if the source note has no `subject`, backend applies the generated `subject`
- if the source note has no `tags`, backend applies the generated `tags`

This happens automatically with no extra onboarding prompt.

This is the guided-flow exception. Normal note generation keeps AI metadata suggestions transient until the user applies them.

## Server-Side Boundary

Profile type is required before these authenticated mutations can create or generate content:

- note creation
- note-from-topic generation
- note-owned Study Pack generation
- note copy (public copy and owner self-copy)
- bulk generation
- batch import

The backend throws `ProfileSetupRequiredException` with HTTP `403`, code `ONBOARDING_REQUIRED`, and action `COMPLETE_PROFILE_TYPE`.

The guard fires only for the legacy **completed-but-null** cohort: `profileType == null` **and** `onboardingCompletedAt != null`. Users still mid-onboarding (`onboardingCompletedAt == null`) are exempt; copy-on-signup is likewise exempt because it runs before onboarding completes. **The original reason for the mid-onboarding exemption expired in `v0.71.0`** — `profileType` now persists at Step 1, not at the final step — but the exemption is *more* load-bearing than before, for a new reason: the curator predicates key on `onboardingCompletedAt` as well, so narrowing this guard would re-open the ADMIN-uncompletable-onboarding defect from the other direction. Gating on `profileType == null` alone would 403 every new user's first generation and silently lose copy-on-signup intent — do not narrow the condition back to that.

Do not gate recovery paths:

- `GET /auth/me`
- `POST /auth/onboarding`
- `POST /auth/onboarding/profile-type`
- email verification
- logout/auth/session endpoints
- product-onboarding
- read-only endpoints

## Analytics

Twenty `ONBOARDING_V2_*` events, all declared in the `AnalyticsEventType` Java enum before being fired (per `CLAUDE.md`). The emitted set and the enum currently match exactly; verify that when adding one.

`ONBOARDING_V2_STEP_VIEWED` uses the post-split screen names `profile`, `course-program`, `learner-level`,
`first-intent`, `input-method`, `note`, `generating`, and `completion`; the alternate ready-made terminal screen
keeps `confirm-practice`. The retired names `learning-context` and `input` described wider pre-split screens and
must not be reused. Pre-split and post-split step events are not period-over-period comparable by name or number.

Slice 5 added four: `ONBOARDING_V2_INTENT_SELECTED`, `ONBOARDING_V2_INTENT_UNSUPPORTED_VIEWED`, `ONBOARDING_V2_PRACTICE_FIRST_ELIGIBLE`, and `ONBOARDING_V2_PRACTICE_FIRST_PLAN_ADOPTED`. The two practice-first events dedupe per collection id through a ref, so a re-render or a StrictMode double-mount cannot double-count them.

**`ONBOARDING_V2_COMPLETED` has three emit sites with inconsistent payloads — do not assume the router fields are always present.** All three carry `profile_type`, `learner_level`, `course_program`, and `time_elapsed_seconds`. Only the **fallback exit** (`completeOnboardingAndLeave`) also carries `intent` and `destination`; the two *success* paths — Screen 8 create completion and practice-first adopt — carry neither, and the adopt path additionally sends `method: null`.

The practical consequence: **"did the user reach the first experience they selected?" is not answerable from a single event on the paths that matter most.** `intent` remains inferable from `method` there; `destination` does not. Completing the field on all three exits is a recorded `v0.71.1` candidate — it was deliberately not widened at signoff rather than quietly patched.

**Completion analytics fire in a `finally` block on every path**, including a failed `completeOnboarding`. Anything keying on the event (the Diagnostic Read does) is therefore unaffected by the deferred-completion gap described under Persistence, which leaves `onboardingCompletedAt` null.

## Product-Onboarding Relationship

NoteLib also keeps a separate lightweight product-onboarding tracker through `productOnboardingCompletedAt`.

That system is used for first-study guidance outside `/onboarding`, such as:

- first-study welcome guidance on Dashboard
- completion tracking after the user finishes the early learning flow

It is separate from `/onboarding` and must not reuse `onboardingCompletedAt`.
