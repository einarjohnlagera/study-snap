# Challenge Quiz quality evaluation: scoring rubric

Status: **v3, incorporating Product UX's three decisions and its four final sign-offs of 2026-10-27 (all relayed by the owner). Not frozen until the owner signs it and its SHA-256 is recorded in the
pre-registration file (`docs/claude-plans/2026-10-27-challenge-quiz-quality-preregistration.md`) before the first paid command.** Written by the Release Implementor from the scope
(`2026-10-26-challenge-quiz-quality-scope.md` §7), Product UX's final review and decisions, and Feature Planner's Phase 1 proposal §11. Nothing here changes after the first paid command; a
mistake found later is reported as a protocol deviation, never fixed silently. **Settled and not reopened:** gates G1 to G8, the 20-note design A, the $4 owner-run cap, default HOLD. **Signed off by Product UX on 2026-10-27 (relayed by the owner): the G4 parameters P1 to P5 (section 5, P3 raised to 60), the wording-sensitive regression rule (section 2) and G7's 10-percentage-point tolerance. The denominators and missing-data handling in section 5 were written by the Release Implementor on request and are not separately signed.**

## 1. What is scored

About 1,600 items: five generation arms (A1 mini, A2 Luna, A3 Sol on the current prompt; B2 Luna, B3 Sol on the proposed prompt), about 300 questions each from the same 20 Official notes
(10 `COLLEGE` + 10 `BOARD_EXAM_REVIEW`, hard, 15 per start), plus about 100 stored Official template questions. Scorers see **only** the blind item (`question, choices, questionFormat,
keyedAnswer, explanation`, a pseudonymous `noteRef`) and the per-note file. They never see an arm, model, prompt hash, position or `generatedAt`. **Scores are written and frozen before the key is opened.**

## 2. Source grounding and factual correctness are separate judgements (Product UX Decision 1)

The generator never sees the full note: it sees the Study Pack **summary and key concepts**, the learner context and the exclusions (`buildChallengeQuizInputMessages`). The per-note file therefore carries the generator's
inputs (summary, key concepts), the full note text and the note's level. Two independent fields per item:

- **`factuallyCorrect`**: yes, no, or unverifiable (judged against the full note and settled fact).
- **`grounding`**: one of
  - **GROUNDED**: supported by the summary and key concepts **and** consistent with the full note;
  - **UNSUPPORTED**: true in the full note but absent from the generator inputs, **or** unverifiable from the available material. **Non-critical, counted against the Faithfulness score**; an unverifiable statement is also
    investigated, and is **not** automatically a confirmed critical defect;
  - **CONTRADICTED**: contradicted by the generator inputs **or** by the full note. This is a **critical learner-facing defect**.
- **Root cause is recorded for every critical defect and kept distinct where the sources themselves conflict.** Reviewers are **blind to the arm**, so they assign a **defect type** from a fixed list, never "caused by the new wording":
  `WRONG_KEY`, `EXPLANATION_CONTRADICTS_KEY`, `CONTRADICTS_GENERATOR_INPUTS`, `CONTRADICTS_FULL_NOTE`, `SOURCE_CONFLICT` (the summary or key concepts disagree with the full note), `PARTLY_CORRECT_DISTRACTOR`, `SECOND_DEFENSIBLE_ANSWER`, `AMBIGUOUS_STEM`,
  `COMPLEXITY_UNSUPPORTED_BY_INPUTS`, `OTHER`.
- **The wording-sensitive regression rule (signed off; a mechanical screen, not proof of individual causal attribution).** The types the new wording could plausibly cause are the **wording-sensitive types**: `PARTLY_CORRECT_DISTRACTOR`, `SECOND_DEFENSIBLE_ANSWER`, `AMBIGUOUS_STEM`, `COMPLEXITY_UNSUPPORTED_BY_INPUTS`
  (Change B's "defensibly incorrect" and parallel-choice rules; Change A's "do not invent complexity"). After unblinding, for each wording-sensitive type `T` and each proposed arm (B2, B3), the defects **counted as a wording-sensitive regression** are the **excess over the same-model current arm**:
  `max(0, count_B(T) − count_A(T))`. Every other type is never attributed to the wording by default; it still counts under "no more than the same-model current arm". The calculator computes this; nobody decides it after seeing results. **All confirmed critical defects are reported individually regardless of category, G1's overall no-regression requirement is preserved, and no individual wrong key is dismissed merely because the baseline arm also has defects.**
