# NoteLib Product Health: Bounded, Sequenced Release Plan

**Date:** 2026-10-09 · **Type:** planning only. No code, git, production, pricing or entitlement changes were made in producing this file, and no database query was run.
**Evidence baseline:** `docs/claude-findings/2026-10-08-product-health-funnel-audit.md` (cited below as "audit §N"). Its findings are cited, not re-derived.
**Additional inputs:**
- Facts gathered for this task on 2026-10-09 by the requesting session (production `SELECT`s and greps), cited as "brief fact".
- A small number of read-only code reads made while writing this plan, cited by `file:line` and marked "MEASURED (this plan)".

**Labels:** the audit's discipline is kept throughout. **MEASURED** means read directly from code or production. **DERIVED** means computed from measured facts. **INFERRED** means plausible but not proven. **UNKNOWN** means the evidence is missing; this plan does not fill those gaps.

---

> ### ⚠️ Stale premise — now RESOLVED by direct confirmation from Release Implementor (2026-10-09)
>
> **The brief said:** "The Study Journey release is already signed off but unmerged. Release Implementor is conducting a narrow pre-merge assessment."
>
> **This was stale, and it is now fully resolved, confirmed directly by Release Implementor in this same working tree:**
> - `v0.167.0` (Study Journey Purpose: Candidate A + B + Civil Engineering) is **merged to `main`** as PR #1481. **Merge commit `c6e93ee4`, actual timestamp 2026-10-08T23:01:13+08:00 (15:01:13 UTC)** — verified via `git log -1 c6e93ee4`. (An earlier draft of this document cited 09:05:05Z from a `gh pr list` field that was not the merge timestamp; Release Implementor caught this and it is corrected here.)
> - **Release Implementor's pre-merge assessment is closed, not open.** They ran a GO/NO-GO check, Product UX reviewed and approved 4 focused pre-merge checks (terminology collision guard, goal-switch safety, access-restriction check, empty-state handling), and Release Implementor re-ran all 4 fresh against live code plus a live read-only production query immediately before merge. **All passed. No material defect found in that pass.** (Two unrelated issues — a privacy leak and an N+1 query — were caught and fixed earlier by the implementing session, before signoff, not during this pre-merge assessment.) Recommendation was MERGE AS SIGNED OFF, and that's what happened.
> - No Study Journey PR is open. Nothing is pending on it.
>
> **⚠️ A second, more consequential discovery from the same exchange: `releases/v0.168.0` is not free.** Release Implementor kicked it off with their user minutes before this session started investigating the quota bug, **for the exact same fix** (`StudyPackUsageService.java:24-29`), independently arrived at from the same audit file. **RC-1 below is already in progress, not a recommendation for the owner to initiate.** See the revised §1, §3 RC-1, §6, §7 and the closing section — all updated to reflect this rather than presenting RC-1 as a pending decision.

---

## 1. Executive Recommendation

**Ship the Study Pack quota-accounting fix first (RC-1), as a small standalone release. In parallel, the owner does the no-code checkout diagnosis (RC-0). The ₱99 / ₱149 price test (RC-4) starts only after both are done and a post-fix paywall baseline exists.**

**⚠️ Update: RC-1 is already in progress, confirmed directly with Release Implementor on 2026-10-09.** They independently reached the same root cause from the same audit and kicked off `releases/v0.168.0` for it minutes before this plan was requested. **This is not a recommendation for the owner to initiate RC-1 — it is already moving.** The owner's own first action is **RC-0** (the checkout diagnosis), which is fully independent, runs in parallel with zero collision risk, and is unclaimed by anyone. The reasoning below for *why RC-1 matters and must precede the price test* still holds — it's the justification for the sequencing in §6/§7, not an instruction to start RC-1.

**Two spec gaps flagged to Release Implementor, worth tracking until they confirm:** (1) this plan's fix uses `notes.copied_at IS NULL` rather than `copied_from_note_id IS NULL`, because the latter's FK is `ON DELETE SET NULL` and would silently miscounted a copy whose source note gets deleted (E6); (2) `ShareService.remixSharedStudyPack` (a different-owner remix) also inserts a zero-cost `study_packs` row the same way copying does (E7) — worth confirming their fix covers it too.

Why this order (the reasoning, not a to-do list — RC-1 itself is already underway):

1. **The quota fix corrects a MEASURED defect that currently drives the dominant paywall.**
   - All 76 real users who hit the Study Pack near-limit paywall had `remaining = 0`.
   - All 76 had copied notes in the prior 30 days (average 81.8 copies), and 75 of 76 had adopted a Study Plan in that window (audit §17).
   - The cause is `StudyPackUsageService.resolveUsage` taking `max(tracked counter, raw study_packs row count)` (`StudyPackUsageService.java:24-29`), where every copied pack is a row.
   - The defect is accidental, not designed: the `max()` shipped 2026-04-02, two months before copy-inserts-a-row shipped on 2026-06-04 (audit §17).
2. **A price test run before the quota fix cannot be interpreted.**
   - The quota fix changes who reaches the paywall at all. Study Pack near-limit accounts for 77 of the 121 paywall viewers (audit §10).
   - Running both together means a change in upgrade or checkout behaviour cannot be attributed to price. A confounded test is worse than no test, because it produces a confident-looking wrong answer.
3. **Checkout is failing for reasons nobody has looked at yet.**
   - 0 of 11 genuine external checkout starters paid. Every one was already at the discounted intro price (₱149 Plus / ₱199 Pro), and all 16 failed invoices ended `EXPIRED` (audit §11).
   - If the Xendit dashboard and the 11-user outreach show a non-price cause (channel, trust, distraction), a price test would measure noise.
   - This reason is for **sequencing** the owner's price decision, not for re-arguing it. The price test happens, after RC-0 and RC-1.
4. **The adoption→return lead (≈10x D7 return, audit §6) should not be amplified until the quota fix ships.** Adoption is both what predicts return and what currently triggers the paywall (audit §17).

**A tradeoff the owner must accept knowingly:** fixing the meter will probably **reduce** paywall-driven upgrade intent, because fewer people will be wrongly pushed to "0 remaining". That thins an already small price-test sample (about 2–3 checkout starters a month, audit §12). It is a reason to size the price test honestly, not a reason to skip the fix. Users are currently paywalled for a defect.

---

## 2. Evidence vs Hypotheses

### 2a. Observed (MEASURED / DERIVED)

