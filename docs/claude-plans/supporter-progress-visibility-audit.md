# Supporter / Guardian Progress Experience — Audit & Plan

**Status:** PLAN ONLY — nothing implemented. Written 2026-09-05, **revised 2026-09-05** to
incorporate owner product decisions.
**Companion:** `docs/claude-plans/shared-quiz-recipient-experience-plan.md` (owns the recipient-side
UI direction; this document owns supporter semantics and permissions).

**Central principle (owner):** *help the supporter understand whether learning is moving forward,
without revealing what the learner keeps private.*

---

## 1. Updated executive judgment

**The privacy architecture is correct and stays exactly as it is.** The authorization gate is tight:
`LinkedLearnerReadAuthorizationService.requireAcceptedLearnerId` asserts the caller is the
**supporter**, requires a live `PROGRESS` grant, then re-verifies the grant resolved to this
relationship's learner — with a comment recording that the caller check exists because `requireGrant`
is symmetric and would otherwise expose the supporter's own progress to the learner.

**What is wrong is not the permission model — it is discoverability and interpretation.**

1. **A supporter cannot ask.** `requestGrant` / `requestAccess` / `grantRequest`: **zero hits**
   repo-wide. The progress toggle renders only when `callerRole === "LEARNER"`, so a supporter never
   sees any control, and the dashboard's *"{Name} is not sharing progress with you."* is an accurate
   dead end.
2. **Already-permitted data is under-interpreted and split across two pages** — Progress on
   `/linked-learners/{id}/progress`, Activity on `/linked-learners`.
3. **⚠️ Phase 4 rests on data that does not exist** — see §11. This is the one place the product
   direction outruns the repo.

**Phases 1 and 2 are small and need almost nothing new. Phase 3 is a real feature. Phase 4 needs new
persistence and a genuine privacy decision.**

---

## 2. Access-request recommendation

**Approved shape: an "Ask to view progress" action** replacing the dead-end sentence, with
*"They'll decide whether to share it with you."*

**Semantics (all owner-locked, restated as build rules):** the request **creates no grant**; the
learner still chooses explicitly; a declined or ignored request is **indistinguishable from never
asked** from the supporter's side; it is idempotent per relationship; it carries a cooldown.

**⚠️ Forbidden copy:** *"Pending approval"*, *"Waiting for learner"*, any supporter-visible reminder
or counter. The supporter's view stays **"not shared"** in every non-granted state — that single
string is what keeps the relationship psychologically safe.

### ⚠️ It needs one small migration, and the reason is not rate-limiting

The owner asked for the smallest architecture and whether persistence is genuinely necessary. **It
is — but for a different reason than rate-limiting.** The learner has to *see* the request in-app,
and it must survive the supporter's session, so the request has to exist somewhere.

**There is no notification substrate to lean on.** The only notification-shaped services in the repo
are `FeedbackService` and `SubscriptionExpiryEmailService`; there is no general in-app notification
system. `EmailService` / `EmailTemplateService` do exist, so an email is possible — but **email-only
gives no idempotence, no cooldown and no in-app affordance for the learner**, which fails three of
the locked semantics at once.

**Smallest sufficient shape:** a `linked_learner_grant_requests` row —
`(id, relationship_id, requested_by_user_id, scope, requested_at)` — with a partial unique index
giving idempotence and cooldown for free, and the learner's own `/linked-learners` card reading it to
surface a prompt beside their existing toggle.

**⚠️ Do NOT model a request as a `linked_learner_grants` row with a null `granted_at`.** That table is
the authorization substrate; a request must never be one query mistake away from being read as a
grant.

**This is Phase 1's only migration.** If anything else in Phase 1 appears to need one, the scope has
drifted.

---

## 3. Guardian consent vs Progress — doctrine, restated as locked

Two different questions, two different decisions:

| | Question it answers |
|---|---|
| **Guardian consent** | *May this minor participate in this Learning Connection?* |
| **Progress sharing** | *Does this learner want this supporter to see their learning progress?* |

**Verified in code: `LinkedLearnerGrantService` contains no reference to guardian consent.** Consent
activates the relationship and confers no visibility.

