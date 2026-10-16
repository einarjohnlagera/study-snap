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

`blind-items.jsonl` has no model labels; keep `blind-key.jsonl` separate from the adjudicator. The source note text is included for faithfulness scoring. Run `./mvnw test -Dtest='LlmModelBenchmark,LlmModelBenchmarkTest,BlindExport,BlindExportTest' -Dnativequery.pg.skip=true` for offline tests. The package wildcard `-Dtest='com.studysnap.backend.benchmark.*'` matches no classes with this Surefire configuration; do not use `-Dsurefire.failIfNoSpecifiedTests=false` to hide that. Normal `./mvnw test -Dnativequery.pg.skip=true` skips the opt-in benchmark and blind export.
