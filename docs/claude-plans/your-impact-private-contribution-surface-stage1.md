# Your Impact as a First-Class Private Contribution Surface — Stage 1 Audit

**Status:** Audit only. No code changes made; none proposed for this session to execute.
**Date:** 2026-09-09
**Author:** Claude Code (NoteLib Feature Planner)
**Deliverable boundary:** audit + handoff. Implementation and the Codex prompt belong to the Release
Implementor session.

> Production reads were **READ-ONLY `SELECT`s** against `notelib-db-prod`
> (`dpg-d6tvb8fkijhs73fda4m0-a`), per `CLAUDE.md`. No writes executed, none proposed.

---

## Executive judgment

The brief carries two premises. **One is true and now measured. One is false.** And the most serious
problem in this feature is named by neither.

### ✅ Premise 1 — "poorly discoverable" — TRUE, and now quantified

`KNOWLEDGE_IMPACT_DASHBOARD_VIEWED` has fired **7 times in the lifetime of the product** (4 with an
attributed user). The brief's §4 claim that Impact is "technically available but poorly discoverable"
is not a hunch — the surface has been opened seven times, ever. **The IA argument is sound.**

### ❌ Premise 2 — "separating it may reduce Public Profile query cost" (§22) — FALSE

Impact was **never part of the Public Profile payload.** It is a separate endpoint
(`GET /creator-impact/me`), fetched client-side in an effect gated on `isOwner`
(`public-profile-page-client.tsx:240-250`), with the server scoping to `@AuthenticationPrincipal` and
accepting **no `userId` parameter at all** (`CreatorImpactController.java:19`). A public visitor
triggers no impact query and receives no impact bytes.

**So moving the page saves Public Profile nothing.** The IA move is worth doing for discoverability;
it is not a performance change, and presenting it as one would be wrong.

> ⚠️ **This is the second brief in a row whose central performance/refactor premise is false** — the
> `v0.133.0` brief asked to generalise an Engineering-specific shortcut that was already generic. The
> pattern is worth naming: the premise fails in the same way each time, by describing coupling that
> the code does not actually have.

### ⚠️ The finding neither premise names, and the real headline

**`frontend/app/settings/page.tsx:276-296` calls `getCreatorImpact()` in a `Promise.all` on every
Settings page load — and uses the result for exactly one thing:**

```ts
setHasPublicNotes(Boolean(impact.value?.notes.length));
```

A boolean. To evaluate `notes.length > 0`, Settings pulls the **entire** creator-impact payload. For
the account that actually uses this feature that is **1,587 note records** — each with title, learner
count, view count and copy count — produced by three separate `IN (:noteIds)` grouped queries over a
1,587-element list, plus a headline aggregate, **on a 256 MB Postgres behind a 20-connection pool that
has already failed twice from unbounded reads** (`2026-09-04`, `2026-09-05`; the recorded lesson is
*"bound the work instead"*).

This is a live defect on the owner's own account today, not a scaling hypothesis. **It is the single
highest-value thing in this document, and it is independent of where the page lives.**

### The denominator, and why it yields two verdicts rather than one

| Owner | Role | Public notes | Learners helped |
|---|---|---|---|
| 1 | **ADMIN** | **1,587** | **56** |
| 2 | USER | 2 | 0 |
| 3 | USER | 1 | 0 |
| 4 | USER | 1 | 0 |

**Exactly one account in production has any impact, and it is the owner's own admin account** — the
"56 distinct learners helped" in the brief is that account. It is tempting to apply the
denominator-based deferral used for note likes and Learning Connections in the notification audit.
**That move does not transfer here**, and the difference matters: the single user is the owner, who is
asking for the feature, and 56 learners helped is *measured impact*, not a speculative event stream.

So there are **two verdicts, not one**:

1. **The IA move (dedicated `/impact` route) is low-value but genuinely cheap.** Ship it if the owner
   wants it. It fixes a real discoverability problem (7 lifetime views) and costs little.
2. **The unbounded payload is a real defect and should be fixed regardless of whether the page
   moves.** It is not contingent on the IA decision.

**Recommended order: fix (2) first.** It is smaller, it is the only production risk here, and it makes
(1) strictly easier — a paginated/filtered endpoint is what the new page should consume from day one.

---

## A. Current architecture

### A.1 Where it lives

| Layer | Location |
|---|---|
| Route | **None of its own.** Rendered inside `/public/profile/...` |
| Component | `frontend/components/public/public-profile-page-client.tsx:85-180` (`CreatorImpactSection`) |
| Client fetch | `frontend/lib/api.ts:5988` — `getCreatorImpact()` → `GET /creator-impact/me` |
| Controller | `backend/.../controller/CreatorImpactController.java:19` — `getMine(@AuthenticationPrincipal)` |
| Service | `backend/.../service/CreatorImpactService.java` (93 lines) |
| DTO | `CreatorImpactResponse(long distinctLearnersHelped, List<NoteImpact> notes)` |
| Analytics | `KNOWLEDGE_IMPACT_DASHBOARD_VIEWED` fired on render (`:99-102`) |

### A.2 How Public Profile loads it

Lazily and owner-only. `isOwner = Boolean(profile?.isCurrentUser)` (`:216`); the effect at `:240-250`
returns early and clears state when `!isOwner`, otherwise calls `loadImpact()`. A request-id ref
(`impactRequestIdRef`) guards against out-of-order responses — **this is already carefully written.**

### A.3 Do public visitors receive any of the payload?

**No — and there are two independent controls, either of which suffices:**