**⚠️ Do NOT auto-grant `PROGRESS` on guardian consent, on acceptance, or by backfill onto existing
guardian relationships.** `v0.93.0` refused exactly that backfill on the grounds that it converts an
implicit rule into explicit consent nobody gave.

**DECIDED 2026-09-05 (owner): the Phase 1 learner-side prompt DOUBLES as the post-consent
invitation.** After consent or activation, the learner is invited to decide whether to share
progress — on the same surface Phase 1 already builds, so it costs nothing extra.

**⚠️ It remains an explicit learner choice and grants nothing by itself.** The invitation is a
prompt, not a default: an ignored invitation leaves the learner sharing nothing, and the supporter
still sees only *"not shared"*. **Do not pre-check, pre-select, or default the toggle on.**

---

## 4. Permission-aware supporter information model

**The supporter promise is not "more data".** The page answers five questions:

- Is the learner practising?
- Is overall progress moving?
- Is quiz performance improving?
- Are they moving through their study plans?
- Would a little support or practice help?

**⚠️ "Where exactly are they struggling?" is deliberately NOT a supporter promise.** Exact weakness
location leaks concept names, Note and Study Pack titles, and private curriculum structure. **Do not
expose exact weak concepts in this work** — and note the additional constraint if it is ever
revisited: `v0.107.0` locks weak concepts to per-pack presentation, never merged across packs, so a
supporter-facing concept view inherits that too.

Aggregate *"N due for review"* counts are already exposed and are the safe form of this signal.

---

## 5. Activity + Progress consolidation

**Consolidate the presentation; never the authorization.** Each section independently checks its own
grant, and the two grants stay separate.

| Grants held | Supporter surface shows |
|---|---|
| Progress only | Readiness · quiz performance · plan progress |
| Activity only | Learning momentum only |
| Both | Both, on the **same** page |
| Neither | Name, status, and the *Ask to view progress* action |

**This is a relocation, not new data.** `LinkedLearnerActivityResponse(displayName, engagementMode,
currentStreak, longestStreak, studyDaysThisWeek)` already exists behind `GET
/{relationshipId}/activity`; today it renders only on `/linked-learners`.

**⚠️ When `ACTIVITY` is off, the momentum section must be absent from the payload, not merely hidden
in the UI.** And **⚠️ never infer activity from progress data to fill the page** — `getProgress`
already carries a comment recording that even a boolean derived from streaks or study days would
disclose activity without a grant, which is why `hasActivity` is computed from progress-shaped fields
only.

---

## 6. Readiness interpretation

Today's card can honestly render *"0% ready · 0 mastered · 4 due · 734 not started"*. With
comprehensive Review Sets running to hundreds of topics, **the largest number on the page becomes
"not started", which is the least useful and most discouraging fact available.**

**Rule: readiness explains progress; it does not expose raw counters.**

- Lead with the percentage and the **actionable** counts — mastered and due.
- **Demote or drop "not started"** as the emotional centre.
- Early/zero state gets intentional framing rather than a wall of zeros — e.g. *"Progress is just
  getting started — 4 concepts are due for review."*

**No new data and no new query.** `aggregateReadiness` already computes all four counts; this changes
which are foregrounded. Exact copy is a later detail (§18 of the response).

---

## 7. Quiz-performance interpretation

**Verified: everything the owner asked for is already read, and only the projection is thin.**
`DashboardService.getMasterySnapshot` builds `recentCompletedSessions` (filtered to `COMPLETED`) and
a `recentScores` list, then returns only `averageRecentScore`, `bestRecentScore`, `studyPacksReviewed`.

So **count = the list size** and **latest = the most recent element** are both available with **no
new query and no migration.**

Recommended hierarchy:

> **82% recent average** · 5 quizzes completed · Latest: 80%
> *(best recent score as tertiary context)*

**⚠️ Do not manufacture a trend line from this** — it is a recent window, not a history. Movement
belongs to Phase 3 (§9).

---

## 8. Plan-progress interpretation

Exposed today: `collectionCount`, `totalItems`, `readyItems`, `practicedItems` — **counts only**.