*Harness addition required:* `notes.jsonl` must also carry `studyPackSummary` and `keyConcepts`.

## 3. Item-level judgements

The primary scorer (Codex) records every field. Choice-based = standard MCQ, MULTI_SELECT, assertion items and MATCHING items; True/False and the free-text formats are scored on correctness, grounding and clarity only.

**Critical defect flag (G1), yes or no, with a reason and a root cause.** Yes if **any** of:
1. the keyed answer is wrong (`factuallyCorrect = no` for the keyed answer);
2. the keyed answer is not the uniquely defensible answer or complete correct-answer set required by the question format;
3. the explanation contradicts the keyed answer, or the question or explanation is **CONTRADICTED** per section 2.
A critical flag is never averaged away.

**Classification order (Product UX final review, 2026-10-27):** a question must **first satisfy the critical-defect definition above**; only then is a `criticalType` assigned. The type never makes an item critical by itself.
- **Unsupported complexity alone is not critical.** `COMPLEXITY_UNSUPPORTED_BY_INPUTS` applies only when the added complexity also makes the keyed answer wrong, not uniquely defensible, or contradicted; otherwise the item is non-critical and the cost shows in `grounding` (`UNSUPPORTED`) and `faithfulness`.
- **An ambiguous stem is critical only when the ambiguity compromises the defensibility or uniqueness of the keyed answer.** A clumsy but answerable stem is a `clarity` matter.
- **Format-specific correctness.** Single-select needs exactly one defensible answer. MULTI_SELECT requires the **complete intended correct set**: several correct options are not a defect in themselves, a missing or extra correct option is. Assertion-style and matching items follow their own rules (every assertion or pair judged on its own; the keyed combination must be the only defensible one).
- **Grounding stays separate from factual correctness** (section 2), **cognitive demand stays descriptive**, and the mode-alignment anchors in the table below are unchanged.

**Scores 1 to 5 (whole numbers):**

| Dimension | 1 | 3 | 5 |
|---|---|---|---|
| **Distractor plausibility** (G2, choice-based) | at least one absurd, unrelated or obviously false incorrect choice | mostly plausible, at least one weak strawman | every incorrect choice reflects a realistic misconception, an incomplete idea or a close concept |
| **Mode alignment** (G3, every item) | trivial, trick, off-source or unfair for a Challenge Quiz | fair and routine | appropriately challenging **and** fair for the source; would separate a learner who understands from one who does not |
| **Cognitive demand** (descriptive only) | pure recall of a stated fact | single-step application or comparison | multi-step reasoning on a stated situation |
| **Clarity** (G6) | ambiguous or hard to parse | understandable with effort | precise and unambiguous |
| **Faithfulness** (G6) | contradicts or invents beyond the inputs | mostly supported, some UNSUPPORTED detail | fully GROUNDED |

**Serious giveaway (G4), yes or no, with a pattern type, choice-based items only.** Yes if a learner who does **not** know the material could identify the keyed answer, or eliminate all but one choice, with confidence **from form
alone**. Pattern types: `LENGTH_OR_SPECIFICITY`, `ABSOLUTE_WORDS`, `GRAMMAR_AGREEMENT`, `FORMAT_ODD_ONE_OUT`, `DUPLICATE_CHOICES`, `ALL_OR_NONE_OF_THE_ABOVE`. A mere tendency is **not** serious. The scripted descriptors
(`QualityDescriptors`) are **mechanical warning indicators only: they point reviewers at patterns, they are never independently confirmed serious giveaways, and no script rejects an item.**

## 4. Review protocol and sampling (specified before scoring)

1. **Primary scoring, every item:** Codex (D11), blind, all fields above.
2. **Pre-drawn review order, fixed before scoring starts.** With a fixed seed, per arm, the exporter writes a seeded **stratified random order of its unflagged-eligible choice-based items over note × question-format strata** (largest-remainder allocation, so any prefix stays close to proportional across notes and formats, and a top-up beyond the floor stays stratified). Codex-flagged items are removed from this order and reviewed separately as `C_F`. The G4 estimator's strata remain the question formats. The Release Implementor
   commits only the seed and the manifest hash to the pre-registration; the order file stays private.
