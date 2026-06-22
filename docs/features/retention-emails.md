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
  - gated by `weeklySummaryRemindersEnabled` (default off until the user opts in)
  - cooldown: `7` days
- `RE_ENGAGEMENT_2025`
  - trigger: admin-started re-engagement campaign for inactive verified users
  - gated by `marketingEmailsEnabled` (default off until the user opts in)
  - deduped by `email_log`

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

## Subscription Expiry Emails

Subscription expiry emails are transactional billing alerts, not behavior-based retention emails. They use a separate `SubscriptionExpiryEmailService` and `SubscriptionExpiryEmailScheduler`, and are sent regardless of reminder preferences.

Types:

- `SUBSCRIPTION_EXPIRY_7_DAY`
  - trigger: active Plus/Pro subscription ending in the `now + 6 days` to `now + 8 days` window
  - cooldown: `14` days
- `SUBSCRIPTION_EXPIRY_1_DAY`
  - trigger: active Plus/Pro subscription ending between `now` and `now + 36 hours`
  - cooldown: `3` days
- `SUBSCRIPTION_EXPIRED`
  - trigger: expired Plus/Pro subscription with `endAt` between `now - 36 hours` and `now`
  - cooldown: `30` days

Rules:

- Only verified users receive billing expiry emails.
- Free subscriptions are never selected.
- Deduplication uses `email_log` with the email type and cooldown window.
- The CTA links to `/settings?tab=billing` and must not imply automatic renewal or automatic charging.
- The scheduler runs daily at `0 0 3 * * *` by default via `studysnap.billing.expiry-email-cron`, after the subscription expiry lifecycle job.

## Future Learning Style Mapping

V1 stores reminder preferences and sends fixed-threshold reminders.

Future cadence logic should use `Learning Style`:

- `Focused` -> least reminders
- `Consistency` -> moderate reminders
- `Streak` -> more frequent reminders
