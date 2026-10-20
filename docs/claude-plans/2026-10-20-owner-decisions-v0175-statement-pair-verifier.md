# Owner decisions, 2026-10-20: v0.175.0 statement-pair verifier

Saved verbatim as received by Release Implementor on 2026-10-20. These answer the five open decisions in
`docs/claude-plans/2026-10-20-v0175-statement-pair-verifier-product-ux-prompt.md`. The follow-up they ask for
is §10 of `docs/claude-plans/2026-10-20-v0175-statement-pair-verifier-investigation.md`.

**Scope of the approval:** planning and refinement only. Paid calls, implementation, deployment and production
data changes each still need separate approval.

**Product UX's final note, relayed by the owner in the same session:** "don't let the verifier become a
prerequisite for the entire background preparation feature. We should build the preparation experience so
verification can be enabled independently after passing its own quality checks."

---

Release Implementor — Owner Decisions: v0.175.0 Statement-Pair Verifier

I've reviewed the five open decisions in `2026-10-20-v0175-statement-pair-verifier-product-ux-prompt.md`, together with my earlier Option 3 investigation direction.

My priority remains learner trust without compromising quiz readiness, performance or question completeness.

These are my decisions:

### 1. Verification Models — APPROVE OPTION F CONDITIONALLY

Use both Luna and Sol as independent checks for statement-pair questions, applying the same verification policy to FREE and Plus/Pro learners.

Luna checking a Sol-generated question does not constitute a paid-model downgrade because it is an additional validation layer, not a replacement generation model.

However, approval for production activation depends on validation demonstrating acceptable factual-error detection and false-rejection rates.

Do not assume two-model agreement guarantees correctness. Investigate correlated errors, disagreements, ambiguous statements and unsupported source claims.

If two-model verification proves excessively restrictive, return with the measured results and a revised proposal before rollout.

### 2. +5 Questions — APPROVE OPTION (c) CONDITIONALLY

For now, prefer excluding statement-pair questions from newly generated +5 batches.

+5 occurs during an active timed quiz. I do not want additional synchronous verification increasing the learner's mid-quiz wait.

This is a targeted exception to the question-type mix, not permission to reduce question quality or difficulty.

Please validate that the prompt change reliably avoids statement-pair questions and does not create other generation or completeness problems.

Preserve existing R1 consistency protections for any statement-pair questions that still appear.

If the exclusion is unreliable, propose Option (b) as a temporary fallback rather than introducing a longer blocking verification step.

Do not implement the prompt change without review of its exact wording.

### 3. Replacement Question Types — APPROVED

A rejected statement-pair question may be replaced by another appropriate question type.

Requirements:
- Preserve the learning objective and source-note grounding.
- Maintain comparable difficulty.
- Preserve the requested question count wherever feasible.
- Do not replace questions with unrelated or trivially easier material.
- Apply the normal answer-key and schema validation rules.
- Avoid repeated synchronous generation calls that unnecessarily delay quiz startup.

Track the effect on the overall question-type distribution.

### 4. Different AI Provider — FUTURE INVESTIGATION ONLY

Keep cross-provider verification as a future research option.

Do not introduce a new provider in v0.175.0.

Any future proposal must address learner-note privacy, provider data handling, cost, reliability and independently measured accuracy benefits.

Separate owner approval will be required before any integration or transmission of learner notes to another provider.

### 5. Existing Official Question Sets — APPROVED CONDITIONALLY

Once the verifier has passed controlled validation, prepare a read-only recheck of the approximately 890 Official statement-pair questions that previously received only first-pass review.

Produce a reviewed correction list, including evidence for proposed changes and unresolved ambiguous cases.

Do not automatically flip answer keys, regenerate Official content, or modify production records.

The owner must review and separately authorize any production corrections.

Prioritize this because existing Official questions reach substantially more learners than live generation at current traffic levels.

### Rollout Sequence

1. Validate the proposed verifier against known incorrect and independently confirmed correct questions.
2. Measure factual-error detection, false rejections, disagreements, cost and latency.
3. If validation passes, propose activation for Official question-set creation first.
4. Prepare the read-only recheck of existing Official statement-pair questions for separate approval.
5. Activate live Challenge verification only after background preparation and shortfall recovery are ready and validated.
6. Handle +5 separately using the approved conditional approach.

Do not enable every path simultaneously.

### Learner Experience

Verification and question replacement should normally be invisible to learners.

Do not expose model names, verification steps or internal disagreements.

Preserve:
- The requested question count wherever feasible.
- At most one question short under the approved temporary policy.
- No quota charge for failed quiz preparation.
- No automatically started quiz timer.
- No unnecessary blocking verification during active quiz sessions.
- Clear recovery when preparation fails.

### Required Follow-up

Before implementation, please report:

1. Whether Option F creates an unacceptable false-rejection or underfill risk.
2. The proposed acceptance criteria and evaluation cost.
3. The exact +5 prompt change for review.
4. How replacement question types preserve learning objectives and difficulty.
5. Which verifier decisions must be finalized before the first rollout.
6. Any dependencies that could delay the background preparation experience.

These decisions authorize planning and refinement. They do not authorize paid API calls, implementation, deployment or production data changes.

Please update the v0.175.0 planning documents and return with the remaining blockers and recommended implementation sequence.
