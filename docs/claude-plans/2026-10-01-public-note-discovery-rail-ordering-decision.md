# Public Note discovery rail ordering — owner decision (2026-10-01)

**Status: approved, binding. Supersedes the open rail-order question in
`docs/claude-plans/2026-10-01-public-note-page-fanout-fix-plan.md` (Leg B).** Reached via
`docs/claude-plans/public-note-discovery-rail-ordering-product-ux-consultation-prompt.md`. This
package is content, not re-derived here — read it for the full product reasoning; this file is the
instruction surface for implementation.

---

## The decision

For the two contextual discovery rails on an individual Public Note page — **"More from {Subject}"**
and **"More {Program} notes"** — switch ordering to **most recent eligible/ready public Notes**, via a
single bounded page-0 fetch per rail with a small fixed result size. **Do not preserve live popularity
ranking on these rails.** Labels stay as-is ("More from {Subject}", "More {Program} notes") — they
describe the relationship, not the ranking mechanism, and don't need to change to "Recent".

## Why

These rails are secondary discovery for a visitor who already reached a Public Note, usually via search
or a shared link. They answer "what else can I explore from this Subject/Program?", not "what are the
globally most popular Notes?" Live popularity ranking does not earn its request-time cost here. The
production failure mode (24 restarts/30 days) is unacceptable, the database is deliberately sized for a
pre-revenue product, and **raising the DB plan is not an option** — optimize the read path, don't buy
headroom.

## Reliability invariant (adopt)

> Anonymous Public Note traffic must never pay the cost of calculating a global popularity ranking.

Broader principle: use live queries for discovery signals that are cheap to derive; precompute expensive
aggregate rankings only when popularity is genuinely useful. **Do not build new ranking infrastructure in
this release** unless the approved engineering plan already requires it — the immediate goal is
eliminating the expensive request-time behavior, nothing more.

## Rail contract

Per rail: one bounded DB page fetch; fetch only the candidates needed plus the minimum bounded
over-fetch to exclude the current note (if required); use the existing cheap/index-friendly `recent`
ordering; preserve existing eligibility semantics (public visibility, Study Pack readiness, Subject/
Program match, current-note exclusion, lifecycle requirements) unless the outage plan names one as
wrong. Do not: walk subsequent pages; fetch the whole Subject/Program; compute app-side popularity;
perform live ranking; introduce N+1; or swap one unbounded query for another.

## Recency is the safe baseline, not a reversal of product philosophy

Do not record this as "NoteLib decided popularity is bad." The narrower claim: **contextual Public Note
rails don't justify live popularity computation.** Popularity remains potentially useful on a deliberate
browse surface (Explore) — but expensive popularity should eventually be precomputed/cached, not
recomputed per anonymous request. Not built automatically in this release.

**Popularity-fairness rationale, recorded without overstating it as A/B evidence:** the existing score
(`views + 3×copies + 2×likes`) can create a rich-get-richer loop (popular → shown more → more views →
more popular), and views are inflated by crawler/search/shared-link traffic independent of quality.
Recency gives newly published knowledge a deterministic, temporary exposure window without a new
recommendation algorithm. No randomness added — deterministic recency is sufficient for this release.

## Follow-up audit of other anonymous discovery surfaces (read-only code audit, 2026-10-02)

Required format, per the owner's decision:
`SURFACE / ENDPOINT-SERVICE-QUERY / ANONYMOUSLY REACHABLE? / CURRENT SORT-RANK METHOD / BOUNDED OR
UNBOUNDED? / QUERY-FAN-OUT SHAPE / LIKELY GROWTH CHARACTERISTIC / CURRENTLY PART OF OUTAGE? /
RECOMMENDED FOLLOW-UP`.

**1. Public Note detail page — "More from {Subject}" / "More {Program} notes" rails.**
`frontend/lib/server-public-notes.ts` (`getServerPublicNotesBySubjectSlugRecent`/
`...ByCourseProgramRecent`) → `NoteService.listPublic` SQL-orderable branch. Anonymously reachable (yes —
every Public Note page view). Sort: was live `RECOMMENDED` engagement ranking, **now fixed to `recent`
this release**. Was unbounded (walked every page of the subject/program, 8–18 ranking queries per
render); now one bounded `page=0` fetch. Fan-out shape was O(pages in subject/program) ranking queries
per single note render; now O(1) cheap indexed query. Growth characteristic was unbounded with catalog
size; now constant. **Part of the outage: YES — this is the fixed mechanism.** Follow-up: none; closed.

**2. Public Note detail page — "More in {Subject}" rail.** `getServerPublicNotesBySubject` →
`NoteService.listPublic` SQL-orderable branch. Anonymously reachable (yes). Sort: `recent` already
(owner decision, 2026-09-06, pre-existing). Bounded: yes, one `page=0&pageSize=4` fetch already. Fan-out:
O(1), already. Growth: constant, already. **Part of the outage: NO — already fixed 2026-09-06, predates
this release, not touched.** Follow-up: none.

