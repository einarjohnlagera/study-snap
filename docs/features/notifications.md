# Notifications

The in-app notification substrate and Admin "What's New" shipped in `v0.130.0`. `v0.135.0` added its
first feature-owned producer: published Official Review Set updates for learners who adopted the set.

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

`dedup_key` is deterministic and built by one helper — `NotificationService.dedupKey(type, discriminator)`
— producing **`"<TYPE>:<discriminator>"`**, e.g. `ANNOUNCEMENT:9f3c…`.

**⚠️ The discriminator is a `String`, widened from `UUID` in `v0.134.0`** so a producer whose event
identity is not a bare entity id can express it. **The format did not change** — announcements still
key on `announcement.getId().toString()` and produce byte-identical keys. **⚠️ The widening was free
only because no key had ever been written: `notifications` was empty in production. It is not a format
any future producer may assume it can change cheaply.**

**⚠️ Do NOT add an `existsBy…` check before the insert.** Two concurrent deliveries can both pass such
a check and still create duplicate awareness. `NotificationService.deliver` instead attempts the
insert and **catches `DataIntegrityViolationException`**, returning the existing row — the same shape
`NoteCollectionService:952` uses for the adoption unique index. **A duplicate delivery is a
successful no-op, never an error surfaced to the caller.**

## Categories, types, and the badge

**`NotificationCategory` carries the badge policy; `NotificationType` is producer identity and
delegates to its category** (`v0.134.0`). One `badgeEligible` flag on the category yields two derived,
complementary sets — `badgeEligibleCategories()` and `retentionExpirableCategories()` — and
`actionableTypes()` derives from the first. **⚠️ A test asserts the two sets PARTITION the categories:
two hand-maintained lists is how they drift.**

The shipped mappings are exactly:

- `ANNOUNCEMENT` → `ANNOUNCEMENT(false)` — visible in the inbox, never badge-eligible.
- `REVIEW_SET_UPDATE` → `LEARNING_SYSTEM(true)` — visible in the inbox and badge-eligible while unread.

**`ACTION_REQUIRED` is gone.** It was a transitional producerless placeholder and was removed in
`v0.135.0` while production still had zero notification rows, rather than preserving a category with no
type or producer.

- **Actionable unread → the numeric badge.**
- **Announcements → appear in the inbox but NEVER contribute to the number.**

**⚠️ The split is a property of the TYPE, not an `if` at each call site.** An announcement nobody acts
on would otherwise leave a permanent count on the bell, which trains people to ignore it and degrades
the actionable half the badge exists for.

**⚠️ Never render a literal `0`** — at zero there is no badge element at all, not an empty circle.

**⚠️ THE BADGE QUERY AND THE INBOX QUERY MUST CARRY THE SAME VISIBILITY PREDICATE.** `countActionableUnread`
shipped in `v0.130.0` filtering on `read_at` alone while `findVisibleInbox` also filters `dismissed_at`,
so an actionable row dismissed without being read left the inbox and kept incrementing the bell — a
number the learner could neither open nor clear. Fixed in the same release; both queries now filter
`dismissed_at` and both apply the announcement-lifecycle subquery. **A change to either is a change to
both**, and the lifecycle leg on the badge is mirroring rather than live (announcements are
non-actionable, so no row has both today) — it is there so the two cannot drift.

## Official Review Set update producer

Only `NoteCollectionService.publishReviewSetUpdate` triggers this producer, and only when the locked
Official source root actually has unpublished changes. Editing source curriculum emits nothing, a
no-change re-press emits nothing, and first publication through `publishInitialCurriculum` emits
nothing even though that path also initializes `last_update_published_at`.

The transactional publish path emits a plain-value event containing the source collection id and the
persisted publication stamp. A `TransactionalEventListener(AFTER_COMMIT, fallbackExecution = true)`
then submits fan-out to the existing bounded `notificationFanOutExecutor`. Recipient resolution,
suppression and every call to `NotificationService.deliver` therefore happen outside the publication
transaction. A queue rejection is logged and cannot roll back or report the curriculum publication as
failed; one recipient failure is logged and skipped while the loop continues.

Adopters are resolved in one query from `note_collections.source_plan_id`. The adoption row supplies
both `owner_user_id` and the learner-owned collection id, so each notification links to
`/collections/{adoptedCollectionId}` rather than the curator's source.

The dedup key is
`REVIEW_SET_UPDATE:<sourceCollectionId>:<lastUpdatePublishedAtEpochMilli>`. The service reads the
persisted `TIMESTAMPTZ` value before publishing the event, then computes the shared fan-out key once;
this avoids nanosecond/microsecond precision drift. The permanent recipient/dedup unique index remains
the sole identity and retry mechanism.

