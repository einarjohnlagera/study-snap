# G5 reword proposal for the v0.177.0 evaluation

Status: **PROPOSAL, awaiting the owner's decision (Option A, B or C below). Nothing here is signed, frozen or built.** Written 2026-10-28 by the Release Implementor. It authorizes no paid call, no production prompt change and no deployment.

Context: `v0.176.0` item 1 of `v0.177.0`'s Planned Scope (`RELEASES.md`). The frozen `v0.176.0` rubric (`2026-10-27-challenge-quiz-quality-scoring-rubric.md`), scorer instructions and pre-registration are the record and are **not edited**; a changed gate lives in a new v2 rubric file and a new pre-registration, committed before the first paid command of the new run.

## 1. What G5 says today, and why it must change

Rubric line 96: "**G5 Formats:** no format below 3.5 on any dimension with at least 20 items." Implemented in `QualityGateReport.java:295-297` with `QualityGateParameters.G5_FORMAT_MIN_MEAN = 3.5` and `G5_FORMAT_MIN_ITEMS = 20`.

The evaluation report (section 4) records the defect: G5 is "harsh by construction". It reads every dimension, including cognitive demand, which G3 deliberately reports and does not gate. Short TRUE_FALSE items score low on cognitive demand by nature: Sol's **current** arm has 25 TRUE_FALSE items at 3.48 mode alignment and 2.48 cognitive demand, below the same line, although the calculator applies G5 to the proposed arm only. The line therefore fails a format for what the format is, not for what the prompt did.

## 2. Options

**Option A (recommended): gate formats on the gated dimensions, as a non-regression test against the same model's current arm.**
A format with at least 20 items in the proposed arm fails G5 only if, on a gated dimension (distractor plausibility for choice-based formats, mode alignment for every format), its mean is **below 3.5 AND more than 0.2 below the same model's current-arm mean for that format and dimension**. Cognitive demand stays descriptive. A format with fewer than 20 items in either arm is reported and cannot pass or fail G5 (the gate is then INCONCLUSIVE for that format only if it is the only evidence, otherwise it is silent; see "open detail" below).
- Why: it keeps the intent ("the change must not wreck one format") and drops the part that punishes a format's nature. The 0.2 margin reuses the G6 clarity and faithfulness tolerance, so no new tolerance is invented.
- Against: a format already weak on the current prompt cannot fail G5 unless it gets worse, so G5 no longer catches a pre-existing weak format. That is G2 and G3's job on the arm means.

**Option B: keep the 3.5 absolute floor, but only on the dimensions G2 and G3 gate.**
Smallest change (drop cognitive demand from the check). Against: TRUE_FALSE mode alignment sat at 3.48 on the current Sol arm, so the same format can still fail on a line it fails today with no prompt change.

**Option C: drop G5 and report per-format means descriptively.**
Honest about the sample (a format with 20 to 40 items is a noisy mean), but removes the only per-format guard, and the `v0.176.0` rubric lists it as a signed gate.

## 3. Consequences to decide with it

- **Code:** all three options change test-scope code only (`QualityGateReport.java`, `QualityGateParameters.java` and their tests), a small Tier N Codex brief. The offline gate report for the old `v0.176.0` data must still reproduce its recorded results, so the v0.176.0 behavior is kept behind the frozen parameters and the new rule is selected by a rubric-version switch; the brief pins that with the existing recorded numbers.
- **Not re-judging old data:** the `v0.176.0` HOLD stands. It did not rest on G5 (G2 and G3 failed independently, evaluation report section 4).
- **Post-hoc risk, stated:** this change is made after seeing a G5 failure. It is acceptable only because it is committed before any new generated result exists, applies to the new run only, and is reported as an amendment with this file as its record.
- **Luna's G2 bar is high:** G2 needs a mean of at least 4.0 **and** at least +0.3 over the same model's current arm. Luna's current distractor mean is 3.23, so the 4.0 floor needs +0.77. That is the signed gate and this proposal does not touch it; it is flagged so the owner knows what a Luna pass means.

## 4. Open detail for the owner (default in brackets)

- Under Option A, whether a format with 20+ items in the proposed arm but fewer than 20 in the current arm is compared (no baseline) or skipped. [Skipped and reported; a floor with no baseline is the rule that failed today.]
- The cap for the new run. [The owner sets it; `v0.176.0` used $4 for five generation arms. A two-arm-per-model design (current, giveaway-only) is cheaper, roughly $3 to $4 per the report.]

## 5. What happens next once the owner decides

1. The owner picks A, B or C and answers section 4.
2. I write the v2 rubric (a copy with the G5 line and the arm list changed, `v0.176.0` files untouched) and the new pre-registration, and send both for signature before any hash is recorded.
3. One Codex brief for the test-scope change (calculator rule, and the giveaway-only prompt arm of item 2 of the scope). The exact giveaway-only wording goes to the owner **before** the brief.
