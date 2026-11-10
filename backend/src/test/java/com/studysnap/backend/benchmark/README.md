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

## Challenge quality evaluation (v0.176.0), owner-run

This is an opt-in **paid** evaluation. Only the owner runs the generation commands. Put `LLM_API_KEY` in the shell environment; never place it in a file, prompt, command argument, or committed artifact. All raw inputs, results, blind items, keys and notes remain under git-ignored `backend/target/` and are never committed. Do not run `clean` until they are saved elsewhere. No database is accessed by the Java harness or offline tools.

From the repository root, use the read-only SQL. `sample-query-quality.sql` requires 10 `COLLEGE` and 10 `BOARD_EXAM_REVIEW` Official notes by each note's own level, with at least four strata and two quantitative notes in each level; it emits zero lines if any target is unavailable. Overall it requires at least six strata and four quantitative notes. `sample-query-stock.sql` uses claimable ADMIN-owned template rows and emits five per pack. Check the line counts (20 and 100) before spending.

```sh
mkdir -p backend/target/llm-benchmark
psql "<read-only database URL>" -At -f backend/src/test/java/com/studysnap/backend/benchmark/sample-query-quality.sql > backend/target/llm-benchmark/quality-e.jsonl
wc -l backend/target/llm-benchmark/quality-e.jsonl
```

**Before any paid call**, generate the exact `-v "pack_ids='uuid-1','uuid-2',..."` argument from the sampled input and preregister the 20 IDs plus its SHA-256. This command needs only Python 3:

```sh
python3 -c 'import json,hashlib,uuid,pathlib; p=pathlib.Path("backend/target/llm-benchmark/quality-e.jsonl"); ids=[str(uuid.UUID(json.loads(line)["packId"])) for line in p.read_text().splitlines()]; assert len(ids)==20 and len(set(ids))==20; arg="-v \"pack_ids="+",".join(chr(39)+x+chr(39) for x in ids)+"\""; out="quality-e.jsonl SHA-256: "+hashlib.sha256(p.read_bytes()).hexdigest()+"\npackIds:\n"+"\n".join(ids)+"\nstock psql argument: "+arg+"\n"; pathlib.Path("backend/target/llm-benchmark/quality-preregistration.txt").write_text(out); print(arg)'
```

Before generating, run this read-only count check with the printed `-v` argument in place of the placeholder. It returns one count per pre-registered pack; **each of the 20 counts must be at least five**. This uses the same NULL-or-current generation-stamp claimability rule as the stock query:

```sh
psql "<read-only database URL>" -v "pack_ids='uuid-1','uuid-2',...,'uuid-20'" -At <<'SQL'
WITH requested AS (SELECT unnest(ARRAY[:pack_ids]::uuid[]) AS pack_id)
SELECT r.pack_id, count(u.id) AS claimable_admin_templates
FROM requested r
LEFT JOIN study_packs sp ON sp.id = r.pack_id
LEFT JOIN challenge_quiz_question_bank bank ON bank.study_pack_id = r.pack_id
  AND bank.claimed_session_id IS NULL
  AND (bank.generation_stamp IS NULL OR bank.generation_stamp = sp.generation_stamp)
LEFT JOIN users u ON u.id = bank.user_id AND u.role = 'ADMIN'
GROUP BY r.pack_id ORDER BY r.pack_id;
SQL
```

Then use the same printed `-v` argument for the stock export and verify 100 rows:

```sh
psql "<read-only database URL>" -v "pack_ids='uuid-1','uuid-2',...,'uuid-20'" -At -f backend/src/test/java/com/studysnap/backend/benchmark/sample-query-stock.sql > backend/target/llm-benchmark/quality-stock.jsonl
wc -l backend/target/llm-benchmark/quality-stock.jsonl
```

