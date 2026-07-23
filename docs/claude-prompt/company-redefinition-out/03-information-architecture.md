# Information Architecture: Finalizing Review-Set-Centric Navigation

> Planning document. No code changed. Finalizes the parked direction captured in
> `docs/product/ROADMAP.md` under "Review-Set-Centric Navigation (deferred future direction)" — this
> is not a green-field navigation redesign, it is locking the shape that section already committed to
> in principle. Extends `docs/claude-prompt/company-redefinition-out/01-strategic-redefinition.md`;
> does not reopen or restate its identity/moat argument.

## Decisions carried forward

**Final nav shape (authenticated in-app shell only — the public marketing navbar in `navigation.md`
is untouched):** `Dashboard / My Reviews / Library / Explore / Progress`.

- **Dashboard** — hero slot is the learner's Primary Review Set (`primaryCollectionId`) as a condensed
  card (Identity → Current Journey → Primary Action → Readiness, reusing the v0.41.1 detail-page
  hierarchy at summary depth); falls back to the existing goal-prompt flow when no Primary Review Set
  is set. The full subject-by-subject rollup moves off Dashboard entirely (lives only on Progress, not
  duplicated). Any "browse more" pointer now targets Explore, not Public Library or Exam Hub directly.
- **My Reviews** — `/collections`, the learner's owned + adopted Review Sets list (existing v0.41.1
  Primary-card list treatment). Label always resolves through `getCollectionLabels` — never hardcoded.
- **Library** — unchanged: `/library`, private notes workspace. Nothing structural moves out; Library
  and Collections have always been separate concerns.
- **Explore** — a new *nav item*, not a new canonical URL. It composites the existing Official Review
  Set catalog (`/collections/published`, data/components reused, URL untouched) and the existing,
  unchanged `/public/library` behind a segmented `Review Sets` / `Notes` control, plus a pointer out to
  the Exam Hub index (`/exam`). All underlying routes, filters, sitemaps, and structured data stay
  exactly as documented today.
- **Progress** — `/progress`, promoted from sub-page (drop its `← Dashboard` back link — a concrete
  `navigation.md` gap this session surfaces) to a first-class main page. Default view = Primary Review
  Set via the existing `PlanPicker` + `?collectionId=` machinery; `All Subjects` stays a first-class
  peer option in the same picker so notes outside any Review Set are never orphaned. Does not undo the
  v0.36.0 Progress/Readiness unification.

