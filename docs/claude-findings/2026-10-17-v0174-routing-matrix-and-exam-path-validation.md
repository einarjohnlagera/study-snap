# v0.174.0 — model-routing matrix (deliverable 2) and Board / Long Exam production-path validation (deliverable 7)

**Status: COMPLETE for the code read; the open points in §3 go to the item 2 brief and to the owner.**
Written 2026-10-17 by Release Implementor, for the owner's conditional approval
(`docs/claude-plans/2026-10-17-owner-conditional-approval-challenge-quiz-ux.md` §11, items 2 and 7).

Sources: code on `releases/v0.174.0` at `8887ca9e`, and read-only production `SELECT`s run on 2026-10-17.
No production write.

## 1. Routing matrix (deliverable 2)

### How a model is chosen today

`OpenAiLlmStudyPackService` has three resolvers:
- `requireConfiguredModel()` (`:346`) returns `LLM_MODEL_FREE`;
- `requireConfiguredPremiumModel()` (`:350`) returns `LLM_MODEL_PREMIUM`;
- `requireConfiguredCritiqueModel()` (`:354`) returns `LLM_MODEL_CRITIQUE`.

**None of the three takes a user, a plan or a role**, so model choice is fixed per method.
`StudyPackService.java:721-723` records a `model_tier` label, but no request reads it.

### Matrix

FREE = `gpt-6-luna`. PREMIUM = `gpt-6.1-sol`. "Owner rule" means the 2026-10-17 decision: FREE → Luna;
Plus/Pro → Sol; official curation (`UserRole.ADMIN`) → Sol regardless of plan.

| # | Method (resolver line) | Callers in production | Today | Target FREE / Plus·Pro / ADMIN | In kickoff scope? |
|---|---|---|---|---|---|
| 1 | `generateStudyPack` (`:288`) | `StudyPackService:140,396,448,862`: note generation and regeneration, including admin bulk | free | Luna / Sol / Sol | yes |
| 2 | `regenerateSummary` (`:231`) | `AdminStudyPackTransactionHelper:68`: admin-only regeneration | free | — / — / Sol | yes |
| 3 | `generateNoteFromTopic` (`:251`) | `NoteGenerationService:111`, `NoteBulkGenerationService:441` | free | Luna / Sol / Sol | **NO — not among the 7 kickoff services** |
| 4 | `generateChallengeQuiz` / `generateMoreChallengeQuiz` (`:2273`, through `generateChallengeQuizWithSchema`) | `ChallengeQuizService:452` (start), `:1170` (+5), `:1842`; `QuizGenerationService` wrappers | free | Luna / Sol / Sol | yes |
| 5 | `generateChallengeQuiz` for the **Official template seed** | `OfficialChallengeQuizTemplateService:300` (`seedTemplateAsync`). No user identity; the note owner is the Official author | free | — / — / **Sol** (curation) | yes (the service is named; the seed path needs explicit mention in the brief) |
| 6 | `generateBoardExamQuiz` (`:2199`, through `generateQuizWithSchema`) | `ChallengeQuizService:689` (async Board Exam), `ExamQuestionPoolService:316,325` (pool) | free | Luna / Sol / Sol | yes |
| 7 | `generateLongExam` / `generateLongExamParallel` (`:2199` path) | `LongExamService:1159`, `ExamQuestionPoolService:287` | free | Luna / Sol / Sol | yes |
| 8 | `generateAdaptivePracticeQuiz` (`:2199` path) | `QuickReviewAdaptivePracticeService:245,516` | free | Luna / Sol / Sol | yes |
| 9 | `generateTeacherQuiz` (`:2199` path) | `GeneratedQuizService:126` through `QuizGenerationService:288,299` | free | Luna / Sol / Sol | **NO — `GeneratedQuizService` is not among the 7** |
| 10 | `generateQuickReviewStudyTip` (`:1793`) | `QuickReviewStudyTipService:66` | free | Luna / Sol / Sol, **or leave on free (owner choice, see §3)** | **NO** |
| 11 | `generateCompanion` (`:273`) | `NoteCollectionService:1025` | **premium** | unchanged (paid-gated) | out of scope, already premium |
| 12 | `generateInterviewPracticeQuiz` (`:1860`) | `InterviewPracticeService:690` | **premium** | unchanged (Pro-only) | out of scope, already premium |
| 13 | `generateInterviewCritique` (`:1878`) | Interview Practice | critique | **unchanged: `LLM_MODEL_CRITIQUE` is out of this release** | excluded by owner §9 |

### What the matrix changes in item 2's scope

- **Four caller services are missing from the kickoff list of 7:**
  - `NoteGenerationService` and `NoteBulkGenerationService` (note-from-topic);
  - `GeneratedQuizService` (teacher quiz);
  - `QuickReviewStudyTipService`.

  So routing has to reach **11 caller services**, plus the `QuizGenerationService` wrappers they call
  through, plus `StubLlmStudyPackService`, which must change with any interface change.
  - The owner's §1 asks to "verify model routing for all affected generation paths", so they belong in the
    brief.
  - Each still needs the routing test per path (RELEASES item 2, "Owed").
