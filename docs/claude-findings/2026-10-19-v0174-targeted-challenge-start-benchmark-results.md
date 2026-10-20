# v0.174.0 deliverable 6 — targeted Challenge Quiz start benchmark: results

**Status: COMPLETE (measurements).** The decision the results feed is open: they are read against the
owner's acceptable start wait W, which is still owed. Written 2026-10-19 by Release Implementor.

Executes `docs/claude-plans/2026-10-17-v0174-targeted-challenge-start-benchmark-proposal.md` (option A,
owner-approved 2026-10-17) with the Leg E harness (PR #1507, `5dffd5e6`).

The owner ran every paid call on 2026-10-18 and 10-19 (UTC+8). Raw outputs stay under
`backend/target/llm-benchmark/e-*` (git-ignored; they hold note text). No production write.

## Spend

| | Harness, conservative (the meter) | Harness, non-additive | **Billed (OpenAI dashboard, 10/18–10/19)** |
|---|---|---|---|
| `gpt-6.1-sol` (default and low effort) | $3.192 | $2.816 | **$2.586**: cache writes $0.323, cached input $0.018, input $0.001, output $2.244 |
| `gpt-6-luna` (default and low effort) | $0.161 | $0.117 | **$0.110**: cache writes $0.012, cached input $0.001, input $0, output $0.097 |
| `gpt-4.1-mini` (S15 anchor) | $0.054 | $0.054 | not separable (production shares the project) |
| **Total** | **$3.41 of the $4.50 stop** | $2.99 | **about $2.75** |

- **Billed is below both harness figures**, as in the first run: input is billed mostly as cache writes and
  cached input, not at list price.
- Cumulative v0.174.0 benchmark spend: about $2.62 (first run, harness non-additive figure) + about $2.75
  (this run, billed plus mini's harness figure) = **about $5.37 of the original $10 owner cap.**
- The meter stopped nothing: every planned run completed.

## Results

Each start is the real `OpenAiLlmStudyPackService` call at production size, difficulty and avoid-list.
Smoke (run 1) and full (runs 2–10) are pooled. Latency is the learner's wait for the model; multi-note
latency is the sum of its sequential per-source calls.

| Start | Model | n | Succeeded | p50 | p90 | max | Slowest per-source call (mean) |
|---|---|---|---|---|---|---|---|
| 15 q, `hard` | mini | 10 | 9 (1 `SCHEMA_OR_PARSE`) | 13.1 s | 19.0 s | 34.0 s | — |
| 15 q, `hard` | Luna | 10 | 10 | 48.8 s | 59.2 s | 74.3 s | — |
| 15 q, `hard` | **Sol** | 10 | 10 | **106.7 s** | **123.4 s** | **160.5 s** | — |
| 12 q, `medium` | Luna | 10 | 10 | 45.8 s | 58.4 s | 77.6 s | — |
| 12 q, `medium` | Sol | 10 | 10 | 78.4 s | 94.2 s | 106.9 s | — |
| 10 q, `easy` | Luna | 10 | 10 | 25.7 s | 42.0 s | 42.6 s | — |
| 10 q, `easy` | **Sol** | 10 | **9: 1 read timeout at 180 s** | 66.4 s | 168.2 s | **180.0 s** | — |
| Multi-note 3 × 6 | Luna | 6 | 6 | 81.1 s | 103.9 s | 103.9 s | 32.4 s |
| Multi-note 3 × 6 | Sol | 4 | 4 | 165.4 s | 206.2 s | 206.2 s | 65.4 s |
| Multi-note 6 × 3 | **Sol** | 6 | 6 | **216.8 s** | **255.7 s** | **255.7 s** | 44.1 s |
| 15 q, **low effort** | Luna | 10 | 10 | **27.8 s** | 35.3 s | 42.7 s | — |
| 15 q, **low effort** | Sol | 10 | 10 | 89.3 s | 101.1 s | 120.2 s | — |

- **Accepted questions:** every successful run returned at least as many accepted questions as requested,
  after production's dedup. The two "short" runs are the two failures.
- **Sol's timeout** (10-question run 8) made one attempt only: the service does not retry a read timeout.
  In production that is a start that fails after three minutes.
- **Multi-note:** 0 failed sources in 48 Sol and 18 Luna per-source calls, so the compounding risk did not
  show at this sample size. 6 × 3 on Sol took **3–4 minutes in every run**, although no single call took
  more than 54 s.
- **Low effort:**
  - **Luna:** cuts the 15-question p50 from 48.8 s to 27.8 s (−43%).
  - **Sol:** cuts it from 106.7 s to 89.3 s (−16%). Its reasoning tokens were already low (516 → 109 in
    the smoke), so Sol's time is spent generating output, not reasoning.

## Sol's tail clusters in time

- Sol's 10-question runs 7–10 were all slow (149 s, **180 s timeout**, 168 s, 135 s), back to back between
  23:55 and 00:04. The runs before and after took 45–107 s.
- **The same packs ran fast on Luna** (19–26 s, run in a different window). So this was a **provider-side
  slow window for Sol of about 10 minutes**, not the packs.
  - One pack (run 8) is mildly slow on every model, and that is a separate, smaller effect.
- **In production a window like that times out several learners at once,** not one in 40.
- **A smaller start would not avoid it either:** the timeout came on a 10-question start, and the slowest
  15-question run was 160 s.

## Two different measures, kept separate

**A. Timeout exposure: the model call against its 180 s read timeout.** The harness timed that same call
under the same 180 s limit, so harness figures apply directly; production overhead outside the call does
not count against it.
- **Sol:**
  - 15 questions: p90 123 s, max 160 s;
  - 10 questions: p90 168 s, plus one 180 s timeout in 40 single-note starts, inside the slow window above.
- **Luna:** max 78 s on any single-note start. Its largest per-source call was 39 s.
- **Unknown:** production prompts for learners' own notes may be larger than the public sample, which would
  move these figures up.

**B. The learner's wait:** the call plus production's overhead (session creation, bank and template lookups,
database writes). mini's 15-question harness start (p50 13.1 s / p90 19.0 s) against production's
15-question live start (p50 35.7 s / p90 72.6 s, 21 starts, 2026-07-24 → 10-17). **The proposal's rule,
stated before the run, is to report both translations and decide on the larger one. It is applied here
unchanged.**

| Projected production 15-question wait | p50 | p90 |
|---|---|---|
| Overhead (additive / proportional) | +22.6 s / × 2.7 | +53.6 s / × 3.8 |
| Luna | 71 s / **133 s** | 113 s / **226 s** |
| Luna, low effort | 50 s / **76 s** | 89 s / **135 s** |
| Sol | 129 s / **291 s** | 177 s / **471 s** |
| Sol, low effort | 112 s / **243 s** | 155 s / **386 s** |

- **The data cannot say which translation is right.**
  - At 5 questions, production's p50 was 11.7 s against the first benchmark's 6.6 s: +5 s, or × 1.8.
  - At 15 questions it is +23 s, or × 2.7.
  - Neither the gap nor the ratio is constant, so the larger figure (bold) is used, as pre-stated.
- **These waits are read against W, which the owner has not set.** This document makes no
  acceptable-or-not judgment.

## What this establishes for the decision

**Holds for any W:**
1. **A synchronous Challenge start on Sol is not viable.** A timeout was observed, and Sol's slow windows
   cluster. Async start (Feature Planner's required item) is confirmed as required for Sol.
   - **Async start does not fix the timeout.** A single-note start is one call, so there is no partial set
     to fall back on, and the service does not retry a read timeout (the timed-out run made 1 attempt).
   - So an async Sol start that times out is still a failed start, after three minutes.
   - **What happens then is an owner decision this release now needs**: retry once, fail with a clear
     recovery screen, or fall back to Luna. A Luna fallback is a downgrade, which owner §1 forbids doing
     silently.
   - Feature Planner's required list covers this for multi-note starts (catch-with-floor) but not for
     single-note.
2. **Multi-note on Sol needs per-source parallel calls (option H).** On 6 × 3 the slowest source averaged
   44 s against a 217 s sequential p50, so parallel calls would bound the start near the slowest call,
   roughly a 4–5× cut. Its catch-with-floor half is unaffected: no source failed here, but n is small.
3. **Lower reasoning effort is a lever for Luna (−43% p50), not for Sol (−16%).** Sol's time is spent
   generating output.

**Depends on W:**
4. **Whether FREE (Luna) single-note starts are acceptable async:** 71–133 s p50, 113–226 s p90. Low effort
   would make that 50–76 s / 89–135 s, but it needs its own blind quality review before adoption.
5. **Whether Plus/Pro (Sol) single-note starts are acceptable async:** 129–291 s p50. If W rules them out,
   the remaining options are each an owner decision:
   - Luna, or Luna at low effort, for Challenge start on every tier: a third routing destination;
   - a smaller first batch that fills in the background. Per the tail clustering above, this would not
     reduce Sol's timeout risk.

## Limits

- **n = 10 per cell (4–6 for multi-note): the p90 is a rough tail, and tail latency is not established.**
  The single Sol timeout is 1 in 40 single-note starts; the true rate could be several times higher or lower.
- **The production translation rests on one mini anchor of 10 runs,** measured against production data
  from different days and loads.
- **Multi-note cells use `medium`**; a real learner's last score can select `easy` or `hard`.
- **No quality review in this run.** The first benchmark's blind tier covers default effort; **low effort
  is untested for quality and would need its own blind review before adoption.**
- **mini's one `SCHEMA_OR_PARSE` failure in 10** is consistent with the first run's mini failures. It is
  today's production model.
