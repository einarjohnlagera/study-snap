# Opt-in LLM model benchmark

Test scope only; it does not change product behavior. The runner uses `OpenAiLlmStudyPackService` and the production prompt resources. It does not load Spring, a datasource, or `application.yaml`. It runs sequentially. `LLM_API_KEY` must be set in the shell; never put it in JSONL or a command argument. The owner alone runs paid calls.

## JSONL input

One object per line. Every line requires `id` (unique, filename-safe after punctuation replacement), `leg`, and `tags` (array of strings, possibly empty). Context fields are `learnerLevel` (viewer profile level), `courseProgram` (resolved single joined catalog program, or the note/profile fallback), `subject`, `domainContext` (enum name), and `noteLearnerLevel` (authored note level). The latter four may be null/omitted when production's resolver would return null; do not invent a program, domain or level. The resolver uses the note's `domainContext` before `courseProgram`, and authored `noteLearnerLevel` before the viewer level for curriculum depth. Supply the actual values from the source note, joined program and owner. For multi-program notes, supply the note's domain context and the resolver's actual fallback program value.

| Leg | Exact service signature | Extra JSONL fields |
| --- | --- | --- |
| A | `GeneratedStudyPackContent generateStudyPack(String normalizedNotesText, StudyPackGenerationContext context)` | `noteText` (the normalized note body sent by production) |
| B Challenge | `GeneratedChallengeQuizContent generateChallengeQuiz(String studyPackTitle, String studyPackSummary, List<String> keyConcepts, List<String> disallowedQuestions, int questionCount, String difficulty, StudyPackGenerationContext context)` | `method: "generateChallengeQuiz"`, `studyPackTitle`, `studyPackSummary`, `sourceNoteText`, `keyConcepts`, `disallowedQuestions`, `difficulty` |
| B Board | `List<QuizItem> generateBoardExamQuiz(String studyPackTitle, String studyPackSummary, List<String> keyConcepts, List<String> disallowedQuestions, int questionCount, String difficulty, StudyPackGenerationContext context)` | same fields, `method: "generateBoardExamQuiz"` |
| B Adaptive | `List<QuizItem> generateAdaptivePracticeQuiz(String studyPackTitle, String studyPackSummary, List<String> keyConcepts, List<String> weakConcepts, List<String> disallowedQuestions, int questionCount, StudyPackGenerationContext context)` | same fields except `difficulty`; `method: "generateAdaptivePracticeQuiz"`; `weakConcepts` must be nonempty |
| B Long | `List<QuizItem> generateLongExam(String studyPackTitle, String studyPackSummary, List<String> keyConcepts, List<String> disallowedQuestions, int questionCount, String difficulty, StudyPackGenerationContext context)` | same fields, `method: "generateLongExam"` |
| C | `String generateNoteFromTopic(String topic, StudyPackGenerationContext context)` | `topic` |
| D | same signature as A | `noteText`, `regression: true` |

The harness uses production's smallest normal counts: Study Pack 5 fixed; Challenge 5 (`INITIAL_CHALLENGE_QUIZ_COUNT`); Board 12 (`BOARD_EXAM_SESSION_QUESTION_COUNT` in the pool path); Adaptive 5 (`BASE_QUESTION_COUNT` for one or two weak concepts); Long Exam 20 (low learner-level tier). Use `difficulty` as the production profile value, normally `mixed`, `medium` or `hard` as appropriate to the source. `sourceNoteText` is the public source note body for blind faithfulness scoring; it is not sent to the Leg B service method. `disallowedQuestions` should contain saved Study Pack question text, as the production caller supplies. A/B/D source notes should be public, as specified in the execution plan.

Example Leg A line (illustrative, not a production sample):

```json
{"id":"example-1","leg":"A","noteText":"Cell respiration converts glucose to ATP.","tags":["cells"],"learnerLevel":"COLLEGE","courseProgram":"Biology","subject":"Biology","domainContext":null,"noteLearnerLevel":"COLLEGE"}
```

