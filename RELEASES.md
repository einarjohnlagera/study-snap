# RELEASES.md - NoteLib

## v0.174.0 - Enhanced Generation

**Status: In Progress** (kicked off 2026-10-16 on `releases/v0.174.0`, **cut from `releases/v0.173.0`
(`7d1407a1`, signed off, NOT yet on `main`)** — owner decision 2026-10-16, the same pattern `v0.111.0`
and `v0.120.0` used. Anything that changes on `releases/v0.173.0` after this cut is merged forward.)

Theme: paid plans get the stronger model they were always tagged for. FREE moves to a newer model,
Plus and Pro to the stronger one, and official NoteLib curation is routed on purpose rather than by
the admin account's subscription tier, all before `gpt-4.1` retires on 2027-01-14.

**Why this release exists.**
- Every generation path ignores the plan tier. `ModelTier.PREMIUM` is written for paid plans
  (`StudyPackService.java:721`), but nothing reads it to pick a model. Nine `LlmStudyPackService`
  methods always use `LLM_MODEL_FREE` (`OpenAiLlmStudyPackService.java:231,251,288,1793,2199,2273`);
  premium is used only at `:273` (companion) and `:1860` (Interview Practice).
- The owner chose the new models on 2026-10-16: FREE → `gpt-6-luna`, Plus/Pro → `gpt-6.1-sol`.
- The planning package is in `docs/claude-plans/2026-10-16-v0174-llm-tiering-remediation-tightened.md`
  (plan), `…-audit-and-scope.md` (audit, 5a/5b trace) and
  `docs/claude-plans/2026-10-16-llm-model-migration-benchmark-design.md` (benchmark).
- Cost context (`docs/claude-findings/2026-10-16-study-packs-plantype-distribution-original-generations.md`):
  2,002 of 2,234 original Study Packs come from the owner's ADMIN account, which holds a
  non-billable PRO grant. No paid subscription is active. So the real Sol exposure is official
  curation, not subscribers.

**⚠️ MERGE GATE: this release's PR merges to `main` only after BOTH:**
1. `v0.173.0`'s PR #1505 has merged, with a merge commit (it is held for the
   `[CHECKPOINT — due 2026-10-22]` signal-9 read); and
2. the `[CHECKPOINT — due cache deploy + 7 days]` read of `v0.173.0`'s cache. Owner decision
   2026-10-16. Any `main` merge, and any Render env-var change, redeploys the backend. A deploy
   briefly runs two connection pools, which is a known kill mechanism and would muddy that read.
   `gpt-4.1` retires 2027-01-14, so waiting costs nothing.
- The owner's Render env-var change for the new models is held to the same gate.

### Planned Scope

1. **Model benchmark (read-only, owner-approved $10 spend cap). Runs FIRST, and gates item 3.**
   - Candidates: `gpt-4.1-mini` (baseline), `gpt-6-luna`, `gpt-6.1-sol`. Design in
     `docs/claude-plans/2026-10-16-llm-model-migration-benchmark-design.md`: deterministic metrics
     plus blind-adjudicated quality.
   - Sol runs at its documented default reasoning effort. Measure billed reasoning tokens, quality,
     latency and retries; tune effort afterwards.
   - **$10 is a maximum, not an allowance for unlimited runs.** The execution plan (who runs it,
     where the API key lives, the per-run cost estimate) goes to the owner before any spend. No
     production writes.
   - It also verifies API compatibility of both new models with the current request shape.
2. **Plan-tier-aware model routing (backend, Tier H, Codex at high effort under D10).**
   - Thread a tier into all nine `LlmStudyPackService` methods and their callers across
     `StudyPackService`, `AdminStudyPackTransactionHelper`, `ChallengeQuizService`,
     `ExamQuestionPoolService`, `QuickReviewAdaptivePracticeService`,
     `OfficialChallengeQuizTemplateService` and `LongExamService`, so FREE resolves to the free
     model and Plus/Pro to the premium model.
   - **Official curation:** generation by a `UserRole.ADMIN` account resolves to the premium
     model **regardless of its plan** (owner, 2026-10-16). Do NOT use
     `CuratorAuthoringPredicate.isCurator()`: it also matches TEACHER profiles and would route
     teachers' personal notes to Sol.
   - **Two separate checks, one destination (Feature Planner, 2026-10-16):** Plus/Pro reach the
     premium model through the plan tier; ADMIN curation reaches it through its own `UserRole.ADMIN`
     check.
     - Do not fold curation into a shared `ModelTier` value. `model_tier` stays as it is:
       `planType.isPaid()` at `StudyPackService.java:721`, which is `PLUS || PRO` only
       (`PlanType.java:8-10`).
     - So the admin's non-billable PRO grant is already tagged `PREMIUM` today. That is why 98% of
       rows carry the tag.
     - Item 4's monitor therefore identifies curation by the owner account's role plus
       `model_used`, never by `model_tier`.
     - The role check is what keeps curation on the premium model if the admin account's plan ever
       changes.
   - **Tier H triggers:**
     - per-generation cost (money semantics);
     - a shared-method change across 7 services;
     - the Challenge Quiz and exam-pool routing defects that 5b confirmed.
   - **Owed:** regression tests per quiz mode; plus one test per path that asserts the model each
     plan tier actually sends, not just that a tier parameter exists.
   - Interview Practice and the companion already use premium, and both are paid-gated
     (`StudySnapProperties.java:308-317`; companion only while `FREE_MONTHLY_ASK_COMPANION_LIMIT`
     stays 0). They stay as they are.
3. **Model defaults to Luna / Sol (config + an OWNER env-var handoff). Gated on item 1's result.**
   - Update the `LLM_MODEL_FREE` and `LLM_MODEL_PREMIUM` defaults in `application.yaml`, plus any
     request-shape change the benchmark shows the new models need.
   - The production values are Render env vars, and **changing them is an owner action**. Claude
     writes the handoff and never calls the Render MCP's env-var update. They change only after
     the merge gate above.
   - `LLM_MODEL_CRITIQUE` is NOT changed here; see Deferred.
4. **Curation cost monitoring (backend, Tier N).**
   - A scheduled, read-only monthly sum of the ADMIN account's premium-model token use, priced at
     configured per-token rates, logged against an owner-set threshold. **It alerts and never
     blocks** (owner, 2026-10-16).
   - No dollar cost is stored anywhere today (`study_packs.estimated_cost` is NULL on every row).
   - **Owed from the owner before the brief:** the monthly threshold.

**Owner decision on the benchmark (2026-10-17, verbatim in
`docs/claude-plans/2026-10-17-owner-conditional-approval-challenge-quiz-ux.md`). Conditional approval of the strategy,
not approval to deploy.**
- **APPROVED:** FREE → `gpt-6-luna`.
- **CONDITIONALLY APPROVED:** Plus/Pro → `gpt-6.1-sol`, and official curation → Sol regardless of the admin
  account's plan, within the agreed cost caps and monitoring.
- **Condition: Challenge Quiz start latency is a product UX issue this release must address, not an accepted
  caveat.** Do not treat the 5-question benchmark leg as a proxy for the real 10/12/15-question and multi-note
  starts.
- Ten deliverables are owed before final rollout approval:
  1. the updated v0.174.0 scope;
  2. the model-routing matrix for FREE, Plus, Pro and curation;
  3. the start-latency root cause;
  4. a recommended UX solution;
  5. a comparison of solutions;
  6. a targeted-benchmark proposal for the real start sizes, needing owner approval before any paid call;
  7. Board Exam and Long Exam validation on the production paths, including the parallel Long Exam variant;
  8. a monitoring and rollback plan;
  9. the split between v0.174.0 and future releases;
  10. the remaining decisions.
- Split by role (agreed between sessions 2026-10-17): **Feature Planner** owns 3, 4 and 5; **Release
  Implementor** owns 1, 2, 6, 7, 8, 9 and 10.
- The decision supersedes the separate Product UX consultation: Feature Planner's deliverables 3–5 answer the
  prompt's questions. The prompt stays as input.
- **Owner decisions, 2026-10-17 (later):**
  - **Targeted benchmark: option A approved.** It runs Tier 1 (10/12/15-question starts and multi-note
    3×6 and 6×3) plus the lower-reasoning-effort variant, under a **$4.50 hard stop** that includes the smoke
    run (`docs/claude-plans/2026-10-17-v0174-targeted-challenge-start-benchmark-proposal.md`).
    - The harness change is test scope only and goes to Codex on its own PR.
    - The owner runs the paid calls.
  - **Long Exam pool: lower it rather than benchmark the 26-question pool call (owner).** The owner chose to
    lower it; **Release Implementor chose 30** (`long-exam-pool-size`, `application.yaml:249`;
    `StudySnapProperties:169`).
    - **Trade-off:** at 30 a pool serves one exam and is then refreshed (`ExamQuestionPoolService:134`), so
      reuse across sessions effectively ends. No learner uses Long Exam today.
    - Item 3 checks that no validator or test assumes the pool is larger than the exam (`examSizeForMode`).
    - 30 is the floor: a pool must hold at least one whole exam (`ExamQuestionPoolService:116-123`), and
      the largest Long Exam is 30 questions (`StudySnapProperties:168`).
    - At 30 the pool generates as two calls of 17, the same size a session already sends.
    - It lands with item 3's `application.yaml` defaults. Existing pools are untouched, since none has been
      served and no regeneration is allowed. If `LONG_EXAM_POOL_SIZE` is set on Render, changing it is an
      owner action.
  - Still owed: the acceptable Challenge start wait, for FREE and for Plus/Pro. The benchmark results are
    read against it.
- Item 3's `application.yaml` default for `LLM_MODEL_PREMIUM` and the item 2 brief wait on these deliverables.
  So does any Challenge-specific routing: deliverable 4 may propose one, and it would be a third destination
  needing an explicit owner decision.

