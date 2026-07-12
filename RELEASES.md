# RELEASES.md - NoteLib

## v0.44.0 - Conversion & Retention Polish

**Status: In Progress**

Theme: ship the highest-confidence findings from a 7-session conversion/retention UX audit (`docs/claude-prompt/conversion-audit-out/`, consolidated in `docs/claude-prompt/conversion-audit-prioritized-backlog.md`) — closing a verified backend gap in return-visit progress tracking, plus the audit's Tier 1 (high-impact, low-effort) UI/UX items across landing, pricing, public notes, onboarding, and Public Library.

**Scope broadened mid-release (2026-07-12):** a follow-up diagnosis of the actual dominant acquisition channel (public-note links shared into Facebook study groups, not the landing page — `docs/claude-prompt/facebook-entry-funnel-out/01-facebook-entry-funnel.md`) found and confirmed, via direct code trace, that every copy-on-signup user is redirected to `/onboarding` before ever seeing their copied note or its auto-launched Quick Review — a universal defect in the core conversion mechanic, not a channel-specific edge case. Folded into this release rather than deferred, per the same precedent as v0.43.1's pre-signoff trust-bug fix.

Anti-drift: no new quota/gating logic for any quiz mode or plan tier; the separate `adaptivePracticeProOnly` pricing-copy-vs-runtime-gate divergence (flagged by the same audit) is tracked separately and not addressed in this release; no reordering of Companion sections or other locked v0.41–v0.43 Coach/Companion decisions; "sell outcomes, not AI" copy rule applies to every rewritten string in this release; taxonomy fields stay combobox-only; the locked 5-step `/onboarding` flow (v0.39.1) is not restructured for any existing cohort by the mid-release fix below — it adds a parallel path for one specific cohort only.

### Shipped

- **Copy-on-signup onboarding-intercept fix (mid-release scope addition, frontend).** Fixed the confirmed universal defect where a newly verified public-note copier was redirected to `/onboarding` before their copied note or auto-launched Quick Review could render. Successful copy-on-signup now carries a distinct local completion marker that preserves the copied-note landing, then collects Profile Type, Learner Level, Course / Program, and optional Board Exam date through a dismissible Dashboard card; normal onboarding and the legacy profile-type re-prompt remain unchanged, and unavailable marker storage falls back to the existing redirect.
- **Pricing trust-copy and quota-surface analytics (frontend).** Added the shared no-auto-charge reassurance directly below paid pricing-card, paywall, and Settings CTAs while retaining the broader footer trust context. Near-limit quota banners and the shared Study Pack-limit modal now emit one-shot, source-tagged view events plus source-tagged upgrade clicks, including distinct Study Pack, note-generation, and OCR entry surfaces; existing Pricing, Settings, and paywall tracking sources remain unchanged.
- **Landing hero, audience links, and Exam Hub conversion path (frontend).** Aligned the hero and canonical metadata with NoteLib's notes-library-to-quizzes promise; clarified the nearby Board Exam Mode callout as a timed full-exam simulation without misrepresenting the Study Pack screenshot. Target User panels now link to their matching Learn categories (with Board Exam also linking to Exam Hubs), and zero-note Exam Hubs preserve their established signup intent path beside the Public Library fallback.
- **ConceptHealth tracking for Quick Review (backend).** Completed Quick Review sessions now derive fully-correct and missed concepts from the base Study Pack quiz plus persisted selections, then write the same per-user, per-Study-Pack `concept_health` records used by Challenge Quiz and Adaptive Practice. Completion remains state-guarded and transactional, so a duplicate completion does not write twice and a ConceptHealth failure rolls back the completed session.
- **Public note and onboarding conversion hooks (frontend).** Quick Check completion now uses an outcome-framed prompt that reuses the established copy-intent flow without creating anonymous progress; ready public notes explain that their visible study tools came from the source note. Onboarding Step 5 now names the learner's topic when available, sets a return expectation, makes `Open your Study Pack` the clear primary action, and keeps Dashboard as a quiet secondary path.
- **Public Library Recommended ranking and Official-plan bridge (frontend).** Filter mode now defaults to the existing decay-adjusted discovery score when no sort is specified, while explicit Newest, copies, views, and title sorts retain canonical URLs. A Course / Program filter now silently checks the existing public-plan list and links to Official Study Plans only when a matching plan exists.

---

## v0.43.1 - Companion Mentor Tips

**Status: Released**

