# 2026-09-01 — production frontend build failure: `/notes/public` crossed the 2 MB data-cache limit

## Status: diagnosis only. Nothing fixed, nothing scoped, no code changed.

Written 2026-09-01 from the failing production deploy log plus a read of the code at `origin/main`
(`aa515fe5`). **The build is failing right now and will fail on every branch until it is fixed** — see
*Why this is not a v0.99.0 regression*.

---

## The failure, as logged

```
23:54:00  Failed to set Next.js data cache for [REDACTED]/notes/public,
          items over 2MB can not be cached (2579045 bytes)          ← repeats ~12+ times
23:54:33  Failed to build /public/library/[subject]/page:
          /public/library/foundation-engineering after 3 attempts.
23:54:33  Export encountered an error on /public/library/[subject]/page:
          /public/library/foundation-engineering, exiting the build.
23:54:34  Next.js build worker exited with code: 1 and signal: null
23:54:34  Error: Command "npm run build" exited with 1
```

Earlier in the same log: `Generating static pages using 1 worker (156/252)`.

**⚠️ `foundation-engineering` is not special.** It is whichever page happened to be in flight when the
backend gave out. Re-running the build will likely name a different subject. Do not go looking for
something wrong with that subject's data.

---

## Root cause

**The public note catalog has grown past 2 MB (2,579,045 bytes), and Next.js will not put anything over
2 MB in its data cache.** That is a hard constant in Next.js, not a tunable.

The repeated cache warnings are **the mechanism, not noise**. Losing the cache turns one fetch into
hundreds:

`getServerPublicNotes()` — `frontend/lib/server-public-notes.ts:65` — fetches `/notes/public` with **no
`size` parameter**, i.e. the entire public catalog in one response. It is reached from almost every page in
the build:

| Call site | Times per build |
|---|---|
| `frontend/app/sitemap.ts:11` | 1 |
| `[subject]/page.tsx:134` — `generateStaticParams` (via `getServerPublicSubjects`) | 1 |
| `[subject]/page.tsx:144` — `generateMetadata` (via `getServerPublicNotesBySubjectSlug`) | **once per subject page** |
| `[subject]/page.tsx:158` — page body (same) | **once per subject page** |
| `[subject]/[slug]/page.tsx:89` | **once per note page** |
| `getServerPublicNotesByCourseProgram` / `ByCoursePrograms` — `server-public-notes.ts:136,151` | per call |

Normally Next.js's data cache dedupes all of that into a single fetch. **Over 2 MB it silently does not**,
so each of ~250 static pages issues its own 2.5 MB request against the backend during one build. The
backend saturates, one page exhausts its 3 attempts, and the build exits 1.

**A second, compounding inefficiency at the same spot:** `getServerPublicNotesBySubjectSlug`
(`server-public-notes.ts:121-124`) fetches the **whole catalog and filters it in JavaScript**, even though
the endpoint has accepted a `subject` filter all along. So the two hottest call sites in the build are the
two that least need the full catalog.

---

## Why this is not a v0.99.0 regression

- `backend/.../dto/NoteListItemResponse.java` last changed **2026-08-17** (`4b0bdd8a`).
- `frontend/lib/server-public-notes.ts` last changed **2026-08-06** (`1b04929e`).
- `v0.99.0` touched **neither**, nor `NoteController`, nor anything under `app/public/library/`.

**This is data growth crossing a threshold, not a code change.** The defect has been latent since the
fetch was written unbounded; the catalog simply grew into it. **Reverting `v0.99.0` will not fix it, and
`v0.100.0` will hit the same wall.**

---

## Timing — and the link to the 1am production incident

