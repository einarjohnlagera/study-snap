# The Discovery System — Explore as the public discovery front door

## Status: pre-implementation pressure test only. Nothing in this document is ratified, kicked off, or scoped for a version. No code has changed as a result of this file.

## Independent review note (2026-07-31)

This document went through a second pass: a fresh-context Opus review, given no prior conversation
context, told to re-derive every citation against the real code rather than trust the first draft, and
to argue the opposite side of every contested call. It found the core diagnosis (Explore's premise
doesn't hold yet) correct, but it also found **one fabricated citation**, three findings whose
conclusions were wrong even though their literal facts were right, one live production bug unrelated
to this proposal, and a time-sensitive dependency the first draft missed entirely. All of it is folded
into the document below; nothing from the original draft survives unexamined. Corrections are marked
inline as **[corrected on review]**.

The most consequential single correction: **Finding D-ii originally cited an active "Wave 2 Exam Hubs"
roadmap initiative that does not exist.** That phrase came from `docs/gpt-contexts/GPT_CONTEXT.md`
(session scratch notes), not `ROADMAP.md`, and the real status is the opposite of what was claimed —
further Exam Hub expansion is explicitly deferred, low-priority, gated on a content-depth threshold no
candidate has cleared. Had this shipped into `ROADMAP.md`'s Backlog Index as originally proposed, it
would have planted a false fact for a future session to cite as settled. It has been removed and the
finding rewritten on its one argument that actually holds.

**Third-pass note (2026-07-31, later the same day):** the owner supplied a product-architecture
correction — the document's framing was still page-oriented ("should Explore replace or absorb Public
Library?") when the real question is "what system owns discovery in NoteLib?" This pass reframes the
whole document under a named **Discovery System** concept without touching any verified technical
finding, correction, route table, SEO safeguard, staging plan, or open decision from the two passes
above — those are load-bearing and survive unexamined. It also adds three things verification turned up
that the framing pass exposed: `AGENTS.md:671` blocks Stage 3 in absolute terms (a doctrine conflict the
first two passes under-weighted), `AGENTS.md:621` is stale relative to shipped behavior and would
contradict any never-redirect rule for subject pages, and the 2026-09-13 checkpoint's segmentation gap
is narrower than originally scoped — `ROADMAP.md` already mandates `source` segmentation this release;
what's missing is a viewer-type dimension on top of it, not new instrumentation. New corrections are
marked **[corrected on review, third pass]**.

## Framing correction, up front

The request that produced this document called it "additional tweaks for this release" (`v0.67.0`).
It isn't. The direction described — Explore as the *anonymous* front door, marketing-nav changes,
and backward-compatible redirects for existing public URLs — has a release-sized prerequisite
(Stage 0 below) that doesn't exist in the codebase today, plus a redirect stage that can only be
verified safe after weeks of post-ship indexing evidence. Per this project's own kickoff discipline,
this cannot land inside `v0.67.0`. It's a new, separately-gated initiative.

**[corrected on review]** It is not, however, in the same "still gated" category as the
"Review-Set-Centric Navigation" section in `ROADMAP.md` — that section's own gate (Primary Review Set
proving useful) was marked **satisfied 2026-07-30**, and its drafted nav shape has already been
reached. This proposal is genuinely new scope, not a continuation of already-cleared work.

**[corrected on review, third pass]** There is a second framing correction, orthogonal to sizing: the
question this document answers is not "should Explore replace or absorb Public Library?" That framing
made every subsequent finding read as a negotiation over which pages survive. The real question is
"what system owns discovery in NoteLib?" — answered in the new section immediately below. Sizing and
staging are unchanged by this; the reframing changes how the findings are read, not what they say.

## The Discovery System

**This section is new (added 2026-07-31, third pass) at the owner's direction — it supplies the
organizing principle the first two passes lacked.** Every finding below still holds; this reframes why
they resolve the way they do rather than changing any of them.

**The question this document answers is not "should Explore replace or absorb Public Library?" It is
"what system owns discovery in NoteLib?"**

> The Discovery System helps learners find knowledge and structured learning experiences that are not
> yet part of their personal workspace. Explore is its primary interface.

Today, that system includes: the Explore interface; public/community notes (the `/public/library`
route family); Official Review Sets (`/collections/published`); Exam Hubs (`/exam`, `/exam/{slug}`);
course/program discovery; subject discovery. It may eventually include featured resources, recently
shared content, trending content, and further curated discovery surfaces.

Public Library is one existing content source and route family inside that system. Exam Hubs are
another curated discovery surface inside it. Official Review Sets are another. None of these are
demoted by naming the system — they are what the system is made of.

**Two principles:**

> Discovery is a product responsibility, not a route. Explore is its primary interface; Public Library,
> Official Review Sets, and Exam Hubs are discovery sources and content surfaces within it.

> Navigation may converge without deleting or collapsing independently valuable public content routes.

**This is not a second doctrine competing with the locked one.** `AGENTS.md`'s "Explore Owns Discovery"
(locked 2026-07-30, § Page Responsibility Rule) is the **authenticated-navigation scope** of this same
doctrine — it already states Explore is the single owner of content discovery for signed-in users.
"Discovery System" names the full source set that doctrine governs and extends the scope toward
eventual anonymous access. One doctrine, two scopes, not two doctrines. Any future doc language should
say so explicitly rather than let a later session read them as separate ideas.

