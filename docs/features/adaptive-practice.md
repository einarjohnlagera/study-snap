# adaptive-practice.md - NoteLib Feature Context

## Goal

Adaptive Practice is the weak-area follow-up quiz mode for a Study Pack-ready note.

It should stay focused on:

- weak concepts from prior performance
- targeted reinforcement
- repeat practice without drifting into unrelated topics

## Current availability

Runtime gating currently treats Adaptive Practice as:

- unavailable on Free
- unavailable on Plus
- available on Pro with a monthly quota

If the user cannot access it:

- use the shared Pro upsell flow for locked access
- use the dedicated limit state when a Pro user has exhausted the monthly quota

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
