# RELEASES.md - NoteLib

## v0.135.0 - Update Signal

**Status: In Progress** (kicked off 2026-09-09, base branch `releases/v0.135.0`, cut from `main` after `v0.134.0` merged as #1353 and tagged `eac429a4`)

Source: `docs/claude-plans/attention-notifications-email-expansion-stage1.md` (Stage D) **as corrected by `docs/claude-plans/attention-notifications-stage1-tightening-addendum.md` §2, which is the binding design.**

Theme: give the notification substrate its first real producer — when a curator publishes an Official Review Set update, the learners who adopted it are told.

### ⚠️⚠️ READ THE ADDENDUM'S §2, NOT THE AUDIT'S §17.2 — THE AUDIT'S DEDUP KEY IS A PERMANENT SUPPRESSION BUG

The audit proposes `REVIEW_SET_UPDATE:<adoptedCollectionId>`. Under the permanent unique index on `(recipient_user_id, dedup_key)` **that key can deliver exactly ONE notification per learner per set, ever.** `NotificationService.deliver` catches the constraint violation and **returns the OLD row** — no exception, no log line, publish reports success. `dismissed_at` is not in the index, so dismissing never frees the key, and retention only frees it after 90 days *and* a read/dismiss — so **a learner who never opens the bell is suppressed forever.**

**The corrected key is source-side:**

```
REVIEW_SET_UPDATE:<sourceCollectionId>:<lastUpdatePublishedAt as epochMilli>
```

`lastUpdatePublishedAt` already advances **only** when `unpublishedChanges` is true (`NoteCollectionService:754-760`), so **re-press idempotency is inherited for free** and no migration is needed.

### Planned Scope

1. **`NotificationType.REVIEW_SET_UPDATE` + `NotificationCategory.LEARNING_SYSTEM` (backend).** The first type with a real producer.
2. **The producer (backend).** Fires from the `publishReviewSetUpdate` **call site**, delivering to adopters of that source root, deep-linked to `/collections/{adoptedCollectionId}`.
3. **Episode suppression (backend).** A batch filter in the producer, between audience resolution and fan-out, dropping recipients who already hold an **undismissed** `REVIEW_SET_UPDATE` row for that source. **Settled by owner decision A2 — ships WITH the producer, not later.**
4. **Retire the transitional `ACTION_REQUIRED`** — see the open decision below.

### ⚠️ Anti-drift

- ❌ **NEVER fire on raw source drift.** Only `publishReviewSetUpdate`. The publication boundary exists to forbid exactly this.
- ⚠️ **THE TRIGGER IS THE CALL SITE, NOT THE STAMP.** `markReviewSetUpdatePublished` has **two** call sites — `publishReviewSetUpdate:758` and `publishInitialCurriculum:1849`. Wiring the producer to "the stamp advanced" would also fire on a set's first-ever publication.
- ❌ **NO COUNT IN THE COPY.** Under episode suppression the single open row is the learner's only signal across N publishes, so *"3 new notes"* is false by the second one. Generic copy only — this also preserves R10, the inbox's zero-N+1-by-construction property.
- ❌ **Do NOT call `deliver()` inside a transaction (R9).** It is deliberately non-transactional so a dedup-index violation cannot mark a whole fan-out rollback-only. `FanOutTransactionBoundaryTest` guards this — **do not delete it, and do not delete the open-in-view runtime test either; they cover different angles.**
- ❌ **Do NOT put suppression in `AnnouncementAudienceResolver`** — that class is editorial, never authorization, and a test guards it.
- ❌ **NO email** (decision D3; R1 unresolved — the 100/day cap is breached at 156–159 observed).
- ❌ No new endpoint unless the producer genuinely needs one; no admin surface; no preferences centre.
- ❌ **NO Learning Connections work** — `[CHECKPOINT — due 2026-09-19]` is ten days out and its kill criterion keys on `ACCEPTED`, denominator ONE.
- ✅ **Reuse `notificationFanOutExecutor`** (core 1 / max 2) — do not add a second executor or resize it (R5/R13).
- ✅ Dismiss must change no curriculum truth; applying an update stays authoritative in Review Set state.

### ⚠️ Corrections to carry — the audit's own numbers are stale

- **The audit says "≤30 recipients". `LET Comprehensive Review` now has 42 adopters** (ALE 30, PNLE 15, CPALE 8, Civil Engineering 1). D2's justification cited the old figure.
- **Exactly ONE update publish exists in the product's history** — 2026-09-09 at 01:50:47, on LET, forty seconds after the Education tagging run. Four of five public source roots have never published an update at all.
- **⚠️ That read WEAKENED the case for shipping suppression now, and the owner took the recommendation anyway.** Record it as a judgement call made with the counter-evidence in view, **not** as "suppression was obviously necessary."

### Settled owner decisions (do not re-litigate)

- **A1 — dismiss without applying → the next publish DOES re-notify.** Suppress-until-applied would reintroduce permanent silence through a different trigger.
- **A2 — episode suppression ships WITH the first producer.**

### ⚠️ ONE OPEN DECISION

**Does this release delete `ACTION_REQUIRED`?** `v0.134.0` marked it transitional and said Stage D replaces it. **But deleting the type also strands the `ACTION_REQUIRED` category with no type mapped to it** — a category with no producer, which is the same defect the whole audit opens with. And **D2 (connection request) would want that category back.** Options: (a) delete both type and category now, re-adding at D2; (b) keep both until D2 gives them a real producer. **Decide before the Codex prompt.**

### ⚠️ Inherited checkpoint — `v0.134.0` is DEPLOYED and its clock has started

`V143` ran **2026-09-09 03:53:07**, so `[CHECKPOINT — due deploy + 1 day]` is **2026-09-10**. **Proximal (a) is already ANSWERED: `idx_notifications_inbox` is live in production with the exact expected definition.** Proximal (b) — `/actuator/metrics/announcement.fanout.duration` resolves — is still owed. **⚠️ Its distal tier is gated on a first announcement publish, and ZERO announcements have ever been published**, so a quiet read is *not yet measurable* → re-date, never a pass.

### Verification tier

**To be decided when the shape is known**, but the prior is **one scoped cold agent**: this adds a producer to a fan-out path that has never run in production, and the last two releases each shipped a delivered test that passed for a reason unrelated to its change. **Routing: CODEX** — new enum values, a producer, a batch suppression query and its tests span backend service, repository and entity layers.

### Shipped

_(nothing yet)_

## v0.134.0 - Notification Foundations

**Status: Released** (kicked off 2026-09-09, signed off 2026-09-09, base branch `releases/v0.134.0`, cut from `main` after `v0.133.0` merged as #1349 and tagged. Shipped as PRs #1350, #1351, #1352.)

Source: `docs/claude-plans/attention-notifications-email-expansion-stage1.md` (Stage A audit, 2026-09-08, every claim `file:line`-anchored and backed by read-only production `SELECT`s).

Theme: harden the notification substrate's taxonomy and dedup identity **while `notifications` still has zero rows in production**, so the first real producer lands on a shape that can carry it.

### ⚠️⚠️ THIS RELEASE SHIPS NO USER-VISIBLE CHANGE, DELIBERATELY — DO NOT READ IT AS A STALLED RELEASE

Every item here is a mechanism change behind an inbox that has **never rendered a numeric badge in production and could not have**. `countActionableUnread` filters `type IN actionableTypes()`, which resolves to `{ACTION_REQUIRED}`, and **zero code paths produce that type** — `AnnouncementService.deliverOne` is the only caller of `NotificationService.deliver` and it always passes `ANNOUNCEMENT`.

**The entire argument for doing this now is the row count: `notifications` is EMPTY in production — RE-VERIFIED READ-ONLY AT THIS KICKOFF, 2026-09-09**, not carried over from the audit: `notifications` **0 rows**, `announcements` **0 rows**, **0** distinct dedup keys, **0** `ACTION_REQUIRED`. **⚠️ The re-read is not a formality — it is the `v0.133.0` precedent, where the precondition read found EIGHTEEN catalog programs against the migration's THREE seeds and a repo-only audit would have been unsound. One admin publish between the audit and the prompt would put up to 396 rows in the table and turn item 2's key-format change from free into a live reconciliation that is not scoped.** Items 1–3 are pure Java, item 5 is one JPQL predicate. **Every one of them becomes a data migration with a backfill and a reconciliation the day the first real notification lands.** That window closes permanently and silently — nothing will announce it.

### Planned scope — Stage B items 1–3 + 5 only, DDL-FREE

1. **Taxonomy split.** `NotificationType` becomes **producer-level identity**; a new `NotificationCategory` carries **badge policy**; `actionableTypes()` derives its set from the category rather than from a boolean on the type.
2. **Widen `dedupKey` to accept a String discriminator.** Today `dedupKey(type, entityId)` returns `type.name() + ":" + entityId` (`NotificationService:104-106`) — so every future `ACTION_REQUIRED` producer shares one key space and two producers holding the same entity UUID collide silently under `idx_notifications_recipient_dedup`.
3. **The API response carries `actionable`/`category`, and the frontend stops comparing to the literal `"ANNOUNCEMENT"`.** `notification-inbox.tsx:110,117` is the frontend's private mirror of `NotificationType.actionable`; it mis-counts the badge the moment a third type exists.
5. **Retention expires non-actionable unread rows.** `deleteReadOrDismissedBefore` deletes only rows with `read_at` or `dismissed_at` set, so **an unread row is immortal** — an `EVERYONE` announcement to 396 users leaves 396 permanent rows per announcement, forever. **90 days, reusing the existing `retention-days` constant — no second knob** (decision D7).

### ⚠️⚠️ SUPERSEDED 2026-09-09 — ITEM 4 IS BACK IN SCOPE, BECAUSE THE PREMISE BELOW EXPIRED

**`V141` AND `V142` BOTH RAN IN PRODUCTION ON 2026-09-08** — verified read-only against
`flyway_schema_history` at 2026-09-09 (V141 12:23, V142 15:47, both `success = true`). **The single
stated reason item 4 was deferred no longer holds:** the queue is clean, `V142` is the tip, and a
`V143` is now an isolated one-statement migration against a table that is still empty (0 rows,
re-verified the same day).

**⚠️ The owner elected to complete ALL of Stage B** — items **4, 6 and 7** ship as PART 2, after
Part 1 merged as **PR #1350**. **So the DDL-free constraint below applied to Part 1 ONLY and is now
lifted.** It is kept verbatim rather than deleted because it records *why* the split happened, and
because the reasoning — do not add a migration to an unrun queue — is correct and will apply again.

**⚠️ The verification tier is RE-DECIDED for Part 2 — see the Verification tier section.**

### ⚠️ Item 4 (the `idx_notifications_inbox` index) WAS EXPLICITLY OUT OF PART 1, AND SO WAS EVERY DDL STATEMENT

Items 1–3 need no DDL — `type` is already `VARCHAR(64)` with `EnumType.STRING`, `dedup_key` is already `VARCHAR(255)` (`V139__notifications.sql:4-5`) — and item 5 is a predicate change. Item 4 would be the **only** reason this release carries a migration, and **`V141` and `V142` are both still UNRUN in production**. Adding a third migration to an unrun queue to fix a sort the audit measured as immaterial at 0 rows is a bad trade. **Let the index ride the next migration that exists for another reason.**

**⚠️ If a diff in this release adds a migration, the scope has drifted.**

### ⚠️ THE VERIFICATION TRAP: "behaviour must be identical after items 1–3" IS THE `v0.116.0`/`v0.117.0` SILENT-NO-OP SHAPE

`actionableTypes() == {ACTION_REQUIRED}` passes **before and after** the change, so a green run on it is evidence about nothing. **At least one assertion must be one that CANNOT pass against `main`** — the category **partition** test is the natural one, because no category enum exists today.

Stage B's verification list (**not** the audit's "Verification the first producer owes" section — that is a **Stage D** list and seven of its ten items presuppose a Review Set update producer that is not shipping here):

- `actionableTypes()` still resolves to exactly `{ACTION_REQUIRED}` — the regression guard for the derivation change.
- **Badge-eligible and retention-expirable PARTITION the categories** — one flag, two derived sets, with a test asserting the partition. Two hand-maintained lists is how they drift. ⚠️ **This is the assertion that cannot pass before the change.**
- A **non-actionable** unread row past 90 days is deleted; an **actionable** unread row past 90 days is **not**.
- The `GET /notifications` JSON actually carries `actionable`/`category` — extend `NotificationControllerTest`'s existing **real request** (`.contentType(MediaType.APPLICATION_JSON)`), never a direct handler call (`v0.119.0`).
- The frontend badge decrement reads the new field. **The existing tests asserting the `"ANNOUNCEMENT"` literal must MOVE WITH the change** — left as they are they pass for the wrong reason.

### ⚠️ `docs/features/notifications.md` IS A DELIVERABLE OF THIS RELEASE, NOT A SIGNOFF SCRAMBLE

All four items change behaviour that file **currently documents as true**, and it must move in the same PR — this is the failure CLAUDE.md flags hardest, and it has cost three consecutive releases:

| Line | Claim that becomes false | Item |
|---|---|---|
| `:24` | `dedup_key` is built by `NotificationService.dedupKey(type, entityId)` | 2 |
| `:35` | *"`NotificationType` carries an `actionable` flag, and `actionableTypes()` derives the set from it"* | 1 |
| `:166-167` | the announcement id is passed as **both** arguments to `dedupKey` | 2 |
| `:252-256` | retention deletes read/dismissed rows only, and *"Unread AND UNDISMISSED actionable notifications are RETAINED regardless of age"* | 5 |
| inbox response shape | must gain `actionable`/`category` | 3 |

**⚠️ `:252-256` is the sharp one: item 5 makes that sentence true only for the ACTIONABLE half.** Non-actionable unread rows start expiring at 90 days, and the doc currently states the opposite without qualification.

**✅ One correction to that file was made AT KICKOFF, because it was stale independently of this release:** its *"Stage 6 — blocked on the §8 drift-signature dedup decision"* line pointed at a design `v0.132.0` already ruled must **not** be implemented. Corrected there and in the superseded Backlog row, which read the same way.

### Anti-drift

- ❌ **NO new enum values without a producer.** `NotificationType` keeps **exactly** its two current values. Adding `REVIEW_SET_UPDATE` / `CONNECTION_REQUEST` / `NOTE_SHARED` now recreates the exact defect this audit opens with — a type with zero producers whose only tests hand-build a state no code path reaches.
- ⚠️ **`ACTION_REQUIRED` survives as an explicitly TRANSITIONAL placeholder for BACKWARD-COMPATIBLE TAXONOMY TRANSITION.** **Stage D replaces it** with `REVIEW_SET_UPDATE`. **Say this in the enum's javadoc**, or a future session reads it as permanent and builds on it. **⚠️ AMENDED 2026-09-09 by the owner's tightening addendum: do NOT justify it as "retained so badge behaviour stays exercised" — a domain-model value is not justified by the tests it keeps alive. Tests exercise the production model; they do not determine it.**
- ❌ **`NotificationCategory` stays DERIVED IN JAVA, never a stored column.** A stored category is a second source of truth that can disagree with the type, and no query needs to filter on it independently — `countActionableUnread` already takes its set as a parameter.
- ❌ **No DDL, no migration, no index** (see above).
- ❌ **Do not call `NotificationService.deliver()` from inside a transaction.** `deliver()` is deliberately non-transactional so a `DataIntegrityViolationException` on the dedup index does not mark an entire fan-out rollback-only. Item 2 edits the key that catch depends on.
- ❌ No `exists` check before a notification insert — the unique index is the mechanism.
- ✅ **`findVisibleInbox` and `countActionableUnread` predicates change TOGETHER.** The `dismissed_at IS NULL` leg on the count was a `v0.130.0` pressure-test fix and the repository javadoc records why.
- ✅ `read_at` = awareness, `dismissed_at` = inbox visibility. **No `resolved` column.**
- ✅ Opening the panel marks nothing read; poll the count endpoint only, never more often than 60 s; never render a literal `0` badge.
- ❌ **No Stage D producer, no Stage E email work, no executor change (item 6), no metrics (item 7).** Items 6 and 7 have **no closing window** — they cost the same in six months. That is the whole reason they are not here and item 1–3+5 are.
- ❌ **No Learning Connections work** — `[CHECKPOINT — due 2026-09-19]` is ten days out and its kill criterion keys on `ACCEPTED`, denominator **ONE**.
- ❌ **No `frontend/app/onboarding` work** — `[CHECKPOINT — due 2026-09-11]` is two days out.
- ❌ `AnnouncementAudienceResolver` must not touch `FeatureGateService` — editorial, never authorization.

### ⚠️ One thing to record while it is still true

**Item 2's key-format widening is free ONLY because there are no existing keys.** No stored dedup key has ever been written in production, so no format is a contract anyone can rely on. After the first producer ships, changing this format means reconciling live rows.

### ✅ Pre-signoff cold agent — RAN 2026-09-09, ALL EIGHT CLAIMS UPHELD, ONE GAP CLOSED

The scoped cold agent this release was re-tiered to **was run rather than waived**, framed as
falsification against eight named claims plus the two structural questions. It read the real code and
**re-ran the suites itself** rather than trusting this session's summary. **No claim was refuted** —
R9, the detached-entity hand-off, `V143`'s predicate match, the retention split, the contract removal,
the no-new-producer rule and the end-to-end badge all held.

**⚠️ IT FOUND ONE REAL GAP, AND IT IS THE KIND ONLY A COLD READER FINDS: the executor WIRING was
proven by nothing.** Every test in `AnnouncementServiceIntegrationTest` builds the service by hand
with a test-double executor, and `AppConfigTest` calls `new AppConfig()` directly — so neither proves
the **Spring-managed** service receives the **Spring-managed** fan-out executor. Context-load success
proves only that *some* `TaskExecutor` resolved.

**That gap was live, not theoretical.** Re-qualifying the constructor to `analyticsTaskExecutor`
**loads the context and passes every other test in the class**, while quietly putting announcement
fan-out on the pool that persists analytics — doubling pressure on the 20 connections production has
already exhausted twice (R5/R13). Closed with
`theSpringManagedServiceReceivesTheDedicatedFanOutExecutorAndNotTheAnalyticsPool`, which asserts bean
identity and thread-name prefix on the Spring-managed instance. It fails under that mutant.

**⚠️ It also credited a test this session had NOT: `aRetriedFanOutStillWorksWithAnOpenSessionInViewEntityManagerBoundToTheThread`**
binds an `EntityManagerHolder` exactly as `OpenEntityManagerInViewInterceptor` does and asserts the
persistence context is still readable after three caught constraint violations. That is the R9 angle
the reflection guard cannot see, and the two are complementary rather than redundant — worth recording
so neither is later deleted as duplicative.

**⚠️ Method note, recorded against the next time: this session switched the working branch while the
agent was running**, and the agent reported a file "changing under it" mid-review. It re-read and
self-corrected, and its test runs post-date the switch, so the conclusions stand — **but do not check
out another branch under a running cold agent.**

**Part 2 mutants — three run, three killed:**

| Mutant | Killed by |
|---|---|
| Fan-out reverted to synchronous | three async/rejection tests |
| `@Transactional` added to `fanOut` (R9) | `FanOutTransactionBoundaryTest` — *added by this audit* |
| **Fan-out re-qualified to `analyticsTaskExecutor`** | **`theSpringManagedServiceReceivesTheDedicatedFanOutExecutor…` — *added after the cold agent; it loaded the context and passed everything else*** |

### ⚠️ R1 IS A LIVE PRODUCTION FINDING THIS RELEASE DOES NOT FIX — recorded so it is not lost

**The email daily cap is already breached.** Configured `EMAIL_DAILY_LIMIT` is **100** with a **40** reserve; observed production peaks are **156, 158, 157, 156** sends/day over the last 90. The budget gates **`INACTIVITY` only** — weak-concept, weekly-summary, due-concepts and knowledge-impact dispatches are all unbudgeted, so the "100/day limit" describes one of five channels. **This blocks every future email producer (Stage E) and is indexed in `ROADMAP.md`'s Backlog Index, not scoped here.**

### ⚠️⚠️ Verification tier — RE-DECIDED 2026-09-09 FOR PART 2: ONE SCOPED COLD AGENT

**Part 1 was correctly tiered at a single `advisor()` call and shipped that way.** Part 2 changes the
shape, and `CLAUDE.md`'s gate now fires on **three** independent triggers rather than one:

1. **⚠️ Delivery introduced a defect this session that the session then fixed** — the measured
   blind-spot signal, and the gate names it explicitly. Part 1's frontend tests passed against the
   OLD implementation, and a backend mutant survived. That is not a hypothetical.
2. **The bug class is one the gate says is inherently hard to reason about serially** — async
   ordering. Fan-out moves off the request thread, and R9 (transaction poisoning) is an invariant that
   a green suite will not notice being broken.
3. **A second change touches the same shared methods** — `AnnouncementService.publish` / `fanOut` and
   `NotificationService.deliver`, both already edited by Part 1.

**Frame it as FALSIFICATION, not open-ended audit:** hand it a tight file list and the specific claims
this session made, and ask it to disprove each. `model: "sonnet"` is enough for claim-checking.

**⚠️ The claim most worth attacking: *"fan-out still runs outside any transaction."*** That is R9, it
is what keeps a single duplicate key from taking down an entire fan-out, and it is invisible to a
passing test suite.

**Superseded rationale, kept as the record:** *A single `advisor()` call.* No authorization or privacy boundary moves, no money/quota/production-data semantics change, and `notifications` has **zero rows** so item 5 has nothing to delete in production. Declared at kickoff so signoff does not re-derive it — **but re-decide if the shape changes**, per `v0.133.0`, which was tiered at one `advisor()` call, gained a write endpoint, ran one cold agent, and had **three of seven named claims refuted**.

**Routing: CODEX** — backend enum + service + JPQL + response DTO, plus the frontend component. Multi-system, so a prompt comes first. **Call `advisor()` before writing that prompt.**

### Shipped

- Added `NotificationCategory` as the single owner of numeric-badge policy, with badge-eligible and
  retention-expirable category sets derived as complements from one flag. `NotificationType` remains
  the two-value producer identity and now delegates policy to its category.
- Widened notification dedup discriminators from UUID to string while preserving the existing
  `ANNOUNCEMENT:<uuid>` keys and the unique-index catch-and-reread delivery contract.
- Added the server-derived `actionable` field to inbox responses and moved the frontend badge update
  and rollback branches to that field.
- Extended the existing 90-day cleanup to expire unread non-actionable rows while retaining unread
  actionable rows indefinitely; no new retention setting was added.
- Added category-partition, retention-direction, real-response-shape, dedup-format and frontend badge
  behavior coverage. Part 1 added no producer, notification type, endpoint, migration or index.
- Added V143's partial inbox index on recipient and descending creation time for visible notifications,
  closing the unindexed inbox sort now that the production migration queue is clear.
- Moved announcement delivery to the bounded `notificationFanOutExecutor` (core 1 / max 2). Publish now
  resolves the audience synchronously, reports queue acceptance immediately, and preserves retry/top-up
  semantics through the existing unique dedup index.
- Replaced synchronous publish delivery totals with `recipientCount` / `queued` and updated the admin
  feedback to describe background delivery or a recoverable queue rejection accurately.
- Added fresh-delivery, dedup-conflict, fan-out-duration and fan-out-rejection meters so asynchronous
  delivery remains observable without a per-user reporting surface.

### ⚠️ Audit finding — the frontend tests as delivered passed for the wrong reason, and mutation testing is what caught it

**Reverting `notification-inbox.tsx` to its exact pre-release implementation (`type !== "ANNOUNCEMENT"`)
left all 18 frontend tests GREEN.** Every fixture set `actionable` to *agree* with `type`, so the old
branch and the new one returned the same answer for all of them — the suite could not distinguish the
change it existed to verify. **⚠️ This is the `v0.116.0`/`v0.117.0` silent-no-op shape arriving from a
new direction: tests WERE added, and they still proved nothing about the change.**

Fixed by adding the one fixture where the two **disagree** — a non-actionable type whose name is not
`"ANNOUNCEMENT"` (`IMPACT_MILESTONE`), which is exactly the case the taxonomy split exists to fix and
the case the old code got wrong. That test fails against the old implementation and passes against the
new one.

**Four mutants were run and all four were killed, each by a named test:**

| Mutant | Killed by |
|---|---|
| `retentionExpirableCategories()` stops filtering (partition broken) | `NotificationCategoryTest.badgeEligibleAndRetentionExpirableCategoriesPartitionEveryCategory` |
| Every type made retention-expirable (unread actionable rows deleted) | `NotificationServiceIntegrationTest.retentionDeletesUnreadNonActionableAndReadRowsButKeepsUnreadActionableRowsPastTheWindow` |
| Badge branch never fires | `decrements the badge when an actionable notification is marked read` + `reverts the optimistic unread delta when marking read fails` |
| **Component reverted to the old `"ANNOUNCEMENT"` literal** | **`does not change the badge for a non-actionable type that is not ANNOUNCEMENT`** — *added by this audit; nothing killed this mutant before* |
| `retentionExpirableTypes()` returns the wrong side of the split | `NotificationCategoryTest.retentionExpirableTypesAreTheComplementOfActionableTypes` — *added by this audit* |

**⚠️ ONE MUTANT SURVIVES, KNOWINGLY, AND IT IS RECORDED RATHER THAN PAPERED OVER.** Hardcoding
`NotificationType.isActionable()` to `this == ACTION_REQUIRED` — bypassing the category delegation
entirely — **passes the whole suite.** That is not a fixable gap at this size: with two types mapped
one-to-one onto two like-named categories, delegation and hardcoding are **observationally identical**,
and no test can separate them without a third type, which this release forbids.

What was added instead is the guard that fires when it *starts* to matter: `everyTypeDerivesItsActionabilityFromItsCategory`
enumerates `values()`, so a third type that is misclassified fails immediately. A `category()` accessor
was added to make that invariant assertable. **The javadoc on that test states outright that it cannot
discriminate today**, so a later reader does not credit it with more than it proves.

### ⚠️ Part 2 audit finding — the R9 guard could not fail, and only mutation revealed it

**R9 is the invariant this release rests on:** `deliver()` catches the unique-index
`DataIntegrityViolationException`; under an ambient transaction that marks the whole transaction
rollback-only, so **one duplicate recipient would take an entire fan-out down**. Part 2 shipped a test
asserting `TransactionSynchronizationManager.isActualTransactionActive()` is false during delivery,
which reads exactly like the guard for it.

**It is not one. Adding `@Transactional` to `fanOut` left all 20 of that class's tests GREEN.** The
test constructs `AnnouncementService` with `new`, so there is **no Spring AOP proxy and the annotation
is inert** — the fixture cannot express the state it claims to forbid. **⚠️ This is the repo's
recurring "the guard must reach its subject the way production does" failure arriving from the
opposite end**, and it is the second release running where a delivered test passed for a reason
unrelated to the change.

Closed with `FanOutTransactionBoundaryTest`, a reflection check over the declared annotations on
`publish`, `fanOut`, `deliver` and both classes. It **cannot** be fooled by proxy absence, because the
annotation is exactly what production reads. It fails under the mutant.

**Also removed:** the single-argument `fanOut(AnnouncementEntity)` overload, left with **no callers in
main or test** once `publish` began resolving the audience itself — dead public API on a service whose
transaction boundary is load-bearing.

**Part 2 mutants — two run, both killed after the fix:**

| Mutant | Killed by |
|---|---|
| Fan-out reverted to synchronous | `publishReturnsAfterQueueAcceptanceBeforeAnyNotificationIsDelivered` + `rejectedDispatchReturnsZeroAndRepublish...` + `executorTaskRunsFanOutWithoutAnAmbientTransaction` |
| **`@Transactional` added to `fanOut` (R9 violated)** | **`FanOutTransactionBoundaryTest.announcementPublishAndFanOutAreNotTransactional` — *added by this audit; the whole suite passed before it*** |

**Verification run:** backend 2,312 tests + the 101-query PostgreSQL native harness against a real
container; frontend 2,346 tests across 211 suites; `tsc --noEmit` clean; `npm run lint` 0 errors.

## v0.133.0 - Education Family

**Status: Released** (kicked off 2026-09-08, signed off 2026-09-08, base branch `releases/v0.133.0`, cut from `main` after `v0.132.0` merged as #1347 and tagged)

Source: `docs/claude-plans/program-family-generalization-and-education-family.md` (audit complete 2026-09-08, every claim `file:line`-anchored).

### ⚠️⚠️ THE BRIEF'S CENTRAL PREMISE IS FALSE, AND THAT IS THE MOST IMPORTANT THING IN THIS SECTION

**There is no Engineering-specific authoring shortcut, so there is no generalization to build.** The button at `applicable-programs-combobox.tsx:290` interpolates the family name and count, and the families are derived **dynamically from the catalog** (`:88-111`) by mapping each program's `programFamilyId`/`programFamilyName`. **There is no Engineering literal anywhere in it.** It reads *"Add all 18 Engineering programs"* only because **Engineering is the only family that has members**.

**So this release is a DATA change plus one admin form field. Seed the Education family and the existing UI renders *"Add all 8 Education programs"* with no code change.** §6 of the brief is already satisfied too — all four authoring surfaces share that one component.

**⚠️ DO NOT MODIFY THE APPLICABLE-PROGRAMS COMBOBOX. If a diff touches it, the scope has drifted.**

### The two gaps the audit named — ⚠️ ONLY ONE OF THEM IS REAL (see below)

| Gap | Detail |
|---|---|
| Families can only be created by migration | Zero `ProgramFamilyEntity` construction or `save` anywhere in the backend |
| The admin UI cannot assign a family | `POST /course-programs` **already accepts and validates** `programFamilyId` (`CourseProgramCatalogService:54-56`, `UnknownProgramFamilyException`) — `app/admin/course-programs/page.tsx` just exposes no field for it |

### Planned scope

1. **One migration** — insert the `Education` family; assign the **existing** `Education` program row to it; **RENAME** `Special Needs Education – Generalist` → `Special Needs Education` keeping its `id`; insert the remaining programs from the audit's §4 (Elementary Education, Secondary Education, Early Childhood Education, Technical-Vocational Teacher Education, Physical Education, Teacher Certification).
2. **Admin family selector on create** — the endpoint already supports it. **This is what stops the release recurring**: without it, every future family member is another migration.
3. **Copy polish** on the Course/Program and Domain Context helper text. **⚠️ Drop resolver mechanics from it** — keep the conceptual separation, do not explain the backend.

### ⚠️⚠️ THE MIGRATION SEED IS NOT THE LIVE CATALOG — THE READ-ONLY AUDIT IS A PRECONDITION, NOT A FORMALITY

`V106` seeds **three** Engineering programs; production reportedly has **18**, and no later migration inserts any. **The catalog has been extended through `POST /course-programs` in production, so a duplicate audit from the repo alone is UNSOUND.** Run `docs/claude-plans/v0.133.0-education-family-precondition-read.sql` (read-only) **before writing the migration**. An inserted duplicate is visible to every curator immediately and is awkward to withdraw once Notes reference it.

**⚠️ `GET /course-programs/similar?name=` already exists** (ADMIN-only) — the duplicate check the brief asks for is already built; the admin create flow should use it per name.

### ⚠️ What the rename touches

**Safe:** `note_course_program` joins by `course_program_id` (`V107:4`), so every Note keeps its link through a rename — the row keeps its `id`, only `name` changes.

**⚠️ NOT safe automatically: five free-text `course_program` columns** (`notes`, `note_collections`, `users`, `bulk_generation_result`, `official_study_plan_wishlist` — the last has `normalized_course_program` too) may hold the literal old string and would silently keep it. No repository method resolves a catalog entry by name, so nothing breaks — but a stale string stops matching the catalog, which affects discovery and wishlist normalization. **The precondition read counts these.**

### ✅ PRECONDITION READ RUN 2026-09-08 — RESULTS, AND ONE CONDITION NOT MET

Run read-only against `notelib-db-prod`. **The audit's central warning is now VERIFIED, not assumed: `program_families` shows Engineering with EIGHTEEN programs against `V106`'s THREE seeds**, so the catalog was indeed extended through the API and a repo-only duplicate audit would have been unsound.

| Check | Result |
|---|---|
| Education-adjacent catalog rows | **Only two** — `Education` (`exam_goal_slug='let'`, no family) and `Special Needs Education – Generalist` (no slug, no family) |
| Families | **One** — `Engineering`, 18 programs. `Education` does not exist as a family |
| Collisions with the six proposed inserts | **None.** Elementary, Secondary, Early Childhood, Technical-Vocational Teacher, Physical Education and Teacher Certification are all clear |
| Notes linked to the rename target via `note_course_program` | **ZERO** |
| Free-text `course_program` hits | **ONE** — `users.course_program`, exactly `Special Needs Education – Generalist` (36 chars, en dash) |

**⚠️ OWNER DECISION 1'S CONDITION IS NOT MET.** The rename was settled *conditional on zero free-text hits*; there is one. **⚠️ NOTHING BREAKS** — `users.course_program` is consumed by `StudyPackGenerationContextResolver` as **free text**, never resolved against the catalog by name — but that one account's profile program would stop corresponding to a catalog entry. **Handed over as an owner-run write in `docs/claude-plans/v0.133.0-owner-profile-string-update.sql`** with the expected row count and before/after verification. Three options are stated there; the recommendation is to run it **in the same maintenance step as the migration, not before it**.

**⚠️ CONSEQUENCE FOR THE RENAME GUARD TEST: its production denominator is ZERO.** No Note is linked to the row being renamed, so the guard is a purely synthetic structural test. **Write it anyway — it is the regression guard for the migration — but do NOT record it as evidence that real data survived**, because there is no real data to survive.

**✅ DECISIONS 2 AND 3 SETTLED 2026-09-08 (owner):** new Education programs carry `exam_goal_slug = 'let'` **only where learners genuinely sit the LET**, never as a family proxy; and **the admin family selector ships in this release.**

### Owner decisions

1. ~~Reuse or rename `Special Needs Education – Generalist`?~~ **SETTLED 2026-09-08: RENAME**, keeping the row's `id`. **⚠️ THE CONDITION WAS NOT MET — the read returned ONE hit in `users.course_program`.** The catalog rename still proceeds; the one stale profile string is handed over as an owner-run write (`docs/claude-plans/v0.133.0-owner-profile-string-update.sql`), and option (c) there reopens this decision if the owner prefers.
2. **✅ SETTLED 2026-09-08 — `exam_goal_slug = 'let'` only where learners genuinely sit the LET.** Original recommendation, accepted: **only where learners genuinely sit the LET**; leave NULL otherwise rather than making the exam goal a family proxy. `Education` already carries `'let'`.
3. **✅ SETTLED 2026-09-08 — the admin family selector SHIPS IN THIS RELEASE.**

### Verification tier

**A single `advisor()` call**, per the audit's §9 — a data migration plus one admin form field. No permission substrate, no cross-user read, no money semantics, no learner-facing behaviour change. **⚠️ But it writes to a production catalog table, so the precondition read is the gate.** **⚠️ THE `v0.131.0`/`v0.132.0` LESSON STILL APPLIES: decide the tier from the SHAPE of the change, and re-decide if the shape changes** — `v0.132.0` was tiered at one cold agent, ran two, and they found four blocking defects including one that silently emptied a learner's adopted Goal.

### ⚠️ THE AUDIT'S SECOND "REAL GAP" IS ALSO FALSE — THE ADMIN FAMILY SELECTOR ALREADY SHIPPED

The audit named two real gaps. **One of them is not real.** It claimed *"the admin UI cannot assign a family — `frontend/app/admin/course-programs/page.tsx` exposes no family field."* That is literally true of `page.tsx`, which is a 40-line shell — **but the field lives in `admin-course-program-catalog-section.tsx`, which that page renders.** It has a `<select id="catalog-program-family">` listing every family derived from the catalog, `create()` sends `programFamilyId`, the table shows a Family column, and its helper text already says assigning a family makes the program participate in that family's expansion.

**It shipped 2026-08-11 in `9e77f412`, tagged `v0.100.0`** — verified as an ancestor of `main`, not merely present in a working tree.

**⚠️ THIS IS THE SAME ERROR CLASS THE AUDIT ITSELF CAUGHT IN THE BRIEF: concluding a capability is missing by reading the wrong file.** Both times the mistake was to check a shell rather than the component doing the work. **So step 3 of the implementation plan is NOT BUILT HERE — it was already done**, and the release shrinks accordingly. The other gap in that table — *families can only be created by migration* — is real, and remains the deferred admin create-family surface.

**⚠️ Do NOT re-add a family selector to the admin page. If a diff adds one, it is a duplicate.**

### Shipped

- **`V142__education_program_family.sql`** — seeds the `Education` family, assigns the EXISTING `Education` program to it, **renames `Special Needs Education – Generalist` to `Special Needs Education` keeping its `id`**, and adds Elementary, Secondary, Early Childhood, Technical-Vocational Teacher and Physical Education plus `Teacher Certification`. **The migration IS the feature** — the combobox is untouched and now renders *"Add all 8 Education programs"* on its own. **⚠️ The ID range was verified against production rather than assumed:** 44 programs exist, exactly 21 in `V106`'s seed range with `...021` highest, so `...022`+ cannot collide.
- **⚠️ The renamed row also gains `exam_goal_slug = 'let'` — a user-visible change beyond a rename, flagged rather than buried.** `findNamesByExamGoalSlug` returns a `List`, so the field is one-to-many by design; leaving this row NULL would have made seven of eight family members LET-discoverable and one silently not. It now appears under the LET exam goal on the public endpoint.
- **Helper-text copy polish.** The Applicable Programs hint no longer explains the resolver (*"only a single program can inform the writing domain, and Domain Context overrides it"*) — true, but backend mechanics a curator cannot act on, and it invited the reading that picking one program is how you steer the writing. It now states the conceptual separation and points at program families.
- **Three multi-family combobox tests, guarding a bug class that was previously invisible.** Every existing fixture held exactly ONE family, because until `V142` production did too — so a component that ignored which family was clicked and expanded them all would have shipped green. **Mutation-verified with an isolating mutant that typechecks and is identical to correct behaviour under a single family: it kills ONLY the two new tests, with all 14 pre-existing tests passing.**
- **A Program Family can now be CREATED from the admin surface, so a new family no longer needs a migration.** `POST /course-program-catalog/families` plus a create control beside the existing family picker. **⚠️ THIS IS THE GAP THE AUDIT'S STEP 3 WAS SUPPOSED TO CLOSE BUT COULD NOT, BECAUSE STEP 3 ALREADY EXISTED** — assigning a family was already possible; creating one was not, which is why `V106` seeded Engineering and `V142` seeded Education. A third family would have been a third migration.
- **⚠️ A families READ endpoint ships with it, and it is not garnish.** A family is created EMPTY, and the admin form previously derived its options from the catalog — so a family created today would have vanished from the picker on refresh, before any program could be assigned to it. **The authoring combobox still derives families from the catalog, deliberately: it only cares about families that have members.** Do not unify the two.
- **Duplicate family names are rejected case- and whitespace-insensitively**, matching the course-program check, because two identical-looking families would produce two identical-looking expansion shortcuts. A lost race on `uk_program_families_name` resolves to the winning row rather than surfacing a constraint violation.
- **⚠️ The new endpoint owes and has a REAL request test.** The pre-existing `CourseProgramCatalogControllerTest` only reflected on annotations — the exact `v0.119.0` shape — so a `MockMvc` POST with `.contentType(APPLICATION_JSON)` was added and **mutation-verified: deleting the header reproduces `HttpMediaTypeNotSupportedException` and a 415.** `lib/api-program-families.test.ts` pins the client's own request shape, since the component test mocks `@/lib/api` wholesale.
- **`EducationProgramFamilyMigrationTest`** — the rename-guard fixture is created BEFORE the migration runs, since a link inserted afterwards resolves to the new name trivially. It also carries the Engineering-untouched regression guard. **⚠️ Its production denominator is ZERO** (no note is linked to the renamed row), so it is a structural guard and must not be reported as evidence that real data survived; its javadoc says so.


### Scope completeness — the three planned items, reconciled

**⚠️ THE PLANNED SCOPE LIST ABOVE READS AS THREE DELIVERABLES AND ONLY TWO OF THEM WERE BUILT. That is
correct, and this table is why** — a later reader must not see a release that shipped 2 of 3.

| Planned item | Outcome | Evidence |
|---|---|---|
| **1.** One migration | **SHIPPED** | `V142__education_program_family.sql`; guarded by `EducationProgramFamilyMigrationTest` |
| **2.** Admin family **selector** on create | **⚠️ NOT BUILT — IT ALREADY EXISTED** | `<select id="catalog-program-family">` has been in `admin-course-program-catalog-section.tsx` since `9e77f412` (2026-08-11, `v0.100.0`), verified an ancestor of `main`. The audit concluded it was missing by reading `app/admin/course-programs/page.tsx`, a 40-line shell that renders it. |
| **2′.** Admin family **create** surface — *substituted for item 2* | **SHIPPED** | `GET`/`POST /course-program-catalog/families` plus the create control. **This is what item 2 was actually for:** its stated justification was *"this is what stops the release recurring"*, and a selector alone does not — a family still could not be created without a migration. The substitution moved the work to the half of the gap that was real. |
| **3a.** Copy polish, **Course / Program** helper text | **SHIPPED** | `applicable-programs-combobox.tsx:328-331`; the resolver sentence was replaced one-for-one, no paragraph added |
| **3b.** Copy polish, **Domain Context** helper text | **NO CHANGE NEEDED — verified, not assumed** | `note-editor-form.tsx:512-515` already reads *"it shapes how the note is written, while the programs decide who finds it."* It carried no resolver mechanics to drop. Checked against the code rather than inferred from the item's wording. |

**So: 2 of 3 planned items built, 1 found already built, plus 1 substitution and 1 verified no-op.** The
release grew by one item (2′) and shrank by one (2).

**⚠️ VERIFICATION TIER WAS RE-DECIDED WHEN THE SHAPE CHANGED, NOT WHEN THE COUNT DID.** Kickoff
pre-declared *a single `advisor()` call* for "a data migration plus one admin form field." Folding 2′
added **a new write endpoint** — a different shape, not merely a fourth item — and `V142` changes
production catalog semantics (the renamed row gains `exam_goal_slug='let'` and becomes publicly
LET-discoverable). Under the `v0.131.0` rule (*decide the tier from the SHAPE*), that fires the
production-data trigger, so **`advisor()` plus one scoped cold agent ran**, framed as falsification of
this session's own named claims.

### Pressure test — one scoped cold agent, framed as falsification

Tier was re-decided when the shape changed (see above), not when the item count did. One agent, a tight
file list, and seven of this session's own named claims to DISPROVE rather than an open-ended audit.
**Three claims were refuted. Four were confirmed, and the confirmations matter as much** — the
`MockMvc` POST really does issue a request with `Content-Type` (not a method call), `exam_goal_slug` really
is one-to-many at every consumer, the combobox really is family-generic, and the admin control really
does GET on mount.

- **Fixed — the new repository SQL was executed by NOTHING.** `CourseProgramCatalogRepository` is a plain
  `JdbcTemplate` class with hand-written SQL constants and **zero** `@Query(nativeQuery = true)` methods,
  and `NativeQueryPostgresIntegrationTest` finds its subjects by reflecting over `@Query` — so the class
  is **structurally invisible** to the PostgreSQL harness that `CLAUDE.md` describes as covering *"every
  native query."* The service test mocks the repository and the controller test mocks the service, so all
  three new statements were verified **by inspection only**. Added
  `CourseProgramCatalogRepositoryProgramFamilyIntegrationTest`, which runs them against a real database.
  **⚠️ It also covers `mapProgramFamily`'s alias→getter mapping — the exact gap `v0.132.0` named, since
  `PREPARE` validates syntax and types but never that `SELECT id, name` actually feeds
  `getObject("id", …)`.** Mutation-verified: deleting the `jdbcTemplate.update(...)` inside
  `insertProgramFamily` — which still typechecks and still returns a valid-looking response — is killed
  by the new read-back assertion at `:88`, **with all 14 pre-existing service tests still passing.**
- **Fixed — `InvalidProgramFamilyNameException` had zero test references.** An added file with no test
  that executes it. It looks redundant with `@Size(max = 120)` on the request record, which is exactly why
  it was skipped — but the annotation guards the CONTROLLER while the exception guards the SERVICE, which
  a direct call reaches without validation.
- **Fixed — `"nothing resolves a course_program by name"` IS FALSE, and it had already reached the owner.**
  `bulk-generation-page-client.tsx:290` does `catalog.find(p => p.name === courseProgram.trim())`, where
  `courseProgram` is seeded from the user's free-text profile field (`:182`). **So the ONE account holding
  the stale `Special Needs Education – Generalist` string loses Bulk Generate's auto-selection after the
  rename** — graceful degradation, not corruption, but a real regression rather than "a string that no
  longer matches." The claim was true of the path it was checked against (`StudyPackGenerationContext-
  Resolver` resolves by ID) and was over-generalized from there.
  `docs/claude-plans/v0.133.0-owner-profile-string-update.sql` now carries the correction and it
  **strengthens** the recommendation to run the write. **⚠️ `V142`'s inline comment still carries the
  original wording and is deliberately NOT edited — the migration is committed, and changing it would
  alter its Flyway checksum and break startup wherever it has already been applied.**
- **Fixed — the LET fallback lists went stale the moment `V142` landed.** `ExamGoalConfig.java` and
  `frontend/lib/exam-hub-config.ts` both hardcode `let → ["Education"]` as a **fail-open** fallback used
  when the live catalog read fails or returns empty. Every consumer of the *live* list correctly treats it
  as list-valued, so there is no single-value bug — but the fallback itself would have silently
  under-represented the exam goal by **seven programs**. Both now list all eight, with their tests updated.
  **⚠️ This is the "sweep by SURFACE, not by diff" rule paying out: neither file was in the diff, and a
  stale fail-open fallback fails silently by design.**

### Findings recorded and deliberately NOT fixed

- **`CourseProgramCatalogRepository.resolveIdForLegacyName()` resolves by exact name and has ZERO callers**
  anywhere in `backend/src`. Confirmed untouched by this release. Genuinely dead code — deleting it is a
  separate change, and it is recorded here so the next reader does not rediscover it as a live risk.
- **The family dedup SQL uses `lower(trim(name))` while the course-program dedup additionally collapses
  internal whitespace** via `regexp_replace`. Not exploitable today: `normalizeForLookup` already collapses
  whitespace in Java before the parameter is bound. Recorded because the two paths are described as
  matching and, at the SQL level, they do not.

### What the cold agent could NOT check

**The production-collision claim.** `V142`'s comment asserts 44 programs with exactly 21 in the seed range,
verified against production on 2026-09-08. **A repo-only reviewer cannot confirm or refute that** — which
is the migration's own stated reason for requiring a precondition read. This is why the release owes a
post-deploy read rather than treating the migration test as sufficient.

### Anti-drift

- **⚠️ Do NOT modify the applicable-programs combobox** — it is already family-generic.
- **⚠️ Do NOT create a new Domain Context** — `GENERAL_EDUCATION`, `PROFESSIONAL_EDUCATION` and `PROFESSIONAL_PRACTICE_AND_REGULATION` already cover LET.
- **⚠️ Do NOT let Program Family select or override Domain Context**, and do not infer the Education family from `GENERAL_EDUCATION`.
- **⚠️ Do NOT feed Program Family or an expanded program list to the LLM.** `StudyPackGenerationContext` has no field for either and `courseProgram` is a single resolved String, so this is **structurally impossible today — keep it that way.**
- **⚠️ Do NOT delete or migrate away the existing `Education` program** — assign it a family only.
- **⚠️ Do NOT mass-update existing Notes' applicable programs.** No backfill of `note_course_program`.
- **⚠️ Do NOT infer Applicable Programs from Review Set membership**, and do NOT make family membership dynamic inheritance — expansion writes explicit program IDs at authoring time and nothing more.
- **⚠️ Do NOT create duplicate catalog entries**, and **do NOT use a credential abbreviation (BEEd, BSEd, CPE/DPE) as a canonical name.** `Teacher Certification` is the endorsed canonical name because `Professional Education` already exists as a `DomainContext` value and as a Subject, so it would collide across two axes.
- **⚠️ Do NOT block mixed-family selections** or add warning UX for them.
- **⚠️ Do NOT redesign the program taxonomy, touch learner-owned Notes, or change pricing, entitlements or Review Set architecture.**
- **⚠️ NO Review Set publication work.** `v0.132.0` shipped that boundary and owes `[CHECKPOINT — due 2026-09-22, deploy-relative]`; its F5 gap (no publish surface in the Builder) is recorded and **is not this release's to fix.**
- **⚠️ NO Learning Connections work** — `[CHECKPOINT — due 2026-09-19]` has a denominator of ONE.

### Tests

The audit's §14 is mostly covered already by the generic component. The genuinely new cases: **Education family expands to its explicit member IDs**; expansion creates no duplicate selections **asserted with two families present**; **mixed-family selection survives — ⚠️ a single-family fixture proves nothing**; the generation context receives no family or expanded list; `GENERAL_EDUCATION` does not auto-select Education programs; Review Set membership does not alter Applicable Programs.

**⚠️ RENAME GUARD: a Note linked to `Special Needs Education – Generalist` BEFORE the rename must still be linked after it and render the NEW name. A fixture created after the rename passes trivially and proves nothing.**

**⚠️ The Engineering-still-expands test is the regression guard for the migration** — if assigning the existing `Education` row a family accidentally touched Engineering rows, that is what catches it.


### ✅ Deploy sequencing — RESOLVED 2026-09-08, both migrations ran (note kept as the record)

**✅ RAN 2026-09-08 — V141 at 12:23, V142 at 15:47, both `success = true`, verified read-only against `flyway_schema_history` on 2026-09-09. The Education family has EIGHT members in production, which is this release's headline claim.** What follows described the state before that deploy and is kept as the record.

`V141` (`v0.132.0`) and `V142` (this release) were **both unrun in production.** Flyway applies them in
order on the next deploy, which is correct — but three dated obligations hang off *when that deploy
happens*, and they are recorded here rather than left to be inferred:

1. **`v0.132.0`'s `[CHECKPOINT — due 2026-09-22]` is deploy-relative, not merge-relative.** If the
   deploy slips, that date must be re-dated — it does not start counting at merge.
2. **`docs/claude-plans/v0.133.0-owner-profile-string-update.sql` must run in the SAME maintenance step
   as `V142`, not before it.** Running it first points the one affected profile at a catalog name that
   does not exist yet.
3. **`docs/claude-plans/v0.130.0-owner-production-checks.sql` is still outstanding.**

**⚠️ All three are the owner's to run. Claude does not execute writes or migrations against production.**

### Known limitations

- **All FOUR authoring surfaces render their own helper paragraph above the combobox's.**
  `note-editor-form.tsx:437-441`, `private-note-detail-page-client.tsx:2704-2706`,
  `bulk-generation-page-client.tsx:591-593` and `admin-applicable-programs-section.tsx:204` each carry a
  discovery-vs-authoring sentence directly above the combobox's own *"they decide who finds it, never
  how it is written."* They overlap without contradicting. **This predates the release** — the copy
  polish replaced one paragraph with one paragraph and added none — and was found by sweeping the
  surface rather than the diff. Left alone deliberately: consolidating it is a copy change across two
  more files and would have made this a fifth item.
- **`Physical Education` is seeded as an Education-family program carrying `exam_goal_slug = 'let'`.**
  That is right for the teaching degree, and the name is also how the *school subject* is commonly
  written. No collision exists today (the read found no such catalog row), but a future curator adding
  a subject-flavoured entry should reuse this row rather than create a sibling.
- **The families `GET` is ADMIN-only, matching the create endpoint.** The authoring combobox still
  derives families from the catalog, deliberately — it only cares about families that have members.
  **Do not unify the two paths.**

## v0.132.0 - Publication Boundary

**Status: Released** (kicked off 2026-09-08, signed off 2026-09-08, base branch `releases/v0.132.0`, **cut from `docs/planning-publication-boundary-and-plan-corrections` rather than `main`** so that branch's audit commit rides in via the release PR — the `v0.130.0`/`v0.120.0`/`v0.111.0` precedent, used here for convenience rather than because anything is blocked)

**Scope is slices P1 and P2** of `docs/claude-plans/official-review-set-update-publication-boundary.md` (audit written 2026-09-07 by a peer session, every claim `file:line`-anchored). **⚠️ P3 IS DEFERRED — see below.**

### The problem, in the audit's own words

> **Editing an Official Review Set is not publishing an Official Review Set update.**
> **Source changed != published update available.**
> **New adopters should not accidentally receive unfinished curator work.**

**There is exactly ONE source state today. No working/published separation exists anywhere**, so every curator edit is instantly learner-facing: adopters are immediately "behind", and a new adopter or a public viewer sees half-finished curriculum work.

### Planned Scope

**(P1) Publication boundary foundation.** Three columns plus a backfill; filter drift detection, adoption copy and `getPublic` to **published** rows only; a `Publish update` service and endpoint that is atomic, locked and idempotent. **⚠️ ONE MIGRATION. The whole contract lives in this slice.**

**(P2) Curator UX.** The `Publish update` action, an `Unpublished changes` indicator, and a confirmation carrying additions-only counts. No migration.

**⚠️ P1 AND P2 SHIP TOGETHER AND THAT PAIRING IS DELIBERATE, NOT PADDING.** P1 creates the endpoint but no way to press it; shipping it alone would leave curators unable to publish anything except through the API, while every edit silently stopped reaching learners. **That is the `v0.130.0` empty-inbox shape** — a substrate with no surface — and it was expensive enough once.

### ✅ THREE OWNER DECISIONS — ALL SETTLED 2026-09-08, BEFORE THE CODEX PROMPT WAS WRITTEN

The owner accepted all three of the audit's recommendations:

1. **✅ THE NARROWED PUBLIC-VIEW CONTRACT (§6) IS ACCEPTED.** Public visitors see published **additions**; **removals and reorders remain live and are NOT hidden.** The alternatives were dual working/published columns per field plus soft-delete, or a full snapshot — both disproportionate. **⚠️ THIS IS A STATED LIMITATION, NOT A GAP TO CLOSE LATER BY DEFAULT, and it is recorded here rather than only in the prompt because the prompt is gitignored.** See "Known limitation" below.
2. **✅ BACKFILL STAMPS THE COLLECTION'S `created_at`, not `now`** — so a future "Updated" date is not uniformly the deploy date.
3. **✅ `last_update_published_at` PRESERVES THE CAPABILITY FOR A PUBLIC "Updated" DATE, AND SHIPS NO UI FOR IT.** **⚠️ Do NOT add that UI in this release.**

### ⚠️ Known limitation, accepted at kickoff rather than discovered at signoff

**The public view hides unpublished ADDITIONS ONLY.** A note the curator **removes**, or a section they **reorder or rename**, is visible to the public and to new adopters immediately — because `position` and `label` are single columns and a delete is a real delete, so there is nothing to hide behind without a second column per field or a soft-delete. **⚠️ Test 11 must be written to this narrowed contract and MUST NOT be reported as passing in full.** The learner-facing contracts are fully satisfied; only the public browsing view is partial, and the residual is small in practice because a mid-expansion curator is overwhelmingly *adding*.

### Why P3 is deferred

**P3 (the Review Set update notification) was Stage 6 of the notification plan and this audit unblocks it** — §7 supersedes that plan's §8 drift-signature dedup blocker, which must NOT be implemented. **It is deferred on SIZE, not on doubt:** P1 carries a migration and rewires what `getPublic` and adoption copy can see, which is enough surface for one release. **⚠️ Release SIZE is the biggest lever on verification cost, and it compounds.**

### Anti-drift

**⚠️ A PER-ROW PUBLICATION STAMP, NOT A SNAPSHOT ARCHITECTURE.** The audit's central structural finding is that `applySourceUpdate` is **additive-only** — `NoteCollectionService:2050` says *"THIS RELEASE REPORTS AND NEVER APPLIES"*, and `MOVED` changes are detected and reported but never applied. **So the boundary only has to gate what becomes visible as an ADDITION.** Do NOT build snapshots, versions or history. **⚠️ Do NOT implement §8's drift-signature dedup — it is SUPERSEDED.** **⚠️ NO Learning Connections work: `[CHECKPOINT — due 2026-09-19]` is ELEVEN DAYS OUT and its denominator is ONE.** **⚠️ Do NOT build the notification half (P3), and do NOT add a public "Updated" date UI.** **⚠️ Curriculum update != Note content overwrite, and one learner remains one adopter across every update** — publishing makes changes *available for review*, it never forces learner synchronization. **⚠️ Adoption counts must keep working: this release changes what `getPublic` can SEE, and `v0.129.0`'s count reads the same rows.** No quota, entitlement or pricing change; onboarding untouched.

### Verification

**⚠️ AT LEAST ONE SCOPED COLD AGENT, DECIDED AT KICKOFF RATHER THAN AT SIGNOFF.** This release **moves a visibility boundary** — it changes what an anonymous `getPublic` caller and a new adopter can see — and it carries a migration with a backfill over existing production rows. Both are named triggers. **⚠️ AND THE `v0.131.0` LESSON APPLIES DIRECTLY: that release decided its tier by the letter of the gate, shipped, and a cold agent then found a trap it had introduced. Decide the tier from the SHAPE of the change, not from the item count.**

**⚠️ PRE-DECLARED GUARDS, from the audit's §12 and this repo's carried lessons:**
- **(1)** an unpublished curator edit is invisible to `getPublic`, to a NEW adopter, and to drift detection — **assert all three, since they read the same rows by different paths.**
- **(2)** publishing is **idempotent and atomic** — a second publish adds nothing, and a failure mid-way leaves no half-published set.
- **(3)** **an existing adopter's already-copied content is UNTOUCHED by a publish** — publishing offers, it never overwrites.
- **(4)** the backfill leaves every pre-existing set **published**, so nothing silently vanishes from Explore on deploy. **⚠️ ASSERT AGAINST REAL MIGRATED ROWS, AND THE HARNESS IS NAMED SO IT ACTUALLY HAPPENS: `NativeQueryPostgresIntegrationTest`**, which starts PostgreSQL 16 and applies the real Flyway migrations. **⚠️ Do NOT assert this in `NoteCollectionServiceProjectionIntegrationTest` — that file hand-writes its H2 DDL and can silently drift from the migration set**, which is the anti-pattern `v0.130.0` recorded.
- **(5)** **⚠️ CORRECTED AT PROMPT TIME — THE ORIGINAL WORDING WAS UNTESTABLE AND POINTED THE WRONG WAY.** It read *"the adoption count still returns the same numbers for a set with no unpublished edits"* — but post-backfill every row is published, so that fixture never fires the filter. It is exactly what the audit lists under *"fixtures that prove nothing."* **The discriminating test uses a source set WITH unpublished additions.** And the direction that actually breaks is the opposite one: **⚠️ `published_at` IS A SOURCE-SIDE CONCEPT, but the migration adds the column to `note_collections` and `note_collection_items`, which hold ADOPTER rows too.** Nobody publishes a learner's copy, so those rows' stamps are meaningless. `countAdoptionsByCollectionIds` counts **adopter-side** collections by `sourcePlanId` and **MUST NOT filter on `published_at`** — if it does, `v0.129.0`'s counts start reading a column that means nothing on the rows it counts.
- **(6)** **⚠️ A RENDERED CONTROL THAT IS DISABLED IS NOT A CONTROL** — if the curator UX disables `Publish update` in any state, assert what the curator can actually do in that state. This guard exists because `v0.131.0` verified an exit by its RENDER gate and missed that it was `disabled`.

**⚠️ Routing: CODEX** — migration plus service plus controller plus frontend. **⚠️ Call `advisor()` BEFORE writing the prompt** — measured as the highest-yield checkpoint in this repo, and settle owner decision 1 first.

### Shipped

- Added `V141__review_set_publication_boundary.sql`: source collection and item publication stamps plus a root finalization marker. Its backfill stamps every existing collection with its own `created_at`, every existing item with its owning collection's `created_at`, and existing public source roots' `last_update_published_at` with `created_at`.
- Curator additions remain working-only until an explicit, atomic `Publish update` action stamps the Official Review Set subtree. Drift, adoption copying, and public detail read published source rows; learner-owned library/detail reads and adoption counts remain unfiltered.
- Added the curator-only `Published` / `Unpublished changes` state and additions-only publication confirmation. P3 notification fan-out and public Updated-date UI remain deferred.

### Pressure test — two cold agents, and what they found

Tier was pre-declared at kickoff ("AT LEAST ONE SCOPED COLD AGENT"). **Two were run**, on non-overlapping halves, framed as FALSIFICATION of the implementing session's named claims — and partitioned so the **frontend/backend seam was explicitly OWNED** rather than falling between them, which is the `v0.119.0` failure. Full report: `docs/claude-findings/v0.132.0-publication-boundary-pressure-test.md`. Four defects blocked signoff; **both agents independently found the same one from opposite halves.**

- **Fixed — a Goal whose child Subject Plans were all unpublished produced a SILENTLY EMPTY adopted Goal.** The gate counted children unfiltered while the copy list beneath it filtered by publication, and `adoptGoal` never copies the root's own items. The learner got zero plans and zero notes, it became their **primary** collection, and `alreadyAdopted` made it **unrepairable by retrying**; the public adoption count incremented for it. This release had converted a loud `CollectionNotFoundException` into a silent one. The gate now reads the same filtered, owner-scoped list it copies.
- **Fixed — `listPublic` was unfiltered while `getPublic` was filtered.** Explore, Exam Hub and dashboard cards counted unpublished plans and notes that the linked page would not show. Those count queries were unfiltered before this release too; **what this release introduced is the divergence.** `childCount > 0` also sets `isGoal`, which routes Adopt to `adoptGoal` — so it fed the defect above. **⚠️ The fix filters in Java over already-fetched rows on purpose: the authenticated `list()` shares those exact queries, and an adopted row is never published, so a predicate in the query would have emptied every learner's own library.**
- **Fixed — the first publication of any PRE-EXISTING collection stamped nothing.** V141's first `UPDATE` carries no visibility predicate, so every pre-deploy row — private drafts included — was backfilled with a `published_at`, making the `published_at == null` guard false for all of them. A draft that existed at deploy, was filled in afterwards and then published would publish **nothing**. The discriminator is now `last_update_published_at`, which V141 stamps only for pre-existing public source roots. **⚠️ Not "always stamp": that would walk a `PUBLIC → PRIVATE → PUBLIC` flip's edits past the boundary.**
- **Fixed — a publish failure was invisible.** `setPublishUpdateOpen(false)` sat inside the `try`, so on failure the modal stayed open and `AppModal`'s `fixed inset-0` portal covered the page-level error Card. The curator saw a click that did nothing. **⚠️ This defeated the obvious test — `getByText(message)` passed — so the new guard asserts `within(dialog)`.** It is the `v0.131.0` disabled-control lesson one layer up: the control was enabled, the FEEDBACK was occluded.
- **Fixed — coverage gaps on added files.** `ReviewSetPublicationStatusProjection` was never produced by real Spring Data (every assertion ran against a hand-built stub over a mocked repository, and `PREPARE` cannot check alias→getter mapping); `ReviewSetUpdateNotPublishableException` had **zero** test references, so nothing proved a private, child or adopted root is rejected; and no `lib/api-*.test.ts` existed for either new endpoint despite the repo's named convention for exactly that. All three now exist. Each of the five fixes above is mutation-verified with the killing test named.

**⚠️ Correction to this release's own claim.** The delivery was recorded as asserting the backfill "against REAL migrated rows, not a hand-built fixture." **That is false as worded** — the test seeds its own rows after Flyway and replays the shipped SQL. A V141 mutation *is* caught, so it retains its value, but **pre-declared guard #4 is structurally unsatisfiable** in a Flyway-on-empty-schema harness: reading the real SQL text is the best available approximation. The guard needs rewording; the test does not need changing.

### Known limitations

- **The public view hides unpublished ADDITIONS only.** Removals, reorders and renames stay live, because `position` and `label` are single columns and a delete retains no row. Accepted at kickoff as owner decision 1.
- **There is no publication surface on the page where curators actually edit.** `addItems` refuses a collection that has children, so every topic addition happens on a child Subject Plan in the Builder — and the `Unpublished changes` indicator and `Publish update` action exist **only** on the root collection detail page. A curator can add a Subject Plan and ten topics in the Builder with nothing telling them the work is invisible to learners. **⚠️ This is the sibling of pre-declared guard 6: a control the curator never navigates to is not a control.** Not built in this release deliberately; recorded rather than discovered later.
- **A directly-published child Subject Plan is a permanent dead end.** `publishInitialCurriculum` early-returns for a non-null `parentCollectionId` while `validatePublishable` rejects neither a child nor an adopted copy, so a hand-issued visibility POST on a child yields a `PUBLIC` collection at zero public items that no control can stamp — `assertOfficialReviewSetRoot` rejects children. ADMIN-only, no UI path reaches it.
- **The item backfill uses the collection's `created_at`, not the item's**, so an item added long after its collection carries `published_at < created_at`. Nothing compares them today; it would matter only if a future release surfaces a per-item date.
- **The `@Modifying` natives lack `clearAutomatically`.** Nothing dirties those entities after the natives today, so this is latent fragility rather than a live bug.

## v0.131.0 - Inbox Polish

**Status: Released** (kicked off 2026-09-07, signed off 2026-09-08, base branch `releases/v0.131.0`, cut from `main` after `v0.130.0` merged as #1344 and tagged `3cec79bb`)

**Three fixes against the SHIPPED `v0.130.0` inbox**, from `docs/claude-plans/notification-inbox-polish.md` (owner report, 2026-09-07). **⚠️ FRONTEND ONLY — no backend, no migration, no contract change, no new notification type.**

### Planned Scope

**(1) Close on outside click, and drop the `Close` button.** The desktop panel today can be closed ONLY by its `Close` button (`notification-inbox.tsx:172`); the component's sole `addEventListener` is a `matchMedia` listener for `isMobile` (`:41`). **⚠️ THE INBOX IS THE ODD ONE OUT** — the avatar menu in the same header (`app-shell.tsx:525`), the theme toggle (`theme-toggle.tsx:125,136`) and the export dropdown (`export-dropdown-menu.tsx:43`) all already close on outside click. Copy the avatar-menu pattern, then delete the `Close` button. Add `Escape` for the desktop panel. **⚠️ MOBILE NEEDS NO CHANGE** — that path renders `AppModal`, which already handles both (`app-modal.tsx:109-111`); do NOT add a second handler.

**(2) The bell is a toggle, not a refresh.** `openInbox` (`:57-60`) does `setIsOpen(true)` + `loadInbox()` unconditionally, so clicking an open inbox re-opens and refetches. When open, close WITHOUT refetching; when closed, open and load. **⚠️ Do NOT drop load-on-open** — only the re-click while open skips the fetch. The trigger also carries a hardcoded `aria-label="Open notifications"` (`:157`) and **no `aria-expanded`**; a toggle owes both.

**(3) Hide the bell while a learner is taking a quiz.** It is already hidden, but for two modes only: the bell renders inside the header (`app-shell.tsx:643`) which is wrapped in `{!isExamFocusActive ? (` (`:628-629`), and there are exactly **two** `useExamFocusMode` consumers — Long Exam (`phase === "running"`) and Challenge Quiz **Board Exam mode only**. Extend to ordinary Challenge Quiz, Quick Review, Adaptive Practice, Interview Practice and the shared quiz. **No new mechanism** — hook, context and header gate all exist.

### ⚠️ SCOPE SET AT KICKOFF BY A VERIFIED FINDING — ITEM 3 SHIPS EXITS FIRST

**⚠️⚠️ CORRECTED 2026-09-07, HOURS AFTER THIS KICKOFF — THE FINDING BELOW WAS WRONG, AND IT IS KEPT RATHER THAN DELETED BECAUSE IT IS THE SAME DEFECT CLASS `v0.130.0` SHIPPED A WHOLE PRESSURE TEST TO CATCH.** The claim was that Quick Review's running branch has no in-page exit. **It has one.** A sticky top bar carrying a **"Leave Quiz"** button (`quick-review/page.tsx:1101`, gated on `quizSessionActive`) renders *above* the branch chain that was read. The audit that produced the claim grepped for `BackLink`, `<Link` and `router.push` and **never searched for the `onClick={() => requestLeave()}` button pattern that is the actual running-state exit in this repo** — a claim asserted from an incomplete search rather than anchored to the code that implements it.

**THE CORRECTED AUDIT — every surface, checked for the right pattern:**

| Surface | Bell during quiz | Running-state exit |
|---|---|---|
| Long Exam | already hidden | ✅ `ExamTopBar` |
| Challenge Quiz — Board Exam | already hidden | ✅ `ExamTopBar` |
| Challenge Quiz — ordinary | **visible** | ✅ inline top bar, *Leave Quiz* (`:1670`) |
| Quick Review | **visible** | ✅ *Leave Quiz* (`:1101`) |
| Adaptive Practice | **visible** | ✅ *Leave Quiz* (`:782`) |
| Interview Practice | **visible** | ✅ *Leave Practice* (`:327`) |
| Shared quiz `/quiz/[token]` | **already absent** | n/a — see below |

**⚠️ CONSEQUENCE 1: NO EXIT WORK IS OWED. All four surfaces that need focus mode already have a running-state exit**, so item 3 is the hook call alone. Guard 7 still gets asserted per surface — it is now a regression guard rather than a prerequisite.

**⚠️⚠️ CONSEQUENCE 2, AND IT IS A REAL FINDING THE PLAN GOT WRONG: DO NOT ADD `useExamFocusMode` TO THE SHARED QUIZ — IT WOULD BE A SILENT NO-OP.** `app-shell.tsx:593` returns early for `/quiz/` with a bare `<main>`, **so that route never renders the header and the bell is already absent there.** Focus mode's only consumer is the header gate, so the hook would change nothing while reading as shipped work — precisely the `v0.116.0`/`v0.117.0` shape (a behaviour changed with no test that runs it). **The plan's five-surface list is therefore FOUR surfaces.**

**⚠️⚠️ QUICK REVIEW'S RUNNING STATE HAS NO IN-PAGE EXIT, AND ADDING FOCUS MODE TO IT AS WRITTEN WOULD TRAP THE LEARNER.** Checked at kickoff rather than taken on trust: `app/study-packs/[id]/quick-review/page.tsx` renders a branch chain — loading → error → `totalQuestions === 0` → `!currentSessionId` → `isComplete` → `retry-transition` → **else, the running quiz**. All four `BackLink`s sit in NON-running branches (`:1116`, `:1146`, `:1154`, `:1319`); the running branch has none. **Focus mode hides the ENTIRE header plus the mobile tab bar** (accepted deliberately by the owner), so on that surface it would remove the only way out.

**⚠️ THEREFORE ITEM 3 IS "AUDIT AND ADD EXITS, THEN APPLY FOCUS MODE" — owner decision 2026-09-07, taken with the finding in hand.** Every one of the five surfaces has its RUNNING-state branch audited and an in-page exit added where missing, *before* the hook goes in. **⚠️ A `BackLink` elsewhere in the file does NOT satisfy this** — that is exactly what made Quick Review look safe. **⚠️ Guard 7 is asserted PER SURFACE, never once.**

### Anti-drift

**⚠️ Do NOT let the outside-click handler treat the BELL as "outside"** — the handler and the toggle would both fire on one click and the panel reopens or flickers. **⚠️ Guard 2 exists for exactly this: a fixture that clicks the page BODY passes while the bell double-fires.** **⚠️ Do NOT stop loading the inbox when it OPENS.** **⚠️ Do NOT add a second dismissal handler to the mobile `AppModal` path.** **⚠️ Do NOT give any quiz surface focus mode without an in-page exit.** **⚠️ Do NOT change badge semantics** — announcements still never inflate the number and a zero count still renders no badge element at all. **⚠️ Do NOT change read/dismiss semantics** — opening the panel still must not mark everything read, and dismissal must never alter product state. **⚠️ NO backend change, NO migration, NO new notification type, NO Stage 5/6/7 event.** **⚠️ `[CHECKPOINT — due 2026-09-19]` is TWELVE DAYS OUT and its denominator is ONE — no Learning Connections work, and no connection-request notification.** **⚠️ §8's drift-signature dedup is SUPERSEDED and must NOT be implemented** (recorded by the peer session in the Stage 1 doc; Stage 6 now belongs to `official-review-set-update-publication-boundary.md`). **⚠️ This release SUPERSEDES `shared-quiz-recipient-experience-plan.md` §10's "do NOT expand focus mode to other quiz modes"** — that deferral was withdrawn 2026-09-07 and §10 now points at the polish plan. Do not re-derive the narrowing from that file's history. No quota, entitlement or pricing change; onboarding untouched.

### Verification

**A single `advisor()` call**, per the plan's own routing — one component plus hook calls, no backend, no migration, no authorization or privacy boundary moved. **⚠️ Routing: CLAUDE CODE inline.**

**Pre-declared guards, from the plan's §4:**
- **(1)** an outside click closes the desktop panel.
- **(2)** **⚠️ clicking the BELL while open closes it EXACTLY ONCE — it must not reopen via the outside-click handler.** A fixture that clicks the page body passes while the bell double-fires.
- **(3)** **⚠️ a re-click does NOT refetch — ASSERT THE REQUEST COUNT, not the visible state.**
- **(4)** a closed → open transition still fetches.
- **(5)** `Escape` closes the desktop panel.
- **(6)** the bell is hidden during a quiz and restored on exit, including leaving mid-quiz.
- **(7)** **⚠️ every focused surface has a reachable in-page exit IN ITS RUNNING BRANCH — asserted PER SURFACE.**

**⚠️ CARRIED LESSONS.** From `v0.130.0`, three that bear directly on this release: **a `jest.mock` factory is an ALLOW-LIST** — a module the component imports and the factory omits fails silently at the call site, and `app-shell.test.tsx` was green *because* its poll threw; **confirm a mutation is PRESENT AND EFFECTIVE**, since a mutation that cannot reach its subject proves nothing; and **never revert a mutation by restoring the file from HEAD**, which discards the real fix alongside the mutant.

### Shipped

- **The desktop inbox closes on outside click and on `Escape`, and the `Close` button is gone.** It existed only because closing was otherwise impossible. **⚠️ THE REF WRAPS THE BELL *AND* THE PANEL, AND THAT IS THE WHOLE FIX** — had it wrapped only the panel, the bell would count as "outside", `mousedown` would close and the bell's own `click` would reopen **and refetch**, so one click would flicker instead of closing. Same placement as the avatar menu (`app-shell.tsx:653`). **⚠️ Mobile is untouched**: that path renders `AppModal`, which already handles backdrop and Escape, so the desktop handler is gated on `!isMobile` and the two cannot fight.
- **The bell is a toggle.** Clicking an open inbox closes it **without refetching**; opening still loads. Previously `openInbox` set open and called `loadInbox()` unconditionally — the "refresh, not toggle" the owner reported. The trigger gained `aria-expanded`, with a **stable** `aria-label` beside it: that is the convention already in this repo (`theme-toggle.tsx:174`, `export-dropdown-menu.tsx:74`), and swapping both would announce the same fact twice.
- **The bell is hidden for the duration of any quiz, not just the two exam modes.** `useExamFocusMode` now covers **ordinary Challenge Quiz, Quick Review, Adaptive Practice and Interview Practice** alongside Long Exam and Board Exam. No new mechanism — the hook, the context and the header gate all existed.
- **Two sticky in-page bars moved from `top-16` to `top-0`** (`quick-review-top-bar`, `challenge-quiz-top-bar`). The 4rem offset cleared the app-shell header, which focus mode now hides for exactly the state those bars render in; left alone they would float 4rem down with nothing above them. **⚠️ Adaptive Practice and Interview Practice deliberately got NO layout change** — their leave controls sit in ordinary non-sticky rows with no offset to correct.

**⚠️⚠️ THE KICKOFF'S OWN HEADLINE FINDING WAS WRONG, AND CORRECTING IT SHRANK THE RELEASE.** It claimed Quick Review's running branch had no in-page exit and widened item 3 to "audit and add exits first". Quick Review has a *Leave Quiz* button (`:1101`) rendered above the branch chain that was read; the audit had grepped for `BackLink`, `<Link` and `router.push` and **never searched for the `onClick={() => requestLeave()}` button that is the actual running-state exit in this repo.** **All four surfaces already had one, so ZERO exit work was owed.** Recorded rather than quietly dropped, because it is the same "claim not anchored to the code that implements it" defect `v0.130.0` shipped a whole pressure test to catch — committed as `35976007` before any code was written.

**⚠️ AND THE PLAN'S FIVE-SURFACE LIST WAS FOUR: the shared quiz was DROPPED because the hook there would be a SILENT NO-OP.** `app-shell.tsx:593` returns early for `/quiz/` with a bare `<main>`, so that route renders no header and the bell is already absent; focus mode's only consumer is the header gate. Adding it would have changed nothing while reading as shipped work — the `v0.116.0`/`v0.117.0` class exactly.

**Verification.** A single `advisor()` call, per the pre-declared tier — and it changed three decisions before any code was written: it caught that the label should stay stable (checked against the repo rather than assumed), that only two of the four surfaces have sticky bars needing the `top-0` change, and that guard 2's **event sequence is the guard**.

**⚠️ MUTATION VERIFICATION — EACH MUTANT NAMED WITH THE TEST THAT KILLED IT:**
- **Moving the ref from the wrapper onto the panel** (making the bell "outside") → killed by `closes exactly once when the bell itself is clicked while open, without refetching`, and **only** that test.
- **Restoring the unconditional `setIsOpen(!isOpen); void loadInbox()`** → killed by that same test *and* `still loads the inbox on a closed to open transition` — the pair is what pins "close does not refetch, open still does".
- **`useExamFocusMode(false)`** on Quick Review → killed by both of its focus guards, including the one that drives the quiz to completion and asserts the chrome comes **back**.
- **Reverting Challenge Quiz to `isBoardExamMode && phase === "running"`** → killed by `hides the app-shell chrome during an ORDINARY Challenge Quiz, not just a Board Exam`. **⚠️ Its fixture is deliberately an ordinary quiz: a Board Exam fixture passes under both the old and the new expression and would prove nothing.**
- **`useExamFocusMode(false)`** on Adaptive Practice and Interview Practice → one killed test each.

**⚠️ GUARD 2's TEST SHAPE IS THE GUARD, AND THIS IS THE REUSABLE LESSON.** `fireEvent.click` does **not** fire `mousedown`, and this project has **no `@testing-library/user-event`** — so a test that merely clicks the bell passes under the defect by construction. The guard dispatches `mousedown` **then** `click`, and asserts the **request count** rather than the DOM, because the panel can close and reopen inside one sequence and still read as "open".

**⚠️ THE `jest.mock` ALLOW-LIST TRAP FIRED TWICE DURING THIS RELEASE — ONCE ON THE EXISTING SUITE, ONCE ON WORK ADDED HERE.** `quick-review/page.test.tsx` mocked `exam-focus-context` with **only** `useBottomViewportClaim`, so the moment the page imported `useExamFocusMode` the call site would have received `undefined`. Then the new mock added to `adaptive-practice/page.test.tsx` omitted `useBottomViewportClaim` and broke an unrelated answer-review test — **because the CHILD `QuizAnswerReview` imports it, not the page.** A mock factory has to cover the whole subtree's use of a module, not the file's own import list. This is the `v0.130.0` carried lesson landing exactly where it was predicted to.

**Guard 7 is asserted PER SURFACE**, in the same test as guard 6 rather than separately — focus mode hides the whole header, so the in-page exit is the only remaining way out and the two facts are one fact.

**⚠️ THE POLL KEEPS RUNNING DURING FOCUS MODE, AND IT NOW HAS A GUARD RATHER THAN A REASON.** The release notes promise the count "keeps updating quietly in the background, so the bell is accurate the moment you finish." That holds because the poll is a top-level effect in `AppShell` while focus mode gates only the header *render* — but that is reasoning about effect placement, not evidence, and this repo has twice shipped a behaviour whose only support was exactly that kind of reasoning (`v0.116.0`, `v0.117.0`). `keeps polling the unread count while exam focus hides the bell` is killed by suppressing the poll when `isExamFocusActive`. **Caught at signoff by `advisor()` as a user-facing claim with no test behind it.**

**⚠️ `docs/releases/v0.130.0.md` CARRIED TWO PRESENT-TENSE CLAIMS THIS RELEASE FALSIFIED, AND THEY WERE CORRECTED THERE RATHER THAN LEFT AS HISTORY.** *"A bell sits in the header on every signed-in page"* and the panel's `Close` button both describe how the product works, not what `v0.130.0` did, and a published release-notes file is somewhere people look to find that out. **This is the sweep-by-SURFACE rule, and the first instinct — "release notes are point-in-time, leave them" — was the wrong one.**

### ⚠️⚠️ SCOPED COLD AGENT, RUN AFTER SIGNOFF — AND IT FOUND A REAL TRAP THIS RELEASE INTRODUCED

**The tier was re-decided, and the first call was the weaker one.** By the letter of the gate no trigger fired — no authorization or privacy boundary moved, one feature PR, no money/quota/production-data semantics — so a single `advisor()` call was defensible. **But the fourth trigger, *"delivery introduced a defect the same session then fixed — a measured blind-spot signal"*, does fire, and it took the owner asking to see it:** this session made **three unanchored claims** in one release (Quick Review's exit, the `v0.130.0` notes being point-in-time, effect placement as evidence for the poll), two of them caught by something other than the author. **One scoped agent, framed as falsification, on `sonnet`.**

**⚠️ FINDING 1 — CONFIRMED, AND IT IS A TRAP THIS RELEASE CREATED. Widening focus mode to `phase === "running"` left Challenge Quiz with the header hidden AND its only exit disabled for the entire submission round-trip.** Both Leave controls are `disabled={submitting}` (`:1662` Board Exam, `:1678` ordinary), and `finalizeChallengeSession` holds `submitting` true across the whole completion request while `phase` is **still `"running"`** — it flips to `"complete"` only *after* the await. So the learner had **no header and no working exit**, indefinitely if the request hung. **⚠️ Board Exam reaches this WITHOUT THE LEARNER DOING ANYTHING: its timer auto-submits on expiry.** Fixed by `useExamFocusMode(phase === "running" && !submitting)` — the header returns for exactly the window the in-page exit is unavailable, which is the cheap side of the trade. Guard: `gives the header back while submitting, because both Leave controls are disabled then`, killed by dropping the `!submitting` term.

**⚠️ IT ALSO FALSIFIED THIS RELEASE'S OWN NOTES**, which claimed *"Every one of those screens keeps its own Leave button, so you can still stop at any point."* Corrected in `docs/releases/v0.131.0.md`.

**⚠️ THE INVARIANT WAS RIGHT AND THE VERIFICATION OF IT WAS NOT.** This release stated *"focus mode may never be active in a state the exit does not cover"* and checked it by comparing the hook's gate to the exit's **render** gate on each surface. Those matched. **What went unchecked was whether the rendered exit was ENABLED** — a disabled exit is no exit, and no amount of comparing render gates would ever have surfaced it. Quick Review, Adaptive Practice and Interview Practice were re-checked and are clean: none of their Leave controls carries a `disabled` prop.

**Known limitation — pre-existing, NOT introduced here, and deliberately not fixed here.** `long-exam/page.tsx:966` carries the identical `leaveDisabled={submitting}` against a focus mode that has been active since long before this release. **⚠️ It is a live instance of the same trap**, but that file is untouched by `v0.131.0` and fixing it is a change to a surface this release did not open. It gets a row rather than a silent ride-along.

**Findings 2-7: could not disprove.** The outside-click ref genuinely wraps bell and panel; mobile cannot fight `AppModal` (the desktop effect is gated `!isMobile`, and the modal is a `document.body` portal that only mounts when `isOpen && isMobile`); no path opens the panel without loading or loads twice; the shared quiz genuinely renders no header for authenticated **or** anonymous viewers, so dropping it was correct. One cosmetic residual: because `useExamFocusMode` sets context state in an effect, the `top-0` bar can paint in the same frame as a still-visible header — **the bar is `z-20` against the header's `z-10`, so it overlaps rather than tucks under**, and it self-corrects on the next paint. Sub-frame, not reproducible in jsdom, recorded rather than chased.

**NO `[CHECKPOINT]` IS OWED, and the reason is that nothing here shipped ahead of its evidence.** All three items fix defects the owner reported against shipped behaviour, each verified directly against the code and pinned by a mutation-killed guard — there is no pre-committed rule, owner override, ambiguous read or bootstrap argument anywhere in the release. The one thing that *was* uncertain — whether the newly-focused surfaces keep a way out — was settled by reading the code before writing any, and is now a standing per-surface guard rather than a dated obligation.

**Suites: 209 frontend suites / 2,325 tests, 0 failures; `tsc` clean; 0 lint errors** (one pre-existing `react-hooks/exhaustive-deps` warning at `challenge-quiz/page.tsx:1395`, on a line this release did not touch). **Backend untouched — frontend-only release, no migration.**

## v0.130.0 - Notification Inbox

**Status: Released** (kicked off 2026-09-07, signed off 2026-09-07, base branch `releases/v0.130.0`, **cut from `releases/v0.129.0` rather than `main`** because `v0.129.0`'s release PR #1340 is BLOCKED by the `main` ruleset's `require_extra_approval_for_unattributed_changes` parameter — the `v0.120.0`/`v0.111.0` precedent, where the signoff commit rides into `main` via the release PR)

Theme: build the notification substrate, and give it the one producer that can actually fill it.

Source: `docs/claude-plans/in-app-notifications-and-review-set-adoption-signals-stage1.md`, §17 — **Stages 3 and 4 together.**

### Why 3 and 4 together, and why NOT 5, 6 or 7

**⚠️ STAGE 3 ALONE SHIPS AN EMPTY INBOX, AND PRODUCTION SAYS SO RATHER THAN INTUITION.** Read read-only 2026-09-07:

| producer | rows |
|---|---:|
| `linked_learner_relationships` ACCEPTED | **1** |
| `linked_learner_invitations` PENDING | **1** |
| `note_shares` | **0** |

Stage 3's own producers are Stage 5 (those rows) and Stage 6 (blocked). **Stage 4 is the only producer that generates content on demand**, and it is admin-controlled, so pairing them is what makes the substrate demonstrable. They share one table, one fan-out path and one verification pass, so splitting them buys no isolation and costs a second cold agent.

**⚠️ STAGE 5 IS DEFERRED FOR A REASON THAT IS NOT EFFORT: IT WOULD CONTAMINATE `[CHECKPOINT — due 2026-09-19]`.** That checkpoint decides whether the Learning Connections arc continues **at all**, and its kill criterion keys on `ACCEPTED` — **where the denominator is ONE.** Stage 5's headline event surfaces a pending connection request to the invitee, which is precisely the nudge that converts a pending invitation into an acceptance. **One notification-caused acceptance would DOUBLE the metric, and the checkpoint would then be reading the notification rather than demand.** Stage 5 also has almost nothing to notify about today (1 pending invitation, **0** note shares). **⚠️ Do NOT ship any Stage 5 event before 2026-09-19 reports.**

**Stage 6 stays BLOCKED on §8** — the drift-signature dedup correction is an unresolved owner decision, and `(recipient, adopted_collection_id, source_synced_at)` is unsound in both directions. **Stage 7 is out of scope by the plan's own table.**

### Planned Scope

**(3) Notification foundation.** `notifications` table with **a UNIQUE index on `(recipient_user_id, dedup_key)` — that index IS the idempotency guarantee, not a service-side check that can race.** Inbox read, read/dismiss, actionable count, bell in the existing header, polling, mobile sheet, and the deep-link contract. **⚠️ Notification = awareness; the owning feature = truth.** A row stores recipient, type, dedup key, deep link and timestamps and **never** request/grant/update state.

**(4) Admin What's New.** `announcements` with `DRAFT → PUBLISHED → ENDED`, **immutable content after publish** (to correct copy: end it and publish a replacement), audience `EVERYONE` / `PROFILE_TYPE` / `PLAN_TYPE`, fan-out to rows, optional expiry via the existing hourly-cleanup job pattern, and an internal-only CTA. **⚠️ Title/body/CTA are COPIED AT FAN-OUT and never re-read**, so `announcement_id` is provenance and lifecycle association, **not live content inheritance.**

**Audit of the Stage 4 delivery (implemented by a cold agent, audited here).** Every claim was re-verified rather than accepted, and **two mutations were run independently of the agent's own**:
- **Collapsing the delivery's `entityId` to a constant** — which would silently give every user only the FIRST announcement ever published, forever — is killed by `twoAnnouncementsToTheSameUserGetDistinctDedupKeysAndBothArrive` and **passes straight through the fan-out-twice test**, exactly as the agent predicted. **⚠️ THAT IS THE FINDING WORTH KEEPING: the obvious idempotency test cannot see this bug class**, and the agent wrote the test that can without being asked to.
- **Removing the `DRAFT`-only guard on edit** fails both `editAfterPublishIsRefusedAndDeliveredRowsAreUnchanged` and `anEndedAnnouncementCannotBeEditedOrPublishedAgain`.

**Independently confirmed:** `./mvnw clean install` **BUILD SUCCESS, 2278 tests**; `V140` applied by Flyway **against the real PostgreSQL 16 container**; frontend **209 suites / 2310 tests**, `tsc` clean, ESLint 0 errors. The immutability test asserts **both halves** — the refusal *and* that delivered rows are byte-identical — and the fan-out-twice test counts **database rows**, not a service return value.

**Four deviations from the prompt, all judged sound and none reverted:**
- **Expiry is evaluated ON READ, not by the cleanup job the Planned Scope named.** ⚠️ This is a correction, not drift: a job-driven expiry is wrong for as long as the job has not yet run, which the release's own error states forbid. The Planned Scope line above is superseded.
- **`publish` on an already-`PUBLISHED` announcement re-runs fan-out** rather than refusing. Fan-out is thousands of committed inserts inside one admin request; the status transition commits *before* it and the unique index makes a re-run insert zero duplicates, so **a timed-out publish is recoverable instead of stranded**. `published_at` is stamped once and a test asserts it is never re-stamped; `ENDED → publish` is still refused.
- **⚠️ THE PROMPT WAS WRONG ABOUT `PLAN_TYPE` AND THE AGENT CAUGHT IT.** The prompt asserted all three audiences are indexed columns on `users`; **plan is not on `users` at all** and resolves through `SubscriptionService.resolvePlan`. The audience query mirrors that precedence (PLUS+PRO resolves to PRO, so such a user is in the PRO audience and not the PLUS one) and is pinned by `planTypeAudienceMirrorsResolvePlanIncludingItsPrecedence`.
- **Admin-only is proven REFLECTIVELY, not as a live 403** — `standaloneSetup` does not run the security filter chain, so a request-level assertion would prove nothing. The test asserts the class-level `hasRole('ADMIN')` exists and that none of the five mapped methods overrides it. **⚠️ This is a repo-wide limitation, not one introduced here: NO `Admin*ControllerTest` in this codebase asserts a live 403**, so this is stronger than the existing precedent rather than weaker.

**Known limitation.** Fan-out runs synchronously inside the admin publish request, one insert per recipient in chunks of 500. **⚠️ THIS PARAGRAPH ORIGINALLY ENDED "Fine at current scale" AND THAT FRAME WAS WRONG — see `### Known limitations` below, rewritten after the pressure test.** The chunking bounds the log output, not the resource: `deliver` is deliberately not `@Transactional`, so each insert auto-commits, **but `open-in-view=ON` with `DELAYED_ACQUISITION_AND_HOLD` means the connection is held until the HTTP request ends regardless** — so the whole fan-out sits on one of twenty, and the blast radius is every learner request, not the admin's. **⚠️ `publish` and `fanOut` are deliberately NOT `@Transactional`** — `deliver` depends on catching `DataIntegrityViolationException`, which under an ambient transaction would mark it rollback-only and kill the whole fan-out.

### Anti-drift

**⚠️ NO Learning Connections work of any kind** — see the Stage 5 deferral above; `[CHECKPOINT — due 2026-09-19]` is twelve days out with `n=1`. **⚠️ Do NOT build ANY Stage 5, 6 or 7 event**: no connection-request notification, no note-share notification, no Review Set update sweep, no Assignment. **⚠️ Do NOT fabricate a recipient for a quiz share link** — a share link has no addressee. **⚠️ Do NOT notify on a Note becoming PUBLIC or on a generic public link.**

**⚠️ POLL, DO NOT STREAM** — there is no WebSocket/SSE infrastructure; `lib/study-pack-generation.ts` is the proven polling pattern. **⚠️ Do NOT compute adopter drift on inbox load.** **⚠️ Never one feature query per notification row** — batch, or snapshot at fan-out.

**⚠️ Announcements NEVER contribute to the numeric badge**, and **never render a literal `0`** — no badge at all at zero. **⚠️ Do NOT mark-all-read on panel open**; that discards the one signal a learner needs for a pending request. **⚠️ Dismissal must never** revoke a connection, decline a request, apply an update, unshare a note, or alter any product state. **⚠️ Do NOT add an authoritative `resolved` column.**

**⚠️ Announcement targeting is EDITORIAL, NEVER AUTHORIZATION** — entitlement stays with `FeatureGateService`. **⚠️ Do NOT allow external or protocol-relative CTA URLs**: accept a relative path, reject anything with a scheme, `//`, or a host. **⚠️ Do NOT build push, email, SMS, quiet hours or a preferences centre. Do NOT build announcement funnels, CTR dashboards or attribution.** **⚠️ Do NOT add a fifth mobile bottom tab** — the bar is already contested by `ExamFocusContext`. **⚠️ Do NOT build on `analytics_events`** — it is telemetry by declaration (`V77` dropped its user FK). **⚠️ No quota, entitlement or pricing change; onboarding untouched.**

### Verification

**ONE SCOPED COLD AGENT**, per the plan's own Verification section — this is **a new cross-user delivery surface whose only duplicate-prevention is a single unique index**, which is exactly the shape that earns an independent read. **⚠️ UPGRADED AT SIGNOFF TO THE FULL THREE-AGENT TEST — this pledge was exceeded, not left unmet.** Two further triggers appeared during delivery that were not visible when this line was written: the implementing session ended up auditing its own inline half after Codex stopped on a usage limit, and **`v0.129.0` turned out to have been signed off with no pressure test at all**, so it was folded in as a third partition. See the pressure-test section under `### Shipped`.

**⚠️⚠️ EVERY NEW ENDPOINT OWES ONE TEST THAT ISSUES A REAL REQUEST — `MockMvc` with `.contentType(MediaType.APPLICATION_JSON)` and a body, the pattern already in `NoteControllerTest`.** This release adds several endpoints, and `v0.119.0` is the measured precedent: **both of that feature's JSON POSTs sent no `Content-Type`**, Spring rejected every request **before the controller was entered**, and the feature could not make one successful request while **2,182 frontend tests passed**. A direct controller-method call is **not** a substitute — it bypasses content negotiation and passes under the defect by construction. On the client side, `lib/api-*.test.ts` pins the request shape; a component test that mocks `lib/api` proves nothing about it.

**Pre-declared guards, from the plan's discriminating list:**
- **(6) Idempotent fan-out** — the same event twice leaves **exactly one row**. ⚠️ Assert the **database**, not the service return.
- **(7) Published Announcement is immutable** — an edit after publish is refused **and delivered rows are unchanged**.
- **(8) Badge excludes Announcements** — one unread Announcement, zero actionable → **no numeric badge at all**, not a `0`.
- **(9) Notification is not authority** — accepting a request through the normal flow leaves the row untouched and the inbox still renders sensibly.
- **(10) No N+1 on inbox load** — a page of N notifications issues a **bounded** number of queries independent of N. ⚠️ **Assert the query count, not the rendered rows.**

**⚠️ CARRIED LESSONS: confirm a mutation is PRESENT before trusting a green suite** (twice-burned), and **anchor every quoted defect to the CURRENT file at kickoff** — `v0.128.0` opened on an item that had already shipped nine days earlier because the kickoff copied planned-scope prose forward.

**Routing: CODEX** — multi-system (two migrations, new entity/service/controller, plus frontend inbox and bell), which the routing table sends to Codex with a prompt. **⚠️ Call `advisor()` BEFORE writing that prompt** — measured as the highest-yield checkpoint in this repo.

### Shipped

**Stage 3 — the notification substrate.** ⚠️ **The inbox is EMPTY by construction until Stage 4 lands; that is the design, not a gap.**

- **`V139` adds `notifications` with a UNIQUE index on `(recipient_user_id, dedup_key)`, and THAT INDEX IS THE DELIVERY GUARANTEE.** `dedup_key` is deterministic — one helper builds `"<TYPE>:<entity-id>"` — and `deliver` **attempts the insert and catches `DataIntegrityViolationException`**, returning the existing row. **⚠️ There is NO `existsBy` pre-check anywhere, deliberately: two concurrent deliveries can both pass one and still duplicate.** Same shape as `NoteCollectionService:952`'s adoption race.
- **The actionable/announcement split is a property of `NotificationType`, not a call-site `if`** — `actionableTypes()` derives from the enum flag, so announcements can never inflate the numeric badge by accident. **⚠️ No badge element renders at zero** — not a `0`, not an empty circle.
- **Cross-user access returns NOT-FOUND, not forbidden**, so an endpoint never confirms someone else's notification exists.
- **Retention deletes read-or-dismissed rows past a config-backed window; unread AND UNDISMISSED actionable rows are RETAINED regardless of age** — such a row is the learner's only pointer to a pending request. **⚠️ The "undismissed" qualifier was missing from this line when it was first written and the sentence was therefore false:** the predicate is `read_at IS NOT NULL OR dismissed_at IS NOT NULL`, so a row dismissed without being read IS eligible for deletion. That is the correct behaviour — dismissing is the learner saying they are done — but it is not what the line said.
- **⚠️ A polled 401 can no longer sign a learner out.** `fetchWithAuth` gained a `handleUnauthorized` parameter **defaulting to `true`, so every existing caller is byte-for-byte unchanged**; only `getNotificationUnreadCount` passes `false`. This is a change to shared auth plumbing and is called out rather than buried.
- **Bell polls at 60s, stops while the tab is hidden, and a failed poll keeps the last known count silently** — clearing it would tell a learner they have nothing when a request is pending.

**Verification.** **⚠️ BOTH `POST` ENDPOINTS HAVE REAL `MockMvc` REQUESTS WITH `.contentType(MediaType.APPLICATION_JSON)`** — the `v0.119.0` defect class, where two JSON POSTs sent no `Content-Type` and Spring rejected every request before the controller was entered. `lib/api-notifications.test.ts` pins the request shape independently. **⚠️ That file originally also asserted the poll issues EXACTLY ONE fetch on a 401 — a test protecting a defect, corrected below.**

**⚠️ MUTATION VERIFICATION, AND ONE MUTATION FAILED TO REACH ITS SUBJECT — RECORDED BECAUSE THAT IS THE INSTRUCTIVE PART:**
- Rendering the badge at zero (`> 0` → `>= 0`) fails the no-badge-at-zero test. ✅
- **A first attempt at "mark everything read on panel open" PASSED ALL TESTS — and it was a false negative, not a missing guard.** The mutation iterated `notifications` from a **stale closure**, which is `[]` on first open, so it marked nothing. **⚠️ It compiled, it was present in the file, and it proved nothing.** Rewritten to mark read from the freshly-loaded list, it fails two tests. **This is the repo's own "the guard must reach its subject the way production does" lesson, hit from the mutation side rather than the fixture side.**

**⚠️ TWO DEFECTS WERE FOUND IN THE HANDOVER, AND BOTH WERE FOUND BY GUARDS RATHER THAN BY READING:**
- **`NotificationCleanupJob` was unregistered, and `ScheduledJobCronContractTest` failed the build for it** — the job declared a production cron but was absent from `EXPECTED_DEFAULTS`, `EXPECTED_ZONES` and the test profile's disable list. **⚠️ Its message names the real consequence: *"an unlisted cron job runs on the wall clock during the suite."*** Fixed by registering it in all three. **This is exactly what that guard exists for, and it worked with no human noticing the omission.**
- **⚠️ THE INBOX CRASHED THE ENTIRE APP SHELL WHERE `matchMedia` IS UNAVAILABLE.** `globalThis.matchMedia(...)` was called unguarded, and **this component renders inside the header — so it takes down EVERY authenticated page, not just the bell.** It surfaced as 14 failures in `app-shell.test.tsx`. **⚠️ Fixed in the COMPONENT, not by mocking it in the test** — the fault was real, not a test gap — with optional chaining and a desktop-popover fallback, plus a guard asserting the component still renders.

**Routing note.** Codex delivered the backend and part of the frontend, then stopped on a usage limit. **The backend was audited as third-party work and needed no correction.** The remainder — bell mount, count state and polling in `app-shell.tsx`, frontend tests, this documentation — was completed inline. **⚠️ The pre-declared scoped cold agent at release end is therefore doing double duty as the independent read on the inline half**, since the implementer also audited it.

**Stage 4 — Admin "What's New", the inbox's first producer.**

- **`V140` adds `announcements` with a `DRAFT → PUBLISHED → ENDED` lifecycle**, fanning out into the **existing** `notifications` table. **⚠️ NO SECOND DELIVERY PATH, no lazy eligibility evaluation, and no scheduler** — Draft → Published is manual. `ReEngagementCampaignService` was **not** extended: it has a hardcoded audience, a fixed template and email-only delivery, with no campaign entity to build on.
- **⚠️ PUBLISHED CONTENT IS IMMUTABLE, AND AN EDIT IS REFUSED — NEVER A SILENT NO-OP.** `AnnouncementNotEditableException` (409) carries the remedy in its `action`: *end this announcement and publish a replacement*. **Title, body and CTA are COPIED onto each notification row at fan-out and never re-read**, so `announcement_id` is provenance and lifecycle association, not live content inheritance. The test asserts **both halves** — the edit is refused *and* the already-delivered rows are byte-identical afterwards.
- **⚠️ IDEMPOTENCY IS PROMPT A's UNIQUE INDEX, WITH NO `existsBy` PRE-CHECK ADDED.** `dedup_key` is `"ANNOUNCEMENT:<announcementId>"` from the one existing helper. **Re-publishing RE-RUNS the fan-out rather than being refused, deliberately:** fan-out is one committed insert per recipient with no ambient transaction, so a few thousand recipients is a few thousand round trips inside one admin request — long enough to outrun a gateway timeout. The status transition commits *before* fan-out, so a timed-out publish is recoverable by pressing Publish again. **`published_at` is stamped once and never re-stamped; `ENDED` → publish is refused.**
- **⚠️ `publish`/`fanOut` ARE DELIBERATELY NOT `@Transactional`.** `deliver` depends on catching `DataIntegrityViolationException`; under an ambient transaction that violation would mark the whole transaction rollback-only and take the entire fan-out down with it. **Partial failure never rolls back deliveries already made** — one recipient's failure is counted, logged and stepped over, and a retry picks up exactly the recipients that were missed.
- **⚠️ ENDING AND EXPIRY ARE DECIDED ON READ, SO THEY BITE IMMEDIATELY** — no cleanup job stands between the admin's click and the learner's inbox. The lifecycle check is a correlated subquery inside the **single** inbox query, so the query count stays independent of row count. **⚠️ It is NOT-EXISTS-ended rather than EXISTS-live**: a notification outlives the thing it points at by design, so a vanished announcement row leaves the delivered row **visible** instead of silently eating inboxes. **This supersedes the Planned Scope line above that said "optional expiry via the existing hourly-cleanup job pattern"** — a job-driven expiry is wrong for as long as the job has not run.
- **⚠️ CTA VALIDATION IS A SECURITY CONTROL, AND IT IS A RULE, NOT AN ALLOW-LIST.** `cta_path` must be a same-origin relative path — `^/[A-Za-z0-9\-._~/]*$` plus an optional query string — rejecting any scheme, host or protocol-relative `//` prefix. An allow-list of routes would need an application release for every new legitimate destination, which is the pressure that gets a check deleted. **Validated on write AND re-checked on render** (`lib/safe-relative-path.ts`), because `next/link` renders an absolute URL as a live external anchor and a stored value is still untrusted by the time it reaches an `href`.
- **⚠️ TARGETING IS EDITORIAL, NEVER AUTHORIZATION.** Three audiences, no query builder. `AnnouncementAudienceResolver` holds no reference to `FeatureGateService` and a test asserts it; an untargeted account's rows are byte-identical after a publish. **The `PLAN_TYPE` leg mirrors `SubscriptionService.resolvePlan` including its precedence** — a user with both an active PLUS and an active PRO resolves to PRO, so they are in the PRO audience and not the PLUS one — using its own query rather than `findActiveUserIdsByPlanTypeInAndStatus`, which omits the `startAt` leg. **⚠️ `EVERYONE`/`PROFILE_TYPE` are ACTIVE accounts, deliberately NOT filtered on `emailVerifiedAt`**: delivery is in-app, unlike the email campaign path. **An audience resolving to zero users publishes successfully and delivers nothing** — a legitimate outcome, not an error.
- **Admin surface at `/admin/announcements`**, modelled on the Campaigns page: list with status, draft form, and confirmed Publish / End. **Publish confirms before firing**, and **the form states immutability-after-publish up front** so Edit disappearing is never a surprise. Announcement rows render in the **existing** inbox — no new surface, no badge-rule change.

**Verification (Stage 4).** **⚠️ ALL FIVE ENDPOINTS HAVE REAL `MockMvc` REQUESTS WITH `.contentType(MediaType.APPLICATION_JSON)` AND A BODY**, and `lib/api-announcements.test.ts` asserts the `Content-Type` header on the three POSTs and the PUT independently — the `v0.119.0` defect class. Admin-only is proven **reflectively and honestly**: `standaloneSetup` does not run the security filter chain, so the test asserts the class-level `hasRole('ADMIN')` gate exists and that **none of the five mapped methods overrides it** — it is not a live 403 assertion and is not written up as one.

**⚠️ MUTATION VERIFICATION — EACH MUTANT NAMED WITH THE TEST THAT KILLED IT:**
- Passing a **constant** instead of the announcement id as the delivery's `entityId` (which would collapse every announcement onto one dedup key and deliver only the first one, forever) → killed by `twoAnnouncementsToTheSameUserGetDistinctDedupKeysAndBothArrive`. **⚠️ The fan-out-twice test does NOT kill this one** — that is exactly why both exist.
- Dropping the `expires_at` leg of the inbox predicate → killed by `anExpiredAnnouncementStopsPresentingWithoutAnyCleanupJobRunning`.
- Dropping the protocol-relative `//` check while keeping the regex (the regex alone passes `//evil.example`, because `/` is in its character class) → killed by `rejectsAnythingThatIsNotASameOriginRelativePath` and `theProtocolRelativePrefixIsRejectedEvenThoughItStartsWithASlash`.
- Rendering `notification.ctaPath` directly instead of the validated path → killed by `refuses to render a CTA that is not a same-origin relative path`.

### Pre-signoff pressure test — FULL THREE-AGENT, and what it changed

**Tier chosen: the FULL test, not the scoped one.** Two triggers fired together — this release added a
substrate touched by two PRs plus a new admin write surface, and the implementing session also audited
its own inline half after Codex stopped on a usage limit. **`v0.129.0` was folded into the same test
because it was signed off without one**, and that decision paid for itself: three of the findings below
are in that already-released version. Three cold agents on non-overlapping halves (`v0.130.0` backend /
`v0.130.0` frontend+seam / `v0.129.0`), synthesized through `advisor()`.

**⚠️ THE HEADLINE RESULT IS THAT EVERY DEFECT BELOW WAS INVISIBLE TO A GREEN SUITE.** 2,282 backend and
2,300-odd frontend tests passed over all of it.

**Fixed in this release:**

- **⚠️ THE BADGE AND THE INBOX DISAGREED — found independently by BOTH the backend and the frontend
  agent, which is why it is first.** `countActionableUnread` filtered on `read_at` alone while
  `findVisibleInbox` also filters `dismissed_at`. An actionable row dismissed without being read left the
  inbox and **kept incrementing the bell forever** — a number the learner could neither open nor clear.
  Latent today only because Stage 5 has not shipped an actionable producer. Both queries now carry the
  same visibility predicate, plus the announcement-lifecycle leg as mirroring (announcements are
  non-actionable, so no row has both today — it is there so the two cannot drift). Guard:
  `aDismissedActionableRowStopsCountingTowardTheBadge`, killed by removing the `dismissed_at` leg.
- **⚠️ THE UNREAD POLL COULD NEVER REFRESH ITS TOKEN, SO THE BADGE FROZE AFTER 15 MINUTES IDLE.**
  `getNotificationUnreadCount` passed `retry=false` **and** `handleUnauthorized=false`. Access tokens live
  15 minutes and the poll runs every 60 seconds, so every poll after the first idle quarter-hour 401'd
  without refreshing and the badge silently stuck on its last value for the rest of the session.
  **⚠️ The "refresh storm" justification written into the code comment was never real** —
  `tryRefreshAccessToken` already dedupes concurrent refreshes, and `trackAnalyticsEvent` has used exactly
  this pairing all along. Now `retry=true, handleUnauthorized=false`; the two halves are separate
  decisions and each has its own test, each mutation-verified.
- **⚠️ A TEST WAS PROTECTING THAT DEFECT.** `does NOT retry or clear the session when the unread-count
  poll returns 401` asserted **exactly one** fetch — so the fix could not land without the test failing.
  This is the `v0.74.0` shape (a guard asserting the wrong behaviour) and is called out rather than
  quietly rewritten.
- **⚠️ A PURGED ACCOUNT LEFT ITS NOTIFICATIONS BEHIND PERMANENTLY.** `notifications` was missing from
  `AccountPurgeService.deletePersonalRows`. **⚠️ Nothing else could ever take those rows:** retention
  deliberately retains unread rows regardless of age, so the cleanup job is not a fallback. Added
  `deleteByRecipientUserId`, asserted in `AccountPurgeServiceTest`.
- **⚠️ `app-shell.test.tsx` MOCKED `@/lib/api` WITHOUT `getNotificationUnreadCount`, SO THE BADGE PATH WAS
  EXECUTED BY NO TEST — AND THE SUITE WAS GREEN *BECAUSE* THE POLL FAILED.** The call was `undefined(...)`,
  every poll threw, and the effect's own "a failed poll keeps the last count" catch swallowed it in all 18
  tests. **⚠️ A `jest.mock` factory is an ALLOW-LIST: a module the component imports and the factory omits
  fails silently at the call site, not at import.** Added the key, a missing `mockReset` (call counts were
  leaking between tests), and three tests covering poll → state → prop → badge; the wiring test is killed
  by replacing the prop with a literal `0`.
- **⚠️ `deliver` ACCEPTED AN UNVALIDATED `ctaPath`, falsifying the validator's own "ONE VALIDATOR, ONE
  LOCATION" javadoc.** Announcement create/update validate, so the guarantee held only while announcements
  stayed the sole producer — and Stage 5 will not be an announcement. `deliver` now validates as the last
  chokepoint before a link is persisted into an inbox.
- **⚠️ RE-PUBLISH IS A TOP-UP, NOT A PURE RETRY, AND BOTH THE JAVADOC AND THE FEATURE DOC SAID OTHERWISE.**
  `fanOut` re-resolves the audience **at call time**, so anyone who signed up, changed profile type or
  upgraded plan since the first publish receives it on the second. Existing recipients are deduped by the
  index. The behaviour is defensible and unchanged; the claim was wrong and is corrected in both places.
- **⚠️ "Unread actionable rows are RETAINED regardless of age" was FALSE as written** — the predicate is
  `read_at IS NOT NULL OR dismissed_at IS NOT NULL`, so a dismissed-unread row IS deletable. Corrected in
  `RELEASES.md` and `docs/features/notifications.md`.
- **Three `v0.129.0` defects, fixed here and corrected in that release's own section** — a vacuous
  `applySourceUpdate` guard whose false claim reached both `RELEASES.md` and the published release notes,
  and a completely unguarded public-detail adoption wiring (**mutation-proved: literal `0` left all 219
  tests green**). Details under `v0.129.0` → Known limitations.

**⚠️ SURFACE SWEEP — the false "creates none" claim had reached FIVE documents, and only two were in any
diff.** The repo's rule is to sweep by SURFACE rather than by diff when a release changes what a claim
means, and this is the fourth release running where that is where the finding was. Corrected at the
origin (`docs/claude-plans/in-app-notifications-and-review-set-adoption-signals-stage1.md:417`, which is
where the claim was first written and from which the vacuous guard was authored), in `RELEASES.md`, in
`docs/releases/v0.129.0.md`, and in **`docs/gpt-contexts/GPT_CONTEXT.md`, which is pasted into GPT
sessions as fact and was not in any diff.** The spent Codex prompts under `docs/codex-prompts/` carry it
too and are left alone — untracked, and superseded by the shipped code. **⚠️ The same sweep caught the
admin confirm dialog** saying re-publish *"re-runs delivery for anyone the first attempt missed. Nobody
receives it twice"* — the copy an admin reads immediately before firing an irreversible action, and
incomplete in exactly the way the javadoc was. It now names the top-up.

### Known limitations

- **⚠️⚠️ FAN-OUT HOLDS ONE OF TWENTY HIKARI CONNECTIONS FOR ITS WHOLE DURATION, AND THIS IS THE SHAPE OF
  BOTH RECORDED PRODUCTION OUTAGES.** The previous wording of this limitation reasoned only about gateway
  timeout and called it "a scaling ceiling rather than a correctness gap". **That was the wrong frame.**
  With `open-in-view=ON` and `DELAYED_ACQUISITION_AND_HOLD`, the connection is held until the HTTP request
  ends, not until each insert commits — so a publish to 400 recipients is 400 transactions and ~800
  prepared statements on **one held connection**, with the persistence context never cleared (O(N²)
  growth). **⚠️ The blast radius is the whole application, not the admin's request:** a large publish can
  starve the pool that every learner request draws from. **Not fixed here on purpose** — bounding it means
  moving fan-out off the request thread, which is a design change owed its own release and its own
  verification, not a late patch to a release that has already had this much rework. **It now owes a
  `[CHECKPOINT]` keyed on user count, not on a date** (below), because this is the one finding that will
  hurt without warning.
- **⚠️ A publish whose deliveries ALL fail still returns HTTP 200 "Published" with `delivered: 0`.** Read
  alone this is a cosmetic reporting gap; read beside the item above it is not, because that item is what
  makes total failure plausible. **The status code is left as-is deliberately** — partial failure must not
  roll back, and choosing the right non-200 semantics for "published but delivered to nobody" belongs with
  the fan-out redesign, not ahead of it. **Mitigated rather than fixed:** the admin surface now says
  *"Nobody actually received it — check the logs before assuming it went out"* when `delivered` is 0 and
  the audience was not empty, so the signal is no longer a number the admin has to notice unaided.
- **⚠️ AND THE `deliver`-SIDE CTA VALIDATION ADDED IN THIS RELEASE CAN REACH THAT HOLE THROUGH A NEW DOOR
  — recorded because the two findings are otherwise adjacent and unconnected.** `deliverOne` catches
  `RuntimeException` broadly, so if a future producer hands `deliver` a bad `ctaPath`, **every** recipient
  is counted as `skipped` and the publish reports `200 OK {delivered: 0}` rather than failing loudly. The
  broad catch is the right call for the partial-failure contract and is not being narrowed here. **Not
  reachable today**: announcement create *and* update both validate, `V140` is new so no legacy rows
  exist, and announcements are the only producer. **⚠️ It becomes reachable the moment Stage 5 adds a
  producer that does not validate on write** — which is exactly why the chokepoint was added.
- **⚠️ ONE NEW GUARD RESTS ON A FIXTURE PRODUCTION HAS NOT CONFIRMED.**
  `applyingSourceUpdateCreatesAnAdoptionOfANEWLYAddedChildSubjectPlan` — the test anchoring the
  `v0.129.0` correction — builds the newly-added child as **PRIVATE** (the helper's default), and
  `applySourceUpdate` copied it. Either Goal children are PUBLIC in production, making this the very
  "fixture no code path can produce" shape this release documents elsewhere, or PRIVATE children really
  are copied into an adopter's library and a collection shell crosses an ownership boundary on the update
  path. **The guard kills its mutation either way, so the correction stands** — but which case it is, is
  unresolved, and `docs/claude-plans/v0.130.0-owner-production-checks.sql` Q3 asks production. (The
  *notes* inside are separately gated: `applyPlacementAddition` throws for a non-public source note.)
- **⚠️ `NotificationCleanupJob.run()` is invoked by no test**, and its two exception classes are untested.
  The scheduled-cron contract test proves it is *registered*; nothing proves it *deletes*.
- **⚠️ THE NOTIFICATION INTEGRATION TESTS HAND-WRITE THEIR H2 DDL AND CAN SILENTLY DRIFT FROM `V139`/`V140`.**
  This is the "fixture no code path can produce" anti-pattern at schema level, and it weakens every
  confirmation those tests provide. Neither new JPQL query is executed against PostgreSQL by any test.
  **⚠️ The existing `NativeQueryPostgresIntegrationTest` does not cover this** — it prepares *native*
  queries, and these are JPQL.
- **⚠️ `app/admin/announcements/page.tsx` is 460 lines with ZERO tests.** Admin-only and low blast radius,
  which is why it is recorded rather than fixed, but it is the largest untested file the release added.
- Smaller, each real and each recorded rather than fixed: a `datetime-local` round-trip shifts a draft's
  expiry instant; a failed mark-read restores from a snapshot rather than functionally, so it can
  resurrect a row dismissed server-side in between; the poll runs on routes that render no bell; the admin
  form stays in edit mode after publishing; and the test named
  `restores the row and the badge when a dismiss fails` asserts the row but never the badge.
- **⚠️ AGENT 3's PRODUCTION INDEX CHECK WAS BLOCKED AND IS THEREFORE UNVERIFIED AGAINST PRODUCTION.** The
  `v0.129.0` index claim is confirmed **in-repo only**. Two read-only SELECTs are handed to the owner in
  `docs/claude-plans/v0.130.0-owner-production-checks.sql` rather than being reported as verified.
