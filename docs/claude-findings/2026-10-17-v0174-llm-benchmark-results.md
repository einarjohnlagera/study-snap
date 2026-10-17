# v0.174.0 item 1 — LLM model benchmark: results and recommendation

**Status: COMPLETE. Recommendation: both candidates PASS the pre-stated rule. The owner decides item 3.**
Written 2026-10-17 by Release Implementor. Executes
`docs/claude-plans/2026-10-16-v0174-llm-benchmark-execution-plan.md` (owner-approved 2026-10-16) against
the pass/fail rule in `docs/claude-plans/2026-10-16-llm-model-migration-benchmark-design.md`
§"Pass/fail framing for the owner", which was written before any call was made.

The owner ran every paid call from their own terminal (2026-10-16/17, UTC+8). Harness: PR #1506
(`backend/src/test/java/com/studysnap/backend/benchmark/`). No production write, no code change that
production runs. Raw outputs stay under `backend/target/llm-benchmark/` (git-ignored; they hold note text).

## Spend

| Run | Harness cost (`nonAdditiveUsd`) |
|---|---|
| Smoke, 1 Study Pack × 3 models | $0.030 |
| Full, `gpt-4.1-mini` | $0.270 |
| Full, `gpt-6-luna` | $0.118 |
| Full, `gpt-6.1-sol` | $2.203 |
| **Total** | **≈ $2.62 of the $10 cap** |

**Which cost formula is right — settled against the OpenAI usage dashboard (project `NoteLib-Prod`):**
- `usage.output_tokens` already includes `output_tokens_details.reasoning_tokens` (input + output =
  `total_tokens` on every GPT-6 response). The non-additive figure is correct; the conservative one
  double-counts reasoning by about 7–15% on Sol and 25–40% on Luna.
- Luna, the only clean comparison (production does not use it): billed output $0.09 vs harness $0.092;
  billed input $0.015 (cache writes $0.013 + cached $0.002) vs harness $0.027 at list price. **Input is
  billed below list price** because of prompt caching. The harness, and every spend stop, overstated cost.
- Mini cannot be reconciled: `NoteLib-Prod` is also production's project and production runs on
  `gpt-4.1-mini`. The 3 mini timeouts recorded no tokens and may have been billed anyway.
- **The benchmark key is production's key.** Harmless here; noted for future runs.

## Deterministic tier (81 generations per model: A 40 Study Packs, B 32 quiz-mode, C 5 note-from-topic, D 4 regression)

| | `gpt-4.1-mini` (baseline) | `gpt-6-luna` | `gpt-6.1-sol` |
|---|---|---|---|
| Generations succeeded | **77 / 81** | 81 / 81 | 81 / 81 |
| Failures | 3 `HTTP_ERROR` (180 s read timeout), 1 `SCHEMA_OR_PARSE` after retry | none | none |
| Multi-`output_text` responses | 0 | 0 | 0 |
| Mean attempts, Leg B | 1.16 | 1.00 | 1.00 |
| H4 flag rate (`isAnswerExplanationInternallyInconsistent`) | 0 | 0 | 0 |
| Questions produced | 491 | 556 | 556 |
| Study Pack latency p50 / p95 | 7.7 s / 10.4 s | 14.8 s / 19.8 s | 27.3 s / 32.5 s |
| Leg B latency p50 / p95 | 9.3 s / 198 s | 28.6 s / 51.1 s | 55.2 s / 112.7 s |
| Cost per generation | $0.0033 | $0.0015 | $0.0272 |
| Cost per Study Pack (A + D) | $0.0032 | $0.0013 | $0.0225 |

Leg B by method (8 each):

| Method | mini ok / max latency | Luna ok / max latency | Sol ok / max latency |
|---|---|---|---|
| Challenge (5 q) | 7 / 180 s (timeout); successes 6–9 s, p50 6.6 s | 8 / 31 s; p50 25.4 s | 8 / 55 s; p50 43.4 s |
| Board Exam (12 q) | 8 / 26 s | 8 / 50 s | 8 / 89 s |
| Adaptive (5 q) | 8 / 7 s | 8 / 36 s | 8 / 44 s |
| Long Exam (20 q), single-call method | **5** / 203 s (2 timeouts, 1 invalid format) | 8 / 52 s | 8 / **126 s** |

- Both new models return one `reasoning` item then one `message` item. `LlmResponseUtils` skips the
  reasoning item, so the request and parser work unchanged. The compatibility risk in the design did not
  appear in 162 GPT-6 generations.
- Both new models ran at their default `reasoning.effort: "medium"` with nothing sent (owner decision 3).
  Mean reasoning tokens: Luna 635 per Study Pack, Sol 174. Luna reasons more than Sol on this workload.
