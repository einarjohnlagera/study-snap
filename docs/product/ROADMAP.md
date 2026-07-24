# ROADMAP.md - NoteLib

Rebrand note: StudySnap has been rebranded to NoteLib while preserving current database schema naming unless explicitly changed.

Goal: evolve NoteLib from a one-shot generator into a reusable note-first study workspace.

## Current Release Baseline

No version is currently kicked off. **`v0.58.0 - Explore Convergence` is no longer the immediate next version** — the 2026-07-23 decision to proceed straight to it was reversed on 2026-07-24 following a signup surge and a reusable-assets product realization; see "Company Redefinition Roadmap — Phase Detail" below for the resequenced plan (a diagnostic read + a new "Reusable Practice Assets & the Return Loop" initiative now come first, each still pending owner ratification). See the Backlog Index row below for the full decision history.

`v0.57.0 - Practice-First Activation Onboarding` is the previous released version (on `releases/v0.57.0`, cut from `main` after v0.56.0 merged) — Phase 1 of the Company Redefinition roadmap (see "Company Redefinition (Boardready-Model Re-Architecture, 6 docs)" in the Backlog Index below, and `docs/claude-prompt/company-redefinition-out/06-unified-roadmap.md`). See its section below.

`v0.56.0 - Weak-Concept Explanation Links` is the previous released version (on `releases/v0.56.0`, cut from `main` after v0.55.0 merged) — see its section below.

`v0.55.0 - Result-Screen Companion Bridge` is the previous released version — see its section below.

`v0.54.1 - Public Note Copy Correctness Fixes` is the previous released version — see its section below.

`v0.54.0 - CPALE Exam Hub (Wave 2)` is the previous released version — see its section below.

`v0.53.0 - SEO Discoverability: Exam Hub Depth & Organic Attribution` is the previous released version — see its section below.

`v0.52.1 - Early-Lifecycle Feedback Signals` is the previous released version — see its section below.

`v0.52.0 - Proactive In-App Feedback Prompts` is the previous released version — see its section below.

`v0.51.1 - Dashboard Stage-1 Limit Wiring` is the previous released version — see its section below.

`v0.51.0 - Read-Path Performance Pass II` is the previous released version before that — see its section below.

`v0.50.4 - Exam Hub Discovery Polish` is the previous released version — see its section below.

`v0.50.3 - Public Note Copy Flow & Related-Notes Consistency` is the previous released version — see its section below.

`v0.50.2 - Note Card Content Consistency` is the previous released version — see its section below.

`v0.50.1 - Mobile UI Polish` is the version before that (on `releases/v0.50.1`, cut from `main` after v0.50.0 merged) — a fast-follow patch batch: tab bar refinements (filter-retaining Library/Public Library links, a backend-persisted show/hide preference — the icon-only variant was implemented then reverted after a consumer-psychology review) plus two small unrelated pre-existing UI issues (Review Set description truncation, Progress page milestone empty state/Concept Mastery subject-row rework) surfaced by direct user report. Not retention-flavored. See `RELEASES.md`.