- **The template seed (row 5) has no caller identity.** The note-owner check already in
  `isEligibleOfficialTemplate` resolves it: the owner is an Official author, so curation applies and the
  model is Sol. The brief must state this, or a "no user, so default to free" fallback will quietly keep
  Official templates on Luna.
- **Routing source for the plan: `SubscriptionService.resolvePlan` / `getPlanSnapshot` (`:65-85`).** It
  returns a paid plan only for a subscription current at `now`, otherwise FREE, so a lapsed manual renewal
  routes to Luna. This meets the owner's "trusted backend entitlement data". The brief must name it and
  forbid reading a raw plan field.
- **Official-author check:** the template service counts an account as Official if
  `role == ADMIN || email == OFFICIAL_AUTHOR_EMAIL` (`isOfficialAuthorAccount`). The owner's curation rule
  is `UserRole.ADMIN`.
  - **In production the two pick out the same single account** (read-only: 1 ADMIN; the configured email
    belongs to it; 0 non-ADMIN matches).
  - The item 2 brief uses `UserRole.ADMIN` as decided.
  - If an email-only Official account ever appears, its templates would route to Luna. Record that, but no
    decision is needed now.
- **Study tip (row 10): routed by plan, like the rest.** The owner's "do not silently downgrade paid
  generation" (§1) settles it.
- **No Challenge-specific destination is recommended** (Feature Planner, 2026-10-17). The matrix stays at
  two destinations plus the unchanged critique model. A third is an owner decision gated on the wait bound.

## 2. Board Exam and Long Exam production-path validation (deliverable 7)

### Production usage: none from learners

Read-only, all time:
- **Board Exam: 8 sessions, all by the ADMIN account, last 2026-05-29.**
- **Long Exam: 5 sessions, all ADMIN** (2 completed, 1 failed, 1 forfeited, 1 still `IN_PROGRESS`), last
  2026-05-29.
- **Zero learner sessions in either mode, ever.** This was checked against every `session_mode` and
  `session_state->>'mode'` combination in the table: learners have 201 Challenge, 61 Adaptive and 420 Quick
  Review sessions, and none of the exam modes.
- **Exam pools:** 1,316 `exam_question_pool` rows, created 2026-05-20 → 07-03 by the pre-warm before its kill
  switch (`application.yaml:254-259`). None has ever been served (`served_question_keys` is empty on all).
  - **They are the one production measurement of these paths, on `gpt-4.1-mini`:** Board Exam pools 35 of
    659 FAILED (5.3%), Long Exam pools 31 of 657 FAILED (4.7%).
  - That is consistent with the benchmark's mini timeouts. Luna and Sol had 0 failures in 162 benchmark
    calls, but those calls were smaller.

**Production cannot give per-request latency or retry rates for learner sessions.** The pool failure rate
above is the only production signal. The rest of this section is read from code. Any measurement has to come from the targeted benchmark (deliverable 6).

### Real request shapes

| Mode | How it generates | Request size per LLM call | Pool / executor |
|---|---|---|---|
| **Board Exam session** | Async after commit (`ChallengeQuizService` `generateBoardExamAsync`, `:583-602`). One `generateBoardExamQuiz` call **per source note, run in sequence** (`generateBoardExamQuizResiliently`, `:667-700`). | The 30-question target (`board-exam-target-question-count`, `application.yaml:251`) divided evenly across sources (`ChallengeQuizService:1639-1649`; up to 3 sources, `MAX_ADDITIONAL_BOARD_EXAM_SOURCE_COUNT = 2` at `:185`). That is 15 per call with 2 sources and 10 with 3. A single-source start would send 30 in one call; whether one is reachable, given the 2-contributor floor (`application.yaml:253`), was not traced | `studyPackGenerationTaskExecutor` (2 threads, queue 100, `AppConfig`) |
| **Board Exam pool** | `ExamQuestionPoolService.generateBoardExamPool` (`:309-330`): two **sequential** 12-question calls | 12 + 12, pool size 24 (`:250`) | same executor. **Pre-warm is OFF** (`exam-pool-prewarm-enabled: false`, `:259`), so a pool is now built only on first use |
| **Long Exam session** | Async after commit (`LongExamService:293-310`). Per source note, in sequence, one `generateLongExamParallel` (`:1159`) | The note's share of 20 / 25 / 30 questions by learner level (`StudySnapProperties:166-168,206-211`). `generateLongExamParallel` splits it into **2 parallel calls of ⌈n/2⌉ + 2** (`OpenAiLlmStudyPackService:2013-2030`, `PARALLEL_BUFFER = 2` at `:103`): up to 17 per call | the outer task on `studyPackGenerationTaskExecutor`; the two halves on `llmParallelTaskExecutor` (core 4, max 8, queue 50, `AppConfig:214-218`), **which `seedTemplateAsync` also uses** |
| **Long Exam pool** | `ExamQuestionPoolService:285-296`, `generateLongExamParallel` of 48 | 2 parallel calls of 26 | same |

