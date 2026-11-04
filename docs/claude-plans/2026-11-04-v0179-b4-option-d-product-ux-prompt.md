# Product UX decision needed: link prefetch starts catalog walks under any structure; Option D (`prefetch={false}`) evaluated (v0.179.0)

Written 2026-11-04 by the Release Implementor session. Paste this whole file to Product UX. It is self-contained and answers your stopping rule: "If Option C still causes catalog walks during link prefetching ... stop and report the evidence ... First: evaluate Option D only if the remaining issue is clearly limited to link prefetching and the change is small and verifiable." **Option C tripped that rule, I stopped, and this is the Option D evaluation. Nothing is implemented for D, and nothing is pushed or deployed. All evidence below is from a local production server driven by real Chrome; there is no Vercel Preview evidence yet.**

## 1. The finding changes the question

**A real browser's link prefetch starts a catalog walk for the subject listing whether or not the route has a `loading.tsx`, and whether or not its skeleton is in a Suspense boundary.** So the problem is not B4's, it is not fixed by Option C, and **it is not fixed by Option B either**; it is present in Stage B as built since B1 (a force-dynamic page whose `generateMetadata` walks the catalog).

- **Control experiment.** A plain dynamic route with no `loading.tsx`: a real browser prefetch ran its `generateMetadata` (and not its page body). The diagnostic also showed Next.js hides the router's prefetch header from `headers()`, which is why the header guard could not work.
- **Option C build, real-browser run.** Three subject links on a test page produced **3 walk starts** during 6 s of viewport time (one per link); the two exam-hub links produced none (their metadata is static).
- **Why it matters.** The most-visited page is a public note page, which links to its subject listing ("More from {subject}", `app/public/library/[subject]/[slug]/page.tsx:421`). Each note view could prefetch that listing and start its walk; for `general` that is the whole-catalog walk. Walks are cached for 120 s per instance, so the load is bounded by (distinct visible subject links) x (instances) per 120 s and driven by real browsers, but it is **new, unmeasured, and exactly the load your rule says not to add** ("no additional backend requests merely because a link enters the viewport").
- Production today is still ISR-based; I have not tested it and make no claim about it.

## 2. Option D evaluation: `prefetch={false}` on links to the subject listing

**It works, in a real browser.**
- A `<Link prefetch={false}>` to a subject started **0 walks** while in the viewport, started **exactly 1** walk when clicked, navigated client-side, and showed content in 416 ms.
- A control probe route linked with `prefetch={false}` never had its metadata run, while the same route with the default prefetch did.

**It is small.** Links built with `buildPublicLibrarySubjectPath` are rendered as `<Link>` in exactly **two** places: the note page (line 421) and the exam hub page (line 159). I looked in the library index client, the profile page, the published-plans client, the app shell and the help pages: none links to a subject listing through that builder. The exam hubs themselves need nothing (static metadata, no walk observed).

**It can be verified.** The real-browser suite already measures walk starts per navigation; after D it must show 0 walks in the viewport window and exactly 1 per genuine click.

**What D does not cover (honest limits).**
- A *future* link to a subject listing, a `router.prefetch()` call, or a link built another way is not protected by the two edits. Mitigation I propose: a static test that fails on any `<Link>` whose href is built with `buildPublicLibrarySubjectPath` (or a `/public/library/${` template) without `prefetch={false}`, plus the production watch: `CATALOG_CACHE_STATS` shows walk misses, and a rise in `walk:page:subject` misses without matching visitors would reveal a leak.
- The test is pattern-based: it cannot see a link whose href is assembled some other way.
- Disabling prefetch removes the click-speed benefit of prefetching these links. The skeleton still appears within about 100 ms of a click (measured), and subject listing data was never prefetchable anyway.
- **Side observation, not a blocker:** prefetching *note* links runs the note page's metadata, which makes one single-note backend fetch per visible link. That is a cheap single request, not a walk, and it exists today.

## 3. Results of the local run, by scenario (Option C build; local server only)

| Scenario | Result |
|---|---|
| B1 viewport prefetch starts no walk | **FAIL**: 3 walks (the finding) |
| B2 fast subject click | **FAIL on my own check only**: content in 408 ms, client-side; it expected exactly 1 walk and saw 0 because the prefetch had already filled the cache |
| B3 slow subject click | **PASS**: skeleton in 108 ms, exactly 1 walk started by the click (metadata and page share one in-flight load), failure state at the deadline |
| B4a fast hub about 46 s after prefetch | **PASS** |
| B4b slow hub click | **Not counted**: skeleton in 11 ms and failure state OK, but my walk counter read the log before the walk logged; fixed with a short wait, **not re-run** |
| B5a direct URL and refresh | **PASS** (closes the earlier case-sensitivity false negative) |
| B5b navigation after cache expiry | **PASS**, exactly 1 walk |
| B6 failed load, also inside the backoff | **PASS** |

## 4. What I am proposing

**Adopt Option D, and treat it as required for Stage B regardless of how the skeleton is done**, because Option B alone leaves the prefetch walks in place.

- Add `prefetch={false}` to the two links; add the static guard test.
- **Skeleton choice (your call):** keep Option C's in-page Suspense skeleton for the subject listing (it works on navigation, measured), or return to a route `loading.tsx` (equally acceptable once the links are not prefetched, but a `loading.tsx` makes any stray prefetch run the metadata), or drop the subject skeleton (Option B plus D). I recommend keeping C's Suspense: with no `loading.tsx` there, a missed link is at least not made worse by the boundary.
- Re-run the full real-browser suite locally and then on the Vercel Preview, with B4b and B2 corrected, counting walk starts from the runtime logs; report local and Preview results separately.
- Update the B4 verification record with: the header guard rejected and why, the control experiment, the Option C result, the Option D evaluation, and the limits above.

## 5. Questions

1. **Approve Option D** (two `prefetch={false}` edits plus the static guard) as a required Stage B fix?
2. **Skeleton:** keep the in-page Suspense skeleton (recommended), return to `loading.tsx`, or drop it?
3. **Accept the residual risk** (a future or unusual link is only caught by the pattern test and the `CATALOG_CACHE_STATS` watch)?
4. **Accept that prefetching of these links is given up,** in exchange for no speculative walks?
5. Confirm the **verification gate**: real-browser run with 0 walks in the viewport window and exactly 1 per genuine navigation, local and Vercel Preview.

## 6. Schedule and what stays unchanged

Stage B cannot deploy before the Stage A 72 h read on 2026-11-06 regardless. D is a small change plus a Preview run (about 1 hour). The 120 s hard TTL, the 90 s deadline, the 15 s backoff, single-flight, the sitemap rules, the `w10p` observation threshold, every deploy-plan hard stop, the Stage A gates, the `main` freeze and the rollback plan are unchanged. **The deploy plan gains one gate: no Stage B deploy recommendation until the prefetch result above is verified on the Preview.** No merge, deployment, backend change or infrastructure change is authorized by this document; production deployment still needs the owner's separate explicit approval.

## 7. How to answer

For each of 1 to 5: "approve", "change to X" or "reject", with one line of why. If you dispute a fact, name it; the control run, the three walk-start logs and the link inventory can be re-read with you.
