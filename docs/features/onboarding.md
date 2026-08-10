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

## Current Flow

Route: `/onboarding`

Five steps:

1. `Profile Type`
2. `Learning Context` (Learner Level + Course / Program, and an optional Exam Date for Exam Reviewers)
3. `Input Method`
4. `Study Pack Generation`
5. `Completion`

> **Being redesigned — `v0.71.0` slice 5.** This five-step flow is scheduled to be replaced by an intent-routing
> flow in which Step 3 asks *what the learner wants to do first* rather than *how they want to input a note*.
> Plan: `docs/claude-plans/onboarding-activation-and-intent-router.md`. Governing philosophy, owner-ratified
> 2026-08-06: **onboarding is no longer profile collection — it is an intelligent router that helps every learner
> reach the fastest successful study experience available to them.** The sections below describe current shipped
> behavior and remain authoritative until that ships. Note that `docs/features/dashboard.md:235` still carries an
> anti-drift lock on this flow (and names the nonexistent "Study Goal" step); that lock is consciously superseded
> by the slice 5 ruling and must be updated when the redesign lands.

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

This does not alter the five-step `/onboarding` flow, its order, or the legacy profile-type-only re-prompt for any other cohort. If marker storage is unavailable, the app fails open to the existing `/onboarding` redirect behavior.

### Step 2 — Learning Context

**CORRECTED 2026-08-06 — this step was previously documented as "Study Goal", with the claim that "goal options
are filtered by the selected profile type." No study-goal selector exists anywhere in the onboarding flow.**
`studyGoal` is a separate profile field written by `PUT /users/profile/goal`, which onboarding never calls. The
step numbering list above carried the same stale name and is corrected with it.

Step 2 collects and requires:

- `Learner Level`
- `Course / Program`

Teacher Learner Level helper copy frames the field as the default quiz difficulty for quizzes the teacher generates. Non-teacher helper copy stays focused on the learner's own study material.

Board Taker also gets an inline optional `Exam Date` field on this step.

**The Step 2 `Course / Program` must be sent on both Step 3 note calls** — as `courseProgramText` on `createNote`
and as the `courseProgram` argument to `generateNoteFromTopic`. It is not optional on either. The learner branch
of both `NoteGenerationService.resolveAuthoringContext` and `NoteService.resolveRequestedCourseProgram` throws
`CourseProgramSelectionRequiredException` when the request omits the program and the profile has none, and
onboarding does not persist the profile value until Step 5 — so the collected value is the *only* source at
Step 3. Omitting it from either call makes onboarding a dead end: the account receives *"Choose at least one
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

Step 3 cannot be reached with a blank `Course / Program`: `canContinueFromStepTwo` gates the only entry into it,
draft hydration fills the field from the profile but never clears it, and the input renders only on Step 2.

#### Practice-first branch

> **`v0.71.0` slice 5 opened this branch to every profile type and put it behind an explicit intent choice.**
> It was `BOARD_EXAM`-only, which made a qualifying Review Set unreachable for `STUDENT` (~27% of
> profile-typed accounts) even when one existed. Eligibility is now
> `draft.intent === "ready_made" && practiceFirstPlan !== null` — availability is resolved for all profile
> types, and **the intent gate matters as much as availability**: a learner who chose "own notes" is never
> shown the adopt screen just because a set happens to exist. Read the Intent Router section below first;
> the paragraphs here describe the adopt screen the branch leads to, not who reaches it.

After a learner submits the required learner level and course/program, onboarding checks the
existing published Official Review Sets for that course/program. When the first match has both
`itemCount > 0` and `readyCount > 0`, Steps 3–4 are replaced with a `Confirm & Practice` screen.
It confirms the collected course/program (and reuses the optional exam-countdown presentation),
shows the matching official Review Set, and lets the learner adopt it, landing on the adopted Review
Set's detail page (Today's Focus, with Continue Studying one tap away) rather than directly inside a
quiz — a brand-new learner should land somewhere oriented, not cold inside a question. This path has
no note authoring and no AI generation.

The check fails open: no qualifying set, a zero-ready set, or a lookup error continues to the
normal five-step path. **The failure mode this protects against is telling a learner that content does not
exist when it does** — so an *unknown* availability result renders no availability line at all, rather than a
negative one.

The header shows `Step 5 of 5` (full progress bar) on this screen, display-only — the underlying
step-machine state stays at 3 so Back and transition logic are unaffected. This screen is the last
one this cohort sees before onboarding completes, so the header should read as the final step
rather than the misleading `Step 3 of 5` a literal step number would otherwise show.

### Step 3 — Input Method

For the qualifying Board Taker cohort described above, this step does not render: `Confirm &
Practice` replaces both Input Method and Study Pack Generation, and Step 5 is skipped entirely
after adoption. All other learners see the unchanged input-method choices below.

Users choose one path:

- `Create a note`
- `Write or paste my own note`

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

### Step 4 — Study Pack Generation

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

### Step 5 — Completion

Headline:

- Uses the learner's onboarding topic when available: `Your {topic} Study Pack is ready. Come back tomorrow to keep building on it.`
- Falls back to the same return-framed message without the topic when the topic is unavailable.

Actions:

- `Open your Study Pack` is the single visually-primary action and keeps the existing fresh-Study-Pack destination.
- `Go to Dashboard` remains functional as a quiet secondary action.

The completion call persists onboarding completion through the existing backend flow and sets `onboardingCompletedAt`.
For the practice-first Board Taker branch, the same call fires from `Start this plan` after adoption
rather than from a rendered Step 5; a completion failure can retry without re-adopting the plan.

#### Recommended plan adopt card

Below the two actions, the completion step reuses the Dashboard's `DashboardStudyPlanSection` adopt card (`courseProgram` and `profileType` passed from the onboarding draft, plus `context="onboarding"`). It is a supplementary discovery surface — the learner's own freshly-generated Study Pack stays the primary `Open your Study Pack` action. The `context="onboarding"` prop adds a visible "Optional: explore an official {plan} alongside the Study Pack you just created." line reinforcing this; Dashboard and Collections call sites omit the prop and keep their existing copy unchanged.

- The card self-hides when the learner's course/program has no published plan, so most tracks see Step 5 unchanged.
- For tracks with a published plan, it offers one-tap adopt via the existing `listPublicStudyPlans({ courseProgram })` + `adoptStudyPlan` (no new endpoint).
- Because reaching Step 5 already persists onboarding completion, tapping `Start this plan` and navigating to the adopted collection does not lose onboarding state.
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
`learnerLevel` and `courseProgram` are persisted at **Step 2 submit**, awaited, through the narrow
`PUT /users/profile/learning-context`, and a failure **blocks the step and is shown to the user** while the values
are still on screen and retryable. `profileType` is persisted at **Step 1**, deliberately fire-and-forget —
`completeOnboarding` re-sends it, so nothing is at risk there; the Step 2 values had no second writer, which is
exactly why losing them was permanent. `examDate` is persisted at Step 2 for `BOARD_EXAM`, and
`completeOnboarding` no longer nulls it. **Do not reinstate a learning-context write at Step 5** — a second
writer would re-open the hole and could overwrite a value the user has since edited.

The description of the defect is retained below, because it explains why the flow is shaped this way.

**~~CORRECTED 2026-08-06 — this section previously claimed `learnerLevel` and `courseProgram` "are saved before the
user advances through onboarding." That was false.~~** Both are written only at Step 5, *after* `completeOnboarding`
resolves, and the call is **fire-and-forget with a swallowed error** (`onboarding/page.tsx:610-613`, and
identically at `:774-777` on the practice-first path). If it fails — or the user closes the tab in the ~1s window
— `learnerLevel` and `courseProgram` are permanently lost with no user-visible error, while
`onboardingCompletedAt` is already set, so the user is never routed back to supply them again.

**The three defects this section used to record as pending were fixed by `v0.71.0` slice 5, stage 2.** What follows is current behavior.

- **`profileType` persists at Step 1**, via `POST /auth/onboarding/profile-type`, not at Step 5.
- **Learning context (`learnerLevel` + `courseProgram`) persists at Step 2**, through the narrow
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
then routes to the adopted Review Set's detail page; it intentionally does not render Step 5 for
that cohort.

## Generation Safety

### Idempotency

Onboarding Study Pack creation must not duplicate notes or Study Packs for the same in-progress onboarding flow.

Current guard:

- `handleStartStudyPack()` checks `draft.noteId`
- if a note already exists, onboarding returns to Step 4 instead of creating a new note

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
- Step 4 shows the friendly failure state
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

Slice 5 added four: `ONBOARDING_V2_INTENT_SELECTED`, `ONBOARDING_V2_INTENT_UNSUPPORTED_VIEWED`, `ONBOARDING_V2_PRACTICE_FIRST_ELIGIBLE`, and `ONBOARDING_V2_PRACTICE_FIRST_PLAN_ADOPTED`. The two practice-first events dedupe per collection id through a ref, so a re-render or a StrictMode double-mount cannot double-count them.

**`ONBOARDING_V2_COMPLETED` has three emit sites with inconsistent payloads — do not assume the router fields are always present.** All three carry `profile_type`, `learner_level`, `course_program`, and `time_elapsed_seconds`. Only the **fallback exit** (`completeOnboardingAndLeave`) also carries `intent` and `destination`; the two *success* paths — Step 5 create completion and practice-first adopt — carry neither, and the adopt path additionally sends `method: null`.

The practical consequence: **"did the user reach the first experience they selected?" is not answerable from a single event on the paths that matter most.** `intent` remains inferable from `method` there; `destination` does not. Completing the field on all three exits is a recorded `v0.71.1` candidate — it was deliberately not widened at signoff rather than quietly patched.

**Completion analytics fire in a `finally` block on every path**, including a failed `completeOnboarding`. Anything keying on the event (the Diagnostic Read does) is therefore unaffected by the deferred-completion gap described under Persistence, which leaves `onboardingCompletedAt` null.

## Product-Onboarding Relationship

NoteLib also keeps a separate lightweight product-onboarding tracker through `productOnboardingCompletedAt`.

That system is used for first-study guidance outside `/onboarding`, such as:

- first-study welcome guidance on Dashboard
- completion tracking after the user finishes the early learning flow

It is separate from `/onboarding` and must not reuse `onboardingCompletedAt`.
