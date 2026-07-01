# RELEASES.md - NoteLib

## v0.34.0 - Journey: Goal-First Study Experience

**Status: In Progress**

Theme: transform the study plan detail from a note list into a guided study surface. Every piece reuses shipped infrastructure — no new AI, no new mastery signal, no new quiz model. Composition over rewrite.

### Planned Scope

- **Section readiness on plan detail (backend + frontend).** Reverse the v0.33.x locked rule: section cards now show readiness % and concepts due. Backend adds per-note concept health counts (mastered/due/total) to the plan detail response; frontend aggregates by section label client-side. Lazy-loaded post-initial-render to avoid blocking first paint. No new mastery signal or stored field.
- **Estimated study time (backend + frontend).** Add optional `estimatedStudyHours` field to collection entity via Flyway migration. Curator-entered. Carries over on adopt. Never blocks adopt or quiz generation.
- **Plan Hero (frontend).** Surface plan title, description, course/program, and estimated study time as a visual hero card on the plan detail page. No backend changes.
- **"Continue where you left off" (frontend).** Identify the last-studied note via `argmax(lastSessionCompletedAt)` — already present in the plan detail response. Adaptive CTA drives directly to that note's next action. Zero new backend work.
- **Builder for leaf plans (frontend).** Extend `/collections/{id}/builder` to detect leaf vs Goal and render a single-plan canvas: notes as draggable cards with section-label drag-and-drop assignment. Eliminates the inline organize mode toggle from leaf plan detail pages once the Builder handles them. Existing endpoints only — no new API.

Anti-drift: section readiness reuses existing `ConceptHealth` / `ProgressReportService` — no new signal, field, or AI call; `estimatedStudyHours` is always optional; Builder leaf canvas uses existing collection endpoints only; "Continue where you left off" uses `lastSessionCompletedAt` already in the detail response; no new chart library; readiness stays Free; adopt stays Free; no quota / billing / price / checkout changes; no 3rd hierarchy level; 2-level max enforced.

### Shipped

---

## v0.33.4 - Builder Surface Clarity

**Status: Released**

Theme: the Goal Builder is an authoring surface, not a study surface. The `ReadinessSummary` ring currently appears on the Builder page — the same component used on the plan detail — creating role confusion between editing and monitoring. v0.33.4 removes it from the Builder, where per-module stats in each Subject plan header already provide all the authoring-relevant signal a curator needs.

### Planned Scope

- **Remove `ReadinessSummary` from Builder page (frontend).** The top-level readiness ring (`<ReadinessSummary>` at the Builder page root) belongs on the plan detail, not the builder canvas. Per-module readiness stats in each Subject plan header ("4 notes · 0% ready · 0/34 mastered") are retained — they are inline curation signals, not the monitoring ring. Frontend-only; no backend changes.

Anti-drift: readiness still appears on the Goal detail page via `GoalDetailView` (unchanged); per-module stats in Subject plan header rows stay; no new component, endpoint, or migration; no change to leaf-plan organize mode (Builder-for-leaf-plans is scoped to v0.34.0).

### Shipped

- **Remove `ReadinessSummary` from Builder page (frontend).** Removed the top-level readiness ring from the Goal Builder canvas. The `<ReadinessSummary>` block and its now-unused import were deleted from `study-plan-builder-page-client.tsx`; readiness continues to appear on the Goal detail page via `GoalDetailView` (unchanged). Per-module stats in each Subject plan header row ("4 notes · 0% ready · 0/34 mastered") are retained as inline curation feedback. Also fixed a typo in the builder page description ("subject plan plans" → "subject plans").

---

## v0.33.3 - Recursive Goal Adopt

**Status: Released**

Theme: the curated-plan experience is only complete when a learner can adopt a Goal and receive all its child Subject plans and notes in one action. Today only leaf plans are adoptable — a curator building a multi-Subject Goal plan cannot surface it as a single unit. v0.33.3 closes that gap.

### Planned Scope

- **Recursive Goal adopt** (backend + frontend). Adopting a Goal recursively copies all child Subject plans and their notes into the learner's library, consistent with the Study Pack copy-and-edit principle. Each adopted note is the learner's own copy, editable and removable immediately — the Study Pack precedent (copy freely, edit freely) applies. Adoption stays Free. Unlocks the end-to-end Goal → Subject readiness view: once adopted, each child Subject plan shows its readiness bar in the Goal detail (already ships from v0.33.1).

Anti-drift: 2-level max (Goal → Subject) — no 3rd level; adopt stays Free; adopted plan readiness reuses `ProgressReportService` (same as owned plans); no new mastery signal, persisted field, AI call, or chart library; no quota / billing / price / checkout change; teacher bulk-quiz stays a future gated release.

### Shipped

- **Recursive Goal adopt** (backend + frontend). Public Goal collections can now be adopted through `POST /collections/{id}/adopt-goal`, returning an `AdoptGoalResponse` with the personal Goal id plus adopted/skipped Subject and note-copy counts. The learner receives a private top-level Goal plus copied child Subject plans nested at the source sibling positions; adopted notes remain freely editable personal copies. Existing standalone adopted Subjects are re-parented under the new Goal, while Subjects already nested under another personal Goal are skipped rather than duplicated or moved.
- **Goal publish and discovery rules** (backend). Publishing a Goal now validates every child Subject has public notes and cascades the publish action to child Subject plans. Unpublishing a Goal does not cascade. Public plan listing now returns root collections only, so child Subject plans no longer appear as standalone public cards.
- **Goal adopt analytics**. Added the `STUDY_GOAL_ADOPTED` analytics event to the backend enum and frontend union before firing it, with metadata for adopted/skipped Subjects, copied/skipped notes, and idempotent re-entry.

---

## v0.33.2 - Plan Detail Redesign (view/edit split)

**Status: Released**

Theme: the v0.33.x study-plan series continues. v0.33.1 shipped hierarchy (Goal → Subject) and curated-plan content; now the **leaf plan detail is the gap** — on mobile, 29-note plans render a wall of edit chrome that makes the page unusable. v0.33.2 is a frontend-only polish release targeting the mobile experience without touching locked rules. v0.33.3 (recursive Goal adopt) follows.

### Planned Scope

- **Plan detail mobile redesign (collapsible section cards + view/edit split, frontend).** Sections become collapsible cards: collapsed state shows section header + note count, no edit chrome; expanded shows note rows. View mode is the default — SECTION combobox, Move, and Remove controls are hidden by default and revealed only in an organize/edit mode. No readiness on section headers (sections stay label-derived; locked rule unchanged). Codex prompt required.
- **Subject metadata normalization on LET curated plan** (data ops). Normalize inconsistent `subject` field variants on the 29-note LET Professional Education plan so the readiness page groups correctly. No code change — metadata-only cleanup confined to that plan's notes; no change to other plans.
- **Study Pack subject source fix** (backend). Root cause of readiness subject drift: LLM freely invents a `subject` string and `normalizeSubject()` only catches exact case/whitespace variants, not semantic synonyms. Fix: `StudyPackService.setSubject(...)` now prefers `note.subject` over the LLM-generated value when the note already has one. Prevents future grouping drift on all curated plans without touching the data model.
- **Readiness grouping by note subject** (backend). `ProgressReportService.resolveSubject()` now resolves the grouping key from the note's own `subject` field (batch-fetched, no N+1), falling back to `studyPack.subject` when the note has none. Heals historical drift on all existing plans without per-plan data patches.

Anti-drift (carried forward and new): sections stay label-derived (no section entity, no mastery on section headers/cards); readiness stays Free, derived, matches `/me/progress`; no new backend endpoint or model this release (frontend-only); no new chart library; no quota / billing / price / checkout change. Recursive Goal adopt is deferred to v0.33.3.

### Shipped

- **Leaf plan detail view/organize split** (frontend). `/collections/[id]` now opens in a clean read view: note cards show title, metadata, execution status, due-concept signals, and admin private badges without drag handles, Section comboboxes, Move buttons, or Remove buttons. A Notes-card `Organize` toggle reveals the structural controls only when needed, and label-derived sections now render as collapsible cards whose initial expanded state follows the `lg` breakpoint (collapsed on mobile, expanded on desktop) with no readiness or mastery added to section headers.
- **Section card polish** (frontend). Section headers no longer use blue (which read as a link); the collapse toggle gains a hover background and a single rotated `ChevronDown` in place of the two-icon swap. Collapsed cards show a **title peek** (first 1–3 note titles + "+N more") so the card is never empty. In organize mode the section name becomes an **inline-editable input**: blur or Enter commits the rename as a single batch `setCollectionItemOrder` call; Escape cancels; renaming to an existing section name triggers a merge-confirmation modal. `"Ungrouped"` is reserved and cannot be typed as a target name.
- **Study Pack subject source fix** (backend). `StudyPackService` now saves `note.subject` as the Study Pack subject when the note already has one, falling back to the LLM-generated value only when the note has none. Prevents future readiness grouping drift on all curated plans without touching the data model.
- **Readiness grouping by note subject** (backend). `ProgressReportService.resolveSubject()` now reads from the note's own `subject` field (batch-fetched, no N+1), falling back to `studyPack.subject` when the note has none. Combined with the source fix above, this heals historical subject drift on all existing plans without per-plan data patches.
- **LET Professional Education subject normalization** (data ops). Normalized inconsistent `subject` field variants on the 29-note LET Professional Education curated plan so the readiness page groups the notes cleanly under consistent subject labels. No code change — metadata-only update confined to that plan's notes.

---

## v0.33.1 - Study Plan polish & Curated Plan Coverage

**Status: Released**

Theme: the study-plan line. v0.33.0 shipped the readiness *lever*, but a validation pull (`docs/product/journey-validation-pulls.md`) showed the plan-adoption retention bet was never testable — only **4 adoptions across ~153 users, 1 goal (Accountancy) with a ready plan, 0 post-adopt returns**. The binding constraint is **curated-plan coverage**, so v0.33.1 leads with coverage (content/curation ops) plus two UX-clarity polish items. Scope was then **deliberately expanded** to include **Study Plan Hierarchy Phase 1** — one level of Goal → Subject-plan nesting — so curated plans can be built *with levels* from the start (a schema change that makes v0.33.1 a large "patch", accepted). Phases 2–4 of the hierarchy stay a future, validation-gated initiative.

### Planned Scope

- **Curated Plan Coverage (headline, content/curation ops).** Run the follow-up inventory query in `journey-validation-pulls.md` to decide **assemble** (build plans from seeded public notes already on hand) vs **seed** (Bulk Generation for thin goals). Target ≥1 complete, credible, Study-Pack-ready curated plan per goal the actual public learners have. No new AI synthesis.
- **Study Plan Hierarchy — Phase 1 (Goal → Subject plans; backend + UI).** Add `parent_collection_id` (self-referential, **2-level enforced**: Goal → Subject, cycles impossible); a Goal detail page listing child Subject plans with each child's readiness plus a cheap **Goal % = Σ child.mastered / Σ child.total** (sums per-child counts, so **no cross-subject concept re-dedup**); the owned-plans list shows top-level only; a nest/unnest control for curation. Modules stay as label-sections. Deferred to later phases: recursive adopt-the-whole-Goal, per-module %, metadata, arbitrary depth. Codex prompt: `docs/codex-prompts/v0.33.1-study-plan-hierarchy-phase1.md`; architecture audit: `docs/product/STUDY_PLAN_HIERARCHY_PLAN.md`. This is a **deliberate, scoped reversal** of the "no parent/child collections" rule.
- **Recommended card — already-owned state (frontend).** Don't show a re-adopt CTA for a plan the user already owns or adopted; detect owned-source (not just `sourcePlanId`-adopted).
- **Section drag refinement (frontend).** Scope drag-and-drop per section so cross-section drag stops being confusing; sections stay label-derived.

Locked anti-drift: readiness stays Free (decided, not revisited); readiness derived and matches `/me/progress`; the Goal % sums child counts (no cross-subject concept re-dedup); the nesting reversal is **scoped to 2 levels** (Goal → Subject) — no arbitrary depth, no per-module mastery, no recursive adopt this release; no `ProfileType` branching in services (Goal/Subject terminology via `getCollectionLabels`); no quota / billing / price / checkout change; no new chart library. Deferred: hierarchy Phases 2–4 (validation-gated); "Journey" repositioning; teacher bulk-quiz & dashboard/plan-list readiness badges → v0.34.0.

### Shipped

- **Recommended card — already-owned state** (polish). The Recommended {singular} section (`DashboardStudyPlanSection`, on Dashboard and `/collections`) now detects when the learner already has the matched plan — either they **adopted** it (`sourcePlanId` match, as before) or they **own the published source itself** (`id` match, the admin/curator case). In both cases it shows an **"In your library"** badge and opens the existing plan instead of offering a re-adopt CTA — fixing the bug where an owner saw "Start this plan" and would self-adopt a redundant copy. CTA reads "Open this plan" for an owned source and "Continue this plan" for an adopted copy; only a genuinely not-yet-owned plan shows "Start this plan".
- **Section reorder scoped to within a section** (polish). On the Study Plan detail page, drag-and-drop now uses a `SortableContext` **per section** and Move up/down operate within the item's section, so reordering a note no longer crosses a section boundary, snaps back, or reshuffles the section order via min-position. Both reorder paths run against the **grouped display order** (sections contiguous), so a within-section move never renumbers another section's positions; cross-section drag is a no-op and Move buttons are disabled at section boundaries. To move a note to a different section, change its **Section** (label). Frontend-only; flat (no-label) plans are unchanged.
- **Study Plan Hierarchy Phase 1 — Goal → Subject plans** (backend + UI). Collections now support one owner-scoped parent level through nullable `parent_collection_id` (`ON DELETE SET NULL`) so curators can build a top-level Goal containing child Subject plans. Nest/unnest enforces the two-level shape in one transaction: parent must be top-level, child must have no children, self-parent is rejected, and cross-owner parent ids remain `404`. `/collections` returns top-level collections only; Goal cards show child-plan counts; Goal detail lists child plans with each child's existing readiness and a rolled-up Goal readiness using `round(100 × Σ child.mastered / Σ child.total)`, preserving cross-subject concept names instead of re-deduping them. Leaf plan detail, label-derived sections, adopt, and `/me/progress` are unchanged.
- **Study Plan Builder canvas for Goals** (hierarchy curation polish). Added a single-canvas builder at `/collections/{id}/builder` where the Goal is the canvas, child Subject plans are draggable collapsible sections, and notes are draggable cards inside/across those Subjects. The builder orchestrates existing collection endpoints: add Subject = create collection + nest, rename = metadata update, delete = delete child collection only, add notes = add items, move note across Subjects = remove + add with refetch-on-settle. The only new backend capability is explicit child ordering (`sibling_position` plus `PUT /collections/{id}/children/order`); `getGoal` now returns children in sibling order with null positions last. The old scattered nest/unnest detail-menu curation path is replaced by the builder entry point; learner Goal view, leaf detail, label-derived sections, adopt, and `/me/progress` stay unchanged.
- **Plan detail readability polish** (frontend). The leaf Study Plan detail reads less like a permanent edit form: section headers are emphasized (uppercase, bold, accent) so they're clearly dividers, not sub-labels; the per-note **Section** field uses the shared `SuggestionCombobox` (matching the app's metadata comboboxes) with a short debounced auto-save; and the plan header description is clamped (`line-clamp-2`) so it shows on mobile without dominating. A fuller view/edit-mode split (clean read view by default) is intentionally deferred until a real curated plan exists to design against.
- **Study Plans cards show course/program instead of description** (frontend). On the `/collections` list, each plan card now shows its **course/program** (when set) in place of the description — a more useful at-a-glance signal for scanning (especially exam-takers), reinforcing the course/program-scoped Recommended section. Cards with no course/program simply omit the line (no "No description yet." placeholder). The description now lives only on the plan detail header, where it's most valuable for longer text.
- **Fix: editing a plan's title/description no longer wipes its course/program (and vice versa).** `updateMetadata` overwrites every field present in the request, but the two metadata editors sent partial bodies — the Edit-details modal omitted `courseProgram` (clearing it) and the course/program Save omitted `description` (clearing it). Both editors now send the current values for the fields they don't change, so a partial edit preserves the rest while still allowing an explicit clear. Surfaced once the card began showing course/program.

---

## v0.33.0 - Study Plans as a Retention Engine

**Status: Released**

Theme: the constraint carried over from v0.32.2 is **near-zero W1→W2 retention (5.6%, recent cohorts ~0%)** — users activate but don't return (longest observed streak ~2 days). This release attacks retention through the natural trackable unit, the **Study Plan**: turn it from a static folder / one-time adoption surface into a **readiness journey** that gives a learner a number that only moves by returning to practice. **Track A** removes publish/discovery friction so curated plans actually reach learners (activation); **Track B** (headline) introduces **plan- and subject-scoped readiness** with charts (the retention lever). See `docs/product/ROADMAP.md` for full scope and anti-drift rules.

### Planned Scope

**Track A — Study Plan publish & discovery polish (activation)**

- **Decouple metadata-save from publishing.** Course/program and description persist independently of the Publish action, fixing the silent loss observed when Publish fails its notes validation (the typed course/program was discarded and the create-time description sometimes did not save). Publish stays gated on its existing rules (every note public, at least one note) with a clearer blocker message. The backend already exposes separate `updateMetadata` and `updateVisibility` endpoints; the fix is frontend sequencing in the publish modal + create flow.
- **Surface recommended plans on the user's own Study Plans page.** Reuse the Dashboard "Recommended {plural}" section (course/program-scoped, `See all N` → `/collections/published`) on `/collections` rather than tabs, so a learner arriving with intent to organize also sees curated plans for their track. Scoped to the same course/program only (all-programs browse is Public Library's job).

**Track B — Readiness as a retention signal (headline, two Codex prompts)**

Place one ConceptHealth-derived readiness signal where it has reach (audit: `/me/progress` already a Free subject-mastery dashboard; note detail already shows per-concept "due" badges but gated to PLUS/PRO; the plan scope is the only one missing). Organized as **signal vs. detail**.

- **Prompt ① — plan readiness detail surface + shared component** — owner-scoped `GET /collections/{id}/readiness`: overall readiness % + per-subject readiness scoped to the plan's notes, on a dedicated sub-route `/collections/[id]/readiness` (reached by a "Check readiness" CTA, not a tab, not inline on execution rows), rendered via the existing Progress bar pattern + inline SVG ring (no chart library) through a shared `ReadinessSummary` component. **Deliberately reverses** the "Study Plans do not duplicate Progress" decision (`collections.md`) on this dedicated surface only — execution-detail rows keep their no-mastery rule.
- **Prompt ② — per-note signal + Free-gate split** — a compact per-note readiness rollup ("% ready · X/Y mastered · N due") on note detail (from already-fetched concept-health), and **ungate the readiness signal to Free** (rollup + which concepts are due) while keeping the per-concept review-**timing** detail ("Due — 3d ago") PLUS/PRO. Aligns note detail with the already-Free `/me/progress`. Unify the readiness vocabulary (`ready / mastered / due / not started`) across note, plan, and Progress.

Locked anti-drift (see `ROADMAP.md` / `CLAUDE.md` for the full set): readiness reuses the existing ConceptHealth recency spine and must match `/me/progress` for the same concepts (reuse `ProgressReportService`, no new thresholds; no new mastery signal, no new persisted field on collections, no new generated content, no AI/LLM call); current-state only (no trend/snapshot); the Free-gate is an **access change, not a billing change** (no price/quota/pass-duration/checkout change); no new chart library. Deferred to v0.34.0: dashboard + plan-list readiness badges (signal reach); teacher bulk-quiz / teacher-flow polish.

### Shipped

- **Study Plan publish decouple — metadata no longer discarded on a blocked publish** (Track A). The publish modal (`PublishStudyPlanModal`) now persists a dirty course/program **before** the private-notes / empty-plan gate, and the unpublished state exposes a standalone **Save** action alongside Publish, so course/program (and description, via the existing edit/create paths) save independently of publishing. Publish validation is unchanged — every note public + at least one note, enforced on the backend — only the silent metadata loss is fixed. The backend already exposed separate `updateMetadata` / `updateVisibility` endpoints; this is frontend sequencing.
- **Library selection-create now collects a description** (Track A). The Library split-button `{singular}` create modal added an optional description field and passes it through `createCollection`, matching the `/collections` modal. A plan built by multi-selecting notes in the Library is no longer title-only — fixing the "description wasn't saved" report (that path simply had no field).
- **Recommended plans surfaced on the user's own Study Plans page** (Track A). `/collections` reuses the Dashboard "Recommended {singular}" section (`DashboardStudyPlanSection`) below the user's own plans — course/program-scoped, self-hiding when no plan matches, with the same `See all N` link to `/collections/published`. Not tabs; scoped to the learner's own course/program only.
- **Plan readiness detail surface + shared readiness summary** (Track B Prompt 1). Added owner-scoped `GET /collections/{id}/readiness` and the dedicated `/collections/[id]/readiness` route reached by a "Check readiness" CTA on collection detail. The endpoint resolves only owned collections, aggregates the plan's owned Study Packs through `ProgressReportService`'s existing ConceptHealth classification, returns overall and per-subject readiness, and serves documented zero shapes for empty/no-pack/not-started plans. The frontend renders a reusable `ReadinessSummary` (inline SVG ring + CSS readiness bars, no chart dependency), cross-links to `/progress`, and fires `PLAN_READINESS_VIEWED` once after a successful load. This records the scoped reversal: readiness belongs on the dedicated sub-route only; execution-detail rows stay no-mastery.
- **Per-note readiness signal + Free access split** (Track B Prompt 2). Note Detail now shows a compact `ReadinessSummary` rollup for ready notes with key concepts: `% ready`, `X/Y mastered`, due count, and not-started count, using the same `ready / mastered / due / not started` vocabulary as plan readiness and Progress. Free users can load the readiness signal and see per-concept status chips, while PLUS/PRO keep the review-timing detail (`Due — Nd ago`, timestamps, and struggling state); failed concept-health loads show a neutral unavailable state without hiding note content. This is an access/value-ladder change only — no price, quota, pass-duration, checkout, generated content, or persisted readiness change.
- **Readiness polish** (Track B follow-up). Restored the em-dash in the PLUS/PRO `Due — Nd ago` timing chip for consistency with the readiness vocabulary, and hardened the note-detail readiness fallback so a not-due concept resolves to `mastered` from the never-redacted `isDue` flag even if `readinessStatus` is absent — preventing a Free response (timing fields nulled) from collapsing to `not started`.
- **Curation UX — select all** (Track A follow-up). The Study Plan "Add notes" picker and the Library selection mode each gained a **Select all / Deselect all** toggle scoped to the current search/filter (modal: all eligible notes matching the search; Library: all notes matching the active filters, including those beyond the first display page). Removes the one-by-one tedium when assembling a plan from many notes.
- **Curated-plan discovery — honest empty state** (Track A follow-up). The Recommended-plans section on `/collections` no longer renders to nothing when the learner's course/program has no curated plan yet: it shows a clear "No curated plans for {program} yet" state (behind a `browseWhenEmpty` prop, so the Dashboard stays unchanged). Seeing all curated plans for the learner's program already existed via "See all N → /collections/published"; this only makes the scoped surface visible when coverage is thin.
- **Study Plan sections from item labels** (Track A follow-up). The plan detail page now groups notes into named sections derived entirely from existing `note_collection_items.label` values, with `position` still owning global order and unlabeled notes collected into a trailing **Ungrouped** section. Plans with no labels keep the old flat list. This is frontend-only, adds no nested/umbrella plan model or endpoint, and section headers/rows preserve the no-mastery/no-readiness rule.

## v0.32.2 - Conversion Diagnosis & Quota Honesty

**Status: Released**

Theme: v0.32.1 surfaced the premium exams and reframed pricing, but conversion was still ~0 of ~153 verified users. The funnel diagnosis ran first and **re-scoped this release**: the real, un-conflicted constraint is **near-zero W1→W2 retention (5.6%, recent cohorts ~0%)** — users activate (68.6%) and engage once (58.8%), then don't return. (An initial read flagged a "broken checkout" from 6 upgrade clicks → 0 `CHECKOUT_INITIATED`; that was a **metric-inception artifact** — `CHECKOUT_INITIATED` was added in v0.31.2 while `UPGRADE_CLICKED` is far older, and a live upgrade reached the real Xendit invoice. Checkout works.) Free is not too generous. **Anti-drift: do not raise exam quota numbers** — quota size is not the constraint; retention is. Full data in `docs/product/conversion-funnel-finding.md`; see `docs/product/ROADMAP.md` for sequencing.

### Planned Scope (re-prioritized after the funnel diagnosis)

- **Backend OOM fix — JVM heap + thread sizing** — recurring prod `OutOfMemoryError` (Render Starter, 0.5 CPU / 512 MB). Root cause: the container ran `java -jar` with no memory flags, so the JVM capped the heap at ~25% of RAM (~128 MB) while Spring Boot's default 200 Tomcat worker threads consumed native memory — starving the heap under load (all JSONB payloads were measured KB-scale, ruling out a "fat query"). Fix: `-XX:MaxRAMPercentage=50.0 -XX:+ExitOnOutOfMemoryError` in the Dockerfile and `server.tomcat.threads.max=25` (from 200) in `application.yaml`. A read-path projection change (session_state kept out of dashboard/retention aggregates) shipped alongside as defense-in-depth, not the root cause. Confirm via Render's memory graph over several days (flat-under-load = sized correctly; monotonic climb = a leak to hunt).
- **Conversion funnel diagnosis** *(done)* — read prod `/admin/funnel`; finding written to `docs/product/conversion-funnel-finding.md`. Corrected reading: checkout works; retention is the constraint. Drives the priorities below.
- **Retention diagnosis (top priority)** — W1→W2 retention is 5.6% (recent cohorts ~0%). Diagnose why activated users don't return and define + ship one scoped lever to test.
- **Plan-launch prescreen polish** *(shipped)* — "Choose another mode" is hidden on the Long Exam, Interview Practice, and Board Exam prescreens when launched from a Study Plan (`collectionId` present), since there is no mode grid to return to and the back link already routes to the plan. Note-launched flows are unchanged.
- **Plus-tier reason-to-exist** *(deferred)* — revisit once retention improves and there is real recent conversion data.

### Shipped