1. **Client:** the section is not rendered and the fetch is not issued for a non-owner
   (`public-profile-page-client.test.tsx:277` asserts `getCreatorImpact` is *not* called).
2. **Server:** `/creator-impact/me` takes no `userId`; the identity comes from the JWT principal.
   There is no parameter to tamper with.

**§24's requirement is therefore already met**, and `/impact?userId=…` is not merely blocked — it is
**unrepresentable**. That is the stronger property and it must be preserved by any move.

### A.4 ⚠️ The second consumer the brief does not know about

`frontend/app/settings/page.tsx:281` — `getCreatorImpact()` inside the page-load `Promise.all`,
wrapped in `.then(ok)/.catch(fail)` so a failure degrades rather than breaks the page. Consumed at
`:296` as `setHasPublicNotes(Boolean(impact.value?.notes.length))`, and `impactCheckFailed` drives a
fallback message at `:981`.

**This directly violates the brief's §21** ("Impact analytics should load only when the user opens Your
Impact"). It is also the concrete instance of §21's own remedy: *"expose/use a lightweight aggregate
summary rather than loading the entire Impact page payload."*

### A.5 Current mobile behaviour

`grid gap-3 sm:grid-cols-2` — one column below `sm`, two above. Cards use `line-clamp-2` on the title.
**No horizontal overflow risk**, no hover dependency. The problem is not layout, it is length: 1,587
single-column cards.

---

## B. Metric semantics

### B.1 "Distinct learners helped" — the exact definition

From `NoteRepository.java:274-285`:

```sql
count(distinct copy.ownerUserId)
from NoteEntity source
join NoteEntity copy on copy.copiedFromNoteId = source.id
join QuickReviewSessionEntity session on session.noteId = copy.id
where source.ownerUserId = :creatorUserId
  and source.visibility = PUBLIC
  and copy.copiedFromPublic = true
  and session.status = COMPLETED
  and session.completedAt is not null
```

**In words: a distinct user who copied one of your public notes AND completed at least one quiz
session on their copy.**

| Question (§7) | Answer | Evidence |
|---|---|---|
| What qualifies as "helped"? | A **completed** quiz session on a **copy** of a **public** note. Viewing does not count. Copying alone does not count. | `:281-283` |
| Do anonymous users count? | **No** — a copy requires an authenticated owner. | `copy.ownerUserId` is non-null by schema |
| Is owner activity excluded? | ✅ **Yes, structurally.** `NoteService.copyNote:392-397` — on a self-copy the `isOwner` branch sets `copiedFromNoteId = null` and `copiedFromPublic = FALSE`, so a self-copy can never satisfy the join. **Verified in production: `self_copies_from_public` = 0.** | `NoteService.java:392-397` |
| Does repeat study dedupe? | ✅ **Yes** — `count(distinct copy.ownerUserId)`. Ten sessions by one learner count once. | `:275` |
| Does one learner studying several of your notes count once in the headline? | ✅ **Yes** — the distinct is over the whole creator's set. | `:275-279` |
| How does per-note differ? | Per-note (`:258-269`) groups by `source.id`, so the same learner counts in each note they studied. **Per-note counts can therefore sum to more than the headline.** | `:259` |

### B.2 ✅ The current explanatory copy is CORRECT

> *"Each learner is counted once in this headline, even if they studied more than one of your notes.
> Per-note learner counts can therefore add up to more than the headline total."*

This matches the queries exactly. **Preserve it verbatim** — it is doing real work, and §7's
instruction to verify before changing copy is satisfied: no change is warranted.

### B.3 Views — ⚠️ raw, anonymous, and un-attributable

`AnalyticsEventRepository:346-355` counts `count(e.id)` for `PUBLIC_NOTE_VIEWED` — **raw events, not
distinct people.**

Production, all event rows:

| Event type | Total | With `user_id` |
|---|---|---|
| `PUBLIC_NOTE_COPIED` | 5,586 | **5,586** |
| `KNOWLEDGE_IMPACT_DASHBOARD_VIEWED` | 7 | 4 |
| **`PUBLIC_NOTE_VIEWED`** | **25,262** | **0** |

`AnalyticsService.trackEvent` persists `user_id` correctly (proved by the copy row above), and
`NoteController:846` resolves it properly as `user == null ? null : user.userId()`. So this is not a
bug — **every public-note detail view in production has been anonymous traffic.**

**Consequence:** views can never be deduplicated per person, by construction of the data. The brief's
§6 instruction *"do not make views the hero metric"* is therefore not just taste — **the metric is
incapable of supporting that role.**

⚠️ **REQUIRED BY THE ADDENDUM (§9), not optional:** the UI label must be **"page views"** (or an
equally precise event-count term). The current bare *"views"* invites reading it as people, which the
data cannot support. This is a copy change, not a metric change.

### B.4 Copies

`NoteRepository:249-256` — `count(n)` of notes with `copiedFromNoteId = source.id` and
`copiedFromPublic = true`. A **raw copy count**, not distinct copiers. Since `copyNote:357-359` dedupes
non-owner copies per `(ownerUserId, sourceNoteId)`, one user cannot mint multiple copies of the same
note — so **in practice this equals distinct copiers**, but it is not enforced by the query. Worth a
sentence in the feature doc rather than a change.

### B.5 Study sessions

**Not currently surfaced.** `countDistinctLearnersHelpedByCreatorUserIdSince` exists (`:287-301`) and is
unused by this service — it feeds the Knowledge Impact digest email. **Do not add a session-count
metric in this work**; it is not in the DTO and would need a new query.