- **Mini's failures are today's production model**, on the same 180 s timeout production uses. Whether
  production sees Long Exam timeouts at this rate is not established here; it is a production read for
  a later pass (Long Exam in production uses the parallel variant, which this benchmark did not run).

## Blind tier (design §8)

- 20 questions per model, sampled and shuffled with seed 174 by `BlindExport`; model labels removed and
  the file checked for leaks before scoring.
- Scored by a fresh Claude agent (Opus) with no context, restricted to the items file and never shown the
  key. Rubric 1–5 on factual correctness, single best answer, distractor quality, explanation and
  faithfulness to the source note, plus a learner-harming `defect` flag. It worked all 12 numeric items by
  hand: every keyed answer was arithmetically correct.

| Mean score (n = 20 each) | mini | Luna | Sol |
|---|---|---|---|
| Factual | 4.75 | 4.85 | **5.00** |
| Single best answer | 4.35 | 4.75 | **5.00** |
| Distractors | 4.60 | **4.75** | 4.60 |
| Explanation | 4.80 | 4.80 | **5.00** |
| Faithfulness | 4.55 | 4.65 | **4.75** |
| Items with every score 5 | 9 | **14** | 13 |
| Items with any score ≤ 3 | 4 | 2 | 3 |
| Answer-key defects (adjudicator-flagged, not independently verified) | 1 (`blind-32`, two defensible choices) | 1 (`blind-55`, key not supported by the note; a second electrolyte equally defensible) | 0 |

- Sol's one low score (`blind-58`, distractors 2) is two options stating the same concept; the key is right.
- The adjudicator noted near-duplicate pairs (`blind-7`/`12`, `27`/`45`, `15`/`49`). Unblinded, each pair
  is Luna and Sol answering the same note: an artifact of the shared sample, not redundancy within a model.
- **These differences are within noise: n = 20 per model, one adjudicator.** The tier shows **no regression** for either candidate; it does NOT show that either is better, and it is not evidence for an "Enhanced" quality claim in customer copy.
- **One defect in 20 versus zero in 20 is not a difference.** As the design states, no feasible sample can
  measure the ~0.06% production defect rate; this tier tests for regression only.

## Against the pre-stated rule

| Condition | Luna | Sol |
|---|---|---|
| Non-inferior on the deterministic tier (success, schema, structure, H4) | ✅ better than baseline | ✅ better than baseline |
| No repeated malformed or multi-item responses | ✅ 0 | ✅ 0 |
| Cost flat or improved (design) | ✅ about 55% cheaper per generation | ✗ about 8× baseline. **The owner-approved execution plan relaxes this for Sol:** "Sol's higher cost is expected and does not block on its own." Sol's real $0.027 per generation is at the LOW end of the plan's $0.027–0.047 estimate. Plus/Pro pricing is unchanged in this release |
| Blind tier: no regression in factual, distractors, faithfulness | ✅ no regression | ✅ no regression |

**Recommendation:**
- **Adopt `gpt-6-luna` for `LLM_MODEL_FREE`.** It passes every condition and is cheaper and more reliable
  than today's model.
- **Adopt `gpt-6.1-sol` for `LLM_MODEL_PREMIUM`** (Plus/Pro and ADMIN curation, through item 2), with the
  latency caveats below carried into item 2's brief and item 3's handoff.

## Caveats the owner should weigh before item 3