**⚠️ `./mvnw clean` deletes `backend/target/`, including the input and every run's results.** Do not run `clean` until the results are copied out. Malformed lines are skipped and counted. Keep input and output under `backend/target/llm-benchmark/` or outside this repo. Raw response bodies, results and blind export can contain note text; do not commit them. Each run writes `results.jsonl`, `summary.csv`, and `raw/`. Use a fresh output directory for each model/run; `results.jsonl` appends so an interrupted run stays valid JSONL. A run is bounded by `maxCalls` (default 300) and `spendStopUsd` (default $8, hard maximum $10). The stop is checked before the next HTTP call after the total crosses the threshold, so the final billed call may take the total slightly over the stop. Costs include `output_tokens_details.reasoning_tokens` conservatively and separately report the non-additive output-token figure.

## Owner smoke run

Build the input with the read-only `sample-query.sql` in this directory (its header has the exact `psql` commands; 81 lines expected), then `head -1` it into `backend/target/llm-benchmark/smoke.jsonl`, which is exactly one Leg A line. From the repo root, with `LLM_API_KEY` already set in the shell:

```sh
cd backend
./mvnw test -Dtest=LlmModelBenchmark -Dllm.benchmark=true -Dnativequery.pg.skip=true -Dllm.benchmark.model=gpt-4.1-mini -Dllm.benchmark.input=target/llm-benchmark/smoke.jsonl -Dllm.benchmark.outDir=target/llm-benchmark/smoke-mini -Dllm.benchmark.legs=A -Dllm.benchmark.priceInPerM=0.40 -Dllm.benchmark.priceOutPerM=1.60 -Dllm.benchmark.spendStopUsd=0.25
./mvnw test -Dtest=LlmModelBenchmark -Dllm.benchmark=true -Dnativequery.pg.skip=true -Dllm.benchmark.model=gpt-6-luna -Dllm.benchmark.input=target/llm-benchmark/smoke.jsonl -Dllm.benchmark.outDir=target/llm-benchmark/smoke-luna -Dllm.benchmark.legs=A -Dllm.benchmark.priceInPerM=0.10 -Dllm.benchmark.priceOutPerM=0.50 -Dllm.benchmark.spendStopUsd=0.25
./mvnw test -Dtest=LlmModelBenchmark -Dllm.benchmark=true -Dnativequery.pg.skip=true -Dllm.benchmark.model=gpt-6.1-sol -Dllm.benchmark.input=target/llm-benchmark/smoke.jsonl -Dllm.benchmark.outDir=target/llm-benchmark/smoke-sol -Dllm.benchmark.legs=A -Dllm.benchmark.priceInPerM=2.00 -Dllm.benchmark.priceOutPerM=10.00 -Dllm.benchmark.spendStopUsd=0.25
```

Stop after the smoke run and report Sol's observed cost before the full run, as the approved execution plan requires.

## Owner full run

After the smoke review, create `target/llm-benchmark/full.jsonl` with the approved A/B/C/D sample, then run:

```sh
./mvnw test -Dtest=LlmModelBenchmark -Dllm.benchmark=true -Dnativequery.pg.skip=true -Dllm.benchmark.model=gpt-4.1-mini -Dllm.benchmark.input=target/llm-benchmark/full.jsonl -Dllm.benchmark.outDir=target/llm-benchmark/full-mini -Dllm.benchmark.priceInPerM=0.40 -Dllm.benchmark.priceOutPerM=1.60 -Dllm.benchmark.spendStopUsd=1.50
./mvnw test -Dtest=LlmModelBenchmark -Dllm.benchmark=true -Dnativequery.pg.skip=true -Dllm.benchmark.model=gpt-6-luna -Dllm.benchmark.input=target/llm-benchmark/full.jsonl -Dllm.benchmark.outDir=target/llm-benchmark/full-luna -Dllm.benchmark.priceInPerM=0.10 -Dllm.benchmark.priceOutPerM=0.50 -Dllm.benchmark.spendStopUsd=0.75
./mvnw test -Dtest=LlmModelBenchmark -Dllm.benchmark=true -Dnativequery.pg.skip=true -Dllm.benchmark.model=gpt-6.1-sol -Dllm.benchmark.input=target/llm-benchmark/full.jsonl -Dllm.benchmark.outDir=target/llm-benchmark/full-sol -Dllm.benchmark.priceInPerM=2.00 -Dllm.benchmark.priceOutPerM=10.00 -Dllm.benchmark.spendStopUsd=7.00
```

