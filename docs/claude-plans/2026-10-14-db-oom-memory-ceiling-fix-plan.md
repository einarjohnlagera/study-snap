# Fix plan — `notelib-db-prod` signal-9 kills

**Written 2026-10-14 by the Prod Investigator session, against `main` and `notelib-db-prod`. Not
implemented. Release Implementor sequences this; the owner runs every DB-level change; Codex implements
any code change via the normal lifecycle.**

Evidence: `docs/claude-findings/2026-10-14-db-oom-memory-ceiling-public-catalog.md` (fourth draft —
three `advisor()` passes corrected it; read it, not just this summary). **Two distinct, separately-
evidenced mechanisms:**

- **Mechanism 1 (finding §2–§3), CONFIRMED for all 4 deploy-adjacent kills, via shutdown-log timestamps:**
  on every deploy, the old instance's Hikari pool stays fully open for **39 seconds to 2 minutes after**
  the new instance is already pooled — Render routes to the new instance and only then signals the old
  one to begin graceful shutdown. Up to 40 connections are genuinely concurrent for that whole window.
  **Confirmed universal (5 of 5 deploys checked show it), and confirmed NOT always sufficient** — 10-12
  had the identical ~90-second overlap, memory reaching 251 MB, and no kill.
- **Mechanism 2 (finding §4), real but not universal:** CPU elevation (0.06–0.1 of a 0.1-core limit)
  precedes most, not all, of the non-deploy kills — a clean no-kill day never exceeds 0.02. One kill
  (10-12, a different kill than the no-kill deploy above, same date) shows no CPU elevation at all.

Both spend the same scarce resource: a floor that sits within about 45 MB of the 268 MB ceiling even in
ordinary daytime traffic (finding §5).

## Database and role, verified (do not use `CLAUDE.md`'s local-dev example names)

```sql
SELECT current_database();  -- notelib_db_prod
SELECT DISTINCT usename FROM pg_stat_activity WHERE backend_type = 'client backend';  -- notelib_db_prod_user
```

## Scope, ranked by which mechanism each leg addresses

### Leg A — raise the database plan, and confirm it raises CPU too (owner action, no code)

Still `basic_256mb`: 0.1 CPU, 256 MB RAM.

- **Buys headroom against both mechanisms.** More RAM gives the confirmed ~90-second, 40-connection
  overlap (mechanism 1) somewhere to land without crossing the ceiling — note 10-12 already shows a
  251 MB peak during a normal overlap with no other load, so the margin to the next tier matters even
  without touching anything else. Confirm the target tier's actual CPU allocation, not just its RAM,
  before presenting this as a full fix for mechanism 2.
- **Owner decision:** which tier, and the cost.

### Leg F (new) — shorten or absorb the deploy's double-pool window (owner config / platform question — addresses Mechanism 1 directly, now correctly targeted)

**Corrected from an earlier draft of this plan, which targeted the wrong side.** The overlap is not
caused by the new instance ramping up too fast — it's caused by the **old instance staying fully pooled
for 39 s–2 min after the new one is already live**, because Render only signals the old instance to
begin graceful shutdown after cutover, and that signal is what's delayed, not the shutdown itself (which
completes in ~1–2 seconds once started, per the finding's §2 table). A `minimum-idle` change on the new
instance's pool doesn't touch this — the new instance's ramp speed was never the long pole.

Options, most direct first:

1. **Ask Render (or read its deploy-strategy docs) whether the old instance can be signaled to begin
   graceful shutdown sooner after cutover**, rather than whatever is currently delaying it by up to 2
   minutes. This is the one change that would shrink the ~90-second overlap window directly, and it's a
   platform/service-config question, not a code change this repo controls.
2. **If Render's timing can't be changed:** reduce how much the overlap costs instead of its duration
   — this folds back into Leg A (bigger plan = more room for the same ~90s of 40 connections) and Leg B
   (fewer parallel workers during the overlap) rather than a separate fix.
