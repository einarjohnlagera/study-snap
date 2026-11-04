# Preview verification harness (THROW-AWAY, branch `verify/v0179-cache`, never merged)

Purpose: prove, on a real Vercel Preview deployment, that Stage B's hard-TTL cache keeps a withdrawn note's title and
excerpt visible for at most the approved bound (subject pages, exam hubs and note-page rails 120 s; sitemap 1 h),
that no other layer (CDN, page cache) serves stale content, and what happens when the upstream fails.

## How it works
- `preview-verify/stub.ts`: a fake catalog API whose state is a **pure function of the wall clock**, so every serverless
  instance agrees. Schedule per 15u period (u = page TTL = 120 s, so 30 minutes): marker note, edited note and marker plan
  are withdrawn/edited at 4u; list and plan endpoints return 503 from 10u to 11u. Every list response carries a "probe"
  note whose title holds the instance id and a per-instance rebuild counter, so rebuilds and instances are visible in the HTML.
- `instrumentation.ts` + `preview-verify/install.ts`: wrap `globalThis.fetch` so stubbed API paths are answered
  **in-process**. A request to the configured API base that the stub does not own is **blocked (404) and recorded**: the real
  API is never contacted. No environment variable is needed; it activates when `VERCEL_GIT_COMMIT_REF` is
  `verify/v0179-cache` (or `PREVIEW_VERIFY=1` locally). It also shortens only the sitemap TTL to u/2 (the module's
  lowering-only override).
- `app/preview-verify-state/route.ts`: reports the schedule, this instance's id and counters.
- `preview-verify/run.mjs`: the driver. It waits for the next period start, then runs the tests and prints PASS/FAIL.

## Prerequisites (the owner's)
1. The branch is pushed so Vercel builds a Preview (a push triggers a build; nothing reaches production).
2. The Preview is protected by Vercel Authentication. Provide **either** the project's *Protection Bypass for Automation*
   secret (Project Settings, Deployment Protection) **or** say so and Claude will request a shareable access link.

## Run
```
cd frontend
node preview-verify/run.mjs --base https://<preview-host> --bypass <secret> --out results.json
```
Allow up to 30 minutes to reach the next period start plus about 23 minutes of run. Keep the machine awake.

Local dry run at one-tenth scale (3-minute period, 12 s TTL):
```
npm run build
PREVIEW_VERIFY=1 PREVIEW_VERIFY_SCALE=0.1 npx next start -p 3100
node preview-verify/run.mjs --base http://localhost:3100
```

## What each test asserts
| id | assertion | pass when |
|---|---|---|
| T0 | preconditions | every page renders the marker before the withdrawal |
| T1a, T1b, T1c, T9, T6 | subject page, exam hub (note and plan), note-page rails, edited text | no response later than withdrawal + 1u + a small render allowance shows the withdrawn or old content; later responses show the new state |
| T7 | sitemap (TTL shortened to u/2) | same, bound u/2 |
| T2 | first request after 6u idle spanning the withdrawal | no withdrawn content |
| T3 | 20 parallel requests after expiry | all 200, at most one rebuild id per instance, no expired id served |
| T4 | headers | no `s-maxage` or `stale-while-revalidate`; `x-vercel-cache` never HIT, STALE or PRERENDER |
| T5a | upstream failure after the TTL | pages show NO expired data; the sitemap returns a 5xx |
| T5b | same, note page | 200 with EMPTY rails, no expired list |
| T5c | recovery | every page 200 with fresh data |
| T8 | instances and safety | instances seen; the real API was never contacted |
| T5c (B3b) | recovery | every page back with fresh data within 25 s of the failure window ending (15 s failure backoff plus margin) |
| T10, T11 | strict walks | a page without `hasMore`, and an empty page that still says `hasMore: true`, fail closed (error shell, no list) |
| T12 | 400-page guard | an endless catalog fails closed; runtime logs show `CATALOG_WALK_GUARD_BREACH ... pages=400` |
| T13 | valid empty catalog | `hasMore: false` with no items renders the normal empty subject page |
| T14 | junk slugs | 60 junk subject URLs render the existing empty page; logs show about one `CATALOG_WALK ... notes=0` per request (not cached) |
| T15 | slow walk | 20 pages x 6 s (each under the 15 s request timeout): an error shell at min(90 s deadline, page TTL). A 504 or an earlier end at scale 1 means the platform function limit is below 90 s |
| T17 | sitemap | complete for the stub catalog, every `<loc>` on the `www` host |
| T18 (B4) | skeleton streams early | on a slow subject (`verify-zeta`) the text-free skeleton marker (`data-public-page-skeleton="listing"`) and the site nav arrive within 5 s, with no catalog data and no "Subject Library" in the early bytes |
| T19 (B4) | prefetch starts no walk | a prefetch-style RSC request (`RSC: 1`, `Next-Router-Prefetch: 1`) to a slow loading-bounded subject (`verify-theta`) returns in under 5 s with a small payload and no catalog data (baseline on B3 without `loading.tsx`: 1.2 s, 251 bytes); also check the runtime logs that no walk started |
| T20 (B4) | note route | the note page renders and carries no skeleton marker (the note route has no loading boundary) |
| T21 (B4) | limited bots | `facebookexternalhit` and `Twitterbot` get complete HTML (content present, title in the head); the raw streamed HTML also contains the text-free skeleton fallback, which is reported, not forbidden; N1 records what a browser user agent gets |

B4 evidence that needs the Preview's runtime logs (within the hour): `CATALOG_CACHE_STATS` lines (format, fields, one per minute per instance, no key or slug, `inst` distinct per instance, a burst showing `join` counts), and no `CATALOG_WALK` for the prefetch probe. The hub skeleton is unit-tested only (the stub cannot slow a hub).

B3 notes: the deadlines are fixed constants, so the slow-walk phase is real time even in a scaled dry run (where the page TTL override clamps the deadline to u). The 240 s sitemap deadline cannot be tested here (the harness shortens the sitemap TTL, which clamps it); read the platform limit in Settings, Functions. Evidence that needs the Preview's runtime logs (1 hour retention on Hobby): the guard breach line, the deadline line and the not-cached junk walks.

## Known limitations (read before relying on a PASS)
- **L1, found by the local dry run: pages do NOT return a real 5xx on upstream failure.** `app/layout.tsx` wraps all page
  content in a root `<Suspense fallback={null}>`, so the HTTP status commits as 200 before the page's data resolves; a
  thrown error becomes an in-page error shell. The sitemap (a route handler) does return 500. The privacy requirement is
  unaffected (the failure responses carry no data), but "a crawler sees a 5xx" is not true for pages. The driver reports
  this as a NOTE, not a failure.
- The sitemap is verified at a shortened TTL (u/2) to keep the run short; **the real 1-hour sitemap bound is covered by the
  cap constant and unit tests, not by this run**. A real-length check needs a separate run with a longer schedule.
- The stub removes real upstream latency (it adds about 100 ms per call), so the first-request slowness after a rebuild is
  not measured here.
- A single local instance cannot show multi-instance behaviour; only the Vercel run can (T3 and T8).
