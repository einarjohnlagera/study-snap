# 2026-09-10 — production: HikariCP pool exhaustion → health-check starvation → restart

## Status: diagnosis only. No code, config or data changed. No release opened.

**Reported** by the owner: "server failure around 9:14pm" (21:14 +08 = **13:14 UTC**). Confirmed.
Backend **`v0.140.0`**, instance `srv-d6u0jkvgi27c73dvl9k0-v9znt`, DB `dpg-d6tvb8fkijhs73fda4m0-a`.

Claims are **VERIFIED** (a log line, a metric, a query result, or code opened) or **INFERRED**.

> **⚠️ THIS FILE WAS CORRECTED BY A COLD FALSIFICATION PASS AND THE CORRECTIONS ARE KEPT, NOT
> SILENTLY REPAIRED — see §0. The first draft contained a SELF-CONTRADICTION and a wrongly
> generalised code claim. Both are marked below.**

**⚠️ The mechanism is settled. The trigger is NARROWED but still not identified — §6.**

**⚠️⚠️ THIS HAS NOW RECURRED A FOURTH TIME — 2026-09-17, same signature, still unidentified. See §11.**

---

## 0. ⚠️ What the first draft got wrong

1. **It contradicted itself on telemetry.** §6 asserted *"no per-request telemetry exists"* while §1
   quoted `36 × 502, 10 × 500, 10 × 499, 153 × 200` — numbers that could only have come from
   `http_request_count` aggregated by `statusCode`, **which works**. What is missing is **per-PATH**
   data, which is a much narrower gap. Corrected in §7.
2. **It generalised a one-file grep into a claim about the codebase.** *"The unbounded reads are gone
   from the code"* rested on grepping `NoteService.java` alone.
   **`findByVisibilityOrderByUpdatedAtDesc` still exists at `NoteRepository.java:118`** (zero callers
   — dead code) and other unbounded anonymous reads survive elsewhere. **The conclusion still holds
   on other evidence; the method did not.** Corrected in §5.
3. **It never used app-side CPU, which it had already fetched.** That metric refutes *both* readings
   the draft called equally supported. Corrected in §6 — this is the single biggest change.
4. **`active_connections` detail was wrong.** It was **20**, becoming 21 only at 12:42 (and **40** at
   12:02, the deploy overlap the yaml comment at `:36-39` predicts) — not "flat at 21 throughout".
   The substance survives (§3); the detail did not.

---

## 1. Timeline (UTC) — all VERIFIED

| Time | Event |
|---|---|
| 12:01:41 | `v0.140.0` deploy finishes. ⚠️ **73 min before the incident — not a deploy event.** |
| 13:10:00.012 | `GenerationRecoveryJob` completes normally — pool healthy |
| **13:12:54.940** | **First pool timeout**, thread `analytics-1`: `timed out after 5001ms (total=20, active=20, idle=0, waiting=5)`; `analytics_event_persist_failed … PUBLIC_NOTE_VIEWED` |
| 13:12:55.341 | First Tomcat request thread times out; `unhandled_exception` |
| 13:12:55 → 13:13:50 | Sustained exhaustion, `active=20, idle=0, waiting=4–5` |
| **13:13:50.141** | `DataSourceHealthIndicator: DataSource health check failed` |
| 13:14:25.789 | `Commencing graceful shutdown` — **SIGTERM, shutdown hook ran** |
| 13:14:57.189 | `==> Instance srv-…-v9znt restarted` |
| 13:14:59.489 | `Starting BackendApplication v0.140.0` — **same version, same instance id** |
| 13:15:35.998 | `Started BackendApplication in 37.973 seconds` |

**Impact:** ~**70 s with no process**; total user-visible window ≈ **2 m 42 s**. In it: **36 × 502,
10 × 500, 10 × 499** (clients abandoning) against 153 × 200.

⚠️ **13:12:54 is the only steady-state pool exhaustion in 26 hours** — a
`["Connection is not available"]` search over 2026-09-09T12:00Z → 2026-09-10T14:00Z shows nothing
between 06:02:40 and 13:12:54. See §8 for what the other episodes were.

## 2. Mechanism — VERIFIED