Theme: let the *authored* Companion content participate in the Coach experience the way live signals already do — small, individually-surfaceable, action-linked "Mentor Tips" instead of an article to read start to finish. A real content-model change (backend + authoring-UI), unlike v0.43.0's frontend-only fast-follows.

### Planned Scope

- **Content model (backend).** New Mentor Tip shape with its own identity, optional linked action, and optional deterministic surfacing condition — `CompanionContent`'s existing five long-form markdown fields cannot be individually surfaced without a new entity/DTO shape.
- **Authoring (backend + frontend).** Extend the authoring modal, v0.42.0's per-section AI-assist, and the structure-staleness snapshot to cover Mentor Tips. Mandatory human review before publish, same as every other Companion write path.
- **Action-linking (backend + frontend).** Curator-tagged, not inferred — the curator sets the linked action (e.g. "Review due concepts") when authoring the tip. No per-view LLM call.
- **Deterministic surfacing (backend + frontend).** Date/progress-rule triggers only (e.g. "within 2 weeks of exam date," "after N subjects completed") — no learning-pattern/LLM-driven selection.
- **"View Full Guide" stays reachable regardless of trigger state (frontend).** A learner must never permanently miss a tip because its surfacing condition never fired for them.

Anti-drift: "Curation, never generation" unchanged — learner never receives an auto-generated tip; publishing stays non-autonomous; surfacing logic must stay deterministic/rule-based, not adaptive/LLM-driven (that tier is reserved for the gated PRO Personalization candidate in `docs/product/ROADMAP.md`); no change to the existing five Companion sections' content or order.

**Known low-volume caveat, checked at kickoff, not assumed:** dev DB shows only 1 PUBLIC/Official top-level Review Set currently carrying an authored Companion (2 companions total across 7 top-level collections; the other sits on a PRIVATE collection). `docs/product/ROADMAP.md`'s v0.43.1 candidate section flagged this explicitly as a go/no-go check. Decision: proceed — this is dev/local data that may not reflect prod authored volume, and the content-model/authoring-UI work has standalone value even before curators have built up tip inventory.

### Shipped