- **Quiz session aggregate OOM fix** — Dashboard and retention aggregate reads now use scalar/session-metadata projections that omit the eager `session_state` JSONB, preventing large completed-session histories from deserializing every question payload on GET/admin-style read paths. Dashboard overview loads Challenge history once for performance + focus areas, note-performance summaries preserve all-time values through scalar rows, retention weekly/weak-concept checks use bounded projection reads, and account data export processes completed sessions in batches instead of one unbounded entity list.
- **Date-windowed conversion funnel + quota-hit completeness** — Admin Conversion Funnel now defaults event-based stages to a common 30-day window, with 7 / 30 / 90 / all-time options and response metadata (`windowDays`, `windowStartedAt`) so `UPGRADE_CLICKED` and newer `CHECKOUT_INITIATED` data are not compared across mismatched inception periods. Free quota-hit now reports current-period per-type hits for Study Packs, Challenge Quiz, Adaptive Practice, Long Exam, Board Exam, and Interview Practice plus an "any quota hit" aggregate; Free-unavailable 0-limit types are excluded from their own denominators.
- **Quota honesty via per-session deduction** — Long Exam and Board Exam now deduct **1 unit per session** instead of per source note, so the existing "12 / 10 sessions" plan copy is literal without changing quota numbers or the monthly reset. Multi-note source count still drives question-count, source refs, and generation breadth; prescreens now say "uses 1 of N remaining" and no longer block a multi-note exam when one session remains.
- **Budget-aware inactivity reminders** — The daily inactivity dispatch now sends only within the configured shared email pool (`studysnap.email.daily-limit`, default 100) after reserving transactional headroom (`studysnap.email.transactional-reserve`, default 40). The budget is computed from same-day `email_log` sends, skipped candidates are not logged and naturally roll to the next daily run, and `studysnap.email.reengagement-enabled=false` turns inactivity dispatch into a no-op without affecting transactional email.
- **Inactivity reminders reachable by default** — New email/password and Google signups now default `inactivityRemindersEnabled=true`, while weak-concept, weekly-summary, and marketing preferences remain default-off. A migration backfills existing users to enable inactivity reminders only, preserving the other optional email flags.
- **Resend suppression handling** — Added a signature-verified Resend webhook for bounce, complaint, and suppression events. Verified events upsert `suppressed_email`, and all Resend sends skip suppressed recipients with an `email.suppressed.skip` log entry instead of spending provider capacity or retrying bad addresses.
- **Transactional budget accounting** — Successful verification and password-reset sends are now written to `email_log` so the re-engagement budget sees the same daily pool that transactional email uses. Transactional sends remain immediate and are never blocked by the re-engagement budget.

## v0.32.1 - Monetization Surfacing & Pricing Clarity

**Status: Released**

Theme: the monetization leak is conversion, not engagement: premium exam value is under-surfaced, and pricing still reads like a recurring subscription even though paid plans are one-time, time-boxed passes. This patch focuses on top-of-funnel exposure and desire by moving premium exam paywalls back to the Start CTA, adding a Study Plan premium-exam entry point for non-teachers, and clarifying one-time pass pricing copy. Checkout conversion itself remains measured through the v0.31.2 funnel instrumentation. See `docs/product/ROADMAP.md` for full scope and anti-drift rules.

### Shipped

- **Premium-exam Start CTA paywall** — Free and Plus users can now open the premium exam setup surfaces before seeing an upgrade ask: Board Exam Mode opens its pre-flight setup from mode selection, Long Exam opens its full prestart page, and Professional Interview Practice opens its session setup. The Pro paywall now fires from the Begin/Unlock Start CTA for those modes, while backend gates remain the final enforcement boundary.
- **Study Plan premium exam CTA** — Non-teacher Study Plan detail pages now expose a profile-aware premium-exam action when the viewer profile maps to a premium exam mode: Student sees `Take the Long Exam`, Board Exam sees `Take the Board Exam` (Board Exam setup), and Professional sees `Start Interview Practice`. Eligibility is a ready Study Pack only (no pre-generated quiz required — these modes generate their own questions). The launch carries only `collectionId`; each prescreen scopes its additional-note picker to Study Pack-ready notes from that plan, preselects up to the existing per-exam cap, and still relies on the Thread 1 Start-CTA paywall/backend gates for Free and Plus users.
- **One-time pass pricing clarity** — Reframed paid pricing surfaces (pricing page, landing pricing sections, settings plans cards, paywall modal) to read as one-time, time-boxed passes instead of recurring subscriptions: prices render as duration (`₱X / 1 month`, `₱X / 3 months`, `₱X / 1 year`); intro pricing reads as a first-pass discount (`₱149 for your first 1-month pass · ₱179 after`) rather than `first month, then …/month`; cards carry one-time-payment / never-auto-charged / monthly-usage-refresh / library-permanence / desktop-and-mobile-web reassurances (shared constants in `plans.ts`); the duplicate Pro CTAs collapsed to one hero `Get Pro — ₱X / 3 months` (the 3-month exam pass) with a small `Also available: 1 month · 1 year` line; an FAQ "Will I be charged again?" was added. Billing-status copy for an active pass stays accurate. No billing, quota, pass-duration, price, or checkout mechanics changed.
- **Settings unified pass-length selector** — The Settings plans cards replace the `Monthly / Annual` toggle plus the separate exam-pass button with one 3-segment selector (`1 month · 3 months · 1 year`, segments shown by availability). The selected length drives a single Pro price line and one CTA (`Get Pro` for the 1-month pass; `Get Pro — ₱X / 3 months` / `… / 1 year` for the longer passes, keeping the long monthly-intro string out of the button). Pure UI; checkout cycles and mechanics unchanged.
- **Plan-launched exam prescreen polish** — When a premium exam is launched from a Study Plan (`collectionId` present), the Long Exam, Board Exam, and Interview Practice prescreens now route their back link to the originating plan (`/collections/{id}`) with the profile-aware label (`Study Plan` / `Review Set` / `Collection`) instead of "← Note", and the additional-notes picker reads "Add up to N more notes from this plan" instead of the stale "from this subject" copy. Non-plan launches are unchanged.
- **"Review first" exam advisory** — Launching a plan's premium exam when one or more exam-eligible (Study Pack-ready) plan notes haven't been practiced yet now surfaces a soft advisory modal ("Review before the exam? You haven't practiced X of N notes …") with `Review first` (stay on the plan) and `Start the exam anyway` (proceed) — a recommendation, not a block. If every eligible note has been practiced, the CTA routes straight through. Uses the existing per-note `lastSessionCompletedAt` signal; no persistence (re-evaluates each launch).
- **Pass selector polish** — Made the pass-length wording consistent (the 90-day exam pass now displays as **3 months**, so the selector reads `1 month · 3 months · 1 year` and the active-pass billing status reads `3-Month Pass`); fixed the savings badge that clashed green-on-blue when a segment was selected (now a state-aware on-blue treatment in both themes); and added a `Save N%` badge to the 3-month pass alongside the 1-year one, computed by a shared `passSavingsPct` helper (regular-monthly baseline, cycles derived from `durationDays`, guarded to > 0). Underlying durations, prices, and checkout cycles are unchanged — display only.

---

## v0.32.0 - Account & Communication Controls

**Status: Released**

Theme: give users real control over their account and the email we send them, and close the associated privacy/compliance gaps (GDPR right-to-erasure + portability; CAN-SPAM/GDPR one-click unsubscribe). Consolidates account and communication controls into a coherent, compliant surface — account deletion, data export, an email-preferences center (replacing the ad-hoc Study Reminders card), a weekly-summary opt-in (default OFF), and a tokenized unsubscribe link — plus right-sized email deliverability hardening. Mostly additive; account deletion needs careful transactional handling. See `docs/product/ROADMAP.md` for full scope and locked rules.

### Shipped

- **Email deliverability hardening** — `ResendEmailService` now retries on HTTP 429 (rate limit), honoring the provider's `Retry-After` header and otherwise backing off exponentially (max 3 attempts, capped at 5s), so a transactional email landing during a retention/marketing send burst isn't dropped. IO and non-429 failures keep their existing behavior; the `HttpClient` is now injected (testable). The primary capacity fix remains operational (upgrade the Resend tier).
- **Weekly summary opt-in** — Added a per-account `weeklySummaryRemindersEnabled` preference, defaulting OFF for new and existing users, so the Sunday `WEEKLY_SUMMARY` retention email only sends to verified active users who explicitly enable it in Study Reminders. Inactivity and weak-concept reminder preferences are unchanged.
- **Email Preferences center** — Settings now has a dedicated Email Preferences section for four optional categories: study reminders, weak-concept nudges, weekly summary, and opt-in Product news & tips via `marketingEmailsEnabled` (default OFF). Re-engagement campaign sends and eligible counts now require the marketing opt-in, the old reminder write path is renamed to `POST /auth/preferences/email-preferences`, and transactional account/billing email is shown as always sent rather than as a toggle.
- **Account deletion Phase 1** — Settings now lets users request account deletion with type-to-confirm `DELETE`, moving the account to `PENDING_DELETION`, recording `deleted_at`, revoking refresh tokens, and blocking normal login behind a reactivation path. Login now returns `ACCOUNT_PENDING_DELETION` for accounts in the 30-day grace window, and reactivation with valid password or Google credentials restores the account; irreversible purge/anonymization remains Phase 2.
- **Account deletion Phase 2** — Added the scheduled irreversible purge for accounts past the 30-day `PENDING_DELETION` grace window. The job seeds and uses the fixed deleted-user sentinel, reassigns public notes and retained financial records to that sentinel, terminates active subscriptions, deletes private/personal practice data, leaves `analytics_events` untouched, and isolates each user purge in its own transaction for retryable failures.
- **Tokenized email unsubscribe** — Optional retention and marketing emails now include a signed stateless unsubscribe link plus RFC 8058 `List-Unsubscribe` / `List-Unsubscribe-Post` headers. The unauthenticated one-click endpoint flips the matching Email Preferences flag idempotently without revealing account existence, and the public `/unsubscribe` confirmation page links users back to Settings to manage all email preferences.
- **Owner-only data export** — Added `GET /auth/account/export` and a Settings `Download my data` button that return the authenticated user's own account basics, notes, Study Packs, collections, and aggregate practice summary as one rate-limited JSON attachment. The export is synchronous, principal-scoped, includes empty arrays for empty accounts, and intentionally excludes secrets/tokens, analytics, and financial records.
- **Rate-limiter memory hotfix (prod OOM)** — The in-memory rate-limiter maps (`Auth`/`Ai`/`Ocr`) never evicted buckets, so unique keys accumulated forever; because `AuthRateLimitService` is keyed by client IP (and IP+email for login), an auth scan/credential-stuffing flood leaked heap until prod OOM'd and restarted. Each limiter now runs a scheduled 60s sweep that removes buckets whose window has fully elapsed, capping the maps at active keys. Shipped first as a hotfix on `main` (v0.31.2 line), then carried into this release via back-merge.

---

## v0.31.2 - Analytics Integrity & Funnel Visibility

**Status: Released**

Theme: the funnel and admin dashboards already exist and the core loop is healthy — the real gaps are **data integrity in analytics** and **visibility into retention and where monetization leaks**, not the product loop itself. This release fixes the recurring `analytics_events` FK violation that silently drops SIGNUP analytics, audits every analytics event against its fire site, and adds retention-cohort + upgrade→checkout drop-off instrumentation. Small, additive, mostly backend; no new product surface for users. See `docs/product/ROADMAP.md` for full scope and locked rules.

### Shipped

- **Analytics persistence integrity** — Analytics writes now publish an after-commit event before dispatching persistence to `analyticsTaskExecutor`, so signup lifecycle telemetry is recorded only after the signup transaction commits and rolled-back flows do not create phantom events. `analytics_events.user_id` no longer has a hard FK to `users(id)`, preventing referential timing from dropping `SIGNUP`, `SIGNUP_COMPLETED`, and `EMAIL_VERIFICATION_SENT` funnel events while preserving the nullable indexed column for reporting.
- **Analytics event audit** — Audited the then-current 70 `AnalyticsEventType` values against every fire site (incl. the `eventType=` / `analyticsEvent=` prop and `trackOnboardingEvent` patterns) and the admin funnel/summary queries. Fixed the one live drop: `QUIZ_SHARE_LINK_CREATED` / `_OPENED` / `_TOGGLED` are fired by the share-link feature but were missing from the backend enum, so each POST `/analytics/events` was rejected with HTTP 400 and the event lost — they are now in the enum and recorded. Removed the never-fired `ONBOARDING_V2_GOAL_SELECTED` (no goal-selection step emits it). The audited set is consistent across frontend union and backend enum, and every admin-referenced event is live.
- **Retention and checkout funnel visibility** — Admin Funnel now includes W1→W2 retention cohorts (first Study Pack activation, then any activity in the completed week-2 window) and an upgrade → checkout → paid conversion view. Added `CHECKOUT_INITIATED`, fired from successful Xendit checkout URL creation/reuse with only `planType` and `billingCycle` metadata, so admins can see whether upgrade intent drops before hosted checkout or after checkout. The checkout-step metric is forward-looking from deploy; historical checkout arrivals before this event are not backfilled.

---

## v0.31.1 - Adoptable Study Plans Discovery & Status

**Status: Released**

Theme: v0.31.0 shipped the adopt mechanics, but discovery is intentionally minimal — published plans surface only on the Dashboard, only the top match for the learner's course/program, and there is no plan-completion signal. This patch adds the additive follow-ups: an onboarding adopt surface, a way to browse all matching published plans, and an execution-status badge on the Study Plans list. It also tightens bulk-tool quota awareness. No new architecture. See `docs/product/ROADMAP.md` for full scope.

### Shipped

