# Owner decisions B1–B7 for v0.175.0 (2026-10-19) — verbatim

**Why this file exists:** the owner sent these decisions to Release Implementor on 2026-10-19, answering
questions 4–10 of `docs/claude-plans/2026-10-19-v0174-v0175-open-decisions-product-ux-prompt.md` (Product UX
consulted). They are saved verbatim and unedited. A1–A7 (`2026-10-19-owner-final-decisions-v0174-a1-a7.md`)
are unchanged. **Not authorization for paid calls, production data changes, deployment or expanded
implementation.**

---

Product UX / Feature Planner — Owner Decisions for v0.175.0 (B1–B7)

I've reviewed `2026-10-19-v0174-v0175-open-decisions-product-ux-prompt.md`.

The previous A1–A7 owner decisions remain unchanged. Below are my decisions for the remaining Product UX questions 4–10, corresponding to B1–B7.

These decisions establish the intended learner experience. Please validate them against the existing workflows before finalizing the implementation plan.

### B1 — Preparation Timing: APPROVED WITH CLARIFICATION

Show the preparation screen immediately when question generation is required.

Do not introduce an artificial 10-second blocking wait before showing the learner their options.

The 30-second target represents our desired quiz preparation performance, while 60 seconds is the maximum acceptable period of blocking the learner without a meaningful alternative.

Neither value is a hard timeout for background preparation.

If sufficient eligible questions are already available, skip the preparation screen and proceed through the normal quiz-start flow.

If generation is needed, the learner should immediately understand that their quiz is being prepared and that they may continue reviewing.

### B2 — Learner Closes the Tab: APPROVED

Preparation must continue independently of the browser tab once the learner explicitly requests the quiz.

If the learner closes the tab, navigates away, or returns to their notes without choosing an option, the preparation should continue.

When ready, create one in-app notification linked to that exact prepared quiz.

Do not send email, push notifications, or repeated reminders.

Avoid duplicate preparation jobs or quota consumption if the learner returns while preparation is still running.

### B3 — Prepared Quiz Expiration: 7 DAYS

A prepared but unstarted quiz should remain available for seven days.

After expiration:
- The prepared quiz can no longer be started.
- Its reusable questions should remain eligible for future quiz preparation, subject to existing question-bank rules.
- Opening an expired notification or deep link should show a friendly expiration state, not a generic error.
- Provide a "Prepare New Quiz" action.
- Do not automatically create a replacement quiz merely because the expired link was opened.

The seven-day expiration applies to unstarted preparation, not existing completed or in-progress quiz sessions.

Please verify whether this retention period introduces any meaningful storage or lifecycle concerns.

### B4 — Button Labels and Learner-Facing Copy: APPROVED WITH REFINEMENT

Use distinct language for preparing questions and beginning a timed attempt.

**Initial setup CTA:**
"Prepare Quiz"

This initiates the readiness check. If enough eligible questions already exist, proceed immediately through the normal quiz-start flow without unnecessary preparation screens.

**Preparation screen:**

Title: "Getting your quiz ready"

Body: "We're preparing your questions. You can stay here or continue reviewing while we finish."

Primary action: "Stay on this page"

Secondary action: "Continue reviewing"

If the learner stays, show meaningful preparation status. When ready, automatically transition to the quiz pre-screen, not directly into a running timed attempt.

**Ready pre-screen:**

Title: "Your quiz is ready!"

Body: "Your questions are prepared. Start whenever you're ready."

Primary CTA: "Start Quiz"

The quiz timer must start only after this explicit action.

**In-app notification:**

"Your quiz is ready! Pick up where you left off."

The notification must link to the exact prepared quiz's pre-screen.

**Failure/recovery state:**

Title: "We couldn't finish preparing your quiz"

Body: "Your quiz isn't ready yet. You can try again or return to reviewing."

Actions:
- "Try Again"
- "Continue Reviewing"

**Expired quiz state:**

Title: "This prepared quiz has expired"

Body: "Your prepared quiz is no longer available, but you can create a new one when you're ready."

Primary action: "Prepare New Quiz"

Avoid model names, tier comparisons, claims of guaranteed quality, or wording suggesting FREE learners receive inferior materials.

