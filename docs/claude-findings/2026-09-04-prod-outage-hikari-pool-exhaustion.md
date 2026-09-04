# 2026-09-04 — production outage: HikariCP pool exhaustion → health-check failure → platform restart

## Status as of 2026-09-04: diagnosis only. Nothing fixed, no code or config changed, no release opened.

Source: `docs/20260904_prod-issue-down.log` (763 lines, 05:55:07–05:57:00 UTC), plus a code read at
`v0.110.2`. Every `file:line` below was opened; claims that were **not** verified are labelled as such.

> **⚠️ THIS FILE CONTAINS ONE RETRACTED FINDING AND ONE OPEN HYPOTHESIS. Both are marked. Do not cite
> §6 as a defect or §7 as a conclusion.** The header of the previous incident file in this directory
> went stale against its own body; if the state below changes, correct this block rather than leaving it.

---

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
4. `SELECT count(*) FROM notes WHERE status='GENERATING' AND generation_status_at BETWEEN …;`
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

## 7. ⚠️ OPEN HYPOTHESIS, NOT A FINDING — Open Session In View

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