Every selected note has its own level, so the pinned viewer `learnerLevel` is irrelevant to level guidance. Cell `S15` asks for 15 `hard` questions. The evaluation covers both `COLLEGE` and `BOARD_EXAM_REVIEW` notes and reports overall and per-level results; per-level reads are descriptive only, never gate inputs. `BOARD_EXAM_REVIEW` notes already receive “plausible distractors, multi-step thinking” through level guidance, so a smaller prompt effect there is expected. Saved quiz texts supply the disallowed-question list in every arm; the bank is not used for generation. A failed or spend-stopped start remains in `results.jsonl` and is not retried by the harness. Each row and `summary.csv` carry the arm and the SHA-256 of the actual Challenge developer template. `validatorRejected` and `validatorRetried` in quality rows come from the production `challenge_quiz_generation_counts` log line attached in test scope; `attempts` is the separate API-call proxy. If a successful start has no log event, its telemetry is `MISSING`, the counts are null, and the summary reports `telemetryMissingStarts`.

From `backend/`, run A2 and B2 back to back, then A3 and B3 back to back, with A1 anywhere, all in one sitting. The order controls time-of-day latency drift. First run the smoke pass (`runs=1`; the input still contains all 20 notes and passes preflight):

```sh
./mvnw test -Dtest=LlmModelBenchmark -Dnativequery.pg.skip=true -Dllm.benchmark=true -Dllm.benchmark.qualityEval=true -Dllm.benchmark.legs=E -Dllm.benchmark.cells=S15 -Dllm.benchmark.expectedNotes=20 -Dllm.benchmark.runs=1 -Dllm.benchmark.input=target/llm-benchmark/quality-e.jsonl -Dllm.benchmark.arm=A2 -Dllm.benchmark.outDir=target/llm-benchmark/quality-smoke-A2 -Dllm.benchmark.model=gpt-6-luna -Dllm.benchmark.priceInPerM=0.10 -Dllm.benchmark.priceOutPerM=0.50 -Dllm.benchmark.spendStopUsd=0.03
./mvnw test -Dtest=LlmModelBenchmark -Dnativequery.pg.skip=true -Dllm.benchmark=true -Dllm.benchmark.qualityEval=true -Dllm.benchmark.legs=E -Dllm.benchmark.cells=S15 -Dllm.benchmark.expectedNotes=20 -Dllm.benchmark.runs=1 -Dllm.benchmark.input=target/llm-benchmark/quality-e.jsonl -Dllm.benchmark.arm=B2 -Dllm.benchmark.challengePromptFile=src/test/resources/benchmark/challenge-quality/challenge-quiz-developer.proposed.txt -Dllm.benchmark.outDir=target/llm-benchmark/quality-smoke-B2 -Dllm.benchmark.model=gpt-6-luna -Dllm.benchmark.priceInPerM=0.10 -Dllm.benchmark.priceOutPerM=0.50 -Dllm.benchmark.spendStopUsd=0.03
./mvnw test -Dtest=LlmModelBenchmark -Dnativequery.pg.skip=true -Dllm.benchmark=true -Dllm.benchmark.qualityEval=true -Dllm.benchmark.legs=E -Dllm.benchmark.cells=S15 -Dllm.benchmark.expectedNotes=20 -Dllm.benchmark.runs=1 -Dllm.benchmark.input=target/llm-benchmark/quality-e.jsonl -Dllm.benchmark.arm=A3 -Dllm.benchmark.outDir=target/llm-benchmark/quality-smoke-A3 -Dllm.benchmark.model=gpt-6.1-sol -Dllm.benchmark.priceInPerM=2.00 -Dllm.benchmark.priceOutPerM=10.00 -Dllm.benchmark.spendStopUsd=0.12
./mvnw test -Dtest=LlmModelBenchmark -Dnativequery.pg.skip=true -Dllm.benchmark=true -Dllm.benchmark.qualityEval=true -Dllm.benchmark.legs=E -Dllm.benchmark.cells=S15 -Dllm.benchmark.expectedNotes=20 -Dllm.benchmark.runs=1 -Dllm.benchmark.input=target/llm-benchmark/quality-e.jsonl -Dllm.benchmark.arm=B3 -Dllm.benchmark.challengePromptFile=src/test/resources/benchmark/challenge-quality/challenge-quiz-developer.proposed.txt -Dllm.benchmark.outDir=target/llm-benchmark/quality-smoke-B3 -Dllm.benchmark.model=gpt-6.1-sol -Dllm.benchmark.priceInPerM=2.00 -Dllm.benchmark.priceOutPerM=10.00 -Dllm.benchmark.spendStopUsd=0.12
./mvnw test -Dtest=LlmModelBenchmark -Dnativequery.pg.skip=true -Dllm.benchmark=true -Dllm.benchmark.qualityEval=true -Dllm.benchmark.legs=E -Dllm.benchmark.cells=S15 -Dllm.benchmark.expectedNotes=20 -Dllm.benchmark.runs=1 -Dllm.benchmark.input=target/llm-benchmark/quality-e.jsonl -Dllm.benchmark.arm=A1 -Dllm.benchmark.outDir=target/llm-benchmark/quality-smoke-A1 -Dllm.benchmark.model=gpt-4.1-mini -Dllm.benchmark.priceInPerM=0.40 -Dllm.benchmark.priceOutPerM=1.60 -Dllm.benchmark.spendStopUsd=0.03
```

