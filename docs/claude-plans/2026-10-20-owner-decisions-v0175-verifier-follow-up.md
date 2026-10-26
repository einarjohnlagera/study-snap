# Owner decisions, 2026-10-20: v0.175.0 statement-pair verifier follow-up

Saved verbatim as received by Release Implementor on 2026-10-20. They answer §10 of
`docs/claude-plans/2026-10-20-v0175-statement-pair-verifier-investigation.md`. The wording, schema and pass rules
they ask for are in `docs/claude-plans/2026-10-20-v0175-statement-pair-verifier-wording-and-pass-rules.md`.

**Scope of the approval:** a local, non-production test harness (decision 4). Nothing else: no deployment,
production data change, paid API call or verifier activation.

---

Release Implementor — Follow-up Decisions for v0.175.0 Statement-Pair Verifier

Thanks for consolidating the remaining items and committing the planning documentation.

Here are my decisions and next steps.

### 1. +5 and Replacement Wording — REVIEW REQUIRED

Please present the exact proposed wording in §§10.3–10.4 for my review.

My intended product behavior remains:

- Avoid generating new statement-pair questions during +5, where feasible.
- Preserve the existing R1 guard for any statement-pair questions that still appear.
- Do not add blocking independent-verifier calls during an active timed quiz.
- Allow replacement of rejected statement-pair questions with another suitable question type.
- Preserve the original learning objective, difficulty and source-note grounding.
- Avoid unnecessary sequential generation calls.

Do not finalize prompt changes until I approve their exact wording.

### 2. Checker Prompt and Response Format — REQUEST DRAFT

Please draft the exact independent-verifier prompt and structured response schema.

The verifier must:
- Judge both statements independently against the source note.
- Not receive the generator's answer key, explanation or verdicts.
- Distinguish supported, contradicted and ambiguous/unsupported claims.
- Provide concise evidence references where feasible.
- Return a machine-readable result suitable for deterministic server validation.
- Treat disagreement or uncertainty as a reason not to automatically accept the question.

Do not implement the checker yet.

### 3. Acceptance Criteria — REQUEST FINAL PASS RULES

Please present the proposed acceptance criteria for owner sign-off before any paid evaluation.

At minimum, evaluate:
- Detection of the three previously identified mini factual-verdict errors.
- False rejection of independently verified correct questions.
- Cases where Luna and Sol agree but are both wrong.
- Ambiguous or unsupported statements.
- Impact on question omissions and the at-most-one-short minimum.
- Estimated added cost and latency.

Please distinguish smoke-test acceptance from evidence of broader reliability.

### 4. Test Harness — APPROVED FOR LOCAL DEVELOPMENT

I approve building a local, non-production test harness for the proposed verifier.

Scope:
- Local evaluation tooling and fixtures.
- Mock-based and deterministic tests.
- No production writes or deployment.
- No paid API calls yet.
- No learner-facing activation.

Please confirm that the harness can run safely without requiring live production data mutation or exposing sensitive learner notes.

After the harness is ready, return with the exact paid evaluation plan and the requested $3.50 spending cap for separate approval.

### Release Boundaries

Item 2, background Challenge Quiz preparation, should continue independently and must not wait for the verifier investigation.

No production deployment, production data changes, paid API calls or verifier activation are authorized by these decisions.

Please present the exact wording and pass rules for review, and report any remaining blockers before implementation.
