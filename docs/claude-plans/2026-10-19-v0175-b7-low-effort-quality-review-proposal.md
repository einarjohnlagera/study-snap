# B7 — low reasoning effort for FREE quiz generation: quality review proposal

**Status: PROPOSAL for owner approval. No paid call is authorized. Lower effort is NOT adopted** (owner B7);
production keeps the current, validated reasoning setting.

Written 2026-10-19 by Release Implementor for owner decision B7
(`docs/claude-plans/2026-10-19-owner-decisions-v0175-b1-b7.md`).

## Why it is worth reviewing

The targeted benchmark (`docs/claude-findings/2026-10-19-v0174-targeted-challenge-start-benchmark-results.md`)
measured a 15-question Challenge start on Luna:

| | p50 | p90 | Succeeded |
|---|---|---|---|
| Default effort | 48.8 s | 59.2 s | 10/10 |
| Low effort (`reasoning.effort: "low"`) | 27.8 s | 35.3 s | 10/10 |

That cut matters for B1's 30-second target, and it reaches every FREE learner's live start. **Sol barely
benefits** (106.7 s → 89.3 s), so this proposal covers FREE (Luna) only.

## Phase 1: a blind review of questions already generated (no OpenAI cost)

**The questions already exist.** The benchmark saved **150 Luna default-effort and 150 Luna low-effort
questions**, generated on the **same 10 Study Packs** (paired), as real 15-question `hard` Challenge starts.
They are under `backend/target/llm-benchmark/e-*-luna*`, git-ignored and never committed.

**Method**, the same blind design as the first benchmark's quality tier:
1. Export both sets blind, using a fixed seed, effort labels removed, and the questions shuffled across
   both arms. Check the export for leaked labels before scoring.
   - **`BlindExport` keys its arms by model, and both arms here are Luna.** So the export needs a small
     test-scope tweak to key by `reasoningEffort`, or a short local script over the same `results.jsonl`
     files. Neither is a paid call or a production change.
2. A **fresh Claude agent with no context** scores every question (all 300, not a sample). It sees only
   the source note and the question, never the key file.
3. **Rubric:** the benchmark's 1–5 scores (factual correctness, single best answer, distractor quality,
   explanation, faithfulness to the note), plus a learner-harming `defect` flag. Two dimensions the owner
   named are added:
   - **clarity** (1–5);
   - **appropriate difficulty** for `hard` (1–5).
4. **Answer keys:** the agent works every numeric item and checks every key against the note. A flagged key
   is re-checked independently by a second fresh agent before it counts.

**Acceptance criteria, stated before scoring.** Low effort is acceptable only if **all** hold, comparing low
against default on the paired packs:
- **Verified answer-key defects in low ≤ default.** If default has 0, low must have 0.
- For each of factual, single best answer, explanation and faithfulness: **the mean for low is no more than
  0.2 below default.**
- For clarity and difficulty: **no more than 0.3 below default.**
- **No pack where low has two or more items scoring ≤ 2** on any dimension.
- **Learner-harming defects in low ≤ default.**

**Limits:** n = 150 per arm, on 10 packs, at one difficulty, scored by one adjudicator.
- **Passing shows no regression detected. It does not prove equal quality.**
- Failing any criterion means low effort is rejected for now.

## Phase 2: only if Phase 1 is inconclusive or passes narrowly (paid; separate approval)

- Widen the paired sample: 20 more packs, at `easy` and `medium` as well as `hard`, run through the existing
  harness on Luna default and Luna low (cells S10, S12 and S15, plus a low-effort cell per size, a small
  harness tweak).
- **Estimated cost: about $0.40** at list price (Luna is about $0.002–0.004 per call; 120 calls). Hard stop
  $1.00.
- Then repeat Phase 1's blind review on the new questions.

## If it passes

Adopting it would be a routing change: FREE quiz generation (`LLM_MODEL_INTERACTIVE_QUIZ` or Luna, once
v0.175.0 routes Challenge by tier) would send `reasoning.effort: "low"`. That is a config-driven request
setting, with a test asserting the request body, and a separate owner decision. **It is not part of
v0.175.0 unless the owner approves it after the review.**

## Owner decisions needed

1. **Approve Phase 1** (no OpenAI cost; Release Implementor runs the export and the blind agents locally).
2. Phase 2 is decided after Phase 1's results.