---

## C. Query / performance

### C.1 Query shape — no N+1, but unbounded

`CreatorImpactService.getMine` issues **exactly 5 queries regardless of note count:**

1. `findByOwnerUserIdAndVisibilityOrderByUpdatedAtDesc` — all public notes
2. `countDistinctLearnersHelpedByCreatorUserId` — headline
3. `countDistinctLearnersHelpedBySourceNoteIds(noteIds)` — grouped
4. `countPublicNoteEventsByTypeAndNoteIds(VIEWED, noteIds)` — grouped
5. `countCopiedPublicNotesBySourceNoteIds(noteIds)` — grouped

**✅ There is no N+1** — the brief's §22 worry does not apply, and the existing implementation deserves
credit for that. It builds `Map<UUID, Long>` lookups (`:59-92`) rather than per-note calls.

### C.2 ⚠️ But every parameter is unbounded

| Bound | Present? |
|---|---|
| Pagination on the note list | ❌ none |
| Limit on the `IN` list | ❌ none — **1,587 UUIDs in production** |
| Filter to notes with impact | ❌ none |
| Cap on response size | ❌ none — 1,587 `NoteImpact` records |

Three `IN (1587 UUIDs)` grouped queries plus an aggregate, per call. **This is the same shape as the
2026-09-05 outage** (`an anonymous /notes/public request loading the entire public catalog inside its
transaction`), whose recorded fix was to bound the work rather than raise the pool.

There is also precedent for the payload side: `docs/claude-findings/2026-09-01-prod-frontend-build-failure-public-notes-2mb.md`
— a 2 MB public-notes payload already broke a frontend build.

### C.3 Indexes — ✅ present

| Index | Covers |
|---|---|
| `idx_notes_copied_from_note_id` | the copy/learners joins |
| `idx_analytics_events_event_type_entity_id` | the view count |

**No new index is needed.** The problem is cardinality, not access path.

### C.4 Does separation reduce Public Profile cost?

**No.** See Executive judgment. Public Profile never paid for it.

**But separation DOES enable the fix that matters**, if the new endpoint is paginated/filtered and
Settings switches to a lightweight summary. That is the honest version of the brief's §22 claim.

---

## D. Proposed information architecture

| Element | Recommendation |
|---|---|
| **Route** | **`/impact`** — top-level, matching `/progress`, `/library`, `/explore`. `/profile/impact` would wrongly imply it is part of the public profile, which is the exact confusion being fixed. Authenticated-only, like `/progress`. |
| **Public Profile (owner)** | Replace the full dashboard with a compact card: heading *"Your Impact"*, one line, `View impact →`. Owner-only, unchanged privacy. |
| **Public Profile (visitor)** | No change. Nothing added, nothing exposed. |
| **Sidebar** | ❌ **Do not add.** Per §3 — a first-class page does not require first-class navigation. Denominator is 1. |
| **Dashboard teaser** | ⚠️ **Defer.** Needs a lightweight summary endpoint that does not exist yet, and Dashboard's job is next-step guidance (§14/§15). Revisit only after the summary endpoint exists for Settings anyway. |
| **Note Detail entry** | ⚠️ **Defer** (§17 explicitly permits this). Only 61 of 1,591 public notes have any impact; the link would be dead weight on 96% of them. |

### D.1 ⚠️ Route-shape note for §25

`/impact?note=<id>` is fine **only if** the note id is validated as owned-and-public server-side and an
unowned id renders the general page rather than an error. Since the payload is already owner-scoped,
the id leaks nothing on its own — but do not let it become a lookup parameter.

---

## E. Notification readiness

**Canonical destination: `/impact`.** This satisfies the notification audit's deep-link matrix entry
for `IMPACT_MILESTONE`, which currently points at `/progress` as a placeholder —
`docs/claude-plans/attention-notifications-email-expansion-stage1.md` §15 should be updated to `/impact`
once this ships.

Constraints carried over from that audit, unchanged:

- Impact notifications are **category `IMPACT`: no numeric badge**, aggregated, milestone-based.
- Dedup key `IMPACT_MILESTONE:<noteId>:<threshold>` needs the **composite dedup-key helper** from that
  audit's §17.1 — which is Stage B, already owner-approved (D1).
- **Milestones are NOT in scope here** (§12, and that audit defers them to Stage F). This page only
  needs to be a stable destination.

**Nothing in this work blocks or is blocked by the notification work**, beyond that one doc update.

---

## F. UX

### F.1 Page hierarchy

```
Your Impact                                    [Private to you]
See how your published notes are helping people learn.

  56
  distinct learners helped
  Each learner is counted once here, even if they studied more than
  one of your notes. Per-note counts can add up to more.

Notes helping learners · 61
  [card] [card] [card] …

Other published notes             ▸ collapsed by default
  These haven't recorded learning activity yet.
  [shown on expand, paginated]
```

⚠️ **CORRECTED BY THE ADDENDUM (§7).** An earlier draft of this block put a prominent
*"Awaiting first study · 1,526"* counter on the page. **Do not do that** — it turns a positive
contribution surface into a deficit scoreboard, and 1,526 is the largest number on the page. The
impacted count (`· 61`) is shown because it is the achievement; the zero-impact count is not
promoted.

**Learning impact first, distribution second** (§6). Views/copies stay as the small third line on each
card, never promoted.

### F.2 Zero states

