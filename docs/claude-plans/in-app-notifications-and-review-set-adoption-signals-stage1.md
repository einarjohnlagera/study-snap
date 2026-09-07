# In-App Notifications + Official Review Set Adoption Signals — Stage 1 Audit

**Status:** **STAGE 1 APPROVED FOR STAGES 2–5 — ready to prepare the Stage 2 implementation prompt.**
**⚠️ STAGE 6 IS BLOCKED on one unresolved architecture correction (§8).** No implementation, no
migration, no admin surface built.
**Date:** 2026-09-07, **revised 2026-09-07** to incorporate owner tightening.
**Scope:** two capabilities audited together, delivered as **Stages 2–7, not one release.**

---

## 1. Executive judgment

**The two halves are in opposite states, and that should drive sequencing.**

**Adoption signal is nearly free and already correct.** The database *already* guarantees the
property the product wants:

```sql
CREATE UNIQUE INDEX idx_note_collections_owner_source_plan
    ON note_collections(owner_user_id, source_plan_id)
    WHERE source_plan_id IS NOT NULL;
```
`V76__collection_visibility_and_plan_source.sql:8-11`

**One learner can hold at most one collection per source — enforced in Postgres.** So the adoption
count is `COUNT(*) WHERE source_plan_id = :id`, with **no `DISTINCT` needed and no inflation
possible**. Both adoption paths check for an existing adoption first and return it rather than
minting a second (`adopt:923`, `adoptGoal:955`, via `findByOwnerUserIdAndSourcePlanId`).

**Notifications are greenfield.** There is **no notification entity, service, inbox, event bus,
websocket, SSE or unread primitive anywhere in the repo.** Everything must be built.

**⚠️ Three repo contradictions. Two were resolved in the tightening; one remains and blocks Stage 6.**

| Contradiction | Status |
|---|---|
| No "published revision" exists (§2) | **Resolved** — behind-episode semantics adopted instead |
| "Campaigns" is not reusable infrastructure (§3) | **Resolved** — build separately, reuse the dedup *shape* only |
| **The sync baseline does not advance reliably enough to dedup on (§8)** | **⚠️ OPEN — blocks Stage 6** |

**Two prerequisites were checked as instructed. One passed, one failed:**

- **Legacy / null baselines — PASSES.** `V134`'s backfill stamps `source_synced_at` on every
  adoption, and the drift code already documents the rule: *"A null baseline is not drift: we never
  recorded a position to compare."* Legacy adoptions can under-notify, never spam.
- **⚠️ `applySourceUpdate` advancing the baseline — FAILS PARTIALLY.** See §8.

**And one prerequisite is already satisfied that the tightening assumed was outstanding:** the
adopted-collection **"Update available" surface already exists** — `collection-detail-page-client.tsx`
carries `sourceUpdate` state, a `source-update` mutation kind and `sourceUpdateChangeText` rendering.
**§18's "banner first" ordering is already met; Stage 6 does not have to build it.**

---

## 2. ⚠️ Contradiction A — there is no "published revision" to notify on

§9 and §11 assume a *meaningful published curriculum revision* exists, and propose the dedup
identity `recipient + source Review Set + published revision`.

**No revision concept exists.** `v0.116.0` shipped a **sync-baseline** model instead
(`V134__additive_review_set_sync_provenance.sql`):

```sql
ALTER TABLE note_collections
    ADD COLUMN source_title_at_sync VARCHAR(150),
    ADD COLUMN source_parent_id_at_sync UUID,
    ADD COLUMN source_position_at_sync INTEGER,
    ADD COLUMN source_synced_at TIMESTAMPTZ;
```

with the deliberate design, quoted from the migration header:

> *"drift compares the source fact saved here with the source fact now, so learner customization can
> never be mistaken for an upstream change."*

**Consequence: "behind" is a per-adopter computed comparison, not a version number.** There is no
revision id to key a notification on, so **§11's dedup identity cannot be built as written.**

**The honest contract name is "one notification per BEHIND EPISODE"** — never "one per published
revision", because no revision exists. Intended behaviour:

> learner is synced → source drifts → **notify once** → source drifts again before they act →
> **no second notification** → learner applies → baseline advances → future drift is eligible again.

**⚠️ The obvious dedup key does not work. See §8 for why, and for the smallest correction.**

