# 2026-09-04 — production outage: HikariCP pool exhaustion → health-check failure → platform restart

## Status as of 2026-09-04: diagnosis only. Nothing fixed, no code or config changed, no release opened.

Source: `docs/20260904_prod-issue-down.log` (763 lines, 05:55:07–05:57:00 UTC), plus a code read at
`v0.110.2`. Every `file:line` below was opened; claims that were **not** verified are labelled as such.

> **⚠️ THIS FILE CONTAINS ONE RETRACTED FINDING AND ONE OPEN HYPOTHESIS. Both are marked. Do not cite
> §6 as a defect or §7 as a conclusion.** The header of the previous incident file in this directory
> went stale against its own body; if the state below changes, correct this block rather than leaving it.

---

## 0. ⚠️ COLD RE-READ, 2026-09-04 — SIX CLAIMS BELOW ARE CORRECTED. READ THIS FIRST.

A cold investigator re-mined the 763-line log with no inherited context. **Pool exhaustion itself
stands** — `waiting=15` at L379 and a request that could not open a transaction at all (L381) are
direct evidence the pool was dry. What follows corrects the NARRATIVE and EVIDENTIAL overstatements
around it. Each was verified independently before being recorded here.

1. **"Render REPLACED the instance" → it RESTARTED it, and there was NO deploy.** The only platform
   line in the file is `L732: ==> Instance srv-…-q9cbh restarted`. There is no `Deploying`, no build
   output, and the new JVM reports the **same version** (`L730: Starting BackendApplication v0.110.2`).
   **It was also not a rolling replacement** — `L721` Hikari shutdown completed at 05:56:28.939 and
   `L730` starts at 05:56:33.334: **a 4.4-second window with no process at all.** ⚠️ §8a's
   deploy-overlap pool ceiling is still correct *for deploys*; it simply was not this event's shape.
   **Corroborated independently from git: the last merge to `main` before the outage was `e513f867` at
   03:07:21 UTC — 2 h 48 m earlier.**
2. **"It was KILLED FOR failing a health check" is an INFERENCE printed under a heading that says it is
   not.** The log contains **no Render line attributing the restart to anything**. ⚠️ **A MANUAL
   RESTART BY THE OWNER FITS EVERY OBSERVATION AND WAS NEVER CONSIDERED** — SIGTERM → shutdown hook →
   graceful shutdown → `restarted`. OOM stays ruled out (an OOM `SIGKILL` runs no shutdown hook, and
   L717-721 show an orderly one).
   **⚠️ ANSWERED 2026-09-04 — THE OWNER DID NOT RESTART IT MANUALLY. That branch is CLOSED**, which
   leaves a Render-side action (health-check auto-restart being the leading candidate) as what
   remains. **⚠️ THIS RAISES CONFIDENCE IN §1's CLAIM WITHOUT PROVING IT: eliminating the one
   alternative anybody named is not evidence FOR the survivor, and the log still attributes the
   restart to nothing.** Render's own event history for 05:55-05:57 UTC would settle it outright and
   has not been read. Until then it stays an inference — better supported, still not a mechanism.
3. **"Zero INFO lines before the restart / WARN-ERROR-filtered" is FALSE, and this one matters
   evidentially.** `L717`/`L718` are INFO graceful-shutdown lines *before* `L730`, and
   `application-prod.yaml:10` sets root level **INFO** in production. **So the log is NOT
   level-filtered.** §4 used that filtering as the reason absent generation-thread activity could be
   waved off — **that reason does not exist.** The correct statement is weaker and different: a Spring
   Boot app emits nothing at INFO while serving requests, so a quiet INFO window carries **no
   information either way.**
4. **The `study-pack-generation-` search could not have matched what it looked for.** Spring Boot's
   console pattern is `[%15.15t]` — **15 characters, truncated from the FRONT** (confirmed in-file:
   `ionShutdownHook` is `SpringApplicationShutdownHook`; `io-10000-exec-7` is
   `http-nio-10000-exec-7`). `study-pack-generation-1` therefore renders as **`ck-generation-1`**, and
   a literal grep for the full prefix can never match in any log. ⚠️ **The conclusion survives the bad
   method** — a re-search on truncated forms found no generation thread either, and `llm-parallel-1` is
   14 chars so *would* render in full. It remains absence of evidence.
5. **"Saturation began ~05:54:43" → NO LATER THAN 05:54:43.** The arithmetic is right, but **the file's
   first line IS 05:55:07.057**, so anything earlier is outside the excerpt. Independent support that
   it started earlier: the first broken pipe (`L2`, 05:55:08.006) means a client had **already given
   up**, 6.5 s before the first logged pool timeout.