**Per-model stops above (0.25 ×3 smoke, then 1.50 / 0.75 / 7.00) total $9.25, inside the $10 cap.** If the smoke run shows Sol would need more than $7.00 for this sample, shrink the sample; never raise a stop. The $8 default stop is per command. The approved $10 cap is **across the entire smoke and full exercise**, so review cumulative totals before each command and lower `-Dllm.benchmark.spendStopUsd` to the remaining allowance. Do not treat the per-run $8 default as a fresh budget.

Blind export, entirely offline:

```sh
./mvnw test -Dtest=BlindExport -Dllm.benchmark.blind=true -Dnativequery.pg.skip=true -Dllm.benchmark.blindDirs=target/llm-benchmark/full-mini,target/llm-benchmark/full-luna,target/llm-benchmark/full-sol -Dllm.benchmark.blindOutDir=target/llm-benchmark/blind -Dllm.benchmark.blindCount=20 -Dllm.benchmark.blindSeed=174
```

`blind-items.jsonl` has no model labels; keep `blind-key.jsonl` separate from the adjudicator. The source note text is included for faithfulness scoring. Run `./mvnw test -Dtest='LlmModelBenchmark,LlmModelBenchmarkTest,LegEBenchmarkTest,BlindExport,BlindExportTest' -Dnativequery.pg.skip=true` for offline tests. The package wildcard `-Dtest='com.studysnap.backend.benchmark.*'` matches no classes with this Surefire configuration; do not use `-Dsurefire.failIfNoSpecifiedTests=false` to hide that. Normal `./mvnw test -Dnativequery.pg.skip=true` skips the opt-in benchmark and blind export.

## Leg E: Challenge start benchmark

This extension is test scope only and changes no product behavior. Build the paired input with `sample-query-e.sql` (see its header for the `psql` command); keep the resulting `e.jsonl` and every output under `backend/target/llm-benchmark/`. The query emits 52 lines in priority order: S15 1–10, M6x3 1–6, S10 1–10, S12 1–10, M3x6 1–6, VLOW 1–10. Single-note run *k* uses the same pack in all four single-note cells. M3x6 run *k* uses the first three packs of M6x3 run *k*. Each six-pack group has one shared subject, and groups are disjoint from each other and from singles. **At least six subjects must each have six eligible packs after the 10 singles are excluded.** If the query returns fewer than 52 lines, stop and report the shortage; do not relax the subject rule. The query uses the same public original-note, Study Pack, 400–12,000 character, context approximation, and md5 selection rules as `sample-query.sql`, with a Leg E salt.

Each E JSONL line has `id`, `leg: "E"`, `cell`, `run` (starting at 1), and `sources`. Every source contains `studyPackTitle`, `studyPackSummary`, `keyConcepts`, `disallowedQuestions` (saved quiz question texts), `sourceNoteText`, `tags`, `learnerLevel`, `noteLearnerLevel`, `domainContext`, `subject`, and `courseProgram`. The harness takes question count, difficulty, and allocation from the cell; input values cannot override them. Source counts must be one for S10/S12/S15/VLOW, six for M6x3, and three for M3x6. A malformed selected source count is counted in `summary.csv` and stops preflight before any HTTP call. M cells use the production default `medium` profile; a real learner's latest Quick Review score can select `easy` or `hard` while the multi-note count stays 18. The SQL's same-subject groups exercise the normal eligibility branch; production also permits differing subjects when the primary and additional notes are members of the same selected plan.