| State | Copy |
|---|---|
| Published notes, no qualifying activity | **"Your shared notes are ready to help someone learn."** — impact appears when learners study from your published notes. |
| No published notes | **"Share what you know."** — publish a note when you're ready to make it discoverable. CTA → existing Library/create flow. **Do not invent a publishing workflow** (§10). |
| Load error | Keep the existing retry affordance (`:117-124`) — it already works. |

⚠️ **Replace the current *"No learners yet"*** (`:139`). It is the negative framing §10 asks to avoid,
and it is the string a first-time contributor is most likely to see.

### F.3 Impacted vs zero-impact treatment

Sort by `distinctLearnersHelped DESC`, then a **stable deterministic tie-breaker**.

⚠️ **CORRECTED BY THE ADDENDUM (§4). The tie-breaker is `noteId ASC` alone — NOT `copyCount DESC`.**
An earlier draft recommended `copyCount DESC, noteId ASC`; that was wrong on two counts. `copyCount`
is not available in the ranking query (it is a separate grouped query over the page), so ordering by
it would force an extra join into the ranking path; and it promotes a distribution metric into the
semantic ranking, which §4 and the product doctrine both reject. `noteId` is immutable, unique and
already present — it is the only tie-breaker that guarantees stable pagination. See Addendum §2.

**Do not hide zero-impact notes permanently** (§9) — collapse them, with an obvious, keyboard-reachable
control that is not hover-dependent.

### F.4 Mobile

Existing `grid`/`line-clamp` behaviour is already correct and should carry over unchanged. The
collapsed section is what makes the page usable on mobile — it turns 1,587 cards into 61.

---

## G. Privacy

| Requirement (§20) | Status |
|---|---|
| Owner-only access | ✅ **already structurally guaranteed** — no `userId` parameter exists |
| Aggregate only | ✅ every field is a `count`; no identity is selected anywhere |
| No learner identities | ✅ `copy.ownerUserId` appears **only inside `count(distinct …)`**, never projected |
| No quiz scores / weak concepts | ✅ session is joined for `status` and `completedAt` only |
| No private notes | ✅ every query requires `source.visibility = PUBLIC` |
| No private Review Set membership | ✅ collections are not touched |
| No "who copied/studied" | ✅ not selectable from the DTO |

**The privacy contract is sound and must be preserved verbatim through any move.** The single rule that
carries it: **the endpoint takes no user identifier.** If a future change adds one "for admin support",
that is a new privacy contract and needs its own review.

⚠️ **§19 guard:** Impact must not gain any Learning Connections dimension. The relationship model is
separate and the impact queries have no join to it — keep it that way.

---

## H. Implementation scope

### H.1 Required — and note the ordering differs from the brief's §30

| # | Item | Why |
|---|---|---|
| **1** | **Bound the impact query.** Server-side sort by impact, pagination or a cap, and a filter for the zero-impact set. | ⚠️ **The only production risk in this document.** Independent of the IA move. |
| **2** | **Add a lightweight summary endpoint** (e.g. `GET /creator-impact/me/summary` → `{distinctLearnersHelped, publicNoteCount}`) **and switch Settings to it.** | Kills the 1,587-record fetch that computes a boolean. Also unblocks any future Dashboard teaser. |
| 3 | Dedicated `/impact` route reusing the existing service — **no duplicated analytics logic** (§2). | The discoverability fix; 7 lifetime views |
| 4 | Remove the full dashboard from Public Profile; add owner-only `View impact →`. | §5, §16 |
| 5 | Per-note hierarchy: impacted first, zero-impact collapsed, deterministic tie-break. | §9 — and it is what makes 1,587 notes survivable |
| 6 | Zero states per F.2. | §10 |
| 7 | Preserve mobile behaviour; ensure the collapse control is accessible. | §23 |

### H.2 Optional if cheap

**Nothing.** ⚠️ **CORRECTED BY THE ADDENDUM (§11):** `/impact?note=<id>` moves from *optional* to
**deferred**. There is no shipped consumer that needs it — Note Detail entry is deferred and milestone
notifications are a later stage — so building it now would create **an unused deep-link contract**,
which is a commitment with no caller. The canonical destination is `/impact`, full stop.

### H.3 Deferred — with reasons, not just labels

| Item | Reason |
|---|---|
| Dashboard teaser | Needs item 2's summary endpoint first; risks displacing next-step guidance (§14) |
| Note Detail → View impact | Dead weight on 96% of public notes (1,530 of 1,591) |
| Impact milestone notifications | Notification audit Stage F; needs the composite dedup key from its §17.1 |
| Impact email | Blocked on that audit's R1 — the daily email cap is already exceeded |
| Public impact metrics, leaderboards, badges, XP, following | §26, §27, §32 — **out of scope permanently**, not merely deferred |

### H.4 Size

**7 required items.** That is well past the 3–4 band where verification stays at a single `advisor()`
call, and the Release Implementor has asked for this to be stated rather than discovered at signoff.

**Recommended split into two releases:**

- **Release 1 — items 1 + 2** (bound the query, add the summary endpoint, switch Settings). Backend +
  one frontend call site. Fixes the actual defect. **~2 items, cheapest possible verification.**
- **Release 2 — items 3–7** (the IA move and UX). Pure frontend once item 1 gives it a bounded
  endpoint to consume. **~5 items.**

This ordering also means Release 2 never has to consume an unbounded endpoint, so the new page is
never briefly worse than the old one.

---

## Discriminating tests (§31)

