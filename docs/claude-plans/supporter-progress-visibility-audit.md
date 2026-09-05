# How a Guardian / Parent Sees Their Learner's Performance — Audit

**Status:** AUDIT ONLY — nothing implemented. Written 2026-09-05.
**Question:** how does a guardian/parent actually see the performance of the learner they support?

**Short answer: the whole path is built and works — but it is gated on the LEARNER turning it on,
and there is no way for the parent to ask.** A guardian who recorded consent for a minor still sees
nothing until that minor finds and flips a toggle.

---

## 1. The journey as it exists today

| Step | Where | Gate |
|---|---|---|
| 1. Connect | email invite or invitation link | the other party must **accept** |
| 2. Guardian consent (minors) | supporter records it | required to activate, **grants no visibility** |
| 3. **Learner grants PROGRESS** | `/linked-learners`, learner side only | **the blocking step** |
| 4. Entry | Dashboard → *People you support* → **View progress** | `progressSharedWithMe` |
| 5. View | `/linked-learners/{relationshipId}/progress` | live `PROGRESS` grant, re-checked per read |

**The authorization is correct and tight.** `LinkedLearnerReadAuthorizationService.requireAcceptedLearnerId`
asserts the caller is the **supporter**, then requires a live `PROGRESS` grant, then re-verifies the
grant resolved to this relationship's learner — with an explicit comment recording that the caller
check exists because `requireGrant` is symmetric and would otherwise let a learner read their
supporter's progress through a unidirectional route.

**The entry point exists and is correctly scoped.** `dashboard/page.tsx:524-527` filters to
`callerRole === "SUPPORTER"` and `PENDING | ACCEPTED`; `SupportedLearnersCard` renders *"People you
support"* with a per-learner **View progress** link when `progressSharedWithMe` is true.

### What the parent actually sees (`progress/page.tsx`, 115 lines)

Three cards, all point-in-time:

| Card | Content |
|---|---|
| Readiness | `N% ready` · mastered / due / not-started concepts |
| Quiz performance | average recent score · best recent score · Study Packs reviewed |
| Plan progress | `practiced / total` · ready items across N plans |

Payload: `LinkedLearnerProgressResponse(relationshipId, learnerDisplayName, quizPerformance,
readiness, collectionProgress, hasActivity)`.

**The privacy line holds and is stated on the page:** *"Personal notes and study material are never
shown."*

---

## 2. Findings

### A. ⚠️ The parent cannot ask — there is no request mechanism anywhere

Searched `requestGrant` / `requestAccess` / `grantRequest` across backend and frontend: **zero hits.**
The progress toggle renders **only** when `callerRole === "LEARNER"` (`linked-learners/page.tsx:184`),
so a supporter never sees a control of any kind.

What the parent sees instead is an accurate dead end — on the dashboard,
*"{Name} is not sharing progress with you."*, and on `/linked-learners`,
*"{Name} does not share their study progress with you"*. Both are true; neither offers a next step,
and neither tells the learner anything.

**This is the highest-value gap.** The learner must independently discover a toggle on a page they
have little reason to visit.

### B. ⚠️ Guardian consent grants nothing — correct by design, and probably the surprise

`LinkedLearnerGrantService` contains **no** reference to guardian consent. Recording consent
activates the relationship; it confers **no** visibility. So a parent who went through the whole
minor-consent flow still sees nothing until the child shares.

