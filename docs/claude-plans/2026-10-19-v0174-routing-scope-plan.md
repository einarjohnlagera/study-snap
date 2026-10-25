# v0.174.0 — routing scope, temporary Challenge routing, monitoring and rollback (owner review)

**Status: DRAFT for owner review — the v0.174.0 half of the consolidated plan.** Not implementation and
not deployment. Written 2026-10-19 by Release Implementor, against the owner's decisions of 2026-10-19
(`docs/claude-plans/2026-10-19-owner-decisions-v0174-split-and-challenge-preparation-ux.md`). Feature
Planner is writing the v0.175.0 half.

The two halves are consolidated in
`docs/claude-plans/2026-10-19-v0174-v0175-consolidated-implementation-plan.md`.

**How each claim is labelled:**
- **[FINDING]**: measured in a completed benchmark, or read from code or production. Cited.
- **[DECIDED]**: the owner decided it, quoted from the decisions file.
- **[PROPOSED]**: this plan's proposal, awaiting owner approval.

## 1. Why the two existing config values cannot do this alone

- **[FINDING]** Every quiz method resolves `LLM_MODEL_FREE` (`OpenAiLlmStudyPackService:346`, called at
  `:231,251,288,1793,2199,2273`). Setting it to Luna would move **Challenge start, "+5", multi-note,
  Adaptive, Board/Long Exam and the teacher quiz to Luna for every plan**.
  - That is the Challenge latency regression the owner ruled out for v0.174.0. Luna's 15-question start
    is p50 49 s in the harness against 13 s for mini.
- **[FINDING]** Interview Practice (`:1860`) and the companion (`:273`) resolve `LLM_MODEL_PREMIUM`.
  Setting it to Sol would move both, unbenchmarked. Both are paid, and the learner waits on them.
- **So keeping a path as it is today requires an explicit pin: a new config key.**

## 2. Foreground or background: which paths a learner waits on

**[FINDING]** Each row was read at its call site.
- "Foreground" means the call runs inside the request, with no dispatcher between the controller and the
  model call.
- Latencies are from the first benchmark (p50 / max, harness only).

| Path | Call site | Foreground or background | mini | Luna | Sol |
|---|---|---|---|---|---|
| Study Pack, async from a note | `StudyPackService:811` (`generateStudyPackFromExistingNoteAsync`) | background | 7.8 / 13.2 s | 14.8 / 20.6 s | 27.3 / 34.4 s |
| **Study Pack, synchronous**: create from text, from image, confirm text | `StudyPackService:128,359,424`; **live, called by `lib/api.ts:3698,3822,3912`** | **foreground** | same | same | same |
| Summary regeneration (admin) | `AdminStudyPackTransactionHelper:68`, run from `AdminStudyPackService.regenerateOfficialSummaries` (`:53-70`) on `llmParallelTaskExecutor` | background | — | — | — |
| **Malformed-quiz repair (admin)**, added after the Opus check | `AdminStudyPackTransactionHelper:138` via `AdminStudyPackService.repairMalformedQuizzes` (`:80-97`), `llmParallelTaskExecutor`. It runs over **every owner's** malformed packs and rewrites only `quiz` (`:139`) | background | | | |
| Note from topic, single | `NoteGenerationService:92` | foreground | 3.9 / 4.8 s | 10.0 / 12.5 s | 19.7 / 28.3 s |
| Note from topic, bulk (admin) | `NoteBulkGenerationService:439` | background | | | |
| Official template seed | `OfficialChallengeQuizTemplateService:276` | background | | | |
| Challenge start, single and multi-note | `ChallengeQuizService:221` (`startSession`) | **foreground** | 13 s (15 q) | 49 s | 107 s |
| Challenge "+5" | `ChallengeQuizService:1105` | **foreground** | | | |
| Adaptive Practice | `QuickReviewAdaptivePracticeService:161,494` | **foreground** | 5.7 / 6.7 s | 25.7 / 36.0 s | 40.4 / 44.3 s |
| Quick Review study tip | `QuickReviewStudyTipService:31` | **foreground** | | | |
| Teacher quiz | `GeneratedQuizService:94` | **foreground** | | | |
| Board Exam session and pool; Long Exam session and pool | `ChallengeQuizService:583`, `LongExamService:293`, `ExamQuestionPoolService:147` | background (no learner has ever used either) | | | |
| Interview Practice | `InterviewPracticeService:674` | foreground | | | |
| Companion | `NoteCollectionService:1015` | foreground | | | |
| **Ask Companion**, added after the Opus check | `AskCompanionService:147` → `OpenAiAskCompanionLlmService:80`, which reads **`LLM_MODEL_CRITIQUE`** | foreground | | | |
| Interview critique | `OpenAiLlmStudyPackService:1880`, `LLM_MODEL_CRITIQUE` | foreground | | | |

