# Fix plan — pool exhaustion, fourth occurrence: ship the instrumentation, stop hunting the trigger

**Written by:** Prod Investigator session, 2026-09-17, on request from a peer session ("Release
Implementor") relaying the owner's report that prod goes down almost daily. **For:** the implementing
session — this is a plan, not a diff; no code was changed to produce it.

**Evidence:** `docs/claude-findings/2026-09-10-prod-pool-exhaustion-trigger-unresolved.md`, especially
**§11** (added today, documenting this exact occurrence — timeline, every discriminating check
re-run, all repeating identically). Read §11 before this plan; this plan does not repeat that evidence.

---

## 0. ⚠️ The framing this plan follows, and why it differs from the last three writeups

`ROADMAP.md`'s Backlog Index carries `[CHECKPOINT — due 2026-09-17]` on this exact issue, with a
stated kill criterion: **if a fourth steady-state exhaustion occurs before the trigger is named, stop
trying to identify it from existing telemetry and ship the missing instrumentation instead.**

Today's incident (05:56:29–05:58:46 UTC, §11) is that fourth occurrence, on the identical signature as
2026-09-04, 2026-09-05 and 2026-09-10. **The kill criterion has fired.** This plan does not attempt a
fifth root-cause investigation — three prior investigations plus a cold-agent falsification pass
already narrowed the mechanism as far as the existing telemetry allows (§6 of the source file: a
non-DB, non-CPU blocking wait inside a request, under 60 seconds, under OSIV). Re-running that
analysis a fourth time would reliably reproduce the same "narrowed, not identified" conclusion, at
the same cost, for the fourth time. **The obligation this recurrence raises is process, not analysis**
(§10 of the source file): the fix was already named — it just hasn't been scheduled across seven
releases (`v0.141.0` → `v0.152.0`).

---

## 1. The owner's specific question — answered directly

**"Could this be a docker-compose / app-config issue rather than a Render platform issue?"**

**No — ruled out. Docker Compose is not part of the production runtime at all.** Verified by reading
both files:

- `docker-compose.yml` (repo root) is a **local-development-only** convenience wrapper: hardcoded
  `localhost` values, a local Postgres container on port `5433`, `CORS_ALLOWED_ORIGINS:
  http://localhost:3000`. It matches `CLAUDE.md`'s documented local-dev instructions exactly and is
  never referenced by the Render service.
- Render's service config (read earlier in this investigation) is `env: docker`,
  `dockerfilePath: Dockerfile`, `dockerContext: .` — Render builds and runs `backend/Dockerfile`
  **directly**, under Render's own container orchestration, restart policy, and health-check loop.
  docker-compose is never invoked in the deploy path.

**So "app config" in the sense that matters is real, but it isn't docker-compose** — it's
`application.yaml` / `application-prod.yaml` (Hikari pool size, Tomcat thread cap) and the
Dockerfile's `JAVA_OPTS` (heap, GC tuning), all of which apply identically with or without
docker-compose existing on a laptop. **This is confirmed to have already had deliberate,
documented iteration** — the Dockerfile carries a dated comment block on an "ongoing" memory
investigation (G1 heap-uncommit tuning, glibc arena capping) that is a *different*, already-addressed
problem from the one in this plan. Nothing there points at docker-compose, and nothing needs undoing.

---

## 2. Settled facts — do NOT re-derive (carried from the source file, restated for this plan alone)

1. Mechanism: HikariCP pool exhaustion (`active=20/20`) → `DataSourceHealthIndicator` starves on the
   same pool → Render restarts an instance whose only problem was that it was busy.
2. Ruled out, four times independently: OOM, a bad deploy, the database itself, a scheduled job.
3. No connection is ever held ≥ 60 seconds (leak detector never fires) — so this is **not** the
   2026-09-05 defect (that one is closed, `v0.125.0`, verified in code).
4. `server.tomcat.threads.max: 25` **exceeds** `spring.datasource.hikari.maximum-pool-size: 20`
   (`application.yaml`) — under `spring.jpa.open-in-view: true` (also confirmed on), roughly 21
   concurrent requests exhaust the pool **regardless of query speed**. This is a real, structural,
   independently-true fact — it does not by itself prove it caused any of the four incidents, but it
   is a live exposure regardless.
5. **No per-path request telemetry exists on this service.** `list_log_label_values` for `type`
   returns only `["app","build"]`; `http_latency` returns an empty series; `http_request_count`
   filtered by `httpPath` returns empty. This is the single fact every occurrence has run into.

---

## 3. Scope — two independent legs

### Leg A — ship the missing instrumentation (the kill criterion's own directive; do this first)

Two pieces, genuinely independent of each other:

- **A1 — Render platform request logging.** ⚠️ **This may not be a code change at all** — it is
  plausibly a Render dashboard setting (Settings → Logs, or a plan-tier gate) rather than something a
  PR can turn on. **This is an OWNER action to check, not an implementation task** — confirm in the
  Render dashboard whether per-request logging (path, status, duration) can be enabled for this
  service, and if so, enable it. If it requires a plan upgrade, that is the owner's call, not this
  plan's to make.

- **A2 — Hikari-saturation-triggered diagnostic logging (this is the real, buildable deliverable).**
  When the pool is saturated (`activeConnections >= maximumPoolSize` and
  `threadsAwaitingConnection > 0`, sustained), log which request paths are actually holding the
  connections at that moment. Concretely:
  - Capture the current request's method + URI into a lightweight registry keyed by thread
    (a `ThreadLocal` set in a servlet filter at request entry, cleared at exit — the same place
    `RequestIdFilter` already runs, so this can likely sit beside it).
  - Poll `HikariPoolMXBean` (via `HikariDataSource.getHikariPoolMXBean()`) on a short interval (e.g.
    every 2–5 s via a `@Scheduled` check, or a Micrometer gauge with a threshold alert) for
    `getActiveConnections() >= getTotalConnections()`.
  - On sustained saturation, log the registry's current contents: every live Tomcat request thread's
    path and elapsed time since entry. **This is the one thing that would have answered every one of
    the four incidents on the spot**, instead of leaving "narrowed, not identified" as the final
    answer each time.

⚠️ **A2 must not become a general APM integration.** The scope is exactly: detect saturation, log
in-flight paths. Nothing more elaborate — this is diagnostic instrumentation for a specific, recurring
failure, not a new observability platform.

### Leg B — close the structural Tomcat/Hikari mismatch (§2.4)

Lower `server.tomcat.threads.max` to at or below `spring.datasource.hikari.maximum-pool-size`
(i.e., ≤ 20), rather than raising the pool. This does **not** identify or fix whatever is actually
holding connections for tens of seconds — it closes a *different*, independently-real way an ordinary
traffic burst (no bug, no long hold, just concurrency) can exhaust the same pool on its own. Cheap,
safe, and correctly scoped: it removes one confirmed exposure without touching the pool size the prior
investigation already forbade raising.

---

## 4. Explicit non-fixes — carried forward, do not re-propose

- ⚠️ **Do NOT raise `maximum-pool-size`.** Already raised once (10→20) after 2026-09-04; the identical
  failure has now recurred three more times at 20. The holds are duration-bound, not
  throughput-bound — a bigger pool buys time proportional to nothing.
- ⚠️ **Do NOT touch `spring.jpa.open-in-view`.** Real blast radius, and the prior investigation
  explicitly wants a staging run before that change — this incident does not change that calculus.
- ⚠️ **Do NOT add PgBouncer.** Standing rule — it addresses too-many-clients, not
  connections-held-too-long.
- ⚠️ **Do NOT chase the "synchronous external call" lead from §11 without new evidence.** It was
  opened and explicitly not confirmed (a broad grep was too noisy, a targeted follow-up found no
  direct callers under the searched name). It is not ruled out either — but Leg A2 is what would
  actually confirm or kill it on the next occurrence, which is a better use of effort than another
  manual code search.
- ⚠️ **Do NOT let A2 block on A1, or vice versa.** They are independent; ship whichever is ready first.

---

## 5. Decisions owed to the owner before implementation

1. **A1** — can Render's per-request logging be enabled for this service/plan? Only the owner can
   check the dashboard and act on it (env/deploy/plan actions are owner-only, per standing rule).
2. **Leg B timing** — ship the threads.max correction now as a fast, low-risk fix, or bundle it with
   Leg A in the same release? Either is defensible; it does not depend on A2 being built first.

---

## 6. Pre-declared guards

⚠️ Same repo-wide lesson as ever: a diff that changes behaviour with no test exercising it has shipped
as a silent no-op twice already. These are written so a fixture cannot pass under both "the
instrumentation exists" and "the instrumentation actually fires."

- **A2, discriminating guard:** a test that actually saturates a small test Hikari pool (fill it with
  held connections up to `maximumPoolSize`, leave one thread waiting) and asserts the saturation log
  line **fires and names the blocking path** — not just that the detector method exists or compiles.
  A guard that only checks the MXBean is wired up, without ever driving it to a saturated state,
  passes under a detector that never actually triggers.
- **A2, false-positive guard:** assert the detector does **not** fire during ordinary, non-saturated
  load (e.g., a handful of concurrent requests well under the pool size) — a detector that logs
  constantly is as useless as one that never fires.
- **Leg B, regression guard:** confirm the application context still starts, and that a burst of
  ~20 concurrent requests queues at the Tomcat acceptor rather than erroring, after lowering
  `threads.max`. This is the check that would catch "lowered the wrong number" before it ships.

---

## 7. Verification tier and routing (recommendation, not a ruling)

- **Leg A2** touches connection-pool internals on a service with a four-incident production reliability
  history. It does not strictly trigger any of the named full-pressure-test gates (no authorization
  boundary, no cross-user read, no money/quota semantics) — but given the recurring-incident context,
  **recommend at least one scoped cold agent, falsification-framed**: hand it this plan plus §11's
  evidence and ask it to disprove that the saturation detector actually fires under load and doesn't
  false-positive under normal traffic. This is a judgment call for whoever kicks off the release, not
  a hard gate requirement.
- **Leg B** is a one-line config change with a clear regression guard — a single `advisor()` call on
  the diff is enough.
- **Routing: CODEX** for A2 (backend service + filter + config, anti-drift care around not scope-creeping
  into a general APM layer). **Routing: CLAUDE CODE inline** for Leg B (one YAML line, existing pattern).

---

## 8. Version, branch, and the checkpoint row

`v0.152.0` is **signed off and Released** as of today. **The next version has not been kicked off.**
This work belongs in that next kickoff (`v0.153.0` or whatever it is numbered) — not retrofitted into
the closed `v0.152.0`.

⚠️ **At that kickoff, step 8/9's Backlog Index scan must update the
`[CHECKPOINT — due 2026-09-17]` row.** Its kill criterion has fired (this is the fourth occurrence);
the row should be corrected to reflect that the scope has changed from "identify the trigger" to
"ship the instrumentation that would identify it," per this plan, rather than left reading as an
open, undated investigation. Do not mark it resolved until Leg A2 has actually shipped and fired at
least once (in a test, per §6, since a fifth production occurrence is not something to wait for).

---

## 9. Obligations at signoff (whichever release carries this)

- Update the Backlog Index row per §8.
- Update `docs/claude-findings/2026-09-10-prod-pool-exhaustion-trigger-unresolved.md` with a closing
  note once A2 ships, and again if/when it actually names a trigger on a future occurrence — the
  investigation isn't closed, it's instrumented.
- If Leg A2 does capture a real trigger on a subsequent occurrence, that is a **new, separate**
  findings file — do not retrofit the identified cause into the "trigger unresolved" file's title,
  per this repo's own rule about titles decaying with the content they describe.
