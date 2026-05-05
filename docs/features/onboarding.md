# onboarding.md - NoteLib Feature Context

## Goal

`/onboarding` is a short activation flow for verified users. Its job is to get the user to a real first Study Pack, not to collect every preference up front.

Current onboarding is intentionally low-friction:

- it happens once after first verified entry
- it ends with a generated Study Pack
- it defers learner-profile and preference details to Profile and Settings

## Activation Rule

- verified users who have not completed onboarding are routed to `/onboarding`
- public pages and anonymous flows are never blocked by onboarding
- users with `onboardingCompletedAt` already set should be sent to `/dashboard`

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

This is the only identity-like field collected during onboarding.

### Step 2 — Study Goal

Goal options are filtered by the selected profile type.

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

- `You just started your study loop.`

Actions:

- `Continue Studying`
- `Go to Dashboard`

The completion call persists onboarding completion through the existing backend flow and sets `onboardingCompletedAt`.

## Deferred Personalization

These inputs are **not** collected during onboarding:

- `learnerLevel`
- `courseProgram`
- `bio`
- `engagementMode`
- reminder preferences

They are adjusted later through:

- `/profile` -> `Learning Profile`
- `/settings` -> `Preferences`

Dashboard follow-up prompt:

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

The following are **not** persisted by onboarding itself:

- `learnerLevel`
- `courseProgram`
- `bio`
- `engagementMode`
- reminder preferences

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

## Product-Onboarding Relationship

NoteLib also keeps a separate lightweight product-onboarding tracker through `productOnboardingCompletedAt`.

That system is used for first-study guidance outside `/onboarding`, such as:

- first-study welcome guidance on Dashboard
- completion tracking after the user finishes the early learning flow

It is separate from `/onboarding` and must not reuse `onboardingCompletedAt`.
