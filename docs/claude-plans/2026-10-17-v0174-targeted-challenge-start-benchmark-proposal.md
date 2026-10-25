# v0.174.0 — targeted Challenge Quiz start benchmark: proposal (deliverable 6)

**Status: APPROVED 2026-10-17, option A ($4.50 hard stop, smoke included). The Long Exam pool is lowered
to 30 instead of running L26, and L30 and V-fan are out. No paid call yet; next is the harness PR.** Written 2026-10-17 by Release Implementor for the owner's conditional
approval (`docs/claude-plans/2026-10-17-owner-conditional-approval-challenge-quiz-ux.md` §3).

The owner runs every paid call, as in the first benchmark. The API key never enters a prompt, a repo file
or a Claude session. No production write.

## Why a second run

- **The start sizes were never measured.** The first benchmark ran Challenge at 5 questions, but a real
  start asks for 10, 12 or 15 (`ChallengeQuizService` `resolveGenerationProfile` `:1364`, constants
  `:160-162`), or 18 for multi-note (`:178`).
- **Multi-note starts were not tested at all, and they are the slowest start in the product.** Read
  2026-10-17:
  - they **always generate live**, never using the bank or a template (`:419-424`);
  - they make **one call per source note, in sequence, inside the request**
    (`generateChallengeQuizForSources`, `:1831-1860`);
  - the 18 questions split across up to 3 sources on FREE and up to 6 on Plus/Pro (`:171-178`), so a
    Plus/Pro start can be **6 sequential Sol calls**.
  - Production: 3 learner multi-note sessions, all 2026-10-13 → 10-17, with 2–3 sources each. The feature
    is new and its use is starting.
- **The candidate mitigations are unmeasured.** Feature Planner's comparison
  (`docs/claude-plans/2026-10-17-challenge-quiz-latency-root-cause-and-ux-recommendation.md` §4) proposes
  lower reasoning effort and parallel fan-out, and neither has data.
- **Two Long Exam paths have the least timeout headroom on Sol**: the 48-question pool at 26 per call, and
  the full-set fallback at up to 30 in one call
  (`docs/claude-findings/2026-10-17-v0174-routing-matrix-and-exam-path-validation.md` §2). **No learner has
  ever used Long Exam**, so these cells are optional (see Tier 3).

## Method

- Every cell calls the **real `OpenAiLlmStudyPackService` method production uses, with production's
  question count, difficulty and disallowed-question list.**
  - **Single-note:** the pack's saved quiz texts, then dedup against their keys
    (`ChallengeQuizService:396,463`).
  - **Multi-note:** each source gets its own context. The list starts **empty** and accumulates the
    questions generated for earlier sources (`:1837-1860`). A failed source fails the whole start, as in
    production.
