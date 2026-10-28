# Challenge Quiz quality evaluation: report (v0.176.0)

**Outcome under the signed rubric: HOLD.** The proposed prompt wording is **not** shipped, no production prompt changed, and nothing is deployed. Specification: `docs/claude-plans/2026-10-27-challenge-quiz-quality-scoring-rubric.md` (signed 2026-10-27). Record of every fixed value, deviation and hash: `docs/claude-plans/2026-10-27-challenge-quiz-quality-preregistration.md`. This report **recommends**; it authorizes no change.

## 1. What was tested
The prompt the owner approved on 2026-10-27 (Changes A, B and C, one file, `challenge-quiz-developer.txt`) against today's production prompt. 20 Official notes (10 `COLLEGE`, 10 `BOARD_EXAM_REVIEW` by each note's own level, 12 strata, 4 quantitative), 15 `hard` questions per start, one start per note per arm, five generation arms (current prompt on `gpt-4.1-mini`, Luna and Sol; proposed prompt on Luna and Sol) plus about 100 stock template questions. 1,600 questions, blind: Codex scored all of them against the generator's inputs and the full note; cold Opus reviewers re-scored every flagged item plus a stratified sample of unflagged items (497 items); 144 disagreements were adjudicated by two fresh reviewers. Spend about $3.03 by the meter, under the $4 cap.

## 2. Result: Sol (the premium model), proposed against current prompt, 20 paired notes

| Gate | Required | Proposed (B3) | Current (A3) | Verdict |
|---|---|---|---|---|
| G1 critical defects | no more than current, no wording regression | 0 confirmed | 0 confirmed | PASS |
| G2 distractor plausibility | mean ≥ 4.0 and +0.3 | 3.59 | 3.75 | **FAIL** (went down) |
| G3 mode alignment | mean ≥ 4.0 | 3.81 | 4.23 | **FAIL** |
| G4 serious giveaways | estimate ≤ 5%, no recurring pattern | 2.7% (upper limit 7.3%) | 4.5% with a recurring `ABSOLUTE_WORDS` pattern | PASS for B3 |
| G5 formats | no format below 3.5 | MCQ cognitive demand 2.98 | TRUE_FALSE 3.48 and 2.48 | **FAIL** |
| G6 collateral, G7, G8 | within tolerance, 15 of 20 paired | within tolerance, 20 of 20 | | PASS |

- **What moved:** the proposed prompt removed the recurring absolute-word giveaway pattern and lowered the giveaway estimate. But reviewers scored its questions **easier and less demanding**: cognitive demand fell from 3.47 to 2.90 and mode alignment from 4.23 to 3.81, and distractors did not improve. That is the opposite of what the prompt was meant to do.
- **Per level (descriptive only, 10 notes each):** `BOARD_EXAM_REVIEW` distractors -0.14 and mode alignment -0.34; `COLLEGE` mode alignment 3.75 against 4.25. No level shows a hidden gain.
- **Both models must pass.** Sol fails three gates, so the overall outcome is HOLD whatever Luna would show. **Luna's proposed-prompt arm (B2) is not interpreted in this report**: its missed-defect escalation (one critical defect found by a reviewer and not by Codex, `blind-0394`) would require a whole-arm re-review that was deliberately not run, because it could not change the outcome (pre-registration Addendum D).

## 3. What the baselines show about today's production prompt
These describe the **current** prompt and are the most useful product finding of the run. They are 20 notes and one start each, so treat them as strong signals, not measurements.

| | Luna current (A2) | Sol current (A3) |
|---|---|---|
| Distractor plausibility (1 to 5) | **3.23** | 3.75 |
| Mode alignment | **3.36** | 4.23 |
| Cognitive demand (descriptive) | 2.49 | 3.47 |
| Serious-giveaway estimate | **10.5%** (upper limit 16%) | 4.5% (upper limit 8.9%) |
| Recurring giveaway pattern | `ABSOLUTE_WORDS` | `ABSOLUTE_WORDS` |
| Confirmed critical defects (of 300) | **5** | 0 |

The learner's feedback ("distractors are easily spotted", "too easy") matches the weaker model: **Luna, which serves the free plan, produced weaker distractors, easier questions and more giveaways than Sol on the current prompt, and 5 confirmed critical defects against Sol's none**, while Sol's current output is considerably stronger. This is an internal finding. **It must not reach customer copy**, which never names a model and implies no plan quality difference (anti-drift of this release).

## 4. Limits, stated plainly
- **Small sample.** 20 notes, one start per arm. A few notes can move a mean; per-level reads (10 notes) are descriptive only. The gates are thresholds, not proof.
- **Reviewers are models.** Codex scored everything; Opus re-scored 497 items; there was no human review. Reviewers agreed on only about 71% of re-scored items by the harness's own disagreement rule (144 of 497 needed adjudication).
- **G5 is harsh by construction.** It fails if any format with 20 or more items scores under 3.5 on any dimension, including the descriptive cognitive demand; short true/false items score low on it by nature, so the **current** Sol prompt also fails G5. G2 and G3 failed independently, so the verdict does not depend on G5, but the rule should be revisited before any rerun.
- **The test cannot attribute the drop.** It compared the whole of Changes A, B and C together with one prompt, so it cannot say which change made questions easier.
- **Luna's proposed arm is uninterpreted** (see section 2), and the `gpt-4.1-mini` arm is incompletely scored (two rows, see below) and is no gate's arm.

## 5. What did not go to plan (full detail in the pre-registration)
- Two primary rows (A1) broke the harness's schema; I had wrongly recorded all rows as valid (Addendum B, corrected in Amendment 2). The batch builder was fixed (PR #1537) and the calculator hash re-pinned.
- The audit rule voided every primary chunk on the word "manifest" matching "clinical manifestations"; an independent command audit showed no scorer read anything it should not have, and the rule was narrowed.
- One Sol spend stop was raised before the full run (Amendment 1); one reviewer pass was voided by a pre-declared rule and re-run; reviewer isolation moved from inline text to scratch directories (Amendment 3), which is weaker than a command transcript audit.
- While reading the report I briefly printed Luna's failed-gate list that I had said would stay uninterpreted; nothing rests on it.

## 6. Recommendation and options for the owner
1. **Do not ship the proposed wording.** It lowers difficulty and demand on the premium model for a giveaway gain.
2. **The real problem is Luna's current quality**, not the premium model's. Decide whether Challenge generation for the free plan deserves its own improvement work.
3. **If the work continues, split it.** A follow-up could test only the giveaway fix (absolute words and parallel choices) without Change A, which is the likeliest cause of the easier questions, and test Luna and Sol separately. Each rerun is about $3 to $4 and the same blind pipeline; the harness and the pre-registration method are reusable. G5 should be reworded first.
4. **Other levers, not tested here:** a Luna-specific prompt or a verifier pass for the free plan, or routing Challenge on the free plan to a stronger model (a cost and entitlement decision, out of scope for this release).
5. **Release closure.** With HOLD, v0.176.0 produced no production change. The harness (test scope) and this record are the deliverables; the closure options are in the owner decision list.

## 7. Reproducibility
Everything needed is hashed in the pre-registration: the sample, the 1,600 blind items, the primary scores, the 497 reviews, the 144 adjudications, the gate report and the calculator (benchmark tree `f5f20006c3a2ecf705460bc2366e82f664a5ab14`). Raw data is private and git-ignored under `backend/target/llm-benchmark/`, with a backup at `~/quality-eval-v0176-backup/`.