3. **Independent Opus review**, cold agents, key closed, same inputs, **a reviewer that has not seen the primary scores or any earlier Opus scores**: (a) **every** primary critical flag and **every** primary serious-giveaway flag; (b) a **random sample of unflagged choice-based items, stratified across notes and question formats, of at least 60 items per arm** (the review floor, P3), drawn from the pre-drawn order. **Codex-flagged items are reviewed separately as their own stratum (`C_F`)**, and are interleaved in the batches only so reviewers cannot tell them apart; (c) every item where two reviewers differ by 2 or more points or disagree on a critical flag, which a **third cold, independent pass** adjudicates.
   Defect definitions, adjudication standard and instructions are **identical across baseline and proposed arms**. **Flagged items and sampled items are interleaved in the same batches under identical instructions**, so a reviewer cannot tell which items Codex flagged.
4. **Confirmed** = flagged by the primary scorer and agreed by an independent reviewer, or found by an independent reviewer. Each confirmed critical defect is root-caused and shown to the owner individually.
5. **Missed-defect escalation (Product UX Decision 3, approved).** If a cold Opus reviewer finds **even one** confirmed critical defect that Codex missed in a **proposed-prompt** arm (B2 or B3), that **entire arm** is independently re-reviewed (a fresh cold reviewer, a separate adjudication) **before its gates are interpreted**.
   The **missed-defect rate** (independently found critical defects Codex did not flag, per unflagged item reviewed) is reported for **every** arm and each proposed arm is **compared with the same-model current-prompt arm**. A missed defect triggers escalation, **not** automatic failure; **any unresolved critical defect, or an
   incomplete required re-review, means that arm cannot pass.**
6. **Scorer isolation (binding).** Blinding is only real if a scorer cannot read the key. Primary scoring runs as `codex exec -s read-only -C <scratch directory>`, where the scratch directory is **outside the repository** and holds **only** the blind chunk files and `notes.jsonl`. Independent reviewers, adjudicators and re-reviewers
   receive their batch content **inline in the prompt, with no file paths**. `blind-key.jsonl`, every `results.jsonl`, `review-order.jsonl`, `opus-batch-manifest.json` and the descriptors never sit under any directory a scorer can read or is pointed at. **A scoring pass that opened or was shown any of those files is void and is re-run by a fresh reviewer.** The scorer instructions are frozen separately
   (`docs/claude-plans/2026-10-27-challenge-quiz-quality-scorer-instructions.md`) and its SHA-256 is pre-registered next to this rubric's.
7. **Reporting:** representative before-and-after examples for **improvements and failures**, chosen by a fixed rule (the largest paired differences in each direction per model), never by hand. Artifacts are private, git-ignored and Official-only.

## 5. Gates

Luna and Sol are judged **separately**, each proposed arm against the same-model current-prompt arm on the 20 paired notes; **both** must pass before a global prompt change. Gates are read **overall**.

