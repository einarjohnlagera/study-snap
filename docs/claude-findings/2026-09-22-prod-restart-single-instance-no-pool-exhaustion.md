# 2026-09-22 — production: single-instance restart, ~44s outage, mechanism NOT identified

## Status: diagnosis only. No code, config or data changed. No release opened.

**Reported** by the owner: "prod went down again." Backend `notelib-backend-prod`
(`srv-d6u0jkvgi27c73dvl9k0`), instance `srv-d6u0jkvgi27c73dvl9k0-dvnkf`, `v0.156.0`.

Investigation was read-only (Render MCP: logs, metrics, deploy history). Claims below are **VERIFIED**
(a log line or a metric) or marked **NOT VERIFIED** where the instrument doesn't exist.

**⚠️ This file's own primary claim is negative and stated as such: the restart does NOT show the
HikariCP pool-exhaustion → health-check-starvation signature documented four times in
`2026-09-10-prod-pool-exhaustion-trigger-unresolved.md` (2026-09-04, -05, -10, -17). This is
recorded as a discriminator against that pattern, not folded into it as a fifth occurrence.**

---

## 1. Timeline (UTC) — all VERIFIED

| Time | Event |
|---|---|
| 10:55:25 | Last deploy (`v0.156.0`) finishes, `status: live`. **~2h55m before the incident — not a deploy event.** |
| 13:40:00 / 13:45:00 | `GenerationRecoveryJob` / `BulkGenerationResultCleanupJob` complete normally, all-zero — no stuck work |
| 13:49:25.293 | An unrelated `unhandled_exception` — `HttpMessageNotWritableException: … Broken pipe` while writing a JSON response. A client dropped its own connection mid-response; this is **not** a server-side failure and precedes the shutdown by 34s |
| **13:49:59.711** | `Commencing graceful shutdown` — SIGTERM received, shutdown hook ran |
| 13:49:59.717 | `Graceful shutdown complete` — **6 ms**, i.e. no requests in flight |
| 13:49:59.746 → 13:50:00.635 | HikariPool-1 shutdown initiated → completed |
| 13:50:01.602 | `==> Instance srv-…-dvnkf restarted` — **same instance id, no new deploy** |
| 13:50:04.886 | `Starting BackendApplication v0.156.0` — **same version** |
| 13:50:43.223 / 13:50:43.241 | Tomcat started / `Started BackendApplication in 39.818 seconds` |

**Impact:** `instance_count` reads **0** for the 13:49 and 13:50 metric buckets. HTTP status counts in
that window: **6 × 502, 2 × 499** against 35/85/21 × 200 in the surrounding minutes — consistent with
~44 seconds of no live instance plus cold-start latency. Traffic was ordinary (single-digit-to-low-double-digit
requests/minute); no volume spike precedes the restart.

## 2. Ruled out

- **OOM.** No `OutOfMemoryError`; a `SIGKILL` runs no shutdown hook, and an orderly one ran (Tomcat
  drain → JPA `EntityManagerFactory` close → Hikari shutdown, in that order). Memory usage was flat at
  **~516–530 MB** for the 50 minutes leading up to the restart, against a **2,147,483,600-byte
  (~2 GB) `memory_limit`** — never above ~26% of the container limit. The single 692 MB reading at
  13:50 lines up exactly with the ~40s window where the old and new processes briefly coexisted during
  restart, not a leak; usage dropped to 434 MB immediately after.
- **CPU saturation.** `cpu_limit` is 1 core; usage stayed at 0.001–0.08 throughout, only rising to 0.65
  in the minute *after* the restart (JIT warm-up / traffic catch-up).
- **A deploy.** `list_deploys` shows nothing between 10:55:25 (v0.156.0, live) and well after this
  incident. Same version, same instance id on restart — a deploy always mints a new instance id in this
  service's history (see §4).
- **The HikariCP pool-exhaustion pattern from the four prior incidents.** Searched
  `["Connection is not available", "HikariPool", "health check failed", "waiting="]` over
  13:20:00–13:50:00 UTC: the **only** hit is the routine `HikariPool-1 - Shutdown initiated...` line
  that is part of every graceful shutdown. There is no pool timeout, no `active=N, waiting=N`, no
  `DataSourceHealthIndicator` failure logged before the shutdown — all of which were present and
  central to every one of 2026-09-04, -05, -10 and -17. **This restart's own health check was not seen
  failing; the app went from serving traffic normally to receiving SIGTERM with no logged precursor.**

## 3. What this leaves — NOT identified

Unlike the four prior incidents, there is **no application-log evidence of what triggered the SIGTERM**.
The shutdown looks platform-initiated (SIGTERM → graceful hook → restart, not a crash), but nothing in
the app logs or the metrics this session can read explains *why* the platform sent it. Two candidates,
neither confirmed or ruled out here:

- A Render-side health-check failure with a precursor this session's log query window/filters didn't
  catch (e.g. a single missed probe rather than the sustained pool-starvation pattern of the other four).
- A platform-level action unrelated to this app's health (host maintenance, instance recycle) — Render's
  own event/incident log would settle this and was not read; **no read tool for that exists in this
  session's toolset.**

**This is explicitly not rounded up to either.** Recording it as unidentified is more useful than
guessing, per the standard this directory already holds itself to.

