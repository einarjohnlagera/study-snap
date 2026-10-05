# 2026-10-01 — production restarts: one public note page fans out into dozens of ranking queries and saturates a 0.1-CPU database

## Status: diagnosis only. No code, config or data changed. Fix plan: `docs/claude-plans/2026-10-01-public-note-page-fanout-fix-plan.md`.

**Reported** by the owner: "our prod backend server was down again", with a `Broken pipe` stack trace
captured at 12:51:50 UTC, and the hypothesis that a long-running job was to blame.
Backend `notelib-backend-prod` (`srv-d6u0jkvgi27c73dvl9k0`), instance `-zpntl`, `v0.164.0`.
Database `notelib-db-prod` (`dpg-d6tvb8fkijhs73fda4m0-a`), plan `basic_256mb`.

Read-only investigation: Render events, logs and metrics for both resources, production `SELECT`s and
one `EXPLAIN` (no `ANALYZE`), and the code at `main` `edc91fb4`. Claims are **VERIFIED** (an event, a
log line, a metric, a query result, or code opened) or **INFERRED**.

**The verdict in four lines:**

1. **VERIFIED:** Render restarted the instance because *"HTTP health check failed (timed out after 5
   seconds)"* — `server_failed` event, 12:53:02 UTC. It was not a scheduled job, an OOM or a deploy.
2. **VERIFIED:** in the two minutes before, Postgres logged 100+ statements slower than 2 s (up to 23 s),
   almost all from the public-library list endpoint, while the database ran at ~76 % of its CPU limit.
3. **VERIFIED:** those statements are what one public note page issues per render — it walks every
   page of two note lists to fill two rails totalling 7 cards (§3).
4. **INFERRED:** with every request thread waiting on a slow query, the liveness probe could not get a
   thread within 5 s. Nothing measured thread occupancy directly (§5).

**⚠️ This file corrects three earlier findings — see §7. The largest: "the database was idle" was read
against the wrong scale. The database's CPU limit is 0.1 core, not 1.**

---

## 1. Timeline (UTC), 2026-10-01 — all VERIFIED

| Time | Event | Source |
|---|---|---|
| 12:40, 12:45, 12:50 | `GenerationRecoveryJob` / `BulkGenerationResultCleanupJob` complete, all counters zero | app log |
| 12:44:54 | One public note viewed (`residual-income-…`, Accountancy). Ranking queries for `accountancy` at offsets 50–350 take 3.1–5.4 s each. No failure | `analytics_events`, DB log |
| 12:48 → 12:51 | Zero completed requests | `http_request_count` |
| 12:51:00 → 12:52:14 | Public note `understanding-audit-risk-inherent-control-and-detection-risk` (subject `audit-fundamentals`, Accountancy) fetched **14 times in 74 s**, all anonymous, `pathType: seo` | `analytics_events` |
| 12:51:03 → 12:52:04 | 100+ statements over 2 s. In the 47 s after 12:51:17 alone: 35 ranking queries, 16 `count(*)`, 37 list-item fetches. Ranking durations 3.3–23.1 s | DB log |
| 12:51:34.955 | `unhandled_exception requestId=4a645046-…` — `Broken pipe` while serializing element **3677** of a list (§8) | app log |
| 12:51:50.635 | `unhandled_exception requestId=4162bf42-…` — `Broken pipe`. **This is the trace the owner captured** | app log |
| 12:52:16 / 12:52:30 | `EXPLORE_VIEWED` (anonymous, `referrerSource: social`); `GUIDANCE_TIP_SHOWN` for user `dee4225c-…` | `analytics_events` |
| 12:52:59.946 | `Commencing graceful shutdown`; complete in 27 ms | app log |
| **12:53:02.422** | **`server_failed` — `unhealthy: "HTTP health check failed (timed out after 5 seconds)"`, `evicted: false`** | Render events |
| 12:53:05 → 12:53:42 | `Starting BackendApplication v0.164.0` → started in 38.2 s (same version, same instance id) | app log |
| 12:53:45 | `server_available` | Render events |

**Impact:** `instance_count` 0 for the 12:53 bucket (~43 s with no process); 8 × `499` and 1 × `502`
across 12:52–12:54. The user-visible stall began earlier, around 12:51, when requests stopped returning.

**Resources during the burst:**

| | Baseline | 12:52–12:53 | Limit |
|---|---|---|---|
| Database CPU | 0.007–0.013 | **0.0756 / 0.0751** (per-minute average) | **0.1** |
| Database memory | 144–152 MB | **243 MB** | 256 MB |
| Database connections | 20 | 20 | — (pool holds 20 open permanently) |
| App CPU | 0.001–0.004 | 0.020 / 0.018 | 1 |
| App memory | 851 MB | 853 MB | 2 GB |

