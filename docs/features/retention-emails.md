# Retention Emails

NoteLib sends a small set of behavior-based retention emails through Resend to help verified users come back without spamming them.

## Email Types

Current reminders include:

- `INACTIVITY`
  - trigger: no meaningful study activity for `3` days
  - activity includes Study Pack creation, Quick Review, Challenge Quiz, and Adaptive Practice
  - gated by `inactivityRemindersEnabled`
  - cooldown: `3` days
  - CTA carries `source=inactivity&e=<email_log.id>` for inert per-send click correlation
- `WEAK_CONCEPT`
  - trigger: latest completed Challenge Quiz has weak concepts (`< 60%` accuracy metadata) and the user has not practiced those concepts for `3` days
  - gated by `weakConceptRemindersEnabled`
  - cooldown: `5` days
  - CTA carries `source=weak-concept&e=<email_log.id>` for inert per-send click correlation
- `WEEKLY_SUMMARY`
  - trigger: weekly summary run every Sunday at `6:00 PM`
  - includes study packs created, quizzes taken, adaptive sessions, and average quiz score for the last `7` days
  - gated by `weeklySummaryRemindersEnabled` (default off until the user opts in)
  - cooldown: `7` days
  - CTA carries `source=weekly-summary&e=<email_log.id>` for inert per-send click correlation
