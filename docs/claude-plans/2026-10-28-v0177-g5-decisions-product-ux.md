# v0.177.0 G5 decisions (Product UX, relayed by the owner)

Status: **RECORD, verbatim.** Two blocks pasted by the owner into the Release Implementor session on 2026-10-28, in answer to `2026-10-28-challenge-quiz-g5-reword-proposal.md`. I have the owner's relay, not Product UX's original message. Nothing here authorizes a paid run, a production prompt change or a deployment.

## Block 1: Product UX decisions for v0.177.0

**Decision 1 — Option A, APPROVED WITH CLARIFICATION.**

Revise G5 to compare each eligible question format against the same-model current-prompt baseline.

A format fails G5 only when:
- The proposed arm has at least 20 scored items in that format.
- The corresponding current arm also has at least 20 scored items.
- The proposed format mean is below 3.5 on distractor plausibility or mode alignment.
- That same dimension has declined by more than 0.2 points compared with the current arm.

Cognitive demand remains descriptive only and must not participate in G5.

Clarity and faithfulness remain protected by G6; critical correctness defects remain protected by G1.

**Decision 2 — Skip comparison when the baseline has fewer than 20 items, but report it as NOT EVALUATED, not PASS.**

Include the format, current and proposed item counts, and their respective means.

Do not silently count unevaluated formats as passing. If missing baseline coverage materially limits the evaluation or indicates a significant format-distribution shift, flag this for owner review rather than declaring complete evidence.

**Decision 3 — Set the new generation cap to $4 maximum.**

The cap is a spending ceiling, not authorization to execute paid API calls.

Please update the v0.177.0 rubric and test-scope gate calculator accordingly, preserving the existing evaluation design and all other approved gates.

Confirm the revised G5 behavior using offline tests, including boundary cases at exactly 3.5, exactly 0.2 decline, and the 19-versus-20-item baseline scenario.

The previous v0.176.0 results must remain reproducible under their original frozen rubric. Do not retroactively rescore that evaluation using the v0.177.0 rules.

No paid runs, production prompt changes, or deployment are authorized by this decision.

## Block 2: the owner's emphasis

One thing I want to emphasize
Claude also mentioned that Luna's distractor-quality gate (G2) remains unchanged. Its current mean is 3.23, and the proposed prompt must still achieve at least 4.0 and improve by 0.3.

That's important.

We're correcting a gate that unfairly penalizes certain question formats, not lowering the standard for believable distractors.

My recommendation: A, skip with disclosure, $4. This is a reasonable refinement, and I wouldn't reopen the other gates unless the offline tests reveal an actual contradiction.