**The loophole this opens, closed explicitly:** the second principle above ("navigation may converge
without deleting content routes") could be misread as license to converge the bare `/public/library`
list page immediately, on the theory that a list page is "navigation" rather than "content." It is not
license for that. The bare list page is the single ambiguous case in this whole document: it is
classified as navigation for ownership purposes (Explore may eventually be the thing users are pointed
at first), but it keeps full content-route protections — canonical tag, sitemap entry, 200 status —
until Stage 3's own evidence gate clears independently, exactly as staged below. **Framing does not
clear evidence gates.**

**What this reframing dissolves, not just reconciles:** the owner's own recorded direction in
`ROADMAP.md`'s "Review-Set-Centric Navigation" section — "Public Library preserved as a distinct
discovery path... the Public Library is not absorbed or removed" — stays **true** under this framing.
Public Library remains a Discovery System source and an independently addressable route family; only
its claim on first-class *navigation* changes. That is a narrowing of an existing direction, not a
reversal of it, and it is the resolution proposed for Conflict 3 below. (See "The doctrine tension" —
this reframing does not, on its own, resolve Conflicts 1 and 2, which are about literal `AGENTS.md`
text that still needs an owner-approved amendment.)

**One open item this framing surfaces and does not resolve — flagged, not decided here.** The owner's
Discovery System framing assigns Companion the governing question "What should I do next?"
`AGENTS.md`'s Page Responsibility Rule table currently assigns "What should I do now?" to Dashboard and
gives Companion a narrower question, "What guidance applies to this curated journey?" Two surfaces
answering the same question breaks the table's own one-question-per-surface discipline and leaves
Dashboard without one. Library, Review Sets, Explore, and Progress all match the owner's framing
against `AGENTS.md` with no daylight between them — Companion is the one collision, and it needs an
explicit owner call, not a silent pick of one wording over the other. (This is a narrower question than
the one `ROADMAP.md`'s Backlog row on "Companion Guidance Doctrine" already settled — that row rejected
merging three structurally different things into one user-facing "Companion" brand; this is only about
which sentence describes Companion's governing question, and doesn't reopen that decision.)

## The doctrine tension — three conflicts, not two, and the sharpest one is a literal blocker

**[corrected on review, third pass]** The first two passes found two conflicts and, on the second pass,
reordered which one leads. Verification for this pass found a third — inside `AGENTS.md` itself — that
is sharper than either: it doesn't need interpretation to read as blocking, it says so directly. It
leads below.

**Conflict 1 — `AGENTS.md:671`, the `### Explore Navigation Rule`, is an absolute prohibition, not a
soft steer:**

> Explore must reuse the existing Official Review Set catalog and Public Library rendering. It must not
> replace, **redirect**, or redefine the canonical `/collections/published` and `/public/library` routes.

This names redirect explicitly. **Stage 3 — the only stage in this document that redirects anything —
does not exist as a real stage until this line is amended.** Any reading of the document that treats
Stage 3 as merely evidence-gated, without also being doctrine-blocked, is incomplete.

**Conflict 2 — the `AGENTS.md` doctrine locked the day before this request arrived** (commit
`455ed669`, on `feat/collections-page-alignment`, not yet merged past `releases/v0.67.0` — i.e. not
even on `main` yet), § Page Responsibility Rule:

> `/public/library` and `/collections/published` remain canonical, separately-addressable routes
> (deep links, SEO, anonymous access) — a navigation-level claim, not a route deletion.

This is softer than Conflict 1 — arguably compatible with a list-page-only redirect if "route deletion"
is read narrowly — but it still needs the same amendment to say so explicitly rather than leaving two
`AGENTS.md` sections implying different things about the same route.

**Conflict 3 — the owner's own recorded product direction.** `ROADMAP.md`'s "Review-Set-Centric
Navigation" section quotes "Direction, as stated by the user" verbatim:

> **Public Library preserved as a distinct discovery path.** "I want to browse notes" (Public Library)
> and "I want to study for an exam" (Review Sets) are two valid entry points to the same notes; **the
> Public Library is not absorbed or removed.**

Under the Discovery System framing above, this conflict **dissolves rather than needing reconciliation**:
Public Library stays a distinct discovery path and stays not-absorbed — it is a source and a route
family inside the Discovery System, unchanged by which surface owns first-class navigation. What
changes is narrower than the owner's original statement addressed, so it doesn't contradict it.

**What still requires an explicit, dated `AGENTS.md` amendment (Conflicts 1 and 2 — text, not
philosophy):**

- **The `/public/library` list page only** is a legitimate future redirect target, *once its
  replacement is live, public, and verified equivalent* (see Stage 3 below), *and* once `AGENTS.md:671`
  and `:849` are amended to say so — today they don't.
- Individual public note pages (`/public/library/{subject}/{slug}`) **and subject-listing pages**
  (`/public/library/{subject}`) are never redirected, full stop. These are content, not discovery — the
  same category as `/collections/{id}` never being absorbed into anything.
- This needs a **dated amendment** to `AGENTS.md`'s `### Page Responsibility Rule` (covering both
  `:849` and `:671` in one place) — not a silent reinterpretation later by whoever implements this.

## The most time-sensitive dependency — should be decided first, not fourth

This release already committed to a dated, measurable checkpoint. `ROADMAP.md:232`:

> `[CHECKPOINT — due 2026-09-13]` … a new `AnalyticsEventType` on Explore nav engagement ships inside
> `v0.67.0` itself, then gets checked against pre-launch baseline ~30-45 days later … whether
> Explore-driven adopt/preview activity meaningfully exceeds what the old direct `/collections/published`
> and `/public/library` nav entries drove pre-launch.

That baseline is specifically **authenticated-nav-driven engagement**. Making `/explore` anonymous
before 2026-09-13 injects an entirely new, un-baselined population into the exact metric this release
is measuring — and it would do so silently: anonymous analytics events are accepted and persisted
today (the backend's `user_id` column is nullable, the endpoint has no auth requirement and no rate
limiter), so nothing would reject or flag the contamination. Whoever runs the 2026-09-13 read would be
comparing an authenticated-only baseline against a mixed anonymous+authenticated result without
knowing it, unless a `source`/viewer-type segmentation is added before Stage 0 ships.

