# NoteLib v0.174.0 — LLM Model Tiering, Quiz Reliability & Existing Content Remediation (tightened)

**Status: tightened — final, per owner + Product UX, 2026-10-16. Pass-2 input to Feature Planner's
audit/finalize pass; see the companion audit file
`docs/claude-plans/2026-10-16-v0174-llm-tiering-remediation-audit-and-scope.md` for the binding-artifact
check, the deliverable-by-deliverable owner split, and the scope-size challenge.** Saved verbatim here —
this is the source-of-truth text, not a paraphrase.

**Owner's final clarification (2026-10-16, after this text was sent):** quiz-only regeneration
(§"Existing Study Pack regeneration experience" below) stays an **investigation**, not a mandatory
v0.174.0 feature. Reason given verbatim: *"we don't yet know whether NoteLib can safely regenerate the
quiz pool independently of the rest of the Study Pack. Making that a hard release requirement could
unnecessarily delay the model migration."*

---

# NoteLib v0.174.0 — LLM Model Tiering, Quiz Reliability & Existing Content Remediation

## Owner decision

We are tightening the LLM migration strategy around the following product direction:

- **FREE → GPT-6 Luna (`gpt-6-luna`)**
- **Plus → GPT-6.1 Sol (`gpt-6.1-sol`)**
- **Pro → GPT-6.1 Sol (`gpt-6.1-sol`)**

These are the intended model assignments, subject to API compatibility, benchmark results, and verified costs.

Plus and Pro should retain their existing feature and usage-limit differences. Do not invent new subscription entitlements.

The stronger model should become an additional benefit of paid subscriptions, without immediately changing subscription prices.

Our objective is to improve generated learning materials while protecting operating costs, especially because NoteLib currently has no paying users.

## 1. Model routing and subscription entitlements

Investigate and design the correct routing for:

- FREE Study Pack generation.
- Plus Study Pack generation.
- Pro Study Pack generation.
- Initial quiz-pool generation.
- Additional quiz generation, where applicable.
- Regeneration and retry operations.
- Any other learning workflow using the same model resolver.

The existing audit identified a PREMIUM routing defect: FREE and PREMIUM currently use the FREE model during Study Pack generation.

Include its correction in the migration scope, with independently testable acceptance criteria.

Requirements:

- FREE must resolve to Luna.
- Plus and Pro must resolve to Sol.
- Model selection must use the actual subscription entitlement, not client-supplied tier information.
- Existing subscription limits and feature permissions must remain unchanged unless separately approved.
- Model-routing behavior must be testable and observable without exposing sensitive user data.
- Failed or unsupported model calls must have an explicit error or fallback policy; do not silently downgrade paid users.

Determine whether the existing FREE/PREMIUM model resolver can support these assignments without unnecessary new abstractions.

## 2. Pricing and product positioning

Evaluate adding enhanced generation to Plus and Pro subscription benefits.

Proposed positioning:

FREE:
Reliable everyday Study Pack and quiz generation.

Plus:
Enhanced Study Pack and quiz generation using a more capable reasoning model.

Pro:
Enhanced generation with Pro's existing higher limits and advanced entitlements.

Do not advertise guaranteed accuracy or claim Sol is superior without benchmark evidence.

Recommend user-facing pricing-page wording that emphasizes learning benefits rather than model identifiers.

Do not change actual subscription prices in this release.

## 3. Study Pack and quiz generation architecture

Trace the current generation pipeline and explicitly establish:

1. Whether Summary, Key Concepts, and initial Quiz are generated in one LLM request.
2. Whether the initial question pool is generated in the same request or separately.
3. Whether Quick Review selects existing stored questions or generates new questions.
4. Whether Challenge Quiz uses the same pool or another generation path.
5. Which workflows regenerate questions and when.
6. Whether adopted Review Sets and public notes reuse generated Study Packs or generate independent copies.
7. Whether changing the model affects existing Study Packs or only future generation.

Produce a call-path diagram and a model-routing matrix.

Do not assume that a quiz session incurs an LLM call unless the implementation confirms it.

## 4. Revised benchmark requirements

Update the existing benchmark design to evaluate:

- Current `gpt-4.1-mini` baseline.
- `gpt-6-luna`.
- `gpt-6.1-sol`.

Confirm exact API model identifiers, availability, pricing, supported reasoning settings, structured-output behavior, and Responses API compatibility from official documentation.

Test the actual NoteLib request schema and parsing implementation.

Measure:

- Request success rate.
- JSON/schema validity.
- Structural answer-key validity.
- Explanation/key consistency.
- Factual correctness.
- Single-best-answer defensibility.
- Distractor quality.
- Explanation quality.
- Faithfulness to source notes.
- Latency.
- Actual billed tokens, including reasoning tokens.
- Retry and rejection rates.
- Cost per accepted, verified Study Pack and question.

Include independently adjudicated educational-quality cases and known defective-question regression examples.

Keep the statistical limitation explicit: a small benchmark cannot establish that rare production defects have disappeared.