- `DUE_CONCEPTS_DIGEST`
  - trigger: weekly run finds due concepts across the user's owned Study Packs through `ConceptHealthService`
  - includes the total due-concept count and up to three Study Pack titles with the most due concepts. **As of `v0.72.0` the CTA deep-links to `/notes/{noteId}/quick-review?source=due-concepts-digest`** for the owned note with the most due concepts — one tap to the first question — falling back to the dashboard only when no owned note resolves. It previously linked to Dashboard Today Focus, which left a decision in the way.
  - **Dispatches daily and selects committed learners by their chosen review weekdays** (`users.review_days`, matched in `Asia/Manila`). A learner with chosen days has a one-day cooldown, so they may receive a digest on each selected day when concepts are actually due. **This digest moved off the weekly Sunday job in `v0.72.0`**: on a weekly dispatch only one weekday could ever match, so any learner choosing other days was silently dropped.
  - **A null or empty `review_days` no longer means "eligible every day."** `v0.148.0`: a null/empty value is assigned one deterministic weekday, `Math.floorMod(userId.hashCode(), 7)`, computed at read time from the existing `id` column (no new column, no migration). Before this fix every such learner was checked every day the job ran and gated only by a flat cooldown, which clustered sends onto whichever weekday they first happened to land on (measured: 107/101/98 sends on the top 3 weekdays vs. 9/8/2 on the bottom 3, over 28 days) — this now spreads them the same way a chosen-review-day learner is spread.
  - gated by `dueConceptsDigestRemindersEnabled` (default on for new signups; existing users retain their previously persisted preference)
  - cooldown: `1` day with chosen review days; `6` days without chosen review days (`v0.148.0`, down from `7` — the flat 7-day cooldown combined with a brand-new hash-assigned day can otherwise produce two sends inside one week during the first cycle after a learner's day changes; this value is a compile-time constant, not a config property, since it has no legitimate reason to vary per deployment)
  - CTA retains `source=due-concepts-digest` and adds `e=<email_log.id>` for inert per-send click correlation
- `KNOWLEDGE_IMPACT_DIGEST`
  - trigger: monthly impact digest for opted-in creators who helped a new learner in the previous `30` days
  - gated by `knowledgeImpactDigestRemindersEnabled` (default off; zero opted-in users at the Stage 1a sizing read)
  - cooldown: `30` days
  - CTA carries `source=knowledge-impact-digest&e=<email_log.id>` for inert per-send click correlation
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
selection no longer means daily eligibility (`v0.148.0`, see above): the learner still gets a weekly
nudge, on a deterministic weekday assigned from their `id`, with a six-day cooldown — nobody loses the
digest by leaving the prompt unanswered, they just aren't checked every day to send it. Learners can
later edit the same weekdays and the digest preference
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
- `clicked_at` (nullable; the first `email.clicked` event PROCESSED for that row, correlated through the CTA's `e` parameter — under concurrent or out-of-order webhook deliveries that is the first processed, not necessarily the earliest)

The log prevents same-type reminders from being sent again before cooldown expires.

`email_open_daily_counts` stores aggregate Resend open events by UTC `event_date` and `open_count`.
Opens deliberately have no `email_log` foreign key: NoteLib does not persist Resend message ids, and
open tracking is directional only. Click webhooks read the original URL from `data.click.link` and the
click time from `data.click.timestamp`; open aggregation uses the webhook's top-level `created_at`.
**The open counter is not retention-specific:** Resend sends `email.opened` for every message on the account,
so verification, password-reset and retention opens all land in the same UTC-day count. Treat it as a
directional whole-account signal only. A click whose timestamp is not ISO-8601 is logged and skipped, never
thrown (Resend retries any 5xx).

## Scheduler

`RetentionEmailScheduler` runs:

- daily for the due-concepts digest, weak concept reminders, and inactivity — in that order (order is
  budget priority, see Email Delivery); a failure in the digest is logged and does not cancel the rest
- weekly for the weekly study summary
- monthly for the knowledge-impact digest

Default cron:

- `0 45 2 * * *` (Asia/Manila)
- `0 0 18 * * SUN` (Asia/Manila)
- `0 0 9 1 * *` (Asia/Manila; pinned in `v0.158.0`; it previously ran on the host zone)

Configured under:

- `studysnap.retention.daily-cron`
- `studysnap.retention.weekly-cron`
- `studysnap.retention.knowledge-impact-digest-monthly-cron`

## Email Delivery

Retention emails use the existing `EmailService` / Resend integration and template rendering system.

One shared budget governs the five dispatched retention types: `INACTIVITY`, `WEAK_CONCEPT`,
`WEEKLY_SUMMARY`, `DUE_CONCEPTS_DIGEST`, and `KNOWLEDGE_IMPACT_DIGEST`. Each dispatch path recomputes
the available budget immediately before bounding its own candidates. The count includes only those
five types; transactional mail and the admin-triggered `RE_ENGAGEMENT_2025` campaign are excluded
entirely. `transactional-reserve` remains a separate safety margin below the nominal daily limit.

**Dispatch order is budget priority.** `INACTIVITY` alone saturates the shared budget (60/day observed
against a 100 limit and 40 reserve), so whatever runs after it is starved. `runDaily` therefore
dispatches `DUE_CONCEPTS_DIGEST` first, then `WEAK_CONCEPT`, then `INACTIVITY` — engaged-learner types
claim budget before the dormant-user nudge takes the remainder. Do not reorder without re-reading this.

**Known limitation: the weekly and monthly types are starved while `INACTIVITY` saturates the cap.**
`WEEKLY_SUMMARY` (Sunday 18:00 Manila) and `KNOWLEDGE_IMPACT_DIGEST` (1st, 09:00 host time, 17:00 Manila)
run after the 02:45 daily dispatch has used the day's budget, so they start with budget 0; skipped candidates
stay eligible but only for the next week or month, where the same thing happens. Immaterial while almost no
one has opted in (1 and 0 learners at `v0.157.0`), but adding opt-ins will not unlock them. Fixing it needs an
explicit cap on `INACTIVITY`'s share (a product decision, tracked in the ROADMAP Backlog Index). Lowering
`transactional-reserve` does not help: `INACTIVITY` has more eligible learners than budget and absorbs any
extra room. The admin `RE_ENGAGEMENT_2025` campaign is outside this budget, so a campaign batch plus the
retention sends can exceed the provider's daily limit.

- `studysnap.email.daily-limit` defaults to `100`
- `studysnap.email.transactional-reserve` defaults to `40`
- `studysnap.email.reengagement-enabled` defaults to `true` and can disable inactivity dispatch without touching transactional sends
- candidates skipped for budget are not written to `email_log`, so cooldown does not prevent a later eligible run

Each retention dispatch computes `sentToday` from matching `email_log` rows at or after the start of
the app day, then sends at most:

`max(0, dailyLimit - transactionalReserve - sentToday)`

Verification, password reset, billing, and other transactional paths send immediately and are never
gated by this retention budget. The `reengagement-enabled` switch remains specific to `INACTIVITY`.

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