- **Mentor Tips content model.** `CompanionContent` now carries `mentorTips` inside the existing `note_collections.companion` JSONB payload. No new table, migration, endpoint, persisted learner state, progress signal, or Companion eligibility rule was added.
- **Curator authoring and validation.** ADMIN Companion authoring can add, edit, remove, and save Mentor Tips with a fixed linked action (`None`, `Continue Studying`, `Review Due Concepts`, terminal exam/builder action) plus an optional deterministic surfacing condition. Invalid negative thresholds are rejected through the existing inline modal error path.
- **AI draft extension, not auto-publish.** Per-section Companion generation now supports `MENTOR_TIPS`; the LLM returns draft tip title/body only, with `linkedAction=NONE` and `surfacingCondition=null`. Curators still review, configure, and save through the existing full-replacement Companion write path.
- **Deterministic Coach surfacing.** The collection detail page selects at most one eligible Mentor Tip in authored order and renders it near `TodaysFocusCard`, resolving its linked action against the already-computed primary action, due-concept review link, or terminal action. No per-view LLM call or extra fetch.
- **Full guide escape hatch.** `CompanionDisplayCard` now lists all authored Mentor Tips as a sixth expanded guide section after Resources, regardless of whether each tip is currently eligible to surface near Today's Focus. Existing five-section order is unchanged.
- **Staleness/adopt parity.** Companion structure staleness remains child/note-membership-only, so Mentor Tip text/config changes do not mark content outdated. Cross-owner adopt/copy carries Mentor Tips the same way it carries the rest of Companion content.
- **Fix: Pro-only paywalls no longer offer dead-end Plus checkout.** Board Exam Mode, Long Exam, Difficulty Selection, and Interview Practice paywalls now map to Pro-specific CTA labels and also gate the Plus plan card's selectability plus `startCheckout` itself. This closes the trust bug where fixing only the label/context layer still left the visible Plus card clickable for features Plus does not unlock.
- **Fix: review-timing upsell no longer opens the Adaptive Practice paywall.** Free users tapping the Note Detail review-timing upgrade row now see dedicated "See your review timing" copy with Plus as the primary path and Pro still selectable, matching the actual Plus/Pro entitlement split.
- **Help Center coverage for current review-set guidance.** Added a Learning Companion guide covering Companion, Today's Focus, Mentor Tips, the collapsed Full Guide, and official-author workflow. The Study Plans & Collections guide now also explains Primary Review Sets and target-date / Weekly Countdown pacing.
- **Fix: stale "This Week" references caught at pre-signoff audit.** The new Study Plans & Collections Help copy and `docs/features/collections.md` still described the standalone weekly-countdown card that v0.43.0 removed. Both now describe the actual current behavior (countdown line inside the Progress card, daily budget inside `TodaysFocusCard`'s coaching sentence); `docs/features/collections.md`'s page-hierarchy description was also updated to match the v0.43.0 Coach/Progress/Companion order. No behavior changed — documentation only.

---

## v0.43.0 - Companion Coach Experience

**Status: Released**

Theme: give the Review Set detail page a coach-voice presentation layer over the Companion and an already-loaded live signal (whether a primary action remains) — frontend-only, no new engine, no new backend.

### Planned Scope

- **Coach-voice terminology mapping (frontend).** A static section-key → coach label/icon map, same shape as `getCollectionLabels`. Applied in `CompanionDisplayCard`; strictly order-preserving — renders sections in the curator's authored sequence, no reordering.
- **Coach-voice composition of existing live signals (frontend).** `TodaysFocusCard` merges the already-resolved primary action (`primaryStudyAction`/`getNextPlanAction`), target-date pacing, due-concept review, and terminal exam/builder actions into one Coach surface above Progress and the authored Companion. No new data fetch.
- **Curator-authoring guidance note (docs).** Short addition to `docs/features/companion.md` so authored prose and the new coach framing don't visually fight each other.
- **"View Full Guide" collapse (added mid-release, frontend).** `CompanionDisplayCard`'s five sections stop rendering inline on the collection detail page; they move behind a "View Full Guide" disclosure below Today's Focus and Progress. No new data, no new persisted state. See `docs/product/ROADMAP.md`'s "Coach vs. Companion" refinement for why.

Anti-drift: no reordering of authored Companion sections (narrative-flow reason — today's long-form sections assume reading order; this is specific to the current content shape, see `docs/product/ROADMAP.md`'s v0.43.1 candidate section); no generation — relabeling is not synthesis, "Curation, never generation" stays locked; no new backend, endpoint, or persisted state; does not reopen Timeline/Checklist as authored prose (stays live-feature embeds per v0.42.0); no Ask Companion, no Personalization, no nav/Dashboard change; labels continue through `getCollectionLabels`.

### Shipped

- **Coach-voice terminology mapping.** `COMPANION_COACH_HEADINGS` in `collection-detail-page-client.tsx` maps each of the five Companion sections to a coach-voice heading (Overview → "🗺️ What this covers", Study Strategy → "🧭 How to study this", Common Mistakes → "⚠️ Avoid these traps", FAQ → "💬 Common questions", Resources → "📎 Extra resources"). Order and authored text unchanged.
- **`TodaysFocusCard` Coach hierarchy.** Replaced `PrimaryActionCard` and `CompanionCoachIntro` with one top-of-page Coach card that answers "what should I do next?" in one glance: primary action title, `Continue Studying`, deterministic pacing/encouragement copy, and Quick Actions for due-concept review plus the existing terminal exam/builder action. Quick Actions render even when no primary note action remains, preserving exam/builder access in caught-up states. Coach encouragement is no longer gated on authored Companion content; missing/empty Companion hides only the Companion card.
- **Deleted `GoalWeeklyCountdownCard`; redistributed pacing data.** Weeks remaining and concepts remaining now render in a generic compact `ReadinessSummary` `countdown` slot on the Progress card when a target date exists. Today's concept budget now appears only in `TodaysFocusCard`'s pacing sentence when the same target-date state is present.
- **Progress/Guidance placement.** `ReadinessCardFooter` now keeps only `View full progress`; `Review Due Concepts` moved into Today's Focus Quick Actions. The post-adopt target-date `GuidanceTip` keeps its existing condition, dismissal, analytics, and edit-modal action, but now renders directly after Progress in the Goal branch.
- **"View Full Guide" collapse.** `CompanionDisplayCard` now renders collapsed by default (regardless of viewport) — its header always shows, its five sections only render once expanded. Local component state, not persisted. Toggle is a light inline text+chevron affordance (no border/background), not a bordered pill button — a labeled pill competed with the title for width and forced its own line on mobile; caught from a screenshot review and fixed the same round, keeping the text label (not a bare icon) since the Companion's value isn't self-evident the way the note-list sections' collapse targets are.
- **Expanded Companion reskin.** Expanded guide sections now render as icon-led sub-panels with restrained tinting while preserving the authored Overview → Study Strategy → Common Mistakes → FAQ → Resources order and `SummaryMarkdown` bodies.
- **Fix: Quick Actions didn't read as buttons.** `Review Due Concepts` had its button chrome explicitly stripped (`border-0 bg-transparent shadow-none`, blue text) into a bare link, and the terminal exam/builder action used `variant="ghost"` — both read as plain text, not clickable actions. Both now use the existing `variant="outline"` treatment (the design system's own secondary-button style) so they read as buttons while staying visually subordinate to the filled `Continue Studying` primary. Dropped the `ChevronRight` separator between them — breadcrumb-style separators read as connected label fragments, not two independent buttons.
- **Fix: Quick Actions read as two mismatched buttons on mobile.** Both were content-width, so on narrow screens they wrapped to separate lines with different, misaligned widths. Continue Studying and both Quick Actions are now `w-full sm:w-auto` — full-bleed, matching-width rows stacked on mobile (the native action-sheet pattern, same mobile-column/desktop-row shape already used elsewhere on this page), reverting to today's side-by-side content-width layout from `sm:` up. A `grid-cols-2` equal-width layout was considered and rejected: either Quick Action can be independently absent, so a fixed 2-column grid would regularly render a half-width button next to an empty cell, and the exam action's explanatory subtext would wrap into 3–4 cramped lines in a half-width cell instead of reading as a clean full-width footnote.
- **Fix: Review Set/Study Plan detail title crumpled into a word-per-line column on narrow screens.** `PlanHeroCard`'s title and actions (Published badge, icon buttons) sat in one `flex-wrap` row; the title column's `min-w-0 flex-1` let the browser shrink it toward zero width to keep the `shrink-0` actions column on the same row, instead of wrapping actions below — `min-w-0` on a flex-grow item suppresses the wrap. Now stacks vertically by default (title full-width, actions below) and switches to the original side-by-side row from `sm:` up. Unrelated to Companion; surfaced from the same screenshot review.
- **Anti-drift confirmed.** This reorganization is frontend-only: no backend endpoint, persisted state, migration, Companion content-model change, section reorder, or Companion authoring/generation change.

