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

## Escalating Beyond This Checklist

Run this checklist **inline, in the current session** — it already has the Codex prompt, the diff, and the surrounding code in context, so the checklist above is cheap. Do not default to spawning an independent fresh-context agent to redo this audit: a fresh agent has to re-read the prompt, every changed file, and the anti-drift rules from zero before it can even start, and that rediscovery cost — not model tier — is what makes a full independent re-audit expensive (tens of thousands of tokens, several minutes), regardless of which model runs it.

- **Long-mode Codex prompts:** after the checklist above is clean, call `advisor()` once for a second opinion before committing. `advisor()` reads this session's full transcript for free (no rediscovery), so it's the cheap way to get a stronger read on top of the checklist.
- **Short-mode Codex prompts:** the checklist plus a normal build/test pass is enough. Skip `advisor()` unless something specific is bothering you.
- **Escalate past `advisor()` to a fresh, independently-instructed agent** (Agent tool, `model: "opus"`, explicitly told to read the real code rather than trust a summary) only when the diff meets one of the trigger conditions in `CLAUDE.md`'s "Escalate to a fresh, independently-instructed review for hard-to-find bugs" section — a data/metric relationship that shouldn't be mathematically possible, a bug class inherently hard to reason about serially (concurrency, async ordering, lifecycle timing, migrations), or a fix that's about to ship and feeds a real product/business decision. Reuse that section's exact criteria rather than re-deciding them here — don't drift the two lists apart.
- A typical Codex delivery (one PR, additive change, no shared-method collision) needs none of the above beyond the checklist itself. Gate the escalation on the diff's actual risk shape, not on "let's be extra careful this time."

## After the Audit

Fix any gaps, re-run tests, then commit. Do not commit a diff that fails any checked item above.

**Exclude `docs/codex-prompts/*.md` from the commit.** If the prompt that generated this diff was saved there, stage and commit only the Codex-delivered code/doc changes — the prompt file itself is local planning material, not a shipped artifact, and must stay untracked.
