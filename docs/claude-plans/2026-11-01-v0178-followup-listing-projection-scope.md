# Follow-up scope: the paged public-library projection read (written 2026-11-01, after the v0.178.0 24-hour read)

Status: **scope note for the owner, not a plan to implement.** Nothing here is built or approved. It follows `docs/claude-plans/2026-10-29-v0178-production-stabilization-plan.md` (which left "listing projections, facets, creator-impact" for a later release) and the `v0.178.0` Deploy record in `RELEASES.md`.

## Why this, and how sure we are

**Measured (production, read-only, 2026-11-01T00:44Z):**
- `pg_stat_statements` (reset by crash recovery at 2026-10-31T07:41:03Z) ranks the note-projection `select n.id, n.owner_user_id, ... substring(n.content, 1, 2000) ... from notes n left join (...)` statements first: variants of 43 calls at a 14.9 s mean (13.8% of database time), 168 at 2.3 s (8.3%), 391 at 657 ms (5.5%), 238 at 938 ms (4.8%). About 32% of database time since the reset.
- The kill at 2026-10-31T07:41:01Z followed a burst of 43 and 44 requests a minute, first only `listing`, with CPU at 0.091 of 0.1 (`RELEASES.md` Deploy record). The statement logged at 2,003 ms at 07:38:49Z has this shape.

**Read from the code:** the statement is `NOTE_LIST_ITEM_SELECT + NOTE_LIST_ITEMS_FROM + filter + orderBy`, used by `PublicLibraryRepositoryImpl.findPublicLibraryPage` (`PublicLibraryRepositoryImpl.java:154-171, 208-226`). `NoteLibraryRepositoryImpl.java:66` has a second copy of the same select (not examined).

**Planner estimate, NOT a timing (`EXPLAIN` without `ANALYZE`, unfiltered, `order by created_at desc, id`, `limit 20 offset 1000`, 2026-11-01):** the plan scans all about 2,374 public notes, computes `substring(n.content, 1, 2000)` for each of them **below** the Sort, hash-joins them to a `note_programs` aggregate over about 8,240 rows that is built for every public note, sorts the lot, and only then applies `LIMIT 20 OFFSET 1000` (total cost about 6,457). So the cost per call looks independent of page size and offset: asking for 20 rows costs as much as asking for all of them.

**Not established:** that this is what saturated the database at 07:40Z (a single timing from the log is the only direct link); the plan of the RANKED sorts, which use a different FROM and the snapshot arrays (not examined); whether the cold cache after the crash inflated the 14.9 s mean.

## CORRECTION, same day, after reading the callers and running two more planning-only `EXPLAIN`s

The first draft of this note said the plan sorts the whole catalog and proposed paging the keys first. **That describes only two of the eight sorts.** `NoteService.java:833-869`: the two SQL-orderable sorts take `findPublicLibraryPage` (the single statement explained above); **the other six, including the default `recommended`, already page the ids first** (`findPublicLibraryRankedPageIds` or the snapshot variant) **and then read the projection with `findPublicLibraryListItemProjectionsByIdIn`** (`PublicLibraryRepositoryImpl.java:608-618`).

**That by-id projection has the same problem, and it is the larger one.** It reuses `NOTE_LIST_ITEMS_FROM`, whose `left join (select ... array_agg(cp.name) ... from note_course_program ncp join course_programs cp join notes program_note where program_note.visibility = 'PUBLIC' group by ncp.note_id) note_programs` is a derived table over the whole catalog. `EXPLAIN` (no `ANALYZE`, 20 real public ids, 2026-11-01): the notes read costs about 48, the join to `note_programs` pushes the total to about 4,456, i.e. **about 98% of the estimated cost of fetching 20 notes is aggregating the programs of every public note** (a merge of about 61,600 `note_course_program` index entries with all about 2,374 public notes, then a group and sort), and the planner does not restrict it to the 20 ids. Planner estimate only, not a timing.

**Measured, which tempers the paragraph above (production, read-only, 2026-11-01):** the tails of the top eight `select n.id as id ...` statements in `pg_stat_statements` are all the `order by n.created_at desc, n.id asc offset/fetch first` shape, i.e. `findPublicLibraryPage` (the newest-first sort, with and without subject, program and ready filters; calls 43, 168, 406, 298, 245, 166, 23, and one ranked sort at 18). **The by-id form is not among them.** So the measured load is `findPublicLibraryPage`, whose plan has BOTH costs (the whole-catalog programs aggregate and the sort over every public note with the content substring computed below it). The estimate that the programs aggregate is about 98% of the by-id projection is a planner figure; whether the by-id form matters as load is not shown. The `EXPLAIN ANALYZE` timing on the restored dump (below) decides how much each half is worth.

## Probable trigger shape (code read 2026-11-01; NOT proven)

`getServerPublicNotes()` is `fetchAllPublicNotePages("")` (`frontend/lib/server-public-notes.ts`): a **sequential** loop of `/notes/public?page=N&pageSize=50&sort=recent`, 44 pages for about 2,166 notes; each page is cached by Next for 300 s. Its callers are the sitemap (`force-dynamic` since `v0.178.0`), the `general` subject page and the exam hubs. That matches what was recorded: 43 requests in one minute carrying only `page`, `pageSize`, `sort` from one `/24` with user agent `node` at 2026-10-31T07:39Z, and the other session's 14:05Z burst over 44 distinct offsets 50 to 2,150. `sort=recent` is the SQL-orderable newest sort, i.e. `findPublicLibraryPage`, the statement timed at about 0.8 to 1.7 s per page at 0.1 CPU above, so one full walk is about 44 x 1.4 s, roughly a minute of the database's entire CPU (CPU was 0.091 of 0.1 at 07:40Z). With the rewrite, about 44 x 0.1 s. The code comment that calls these pages "one cheap, indexed query" predates the programs aggregate and is stale.