Before fan-out, one batch query suppresses recipients who already hold an undismissed Review Set update
row whose key starts `REVIEW_SET_UPDATE:<sourceCollectionId>:`. This compares different published
revisions rather than pre-checking delivery identity. Its worst race produces one extra signal for a
real newer revision, while the unique index still prevents duplicates for the same revision. Reading
does not close the episode; dismissal does, so a learner who dismisses without applying is notified
again after the next real publication. The copied title and body are fixed and carry no change count.

**⚠️ A CURATOR WHO ADOPTED THEIR OWN SET IS NOT A RECIPIENT.** `adopt()` carries no owner guard, so a
curator can self-adopt their own PUBLIC Review Set; without an exclusion they would be told about a
publish they just performed. `findReviewSetUpdateRecipients` excludes self-copies with
`adoption.ownerUserId <> source.ownerUserId` — **the same predicate `countAdoptionsByCollectionIds`
uses, deliberately, so the two queries agree on what an adoption is.** If they ever diverge, the
adoption count and the notification audience disagree about the same relationship.

## Opening, closing, and the bell as a toggle

The bell is a **toggle**, not a re-open. Clicking it while the panel is open **closes it and does NOT
refetch**; clicking it while closed **opens it and loads**. **⚠️ Do NOT drop the load-on-open** — only
the re-click while open skips the fetch. Shipped in `v0.131.0`; before that `openInbox` set open and
called `loadInbox()` unconditionally, so clicking an open inbox re-opened and refetched it.

The desktop panel closes on **outside click** and on **Escape**, matching every sibling dropdown (the
avatar menu, the theme toggle, the export menu). There is deliberately **no `Close` button** — it
existed only because closing was otherwise impossible.

**⚠️ Activating a notification's CTA also closes the panel (both the desktop dropdown and the
mobile `AppModal` sheet, since both render the same `rows` block) — fixed in `v0.142.0` item A5.**
Before the fix, the CTA `<Link>` called `markRead` and never `setIsOpen(false)`, so the panel stayed
open over the destination page. This fires on the close path a learner actually takes, not an edge
case: at fix time, 42 of 42 production notifications carried a CTA. **Both paths are covered by a
test asserting the panel is gone after the CTA click** — every other test in this file mocks
`matchMedia` to `matches: false`, so a dedicated mobile-branch test (`matches: true`) was added
rather than assuming the shared `rows` render made the desktop test sufficient for the sheet too.

**⚠️ THE OUTSIDE-CLICK REF WRAPS THE BELL AND THE PANEL TOGETHER, AND THAT IS LOAD-BEARING.** If it
wrapped only the panel, the bell would count as "outside": `mousedown` would close the panel and the
bell's own `click` would immediately reopen **and refetch** it, so one click would flicker instead of
closing. A test that clicks the page *body* passes while the bell double-fires — the guard has to
dispatch `mousedown` **and** `click` on the bell itself, and assert the **request count** rather than
what is on screen.

**⚠️ MOBILE IS UNCHANGED AND MUST STAY THAT WAY.** That path renders `AppModal`, which already handles
its own backdrop and Escape; the desktop handler is gated on `!isMobile` so the two cannot fight.

**Trigger accessibility:** the `aria-label` stays **stable** and `aria-expanded` carries the state,
which is the convention already used by `theme-toggle.tsx` and `export-dropdown-menu.tsx`. A label that
swaps with state *alongside* `aria-expanded` announces the same fact twice.

## The bell is hidden while a quiz is running

`useExamFocusMode(active)` hides the whole app-shell header — and with it the bell — plus the mobile tab
bar. As of `v0.131.0` it is active on **Challenge Quiz (every mode, not just Board Exam), Quick Review,
Adaptive Practice, Interview Practice**, alongside Long Exam.

**⚠️ EVERY FOCUSED SURFACE MUST KEEP AN IN-PAGE EXIT, AND THE HOOK'S GATE MUST MATCH THE EXIT'S GATE.**
Hiding the chrome removes the normal way out, so focus mode may never be active in a state the exit does
not cover. Each of these pages renders a *Leave Quiz* / *Leave Practice* control on the same expression
passed to the hook, and a test asserts both together, **per surface**.

**⚠️ A `BackLink` elsewhere in the file does NOT satisfy this.** The `v0.131.0` kickoff wrongly concluded
Quick Review had no exit, because the audit grepped for `BackLink` and links and missed the
`onClick={() => requestLeave()}` button that is the real running-state exit in this repo.

