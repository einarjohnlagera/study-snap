# Price decrease release brief (DRAFT, 2026-11-04, committed 2026-11-06). Prep only: not kicked off, nothing built.

Owner decisions D1 to D6 below were given on 2026-11-04. The working notes this brief was derived from (the RC-4 re-check and the RC-0 checkout read, which found 18 invoices from 14 people of which 4 were the owner's, 0 genuine completions and 1 genuine initiation since 2026-10-09) are not committed; the facts that matter are restated here.

Supersedes RC-4's 8-week experiment framing (owner decision 2026-11-04, agreed with Product UX per the owner): **lower the PH price because a paid learner at a lower price beats no paying learner; raise it later if paying learners and retention justify it.** RC-0 is no longer a gate (its NoteLib-side findings are summarized in the header above). Source spec for mechanics: `docs/claude-plans/2026-10-09-product-health-release-plan.md` section 4.2 to 4.3, re-verified against code on 2026-11-04.

## 1. Decisions (owner, 2026-11-04; defaults accepted with "proceed")
| # | Decision |
|---|---|
| D1 | **Plus ₱99 and Pro ₱149** monthly (30-day pass), **PH only**; USD `DEFAULT` region unchanged |
| D2 | **Hide Pro yearly (₱1,999) and the Pro 90-day exam pass (₱599) for now** via their existing `active` flags, in the same env save as the prices; revisit when the price settles |
| D3 | **`INTRO-PH-*` vouchers stay active and inert** (verified: `PricingService.applyVoucher` returns empty when the override is not below base, so `introEligible` is false and `introAmount` null) |
| D4 | **Plain price, no end date and no "limited time" label**, so a later increase breaks no promise |
| D5 | The old experiment machinery (pre-registered decision rules, 8-week window, guardrail on the generation threshold) is dropped. Keep one count to watch: genuine checkout starts and completions per week |
| D6 | **Sequence:** after Stage B is deployed, watched 72 h and `v0.179.0` is signed off; then its own kickoff |

## 2. Scope: what changes (verified list; line numbers from `origin/main` on 2026-11-04)
**Backend: no code change and no backend deploy of its own.** The price is an env change (owner). Leave `application.yaml` defaults (179/249/1999/599 at lines 370, 383, 387, 391) untouched, so the release does not trigger a second Render deploy; the live env overrides them. The release notes must say the env, not the yaml, carries the price, and that the defaults now disagree with production on purpose.

**Frontend (one PR, about 3 source files plus tests and docs):**
1. `frontend/lib/pricing-config.ts:44-77`: `price.PH.plus.monthly` 99, `price.PH.pro.monthly` 149, `price.PH.pro.yearly` **null**; `intro.PH.plus.monthly` and `intro.PH.pro.monthly` **null**. (The fallback must hide yearly too: `resolveFallbackCycleLabel` and `app/settings/page.tsx:606-626` read `price.PH.pro.yearly` whenever the backend pricing call fails, so hiding yearly only through the env flag would still show ₱1,999 in that case.)
2. `frontend/components/billing/pricing-plans-section.tsx`:
   - **Intro hint `:214-222`:** show an intro line only when the backend says `introEligible` with an `introAmount`; **never fall back to the static intro when the backend call succeeded** (today a successful call with `introEligible=false` still falls through to the static intro: the E19 display bug). Fall back to static intro only when the backend pricing is unavailable, and never render an intro amount that is not below the base price.
   - **`SimplePricingSection` `:413-414` and `:439-440`** ("₱{intro} first 1-month pass / then ₱{price} after"): replace with the plain monthly price line (for example "₱99 / 1-month pass"), no "then ₱X after".
   - **Regional Pricing card `:340-343`:** drop the Pro 1-year line for PH and the "First-pass intro" line; keep Plus and Pro monthly.
   - `resolveFallbackCycleLabel :72-90`: no intro text when intro is null (already handled by `introAmount === null`).
3. `frontend/components/billing/paywall-modal.tsx:63-72`: the intro sentence is already null-guarded; with `intro.PH` null it renders nothing. Add a test, not a code change, unless the null path is wrong.
4. `docs/features/pricing.md` (lines 81, 99, 108, 109 carry the old amounts and the "₱X for your first 1-month pass" format) and `docs/features/billing.md`: update to the new prices and note that Pro yearly and the exam pass are hidden by flag.
5. Tests that assert amounts or intro text: `components/billing/pricing-plans-section.test.tsx`, `components/billing/paywall-modal.test.tsx`, `app/pricing/page.test.tsx`, `app/settings/page.test.tsx` (others matched incidentally on numbers or the word "intro"; to be checked at implementation).

**Out of scope (anti-drift):** no entitlement or quota change, no new tier, no USD change, no per-user pricing, no backend code or migration, no new voucher, no checkout or Xendit change, no copy beyond price surfaces, no Public Library or Explore change.

## 3. Deploy order (frontend and backend are separate integrations; the release must say so)
1. **Frontend PR first** (intro-display gating, plain price lines, fallback values). Correct under the old prices too except the fallback numbers: **the fallback numbers must not go live ahead of the env** or a failing pricing call would show ₱99 while checkout charges ₱179. So ship the gating and copy changes and the fallback values **together with, not before, the env change on the same day**, or ship the fallback values last (spec step 4). Decide at kickoff; the safe pair is: gating-only first, then env, then fallback values within hours.
2. **Owner env save** (one save, one Render redeploy): `PRICING_PH_PLUS_MONTHLY_PRICE=99`, `PRICING_PH_PRO_MONTHLY_PRICE=149` (set the Pro-specific variable, not the legacy `PRICING_PH_MONTHLY_PRICE` fallback at `application.yaml:383`), `PRICING_PH_PRO_YEARLY_ACTIVE=false`, `PRICING_PH_PRO_EXAM_CYCLE_ACTIVE=false`. A Render deploy: off-peak, not within 24 hours of a non-deploy kill or of another deploy, and the kill-after-deploy pattern applies.
3. `scripts/check-deploys.sh` to confirm both platforms serve the intended build (note: it cannot check Render without `RENDER_API_KEY`; say so).
4. **Owner makes one real ₱99 test purchase** (invoice amount, webhook activation, pass end date) and checks the pass length.
5. Pre-rollout `SELECT` on the day: pending invoices (0 on 2026-11-04). An old-price Xendit invoice can stay payable up to 24 hours; 0 pending, so negligible.

## 4. Acceptance (named sweep, then the display check)
- **Price-literal sweep:** no `179`, `249`, `149`, `199`, `1,999` or `599` amount left in frontend source, tests (other than intentional old-price regression cases), `docs/features/pricing.md`, `billing.md`, `docs/product/SPEC.md`; no email template amount (verified none today); no `Offer`/`priceCurrency` structured data (none today).
- **Display check (logged-out `/pricing`, the authenticated paywall, Settings plans, and the backend-failure fallback path):** Plus ₱99 and Pro ₱149 everywhere; no intro line; no "then ₱X after"; no Pro yearly or exam pass shown in PH; USD region unchanged.
- **Real-request test** for any endpoint touched (none expected: no backend change).
- Tests that execute the changed paths: the intro-hint gating (backend eligible, backend not eligible, backend unavailable), the SimplePricingSection lines, the fallback yearly-null path in Settings. A diff that changes display behaviour with no test beside it is not acceptable.

## 5. Risks and how they are handled
- **Env-save redeploy** (kill-after-deploy pattern): off-peak, after Stage B's window, owner-run.
- **Fallback disagreement** between steps: same-day ordering above.
- **Hiding yearly and the exam pass removes a purchase option:** deliberate (D2); the `active` flags are the rollback.
- **Raising the price later** needs only the env and the fallback numbers; no re-billing exists (no auto-renew), no grandfathering, passes already sold run their full term.
- **Margin is not computed** (provider fee, tax, premium-model cost per paid user are unknown); revenue per paid learner drops 45% (Plus) and 40% (Pro) against list. Owner accepted: with 0 genuine paying learners, any payer is net-positive on revenue.
- **Release size:** about 3 source files plus tests and two docs: below the threshold where the pre-signoff gate becomes heavy; one `advisor()` call plus the named sweep should do. A price surface missed by the sweep is the failure mode, so the sweep is an acceptance item, not a hope.

## 6. Next actions (in order; none start before `v0.179.0` is signed off)
1. Kickoff `releases/v0.180.0` (tentative number) on the release branch, first commit, before any code.
2. Write the Codex prompt from this brief (frontend plus docs, about 5 files: Codex per task routing), with the sweep and the three display checks as acceptance.
3. Audit the diff, run the sweep and display checks, one `advisor()` call, owner approval, then the deploy order in section 3.
4. A `[CHECKPOINT]` row for the weekly count (genuine checkout starts and completions), dated at the env change, per the signoff rule.