Use `-Dllm.benchmark.legs=E` and optionally `-Dllm.benchmark.cells`, `-Dllm.benchmark.runs`, and `-Dllm.benchmark.cellRunCap`. Runs execute in file order. A run cap such as `M3x6:4` keeps Sol's first four M3x6 packs paired with Luna's six. VLOW is the only cell that adds `reasoning.effort=low`, on every attempt. A 4xx rejection is an HTTP error; run VLOW in its own command so the three-error stop affects only VLOW. The read timeout remains 180 seconds. `results.jsonl` includes accepted question counts after production deduplication, per-source attempts and latency, the sequential start latency, the largest source latency, failure source index, effort, and timeout flag. The E rows in `summary.csv` exclude runs cut off by a spend stop (they are not real starts) and report success rate, timeout count, p50/p90/max start latency, mean attempts, short accepted sets, mean largest source latency for multi-note cells, and both cost formulas.

### Owner run sheet

Run all commands in one sitting so provider latency is comparable. Set `LLM_API_KEY` in the shell and use fresh output directories. These are conservative meter stops; each command includes its input/output prices per 1M tokens. **Smoke stops total $0.64 and full stops total $3.46, together $4.10.** The meter checks its stop *before* each call, so a command that reaches its stop can overshoot by one call: about $0.08 for a Sol command and under $0.01 for the others. **Worst case, stops plus one call on every command: about $4.45, under the approved $4.50 cap.** A command that fails pre-flight now fails loudly instead of passing. After reviewing the smoke's real costs, Release Implementor resets the full-run stops so that stops plus one call per command still fit the cap. No command uses the $8 default.

From `backend/`, smoke pass (`runs=1`, all applicable cells):

```sh
./mvnw test -Dtest=LlmModelBenchmark -Dllm.benchmark=true -Dnativequery.pg.skip=true -Dllm.benchmark.legs=E -Dllm.benchmark.input=target/llm-benchmark/e.jsonl -Dllm.benchmark.outDir=target/llm-benchmark/e-smoke-mini -Dllm.benchmark.model=gpt-4.1-mini -Dllm.benchmark.cells=S15 -Dllm.benchmark.runs=1 -Dllm.benchmark.priceInPerM=0.40 -Dllm.benchmark.priceOutPerM=1.60 -Dllm.benchmark.spendStopUsd=0.02
./mvnw test -Dtest=LlmModelBenchmark -Dllm.benchmark=true -Dnativequery.pg.skip=true -Dllm.benchmark.legs=E -Dllm.benchmark.input=target/llm-benchmark/e.jsonl -Dllm.benchmark.outDir=target/llm-benchmark/e-smoke-luna -Dllm.benchmark.model=gpt-6-luna -Dllm.benchmark.cells=S15,S10,S12,M3x6 -Dllm.benchmark.runs=1 -Dllm.benchmark.priceInPerM=0.10 -Dllm.benchmark.priceOutPerM=0.50 -Dllm.benchmark.spendStopUsd=0.03
./mvnw test -Dtest=LlmModelBenchmark -Dllm.benchmark=true -Dnativequery.pg.skip=true -Dllm.benchmark.legs=E -Dllm.benchmark.input=target/llm-benchmark/e.jsonl -Dllm.benchmark.outDir=target/llm-benchmark/e-smoke-sol -Dllm.benchmark.model=gpt-6.1-sol -Dllm.benchmark.cells=S15,M6x3,S10,S12,M3x6 -Dllm.benchmark.runs=1 -Dllm.benchmark.cellRunCap=M3x6:4 -Dllm.benchmark.priceInPerM=2.00 -Dllm.benchmark.priceOutPerM=10.00 -Dllm.benchmark.spendStopUsd=0.50
./mvnw test -Dtest=LlmModelBenchmark -Dllm.benchmark=true -Dnativequery.pg.skip=true -Dllm.benchmark.legs=E -Dllm.benchmark.input=target/llm-benchmark/e.jsonl -Dllm.benchmark.outDir=target/llm-benchmark/e-smoke-luna-vlow -Dllm.benchmark.model=gpt-6-luna -Dllm.benchmark.cells=VLOW -Dllm.benchmark.runs=1 -Dllm.benchmark.priceInPerM=0.10 -Dllm.benchmark.priceOutPerM=0.50 -Dllm.benchmark.spendStopUsd=0.01
./mvnw test -Dtest=LlmModelBenchmark -Dllm.benchmark=true -Dnativequery.pg.skip=true -Dllm.benchmark.legs=E -Dllm.benchmark.input=target/llm-benchmark/e.jsonl -Dllm.benchmark.outDir=target/llm-benchmark/e-smoke-sol-vlow -Dllm.benchmark.model=gpt-6.1-sol -Dllm.benchmark.cells=VLOW -Dllm.benchmark.runs=1 -Dllm.benchmark.priceInPerM=2.00 -Dllm.benchmark.priceOutPerM=10.00 -Dllm.benchmark.spendStopUsd=0.08
```

