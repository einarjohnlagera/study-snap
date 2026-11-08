# Creator and profile page freshness: plan (DRAFT, 2026-11-04, committed 2026-11-06; plan only, nothing built)

Decision recorded 2026-11-05 (owner): Option B, the 120 s hard-TTL single-flight cache; order Stage B, then this fix, then the price release, each at least 24 hours apart. Backlog row: `ROADMAP.md`, the creator and profile privacy defect row.

Defect row: `ROADMAP.md` "CONFIRMED PRIVACY DEFECT: creator and profile pages list a creator's note titles through the 300 s data cache" (cold review F4, Product UX D4: deferred out of `v0.179.0`, prioritized after Stage B stabilizes). Everything below was read on `origin/releases/v0.179.0-stage-b` (identical for these files to `main`) on 2026-11-04 unless marked unverified.

## 1. What is actually exposed (wider than the row says)
- **Pages:** `app/public/creator/[username]/page.tsx` and `app/public/profile/[userId]/page.tsx`. Both call `lib/server-public-profiles.ts` from `generateMetadata` AND the page body; the three fetches are `next: { revalidate: 300 }` at `:20`, `:45`, `:53` (profile and learning-focus, for both lookups).
- **Payload** (`lib/api.ts:2389`, `PublicProfileResponse`): display name, username, **bio**, learner level, course program, profile type, `publicProfileVisible`, counts, notes by subject, and `publicNotes[]` with **title, course program, subject, tags, `contentPreview`, `summaryPreview`**, counts and slug. So a withdrawn note leaks its title **and a content and summary preview**, an edited bio stays, and **a profile switched to private keeps showing for at least 300 s** (the server-rendered page decides private versus ok from the cached 403/200). The `<title>` and description (name, note and copy counts) come from the same cache.
- **Owners do not see it:** the client component refetches fresh data only when the viewer is the owner (`components/public/public-profile-page-client.tsx:151-187`), so a creator who withdraws a note and checks their own page sees the right thing while everyone else still gets the stale one.
- **The bound today:** no hard bound. Next's data cache serves stale-while-revalidate, so the first request after 300 s is still stale; with low traffic the stale copy can be served long after 300 s. How long withdrawn data was actually served is not measured.
- **Which layer:** the routes are not in the local production build's prerender manifest, so the page renders per request and the fetch data cache is the layer (a local build only; not tested on Vercel for these routes).
- **Search engines:** both pages are `robots: noindex` already, so this is not an indexing leak; it is a freshness-of-a-public-page defect (link previews, other crawlers, anyone opening the URL).
- **Other 300 s fetches on public surfaces, checked and left alone:** subject slug list for `generateStaticParams` (`server-public-notes.ts:290`), the public note count (`:77`), and the exam-goal program catalog (`server-exam-goal-course-programs.ts:49`): from their headers none carries user-authored note text. I read the surrounding comments, not every caller.
- **Not re-read:** the backend controller bodies (`PublicProfileController.java:22-62`); the backend sets no `Cache-Control` header (grep).

## 2. Load input: tiny, but prefetch changes the answer
- **Human traffic is very low:** Vercel Web Analytics for 2026-10-05 to 2026-11-04 shows about 11 pageviews in total on creator and profile routes (JS browsers only; non-JS crawlers and link-preview bots are invisible there, and the Render logs cannot separate these paths, so bot traffic is unmeasured). Each view is 2 backend calls (profile and learning focus); `generateMetadata` and the page ask for the same data.
- **But the links are not rare, and the Stage B prefetch finding applies:** a real browser's link prefetch runs the destination's `generateMetadata`. Links to creator and profile pages: `components/notes/public-library-page-client.tsx:386` (an author link on each library card; the library index is the busiest page, 3,900 visitors), `public-note-author-card.tsx:67`, `public-note-ownership-actions.tsx:81`, `app-shell.tsx:666`, `app/profile/page.tsx:916`. Today the 300 s cache absorbs those prefetches. **An uncached fix would turn every visible author link into backend calls from the viewport, exactly the speculative load Stage B just removed.**