**[corrected on review, third pass]** This is narrower than originally scoped. `ROADMAP.md:232` already
mandates, shipping inside `v0.67.0` itself: "The checkpoint analysis must segment pointer-originated
`EXPLORE_VIEWED` events using `source` metadata from direct/nav views." The segmentation mechanism
exists and ships this release — what it segments is pointer-origin (Dashboard pointer vs. direct
nav/URL), not viewer type (anonymous vs. authenticated). The gap is a **viewer-type dimension added on
top of an existing mechanism**, not new instrumentation built from nothing. This does not lower the
urgency — the contamination is exactly as silent if that dimension is missing — but it changes the
cost comparison: "wait until 2026-09-13" is no longer the automatically cheap default, since the work
needed to ship Stage 0 safely earlier is smaller than the first pass implied.

**This should be the owner's first decision on this whole proposal, not the last:** does Stage 0 wait
until after 2026-09-13, or does it ship earlier with an added viewer-type dimension on the checkpoint's
existing segmentation plan? Getting this wrong doesn't just risk a bad decision later — it corrupts a
measurement this release has already promised to produce.

## Finding A: Explore is authenticated-only today — the proposal's central premise doesn't hold yet

`frontend/app/explore/explore-page-client.tsx` gates its entire render on
`requireAuthenticatedOnboardedUser(router)`, which is a **client-side** check
(`frontend/lib/route-guards.ts` reads `getAuthUser()` and calls `router.replace(...)` to `/login` if
absent — there is no server-side auth, no middleware, and this repo has no `middleware.ts` at all).
**[confirmed on review, including the specific line numbers: `explore-page-client.tsx:37-42` for the
gate, `:62-64` for the blank-render-until-ready behavior.]**

Meanwhile, every route that is a Discovery System source today — content Explore would surface, filter
into, or link out to, not routes Explore needs to absorb — is genuinely anonymous, with real SEO
investment behind it:

| Route | Anonymous? | SEO surface |
|---|---|---|
| `/public/library` | Yes — server component, no auth check | `StructuredDataScript` (CollectionPage JSON-LD), canonical metadata, sitemap entry (daily, priority 0.9) |
| `/public/library/{subject}` | Yes | canonical metadata, conditionally `noIndex` below `SUBJECT_PAGE_INDEX_THRESHOLD`, sitemap entry when indexed, **plus its own BreadcrumbList JSON-LD (see Finding C)** |
| `/public/library/{subject}/{slug}` | Yes | canonical metadata (verified: e.g. `https://notelib.app/public/library/science/cell-structure`), sitemap entry per note, **plus a CollectionPage/Article block and a BreadcrumbList block that hardcodes `/public/library` as its parent URL** |
| `/exam` (Exam Hub index) | Yes | canonical metadata, sitemap entry (**weekly**, 0.9 — [corrected on review; originally miswritten as daily]) |
| `/exam/{slug}` (each hub) | Yes | canonical metadata, sitemap entry per slug (daily, 0.9) |
| `/collections/published` | Yes — no server auth gate; `getAuthUser()` used only to soften CTA copy, not to block rendering | not in sitemap today, but publicly reachable |
| `/explore` | **No** — client-side redirect to `/login` for any signed-out visitor | not in sitemap; **already has title-only page metadata (`app/explore/page.tsx`) but no canonical/OG/structured-data**; **crawlable per `robots.ts` today — nothing currently disallows it, it's simply unlinked from any anonymous page** |

**This is not a small gap.** "Explore should become the single public and authenticated discovery
destination" describes two different things wearing one name. The authenticated half already happened
in `v0.67.0`. The public half requires new work this proposal doesn't name — see the revised Stage 0
scope below, which is materially larger than the original draft stated.

**A cheaper alternative exists and should be named as a real option, not skipped past.** If the actual
goal is "existing backlinks and nav point somewhere coherent," you do not need `/explore` itself to
become anonymous. You could instead retarget the marketing nav item and Exam Hub's outbound links at
`/public/library` directly — already anonymous, already canonical, already sitemap-indexed, and already
the exact content Explore's own Notes tab renders. The "single front door" then becomes a *naming and
routing* decision, not an *auth architecture* decision, and none of Stage 0 is required. Making
`/explore` itself the public URL is a legitimate product preference (a consistent brand name for
discovery everywhere), but it is a preference, not a technical necessity — the owner should decide it
knowing the cheaper path exists.

## Finding B: the gate is not "one place" — there's a real backend blocker, and it exposed a live bug

**[corrected on review — the original claim was half right and the reviewer found something new while
checking it.]**

The half that holds: `PublicLibraryPageClient` and `PublishedPlansPageClient` — the two components
`/explore`'s tabs already composite — are not auth-coupled themselves. They're the exact same
components already rendering anonymously today at their standalone routes. Nothing anonymous-facing in
either component triggers the frontend's hard `location.replace`-to-login behavior on a 401.

The half that doesn't: **two backend endpoints have no anonymous permit rule and fall through to
"authenticated by default."** `GET /subjects` and `GET /course-programs` are not listed in
`SecurityConfig`'s permitted paths, so an anonymous request gets a 401, even though both controllers
were written to serve public `scope=public` traffic (they only throw for `scope=mine`). Stage 0 needs a
real backend change — two `permitAll()` additions — not just a frontend gate removal.

**This is also a live, independent bug, unrelated to whether this proposal ever proceeds.**
`PublicLibraryPageClient` calls these two endpoints via bare `fetch` wrapped in `Promise.allSettled`,
so the 401s are swallowed silently. **Anonymous visitors to `/public/library` today already see broken,
empty Subject and Course/Program filter dropdowns.** Worth fixing now, independent of this whole
direction — same size and shape as the `/exam` BackLink fix in Finding D below.

