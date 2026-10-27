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
  (Change B's "defensibly incorrect" and parallel-choice rules; Change A's "do not invent complexity"). After unblinding, for each wording-sensitive type `T` and each proposed arm (B2, B3), the defects **counted against the new wording** (reported as `NEW_WORDING`) are the **excess over the same-model current arm**:
  `max(0, count_B(T) − count_A(T))`. Every other type is never attributed to the wording by default; it still counts under "no more than the same-model current arm". The calculator computes this; nobody decides it after seeing results. **All confirmed critical defects are reported individually regardless of category, G1's overall no-regression requirement is preserved, and no individual wrong key is dismissed merely because the baseline arm also has defects.**
*Harness addition required:* `notes.jsonl` must also carry `studyPackSummary` and `keyConcepts`.

## 3. Item-level judgements

The primary scorer (Codex) records every field. Choice-based = standard MCQ, MULTI_SELECT, assertion items and MATCHING items; True/False and the free-text formats are scored on correctness, grounding and clarity only.

**Critical defect flag (G1), yes or no, with a reason and a root cause.** Yes if **any** of:
1. the keyed answer is wrong (`factuallyCorrect = no` for the keyed answer);
2. more than one choice is defensibly correct, or none is (the single-best-answer rule fails);
3. the explanation contradicts the keyed answer, or the question or explanation is **CONTRADICTED** per section 2.
A critical flag is never averaged away.

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
2. **Pre-drawn review order, fixed before scoring starts.** With a fixed seed, per arm, the exporter writes a seeded random order of all items **within strata of question format**, plus a stratified 20% sample (arm × format × note level). The Release Implementor
   commits only the seed and the manifest hash to the pre-registration; the order file stays private.
3. **Independent Opus review**, cold agents, key closed, same inputs, **a reviewer that has not seen the primary scores or any earlier Opus scores**: (a) **every** primary critical flag and **every** primary serious-giveaway flag; (b) a **random sample of unflagged choice-based items, stratified across notes and question formats, of at least 60 items per arm** (the review floor, P3), drawn from the pre-drawn order. **Codex-flagged items are reviewed separately as their own stratum (`C_F`)**, and are interleaved in the batches only so reviewers cannot tell them apart; (c) every item where two reviewers differ by 2 or more points or disagree on a critical flag, which a **third cold, independent pass** adjudicates.
   Defect definitions, adjudication standard and instructions are **identical across baseline and proposed arms**. **Flagged items and sampled items are interleaved in the same batches under identical instructions**, so a reviewer cannot tell which items Codex flagged.
4. **Confirmed** = flagged by the primary scorer and agreed by an independent reviewer, or found by an independent reviewer. Each confirmed critical defect is root-caused and shown to the owner individually.
5. **Missed-defect escalation (Product UX Decision 3, approved).** If a cold Opus reviewer finds **even one** confirmed critical defect that Codex missed in a **proposed-prompt** arm (B2 or B3), that **entire arm** is independently re-reviewed (a fresh cold reviewer, a separate adjudication) **before its gates are interpreted**.
   The **missed-defect rate** (independently found critical defects Codex did not flag, per unflagged item reviewed) is reported for **every** arm and each proposed arm is **compared with the same-model current-prompt arm**. A missed defect triggers escalation, **not** automatic failure; **any unresolved critical defect, or an
   incomplete required re-review, means that arm cannot pass.**
6. **Reporting:** representative before-and-after examples for **improvements and failures**, chosen by a fixed rule (the largest paired differences in each direction per model), never by hand. Artifacts are private, git-ignored and Official-only.

## 5. Gates

Luna and Sol are judged **separately**, each proposed arm against the same-model current-prompt arm on the 20 paired notes; **both** must pass before a global prompt change. Gates are read **overall**.