**⚠️ Scale consequence (§57):** because "behind" is computed rather than stored, finding eligible
adopters is an **O(adopters) sweep** calling drift per adopted collection. At current scale that is a
scheduled job, not a per-request read. **Do not compute it on inbox load.**

## 3. ⚠️ Contradiction B — "Campaigns" is not reusable announcement infrastructure

§13 asks whether Campaign infrastructure can be reused. **Audited: there is no Campaign entity, no
migration, no content model, no scheduling, no audience builder and no lifecycle.**

`ReEngagementCampaignService` is **161 lines with two public methods** — `countEligible()` and
`send()`. It is a single hardcoded email campaign:

- audience is a hardcoded query — `findByStatusAndEmailVerifiedAtIsNotNullAndMarketingEmailsEnabledTrue(ACTIVE)`;
- content is a fixed template; the type is a hardcoded enum value `RE_ENGAGEMENT_2025`;
- delivery is **email only**; the admin UI is one button (`admin/campaigns/page.tsx`).

**Verdict: BUILD SEPARATELY.** There is nothing to extend — merging would mean inventing the
infrastructure and then bolting an unrelated email campaign onto it.

**⚠️ But one primitive IS worth reusing — its idempotency pattern.**
`emailLogRepository.existsByUserIdAndEmailType(userId, type)` (`:124`) is exactly the
"has this recipient already been told about this thing" check §29 asks for, proven in production.
**Reuse the shape, not the table.**

---

## 4. Current repo architecture (§50)

| Capability | Exists? |
|---|---|
| Notification model / table / service | **No** — searched entities and services |
| Inbox, unread counter, badge primitive | **No** |
| Event bus / domain events / outbox | **No** |
| WebSocket / SSE / push | **No** |
| Announcement / What's New concept | **No** |
| Campaign entity or lifecycle | **No** — only `ReEngagementCampaignService` |
| Email infrastructure | **Yes** — `EmailService`, `EmailTemplateService`, `email_log`, unsubscribe links |
| Scheduled jobs | **Yes** — `GenerationRecoveryJob` (10 min), `BulkGenerationResultCleanupJob` (hourly), `SubscriptionExpiryEmailScheduler`, `BillingUsageResetJob` |
| Analytics events | **Yes** — but `analytics_events` is **telemetry by declaration** (`V77` dropped its user FK); **not a product-state source** |
| Client polling precedent | **Yes** — `lib/study-pack-generation.ts` (3 s interval, quiet-window + hard-cap backstop) |
| App shell / mobile tab bar | **Yes** — `app-shell.tsx`, with `ExamFocusContext` already able to hide chrome |

**Reusable:** the scheduled-job pattern, the `email_log` idempotency shape, the polling helper, the
shell. **Everything notification-specific is new.**

## 5. Notification product model (§4, §51)

**Notification = awareness. The feature = truth.** Concretely:

- a notification row stores **recipient, type, dedup key, deep link, created_at, read_at** — and
  **never** request/grant/update state;
- rendering resolves current state at read time from the owning feature, so an accepted request or an
  applied update shows as resolved without the notification being the authority;
- read/dismiss state changes nothing outside the inbox.

**Recommended core model — one table, two producers:**

`notifications(id, recipient_user_id, type, dedup_key, title, body, cta_label, cta_path,
announcement_id NULLABLE, created_at, read_at, dismissed_at)` with a **unique index on
`(recipient_user_id, dedup_key)`** — that index *is* the idempotency guarantee (§29), not a service
check that can race.

## 6. Transactional vs Announcement persistence (§18, §53)

**Recommendation: fan out both. Do not build lazy eligibility in v1.**

At current scale (~950 notes for the largest curator; low-thousands users) an announcement to
everyone is a few thousand rows — trivial for Postgres, and it makes the inbox a single indexed read
per user rather than a per-request eligibility evaluation. §18 explicitly permits this.

**Keep `announcement_id` on the row** so an announcement can later be expired or edited centrally and
so a future lazy-eligibility model is not blocked. **⚠️ Do not build a separate announcement-delivery
path** — one inbox, one table, one read.

## 7. Admin Announcements (§12–§17, §53)

**Minimum v1 fields:** `title`, `body`, `cta_label`, `cta_path` (internal only), `audience`,
`status ∈ {DRAFT, PUBLISHED, ENDED}`, `published_at`, `expires_at NULLABLE`.