**Not established:** which page or crawler triggered the walk (Vercel runtime logs are kept 1 hour on the Hobby plan, so 10-31 is gone), or whether `v0.178.0` made it more frequent. Before `v0.178.0` the subject and exam pages already had `revalidate = 300` (a background walk every 5 minutes after traffic) and the sitemap was not `force-dynamic`; `v0.178.0` removed the build-time prebuild and made the sitemap render per request. The recorder in `v0.178.0` will show the signature next time (a minute with about 40 listing requests carrying only `page`, `pageSize`, `sort`).

## Options (a recommendation, not a decision)

**A. Make the programs lookup per page, not per catalog (recommended to scope).**
1. In the projection FROM, replace the whole-catalog `note_programs` derived table with a per-row lookup restricted to the page's notes (for example a correlated or lateral `array_agg` keyed on `n.id`), keeping the same `program_note.visibility = 'PUBLIC'` rule and the same sorted names, so `applicablePrograms` is byte-identical.
2. For `findPublicLibraryPage` (two sorts), select the page's ids with narrow columns first (no `content`, no programs), then use the by-id projection, as the other six sorts already do.
Same rows, same order, same response. No migration, no prompt or entitlement change, no new endpoint. Parity risk: the `applicablePrograms` array (order and the public-note condition), the sort tiebreak, and filters; it needs the same parity-test style as `v0.178.0` (old statement versus new on the same data, every sort, offsets at both ends and past the end, a note with zero, one and many programs, a note whose program row points at a non-public note). `NoteLibraryRepositoryImpl` has the same FROM for the owner's own library lists; that is a separate, smaller-traffic path and stays out unless the owner widens it.

**B. Cache the finished page for a short time.** Smaller code change, but it adds a second staleness rule on top of the snapshot's lag, and a crawler walking 44 distinct offsets never hits a cache.

**C. The database upgrade.** Raises the 0.1 CPU ceiling for everything, including facets and creator-impact that A does not touch. Owner decision, separate; see the 14-day checkpoint row and plan section 8.

A and C are not alternatives: A cuts the work, C raises the ceiling.

## Timing result (2026-11-01, local, from the 2026-10-30 production dump)

Restored into PostgreSQL 18 (`shared_buffers` 64 MB), ANALYZE run, container capped at 0.1 CPU. The dump is older than production (2,164 public notes against about 2,374 now). `EXPLAIN (ANALYZE, BUFFERS)`, three runs each, **noisy at 0.1 CPU because of CFS throttling**, so read the medians and the order of magnitude, not the digits:

| Statement | Runs (ms) | Median |
|---|---|---|
| S1 current by-id projection, 20 ids | 596, 301, 895 | about 596 |
| S2 same, per-row programs lookup | 405, 2.5, 96 | about 96 |
| S3 current `findPublicLibraryPage`, newest, offset 1000, limit 20 | 1695, 797, 1397 | about 1397 |
| S4 id-first page + per-row programs lookup, same page | 196, 99, 99 | about 99 |

So about 6x on the by-id projection and about 14x on the one-statement page, in this setup. **Parity of the programs array on the restored data: 2,164 public notes, 0 mismatches between the current join and the per-row lookup, including 53 notes with no programs (NULL in both) and 1,632 with more than one.** This is a local check, not the repository parity test the brief must require against the real statements. The container was removed afterwards; no production writes, no `EXPLAIN ANALYZE` on production.

Caveats: a smaller catalog than production; no concurrent load; the by-id form was not measured as the top production consumer (see the measured tails above), so the win that matters most in production is S3 to S4.

## What would have to be true before building A