**⚠️ Plan names must stay hidden.** `NoteCollectionSummaryResponse` *does* carry `title` server-side,
but `LinkedLearnerProgressService` aggregates it away, and the locked privacy line forbids private
curriculum details. **Do not expose plan names.**

- With data: *"46 of 210 practiced · 22% complete across 3 plans"* — a **pure formatting change** over
  data already sent.
- Without: an intentional zero state — *"No study plan activity yet. Progress will appear here once
  the learner starts practising from a Study Plan or Review Set."* — never *"0/0 practiced · 0 ready
  items across 0 plans"*.

---

## 9. Future trend direction (Phase 3)

**⚠️ Readiness trend cannot be reconstructed today, and this is structural.** There is **no readiness
history or snapshot table** (searched migrations; the only near-matches are an unrelated
subscription-history guard and a collection-structure snapshot). `ConceptHealth` stores current state
per concept — `incorrect_streak`, `last_correct_at`, `last_incorrect_at` — with no series. **What
readiness was 30 days ago is not recoverable.**

**Quiz-performance movement is different: completed sessions carry timestamps**, so a genuine
performance trend is buildable from existing rows.

| Trend type | Buildable today? |
|---|---|
| Quiz performance over time | **Yes** — completed sessions with timestamps |
| Practice / plan movement | **Yes** — session history |
| Activity momentum (with `ACTIVITY`) | **Yes** — existing streak/study-day data |
| **Readiness movement** | **No** — needs a periodic snapshot |

**DECIDED 2026-09-05 (owner): build the three that are free; the readiness snapshot is DEFERRED.**
Quiz-performance, practice/plan and momentum movement together answer *"is the learner improving?"*,
so readiness trend is not worth a new write path yet.

**⚠️ If it is ever revisited**, the minimum honest architecture is a small periodic per-user
readiness snapshot — **prospective only, never backfilled**, because a series cannot be reconstructed
from current-state rows. **⚠️ Do NOT add event sourcing or a history framework** for it.

**Trend principle: show movement, not private study content.** No weak-concept history, no per-Note
weakness, no curriculum breakdown.

---

## 10. Contextual "Create a quiz for {Name}"

**Approved, and it reuses the existing flow — but it is an entry point, not a button.**

Verified: *Quiz for someone* is a **note-actions menu item** on the private note detail page
(`private-note-detail-page-client.tsx:2363`), so the flow begins from a note the **supporter** owns.
A contextual action therefore deep-links into note selection; it cannot start a quiz directly from
the progress page.

**⚠️ This does not make quiz sharing connection-only** — the global flow stays independent
(`/quiz/share/**` is `permitAll`, the recipient needs no account and no relationship). **No new quiz
engine, no Assignment.**

---

## 11. ⚠️ "Quizzes you shared" — permission audit (§13/§19)

**The audit answer is not a permission answer. The data does not exist.**

| Question asked | Repo answer |
|---|---|
| Is **completion** visible without Progress permission? | **Nothing is recorded.** `getSharedQuizResults` (`QuizShareLinkService:182`) grades in memory and returns — zero `.save(`, zero analytics, zero activity tracking in the method |
| Does **score** require Progress permission? | **No score is stored anywhere** |
| Does **opened / in-progress** require Activity permission? | Only an **analytics event** exists — see below |
| Is a supporter-shared quiz **relationship-owned** or **learner-private progress**? | **Neither.** The *artifact* (`generated_quizzes`, `quiz_share_links`) is supporter-owned; the *completion* has no owner because it is never created |

**And a shared quiz creates no session** — `GeneratedQuizService` and `QuizShareLinkService` reference
`quick_review_sessions` **zero times** (`v0.110.0`). So a shared-quiz completion never enters the
learner's progress data either: **even a full `PROGRESS` grant would not reveal it.**