The pool was exhausted and `DataSourceHealthIndicator` then starved on the **same** pool: it needs a
pooled connection to answer `/api/actuator/health`, could not get one, and the platform restarted an
instance whose only problem was that it was busy.

⚠️ **`connection-timeout: 5000` worked exactly as designed and did not prevent the restart.** Every
timeout reads ~5000 ms, so waiters failed fast rather than queueing — **and the instance was still
killed.** Third incident in a row where this leg converts an incident into an outage; it remains the
one open item from the 2026-09-04 finding.

## 3. Ruled out

- **OOM.** No `OutOfMemoryError` / `Java heap space`; a `SIGKILL` runs no shutdown hook and a graceful
  one ran. App memory peaked **753 MB against a 2 GB limit — 35 %**.
- **Deploy.** Last deploy finished 12:01:41 (`status: live`, none after); same version, same instance
  id on restart. ⚠️ Other restarts in the 26 h window minted **new** instance ids (`47kfg`, `r8zqr`,
  `rtrwz`, `4kl9q`), so the id match is real evidence rather than a tautology.
- **The database.** DB CPU **0.0075 baseline, 0.0626 peak**; memory 160 → 237 MB. Idle throughout.
  ⚠️ **`active_connections` is NOT evidence either way** — `minimum-idle` is absent from
  `application.yaml` *and* `application-prod.yaml`, so HikariCP defaults it to `maximum-pool-size`
  (20) and the pool holds its connections open permanently.
- **A scheduled job.** `list_services` (`includePreviews: true`) returns **exactly one resource** — no
  cron job, no worker. No `@Scheduled` fires at 13:12–13:13 (`GenerationRecoveryJob` `0 */10 * * * *`
  → 13:10; `NotificationCleanupJob` `:15`; `BulkGenerationResultCleanupJob` `:45`).
- **Application work.** `["action="]` over 12:40–13:14:30 returns zero rows. ⚠️ The token is not
  vacuous — the same search over 09-09 → 09-10 returns many (`action=complete_async_studyPack_generation`,
  `action=bulk_generate_batch`), most recently 08:54.

## 4. No connection was held ≥ 60 s — VERIFIED with a positive control

`leak-detection-threshold` is `60000` ms (`application.yaml:29`). A **26-hour** search on the
substring `["eak"]` (which catches `leak`, `Leak`, `ProxyLeakTask`, `Apparent connection leak`)
returns **one** unrelated hit (`RetentionEmailScheduler … weakConcept=0`).

⚠️ **Positive control, which the first draft lacked:** the same filter against **2026-09-05** returns
`11:43:34.991Z WARN ProxyLeakTask : Connection leak detection triggered`. **The detector demonstrably
fires in this environment**, so its silence today is a real negative, not a broken filter.