Review the smoke results and cumulative spend. Then run the remaining 19 notes (`runs=2-20`) with fresh output directories:

```sh
./mvnw test -Dtest=LlmModelBenchmark -Dnativequery.pg.skip=true -Dllm.benchmark=true -Dllm.benchmark.qualityEval=true -Dllm.benchmark.legs=E -Dllm.benchmark.cells=S15 -Dllm.benchmark.expectedNotes=20 -Dllm.benchmark.runs=2-20 -Dllm.benchmark.input=target/llm-benchmark/quality-e.jsonl -Dllm.benchmark.arm=A2 -Dllm.benchmark.outDir=target/llm-benchmark/quality-full-A2 -Dllm.benchmark.model=gpt-6-luna -Dllm.benchmark.priceInPerM=0.10 -Dllm.benchmark.priceOutPerM=0.50 -Dllm.benchmark.spendStopUsd=0.15
./mvnw test -Dtest=LlmModelBenchmark -Dnativequery.pg.skip=true -Dllm.benchmark=true -Dllm.benchmark.qualityEval=true -Dllm.benchmark.legs=E -Dllm.benchmark.cells=S15 -Dllm.benchmark.expectedNotes=20 -Dllm.benchmark.runs=2-20 -Dllm.benchmark.input=target/llm-benchmark/quality-e.jsonl -Dllm.benchmark.arm=B2 -Dllm.benchmark.challengePromptFile=src/test/resources/benchmark/challenge-quality/challenge-quiz-developer.proposed.txt -Dllm.benchmark.outDir=target/llm-benchmark/quality-full-B2 -Dllm.benchmark.model=gpt-6-luna -Dllm.benchmark.priceInPerM=0.10 -Dllm.benchmark.priceOutPerM=0.50 -Dllm.benchmark.spendStopUsd=0.15
./mvnw test -Dtest=LlmModelBenchmark -Dnativequery.pg.skip=true -Dllm.benchmark=true -Dllm.benchmark.qualityEval=true -Dllm.benchmark.legs=E -Dllm.benchmark.cells=S15 -Dllm.benchmark.expectedNotes=20 -Dllm.benchmark.runs=2-20 -Dllm.benchmark.input=target/llm-benchmark/quality-e.jsonl -Dllm.benchmark.arm=A3 -Dllm.benchmark.outDir=target/llm-benchmark/quality-full-A3 -Dllm.benchmark.model=gpt-6.1-sol -Dllm.benchmark.priceInPerM=2.00 -Dllm.benchmark.priceOutPerM=10.00 -Dllm.benchmark.spendStopUsd=1.40
./mvnw test -Dtest=LlmModelBenchmark -Dnativequery.pg.skip=true -Dllm.benchmark=true -Dllm.benchmark.qualityEval=true -Dllm.benchmark.legs=E -Dllm.benchmark.cells=S15 -Dllm.benchmark.expectedNotes=20 -Dllm.benchmark.runs=2-20 -Dllm.benchmark.input=target/llm-benchmark/quality-e.jsonl -Dllm.benchmark.arm=B3 -Dllm.benchmark.challengePromptFile=src/test/resources/benchmark/challenge-quality/challenge-quiz-developer.proposed.txt -Dllm.benchmark.outDir=target/llm-benchmark/quality-full-B3 -Dllm.benchmark.model=gpt-6.1-sol -Dllm.benchmark.priceInPerM=2.00 -Dllm.benchmark.priceOutPerM=10.00 -Dllm.benchmark.spendStopUsd=1.40
./mvnw test -Dtest=LlmModelBenchmark -Dnativequery.pg.skip=true -Dllm.benchmark=true -Dllm.benchmark.qualityEval=true -Dllm.benchmark.legs=E -Dllm.benchmark.cells=S15 -Dllm.benchmark.expectedNotes=20 -Dllm.benchmark.runs=2-20 -Dllm.benchmark.input=target/llm-benchmark/quality-e.jsonl -Dllm.benchmark.arm=A1 -Dllm.benchmark.outDir=target/llm-benchmark/quality-full-A1 -Dllm.benchmark.model=gpt-4.1-mini -Dllm.benchmark.priceInPerM=0.40 -Dllm.benchmark.priceOutPerM=1.60 -Dllm.benchmark.spendStopUsd=0.18
```

