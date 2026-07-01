---
name: audit-diff
description: Audit a Codex diff before committing. Checks error states, transactions, idempotency, load-on-refresh behavior, anti-drift compliance, and test coverage. Required after every Long-mode Codex prompt. Use immediately after Codex delivers and before staging any files.
---

You are auditing a Codex delivery for NoteLib. Work through each category below. For each item: confirm it is handled, or flag what is missing.

```bash
git diff HEAD   # review the full diff before starting
```

## 1. Error States

Every user-facing action needs a visible failure path.

- [ ] Network/transient error on load → retry state shown, not a misleading empty or "not found" screen
- [ ] API error on form submit → inline error shown, user's input preserved
- [ ] 404 on a resource → dedicated "not found" message, not a crash
- [ ] Quota exceeded → upgrade prompt shown, not a generic error
- [ ] Async/background failure → user-visible status change, not silent

**Red flag:** a `catch` block that only `console.error`s with no UI state update.

## 2. Transactions and Idempotency

- [ ] Quota check + write in same flow → `@Transactional` on the service method
- [ ] Create flows that should be idempotent → service returns the existing active record, not a duplicate
- [ ] Second write after a first write → rollback behavior defined if the second call fails

**Red flag:** `save()` followed by another service call, no `@Transactional`.

## 3. Load-on-Refresh

If the feature creates a persistent record, it must survive a page reload.

- [ ] New entity is loadable by ID on refresh — not just created in memory
- [ ] GET path is implemented and tested, not just the POST/create path
- [ ] Navigation after creation goes to a URL that actually loads the record

**Red flag:** tests only cover creation, no test for fetching the created entity.

## 4. Frontend State

- [ ] Optimistic updates roll back on API failure
- [ ] Submit/loading states prevent duplicate submissions
- [ ] Modal state resets on close (not on open)
- [ ] `useEffect` dependency arrays are complete — no stale closures

## 5. Anti-Drift Compliance

Cross-reference the original prompt's CONTEXT anti-drift rules:

- [ ] No extra endpoints, fields, or migrations beyond the prompt scope
- [ ] Upgrade CTAs use `getUpgradeCtas(currentPlan)` — no hardcoded copy
- [ ] New analytics events added to `AnalyticsEventType` enum before firing
- [ ] `globalThis` used instead of `window` / `self` / `global`
- [ ] No new chart library, quiz model, or mastery signal

## 6. Test Coverage

- [ ] Happy path tested
- [ ] At least one error/edge state tested per user-facing action
- [ ] If persistent record created — test loads it after creation
- [ ] No DB mocks that hide real integration behavior

## Common Misses by Type

| Type | Most common gap |
|---|---|
| New endpoint | Missing `@Transactional` on quota-check + write |
| Create flow | No test for loading created record on refresh |
| Modal / form | State not reset on close |
| Async job | No user-visible failure state |
| Public page | Anonymous session state persisted (must not be) |

## After the Audit

Fix any gaps, re-run tests, then commit. Do not commit a diff that fails any checked item above.