---

## v0.42.1 - Companion & Progress Polish

**Status: Released**

Theme: small UX fixes surfaced from using v0.42.0 in practice — no new features, no backend changes.

### Planned Scope

- **Merge Readiness card and "View full progress" row (frontend).** On the Review Set detail page, the readiness card and the "View full progress"/"Review due concepts" row currently render as two visually separate elements even though they're the same Readiness tier (already documented as such in `docs/features/collections.md`). Add an optional `footer` slot to the shared `ReadinessSummary` component so both actions render inside the same card.
- **Fix Progress page backlink for scoped views (frontend).** `/progress?collectionId={id}` always shows a "Dashboard" backlink regardless of how the page was reached. When reached via a specific collection's "View full progress" link, the backlink should return to that collection instead.

Anti-drift: no backend change, no new endpoint, no new persisted state; course/program stays plain text on collection cards/detail (metadata, not identity/state, per `docs/features/collections.md`'s badge-classification rule) — considered and declined, not silently skipped; unscoped `/progress` (reached from nav, no `collectionId`) keeps its existing "Dashboard" backlink, unchanged.

### Shipped

- **Merged Readiness card and "View full progress" row (frontend).** `ReadinessSummary` gains an optional `footer` slot rendered inside its existing card; the Review Set detail page passes "View full progress"/"Review due concepts" through it instead of a separate stacked box.
- **Fixed `/progress?collectionId={id}` backlink (frontend).** Now returns to the originating collection (profile-aware label) when reached via that collection's "View full progress" link, instead of always showing "Dashboard" — keyed off the URL's `collectionId` param, not live selection state, so a plain nav-in defaulting to the Primary Review Set still shows "Dashboard".
- **Considered and declined:** course/program as a badge on collection cards/detail — stays plain text per the existing badge-classification rule (metadata is never a badge).

---

## v0.42.0 - AI-assisted Companion authoring + regeneration

**Status: Released**

Theme: give curators an LLM-assisted first draft for Learning Companion content, with mandatory human review before publish — proving out the "Curation, never generation" rule's curator-facing clarification from v0.41.0's design work, while keeping the learner-facing guarantee (no auto-generated plans) unchanged.

### Planned Scope

- **Curator workflow (backend + frontend).** `Generate Companion` action (per section or all four) → LLM draft → mandatory human review and edit in the existing authoring modal → `Publish`. Publishing is never autonomous. Reuses the existing OpenAI service and PREMIUM/CRITIQUE model tiers — no new LLM infrastructure.
- **Granular per-section regeneration (backend + frontend).** Overview / Study Strategy / Common Mistakes / FAQ regenerate independently, not an all-or-nothing action.
- **Staleness signal (backend + frontend).** A "Companion may be outdated" indicator when the set's structure changes since authoring — a lightweight stored structure snapshot (child count / note ids / concept count) compared on read, no new job infrastructure.
- **Resources section (backend + frontend).** Adds the Resources section deferred from v0.41.0's four-section MVP.
- **Timeline/Checklist live-feature embeds (frontend).** Deferred from v0.41.0; when built here, these must link the already-shipped live weekly countdown (v0.40.0) and readiness features — never re-authored as static prose.

Anti-drift: learner-facing behavior is unchanged — curation over generation, a learner never receives an auto-generated plan; the curator-facing AI-assist is new but scoped to Official Review Set Companions only, per the documented rule clarification in `docs/product/ROADMAP.md`'s "Guided Learning Initiative" section; publishing stays non-autonomous in every path; no new LLM infrastructure (reuse the existing OpenAI service); no new job infrastructure for the staleness signal; Timeline/Checklist must link live features, never re-author them.

### Shipped

- **AI-assisted Companion draft generation.** Added ADMIN-only per-section and all-section Companion draft generation in the existing authoring modal, backed by a stateless `POST /collections/{id}/companion/generate` endpoint and the existing OpenAI service's PREMIUM model tier. Drafts populate local form state only; curators must still review/edit and click Save before anything persists. The same action generating an already-authored section is the granular per-section regeneration the "Planned Scope" bullet above called for — no separate regenerate endpoint was needed.
- **Companion staleness signal.** Added an ADMIN-only "Companion may be outdated" authoring signal on Review Set detail, backed by a nullable `note_collections.companion_structure_snapshot` captured only when Companion is saved and compared inline on the existing Goal detail read. Known limitations: v1 compares only member count plus sorted child/note ids; it does not compare note body edits or concept counts because the existing concept-count pipeline is per-user progress work, not a cheap structural signal.
- **Companion Resources section.** Added a fifth manual-only Resources field to Companion content so curators can author markdown links and references without mixing them into strategy prose. Resources saves through the existing Companion Save path, renders after FAQ with the shared markdown renderer, and is deliberately excluded from Companion generation and staleness comparisons.
- **Timeline/Checklist live-feature embeds — satisfied by placement, no code change.** v0.41.1's information-hierarchy reorder already positions `CompanionDisplayCard` immediately below the live weekly countdown (`GoalWeeklyCountdownCard`) and readiness (`ReadinessSummary`) cards, in that order, on both the Goal and Leaf view branches. A jump-link from inside Companion would point at a card one scroll away, and a duplicate live-data recap would re-render the same numbers a card apart on the same screen — neither adds value beyond what the existing adjacency already provides. The "link the live features, never re-author them as static prose" intent is satisfied by that placement; no separate embed was built.
- **Fix: `setCompanion` null-content guard.** Surfaced by this release's pre-signoff pressure test (three-PR cross-cutting audit, since the curator-generation, staleness-signal, and Resources-section PRs all touched the same shared Companion save path). A `PUT /collections/{id}/companion` call with a null body previously still computed and persisted a non-null `companionStructureSnapshot` while leaving `companion` null — inconsistent with the file's own "clear both together" convention used everywhere else. `setCompanion` now rejects a null body before touching the collection, matching every other request-DTO mutator in `NoteCollectionService`.

---

## v0.41.1 - Review Set Detail Page: This-Set Study Dashboard

**Status: Released**

Theme: re-compose the Review Set detail page so it orients the learner ("what should I do next, in this Review Set?") instead of introducing the set as a collection screen — a frontend-only re-composition of already-shipped pieces (Readiness, Weekly Countdown, Companion, next-action surfaces), scoped to this Review Set only (the cross-set "which set" job stays with `/dashboard`). See `docs/product/ROADMAP.md`'s "Review-Set-Centric Navigation" section for the design rationale this release advances (detail-page slice only).

### Planned Scope

_(shipped below)_

Anti-drift: no backend change, no new endpoint, no new persisted state; does not wire the unwired, user-scoped `TodayFocusCard`/`MasterySnapshotCard` (wrong scope for a this-set page); no day-level scheduler ("today's schedule" stays a `todaysConceptBudget` number + countdown, Phase 2 weekly work stays deferred); no nav/Dashboard change; no Ask Companion, Resources, or Achievements; no feature-gate/billing change; publish validation unchanged.

### Shipped

- **Information hierarchy reorder.** Goal and Leaf detail now render as Identity → Current Journey → Primary Action → Readiness → Guidance → Subject Plans/Notes → Progress. There is no separate "Supporting info" page section — the hero itself carries both the metadata line and authoring chrome (see below), so nothing about managing the collection occupies its own tier in the learner's scroll path.
- **Single primary action.** The previous next-card, continue banner, and mastery-step competition now resolve into one free-tier-safe `Continue` action. The terminal exam CTA (e.g. `Take the Board Exam`) renders stacked below `Continue`, using the muted `ghost` button style, so it reads as a periodic checkpoint rather than a same-weight peer action.
- **Badge cleanup.** Primary is now a hero accent (border + indicator), Adopted remains an identity badge, and course/program, estimated hours, and notes-ready/Subject-Plan-count collapse into one muted metadata line beneath the title (e.g. `Nursing · 4/6 notes ready · ~3 hrs`).
- **Authoring controls as compact hero chrome.** Publish, Build, Edit, Manage Companion, Set/Remove primary, and Delete are grouped into a small `⋯` menu + Build button in the hero's top-right corner, next to the title — not a dedicated body card. This was refined once post-implementation: the first pass built a full "Manage this Review Set" card at the bottom of the page, which a follow-up UX review flagged as having over-corrected from "admin controls interleaved in the hero" to "admin controls interrupting the learner's scroll." The fix keeps controls present but visually minimal wherever they sit — the design rule going forward is "helps the learner study today → learning flow; changes the collection itself → compact chrome," now recorded in `docs/features/collections.md`.
- **Companion placement.** Existing Companion display renders directly after Readiness in the Guidance tier, without changing Companion content or authoring behavior.
- **Test coverage closed.** Added a dedicated test for the Goal view's Primary Action fallback (resolves to the first child Subject Plan when there's no continue/next-note action) and a BOARD_EXAM-profile assertion on the hero eyebrow/back-link, closing two gaps the original Codex prompt asked for but didn't fully cover.
- **`/collections` list card Primary treatment.** Follow-up UX review found the list card's Primary badge was the one surface left on the old filled-pill treatment after the detail hero moved to a card-level accent (this morning's reorder). Mirrored the same treatment here: Primary now renders as a left-accent border + tinted background on the card, with plain caption text (Star icon, no pill), matching `PlanHeroCard` exactly. Confirmed the card's existing identity/state/metadata tiering (from the two prior "badge hierarchy" polish passes) already matched the requested hierarchy — no reorder needed, only Primary's visual form changed. Documented the badge-classification rule (identity vs. state vs. metadata; metadata is never a badge) in `docs/features/collections.md` so future Guided Learning additions (Companion indicator, Weekly Plan glance, Readiness) get checked against it before reaching for a pill, without pre-building placeholders for identity/state data (Official, Community, Archived) that doesn't exist in the backend yet.

