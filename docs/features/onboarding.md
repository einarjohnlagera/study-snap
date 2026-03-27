# onboarding.md - NoteLib Feature Context

## Goal

NoteLib uses two short onboarding flows:

- preferences onboarding for newly verified users
- first-study product onboarding for users who have not created a Study Pack yet

The goal is to personalize the app quickly, then guide brand-new users through their first note-to-review workflow.

## Activation Rule

Onboarding is active for all verified users.

Required behavior:

- show onboarding once after email verification and first verified entry into the app
- do not show onboarding to anonymous users
- do not repeat onboarding after completion
- do not block public pages such as landing, pricing, or Public Library

## Collected Fields

Onboarding collects:

- `profileType`
- `learningStyle` (stored as `engagementMode`)
- reminder preferences
- `examDate` for board exam users

Profile Type options:

- `Student`
- `Board Exam`
- `Teacher`

Learning Style options:

- `Focused` -> Use NoteLib when you need it. No streaks or pressure.
- `Consistency` -> Light encouragement to study regularly.
- `Streak` -> Track consecutive study days.

Reminder step:

- `No reminders for now`
- `Light reminders`
- `Stay on track`

Board exam users also select an `Exam Date` before finishing onboarding.

## Persistence

Backend should store:

- `profileType`
- `examDate`
- `engagementMode`
- `inactivityRemindersEnabled`
- `weakConceptRemindersEnabled`
- `onboardingCompletedAt`

On submit:

- save both selected values
- mark onboarding as completed
- redirect the user to `Dashboard`

## Later Editing

- Profile Type can be edited later in `Profile`
- Learning Style can be edited later in `Settings > Preferences`

Onboarding is only the first-time setup surface.

## First-Study Product Onboarding

This separate walkthrough teaches the first core workflow:

`Create Note -> Generate Study Pack -> Quick Review -> Dashboard`

Required behavior:

- show the welcome modal only when `studyPackCount == 0`
- do not show the walkthrough after `productOnboardingCompletedAt` is set
- let the user skip the guide at any step
- persist only the final completion/dismissal flag in backend
- keep the in-progress UI step lightweight on the frontend

First-study steps:

- welcome modal on `Dashboard`
- create-note hint on `New Note`
- generate-study-pack modal on `Note Detail`
- quick-review modal on `Note Detail`
- completion modal after first Quick Review