- **G1 Correctness (owner-approved):** confirmed critical defects no more than the same-model current arm, **and zero wording-sensitive regression** (the excess rule in section 2: a mechanical screen, not proof of causal attribution).
- **G2 Distractors:** mean plausibility at least 4.0 and at least +0.3 over the same-model current arm.
- **G3 Mode alignment:** mean at least 4.0 of 5. Cognitive demand is reported, not gated.
- **G4 Giveaways (Product UX Decision 2, method predeclared here):** the approved target stays **5% serious giveaways**. The gate **decides on the sampling-adjusted estimate**, with the no-recurring-pattern condition. Report **both**:
  1. the **confirmed minimum rate** = all independently confirmed serious giveaways ÷ all scored choice-based items in the arm (`N`);
  2. the **sampling-adjusted estimate** `Ĝ = (C_F + Σ_h U_h · k_h / n_h) / N`, where `C_F` = Codex-flagged items an independent reviewer confirmed, and for each question-format stratum `h`: `U_h` = unflagged items, `n_h` = unflagged items independently reviewed, `k_h` = those confirmed as serious giveaways (a stratum with
     `n_h = 0` uses the pooled unflagged rate).
  **Uncertainty rule (predeclared; parameters signed off by Product UX 2026-10-27):** P1 confidence = one-sided **90%**; P2 `UL` threshold = **10%** (an uncertainty tolerance, **not** a replacement quality target: the estimate `Ĝ` must itself be at most 5% to pass); P3 minimum independently reviewed unflagged items per arm = **60**; P4 `N ≥ 150`; P5 recurring pattern = the same type on **3 items across 2 notes**. If a minimum sample requirement or either threshold fails: **HOLD**. `UL` = `(C_F + U · p_up) / N`, where `U` = all unflagged choice-based items and `p_up` = the **one-sided 90% exact (Clopper-Pearson) upper limit** of the pooled sampled-unflagged proportion `Σk_h / Σn_h`. Also reported: the 95% interval, for transparency only.
  **What `UL` is, and is not (technical characterization, added before freeze).** `Ĝ` is stratified by question format; `UL` applies the Clopper-Pearson limit to the **pooled** sampled-unflagged proportion. It is **not an exact confidence bound for the stratified estimate** and is not claimed to be one. It is an **uncertainty safeguard** whose bound is *approximately* valid because (1) the review order allocates the sample **proportionally** across note × format strata, so the pooled proportion estimates the same stratum-weighted rate as `Ĝ`; (2) it treats the draw as independent binomial trials although the sample is drawn **without replacement** from a small finite pool (a 60 of about 160 draw is over a third), which makes the bound **wider, so conservative**; and (3) proportional stratification normally lowers variance relative to a simple random sample, again conservative. It can be **optimistic** where allocation drifts from proportional (rounding in small strata, the at-least-one-per-stratum rule, Codex-flagged removals, a top-up). That is why `UL` is only a secondary safeguard under the decision rule and `Ĝ ≤ 5%` is the primary criterion; no verdict is described as a 90% guarantee.
  **Recurring systematic pattern:** the same pattern type confirmed on at least 3 items spanning at least 2 notes within the arm.
  **Decision:** **FAIL** if `Ĝ > 5%` or a recurring pattern exists. **PASS** only if `Ĝ ≤ 5%` **and** `UL ≤ 10%` **and** `N ≥ 150` **and** at least 60 unflagged items were independently reviewed **and** there is no recurring pattern. Otherwise **INCONCLUSIVE**, which is **HOLD**.
  **A 95% upper bound below 5% is not required** (not achievable at this sample size). Numerical illustration of the **formula only, not a verdict** (30 reviewed is **below the signed floor of 60, so every verdict at that size is INCONCLUSIVE**): with 4 confirmed among flagged, 160 unflagged items, 30 reviewed and 0 found, `Ĝ` is 2.0% and `UL` about 7.9%; with 1 found, `Ĝ` is 4.7% and `UL` about 11.9%; with 2 found, `Ĝ` is 7.3%. Verdicts at the signed floor are in Appendix A (`n = 60`). **With the floor at 60 the `UL ≤ 10%` condition rarely binds**: it matters only in a narrow region (for example 5 confirmed among flagged, 295 unflagged, 60 reviewed, 2 found: `Ĝ` 4.9%, `UL` 10.2%, INCONCLUSIVE); the floor and `Ĝ ≤ 5%` are the effective constraints.
  **Coverage check (G2 and G4 are complementary, no new gate).** G4 catches *structural* giveaways (length, absolute words, grammar, odd-one-out, duplicates, all/none of the above), judged **from form alone**. G2 catches *semantic* weakness: a distractor that is absurd, unrelated or obviously false in content scores 1 on the anchor even when its form is unremarkable, and a mean below 4.0 or less than +0.3 over the same-model current arm fails the gate. An item can be a G2 problem with no G4 flag, and the reverse; both are scored on every choice-based item, so neither depends on the other flagging it.
