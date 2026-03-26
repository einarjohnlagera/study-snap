# Retention Emails

NoteLib sends a small set of behavior-based retention emails through Resend to help verified users come back without spamming them.

## Email Types

Current reminders include:

- `INACTIVITY`
  - trigger: no meaningful study activity for `3` days
  - activity includes Study Pack creation, Quick Review, Challenge Quiz, and Adaptive Practice
  - gated by `inactivityRemindersEnabled`
  - cooldown: `3` days
- `WEAK_CONCEPT`
  - trigger: latest completed Challenge Quiz has weak concepts (`< 60%` accuracy metadata) and the user has not practiced those concepts for `3` days
  - gated by `weakConceptRemindersEnabled`
  - cooldown: `5` days
- `WEEKLY_SUMMARY`
  - trigger: weekly summary run every Sunday at `6:00 PM`
  - includes study packs created, quizzes taken, adaptive sessions, and average quiz score for the last `7` days
  - cooldown: `7` days

## Persistence

Sent emails are tracked in `email_log`:

- `id`
- `user_id`
- `email_type`
- `sent_at`

The log prevents same-type reminders from being sent again before cooldown expires.

## Scheduler

`RetentionEmailScheduler` runs:

- daily for inactivity and weak concept reminders
- weekly for the weekly study summary

Default cron:

- `0 45 2 * * *`
- `0 0 18 * * SUN`

Configured under:

- `studysnap.retention.daily-cron`
- `studysnap.retention.weekly-cron`

## Email Delivery

Retention emails use the existing `EmailService` / Resend integration and template rendering system.

## Future Learning Style Mapping

V1 stores reminder preferences and sends fixed-threshold reminders.

Future cadence logic should use `Learning Style`:

- `Focused` -> least reminders
- `Consistency` -> moderate reminders
- `Streak` -> more frequent reminders