| # | Finding | Label | Source |
|---|---|---|---|
| E1 | 16 FAILED `payment_transactions` across 13 users (11 genuine external). All map 1:1 to Xendit `EXPIRED`. All 11 genuine users were charged the intro price (₱149 / ₱199). 0 external completions ever. | MEASURED | audit §11 |
| E2 | Invoice lifetime is 24h (`PaymentService.java:73`). No `payment_methods` restriction is sent (`:338-345`). | MEASURED | audit §11 |
| E3 | The quota counts `max(counter, COUNT(*) study_packs in period)`. Copies insert rows. 76/76 users at the near-limit paywall had copied notes, and 75/76 had adopted a plan. | MEASURED | audit §17; `StudyPackUsageService.java:24-29` |
| E4 | `study_packs` has 12,288 rows, **100% `status = DONE`**. A row is written only on success (`StudyPackService.java:728-732`). Failed or retried generations never insert a row. | MEASURED | brief fact |
| E5 | `incrementStudyPackGeneration` fires only on success (`StudyPackService.java:738-739`). Regenerating a copied note increments it correctly. `recordUsage=false` (`:826-843`) is a deliberate single-charge-per-action design. | MEASURED | brief fact |
| E6 | **`copied_from_note_id` is a column on `notes`, not on `study_packs`** (`NoteEntity.java:71-72`). `copySourceStudyPack` writes no copy marker onto the pack row (`NoteService.java:434-463`). The brief's phrase "exclude `copied_from_note_id IS NOT NULL` rows" therefore needs a join through `study_packs.note_id → notes`. **That column is also not a stable discriminator.** Its FK is `ON DELETE SET NULL` (`V21__note_copy_attribution.sql`, `fk_notes_copied_from_note_id`), so deleting a source note (for example during a Review Set reshape) would null it on every copy and flip those copied packs back into the count. **`notes.copied_at` is stable:** it has no FK, it is set exactly when a non-owner copy or remix is created (`NoteService.java:402`, `ShareService.java:145`), and it is set to null only at creation sites (`NoteService.java:201,396`, `ShareService.java:139`). No other writer exists in code or migrations (grep). | MEASURED (this plan) | code + migration |
| E7 | **Three `StudyPackEntity` creation sites exist:** (1) generation, `StudyPackService.java:698`; (2) copy, `NoteService.java:439`, reached from the new-copy path (`:422`) and from re-attaching a pack to an existing copy (`:366`); (3) **share remix**, `ShareService.java:83`. Remix also writes a row with zero LLM calls. A different-owner remix sets `copiedFromNoteId`; a same-owner remix sets it to null (`ShareService.java:134-145`). | MEASURED (this plan) | code |
| E8 | All quota consumers go through `resolveUsage`: `StudyPackService.assertMonthlyStudyPackQuotaAvailable` (`:1252-1266`), `MePlanService.java:35,134`, which feeds `NoteRegenerationPreflightService.java:122`. The same raw count is also used, **not for quota**, by the weekly summary email at `RetentionService.java:360`, which therefore reports copies as "Study Packs created". | MEASURED (this plan) | grep |
| E9 | Adopting a plan in the first 24h associates with ≈14.6–14.8% D7 return, versus ≈1.3–1.4% without (≈10x). The non-adopter baseline is **2 returners**. | DERIVED, directional | audit §6 |
| E10 | Activity-based return (owner excluded) is 43/26/14 of 417 after 1/7/30 days. `last_login_at` undercounts. | DERIVED | audit §7 |
| E11 | Paywall viewers: 121. Study Pack near-limit: 77. Board Exam lock: 49. Upgrade clickers: 16, of which 11 came via "other", mostly the Plus-only `dashboard_free_plan_card` (`free-plan-upgrade-card.tsx:23-27`). | MEASURED | audit §10, §11 |
| E12 | No anonymous/session ID exists, so anonymous views cannot be joined to signups. `PUBLIC_NOTE_VIEWED` fires server-side per backend fetch (`NoteService.java:1187`). There is no bot telemetry. | MEASURED | audit §2, §3, §19 |
| E13 | **First-touch attribution already exists client-side.** `lib/first-touch-attribution.ts` stores referrer and UTM in `sessionStorage` on the first page of a tab. It is mounted in the root layout (`app/layout.tsx:78`) and submitted only at signup (`app/auth/page.tsx:248,271`), then written to `users.referrer`/`utm_*` (`AuthService.java:1128-1133`). | MEASURED (this plan) | code |
| E14 | 24 `server_failed` restarts since 2026-09-01, on the public SEO surfaces. The `v0.165.0` fix is deployed with 0 restarts since (checkpoint due 2026-10-12T15:24Z). `v0.166.0` is live (checkpoint due 2026-10-14T11:19Z). | MEASURED | audit §18 |
| E15 | `estimated_cost` is NULL on every row because the upstream value is null. No $/call figure exists. | MEASURED | audit §17; brief fact |
| E16 | No auto-renewal exists. 0 real paying subscribers (the only ACTIVE PRO row is the owner's comp grant). 0 `PENDING` transactions as of 2026-10-09. | MEASURED | brief fact (the PENDING count is a snapshot; re-check at rollout) |
| E17 | **Pending-invoice reuse is price-safe in NoteLib.** `canReusePendingTransaction` refuses reuse unless both `amount` and `originalAmount` match the current selection (`PaymentService.java:388-403`). A stale pending row is marked FAILED and replaced (`:134`). | MEASURED (this plan) | code |
| E18 | `INTRO-PH-*` is an `OVERRIDE_PRICE` voucher. If the override price is no longer below base, `applyVoucher` returns empty (`PricingService.java:287-307`), so no overcharge is possible. | MEASURED | brief fact |
| E19 | **The intro-price display is not gated by backend eligibility on several surfaces:** `pricing-plans-section.tsx:343` and `:413-414`/`:439-440` render `pricingConfig.intro.PH` and `"then ₱{price} after"` straight from static config. The fallback hints at `:214-223` fall through to the static intro amount whenever the backend says `introEligible = false`. `paywall-modal.tsx:63-72` does the same in its fallback label. After a base cut to ₱99 these would read **"₱149 first 1-month pass · then ₱99 after"**. | MEASURED (this plan) | code |
| E20 | **The price ladder inverts under the test prices.** 3 × ₱149 = ₱447, which is below the ₱599/90-day exam pass (the `/pricing` primary CTA, `pricing-plans-section.tsx:144-212`). 12 × ₱149 = ₱1,788, which is below the ₱1,999/yr pass. | DERIVED | audit §12 + owner prices |
| E21 | No payment-provider fee percentage is documented anywhere in the repo. | MEASURED (absence) | brief fact |
| E22 | Spend controls: there is no global spend ceiling, no per-user $ cap and no circuit breaker. `AiRateLimitService` is in-memory and per-process (5/min Free, 20/min paid) and resets on restart. | MEASURED | audit §14 |

### 2b. Hypotheses (INFERRED, or untested effects)

| # | Hypothesis | Label | What would test it |
|---|---|---|---|
| H1 | Checkout abandonment is driven by channel friction, trust or distraction rather than price. | INFERRED (audit §11, §20 E1) | RC-0: Xendit dashboard plus the 11-user outreach |
| H2 | A lower price increases genuine completed payments. | Untested; the audit found no event that isolates price (§12) | RC-4, read as counts, not significance |
| H3 | Prompting first-24h plan adoption *causes* higher return, rather than engaged users self-selecting into adoption. | INFERRED, correlational (audit §6, rule 1) | RC-5 (Public Note → Review Set link), pre/post |
| H4 | The 30-minute copy and discovery intent TTL silently loses intents. | INFERRED, frequency UNKNOWN (audit §8) | RC-6, with an `*_INTENT_EXPIRED` count |
| H5 | Fixing the quota meter will raise `STUDY_PACK_GENERATED` among adopters and lower near-limit paywall views. | Expected effect, untested (audit §20 E4) | RC-1 window |
| H6 | The audit's 2.8M tokens/month figure is an upper bound. Realized demand will be much lower, because most of the 76 blocked users are no longer active (only 14/417 show activity after 30 days, audit §7). | INFERRED | RC-1 weekly monitor |
| H7 | `PUBLIC_NOTE_VIEWED` counts Next.js server fetches, and ISR caching breaks the one-fetch-per-visitor link. Its user agent would be the server's, not the visitor's, so user-agent tagging on that event cannot separate humans from bots. | INFERRED | Not worth building (see §5) |
| H8 | The PH board-exam calendar shifts demand independently of price, which confounds a pre/post test. | INFERRED | The owner knows the exam calendar; the test window is chosen with it in mind |

### 2c. UNKNOWN (and what this plan does about each)

The plan does not fill these gaps. For each it states the consequence and who can close it.

- **The Xendit fee percentage and any VAT treatment.** Margin cannot be computed in pesos. Owner input; §4 gives the formula only.
- **Per-token LLM pricing.** No peso cost per tier can be stated. Owner input (OpenAI dashboard); §4 gives token envelopes only.
- **Live Render env price values** (audit §2, §12). Confirm before RC-4; this is an owner read.
- ~~The exact env var names for PH prices~~. **Resolved (MEASURED, this plan):**
  - `application.yaml:332` reads `${PRICING_PH_PLUS_MONTHLY_PRICE:179}`.
  - `:345` reads `${PRICING_PH_PRO_MONTHLY_PRICE:${PRICING_PH_MONTHLY_PRICE:249}}`. **Pro falls back to the legacy `PRICING_PH_MONTHLY_PRICE`** when the Pro-specific variable is unset.
  - So the owner must set `PRICING_PH_PRO_MONTHLY_PRICE` explicitly, rather than assume which variable is live. The live values are still UNKNOWN.
- **The original purpose of the `max()`** in `resolveUsage`. The commit message says "unify backend/frontend usage calculations" (audit §17). This is why RC-1 keeps the `max()` and filters copies out, rather than removing it.
- **Whether a study_packs ⋈ notes count on `(owner_user_id, created_at)` is index-supported.** RC-1 acceptance requires an `EXPLAIN` in the PostgreSQL harness.
- **Whether a Xendit invoice URL issued at the old price stays payable** after the base price changes, until its 24h expiry. INFERRED yes. Negligible today because 0 are PENDING (E16), but re-check at rollout.
- **The human share of anonymous traffic** (audit §3). It stays unknown under this plan. See §5 for how the funnel avoids depending on it.

---

## 3. Prioritized Release Candidates

Each candidate is a separate release or owner action. **None is combined with another.** The priority order is the execution order.

### RC-0: Checkout diagnosis (owner action, no code, not a release)

- **Objective:** separate the five checkout stages (initiated, attempted, failed, expired, completed) for the 16 invoices, and learn why 11 genuine users did not finish. This is done **before** any recovery automation and **before** the price test.
- **Scope:**
  1. **Xendit dashboard pull for all 16 invoices.** Did the user open the hosted page? Which channel did they pick? How many attempts were made inside the invoice, and were any declined? How long were they on the page?
  2. **One-question outreach to the 11 genuine users:** "What stopped you from finishing payment?" Optionally offer a short reason list: price / didn't have GCash-Maya-card / didn't trust the page / got distracted / just looking.
  3. **Classify each user into audit §11's causes (a)–(e), or "no reply".**
- **Stage definitions** (they are not interchangeable):

  | Stage | NoteLib source | Observable today? |
  |---|---|---|
  | Checkout initiated | `payment_transactions` row created. `CHECKOUT_INITIATED` exists only from 2026-06-24 (audit §11). | **Yes**, from the table. Treat the event as secondary. |
  | Payment attempted | Nothing in NoteLib | **No.** Xendit dashboard only |
  | Payment failed | No invoice-level decline webhook. A declined in-invoice attempt likely leaves the invoice open (INFERRED, audit §11). | **No.** Xendit dashboard only |
  | Invoice expired | `webhook_events` `EXPIRED` | Yes |
  | Payment completed | `PAID` webhook → `SUBSCRIPTION_STARTED` (`SubscriptionService.java:230`) | Yes |

  ⚠️ **NoteLib's `FAILED` status is not "payment failed".** It covers both an expired invoice and a stale pending row superseded by a new checkout (`PaymentService.java:134`). Never report `FAILED` count as "failed payments".
- **Exclusions:**
  - No recovery email.
  - No resume state.
  - No code.
  - No assumption that `EXPIRED` means rejection.
- **Acceptance criteria:** a per-invoice table (no emails in any committed file) recording channel or attempt data where Xendit has it, plus an outreach reply rate and reasons. This written result decides RC-3's scope.
- **Dependencies:** none. It can start today.
- **Risks:**
  - Low reply rate. Eight of the 11 show no activity after day 1 (audit §11).
  - Xendit may not retain page-level detail.
  - Either outcome is still informative: it bounds what RC-3 can be.

### RC-1: Study Pack quota accounting fix — ⚠️ ALREADY IN PROGRESS on `v0.168.0` (Release Implementor, confirmed 2026-10-09)

**The scope below is kept as a reference spec, not an instruction to implement.** Release Implementor independently found this same bug from the same audit and kicked off `releases/v0.168.0` for it before this plan existed. Two differences from what Release Implementor may have built were flagged to them directly: use `notes.copied_at IS NULL`, not `copied_from_note_id IS NULL` (FK is `ON DELETE SET NULL`, E6), and confirm `ShareService.remixSharedStudyPack`'s different-owner remix path is covered too (E7). Everything below documents what the correct fix looks like for the owner's and Product UX's own verification, not as a second implementation track.

- **Objective:** copying a note (including Study Plan adoption and share remix) stops spending the Study Pack generation quota. Genuine generation and regeneration, including on copied notes, keep counting. No uncontrolled generation is introduced.
- **How each action is counted:**

  | Action | Inserts `study_packs` row? | Increments tracked counter? | Counted today | Counted after fix |
  |---|---|---|---|---|
  | Reusing an existing Study Pack (open, Quick Review) | No | No | No | No |
  | Copying a note whose source has a pack (`NoteService.java:422`, `:366`) | **Yes** (`:439`, `createdAt = now`) | No | **Yes, via row count (defect)** | **No** |
  | Share remix (`ShareService.java:83`) | **Yes** | No | **Yes (defect)** | No for a different-owner remix. **Still yes for a same-owner remix** (known limitation, rare) |
  | First generation on any note, including a copied note with no pack (`StudyPackService.java:698`) | Yes | **Yes** (`:738-739`) | Yes | **Yes, via the counter** even though the row is excluded |
  | Regeneration (in place) | No (row updated) | Yes | Yes | Yes |
  | Failed or retried generation | **No** (rows only on DONE; 100% of 12,288 rows are DONE, E4) | No | No | No |
  | Combined-scope regeneration with interim `recordUsage=false` (`:826-843`) | n/a | Once, on the final step | Once | Once |

- **Scope — two candidate fixes, both verified workable; the choice is Release Implementor's, confirmed in direct exchange on 2026-10-09:**
  - **Option A (this plan's original spec): join and exclude copies.** In `StudyPackUsageService.resolveUsage`, replace the raw `countByOwnerUserIdAndCreatedAt…` with a count of the user's `study_packs` rows in the period **whose note has `copied_at IS NULL`** (a join through `study_packs.note_id → notes.id`), keeping the `max()` as a backstop against whatever it was originally guarding.
    - Use `copied_at`, not `copied_from_note_id` — the latter is nulled by `ON DELETE SET NULL` when a source note is deleted, which would silently re-count copies (E6, verified by Release Implementor independently).
    - **⚠️ Known narrow gap in Option A, found by Release Implementor:** a same-owner remix of your *own* shared pack (`ShareService.createRemixedNote`'s `ownerUserId.equals(source.getOwnerUserId())` branch) also sets `copiedAt = null` — the same pattern as the existing self-copy exemption, so Option A would still (correctly, by the existing exemption's own logic, but worth naming) not catch that case as a "copy" either. Narrow in practice (how often someone remixes their own share link), logged rather than blocking.
  - **Option B (Release Implementor's refinement, found while verifying Option A — likely cleaner): drop the `max()` entirely and use the tracked generation counter alone.** `trackedUsage.studyPackGenerations()` is incremented at exactly one call site (`StudyPackService.java:739`, the real LLM-generation path) and is never touched by `copyNote`, `copySourceStudyPack`, or `remixSharedStudyPack` (verified). This sidesteps the whole copy/remix discriminator question — including the Option A gap above — for free, and touches one file instead of three.
    - **Before committing to Option B: confirm why the `max()` was added** (its purpose is UNKNOWN, audit §17 — the commit message says only "unify backend/frontend usage calculations"). If it exists to guard against some other path that creates a real generation row without calling `incrementStudyPackGeneration`, dropping it blind would open a new gap rather than close one. Release Implementor is checking this before deciding.
  - Every consumer of quota usage already goes through `resolveUsage` (E8), so either option is a single fix point.
- **Historical implications:**
  - Usage is computed on read, so **no backfill, no migration and no production write are needed**.
  - `user_usage` counters are already correct and stay untouched.
  - **At deploy, every user's used count in the current billing period drops to their real generation count, so their headroom becomes cap minus that count.** This is the intended effect, not a data change.
  - Historical `PAYWALL_VIEWED` rows remain as they were. Every pre/post comparison must split at the deploy timestamp.
- **Exclusions:**
  - No change to quota limits, copy behaviour, the tracked counter, Challenge Quiz's own `max()` (`DashboardService.java:760-772`), `RetentionService.java:360`'s weekly-email count (log it as a separate, smaller copy-accuracy follow-up), or pricing.
  - No global spend breaker build. It is gated as an owner checkpoint instead (§8).
- **Acceptance criteria:**
  1. **A test against real PostgreSQL rows,** not Mockito. Seed one original-generation row and one copied-note row in the period and assert a used count of 1. Seed a copied note whose source had no pack, generate on it, and assert it counts through the counter. **Delete a copy's source note and assert the copy still does not count.** This is the `ON DELETE SET NULL` case from E6. CLAUDE.md is explicit that `PREPARE` does not verify predicates and that mocked repositories hide them.
  2. An `EXPLAIN` of the new count in the harness, with the index situation recorded.
  3. Confirm the frontend does not recompute remaining Study Packs locally. Grep the near-limit banner and paywall paths; the expected source is `MePlanService`.
  4. The feature docs covering the Study Pack quota and billing are updated, along with `RELEASES.md`.
  5. A read-only monitoring SQL file is committed under `docs/claude-plans/` for the post-deploy watch (below).
- **Post-deploy watch** (4 weeks, read-only `SELECT`s, owner excluded):
  - Weekly near-limit `PAYWALL_VIEWED` distinct users (expected to drop).
  - Weekly original-generation `study_packs` rows and `STUDY_PACK_GENERATED` (expected to rise).
  - Count of users at `remaining = 0` whose real generation count is below the cap (expected 0). A non-zero count is the audit's reject condition (§20 E4).
- **Guardrail:**
  - The per-user cap is unchanged, so the restored headroom is bounded per user by construction (10 Free / 50 Plus / 100 Pro, audit §13).
  - **Before ship, the owner sets a weekly non-owner generation threshold.** Exceeding it triggers revert. This is the minimum stand-in for the missing global ceiling (E22).
  - The audit's 76 × 10 × ~3,700 ≈ 2.8M tokens/month is the DERIVED upper bound. Realized demand is expected to be far lower (H6).
- **Dependencies:** none remaining — `v0.168.0` is already kicked off and this fix is already in progress on it (Release Implementor). Independent of Study Journey and of the stability checkpoints, because it touches an authenticated path and adds no anonymous load.
- **Risks:**
  - The join adds cost to every quota read. It is bounded to one user and one period, and acceptance #2 covers it.
  - The same-owner remix is still counted.
  - Fewer paywall views thin the price-test sample (§1).
  - **Routing is the owner's call.** CLAUDE.md's table allows Claude Code to implement "isolated bug fixes in 1–3 files with a clear root cause" directly. This fix is one service plus one repository query plus tests, so it plausibly qualifies. Either way, run `advisor()` before implementation.

### RC-2: Minimal funnel attribution (entry surface at signup)

- **Objective:** know which surface (landing / public note / explore / exam hub / other) each signup's first-touch tab started on. This adds no anonymous server state and no per-view DB load.
- **Scope:** add the first-touch page's **pathname class**, and optionally the raw path truncated, to the existing `sessionStorage` first-touch object (`first-touch-attribution.ts:47-57`). Submit it with the existing signup payload and persist it once at signup. The storage choice is the owner's (§8): a `users.first_touch_surface` column (needs a migration) or a `SIGNUP_COMPLETED` metadata key (no migration, but that event covers only 311/420 historic accounts, audit §19 #6).
- **Exclusions:**
  - No anonymous ID or cookie.
  - No new server write on public pages.
  - No user-agent classification.
  - No change to `PUBLIC_NOTE_VIEWED`.
- **Acceptance criteria:**
  - A MockMvc signup test with the new field.
  - A `lib/api-*.test.ts` request-shape test.
  - A signup with no stored attribution still succeeds (the existing `try/catch` posture).
- **Dependencies:** none (independent of RC-1 and Study Journey). It sits after RC-1 only because RC-1 is a measured defect.
- **Risks:**
  - `sessionStorage` is per tab, so cross-tab and cross-day signups read as unknown. That is consistent with the existing referrer limits (audit §2), not a new error.
  - Whether extending an existing client-only `sessionStorage` capture counts as "persisting anonymous session state" under CLAUDE.md's public-page rule is something the owner should confirm (§8). The mechanism already runs on every page today (E13).

### RC-3: Checkout completion repair (conditional on RC-0)

- **Objective:** fix whatever RC-0 shows.
- **Candidate scope**, chosen from RC-0's result rather than in advance:
  - (a) A "payment not completed — resume or choose another method" state on `failure_redirect_url` return.
  - (b) One reminder email about 1h after an unpaid invoice (audit §20 E1).
  - (c) Enabling or featuring a missing channel. This is an owner action in Xendit.
  - (d) Recording a distinct terminal reason on `payment_transactions` (expired vs superseded) so `FAILED` stops conflating them.
- **Exclusions:**
  - No automated multi-step recovery campaign.
  - No discounting as a recovery lever.
- **Acceptance criteria:**
  - A MockMvc test for any new endpoint.
  - No duplicate activations (webhook dedupe already exists, audit §11).
- **Dependencies:** RC-0 written result.
- **Must ship before or after the RC-4 window, never inside it.** A recovery change inside the price window confounds it the same way RC-1 would.
- **Risks:**
  - Building recovery for a cause RC-0 did not find.
  - Email deliverability is UNKNOWN.

### RC-4: ₱99 Plus / ₱149 Pro price test (owner decision is final; this designs the safe version)

The full specification is in §4.

- **Objective:** measure whether the lower price produces genuine completed payments, as raw counts in a fixed window.
- **Scope:**
  - Owner sets the env override.
  - A frontend display fix that gates the intro display on backend eligibility and suppresses any intro ≥ base.
  - Update the `pricing-config.ts` fallback.
  - An owner-run voucher SQL file, if retiring `INTRO-PH-*`.
  - Owner decisions on the exam pass and yearly pass (§8).
- **Exclusions:**
  - No entitlement or quota change.
  - No new tier, Unlimited or Lifetime.
  - No USD/`DEFAULT` region change.
  - No per-user randomization.
- **Acceptance criteria (named price-surface sweep):** before launch, run a price-literal sweep across every surface that shows a price, not only the ones already known.
  - **Already swept by this plan:**
    - The backend source tree contains no `₱` literal.
    - `application.yaml:332,345` holds the env defaults.
    - The frontend `src`, `lib`, `app` and `components` trees contain no ₱-prefixed literal; `pricing-config.ts` is the only static source.
    - The `app/settings/page.tsx:636-646` labels are backend-derived.
    - No `Offer`/`priceCurrency` structured data was found under `app/pricing`.
  - **Still to sweep at implementation:**
    - `docs/features/pricing.md` and `docs/features/billing.md` (docs must change with the price).
    - Any email template that renders an amount.
    - Any structured data outside `app/pricing`.
  - **Then run the §4.5 guardrail-5 display check.**
- **Dependencies:**
  - RC-0 done.
  - RC-1 deployed plus a ≥4-week baseline.
  - RC-3 either shipped with its own window closed, or explicitly deferred.
  - The `v0.165.0`/`v0.166.0` checkpoints closed clean.
- **Risks:**
  - An underpowered result.
  - Seasonality (H8).
  - Display mismatch (E19).
  - Ladder inversion (E20).
  - Margin cannot be computed (E21).

### RC-5: Public Note → "Part of Official Review Set X" link (the minimal adoption/return experiment)

- **Objective:** test H3 causally (audit §20 E2) by closing the §8 gap. Today a public note never names the plan it belongs to.
- **Scope:**
  - **One bounded, indexed lookup per public-note render** (note → at most one Official Review Set), cached with the page's ISR.
  - Reuse `v0.167.0`'s server-side exam-slug resolution where it applies, without changing it.
- **Evaluation of the opportunity:** high potential, because public notes are the dominant entry surface (DERIVED, audit §3) and the strongest retention lead is adoption (audit §6). It is also the highest-risk surface for infrastructure: the restart chain was caused by "walk the catalog, re-rank in JS" discovery rails on exactly these pages (audit §18).
- **Exclusions:**
  - **Not part of `v0.167.0`.** `v0.167.0` did not ship it (checked against `RELEASES.md`), so there is no duplication. It is not added to any Study Journey release unless Release Implementor independently flags it as an approved-scope defect.
  - No unbounded discovery and no multi-set ranking.
  - No change to Study Journey architecture, Degree hierarchy or Academic Term semantics.
- **Acceptance criteria:**
  - At most one statement per render, proven by `EXPLAIN` and by a statement-count test.
  - No new per-view analytics write.
  - Click-through is measured with the existing `EXPLORE_OFFICIAL_SET_ADOPT_CLICKED`/`STUDY_PLAN_ADOPTED` vocabulary, or one new authenticated-side source tag.
- **Dependencies:**
  - **RC-1 shipped**, otherwise it pushes new adopters into the defective paywall (audit §17).
  - The 2026-10-12 and 2026-10-14 stability checkpoints closed clean.
  - Not concurrent with RC-4 unless the owner accepts the confound (§8).
- **Risks:**
  - Load on a fragile surface.
  - An approximately 25-week observation horizon at about 4 signups a week (audit §20 E2).

### RC-6: Intent TTL and flashcards redirect (audit §20 E3)

- **Objective:** stop silently losing copy and adopt intents.
- **Scope:**
  - Raise `COPY_INTENT_COOKIE_MAX_AGE_SECONDS` (`lib/public-note-copy.ts:3`) and the discovery-intent TTL (`lib/discovery-intent.ts:2`).
  - Route the flashcards intent correctly (`verify-email/page.tsx:72`).
  - Add an authenticated-side `*_INTENT_EXPIRED` event.
- **Exclusions:** no new onboarding.
- **Acceptance criteria:** a frontend test per intent path.
- **Dependencies:** none technical. It is behaviour-affecting on the adoption path, so it should not land inside the RC-4 or RC-5 windows.
- **Risks:**
  - Low value if expiries are actually zero. The audit's reject rule covers this.
  - These are existing cookies whose lifetime is extended, not new anonymous state, but say so in the release.

### Part F: Production and cost guardrails across all candidates

| Candidate | New anonymous traffic or DB load | LLM spend exposure | Rate limit / abuse | Minimum telemetry | Stability dependency |
|---|---|---|---|---|---|
| RC-0 | None | None | n/a | Xendit export (owner) | None |
| RC-1 | None. Authenticated quota reads gain a bounded join. | **Rises**, bounded per user by the unchanged caps. No global ceiling (E22). | Existing `AiRateLimitService`. It is in-memory and resets on restart, so it is not a spend control. | Weekly read-only generation counts. `estimated_cost` stays unpopulated. | None |
| RC-2 | **None** (client `sessionStorage` + one signup write) | None | n/a | The new signup field | None |
| RC-3 | None (authenticated) | None | Reminder email: one per invoice, max | Terminal reason on the transaction | Checkout path only |
| RC-4 | None | Per paid user unchanged (same caps). Revenue per sale is lower. | n/a | Existing `payment_transactions`, `webhook_events`, `SUBSCRIPTION_STARTED` | **Yes** (§6) |
| RC-5 | **One bounded lookup per render, ISR-cached.** The only candidate that adds anonymous-path load. | Indirect (more adopters, then more generation after RC-1) | n/a | Existing adopt events | **Yes**, hard gate |
| RC-6 | None | None | n/a | `*_INTENT_EXPIRED` | None |

**No candidate introduces an unlimited-generation promise.** A real global spend breaker remains a prerequisite for any generosity or Unlimited move (audit §14). It is in LATER as its own release, not folded into RC-1.

---

## 4. Pricing Experiment Specification

### 4.1 Design choice: time-bounded public price test, not a controlled cohort

| Option | Feasible? | Why |
|---|---|---|
| **Randomized cohort (A/B)** | **No** | About 2–3 genuine checkout starters a month and 0 completions ever (audit §11, §12). Two arms would take many months to show anything, and the audit already said a 2-arm test "would mostly measure noise" (§12). No per-user price assignment exists; pricing resolves by region (`pricingRegions`). A voucher-based cohort would need new assignment code. Two prices for the same pass in a small, connected PH board-exam reviewer community is a trust risk (INFERRED). |
| **Time-bounded public price test (pre/post)** | **Yes, with honest limits** | One price for everyone. Reversible with an env change. The comparator is a post-RC-1 baseline window. **It cannot produce a statistically conclusive result at this n.** It produces counts against pre-registered decision rules. |

### 4.2 What changes (displayed price is not sufficient)

| Layer | Current | During test | Who | Note |
|---|---|---|---|---|
| Backend PH base price (`PRICING_PH_PLUS_MONTHLY_PRICE`, `PRICING_PH_PRO_MONTHLY_PRICE`; `application.yaml:332,345`) | Plus ₱179/30d, Pro ₱249/30d (code defaults; **live env UNKNOWN**) | Plus ₱99, Pro ₱149 | **Owner** (Render env var; CLAUDE.md puts env changes in the owner's class) | This is what the checkout amount follows. Set the Pro-specific variable explicitly (legacy fallback at `:345`). |
| `INTRO-PH-*` vouchers (`V52`) | Plus ₱149, Pro ₱199 override | Retire (owner SQL file), or leave inert | **Owner** (production write) | Inert is safe: `applyVoucher` returns empty when override ≥ base (E18). Retiring removes ambiguity. |
| Frontend static fallback `pricing-config.ts:44-77` | `price.PH` 179/249; `intro.PH` 149/199 | `price.PH` 99/149; `intro.PH` null | Code (PR) | It disagrees with the backend if left alone |
| **Intro display** (`pricing-plans-section.tsx:214-223, 343, 413-414, 439-440`; `paywall-modal.tsx:63-72`) | Static intro is rendered regardless of backend eligibility | Gate on backend `introEligible`, and never render an intro amount ≥ base | Code (PR) | **Without this the page reads "₱149 first pass · then ₱99 after" (E19)** |
| Exam pass ₱599/90d, yearly ₱1,999 | Live | **Owner decision** (§8): keep (now dominated by monthly), pause for the window, or reprice | Owner | E20 inversion. This plan invents no new cycle prices. |
| USD `DEFAULT` region | 3.99 / 4.99 | Unchanged | — | The test is PH-only |
| Entitlements and quotas | Audit §13 | **Unchanged** | — | Owner decision #1 |

**Deploy ordering.** Frontend and backend deploy through independent integrations; the release must state this ordering.

1. Ship the **intro-display gating fix first.** It is correct under both old and new prices, so it is safe on its own.
2. The owner runs the voucher SQL, if retiring.
3. The owner changes the env price, triggering a Render redeploy.
4. Ship the `pricing-config.ts` fallback values. The mismatch between steps 3 and 4 is visible only when the backend pricing call fails. Keep it to the same day.
5. Run `scripts/check-deploys.sh` to confirm both platforms serve the intended build.
6. **The owner makes one real ₱99 test purchase** to confirm that invoice amount, webhook activation and pass end-date are correct at the new amount. The owner's earlier ₱149 test is the precedent (audit §11).

### 4.3 Billing, renewal disclosure, existing subscribers, pending invoices

- **Recurring billing:** none exists (E16). A price change re-bills nobody. It only changes what the next manually started checkout shows.
- **Renewal-price disclosure:** the existing copy already says passes never auto-renew (`pricing-plans-section.tsx:364-366`; `app/refund/page.tsx:39`).
  - Required change: remove the "then ₱X after" lines, which become wrong (`:413-414`, `:439-440`).
  - Owner decision (§8): whether to label the price as time-limited ("price for passes bought before <date>"). If labelled, the end date must be honoured.
- **Existing subscribers / grandfathering:** **0 genuine paying subscribers** (E16). Nobody needs grandfathering. Rollback policy for passes bought *during* the test is in §4.6.
- **Pending invoices:** 0 PENDING today (snapshot, E16). NoteLib will not reuse an old-price pending row (E17). An old-price Xendit invoice URL may still be payable until its 24h expiry (INFERRED). **Pre-rollout check:** re-run the `PENDING` `SELECT` on the day of the env change.

### 4.4 Margin and LLM exposure (formula only; inputs UNKNOWN)

**Contribution per pass = price − provider fee(price) − taxes (UNKNOWN) − LLM cost(usage).**

- **Provider fee:** UNKNOWN (E21). No Xendit percentage is assumed. Owner input.
- **LLM cost in pesos:** UNKNOWN (E15).
  - **Token envelopes (DERIVED):** cheap-tier Study Pack generation averages about 2,820 input / 877 output tokens (audit §17). Paid plans use the **premium** model for Study Packs (`StudyPackService.java:721-723`). The premium average is UNKNOWN (n = 1 non-owner row).
  - **Worst-case shape per month** (audit §13 caps):
    - Plus: 50 Study Packs + 10 Adaptive + 10 multi-note.
    - Pro: 100 Study Packs + 30 Adaptive + 12 Long Exam + 10 Board Exam.
  - **The test does not change this exposure per paid user. It lowers revenue per pass against it.**
- **Revenue per pass:**
  - Plus: ₱99 vs ₱179 list (−44.7%) or ₱149 intro (−33.6%).
  - Pro: ₱149 vs ₱249 list (−40.2%) or ₱199 intro (−25.1%).
  - To match intro-price revenue the test needs about 1.51x as many Plus payers and about 1.34x as many Pro payers (DERIVED).
  - The current baseline is ₱0 external revenue (audit §17), so any genuine payer is net-positive on revenue. Whether each payer is net-positive on **contribution** cannot be stated until fee and per-token cost are supplied.

### 4.5 Metrics, decision rules, guardrails

**Window:** 8 weeks from the env change. Owner excluded throughout.

**Interim checks** at +2 and +4 weeks review **guardrails only**, not efficacy. Do not stop early for a good result.

**Primary metric:** genuine external **completed payments**, counted as `SUCCESS` `payment_transactions` with a `SUBSCRIPTION_STARTED` row.

**Secondary metrics** (counts, compared with the ≥4-week post-RC-1 baseline, normalized per week):
- Distinct `PAYWALL_VIEWED` users.
- Distinct `UPGRADE_CLICKED` users.
- Genuine checkout initiations (from `payment_transactions`).
- Checkout initiations ÷ upgrade clickers.
- Plan mix (Plus/Pro).
- Billing-cycle mix.

**Pre-registered decision rules** (counts, not significance):

| Outcome at 8 weeks | Reading | Action |
|---|---|---|
| ≥ 2 genuine completions | Positive signal, not proof (pre/post, seasonality H8) | Keep the test price and extend 8 weeks to check repeatability |
| Exactly 1 completion | Inconclusive-positive | Extend once (max), then decide |
| 0 completions and ≥ 5 genuine initiations | The lower price did not unblock checkout. Price is not the binding constraint, consistent with audit §12. | Owner chooses revert or keep. Return to RC-0/RC-3 findings. |
| 0 completions and < 5 initiations | Underpowered, inconclusive | Extend once (max). Then revert or keep by owner preference, and **record the result as "no evidence either way"** |

**The most likely outcome is the inconclusive row.** At about 2–3 genuine checkout starters a month (audit §12), and possibly fewer once RC-1 removes the false near-limit paywall, an 8-week window most likely ends with "0 completions and fewer than 5 initiations." The owner should expect that result going in. It would not be a failure of the test design; it is what the traffic allows.

**Do not claim** "₱99 converts X% better". At this n, a reported percentage would overstate precision. Report counts with exact (Wilson) intervals if any rate is shown.

**Guardrails.** Breaching any one triggers rollback review.

1. Weekly non-owner generation count stays at or below the owner-set threshold (shared with RC-1).
2. No paid user exceeds plan caps. This holds by construction; verify with one weekly `SELECT`.
3. Zero duplicate activations and zero billing-error refund requests.
4. Zero `server_failed` restarts attributable to the checkout path.
5. Pre-launch display check: no surface renders an intro amount ≥ base, and no "then ₱X after" line remains. Run both logged-out `/pricing` and the authenticated paywall.

**Review date:** start + 8 weeks, a hard calendar date fixed on launch day and written into a `ROADMAP.md` `[CHECKPOINT — due YYYY-MM-DD]` row, per the CLAUDE.md signoff rule.

*Illustrative only:* if RC-1 deploys around 2026-10-20, the earliest start is around 2026-11-17 (after the 4-week baseline), with review around 2027-01-12. That window spans the December holidays (H8). The owner should place it against the board-exam calendar.

### 4.6 Rollback policy

- **Trigger:** any guardrail breach, or the owner's call at review.
- **Procedure:** the reverse of §4.2. The owner restores the env price and, if desired, reactivates the voucher with an owner SQL file. Revert the `pricing-config.ts` fallback. **Keep the intro-display gating fix**, since it is correct under any price.
- **Buyers during the test:** every pass sold at ₱99/₱149 is **honoured for its full term**. There is no auto-renew, so nobody is re-billed at a new price. Their next pass is priced at whatever is live when they buy, and the existing "never auto-renews" copy already discloses this. If the price was labelled time-limited, rollback does not happen before the labelled date unless a guardrail breaks.

---

## 5. Funnel Measurement Specification

### 5.1 Funnel definition (smallest useful set; existing events unless marked NEW)

| # | Stage | Definition | Source | Join level | Label |
|---|---|---|---|---|---|
| 1 | **Qualified anonymous visitor** | A JS-executing browser visit to landing, public note or explore, from **Vercel Web Analytics**. Reported as "browser visitors, **not verified humans**". | Vercel (owner dashboard; not accessed by any session so far, audit §2) | Aggregate only | UNKNOWN today |
| 1x | *Backend fetch volume (NOT visitors)* | `PUBLIC_NOTE_VIEWED` | `NoteService.java:1187` | n/a | MEASURED. **Never used as a visitor count** (E12, H7) |
| 2 | Entry surface | `LANDING_PAGE_VIEWED`, `EXPLORE_VIEWED`, `EXAM_HUB_VIEWED` (aggregate) | `analytics_events` | Anonymous aggregate | MEASURED (requests) |
| 3 | Signup started / completed | `SIGNUP_STARTED` (anonymous, events); **account creation = `users` row** (authoritative; `SIGNUP_COMPLETED` is incomplete, audit §19 #6) | `users` | Authenticated | MEASURED |
| 3a | **Signup entry surface** (NEW, RC-2) | First-touch tab's pathname class, written once at signup | RC-2 | **The only anonymous→authenticated join**, client-carried | — |
| 4 | **First meaningful learning action (activation)** | ≥1 of `QUICK_REVIEW_COMPLETED`, `CHALLENGE_QUIZ_COMPLETED`, `ADAPTIVE_PRACTICE_COMPLETED`, `BOARD_EXAM_COMPLETED`, `LONG_EXAM_COMPLETED` **within 72h of signup** | `analytics_events` | Authenticated | Definition chosen here |
| 5 | Study Plan adoption | `STUDY_PLAN_ADOPTED` or `ONBOARDING_V2_PRACTICE_FIRST_PLAN_ADOPTED` within 72h | `analytics_events` | Authenticated | — |
| 6 | Return | R_N (5.3) | `analytics_events` | Authenticated | — |
| 7 | Paywall → upgrade → checkout → paid | `PAYWALL_VIEWED` → `UPGRADE_CLICKED` → `payment_transactions` created → `SUCCESS` + `SUBSCRIPTION_STARTED` | Tables + events | Authenticated | MEASURED |

**Rates are computed only within the authenticated part (stages 3–7).** Stages 1–2 are reported as aggregates beside the funnel, never divided into stage 3. RC-2's 3a gives a per-signup surface split, which is the only honest anonymous→signup attribution available without new anonymous state.

### 5.2 Crawler and automated traffic

- **Do not:**
  - Add user-agent tagging or classification to `PUBLIC_NOTE_VIEWED` (H7: it would see the Next server's user agent).
  - Add any per-view write, join or heavier query on public pages (audit §18; brief constraint).
  - Add an anonymous ID or cookie (CLAUDE.md public-page rule).
- **Do:**
  - Treat Vercel Web Analytics (a client-side beacon, so most non-JS crawlers are excluded, INFERRED) as the upper bound on browser visitors, explicitly labelled "not verified humans".
  - Treat RC-2's surface field as human-filtered by construction: it exists only for a browser that completed signup.
  - Report the `PUBLIC_NOTE_VIEWED` daily count only as a **load** metric for infrastructure.

### 5.3 Return metrics (D1/D3/D7/D14/D30)

**Qualifying event:** any `analytics_events` row with a non-null `user_id`, **excluding** types that can fire from an async job rather than a real visit:
- `PUBLIC_NOTE_COPIED` and `SHARED_NOTE_COPIED`
- `STUDY_PLAN_ADOPTED` and `STUDY_PACK_GENERATED`
- Collection events
- Email events

This is the audit's validated definition (audit §6 validity check, §7). It does not use `last_login_at`.

**⚠️ Correction: the originally-drafted windows overlapped** (each defined as `(signup + N·24h, signup + (N+7)·24h]`, so R_1 covered days 1–8, R_3 covered days 3–10, and R_7 covered days 7–14 — largely the same users, which cannot show decay between them). Fixed to **non-overlapping brackets**:

**R_N for N ∈ {1, 3, 7, 14, 30}:** the user has ≥1 qualifying event in the window **(signup + previous-N·24h, signup + N·24h]**, where "previous-N" is the prior value in the sequence {0, 1, 3, 7, 14, 30} — i.e. R_1 = (0,1] days, R_3 = (1,3] days, R_7 = (3,7] days, R_14 = (7,14] days, R_30 = (14,30] days.
- **Eligible cohort:** accounts with signup + N·24h ≤ measurement time. This removes right-censoring, which the audit's "ever after N days" measure carries (§7 caveat 2).
- **The definition differs from the audit's,** so **re-run the baseline on R_N** (read-only `SELECT`) before any before/after comparison.

**Segments** (assigned from the first 72h, mutually exclusive):
- **Plan learner:** adopted a Study Plan.
- **Standalone note learner:** activated (stage 4) without adoption.
- **Copy-intent arrival:** `PUBLIC_NOTE_COPIED` or `COPY_ON_SIGNUP_COMPLETED` within 24h, with no adoption.
- **Unactivated:** none of the above.

Report R_N per segment **by signup month**.

### 5.4 Evaluation approach for small cohorts

- **Report raw counts first** ("7 of 48"). Add a Wilson 95% interval when a rate is shown, so overlap is visible.
- **Fix decision rules before looking.** Use the audit's count-based SUPPORTS/REJECTS framing (§20).
- **Pool cohorts cumulatively by period around a single change.** Never compare two changes inside one window.
- **One behaviour-changing release per measurement window.** This is why RC-3, RC-5 and RC-6 are kept out of the RC-4 window.
- **State the horizon honestly.** At about 4 signups a week, about 100 signups takes about 25 weeks (audit §20 E2). Any single run is a first data point.

### 5.5 Minimal experiment for first learning action and return

The experiment is **RC-5** (Public Note → Official Review Set link), read as H3:
- **Supports:** the first-72h adoption share rises, *and* the new adopters' R_7 stays near the existing adopter level.
- **Rejects** (selection, not causation): adoption rises but the new adopters' R_7 falls toward the non-adopter level (audit §20 E2).
- **Guardrails:**
  - First-72h activation among non-adopters does not fall.
  - Post-RC-1, near-limit `PAYWALL_VIEWED` among new adopters does not spike.

**Explicitly not introduced** (brief exclusions, honoured exactly): a mastery model, a scheduler, a recommendation engine, or a Companion redesign. Another "commit to return" prompt is also excluded, since H1+H5 already failed with 0 of 9 (audit §7).

---

## 6. Dependency Map

**Verified and confirmed state:** `v0.167.0` (Study Journey Purpose) is merged to `main` (PR #1481, merge commit `c6e93ee4`, 2026-10-08T23:01:13+08:00). Release Implementor confirmed directly (2026-10-09) that their pre-merge assessment closed clean before the merge, with no material defect found. There is no open Study Journey PR. **No candidate waits on Study Journey.** The brief's "signed off but unmerged" premise was stale; this is now fully resolved, not pending confirmation (see the banner at the top).

### Independent of Study Journey (any review outcome)

| Candidate | Gate |
|---|---|
| RC-0 Checkout diagnosis | None; owner can start today |
| RC-1 Quota fix | **None — already kicked off and in progress on `v0.168.0` (Release Implementor, confirmed 2026-10-09).** |
| RC-2 Funnel attribution | Kickoff of its release; owner storage decision (§8) |
| RC-3 Checkout repair | RC-0 written result |
| RC-4 Price test | RC-0 done; RC-1 deployed + ≥4-week baseline; RC-3 shipped-and-windowed or deferred; stability checkpoints clean; owner decisions §8 |
| RC-6 Intent TTL | Not inside the RC-4 or RC-5 windows |

### Touches Study Journey surfaces — gate resolved, no longer waiting

| Candidate | Why it touches Study Journey | Status |
|---|---|---|
| RC-5 Public Note → Review Set link | Links into Official Review Sets and may reuse `v0.167.0`'s exam-slug resolution | **No longer gated on Study Journey.** Release Implementor confirmed (2026-10-09) the pre-merge assessment closed clean, no material defect. RC-5's only remaining gates are RC-1 (shipped) and the stability checkpoints (§6 below) — not Study Journey. |

### Infrastructure dependencies

- **`v0.165.0` checkpoint** (due 2026-10-12T15:24Z, audit §18) and **`v0.166.0` checkpoint** (due 2026-10-14T11:19Z).
  - **Hard gate for RC-5**, which adds anonymous load.
  - **Gate for RC-4**: a restart during checkout would contaminate the test.
  - RC-0, RC-1, RC-2 and RC-6 do not depend on them.

### Owner-only actions in the chain (CLAUDE.md: production writes, env vars and deploys are the owner's)

- Xendit dashboard and outreach (RC-0).
- Render env price change and voucher SQL (RC-4).
- Spend threshold (RC-1 and RC-4).
- Merges past any blocked ruleset.

```
RC-0 (owner, now) ───────────────┐
                                  ├──► RC-3 (if indicated) ──► [RC-3 window] ──┐
RC-1 (in progress, v0.168.0) ──► [≥4-wk post-fix baseline] ──────────────────┼──► RC-4 (8 wks)
                         │                                                    │
stability checkpoints (10-12, 10-14) ─────────────────────────────────────────┤
                         └──► RC-5 (gated also on stability + SJ review) ─────┘ (not concurrent w/ RC-4 unless owner accepts)
RC-2 ── independent, any time after RC-1
RC-6 ── independent, outside RC-4/RC-5 windows
```

---

## 7. NOW / NEXT / LATER / NOT NOW

**NOW** (this week)
- ~~Owner confirms the stale Study Journey premise~~ — **done.** Release Implementor confirmed directly: the pre-merge assessment is closed clean (no defect), v0.167.0 is merged, and `v0.168.0` is already kicked off by them for RC-1.
- **RC-0:** Xendit pull for 16 invoices plus outreach to the 11 genuine starters. **This is the owner's own first action** — fully independent of RC-1, unclaimed, and the highest-value unblocked step.
- **RC-1 is already in progress on `v0.168.0` (Release Implementor) — track it, don't re-implement it.** Owner still needs to set the weekly generation threshold/guardrail (§8) before it ships, and the two spec gaps (E6, E7) should be confirmed with Release Implementor.
- Read the stability checkpoints when due (10-12, 10-14).

**NEXT** (after RC-1 deploys)
- RC-1's 4-week post-deploy watch, which doubles as the price-test baseline.
- RC-2 funnel attribution, as its own small release.
- RC-3, scoped by RC-0's findings.
- RC-4 price test, once every §6 gate is green.

**LATER**
- RC-5 Public Note → Review Set link: after RC-1 ships and the stability checkpoints close clean, outside the RC-4 window. (No longer gated on Study Journey — resolved.)
- RC-6 intent TTL and flashcards redirect.
- A global LLM spend breaker as its own release, a prerequisite for any generosity move (audit §14).
- Populating `estimated_cost`. It needs a configured per-token rate from the owner.
- Fixing `RetentionService.java:360`'s weekly-email "Study Packs created" count, which includes copies.
- The ambient card's Plus-only wiring (audit §5, §23 #3). It is a positioning decision first.

**NOT NOW**
- Unlimited or Lifetime tiers (audit §14, §15).
- New tiers or entitlement changes.
- Randomized price cohorts.
- An anonymous visitor ID or cookie.
- User-agent classification on `PUBLIC_NOTE_VIEWED`.
- A landing or onboarding redesign.
- A Board Exam default-mode change (`EXAM_MODES.md` contract).
- A mastery model, scheduler, recommendation engine or Companion redesign.
- Automated multi-step checkout recovery campaigns.
- Any reopening of Study Journey architecture, Degree hierarchy, Academic Term semantics, ConceptHealth or assessment integrity.

---

## 8. Owner Decision Checkpoint

Unresolved decisions only.

1. ~~Confirm the Study Journey premise~~ — **resolved.** Release Implementor confirmed directly (2026-10-09): `v0.167.0`'s pre-merge assessment closed clean (no defect), it's merged, and nothing is pending on it. RC-5's gate from this is lifted; it now depends only on the stability checkpoints (§6).
2. **RC-1 spend threshold.** What weekly non-owner Study Pack generation count triggers a revert? There is no global ceiling (E22). The audit's ≈2.8M tokens/month is the upper bound (§17). **Time-sensitive: RC-1 is already in progress on `v0.168.0` (Release Implementor) — this threshold should be set before it ships, not after.**
3. **Accept the RC-1 tradeoff:** fixing the meter may cut paywall views and therefore thin the price-test sample (§1).
3a. **Which RC-1 fix option ships (§3):** Option A (exclude copies via `notes.copied_at`, keep the `max()` backstop) or Option B (drop the `max()`, trust the tracked counter alone) — Release Implementor is evaluating both; this is their and the owner's call, flagged here for visibility only.
4. **`INTRO-PH-*` vouchers during the test:** retire them (owner-run SQL file), or leave them inert (backend-safe, E18)?
5. **Exam pass (₱599/90d) and yearly (₱1,999)** become worse value than monthly at ₱149 (E20). Keep them as-is, pause them for the window, or reprice them? This plan will not invent prices.
6. **Time-limited labelling:** should `/pricing` say the test price applies to passes bought before a stated date? If so, that date binds rollback.
7. **Price-test window placement** against the PH board-exam calendar (H8), and sign-off on the §4.5 decision rules.
8. **RC-5 concurrency:** wait until after the RC-4 window (recommended), or run concurrently and accept a labelled confound?
9. **RC-2 storage:** a new `users` column (migration) or `SIGNUP_COMPLETED` metadata (no migration, incomplete history)? Also confirm that extending the existing client-only `sessionStorage` first-touch capture is acceptable under the "no anonymous session state on public pages" rule.
10. **Inputs owed** (not decisions, but nothing can replace them):
    - The Xendit fee percentage.
    - The OpenAI per-token pricing or a usage-dashboard export.
    - The live Render env price values.

---

## Single recommended next release candidate: RC-1, the Study Pack quota accounting fix — ⚠️ already in progress, confirmed by independent convergence

Each candidate was put through the three tests:
- **(a) Independence:** independent of the `v0.167.0`/`v0.168.0` branch state?
- **(b) Precondition:** a precondition for correctly interpreting other candidates?
- **(c) Measured:** backed by something already MEASURED rather than a hypothesis?

| Candidate | (a) Independent | (b) Precondition for others | (c) Measured basis | Result |
|---|---|---|---|---|
| **RC-1 Quota fix** | **Yes** | **Yes.** RC-4 needs a stable post-fix paywall baseline. RC-5 must not push adopters into a defective paywall. | **Yes.** 76/76, 75/76, git timeline, code path (audit §17; E3–E8) | **Passes all three** |
| RC-0 Checkout diagnosis | Yes | Yes (for RC-3, RC-4) | Partly. The leak is measured, its cause is UNKNOWN. | Passes, but **it is owner work, not a code release**. It runs in parallel and does not compete. |
| RC-2 Funnel attribution | Yes | Partly. It helps acquisition reads; RC-4 does not need it. | No. It closes a measured gap, but its value is prospective. | Fails (b) and (c) |
| RC-3 Checkout repair | Yes | Yes (for RC-4) | **No.** Its scope depends on RC-0's unknown result. | Fails (c) until RC-0 lands |
| RC-4 Price test | Yes | **No.** It is the thing other candidates must not confound. | **No.** H2 is the hypothesis being tested. | Fails (b) and (c) |
| RC-5 Review Set link | Partly — gated on stability checkpoints, not Study Journey (that gate is now resolved, see banner) | No | No (H3 is correlational) | Fails (b) and (c) |
| RC-6 Intent TTL | Yes | No | No (frequency UNKNOWN) | Fails (b) and (c) |

**RC-1 passes all three tests — and this analysis was built independently of, then confirmed against, Release Implementor's own independent arrival at the identical root cause from the same audit, who kicked off `releases/v0.168.0` for it before this plan existed.** That double arrival is itself a confidence signal: two separate analyses of the same evidence converged on the same single highest-priority defect.

**⚠️ Because RC-1 is already moving, it is not what the owner needs to go *start*.** The owner's concrete next action, right now, unclaimed and fully parallel, is **RC-0** (the Xendit dashboard pull and the 11-user outreach) — it needs nothing from RC-1, blocks RC-3 and RC-4 until it runs, and costs the owner roughly an hour with no code and no risk. Everything else in this plan (RC-2 through RC-6) sequences after RC-1 and RC-0, per §6/§7.

**Why RC-1 is correctly the top technical priority regardless of who implements it:**
- It removes a defect that currently paywalls users for a zero-cost action, and that action is the one most associated with return.
- It needs no data migration and no production write.
- Its main risk, restored LLM demand, is bounded per user by unchanged caps and should be gated by an owner-set threshold (§8) before Release Implementor's fix ships.
- Shipping the price test or the adoption prompt (RC-5) before it would either confound their results or push new learners into this defect on day one.