---

## v0.41.0 - Learning Companion (MVP)

**Status: Released**

Theme: add a persisted, curator-authored guidance layer on top of Official Review Sets — the missing piece between "a collection of notes" and a premium guided learning experience. See `docs/product/ROADMAP.md`'s "Guided Learning Initiative (Companion)" section for the full design rationale.

### Planned Scope

- **Persisted Companion content model (backend).** A JSONB column on the top-level `note_collections` row (not a new table), mirroring the existing `sessionState` JSONB precedent. 1:1 with a top-level collection only — rejected on child Subject Plans with the same `400` pattern as the existing `targetCompletionDate`/primary hierarchy validation.
- **Four sections only: Overview, Study Strategy, Common Mistakes, FAQ.** Study Timeline and Final Checklist are explicitly deferred to v0.42.0+ and must never be static prose when built — they link the already-shipped live weekly countdown/readiness features instead of re-authoring them.
- **Manual authoring only, Official Companions only (frontend + backend).** No AI generation yet. Only the NoteLib official author can author a Companion in this version.
- **Publish/adopt integration (backend).** Publishes with the Review Set via the existing `updateVisibility`/`publishChildCollections` cascade. Travels on adopt (added to `persistAdoptedGoal`'s copied set, the opposite of `targetCompletionDate`'s exclusion). Owner self-copy excludes the Companion, same category as the existing generated-content exclusion.
- **FREE for all learners.** Zero paid uplift by design — an activation/retention bet.

