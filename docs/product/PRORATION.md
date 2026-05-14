# PRORATION.md — NoteLib Plan Change & Quota Recomputation Design

## Purpose

This document defines how NoteLib handles plan upgrades, downgrades, cancellations, and quota recomputation when a user's plan changes mid-cycle. It is a **design doc** — no implementation starts until this doc is reviewed and decisions are confirmed.

---

## Billing Model Constraints

Before defining proration behavior, the billing model sets hard constraints on what is practical:

- **Manual renewal** — NoteLib has no auto-charge. Users pay when they choose to. A paid subscription is a time-boxed entitlement (30 days for monthly, 365 days for annual).
- **Xendit hosted checkout** — payments go through a Xendit-hosted invoice. There is no Xendit credit or partial-charge API in the current integration.
- **`subscriptions` is the source of truth** — plan state, entitlements, and period boundaries come from the `subscriptions` table, never from `payment_transactions` or frontend state.
- **Quota cycles are bounded** — `user_usage.period_start` and `user_usage.period_end` define the quota window. Usage resets when a new period starts.

These constraints rule out credit-based proration (calculating ₱X remaining on the old plan and deducting it from the new plan price) at v1 — there is no mechanism to issue partial charges or Xendit account credits.

---

## Scenarios

### 1. Upgrade mid-cycle (Free → Plus, Free → Pro, Plus → Pro)

**Most common case.** A user hits a quota limit mid-cycle and decides to upgrade. Example: Plus user has used 40/50 Study Packs and upgrades to Pro on day 15 of their cycle.

**Decision: Fresh 30-day cycle on upgrade, no credit for remaining days.**

Rationale:
- Manual renewal makes this fair — the user is choosing to upgrade early, not being forced to. They get a full new month at the higher tier.
- Credit calculation requires knowing the exact amount paid (not just plan tier — vouchers and intro pricing affect this), a Xendit credit mechanism, and pro-rated math. This is disproportionate complexity for a manual-renewal product at v1.
- Standard behavior for manual-renewal SaaS (no auto-billing = no system-initiated mid-cycle charges).

Behavior:
1. User pays for the new plan via Xendit checkout.
2. On `PAID` webhook: the current `ACTIVE` subscription row is ended (`end_at = now()`). A new `ACTIVE` subscription row is created for the new plan (`start_at = now()`, `end_at = now() + 30 days`).
3. New `user_usage` period starts from the upgrade date (`period_start = upgrade date`, `period_end = upgrade date + 30 days`). All quota counters reset to 0.
4. New plan limits apply immediately.

**What the user experiences:**
- Immediate access to the new plan's features and limits.
- Quota resets to 0 used (a full fresh month of the higher quota).
- No refund or credit for unused days on the old plan — this is communicated clearly in the upgrade flow.

---

### 2. Downgrade mid-cycle (Pro → Plus, Plus → Free)

**Less common** with manual renewal — when a Pro subscription expires, the user simply chooses not to renew at the same tier. True mid-cycle downgrades require explicit user action.

**Decision: No mid-cycle downgrade at v1. Downgrade by not renewing.**

Rationale:
- With manual renewal, the natural path is: paid period expires → access reverts to Free → user decides whether to renew and at what tier. This is already working.
- A mid-cycle downgrade (from Pro to Plus while Pro access is still valid) creates complexity: do quotas recompute immediately? Does the user get Pro features for the rest of the cycle, or Plus features?
- No user demand signal yet — defer until auto-renewal ships, at which point downgrade scheduling becomes necessary.

Behavior at v1:
- "Cancel plan" schedules access to expire at `end_at`. After that, the user falls back to Free.
- To "downgrade" to Plus, the user cancels Pro and buys Plus when Pro expires.
- Settings UI makes this explicit: cancellation shows "Your Pro access continues until [date]. After that, your account moves to Free."

**Future (post auto-renewal):** When auto-renewal ships, introduce "Schedule downgrade" — user selects the new tier, effective at the next renewal date. The current paid period completes at the current tier before the downgrade takes effect.

---

### 3. Same-plan renewal

User re-purchases the same active plan before it expires (e.g., Pro user buys Pro again with 5 days remaining).

**Decision: Extend the existing subscription, do not create a duplicate.**

