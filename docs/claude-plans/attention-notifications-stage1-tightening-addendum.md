# Attention & Notifications — Stage 1 Tightening Addendum

**Status:** Answers the owner's tightening addendum. **Amends `attention-notifications-email-expansion-stage1.md`; does not replace it.**
**Date:** 2026-09-09
**Author:** Claude Code
**Scope note:** No implementation. `v0.134.0 — Notification Foundations` (Stage B) is already kicked off and its scope is **unchanged** by this document except for one javadoc phrasing fix (§3).

> Every claim below is proven from code at `file:line`. **No production read was needed** — this is a
> design-correctness question, not a denominator question. The one production fact relied on
> (`notifications` is empty) was re-verified read-only at the `v0.134.0` kickoff, 2026-09-09.

---

## 1. Is the Review Set behind-episode dedup design correct today?

# **NO.** It is a permanent, silent suppression bug, and it fails closed.

The audit proposes `REVIEW_SET_UPDATE:<adoptedCollectionId>` under the existing permanent unique index
`idx_notifications_recipient_dedup` on `(recipient_user_id, dedup_key)` (`V139__notifications.sql:17`).
**That key can only ever deliver ONE notification per learner per adopted set, for the life of the row.**

The mechanism, from `NotificationService.deliver` (`:47-54`):

```java
try {
    return toResponse(notificationRepository.saveAndFlush(notification));
} catch (DataIntegrityViolationException duplicateDelivery) {
    return notificationRepository.findByRecipientUserIdAndDedupKey(delivery.recipientUserId(), dedupKey)
            .map(this::toResponse)
            .orElseThrow(() -> duplicateDelivery);
}
```

A second, genuinely later Official update computes **the same key**, hits the index, is caught, and
**returns the OLD row** — already read, already dismissed, carrying stale copy. No exception surfaces.
No log line marks it. The learner is never told, and the publish reports success.

**⚠️ The index has no lifecycle predicate.** `dismissed_at` and `read_at` do not participate in it, so
dismissing a notification does **not** free the key. Setting `dismissed_at` removes the row from the
inbox and leaves the key permanently occupied.

**⚠️ Retention does not rescue it, and the way it half-rescues it is worse than not rescuing it.**
`deleteReadOrDismissedBefore` (`NotificationRepository:100-105`) requires **both** `created_at < threshold`
**and** `(read_at IS NOT NULL OR dismissed_at IS NOT NULL)`. So the real behaviour is:

| Learner state | Behaviour on a later publish |
|---|---|
| Read or dismissed the first row, **< 90 days** | **Silently suppressed** |
| Read or dismissed the first row, **≥ 90 days** | Row deleted by the job → notification lands again |
| **Never opened the bell** | **Suppressed forever** — the row is never eligible for deletion |

That is not a design. It is a suppression bug wearing a 90-day timer, and the third row is the common case
for exactly the learners the feature exists to reach.

**⚠️ `v0.134.0`'s item 5 does not fix it either.** `REVIEW_SET_UPDATE` is `LEARNING_SYSTEM`, which is
badge-eligible, and item 5 expires only **non-actionable** unread rows. The permanent-suppression row is
on the retained side of that split by design.

---

## 2. The minimum corrected design

Two concerns are conflated in the current proposal. Separate them and each has a cheap, existing answer.

| Concern | Question it answers | Mechanism |
|---|---|---|
| **Event identity / idempotency** | "Is this the same publication I already delivered?" | the dedup key + the unique index — **unchanged, still the sole mechanism** |
| **Episode suppression** | "Is this learner already holding an open *behind* signal?" | a batch filter in the producer, **before** fan-out |

### 2.1 Identity: put the published revision in the key

```
REVIEW_SET_UPDATE:<sourceCollectionId>:<lastUpdatePublishedAt as epochMilli>
```

**⚠️ The key is source-side, and that is the whole correction.** The superseded §8 design failed because
it keyed on the **learner** side (`source_synced_at`), which `applySourceUpdate` advances only for plans
actually applied and never for an adopted Goal's root — unsound in both directions. `lastUpdatePublishedAt`
is **one value on the source root**, and it is already exactly the discriminator we need:

```java
Instant lastPublishedAt = sourceRoot.getLastUpdatePublishedAt();
if (before.getUnpublishedChanges()) {          // NoteCollectionService:754-760
    Instant now = Instant.now();
    ...
    collectionRepository.markReviewSetUpdatePublished(collectionId, now);
    lastPublishedAt = now;
}
```