## 3. Options
| Option | Hard bound | Load | Cost and risk |
|---|---|---|---|
| **A. `cache: "no-store"` on the fetches** (the note page's pattern) | 0 s at the data layer | every request and every prefetch hits the backend (2 calls) | needs `prefetch={false}` on the public author links plus the static guard extended; a missed link or `router.prefetch()` becomes unbounded backend load on a database that dies under bursts |
| **B. Reuse `getCatalogCached` (Stage B's hard-TTL, single-flight, fail-closed cache) at 120 s** (**recommended**) | 120 s hard, never serves expired data, no stale-while-revalidate | absorbs prefetch and bots per instance, like today's 300 s but bounded | one small module change plus tests; per-instance (not shared) caches; same 120 s the other surfaces accepted; reuses code that already passed two Preview verifications |
| C. On-demand revalidation when an owner changes something | tight | low | needs a backend-to-frontend trigger: new infrastructure, **rejected by the anti-drift list** |

**Why B over A:** the database is the scarce resource (basic_256mb, kills after bursts) and B keeps its protection against prefetch and bot fan-out, while the privacy bound becomes a hard 120 s that matches every other public surface. A is stricter (0 s) but only safe if every link is covered, and Stage B just showed that guarantee is pattern-based.

## 4. Fix shape (Option B), small and frontend only
- `lib/server-public-profiles.ts`: wrap the profile and focus load for each lookup in `getCatalogCached` (key class per lookup type, 120 s TTL, deadline about 15 s per request, uncached upstream fetches with `cache: "no-store"` and the request signal). **`shouldCache` rules:** cache `ok` and `private` (both are real answers whose freshness the TTL bounds); **do not cache `not_found`** so a flood of made-up usernames cannot evict real entries (the Stage B rule for empty subject slugs). A failed or malformed load rejects and keeps the existing error handling; no expired data; the existing 15 s failure backoff applies.
- One shared load per lookup so `generateMetadata` and the page body join the same in-flight load (verify in the Preview with the stub's call counts, do not assume: B4 taught that framework behaviour has to be measured).
- No change to the pages' markup, to the owner refetch path, or to the backend.
- The privacy claim after the fix: creator and profile pages are bounded at 120 s on the server; the owner view is already fresh.

## 5. Verification (decided now)
- **Unit and guard tests** that execute the new path: expiry at 120 s, no stale-while-revalidate, failure rejects with no expired data, `not_found` not cached, `private` and `ok` cached, join of metadata and body, key cap behaviour. Mutants against each (serve expired, cache `not_found`, per-call fetch instead of shared load).
- **Vercel Preview with the existing harness** (stub API extended with creator and profile endpoints): a withdrawn note's title, content preview and summary gone within 120 s; a profile switched to private shows the private page within 120 s; an edited bio refreshes; upstream failure after the TTL shows no expired data; real-browser check that the library listing's author links cause no more backend calls than one per author per instance per 120 s and that metadata and page share one load; call counts read from the stub.
- **Regression:** the T-series (T0 to T21) re-run is not needed for untouched code; run the subset the shared module affects.
- **Release shape:** one frontend PR, about 3 source files plus tests and `docs/features/public-profile` docs (the feature doc name to be confirmed), no backend, no version file except the release's own bump at kickoff. Small enough for a single `advisor()` call plus the Preview run, no heavy pre-signoff test.

## 6. Decisions needed (owner and Product UX)
1. **Option B (recommended) or A.**
2. **120 s** as the creator and profile bound (the same as every other surface) or tighter.
3. **Accept the wider scope in the defect row:** it covers bio, content and summary previews, tags and the profile's own private toggle, not only titles (I would update the row's wording).
4. **Which release and when:** after Stage B's 72 h watch; before or after the price release. Both are frontend-only; each deploy rebuilds Vercel production and starts its own 24-hour `main` freeze, so they should not overlap.
5. Whether to also **measure the real exposure** first (how long a withdrawn note was actually served). It cannot be reconstructed from the 1-hour Vercel logs and is not worth waiting for.

## 7. Not done in this pass
No code written; the backend controller bodies not re-read; Vercel behaviour for these routes not tested; bot traffic unmeasured; the profile-related feature doc not read.

## 8. Decisions recorded (owner, 2026-11-04)
- **Option B at 120 s accepted** (hard-TTL single-flight cache via `getCatalogCached`, fail-closed, no stale-while-revalidate). Q1 and Q2 of section 6 are answered.
- **Still open, with my default:** Q3 (update the defect row's wording to name bio, content and summary previews, tags and the profile's private toggle): I will put that wording change into Friday's docs PR unless told otherwise. Q4 (order against the price release): recommendation below.
- **Sequence recommendation:** Stage B (deploy, 72 h watch, sign-off) then the creator and profile fix, then the price release, each at least 24 hours apart. Reasons: the fix is a confirmed privacy defect with no env step and no Render redeploy; the price release needs an owner env save that redeploys Render and carries the kill-after-deploy risk, so it should have a quiet window to itself. Order is the owner's call.
- **Nothing built.** Next steps after `v0.179.0` is signed off: kickoff, Codex prompt (about 3 source files plus tests and docs, frontend only), extend the Preview harness with creator and profile stub endpoints, audit and mutants, Preview run, owner approval.

## 9. Confirmed (owner, 2026-11-04): order accepted (Stage B, then this fix, then the price release, at least 24 hours apart); defect-row wording change goes into Friday's 72 h docs PR.