The five smoke stops total **$0.33** (A2 0.03, B2 0.03, A3 0.12, B3 0.12, A1 0.03); the five full stops total **$3.28** (A2 0.15, B2 0.15, A3 1.40, B3 1.40, A1 0.18). Their sum is **$3.61**. The meter checks before each call, so one final call per command may overshoot its stop; Sol is about $0.07 per call and the other models about $0.01, so the estimated worst case is about **$3.95**, inside the **$4.00** cap. The Sol full stops (1.40) sit about 17% above the expected spend of roughly $1.20 per Sol arm (19 notes × 15 questions × about $0.0042 per delivered question, from the v0.175.0 R1 runs); the B arms send a longer prompt but the cost is dominated by output tokens. A Sol arm that stops early leaves pairs missing, which is recorded and never re-run; any re-run spends from the same **$4.00**; a stop is never raised.

Afterward, export and report entirely offline. The exporter consumes all ten result directories, includes every successful generated item and the stock items, and shuffles fixed-size resumable chunks. Give adjudicators only the chunks and `notes.jsonl`; retain `blind-key.jsonl` separately. The descriptor report lists missing or failed pair sides, applies a documented **15 of 20** minimum for paired notes, and computes G4, G6 and G7 diagnostics. No script rejects a question.

```sh
./mvnw test -Dtest=QualityBlindExport -Dnativequery.pg.skip=true -Dllm.benchmark.qualityBlind=true -Dllm.benchmark.blindDirs=target/llm-benchmark/quality-smoke-A1,target/llm-benchmark/quality-full-A1,target/llm-benchmark/quality-smoke-A2,target/llm-benchmark/quality-full-A2,target/llm-benchmark/quality-smoke-A3,target/llm-benchmark/quality-full-A3,target/llm-benchmark/quality-smoke-B2,target/llm-benchmark/quality-full-B2,target/llm-benchmark/quality-smoke-B3,target/llm-benchmark/quality-full-B3 -Dllm.benchmark.stock=target/llm-benchmark/quality-stock.jsonl -Dllm.benchmark.blindOutDir=target/llm-benchmark/quality-blind -Dllm.benchmark.blindSeed=176 -Dllm.benchmark.blindChunk=100
./mvnw test -Dtest=QualityDescriptors -Dnativequery.pg.skip=true -Dllm.benchmark.qualityDescriptors=true -Dllm.benchmark.input=target/llm-benchmark/quality-e.jsonl -Dllm.benchmark.blindDirs=target/llm-benchmark/quality-smoke-A1,target/llm-benchmark/quality-full-A1,target/llm-benchmark/quality-smoke-A2,target/llm-benchmark/quality-full-A2,target/llm-benchmark/quality-smoke-A3,target/llm-benchmark/quality-full-A3,target/llm-benchmark/quality-smoke-B2,target/llm-benchmark/quality-full-B2,target/llm-benchmark/quality-smoke-B3,target/llm-benchmark/quality-full-B3 -Dllm.benchmark.descriptorsOutDir=target/llm-benchmark/quality-descriptors
```