**The anonymous-adoption pattern originally proposed for Stage 0 also doesn't hold as stated.** The
draft suggested reusing "the existing anonymous-intent redirect pattern from Exam Hub adoption"
(`/auth?mode=signup&intent=exam&exam={slug}&redirect=/exam/{slug}`). `docs/features/exam-hub.md`'s own
documented caveat, added by this release's own audit, says:

> The `redirect` param only takes effect for a *returning* visitor who switches to login —
> `resolvePostLoginDestination` sends a brand-new signup through `/verify-email`/`/onboarding` first, so
> `redirect` is dropped on the primary `mode=signup` path. The existing `notelib-exam-intent` cookie,
> not `redirect`, is what actually carries exam context through that path.

The anonymous-Explore audience is overwhelmingly new signups — exactly the population for whom
`redirect` is dropped. Stage 0 needs a **new, generalized discovery-intent cookie** (the same shape as
`notelib-exam-intent`, but not scoped to exams), not a reuse of the existing mechanism as-is.

**Under the Discovery System framing, this is not Explore-specific debt.** The two missing
`permitAll()` rules are an access gap in a Discovery System source (subject/course-program filtering),
the same category of gap as `/explore` itself being authenticated-only. Fixing them is required
regardless of which surface ends up wearing the public front-door label, and regardless of whether the
owner picks the `/explore` fork or the cheaper `/public/library` fork below.

## Finding C: two tiers of "existing URL," not three — subject pages are never-redirect too

