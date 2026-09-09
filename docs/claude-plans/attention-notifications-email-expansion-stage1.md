# Attention, Notifications & Email — Stage 1 Audit

**Status:** Audit only. No behaviour changes proposed for immediate implementation.
**Date:** 2026-09-08
**Author:** Claude Code (NoteLib Feature Planner)
**Scope note:** This document is deliberately **separate from `v0.133.0` — Education Family**, whose
scope is closed. Nothing here is in that release.

> **Production reads used in this audit were READ-ONLY `SELECT`s** against `notelib-db-prod`
> (`dpg-d6tvb8fkijhs73fda4m0-a`), per `CLAUDE.md`. No writes were executed and none are proposed
> outside a normal Flyway migration run by the owner.

---

## 1. Executive judgment

**The brief is written for a system with an event-explosion problem. NoteLib has the opposite
problem, and every recommendation below follows from that inversion.**

The four numbers that should drive this decision:

| Fact | Value | Source |
|---|---|---|
| Rows ever delivered into `notifications` in production | **0** | prod read |
| `announcements` rows ever created in production | **0** | prod read |
| Code paths that produce an `ACTION_REQUIRED` notification | **0** | `AnnouncementService` is the *only* caller of `NotificationService.deliver`, and it always passes `ANNOUNCEMENT` |
| Largest fan-out any proposed event would produce today | **396** (announcement to EVERYONE) / **30** (largest Official Review Set adopter count) | prod read |

**So the numeric badge has never rendered in production, and could not have.**
`countActionableUnread` filters `type in actionableTypes()`, which resolves to `{ACTION_REQUIRED}`,
and nothing produces that type. The v0.130.0 / v0.131.0 substrate is real, well-built, carefully
documented — and **entirely unexercised against live data.**

**The actual risk in this work is not write amplification, email volume, or fan-out cost. It is
commissioning an unexercised substrate with its first real producer.** At 396 users, 54 review-set
adopters, 0 note shares, 1 learning connection and 3 note likes, none of the brief's performance
scenarios are load-bearing *yet*. Designing the guards is still correct — they are far cheaper to
build now than to retrofit — but the document should not pretend the denominators justify them.