1. **User-facing wait: STARTING a Challenge Quiz, not "+5 Questions". Corrected twice on 2026-10-17 after
   production reads; an earlier draft blamed "+5" and was wrong.**
   - **How both paths work** (`ChallengeQuizService`): start (`:425-470`) and "+5" (`generateMoreQuestions`,
     `:1105`) both claim from the learner's own bank for that pack, then copy from an Official template, and
     call the model live **only for the shortfall**, while the learner waits. **A start asks for 10, 12 or 15
     questions, set by the learner's last Quick Review score on the pack (18 for multi-note;
     `resolveGenerationProfile` `:1364`, `:160-178`, `:267`), not 5,** and the leftovers are banked, so
     a later "+5" is almost always served from the bank.
   - **Production, Challenge sessions since the bank shipped (2026-07-24 → 2026-10-17), read-only:**

     | | USER accounts | ADMIN |
     |---|---|---|
     | Sessions / accounts | 68 / 34 | 11 / 1 |
     | **Starts that called the model live** | **33 sessions, 29 learners** | 2 |
     | New questions generated per live start | 12.2 on average | 8.5 |
     | **Live start wait today, on `gpt-4.1-mini`** | **p50 29.9 s, p90 61.7 s, max 147.3 s** | p50 31.9 s |
     | "+5" batches that called the model live | **2** | 2 |
     | "+5" batches copied from a template | 4 | 0 |

     Only 23 Challenge sessions in the last 30 days, so read the shape, not the precision.
     Method: bank rows grouped by `(origin_session_id, generated_at)`; a session's first batch is its start,
     later ones are "+5"; a batch counts as live unless every question key already existed in another
     account's bank (templates live in the bank under the Official author). Wait = `generated_at` minus the
     session's `created_at`. Checked: no Board Exam rows sit in these sessions, and timing alone does not
     separate start from "+5" (starts land 10-147 s after the session row), which is what the first draft got wrong.
   - **Third read, 2026-10-17 (after the owner's conditional approval): why those starts went live. Most of
     the 33/68 comes from the period before the Official templates existed.** Read-only; this run counts 34
     live starts, all `USER` accounts. The 33 versus 34 gap is not reconciled; the shape is the same.
     - 10 starts were on the learner's own notes, which never have a template.
     - 10 were on adopted Official packs that still have **no template today**.
     - 14 were on adopted packs whose template was seeded **after** the session. Every template in this
       sample was first seeded 2026-09-02 → 09-11. `seedTemplateAsync` writes only when the pack has no
       template rows (`OfficialChallengeQuizTemplateService.java:282,313`), so the earliest row is the seed.
     - **0 starts went live because a learner had used up a template**, and 31 of 34 were the learner's
       first Challenge session on that pack. `copyTemplateQuestions` (`:195-235`) skips only keys already
       in the learner's bank and does not filter by level or difficulty.
   - **Since 2026-09-12: 4 live starts in 26 learner Challenge sessions.** 3 were on adopted packs with no
     template, 1 on the learner's own notes; 8 starts were served from a template.
     - Of the 14 sessions that wrote no bank rows, 9 completed, 4 were forfeited and 1 failed. They were
       probably served from the learner's own bank, but this data cannot confirm it.
     - The 2026-09-12 boundary is read from the seed dates, not from a deploy record.
   - **Official template coverage today:** of 1,997 public Official Study Packs, 1,554 hold the full
     20-question template, 11 hold 10–32 questions, and **432 hold none**.
     - Filling those 432 would be paid generation (on Sol once item 2 lands) and a production write by a
       job, so it is an owner decision. It also sits near the release's "no mass regeneration of question
       pools" rule.
   - **Today's wait depends on start size.** A 15-question live start takes p50 35.7 s / p90 72.6 s
       (n = 21) and a 12-question start 34.4 / 42.0 s (n = 4). The 9 five-question starts (p50 11.7 s) are
       from late July and pull the blended p50 of 30 s down.
     - So the Luna and Sol projection above, which starts from 30 s, is optimistic for 15-question starts.
   - **What the learner sees at start:** a "Generating your quiz..." overlay (`frontend/app/study-packs/[id]/challenge-quiz/page.tsx:1874`).
     **The quiz timer starts only when the questions are ready** (`markSessionReady`, `:2621`), so the start
     wait costs no quiz time.
   - **What the new models do to it:** the benchmark's Challenge leg ran only 5 questions (the harness picked
     `INITIAL_CHALLENGE_QUIZ_COUNT`, a time-limit constant, not the start size), so it understates start. The
     nearest measured size is the 12-question Board Exam method: p50 11.9 s mini, **36.4 s Luna, 69.9 s Sol**
     (max 26 / 50 / 89 s). Production's start already runs about 18 s slower than that benchmark p50 on mini.
     **Rough projection for a 12-question live start: Luna about 55-90 s, Sol about 90-175 s,** and Sol's tail
     could reach the 180 s read timeout, which fails the start. This is an estimate, not a measurement.
   - **"+5", for completeness:** while it waits, the button reads "Adding...", answers are disabled, and the
     countdown keeps running (`:1369-1381`; the 450 s it adds arrives with the response; at zero it
     auto-submits). That is a real but small issue: it went live only 2 times for learners in nearly three months.
   - **This is a product decision, sent to Product UX** (`docs/claude-plans/2026-10-17-challenge-quiz-wait-product-ux-consultation-prompt.md`).
2. **Long Exam headroom on Sol:** 126 s max against the 180 s read timeout, on the single-call method.
   Production uses the parallel variant, so per-call output is smaller; still, record it.
3. **Sample limits:** Leg B fed every model the same existing `gpt-4.1-mini` Study Packs as input; context
   fields approximate `StudyPackGenerationContextResolver`; 20 blind items per model; one adjudicator.
4. **Cost monitoring (item 4):** price from billed usage or the token breakdown. List price overstates
   input cost because of caching, and Sol's per-generation cost is about $0.027.
5. **Not measured:** answer-key improvement at the production rate (infeasible), `LLM_MODEL_CRITIQUE`
   (deferred), Sol at a lower reasoning effort (only if cost proves material; separate approval).
