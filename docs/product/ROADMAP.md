# ROADMAP.md - NoteLib

Rebrand note: StudySnap has been rebranded to NoteLib while preserving current database schema naming unless explicitly changed.

Goal: evolve NoteLib from a one-shot generator into a reusable note-first study workspace.

## Current Release Baseline

`v0.50.0 - Mobile Bottom Tab Bar` is the current in-progress version (on `releases/v0.50.0`, cut from `main` after v0.49.0 merged) — a persistent 4-tab mobile bottom navigation bar (Dashboard, Library, Review Sets, Public Library), gated on device-mix evidence from the App Shape Fable proposal and un-parked by a 2026-07-15 production pull showing ~75% mobile usage by distinct users. Navigation-shape work, not a retention experiment — orthogonal to the concurrently-accruing v0.48.0 cohort read. Mid-release scope addition: the 3 held instrumentation pulls (UTM/referral tracking, offline-fallback hit rate, browse-without-adopt tracking) folded in after an explicit 2026-07-15 decision to instrument — analytics-collection only, no new UI surfaced from the data yet, so it doesn't confound the concurrently-accruing v0.48.0 cohort read either. See `RELEASES.md`.

`v0.49.0 - Progress Page: Private Library Links` is the previous released version (on `releases/v0.49.0`, cut from `main` after v0.48.0 merged) — a small, deliberately non-retention-flavored fix: Progress page's per-subject links and its "weakest subject" CTA now point at the learner's private Library instead of the public one. Scoped to fill the interim window while the v0.48.0 retention experiment cohort accrues enough data for a read, without confounding that read. See `RELEASES.md`.

`v0.48.0 - Retention Experiment: Open Loop & Digest Trigger` is the previous released version (on `releases/v0.48.0`, cut from `main` after v0.47.1 merged) — two independent retention experiments testing the co-dominant causes from the retention root-cause diagnosis: an open-loop first-quiz ending (frontend) and a due-concepts digest default-ON trigger fix with CTA/content work (backend + frontend). Both are unproven experiments, not confirmed wins — lift is not yet measured. See `RELEASES.md`.

`v0.47.1 - V82 Migration Collision Hotfix` is the previous released version (on `releases/v0.47.1`, cut from `main` after v0.47.0 merged) — a single-file fix for a duplicate Flyway migration version that had blocked every production deploy since `v0.46.0` merged. No schema or behavior change, renumbers a never-applied migration only. See `RELEASES.md`.

`v0.47.0 - Conversion Audit Tier 4: Cleanup Batch` is the previous released version (on `releases/v0.47.0`, cut from `main` after v0.46.0 merged) — 16 low-impact, cheap-cleanup items from the conversion/retention UX audit backlog's Tier 4, shipped across 6 Codex-prompted PRs (Landing & Pricing, Public Note Detail, Discovery & Library, Onboarding copy, Doc hygiene, Learn signup-intent). Item 37 was initially dropped, then folded back in at a smaller scope once its original cookie-based mechanism proved a poor fit. No new backend entities, migrations, or endpoints. See `RELEASES.md`.

`v0.46.0 - Retention Depth: Due-Concepts Digest & Exam Pacing` is the previous released version (on `releases/v0.46.0`, cut from `main` **before** `v0.45.2` existed, now rebased onto latest `main`) — two Fable-sourced new-capability ideas (`docs/claude-prompt/new-capability-out/01-new-capability-ideation.md`, Ideas 1 and 5): a weekly due-concepts email digest as a new type on the existing `RetentionEmailScheduler`, and an exam-date pacing plan scheduling the learner's owned content only (explicitly not Smart Review Planning). Scope broadened mid-release with a session-completion pacing echo and a 4-item app-feel polish batch (a 5th, a sticky search/filter toolbar, shipped then was reverted the same day after a user-reported mobile regression). See `RELEASES.md`.

`v0.45.2 - Public Plan Preview Rollup Fix` is the previous released version (on `releases/v0.45.2`) — a fix to the one remaining gap in v0.45.1's Goal-collection rollup fix: the plan-preview panel's `getPublic()` endpoint, a third code path v0.45.1 never touched, plus a note-list cap (the rollup fix now surfaces every note, and some production plans have 40-52) so the preview panel stays bounded. No new backend entities, migrations, or pricing/quota changes. See `RELEASES.md`.

`v0.45.1 - Study Plan Collection Fixes` is the previous released version before that (on `releases/v0.45.1`) — three pre-existing collection/discovery defects surfaced during v0.45.0 pre-signoff review: a Goal-collection note-count rollup that always showed 0 when notes lived only on child Subject Plans, a Published Plans backlink that ignored how the user actually arrived, and no path back to the official-plan catalog once a primary study plan was set. Also fixed a dark-mode contrast bug on the `Official` plan badge, found post-merge. No new backend entities, migrations, or pricing/quota changes. See `RELEASES.md`.