6. **"Nothing recovered on its own" is NOT ESTABLISHED, and the evidence leans mildly the other way.**
   65 s of total silence (L716 05:55:23.456 → L717 05:56:28.272); `waiting` fell **15 → 11** with
   **zero** further logged timeouts although `GlobalExceptionHandler:92` logs every unmapped exception;
   and graceful shutdown completed in **20 ms**, which Spring only reports when in-flight requests reach
   **zero**. ⚠️ **BUT THE LOG CANNOT DISTINGUISH RECOVERY FROM DE-ROUTING** — Render pulls a failing
   instance from the load balancer, and clients were demonstrably abandoning requests. So the honest
   form is *"not established"*, not *"it recovered."*

**⚠️ THE MOST CONSEQUENTIAL FINDING IS ABOUT §5, NOT §1 — SEE `docs/claude-plans/v0.112.0-outage-falsification-read.sql`.**
All five falsification queries read tables that record **writes**. **None can detect an anonymous READ
burst**, so "zero rows refutes it" is wrong — it would merely fail to see it. The one path the log
actually names as starved (`NoteCollectionService.listPublic:206`) is `permitAll`, unpaginated and
read-only, and `revalidate = 300` means public endpoints are re-fetched every ~5 minutes independent of
any deploy. **If that is the cause, Phase 3 is aimed at the wrong target.** The decisive evidence is
Render request-rate data, not Postgres.

## 1. What happened — mechanism, not guesswork

The pool was exhausted, and **the health check then starved on the same pool**:

```
05:55:14  HikariPool-1 - Connection is not available, request timed out after 30001ms
          (total=10, active=10, idle=0, waiting=15)
```

| Time (UTC) | Event |
|---|---|
| 05:55:07 | `DataSourceHealthIndicator` already degraded — **23,672 ms** to respond |
| 05:55:08 / 05:55:14 | two `unhandled_exception`s (client aborts / broken pipe) |
| 05:55:14 | pool acquisition timeout — `active=10, idle=0, waiting=15` |
| 05:55:18 / 05:55:20 | health check 25,533 ms / 12,707 ms |
| **05:55:23** | **`DataSource health check failed` at 30,002 ms** |
| 05:56:28 | `Commencing graceful shutdown` — ~65 s after the health check failed |
| 05:56:33 | restart, `BackendApplication v0.110.2` |

**The server did not crash. It was killed for failing a health check it could no longer answer.**
Spring Boot's `DataSourceHealthIndicator` needs a pool connection to serve `/actuator/health`; once the
pool was empty it queued behind the same 30 s acquisition timeout as every other request, Render's probe
failed repeatedly, and the platform replaced the instance. The shutdown was **graceful** (`ionShutdownHook`,
`GracefulShutdown`), with no OOM and no error preceding the exhaustion — consistent with a probe-driven
restart rather than a fault.

**Nothing in the application recovered on its own.** The restart ended the incident.

## 2. The configuration finding

**There is no HikariCP configuration anywhere** — not in `application.yaml`, not in `application-prod.yaml`.
No `maximum-pool-size`, no `connection-timeout`, no `leak-detection-threshold`.

- `total=10` is Hikari's **default** `maximumPoolSize`. Nobody chose 10.
- `30001 ms` is the **default** `connectionTimeout`.
- `server.tomcat.threads.max: 25` (`application.yaml:35`) — deliberately sized for Render's 0.5 CPU.

**So the ratio is 25 request threads against 10 connections**, and `active=10 + waiting=15 = 25` is exactly
the thread cap: at the moment of the timeout, **every Tomcat thread was holding or waiting for a connection.**
That is consistent with request threads being the holders; it is **not proof**, because Hikari's `waiting`
does not record thread origin and the health-check threads are Tomcat threads too.

**Render was never the constraint.** Render Postgres allows ≥100 connections on every plan (100 up to
1c-4g; 200 at 2c-4g/2c-8g; 400/500 above), minus ~10 reserved. The app was configured for 10.

## 3. Root cause class: transactions held across slow external calls

**Fourteen code paths hold a JDBC connection across an OpenAI call.** The LLM read timeout is **180 s**
(`config/OpenAiLlmConfig.java:26`), and parallel fan-out is capped at **240 s**
(`service/impl/OpenAiLlmStudyPackService.java:1968`) — against a **30 s** acquisition timeout. One request
can pin a connection for three to four minutes while others time out waiting.

**Seven concurrent synchronous generation requests exhaust a pool of ten.**

Highest-exposure paths, ranked:

| # | Path | Transaction spans LLM? | Cost per request | Reachability |
|---|---|---|---|---|
| 1 | `ChallengeQuizService:449` — Challenge start | **Yes**, class `@Transactional` `:84` | 1 conn, ≤180 s | **All plans.** Highest-volume LLM path in the product |
| 2 | `ChallengeQuizService:1157` — `generateMoreQuestions` | **Yes**, + session row lock (`:1409`) | 1 conn + 1 lock | **All plans.** Every "+5 More Questions" click |
| 3 | `ChallengeQuizService:258` — multi-note Challenge | **Yes**, + `findByIdForUpdate` on `users` `:258`, then **N sequential** LLM calls `:1817` | 1 conn + user-row lock, ≤ N×180 s | FREE 2/mo, PLUS 10/mo |
| 4 | `QuickReviewAdaptivePracticeService:234` / `:539` | **Yes**, class `@Transactional` `:60`; `:539` fans out per pack | 1 conn, ≤ N×180 s | FREE 3 / PLUS 10 / PRO 30 |
| 5 | `InterviewPracticeService:604→615` | **Yes** — row lock taken **then** LLM called, per source | 1 conn + **N locks on `study_packs`** | PRO, 10/mo |
| 6 | `InterviewPracticeService:193` — `submitAnswer` | **Yes** | 1 conn per **answered question** | PRO |
| 7 | `GeneratedQuizService:126` — quiz for someone | **Yes**, class `@Transactional` `:53` | 1 conn | **Ungated** since `v0.89.0` |
| 8 | `StudyPackService:130` / `:274` / `:326` | **Yes**, class `@Transactional` `:73` | 1 conn | FREE 10/mo |
| 9 | `AskCompanionService:147` | **Yes**, + session lock `:125` | 1 conn + lock | PLUS/PRO 20/mo |
| 10 | `ChallengeQuizService:611`, `LongExamService:317` | **Yes — off-request but still holding.** `execute(...)` wraps the LLM | 1 conn on a generation thread | PRO |

**⚠️ "Dispatches after commit" ≠ "does not hold a connection."** `LongExamService.startSession` is
`@Transactional(NOT_SUPPORTED)` and is safe **for request latency only**; `generateLongExamAsync` then wraps
`studyPackGenerationTransactionOperations.execute(...)` (`:317`) **around** the LLM call at `:1151`. The
connection is held for the whole generation — by a `study-pack-generation-` thread instead of a Tomcat
thread. **Hikari does not care which thread.**

**Amplifier:** `service/ActivityTrackingEventListener.java:43-44` is `@Transactional(REQUIRES_NEW)` on an
`AFTER_COMMIT` listener and runs synchronously while the outer connection is still bound, so quiz
start/completion briefly holds **two** connections.

**Background holders are smaller than they look.** `config/AppConfig.java:52-60` —
`studyPackGenerationTaskExecutor` is core 3 / max 6 / **queue 100**, and a `ThreadPoolTaskExecutor` only
grows past core once the queue fills, so effective concurrency is **3**. `llmParallelTaskExecutor` takes
**zero** connections (no repositories injected). `spring.task.scheduling.pool.size` is unset, so all nine
`@Scheduled` jobs share **one** thread. **~4 connections can be held outside requests, leaving ~6 for 25
Tomcat threads.**

## 4. What the log cannot tell us — stated because an earlier draft of this investigation overstated it

**"No application error precedes the exhaustion" is NOT established.** The log carries **39 level-bearing
lines out of 763** and contains **zero INFO lines before the restart** — it is WARN/ERROR-filtered over a
~90-second window. **No `study-pack-generation-` or `llm-parallel-` thread appears anywhere in it.**

**The log cannot name the responsible path.** Absence of generation-thread errors is absence of evidence.
The three logged `unhandled_exception`s are all downstream consequences — `ClientAbortException`,
`AsyncRequestNotUsableException`, `CannotCreateTransactionException`.

## 5. Falsification — five read-only production queries

Window `2026-09-04 05:45` – `05:56` UTC.

1. `SELECT session_mode, status, count(*) FROM quick_review_sessions WHERE created_at BETWEEN … GROUP BY 1,2;`
   — **a cluster of `CHALLENGE` rows confirms the hypothesis.**
2. `SELECT event_type, count(*) FROM analytics_events WHERE created_at BETWEEN … GROUP BY 1 ORDER BY 2 DESC;`
3. `SELECT generation_status, count(*) FROM exam_question_pool WHERE generation_status_at BETWEEN … GROUP BY 1;`
   — tests whether `GenerationRecoveryJob` (fired 05:50:00, `0 */10 * * * *`) marked pools `FAILED`, causing a
   later `refreshPool` to fill the generation executor.
4. `SELECT count(*) FROM notes WHERE status='GENERATING' AND generation_enqueued_at BETWEEN …;`
   — **⚠️ CORRECTED 2026-09-04 (v0.112.0 Phase 2): this line originally read `generation_status_at`,
   which does not exist on `notes` and would have errored.** `V118` adds `generation_status_at` to
   `exam_question_pool` (query 3) but `generation_enqueued_at` to `notes` — two different clocks,
   deliberately, because pool rows are reused and note rows are not. Runnable form:
   `docs/claude-plans/v0.112.0-outage-falsification-read.sql`.
5. `SELECT count(*) FROM study_pack_drafts WHERE created_at BETWEEN …;` — tests the document-import path.