- It reuses the existing harness, metering and spend stop (PR #1506), and the same public-note sample,
  which is never committed.
- **Paired design:** within a cell, every model runs on the **same Study Packs**, so pack-to-pack variation
  does not swamp the model difference at n = 10.
  - M-6×3 needs 36 distinct source packs and the full set about 60. The 81-line sample has 40 Leg A notes
    plus 32 Leg B inputs; the harness confirms enough distinct packs before any paid call, or fails.
- **Recorded per call:**
  - latency;
  - attempts, including the service's own retry;
  - success or failure class, including timeouts at the production 180 s read timeout;
  - token usage (input, cached input, output, reasoning) and cost;
  - **accepted questions**: the count after production's `QuizDeduplicationUtils.uniqueQuestions` and the
    structural checks;
  - **whether accepted ≥ requested.** A short set is what fails a real start.
- **Multi-note runs** also record total start latency, the sum of the sequential calls the learner waits
  for. The slowest single call is recorded as a free lower bound for a *parallelized* multi-note start.
- **Cells run in priority order** (the table order), so a spend stop cuts the least important cells, never
  the headline ones.

## Cells

n = runs per cell.

| Tier | Cell | Workload (production path) | Luna | Sol | mini | Est. cost (list) | Informs |
|---|---|---|---|---|---|---|---|
| **1** | **S15** | Single-note start, 15 q, `hard` | 10 | 10 | 10 | $0.74 | Worst single-note start; mini anchors the harness against production |
| **1** | **M-6×3** | Multi-note, 6 sources × 3 q, sequential (Plus/Pro max) | — | 6 runs (36 calls) | — | $0.66 | Longest wait in the product; whether multi-note needs parallel per-source calls |
| **1** | **S10** | Single-note start, 10 q, `easy` | 10 | 10 | — | $0.49 | Start sizes, low Quick Review score |
| **1** | **S12** | Single-note start, 12 q, `medium` (`DEFAULT_SELECTED_DIFFICULTY`, `ChallengeQuizService:190`) | 10 | 10 | — | $0.56 | Start sizes, middle score or none |
| **1** | **M-3×6** | Multi-note, 3 sources × 6 q, sequential (FREE max; Plus/Pro with 3 notes) | 6 runs | 4 runs | — | $0.39 | Multi-note on both tiers |
| **2** | **V-low** | S15 with `reasoning.effort: "low"`, injected by the harness interceptor (test scope only) | 10 | 10 | — | $0.69 | Whether a lower-effort Challenge setting is worth a decision |
| **2** | **V-fan** | S15 as 2 parallel calls of 10 (⌈15/2⌉ + 2, as `generateLongExamParallel` does) on a harness-owned 2-thread pool, merged with production's dedup | 10 runs | 10 runs | — | $0.96 | Feature Planner's fan-out option (expected 25–35% cut) |
| **3** | **L30** | `generateLongExam` at 30 q in one call (the full-set fallback) | 5 | 5 | — | $0.47 | Long Exam fallback timeout risk on Sol |
| **3** | **L26** | `generateLongExam` at 26 q (the 48-question pool's per-call size) | 5 | 5 | — | $0.41 | Only needed if the pool size stays 48 |

How the costs are priced, at list price:
- Each method is priced from its own per-call usage in the first run. Sol Challenge is about $0.0067 per
  call plus $0.0039 per question; Sol Long Exam about $0.0065 plus $0.0028 per question. Luna is about
  $0.0003–0.004 per call; mini about $0.005 per 15-question call.
- Billed cost will be lower: cached input is billed below list price.

**Left out on purpose:**
- the 5-question legs, Study Pack generation and Adaptive, all measured already;
- Board Exam session sizes: S10 and S15 bracket its 10–15 per call;
- `LLM_MODEL_CRITIQUE`: out of scope (owner §9).

## Budget

| Option | Cells | Estimate (list) | Smoke | Proposed hard stop | Cumulative v0.174.0 benchmark spend at most |
|---|---|---|---|---|---|
| **A. Recommended** | Tier 1 + V-low | $3.53 | about $0.45 | **$4.50** | $7.12 of the original $10 cap |
| B. Full | Tiers 1–3 | $5.37 | about $0.75 | $6.50 | $9.12 |
| C. Minimum | Tier 1 only | $2.84 | about $0.35 | $3.50 | $6.12 |

- **The smoke counts inside the hard stop, and its results count toward n.** Smoke is run 1 of each cell;
  the full run starts at run 2.
- **The spend stop meters the conservative figure,** which counts reasoning twice. That reads about 10%
  above list on Sol and about 30% on Luna, so A's $3.53 meters at about $3.90 against the $4.50 stop.
- **Recommendation A is the option without V-fan or the Long Exam cells.**
  - V-fan costs most and is expected to cut only 25–35%. Multi-note's per-call data already bounds a
    parallel approach.
  - The L cells test paths no learner has used. **Lower `LONG_EXAM_POOL_SIZE` instead** (an owner choice;
    the routing doc lists it) and leave L30 for when Long Exam has users.
- **Every command gets an explicit `-Dllm.benchmark.spendStopUsd`**, because the $8 per-command default
  would otherwise be the bound. A run sheet with one stop per command, summing to the approved cap, comes
  with the harness PR.
  - After the smoke, Release Implementor recomputes the full-run stops from the real per-cell costs.
- **The stops are metered at list price,** so they overstate spend and the real bill is lower.
- **Billed-cost reconciliation, as in the first run:**
  - per model on the OpenAI dashboard;
  - Luna and Sol run only in the benchmark, so they reconcile exactly;
  - mini shares the project with production, so its 10 calls cannot be separated.
  - A separate project key would make it exact. That is the owner's call, and it is optional.

## How results will be read (stated before the run)

1. **Translating harness latency to a production wait.** mini's S15 harness figure is compared with
   production's 15-question start (p50 35.7 s / p90 72.6 s). Both translations are reported, and the
   larger is used for decisions:
   - **additive**: production overhead in seconds, added to the Luna and Sol harness figures;
   - **proportional**: production ÷ harness, multiplied in.
2. **Against the owner's acceptable start wait W**, which is still owed (see below). For each tier and
   size, with async start in place as Feature Planner's required item:
   - If the projected production p90 ≤ W, async start plus template backfill is enough, and no third routing
     destination is needed.
   - If the default exceeds W but V-low is within W, a lower-effort Challenge setting goes to the owner as a
     decision. **It would also need its own small blind quality review before adoption.**
   - If both exceed W, the remaining options are a smaller start that fills in the background (option A,
     although Leg B suggests it gains little), parallel per-source calls for multi-note, or Luna for
     Challenge start on every tier. Each is an owner decision.
   - **If any Sol cell times out at 180 s, or returns fewer accepted questions than requested, it is
     reported as a reliability failure regardless of W.**
3. **Tail limits.** With n = 10, the p90 is the 9th of 10 values: a rough tail, not a reliable one. **This
   sample cannot establish tail latency**, as the owner's §3 asks to be stated. Rare timeouts (≤ 5%) may
   not appear at all; the first run's mini timeouts were 3 in 81.

## Not measured

- Production server load. It is estimated through the mini anchor and nothing more.
- The UX fix itself: async start, recovery and degradation are code, verified by item 2's tests.
- Question quality: the first run's blind tier stands, and V-low would need its own blind review.

## Implementation (after approval only)

- **Harness change, test scope only.** A new `E` leg in
  `backend/src/test/java/com/studysnap/backend/benchmark/`:
  - configurable count and difficulty;
  - a sequential multi-source runner;
  - an opt-in `reasoning.effort` override in the interceptor;
  - a harness-owned fan-out pool (only if B is chosen);
  - the accepted-question counts;
  - priority ordering and a pre-flight check of distinct packs.
- No file under `backend/src/main` changes. About 150 LOC plus tests, so it goes to Codex under the task
  routing rules, on its own branch and PR into `releases/v0.174.0`.
- Then the owner runs the smoke, Release Implementor checks it and resets the stops, and the owner runs the
  rest.
- Results go into a findings doc in the format of `docs/claude-findings/2026-10-17-v0174-llm-benchmark-results.md`.

## Owner decisions needed

1. **Budget: A ($4.50 stop, recommended), B ($6.50) or C ($3.50)?**
2. **The acceptable Challenge start wait W, for FREE and for Plus/Pro.** The results are read against it.
   Without it they describe the wait but cannot decide anything.
3. **Long Exam pool: lower `LONG_EXAM_POOL_SIZE`** (recommended, no paid test), **or keep 48 and run L26?**
