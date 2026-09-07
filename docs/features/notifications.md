# Notifications

In-app notification substrate (Stage 3) and its first producer, Admin "What's New" (Stage 4), both
shipped in `v0.130.0` from
`docs/claude-plans/in-app-notifications-and-review-set-adoption-signals-stage1.md`.

## The model: notification = awareness, the owning feature = truth

A notification row stores **recipient, type, dedup key, deep link, copied display text and
timestamps**. It never stores request/grant/update state, and reading or dismissing it changes
nothing outside the inbox.

**⚠️ Do NOT add an authoritative `resolved` column.** Whether a request was accepted, an update
applied, or a share revoked is answered by the owning feature, never by the notification.

**⚠️ Dismissal must never** revoke a connection, decline a request, apply an update, unshare a note,
or alter any product state. It hides the row from the inbox and nothing more.

## Idempotency is the unique index, not a service check

`notifications` carries a **UNIQUE index on `(recipient_user_id, dedup_key)`** (`V139`). That index
**is** the delivery guarantee.

`dedup_key` is deterministic and built by one helper — `NotificationService.dedupKey(type, entityId)`
— producing **`"<TYPE>:<entity-id>"`**, e.g. `ANNOUNCEMENT:9f3c…`.

**⚠️ Do NOT add an `existsBy…` check before the insert.** Two concurrent deliveries can both pass such
a check and still create duplicate awareness. `NotificationService.deliver` instead attempts the
insert and **catches `DataIntegrityViolationException`**, returning the existing row — the same shape
`NoteCollectionService:952` uses for the adoption unique index. **A duplicate delivery is a
successful no-op, never an error surfaced to the caller.**

## Actionable vs announcement, and the badge

`NotificationType` carries an `actionable` flag, and `actionableTypes()` derives the set from it.

- **Actionable unread → the numeric badge.**
- **Announcements → appear in the inbox but NEVER contribute to the number.**

**⚠️ The split is a property of the TYPE, not an `if` at each call site.** An announcement nobody acts
on would otherwise leave a permanent count on the bell, which trains people to ignore it and degrades
the actionable half the badge exists for.

**⚠️ Never render a literal `0`** — at zero there is no badge element at all, not an empty circle.

## Read, dismiss, and what the panel must not do

`read_at` is awareness; `dismissed_at` is inbox visibility. Both are idempotent — setting an already
-set timestamp is a no-op.

**⚠️ Rows are marked read INDIVIDUALLY, on open or on CTA click. The panel must NEVER mark-all-read on
open** — that silently discards the one signal a learner needs for a pending request.

## Scoping

Every read and mutation is scoped to the authenticated recipient via
`findByIdAndRecipientUserId`. **⚠️ A cross-user attempt returns NOT-FOUND, never forbidden**, so the
endpoint does not confirm that someone else's notification exists.

## Deep links must tolerate a missing target

**⚠️ A notification outlives the thing it points at, by design.** Every `cta_path` destination must
degrade to a plain "no longer available" state when the target has been accepted, revoked, unshared,
applied or deleted — never a raw 404 and never a crash.

## Polling, not streaming

There is no WebSocket or SSE infrastructure in this repo. The bell polls
`GET /notifications/unread-count` from `app-shell.tsx` on a 60-second interval, modelled on
`lib/study-pack-generation.ts`.

Three properties are deliberate:

- **Polling stops while the tab is hidden**, and refreshes on `visibilitychange`.
- **A failed poll keeps the last known count and stays silent.** Clearing the badge would tell a
  learner they have nothing when they may have a pending request; a toast per failed poll is noise.
- **⚠️ `getNotificationUnreadCount` is called with retry AND unauthorized-handling disabled**, so a
  401 from a background poll can never sign a learner out of an otherwise valid session. This is why
  `fetchWithAuth` gained a `handleUnauthorized` parameter — it defaults to `true`, so every other
  caller is unchanged.

## Announcements — the Admin "What's New" producer

`announcements` (`V140`) carries a **`DRAFT` → `PUBLISHED` → `ENDED`** lifecycle, and fans out into the
same `notifications` table. **⚠️ ONE INBOX, ONE TABLE, ONE READ** — there is no second delivery path
and no lazy eligibility evaluation.

### Published content is immutable, and an edit is REFUSED

Drafts are freely editable. From `PUBLISHED` onwards an update attempt throws
`AnnouncementNotEditableException` (409) whose `action` names the remedy: **end this announcement and
publish a replacement.**

**⚠️ Never a silent no-op.** Title, body and CTA are **COPIED onto each notification row at fan-out and
never re-read**, so a tolerated edit would leave the announcement definition and the copies people
already saw disagreeing, with nothing in the product saying which is real.

**⚠️ `announcement_id` on a notification row is PROVENANCE AND LIFECYCLE ASSOCIATION — not live content
inheritance.**

### Idempotency, and what "retry" means here

`dedup_key` is `"ANNOUNCEMENT:<announcementId>"`, built by the same
`NotificationService.dedupKey(type, entityId)` helper — the announcement id is passed as **both** the
delivery's `entityId` and its `announcementId`. **⚠️ There is NO service-side `existsBy` pre-check**;
the unique index is the guarantee.

**Publish on an already-`PUBLISHED` announcement RE-RUNS the fan-out rather than being refused**, and
that is deliberate. Fan-out is one committed insert per recipient with no ambient transaction, so a
few thousand recipients is a few thousand round trips inside one admin HTTP request — long enough to
outrun a gateway timeout. The status transition commits *before* fan-out starts and the index makes a
re-run insert zero duplicates, so a timed-out publish is recoverable by pressing Publish again.
**⚠️ `published_at` is stamped once and never re-stamped.** **⚠️ `ENDED` → publish is refused.**