Use a bounded initial benchmark with a proposed maximum API spend of $10, requiring explicit owner approval before execution.

## 5. Existing content remediation

The owner does NOT want blanket regeneration of all notes or Study Packs.

Treat the following as distinct assets:

- Original user-created or uploaded notes.
- Generated Full Notes.
- Generated Summaries.
- Generated Key Concepts.
- Generated quiz pools.
- Historical quiz attempts and learner progress.

Default preservation policy:

- Never overwrite original user-authored notes as part of model migration.
- Preserve accurate Summaries, Key Concepts, and Full Notes.
- Prioritize question-pool validation and repair.
- Regenerate an entire Study Pack only when a confirmed issue requires it.
- Preserve valid questions whenever possible.
- Preserve historical attempts, grading records, and progress.

Investigate whether question-level repair is supported by the current data model.

If not, compare the cost and risk of introducing targeted replacement versus regenerating a complete pool.

Do not silently replace previously attempted questions in a way that changes historical grading.

## 6. Prioritize public collections

NoteLib's curated Review Sets and public note collections are increasingly central to product value.

Propose this remediation order:

**Priority 1 — Confirmed defective questions**
- Wrong answer keys.
- Incorrect explanations.
- Multiple defensible answers.
- Confirmed grading or answer-mapping defects.

**Priority 2 — High-impact public question pools**
- Frequently attempted questions.
- Popular public notes.
- Curated Review Sets.
- High-stakes exam preparation content.

**Priority 3 — Remaining public question pools**
- Use measured defect prevalence and available budget to determine whether further auditing or regeneration is justified.

**Priority 4 — Private user-generated content**
- Preserve existing content by default.
- Provide a safe remediation path for reported defects.
- Do not mass-regenerate private user content.

Determine whether curated public question-pool remediation should use Sol regardless of the accessing learner's subscription tier.

Distinguish content-generation cost from content-access cost.

## 7. Verification and repair policy

Design a cost-controlled reliability pipeline.

At minimum:

1. Generate content with the subscription-appropriate model.
2. Run deterministic schema, structure, answer-index, and explanation-consistency checks.
3. Identify suspicious or high-risk questions.
4. Escalate uncertain cases to additional verification when justified.
5. Reject, quarantine, or repair questions that fail validation.
6. Never serve a known defective question simply because it was generated for FREE.

Evaluate whether Sol should be used selectively as a verifier or repair model for Luna-generated questions.

Do not assume model-to-model agreement proves factual correctness.

Where source notes are themselves wrong or insufficient, flag the source issue rather than generating a confident but unsupported answer.

## 8. Cost and profitability analysis

Use real production token measurements wherever available.

Provide estimated monthly costs for:

- 1,000 Study Pack generations.
- 5,000 Study Pack generations.
- 10,000 Study Pack generations.
- A realistic FREE/Plus/Pro generation mix.
- Luna generation with selective Sol verification.
- Sol generation with retries and reasoning-token overhead.
- Targeted public question-pool remediation.
- Full public pool regeneration as a comparison only.

Include:

- Cost per Study Pack.
- Cost per generated question.
- Cost per accepted verified question.
- Cost per active generating user.
- Potential monthly LLM spend under existing FREE limits.
- Estimated gross margin implications for Plus and Pro using actual subscription prices and limits.

Separate verified costs from assumptions.

Identify whether the current pricing and usage limits sustainably support Sol for Plus and Pro.

Recommend cost caps, monitoring, and alerting, but do not implement new billing controls without approval.

## 9. Existing Study Pack ownership and upgrades

Determine the intended behavior when:

- A FREE user upgrades to Plus or Pro.
- A paid user downgrades to FREE.
- A user copies a public note.
- A user adopts a curated Review Set.
- A Study Pack already exists under an older model.
- A user requests regeneration.
- A shared public note has already been used by multiple learners.

Do not automatically regenerate all existing Study Packs upon upgrade.

Evaluate an explicit regeneration option where supported, with appropriate limits and costs.

Avoid unnecessary duplicate generation of identical reusable content.

## 10. CRITIQUE model migration

The Interview Practice CRITIQUE model is a separate workload.

Evaluate Luna as the default replacement, but require a separate feedback-quality and compatibility assessment.

Do not assume quiz-generation benchmark results validate Interview Practice scoring.

## 11. Retention and quality monitoring

Keep the retention hypothesis separate from confirmed defects.

Investigate feasible signals such as:

- Question reports.
- Failed generation rates.
- Quiz completion.
- Retry behavior.
- Repeat practice.
- Return visits following quiz use.
- Learner feedback about incorrect questions.

Do not claim model migration will improve retention without evidence.

Recommend the smallest useful instrumentation changes, if needed.

## Required deliverables

Return:

1. Revised model-routing matrix for FREE, Plus, and Pro.
2. Confirmed generation and quiz-pool call paths.
3. Updated Luna-versus-Sol benchmark design.
4. Proposed subscription benefit wording.
5. Existing content preservation and remediation policy.
6. Prioritized public question-pool repair plan.
7. Cost and gross-margin scenarios.
8. Upgrade/downgrade and regeneration behavior.
9. v0.174.0 release scope, acceptance criteria, and rollback strategy.
10. Remaining owner decisions and implementation dependencies.

## Execution boundaries

This remains a planning and design task.

- Do not make API calls.
- Do not spend money.
- Do not modify code.
- Do not regenerate content.
- Do not mutate production data.
- Do not change subscription prices.
- Do not initiate a release.
- Do not interfere with ongoing production-stability work.
- Follow repository role charters and release governance.
- Check existing backlog items before creating new work.

## Final strategic direction

NoteLib should provide reliable learning materials to every user while reserving enhanced generation capabilities for paying subscribers.

FREE uses Luna.

Plus and Pro use Sol.

Existing original notes are preserved.

Existing quiz pools are audited and repaired selectively, with curated public collections prioritized.

The goal is not simply to migrate models. It is to improve learner trust, protect operating margins, and strengthen the long-term value of NoteLib's reusable study content.

---

# Additional Owner Decisions — Product Messaging, Regeneration UX & Release Communication

The owner has approved the following product and communication direction for the LLM migration.

## 1. Pricing page and subscription settings

Do not expose internal model names such as GPT-6 Luna or GPT-6.1 Sol in customer-facing pricing comparisons.

Continue using model identifiers internally for routing, diagnostics, and operational monitoring.

For Plus and Pro, evaluate adding the benefit:

**Enhanced Study Pack Generation**

Suggested supporting description:

"Create study materials with enhanced generation capabilities designed for more complex topics and exam-style practice."

Requirements:
- Use consistent terminology on the public pricing page and in-app subscription settings.
- Maintain NoteLib's notes-first, learning-focused positioning.
- Avoid unnecessary references to AI, LLMs, or specific model providers in marketing copy.
- Do not imply FREE users receive inaccurate or unreliable questions.
- Do not promise superior accuracy without benchmark evidence.
- Do not change subscription prices.
- Only advertise enhanced generation after the benefit has been verified and deployed.

## 2. Existing Study Pack regeneration experience

Do not automatically regenerate existing Study Packs during model migration.

Original user notes must remain unchanged.

Evaluate an optional, user-initiated refresh experience for existing generated materials.

Preferred direction:
- Allow users to refresh an existing Study Pack when supported.
- Investigate quiz-pool-only regeneration as a potentially more efficient option.
- Preserve existing quiz attempts, historical results, and learner progress.
- Apply subscription-appropriate generation models.
- Respect existing usage limits and regeneration quotas.
- Clearly explain what will change before the user confirms regeneration.
- Avoid creating duplicate generations unnecessarily.

Do not introduce a new regeneration feature if existing functionality already supports the intended experience safely.

If quiz-only regeneration requires significant architectural changes, document it as a separate release candidate rather than automatically expanding v0.174.0.

**(Owner clarification, 2026-10-16 — supersedes nothing above, just resolves its own open question: quiz-only regeneration stays an investigation, not a mandatory v0.174.0 deliverable. See header.)**

## 3. In-app announcement

After successful deployment and validation, propose a short in-app announcement focused on improved Study Pack generation and learning experience.

Messaging should:
- Explain the learner-facing improvement.
- Reassure users that their original notes remain intact.
- Clarify whether existing Study Packs need any action.
- Mention optional refresh only if the functionality is available and verified.
- Avoid model names and technical implementation details.
- Avoid implying that previous Study Packs were generally inaccurate.

Proposed headline:

"📚 Your Study Packs are getting better!"

Do not publish the announcement before release approval.

## 4. Facebook announcement and re-engagement

Prepare a post-release Facebook announcement focused on helping existing users rediscover their notes and Study Packs.

Core message:

"Your notes. A better study experience."

Position the release as an improvement to NoteLib's learning experience rather than an AI or model upgrade.

Encourage users to revisit notes they are actively studying.

Do not encourage mass regeneration of every existing note or Study Pack.

If optional regeneration is available, explain it as a choice rather than a requirement.

Follow NoteLib's established warm, educational, community-first marketing tone.

## 5. Release communication sequence

Recommend this order:

1. Deploy and validate the new generation pipeline.
2. Monitor generation reliability, question quality, latency, and operating costs.
3. Confirm optional regeneration behavior and quotas.
4. Update the public pricing page and in-app subscription settings.
5. Publish the in-app announcement.
6. Publish the Facebook announcement and re-engagement campaign.

Keep marketing publication outside the automatic technical release process. Require owner approval for final copy and timing.

## Additional deliverables

Include:
- Final recommended pricing-page benefit wording.
- Matching in-app subscription wording.
- Optional regeneration UX recommendation.
- Quiz-only regeneration feasibility assessment.
- Draft in-app announcement.
- Draft Facebook announcement.
- Proposed post-release communication sequence.

These additions refine the existing v0.174.0 plan. They do not authorize implementation, production changes, API spending, or publication.