**[corrected on review — the original three-tier model put subject pages in a "maybe, second-wave"
middle tier. That's wrong; existing feature documentation already forbids what it proposed.]**

**Restated under the Discovery System framing:** this is a **navigation-ownership vs. content-route**
distinction, not a "routes Explore absorbs" vs. "routes Explore spares" distinction. The bare list page
can lose navigation primacy — become a redirect, once justified — because losing navigation primacy is
exactly the kind of change the Discovery System's convergence principle allows. Subject and note pages
cannot, because they are content, and a navigation decision never owns a content route.

`docs/features/public-library.md` states plainly that the subject landing page is "**server-rendered,
not a redirect**," and separately: "the in-app `?subject=` filter and the canonical
`/public/library/{subject}` landing page intentionally serve different purposes... **they should not be
merged into one component without a dedicated future refactor.**" Putting subject pages on a path
toward an eventual Explore redirect is exactly the merge that documentation warns against. It's also
structurally awkward regardless of policy: `buildExploreUrl` is query-param-only
(`/explore?subject=Anatomy`), so redirecting a path-based canonical page with its own dedicated
structured data to a query-parameter URL would be an SEO downgrade dressed up as a migration, not an
equivalent replacement.

**Corrected model, two tiers:**

1. **`/public/library` (the bare list page).** The only genuine future redirect candidate, gated on
   Stage 0 shipping and Stage 3's own evidence bar (see below).
2. **Everything else — subject pages (`/public/library/{subject}`) and individual note pages
   (`/public/library/{subject}/{slug}`).** Content and curated discovery surfaces with their own
   canonical/structured-data identity. Never redirected, regardless of what happens to Explore.

**Additional migration surface area found on review, all bearing on tier 2 staying untouched:**
- Every public note page's structured data hardcodes `/public/library` as its breadcrumb parent
  (`{ name: "Public Library", url: absoluteUrl("/public/library") }`) — if the list page ever redirects,
  every indexed note ships a breadcrumb pointing at a redirect target. This scales with note count and
  would need its own fix pass, not a side effect of Stage 3.
- A shipped mechanism (`savePublicLibraryReturnUrl` / `PUBLIC_LIBRARY_RETURN_URL_STORAGE_KEY`,
  documented in `docs/features/public-library.md`) explicitly covers Explore's Notes tab, subject
  pages, **and Exam Hub note grids**, and states Exam Hub cards "always save a courseProgram-filtered
  *Public Library* URL, never an Exam Hub URL even when one exists." This is a real dependency Stage 1
  and Stage 3 would both touch and wasn't named in the original draft.
- `AGENTS.md` and `docs/features/navigation.md` hardcode `/public/library` as the back-link destination
  for non-owner Public Profile views, independent of anything Explore-related. A list-page redirect
  makes a documented navigation doctrine rule point at a redirect target.
- At least one existing redirect chain already exists (`/notes` → `/public/library`); Stage 3 would
  extend it to three hops.
- **[new, third pass] `AGENTS.md:621` is stale and directly contradicts this tier model.** It currently
  reads: "keep `/public/library/{subject}` as compatibility redirect-only when it exists" — true only
  before v0.14.0 shipped the server-rendered subject landing page (`docs/features/public-library.md:12`,
  `:624`). Any doctrine amendment recording "subject pages are never redirected" must fix this line
  first, or `AGENTS.md` will contain two contradicting rules about the same route.

No usable count of individual note pages was pulled for this document — `getServerPublicNotes()` is a
runtime call — but the mechanism is confirmed regardless of scale.

## Finding D: Exam Hub's couplings are wider than first counted, and the nav recommendation needed a real argument, not a fabricated one

**[corrected on review — D-i undercounted the actual coupling sites, and D-ii's supporting evidence was
fabricated. The recommendation itself survives, but on a narrower and more honest basis.]**

**Under the Discovery System framing:** Exam Hubs are a curated discovery surface *inside* the system,
the same category as Public Library and Official Review Sets — not a competing product area outside it.
That reframing changes vocabulary, not the recommendation below.

**D-i. Retargeting Exam Hub's outbound discovery links.** More sites reference Public Library than
originally counted:
- `frontend/app/exam/[slug]/page.tsx` — the low/zero-note empty state's "browse the full Public
  Library" copy and link (the clearest "go elsewhere to discover more" case), its sessionStorage return
  URL, and its browse-by-subject grid links.
- `frontend/app/exam/page.tsx` — the index page's `BackLink` to `/public/library`, which is a
  pre-existing miscategorization bug independent of this proposal: `/exam` is already a first-class,
  independently-reachable destination (top-level in both the marketing `Navbar` and `PublicFooter`),
  not a sub-page of Public Library. `docs/features/navigation.md`'s own "no back link" list for main
  pages doesn't include `/exam`, confirming the oversight. **The correct fix is to remove this BackLink
  entirely**, not repoint it — and this can ship today, independent of everything else here.
- `components/exam-hub/exam-hub-cta.tsx` — an **authenticated** CTA destination that also currently
  points at a `/public/library?courseProgram=…` URL.

Stage 1 is realistically a change touching four-plus files with matching test updates, not the
two-line edit the original draft implied.

**D-ii. Removing "Exam Hubs" as a distinct marketing-nav/footer item.** The original argument for
keeping Exam Hubs in top nav cited "an active, gated initiative — Wave 2 Exam Hubs beyond CPALE
(Civil/Electrical/Mechanical Engineering, Pharmacy, Physical Therapy, Civil Service Exam)... the product
is actively investing in more of these." **That citation does not exist in `ROADMAP.md`.** The real
status, confirmed across `ROADMAP.md`, `docs/features/exam-hub.md`, and the SEO strategy planning docs:
the one Wave 2 candidate that was scoped (CPALE) already shipped in v0.54.0, and further expansion is
**explicitly deferred, low-priority**, gated on a content-depth threshold ("zero official coverage
exists for any board exam outside" the current four) that no further candidate has cleared. This is the
opposite of active investment.

**The recommendation to hold off still stands, but on a narrower, honest basis:** Explore has no
exam-aware browsing mode today. Removing "Exam Hubs" from top nav while Explore has nothing equivalent
to route an exam-seeking visitor to would degrade a real journey before its replacement exists. That is
a sequencing constraint, not a permanent objection — and it costs nothing to revisit once Explore grows
that capability.

**What actually favors the owner's original instinct, and was underweighted in the first draft:**
Exam Hub is anchored in *two* places (`Navbar` and `PublicFooter`), so removing it from top nav does
**not** orphan it — it stays reachable from every page's footer, and its routes, canonicals, and sitemap
entries are entirely untouched by a nav-only change. `ROADMAP.md`'s own recorded direction states: "the
Official Review Set catalog [is] the scalable replacement for hand-built per-profession pages...
Publishing another Official Review Set should require no new frontend, versus adding an Exam Hub page
per profession." That catalog is literally Explore's Review Sets tab — the roadmap's own stated
long-term direction favors convergence, not permanent separation. The owner's instinct to simplify here
isn't wrong; it's early, pending Explore actually growing exam-aware browsing.

## Finding E: redirect infrastructure already exists — the real gap is narrower than originally stated

**[corrected on review.]** `frontend/next.config.ts` has no `redirects()` block and there is no
`middleware.ts` — both true. But the conclusion that "preserving backlinks via redirects is new
infrastructure to design" is wrong: the repo already uses server-component redirects in several places,
including a **filter-preserving** one (`frontend/app/library/public/page.tsx`, which redirects while
carrying query parameters through to the new destination) — exactly the pattern Stage 3's list-page
redirect would need. The real, narrower gap: `redirect()` from `next/navigation` issues a 307
(temporary); a genuine retirement of `/public/library`'s list page for SEO purposes would want a 308
(`permanentRedirect()`) or a `next.config.ts` entry for link-equity consolidation. That's a small,
well-understood change, not new infrastructure.

## Finding F: "Community Notes" is not a new concept — don't let this document invent a second definition of it

The request frames Explore eventually surfacing "Community Notes" as future scope. It already exists:
`DashboardCommunityNotesSection` is the current Dashboard-side name for this, and Explore's existing
"Notes" tab **is** the community-notes browsing surface — it's literally `PublicLibraryPageClient`, the
same component. The in-flight "Explore Owns Discovery" scope addition (this release, both PRs now
shipped) actively converted `DashboardCommunityNotesSection`'s outbound link to point at this exact tab.
If this proposal's eventual doc updates re-describe "Community Notes" as a new Explore feature to
build, it will duplicate work that's already shipped. Use "the Notes tab" or "today's Public Library
rendering" in any future-facing doc language instead. **(No correction on review — this finding held
up.)**

**The same discipline applies to "Discovery System" itself.** It is Public Library's, Official Review
Sets', and Exam Hubs' existing behavior placed under one name — the same relationship "Explore Owns
Discovery" already has to those three. See "The Discovery System" section above: this framing extends
that locked doctrine outward; it does not replace it, and it does not invent new Explore functionality
that isn't already shipped.

## Additional gaps found on review, not in the original draft

- **An anonymous visitor to `/explore` lands on a structurally empty tab.** `resolveExploreTab`
  defaults to Review Sets, and the "Recommended" section of `PublishedPlansPageClient` short-circuits
  to empty for any signed-out viewer (it depends on a personalized `getMe()` call) — only the
  unfiltered Browse-All list would render. Concretely: the first thing a Facebook-referred or
  backlink-following anonymous visitor would see at the proposed new front door is a half-empty tab.
  This is a stronger, more concrete version of the existing open question in `GPT_CONTEXT.md` about
  flipping Explore's default tab to Notes — worth resolving with this fact in view specifically, not
  just the adoption-concentration reasoning already on record there.
- **`/explore` is crawlable today with nothing stopping it.** `robots.ts` only disallows
  `/dashboard`/`/study`/`/settings`/`/profile`/`/api`/`/app`. The only reason it hasn't been an issue is
  that no anonymous-facing page currently links to it — but this release's own Dashboard-pointer PR is
  actively adding more internal links toward it. Worth an explicit decision on whether `/explore` should
  be added to the `disallow` list *now*, before Stage 0, as a defensive measure.
- **Two anonymous share-token routes exist and weren't inventoried** (`/p/{token}` and
  `/quiz/{token}`), along with a handful of other public/no-index pages (`/demo`, billing
  success/failure, unsubscribe, public creator/profile pages under `noindex`). None change the
  analysis, but a full accounting of "every anonymous route" should include them before this becomes
  an implementation-ready scope, since the request explicitly asked for an audit of "every place legacy
  URLs are referenced."