| Test | Assertion | Note |
|---|---|---|
| Owner access | Authenticated owner gets their own aggregates | — |
| **Cross-user access** | User A cannot retrieve User B's impact | ⚠️ **There is no parameter to attack.** Assert the *absence* of a userId input, not just a 403 — a test that passes a param and expects rejection would be testing a route that does not exist |
| Public profile visitor | Visitor receives/renders none of it | already covered at `public-profile-page-client.test.tsx:277` — **keep this test through the move** |
| Owner public profile | Owner sees `View impact →`, **not** a duplicated dashboard | §16 |
| Headline dedup | One learner studying 2+ notes → headline counts them **once** | the documented semantic |
| Per-note count | Same learner counts in **each** note; headline stays globally distinct | per-note sum > headline is correct, not a bug |
| **Self-copy exclusion** | Owner copies own public note, completes a session → **still 0 learners helped** | ⚠️ guards `NoteService.copyNote:392-397`; prod value is 0 so this is a **structural** guard with no live data behind it — say so in the javadoc |
| Zero-impact user | Published notes, no activity → intentional zero state, no empty analytics wall | §10 |
| No-public-notes user | Contribution zero state, **no misleading learner-helped claim** | §10 |
| Sorting | Impacted notes precede zero-impact ones; **tie-break is deterministic across pages** | run it twice, assert identical order |
| **Query count** | Loading impact with many notes issues a **bounded** number of queries **and a bounded IN list** | ⚠️ the existing code already passes "no N+1" — the new assertion is on **list size**, which is the thing that is actually broken |
| **Settings payload** | Settings no longer calls the full impact endpoint | ⚠️ the regression guard for the headline finding |
| Public Profile performance | ⚠️ **Expect NO change** — it never loaded impact. Do not write a test asserting an improvement that cannot occur | disproved premise |
| Deep-link readiness | `/impact` opens directly after auth | for future notification CTA |
| Mobile | Headline, metrics, cards, collapse control; no horizontal overflow | §23 |

---

## Anti-drift

**Must NOT:**
- ❌ Add Impact to the permanent sidebar (§3).
- ❌ Make any impact metric public — no rankings, leaderboards, top-contributor badges, follower counts.
- ❌ Add following, comments, public reactions, activity feeds, creator subscriptions (§26).
- ❌ Add XP, points, levels, trophies, badges, publishing streaks (§27).
- ❌ Merge Impact into Progress (§18) or into Learning Connections (§19).
- ❌ Expose learner identities, scores, weak concepts, or who copied/studied (§20).
- ❌ **Duplicate the analytics logic** — move and reuse `CreatorImpactService` (§2).
- ❌ **Add a `userId` parameter to the impact endpoint.** Its absence *is* the privacy control.
- ❌ Load full impact analytics from the app shell, notification polling, or Dashboard (§21).
- ❌ Make views or copies the hero metric — and note views **cannot** be a person-metric (B.3).
- ❌ Ship impact milestone notifications or impact email in this work.
- ❌ Redesign Public Profile beyond removing the dashboard and adding the owner link.

**Must preserve:**
- ✅ `PRIVATE TO YOU` labelling and owner-only rendering.
- ✅ The headline-dedup explanatory copy — **it is accurate** (B.2).
- ✅ Self-copy exclusion via `copiedFromNoteId = null` on the owner branch.
- ✅ The `impactRequestIdRef` out-of-order-response guard, if the loading logic moves.
- ✅ `public-profile-page-client.test.tsx:277` (visitor issues no impact request).

---

## Product contracts (§33, verbatim)

> **Public Profile is how others see my contribution.**
> **Your Impact is how my shared knowledge is helping people learn.**
> **Your Impact is private contribution feedback, not public social status.**
> **Learning impact is primary. Views and copies are secondary signals.**
> **Contribution should feel rewarding because knowledge helped someone learn — not because the author won a popularity contest.**
> **A first-class page does not automatically deserve permanent navigation.**
> **Notifications may surface meaningful impact; Your Impact is the durable destination.**
> **NoteLib can feel alive without becoming social media.**

---

## Housekeeping

**⚠️ Backlog Index obligation.** `CLAUDE.md` kickoff step 8 requires every `docs/claude-plans/` file to
carry a row in `ROADMAP.md`'s Backlog Index. **This file needs one at the next kickoff**, alongside
`attention-notifications-email-expansion-stage1.md`, which is still owed the same.

**Cross-reference to update when this ships:** the notification audit's §15 deep-link matrix lists
`/progress` as the `IMPACT_MILESTONE` destination. It becomes `/impact`.

---
---

# ADDENDUM — Tightening Response (2026-09-09)

**Approved in principle.** This addendum answers the eight required outputs. It does not restate the
audit. Where it contradicts the audit above, **the addendum wins** and the audit body has been
corrected inline at F.1, F.3, B.3 and H.2.

**Preserved, per addendum §1:** moving Your Impact is an **information-architecture and
discoverability** improvement, **not** a Public Profile performance optimization. No implementation
copy, doc or test may claim otherwise. The production defect is Settings loading the full payload to
derive a boolean, and it remains the highest-priority work.

---

## 1. Proposed bounded query / pagination architecture

Three independent bounded reads. **No query materializes the owner's full public-note ID set**, and
no page carries an unbounded `IN` list.

### Q1 — Impacted notes, globally ranked (section 1)

```sql
SELECT s.id, s.title, COUNT(DISTINCT c.owner_user_id) AS helped
FROM notes s
JOIN notes c ON c.copied_from_note_id = s.id
JOIN quick_review_sessions q ON q.note_id = c.id
WHERE s.owner_user_id = :ownerId
  AND s.visibility = 'PUBLIC'
  AND c.copied_from_public = true
  AND q.status = 'COMPLETED'
  AND q.completed_at IS NOT NULL
GROUP BY s.id, s.title
ORDER BY helped DESC, s.id ASC
LIMIT :limit OFFSET :offset
```

