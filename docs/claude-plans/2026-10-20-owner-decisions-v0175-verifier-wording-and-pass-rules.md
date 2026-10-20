# Owner decisions, 2026-10-20: v0.175.0 verifier wording and pass rules

Saved verbatim as received by Release Implementor on 2026-10-20. They sign off Parts 1–4 of
`docs/claude-plans/2026-10-20-v0175-statement-pair-verifier-wording-and-pass-rules.md`, with clarifications;
that doc's Part 8 records how each clarification was incorporated.

**Scope:**
- Approved: the wording and the pass rules, with clarifications, and a conditional merge of PR #1514.
- Not approved: the paid Stage 1 run, Stage 2, production activation and deployment.

---

Release Implementor — Final Owner Decisions on v0.175.0 Verifier Wording and Pass Rules

I have reviewed Parts 1–4 of `2026-10-20-v0175-statement-pair-verifier-wording-and-pass-rules.md` and the accompanying investigation.

My decisions are below.

**Part 1 — +5 wording: APPROVED WITH CLARIFICATION**

I approve the proposed +5 prompt wording and the fallback policy.

Keep +5 free from additional blocking verifier calls. Preserve difficulty, concept coverage, and R1 protection for leaked statement-pair questions.

Track statement-pair leakage and enforce the agreed P1–P3 acceptance criteria. Do not claim that the exclusion is guaranteed solely by the prompt.

**Part 2 — Replacement wording: APPROVED WITH CLARIFICATION**

I approve the batched replacement approach and proposed wording.

Please explicitly state that the original rejected statements are untrusted and must never override the provided source material.

Preserve concept, difficulty, reasoning depth, normal validation, and the one-additional-call-per-batch limit.

**Part 3 — Verifier prompt and schema: CONDITIONALLY APPROVED**

I approve the proposed independent verification design, structured schema, and deterministic server comparison rules, subject to these clarifications:

1. When a source note is SILENT, standard established knowledge may be used, but the SILENT classification must remain visible in internal results and reporting. Do not misrepresent those decisions as directly source-supported.
2. When the note contradicts established knowledge, return UNCERTAIN and reject the question for review. Do not silently override the note or alter its content.
3. For absolute-word claims, require a genuine, applicable, established exception under the relevant jurisdiction and standards. Do not invent speculative or outdated exceptions.
4. Preserve checker blindness: neither checker receives the generator's key, answer choices, explanation, or verdicts.
5. Keep the existing fail-closed behavior and reason-code logging.

Please incorporate these clarifications into the exact prompt text before considering Part 3 fully signed off.

**Part 4 — Pass rules: APPROVED WITH CLARIFICATIONS**

I approve the two-stage evaluation and the proposed thresholds, subject to the following:

1. Select the STRICT option for r1-04: reject in at least 2 of 3 repeats, while reporting separately that its original classification is arguable.
2. Any gray-zone result is NOT a pass and must return for owner review.
3. Preserve the distinction between smoke-test acceptance and broader production reliability.
4. The independently verified correct set must retain its reviewer-agreement limitations in reporting.
5. At the production checkpoint, any wrong key among accepted items must trigger immediate investigation and a rollout review. Do not silently continue; recommend whether the relevant verification switch should be disabled.
6. No relaxation of acceptance thresholds after viewing the results without separate owner approval.

**PR #1514 — CONDITIONAL MERGE APPROVAL**

I approve merging the test harness PR after confirming:
- CI and tests pass.
- No production runtime behavior changes are included.
- No production data writes or paid calls occur.
- Gold-set data and API credentials are not committed.

If any condition is unmet, return for review.

**Paid Stage 1 — SEPARATE APPROVAL STILL REQUIRED**

I acknowledge the revised Stage 1 budget of $3.20 ($3.00 Sol + $0.20 Luna).

Do not run it yet.

Finish the gold set, incorporate the wording clarifications, verify the harness and present the final commands and spending limits for explicit authorization.

Stage 2 remains separately gated.

**Release Priority**

Background quiz preparation (Item 2) remains the priority and must not wait for the verifier.

The verifier is enabled in stages, with Official question-set creation first, and live Challenge starts only after background preparation and shortfall recovery have been validated.

Please update the document with these decisions and return only if a material ambiguity or blocker remains. No production activation or deployment is authorized.