- **G5 Formats:** no format below 3.5 on any dimension with at least 20 items.
- **G6 Collateral:** clarity and faithfulness within 0.2 of the same-model current arm; **delivered ratio** (questions delivered ÷ 15 requested, per start) within **2 percentage points**; p50 generation latency and tokens per delivered question each within **15%** (these two are separately approved limits).
- **G7 MULTI_SELECT and validator telemetry (provisional tolerance signed off 2026-10-27):** compared with the same-model current arm using **absolute percentage-point differences, never relative percentages**, all within **10 percentage points**: (a) the share of MULTI_SELECT items with 3 (rather than 2) correct indices;
  (b) the **validator-rejection rate** = `Σ validatorRejected ÷ Σ validatorRequested` over **successful** starts whose telemetry was `CAPTURED`; (c) the **validator-retry rate** = `Σ validatorRetried ÷ Σ validatorRequested` over the same starts. Both are item-level proportions in percentage points. **The production counts log is all-format: it exposes validator rejections for every question format, not MULTI_SELECT alone, so (b) and (c) are labelled all-format and cannot isolate MULTI_SELECT rejections.** *This supersedes the earlier provisional "retry rate within 2 points" in G6 (a genuine overlap, resolved by this sign-off).*
  **Counter semantics, verified against the production code before freeze** (`OpenAiLlmStudyPackService`, the `challenge_quiz_generation_counts` log line, one line **per generation attempt**, never per question):
  1. `requested` = questions asked for in that attempt (15 in this evaluation). `rejected` increases **at most once per question slot**, only when a first-pass candidate fails the statement-pair or answer/explanation consistency check. A replacement that fails again increments `omitted`, **not** `rejected`.
  2. `retried` increases **exactly once per first-pass rejection** (the one replacement call). **`retried` therefore equals `rejected` by construction, and (c) is not an independent signal**: it is kept because it was signed off, but its verdict can never differ from (b)'s.
  3. `accepted` counts **filled slots**: a rejected slot whose replacement succeeded is counted in `rejected` **and** again in `accepted`. So `accepted + rejected` counts replaced slots twice and is not a count of questions: with the verifier off it equals `requested − omitted + rejected`, which exceeds `requested` by the number of successfully replaced slots in that start. **AMENDMENT, APPROVED BY THE OWNER 2026-10-27 (it edits signed-off text, so it was returned for approval as Product UX asked): the denominator `Σ(accepted + rejected)` is replaced by `Σ requested`**, the one denominator in which each slot appears exactly once. No threshold changes; the 10-point tolerance, the 20% telemetry rule and the all-format label are untouched. The effect on a rate is `rejected ÷ requested` instead of `rejected ÷ (requested + replaced)`, always at least as large, so (b) and (c) become, if anything, slightly stricter; the size is not estimated here because no production sample was drawn for it.
  4. **Attempts.** A call retries as a whole on invalid output, so one start can emit two lines. **Only the line with `outcome=returned` (the attempt that produced the delivered quiz) enters (b) and (c), and only for starts that succeeded;** a line with `outcome=threw` describes discarded work and would double-count the same slots, and a failed start has no delivered quiz. The number of discarded attempts per arm is reported descriptively (a reliability and cost signal), not gated.
  5. **Verifier.** `accepted` is overwritten when the optional statement-pair verifier runs. It is **off by default** (`templateSeedEnabled` and `liveStartEnabled` are both `false`, `StudySnapProperties` lines 107 to 108) and the benchmark does not enable it. The log still prints a `verifierPath` value when the verifier is disabled, so that field proves nothing; the harness instead records the line's `verified` count, and any non-zero value is reported and voids (b) and (c) for that start. With the verifier off the denominator in point 3 does not depend on `accepted` at all.