**⚠️ DO NOT ADD `useExamFocusMode` TO THE SHARED QUIZ (`/quiz/[token]`) — IT WOULD BE A SILENT NO-OP.**
`app-shell.tsx:593` returns early for that route with a bare `<main>`, so it renders no header and the
bell is already absent. Focus mode's only consumer is the header gate.

**⚠️ A sticky in-page bar on a focused surface belongs at `top-0`, not `top-16`.** The 4rem offset exists
to clear the app-shell header; with the header hidden it leaves the bar floating with nothing above it.

**⚠️ The unread poll keeps running during focus mode**, deliberately — stopping it would leave the badge
stale the moment the learner exits.

## Read, dismiss, and what the panel must not do

`read_at` is awareness; `dismissed_at` is inbox visibility. Both are idempotent — setting an already
-set timestamp is a no-op.

**⚠️ Rows are marked read INDIVIDUALLY, on open or on CTA click. The panel must NEVER mark-all-read on
open** — that silently discards the one signal a learner needs for a pending request.

### ⚠️ The badge decrement reads a SERVER-PROVIDED field, never the type string

`NotificationResponse` carries **`actionable`** (`v0.134.0`), derived server-side from the type's
category, and `notification-inbox.tsx` branches its optimistic decrement and its rollback on that
field. **It previously compared `notification.type !== "ANNOUNCEMENT"`** — a private client-side mirror
of a server policy, which would have silently mis-counted the badge the moment a third non-actionable
type existed.

**⚠️ The response deliberately does NOT carry `category`.** The client needs exactly one field to branch
on; exposing the category would invite it to re-derive policy from a category string — the same defect
one level up. **⚠️ A fixture whose `actionable` merely agrees with its `type` cannot tell the two
implementations apart** — the guard that pins this is a fixture where they DISAGREE (a non-actionable
type that is not `"ANNOUNCEMENT"`).

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
- **⚠️ `getNotificationUnreadCount` is called with `retry=true` and `handleUnauthorized=false`, and the
  two halves are separate decisions.** `handleUnauthorized=false` is why `fetchWithAuth` gained the
  parameter — it defaults to `true`, so every other caller is unchanged — and it means a 401 from a
  background poll can never sign a learner out of an otherwise valid session.
  **⚠️ Disabling the RETRY as well was a defect, shipped and then fixed inside `v0.130.0`.** Access
  tokens live 15 minutes and this polls every 60 seconds, so with no refresh every poll after the first
  idle quarter-hour 401'd and the badge froze on its last value for the rest of the session. The
  "refresh storm" rationale was never real: `tryRefreshAccessToken` already dedupes concurrent
  refreshes. `trackAnalyticsEvent` is the precedent — same pairing, same reason.

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
`NotificationService.dedupKey(type, discriminator)` helper — `announcement.getId().toString()` is passed
as the delivery's `dedupDiscriminator`, and the same id again as its `announcementId`. **⚠️ There is NO service-side `existsBy` pre-check**;
the unique index is the guarantee.

**Publish on an already-`PUBLISHED` announcement RE-RUNS the fan-out rather than being refused**, and
that is deliberate. **⚠️ But it is a RETRY ONLY FOR PEOPLE WHO ALREADY HAVE IT — for anyone who has
joined the audience since the first publish it is a FIRST SEND.** `publish` resolves the audience
synchronously at call time, then hands that recipient list to the bounded `notificationFanOutExecutor`.
A user who signed up, changed profile type or upgraded plan between publishes is therefore included in
the second resolution; existing recipients are deduped by the unique index and get nothing new. The net
effect is a **top-up**.

Fan-out now runs in the background at core 1 / max 2, sized against the JDBC connection pool. The admin
response is `announcement` / `recipientCount` / `queued`: `queued` equals the resolved count when the
executor accepts the task and is zero when dispatch is rejected. A rejection is logged and leaves the
announcement `PUBLISHED`; pressing Publish again is the recovery. **The response never claims delivery
has completed.**
**⚠️ `published_at` is stamped once and never re-stamped.** **⚠️ `ENDED` → publish is refused.**

**⚠️ `AnnouncementService.publish` and `fanOut` are deliberately NOT `@Transactional`.** `deliver`
depends on catching `DataIntegrityViolationException`; under an ambient transaction that violation
marks the whole transaction rollback-only and takes the entire fan-out down with it.

**⚠️ Partial failure never rolls back deliveries already made** — a user who saw it cannot un-see it.
One recipient's failed insert is counted, logged and stepped over inside the background fan-out. The
existing chunk, total and per-recipient failure logs remain the delivery detail after dispatch.

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

