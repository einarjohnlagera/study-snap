# Settings Preferences

`Settings` should surface `Preferences` before `Plan & Billing` and `Account`.

## Preferences

Preferences currently include:

- `Theme`
  - `Light`
  - `Dark`
  - `System` (default)
- `Learning Style`
  - `Focused` -> Use NoteLib when you need it. No streaks or pressure.
  - `Consistency` -> Light encouragement to study regularly.
  - `Streak` -> Track consecutive study days.
- `Study Reminders`
  - `Inactivity reminders`
  - `Weak concept reminders`

`Learning Style` stays editable in `Settings > Preferences` even after onboarding.

## Persistence

Preference values are stored on the user record and returned by `GET /auth/me`:

- `themePreference`
- `engagementMode`
- `inactivityRemindersEnabled`
- `weakConceptRemindersEnabled`

Settings saves them through:

- `POST /auth/preferences/theme`
- `POST /auth/preferences/engagement-mode`
- `POST /auth/preferences/study-reminders`

`themePreference` also persists locally so NoteLib can apply the correct theme before the UI renders on the next load.

## Future Reminder Logic

This task stores preference values only. Reminder scheduling comes later.

Intended cadence mapping:

- `Focused` -> least reminders / low pressure
- `Consistency` -> moderate cadence
- `Streak` -> more frequent cadence