- **Browse published plans** — New `/collections/published` surface lists every published plan matched to the learner's course/program, fixing the Dashboard's top-match-only blind spot when several plans are published per track. Frontend-only (reuses `GET /collections/public?courseProgram=` + `GET /collections`, no new endpoint); each plan adopts via the shared `PublicStudyPlanCard` with Start/Continue per adoption state and the shared skipped-notice key. Reached via a prop-gated `See all N plans` link on the Dashboard card (shown only with 2+ matches; never on the onboarding card). Handles loading, error/retry, no-course-program guidance, and empty states.
- **Onboarding adopt surface** — The onboarding completion step (Step 5) now reuses the Dashboard's recommended-plan adopt card below `Continue Studying` / `Go to Dashboard`. For learners whose course/program has a published plan it offers one-tap adopt via the existing `listPublicStudyPlans` + `adoptStudyPlan` (no new endpoint); it self-hides for tracks with no published plan, leaving Step 5 unchanged. Completion is already persisted on Step 5, so adopting and navigating to the new collection doesn't lose onboarding state.
- **Bulk generate quota gate** — The Topics counter now folds the note-generation quota into its cap (`X / min(50, note generations left)`) with a helper line; `+ Add topic`, paste, and the Queue button are hard-capped at the remaining note generations, and a near-limit amber banner shows when ≤ 2 are left. When more topics are queued than Study Packs remain, a soft confirmation explains the extras stay as drafts (notes still get content) before proceeding. The backend also rejects stale-client over-quota batches at submit with a precise remove-count message before any work is queued.
- **Bulk import OCR banner** — Bulk import (Draft-only, no Study Pack) now surfaces remaining OCR/image-scan quota as an inline line, escalating to a near-limit banner when ≤ 2 are left, worded so DOCX/TXT/text-PDF imports (which don't consume OCR) aren't confused.
- **Shared near-limit banner** — `NearLimitBanner` is now generalized with a credit-noun prop so the same component covers Study Packs, note generations, and image scans without duplicated copy.
- **Study Plan list status badge** — `/collections` cards now show a subdued Not started / In progress / Completed execution-status badge derived from practiced notes vs. total notes, using the same completed-session signal as the detail rollup and staying off public browse and Dashboard plan cards.
- **Library search clear button** — The Library and Public Library search fields now show an inline clear (`×`) button when they contain text, matching the combobox clear affordance; clearing runs the normal search path (live filter / debounced URL sync) and is distinct from the filter panel's `Clear all`. Search inputs also use a 16px mobile font to avoid iOS focus-zoom.
- **Snappier Public Library search** — Public Library search no longer flashes the whole list to skeletons on every keystroke. It now uses stale-while-revalidate: the skeleton shows only on first load, and subsequent search/filter refetches keep the prior results (and search focus) on screen with a small `Searching…` indicator. The debounce dropped from 400ms to 250ms. URL sync is unchanged, so refresh-persistence, deep-linking, and sharing a pre-filtered view still work.
- **Bulk generate submit-button fix** — The bulk-generate submit button is now a static `Generate` instead of a dynamic `Queue N notes`; the live topic count already shows in the `X / cap` counter above the fields, removing a duplicated label that could briefly read `0` on a dev recompile. Topic inputs now use a 16px font on mobile to stop iOS Safari's focus-zoom while typing.
- **Study Plan per-note status + Exam Builder exclusion notice** — Study Plan detail rows now show a learner-facing execution status (`Needs Study Pack` → `Not started` → `Practiced`, plus transient generating/failed states) instead of the redundant `Study Pack ready` / `Quiz ready` hint. The quiz-readiness blocker moved to where teachers act on it: the Exam Builder now shows an amber `N of M notes excluded — no quiz generated yet` notice listing those notes instead of dropping them silently.

---

## v0.31.0 - Adoptable Study Plans

**Status: Released**

Theme: most learners don't assemble a study plan note-by-note — they want a ready-made, structured plan for their goal. This release lets a learner **adopt** a curated, ordered study plan in one tap (v1: admin-curated plans over already-public seeded notes, ALE/PNLE/LET). Adopt = snapshot copy (notes + linked Study Packs) into a personal Study Plan, then the existing learning loop. Curation, never AI-generated curriculum. See `docs/product/ROADMAP.md` for full scope and locked rules.

### Shipped

- **Adoptable study plans** — Admin-published `NoteCollection` records can now act as public, course/program-targeted study plans. Learners see a matching plan on Dashboard, tap `Start this plan`, and NoteLib snapshot-copies the plan's public notes plus linked Study Packs into a private personal collection with `sourcePlanId` lineage/idempotency. Adoption uses existing public-note copy behavior, skips unavailable items, bills no quota, and fires `STUDY_PLAN_ADOPTED`.
- **Study Plan Detail publish UX** — Edit / Publish settings / Delete now live in a single `⋯` context menu (mirroring Note Detail) instead of scattered buttons. Publishing moved into a dedicated modal with a constrained Course/Program **combobox** (no more freetext), a single `Publish` / `Unpublish` action, and a published/private indicator near the title. Admin detection is read reactively (SSR-safe), and the modal surfaces any still-private plan notes with a one-tap `Make N public` (plus per-row `Private` badges) so adopters never get a partial plan.
- **Combobox polish** — Fixed the public/private Library filter comboboxes that wiped the current selection on focus (you can now edit the selected value instead of retyping), and added an inline clear (`×`) button across the shared `SuggestionCombobox` and both Library filters.
- **Study Plan Detail UI polish** — Reworked the plan-detail header: `Build Exam` (renamed from `Build exam from this {singular}`) now sits at the bottom-left of the header card via a new `PageHeader` footer slot, leaving a clean badge + `⋯` cluster top-right; context-menu labels shortened (`Delete`); the published/private badge is now the one-tap publish control (gear affordance) instead of a `⋯` menu item. Redesigned the publish modal (status pill, in-flow combobox dropdown that no longer clips against the footer, single close affordance). Sheet modals now slide up from the bottom on mobile and scale-fade on desktop (`motion-sheet-panel`).

---

## v0.30.1 - Copy Flow Polish

**Status: Released**

Theme: reduce friction in copying notes from the Public Library. A small, frontend-only UX pass on labeling, the post-copy modal, and an editable-draft option — no backend, no new infrastructure.

### Shipped

- **Public Library copy flow polish** — the card action is now `Add to Library` (copy/library icon), replacing `Save`, which read as a bookmark next to the like/heart; the disabled copied-state reads `In Library`. The post-copy success modal now leads with a single `View Note` action (the Quick Review quick-action was removed — it under-utilized the note) and states the payoff ("The note and its Study Pack are now in your library — open it to read, quiz yourself, and track your progress."). Fixed a duplicate close-button bug in that modal (`AppModal` already renders its own close; the modal no longer passes a second one). On the public note detail page, the two copy options are relabeled `Add to Library` (note + Study Pack) and `Copy as editable draft` (`includeStudyPack=false` → editable Draft, edit before generating). Frontend-only: reuses the existing `copyNote` `includeStudyPack` param and the existing `PUBLIC_NOTE_COPY_CLICKED` / `PUBLIC_NOTE_COPIED` analytics — no backend, quota, enum, or endpoint changes.

---

## v0.30.0 - Readiness Signals

**Status: Released**

Theme: make Progress an honest, complete readiness picture for students and exam-takers. Before this release, only Quick Review, Challenge Quiz, and Adaptive Practice wrote `ConceptHealth` (the only thing Progress reads), so Long Exam, Board Exam, and Interview Practice could be ground for hours without moving Progress. This release records concept-level signals from those exam modes into `ConceptHealth` on session completion. The hard part is the domain→concept mapping (Long Exam reports LLM-tagged domain mastery; Progress is per-concept) — design it before writing. No new entity, no new quota, no new artifact; the work lives in `LongExamService` (Long + Board) and `InterviewPracticeService`, mirroring the existing `recordCorrectAnswers` contract. See `docs/product/ROADMAP.md` for full scope and locked rules.

### Shipped

- **Exam practice now feeds Progress** — Long Exam, Board Exam, and Interview Practice completions now record fully-correct concepts into `ConceptHealth` so Progress reflects exam-mode practice. Recording is constrained to concepts that exactly match a source Study Pack's `keyConcepts`, skips missing/unreadable source packs, and preserves the existing Progress read path.
- **Exam questions carry source key concepts** — Long Exam and Interview Practice generation now adds a separate per-question `keyConcept` field, schema-constrained to the source Study Pack's key concepts, while leaving the report-facing `concept` labels unchanged. Completion recording now prefers `keyConcept` and falls back to legacy `concept` for old sessions or pre-warmed pool questions, completing the Readiness Signals source-side fix without fuzzy matching, new storage, or Progress read changes.
- **Weakness signal completes Readiness Signals** — `ConceptHealth` now records `lastIncorrectAt` alongside `lastCorrectAt` and surfaces a derived struggling state when the latest signal is a miss. Quick Review, Challenge Quiz, Adaptive Practice, Long Exam, Board Exam, and Interview Practice all record missed concepts on normal completion, so Progress can distinguish concepts a learner keeps getting wrong from concepts they have never practiced.

---

## v0.29.1 - Bulk Generation Polish

**Status: Released**

Theme: follow-up polish on the v0.29.0 bulk-generation flow. Primary item: honest **partial-outcome reporting** — when a topic's content generation fails, no note row is created (correct — a note without content is not persisted), but the immediate `Queued N notes` toast over-promises before background work finishes. Surface the real outcome so the count stays honest. This uses one narrow terminal-outcome receipt exception; no batch-job entity, live progress table, or status enum is allowed. See `docs/product/ROADMAP.md` for full scope.

### Shipped

- **Bulk generation partial-outcome receipts** — `POST /notes/bulk-generate` now returns a `resultId` and the background worker writes one terminal, owner-scoped `bulk_generation_result` receipt at batch completion (including zero-failure and whole-batch-failure cases). `GET /notes/bulk-generate/results/{id}` is owner-scoped, read-once, deletes after returning, and 24h cleanup removes unread receipts. The Library keeps the immediate `Queued N notes` toast, then after the existing auto-refresh settles it reads the receipt and shows a dismissible banner listing the exact failed topic strings with `Retry these`, which pre-fills `/library/bulk-generate` with the failed topics plus subject/course/audience/public context. Content-first behavior is unchanged: failed content generation still creates no note row, and post-create Study Pack failures are not counted as failed topics.
- **Bulk generation opened to all users** — removed the ADMIN-only gates from the Library Create entry, `/library/bulk-generate` route, and bulk result endpoints while preserving ADMIN quota bypass and existing profile-aware metadata resolution. Non-admin batches now classify no-row failures as either generation failures or note-generation quota blocks; the Library banner offers `Retry these` only for genuine generation failures and uses plan-aware upgrade CTAs for quota-blocked topics. The bulk page shows remaining note-generation quota when available, Note Create links to bulk generation from the single-topic panel, and Help adds a deep-linkable `/help#bulk-generate` guide.

---

## v0.29.0 - Bulk Generation

**Status: Released**

Theme: kill the one-note-at-a-time tax on seeding study content. An admin enters one subject and a list of topics, and the system generates a note **and** its Study Pack per topic, unattended. Built admin-first to seed our exam-prep buckets, but architected as a normal Library capability behind a role gate — opening it to all users later is a gate-flip, not a rebuild. No new job/progress infrastructure: each topic is generated content-first into a real note that then runs Study-Pack-gen on the existing executors and resolves `GENERATING → READY` via the status field the Library already carries. The non-admin path reuses the existing quota-enforcing entry points (ADMIN bypasses) so opening it to all users is a gate-flip. See `docs/product/ROADMAP.md` for full scope and anti-drift rules.

### Shipped

- **Admin Bulk Generation** — Added an ADMIN-only, Note-Create-aligned Library flow for entering one subject plus a discrete topic list, with a compact profile-aware metadata grid and accessible Public toggle. Each topic immediately queues throttled note-content and Study Pack generation on the existing executor; bulk-created notes keep the batch subject while accepting AI-refined titles and tags, optionally become public, isolate individual failures, and bypass ADMIN quota/rate-limit usage without changing single-note enforcement or adding batch-job infrastructure. Queuing redirects to the Library with a confirmation toast (`Queued N notes…`) so the batch is visibly received where the notes will appear.
- **Bulk-generated notes animate in** — rows that arrive after the initial Library load (generated notes surfaced by the auto-refresh poller) now fade/slide in via the shared `motion-fade-enter` entrance instead of popping in abruptly. The initial list does not animate wholesale, and the entrance respects `prefers-reduced-motion`.
- **Bulk topics: paste-to-split** — Pasting a multi-line list into a bulk topic row now splits it into one topic per line instead of dumping everything into a single field. Splits on newlines only (CRLF-aware, never on commas), strips leading list markers (bullets and numbered prefixes that require trailing whitespace, so `.NET` and `1.5 inch` survive), clamps each line to the topic max, preserves source order, fills the pasted-into row when empty, and caps at the topic max with a visible notice rather than silently dropping. Stripping is paste-time only — typed input is untouched. Logic lives in a pure, unit-tested `parsePastedTopics`.
- **Generation-context correctness** — Static note and Study Pack content now calibrates depth, vocabulary, terminology, examples, and embedded Quick Review material from the shared note-first Course / Program instead of per-user learner level. Quiz/exam prompts and the exam-question pool retain learner level for taker-specific difficulty; bulk generation no longer asks admins for a learner level and uses the owner's profile level only as best-effort pool context.
- **Library auto-refreshes while generation is in flight** — after a bulk queue, generated notes now appear in the Library on their own instead of requiring a manual refresh (the toast already promised this). A silent poller (re-fetches the note list only — no skeleton flicker, no pagination reset) runs while any note is `GENERATING` or the list is still growing, then stops after a generous quiet window + hard-cap backstop. The quiet window is sized to exceed the inter-topic gap of throttled sequential bulk fan-out so a large batch is not truncated mid-way, with a short initial grace for the redirect moment when no rows exist yet. Same poller fixes the identical single-note generation gap. No backend batch/progress infrastructure added — this is automatic client-side load-on-refresh.
- **Copy CTA landing matches the verb** — `Copy Study Pack` on public notes now lands on the copied note's **detail page** (`redirectTarget="generate"`) instead of auto-launching Quick Review, so the viewer sees the full Study Pack and can choose their next action — including Challenge Quiz. `Quiz yourself on this note` still honors its quiz promise and auto-launches Quick Review; `Copy to My Library` still lands on detail. Surfaces note value and the Challenge Quiz entry point to the public-note conversion cohort, who previously blew straight past note detail into a quiz.
- **Post-session Challenge promotion loosened (5/5 → 4/5)** — The post-session next-step now promotes `Take a Challenge` as the primary action after a strong-majority Quick Review (at most one missed concept), not only a perfect score. When there is a single miss, `Retry Incorrect Questions` is preserved as a secondary action so the missed question is not lost; two or more misses still lead with retry. Targets Challenge Quiz adoption for the public-note conversion cohort, who land directly in Quick Review (bypassing note detail) and only see this surface.
- **Profile-type integrity + onboarding enforcement** — Completed-but-null legacy users are now routed back to `/onboarding` for a focused profile-type prompt only, while truly incomplete users keep the full onboarding flow. Content-creating and generation mutations (note create, note-from-topic, Study Pack generation, note copy, bulk generation, batch import) now enforce profile setup server-side through `OnboardingGuardService` and return a named 403 `ONBOARDING_REQUIRED` / `COMPLETE_PROFILE_TYPE` error. The guard fires only for the legacy completed-but-null cohort (`profileType == null && onboardingCompletedAt != null`); users mid-onboarding and copy-on-signup are exempt (both persist/complete profile setup after generating), so the activation funnel is never blocked. No backfill or silent defaulting was added; read/auth/onboarding/recovery endpoints remain reachable so users can fix their setup.

---

## v0.28.0 - Feature Discoverability & Activation

**Status: Released**

Theme: close the gap between strong signup conversion and weak feature activation. Underused surfaces — quiz-session export, Challenge Quiz, and the new Study Plans — get surfaced *in flow* through systems we already have (one-time contextual tips, the Dashboard recommendation), not new help pages. This is an activation problem, not a docs problem: quiz-session export is already documented in Help and still goes unused. Reuse `GuidanceTip` / `pickActiveGuidance` — no new tips framework, no new infrastructure. See `docs/product/ROADMAP.md` for full scope and anti-drift rules.

### Shipped

- **Mode-aware post-session progression** — `PostSessionNextStepService` now derives the just-completed mode from the latest saved session and bases weak-area routing on genuine weakness only: reviewed-and-decayed concepts plus actual misses, excluding never-reviewed concepts. Strong Quick Reviews now advance primarily to Challenge Quiz while keeping genuine weak-area practice as a secondary action; Quick Review misses prioritize retry with Challenge still reachable, Challenge Quiz keeps diagnostic weak-area practice primary, and Adaptive Practice always steps up to Challenge instead of looping back into itself. Long Exam and Board Exam flows remain unchanged.
- **Referrer-aware note back link from collections** — opening a note from a Study Plan / Review Set / Lesson Plan / Collection now returns to that collection (profile-aware label via `getCollectionLabels`) instead of always falling back to Library. The collection detail page passes a `ref=/collections/{id}` param and note detail derives the back-link href + label from it; bare `/library` referrers and direct visits are unchanged.
- **Study Plan progress rollup** — collection detail responses now aggregate existing per-note readiness and completed-practice signals into `totalNotes`, `notesWithStudyPack`, and `notesPracticed`, with `lastSessionCompletedAt` included on each item through the same batched history source used by Library. The detail page shows a compact “Study Packs ready · practiced” summary and practiced-progress bar, including a neutral empty state; the rollup is read-only, detail-only, profile-agnostic, and adds no AI, persistence, entity, or quota.
- **Actionable Study Plan queue and weak areas** — collection detail now batch-loads plan-gated ConceptHealth due signals across its Study Packs and returns per-note due counts plus up to three ordered concept names, degrading to empty data for Free users, missing packs, or lookup failures. A frontend-only `Next in this plan` card follows saved order through generate -> study -> entitled due-review -> caught-up phases and preserves the collection referrer on note links; the plan remains read-only and does not duplicate Progress subject mastery, milestones, goals, or streaks.
- **Smarter Continue Studying activation recommendation** — added `SUGGESTED_CHALLENGE` between weak-score review and passive recently-opened/created fallbacks. Eligible idle Student and Board Exam users with quiz-ready material, no prior Challenge Quiz session, and remaining monthly Challenge Quiz quota now see a contextual `Try Challenge Quiz` action in the existing card. Resume and low-score priorities remain unchanged, Teacher/Professional and quota-blocked users fall through safely, and dashboard recommendation impressions/clicks are tracked through `DASHBOARD_RECOMMENDATION_SHOWN` / `DASHBOARD_RECOMMENDATION_CTA_CLICKED` with reason and resume-type metadata.
- **Study Plans & Collections Help topic** — added a universal, profile-aware Help guide (`study-plans` card, deep-linkable at `/help#study-plans`) explaining what a {Study Plan / Review Set / Lesson Plan / Collection} is (a playlist over existing notes, no AI synthesis, no new quota), what you can do (group, reorder, label, multi-membership, safe delete), the profile-aware terminal action (Teacher build-exam vs. study/generate per note), and how to create one. Labels resolve through `getCollectionLabels`; closes the documented Help gap for v0.27.0's Collections feature.
- **Activation nudges (track 1) — contextual tips + funnel instrumentation** — added two new one-time tips at their moment of relevance: an **Export** tip on the quiz session review screen ("export this review as a PDF…"), and a profile-aware **Study Plans** grouping tip in the Library (`library-study-plan-grouping`, non-teacher, ≥3 notes, with a `Create {Study Plan/Review Set/Collection}` CTA). The Library's existing tips were consolidated into a single `pickActiveGuidance` selection so nudges never stack, with Study Plan grouping prioritized for non-teachers. `GuidanceTip` gained an **opt-in `trackAnalytics`** flag that fires `GUIDANCE_TIP_SHOWN` (impression) and `GUIDANCE_TIP_CTA_CLICKED`, and the review screen fires `QUIZ_REVIEW_EXPORTED` on a successful export — completing an impression → click → use funnel via the existing `AnalyticsEventType` enum (Java + frontend). No Challenge Quiz tip was added: the Quick Review completion screen already drives it through `PostSessionNextStep` plus a fallback CTA, so a one-time tip would be redundant.

---

## v0.27.0 - Material Import & Collections

**Status: Released**

Theme: lower the cold-start barrier for getting existing material *into* NoteLib (bulk multi-file import) and let any learner group notes into a reusable, ordered **collection**. Triggered by preparing an effortless teacher path before we recruit teachers, but built profile-agnostic at the core — students, board reviewers, and professionals get the same import-and-organize speed. The teacher payoff (combined exam packet + shareable links) is a profile-aware terminal action on a universal `NoteCollection` spine, not a separate system. A collection is a playlist over existing notes — no collection-level AI synthesis, no new quota. See `docs/product/ROADMAP.md` for full scope, the profile-label table, and anti-drift rules.

### Shipped

- **Track 1 backend — Bulk material import API** — added `POST /notes/import-batch` for profile-agnostic multi-file import. The endpoint reuses the existing per-file extraction pipeline, creates one owned `DRAFT` note per successful file, reports per-file failures without rolling back earlier successes, enforces a configurable max-files cap, and fires `NOTES_BULK_IMPORTED` once per successful batch. It never triggers Study Pack generation, LLM calls, or a new quota category.
- **Track 1 frontend — Multi-file uploader UI** — added the guarded `/notes/import` workspace page and a universal Library header entry point. Users can select up to 20 supported files, submit one batch request, review per-file created/failed results and low-confidence warnings, retry whole-request failures without losing their selection, and optionally add the created drafts to an existing or new profile-labeled collection. The flow never auto-generates Study Packs or auto-assigns collections.
- **Track 2 backend — Note Collections API** — added the universal, owner-private `NoteCollection` entity and ordered item join table, plus CRUD endpoints under `/collections`. Collections group only the caller's own existing notes, allow one note in multiple collections, dedupe repeats within a collection, support reorder/relabel, and delete only collection/item rows. No profile gate, profile-aware labels, LLM calls, or quota logic were added.
- **Track 2 frontend core — Collections UI** — added the profile-aware Collections / Study Plans / Review Sets / Lesson Plans nav entry, `/collections` list page, and `/collections/[id]` detail page. Users can create, edit, delete, add notes through an in-detail picker, remove notes, relabel items, and reorder with drag-and-drop plus Move up/down fallback.
- **Track 2 integrations — Collections entry/exit points** — added universal Library multi-select `Add to {collection}` for any note readiness, wired the Teacher Lesson Plan terminal CTA into Exam Builder with quiz-ready filtering and disabled empty-state copy, and added server-side `COLLECTION_CREATED` analytics on collection create only.
- **Track 3 — Label-driven Teacher Exam Builder handoff** — completed the frontend-only Lesson Plan terminal path by passing `collectionId` into Exam Builder and pre-seeding editable sections from distinct trimmed collection item labels. Quiz-ready notes retain collection order, unlabeled notes share one trailing default section, and non-ready notes are excluded without creating empty sections. Existing Teacher/Admin DOCX, anti-cheating versions, shareable quiz links, quotas, and generation behavior remain unchanged.
- **Track 4 — Profile-aware first-run / activation** — replaced the generic zero-notes dashboard empty state with a profile-aware `DashboardEmpty` that teaches the loop per profile (teacher: import material → generate → group into a Lesson Plan to export/share; student/board/professional: import or create → generate → study/quiz). Every variant surfaces both the `Import files` and `Create a note` entry points; the collection term resolves through `lib/collection-labels.ts`. No new tips framework.
- **IA refinement — Library Create split-button & import placement** — replaced the Library header's `Create Note` + standalone `Select` with one split button: primary **New Note** (one click) plus a caret menu (`Note` / `Import files` / `{Study Plan}`). The standalone `Select` button is gone; **`{Study Plan}` enters Library selection mode** ("Pick notes for your new plan" — filter + multi-select → **Create {plan}**, empty allowed), and teachers reach **Build exam** from the same selection. The Study Plan detail's "Add notes" picker still handles adding to an existing plan. Bulk import moved off the Library header into the Create-note flow's `Import notes` panel ("Bulk import multiple files"); the `/notes/import` back link is referrer-aware (`from=new` → New Note, default → Library). `COLLECTION_CREATED` is unaffected.
- **IA refinement — Progress goal card clarity** — the Progress goal summary was already read-only (Profile is the single Study Focus/Goal editor); aligned its naming with Profile (subject-focus header `FOCUS Goal` → `Study Focus`) and made the edit affordance explicit (`Change goal` → `Edit in Profile`). Documented that Study Plans (durable organizer) and saved library filters (transient quick lens) are distinct and both intentionally kept.
- **Polish — Create CTA sizing & Send Feedback placement** — slimmed the Library `New Note` split button to the compact (`sm`) size to match the app's other CTAs. Fixed a regression where Send Feedback appeared as a floating widget on non-learning routes (Library, Study Plans, Dashboard…): feedback now lives in the header on every page (auto-hidden only in distraction-free exam focus, where the header itself is hidden), matching production. Removed the route-split (`isCoreLearningRoute`) and the floating widget; locked the header placement with an updated app-shell test.

### Deferred

- Lesson-plan / syllabus document parsing as quiz source (scaffold references content, doesn't contain it — weak quiz source).
- Student/board/professional multi-note practice terminal CTA from collections; the existing Long Exam is same-subject scoped and meters quota per source note, which does not fit cross-subject, mixed-readiness collections without a separate practice-flow design.
- Collection-level AI synthesis (Option B), public/shareable collections, per-profile structured presets beyond Exam Builder's existing ones.

---

## v0.26.1 - Guidance System

**Status: Released**

Theme: make NoteLib's most useful — but least self-explanatory — features teach themselves. Build a reusable in-app guidance mechanism (deep-linkable Help guides + an inline "gist + How this works →" pattern), then apply it to the two highest-pain gaps: the Progress / Study Focus / Milestones loop and the Exam Hubs. Reference material lives where the feature lives; the Help guide is the depth path, not the only explanation.

### Shipped

**Mechanism — reusable guidance primitives**
- **Deep-linkable Help guides** — Help page opens a specific guide from a URL hash (e.g. `/help#progress-focus`); reads `location.hash` on mount and `hashchange`, syncs via `history.replaceState`. Hash (not query param) keeps `/help` statically prerendered, avoiding a Next.js `useSearchParams` Suspense build de-opt.
- **`HelpLink` inline reference link** — `components/ui/help-link.tsx`: a small, persistent "How this works →" link co-located with complex features, paired with a one-sentence inline gist. Reference-grade (always present, re-readable), distinct from the one-time dismissible `GuidanceTip` (reserved for "this feature exists" discovery nudges).

**Progress & Study Focus guide**
- New Help guide (`progress-focus`) covering the three concept-mastery states (mastered / due for review / not started), spaced-repetition decay, the six goal milestones, Study Focus (profile-type-aware: STUDENT = "Study Focus", BOARD_EXAM = "Exam Focus", TEACHER has none), goal vs. subject mutual exclusion, and the honest "starting a new term" answer (no reset button; new subjects start fresh, kept subjects carry mastery forward).
- Inline gist + "How milestones work →" deep-link on the Progress Goal Milestones card.
- Inline "How this works →" deep-link on the Profile Study Focus / Exam Focus section.

**Exam Hubs guide**
- New Help guide (`exam-hubs`) covering what `/exam/ale`, `/exam/pnle`, `/exam/let` are, how they curate existing public notes, and the three ways to reach them (nav, public-note callout, exam goal) — previously zero Help coverage.

### Deferred to v0.26.2+

- Discovery tips / guidance coverage for the remaining surfaces (quiz modes, study packs, export & sharing, exam-cycle pass, post-quiz nudges). The mechanism built here extends to them in later versions; this release intentionally scopes to the two highest-pain gaps.

---

## v0.26.0 - Exam Depth

**Status: Released**

Theme: expand the exam capture surface with wave-2 exam hubs, deepen the goal progression loop with mastery-threshold milestones, give board exam takers a pricing commitment that matches how they prep — a 90-day exam-cycle pass — and replace broad study-focus picks with subject-level focus.

### Shipped

**Track 1 — Exam Hub Surface**
- **`/exam` index redesign** — restyled exam hub index cards to the Help page card pattern: icon badge (PenTool/Heart/GraduationCap), `CardTitle` + `CardDescription`, "Browse [Exam] notes" + ArrowRight CTA with hover translate; replaced inline back link with `BackLink` component.
- **Progress page link fix** — `NextStudyCard` now resolves exam hub slug via `getExamSlugForCourseProgram()` as a fallback when `goalType !== "EXAM"`, so Architecture/Nursing/Education goals set from the Profile chip picker correctly route to `/exam/ale` etc. instead of the filtered public library.
- **Public note contextual callout** — when a public note's `courseProgram` maps to an exam hub, a compact callout strip appears after the practice mode teaser ("Preparing for the ALE? Browse curated notes, summaries, and practice quizzes. → Browse ALE hub"); the "View all →" link in the "More notes" section also upgrades to the exam hub destination. Replaces the originally planned landing page section (most traffic arrives via direct note links, not the homepage).
- **Exam Hubs nav link** — added "Exam Hubs" to the public navbar (`PUBLIC_NAV`), placed between "Public Library" and "Learn", linking to `/exam`. Replaces the footer-only placement for discoverability.

**Track 2 — Mastery-Threshold Milestones**
- **Goal milestone markers** — six fixed ConceptHealth-derived checkpoints on the `/progress` goal view (between goal summary and next-study card): first concept mastered, 25%, all reviewed, 50%, 70%, and all mastered. Added `notPracticedConcepts` to `GoalSummaryResponse` DTO; milestones computed client-side, no new endpoint.

**Track 3 — Exam-Cycle Pass**
- **90-day Pro exam-cycle pass** — added `EXAM_CYCLE` as a config-driven billing cycle, exposing a PH Pro-only 90-day pass at ₱599 while keeping Plus and non-PH exam-cycle pricing inactive by default. Settings and shared pricing cards show `Go Pro — 90-Day Exam Pass` only when live backend pricing marks the cycle available; checkout still uses the existing prepaid Xendit flow and monthly quota reset model.

**Track 4 — Subject-Level Focus & Profile-Aware "What's Next"**
- **Subject multi-select Study Focus** — replaces the course-program chip picker with AI-inferred subject chips from the user's own notes (`/subjects?scope=mine`); multi-select saves to a new `focusSubjects text[]` column (V71 migration); mutual exclusion with `studyGoal` (exam hub intent still sets `studyGoal` unchanged).
- **Profile-type-adaptive framing** — Study Focus section hidden for TEACHER profile; BOARD_EXAM shows "Exam Focus" header; STUDENT shows "Study Focus — subjects you're preparing for this term"; PARENT/PROFESSIONAL shows generic copy.
- **`SUBJECT_FOCUS` goal type** — when `studyGoal` is null and `focusSubjects` is non-empty, `ProgressReportService` computes a combined goal summary over the selected subjects; `goalName` is single subject or "N subjects in focus"; `NextStudyCard` routes to `/public/library?subject=weakestGoalSubject`.
- **Actionable Progress empty state** — when no goal is set but subject mastery data exists, the Progress page shows the weakest 5 subjects as one-click chips linking to Profile settings, replacing the passive "No study focus set" link.

### Planned

**Track 1 — Exam Hub Surface (remaining)**
- **Wave-2 exam hubs** — extend `/exam/[slug]` to CPALE, Engineering (Civil/Electrical/Mechanical), Pharmacy, Physical Therapy, and CSE. Gate: 20+ public notes per exam required before launch; High School/SHS excluded (not licensure exams). Deferred — no wave-2 candidate currently meets the threshold.

---

## v0.25.1 - Polish & Quick Review Fixes

**Status: Released**

Theme: targeted polish pass for issues observed in Quick Review and the private library after v0.25.0 shipped.

### Polish & Fixes

- **Theme picker z-index** — raised `<header>` from `z-40` to `z-50` so the mobile theme-picker dropdown always layers above page body content
- **Library More Filters "Done" → "Apply"** — standardized the private-library More Filters modal close-button label to "Apply" (matches public library)
- **Quick Review multi-select Submit flow** — multi-select questions now always show "Submit" as the CTA regardless of position; clicking Submit reveals the answer highlight and explanation; button then becomes "Next" / "Finish Quick Review" / "Finish Retry"
- **Multi-select answer-review label mismatch** — fixed the choice shuffle seed in `QuizAnswerReview` to match `QuizChoiceList`; the "Correct answer: C and D" summary text now names the same letters shown in the rendered choice list
- **Public Library filter reorder** — More Filters modal reordered to For → Course/Program → Subjects → Tags → Source so users can narrow by program before drilling into subjects/tags
- **Public Library cascading Course/Program filter** — selecting a Course/Program in the More Filters modal narrows the available Subjects and Tags to only those associated with notes in that program; stale subject/tag drafts auto-clear when a program is selected
- **Study Focus chip cap** — profile Study Focus section now shows at most 8 program chips with a "Show N more" toggle; prevents the chip grid from overflowing on accounts with many note programs or on mobile
- **Quiz question newline rendering** — `QuizQuestionText` now renders `\n` characters in question text as visible line breaks in both the Statement N: path and the plain-text fallback; fixes statement-list questions that appeared as one continuous run-on line

---

## v0.25.0 - Exam Capture & Goal Setting

**Status: Released**

Theme: convert the marketing traffic from exam communities (PNLE, LET, ALE, …) into signed-up, activated learners with exam-specific landing pages, and give every new learner a goal that turns the progress report into a destination. Two tracks, one funnel — exam page → signup → goal → progress → back to community notes. App stays universal; an exam is a curated view over existing public notes (no new entity), and a goal is mastery-derived (no generated curriculum). See `docs/product/ROADMAP.md` for full scope and open kickoff questions.

### ✅ Shipped

- **Exam hub pages** — added public, server-rendered exam hubs for `/exam/ale`, `/exam/pnle`, and `/exam/let`, curated from existing public notes through a frontend `courseProgram` alias map. Each hub has SEO metadata, CollectionPage structured data, featured/popular/recent discovery sections, an empty state, anonymous signup CTA with persisted `exam` intent, authenticated Public Library CTA, and `EXAM_HUB_VIEWED` / `EXAM_HUB_CTA_CLICKED` analytics events. Added the static `/exam` index and a public-footer entry point.
- **Exam goal setting and progress framing** — added a confirmed exam goal field on users plus `PUT /users/profile/goal`, with dashboard suggestions sourced from the exam-intent cookie first and `courseProgram` fallback second. `/progress` now includes an exam goal summary, next-best-study card linked back to the relevant exam hub, and an exam-hub callout for users with progress but no goal; the Dashboard "View full progress report" link is always visible, even before weak concepts exist.
- **Universal study goal** — goal setting now works for all profile types; students can pick any of their note subjects as a study focus, not just board-exam slugs; progress report shows mastery toward any `courseProgram` goal; subject chip picker on `/progress` and a dashboard banner for non-exam profiles.
- **Post-quiz goal nudge** — after any off-goal quiz session, a `GoalNudgeCard` appears below the primary next-step CTA showing goal mastery progress and linking to `/progress` (Quick Review, Challenge Quiz, Adaptive Practice, Board Exam Mode; Long Exam excluded from v1).
- **Dashboard goal card** — persistent card replaces the empty slot when a study goal is set; shows goal name, mastery %, and CTA to browse notes or view full progress; `GoalPromptBanner` remains unchanged for the no-goal state.

### 🔲 Pending

- **Track 1 — Exam Capture:** deferred curation/admin normalization for future exam waves.
- **Track 2 — Goal + Milestones:** mastery-threshold milestone depth beyond the shipped exam goal summary and next-study suggestion.

### Polish & Fixes

- **Progress page nav + header** — added Progress to the MAIN sidebar nav (`BarChart2` icon); fixed `ProgressHeader` to use `BackLink` + `PageHeader` (eyebrow/card pattern matching Library and Profile); link now reads "Dashboard" not "Back to Dashboard"
- **"View progress report" link placement** — moved from inside the Weak Concepts card to inline with the section header (right-aligned, blue), so it signals a full mastery report — not just weak-concept detail
- **Back link label consistency** — corrected four `BackLink` usages across long-exam, adaptive-practice, and quick-review pages from `label="Back to Note"` to `label="Note"` per AGENTS.md rule
- **UI standards context file** — added `docs/ui-standards.md` documenting page header card pattern, back link rules, "view all" placement, and nav grouping for Codex and Claude anti-drift; fixed stale "muted text" description in `docs/features/navigation.md`
- **Subject card priority hierarchy** — added mastery-keyed left border accent (gray = not started, rose < 40%, amber 40–60%, blue ≥ 60%) and matching mastery % text color to each subject card; added "Concept Mastery · N subjects" section header above the card grid for structural grouping
- **Exam hub build fix** — `fetchPublicNotes` now catches network-level errors (ECONNREFUSED) and returns an empty array instead of crashing; exam hub pages prerender to their empty state at build time and populate via ISR on first live request
- **Rename `exam_goal` → `study_goal`** — DB column, entity field, DTOs, service params, analytics event names (`STUDY_GOAL_SET` / `STUDY_GOAL_DISMISSED`), and all frontend types/functions renamed for clarity; V70 migration updated in-place (not yet applied)
- **Goal UX consolidation** — goal setting now lives exclusively in Profile settings (Study Focus card with inline chip picker) and the Dashboard banner; Progress page is read-only mastery view with a "Change goal" link to `/profile#study-focus`; removed `SetGoalCallout` from Progress to eliminate the circular "set here → go there" loop; fixed exam hub CTA incorrectly appearing for non-BOARD_EXAM profiles whose `courseProgram` happened to map to an exam slug
- **Dashboard goal card UX refinements** — card now shows `weakestGoalSubject` as a "Focus: [subject]" hint when available (requires `GoalNudgeResponse.weakestGoalSubject` from `buildGoalNudge()`); replaced the two inline browse/progress links with a single "View goal progress" outline button to `/progress`
- **Progress page layout and sort** — subject mastery cards now sorted ascending by mastery % (weakest first) so the most actionable subjects surface at the top; "What to study next" card moved above the subject list (below the goal summary header) so the primary action is immediately visible without scrolling
- **Library visibility filter** — added a "Visibility" section (All / Public / Private chips) to the More Filters modal so users can quickly isolate their private or public notes; fully client-side, persisted in URL params and saved filter state
- **Library visibility filter compilation fix** — fixed three exhaustive-deps violations in the visibility filter hooks (`setVisibleCount` effect, `handleNoteNavigate` callback, inline `Object.keys()` cast in JSX) that caused Turbopack to hang on `/library`; also ensures navigating to a note now preserves the active visibility filter in the return URL

---

## v0.24.1 - Content Moderation Hotfix

**Status: Released**

Theme: patch false positives in the content moderation dictionary that blocked legitimate academic notes — removing terms with common proper-name, scientific, or engineering uses from the banned-word lists.

### ✅ Shipped

- **Content moderation false positive fix** — removed `dick`, `cock`, `bitch`, `tranny`, and `faggot` from the English banned-word list; all five have mainstream academic uses (surname, rooster/engineering tap, female dog, automotive transmission, bundle of sticks). Removed `hayop`/`hayup` (Filipino for "animal") and `puke` (English word misplaced in the Filipino file) from the Filipino list. Removed the dead `putang ina` two-word entry that could never match (tokenizer splits on spaces).

---

## v0.24.0 - Guided Learning

**Status: Released**

Theme: turn NoteLib into a study companion that shows direction and progress — close the learning loop (study → assess → see gaps → targeted next action) by surfacing the concept-mastery data the app already tracks, and give learners a sense of where they are and what to do next.

### ✅ Shipped

- **Free Adaptive Practice allowance** — Free users now get `3` Adaptive Practice sessions per month, removing the paywall that broke the weak-area learning loop after Quick Review or Challenge Quiz. Backend plan limits, feature gates, plan comparison copy, paywall messaging, and mode-access surfaces now treat Adaptive Practice as quota-gated instead of Pro-only.
- **Quick Review and Challenge Quiz ConceptHealth feeder** — Quick Review and Challenge Quiz, including Board Exam Mode, now feed ConceptHealth on completion; the mastery/due-concept spine and dashboard Today's Focus now reflect all practice modes, not just Adaptive Practice.
- **ConceptHealth-driven post-session next-step handoff** — after Quick Review, Challenge Quiz (including Board Exam Mode), and Adaptive Practice, result screens resolve a spaced-repetition-aware next action from the concept mastery spine rather than client-side wrong-answer lists; the handoff is unified across all four completion surfaces.
- **"My Progress" report** — per-subject ConceptHealth mastery view at `/progress`; shows mastered, due, and not-yet-practiced concept counts per subject across all owned Study Packs, with the dashboard Focus Areas card linking to the full report.
- **Full Study Pack copying** — copying a public note now includes the generated summary, key concepts, and quiz; the copied note arrives as Study Pack Ready when the source has a Study Pack. A "Copy note only" option is available for users who want a blank start.
- **Study Pack regeneration** — users can now explicitly regenerate the Study Pack on any of their own notes; note content is editable again, and regeneration updates the existing pack in-place while preserving session history.
- **Guardian demand test** — "For Parents & Guardians — Coming Soon" section on the landing page; clicking "I'm interested" fires a `GUARDIAN_INTEREST` analytics event. Signal-only — no email collection, no Guardian flow. Build/no-build threshold: 50 `GUARDIAN_INTEREST` events before v0.25.0 kickoff.

### 🔲 Pending

---

## v0.23.1 - Quiz Format Fix

**Status: Released**

Theme: patch a quiz content-correctness bug where assertion-style "Which is correct?" questions were generated as TRUE_FALSE with True/False choices — a mismatch that undermines trust. Fixed at the shared generation + validation layer so it cannot recur in any quiz mode.

### ✅ Shipped

- **Fix True/False format mismatch on "which is correct?" questions** — strengthened shared Study Pack, Challenge, Adaptive Practice, Long Exam, Board Exam, and Teacher quiz prompts so `TRUE_FALSE` is only used for one declarative statement judged true/false. Assertion-style "Which is correct?", "Which of the following...", and multi-statement `Statement 1` / `Statement 2` items must now be `MCQ` with four assertion choices. Added shared backend validation that rejects malformed `TRUE_FALSE` + MCQ-intent stems and retries generation, plus admin-only `POST /admin/study-packs/repair-malformed-quizzes` to regenerate only affected `study_packs.quiz` values across all owners while preserving summaries and key concepts.

### 🔲 Pending

---

## v0.23.0 - From Readers to Learners

**Status: Released**

Theme: convert the public library's anonymous reading traffic into signed-up, activated users — turning the ~2,149 public-note views (≈0.05% account capture today) into the registered base the healthy middle funnel is starved of.

### ✅ Shipped

- **Quiz-first public-note conversion CTA** — the public note detail page's conversion CTA now leads with "Quiz yourself on this note" (routing through the existing copy → instant Quick Review flow) instead of "Create your own Study Pack"; the mini-quiz completion screen leads with the same quiz-first CTA and demotes "Copy to My Library" to secondary. The page stays note-first (content/SEO unchanged) — only the CTA framing changed. Added a `PUBLIC_NOTE_QUIZ_YOURSELF_CLICKED` analytics event to measure the new CTA, and parameterized `PublicSeoCopyCta` (`action`, `analyticsEvent`, `authModalTitle/Body`, `variant`). Most of the underlying capture flow (copy-intent cookie, signup `redirect`, auto-copy, auto-generate, auto-start Quick Review) already existed; Google OAuth is popup-based so no redirect-intent preservation work was needed.
- **Dynamic public-note share cards** — public note detail pages now generate a per-note Open Graph image (`opengraph-image.tsx`) showing the note title, subject, and `{N} practice questions · Quiz yourself in seconds` instead of the generic logo card. Makes shared links (especially in Facebook study groups) far more clickable. The page's `generateMetadata` drops the static default image so the file-convention card wins, and Twitter falls back to the same dynamic `og:image`. (Article JSON-LD structured data was already present on these pages.)
- **Free note generation raised 5 → 10/month** — bumped `freeMonthlyNoteGenerationLimit` (default + `application.yaml`), aligning free topic-note generation with the free Study Pack limit (10). Goodwill / activation headroom, not a conversion lever — production free-quota hit rate is 0.0%, so no current user was constrained by the old cap. Frontend surfaces read the limit dynamically; pricing copy ("Limited") is unchanged.
- **Faceted private-library subject strip** — the Library subject chips now recompute against the active filters instead of always showing the whole-library breakdown. With a Course/Program (or tag/search/readiness) filter active, only that set's subjects appear, with matching counts; clicking a chip narrows within the current view. Computed client-side from the already-loaded note list, so it's accurate; the unused `GET /notes/stats` endpoint and `NoteStatsResponse` DTO were removed, with `SubjectCount` extracted as the shared public-profile DTO.

### 🔲 Pending

---

## v0.22.0 - Course & Subject Discovery

**Status: Released**

Theme: make Course/Program and Subject the primary discovery axes across the public library, private library, and public profiles — removing the profile-type audience gate that created false boundaries, surfacing subject breakdowns as interactive filter shortcuts, and closing a session reliability bug that caused unexpected sign-outs under concurrent API load.

### ✅ Shipped

- **Fix concurrent token refresh race condition** — when multiple API calls fire simultaneously with an expired access token, all callers now coalesce on a single in-flight refresh promise (`refreshPromise` in `api.ts`) rather than each independently sending the refresh token. Previously the second caller would send the already-revoked token, get rejected, and trigger an unexpected sign-out. Also bumped the default `JWT_REFRESH_TOKEN_DAYS` from 1 → 7 so users are not forced to re-login after one day of inactivity.
- **Remove Public Library audience pre-filter** — the Public Library no longer auto-applies a profile-based audience filter on fresh visit. All users now land on the full unfiltered library; the audience filter still works when explicitly set via URL param or the filter sheet. Cleaned up `audienceLockedToAll` and `profileDefaultAudience` workarounds that guarded against re-application after the user cleared the filter.
- **Course/Program helper CTA** — a dismissible card above the note list prompts users to browse by Course or Program when no course program filter is active. Clicking "Browse by Course/Program" opens the filter sheet directly. Dismissed state is stored in `sessionStorage` so it does not reappear within the same browsing session.
- **"More [CourseProgram] notes" section on public note detail pages** — when the current note has a `courseProgram` set, the public note detail page shows a "More [X] notes" section after the Practice Mode Teaser. Fetches up to 4 other study-ready public notes with the same course program, sorted by engagement score, with a "View all →" link to the filtered Public Library. `courseProgram` is read from the already-fetched list response (`NoteListItemResponse`) so no backend DTO changes were needed. Next.js deduplicates the `GET /notes/public` fetch within the same render.
- **Library note counts** — Private Library and Public Library now show a note count near the filter bar (`X notes`, or `X of Y notes` when filters are active). `GET /notes/public` now returns `{ items, total }`, with SSR helpers unwrapping `items` for existing static and server-rendered public pages.
- **Private Library subject stats strip** — users with at least 5 notes across multiple subjects now see a subject-count strip above their Library results. Subject chips apply the existing `subject` URL filter, and the new authenticated `GET /notes/stats` endpoint provides top-subject counts plus the total note baseline.
- **Public Profile subject breakdown** — public profiles now show a Learning Focus stat line (`X notes across Y subjects`) plus top subject chips backed by public-note counts. Each chip links to the creator-filtered Public Library for that subject, and both username and legacy userId profile endpoints return the new fields.
- **Course/Program empty state in Public Library** — when a `courseProgram` filter is active and returns no results, the Public Library shows "No [CourseProgram] notes shared yet." with a "Share a note" CTA linking to `/notes/new` (signed-in) or `/auth` (anonymous). Generic "No public notes match your filters." empty state is preserved for all other filter combinations.
- **Statement 1/2 quiz question formatting** — multi-statement questions (e.g. "Statement 1: … Statement 2: …") now render each statement on its own labeled line instead of as a single dense paragraph. Applied across Quick Review, Challenge Quiz, Adaptive Practice, Long Exam, Board Exam (via `quiz-answer-review`), and shared quiz pages via a new `QuizQuestionText` component.
- **Matching group prompt quality fix** — strengthened the Long Exam prompt constraint for MATCHING blocks: choices must be the identical array copied exactly across all items in the group; any deviation requires falling back to standard MCQ. Reduces `reason=different_choices` demotions at validation time.

### 🔲 Pending

---

## v0.21.0 - Personalized Discovery & Library Organization

**Status: Released**

Theme: surface community notes relevant to each user's study track and let them save and reuse their own filter shortcuts — making the app feel personal from day one.

### ✅ Shipped

- **Official author detection made role-based** — removed hardcoded `OFFICIAL_AUTHOR_EMAIL` constant and `isNoteLibOfficialAccount()` email check from `NoteService`; `isOfficialAuthor()` now uses `UserRole.ADMIN` only. Admin users' actual `displayName` is shown on public notes (falls back to "NoteLib" if displayName is unset). No API or frontend changes required.
- **Summary word limit raised to 350** — `MAX_SUMMARY_WORDS` raised from 200 to 350 in `OpenAiLlmStudyPackService` and `developer.txt` prompt; fixes validation rejections for enriched summaries that include a comparison table and Common Misconceptions section (markdown pipe characters inflate the `countWords()` count by ~60 tokens above readable word count).
- **Profile Identity helper text** — added helper text for Display Name ("The name shown on your public notes and profile. Falls back to your first name if left blank.") and rewrote Username helper text to plain English ("Your @handle on NoteLib — shown on your public profile and next to your public notes.").
- **Public Library creator filter + profile "View all" link** — `GET /notes/public` now supports a username-based `creator` filter; public profiles with more than 8 public notes link visitors to `/public/library?creator=<username>` for the full catalog.
- **Remove Learning Focus subject badges from public profile** — removed the subject badge list from the public profile header's Learning Focus section; the "Mostly shares notes in…" summary sentence is kept; the creator filter "View all" link now handles subject browsing.
- **Community Notes dashboard section** — Dashboard now surfaces up to 4 public notes for the user's `courseProgram` below Recent Notes, links to the filtered Public Library, and prompts users without a Course/Program to complete their Learning Profile.
- **Saved Filters for private library** — users can save named private-library filter states to backend storage, apply them from the Library filter bar, and delete stale saved filters.
- **Admin funnel metrics page** — added admin-only `/admin/funnel` and `GET /admin/funnel/metrics` with five all-time conversion-health metrics: activation rate plus median days to first Study Pack, verified users stuck before generation, free quota hit rate excluding active paid users, paywall seen → subscription started conversion, and Study Pack generated → quiz started within 7 days. Metrics are derived from existing tables only; no migrations or new analytics events.
- **Admin summary re-generation** — added `POST /admin/study-packs/regenerate-summaries` to queue summary-only regeneration for admin-owned Study Packs whose summaries are not yet enriched. The endpoint returns queued/skipped counts immediately, runs work on `llmParallelTaskExecutor`, updates only `study_packs.summary`, and skips already-enriched summaries idempotently.

### 🔲 Pending Codex (Conversion Visibility & Admin Tools)

- No pending admin conversion tools.

---

## v0.20.0 - Conversion & Re-engagement

**Status: Released**

Theme: bring inactive users back and close account security gaps — re-engagement campaigns, forgot/change password, richer AI summaries, and public profile polish.

### ✅ Shipped

- **Forgot password** — Users can request a password reset link from `/forgot-password`. The link is valid for 60 minutes and hashed (SHA-256) before storage in a new `password_reset_tokens` table (V67 migration). On successful reset: password is re-hashed, `tokenVersion` is bumped to revoke active sessions, account lockout fields are cleared, and `emailVerifiedAt` is backfilled if null. No user enumeration: all responses return the same generic message. Frontend: `/forgot-password` with email form and generic success state; `/reset-password` with three states (form, submitting, invalid/expired + "Request a new link" CTA); "Forgot password?" link on the login form; green success banner on `/auth?reason=password_reset` after successful reset.
- **Re-engagement campaign (Admin)** — one-time admin-initiated email campaign targeting users inactive for 30+ days who signed up before v0.19.0. Profile-segmented into three variants (student/generic, professional, teacher). Available at `/admin/campaigns`; shows eligible count, last-sent date, and total recipients. Sends up to 100 emails per invocation (free Resend tier) with `email_log` deduplication so already-sent users are always skipped on re-runs.
- **Quiz header polish** — Quick Review now shows the note title below the mode label in the sticky top bar and removes the redundant `N.` number prefix from question text (the progress counter serves this). Challenge Quiz shows the note title below the mode label in its running top bar. Long Exam now shows a sources banner listing all note titles in the running phase when the session spans multiple notes, matching Board Exam's existing treatment.
- **Study Pack summary enrichment** — The AI-generated summary now supports two optional structured sections when applicable: (1) a compact markdown comparison table for notes covering multiple comparable concepts or categories; (2) a **Common Misconceptions** paragraph listing 1–3 common points of confusion. Summary word limit raised from 120 to 200. Frontend renders summary via `react-markdown` + `remark-gfm` across all study pack surfaces (note detail, shared study pack, public library note page, onboarding preview, demo, and admin study view). Preview-only contexts (public library listing card) use a plain-text extractor to avoid rendering raw pipe characters.
- **Change password** — Password-enabled users can change their password from Profile > Sign-in Methods. Backend validates the current password, encodes the new password, updates `lastPasswordChangeAt`, and bumps `tokenVersion` to revoke all active sessions on other devices. Minimum length of 8 characters enforced on both client and server.
- **Public profile polish** — Note cards on the public profile page now cap tags at 3 (with overflow count) and display the top 8 notes sorted by copies → views → shares to keep the page feeling like a lightweight portfolio. Copying the "Share Profile" link now fires a `PUBLIC_PROFILE_SHARED` analytics event and surfaces a "Profile Shares" metric on the profile header (hidden when zero, same pattern as other header metrics). LLM study pack generation retry logging upgraded from INFO to WARN and now includes the rejection reason for easier production diagnosis.

---

## v0.19.0 - Multi-Note Depth & Simulation Parity

**Status: Released**

Theme: complete the multi-note story across all premium simulation modes — Multi-note Board Exam is the last remaining gap — and fix the admin analytics subject drift.

### ✅ Shipped

- **Multi-note Board Exam (Pro)** — Pro users can now span Board Exam Mode across up to 3 same-subject notes. The flow keeps the existing `BOARD_EXAM` identity, quota, fixed question cap, and feedback-free simulation behavior while redistributing questions across selected sources, storing `sessionState.sourceNoteRefs`, skipping single-note pools for multi-source starts, and showing source attribution in-session.
- **Multi-note Board Exam polish** — batch-fetch additional note subjects in a single query instead of one per source; align frontend subject normalization to use explicit English locale (`toLocaleLowerCase('en')`) matching backend `Locale.ROOT` behavior.
- **Admin analytics subject drift fix** — "Top Subjects by Study Pack" now joins through `NoteEntity` to use the note's current subject instead of the study pack's stale cached subject column; a JPQL theta-join (`FROM StudyPackEntity s, NoteEntity n WHERE n.id = s.noteId`) keeps this as a query-only change with no entity or migration work.
- **Multi-note Board Exam session parity** — Board Exam sessions that span multiple notes now surface on every participating note's Recent Sessions and update `lastSessionCompletedAt` for each source note (previously only the primary note was updated); the history sublabel now reads "Multi-note Board Exam · spans N notes" matching the Long Exam pattern.
- **Simulation quota economics** — Board Exam now charges quota per source note instead of per session, Long Exam does the same for multi-note starts, and the Pro monthly caps increased to 10 Board Exam source-note units and 12 Long Exam source-note units.
- **Board Exam coverage scaling** — Board Exam question count now scales with source count (`min(12 * sourceCount, 30)`): single-note remains 12 questions, two-note sessions generate 24, and three-note sessions cap at 30.
- **Simulation paywall timing** — Board Exam quota checks now surface on mode selection and prestart page load before users click Begin, while Long Exam direct visits show the Pro paywall modal instead of silently redirecting non-Pro users.

---

## v0.18.0 - Profile Completeness & Communication

**Status: Released**

Theme: complete the Professional profile experience, fix subscription expiry and email communication gaps, add KaTeX math rendering for computational working solutions, and introduce concept-level spaced repetition signals in Adaptive Practice.

### ✅ Shipped

- **Professional profile gap fix** — selecting Professional in onboarding now auto-selects the Professional learner level; the completion screen copy updated to mention Interview Practice; the note detail page now recognises Professional users (previously fell through to Student), and shows an Interview Practice action button for Pro-plan Professional users.
- **Multi-note Interview Practice** — Professional Pro users can add up to 2 additional notes on the Interview Practice prestart screen; questions are distributed proportionally across sources with cross-source deduplication; no subject constraint (real interviews span domains).
- **Interview Practice note picker filter** — additional notes shown in the prestart picker are now filtered to match the primary note's course/program, preventing unrelated domain notes from appearing; falls back to all ready notes when the primary note has no course/program set.
- **Parent profile hidden** — removed the "Parent" option from the Profile Type picker in profile settings; the profile type remains in the enum and backend but is not shown until the use case is defined.
- **Interview Practice prestart UI polish** — redesigned prestart page to match Long Exam's layout: free header with "Built from [note]" attribution, standalone Session Length card, What to Expect card with icon-list items, full-width note picker rows (replacing chips), and a consistent footer bar with note/question count.
- **Subscription expiry email notifications** — paid users receive a 7-day warning, a 1-day reminder, and a post-expiry "your plan has ended" email with a renewal link; deduplication via email_log prevents re-sends; emails are transactional and sent regardless of retention email preferences; plain-text fallback templates added for all three variants (missing `.txt` files would have caused silent NPE in Resend payload serialization).
- **KaTeX math rendering for working solutions (Pro)** — `workingSolution` panels for COMPUTATIONAL questions now render LaTeX expressions using KaTeX; inline `$...$` and block `$$...$$` delimiters are detected and rendered; plain-text solutions fall back gracefully; LLM prompts updated to emit LaTeX-formatted working solutions for all quantitative quiz modes.
- **Concept-level spaced repetition signals** — tracks the last correct answer date per concept per user per study pack; Adaptive Practice merges "due" concepts (not reviewed in 3+ days) with weak concepts at generation time; Key Concepts tab shows amber "Due — Xd ago" badges and a prompt to start Adaptive Practice for PLUS/PRO users with overdue concepts.
- **Public Library "Clear all" filter bug fix** — "Clear all" was incorrectly resetting `audienceLockedToAll` to `false`, causing the profile-based audience pre-filter (e.g. Professional) to immediately re-apply after clearing; fixed to lock to All on clear.
- **Admin dashboard engagement metrics gap** — Long Exam and Interview Practice were missing from the Engagement section of the admin dashboard; added `LONG_EXAM_STARTED` and `INTERVIEW_PRACTICE_STARTED` counts as "Long Exams" and "Interview Practice" metric cards.
- **Library filter persistence** — private library filters (search, subject, course/program, tags, readiness, sort) are now reflected in URL query params; state initializes from URL on mount so back navigation restores the exact filter state.
- **Back-navigation filter restore (private library)** — opening a note from the filtered library now encodes the current filter URL as a `?ref=` param on the note URL; the "← Library" back link reads `ref` from `searchParams` so clicking it returns to the exact filtered state; fixed three "prune stale values" effects that ran on mount with empty `items` and wiped URL-restored subject/courseProgram/tag filters before data loaded.
- **Back-navigation filter restore (public library)** — opening a note from the filtered public library saves the current filter URL to `sessionStorage`; the "← Public Library" back link restores it; fixed `window` → `globalThis` lint violation in `PublicLibraryBackLink`.
- **Public Library audience pre-filter re-application bug** — after a Professional user cleared the audience pre-filter, navigating to a note and back would re-apply the Professional filter; fixed by writing `?audience=all` to the URL when the user explicitly selects "All", so the URL encodes the intent rather than relying on transient `audienceLockedToAll` React state that resets on re-mount.
- **Unverified email banner and verify page copy updated** — replaced the stale feature enumeration ("generate Study Packs, use OCR, and publish notes") with feature-agnostic copy: "Verify your email to unlock all features. You can write and save notes in the meantime." — avoids the recurring problem of the list going out of date as new gated features are added; same fix applied to the Verify Email page card description.
- **Email template audit and polish** — verified all transactional emails (verification, welcome, subscription expiry, post-expiry, refund, waitlist, retention); added spam folder guidance ("Don't see it? Check your Spam or Promotions folder") to the Verify Email page and Verify Email Required modal — not the email itself, since users who can't find the email won't read it; resend success toast updated to mention spam folder; corrected stale welcome email plan tier copy: Plus now correctly lists Adaptive Practice as its primary differentiator; Pro now correctly lists Board Exam Mode, Interview Practice, Long Exam, and difficulty selection (removed "Weak Concept Training" and the incorrect implication that Adaptive Practice is Pro-exclusive); fixed Google signup not sending the welcome email — Google users were tagged as verified immediately on account creation but `sendWelcomeEmail` was never called; email/password users received it via `verifyToken`; Google users now receive it in `createGoogleUser` with the same `email_log` deduplication guard.

---

## v0.17.0 - Quiz Quality & Depth

**Status: Released**

Theme: fix prompt-level defects in generated quizzes (choice bleed into explanations, repeating distractors, monotone framing) and add question format variety — NOT/EXCEPT/TRUE framing, computational questions (engineering/sciences, KaTeX), True/False, multi-select, and matching-type blocks. Multi-select, matching, and quality fixes are plan-agnostic; premium-only formats must be explicitly gated when introduced.

### ✅ Shipped

- **Quiz generation quality fixes** — across all quiz modes (Quick Review, Challenge Quiz, Board Exam, Long Exam, Adaptive Practice, Teacher Quiz): added prompt instruction to keep answer choices out of explanation text; added distractor-independence constraint so no two questions share the same choice set; added question framing variety instruction to distribute across positive, negative ("NOT/EXCEPT"), best-describes, and assertion-style framings (Quick Review, Challenge Quiz, Board Exam, Long Exam, Teacher Quiz only — Adaptive Practice excluded as targeted practice).
- **Study Pack key concepts reliability fix** — aligned the JSON schema with backend validation (`keyConcepts` now accepts 5–10 items) and added `key_concepts` field-name coercion for LLM payloads. Reduces intermittent "invalid key concepts" generation failures without changing the retry mechanism.
- **Multi-select questions** — added the `MULTI_SELECT` question format with `correctIndices`, all-or-nothing v1 scoring, session-state persistence, answer review highlighting, and deterministic shuffle support. Multi-select is available on all plans across Quick Review, Challenge Quiz, Adaptive Practice, Long Exam, and Teacher Quiz; Board Exam remains single-correct MCQ only.
- **Matching-type questions** — added the `MATCHING` question format with shared `questionGroup` option blocks, one block max per quiz batch, deterministic group-aware shuffling, shared-options rendering, and per-item scoring. Matching is available on all plans across Quick Review, Challenge Quiz, Adaptive Practice, Long Exam, and Teacher Quiz; Board Exam remains single-correct standalone MCQ only.
- **Library filter consolidation** — moved Filter (readiness), Subjects, Popular Tags, and Course/Program into the "More Filters" modal for both private and public libraries; removed the inline collapsible sections that pushed results off-screen when typing; active filters are now shown as removable blue chips in a compact row below the search/filter buttons; private library applies all modal filters immediately; public library stages changes and applies on "Apply" click to avoid extra backend round-trips.
- **AI subject suggestion specificity fix** — changed the course/program-to-subject guidance in the AI metadata suggestion prompt: the LLM no longer echoes the course/program name as the subject; instead it derives the specific academic sub-field from the note title and content (e.g. a note in "Mechanical Engineering Licensure" about fluid machinery now suggests "Fluid Machinery" rather than "Mechanical Engineering").
- **Long Exam pre-screen multi-note hint** — when a student has only one note for the current subject (no additional notes available to span the exam), the Long Exam prestart screen now shows a contextual hint prompting them to create another note with the same subject to unlock multi-note exam mode.
- **Long Exam parallel batch salvage** — when one of the two parallel generation batches fails (e.g. invalid explanation after retries), the succeeded batch is now reused and only the remaining questions are regenerated sequentially, cutting fallback latency roughly in half instead of regenerating all questions from scratch. The long exam prompt also received a stronger MATCHING block constraint (minimum 2 items, explicit "omit rather than create a 1-item group" rule) to reduce `invalid_size` and `different_choices` demotions that produce noisy WARN logs.
- **Computational quiz working solutions** — computational questions generated for engineering and sciences notes now include a `workingSolution` field with a step-by-step derivation; Pro users see a distinct working-solution panel below the explanation after answering; teachers see it unconditionally in the quiz preview for pre-export review.
- **Xendit compliance — legal identity and social presence** — added operator identity section to Terms of Service (section 1: "About NoteLib", operated by Einar Lagera, Philippines); added Facebook page link to public footer; updated `README.md` with social and contact links. Required for GCash payment mode activation.

---

## v0.16.0 - Conversion & Growth

**Status: Released**

Theme: close the gap between social traffic and signed-up users; make teachers a distribution channel through student-facing shareable quiz links; tighten the post-signup onboarding so new users land in their first quiz session, not an empty library; PWA installability for mobile social traffic.

### ✅ Shipped

- **Refund & Cancellation Policy** — added the public `/refund` policy page with step-by-step refund request and cancellation instructions, linked it from the public footer, Settings billing note, and Terms of Service, and documented the manual-renewal/no-proration refund model for payment review readiness.
- **Admin refund action** — added an admin-only Xendit invoice refund endpoint and Recent Paid Upgrades refund button; eligible Xendit paid transactions can be marked `REFUNDED` after Xendit accepts the refund, and users receive a refund confirmation email with the expected 5–10 business-day timeline.
- **Cancellation UX fixes** — Settings cancellation now shows the specific access end date, keeps the modal open with a support-oriented error message when cancellation fails, and replaces vague post-cancellation plan-card text with `Access ends [date]`.
- **Teacher in-app guided tips** — added 5 one-time dismissible `GuidanceTip` placements gated by `profileType === "TEACHER"`: (1) first dashboard visit, (2) note content area on create, (3) inside the Generate Quiz modal, (4) library list when multi-note selection is available, (5) quiz preview page near the DOCX Export button; each tip is dismissed to localStorage and never re-shown.
- **Send Feedback consistency fix** — removed the floating "Send Feedback" button (was inconsistently shown on Dashboard/Library/Settings only); header icon now renders on all authenticated pages; fixed modal positioning bug where `backdrop-filter` on the sticky header created a CSS containing block for `position: fixed` children — resolved by wrapping `AppModal` in `ReactDOM.createPortal(..., document.body)`, which benefits all modals app-wide.
- **Profile-aware learning loop on landing** — replaced generic HowItWorks + ProfileShowcase sections with a single interactive ProfileLearningSection; four-tab layout (Students / Exam Reviewers / Teachers / Professionals) each showing learning loop steps, mode chips, and a screenshot
- **Post-signup copy-note → instant quiz** — new users who sign up via a public note copy CTA have the note automatically copied and Study Pack generation started; they land directly on a Quick Review session, bypassing onboarding; copy intent survives Google One Tap and email verification via a short-lived cookie
- **Shareable Student Quiz Links** — teachers generate a /quiz/[token] link from the quiz preview page; students take the quiz anonymously in-browser; results screen prompts signup; quota gated (Free: 3/month, Plus: 10/month, Pro: unlimited)
- **Library filter discoverability** — replaced chip-styled `+ More` overflow controls with `Browse all` text links, added Course / Program filtering to private and public libraries, and added faded scroll-rail affordances so horizontal filter rows read as scrollable.
- **Library UX polish** — moved `Browse all` to section headers, fixed bottom-sheet modal behavior on mobile, and moved the public library Course / Program control into More Filters.
- **Consistent quota-limit UI** — unified in-page at-limit banners (Study Pack, note generation) into a single `QuotaLimitBanner` component with icon, title, reset date, and plan-aware upgrade CTA.
- **PWA installability** — added `start_url`, `scope`, and icon `purpose` to the manifest; service worker caches static assets and shows an offline page when navigating without a connection.
- **Mobile UX** — fixed iOS Safari input zoom (font-size >= 16px globally); added an "Add to Home Screen" nudge for returning mobile visitors.

---

## v0.15.2 - UX Cleanup & Bug Fixes

**Status: Released**

Theme: post-Teacher-Power-Features polish pass focused on long-standing UI/UX bugs and rough edges across notes, library, profile navigation, help guides, and quiz session surfaces. No new features — just sharper defaults and accurate state.

### Planned Scope

- **Quiz session display correctness** — Recent Sessions chip shows the actual quiz mode (Quick Review / Challenge Quiz / Adaptive Practice / Long Exam / Board Exam / Interview Practice) instead of always "Challenge Quiz"; library card "Not reviewed yet" label updates after any quiz mode completion, not just Quick Review; multi-note Long Exam sessions appear on every participating note with a "spans N notes" sublabel
- **Copy and navigation polish** — Edit Note drops the Import Notes uploader; app shell Profile sidebar opens Profile Settings (avatar dropdown's "My Profile" remains the public-profile path); Board Exam Guide no longer recommends Long Exam and the footer "Switch Profile" CTA deep-links to the Profile Type section; Student / Teacher / Professional guides show a profile-aware "Switch Profile" footer CTA (hidden on the user's own profile guide); share-note modal auto-copies the URL on open with a "Copied" success pill
- **Library Draft filter** — add a `Draft` chip alongside All / Quiz Ready / Study Pack Ready so users can quickly find notes parked while waiting for Study Pack quota reset
- **Target Audience cleanup** — Create Note "Who is this note for?" keeps hidden auto-prefill for Student / Board Exam / Professional profiles and fixes Professional notes so they save with the Professional audience instead of Student; Teacher/Admin keeps a visible required picker with Professional as a selectable audience

### ✅ Shipped

- **Quiz session display correctness** — library card review labels and Note Detail Recent Sessions now aggregate every quiz mode: Challenge Quiz, Adaptive Practice, Long Exam, Board Exam, and Interview Practice completions update the library reviewed timestamp, Recent Sessions chips show the actual mode, and multi-note Long Exam sessions surface on every participating note with a spans-N-notes sublabel
- polish: Edit Note no longer shows the Import Notes uploader — that flow belongs to Create Note only.
- polish: app shell Profile sidebar opens Profile Settings; public profile remains accessible via the avatar dropdown's "My Profile".
- fix: Board Exam Guide no longer recommends Long Exam and the footer "Switch Profile" CTA deep-links to the Profile Type section.
- polish: Student / Teacher / Professional guides now show a profile-aware "Switch Profile" footer CTA — hidden when the user is already on the matching guide.
- polish: share-note modal auto-copies the shareable URL on open and shows a Copied success pill.
- polish: library Filter now includes a Draft chip so users can quickly find notes they haven't generated a Study Pack for — useful when Study Pack quota is exhausted and you're parking ideas before the monthly reset.
- fix: Create Note Target Audience no longer mis-tags Professional users as "Student" — Professional profile now saves the correct PROFESSIONAL audience, and Teachers/Admins can also tag notes as Professional from the dropdown. Field remains hidden + auto-prefilled for Student / Board Exam / Professional, and visible + required for Teacher / Admin.
- fix: Public Library now shows a "Professional" filter chip in the "For" rail and auto-selects it on first visit for Professional profile users — previously `PUBLIC_NOTE_TARGET_PROFILE_TYPES` excluded PROFESSIONAL, `parsePublicLibraryAudienceQueryValue` didn't map the value, and `resolvePublicLibraryTargetProfileFilter` was never called; profile default is now resolved inline via `effectiveAudience` so the first API call uses the correct audience with no redirect and no race condition.
- polish: Public Library shows a sparse-audience helper card when a non-All audience filter (Student, Exam Reviewer, Professional) returns fewer than 10 notes — copy names the audience and offers a "View all notes" escape hatch; card disappears automatically as the audience library grows past the threshold.
- fix: Export Quiz modal Versions selector (1 / 2 / 3) now highlights the selected version in blue — was using `bg-background` which blended into the dark container and made the active selection invisible.
- fix: Regenerate Quiz modal question-count selector (10 / 20 / 30) now highlights the selected count in blue — same dark-on-dark issue as the Versions selector.
- fix: Settings → Plan & Billing Monthly / Annual billing cycle toggle now highlights the active cycle in blue — was using `bg-background text-foreground` which produced a dark-on-dark selected state in dark mode.

---

## v0.15.1 - Teacher Power Features

**Status: Released**

Theme: extend the teacher quiz-authoring workflow with concrete controls that turn it into a complete classroom tool, building on the v0.15.0 teacher flow polish and plan accessibility foundation.

### Planned Scope

- **Question count control on Generate Quiz** — let teachers choose 10 / 20 / 30 questions per generated quiz; Plus+ Teacher unlocks 20 and 30
- **Custom DOCX header** — teacher profile carries an optional school name that appears in every DOCX export; per-export modal adds class/section name and date toggle
- **Multiple exam versions (A/B/C)** — single-DOCX export with 2 or 3 deterministically shuffled versions for anti-cheating; Plus+ Teacher only

### ✅ Shipped

- **Question count control on teacher Generate Quiz** — Teacher Note Detail now offers 10 / 20 / 30 question generation controls, keeps Free teachers at 10 with a Plus-specific longer-quiz paywall, and sends the selected count through teacher quiz generation so Plus and Pro teachers get the requested quiz length
- **Generate Quiz modal** — moved the question count picker out of the note card into a focused modal; both Generate Quiz and Regenerate now open the same modal so teachers can choose count on every generation including regeneration; preflight checks (quiz limit, Study Pack gate) still fire before the modal opens
- **Custom DOCX header for teacher exports** — Teacher Profile now stores an optional school name used by DOCX quiz and Exam Builder exports, while the export modal lets teachers add a class / section line and toggle the export date without changing stored quiz data
- **Multiple exam versions (A/B/C) for teacher DOCX export** — the shared teacher DOCX export modal now lets Plus and Pro teachers export one, two, or three deterministic exam versions in a single packet from Quiz Preview or Exam Builder; multi-version exports shuffle stored generated-quiz question and choice order per version, keep answer keys aligned to the shuffled choices, and show Free teachers a Plus-specific anti-cheating paywall for locked version counts
- refactor: per-note learner level removed — generation context uses owner profile level; teacher quiz modal adds an optional Target Level picker per generation
- **Learner Level required across onboarding, profile, and teacher quiz generation** — onboarding step 2 now gates Continue on a selected level; `UpdateUserProfileRequest` enforces `@NotNull`; teacher Generate Quiz modal Target Level picker is required and pre-fills with the last used level on that note (falling back to the profile level); teacher-aware copy reframes the field as "default quiz difficulty for material you teach" in onboarding, profile settings, and the modal.
- fix: separate LLM parallel-batch executor to eliminate Study Pack generation deadlock — pool generation no longer self-submits to a saturated single-thread executor
- **AI suggestion modal survives navigation** — fixed a bug where navigating away from a note while the Study Pack was generating and returning later would silently skip the AI title/subject/tags suggestion modal; the awaiting-suggestion state is now persisted in `sessionStorage` so the modal still fires on return, whether the note was still generating or already ready

---

## v0.15.0 - Premium Mode Uplift + Cost-Control Quota Refactor

**Status: Released**

Theme: make Long Exam and Board Exam feel premium, not just gated behind a paywall, and close the unbounded-LLM-cost gap on uncapped modes. This is a margin fix framed as a UX uplift, not a feature add.

### Planned Scope

- **Premium feel for Long Exam and Board Exam** — stronger pre-session framing, expected-duration and simulation cues, post-session score-report polish, domain-coverage visualization, suggested-next-step framing, and distinct mode presentation while keeping both modes austere by design
- **Cost-control quota refactor** — replace uncapped Long Exam and Board Exam behavior with explicit per-mode monthly caps, keep existing Pro Challenge Quiz / Adaptive Practice / Study Pack value intact unless v0.14.0 usage data says otherwise, and surface per-mode usage in Settings -> Plan & Billing
- **Quota infrastructure alignment** — add explicit per-mode quotas on `UserUsageEntity` and `StudySnapProperties`, reset them through `BillingUsageResetJob`, and frame the change honestly as clearer monthly plan inclusions
- **Interview Practice evolution review** — evaluate multi-note Interview Practice, structured interview templates, open-ended evaluation, profile / role enrichment, and possible Plus promotion only after Interview Practice v1 has run for at least one billing cycle

### ✅ Shipped

- Add monthly quotas for Long Exam (10/mo Pro) and Board Exam (5/mo Pro, shared Challenge Quiz budget); per-mode usage counters (Long Exam, Board Exam, Interview Practice) surfaced in Settings → Plan & Billing alongside existing Study Pack and Quiz counters
- Surface Interview Practice and Professional profile on landing page, help center, and learn page
- **Premium feel for Long Exam and Board Exam — prestart anticipation and score-reveal result** — replaced the blue eyebrow + dense 3-column info grid with a confident hero heading, sitting-framing subtitle, and vertical icon-led "What to expect" stack on both prestart screens; replaced the small 2-column score grid on results with a hero `ScoreReveal` (large tabular percentage, performance pill, supporting line); converted domain/concept breakdown tables to vertical bar lists; quieted weak-domain and weak-concept callouts; elevated "Suggested Next Step" framing. Calm neutral palette, no brand blue. New shared `ScoreReveal` primitive in `components/exam-mode/`. In-session chrome polish ships in a follow-up
- **Premium feel for Long Exam and Board Exam — in-session focus mode** — app-shell sidebar and page header hide during the `running` phase of both modes via a new `ExamFocusContext` + `useExamFocusMode` hook (Long Exam was previously left with the full chrome since it doesn't request fullscreen); new shared `ExamTopBar` primitive with a prominent tabular-nums timer and "Time remaining" label; extracted `QuestionNavigator` primitive and added it to Long Exam (previously had no jump-between-questions navigation, which made the mode feel harder than Board Exam); Long Exam question card polished with larger leading and calmer chrome; Board Exam now exits fullscreen on submit/forfeit/auto-submit so the score report renders in normal layout
- **App shell exam titles** — app-shell page titles now reflect the active exam mode: Board Exam overrides the shared Challenge Quiz route title while active, and Long Exam / Interview Practice routes resolve to their actual mode names instead of the generic Note title
- **Pre-generated Long Exam and Board Exam question pools** — new Study Pack generation now asynchronously prepares reusable mixed-difficulty pools (48 Long Exam questions, 24 Board Exam questions) so READY single-note exams can start immediately at `IN_PROGRESS` without live LLM generation; pool sampling reserves unserved questions to avoid repeat exam sets, refreshes when the pool drops below one full exam, and lazily initializes pools for existing Study Packs while preserving live generation fallback for missing, failed, or multi-note Long Exam cases
- **Landing page repositioning + reading-surface feedback fix** — replaced the generator-pitch hero with a workspace-first positioning ("Your notes become your study system."), swapped CTA priority so Start for Free leads and Try Demo follows, refreshed the eyebrow chips to surface "5 study modes" plus the Pro mode trio (Long Exam · Board Exam · Interview Practice); replaced the generic Target Users section with a profile-aware showcase that shows the specific flow for Student, Exam Reviewer, Teacher, and Professional with mode chips, plan badges, and outcome screenshots (Mastery Report, Score Report, Quiz Preview, Interview Readiness Report); added a "Five study modes, one workspace" section that names every mode and its plan tier, with an Interview Practice callout strip below; fixed the audience grid that previously wrapped 3+1 on tablet by moving the new showcase to `md:grid-cols-2 lg:grid-cols-4`; dropped the floating Send Feedback widget from `/public/library/*` so it no longer overlaps note content (header icon remains for authenticated users on those reading surfaces)
- **Fix Long Exam / Board Exam generation hanging indefinitely** — added connect (10s) and read (180s) timeouts to `RestClient` in `OpenAiLlmConfig` via `SimpleClientHttpRequestFactory`; added `.orTimeout(240, TimeUnit.SECONDS)` to `CompletableFuture.allOf()` in the parallel Long Exam path so a stalled batch falls back to sequential generation after 4 minutes; reduced `PARALLEL_BUFFER` from 6 to 2 (per-batch size for 25Q drops from 19 to 15, still sufficient overlap for deduplication)
- **Fix multi-note Long Exam `INVALID_LONG_EXAM_SOURCE` false positive** — `LongExamService.resolveSourceNoteRefs()` was comparing `studyPack.getSubject()` fields, which could diverge from note subjects if the user edited a note's subject after generating a Study Pack (note subject updated, linked study pack subject was not); changed validation to use note subjects (via `NoteRepository`) so it matches what the frontend shows in the "Span this exam" card; also fixed `NoteService.update()` to sync subject changes to the linked Study Pack going forward, closing the same gap that `StudyPackService.updateMetadata()` already handles in the reverse direction
- **Teacher Dashboard polish** — reordered teacher dashboard sections around the Create → Generate → Export loop so export-ready quizzes and generated previews appear before recent notes, and refreshed the zero-note state with Library access plus Exam Builder guidance
- **Teacher Library empty state** — teachers now see export-first note-library onboarding copy and no demo CTA, while student Library empty-state copy remains unchanged
- **Teacher Quiz Preview polish** — Quiz Preview now uses the note title as the page heading, shows question count beside the export-ready badge, keeps Export as the sole primary action, and moves Regenerate into an overflow menu
- **Teacher Exam Builder polish** — renamed the scratch preset to `Start Blank`, made it the first template, clarified Even/Smart Balance helper text, and added a footer breakdown of questions per section before export
- **Exam Builder UX polish** — teachers can add quiz-ready notes into a chosen section without leaving the builder, balancing controls now sit below section organization with simpler guidance and distinct icons, section composition renders as wrapped footer chips, and section note cards drop reorder-sensitive `NOTE N` eyebrows
- **Teacher paywall copy polish** — quiz-generation and export-limit upgrade paths now use teacher-aware CTA/body copy for classroom quiz generation and DOCX export limits without changing plan gates
- **Teacher plan accessibility** — Teacher DOCX export quotas now follow the documented profile override: Free teachers get 10 monthly DOCX exports and Plus teachers get unlimited DOCX exports while PDF limits stay plan-standard; Plan & Billing shows resolved DOCX/PDF quotas separately and Teacher Plus upgrade copy no longer promises export headroom it already has
- **Per-note learner level override** — notes now carry an optional learner level so teachers can set the difficulty / grade level per class note independently of their profile default; Study Pack generation plus Long Exam and Board Exam question pools use the note's level when set
- **Learner Level and Course/Program required on notes** — both fields are now required before saving or generating a Study Pack; pre-filled from the user's profile so the gate rarely blocks users who completed onboarding; Course/Program helper text adapts dynamically to the selected Learner Level in both the creation form and the inline metadata edit; required fields are marked with asterisks, validation shows a single aggregate amber toast listing all missing fields at once, and the "Add details" section header no longer says "(optional)"

---

## v0.14.0 - Grow the Surface, Deepen the Practice

**Status: Released**

Theme: expand organic reach through subject SEO pages, unlock professional-audience depth with Interview Practice, extend Long Exam to span multiple notes, and close out the quiz generation performance work deferred from v0.13.0.

### Planned Scope

- ~~**Subject landing pages (SEO)**~~ ✅ — see Shipped below
- ~~**Faster quiz generation**~~ ✅ — see Shipped below
- ~~**Interview Practice Mode (Professional Profile)**~~ ✅ — see Shipped below
- ~~**Multi-note Long Exam**~~ ✅ — see Shipped below
- ~~**Stale docs cleanup**~~ ✅ — see Shipped below

### ✅ Shipped

- **Faster quiz generation** — Board Exam now uses dedicated simulation-framed prompts; Long Exam start returns `GENERATING` immediately and completes generation in the background; Long Exam generation splits work across parallel LLM calls with sequential fallback
- **Interview Practice Mode v1** — Professional Profile Pro users get an Adaptive Practice sub-mode with scenario MCQs, per-answer AI critique, a soft 2-minute pacing timer, dedicated monthly quota, dashboard entry card, and an Interview Readiness Report
- **Quiz mode launch polish** — Professional mode selection now includes Interview Practice for per-note entry; Long Exam and Interview Practice prestart screens link back to mode selection; Board Exam and Long Exam default to Mixed without a difficulty selector
- **Prestart consistency pass** — aligned Interview Practice, Long Exam, and Board Exam prestart layouts with Challenge Quiz using uppercase eyebrows and inset cards with mode-specific top sections plus 3-column meta grids
- **Multi-note Long Exam** — Pro users can add up to 3 additional same-subject notes from Long Exam prestart; generation stores source note refs in session JSONB, distributes questions proportionally across sources, and includes covered sources in the mastery report
- **Rename: "Board Taker" → "Exam Reviewer"** — updated all user-facing profile-type labels to "Exam Reviewer" for broader applicability across board, civil service, licensure, and professional exams; underlying enum values and database schema unchanged
- **Consistent quiz generation loading** — Long Exam and Interview Practice now use the shared QuizGenerationOverlay with mode-specific titles and rotating messages; polished the overlay animation from a vertical bounce to a smoother opacity/scale pulse and added a slow-fill progress bar
- **Subject landing pages** — server-rendered `/public/library/[subject]` pages with per-subject metadata, decay-ranked sections, and static generation
- **Stale docs cleanup** — removed 17 stale/legacy files; merged AI generation spec and overflow menu rules into active docs; renamed `SPEC.md` → `BILLING_ADDENDUM.md`
- **Fix: Challenge Quiz question navigator shows stale count on Previous navigation** — added `key={currentIndex}` to the navigator summary `<p>` to force remount on index change, clearing a stale GPU compositing layer bitmap caused by `will-change: transform` on the toggle button (iOS Safari rendering quirk)
- **Per-mode paywall gating** — moved challenge quiz quota check from the note detail "Challenge Quiz" button (which blocked the mode selection screen) to each mode card click; users now always reach the "Choose your quiz mode" screen, and the paywall or limit page only triggers when selecting Challenge Quiz or Board Exam Mode specifically; Long Exam and Interview Practice continue to handle their own quota checks independently
- **Help Center strengthened for non-technical users** — split the old "Study Packs & Quizzes" card into two focused cards (Study Packs and Quiz Modes); added a Board Exam Guide card with a 4-step board exam workflow, tips, and a profile-switch CTA; Quiz Modes guide now covers all 5 modes with plan badges (Plus/Pro); Study Packs guide rewritten with 3 sections, bullets, and a tip callout; corrected Adaptive Practice badge from "Pro" to "Plus / Pro"; added Note vs Study Pack mental model to Getting Started step 1; updated Teacher Guide steps 3–4 with explicit location context for Quiz Preview and Export; updated Student Guide and Getting Started to mention Long Exam and Board Exam Mode

---

## v0.13.0 - Complete the Promise, Reach New Audiences

**Status: Released**

Theme: ship the modes that were already promised (Long Exam), open NoteLib to a new professional audience, improve organic discovery through subject SEO pages, and close out infrastructure research items deferred from v0.12.0.

### Planned Scope

- ~~**Long Exam Mode v1 (Pro-only)**~~ ✅ — see Shipped below
- ~~**Professional Profile activation**~~ ✅ — see Shipped below
- **Faster quiz generation** — profile LLM latency end-to-end; evaluate streaming, model selection (`gpt-4.1-mini`), and early session creation; implement based on findings
- **Subject landing pages (SEO)** — proper server-rendered `/public/library/[subject]` pages with per-subject `<title>`/`<meta>`; decay-ranked note cards; sitemap update; deferred from v0.12.0
- ~~**Proration / recomputation design doc**~~ ✅ — see Shipped below
- **Stale docs cleanup** — audit and update or remove `docs/` files still referencing v0.11.0 or earlier resolved items

### ✅ Shipped

- **Long Exam Mode v1 — backend** — added Pro-only `/long-exam` endpoints backed by the shared `quick_review_sessions` lifecycle: fixed question generation is committed before session start, `LONG_EXAM` sessions support `PAUSED` pause/resume state, completion returns a mastery report with domain breakdown / weak domains / suggested next step, FeatureGateService owns access control, and Flyway V55 adds the active-or-paused uniqueness guard
- **Long Exam Mode v1 frontend** — added the `/notes/[id]/long-exam` page with prestart, generating, paused-recovery, running, and complete phases; wired Pro paywall gating from Challenge Quiz mode selection; removed the Long Exam `Coming Soon` placeholder; added the legacy `/study-packs/[id]/long-exam` redirect shim
- **Timer fix** — Challenge Quiz and Board Exam time limits are now computed per question (`90s` per Challenge question, `60s` per Board Exam question); generate-more extends the deadline correctly; Long Exam now uses the same server-anchored deadline mechanism
- **Long Exam UI consistency** — running Long Exams now use the Board Exam-style focused top bar, leave/forfeit modal with navigation guard, sticky Previous/Next/Submit footer, beforeunload warning, and one-time focus guidance instead of inline pause/forfeit controls
- **Navigation footer alignment fix** — Challenge Quiz, Board Exam, and Long Exam sticky footers now keep `Previous` left-aligned and Next / Submit / add-question actions right-aligned within the assessment column
- **Professional Profile activated** — `PROFESSIONAL` profile type selectable in profile settings and onboarding (no longer `Coming Soon`); Challenge Quiz shown as `Certification Review` and Long Exam as `Full Practice Exam` for Professional users; learner level grouped picker shows "Recommended for Professionals"; professional dashboard emphasis for certification and career learning; Interview Practice Mode deferred to v0.14.0+
- **Onboarding overhaul** — profile type cards redesigned as a 2×2 grid with emoji icons for all four active profile types (🎓 Student, 📋 Board Taker, 🏫 Teacher, 💼 Professional); step 2 "What's your goal?" replaced with Learner Level + Course/Program step that feeds AI generation context directly; step 2 pre-populates from the user's saved profile on re-entry; step 4 Back button removed post-generation to prevent study pack quota exhaustion; learner level now collected during onboarding instead of deferred to dashboard prompt
- **Board Taker exam date editable in profile settings** — Board Taker users can update their exam date after onboarding via the Profile Type card in profile settings; supports retakes and reschedules; new `PUT /users/profile/exam-date` endpoint; dashboard countdown reflects the updated date
- **Proration / recomputation design doc** — `docs/product/PRORATION.md`; defines upgrade (fresh 30-day cycle, no credit), downgrade (deferred to post auto-renewal), same-plan renewal (stack period, no reset), cancellation (end-of-period, manual refund via support), and quota recomputation rules per scenario; four open questions identified for pre-implementation resolution

---

## v0.12.0 - Learning Experience, Discovery, and Retention

**Status: Released**

Theme: deepen the learning experience, make the product easier to discover and navigate, and improve retention signals that bring users back to study.

### Planned Scope

- **Public Library conversion optimization** *(top priority)* — make public note pages useful as shareable learning pages and top-of-funnel acquisition surfaces; add a learning hook near the top of the public note page so visitors understand the topic before being asked to act; surface a mini quiz preview that lets public visitors answer 1–2 questions before requiring signup; gate full quiz access and progress tracking behind login; add a CTA that encourages visitors to create their own Study Pack from their own notes; reorder CTAs so value is shown before any conversion ask; improve generated note formatting for scannability with shorter sections, clearer headings, key-fact blocks, and exam-friendly paragraph density
- **Public note detail engagement polish** — refine the learning hook fallback and summary-led framing, update Quick Check copy so it feels like a lightweight study prompt, add a post-answer CTA that appears only after the visitor has engaged, tighten CTA wording to `Create your own Study Pack` / `Copy to My Library` / `Share this note`, and improve Full Notes readability without changing quiz/session logic
- **Public creator identity / creator display safety** — public note cards and public note detail should stop relying on `displayName` alone; show `displayName` as the readable label, add a stable public handle or slug when disambiguation is needed, keep profile links tied to a stable public identifier, and preserve existing public links without exposing raw user IDs or emails
- **Learner Level + Course/Program UX refinement** — quiz prompts and metadata suggestions are influenced by the user's saved learner level; Course/Program suggestions are narrowed by the active subject and learner context so recommendations feel personalised rather than generic
- **Conversion funnel optimization** — plan-aware upgrade CTAs (`getUpgradeCtas`) on all paywall and limit surfaces; post-quiz `PostSuccessUpgradeNudge` on Quick Review and Challenge Quiz result screens; analytics events (`QUICK_REVIEW_COMPLETED`, `CHALLENGE_QUIZ_COMPLETED`, `ADAPTIVE_PRACTICE_COMPLETED`, `UPGRADE_CLICKED`) tracked and queryable via admin dashboard
- **Proration / recomputation design** — design how mid-cycle plan changes (upgrade or downgrade) recompute Study Pack and quiz quotas; do not implement until the design is approved
- **Retention loops** — continue-studying prompts on Dashboard; weak-concept reminder emails on a backend schedule; near-limit banners surface reset dates and upgrade CTAs
- **Backend Public Library filtering + shareable URLs** — subject, tags, learner level, and profile-type filters moved to backend query params; each filtered state maps to a shareable URL so students can bookmark or share specific topic collections
- **Library organization guidance** — in-app guidance tells students how to use subjects and Course/Program to keep their private Library organized as it grows
- **Social login (Google first)** — Google OAuth login/signup alongside the existing email-and-password flow; no other providers until Google is shipped and stable
- **Faster quiz generation investigation** — profile current LLM latency for quiz generation; prototype streaming or early-session creation patterns; write findings and a recommended approach before any implementation
- ~~**Profile-aware mode selection UX**~~ ✅ — see Shipped below
- ~~**Long Exam Mode coming-soon foundation**~~ ✅ — see Shipped below
- **Learner Level helper text** — updated inline helper text from generic "Controls difficulty, explanation depth, and quiz complexity." to "Quiz questions and explanations will better match your learning stage."; `getGroupedLearnerLevels()` added to `lib/learning-profile.ts` for future grouped picker UI (Student / Board Taker / Teacher recommendations)
- ~~**Board Exam premium UX polish (presentation-only)**~~ ✅ — see Shipped below
- ~~**Adaptive Practice tier reconciliation**~~ ✅ — aligned `docs/features/adaptive-practice.md`, `docs/features/quiz.md`, `docs/PROJECT_CONTEXT.md`, and runtime gating (`StudySnapProperties`, `application.yaml`) with `PLANS.md`: Plus = 10 sessions / mo, Pro = 30 sessions / mo; `adaptivePracticeProOnly` default corrected to `false`; open discrepancy in `EXAM_MODES.md` closed

### ✅ Shipped

- **Profile-aware mode selection UX** — mode-selection screen shows the right modes per profile: Students see Challenge Quiz + Long Exam (coming-soon); Board Takers see Challenge Quiz + Board Exam; Teachers skip mode-selection and go directly to Challenge Quiz setup; cross-profile escape hatch (`"Preparing for boards? Switch your profile in Settings"`) guides Students who want Board Exam Mode; `lib/exam-mode-visibility.ts` is the single source of truth and is fully unit-tested
- **Long Exam Mode coming-soon foundation** — Long Exam Mode card is live in mode selection for Students with a `Coming Soon` badge; clicking it opens a graceful coming-soon setup screen with a disabled `"Long Exam — Coming Soon"` CTA and a link back to mode selection; mode identity is established in the UI without a backend session; `LONG_EXAM` engine discriminator and session logic ship in v0.13.0
- **Board Exam premium UX polish (presentation-only)** — Board Exam Mode now reads as a higher-ceremony simulation without changing quiz engine behavior:
  - setup screen uses `Begin Board Exam` framing with a five-item pre-flight checklist
  - primary setup CTA is now `Begin Board Exam` while preserving existing Pro gating and mode-switching controls
  - completed Board Exams render a `Score Report` subtitle and formal score-report guidance copy
  - Board Exam results hide the inline learner-level adjustment and post-success upgrade nudge while preserving Challenge Quiz result behavior
- **Learner Level grouped picker on quiz result screens** — Quick Review and Challenge Quiz results now split inline learner-level chips into profile-aware recommended levels and `Other Learning Styles`, preserving the existing chip save behavior while keeping Board Exam results free of learner-level controls

- **Public Library conversion funnel polish (recommendations A–G)** — multi-part audit pass that hardens the public note detail page as a top-of-funnel acquisition surface:
  - **Related notes in quiz completion card (C)** — after finishing the Quick Check, visitors see a "More from {Subject}" section with up to 3 engagement-ranked notes from the same subject; fetched server-side via the existing 5-min cached `getServerPublicNotesBySubjectSlug` call at no extra network cost; linked via canonical public note URLs
  - **Auth-prompt consolidation (D)** — all public copy CTAs (`PublicSeoCopyCta`) now use a single `AppModal` guest-auth surface with Log In + Sign Up buttons and copy-intent redirect URLs so the user lands back in copy flow after authentication; `guestAuthMode` prop removed from all callers; dead tabbed-content component (`public-note-detail-tabbed-content.tsx`) deleted (superseded by the stacked-card SEO layout); legacy redirect routes preserved for external link compatibility
  - **Practice-mode preview teaser (I)** — new `PublicPracticeModeTeaser` static server component placed after Full Notes and before Ownership Actions on the public note detail page, gated on `!isDraft`; shows Challenge Quiz and Adaptive Practice (both free) and Board Exam Mode (Pro chip) as teaser cards to anchor platform value before the copy decision
  - **Time-decayed Featured score (G)** — `computeDiscoveryScore` (frontend `public-library-discovery.ts`) and `computeScore` (backend `PublicNotesScoringUtils`) now apply an age-decay factor: `score = baseScore × max(0.1, 1 / (1 + daysSince / 30))`; notes halve in ranking weight every 30 days; floor of 10% prevents high-engagement legacy notes from permanently outranking fresh content; both implementations accept a `now` parameter for deterministic testing; frontend and backend formulas kept in sync

- **Public Note Quick Check — multi-question preview** — the Quick Check section on public note detail pages now shows up to 3 sequential preview questions (drawn from existing Study Pack quiz data, no new AI generation); a progress indicator (`1 / 3`) tracks where the visitor is; after submitting each answer, feedback microcopy (✅ Correct!, 🧠 Nice work!, Almost there.) and a "Next Question →" button appear before advancing; the final question transitions to a lightweight completion state with CTAs to copy and start practicing; gracefully falls back when fewer than 3 questions exist; notes-first layout is preserved — Quick Check stays below Summary and Key Concepts
- **Google social login** — added Google OAuth as an alternative to email/password login and signup; verified Google emails create or link accounts without duplicating existing users; Google-only users skip separate email verification; Profile now shows simple sign-in method status and a `Connect Google` action for matching account emails; existing email/password login remains supported; foundational connected-account architecture (provider linking, `email_verified` guard, sign-in method tracking) is in place to support future provider management features (unlinking, add-password for Google-only users, multi-provider support) tracked in ROADMAP.md
- **Conversion funnel + quiz UX refinement pass** — focused pass across paywall surfaces, quiz flows, and empty states:
  - `PaywallModal` plan cards are now clickable/selectable with a ring highlight; the action area collapses to a single `Continue with [Plan]` footer CTA that updates as the user switches cards; PRO users see a calm "you're already on Pro" message with no upgrade cards; the redundant per-card PLUS/PRO buttons are removed
  - `StudyPackLimitModal` trimmed to two buttons maximum — primary upgrade CTA + `Maybe Later` for FREE/PLUS; PRO users see a single `Got It` dismiss with no upgrade options
  - `getUpgradeCtas` extended with an optional `UpgradeCtaContext` parameter (`"study-pack-limit"`, `"adaptive-practice"`, `"general"`) for context-aware copy: `Get More Study Packs` when triggered from the study-pack limit surface, `Unlock Adaptive Practice` when triggered from an adaptive-practice gate
  - Challenge Quiz progression microcopy at the last question is now progression-aware: `"Good start — want to keep going?"` at 5 questions, `"10 questions in — push to 15?"` at 10, `"Almost there — finish with all 20?"` at 15, `"You've answered all {n} questions — ready to submit?"` when `noMoreQuestions` or at the 20-question cap
  - Challenge extension toast updated from `+5 questions added` to `"Challenge extended to {n} questions"` or `"Full challenge unlocked: 20 questions"` when the session reaches the cap
  - Quick Review result screen now shows a guidance line `"Review your results, then choose your next study step."` below the result heading; the fallback retry CTA is renamed from `Practice Again` to `Retry Quick Review` for clarity
  - Empty state copy polished: Dashboard `"Start studying smarter"` / `"Add your first note, generate a Study Pack, and start quizzing in minutes."`; Library `"Your note library is empty"` / `"Create a note to get started — generate a Study Pack and quiz yourself in minutes."`
- **Guidance Foundation System** — introduced a minimal guidance engine (`lib/guidance-engine.ts`) with a `GuidanceRule` type and `pickActiveGuidance()` function for priority-ordered, dismiss-aware tip selection; added two contextual one-time tips to the Library: `library-first-note-organization` (notes 1–3) prompts users to add subject and tags, `library-organization-habits` (notes ≥ 5) encourages subject filtering; Dashboard personalization prompt (`"Too easy or too hard?"`) now suppressed for users who already have a learner level set, fixing a bug where it showed even after configuration; prompt moved to appear after the primary study action (Continue Studying / Start Board Exam / Create Teaching Material) for all three profile types so it reads as a secondary refinement rather than a roadblock
- **Retention loop — continue studying + focus areas** — targeted fixes across `DashboardService`, `ContinueSpotlight`, and `DashboardFocusAreasCard` to close the three highest-value retention gaps:
  - Continue Studying session priority reordered to Challenge Quiz → Adaptive Practice → Quick Review — a Challenge Quiz in-progress now always surfaces over a more recently created Quick Review session, matching the learning priority of the more structured mode
  - Continue Studying body copy is now mode-aware: `"You left off on Question 4 of 10 in your Challenge Quiz."` / `"…in your Adaptive Practice."` instead of the generic `"You left off on Question 4 of 10."`
  - Focus Areas action now has a free-tier fallback: when weak concepts exist but Adaptive Practice is locked, Free and Plus users see a `"Revisit Note"` link to the source note instead of only an upgrade prompt; the paywall button is shown only when no note is resolvable
  - `MEANINGFUL_STUDY_ACTIVITIES` constant deduplicated — moved to `ActivityType.MEANINGFUL_STUDY_ACTIVITIES` as a single `public static final` field; `DashboardService` and `RetentionService` both reference the shared constant

### 🐛 Fixes

- **AI subject suggestion resilience** — broad or invalid AI-suggested subjects no longer fail Study Pack generation; invalid suggestions are safely ignored while core summary, key concept, and quiz generation continues. Optional tag metadata issues such as duplicates are filtered without rolling back a valid Study Pack, and valid specific subject suggestions still flow through the normal review/apply path.
- Replaced Google-rendered personalized button with a NoteLib-styled button (outline, Google G icon, "Continue with Google") — eliminates the misleading "Continue as {name}" from appearing inside NoteLib UI; switched to the `google.accounts.oauth2.initCodeClient` authorization code popup flow so no hidden programmatic click is needed; backend now exchanges the authorization code at `https://oauth2.googleapis.com/token` (`redirect_uri: "postmessage"`) and verifies the returned `id_token` JWT, keeping the rest of the auth path unchanged
- Added unique public usernames for stable creator attribution and future creator profile links.
- Public notes now disambiguate creators with `displayName` plus `@username` while keeping display names as readable presentation.
- Login now accepts email or username without breaking existing email login.
- Profile identity settings now allow users to edit their public username.
- Fixed TypeScript type errors in test fixtures (`NoteListItemResponse`, `NotePerformanceSummaryResponse`, `PublicProfileResponse`) to align test data with updated type definitions
- **Quiz Ready badge accuracy** — Quiz Ready indicators are now profile-aware and only appear where they support Teacher/exam-export workflows; Student and Board Taker Library browsing keeps Study Pack Ready as the learner-facing readiness signal
- Fixed Study Pack generation metadata flow so note-level `courseProgram` remains the source of truth and user profile `courseProgram` is used only as a fallback
- Fixed Quiz metadata context consistency — Challenge Quiz and Board Exam now honor note-level Course/Program before falling back to profile context; Note Creation copy clarified Course/Program as domain context
- Fixed Generate from Topic first-generation Course / Program handling so the current Create Note selection is read at submit time and sent immediately, with user profile Course / Program used only when no draft value is selected; `GenerateNoteFromTopicRequest` accepts the optional `courseProgram` field
- Strengthened LLM domain binding — `buildLearnerContextBlock` now emits a domain-constraint directive when Course / Program is set, instructing the model to stay within that academic domain and avoid blending unrelated disciplines
- Added "Tailored for: [Level] · [Course / Program]" visibility line to the note editor floating footer so users can see which context will be applied before generating; an "Adjust" affordance links directly to the optional details section
- Added helper text near the Generate from Topic input: "Your Learner Level and Course / Program help tailor the generated note's depth, terminology, and examples."
- Fixed normal note-owned Study Pack generation so AI `title` / `subject` / `tags` suggestions stay transient until the user applies them
- Fixed AI Suggestions tag comparisons so overlapping user tags are not shown as duplicate new suggestions
- Onboarding keeps its explicit zero-friction metadata auto-apply behavior for empty `subject` / `tags`
- Added shareable URL-based Public Library filters on the canonical `/public/library` route, including a list-page `Share this list` action that copies the current filtered URL
- Consolidated Public Library browsing around `/public/library` and cleaned up the duplicate `/library/public` / `/public/library/{subject}` route wrappers into compatibility redirects
- Polished Public Library URL filter UX with debounced search sync, scroll-preserving filter updates, always-available tag browsing, and stable selector-modal input focus

---

## v0.11.0 - Learning Flow Foundation

### Learning Personalization Polish

- added inline learner level pill-selector to Quick Review and Challenge Quiz result screens so users can adjust their level immediately after a quiz without leaving the review flow; saving shows a toast `Learner level updated. Future Study Packs and quizzes will match this level.`
- restructured Quick Review result screen to show exactly one primary CTA: `Practice Weak Areas` when struggling and Adaptive Practice is available, `Take Another Challenge` after a strong or perfect result, `Practice Again` otherwise
- moved confidence feedback to a secondary collapsed section below the primary CTAs on Quick Review results; selecting a level replaces the option buttons with a badge — `🟢 Confident`, `🟡 Improving`, `🔴 Needs Practice`
- updated Dashboard personalization prompt to `Too easy or too hard?` / `Set your learner level so future quizzes match your study stage.` with a `Adjust level` CTA that navigates directly to the Learning Profile section of `/profile`
- Profile Settings now shows `← Dashboard` back link when reached from the Dashboard "Adjust Level" button, instead of the default public-profile back link
- "Adjust Level" CTA navigates to `/profile?from=dashboard#learning-profile` and auto-scrolls to the Learning Profile card on arrival

### Onboarding Safety

- onboarding Study Pack generation step is now idempotent: `handleStartStudyPack()` checks `draft.noteId` before creating a note and routes to step 4 if a note already exists, preventing duplicate notes from back/forward/refresh behavior
- back button is hidden while Study Pack generation is active during onboarding and the notice copy is replaced with `Your Study Pack is being created. This step can't be undone.`

### Study Pack Metadata Sync

- after Study Pack generation from an existing note, the backend now automatically applies AI-generated `subject` and `tags` back to the source note if those fields are empty — zero-friction, non-destructive, no user prompt required

### Improvements

- repositioned NoteLib as a structured learning system built around the study loop from input to mastery
- updated the landing page hero and learning-loop section to explain the flow: Create -> Understand -> Practice -> Challenge -> Improve
- added Generate Note from topic so users can draft editable notes before saving or generating a Study Pack
- improved Create Note UX with dual entry options: write your own note or generate from topic
- upgraded topic note generation so drafts are more study-ready and structured instead of stub-like filler
- added monthly note-generation limits with paid-plan-aware gating so topic drafting follows the same protection pattern as other credit-based AI actions
- refined the first-study onboarding flow so topic generation stays guided and single-use there, while the standalone New Note page keeps iterative `Generate Again` behavior
- redesigned the New Note page to focus on content creation first and moved `Title`, `Subject`, `Course / Program`, `Tags`, and teacher/admin audience selection into collapsed `Add details (optional)`
- kept Create Note and Study Pack generation low-friction by preserving profile-based defaults and allowing save/generate actions without opening optional metadata
- polished onboarding and generated-note transitions with lighter motion and better scroll-to-content behavior
- added a post-onboarding Dashboard prompt that encourages users to adjust learner level from Profile
- aligned create-note action copy around `Generate Study Pack`
- expanded manual Xendit checkout to support Plus / Pro monthly checkout and Pro yearly checkout using config-driven pricing
- fixed intro-offer voucher application so eligible first checkouts use discounted pricing and successful payments record voucher redemption history
- hardened pending checkout reuse so billing cycle, final amount, and voucher state must still match before an existing Xendit invoice is reused
- replaced legacy single-tier Premium billing with Free / Plus / Pro multi-plan model; plan state is now owned by the `subscriptions` table with one active row per user
- redesigned Settings Plan & Billing with a billing cycle toggle (Monthly / Annual) and three plan cards (Free, Plus, Pro) in a responsive side-by-side layout
- defaulted region to `PH` when the `CF-IPCountry` header is absent so checkout amounts and currency display correctly for local testing and non-Cloudflare environments
- added a cancel plan entry point in Settings for active paid subscribers
- updated product context, roadmap, spec, and release documentation for the current onboarding, billing, and plan model

## v0.10.1 - Landing & Pricing Conversion Polish

### Improvements

- **Landing page conversion polish** — refined the public homepage to reduce hesitation and drive signups:
  - hero headline updated to `Turn your notes into real study tools`
  - hero subheadline updated to `Write or upload your notes, then turn them into summaries, key concepts, and quizzes when it's time to review.`
  - hero secondary CTA changed from `Browse Public Library` to `Try Demo` (proper outline button) so the no-commitment path is front and center
  - hero trust line changed to `Free to start · No credit card required` to directly address signup hesitation
  - added a demo nudge beneath the How It Works steps: `Not ready to sign up? Try the demo first — no account needed.`
  - How It Works step copy simplified to be more direct and scannable
  - Why NoteLib differentiation rows reframed around workflow clarity rather than AI comparison
  - target user descriptions shortened; `Board exam reviewees` renamed to `Board Exam Takers`, `Teachers and tutors` renamed to `Teachers`
  - Public Library body text updated to `Browse notes shared by others. Copy them into your library and turn them into summaries, key concepts, and quizzes.`
  - Pricing Preview heading updated to `Simple pricing. Start free.` with supporting body copy
  - removed redundant `See full pricing` link from the landing pricing preview (covered by the `View Pricing` button on the Premium card)
  - removed the standalone regional pricing block from the landing pricing preview to keep the page clean

- **Pricing page refinements** — improved clarity and compliance on the full pricing page:
  - hero description simplified to `Start free for core features. Upgrade when you need more quizzes, deeper practice, and higher limits.`
  - plan subtitles updated: Free → `For everyday study`, Premium → `For focused exam preparation`
  - feature label normalised: `AI Summary + Key Concepts` → `Summary + Key Concepts`
  - added a static **Regional Pricing** block showing both currencies explicitly without geo-detection:
    - Philippines: `₱249/month` · `₱2,499/year`
    - International: `$4.99/month` · `$39.99/year`
  - added an **FAQ** section covering free access, upgrade timing, regional pricing, and Board Exam Mode
  - comparison table Adaptive Practice row now shows a checkmark instead of session count for consistency

- **PHP pricing compliance (Xendit)** — PHP pricing is now statically visible on the pricing page regardless of the visitor's region:
  - the Regional Pricing block in `PricingPlansSection` is hardcoded and does not rely on Cloudflare geo-detection headers
  - any reviewer accessing `/pricing` from outside the Philippines will still see the PHP pricing block
  - this replaces the previous approach where PHP pricing was only shown if the request region resolved to `PH`
  - intro offer line now shows both currencies statically: `Intro offer: first month ₱199 / $3.99` — previously only the visitor's regional currency was shown, causing Xendit reviewers outside PH to see USD only

- **Mobile conversion polish** — added a sticky `Start for Free` CTA on the pricing page for mobile visitors

## v0.10.0 - Profile Type System & Teacher Flow Phase 1 (In Progress)

### New Features

- **Challenge Quiz entry flow fix** — restored the shared entry screen and premium gating behavior:
  - free users who choose premium-only `Board Exam Mode` now see the premium upsell modal instead of falling into a confusing setup flow
  - free users who exhaust credit-gated `Challenge Quiz` usage now see the premium/upgrade modal immediately instead of entering quiz flow or landing on the monthly-limit page
  - premium users who exhaust `Challenge Quiz` usage still see the real monthly-limit state
  - monthly quiz-limit handling remains separate from premium-feature upsell handling
  - `Student` and `Board Taker` now both enter through the same mode-selection screen again
  - mode selection now uses persona-based default emphasis: `Student` highlights `Challenge Quiz`, while `Board Taker` highlights `Board Exam Mode`
  - the `Challenge Quiz` action on Note Detail now always routes into the shared mode-selection entry instead of dropping users into a setup screen
  - `Board Exam Mode` remains visible to free users inside mode selection and opens the shared paywall modal on click

- **Teacher Dashboard** — added a teacher-first dashboard experience without splitting Teacher into a separate product:
  - keeps the shared Study Pack / note workspace visible through `Recent Notes`
  - replaces student analytics sections with teacher-focused sections: `Create Teaching Material`, `Recently Generated Quizzes`, `Ready to Export`, and `Teacher Help / Tips`
  - links generated quizzes directly into Quiz Preview so export stays inside the teacher review flow
  - refined post-audit teacher guidance so the dashboard welcome copy is teacher-specific and the generated-quiz empty state now routes teachers to a recent ready note when no quiz preview exists yet

- **Persona-based quiz defaults** — quiz entry now emphasizes the right mode per learner intent while keeping the alternate mode reachable:
  - `Student` defaults to `Challenge Quiz` emphasis on the shared mode-selection screen
  - `Board Taker` defaults to `Board Exam Mode` emphasis on the same shared mode-selection screen
  - both personas still start from the same entry screen instead of branching into different first screens
  - `Board Exam Mode` is reinforced as Premium-only at quiz entry through the shared paywall pattern

- **Free-plan credit gating cleanup** — free users now stop at the upgrade modal for credit-gated study actions:
  - `Generate Study Pack` shows the premium/upgrade modal for Free users at `0` remaining
  - `Challenge Quiz`, `Board Exam Mode`, and `Adaptive Practice` use the premium/upgrade modal for Free users when credits or premium access block entry
  - Premium users with genuine monthly exhaustion still see the dedicated limit state instead of the Free-plan upsell flow
  - refined paywall modal messaging so quiz-limit copy now adapts to `Student`, `Board Taker`, and `Teacher` context without creating separate modal implementations
  - standardized broad plan and marketing terminology from `Challenge Quiz` to `Quiz` across pricing, landing, settings billing, and plan comparison surfaces

- **Onboarding / profile wording alignment** — persona naming is now consistent across setup and profile surfaces:
  - onboarding now uses `Board Taker` instead of `Board Exam`
  - onboarding continues to show only the active selectable personas: `Student`, `Board Taker`, and `Teacher`

- **Auth status messaging cleanup** — login messaging now stays reason-based and avoids misleading logout copy:
  - manual logout shows no status banner
  - expired sessions show `Your session expired. Please log in again.`
  - specific sign-out reasons should only be shown when they are reliably detectable; otherwise auth falls back to the session-expired message

- **Post-quiz feedback cleanup** — simplified the result-screen feedback actions:
  - keeps `Yes` and `Give Feedback`
  - removes the duplicate `Send Feedback` button from the quiz-results card
  - aligns icon / label spacing with the shared button system

- **Profile Type System** — formalises the three active profile types (Student, Board Taker, Teacher) with a controlled availability model:
  - `PROFESSIONAL` and `PARENT` are now visible in the Profile Type card but not selectable — they show a "Coming Soon" badge and remain disabled until the personas are ready
  - active types (Student, Board Taker, Teacher) use a visual card-list selector with radio indicator and one-line description instead of a plain `<select>`

- **Profile switching confirmation modal** — switching to a different profile type now requires a confirmation step:
  - modal copy is mode-specific (not generic) and explains what changes with the new mode
  - modal always includes "You can switch back anytime."
  - on confirm: saves the new type and shows a post-switch toast that auto-dismisses after 4 seconds
  - on cancel: closes without saving — selected UI state stays but no API call is made
  - toast copy is mode-specific (e.g. "You're now in Board Taker mode — focused for exam prep.")

- **Mode system (`frontend/lib/profile-mode.ts`)** — introduces a clean mode layer above profile types so shared components branch on mode, not on profile name:
  - `ProfileMode`: `"LEARNING"` (Student, Board Taker) or `"TEACHING"` (Teacher)
  - `resolveProfileMode()` is the canonical resolution function
  - `ACTIVE_PROFILE_TYPES` and `DISABLED_PROFILE_TYPES` constants centralise availability rules
  - `getProfileTypeSwitchContent()` returns mode-specific confirmation copy for each active type

- **Teacher Flow v1**
  - Added quiz generation for teachers with a note-owned `generatedQuiz` model instead of quiz sessions
  - Introduced Quiz Preview with answers and explanations visible by default
  - Moved Export into the dedicated quiz view for better context
  - added Teacher DOCX export from Quiz Preview using stored `generatedQuiz` data only — no LLM calls, no quiz session reuse
  - teacher export now supports `Quiz Only` and `Quiz + Answers` as downloadable `.docx` files
  - added teacher Library `Select` mode and `Exam Builder` for combining multiple quiz-ready notes into one ordered DOCX exam export
  - `Exam Builder` now supports handle-based drag-and-drop reordering with `@dnd-kit`, while keeping `Move up` / `Move down` controls as the accessibility fallback
  - `Exam Builder` now adds teacher-defined sections with inline titles, drag-reorderable section groups, and note movement across sections before export
  - combined exam export supports note reordering plus optional `Answer Key` and `Explanations` sections in the generated document
  - combined exam DOCX export now preserves section order and section headings from the teacher builder instead of flattening notes into one unnamed list
  - `Exam Builder` now supports both `Even Balance` and `Smart Balance` for section redistribution:
    - `Even Balance` keeps the original deterministic equal-slice behavior
    - `Smart Balance` keeps counts even while spreading note coverage, concept coverage, and soft template intent where metadata exists
  - combined exam export now uses question-level section assignments, so balanced sections export exactly the same grouped question order shown in the builder
  - finalized v0.10.0 limit-state wording so paywalls, premium exhausted states, and teacher quiz generation use consistent `limit` terminology instead of mixed `usage` copy
  - fixed teacher quiz preview regeneration to use the dedicated quiz-generation paywall context instead of the student quiz-limit context
  - refined the Send Feedback modal for mobile with a roomier bottom-sheet-style layout and safer textarea spacing
  - unified teacher export entry points so Quiz Preview and Exam Builder both open the same two-option export chooser: `Quiz Only` or `Quiz + Answers`
  - reduced teacher Quiz Preview `Regenerate` to a lighter secondary action so `Export` stays the primary CTA, especially on mobile
  - Added regeneration with credit usage and confirmation
  - Removed student-only quiz actions, performance UI, recent sessions, and Board Exam references from Teacher mode note detail

- **Final UX polish pass** — tightened the last release-blocking workflow details without changing core behavior:
  - Create/Edit Note now uses one shared sticky footer across profiles with `Save Note` and `Generate`, replacing duplicated top and bottom action clusters
  - Library now includes local readiness chips for `All`, `Quiz Ready`, and `Study Pack Ready` to make teacher quiz-ready notes easier to scan
  - readiness badges are visually separated more clearly: `Study Pack Ready` uses a neutral/blue treatment while `Quiz Ready` uses green
  - Exam Builder now uses the same export-choice wording as Quiz Preview, removes the old answer/explanation toggle combinations, and adds enough bottom spacing so the sticky footer no longer covers content on mobile
  - Exam Builder note cards now keep better mobile readability with two-line title clamping and clearer drag-state feedback on the handle and active item

- **Note target profile type system** — notes now store who they are written for separately from the creator's profile:
  - added required `notes.target_profile_type` with `STUDENT` and `BOARD_TAKER`
  - cleaned up incorrectly assigned teacher-target notes by falling back `TEACHER` -> `STUDENT` through a follow-up migration
  - `Student` and `Board Taker` note creation now auto-assign the note target profile from the current user profile without showing extra UI
  - `Teacher` and `Admin` note creation/editing now require `Who is this note for?` with `Student` and `Board Taker` options
  - post-generation metadata editing now lets `Teacher` and `Admin` change note audience without triggering regeneration; the change only affects future quiz generation
  - Public Library filtering now uses `note.targetProfileType` instead of creator profile type and offers `All`, `Student`, and `Board Taker`
  - category-empty Public Library states now guide users to `View all notes` when no notes exist yet for the selected audience

- **Loading-state system** — standardized the app’s loading feedback for async actions, delayed redirects, and fetched sections:
  - shared `Button` loading state now shows one consistent spinner treatment and disables duplicate clicks while requests are pending
  - mounted a subtle top route-progress indicator so delayed programmatic navigation no longer feels unresponsive
  - applied the shared pending pattern to auth submit, profile/settings saves, sign-out, teacher quiz generation/regeneration, export actions, and waitlist/paywall actions
  - standardized high-visibility skeletons across dashboard, profile, settings, generated-quiz preview, strongest-notes, and public-library loading states
  - tightened duplicate-action protection so repeat taps during in-flight async work do not create confusing extra requests

- **Challenge Quiz Note Detail entry hardening** — fixed the recurring routing drift from Note Detail into quiz setup:
  - the Note Detail `Challenge Quiz` button now always enters through the shared initial mode-selection screen for both `Student` and `Board Taker`
  - `Student` still defaults to `Challenge Quiz` emphasis there, while `Board Taker` still defaults to `Board Exam Mode`
  - the challenge-quiz page now treats the shared mode-selection entry as the single source of truth and no longer lets session-recovery logic bypass it into setup or running state

- **Action-aware paywall and exhausted messaging system** — unified contextual messaging for free-user gating and premium-user limit states across all supported actions:
  - new shared `frontend/lib/paywall-content.ts` centralises copy for five action contexts: Study Pack, Quiz, Board Exam, Quiz Generation, and Adaptive Practice
  - `FREE_PAYWALL_CONTENT` maps each `PaywallAction` to title, body, analytics feature string, and dismiss label — imported by `PaywallModal` so all free-user gating uses the same content rules
  - `PREMIUM_EXHAUSTED_CONTENT` maps each `PaywallAction` to title and body — imported by premium limit-reached states so exhausted messaging is action-specific and not generic "Monthly limit reached"
  - **PaywallModal** (`components/billing/paywall-modal.tsx`): removed inline per-variant content constants and the profileType-based `resolveQuizLimitMessage()`; now resolves copy through `resolvePaywallAction(variant)` → `FREE_PAYWALL_CONTENT[action]`; keeps inline fallback content for `difficulty-selection` and `ocr-limit` which are out of scope for action-aware copy
  - **New variant `quiz-generation-limit`** added to `PaywallModalVariant` — maps to `QUIZ_GENERATION` action; used when Teacher (Creator mode) hits the quiz generation limit, giving a distinct title ("You've reached your quiz generation limit") separate from the student quiz limit ("You've reached your quiz limit")
  - **Teacher quiz generation gating** in Note Detail now uses `"quiz-generation-limit"` instead of `"challenge-quiz-limit"` for accurate action context; analytics source strings updated to `private_note_detail_teacher_quiz_generation_limit`
  - **Challenge Quiz page `limit-reached` card**: heading updated from generic "Monthly limit reached" to "You've used all your quiz credits for this month" with reset cycle body copy
  - **Adaptive Practice page `limit-reached` card**: heading updated from "Monthly limit reached" to "You've used all your quiz credits for this month" with Adaptive Practice-specific body copy
  - **`StudyPackLimitModal`**: free plan and premium plan titles and body copy updated to match `FREE_PAYWALL_CONTENT.STUDY_PACK` and `PREMIUM_EXHAUSTED_CONTENT.STUDY_PACK` respectively; reset date is still surfaced when available
  - `resolvePaywallFeature()` in Note Detail updated to handle all seven current `PaywallModalVariant` values including the new `quiz-generation-limit` and `ocr-limit`
  - back navigation in limit-reached cards uses short destination label `Note` per Back Navigation Rule

### Documentation

- `docs/product/SPEC.md`: updated teacher dashboard purpose, teacher quiz separation, persona-based quiz defaults, and auth/login message rules
- `docs/features/dashboard.md`: documented the teacher-first dashboard sections and profile-specific priorities
- `docs/features/authentication.md`: documented session-expired vs manual-logout messaging rules
- `docs/features/teacher-flow.md`: clarified how Teacher Dashboard feeds the Generate -> View -> Export teacher lifecycle
- `docs/product/ROADMAP.md`: added Public Library persona filtering as a future direction
- `docs/product/SPEC.md`: documented note target profile ownership, teacher audience selection, and Public Library audience filtering defaults
- `docs/features/public-library.md`: documented note-audience rails and category-empty-state behavior
- `docs/features/study-library.md`: documented note target audience assignment during note creation and copy behavior

---

## v0.9.0 - Learning Experience & Product Polish (In Progress)

### New Features

- **In-App Guidance System** — lightweight contextual guidance helps users understand features without blocking or overwhelming:
  - micro-guidance text added to key form fields: Subject and Course / Program on the note editor explain what each field does; Course / Program on the Profile page explains how it affects recommendations
  - quiz mode description line added below the Study Pack action buttons on Note Detail explaining the difference between Quick Review and Challenge Quiz
  - `GuidanceTip` component: a subtle, dismissible one-time tip strip backed by `localStorage` — fades in on first visit, dismissed permanently with a single click
  - first-time-quiz nudge on Note Detail Performance Overview: when a Study Pack is ready but no quiz sessions exist, a tip prompts the user to try Quick Review or Challenge Quiz
  - Help Center page at `/help` refactored into a card-based layout matching the Learn page design: six topic cards (Getting Started, Creating Notes, Study Packs & Quizzes, Performance & Insights, Export & Sharing, Profile & Settings) plus a Student Guide card linking to `/learn` and a support footer; clicking any topic opens a modal with detailed Q&A without leaving the page
  - Help is accessible from the avatar dropdown menu (next to Settings) and from a "Help Center" link in the Settings page header
  - `"help"` added as a new `ActionIconName` using the `HelpCircle` icon from Lucide
  - **Design token system** — established a lightweight, semantic token layer on top of the existing Tailwind v4 + CSS custom properties infrastructure; added four new tokens: `--primary` / `--primary-hover` / `--primary-active` (brand color with correct light/dark values) and `--surface-alt` (card surface background); mapped to Tailwind utilities via `@theme inline` so `bg-primary`, `hover:bg-primary-hover`, `active:bg-primary-active`, `bg-surface-alt` work without `dark:` prefixes — dark mode is handled entirely by CSS variable substitution, making the system theme-ready; updated `button.tsx` default variant (7 hardcoded blue classes → 3 semantic tokens), updated `card.tsx` surface background, updated step circles in all three guide modals; documented full token table, radius/spacing/shadow conventions, and token usage rules in `docs/product/SPEC.md`
  - **UI system — card hierarchy and icon standardization** — audited card and icon usage across Dashboard, Help, Library, Note Detail, and guide modals; result: card hierarchy is now documented and intentional (Primary Action / Secondary Info / Content / Inner Utility levels); icon container system standardized across all six guide modal components — section icon containers (`h-7 w-7 rounded-lg border border-border bg-muted/40`) now consistently use `h-4 w-4` icons instead of the prior mixed `h-3.5 / h-4` split; page-level Help card icons (`h-8 w-8` container) remain intentionally larger to maintain hierarchy between page cards and modal inner sections; card padding, border, and hover patterns confirmed consistent across the app; card hierarchy and icon rules documented in `docs/product/SPEC.md`
  - **Help modal content refactor** — all Help Center modals now use structured, scannable layouts instead of plain Q&A text blocks: Creating Notes (4 sections: note content, Subject/Course fields, post-generation editing, Make a Copy), Study Packs & Quizzes (6 sections: Summary, Key Concepts, Quick Review, Challenge Quiz, Adaptive Practice with Premium badge, Weak Concepts), Export & Sharing (3 sections: export options with export type bullets, file format, public sharing); each section uses an icon badge, title, 1–2 sentence description, optional bullet list, and optional CTA link; `HelpCard` type simplified — `items` array removed, `modalDescription` added as optional field; modal render delegates to a `GuideContent` switch component instead of a generic Q&A fallback
  - **Help page card cleanup** — reduced Help Center from 8 cards to 6 by removing Performance & Insights and Profile & Settings (not primary help needs); remaining cards are Getting Started, Creating Notes, Study Packs & Quizzes, Export & Sharing, Student Guide, and Teacher Guide; 6 cards now fill a clean 2×3 grid on desktop with no orphaned rows
  - **Modal step alignment fix** — connector lines in step-based guide modals (Getting Started, Student Guide, Teacher Guide) now use `flex-1` instead of `h-full` so the line reliably extends to the bottom of each step card; gap between circle and connector adjusted to `mt-1.5` for consistent vertical rhythm across all screen sizes
  - **Teacher Guide** added to Help Center: a 4-step workflow (Add Lesson Material → Generate Study Pack → Review the Output → Export for Reuse) explaining how teachers can use NoteLib today to turn lesson notes into quiz-ready study packs and exportable review PDFs; includes an honest "where NoteLib fits today" note scoped to current capabilities, plus practical tips; uses the same step-card modal pattern as the Student Guide and Getting Started guide
  - **Progressive in-app hints** — three new one-time `GuidanceTip` placements using the existing localStorage-backed dismissal system: (1) Note Detail draft state — shows when a note has no Study Pack and is ready to generate, message explains what unlocks after generation; (2) Session History empty state — shows when no quiz sessions exist, message explains that completing a session unlocks review and PDF export; (3) Public Library — shows on first visit, message explains how to copy notes into your own library; all three are automatically hidden after dismissal and never shown again
  - **AppModal scroll and close usability** — modal panels that contain long content (Student Guide, Getting Started) are now fully accessible without scrolling the page: panel is capped at `90dvh` with `overflow-hidden flex-col`, the content area scrolls independently with `overflow-y-auto`, and header/actions stay fixed; an always-visible X close button is rendered in the top-right corner of every modal, regardless of whether `headerActions` are provided, making it easy to dismiss on both desktop and mobile

### Bug Fixes

- **Manual logout redirect no longer polluted by route guard** — fixed a race condition where logging out from Note Detail could land on `/login?redirect=...&reason=auth_required` instead of the clean `/login?reason=logged_out`: after `clearAuthUser()` emits `studysnap-auth-change`, Note Detail's auth re-check called `redirectToLoginWithCurrentDestination` before the logout navigation completed, overwriting the logout-initiated redirect; fix: `redirectToLoginWithCurrentDestination` in `route-guards.ts` now checks `isManualLogoutInProgress()` and returns early when a manual logout is in progress — the logout handler remains the sole owner of that navigation; new test added to `route-guards.test.ts` verifying the route guard does not redirect during manual logout

- **Note Detail context menu no longer floats into the top bar** — the three-dot menu trigger was positioned with `absolute right-4 top-4 z-10` inside a `relative` Card, causing it to visually overlap the sticky page header during scroll and appear disconnected from the title row; restructured to inline: trigger now lives inside a `flex items-start gap-3` row alongside the title div, using `relative shrink-0 self-start` — no more `absolute` outer wrapper or `pr-14` title padding; the dropdown panel remains `absolute right-0 top-12 z-20` relative to the trigger container, which is correct

- **Auth redirect safety after session expiry on shared devices** — fixed a cross-account redirect vulnerability where a different user logging in after a session expiry was redirected to a protected resource owned by the previous user: `handleUnauthorizedSession` now stores the expired user's ID in `sessionStorage` before clearing auth; `resolvePostLoginDestination` for `reason=session_expired` now only follows the saved redirect when the newly logged-in user ID matches the stored expired user ID — any other user lands on the dashboard; the stored ID is cleared on every successful `setAuthUser` call; logout behavior (`reason=logged_out`) is unchanged — it never includes a redirect param and always returns to the dashboard; 5 new tests added covering same-user restore, different-user block, no-stored-ID fallback, setAuthUser clearing, and handleUnauthorizedSession storage

- **Quiz session history + review** — Note Detail now keeps completed quiz history tied to the note so past practice is reviewable instead of disposable:
  - adds a `Recent Sessions` section below `Performance Overview` on Study Pack-ready notes, combining Quick Review and Challenge Quiz attempts in reverse-chronological order
  - `Review session` now opens one dedicated session-review page on both desktop and mobile for a clearer and more stable interaction model
  - removes the fragile desktop inline review and auto-scroll behavior in favor of one consistent route from `Recent Sessions`
  - the dedicated review page gives score summary, weak concepts, questions, and explanations enough width to stay readable across screen sizes
  - Session Review now includes a structured `Export` action with three options grouped under `Review Materials`:
    - `Full Review` — all questions with answers, explanations, and score summary; filename `notelib-quiz-[title]-[date].pdf`
    - `Mistakes` — incorrect answers only with focused `Mistakes Review` section, mistake count, accuracy, and weak concepts; handles perfect-score edge case with `Perfect Score!` message; filename `notelib-mistakes-[title]-[date].pdf`
    - `Weak Concepts` — questions from identified weak concept areas with `Weak Concepts Review` section and `Questions from Weak Areas` list; handles no-weak-concepts edge case gracefully; filename `notelib-weak-concepts-[title]-[date].pdf`
  - desktop Export shows a compact grouped dropdown; mobile shows a bottom sheet with title, subtitle, large tap targets, and Cancel
  - the bottom sheet uses a slide-up entry animation (`motion-export-panel`) that switches to the standard dropdown animation on `sm+` via CSS media query
  - exported PDFs use note title, quiz type, generated date, and subtle `Generated by NoteLib` footer — all built from stored session data without LLM calls
  - lets users open a completed session using stored session data only for question-by-question answers, explanations, and correctness
  - derives concept breakdown and weak concepts from persisted quiz/session state, keeping the weak-concept threshold aligned at `< 60%`
  - older sessions without enough stored quiz detail degrade gracefully with concept summary and weak-concept feedback instead of failing the page

- **Library filtering and search upgrade** — the private Library now behaves more like a structured study workspace:
  - keeps search as the primary entry, filtering Library notes in real time by title and tags
  - uses subject-first horizontal scroll chips with single-select `All` default so the main filter stays fast and lightweight
  - limits tags to a compact `Popular Tags` rail with `+ More` progressive disclosure instead of exposing the full tag list by default
  - opens searchable subject and tag selectors in the shared bottom-sheet/modal pattern, with sticky search, `Apply`, and `Clear` actions
  - tag selector now shows selected tags in a dedicated top section so users can quickly deselect without rescanning the full list
  - Library multi-select tags now use OR logic by default so combining tags from different notes broadens results instead of creating false empty states
  - notes missing an explicit subject still derive a temporary fallback subject from existing metadata so subject grouping works consistently

- **Landing page Public Library preview** — the homepage now visually demonstrates the Public Library experience instead of relying on copy alone:
  - refined the section into a responsive text-and-preview layout so the screenshot supports the message instead of dominating the page
  - uses `public/landing/feature-public-library.jpg` inside a framed product-preview container with constrained height, rounded corners, and subtle depth
  - keeps the preview balanced across desktop and mobile with text-first stacking on small screens

- **Performance by Note on Profile and Dashboard** — replaced the flat "Best Sessions" list with a note-grouped performance view that shows how well the user knows each note:
  - Profile "Top Performance by Note" card groups all QUICK_REVIEW and CHALLENGE sessions by note, computing best score, average score, attempt count, and last attempted date per note; sorted by best score DESC
  - each row shows `⭐ Perfect` (100%) or `Top Score` (≥80%) badge, note title, best/average percentages, attempt count, and last attempted date
  - clicking any note opens the Session Review page for the best session on that note, with back navigation returning to the Profile page
  - Dashboard "Strongest Notes" section shows the top 3 notes by best score with a "View all" link to the Profile page; back navigation from those sessions returns to the Dashboard
  - Session Review back link is now source-aware: navigating from Profile shows "← Profile", from Dashboard shows "← Dashboard", from the note page shows "← Note"
  - backend: `GET /dashboard/note-performance?limit=N` replaces `/dashboard/best-sessions`; groups sessions by noteId in service layer, returns `NotePerformanceSummaryResponse` with bestScore, averageScore, attemptCount, lastAttemptedAt, bestSessionId, bestSessionMode, and noteTitle

- **Public Library evaluation signals** — public notes now expose a lightweight like system so note quality is easier to judge without turning discovery into a social feed:
  - authenticated users can toggle one like per public note, with likes stored per user-note pair and duplicate likes prevented server-side
  - Public Library cards now show a subtle heart count beside the existing view/copy signals, and guests who tap like see an auth prompt modal instead of a silent failure
  - Featured ranking now uses `viewCount + (copyCount * 3) + (likeCount * 2)` while Most Popular keeps copies first, views second, and uses likes as the next tie-breaker
  - public note cards can now show a lightweight `❤️ Well liked` badge when a note reaches the like threshold, keeping the evaluation model simple and student-facing

- **Dedicated How it Works page** — product walkthrough content now has its own public route at `/how-it-works`:
  - the new page explains the full NoteLib flow with real screenshots for note editing, Study Pack generation, quiz practice, and results review
  - `/how-it-works` also includes the simple 3-step overview, Board Exam Mode highlight, and a closing signup CTA
  - the walkthrough reuses the shared optimized screenshots from `public/landing`
- **Landing page screenshot integration** — the public homepage now shows real product UI instead of abstract product illustrations:
  - hero now uses the real note-editor screenshot to ground the product story immediately
  - the homepage walkthrough has been simplified so the detailed multi-screenshot explanation now lives on `/how-it-works`
  - landing screenshots now share one polished treatment: rounded corners, soft shadows, preserved aspect ratios, and subtle hover scale
  - the public navbar and footer now surface `How it Works` so deeper product guidance is easier to find
- **Theme system refresh** — NoteLib now supports `Light`, `Dark`, and `System` theme modes as the first polish feature of `v0.9.0`:
  - `Settings > Preferences` now includes a dedicated theme selector instead of relying only on a utility toggle
  - `Settings` keeps the always-visible inline `Light` / `Dark` / `System` segmented selector for fast preference changes
  - the shared top-bar theme control now uses a simpler responsive inline pattern:
    - desktop shows a compact always-visible icon-only theme group with tooltips
    - mobile keeps a compact trigger that expands an inline theme panel
  - the top-bar `System` option now uses a monitor-style icon on desktop and a phone-style icon on mobile
  - the mobile top-bar theme control no longer relies on the unstable popup rendering path and now expands cleanly without clipping into the header area
  - the desktop top-bar theme control is now more compact, with tighter icon sizing and spacing while keeping the same theme actions
  - the public header now uses a subtle separator between the theme utility group and the `Login` / `Get Started` actions
  - `System` follows the device `prefers-color-scheme` setting and updates while the app is open
  - theme choice persists locally and also syncs through the existing authenticated theme-preference API when available
  - initial theme classes are applied before the main UI renders to avoid flashing the wrong theme on load
  - theme changes now use subtle color-only transitions for background, text, and borders
- **Motion system foundation** — NoteLib now uses a tighter shared motion language for polish without slowing study flows:
  - shared motion tokens now live in the global CSS layer so durations and easing stay consistent
  - cards and shared buttons now use lightweight surface and pressed-state motion utilities instead of one-off transition values
  - the Challenge Quiz Question Navigator now uses a shared collapse/expand motion pattern instead of abrupt mount/unmount behavior
  - Quick Review and Challenge / Board Exam result-review surfaces now use gentle fade-in entry motion for calmer state changes
  - quiz-critical interactions such as answer selection, timer updates, and question progression intentionally avoid extra motion so focus and responsiveness stay intact
- **Review Answers UX polish** — Challenge Quiz review is now clearer and more learning-focused without changing scoring or quiz generation:
  - Review Answers now starts with a compact summary for correct count, total questions, percentage, performance level, and weak concepts
  - each reviewed question now shows explicit `Correct` / `Incorrect` state plus `Your Answer` and `Correct Answer` summaries before the choice list
  - answer review now supports `All Questions` / `Incorrect Only` filtering alongside per-question explanation toggles and shared `Expand All` / `Collapse All` controls
  - explanation disclosure uses the shared lightweight motion system to avoid abrupt layout jumps
  - Challenge Quiz review now ends with clearer next-step actions such as `Practice Weak Concepts` and `Review Study Pack`
- **Local quiz-generation mock mode** — local development can now exercise quiz UIs without burning real LLM tokens:
  - `QUIZ_GENERATION_MODE=mock` stubs only Challenge Quiz, Adaptive Practice, and Board Exam generation while leaving Study Pack generation on the normal provider path
  - mock mode still preserves normal quiz session creation, `GENERATING` / `IN_PROGRESS` flow, idempotent reuse, result handling, and review-answer compatibility
  - `QUIZ_GENERATION_MOCK_DELAY_MS` can add a small local-only delay to test generation overlays and loading states more realistically
  - production remains on the real quiz-generation path unless the quiz-specific mock flag is explicitly overridden

### Fixes

- **Public Library evaluation system audit + trust signal refinements** — audited all evaluation signals, ranking logic, and metric display against the intended philosophy; preserved all working logic and refined only what was inconsistent:
  - audited views/copies/likes display, badge system, discovery ranking, and zero-value handling — all core logic preserved with no threshold or formula changes
  - confirmed zero-value rules: views and copies already hidden at 0 on cards; like count now also hidden at 0 so the heart button shows only when engagement exists, aligning all three metrics under one rule
  - resolved emoji ambiguity: Featured Notes section uses ⭐ (quality signal, aligns with High Quality badge) and Most Popular section uses 🔥 (social proof signal, aligns with Popular badge) — previously both used 🔥
  - Featured Notes now rank only study-ready public notes with meaningful summary, quiz content, and note preview using `views + (copies * 3) + (likes * 2)`
  - Most Popular now requires real social proof (`copies >= 3` or `views >= 20`) and the Popular badge threshold now matches that rule
  - Recently Added remains freshness-based with `createdAt DESC`
  - badge priority rules (High Quality > Well liked > Popular, max 2 per card) confirmed correct and unchanged
- **Public Library copy-flow cleanup** — public note copying now behaves consistently across discovery and detail surfaces:
  - Public Library now keeps search first, moves subject and tag browsing into compact horizontal rails, and uses searchable `+ More` selectors so filtering scales without vertical clutter
  - Public Library multi-select tags now use OR logic by default so combining tags broadens results instead of creating false empty states
  - Public Library cards now use a smaller inline `Save` action with iconography, guest auth prompt modal, and muted `Saved` state instead of the old full-width copy CTA
  - mobile card presentation is tighter and more scan-friendly, with compact preview spacing, limited tags, and metadata plus action aligned in one footer row
  - repeated copies of the same public note by the same user now reuse the existing copied note instead of creating duplicate drafts
  - successful public copies now use a more polished success surface with stronger title hierarchy, subtle success iconography, right-aligned desktop actions, and cleaner mobile sheet spacing
  - copied private notes now show `Copied from {title} in Public Library.` attribution when source metadata exists
- **Private Note Detail action cleanup** — secondary note-management actions no longer compete with study actions inside the note header:
  - inline `Edit`, `Delete`, `Make a Copy`, and `Share` controls were consolidated into a single top-right `⋯` context menu
  - the `⋯` trigger is now anchored to the card corner on both mobile and desktop instead of sitting inside the metadata flow
  - the note detail card now keeps primary study actions visually dominant while still exposing full note management
  - the shared menu works on both mobile and desktop, closes on outside click, and avoids the old multi-row utility-button clutter
- **Feedback launcher sticky-CTA conflict fix** — the floating `Send Feedback` button is now hidden on routes with sticky or fixed bottom primary actions, including Note Editor and in-progress quiz flows, so it no longer overlaps `Generate`, `Next`, `Submit`, or similar bottom CTAs on mobile.
- **Feedback UX cleanup for core study flows** — feedback entry is now more intentional across learning surfaces:
  - core authenticated learning routes now use a subtle header feedback icon instead of the floating launcher
  - the floating launcher remains only on safe non-critical authenticated pages such as Dashboard, Library, Public Library, and Settings
  - quiz result screens now ask `Was this quiz helpful?` with lightweight `Yes` and `Give Feedback` actions before the deeper review feedback panel
- **Unified animation and interaction system** — all UI interactions now follow one consistent timing, easing, and feedback language across the app:
  - `--motion-duration-fast` tuned to 150ms so all quick interactions stay within the 120–180ms spec
  - new `motion-dropdown-panel` CSS utility animates all dropdown and context menus with a fade-in + 6px slide-down entry (150ms ease-emphasized) — applied to the note actions menu, visibility menus, export menu, avatar menu, combobox listbox, and mobile nav panel
  - new `motion-lift` CSS utility adds a subtle 1px hover lift (`translateY(-1px)`) to small interactive elements, correctly suppressed during press and for disabled elements — applied to all dropdown/context menu items across the app
  - `motion-pressable motion-lift` applied to filter chips (Public Library) and the like badge for press scale + hover lift on pill-shaped interactive elements
  - theme selector unselected buttons gain `motion-lift` hover lift for consistent feel within the control
  - `prefers-reduced-motion` block covers all new utilities so users with motion sensitivity see zero animation
- **Theme-aware highlight and interaction system** — all hover, active, and selected states now use primary-color-tinted tokens instead of opaque grey values, giving a consistent blue-tinted interaction language that adapts cleanly to light and dark themes:
  - new `--highlight` token (`rgb(37 99 235 / 0.08)` light, `rgb(59 130 246 / 0.08)` dark) drives all hover highlight states; `--highlight-strong` (`0.15` opacity) drives active/pressed states and selected chip fills
  - new `--muted` token (`#e5e7eb` light, `#374151` dark) registered in `@theme inline` so `bg-muted/*` utilities now generate CSS where previously they were silently invisible
  - all `hover:bg-muted/*` and `active:bg-muted/*` classes across every component replaced with `hover:bg-highlight` / `active:bg-highlight-strong`; replaced in 17 files covering sidebar, avatar menu, navbar, filter chips, export menus, context menus, dropdown lists, session history, card hovers, visibility menus, theme toggles, combobox options, and the feedback widget
  - `Button` `outline` and `ghost` variants now use `hover:bg-highlight` / `active:bg-highlight-strong` and drop the explicit `dark:hover:bg-gray-*` overrides since the token already handles dark mode
  - active sidebar nav links use `bg-highlight-strong` instead of a hardcoded `bg-blue-600/15 dark:bg-blue-500/20`; active public navbar links use `bg-highlight` instead of `bg-blue-600/10 dark:bg-blue-500/15`
- **Interactive element feedback polish** — tap and hover feedback is now consistent across all interactive surfaces so nothing feels unresponsive on touch or desktop:
  - all three `Button` variants (`default`, `outline`, `ghost`) now carry explicit `transition-colors` and `active:` pressed states
  - destructive confirm button in Delete modal gets a red `active:` state consistent with the danger intent
  - AI Suggestion modal radio labels now show a highlight `active:` press state alongside the existing hover
  - Public Library Like badge (`unlike` state) has a tap state matching other badge interactions
  - Theme selector, filter chips, source filter labels, sort sheet options, and mobile nav links all carry consistent tap feedback
- **Mobile note header overflow fix** — long private note titles no longer push `Edit` / `Delete` outside the header card on small screens:
  - mobile Note Detail now stacks the title above the action row
  - `Edit` and `Delete` stay inline again from `sm` upward so desktop layout remains unchanged

## v0.8.0 – Board Exam Mode + Public Library Discovery System (In Progress)

### New Features

- **Pricing page + Premium positioning** — `/pricing` now presents a cleaner two-plan comparison for the current pre-launch stage:
  - Free and Premium are the only visible plans
  - Free highlights `10` Study Packs/month, `5` Challenge Quizzes/month, AI Summary + Key Concepts, Weak Concepts tracking, and `Board Exam Mode (Free for limited time)`
  - Premium highlights higher limits, Adaptive Practice, Difficulty selection, and Board Exam Mode
  - the comparison table now focuses on the core study features users actually choose between
  - upgrade CTAs still route into the Premium waitlist flow instead of payment
- **Post-Quiz UX Polish** — Unified quiz result UX across Quick Review, Challenge Quiz, and Adaptive Practice:
  - Removed all "Note" buttons from quiz screens; replaced with `← Back to Note` text link placed **below** action buttons (not grouped with them)
  - Quick Review confidence feedback: selecting a confidence level now replaces the option buttons with a styled badge — `🟢 Confident` (HIGH), `🟡 Improving` (MEDIUM), `🔴 Needs Practice` (LOW); "Thanks for the feedback." text removed
  - Adaptive Practice result screen: "Generate New Set" is now the primary action; "Note" button removed
  - Challenge Quiz result screen: "Practice Weak Concepts" is now primary (when present); "Start Another Challenge" and "Review Answers" are secondary; "Note" button removed
  - Review Answers now uses a shared learning-focused layout across Quick Review, Challenge Quiz, and Adaptive Practice with selected/correct answer badges, concept chips, visible explanations, Previous/Next navigation, and an `Incorrect only` filter for missed questions
  - Final result-flow alignment pass: Quick Review now promotes `Practice Again` when weak practice is locked, Challenge Quiz promotes `Start Another Challenge` when no weak concepts exist, Adaptive Practice shows a clear empty targeted-weak-areas state, and quiz edge states use text-link `← Back to Note` navigation instead of navigation buttons
  - Adaptive Practice `completionMessage` upgraded to use `mapPerformanceLevel` 4-tier thresholds (Excellent / Good / Fair / Needs Improvement) instead of a 2-tier check
  - Error cards in Adaptive Practice (error, premiumLocked, prestart) no longer have a redundant "Note" button — the persistent `← Note` BackLink at the page header handles navigation
- **Board Exam Mode (Phase 1)** — Challenge Quiz is now a true exam experience:
  - Challenge Quiz now opens with explicit mode selection between `Challenge Quiz` and `Board Exam Mode` instead of auto-starting or inferring exam mode from Premium-only capabilities
  - Challenge Quiz entry is now split into `Mode Selection` then `Prescreen`, so both `Challenge Quiz` and `Board Exam Mode` explain their setup before generation starts
  - `Challenge Quiz Setup` now shows timer, question-count, and attempt-usage summaries for all users; Premium users get live difficulty controls, while Free users see a recommended `Medium` difficulty plus subtle Premium upsell copy
  - Board Exam Mode is available on both Free and Premium plans and uses the same Challenge Quiz credit/quota rules during the current rollout stage
  - Board Exam Mode now has a formal `Board Exam Setup` prescreen with exam description, strict-timer summary, rule summary, `Cancel`, `Start Exam`, and best-effort fullscreen focus entry
  - Board Exam Mode now explains its distraction-free restrictions before the exam starts, confirms start explicitly, reinforces `Exam in progress` during the session, and uses more formal result framing so hidden navigation does not feel like a bug
  - Board Exam Mode no longer shows difficulty selection in the UI and now always uses mixed difficulty with the fixed exam question count
  - No correctness feedback during answering — answer first, see results later
  - Board Exam answering UI now uses a more neutral, formal presentation than the standard Challenge Quiz screen
  - Board Exam timer is now hardened around persisted session timing, low-time warning states, refresh-safe recovery, and one-shot timeout submission
  - Timer resumes from persisted session state after refresh and auto-submits when time runs out; manual submit remains available from the last question
  - Neutral question-number navigation lets users move through the exam without revealing correctness
  - Result screen keeps shared recovery actions and Review Answers, but now uses more formal Board Exam framing plus `Take Another Board Exam`
  - "Practice Weak Concepts" CTA (→ Adaptive Practice) shown only when weak concepts exist
  - All result statistics are derived from session data only — no LLM calls
- Public note cards in the Public Library, Public Profile, and public subject pages now show **quality indicator badges** (at most 2 per card) to help users quickly identify strong notes:
  - ⭐ **High Quality** — `copyCount >= 5 AND viewCount >= 10`
  - 🔥 **Popular** — `copyCount >= 10 OR viewCount >= 20` (shown only when High Quality is not already displayed)
  - Badges appear below the title; layout is mobile-safe and capped at 2 to avoid clutter. Private Library cards never show quality badges. The "New" badge has been removed.
- **Note card badge layout standardized** across all note-list surfaces (Library, Public Library, Public Profile, subject pages):
  - TOP ROW (above title): Subject badge (blue) + Course/Program badge (neutral/gray)
  - TITLE
  - BELOW TITLE: Study Pack Ready badge (green, only when applicable) + quality badges
  - `SharedNoteCard` props updated: `metaLine` removed, `courseProgram` (string) and `stateBadge` (ReactNode) added
- Public Library now has a **discovery mode** that replaces the flat note list with curated sections when no search or filters are active:
  - 🔥 **Featured Notes** — top 6 notes ranked by a weighted engagement score: `(views × 0.4) + (copies × 0.5) + (shares × 0.1)`. Tiebreak by newest first.
  - 📈 **Most Popular** — top 6 notes by copy count then view count, excluding notes already in Featured.
  - 🆕 **Recently Added** — top 6 newest notes, excluding notes already in Featured or Most Popular.
  - 📚 **Browse by Subject** — clickable chips of all unique subjects sorted by note count. Clicking a chip applies the subject filter and switches to filter mode.
- Sections are deduplication-safe: each note appears in at most one section per page load.
- Discovery mode is automatically hidden when the user types a search query, changes any filter, or selects a non-default sort option — switching seamlessly to the existing filter/sort list.

### Technical Changes

- Quick Review, Challenge Quiz, and Adaptive Practice test suites extended with post-quiz UX tests: no "Note" button on result screens, "← Back to Note" link present, confidence badge rendering (HIGH/MEDIUM/LOW), confidence option buttons hidden after selection, "Generate New Set" as primary on Adaptive Practice result, "Start Challenge Quiz" CTA on perfect Quick Review score, and Review Answers coverage across all quiz modes.
- Added `frontend/components/study-pack/quiz-answer-review.tsx` as the shared Review Answers surface for selected-vs-correct answer states, concept linking, visible explanations, and sequential review navigation. Covered by component tests plus Quick Review, Challenge Quiz, and Adaptive Practice page integration tests.
- Added a shared active quiz session guard for Quick Review, Challenge Quiz, and Adaptive Practice. Active sessions now block app route clicks, browser back navigation, and refresh/reload attempts with a shared `Leave quiz?` confirmation before users can forfeit and leave.
- Added explicit quiz session forfeits with `FORFEITED` status for Quick Review, Challenge Quiz, and Adaptive Practice. Challenge Quiz and Adaptive Practice forfeits do not refund consumed quiz credits and are not marked completed.
- Centralized quiz choice prefix cleanup so generated and legacy payloads strip hardcoded leading labels such as `A. ` and `B) ` before validation/storage; the frontend also strips legacy prefixes defensively before rendering dynamic choice letters.
- Study Pack generation from notes now runs asynchronously: Note Editor saves first and redirects immediately to Note Detail, which shows `GENERATING`, polls lightly until `STUDY_PACK_READY` or `FAILED`, and exposes `Retry Generate` without consuming quota on failed attempts.
- Challenge Quiz start now locks difficulty controls and the Start button immediately to prevent duplicate start requests or difficulty changes while initialization is in flight.
- Quick Review reload/start is now guarded against repeated fetch/redirect initialization loops without adding any LLM-specific lock behavior.
- Challenge Quiz and Adaptive Practice now reserve `GENERATING` sessions before LLM execution, return existing `GENERATING`/`IN_PROGRESS` sessions idempotently, and allow retry after `FAILED` without duplicate LLM calls from double-clicks, refreshes, or multiple tabs.
- Challenge Quiz and Adaptive Practice now show a full-screen `Generating your quiz...` lock during LLM generation, blocking app links, sidebar/header navigation, browser back, and refresh/reload until the session becomes `IN_PROGRESS` or `FAILED`.
- Enhanced quiz generation loading UX for Challenge Quiz and Adaptive Practice with pulsing AI-style dots, clearer personalized-question copy, and calm rotating progress messages while preserving the strict interaction lock.
- Adaptive Practice now checks existing session state on page load and starts new LLM generation only from the visible `Start Adaptive Practice` / `Generate New Set` actions.
- Added `frontend/lib/challenge-quiz-results.ts` with pure result computation utilities: `computeScore`, `mapPerformanceLevel`, `computeConceptBreakdown`, `computeWeakConcepts`, and exported `WEAK_CONCEPT_THRESHOLD = 60`. Challenge Quiz page now uses `computeScore` in `handleSubmit` instead of an inline reduce. Covered by 31 unit tests in `challenge-quiz-results.test.ts` (all-correct, all-wrong, mixed, unanswered, single-question, empty quiz, all 8 performance level boundary values, concept grouping, Unknown fallback, alphabetical sort, weak concept threshold edge cases, end-to-end integration scenarios).
- Added `frontend/lib/note-quality-badges.ts` with `computeQualityBadges` and exported `QUALITY_THRESHOLDS` constants. Added `frontend/components/notes/note-quality-badge.tsx` with the `NoteQualityBadges` component. Covered by 12 unit tests in `note-quality-badges.test.ts` (zero counts, null/undefined, threshold boundaries, High Quality suppresses Popular, label correctness). "New" badge removed — `createdAt` param and `NEW_WITHIN_DAYS` constant removed.
- Refactored `SharedNoteCard`: replaced `metaLine?: ReactNode` with `courseProgram?: string | null` (renders neutral gray badge above title) and added `stateBadge?: ReactNode` (renders Study Pack Ready badge below title). Updated all 4 callers: `public-library-page-client.tsx`, `app/library/page.tsx`, `public-profile-page-client.tsx`, `app/public/library/[subject]/page.tsx`.
- Added `frontend/lib/public-library-discovery.ts` with pure utility functions: `computeDiscoveryScore`, `getFeaturedNotes`, `getPopularNotes`, `getRecentNotes`, `getBrowseSubjects`, `excludeById`. All ranking is client-side using existing data from `listPublicNotes()` — no new backend endpoints required.
- Extracted `PublicNoteCard` sub-component inside `public-library-page-client.tsx` to share card rendering between discovery sections and the main filtered list.
- Added 28 unit tests for discovery utility functions and 5 integration tests for discovery UI behavior in `PublicLibraryPageClient`.
- Added backend scoring support via `GET /notes/public?sort=featured|popular|recent`. Sort is computed dynamically from existing engagement signals — no DB persistence. `featured` uses score `(copies × 0.6) + (views × 0.4)` with newest-first tiebreak; `popular` uses copy count → view count → newest-first; `recent` uses `createdAt` desc. Unknown or missing sort values fall through to the default DB order.
- Added `backend/util/PublicNotesScoringUtils.java` with `computeScore`, `sortByFeatured`, `sortByPopular`, and `sortByRecent`. Covered by 16 unit tests including scoring formula, sort ordering, null-count handling, tiebreaks, and empty dataset cases. Added 7 sort-specific tests to `NoteServiceTest`.
- Added backend subject filtering: `GET /notes/public?subject=<value>` returns only notes whose normalized subject matches (case-insensitive). Applied after fetch, before sort. Frontend `listPublicNotes()` accepts optional `{ subject }` param. Covered by `NoteServiceTest.listPublic_withSubjectFilter_returnsOnlyMatchingNotes`.
- Refactored LLM subject and key-concept sanitization into dedicated utility classes: `SubjectSanitizer` (max 6 words, overly-broad detection, course-program echo detection) and `KeyConceptSanitizer` (max 4 words per concept, filler-prefix stripping). Removed inline private methods from `OpenAiLlmStudyPackService`. Covered by new `SubjectSanitizerTest` and `KeyConceptSanitizerTest` unit test classes.
- Key-concept sanitization now applies to the `keyConcepts` list (not just quiz `concept` fields): overlong concepts are repaired in-place and hard-truncated as a last resort — study pack creation is never blocked by word-count alone.
- Updated LLM subject prompt to use the 3-tier subject strategy: Specific Subject (preferred) > Primary–Subtopic (when context needed) > General Subject (fallback).
- Browse by Subject section repositioned to appear ABOVE Featured/Popular/Recently Added sections in discovery mode. Limit reduced from 12 to 8 subjects.
- **Subject metadata cleanup**: subjects are now domain-only (e.g., `Biology`, `Physics`, `Engineering`). Any combined domain-topic value (`Biology – Cell Division`, `Physics: Ohm's Law`) is automatically stripped to the domain part before saving. Broad single-word domains (`Engineering`, `Medicine`, `Law`, `Business`, `Education`) are now valid and no longer trigger a retry. LLM prompt and subject guidance block updated to request domain-only subjects. Added `SubjectSanitizer.stripSubtopicSuffix` utility method. Covered by new `stripSubtopicSuffix` tests in `SubjectSanitizerTest` and updated `OpenAiLlmStudyPackServiceTest` subject edge cases.

## v0.7.0 - Learning & Metadata Foundation (In Progress)

### New Features

- User profiles now support `Learner Level` plus required `Course / Program` on Learning Profile saves as part of the learning-profile foundation.
- Onboarding now includes a dedicated `Learning Profile` step that collects required learner level, required course/program, and optional bio.
- Notes now support optional per-note `Course / Program`, defaulted from the user's profile and editable per note.
- Note metadata suggestions now use a shared field-level AI review modal for `title`, `subject`, and `tags`.

### Improvements

- Public Profile and Private Profile are now clearly separated in both navigation and purpose. Public Profile (`/public/profile/{userId}`) is the user's shareable learning-portfolio surface. Profile Settings (`/profile`) is the private account editing surface, accessed via `Edit Profile`.
- The avatar dropdown now uses clear, consistent labels: `My Profile` (→ public profile), `Settings` (→ `/settings`), and `Sign Out`. The sidebar Account section uses the same model: `Profile` (→ public profile) and `Settings` (→ `/settings`).
- Terminology is now consistent: **Profile = public identity page. Settings = account/app settings.**
- Share Profile now uses the same modal pattern as note sharing: a modal with title `Share this profile`, a labeled `Shareable URL` field, and `Copy Link` + `Close` buttons. The previous toast/inline-text-only behavior is replaced.
- If a profile is private, clicking Share Profile opens a confirm modal (`This profile is private`) that offers `Make Public & Share` — the same gate used for private note sharing.
- Share behavior is now consistent across notes and profiles: public content opens the share modal directly; private content requires owner confirmation before the share modal appears.
- Private profile confirm message updated to include `and notes` so users understand what becomes visible.
- Private Profile now separates `Identity`, `Learning Profile`, and `Profile Type` into distinct saveable cards.
- Public Profile can now show learner level and course/program when the owner chooses to provide them.
- Public Profile now feels more like a learning portfolio, with compact real metrics for public notes, copies, shares, and views when available.
- Public Profile now derives lightweight learning-focus text from real public-note metadata and can highlight a featured note when usage data exists.
- Learner-level and course/program inputs now reuse the same subject-style combobox UX as the Note Editor `Subject` field.
- Fixed-option learner-level comboboxes now snap back to the last valid saved value if a user types an unsupported option and closes the field.
- Note Editor now includes `Course / Program`, subject autocomplete, optional tags guidance, and the same metadata shape in both create and edit modes.
- Course / Program now behaves like a reusable top-level taxonomy shelf with stronger default suggestions, normalized saved-value reuse, and shared autocomplete across Note Editor, Note Detail metadata edit, Profile, and Onboarding.
- Course / Program autocomplete now filters in real time, ranks exact/prefix/contains matches more cleanly, keeps existing suggestions ahead of the custom action, and reuses existing display labels for exact case-insensitive matches.
- Course / Program helper text now adapts to `Learner Level`, and Profile learning-profile saves now show inline validation when either required field is missing.
- Saved custom subjects now feed future autocomplete suggestions through the existing distinct-subject backend source.
- Subject reuse now normalizes whitespace, dash formatting, and case-insensitive matches so equivalent custom subjects collapse into a cleaner autocomplete/filter catalog without adding a new subjects table.
- AI-generated subjects now use stronger library-specific guidance plus backend validation so overly broad labels like `Engineering` or `Business` are retried before being accepted.
- The AI Suggestions modal now uses a compact review layout with field-by-field comparisons, tag chips, a live preview, and a sticky mobile footer.
- Quiz generation now uses learner-level-aware prompt guidance across Quick Review, Challenge Quiz, and Adaptive Practice, defaulting to college-level when the user has no saved learner level.
- Quantitative notes can now produce computation and problem-solving questions with step-based explanations when the note context supports it.
- Challenge Quiz and Adaptive Practice now require richer explanations and concept labels in their generated quiz payloads.
- Library and Public Library now use richer metadata-driven filtering with course/program support, Public Library learner-level/source filters, and subtler note-card metadata hierarchy with visibility icons instead of extra badges.
- Public Library cards now emphasize the original note preview first and use subtle `views` / `copies` metrics plus `Most Viewed` sorting to help users spot strong notes faster.
- Dashboard `Continue Studying` now shows the actual note title plus subject/course metadata and uses the correct resume label for Quick Review, Challenge Quiz, or Adaptive Practice.
- Private and public note detail now include a `Full Notes` tab so users can inspect the complete original note alongside `Summary`, `Key Concepts`, and `Quiz`.
- The `Summary` view on private and public note detail now includes a subtle `View Full Notes →` CTA so users can jump from AI preview to the original note without losing context.
- Back navigation across all sub-pages now uses a shared `BackLink` component that renders `← {label}` with an arrow icon — small, muted, and link-styled rather than a button. Replaces all previous blue "Back to Library" / "Back to Note" link text and large Back buttons.
- My Profile (owner view) has no back link — it is a main navigation page reachable from the sidebar. Non-owners viewing another user's public profile see `← Public Library` linking explicitly to `/public/library`.
- Note Detail shows `← Library`, quiz pages show `← Note`, Create Note shows `← Library`, Edit Note shows `← Note`, Edit Profile shows `← Profile`, learn articles show `← Learn`. Inline card action buttons use short labels (`Note`, `Library`) without "Back to" prefix.

### Technical Changes

- Added `users.learner_level` and `users.course_program` with backward-compatible nullable storage for existing users.
- Backend Study Pack generation now prepares learner-level and course/program metadata in generation context for future prompt tuning, alongside note subject and tags.
- Refactored the OpenAI Study Pack service to share request/response/error handling across Study Pack, study-tip, and quiz generation flows, and added direct unit coverage for the refactored service.
- Added `notes.course_program` plus note-service create/update/copy handling so note metadata can diverge from the profile default when needed.
- Added normalized `GET /api/course-programs?scope=mine|public` suggestions backed by saved note/profile course-program values without adding a separate taxonomy table.
- Unified backend quiz-generation contracts onto strict JSON with required `answer`, `explanation`, and `concept` fields for more reliable parsing.

### Fixes

- Manual sign-out no longer reuses a stale protected-page `redirect` on the next login, so same-account and cross-account relogin now return to `Dashboard` instead of leaking back into the previous protected route.
- Restored distinct Note Editor create vs edit behavior so existing notes now render `Edit Note` copy, correct edit-mode actions, and the generated-note content lock without falling back to create-note messaging.
- Quiz validation now uses math-safe choice normalization, catches real blank/duplicate/invalid-choice payloads more accurately, and retries LLM invalid quiz output only once before failing.
- Quiz choice shuffling now preserves answer correctness by normalizing runtime data to canonical `choices + correctIndex`, keeping `A` / `B` / `C` / `D` as UI-only labels, and accepting legacy answer-text session payloads during load.
- Study Pack generation is now significantly more reliable for technical notes (Ohm's Law, electrical engineering, math formulas, science notes). The backend now safely logs failing field values for debugging (`requestId`, `field`, `value` truncated to 80 chars, `reason`) without logging full note content, prompts, or raw LLM output. Before failing on an invalid `quiz[].concept` or `subject`, the backend attempts a repair pass: concepts exceeding 4 words have leading filler phrases stripped (`Relationship between`, `Using the`, etc.) and are truncated to at most 4 words; subjects exceeding 6 words have their subtopic portion truncated to fit. The prompt rules for concept (now explicitly 1–3 words with counter-examples) and subject (now explicitly max 6 words with counter-examples) are tightened to reduce LLM drift. Covered by 10 new unit tests including an Ohm's Law regression scenario.

## v0.6.0 - Landing Revamp & Positioning (In Progress)

### New Features

- Landing page now frames NoteLib as a notes library and study workspace, not just a one-time quiz generator.
- Public marketing navigation now exposes `Home`, `Public Library`, `Learn`, `Pricing`, `Login`, and `Get Started`.
- NoteLib now has a standardized favicon and app-icon set based on the NL monogram for desktop, mobile, and home-screen usage.
- Demo page rewritten as a 5-step interactive flow (choose start → topic/paste input → generated note → Study Pack CTA → Study Pack results) using static Photosynthesis content only — no backend or LLM calls.

### Improvements

- Landing hero repositioned around exam-readiness: headline changed to `Turn your notes into exam-ready study materials in seconds`; `Try Demo` promoted to primary CTA with `Start for Free` as secondary.
- `Why NoteLib` feature section updated with three benefit cards framed as learning outcomes: Built for studying, Learn from your weak points, From notes to mastery.
- Demo quiz made interactive: users select an answer before seeing correct/incorrect feedback, simulating real exam conditions; post-quiz CTA (`Ready to create your own Study Pack?`) drives conversion after the demo experience.
- Landing pricing section updated to Free / Plus / Pro cards with plan descriptions tied to learner stage, intro pricing display, export tooltip (`PDF/DOCX for offline or classroom use`), and Plus Adaptive Practice (10 sessions/month).
- `Plus` plan pricing config gains `adaptivePracticePerMonth: 10` so Adaptive Practice is properly reflected in plan comparison surfaces.
- Product positioning principles added to AGENTS.md: learning-outcome framing, demo as conversion driver, clear plan progression rules.
- Landing page now uses a tighter high-conversion structure built around:
  - a faster product headline focused on summaries, quizzes, and exam simulations
  - a 3-step `Add notes -> Generate study pack -> Test yourself` explanation
  - dedicated feature coverage for Study Packs, Challenge Quiz, Adaptive Practice, and Board Exam Mode
  - clearer comparison against generic AI tools plus target-user guidance for students, board exam reviewees, and teachers
  - stronger CTA flow with `Start for Free`, `See how it works`, pricing preview, and a clearer closing section
- Challenge Quiz and Board Exam Mode now use a collapsible Question Navigator so mobile quiz screens stay less cluttered:
  - Challenge Quiz defaults to an expanded navigator on desktop and a collapsed summary on mobile
  - Board Exam Mode defaults to a collapsed navigator on both desktop and mobile to keep the exam view more focused
  - the collapsed summary still shows current question position and answered count, and expanding it keeps direct jump navigation intact
- Major action buttons now keep icon + text labels on mobile across the app’s shared action surfaces instead of collapsing to icon-only.
- Profile now supports a short bio on the private identity page, and Public Profile now renders that bio with avatar/initial styling and derived subject chips.
- Public Profile now uses a page-level `Back` action above the header card, based on navigation history instead of a hardcoded return link to Public Library.
- Private Library and Public Library now share the same `Search`, `Filter`, `Sort`, notes-list structure, with mobile-friendly filter/sort sheets instead of always-visible controls.
- Library, Public Library, and Public Profile note cards now stay action-free preview surfaces so note management happens consistently in Note Detail.
- Private Note Detail `Summary` and `Quiz` tabs now keep text labels on mobile for clearer view switching.
- Landing page now positions NoteLib as a notes library and study workspace first, with stronger Public Library and active-recall messaging.
- Public Library is now promoted directly from the landing page as a discovery surface that stays accessible without login.
- The landing page now integrates the Learn / active-recall message so new users understand the study method, not only the generation workflow.
- Learn article pages now use a consistent content-marketing structure with introduction, summary, key concepts, sample practice questions, and a bottom account-creation CTA.
- Landing page SEO title, meta description, and Open Graph metadata now align with the notes-library positioning update.
- Pricing page messaging now frames NoteLib as a notes library plus review workflow, with Free/Premium copy aligned around core note creation and heavier exam review periods.
- Pricing page now includes a `Why Go Premium` section that explains Premium in terms of serious review, practice, and exam preparation rather than only limits.
- Pricing no longer treats Public Library as a paid-plan feature.
- Theme toggle is now available on the shared public navbar and syncs with a persisted user theme preference for authenticated users.
- Navbar and app-shell logos now use the NL monogram, while marketing headers and the public footer use the full NoteLib wordmark.
- The Open Graph image now uses the standardized NoteLib branding, notes-and-lightning illustration, and notes-library messaging.
- Study Pack generation surfaces now use student-friendly monthly-limit banners and plan-specific limit modals for both Free and Premium instead of relying on disabled generate actions.
- Public Library now supports discovery sorting by newest, most copied, most shared, and most viewed.
- Public note detail now uses a stronger copy-first growth CTA for non-owners, including a handoff into their own Library note for generation.

### Fixes

- Auth redirect logic now returns users to interrupted protected pages through explicit redirect intent while sending manual public-page logins to `Dashboard`.
- Login-page auth messaging now distinguishes `session_expired`, `logged_out`, and `auth_required` so manual logout no longer shows the expired-session warning.
- Manual logout now suppresses late expired-session redirects from in-flight protected requests so logout messaging stays neutral.
- Shared public navbar no longer duplicates the theme toggle inside the mobile menu, and public CTA hierarchy now keeps `Get Started` primary, `Login` secondary, and theme as a utility control.
- Study Pack limit enforcement and usage warnings now use the same effective usage calculation so users are no longer told they have credit left while generation is already blocked.
- Free-plan near-limit messaging now shows the actual remaining Study Pack count instead of a generic warning.
- Note Detail generation now applies the same title/subject/tag suggestion flow as Create Note.
- Note Detail tab switching no longer refetches the note or snaps long pages back to the top when `?tab=` changes.
- Mobile Note Editor no longer lets the global `Send Feedback` launcher overlap the primary Generate CTA.
- Library-style note cards no longer mix management menus into preview surfaces, avoiding conflicting card-navigation behavior.

### Technical Changes

- Shared responsive action components now default to mobile icon + text labels, with explicit opt-out reserved for true icon-only utility controls.
- Added shared library toolbar and sheet components so private/public library controls stay consistent across desktop and mobile.
- Added shared brand-asset components for the monogram, full logo, and product icon, plus a local OG-image render pipeline and web manifest for the public icon set.
- Added a shared backend Study Pack usage resolver so plan summary and generation-limit enforcement stay synchronized across services.

## v0.5.0 - Public Profiles & Public Notes

Public Profile:

- Public profile page at `/public/profile/{userId}`
- Public identity uses `displayName`; public pages never show email
- Public profile shows `Profile Type`, public-note stats, and total copies
- Public profile visibility can be turned `On` or `Off`
- Owner-only public-page controls live on Public Profile:
  - `Edit Profile`
  - `Share Profile`
  - Public visibility badge/dropdown
- Non-owners can view/share public profiles only when the profile is public

Public Notes:

- Public notes appear in Public Library and Public Profile
- Public author labels are viewer-relative:
  - `By You`
  - `By NoteLib` with `Official`
  - `By {Display Name}`
- Public author labels link to Public Profile
- Public note detail remains read/copy/share only
- Public note copying preserves attribution to the source note and creator

UI and UX:

- Shared note-card layout across Library, Public Library, Public Profile, and public subject pages
- Whole-card click behavior across library-style note cards
- Removed redundant `Open Note` buttons from public showcase/discovery cards
- Shared cards now show clamped `Note Preview` plus `Summary Preview`
- Private Note Detail now uses underline tabs for `Summary` and `Quiz`
- Icon usage is standardized across navigation and common actions
- Quick Review, Challenge Quiz, and Adaptive Practice use distinct icons
- Action buttons now follow a shared responsive desktop/mobile pattern
- Dark-mode outline buttons use higher-contrast borders, lighter text, and clearer hover states
- Profile page is split into Display Name, Identity, and Profile Type cards with per-section save actions
- Public profile controls were moved off `/profile` and onto the Public Profile page
- Auth recovery now returns users to their interrupted or last visited page after login instead of always forcing `Dashboard`

Documentation baseline:

- `v0.5.0` is the documentation lock point for Public Profiles and Public Notes
- next planned milestone is `v0.6.0 - Landing Revamp & Positioning`

## v0.4.0 - Profile-Based Experience & UX

- Profile identity management
- Email change verification
- Onboarding per profile type
- Personalized dashboards
- Teacher workflow and quiz-first note creation
- Note editor UX improvements across desktop and mobile
- First-time activation flow from verification through first Study Pack and first quiz guidance
