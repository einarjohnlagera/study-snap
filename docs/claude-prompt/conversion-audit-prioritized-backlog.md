# Conversion Audit — Consolidated, Prioritized Backlog

> Synthesized from all 7 conversion audits in `docs/claude-prompt/conversion-audit-out/`. ~50 individual
> recommendations deduplicated (A4 R7 and A7 rec 7 were the same idea from two independent audits —
> merged into one line) and re-ranked into one list. Routing tags follow `CLAUDE.md`'s task-routing
> table: **[Claude Code]** = small, single-surface, no new backend/infra — implement directly.
> **[Codex]** = touches backend or spans enough files/new infra to warrant a written prompt.
> **[Decision]** = needs a product/business call before any implementation, from either bucket.

---

## 0. Verified against actual code (2026-07-11)

| # | Item | Finding |
|---|---|---|
| V1 | **Adaptive Practice pricing-copy vs. runtime-gate divergence** (A2 F1/R1) | **Confirmed real, and already partially self-acknowledged in the docs.** `StudySnapProperties.resolveMonthlyAdaptivePracticeLimit` has a dedicated `adaptivePracticeProOnly` toggle (env var `ADAPTIVE_PRACTICE_PRO_ONLY`, defaults `false` in the repo's `application.yaml`/`application-prod.yaml` — not overridden in-repo, so the live value depends on whatever's set in the actual hosting environment, which isn't visible from the codebase). When that flag is `true`, Free/Plus both resolve to a `0` limit regardless of their configured 3/10 defaults — i.e. Pro-only. `subscriptions-and-usage-limits.md` itself already contains a "Pricing-surface note" admitting *"some pricing surfaces currently position Plus with stronger 'regular study' messaging than the backend feature gates provide"* — this is a known, already-flagged, but still-unfixed divergence, not a fresh bug. **Action:** confirm the live `ADAPTIVE_PRACTICE_PRO_ONLY` value in your hosting env; if `true` (matching the enforcement doc's plain-language claim), correct `pricing.md`/`PLANS.md` copy to match — do not "fix" the code, the toggle is working as designed. |
| V2 | **`concept_health` write scope** (A6 finding 3.5/rec 7) | **Partially confirmed, narrower than the audit guessed.** Plain Quick Review completion (`QuickReviewSessionService.completeSession`) has **zero** calls into `ConceptHealthService` — confirmed no durable progress accumulates from Quick Review. But Challenge Quiz **does** write it (`ChallengeQuizService` calls `recordCorrectAnswers`/`recordIncorrectAnswers`), which the audit's inference missed — quiz.md never actually claimed writes were Adaptive-only, that was the auditor's own inference and it was too broad. **Net finding:** the real gap is Quick Review specifically — the mode used as the default "Quiz yourself on this note" CTA everywhere (public notes, onboarding) — not "everything except Adaptive Practice." Given V1, Free users likely rely on Quick Review + gated-out Adaptive Practice, so this narrower gap is still real and still worth closing (extend concept_health writes to Quick Review completion), just more precisely scoped than A6 stated. |
| V3 | **`public-library.md` internal contradiction** (A7 §2.2) | **Confirmed and fixed.** `frontend/app/public/library/[subject]/page.tsx` is a real 227-line server-rendered page with `generateMetadata` and `buildCollectionPageStructuredData` — no redirect. The doc's "item J" (line 566) was correct; the Routes-section "legacy redirect" line was the stale one. **Fixed directly in `docs/features/public-library.md`** — moved `/public/library/{subject}` out of the legacy-redirects list into its own canonical-route line. |

---

## Tier 1 — High impact, low effort (ship first)

