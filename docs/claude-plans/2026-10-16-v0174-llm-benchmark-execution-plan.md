# v0.174.0 item 1 — LLM model benchmark: execution plan

**Status: APPROVED by the owner, 2026-10-16. The OWNER runs the paid calls (smoke run and full run) from their own terminal. Nothing has been spent and no API call has been made yet.**
Written 2026-10-16 by Release Implementor. It executes Feature Planner's design,
`docs/claude-plans/2026-10-16-llm-model-migration-benchmark-design.md`, whose metrics and pass/fail rule
are binding here. The owner approved a **$10 maximum** (2026-10-16): a ceiling, not an allowance for
repeated runs.

## What it decides

- Whether `gpt-6-luna` (FREE) and `gpt-6.1-sol` (Plus/Pro, and ADMIN curation) are **non-inferior** to
  today's `gpt-4.1-mini` on our actual request shape.
- Their real per-generation cost.
- That result gates item 3, the default-model switch.
- It does **not** measure whether answer-key defects get rarer. At a 0.06% production rate, no feasible
  sample can show that (design §"What this benchmark can and cannot show").

## Prices: confirmed on OpenAI's own pricing page, 2026-10-16

Standard rates, per 1M tokens (input / output):

| Model | Input | Output |
|---|---|---|
| `gpt-4.1-mini` | $0.40 | $1.60 |
| `gpt-6-luna` | $0.10 | $0.50 |
| `gpt-6.1-sol` | $2.00 | $10.00 |

- Batch and Flex are half price for the GPT-6 models. They are not used here: the benchmark must time
  the same synchronous path production uses.
- The page does not say how reasoning tokens are billed. **The runner prices every call from that
  response's own `usage` object, counting `output_tokens_details.reasoning_tokens` as output.** That
  is the conservative assumption.

## Who does what

| Step | Who | Spends money? |
|---|---|---|
| Write the harness | Codex, in a worktree; its sandbox has no network, so it cannot spend | No |
| Review, build, PR into `releases/v0.174.0` | Release Implementor | No |
| Pull the note sample (read-only `SELECT`) | Release Implementor | No |
| Run the paid calls | **Owner** (decided 2026-10-16): one command in their own terminal, with the key in their environment | Yes |
| Blind quality adjudication | A cold Claude agent, read-only | No (Claude tokens, not API dollars) |
| Results write-up and recommendation | Release Implementor | No |

**The API key never enters a prompt, a file in the repo, or this session's context.** The runner reads
it from the environment only.

## The harness

- **Test-scope only.** `backend/src/test/java/.../benchmark/LlmModelBenchmark`. It is skipped unless
  run with `-Dllm.benchmark=true`, and refuses to start without the API key set. It never runs in a
  normal `./mvnw test` or in CI.
- **Our real request path.** It builds the real `OpenAiLlmStudyPackService` with only the model string
  overridden per candidate. The prompts, the JSON schema and `buildJsonSchemaRequest` are therefore
  exactly what production sends, with no added `reasoning_effort`: Sol runs at its default, per owner
  decision 3.
- **Records the raw response**, so the multi-item / stray-token risk in design §"Known compatibility
  risk" is observed directly. A non-empty but malformed or multi-item response is counted as its own
  failure class, not swallowed.
- **Hard spend stop.**
  - A running total, priced from each response's `usage`, stops the run before the next call once it
    passes **$8**, leaving $2 of headroom under the cap.
  - A `--max-calls` limit applies as well.
  - Both are unit-tested offline with a stubbed HTTP client.
- **Read-only.** No database connection: notes come from a local input file, and output is local files
  only.
- **Outputs:**
  - `results.jsonl`: one line per call with model, mode, latency, token counts including reasoning,
    cost, success/failure class, schema validity, structural checks, and the H4 check
    (`QuizValidationUtils.isAnswerExplanationInternallyInconsistent`, called read-only);
  - `summary.csv`;
  - an anonymised file for adjudication, with items shuffled and model labels removed.
- Codex effort: **medium.** Test-only code with no D10 trigger.

## Sample

- **Public notes only** (`visibility = 'PUBLIC'`, author not deleted). Their content is already public,
  so no private learner text goes anywhere new.
- Stratified across subjects and Domain Contexts (ADR-001).
- Pulled by one read-only `SELECT`, saved to a local file outside the repo, and **never committed**.
- **Regression set:** the source notes of the confirmed defective questions in the 2026-09-19 incident
  doc and the 2026-10-15 re-scan, run through each model.

## Call plan and cost

| Leg | Notes | Methods | Calls (× 3 models) |
|---|---|---|---|
| A. Study Pack (+ Quick Review quiz) | 40 | `generateStudyPackOnce` | 120 |
| B. Other quiz modes | 8 per mode | Challenge, Board Exam, Adaptive Practice, Long Exam (single, not parallel) | 96 |
| C. Note from topic (curation path) | 5 | `generateNoteFromTopicOnce` | 15 |
| D. Regression set | about 6 | `generateStudyPackOnce` | about 18 |

Cost estimate, from production's average of 3,426 input / 986 output tokens per generation:

- **Mini:** about $0.003 per call, so about $0.75 in total.
- **Luna:** about $0.001 per call, so about $0.25 in total.
- **Sol:** about $0.017 per call on visible tokens alone.
  - With reasoning at 1–3× the visible output, about $0.027–0.047 per call, so **about $2.25–4** in
    total.
  - Long Exam calls are larger.
- **Total: roughly $3–5 expected,** with the $8 hard stop and $10 owner cap above it.

## Sequence

1. **Owner approves this plan**, and says who presses run.
2. Codex writes the harness. Release Implementor reviews it, builds it, and opens a PR into
   `releases/v0.174.0`.
3. Release Implementor pulls the sample, read-only.
4. **Smoke run: 1 note × Study Pack × 3 models**, about $0.05.
   - It confirms both new models accept our request shape.
   - It confirms the runner reads reasoning tokens.
   - It shows the **real** Sol cost per call.
   - **Then stop and report it to the owner.** If Sol's real cost makes the full run project over $8,
     the owner decides whether to shrink the sample (for example, Leg A to 25 notes); the cap is not
     raised.
5. Full run, after the owner's go-ahead on the smoke result.
6. Blind adjudication: about 20 items per model, shuffled and unlabelled, scored against the design's
   §8 rubric (factual correctness, single best answer, distractors, explanation, faithfulness to the
   source).
7. A results doc in `docs/claude-findings/`, then a recommendation against the design's pre-stated
   pass/fail rule. The owner decides item 3.

## Pass/fail (pre-stated, from the design)

- **Adopt a candidate only if all of these hold:**
  - It is non-inferior on the deterministic tier: request success, schema validity, structural checks,
    and no higher H4 rejection rate.
  - Its cost is acceptable (Sol's higher cost is expected and does not block on its own).
  - The blind tier shows no regression in factual correctness, distractor quality or faithfulness.
- **A repeated malformed or multi-item response rate blocks adoption** until a defensive-parsing fix or
  a different candidate exists.
- **If Luna fails:** `gpt-5.6` is the design's fallback comparison. It is a second, separately
  approved run, not part of this budget.

## Not in this run

- The CRITIQUE model, covering Interview Practice and Ask Companion: it needs its own assessment.
- A `reasoning_effort` override leg for Sol: only if the default proves materially expensive, and as
  a separate approval.
- Any production write. Any change to code that production runs.