Full pass (`runs=2-10`), after the smoke review and stop reset:

```sh
./mvnw test -Dtest=LlmModelBenchmark -Dllm.benchmark=true -Dnativequery.pg.skip=true -Dllm.benchmark.legs=E -Dllm.benchmark.input=target/llm-benchmark/e.jsonl -Dllm.benchmark.outDir=target/llm-benchmark/e-full-mini -Dllm.benchmark.model=gpt-4.1-mini -Dllm.benchmark.cells=S15 -Dllm.benchmark.runs=2-10 -Dllm.benchmark.priceInPerM=0.40 -Dllm.benchmark.priceOutPerM=1.60 -Dllm.benchmark.spendStopUsd=0.07
./mvnw test -Dtest=LlmModelBenchmark -Dllm.benchmark=true -Dnativequery.pg.skip=true -Dllm.benchmark.legs=E -Dllm.benchmark.input=target/llm-benchmark/e.jsonl -Dllm.benchmark.outDir=target/llm-benchmark/e-full-luna -Dllm.benchmark.model=gpt-6-luna -Dllm.benchmark.cells=S15,S10,S12,M3x6 -Dllm.benchmark.runs=2-10 -Dllm.benchmark.priceInPerM=0.10 -Dllm.benchmark.priceOutPerM=0.50 -Dllm.benchmark.spendStopUsd=0.18
./mvnw test -Dtest=LlmModelBenchmark -Dllm.benchmark=true -Dnativequery.pg.skip=true -Dllm.benchmark.legs=E -Dllm.benchmark.input=target/llm-benchmark/e.jsonl -Dllm.benchmark.outDir=target/llm-benchmark/e-full-sol -Dllm.benchmark.model=gpt-6.1-sol -Dllm.benchmark.cells=S15,M6x3,S10,S12,M3x6 -Dllm.benchmark.runs=2-10 -Dllm.benchmark.cellRunCap=M3x6:4 -Dllm.benchmark.priceInPerM=2.00 -Dllm.benchmark.priceOutPerM=10.00 -Dllm.benchmark.spendStopUsd=2.50
./mvnw test -Dtest=LlmModelBenchmark -Dllm.benchmark=true -Dnativequery.pg.skip=true -Dllm.benchmark.legs=E -Dllm.benchmark.input=target/llm-benchmark/e.jsonl -Dllm.benchmark.outDir=target/llm-benchmark/e-full-luna-vlow -Dllm.benchmark.model=gpt-6-luna -Dllm.benchmark.cells=VLOW -Dllm.benchmark.runs=2-10 -Dllm.benchmark.priceInPerM=0.10 -Dllm.benchmark.priceOutPerM=0.50 -Dllm.benchmark.spendStopUsd=0.05
./mvnw test -Dtest=LlmModelBenchmark -Dllm.benchmark=true -Dnativequery.pg.skip=true -Dllm.benchmark.legs=E -Dllm.benchmark.input=target/llm-benchmark/e.jsonl -Dllm.benchmark.outDir=target/llm-benchmark/e-full-sol-vlow -Dllm.benchmark.model=gpt-6.1-sol -Dllm.benchmark.cells=VLOW -Dllm.benchmark.runs=2-10 -Dllm.benchmark.priceInPerM=2.00 -Dllm.benchmark.priceOutPerM=10.00 -Dllm.benchmark.spendStopUsd=0.66
```

