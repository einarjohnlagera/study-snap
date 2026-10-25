# B6 — targeted Official Challenge template coverage: proposal

**Status: PROPOSAL for owner approval. No paid generation and no production write is authorized.**
Written 2026-10-19 by Release Implementor for owner decision B6
(`docs/claude-plans/2026-10-19-owner-decisions-v0175-b1-b7.md`). Sources: read-only production `SELECT`s
(2026-10-19) and code on `releases/v0.174.0`.

## 1. Which missing sets are actually used (owner step 1)

**432 public Official Study Packs have no Challenge template** (out of 1,997). Learner usage of those packs,
through their adopted copies:

| | Packs |
|---|---|
| Adopted by at least one learner, ever | **252** (about 2,530 adoptions in total; the most for one pack is 51) |
| Adopted in the last 30 days | **210** |
| With any Challenge session on an adopted copy | 11 (28 sessions) |
| With any Quick Review session on an adopted copy | 32 (44 sessions) |

- **Adoption is concentrated:** the top 50 packs by adoption account for 56% of adoptions, and the top 100
  for **77%**.
- **The gap is not just new content.** The unseeded packs were created across every month from March to
  September 2026 (92 in May, 103 in June, 98 in September).
- **Why they have no template:** eligibility is only "public, Official author, the pack belongs to the
  note" (`OfficialChallengeQuizTemplateService.isEligibleOfficialTemplate`, `:332-357`). These packs are
  eligible.
  - Seeding runs once when a pack is generated or made public (`StudyPackService:797`, `NoteService:538`,
    `AdminStudyPackService:66`).
  - A failure is logged and swallowed (`seedTemplateAsync`, `:276-325`), with no retry.
  - So these are almost certainly failed seeds (today's model timed out in about 5% of benchmark and pool
    calls), or packs made public before the pipeline existed.

## 2. Does a template materially improve first-quiz readiness? (owner step 2)

**Yes, for single-note Challenge starts on adopted Official content; no for anything else.**
- A template holds 20 questions. A start asks for 10, 12 or 15.
- `copyTemplateQuestions` (`:195-235`) copies any of them that the learner's bank lacks. It applies no level
  or difficulty filter, so a learner's **first** single-note start on an adopted pack with a template is
  served entirely from the template: **no live generation, no wait.**
- **Multi-note starts never use templates** (`ChallengeQuizService:419-424`), so B6 does not help them.
- Since 2026-09-12, 3 of the 4 live Challenge starts were on adopted packs with no template
  (`docs/claude-findings/2026-10-17-v0174-llm-benchmark-results.md`, caveat 1, third read).
- **Benefit is real but modest at today's volume:** 28 Challenge sessions ever across these packs. It grows
  with Challenge use. It is a complement to v0.175.0's async preparation, not a substitute (owner B6).

## 3. Cost (owner step 3)

Per template: one 20-question `generateChallengeQuiz` call. **On Sol** (the curation model after the
v0.174.0 switch, owner A1), that is about $0.085 at list price. The billed figure is about 10–15% lower,
because of cached input (both benchmark reconciliations).

| Batch | Packs | Share of adoptions | Estimated cost (list) |
|---|---|---|---|
| **A. Top 50 by adoption (recommended first)** | 50 | 56% | **about $4.30** |
| B. Top 100 | 100 | 77% | about $8.50 |
| C. Every adopted pack | 252 | 100% | about $21 |
| D. All 432 | 432 | 100% plus unadopted | about $37 |

- **This spend is not seen by the $10 curation alert.** Seeding persists no tokens (owner A6). It is visible
  only on the OpenAI billing dashboard.
- **Timing matters:**
  - run before the v0.174.0 merge and switch, and it seeds on today's `gpt-4.1-mini`;
  - between the merge and the switch, on `gpt-4.1`;
  - after the switch, on Sol.

  **Recommended: after the switch**, so templates are Official-quality content on the curation model (A1).

## 4. Proposed batch (owner step 4)

- **Batch A: the top 50 unseeded Official packs by learner adoption**, after the v0.174.0 Render switch.
- **It needs a small code change, because there is no targeted path today.** `queueBackfill()` (`:101`)
  seeds **every** eligible unseeded pack at once, which would be batch D. Proposed:
  - an admin-only, explicit-list form, for example `queueBackfill(List<UUID> studyPackIds)` with a maximum
    size;
  - each id re-checked against `isEligibleOfficialTemplate` and the "no template yet" gate;
  - the existing pacing on `llmParallelTaskExecutor` kept.
  - It is a small backend item for v0.175.0, or its own small release. **It needs owner approval to build.**
- **Running it** (calling the admin endpoint with the 50 ids from a read-only selection query) **is an owner
  action**: it is paid generation and a production write.

## 5. Validation of quality, answer keys and completeness (owner step 5)

- **Automatic, already in the generation path:** structural validation and the answer/explanation
  consistency check (H4), with retry, run on every seeded question (`OpenAiLlmStudyPackService`). The
  per-call log line records the outcomes.
- **Completeness:** after the batch, a read-only `SELECT` confirms each of the 50 packs holds 20 template
  rows, and lists any that hold fewer (a seed failure, which can be retried individually).
- **Answer keys and quality:** a blind review of a sample.
  - 10 of the 50 packs (200 questions), scored by a cold Claude agent with no model labels, on the
    benchmark's rubric: factual, single best answer, distractors, explanation, faithfulness, plus a defect
    flag.
  - **It costs no OpenAI calls.**
  - **Acceptance:** zero answer-key defects among keys the agent can verify from the note. Any flagged item
    goes to the owner with a proposed repair; there is no automatic rewrite.

## 6. Owner decisions needed

1. **Approve building the targeted backfill path** (step 4), in v0.175.0 or separately.
2. **Approve batch A** (top 50, about $4.30, after the v0.174.0 switch), or choose another batch size.
3. **Confirm the timing:** after the Render switch (Sol), not before.