**This is consistent doctrine, not an oversight** — `v0.91.0` ("connecting shares NOTHING"),
`v0.92.0` ("absence of a live grant means NO ACCESS"), `v0.93.0` (consent is asymmetric and gates the
*learner's* data). **⚠️ Do NOT "fix" it by auto-granting PROGRESS on guardian consent.** That would
convert an explicit consent model into an implicit one and is precisely the backfill `v0.93.0`
refused ("it converts an implicit rule into explicit consent nobody gave"). If the product wants
guardians of minors to have default visibility, that is a **deliberate privacy-model decision for the
owner**, not an implementation detail.

### C. ⚠️ The view is a snapshot with no time dimension

There is no trend, no history, no per-subject breakdown and no weak-concept list. A parent's actual
question — *"is my child improving, and where are they struggling?"* — is not answerable from this
page. `MasterySnapshotResponse` is three scalars (`averageRecentScore`, `bestRecentScore`,
`studyPacksReviewed`).

**The underlying data exists** (`ConceptHealth` per pack, completed sessions with timestamps); it is
simply not projected. **⚠️ But per-concept weakness is exactly the data `v0.107.0` locked to
per-pack presentation, never merged across packs** — so a supporter-facing weak-concept view inherits
that constraint and is a real feature, not a formatting change.

### D. ⚠️ Performance and engagement live on different pages

Streaks, longest streak and study-days-this-week come from a **separate** `GET
/{relationshipId}/activity` behind a **separate `ACTIVITY` grant**, and render on `/linked-learners`
— **not** on the progress page. The progress payload carries only a `hasActivity` boolean.

So a parent checking "how is my child doing" must look in two places and hold two grants. `v0.94.0`
split these deliberately (so the activity toggle actually controls something), and that split is
correct — **but nothing consolidates the two for the reader.**

### E. Two grants, two directions — a real asymmetry to communicate

`ACTIVITY` is mutual; `PROGRESS` is **learner → supporter only** (`v0.93.0`, enforced by the caller
check in §1). A parent who enables *"Share my study progress"* on their own card is sharing **their
own** progress, which for a non-learning parent is empty. The labels are accurate but the asymmetry
is not explained anywhere.

### F. `ProfileType.PARENT` does nothing — and must stay that way

**Zero references** to `ProfileType.PARENT` in backend services or `frontend/lib`. Consistent with
standing doctrine: no relationship-type column, no new profile type, **nothing gated on
`ProfileType`** — `v0.89.0` records that axis error as the thing the capability exists to correct.
**⚠️ Recorded so nobody "implements PARENT" by gating on it.**

---

## 3. Recommendations

**1. Add a request-access affordance (smallest, highest value — closes §A).**
On the supporter's card, replace the dead-end sentence with an action: *"Ask {Name} to share their
progress"*. It notifies the learner and deep-links them to their own toggle.

**⚠️ It must not change the privacy model:** the learner still decides, the request creates no
access, and a declined or ignored request must not be inferable as anything other than "not shared".
Rate-limit it so it cannot become nagging, and make it idempotent per relationship.

**2. Consolidate the parent's view (closes §D) — small, data already present.**
When the supporter holds **both** grants, render the activity figures on the progress page rather
than only on the connections list. `hasActivity` already tells the page whether to ask.

**3. Explain the asymmetry (closes §E) — copy only.**
One line under the toggles distinguishing "activity is mutual" from "progress runs learner →
supporter".

**4. Trend over time (§C) — a real feature, not a quick fix.**
The honest answer to "is my child improving". Needs a decision on what is exposed (a readiness series
is safe; a weak-concept list inherits `v0.107.0`'s per-pack constraint). **Out of scope for a patch;
worth its own slice.**

**5. Guardian default visibility (§B) — owner decision, not an implementation choice.**
Recommend **leaving it as-is** unless the owner rules otherwise. If it changes, it is a privacy-model
change requiring its own release and verification tier, and it must be prospective — never a backfill
onto existing consents.

---

## 4. Anti-drift

- **⚠️ Do NOT auto-grant `PROGRESS` on guardian consent, acceptance, or relationship creation.**
- **⚠️ Do NOT make `PROGRESS` bidirectional** — the caller-is-supporter assertion is load-bearing and
  a test pins that a learner calling `/progress` gets 404.
- **⚠️ Do NOT gate anything on `ProfileType.PARENT`**, and do not add a relationship-type column.
- **⚠️ Do NOT expose the learner's notes or study material** — the absolute line since `v0.89.0`.
- **⚠️ Do NOT merge weak concepts across Study Packs** if a concept view is ever added (`v0.107.0`).
- **⚠️ Do NOT change what `linked_learner_relationships`, `_invitations`, `_guardian_consents` or
  `linked_learner_grants` mean** — several dated checkpoints read those tables.
- **⚠️ A request-access feature adds no grant, no scope value and no migration** beyond (at most) a
  request record; if it appears to need a new scope, the scope is wrong.
