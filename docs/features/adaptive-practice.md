# adaptive-practice.md - NoteLib Feature Context

## Goal

Adaptive Practice is the weak-area follow-up quiz mode for a Study Pack-ready note.

It should stay focused on:

- weak concepts from prior performance
- targeted reinforcement
- repeat practice without drifting into unrelated topics

## Current availability

Adaptive Practice is available on Plus and Pro with a monthly quota per `PLANS.md` (canonical):

- Free: unavailable
- Plus: 10 sessions / month
- Pro: 30 sessions / month

If the user cannot access it:

- Free users: use the shared Plus/Pro upsell flow for locked access
- Plus or Pro users who have exhausted the monthly quota: use the dedicated limit-reached state

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