Please also check whether "Prepare Quiz" is appropriate for untimed quiz modes. If one universal label causes confusion, propose a consistent mode-aware alternative.

### B5 — Board Exam and Long Exam: APPROVE SEPARATE FOLLOW-UP, HIGH PRIORITY

Do not automatically include the complete Board Exam and Long Exam preparation overhaul in v0.175.0.

First validate the Challenge Quiz implementation in production.

However, I consider automatically starting a timed exam when generation finishes a meaningful UX problem.

Please:
- Audit the existing Board Exam and Long Exam timer-start behavior.
- Identify whether a small, independently safe correction can require explicit learner confirmation without the full preparation overhaul.
- If that correction is genuinely low-risk, present it as a separately reviewable change.
- Otherwise, schedule the complete preparation flow for the next appropriate release after v0.175.0.

The intended long-term rule is that no timed exam should start merely because background question preparation has completed.

Do not silently broaden v0.175.0's implementation scope.

### B6 — Missing Official Challenge Question Sets: APPROVE TARGETED PRIORITIZATION

The finding that 432 of 1,997 Official Study Packs lack pre-made Challenge questions is important.

I want first-time Challenge Quizzes on Official content to start quickly whenever feasible.

However, do not immediately approve a bulk paid generation job for all 432 Study Packs.

Instead:
1. Identify which missing sets are actually used or adopted most frequently.
2. Verify that generating their Challenge questions will materially improve first-quiz readiness under the existing eligibility rules.
3. Estimate the one-time generation cost and expected benefit.
4. Propose a targeted batch, prioritizing high-use Official content.
5. Include validation of question quality, answer keys, and completeness.

Present the batch proposal for separate owner approval before any paid generation or production data mutation.

Treat Official question-bank coverage as a complementary performance improvement, not a substitute for reliable asynchronous preparation.

### B7 — Lower Reasoning Effort: APPROVE QUALITY VALIDATION, NOT ROLLOUT

Prioritize a controlled blind quality review of the lower reasoning-effort setting for FREE quiz generation.

The reported improvement from approximately 49 seconds to 28 seconds is significant enough to investigate.

However, faster generation must not compromise:
- Answer-key correctness.
- Question clarity.
- Alignment with the learner's notes.
- Appropriate difficulty.
- Explanation accuracy.

Do not adopt lower reasoning effort based on latency alone.

Prepare a quality evaluation proposal with a clear comparison method, acceptance criteria, and cost estimate.

No additional paid API calls are authorized by this decision. Request separate approval before running new benchmarks.

Keep the current validated reasoning setting until the quality review supports a change.

### Cross-Cutting Requirements

Please preserve these previously agreed principles:

1. Reuse eligible saved questions first.
2. Generate only the required shortfall.
3. Avoid duplicate jobs, duplicate quiz sessions, and double quota charges.
4. Allow learners to continue reviewing while preparation runs.
5. Use a single in-app readiness notification when appropriate.
6. Never automatically start a timed quiz when preparation completes.
7. Preserve quiz progress and history.
8. Handle timeouts and retries safely, checking existing job state before attempting a retry.
9. Never silently downgrade a paying learner to a weaker model.
10. Keep model names out of customer-facing copy.

### Next Steps

Please incorporate B1–B7 into the consolidated v0.174.0/v0.175.0 plan and related release documentation.

Before implementation, provide:

- The finalized learner journey for Challenge Quiz.
- Any conflicts between these decisions and current application behavior.
- A proposed sequence for Board Exam and Long Exam improvements.
- A targeted Official question-bank coverage proposal.
- A separate lower-reasoning-effort quality review proposal.
- Any remaining owner decisions that genuinely block implementation.

Do not execute paid benchmarks, production data changes, deployment, or newly expanded implementation work without my explicit approval.

---

## Owner's additional note (same message)

One product concern I'd flag: Claude's proposed preparation screen has a primary button labeled "Stay on this page," even though the learner is already on that page. I would have Product UX validate whether that needs to be a button at all. A visible progress indicator and a single "Continue Reviewing" action might feel more natural and reduce friction.
