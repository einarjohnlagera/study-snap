# HTTP-status Preview spike protocol: public collection page not-found (planning only)

**Status:** PROPOSAL, planning only; owner R1 FINAL 2026-11-11. **Run by:** the owner or the Release Implementor
on a **Vercel Preview**. Claude does not deploy, merge, create Previews or handle bypass tokens. The throwaway
branch is **never merged**, and merges to `main` stay held per the release rules (no merge before `v0.180.0`'s
+24 h read; CONFIRMED `CLAUDE.md:43`). Nothing here authorizes production traffic, a deploy or an env change.
**Parent plan:** `docs/claude-plans/2026-11-10-study-plan-discovery-and-community-collections-plan.md`, Phase 2b
"HTTP status and not-found strategy (pre-implementation spike)" and C1/C11. **Code read:** `origin/main` at
`d5eaf3837812c965f4f7ca03d9a8bd9948269ac6` (2026-11-11). Next `16.1.6`, React `19.2.3` (CONFIRMED
`frontend/package.json`).

Labels: **CONFIRMED** (file:line), **ASSUMPTION**, **PROPOSAL**.

## 1. Question and decision rule

**Question:** can `/explore/collections/[id]`, built to the plan's structural rules, return a genuine HTTP 404
from `notFound()` under the real root layout, for both a browser and a crawler user agent?

**Why it is in doubt (CONFIRMED):** `frontend/app/layout.tsx:88-96` wraps every route in
`<Suspense fallback={null}>`, which can commit a 200 before the page decides. `/exam/[slug]` measured 200 on the
`v0.179.0` Preview, but it has `frontend/app/exam/[slug]/loading.tsx`. `frontend/app/explore/` has no
`loading.tsx`; `frontend/app` has no `not-found.tsx` or `global-error.tsx`; `frontend/` has no `middleware.ts`
or `proxy.ts` (CONFIRMED `git ls-tree`).

**R1 decision rule (binding):**
| Spike outcome | Action |
|---|---|
| Genuine 404 for the real-structure variants under every tested UA | Use 404 in the real page; still `noindex, nofollow`, generic shell |
| Anything else | Accept **HTTP 200 + generic `noindex, nofollow` shell**, identical for never-existed and withdrawn; **no crawler-status claim** in copy or docs |
| In every case | **No middleware/`proxy.ts`, no change to `app/layout.tsx` or any global layout** to force a 404 |

## 2. What to build (throwaway, Preview-only; not production code)

A throwaway branch off current `main`, one route tree that mirrors the real structure. No backend call at all:
existence is a server-side stub keyed on the id, so the spike never touches production data.

**Shared rules (real-structure variants):** under `app/explore/collections/[id]/` (a sibling path such as
`app/explore/spike-collections/...` is fine if the real path is kept clean, PROPOSAL); **no `loading.tsx` in the
segment or any parent**; `export const dynamic = 'force-dynamic'`; the existence decision is awaited **before**
any JSX is returned; a not-found loader **returns `null`** and the page calls `notFound()`; any other failure
**throws**; `generateMetadata` awaits the same loader (not-found → `robots: { index: false, follow: false }`,
generic title).

| Var | Id / route | Behaviour | Matches real page? |
|---|---|---|---|
| Var | Path (`$P`) | Behaviour | Matches real page? |
|---|---|---|---|
| A | `/explore/collections/spike-unknown` | loader returns `null` → `notFound()` | **Yes** |
| B | `/explore/collections/spike-fail` | loader throws a deliberate server-side `Error` (no real backend, no bad input to production) | **Yes** |
| C | `/explore/collections/spike-ok` | loader returns a fixed object → renders title + heading | **Yes** (control) |
| D | `/explore/spike-with-loading/spike-unknown` (segment has a `loading.tsx`) | as A | Contrast only (the `/exam/[slug]` shape) |
| E | `/explore/spike-inner-suspense/spike-unknown` (`notFound()` inside a `<Suspense>` below the decision) | as A | Contrast only |
| F | annotation on A, **no separate requests** | A rendered under the unmodified root layout (`layout.tsx:88-96`) | **Yes** (names the suspected cause) |

The spike must keep the real root layout unmodified; F is recorded as that fact on A's rows. **Request cap:**
at most 40 in total; sequential, no loops, no concurrency, no load.

## 3. Measurement matrix and commands

Two UAs per variant (ASSUMPTION: Next 15.2+ streams metadata differently for bots, so a crawler may see a
different head or status): `UA_BROWSER` (a current desktop Chrome string) and `UA_BOT` (`Googlebot/2.1`).
The Preview protection bypass uses the owner's own mechanism; **never write a token into any doc, script or
committed history** (use a shell variable the operator sets locally, e.g. `$BYPASS_HEADER`).