## Challenge quality scoring and gates (offline)

The export's `notes.jsonl` contains `noteRef`, `studyPackSummary`, `keyConcepts`, `sourceNoteText`, and `noteLevel`. Blind item chunks contain only `id`, `noteRef`, `question`, `choices`, `questionFormat`, `keyedAnswer`, and `explanation`. Keep the blind key, review order, manifests, scores, reviews, adjudications, and reports private under `target/llm-benchmark/`; give reviewers only blind items, `notes.jsonl`, and their batch files. The review plan uses no scores and can be frozen before scoring.

Each line of `scores-codex.jsonl`, `reviews-opus.jsonl`, and `adjudications.jsonl` has exactly this schema:

```json
{"id":"blind-0001","reviewerId":"codex-1","round":"PRIMARY","factuallyCorrect":"YES","grounding":"GROUNDED","critical":false,"criticalType":null,"distractorPlausibility":4,"modeAlignment":4,"cognitiveDemand":3,"clarity":5,"faithfulness":5,"seriousGiveaway":false,"giveawayPattern":null}
```

`round` is `PRIMARY`, `INDEPENDENT_REVIEW`, `ARM_RE_REVIEW`, or `ADJUDICATION`. Factual values are `YES`, `NO`, `UNVERIFIABLE`; grounding values are `GROUNDED`, `UNSUPPORTED`, `CONTRADICTED`. `criticalType` is required when `critical` is true and is one of `WRONG_KEY`, `EXPLANATION_CONTRADICTS_KEY`, `CONTRADICTS_GENERATOR_INPUTS`, `CONTRADICTS_FULL_NOTE`, `SOURCE_CONFLICT`, `PARTLY_CORRECT_DISTRACTOR`, `SECOND_DEFENSIBLE_ANSWER`, `AMBIGUOUS_STEM`, `COMPLEXITY_UNSUPPORTED_BY_INPUTS`, `OTHER`. `giveawayPattern` is required when `seriousGiveaway` is true and is one of `LENGTH_OR_SPECIFICITY`, `ABSOLUTE_WORDS`, `GRAMMAR_AGREEMENT`, `FORMAT_ODD_ONE_OUT`, `DUPLICATE_CHOICES`, `ALL_OR_NONE_OF_THE_ABOVE`. Scores are whole numbers from 1 to 5. `distractorPlausibility`, `seriousGiveaway`, and `giveawayPattern` are null for non-choice items (`TRUE_FALSE`, `IDENTIFICATION`, and `ENUMERATION` in the production Challenge format set). `criticalType` and `giveawayPattern` are null when their flags are false. Unknown values, missing or extra fields in a PRIMARY row mark that arm incompletely scored and its gates INCONCLUSIVE. Invalid independent-review or adjudication rows and reviewer identities abort the offline report.

Use the following order after the export and descriptor commands above. Run these commands from `backend/`; they read and write local files only. Record `reviewSeed` and `review-manifest.json`'s `sha256` in the pre-registration before primary scoring. The planner refuses to change an existing order or seed. Never give `review-order.jsonl` or either manifest to scorers. The batch manifest records private selection reasons; every batch row has the normal blind item shape, and all reviewers use the same rubric instructions. Complete an entire proposed arm's `ARM_RE_REVIEW` with one fresh reviewer when the report requests escalation, and adjudicate reviewer disagreements before treating that arm as passing.

