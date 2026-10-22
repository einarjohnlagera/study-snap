# Owner decisions, 2026-10-21: v0.175.0 open questions (Q1–Q5) and final product priorities

**Source:** the owner's two messages of 2026-10-21, given to Release Implementor after the Product UX
consultation `docs/claude-plans/2026-10-21-v0175-open-questions-product-ux-prompt.md`. Saved verbatim below.
**These decisions are binding.** Release Implementor's application of them follows the verbatim text.

**Indexing note:** a release artifact (owner decisions), under the Backlog Index's exemption. The v0.175.0
section of `RELEASES.md` points here.

---

## 1. Decisions on the five open questions (verbatim)

> Release Implementor / Feature Planner — Owner Decisions for v0.175.0 Open Questions (October 21)
>
> I've reviewed `2026-10-21-v0175-open-questions-product-ux-prompt.md`.
>
> These are my Product UX decisions for all five questions. Please incorporate them into the relevant planning and release documents.
>
> ### Q1 — Multi-Note Challenge Floor: APPROVE TWO-THIRDS
>
> The proposed 50% minimum is too low for a multi-note Challenge Quiz.
>
> Use these requirements:
>
> 1. Target the full requested question count.
> 2. Require at least two-thirds of requested questions, rounded up, before declaring preparation ready.
> 3. Require representation from at least two-thirds of selected source notes, rounded up, among notes with a positive question allocation.
> 4. Both thresholds must pass.
> 5. If preparation fails either threshold, do not charge quota. Show the existing failure/retry experience.
> 6. If preparation succeeds below the requested count, disclose the actual question count on the ready screen.
> 7. Continue using bounded background shortfall recovery to pursue the requested count before marking the quiz ready.
>
> For example, an 18-question request across six eligible notes needs at least 12 questions from at least four notes.
>
> These are minimum acceptance thresholds, not generation targets.
>
> Before implementing, verify that the source-coverage requirement is feasible for the existing allocation algorithm, including two-note quizzes, short notes, uneven allocations and source-specific failures. Report any conflict rather than silently relaxing the floor.
>
> Keep the thresholds configurable, but do not change them in production without owner approval.
>
> ### Q2 — +5 Statement-Pair Exclusion: OPTION (b)
>
> Keep the exclusion rule behind its own feature switch, OFF by default.
>
> The measured 31/40 completeness result fails the agreed threshold, even though all nine failures were timeouts.
>
> For the paired-test outcomes:
>
> - If the exclusion rule causes timeouts: keep OFF and recommend removing or revising it.
> - If the source notes cause timeouts: keep OFF until validation on the actual deployment model.
> - If inconclusive: keep OFF and return with the uncertainty documented.
>
> Do not enable the rule automatically on merge.
>
> Preserve the existing R1 statement-pair consistency guard.
>
> No additional paid tests are authorized by this decision.
>
> ### Q3 — Replacement Concept Matching: OPTION (C)
>
> Revise the replacement instruction so it targets the actual learning objective tested by the rejected question, not merely its potentially incorrect concept label.
>
> Use the original question and source note to establish the intended concept. Treat the concept label as supporting metadata.
>
> If the concept is ambiguous or cannot be established reliably, reject the replacement rather than inventing a connection.
>
> Preserve comparable difficulty, correct answer keys and source-note grounding.
>
> Keep the original 82.5% concept-match result recorded. Do not reinterpret the existing test as a pass.
>
> Present the exact revised prompt wording for owner sign-off and a new evaluation proposal. The checker remains OFF until the revised replacement behavior is validated.
>
> ### Q4 — Learner-Facing Copy: APPROVED WITH EDITS
>
> Collision titles:
> - Ready: "You already have a quiz ready"
> - Preparing: "Your quiz is still being prepared"
>
> Collision body:
> "You can return to your quiz or start something else. If you start something else, your unstarted quiz will be discarded without using a monthly attempt."
>
> Buttons remain:
> - "Resume your prepared quiz"
> - "Start something else"
>
> Dashboard:
> "Open your prepared quiz"
>
> Notification:
> - Title: "Your quiz is ready!"
> - Body: "Pick up where you left off."
> - Action: "Open your prepared quiz"
>
> Ordinary Challenge setup:
> "This quiz counts toward your monthly limit only when you press Start Quiz. Leaving before you start won't use an attempt."
>
> Redo Missed Questions setup:
> "Redo Missed Questions doesn't count toward your monthly quiz limit."
>
> Please verify that this copy matches the actual quota and discard behavior. Do not claim refunds or saved-question reuse beyond what the backend guarantees.
>
> For underfilled multi-note quizzes, use:
> "Your quiz is ready with X of Y questions."
>
> Display the actual question count and applicable source coverage clearly.
>
> ### Q5 — Slow +5 Generation: PRODUCT DIRECTION APPROVED
>
> The no-silent-downgrade principle applies to +5 as well as initial Challenge generation.
>
> Requirements:
>
> 1. Do not silently route Plus/Pro +5 generation to a weaker model.
> 2. Provide visible loading feedback without an unsupported completion-time promise.
> 3. Target a short interactive wait, ideally under 30 seconds.
> 4. At approximately 45–60 seconds, provide a meaningful way to stop waiting or recover.
> 5. Never discard existing answers or reset quiz progress because +5 is slow.
> 6. Avoid duplicate paid generation calls.
> 7. Audit whether the quiz timer continues running during blocking +5 generation and report the behavior.
> 8. Investigate nonblocking background +5 generation as the preferred long-term approach, including timer fairness, question ordering and safe session updates.
>
> Do not introduce timer pauses, background appends, model-routing exceptions or new hard timeouts without a separate implementation proposal.
>
> ### Release Boundaries
>
> - Item 5b multi-note preparation should proceed with the approved floor and coverage policy after its technical feasibility is confirmed.
> - The verifier may merge with all switches OFF if code review and release checks pass, but no checker activation is authorized.
> - The +5 exclusion switch must remain OFF.
> - The revised replacement prompt requires separate wording approval and validation.
> - Board Exam and Long Exam remain outside this release.
> - No pricing or quota-limit changes.
> - No additional paid API calls, production deployment or production data mutation without separate authorization.
>
> Please record these owner decisions, identify only genuine implementation blockers, and return with the revised replacement wording and any multi-note allocation conflicts before proceeding with those affected changes.

