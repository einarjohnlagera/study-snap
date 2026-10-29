# Challenge Quiz quality evaluation: report for the owner (v0.177.0)

Status: **RESULT: HOLD. Neither Luna nor Sol passes under the signed rubric v2.** The production Challenge prompt is unchanged and nothing is deployed. Written 2026-10-29 by the Release Implementor from `quality-gates/gates.json` (SHA-256 `cd0c440322a098d8612b1a9e140ae3bf6fa363544807bd9bc04c9b2b736257ca`) and `gates.md` (`1eed42461c18dfba0583d7b9e37236ff6ce2cbbc2375c7ff4fc2b7ccde526ed5`). Internal only: customer copy never names a model and implies no plan quality difference. Pre-registration and amendments: `docs/claude-plans/2026-10-28-challenge-quiz-quality-preregistration-v0177.md`.

## 1. What was tested
Variant G+ (the five owner-approved Change B choice-quality bullets, no Change A, no Change C; resource SHA-256 `019c5bdf…1029f16`) against today's production prompt, on 20 Official notes (10 `COLLEGE`, 10 `BOARD_EXAM_REVIEW`), 15 `hard` questions per start, Luna (free plan) and Sol (premium) judged separately and both required to pass. 1,540 blind items scored by Codex, 465 independently reviewed by Opus, the whole of the B3 arm re-reviewed on the rubric's trigger, 159 adjudications; no item unresolved. Generation cost: metered $3.13, the OpenAI dashboard $2.53, inside the $4.00 ceiling.

## 2. Result (G5 under the corrected v2 rule; every other gate as signed)
| Gate | Luna (current to G+) | Sol (current to G+) |
|---|---|---|
| G1 correctness | **FAIL**: confirmed critical defects 0 to 2 (both `WRONG_KEY`) | PASS: 1 to 1 |
| G2 distractor plausibility (at least 4.0 and +0.3) | **FAIL**: 3.27 to 3.30 (+0.03; 95% interval -0.14 to +0.19) | **FAIL**: 3.92 to 3.65 (-0.28; interval -0.41 to -0.14) |
| G3 mode alignment (at least 4.0) | **FAIL**: 3.41 to 3.30 | **FAIL**: 4.22 to 3.85 (-0.36; interval -0.49 to -0.24) |
| G4 giveaways | **FAIL**: estimate 4.2% and upper limit 8.7% are inside the limits, but a recurring giveaway pattern exists | PASS: 0.7% |
| G5 formats (v2) | PASS (MCQ and TRUE_FALSE evaluated, three formats NOT_EVALUATED) | PASS on MCQ; **owner-review flag**: TRUE_FALSE has 22 current and 18 G+ items, so NOT_EVALUATED |
| G6 collateral | **FAIL**: tokens per question +16.7% (limit 15%); latency +7.8% | **FAIL**: p50 latency +21.6% (limit 15%); tokens +6.9% |
| G7 MULTI_SELECT | **FAIL**: three-correct share 66.7% to 55.6% (11.1 points, limit 10) | **FAIL**: 72.2% to 18.2% (54 points) |
| G8 | PASS (20 paired notes) | PASS (20 paired notes) |

Descriptive, no gate: cognitive demand fell on both (Luna 2.51 to 2.32, Sol 3.45 to 3.06). Sol faithfulness fell 4.87 to 4.70 (interval -0.25 to -0.10), inside G6's 0.2 tolerance. Per level (10 notes each): Luna distractors rose at `COLLEGE` (+0.20) and fell at `BOARD_EXAM_REVIEW` (-0.15, and both of its two critical defects); Sol fell at both. Escalation: B3 triggered the whole-arm re-review (done); B2 did not.

## 3. What the baselines show
The current-prompt arms reproduce `v0.176.0`'s closely for Luna (distractors 3.27 against 3.23, mode alignment 3.41 against 3.36) and for Sol mode alignment (4.22 against 4.23). Sol's current-arm distractor mean moved from 3.75 to 3.92 on the same prompt, model and notes, so run-to-run noise on a 20-note start is about 0.17, comparable to part of the G2 margin; it does not change a verdict here, because Sol's G+ drop (-0.28) is outside its interval and Luna's gain (+0.03) is about zero.

## 4. Limits, stated plainly
- 20 notes and one start per arm; per-level and per-format reads are descriptive. G7 rests on 9 to 18 MULTI_SELECT items per arm, so its percentage-point swings are large and noisy; I do not know why Sol's three-correct share moved so much, and the data cannot say.
- Reviewers are models. Codex scored every item and Opus independently reviewed a stratified sample; no human review.
- **Sol's G6 latency is confounded**: the original Sol starts, their re-runs (Amendment 1) and note 1 ran at different times, and the A3 and B3 re-runs finished about 8.5 hours apart. Its token cost is not affected. The verdict does not depend on G6.
- Amendment 1: 35 Sol starts that failed with no HTTP response during a local network outage were voided and re-run once; 4 mini parse failures stand. A1 is no gate's arm and has two invalid primary rows, as in `v0.176.0`.
- This tested Change B alone against production, with no attribution among its five bullets (owner decision); it cannot say why it did not help.

## 5. What this means
Changing the Challenge prompt's wording, with Changes A, B and C together in `v0.176.0` and with Change B alone here, did not lift distractor plausibility to the gate for either model, and on Sol it made things measurably worse. **The production prompt stays as it is.** The gap the learner reported is real for the free plan's model (Luna's distractor mean is about 3.3 against a gate of 4.0), but prompt wording has now not closed it twice.

## 6. Options for the owner (nothing here is approved)
1. **Close `v0.177.0` as HOLD**, as `v0.176.0` was: the deliverables are the corrected G5 calculator, the G+ evaluation resource and this record; the Backlog row records that Change B alone does not help.
2. **Other levers, each needing its own owner decision and release, excluded by this release's anti-drift:** route free-plan Challenge to a stronger model (a pricing and entitlement change), or a Luna-specific verifier pass (the three verifier switches stay OFF).
3. **Stop here on Challenge quality** and leave the free-plan gap as a known limitation, reported honestly.
No measurement-only deploy without separate owner approval; no deployment until the operational gate (the signal-9 kills) clears.

## 7. Reproducibility
Inputs: the 20-note and 100-stock input files, the G+ resource, rubric v2 `3e52c815…`, the scorer instructions, seeds 177, all pinned in the pre-registration; final scoring inputs and their hashes in its Addendum E; the gate report was run once, with no change to a rule, threshold or input after Addendum E, into `target/llm-benchmark-v0177/quality-gates`. The `v0.176.0` evaluation is untouched and was never rescored under v2.