| # | Item | Source | Routing |
|---|---|---|---|
| 1 | Cross-linking pass: Target Users ↔ Learn categories ↔ `/exam/*`, related-guides block | A1 #1 | [Claude Code] |
| 2 | Hero sharpening — headline/SEO-title mismatch, Board Exam Mode badge vs. screenshot mismatch | A1 #2 | [Claude Code] |
| 3 | Instrument every upgrade surface with source-tagged view/click events | A2 R2 | [Claude Code] (enum + firing) |
| 4 | Move `PASS_NO_AUTO_CHARGE_FOOTER` reassurance CTA-adjacent, not footer-only | A2 R3 | [Claude Code] |
| 5 | Quick Check completion hook (inline outcome-framed prompt at peak intent) | A3 #1 | [Claude Code] |
| 6 | Value-attribution line above Study Pack sections ("built from the note below") | A3 #2 | [Claude Code] |
| 7 | Rewrite onboarding Step 5 headline + return-expectation subcopy | A5 #1 | [Claude Code] |
| 8 | Establish visual hierarchy between the two Step-5 actions | A5 #2 | [Claude Code] |
| 9 | Result screens narrate the return appointment ("due for a refresh in 3 days") | A6 #2 | [Claude Code] (small `next-step` payload addition — check if [Codex] once scoped) |
| 10 | Default Public Library filter mode to a "Recommended" sort (score already exists both sides) | A7 #1 | [Claude Code] |
| 11 | **Official-plan bridge from Public Library** (merged: A4 R7 = A7 rec 7) — contextual pointer from a courseProgram-filtered view to the matching Official Study Plan | A4 R7 / A7 #7 | [Claude Code] |

---

## Tier 2 — High-to-medium impact, medium effort