```sh
./mvnw test -Dtest=QualityReviewPlan -Dnativequery.pg.skip=true -Dllm.benchmark.reviewPlan=true -Dllm.benchmark.blindOutDir=target/llm-benchmark/quality-blind -Dllm.benchmark.reviewOutDir=target/llm-benchmark/quality-review-plan -Dllm.benchmark.reviewSeed=176
# Freeze scores-codex.jsonl after blind primary scoring.
./mvnw test -Dtest=QualityReviewBatches -Dnativequery.pg.skip=true -Dllm.benchmark.reviewBatches=true -Dllm.benchmark.blindOutDir=target/llm-benchmark/quality-blind -Dllm.benchmark.reviewOutDir=target/llm-benchmark/quality-review-plan -Dllm.benchmark.scoresCodex=target/llm-benchmark/scores-codex.jsonl -Dllm.benchmark.batchesOutDir=target/llm-benchmark/quality-opus-batches
# Complete cold independent review and adjudication. Use empty JSONL files if there are no adjudications.
./mvnw test -Dtest=QualityGateReport -Dnativequery.pg.skip=true -Dllm.benchmark.qualityGates=true -Dllm.benchmark.rubricVersion=v1 -Dllm.benchmark.blindOutDir=target/llm-benchmark/quality-blind -Dllm.benchmark.reviewOutDir=target/llm-benchmark/quality-review-plan -Dllm.benchmark.scoresCodex=target/llm-benchmark/scores-codex.jsonl -Dllm.benchmark.reviewsOpus=target/llm-benchmark/reviews-opus.jsonl -Dllm.benchmark.adjudications=target/llm-benchmark/adjudications.jsonl -Dllm.benchmark.descriptors=target/llm-benchmark/quality-descriptors/descriptors.json -Dllm.benchmark.gatesOutDir=target/llm-benchmark/quality-gates
```

`-Dllm.benchmark.rubricVersion=v1|v2` is required for the gate command. `v1` reproduces the frozen `v0.176.0` rubric; `v2` applies the signed `v0.177.0` G5 rule. Select the version explicitly for each run; the recorded `v0.176.0` evaluation remains under `v1`.

The report writes `gates.json` and `gates.md`. It is HOLD unless Luna and Sol both pass G1–G8; level splits are descriptive. It selects improvement and failure note references by the largest positive and negative paired per-note distractor difference, breaking ties by pack id; resolve their item ids through the blind export for the owner. The calculator's signed thresholds live in `QualityGateParameters`. The independently reviewed unflagged floor is 60 choice-based items per arm, drawn in a seeded order stratified across notes and question formats; Codex-flagged items are a separate review stratum and are excluded from that floor. The G4 95% interval transforms the pooled unflagged exact binomial interval; it does not decide the gate. Validator retry rate is `sum retried / sum requested` over successful starts with CAPTURED telemetry; rejection rate is `sum rejected / sum requested` over the same starts. Only `outcome=returned` generation-count lines enter these sums; `outcome=threw` lines increment `discardedAttempts`. A failed start has `NOT_APPLICABLE` telemetry and null counts; a successful start with no returned line has `MISSING` telemetry and null counts. `validatorVerified > 0` starts are listed and excluded from both rates. The report also lists starts where `retried == rejected` did not hold. MISSING telemetry is counted and excluded from both denominators; more than 20% MISSING among an arm’s successful starts makes both rates INCONCLUSIVE for that comparison. G7 compares the MULTI_SELECT three-correct-index share and both validator rates by absolute percentage-point differences, each within 10 points. Delivered ratio is delivered questions divided by 15 requested per successful start, compared within 2 points. G6 compares p50 latency and tokens per delivered question from successful starts within 15%, and clarity and faithfulness within 0.2. A note is paired only when both starts succeeded; fewer than 15 of 20 paired notes is INCONCLUSIVE. The missingness report lists every missing pair, paired and unpaired counts by note level and input stratum, and the proposed-minus-current failed-start difference. A comparison is INCONCLUSIVE if the proposed arm has at least two excess failed starts, either level has fewer than six paired notes or an input stratum has none, or validation-type failures dominate an arm's failed or MISSING-telemetry starts (more than half, with at least two). A note missing on both sides is reported and does not count toward the excess-failure or validation-correlation triggers, but it still leaves the paired set, so it counts toward the paired-level and input-stratum triggers. An unscored item or invalid score row makes its arm incompletely scored and its gates INCONCLUSIVE. The generation-count log has all-format rejection and retry counts, so G7 labels both telemetry rates **all-format**; it cannot isolate MULTI_SELECT rejections. Regenerate `descriptors.json` with the QualityDescriptors command above before each gate report, especially after updating result files or the descriptor calculator. Missing validator telemetry yields `MISSING`, never zero. After escalation, rerun the same gate command with the complete fresh review rows appended to `reviews-opus.jsonl` and disagreement adjudications in `adjudications.jsonl`.