```sh
# per variant path P (A-E) and user agent UA: 5 variants x 2 UAs x 3 calls = 30 requests
curl -s -o /dev/null -w '%{http_code}\n' -A "$UA" -H "$BYPASS_HEADER" "$PREVIEW$P"
curl -s -D - -o /dev/null -A "$UA" -H "$BYPASS_HEADER" "$PREVIEW$P" \
  | grep -i -E '^(HTTP|cache-control|content-type|x-vercel-)'
curl -s -A "$UA" -H "$BYPASS_HEADER" "$PREVIEW$P" \
  | grep -o -E '<title>[^<]*</title>|<meta name="robots"[^>]*>|<link rel="canonical"[^>]*>|<meta property="og:(title|url)"[^>]*>|<h1[^>]*>[^<]*'
# repeat check for Outcome Z: the status call for A only, once more per UA, after a pause = 2 requests
```
**Rendered DOM (head and body can disagree):** for A, B, C, load once in headless Chrome under the browser UA
(the owner's browser or the `verify/v0179-cache` branch's `frontend/preview-verify/run.mjs`,
`redirect:'manual'`, `cache:'no-store'`; ASSUMPTION it still runs) and record `document.title`, the robots meta
content and the visible `h1` = 3 requests. **Total 35 of the 40 cap**; the 5 spare are for one re-run of a
failed call, never for a loop.

## 4. Expected outcomes and decision tree

- **Outcome X:** A (under the unmodified layout, F) returns **404** under both UAs, on the first call and the repeat, with `noindex` and the generic title; B returns 500 (or
  a documented error status). → Record "404 feasible"; adopt for the real page.
- **Outcome Y:** A returns **200** with the `noindex, nofollow` generic shell under both UAs; B returns 200
  with an error shell. → **ACCEPT per R1(a):** 200 + generic `noindex, nofollow` shell, identical for
  never-existed and withdrawn, no crawler-status claim, no middleware, no layout change.
- **Outcome Z:** mixed (404 for one UA only, or the status flips with streaming / repeated calls). → **Treat as
  Y** unless 404 is consistent for every UA and every repeat. Never state the status in copy or docs.
- **Expected (ASSUMPTION):** Y, because of the root Suspense. D and E are expected 200 and only show whether the
  segment shape matters.

**Spike PASS/FAIL:** it **passes** by producing complete evidence in either direction (X, Y or Z). It **fails**
only if the matrix is incomplete (a variant × UA cell missing, no A repeat, no DOM read for A/B/C) or a safety rule in
section 6 was broken; then it is re-run, not interpreted.

## 5. Results template

| Variant | UA | Status | robots meta | `<title>` | canonical | og:title / og:url | DOM title / h1 | Notes |
|---|---|---|---|---|---|---|---|---|
| A | browser | | | | | | | |
| A | bot | | | | | | | |
| B | browser | | | | | | | |
| B | bot | | | | | | | |
| C | browser | | | | | | | |
| C | bot | | | | | | | |
| D | browser / bot | | | | | | — | contrast |
| E | browser / bot | | | | | | — | contrast |
| A repeat | browser / bot | | — | — | — | — | — | status only (Outcome Z) |
| F | — | — | — | — | — | — | — | annotation: root layout unmodified? yes/no |

Fields: Preview URL (**no token**): … · branch and commit SHA: … · Next / React versions: … · date/time (UTC):
… · operator: … · total requests: … (≤ 40) · outcome: X / Y / Z · decision applied (R1): … · cleanup done
(Preview deleted, branch deleted): yes/no · evidence files location (local only): …

## 6. Safety and cleanup
- Preview only; reads only; no production URL, no production writes, no artificial load, no loops.
- The stub makes no backend call. **Stop** if the Preview logs show any request to the production backend or any
  production error; record and report.
- Afterwards delete the Preview deployment and the throwaway branch; nothing from it is merged or cherry-picked.
- Record the result in the parent plan (Phase 2b spike section) and in the Backlog row text; keep raw evidence
  files locally, **not in git**.

## 7. Loader contract for the real page (dependency, not part of the spike)
- Not-found is **returned as `null`, never thrown, never cached:** pass `shouldCache: (v) => v !== null` to
  `getCatalogCached` (CONFIRMED `frontend/lib/catalog-cache.ts:242`). A thrown not-found would be recorded as a
  failure (:251-258) and, with `failureBackoffMs` (constant `CATALOG_FAILURE_BACKOFF_MS = 15_000`, :16), make
  the key throw `CatalogBackoffError` (:210-213) for the next 15 s, so a withdrawn id would poison the next
  15 s of visitors into the failure path.
- Any other non-OK response **throws** (the failure path, with backoff), following `fetchPublicNote`
  (CONFIRMED `frontend/lib/server-public-notes.ts:33-46`: `cache: "no-store"`, 404 → `null`, else throw).
- Pass `hardCapMs: CATALOG_PAGE_TTL_CAP_MS` (120 s, :2; R4 keeps it); never `next: { revalidate: 300 }`.
- Failure renders a static `noindex` "unavailable" head (the `v0.180.0` profile pattern, e.g.
  `frontend/app/public/profile/[userId]/error.tsx`), never an empty 200 outline.