**⚠️ Validated on WRITE, again on DELIVER, and re-checked on RENDER.** The deliver-side check is normally
a no-op — announcement create/update already validated — and exists because `NotificationService.deliver`
originally took whatever `ctaPath` it was handed, which made the "one rule, one location" guarantee hold
only for as long as announcements stayed the sole producer. `AnnouncementCtaPathValidator` (backend) and
`lib/safe-relative-path.ts` (frontend) implement the same rule; a stored value is still untrusted by
the time it reaches an `href`, because `next/link` renders an absolute URL as a live external anchor.
If one side changes, change the other.

### What is deliberately not built

**No scheduled publishing** (Draft → Published is manual), **no announcement funnels, CTR dashboards,
attribution or campaign analytics**, and **no external CTA URLs**. **⚠️ `ReEngagementCampaignService`
was NOT extended** — it is 161 lines with a hardcoded audience, a fixed template and email-only
delivery, with no campaign entity to build on. Do not refactor, merge or delete it.

## Retention

A `@Scheduled` job modelled on `BulkGenerationResultCleanupJob` deletes, past a config-backed window
(default 90 days), any notification that is **read, dismissed, or of a retention-expirable type**.

**⚠️ Unread AND UNDISMISSED ACTIONABLE notifications are RETAINED regardless of age** — such a row is the
learner's only pointer to a pending request. **⚠️ The ACTIONABLE qualifier is load-bearing and was added
in `v0.134.0`.** Before it, the predicate was `read_at IS NOT NULL OR dismissed_at IS NOT NULL` alone,
which made **every** unread row immortal: a single `EVERYONE` announcement to 396 users left 396
permanent rows, forever, because most people never open the bell. The predicate now also expires unread
rows whose category is **not** badge-eligible — announcements today — while an unread **actionable** row
is still kept indefinitely. **⚠️ The two behaviours are complements derived from one flag, and the
retention test asserts BOTH directions**; asserting only the deletion half would pass with the retention
half broken.

**⚠️ ONE CONSEQUENCE OF THE ACTIONABLE/RETAINED SPLIT, NAMED BY THE `v0.135.0` COLD AGENT SO IT IS NOT
DISCOVERED AS A BUG LATER: a `REVIEW_SET_UPDATE` that is UNREAD AND UNDISMISSED NEVER EXPIRES.** It is
badge-eligible, so retention keeps it indefinitely, and episode suppression keys on `dismissed_at IS
NULL` — so a learner who never opens their inbox holds exactly one such row and a badge stuck at 1,
permanently, until they dismiss or read it. **That is intended** (a pending signal must not vanish, and
one stuck badge is far better than the alternative the release exists to prevent — a learner silently
never told again). It is documented because the dismissal path was the only one written down, and the
permanence is the half a reader would otherwise meet as a surprise.

A row the learner dismissed without reading remains eligible either way — dismissing is the learner
saying they are done with it.

The notification indexes are the V139 recipient/dedup unique index, the V139 recipient/unread index,
and the V143 partial inbox index on `(recipient_user_id, created_at DESC) WHERE dismissed_at IS NULL`.
The inbox index matches `findVisibleInbox`'s filter and sort. Retention intentionally has no supporting
index before the measured table-size threshold warrants one.

**⚠️ Retention is NOT erasure, and cannot stand in for it.** Because unread actionable rows are kept indefinitely,
nothing on the retention path can ever clear a deleted account's inbox. `AccountPurgeService.deletePersonalRows`
calls `deleteByRecipientUserId` for exactly that reason — it shipped missing in `v0.130.0` and was fixed in
the same release, having left every purged account's notification history behind permanently.

**⚠️ Notification is not workflow history.** Deleting a row must never touch the invitation, share,
grant or collection it referred to. There is no archival infrastructure — one delete query on a
schedule.

## What deliberately does not exist yet

The inbox currently has two producers: Admin "What's New" announcements and published Official Review
Set updates for adopters.

**Not built, and not to be added without its own release:**

- **Stage 5 events** — connection request, named note share. **⚠️ Deferred on CONTAMINATION, not
  effort: the connection-request notification nudges a PENDING invitation toward ACCEPTED, which is
  the kill criterion of `[CHECKPOINT — due 2026-09-19]`, whose denominator is ONE.**
- **Stage 7** — assignments, push/email/SMS, quiet hours, a preferences centre.
- **Quiz-share notifications** — a share link has no addressee. **⚠️ Do not fabricate a recipient.**
- A notification for a note becoming PUBLIC, or for a generic public link — **named shares only**.
