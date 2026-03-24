# onboarding.md - NoteLib Feature Context

## Goal

Collect the minimum study preferences needed to personalize NoteLib for newly verified users without adding heavy setup friction.

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

Profile Type options:

- `Student`
- `Teacher`
- `Professional`
- `Parent`

Learning Style options:

- `Focused` -> Use NoteLib when you need it. No streaks or pressure.
- `Consistency` -> Light encouragement to study regularly.
- `Streak` -> Track consecutive study days.

## Persistence

Backend should store:

- `profileType`
- `engagementMode`
- `onboardingCompletedAt`

On submit:

- save both selected values
- mark onboarding as completed
- redirect the user to `Dashboard`

## Later Editing

- Profile Type can be edited later in `Profile`
- Learning Style can be edited later in `Settings > Preferences`

Onboarding is only the first-time setup surface.