## 2. Ruled out — VERIFIED

- **A long-running or scheduled job.** Every job that logged in the window reported zeros, and no
  `action=` generation line appears. The slow statements are all list reads issued by request threads
  (`client=10.30.16.46`, `app=PostgreSQL JDBC Driver`), with public-library parameters.
- **OOM.** A graceful shutdown ran; app memory was flat at 851 MB of 2 GB; the event says
  `evicted: false`.
- **A deploy.** Last deploy ended 2026-09-30T13:55:56Z, 23 hours earlier.
- **Pool exhaustion.** No `Connection is not available` line and no saturation-detector output between
  12:00 and 12:53. The search used `level: ["error","warning"]` — Render labels WARN as `warning`, and
  an earlier pass in this session missed lines by filtering on `warn`.

## 3. The mechanism — one page render issues dozens of heavy statements

**VERIFIED in code.** `frontend/app/public/library/[subject]/[slug]/page.tsx` displays three rails:
3 "related" cards (`:95-102`), 3 "more in subject" cards (`:90-93`) and 4 "more in program" cards
(`:116-124`). Each render calls:

| Call | Line | What it fetches |
|---|---|---|
| `getServerPublicNoteBySeoPath` | `:84` | the note (`cache: "no-store"`, `server-public-notes.ts:32-36`) |
| `getServerPublicNotesBySubjectSlug(subject)` | `:89` | **every page** of the subject's notes — feeds the 3 related cards |
| `getServerPublicNotesBySubject(note.subject)` | `:91` | 4 notes, `sort=recent` — already bounded (owner decision 2026-09-06); feeds "more in subject" |
| `getServerPublicNotesByCourseProgram(courseProgram)` | `:117` | **every page** of the program's notes — feeds the 4 program cards |

The two unbounded calls go through `fetchAllPublicNotePages` (`server-public-notes.ts:84-106`), which
requests `page=0,1,2,…` at `pageSize=50` until `hasMore` is false **and sends no `sort`**. With no
`sort` the server uses `RECOMMENDED`, which is not SQL-orderable (`PublicLibrarySort.java:13-15` — only
`RECENT` and `TITLE` are), so every page takes the ranked branch at `NoteService.java:807-831`: one
`countPublicLibraryRankedMatches` plus one `findPublicLibraryRankedPageIds`, then the projection fetch.

**Each ranked page recomputes the whole ranking.** `EXPLAIN` of the statement (estimated cost ≈ 7,041):
a sequential scan of `notes` (10,551 rows) to aggregate copies, an index-only scan of every
`PUBLIC_NOTE_VIEWED` row (49,499) to aggregate views, a `regexp_replace` filter over the public notes,
then a sort — repeated identically for offset 0, 50, 100, ….

**The arithmetic, from production counts:**

| Program | Public notes | Pages walked per render |
|---|---|---|
| Civil Engineering | 875 | 18 |
| Architecture | 417 | 9 |
| Sanitary Engineering | 371 | 8 |
| Accountancy | 358 | 8 |
| Eight further programs | 318–328 each | 7 |

An Accountancy note render therefore issues about 8 ranking queries, 8 ranked counts and 8 projection
fetches for the program rail, one more such trio per page of the subject, plus the note lookup —
roughly 30 statements, ~18 of them heavy, **to show 7 cards whose order the page then recomputes in
JavaScript** (`page.tsx:97-101`, `:119-123`). A Civil Engineering note needs more than twice that.

**The log matches the code exactly.** The DB log shows `$1 = 'accountancy'` with offsets
50, 100, 150, 200, 250, 300, 350 and limit 50, and `$1 = 'audit-fundamentals'` with limits 50 and 4.

**The same offset appears several times within seconds** (offset 100 at 12:51:03.0, 12:51:03.3 and
12:51:08.4; offset 150 at 12:51:02.3 and 12:51:03.6). **INFERRED:** several renders ran concurrently and
each missed the 5-minute fetch cache (`next: { revalidate: 300 }`), because a cache entry only exists
once the first fetch completes. At low traffic the cache is cold for almost every visit.

**⚠️ The logged statements are a floor.** `log_min_duration_statement` is `2s`, so anything faster is
absent from the count.

## 4. Why one visit becomes 14 fetches — partly VERIFIED, partly open

