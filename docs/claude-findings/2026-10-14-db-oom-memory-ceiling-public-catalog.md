# 2026-10-14 — `notelib-db-prod` signal-9 kills: every deploy opens a 1–2 minute double-pool window; whether it breaches the ceiling depends on the floor at that moment

## Status: diagnosis only. No code, config or data changed. Fix handoff: `docs/claude-plans/2026-10-14-db-oom-memory-ceiling-fix-plan.md`.

**Handed off** by Release Implementor, scoping `v0.172.0`:
`/private/tmp/.../prod-investigator-handoff-db-oom.md`. That handoff listed 9 signal-9 kill timestamps
(stated as "8" in its own summary — not reconciled, immaterial to the mechanism). This file verifies
against `notelib-db-prod` (`dpg-d6tvb8fkijhs73fda4m0-a`), the backend service, and the code at `main`,
using `SHOW`/`SELECT`/`EXPLAIN` (no `ANALYZE`), `get_metrics`, `list_events` and `list_logs`.

**⚠️ This is the fourth draft.** Three `advisor()` passes each caught real errors in the prior draft —
untested hypotheses, a misattributed log line, an overclaimed "universal CPU" headline contradicted by
the session's own data, and (this round) a connection-doubling story built on the wrong half of the
evidence: the earlier draft reasoned from when the *new* instance's pool started, when the decisive
fact is when the *old* instance's pool stops. Pulling the old instance's shutdown logs changed the
verdict again, cleanly this time. Treat this one as current.

**The verdict:**

1. **CONFIRMED by log timestamps, for all 4 deploy-adjacent kills, not 3 of 4 — every deploy keeps the
   old instance's Hikari pool alive for 1–2 minutes past the point the new instance is already pooled.**
   The old instance's `Commencing graceful shutdown` log line lands **39 seconds to 2 minutes after**
   each of the four kills (10-08, 10-09, 10-13, 10-14) — not before, as an earlier draft assumed. For
   that whole window, up to 40 connections are genuinely open. §2.
2. **CONFIRMED as a universal deploy behavior, not an edge case — and NOT always sufficient on its own.**
   The 10-12 deploy shows the identical ~90-second, 40-connection overlap, memory reaching 251 MB — and
   no kill. §3. Whether an overlap breaches the 268 MB ceiling depends on the memory floor at that
   moment, not on the overlap happening at all.
3. **CPU elevation (0.06–0.1 of a 0.1-core limit) precedes most, not all, of the non-deploy kills, and a
   clean no-kill day (10-10) never exceeds 0.02** — a real signal when present, but 10-12's control case
   and 10-12's own earlier kill (14:55:15, no deploy nearby) are not explained by CPU. §4.
4. **The unifying factor under all of the above is headroom.** The memory floor sits within about
   45 MB of the 268 MB ceiling even in ordinary daytime traffic with nothing else going on (§5). A
   40-connection overlap or a CPU-bound slowdown each cost roughly that order of magnitude; either one,
   on top of this floor, is sometimes — not always — enough.
5. **`getPublicLibraryDiscoverySections` is a confirmed, uncached, redundant-computation defect, called
   from a live client-side fetch** that re-fires on view-tab switches, not just once per page load. Its
   request volume could not be measured this pass (`http_request_count` filtered to its path returned
   empty — likely a path-prefix mismatch, not zero traffic; not resolved). §6.

---

## 1. What's pinned and can't move

`CLAUDE.md`: shrinking the Hikari pool is pinned by the 2026-09-04 outage fix and
`TomcatThreadPoolHikariAlignmentTest`. That test only pins `maximum-pool-size` relative to
`server.tomcat.threads.max`; `minimum-idle`/`max-lifetime` are unset in `application.yaml` (confirmed by
grep) and therefore at Hikari defaults.

## 2. Every deploy keeps both pools alive for 1–2 minutes — VERIFIED by shutdown-log timestamps

The decisive evidence is **when the old instance's pool closes**, not when the new one opens (an earlier
draft used the latter and got the direction of the effect backwards for 10-09). `list_logs`, filtered to
`*Commencing graceful shutdown*` and `*HikariPool-1 - Shutdown*`, on the **old** instance for each
deploy:

| Deploy | New pool up (`Start completed`) | Kill | Old instance: graceful shutdown begins | Old pool: shutdown completed |
|---|---|---|---|---|
| 10-08 | 15:03:21.7 | **15:03:43** | 15:05:06.7 (**83 s after the kill**) | 15:05:08.0 |
| 10-09 | 08:41:45.1 | **08:42:41** | 08:43:20.3 (**39 s after the kill**) | 08:43:21.9 |
| 10-13 | 13:05:29.3 | **13:05:48** | 13:07:24.2 (**96 s after the kill**) | 13:07:25.4 |
| 10-14 | 13:45:32.5 | **13:45:53** | 13:47:06.5 (**73 s after the kill**) | 13:47:08.3 |

**In every one of the four deploy-adjacent kills, the old instance's pool is still fully open for well
over a minute *after* the kill.** The new instance's pool had already started (minutes-to-seconds
earlier in each case). So at the moment of every one of these four kills, both pools existed
concurrently — this is now confirmed from both directions (new pool already up, old pool not yet torn
down), not inferred from one side. **Correction:** an earlier draft of this file concluded 10-09 "does
not fit this pattern" because it only checked the new instance's startup timing; the old instance's own
shutdown log, pulled this round, shows the opposite.

**What produces the overlap:** Render's zero-downtime deploy starts routing to the new instance once it
passes health checks, and only *then* signals the old instance to begin Spring Boot's graceful shutdown
(which itself takes on the order of a second once started — the gap above is the wait *before*
shutdown starts, not the shutdown itself). The old instance's Hikari pool — and its 20 connections —
stays fully live through that wait. This is a property of the deploy sequencing, independent of any
query shape.

## 3. The overlap happens on every deploy checked, and is not always sufficient — VERIFIED (control case)

The 10-12 deploy (`deploy_started` 14:43:35, `deploy_ended` 14:46:03) had **no kill**. Its
`active_connections` metric, read cleanly (no data-gap this time):

| Time (UTC) | Connections | Memory |
|---|---|---|
| 14:45:30 | 20 | 232 MB |
| 14:46:00–14:47:30 (4 readings, ~90 s) | **40** | 230→252 MB |
| 14:48:00 | 20 | 157 MB |

A genuine, sustained ~90-second double-pool window, memory reaching 251 MB — and the ceiling (268 MB)
was not crossed. **This is the clean control case: the overlap is a universal deploy behavior (5 of 5
deploys checked show it), and by itself it is necessary-adjacent but not sufficient — it only becomes a
kill when the floor it starts from is already high enough that the added ~20-40 MB (very rough; not
independently measured per-connection this pass) tips it over 268 MB.** The four deploys that did kill
(§2) started their climb from floors in the 150–230 MB range during the overlap; 10-12 topped out at
251 MB without crossing.

## 4. CPU: a real signal for some non-deploy kills, not universal, not the mechanism for 10-12's kill

`cpu_limit` is flat at 0.1 core. A clean no-kill day, 10-10, never exceeds 0.02 across 96 samples at
15-minute resolution, so elevated readings elsewhere are a real signal, not noise.

