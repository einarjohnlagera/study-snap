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
- `DUE_CONCEPTS_DIGEST`
  - trigger: weekly run finds due concepts across the user's owned Study Packs through `ConceptHealthService`
  - includes the total due-concept count and up to three Study Pack titles with the most due concepts. **As of `v0.72.0` the CTA deep-links to `/notes/{noteId}/quick-review?source=due-concepts-digest`** for the owned note with the most due concepts — one tap to the first question — falling back to the dashboard only when no owned note resolves. It previously linked to Dashboard Today Focus, which left a decision in the way.
  - **Dispatches daily and selects committed learners by their chosen review weekdays** (`users.review_days`, matched in `Asia/Manila`). A null or empty value keeps the existing every-day eligibility and seven-day cooldown, never "never send." A learner with chosen days has a one-day cooldown, so they may receive a digest on each selected day when concepts are actually due. **This digest moved off the weekly Sunday job in `v0.72.0`**: on a weekly dispatch only one weekday could ever match, so any learner choosing other days was silently dropped.
  - gated by `dueConceptsDigestRemindersEnabled` (default on for new signups; existing users retain their previously persisted preference)
  - cooldown: `7` days without chosen review days; `1` day with chosen review days
- `RE_ENGAGEMENT_2025`
  - trigger: admin-started re-engagement campaign for inactive verified users
  - gated by `marketingEmailsEnabled` (default off until the user opts in)
  - deduped by `email_log`

## Review Commitment Prompt

The post-session review commitment prompt is the initial collection surface for `users.review_days`.
It may appear after any completed review session when the learner **has the due-concepts digest
preference ON**, has no chosen days, has not answered the prompt, has seen it fewer than three times,
and was not prompted in the previous 14 days. **⚠️ The digest-preference clause is not decoration:
a learner with reminders off receives no digest at all, so choosing days could not change anything
for them — the ask would be inert and the prompt's own copy false. 252 of 396 accounts had the
preference off when this shipped.** Do not drop that clause to "restore reach", and do not make
committing switch the preference on — that is an email-consent change. The
server records impressions separately from answers: an impression increments
`review_commitment_prompt_count` and updates `review_commitment_last_prompted_at`, while
`review_commitment_prompted_at` continues to mean that the learner answered. The learner can save one
or more review weekdays, optionally add an exam date when that field applies, or choose `Not now`;
either saved outcome resolves the prompt.

Every learner with the digest preference enabled already gets a weekly nudge when concepts are due.
Choosing weekdays upgrades that schedule: the learner becomes eligible only on those days and the
cooldown drops to one day, allowing a nudge on each chosen day that has due concepts. A null or empty
selection retains the existing daily eligibility and seven-day cooldown, so nobody loses the digest
by leaving the prompt unanswered. Learners can later edit the same weekdays and the digest preference
under Settings → Email Preferences. **⚠️ THAT SURFACE'S COPY MUST STATE BOTH CADENCES — weekly by default, and one per chosen day once days are picked.** It was left reading *"a weekly reminder"* after `v0.139.0` changed the cadence, and was caught only by that release's pre-signoff pressure test, **on a file that appeared nowhere in the release diff**. When this cadence changes again, sweep by SURFACE, not by diff. See
[`email-preferences.md`](email-preferences.md).

Prompt impressions, commits, declines, and abandonments are analytics events. Abandonment is recorded
on page exit or component unmount, at most once for each rendered impression. Digest landing requires
an authenticated principal before it is persisted, while analytics events that originate on public
surfaces may still be anonymous.

## Persistence

Sent emails are tracked in `email_log`:

- `id`
- `user_id`
- `email_type`
- `sent_at`

The log prevents same-type reminders from being sent again before cooldown expires.

## Scheduler

`RetentionEmailScheduler` runs:

- daily for inactivity, weak concept reminders, and the due-concepts digest
- weekly for the weekly study summary

Default cron:

- `0 45 2 * * *`
- `0 0 18 * * SUN`

Configured under:

- `studysnap.retention.daily-cron`
- `studysnap.retention.weekly-cron`

## Email Delivery

Retention emails use the existing `EmailService` / Resend integration and template rendering system.

Daily inactivity reminders are budgeted against the shared Resend daily pool so they cannot starve transactional mail:

- `studysnap.email.daily-limit` defaults to `100`
- `studysnap.email.transactional-reserve` defaults to `40`
- `studysnap.email.reengagement-enabled` defaults to `true` and can disable inactivity dispatch without touching transactional sends

The inactivity run computes `sentToday` from `email_log.sent_at >= start of the app day`, then sends at most:

`max(0, dailyLimit - transactionalReserve - sentToday)`

Candidates skipped by this budget are not written to `email_log`; they remain eligible for a later daily run if cooldown and activity gating still allow it. Verification, password reset, billing, and other transactional paths send immediately and are never gated by this re-engagement budget.

New signups default `inactivityRemindersEnabled` and `dueConceptsDigestRemindersEnabled` to `true`. `weakConceptRemindersEnabled`, `weeklySummaryRemindersEnabled`, and `marketingEmailsEnabled` remain default-off.

`ResendEmailService` retries on HTTP `429` (rate limit) — honoring the `Retry-After` header, otherwise exponential backoff (max 3 attempts, capped at 5s) — so transactional email isn't dropped during a send burst. IO and non-429 errors are not retried. The real capacity fix is operational (upgrade the Resend tier).

Resend bounce, complaint, and suppression webhook events are accepted at `/webhooks/resend` only after Svix signature verification (`svix-id`, `svix-timestamp`, `svix-signature`) against `studysnap.email.resend-webhook-secret`. Verified events upsert the recipient into `suppressed_email`; all future sends skip suppressed addresses and log `email.suppressed.skip` instead of calling Resend.

Optional retention and marketing emails include:

- a visible footer link to `/unsubscribe?token=...`
- `List-Unsubscribe` with the one-click API endpoint plus the support mailto fallback
- `List-Unsubscribe-Post: List-Unsubscribe=One-Click`

Unsubscribe categories map to the same Email Preferences flags:

- `INACTIVITY` and `UNFINISHED_NOTE` -> `STUDY_REMINDERS` -> `inactivityRemindersEnabled`
- `WEAK_CONCEPT` -> `WEAK_CONCEPT` -> `weakConceptRemindersEnabled`
- `WEEKLY_SUMMARY` -> `WEEKLY_SUMMARY` -> `weeklySummaryRemindersEnabled`
- `DUE_CONCEPTS_DIGEST` -> `DUE_CONCEPTS_DIGEST` -> `dueConceptsDigestRemindersEnabled`
- `RE_ENGAGEMENT_2025` -> `MARKETING` -> `marketingEmailsEnabled`

Transactional emails do not include unsubscribe links or one-click unsubscribe headers.

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
