# 07 — Technical Approach & Phased Roadmap (Deliverables 11 + 12)

> **Session 07 of the Smart Review Planning planning series — synthesis capstone.** Planning only.
> Builds strictly on the "Decisions carried forward" blocks of sessions 01–05 (all five present; no
> gaps). This document sequences the work as NoteLib release-sized chunks expressible in the shipped
> workflow (kickoff → scoped PR → signoff); it authorizes nothing — each release still gets its own
> kickoff and Codex scoping in a later session.

## Decisions carried forward

**Build order (backend spine first, one human gate live per release):**
Schema/entities → template + AI-drafted objectives (Gate A) → matcher + Coverage Board (Gate B) →
gap-fill generation queue + review queue (Gate C) → assemble/publish + staleness → learner
recommend/adopt → learner partial-coverage assembly → gated PLUS/PRO layers.

**Phase 1 — MVP: Internal Curator core (4 releases, ADMIN-only, zero learner surface):**
- **R1 `curriculum-foundation`** — migration (4 tables + 2 `note_collections` columns), template
  CRUD, AI-drafted objectives via the v0.42.0 assist pattern, template editor at `/admin/curation`.
- **R2 `matching-coverage`** — tiered lexical matcher (+ small `concept_aliases` support table),
  SUGGESTED fulfillments, gap scan, derived coverage endpoint, Coverage Board (confirm/reject).
- **R3 `gap-fill-review`** — `curator_generation_requests` queue, generation via bulk-generation
  internals (PREMIUM tier, always-PRIVATE drafts), review queue UI with Approve & Publish.
- **R4 `assemble-publish`** — auto-slot confirmed notes into Subject Plans, publish stamps
  `curriculum_template_version_at_publish`, ADMIN `mayBeOutdated` staleness, wizard overlay.
- **GATE 1 (prove-it-out, low-volume caveat):** admin builds ≥1 real Official Review Set (ALE/PNLE/
  LET) end-to-end; measure match precision + curator throughput before any learner surface ships.

**Phase 2 — v2: Learner-facing "Plan My Review" (2 releases + polish, FREE layer only):**
- **R5 `plan-my-review-core`** — step-0 recommendation endpoint, `/collections/setup` wizard
  (Goal step + coverage map: official / empty / no-template states), `LEARNER_REQUEST` filing
  endpoint (the only new learner write), `guidedSetupCta` labels, entry points.
- **R6 `coverage-assembly`** — partial-coverage map state + confirm-&-assemble step orchestrating
  existing endpoints client-side; land on existing Goal detail.
- **R7 `demand-loop-polish`** — demandCount-ranked Coverage Board prioritization, Goal-detail
  coverage re-check (the named phase-2 follow-up), request-status honesty polish.
- **GATE 2:** owner confirms FREE placement of recommend+adopt before R5 (Monetization doc is a
  recommendation, not a decision). GATE 1 must have passed.

**Phase 3 — long-term, each independently gated:**
- **PLUS conversational assembly** — gated on owner tier ratification + Phase 2 adoption signal.
- **PRO adaptive planning** — gated on PLUS shipping + ConceptHealth signal quality.
- **`EMBEDDING` matcher tier** — gated on lexical recall proving insufficient on real curricula.
- **Tier auto-confirm** — gated on curator confirm-rate calibration data; default stays always-human.
- **Review-Set-Centric nav** — stays deferred exactly as ROADMAP.md states; nothing here advances it.

**Invariant across every phase:** Curator (ADMIN, generates behind three human gates) and Learning
Assistant (learner-facing, read-only + request filing, never generates) never merge; curation-never-
generation holds in each release independently.

---

# 1. Technical implementation approach (Deliverable 11)

## 1.1 Why this build order

The ordering principle: **each release ships one layer of the derivation chain, and every release
leaves the system in a state where the manual path still works.** Session 03's core design move is
that everything derives from the curriculum template (structure, attachment, gaps, generation scope,
staleness) — so the template must exist first (R1), derivation of *existing* content second (R2),
derivation of *missing* content third (R3), and the delivery container last (R4). This also means
each of the three human gates (A: template save, B: match confirm, C: per-note publish approval)
goes live in the same release as the automation it gates — no release ever ships automation ahead of
its gate.

