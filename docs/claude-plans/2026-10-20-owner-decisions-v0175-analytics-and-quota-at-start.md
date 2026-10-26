# Owner decisions, 2026-10-20: Challenge analytics at Start Quiz; quota charged only at Start

Saved verbatim as received by Release Implementor on 2026-10-20.

**This supersedes the charge-at-creation half of C4**, in Part G of
`docs/claude-plans/2026-10-19-v0174-v0175-consolidated-implementation-plan.md`, and the matching wording in
`RELEASES.md` v0.175.0 item 2 and the `CLAUDE.md` anti-drift line.
- **New rule:** a Challenge attempt is charged only when Start Quiz succeeds. Preparation, failure, discard and expiry
  never consume quota.
- C4's intent still holds: no charge for a quiz the learner never got.

**Scope:** incorporating both decisions into the v0.175.0 briefs, and proceeding with item 2a. Not approved:
deployment, production data changes, or turning on the verifier.

---

Release Implementor — Owner Decisions for v0.175.0: Analytics and Discarded Quiz Quota

I've reviewed the two outstanding product decisions.

### Decision 1 — Challenge Quiz Started Analytics: APPROVED

Move the "Challenge quiz started" event to the learner's explicit Start Quiz action on the prepared quiz pre-screen.

The event should represent a successfully started timed attempt, not a preparation request or a completed background job.

Requirements:
- Fire once per successfully started session.
- Do not fire when preparation begins, completes, or a readiness notification is opened.
- Avoid duplicates on double-click, refresh, retry, or resume.
- Keep the quiz timer aligned with the successful start transition.
- Document the analytics definition change so historical and post-v0.175.0 metrics are not compared without qualification.
- Where practical, preserve separate preparation-requested, preparation-ready, and preparation-abandoned measurements.

### Decision 2 — Discarded Unstarted Quiz: APPROVE QUOTA REFUND WITH SAFEGUARDS

An unstarted prepared quiz should not consume the learner's monthly Challenge Quiz attempt quota.

A quiz attempt becomes chargeable only after the learner explicitly clicks Start Quiz and the backend successfully begins the timed attempt.

Rules:
1. Preparation requested: no quota consumed.
2. Preparation completed: no quota consumed.
3. Learner continues reviewing: no quota consumed.
4. Learner returns through notification: no quota consumed.
5. Prepared quiz discarded before starting: no quota consumed.
6. Prepared quiz expires before starting: no quota consumed.
7. Preparation fails: no quota consumed.
8. Quiz successfully started: consume one attempt.
9. Quiz abandoned, submitted, or expired after starting: attempt remains consumed, even if zero questions were answered.
10. No quota refunds for voluntarily abandoning an already-started quiz.

If the existing architecture reserves quota during preparation, treat that as a temporary reservation only and release it on discard, failure, or expiration. It must not become a permanent monthly charge unless the quiz successfully starts.

### Abuse and Cost Protection

Please verify the actual question-bank and prepared-session lifecycle rather than assuming all generated questions are reusable.

I want these protections:

- Reuse existing eligible generated questions whenever possible.
- Avoid duplicate preparation jobs and unnecessary repeat model calls.
- Preserve generated questions in the learner's question bank when appropriate.
- Release question claims safely on discard or expiration.
- Apply server-side rate limiting or bounded preparation attempts to prevent repeated generation abuse.
- Ensure concurrent requests cannot exceed quota or trigger duplicate charging.
- Make quota transitions atomic and idempotent.
- Preserve a recoverable state for legitimate learners wherever feasible.

Do not introduce a visible preparation quota or arbitrary punitive cooldown without separate product review.

### UX Copy

For an unstarted prepared quiz, the confirmation should communicate that discarding does not consume monthly quota.

Only promise that generated questions remain saved if the implementation audit confirms they are actually retained and reusable.

### Required Technical Check

Before implementation, please confirm:

1. Whether generated questions are persisted independently of the prepared session.
2. Whether discarded or expired sessions safely release question claims.
3. Whether the current monthly quota is charged during preparation or only at session start.
4. How simultaneous preparation and Start Quiz requests are handled.
5. How repeated prepare/discard cycles are bounded without penalizing normal learners.
6. Whether Board Exam and other supported exam modes can follow the same attempt-start principle.

Please flag any incompatibility before changing quota accounting.

### Implementation Direction

These two product decisions are approved for incorporation into the relevant v0.175.0 implementation briefs.

Proceed with Item 2a according to the previously agreed implementation and review boundaries. Keep the verifier work independent and its production switches OFF until separately approved.

No production deployment or production data changes are authorized by this message.
