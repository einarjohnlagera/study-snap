# Discovery System — Stage 0 scoping pass

**Status: scoped, not kicked off.** No code has changed as a result of this file. It exists so the next
release can be opened against a verified scope instead of a two-week-old plan.

**Parent document:** `docs/claude-plans/explore-as-public-discovery-front-door.md` (pressure-tested
2026-07-30/31). That document's findings are the input here; this one re-verifies them against current
code and turns Stage 0 into release-shaped work.

---

## The fork is decided

**Owner decision, 2026-08-17: the public discovery destination IS the literal `/explore` URL.**

The parent document named a cheaper alternative — retarget marketing nav and Exam Hub's outbound links
at the already-anonymous `/public/library`, skipping Stage 0 entirely. That was **considered and
rejected** in favour of one canonical discovery URL for both audiences.

**Record the consequence honestly: this is the expensive branch, and it buys back nothing.** The full
Stage 0 is real work, and Stage 3 stays doctrine-blocked by `AGENTS.md`'s Explore Navigation Rule until
its amendment lands. The cheaper fork remains the fallback if Stage 0's cost proves unacceptable — it is
not foreclosed by this decision, only deferred.

**Also decided 2026-08-17: the `[CHECKPOINT — due 2026-09-13]` gate DISSOLVES.** Do not re-derive it
from the parent document, whose first decision assumes a clean Explore pre/post read still exists. It
does not — `v0.78.0` degraded that baseline on 2026-08-15 (`ROADMAP.md`: *"the pre/post comparison is no
longer clean and should not be read as one"*) and its successor metric is measured directly rather than
inferred from Explore traffic, so anonymous visitors cannot contaminate it. **The viewer-type
segmentation the gate was trading against is still owed** — see deliverable 6.

---

## Premise re-verification, 2026-08-17

The parent document is dated 2026-07-31 and `v0.83.0` shipped Public Library changes in between. Every
load-bearing premise was re-checked against current code:

| Premise | Status |
|---|---|
| `/explore` gates client-side only | **HOLDS.** `explore-page-client.tsx:49` calls `requireAuthenticatedOnboardedUser(router)`; there is no `frontend/middleware.ts` at all, so no server-side gate exists |
| `/explore` has no canonical/OG/structured data | **HOLDS.** `app/explore/page.tsx` is six lines: a title-only `metadata` export and the client component |
| `/explore` is crawlable but unlinked | **HOLDS.** `robots.ts` disallows only `/dashboard`, `/study`, `/settings`, `/profile`, `/api`, `/app`. `/explore` is allowed and simply absent from any anonymous page and from the sitemap |
| `GET /subjects` and `GET /course-programs` 401 for anonymous | **HOLDS, and is worse than recorded** — see below |
| `/exam/page.tsx` has a miscategorised `BackLink` | **HOLDS.** `:31` still renders `<BackLink href="/public/library" label="Public Library" />` |
| The shared return-URL mechanism needs Stage 0 work | **DOES NOT HOLD — corrected here.** `currentPublicLibraryPath` is built from a `basePath` **prop** (`public-library-page-client.tsx:1345-1348`), so the component already saves whatever base path it is mounted under. It is Stage-0-safe as written and is **not** a blocker |

### The 401 bug is now more visible than when it was recorded

`SecurityConfig` permits `/tags` (GET), `/public/**` and `/notes/public/**`, and ends with
`.anyRequest().authenticated()`. `/subjects` and `/course-programs` appear **zero times** in that file,
while `SubjectController` and `CourseProgramController` both explicitly serve anonymous `scope=public`
and throw `AUTHENTICATION_REQUIRED` only for `scope=mine`. The intent is in the controllers; the
security config never granted it.

`PublicLibraryPageClient` fetches all four facet sources inside one `Promise.allSettled`, so the 401s
are swallowed into empty chip lists rather than surfacing.

**`v0.83.0` sharpened the symptom.** Its Authored Depth chips come from `/notes/public/learner-levels`,
which *is* permitted. So an anonymous visitor to `/public/library` today sees **Tags and Authored Depth
populated, Subject and Course/Program empty** — an inconsistency a visitor can see, on the surface with
the most SEO investment behind it.

**This is a live bug independent of the whole Discovery System direction**, and it is required
regardless of which fork is taken.

---

## Stage 0 deliverables

Six items. **Four are engineering; two are owner decisions that should not be resolved by an
implementer.**

### 1. Permit anonymous `GET /subjects` and `GET /course-programs` — engineering

Two `requestMatchers(GET, …).permitAll()` additions in `SecurityConfig`, mirroring the existing `/tags`
rule. Both controllers already gate `scope=mine` on their own, so this widens no data access beyond
what they were written to serve.

**⚠️ This is a security-config change and must not ride an unrelated patch release.** It widens
anonymous access on two endpoints, which is a different risk class from a null-field fix. It needs its
own review, tests asserting an anonymous `scope=public` 200 **and** an anonymous `scope=mine` 401, and a
`docs/features/public-library.md` note.

### 2. A generalized discovery-intent cookie — engineering

The parent document's original suggestion — reuse Exam Hub's
`/auth?mode=signup&intent=exam&exam={slug}&redirect=…` pattern — **does not transfer.**
`docs/features/exam-hub.md` records that `redirect` is dropped on the primary `mode=signup` path because
`resolvePostLoginDestination` routes new signups through `/verify-email` and `/onboarding` first; the
`notelib-exam-intent` cookie is what actually carries context through. Anonymous Explore's audience is
overwhelmingly new signups — precisely the population for whom `redirect` is dropped.

So this needs a **new cookie in the same shape as `notelib-exam-intent` but not scoped to exams**,
carrying the discovery context (filters, target note or plan) across verify-email and onboarding to the
adopt/copy action the visitor was reaching for.

### 3. Canonical, OG and structured data for `/explore` — engineering

`/explore` currently has a title and nothing else. Every other discovery surface has canonical metadata,
and the note and subject pages carry JSON-LD. If `/explore` is to be the public front door it needs
parity, and the work is not merely additive: **the existing `CollectionPage` JSON-LD on
`/public/library` describes the same content**, so two indexable pages would claim one collection.
Canonical direction between them has to be decided as part of this item, not after it.

### 4. The empty-tab problem for anonymous Review Sets browsing — ⚠️ OWNER DECISION

`/explore` composites two tabs. The Notes tab renders content that is already anonymous. **The Review
Sets tab is the open question:** what does a signed-out visitor see — the full published catalog, a
teaser with an adopt-gated CTA, or is the tab hidden until sign-in?

This is a product call with real consequences in both directions: hiding it makes the anonymous and
authenticated Explore structurally different pages, while showing a catalog whose primary action
requires an account invites a bounce. **Do not let an implementer pick this by default.**

### 5. `robots.ts` — ⚠️ OWNER DECISION

Two coherent positions. **Disallow `/explore` during Stage 0** and allow it once metadata and indexing
behaviour are verified — safer, and avoids a half-built page entering the index. Or **allow immediately**
and accept that early crawls see an incomplete surface.

This interacts with a gap that does not yet exist as work: **Stage 3's SEO measurement plan is
undefined, and Search Console setup is separately unassigned.** "Verify, then allow" needs a definition
of verified. Deciding this item may require accepting that the verification bar is informal for now.

### 6. Viewer-type segmentation on Explore analytics — engineering, and the easiest item to lose

**This is the deliverable the dissolved 09-13 gate traded away, and it must not become a footnote.**
`v0.67.0` already ships `source` segmentation (pointer-origin vs direct nav). What is missing is an
**anonymous-vs-authenticated dimension**. Every future Explore read needs it; without it, the moment
`/explore` goes anonymous, Explore engagement numbers silently mix two populations with no way to
separate them after the fact.

It ships **with** Stage 0 rather than being gated on a date. Note that anonymous analytics events are
already accepted and persisted today (`user_id` is nullable, `/analytics/events` is `permitAll`), so
nothing would reject or flag the contamination — which is exactly why the dimension has to precede the
traffic rather than follow it.

---

## Proposed sequencing inside Stage 0

Stage 0 is release-sized, but it is not monolithic. Two items are independently shippable and should not
wait behind the cookie or the structured-data work:

**Slice A — the independent fixes (ship first, no dependencies).**
- Item 1, the `permitAll` additions. Fixes a live visible bug.
- Remove the `/exam/page.tsx` BackLink. `/exam` is a first-class destination, top-level in both the
  marketing `Navbar` and `PublicFooter`, so a back link to `/public/library` miscategorises it as a
  sub-page. `docs/features/navigation.md`'s own "no back link" list omits `/exam`, confirming the
  oversight. **The fix is removal, not repointing.**

**Slice B — the measurement prerequisite.**
- Item 6, viewer-type segmentation. Before any anonymous traffic reaches `/explore`.

**Slice C — the anonymous surface itself.**
- Items 2 and 3, plus whatever items 4 and 5 are decided to be.
- Gated on Slice B, because shipping this before segmentation is what corrupts the reads.

---

## Explicitly NOT Stage 0

- **Stages 1–3.** Not scoped here; the ask was Stage 0. Stage 1 retargets Exam Hub's outbound links
  (four-plus files), Stage 2 swaps the marketing nav entry, Stage 3 redirects the `/public/library` list
  page only.
- **Stage 3 remains doctrine-blocked** by `AGENTS.md`'s Explore Navigation Rule (*"must not replace,
  redirect, or redefine"*) until the amendment the parent document proposes lands and is ratified. Treat
  the amendment as a Stage 3 prerequisite, not documentation trailing implementation.
- **Never redirect `/public/library/{subject}` or `/public/library/{subject}/{slug}`.** Both carry
  canonical metadata and per-page sitemap entries, and the note pages' BreadcrumbList JSON-LD hardcodes
  `/public/library` as parent (`[slug]/page.tsx:164`). That hardcoding is a **Stage 3** concern — it
  stays correct as long as the list page is not redirected.
- **Removing "Exam Hubs" from top nav.** Explore has no exam-aware browsing mode to route an
  exam-seeking visitor to. Sequencing constraint, not a permanent objection.
- **Similarity / "a similar note."** Deferred leg (b) of the post-mastery row folds into the Discovery
  System, but it is not Stage 0.

---

## What is needed before `/kickoff`

1. **Decisions on items 4 and 5** — the anonymous Review Sets tab, and the `robots.ts` position. Both
   block Slice C; neither blocks Slice A or B.
2. **A release-shape call:** Slice A alone is a small patch-shaped release that fixes a live bug. Slices
   A+B+C together are a full release. Splitting means the visible bug is fixed sooner.

Everything else in Stage 0 is scoped and verified against current code as of 2026-08-17.