**Audience — constrained to what the repo can answer cheaply and safely:** `EVERYONE`,
`PROFILE_TYPE`, `PLAN_TYPE`. All three are indexed columns on `users`.
**⚠️ No SQL/query-builder targeting** (§16). Later system-defined segments ("users with generated
Notes", "users with adopted Review Sets") are derivable but should wait for evidence.

**CTA safety (§15): internal paths only, validated against an allow-list of known routes.**
**⚠️ No external URLs** — an admin-authored link rendered in every user's inbox is a phishing surface
if it can point anywhere.

**⚠️ Scheduling is NOT cheap here** — there is no existing campaign scheduler to reuse (§3), so
"scheduled publish" means a new job. **Recommendation: DRAFT → PUBLISHED manually in v1**, with
`expires_at` handled by the existing hourly-cleanup job pattern.

## 8. Unread / read / dismiss and badge semantics (§20–§23)

| State | v1? |
|---|---|
| `unread` / `read` | **Yes** — `read_at` |
| `dismissed` | **Yes** — `dismissed_at`, hides from the inbox only |

**Badge recommendation — the simplest coherent split (§21):**

- **Personal/actionable** (connection request, progress request, note/quiz shared, Review Set update)
  → **contributes to the numeric badge**;
- **Announcements** → appear as `NEW` in the inbox but **do not inflate the numeric badge**.

**Rationale:** an announcement that nobody acts on would otherwise leave a permanent "3" on the bell,
which trains users to ignore it — and that would degrade the actionable half.

**Clearing:** mark individually on open; **⚠️ do not mark-all-read on panel open** — that silently
discards the one signal a learner needs for a pending connection request.

**⚠️ Dismissal must never** revoke a connection, decline a request, apply an update, unshare a note,
or alter any product state (§23).

## 9. Deep links and stale state (§24, §25)

| Notification | Destination |
|---|---|
| Learning Connection request | `/linked-learners` |
| Progress access request | `/linked-learners` (the learner's own toggle) |
| Note shared | the shared note |
| Quiz shared | `/quiz/{token}` |
| Review Set update | the adopted collection's update-review surface |
| Announcement | its validated internal `cta_path` |

**Stale handling: resolve at render, never trust the row.** A request already accepted shows as
accepted; an applied update shows as up to date; an unshared note degrades to a plain "no longer
available" state rather than a 404. **⚠️ Every deep link must tolerate the target being gone** — the
notification outlives the thing it points at by design.

## 10. Notification event matrix (§52)

| Event | Recipient | Source of truth | v1? | Actionable | Badge | Deep link | Dedup identity |
|---|---|---|---|---|---|---|---|
| Learning Connection request | invitee | `linked_learner_invitations` | **Yes** | Yes | Yes | `/linked-learners` | `invitation_id` |
| Progress access request | learner | *(does not exist yet)* | **No — blocked** | Yes | Yes | `/linked-learners` | `relationship_id + scope` |
| Note shared | recipient | `note_shares` | **Yes** | Yes | Yes | note | `note_share_id` |
| Quiz shared | recipient | `quiz_share_links` | **No — see below** | Yes | Yes | `/quiz/{token}` | `share_link_id` |
| Official Review Set update | adopter | drift vs `source_*_at_sync` | **Slice E** | Yes | Yes | adopted collection | `adopted_collection_id + source_synced_at` |
| What's New announcement | audience | `announcements` | **Yes** | No | **No** | internal `cta_path` | `announcement_id + user` |
| Quiz Assignment received | assignee | *(future)* | No | Yes | Yes | assignment | `assignment_id` |
| Assignment completed | assigner | *(future)* | No | No | Yes | assignment | `assignment_id + completion` |
| Routine self quiz completion | — | — | **Never** (§6) | — | — | — | — |

**⚠️ Two rows need calling out.**

**Progress access request has no event to fire from.** The *"Ask to view progress"* affordance does
not exist — verified: `requestGrant`/`requestAccess`/`grantRequest` have **zero hits** repo-wide, and
it is Phase 1 of `supporter-progress-visibility-audit.md`. **This notification cannot ship before
that feature does.** It is the strongest argument for sequencing that work alongside Slice D.

**Quiz shared is weaker than it looks.** A share link is delivered by the sender through an external
channel to anyone — there is **no recipient** until someone opens it. `quiz_share_links` has an
`owner_user_id` but no addressee. **Recommendation: exclude from v1** — it belongs with Assignment,
which is where a known recipient first exists.

## 11. Adoption data-model audit (§32, §54)

Answering §32's ten questions directly:

| # | Question | Answer |
|---|---|---|
| 1 | Does every adoption preserve source identity? | **Yes** — `setSourcePlanId(source.getId())` on all paths (`:1820`, `:1877`, `:2323`) |
| 2 | Does onboarding use the same path? | **Yes** — same `adopt`/`adoptGoal` service methods |
| 3 | Does Explore use the same path? | **Yes** |
| 4 | Can one user create multiple collections from one source? | **No — DB-enforced** (partial unique index) |
| 5 | Is re-adoption prevented? | **Yes** — both methods return the existing adoption; a lost race is caught as `DataIntegrityViolationException` |
| 6 | Deleted/archived adoptions? | Deleted rows vanish → no longer counted. **⚠️ No archive state exists to distinguish** |
| 7 | Legacy adoptions without provenance? | Possible — `source_plan_id` is nullable and predates `V76` |
| 8 | Can admin/curator copies contaminate? | **Yes, in principle** — a curator self-copy also sets `source_plan_id` |
| 9 | Does copying a learner plan count? | Only if it sets `source_plan_id` to the Official source — verify the copy path |
| 10 | Does adopting a child Subject Plan count toward the parent? | **No** — the child's `source_plan_id` is the child source (`:2323`) |

**§42 satisfied structurally:** `applySourceUpdate` mutates the existing adopted collection; it
creates no row, so **applying an update cannot increment the count.**

## 12. Exact adoption-count definition (§55)

> **`SELECT COUNT(*) FROM note_collections WHERE source_plan_id = :officialCollectionId`**
> — optionally `AND owner_user_id <> :officialOwnerUserId` to exclude curator self-copies (§32.8).

**No `COUNT(DISTINCT owner_user_id)` is required** — the partial unique index makes count-of-rows
identical to count-of-learners. This is the strongest form of §33 ("one learner = at most one
adoption"): it is a database invariant, not a query convention.

**Current, not lifetime (§34).** Deleting the adopted collection removes the row, so the count is
*current legitimate adopters* by default — the owner's stated preference, achieved with no extra
work. **⚠️ Lifetime is not available** and would require a new event record; do not add one.

**§35:** there is **no archive state** on collections — only present or deleted. So
"archived = still adopted" is not expressible today, and the honest semantics are **present = adopted,
deleted = not adopted.**

**§44 legacy:** rows predating `V76` may have a null `source_plan_id` and are simply **excluded** —
they undercount rather than mislead. **⚠️ Do NOT reconstruct provenance from titles, names or note
overlap** (§59). Report the undercount; do not fix it.

## 13. Adoption-count UX and surfaces (§37–§40, §56)

**Show on:** Explore / discovery cards **(highest value)**, and Official Review Set detail.
**Do not show on:** onboarding selection — social proof there distorts the recommended hierarchy
during the one flow where NoteLib is making the recommendation (§39's own caution).

**Formatting:** exact source of truth; compact display (`1.2K adopted`) on cards, fuller wording on
detail (§38).

**⚠️ Small-count threshold is a genuine owner decision requiring production data (§37).** The
distribution is not in the repo or a dev seed, and the production database is **read-only for Claude**
— that query is the owner's to run. A `.sql` can be prepared. **Do not pick a threshold blind.**

**§40: no ranking in this slice.** Popularity is not curriculum quality, and Official curation stays
primary.

**§41 freshness: not v1**, and there is a trap — `updated_at` moves for internal mutations, so it must
not be shown raw. `source_synced_at` is per-adopter, not per-source, so it is also wrong for this. A
meaningful "Updated" date has **no honest source today.**

## 14. Privacy analysis (§36)

**The count is aggregate and must stay so.** No adopter identities, lists, avatars, "X and 4 others",
or adoption feed. The recommended query returns a scalar; nothing about it can leak an identity.

**For notifications:** a Review Set update notification is sent **to the adopter about their own
collection** — it discloses nothing about other adopters. **⚠️ Never surface "N other learners also
updated"** — that converts aggregate social proof into activity disclosure.

## 15. Mobile implications (§47, §48)

Reuse existing primitives: **`AppModal` already renders as a bottom sheet on mobile** and a centred
dialog on desktop (`app-modal.tsx:163`), with `max-h-[85dvh]` and a scrollable body.

**Recommendation:** desktop popover, mobile **full-screen sheet** — the inbox is a list, and a popover
over a 390px viewport is the pattern §47 warns against. Bell in the existing header
(`app-shell.tsx`); **⚠️ the bottom tab bar is already contested** — `ExamFocusContext` hides it, and
the shared-quiz plan proposes hiding it during quizzes. **Do not add a fifth bottom tab.**

Adoption count on cards: compact only (`1.2K adopted`).

## 16. Scale and performance (§57)

**Notifications** — inbox is `WHERE recipient_user_id = ? AND dismissed_at IS NULL ORDER BY created_at DESC`
with a limit; unread count is a `COUNT(*) WHERE read_at IS NULL`. Both need an index on
`(recipient_user_id, read_at)`. Trivial at current scale. **⚠️ Poll, do not stream** — there is no
WebSocket/SSE infrastructure, and the existing polling helper is the proven pattern.

**⚠️ Review Set update eligibility is the only expensive piece** — drift is computed, so finding
who is behind is O(adopters). **Run it as a scheduled job** (the repo has four such jobs to model
on), never on request.

**Adoption count — one real gap.** The existing index is
`(owner_user_id, source_plan_id)`; its **leading column is `owner_user_id`**, so a query filtering on
`source_plan_id` alone **cannot use it**. An adoption count therefore needs **a new index on
`source_plan_id`** — one line, and the only schema change the adoption slice requires.

**Recommendation: query-time count (§43 option A)**, no denormalized counter, no event-maintained
counter. Correctness first; revisit only if Explore aggregation measurably suffers.

## 8. ⚠️ Stage 6 prerequisite FAILURE and the smallest correction

**Prerequisite 1 — does `applySourceUpdate` reliably advance `source_synced_at`? PARTIALLY, and the
partiality is exactly the hazard.**

```java
for (NoteCollectionEntity sourcePlan : inspection.sourcePlans()) {
    NoteCollectionEntity adoptedPlan = inspection.adoptedBySourcePlan().get(sourcePlan.getId());
    if (adoptedPlan == null || !appliedPlanIds.contains(adoptedPlan.getId())) {
        continue;                       // ⚠️ baseline advances ONLY for plans actually applied
    }
    ...
    adoptedPlan.setSourceSyncedAt(now);
}
```
`NoteCollectionService:2358-2367`

Two consequences:

1. **A plan whose changes were skipped keeps its old baseline.** `SKIPPED_NOT_PUBLIC` changes cannot
   be applied at all, so after a partial apply the learner is still behind on those — with an
   unchanged `source_synced_at`.
2. **The adopted ROOT's baseline is not advanced by apply.** Of the seven `setSourceSyncedAt` sites,
   two are adoption (`:1824`, `:1881`), four are per-plan/per-item inside apply, one is an item path.
   For a **Goal** adoption the root goal's baseline is stamped at adoption and not refreshed.

**So `(recipient, adopted_collection_id, source_synced_at)` is unsound**: keyed on the root it never
changes, permanently suppressing every future notification; keyed per-plan it re-fires on the next
sweep for skipped plans, which is spam.

### Smallest correction — no migration to `note_collections`, no revision architecture

**Dedup on `(recipient_user_id, adopted_collection_id, drift_signature)`**, where `drift_signature` is
a stable hash of the **pending change set `getSourceUpdate` already returns** (`ReviewSetUpdateResponse`:
`additionsAvailable`, `notesAdded`, `subjectPlansAdded`, `skippedCount`, `changes`).

Why this is correct where the timestamp is not:

| Situation | Behaviour |
|---|---|
| Source drifts again before the learner acts | signature changes → **one** new notification, not silence |
| Learner applies everything | pending set empties → next drift is a new signature → eligible again |
| Learner applies partially | remaining set changes → **one** notification about what is left |
| Changes are `SKIPPED_NOT_PUBLIC` and unappliable | pending set unchanged → **same signature → no spam** |

It lives entirely in the `notifications.dedup_key` column — **the curriculum tables are untouched**,
and no revision/version concept is invented (§9, §17).

**⚠️ Stage 6 must not start until this is agreed.** It is the one place the audit could not confirm
the tightening's assumption.

**Prerequisite 2 — legacy / null baselines: SATISFIED, no work owed.** `V134` backfills
`source_synced_at` on every adoption, and null baselines are explicitly treated as *not drift*
(`:2404-2410`). Legacy rows under-notify rather than mis-notify.

---

## 9. Settled adoption-count semantics

**Display wording (§1): `1.2K adopted`.** **⚠️ Do NOT write "N learners adopted this"** — the repo
cannot prove every counted owner is semantically a learner, and **`ProfileType` must not be used to
filter adoptions to justify the word.**

**Definition:**

> **`SELECT COUNT(*) FROM note_collections WHERE source_plan_id = :officialId AND owner_user_id <> :officialOwnerId`**

- **No `DISTINCT`** — the partial unique index makes rows-per-source identical to learners-per-source.
- **Exclude the Official source owner only (§2).** No Profile Type, plan, or role filters.
- **Current, not lifetime (§34)** — deleting the adopted collection removes the row.
- **⚠️ Parent and child counts stay independent (§3).** A child Subject Plan's `source_plan_id` points
  at the **child** source (`:2323`), so child adoptions do not roll up. **Never sum descendants into a
  parent's social-proof number.**
- **⚠️ CORRECTED 2026-09-07 — THIS LINE IS THE ORIGIN OF A FALSE CLAIM THAT REACHED FOUR OTHER DOCUMENTS.** It read: *"Applying an update mutates rows and creates none — it cannot increment (§42)."* `applySourceUpdate` DOES create rows carrying a `sourcePlanId`, via `createSubjectAddition`. What is true is narrower: applying an update never re-counts an existing adopter against the set they already hold. A newly-added child Subject Plan legitimately gains an adopter. The `v0.129.0` guard written from this line used a leaf fixture and never entered the creating loop; a `v0.130.0` pressure test disproved the claim and both halves are now tested.
- **Legacy null-provenance rows are excluded** and under-count. **⚠️ Never reconstruct provenance from
  titles, names or note overlap.**

## 10. Production-data checks required before public display (§4)

**⚠️ Both are the owner's to run — the production database is read-only for Claude.** A `.sql` can be
prepared on request.

1. **Adoption-count distribution across Official Review Sets** — sets the threshold.
2. **Provenance completeness** — share of adopted collections with non-null `source_plan_id`.

**⚠️ If provenance completeness is materially low, public display should WAIT.** A visibly wrong
undercount on a flagship Official set is worse than no number. **The count is exact regardless;
the threshold is display policy only (§5).**

## 11. Announcement source of truth and immutability (§8 of the tightening)

**Locked: draft Announcements are editable; PUBLISHED Announcement content is immutable.**

- After publish, Admin may **end/expire** only.
- To correct copy materially: **end the old Announcement and publish a replacement.**
- **⚠️ `announcement_id` on a notification row is provenance and lifecycle association — NOT live
  content inheritance.** Title/body/CTA are **copied at fan-out** and never re-read, so delivered rows
  stay historically coherent.

**Targeting is editorial, never authorization (§10 of the tightening).** `PROFILE_TYPE` and
`PLAN_TYPE` select *who is likely to care*. **⚠️ Announcement targeting must never become feature
gating** — entitlement stays with `FeatureGateService` and each feature's own rules.

**CTA validation (§11 of the tightening) — a same-origin path rule, not a hardcoded route list.**
Accept a value that is a **relative path** (`^/[A-Za-z0-9\-._~/]*$` plus optional query), reject
anything with a scheme, `//`, or a host. That blocks external and protocol-relative destinations
without needing an application release for each new legitimate destination.

## 12. Badge semantics (§13 of the tightening)

- **Actionable unread → numeric badge.**
- **Announcements → never contribute to the number.**
- **⚠️ Never render a literal `0`** — no badge at all when the count is zero.
- **Optional and cheap: a subtle dot when actionable = 0 but unseen Announcements exist.** One boolean
  in the same query that already computes the count. **Ship it only if it stays that cheap.**

**State fields (§14 of the tightening):** `read_at` = awareness; `dismissed_at` = inbox visibility;
the owning feature = workflow authority. **⚠️ Do NOT add a generic authoritative `resolved` column** —
resolution is derived from the source feature when useful.

## 13. Current-state resolution without N+1 (§15 of the tightening)

**Semantic goal kept; the live-resolve-everything implementation dropped.** Per type:

| Type | Strategy |
|---|---|
| Learning Connection request | **Batched** — one query over the referenced invitation ids for the page |
| Note shared | **Snapshot copy** of the note title at fan-out; validity resolved **on click** |
| Announcement | **No lookup** — content is copied and immutable (§11) |
| Review Set update | **Batched** over the adopted collection ids on the page |

**⚠️ Never one feature query per notification row.** A notification does not need a live *Resolved*
badge; **it needs a deep link that fails gracefully** — the target may be accepted, revoked, unshared
or already applied, and each must land on a sensible surface rather than a 404.

## 14. Revised event set (§20–§22 of the tightening)

| Event | Stage | Why |
|---|---|---|
| **Learning Connection request** | **5** | Real recipient, real source row (`linked_learner_invitations`) |
| **Note shared — explicitly, to a named user** | **5** | `NoteShareEntity` carries `granteeUserId` **and** `relationshipId`, so the recipient is real. **⚠️ A note becoming PUBLIC, or a generic public link, creates no share row and must never notify** |
| Progress access request | **later** | **The feature does not exist** — `requestGrant`/`requestAccess`/`grantRequest`: zero hits. Plugs in when it ships |
| Quiz shared | **excluded** | A share link has **no addressee**; do not fabricate a recipient. *"New practice from Maria"* belongs to Assignment |
| Official Review Set update | **6** | Blocked on §8 |
| What's New Announcement | **4** | — |
| Assignment received / completed | **7** | — |
| Routine self-activity | **never** | §6 |

## 15. Retention recommendation (§23 of the tightening)

**Proportional, config-backed, reusing the existing hourly-cleanup job pattern
(`BulkGenerationResultCleanupJob`).**

| Row | Policy |
|---|---|
| Read **or** dismissed transactional notification | delete after a config-backed window (suggest 90 days) |
| Unread actionable notification | **retain** — it is the learner's only pointer to a pending request |
| Ended/expired Announcement rows | delete after the same window; **must stop reading as new immediately on expiry** |
| Announcement definition row | retain — it is the admin record |

**⚠️ Notification is not workflow history.** Deleting a notification must never touch the invitation,
share, grant or adopted collection it referred to. **⚠️ Build no archival infrastructure** — one
delete query on a schedule.

## 16. Announcement analytics (§24 of the tightening)

**Minimal only.** `published_at`, recipient count at fan-out, and the read counts already implicit in
`read_at`. **⚠️ No conversion funnels, CTR dashboards, attribution or campaign analytics.** This is a
product-communication tool, not a campaign platform.

---

## 17. Delivery roadmap — Stages 1–7

| Stage | Content | Migration | Gate |
|---|---|---|---|
| **1 — Audit & architecture** | this document | — | **Complete** |
| **2 — Adoption signal** | index on `source_plan_id`; exact count excluding the Official owner; **batched** Explore counts; scalar detail count; compact display | **1 index** | **Ready.** Display gated on §10's production checks |
| **3 — Notification foundation** | `notifications` + unique `(recipient_user_id, dedup_key)`; inbox; read/dismiss; actionable count; bell; polling; mobile sheet; deep-link contract | **Yes** | Ready |
| **4 — Admin What's New** | `announcements`; Draft/Published/Ended; **immutable after publish**; EVERYONE / editorial ProfileType / Plan; fan-out; optional expiry; internal CTA; excluded from the numeric badge | **Yes** | After 3 |
| **5 — First transactional events** | Learning Connection request; **explicit named** Note share | No | After 3 |
| **6 — Review Set update notifications** | low-frequency scheduled sweep; **behind-episode** dedup | No | **⚠️ BLOCKED on §8** |
| **7 — Future integrations** | Assignment received/completed; learning-attention; push/email; preferences | — | Out of scope |

**⚠️ Stage 2 is independent of every other stage and is the cheapest real user-visible win — one
index, one query, two surfaces. Do not fold it into the notification work.**

**Explore must batch (§6 of the tightening):** one grouped query —
`SELECT source_plan_id, COUNT(*) … WHERE source_plan_id IN (:visibleIds) GROUP BY source_plan_id` —
**never one count per card.** The detail page may use a scalar. **This belongs in Stage 2's
verification, not just its description.**

**Sweep cadence (§19 of the tightening):** curriculum changes are infrequent — **daily is ample**;
hourly is unjustified. **⚠️ Never compute adopter drift on inbox load.**

## 18. Genuine remaining owner decisions

1. **⚠️ Approve the §8 drift-signature dedup correction.** **Blocks Stage 6 only.** The alternative —
   a `last_update_notified_at` column on `note_collections` — is a migration that leaks a notification
   concern into the curriculum table; **not recommended.**
2. **Adoption display threshold**, after §10's distribution query. **Blocks Stage 2's display half,
   not its count.**
3. **Whether public display waits** if provenance completeness proves materially low (§10).

**Settled, not owner questions:** the adoption formula (DB-enforced); parent/child independence;
Official-owner exclusion; Campaigns not reusable; announcement immutability; announcements excluded
from the numeric badge; quiz-shared excluded; progress-request deferred; retention policy.

## 19. Anti-drift checklist

- **⚠️ Do NOT say "N learners adopted this"**, and do not filter adoptions by `ProfileType`.
- **⚠️ Do NOT roll child Subject Plan adoptions into a parent's count.**
- **⚠️ Do NOT issue one count query per Explore card.**
- **⚠️ Do NOT add a denormalized or event-maintained adoption counter** before measurement.
- **⚠️ Do NOT edit a PUBLISHED Announcement's content** — end it and publish a replacement.
- **⚠️ Do NOT let Announcement targeting become feature gating or entitlement.**
- **⚠️ Do NOT allow external or protocol-relative CTA URLs.**
- **⚠️ Do NOT add an authoritative `resolved` field**, or let read/dismiss change workflow state.
- **⚠️ Do NOT resolve current state with one feature query per notification row.**
- **⚠️ Do NOT render a numeric `0` badge**, and do not let Announcements inflate the number.
- **⚠️ Do NOT call the Review Set contract "one per published revision"** — no revision exists.
- **⚠️ Do NOT invent revision/version architecture for notifications** (§9, §17).
- **⚠️ Do NOT compute adopter drift on inbox load**, and do not sweep at high frequency.
- **⚠️ Do NOT notify on a Note becoming PUBLIC or on a generic public link** — named shares only.
- **⚠️ Do NOT fabricate a recipient for a quiz share link.**
- **⚠️ Do NOT ship Progress-request notifications before the request feature exists.**
- **⚠️ Do NOT expose adopter identities, avatars, or an adoption feed.**
- **⚠️ Do NOT infer legacy adoption from titles, names or note overlap** — report the undercount.
- **⚠️ Do NOT rank Official Review Sets by adoption count.**
- **⚠️ Do NOT build push, email, SMS, quiet hours, or a preferences centre.**
- **⚠️ Do NOT build Announcement funnels, CTR dashboards or attribution.**
- **⚠️ Do NOT build Assignment**, and do not build on `analytics_events` (telemetry by declaration).
- **⚠️ Do NOT add a fifth mobile bottom tab.**
- **⚠️ No quota, entitlement or pricing change; onboarding untouched.**

---

## Verification

**Stage 2: a single `advisor()` call.** **Stages 3–6: one scoped cold agent** — a new cross-user
delivery surface whose only duplicate-prevention is one index.

**Pre-declared discriminating guards:**

1. **Adoption cannot inflate** — adopt, delete, re-adopt: the count returns to its prior value.
2. **Applying an update does not change the count** — assert either side of `applySourceUpdate`.
3. **Child does not roll up** — adopting a child Subject Plan leaves the parent's count unchanged.
4. **Official owner excluded** — the curator's own copy does not increment.
5. **Explore is batched** — rendering N cards issues **one** count query, not N. *Assert the query
   count, not the rendered numbers.*
6. **Idempotent fan-out** — the same event twice leaves exactly one row (assert the DB).
7. **Published Announcement is immutable** — an edit attempt after publish is refused, and delivered
   rows are unchanged.
8. **Badge excludes Announcements** — one unread Announcement, zero actionable → **no numeric badge**.
9. **Notification is not authority** — accepting a request through the normal flow leaves the row
   untouched and the inbox still renders sensibly.
10. **No N+1 on inbox load** — a page of N notifications issues a bounded number of queries
    independent of N.