Current behavior already handles this correctly per `billing.md`:
> If the user renews the same active paid plan, the same active row is extended instead of creating a duplicate active row.

Confirm: quota cycle also extends. `user_usage.period_end` should update to `existing_end_at + 30 days` (stacked renewal), not `now() + 30 days` (which would waste the 5 remaining days).

**Open question (see below):** Should same-plan renewal stack or reset?

---

### 4. Cancellation

User clicks "Cancel plan" in Settings.

**Decision: End-of-period cancellation. No immediate refund at v1.**

Behavior:
1. `subscriptions` row gets `cancel_scheduled_at = now()`.
2. Access continues until `end_at`.
3. On `end_at`, billing job transitions to Free (existing behavior).
4. No Xendit refund is issued at v1. Refunds are handled manually via support if a user requests one.

**Refund policy (to be added to Settings/billing UI):**
> "Cancellation takes effect at the end of your current billing period. We do not offer automatic refunds for unused time, but contact support within 48 hours of purchase if you believe a refund is warranted."

---

## Quota Recomputation Rules

Quota recomputation defines what happens to `user_usage` counters when a plan change occurs.

| Scenario | Quota behavior |
|---|---|
| Upgrade (any → higher) | New `user_usage` period starts on upgrade date. All counters reset to 0. New plan limits apply. |
| Same-plan renewal | `user_usage.period_end` extends. Counters are **not** reset mid-period — they continue accumulating until the period ends naturally. |
| Expiry → Free fallback | Free limits apply immediately. Counters from the paid cycle are preserved for history but Free limits enforce the new cap. |
| Cancellation (scheduled) | No change during the active period. Transition to Free on `end_at` follows the expiry rule above. |
| Mid-cycle downgrade (not supported at v1) | N/A — downgrade is deferred to post auto-renewal. |

---

## Open Questions (resolve before implementation)

1. **Same-plan renewal: stack or reset?**
   - **Stack** (`period_end = old_end_at + 30d`): rewards users who renew early; they don't lose remaining days.
   - **Reset** (`period_end = now() + 30d`, quota resets): simpler; user gets a fresh month regardless of timing.
   - Recommendation: **stack** — losing remaining paid days feels unfair and discourages early renewal. Implement as: if renewing the same plan while still active, `period_end += 30` and do not reset counters until the stacked period end.

2. **Upgrade: should the UI communicate "no credit for remaining days" explicitly?**
   - Recommendation: yes, one line in the upgrade confirmation: *"Your new plan starts today. Your unused [Plus] days will not be credited."*
   - This is especially important for users upgrading with significant time remaining on their current plan.

3. **Annual ↔ monthly plan changes:**
   - A Pro Monthly user upgrading to Pro Annual mid-cycle: same fresh-cycle rule applies (pay annual, get 365 days from today, old monthly cycle ends).
   - A Pro Annual user "downgrading" to Pro Monthly: not supported at v1 (no mid-cycle downgrade).

4. **When is Plus Annual added?** (`billing.md` notes it is not yet available.)
   - If Plus Annual ships before proration is implemented, the same fresh-cycle rule applies.
   - Document here once Plus Annual has a ship date.

---

## What This Unblocks

- **Auto-renewal implementation** — auto-renewal requires clear rules about what happens on renewal (stack or reset). This doc settles that: stack `period_end`, do not reset counters at renewal time.
- **Downgrade scheduling** — the post-auto-renewal downgrade feature has a clear design starting point: schedule tier change for next renewal, no mid-cycle recomputation.
- **Refund policy** — the cancellation section gives a concrete policy that can be surfaced in the Settings/billing UI and support docs.

---

## Not in Scope

- Automated Xendit refund issuance — handled manually via support at v1.
- Credit system (account balance that offsets future purchases) — requires Xendit integration work not justified at v1 usage levels.
- Partial-period charges (e.g., charge only for the remaining days of a new plan) — requires custom checkout amounts, not possible with the current fixed-price Xendit invoice model.
- Family or group plans.

---

## Cross-Reference

- Active billing implementation: `docs/features/billing.md`
- Plan tiers and quotas: `docs/product/PLANS.md`
- Subscription usage enforcement: `docs/features/subscriptions-and-usage-limits.md`
- Roadmap context: `docs/product/ROADMAP.md` §v0.13.0 item 5
