# Notifications

In-app notification substrate, shipped in `v0.130.0` as Stage 3 of
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

## Retention

A `@Scheduled` job modelled on `BulkGenerationResultCleanupJob` deletes **read or dismissed**
notifications older than a config-backed window (default 90 days).

**⚠️ Unread actionable notifications are RETAINED regardless of age** — an unread row is the learner's
only pointer to a pending request.

**⚠️ Notification is not workflow history.** Deleting a row must never touch the invitation, share,
grant or collection it referred to. There is no archival infrastructure — one delete query on a
schedule.

## What deliberately does not exist yet

`v0.130.0` Stage 3 is the substrate. **Stage 4 (Admin What's New) is the first producer**; until it
lands the inbox is empty by construction.

**Not built, and not to be added without its own release:**

- **Stage 5 events** — connection request, named note share. **⚠️ Deferred on CONTAMINATION, not
  effort: the connection-request notification nudges a PENDING invitation toward ACCEPTED, which is
  the kill criterion of `[CHECKPOINT — due 2026-09-19]`, whose denominator is ONE.**
- **Stage 6** — Review Set update notifications, blocked on the §8 drift-signature dedup decision.
- **Stage 7** — assignments, push/email/SMS, quiet hours, a preferences centre.
- **Quiz-share notifications** — a share link has no addressee. **⚠️ Do not fabricate a recipient.**
- A notification for a note becoming PUBLIC, or for a generic public link — **named shares only**.