**Zero rows across 1, 2 and 4 refutes it** and redirects toward a slow query or lock on a large table
(admin dashboard, funnel, account export), a long document import, or a Render-side database event.

Cron timing does **not** line up: `GenerationRecoveryJob` at 05:50:00 and `BulkGenerationResultCleanupJob`
at 05:45:00; everything else runs 01:15–03:30 UTC. Saturation began ~05:54:43 (back-dated from the
23,672 ms health check logged at 05:55:07). `GenerationRecoveryService` is explicitly **not**
`@Transactional` (`:27`) and sweeps row-by-row.

## 6. ⚠️ RETRACTED — the OCR finding does not apply in production

An earlier draft of this investigation named `NoteTextExtractionService` as the worst connection-hold
ceiling in the codebase: up to **30 Google Vision OCR calls** inside one class-level `@Transactional`
(`:35`, loop `:158-160`, `pdf-max-pages: 30`), with **no timeout configured** on the Vision client.

**That path is unreachable in production. Vision is disabled** (`OCR_ENABLED=false`), and
`extractFromPdfViaOcr:144-146` throws `OcrDisabledException` **immediately** — before the quota check,
before the loop, before any Vision call.

**The code-level observation stands and the production conclusion does not.** If OCR is ever re-enabled,
this becomes the single worst connection-hold ceiling in the codebase, on a FREE path, with no timeout to
bound it. Recorded here so re-enabling Vision is a decision made with that in view.

**What IS live on the import path**, and it is much smaller: `extractFromPdf:114-141` holds the same
class-level transaction across `PDDocument.load(file.getBytes())` (up to 10 MB into heap) and
`PDFTextStripper.getText` over ≤30 pages, on 0.5 CPU. CPU-bound seconds, not LLM minutes. A scanned PDF
now fails fast rather than grinding. Same shape for DOCX (`:202`), smaller. **Ranks well below the LLM
paths.**

## 7. ⚠️ ~~OPEN HYPOTHESIS~~ — SETTLED 2026-09-04 (v0.112.0 Phase 2): CONFIRMED

**⚠️ THIS SECTION'S HYPOTHESIS WAS MEASURED AND HELD. Read the resolution first; the original text
below is preserved because its reasoning is still the reason the measurement was specified this way.**

Both reads the release required were taken, in order, and neither was substituted with an argument
from Spring Boot defaults:

- **PRIMARY (the setting).** The effective `hibernate.connection.handling_mode` is
  **`DELAYED_ACQUISITION_AND_HOLD`**. It is **not** a Hibernate default — Spring's
  `HibernateJpaVendorAdapter:190-192` sets it unconditionally whenever `prepareConnection` is true and
  the persistence unit is non-JTA, which is this application. Not dialect-specific, not a test artefact.
- **CONFIRMING (the behaviour).** Measured directly against Hikari's checked-out count: with the
  `EntityManager` still open, **the connection is still held after the transaction commits** (delta 1),
  and it is the `EntityManager` close — the end of the request under OSIV — that returns it.

**⚠️ CONSEQUENCE: PHASE 3 CANNOT FIX THE EXHAUSTION ON ITS OWN.** Relocating the LLM call outside
`@Transactional` releases the *transaction* while the *connection* stays bound to the request. The
prediction in this section was correct.

**Remedy is an OWNER DECISION and is deliberately not pre-selected.** `open-in-view: false` has a
**known** blast radius already priced in and routed to staging.
`hibernate.connection.handling_mode: DELAYED_ACQUISITION_AND_RELEASE_AFTER_TRANSACTION` was measured to
release at commit (delta 0) and a user property does override Spring's forced `HOLD` — but its blast
radius is **unknown**, and Spring's javadoc (`:101-103`) advises pairing a mode override with
`prepareConnection=false`, which is **not free here: 124 methods use `@Transactional(readOnly = true)`**.
**Cheaper-looking is not the same as cheaper.**

Guards: `ConnectionHandlingModeContractTest`, `ConnectionHandlingModeReleaseOverrideTest`.

### Original hypothesis, preserved

**`spring.jpa.open-in-view` is not set anywhere in `backend/src/main/`**, so it takes Spring Boot's default
of `true`. Verified by grep; the startup warning is absent from the log, but the log **ends 9 seconds after
JPA init**, so that absence is explained by truncation rather than by the setting.

OSIV binds the EntityManager for the **entire HTTP request** rather than the transaction. If the connection
follows the EntityManager here, then:

- **every slow request is a connection holder**, whether or not a transaction spans the slow part; and
- **it would blunt the structural fix in §8b** — moving the LLM outside `@Transactional` releases the
  *transaction*, but the connection could stay bound until the response is written, so the refactor could
  land, look correct, and not fix the exhaustion.

