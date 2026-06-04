# adaptive-practice.md - NoteLib Feature Context

## Goal

Adaptive Practice is the weak-area follow-up quiz mode for a Study Pack-ready note.

Interview Practice is a Pro-only Professional Profile sub-mode of Adaptive Practice. It keeps the `ADAPTIVE` session discriminator and stores `subMode: "INTERVIEW"` in session state.

It should stay focused on:

- weak concepts from prior performance
- targeted reinforcement
- repeat practice without drifting into unrelated topics

## Current availability

Adaptive Practice is available on all learner plans with a monthly quota per `PLANS.md` (canonical):

- Free: 3 sessions / month
- Plus: 10 sessions / month
- Pro: 30 sessions / month

If the user cannot access it:

- Free users who have exhausted their monthly quota: use the shared upgrade flow with "upgrade for more sessions" framing
- Plus or Pro users who have exhausted the monthly quota: use the dedicated limit-reached state
- If the `adaptivePracticeProOnly` kill switch is enabled, lower plans should use the shared unavailable/upgrade flow without claiming the feature is normally Pro-only

## Generation behavior

- Adaptive Practice is LLM-generated
- page load may recover `GENERATING`, `IN_PROGRESS`, or `FAILED` state
- page load must not automatically trigger a new generation request
- new generation starts only from the visible CTA

## Result screen

Primary CTA:

- `Generate New Set`

Secondary actions:

- `Review Answers`
- `← Back to Note`

The result screen should stay focused and should not compete with unrelated actions.

## Session rules

- sessions are note-owned
- generation and resume flow must be idempotent
- active generation uses the shared generation lock
- leaving an active Adaptive Practice session forfeits that session without refunding quota