- **The homepage itself links to Public Library** (a tracked "Browse Public Library" CTA with its own
  analytics event) — the single highest-traffic anonymous entry point to the surface under discussion,
  and it wasn't named anywhere in the original draft's inventory.
- **Performance under crawler load.** This release already logged a Known Limitation that `/explore`
  fetches both tabs' data on every load regardless of which is active. Making `/explore` the anonymous
  front door doubles that cost per crawl, on top of everything else Stage 0 requires.
- **Stage 3's gate, as originally written, names no actual metric or instrument.** "Verify Explore is
  indexing/ranking at parity" isn't measurable without Search Console access, and the product's own SEO
  planning docs list Search Console setup as still-open, unassigned work. Stage 3 currently has no way
  to clear its own gate — this needs a concrete measurement plan before it's treated as a real stage
  rather than a placeholder.
- **`AGENTS.md:671` (Explore Navigation Rule) blocks Stage 3 in absolute terms**, not just softly —
  see "The doctrine tension" above, now led by this conflict rather than treating it as a footnote to
  `:849`.
- **`AGENTS.md:621` is stale** relative to shipped behavior (`docs/features/public-library.md:12`,
  `:624`) and needs fixing before any doctrine amendment can state "subject pages are never redirected"
  without self-contradiction — see Finding C.
- **`docs/features/explore.md` does not exist.** Explore is the only one of the five Page
  Responsibility Rule systems (Library, Review Sets, Explore, Progress, Companion) with no feature doc
  — `CLAUDE.md` requires reading `docs/features/<feature>.md` before changing any feature, and for
  Explore there is currently nothing to read. A gap worth tracking on its own, separate from whether
  this proposal proceeds.
- **The 2026-09-13 checkpoint has no Backlog Index row.** It lives only in `ROADMAP.md` prose (`:9`,
  `:232`) and in `RELEASES.md`, so `ROADMAP.md`'s own kickoff review ritual — which scans the Backlog
  Index for `CHECKPOINT` rows past their due date — will not surface it. This directly bears on decision
  #1 below and is worth fixing regardless of this proposal's outcome.

## Interaction with an already-open, uncommitted question

`docs/gpt-contexts/GPT_CONTEXT.md` currently has an **uncommitted** section from a prior session —
"Open Question This Session — Explore's default tab" — asking whether `/explore`'s default tab should
flip from Review Sets to Notes, on adoption-concentration grounds. This proposal strengthens that case
independently, and more concretely (see "empty tab" gap above) — if Explore ever becomes anonymous, an
un-flipped default tab is a materially worse first impression for anonymous arrivals than for
authenticated ones. Resolve the default-tab question with this dependency in view.

**I have not edited `GPT_CONTEXT.md`.** It has its own pending, unrelated-but-adjacent edit sitting
uncommitted in the working tree from before this conversation. Layering a second change on top of an
uncommitted file risks burying one open question inside another. Resolve or commit the existing edit
first; this document's own proposed `GPT_CONTEXT.md` note (below) is written to be added independently
of that.

## Recommended migration strategy (staged, each stage gated on the previous shipping and being verified)

**[revised on review — Stage 0's scope is materially larger than the original draft; a zero-Stage-0
alternative is now named explicitly as a fork.]**

**Fork, before Stage 0 — decide the URL question.** Does discovery specifically need to live at the URL
`/explore` for anonymous visitors, or does "point everything at one coherent destination" satisfy the
goal even if that destination is `/public/library` (already anonymous, already canonical, already
indexed)? If the latter, skip Stage 0 and everything below entirely — retarget the marketing nav item
and Exam Hub's outbound links at `/public/library` directly. This is materially cheaper and carries none
of Stage 0's backend/cookie/checkpoint-timing risk. If the owner specifically wants the `/explore` URL
itself to be the public destination, proceed to Stage 0.