| Kill | Last clean pre-kill CPU reading |
|---|---|
| 10-11 11:55:45 (no deploy nearby) | 0.1 at 11:55:00 and 11:55:30 |
| **10-12 14:55:15 (no deploy nearby — a separate kill from §3's no-kill deploy earlier that day)** | **0.004–0.019 for the full 3+ min before — idle** |
| 10-13 14:06 / 14:15 / 14:22 (no deploy nearby) | 0.082–0.097 — elevated, not always pegged |

CPU elevation is present before most non-deploy kills but not 10-12's — that one is a pure memory-floor
event (§5) with no CPU involvement this file could find. For the four deploy-adjacent kills, §2's pool-
doubling is the better-evidenced mechanism; whether the CPU elevation also seen in those windows is
cause or consequence of running 40 connections' worth of concurrent work isn't separable with the data
available here.

## 5. The memory floor has almost no headroom, independent of either mechanism above

```
SHOW work_mem;              -- 1654kB
SHOW shared_buffers;        -- 64MB
SHOW max_connections;       -- 103
SHOW maintenance_work_mem;  -- 16MB
SHOW statement_timeout;     -- 0   (no cap)
SHOW max_parallel_workers_per_gather; -- 1
```

2026-10-14 03:00–04:00 UTC (11:00–12:00 PHT — daytime for this product's primary Philippine user base,
not an off-hours trough; `http_request_count` for the same window ran 10–30 requests/5 min with bursts
to 66 and 93–103): memory cycled 145→222 MB with connections flat at 20–21, no deploy, no incident.
Against 268 MB, that leaves roughly 45 MB of margin spent by ordinary traffic alone, before either
mechanism above adds anything.

**Not investigated further, not assumed cosmetic:** `pg_stat_user_tables.last_analyze`/`last_vacuum`
read null for `notes`/`study_packs`/`note_course_program` (PG15+ resets these cumulative counters on
crash recovery; one continuous postmaster since 2026-06-11). The planner's real cardinality source,
`pg_class.reltuples`, survives crashes and is about 17% stale (11,419 estimated vs. 13,721 real) — close
enough that query plans aren't being chosen blind.

## 6. `getPublicLibraryDiscoverySections` — confirmed uncached, confirmed on a live (not ISR) request path

`NoteService.java:939-981` runs `FEATURED`, `POPULAR`, `RECENT` sequentially inside one
`@Transactional(readOnly = true)`. `PublicLibraryRepositoryImpl.java:528-539`: `FEATURED` and `POPULAR`
both independently read `EnumSet.allOf(RankMetric.class)` — the full copies/likes/views aggregate,
computed twice, back to back.

**Confirmed this pass:** the frontend caller
(`frontend/components/notes/public-library-page-client.tsx:648-675`) fires `loadDiscoverySections` from
a `useEffect` keyed on `[activeDiscoveryView, isDiscoveryMode, loadDiscoverySections]` — so it re-runs
whenever the visitor switches discovery tabs (Featured/Popular/Recent), not only once per page load.
There is no cache header and no ISR window (unlike the exam hub/subject pages' `revalidate = 300`).
**Request volume for this exact path could not be measured this pass** — `get_metrics http_request_count`
filtered to `httpPath: /notes/public/discovery-sections` returned an empty series for the whole
2026-10-13→14 window, which given the page clearly exists and is linked from the public library landing
page is more likely a path-prefix mismatch in how this metric indexes paths than genuinely zero traffic.
Not resolved — flagged rather than asserted either way.

The copies aggregate (`RANK_COPIES_JOIN`) filters `copied_from_public = true`, 83.6% selective
(11,471 of 13,721 `notes` rows) — a sequential scan is the *correct* plan there; **no index is
recommended.** `max_parallel_workers_per_gather = 1` is live, and `EXPLAIN` of this shape shows a
`Gather Merge` with one parallel worker — an extra backend process with its own `work_mem` share,
spawned up to twice per call to a method with uncached, repeat-firing traffic.

**Correction carried from an earlier draft:** the DB-log line that draft cited as evidence
(`rank_copy_n`-shaped, tag filter `data-analysis`) is confirmed **not** from this method —
`buildPublicLibraryFilterCriteria` is called here with `tags = List.of()` (`NoteService.java:941-951`,
`:1040-1063`). The structural finding stands on the code and `EXPLAIN` alone, not that log line.

## 7. The per-program count fan-out and the exam-hub hypothesis — inconclusive, not ranked as primary

The 5 programs fanned out at 10-14 13:44:37–39 partially match (4 of 5) the `LET` exam hub's
`coursePrograms` (`frontend/lib/exam-hub-config.ts:28-38`); the 5th ("nursing-pharmacy") doesn't belong
to any single hub's combined roster. Suggestive of the exam-hub page's parallel per-program walk
(`app/exam/[slug]/page.tsx:199`), not confirmed. Per-query cost is cheap and index-backed regardless
(`EXPLAIN` cost ≈ 519, no parallel node). Not ranked as a primary mechanism.

## 8. What's still open

- **The exact per-connection memory cost of the overlap** — §3's "~20-40 MB, very rough" is not derived
  from a measurement, just the gap between floors that did and didn't breach. A cleaner read would need
  `pg_stat_activity` memory-context data this session doesn't have access to (no superuser).
  (`work_mem` is 1654kB)
- **Whether the CPU elevation in the deploy-adjacent kills is cause or consequence of the connection
  overlap** — not separable here.
- **What specifically drove 10-12's kill** (no CPU elevation, no deploy nearby) — looks like the plain
  memory-floor mechanism (§5) crossing the line on its own, not traced to a specific query.
- **Flyway shares the app's Hikari pool** — `spring.flyway.url`/`user` unset in `application.yaml`, so
  it runs on the same `DataSource`. Relevant to the fix plan's Leg C, not to the mechanism here.
- **Discovery-sections request volume** — §6, metric query came back empty; not resolved.

## 9. Kill criterion

`*terminated by signal 9*` count over 7 days on the DB resource. **This file examined 9 distinct
timestamps** against the handoff's "8" — use 9 unless Release Implementor finds a genuine duplicate.
Track the overlap-window memory peak at the next several deploys (§2's method) as the primary leading
indicator for the deploy-adjacent class specifically — it's a clean, log-timestamp-level check, not an
inference.