3. **Do not pursue a Hikari `minimum-idle` change for this purpose.** It was this plan's prior
   recommendation and doesn't address the confirmed mechanism — it would only soften a new-instance
   ramp-up cost that the evidence doesn't show as the bottleneck.

**Owner decision:** whether option 1 is worth raising with Render, or whether to treat the overlap as a
fixed cost and address it via headroom (Leg A) instead.

### Leg B — `max_parallel_workers_per_gather = 0` for this database (owner `SET`, cheap, reversible — addresses Mechanism 2 and §6's structural defect)

```sql
ALTER DATABASE notelib_db_prod SET max_parallel_workers_per_gather = 0;
```

- **Why:** `notes` (13,721 rows) and `analytics_events` (77,093 rows) are small enough that parallelism
  buys negligible wall-clock time, but each parallel worker is a full extra process competing for the
  same 0.1-core budget — and, during a deploy overlap, for a share of whatever memory margin is left.
  Finding §6's `EXPLAIN` shows this chosen for the discovery-sections ranked query.
- **Verify:** re-run `EXPLAIN` after the `SET` — `Gather Merge` should disappear.
- **Risk:** low, reversible.

### Leg C — `statement_timeout` (owner `SET` — a real tradeoff, with a workaround for its one real cost)

```sql
ALTER ROLE notelib_db_prod_user SET statement_timeout = '20s';
```

- **⚠️ CONFIRMED: Flyway runs on this same role and pool.** `application.yaml` sets neither
  `spring.flyway.url` nor `spring.flyway.user`, so Flyway migrates using the app's own `DataSource` —
  this timeout **will** apply to migrations too.
- **Workaround, not "none exists":** Flyway supports `spring.flyway.init-sqls` — SQL it runs itself when
  it acquires a connection, before migrating. Setting `spring.flyway.init-sqls: SET statement_timeout = 0`
  re-exempts Flyway's own migration run specifically, without needing a separate datasource. (Caveat:
  if this were instead done via Hikari's pool-wide `connection-init-sql`, the `SET` would persist on
  that *physical* connection for its whole lifetime — up to `max-lifetime` — regardless of which logical
  caller borrows it next, so that specific mechanism would NOT cleanly exempt just Flyway. The
  `spring.flyway.init-sqls` property is Flyway's own hook and doesn't have that leak.)
- **⚠️ `ALTER ROLE` only takes effect on new sessions.** The 20 pinned Hikari connections keep whatever
  they opened with until individually recycled or the backend restarts. **Verify with
  `SHOW statement_timeout` from an app-role session** before treating it as live fleet-wide.
- **Value:** 2026-10-01's finding logged ranking statements up to 23.1 s under comparable pressure,
  higher than this window's 2.5–5.1 s. 20 s is a judgment call, not a derived number.

### Leg D — stop `getPublicLibraryDiscoverySections` computing the same aggregates twice (code, backend — addresses §6, confirmed live, re-firing traffic)

File: `backend/src/main/java/com/studysnap/backend/service/NoteService.java:939-997`,
`backend/src/main/java/com/studysnap/backend/repository/PublicLibraryRepositoryImpl.java:521-539`.

**Confirmed this pass:** called from a `useEffect` in
`frontend/components/notes/public-library-page-client.tsx:648-675` keyed on the active discovery tab —
it re-fires on every tab switch, not just once per page load. Request volume for this path could not be
measured (`http_request_count` filtered to it came back empty — likely a path-matching issue with the
metric, not zero traffic; unresolved).

**Not a small/trivial change — two real constraints:**

- `@Transactional(readOnly = true)` likely reaches Postgres as an actual read-only transaction, which
  would disallow `CREATE TEMP TABLE` — not verified against this app's actual `readOnlyMode`/datasource
  config; confirm before assuming either way.
- `POPULAR` excludes `FEATURED`'s already-returned IDs (`NoteService.java:955-956`), so the two calls
  are inherently sequential.

**Real options for whoever implements this:** (1) a CTE/single-statement rewrite computing the three
aggregates once, with `POPULAR`'s exclusion applied as a filter on top; (2) pull the three aggregates
once in Java and rank both sections in application code. Route through Codex (changed service logic).