**Anti-drift:**
- No change to Plus/Pro entitlements, `FeatureGateService` quotas or `UserUsageEntity`.
- No pricing, plan copy or announcement. If customer-facing copy is ever needed, it says "Enhanced
  Study Pack Generation" for Plus/Pro and never names an internal model.
- No mass regeneration, and no historical `model_tier` or `model_used` rewrite. 5a: those columns
  correctly record who last generated each pack.
- No prompt-content change beyond what model compatibility strictly requires.
- No migration.
- No checkout/payment path.
- No Hikari pool change.
- Existing notes, Study Packs and learner progress are untouched.

**Deferred, each to be scoped on its own once it has data** (decision 1, approved 2026-10-16):
- the optional regeneration UX; quiz-only regeneration stays a feasibility assessment;
- Sol-as-checker quiz-pool remediation, which needs the benchmark numbers and selective validation
  first;
- the `LLM_MODEL_CRITIQUE` migration, which needs its own feedback-quality check (tightened plan
  §10) and must also land before 2027-01-14;
- pricing/subscription copy and announcements;
- retention instrumentation.

**Verification tier:** Tier H, with at least one scoped cold falsification pass at pre-signoff.
Item 2 changes a shared method and per-generation cost, so falsify both the tier resolution and the
ADMIN carve-out.

**Kickoff scan (2026-10-16):**
- One unindexed plan file (the `v0.173.0` Product UX consultation prompt) was moved to
  `docs/claude-plans/done/`: a consumed release artifact.
- 16 overdue `[CHECKPOINT]` rows are carried unread, not actioned at this kickoff. Among them are
  the 7 long-running ones (`v0.107.0`, `v0.90.0`, `v0.106.0`, `v0.97.0`, `v0.111.0`, `v0.96.0`,
  `v0.74.0`).
- New this scan: the `v0.166.0` Hikari checkpoint row's title still reads open although its Status
  records the 2026-10-14 read. That is a title/status contradiction, left for a dedicated pass.
- RC-3's reminder-timing checkpoint falls due 2026-10-21, inside this release.
- `v0.168.0` was archived from both files; no line was lost, checked against the pre-move copies.

### Shipped

- **Item 1, part 1: the benchmark harness (PR #1506, `8d417a35`, test scope only). No API call made
  and nothing spent yet; the owner runs the paid smoke and full runs.**
  - `backend/src/test/java/com/studysnap/backend/benchmark/`. `LlmModelBenchmark` runs the real
    `OpenAiLlmStudyPackService` and production prompts on one candidate model. It is skipped unless
    `-Dllm.benchmark=true`, so a normal build and CI never run it. No file under `backend/src/main`
    changed.
  - `MeteringInterceptor` meters every HTTP call, including the service's own retries. It stops
    before the next call at `spendStopUsd` (hard max $10) or `maxCalls`, and records raw `usage`
    including reasoning tokens. It never saves the request or its headers.
  - The README sets explicit per-model stops totalling $9.25 under the $10 cap: smoke $0.25 × 3,
    then full runs at mini $1.50, Luna $0.75, Sol $7.00. Without them, the $8 per-command default
    would have allowed up to $24.
  - `sample-query.sql` is one read-only `SELECT` producing 81 public-note lines (A 40, B 32, C 5,
    D 4). Its output holds note text, stays under `backend/target/`, and is never committed.
  - Codex review P2, fixed with a regression test that fails without the fix: a transport failure
    was classified `EMPTY`, which distorted that metric and bypassed the 3-consecutive-errors stop.
  - Verification: full `./mvnw clean install` with Docker, 2,713 tests, 0 failures, before the
    test-only P2 fix; `LlmModelBenchmarkTest` 12/12 after it.
  - **Results caveat:** Leg B reuses each note's existing `gpt-4.1-mini` Study Pack as identical
    input for all three models; the context fields approximate `StudyPackGenerationContextResolver`.
  - Findings for item 2/4: `LlmResponseUtils` takes the first `output_text` and skips reasoning
    items, so a second `output_text` is silently ignored; `extractUsageMetadata` does not record
    reasoning tokens, which item 4's cost monitor needs.
- **Item 1, part 2: benchmark run and results (2026-10-17). COMPLETE; item 3 is the owner's decision.**
  Results and recommendation: `docs/claude-findings/2026-10-17-v0174-llm-benchmark-results.md`.
  - The owner ran the smoke run and the three full runs: about **$2.62** of the $10 cap. Billed cost
    is lower: `output_tokens` already includes reasoning, and cached input is billed below list price.
  - Generations succeeded: `gpt-4.1-mini` 77/81 (3 timeouts at 180 s, 1 invalid Long Exam format),
    `gpt-6-luna` 81/81, `gpt-6.1-sol` 81/81. No multi-output responses and no H4 flags on any model.
  - Blind tier, 20 items per model, scored by a fresh agent: no regression detected for either
    candidate. Differences between the means are within noise and are not evidence for an "Enhanced"
    quality claim.
  - Cost per generation: mini $0.0033, Luna $0.0015, Sol $0.0272.
  - Recommendation: both candidates pass the pre-stated rule; Sol's cost is accepted under the execution
    plan's own clause. Caveat for items 2 and 3: STARTING a Challenge Quiz waits in the foreground for 10–15
    live questions whenever the learner's bank and the Official template fall short (33 starts by 29 of
    34 learners since 2026-07-24). That wait is already p50 30 s / p90 62 s on mini, and projects to about
    55–90 s on Luna and 90–175 s on Sol, near the 180 s timeout. "+5" is almost always served from the
    bank (2 live batches for learners). The owner made it a release condition on 2026-10-17 (see Planned Scope); Sol's single-call Long Exam took up to 126 s against the 180 s timeout.

- **Item 1, part 3: the Leg E harness for the targeted Challenge start benchmark (PR #1507, `5dffd5e6`,
  test scope only). Nothing spent yet; the owner runs the paid calls.**
  - `backend/src/test/java/com/studysnap/backend/benchmark/` gains cells S15 / S10 / S12 (15 `hard`,
    10 `easy`, 12 `medium`, read from the `ChallengeQuizService` constants), M6x3 and M3x6 (multi-note
    starts as sequential per-source calls), and VLOW (S15 with `reasoning.effort: "low"`, injected by the
    interceptor on every attempt).
  - It mirrors production's avoid-lists. Single-note uses the pack's saved quiz (`:396,463`). Multi-note
    starts empty, accumulates, aborts on a failed source, and dedups the final set against the primary
    pack's quiz (`:421,1831-1860`).
  - It records accepted vs requested questions, the sequential start latency, the slowest source call, the
    failed source index and timeouts. The summary excludes spend-stopped runs.
  - The pre-flight fails the command before any HTTP call. `sample-query-e.sql` is one read-only `SELECT`;
    a count-only version run against production returned all 52 paired lines.
  - Run sheet: ten commands with explicit stops totalling $4.10, about $4.45 worst case with a one-call
    overshoot per command, under the approved $4.50.
  - Release Implementor fixed two issues after Codex, each with an assertion mutation-checked to fail
    without its fix: a malformed line no longer passes silently, and spend-stopped runs no longer skew the
    latency summary.
  - Verification: `./mvnw install` with Docker, 2,723 tests, 0 failures, including
    `NativeQueryPostgresIntegrationTest` (115). No file under `backend/src/main` changed.
  - Known difference from production: multi-note cells use `medium`, but a learner's last Quick Review
    score can make it `easy` or `hard`.
## v0.173.0 - Cached Catalog

