# Retention Emails

NoteLib sends a small set of behavior-based retention emails to help verified users come back without spamming them.

## Email Types

V1 includes:

- `INACTIVITY`
  - trigger: no login for `7` days
  - gated by `inactivityRemindersEnabled`
  - cooldown: `7` days
- `WEAK_CONCEPT`
  - trigger: latest completed Challenge Quiz has weak concepts and the user has not completed Adaptive Practice for those concepts
  - gated by `weakConceptRemindersEnabled`
  - cooldown: `5` days
- `UNFINISHED_NOTE`
  - trigger: note stays `DRAFT` for `2` days without a generated Study Pack
  - gated by `inactivityRemindersEnabled`
  - cooldown: `3` days

## Persistence

Sent emails are tracked in `email_log`:

- `id`
- `user_id`
- `email_type`
- `sent_at`

The log prevents same-type reminders from being sent again before cooldown expires.

## Scheduler

`RetentionEmailScheduler` runs once per day and calls `RetentionService`.

Default cron:

- `0 45 2 * * *`

Configured under:

- `studysnap.retention.daily-cron`

## Email Delivery

Retention emails use the existing `EmailService` / Resend integration and template rendering system.

## Future Learning Style Mapping

V1 stores reminder preferences and sends fixed-threshold reminders.

Future cadence logic should use `Learning Style`:

- `Focused` -> least reminders
- `Consistency` -> moderate reminders
- `Streak` -> more frequent reminders
