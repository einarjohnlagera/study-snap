# NoteLib — Plans, Pricing & Payments

> **Module — not a standalone brief.** Paste `GPT_CONTEXT.md` first; this file assumes it.
> Paste this module when the conversation is about **pricing, plan tiers, paywalls, or checkout**.
> Last updated: v0.72.1 - 2026-08-11

---

## Plans, Pricing, and Payments

Runtime entitlement source of truth is the backend subscription model and `GET /api/me/plan`.

- Plans: Free, Plus, Pro. Checkout: Xendit hosted checkout via backend `POST /api/payments/create`. Paid access is granted only by validated webhook-confirmed payments.
- Pricing is backend-owned; frontend pricing surfaces use billing/pricing APIs and shared plan config.
- **Paid plans are one-time, time-boxed passes in UI copy, not auto-renewing subscriptions.** This is a load-bearing fact — it's why the "recurring vs. one-time" framing in competitor comparisons doesn't actually apply to NoteLib.
- Cancellation is scheduled for period end; paid access remains active until then.
- Do not add plan flags to `users`. Do not change prices, quota numbers, pass durations, billing, or checkout mechanics as part of readiness/UX work unless explicitly scoped.
- New question *formats* are explicitly kept out of plan-gating (see the Quiz / Practice Mode Contract in `QUIZ_AND_PRACTICE_CONTEXT.md`) — a considered exception, not an oversight.
- **Quota numbers currently live in two independent places that must move together:** backend enforcement defaults (`application.yaml`) and frontend marketing/display copy (`frontend/lib/pricing-config.ts`'s `pricingConfig` object) — the latter is not derived from the former. A quota change that only touches one will silently desync marketing copy from actual enforcement.
- **Pricing itself stays unchanged as of `v0.61.0`** — see the Company Redefinition section in `STRATEGY_AND_ROADMAP_CONTEXT.md` for the full resolution (quota raised now, price deferred pending paywall/retention/usage data). Do not propose a price change as if this is still an open debate; it's been resolved for now, revisit only once that data exists.
- **Plan taglines were re-messaged in `v0.68.0`; plan *names* were not.** `PLUS.title` → "Guided learning built around your notes", `PRO.title` → "Your complete learning system" (from "For regular study" / "Best for exam prep"). `name: "Plus"` / `name: "Pro"` are **untouched everywhere** — checkout, Settings, badges, receipts, support — a deliberate decision to avoid a bifurcated vocabulary, the same reasoning that kept Creator/Curated Learning internal-frame-only. `FREE.title` ("For getting started") is **still owed** to the Messaging Architecture: an outcome-framed candidate was written and reverted in `v0.68.0` because deriving it from Plus/Pro consistency contradicted the ratified **FREE=adopt** tier placement. It must be written against the locked hierarchy, not against its siblings.
- **Four separate components render plan `title`/`description`** — `/pricing`'s `PricingPlansSection`, the landing page's `SimplePricingSection`, `app/settings/page.tsx`, and `components/billing/paywall-modal.tsx`. `v0.68.0` shipped a real misalignment bug because plan `description` lengths had drifted to 62/96/133 characters, which cannot render at equal line counts in a multi-column card grid. They are now balanced at **88/88/92**. If you propose changing a plan description, keep the three within a few characters of each other, or you will silently break card alignment on four surfaces at once.

Upgrade CTA rule:

- Use `getUpgradeCtas(currentPlan)` from `frontend/src/config/plans.ts`.
- Free -> primary `Upgrade to Plus`, secondary `Go Pro`.
- Plus -> primary `Upgrade to Pro`.
- Pro -> no upgrade CTA.

---
