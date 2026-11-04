# Product UX report: B4 (loading skeleton, prefetch fix, cache statistics) and the Stage B verification state (v0.179.0)

Written 2026-11-04 by the Release Implementor session. Paste this whole file to Product UX. It answers your "Required next response" list in order (items 1 to 9). **Nothing here is a deploy recommendation. No merge, deployment, backend change or infrastructure change is authorized by this document; Stage B deploy needs the owner's separate explicit approval.** Local-run and Vercel Preview results are reported separately throughout.

## 1. Implementation summary

B4 (PR #1558, branch `feat/v0179-b4-loading-and-stats`, head `7d620428`, stacked on B3) is the approved "B-lite" plus the prefetch fix you approved as Option C + D.

- **Exam hub:** a route `loading.tsx` renders a static, text-free skeleton (`components/public-page-skeleton.tsx`: `aria-busy`, no copy, `data-public-page-skeleton="listing"`). Its metadata is static config, so a prefetch of it starts no walk.
- **Subject listing:** **no `loading.tsx`.** The page returns a shell at once with `<Suspense fallback={<PublicListingSkeleton />}>` around an async `SubjectListingContent` (in `subject-listing-content.tsx`, because a Next page file may only export route fields). `generateMetadata` is unchanged. What the page awaits is unchanged: B-lite's rule holds.
- **Prefetch fix (Option D):** `prefetch={false}` on every `<Link>` to a subject listing, plus a static guard test and a development rule (comment above `buildPublicLibrarySubjectPath`).
- **`CATALOG_CACHE_STATS`:** one lazy per-minute line per instance (`inst`, `windowMs`, per key class `hit`, `miss`, `join`, `backoff`, wait buckets `w1` under 1 s, `w10` 1 to under 10 s, `w10p` 10 s or more, `err`). No timer, no key, slug, path or query. It counts cache **reads**, not requests: a cold subject request is normally one miss plus one join (metadata and page).
- **Unchanged:** 120 s hard TTL, 90 s page deadline, 240 s sitemap deadline (provisional), 15 s failure backoff, single-flight, strict `hasMore`, de-dup by note id, the HTTP 200 error shell (L1), the sitemap rules, the `w10p` observation threshold, every deploy-plan hard stop, the `main` freeze and the rollback plan.

**How we got here (so the record is honest):** the first B4 build used a route `loading.tsx` on the subject listing and made a link prefetch start a walk (a prefetch-style request to a slow subject hung 12 s; the B3 Preview baseline had returned in 1.2 s with 251 bytes). Your Option 1 (a header guard) was conditionally approved and then proved **infeasible**: Next.js 16.1.6 does not expose `Next-Router-Prefetch` or `RSC` to `headers()` (7 headers for a plain and a prefetch-style request alike; absent in a real browser prefetch too). It was discarded and not committed. A control experiment then showed the real cause: **a real browser's prefetch of any dynamic route runs its `generateMetadata`, with or without a `loading.tsx`**, and the subject listing's metadata walks the catalog. So Option C alone did not fix it (3 walk starts, one per subject link, in a real-browser run), and the behavior predates B4 (present since B1's force-dynamic design). Option C + D, as you approved, fixes it.

## 2. Link inventory (three sites, not two)

My first audit said two; a third existed behind an alias variable. All three now carry `prefetch={false}`:

1. Note page breadcrumb: `app/public/library/[subject]/[slug]/page.tsx`, `href={subjectPath}` (alias variable).
2. Note page "See all" link, same file.
3. Exam hub subject chips: `app/exam/[slug]/page.tsx`.

I looked in the library index client, the profile page, the published-plans client, the app shell and the help pages: none links to a subject listing through the builder. **Guard:** `lib/subject-link-prefetch.static.test.ts` fails on any `<Link>` built with `buildPublicLibrarySubjectPath` (directly or through an alias variable), a subject-listing URL template or a literal subject path, without `prefetch={false}`; its scanner has its own tests, and removing each of the three props was confirmed to fail it. **It is a safeguard, not proof:** a `router.prefetch()` call or an href assembled another way escapes it. The watch for a leak is `CATALOG_CACHE_STATS` (subject-class misses without matching visitors).

## 3. Prefetch results

| Run | Result |
|---|---|
| **Local production server, real Chrome (DevTools protocol)** | B1: 0 walk starts in 6 s of viewport time with the five real links (subject links produced 0 prefetch requests, hub links 2 each, all under 5 s). B7a: the real note page's subject links start no walk and no prefetch. B8: the real exam hub's 15 subject chips start 0 subject walks and 0 prefetch requests (its own load is 1 program walk). **B1c control** (a subject link with the DEFAULT prefetch) started 1 walk: the framework behavior, kept as an upgrade tripwire. |
| **Vercel Preview, final code** (private Preview, in-process fake catalog, 2026-11-04T10:58:06Z to 11:04:57Z) | **13 of 13 scenarios pass.** The five real links: 0 walk starts in the viewport window. The real note page's two subject links and the real exam hub's 14 subject chips: 0 prefetch requests, 0 subject walks. B1c control started 1 walk from the viewport on Vercel too, confirming the tripwire there. |
| **Walk starts read from the Preview's runtime logs** | **11 in the whole run, each at the click that caused it, none from a viewport or a prefetch.** |

## 4. Navigation results

All real clicks, real Chrome.

| Scenario | Local | Vercel Preview |
|---|---|---|
| B2 fast subject click | content in 408 ms, client-side, 1 walk | passed |
| B3 slow subject click | skeleton in 102 ms, 1 walk, failure state at the deadline | skeleton and site navigation in 411 ms; failure state at 90.5 s (the deadline) |
| B4a fast hub about 46 s after prefetch | complete content | complete content |
| B4b slow hub click | skeleton in 14 ms, 1 walk, failure state at the deadline | skeleton in 108 ms; failure state at 90.4 s |
| B5a direct URL, then refresh | complete content | complete; 1 walk for the URL, none for the refresh (cache hit) |
| B5b navigation after cache expiry (123 s wait) | complete content, exactly 1 walk | complete content, 1 walk |
| B6 failed load, also inside the 15 s backoff | existing failure state | existing failure state; the two clicks ran on two different Vercel instances (the backoff is per instance), each failed closed |

**The skeleton never lasts indefinitely:** the slow walks ended in the existing failure state at the 90 s deadline. The skeleton shows that the page is responding; it does not reduce the wait. **B2, B4b, B5a and B5b had failed locally on my harness bugs** (a case-sensitive check against a label the page uppercases with CSS; a log-read race). They passed only after the harness was fixed and the scenarios re-run, locally and on the Preview; I am saying so rather than letting 13 of 13 stand alone.

## 5. Duplicate-walk results

**One walk per cold navigation: metadata and page share the in-flight load** (single-flight), locally and on the Preview. On the Preview, `verify-nohasmore` shows 2 starts for B6's two clicks, which is two different instances (per-instance backoff), not a duplicate within one. Note the statistic caveat: a cold subject request is 1 miss + 1 join in `CATALOG_CACHE_STATS`, which is correct, not a duplicate walk.

## 6. Test results

| Check | Result |
|---|---|
| `tsc --noEmit` on the B4 branch | no errors printed |
| Lint | 0 errors, 20 warnings |
| Jest, full suite, B4 head `7d620428`, run 2026-11-04 | **237 suites; 2,884 passed, 1 skipped, 2,885 total.** (The skipped test is not one B4 added or skipped; I have not traced its origin in this pass.) |
| Mutation testing of B4 | the window-start-reset survivor was killed by a test added for it; one equivalent mutant (an in-flight identity check) is documented; each of the three `prefetch={false}` props was removed and the static guard failed |
| Real-browser suite, local | all scenarios pass after the harness fixes (section 4) |
| Real-browser suite, Vercel Preview | **13 of 13** |
| T-series (T0 to T21) on the Preview of the final code (2026-11-04T11:05:47Z to 11:54:50Z, exit 0) | **ALL PASS, 25 checks** |

The Preview runs use a throw-away verification branch (`verify/v0179-cache`, never merged) with an in-process fake catalog; the real API was never contacted (`blockedApiRequests=[]`).

## 7. Privacy, SEO and stability regression

**Privacy (T-series, Preview):** withdrawn or edited-out content is gone within the page bound on the subject page, exam hub, study-plan rail and note-page rails; the withdrawn slug leaves the sitemap within its (shortened) bound; the first request after an idle period spanning the withdrawal shows no withdrawn content; 20 parallel requests after expiry see no expired data and cause at most one rebuild per instance; there is no CDN or page-cache layer (no `s-maxage`, no `stale-while-revalidate`, `x-vercel-cache` never HIT or STALE). After the TTL, an upstream failure shows no expired data on pages (the sitemap returns a 5xx; the note page renders with empty rails) and recovers with fresh data inside the backoff bound.

**Strict walks / stability:** no `hasMore`, an empty page still saying `hasMore=true`, an endless catalog and a slow 20-page walk all fail closed; a valid empty catalog and junk slugs render the existing empty page with HTTP 200; the sitemap is complete with every URL on the `www` host. 22 distinct instances served the run.

**SEO:** `generateMetadata` is unchanged. A slow subject streams the skeleton and site chrome within seconds with no catalog data in the early bytes (T18); a prefetch-style request returns fast and starts no walk (T19); the note page has no skeleton marker (T20); **Facebook and Twitter user agents get complete HTML with the content present and the title in the head (T21)**. The raw HTML for those agents also contains the text-free skeleton fallback markup, which React's streaming includes by design (informational, not a defect). **Not tested: a real Googlebot render of a Preview**, and the real production catalog.

**Not read from the logs in the last pass, so not claimed:** which of the 400-page guard or the deadline stopped the endless catalog in T12, and that T14's empty walks were not cached. Both passed on their response checks; the earlier B3 Preview run (22 of 22) read both log lines.

## 8. Remaining risks (none new is hidden; the first four need a decision or a read)

1. **Platform function limit vs the 240 s sitemap deadline.** The sitemap deadline is provisional; the Hobby function cap is believed to be about 300 s but is **unverified**. The owner has not yet read Vercel Settings, Functions. The 240 s deadline cannot be tested on a Preview (the harness shortens the sitemap TTL, which clamps it).
2. **Stage A's 72 h read** (due 2026-11-06T10:56Z) still needs a real walk-shaped burst; so far there has been **one** (54 listing requests in a minute at 12:50Z on 2026-11-03). Stage A passed its 24 h read on 2026-11-04T10:56:31Z (zero kills, no 5xx). Database memory drifted to 256.4 MB (95.5% of the limit) in ordinary traffic: a trend, not a criterion.
3. **Your acknowledgement is still owed** for the D3 constraints, the 240 s sitemap deadline, the 15 s failure backoff, de-dup by id with the unpublished-mid-walk skip limit, the cold-wait behavior (a cold subject or hub load can wait up to 90 s, then show the error shell), the 1-hour Hobby log retention, and the rollback and `main` freeze plan (any `main` merge rebuilds Vercel production; Hobby can only roll back to the previous production deployment).
4. **Per-instance cache dilution.** The Preview showed 19 to 22 instances. Each instance has its own 120 s cache and its own failure backoff, so rebuilds are more frequent than the old shared cache; the load is unmeasured on real traffic until the +1 h, +24 h and +72 h reads after a deploy.
5. **Note-link prefetch fan-out** (observation, unchanged by B4): one exam hub view produced 24 (Preview) to 26 (local) prefetch requests to note pages in 6 s; each runs a note page's metadata, which is one single-note backend fetch, not a walk. Outside B4 scope unless it proves material in the production counts.
6. **Static-guard limits** (`router.prefetch()` or an unusual href escape it) and **Next.js upgrades** (re-verify in a real browser on every upgrade).
7. **L1:** failed loads still render the HTTP 200 error shell; unknown subject URLs still return the empty 200 page. A real 404 would reopen L1.
8. **Creator and profile pages stay on the 300 s data cache** (D4, a recorded privacy defect, deferred). Do not claim every public listing meets the privacy bound.
9. **Environment overrides:** a `NOTELIB_CATALOG_*` override below the walk duration would fail every cold load; production must be confirmed to have none before any deploy.
10. The `main` merges for the stacked PRs (#1550, #1552, #1553, #1556, #1558, then the final PR) need the owner; the ruleset's unattributed-changes approval has blocked a plain merge before. Claude does not use `--admin`.

## 9. Release recommendation

- **B4 is ready for the owner's review and for merging into the stacked Stage B branch** (PR #1558): the prefetch finding is fixed and verified in a real browser locally and on a Vercel Preview, the skeleton behaves as approved, and the full T-series passes on the final code.
- **This is NOT a recommendation to deploy Stage B.** Per the deploy plan (`docs/claude-plans/2026-11-04-v0179-stage-b-deploy-plan.md`), still open before any deploy recommendation: Stage A's 72 h read with a real burst, the owner's Functions-limit read, and your acknowledgement (risk 3). Nothing is merged to `main` for Stage B and nothing is deployed.
- **Do not describe Stage B as production-ready.** A deploy needs the owner's separate explicit approval and the plan's checklist (verified backup, off-peak, watch and stop conditions, no deploy within 24 hours of a non-deploy kill).

## How to answer

For each of risks 1 to 10 in section 8: "accept", "change to X" or "reject", with one line of why; and confirm or amend the recommendation in section 9. If you dispute a stated fact, name it; the scenario logs, the walk-start log reads and the link scan can be re-read with you.