**Status: Released** (signed off 2026-10-16; **merge to `main` HELD for the 2026-10-22 gate, so NOT yet deployed**. Kicked off 2026-10-15 on `releases/v0.173.0`, cut from `docs/v0.172.0-deploy-stamp`
(`3961383e`), which is `main` after `v0.172.0` merged as #1501 plus one docs commit.)

Theme: take the remaining repeat load off the production database's Explore discovery reads by
caching the copies/likes/views aggregates in-process for a short TTL, so most page views stop
recomputing them at all.

**Why this release exists.** `v0.172.0` made each discovery call compute the three engagement
aggregates once instead of once per section. The fix plan's durable follow-up is to stop computing
them on every call: an in-process Caffeine cache, TTL 60–300 s
(`docs/claude-plans/2026-10-14-db-oom-memory-ceiling-fix-plan.md`, Leg D "Durable follow-up"). The
owner deferred it to this release on 2026-10-14.

**⚠️ MERGE GATE: the release PR merges to `main` only AFTER the `[CHECKPOINT — due 2026-10-22]` read.**
- That checkpoint measures `v0.172.0` (Leg D) plus Leg B with **no** cache, over 7 days from the
  2026-10-15T07:09:09Z deploy. Shipping the cache inside that window would make the read unattributable.
- Every `main` merge also triggers a Render deploy, and the deploy double-pool overlap is itself a
  known kill mechanism. So nothing merges to `main` before 2026-10-22T07:09Z.
- **The read can redirect this release.** ≥ 5 kills: Leg A (plan upgrade) becomes the next lever and
  the cache alone is not enough; the owner decides whether this release still ships. 2–4: proceed as
  planned. 0–1: the checkpoint is silent on the cache; owner call after the read.
- Work can be built, audited and merged into `releases/v0.173.0` before then.

### Planned Scope

1. **Discovery ranking cache (backend, Tier H, Codex). ✅ Merged #1503 (`70fdd019`), 2026-10-15; see Shipped.**
   - **Tier N → H (2026-10-15, before the brief):** single-flight under concurrent load is the charter's concurrency trigger, and a waiter holding a JDBC connection under open-in-view would reproduce this method's own leak traces. A scoped cold falsification pass is owed at pre-signoff. Brief: `docs/codex-prompts/v0.173.0-discovery-ranking-cache.md` (no stale-on-error: `expireAfterWrite` 300 s keeps Product UX's bound).
   - Add Caffeine and cache the copies/likes/views aggregates behind
     `NoteService.getPublicLibraryDiscoverySections` for a short TTL (fix plan: 60–300 s).
   - **Product UX decision, 2026-10-15 (APPROVED with safeguards; consultation prompt
     `docs/claude-plans/done/2026-10-15-discovery-cache-product-ux-consultation-prompt.md`):**
     - **Cache the ranked id lists of all three sections (Featured, Most Popular, Recently Added) as
       one coherent snapshot, TTL 300 s.** Existing ranking and exclusion semantics are preserved.
       Card counts and the viewer's liked state stay live per request, and are never cached.
     - **No early invalidation**: not on publish, unpublish, copy, like or view.
     - **The per-request `visibility = 'PUBLIC'` re-check stays on every response, cache hits
       included.** A private or deleted note drops out at once, even if a section then shows fewer
       than 6 cards.
     - No user-facing freshness notice, and no promise that publishing lands a note in Recently Added
       (it orders by creation date, which this release must not change).
     - **Safeguards to verify:** concurrent misses must not each run the ranking query
       (single-flight). A failing refresh must not cause a retry storm. Cold-start, expiry and
       failure behaviour must be tested under concurrency. No per-copy or per-like invalidation, and
       no expensive per-request backfill.
     - **Post-deploy:** compare the 7-day signal-9 count against the baseline of 9. Add bounded
       instrumentation for ranking computations, hit/miss, refresh failures, ranking duration and
       short (< 6) sections.
     - **Why the copy-count concern is gone:** counts are loaded per request by
       `NoteService.toListItems`, not by the ranking, so #1500's in-page freshness is unaffected.
   - **Re-read 2026-10-15, read-only:** backend `numInstances` = 1 (Render `get_service`), so an
     in-memory cache is consistent across visitors.
   - Guard (fix plan): a test asserting the aggregate computation runs at most once per TTL window
     across repeated `getPublicLibraryDiscoverySections` calls, plus a real-request test if any
     endpoint changes.
2. **Carried docs commit (no code). `[merged-to-release: 3961383e]`** Stamps the `v0.172.0`
   production deploy time (2026-10-15T07:09:09Z) into the `ROADMAP.md` Backlog rows. It rides in via
   this release instead of PR #1502, so it does not trigger its own deploy mid-checkpoint window.

Anti-drift:
- **Caffeine is the one allowed new dependency.** No Redis, no HTTP cache headers, no Next.js
  `revalidate` change.
- No Hikari pool-size change (pinned by the 09-04 outage fix and
  `TomcatThreadPoolHikariAlignmentTest`).
- No change to FEATURED/POPULAR weights, decay, thresholds, or the paginated listing's SQL (pinned by
  the 14 golden SQL files from #1498).
- No checkout/payment path (RC-4 baseline) and no public-note/adoption path (RC-5 read).
- No migration.

**Open at kickoff, not scoped:**
- `[CHECKPOINT — due 2026-10-22]` (signal-9 kills; read by 2026-10-29 at the latest, Render log
  retention). RC-3 checkpoint due 2026-10-21.
- Overdue checkpoint reads carried from `v0.172.0`: `v0.107.0`, `v0.90.0`, `v0.106.0`, `v0.97.0` (due
  10-13); `v0.111.0`, `v0.96.0` (09-28); the `v0.74.0` conversion read (09-30).
- The `v0.166.0` Hikari checkpoint row's title still reads open, though the `v0.171.0` kickoff read
  it (cell contradiction, not re-read here).
- Owner: re-run the admin Official Challenge backfill seed for note `0c6b0be8`.
- Workflow redesign: `v0.172.0` ran two Tier-N items through the new worktree lifecycle, but no M5
  pilot measurement was recorded. D4/D9 stay gated on it.

### Shipped

1. **Explore discovery rankings cached for 300 s** — #1503 (`70fdd019`), Tier H.
   - `PublicLibraryDiscoveryRankingCache` holds one viewer-independent snapshot of the Featured /
     Popular / Recent id lists. It is a Caffeine `AsyncLoadingCache` (`:51-55`): the 300 s TTL counts
     from completion, concurrent misses share one computation and its failure, and a failure is not
     cached.
   - The ranking runs on `discoveryRankingTaskExecutor` (`AppConfig.java:35`) in its own read-only
     transaction. `NoteService.getPublicLibraryDiscoverySections` (`:940-942`) is `NOT_SUPPORTED`, so
     waiting requests hold no JDBC connection.
   - Card projections, counts, the `visibility = 'PUBLIC'` re-check and the viewer's liked state run on
     every request. There is no early invalidation and no stale-on-error. Repository SQL, ranking
     formulas, the pool and migrations are unchanged; the one new dependency is Caffeine 3.2.3, from
     the Boot BOM.
   - Instrumentation:
     - `DISCOVERY_RANKING_CACHE computed` (INFO): `durationMs`, the three list sizes, cumulative
       hits/misses/failures, and `shortSectionResponses`. The line emits; it appeared 13 times in the
       full build.
     - `DISCOVERY_RANKING_CACHE failed` (WARN).
     - A request that joins a ranking still in flight counts as a **hit**: in a cold-start burst of 8,
       7 show as hits.
   - Verification:
     - Full `./mvnw clean install` with Docker: 2,694 tests, 0 failures.
     - `concurrentHttpDiscoveryWaitersDoNotHoldJdbcConnections` (real HTTP, 8 concurrent requests,
       ≤1 active connection) fails with **9** when the method is reverted to `readOnly = true`. That
       mutation was run by Release Implementor.
     - `inFlightRankingSurvivesTtlAndExpiresFromCompletion` fails with 2 rankings under insertion-time
       expiry. That mutation was run by Codex; the test was read by Release Implementor.
     - Two defects in Codex pass 1 were confirmed and fixed in fix-1: the TTL started at insertion, and
       the connection guard bypassed the real request path.
   - **Known gaps (as merged in #1503; the first two are closed by item 2):**
     - No test asserts that `shortSectionResponses` rises.
     - The HTTP test's identical-body check is trivial, because every section is stubbed empty.
     - ~~`future.join()` has no timeout. That is no worse than the previous per-request query.~~
       **Corrected 2026-10-16: this was wrong.** The old query failed alone. Here a stalled ranking left an
       in-flight future that never expired, and every request pinned a Tomcat thread (25 total) on it.
       Health checks starved, and Explore stayed frozen until restart. The cold pass found it (C8), and
       item 2 fixes it.
   - The Tier-H cold falsification pass (Opus, read-only) ran on 2026-10-15. It found no blockers;
     its findings are resolved under item 2.

2. **Stalled rankings bounded and the cache hardened** — #1504 (`ff555530`), Tier H, Codex at high
   effort (D10).
   - **C8 (stall):** the ranking transaction has a 50 s statement timeout
     (`PublicLibraryDiscoveryRankingCache.java:40`, via `TransactionTemplate.setTimeout`). The loader
     future has a 55 s `orTimeout` backstop (`:41`). Both are under the 60 s Hikari leak threshold.
     - 50/55 rather than the 30/45 that was built: before `v0.172.0`, this endpoint held connections over
       60 s three times during CPU spikes. A shorter cancel risks a cancel-and-retry loop with no backoff.
       The owner chose this on 2026-10-16.
     - A timed-out ranking is not cached, so the next request retries.
   - **Rejection:** when the queue of 16 is full, the load fails at once and reports one failure, with
     no orphan timer. A queued load whose future already timed out skips the ranking.
   - **Accounting (both Codex-review P2s):**
     - The success line and the `shortSectionResponses` reset happen only when `complete()` delivers
       the snapshot.
     - The counter is captured before publishing and added back if the snapshot is not delivered. That
       last race can't be reproduced by a test, so this fix is unverified.
     - Failures, including `Error`, are counted once per load.
   - **Missing decorator:** `InFlightThreadRegisteringTaskDecorator` is now set on
     `discoveryRankingTaskExecutor`, pinned by
     `AppConfigTest.discoveryRankingExecutorIsBoundedAbortOnOverflowAndRegistered`.
   - **C10 (test gaps):**
     - The pagination fixture now has 7 notes, so a backfill would be detected, and it asserts
       `shortSectionResponses`.
     - The cold test is now `coldRequestsShareOneRanking`; its "no connection" half could not fail, so
       that claim was dropped there and is left to the HTTP test.
     - The HTTP test now sends one authenticated (JWT) request.
   - The inert `invalidateAll` call in the pagination test was removed.
   - **New tests:**
     - `PublicLibraryDiscoveryTimeoutPostgresIntegrationTest.lockedNativeRankingIsCancelledServerSideAndNextRequestRetries`
       (Docker): an `ACCESS EXCLUSIVE` lock on `notes` checks the server-side cancel, the worker being
       released, and recovery after unlock.
     - Unit tests for a late result after the wait timeout, a rejected submission, a timed-out queued
       load, and a failure logged once.
   - **Verification:**
     - Full `./mvnw clean install` with Docker: 2,701 tests, 0 failures, at 30/45 and again at 50/55.
     - Mutations run by Release Implementor:
       - Removing the transaction timeout fails the lock test at "each caller sees PostgreSQL
         cancellation before the 5 s backstop".
       - Removing both bounds now fails in about 7 s. Before fix-1, the test hung instead.
       - Removing `readOnly` gives 9 connections against 1 in the HTTP test.
     - Codex killed the complete-guard, rejection-handling and decorator mutations.
   - **D10 cost (gpt-6-sol), as 5h-window / weekly `used_percent`:**

     | Run | Effort | 5h window | Weekly |
     |---|---|---|---|
     | Hardening pass | high | 49→72 | 8→11 |
     | Review | medium | +2 | 11→12 |
     | Fix-1 | high | 0→7 (after the 5h reset) | 12→13 |

     That is two D10 runs this week on one item, and the owner was told.
   - **Known limitations (cold-pass notes, not fixed):**
     - **A dead socket can't be cancelled.** The worker stays stuck until restart. Each request fails
       after 55 s, and requests fail at once once the queue of 16 fills. Tomcat threads stay free, so
       health checks don't restart the service, but Explore is down until restart.
     - **C2:** for a few microseconds after a failed ranking, a request can join the failed future.
       Retries have no backoff.
     - **C7 (log emitters):**
       - One failure also emits Caffeine's own WARNING and one `unhandled_exception` ERROR per waiting
         request. Count failures from `DISCOVERY_RANKING_CACHE failed` only.
       - Joining an in-flight ranking counts as a hit.
       - `shortSectionResponses` resets after each delivered computation.
     - The HTTP test's identical-body check is still trivial.
     - **C11:** the doc-wording notes were folded into `docs/features/public-library.md`.

3. **Owner action: four residual pre-H4 answer-key defects repaired in `study_packs.quiz`** — 2026-10-16,
   run by the owner with psql.
   - Source: `docs/claude-findings/2026-10-15-quiz-quality-retention-llm-cost-production-read.md` §2
     (Feature Planner / Prod Investigator).
   - Script: `docs/claude-plans/done/2026-10-16-study-packs-residual-answer-key-defects-repair-tightened.sql`.
     It is the Release Implementor's tightened copy of the Prod Investigator's
     `done/2026-10-15-…-repair.sql`. It adds a question-text match (D3's choices are generic), keeps array
     order (`WITH ORDINALITY … ORDER BY`), and runs as one transaction with checks that abort it.
     Before production, it was tested in a throwaway local Postgres, with a decoy question sharing
     D3's choices and wrong index.
   - Corrections:
     - D1 audit detection risk: 0.06/(0.4×0.5) = 0.3, so idx 3 → 0.
     - D2 labor efficiency variance: (120−100)×$15 = $300 unfavorable, so idx 0 → 2.
     - D3 weighted average cost: $10,000/500 = $20, so idx 2 → 1.
     - D4 straight-line depreciation: ($50,000−$5,000)/9 = $5,000, so idx 3 → 0, on the source and
       both public-note copies.
   - Owner run: `UPDATE 1/1/1/3`, the check block passed ("6 packs corrected"), and `COMMIT`.
   - **Verified read-only by Release Implementor, after commit:**
     - In each of the 6 packs, only the targeted question's `correctIndex` changed, compared with a
       per-question read taken before the repair.
     - Question order is unchanged, and every quiz still has 5 questions.
     - `quiz_stamp` (0) and `updated_at` are unchanged, so no Quick Review mastery was reset.
     - A whole-table recount finds 0 wrong keys for all four defects, and 3 corrected D4 copies.
   - Exposure: one incomplete owner test session existed on these packs. The real learner's D4 copy
     had no sessions.
   - Not repaired, by design: the APGAR, percentage-tax and grain-size candidates were re-derived as
     correctly keyed (false positives).

### Signoff (2026-10-16)

- **Signed off ahead of the merge, owner decision 2026-10-16**, so `v0.174.0` can be cut from this
  branch. The merge gate is unchanged: the release PR merges to `main` only after the
  `[CHECKPOINT — due 2026-10-22]` signal-9 read (by 2026-10-29 at the latest). If that read shows
  ≥ 5 kills, the owner decides whether this release still ships, and any change made here is merged
  forward into `v0.174.0`.
- **Interim reads at signoff (read-only, NOT the verdict):**
  - 0 `terminated by signal 9` lines from 2026-10-15T07:09:09Z to 2026-10-16T03:14Z. The filter was
    checked against the known 07:08:54Z kill.
  - 1 `Apparent connection leak` trace since the `v0.172.0` deploy: 2026-10-15T14:25:32Z on
    `PublicProfileController.getByUsername`, returned to the pool about 70 s after acquire. That is a
    `v0.166.0` method, not discovery, and is recorded on its checkpoint row.
- **Scope completeness:**
  - Item 1 shipped: #1503 plus the #1504 hardening, file:line anchors under Shipped.
  - Item 2 shipped: the carried deploy-time stamp `3961383e` is on this branch.
  - Item 3 is an owner action, done and verified.
  - Nothing was planned and left unbuilt.
- **Pressure test:** one scoped cold falsification agent (Opus, read-only), Tier H for concurrency.
  It found no blockers; every finding is fixed or listed as a known limitation under item 2.
- **Feature-doc drift:** `docs/features/public-library.md`'s cache paragraph was re-read against the
  final `PublicLibraryDiscoveryRankingCache`. These all match:
  - the 50 s / 55 s bounds (`:40-41`);
  - a failure is not cached;
  - immediate rejection when the queue is full;
  - no viewer id;
  - no backfill;
  - the log and counter semantics.
- **Backlog:**
  - The "Production DB memory ceiling" and discovery long-hold `[DECISION]` rows are updated: cache
    built, deploy held.
  - The `v0.166.0` Hikari checkpoint row carries the `getByUsername` recurrence.
  - A new `[CHECKPOINT]` row covers the cache itself, dated from its deploy.

## v0.172.0 - Headroom

**Status: Released** (signed off 2026-10-15 on `releases/v0.172.0`; kicked off 2026-10-14, cut from
`main` after `v0.171.0` merged as #1497. PRs #1498, #1499, #1500.)

Theme: stop the production database being pushed past its 268 MB memory ceiling. The cheapest
already-diagnosed code legs land here: stop the Explore discovery sections computing the same
engagement aggregates twice per call, and stop them re-fetching on every tab switch. Close the
Challenge `+5` duplicate-question HTTP 500 alongside them.

**Why this release exists.** At this kickoff, the database I/O bursts the Backlog Index attributed to
deploys were traced to the database itself. `notelib-db-prod` (`basic_256mb`) had **9 backend kills by
signal 9 in 7 days** (2026-10-08 → 10-14), each followed by a full Postgres crash-recovery that drops
every in-flight request. Daily memory peaks hit 251–268 MB against a 268,435,460-byte limit on **every
day from 09-15 to 10-14**: a long-standing undersizing, not a regression. Prod Investigator root-caused
two mechanisms (`docs/claude-findings/2026-10-14-db-oom-memory-ceiling-public-catalog.md`; fix plan at
`docs/claude-plans/2026-10-14-db-oom-memory-ceiling-fix-plan.md`):
- **Deploy overlap.** The old instance keeps its 20-connection pool open 39 s–2 min after the new
  instance is live. All 4 deploy-adjacent kills fit this, but the overlap is not sufficient alone:
  10-12 had the same overlap with no kill.
- **CPU-saturated public-catalog reads.** These precede most, not all, non-deploy kills.

Both are made worse by a memory floor sitting within ~45 MB of the ceiling.

**Owner decisions, 2026-10-14:**
- **Leg A (plan upgrade): not now.** There are no paying users yet.
- **Leg B: yes**, as an owner `SET`.
- **Leg D: yes**, as a single-statement rewrite, not Java-side ranking, because Java-side ranking
  would duplicate the FEATURED/POPULAR scoring formula that the paginated Public Library listing
  keeps in SQL.
- **Durable aggregate cache: next release.**
- **Legs C, E, F: not scoped.**

### Planned Scope

1. **Discovery sections: compute the engagement aggregates once (Leg D, Option 1), and stop
   re-fetching on discovery-overview re-entry (backend + frontend, Tier H, Codex). `[shipped: #1498 4871be7e + follow-up #1500 fc7da866]`**
   - **Re-tiered N → H (2026-10-15):** delivery introduced defects that this session then fixed, which is the charter's Tier H trigger. Pass 1 sorted the whole public catalog twice with ~2,437-byte rows (prod `EXPLAIN`, and Codex's own review). Pass 2 moved `n.content` into a 1,290-byte-wide hash join inside the ranking subquery (prod `EXPLAIN`). Pass 1's H2 oracle tie-check also sorted UUIDs in Java (signed `compareTo`), so it passed or failed depending on the random ids; Release Implementor rewrote that one assertion as `subList(0, 2).containsExactlyInAnyOrder(...)`. The oracle comparison itself is unchanged. A scoped cold falsification pass is owed at pre-signoff (it can be batched with item 2). The paginated listing's golden SQL files were verified against the pre-refactor code (`fcfae82b`) in a throwaway worktree.
   - **Backend.** `NoteService.getPublicLibraryDiscoverySections` (`NoteService.java:953-960`) runs
     the ranked query separately for `FEATURED` and `POPULAR`. Each run builds the
     `RANK_COPIES_JOIN`/`RANK_LIKES_JOIN`/`RANK_VIEWS_JOIN` aggregates from scratch
     (`PublicLibraryRepositoryImpl.java:534-564`). Rewrite it so the three aggregates are computed
     once per call and both rankings read from them. `POPULAR` must still exclude `FEATURED`'s
     returned IDs. Same results, same ordering, same thresholds.
   - **One scoring definition.** The paginated listing (`NoteService.java:829`, `:915`) keeps using
     the existing fragments, so there must be exactly one definition of the scoring formula.
   - **Frontend.** `public-library-page-client.tsx:648-675` re-requests discovery sections each time
     discovery overview is re-entered (`?view=` round-trip, filter cleared; Explore only `hidden`-toggles the tab, so its tab switch does not remount). Reuse the already-loaded response instead.
   - **Guards.**
     - A test asserting the aggregate computation runs at most once per call.
     - The rewritten SQL covered by `NativeQueryPostgresIntegrationTest`.
     - A result-equivalence test against the old two-query output on seeded data.
2. **Challenge `+5`: duplicate bank question no longer 500s (backend, Tier N, Codex). `[shipped: #1499 89f4edad]`**
   - **Audit (2026-10-15).** Both bank writers (`persistGeneratedQuestions`, which also serves
     `seedTemplateAsync`, and `copyTemplateQuestions`) now go through one native
     `insertIfAbsent … on conflict do nothing`. A skipped question is still served in its session but
     is not banked or claimed for it. Real-PostgreSQL guards: same key twice at the same level, at a
     NULL level and at a different level; a two-connection race; `+5` with a pre-existing duplicate;
     bank rows present after start, `+5`, seed and copy; and a jsonb round-trip through the entity.
     Removing `on conflict do nothing` fails three tests by name, including the end-to-end `+5` test.
     Removing `flushAutomatically` fails nothing, so the start-path row-existence test guards the
     outcome but does not prove the unflushed-session state was reproduced; the flag stays as a
     harmless safeguard. The three claim queries gained `cast(:learnerLevel as string)`, which changes
     no behaviour (verified by reverting it). It is needed only because `NativeQueryParameterTypingTest`
     scans whole files and flags JPQL `:param is null` once a file holds a native query; narrowing
     that lint is a follow-up. Full `./mvnw clean install` with Docker: 2685 tests, 0 failures.
   - **The defect.** `ChallengeQuizQuestionBankService` de-duplicates new bank rows only within the
     incoming batch (`:101-106`), never against rows already in the bank. A same-level repeat of
     `question_key` therefore hits `uq_challenge_quiz_question_bank_user_pack_key` and rolls back the
     request as HTTP 500. This is documented as a known limitation in `docs/features/challenge-quiz.md:119`.
   - **The fix.** Make the insert itself skip duplicates, for example `ON CONFLICT DO NOTHING` on that
     key, so a concurrent `+5` or `seedTemplateAsync` cannot race a pre-read. The exact mechanism is
     settled in the brief.
   - **Guard.** A real-PostgreSQL test inserting the same key twice.
   - **Evidence.** No occurrence in the 7 retained days of logs, so this is a latent-defect fix, not
     an active incident.

**Owner action, outside the code release:** Leg B,
`ALTER DATABASE notelib_db_prod SET max_parallel_workers_per_gather = 0;`. The statement, expected
effect and verification are handed over in `docs/claude-plans/done/2026-10-14-db-leg-b-parallel-workers.sql`.
It only applies to new sessions, so it is fully live after the next deploy cycles the pool.
**Applied by the owner 2026-10-15** (before ~03:04Z). Verified read-only at signoff (05:39Z):
`pg_db_role_setting` holds `max_parallel_workers_per_gather=0` for `notelib_db_prod`, a new connection
reads `0` (source `database`), and all 20 app connections opened at or after 05:14Z, after the `ALTER`.
The owner's IntelliJ console kept reading `1` because its connection predated the `ALTER` and
"detach session" reuses it.

**Kill criterion, stated before any leg lands:** count `*terminated by signal 9*` lines on
`dpg-d6tvb8fkijhs73fda4m0-a` over 7 days after the release deploys and Leg B is applied. **Baseline: 9.**

Anti-drift:
- No Hikari `maximum-pool-size` or `minimum-idle` change. The pool size is pinned by the 2026-09-04
  outage fix and `TomcatThreadPoolHikariAlignmentTest`.
- No change to the FEATURED/POPULAR scoring weights, decay, thresholds or section limits. No change
  to the paginated Public Library listing's results.
- No new cache and no new dependency (the Caffeine cache is next release).
- No checkout, payment or pricing path, so RC-4's baseline window is untouched. No public-note-page
  or adoption-path change, so RC-5's read is untouched.
- No migration.
- No `statement_timeout`.

### Shipped

1. **Discovery sections compute the engagement aggregates once (Leg D), and stop re-fetching on
   overview re-entry** — #1498 (`4871be7e`), Tier H.
   - `PublicLibraryRepositoryImpl.findPublicLibraryFeaturedAndPopularIds` (`:314`) ranks Featured and
     Popular in one statement. Each of the copies/likes/views aggregates appears once. A narrow
     `featured_ids` CTE keeps `n.content`/`sp.summary`/`sp.quiz` in scan filters only. Each section
     filters to eligible notes, then `ORDER BY … LIMIT`. Called from `NoteService.java:955`; RECENT
     keeps its own query and still excludes both.
   - One scoring definition, shared with the paginated listing via `rankedOrderKeys`/`rankScoreSql`.
     14 golden files pin the paginated page/count SQL. The pin was verified against the pre-refactor
     code.
   - Prod `EXPLAIN` (read-only, planner estimate): old pair ~25,300 total cost, widest node 48 bytes;
     final ~18,600, widest node 64 bytes. Two earlier Codex passes were rejected for wide sorts and
     joins (see the re-tier note in Planned Scope).
   - Frontend: `discoverySectionsLoadedRef` (`public-library-page-client.tsx:552`) reuses a successful
     load across overview re-entry. Retry always fetches, and an error resets the flag.
2. **Copy counts stay current on the reused overview** — #1500 (`fc7da866`), a follow-up to item 1
   that the cold falsification pass found. Reusing the sections froze `copyCount` at first load.
   `handleCopySuccess` (`public-library-page-client.tsx:774`) now adds one on the source card in the
   browse list and all three sections. It skips a re-copy (the backend returns the existing copy) and
   an owner's own copy, which is never counted. Copies recorded during the mount are merged over the
   copied-notes load (`:510`), so an in-flight load cannot erase them. Each of the four guards is
   mutation-checked against a named test.
3. **Challenge bank duplicates are skipped at insert instead of returning HTTP 500** — #1499
   (`89f4edad`).
   - Both bank writers route through `ChallengeQuizQuestionBankRepository.insertIfAbsent`
     (`:31`, `on conflict do nothing` at `:29`), called from `ChallengeQuizQuestionBankService.java:133`
     and `OfficialChallengeQuizTemplateService.java:257`.
   - A skipped question is still served but not banked or claimed for that session
     (`challenge-quiz.md` "Per-user question bank").
   - Real-PostgreSQL guards cover same, NULL and different level; a two-connection race; `+5` with a
     pre-existing duplicate; rows persisted on every path; and a jsonb round-trip.

**Pre-signoff pressure test: one scoped cold falsification agent (Opus), Tier H trigger.** Leg D's
delivery introduced defects that the same session fixed. The agent tested 6 Leg D claims, 7 Item 2
claims and 2 release-wide sweeps, using mutation testing in a throwaway worktree. **No code defect was
found in either PR.** Confirmed findings and their disposition:
- Frozen copy counts on the reused overview: **fixed in #1500**.
- Stale Backlog rows (`v0.81.0` checkpoint, "Isolate Challenge bank writes properly"): **corrected in
  this signoff**.
- Two imprecise sentences in `challenge-quiz.md` (the seed path's transaction; the shortfall's dedup
  scope): **corrected in this signoff**.
- An unpinned flush flag and narrow golden coverage: recorded below.

**Checkpoint:** `[CHECKPOINT — due 2026-10-22]` in `ROADMAP.md`'s Backlog Index carries the signal-9
kill criterion (baseline 9 in 7 days).

**Owner note, outside this release's scope:** the Official Challenge template seed for note
`0c6b0be8-8b7d-41dc-9619-94cab1d52064` (pack `3deae879-…`) failed at 2026-10-13T03:32:35Z. A read-only
log read at signoff traced it to an LLM output-validation failure: `AppException: Challenge quiz
generation returned an invalid quiz format` from `validateGeneratedQuizItem`, after its one retry. It
was **not** a duplicate-key violation, so it is not evidence against item 2 being latent. The pack has
**0** bank rows and the note is `PUBLIC`, so the admin backfill seed will pick it up if re-run.

### Known Limitations

- **Shared-then-public copy count:** a learner who copied a note while it was privately shared, and
  then copies it again after it goes public, reuses the old copy. That copy isn't counted publicly
  (`copied_from_public = false`), but the card shows +1 until reload. Closing this needs the backend to
  say whether a copy is new.
- **`@Modifying(flushAutomatically = true)` on `insertIfAbsent` is not pinned by any test.** Removing it
  fails nothing, most likely because Hibernate already flushes before native DML. The start-path test
  proves bank rows land, but not that an unflushed session row was ever reproduced.
- **The paginated golden files pin only the H2 dialect with empty criteria.** The PostgreSQL branch of
  `rankScoreSql`, which #1498 edited, is unchanged by reading but not byte-pinned. Discovery's filter
  parameters are oracle-tested on H2 only; production discovery always sends empty criteria.
- **No end-to-end duplicate test on the start or template-copy paths.** They share `insertIfAbsent`
  with the tested `+5` path, so they are covered by construction only.
- **Non-duplicate bank write failures** (foreign-key, check-constraint or 512-character `question_key`
  overflow) still poison the caller's transaction, as before.
- **`NativeQueryParameterTypingTest` scans whole files**, so it flagged the JPQL `:learnerLevel is null`
  in the bank repository once that file held a native query. The three claim queries now use
  `cast(:learnerLevel as string)`, which is behaviour-neutral (verified on PG with a NULL level).
  Narrowing the lint to native-query blocks is a follow-up.

## v0.171.0 - Checkout, Recovered

**Status: Released**

Theme: close the checkout-abandonment leak the product-health release plan's RC-0 diagnosis found
(`docs/claude-plans/2026-10-09-product-health-release-plan.md`), plus signup entry-surface
attribution from the same plan.

### Planned Scope

1. **RC-0 — Checkout diagnosis: CLOSED by owner decision, 2026-10-14, outreach step skipped.** The
   Xendit dashboard pull was done directly by the owner: none of the 16 failed invoices show a
   selected payment channel or an attempt inside the invoice — every one is a plain, un-engaged
   expiry, not a declined payment. The one-question outreach to the 11 genuine users (why didn't you
   finish paying?) was explicitly skipped — a deliberate call, not an oversight, accepting the plan's
   own named risk ("building recovery for a cause RC-0 did not find"). Per-invoice table (Xendit
   invoice IDs, emails, plan/amount) was handed to the owner out-of-band; not committed here per the
   plan's own acceptance criteria ("no emails in any committed file").

2. **RC-3 — Checkout reminder, via Xendit's native invoice notifications (not an in-house job). SHIPPED
   (PR #1495, commit `ab8e006e` on `releases/v0.171.0`).**
   `advisor()` caught, before any Codex prompt was written, that Xendit's Invoice API supports a
   native payment-reminder email gated by an account-level Settings → Checkout → Notifications
   toggle — **the owner has already enabled "Payment reminders" in the Xendit dashboard, 2026-10-14.**
   Our own `createInvoice` call (`PaymentService.java:330-347`) sends no `payer_email` today, so
   Xendit has never had an address to notify. The code fix is one field: add `payer_email` (the
   checkout user's own email, already available via `userRepository.findById(userId)` in
   `createCheckoutSession`) to the invoice-creation payload. No migration, no job, no template, no
   `email_log` row — this supersedes the in-house reminder-job design originally sketched for RC-3 in
   the product-health plan (kept there as historical record, not an instruction to build it).
   **Restarts RC-4's baseline clock**: any checkout-path change lands inside the post-RC-1 baseline
   window (started 2026-10-09), so the price test's start moves past ~2026-11-06 regardless.

3. **RC-2 — Minimal funnel attribution (signup entry surface). SHIPPED (PR #1496, commit `4e3f727e`
   on `releases/v0.171.0`).** Implemented by Codex from a Long-mode prompt, independently re-verified
   (full backend suite including `NativeQueryPostgresIntegrationTest`: 2675/0; full frontend suite:
   2662/0) before merging — Codex's own sandbox could not reach Docker for the Postgres-backed test.
   Extends the existing
   first-touch-attribution mechanism (`V93__user_first_touch_attribution.sql`'s
   `utm_source`/`utm_medium`/`utm_campaign`/`utm_content`/`utm_term`/`referrer` columns, captured
   client-side via `sessionStorage` and submitted once at signup) with one more column,
   `first_touch_surface` (`V156`), classifying the first-touch pathname into `landing` / `public_note`
   / `explore` / `exam_hub` / `other`. Resolves both halves of the plan's §8 item 9 from this existing
   precedent rather than as fresh decisions: a `users` column (matching all 6 siblings, not the
   `SIGNUP_COMPLETED`-metadata alternative, which covers only 311/420 historic accounts), and no new
   anonymous-session-state risk (the capture mechanism already runs on every page today, including
   public ones, per `app/layout.tsx:78`). Narrowed from the plan's "optionally the raw path truncated"
   — class only. Touches both stacks (new migration + `UserEntity`/`SignupRequest`/`GoogleAuthRequest`/
   `AuthService` + `first-touch-attribution.ts`/`lib/api.ts`).

4. **RC-6 — deferred, not scoped into this release.** Raising the discovery-intent/copy-intent
   cookie TTLs touches the same adoption-path instrumentation RC-5 (shipped `v0.170.0`, 2026-10-13) is
   currently measuring for its own H3 pre/post read. The product-health plan says RC-6 "should not
   land inside the RC-5 window," but RC-5 was never given a concrete window end-date at `v0.170.0`
   signoff (a gap in itself — flagged below). Revisit once that window is explicitly dated.

### Anti-drift

No change to `PricingService`, voucher resolution, webhook handling, or any payment amount. No new
endpoint. `payer_email` is read-only existing data, not collected anew.

### Known gaps carried into this release

- **RC-3's reminder is UNVERIFIED to actually fire inside the 24h invoice window — `[CHECKPOINT —
  due 2026-10-21]` owed, since the outreach step that would have given independent signal on checkout
  behavior was itself skipped (RC-0).** Xendit's per-invoice `reminder_time`/`reminder_time_unit`
  fields were not set (only `payer_email` was added), so timing falls back to the Xendit account's own
  Dashboard Invoice Settings default — unconfirmed, and some Xendit SDK examples pair a 1-day-before
  reminder with a 48h invoice, not NoteLib's 24h. **Proximal check (owed, owner action): start a real
  test checkout, leave it unpaid, confirm the reminder email arrives within 24h.** If it does not fire,
  RC-3 has shipped a dashboard toggle with no effect, not a verified fix. Recorded in
  `docs/features/billing.md`.
- **RC-5 (`v0.170.0`) never got its own `[CHECKPOINT]` row for its H3 adoption read at signoff** —
  only the Hikari leak-trace checkpoint was added. This is why RC-6's deferral above has no concrete
  re-open date; owed as a follow-up.
- **The `v0.166.0` Hikari checkpoint (`due 2026-10-14T11:19:29Z`) was read at this kickoff and found a
  THIRD long-hold mechanism `v0.166.0` did not catch** — see `ROADMAP.md`'s Backlog Index for the full
  read and the new row it produced. Not in this release's scope; flagged to the owner as a candidate.

## v0.170.0 - Study Journey, Closed

**Status: Released**

Theme: close out the Study Journey Purpose / Learner Goal architecture — the last discovery item
(Public Note → Official Review Set) plus multi-goal exam support, both shipping on explicit owner
overrides of their own evidence gates rather than waiting for either to clear naturally. **Renamed
same-day from "Public Note → Official Review Set Discovery"** when the owner expanded scope to include
item 2 below; nothing had deployed yet.

### Planned Scope

1. **Public Note → Official Review Set link (RC-5, backend + frontend). SHIPPED — see below.** On the
   public note detail page (`/public/library/[subject]/[slug]`), when the note sits in a qualifying
   Official Review Set — top-level, a true source (not an adopted copy), owned by a `UserRole.ADMIN`
   account — show a callout linking to `/collections/published?highlight={id}`, which scrolls to and
   expands that set's existing `PublicStudyPlanCard`. Reuses the existing signed-out intent-cookie
   handoff (`discovery-intent.ts`), widened to accept this new return path. 3 new analytics events
   (`PUBLIC_NOTE_OFFICIAL_SET_LINK_CLICKED/_PREVIEWED/_ADOPT_CLICKED`). No new migration — the lookup
   uses the existing `idx_note_collection_items_note_id` index and runs inside
   `NoteService.mapToPublicDetail`'s existing read-only transaction.

   **⚠️ Kicked off ahead of the `v0.166.0` Hikari checkpoint clearing (owner override, 2026-10-13).**
   This item's own gate — stated at the `v0.169.0` kickoff — was two stability checkpoints:
   `v0.165.0`'s (due 2026-10-12, closed clean via PR #1489) and `v0.166.0`'s Hikari long-hold checkpoint
   (`[CHECKPOINT — due 2026-10-14T11:19:29Z, backstop 2026-10-21]`, `ROADMAP.md`'s Backlog Index), which
   was **not yet due** at kickoff — roughly a day out. Codex's implementation (audited and committed as
   `dadac05a` on `feat/v0.170.0-public-note-review-set-link` before kickoff) adds exactly one new
   public-note-page database lookup, which is the class of load the Hikari checkpoint exists to watch
   for. **⚠️ Not actually blocking by the time this release reaches `main`**: nothing in this change
   deploys until `releases/v0.170.0` merges, and the checkpoint's own due date (2026-10-14T11:19:29Z) is
   earlier than this release — a 2-item release — is likely to sign off. **Read the checkpoint at
   signoff, before the `main` merge, not just at this kickoff** — the override may turn out to have cost
   nothing in practice. This release owes its own `[CHECKPOINT]` row at signoff either way, reading the
   post-merge `Apparent connection leak` trace rate for any new attribution to this lookup, distinct from
   `v0.166.0`'s own two traced methods (`PublicProfileController`/`NoteCollectionController`).

2. **Multi-goal exam support (backend + frontend). SHIPPED — PR #1491, commit `04435dde` on
   `releases/v0.170.0`.** The owner's 5
   direction points, recorded verbatim at the `v0.169.0` kickoff (2026-10-10):

   1. **Storage — a normalized join table**, one row per (user, exam goal), with one goal flagged
      active per user. No redundant active-state storage. Existing single `studyGoal` values migrate
      losslessly; current users keep their existing goal, now active.
   2. **Dashboard & nudges — the active goal only** drives general dashboard nudges; other goals stay
      visible/accessible but don't compete for nudge real estate. **Exception:** post-session next
      steps must reflect the actual session/Study Journey context even when it differs from the active
      goal.
   3. **EXAM/SUBJECT exclusivity — unchanged.** Multiple exam goals may coexist with each other; a
      subject focus still can't coexist with any exam goal. Profile-preference rule only, never a
      Note/Study Plan access restriction.
   4. **Exam-hub signup intent — add and activate**, never silently replace other exam goals. Clear
      disclosure before the intent flow overwrites an existing subject focus. Signing up via an Exam
      Hub must NOT auto-adopt any Review Set.
   5. **Picker — a real goal-management surface** in Profile's Study Focus (add/remove/set-active).
      Removing a goal must never delete adopted content or assessment history.

   **Validated feasible against current code before any Codex prompt**, per the owner's own
   instruction recorded above: `docs/claude-plans/2026-10-12-multi-goal-exam-support-feasibility-validation.md`
   (Feature Planner, 2026-10-12). Technical verdict: every point is buildable, no blocking
   contradiction found. **4 follow-up decisions made 2026-10-13, at this scope expansion, resolving the
   validation document's §8 open questions:**
   - Adding an additional exam goal while one is active: the new goal becomes the **sole** active goal
     (deactivates the previous one) — reads point 1's "one goal flagged active" literally.
   - Removing the currently-active goal when other adopted goals exist: **auto-promotes** another
     adopted goal to active (mirrors `NoteCollectionService.reassertPrimaryInvariant`'s existing
     pattern for Study Plan adoption — same shape, not the same code, since that operates on
     `NoteCollectionEntity` not exam slugs). **Tie-breaker when more than one goal is eligible for
     promotion: the most recently added remaining goal, then lowest `id`.**
   - Disclosure before a silent goal-clearing write is required in **both** directions — not just
     "exam-intent flow overwrites a subject focus" (point 4's literal wording) but also "setting a
     subject focus clears every adopted exam goal" (the reverse, previously unaddressed). Reuses the
     existing `AppModal`/`ProfileTypeSwitchModal` confirm-modal pattern; no new UI primitive.
   - **Bundled into this same work, not deferred:** `GoalPromptBanner`'s confirmed-live defect (gates
     only on `studyGoal`, never checks `focusSubjects`, so a user with subject focus sees "no goal yet"
     and one click silently wipes their `focusSubjects` with zero disclosure — verified against
     production, 1 of 426 real users is in exactly this state today) and its separate dismissal-state
     bug (component-local, re-shows on reload). PR #1488 (which had backlogged this defect for a future
     release) is closed unmerged as superseded — fixing it now, not later.

   **Applied defaults, per the validation document's own recommendation (low-stakes, argued well
   there, not re-litigated here):** the 3 legacy free-text `studyGoal` rows are left untouched (adding
   any active exam goal still clears the legacy value, closing the exclusivity gap this would
   otherwise open); "SUBJECT focus" in point 4 means `focusSubjects`, not legacy free-text; a single
   shared "resolve active exam goal(s)" method is introduced, consolidating 4 call sites
   (`AuthService`, `ProgressReportService` ×3, `PostSessionNextStepService`) that currently each
   independently re-derive "is this an EXAM goal" — closing a drift risk `ExamGoalConfig.java:38-43`'s
   own comment already documents one prior incident of.

   **⚠️ Kicked off ahead of the `[CHECKPOINT — due 2026-12-07]` population gate clearing — explicit
   owner override, 2026-10-13.** That checkpoint's own stated trigger ("re-open only if the
   ≥2-distinct-goal population grows past a handful") has **not** been met: still 2 of 426 users,
   unchanged since 2026-10-09, re-confirmed 2026-10-12. This is a deliberate reversal of three prior
   owner decisions (2026-10-08, -09, -10) that each declined to build this on the same number. **This
   release owes its own `[CHECKPOINT]`** at signoff, tracking whether the population actually grows now
   that the capability exists — the original checkpoint measured whether to build it; this one measures
   whether building it mattered.

   **Verification note:** this item adds a data-mutating migration, changes API/export response
   shapes, and touches profile, dashboard, post-session, exam-hub, and GDPR-export code — clears the
   bar for at least one scoped cold falsification agent at signoff per the pressure-test gate, and is a
   candidate for the full 3-agent pressure test given the permission-adjacent (goal/content exclusivity)
   surface. **Decided at signoff:** one scoped cold agent (fresh context, `opus`, read-only, instructed
   to falsify 7 named claims), not the full 3-agent test — `git diff --name-only` on both PRs found no
   shared Java method between PR #1490 and PR #1491 (3 shared files, disjoint lines each), and the
   goal/content exclusivity surface is a profile-preference rule, never an access-control boundary (no
   cross-user read, no money/quota change), so the full-test trigger does not fire. Findings below.

   **Audited before commit** against the Codex prompt's full acceptance-criteria list: migration
   backfill replay test against seeded (non-empty) data; a real-Postgres `@SpringBootTest` +
   `@Testcontainers` test proving the partial unique index exists and the actual Hibernate flush order
   holds under add/remove/idempotent-re-add (not H2, which has no such index); `AccountPurgeService`
   deletes `user_exam_goals` rows rather than sentinel-reassigning (the table enforces per-user
   uniqueness); GDPR export (schema 1.3) includes every adopted goal, not just the active one; the
   pre-existing `PUT /users/profile/goal` endpoint kept its request/response contract unchanged for
   un-upgraded clients; `EXAM_GOAL_ADDED/ACTIVATED/REMOVED` confirmed firing from real user actions (not
   just enum membership), verified by a test asserting the call. One false-positive raised and reverted
   during audit: "CELE" in `progress-focus-guide.tsx` is the correct, pre-existing Civil Engineering
   exam acronym (`ExamGoalConfig.java`), not a typo. Backend: 2,672 tests/0 failures (incl. the
   Testcontainers native-query and real-Postgres flush-order harnesses). Frontend: 231/231 suites,
   2,642/2,643 tests (1 pre-existing unrelated skip), `tsc --noEmit` clean, `npm run lint` clean.

Anti-drift: no `ProfileType` change, no recommendation-engine change, no Degree Journey/Academic
Term/ConceptHealth change, no change to `resolvePlanPremiumExamMode`/`EXAM_MODES.md`, no change to
`UserEntity.examDate`'s existing single-valuedness (a known, separate, out-of-scope gap).

**Explicitly excluded, unaffected by either override above:** the Degree Journey catalog (Phase C) —
its own dependency (≥2 published Years) is a content-authoring gap no decision overrides; exactly one
BSCS Year exists, unpublished — and Phase D (learner curriculum customization), not yet scoped, deferred
pending real adopters. Neither is "Study Journey" work this release can close by deciding to.

### Shipped

- **Public Note → Official Review Set link** (PR #1490, commit `dadac05a`). Backend: new
  `NoteCollectionItemRepository.findOfficialReviewSetRootForNote` native query (admin-owned,
  top-level, true-source root; a PUBLIC root collection is never filtered on `published_at`, a child
  collection and its item both require one — matching `listPublic`/`getPublic` exactly), wired into
  `NoteService.mapToPublicDetail`; `PublicNoteDetailResponse` gains `officialReviewSetId`/
  `officialReviewSetTitle`; 3 new `AnalyticsEventType` values. Frontend: `PublicNoteOfficialSetLink`
  callout on the public note detail page linking to `/collections/published?highlight={id}`;
  `PublicStudyPlanCard` widened with a `discoverySource: "public_note"` branch and a
  `defaultPreviewOpen` prop; `PublishedPlansPageClient`'s "Browse All" grid reads `?highlight=` to
  scroll to and auto-expand the matching card; `discovery-intent.ts`'s signed-out return-path
  allowlist widened (`isDiscoveryReturnPath`, renamed from `isExplorePath`) to accept
  `/collections/published` paths, with an adversarial test (`/collections/published-evil`).
  Audited before commit: fixed a misplaced code comment and generic "Collection" copy corrected to
  "Review Set" (`getCollectionLabels(null)` default was wrong here — this is a fixed product concept,
  not a viewer-varying one). **Known limitation, non-blocking:** the "Recommended" grid on
  `/collections/published` (course/program-filtered, authenticated-only) doesn't receive the
  highlight/`defaultPreviewOpen` wiring that the "Browse All" grid does — acceptance criteria are
  still met since Browse All is always the unfiltered superset and always renders the target plan.
  Backend: 2,659 tests/0 failures (incl. the Testcontainers native-query Postgres harness, 46 queries
  now PREPAREd). Frontend: 230/230 suites, 2,634/2,635 tests (1 pre-existing unrelated skip).

- **Multi-goal exam support.** V155 moves valid scalar exam slugs
  into `user_exam_goals`, preserving legacy free-text values; Profile can add, remove, and activate
  goals, and Dashboard uses the active goal while post-session nudges follow the completed note's
  adopted-goal context. Subject focus and exam goals remain exclusive with confirmation before
  either direction clears existing preferences. The goal prompt now respects subject focus and
  persists generic dismissal. Account data export schema 1.3 includes every goal. **Frontend and backend must deploy
  together.** After V155 runs, rolling the backend back to v0.169.0 is **not safe for the 16 migrated
  users**: their scalar `studyGoal` is empty and that backend cannot read the retained rows in
  `user_exam_goals`. At signoff, add a post-ship population checkpoint to measure whether the
  ≥2-distinct-goal cohort grows beyond the 2-of-426 baseline; this release shipped ahead of that
  evidence gate by owner decision. Audited before commit against the Codex prompt's acceptance
  criteria: migration backfill replay test, real-Postgres flush-order test (partial unique index
  verified present), purge deletes rows (not sentinel-reassigned), GDPR export includes every
  adopted goal, `PUT /users/profile/goal` kept contract-compatible for un-upgraded clients, and new
  `EXAM_GOAL_*` analytics events confirmed firing (not just enum members) via frontend tests.
  Backend: 2,672 tests/0 failures (incl. the Testcontainers native-query Postgres harness).
  Frontend: 231/231 suites, 2,642/2,643 tests (1 pre-existing unrelated skip), `tsc --noEmit` clean,
  lint clean. **Known limitations, found by the pre-signoff falsification agent, none blocking:**
  (1) `PUT /users/profile/goal`'s `studyGoal: null` branch now also empties `focusSubjects`
  (`AuthService.java:605-621`) — a deliberate "full reset" per the Codex prompt, but a genuine
  behavior change from `v0.169.0` (null used to leave `focusSubjects` untouched); an un-upgraded
  client sending `null` during the brief deploy-rollover window sees this new behavior before its own
  UI catches up. (2) V155 does not special-case a legacy row holding both a valid exam-slug
  `studyGoal` and non-empty `focusSubjects` — confirmed via a read-only production query at signoff
  (2026-10-13) that 0 of 426 users are in that state today, so the gap is theoretical, not live; if it
  ever becomes non-zero, such a migrated row would hold an active goal and focus subjects
  simultaneously, violating the new exclusivity invariant. (3) The migration's SQL `trim()` is
  whitespace-only, narrower than `ExamGoalConfig`'s Java-side slug normalization — a value like
  `"ale\n"` would not migrate and falls back harmlessly to legacy scalar handling via the resolver.
  (4) The Official Review Set predicate (Item 1) does not exclude a `PENDING_DELETION` admin
  account's collection until the account is actually purged. (5) During the deploy rollover, an old
  backend instance can still write a stale scalar `studyGoal` for a user who already has an active
  `user_exam_goals` row; the resolver prefers the row, so the stale write is silently ignored rather
  than losing data, and the window closes once rollover completes.

- **Two carried-along process/doc fixes (PR #1487, PR #1489).** Both had been open against `main`
  since before this release and were blocked there by the same `require_extra_approval_for_unattributed_changes`
  ruleset rule as this release's own signoff PR; retargeted to `releases/v0.170.0` and merged here so
  they ride to `main` with the rest of this release rather than sitting blocked indefinitely.
  **PR #1489** closes the `v0.165.0` discovery-rail checkpoint on its due date: zero `server_failed`
  restarts in the full 7-day post-deploy window (down from a ~7/week baseline) — the fix worked. This
  is the checkpoint Item 1's own kickoff text above cites as "closed clean via PR #1489"; that claim
  is accurate as of this merge, not before it. **PR #1487** documents a `check-deploys.sh`
  false-positive mode found during `v0.169.0`'s deploy: a real, successful Vercel deployment that
  never appeared in GitHub's Deployments API, so a future Vercel "DRIFT" verdict from that script
  should be cross-checked against the actual Vercel dashboard before anyone acts on it. Comment-only
  change to the script; no logic changed.

## v0.169.0 - Study Journey, Continued

**Status: Released** (signed off 2026-10-12; commit `3200c773` on `releases/v0.169.0`)

Theme: continue the Study Journey Purpose / Learner Goal architecture by shipping the one piece
that's actually ready — after an `advisor()` pass caught that the original 3-item plan had
sequencing and decision gaps, and the owner then explicitly chose to close this release on that
one piece rather than fold in the others.

### Planned Scope

- **Civil Engineering Exam Hub page.** Adds a discovery page for CE alongside the four that already
  exist (ALE, PNLE, LET, CPALE) in `frontend/lib/exam-hub-config.ts`. **Owner override of Product
  UX's earlier "defer pending adoption evidence" call** — no new evidence arrived; this is a
  deliberate reversal, recorded as such. Codex prompt: `docs/codex-prompts/v0.169.0-ce-exam-hub.md`.
  Found during scoping, not assumed: 4 files (not just the config) hardcode the 4 existing slugs by
  name, including one (`exam-hubs-guide.tsx`) already independently stale (missing CPALE) before
  this task touched it — fixed as one move, deriving all of them from the shared config instead of
  a 3rd/4th/5th duplicate list. Production size checked directly: CE's 113 public notes sit between
  the smallest (ALE, 90) and largest (CPALE, 154) already-live hubs — no new bound needed.

### Multi-goal exam support — owner direction recorded 2026-10-10, explicitly NOT authorized to implement

The 5 batched questions got real answers, but **the owner was explicit these are product-direction
preferences, not technical authorization** — before any Codex prompt, Feature Planner is to validate
the storage migration, state transitions, onboarding semantics, cost, and acceptance criteria against
the current code. No multi-goal implementation prompt exists, and none should until that validation
and a separate go-ahead happen. Recorded here so the direction isn't re-litigated or lost before that
validation happens:

1. **Storage — a normalized join table**, one row per (user, exam goal), with one goal flagged
   active per user. No redundant active-state storage (e.g. not a separate "active goal" column
   that can drift from the join rows). Existing single `studyGoal` values migrate losslessly;
   current users keep their existing goal, now active.
2. **Dashboard & nudges — the active goal only** drives general dashboard nudges; other goals stay
   visible/accessible but don't compete for nudge real estate. **Exception:** post-session next
   steps must reflect the actual session/Study Journey context even when it differs from the active
   goal — a session on a non-active goal's material must not get nudged toward the active goal
   instead.
3. **EXAM/SUBJECT exclusivity — unchanged.** Multiple exam goals may coexist with each other; a
   subject focus still can't coexist with any exam goal. This is a profile-preference rule only —
   it has never restricted which Notes/Study Plans are reachable, and broadening subject/exam
   coexistence needs its own separate decision, not an inference from this one.
4. **Exam-hub signup intent — add and activate**, never silently replace other exam goals. For a
   learner who currently has subject focus (not an exam goal), the existing exclusivity contract
   still requires clear disclosure before the intent flow overwrites it. Signing up via an Exam Hub
   must NOT auto-adopt any Review Set — goal-setting and content-adoption stay separate actions.
5. **Picker — a real goal-management surface** in Profile's Study Focus (add/remove/set-active),
   not a single-select-to-multi-select conversion with no active-goal concept. Removing a goal must
   never delete adopted content or assessment history — it only changes which goal drives nudges.

**Release boundary:** multi-goal stays out of `v0.169.0` entirely. It remains additionally gated on
the `[CHECKPOINT — due 2026-12-07]` population read (still 2 users as of 2026-10-09) and
prioritization against the product-health release plan — neither is resolved by this direction
record.

### Deferred out of this release (sequencing, not scope changes)

- **Public Note → Study Journey discovery — moved to `v0.170.0`.** Two problems caught by
  `advisor()`: (1) it already has a design — Feature Planner's RC-5 in
  `docs/claude-plans/2026-10-09-product-health-release-plan.md` §3 ("Part of Official Review Set
  X") — so this release does not need to design it from scratch, only reuse that spec; (2) RC-5's
  own stated gate is the two active stability checkpoints (`v0.165.0`'s discovery-rail fix, due
  2026-10-12, and `v0.166.0`'s Hikari long-hold fix, due 2026-10-14) — a new per-request lookup on
  public Note pages, shipped before those read out, makes both checkpoints' kill criteria
  uninterpretable (the same contamination pattern already recorded for Learning Connections).
  Sequencing this after 10-14 roughly lines up with the Claude budget reset on 10-12 anyway.

Anti-drift carried over from the original discovery brief, still binding for everything in this
release: no `ProfileType` change, no recommendation-engine change beyond the explicitly-decided
Public Note link, no Degree Journey / Academic Term / ConceptHealth change, no change to
`resolvePlanPremiumExamMode`/`EXAM_MODES.md`.

**Explicitly excluded — the one piece NOT in this release:** the Degree Journey catalog (Phase C).
Re-checked 2026-10-09: its own stated dependency ("≥2 published Years") is unmet — exactly one BSCS
Year exists and it's unpublished. This is a content-authoring gap, not a code gap, and no decision
override removes it. A separate, unmerged curriculum-authoring branch
(`docs/bscs-year1-curriculum-shaping`) exists with planning material for completing that one Year,
but it predates `v0.167.0`/`v0.168.0` (merging it as-is would roll both back) and even once rebased
and authored, completes only the existing Year — not a second one. Tracked separately; not part of
this release.

**Delivery:** all three items are new backend/multi-system work — Codex-routed per `CLAUDE.md`'s
task-routing table, one prompt per item. **Budget note:** the owner's Claude usage resets 2026-10-12;
implementation is deliberately routed through Codex rather than done inline to conserve it, with
Claude Code's role limited to scoping, prompt-writing, and post-delivery audit.

**Verification tier:** three separate Codex deliveries folding into one release raises verification
cost per `CLAUDE.md`'s own size-vs-cost rule. None of the three trigger the full three-agent tier
(no money/quota/permission-substrate change, no first-of-kind cross-user read). Default to one
`advisor()` pass per item after its audit, escalating to one scoped cold agent only if a trigger
fires during implementation (e.g. two items touching the same shared method, or a defect found and
fixed in the same session).

### Shipped

- **Civil Engineering Exam Hub (CELE) — PR #1485, commit `3200c773`.** Added `/exam/ce` and all 5
  consuming surfaces (index page, Help Center guide and card, server-side course-program resolution).
  Fixed `EXAM_HUB_ICONS` duplication along the way — moved into `exam-hub-config.ts` as the single
  source of truth, which also fixed `exam-hubs-guide.tsx`'s pre-existing CPALE omission. Implemented
  via Codex, independently re-verified (tests, `tsc --noEmit`, `npm run build` all re-run fresh, not
  trusted from the delivery report alone).

## Archived releases

- `v0.163.0 — No Peeking` (Released) — archived to `docs/archive/RELEASES_ARCHIVE.md`.
- `v0.162.0 — Say the Value` (Released) — archived to `docs/archive/RELEASES_ARCHIVE.md`.
- `v0.160.0 — Study Plans by Semester` (Released) — Degree Study Journeys Phase A0/A: academic-term placement on Subject Plans, Year-page term grouping, compact Subject cards, and the curriculum-pipeline `academic_term` column; moved at the `v0.166.0` kickoff.
- `v0.161.0 — Scannable Study Plans` (Released) — Degree Study Journeys Phase B: Study Plan Sections collapsed by default, an `Expand all`/`Collapse all` toggle, `Not started` wording at Section and plan grain; plus a pre-signoff pressure-test fix making `adoptGoal` adopt every published-but-private child instead of requiring `PUBLIC`. Moved at the `v0.167.0` kickoff.
- `v0.164.0 — Computing` (Released) — ratified `Computing` as a 13th Domain Context (ADR-001 amendment), shipped the enum/frontend value, swept the context/spec docs; carries a `[CHECKPOINT — due when BSCS Year 1 Computing authoring reaches 10+ classified notes, backstop 2026-12-29]`. Moved at the `v0.170.0` kickoff.
- `v0.165.0 — Bounded Discovery` (Released) — closed the public-note discovery-rail DB-saturation restart mechanism (Leg B) and shipped three R4-pilot generation-quality fixes (G1, G2b, G5); carries its own now-closed `[CHECKPOINT — due 2026-10-12T15:24:27Z]` (fix worked, 0 restarts). Moved at the `v0.171.0` kickoff.
- `v0.166.0 — Measured Twice` (Released) — archived to `docs/archive/RELEASES_ARCHIVE.md`. Moved at the `v0.172.0` kickoff.
- `v0.167.0 — Study Journey Purpose` (Released) — archived to `docs/archive/RELEASES_ARCHIVE.md`. Moved at the `v0.173.0` kickoff.
- `v0.168.0 — Free Quota, Fairly Metered` (Released) — archived to `docs/archive/RELEASES_ARCHIVE.md`. Moved at the `v0.174.0` kickoff.
