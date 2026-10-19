# Owner decisions, 2026-10-19: v0.174.0 / v0.175.0 split and Challenge Quiz preparation UX — verbatim

**Why this file exists:** the owner sent these decisions to Release Implementor on 2026-10-19 after the
targeted benchmark (`docs/claude-findings/2026-10-19-v0174-targeted-challenge-start-benchmark-results.md`)
and Feature Planner's recommendation
(`docs/claude-plans/2026-10-17-challenge-quiz-latency-root-cause-and-ux-recommendation.md`). They were
agreed with Product UX. Saved verbatim and unedited. **The second set (hybrid preparation UX) is
authoritative wherever it refines the first** (owner instruction). Not authorization to implement or deploy.

---

## Set 1

Release Implementor — Owner decisions for v0.174.0

I've reviewed the updated Challenge Quiz benchmark findings and Feature Planner's recommendations.

My decisions are:

1. CHALLENGE QUIZ STARTUP UX

Target: 30 seconds or less for interactive startup.

Maximum acceptable blocking wait: 60 seconds for both FREE and Plus/Pro.

These are product UX targets, not hard deadlines for background generation jobs.

If generation takes longer, the learner must be able to leave the loading state safely and return when the quiz is ready.

The quiz timer must start only after the questions are ready.

Please define measurable acceptance criteria for readiness, failure recovery, and timeout behavior.

2. SOL TIMEOUT HANDLING

Approved direction: Retry once when safe, then provide a clear recovery state.

Before retrying, check whether the original job completed or persisted its questions.

Prevent duplicate generation, duplicate quota charges, and duplicate quiz sessions.

If recovery fails, show an actionable retry option and preserve the learner's existing progress.

Do not silently downgrade Plus/Pro generation to Luna.

3. RELEASE SPLIT

I approve splitting the expanded work into two releases.

v0.174.0:
- FREE Study Pack generation: Luna.
- Plus/Pro Study Pack generation: Sol.
- Official curation: Sol with spending controls.
- Correct and verify the relevant model-routing defects.
- Add appropriate monitoring, cost controls, and rollback.
- Preserve existing Study Packs and learning progress.
- Do not introduce an unvalidated Challenge Quiz startup latency regression.

v0.175.0:
- Challenge Quiz startup UX improvements.
- Bounded background generation where justified.
- Single-note and multi-note readiness.
- Safe multi-note parallel generation.
- Timeout handling and recovery.
- Duplicate and quota safeguards.
- Validation of tier-specific generation routing for Challenge Quiz.

Please identify the exact temporary routing behavior for Challenge Quiz in v0.174.0 and confirm that it does not conflict with customer-facing subscription claims.

Do not silently substitute a lower-tier model for paid workflows.

4. LOW REASONING EFFORT

Do not adopt Sol's lower reasoning effort until the required blind educational-quality review is completed.

5. DOCUMENTATION

Approved: Commit the benchmark results document, ROADMAP.md pointer, and RELEASES.md entry for PR #1507.

Clearly distinguish completed benchmark findings, approved design decisions, and future implementation.

6. RELEASE SAFETY

No production deployment or additional paid API calls are authorized by this decision.

Please update the release scope, dependencies, acceptance criteria, and remaining risks for owner review before implementation or rollout.

---

## Set 2 (authoritative where it refines Set 1)

Feature Planner / Release Implementor — Additional Owner UX Decisions

Please incorporate the following decisions into the Challenge Quiz startup and background preparation plan.

1. HYBRID PREPARATION EXPERIENCE

Continue prioritizing eligible question-bank reuse. If enough questions are ready, allow the learner to start normally without a preparation wait.

If questions need generation, show a quiz preparation screen with two options:

PRIMARY CTA: "Stay on this page"
SECONDARY ACTION: "Continue reviewing"

The primary action must be visually prominent. The learner should not be encouraged to leave the study flow unnecessarily.

2. STAY ON THIS PAGE

If the learner stays, show preparation progress or status.

When preparation finishes, automatically transition to the quiz pre-screen, not into an active timed quiz.

Do not require the learner to click a notification to continue.

3. CONTINUE REVIEWING

If the learner chooses to continue reviewing, allow them to return to their notes or Study Pack while the quiz prepares in the background.

Generation must continue independently of the browser page.

When preparation completes, create one in-app readiness notification.

Do not introduce email, push notifications, or repeated reminders in this scope.

4. NOTIFICATION DESTINATION

Clicking the notification must navigate to the pre-screen of the exact prepared quiz, not directly into an active quiz.

Preserve the correct note, quiz type, preparation job, question set, and learner ownership.

The learner must explicitly click Start Quiz before the quiz timer begins.

Reuse prepared questions without additional generation or duplicate quota charges.

5. FAILURE AND RECOVERY

If preparation fails or times out, provide a recoverable state.

Do not create duplicate sessions, consume duplicate quotas, or lose existing learning progress.

Handle stale, expired, and already-started preparation links safely.

6. BOARD EXAM AND LONG EXAM

Evaluate the same preparation pattern for Board Exam and Long Exam, accounting for their existing pre-screen and session lifecycle.

Never automatically begin a timed exam because a background job completed or a notification was opened.

7. IMPLEMENTATION BOUNDARIES

Verify whether the current pre-screen, notification infrastructure, and quiz session architecture support this flow.

Prioritize reuse of existing components and services.

Identify any necessary architecture changes, risks, acceptance criteria, and release dependencies before implementation.

This is an approved UX direction, not authorization for implementation or production deployment.

---

## Instruction

Please consolidate both sets of owner decisions into one final implementation plan.

Treat the latest hybrid quiz preparation UX decisions as authoritative wherever they refine earlier recommendations.

Keep v0.174.0 focused on model migration and safe routing, and plan the Challenge Quiz preparation improvements for v0.175.0.

Before implementation, identify any conflicts, unresolved technical dependencies, or decisions requiring my approval.

Do not implement or deploy yet.