`v0.45.0 - Conversion Audit Tier 3 — Landing, Pricing & Discovery Polish` is the previous released version before that (on `releases/v0.45.0`) — 18 medium-impact, lower-urgency items (#19–36) from the same 7-session conversion/retention UX audit that drove v0.44.0, shipped across 6 thematic PRs (landing, pricing, note detail, public plan card, onboarding/Dashboard guidance, Public Library) plus two post-merge follow-ups and one regression-fix bundle. See `RELEASES.md`.

`v0.44.0 - Conversion & Retention Polish` is the previous released version before that (on `releases/v0.44.0`) — Tier 1 findings from the same audit shipped in full, plus a verified backend fix for Quick Review's missing ConceptHealth tracking and a mid-release copy-on-signup onboarding-intercept fix (both pressure-tested and closed), plus Tier 2 of the same audit backlog (7 items, folded in as a second slice rather than opening a new version). A whole-release pre-signoff pressure test found no functional defects; three items are logged as known, unfixed limitations rather than blockers — see `RELEASES.md`. The "AI-generated Review Sets" candidate (see "Future, gated — AI-generated Review Sets" below) remains under product/UX discussion and has not been scoped or kicked off; a much larger, separate "Smart Review Planning" exploration exists as paused planning material in `docs/claude-prompt/fable-out/` and is not yet scoped to any release either — see the Backlog Index above for its current gate condition.

`v0.43.1 - Companion Mentor Tips` is the previous released version before that (on `releases/v0.43.1`).

`v0.43.0 - Companion Coach Experience` is the previous released version before that (on `releases/v0.43.0`).

`v0.42.1 - Companion & Progress Polish` is the previous released version (on `releases/v0.42.1`); `v0.42.0 - AI-assisted Companion authoring + regeneration` before that.

`v0.41.1 - Review Set Detail Page: This-Set Study Dashboard` is the previous released version (on `releases/v0.41.1`).

Older released versions (`v0.41.0` and earlier, back to `v0.11.0`) are summarized in `docs/archive/ROADMAP_ARCHIVE.md`'s index and in full in `RELEASES.md` / `docs/archive/RELEASES_ARCHIVE.md` / `docs/releases/vX.Y.Z.md`.

## Backlog Index (read this before deciding what's next — do not scan prose to find candidates)

**Invariant: no `docs/claude-prompt/*-out/` directory or session-plan file may exist without a row here.** A Fable session that writes planning output adds its row in the same commit. This rule exists because Smart Review Planning (7 fully-architected documents in `docs/claude-prompt/fable-out/`) sat undiscovered for ~5 release cycles, found only by chance when the user asked about it directly — a prose mention buried in a released-version paragraph is not an index entry.

**Review ritual:** every `/kickoff`, scan this table — bump `Last reviewed`, check whether any `Gate` condition became true, verify every `docs/claude-prompt/` planning directory still has a row (see kickoff checklist step 8 in `CLAUDE.md`).

| Item | Source | Status | Gate (what un-parks it) | Last reviewed |
|---|---|---|---|---|
| Retention H1 + H5 (commitment device + pre-decided return action) | `retention-diagnosis-session-plan.md` | Conditional — next up | v0.48.0 cohort read positive-or-ambiguous (~late July 2026) | 2026-07-15 |
| User interviews (retained + churned exam-dated, script written) + 3 zero-code analytics pulls (device mix, PDF export volume, Official Review Set coverage audit) | `retention-diagnosis-out/04-interim-window-queries.sql`, `05-interview-script.md` | Active — do now, ready to run | none | 2026-07-15 |
| 3 pulls needing real instrumentation first (UTM/referral tracking, offline-fallback hit rate, catalog "browse" tracking for the browse-without-adopt metric) | `retention-diagnosis-session-plan.md` "Strategy checkpoint" | **In progress — folded into v0.50.0** | explicit decision to instrument — ✅ made 2026-07-15 | 2026-07-15 |
| **Smart Review Planning (Internal Curator, 7 docs)** | `docs/claude-prompt/fable-out/01–07` | Parked | interviews confirm content-gap churn AND manual coverage sprint proves lift AND hand-curation saturates | 2026-07-15 |
| Manual Official-coverage sprint (hand-curate ALE/PNLE/LET) | this checkpoint (2026-07-15) | Conditional | interviews surface "no content for my exam" as a churn reason | 2026-07-15 |
| Photo Capture of handwritten notes (Idea 6) | `new-capability-out/01-new-capability-ideation.md` | Held | retention loop proven (W1→W2 lift confirmed) | 2026-07-15 |
| Parent Readiness Digest (Idea 4) | same | Conditional | H1 read positive + explicit product decision (email-only vs. dashboard shape) | 2026-07-15 |
| Offline Study Pack access (Idea 9) | same | Held — one leg down | heavy mobile (✅ confirmed 2026-07-15, ~75%) AND (~~PDF export volume~~ ruled out, essentially unused, 1 export ever OR offline-fallback hit rate, not instrumented OR interview signal, not yet run) | 2026-07-15 |
| Unified Next-Step Resolver | `app-shape-session-plan.md` | Merged into H5 | ships only alongside H5, not standalone | 2026-07-15 |
| Mobile bottom tab bar | same | **Shipped — v0.50.0** | device-mix pull shows heavy mobile usage — confirmed, ~75% mobile by distinct users | 2026-07-15 |
| App Shape Core (Live Milestones, Concept-to-Note Back-Annotation, Struggle Map) | same | Held indefinitely | retention constraint clears | 2026-07-15 |
| App Shape Polish stragglers (sticky toolbar re-attempt, Result-Screen Companion Bridge, Review Set filter facet, feedback digest) | same | Held / needs real scoping | Struggle Map (above) ships first for the digest item; others need a scoping pass | 2026-07-15 |
| Bulk Quiz Generation & Teacher-Flow Polish (+ Ideas 2/3 folded in) | `ROADMAP.md` §Bulk Quiz Generation | Held | ≥5 active teacher accounts | 2026-07-15 |
| Listen Mode / Bilingual UI / Study Buddy (Ideas 7, 10, 11) | `new-capability-out/01-new-capability-ideation.md` | Low priority | interview language/social/loop signal | 2026-07-15 |
| PDF export surfacing (near-zero usage — 1 export, ever) | `retention-diagnosis-session-plan.md` "Fourth Fable checkpoint" | Parked — do not build | retention funnel + interview signal on offline demand both resolve; value-vs-discovery is currently undeterminable (see checkpoint) | 2026-07-15 |
| Conversion-audit deferred pair (adoption-count social proof, "Trending this week") | `conversion-audit-prioritized-backlog.md` | Held | windowed backend counts get built | pre-2026-07-15 |
| AI-generated Review Sets / Runtime Companion (Ask Companion, Personalization) | `ROADMAP.md` §Future, gated | Parked | explicit product go-decision | pre-2026-07-15 |
| Review-Set-Centric Navigation | `ROADMAP.md` §deferred | Parked | direction, not a scoped item — no gate stated | pre-2026-07-15 |
| Deeper plan nesting (3+ level hierarchy) | `ROADMAP.md` §Deeper plan nesting | Parked, nice-to-have | no gate stated | pre-2026-07-15 |
| Note Detail readiness as its own tab | `ROADMAP.md` §Note Detail readiness | Blocked | needs a mobile tab-overflow design pass | pre-2026-07-15 |
| Legacy "Future Directions" block (exam-mode work, billing, teacher items pre-v0.20) | `ROADMAP.md` §Future Directions | Stale — needs a fresh audit, largely pre-dates current architecture | none stated | never (flag for cleanup) |

## v0.50.0 - Mobile Bottom Tab Bar (In Progress, base branch `releases/v0.50.0`)

Origin: Fable App Shape proposal (`docs/claude-prompt/app-shape-out/02-app-like-ui.md`), gated on device-mix evidence. Un-parked 2026-07-15 by a production pull showing ~75% mobile vs. 25% desktop by distinct users. Scoping confirmed the original proposal's coordination concern (a "sticky Continue bar") never shipped; two other real, currently-shipped bottom-of-viewport elements (`AddToHomeScreenNudge`, the floating "Send Feedback" launcher) need coordination instead. Full scope in `RELEASES.md`.

Mid-release addition (2026-07-15): the 3 held instrumentation pulls from `retention-diagnosis-session-plan.md`'s Strategy checkpoint (UTM/referral tracking, offline-fallback hit rate, browse-without-adopt tracking), folded in after an explicit decision to instrument rather than opened as their own version. See `RELEASES.md`.

## v0.49.0 - Progress Page: Private Library Links (Released, base branch `releases/v0.49.0`)

Origin: surfaced during the v0.49.0 scoping pass as one of two candidates from the "Post-v0.40.0 Polish Backlog" for a small orthogonal release to fill the interim window while v0.48.0's retention experiments accrue cohort data. The other candidate (Adopted badge on Review Sets) turned out to already be shipped in v0.40.1 — that roadmap entry was stale and has been corrected above. Full scope in `RELEASES.md`.

## v0.48.0 - Retention Experiment: Open Loop & Digest Trigger (Released, base branch `releases/v0.48.0`)

Origin: `docs/claude-prompt/retention-diagnosis-session-plan.md`'s "Recommended v0.48.0 scope" — two Fable sessions (growth/retention diagnosis + consumer psychology) converged on dead trigger infrastructure and no open loop at first-session end as co-dominant retention causes, sharpened by real production data pulls (week-1 depth ≈ W2 retention magnitude; exam-dated users retained *worse* under status quo, since nothing currently acts on the field). A pre-kickoff Resend open/click check (domain-wide, not `INACTIVITY`-specific — no per-type tagging exists to decompose it) found sub-1% click-through, which didn't kill either experiment but reweighted the digest trigger fix to include CTA/content work rather than a bare default flip. Full scope in `RELEASES.md`.

## v0.47.1 - V82 Migration Collision Hotfix (Released, base branch `releases/v0.47.1`)

Origin: production deploy has failed since `v0.46.0` merged with `Found more than one migration with version 82`. Root cause: a rebase artifact — `releases/v0.46.0` was cut from `main` before `v0.45.2` existed and later rebased onto latest `main`, but the due-concepts-digest migration kept its version number from the older base instead of picking up the number the newer `main` had already taken. Full scope in `RELEASES.md`.

## v0.47.0 - Conversion Audit Tier 4: Cleanup Batch (Released, base branch `releases/v0.47.0`)

Origin: Tier 4 of `docs/claude-prompt/conversion-audit-prioritized-backlog.md` (items 37–55, "low impact, cheap cleanups," explicitly meant to be batched together). Item 52 already shipped in v0.46.0; items 47 and 50 (already fixed in prior releases) were dropped during pre-scoping verification. Item 37 was also initially dropped (its assumed cookie-consumption mechanism didn't fit Learn's category-keyed guides), then folded back in at a smaller scope once the rest of the release shipped, reusing item 43's query-param intent pattern instead. Routed through Codex prompts per-item this release (token-budget choice), not the direct-Claude-Code lane the routing table would otherwise put every item in. Full scope in `RELEASES.md`.

## v0.46.0 - Retention Depth: Due-Concepts Digest & Exam Pacing (Released, base branch `releases/v0.46.0`)

Origin: Ideas 1 and 5 from the "New Capability Ideation" Fable session (`docs/claude-prompt/new-capability-out/01-new-capability-ideation.md`), the two ranked highest of three recommended for a real scoping pass. Both are retention-themed, continuing v0.44.0's data-driven thesis that retention (not top-of-funnel) is the proven constraint. Full scope in `RELEASES.md`.

## v0.45.2 - Public Plan Preview Rollup Fix (Released, base branch `releases/v0.45.2`)

Origin: the "Preview this plan" panel on public plan cards was found still showing "0 of 0 notes practice-ready" for Goal collections during v0.46.0 kickoff research — v0.45.1's rollup fix touched `list()`/`listPublic()` but never `getPublic()`, the third code path backing this panel. Cut from `main` as its own patch, independent of the concurrent `v0.46.0` branch. Full scope in `RELEASES.md`.

## v0.45.1 - Study Plan Collection Fixes (Released, base branch `releases/v0.45.1`)

Origin: three pre-existing bugs surfaced by direct user report and confirmed via Explore-agent investigation plus independent Opus and Fable consultations during v0.45.0's pre-signoff review — deliberately deferred to their own release rather than folded into v0.45.0 late. Full scope in `RELEASES.md`.

## v0.45.0 - Conversion Audit Tier 3 — Landing, Pricing & Discovery Polish (Released, base branch `releases/v0.45.0`)

Origin: the same 7-session conversion/retention UX audit that drove v0.44.0, Tier 3 of `docs/claude-prompt/conversion-audit-prioritized-backlog.md` (items #19–36, "medium impact, lower urgency"). Unlike v0.44.0's Tier 1/2, every item here routes to direct Claude Code implementation per `CLAUDE.md`'s task-routing table (copy/composition/UI polish, no new backend/infra) except the note-detail related-notes module, whose routing depends on whether a new query is needed. Full scope in `RELEASES.md`.

## v0.44.0 - Conversion & Retention Polish (Released, base branch `releases/v0.44.0`)

Origin: a 7-session conversion/retention UX audit (`docs/claude-prompt/conversion-audit-out/`, consolidated and tiered in `docs/claude-prompt/conversion-audit-prioritized-backlog.md`), run against a real prior data-driven finding (`docs/archive/conversion-funnel-finding.md`, v0.32.2) that identified retention — not top-of-funnel or onboarding — as the proven constraint. This release shipped the audit's Tier 1 (high-impact, low-effort) items plus one verified backend gap, then folded in Tier 2 (items 12–18) as a second slice rather than closing and reopening a new version. Full scope in `RELEASES.md`.

Two things surfaced by the same audit are explicitly **not** in this release's scope:
- **The `adaptivePracticeProOnly` pricing-copy-vs-runtime-gate divergence — resolved, no code change needed.** `StudySnapProperties.resolveMonthlyAdaptivePracticeLimit` has an `adaptivePracticeProOnly` kill-switch that, if `true`, would zero Free/Plus regardless of their configured 3/10 limits; it defaults `false` in both `application.yaml` and `application-prod.yaml`, and the owner confirmed production also runs with it unset/`false`. Marketing copy (3/10/30 across Free/Plus/Pro) was correct all along — `subscriptions-and-usage-limits.md`'s stale "Adaptive Practice unavailable"/"currently unavailable in runtime" lines and its "Pricing-surface note" mismatch disclaimer were corrected to match. No release scope required.
- **"Smart Review Planning."** A much larger, separate initiative (curriculum-driven Review Set auto-assembly) exists as paused planning material in `docs/claude-prompt/fable-out/` — architecture, matching/coverage, admin workflow, student UX, monetization recommendation, terminology audit, and a phased technical roadmap are all drafted but nothing is scoped or kicked off. Unrelated to this release; mentioned here only so it isn't confused with the conversion-audit work. **Current status and gate condition: see the Backlog Index above** — this sat unindexed for ~5 release cycles until a 2026-07-15 checkpoint surfaced it; don't let this historical mention be the only place it's tracked again.

## Retention Root-Cause Diagnosis (candidate, not yet scoped — diagnosis only, no implementation)

Not a version — no release branch, no implementation scope, and explicitly not intended to become one without a further, deliberate scoping pass. Origin: production W1→W2 retention read at **2.4%** (3 of 127 eligible activated users, 2026-07-15), against an earlier 5.6% read (v0.32.2) that first flagged retention as "the real constraint" — the level has not meaningfully improved despite two intervening releases touching retention surfaces (v0.44.0, v0.46.0). Two independent Fable consultations (growth/retention lens and consumer-psychology/behavioral-economics lens, run with no shared context) converged on the same core diagnosis: the retention infrastructure that exists (due-concepts digest, weak-concept nudge, weekly summary) ships default-OFF and is gated behind the exact engagement it's meant to create; the first session ends in psychological completion rather than an open loop; exam date is the strongest retention primitive in the product but is optional and Board-exam-only; and single-serving-utility risk is real for anchor-less casual users but not for exam-dated reviewees, whose job is inherently longitudinal. Pricing was independently ruled out by both sessions as a current bottleneck (Free quota is essentially never hit). Full diagnosis, ranked root causes, ruled-out causes, recommended data pulls (week-1 depth, an exam-date natural experiment, acquisition-source segmentation — all queries against existing data, no code), and candidate directions (all explicitly contingent on those data pulls, none release-ready) are in `docs/claude-prompt/retention-diagnosis-session-plan.md`, with the two full raw sessions in `docs/claude-prompt/retention-diagnosis-out/`. **Data pulls run 2026-07-15**: week-1 depth came back at 3.88% (near the same magnitude as W1→W2 itself, ruling out the "session 1 is fine, only week 2's trigger is broken" reading — the two root causes are co-dominant, not one ahead of the other); the exam-date natural experiment found exam-dated users retained no better under the status quo (0/35 vs 3/94 for non-exam-dated, small-sample), which strengthens the *active* commitment-device candidate direction over the passive one; acquisition-source segmentation remains genuinely unanswerable (no UTM/referral tracking exists in the schema). The recommended v0.48.0 scope (trigger fix + open-loop session ending, both cheap and independently testable) was confirmed and shipped — see `v0.48.0` above. **Post-shipment strategy checkpoint (2026-07-15):** two Fable consultations, run after v0.48.0 merged, prioritized what comes next — full detail in the session-plan file's "Strategy checkpoint" section. Headline: talk to actual retained/churned users now (cheap, non-confounding, more informative at this scale than further cohort analysis); pull UTM/referral tracking and device mix in the same pass; pre-committed decision rule for the v0.48.0 read (any positive-or-ambiguous signal → ship H1 + H5 together as one release, not sequentially, since a 2-week cohort is too small to cleanly resolve); Unified Next-Step Resolver reframed as H5's infrastructure, not standalone app-shape work; the exam-date-users-retained-worse finding may indicate a value problem, not just a trigger problem — unresolved until the interviews happen.

## Post-v0.44.0 Conversion Audit Backlog (candidate, not yet scoped)

Not a version — no release branch, no implementation scope yet. Tier 2 (7 items) folded into `v0.44.0`; Tier 3 (18 items) is now scoped as `v0.45.0` (see above). Tier 4 (19 items, "low impact, cheap cleanups") remains here from the same 7-session conversion/retention UX audit — full detail, impact/effort ratings, and Claude Code/Codex routing per item in `docs/claude-prompt/conversion-audit-prioritized-backlog.md`. **Item 52 (echo Weekly Countdown pacing at quiz-session completion) is now scoped into `v0.46.0`** (2026-07-14, pulled in as a Concept Flashcards replacement — see `RELEASES.md`). **16 of the remaining 18 items are now scoped into `v0.47.0`** (2026-07-14 — see `RELEASES.md`); items 47 and 50 (both already fixed in prior releases) were dropped during v0.47.0's pre-scoping verification and remain closed candidates here. Item 37 was also initially dropped (its assumed cookie/goal-banner mechanism doesn't fit Learn's category-keyed guides) but was folded back into `v0.47.0` at a smaller scope once the rest of the release shipped, reusing the query-param intent pattern from item 43 instead. Also includes two explicitly deferred items (adoption-count social proof on Public Library plan cards; "Trending this week," blocked on windowed backend counts that don't exist yet). Pull from here when scoping what comes after v0.45.0 — do not re-run the audit or re-derive this list from scratch.

## App Shape, App-Like UI & Companion Authenticity (candidate, not yet scoped)

Not a version — no release branch, no implementation scope yet. Deliberately **not** conversion-related (unlike the two backlogs above) — three independent questions run through Fable about product shape and experience quality: (1) what features could deepen how Notes/Study Packs/Review Sets/Companion/Progress compose as one system, (2) how the five highest-traffic pages (Note Detail, Review Sets list, Review Set detail, Private Library, Public Library) could read as an app rather than a website, and (3) how Learning Companion content (both curator-written and the v0.42.0 AI-assist draft) could be grounded in real aggregate learner-experience data instead of reading as generic AI output. Full prompts, Fable's raw output, and a classified synthesis (Core Feature / Polish / Future Enhancement, plus explicit out-of-scope guardrails) are in `docs/claude-prompt/app-shape-session-plan.md`. **4 of the 7 Polish items are now scoped into `v0.46.0`** (2026-07-14 scope broadening, see `RELEASES.md`): shared note-card press feedback, skeleton-first initial load (Note Detail only — Review Sets list already had it), sticky search/filter toolbar, and collapse-by-default Note Detail sections. **3 items were pulled in and then dropped the same day** after direct investigation found they were misclassified as Polish — the Review Set filter facet needs a note→collection association that doesn't exist anywhere in the note-list API; the Result-Screen Companion Bridge has a premise problem (Companion content belongs to Collections, not Study Packs — resolve that scoping question before any future attempt); the Review Set feedback digest needs new schema (`FeedbackEntity` is a flat, unscoped global table today) and its paired staleness flag has no target (no "Struggle Map" evidence panel exists anywhere yet to attach it to — that panel is itself one of the still-unscoped Core Feature candidates below). All three remain here as candidates, correctly re-classified as needing real scoping passes, not quick fold-ins. The Core Feature candidates (Companion Live Milestones, Unified Next-Step Resolver, Concept-to-Note Back-Annotation, mobile bottom tab bar, Companion "Struggle Map" evidence panel) remain unscoped — nothing there is kicked off yet, and the Companion-authenticity work still has its named prerequisite (an adoption-provenance link, confirmed cheap on inspection) needing an explicit go/no-go before it's picked up. **Re-prioritized 2026-07-15** against the retention-root-cause diagnosis (see above): Live Milestones, Back-Annotation, and Struggle Map held indefinitely — none touch the proven retention constraint. Unified Next-Step Resolver reframed as infrastructure for the deferred H5 retention direction, not standalone scope — pick up only alongside H5. Mobile tab bar conditional on the device-mix data pull queued as part of the same checkpoint. Full reasoning in `docs/claude-prompt/app-shape-session-plan.md`'s correction note.

## New Capability Ideation (candidate, not yet scoped)

Not a version — no release branch, no implementation scope yet. Distinct from the three backlogs above: not conversion polish, not the composition/app-shape work, not the curriculum-auto-assembly bet — a single open-ended Fable pass asking what capability areas NoteLib has **no version of at all today**. 11 ideas classified via `docs/skills/roadmap-feature-audit.md`'s tiers (5 Core Feature, 4 Future Enhancement, 2 Low-Priority), plus 9 explicit rejections (no 6th quiz mode, no learner-to-learner content exchange bypassing curation, no auto-regeneration, no leaderboards, no freetext taxonomy). Top-ranked candidates: a due-concepts email digest and an Exam Date Countdown/paced-review feature over owned content only (explicitly not Smart Review Planning) — **both scoped into `v0.46.0`, see above**. **Second correction (2026-07-14, load-bearing):** Concept Flashcards (Idea 8) was briefly considered as a third v0.46.0 fold-in but turned out to already be a fully shipped feature (`docs/features/flashcards.md`, including a public preview) — Fable's write-up wrongly listed it as an area with no existing version. Dropped from consideration; do not re-propose it without checking `docs/features/flashcards.md` first. Photo capture of handwritten notes (Idea 6) remains the next recommended-but-unscoped idea — Core Feature-sized (new image upload + vision-extraction infrastructure), not a quick addition; **independently re-endorsed 2026-07-15** as the next Core-Feature bet, explicitly gated on the retention loop existing first (see below), not before. Full detail, corrections, and reasoning in `docs/claude-prompt/new-capability-session-plan.md` and `docs/claude-prompt/new-capability-out/01-new-capability-ideation.md`. **First correction, already applied and load-bearing:** the original top idea assumed NoteLib has no out-of-app re-engagement channel — false, a real retention-email system already ships (`docs/features/retention-emails.md`); the idea survives as a much cheaper addition to that system, not new infrastructure. Read the correction notes in the output file before scoping, not just the original Fable text. **Third correction (2026-07-15), the remaining 8 ideas re-evaluated against the retention diagnosis:** Idea 4 (Parent Readiness Digest) promoted to a conditional retention candidate, gated on the v0.48.0/H1 read — reframed as an external accountability trigger, not persona-completeness work; Idea 9 (Offline Study Pack Access)'s cost was overstated — a service worker and offline fallback already exist in the codebase (`frontend/public/sw.js`, `offline.html`), so remaining work is content-layer, not platform-layer, though it needs its own evidence leg beyond device mix (PDF export volume, offline-fallback hit rate) before it's a real trigger condition; Ideas 2 and 3 (teacher shared-results probe, class groups) folded into the Bulk Quiz Generation trigger condition below rather than tracked separately; Ideas 7 (Listen Mode) and 10 (Bilingual UI) unchanged, stay low; Idea 11 (Study Buddy) confirmed lowest — at 2.4% W1→W2, a pairing mechanic multiplies churn risk rather than countering it. Three items (Ideas 2, 4, 9) flagged as needing a real product decision before they're scopeable, not just a priority slot — full detail in `docs/claude-prompt/retention-diagnosis-session-plan.md`'s "Strategy checkpoint" Session B.

## Archived releases

Full scope for the following shipped versions moved to `docs/archive/ROADMAP_ARCHIVE.md` (see `RELEASES.md` for each changelog entry):

- `v0.35.0` - Mobile-First Builder
- `v0.36.0` - Readiness/Progress Merge
- `v0.36.3` - OCR Fast-Follow: Messaging & Feedback
- `v0.36.2` - OCR Disable Hotfix
- `v0.36.1` - Post-Release Fixes
- `v0.38.0` - Read-Path Optimization Pass
- `v0.37.4` - Idle GC & Metaspace Ceiling Hotfix
- `v0.37.3` - Study Plan Read-Path Memory Optimization
- `v0.37.2` - Plan Data Integrity Hotfix
- `v0.37.1` - Native Memory Hotfix
- `v0.37.0` - Readiness-First Plans & Mastery Integrity
- `v0.39.0` - Flexible Review Methods
- `v0.39.1` - Study Plan Builder Polish
- `v0.39.2` - Public Library Learning Experience
- `v0.40.0` - Weekly Study Plan (Exam Countdown) + Primary Review Set
- `v0.40.1` - Public Review Set Reachability

## Post-v0.40.0 Polish Backlog (7 items from live usage — candidates, not yet scoped)

Not a version — no release branch, no implementation scope yet. Surfaced by the user while using their own v0.40.0 release; captured here so the ideas don't drift/disappear before they're deliberately scoped. Research below was done via direct code trace (some Explore-agent verification hit a session limit and returned nothing — items are marked CONFIRMED where traced directly vs. PARTIAL where grounded but incomplete).

**Fold into v0.40.1 (no new item needed):**
- **Misleading "No curated Review Sets for X yet" empty state.** `dashboard-study-plan-section.tsx:150` shows this regardless of how many Review Sets the user already owns — it means "no *official curated* set for your track," not "you have none," but reads like the latter. Single-string copy fix on the exact same empty-state card v0.40.1 already rewires (see v0.40.1's "Rewire the existing empty state" bullet above) — fold in, don't ship standalone.

**Open philosophy question — blocks any related scoping until decided:**
- **Primary Review Set vs. the older Study/Exam Focus mechanism (`studyGoal`/`focusSubjects`, `docs/features/profile.md:85-108`) are unreconciled.** CONFIRMED via `progress-report-client.tsx`: on Progress they're already mutually exclusive at the view level (Primary, once set, fully supersedes the old goalSummary/milestones UI) — but the Profile page has **zero awareness of Primary Review Set** (no `primaryCollectionId` reference anywhere in `frontend/app/profile/`), so it still shows only the old per-subject Exam Focus picker, independently editable, with no cross-reference to the Primary the user separately set. Two "what am I working toward" fields, coincidentally similar-looking values, no link between them.
  - PARTIAL: whether `studyGoal`/`focusSubjects` also drive Dashboard, onboarding, or the exam-hub intent flow beyond Progress was not verified — don't assume "Primary wins" holds everywhere.
  - Recommended direction (lowest-drift, matches what Progress's code already does): Primary Review Set becomes the canonical surface; Study/Exam Focus is kept but reframed as the explicit fallback for users with no Goal, and Profile actually shows the Primary. Not purely additive — Study Focus is load-bearing as the no-Goal fallback, so this redefines what it means. Needs a deliberate product decision (user is taking this to GPT) before it becomes a roadmap item.

**Cheap, independent candidates (sequence after the philosophy question above, since it touches the same Profile/Progress neighborhood):**
- ~~Show "Adopted" vs. "Created by you" on a Review Set~~ — **already shipped, v0.40.1** (`AdoptedBadge` in `collections-page-client.tsx`, equivalent treatment in `collection-detail-page-client.tsx`, both test-covered). This roadmap entry was stale — corrected 2026-07-15 while scoping v0.49.0, which briefly re-surfaced it as a candidate before direct code inspection found it already live. Showing the *original author's name* (like Public Notes' `authorDisplayName`) remains a real, separate, larger item — needs new backend exposure (no owner/author/official field on collection responses today) and a real edge case (source since deleted/private, no author left to show).
- **Overdue color-warning on child Subject-plan cards.** `child.dueConcepts` is already rendered as plain text on the Goal detail page (`collection-detail-page-client.tsx:660`) — no color coding today. Cheap, uses existing data. Flag: a warning *color* leans toward the "monitoring" framing the locked list/execution-row anti-drift rule pushes back on; defensible only because this is the Goal's own detail surface, not a list/browse card — needs a conscious sign-off, not an automatic yes. (Distinct from Phase 2 weighted-allocation scheduling above, which remains separately deferred and ungated.)
- ~~Make Progress's per-subject "Concept Mastery" cards link to Private Library filtered by subject~~ — **scoped into `v0.49.0`**, see below.

---

## Guided Learning Initiative (Companion) — v0.41.0 and beyond

Not a version — no release branch yet, planned to kick off after v0.40.1. Origin: a product realization surfaced across several planning discussions — Review Centers are not valuable because they provide PDFs or quizzes, they are valuable because they provide **guidance** (structure, direction, pacing, coaching, confidence). NoteLib has the knowledge layer (Notes), the learning engine (Study Packs), and the journey (Review Sets) — but no **guidance layer** riding on top of the journey. This does not turn NoteLib into an online Review Center; it adds one new layer while keeping Notes as the source of truth and Subject Plans strictly academic (no "Exam Strategies"/"FAQs" masquerading as Subject Plans).

**Success criterion (the north star every phase below is judged against):** *"Every Official Review Set should feel like a premium guided learning experience rather than a collection of notes."* Not feature count, not revenue.

**The organizing insight:** of the topics discussed (Companion, AI-assisted authoring, Companion regeneration, AI-generated Review Sets, creation/adoption/discovery UX, Public Library integration, runtime AI, monetization, profile-aware terminology), only **one is a genuinely new concept** — the **Learning Companion**, a persisted, curator-authored, profile-aware, statically-served guidance layer on a top-level Review Set. Confirmed by codebase audit: no entity carries authored narrative content on a collection today (guidance today is client-side ephemeral tips only, `frontend/lib/guidance-engine.ts` — no stored content). Everything else in the discussion is either an authoring enabler for the Companion, already on the roadmap under a different name, a deferred premium tier, or a cross-cutting naming constraint:

- **Review Set creation/adoption UX** — already shipped (Builder Canvas, adopt/adopt-goal) plus already-planned refinements (Post-v0.40.0 Polish Backlog's "Adopted" badge). Not new scope here.
- **Review Set discovery** — already scoped narrowly in `v0.40.1` (Browse All) and further gated in the deferred "Review-Set-Centric Navigation" section below. This initiative does not reopen that.
- **Public Library integration** — already shipped (v0.39.2 Flashcards/Memorization preview) plus the same deferred Explore-convergence direction below.
- **Runtime AI / personalization** — deferred premium tiers, gated on the Companion existing first (see Monetization below); does **not** violate the locked "no interactive AI / no mid-exam coaching" constraint in `EXAM_MODES.md` because the Companion MVP is authored static content, not a chatbot.
- **Profile-aware terminology** — a constraint, not a feature: any "Companion" label resolves through `getCollectionLabels` (candidate new field, e.g. `companionSingular`), same as `primarySingular`.

### v0.41.0 — Learning Companion (MVP), released (base branch `releases/v0.41.0`)

- **Persisted Companion content model.** A JSONB column on the top-level `note_collections` row (not a new table) — lowest-drift, mirrors the existing `sessionState` JSONB precedent, and copies naturally with the row on adopt. Promotable to its own table later if it grows. Companion is 1:1 with a top-level collection only (mirrors the `targetCompletionDate`/primary constraint — rejected on child Subject Plans, same `400` pattern as existing hierarchy validation).
- **Four sections only, deliberately small:** Overview, Study Strategy, Common Mistakes, FAQ. **Study Timeline and Final Checklist are explicitly deferred and must NOT be static prose** — when built (v0.42.0+) they link the already-shipped, already-free **live** features (the v0.40.0 weekly countdown and readiness), never re-author them. Resources/Updates sections deferred to v0.42.0.
- **Manual authoring only in v0.41.0** — no AI generation yet; there are few Official sets today so the authoring burden is trivial, and this de-risks the content model before layering LLM on top.
- **Official Companion authoring only (MVP scope decision, not a permanent limitation).** Only the NoteLib official author can author a Companion in v1; architecture stays open to any top-level-Goal owner later.
- **Publishes with the Review Set** — hooks into the existing `updateVisibility`/`publishChildCollections` publish cascade.
- **Travels on adopt, per the locked snapshot-copy rule (v0.31.0, below).** Companion is added to `persistAdoptedGoal`'s copied set (the way `targetCompletionDate` is explicitly *excluded* — Companion is the opposite, it should travel, like a linked Study Pack does on note copy). Source edits do **not** propagate to existing adopters (same as notes today). Owner self-copy **excludes** the Companion (same category as the existing generated-content self-copy exclusion).
- **FREE for all learners.** Zero paid uplift by design in v0.41.0 — this is an activation/retention bet, consistent with the success criterion being about experience quality, not revenue.

Anti-drift: no runtime LLM call to serve a Companion (authored once, served static — zero per-view cost); no new top-level entity; no change to the 5-mode quiz contract; no change to `UserEntity`; Companion label resolves through `getCollectionLabels`.

## Archived releases

`v0.41.1 - Review Set Detail Page: This-Set Study Dashboard` shipped; full scope moved to `docs/archive/ROADMAP_ARCHIVE.md` (see `RELEASES.md` for the changelog entry).

## Post-v0.41.0 Polish Backlog (candidate, not yet scoped)

Not a version — no release branch, no implementation scope yet. Surfaced by the user while using their own v0.41.0 release.

- **App-wide CRUD success-toast feedback.** Currently a Review Set-scoped pass (edit details, set/clear primary, Companion save/clear, create, delete) reuses the existing `ToastMessage` component (`frontend/components/ui/toast-message.tsx`) with local-state + `setTimeout` auto-dismiss, matching the pattern already used in 10 other files (profile, study, admin, etc.). Extending this to every mutating action across the whole app is a separate, larger initiative — the current pattern is per-page local state with no shared queue, so app-wide rollout needs a `useToast()` provider/hook first (concurrent toasts aren't handled today). Gate: scope and design the shared provider before starting; don't replicate local-state toasts file-by-file at app-wide scale.

### v0.42.0 — AI-assisted Companion authoring + regeneration, released (base branch `releases/v0.42.0`)

- **Curator workflow:** `Generate Companion` (per section or all) → LLM draft → **mandatory human review and edit** → `Publish`. Publishing is never autonomous. Reuses the existing OpenAI service + PREMIUM/CRITIQUE model tiers — no new LLM infra.
- **Granular per-section regeneration** (Overview / Strategy / FAQ / Checklist independently, not an all-or-nothing regenerate) plus a **"Companion may be outdated"** staleness signal when the set's structure changes — a lightweight stored structure snapshot (child count / note ids / concept count) compared on read, no new job infra.
- Adds the Resources section and the Timeline/Checklist live-feature embeds deferred from v0.41.0.

### v0.42.1 — Companion & Progress Polish, released (base branch `releases/v0.42.1`)

Small UX fixes surfaced from using v0.42.0 in practice, frontend-only, no new features:

- Merge the Review Set detail page's readiness card and its "View full progress"/"Review due concepts" row into one card — they're already documented as the same Readiness tier (see `docs/features/collections.md`), this just makes the layout match.
- Fix `/progress?collectionId={id}`'s backlink: it always showed "Dashboard" regardless of entry point; now returns to the originating collection when reached via that collection's "View full progress" link.
- Considered and declined: turning collection cards' course/program metadata into a badge — stays plain text per the existing badge-classification rule (identity/state get badges, metadata does not).

### v0.43.0 — Companion "Coach Experience", Released (base branch `releases/v0.43.0`)

Origin: a product proposal to make the Review Set detail page *feel* like a coach talking to the learner rather than a set of labeled CMS fields ("Overview", "Study Strategy", "Common Mistakes", "FAQ"). Pressure-tested via architecture review before scoping.

**Finding: the proposal's own mockup conflates three different operations — only one is genuinely "same content, new presentation":**
- **(a) Relabel/re-voice authored sections** (e.g. "Overview" → "🗺️ What this covers") — same curator text, friendlier frame, order preserved. Not generation (no per-learner synthesis, same author, same review-before-publish gate); "Curation, never generation" is not implicated. (Shipped headings stay descriptive of the section's content rather than becoming a generic greeting — an earlier "👋 Welcome back" draft for Overview was rejected mid-build for exactly that reason; see the "Coach vs. Companion" refinement below.)
- **(b) Reorder/prioritize authored sections by learner context** (e.g. surface Common Mistakes first when readiness is low) — **explicitly deferred**, not part of the near-term slice. Two problems: it collides with the monetization line below (adaptive prioritization is named PRO value), and it breaks curator-authored narrative flow — Overview → Strategy → Mistakes → FAQ assumes that reading order, and later sections can reference earlier ones.
- **(c) Coach-voice composition of already-shipped live signals** — target-date pacing, the resolved next action (`getNextPlanAction`), readiness/due-concepts (`ReadinessSummary`/ConceptHealth), and terminal exam/builder actions. None of this is Companion content; it's v0.41.1's dashboard signals, already FREE, just not yet wearing a coach voice.

**Near-term buildable slice = (a) + (c).** A static coach-label mapping (same shape as `getCollectionLabels`) over `CompanionDisplayCard`, order-preserving, plus a conversational frame composed from already-loaded live signals, positioned above Progress and the authored Companion (which stays a stable, unreordered narrative). Frontend-only, no new backend call, no new persisted state — same architectural class as `pickActiveGuidance` (`frontend/lib/guidance-engine.ts`) and `getNextPlanAction`, both deterministic and frontend-only already. Shipped as `TodaysFocusCard`, which merges the former countdown/primary-action/coach-intro surfaces into one Coach card, while Progress owns the countdown summary and Companion remains reference material.

**(b) stays deferred, reserved for future PRO personalization** (see "Future, gated — Runtime Companion" below). If ever picked up, the FREE-deterministic/PRO-adaptive line already drawn there applies: rule-based deterministic reordering could ship FREE like the weekly countdown did, but genuinely adaptive/learning-pattern/LLM-driven selection is the PRO differentiator — not a re-paywalled version of deterministic logic.

**Planned Scope:**
- Coach-voice terminology mapping over `CompanionDisplayCard` (order-preserving, no reordering).
- `TodaysFocusCard` in the Coach tier, above Progress and the authored Companion, driven by the already-resolved primary action (`getNextPlanAction`) plus target-date pacing and existing quick/terminal actions.
- Short curator-authoring guidance note in `docs/features/companion.md`.
- **"View Full Guide" collapse (added mid-release, see philosophy refinement below).** `CompanionDisplayCard`'s five sections stop rendering inline; they move behind a "View Full Guide" disclosure, below Today's Focus and Progress. Frontend-only, no new data, no new persisted state — same guardrails as the rest of this release.

Anti-drift: no reordering of authored Companion sections (the narrative-flow reason for this holds for today's long-form-paragraph content model — see the "Coach vs. Companion" refinement below for why that reasoning changes if content ever becomes atomic tips); no generation; no new backend, endpoint, or persisted state; does not reopen Timeline/Checklist as authored prose (stays live-feature embeds per v0.42.0); labels continue through `getCollectionLabels`.

### Coach vs. Companion, formalized (mid-release philosophy refinement)

Surfaced from using the shipped relabel + intro in practice: swapping section headings for coach-voice copy didn't fix the actual complaint. Five long-form paragraphs stacked under friendlier labels still reads as an article, not an app. The heading-copy lever is exhausted; the real lever is disclosure and interaction, not vocabulary.

**The organizing split, going forward:**
- **Coach (dynamic).** Reacts to the learner: continue-where-you-left-off, target-date pacing, readiness, due concepts, resolved next action, and existing terminal actions. This is not a new concept — it's naming what already exists (`TodaysFocusCard` plus `ReadinessSummary`). Zero new cost.
- **Companion (timeless).** Authored, does not react to daily progress. Teaches how to approach the curriculum — mindset, expectations, common mistakes, practical advice. Should read like mentor advice, not reference material.
- **Curriculum.** Subject Plans → Notes → Practice. Unchanged, not part of this discussion.

**A correction to this release's own anti-drift reasoning, recorded so it isn't re-derived wrong later:** the (b) reordering objection above cites two reasons — a PRO-monetization collision and a narrative-flow break. The monetization citation is imprecise in isolation: only *learning-pattern/LLM-informed* selection is the PRO differentiator (see Monetization philosophy, below); deterministic, rule-based selection (a date threshold, a progress count) is FREE-safe by the same precedent as the weekly countdown. The narrative-flow reason is the one that actually holds — and only because today's Companion is five long-form paragraphs where later sections can presume earlier ones were read. That reasoning is specific to *this* content shape, not a permanent rule; see v0.43.1 below for why it changes if sections become atomic tips.

**The verdict, split by cost:**
- **Cheap (this release, frontend-only):** the Coach/Companion naming above (free, just clarity), the `TodaysFocusCard` → Progress → Companion hierarchy, and the "View Full Guide" collapse (Planned Scope, above). Together these are the actual fix for "feels like documentation" — leading with what already exists and demoting the long-form article to reference material, reachable but not the first thing shown.
- **Expensive (a distinct initiative, not polish):** atomic, individually-surfaceable "Mentor Tips" with rotation and action-linking. This needs a new content shape — `CompanionContent`'s five long-form fields can't be "surfaced as a moment" without truncating curator intent. Scoped separately below as v0.43.1, since it is not frontend-only and does not fit this release's guardrails.

### v0.43.1 — Companion Mentor Tips, Released (base branch `releases/v0.43.1`)

Origin: continuation of the philosophy refinement above. Once the Companion is reachable via "View Full Guide" rather than rendered inline, the next question is whether the *authored* content itself can participate in the experience the way the Coach cluster already does — small, individually-surfaced, action-linked moments instead of an article to read start to finish.

**Why this is a distinct version, not a v0.43.0 fast-follow in the polish sense** (unlike v0.40.1, v0.41.1, v0.42.1, which were frontend-only fast-follows on their preceding `.0`): this needs a real content-model change, not a presentation change.

- **Content model.** `CompanionContent`'s five long-form markdown fields (`overview`, `studyStrategy`, `commonMistakes`, `resources`, `faq[]`) do not support an individually-rotatable, individually-linkable tip. A tip needs its own identity, optional linked action, and optional surfacing condition — a new entity/DTO shape, not a frontend read of existing fields. This is the fulcrum: everything past this point is backend + authoring-UI scope, which is why it cannot fold into v0.43.0.
- **Authoring.** The authoring modal, v0.42.0's per-section AI-assist, and the structure-staleness snapshot all need to extend to the new shape. Mandatory human review before publish still applies — no change to "Curation, never generation" or "publishing is never autonomous."
- **Action-linking is curator-tagged, not inferred.** `TodaysFocusCard` already links *Coach* signals to actions — that part exists. Linking a curator's *authored* tip to an action (e.g. "you still have due concepts" → Review due concepts) must be a field the curator sets when authoring the tip, not something inferred at render time — inferring it would require a per-view LLM call, which v0.41.0 explicitly ruled out ("authored once, served static — zero per-view cost"). This constraint and the cheap/compliant path are the same path: deterministic, curator-tagged linking.
- **Surfacing stays deterministic.** "Show this tip within 2 weeks of the exam date" or "after N subjects completed" are date/progress rules, not learning-pattern inference — FREE-safe by the weekly-countdown precedent, not a PRO feature. Nothing here requires the adaptive/LLM-driven selection that Monetization philosophy (below) reserves for PRO.
- **"View Full Guide" stays a permanent escape hatch.** Whatever rotation/surfacing logic ships, the full authored Companion must remain reachable regardless of which triggers have fired — a learner should never permanently miss a curator's warning because its surfacing condition never happened to trigger for them.
- **Volume caveat.** Per the original Companion MVP scoping, there are still few Official Review Sets today. A "show another tip" affordance over a two-tip guide will feel hollow — this feature's perceived value scales with authored tip volume, which curators have not yet been asked to produce at this grain. Worth an explicit go/no-go check against actual authored-content volume before or during kickoff, not an assumption.

**Go/no-go check, done at kickoff (2026-07-10):** dev DB query found only 1 PUBLIC/Official top-level Review Set carrying an authored Companion (2 companions total across 7 top-level collections; the other sits on a PRIVATE collection). Decision: proceed anyway — dev/local volume is not necessarily representative of prod, and the content-model/authoring-UI work has standalone value independent of how many tips exist on day one. Recorded here so this isn't re-litigated as a fresh concern mid-release. Full scope in `RELEASES.md`.

**Scope broadened mid-release (2026-07-10):** a pre-signoff "tighten these new features" audit surfaced a real trust bug — Pro-only paywalls (Board Exam Mode, Long Exam, Difficulty Selection, Interview Practice) let a Free/Plus user select and pay for Plus without unlocking the feature — plus two lower-stakes gaps: no paywall upsell existed for the Plus/Pro-gated per-concept review-timing detail, and the Help Center had no coverage for this cycle's Companion/Coach/Mentor Tips or Primary Review Set/target-date pacing features. All three landed as additional `v0.43.1` Shipped bullets rather than a separate version, per the same mid-release-fix precedent as v0.42.0's `setCompanion` null-content guard. Full detail in `RELEASES.md`.

### Documented rule clarification (not a reversal) — enables AI-assisted authoring

The standing "Curation, never generation" rule (see v0.31.0 below, "the forbidden version is *auto-generate a personalized plan* — do not build that here") is **clarified, not reversed**, to make v0.42.0 possible:

- **Learner-facing: unchanged.** Curation over generation — a learner never gets an auto-generated plan.
- **Curator-facing (new, scoped to Official Review Sets/Companions): AI-assisted authoring, with mandatory human review before publish.**
- **Publishing: never autonomous**, in either case.

This is the same category of deliberate, written rule refinement as v0.33.0's Progress-separation reversal — recorded here so it is never mistaken for scope creep or relitigated later.

### Future, gated — AI-generated Review Sets

Curator pipeline: public notes → suggest Subject Plans → map notes → generate Companion → human review → publish. A separate, larger initiative — gated on v0.42.0's authoring-assist pipeline proving out (this reuses that pipeline rather than building a second one) and on the rule clarification above. Not scoped to a version yet.

### Future, gated — Runtime Companion (Ask Companion, Personalization)

- **Ask Companion (PLUS).** Grounded Q&A over the authored Companion content — cheap, bounded, reuses the existing Interview Practice cost-control template (feature gate + monthly quota + per-minute `AiRateLimitService` + the cheaper CRITIQUE model + capped turns), the only existing runtime/interactive LLM feature in the product today. Deliberately placed at PLUS (not PRO, where Interview Practice's PREMIUM-model generative simulation lives) because it is grounded retrieval over static content, not generation — a documented, deliberate ladder repositioning.
- **Personalized/Adaptive guidance (PRO).** Must be genuinely adaptive (learning-pattern/LLM-driven) — **not** the existing deterministic v0.40.0 weekly countdown re-labeled with a price tag, which already shipped FREE.
- Both gated on the persisted Companion existing. Personalization is additionally blocked by the open Primary-Review-Set-vs-Study/Exam-Focus philosophy question (Post-v0.40.0 Polish Backlog, above) — that question is about Profile/Progress, not the Companion, so it does **not** gate v0.41.0 or v0.42.0.

### Monetization philosophy (long-term principle, established here for future features to follow)

Codifies what is already the de facto model in this codebase (readiness was ungated to FREE in v0.33.0 as "access not billing"; interaction-heavy features already sit at PRO) rather than redesigning pricing:

- **FREE — static guidance.** The Companion itself. Near-zero marginal cost (authored once, served static), high perceived value — the activation/retention driver and the conversion hook for paid interaction/personalization. Not a giveaway; a funnel.
- **PLUS — interaction.** Ask Companion. Gives PLUS its first genuinely distinct capability (today PLUS is quota-only) — strengthens PLUS's reason to exist.
- **PRO — personalization.** Genuinely adaptive guidance, not a re-paywalled version of something already free. **Not any prioritization** — deterministic, rule-based reordering (e.g. "surface Common Mistakes first when readiness is low," see Companion "Coach Experience" candidate above) follows the same FREE precedent as the v0.40.0 weekly countdown. PRO's prioritization must specifically be adaptive/learning-pattern/LLM-informed selection. Much of that adaptive value is derivable from existing ConceptHealth with no per-query LLM for the underlying signal — high margin; only the conversational/adaptive selection logic itself carries recurring cost, controlled via the Interview Practice template.
- Applies consistently across profiles (Student/Exam-taker/Teacher/Professional) because it gates capabilities, not content, and all labels route through `getCollectionLabels`.
- No price/quota/checkout change from this principle alone — it governs how *future* features (Ask Companion, Personalization) get tiered when they're built, not a repricing of today's plans.

---

## Review-Set-Centric Navigation (deferred future direction — not scoped to any release)

Not a version — no release branch, no implementation scope yet. Captured here so we stop designing around abstractions (chiefly Exam Hub) that may no longer be the right shape, per the user's explicit request to lock the long-term architecture without building it all now. **Gate on the Primary Review Set concept (shipping in v0.40.0) proving useful in real usage before committing any of this.**

**Partial exception (v0.41.1, released):** the Review Set detail-page hierarchy (Identity → Current Journey → Primary Action → Readiness → Guidance → Subject Plans/Notes) and the matching `/collections` list-card Primary treatment shipped as a narrow, frontend-only re-composition — see the "v0.41.1" section above. This did **not** advance the nav-shape/Dashboard/Explore parts of this direction, which remain gated exactly as stated below.

Origin: structural realization that Review Sets have become NoteLib's primary study experience, not a secondary feature alongside Public Library/subjects. When the product was designed, users mostly discovered notes through the Public Library, so Dashboard, Progress, and Exam Hub were all built subject-first. That assumption no longer holds now that Official Review Sets, Subject Plans, smart progression, readiness, and (v0.40.0) weekly plans exist.

Direction, as stated by the user:

- **Official Review Set catalog as the scalable replacement for hand-built per-profession pages.** Publishing another Official Review Set should require no new frontend, versus adding an Exam Hub page per profession (Civil/Electrical/Mechanical Engineering, Nutrition, Midwifery, …).
- **Public Library preserved as a distinct discovery path.** "I want to browse notes" (Public Library) and "I want to study for an exam" (Review Sets) are two valid entry points to the same notes; the Public Library is not absorbed or removed.
- **Dashboard and Progress reorganized around the Primary Review Set** instead of subject/course-program. Dashboard asks "what review are you preparing for?"; Progress answers "what's happening with my primary study journey?" Subject mastery still exists — it becomes a facet of the Review Set rather than the primary navigation.
- **Eventual nav shape:** Dashboard / My Reviews / Library / Explore / Progress, where Explore houses the Official Review Set catalog + Public Library + trending/community.

Corrections recorded from the codebase research behind this (so they aren't relitigated later):

- **Exam Hub is not the maintenance burden the original framing assumed.** It is a single dynamic `/exam/[slug]` route over a small hardcoded config (`frontend/lib/exam-hub-config.ts`), and it does a distinct job an authenticated catalog does *not* replace: **anonymous / SEO acquisition** (organic search → signup; authenticated visitors route into a filtered Public Library). The recommended resolution is **convergence, not deletion** — an Explore surface that houses the Official Review Set catalog + Public Library, and lets the existing `/exam/[slug]` SEO pages deep-link into a matching Official Review Set once one exists. Retiring the SEO surface would cost organic acquisition.
- **Naming stays profile-aware.** Any "Primary Review" / "My Reviews" language must resolve through the existing `getCollectionLabels` pattern, not ship as hardcoded universal copy — the concept is profile-agnostic; the label is not (Study Plan / Review Set / Lesson Plan / Collection).
- **Progress reorg is a default-view change, not a re-scoping.** The `PlanPicker` + `?collectionId=` machinery already exists; a future reorg defaults it to the primary and must keep the all-subjects rollup reachable so notes outside any Review Set aren't orphaned, and must not undo the v0.36.0 Progress/Readiness unification.

---

## Note Detail readiness as its own tab (candidate)

Idea surfaced while fixing v0.36.1's Note Detail readiness placement: instead of showing the readiness summary inline (currently just before Performance Overview on every tab), give it its own tab alongside Summary / Key Concepts / Quiz / Full Notes.

Blocker: the current 4-tab bar already fills the width of a standard iPhone viewport exactly. Adding a 5th tab would overflow and needs a scroll/overflow affordance (e.g. an arrow or horizontal scroll) so the added tab isn't silently hidden on mobile — that affordance needs its own design pass, not a fast-follow. Not scoped into any release yet.

---

## Archived releases

Full scope for the following shipped versions moved to `docs/archive/ROADMAP_ARCHIVE.md` (see `RELEASES.md` for each changelog entry):

- `v0.33.0` - Study Plans as a Retention Engine
- `v0.33.1` - Study Plan polish & Curated Plan Coverage
- `v0.33.2` - Plan Detail Redesign (view/edit split)
- `v0.33.4` - Builder Surface Clarity
- `v0.33.3` - Recursive Goal Adopt

## Deeper plan nesting — study-plan-within-a-study-plan (candidate, nice-to-have)

The 2-level Goal → Subject model is intentionally constrained. Going to 3+ levels is **feasible but a real project, not a constraint flip** (`parent_collection_id` is self-referential so the *column* supports depth, but every shipped invariant assumes 2): N-level needs real ancestor-walk **cycle detection** (today 2-level makes cycles impossible by construction); **recursive readiness rollup** (today sums *direct* children; the no-cross-subject-dedup rule gets thornier each level); **adopt-recursion**; per-level `sibling_position`; and a tree/breadcrumb builder UX. Genuinely nice-to-have, later — see `docs/archive/STUDY_PLAN_HIERARCHY_PLAN.md`.

---

## Archived releases

Full scope for the following shipped versions moved to `docs/archive/ROADMAP_ARCHIVE.md` (see `RELEASES.md` for each changelog entry):

- `v0.34.0` - Journey: Goal-First Study Experience
- `v0.29.0` - Bulk Generation & Generation-Context Correctness
- `v0.29.1` - Bulk Generation Polish
- `v0.30.1` - Copy Flow Polish
- `v0.30.0` - Readiness Signals
- `v0.31.0` - Adoptable Study Plans (v1)
- `v0.31.1` - Adoptable Study Plans Discovery & Status
- `v0.31.2` - Analytics Integrity & Funnel Visibility
- `v0.32.2` - Conversion Diagnosis & Quota Honesty
- `v0.32.1` - Monetization Surfacing & Pricing Clarity
- `v0.32.0` - Account & Communication Controls

## Bulk Quiz Generation & Teacher-Flow Polish (candidate, gated on teacher users; version number TBD — do not confuse with the shipped v0.35.0 - Mobile-First Builder)

Theme: reduce the friction of turning material into quizzes. Builds on the v0.27.0 collections spine and the v0.29.0 bulk-generation foundation. **Deferred (was v0.33.0, before that v0.32.0, earlier v0.31.0, before that v0.30.0, originally v0.29.0)** — we have no teacher users yet, so this only schedules once a teacher cohort exists; it may slip further. (v0.33.0 was repurposed for the Study Plans retention work above.) **Honest remainder after v0.29.0:** v0.29.0 builds the shared batch-orchestration + quota foundation for bulk *content* (note + Study Pack) generation from topics; this release extends that to **collection-level bulk *quiz* generation over existing notes** plus async quiz generation, and bundles three teacher-flow quiz-preview polish fixes. Make quiz generation async (like the Study Pack pipeline), then add a collection-level bulk action that batches the universal per-note pipeline.

**Formalized as an explicit trigger condition, 2026-07-15** (post-v0.48.0 Fable strategy checkpoint, `docs/claude-prompt/retention-diagnosis-session-plan.md`): the 5-consecutive-deferral pattern was confirmed as correct — teachers can already generate quizzes per-note, so missing bulk-batching isn't what's blocking teacher adoption; zero teacher users is a positioning/distribution problem, not a feature gap — but re-litigating the same decision every release cycle is real waste. This now **auto-schedules once ≥5 active teacher accounts exist**, and is out of per-release scoping consideration until that threshold is hit. **New Capability Ideation's Idea 2 (teacher shared-quiz-results probe) and Idea 3 (class groups) fold into this same trigger, not tracked as separate candidates** — when the threshold fires, Idea 2 (cheapest demand test) ships first, and its result decides between this item and Idea 3.

Locked direction:

- **The universal spine is preserved.** Every profile still generates a Study Pack before a quiz — this is intentional, not a funnel to remove (consistency across easy profile switching + uniform quota). Bulk solves the friction by *batching* the same pipeline, never by forking a profile-specific shortcut.
- **Profile-aware framing, not a fork.** Teacher emphasizes quizzes, Student study packs, via the existing Study/Exam Focus copy mechanism — never per-profile pipeline branches or hardcoded `if (TEACHER)` checks.
- **No new quota category, no collection-level AI synthesis.** Each note spends one existing per-note credit per artifact; bulk is a fan-out of per-note generation, not a synthesized collection document (Option B stays deferred).
- **Bulk is explicit, not an import side-effect.** One deliberate user click for the batch; preserves the explicit-generation rule. DOCX export and shareable quiz links stay Teacher/Admin only.

Scope:

- **Teacher quiz-preview polish** — move the ⋯ context menu to the top-right of the note title; remove the redundant "Correct Answer" panel (the choice already shows a ✓ Correct badge + highlight, no a11y loss); render the question stem through `QuizQuestionText` so `Statement N:` lines break onto separate lines (the teacher preview is the only quiz view rendering raw stem text).
- **Async quiz generation** — mirror the Note → Study Pack pipeline: status field (`GENERATING` / `READY` / `FAILED`), task-executor enqueue, frontend polling. `GeneratedQuizService.generate()` is currently synchronous. Prerequisite for bulk.
- **Collection-level bulk generation** — batch the universal per-note pipeline across a collection. The hard part is **quota-aware partial execution**: generate as many as quota allows, report completed vs. blocked, upsell — never fail the whole batch.

---

## Archived releases

Full scope for the following shipped versions moved to `docs/archive/ROADMAP_ARCHIVE.md` (see `RELEASES.md` for each changelog entry):

- `v0.28.0` - Feature Discoverability & Activation
- `v0.27.0` - Material Import & Collections
- `v0.26.1` - Guidance System
- `v0.26.0` - Exam Depth
- `v0.25.1` - Polish & Quick Review Fixes
- `v0.25.0` - Exam Capture & Goal Setting
- `v0.24.0` - Guided Learning
- `v0.23.0` - From Readers to Learners
- `v0.20.0` - Conversion & Re-engagement
- `v0.21.0` - Personalized Discovery & Library Organization
- `v0.22.0` - Course & Subject Discovery
- `v0.18.0` - Profile Completeness & Communication
- `v0.19.0` - Multi-Note Depth & Simulation Parity
- `v0.16.0` - Conversion & Growth
- `v0.17.0` - Quiz Quality & Depth
- `v0.15.2` - UX Cleanup & Bug Fixes
- `v0.15.1` - Teacher Power Features
- `v0.15.0` - Premium Mode Uplift + Cost-Control Quota Refactor
- `v0.14.0` - Grow the Surface, Deepen the Practice
- `v0.13.0` - Complete the Promise, Reach New Audiences
- `v0.12.0` - Learning Experience, Discovery, and Retention
- `v0.11.0` - Completed

## Current Product Shape

Navigation:

- Main:
  - Dashboard
  - Library
  - Public Library
- Account:
  - Profile
  - Settings
  - Admin (admins only)

Core routes:

- `/dashboard`
- `/library`
- `/public/library`
- `/notes/{id}`
- `/notes/{id}/sessions/{sessionId}` for session review
- `/public/library/{subject}`
- `/public/library/{subject}/{slug}`
- `/public/profile/{userId}`

Current session-review UX:

- desktop and mobile both open the same dedicated session-review page from `Recent Sessions`
- Note Detail stays the entry point for history, while the dedicated review page owns focused answer review

## Future Directions

### Background Quiz Pre-Generation (de-scoped from v0.17.0)

Challenge Quiz was the original pooling candidate but was de-scoped: its progressive generation model (5 → 10 → 15 → 20 questions on demand) already limits the initial wait to a single 5-question LLM call, so pre-generation adds LLM cost without a meaningful UX benefit. Additionally, pre-generating the initial batch and then generating extensions on demand would introduce concurrency waste (pool generation and on-demand generation could race for the same note).

Revisit for a different mode if a cold-start bottleneck is confirmed by usage data — e.g., a mode with a fixed long question set where the full generation is the bottleneck (Long Exam already uses the `ExamQuestionPoolService` pool mechanism for this). Do not pre-generate Challenge Quiz.

### Exam-mode work (planned)

- **Multi-note Long Exam** — shipped in v0.14.0
- **Board Exam advanced result analytics** — promoted into the active v0.15.0 premium-mode result presentation scope
- **Multi-note Board Exam** — planned for v0.19.0; see v0.19.0 section above for full spec
- **Long Exam tier promotion to Plus** — only if usage data justifies the LLM cost; not part of the current v0.15.0 cap refactor unless the cost review supports it
- **Planning-only** — cross-profile mode unlock (Students opting into Board Exam without changing profile); curated exam decks / cohort content (Pro+); cross-profile journey (Student → Board Taker upgrade flow with continuity)

### Premium mode uplift + cost-control quota refactor

Promoted to the active `v0.15.0 - Premium Mode Uplift + Cost-Control Quota Refactor` section above.

### Interview Practice evolution

Initial evaluation is promoted to the active v0.15.0 section. The following remain unsequenced follow-up directions and should not be committed until Interview Practice v1 has usage data from at least one billing cycle.

- **Multi-note Interview Practice (smart context aggregation)** — generate from the base note plus related notes that share `courseProgram` and at least one tag; cap at 2–3 sibling notes to manage prompt size and per-session cost; the dashboard entry already selects the most relevant note, so multi-note adds breadth across a topic, not a replacement for that smart selection; do not implement until v1 usage data confirms users want wider scenario coverage than a single note provides
- **Structured interview templates by role/job family** — opinionated section breakdowns (e.g. Backend Engineer = PL fundamentals + DB + Behavioral); requires either a curated role taxonomy or a user-defined template builder; do not build until v1 usage data shows real demand and the section-aware generation prompt's limitations are observed
- **Open-ended / conversational evaluation** — replace MC structure with free-text answers and AI rubric scoring; architecturally heavy (new session schema, new evaluation pipeline, new result model); only consider if MC + critique format hits its ceiling and Pro users explicitly ask for it
- **Profile / role enrichment** — capture target role explicitly on the user profile (instead of inferring from notes) to drive better generation context; bigger architectural decision; do not bundle with any of the above — design separately
- **Interview Practice tier promotion to Plus** — only if v0.14.0 usage data justifies the LLM cost; current model split (gpt-4.1 generation + gpt-4.1-mini critique) is what makes Pro-only economically viable, and lowering the tier requires re-running that math

### Lesson Plan for Teachers (future, unsequenced)

A lightweight collection entity grouping an ordered set of notes into a lesson plan for teacher-profile users. No AI synthesis at the plan level — a lesson plan is a playlist, not a synthesized document.

- New `LessonPlan` entity: title, description, ordered list of note references with optional week/topic labels per item
- No new AI generation at plan creation; Study Pack and Quiz generation still happen per-note using existing quotas — no new quota category
- Teacher dashboard gains a "Lesson Plans" section alongside the library; notes remain individually owned and independently editable
- DOCX export from a lesson plan produces a multi-section packet: one quiz section per note in lesson-plan order
- "Generate Quiz for Lesson Plan" toolbar action generates quizzes for each note in sequence, consuming the teacher's existing quiz quota per note
- Requires backend: new `LessonPlan` entity + ordering join table; frontend: lesson plan creation UI + DOCX multi-section export
- Do not implement Option B (multi-note AI synthesis across all notes in the plan) in v1 — risk of lower-quality synthesis and significantly higher LLM cost per plan; Option A (collection model) delivers the organization and sequencing value teachers need without new AI spend

### Study Pack Section Improvements (future, unsequenced)

The current Study Pack format (Overview / Key Idea / Core Details / Why It Matters / Quick Recall) is consistent but template-locked — every note produces the same five sections regardless of subject or content type. Planned improvements in order of effort:

- **Common Misconceptions section** — names what students typically get wrong about the topic; high quiz-prep value; prompt-only, no schema change; hotfix-deployable
- **Richer Quick Recall** — expand term-definition pairs to include a memory hook (analogy, mnemonic, or visual cue); prompt-only; hotfix-deployable
- **Comparison tables** — when the note contrasts two or more concepts, generate a structured markdown table instead of parallel bullet lists; prompt-only; hotfix-deployable
- **Concept relationships** — prerequisite chains ("Understand X before this") and contrast pointers ("Different from Y because..."); prompt-only; hotfix-deployable
- **Subject-adaptive section templates** — STEM notes get an Equations + Variables block and a Worked Example; humanities notes get a Timeline or Key Arguments section; may require a `sectionType` field addition to the key concept schema if the current JSONB storage cannot accommodate variable section shapes; requires a design pass before implementation

The first four improvements are prompt-only and can ship as hotfixes without schema changes. Subject-adaptive templates require a design review on the key concept schema before implementation and should not be bundled with the prompt-only items.

### Public Library Discovery — Future Items

- **Trending this week section (H)** — a new discovery section above Featured showing notes gaining traction in the last 7 days; blocked on backend: `NoteListItemResponse` has no windowed engagement fields; requires `recentCopyCount` / `recentLikeCount` or a precomputed rolling 7-day aggregate before this section can be built correctly; do not implement under a "Trending" label using lifetime totals — the signal would be misleading
- **Subject landing pages (J)** — moved to v0.14.0 scope; `/public/library/[subject]` proper server-rendered landing pages with per-subject metadata and decay-ranked note cards

Potential expansion areas after `v0.8.0`:

- richer note workspace
- deeper progress insights from quiz history
- board-exam-specific recommendations and weak-area planning
- optional public-profile enhancements such as followers, likes, and creator bios
- optional snapshot/history tables if product value is proven

### Billing Improvements (Future)

- **Exam-cycle pass** — promoted to v0.26.0 Track 3. See v0.26.0 section.
- recurring subscription support
- coupon-code entry UI
- cancel subscription flow
- billing portal / self-serve billing management
- automatic renewal
- invoices / receipts UI
- billing history UI improvements
- plan switching and downgrade flows
- provider-managed recurring billing via `provider_subscription_id`

### Account Management (Future)

- forgot password flow
- change password from Settings
- email verification and account-security improvements beyond the current launch flow

### Connected Account Management / Auth Provider Management (Future)

Future improvements for users with multiple sign-in methods. The Google OAuth foundation (account linking, `email_verified` guard, Profile sign-in method status) is in place; these are follow-on improvements that require their own design pass before implementation.

Potential scope:

- unlink Google account safely — must prevent lockout when Google is the only sign-in method
- add/change password for Google-only users — allow switching to or adding email/password without forcing a full re-signup
- multiple provider support — Apple, Microsoft, etc.; do not add until Google is stable and provider abstraction is reviewed
- connected account security UX — notify users by email when a new provider is linked to their account
- account recovery flows for social-login users — what happens when Google revokes access or the associated email changes
- provider conflict resolution UI — surface a clear choice when a Google email matches an existing email/password account
- recent login/session visibility — show sign-in history and active sessions in Settings for security-aware users
- email change flow with connected providers — changing the NoteLib email when a Google account is linked needs careful sequencing

Implementation notes for future reference:

- the provider abstraction should live in a shared auth provider layer, not scattered across login and signup flows
- lockout prevention: never allow unlinking the only auth method unless an alternative is confirmed first
- Google-only users have no password; the "add password" flow must go through a secure email-based credential creation path
- do not add more providers until the connected-account management UX is designed; adding providers without management UX creates a support burden

### Public Library persona filtering (roadmap)

Planned for a future release after the mode system matures:

- persona-based note recommendations in Public Library discovery (same-profile notes ranked higher)
- cross-profile discovery still allowed so learners can find materials outside their profile type
- filtering UI: optional "Relevant to me" toggle that uses the current user's profile mode for ranking
- implementation must remain additive — no ranking change without the toggle enabled
- do not build until there are enough public notes per profile type to make filtering meaningful

## Known UX Fixes (cross-cutting, no version gate)

These are correctness fixes that ship as soon as they are ready and are not held to a version milestone.

- ~~**AI suggestion modal survives navigation**~~ ✅ — fixed in v0.15.1 branch: `awaitingGeneratedMetadataSuggestionRef` was in-memory-only and reset on component remount; navigating away mid-generation and returning silently skipped the AI title/subject/tags modal; replaced with a `sessionStorage` key (`notelib-awaiting-suggestion:{noteId}`) that is set when generation starts and cleared after the modal fires; `loadDetail` re-arms the ref from storage when returning to a still-generating note so the polling effect can still trigger the modal on completion.

## Product Learning Loop

Capture -> Generate -> Review -> Improve -> Copy -> Repeat

Roadmap decisions should reinforce this loop rather than one-time output generation.

## Legacy planning context

Older phase-by-phase roadmap details are preserved in `/docs/legacy/ROADMAP.md`.
