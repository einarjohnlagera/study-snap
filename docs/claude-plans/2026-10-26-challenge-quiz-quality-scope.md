# Challenge Quiz quality: v0.176.0 release scope (Phase 1)

Status: **SCOPE PROPOSAL for the next release, with Product UX's final review applied (section 7).** Written 2026-10-26, revised twice on 2026-10-27 by the Release Implementor. It
implements Feature Planner's Phase 1 proposal (`docs/claude-plans/2026-10-26-challenge-quiz-quality-phase1-proposal.md`,
uncommitted in the shared checkout), which records the owner's approval on 2026-10-27 of: (1) the exact prompt wording,
(2) the bounded evaluation with a $4 generation cap, run by the owner, (3) the reframed success criterion. **I have not seen
the owner's own words, only that record.** Where this document and Feature Planner's differ, Feature Planner's approved
wording and criteria win; the sizing and sequencing below are mine.

**Not approved, still gated:** shipping any prompt change before the evaluation passes; any paid call by an agent; Phase 2
(outcome-aware bank selection) and Phase 3 (per-question reporting) behaviour; Board Exam, Long Exam, pricing and quota
(untouched). The Board/Long Exam follow-up scope is deferred by the owner.

## 1. The learner problem (unchanged)

A `hard` 15-question Challenge on a private note scored 93% in about 3 minutes with "easily spotted" distractors. All 15
questions were generated live on the pinned `gpt-4.1-mini`, before v0.174.0 and the v0.175.0 routing. Quality enhancement,
not a correctness defect.

## 2. Re-verified facts (this session, `main` `06a09f7c`, production read-only)

| Fact | Evidence |
|---|---|
| The Challenge prompt has no distractor rule and a bare difficulty word. | `challenge-quiz-developer.txt:6` `Difficulty target: {DIFFICULTY}.`; Study Pack (`developer.txt:116`) and Board Exam (`board-exam-developer.txt:14`) carry the wording |
| The template is read in one place, so the blast radius is Challenge only. | `OpenAiLlmConfig.java:48` loads it, `OpenAiLlmStudyPackService.java:1018` is the only reader (start, +5, multi-note, Official template seeding); Board and Long Exam load their own templates |
| The bank has **no difficulty column**; template stock is fixed `medium`. | schema read; `OfficialChallengeQuizTemplateService.java:53` |
| **Correction to my earlier draft:** the 31,886 unanswered bank rows are **not** learner "template copies". | `challenge_quiz_question_bank` joined to `users`: 31,791 `UNANSWERED` rows belong to **one ADMIN user** (the Official template source rows); learner-owned stock is **692 rows** (`USER`: 440 correct, 157 incorrect, 95 unanswered). Feature Planner had this right |
| Hard vs medium at the top end. | completed Challenge sessions of 5 or more questions: `hard` n=87, median 73.3%, 17 at or above 90%; `medium` n=114, median 60.0%, 20 at or above 90%; confounded (`hard` only follows a Quick Review of 80% or above) |
| Difficulty touches only the prompt word and the 10/12/15 count. | `resolveGenerationProfile`; the label is stored in `session_state` and analytics, and no claim, timer or scoring code reads it |
| No Challenge session exists since v0.175.0 went live (2026-10-26T14:58:47Z). | production read |
| **A trap in the approved reword (corrected 2026-10-27 by Feature Planner; I first misread it as a test fixture).** The +5 exclusion prompt is pinned byte for byte **to the owner-signed wording document**. | `StatementPairProductionParityTest.java:44-45` asserts `challenge-more-no-statement-pair-developer.txt` equals the "## Part 1. The +5 rule" block of `docs/claude-plans/2026-10-20-v0175-statement-pair-verifier-wording-and-pass-rules.md` (line 26). Rewording it means editing the **signed document** in the same commit or relaxing the test. The `STATEMENT_PAIR_PLUS5_EXCLUSION_ENABLED` switch is OFF, so the file is never sent in production |
| Unit tests do not assert the real prompt text. | `OpenAiLlmStudyPackServiceTest:141` stubs a fake template (`"Challenge quiz developer prompt for {QU…"`); `{DIFFICULTY}` is still substituted harmlessly at `OpenAiLlmStudyPackService.java:1020` |