- **G1 Correctness (owner-approved):** confirmed critical defects no more than the same-model current arm, **and zero with root cause `NEW_WORDING`**.
- **G2 Distractors:** mean plausibility at least 4.0 and at least +0.3 over the same-model current arm.
- **G3 Mode alignment:** mean at least 4.0 of 5. Cognitive demand is reported, not gated.
- **G4 Giveaways (Product UX Decision 2, method predeclared here):** the approved target stays **5% serious giveaways**. The gate **decides on the sampling-adjusted estimate**, with the no-recurring-pattern condition. Report **both**:
  1. the **confirmed minimum rate** = all independently confirmed serious giveaways ÷ all scored choice-based items in the arm (`N`);
  2. the **sampling-adjusted estimate** `Ĝ = (C_F + Σ_h U_h · k_h / n_h) / N`, where `C_F` = Codex-flagged items an independent reviewer confirmed, and for each question-format stratum `h`: `U_h` = unflagged items, `n_h` = unflagged items independently reviewed, `k_h` = those confirmed as serious giveaways (a stratum with
     `n_h = 0` uses the pooled unflagged rate).
  **Uncertainty rule (predeclared; parameters signed off by Product UX 2026-10-27):** P1 confidence = one-sided **90%**; P2 `UL` threshold = **10%** (an uncertainty tolerance, **not** a replacement quality target: the estimate `Ĝ` must itself be at most 5% to pass); P3 minimum independently reviewed unflagged items per arm = **60**; P4 `N ≥ 150`; P5 recurring pattern = the same type on **3 items across 2 notes**. If a minimum sample requirement or either threshold fails: **HOLD**. `UL` = `(C_F + U · p_up) / N`, where `U` = all unflagged choice-based items and `p_up` = the **one-sided 90% exact (Clopper-Pearson) upper limit** of the pooled sampled-unflagged proportion `Σk_h / Σn_h`. Also reported: the 95% interval, for transparency only.
  **Recurring systematic pattern:** the same pattern type confirmed on at least 3 items spanning at least 2 notes within the arm.
  **Decision:** **FAIL** if `Ĝ > 5%` or a recurring pattern exists. **PASS** only if `Ĝ ≤ 5%` **and** `UL ≤ 10%` **and** `N ≥ 150` **and** at least 60 unflagged items were independently reviewed **and** there is no recurring pattern. Otherwise **INCONCLUSIVE**, which is **HOLD**.
  **A 95% upper bound below 5% is not required** (not achievable at this sample size). Illustration, not a result: with 4 confirmed among flagged, 160 unflagged items, 30 reviewed and 0 found, `Ĝ` is 2.0% and `UL` about 7.9% (PASS); with 1 found, `Ĝ` is 4.7% but `UL` about 11.9% (INCONCLUSIVE); with 2 found, `Ĝ` is 7.3% (FAIL).
- **G5 Formats:** no format below 3.5 on any dimension with at least 20 items.
- **G6 Collateral:** clarity and faithfulness within 0.2 of the same-model current arm; **delivered ratio** (questions delivered ÷ 15 requested, per start) within **2 percentage points**; p50 generation latency and tokens per delivered question each within **15%** (these two are separately approved limits).
- **G7 MULTI_SELECT and validator telemetry (provisional tolerance signed off 2026-10-27):** compared with the same-model current arm using **absolute percentage-point differences, never relative percentages**, all within **10 percentage points**: (a) the share of MULTI_SELECT items with 3 (rather than 2) correct indices;
  (b) the **validator-rejection rate** = `Σ validatorRejected ÷ Σ (validatorAccepted + validatorRejected)` over starts whose telemetry was `CAPTURED`; (c) the **validator-retry rate** = `Σ validatorRetried ÷ number of CAPTURED starts`. *This supersedes the earlier provisional "retry rate within 2 points" in G6 (a genuine overlap, resolved by this sign-off).*
- **G8:** anything the sample cannot support is INCONCLUSIVE and counts as **not passing**.
- **Denominators and missing data (defined before scoring; any gap is reported and never filled in):**
  - **Paired note:** a note is paired for a model only if **both** its current-prompt and proposed-prompt starts succeeded. Fewer than **15 of 20** paired notes for a model is INCONCLUSIVE. A failed or spend-stopped start is a missing side of a pair, **reported, never re-run or replaced**.
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

- [ ] Owner signs sections 2 to 5. Product UX's decisions 1 to 3 and its four sign-offs (review floor 60, the G4 parameters, the wording-sensitive regression rule, G7's 10-point tolerance) are incorporated; the section 5 denominators and missing-data handling were added by the Release Implementor on request.
- Signed values: P1 90% · P2 10% · P3 60 · P4 150 · P5 3 items across 2 notes · G7 10 percentage points · G6 delivered ratio 2 points.
- Rubric SHA-256 (computed on the signed file): `________________`
- Date signed: `____-__-__` &nbsp; Pre-registration: `docs/claude-plans/2026-10-27-challenge-quiz-quality-preregistration.md` (20 pack ids, SHA-256 of `quality-e.jsonl`, this rubric's hash, the review-order **seed**, **and the commit SHA of the gate calculator** with its tests reproducing this rubric's illustrations), committed before the first paid command. **The review-order manifest hash depends on the generated items, so it is committed as an addendum after generation and before any scoring begins.** Reviewer volume (Opus passes of about 100 items) is stated to the owner at sign-off.

## Appendix A. G4 operating characteristic (computed offline, N = 200 choice items per arm, flagged items = confirmed + 4 unconfirmed; P1 = 90%, P2 = 10%)

Each cell is the verdict (the sampling-adjusted `Ĝ` / the upper limit `UL`, in %). `C_F` = Codex-flagged items an independent reviewer confirmed; `k` = serious giveaways an independent reviewer found among the `n` reviewed **unflagged** items.
**Reading it:** the number of reviewed unflagged items `n` is the lever, and it costs reviewer time, not generation dollars. At `n = 25` one miss (`k = 1`) already means INCONCLUSIVE or FAIL; at `n = 60` one miss passes for `C_F` up to 6.

| C_F, k | n = 25 | n = 40 | n = 60 | n = 80 |
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