Learner-facing work (Phase 2) is strictly after the Curator MVP because the Assistant is read-only
over what the Curator publishes: with zero ACTIVE templates and zero CONFIRMED fulfillments, the
coverage map has nothing honest to show. GATE 1 (one real curriculum built end-to-end) is the
existence proof that the learner surface will have real content behind it on day one.

## 1.2 Backend build order

### R1 — Entities and template layer

| Piece | Reuse or new |
|---|---|
| Flyway migration: `curriculum_templates`, `curriculum_objectives`, `curriculum_objective_fulfillments`, `curator_generation_requests`; 2 nullable columns on `note_collections` | **New** (schema exactly as fixed in session 01 — no additions, no note/Study Pack changes) |
| Entities + repositories + `CurriculumTemplateService` (CRUD, version bump on objective-set edit, ACTIVE/DRAFT/ARCHIVED transitions) | **New**, conventional Spring layer; named exception subclasses per repo convention |
| AI-drafted objective list (`POST` draft endpoint, staging-area semantics: generation never persists on its own, Save is the only write) | **Reuses the v0.42.0 Companion per-section AI-assist pipeline shape verbatim** — same PREMIUM tier, same "draft renders editable, admin saves" contract. Calibrated by courseProgram only, never learnerLevel (foundation rule) |
| Duplicate-title warning on template save (normalized `conceptTitle` comparison) | **New but trivial** — uses the matcher's normalization util (built in R1 as a shared utility so R2 consumes it, mirroring the `QuizSessionStateUtils` single-owner pattern) |

The fulfillment and request tables ship in R1 even though nothing writes them until R2/R3 — one
migration for the whole entity model avoids three consecutive schema PRs touching the same area and
keeps the foundation doc's "4 tables, everything else is reuse" auditable as a single diff.

### R2 — Matcher, fulfillments, coverage

| Piece | Reuse or new |
|---|---|
| Normalization (trim, casefold, punctuation/possessive-strip, token-sort) | **New utility, superset of the existing ConceptHealth dedupe normalization** — extracted so both call sites share one implementation, not copied |
| Tiered matcher: `EXACT_CONCEPT` / `CROSS_CURRICULUM` / `ALIAS_CONCEPT` / `TOKEN_OVERLAP` with confidence bases + bounded modifiers | **New, deterministic, zero-LLM.** Pure-function core over candidate sets → unit-testable without fixtures. Opaque `matchSource`/`matchConfidence` persisted so the `EMBEDDING` tier slots in later without schema change |
| `concept_aliases` support table (curator-maintained) | **New, small** — the one addition beyond the foundation four; introduced here because it belongs to the matcher, not the entity model. Flagged explicitly so the foundation doc's table count is amended knowingly, not silently |
| Candidate scoping: PUBLIC + `courseProgram` match (fallback `subjectLabel`) | **Reuses** existing note visibility + taxonomy columns; categories filter, never match |
| Batch gap scan (template activation / on-demand) + incremental re-match on public-note publish | **New service logic**; the publish-time hook is the only touchpoint on an existing flow — an after-commit listener, never in the publish transaction |
| Coverage endpoint: weighted CONFIRMED-to-currently-PUBLIC ratio, computed on read | **New but thin** — one count query per template; nothing persisted per learner, so visibility changes self-heal by the counting rule |

Matching runs **curator-side only** (batch + incremental); it never executes in a learner request
path. That keeps learner latency and load profile untouched by R2.

### R3 — Generation queue and review

| Piece | Reuse or new |
|---|---|
| `CuratorGenerationRequestService`: REQUESTED → DRAFT_GENERATED → IN_REVIEW → PUBLISHED/REJECTED; dedupe + `demandCount++` | **New** state machine over the R1 table |
| Draft generation from objective seed (conceptTitle + description as topic, subject = subjectLabel, courseProgram from template) | **Reuses the bulk-generation pipeline internals**: ADMIN bypass, throttled sequential fan-out, note-from-topic + async Study Pack. Two hard rules from sessions 01/03: PREMIUM tier, and **always PRIVATE** — the bulk Public toggle is never exposed on this path |
| Executor discipline | **Existing rule honored**: fan-out on `llmParallelTaskExecutor`; `studyPackGenerationTaskExecutor` is never passed to parallel generation |
| Approve & Publish action (note → PUBLIC, fulfillment → CONFIRMED, request → PUBLISHED, atomically) | **New service method** — the one multi-entity transaction in the whole feature; the audit focus for this release (see risks) |