- **G8:** anything the sample cannot support is INCONCLUSIVE and counts as **not passing**.
- **Denominators and missing data (defined before scoring; any gap is reported and never filled in):**
  - **Paired note:** a note is paired for a model only if **both** its current-prompt and proposed-prompt starts succeeded. Fewer than **15 of 20** paired notes for a model is INCONCLUSIVE. A failed or spend-stopped start is a missing side of a pair, **reported, never re-run or replaced**.
  - **Missingness safeguards (Product UX final review; mechanical, fixed before any result exists).** Whatever the numeric minimum, the report lists **every missing pair with its reason**, the **subject (stratum) and level distribution** of the paired and unpaired notes, and the **arm-by-arm difference** in missingness. A comparison is marked **INCONCLUSIVE** if **any** of these holds, even when 15 pairs exist:
    1. **concentrated in the proposed arm:** the proposed arm has at least **2 more** failed or spend-stopped starts than the same-model current arm;
    2. **skewed paired set:** after dropping unpaired notes, either level has fewer than **6** paired notes, or a stratum present in the 20 selected notes has no paired note left;
    3. **correlated with validation failure:** more than half of an arm's failed starts, or of its MISSING-telemetry starts, are classified `SCHEMA_OR_PARSE` or `MULTI_OUTPUT_TEXT` (validation-type failures) rather than `HTTP_ERROR`, `EMPTY` or `SPEND_STOP`, with at least 2 such starts.
    A note missing on **both** sides of a pair (the same note failing in the current and the proposed arm) is reported and does **not** count toward triggers 1 and 3, because it does not bias a paired comparison; it **does** count toward trigger 2, which describes the paired set that remains (a level or stratum emptied by a both-sides miss is still lost coverage). **Practical note: with 20 notes over about 12 strata, several strata hold a single note, so one failed start in such a stratum makes that model's comparison INCONCLUSIVE; that is the approved rule operating as written.** The 20% telemetry rule stays as written. **No imputation, no silent replacement of a failed start, no exclusion of an inconvenient result.** The three limits (2, 6 and half with a minimum of 2) are mechanical defaults chosen for a 20-note design and were **approved by the owner on 2026-10-27**.
  - **Item means (G2, G3, G6 clarity and faithfulness, G5):** the mean over items that carry that dimension (G2 and distractor scores only for choice-based items); an item the primary scorer failed to score, or whose score file row is invalid, makes the arm **incompletely scored** and the gates for that arm INCONCLUSIVE.
  - **Telemetry:** a successful start with no generation-counts event is `MISSING`, **excluded** from the (b) and (c) denominators and counted. If more than **20%** of an arm's successful starts are `MISSING`, G7 (b) and (c) are INCONCLUSIVE for that comparison. A rate is never imputed as zero.
  - **G4 `N`:** every scored choice-based item in the arm; strata with `U_h = 0` contribute nothing.
  - **Latency and tokens:** the per-start values from `results.jsonl` for successful starts only; a spend-stopped start contributes none.

- **Near-duplicate questions** in fewer than 10% of starts. A failed or spend-stopped start is a **missing side of a pair**, reported, never re-run or replaced.

**Per-level report (MANDATORY, descriptive only).** Every metric is reported overall **and** for `COLLEGE` and `BOARD_EXAM_REVIEW` separately (10 notes each, no per-level gate). Required because the feedback learner's own effective level was `BOARD_EXAM_REVIEW`, whose guidance already says "plausible distractors,
multi-step thinking": the new wording is expected to matter more at `COLLEGE`, and an overall mean could hide a flat `BOARD_EXAM_REVIEW` result. A flat or negative `BOARD_EXAM_REVIEW` result is reported prominently as a finding for the owner even when the gates pass.

## 6. Outcomes

- **PASS:** both models pass G1 to G8 → the owner sees the findings and decides whether to ship the one-file prompt change (the operational deploy gate still applies).
- **HOLD (default):** any gate fails, is inconclusive, or the run is incomplete → no prompt change and no measurement-only deploy without separate owner approval. A pass for one model never overrides a failure for the other.
- The evaluation recommends; it never touches production prompts, runs no paid call by an agent, and authorizes no deployment.

## 7. Signature block (fill in at sign-off)

