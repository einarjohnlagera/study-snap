# Product UX decision needed: the approved prefetch guard cannot work in Next.js 16.1.6 (v0.179.0 B4)

Written 2026-11-04 by the Release Implementor session. Paste this whole file to Product UX. It is self-contained and answers your instruction "if the guard cannot reliably distinguish prefetch from actual navigation, stop and report the result rather than accepting additional catalog load." **That is what happened. Nothing was committed, pushed or deployed.**

## 1. What you approved, and what happened

You approved Option 1: a server-side guard that reads the `Next-Router-Prefetch` request header and, on a prefetch, answers with the loading skeleton without touching the catalog walker, in both the page and `generateMetadata`. You asked for it to be verified with a real browser's prefetch-then-click flow, not only with a synthetic probe.

I implemented it (helper, two pages, unit tests including upgrade tripwires), then built the real-browser verification: real Chrome driven over its debugging protocol against a local production server backed by the in-process fake catalog, with a harness page of real `next/link` anchors (fast subject, slow subject, failing subject, fast hub, slow hub). The browser run failed, and the diagnosis below shows the guard is **infeasible as specified**, so I stopped.

## 2. Evidence

**A. A real browser's prefetch still started a walk, with the guard in place.**
- Each link gets two prefetch requests: a cheap route-tree request (`Next-Router-Segment-Prefetch: /_tree`, about 20 ms) and a second request that carries the router state tree and renders down to the loading boundary.
- For the slow subject, that second request ran **12 s** (the deadline at test scale), and the server log shows one walk start for that subject. For the slow exam hub the same request took 21 ms and started **no walk**.

**B. Next.js does not expose the prefetch header to server code at all.** I built a diagnostic route with the same shape as the real one (dynamic param, `loading.tsx`, `force-dynamic`) that logs what `headers()` returns:
- A plain request logged 7 headers, with `next-router-prefetch` and `rsc` absent, as expected.
- A request sent with `RSC: 1` and `Next-Router-Prefetch: 1` also logged **7 headers, with both absent**: the router headers are not visible to `headers()`.
- A real browser prefetch logged 17 headers (browser headers) with the router headers **absent**.
- So `isRouterPrefetch()` returns false on every request, in `generateMetadata` and in the page. No variant of "read the prefetch header" can work in this Next version; the header is not available to user code. (My idea of treating an empty header set as a prefetch signal also fails: host and the rest are present.)

**C. What actually runs during a prefetch of a loading-bounded route.** The diagnostic route logged `generateMetadata` for the real browser prefetch and **never logged the page body**. So:
- **The subject listing's walk comes from its `generateMetadata`**, which walks the catalog to build the title and the `noindex` flag.
- **The exam hub is already safe**: its `generateMetadata` uses static config only, and its prefetch started no walk in the browser run (0 walk starts over 6 s of viewport time).
- Before B4 there was no `loading.tsx`, so a prefetch of these dynamic routes rendered nothing (the B3 Preview baseline: 251 bytes, no walk). `loading.tsx` is what makes Next run the route's metadata on prefetch.

**D. What worked in the same browser run (B3 and B4 scenarios).** On a real click, the skeleton and the site navigation appeared in about 100 ms on both a slow subject and a slow hub; the click (not the prefetch) started the walk; the page ended in the existing failure state at the deadline and did not stay a skeleton; a fast hub clicked about 46 s after its prefetch rendered complete content; navigation after a failed load ended in the failure state, also inside the backoff. Client-side navigation preserved page state. **Not yet established:** three scenarios (B2 fast subject click, B5a direct URL and refresh, B5b navigation after cache expiry) failed because my own text check was case-sensitive and missed the page's uppercase "SUBJECT LIBRARY" label (a test bug, now fixed); they have not been re-run, so I am not claiming they pass.

## 3. Consequence for your earlier decisions

- **Decision 1 (Option 1, server-side prefetch guard): not implementable** as specified.
- **Decision 2 (rely on `Next-Router-Prefetch`): not feasible**; the header is not available to server code.
- **Decision 3 (scope) and Decision 4 (thresholds, gates, freeze, rollback): unchanged and still binding.**
- The B4 commit as it stands (`6ff02f2a`, unpushed) still has the problem for subject links: a visible link to a subject listing starts a catalog walk on prefetch. **It must not be pushed or deployed as it is.** The exam hub part of B4 is safe as built.

## 4. Options

**Option C: move the subject listing's skeleton into the page (recommended).** No `loading.tsx` on the subject listing. The page returns immediately with a Suspense boundary whose fallback is the same text-free skeleton, and the walk moves into an inner component inside that boundary. The exam hub keeps its `loading.tsx` (verified safe).
- *Why it fits:* without a `loading.tsx`, a prefetch of the route is inert again (the B3 baseline: no metadata, no page, no walk); a real navigation shows the skeleton as soon as the shell streams, as in the verified B3 and B4 scenarios.
- *Cost:* a medium change to one page (the data-dependent body moves into an async child; the page tests call that child directly), the `(listing)` route group becomes unnecessary, and one more Preview verification run (about 1 hour plus the browser run).
- *Limits:* real navigation is unchanged: `generateMetadata` still walks (browsers get streamed metadata; Facebook and Twitter style bots still wait for it, as today); the skeleton still covers the whole content area (heading-first is not possible, the label and `noindex` depend on the walk); and the claim "prefetch is inert again" must be re-proved with the same real-browser run, not assumed.

**Option D: `prefetch={false}` on every link to the subject listing.** About 6 to 8 link sites, including shared components; easy to miss one; protects nothing against a future link; verification would be mostly a static scan plus the browser run.

**Option B: drop the subject listing's `loading.tsx` and its skeleton.** Prefetch is inert; the content area stays empty on a cold subject request, as it is today. The hub keeps its skeleton.

**Option A: make the subject metadata cheaper (one small query instead of a walk).** Not recommended: it adds a backend query per prefetched link, which you ruled out ("no additional backend requests merely because a link enters the viewport").

## 5. What I am asking

1. **Pick an option** (C, D, B or A), with a reason if it is not C.
2. **Accept that the header-based helper and its tests are discarded** (saved as a patch in the working notes, not committed).
3. **Confirm the verification gate for the chosen option:** the real-browser prefetch-then-click run (B1 to B6 plus the hub scenarios), with prefetch showing no walk start in the logs and real navigation rendering complete content, run on the Vercel Preview. If Preview prefetching cannot be reproduced reliably, I will report that and propose the smallest alternative.
4. **Confirm nothing else changes:** the 120 s hard TTL, the 90 s walk deadline, the 15 s failure backoff, single-flight, the sitemap rules, the `w10p` observation threshold, every deploy-plan hard stop, the Stage A gates, the `main` freeze and the rollback plan.

## 6. Schedule

Stage B cannot deploy before the Stage A 72 h read on 2026-11-06 regardless. Option C costs a Codex or direct change, my audit and mutants, and the Preview run. No merge, deployment, backend change or infrastructure change is authorized by this document, and production deployment still needs the owner's separate explicit approval.

## 7. How to answer

For each of 1 to 4: the option letter or "accept"/"change to X", with one line of why. If you dispute a stated fact, name it; the diagnostic route, the real-browser run and the server logs can be re-read with you.