**Durable follow-up, separate release:** an in-process Caffeine cache (none exists in `pom.xml` today)
for the three aggregates, TTL 60–300 s — safe given the backend's `instance_count` is confirmed flat at
1 outside deploy windows.

**Explicitly not recommended:** an index on `notes(copied_from_public)` — 83.6% selective, seq scan is
correct.

**Guard to write alongside it:** a test asserting the engagement-metric computation runs at most once
per `getPublicLibraryDiscoverySections` call.

### Leg E — exam-hub / subject-page walk concurrency (code, frontend — not confirmed, do not scope yet)

Finding §7: only a partial (4 of 5) match to one exam hub's roster, and the per-query cost is cheap and
index-backed regardless. Do not write a Codex prompt for this without a cleaner read first.

## Explicit non-fixes, with reasons

- **Do not shrink the Hikari pool's `maximum-pool-size`.** Pinned by the 2026-09-04 outage fix and
  `TomcatThreadPoolHikariAlignmentTest`.
- **Do not tune `minimum-idle` as a fix for the deploy overlap.** Corrected in Leg F — the new
  instance's ramp speed isn't the bottleneck; the old instance's delayed teardown signal is.
- **Do not add an index on `notes(copied_from_public)`.** 83.6% selectivity rules it out.
- **Do not scope Leg E work yet.** Not confirmed.

## Verification after any leg lands, and the kill criterion

1. `*terminated by signal 9*` count on the DB resource over the following 7 days. **Baseline: 9**
   (finding §9 — this file examined 9 distinct timestamps against the handoff's "8").
2. **For Leg F specifically:** at the next several deploys, pull the same shutdown-log timestamps as
   finding §2's table and the `active_connections` overlap-window peak as §3's table. This is a clean,
   repeatable, log-timestamp-level check, not an inference — compare the overlap-window memory peak
   against 268 MB each time.
3. `cpu_usage` daily peak against the 0.1 (or new) limit — informative for most non-deploy kills, not
   decisive for all (finding §4).
4. `get_metrics memory_usage` daily peak — currently 251–268 MB since 09-15.
5. Re-run finding §6's `EXPLAIN` after Leg B lands to confirm the parallel-worker node is gone.

**Kill criterion, stated before the read:** if the overlap-window memory peak (check 2) still regularly
exceeds ~250 MB after Leg A ships, Leg A's tier increase wasn't enough margin and the next step is
pursuing Leg F option 1 with Render directly rather than more query-level fixes. The non-deploy kills
(10-11, 10-12, 10-13's three 14:xx kills) are a separate question from the deploy-adjacent class and
should be tracked against Legs B/C/D, not Leg F.

## Constraints carried over from the handoff

- **RC-4's checkout baseline (started 2026-10-09) and RC-5's adoption-path read window are running.**
  None of the legs here touch checkout, pricing, or adoption-path instrumentation directly — but
  removing several crashes a week changes the environment both reads measure against. **Whichever leg
  ships, record its production deploy timestamp in the release notes** so RC-4 and RC-5 can split their
  baselines on it if their numbers move.
- Leg A is a cost decision outside code. Leg F is primarily a platform question now, demoted from a
  config change. Legs B and C are owner-run `SET`s with their tradeoffs stated. Leg D is a real backend
  change scoped to Codex. Leg E is not confirmed and should not be scoped.

## Decisions owed to the owner

1. **Leg A:** which plan tier, after confirming it raises the CPU limit.
2. **Leg F:** whether it's worth raising the old-instance teardown timing with Render, or treating the
   overlap as a fixed cost to be absorbed via Leg A's headroom instead.
3. **Leg B:** run the `max_parallel_workers_per_gather = 0` `SET`.
4. **Leg C:** run the `statement_timeout` `SET`, using `spring.flyway.init-sqls` to exempt Flyway.
5. **Leg D scope:** CTE rewrite vs. Java-side ranking, and whether to fold in the durable Caffeine cache
   now or defer it.
6. **Leg E:** not recommended for action yet.
