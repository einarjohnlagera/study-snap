# Scoping pass — pool saturation detector: cover non-request threads and the scheduler's own contention

**Written by:** Release Implementor session, 2026-09-22, on owner request ("let's do the scoping pass"
for the `PoolSaturationDetector` Known Limitation, "implement thru Codex to have a better
implementation" once scoped). **For:** whichever session dispatches the Codex prompt this plan feeds —
this is a plan, not a diff; no code was changed to produce it. **Status: SCOPED, NOT DISPATCHED.**
Explicitly deferred to a future release at the owner's request — do not write the Codex prompt from
this file without a fresh go-ahead.

**Source of the gap:** `RELEASES.md`'s `v0.153.0` Known Limitations (two entries — registry coverage,
and the scheduler sharing a single thread with the poller) and `ROADMAP.md`'s Backlog Index row on the
pool-exhaustion checkpoint. Read both before this plan; this plan does not repeat their evidence.

---

## 1. The two gaps, restated precisely

**Gap A — the diagnostic registry only sees HTTP request threads.** `InFlightRequestRegistry` is
populated exclusively by `InFlightRequestTrackingFilter`, a servlet filter. `PoolSaturationDetector`
logs the registry's snapshot when the pool saturates. A connection held by a background thread —
`studyPackGenerationTaskExecutor`, `bulkRegenerationTaskExecutor`, `notificationFanOutExecutor`,
`analyticsTaskExecutor`, or any of 16 `@Scheduled` jobs — is invisible to that snapshot. The log then
reads `requests in flight at saturation=[]`, which is ambiguous between "nothing was in flight" and
"the holder was never eligible to be seen." `llmParallelTaskExecutor` is excluded from the list above
deliberately — confirmed in the 2026-09-04 outage finding to take zero DB connections (no repositories
injected).

**Gap B — the poller shares a contended thread.** No custom `TaskScheduler` bean exists, so
`@EnableScheduling` uses Spring Boot's implicit default: a single-threaded `ThreadPoolTaskScheduler`.
`PoolSaturationDetector.poll()` (every 2s) and all 16 `@Scheduled` jobs queue on that one thread. If a
job blocks acquiring a connection during a real saturation event (up to `connection-timeout: 5000`),
the poll that should be detecting the saturation is delayed by exactly that long — a detection-latency
risk at precisely the moment the instrument exists to fire.

Both were deliberately deferred at `v0.153.0` signoff rather than missed — Gap A explicitly as "a
larger design question," Gap B explicitly "not changed here to keep this leg's diff minimal."

## 2. The design — verified against this project's actual Spring version, not assumed

**Spring Framework 7.0.5** (via `spring-boot-starter-parent:4.0.3`). Confirmed by `javap` against the
resolved jar at `~/.m2/repository/org/springframework/spring-context/7.0.5/spring-context-7.0.5.jar` —
not read from documentation, in case a newer/older API surface applies to this project than assumed:

```
$ javap -classpath spring-context-7.0.5.jar org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor | grep -i taskdecorator
  public void setTaskDecorator(org.springframework.core.task.TaskDecorator);

$ javap -classpath spring-context-7.0.5.jar org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler | grep -iE "taskdecorator|setPoolSize"
  public void setPoolSize(int);
  public void setTaskDecorator(org.springframework.core.task.TaskDecorator);

$ javap -classpath spring-context-7.0.5.jar org.springframework.scheduling.support.ScheduledMethodRunnable
  public java.lang.Object getTarget();
  public java.lang.reflect.Method getMethod();
  public java.lang.String toString();   # confirmed via sources jar: returns
                                          # `method.getDeclaringClass().getName() + "." + method.getName()`
```

**Both `ThreadPoolTaskExecutor` and `ThreadPoolTaskScheduler` support `setTaskDecorator`.** This is the
mechanism that resolves both gaps without touching any business-logic call site:

### 2a. Executor side — one shared `TaskDecorator`, applied to four beans

A new `TaskDecorator` (e.g. `config/InFlightThreadRegisteringTaskDecorator.java`) wraps the submitted
`Runnable`: calls `registry.registerCurrentThread(description)` before `runnable.run()`, and
`registry.removeCurrentThread()` in a `finally` block after. `description` is the executor's own name
(e.g. `"executor:study-pack-generation"`) — coarser than an HTTP path, but a real improvement over an
empty list, and it requires no change inside the classes that actually submit work
(`LongExamService`, `ExamQuestionPoolService`, `AdminStudyPackService`,
`OfficialChallengeQuizTemplateService`, `NotificationService`, the analytics dispatch path).

Apply `.setTaskDecorator(...)` to exactly four `@Bean` methods in `AppConfig.java`:
`studyPackGenerationTaskExecutor`, `bulkRegenerationTaskExecutor`, `notificationFanOutExecutor`,
`analyticsTaskExecutor`. **Do not apply it to `llmParallelTaskExecutor`** — it holds no connections, so
decorating it would only add registry churn with no diagnostic value, and the existing
`config/AppConfig.java` doc-comments explaining *why* each executor is sized the way it is should gain
one line each noting the decorator's presence, not lose their existing reasoning.

### 2b. Scheduled-job side — one new `TaskScheduler` bean, doing double duty

Define a custom `TaskScheduler` bean (`ThreadPoolTaskScheduler`) to replace Spring Boot's implicit
default:

```java
@Bean
public TaskScheduler taskScheduler(InFlightRequestRegistry registry) {
    ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
    scheduler.setPoolSize(2);                 // closes Gap B — was implicitly 1
    scheduler.setThreadNamePrefix("scheduled-task-");
    scheduler.setTaskDecorator(runnable -> () -> {
        String description = runnable instanceof ScheduledMethodRunnable smr
                ? smr.toString()               // "com.studysnap.backend...GenerationRecoveryJob.recover"
                : runnable.getClass().getSimpleName();
        registry.registerCurrentThread(description);
        try {
            runnable.run();
        } finally {
            registry.removeCurrentThread();
        }
    });
    scheduler.initialize();
    return scheduler;
}
```

This single bean closes **both** gaps for the scheduled-job side: pool size 2 directly answers Gap B
(named in `v0.153.0` as the exact fix, `spring.task.scheduling.pool.size: 2` — implementing it as a
bean property here instead of a YAML property is equivalent and keeps the decorator and the pool size
declared together), and the decorator gives Gap A's registry coverage for all 16 jobs with a
*more precise* identifier than the executor side gets, for free, via `ScheduledMethodRunnable`.

**Verify before shipping:** confirm no two `@Scheduled` methods share mutable non-thread-safe state —
already checked in this scoping pass for the four rate-limiter classes (`AuthRateLimitService`,
`AiRateLimitService`, `InvitationRateLimitService`, `OcrRateLimitService`, all backed by
`ConcurrentHashMap`) and `RetentionEmailScheduler` (no mutable fields). Re-confirm this holds for the
remaining scheduled classes (`GenerationRecoveryJob`, `NotificationCleanupJob`,
`BulkGenerationResultCleanupJob`, `AccountPurgeScheduler`, `BillingUsageResetJob`,
`SubscriptionExpiryJob`, `SubscriptionExpiryEmailScheduler`, `LinkedLearnerRequestExpiryJob`) before
the Codex prompt is dispatched — Spring's default scheduler already guarantees a given `@Scheduled`
*method* never re-enters itself concurrently; raising the pool size only changes whether two
*different* jobs can now run at the same instant, which is the property that needs checking.

### 2c. What does NOT need to change

- `InFlightRequestRegistry` — already generic (keyed by `Thread`, stores a description + timestamp).
  No structural change needed. Its javadoc ("requests currently executing inside the servlet filter
  chain") becomes inaccurate once non-request threads register too — update the comment, not the type.
- `PoolSaturationDetector.describeInFlightRequests` — already iterates the registry generically and
  formats whatever `path()` holds. Works unchanged regardless of whether an entry came from an HTTP
  request or a decorated executor/scheduled task.
- `InFlightRequestTrackingFilter` — untouched; it remains the HTTP-side registration path.

## 3. Anti-drift

- Do not touch `llmParallelTaskExecutor` (zero connections, decorating it adds no value).
- Do not widen `studyPackGenerationTaskExecutor`'s core/max pool size — that bound is a Phase 3
  decision (`AppConfig.java`'s own doc-comment) unrelated to this plan.
- Do not remove or weaken `InFlightRequestTrackingFilter`'s `@Order(HIGHEST_PRECEDENCE + 1)` — it's
  load-bearing so a connection held inside Spring Security's own filters is still visible.
- Do not raise `spring.datasource.hikari.maximum-pool-size` as part of this work — this plan is about
  *visibility*, not capacity, and `ROADMAP.md`'s checkpoint explicitly warns against reaching for the
  pool size in response to a recurrence.
- Do not fold in `threads.max`/Render env var concerns — that half is already shipped (`v0.153.0`) and
  is now live in production (owner confirmed removing the overriding Render env var 2026-09-19); this
  plan is the remaining, separate gap.

## 4. Test coverage a Codex prompt for this must include

- A test proving a decorated executor task actually appears in `registry.snapshot()` while running,
  and is removed after — mirroring the existing `PoolSaturationDetectorTest` pattern that genuinely
  exhausts a real Hikari pool rather than mocking it.
- A test proving a `@Scheduled` method's thread appears in the registry with the
  `ClassName.methodName` description, not a generic label.
- A test proving the registry entry is removed even if the decorated task throws (both paths, executor
  and scheduler) — the existing `finally`-block pattern in `InFlightRequestTrackingFilter` is the
  precedent to match.
- A test proving `PoolSaturationDetector`'s existing saturation-detection tests still pass unchanged —
  this plan must not alter detection behavior, only what gets logged when it fires.

## 5. Routing

**Codex, Long mode** — multiple files in `backend/src/main/java/com/studysnap/backend/config/`, a real
design decision already resolved by this plan (not left to Codex to invent), touches production
reliability infrastructure with a four-incident history. Not Claude-Code-inline: this crosses the
routing table's own size/multi-file thresholds once the scheduler bean, the decorator class, and their
tests are counted together, even though no business-logic file is touched.

**Verification tier once implemented:** at minimum one scoped `advisor()` pass on the diff before
commit; given this is production-reliability infrastructure with a four-incident history, consider one
scoped cold agent (falsification-framed against this plan's specific claims) rather than `advisor()`
alone, per this repo's own escalation criteria for changes touching reliability-sensitive
infrastructure with a measured incident history.

## 6. Explicitly out of scope, not forgotten

Two further items already flagged in `ROADMAP.md`'s Backlog Index and not addressed by this plan:
§5's still-unbounded anonymous public reads, and the "ramp-window exposure" (a saturation pattern
observed in the few seconds immediately after a deploy, independent of this plan's gaps). Each needs
its own scoping pass; do not fold either into the Codex prompt this plan eventually produces.