Anti-drift: no runtime LLM call to serve a Companion (authored once, served static); no new top-level entity; no change to the 5-mode quiz contract; no change to `UserEntity`; Companion label resolves through `getCollectionLabels` (new `companionSingular` field, same pattern as `primarySingular`).

### Shipped

- **Learning Companion backend content model.** Added nullable `note_collections.companion` JSONB plus `CompanionContent` / `CompanionFaqItem` DTO records, surfaced Companion on existing collection detail reads (`GET /collections/{id}` and `GET /collections/{id}/goal`), and added ADMIN-only `PUT`/`DELETE /collections/{id}/companion` write endpoints for top-level collections (`parentCollectionId == null`, not child count). Adopt now copies Companion on genuine cross-owner Review Set adoption and excludes it on same-owner self-copy; publish cascade remains row-local with no child Companion logic.
- **Learning Companion authoring UI.** Added an ADMIN-only `Manage Companion` action on eligible top-level Review Sets with a modal for Overview, Study Strategy, Common Mistakes, and FAQ authoring, wired to the existing full-replace and clear endpoints.
- **Learning Companion learner display.** Collection detail now renders authored Companion guidance for top-level Review Sets in both Goal and leaf views, using the shared markdown prose renderer and skipping empty draft sections. This completes v0.41.0's planned Companion scope: content model, official authoring UI, and learner-facing display.
- **Companion terminology fix (pre-signoff pressure test finding).** All "Companion" copy on the collection detail page now resolves through a new `companionSingular` field on `getCollectionLabels`, per the kickoff's own anti-drift rule, instead of hardcoded literal strings. Also fixed one adjacent hardcoded "Review Set" in the Companion editor's description text. No visible copy change today (the value is `"Companion"` across every profile), but closes a real cross-PR consistency gap a per-PR review wouldn't catch.
- **Review Set badge hierarchy (frontend polish).** `Primary`/`Adopted` identity badges moved next to the title (detail page hero) or directly under it (list page cards), out of the flattened row they previously shared with the notes count and the execution-status badge. List cards now stack cleanly: title → identity badges → status badge → notes-or-plans count + last-updated at the bottom. courseProgram/estimated-hours/Published stay in the existing top eyebrow row on the detail page — no conflict since they're a different tier from Primary/Adopted. Also fixed a related grid-height bug: `/collections` list cards now stretch to a uniform height per row (`h-full` on the card, matching the pre-existing `flex flex-col justify-between` internal layout) so the notes-count/last-updated footer stays pinned to the bottom of every card regardless of title length or badge count, instead of each card sizing independently and producing a visibly ragged row.
- **Review Set success-toast feedback (frontend polish).** Editing details, setting/removing primary, and saving/removing a Companion now show a success toast on the collection detail page, reusing the existing `ToastMessage` component and the same local-state pattern already used elsewhere in the app. Deleting a collection shows the toast on the `/collections` list page after navigating back, via a new one-shot `sessionStorage` flash notice (mirroring the existing `just-adopted-notice`/`study-plan-skipped-notice` pattern). Scoped to Review Set actions only; app-wide CRUD feedback is tracked separately in `docs/product/ROADMAP.md`'s Post-v0.41.0 Polish Backlog.
- **Pre-signoff pressure test fixes.** A full whole-release audit (two source-reading agents covering every backend/frontend file touched this release, since 4 different PRs touched the same `collection-detail-page-client.tsx`) found two real issues, both fixed: (1) `adoptGoal`'s inline child-reparenting loop didn't null out `targetCompletionDate`/`companion` the way `updateParent()` already does for the identical operation — currently unreachable because a child never has a Companion to begin with, but now defended the same way at both reparenting call sites so a future change to the adopt/copy path can't silently leak one; (2) the success-toast timers on both `/collections` and the collection detail page used bare `setTimeout`/`clearTimeout` instead of `globalThis.setTimeout`/`globalThis.clearTimeout`, contradicting this file's own established convention and CLAUDE.md's `globalThis` rule — fixed at all call sites. A third suspected issue (list-page flash toast never auto-dismissing) was checked against source and found to be a false positive — the 4-second auto-dismiss effect was already present and working.

