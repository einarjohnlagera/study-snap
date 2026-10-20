# Owner direction, 2026-10-20: reconsider Option 3 (independent statement-pair verifier)

Saved verbatim as received by Release Implementor on 2026-10-20. It answers the options in
`docs/claude-findings/2026-10-20-v0175-r1-acceptance-run.md`. The proposal it asks for is
`docs/claude-plans/2026-10-20-v0175-statement-pair-verifier-investigation.md`.

**Scope of the approval:** investigation and planning only. Implementation, deployment, paid API calls and
production data changes each still need separate approval.

---

Release Implementor — Reconsideration of Option 3: Independent Statement-Pair Verification

After reviewing the R1 acceptance findings, I want to reconsider Option 3.

Since v0.173.0 remains gated until October 22, I would prefer to investigate closing the remaining factual-answer-key gap now rather than automatically deferring it.

My product priority is learner trust. A learner should not be marked incorrect because the generated answer key is factually wrong.

### Owner Direction

I approve an immediate technical investigation and implementation proposal for an independent statement-pair answer verifier, with the goal of including it in v0.175.0 if validation supports it.

This is approval to plan and assess, not yet approval to implement or deploy.

### Proposed Scope

1. Start with statement-pair questions generated for Challenge Quiz.
2. Verify both statements independently against the source notes, not merely against the generator's own verdicts.
3. Confirm that the final answer key agrees with the independently established verdicts.
4. Apply the same verification standard to FREE and Plus/Pro learners.
5. Avoid expanding to every question type or quiz mode within this change.
6. Preserve the existing R1 consistency guard as the first validation layer.

### Architecture and UX Requirements

- Prefer verification during background quiz preparation, not an additional blocking step during synchronous startup.
- Investigate whether the verifier can run independently of the generation model.
- Do not assume that using the same model twice produces independent factual judgments.
- A verification disagreement must trigger a safe correction, regeneration, or rejection policy—not silently accept an uncertain key.
- Avoid duplicate quota charges and excessive question omissions.
- Preserve the requirement to deliver the requested question count wherever feasible.
- Do not introduce a regression to Challenge Quiz startup UX.

### Required Investigation

Please propose:

1. The verifier architecture and model options, including the cost per quiz for FREE and paid tiers.
2. How source-note grounding will work, including ambiguous or unsupported statements.
3. How disagreements, uncertainty, and false rejections will be handled.
4. Whether verification should happen before or after background preparation is available.
5. The estimated effect on latency, question completeness, and quota handling.
6. A controlled blind evaluation using previously identified factual-error cases and independently verified correct cases.
7. Clear acceptance criteria, including detection of the three mini failures without unacceptable false rejections.
8. Whether this can safely fit within v0.175.0 without delaying the more important background preparation flow.

### Release Decision

Treat Option 3 as a candidate for v0.175.0, subject to a separate owner go/no-go decision after the investigation.

If the verifier performs well and does not compromise quiz preparation reliability, I would prefer including it now.

If it proves unreliable, too expensive, or incompatible with the preparation flow, retain R1 and schedule verification as follow-up work.

Do not perform paid API calls, begin implementation, deploy, or modify production data without my separate approval.

Please bring the investigation findings, cost estimate, proposed architecture, and recommended implementation sequence back for review.