**⚠️ `AnnouncementService.publish` and `fanOut` are deliberately NOT `@Transactional`.** `deliver`
depends on catching `DataIntegrityViolationException`; under an ambient transaction that violation
marks the whole transaction rollback-only and takes the entire fan-out down with it.

**⚠️ Partial failure never rolls back deliveries already made** — a user who saw it cannot un-see it.
One recipient's failed insert is counted, logged and stepped over; the response reports
`recipientCount` / `delivered` / `skipped`, where a duplicate counts as *delivered* (it is already in
that inbox) and only a genuine failure counts as *skipped*.

### Ending and expiry are decided ON READ

**⚠️ An ended or expired announcement stops reading as new IMMEDIATELY, before any cleanup job runs.**
`NotificationRepository.findVisibleInbox` excludes rows whose announcement is `ENDED` or past
`expires_at`, as a correlated subquery inside the single inbox query — so no `@Scheduled` job is
involved and the query count stays independent of row count.

**⚠️ The predicate is NOT-EXISTS-ended, not EXISTS-live.** A notification outlives the thing it points
at by design and its copy is self-contained, so a missing announcement row leaves the delivered row
**visible**; an EXISTS-live form would let a vanished announcement silently eat inboxes.

The delivered row is hidden, never deleted — retention, not the lifecycle, decides when it goes.

### Audience is EDITORIAL, never authorization

Three audiences, no query builder: `EVERYONE`, `PROFILE_TYPE`, `PLAN_TYPE`.

**⚠️ Targeting selects who is likely to CARE. It must never become feature gating or entitlement** —
that stays with `FeatureGateService` and each feature's own rules. `AnnouncementAudienceResolver` holds
no reference to `FeatureGateService`, and a test asserts it.

- `EVERYONE` and `PROFILE_TYPE` read `users` where `status = ACTIVE`. **⚠️ Deliberately NOT filtered on
  `emailVerifiedAt`** — delivery is in-app, and an unverified account can read its own inbox. The
  verification filter on `ReEngagementCampaignService`'s audience exists because that path sends EMAIL.
  Do not "align" the two.
- `PLAN_TYPE` **mirrors `SubscriptionService.resolvePlan`, precedence included**: a user with an active
  PLUS *and* an active PRO subscription resolves to PRO there, so they are in the PRO audience and NOT
  the PLUS one, and FREE is the complement (no paid subscription inside its active window). It uses its
  own repository query rather than `findActiveUserIdsByPlanTypeInAndStatus`, which omits the `startAt`
  leg.
- **An audience resolving to zero users publishes successfully and delivers nothing.** That is a
  legitimate outcome, not an error.

### CTA validation is a security control

**⚠️ An admin-authored link rendered in every user's inbox is a phishing surface if it can point
anywhere.** `cta_path` must be a **same-origin relative path**: `^/[A-Za-z0-9\-._~/]*$` plus an
optional query string, rejecting anything with a scheme, a host, or a protocol-relative `//` prefix.

**⚠️ A RULE, NOT AN ALLOW-LIST OF ROUTES** — an allow-list needs an application release for every new
legitimate destination, which is the pressure that gets a security check deleted.

**⚠️ Validated on WRITE and re-checked on RENDER.** `AnnouncementCtaPathValidator` (backend) and
`lib/safe-relative-path.ts` (frontend) implement the same rule; a stored value is still untrusted by
the time it reaches an `href`, because `next/link` renders an absolute URL as a live external anchor.
If one side changes, change the other.

### What is deliberately not built

**No scheduled publishing** (Draft → Published is manual), **no announcement funnels, CTR dashboards,
attribution or campaign analytics**, and **no external CTA URLs**. **⚠️ `ReEngagementCampaignService`
was NOT extended** — it is 161 lines with a hardcoded audience, a fixed template and email-only
delivery, with no campaign entity to build on. Do not refactor, merge or delete it.

## Retention

A `@Scheduled` job modelled on `BulkGenerationResultCleanupJob` deletes **read or dismissed**
notifications older than a config-backed window (default 90 days).

**⚠️ Unread actionable notifications are RETAINED regardless of age** — an unread row is the learner's
only pointer to a pending request.

**⚠️ Notification is not workflow history.** Deleting a row must never touch the invitation, share,
grant or collection it referred to. There is no archival infrastructure — one delete query on a
schedule.

## What deliberately does not exist yet

`v0.130.0` shipped Stage 3 (the substrate) and Stage 4 (Admin "What's New", the first producer). The
inbox is no longer empty by construction — but **Admin "What's New" is still its ONLY producer**, so a
learner who has never been sent an announcement still sees an empty inbox.

**Not built, and not to be added without its own release:**

- **Stage 5 events** — connection request, named note share. **⚠️ Deferred on CONTAMINATION, not
  effort: the connection-request notification nudges a PENDING invitation toward ACCEPTED, which is
  the kill criterion of `[CHECKPOINT — due 2026-09-19]`, whose denominator is ONE.**
- **Stage 6** — Review Set update notifications, blocked on the §8 drift-signature dedup decision.
- **Stage 7** — assignments, push/email/SMS, quiet hours, a preferences centre.
- **Quiz-share notifications** — a share link has no addressee. **⚠️ Do not fabricate a recipient.**
- A notification for a note becoming PUBLIC, or for a generic public link — **named shares only**.