| # | Item | Source | Routing |
|---|---|---|---|
| 12 | Real social-proof strip (live counts from existing public data, or named-exam focus line) | A1 #3 | [Codex] if a new stats endpoint is needed; else [Claude Code] |
| 13 | Pass-expiry renewal prompt (expiry-approaching notice + one-tap renewal CTA) | A2 R6 | [Codex] — new backend trigger |
| 14 | Pre-adopt plan preview (read-only view over existing `GET /collections/public/{id}`) | A4 R1 | [Claude Code] (no backend change — endpoint exists) |
| 15 | Practice-readiness metadata line on public plan cards ("N of M notes practice-ready") | A4 R3 | [Codex] — new aggregate field on public DTO |
| 16 | Put due concepts on the Dashboard's top "what now" slot | A6 #1 | [Codex] — likely needs `getTodayFocus()` payload extension |
| 17 | Show due-concept signals to Free users (action stays gated, signal doesn't) | A6 #6 | **[Decision]** — needs explicit product sign-off first, current docs call the gate intentional |
| 18 | Promote Course/Program discoverability (chips/rail) + document + fix search scope | A7 #2 | [Claude Code] for chips; verify search predicate covers courseProgram |

---

## Tier 3 — Medium impact, lower urgency (schedule after Tier 1–2)

| # | Item | Source | Routing |
|---|---|---|---|
| 19 | Exam hub empty-state + one-paragraph value strip | A1 #4 | [Claude Code] |
| 20 | Differentiation section rewrite to one felt, concrete contrast | A1 #5 | [Claude Code] |
| 21 | Promote demo access from optional to a required hero/nav slot | A1 #7 | [Claude Code] |
| 22 | FAQ section + FAQPage/Article structured data on landing/Learn | A1 #8 | [Claude Code] |
| 23 | Rewrite paid-plan bullets as outcomes; add "Best for" row to comparison table | A2 R4 | [Claude Code] |
| 24 | Add hesitant-buyer FAQ entries (pass-end, renewal, payment methods, refunds) | A2 R5 | [Claude Code] |
| 25 | Align duration presentation + standardize CTA verb set across surfaces | A2 R7 | [Claude Code] |
| 26 | Reframe near-limit usage banner as a milestone, not just a warning | A2 R9 | [Claude Code] |
| 27 | Consolidate non-owner note CTA set + fix `public-notes.md`/`note-detail.md` doc drift | A3 #3 | [Claude Code] |
| 28 | Related-notes module on note detail ("More in {Subject}" / "More from {Author}") | A3 #4 | [Claude Code] or [Codex] if a new query is needed |
| 29 | Outcome microcopy under the plan adopt CTA (states what "Start" actually does) | A4 R2 | [Claude Code] |
| 30 | Official identity badge on public plan card (no backend field needed — publish is admin-only today) | A4 R4 | [Claude Code] |
| 31 | Add Professional/Board Taker examples to Course/Program helper-text mapping | A5 #5 | [Claude Code] |
| 32 | Post-completion Dashboard guidance tip (references pack topic, offers reminders) | A5 #7 | [Claude Code] — via existing `pickActiveGuidance` engine |
| 33 | Returning-visit guidance tip teaching the decay/review rhythm | A6 #3 | [Claude Code] |
| 34 | Re-weight returning-user dashboard emphasis away from static Quick Review | A6 #5 | [Claude Code] — composition/ordering only |
| 35 | General no-results state for Public Library filter combinations | A7 #3 | [Claude Code] |
| 36 | "Study Pack Ready" toggle filter on Public Library | A7 #5 | [Claude Code] |

---

## Tier 4 — Low impact, cheap cleanups (batch together)

| # | Item | Source | Routing |
|---|---|---|---|
| 37 | Carry Learn-article intent into signup (reuse exam-intent cookie pattern) | A1 #6 | [Claude Code]; [Codex] only if onboarding must newly consume it |
| 38 | Mobile rules for hero density + pricing-preview stacking | A1 #9 | [Claude Code] |
| 39 | Consolidate Learning-Loop/How-It-Works landing sections; retitle "AI Critique" guide | A1 #10 | [Claude Code] |
| 40 | Single detected-currency display with region note | A2 R8 | [Claude Code] |
| 41 | Author mini-card on public note detail | A3 #5 | [Claude Code] |
| 42 | Breadcrumb + BreadcrumbList JSON-LD on note detail | A3 #6 | [Claude Code] |
| 43 | Auth-interstitial promise restatement on signup click-through | A3 #7 | [Claude Code] |
| 44 | Profile polish for non-owners (bio fallback, footer affordance) | A3 #8 | [Claude Code] |
| 45 | Unify adopted-state vocabulary ("Adopted" vs "In your library") | A4 R5 | [Claude Code] |
| 46 | Close Dashboard unset-courseProgram gap in Matching Study Plan Section | A4 R6 | [Claude Code] |
| 47 | Verify Goal card sub-line uses `getCollectionLabels`, not a literal string | A4 R9 | [Claude Code] — trivial check |
| 48 | Reframe onboarding adopt card as explicitly supplementary | A5 #3 | [Claude Code] |
| 49 | Fix Dashboard learner-level follow-up prompt body copy | A5 #4 | [Claude Code] — one string |
| 50 | Teacher-conditional Learner Level label qualifier | A5 #6 | [Claude Code] |
| 51 | Permanence framing line on onboarding Step 4 success | A5 #8 | [Claude Code] — one string |
| 52 | Echo Weekly Countdown pacing at quiz-session completion | A6 #4 | [Claude Code] |
| 53 | Align in-app subject filter with subject landing page, or document divergence as intentional | A7 #4 | [Claude Code] (doc-only) or [Codex] (component reuse) |
| 54 | Fix badge-classification issues (Course/Program badge→chip, define/remove "High Quality") | A7 #6 | [Claude Code] |
| 55 | Doc hygiene batch (seo.md subject-landing gap, Learner Level filter doc mismatch, pagination note) | A7 #8 | [Claude Code] — docs only |

---

## Deferred / explicitly blocked (do not implement now)

- **Adoption-count social proof** (A4 R8) — raw counts would currently hurt more than help at low adoption volume; wait for real volume, reuse the existing Popular-badge threshold philosophy when it's time.
- **"Trending this week" on Public Library** (A7, explicitly rejected) — blocked on windowed backend counts that don't exist yet; do not fake with lifetime totals.

---

## Routing summary

Of ~55 items: **~6–8 are genuinely [Codex]** (new backend fields/endpoints/triggers — items 9\*, 12\*, 13, 15, 16, 28\*, 37\*, 53\* where flagged). The rest are single-surface copy or small-component changes that `CLAUDE.md`'s own routing rule puts in Claude Code's lane directly — spinning up a Codex prompt for each would cost more in prompt-writing overhead than the change itself. **2 items are [Decision]**, not implementation at all — they need your call before any prompt gets written.