**This is deliberately not asserted.** The exact behaviour depends on Hibernate's connection-handling mode,
which is also unconfigured, and reasoning to a confident answer here is exactly the kind of step that
produces a wrong fix. **Settle it empirically with §8a item 1, not by argument.**
`spring.jpa.open-in-view: false` has a real blast radius — it surfaces `LazyInitializationException`
wherever a lazy association is touched during serialization — so it wants a staging run, never a direct
production edit.

## 8. Fixes

### (a) Immediate mitigation — config only. Order matters.

1. **`spring.datasource.hikari.leak-detection-threshold: 60000`.** Logs a full stack trace for any
   connection held past 60 s. **This names the offending path the next time it happens**, and it is the
   only cheap way to settle §7. Zero behaviour change, zero risk. **Worth more than the pool bump.**
2. **`maximum-pool-size`** — raise, with the ceiling below.
3. **`connection-timeout: 5000`** — counterintuitive, and a deliberate trade: waiters fail fast with a 500
   instead of queueing 30 s. That queueing is what let the health check blow past Render's probe threshold.
   **It trades user-visible errors for staying up.**
4. Two one-line code changes worth taking alongside: drop `studyPackGenerationTaskExecutor` to core 2 / max 2
   (`config/AppConfig.java:55-56`) so background generation cannot claim a third of the pool; and cut the LLM
   read timeout from 180 s toward ~90 s (`config/OpenAiLlmConfig.java:26`).

**Ceiling on the pool bump — check before raising.** Read the plan's limit from the Render dashboard and
confirm with `SHOW max_connections;`, then `SELECT count(*), application_name, state FROM pg_stat_activity
GROUP BY 2,3;`. **Render runs the new instance alongside the old during a deploy, each with its own pool**,
so the safe bound is roughly `N ≤ (max_connections − reserved) / 2`.

**PgBouncer is not the fix here.** Render offers it free, but it solves *too many clients*; this was *ten
connections held too long*. It is also transaction-mode only, which breaks session variables, temp tables,
`LISTEN`/`NOTIFY` and **session-level advisory locks** — grep for those before ever enabling it.

### (b) Structural — the correct pattern already exists in this repo three times

**Two short transactions with the LLM call between them.** Do not invent a new shape:

- `StudyPackService` — LLM at `:688`, *then* `studyPackGenerationTransactionOperations.execute(...)` at `:692`
- `ExamQuestionPoolService` — `execute` `:148` (mark GENERATING), LLM `:175`, `execute` `:185` (write result)
- `OfficialChallengeQuizTemplateService` — `execute` `:214`, LLM `:236`, `execute` `:245`

Apply in exposure order: (1) `ChallengeQuizService:449` and `:1157`; (2) `QuickReviewAdaptivePracticeService`
`:234`/`:539`; (3) `InterviewPracticeService` `:604`/`:615` — which must additionally **stop holding
`findByIdAndOwnerUserIdForUpdate` across the LLM call** — and `:193`; (4) `GeneratedQuizService:126`,
`StudyPackService:130`/`:274`/`:326`, `AskCompanionService:147`; (5) the executor-side holders at
`ChallengeQuizService:611` and `LongExamService:317`.

**⚠️ TWO RECORDED LANDMINES.** `ChallengeQuizService:370-384` documents that a previous afterCommit
restructuring **broke every Board Exam start in production while every test passed** — `MockitoExtension`
has no transaction manager, so tests took the inline fallback. And `v0.81.0` records that `REQUIRES_NEW`
for bank inserts **broke every Challenge start** on FK visibility across connections. **Neither shape is
safe. Use the two-short-transactions shape only.**

## 9. Corrections made during this investigation

Recorded so they are not re-derived:

| Claim | Correction |
|---|---|
| Tomcat runs 200 threads (Spring default) | **25** — `application.yaml:35`, sized for Render's 0.5 CPU. Changes the arithmetic to 25:10 |
| Board Exam runs the synchronous Challenge path | **It does not.** `ChallengeQuizService:391` returns early into the async path; `:410-412` is an explicit `IllegalStateException` tripwire preventing it |
| `LongExamService`'s after-commit dispatch is the safe pattern | Safe for **latency**; it still holds a connection across generation (`:317` wraps `:1151`) |
| `ExamQuestionPoolService` pool refresh is a suspect | **Already correct** — LLM sits between two short transactions. Do not touch it |
| The OCR path is the worst ceiling in production | **Retracted** — §6. Vision is disabled; the path throws immediately |

## 10. Follow-ups this file owes

- **This file needs a Backlog Index row in `ROADMAP.md`** per kickoff step 8. The previous incident file in
  this directory (`2026-09-01-prod-frontend-build-failure-public-notes-2mb.md`) was written mid-release and
  **never got one** — the same failure mode, twice.
- §5's queries are **unrun**. Until they are, §3 is a well-supported hypothesis, not a confirmed cause.
- §7 is unresolved and should be settled by §8a item 1 rather than by argument.