**⚠️ On the analytics event — do not build the feature on it.** `QUIZ_SHARE_LINK_OPENED` is fired
from the recipient page, but it lands in `analytics_events`, whose FK to `users` was **deliberately
dropped** (`V77`: *"Analytics telemetry intentionally has no hard FK to users. Orphaned user_id
values are acceptable"*). It is declared telemetry, not product state. It is also fired for **two
different things** — opening the quiz (`page.tsx:59`) and clicking the results signup CTA (`:195`) —
distinguishable only by a metadata string.

**Recommendation: Phase 4 requires a purpose-built completion record, and that is a genuine privacy
decision, not a grant change.** A share link is `permitAll`, so anyone holding it can complete the
quiz — including people who are not the intended recipient. Recording *who* completed therefore means
deciding whether anonymous completions are stored at all.

**DECIDED 2026-09-05 (owner): record SIGNED-IN completions only, surfaced to the supporter only
where an accepted relationship exists.**

**⚠️ Anonymous completions are NOT recorded at all** — not anonymised, not counted, not aggregated.
A share link is `permitAll`, so recording anonymous completions would turn a public link into a
tracking surface, which is the outcome this decision exists to prevent.

**⚠️ The decision unblocks Phase 4's PRIVACY question only — it does not unblock the build.** Phase 4
still needs the completion record itself (a migration) and must not start before that is scoped.
**Do not silently broaden permission semantics to get there**, and do not let it drift into
Assignment.

---

## 12. Permission matrix (§19)

| Progress | Activity | Supporter may see |
|---|---|---|
| **OFF** | **OFF** | Name, relationship status, and **Ask to view progress**. Copy stays *"{Name} is not sharing progress with you."* |
| **ON** | **OFF** | Readiness · quiz performance · plan progress. **No** streaks, study days or momentum — not even inferred |
| **OFF** | **ON** | Learning momentum only — streaks, longest streak, study days this week. **No** readiness, scores or plan data |
| **ON** | **ON** | Both, consolidated on one surface, each section still checking its own grant |

**A quiz the supporter personally created and shared — in every state above: nothing.**
Not completion, not score, not opened status. **Because no record exists** (§11), not because a grant
withholds it. This is uniform across all four rows and is the clearest evidence that Phase 4 is new
construction rather than a permission adjustment.

---

## 13. Request-access state model (§20)

| State | Supporter sees | Learner sees |
|---|---|---|
| **No access, request available** | *"{Name} is not sharing progress with you."* + **Ask to view progress** | nothing |
| **Request recently sent** | *"{Name} is not sharing progress with you."* + action **disabled during cooldown**, no status language | a prompt beside their existing Progress toggle |
| **Access granted** | **View progress** link; full Progress sections | their toggle reads on |
| **Access revoked later** | returns to *"not shared"*, request available again after cooldown | their toggle reads off |
| **Declined or ignored** | **identical to "no access"** — no separate state exists | prompt may be dismissed |

**⚠️ There is deliberately no supporter-visible "declined" state.** Declined and ignored are the same
state as never-asked. **⚠️ The cooldown must not be narrated** (*"you asked 2 days ago"*), because
that leaks that a request is outstanding and reintroduces pressure by the back door — the action is
simply unavailable.

**Persistence:** one small table, as §2 sets out — required for learner visibility, and giving
idempotence and cooldown for free. **The only migration in Phases 1–2.**

---

## 14. Revised sequencing

| Phase | Content | New privacy scope | Migration |
|---|---|---|---|
| **1 — Access usability** | *Ask to view progress*; learner-side prompt deep-linking to the existing control; idempotent + rate-limited; copy explaining the Activity↔Progress asymmetry | **None** — a request grants nothing | **One** small table |
| **2 — Supporter page consolidation** | Activity + Progress on one surface (grants still separate); readiness, quiz-performance and plan-progress interpretation; intentional zero states; contextual *Create a quiz* | **None** | **None** |
| **3 — Progress over time** | Quiz-performance, practice/plan and Activity-based movement. Readiness movement **only** if a snapshot is added | **None** | Only if readiness trend is built |
| **4 — Shared-practice feedback loop** | *Quizzes you shared* — privacy question **DECIDED** (§11); still blocked on the completion record itself | Signed-in completions only, accepted relationships only | **Yes** |

**Phase 2 is the best value per unit of risk** — it is almost entirely reprojection of data the
supporter is already permitted to see, with no new scope and no migration.

**⚠️ Phase 4's privacy decision is now taken (§11), but the phase is still not startable** — it
needs the completion record, which is new persistence. It remains the phase most likely to drift into
Assignment; it must not.

---

## 15. Owner decisions — ALL SETTLED 2026-09-05

**No owner decisions remain open on this plan.** The three that survived repo verification were taken
on 2026-09-05:

| Decision | Outcome | Where |
|---|---|---|
| Record shared-quiz completions? | **Signed-in completions only**, surfaced only within an accepted relationship. **Anonymous completions are never recorded** | §11 |
| Readiness trend snapshot? | **Deferred.** Build the three free trends; readiness series is prospective-only if ever revisited | §9 |
| Phase 1 prompt as post-consent invitation? | **Yes** — same surface, no extra cost, still an explicit learner choice with no default-on | §3 |

**Settled by the audit rather than by decision:** plan names stay hidden (§8); readiness movement is
not reconstructable from current data (§9); the *Create a quiz* action is an entry point rather than a
direct start (§10); Phase 4 was blocked by absent data rather than by permissions (§11).

**⚠️ Phases 1 and 2 are therefore fully specified and ready for implementation-prompt drafting.**
Phase 3 is scoped to the three free trends. Phase 4 has its privacy contract but still owes a
completion-record design.

---

## 16. Anti-drift checklist

- **⚠️ Do NOT auto-grant `PROGRESS`** on guardian consent, acceptance, or by backfill.
- **⚠️ Do NOT make `PROGRESS` bidirectional** — the caller-is-supporter assertion is load-bearing and
  a test pins that a learner calling `/progress` gets 404.
- **⚠️ Do NOT merge the two grants** while consolidating presentation; each section checks its own.
- **⚠️ Do NOT infer Activity from Progress data**, or show activity-derived values under another label
  when `ACTIVITY` is off — omit them from the **payload**, not just the UI.
- **⚠️ Do NOT expose** private Note titles, Note or Study Pack content, exact weak concepts, specific
  missed questions, plan names, a detailed activity timeline, "last online", or any surveillance-style
  log.
- **⚠️ Do NOT create a supporter-visible "declined" or "pending" state**, and do not narrate the
  cooldown.
- **⚠️ Do NOT model a grant request as a `linked_learner_grants` row.**
- **⚠️ Do NOT build "Quizzes you shared" on `analytics_events`** — telemetry by declaration (`V77`),
  and the event is overloaded across two distinct user actions.
- **⚠️ Do NOT add event sourcing or a history framework** for trends; a readiness snapshot, if ever
  built, is prospective and never backfilled.
- **⚠️ Do NOT implement Assignment** — no assignment entity, inbox, due dates, completion tracking,
  rosters or one-to-many teacher assignment.
- **⚠️ Do NOT add `ProfileType.PARENT` behaviour, a guardian dashboard fork, a parent-only progress
  model, or a relationship-type column.** Learning Connections stays relationship-neutral — one
  surface for parent, guardian, tutor, mentor, sibling, friend.
- **⚠️ Do NOT change what `linked_learner_relationships`, `_invitations`, `_guardian_consents` or
  `linked_learner_grants` mean** — several dated checkpoints read those tables.
- **⚠️ No quota, entitlement or meter change; onboarding untouched.**

---

## 17. Verification

**Phase 1: one scoped cold agent** — it adds a write path on the permission substrate, and the
substrate is the product's only cross-user read. **Phase 2: a single `advisor()` call** — reprojection
of already-permitted data, no new scope, no migration. **Phase 3/4: re-tier at kickoff.**

**Pre-declared discriminating guards:**

1. **A request grants nothing** — after a request, `GET /{relationshipId}/progress` must still 404.
   *A fixture that asserts the request succeeds proves nothing about access.*
2. **Declined is indistinguishable** — the supporter payload after a declined/ignored request must be
   **byte-identical** to one where no request was ever made.
3. **Grant separation under consolidation** — with `ACTIVITY` off and `PROGRESS` on, the response must
   contain **no** streak, study-day or momentum-derived value. *Asserting the UI hides the card is not
   enough — assert the payload.*
4. **Idempotence** — two requests in the cooldown window must leave exactly one live row and must not
   produce a second learner-facing prompt.
5. **Plan names never leave the server** — a learner with named collections must produce a supporter
   payload containing **no** collection title.
