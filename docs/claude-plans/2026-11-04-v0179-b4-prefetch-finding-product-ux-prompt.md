# Product UX second opinion: B4's loading skeleton makes link prefetching start catalog walks (v0.179.0)

Written 2026-11-04 by the Release Implementor session. Paste this whole file to Product UX. It is self-contained and follows your "cold-request latency and progressive loading" follow-up. **This document is the audit result you were waiting on.** Nothing in it is implemented beyond what is stated, and nothing is pushed or deployed.

## 1. Where this came from

You and the owner approved "B-lite": a route-level, text-free loading skeleton for the subject listing and the exam hub, plus a cache statistics counter, with the explicit rule that B-lite changes **nothing about what a page awaits**. B4 was built to that scope (commit `6ff02f2a`, unpushed) and passed its own tests. An independent advisor review before the build flagged one risk to be tested before shipping: in the Next.js App Router, adding a `loading.tsx` to a dynamic route can change what link prefetching does. The Preview harness was extended to test it, and a local dry run (one server instance, one-tenth time scale) found the problem.

## 2. The finding, with evidence

**Adding the skeleton makes a link prefetch render the page and start its catalog walk.**

- A prefetch-style request is what a `<Link>` sends (`RSC: 1` and `Next-Router-Prefetch: 1`). Against a deliberately slow test subject (a catalog that takes about 120 s to walk):
  - **Before B4** (the B3 Preview on Vercel, no `loading.tsx`): the request returned in **1.2 s with a 251-byte payload and no walk**.
  - **With B4** (local dry run): the request **did not return within 12 s**.
- The server log of that run shows three slow walks reaching their deadline: the ordinary request, the aborted skeleton probe, **and the prefetch-style request**. So the prefetch started a full walk and waited for it.
- Not yet reproduced on a Vercel Preview of B4, because B4 is not pushed. The local server runs the same Next.js code (16.1.6), so I expect the same on Vercel, but that is unconfirmed until the Preview run.

**Why it matters.** No `<Link>` anywhere in the app sets `prefetch`, so all use Next's default, which in production prefetches links as they enter the viewport. Links to the affected routes include: the `/exam` index (one link per exam hub), the exam hub pages (links to subject listings), and public note pages (links to their subject listing and exam hub). Every real browser that views one of those pages could start a walk for a page the visitor never opens. Each walk is cached per instance for 120 s, so the load is bounded by (distinct linked pages) x (instances) per 120 s, driven by real traffic (prefetch runs in the browser, so crawlers that do not run scripts do not trigger it). It is not catastrophic, but it is **new database load, unmeasured, and it contradicts the approved rule "no increase in database load without explicit justification"** and the whole purpose of Stage A and B. A `courseProgram` hub walk runs at about 1.3 to 1.6 s per page in production.

**What the same dry run confirmed works:** the skeleton and the site navigation arrive immediately on a slow page with no catalog data in the early bytes; the note route carries no skeleton (the route group works); the `CATALOG_CACHE_STATS` line is emitted correctly (hit, miss, join, backoff, wait buckets, no key or slug); every earlier check still passes. One of my own assertions was wrong and is corrected: the raw HTML for limited bots (Facebook and Twitter user agents) is complete (content present, title in the head) but also contains the text-free skeleton fallback markup, which React's streaming includes by design; that is informational, not a defect.

## 3. Options

**Option 1: a server-side prefetch guard (my recommendation).** On a prefetch request, the subject listing's metadata and body and the exam hub's body return the skeleton and never call the catalog walker. Prefetch stays exactly as inert as it is today (parity with the pre-B4 behavior, which was 251 bytes and no walk). A real navigation is unchanged and still shows the skeleton immediately.
- *Strengths:* centralized (a new link added later cannot bypass it); verified directly by the same probe that found the problem; B-lite's visible behavior is unchanged.
- *Costs and limits:* small code (a helper and about four call sites, plus a `next/headers` mock in the page tests) and a Preview re-run of about 1 hour; it relies on Next sending `Next-Router-Prefetch` on prefetches (I believe it does in 16.1.6, proven here only by the probe); a future Next change or a different prefetch mode could bypass it. Mitigation: the deploy plan's watch reads `CATALOG_CACHE_STATS`, where a leak would show as walk misses without matching visitors. It changes what the pages do on prefetch requests only, which is a small widening of "B-lite changes nothing about what a page awaits".

**Option 2: set `prefetch={false}` on every link to those routes.** Clicking a link still works and still shows the skeleton immediately.
- *Strengths:* uses a documented link-level control; no server logic.
- *Costs and limits:* about 8 link sites to find and edit (including shared components and the public library index), easy to miss one, protects nothing against a future link; the server-side probe would still fail, so verification would be static only (a test that scans for links) rather than a direct check.

**Option 3: drop `loading.tsx` (back to your Option A).** Keep the counter. The content area stays empty on cold requests, as it is today. Prefetch stays inert. No new scope.

**Option 4: keep B4 as built and accept prefetch walks deliberately.** One argument for it: prefetch warms the cache, so a visitor's click is instant. Not recommended: the extra load is driven by link visibility, not by intent to visit, it is unmeasured, and it needs an explicit load justification you have not given.

## 4. What I am asking

1. **Pick an option** (1 to 4), with a reason if it is not Option 1.
2. If Option 1: **accept reliance on the `Next-Router-Prefetch` header** and the Preview probe plus the `CATALOG_CACHE_STATS` watch as the guard.
3. **Confirm the scope reading:** the guard changes only prefetch-request behavior and keeps B-lite's rule for real navigations.
4. Any change to the success reading: the observation threshold (a wait of 10 s or more, the `w10p` bucket) and every deploy-plan hard stop stay unchanged unless you separately approve a change.

## 5. Schedule and what stays unchanged

Stage B cannot deploy before the Stage A 72 h read on 2026-11-06 regardless, so Option 1 costs no stabilization time: a small Codex change, my audit and mutants, and a Preview re-run of about 1 hour. All deploy gates, hard stops, the rollback plan, the `main` freeze, and your earlier decisions D1 to D5 are unchanged. No merge, deployment, backend change or infrastructure change is authorized by this document. Production deployment still needs the owner's separate explicit approval.

## 6. How to answer

For each of 1 to 4: the option letter or "accept"/"change to X", with one line of why. If you dispute a stated fact, name it; the probe, the three server-log lines and the call-site list can be re-read with you.