## 3. Release shape

One release, `v0.176.0`, cut from `main` after the docs carry-in (`#1534`, `#1535` applied inside the kickoff commit). **One
deploy at the end, because every `main` merge deploys** (and every deploy since 2026-10-25 was followed by a Postgres
signal-9 kill, 4 of 4). So the evaluation harness lives on the release branch and runs from there; nothing is merged to `main`
early.

| # | Item | Tier | Gate |
|---|---|---|---|
| 1 | **Evaluation harness** (test scope): load an alternate prompt directory for the proposed arms (`OpenAiLlmConfig.java:40` reads `promptDir + "/…"` from `properties.getSettings().getPromptDir()`, so the arms can point at a copied directory), the five arms (A1 mini, A2 Luna, A3 Sol on the current prompt; B2 Luna, B3 Sol on the proposed one), the stock arm S (about 100 Official template questions by read-only SELECT), blind export reusing `BlindExport`, per-arm prompt hash. Codex builds it; no production code. | N | none |
| 2 | **Evaluation run**, **owner-run, $4 generation cap**, **20 Official notes, 10 `COLLEGE` + 10 `BOARD_EXAM_REVIEW` by the note's own level (owner decision, design A, 2026-10-27; gates overall, per level descriptive only)**; blind Codex scoring (D11), Opus adjudication of a 20% sample and every critical flag. Output: a findings doc and a recommendation, **no production change**. | n/a | owner runs; criterion 1 as approved (no more critical defects than the same-model current arm, zero attributable to the new wording) plus Feature Planner's criteria 2 to 6 |
| 3 | **Prompt change**, only if item 2 passes: **the single file `challenge-quiz-developer.txt`** (Changes A, B, C as approved); the Java `{DIFFICULTY}` substitution stays. **The one-sentence reword in `challenge-more-no-statement-pair-developer.txt` is HELD** (Feature Planner's recommendation, **pending your confirmation**): it would need the signed document edited or the parity test relaxed, and the file is never sent while the +5 switch is off, so it should wait until the switch is enabled and you re-sign it. Until then that file still says "keep the same difficulty target", which is harmless because the target no longer appears in the prompt. Update `docs/features/challenge-quiz.md:238` (the difficulty mapping) and the loading line "Balancing difficulty…" (`quiz-generation-overlay.tsx:9`) under `VOICE_AND_TONE.md`. | N code, quality gated by item 2 | item 2 passes **and** the owner confirms the final text |
| 4 | **Measurement log** (Phase 2 prerequisite, small Java): add outcome counts and the model to `challenge_quiz_delivery_counts`, so repeat-question rates and which model served a start become readable. Also repairs the weak spot in the 2026-11-09 Challenge checkpoint (`model_used` is null for bank-served starts). | N | none |
| 5 | **Checkpoint rows** in the Backlog Index: a 14-day read after the deploy (retry and rejection rates from `challenge_quiz_generation_counts`, delivery counts, new feedback volume), and the Phase 2 and 3 candidates as indexed rows. | docs | none |

**If item 2 fails, is incomplete or is inconclusive: HOLD (Product UX, 2026-10-27).** The prompt does not ship, and **v0.176.0 is not deployed
merely to ship the harness and the measurement log**; any measurement-only deployment needs separate owner approval. The findings are preserved and
the next step is a targeted wording revision. A pass for one model never overrides a failure for the other.

**Verification tier:** about 4 to 5 items, but the risky one (the prompt) is gated by an evaluation, not by code review. One
`advisor()` before the harness brief and again on the diff, plus a real-PostgreSQL or log-line test for item 4. A scoped
cold agent is **not** needed unless item 3 or 4 touches a shared method beyond the log line.

## 4. Owner actions

1. Run the evaluation commands (I write them, one model per command, key in your shell only, never in a prompt or file);
   expect about $2.8 of generation under the $4 cap (R1 reference rates per delivered question: mini about $0.0005, Luna
   about $0.0003, Sol about $0.0042).
2. Confirm the final prompt text after item 2, then the release PR.
3. Redeploy at the quietest hour; nothing else deployed that hour.

## 5. Constraints that stay binding

No pricing, entitlement or quota change; customer copy never names a model and implies no plan quality difference; no
prompt or schema change beyond the approved wording; R1, the verifier switches, preparation, +5 and the A.3 filter are
untouched; Board and Long Exam untouched; never call the Render env-var update; real-request test for any new endpoint and a
`PREPARE`d native query for any new SQL (none planned). The v0.175.0 and v0.174.0 checkpoint dates and the
`CHALLENGE_QUIZ_STATEMENT_PAIR_LEGACY_CUTOFF` value (`2026-10-26T14:58:47Z`) stand.

## 6. Not in scope

Phase 2 behaviour (outcome-aware claim ordering; the measurement log in item 4 comes first), the difficulty-aware bank (needs
a migration), Phase 3 per-question reporting, retiring `resolveGenerationProfile` or the stored label, the "Adjust
difficulty level" control (it is the curriculum-level override, no change), Board and Long Exam, pin retirement.

## 7. Product UX final review, applied (2026-10-27)

Overall: **conditionally approved**. Direction unchanged (small, newly generated questions only). Refinements:

**7.1 Finalized evaluation gates** (Luna and Sol are judged **separately**; **both** must pass before a global prompt change; "pass" below means
the proposed-prompt arm against the same-model current-prompt arm on the same 20 notes). Relative to Feature Planner's six
provisional criteria: **criterion 1 (the only one explicitly owner-approved) is kept as written (G1); criteria 2, 5 and 6 are kept unchanged (G2, G5, G6);
only criteria 3 (cognitive demand) and 4 (recurring pattern) are replaced (G3, G4);** G7 and G8 are new.

| Gate | Rule |
|---|---|
| **G1 Correctness** (owner-approved) | Confirmed critical defects (wrong key, more than one defensible answer, contradiction with the source) are **no more than** the same-model current arm, and **zero are attributable to the new wording**. Listed individually, never averaged. A high mean never hides one. Disputed correctness and ambiguity findings are adjudicated independently. |
| **G2 Distractors** | Mean plausibility **at least 4.0** and **at least +0.3** over the same-model current arm (existing absolute and comparative targets kept). |
| **G3 Mode alignment** (new; replaces "cognitive demand 4.0 on the multi-step anchor") | Mean **at least 4.0 of 5**: is the question appropriately challenging and fair for the source? **Cognitive demand is reported separately as a descriptive** (what reasoning is needed); **no requirement that every question be multi-step.** The anchor scale is written before scoring. |
| **G4 Giveaways** | Mechanical indicators (correct choice longest, absolutes only in wrong choices, position skew, near-duplicates) are **diagnostic only, never gates**. Gate on **independently confirmed serious giveaways: at most 5% of reviewed choice-based items, and no recurring systematic pattern.** Near-duplicate questions in fewer than 10% of starts (kept). |
| **G5 Formats** | No format below 3.5 on any dimension with at least 20 items (kept). |
| **G6 Collateral** | Clarity and faithfulness within 0.2; delivered count and validation-retry rate within 2 points; p50 latency and tokens per question within 15% (kept). |
| **G7 MULTI_SELECT** (new) | Cardinality and scoring semantics unchanged: the 2-or-3 correct-index split and the multi-select validator-rejection rate match the same-model current arm within noise, and **no Java is touched** (the validator is `QuizValidationUtils.hasInvalidCorrectIndices`, 2 to 3 correct indices). A prompt-contract test pins the MULTI_SELECT cardinality sentence so Change B cannot overwrite it. |
| **G8 Inconclusive** | Any gate the sample cannot support is **INCONCLUSIVE, which counts as not passing**, never an automatic pass. |

**Feasibility, stated before the run (as Product UX asked).** About 300 questions per arm gives roughly 200 choice-based items. **G4's 5% cannot be
*proven* at that size:** an observed 0 confirmed giveaways has a 95% upper bound of about 1.5 to 1.8%, but an observed 5% (10 of 200) has an upper bound near 9%.
So G4 is applied to the **observed** rate with the interval reported, and a stratum with **fewer than 150 reviewed choice-based items is
INCONCLUSIVE**. That is the smallest defensible adjustment; it is fixed **now**, not after the results. Per-model effects smaller than 0.3 on the 1 to 5
scales are not claimed (20 paired notes).

**7.2 Review protocol (tightened).** Independent Opus review of the stratified random sample **including items the primary scorer did not flag**,
**against the actual source notes**; **report critical defects the primary scorer missed**, not only confirmed flags. *Proposed, not yet approved:* if the
sample finds any missed critical defect in a proposed arm, that whole arm is independently re-scored (Opus passes, no API dollars). Representative
before-and-after examples for improvements **and** failures. Artifacts private, git-ignored, **Official notes only, no learner content**. No further paid
call without approval.

**7.3 Measurement log (item 4) safeguards.** Log **prior bank outcomes at delivery time**, clearly separate from the current attempt's results; use **actual
delivered counts**; distinguish **fresh generation from bank or template reuse**; keep **unknown model provenance as unknown**, never inferred; **no question
text, private-note content or unnecessary learner identifiers**; the logging must not affect quiz correctness, availability or quota (a failure to log is
swallowed); a real-database or log-contract test.

**7.4 +5.** The reword in `challenge-more-no-statement-pair-developer.txt` is **deferred** (confirmed by Product UX); the signed source document and the
parity test are untouched and the exclusion switch stays OFF. **Verify with a test that a normal +5 still uses the updated Challenge prompt:** `generateMoreChallengeQuiz`
reaches the same `challenge-quiz-developer.txt` template through `buildChallengeQuizInputMessages`.

**7.5 Operational deployment gate (new).** The four signal-9 kills that followed four deploys must be investigated before any production approval:
assess the cause and the current database health (the 2026-11-01 cache read is the first input), confirm capacity and pre-deploy health checks,
document rollback and recovery, and define post-deploy checks of the core quiz and study flows. **One deploy reduces exposure but does not resolve the
risk.** If the risk stays unexplained with no credible recovery plan: **HOLD**.

**7.6 Unchanged scope.** No learner-facing difficulty selection; no change to the 10/12/15 counts; no Phase 2 bank selection; no Phase 3 reporting; no mass
regeneration or bank migration; no Board or Long Exam change; no pricing, quota or entitlement change; **no production prompt change before the
evaluation and final approval; this review is not a deployment authorization.**

## 8. Release readiness checklist

- [ ] Owner confirms: the +5 reword deferral, gates G1 to G8 above (only Feature Planner's provisional criteria 3 and 4 are replaced), and HOLD-by-default.
- [ ] Release branch kicked off (docs carry-in `#1534`/`#1535`); **no merge to `main` before the final deploy decision.**
- [ ] Harness built and tested: alternate prompt directory, five arms plus Official stock arm, per-arm prompt hash, blind export with leak check.
- [ ] 20 Official notes chosen by `sample-query-quality.sql` (10 `COLLEGE` + 10 `BOARD_EXAM_REVIEW`; each level at least 4 strata and 2 quantitative; no learner content); pre-registration (pack ids, input SHA-256, rubric hash) **committed to the release branch before the first paid command**; rubric and anchor scales frozen first, with G4's denominator pinned to every scored choice item and "grounded in the notes" defined.
- [ ] Owner runs the paid generation under the **$4 cap**, key only in the owner's shell.
- [ ] Blind Codex scoring of all items; Opus review per 7.2; disputes adjudicated; per-model verdicts (Luna and Sol separately); INCONCLUSIVE rule applied.
- [ ] Findings doc with before-and-after examples and every critical defect root-caused; recommendation made to the owner.
- [ ] If and only if **both** models pass: owner confirms the final text of `challenge-quiz-developer.txt`; prompt change plus prompt-contract tests (MULTI_SELECT sentence, +5 uses the template).
- [ ] Measurement log (7.3) with its test; docs and the "Balancing difficulty…" line updated under `VOICE_AND_TONE.md`.
- [ ] **Operational gate (7.5) cleared:** DB health and the 2026-11-01 read assessed, capacity and pre-deploy checks confirmed, rollback and recovery documented, post-deploy flow checks defined.
- [ ] Owner's explicit deployment approval (not granted by any review so far); one deploy at the quietest hour.