---

## Archived releases (v0.40.1 and earlier)

Full detail for every version below moved to `docs/archive/RELEASES_ARCHIVE.md` on
2026-07-10 to keep this file lean and readable in one pass. Condensed per-version summaries
(user-facing, no implementation detail) also exist at `docs/releases/vX.Y.Z.md` for each.

- `v0.40.1` - Public Review Set Reachability
- `v0.40.0` - Weekly Study Plan (Exam Countdown) + Primary Review Set
- `v0.39.2` - Public Library Learning Experience
- `v0.39.1` - Study Plan Builder Polish
- `v0.39.0` - Flexible Review Methods
- `v0.38.0` - Read-Path Optimization Pass
- `v0.37.4` - Idle GC & Metaspace Ceiling Hotfix
- `v0.37.3` - Study Plan Read-Path Memory Optimization
- `v0.37.2` - Plan Data Integrity Hotfix
- `v0.37.1` - Native Memory Hotfix
- `v0.37.0` - Readiness-First Plans & Mastery Integrity
- `v0.36.3` - OCR Fast-Follow: Messaging & Feedback
- `v0.36.2` - OCR Disable Hotfix
- `v0.36.1` - Post-Release Fixes
- `v0.36.0` - Readiness/Progress Merge
- `v0.35.0` - Mobile-First Builder
- `v0.34.0` - Journey: Goal-First Study Experience
- `v0.33.4` - Builder Surface Clarity
- `v0.33.3` - Recursive Goal Adopt
- `v0.33.2` - Plan Detail Redesign (view/edit split)
- `v0.33.1` - Study Plan polish & Curated Plan Coverage
- `v0.33.0` - Study Plans as a Retention Engine
- `v0.32.2` - Conversion Diagnosis & Quota Honesty
- `v0.32.1` - Monetization Surfacing & Pricing Clarity
- `v0.32.0` - Account & Communication Controls
- `v0.31.2` - Analytics Integrity & Funnel Visibility
- `v0.31.1` - Adoptable Study Plans Discovery & Status
- `v0.31.0` - Adoptable Study Plans
- `v0.30.1` - Copy Flow Polish
- `v0.30.0` - Readiness Signals
- `v0.29.1` - Bulk Generation Polish
- `v0.29.0` - Bulk Generation
- `v0.28.0` - Feature Discoverability & Activation
- `v0.27.0` - Material Import & Collections
- `v0.26.1` - Guidance System
- `v0.26.0` - Exam Depth
- `v0.25.1` - Polish & Quick Review Fixes
- `v0.25.0` - Exam Capture & Goal Setting
- `v0.24.1` - Content Moderation Hotfix
- `v0.24.0` - Guided Learning
- `v0.23.1` - Quiz Format Fix
- `v0.23.0` - From Readers to Learners
- `v0.22.0` - Course & Subject Discovery
- `v0.21.0` - Personalized Discovery & Library Organization
- `v0.20.0` - Conversion & Re-engagement
- `v0.19.0` - Multi-Note Depth & Simulation Parity
- `v0.18.0` - Profile Completeness & Communication
- `v0.17.0` - Quiz Quality & Depth
- `v0.16.0` - Conversion & Growth
- `v0.15.2` - UX Cleanup & Bug Fixes
- `v0.15.1` - Teacher Power Features
- `v0.15.0` - Premium Mode Uplift + Cost-Control Quota Refactor
- `v0.14.0` - Grow the Surface, Deepen the Practice
- `v0.13.0` - Complete the Promise, Reach New Audiences
- `v0.12.0` - Learning Experience, Discovery, and Retention
- `v0.11.0` - Learning Flow Foundation
- `v0.10.1` - Landing & Pricing Conversion Polish
- `v0.10.0` - Profile Type System & Teacher Flow Phase 1
- `v0.9.0` - Learning Experience & Product Polish
- `v0.8.0` - Board Exam Mode + Public Library Discovery System
- `v0.7.0` - Learning & Metadata Foundation
- `v0.6.0` - Landing Revamp & Positioning
- `v0.5.0` - Public Profiles & Public Notes
- `v0.4.0` - Profile-Based Experience & UX