## Statement-pair verifier Stage 1 (v0.175.0, signed)

This local, opt-in harness checks a proposed independent statement-pair verifier before any production implementation. The four prompt resources in `src/test/resources/benchmark/verifier/` carry the owner-signed wording and are pinned to the production resources by a drift test. The runner reads only local files and sends the note context plus extracted statements; it never queries or writes a database. The API key comes only from `LLM_API_KEY` in the owner's shell. No paid call occurs in the normal test suite.

Put gold and result files under the git-ignored `target/llm-benchmark/verifier/` directory. The gold file is JSONL, with one item per line: `id`, `stratum` (`N`, `N_CAVEAT`, `P_SEEN`, `P_UNSEEN`, `P_CONSTRUCTED`, `P_CONSTRUCTED_CONTROL`, or `U`), `batchId`, `noteId`, `question`, `keyedPair` (two booleans), `goldPair` (two booleans, or null only for `U`), and `provenance`. Items sharing `batchId` must share `noteId` and go in one call. The notes file is a JSON object keyed by `noteId`; each value has `noteText` and optional nullable `summary`, `domainContext`, `subject`, and `learnerLevel`. Do not commit real note text or questions. A malformed file fails preflight before any HTTP call. Each `outDir` must be fresh and must not contain `results.jsonl`.

From `backend/`, after preparing local gold and notes and setting `LLM_API_KEY` in the shell, run **one model per command**. Fill in the approved model names, prices and per-run stops; keep cumulative spending within the separately approved budget. `spendStopUsd` has a hard maximum of 3.50. The optional `-Dllm.verifier.promptDir=<dir>` replaces the four DRAFT files with owner-signed wording.

```sh
./mvnw test -Dtest=StatementPairVerifierBenchmark -Dnativequery.pg.skip=true -Dllm.verifier=true -Dllm.verifier.model=<sol-model> -Dllm.verifier.gold=target/llm-benchmark/verifier/gold.jsonl -Dllm.verifier.notes=target/llm-benchmark/verifier/notes.json -Dllm.verifier.outDir=target/llm-benchmark/verifier/sol-run -Dllm.verifier.repeats=3 -Dllm.verifier.priceInPerM=<input-price> -Dllm.verifier.priceOutPerM=<output-price> -Dllm.verifier.spendStopUsd=<stop>
./mvnw test -Dtest=StatementPairVerifierBenchmark -Dnativequery.pg.skip=true -Dllm.verifier=true -Dllm.verifier.model=<luna-model> -Dllm.verifier.gold=target/llm-benchmark/verifier/gold.jsonl -Dllm.verifier.notes=target/llm-benchmark/verifier/notes.json -Dllm.verifier.outDir=target/llm-benchmark/verifier/luna-run -Dllm.verifier.repeats=3 -Dllm.verifier.priceInPerM=<input-price> -Dllm.verifier.priceOutPerM=<output-price> -Dllm.verifier.spendStopUsd=<stop>
./mvnw test -Dtest=VerifierScorer -Dnativequery.pg.skip=true -Dllm.verifier.score=true -Dllm.verifier.solDir=target/llm-benchmark/verifier/sol-run -Dllm.verifier.lunaDir=target/llm-benchmark/verifier/luna-run -Dllm.verifier.gold=target/llm-benchmark/verifier/gold.jsonl -Dllm.verifier.scoreOut=target/llm-benchmark/verifier/score.json
```

The offline scorer also accepts `-Dllm.verifier.r104=report` and `-Dllm.verifier.replacementFailure=<fraction>`. It refuses incomplete or mismatched runs. `./mvnw clean` deletes local gold and results under `target/`; retain a separate local copy if needed.

