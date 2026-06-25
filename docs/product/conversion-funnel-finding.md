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
| **Upgrade clicks** (all-time) | **6** | Low absolute intent |
| **Checkout initiated** (since v0.31.2 only) | **0** | ⚠️ metric-inception artifact — see correction |
| Paid conversions | 0 | |
| W1→W2 retention | 5.6% (2/36); recent cohorts ~0% | **Severe leak — the real constraint** |

## Correction (2026-06-24): checkout is NOT broken

An initial read called "6 upgrade clicks → 0 `CHECKOUT_INITIATED`" a 100%-broken checkout. **That was wrong.** Two pieces of evidence:

1. **Primary:** a live upgrade reached the real Xendit invoice page end-to-end (PHP 249, NoteLib Pro Monthly) — the create-checkout path works.
2. **Git history:** `CHECKOUT_INITIATED` was added in **v0.31.2** (`8935ad43`, whose message even labels the checkout step *"forward-looking"*), while `UPGRADE_CLICKED` is far older (`5e664800`). The dashboard is **all-time**, so it compares an old metric (6 clicks accumulated over many months) against a new one that only counts events since v0.31.2.

So "6 → 0" is a **metric-inception mismatch**, not a failure. The checkout works; there simply hasn't been a serious upgrade attempt *since* the downstream event was wired. Lesson: don't compare all-time funnel stages with different instrumentation start dates.

## Finding 1 (the real constraint): near-zero retention

W1→W2 retention is **5.6%** (2/36), recent weekly cohorts (Jun 1: 16 users, Jun 8: 9 users) at **0%**. Users activate (68.6%) and engage once (value loop 58.8%), then don't return. With ~0% returning users there is almost no one in-app to convert — this caps everything downstream and is unaffected by the checkout misread. **This is the top priority.**

## Finding 2: upgrade demand is low and under-sampled

Only 6 upgrade clicks all-time and 30 paywall views, against 153 verified users. With `CHECKOUT_INITIATED` newly wired and barely any data since, **we cannot yet judge checkout→paid** — it's undersampled, not broken. Need real recent volume (which depends on retention) before reading this stage.

## Answer: is the Free plan too generous? — Still No.

This conclusion never depended on the checkout read. The constraints are **retention** (structural) and **low/undersampled demand**, not Free width. Tightening Free would worsen activation and retention. Do not tighten Free to fix conversion.

## Instrumentation gaps to close

- **Closed in v0.32.2: date-windowed funnel.** Admin Conversion Funnel now defaults event-based stages to a common 30-day window, with 7 / 30 / 90 / all-time options. All-time remains available, but it is no longer the default view that compares old `UPGRADE_CLICKED` data with newer `CHECKOUT_INITIATED` instrumentation.
- **Closed in v0.32.2: quota-hit completeness.** `getQuotaHitMetrics` now reports current-period Free quota hits for Study Packs, Challenge Quiz, Adaptive Practice, Long Exam, Board Exam, and Interview Practice plus an "any quota hit" aggregate. Quota types with a Free limit of `0` are shown as not applicable and excluded from that type's denominator.

## Re-prioritization for v0.32.2

- **Top priority: retention diagnosis** (why ~0% W1→W2) — the real, un-conflicted constraint. Diagnose + one scoped lever.
- **Instrumentation:** date-windowed/cohort funnel (so this artifact can't recur) + quota-hit completeness.
- **Secondary, low-effort:** quota-label honesty; plan-launch prescreen polish.
- **Not a P0:** checkout works — no fix needed; just gather recent data once retention improves.
- **Deferred:** Plus-tier — revisit when there's real conversion data.
