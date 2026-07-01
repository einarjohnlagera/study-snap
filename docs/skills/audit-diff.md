# /audit-diff — Post-Codex Delivery Audit

Review a Codex diff before committing. Required for all Long-mode prompts.

Codex is reliable on happy-path implementation and unreliable on edges. This audit takes 5 minutes and catches the bugs that would otherwise reach production.

---

## How to Run

Read the diff, then work through each category below. For each item: mark it checked, or note what's missing and fix it before committing.

```bash
git diff HEAD  # or review the PR diff if already pushed
```

---

## Audit Categories

### 1. Error States

Every user-facing action needs a visible failure path.

- [ ] Network/transient error on load → retry state shown, not a misleading empty or "not found" screen
- [ ] API error on form submit → inline error message, user's input preserved
- [ ] 404 on a resource → dedicated "not found" message, not a crash or blank screen
- [ ] Quota exceeded → upgrade prompt shown, not a generic error
- [ ] Async/background failure (generation, job) → user-visible status change, not silent failure

**Red flag:** a `catch` block that only `console.error`s without updating UI state.

---

### 2. Transactions and Idempotency

Flows that do two writes — or that must survive a retry — need explicit guards.

- [ ] Quota check + write in the same flow → wrapped in `@Transactional`
- [ ] Create flows that should be idempotent → check that the service returns the existing active record rather than creating a duplicate
- [ ] Any flow that calls a second service after the first write → verify rollback behavior if the second call fails

**Red flag:** a service method that calls `save()` and then calls another service, with no transaction annotation.

---

### 3. Load-on-Refresh

If the feature creates a persistent record, it must survive a page reload.

- [ ] New entity (session, link, draft, plan) is loadable by ID on refresh — not just created in memory
- [ ] If a "create or fetch" pattern is used, the GET path is implemented and tested, not just the POST path
- [ ] Navigation after creation goes to a URL that loads the record (e.g., `/collections/:id` not a dead route)

**Red flag:** tests only cover the creation flow, no test for fetching the created entity.

---

### 4. Frontend State Management

- [ ] Optimistic updates have a rollback path if the API call fails
- [ ] Loading/submitting states prevent duplicate submissions (button disabled, spinner shown)
- [ ] State is not leaked between modal opens (reset on close, not on open)
- [ ] `useEffect` dependency arrays are complete — no stale closures

---

### 5. Anti-Drift Compliance

Check the original prompt's CONTEXT section for anti-drift rules. Verify Codex respected them.

- [ ] No new endpoints added that weren't in the prompt
- [ ] No new entity fields or migrations added beyond what was scoped
- [ ] Upgrade CTAs use `getUpgradeCtas(currentPlan)` — no hardcoded copy
- [ ] New analytics events added to `AnalyticsEventType` enum before firing
- [ ] `globalThis` used instead of `window` / `self` / `global`
- [ ] No new chart library, quiz model, or mastery signal introduced

---

### 6. Test Coverage

- [ ] Happy path is tested
- [ ] At least one error/edge state is tested per user-facing action
- [ ] If a persistent record is created, there is a test that loads it after creation
- [ ] No test mocks that hide integration gaps (e.g., mocked DB when real DB behavior matters)

---

## Common Misses by Feature Type

| Feature type | Most common gap |
|---|---|
| New endpoint | Missing `@Transactional` on quota-check + write |
| Create flow | No test for loading the created record on refresh |
| Modal / form | State not reset on close; error cleared too eagerly |
| Async job | No user-visible failure state |
| Multi-step flow | Second write not rolled back if first fails |
| Public page | Anonymous session state persisted (must not be) |

---

## After the Audit

If gaps are found: fix them, re-run tests, then commit.

If everything passes: commit with the message from the Codex output (format: `type: subject\n- bullet\n- bullet`).

Do not skip this audit for Long-mode prompts. The pattern from v0.16.0 shareable quiz links: a 6-bug audit caught missing `@Transactional`, a wrong idempotency guard, a missing GET endpoint, and two frontend catch blocks that destroyed user state on transient errors.