**3. Subject landing page (`/public/library/[subject]`) and Exam Hub pages (`/exam/[slug]`).**
`getServerPublicNotesBySubjectSlug`/`getServerPublicNotesByCourseProgram(s)` →
`fetchAllPublicNotePages` → `NoteService.listPublic` SQL-orderable branch (now forced `sort=recent` by
this release, was previously unsorted → ranked branch). Anonymously reachable (yes — ISR-revalidated
public pages, and `generateStaticParams`/`generateMetadata` run at build/revalidate time too). Sort: was
no `sort` (defaulted to non-orderable `RECOMMENDED`, ranked branch on every page); **now `recent`
(SQL-orderable) on every page, fixed as a side effect of this release's walker change.** Bounded: **still
NO — this surface intentionally walks every page to assemble the whole subject/program's note list for
client-side Featured/Popular/Recent sectioning (`getSectionedNotes`), so query COUNT still scales with
subject/program size.** What changed is that each of those page queries is now the cheap indexed
`ORDER BY created_at` query instead of the expensive aggregate-ranking-plus-count query — the
ranking-query *multiplier* that caused the outage is gone, but the walk itself is unchanged and is not
re-architected this release. Fan-out shape: O(pages in subject/program), cheap queries, down from O(pages)
expensive queries. Growth characteristic: query count still scales with subject/program size, but at much
lower per-query cost; a subject the size of Civil Engineering (18 pages) still issues 18 queries per
page render, just cheap ones now. **Part of the outage: shares the identical root-cause defect (no
`sort` → ranked branch) but was not among the two rails identified as the 24-restart cause; already
covered by the existing engineering plan's item 3, not a new discovery.** Recommended follow-up: these
pages' own query count is still O(library size); if a subject or program ever grows large enough that 18+
cheap indexed queries per render becomes its own cost concern, the real fix is a backend endpoint that
returns Featured/Popular/Recent sections directly (same shape as Explore's `discovery-sections`) instead
of walking pages to build them in JS — not built this release, no evidence it's needed yet.

**4. Sitemap (`/sitemap.xml`).** `getServerPublicNotes()` → `fetchAllPublicNotePages("")` → same
SQL-orderable branch, same "now forced `sort=recent`" fix. Anonymously reachable: **yes, including by
crawlers specifically** (that's its purpose). Bounded: no — walks the entire public catalog (same
O(pages), now-cheap-per-page situation as #3). **Part of the outage: not implicated in the measured
24-restart incident** (Render's ISR build/revalidate cadence is far lower-frequency than per-visitor Note
page renders), but shares the same underlying defect, now also mitigated by the same `sort=recent` fix.
Recommended follow-up: none beyond what's already shipped; unchanged scope from #3.

**5. Explore / Public Library discovery-sections (`GET /notes/public/discovery-sections`).**
`NoteService.getPublicLibraryDiscoverySections` (`NoteService.java:937-994`). Anonymously reachable: yes
(Public Library homepage default/no-filter view, and `/exam/[slug]` could in principle reuse it but
doesn't currently). Sort: live `POPULAR`/`FEATURED` ranking per request, computed fresh every call — **not
cached**. Bounded: **yes** — fixed `limit=6` per section (Featured/Popular/Recent), no pagination walk,
no separate count query; candidate scan is lean and engagement counts are batch-loaded, with full
list-item enrichment running only on the final capped union (at most 18 notes per `public-notes.md:364`).
Fan-out shape: O(1) per request — one bounded scan plus batched enrichment, not a page-count multiplier.
Growth characteristic: the ranking computation's candidate scan could in principle widen as the catalog
grows (it must consider all eligible notes to find the top 6 by score), but it is a single request-time
query, not a repeated per-page fan-out — the same cost class as any ordinary Explore page view, not the
multiplicative mechanism that caused the outage. **Part of the outage: NO.** Recommended follow-up (owner's
own named future direction, "Discovery v2" / the precompute direction in this file above): popularity
ranking here should eventually be precomputed/cached on a schedule rather than recomputed live on every
anonymous request, as the catalog grows — **not built this release, no regression, not urgent**; flagged
because it is the legitimate case the owner's decision anticipated ("Popularity remains potentially useful
on a deliberate browse surface... but expensive popularity should eventually be precomputed/cached").

**6. Public Library main browse list, `RECOMMENDED`/`MOST_COPIED`/`MOST_VIEWED` paginated sorts**
(`GET /notes/public?sort=recommended|most_copied|views&page=N`, reached only when a visitor has an
active filter — subject/program/search/tags/level/source/readyOnly — with no explicit sort chosen;
`public-library-page-client.tsx:578-586` routes the unfiltered default view to the bounded
discovery-sections endpoint above instead). `NoteService.listPublic`'s non-SQL-orderable branch
(`findPublicLibraryRankedPageIds`/`countPublicLibraryRankedMatches`,
`PublicLibraryRepositoryImpl.java:212-223,282+`). Anonymously reachable: yes. Sort: live ranking,
computed in SQL (`ORDER BY` a computed score expression) with `LIMIT`/`OFFSET`, not recomputed in
application memory. Bounded: **yes, per request** — one ranked query plus one count query, each
constrained to the filtered candidate set, returning one page; **not** a page-walking fan-out like the
original bug. Fan-out shape: O(1) queries per page view (standard REST pagination: one request → one
page), unlike the fixed rails' old O(pages) internal walk. Growth characteristic: each individual ranked
query's own internal cost (scanning/aggregating the filtered candidate set to compute scores before
`LIMIT`) can grow with the size of that filtered set, but there is no per-render multiplier — a crawler
hitting many distinct filter+page combinations drives proportionally many *individual* ranked queries,
the same way any filterable list endpoint does, not the N-queries-for-one-render mechanism that caused
this outage. **Part of the outage: NO — architecturally distinct from the fixed mechanism (no internal
page walk), not implicated in the diagnosis, no stop condition triggered.** Recommended follow-up: same
precompute/cache direction as Explore above, same priority (not urgent, not built this release).

**No surface was found presenting the SAME immediate production-safety failure mode** (an internal
per-render fan-out across many pages of the same expensive ranked query) **left unaddressed by this
release.** Surfaces #3/#4 share the root *defect* (missing `sort`) and are fixed by the same code change;
surfaces #5/#6 are architecturally bounded-per-request and were already so before this release.

## Discovery v2 — backlog direction only, not implemented

Future question: can NoteLib use existing structured learning metadata (curriculum proximity, Section/
Subject Plan, Subject, Applicable Program, recency, possibly precomputed popularity) for cheap
deterministic contextual discovery, aligned with "Study Plans define the journey"? **Not implemented.**
No recommendation engine, no LLM calls, no vector search, no randomness, no manually maintained Featured
system — this is a backlog note for later investigation only.

## Implementation audit (2026-10-02, Release Implementor)

Performed before presenting the diff for commit, per the standing "cheapest checks are the earliest
ones" and "say so explicitly" rules.

- **Subject-vs-Subject duplication: fixed.** The quiz-preview's "More from {Subject}" rail now excludes
  notes already shown in the separate "More in {Subject}" section, but only when that section renders
  (at least two notes). The quiz-preview rail renders only after a visitor completes all three Quick
  Check questions, while "More in {Subject}" is always visible when it has enough notes, so the
  rarely-seen rail defers to the visible one. Its one bounded fetch requests six candidates plus
  current-note headroom and still displays at most three; the "More in {Subject}" query is unchanged.
- **Subject-vs-Program duplication: audited, intentionally not fixed.** A note sharing the current
  note's Subject and Course/Program can appear in both the quiz-preview's "More from {Subject}" rail
  and the "More {Program} notes" section only after a visitor finishes the embedded Quick Check.
  Avoiding that overlap would require over-fetching beyond the original rail contract and risks
  starving the smaller rail when their top-N windows overlap substantially. A dedicated test
  (`page.test.tsx`: "documents that the two most-recent-first rails are not cross-deduped") pins this
  accepted behavior; `RELEASES.md` records it as a known limitation.
- **Blast radius of the walker's new forced `sort=recent`: audited, no regression.** `fetchAllPublicNotePages`'s
  remaining callers — `app/sitemap.ts`; `app/public/library/[subject]/page.tsx`'s `generateMetadata`/page
  body (via `getServerPublicNotesBySubjectSlug`); and `app/exam/[slug]/page.tsx` (via the plural
  `getServerPublicNotesByCoursePrograms`, which fans out to the same singular walker per program) — all
  re-derive their own Featured/Popular/Recent/remaining sections via `lib/public-library-discovery.ts`'s
  `getFeaturedNotes`/`getPopularNotes`/`getRecentNotes`, each of which fully re-sorts the complete set
  it's given rather than trusting arrival order (confirmed for the exam hub's `remaining` section too,
  which also routes through `getRecentNotes`). Changing the walker's fetch order therefore changes
  nothing about what's rendered on any of them. None of these callers passes its own `sort`, so there's
  no duplicate-`sort`-param risk. The walker doesn't cap pages (it walks until the server reports no
  more), so there's no truncation risk either.
- **`coursePrograms?.[0]` determinism: audited, confirmed stable.** `NoteCourseProgramRepository.findByNoteId`
  (`backend/.../repository/NoteCourseProgramRepository.java:20-26`) carries `ORDER BY course_programs.name`,
  so the joined-programs list — and therefore `coursePrograms[0]` — is alphabetically stable across ISR
  revalidations, not flip-flopping.

## Explicit non-goals for this release

Do not: upgrade the DB plan; retain live popularity on these two rails; walk multiple pages; disguise an
expensive query as "bounded"; add Redis, a recommendation service, vector search, or an LLM call for
this; add random ranking; add a Featured flag; redesign Explore or the Public Note page; change global
Public Library ordering; remove popularity everywhere; implement Discovery v2; write to production
manually; or expand scope because adjacent improvements look attractive.

**Core invariant:** anonymous traffic should consume roughly constant discovery-query work per Public
Note render regardless of whether NoteLib has hundreds, thousands, or tens of thousands of public Notes.