### R4 — Assembly, publish, staleness

| Piece | Reuse or new |
|---|---|
| Auto-slot confirmed notes into Subject Plans (grouped by subjectLabel, `moduleLabel` → item `label` free text, objective `position` order) | **Reuses `NoteCollection`** wholesale — an Official Review Set *is* a normal published Goal + Subjects; backend still never interprets labels |
| Publish gate: every item note PUBLIC | **Existing, unchanged** — this is the structural backstop; R4 must not touch it, only pass through it |
| `curriculum_template_version_at_publish` stamp + ADMIN-only `mayBeOutdated` | **Reuses the Companion structure-snapshot staleness pattern**; never auto-republish, adopted copies stay snapshots |

### R5–R6 — Learner-facing backend

Deliberately tiny: **one new write endpoint** (file/dedupe a `LEARNER_REQUEST`) and **two read
endpoints** (step-0 recommendation lookup; learner-shaped coverage map for a courseProgram —
objectives with learner-visible status collapse `Requested`/`Being prepared`/`Not available yet`).
Assembly itself is client-side orchestration of existing endpoints (create Goal, create/nest
children, `copyNote includeStudyPack=true`, add items, labels, target date) — zero new write
infrastructure beyond request filing, exactly as session 04 fixed.

## 1.3 Frontend build order