**⚠️ And the substrate's test suite cannot tell you it works, because of how it is written.** Every
`ACTION_REQUIRED` assertion in `NotificationServiceIntegrationTest` and `NotificationControllerTest`
is produced by calling `notificationService.deliver(...)` directly with a hand-built
`NotificationDelivery` — necessarily, since no production code path constructs one. **This is the
exact fixture pattern `CLAUDE.md` flags twice** ("a fixture hand-built a state no code path can
produce"). The delivery mechanics under test are correct; what the suite establishes about a *real
producer* reaching them is **nothing**. The first producer therefore owes an end-to-end test starting
from the product action, not from `deliver()` — see §6.1 and verification item 8.

**Three findings do rise to the brief's own §40 "stop expansion and harden first" bar**, and all
three are pre-existing, none introduced by this proposal:

1. **The email daily cap is already breached in production.** Configured limit is 100/day with a
   40 reserve; observed peaks are **156–159/day**. The budget gates only `INACTIVITY`.
2. **Unread notification rows are immortal.** Retention deletes only rows with `read_at` or
   `dismissed_at` set. An `EVERYONE` announcement to 396 users where most never open the bell leaves
   396 permanent rows, per announcement, forever.
3. **The dedup key cannot distinguish producers.** `dedupKey = type.name() + ":" + entityId`, and
   `type` is one of two coarse values. Four different `ACTION_REQUIRED` producers would share one key
   space.

**The single highest-value recommendation in this document is that finding 3 is fixed while
`notifications` has zero rows.** It is a migration with no backfill, no reconciliation and no
possible data loss today. That will never be true again.

**Recommended immediate scope: Stage B only** (foundation hardening), then **Stage D's single
lowest-cardinality producer** — the Official Review Set update, at ≤30 recipients. Everything else is
`future` or `not recommended` on denominator grounds, and is marked as such rather than designed.

---

## 2. Current in-app notification implementation inventory

### 2.1 Schema — `V139__notifications.sql`

```sql
CREATE TABLE notifications (
    id UUID PRIMARY KEY,
    recipient_user_id UUID NOT NULL,
    type VARCHAR(64) NOT NULL,
    dedup_key VARCHAR(255) NOT NULL,
    title VARCHAR(255) NOT NULL,
    body VARCHAR(1000),
    cta_label VARCHAR(64),
    cta_path VARCHAR(512),
    announcement_id UUID,
    created_at TIMESTAMPTZ NOT NULL,
    read_at TIMESTAMPTZ,
    dismissed_at TIMESTAMPTZ
);
CREATE UNIQUE INDEX idx_notifications_recipient_dedup ON notifications(recipient_user_id, dedup_key);
CREATE INDEX idx_notifications_recipient_unread ON notifications(recipient_user_id, read_at);
```

Notable: **no foreign keys** (consistent with the rest of the schema), **no `entity_id` column** —
the target identity survives only inside `dedup_key` as a string, and **no category/priority column**.

### 2.2 Types

`NotificationType` (`entity/NotificationType.java`) — exactly two values:

| Value | `actionable` | Badge? | Producers |
|---|---|---|---|
| `ANNOUNCEMENT` | `false` | no | `AnnouncementService.deliverOne` |
| `ACTION_REQUIRED` | `true` | yes | **none** |

`actionableTypes()` derives the badge's filter set from the boolean. That indirection is good design
and is the reason a taxonomy change is cheap — see §13.

### 2.3 Dedup keys

`NotificationService.dedupKey(type, entityId)` → `"ANNOUNCEMENT:<uuid>"`. Uniqueness is enforced by
`idx_notifications_recipient_dedup`, **not** by an application `exists` check — deliberate, and
documented in the service javadoc. `deliver()` catches `DataIntegrityViolationException` and re-reads
the winning row.

**⚠️ `deliver()` is not `@Transactional`, and that is load-bearing.** Under an ambient transaction the
constraint violation would mark the whole transaction rollback-only, taking an entire fan-out down.
`AnnouncementService.publish` is correspondingly non-transactional. **Any new producer that calls
`deliver()` from inside its own transaction breaks this.** This is the most likely way a future slice
introduces a production defect.

### 2.4 Indexes and query paths

| Query | Predicate | Order | Index used |
|---|---|---|---|
| `findVisibleInbox` | `recipient_user_id` + `dismissed_at IS NULL` + `NOT EXISTS(ended/expired announcement)` | `created_at DESC` | ⚠️ **mismatch** — see §9 |
| `countActionableUnread` | `recipient_user_id` + `read_at IS NULL` + `dismissed_at IS NULL` + `type IN (...)` + same subquery | — | `idx_notifications_recipient_unread` (partial fit) |
| `findByRecipientUserIdAndDedupKey` | unique key | — | `idx_notifications_recipient_dedup` ✅ |
| `deleteReadOrDismissedBefore` | `created_at < ?` + `(read_at IS NOT NULL OR dismissed_at IS NOT NULL)` | — | ⚠️ none — sequential scan |

The announcement lifecycle check is a **correlated subquery, not a per-row lookup** — one query
regardless of row count. This is the one place the existing implementation already solved §9's N+1
concern, and it solved it correctly.

### 2.5 Unread count and badge

`GET /notifications/unread-count` → `{ count }`. Frontend renders the badge only when
`actionableUnreadCount > 0` (`notification-inbox.tsx`), so **no literal "0" is ever rendered** —
§6's requirement is already met.

The `dismissed_at IS NULL` leg on the count was a v0.130.0 pressure-test fix: without it a dismissed-
but-unread actionable row incremented a badge pointing at nothing openable. The repository javadoc
records this. **Both queries must change together** — that invariant is documented in-code and should
be restated in any prompt that touches either.

### 2.6 Read / dismiss semantics

- `read_at` = awareness. `dismissed_at` = inbox visibility. **There is deliberately no `resolved`
  column.**
- Opening the panel does **not** mark anything read. Reading is per-row (CTA click or "Mark read").
- Dismissing removes from inbox and changes nothing else.
- Mark-read is optimistic in the client with rollback on failure, and adjusts the badge locally by
  `-1` only when `type !== "ANNOUNCEMENT"`.

**⚠️ That client-side type check is a string literal comparison against `"ANNOUNCEMENT"`.** It is the
frontend's private mirror of `NotificationType.actionable`, and it will silently mis-count the moment
a third type exists. Flagged in §17 and §42.

### 2.7 Inbox / panel / page

`frontend/components/notifications/notification-inbox.tsx` is the entire surface. There is **no
`/notifications` page** — panel only.

- Desktop: absolutely-positioned popover, `max-h-[32rem]`, closes on outside mousedown and Escape.
- Mobile (`max-width: 639px`): `AppModal` sheet variant; the outside-click handler is skipped so it
  does not fight the modal's own backdrop/Escape handling.
- The container `ref` wraps bell **and** panel, so a bell click toggles rather than close-then-reopen.
- Bell renders at `app-shell.tsx:643`, inside a header gated on `{!isExamFocusActive ...}` — hidden
  across all quiz surfaces (v0.131.0).

### 2.8 Polling

`app-shell.tsx`: `NOTIFICATION_UNREAD_POLL_INTERVAL_MS = 60_000`.

- Polls the **count endpoint only**, never the inbox.
- **Stops while the tab is hidden** (`visibilitychange` listener), resumes on visible.
- Failures are swallowed — no toast per failed poll.
- Inbox contents fetch **only on panel open**, and a re-click while open does not refetch.

**This is already the shape §25 asks for.** The one gap versus §25's list is refresh-on-window-focus,
which is a nicety, not a defect.

### 2.9 Deep links

`cta_path` is a free-text same-origin relative path, validated at three chokepoints
(`AnnouncementCtaPathValidator` on announcement create/update, again in `NotificationService.deliver`,
and `toSafeRelativePath` at render). Rule: `^/[A-Za-z0-9\-._~/]*$` plus an optional query string;
scheme, host and protocol-relative `//` rejected.

**There is no typed target model** — no `target_type` / `target_id`. Every deep link is a string
chosen by the producer. That is fine for announcements and becomes a maintenance problem once four
producers each construct paths independently (§7).

### 2.10 Stale-target handling

**None exists, and none is currently needed** — announcements point at product pages, not at
recipient-scoped resources. The inbox does no live target resolution at all, which is why it has no
N+1 problem. Any producer pointing at a revocable resource (a shared note, a connection request)
introduces this concern for the first time (§9).

### 2.11 Current event producers

**Exactly one: `AnnouncementService`.** Enumerated by grepping every reference to
`NotificationService` in `backend/src/main/java` — the only non-test callers are
`NotificationController` (reads) and `AnnouncementService.deliverOne` (the single write).

### 2.12 Admin announcements

Lifecycle `DRAFT → PUBLISHED → ENDED`, `AdminAnnouncementController`.

- **Edits permitted only while `DRAFT`**; an attempt on anything else throws
  `AnnouncementNotEditableException`. Published content is immutable because delivered rows carry a
  copy.
- **Publish fans out synchronously inside the admin HTTP request** — see §23, this is the single
  largest existing performance exposure.
- **Re-publish is a retry *and* a top-up.** The audience is re-resolved at call time, so a user who
  joined since the first publish receives it; existing recipients are protected by the unique index.
  The javadoc records that this was corrected from an incorrect "pure retry" claim.
- `published_at` is stamped once, never re-stamped.
- **Ending takes effect immediately** because the inbox decides on read; delivered rows are not
  deleted, they stop presenting.
- `expired` / `editable` are derived on read, never stored.

### 2.13 Announcement audience rules

`AnnouncementAudienceResolver` — three audiences, no query builder:

| Audience | Resolution | Prod size |
|---|---|---|
| `EVERYONE` | `userRepository.findAllUserIds()` | **396** |
| `PROFILE_TYPE` | active users by profile type | ≤396 |
| `PLAN_TYPE` | mirrors `SubscriptionService.resolvePlan` precedence (PRO beats PLUS; FREE is the complement) | ≤396 |

**⚠️ Editorial, never authorization.** The class holds no reference to `FeatureGateService` and a test
asserts that. This contract must survive any expansion.

### 2.14 Cleanup / retention

`NotificationCleanupJob` — cron `0 15 * * * *` (hourly at :15), `retention-days: 90`.

```java
delete from NotificationEntity n
where n.createdAt < :threshold
  and (n.readAt is not null or n.dismissedAt is not null)
```

**⚠️ A row that is never read and never dismissed is never deleted.** See §8 and §19.

`deleteByRecipientUserId` exists separately for account erasure (`AccountPurgeService`) and correctly
takes unread actionable rows too.

### 2.15 Existing performance instrumentation

Structured log lines only — no metrics, no timers:

- `announcement.fan_out.chunk` (every 500 recipients: progress, delivered, skipped)
- `announcement.fan_out` (final totals)
- `announcement.fan_out.failed` (per failed recipient)
- `notification.cleanup deleted N ...`

There is no counter, histogram or dashboard. Actuator is present (`/actuator/health` is referenced in
the outage findings) but no notification metrics are registered.

---

## 3. Current email inventory

### 3.1 Every email type, classified

`RetentionEmailType` has 13 values. Classification per the brief's §3 taxonomy:

| # | Type | Trigger | Class | Preference gate | Unsubscribe category | Prod sends (30d) |
|---|---|---|---|---|---|---|
| 1 | `EMAIL_VERIFICATION` | signup / email change | **account/security** | none (mandatory) | none | 5 |
| 2 | `PASSWORD_RESET` | user request | **account/security** | none (mandatory) | none | 0 |
| 3 | `WELCOME` | post-verification | **transactional** | none | none | 23 |
| 4 | `INACTIVITY` | daily job, inactivity window | **retention/re-engagement** | `inactivity_reminders_enabled` | `STUDY_REMINDERS` | **1,639** |
| 5 | `WEAK_CONCEPT` | daily job, post-Challenge | **retention/re-engagement** | `weak_concept_reminders_enabled` | `WEAK_CONCEPT` | 0 |
| 6 | `UNFINISHED_NOTE` | ⚠️ **none — dead configuration** | retention | — | — | 0 |
| 7 | `WEEKLY_SUMMARY` | weekly job, Sun 18:00 Manila | **scheduled/digest** | `weekly_summary_reminders_enabled` | `WEEKLY_SUMMARY` | 0 |
| 8 | `DUE_CONCEPTS_DIGEST` | daily job, learner's review days | **scheduled/digest** | `due_concepts_digest_reminders_enabled` | `DUE_CONCEPTS_DIGEST` | **430** |
| 9 | `KNOWLEDGE_IMPACT_DIGEST` | monthly job, 1st at 09:00 | **scheduled/digest** | `knowledge_impact_digest_reminders_enabled` | `KNOWLEDGE_IMPACT_DIGEST` | 0 |
| 10 | `SUBSCRIPTION_EXPIRY_7_DAY` | expiry scheduler | **billing** | none | none | 0 |
| 11 | `SUBSCRIPTION_EXPIRY_1_DAY` | expiry scheduler | **billing** | none | none | 0 |
| 12 | `SUBSCRIPTION_EXPIRED` | expiry scheduler | **billing** | none | none | 0 |
| 13 | `RE_ENGAGEMENT_2025` | `ReEngagementCampaignService` (manual) | **marketing** | — | `MARKETING` | 0 |

**⚠️ On row 6 — `UNFINISHED_NOTE` is dead configuration, verified not assumed.** A full grep of
`backend/src` (main **and** test) returns exactly five hits: the enum constant, two
`StudySnapProperties` fields (`unfinishedNoteDays = 2`, `unfinishedNoteCooldownDays = 3`) and their two
`application.yaml` keys. **There is no dispatcher, no template reference and no test.** It is a
configured, documented, tunable email that cannot be sent. Harmless, but it means the enum is not a
reliable inventory of what the system actually does — worth knowing before anyone reads a new value
into it.

Plus one email that is **not** in `RetentionEmailType` and therefore **not logged to `email_log`**:
the feedback notification (`templates/email/feedback-notification.*`, `FeedbackService`) — internal,
to the operator, not to learners.

### 3.2 Templates

`EmailTemplateService` renders a triple per template: `.html`, `.txt`, `.subject.txt`. Files present
in `resources/templates/email/`: `verification-email`, `password-reset-email`, `welcome-email`,
`feedback-notification`. Retention templates (`retention-inactivity-reminder` and siblings) are
resolved by name at dispatch — their files live alongside and are loaded by convention.

### 3.3 `EmailService`

Interface is one method: `boolean sendEmail(EmailMessage)`. Sole implementation
`ResendEmailService` (Resend HTTP API):

- Checks `SuppressedEmailService.isSuppressed(to)` **first** and returns `false` without sending.
- **Synchronous blocking `HttpClient.send`.**
- Retries **only on HTTP 429**, up to 3 attempts, honouring `Retry-After`, else exponential backoff
  clamped to 5,000 ms. **⚠️ Worst case ≈ 10 s of `Thread.sleep` on the calling thread.**
- Any other non-2xx throws `AppException(EMAIL_DELIVERY_FAILED, …, BAD_GATEWAY)`.
- IO / interruption → `EMAIL_PROVIDER_UNREACHABLE`, `BAD_GATEWAY`.

**⚠️ There is no send queue, no outbox and no async wrapper.** Every email in this codebase is sent on
the thread that decided to send it.

### 3.4 `email_log` and idempotency

`V29__retention_email_log.sql`:

```sql
CREATE TABLE email_log (id UUID PK, user_id UUID NOT NULL, email_type VARCHAR(32) NOT NULL, sent_at TIMESTAMPTZ NOT NULL);
CREATE INDEX idx_email_log_user_type_sent_at ON email_log (user_id, email_type, sent_at DESC);
CREATE INDEX idx_email_log_sent_at ON email_log (sent_at DESC);
```

**⚠️ There is no unique constraint anywhere on this table.** Email idempotency is entirely
`cooldownElapsed()` — a read (`existsByUserIdAndEmailTypeAndSentAtAfter`) followed by a send followed
by an insert. **This is precisely the "check exists → insert" pattern the brief's §24 prohibits**, and
two concurrent dispatchers would both pass it.

In practice it has not fired because every producer is a `@Scheduled` job on a single instance. **It
becomes a live race the moment an event-driven email producer exists** — which is exactly what
Stage E proposes. Recorded as a Stage E precondition in §17.

### 3.5 Marketing preference

`users.marketing_emails_enabled` (`V79`), `NOT NULL DEFAULT FALSE`.

**⚠️ Production: 0 of 396 users have it enabled.** And nothing on the retention path consults it — the
1,639-send `INACTIVITY` channel gates on `inactivity_reminders_enabled`, which `V82` force-set `TRUE`
for every existing user and which stands at **393/396** today.

So the flag named "marketing emails" governs **only** `ReEngagementCampaignService`, which has sent
nothing. This is a genuine product/legal question, not a bug — see §22, decision D4.

### 3.6 Unsubscribe

Mature and correctly built:

- `UnsubscribeTokenService` — signed tokens carrying `(userId, category)`.
- `EmailUnsubscribeLinkService` — builds both a landing URL (`/unsubscribe?token=…`) and a **one-click
  URL** (`/api/email/unsubscribe?token=…`), plus HTML and text footers and List-Unsubscribe headers.
- `UnsubscribeCategory` — 6 categories, each with a display name.
- Fallback when link construction fails: `mailto:support@mail.notelib.app?subject=unsubscribe`.
- `EmailUnsubscribeController` is **unauthenticated by design** (token-authorized).

### 3.7 Transactional rules, budget and suppression

| Control | Value | Enforcement |
|---|---|---|
| `EMAIL_DAILY_LIMIT` | **100** | `resolveReengagementBudget` |
| `EMAIL_TRANSACTIONAL_RESERVE` | **40** | same |
| Budget formula | `limit - reserve - sentToday` | counted from `email_log` for the Manila day |
| **Applies to** | ⚠️ **`INACTIVITY` only** | `dispatchBudgetedInactivityEmails` |
| Suppression | `suppressed_email` (address PK) | checked in `ResendEmailService.sendEmail` |
| Suppressed addresses in prod | **0** | prod read |

**⚠️ THE CAP IS NOT A CAP. Observed production peaks: 156, 158, 157, 156 sends/day** (top four days in
the last 90). Weak-concept, weekly-summary, due-concepts and knowledge-impact dispatches are all
unbudgeted, so the "100/day limit" describes only one of five channels. See §16 risk R1.

### 3.8 Cooldowns

| Type | Property | Default |
|---|---|---|
| Inactivity | `inactivity-cooldown-days` | 3 |
| Weak concept | `weak-concept-cooldown-days` | 5 |
| Unfinished note | `unfinished-note-cooldown-days` | 3 |
| Weekly summary | `weekly-cooldown-days` | 7 |
| Knowledge impact digest | `knowledge-impact-digest-cooldown-days` | 30 |
| Due concepts digest | `due-concepts-digest-cooldown-days` | 7 |

### 3.9 Deep links inside emails

Absolute URLs built by `buildAbsoluteUrl(...)` from configured site base — e.g. `DASHBOARD_PATH` for
inactivity resume. Emails are **not** subject to `AnnouncementCtaPathValidator` (correctly — they are
absolute by necessity, and the author is the codebase, not an admin).

---

## 4. Current scheduled / background jobs

| Job | Cron | Zone | Touches email? | Notes |
|---|---|---|---|---|
| `RetentionEmailScheduler.runDaily` | `0 45 2 * * *` | Asia/Manila | ✅ | inactivity + weak concept, then due-concepts digest |
| `RetentionEmailScheduler.runWeekly` | `0 0 18 * * SUN` | Asia/Manila | ✅ | weekly summary |
| `RetentionEmailScheduler.runMonthly` | `0 0 9 1 * *` | ⚠️ **unset** → `systemDefault()` | ✅ | knowledge-impact digest — see R7 |
| `SubscriptionExpiryEmailScheduler` | — | — | ✅ | 7-day / 1-day / expired |
| `SubscriptionExpiryJob` | — | — | ❌ | plan state |
| `NotificationCleanupJob` | `0 15 * * * *` | default | ❌ | hourly |
| `BillingUsageResetJob` | — | — | ❌ | monthly quota reset |
| `BulkGenerationResultCleanupJob` | `0 45 * * * *` | default | ❌ | hourly |
| `GenerationRecoveryJob` | — | — | ❌ | stuck generations |
| `LinkedLearnerRequestExpiryJob` | — | — | ❌ | **relevant** — connection requests already expire |
| `AccountPurgeScheduler` | — | — | ❌ | erasure |
| 4 × rate-limit services | — | — | ❌ | in-memory bucket sweeps |

`@EnableScheduling` is on `BackendApplication`. **`@EnableAsync` is not present anywhere.** Async work
is submitted explicitly to named executors, never via `@Async`.

### 4.1 Executors (`AppConfig`)

| Bean | core / max / queue | Purpose |
|---|---|---|
| `analyticsTaskExecutor` | 1 / 2 / bounded | fire-and-forget analytics persistence |
| `studyPackGenerationTaskExecutor` | 2 / 2 / 100 | study pack generation |
| `bulkRegenerationTaskExecutor` | 2 / 2 / 8 | bulk regeneration |
| `llmParallelTaskExecutor` | 4 / 8 / 50 | LLM fan-out batches |

**`AnalyticsEventListener` is the exact in-repo precedent for what notification fan-out should be:** a
cheap secondary write, offloaded via `analyticsTaskExecutor.execute(() -> persistEvent(event))`,
fire-and-forget, on a pool of at most 2 threads. Any async recommendation in §18 should name it rather
than invent a pattern.

---

## 5. Existing notification event matrix (what exists TODAY)

| Event | Producer | Type | Dedup key | Recipients | Deep link | Delivery |
|---|---|---|---|---|---|---|
| Admin announcement published | `AnnouncementService.publish` | `ANNOUNCEMENT` | `ANNOUNCEMENT:<announcementId>` | audience-resolved, ≤396 | admin-authored `cta_path` | **synchronous, in the admin request** |

That is the complete list. **One producer, one type, zero rows in production.**

---

## 6. Current badge semantics

| Property | Behaviour | Verified |
|---|---|---|
| Counts | `type IN actionableTypes()` = `{ACTION_REQUIRED}`, `read_at IS NULL`, `dismissed_at IS NULL`, announcement not ended/expired | `NotificationRepository.countActionableUnread` |
| Announcements inflate it | **No** | `ANNOUNCEMENT.actionable == false` |
| Renders literal `0` | **No** — `count > 0` guard | `notification-inbox.tsx` |
| Dot for low-urgency unseen | **Does not exist** | — |
| Poll cadence | 60 s, paused on hidden tab | `app-shell.tsx` |
| Client badge decrement | local `-1` guarded by `notification.type !== "ANNOUNCEMENT"` | `notification-inbox.tsx` |
| **Value in production, ever** | **0** | prod read: 0 rows, 0 `ACTION_REQUIRED` producers |

### ⚠️ 6.1 What the existing tests can and cannot claim

`NotificationServiceIntegrationTest` covers dedup, cross-recipient isolation, retention, badge
exclusion of announcements, and the dismissed-but-unread case. `NotificationControllerTest` includes a
real-request test using `"ACTION_REQUIRED"`.

**But every `ACTION_REQUIRED` assertion is produced by calling `notificationService.deliver(...)`
directly with a hand-built `NotificationDelivery`** — because no production code path constructs one.
This is exactly the fixture pattern `CLAUDE.md` flags twice ("a fixture hand-built a state no code
path can produce"). The behaviour under test is correct; what the suite **cannot** establish is that
any real producer reaches it correctly. **The first `ACTION_REQUIRED` producer owes an end-to-end test
that starts from the product action, not from `deliver()`.**

---

## 7. Current deep-link behaviour

- One mechanism: the `cta_path` string, admin-authored, validated three times.
- **No typed target**, no `target_type` / `target_id`, no resolution at render.
- Frontend renders `next/link` with `toSafeRelativePath(...)`; a value failing the rule renders as
  **no link**, never as a link elsewhere.
- Clicking the CTA marks the row read (optimistically) and navigates. It performs no workflow action.

### 7.1 Deep-link targets that already exist (verified routes)

| Intent | Route | Exists |
|---|---|---|
| Adopted Review Set, incl. its update surface | `/collections/{adoptedCollectionId}` (`sourceUpdate` state lives in `collection-detail-page-client.tsx`) | ✅ |
| A specific note | `/notes/{id}` | ✅ |
| A note shared with me | `/shared/notes/{id}` | ✅ |
| Learning Connections | `/linked-learners` | ✅ (no per-request anchor) |
| Supporter progress view | `/linked-learners/{relationshipId}/progress` | ✅ |
| Knowledge Impact | `/progress` | ✅ |
| Program-filtered discovery | `/public/library?courseProgram=<encoded>&sort=recent` | ✅ |
| Exam-goal hub | `/exam/{slug}` | ✅ |

**Two things this table settles:**

1. The brief's §7 requirement that a Review Set update deep-link to the exact adopted set — **the
   target exists**, `/collections/{adoptedCollectionId}`, and the adopted collection id is already the
   natural dedup identity. No new route needed.
2. The brief's §18 worry that Discovery would have to dump users on a generic Explore page — **it
   would not**; `buildPublicLibraryUrl({courseProgram})` produces a real pre-filtered URL. ⚠️ Note the
   CTA query regex permits `%` but not a literal space, so the program value **must** be
   percent-encoded by the producer.

---

## 8. Current retention / cleanup

| Table | Policy | Gap |
|---|---|---|
| `notifications` | hourly job, deletes `created_at < now-90d AND (read_at IS NOT NULL OR dismissed_at IS NOT NULL)` | ⚠️ **unread + undismissed rows are never deleted** |
| `notifications` (erasure) | `deleteByRecipientUserId` on account purge | ✅ correct, takes unread rows too |
| `announcements` | **none** — rows persist after `ENDED` | low volume, acceptable |
| `email_log` | **none** — 4,708 rows and growing | see below |
| `suppressed_email` | **none** (correct — suppression must be permanent) | ✅ |

**Growth arithmetic for the unread-row gap:** one `EVERYONE` announcement = 396 rows. Historic bell
open-rates are unknown (0 announcements have ever been sent), but if 30% open and act, ~277 rows per
announcement are permanent. At one announcement a month that is ~3,300 immortal rows a year — trivial
in absolute terms against a 135 MB database with 15 GB of disk, and **still wrong as a policy**,
because the brief's §27 asks for a proportional retention rule and there isn't one.

`email_log` at 4,708 rows has no cleanup either, but it is load-bearing for cooldown correctness —
**do not add retention there without redesigning cooldowns**, which is out of scope.

---

## 9. Current indexes / query paths — findings

### ⚠️ 9.1 The inbox sort is unindexed

```
idx_notifications_recipient_unread  = (recipient_user_id, read_at)
findVisibleInbox                    = WHERE recipient_user_id = ? AND dismissed_at IS NULL
                                      ORDER BY created_at DESC LIMIT ?
```

Neither existing index covers `created_at`. PostgreSQL will fetch **all** of a recipient's rows via
the recipient prefix, then sort. Bounded by `LIMIT` in output but not in work.

**Recommendation (Stage B):**
```sql
CREATE INDEX idx_notifications_inbox
    ON notifications (recipient_user_id, created_at DESC)
    WHERE dismissed_at IS NULL;
```
At today's row counts this is immaterial. It is recommended anyway because it is one line in the same
migration the taxonomy change needs, and because a 256 MB Postgres has very little room to absorb a
sort it did not need to do.

### 9.2 The retention delete is a sequential scan

`deleteReadOrDismissedBefore` filters on `created_at` with no supporting index. Hourly, on a table
with 0 rows — currently free. Worth a partial index only if the table passes ~100k rows; **do not add
one now**.

### 9.3 No N+1 exists today, and that is by construction

The inbox does zero per-row lookups. The announcement lifecycle test is a correlated subquery. This
property is **the thing most easily lost** by a producer that decides to resolve live target state per
row (§9 of the brief). Guard it explicitly (§17, §42).

### 9.4 The real constraint is the connection pool, not query cost

This is the single most important systems fact in this document.

| Fact | Value |
|---|---|
| Postgres plan | **`basic_256mb`** — 256 MB RAM, 15 GB disk |
| Read replicas | **none** |
| Connection pooler | **none** |
| `max_connections` | 103 |
| Hikari `maximum-pool-size` | **20** — and `application.yaml` says **do not raise it** |
| Hikari `connection-timeout` | 5,000 ms fail-fast (waiters get a 500 rather than queueing) |
| Current DB size | 135 MB |

**Two production outages were caused by pool exhaustion** — 2026-09-04 at pool=10 and 2026-09-05 at
pool=20 (`docs/claude-findings/2026-09-04-…` and `2026-09-05-…`). The recorded lesson is verbatim:
*"RAISING THIS DOES NOT FIX POOL EXHAUSTION … the holds are unbounded in DURATION … DO NOT RAISE THIS
TO 40 AND BUY ANOTHER FEW WEEKS — bound the work instead."* Raising it is gated on
`[CHECKPOINT — due 2026-10-04]`.

**Consequence for this work, and it is not the obvious one:** moving announcement fan-out to a
background thread fixes the *gateway-timeout* risk and does **nothing** for pool pressure — the same
396 `saveAndFlush` calls still each take a connection, just off the request thread. **The binding
constraint on any fan-out design is concurrency ≤ 1–2 connections**, which is exactly why
`analyticsTaskExecutor` is sized 1/2 and why it is the right precedent.

---

## 10. Polling behaviour

| Property | Current | Verdict |
|---|---|---|
| Interval | 60 s | ✅ appropriate — notifications do not need sub-minute freshness |
| Endpoint | count only (`/notifications/unread-count`) | ✅ meets §25's "lightweight unread-count endpoint" |
| Inbox fetched on poll | **no** — only on panel open | ✅ meets §25's "fetch inbox contents only when panel opens" |
| Paused when tab hidden | **yes** | ✅ |
| Refresh on window focus | **no** | ➖ nicety, not a defect |
| Realtime (WS/SSE) | **none** anywhere in the repo | ✅ keep it that way |
| Cost at 396 users | ≤396 count queries/min worst case, realistically far fewer (only active tabs poll) | ✅ negligible |

**No polling change is recommended.** The one optional improvement — refresh on focus — is
explicitly deprioritised; it adds a listener to the app shell, which is the component whose blast
radius is every authenticated page.

---

## 11. Email preference / unsubscribe semantics

### 11.1 The channel policy as it actually stands

| Class | Gate | Unsubscribable | Honours `marketing_emails_enabled` |
|---|---|---|---|
| Account/security (verification, password reset) | none | **no** (correct) | no |
| Transactional (welcome) | none | no | no |
| Billing (expiry ×3) | none | no | no |
| Retention (inactivity, weak concept) | per-type boolean | yes, per category | ⚠️ **no** |
| Digest (weekly, due concepts, knowledge impact) | per-type boolean | yes, per category | ⚠️ **no** |
| Marketing (`RE_ENGAGEMENT_2025`) | — | yes, `MARKETING` | ✅ yes |

### 11.2 The finding

**`marketing_emails_enabled` is effectively decorative.** 0/396 users have it on, and the only channel
that reads it has sent nothing. Meanwhile the 393-user `INACTIVITY` channel — a re-engagement email by
any reasonable reading — runs on a separate flag that `V82` force-enabled for everyone.

**This is recorded as an owner decision (D4), not as a bug to fix.** Routing retention through the
marketing flag would silence 393 of 396 users overnight and destroy the product's only working
re-engagement channel. The defensible positions are (a) keep per-type flags as the real preference
surface and treat `marketing_emails_enabled` as governing promotional content only — stating that
explicitly in `docs/features/`; or (b) reclassify. Either is fine; the current state is that neither
has been decided and the code reads as though (a) is true without saying so.

**Hard constraint regardless: no new email producer may bypass a per-type preference flag and an
unsubscribe category.** A notification becoming email-eligible does not make it transactional.

---

## 12. Candidate-event cardinality analysis

Measured against production, 2026-09-08.

### 12.1 Low cardinality — safe for direct, targeted notification

| Event | Recipients per occurrence | Occurrences observed | Total rows/yr (projected) |
|---|---|---|---|
| Learning Connection request | 1 | `linked_learner_relationships` = **1** | <10 |
| Explicit named note share | 1 | `note_shares` = **0** | <10 |
| Progress-access request | 1 | **feature does not exist** (`requestGrant`/`requestAccess`/`grantRequest` → 0 hits) | 0 |
| Official Review Set update | 1 per adopter | max **30**, see below | ≤120 (4 sets × ~1 update/quarter) |

**Adopters per Official Review Set root (the exact fan-out size of a publish):**

| Review Set | Adopters |
|---|---|
| 🏛️ ALE Comprehensive Review | **30** |
| 🩺 PNLE Core Nursing Review | 15 |
| 📊 CPALE Comprehensive Review | 8 |
| 🏗️ Civil Engineering Comprehensive Review | 1 |
| **Total root adoptions** | **54** |

*(`note_collections` where `source_plan_id IS NOT NULL` is 557 rows, but that counts adopted **child**
subject plans too. The behind-episode recipient set is the root adopters — 54 across all four sets.)*

### 12.2 Medium cardinality — must be async and idempotent

| Event | Recipients | Notes |
|---|---|---|
| Admin announcement, `EVERYONE` | **396** | today's largest fan-out; currently **synchronous** |
| Admin announcement, `PROFILE_TYPE` / `PLAN_TYPE` | ≤396 | same path |

### 12.3 High cardinality — must never be one row per event

| Event | Volume signal | Verdict |
|---|---|---|
| Note like | `public_note_likes` = **3 lifetime** | not recommended — no product signal, and it is the brief's §16/§42 anti-pattern |
| Note view | `analytics_events` = 17 MB | never |
| Note copy | derived from analytics | milestone only, never per-event |
| New public note in program | **1,591 public notes**, 7,408 total | digest only, and see D5 |
| Study Pack generation | per-user, frequent | never |
| Quiz completion / weak concept / readiness change | per-session | never — Dashboard's job (§10) |

**On "new public note in program":** at 1,591 public notes with a course-program tag and 396 users,
naive per-note fan-out to every learner whose profile program matches would be catastrophic on any
timeline — and it is also **wrong in principle** (§36). It stays deferred.

### 12.4 Burst analysis

The only real burst in this system is an admin publishing an announcement to `EVERYONE`: **396 inserts
in one HTTP request**, currently sequential `saveAndFlush`. At ~2 ms/insert that is ~0.8 s of database
time — fine. At 10,000 users it is ~20 s and outruns a gateway. The mitigation is already documented in
`AnnouncementService`'s javadoc (re-press Publish), which is honest but is not a design.

---

## 13. Proposed notification priority taxonomy

### 13.1 The problem to fix

`NotificationType` is 2 values with 1 boolean. The brief needs 6 categories with 3 badge behaviours.
And `dedupKey(type, entityId)` derives dedup identity **from that same enum** — so every future
`ACTION_REQUIRED` producer shares one key space, and two producers holding the same entity UUID
collide silently.

**These are one change, not two, and the window to make it for free is open right now:
`notifications` has 0 rows.** No backfill, no reconciliation, no risk of mis-classifying a delivered
row. This will never be free again.

### 13.2 Proposed shape

Separate the two concerns the current enum conflates:

**(a) `NotificationType` becomes producer-level identity** — it is what makes a dedup key unique:

| Value | Producer | Category |
|---|---|---|
| `ANNOUNCEMENT` | `AnnouncementService` | ANNOUNCEMENT |
| `REVIEW_SET_UPDATE` | Review Set publish | LEARNING_SYSTEM |
| `CONNECTION_REQUEST` | `LinkedLearnerService` | ACTION_REQUIRED |
| `NOTE_SHARED` | note share | SHARED_WITH_YOU |
| *(future)* `PROGRESS_ACCESS_REQUEST`, `IMPACT_MILESTONE`, `DISCOVERY_DIGEST` | — | — |

**(b) A `NotificationCategory` carries badge policy**, and `actionableTypes()` derives its parameter
set from the category rather than a boolean:

| Category | Numeric badge | Aggregated | Email-eligible |
|---|---|---|---|
| `ACTION_REQUIRED` | ✅ | no | probably |
| `LEARNING_SYSTEM` | ✅ | no | maybe |
| `SHARED_WITH_YOU` | ✅ | no | maybe |
| `IMPACT` | ❌ | yes | no |
| `DISCOVERY` | ❌ | yes | digest only |
| `ANNOUNCEMENT` | ❌ | no | rarely |

**⚠️ Deliberately keep the category derived in Java from the type, not stored as a column.** A stored
category is a second source of truth that can disagree with the type, and there is no query that
needs to filter on it independently — `countActionableUnread` already takes its set as a parameter.

### 13.3 Migration shape (owner runs it; Claude does not)

```sql
-- notifications is EMPTY in production (verified 2026-09-08). No backfill, no data risk.
-- Widening only: existing values ANNOUNCEMENT / ACTION_REQUIRED remain valid.
CREATE INDEX idx_notifications_inbox
    ON notifications (recipient_user_id, created_at DESC)
    WHERE dismissed_at IS NULL;
```

The type widening itself needs **no DDL** — `type` is already `VARCHAR(64)` and the enum is
`EnumType.STRING`. **The whole change is Java plus one index.** That is the argument for doing it now.

### 13.4 The frontend mirror must move with it

`notification-inbox.tsx` compares `notification.type !== "ANNOUNCEMENT"` to decide whether to decrement
the badge locally. **That literal breaks the moment a third non-actionable type exists.** The response
should carry an explicit `actionable` (or `category`) field and the client should read it, rather than
re-deriving policy from a type string.

---

## 14. Proposed channel policy

**Governing rule: in-app can inform; email must justify interruption.**

| Class | In-app | Email | Rationale |
|---|---|---|---|
| ACTION_REQUIRED | direct, numeric badge | **deferred** — denominator is 1 | see D3 |
| LEARNING_SYSTEM (Review Set update) | direct, numeric badge | **not in first slice** | in-app first; measure open rate before spending email budget |
| SHARED_WITH_YOU | direct, numeric badge | deferred (0 shares) | — |
| IMPACT | aggregated, no badge | **never** | §17 |
| DISCOVERY | digest, no badge | **never by default** | §18, D5 |
| ANNOUNCEMENT | inbox, no badge | **never** | admin has no email surface, and building one is a marketing tool |
| Account/security/billing | existing | existing | unchanged |

**⚠️ No email is recommended in the near-term slices at all**, and the reason is the measured one in
§3.7: the daily cap is already exceeded by ~58% and only one of five channels is governed by it.
**Adding an email producer before a global budget exists would push a breached limit further.** That
ordering — global budget first, then email producers — is Stage E's precondition, not a nicety.

---

## 15. Deep-link matrix

| Notification | Copy (per §32) | Deep-link target | Route verified | Stale-target strategy |
|---|---|---|---|---|
| Review Set update | **"ALE Comprehensive Review was updated"** / "24 new topics are available." / *Review update →* | `/collections/{adoptedCollectionId}` | ✅ | snapshot title in body; if the adopted collection is gone the page 404s → **resolve on click**, land on `/collections` with a notice |
| Note shared | **"Maria shared a note with you"** / note title / *View note →* | `/shared/notes/{noteId}` | ✅ | share revocable → **resolve on click**; snapshot title so the row still reads sensibly |
| Connection request | **"Someone asked to connect"** / *Review request →* | `/linked-learners` | ✅ (no per-request anchor) | request may be accepted/expired → surface resolves current state itself |
| Impact milestone | **"Your note helped 50 learners"** / note title / *View impact →* | `/progress` | ✅ | snapshot only; never re-resolved |
| Discovery digest | **"12 new Civil Engineering notes this week"** / *Explore →* | `/public/library?courseProgram=Civil%20Engineering&sort=recent` | ✅ | none needed — a filter always resolves |
| Announcement | admin-authored | admin-authored `cta_path` | validated ×3 | none — self-contained copy |

**Bad copy, explicitly rejected** (§32): `NOTE_SHARE_CREATED`,
`REVIEW_SET_SOURCE_SYNC_DRIFT_DETECTED`, "You haven't studied today!".

---

## 16. Performance risks

Ranked by expected damage, with the pre-existing ones separated from the ones this proposal would
introduce.

### Pre-existing (present today, independent of this work)

| # | Risk | Severity | Evidence |
|---|---|---|---|
| **R1** | **Email daily cap is not a cap.** Budget gates `INACTIVITY` only; peaks of 156–159/day against a limit of 100. | **High** | prod: top-4 days 90d |
| **R2** | **Unread notification rows are immortal.** Retention requires `read_at` or `dismissed_at`. | Medium | `deleteReadOrDismissedBefore` |
| **R3** | **Announcement fan-out is synchronous in the admin request.** 396 sequential `saveAndFlush`, no timeout guard. | Medium (High at 10× users) | `AnnouncementService.publish` |
| **R4** | **`email_log` has no unique constraint.** Idempotency is check-then-insert. | Medium *(latent — single-instance schedulers only)* | `V29`, `cooldownElapsed` |
| **R5** | **Connection pool is the binding constraint, and it has failed twice.** Pool 20, 5 s fail-fast, raising gated to 2026-10-04. | **High** | two findings docs |
| **R6** | **Inbox sort is unindexed** (`created_at DESC` uncovered). | Low today | §9.1 |
| **R7** | **`runMonthly` has no `zone`**, unlike its two siblings — uses `systemDefault()`. | Low | `RetentionEmailScheduler` |
| **R8** | `ResendEmailService` can block a thread ~10 s (3 attempts × 5 s clamp) on 429. | Low *(scheduled context)* | `waitBeforeRetry` |

### Would be introduced by careless expansion

| # | Risk | Trigger to avoid |
|---|---|---|
| **R9** | **Transaction poisoning of fan-out.** `deliver()` relies on catching `DataIntegrityViolationException`; under an ambient transaction that marks the whole thing rollback-only. | Any producer calling `deliver()` inside `@Transactional` |
| **R10** | **N+1 in the inbox.** Currently zero per-row lookups by construction. | Resolving live target state per row |
| **R11** | **Dedup key collision across producers.** `ACTION_REQUIRED:<uuid>` shared by four producers. | Adding a producer before §13 lands |
| **R12** | **Badge mis-count on the client.** `type !== "ANNOUNCEMENT"` literal. | Adding a third non-actionable type without §13.4 |
| **R13** | **Pool starvation from a fan-out pool.** A new executor sized >2 competes with request threads for 20 connections. | Sizing a fan-out executor like `llmParallelTaskExecutor` (4/8) instead of like `analyticsTaskExecutor` (1/2) |

---

## 17. Dedup / idempotency design

**Principle: database uniqueness, never check-then-insert.** The existing
`idx_notifications_recipient_dedup` is correct and must remain the mechanism.

| Producer | Dedup key | Why this identity |
|---|---|---|
| Announcement | `ANNOUNCEMENT:<announcementId>` | current, correct — re-publish inserts 0 rows |
| **Review Set update** | `REVIEW_SET_UPDATE:<adoptedCollectionId>` | **⚠️ the adopted collection, not the source.** One row per adopter per behind-episode, and the episode is closed when the learner applies or dismisses. The older drift-signature key is **superseded — do not implement it** (publication boundary removed the need). |
| Connection request | `CONNECTION_REQUEST:<relationshipId>` | the relationship row is the request |
| Note shared | `NOTE_SHARED:<noteShareId>` | `note_shares.id` already exists |
| Impact milestone | `IMPACT_MILESTONE:<noteId>:<threshold>` | ⚠️ **needs a composite key** — `dedupKey()` takes one UUID today; see below |

### 17.1 The one structural gap

`NotificationService.dedupKey(NotificationType, UUID)` accepts exactly one UUID. An impact milestone
needs `(noteId, threshold)`. **Recommendation: widen the helper to accept a `String` discriminator**
(`dedupKey(type, String)`), keeping the UUID overload. `dedup_key` is already `VARCHAR(255)` — no
migration. Do this in the same Stage B change as §13, not later.

### 17.2 Behind-episode semantics (Review Set update)

> **At most one notification per adopter per behind episode.**

An episode opens when `publishReviewSetUpdate` moves `last_update_published_at` and the adopter is
behind; it closes when the learner applies or dismisses. Because the dedup key is
`REVIEW_SET_UPDATE:<adoptedCollectionId>` — with **no episode counter** — a second publish while the
first row is still undismissed inserts **nothing**, which is the desired behaviour: the learner is
already told they are behind, and the copy ("N new topics") is a snapshot, not a live count. A third
publish after the learner dismissed the first correctly delivers again.

**⚠️ This means the body text can understate.** That is the right trade: an accurate-at-delivery
snapshot beats a live count that would require a per-row query (R10).

### 17.3 Email idempotency

`email_log` has no unique constraint (R4).

**⚠️ THE OBVIOUS FIX IS WRONG, AND THE READ PROVES IT.** The natural proposal —
`UNIQUE (user_id, email_type, date_trunc('day', sent_at))` — was checked against production before
being recommended, and it **would break email verification.**

Duplicate `(user, type, day)` groups in all 4,708 rows:

| user | type | day | sends |
|---|---|---|---|
| `42faa437…` | `EMAIL_VERIFICATION` | 2026-06-30 | **4** |
| `babb35eb…` | `EMAIL_VERIFICATION` | 2026-08-10 | **3** |

**Both are correct behaviour, not defects.** A user may legitimately request a verification resend
several times in a day; the control on that is `resend-cooldown-seconds: 60`, not a daily cap. A
blanket daily-uniqueness index would have started rejecting the second resend — turning a working
account-recovery path into a support ticket, to fix a race that has never occurred.

**The correct shape is scoped to the classes that are actually once-per-period**, i.e. the scheduled
and digest types, and explicitly **not** the on-demand account/security ones:

```sql
-- Shape only. NOT validated for the full type list, and NOT a proposal to run.
CREATE UNIQUE INDEX ux_email_log_scheduled_user_type_day
    ON email_log (user_id, email_type, (date_trunc('day', sent_at)))
    WHERE email_type IN ('INACTIVITY', 'WEAK_CONCEPT', 'WEEKLY_SUMMARY',
                         'DUE_CONCEPTS_DIGEST', 'KNOWLEDGE_IMPACT_DIGEST');
```

⚠️ Even this is **not yet a recommendation** — the three `SUBSCRIPTION_EXPIRY_*` types were not
analysed for legitimate same-day repeats, and `date_trunc` in UTC does not match the Manila dispatch
day the budget already uses, so the index and the cooldown would disagree about "today". **Settling
both is Stage E work, not Stage A output.** Recorded here so nobody proposes the blanket version
again.

**The general lesson, which generalises past this table:** the brief's §24 rightly forbids
check-then-insert, but the fix is not uniqueness everywhere. Uniqueness must match the event's real
period, and for user-initiated events that period is *not* a day.

---

## 18. Async / fan-out recommendations

**Do not build:** Kafka, a broker, event sourcing, an outbox framework, SSE/WebSocket, or a generic
event bus. None is justified at 396 users, and §4 above shows the repo has a working pattern already.

### 18.1 The recommended mechanism

**Copy `AnalyticsEventListener` exactly:**

```java
// bean sized like analyticsTaskExecutor — core 1, max 2, bounded queue
notificationFanOutExecutor.execute(() -> fanOut(announcement, recipientIds));
```

- Fire-and-forget from the request thread; the product action commits first and succeeds regardless.
- **Core 1 / max 2** — sized against the connection pool (R5/R13), not against throughput.
- Bounded queue with a caller-runs or discard policy; **decide explicitly**, and prefer discard-with-
  log for announcements (a dropped fan-out is recoverable by re-pressing Publish, which is already the
  documented recovery).
- **No `@EnableAsync`.** The repo does not use it, and enabling it changes proxying semantics
  repo-wide.

### 18.2 Per-producer delivery mode

| Producer | Recipients | Mode | Justification |
|---|---|---|---|
| Announcement publish | ≤396 | **background executor** | R3; recovery already exists |
| Review Set update publish | ≤30 | **background executor** | same path, same code; do not special-case a small number into the request thread |
| Connection request | 1 | **synchronous, after commit** | single indexed insert, no external call, negligible latency — meets §23's "allowed if proven cheap" |
| Note share | 1 | **synchronous, after commit** | same |
| Impact milestone | 1 | **scheduled job** | milestones are computed, not evented |
| Discovery digest | ≤396 | **scheduled job** | deferred entirely |

**⚠️ "After commit", not "inside the transaction"** (R9). The clean form is Spring's
`TransactionSynchronizationManager` / `@TransactionalEventListener(AFTER_COMMIT)` — but note this repo
has no such listener today, so introducing one is itself a small architectural decision worth naming
in the implementing prompt rather than smuggling in.

---

## 19. Retention recommendation

Extend `NotificationCleanupJob`, keeping its existing leg intact:

| Row class | Policy |
|---|---|
| Read or dismissed | delete after **90 days** *(unchanged)* |
| **Non-actionable, unread, undismissed** (`ANNOUNCEMENT`, `IMPACT`, `DISCOVERY`) | delete after **90 days** — ⚠️ **new leg** |
| **Actionable, unread, undismissed** | **keep indefinitely** *(unchanged — a pending request must not vanish)* |
| Announcement rows whose announcement is `ENDED`/expired | already invisible on read; deleted by whichever leg matches | — |
| `announcements` | no cleanup — low volume | — |
| `email_log` | **no cleanup** — cooldown correctness depends on it | — |

Rationale: an announcement nobody opened is not a pending obligation, so nothing is lost by expiring
it; a connection request nobody answered **is**, so it must survive. That distinction is exactly what
the category taxonomy in §13 makes expressible — another reason it is the Stage B precondition.

**Do not build archival infrastructure.** 135 MB of 15 GB.

---

## 20. Safe rollout stages

### Stage A — Current-state audit ✅ *this document*
No behaviour change.

### Stage B — Foundation hardening ⬅ **recommended next, and the only thing recommended now**
Blocking on nothing. Ships no new events.

1. **Taxonomy split** (§13) — producer-level `NotificationType`, derived `NotificationCategory`,
   `actionableTypes()` sourced from category. **No DDL.**
2. **Widen `dedupKey` to accept a String discriminator** (§17.1). No migration.
3. **Response carries `actionable`/`category`; frontend stops comparing to `"ANNOUNCEMENT"`** (§13.4).
4. **`idx_notifications_inbox`** (§9.1) — one-line migration, owner-run.
5. **Retention: expire non-actionable unread rows** (§19).

> **⚠️ Why 1–5 belong together and belong now: `notifications` has 0 rows today.** Items 1–3 are pure
> Java, item 4 is one index, item 5 is one predicate — and every one of them becomes a data migration
> with a backfill and a reconciliation the day the first real notification lands. **That is the entire
> argument, and it does not extend to anything else.**

**Stage B candidates — owner's call, NOT part of the recommendation:**

| # | Item | Why it is not recommended *now* |
|---|---|---|
| 6 | Move announcement fan-out to a bounded executor (§18) | R3 is real but measured at **~0.8 s of database time** at current scale (§12.4). This is ordinary work with **no closing window** — it will cost exactly the same in six months. Ship it when announcement volume or user count justifies it, or alongside the first producer that needs the executor anyway. |
| 7 | Metrics (§24) | Same: no closing window. Worth doing with the first producer, so the counters have something to count. |

> ⚠️ **These two are listed separately on purpose.** The brief scoped Stage A as *"current-state audit,
> no behavior changes"*, and promoting a live-path code change into the recommended next slice would be
> this document deciding a rollout the owner has not approved. Both are recorded as findings (R3, §24);
> neither is recommended as scope.

#### ⚠️ Stage B implementation notes — settled 2026-09-08, read before writing the prompt

Two scope calls were made when D1 was approved. Both are reversible, but neither should be re-decided
silently.

**(a) Ship it DDL-FREE. Items 1–3 + 5 only; item 4 (the index) is explicitly OUT.**
Items 1–3 need no DDL (`type` is already `VARCHAR(64)`, `EnumType.STRING`; `dedup_key` is already
`VARCHAR(255)`), and item 5 is a JPQL predicate change. Item 4 would be the *only* reason this release
carries a migration — and **`V142` is still UNRUN in production, queued behind `V141`**. Adding a third
migration to an unrun queue to fix a sort that §9.1 measured as immaterial at 0 rows is a bad trade.
**Let the index ride the next migration that exists for another reason.**

**(b) DO NOT add enum values that have no producer.**
The temptation is to add `REVIEW_SET_UPDATE`, `CONNECTION_REQUEST` and `NOTE_SHARED` now. **Don't** —
that recreates the exact defect this audit opens with (§1: a type with zero producers, whose only
tests hand-build a state no code path reaches). Stage B changes the **mechanism**, not the value list:

- `NotificationType` keeps exactly its two current values.
- A new `NotificationCategory` carries badge policy, and `actionableTypes()` derives from the category
  rather than from a boolean on the type.
- **`ACTION_REQUIRED` survives as an explicitly TRANSITIONAL placeholder**, retained *only* so badge
  behaviour stays exercised while it has no producer. **Stage D replaces it** with a real producer type
  (`REVIEW_SET_UPDATE`, category `LEARNING_SYSTEM`). ⚠️ **Say this in the enum's javadoc**, or a future
  session will read it as a permanent value and build on it.
- **Behaviour must be identical after items 1–3.** A test should assert `actionableTypes()` still
  resolves to exactly `{ACTION_REQUIRED}` — the derivation changes, the set does not.

**(c) One boolean, two derived sets.** Badge-eligible and retention-expirable are complements. Put a
single flag on the category and derive both, with a test asserting they partition the categories — two
hand-maintained lists is how they drift.

### Stage C — Deep-link polish
**Skipped / merged into D.** There are no existing product notifications to polish — the only producer
is announcements, whose CTA is admin-authored. Stage C as the brief describes it has an empty backlog.

### Stage D — First safe direct producer
**One producer only: Official Review Set update** (`REVIEW_SET_UPDATE`, ≤30 recipients, LEARNING_SYSTEM
category, deep-link `/collections/{adoptedCollectionId}`, dedup `REVIEW_SET_UPDATE:<adoptedCollectionId>`).

Chosen over the alternatives because it is the only candidate with a **real recipient relationship**
(557 adopted collections, DB-enforced one-per-learner-per-source), a **real deep-link target that
already exists**, and a **publication boundary already shipped** (v0.132.0) that defines exactly when
to fire. The others have denominators of 0, 1 and 3.

Fires **only** from `publishReviewSetUpdate` — never from raw source drift (§20 of the brief).

### Stage E — Email bridge
**Blocked on R1.** Precondition: a **global** daily budget covering all channels, not just
`INACTIVITY`; plus the `email_log` uniqueness read in §17.3. Until then, no new email producer.

### Stage F — Impact milestones
`future`. Needs sparse deterministic thresholds and the composite dedup key from §17.1.
`knowledge_impact_digest_reminders_enabled` = **0 users**, so there is no measured appetite.

### Stage G — Discovery digest
`future`, and gated on **D5** (§22) — an explicit follow/subscription relationship. Not a
profile-program inference.

### Stage H — Delayed learning re-engagement
**Not recommended.** `INACTIVITY` email already does exactly this, 1,639 sends in 30 days to 393
enrolled users, with a 3-day cooldown and an unsubscribe category. A second in-app channel saying the
same thing is the cross-channel duplication §14 warns about. **The audit the brief asked for has been
run, and it found the distinct role does not exist.**

---

## 21. Events explicitly rejected and deferred

Separated by **why**, because the three reasons carry different futures. Full detail per row is in
Appendix A.

### 21.1 Rejected outright — will not be built as direct notifications

| Event | Reason |
|---|---|
| Every note like | **3 likes lifetime** in production. Per-like notification is the brief's own §16/§42 anti-pattern and a social-engagement loop the product does not want. |
| Every note view / copy | High cardinality, no addressee value per event. Copies survive only as an aggregate milestone. |
| Every new public note in program | 1,591 public notes; program applicability is **not** a subscription (§36). |
| Every quiz completion / flashcard session / correct answer / weak concept / readiness change | §31. Dashboard owns continuation guidance (§10). |
| Every next-step recommendation | Would make the bell a second Dashboard — the exact confusion §1 of the brief exists to prevent. |
| Every Study Pack generation, every ordinary self-edit | Self-caused; the user is already looking at it. |
| **New Official Review Set content while the curator is editing** | The publication boundary (v0.132.0) exists precisely to forbid this. Only `publishReviewSetUpdate` fires. |
| **Delayed learning re-engagement** (in-app) | ⚠️ **Rejected on measured evidence, not taste.** The brief asked for an audit before building it; the audit found `INACTIVITY` email already fills the role — **1,639 sends in 30 days** to 393 enrolled users, 3-day cooldown, unsubscribable. A second channel saying the same thing is the cross-channel duplication §14 warns about. |
| **Auto-created announcements from product events** | §21 of the brief. Admin announcements are authored deliberately. |
| Email mirroring of the bell | §12. "In-app can inform, email must justify interruption." |

### 21.2 Deferred on denominator — correct design, no evidence of need

| Event | Denominator | Revisit when |
|---|---|---|
| Learning Connection request | **1 relationship** | connections reach double digits; also `[CHECKPOINT — due 2026-09-19]`, denominator ONE |
| Named note share | **0 shares** | the share feature sees real use |
| Impact milestone | `knowledge_impact_digest` opt-in = **0 users** | there is measured appetite for impact surfacing |
| Discovery digest | needs a follow relationship that does not exist | **D5** is answered |

### 21.3 Blocked on a feature that does not exist

| Event | Missing prerequisite | Verified |
|---|---|---|
| Progress-access request | the "Ask to view progress" workflow | `requestGrant` / `requestAccess` / `grantRequest` → **0 hits** across backend and frontend |
| Assignment created | Assignment feature | not in repo |
| Assignment completion | Assignment feature | not in repo |
| Quiz shared | ⚠️ **a share link has no addressee** — do not fabricate a recipient | belongs with Assignment |

### 21.4 Deferred on a blocking precondition

| Event | Blocked by |
|---|---|
| **Any new email producer** | **R1** — the daily cap is already exceeded (156–159 vs 100). A **global** budget must exist first. |
| Any new producer at all | **Stage B** — taxonomy and dedup-key split, while `notifications` has 0 rows (**D1**). |

---

## 22. Remaining genuine owner decisions

| # | Decision | Recommendation |
|---|---|---|
| **D1** | ~~Ship the Stage B taxonomy + dedup change now, while `notifications` has 0 rows?~~ | ✅ **SETTLED 2026-09-08 (owner): YES.** Recommendation accepted. It is Java today and a data migration forever after. **⚠️ Scope is Stage B items 1–3 + 5 only** — see §20 Stage B, and the scope note directly below. |
| **D2** | Is the Official Review Set update the right first producer? | **YES** — only candidate with a real recipient relationship, an existing deep-link target and a shipped publication boundary. |
| **D3** | Should any Stage D producer also send email? | **NO, not yet.** R1 first. In-app only; measure, then decide. |
| **D4** | What does `marketing_emails_enabled` govern? | **Owner's call.** Recommend documenting the status quo — per-type flags are the real preference surface, the marketing flag governs promotional campaigns — rather than reclassifying, which would silence 393 of 396 users. |
| **D5** | Does Course/Program imply subscription for Discovery? | **NO** (§36). If Discovery ever ships it needs an explicit follow relationship. Nothing to build now. |
| **D6** | Announcement fan-out: discard or caller-runs on queue overflow? | **Discard-with-log.** Re-pressing Publish is already the documented, working recovery. |
| **D7** | Retention window for non-actionable unread rows? | **90 days**, matching the existing constant. No second knob. |

---

## 23. Anti-drift checklist

Carry these verbatim into any implementing prompt.

**Must NOT be done:**
- ❌ Build Kafka, a broker, event sourcing, an outbox framework, SSE or WebSocket.
- ❌ Add `@EnableAsync`; the repo uses explicit executors.
- ❌ Call `NotificationService.deliver()` from inside a transaction (**R9** — it breaks the dedup catch).
- ❌ Size a fan-out executor above **core 1 / max 2** (**R5/R13** — two pool-exhaustion outages).
- ❌ Raise `maximum-pool-size` — gated on `[CHECKPOINT — due 2026-10-04]`.
- ❌ Resolve live target state per inbox row (**R10** — the current zero-N+1 property is by construction).
- ❌ Add an `exists` check before a notification insert; the unique index is the mechanism.
- ❌ Notify on raw Review Set source drift — **only** `publishReviewSetUpdate` fires.
- ❌ Notify on a note becoming PUBLIC, on a public link, or on an anonymous view. **Explicit
  `note_shares` rows only.**
- ❌ One notification per like / view / copy / new public note.
- ❌ Treat `course_program` as a subscription (**D5**).
- ❌ Duplicate Dashboard next-step guidance into the bell.
- ❌ Let `AnnouncementAudienceResolver` touch `FeatureGateService` — editorial, never authorization.
- ❌ Send email for any new event before the **global** budget exists (**R1**).
- ❌ Weaken unsubscribe, or bypass a per-type preference flag.
- ❌ Make notification read-state authoritative for any workflow (§8 of the brief).
- ❌ Auto-create an Announcement from a product event.
- ❌ Expose adopter / copier / liker identities.
- ❌ Poll more often than 60 s, or poll the inbox rather than the count.
- ❌ Render a literal `0` badge.

**Must be preserved:**
- ✅ `read_at` = awareness, `dismissed_at` = inbox visibility, **no `resolved` column**.
- ✅ Announcement content copied at fan-out, never re-read.
- ✅ Published announcements immutable; correct by ending and republishing.
- ✅ CTA paths same-origin relative, validated at all three chokepoints.
- ✅ Opening the panel marks nothing read.
- ✅ `findVisibleInbox` and `countActionableUnread` predicates change **together**.
- ✅ Announcement lifecycle decided on read, not by a sweep.

---

## 24. Observability (brief §29)

*Not one of §37's 23 required sections, but §29 asks for it explicitly.*

Reuse existing logging; add counters only where a bad rollout would otherwise be invisible.

| Signal | Mechanism | Exists? |
|---|---|---|
| Notification rows created/day | counter by type | ❌ add |
| Fan-out duration | timer around `fanOut` | ❌ add |
| Fan-out failures / skipped | already logged per recipient + totals | ✅ keep |
| Duplicate-key conflicts | count `DataIntegrityViolationException` in `deliver` | ❌ add — **this is the idempotency guard's own health signal** |
| Cleanup deletions | `notification.cleanup deleted N` | ✅ |
| Inbox / unread-count latency | Actuator HTTP metrics | ✅ available |
| Notification table growth | one `pg_total_relation_size` read at signoff | manual, fine |
| Email sends/failures | `email_log` rows + provider errors | ✅ partial |
| Scheduled digest runtime | job log lines | ✅ |

**No per-user admin surveillance surface.** No "who read what" reporting.

---

## Appendix A — Full event & channel matrix (brief §38)

Every row is marked against **measured production data**, not intent. `Status` uses the brief's own
vocabulary: `ship` / `blocked` / `future` / `not recommended`.

| Event | Source of truth | Recipient known? | Cardinality | In-app? | Numeric badge? | Email? | Aggregate? | Deep link | Dedup key | Delivery mode | Status |
|---|---|---|---|---|---|---|---|---|---|---|---|
| **Learning Connection request** | `linked_learner_relationships` (PENDING) | ✅ yes — invitee | **Low** (1/event; **1 row in prod**) | ✅ yes | ✅ yes | ❌ not yet (D3) | ❌ no | `/linked-learners` | `CONNECTION_REQUEST:<relationshipId>` | sync after commit | **future** — denominator 1; correct design, no evidence of need |
| **Progress-access request** | — | n/a | n/a | — | — | — | — | — | — | — | **blocked** — feature does not exist (`requestGrant`/`requestAccess`/`grantRequest` → 0 hits) |
| **Named Note share** | `note_shares` (`grantee_user_id`, `relationship_id`) | ✅ yes — grantee | **Low** (1/event; **0 rows in prod**) | ✅ yes | ✅ yes | ❌ not yet (D3) | ❌ no | `/shared/notes/{noteId}` | `NOTE_SHARED:<noteShareId>` | sync after commit | **future** — entity exists, feature unused |
| **Official Review Set update** | `publishReviewSetUpdate` → `last_update_published_at` | ✅ yes — root adopters | **Low–Medium** (max **30**; 54 total across 4 sets) | ✅ yes | ✅ yes | ❌ not in first slice | ❌ no — one per adopter | `/collections/{adoptedCollectionId}` | `REVIEW_SET_UPDATE:<adoptedCollectionId>` | **background executor** (1/2) | **ship** — Stage D, the recommended first producer |
| **Admin Announcement** | `announcements` (PUBLISHED) | ✅ yes — resolved audience | **Medium** (≤**396**) | ✅ yes | ❌ **no** | ❌ never | ❌ no | admin-authored `cta_path` | `ANNOUNCEMENT:<announcementId>` | ⚠️ **synchronous today** → move to background (Stage B) | **ship (hardening only)** — already live, R3 |
| **Note like** | `public_note_likes` | ✅ yes — note owner | **High** per-event (**3 likes lifetime**) | ❌ no | ❌ no | ❌ never | ✅ milestone only | — | — | — | **not recommended** — §16/§42 anti-pattern, and no product signal |
| **Note copy** | analytics / `notes.source_note_id` | ✅ yes — note owner | **High** per-event | ❌ no (per event) | ❌ no | ❌ never | ✅ milestone only | `/progress` | *(rolls into impact milestone)* | scheduled | **not recommended** as a per-event notification |
| **Note impact milestone** | `CreatorImpactService` (computed, **no stored state**) | ✅ yes — creator | **Low** if thresholds are sparse | ✅ yes | ❌ **no** | ❌ never | ✅ yes — sparse deterministic thresholds | `/progress` | `IMPACT_MILESTONE:<noteId>:<threshold>` ⚠️ needs §17.1 composite key | scheduled job | **future** — Stage F; `knowledge_impact_digest` opt-in = **0 users** |
| **New public Note in program** | `notes.visibility=PUBLIC` + `note_course_program` | ❌ **no** — program ≠ subscription | **High** (**1,591** public notes) | ❌ not per note | ❌ no | ❌ never by default | ✅ **digest only** | `/public/library?courseProgram=…&sort=recent` | `DISCOVERY_DIGEST:<userId>:<isoWeek>` | scheduled digest | **future**, gated on **D5** — needs an explicit follow relationship (§36) |
| **New Official Review Set content** (curator editing, pre-publish) | unpublished source rows | ✅ yes | — | ❌ **never** | ❌ no | ❌ no | — | — | — | — | **not recommended** — publication boundary forbids it (v0.132.0, brief §20) |
| **Immediate next-step guidance** | Dashboard / `guidance-engine.ts` | ✅ yes — self | High | ❌ **no** | ❌ no | ❌ no | — | Dashboard | — | — | **not recommended** — Dashboard owns this (§10) |
| **Delayed learning re-engagement** | inactivity window | ✅ yes — self | Medium (≤393) | ❌ no | ❌ no | ✅ **already exists** | ✅ already cooled down | `/dashboard` | `email_log` + 3-day cooldown | daily scheduled job | **not recommended (duplicate)** — `INACTIVITY` email already does exactly this: **1,639 sends/30d**, 393 enrolled, unsubscribable. Stage H's audit is complete and negative. |
| **Future Assignment** | — | ✅ would be (assignee) | Low | ✅ yes | ✅ yes | probably | ❌ no | assignment surface | `ASSIGNMENT:<assignmentId>` | sync after commit | **blocked** — feature does not exist |
| **Assignment completion** | — | ✅ would be (assigner) | Low–Medium | ✅ yes | ❌ no | ❌ no | ✅ aggregate per assignment | assignment surface | `ASSIGNMENT_COMPLETE:<assignmentId>:<assigneeId>` | background | **blocked** — feature does not exist |
| **Routine quiz completion** | `quick_review_sessions` | ✅ yes — self | **High** | ❌ **never** | ❌ no | ❌ never | — | — | — | — | **not recommended** — §31 |
| **Billing / security / account (existing)** | `subscriptions`, auth flows | ✅ yes | Low | ➖ unchanged | ➖ | ✅ **already exists, unchanged** | ❌ no | absolute URLs in email | none (`email_log` + cooldown) | scheduled / sync | **ship (no change)** — verification, password reset, welcome, 3× subscription expiry |

### A.1 What the matrix shows at a glance

- **Exactly one row is `ship` as a new producer**: Official Review Set update.
- **Four are `blocked` on a feature that does not exist** — progress-access request, both assignment
  rows, and (in effect) note share at 0 rows.
- **Six are `not recommended`**, five of them because they are per-event notifications for
  high-cardinality activity, and one (delayed re-engagement) because **an equivalent channel already
  ships 1,639 emails a month**.
- **Nothing is recommended for email**, because R1 must be fixed first.

---

## Verification the first producer owes

Per `CLAUDE.md` and the brief's §41 — these are the **discriminating** tests, not a coverage list:

1. **Dedup** — fire the same publish twice → exactly one row per adopter.
2. **Fan-out retry** — fail halfway, retry → every eligible recipient has **at most one** row.
3. **Badge isolation** — 1 announcement + 1 impact + 0 actionable → badge **0**; add 1 actionable →
   badge **1**.
4. **Read ≠ resolved** — open the update notification → `read_at` set, adopted collection **still
   behind**, nothing applied.
5. **Stale target** — delete/revoke the target before opening → graceful state, **no permission leak**.
6. **Editing ≠ publishing** — add many notes to an Official set → **zero** adopter notifications;
   Publish once → **one** row per eligible adopter.
7. **⚠️ Real request** — `MockMvc` with `.contentType(MediaType.APPLICATION_JSON)` and a body for any
   new endpoint. A direct handler call **passes under a binding defect by construction** (v0.119.0).
8. **⚠️ End-to-end from the product action** — the test must start at "curator presses Publish", not at
   `notificationService.deliver(...)`. **Every existing `ACTION_REQUIRED` test hand-builds a state no
   code path produces** (§6.1); repeating that pattern would prove nothing.
9. **Polling** — the unread poll does not fetch the inbox.
10. **Transaction safety** — mutation-verify R9: wrap `deliver()` in a transaction and assert the
    fan-out still completes, or assert the producer is not transactional.

---

## Housekeeping

**⚠️ This file creates a Backlog Index obligation.** `CLAUDE.md` kickoff step 8 requires every
`docs/claude-plans/` file to carry a row in `ROADMAP.md`'s Backlog Index. **Add a row for
`attention-notifications-email-expansion-stage1.md` at the next kickoff** so the scan does not surface
it as unindexed drift — this is a planning document, not a finished release artifact, so the narrow
release-artifact exemption does not cover it.

**Superseded:** this document supersedes §1 and §4 of
`docs/claude-plans/in-app-notifications-and-review-set-adoption-signals-stage1.md`, which describe a
pre-delivery repo and are now false.