**Stage 0 — make `/explore` anonymous-capable (prerequisite for everything else; release-sized on its
own, larger than originally scoped).** Requires, at minimum: a backend `SecurityConfig` change to permit
anonymous `GET /subjects` and `GET /course-programs` (also fixes the live bug in Finding B); a new,
generalized discovery-intent cookie for the anonymous adopt/copy action boundary (the existing exam-intent
cookie pattern doesn't transfer as-is — see Finding B); real canonical/OG/structured-data for `/explore`
itself; an explicit decision on the empty-tab problem for anonymous Review Sets browsing; a `robots.ts`
decision (disallow now, or verify-then-allow later); and, per the time-sensitive dependency above, an
explicit decision on sequencing against the 2026-09-13 checkpoint (either wait, or add `source`/viewer-type
segmentation to the checkpoint's analysis plan before Stage 0 ships). Also: fix the `/exam/page.tsx`
BackLink (Finding D-i) and the `/subjects`/`/course-programs` 401 bug (Finding B) — both are
independent, low-risk fixes that can ship immediately regardless of whether the rest of this proposal
proceeds.

**Stage 1 — retarget Exam Hub's outbound discovery links** (the empty-state Public Library link, the
sessionStorage return-URL default, and the browse-by-subject grid links — see the expanded Finding D-i
list) to Explore, filtered by course/program. Also needs the shared return-URL mechanism
(`savePublicLibraryReturnUrl`) updated to know about this new destination. Gated on Stage 0 shipping and
being verified anonymous-safe.

**Stage 2 — marketing `Navbar` swap**: replace the `Public Library` entry in `PUBLIC_NAV` with
`Explore`. Gated on Stage 0. **Do not** remove `Exam Hubs` from `PUBLIC_NAV` or `PublicFooter` in this
stage (Finding D-ii) — revisit only once Explore has an exam-aware browsing mode. (Exam Hubs, Official
Review Sets, and Public Library are all Discovery System sources under the framing above; this stage
changes which source the top nav names first, not what exists inside the system.)

**Stage 3 — redirect the `/public/library` list page only** (308/permanent, or a `next.config.ts`
entry — infrastructure already exists per the corrected Finding E), once a concrete measurement plan
(not yet defined — see the SEO-instrument gap above) confirms Explore is indexing/ranking at parity.
Never redirect `/public/library/{subject}` or `/public/library/{subject}/{slug}` (Finding C) — and
before this stage, separately audit and fix the breadcrumb/return-URL/back-link dependencies on the
list page's URL found above, since they hardcode `/public/library` today.

**Doctrine-blocked today, independent of the evidence bar above:** `AGENTS.md:671` currently forbids
redirecting `/public/library` in absolute terms ("must not replace, redirect, or redefine"). Stage 3
cannot ship — even once the SEO evidence bar clears — until the `AGENTS.md` amendment proposed below
lands and is ratified. Treat the amendment as a Stage 3 prerequisite, not a documentation afterthought
that trails implementation.

**Not recommended in the same pass as Stage 2, absent a separate ratified decision once Explore grows
exam-aware browsing:** folding "Exam Hubs" out of top-level nav (Finding D-ii).

## Proposed documentation changes (proposed only — not applied, pending your approval)

None of these are committed. Split into what's required for this revision to be internally coherent,
and what was found along the way but doesn't need to land in the same commit.

### Required for coherence (this pass)

**1. This plan document** — the revision described above, applied in place. `## Status:` line
unchanged; `Fork`/`Stage 0-3`/`Finding A-F` numbering unchanged.

**2. `AGENTS.md` — one dated amendment to `### Page Responsibility Rule`**, covering both `:671` and
`:849` in one place (append, don't rewrite the existing 2026-07-30 doctrine or the table above it):

> **Amendment (dated 2026-07-31, pending Stage 0 — not ratified):** a future direction exists to name
> the **Discovery System** as the product-architecture concept this table already implements — Explore
> is its primary interface; Public Library, Official Review Sets, and Exam Hubs are its sources and
> content surfaces. "Explore Owns Discovery" (locked above) is that doctrine's authenticated-navigation
> scope; this amendment extends the same doctrine toward eventual anonymous access, it does not replace
> it. Under this framing, `/explore` may eventually absorb `/public/library`'s *list-page* traffic only,
> once `/explore` itself gains real anonymous rendering, canonical metadata, and structured data. This
> narrows — it does not reverse — both the "must not replace, redirect, or redefine" language in
> `### Explore Navigation Rule` and the "navigation-level claim, not a route deletion" language above:
> both continue to mean subject-listing pages (`/public/library/{subject}`) and note-detail pages
> (`/public/library/{subject}/{slug}`) are never redirected, full stop; only the bare list page is a
> legitimate future redirect target, and only once this amendment and Stage 3's own evidence bar both
> clear. This also revisits, but does not resolve by itself, the owner's own earlier "Public Library is
> not absorbed or removed" direction recorded in `ROADMAP.md`'s Review-Set-Centric Navigation section —
> under this framing that direction stays true (Public Library remains a Discovery System source and
> route family; only its navigation primacy changes), so this reads as a narrowing of that direction,
> not a reversal needing separate sign-off, but the owner should confirm that reading explicitly rather
> than have it asserted silently. Blocked on Explore gaining real anonymous rendering, canonical
> metadata, structured data, and a resolved sequencing decision against this release's own
> `[CHECKPOINT — due 2026-09-13]`. Tracked in `ROADMAP.md`'s Backlog Index as "Discovery System — Public
> Front Door."

**3. `AGENTS.md:621` fix** (prerequisite of #2 — cannot ship the amendment above while this line
contradicts it):

> Current: "Public subject listing pages must not become second canonical list pages; use
> `/public/library?subject={subjectSlug}` for shareable subject filtering and keep
> `/public/library/{subject}` as compatibility redirect-only when it exists."
>
> Replace with: "Public subject listing pages must not become second canonical list pages for
> query-filtered browsing; use `/public/library?subject={subjectSlug}` for shareable subject filtering.
> `/public/library/{subject}` is a separate, server-rendered canonical subject landing page (shipped
> v0.14.0 — see `docs/features/public-library.md`), not a redirect, and must not be merged with the
> query-filter view without a dedicated future refactor."

**4. `docs/product/ROADMAP.md` — new Backlog Index row**, status **Parked**, in the exact 5-column shape
(`| Item | Source | Status | Gate (what un-parks it) | Last reviewed |`):

> | Discovery System — Public Front Door | `docs/claude-plans/explore-as-public-discovery-front-door.md` | Pressure-tested 2026-07-30/31 (two independent fresh-context reviews), not ratified. Reframed as the Discovery System: Explore is the primary interface; Public Library, Official Review Sets, and Exam Hubs are sources within it, not routes to be absorbed or replaced. Central blocker: `/explore` is authenticated-only today; every Discovery System source route (`/public/library`, `/exam/*`, `/collections/published`) is anonymous with real SEO investment. Requires an unscoped "Stage 0" (anonymous Explore: backend permit changes, a new discovery-intent cookie, canonical/structured-data work) before any redirect is safe. The only redirect stage (Stage 3, bare `/public/library` list page only) is additionally doctrine-blocked today by `AGENTS.md`'s Explore Navigation Rule ("must not replace, redirect, or redefine") until the proposed amendment lands. A cheaper zero-Stage-0 alternative exists (retarget nav/links at already-anonymous `/public/library` instead) if the literal `/explore` URL doesn't need to be the public one. Recommendation on Exam Hubs: keep in top nav for now as a curated Discovery System surface — Explore has no exam-aware browsing mode to route visitors to yet; revisit once it does. | **[DECISION] + [EFFORT].** Two owner decisions are outstanding and unblockable by more research: (1) sequence Stage 0 against the in-flight `[CHECKPOINT — due 2026-09-13]` — wait for it to close, or add a viewer-type dimension to its analysis plan first (`source` segmentation already ships this release per `ROADMAP.md` §Phase 2, so this is narrower than net-new instrumentation); (2) does the public destination need to literally be `/explore`, or does retargeting nav/links at already-anonymous `/public/library` satisfy the goal. Once decided: Stage 0 needs its own scoping pass, and Stage 3 additionally needs a concrete SEO measurement plan that doesn't exist yet (Search Console setup is separately unassigned open work) | 2026-07-31 |

### Found during this pass — recommend separately, not applied now

- **The 2026-09-13 checkpoint has no Backlog Index row of its own.** It lives only in `ROADMAP.md` prose
  (`:9`, `:232`) and `RELEASES.md`, so the kickoff review ritual's `CHECKPOINT`-row scan won't catch it.
  Worth its own row, independent of whether this proposal proceeds.
- **`docs/claude-plans/` is entirely unindexed** — the Backlog Index invariant at `ROADMAP.md:109`/`:111`
  only names `docs/claude-prompt/*-out/` directories, so both files in `docs/claude-plans/` (this one and
  `v0.67.0-explore-owns-discovery-ia.md`) are invisible to the kickoff ritual as written. Widening the
  invariant's wording is the durable fix.
- **`docs/features/explore.md` does not exist.** Worth creating regardless of this proposal's outcome —
  see "Additional gaps" above.
- **`docs/features/navigation.md`** — two changes: fix the already-stale "Current public navigation
  order" list (missing `Demo` and `Exam Hubs`, pre-existing gap independent of this proposal); add a
  short forward-pointer to this plan document and the new Backlog Index row.
- **`ROADMAP.md`'s `## Current Product Shape`** (around lines 1391-1418) is stale — no Explore, no
  Progress, no Backlog row flagging it as stale. Separate cleanup item.
- **`docs/features/public-library.md` / `docs/features/exam-hub.md`** — one-line forward-pointer each,
  next to their existing "does not replace" hedges (which stay true today, unedited).
- **`docs/gpt-contexts/GPT_CONTEXT.md`** — not proposed yet. Resolve its existing uncommitted
  default-tab question first; a short note connecting the two can be added cleanly afterward.

## What I'd want a decision on before writing any of the above into files

**[reordered on review, third pass]** the checkpoint-timing question stays first since it's the one
with a hard external date; a new Companion item is added at #6, reflecting the open collision the
Discovery System framing surfaced.

1. **Does Stage 0 wait until after the 2026-09-13 checkpoint closes, or ship earlier with an added
   viewer-type dimension on the checkpoint's existing `source`-segmentation plan?** (Narrower than
   originally scoped — see the corrected time-sensitive-dependency section above; the segmentation
   mechanism itself already ships this release.) This is the one decision that can silently corrupt
   something the release has already promised to measure if it's made by default rather than on
   purpose.
2. **Does the discovery destination need to literally be the URL `/explore` for anonymous visitors, or
   would retargeting nav/links at the already-anonymous `/public/library` satisfy the actual goal?** If
   the latter, most of this document's Stage 0 risk disappears.
3. Do you agree with the Discovery System framing itself — Explore as the system's primary interface,
   Public Library/Official Review Sets/Exam Hubs as sources within it, "Explore Owns Discovery" as that
   doctrine's authenticated-navigation scope? This is the organizing decision everything else in this
   document now sits under.
4. Do you agree with the resolution of all three doctrine conflicts: Conflict 3 (the owner's own
   "not absorbed or removed" direction) dissolves under the framing above and needs no reversal;
   Conflicts 1 and 2 (`AGENTS.md:671` and `:849`) still need the proposed dated amendment before Stage 3
   can exist as a real stage; list page eventually redirectable, subject and note-detail pages never?
5. Do you agree with holding Exam Hubs in top nav for now, on the corrected (sequencing-only) rationale
   — not the original, incorrect "active Wave 2 investment" rationale — reframed as: Exam Hubs stay a
   curated Discovery System surface until Explore has an equivalent exam-aware browsing mode?
6. **New, this pass:** the Discovery System framing assigns Companion the governing question "What
   should I do next?", which collides with `AGENTS.md`'s current assignment of "What should I do now?"
   to Dashboard. Should `AGENTS.md`'s Page Responsibility Rule table be updated to match the owner's
   framing, kept as-is, or reworded some third way? (Library, Review Sets, Explore, and Progress all
   already match with no daylight — this is the one open cell.)
7. Should I go ahead and apply the required doc diffs above (`AGENTS.md`'s amendment + the `:621` fix,
   and the `ROADMAP.md` Backlog row) as a docs-only commit once you've weighed in on 1-6, or do you want
   to see them written out in full diff form first? Separately: do you want any of the "found during
   this pass, recommend separately" items (the checkpoint's own Backlog row, the `docs/claude-plans/`
   invariant gap, the missing `docs/features/explore.md`) picked up now too, or tracked and left for
   later?