## Stage 2 statement-pair legs P and R (v0.175.0)

`StatementPairStage2Benchmark` is opt-in and skips in the normal suite. Its P arm calls the real `generateMoreChallengeQuiz` path for 40 local cases per model, then a 10-case baseline with only the extra +5 rule removed. It writes per-batch leak, completeness, H4 rejection and generation-call counts, `summary.json`, raw metered responses, and a blind export with the arm key in a separate file. Blind reviewers use the export for P3 difficulty and concept-coverage comparisons; compare P1/P2 counts to the signed pass rules before merge. The R arm supplies one synthetic, R1-consistent gold statement-pair item per case, stubs both checkers to reject it, and makes the real production replacement call on the selected generator model. It reports omitted/replaced counts and replacement failure rate q, exports each replacement with source concept and question for blind R1r key, depth and comparability review, and records formats in the returned questions. The R input uses one rejected item per case so an omitted item cannot shift another item's blind mapping.

Both arms preflight every selected JSONL row, all paths and the spending limit **before any HTTP call**. Only the owner runs paid commands. `MeteringInterceptor` stops at the configured spend or call limit; each command must set `spendStopUsd` at or below **3.50**, and the owner must keep cumulative Stage 2 spend within the separately approved budget. A stop marks the run incomplete. Use fresh output directories under `target/llm-benchmark/verifier/`. The key comes only from `LLM_API_KEY` in the owner's shell. No command below is run by the normal test suite.

Input is JSONL under `target/llm-benchmark/verifier/`, one case per line with unique filename-safe `id`, nonblank `noteText`, `summary`, `subject`, `learnerLevel`, and nonempty `keyConcepts`. Optional fields are `courseProgram`, `domainContext` (enum), `difficulty` (default `medium`), `disallowedQuestions` and `existingConcepts`. Leg R also needs exactly one `rejected` object with `question`, `concept`, `statement1Correct` and `statement2Correct`; the question must contain both statement markers. Use the same ordered cases for Sol and Luna. Keep source notes and all exports git-ignored.

From `backend/`, after owner approval and with the local fixture and `LLM_API_KEY` set, run each model and leg with its actual input/output prices and a fresh output directory. The placeholders below must be filled from the approved Stage 2 run sheet; no default price or spending amount is assumed:

```sh
./mvnw test -Dtest=StatementPairStage2Benchmark -Dnativequery.pg.skip=true -Dllm.stage2=true -Dllm.stage2.leg=P -Dllm.stage2.model=<model> -Dllm.stage2.input=target/llm-benchmark/verifier/stage2-p.jsonl -Dllm.stage2.outDir=target/llm-benchmark/verifier/p-<model> -Dllm.stage2.batches=40 -Dllm.stage2.baselineBatches=10 -Dllm.stage2.maxCalls=200 -Dllm.stage2.priceInPerM=<price> -Dllm.stage2.priceOutPerM=<price> -Dllm.stage2.spendStopUsd=<approved-stop>
./mvnw test -Dtest=StatementPairStage2Benchmark -Dnativequery.pg.skip=true -Dllm.stage2=true -Dllm.stage2.leg=R -Dllm.stage2.model=<model> -Dllm.stage2.input=target/llm-benchmark/verifier/stage2-r.jsonl -Dllm.stage2.outDir=target/llm-benchmark/verifier/r-<model> -Dllm.stage2.batches=40 -Dllm.stage2.baselineBatches=0 -Dllm.stage2.maxCalls=80 -Dllm.stage2.priceInPerM=<price> -Dllm.stage2.priceOutPerM=<price> -Dllm.stage2.spendStopUsd=<approved-stop>
```

Offline safety and behavior tests:

```sh
./mvnw test -Dtest=StatementPairStage2BenchmarkTest,StatementPairProductionParityTest,StatementPairVerifierProductionTest -Dnativequery.pg.skip=true
```