A cold Opus falsification pass on 2026-10-19 confirmed every other call site and every foreground/background
classification above, against the code.

## 3. Proposed v0.174.0 routing

FREE = the FREE plan; Paid = Plus or Pro (`SubscriptionService.resolvePlan`, expiry-aware, `:65-85`);
ADMIN = `UserRole.ADMIN` (official curation, regardless of plan).

| Path | Config key | FREE | Paid | ADMIN | v0.175.0 target |
|---|---|---|---|---|---|
| Study Pack: async **and synchronous** routes | `LLM_MODEL_FREE` / `LLM_MODEL_PREMIUM`, routed by tier | **Luna** | **Sol** | **Sol** | unchanged |
| Summary regeneration (admin) | routed by tier | — | — | **Sol** | unchanged |
| Malformed-quiz repair (admin, over every owner's packs) | **[PROPOSED] the pack owner's tier** (owner decision; see §5) | Luna | Sol | Sol | unchanged |
| Note from topic, single and bulk | routed by tier | Luna | Sol | Sol | unchanged |
| Official template seed | routed by tier (owner = Official author) | — | — | **Sol** | unchanged |
| **Challenge start, "+5", multi-note** | **new `LLM_MODEL_INTERACTIVE_QUIZ`, pinned to `gpt-4.1-mini`** | mini | mini | mini | tier routing, once v0.175.0 validates it |
| Adaptive Practice, study tip, teacher quiz | `LLM_MODEL_INTERACTIVE_QUIZ` (pin) | mini | mini | mini | evaluated in v0.175.0 |
| Board Exam and Long Exam, sessions and pools | `LLM_MODEL_INTERACTIVE_QUIZ` (pin) | mini | mini | mini | evaluated with their pre-screen in v0.175.0 (owner Set 2 §6) |
| Interview Practice, companion | **new `LLM_MODEL_PAID_INTERACTIVE`, pinned to `gpt-4.1`** | n/a (paid-gated) | gpt-4.1 | gpt-4.1 | evaluated in v0.175.0 |
| Interview critique **and Ask Companion** | `LLM_MODEL_CRITIQUE`, unchanged (owner §9) | | | | its own assessment, before 2027-01-14 |

**[PROPOSED] Why this shape:**
- Every path a learner waits on, other than Study Pack and note generation, stays on the model it uses
  today. **Nothing gets slower, and no paid user gets a lower-tier model than they get today.** The two new
  keys are pins, not substitutions.
- v0.174.0's routing work (item 2) shrinks to the Study Pack family plus the template seed: about 5 caller
  services instead of 11. That keeps the release small and its verification proportionate.
- **Each key is a separate rollback lever:** an env-var change on Render, which is an owner action.
- **[FINDING] Design constraint for item 2's brief: the model must be chosen by the caller, not by the
  method.** Challenge start, "+5" and the Official template seed all go through
  `generateChallengeQuizWithSchema`, which picks its model at `OpenAiLlmStudyPackService:2273`; the seed
  enters at `OfficialChallengeQuizTemplateService:300`. `generateStudyPack` is likewise shared by learner
  routes and the admin repair. So each `LlmStudyPackService` method takes the resolved model (or a routing
  input) from its caller, and the resolver lives in one place.
- **[FINDING] Sol-written questions will reach every plan through templates.** Seeded template questions
  are copied into learners' Challenge starts and "+5" (`ChallengeQuizService:434-442,1151-1159`). So "Challenge
  stays on mini" is true of **live** generation only. A FREE learner can answer Sol-written Official
  template questions.
  - This is curation by design: Official content is Sol (owner decision). It is not a tier claim.
  - The session's `model_used` does not show it.

## 4. The customer-claim check

**[FINDING]** The plan copy (`frontend/src/config/plans.ts:47-138,262`) promises **quantities only**:
Study Packs, Challenge quizzes per month, share links, 20- and 30-question teacher quizzes. A copy grep found
no claim about model, quality, speed or "enhanced" generation in `app/`, `components/`, `src/` or `lib/`.
- **[DECIDED]** No announcement in v0.174.0. Any future copy says "Enhanced Study Pack Generation".
- **[PROPOSED] So the pinned routing conflicts with no customer-facing claim.** "Enhanced" will describe
  Study Pack generation, which is exactly what moves to Sol in v0.174.0. Quiz generation is not claimed.
- **Constraint for later:** if marketing ever extends "Enhanced" to quizzes, that copy must wait for
  v0.175.0.

## 5. Conflicts and decisions for the owner

1. **"Correct and verify the relevant model-routing defects"** (Set 1 §3). Under the pin, the Challenge and
   exam routing defect (every plan gets the same model) **stays unfixed until v0.175.0**: all plans get mini
   there, as today. **[PROPOSED]** Read "relevant" as the Study Pack family. That is the only reading
   compatible with "no unvalidated Challenge latency regression" in the same list.
2. **Interview Practice and the companion:** pin to `gpt-4.1` (proposed), or move them to Sol unbenchmarked?
   Both are paid and foreground.
3. **The synchronous Study Pack routes on Sol:** p50 27 s / max 34 s for the model alone, plus production
   overhead. The p50 is under the 60 s maximum; the overhead pushes it over the 30 s target, which was set
   for Challenge. How often these routes are used is not measured here. **Accept for v0.174.0, or route
   them async in v0.175.0?**
4. **The Long Exam pool reduction to 30** was decided to give Sol headroom. With Long Exam pinned to mini it
   is unnecessary in v0.174.0, and it ends pool reuse. **[PROPOSED]** Move it to v0.175.0, alongside the
   decision to route Long Exam to Sol.
5. **The pin deadline:** `gpt-4.1` and `gpt-4.1-mini` shut down on **2027-01-14**
   (`docs/claude-plans/2026-10-16-llm-model-migration-benchmark-design.md:19`). **v0.175.0 must ship before
   then**, or the pins must move to Luna, which is a latency change the owner would decide then. About 12
   weeks remain.
6. **Malformed-quiz repair:** it runs over every owner's packs, with no request user. **[PROPOSED]** Route
   by the pack owner's tier, so a Plus/Pro pack is repaired on Sol and a FREE pack on Luna. The alternative
   is a fixed model for the job. It also leaves `model_used` / `model_tier` stale, because it rewrites only
   `quiz`. Record that as known.
7. **The study tip:** the earlier routing matrix routed it by plan. Under the pin it stays on mini for
   everyone. That is not a downgrade (it is today's behaviour), but it reverses the matrix. Confirm.

## 6. Monitoring and rollback (deliverable 8)

**[FINDING] What exists (corrected after the Opus check):**
- `model_used` and `model_tier` are on Study Packs (`StudyPackService:721-723`).
- **`model_used` and input/output/cached token counts are also on Challenge sessions**
  (`QuickReviewSessionEntity:95-105`, migration `V98`), filled by `accumulateLlmUsage`
  (`ChallengeQuizService:2501-2516`) for single-note start and "+5". Limits:
  - it is per session, not per call, and first-write-wins (`:2508`);
  - **multi-note start discards usage** (`generateChallengeQuizForSources` returns only questions);
  - Board Exam, Long Exam, Adaptive, the teacher quiz, the study tip and Interview record nothing.
- `model_tier` is on Study Packs only.
- Failures are logged as warnings (`openai_unavailable`, `openai_quiz_unavailable`).
- `extractUsageMetadata` does not record reasoning tokens (RELEASES item 1, part 1 findings).

**[PROPOSED] Required in v0.174.0, as part of item 2:**
- **One structured log line per LLM call:** operation, config key, model, plan tier, `UserRole.ADMIN` flag,
  latency, attempts, outcome class (success, HTTP error, timeout, schema/parse), and token usage including
  `reasoning_tokens`.
  - This gives success and failure rates, parse failures, latency by workflow and model, and timeout and
    retry counts from Render logs, with no schema change.
- **Curation cost monitor (item 4):** a scheduled job sums the **persisted** tokens on ADMIN-owned Study
  Packs (`input_tokens`, `output_tokens`, `cached_input_tokens`) with a cached-input discount, not list
  price. It cannot read logs.
  - Spend on template seeds, summary regeneration and repairs persists no tokens, so it is visible on the
    billing dashboard only (consolidated plan, Part A4). List overstates Sol, as shown by both benchmarks' billed reconciliation. **The owner still
  owes the monthly alert threshold.**
- **Fix the `model_used` fallback.** `StudyPackService:722-723` falls back to `getModelFree()` when a
  response lacks `model`. Under tier routing, that would label a paid Sol pack as the free model. Fall back
  to the model actually sent.
- **Rollback:**
  - Each of the four keys is an env var. Reverting `LLM_MODEL_FREE` / `LLM_MODEL_PREMIUM` to
    `gpt-4.1-mini` / `gpt-4.1` restores today's behaviour for the tier-routed paths. This is true **only
    because** every quiz path reads the new pin key once item 2 lands; today `LLM_MODEL_FREE` drives them
    all.
  - No migration and no data rewrite. Existing Study Packs keep their `model_used`, and question banks,
    attempts and progress are untouched.
  - Env-var changes redeploy, so they fall under the same merge gate. **Owner action.**

## 7. v0.174.0 acceptance criteria (for item 2's brief and the pre-signoff check)

1. A per-path test asserts the **model actually sent** for each of FREE, Plus, Pro and ADMIN-on-a-FREE-plan,
   on every row of §3: by tier on the Study Pack family; the pinned model on every pinned path.
   - It also asserts a lapsed paid subscription routes as FREE.
   - These tests go through `SubscriptionService`, not a raw plan field.
2. The template seed sends Sol. The test fails if seeding falls back to the free model.
3. No pinned path changes model when `LLM_MODEL_FREE` or `LLM_MODEL_PREMIUM` changes.
4. The structured log line is emitted on success, HTTP error, timeout and parse failure, with reasoning
   tokens. A test pins its fields.
5. Existing Study Packs, banks and sessions are untouched: no migration, and no write to historical
   `model_tier` / `model_used`.
6. `./mvnw clean install` with Docker passes. The required pre-signoff tier is the scoped cold falsification
   pass (Tier H).

## 8. Dependencies

- **Merge gate (unchanged):** the merge to `main` and the Render env-var change wait for #1505 to merge
  (after its `[CHECKPOINT — due 2026-10-22]` signal-9 read) **and** the cache deploy + 7 days read.
- **RC-3 checkpoint:** due 2026-10-21, inside this release.
- **Item 4:** the owner's monthly threshold.
- **The pin deadline of 2027-01-14 bounds v0.175.0.**