- `v0.99.0` merged to `main` at **2026-08-31 23:39 +08** (`aa515fe5`, PR #1203).
- Per `CLAUDE.md`, merging to `main` **auto-deploys to production**.
- This frontend build failed at **23:54**, ~15 minutes later.

**So the `v0.99.0` production deploy failed at the frontend build step.**

**⚠️ RESOLVED 2026-09-01 — AND THE LEAD THIS SECTION OFFERED WAS WRONG. The build failure and the 1am
outage are SEPARATE EVENTS.** This section proposed the build failure as *"a stronger lead than any
scheduled job"* for the 1am outage. It is not a lead at all: the owner confirmed the outage ran
**01:00–01:01, a single minute**, more than an hour AFTER this 23:54 build failure, and its stack trace is
a `java.net.UnknownHostException` on the Render internal Postgres host (`dpg-…-a`) — a **transient DNS/DB
resolution hiccup**, with `HikariPool total=0, active=0, idle=0` proving the pool never opened a single
connection rather than being exhausted by load.

**⚠️ THIS CUTS BOTH WAYS AND THE SECOND HALF IS THE IMPORTANT ONE.** Before the timestamp was known, the
DB failure was a live alternative explanation for the build failure itself — if the database had been
unreachable at 23:54, the backend would have been failing every request regardless of fetch amplification,
and `v0.100.0` item 7's fix would have been treating a symptom. **The one-minute window at 01:00 rules that
out**, so the amplification diagnosis in this document stands and item 7's *"unblocks deploys"* claim is
sound. Recorded because the question was raised against the release's own headline claim, and *"we checked
and it held"* is a different fact from *"nobody asked."*

**The cron elimination below remains correct and is now pinned by a test** (`v0.100.0`,
`everyCronJobPinsTheTimezoneItsScheduleIsInterpretedIn`): **no cron job is scheduled at 01:00 PHT**, and the
one that reads like it (`billing.usage-reset-cron`, `0 15 1 * * *`) carries **no `zone`**, so it runs in the
JVM default. Nothing in the repo sets `TZ` (Dockerfile, `application.yaml`, `docker-compose.yml` are all
silent; `eclipse-temurin:21-jre` defaults to UTC), which puts it at 09:15 PHT, not 01:15. **⚠️ That
arithmetic is now load-bearing: if someone ever adds a `zone` to that job, the reasoning that cleared it
stops being true — which is why the timezone map is pinned rather than documented.**

---

## Fix options — the backend already supports every one of them

`NoteController.listPublic` (`backend/.../controller/NoteController.java:647-661`) accepts `subject`,
`size`, `page` and `pageSize`. No backend change is required for options 1 or 2.

1. **Filter by subject server-side (smallest, highest leverage).** Make
   `getServerPublicNotesBySubjectSlug` request `?subject=…` instead of fetching everything and filtering.
   This removes the large fetch from the two call sites that run once per subject page — the bulk of the
   ~250 requests. **⚠️ The slug is derived from the label** (`getPublicSubjectSlug`), so this needs a
   slug → label resolution step; `getServerPublicSubjects()` already produces exactly that mapping.
2. **Paginate `getServerPublicNotes()`** so each response stays under 2 MB, keeping the cache working for
   `sitemap.ts` and `generateStaticParams`, which genuinely do need the whole catalog.
3. **Trim the list payload.** ~2.5 MB across the catalog is roughly **2.7 KB per note**, which is heavy for
   a list item. `NoteListItemResponse` carries both `contentPreview` and `summaryPreview`
   (`:15-16`); the subject cards may not need both. This buys headroom but **does not remove the
   unbounded-fetch defect** — it only moves the threshold, which is what produced this incident in the
   first place.

**⚠️ Raising the 2 MB limit is not an option.** It is a hard Next.js constant; only a custom
`cacheHandler` changes it, which is a far larger change than any of the above.

**⚠️ Options 1 and 2 are complementary, not alternatives.** 1 fixes the per-page amplification; 2 fixes the
two call sites that legitimately need everything. Doing only 3 leaves the build one growth spurt from
failing again.

---

## Not investigated

- Whether the backend was *also* degraded at the time for an unrelated reason, which would change how
  quickly the amplification became fatal.
- The actual public note count and per-note payload breakdown — the 2.7 KB average is derived from the
  logged byte count, not measured against a production row count.
- Whether any other route fetches `/notes/public` unbounded outside `app/` and `lib/`.
