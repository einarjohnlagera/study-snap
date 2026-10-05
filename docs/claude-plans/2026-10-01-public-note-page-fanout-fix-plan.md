# Fix plan — public note page fan-out and database saturation

**Written 2026-10-01 by the Prod Investigator session, against `main` at `edc91fb4` (`v0.164.0`
Released). Not implemented. A separate session implements.**

Evidence: `docs/claude-findings/2026-10-01-prod-restarts-public-note-page-fanout-db-saturation.md`.
Read its §3 (the fan-out), §5 (what is inferred) and §6 (the month's 24 restarts) first.

## The problem in one paragraph

Render has restarted the backend 24 times since 2026-09-01, 23 of them because the liveness probe
timed out after 5 seconds. In 15 of the 24, one public note page had just been fetched 11–18 times.
Each render of that page walks every page of two public-note lists — 8 pages for an Accountancy note,
18 for Civil Engineering — and every page runs a full ranking query plus a count on a database limited
to 0.1 CPU and 256 MB. The page uses the result to show 7 cards and re-sorts them itself.

## Scope — four legs, ordered by outage-stopping effect per unit of risk

### Leg A — raise the database plan (owner action, no code)

`notelib-db-prod` is `basic_256mb`: CPU limit 0.1, memory 256 MB, `shared_buffers` 64 MB. During the
incident it averaged 0.076 CPU and reached 243 MB.

- **What it buys:** headroom. The same burst completes in a fraction of the time.
- **What it does not do:** remove the fan-out. A larger crawl or a larger program reaches the new
  ceiling.
- **Owner decision:** which plan, and the cost. This is a Render dashboard change and is the owner's.

### Leg B — bound the two rails to what they display (the headline code change)

Files: `frontend/lib/server-public-notes.ts`, `frontend/app/public/library/[subject]/[slug]/page.tsx`
and their tests. No backend change is required:

- `PublicNoteDetailResponse` already carries `coursePrograms` and `studyPackDone`
  (`dto/PublicNoteDetailResponse.java:11`, `:24`), so the page does not need the subject walk to learn
  the note's program (`page.tsx:105-114` reads it from `allSubjectNotes` today; `:146` already reads `note.coursePrograms`).
  The precedence is the same one `page.tsx:106-111` requires: `resolvePublicDetailPrograms`
  (`NoteService.java:1637`) returns the joined catalog programs first and falls back only when there
  are none. The implementer should re-read that method rather than trust this line.
- `GET /notes/public` already accepts `page`, `pageSize`, `sort`, `readyOnly`, `subject` and
  `courseProgram` (`NoteController.java:824-863`).

Changes:

1. **Program rail** (`page.tsx:116-124`): one request for the cards it shows —
   `courseProgram=X&readyOnly=true&page=0&pageSize=5` — instead of `getServerPublicNotesByCourseProgram`.
   Five, because the current note is filtered out client-side. Confirm first that `readyOnly`
   (`PublicLibraryRepositoryImpl.java:366`) selects the same notes as today's `studyPackDone === true`
   filter.
2. **Related rail** (`page.tsx:95-103`): one request — `subject=<slug>&readyOnly=true&page=0&pageSize=4`
   — instead of `getServerPublicNotesBySubjectSlug`.
3. **`fetchAllPublicNotePages` must send an SQL-orderable `sort`** (`server-public-notes.ts:84-106`).
   Only `RECENT` and `TITLE` are (`PublicLibrarySort.java:13-15`). This covers the remaining walkers:
   the subject page, the exam hub pages and the sitemap (finding §9).

**Owner decision owed — the order of the two rails.** Today both are ordered by popularity
(views + 3 × copies + 2 × likes), computed in JavaScript over the whole list.

| Option | Cost per render | Behaviour change |
|---|---|---|
| **B1 — most recent** (`sort=recent`) | a plain count and an ordered page select per rail | Rails show the newest ready notes, not the most popular. Same trade the owner accepted for "more in subject" on 2026-09-06 |
| **B2 — keep popularity** (default ranked sort, one page) | 2 ranking queries + 2 ranked counts | No visible change. Still ~4 heavy statements per render, down from ~18–38 |

**Recommendation: B1.** B2 leaves the ranking query on the request path of every crawler hit, and its
view aggregate grows with every hit (49,499 rows today; 13,413 were added on 2026-09-26 alone).

**Before changing item 3, the implementer must check each consumer's dependence on server order:**
`getSectionedNotes` in `app/public/library/[subject]/page.tsx:164` (featured / popular / recent),
the exam hub merge in `server-public-notes.ts:298-310`, and `app/sitemap.ts`. The slug page re-sorts
and is unaffected. If a consumer relies on `RECOMMENDED` order, that is a product decision to surface,
not absorb.

### Leg C — stop the public list endpoint from occupying every request thread (survivability)

After B the page is cheap, but `GET /notes/public` with a ranked sort is still anonymous, still
expensive, and still reachable from the Explore page. One of:

- a concurrency cap on the ranked branch (fail fast with 503 beyond N in flight), or
- a query timeout on the ranked statements, so a slow one frees its thread.

**Sequence it after A and B and decide it on their measurement.** It is listed so the gap is not lost:
today nothing bounds how many request threads this one endpoint can hold.

### Leg D — slug route caching (owner decision, recommended: defer)

The slug route renders on every request because the note fetch is `cache: "no-store"`
(`server-public-notes.ts:32-36`). Caching it would cut repeated renders, **but the backend records the
view on that fetch** (`NoteService.java:1187`). A cached page stops counting views, which feed the
ranking, the creator-impact page and the admin dashboard.

**Recommendation: do not change it in this release.** After B a render costs a handful of cheap
queries. Revisit only if view tracking is moved off the page fetch.

## Explicit non-fixes, with reasons

- **Do not raise `maximum-pool-size`.** This incident logged no pool timeout. It went 10 → 20 after
  2026-09-04 and the restarts continued.
- **Do not lower `threads.max` again, and do not touch the liveness group.** Both are correct and
  neither addresses thread occupancy by slow queries.
- **Do not add PgBouncer.** The pool holds 20 connections; the constraint is database CPU.
- **Do not materialise the ranking counters or add an index yet.** `EXPLAIN` shows a sequential scan of
  `notes` for the copy aggregate, and a partial index on `copied_from_note_id WHERE copied_from_public`
  would help — but B removes most of the calls. Measure after B.
- **Do not block crawlers.** Public notes exist to be indexed. The fix is making a crawl cheap.
- **Do not chase who made the 14 requests.** No user agent is retained, and B makes the answer
  irrelevant to availability.

## Guards — pre-declared

1. **Request-shape test** in `frontend/lib/server-public-notes.test.ts`, which already stubs
   `global.fetch` rather than the module: the two functions the rails call issue **exactly one**
   `/notes/public` request each, with `page=0`, `pageSize ≤ 5` and an explicit `sort`. It must fail
   against today's code.
2. **Walker test:** every URL built by `fetchAllPublicNotePages` carries a `sort` whose value is
   SQL-orderable. Pin the allowed set to `recent` and `title`.
3. **Mutation check, named:** remove the `sort` from the walker and remove the `pageSize` from a rail;
   state which test fails for each.

**`app/public/library/[subject]/[slug]/page.test.tsx` does not count as guard 1.** It mocks
`@/lib/server-public-notes` wholesale (`:20-25`), so it passes unchanged before and after B and is
evidence of nothing about request shape.

## Verification after deploy — and the kill criterion

Read-only, in this order:

1. `SELECT`-free check first: Render `list_events` for `server_failed` over the following 7 days.
   **Baseline: 24 in 30 days, 12 in the 7 days to 2026-10-01.**
2. Postgres log: statements matching `rank_copy_n` over 2 s in the same window.
3. Database CPU per-minute peak against the 0.1 limit (or the new limit, if A shipped).

**Kill criterion, stated before the read:** if `server_failed` events continue at a comparable weekly
rate after B is live, the thread-occupancy reading (finding §5, INFERRED) is wrong or incomplete. The
next read is `tomcat.threads.busy` sampled during an incident, and the six restarts the finding could
not explain (§6) become the lead.

**Checkpoint:** `[CHECKPOINT — due 7 days after B's production deploy]`, dated from the deploy, not
from the merge.

## Routing, tier, version

- **Routing:** B is frontend-only and touches two source files plus tests. It is an isolated fix with a
  clear root cause, so by the routing table Claude Code implements it directly. If the consumer check
  under item 3 shows a backend change is needed, it becomes multi-system and goes to Codex.
- **Verification tier:** one `advisor()` call on the diff, plus guards 1–3. No money, quota or
  permission boundary moves.
- **Version:** `v0.164.0` is Released and no release branch is open. Recommend opening the next
  version as an outage fix scoped to **B only**, with A as a parallel owner action and C and D as
  recorded decisions. Folding feature work into it delays the one change that stops the restarts.
- **Deploy order:** B changes only what the frontend requests; the backend already serves every
  parameter it uses. Frontend and backend may deploy in either order.

## Decisions owed to the owner

1. **A:** raise the database plan, and to what.
2. **B:** rail order — B1 (most recent, recommended) or B2 (keep popularity).
3. **C:** whether to cap or time out the ranked list endpoint, after A and B are measured.
4. **D:** confirm the slug route stays uncached for now.