**The `INNER JOIN` is the whole trick.** A note with no qualifying learner produces no rows, so it
never enters the grouping. Q1 therefore returns **only** impacted notes, already globally ordered, and
**never touches the 1,526 zero-impact notes at all.**

### Q2 — Other published notes (section 2), anti-join

```sql
SELECT s.id, s.title
FROM notes s
WHERE s.owner_user_id = :ownerId
  AND s.visibility = 'PUBLIC'
  AND NOT EXISTS (
    SELECT 1 FROM notes c
    JOIN quick_review_sessions q ON q.note_id = c.id
    WHERE c.copied_from_note_id = s.id
      AND c.copied_from_public = true
      AND q.status = 'COMPLETED'
      AND q.completed_at IS NOT NULL)
ORDER BY s.updated_at DESC, s.id ASC
LIMIT :limit OFFSET :offset
```

`helped` is **0 by definition** for every row — no aggregate needed.

### Q3 — Secondary metrics for the returned page only

The three existing grouped queries are **kept unchanged**, but `:noteIds` is now **the current page's
ids (≤ `:limit`)** instead of all 1,587. Views and copies for section 1 and section 2 alike.

```
countPublicNoteEventsByTypeAndNoteIds(PUBLIC_NOTE_VIEWED, pageIds)   -- ≤ limit
countCopiedPublicNotesBySourceNoteIds(pageIds)                        -- ≤ limit
```

`countDistinctLearnersHelpedBySourceNoteIds` becomes **unnecessary** — Q1 already returns `helped` as
its ordering column, and section 2's is always 0. **One existing query is deleted, not adapted.**

### Query budget per page

| Section | Queries | Bounded by |
|---|---|---|
| Section 1 page | Q1 + views + copies = **3** | `LIMIT` on the ID set |
| Section 2 page | Q2 + views + copies = **3** | `LIMIT` on the ID set |
| Section labels | see §3 | — |

**No N+1** (metrics stay grouped), and every `IN` list is `≤ limit`. Recommend `limit = 20`.

### Verified plans (`EXPLAIN`, production, 2026-09-09)

Both are fully index-driven — **no sequential scan on `notes`**:

| Query | Access path |
|---|---|
| Q1 | `idx_notes_owner_user_id_updated_at` → `idx_notes_copied_from_note_id` → `idx_quick_review_sessions_note_created`, all Nested Loop Index Scans |
| Q2 | **Nested Loop Anti Join** + **Incremental Sort** with `Presorted Key: s.updated_at` |

**No new index is required.** All three already exist.

### ⚠️ One honest asymmetry the Implementor must know

| Query | Truly `LIMIT`-bounded? |
|---|---|
| **Q2** | ✅ **Yes.** Incremental Sort on a presorted key means the anti-join is evaluated lazily and the scan stops once `LIMIT` rows are found. |
| **Q1** | ❌ **No — and this is inherent.** `ORDER BY <aggregate>` requires computing the aggregate for **every** candidate before sorting; the plan shows `Sort → GroupAggregate` *beneath* `Limit`. |