**Explore convergence mechanism:** `/exam/[slug]` stays exactly as-is — anonymous-SEO, same URL, same
default CTAs. It gains one additive check: resolve the hub's configured `courseProgram`(s) against the
existing published-Official-Review-Set lookup (the same lookup Public Library's "Browse official
plans" pointer already uses). A match adds a preview + adopt path that reuses `/collections/published`'s
existing anonymous preview and the copy-funnel's existing `redirectTo` query-param handoff — no new
cookie field needed. No match leaves the hub's existing fallback CTA untouched. That "no match" case
*is* the admin-curation-gap, flagged again below, not solved here.

**Dashboard/Progress reorg rule:** organize around `primaryCollectionId` when set; fall back to the
existing goal-prompt mechanism when it isn't. This session proposes, as a new and explicitly flagged
seam, that adopting an Official Review Set with no existing Primary sets it as Primary — that is a
*recommendation*, not a resolution of ROADMAP's still-open Primary-Review-Set-vs-Study/Exam-Focus
philosophy question, which stays open and unaffected by this IA lock-in.

**Gap-check:** Explore's Review-Sets mode plus the Exam Hub deep-link above is exactly where a learner
without a matching Official Review Set gets routed today, with a graceful fallback into Notes
browsing. Producing more Official Review Sets remains bottlenecked on the separate, still-unscoped
Curator pipeline — flagged again, intentionally not solved by this IA session.

---

## 1. Finalized nav shape

### 1.1 The five items, what each shows, and why

| Item | Route | Shows | Audience gate |
|---|---|---|---|
| Dashboard | `/dashboard` | Primary Review Set hero (or goal-prompt fallback) | authenticated only (unchanged) |
| My Reviews | `/collections` | owned + adopted Review Sets, Primary-treatment list | authenticated only |
| Library | `/library` | private notes workspace, unchanged | authenticated only (unchanged) |
| Explore | *(nav item only — see §2)* | Official Review Set catalog + Public Library + Exam Hub index, composited | authenticated only (nav item); underlying content stays independently anonymous-reachable |
| Progress | `/progress` | mastery/readiness, defaulted to Primary Review Set | authenticated only (unchanged) |

Every label in this table that is profile-dependent copy ("My Reviews," the Review-Set-hero title,
any "Continue your {label}" CTA) resolves through the existing `getCollectionLabels` pattern —
Student sees "Study Plan(s)," Board Exam sees "Review Set(s)," Teacher sees "Lesson Plan(s),"
Professional/Parent fall back to the generic "Collection(s)" the enum already supports with no
feature-specific implementation (per `CLAUDE.md`: "`PARENT` and `PROFESSIONAL` exist as enum values
with no feature implementation yet"). None of this is new copy infrastructure — it is applying the
pattern that already exists to five nav-level surfaces instead of only the collection detail page.

### 1.2 What moves, precisely

**Out of Dashboard:**
- The full subject-by-subject mastery stats block (wherever Dashboard currently surfaces it) —
  relocates entirely to Progress. It is not duplicated on both pages; Dashboard's job narrows to "what
  should I be doing right now," Progress's job is "how am I actually doing," per the ROADMAP's own
  framing ("Dashboard asks 'what review are you preparing for?'; Progress answers 'what's happening
  with my primary study journey?'").
- Direct links to Public Library or Exam Hub (e.g. the goal card's `Browse {goalName} notes →`, the
  course/program CTA) — these now point at Explore instead, carrying the same filter params they
  already carry today (`courseProgram=`, exam slug), so no query-contract changes, just a different
  landing shell.

**Out of Library:** nothing structural. `library.md` documents Library as strictly a private-notes
workspace (`PRIVATE` notes, own `PUBLIC` notes, Draft/Study-Pack-Ready status) with no Collection
membership surface described anywhere in it — Library and Collections have always been two separate
concerns in this codebase, so there is nothing to extract. The only change is cosmetic: any future
"organize these notes into a plan" pointer inside Library should point at My Reviews, not invent a new
destination.

**Into My Reviews:** the `/collections` list is promoted to a first-class top-level nav item. This
document does not assert where `/collections` is reached from today (that wasn't in the read scope,
and `navigation.md`'s existing back-link table does not list it) — only that, going forward, it is a
main page (no back link) reachable directly from primary nav, not solely a drill-down destination.

**Into Progress:** the subject rollup described above, arriving from Dashboard, and — new in this
session — a Primary-Review-Set-defaulted `PlanPicker` state (see §3).

### 1.3 `navigation.md` gap this session surfaces

`navigation.md`'s existing Back Navigation Pattern table currently makes **Progress a sub-page of
Dashboard** — `My Progress | Destination: Dashboard | Label: Dashboard` — and does not list Progress
among the documented main pages (`Dashboard, Library, Public Library, My Profile, Settings`).
Promoting Progress to a peer top-level nav item means:

- Drop Progress's `← Dashboard` back link; Progress joins the main-page list.
- Add `/collections` (My Reviews) to the main-page list as a new nav-reachable destination, no back
  link.
- Add `/collections/published` to the main-page list for the same reason (it becomes directly
  reachable through Explore's `Review Sets` mode) — its content and URL are otherwise untouched.

This is the concrete `navigation.md` update this IA lock-in requires; it is called out here rather
than silently implied so a future implementation pass has an explicit checklist instead of having to
re-derive it from the back-link table.

---

## 2. The Explore surface design

### 2.1 What Explore is — and, importantly, what it is not

Explore is **an authenticated in-app nav item, not a new canonical URL that other routes migrate
into.** Its constituent surfaces — the Official Review Set catalog and Public Library — are each
*already* fully anonymous-accessible at their own existing routes (`/collections/published` and
`/public/library` respectively, both documented as anonymous-browsable in `public-library.md`). Explore
does not take over either URL, redirect either URL, or require either page to be rebuilt. It exists
because a logged-in user currently has to already know that "browse notes" (Public Library) and
"browse curated Review Sets" (Collections) are two different destinations — Explore is the one nav
entry that makes both reachable without that prior knowledge, plus a pointer to the Exam Hub index for
users who want to revisit or share a hub page.

This is the same "convergence, not rebuild" instruction from the hard constraints and from doc 01's own
language ("Any future IA work here is a hand-off design ... not a rebuild") applied literally: no
existing canonical URL changes ownership, no sitemap entry moves, no structured-data page is retired.

### 2.2 The page itself: a segmented control over two reused surfaces

`/explore` (new route, nav-item only) renders:

1. **A segmented control**: `Review Sets` (default) / `Notes`.
2. **`Review Sets` mode** — the same data and card components `/collections/published` already uses
   (`PublicStudyPlanCard`, the existing `Preview this plan` disclosure, the existing Start/Continue
   adopt CTA, the existing `Official` identity badge), filtered by Course/Program the same way Public
   Library's course/program filter already works. `/collections/published` itself is untouched and
   keeps working standalone for anonymous traffic and any existing inbound links; Explore's Review Sets
   mode is a second, composed presentation of the same backend data, not a takeover.
3. **`Notes` mode** — clicking it is a plain navigation to the real `/public/library` (not a client-side
   tab swap), because Public Library's own URL-synced filter state, discovery/filter-mode logic,
   canonical tag/subject links, and SEO metadata are all documented as depending on that exact route
   staying stable. The persistent authenticated nav bar still renders on `/public/library` for a
   logged-in visitor (as it presumably already does today, since `library.md`/`navigation.md` list
   Public Library among the authenticated shell's main pages) with `Explore` shown as the active nav
   item — the same "active tab spans multiple routes" pattern Library already uses across `/library`
   and `/library/exam-builder`.
4. **An Exam Hub pointer** — a small persistent card/rail linking to `/exam` (the static index of all
   four hubs), so an authenticated user can still reach or share a hub page without that page being
   folded into the authenticated shell. `/exam/[slug]` pages remain reachable and unchanged for both
   anonymous and authenticated visitors; when an authenticated visitor is on one, the shell nav shows
   `Explore` active there too.

Net new frontend surface: one new route (`/explore`) that is mostly composition — a segmented control
plus two already-existing card/list components reused as-is. No new backend endpoint is required; both
constituent data sources (`GET /notes/public` variants, published-collections lookup) already exist.

### 2.3 Anonymous visitor vs. authenticated visitor

**Anonymous visitor:** never sees "Explore" as a concept, a nav label, or a route — because the
authenticated shell nav (where Explore lives) doesn't render for them at all. They keep experiencing
exactly what's documented today: `/exam/[slug]` as the anonymous-SEO entry point with its existing
intent-preserving signup CTA; `/public/library` and `/public/library/{subject}` as fully independent,
anonymous-complete discovery surfaces with their own conversion CTAs; `/collections/published` as an
anonymous-browsable catalog with preview + "Sign in to adopt." This document proposes **zero behavior
change for anonymous users** — the whole point of the convergence is to give the *authenticated* nav
one coherent destination, not to touch the anonymous acquisition mechanics that `exam-hub.md` and
`public-library.md` already carefully document (including the explicit design decision that
`PublicLibraryBackLink` returns `null` for guests, and that Exam Hub's zero-note states must never
dead-end).

**Authenticated visitor:** sees the composited `/explore` page described in §2.2, reached via the new
`Explore` nav item, with `Review Sets` and `Notes` as one browsing experience instead of two
unconnected destinations they'd have to discover separately.

### 2.4 The exact `/exam/[slug]` → Official Review Set deep-link mechanism

1. `frontend/lib/exam-hub-config.ts` already maps each exam slug to its `courseProgram` alias(es)
   (`ale` → `Architecture`, `pnle` → `Nursing` + `Medical – Surgical Nursing`, etc.). This mapping is
   reused as-is, unmodified.
2. Add one lookup call on the hub page: resolve the hub's `courseProgram`(s) against the existing
   published-Official-Review-Set-by-courseProgram lookup — the same `listPublicStudyPlans({
   courseProgram })` call `public-library.md` documents Public Library's "Browse official plans"
   pointer already using. No new backend endpoint.
3. **If a match exists:** render a preview + CTA block on the hub page (below the existing Product
   Value Strip, above the discovery sections), reusing the existing `Preview this plan` disclosure
   component from `PublicStudyPlanCard` — so an anonymous visitor gets the same read-only preview
   `/collections/published` already offers, in-line on the hub page, with no new component.
   - **Anonymous visitor, CTA:** `Sign in to start the Official {label}`. Reuse the existing copy-funnel
     `redirectTo` query-param handoff pattern (`/signup?redirectTo=/collections/published/{id}?adopt=1`,
     mirroring the pattern `public-library.md` already documents for note-copy signup) instead of adding
     a new cookie field to `notelib-exam-intent` — this is the lower-surface-area option and is
     recommended over extending the cookie schema, though extending the cookie with a nullable matched-
     collection id remains a viable fallback if the `redirectTo` round-trip proves awkward for this
     specific adopt action once actually implemented.
   - **Authenticated visitor, not yet adopted, CTA:** `Start Official {label}` → the same
     `/collections/published/{id}` preview → existing snapshot-copy adopt (zero AI call) → lands on the
     collection detail-as-study-dashboard (already shipped v0.41.1).
   - **Authenticated visitor, already adopted (this collection or any Review Set for this
     courseProgram):** CTA becomes `Continue {label}` → straight to the owned collection detail page.
4. **If no match exists:** the hub's existing CTA and copy are completely untouched — anonymous
   `Start preparing for {exam}` with intent-cookie signup, authenticated `Browse {courseProgram} Notes`
   (now landing in Explore's `Notes` mode instead of bare `/public/library`, same filter param). No new
   empty state is introduced; this is the same "never dead-end" principle `exam-hub.md` already commits
   to for zero-note states, extended to the zero-Official-Review-Set case.

This mechanism only touches the hub page's rendering (one new conditional block) and the exam config's
consumer, not the config data model itself, `/collections/published`, or `/public/library`.

---

## 3. Dashboard and Progress reorganized around the Primary Review Set

### 3.1 Dashboard

**When `primaryCollectionId` is set:** the hero slot (top of Dashboard, above everything else) is a
condensed version of the existing v0.41.1 Review-Set-detail hierarchy — Identity, Current Journey
step, one Primary Action button (e.g. "Continue Studying"), Readiness percentage. Clicking the card's
title/identity opens the full detail page (`/collections/{id}`), which keeps its full existing
hierarchy (Guidance, Subject Plans/Notes tabs) untouched — this reorg only adds a *summary* card on
Dashboard, it does not change the detail page itself.

**When no `primaryCollectionId` is set:** Dashboard falls back to exactly what exists today — the
`GoalPromptBanner` / `DashboardGoalCard` flow (exam-intent cookie first, then `courseProgram`
fallback, per `exam-hub.md`'s existing goal-suggestion order). No behavior change to that mechanism by
itself.

**New, explicitly flagged seam connecting the two:** when a learner with no existing
`primaryCollectionId` adopts an Official Review Set (whether via Explore directly or via the Exam Hub
deep-link in §2.4), this document recommends that adoption sets it as their Primary Review Set. This
is a genuinely new proposed behavior, not something already documented as existing — it is the natural
seam this reorg creates between the pre-adoption `studyGoal` signal and the post-adoption
`primaryCollectionId` signal, but it is a **recommendation for a future implementation pass to confirm
against the still-open ROADMAP question** ("Primary-Review-Set-vs-Study/Exam-Focus philosophy
question," flagged as blocking Personalization gating) — this document does not close that question,
it proposes one resolution for the specific "just adopted, has no Primary yet" case, which is narrower
than the full philosophy question and shouldn't be read as settling it.

**Removed from Dashboard's default view:** the full subject-by-subject stats block, per §1.2 — it
lives only on Progress now.

### 3.2 Progress

**Default view = Primary Review Set**, using the `PlanPicker` + `?collectionId=` machinery that
already exists (per ROADMAP's own correction: "The `PlanPicker` + `?collectionId=` machinery already
exists; a future reorg defaults it to the primary"). Concretely: `PlanPicker`'s default selection
changes from whatever it defaults to today to `primaryCollectionId` when one is set.

**All-subjects rollup stays reachable:** `All Subjects` remains a first-class, always-visible peer
option inside the same `PlanPicker` — never removed, never demoted to a buried secondary link. This is
the mechanism that keeps notes outside any Review Set from being orphaned: a user who has never adopted
or created a Review Set (or has notes outside their Primary Review Set's scope) can still select `All
Subjects` from the same picker and see the full existing subject-mastery breakdown, unchanged.

**Does not undo v0.36.0 Progress/Readiness unification:** the underlying readiness computation, the
`ConceptHealth`-driven signal, and the single unified `/progress` data source are untouched — this
reorg only changes what `PlanPicker` defaults to, not how mastery/readiness is computed or which store
it reads from.

**Goal Summary reconciliation:** if a `studyGoal` is set that does *not* match the Primary Review Set's
`courseProgram` (a real possible state, since these are two separate existing fields), the existing
`GoalSummaryResponse` header shows as a secondary note rather than the page's main frame — the Primary
Review Set view is authoritative for the page's default framing once one exists; the goal summary
becomes a secondary nudge, not a competing primary frame. This is the same recommended-not-settled
caveat as §3.1: it is this document's proposed reconciliation for the IA layer, not a resolution of the
underlying open philosophy question.

---

## 4. Gap-check against the R1 (activation) session's dependency

**Confirmed:** a learner who lands on an Exam Hub for one of the four course/programs with a real,
published, top-level Official Review Set (Accountancy/CPALE, Architecture/ALE, Education/LET,
Nursing/PNLE — per doc 01's production pull) is now routed, via the §2.4 mechanism, directly into that
Official Review Set's preview-and-adopt flow from the hub page itself — the same "zero-decision
activation" R1 depends on, now reachable one step earlier (from the hub, not only after separately
discovering Explore or Collections).

**Confirmed, the fallback case:** a learner whose exam has an Exam Hub page but no matching Official
Review Set yet (any current Wave 2 candidate — Civil Engineering, Electrical Engineering, Mechanical
Engineering, Pharmacy, Physical Therapy, Civil Service Exam — or any future exam added to the hub
config before curation catches up) is routed into Explore's `Notes` mode via the hub's existing,
untouched `Browse {courseProgram} Notes` CTA. No dead end is introduced; this matches the "never
dead-end" principle `exam-hub.md` already commits to for zero-note states.

**Flagged again, not solved here — the admin-curation-gap:** the reason only four course/programs have
this deep-link available today is that producing a new Official Review Set depends on the separate,
still-unscoped Curator pipeline (`docs/product/ROADMAP.md`: "Curator pipeline: public notes → suggest
Subject Plans → map notes → generate Companion → human review → publish... Not scoped to a version
yet"). This IA session achieves the specific property the ROADMAP direction asks for — "Publishing
another Official Review Set should require no new frontend" — because Explore's catalog and the §2.4
hub deep-link both key off the existing published-collection lookup: the moment a curator publishes a
new Official Review Set for, say, Pharmacy, it appears in Explore's `Review Sets` mode and becomes
deep-linkable from a (future) Pharmacy Exam Hub with zero additional nav or routing work. What this
session does **not** do is build or accelerate the Curator pipeline itself — the throughput of *how many*
exams get a real Official Review Set, and how fast, remains exactly as gated and unscoped as ROADMAP
already states. This is the same dependency doc 01 names in its own retention-risk framing (only four
course/programs have real depth today) — restated here as a routing-layer fact, not re-solved.

---

## 5. Constraint compliance checklist

- **Curation, never generation:** untouched. Explore only browses and the adopt action is the existing
  free/idempotent snapshot copy (zero AI call); no generation is introduced by this nav reorg.
- **Internal Curator vs. Learning Assistant split:** untouched. Nothing in this document adds a
  learner-facing generation surface or blurs who authors Official Review Set content — Explore and the
  Exam Hub deep-link are pure discovery/routing.
- **Exam Hub preserved, not replaced:** confirmed throughout §2 and §4 — `/exam/[slug]` keeps its URL,
  its anonymous-SEO role, its existing CTAs and zero-note handling; it gains one additive conditional
  block, nothing is removed.
- **`getCollectionLabels` for any Review-Set-style label:** applied throughout §1.1, §3.1, §3.2, and
  §2.4's CTA copy — no hardcoded "Primary Review"/"My Reviews" universal copy anywhere in this design.
- **Horizontal across Student/Teacher/Professional:** the nav shape, Explore composition, and
  Dashboard/Progress reorg rule are profile-agnostic mechanics (`primaryCollectionId`, `PlanPicker`,
  `getCollectionLabels`) — nothing here is board-exam-specific except §2.4's hub deep-link, which is
  additive to Exam Hub pages that already only exist for board-exam course/programs and does not change
  Dashboard/Progress/Explore/My Reviews behavior for any other profile type.