**VERIFIED:** `PUBLIC_NOTE_VIEWED` is recorded **by the backend** on every
`GET /notes/public/seo/{subject}/{slug}` (`NoteService.java:1187`, and `:1156` for the by-id path). The
frontend holds only the type constant (`lib/api.ts:659`). So 14 events means 14 backend fetches of that
note, from any client — browser, crawler or link unfurler.

**VERIFIED:** the note fetch is `cache: "no-store"` and the slug route declares no `revalidate` and no
`generateStaticParams`; `opengraph-image.tsx:17` fetches the note again for the social image.

**INFERRED:** the route is therefore rendered on every request, which is why repeated requests each
repeat the whole fan-out.

**OPEN:** who made the 14 requests. No user agent or IP is retained for this service. The intervals
(0.2 s, 3 s, 2 s, 3 s, 13 s, 5 s, 4 s, 0.5 s, 1 s, 2 s, 35 s, 4 s) and the `referrerSource: social`
event that follows are consistent with a shared link being unfurled and retried, but nothing here
identifies the client. **The count is strikingly stable across incidents — see §6.**

## 5. Why the health check timed out — INFERRED, and what would settle it

**VERIFIED inputs:**

- The liveness group excludes `db` (`application.yaml:145-146`) and Render probes
  `/api/actuator/health/liveness` (service config, read 2026-09-22). So the probe needs a **request
  thread**, not a database connection.
- `server.tomcat.threads.max` defaults to 18 and the pool to 20 (`application.yaml:108`, `:43`). The
  production value cannot be read — no tool exposes Render environment variables.
- The app used ~2 % of one core while the database sat near its CPU cap, and statements took 3–23 s.
- No pool timeout was logged, which is what 18 threads against 20 connections predicts.

**INFERRED:** every request thread was parked on a slow query, so the probe queued past 5 s. This is
**thread occupancy, not pool exhaustion** — and the three shipped mitigations (liveness without `db`,
`threads.max` below the pool size, `connection-timeout: 5000`) all target the pool, so none of them
could have prevented it.

**⚠️ Do not read concurrency from thread names.** `o-10000-exec-69` is a lifetime counter, not a count
of live threads.

**The instrument that would settle it:** `tomcat.threads.busy` (already exposed at
`/actuator/metrics`, authenticated) sampled during an incident.

## 6. This is the recurring outage — VERIFIED across the month

`list_events` returns **24 `server_failed` events since 2026-09-01**: 23 with
*"HTTP health check failed (timed out after 5 seconds)"* and one *connection refused* (09-27 06:18,
while the instance was already restarting).

**Filter used:** for each event time `t`, `PUBLIC_NOTE_VIEWED` rows in `[t − 4 min, t]`: total views,
distinct notes, and the most views of any single note. Database CPU is the per-minute average for the
failure minute or the one before (limit 0.1).

| Failed at (UTC) | Views | Notes | Max same note | DB CPU peak |
|---|---|---|---|---|
| 10-01 12:53:02 | 14 | 1 | **14** | 0.076 |
| 09-29 17:35:44 | 10 | 10 | 1 | not read — DB log shows the same ranking and count statements from 17:32:38 |
| 09-29 15:44:53 | 17 | 2 | **16** | 0.077 |
| 09-29 10:35:34 | 16 | 1 | **16** | **0.100** |
| 09-28 06:22:30 | 2 | 2 | 1 | not read |
| 09-27 06:44:44 | 0 | 0 | — | not read |
| 09-27 06:18:14 | 0 | 0 | — | (connection refused) |
| 09-27 06:05:04 | 9 | 9 | 1 | not read |
| 09-27 05:52:43 | 1 | 1 | 1 | not read |
| 09-27 05:48:14 | 4 | 1 | 4 | not read |
| 09-26 10:07:07 | 82 | 39 | **14** | not read (crawler surge, 572 views in the prior 30 min) |
| 09-25 06:07:45 | 19 | 2 | **13** | 0.081 |
| 09-23 05:36:04 | 17 | 1 | **17** | 0.083 |
| 09-22 13:50:02 | 13 | 1 | **13** | **0.100** |
| 09-21 14:07:52 | 23 | 5 | **14** | 0.088 |
| 09-18 14:48:47 | 22 | 2 | **12** | **0.100** |
| 09-17 05:58:09 | 13 | 1 | **13** | 0.063 |
| 09-16 12:38:56 | 41 | 39 | 2 | not read |
| 09-11 06:21:09 | 22 | 2 | **11** | **0.100** |
| 09-10 13:14:57 | 23 | 2 | **12** | **0.100** |
| 09-07 10:35:51 | 162 | 82 | 2 | not read (1,587 views in the prior 30 min) |
| 09-05 11:45:48 | 48 | 6 | **15** | **0.100** |
| 09-04 05:56:36 | 16 | 1 | **16** | 0.064 |
| 09-01 07:10:03 | 18 | 1 | **18** | not read |