**Re-press idempotency is already built and we inherit it for free.** A re-press with no unpublished
changes does not advance the stamp, so the key is byte-identical and the unique index absorbs it.

`<sourceCollectionId>` rather than `<adoptedCollectionId>` because the recipient is already in the index,
and `V76__*.sql:8-10`'s partial unique index on `(owner_user_id, source_plan_id) WHERE source_plan_id IS
NOT NULL` makes one-adoption-per-learner-per-source a **database invariant**. Using the source id makes the
key **identical for every recipient in a fan-out** — one string computed once, not one per adopter.

This satisfies four of the six requirements outright:

- ✅ retrying the same publication is idempotent — the stamp does not move, the index catches it;
- ✅ a genuinely later update **can** notify — the stamp advances, the key is new, the row is new;
- ✅ dismissing changes no curriculum truth — the key is derived from source state the notification never writes;
- ✅ applying an update stays authoritative in Review Set state — `source_synced_at` is untouched by this;
- ✅ no event sourcing, no workflow engine, **no new column, no migration** — the stamp already exists (`V141`).

### 2.2 ⚠️ Two writers stamp `lastUpdatePublishedAt`, so "the stamp advanced" is NOT a valid trigger

`markReviewSetUpdatePublished` has two call sites: `publishReviewSetUpdate` (`:758`) and
`publishInitialCurriculum` (`:1849`).

**The rule this forces — state it in the prompt:** the producer fires from **the `publishReviewSetUpdate`
call site**. The stamp is only the **discriminator**, never the trigger. A future session wiring the
producer to "the stamp changed" would also fire it on a set's first-ever publication.

**That second writer cannot currently coincide with an adopter, and this is provable rather than assumed.**
Its only call site is guarded by `previousVisibility != PUBLIC && saved.getLastUpdatePublishedAt() == null`
(`:1011`), so it runs **exactly once per set, ever** — and it runs at the moment a set first becomes
public, when nothing can yet have adopted it. The in-code comment confirms a PUBLIC→PRIVATE→PUBLIC flip
does not re-enter it, because the field is non-null by then.

### 2.3 Suppression: an episode filter in the producer, not in audience resolution

Requirement *"multiple publishes while learner is already behind must not spam them"* is **not** an
identity property, and the revision key alone does not deliver it: three publishes while a learner is
behind produce three keys and three rows.

**One batch query in the producer, between audience resolution and fan-out**, drops recipients who already
hold an **undismissed** `REVIEW_SET_UPDATE` row for that source. One query per publish, not per recipient.

**⚠️ It does NOT belong in `AnnouncementAudienceResolver`, and putting it there would break a guarded
contract.** That class is **editorial, never authorization** — it holds no `FeatureGateService` reference
and a test asserts it. "Which adopters already hold an open behind-episode row" is per-recipient
notification state, not an editorial audience.

**⚠️ On the `exists`-before-insert prohibition — written down so a future reader does not reconstruct it.**
`CLAUDE.md` forbids an `exists` check before a notification insert *as the idempotency mechanism*, because
two concurrent deliveries of **one event** could both pass it and duplicate awareness. This filter is a
different thing and must be labelled as one:

- the unique index **remains the sole identity mechanism** and is unchanged;
- the filter compares **different** events (different revisions), never the same one;
- its worst-case race outcome is **one extra notification about a genuinely newer revision** — it fails
  open to more awareness, never to duplicate awareness of a single event.

### 2.4 ⚠️ HARD RULE: the copy must not carry a count

Under episode suppression the single open row **is** the learner's only signal across N publishes, so
*"3 new notes"* is actively false by the second publish — the row outlives the state it described. Copy
must be generic (*"This Review Set has been updated"*). This is also what keeps R10 intact: **no live
target resolution per inbox row**, which is the property that makes the inbox N+1-free by construction.

### 2.5 Implementation guard

Compute the key from `epochMilli`, always from the **persisted** value. `TIMESTAMPTZ` is microsecond-precision
and in-memory `Instant` is nanosecond-precision, so a key built from the in-flight `Instant.now()` and one
built from a later read of the same row can differ.

---

## 3. Exact tightened Stage B scope (`v0.134.0`)

**Unchanged.** Items 1–3 + 5, DDL-free; item 4 and all DDL out; no producer; `NotificationType` keeps
exactly its two values. Already committed at kickoff (`ea13b8ed`).

**One amendment, from the addendum's §1:** the transitional `ACTION_REQUIRED` javadoc must justify itself as
**backward-compatible taxonomy transition**, *not* as "retained so badge behaviour stays exercised."
Tests exercise the production model; they do not determine it. **This is a phrasing change to one javadoc
line and to `RELEASES.md`/`CLAUDE.md`, not a scope change.**

---

## 4. Exact D1 scope (first producer)

**Official Review Set update only. In-app only. Nothing else.**

- Fires **only** from the `publishReviewSetUpdate` call site — never raw source drift, never a stamp diff (§2.2).
- Recipients: adopters of that source root. Deep link `/collections/{adoptedCollectionId}` — the learner's own copy.
- Key: `REVIEW_SET_UPDATE:<sourceCollectionId>:<lastUpdatePublishedAt epochMilli>` (§2.1).
- Episode-suppression filter between audience resolution and fan-out (§2.3).
- Generic copy, no counts (§2.4).
- New type `REVIEW_SET_UPDATE`, category `LEARNING_SYSTEM`, **replacing** the transitional `ACTION_REQUIRED`.
- **No email** (§5 of the addendum; R1 unresolved).
- ⚠️ `deliver()` must not be called from inside a transaction — it is deliberately non-transactional so a
  dedup-index violation cannot mark a whole fan-out rollback-only.
- ⚠️ **Blocked until §2 is implemented.** Shipping D1 on the current key ships the suppression bug.

### Observation gate before D2
Creation volume, duplicate-conflict rate, badge correctness, delivery failures, query latency, table growth,
connection-pool behaviour. **D2 (interpersonal producers) is gated on D1 proving the substrate — not on a
usage threshold** (§2 of the addendum).

---

## 5. Tests that change because of this addendum

**Stage B (`v0.134.0`) — one change only:** the `ACTION_REQUIRED` javadoc rationale. **No test changes.** The
committed list stands, including the category **partition** assertion, which remains the one assertion that
cannot pass against `main`.

**D1 — these are new, and four exist only because of this addendum:**

| # | Test | Why |
|---|---|---|
| 1 | Re-press Publish with no changes → **exactly one** row per adopter | idempotency; the stamp does not move |
| 2 | **⚠️ Publish → learner dismisses → curator publishes a genuinely later update → a NEW row lands** | **the discriminating test for this whole document.** It **fails** on `REVIEW_SET_UPDATE:<adoptedCollectionId>` and passes on the revision key |
| 3 | **⚠️ Publish → publish again while the learner is still behind and has NOT dismissed → still exactly one row** | episode suppression; distinct from 1 — different revisions, not a retry |
| 4 | **⚠️ Dismiss → assert the adopted collection is still behind and nothing was applied** | notification = awareness, product state = truth |
| 5 | **⚠️ Copy carries no count** | asserts the §2.4 hard rule; the row outlives the state |
| 6 | Add many notes to an Official set without publishing → **zero** notifications; Publish once → one row per eligible adopter | the publication boundary |
| 7 | End-to-end from **curator presses Publish**, not from `deliver(...)` | every existing `ACTION_REQUIRED` test hand-builds a state no code path produces |
| 8 | Real request — `MockMvc` with `.contentType(MediaType.APPLICATION_JSON)` and a body | a direct handler call passes under a binding defect by construction (`v0.119.0`) |
| 9 | Badge isolation: 1 announcement + 0 actionable → badge **0**; add 1 → badge **1** | |
| 10 | Stale target — revoke before opening → graceful, no permission leak | |

---

## 6. Sections of the Stage 1 plan to amend

Edits to `attention-notifications-email-expansion-stage1.md`. **Do not rewrite the document.**

| § | Amendment |
|---|---|
| **§17.2** *behind-episode semantics* | **Replace.** Current design is the bug in §1 above; substitute §2 of this file. |
| **§20 Stage D** | Replace the dedup key; add the episode filter, the trigger rule (§2.2) and the no-count rule (§2.4). |
| **§21.2** *Deferred on denominator* | **Reclassify** Learning Connection request, named Note share and future Progress-access request as **"Approved notification event class; implementation deferred until the first producer proves the substrate."** Remove the framing that they lack product evidence — the denominator sequences rollout, it does not decide notification-worthiness. |
| **§21.1** *Rejected outright* | **Move** delayed learning re-engagement out of *rejected* into **"Currently unnecessary / deferred — reconsider only if evidence shows returning learners struggle to resume despite Dashboard guidance."** Per-like / per-view / per-copy / per-session / per-correct-answer / self-action rejections stay permanent. |
| **§15 Deep-link matrix** | **Do not contract `IMPACT_MILESTONE` to `/progress`.** Its target becomes the canonical route of the dedicated Impact surface (likely `/impact`) if that ships first. Preserve: **Progress = how I am learning; Your Impact = how my shared knowledge helps other learners.** Do not merge them. |
| **§21.1 / §36 / D5** | Unchanged — **Course/Program applicability is not a subscription.** Add: discovery requires an explicit attention relationship (Follow program / Follow Review Set) and then prefers digest over per-Note delivery. |
| **§3 / §17.3 / Stage E** | Unchanged and still blocked on R1. Preserve the three-way channel distinction (account-security-billing / learner-configured reminders-digests / promotional). **Do not route existing retention email through `marketing_emails_enabled`.** Name the six-part pre-Stage-E design as its own audit. |
| **Verification section** | Retitle to make explicit it is a **Stage D** list — seven of its ten items presuppose a producer that Stage B does not ship. |
| **§9 doctrine** | Carry forward verbatim. Notifications = *what meaningfully changed while I was away*; Dashboard = *what should I learn next*; notification = awareness, product state = truth; in-app informs, email must justify interruption; alive without becoming social media. |

---

## 7. Genuine owner decisions

Only two. Everything else above is settled by code or by the addendum.

| # | Decision | Recommendation |
|---|---|---|
| **A1** | ✅ **SETTLED 2026-09-09 (owner): YES, RE-NOTIFY.** Recommendation accepted. **Episode-close semantics.** A learner who **dismisses without applying** is still behind. Should the next publish re-notify them? | **YES — re-notify.** Dismiss means *"I have seen this"*, not *"stop telling me"*; a later publish is genuinely new information, and the alternative re-introduces suppression-until-applied, which is the bug in §1 wearing a different hat. ⚠️ The consequence is worth stating plainly: a learner who dismisses and never applies gets one notification per publish. |
| **A2** | ✅ **SETTLED 2026-09-09 (owner): SHIP IT WITH D1.** Recommendation accepted — **and accepted against a production read that WEAKENED it, which is worth recording rather than smoothing over** (see the note below). **Does episode suppression ship WITH D1, or does D1 ship key-only?** | **Ship it with D1.** Without it, D1's headline behaviour is one notification per publish per behind learner — the spam the addendum's own requirement forbids. It is one batch query, and adding it later means designing it against live rows instead of none. |

### ⚠️ Production read taken 2026-09-09, AFTER the decisions were framed — it changes two inputs

**(1) The audit's "≤30 recipients" for Stage D is STALE. `LET Comprehensive Review` now has 42 adopters.**
D2's justification cited the ≤30 figure. The executor (core 1 / max 2) is unaffected, but **any prompt
or doc repeating "≤30" is repeating a number that is no longer true.** Current adopter counts: LET 42,
ALE 30, PNLE 15, CPALE 8, Civil Engineering 1.

**(2) ⚠️ EXACTLY ONE UPDATE PUBLISH EXISTS IN THE PRODUCT'S ENTIRE HISTORY, AND IT HAPPENED ON
2026-09-09.** Four of the five public source roots have `published_at == last_update_published_at`,
meaning they have never published an update at all. `LET` published one at `01:50:47` — forty seconds
after the 88-note Education family tagging, and hours after `V141` deployed. **That is the `v0.132.0`
publication boundary being used for real, and it is the first evidence the workflow is live.**

**⚠️ This weakens A2's case rather than strengthening it, and the owner took the recommendation anyway.**
At one publish per set ever, the duplicate-notification spam suppression prevents is **hypothetical
today**. The surviving argument is the one about timing, not volume: suppression added later is
designed against a populated `notifications` table, and the need would be discovered by having already
sent duplicates to 42 people. **Do not re-derive this as "suppression was obviously necessary" — it
was a judgement call made with the counter-evidence in view.**

**Not decisions — already settled:** the revision key (§2.1, code-proven), the trigger rule (§2.2,
code-proven), suppression's location (§2.3, an existing guarded contract decides it), and the no-count copy
rule (§2.4, forced by §2.3).

---

## Housekeeping

**⚠️ This file needs a Backlog Index row in `ROADMAP.md`** — added in the same commit rather than left for
the next kickoff's step 8, which has caught a planning artifact seven cycles running.
