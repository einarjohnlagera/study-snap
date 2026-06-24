# Conversion Funnel Finding — v0.32.2 Thread 3

Source: prod `GET /admin/funnel` (AdminFunnelService), read 2026-06-24. Base: 153 verified users.

## Prod funnel snapshot

| Stage | Value | Read |
|---|---|---|
| Activation (verified → generated a Study Pack) | **68.6%** (105/153) | Healthy |
| Median days to first Study Pack | 0 days | Instant — no activation friction |
| Stuck before generation (notes 7d+, never generated) | 1 user | Negligible |
| Value-loop closure (pack → quiz within 7d) | 58.8% (57/97) | Decent |
| Free quota hit rate (**study-pack only**) | 0.0% (0/153) | Nobody hits the study-pack ceiling |
| Paywall seen → subscribed | 0 of 30 | |
| **Upgrade clicks** | **6** | Intent exists |
| **Checkout initiated** | **0** | ← the wall |
| Paid conversions | 0 | |
| W1→W2 retention | 5.6% (2/36); recent cohorts ~0% | Severe leak |

## Finding 1 (P0): checkout creation is 100% broken

6 users clicked upgrade; **0** reached `CHECKOUT_INITIATED`; 0 paid. Click→checkout = 0.0%.

`CHECKOUT_INITIATED` fires only **after** a Xendit invoice is successfully created — in `PaymentService.create`, the order is `ensureCheckoutConfigured()` → `pricingService.resolveCheckoutSelection()` → `createInvoice()` (Xendit API) → persist pending txn → **then** `trackCheckoutInitiated`. So `create()` is throwing **before the invoice exists** on every click. Candidates, in priority:

1. `ensureCheckoutConfigured()` — Xendit not/mis-configured in prod (API key, callback/return URLs).
2. `createInvoice()` — Xendit API error or null `checkoutUrl` → `PaymentCheckoutUnavailableException`.
3. `resolveCheckoutSelection()` — plan/billing-cycle/pricing resolution error (`CHECKOUT_PLAN_NOT_SUPPORTED` / `CHECKOUT_BILLING_CYCLE_UNAVAILABLE`).

Not the email-verify gate: `UPGRADE_CLICKED` fires *after* the `emailVerifiedAt` check in `PremiumUpgradeButton`, so all 6 clickers were verified and reached `createPremiumCheckoutSession`.

**This is the reason for 0 paying users.** Intent exists; nobody can pay. Pricing, quota size, and Free generosity are downstream of this and irrelevant until it's fixed.

Next step (needs prod): grep prod logs around the 6 upgrade-click timestamps for `billing.checkout`, `billing.xendit`, `PaymentCheckoutUnavailableException`, and the `ensureCheckoutConfigured` failure path; confirm Xendit prod credentials + return/callback URL config.

## Finding 2 (P1): retention is the deeper leak

W1→W2 retention is **5.6%** (2/36), with recent weekly cohorts (Jun 1: 16 users, Jun 8: 9 users) at **0%**. Users activate (68.6%) and engage once (value loop 58.8%), then don't return. Even with checkout fixed, ~0% returning users means almost no one is in-app to convert — a leaky bucket that caps everything downstream. Separate workstream from conversion; likely the larger long-term constraint.

## Answer: is the Free plan too generous? — No.

Intent exists (6 upgrade clicks); the blockers are a **broken checkout** and **near-zero retention**, not Free width. A "Free too generous" failure looks like *zero* upgrade intent, not intent that can't transact. Tightening Free would worsen activation and retention. Do not tighten Free to fix conversion.

## Instrumentation gaps to close

- **Free-quota-hit is study-pack-only.** `getQuotaHitMetrics` only checks `studyPackGenerations >= freeMonthlyStudyPackLimit`. Quiz (5), Adaptive (3), and exam limits are not counted, so "0% free-quota-hit" can't tell us whether users hit *other* walls. Extend it to quiz/adaptive/exam.
- **No `CHECKOUT_FAILED` signal.** The create-checkout error path is silent in analytics (only success fires `CHECKOUT_INITIATED`). A `CHECKOUT_FAILED` event carrying the failure reason would make the P0 self-diagnosing instead of needing log spelunking, and would quantify which of the three failure candidates is firing.

## Re-prioritization recommendation for v0.32.2

- **Promote → P0: fix broken checkout.** Headline of the release; nothing else moves conversion.
- **Promote → P1: retention diagnosis** (why ~0% W1→W2). The bigger bucket leak.
- **Add: instrumentation gaps** above (quota-hit completeness, `CHECKOUT_FAILED`).
- **Keep (secondary, low effort):** quota-label honesty (Thread 2), plan-launch prescreen polish (Thread 1).
- **Defer: Plus-tier exploration** — pointless until someone can actually pay.