## 2. Final product priorities (verbatim)

> Release Implementor / Feature Planner — NoteLib Owner's Final Product Priorities
>
> I want to simplify our direction before proceeding.
>
> My primary objective is not to implement every possible optimization or safeguard. It is to make NoteLib a trustworthy and smooth study experience.
>
> Please treat these priorities as the guiding principles for v0.174.0 and v0.175.0:
>
> 1. **Study material accuracy:** Generated summaries, key concepts, and quiz content must faithfully reflect the source notes. Avoid invented facts, misleading simplifications, unsupported claims, and important omissions.
>
> 2. **Quiz correctness:** Answer keys and explanations must be accurate and internally consistent. The statement-pair verifier is one protection, not a guarantee that all quiz questions are correct.
>
> 3. **Quiz reliability:** Learners should receive the requested questions wherever feasible. Failed preparation must not consume quota, lose progress, or leave question-bank claims stuck.
>
> 4. **Smooth study flow:** Learners should not be forced to wait unnecessarily for question generation. Preserve background preparation, Continue Reviewing, readiness notifications, and explicit Start Quiz behavior.
>
> 5. **Fairness and sustainability:** Apply consistent correctness standards across FREE and paid learners while avoiding unnecessary model calls, duplicate generation, and uncontrolled costs.
>
> Before requesting further owner decisions, please consolidate the remaining work into:
>
> - **Must fix before release:** Defects that could produce incorrect learning material, broken quiz attempts, lost progress, unfair quota charges, or unusable quiz flows.
> - **Should improve if safe:** Changes that materially improve accuracy, completeness, or preparation speed without destabilizing the release.
> - **Can follow later:** Additional optimizations, optional verification expansion, analytics refinements, and noncritical enhancements.
>
> For every remaining technical question, make a recommended decision yourself based on these priorities. Only escalate to me when there is a meaningful learner-experience, accuracy, cost, or business-policy tradeoff that genuinely requires owner approval.
>
> Do not repeatedly reopen settled decisions unless new evidence materially changes the risk.
>
> Most importantly, provide a concise end-to-end release acceptance checklist that verifies:
>
> - Generated Study Packs remain faithful to source notes.
> - Quiz questions, answer keys, and explanations pass appropriate quality checks.
> - Single-note and multi-note Challenge preparation works reliably.
> - Learners can continue reviewing during preparation.
> - Ready notifications lead to the correct quiz pre-screen.
> - Timers and monthly quota begin only after Start Quiz.
> - Failed, discarded, and expired preparations recover safely.
> - Existing question banks and learning progress remain intact.
>
> Do not claim that quality is guaranteed solely because prompts or automated tests pass.
>
> Please keep the current approved release boundaries. Do not expand scope or execute paid tests, production deployments, or production data changes without the applicable approvals.
>
> I want the next report to focus on learner outcomes, remaining release-blocking risks, and a clear recommendation to proceed or hold—not another long list of implementation options.

## 3. Later decisions (2026-10-21 and 2026-10-22, verbatim where quoted)

- **Item 6 stays in v0.175.0:** "lets just include it this release since we're not in a hurry". Under Q5, the
  approved +5 proposal comes first.
- **+5 proposal approved:** "approve the proposal". That is background +5, the recommended option in
  `docs/claude-plans/2026-10-21-v0175-plus5-slower-models-proposal.md`, including its learner-facing copy.
- **Coverage line approved:** "coverage line is fine". The 5b ready-screen line is "From {n} of {m} notes".