## 11. ⚠️ RENDER EVIDENCE, READ 2026-09-04 — THE §5 QUERIES ARE NOW LARGELY MOOT, AND §3 IS NOT CONFIRMED

Read directly from Render (read-only: metrics, logs, deploy history, and `SHOW max_connections`).
**⚠️ THIS SECTION REMOVES TWO HYPOTHESES AND CONFIRMS NEITHER OF THE REMAINING ONES.**

### Established

1. **`max_connections = 103`, `superuser_reserved_connections = 3`** → the deploy-overlap ceiling is
   `(103 − 3) / 2 = 50`. **Phase 1's `maximum-pool-size: 20` is verified safe**, and the 45 ceiling
   pinned in `DataSourcePoolContractTest` is correctly conservative. **This was Phase 1's one assumed
   number and it is now measured.**
2. **THE DATABASE WAS IDLE AND HEALTHY THROUGHOUT.** CPU **0.008–0.021** of a core (0.017 at 05:55) and
   memory **140–171 MB of 256 MB**, flat across 05:30–06:10. **⚠️ SO THE STALL WAS NOT ON THE DATABASE
   SIDE**, and a slow-query/lock cause — §5's own named redirect target — is refuted.
3. **Database connection count was FLAT AT 11** for the entire window (05:00–06:30). The pool neither
   grew nor collapsed. **The connections stayed OPEN and did NO WORK.**
4. **NO APPLICATION LOG OUTPUT BETWEEN 05:50:00.028 AND 05:55:07.057** — five minutes of complete
   silence immediately before the first symptom.
5. **THE SCHEDULED JOBS FOUND NOTHING STUCK.** `GenerationRecoveryJob` at 05:40 and 05:50, and
   `BulkGenerationResultCleanupJob` at 05:45, all reported zeros:
   `pools=0 longExamSessions=0 boardExamSessions=0 notes=0`.
6. **NO DEPLOY AT 05:56 — CONFIRMED FROM RENDER'S DEPLOY HISTORY, NOT INFERRED FROM THE LOG.** The last
   deploy finished **03:09:44** (v0.110.2) and the next began **06:24:18** (v0.111.0). Together with the
   owner confirming they did **not** restart manually, **both named alternatives are eliminated from
   Render's own records.** A Render-side health-check auto-restart is what remains — still not directly
   attributed by any platform line.
7. **THE DEPLOY-OVERLAP EFFECT IS REAL AND WAS OBSERVED.** At **06:27**, during the v0.111.0 deploy,
   database connections rose **11 → 21** — two instances, each with its own pool. **This is direct
   empirical confirmation of the reasoning behind the pool ceiling**, previously argued from Render's
   documentation alone.

### ⚠️ Refuted