⚠️ **Contingent on one unknown:** the threshold is `${DB_POOL_LEAK_DETECTION_MS:60000}` and **Render
environment variables cannot be read with the available tools** (the only env tool is a write, which
is the owner's). If that variable is overridden in the dashboard, this conclusion weakens. Same
caveat applies to `DB_POOL_MAX_SIZE` and `SERVER_TOMCAT_THREADS_MAX` below.

## 5. The 2026-09-05 cause is closed — but NOT for the reason first given

**⚠️ The first draft's evidence was wrong; the conclusion survives on better evidence.**

**What is actually true (VERIFIED by reading the code):** `listPublicLegacy`
(`NoteService.java:872-925`) now clamps to `LEGACY_PUBLIC_LIBRARY_MAX_ITEMS` and calls
`findPublicLibraryRankedPageIds(…, 0, limit)`; the SEO path uses a `limit 1` query; discovery-sections
is bounded to six rows. `2a8234e9 — v0.125.0 Bounded Reads` is real.

**What is false:** *"`findByVisibilityOrderByUpdatedAtDesc` … no longer appears"*. It exists at
`NoteRepository.java:118` and `NoteCollectionRepository.java:69`. It has **zero callers** — dead code
— but the claim as written is wrong.

**⚠️ And unbounded reads reachable ANONYMOUSLY still exist**, which the first draft's method could not
have found:
- `NoteCollectionService.listPublic:239-294` → `findByVisibilityAndParentCollectionIdIsNullOrderByUpdatedAtDesc(PUBLIC)`
  with no `Pageable`, fanning out to four more queries. Reached via `NoteCollectionController:78-81`
  behind `SecurityConfig:60` `/collections/public/** permitAll`.
- `CourseProgramCatalogService:34` `findAll()` → `/course-programs`, `SecurityConfig:59`.
- `NoteService:1126-1145` → `/subjects`, `/tags`.

⚠️ **CALIBRATION — these are NOT this incident's cause, and the row should not be reopened.**
Production holds **5 public root collections, 36 public collections, 6,797 collection items**. Five
rows cannot exhaust a 20-connection pool. They are a latent shape worth a Backlog row on their own
terms, not an explanation for 13:12.

## 6. ⚠️ THE TRIGGER, NARROWED — app CPU refutes BOTH of the first draft's readings

The first draft called two readings equally supported. **App-side CPU, which it had already fetched
and never reasoned from, refutes both.** `cpu_limit` is **1 CPU**:

| 13:10 | 13:11 | **13:12** | **13:13** | 13:14 | 13:16 |
|---|---|---|---|---|---|
| 0.0014 | 0.0027 | **0.0204** | **0.0097** | 0.054 | 0.597 (restart) |

**During full pool exhaustion the app used 1–2 % of one core, and the database used 1–2 %.**

- ❌ **"Many short holds" — REFUTED by arrival rate.** Keeping 20 connections busy with sub-second
  holds needs ~40–60 req/s. Observed traffic is *tens of requests per 120-second bucket* (zero for the
  six buckets 12:50–13:10, then 24, then 96) — one to two orders of magnitude short.
- ❌ **"Holds spanning response serialization" — REFUTED by app CPU.** Twenty threads serializing JSON
  on a 1-CPU box would peg the limit. It sat at 1 %.

**✅ What survives, stated narrowly:** connections were held for **tens of seconds each**, across a
wait that consumed **neither app CPU nor database CPU**, with **every individual hold under 60 s**
(hence no leak fire despite the pool being pinned for ~66 s of wall clock). **That is blocking I/O
that is not the database.**

⚠️ OSIV is confirmed live in production — `13:15:36.222Z ConnectionLifetimeStartupLogger :
handling_mode=DELAYED_ACQUISITION_AND_HOLD … open-in-view=ON` — so an acquired connection is held
through the response write. That is the mechanism by which a non-DB wait pins a pooled connection.

**Two alternatives were tried and failed:** stale-connection validation churn (no `["validate"]` lines
13:05–13:14:40) and lock contention (no signal beyond the 10 pool timeouts themselves).

### ⚠️ A structural fact the first draft missed entirely

`server.tomcat.threads.max: 25` (`application.yaml:76`) **exceeds** `maximum-pool-size: 20` (`:43`).
Under OSIV, **~21 concurrent requests exhaust the pool regardless of how fast the queries are.**
`active=20` with `waiting=4–5` is consistent with the whole Tomcat thread pool being saturated — not
an exact identity, since one waiter was `analytics-1` rather than a request thread.

### The trigger evidence that does exist

`analytics_events` for 12:45–13:25 (read-only query): `PUBLIC_NOTE_VIEWED` — 12:59 = 1, **13:12 = 6,
13:13 = 13**, 13:14 = 4, 13:20 = 1, plus one `EXPLORE_VIEWED` at 13:13. **Every row has
`user_id IS NULL` — 100 % anonymous.** So a burst of anonymous public-note traffic arrived at an
instance that had served ~zero requests for the preceding 20 minutes.

⚠️ **But this does not explain the pool.** `NoteService.getPublicById:1148-1159` is
`findByIdAndVisibility` + `findLinkedStudyPack` + `mapToPublicDetail`, with analytics dispatched
asynchronously — cheap and bounded. **~20 note views do not pin 20 connections for a minute.**
Something other than these requests held them. (The counts also undercount, since `analytics-1` was
itself timing out.)

## 7. The telemetry gap — restated correctly

**What works:** `http_request_count` aggregated by `statusCode` (the source of §1's 502/500/499
counts) and by `host`.

**What does not:** log label `type` returns only `["app","build"]` (**no request logs**);
`http_latency` returns an **empty series**; `http_request_count` filtered by `httpPath` returns empty.

**So the specific missing thing is per-PATH and per-request-duration data** — which is exactly what
would name the blocking call in §6. Two cheap candidates:
1. Enable Render request logging for the service.
2. Log in-flight request paths when HikariCP saturation is detected — the pool already knows
   `active`/`idle`/`waiting`; what is never recorded is *which paths hold the connections*.

⚠️ **No fix plan accompanies this file — there is no identified cause to write one against.** §6
narrows the search to "a non-DB blocking wait inside a request under OSIV", which is a much better
starting point than the first draft had, but it is not a defect location.

## 8. Three earlier episodes — a DIFFERENT and separately fixable exposure

`total` far below 20, all **60–85 seconds after a `Starting BackendApplication` line**:

| When | Pool state |
|---|---|
| 2026-09-09T14:36:13 | `total=1, waiting=6` |
| 2026-09-09T14:36:15 | `total=6, waiting=7` |
| 2026-09-10T01:19:26 | `total=7, waiting=1` |
| 2026-09-10T06:02:40 | `total=6, waiting=3` |

⚠️ **The instance passes its health check and serves traffic while the pool holds only 1–7
connections** — it is declared ready before the pool has ramped. That is a distinct exposure, cheaper
to fix than §6, and unrelated to the 13:12 event. It also confirms 13:12 is the **only** steady-state
exhaustion in 26 hours, which strengthens the burst reading.

## 9. What could not be checked

- **Render environment variables** — no read tool is exposed, so `DB_POOL_LEAK_DETECTION_MS`,
  `DB_POOL_MAX_SIZE` and `SERVER_TOMCAT_THREADS_MAX` are assumed at their yaml defaults. ⚠️ A reader
  will notice thread `http-nio-10000-exec-29` against `threads.max: 25`; that is either Tomcat thread
  recycling over 70 minutes of uptime or an env override, and the two cannot be distinguished here.
- **Pool churn during the incident** — HikariCP logs the first "Added connection" at INFO and the rest
  at DEBUG while root level is INFO, so the absence of add/close lines 12:55–13:14 is weak evidence,
  not proof.
- **Which paths held the connections** — genuinely unavailable (§7).

## 10. Obligations

- This file needs a **Backlog Index row** (kickoff step 8 names `docs/claude-findings/`).
- ⚠️ **Do NOT raise `maximum-pool-size` in response to this.** It went 10 → 20 after 2026-09-04 and
  the same failure has now happened twice at 20. §6 shows holds are duration-bound, not
  throughput-bound, so a larger pool buys time proportional to nothing. ⚠️ **And note the interaction:
  raising the pool without lowering `threads.max` changes which of the two limits binds first.**
- The health-check-on-the-same-pool leg (§2) is still open from 2026-09-04 and is the one change that
  would have prevented the **restart** — though not the errors — in all three incidents.
- §5's still-unbounded anonymous reads and §8's ramp-window exposure each deserve their own Backlog
  row. Neither caused this incident.

## 11. ⚠️ RECURRENCE — 2026-09-17, fourth occurrence, identical signature

**Reported** by the owner: "the prod went down just now." Confirmed. **07 releases have shipped since
this file was written** (`v0.140.0` → `v0.148.0`, `v0.149.0`, `v0.150.0`, `v0.151.0` — current
backend at incident time). None of them shipped the telemetry recommendation from §7. This is the
**fourth** occurrence of this exact shape (2026-09-04, 2026-09-05, 2026-09-10, now 2026-09-17), and
the trigger is **still not identified** for the same reason: the same gap.

### Timeline (UTC) — all VERIFIED

| Time | Event |
|---|---|
| 2026-09-16 13:54–13:56 | Last deploy (`v0.151.0`) — **16 hours before the incident, not a deploy event** |
| 05:56:29 | First pool timeout, thread `o-10000-exec-98`: `total=20, active=20, idle=0, waiting=4` |
| 05:56:29 → 05:57:13+ | Sustained exhaustion, `waiting=4–5` throughout (log page truncated at 05:57:13; more warnings followed — not re-fetched, pattern already established) |
| 05:58:06.156 | `Commencing graceful shutdown` — **SIGTERM, shutdown hook ran** |
| 05:58:06.210 | `Graceful shutdown complete` — **54 ms**, i.e. no requests were in flight by then (contrast §1's 32 s on 09-10, where they were) |
| 05:58:07.449 | `HikariPool-1 - Shutdown completed` |
| 05:58:09.087 | `==> Instance srv-…-5hddm restarted` — **same instance id** |
| 05:58:12.241 | `Starting BackendApplication v0.151.0` — **same version** |
| 05:58:46.132 | `Started BackendApplication in 35.269 seconds` |

**Impact:** instance_count reads 0 for the 05:58–05:59 buckets; 15 × 500, 11 × 499, 2 × 502 in the
05:58 bucket against 78 × 200. Shorter than 09-10's window — total user-visible impact roughly
90 seconds.

### Every discriminating check from §3, §4 and §6 repeats identically — VERIFIED again

- **OOM ruled out**: graceful shutdown ran; memory peaked 722 MB of the 2 GB limit (36%).
- **Deploy ruled out**: last deploy 16 h earlier, `status: live`, same version and instance id on restart.
- **No leak fired**: a 20-minute window (05:40–06:00) search on `["eak"]` — the same substring that
  positively controlled against 2026-09-05 in §4 — returns **zero rows**. No hold ≥ 60 s.
- **Database ruled out as the constraint**: DB CPU baseline 0.008, peaking only to **0.063** during
  the exhaustion window itself (05:56–05:58) — the same low ceiling as 09-10's app-CPU finding, this
  time confirmed on the **database** side directly. `active_connections` sat at 20 throughout — same
  §3 caveat applies: this is `minimum-idle` defaulting to `maximum-pool-size`, not a saturation signal.
- **No application activity logged**: `["action="]` over the full 05:40–06:00 window returns **zero
  rows** — no generation, no bulk regeneration, no adoption. Same as 09-10.

**⚠️ Not re-collected this time, and flagged rather than silently skipped:** app-side CPU during the
exhaustion minutes (§6's decisive discriminator against "many short holds" and "response
serialization"), and the `analytics_events` anonymous-traffic check (§6's `PUBLIC_NOTE_VIEWED` burst).
Both would need a fresh pull to confirm they repeat; this entry reports what was checked, not what
was assumed to still hold. One line **is** already consistent with §6's surviving explanation: the
`analytics-1` thread itself timed out at 05:57:06 trying to persist a `PUBLIC_NOTE_VIEWED` event
(`analytics_event_persist_failed`) — the identical failure-mode fingerprint as 09-10's Exhibit,
though on n=1 this is suggestive, not confirmatory.

**A genuinely new avenue was opened and explicitly NOT completed**, so it is not confused with a
finding: a search for synchronous external HTTP calls callable from a request thread (candidates: a
transactional email dispatch, a payment-provider call) that could hold an OSIV connection for tens of
seconds while using near-zero CPU on both app and DB — exactly §6's surviving shape. A broad grep
(`RestTemplate`/`WebClient`/`.execute(`) returned too many false positives (local executor dispatch,
not external HTTP) to be useful, and a targeted follow-up on the email service found no direct
callers under the searched name. **This is an unverified lead, not a mechanism** — flagged so the next
pass does not have to reconsider whether it was already tried.

### ⚠️ The obligation this recurrence actually raises

**Four incidents. Zero of the seven releases in between shipped §7's recommendation** (Render request
logging, or a saturation-triggered dump of in-flight request paths). Every other §10 non-fix
(don't raise the pool, don't touch `open-in-view`, don't add PgBouncer) has correctly held — this is
not a case of the wrong fixes being tried. It is the **one right fix never being scheduled.** This is
the same shape `CLAUDE.md` names directly: *"convert every recurring finding into an automated guard,
because a guard is the only check that costs nothing to re-run"* — except here the guard itself
(the telemetry) has not shipped four occurrences in, and each occurrence currently costs a fresh,
unproductive investigation that re-derives the same "narrowed, not identified" conclusion.
**Recommend this becomes an explicit, scoped release item rather than a recurring incident-response
cost** — the fix is enabling a platform feature and/or a few lines of Hikari-saturation logging, not
a redesign, and it is now cheaper than the fifth investigation will be.
