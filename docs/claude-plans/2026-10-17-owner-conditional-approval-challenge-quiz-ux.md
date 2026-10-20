# Owner's conditional approval on v0.174.0 — verbatim

**Why this file exists:** the owner sent this decision directly to this session (Feature Planner); it
never reached Release Implementor's session. Saved verbatim here, unedited, so Release Implementor can
record it in `RELEASES.md` and the `ROADMAP.md` Backlog row from the owner's own words rather than this
session's summary. See `docs/claude-plans/2026-10-16-v0174-llm-tiering-remediation-audit-and-scope.md`
for this session's audit of it (no binding-artifact contradiction found; one forward note on the
"two checks, one destination" principle if workflow-specific routing is adopted for Challenge Quiz).

---

# NoteLib v0.174.0 — Owner Benchmark Decision & Challenge Quiz UX Resolution

Release Implementor,

I reviewed the completed v0.174.0 LLM benchmark and accept its findings, including the limitations of the sample size and blind educational-quality evaluation.

The benchmark supports moving forward with GPT-6 Luna and GPT-6.1 Sol, but I want to address the Challenge Quiz startup latency concern before authorizing the full production rollout.

This is a **conditional approval of the model migration strategy**, not an unconditional approval to deploy.

## 1. Model migration decisions

**APPROVED — FREE**

Adopt `gpt-6-luna` as the target model for FREE Study Pack and initial quiz-pool generation.

**CONDITIONALLY APPROVED — Plus and Pro**

Adopt `gpt-6.1-sol` as the target model for Plus and Pro Study Pack and initial quiz-pool generation.

**CONDITIONALLY APPROVED — Official NoteLib curation**

Use Sol for official curated Study Packs, independently of the administrator's subscription tier, subject to the previously agreed cost caps and monitoring.

Requirements:

- Preserve existing subscription entitlements and generation limits.
- Correct the previously identified PREMIUM model-routing defect.
- Ensure model selection is determined by trusted backend entitlement data.
- Verify model routing for all affected generation paths.
- Do not silently downgrade paid generation to a different model.
- Keep correctness and question reliability as minimum requirements for every subscription tier.

The benchmark establishes compatibility and no observed educational-quality regression. It does not statistically establish that Sol produces more accurate questions than Luna.

## 2. Challenge Quiz startup latency — required investigation and resolution

The benchmark identified a significant potential learner-experience issue.

Production findings indicate that 33 of 68 learner Challenge Quiz sessions required live question generation at startup.

The benchmark's five-question Challenge Quiz test does not represent the actual 10-, 12-, and 15-question startup profiles.

Projected Sol startup waits may become substantially longer than the current experience and could approach the existing request timeout.

I do not want this treated merely as an accepted caveat.

**Treat Challenge Quiz startup latency as a product UX issue that must be addressed as part of the v0.174.0 release decision.**

Please investigate the existing Challenge Quiz service and question-bank architecture.

Specifically:

1. Explain why 33 of 68 Challenge Quiz sessions required live generation at startup despite existing question-bank and Official template reuse mechanisms.

2. Determine whether additional eligible questions can be reused safely from existing question banks or templates.

3. Verify that question reuse respects learner history, question eligibility, difficulty requirements, and avoidance of inappropriate repetition.

4. Evaluate whether generation models should be selected by workflow rather than using the subscription-tier model for every operation.

5. Determine whether Plus/Pro can retain Sol for Study Pack generation while using a different validated approach for latency-sensitive Challenge Quiz startup.

6. Evaluate preparing Challenge Quiz questions before the learner clicks Start, particularly after Quick Review.

7. Assess background preparation risks, including unnecessary generation, quota consumption, duplicate requests, concurrency, stale questions, and learners who never start Challenge Quiz.

8. Investigate whether asynchronous generation, improved question-bank reuse, bounded generation batches, or another simpler solution can reduce startup waits.

9. Define graceful handling of slow generation, timeout, retry, cancellation, and session recovery.

10. Ensure that the quiz timer starts only when questions are ready and that learners do not lose quiz time while waiting.

11. Include the actual startup profiles of 10, 12, and 15 questions, plus multi-note Challenge Quiz sessions.

12. Preserve the existing "+5 Questions" workflow, but verify that it remains safe when live generation is required.

The objective is not merely to make the new model work. The objective is to provide a fast, reliable Challenge Quiz experience regardless of subscription tier.

## 3. Required targeted latency benchmark

Before finalizing the production implementation, propose a small follow-up benchmark that tests actual Challenge Quiz startup workloads.

Include:

- 10-question generation.
- 12-question generation.
- 15-question generation.
- Relevant multi-note generation behavior.
- Luna and Sol, using the exact applicable production request paths.
- Latency distribution, including p50, p90/p95 where sample size supports interpretation, and maximum observed latency.
- Timeout and retry behavior.
- Actual billed tokens and cost.
- Successful accepted-question counts.

Do not use the five-question benchmark as a proxy for the complete Challenge Quiz startup experience.

If a small sample cannot establish tail latency reliably, state that limitation explicitly.

Recommend the smallest practical test budget and obtain owner approval before making additional paid API calls.

## 4. Challenge Quiz UX solution and implementation scope

Please propose the smallest safe solution based on the existing architecture.

Evaluate solutions in this order:

1. Better reuse of existing eligible questions and Official templates.
2. Elimination of unnecessary live generation.
3. Safer generation batching and timeout handling.
4. Workflow-specific model routing, if justified by quality and latency evidence.
5. Bounded background question preparation or pre-generation.
6. Larger asynchronous architecture changes only if simpler approaches are insufficient.

Do not assume background generation is automatically the best solution.

If preparing questions after Quick Review is proposed, evaluate:

- When preparation should begin.
- Which learners and notes qualify.
- How many questions should be prepared.
- How generation quotas are charged.
- How duplicate generation is prevented.
- How generated questions are stored and reused.
- How the process handles failures.
- What happens if the learner never starts Challenge Quiz.
- Whether the approach is compatible with NoteLib's existing infrastructure and operational capacity.

Separate the recommendation into:

**Required for v0.174.0**
- Changes necessary to avoid a regression in Challenge Quiz startup reliability and user experience.

**Optional for v0.174.0**
- Low-risk improvements that can be safely included without delaying the core migration unnecessarily.

**Future enhancement**
- Larger architectural improvements that need their own implementation and evaluation.

Do not defer the underlying learner-experience issue without an explicit mitigation and owner decision.

## 5. Board Exam and Long Exam validation

Confirm the actual production generation paths for Board Exam and Long Exam.

The benchmark exercised a single-call Long Exam method, while production reportedly uses a parallel variant.

Do not directly extrapolate the single-call results to production.

Identify:

- Actual request sizes.
- Model-routing behavior.
- Per-request latency.
- Timeout exposure.
- Retry behavior.
- Parallelism and resource implications.
- Whether the proposed model migration creates any new operational risk.

Include any confirmed model-routing defects in the implementation scope, with appropriate regression tests.

## 6. Reliability, monitoring, and rollback

The release must include adequate operational safeguards.

At minimum, provide:

- Verification of actual model selection for FREE, Plus, Pro, and official curation.
- Generation success and failure monitoring.
- Structured-output and parsing failure monitoring.
- Latency metrics by workflow and model.
- Timeout and retry metrics.
- Billed token and cost monitoring.
- Cost caps or alerts appropriate to current usage.
- Question validation and rejection monitoring.
- A safe rollback mechanism for each affected model route.

Ensure rollback does not corrupt existing Study Packs, question banks, quiz attempts, or learner progress.

Do not assume that agreement between two models proves educational correctness.

## 7. Existing Study Packs and quiz pools

Preserve original user-authored notes and existing learning history.

Do not initiate mass regeneration of Study Packs or question pools as part of this migration.

Continue the previously agreed direction:

- Prioritize confirmed defective questions.
- Evaluate targeted question-pool repair.
- Prioritize frequently reused curated and public content.
- Preserve valid questions.
- Preserve historical quiz attempts and grading records.
- Investigate quiz-only regeneration separately where appropriate.
- Avoid unnecessary generation costs.

Any optional regeneration or refresh experience must be explicitly approved before implementation.

## 8. Product messaging and pricing

Maintain the approved customer-facing direction:

**Enhanced Study Pack Generation**

This may become an additional Plus and Pro benefit after the actual capability has been deployed and its value verified.

Do not expose internal model names on the pricing page or in subscription settings.

Do not claim that Sol guarantees more accurate answers.

Do not imply that FREE users receive unreliable learning materials.

Do not change subscription prices in v0.174.0.

Do not publish in-app or Facebook announcements yet.

## 9. Interview Practice CRITIQUE

Keep `LLM_MODEL_CRITIQUE` outside this migration unless separately evaluated and approved.

The current benchmark does not establish compatibility or feedback quality for Interview Practice.

Do not silently migrate this workflow.

## 10. Release boundaries

Keep v0.173.0 unchanged.

Do not expand the active stability release.

Prepare v0.174.0 according to existing release governance and repository role responsibilities.

Do not:

- Initiate production deployment.
- Execute additional paid API calls without approval.
- Modify production data.
- Regenerate existing user content.
- Change subscription prices.
- Publish customer-facing announcements.
- Introduce major architectural changes without an approved implementation scope.

## 11. Required deliverables

Before requesting final production rollout approval, return:

1. Updated v0.174.0 implementation scope.
2. Confirmed FREE, Plus, Pro, and official curation model-routing matrix.
3. Challenge Quiz startup latency root-cause analysis.
4. Recommended Challenge Quiz UX solution.
5. Comparison of proposed solutions by complexity, cost, latency, and risk.
6. Small targeted benchmark proposal for actual Challenge Quiz startup sizes.
7. Board Exam and Long Exam production-path validation.
8. Monitoring and rollback plan.
9. Explicit v0.174.0 versus future-release scope separation.
10. Remaining owner decisions and implementation dependencies.

## Final owner direction

The intended model strategy remains:

- FREE → Luna.
- Plus/Pro → Sol.
- Official NoteLib curation → Sol with controlled spending.

However, premium should represent an enhanced learning experience, not simply a more expensive model with longer loading times.

**Do not treat Challenge Quiz startup latency as an acceptable unresolved regression.**

Investigate and propose the smallest safe fix, validate the real workloads, and return the revised scope for owner review.

Do not begin additional paid benchmarking or production implementation without the required approvals.