- **THE ANONYMOUS READ-BURST HYPOTHESIS (§0's "most consequential finding") IS NOT SUPPORTED.**
  Completed-request counts in the run-up were **0–9 per minute** (05:52 → 0, 05:53 → 0, 05:54 → 0).
  There was no burst. **Raised in good faith and withdrawn on evidence; the §5 gap it identified was
  still real, but the hypothesis it pointed at does not hold.**
- **A database-side stall** — refuted by (2).
- **A deploy-triggered build** — refuted by (6).

### ⚠️ What the instruments CANNOT show — stated because absence was nearly misread as evidence

- **REQUEST LOGS ARE NOT RETAINED FOR THIS SERVICE.** A query over the outage window returns zero rows —
  **but so does a query over 06:27, when the metric records 101 requests.** The silence proves nothing.
  **Verified before being relied on; the empty result was very nearly read as "no traffic".**
- **`http_request_count` may bin by COMPLETION, not arrival.** So "0 requests at 05:54" is weaker than
  it looks: requests that arrived then and hung for 30 s would be binned at 05:55–05:56. The low counts
  are evidence against a *large burst*, **not proof of zero arrivals.**

### ⚠️ Where this leaves the cause — and Phase 3

The pool was fully checked out (`active=10`) while **the database was idle and doing no work**. That is
the signature of connections **held but not used**. Two explanations remain and this evidence does
**not** separate them:

- **(a) Connections held across slow external calls** — §3's hypothesis, the one Phase 3 addresses.
- **(b) A connection LEAK** — connections checked out and never returned, draining the pool over time
  until the next caller, however light the traffic, found nothing available.

**⚠️ (b) FITS THE OBSERVATIONS AT LEAST AS WELL AS (a) AND HAS NEVER BEEN CONSIDERED.** It explains
exhaustion under near-zero traffic without requiring ~7 concurrent generations for which there is no
evidence: no request burst, no generation activity, five minutes of silence.

**⚠️ SO §3 IS NOT CONFIRMED, AND PHASE 3 REMAINS UNVALIDATED AS THE RIGHT TARGET.** Restructuring six
services' transaction boundaries and twelve quota sites addresses (a) and **does nothing for (b)**.

**⚠️ THE DISCRIMINATING INSTRUMENT IS ALREADY BUILT AND HAS NOT SHIPPED.** Phase 1's
`leak-detection-threshold: 60000` logs a full stack trace naming any path holding a connection past
60 s — it separates (a) from (b) outright. **It is merged to `releases/v0.112.0` but NOT to `main`, so
it is not in production. Deploying Phase 1 is now the highest-value next action in this release —
higher than building Phase 3.**

## 12. ⚠️ §5 RUN AT LAST (v0.114.0, 2026-09-04) — AND Q2 REOPENS THE HYPOTHESIS §11 REFUTED

**⚠️ READ THIS BEFORE ACTING ON §11's "Refuted" LIST.** §11 called these queries *"largely moot"* and
they were left unrun; §10 recorded them as owed. They were finally run — read-only, against production,
via the Render MCP query tool — and **one of them contradicts §11 on the single most consequential
point.** The queries were not moot. They were unread.

### The write side: hypothesis (a) has ZERO supporting rows, on a deliberately widened window

| Query | Result |
|---|---|
| **Q1** quiz sessions started 05:45–05:56 by mode/status | **0 rows** |
| **Q3** `exam_question_pool` by `generation_status_at` 05:45–05:56 | **0 rows** |
| **Q4** notes `GENERATING` enqueued 05:45–05:56 | **0** |
| **Q5** `study_pack_drafts` created 05:45–05:56 | **0** |
| **Q7** sessions in the same clock window, 7 preceding days | **0 rows — every day** |

**⚠️ Q7's EMPTINESS IS A TRAP AND IS RECORDED AS ONE.** It was written as the baseline that gives Q1 a
meaning. It returns nothing, so **Q1's zero is the normal state for that hour, not an anomaly** — on its
own it therefore says *nothing* about the incident. That is why the window was widened rather than the
result rounded up:

- sessions created **05:00–05:56**: **0**
- notes enqueued **04:00–05:56**: **0**
- notes still `GENERATING` at read time: **0**

**⚠️ SO THE WIDENED READ IS THE ONE THAT CARRIES WEIGHT, AND IT IS UNAMBIGUOUS: no generation-bearing
work of any kind existed in or before the window.** §3's root-cause class requires roughly seven
concurrent synchronous generations. There were **none, for at least an hour and fifty-six minutes**.

### Q2 — ⚠️ THE READ BURST §11 REFUTED IS BACK, AND IT IS ONE CLIENT HAMMERING ONE PAGE

Q2 was specified merely to corroborate Q1 from an independent table. It does something else: **it is the
only instrument in this file that can see READS, and §11 never consulted it.**

**⚠️ THE FIRST DRAFT OF THIS SECTION OVERSTATED IT AND IS CORRECTED HERE RATHER THAN QUIETLY REWRITTEN.**
It reported *"12 events at 05:54, 12× the run-up rate"* from a minute-bucketed count and read that as broad
anonymous traffic. **Pulling the raw timestamps falsifies that reading.** The minute bucket is not twelve
visitors:

| Time (UTC) | Event | Note |
|---|---|---|
| 05:47:13 → 05:50:26 | `PUBLIC_NOTE_VIEWED` ×7 | **seven different notes**, one every ~30 s — ordinary browsing |
| **05:54:28.436 → 05:54:36.434** | `PUBLIC_NOTE_VIEWED` **×12** | **ALL the same note**, `landmark-works-of-modern-architecture` |
| 05:54:44.477 | `EXPLORE_VIEWED` | `referrerSource: social` |
| 05:55:09 → 05:55:46 | `PUBLIC_NOTE_VIEWED` ×5 | **the same note again** |

**⚠️ SO IT IS SIXTEEN HITS ON ONE NOTE IN SEVENTY-EIGHT SECONDS, TWELVE OF THEM IN EIGHT SECONDS**, at
intervals of 0.6 s, 0.7 s, 1.1 s, 1.7 s, 0.3 s. Every row is anonymous, and every one carries
`pathType: seo`.

**⚠️ AND IT IS ANOMALOUS RATHER THAN NORMAL FOR THAT PAGE — CHECKED, NOT ASSUMED.** Over 2026-08-25 →
2026-09-04 that note was viewed **1 hit in the 08-31 13:00 hour, 1 in the 14:00 hour, and 2 in the 09-04
09:00 hour.** The outage hour holds **16**. The pattern occurs **once in eleven days**, and it is during the
outage.

**⚠️ TEMPORAL PRECEDENCE RUNS THE RIGHT WAY, BY ABOUT FIFTEEN SECONDS.** Saturation is dated ~05:54:43,
back-dated from the 23,672 ms health check logged at 05:55:07. **The burst begins at 05:54:28.436** — before
the health check started queueing — and the trailing hits at 05:55:09 → 05:55:46 lengthen to 3 s, 8 s, 8 s,
18 s, which reads as a client backing off against a struggling server.

### ⚠️ WHAT THIS DOES AND DOES NOT ESTABLISH — the direction is NOT settled

**⚠️ THE OBVIOUS ALTERNATIVE WAS RAISED BEFORE THIS WAS WRITTEN DOWN, AND IT IS NOT ELIMINATED: the burst
could be a SYMPTOM.** A client whose page load stalled may simply have reloaded, in which case the repeats
are downstream of the exhaustion rather than its cause. Two facts bear on it and they do not agree:

- **For cause:** the burst starts ~15 s *before* the saturation signature, the page is otherwise hit 1–2
  times an hour, and the endpoint class is exactly the one the log's only stack trace names as starved
  (`NoteCollectionService.listPublic:206` — `permitAll`, `@Transactional(readOnly = true)`, unpaginated,
  several repository calls per invocation). With `open-in-view=ON` and `DELAYED_ACQUISITION_AND_HOLD`
  (§7), **each in-flight request holds a connection for its whole duration** — so sixteen overlapping slow
  reads against a pool that was then **10** is sufficient on its own.
- **Against cause:** the backoff-shaped tail is what a retry loop looks like.
- **⚠️ Cutting slightly toward cause:** `PUBLIC_NOTE_VIEWED` is fired **client-side after the page
  renders**, so each row is a load that *completed*. A server too starved to answer would produce no row.
  **This is suggestive, not conclusive** — it constrains the retry story without killing it.

**⚠️ AND ONE INSTRUMENT LIMIT REMAINS DECISIVE: an ISR revalidation, a crawler or any non-browser client
runs no client-side JavaScript and leaves NO ROW HERE.** Q6's own note says so. `revalidate = 300` means
public pages are re-fetched every ~5 minutes independently of any deploy, and **none of that traffic is
visible in this table at all.** So the 16 rows are a *floor* on read activity in the window, never a total.

**⚠️ THE `.sql` FILE'S COLD-RE-READ BANNER IS ALSO FALSIFIED BY THIS AND IS CORRECTED THERE.** It states
that *"Q1-Q5 all read GENERATION, SESSION, POOL and DRAFT tables… none of them can detect an anonymous READ
burst, because reads leave no row anywhere."* **That is wrong about Q2:** `analytics_events` recorded
anonymous read activity, which is why Q2 turned out load-bearing rather than corroborating. A later session
reading only that banner would discount this finding.

### Verdict — pre-declared in the `.sql` file, and applied rather than rounded up

The file pre-declared: *"a cluster of CHALLENGE rows → CONFIRMS … zero rows across Q1, Q2 AND Q4 →
REFUTES … anything in between → still a hypothesis. Say so; do not round up."* This is **anything in
between**, and it is stated as such:

- **§3's root cause (a), connections held across slow external calls: NOT CONFIRMED, and now
  positively unsupported.** Zero generation rows across a widened window is the strongest evidence yet
  taken against it, and **it is the hypothesis Phase 3 is built to address.**
- **(b), a connection leak** (§11's newly-raised alternative): **still live, still untested.** Nothing
  here touches it either way.
- **A read burst on unpaginated public endpoints: back on the table, and it is the ONLY hypothesis
  here with any positive evidence** — an anomalous, once-in-eleven-days repeat-hit pattern on the exact
  endpoint class the log names as starved, beginning ~15 s before saturation. **⚠️ Its DIRECTION is not
  established** (symptom-versus-cause is argued both ways above), and it is a floor rather than a total
  because ISR and crawler traffic leave no row.

**⚠️ CONSEQUENCE FOR PHASE 3, STATED PLAINLY: it is aimed at the one hypothesis the data now argues
against.** That is not a reason to redesign it here — this release measures — but it **is** the reason
`[CHECKPOINT — due 2026-10-04]` must not be answered by argument.

### The leak-detection read — the discriminating instrument, now live but with no window

§11 named Phase 1's `leak-detection-threshold: 60000` as the instrument that separates (a) from (b), and
recorded that it was **not in production**. It is now: v0.112.0 went live **2026-09-04 10:33:49 UTC**,
and v0.113.1 (current) at **15:35:08 UTC**.

A log query for leak reports over **10:33 → 15:50 UTC returns ZERO.**

**⚠️ THAT SETTLES NOTHING AND MUST NOT BE READ AS CLEARING (b).** It is ~5.3 hours of a low-traffic
afternoon, not the 05:55 traffic profile, and the incident itself was preceded by five minutes of total
silence. **Zero leak reports over a quiet window is exactly what both hypotheses predict.** What it
needs is a window that includes real load — which is a dated read, recorded as a Backlog Index
checkpoint rather than concluded here.