## 4. The owner's question: is `-XX:MaxRAMPercentage=50.0` implicated?

**No supporting evidence, on two independent grounds:**

1. **Memory headroom.** The flag caps JVM heap at 50% of the 2 GB container limit (~1 GB). Actual
   process memory sat at ~516–530 MB the entire time — roughly half the *heap* cap alone, let alone the
   container limit. No memory-pressure signature (no GC-thrash indicators, no climbing baseline, no
   `OutOfMemoryError`) precedes the restart.
2. **The flag predates the pattern it's being asked about.** `Picked up JAVA_TOOL_OPTIONS:
   -XX:MaxRAMPercentage=50.0` appears on every instance start this session could retrieve, back to the
   earliest log line available (2026-09-16T08:11:26Z — log retention does not reach further back;
   queries before 2026-08-24 returned zero log lines of any kind, so the flag's true introduction date
   could not be dated). Non-deploy restarts were **already occurring** on 2026-09-16 and 2026-09-17
   (see §5) — before or concurrent with whenever this flag was actually added, per the owner's own
   account that it was added "when our server is struggling on the base plan." If anything, non-deploy
   restart frequency looks lower in the days immediately before this incident (one on 09-21, one today)
   than in the 09-16/09-17 window, which is consistent with the flag having helped with what it was
   added for, not with it causing this.

**Secondary observation, not a finding:** the service is now on the `standard` plan (2 GB), not the
`base` plan the flag was reportedly tuned for. A 50%/~1 GB heap cap is conservative rather than tight
at this plan tier — no downside to leaving it, but also not doing any work relevant to this incident's
mechanism.

## 5. Other non-deploy restarts seen in the retained log window — NOT individually investigated

Listed for context only; each entry is a `Commencing graceful shutdown` → `Instance … restarted` pair
found by log search, not a re-investigation. Two (09-17 05:58, and implicitly its pattern) are already
covered by `2026-09-10-prod-pool-exhaustion-trigger-unresolved.md`; the rest are **not**, and this file
does not claim to know whether they share that mechanism, today's (no precursor found), or something else:

| Date (UTC) | Restart time | Deploy-correlated? |
|---|---|---|
| 2026-09-16 | 08:13, 12:38, 13:57 | No |
| 2026-09-17 | 05:58 (documented, pool exhaustion), 06:37 | No |
| 2026-09-18 | 03:28, 14:48 | No |
| 2026-09-18 | 16:04, 16:05 | Yes — `v0.154.0` deploy window |
| 2026-09-21 | 14:07 | No |
| 2026-09-22 | 06:04 | Yes — `v0.155.0` deploy window (see §6) |
| 2026-09-22 | 10:56 | Yes — `v0.156.0` deploy window |
| 2026-09-22 | 13:49 | No — **this incident** |

**Seven non-deploy restarts in the retained window, of which only one (09-17 05:58) has a documented
mechanism.** This is a higher rate of unexplained single-instance restarts than the existing findings
files' framing ("fourth occurrence," implying a slow-growing count) suggests — see §7.

## 6. A separate, distinct pattern noticed in passing — post-deploy DB connection resets

Not this incident, and not investigated further here, but worth flagging since it surfaced in the same
log pull: both of today's two deploys (06:04 and 10:56 restarts, i.e. `v0.155.0` and `v0.156.0` going
live) are immediately followed by a burst of
`org.postgresql.util.PSQLException: An I/O error occurred while sending to the backend` —
in-flight queries losing their DB connection during the instance swap, producing several 500s each
time. This is a **deploy-swap** artifact (old instance's pool connections dropped mid-query), mechanically
different from both §2's ruled-out pool exhaustion and from today's unexplained 13:49 restart. Flagged
here so it isn't lost; not sized or root-caused.

## 7. What could not be checked

- **Render's own platform/incident event log** — no read tool exists for it in this session. This is
  the one instrument that could directly confirm or refute "platform-initiated restart" for §3.
- **Render environment variables** — no read tool exists (only a write tool, which is the owner's to
  use); could not directly confirm `JAVA_TOOL_OPTIONS` or any Hikari/Tomcat override values beyond what
  the startup banner logs.
- **Log history before 2026-08-24** — queries against that range return zero log lines, so the true
  first appearance of `MaxRAMPercentage=50.0` and the true onset date of the non-deploy-restart pattern
  are both bounded, not dated exactly.

## 8. Obligations

- **This file needs a Backlog Index row** in `ROADMAP.md` (kickoff step 8 names `docs/claude-findings/`).
- **§5's six undocumented non-deploy restarts** (09-16 ×3, 09-17 06:37, 09-18 ×2, 09-21) deserve either
  a follow-up read against Render's platform event log (if that ever becomes available as a tool) or an
  explicit decision that they're not worth individually diagnosing. Left open here rather than assumed.
- **§3 is unresolved.** If this recurs, the highest-value next step is the same one
  `2026-09-10-prod-pool-exhaustion-trigger-unresolved.md` §7 already recommended and no release has
  shipped: request-level telemetry (Render request logging, or an in-flight-request-path dump on
  saturation) — the gap that makes every one of these restarts, including this one, un-attributable
  from application logs alone.
- **§6's post-deploy DB connection reset pattern** is unsized and untriaged — a candidate for its own
  finding or Backlog row if it recurs on a future deploy.