- **R1–R4 (admin):** all under the existing ADMIN-gated `/admin` surface at `/admin/curation/...` —
  template editor with AI-draft staging area (reuses the v0.42.0 Companion assist UX), Coverage
  Board (reuses the `/progress` bar-and-chips grammar: per-subject horizontal bars, weakest-first,
  no chart library), review queue (reuses note editor + Study Pack preview + the AI Suggestions
  review-and-decision pattern), and the kickoff wizard overlay tying screens together. Desktop-
  oriented, dark-mode-capable, no charts/filters beyond the coverage bars — per the admin-dashboard
  v1 philosophy. Every step degrades to today's manual path; the only always-on new surfaces are
  the Coverage Board and review queue (session 03's low-volume stance).
- **R5–R6 (learner):** `/collections/setup` — a sub-page of the collections surface, no new nav
  item. Labels through the new `CollectionLabels.guidedSetupCta` field (never hardcoded; TEACHER
  hidden). Course/Program via `CourseProgramCombobox` `allowCustom={false}` (taxonomy rule: never
  freetext). Entry points reuse existing slots only: `DashboardEmpty`, the `browseWhenEmpty`
  guidance card, a `/collections` header action + one `pickActiveGuidance` tip, and the
  `/collections/published` Recommended empty state. Wizard ends on the existing Goal detail — no
  destination surface of its own. Voice rule enforced in copy review: outcomes, never mechanism;
  no "AI"/"Smart"/"Auto".

## 1.4 Reuse-vs-new summary

**Reused unchanged:** NoteEntity/StudyPackEntity, the public-note copy spine
(`includeStudyPack=true`, adopt-goal), NoteCollection + its publish gate, the Companion
staleness-snapshot pattern, the v0.42.0 AI-assist pipeline shape, bulk-generation internals,
ConceptHealth/readiness (untouched — coverage and readiness never share a bar, color scale, or
vocabulary), `getCollectionLabels`, `FeatureGateService`/`UserUsageEntity`, `/progress` viz grammar,
`pickActiveGuidance`.

**Genuinely new:** 4 entities + 1 small alias table + 2 columns; the normalization/matcher/coverage
services; the generation-request state machine; `/admin/curation` screens; `/collections/setup`
wizard; one learner write endpoint; `guidedSetupCta`.

## 1.5 Main technical risks

1. **Matcher precision/recall on real curricula** (highest product risk, lowest safety risk). Every
   match is SUGGESTED-only, so a bad matcher wastes curator time rather than corrupting coverage —
   but if precision is poor, the Coverage Board becomes noise and the whole throughput argument
   fails. Mitigation: the pure-function matcher core gets a golden-set test built from the GATE 1
   curriculum; confirm-rate per tier is logged from day one (it is also the input to the deferred
   auto-confirm decision); the `EMBEDDING` tier is the pre-planned escape hatch, gated on measured
   lexical recall, not speculation.
2. **The Approve & Publish transaction** (R3): note visibility flip + fulfillment CONFIRM + request
   PUBLISHED must be atomic and idempotent (double-click, retry after timeout). This is the classic
   Codex-audit gap (error states, transactions, idempotency) — flag it explicitly in the R3 Codex
   prompt and in `/audit-diff`.
3. **Client-side assembly orchestration** (R6): a multi-call wizard (create Goal → children →
   copies → items) can partially fail. Session 04's answer — per-item skip isolation + the existing
   skipped-notice pattern — is the design; the risk is implementation discipline: every step must be
   independently retryable and the wizard must never leave a half-Goal it can't render. Test plan
   must include mid-sequence failure injection.
4. **Shared-method exposure across releases.** R3/R4 and later R6 all touch or lean on the note
   publish flow and the copy spine — different PRs, same shared invariants. Per the pre-signoff
   rule, any release where 2+ PRs touch the same pre-existing shared method (likely R3 and R4) gets
   the **full pressure test**, not the single-advisor summary.
5. **Incremental re-match hook on note publish** (R2): the one new touchpoint on a hot existing
   flow. Must be after-commit/async so a matcher bug can never fail or slow a publish.
6. **Staleness-signal correctness** (R4): version-at-publish vs. template version is simple, but the
   Companion precedent showed snapshot-comparison edge cases; reuse that pattern's tested shape
   rather than re-deriving it.
7. **Scope creep into learner quota.** The cost model is load-bearing: reuse never decrements
   `UserUsageEntity`; only curator generation (admin budget) and later PLUS/PRO interaction layers
   carry LLM cost. Every release's audit checklist should verify no learner-quota write appears on
   a reuse path.

## 1.6 Workflow fit

Every release below is a standard `releases/vX.Y.Z` cycle: kickoff checklist first commit on the
release branch, feature work as scoped PRs, signoff with release notes. Per the task-routing table,
**every R1–R6 chunk is Codex territory** (new endpoints, migrations, multi-system) — each becomes
one or more Codex prompts written via `docs/skills/codex-prompt-generator.md`, audited with
`/audit-diff` before commit. Feature docs: R1–R4 add a new `docs/features/curriculum-curation.md`;
R5–R6 add `docs/features/plan-my-review.md` and touch collections/labels docs. Version numbers are
assigned at each kickoff, not here.

---

# 2. Phased roadmap (Deliverable 12)

## Phase 1 — MVP: Internal Curator core

**Goal:** replace the manual create → find → generate → publish loop with the gated wizard; produce
one real, fully-curated Official Review Set. **Learner-visible change: none** (the only learner-
adjacent effect is better Official sets appearing through existing surfaces). The two-system split
in this phase is trivially intact: only the Curator exists.

| Release | Contents | Depends on | Gates live |
|---|---|---|---|
| **R1 `curriculum-foundation`** | Migration (4 tables + 2 columns), template entity/service/CRUD, AI-drafted objectives (v0.42.0 assist pattern), template editor UI, shared normalization util, duplicate-title warning | — | **Gate A** (admin saves objective set; nothing ACTIVE without save) |
| **R2 `matching-coverage`** | Lexical matcher (4 tiers + confidence), `concept_aliases`, SUGGESTED fulfillments, batch gap scan + publish-time incremental re-match, coverage endpoint, Coverage Board with confirm/reject | R1 | **Gate B** (only human-CONFIRMED counts toward coverage) |
| **R3 `gap-fill-review`** | Generation-request state machine, multi-select "Generate missing" (bulk internals, PREMIUM, always-PRIVATE), review queue UI, Approve & Publish / Edit / Regenerate / Reject | R2 (gap set is derived from unfulfilled objectives) | **Gate C** (per-note approval; no batch-approve, no auto-publish) |
| **R4 `assemble-publish`** | Auto-slot into Subject Plans, publish stamping, ADMIN `mayBeOutdated`, kickoff-wizard overlay including step-0 duplicate-curation short-circuit | R3 (needs confirmed+published notes to assemble) | Structural backstop verified (publish gate unchanged) |

**GATE 1 — prove-it-out (the ROADMAP's low-volume caveat, applied):** before any Phase 2 kickoff,
the admin builds at least one real curriculum (ALE, PNLE, or LET — the top three prod buckets)
end-to-end through the wizard. Exit criteria: (a) template drafted + saved with acceptable edit
effort — this is also where the "gated on authoring-assist proving out" ROADMAP condition is
discharged, since R1 reuses that exact assist pipeline; (b) matcher confirm-rate per tier recorded
and TOKEN_OVERLAP precision acceptable; (c) coverage reaches publishable threshold on admin budget;
(d) the low-volume stance holds — the template demonstrably earns its keep at N=1–3 sets via
coverage %, staleness, and the demand queue. If (b) fails, schedule the `EMBEDDING` tier before
Phase 2 rather than shipping a learner coverage map built on weak matching.

## Phase 2 — v2: Learner-facing "Plan My Review" (FREE layer)

**Goal:** the Learning Assistant's first surface — read-only recommendation + honest coverage +
zero-LLM assembly from what exists. The split stays intact structurally: the Assistant's only write
is filing a `curator_generation_request`; it never generates, never sees private content, never
auto-publishes.

| Release | Contents | Depends on | Notes |
|---|---|---|---|
| **R5 `plan-my-review-core`** | `/collections/setup` steps 1–2 (Goal question, coverage map: official-set short-circuit / empty / no-template states), recommendation + coverage read endpoints, `LEARNER_REQUEST` filing (dedupe, `demandCount++`), `guidedSetupCta`, entry points, voice-rule copy | GATE 1 passed; **GATE 2a**: owner confirms FREE placement of recommend+adopt (session 05 is a recommendation, not a decision) | Official-set path is pure existing `adopt-goal` — zero marginal LLM, the activation funnel |
| **R6 `coverage-assembly`** | Partial-coverage map state, confirm-&-assemble step (client-side orchestration over existing endpoints, per-item skip isolation), land on Goal detail | R5 | Owned-note lineage match + mastery chips inform prioritization only — never auto-transferred; coverage and readiness stay in separate vocabularies |
| **R7 `demand-loop-polish`** | Coverage Board sorted/annotated by `demandCount`, Goal-detail coverage re-check (session 04's named phase-2 follow-up), learner request-status rendering polish (`Requested` / `Being prepared` / `Not available yet`) | R5 (demand data), R4 (board) | Closes the flywheel: learner demand → curator priority → published content → recommendation |

Phase 2 releases likely trip the **full pressure test** rule (single concept touching backend +
several frontend consumers) — plan signoff effort accordingly.

## Phase 3 — long-term, individually gated

No sequencing commitment among these; each has its own gate and its own future kickoff. All keep
the Assistant read-only over published content and the Curator behind its three gates.

| Item | Gate | Shape when it comes |
|---|---|---|
| **PLUS conversational assembly** | Owner ratifies the full tier recommendation (GATE 2b) **and** Phase 2 shows adoption signal worth a paid interaction layer | Assistant composes a personal Review Set from PUBLIC content via conversation; per-query LLM, content still 100% reuse; gaps still file requests |
| **PRO adaptive planning** | PLUS layer shipped; ConceptHealth signal quality validated for weighting | ConceptHealth-weighted selection, readiness-aware sequencing, re-planning — adaptive selection, not deterministic reordering (the philosophy's PRO line) |
| **`EMBEDDING` matcher tier** | GATE 1(b) or later confirm-rate data shows lexical recall insufficient | New recall tier only; `matchSource` slots it in with no schema change; human confirmation unchanged |
| **Tier auto-confirm** | Confirm-rate calibration per tier over real curator history | Highest-confidence tiers may skip Gate B per-row; default remains always-human until data says otherwise |
| **Review-Set-Centric navigation** | Unchanged — stays deferred and gated exactly as ROADMAP.md already states | Not advanced by anything in this series |
| **PARENT/PROFESSIONAL depth** | Profile feature work exists at all | `guidedSetupCta` strings already reserved; no other coupling |

## Dependency spine (one line)

R1 → R2 → R3 → R4 → **GATE 1** → R5 (**GATE 2a**) → R6 → R7 → [PLUS (**GATE 2b**) → PRO] ∥
[EMBEDDING] ∥ [auto-confirm] — with the two-system split and curation-never-generation invariant
checked at every signoff, not just at the end.