### What this means on the new models

- **The benchmark's single-call 20-question Long Exam does not represent production.** Production sends at
  most about 17 questions per call, and two at a time. The nearest benchmark figure is the 12-question
  Board Exam leg: Luna p50 36 s / max 50 s, Sol p50 70 s / max 89 s.
  - Per call, Long Exam's up-to-17 should sit between that leg and the single-call 20-question leg
    (Sol max 126 s).
  - **The exception is the 48-question Long Exam pool: 26 per call, larger than anything benchmarked.** On
    Sol that call has the least headroom against the 180 s read timeout (`application.yaml:202`). It
    deserves a slot in the targeted benchmark, or the pool size should drop.
- **Retry shape adds tail time, and the fallback is the riskiest call in the system.** If a parallel half
  fails, the code salvages (`OpenAiLlmStudyPackService:2077-2088`). If salvage fails, it calls
  `generateLongExam` **once for the whole set** (`:2092`): up to 30 questions, or 48 for a pool, in a single
  request.
  - The benchmark's single 20-question call already reached 126 s on Sol, so a 30-question fallback could
    exceed the 180 s timeout.
  - One Sol timeout can therefore become 180 s plus a fallback that also times out. The session's recovery bound is 30 minutes
  (`long-exam-session-bound-minutes`, `application.yaml:568`).
- **Board Exam sessions run their per-source calls in sequence.** With 3 sources on Sol, expect 3 × about
  70 s, roughly 3.5 minutes of background generation. That is acceptable for an async path, but the
  learner's "preparing" state must tolerate it.

### Operational risk the migration creates (feeds deliverable 8)

1. **Generation threads hold a database connection for the whole LLM call.** The executor's own Javadoc
   (`AppConfig`, `studyPackGenerationTaskExecutor`) says `LongExamService.generateLongExamAsync` wraps
   `execute(...)` around the call. The executor is held at 2 threads precisely because of that hold.
   - **Slower models lengthen every hold, by 2–3× on Sol.** The 2-thread bound still caps the count at 2, so
     the pool (20) is not exhausted. But both generation threads stay busy two to three times longer.
   - That queues Study Pack generation behind Board/Long Exam work, and the reverse.
2. **Challenge start runs its live generation INSIDE the request transaction, under row locks.**
   - `ChallengeQuizService` is `@Transactional` at class level (`:84`, `:220`).
   - Every start locks the Study Pack row (`findByIdAndOwnerUserIdForUpdate`, `:2561`).
   - A multi-note start also locks the user row (`:259`).
   - "+5" locks the session row (`:1106`).
   - All of these locks, and the request's connection (open-in-view is on; Tomcat is capped at 18 threads
     against a 20-connection pool, `application.yaml:80-95`), are held across the LLM call. Today that is
     p50 36 s / p90 73 s for 15 questions; on Sol it would be minutes.
   - **Exposure at today's traffic is low.** There are about 23 learner Challenge sessions in 30 days, and 4
     live starts since 2026-09-12. Two overlapping multi-minute holds are unlikely, so **this is not a
     pool-exhaustion argument.** It is the same mechanism as the 2026-09 incidents, but at a volume too low
     to matter today.
   - **The case for making Challenge start async on Sol rests on the learner path:**
     - a start that reaches the 180 s read timeout waits three minutes and then shows an error;
     - while it waits, the learner's own Study Pack row is locked against their other writes to it.

     Neither depends on traffic. **There is no shorter client-side timeout:** `lib/api.ts` sets none on
     `startChallengeQuizSession` (`:4526`). Render's proxy request limit was not checked here.
   - This confirms Feature Planner's row-lock finding (the locks are real), with that calibration.
3. **No routing defect is specific to Board or Long Exam.** Both use the free model like every other quiz
   method; the item 2 fix covers them (rows 6, 7). The owner's §5 asks for any confirmed defect to be in
   scope with regression tests: it is, through item 2's per-path test.

## 3. Open points for the owner and the item 2 brief

1. **Template seed routing (row 5):** Sol, per the curation rule.
   - Any backfill of the 432 unseeded packs then costs about 432 × one 20-question Sol call.
   - It runs on `llmParallelTaskExecutor`, the same pool as live Long Exam halves, so a backfill should be
     paced. `queueBackfill`'s own pacing has not been read yet.
2. **Long Exam pool, 26 per call:** include it in the targeted benchmark, or lower `LONG_EXAM_POOL_SIZE`.
   No learner has used it.
3. **Executor capacity:** making Challenge start async (Feature Planner's required item) must not use
   `studyPackGenerationTaskExecutor` while generation holds a connection there. Feature Planner's §2 flags
   the same; the brief must name the executor and its size.
