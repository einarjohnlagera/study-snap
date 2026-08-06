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

#### Practice-first Board Taker branch

After a Board Taker submits the required learner level and course/program, onboarding checks the
existing published Official Review Sets for that course/program. When the first match has both
`itemCount > 0` and `readyCount > 0`, Steps 3–4 are replaced with a `Confirm & Practice` screen.
It confirms the collected course/program (and reuses the optional exam-countdown presentation),
shows the matching official Review Set, and lets the learner adopt it, landing on the adopted Review
Set's detail page (Today's Focus, with Continue Studying one tap away) rather than directly inside a
quiz — a brand-new learner should land somewhere oriented, not cold inside a question. This path has
no note authoring and no AI generation.

The check fails open: no qualifying set, a zero-ready set, or a lookup error continues to the
normal five-step path. `STUDENT`, `TEACHER`, and `PROFESSIONAL` never enter this branch.

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

**CORRECTED 2026-08-06 — this section previously claimed `learnerLevel` and `courseProgram` "are saved before the
user advances through onboarding." That was false.** Both are written only at Step 5, *after* `completeOnboarding`
resolves, and the call is **fire-and-forget with a swallowed error** (`onboarding/page.tsx:610-613`, and
identically at `:774-777` on the practice-first path). If it fails — or the user closes the tab in the ~1s window
— `learnerLevel` and `courseProgram` are permanently lost with no user-visible error, while
`onboardingCompletedAt` is already set, so the user is never routed back to supply them again.

Two further persistence facts this section omitted:

- **`courseProgram` is not part of onboarding completion at all.** `CompleteOnboardingRequest` carries only
  `profileType` and `examDate`. The program rides solely on the fire-and-forget call above.
- **`POST /auth/onboarding` nulls `examDate` for any non-`BOARD_EXAM` profile type**, unconditionally
  (`AuthService.java:383` with `resolveExamDate` at `:615-620`). Switching profile type therefore silently clears
  a previously-set exam date. No test covers this.

`profileType` is likewise persisted **only at Step 5**, not at Step 1 — which is why `OnboardingGuardService`
deliberately exempts users mid-onboarding (see Server-Side Boundary below; do not narrow that guard without
changing this ordering first).

These are recorded as defects, not as intended behavior. The fix is scoped as `v0.71.0` slice 5 —
`docs/claude-plans/onboarding-activation-and-intent-router.md` §4, which moves `profileType` to Step 1 and
learning context to Step 2 behind a new narrow `PUT /users/profile/learning-context`. **This section describes
current behavior and must be rewritten when that ships.**

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

The guard fires only for the legacy **completed-but-null** cohort: `profileType == null` **and** `onboardingCompletedAt != null`. Users still mid-onboarding (`onboardingCompletedAt == null`) are exempt because onboarding persists `profileType` only at its final step while generating content earlier; copy-on-signup is likewise exempt because it runs before onboarding completes. Gating on `profileType == null` alone would 403 every new user's first generation and silently lose copy-on-signup intent — do not narrow the condition back to that.

Do not gate recovery paths:

- `GET /auth/me`
- `POST /auth/onboarding`
- `POST /auth/onboarding/profile-type`
- email verification
- logout/auth/session endpoints
- product-onboarding
- read-only endpoints

## Product-Onboarding Relationship

NoteLib also keeps a separate lightweight product-onboarding tracker through `productOnboardingCompletedAt`.

That system is used for first-study guidance outside `/onboarding`, such as:

- first-study welcome guidance on Dashboard
- completion tracking after the user finishes the early learning flow

It is separate from `/onboarding` and must not reuse `onboardingCompletedAt`.