- [x] **Owner signed sections 2 to 5 on 2026-10-27.** Product UX's decisions 1 to 3, its four sign-offs (review floor 60, the G4 parameters, the wording-sensitive regression rule, G7's 10-point tolerance) and its final confirmation (with the condition-2 wording applied) are incorporated; the section 5 denominators and missing-data handling were added by the Release Implementor on request and approved by the owner and Product UX.
- Signed values: P1 90% · P2 10% · P3 60 · P4 150 · P5 3 items across 2 notes · G7 10 percentage points · G6 delivered ratio 2 points · G7 denominator `Σ requested` · missingness limits 2 failed starts, 6 paired notes per level, half with a minimum of 2.
- **Rubric SHA-256: recorded only in the pre-registration file.** The hash is taken *after* this block is filled in and committed; writing the hash here would change it.
- Date signed: `2026-10-27` &nbsp; Pre-registration: `docs/claude-plans/2026-10-27-challenge-quiz-quality-preregistration.md` (20 pack ids, SHA-256 of `quality-e.jsonl`, this rubric's hash, the review-order **seed**, the SHA-256 of the **scorer-instructions document**, **and content hashes of the gate calculator that survive any merge style** (the git tree hash of `backend/src/test/java/com/studysnap/backend/benchmark` and the blob hash of `backend/src/test/resources/benchmark/challenge-quality/challenge-quiz-developer.proposed.txt`, plus the release-branch commit once the harness is merged) with its tests reproducing this rubric's illustrations), committed before the first paid command. **The review-order manifest hash depends on the generated items, so it is committed as an addendum after generation and before any scoring begins.** Reviewer volume (Opus passes of about 100 items) is stated to the owner at sign-off.

## Appendix A. G4 operating characteristic (computed offline, N = 200 choice items per arm, flagged items = confirmed + 4 unconfirmed; P1 = 90%, P2 = 10%)

Each cell is the verdict (the sampling-adjusted `Ĝ` / the upper limit `UL`, in %). `C_F` = Codex-flagged items an independent reviewer confirmed; `k` = serious giveaways an independent reviewer found among the `n` reviewed **unflagged** items.
**Reading it:** the number of reviewed unflagged items `n` is the lever, and it costs reviewer time, not generation dollars. **The `n = 25` and `n = 40` columns are the formula only: they are below the signed floor (P3 = 60), so every verdict at those sizes is INCONCLUSIVE, whatever the cell says.** Only the `n = 60` and `n = 80` columns are valid verdicts. At `n = 60` one miss passes for `C_F` up to 6.

| C_F, k | n = 25 (formula only, below floor) | n = 40 (formula only, below floor) | n = 60 | n = 80 |
|---|---|---|---|---|
| 0, 0 | PASS 0.0 / 8.6 | PASS 0.0 / 5.5 | PASS 0.0 / 3.7 | PASS 0.0 / 2.8 |
| 0, 1 | INCONCLUSIVE 3.9 / 14.4 | PASS 2.5 / 9.2 | PASS 1.6 / 6.2 | PASS 1.2 / 4.7 |
| 0, 2 | FAIL 7.8 / 19.5 | INCONCLUSIVE 4.9 / 12.5 | PASS 3.3 / 8.5 | PASS 2.5 / 6.4 |
| 2, 1 | INCONCLUSIVE 4.9 / 15.2 | INCONCLUSIVE 3.4 / 10.1 | PASS 2.6 / 7.1 | PASS 2.2 / 5.6 |
| 4, 0 | INCONCLUSIVE 2.0 / 10.4 | PASS 2.0 / 7.4 | PASS 2.0 / 5.6 | PASS 2.0 / 4.7 |
| 4, 1 | FAIL 5.8 / 16.1 | INCONCLUSIVE 4.4 / 11.0 | PASS 3.6 / 8.1 | PASS 3.2 / 6.6 |
| 4, 2 | FAIL 9.7 / 21.1 | FAIL 6.8 / 14.3 | FAIL 5.2 / 10.3 | PASS 4.4 / 8.3 |
| 6, 1 | FAIL 6.8 / 17.0 | FAIL 5.4 / 11.9 | PASS 4.6 / 9.0 | PASS 4.2 / 7.5 |
| 6, 2 | FAIL 10.6 / 21.9 | FAIL 7.8 / 15.1 | FAIL 6.2 / 11.2 | FAIL 5.4 / 9.2 |

Worst-case reviewer volume per run: flags (about 15 per arm) + the unflagged review at the chosen `n` (about 60) + adjudication, roughly 75 to 90 items per arm, **about 400 items (4 to 5 Opus passes of about 100)** in the base case; **up to two whole-arm re-reviews of about 300 items each (about 600 more) if escalation fires in both proposed arms**, so roughly 1,000 items (10 to 11 passes) in the worst case.