**Q1's work is bounded by the copy volume, not the note count.** Measured in production: the
aggregation touches **5,584 copy rows** (vs. today's 1,587-note × three-`IN`-list payload). That is a
large, real improvement — but it is **not** a `LIMIT` short-circuit, and no test or doc should claim
it is.

**Why this is acceptable and what would change it:** the impacted set is 61 notes from 5,584 copies,
all index-scanned. If copy volume ever reaches the ~10⁵–10⁶ range, Q1 needs a materialized/incremental
impact counter. **That is the documented upgrade path, not this release's work.** No premature
denormalization.

---

## 2. Proof that impacted-note ordering is globally correct

The addendum's §4 forbids the fetch-page → compute-impact-for-page → sort-locally pattern. **Q1 does
not do that, and the reason is structural rather than a matter of care:**

1. **Ranking happens in the database, over the whole impacted set.** `GROUP BY` runs before
   `ORDER BY`, which runs before `LIMIT`. Page 1 is the globally top-N by `helped` — by SQL's
   evaluation order, not by convention.
2. **No candidate is excluded before ranking.** The only filters are ownership, `PUBLIC`, and the
   definition of "helped" itself. There is no pre-page, no pre-query and no application-side ID list.
3. **The application never sees an unbounded ID set.** It receives `≤ limit` rows and passes exactly
   those ids to Q3. The 1,587-element list has no place to exist.
4. **Zero-impact notes cannot displace impacted ones**, because the inner join excludes them from Q1
   entirely — they are not ranked and then filtered, they are never candidates.

### Tie-breaker: `s.id ASC` — and why not `copyCount`

**Recommended: `ORDER BY helped DESC, s.id ASC`.**

| Candidate | Verdict |
|---|---|
| **`s.id ASC`** | ✅ **Chosen.** Unique (primary key) → total order → **no page can drop or repeat a row**. Immutable → a note edited mid-pagination cannot move. Already in the query; costs nothing. |
| `copyCount DESC` | ❌ Not available in Q1 — it is a separate grouped query over the page, so using it forces another join into the ranking path. It also promotes a distribution metric into the semantic ranking, which §4 and the doctrine reject. **This corrects the audit's F.3.** |
| `updated_at DESC` | ❌ **Mutable.** Editing a note mid-pagination shifts it across pages, duplicating or skipping rows. Also not unique, so it is not a total order on its own. |

⚠️ **Known limitation, stated rather than hidden:** Q2 uses `updated_at DESC, s.id ASC` — mutable
leading key, so OFFSET paging can shift if a note is edited mid-scroll. Accepted because section 2 is
collapsed, secondary, and ordered for recency rather than ranking. **Keyset pagination is the upgrade
if it ever matters**; it is not needed at 1,526 rows behind a collapsed control.

---

## 3. Lightweight summary endpoint — exact contract and cost

```
GET /creator-impact/me/summary          (authenticated; owner-scoped from the principal)

{ "distinctLearnersHelped": 56,
  "publicNoteCount": 1587,
  "impactedNoteCount": 61 }
```

### Cost: two queries, one of them free

**S1 — both learner and impacted-note aggregates in a SINGLE pass:**

```sql
SELECT COUNT(DISTINCT c.owner_user_id) AS helped,
       COUNT(DISTINCT s.id)            AS impacted_notes
FROM notes s
JOIN notes c ON c.copied_from_note_id = s.id
JOIN quick_review_sessions q ON q.note_id = c.id
WHERE s.owner_user_id = :ownerId AND s.visibility = 'PUBLIC'
  AND c.copied_from_public = true
  AND q.status = 'COMPLETED' AND q.completed_at IS NOT NULL
```

**S2 — indexed count, no ID materialization:**

```sql
SELECT COUNT(*) FROM notes WHERE owner_user_id = :ownerId AND visibility = 'PUBLIC'
```

### ✅ §5's open question answered: include `impactedNoteCount`

`EXPLAIN` (production) shows S1 as **a single `Aggregate` node over one join scan** — both DISTINCT
aggregates are computed together. `impactedNoteCount` therefore **adds no extra scan and no extra
query**; it is the same join `distinctLearnersHelped` already requires.

**It also earns its place:** it supplies section 1's `· 61` label without a second call, and it is the
one number that distinguishes *"published notes, none studied yet"* from *"no published notes"* — the
two zero states in F.2 that must not be confused.

### Contract guarantees (§5, §10)

- ❌ **Returns no note list** — three scalars, fixed size, forever.
- ❌ **Accepts no `userId`** — identity from `@AuthenticationPrincipal`, matching `/creator-impact/me`.
- ✅ Safe for lightweight consumers: **Settings** and the **owner Public Profile card**.
- ⚠️ **Must never grow a note list.** If a caller needs notes, it calls the paginated endpoint. A
  "just add the top 3 notes" request is how this becomes a second dashboard payload — refuse it.

### What it replaces

| Consumer | Today | After |
|---|---|---|
| Settings | full payload, **1,587 records**, 5 queries, 3 × `IN(1587)` — to compute a boolean | S1 + S2, three scalars; `hasPublicNotes` = `publicNoteCount > 0` |
| Public Profile owner card | *(does not exist yet)* | same summary, no second fetch |

---

## 4. Release 1 — exact scope

**Theme: data-access hardening. No IA change, no route, no visual redesign.**

| # | Change | Files |
|---|---|---|
| 1 | Add Q1/Q2 repository queries; delete `countDistinctLearnersHelpedBySourceNoteIds` (Q1 supersedes it) | `NoteRepository` |
| 2 | Paginate `CreatorImpactService.getMine` — accept `section` (impacted / other) + `limit` + `offset`; pass **page ids only** to the metric queries | `CreatorImpactService` |
| 3 | Paginated response DTO with `hasMore` (or `nextOffset`) | `CreatorImpactResponse` |
| 4 | `GET /creator-impact/me/summary` + S1/S2 | `CreatorImpactController`, `CreatorImpactService`, `NoteRepository` |
| 5 | Switch **Settings** to the summary endpoint | `frontend/app/settings/page.tsx:281,296` |
| 6 | Keep Public Profile rendering, adapted to the paginated shape | `public-profile-page-client.tsx` |

**No DDL** — all three indexes already exist.
**Out:** `/impact` route, Public Profile card, zero-state copy, sorting UX, "page views" relabel.

⚠️ **Item 6 is the item most likely to be dropped and must not be.** Public Profile still renders
Impact until Release 2. If its call site is not adapted to the paginated shape, Release 1 breaks the
only surface the feature currently has. **Release 1 is not "backend only".**

**Size: 6 changes across ~5 files, one behavioural surface.** Verification: one `advisor()` call —
no privacy-boundary change, no DDL, no new producer, aggregates unchanged.

---

## 5. Release 2 — exact scope

**Theme: the dedicated private Impact experience. Frontend-led; consumes Release 1's endpoints.**

| # | Change |
|---|---|
| 1 | Authenticated `/impact` route (top-level, like `/progress`) |
| 2 | Move the full Impact experience there; **reuse the service — no duplicated analytics logic** |
| 3 | Replace the Public Profile dashboard with the owner-only compact card, fed by **the summary endpoint** (§6) |
| 4 | Section 1 *"Notes helping learners · N"* first, globally ranked |
| 5 | Section 2 *"Other published notes"*, collapsed, accessible, paginated — **no prominent deficit counter** |
| 6 | Zero states per F.2; replace *"No learners yet"* |
| 7 | Relabel `views` → **"page views"** (addendum §9) |
| 8 | Mobile + accessibility: collapse control obvious, keyboard-reachable, not hover-dependent |

**Out:** sidebar entry, Dashboard teaser, Note Detail link, `/impact?note=`, milestone notifications,
any email.

**Size: 8 changes, one new route, mostly presentational.** Verification: one `advisor()` call, plus a
copy re-read of every surface that describes Impact.

⚠️ **Do not merge the two releases.** Release 2's whole premise is that it consumes a bounded endpoint;
merging means the new page briefly ships against the unbounded one, which is the defect being fixed.

---

## 6. Tests to add or change

### Release 1

| Test | Assertion |
|---|---|
| **Global ranking correctness** | Fixture: notes with helped = 5, 3, 1 **and** 40 zero-impact notes created *most recently*. Page 1 (`limit=2`) returns the **5 and 3** notes. ⚠️ **The fixture must make the zero-impact notes newest** — under the old `updated_at DESC` order they would occupy page 1, so this fails loudly against a page-then-sort implementation. |
| **Tie-break stability** | Two notes with equal `helped`; request the same page twice → identical order; assert ordering is by `id ASC`. |
| **Pagination completeness** | Walking all pages yields every impacted note exactly once — no drops, no repeats. |
| **Bounded `IN` list** | ⚠️ With 200 public notes and `limit=20`, assert the metric queries receive **≤ 20** ids. This is the regression guard for the actual defect; "no N+1" already passes today and proves nothing. |
| **Anti-join disjointness** | Section 1 ∪ Section 2 = all public notes; Section 1 ∩ Section 2 = ∅. Guards `NOT EXISTS` drifting from the inner join's predicate. |
| **Summary endpoint** | Returns the three scalars; `impactedNoteCount` matches section 1's total; **returns no note list**. |
| **Summary real request** | `MockMvc` + `.contentType(APPLICATION_JSON)` — new endpoint, per the `v0.119.0` lesson. A direct handler call passes under a binding defect by construction. |
| **Summary takes no userId** | Assert the **absence** of a user-identifier parameter, not a 403. A test that passes `?userId=` and expects rejection tests a route that does not exist. |
| **Settings payload** | ⚠️ Settings calls the **summary** endpoint and **not** the full one. The regression guard for the headline finding. |
| **Self-copy exclusion** | Owner copies own public note + completes a session → still 0 helped. ⚠️ Structural guard, prod value is 0 — **say so in the javadoc**. |
| **Headline dedup** | One learner across 2+ notes → headline counts once; per-note sum may exceed it. |

### Release 2

| Test | Assertion |
|---|---|
| Owner Public Profile | Sees the compact card, **not** a duplicated dashboard; card is fed by the **summary** endpoint |
| Visitor Public Profile | Issues no impact request — **keep `public-profile-page-client.test.tsx:277` through the move** |
| `/impact` unauthenticated | Redirects/denies; no analytics returned |
| Zero state: published, no impact | Positive framing; **`"No learners yet"` is gone** |
| Zero state: no published notes | Distinct copy; **no misleading learner-helped claim** |
| Section 2 collapse | Reachable by keyboard, no hover dependency, works at mobile widths |
| "Page views" label | Rendered label is not a bare `views` |

### Tests explicitly NOT to write

- ❌ **Any assertion that Public Profile got faster.** It never loaded impact; the improvement cannot
  occur, and a passing test would be measuring nothing.
- ❌ Any test asserting Q1 short-circuits on `LIMIT`. It does not (§1).

---

## 7. Documentation and cross-references

| File | Change | Release |
|---|---|---|
| `docs/features/<impact feature doc>` | Metric semantics verbatim from B.1/B.2; **"page views"** wording; new pagination contract. ⚠️ **Confirm the exact filename** — the audit did not, and `docs/features/` holds 59 files. | 1 & 2 |
| `docs/features/public-profile.md` *(if present)* | Impact dashboard removed; owner-only entry card added | 2 |
| `RELEASES.md` | Bullets per release. ⚠️ Must **not** claim a Public Profile performance win | both |
| `docs/claude-plans/attention-notifications-email-expansion-stage1.md` §15 | `IMPACT_MILESTONE` deep link `/progress` → **`/impact`** (addendum §13) | after 2 ships |
| `ROADMAP.md` Backlog Index | ⚠️ Row for **this file** and for the notification audit — kickoff step 8 | next kickoff |
| `AGENTS.md` | Only if a new rule is established (e.g. *"impact endpoints never accept a target user id"*) | 1 |

---

## 8. Genuine owner decisions

Only three. Everything else is settled by the audit, the addendum, or measurement.

| # | Decision | Recommendation |
|---|---|---|
| **D1** | **Ship Release 1 alone first, or hold both until Release 2 is ready?** | **Ship Release 1 alone.** It fixes a live production defect on a 256 MB instance and is independently valuable. Holding it gains nothing. |
| **D2** | **Page size for both sections?** | **20.** Section 1 has 61 impacted notes → 4 pages for the only user who has any. Larger pages enlarge the `IN` list for no benefit. |
| **D3** | **Does section 2 need pagination in Release 2, or is "show first 20, no more" acceptable?** | **Paginate.** §7 requires zero-impact notes stay *accessible*; 1,526 notes behind a 20-row cap are not. Q2 is already `LIMIT`-bounded, so paging is nearly free. |

**Explicitly NOT decisions** (settled, recorded so they are not reopened): the route is `/impact`; the
tie-breaker is `s.id ASC`; `impactedNoteCount` ships (it is free); no sidebar entry; no Dashboard
teaser; no `/impact?note=`; no milestone notifications or email here; the privacy contract is frozen
and the endpoint accepts no target user id.