`v0.50.0 - Mobile Bottom Tab Bar` is the version before that (on `releases/v0.50.0`, cut from `main` after v0.49.0 merged) — a persistent 4-tab mobile bottom navigation bar (Dashboard, Library, Review Sets, Public Library), gated on device-mix evidence from the App Shape Fable proposal and un-parked by a 2026-07-15 production pull showing ~75% mobile usage by distinct users. Navigation-shape work, not a retention experiment — orthogonal to the concurrently-accruing v0.48.0 cohort read. Mid-release scope addition: the 3 held instrumentation pulls (UTM/referral tracking, offline-fallback hit rate, browse-without-adopt tracking) folded in after an explicit 2026-07-15 decision to instrument — analytics-collection only, no new UI surfaced from the data yet, so it doesn't confound the concurrently-accruing v0.48.0 cohort read either. Also shipped a same-day sitemap fix (Exam Hub pages), found while scoping a separate SEO strategy question. See `RELEASES.md`.

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
| Retention H1 + H5 (commitment device + pre-decided return action) | `retention-diagnosis-session-plan.md` | Conditional — next up. Confirmed by `next-priority-new-user-focus-out/01-next-priority-new-user-focus.md` to fit "new users to retain" precisely (targets a user's first 1-2 weeks after signup, not lapsed/churned users) — do not conflate with the churned-user interview item below despite sharing a source doc | v0.48.0 cohort read positive-or-ambiguous (~late July 2026). v0.48.0 merged 2026-07-15; the 14-day W1->W2 window for a cohort that actually experienced the shipped changes doesn't close until 2026-07-29 — not yet reachable as of this review. Re-check query ready: `next-priority-new-user-focus-out/02-h1-h5-cohort-recheck-and-cpale-depth.sql` Query 1 | 2026-07-21 |
| User interviews (retained + churned exam-dated, script written) | `05-interview-script.md` | Active — do now, ready to run. **The 3 analytics pulls that were bundled with this row are done (2026-07-22) — see the retention-diagnosis paragraph below for results** | none | 2026-07-22 |
| 3 pulls needing real instrumentation first (UTM/referral tracking, offline-fallback hit rate, catalog "browse" tracking for the browse-without-adopt metric) | `retention-diagnosis-session-plan.md` "Strategy checkpoint" | **Shipped — v0.50.0** | explicit decision to instrument — ✅ made 2026-07-15 | 2026-07-15 |
| SEO / organic search strategy (why NoteLib doesn't surface for exam-named searches like "free PNLE notes"; sitemap/content/measurement plan) | `seo-strategy-out/01-seo-strategy.md` | **Consolidated status (was split across two stale rows, merged 2026-07-22):** 9 candidates total. P2 (vocabulary pass) shipped in v0.50.4. P4/P5/P6 shipped in v0.53.0. P1 (GSC setup) and P3 (exam-named Learn guides) remain open, non-engineering items. P7, P8, and P9 (Wave 2 hubs) each now have their own Backlog Index row — see "Exam quick-facts block," "Off-page community/backlinks," and the CPALE / Wave 2 rows elsewhere in this table | P1: domain access, still open. P3: a curator, still open. P7/P8/P9: see their own rows | 2026-07-22 |
| P7 — Exam quick-facts block per hub (schedule, PRC, subject areas) | `seo-strategy-out/01-seo-strategy.md` | Future Enhancement, not indexed until this cleanup pass (2026-07-22). Deferred: creates a recurring editorial maintenance obligation | see first from GSC (P1) whether these queries even reach NoteLib before building | 2026-07-22 |
| P8 — Off-page: community presence and backlinks (nursing/education student communities) | `seo-strategy-out/01-seo-strategy.md` | Future Enhancement, non-engineering, not indexed until this cleanup pass (2026-07-22). The only lever against the dominant domain-authority blocker; not a code deliverable | sequence after P2-P4 ship (done) — don't drive links to unfinished pages; still needs an owner to actually do outreach | 2026-07-22 |
| Icon-only mobile tab bar on Note Detail / Challenge Quiz result (v0.50.1, unpushed) — consumer-psychology sanity check after second-guessing the shipped decision | `tab-bar-icon-labels-out/01-consumer-psychology.md` | **Resolved — revert to full labels** | none, decision made | 2026-07-16 |
| Progress page subject row clickability (v0.50.1, unpushed) — UX affordance check after the Concept Mastery redesign's accent-border removal made rows look flat/non-clickable | `progress-subject-row-clickability-out/01-affordance-review.md` | **Resolved — reuse `getBrowsingCardClassName()` + trailing chevron** | none, decision made | 2026-07-16 |
| Note preview vs. Study Pack summary card content strategy (public note detail's two related-notes sections, Public Library grid, private Library grid) | `note-preview-vs-summary-out/01-card-content-strategy.md` | **In Progress — v0.50.2 (Phase 1)** | Phase 1: kicked off, see `RELEASES.md` v0.50.2. Phase 2 (origin tracking): stays parked, needs an explicit product go-decision on whether the measurement/Featured-integrity value is worth the migration+instrumentation cost — not part of v0.50.2 | 2026-07-16 |
| Exam Hub page card inconsistency (`/exam/{slug}`) — `ExamNoteCard` was a near-identical copy of the subject-landing page's pre-fix bespoke card | `RELEASES.md` v0.50.2 | **Shipped — v0.50.2** | migrated to `SharedNoteCard`'s single-excerpt cascade alongside the subject-landing page fix — all six note-card surfaces now share one content rule | 2026-07-16 |
| Duplicate "Browse {Hub} hub →" link on public note detail — when the note's course/program maps to an Exam Hub, the page shows a callout banner ("Preparing for the PNLE? ... Browse PNLE hub →") directly above the "More {Course/Program} notes" section, whose own link repeats the identical label pointing at the same URL | `RELEASES.md` v0.50.2 "Consistent related-notes section link labels" | **Stale row — already resolved.** Re-verified 2026-07-21 during v0.54.1: the callout banner is the only "Browse {Hub} hub →" link on the page; the section header uses a distinct "See all in {Subject}" label pointing at the filtered Public Library instead. Confirmed by existing test `page.test.tsx` "shows the exam hub callout banner exactly once...". No code change needed | none, resolved | 2026-07-21 |
| Public note copy/quiz flow + related-notes link consistency | `public-note-copy-and-related-links-out/01-copy-flow-and-link-consistency.md` | **Shipped in v0.50.3** — copy-as-is + "See all →" + 2-col grid | none, shipped | 2026-07-17 |
| **Smart Review Planning (Internal Curator, 7 docs)** | `docs/claude-prompt/fable-out/01–07`, sourced from `fable-smart-review-audit-session-plan.md` (the S1–S7 prompt sequence) and the original unscoped brief `smart-review-planning-and-product-language.txt` | Parked. **Partially folded (2026-07-23):** the Company Redefinition roadmap's Phase 3 carves out and re-gates only the bounded-object-model / cross-user-question-pool / Reviewer-relabel slice (see `company-redefinition-out/06-unified-roadmap.md` §Phase 3) — the curriculum-authoring pipeline itself (templates/matcher/gap-fill/Plan-My-Review wizard) stays exactly as Parked, same gate below, unchanged. Product-language work also now lives under this effort's Phase 4 (`company-redefinition-out/05-packaging-and-terminology.md`, extending `fable-out/06`'s rename map) — no separate product-language row is being added | interviews confirm content-gap churn AND manual coverage sprint proves lift AND hand-curation saturates | 2026-07-23 |
| Manual Official-coverage sprint (hand-curate ALE/PNLE/LET) | this checkpoint (2026-07-15) | Conditional | interviews surface "no content for my exam" as a churn reason | 2026-07-15 |
| Photo Capture of handwritten notes (Idea 6) | `new-capability-out/01-new-capability-ideation.md` | Held | retention loop proven (W1→W2 lift confirmed) | 2026-07-15 |
| Parent Readiness Digest (Idea 4) | same | Conditional | H1 read positive + explicit product decision (email-only vs. dashboard shape) | 2026-07-15 |
| Offline Study Pack access (Idea 9) | same | Held — one leg down | heavy mobile (✅ confirmed 2026-07-15, ~75%) AND (~~PDF export volume~~ ruled out, essentially unused, 1 export ever OR offline-fallback hit rate, not instrumented OR interview signal, not yet run) | 2026-07-15 |
| Unified Next-Step Resolver | `app-shape-session-plan.md` | Merged into H5 | ships only alongside H5, not standalone | 2026-07-15 |
| Mobile bottom tab bar | same | **Shipped — v0.50.0** | device-mix pull shows heavy mobile usage — confirmed, ~75% mobile by distinct users | 2026-07-15 |
| App Shape Core (Live Milestones, Concept-to-Note Back-Annotation, Struggle Map) | same | Held indefinitely | retention constraint clears | 2026-07-15 |
| App Shape Polish stragglers (sticky toolbar re-attempt, Review Set filter facet, feedback digest) | same | Held / needs real scoping | Struggle Map (above) ships first for the digest item; others need a scoping pass | 2026-07-15 |
| Result-Screen Companion Bridge — surface the relevant curator-authored Common Mistakes/Study Strategy excerpt on the quiz result screen | `app-shape-out/01-app-shape-features.md` item 4 | **Scoped into v0.55.0.** Premise problem resolved 2026-07-22 by reusing the `users.primaryCollectionId` pattern the v0.46.0 Weekly Pacing Echo already shipped for the same result-screen slot | none, in progress | 2026-07-22 |
| Link missed/weak concepts on quiz result screens to their Study Pack explanation | `study-effectiveness-out/01-study-effectiveness-ui-pricing.md` §1 item 1 | **Shipped — v0.56.0.** Quick Review, Adaptive Practice, Challenge Quiz, and Board Exam result weak-concept entries now deep-link to matching Key Concepts anchors on the source note; unmatched concepts remain plain text | no backend, schema, generation, scoring, or ConceptHealth-write changes | 2026-07-22 |
| Study Effectiveness / UI Polish / Pricing Fit — remaining candidates (Note Detail tab-order/reading-flow mismatch, Study Pack scope surfacing, Key Concepts readiness sort, Adaptive Practice per-question rationale tag, Review Set Detail + result-screen card-accretion layout pass, collapsed-Companion teaser, twice-missed-concept re-explanation, Plus-tier review-timing-gate instrumentation, Difficulty Selection moved Pro→Plus) | `study-effectiveness-session-plan.md`, `study-effectiveness-out/01-study-effectiveness-ui-pricing.md` | Candidate, not yet scoped — one item (above) picked up for v0.56.0, rest unscoped. The Difficulty Selection Pro→Plus item is a pricing-structure decision (Plus currently has no qualitative reason to exist since Free quota is non-binding) needing product sign-off, not just a config flip | needs a scoping pass per item; Difficulty Selection item needs a pricing-strategy decision first | 2026-07-22 |
| Bulk Quiz Generation & Teacher-Flow Polish (+ Ideas 2/3 folded in) | `ROADMAP.md` §Bulk Quiz Generation | Held | ≥5 active teacher accounts | 2026-07-15 |
| Listen Mode / Bilingual UI / Study Buddy (Ideas 7, 10, 11) | `new-capability-out/01-new-capability-ideation.md` | Low priority | interview language/social/loop signal | 2026-07-15 |
| PDF export surfacing (near-zero usage — 1 export, ever) | `retention-diagnosis-session-plan.md` "Fourth Fable checkpoint" | Parked — do not build | retention funnel + interview signal on offline demand both resolve; value-vs-discovery is currently undeterminable (see checkpoint) | 2026-07-15 |
| Conversion-audit deferred pair (adoption-count social proof, "Trending this week") | `conversion-audit-prioritized-backlog.md` | Held | windowed backend counts get built | pre-2026-07-15 |
| AI-generated Review Sets / Runtime Companion (Ask Companion, Personalization) | `ROADMAP.md` §Future, gated | Parked. **Split (2026-07-23):** the "AI-generated Review Sets" half is effectively closed/ruled out — the Company Redefinition effort re-affirms the locked curation-never-generation architecture (see `company-redefinition-out/06-unified-roadmap.md`). "Runtime Companion / Ask Companion / Personalization" is untouched by that effort and stays Parked here, unchanged | explicit product go-decision (Runtime Companion half only) | pre-2026-07-15; reviewed 2026-07-23 |
| Review-Set-Centric Navigation | `ROADMAP.md` §deferred | Parked. **Partially advanced (2026-07-23):** the Company Redefinition roadmap's Phase 2 ships a bounded convergence (Explore nav item + Progress promotion) that deliberately stops short of full Review-Set-centricity — Library stays a separate, non-Review-Set-organized concern (see `company-redefinition-out/06-unified-roadmap.md` §Phase 2 and `03-information-architecture.md`) | direction, not a scoped item — Phase 2 needs Phase 1's behavioral read before kickoff | pre-2026-07-15; reviewed 2026-07-23 |
| **Company Redefinition (Boardready-Model Re-Architecture, 6 docs + 2 reprioritization/scoping docs)** | `docs/claude-prompt/company-redefinition-out/01–08` (`07` reprioritization + `08` Diagnostic Read methodology/queries, both added 2026-07-24), sourced from `company-redefinition-session-plan.md` (the R0–R5 prompt sequence). Origin: a boardready.ph-inspired owner proposal; a pre-Fable Claude Code challenge found it right-in-direction/wrong-in-sequence (2.4% W1→W2 retention flat across 3 releases, 0% exam-dated retention undiagnosed, pricing already ruled out as the retention lever). Owner explicitly chose to proceed without user interviews (cold outreach and in-app feedback surfacing have both gone unanswered). **v1.0.0 explicitly reserved for later** — owner decision (2026-07-23): tag it only once the redefinition's user-visible core is actually live and the product succeeds, not at the planning/strategic-commitment stage. **Full phase-by-phase detail (what ships, gates, illustrative release chunks, dependency spine) is written out in the "Company Redefinition Roadmap — Phase Detail" section below** — this row stays a status summary, that section is the reprioritization reference | **Phase 1 shipped and signed off as v0.57.0** (2026-07-23). **Resequenced 2026-07-24** (see `07-reprioritization.md`): a signup surge plus a product/UX "quizzes as reusable assets, not disposable AI output" realization reversed the 2026-07-23 decision to proceed straight to Phase 2/v0.58.0. New recommended-not-yet-ratified sequence: a **Diagnostic Read** of the surge cohort, then a new **Reusable Practice Assets & the Return Loop** initiative (per-user/cross-session quiz reuse — explicitly distinct from Phase 3's cross-user pooling), then Phase 2 split and re-gated (`v0.59.0` Progress needs Reusable Practice shipped first; `v0.58.0` Explore needs the Diagnostic Read to show a discovery problem). Phases 3–4 remain drafted, not ratified, unaffected by this resequencing | Phase 1's retention read is still owed. The Diagnostic Read and Reusable Practice initiative both need explicit owner ratification before `/kickoff` — neither has run yet. Phase 2's two chunks each carry their own new gate above. Phase 3 needs its proposed adoption-volume gate (3a) and the review-queue dependency (3b) resolved — explicitly **not** advanced by the realization above, see that section's callout. Phase 4 needs `05`'s "Owner must decide" §4 items actually decided | 2026-07-24 |
| Deeper plan nesting (3+ level hierarchy) | `ROADMAP.md` §Deeper plan nesting | Parked, nice-to-have | no gate stated | pre-2026-07-15 |
| Note Detail readiness as its own tab | `ROADMAP.md` §Note Detail readiness | Blocked | needs a mobile tab-overflow design pass | pre-2026-07-15 |
| Legacy "Future Directions" block (exam-mode work, billing, teacher items pre-v0.20) | `ROADMAP.md` §Future Directions | Stale — needs a fresh audit, largely pre-dates current architecture | none stated | never (flag for cleanup) |
| Post-v0.53.0 sequencing: CPALE hub vs. H1+H5 vs. churned-user interviews, under the "new users to retain" posture | `next-priority-new-user-focus-out/01-next-priority-new-user-focus.md` | **Run — verdict: verify before building, for both candidate builds.** Neither the CPALE hub (Wave-2 depth unconfirmed) nor H1+H5 (v0.48.0 cohort re-read overdue) should be scoped as a release yet — pull the H1+H5 cohort data first (time-boxed, overdue), run the CPALE depth-count query in parallel (cheap, not time-pressured). Recommends running the already-written churned-user interviews anyway (zero engineering cost) without treating that population as a priority. Recommends P1/P3 be handed back to the product owner now as non-engineering action items | H1+H5 build: cohort re-read must happen first. CPALE hub build: depth-count check must clear the ~25-30 note bar first | 2026-07-21 |
| Public Library SEO expansion beyond the 3 Exam Hubs (broader/general-subject Google surfacing) | `public-library-seo-expansion-out/01-public-library-seo-expansion.md` | **Run — verdict: not now.** Broad general-subject SEO investment rejected (no curation machinery, unknown/thin depth, worse competitive field, dilutes topical authority). Real finding acted on below (N1/N2) | none — verdict is final until the exam-hub deepening work (P3/P4) is done and GSC (P1) exists | 2026-07-17 |
| N1/N2 — subject-page indexation gate + depth inventory (defensive follow-up from the SEO expansion verdict above) | `public-library-seo-expansion-out/02-subject-depth-inventory.sql` | **Shipped — v0.50.4.** `SUBJECT_PAGE_INDEX_THRESHOLD = 6` (matches `DISCOVERY_SECTION_LIMIT`) gates sitemap inclusion + `robots: noindex` on subject pages below it. Set from a production depth inventory: 92 of 130 subject pages had fewer than 6 notes | none, shipped | 2026-07-17 |
| Wave 2 Exam Hub candidate: CPALE (Accountancy) — the N2 depth inventory found ~100+ notes across Accountancy-adjacent subjects (accounting, auditing, taxation, financial management) not covered by any current Exam Hub | `public-library-seo-expansion-out/02-subject-depth-inventory.sql` query 1 results | **Shipped — v0.54.0.** Depth gate confirmed against production (`next-priority-new-user-focus-out/02-h1-h5-cohort-recheck-and-cpale-depth.sql` Query 2) — crosses the ~25-30 note Wave-2 bar | none, shipped | 2026-07-21 |
| L2 — "Earned depth" pathway for non-exam subjects (Learn-guide internal linking, eventually hub-style curation for a subject that earns it) | `public-library-seo-expansion-out/01-public-library-seo-expansion.md` | Future Enhancement, double-gated, not indexed until this cleanup pass (2026-07-22). Build nothing for it now | (a) N2's depth inventory shows a specific non-exam subject with ~15-20+ notes AND (b) post-P1 GSC data shows organic impressions actually arriving on that subject's page | 2026-07-22 |
| Public-note copy idempotency guard never backfills a stale copy | investigated during v0.50.3 signoff follow-up, no session doc | **Shipped — v0.54.1.** `NoteService.copyNote()`'s existing-copy branch now backfills a copied Study Pack onto a previously pack-less copy once the source's Study Pack becomes ready, honoring `includeStudyPack` | none, shipped | 2026-07-21 |
| AI-suggested-metadata modal reported to flash and auto-bypass during a manual Generate → Quick Review flow on a self-copied public note | user report during v0.54.1 pre-signoff review, no session doc | **Not confirmed — needs live reproduction.** Static trace of `private-note-detail-page-client.tsx`'s generation-completion state machine (`awaitingGeneratedMetadataSuggestionRef`, `maybeShowGeneratedMetadataSuggestion`, `shouldAutoStartQuickReview`) did not reproduce or locate the mechanism for the reported owner flow specifically. The similar-sounding race was already fixed generically in `v0.50.3` for the non-owner "Quiz yourself" flow (`public-note-copy-and-related-links-out/01-copy-flow-and-link-consistency.md`), but that fix's trigger (`startQuickReview=1`) doesn't exist on the owner's manual-generate path — so this may be a UX artifact, not the same bug; see `RELEASES.md` v0.54.1 Known Limitations | needs the user to reproduce live and report the exact observed behavior (modal never renders vs. renders then is dismissed vs. page navigates out from under it) | 2026-07-22 |
| Production performance audit (Private Library, Public Library, Note Collection detail, Dashboard reported slow) | `production-performance-audit-out/01-production-performance-audit.md` | **Shipped — v0.51.0.** F1, F2, F4, F5, F6, F7, and F8 shipped in full (F8 — real server-side Public Library pagination — chosen directly over F3's stopgap). F9 (client caching) and F10 (denormalized counts) intentionally remain parked below | F9/F10: gated on post-v0.51.0 production evidence (slow-query logs, whether refetch pain persists once queries are bounded) — not before | 2026-07-20 |
| Dashboard Stage-1 `listNotes()` limit-wiring (F2 follow-up, deliberately deferred during F2's implementation) | `docs/codex-prompts/v0.51.0-note-list-lean-projection.md` | **Shipped — v0.51.1.** Dashboard now requests the 20 most-recently-updated owned notes; the existing overview response supplies the exact owner-note count, quiz-question existence, and most-recent resolved-ready note id so totals, empty states, first-time ordering, and Challenge routing retain their unbounded semantics | none | 2026-07-20 |
| F9/F10 — client-side caching + denormalized engagement counts (parked from the performance audit above) | `production-performance-audit-out/01-production-performance-audit.md` | Parked, intentionally not in v0.51.0 | production evidence after v0.51.0 ships: do bounded/parallelized pages still show refetch pain (F9)? does slow-query logging still flag the enrichment queries (F10)? | 2026-07-17 |
| Feedback system polish — Send Feedback modal visual redesign, optional screenshot/image attachment on bug reports, and a read-only Admin detail view for the "Recent Feedback" table (currently a static, truncated table) | `feedback-system-polish-out/01-modal-design-and-admin-detail.md` | **Folded into v0.52.0 as a mid-release scope addition.** Modal restyle + strictly-optional screenshot field (separate `feedback_image` table, not a column on `FeedbackEntity`, to avoid regressing the v0.51.0 read-path fix); Admin gets a "View" button reusing the existing Refund modal pattern. A status-update action is recommended as a separate fast-follow, not bundled in | none — see `RELEASES.md` v0.52.0 | 2026-07-20 |
| Structured quiz feedback questions (quiz-results-card-only scope; too-easy/repetitive/confusing chips) | `quiz-feedback-microsurvey-out/01-structured-quiz-questions.md` | **Superseded** — product owner corrected scope to app-wide/new-user-focused mid-session; folded as one input into the app-wide session below, not a standalone answer | none — superseded | 2026-07-21 |
| App-wide new-user early-lifecycle feedback signals (Public Library browse-without-adopt, first non-onboarding Study Pack generation, second-ever completed quiz) — explicitly a leading indicator for new-user friction, not a churn-diagnosis substitute for the unsent outbound interviews above | `app-wide-feedback-signals-out/01-app-wide-feedback-signals.md` | **Shipped — v0.52.1.** Fable's own verdict: build this small slice (2 of 3 placements zero-backend), hold anything larger — rejected a structured `quick_reason` field on volume grounds (~127 activated users), rejected the floated "is the UI hard to understand?" as too vague, explicitly does not touch or substitute for H1/H5 above | none — see `RELEASES.md` v0.52.1 | 2026-07-23 |

## Company Redefinition Roadmap — Phase Detail

Full detail from Fable's capstone synthesis, `docs/claude-prompt/company-redefinition-out/06-unified-roadmap.md` (read in full, not just its "Decisions carried forward" block). This section exists so reprioritization discussions (e.g. with product/UX) have one canonical reference instead of six separate planning docs to reconcile against — **treat this section, not conversational recall, as the source of truth for what Fable actually designed.** Update it in the same commit as any reprioritization decision so it never drifts from what's actually agreed.

**Resequenced 2026-07-24 — see `company-redefinition-out/07-reprioritization.md` for the full reasoning.** A real-time signup surge (~15 signups in one evening; hundreds of verified users now; LET the strongest acquisition channel) landed alongside a product/UX realization that Challenge Quiz's always-fresh AI questions might be better treated as a reusable, improving asset than disposable output. That combination is significant enough to reorder what comes right after Phase 1, **reversing the 2026-07-23 decision to proceed straight to Phase 2 (v0.58.0)** made earlier that same day. The reasoning (verified against the actual quiz-generation code, cross-checked by an independent advisor pass and an independent Fable session): the realization is correct but was framed as a cost problem when its real value is a **retention primitive** — and it is not the same thing as Phase 3 below (see that section's callout). The new sequence:

1. **Phase 1** — shipped, as before.
2. **Diagnostic Read** (new, inserted here) — read the surge cohort before committing another build cycle.
3. **Reusable Practice Assets & the Return Loop** (new initiative, inserted here, ahead of Phase 2) — the reframed realization, done as a retention play, not a cost play.
4. **Phase 2** — re-gated: Progress now explicitly depends on Reusable Practice Assets existing; Explore is now contingent on the diagnostic read showing a discovery problem specifically (see Phase 2 below).
5. **Phase 3, Phase 4** — unchanged, still parked at their original gates. Phase 3 in particular should **not** be accelerated just because the realization put "question pooling" top of mind — see its callout below for why it's a different thing.

**Phase order as originally designed (cost/risk-based, not arbitrary; superseded above only where noted):** Phase 1 was cheapest/most reversible and instruments its own validation read. Phase 2 was meant to be the v2 layer, building the convergence surface Phase 3 needs something to pool from. Phase 3 is highest one-time engineering cost, sequenced late so adoption volume justifies it before building. Phase 4 is a business decision with **no engineering dependency on 1–3 at all** — it is explicitly free to move earlier the moment the owner ratifies its terms; it's sequenced last purely by convention, not by cost or risk.

### Phase 1 — Practice-first activation onboarding branch
**Status: Shipped, v0.57.0 (2026-07-23).** Source: `company-redefinition-out/02-activation-onboarding.md`.
- What shipped: `BOARD_EXAM` learners with a depth-qualifying Official Review Set skip note-authoring/generation, adopt the set in one tap, land on its detail page. No qualifying set → unchanged 5-step flow.
- Gate to enter: none (first phase, produces the evidence later phases were meant to consume).
- Validation: pre/post W1→W2 retention on the *same covered course/program tracks* (not naive cross-track A/B, since create-first vs. practice-first cohorts are otherwise confounded with covered-vs-uncovered tracks). Floor ~30 completed onboardings/arm for a directional read, ~75+/arm for decision-grade. **Not pulled yet** — needs a 14-day window after the last onboarding in the intake window; see `docs/releases/v0.57.0.md` Known Limitations. The 2026-07-24 signup surge is a candidate cohort for this read — see Diagnostic Read below.

### Diagnostic Read — read the surge cohort before building again (new, 2026-07-24)
**Status: Recommended, not yet ratified. Scoped 2026-07-24** — concrete methodology and runnable queries are written: `company-redefinition-out/08-diagnostic-read-methodology.md` and `08-diagnostic-read-queries.sql`. Source: `company-redefinition-out/07-reprioritization.md`.
- **Scoping found the existing W1→W2 definition needs a fix, not just a re-run:** it anchors "activated" on `STUDY_PACK_GENERATED`, which never fires for a practice-first adopter (copies an already-generated Study Pack, no LLM call) — every practice-first-onboarded learner was invisible to the old read. `08`'s queries report a signup-anchored read (primary, path-agnostic) alongside a widened activation-anchored read (historical comparability) side by side. See `08-diagnostic-read-methodology.md` for the full reasoning.
- Three prior retention fixes (v0.44.0, v0.46.0, v0.48.0) each shipped on a different hypothesis without moving W1→W2 — that pattern is a diagnosis gap, not evidence that the next feature will be the one that works.
- The 2026-07-24 surge (LET/Facebook-driven) is the best real research asset available: read where the funnel actually breaks (do signups complete a first session? return at all? segment by exam-date proximity/prep-cycle rather than a flat weekly boolean), using instrumentation Phase 1 already emits. A handful of direct interviews with reachable new signups is in scope here too.
- Add a crude cost-per-active-user (no token accounting needed — see the Reusable Practice initiative below for why none exists today).
- **Three hypotheses to actually test here, not assume:** (a) discovery problem — the value exists but exam-dated users don't reach it before bouncing; (b) value problem — they reach it and it isn't worth a second visit; (c) lifecycle-metric mismatch — board-exam prep is episodic (cram → sit the exam → legitimately done), so weekly retention may be structurally low regardless of feature quality. Note **(c) is in tension with the existing 0% exam-dated-retention finding** (0/41, retaining *below* their own exam date) — that fact leans toward (a) or (b), so (c) should be tested, not adopted by default.
- **Why this sits ahead of Phase 2:** reorganizing navigation mid-surge would pollute the exact funnel data this read needs to stay clean.
- Gate to enter: none — cheap, reversible, unblocks the sequencing decision below it.

### Reusable Practice Assets & the Return Loop — new initiative (2026-07-24)
**Status: Recommended, not yet ratified.** Source: `company-redefinition-out/07-reprioritization.md`. **This is not Phase 3** — see the callout in that section for the precise distinction.
- **What it is:** of the 5 quiz modes, Quick Review already replays a stored quiz (zero LLM per session — already the target state). Board Exam Mode and Long Exam have **per-user** question pooling already built in `ExamQuestionPoolService`, but it ships **dormant** — `examPoolPrewarmEnabled=false` by default, so no pool row is ever created and both regenerate fresh every session today. Challenge Quiz regenerates fresh on start *and on every "give me more" click*, with no reuse of any kind. Adaptive Practice regenerates fresh by design (personalized to one learner's own misses) and should stay that way — it is not in scope here.
- **A previously untracked finding:** Challenge Quiz's "give me more" path calls the LLM every click and is **completely unmetered** — no quota decrement at all, unlike every other generation path in the product. Reframe this as an **engagement signal to harvest** (persist those questions as a durable, revisitable set) rather than a leak to simply cap.
- **What ships:** turn on the existing per-user pool for Board/Long Exam; extend the same per-user pattern to Challenge Quiz (a mode it currently isn't wired to at all); persist generated questions as an owned, revisitable set instead of discarding them each session; add a "redo what you missed" surface reusing the existing weak-concept/`ConceptHealth` machinery (v0.56.0's explanation links, readiness scoring) rather than inventing new mastery signals.
- **Why it's a retention play, not a cost play:** at current scale, aggregate LLM cost is small (quota is essentially never hit) and there is no token/dollar metering anywhere to optimize against — so "AI cost" is the weakest argument for this. The strong argument is pedagogical: a learner who never gets a second crack at the specific question they missed has no spaced-repetition mechanic to retain against, and the product has already locked "curation, never generation" as an identity while 4 of 5 quiz modes currently regenerate-every-session — this closes that gap.
- **Why it's sequenced here:** most of the machinery already exists (stored Quick Review quiz, dormant per-user pool, `ConceptHealth`, weak-concept links) — it is cheap relative to Phase 3. It also **unblocks Phase 2's Progress promotion** below, which has nothing stable to show progress against if quizzes regenerate every time.
- Gate to enter: Phase 1 shipped (done). Does not depend on the Diagnostic Read's outcome — it helps under all three hypotheses above.

### Phase 2 — IA / Explore convergence
**Status: Re-gated 2026-07-24 — the 2026-07-23 "proceed to v0.58.0 next" decision is reversed; see the resequencing note at the top of this section.** Source: `company-redefinition-out/03-information-architecture.md`.
- What ships: authenticated nav becomes `Dashboard / My Reviews / Library / Explore / Progress`. Explore is a new nav item (not a new canonical URL) compositing the existing Official Review Set catalog (`/collections/published`) and `/public/library` behind a segmented control, plus a pointer to the Exam Hub index. Progress is promoted from sub-page to first-class nav item (drops its `← Dashboard` back link). `/exam/[slug]` gains one additive check: resolve the hub's `courseProgram`(s) against published Official Review Sets — a match adds a preview+adopt path. Library stays untouched and structurally separate from Collections.
- Reuses almost nothing from fable-out (built on already-shipped mainline machinery instead: v0.41.1 Primary-card hierarchy, `PlanPicker` + `?collectionId=`, `getCollectionLabels`, the copy funnel's `redirectTo` param).
- One flagged-not-resolved recommendation: adopting an Official Review Set with no existing Primary sets it as Primary — does not resolve the still-open Primary-Review-Set-vs-Study/Exam-Focus question.
- **Two release-sized chunks, by design (different risk profiles), now separately gated:**
  - `v0.58.0 — Explore Convergence`: new Explore nav item, segmented Review-Sets/Notes control, Exam Hub additive official-set check. **Gate (new): proceed only if the Diagnostic Read above indicates a discovery problem** (exam-dated users bouncing before reaching value) — otherwise this is a discovery bet the read didn't support.
  - `v0.59.0 — Dashboard & Progress Reorg`: Dashboard hero → Primary Review Set condensed card, Progress promotion, the adopt-sets-Primary default. **Gate (new): proceed only once Reusable Practice Assets & the Return Loop has shipped** — there is no stable progress to promote to a top-level nav item until quiz content stops regenerating every session. Also still touches default states on pages users already rely on daily — do not fold into the same release as the chunk above.
- Producing more Official Review Sets remains bottlenecked on the separate, still-unscoped Curator pipeline (Smart Review Planning) — Phase 2 does not solve this; Phase 3's authoring slice hits the same gap.

### Phase 3 — Cross-user question pool + bounded reusable-object model
**Status: Drafted, not ratified. Stays parked at its original gate — do not accelerate.** Source: `company-redefinition-out/04-reusable-assets-and-reviewer.md`.
- **Callout: this is not the same thing as "Reusable Practice Assets & the Return Loop" above, even though both involve `ExamQuestionPool`.** Phase 3 is **cross-user** sharing — many different learners who adopt the same Official content drawing from *one* pool. The new initiative above is **per-user, cross-session** reuse — one learner's own repeat practice reusing their own previously-generated questions instead of regenerating. Phase 3 needs the resolver + child table below; the new initiative needs neither. Don't let "we're already doing pooling" become an argument to pull Phase 3 forward — its own gate (adoption volume) is unrelated to and unmet regardless of the new initiative shipping.
- Foundation slice (3a): a new `resolvePoolKey(studyPackId)` step inside `ExamQuestionPoolService` only resolves the pool to the Official source's `studyPackId` when the caller's note is Official or a one-hop copy, falling through to per-owner keying otherwise (derived on read, never persisted). Served-question tracking moves to a new per-user child table (`exam_question_pool_progress`) so adopters don't bleed into each other's history; `learnerLevel`-triggered auto-refresh drops for Official pools. Private per-owner pools are fully unchanged.
- Authoring slice (3b): curator-side pool expansion, batches land pending-review before READY, reusing the review-queue shape from the parked Smart Review Planning docs.
- Bounded object model: of 8 proposed fields, 5 need zero new work; Flashcards stays derived; Difficulty is cut (already covered by `DIFFICULTY_SELECTION`). **The 3a resolver + child table is the only genuinely new build in the whole model.**
- Reviewer decision: label-only, no new entity — `getCollectionLabels("BOARD_EXAM")` already returns "Review Set"; "Reviewer" ships as a relabel.
- **Real dependency, not smoothed over:** 3b's review-queue mechanism does not exist in the codebase today. It either waits for the Smart Review Planning Curator pipeline to ship, or needs its own small standalone queue as net-new Phase-3 scope. 3a has no such dependency.
- Proposed gate (not stated by the owner, proposed by Fable): don't kick off 3a until adoption telemetry (already emitted by Phase 1/2's adopt/copy paths) shows a shared Official Review Set with enough concurrent adopters that duplicated per-owner pool generation is a measurable cost, not hypothetical. **This gate is unaffected by the 2026-07-24 resequencing** — building cross-user scale before retention exists optimizes for scale the product doesn't have yet.
- **Illustrative release chunks:** `v0.60.0 — Shared Official Pool Foundation` (3a only, no review-queue dependency, ships standalone); a later TBD release for 3b once the review-queue question is resolved; the Reviewer relabel can ship independently in either chunk.

### Phase 4 — Packaging / terminology delta
**Status: Drafted, not ratified — needs an explicit owner decision before scoping. Unaffected by the 2026-07-24 resequencing.** Source: `company-redefinition-out/05-packaging-and-terminology.md`.
- What ships once ratified: Creator (bring-your-own-notes) and Curated Learning (adopt Official Review Sets) stay **one product** on the existing FREE/PLUS/PRO ladder — a messaging distinction, not a pricing fork. No new SKU. Terminology delta, top item: rename "Generate Note"/"Generate a note" → "Create a Note"/"Draft a Note" (freeform AI-authored prose with no source note shouldn't borrow "Generate"'s differentiator language); "Generate Study Pack"/"Generate Quiz"/"Regenerate Quiz" keep the generation-flavored verb since that names the real differentiator.
- Directly reuses `fable-out/05`'s already-recommended tier placement (FREE=adopt, PLUS=conversational assembly, PRO=adaptive planning) and its recommendation that adopting Official Review Sets stays free and unmetered at every tier.
- **Owner-must-decide gate:** source doc `05` carries a formally-headed "§4. Owner must decide" section — the only one of the six Fable docs with one. Phase 4 cannot be scoped for `/kickoff` until those items are actually decided, not just acknowledged.
- **No engineering dependency on Phases 1–3 — the one phase free to move earlier if ratified sooner.** The terminology-rename slice specifically is small enough (copy/label change, no new infra) to be a direct Claude-Code frontend change per this repo's task-routing table, and could ride along inside any other release's polish bucket once ratified, rather than needing its own release.
- **Illustrative release chunk (only if tracked standalone):** `vX.Y.Z — Terminology & Packaging Cleanup`.

### Dependency spine (resequenced 2026-07-24)
Phase 1 (shipped) → Diagnostic Read (new, reads the surge cohort, no gate) → Reusable Practice Assets & the Return Loop (new, gated only on Phase 1 having shipped) → Phase 2, split: `v0.59.0` Progress gated on Reusable Practice having shipped; `v0.58.0` Explore gated on the Diagnostic Read showing a discovery problem specifically → Phase 3a gated on adoption-concurrency threshold (unaffected by the above, still unmet); Phase 3b additionally waits on the review-queue dependency resolving. **Phase 4 has no dependency on any of the above and floats freely on owner ratification timing** — this is the lever to pull if reprioritizing without new engineering risk.

### Backlog Index rows this roadmap supersedes or folds into
- **Smart Review Planning (Internal Curator, 7 docs)** — partially folded, not closed. Phase 3 carves out and re-gates only the bounded-object-model/cross-user-pool/Reviewer-relabel slice; the curriculum-authoring pipeline (templates, matcher, gap-fill queue, Plan-My-Review wizard) stays exactly as Parked, same original gate.
- **AI-generated Review Sets / Runtime Companion** — splits. "AI-generated Review Sets" is effectively closed/ruled out by the locked curation-never-generation architecture this whole effort re-affirms. "Runtime Companion / Ask Companion / Personalization" is untouched and stays Parked separately.
- **Review-Set-Centric Navigation** — partially advanced, not resolved. Phase 2 ships a bounded convergence (Explore + Progress promotion) that deliberately stops short of full Review-Set-centricity; Library stays a separate, non-Review-Set-organized concern.
- **Product-language row** — no standalone row exists; Phase 4 is where the terminology-delta content now lives (extends `fable-out/06`'s rename map, and reverses its blanket "keep Generate Note, not touched" stance for onboarding specifically, with a new argument for why).

### Nothing here is authorized for implementation until ratified
Each phase and each new item above needs the owner's explicit ratification before its own `/kickoff` — this applies equally to all of them, not just the ones with a stated behavioral/adoption gate. Phase 2's original behavioral-read gate was explicitly overridden by the owner on 2026-07-23, and that same "proceed to Phase 2 next" decision was itself reversed on 2026-07-24 in light of the signup surge and the reusable-assets realization — both are ratification decisions in their own right, not bypasses of the ratification requirement itself.

## v0.57.0 - Practice-First Activation Onboarding (Released, base branch `releases/v0.57.0`)

Origin: Phase 1 of the Company Redefinition roadmap (`docs/claude-prompt/company-redefinition-out/06-unified-roadmap.md`) — the cheapest, most reversible, dependency-free phase of the boardready-model re-architecture, ratified by the owner 2026-07-23. Fully designed in `docs/claude-prompt/company-redefinition-out/02-activation-onboarding.md`. Explicitly picked to enter first because it produces the behavioral read (pre/post W1→W2 retention on the same covered course/program tracks) that was intended to gate Phase 2 — **the owner chose to kick off Phase 2 (v0.58.0, Explore Convergence) before that read comes back (decision: 2026-07-23); see the Backlog Index row above.** The read itself has not been pulled yet (needs a ~14-day window on both sides of the ship date) and remains an open follow-up regardless of Phase 2 proceeding.

### Planned Scope

See `RELEASES.md` v0.57.0 for scope and anti-drift rules.

## v0.56.0 - Weak-Concept Explanation Links (Released, base branch `releases/v0.56.0`)

Origin: Fable's top pick from the 2026-07-22 study-effectiveness session (`docs/claude-prompt/study-effectiveness-out/01-study-effectiveness-ui-pricing.md` §1 item 1) — explicitly scoped to exclude retention-trigger mechanics and Smart Review Planning. Picked as the cheapest, most direct answer to "does the product help you learn right now, in this session" — no dependency on the in-flight retention/acquisition experiments.

### Planned Scope

See `RELEASES.md` v0.56.0 for scope and anti-drift rules.

## v0.55.0 - Result-Screen Companion Bridge (Released, base branch `releases/v0.55.0`)

Origin: the only genuinely unblocked candidate from the App Shape planning session (`docs/claude-prompt/app-shape-out/01-app-shape-features.md` item 4) — its premise problem was resolved 2026-07-22 by reusing the v0.46.0 Weekly Pacing Echo's `primaryCollectionId` pattern instead of building a note→collection reverse lookup. Every other App Shape Core/Polish candidate remains gated on the retention constraint clearing or its own unresolved scoping question — this release does not touch those.

### Planned Scope

See `RELEASES.md` v0.55.0 for the Companion Bridge scope and anti-drift rules.

## v0.54.1 - Public Note Copy Correctness Fixes (Released, base branch `releases/v0.54.1`)

Origin: a self-inflicted-by-timing edge case surfaced during v0.50.3 signoff follow-up (no session doc) — `NoteService.copyNote()`'s existing-copy branch returns a prior copy as-is even if it predates the source's Study Pack becoming ready, so a user who copies before the source pack exists and retries later keeps getting the stale pack-less draft forever. Isolated, root-caused, Claude-Code-direct-sized fix — no Codex prompt needed.

### Planned Scope

See `RELEASES.md` v0.54.1 for the fix scope and anti-drift rules.

## v0.54.0 - CPALE Exam Hub (Wave 2) (Released, base branch `releases/v0.54.0`)

Origin: `next-priority-new-user-focus-out/01-next-priority-new-user-focus.md` (run 2026-07-21) confirmed the CPALE hub fits the "new users to retain" acquisition posture and recommended it as the smaller, lower-stakes build once its depth gate cleared (reuses an already-shipped 3x pattern, unlike H1+H5 which remains gated on the overdue v0.48.0 cohort re-read — see the Backlog Index row below). The depth-count gate (Query 2 in `next-priority-new-user-focus-out/02-h1-h5-cohort-recheck-and-cpale-depth.sql`) has since been confirmed against production, clearing the ~25-30-note Wave 2 bar.

### Planned Scope

See `RELEASES.md` v0.54.0 for the CPALE hub scope and anti-drift rules.

## v0.53.0 - SEO Discoverability: Exam Hub Depth & Organic Attribution (Released, base branch `releases/v0.53.0`)

Origin: the organic-search strategy session (`docs/claude-prompt/seo-strategy-out/01-seo-strategy.md`, run 2026-07-17 via Fable) diagnosed why "free PNLE notes"-style searches don't find NoteLib and ranked fixable gaps. After the product priority explicitly shifted to acquiring and retaining *new* users rather than re-engaging past churned ones, this picks up the code-shippable, gate-free remainder of that plan (P4/P5/P6) — pure acquisition work, no experiment-cohort or interview dependency.

### Planned Scope

All three scoped P4/P5/P6 items are shipped; see `RELEASES.md` v0.53.0 for the exam-hub depth, `ItemList`, and organic-landing attribution decisions and anti-drift rules.

## v0.52.1 - Early-Lifecycle Feedback Signals (Released, base branch `releases/v0.52.1`)

Origin: a follow-on to v0.52.0's proactive feedback prompts. The product owner initially floated this as quiz-result-specific copy, then corrected scope twice mid-session: first to app-wide, then explicitly to target *new* users specifically ("we're not chasing our previous users anymore, we're now chasing new users to retain") rather than winning back already-churned users — a question the existing `retention-diagnosis-session-plan.md` interview track owns instead, and which in-app prompts structurally cannot answer regardless. A Fable design session (`docs/claude-prompt/app-wide-feedback-signals-out/01-app-wide-feedback-signals.md`) scoped the three placements shipped here, explicitly rejecting a fuller build as the same "more listening infrastructure on an already-tiny population" anti-pattern the retention diagnosis flagged, in favor of a small, mostly-zero-backend slice.

### Planned Scope

See `RELEASES.md` v0.52.1 for the three placements (Public Library browse-without-adopt, first non-onboarding Study Pack generation, second-ever completed quiz) and their anti-drift rules.

## v0.52.0 - Proactive In-App Feedback Prompts (Released, base branch `releases/v0.52.0`)

Origin: a direct pivot away from the retention-diagnosis interview plan (`docs/claude-prompt/retention-diagnosis-session-plan.md`), after the v0.48.0 cohort-read gate came back with too weak a signal to act on and cold email outreach to churned users was judged unlikely to work (precedent: failed-payment recovery emails got zero response). Rather than chasing a hard-to-reach churned cohort, this surfaces the app's existing, under-used `SendFeedbackWidget`/`QuizFeedbackPanel` pipeline proactively to *current* users at two moments, so future cohorts' friction is caught in real time instead of inferred later. Retention H1 + H5 (`docs/product/ROADMAP.md` Backlog Index) stays exactly where it was — this doesn't resolve or replace that gate, it's a separate, forward-looking track.

**Scope:**
- First-quiz-ever feedback prompt (backend + frontend).
- Return-after-inactivity feedback prompt (backend + frontend).
- Mid-release addition (2026-07-20): feedback modal restyle + Admin detail view (frontend only), plus an optional screenshot attachment on feedback (backend + frontend) — see `docs/claude-prompt/feedback-system-polish-session-plan.md`.

Anti-drift: reuses the existing `POST /feedback` pipeline as-is. The one exception is the mid-release addition's new `feedback_image` table, explicitly justified by a read-path constraint (see `RELEASES.md`) — no other new storage, no rating/NPS data model. No changes to the existing 3-day inactivity email or its cooldown logic. No outreach to already-churned users. Full scope in `RELEASES.md`.

## v0.51.1 - Dashboard Stage-1 Limit Wiring (Released, base branch `releases/v0.51.1`)

Origin: F2's follow-up item from `v0.51.0 - Read-Path Performance Pass II`, deliberately deferred during F2's own implementation because Dashboard Stage-1's `totalNotes`, `hasCompletedSession`, and Challenge-CTA resolution all depended on the full unbounded note array at the time. Now that F2's bounded `limit` param exists and is unused, this closes the gap.

**Scope (shipped):**
- Dashboard Stage-1 bounded fetch (backend + frontend).

Anti-drift: read-path performance only, following F1/F2/F6's precedent from v0.51.0 — no change to what Dashboard displays or which note Continue Studying / Challenge Quiz routes to. No new caching infrastructure. No changes to quiz-session data model, mastery/readiness computation, or profile-type branching logic. Full scope in `RELEASES.md`.

## v0.51.0 - Read-Path Performance Pass II (Released, base branch `releases/v0.51.0`)

Origin: user-reported production slowness on Private Library, Public Library, Note Collection detail, and the Dashboard. Diagnosed via 4 parallel direct-codebase investigations (one per page) plus a Fable planning session (`docs/claude-prompt/production-performance-audit-out/01-production-performance-audit.md`), grounded against the prior `v0.38.0 - Read-Path Optimization Pass` precedent. Scoped in full via explicit user decision: F9/F10 left parked rather than included; F8 (real server-side Public Library pagination) built directly rather than F3's cap-and-load-more stopgap.

**Scope (shipped):**
- F1 — Dashboard-overview lean projections + bounding (Codex).
- F2 — `NoteService.listMine` lean projection + optional `limit` param (Codex).
- F4 — Private Library poller narrowing (Claude Code direct).
- F5 — Collection detail waterfall flattening + 2 verification tasks (Codex, likely crosses the ~50 LOC threshold).
- F6 — Dashboard Stage 2 batched fan-out endpoint (Codex).
- F7 — Real backend pagination for Private Library (Codex).
- F8 — Server-side filtering + full pagination UX for Public Library (Codex).

Anti-drift: read-path performance only; byte-identical responses for F1/F2/F4/F5/F6; F7/F8 are explicit UX/architecture changes, not disguised backend swaps; no new caching infrastructure; no quiz-session/mastery/readiness/profile-branching changes. Full scope in `RELEASES.md`.

## v0.50.4 - Exam Hub Discovery Polish (Released, base branch `releases/v0.50.4`)

Origin: direct user production testing of the "Quiz yourself"/"Add to Library" copy-as-is fix (confirmed working correctly on a fresh account — the apparent regression was stale test-account data hitting the copy idempotency guard, not a code bug) surfaced the duplicate Exam Hub link as a separate, real finding; bundled with P2 (vocabulary pass) from the SEO strategy Fable session (`docs/claude-prompt/seo-strategy-out/01-seo-strategy.md`) run the same day. Mid-release scope addition: after direct production testing showed NoteLib not surfacing for exam-adjacent searches, the vocabulary pass was extended to every Public Library subject page (not just Exam Hub), paired with a defensive subject-page indexation gate (`SUBJECT_PAGE_INDEX_THRESHOLD = 6`) informed by a production content-depth inventory.

**Scope (shipped):**
- Collapsed the duplicate "Browse {Hub} hub →" link on public note detail (Claude Code direct).
- "Free reviewer" vocabulary pass on Exam Hub pages — SEO candidate P2 (Claude Code direct).
- Extended the same vocabulary pass to all Public Library subject pages (Claude Code direct).
- Subject-page indexation gate — sitemap + `robots: noindex` below the depth threshold, so thin subject pages stop being served to search engines while staying fully reachable in-app (Claude Code direct).

Anti-drift: no mass-generated AI content; no pricing/paywall/quota changes; no Wave 2 Exam Hub expansion; the indexation gate changes search-engine visibility only, never page reachability or access. Full scope in `RELEASES.md`.

## v0.50.3 - Public Note Copy Flow & Related-Notes Consistency (Released, base branch `releases/v0.50.3`)

Origin: direct user testing of the public note detail page surfaced two findings, bundled into one Fable session (`docs/claude-prompt/public-note-copy-and-related-links-out/01-copy-flow-and-link-consistency.md`) since both live on the same page. (1) "Quiz yourself on this note" sometimes forces a full Study Pack regeneration whose AI-suggestion modal gets cut off by a real race condition — investigation confirmed the bug and found the backend already supports a free, synchronous copy-as-is path that should be the only behavior for this CTA; Fable confirmed copy-as-is over regeneration as the right default and recommended skipping the modal on this flow entirely rather than just fixing its timing. (2) The two related-notes sections' "see all" links wrap on mobile for longer subject/course names and use inconsistent grid column counts on desktop, both flagged directly from user screenshots.

**Scope:**
- Part A (Codex): copy-as-is only for the "Quiz yourself" CTA, skip the AI-suggestion modal on this flow, gate the CTA server-side on the source having a ready Study Pack, fix the navigation-vs-modal race as an ordering guarantee.
- Part B (Claude Code direct): shorten both related-notes "see all" links to `See all →` (with `aria-label`s), collapse the subject section's grid to match the course/program section's 2 columns.

Anti-drift: no new personalization/regeneration capability (the existing copied-pack regenerate hint already covers it); `Browse {Hub} hub →` stays untouched. Full scope in `RELEASES.md`.

## v0.50.2 - Note Card Content Consistency (Released, base branch `releases/v0.50.2`)

Origin: direct user report that the public note detail page's two related-notes sections ("More {Course/Program} notes" vs. "More in {Subject}") render inconsistently — traced to shipping drift (built six weeks apart, never reconciled) rather than a deliberate design choice. Broadened into a Fable card-content-strategy session (`docs/claude-prompt/note-preview-vs-summary-out/01-card-content-strategy.md`) after the user questioned whether the standing "prioritize note preview over summary" rule still holds now that a growing share of notes are AI-authored via `Generate Note` rather than user-written.

**Scope (Phase 1 only — Phase 2 origin-tracking stays parked, see Backlog Index):**
- Single-excerpt cascade on all four note-card surfaces (public note detail's two related-notes sections, Public Library grid, private Library grid): note preview if non-empty and long enough, else a labeled "Summary" fallback, else no excerpt block — never both stacked.
- Migrate the bespoke "More {Course/Program} notes" card to the shared `SharedNoteCard` component, matching "More in {Subject}" exactly (sections may still differ in query/count, never in card template).
- Rewrite the documented card-content rule in `docs/features/public-library.md` (and any other feature doc citing it) from a human-authorship rationale to a source-object rationale ("the note is the source, the summary is a fallback preview of a derivative") — the old rationale is factually broken now that note origin can't be verified, but the underlying priority (note preview first) still holds for an unrelated reason.
- Frontend-only, no schema/backend change. Fits the Claude-Code-direct lane (isolated, well-scoped, ≤ a few files) rather than needing a Codex prompt — confirm at kickoff.

Anti-drift: no origin-aware rendering (Phase 2 is explicitly rejected for card display even once instrumentation exists — see the Fable output's "Explicit rejections" §3–4); no change to Featured-ranking eligibility criteria; no change to note visibility/ownership/copy-adopt rules; no change to `Generate Note` itself.

Patch version, not minor — this is a consistency/polish fix to something that already exists, not new planned feature work, matching the v0.45.1/v0.45.2/v0.50.1 patch precedent.

## v0.50.1 - Mobile UI Polish (Released, base branch `releases/v0.50.1`)

Origin: direct user report of five UI polish items after using v0.50.0 in practice — three are fast-follow refinements to the tab bar itself (icon-only on focus pages, filter-retaining links, a show/hide preference), two are small, unrelated pre-existing issues (Review Set description truncation, Progress page milestone empty state). Routed entirely through Codex prompts this release (user's token-budget choice, mirroring v0.47.0's per-item Codex-prompt pattern) rather than the direct-Claude-Code lane the routing table would otherwise put most of these in. Full scope in `RELEASES.md`.

## v0.50.0 - Mobile Bottom Tab Bar (Released, base branch `releases/v0.50.0`)

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

Not a version — no release branch, no implementation scope, and explicitly not intended to become one without a further, deliberate scoping pass. Origin: production W1→W2 retention read at **2.4%** (3 of 127 eligible activated users, 2026-07-15), against an earlier 5.6% read (v0.32.2) that first flagged retention as "the real constraint" — the level has not meaningfully improved despite two intervening releases touching retention surfaces (v0.44.0, v0.46.0). Two independent Fable consultations (growth/retention lens and consumer-psychology/behavioral-economics lens, run with no shared context) converged on the same core diagnosis: the retention infrastructure that exists (due-concepts digest, weak-concept nudge, weekly summary) ships default-OFF and is gated behind the exact engagement it's meant to create; the first session ends in psychological completion rather than an open loop; exam date is the strongest retention primitive in the product but is optional and Board-exam-only; and single-serving-utility risk is real for anchor-less casual users but not for exam-dated reviewees, whose job is inherently longitudinal. Pricing was independently ruled out by both sessions as a current bottleneck (Free quota is essentially never hit). Full diagnosis, ranked root causes, ruled-out causes, recommended data pulls (week-1 depth, an exam-date natural experiment, acquisition-source segmentation — all queries against existing data, no code), and candidate directions (all explicitly contingent on those data pulls, none release-ready) are in `docs/claude-prompt/retention-diagnosis-session-plan.md`, with the two full raw sessions in `docs/claude-prompt/retention-diagnosis-out/`. **Data pulls run 2026-07-15**: week-1 depth came back at 3.88% (near the same magnitude as W1→W2 itself, ruling out the "session 1 is fine, only week 2's trigger is broken" reading — the two root causes are co-dominant, not one ahead of the other); the exam-date natural experiment found exam-dated users retained no better under the status quo (0/35 vs 3/94 for non-exam-dated, small-sample), which strengthens the *active* commitment-device candidate direction over the passive one; acquisition-source segmentation remains genuinely unanswerable (no UTM/referral tracking exists in the schema). The recommended v0.48.0 scope (trigger fix + open-loop session ending, both cheap and independently testable) was confirmed and shipped — see `v0.48.0` above. **Post-shipment strategy checkpoint (2026-07-15):** two Fable consultations, run after v0.48.0 merged, prioritized what comes next — full detail in the session-plan file's "Strategy checkpoint" section. Headline: talk to actual retained/churned users now (cheap, non-confounding, more informative at this scale than further cohort analysis); pull UTM/referral tracking and device mix in the same pass; pre-committed decision rule for the v0.48.0 read (any positive-or-ambiguous signal → ship H1 + H5 together as one release, not sequentially, since a 2-week cohort is too small to cleanly resolve); Unified Next-Step Resolver reframed as H5's infrastructure, not standalone app-shape work; the exam-date-users-retained-worse finding may indicate a value problem, not just a trigger problem — unresolved until the interviews happen. **Interim-window analytics pulls run 2026-07-22** (`retention-diagnosis-out/04-interim-window-queries.sql`, run manually against production by the product owner — not by Claude, which lacks the prod `DB_USER`): device mix by distinct user is ~75% mobile / 25% desktop (209 mobile vs. 69 desktop, 1545 vs. 1700 tokens) — reconfirms, doesn't change, the device-mix read already used to un-park the v0.50.0 mobile tab bar. PDF export volume is unchanged at exactly 1 export, ever, by 1 user (2026-07-11) — further reinforces the existing "Parked — do not build" call on PDF export surfacing (see below), no new signal. Official Review Set coverage: exactly 4 course programs have a published (visibility=PUBLIC, top-level) Review Set at all — Accountancy (74 notes), Architecture (52 notes), Education (43 notes), Nursing (63 notes) — a 1:1 match with the four shipped Exam Hub programs (CPALE/ALE/LET/PNLE respectively). Each has real, non-trivial depth (43–74 notes), which is a mild prior *against* the strongest form of the content-gap-churn hypothesis for those four exams specifically — but zero official coverage exists for any board exam outside those four, which the interviews should probe if churned exam-dated users skew toward other boards. The same pass also re-ran the original exam-date natural experiment (`retention-diagnosis-out/03-data-pull-queries.sql` Query 2) at a larger, later sample: 0/41 (0%) exam-dated vs. 3/106 (2.83%) non-exam-dated — consistent with the original 2026-07-15 read (0/35 vs. 3/94), same direction, larger n. This is a general-population reconfirmation of the underlying hypothesis, not the H1+H5 kickoff gate itself — that gate is still the post-v0.48.0 cohort read, unreachable until 2026-07-29 (see Backlog Index above). None of these four results change any status or gate on their own; they narrow what the interviews still need to answer (mainly: value vs. discovery for exam-dated users, and coverage outside the four existing hubs) rather than resolving anything standalone.

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
