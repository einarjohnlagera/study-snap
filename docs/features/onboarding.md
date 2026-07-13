# onboarding.md - NoteLib Feature Context

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
2. `Study Goal`
3. `Input Method`
4. `Study Pack Generation`
5. `Completion`

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

### Step 2 — Study Goal

Goal options are filtered by the selected profile type.

Step 2 also requires:

- `Learner Level`
- `Course / Program`

Teacher Learner Level helper copy frames the field as the default quiz difficulty for quizzes the teacher generates. Non-teacher helper copy stays focused on the learner's own study material.

Board Taker also gets an inline optional `Exam Date` field on this step.

### Step 3 — Input Method

Users choose one path:

- `Generate a note`
- `Write or paste my own note`

`Generate a note` path:

- enter a topic
- click `Generate Note`
- NoteLib creates an editable note draft first
- user then clicks `Generate Study Pack →`

`Write or paste my own note` path:

- enter note content directly
- click `Generate Study Pack →`

Important:

- onboarding note generation is single-use and guided
- onboarding does not expose standalone iteration controls like `Generate Again`
- note-generation gating still applies here

### Step 4 — Study Pack Generation

Headline during generation:

- `Building your Study Pack...`

Headline after success:

- `Your Study Pack is ready.`

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

#### Recommended plan adopt card

Below the two actions, the completion step reuses the Dashboard's `DashboardStudyPlanSection` adopt card (`courseProgram` and `profileType` passed from the onboarding draft). It is a supplementary discovery surface — the learner's own freshly-generated Study Pack stays the primary `Open your Study Pack` action.

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
- body: `Set your learner level so future quizzes match your study stage.`
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

`learnerLevel` and `courseProgram` are saved before the user advances through onboarding via the shared Learning Profile update path.

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