**What the table shows:**

- **15 of 24 restarts** follow one public note being fetched 11–18 times within four minutes.
- **All 13 of those whose database CPU was read** show it climbing from a ~0.008 baseline to
  0.063–0.100 of a 0.1 limit in the failure minute or the one before.
- **Three have a different shape:** broad crawling (09-07, 09-16, and 09-26 which shows both).
- **Six are not explained by this filter:** the five on 09-27 (the morning after the 09-26 crawler
  surge) and 09-28. 09-29 17:35 has ten different notes viewed once each and the same slow statements
  in the DB log. Zero note views does not mean zero load — the subject pages, exam hub pages and the
  sitemap walk the same lists without fetching a note (§9).

## 7. Corrections to earlier findings

Each is also marked in the file it corrects.

1. **`2026-09-22-prod-restart-single-instance-no-pool-exhaustion.md` — "TRIGGER NOT IDENTIFIED" is
   resolved.** Render's event log records *health check timed out after 5 seconds* at 13:50:02; one
   note was fetched 13 times in the preceding four minutes; database CPU was 0.100 of 0.1 at 13:49.
   That file's §7 said no tool could read Render's platform events. `list_events` can.
2. **`2026-09-04-prod-outage-hikari-pool-exhaustion.md` §11 — "THE DATABASE WAS IDLE AND HEALTHY
   THROUGHOUT" is wrong.** It cited CPU *"0.008–0.021 of a core"*. The limit is **0.1**; this session
   reads 0.052 at 05:55 and 0.064 at 05:56 — 52–64 % of the limit. That section refuted a
   database-side stall on this reading and steered the follow-up work toward connection holding.
3. **`2026-09-10-prod-pool-exhaustion-trigger-unresolved.md` §3 and §11 — "DB CPU 0.0626 peak … idle
   throughout" has the same error.** 0.0626 is 63 % of the limit, and the per-minute average reads 0.100
   at 13:14 on 09-10. **Caveat kept:** on 09-10 the pool-exhaustion minutes themselves (13:12–13:13)
   read 0.033 and 0.013, so that incident does not fit as cleanly as the others.
4. **`2026-09-04-…` §12 — "`PUBLIC_NOTE_VIEWED` is fired client-side after the page renders" is
   wrong.** The backend records it (§4). Two conclusions drawn from it fall: that each row is "a load
   that completed", and that crawlers "leave no row". Crawlers do leave rows; what leaves none is a
   page that never fetches a note.

**What those findings got right and this one confirms:** the 09-04 §12 observation that one note was
hit sixteen times in 78 seconds just before saturation was the trigger. It was recorded as "direction
not settled" and never connected to the page's fan-out.

## 8. Open: a response with at least 3,678 list elements

`requestId=4a645046-f5f0-46de-96e6-ea50a75274ed` (12:51:34.955) failed while serializing
`java.util.ImmutableCollections$ListN[3677]`. Some endpoint returned a list of 3,678 or more items.

**Ruled out:** `GET /notes` (`NoteController.java:731`) — no account owns more than 3,677 notes (the
largest, the owner's, has 2,002); `GET /subjects` — 210 distinct public subjects.
**Not identified.** The stack trace does not name the controller. It is plausibly the same family of
defect and deserves its own read.

## 9. Other callers of the same walk — VERIFIED in code, not measured

| Caller | What it walks |
|---|---|
| `app/public/library/[subject]/page.tsx:146`, `:160` | every page of a subject, twice per render (metadata and body) |
| `app/exam/[slug]/page.tsx:199` | every page of **several programs in parallel** (`server-public-notes.ts:298-300`) |
| `app/sitemap.ts:11` | every page of the whole catalog — 2,001 public notes, 41 ranked pages |

None sends a `sort`, so all take the ranked branch.

## 10. What could not be checked

- **Request paths, user agents and IPs** — request logs are not retained for this service.
- **Render environment variables** — so the live `threads.max` and pool size are assumed at their yaml
  defaults.
- **Thread occupancy during the incident** — §5.
- **Events before 2026-09-01** and metrics older than 30 days.
- **Database CPU for 11 of the 24 events** — marked "not read" in §6 rather than assumed.

## 11. Obligations

- Backlog Index row for this file and its plan — added with this write-up.
- §8's unidentified large response is open.
- The six restarts §6 does not explain should be re-read after the fix ships, not before.