An invalid primary score row is `FLAGGED` for independent review and recorded in the batch manifest's `invalidPrimaryIds`; the gate report marks its arm incompletely scored and its gates INCONCLUSIVE. A missing primary row still aborts batch construction.

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

Input is JSONL under `target/llm-benchmark/verifier/`, one case per line with unique filename-safe `id`, nonblank `noteText`, `summary`, `subject`, `learnerLevel`, and nonempty `keyConcepts`. Optional fields are `courseProgram`, `domainContext` (enum), `difficulty` (default `medium`), `disallowedQuestions` and `existingConcepts`. Leg R also needs a `rejected` JSON array containing exactly one object with `question`, `concept`, `statement1Correct` and `statement2Correct`; the question must contain both statement markers. Use the same ordered cases for Sol and Luna. Keep source notes and all exports git-ignored.

From `backend/`, after owner approval and with the local fixture and `LLM_API_KEY` set, run each model and leg with its actual input/output prices and a fresh output directory. The placeholders below must be filled from the approved Stage 2 run sheet; no default price or spending amount is assumed:

```sh
./mvnw test -Dtest=StatementPairStage2Benchmark -Dnativequery.pg.skip=true -Dllm.stage2=true -Dllm.stage2.leg=P -Dllm.stage2.model=<model> -Dllm.stage2.input=target/llm-benchmark/verifier/stage2-p.jsonl -Dllm.stage2.outDir=target/llm-benchmark/verifier/p-<model> -Dllm.stage2.batches=40 -Dllm.stage2.baselineBatches=10 -Dllm.stage2.maxCalls=200 -Dllm.stage2.priceInPerM=<price> -Dllm.stage2.priceOutPerM=<price> -Dllm.stage2.spendStopUsd=<approved-stop>
./mvnw test -Dtest=StatementPairStage2Benchmark -Dnativequery.pg.skip=true -Dllm.stage2=true -Dllm.stage2.leg=R -Dllm.stage2.model=<model> -Dllm.stage2.input=target/llm-benchmark/verifier/stage2-r.jsonl -Dllm.stage2.outDir=target/llm-benchmark/verifier/r-<model> -Dllm.stage2.batches=40 -Dllm.stage2.baselineBatches=0 -Dllm.stage2.maxCalls=80 -Dllm.stage2.priceInPerM=<price> -Dllm.stage2.priceOutPerM=<price> -Dllm.stage2.spendStopUsd=<approved-stop>
```

Offline safety and behavior tests:

```sh
./mvnw test -Dtest=StatementPairStage2BenchmarkTest,StatementPairProductionParityTest,StatementPairVerifierProductionTest -Dnativequery.pg.skip=true
```