0. ~~The owner re-confirms the CORRECTED scope~~ **Done: the 2026-11-02 Product UX decisions approve this optimization as first priority** (the owner's "go Option A" of 2026-11-01 approved the first draft, "page the keys first"; the programs-lookup half and the measured-tails finding came after).
1. ~~The owner's call on the upgrade~~ **Decided 2026-11-01: the upgrade is not an option at the moment**, so this change is the lever; it is no longer waiting on that call.
2. A timed, local `EXPLAIN (ANALYZE, BUFFERS)`, warm and cold, on the restored production dump at 0.1 CPU (`/Users/einarlagera/Downloads/2026-10-30T03_08Z.dir.tar.gz` still exists, 62 MB; Docker is up; 12 GiB free) for four statements: the current by-id projection, a per-row programs version, the current `findPublicLibraryPage`, and an id-first version of it. Those numbers are the brief's acceptance criterion; if the rewrite does not win clearly, stop. A merge join can stop early, so the estimate may overstate the problem. Note the program-note join is redundant (`program_note` is always `n`, and every caller already filters `n.visibility = 'PUBLIC'`), so a per-row lookup needs no join to `notes`.
3. The plan's deploy rule: a separately approved deploy under the section 5 checklist, and no deploy within 24 hours of a non-deploy kill.
4. Anti-drift: no pool change, no migration, no limiter, no `/explore` migration; facets and creator-impact are separate.

## Related open rows (ROADMAP Backlog, uncommitted on `docs/v0178-post-deploy`)

The 2026-10-31T14:08Z backend restart (health check timed out after a burst of slow listing queries; owner checks pending), the heavy admin page, and the auth-limiter `X-Forwarded-For` question. Diagnostics (`reviewBy=2026-11-12`) are the instrument for the "who sent the burst" half.


---

# PRODUCT UX DECISIONS, RECORDED 2026-11-02 (owner reviewed Google Search Console with Product UX)

Source: the Product UX reply to `2026-11-01-public-catalog-load-product-ux-prompt.md`. **These are owner decisions and supersede earlier tentative recommendations where they conflict.** Nothing below is implemented, merged, deployed or committed. The database upgrade is **not authorized**.

## 1. Decisions recorded

| # | Decision | Status |
|---|---|---|
| 1 | Invisible listing-query optimization (this note, Option A as corrected) | **APPROVED, first priority**, subject to normal stability-release approval; do not delay it for the SEO or crawler work |
| 2 | Freshness targets: sitemap up to 24 h; subject pages up to 1 h; exam hubs up to 1 h; ranked-listing snapshot keeps the accepted 5-minute behaviour; direct note visibility immediate. Single-flight or equivalent for cache misses; **no build-time catalog walks** | **APPROVED as maximum-staleness targets**; see the privacy question in section 3 |
| 3 | Anonymous pagination stays open, no sign-in wall, no depth cap; inspect page-depth and traversal data before any future limit; no invented threshold | **APPROVED (keep open)** |
| 4 | Crawler policy: keep high-value pages indexable; arbitrary filter and sort combinations generally `noindex`; deep pagination stays accessible, indexing policy needs technical SEO review; no blanket Googlebot or Bingbot block; do not blindly canonicalize every page to page 1 | **APPROVED in principle; nothing to change until the audit below is acted on** |
| 5 | AI and low-value crawlers: selective restriction approved in principle, per category; **no blocking or throttling until the owner separately approves rule, scope, thresholds and rollback**; name the enforcement point for each rule | **DEFERRED to owner approval** |
| 6 | Busy message and Retry copy unchanged; no "Browse by subject" fallback unless proven to work during the same failure; retries must not storm | **APPROVED (keep)** |
| 7 | Trade-off order, most to least acceptable: longer sitemap and hub windows; modestly longer ranked freshness only if needed; targeted limits on proven high-cost crawlers; depth limits (last resort, separate approval); sign-in walls (**rejected**); more frequent busy errors (**rejected**). Not an authorization to implement all | **RECORDED** |
| 8 | Hostname and SEO hygiene audit (focused, no redesign) | **AUDIT DONE, findings below; fix awaits owner approval** |
| 9 | Measurement with existing infrastructure and no paid services | **PLAN below** |

## 2. Audit findings (code read and cheap live checks, 2026-11-02)

**Verified:**
- **Hostname.** Vercel's project domains: `www.notelib.app` is the primary; `notelib.app` redirects to it (308, configured in Vercel, confirmed live with `HEAD`). `http://notelib.app` redirects to `https://notelib.app/`, then to `www`.
- **Canonical and sitemap use the apex, not `www`.** `SITE_URL = "https://notelib.app"` (`lib/site-metadata.ts:4`) feeds `metadataBase`, `absoluteUrl()` (so every `/sitemap.xml` entry), `buildPageMetadata()` canonicals (the library list, subject pages, public notes) and the `Sitemap:` line in `/robots.txt` (live: `Sitemap: https://notelib.app/sitemap.xml`). **The homepage and `/refund` hard-code `https://www.notelib.app`** (`app/page.tsx:31`, `app/refund/page.tsx:5`; live homepage canonical is `https://www.notelib.app`). So most pages declare a canonical on a host that 308-redirects away, while the homepage declares `www`, and Search Console shows both hosts. This is the one clear SEO defect found.
- **Library list URLs.** `/public/library` has one static `metadata` (`app/public/library/page.tsx:10`); every `?page=`, `?sort=`, filter variant serves the same shell with canonical `https://notelib.app/public/library` and **no `noindex`** (live check with `?page=3&sort=recent`). So filters and sorts are consolidated by canonical only, not excluded; canonical is a hint, and Search Console already shows a filtered nursing URL with impressions.
- **`robots.txt`** disallows only `/dashboard`, `/study`, `/settings`, `/profile`, `/api`, `/app`. Subject pages below `SUBJECT_PAGE_INDEX_THRESHOLD` are `noindex` and left out of the sitemap (`app/sitemap.ts`); exam hubs, `/explore` and `/public/library` are in the sitemap.
- **The catalog walk.** `getServerPublicNotes()` walks `page=N&pageSize=50&sort=recent` sequentially (44 pages today); callers: the sitemap (`force-dynamic`), the `general` subject page, exam hubs (one walk per program). There is **no in-flight deduplication** in `fetchAllPublicNotePages`; every fetch uses one global `revalidate: 300` for all callers, and the subject and exam pages also carry `revalidate = 300` (so two cache layers stack).
- **Who the walks look like to the backend.** Walks run in a Vercel function, so the backend sees the frontend host's AWS-range addresses, **not the crawler**. A per-IP limit at the backend would throttle our own frontend and cannot tell a crawler from a visitor for this traffic. Browser-side list calls (the list page is a client component that calls the Render API directly) do carry real client addresses.

**Hypotheses (not established):** that a walk, not a flood of single-note requests, caused the 2026-10-31 incidents (see "Probable trigger shape" above); that concurrent cache misses across function instances trigger repeated walks (not tested; Next deduplicates identical fetches within one render, and I have not verified behaviour across concurrent invocations); that stacked page and fetch TTLs can double the worst-case staleness (standard behaviour, to be tested).

## 3. One decision needs the owner's wording before any freshness change

Decision 2 says unpublishing and privacy "must remain immediate". **Verified:** the note page itself is (`fetchPublicNote` uses `cache: "no-store"`). **But subject pages, exam hubs and the sitemap render titles, excerpts (up to 2,000 characters of content) and slugs from cached lists.** Today that exposure after a note is made private is bounded by 5 minutes. The approved targets lengthen it to up to **1 hour (subject pages, hubs)** and **24 hours (sitemap entries)**, and the stacked TTLs could roughly double the page figure. The owner should decide which of these it means: (a) accept a bounded exposure of the title and excerpt on cached list surfaces, with the note page immediate; or (b) require on-demand invalidation when a note is unpublished (more engineering, a backend-to-frontend call). Recommendation: **(a) for the sitemap** (it carries only the slug, and search engines recrawl slowly), **(b) or a shorter TTL for the hubs and subject pages** if the owner treats a private note's excerpt appearing for an hour as a privacy breach. Until decided, do not lengthen the 5-minute TTLs on pages that render excerpts.

## 4. Minimal proposed follow-up (all awaiting approval; none started)

1. **Option A, backend, invisible.** As scoped above, with the parity, tiebreak, filter, visibility and bounded-work checks Decision 1 lists. First priority.
2. **Frontend cache work (separate small item, after the privacy wording).** Give the walker a per-caller TTL (sitemap 24 h; subject and hubs per section 3) and a cache key or tag that does not collide with the others; single-flight the walk within an instance (a module-level in-flight promise, which helps under Vercel Fluid concurrency); keep `force-dynamic` on the sitemap (no build-time walk). Design question to settle in the brief: whether sharing one fetch cache entry between callers with different TTLs behaves as intended. Measure rebuild cost, count, concurrency and the database CPU, memory and connections separately.
3. **Hostname fix (one line plus tests, frontend only, owner approval).** Make `SITE_URL` `https://www.notelib.app`, so canonicals, sitemap entries and the `Sitemap:` line match the host the apex redirects to; align the two hard-coded pages. Risk: Google re-evaluates canonicals for all pages for a while; the redirect already exists, so no URL stops working.
4. **Do not change `noindex` or robots yet.** Filtered and sorted URLs are consolidated by canonical today. Moving them to `noindex` needs the list page's metadata to depend on `searchParams` (a dynamic metadata function), with a test that `/public/library` and the subject, hub and note pages are never `noindex`. Do that only after the owner reads the measurement below, and with technical SEO review for pagination.
5. **No limiter, no blocking** until separately approved. When approved, the enforcement points are: for walk traffic, caching and single-flight on the frontend (a Vercel firewall rule on `/sitemap.xml`, `/public/library/[subject]` and `/exam/[slug]` is possible but I have not re-read the current firewall configuration or its plan limits here); for browser-direct API traffic, only a backend limiter, which is a later release whose threshold must come from the diagnostics read (due 2026-11-06).

## 5. Measurement plan (existing infrastructure, no paid services)

The Product UX note applies: **count cost, not requests.** A crawler fetching 100 note pages is 100 cheap requests; one sitemap or hub request can be 44 sequential catalog queries (about a minute of the database's CPU today, about 5 s after Option A). Request counts hide the second pattern.
- **Cost side.** `pg_stat_statements` total and mean time per statement (read-only, already used; reset by a crash); Render database CPU, memory and connections; the `PUBLIC_API_DIAGNOSTICS` per-minute lines (`reviewBy=2026-11-12`). **Proposed small addition, needs approval:** the frontend walker sends a request header naming its entry point (`sitemap`, `subject`, `exam-hub`), and the backend recorder counts it and sums time per path group, so a walk and a flood of single reads are separable and a walk is countable (the count of `page=0` walker requests). Verified gap without it: the recorder cannot see the crawler behind a walk.
- **Benefit side.** Google Search Console (owner, 28-day clicks, impressions and landing pages; filtered URLs separately); Vercel Web Analytics for landing pages and referrers (read through the Vercel connector); sign-ups from organic landing pages only where reliably attributable; AI-search referrals only where the referrer is visible. Do not treat public-note view events as benefit.
- **Do not** attribute a kill or restart to a named crawler without a log that ties the burst to it. The 2026-10-28 "about 7,200 anonymous public-note events" are not Google Search clicks and are not attributed to Googlebot.

## 6. Risks and rollback

- **Accidental deindexing** (the largest): any change that makes `/public/library`, subject pages with enough notes, hubs, `/explore` or public notes `noindex`, or points canonicals at the wrong host. Mitigation: tests that assert those surfaces are never `noindex` and that canonical equals the final host; no robots or `noindex` change in the stability release; roll back by reverting the frontend deploy (Vercel instant rollback); expect Search Console to take days to settle either way.
- **Legitimate-user blocking:** none proposed. Any limiter must exempt the frontend's own server-side calls or it will throttle ourselves (verified: walk traffic arrives from the frontend host).
- **Stale exposure of a private note's excerpt** (section 3): bounded by TTL, decided by the owner before any lengthening.
- **Cache design:** a shared fetch cache entry across callers with different TTLs may not behave as intended; test before relying on it. Single-flight is per instance, not global; it reduces repeats and does not eliminate them.
- **Option A:** parity risk on `applicablePrograms` and the sort tiebreak, covered by the old-versus-new test against real PostgreSQL rows; rollback is the normal backend redeploy, which has its own connection overlap and the "no deploy within 24 hours of a non-deploy kill" rule (the 07:41Z 2026-10-31 kill has passed that window).

## 7. Remaining owner approvals

1. Release kickoff for Option A (`releases/v0.179.0` from `main`, carrying the uncommitted post-deploy docs) and the explicit "commit".
2. The Option A deploy, under the section 5 checklist (backup, off-peak, watch and stop conditions).
3. The privacy wording in section 3, then the frontend cache TTLs.
4. The hostname change (`SITE_URL` to `www`).
5. The measurement header and the per-path-group timing in the backend recorder.
6. Any crawler restriction, rate limit, firewall rule or `noindex` rule (rule, scope, thresholds, rollback, enforcement point), after the 2026-11-06 diagnostics read.


---

# FINAL PRODUCT UX APPROVALS, RECORDED 2026-11-02 (with the cache verification they required)

**Approved (owner, via Product UX):** (1) sitemap: stale URLs up to 24 h are acceptable, **URLs only**; (2) subject pages and exam hubs: the effective maximum exposure of a newly private note's title and excerpt is **5 minutes including stacked caches, an upper bound and not a target**, with no backend-to-frontend invalidation unless a simpler approach cannot meet it, and longer TTLs reassessed only after Option A ships and is measured; (3) canonical host `https://www.notelib.app` (canonical metadata, sitemap URLs, `robots.txt` sitemap line, related canonical-host settings); (4) a bounded, allowlisted catalog-walk attribution header counted by the backend, diagnostic only, never used for authorization, rate limiting or blocking. **Not approved and out of scope:** blocking, rate limits, new robots or `noindex` rules, pagination caps, sign-in walls, upgrades, new infrastructure, redesigns, Challenge Quiz. Merges, deploys and infrastructure changes need separate owner approval.

## A. Verified: the current caching does NOT meet the approved 5-minute bound

Local experiment (2026-11-02): a minimal Next.js 16.1.6 app (the repo's own Next, `next start`, 5 s TTLs standing in for 300 s) with a mock API whose data I changed mid-run. Scripts and output are in the session scratchpad, not the repo.

| Setup | Result |
|---|---|
| ISR page (`revalidate = 5`) over a shared `fetch(... next.revalidate = 5)`, continuous traffic, data changed 5 times | change reached `/a` in 1.1 to 2.0 s, `/b` in 2.3 to **5.9 s** (two of five over the TTL): the two caches stack |
| Same, **idle for 40 s (8x the TTL)** after the change | the **first request after the idle still returned the old data**; only the next request returned the new |
| ISR page over an in-process hard-TTL cache, idle 40 s, default config and `expireTime: 10` | **still stale on the first request in both**: `expireTime` did not force a blocking regeneration locally |
| `force-dynamic` page over `fetch(... next.revalidate = 5)`, idle 40 s | **stale on the first request**: the fetch data cache is itself stale-while-revalidate |
| `force-dynamic` page over an in-process cache with a hard TTL and single-flight, idle 40 s | **fresh on the first request**; 10 parallel requests after expiry made **1** upstream call |
| Custom request header on `fetch` | **splits the data-cache entry** (two fetches of one URL with different headers made two upstream calls) |

Next's own build table shows `Revalidate 5s, Expire 1y` for the ISR pages. **So the production subject pages and exam hubs (`revalidate = 300` over a `revalidate: 300` fetch) today expose a withdrawn title and excerpt (a) for up to about two TTLs under traffic and (b) for an unbounded time in a quiet period, until the next request, which is served the stale page.** This was true before `v0.178.0`. Not verified: the Vercel CDN layer in front of the ISR cache (it uses the same stale-while-revalidate model per Vercel's documentation, but I did not test it), and `expireTime` on Vercel.

**Backend layers (read in code):** no `Cache-Control` headers anywhere in the backend; the discovery cache holds only id lists (`PublicLibraryDiscoveryRankingCache.Snapshot`) and the by-id projection re-checks `n.visibility = 'PUBLIC'`; the ranking snapshot holds metric maps and tag-to-id membership only (visibility stays live, as `v0.178.0` documents); `sort=recent` listings are live SQL. **The backend adds no excerpt exposure. Not verified:** whether the platform's edge in front of the backend (Render, Cloudflare) caches these responses; nothing in the code asks it to.

## B. Selected caching approach and the maximum exposure it gives

Subject pages and exam hubs: **render dynamically** (no ISR page cache) over **one in-process, hard-TTL, single-flight cache keyed by the upstream query**, with **no other layer** (no fetch data cache, no ISR). A withdrawn title or excerpt is then visible for at most the in-process TTL plus one rebuild (about 5 s after Option A), per instance. **Proposed TTL: 120 s, so the verified maximum is about 2 minutes, inside the 5-minute bound with margin.** It holds in quiet periods too (verified: the first request after idle is fresh). No invalidation mechanism is needed, and it also covers an author editing text out of a note that stays public. Costs, stated plainly:
- the first request after a rebuild is slow (about 44 x 0.1 s after Option A; about a minute before it), so **this must ship together with or after Option A, never before it**;
- pages are no longer CDN-cacheable, so every visit runs a function (Hobby-plan invocation and duration limits not verified);
- the cache is per instance (Vercel Fluid reuses instances, but several can exist), so repeats are reduced, not eliminated; single-flight removes concurrent duplicates inside one instance only.
**Sitemap:** the same in-process cache with a **24 h** hard TTL (a cold instance walks once). The existing fetch cache is stale-while-revalidate, which would let a sitemap request after a 3-day gap return a 3-day-old list.

## C. Material finding for the owner: "URLs only" is not title-free

A public note's URL contains a slug **derived from its title** (`/public/library/{subject}/{slug}`; `buildPublicLibraryNotePath`). So a note made private stays listed in the sitemap, and therefore keeps its title in slug form, for up to 24 h, although the note itself returns not-found at once. The approval says the sitemap must never carry titles. **Decision needed:** (a) accept that the slug (which was public until the note was withdrawn) stays listed for up to 24 h, (b) shorten the sitemap TTL (for example 1 h; the cost is one walk an hour per instance, about 5 s of database CPU after Option A), or (c) accept (a) and record that the slug is part of the URL. Recommendation: (b), because after Option A the cost of a shorter sitemap TTL is small and the approved 24 h was a ceiling chosen for load, not a requirement. Until decided, the plan uses 24 h only if the owner chooses (a).

## D. Canonical host: exact files and settings

- **Change:** `frontend/lib/site-metadata.ts:4`, `SITE_URL` to `https://www.notelib.app`. This one constant feeds `metadataBase` (`app/layout.tsx:33`), `absoluteUrl()` (every sitemap URL), `buildPageMetadata()` (canonicals for the library list, subject pages, hubs, public notes), structured data URLs, and the `sitemap:` line in `app/robots.ts`.
- **Already `www` (leave, or point at `SITE_URL`):** `app/page.tsx:31-32`, `app/refund/page.tsx:5`.
- **Tests to update:** about 58 expected-value references across about 15 frontend test files assert the apex (for example `sitemap.test.ts`, `robots.test.ts`, `layout.test.ts`, the library, subject and note page tests). Add a test that no indexable page canonical or sitemap URL uses the apex.
- **Not changed:** the Vercel domain redirect (apex to `www`, 308, already correct); the backend email templates, which link to the apex (the redirect keeps them working; outside the canonical-metadata scope, optional later).
- **Verify after deploy:** canonicals of one public note, a subject page, an exam hub, `/explore`, `/public/library`; `robots.txt` `Sitemap:` line; sitemap entries.

## E. Attribution header and counter design

- **Header:** `X-NoteLib-Catalog-Walk`, value from the allowlist `sitemap`, `subject`, `exam-hub`. The frontend sends it only on upstream fetches that actually miss the in-process cache, so the count is **walks started by entry point**, not visits (the cache key does not include it).
- **Backend:** in `PublicApiDiagnosticsAggregator`, for requests in the `listing` group, read the header, exact-match the allowlist (anything else counts as `other`, absence as `none`; the value is never stored or logged), and increment a fixed array of counters. One new field on the existing per-minute line, `walk=[sitemap:N,subject:N,exam-hub:N,other:N]`. No new maps, no addresses or user agents tied to it, no effect on any response, guarded like the rest of the recorder.
- **Not a trust signal:** documented as **client-asserted**. Verified: the backend's CORS allows only `Authorization`, `Content-Type`, `X-Request-Id` and the feedback header, so a browser on another origin cannot send it; a script or `curl` can, so it can be forged and is never used for authorization, rate limiting or blocking. The header is ignored by an old backend, so deploy order does not matter (no API form is removed or made required).

## F. Remaining decisions and risks needing the owner

1. The sitemap slug question in section C (1 h, or 24 h with the slug acknowledged).
2. Approval of the dynamic-render plus in-process-cache approach and its costs in section B, including the 120 s TTL and the sequencing rule (not before Option A).
3. Whether the owner wants the platform-edge and Vercel-CDN layers verified by a one-off test on a preview deployment before relying on the 2-minute figure (the local result uses `next start`; a Vercel preview deployment would test the real CDN, costs one build, and touches no production).
4. Release shape: Option A and the header counter are backend; the cache change, canonical host and header sender are frontend; one release, both platforms deploy, and the plan's section 5 checklist and watch conditions apply to the backend deploy.


---

# FINAL PRODUCT UX DECISIONS 2, RECORDED 2026-11-02, AND IMPLEMENTATION READINESS

**Decided (owner, via Product UX; supersedes earlier freshness approvals where they conflict):**
1. **Sitemap: option (b), a 1-hour effective bound** (title-derived slugs make 24 h unnecessary exposure). The bound applies to NoteLib's own sitemap output, not to URLs already in external search indexes. Not to be proven by `revalidate: 3600` alone; the first request after an idle period must be verified. If it cannot be verified, return the smallest alternative for approval.
2. **Subject pages and exam hubs: dynamic rendering over a 120 s hard-TTL, single-flight, in-process cache**, with no other stale-serving layer. The hard TTL governs the data actually served, not when a refresh starts: expired data is never served while rebuilding, and a failed refresh never falls back to expired data. Same bound for notes edited while public; direct-note revocation stays immediate; bounded memory; per-instance duplication accounted for. A cold rebuild of about 5 s is monitored; if harmful, optimize the rebuild, do not weaken privacy without approval.
3. **Vercel Preview verification is required before any production frontend deploy.** Pass: effective withdrawal exposure demonstrated within 5 minutes at the 120 s target with no extra stale-serving layer. If it fails, that implementation does not proceed.
4. **One release milestone, staged deployment: Stage A backend first, Stage B frontend second.** Each deploy needs separate explicit owner approval.

## Verified CI/CD facts (2026-11-02)

- **Render:** `notelib-backend-prod`, `rootDir = backend`, `autoDeploy = yes`, trigger `commit`, branch `main` (service settings read). **Two docs-only merges to `main` produced no Render deploy:** `93a6a134` (2026-09-10 07:46Z, no deploy between 05:59Z and 11:59Z) and `76e77927` (2026-09-15 01:55Z, no deploy between 00:22Z and 07:11Z). Every release merge in the sampled history touched `backend/` and deployed. So a merge that changes nothing under `backend/` does not redeploy the backend (two samples, consistent with the root-directory setting).
- **Vercel:** auto-builds from `main`; SSO protection is on for non-custom domains (previews need a bypass). **NOT established:** whether a merge that changes nothing under `frontend/` triggers a build. The deployment-list lookup by commit works (a control commit returned its deployment) but returned nothing even for a September release merge that certainly built, so Vercel's history does not reach back far enough to test; the Ignored Build Step and root-directory settings are not exposed by the connector. **The owner can read Settings, Git, Ignored Build Step in the Vercel dashboard.**
- **Consequence for staging.** The staging can be done with the existing merges, but only if each stage's diff is confined:
  - **Stage A diff: `backend/` plus docs, and the version bumps.** The `frontend/package.json` bump (part of the kickoff checklist) is a change under `frontend/`, so the Stage A merge will probably start a Vercel production build of otherwise identical code (the build is now one backend request, so it is harmless). That cannot be prevented without the Vercel setting; the frontend behaves identically.
  - **Stage B diff: `frontend/` plus docs, with NO version bump and nothing under `backend/`.** A `pom.xml` bump in Stage B would redeploy the backend (a deploy followed by a kill in 4 of the last 5). So **both version references are bumped once, in the Stage A merge** (`v0.179.0`), and Stage B ships as the second merge of the same release milestone without changing a version number. This departs from the 7-reference version-sync convention for the Stage B merge and needs the owner's OK.
  - Stage B is branched from `main` after Stage A is merged and stable (not from the release branch's pre-A state).

## 1. Exact scope

**Stage A, backend (nothing under `frontend/` except the package.json version):**
- `PublicLibraryRepositoryImpl`: per-page programs lookup in the projection FROM (keyed on `n.id`, same public-note rule, same name order, so `applicablePrograms` is byte-identical; no join to `notes` is needed because `program_note` is always `n` and every caller filters `n.visibility = 'PUBLIC'`); `findPublicLibraryPage` (two sorts) selects the page's ids first, then uses the by-id projection. Consumers of `NOTE_LIST_ITEMS_FROM` in this class: `findPublicLibraryPage` (line 220) and `findPublicLibraryListItemProjectionsByIdIn` (line 614); indirect callers inherit the change: `PublicLibraryDiscoveryRankingCache` (157) and `NoteService` (943). **`NoteLibraryRepositoryImpl` is out of scope and unchanged.**
- Walk counter in `PublicApiDiagnosticsAggregator`: header `X-NoteLib-Catalog-Walk`, allowlist `sitemap|subject|exam-hub`, anything else `other`, absent `none`, a fixed counter array, one new field `walk=[...]` on the existing minute line; value never stored; client-asserted; never used for any decision.
- Tests: old-versus-new statement parity against real PostgreSQL rows (the Postgres 16 container `NativeQueryPostgresIntegrationTest` already starts) for every sort, first page, deep page and past the end, zero, one and many programs, name order, filters and the tiebreak; a bounded-work check; counter tests (allowlist, cap, no value stored, no effect on the response). A `MockMvc` test with a real request and `Content-Type`.
- Docs, `RELEASES.md`, the public-library feature doc.

**Stage B, frontend (nothing under `backend/`, no version bump):**
- One cache module: hard TTL, single-flight, measured from the walk's **start**, bounded entries and items, no partial results cached, no expired serve, no fallback on failure; effective TTL is `min(configured, 120 s)` for pages and `min(configured, 3600 s)` for the sitemap, so no environment value can raise the bound.
- Subject pages and exam hubs: `dynamic = "force-dynamic"`, no ISR, no fetch data cache above the module.
- Sitemap: `force-dynamic` over the same module with the 1 h cap (the walk's slugs are the only title-derived content; no excerpts).
- The walker sends the allowlisted header on cache-miss fetches only.
- `SITE_URL` to `https://www.notelib.app` (`frontend/lib/site-metadata.ts:4`; feeds `metadataBase`, sitemap URLs, canonicals, structured data and the `robots.txt` sitemap line), the two hard-coded `www` pages aligned, about 58 test expectations across about 15 files updated, and a test that no indexable canonical or sitemap URL uses the apex.
- Tests for the cache module (expiry, single-flight, failure, bound, no stale serve, eviction), the failure behaviour of the pages, and the header.
- **Not in scope anywhere:** blocking, rate limits, robots or `noindex` changes, pagination caps, sign-in walls, upgrades, redesigns, Challenge Quiz.

## 2. How the privacy bounds are enforced

- **The TTL is checked when data is served.** An entry records the walk's start time; a read serves it only while `now - startedAt < ttl`; an expired read waits for one shared refresh and is served only that fresh result. There is no path that returns an expired entry (no stale-while-revalidate), and a failed refresh propagates; it is neither cached nor replaced by old data.
- **A failed walk must not become an empty page.** The current walker returns what it has collected if a later page fails, and a subject page with too few notes renders `noindex`. Under the new rule a failure raises an error, so the response is a server error (crawlers treat it as temporary), never a 200 with a short list and never a `noindex` page. This is a behaviour change from today and needs the owner's OK (section 5 below).
- **No second layer.** Pages are dynamic (no ISR page cache), the fetch data cache is bypassed, and the preview must show no CDN or router cache serving the page (headers and repeat requests).
- **Bound:** a withdrawn title or excerpt is visible for at most the TTL (120 s) plus the render itself; a note edited while public is covered the same way; the sitemap for at most 1 h plus the render. Direct-note revocation is unchanged (the note route is `no-store`).
- **Memory:** a cap on cached keys and on total cached notes, least-recently-used eviction, single-flight entries removed in a `finally`; a request that would exceed the cap is served uncached.
- **Per-instance duplication:** each instance holds its own entry and walks once per TTL per key; Vercel instances are not controllable, so this is bounded by the instance count and measured, not eliminated.

## 3. Vercel Preview verification (procedure and pass/fail)

- **Setup:** a preview deployment from a throw-away branch `verify/v0179-cache` that is never merged; it adds a test-only stub API route whose state is a **pure function of the wall clock** (so every instance agrees): a controlled catalog of about 120 notes that includes one marker note withdrawn at time `T_withdraw`, one marker note edited at `T_edit`, and an upstream failure window `T_fail0` to `T_fail1`; the frontend's API base points at the stub for that preview only. No production data, no production API calls. **Prerequisites that are the owner's:** the preview's SSO protection bypass (a shareable link or bypass secret), and the preview-scoped API-base setting (a Vercel environment-variable change, an infrastructure change I will not make without explicit approval).
- **Tests and pass criteria** (TTL 120 s unless stated; "marker" means the withdrawn note's title or excerpt):
  1. Subject page and exam hub, polled every 10 s across `T_withdraw`: the marker is absent from every response later than `T_withdraw + 120 s + one render`. **Fail if any response after that contains it.**
  2. First request after an idle period of at least 10 minutes spanning `T_withdraw`: contains no marker.
  3. 20 parallel requests at cache expiry: no response contains expired data; upstream walks per instance for that key at most 1 (measured from stub hit counts per instance id).
  4. Response headers and CDN behaviour on both pages: no `s-maxage` or `stale-while-revalidate`; `x-vercel-cache` is `MISS` or `BYPASS`, never `HIT` or `STALE`; a second request is not served from an edge cache.
  5. Upstream failure window: after the TTL the page returns a server error, never an expired response and never a 200 with an empty list or `noindex`; recovery after the window on the next request.
  6. Edited marker note: the old text is absent after `T_edit + 120 s + one render`.
  7. Sitemap: with the TTL shortened by the (clamped) setting, the same tests as 1, 2, 5; plus **one real-length run**: a withdrawn slug absent from the sitemap by `T_withdraw + 65 minutes` (the first request after at least that long).
  8. Instance behaviour: count distinct instances seen and walks per TTL window, to quantify duplication.
- **If the preview differs materially from production** (a protected deployment, custom-domain behaviour, plan limits), document it and define the smallest extra check before production. **If any of tests 1 to 6 fails, that cache implementation does not ship.**

## 4. Backend-first sequencing under the current CI/CD

1. Kickoff `releases/v0.179.0` from `main`, bumping both versions once (needs the owner's "commit").
2. Stage A on its own branches into the release branch; the Stage A release PR to `main` has no `frontend/` change except `package.json`'s version and may start a harmless Vercel rebuild of identical code (not preventable without the Vercel setting, which the owner may read).
3. Stage A deploy only on explicit owner approval, with the plan's section 5 checklist (verified backup, off-peak, memory, CPU and connection baseline, kill log, stop conditions), and the compatibility check: the new backend works with the deployed frontend (responses are identical; the counter reads an extra header the current frontend never sends).
4. Stage A stable (the 24-hour and 72-hour reads against a Stage A baseline), the Preview verification passes, then Stage B is branched from `main`, its PR has no `backend/` change and no version bump, and it deploys only on explicit approval.

## 5. Remaining risks and rollback

- **Stage A:** parity or tiebreak error (the old-versus-new test guards it); a deploy overlap with the connection pool (4 of the last 5 deploys saw a kill; the last kill was 2026-10-31T07:41Z, so the 24-hour rule is clear). **Rollback:** the owner redeploys the previous Render deploy from the dashboard (itself a deploy with an overlap) or reverts the merge; the counter change is inert.
- **Stage B:** a cold rebuild blocks a visitor (about 5 s after Stage A, about a minute without it, hence the ordering); loss of CDN caching raises function invocations (Hobby duration and invocation limits not verified); rebuilds are per instance (estimate: if a crawler visits every subject and hub within one TTL, roughly 170 to 250 listing queries per 2-minute cycle, about 15 to 20% of the database's CPU after Stage A; a measurement, not a guarantee). A canonical-host change makes search engines re-evaluate canonicals for a while (no URL stops working). **Rollback:** Vercel instant rollback to the previous production deployment (an owner action).
- **Deindexing:** guarded by the failure rule above and the tests that key surfaces are never `noindex` and never use the apex.

## 6. Blockers and decisions that are the owner's

1. "Commit" for the `v0.179.0` kickoff, and the Stage B version-bump exception (section above).
2. The failure behaviour: a refresh failure returns a server error instead of a short, `noindex`-risky page (smallest alternative: an unavailable page with a 503; the App Router makes a precise 503 awkward, so a thrown error is the simple form).
3. The Vercel Ignored Build Step setting (read-only check by the owner), and the preview prerequisites (SSO bypass, preview-scoped API-base setting).
4. Verifying Hobby function duration and invocation limits before Stage B (not checked).
5. Every merge and deploy, separately.
