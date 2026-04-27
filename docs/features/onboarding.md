# onboarding.md - NoteLib Feature Context

## Goal

NoteLib onboarding has two phases:

- **Preferences onboarding** — collected once on first verified entry. Captures profile type, study goal, and the user's first note or topic. Ends with a generated Study Pack so users leave onboarding with real content.
- **First-study product walkthrough** — a lightweight guide for users who have not created a Study Pack yet. Separate from preferences onboarding.

The goal of v0.11.0 onboarding is to get users to their first Study Pack before they touch the dashboard, eliminating the empty-state problem at activation.

## Activation Rule

Onboarding is active for all verified users.

Required behavior:

- show onboarding once after email verification and first verified entry into the app
- do not show onboarding to anonymous users
- do not repeat onboarding after `onboardingCompletedAt` is set
- do not block public pages such as landing, pricing, or Public Library
- users who have already completed onboarding must be redirected to `/dashboard`

## Onboarding Flow (v0.11.0)

Route: `/onboarding`

Five steps. Steps 1–3 collect input; Steps 4–5 deliver the first win.

### Step 1 — Profile Type

Headline: `Welcome to NoteLib. Let's set things up.`

Options (required, card-select):

- `Student` — Reviewing notes and preparing for quizzes
- `Board Taker` — Preparing for a board or licensure exam
- `Teacher` — Creating study materials for students

### Step 2 — Study Goal

Headline: `What's your goal right now?`

Options are persona-filtered by the Step 1 selection. One option required.

Student options:
- Understand a topic in depth
- Practice and test myself with quizzes
- Review notes I already have

Board Taker options:
- Understand a topic before exam day
- Practice under exam-style conditions
- Review and reinforce weak concepts

Teacher options:
- Create study material for students
- Generate a quiz or exam
- Understand a topic to teach it

Board Taker only: optional inline `Exam Date` date picker appears below goal options. If set, stored as `examDate`. Label: `When is your exam? (optional)`.

### Step 3 — Input Method

Headline: `How do you want to start?`

Two paths (card-select, one required):

**Generate a note** — user enters a topic string (minimum 3 characters). NoteLib generates a note draft and immediately proceeds to Study Pack generation.

**Write or paste my own note** — user enters note content (minimum 50 characters). Proceeds to Study Pack generation.

CTA label for both paths: `Generate Study Pack →`

### Step 4 — Study Pack Generation

Headline during generation: `Building your Study Pack...`

Headline after generation: `Your Study Pack is ready.`

Three sections shown after generation:

- **Summary** — 2–3 sentence preview
- **Key Concepts** — 3–4 concept chips; show `+N more` if count exceeds 4
- **Quiz Preview** — one question shown without the answer to maintain anticipation

On mobile, Key Concepts and Quiz Preview sections collapse by default; Summary is open. All sections are visible on desktop.

### Step 5 — Completion

Headline: `You just started your study loop.`

Subheadline displays the learning loop with the user's current position highlighted:

`Create ✓ → Understand ● → Practice → Challenge → Improve`

Body copy is profile-aware:

- Student: `Your Study Pack is ready. Start practicing when you're ready.`
- Board Taker: `Your first practice material is ready. Keep going and build toward exam day.`
- Teacher: `Your Study Pack is ready. You can export a quiz for your students any time.`

Actions:

- Primary: `Continue Studying` → navigates to the generated Study Pack
- Secondary: `Go to Dashboard` → navigates to `/dashboard`

## Deferred Fields

These fields remain in the system but are not collected during onboarding. They are surfaced in profile settings and preferences after the user's first session.

| Field | Deferred to |
|---|---|
| `learnerLevel` | Profile → Learning Profile |
| `courseProgram` | Profile → Learning Profile; nudged on dashboard empty state |
| `engagementMode` | Settings → Preferences; surfaced after first study session |
| `inactivityRemindersEnabled` | Settings → Preferences; prompted on Day 3 |
| `weakConceptRemindersEnabled` | Settings → Preferences; prompted after first quiz |

## Persistence

Backend stores the following on onboarding completion:

- `profileType`
- `onboardingCompletedAt`
- `examDate` (Board Taker only, optional)

Deferred fields are persisted when the user edits them later via Profile or Settings.

The generated note and Study Pack created during onboarding are saved to the user's library as a normal note. The note is editable after onboarding completes.

## Edge Cases

**Empty topic field (Step 3)**
CTA is disabled. No validation error shown until the user attempts to continue.

**Own note too short (Step 3)**
Character count shown (`32 / 50 minimum`). CTA is disabled until minimum is reached. No red error until continue is tapped.

**Study Pack generation fails (Step 4)**
Show inline error: `Something went wrong generating your Study Pack. Try a shorter topic or check your connection.` with a `Retry` button. Back button remains enabled.

**Study Pack generates with no concepts (Step 4)**
Show `No key concepts found` in the concepts section. Do not hide the section. Summary and Quiz Preview still render.

**Study Pack generates with no quiz questions (Step 4)**
Replace quiz section with: `Quiz questions will be available when you open your Study Pack.`

**Onboarding save fails (Step 5)**
Show inline error below CTAs: `We couldn't save your profile. Your Study Pack is still available.` Allow navigation forward anyway. Flag the incomplete profile for a prompt on the dashboard.

**Back from Step 4**
Show soft inline notice: `Going back will start a new Study Pack. Your current one will be saved.` No blocking confirmation dialog.

## Analytics Events

All events use the `onboarding_v2` namespace.

| Event | Properties |
|---|---|
| `onboarding_v2.started` | `source: "signup" \| "resuming"` |
| `onboarding_v2.step_viewed` | `step: 1–5`, `step_name` |
| `onboarding_v2.profile_selected` | `profile_type` |
| `onboarding_v2.goal_selected` | `goal`, `profile_type` |
| `onboarding_v2.exam_date_set` | `days_until_exam` (computed, not raw date) |
| `onboarding_v2.input_method_selected` | `method: "generate" \| "own_note"`, `profile_type` |
| `onboarding_v2.topic_submitted` | `topic_length` (no raw text) |
| `onboarding_v2.own_note_submitted` | `note_length` |
| `onboarding_v2.study_pack_generated` | `method`, `summary_length`, `concepts_count`, `quiz_questions_count` |
| `onboarding_v2.study_pack_error` | `method`, `error_type` |
| `onboarding_v2.completed` | `profile_type`, `goal`, `method`, `time_elapsed_seconds` |
| `onboarding_v2.cta_continue_studying` | — |
| `onboarding_v2.cta_go_to_dashboard` | — |
| `onboarding_v2.back_navigated` | `from_step` |
| `onboarding_v2.abandoned` | `last_step` |

Do not log raw topic text, note content, or user bio.

## Later Editing

- Profile Type: editable in Profile
- Learner Level, Course / Program, Bio: editable in Profile → Learning Profile
- Engagement Mode: editable in Settings → Preferences
- Study Reminders: editable in Settings → Preferences

## First-Study Product Walkthrough

This is a separate, lightweight guide for users who have a verified account but have not generated their first Study Pack outside of onboarding.

Required behavior:

- show only when `studyPackCount == 0` and `productOnboardingCompletedAt` is not set
- do not show after `productOnboardingCompletedAt` is set
- let the user skip at any step
- persist only the completion/dismissal flag in the backend

Steps:

1. Welcome modal on Dashboard
2. Create-note hint on New Note
3. Generate Study Pack modal on Note Detail
4. Quick Review modal on Note Detail
5. Completion modal after first Quick Review
