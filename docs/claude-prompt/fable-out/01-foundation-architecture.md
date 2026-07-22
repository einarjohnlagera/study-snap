# Smart Review Planning — Foundation Architecture

> **Session 01 of the Smart Review Planning planning series.** Planning only — no code, no scoped
> release, not a Codex prompt. Covers deliverables 1–3 of the brief in
> `docs/claude-prompt/smart-review-planning-and-product-language.txt`: system architecture / data
> model, the reuse-first pipeline, and the knowledge-reuse cost model. Knowledge-matching internals,
> coverage visualization, admin UI, student UI, pricing, and terminology are explicitly deferred to
> later sessions.

> **Amended (2026-07-11):** table count corrected (`concept_aliases` from session 02 accounted for) and CONFIRMED-fulfillment cardinality rule made explicit (swap-on-confirm, `SUPERSEDED` state), per post-series consistency review.

## Decisions carried forward

**New entities (4 core tables — everything else is reuse; session 02 later added one small fifth table, `concept_aliases`, a curator-maintained concept-alias list feeding the matcher's `ALIAS_CONCEPT` tier — it belongs to the matching layer, not this entity model):**
- `curriculum_templates` — admin-owned requirements list for one course/program (e.g. "Civil Engineering Licensure Exam"); `status` DRAFT/ACTIVE/ARCHIVED; integer `version` bumped on objective-set edits. Deliberately **not** a NoteCollection.
- `curriculum_objectives` — note-granular concept requirements under a template: `subjectLabel`, optional `moduleLabel`, `conceptTitle`, `description`, `position`, optional `weight`. One objective ≈ one note-sized concept.
- `curriculum_objective_fulfillments` — many-to-many objective ↔ note mapping with `status` SUGGESTED/CONFIRMED/SUPERSEDED/REJECTED, `confirmedBy`, opaque `matchSource`/`matchConfidence` provenance. Only CONFIRMED mappings to PUBLIC notes count toward coverage. **Swap-on-confirm rule:** at most one CONFIRMED fulfillment per objective at a time — confirming a new one automatically moves the previous CONFIRMED row to `SUPERSEDED` (kept for provenance, re-confirmable, never re-suggested by the matcher). Assembly is therefore deterministic: every objective has exactly 0 or 1 confirmed note. This is the behavior behind session 03's Coverage Board "swap note" action.
- `curator_generation_requests` — the single review queue: `objectiveId` (or freetext concept + courseProgram), `source` (CURATOR_GAP_SCAN / LEARNER_REQUEST), `demandCount`, `status` REQUESTED → DRAFT_GENERATED → IN_REVIEW → PUBLISHED / REJECTED, `draftNoteId`.

**Extensions to existing entities (2 nullable columns on `note_collections`, Official top-level only):**
- `curriculum_template_id` FK + `curriculum_template_version_at_publish` int — links an Official Review Set to the curriculum it implements; a version mismatch drives an ADMIN-only `mayBeOutdated` signal (same pattern as `companionMayBeOutdated`). Never copied on adopt.

**Nothing new on notes or Study Packs.** Curator-generated missing notes are ordinary admin-owned `NoteEntity` drafts (+ Study Pack), PRIVATE until publish. Curriculum `moduleLabel`s materialize into existing collection item `label` free text; the backend still does not interpret labels.

**Coverage** attaches to the curriculum template (derived, never persisted per learner): CONFIRMED fulfillments to PUBLIC notes ÷ objectives. Coverage = "does published content exist"; readiness (ConceptHealth) = "has this learner mastered it". Never conflated.

**Two-system split as applied:**
- **Internal Curator** (ADMIN role): AI-drafts curriculum objectives, runs gap scans, generates draft notes for missing objectives (PREMIUM LLM tier; calibrated by courseProgram, never learnerLevel), assembles and publishes Official Review Sets. Every generated artifact passes mandatory human review before publish; publishing is never autonomous.
- **Learning Assistant** (learner-facing): read-only over published content — recommends Official Review Sets to adopt and PUBLIC notes to copy; on a gap, files a `curator_generation_request` (deduped, `demandCount++`) and tells the learner honestly. It never generates, never auto-publishes, never surfaces other users' private content.
- **Structural enforcement reused:** Review Set publish already requires every item note PUBLIC — an unreviewed draft cannot reach a learner through a published plan even by bug.

**Reuse search order (per unfulfilled objective; whole-request short-circuit first):**
0. Whole request: an ACTIVE Official Review Set already linked to this curriculum → recommend adopt; stop.
1. Public Notes (prefer those with ready Study Packs — the copy spine carries the pack).
2. Existing Study Packs: ready packs on matched public notes, plus admin-owned internal drafts (curator-visible only; re-enter review before publish).
3. Confirmed fulfillments from other curricula / Official Review Sets — reuse prior curation, not just content.
4. Prior user Review Sets — aggregate adoption/co-occurrence signal over PUBLIC notes only; curator-facing suggestion input, never attributable.
5. Generate-only-missing — Curator-only; the Assistant may only enqueue a request at this step.

**Cost model:** generation is one-time and admin-budgeted (never learner Study Pack quota); learner adoption is DB copies only via the existing `includeStudyPack=true` spine — zero marginal LLM. Reuse never consumes quota; only genuinely new generation does. `demandCount` prioritizes gap-fill; fulfillments shared across curricula push the Nth curriculum's marginal cost toward zero. Staleness reuses the structure-snapshot pattern: template `version` bump → ADMIN-only outdated signal → deliberate re-assembly, never auto-republish; adopted copies are snapshots and never auto-update.

---

# 1. System architecture and data model

## 1.1 The existing spine this sits on (reuse, unchanged)

Smart Review Planning is an assembly layer over entities that already exist and already carry the
right semantics. None of these change:

| Existing piece | Role in Smart Review Planning |
|---|---|
| `NoteEntity` (visibility, subject, courseProgram, tags, `copiedFromNoteId`/`copiedFromUserId`) | The atomic knowledge unit. A curriculum objective is fulfilled by a PUBLIC note. |
| `StudyPackEntity` | The generated enhancement of a note. Public-note copies include the linked Study Pack (documented intentional exception) — this is the reuse engine. |
| `NoteCollection` (Goal → Subject, 2 levels; item `label` sections; `visibility`; `sourcePlanId`; adopt = snapshot copy) | The delivery container. An assembled Official Review Set **is** a normal published Goal + Subject plans — no new container type. |
| Companion (`note_collections.companion` JSONB + structure snapshot + `companionMayBeOutdated`) | Curated guidance on the assembled set; also the precedent for the staleness-signal pattern reused in §3.4. |
| v0.42.0 per-section AI-assist authoring (`POST /collections/{id}/companion/generate`, PREMIUM tier, generation never persists on its own, Save is the only write) | The proven curator-assist pipeline shape. The ROADMAP's "Future, gated — AI-generated Review Sets" explicitly reuses this pipeline rather than building a second one; this document keeps that commitment. |
| ConceptHealth / `ProgressReportService` readiness (derived, never persisted) | Learner mastery measurement. Untouched — see the coverage-vs-readiness rule in §1.4. |
| `getCollectionLabels` | All learner-facing nouns stay profile-aware; the backend stays profile-agnostic. Nothing here adds a hardcoded label. |
| `FeatureGateService` / `UserUsageEntity` | Existing gating/quota machinery; §3.3 defines what does and does not touch learner quota. Tier placement itself is deferred (pricing session), constrained by the Monetization philosophy (FREE static / PLUS interaction / PRO personalization). |
| Public-note copy spine (`includeStudyPack`, copy-on-signup, adopt-per-item, recursive Goal adopt) | The zero-LLM distribution mechanism (§3.1). |

## 1.2 Genuinely new entities

Four core tables. Everything else in this design is reuse or a two-column extension. (Session 02
later added one small fifth table, `concept_aliases` — a curator-maintained concept-alias list
supporting the matcher's `ALIAS_CONCEPT` tier. It is matcher-support infrastructure owned by the
matching design in session 02, not part of this session's entity model, so it is not detailed here.)

### `curriculum_templates` — what "the recommended curriculum" is

The brief's flow starts with "System determines the recommended curriculum." That curriculum needs a
home, and it is **not** a NoteCollection (see §1.5 for why). A curriculum template is an admin-owned
requirements list — what a prepared learner must cover — independent of whether any note exists yet.
Its whole job is to be able to name what is *missing*, which a playlist over existing notes cannot do.

- `id`
- `courseProgram` — same normalized taxonomy value used by notes/collections (the v0.25.0 config-map values; no freetext taxonomy)
- `title`, optional `description`
- `status` — `DRAFT` (being authored) / `ACTIVE` (usable for assembly and coverage) / `ARCHIVED`
- `version` — integer, bumped whenever the objective set materially changes; drives the staleness signal in §3.4
- `createdBy`, timestamps

Authoring is Curator-side: an admin may hand-author objectives or use AI drafting under the same
draft-then-review contract as v0.42.0 Companion generation — the model proposes, nothing becomes
ACTIVE without an explicit admin save. A template only becomes `ACTIVE` through an admin action.

### `curriculum_objectives` — the unit coverage is counted in

- `id`, `curriculumTemplateId`
- `subjectLabel` — which Subject plan this objective belongs to when materialized (mirrors the Goal → Subject shape; e.g. "Structural Engineering")
- optional `moduleLabel` — the section grouping within the Subject plan; materializes into item `label` free text, and the backend continues not to interpret labels
- `conceptTitle` — the note-sized concept ("Moment Distribution Method")
- `description` — what a fulfilling note must cover; doubles as the generation brief when the objective reaches the curator queue
- `position` — ordering within the template
- optional `weight` — importance weighting for future weighted coverage; nullable, unweighted by default (weighting semantics are a coverage-session decision, but the column belongs in the model now so weighted coverage is not a migration later)

**Granularity rule: one objective ≈ one note.** The brief consistently counts curricula in concepts
("120 concepts, 102 exist, 18 missing") and the generation unit is a note; making the objective
note-granular keeps fulfillment, gap detection, and generation all counting the same thing. Broader
groupings are expressed by `subjectLabel`/`moduleLabel`, not by coarse objectives.

The objective hierarchy (template → subject → module → objective) deliberately mirrors the archived
Study Plan Architecture v2 shape (Goal → Subject → Module → Note), so materializing a template into
a Review Set is a structure-preserving mapping, not a translation.

### `curriculum_objective_fulfillments` — what a mapping is

The join between requirement and knowledge:

- `id`, `objectiveId`, `noteId`
- `status` — `SUGGESTED` (proposed by matching, whatever the matcher turns out to be) / `CONFIRMED` (admin accepted) / `SUPERSEDED` (was CONFIRMED, displaced when the admin confirmed a different note for the same objective — see the swap-on-confirm rule below) / `REJECTED` (admin declined; kept so the matcher does not re-propose it)
- `matchSource` + optional `matchConfidence` — opaque provenance fields; their semantics belong to the knowledge-matching session, but the columns exist now so that session plugs in without a schema change
- `confirmedBy`, timestamps

Rules:

- Many-to-many: an objective may have several candidate notes, but **at most one CONFIRMED fulfillment is active per objective at a time** (swap-on-confirm rule below); a note may fulfill objectives in multiple curricula — this cross-curriculum sharing is a core cost lever (§3.3).
- **Swap-on-confirm rule:** confirming a fulfillment for an objective that already has a CONFIRMED one automatically moves the previous row to `SUPERSEDED` in the same operation. `SUPERSEDED` (not a revert to `SUGGESTED`) preserves the prior human curation decision — auditable and one-click re-confirmable — without re-entering the suggestion queue. This makes assembly deterministic (every objective yields exactly 0 or 1 confirmed note to auto-slot) and is precisely the behavior behind the Coverage Board's "swap note" action on a Confirmed row in session 03.
- **Only `CONFIRMED` fulfillments pointing at currently-PUBLIC notes count toward coverage.** A SUGGESTED mapping is curator workspace state, invisible to learners.
- Fulfillments target notes in the **canonical public library** (plus admin-owned drafts while inside the curator workspace, per pipeline step 2). Learner-owned private notes are never harvested into fulfillments — privacy boundary, restated in §2.5.
- Confirming a fulfillment is always a human action. The matcher only ever writes `SUGGESTED`.

### `curator_generation_requests` — the review queue

The single funnel through which *all* generation demand flows, from both systems:

- `id`
- `objectiveId` nullable — set when the gap is a known curriculum objective
- `conceptTitle` + `courseProgram` — set for freetext gaps (a learner asks for a concept no ACTIVE template covers); normalized for dedup
- `source` — `CURATOR_GAP_SCAN` / `LEARNER_REQUEST`
- `demandCount` + `lastRequestedAt` — aggregate demand; a repeat request for the same normalized gap increments the counter rather than inserting a row (no per-learner request log in v1; notify-on-publish is a deferred UX question)
- `status` — `REQUESTED` → `DRAFT_GENERATED` → `IN_REVIEW` → `PUBLISHED` / `REJECTED`
- `draftNoteId` nullable — the generated draft (an ordinary PRIVATE admin-owned note), linked once generation runs
- `reviewedBy`, timestamps

This queue **is** the mandatory human-review gate made concrete: the Learning Assistant's
"request generation" capability can only ever create/increment a row here in `REQUESTED` state.
Generation, review, and publish are all curator-side transitions.

## 1.3 Extensions to existing entities

Exactly two nullable columns, both on `note_collections`, both meaningful only on Official top-level
collections (same eligibility shape as Companion: `parentCollectionId == null`):

- `curriculum_template_id` — which curriculum this Official Review Set implements. Gives "this Review Set covers curriculum X" a data anchor, and gives the future Official-identity concept (already anticipated by the collections badge-tier rule) a hook, without overclaiming that identity model now.
- `curriculum_template_version_at_publish` — the template `version` captured at publish time. Divergence from the template's current `version` drives an ADMIN-only outdated signal, exactly the `companionStructureSnapshot` / `companionMayBeOutdated` pattern already shipped (§3.4).

**Deliberately nothing new on `notes` or `study_packs`.** Fulfillments reference notes from the
outside; notes do not know about curricula. Every existing note flow (copy, publish, generation,
quotas) is untouched by this feature.

**Adopt semantics for the new columns:** never copied onto adopted personal plans — the curriculum
link is a property of the Official source, like `targetCompletionDate` (never copied) rather than
Companion (copied cross-owner). An adopted plan's connection to its source stays `sourcePlanId`,
which already exists; per-learner coverage-against-curriculum, if ever wanted, is derivable through
`sourcePlanId → curriculum_template_id` at read time — no denormalization onto the copy.

## 1.4 What "coverage" attaches to

Coverage is a property of **(curriculum template, public library)** — global and learner-independent:

```
coverage(template) = |objectives with ≥1 CONFIRMED fulfillment to a PUBLIC note| / |objectives|
```

- **Derived, never persisted per learner** — same discipline as readiness ("no persisted readiness field" rule). It is a cheap aggregate over `curriculum_objective_fulfillments`; caching, if ever needed, is per template, not per user.
- **Coverage ≠ readiness.** Coverage answers the curator question "does published content exist for this requirement?" Readiness (ConceptHealth → `ProgressReportService`) answers the learner question "have I mastered what I've adopted?" They are computed from different tables by different systems and must never be merged into one number. The learner-facing question "how prepared am I?" remains exclusively readiness.
- The optional `weight` column allows weighted coverage later; the formula above is the unweighted v1 definition. The coverage-visualization session may refine presentation, not the attachment point.

## 1.5 Considered and rejected alternatives

Recorded so later sessions do not relitigate:

- **Curriculum as a special NoteCollection.** Rejected. A collection is a playlist over *existing owned notes*; a curriculum is a requirements list that must exist *before* notes do — its job is naming what's missing. Overloading `note_collections` would also strain the locked two-level Goal → Subject constraint and the "Goals are note-free containers" rule. The template *materializes into* a collection; it is not one.
- **Objective mapping via note tags or subject values.** Rejected. Tags are learner-authored free metadata, and item labels are explicitly "not taxonomy data" (locked collections rule). Curriculum membership needs status, provenance, and admin confirmation — a real join entity.
- **A third hierarchy level (modules as entities) to mirror the curriculum.** Rejected. The archived Study Plan Architecture v2 and the shipped collections model both resolved modules as item-label sections, not entities. `moduleLabel` materializes into item `label` text and nothing more.
- **Storing coverage snapshots.** Rejected for v1 — derived aggregate, consistent with how readiness and the Goal rollup are computed fresh per request.
- **A separate "generated note" content type.** Rejected. A curator-generated note is an ordinary `NoteEntity` + `StudyPackEntity` so that publish, copy, adopt, quiz, and Progress all work on it with zero special-casing. Its provenance lives in the generation-request row, not in the note's type.

---

# 2. The reuse-first pipeline

## 2.1 Two request granularities

The pipeline runs at two levels, and the cheap one always runs first:

**Whole-request level (Learning Assistant, learner-facing).** A learner expresses a goal ("prepare
for the Civil Engineering Licensure Exam" — capture UX deferred). The Assistant's first and usually
only move: find an ACTIVE Official Review Set linked to the matching curriculum (via
`curriculum_template_id`, falling back to `courseProgram` match — which is what
`DashboardStudyPlanSection`'s recommendation already does today) and recommend adoption. Adoption is
the existing recursive Goal-adopt snapshot copy with `includeStudyPack=true`: zero LLM cost, fully
curated content, done. Only when no Official set exists (or coverage is materially incomplete) does
anything deeper happen — and what happens deeper is a *curator* workflow plus, at most, a queued
request from the learner side.

**Per-objective level (assembly).** Given a curriculum template with unfulfilled objectives —
whether the trigger was an admin building a new Official Review Set or accumulated learner demand —
each unfulfilled objective runs the five-step search below.

## 2.2 The five-step search order

For each objective without a CONFIRMED fulfillment:

**Step 1 — Public Notes.** Search the public library for notes satisfying the objective (matching
internals deferred; the contract is: candidates in, `SUGGESTED` fulfillments out). Among candidates,
prefer notes with ready Study Packs, because the copy/adopt spine carries the pack and downstream
reuse then costs nothing. A match yields a `SUGGESTED` fulfillment awaiting admin confirmation.

**Step 2 — Existing Study Packs.** Two sub-pools: (a) Study Pack state on matched public notes as a
ranking input — a PUBLIC note with a ready pack is a complete reusable unit; one without is still
reusable but leaves generation cost on the adopter; (b) **admin-owned internal drafts** — notes
previously generated for another curriculum or an earlier queue item that were never published. Pool
(b) is curator-visible only, and a draft fulfills an objective only after it is reviewed and
published — it re-enters the gate, never bypasses it. Learner-owned private notes are *not* a pool
at this or any step.

**Step 3 — Official Review Sets.** Reuse of prior *curation*: objectives semantically shared with
another curriculum (engineering fundamentals appearing in both Civil and Mechanical templates) can
borrow that curriculum's CONFIRMED fulfillments as high-confidence `SUGGESTED` mappings here. This
step reuses admin judgment, not just content — the highest-trust suggestion source after direct
confirmation.

**Step 4 — Prior user Review Sets.** Signal, not content: aggregate adoption counts and
co-occurrence of PUBLIC notes inside learners' plans for the same courseProgram ("learners preparing
for this exam consistently group these three notes"). Strictly over already-public notes — private
plan contents and private notes are never read, surfaced, or aggregated in any attributable way.
Output is ranking signal for Step 1–3 candidates and curator-facing suggestions only.

**Step 5 — Generate only what is missing.** Objectives still unfulfilled become
`curator_generation_requests` (`CURATOR_GAP_SCAN`). Generation produces an ordinary PRIVATE
admin-owned draft note + Study Pack using the existing note-from-topic + Study Pack generation
spine, with `curriculum_objectives.description` as the brief. Batch fan-out uses
`llmParallelTaskExecutor` (locked fan-out rule). Content is calibrated by **courseProgram only,
never learnerLevel** — these are shared notes, and the Learner Level vs Course/Program anti-drift
rule explicitly forbids leveling shared content per-user. PREMIUM LLM tier, matching the v0.42.0
curator-assist precedent.

## 2.3 The Curator / Assistant boundary at each step

| Step | Internal Curator (ADMIN) | Learning Assistant (learner-facing) |
|---|---|---|
| 0. Official set exists | Publishes/maintains the set | **Recommends adoption** — its primary and preferred outcome |
| 1. Public Notes | Sees SUGGESTED fulfillments; confirms/rejects | May recommend individual PUBLIC notes to copy (existing copy CTA spine) |
| 2. Study Packs / internal drafts | Full visibility including unpublished drafts; may fast-track a draft into review | Sees only PUBLIC state; never sees drafts |
| 3. Other Official sets' fulfillments | Reuses confirmed mappings across curricula | Benefits indirectly (better sets exist sooner); no direct exposure |
| 4. Prior user Review Sets | Consumes aggregate signal for ranking/suggestions | Never exposed to other users' plans; own private plans unaffected |
| 5. Generation | **Only actor that generates.** Reviews, edits, publishes | **May only file a request** (`LEARNER_REQUEST`, demandCount dedup). Learner sees an honest "not in the library yet — we've noted it" state (copy deferred) |

Steps 0–4 are read-only searches over published or aggregate data — safe for both systems, because
everything the Assistant can surface has already passed a human publish decision. Step 5 is where
the systems fully separate: the Assistant's *write* access to the entire pipeline is one row type,
in one state, in one queue.

What the Assistant recommends is always something that already exists publicly, so "recommend" is
curation-compliant by construction. The learner's own explicit per-note actions (write a note,
generate their own Study Pack, build their own plan by hand) are the existing product loop and are
untouched — the locked rule governs what the *system* delivers unasked, not what a learner
deliberately creates for themselves under existing quotas.

## 2.4 Where the mandatory human-review gate sits

There are two gates, and the second one already exists in shipped code:

**Gate 1 — content review (new workflow, existing mechanics).** Between `DRAFT_GENERATED` and
`PUBLISHED` on every generation request: an admin reviews and edits the draft note (ordinary note
editing) and explicitly publishes it (ordinary `visibility=PUBLIC` flip). Publishing the note
transitions the request to `PUBLISHED` and upgrades the objective's fulfillment to `CONFIRMED`. The
same gate shape covers *found* content: matcher output is only ever `SUGGESTED`; a human confirms.
And per the v0.42.0 clarified rule, AI-drafted curriculum objectives follow the identical contract:
generation drafts, only an admin save activates.

**Gate 2 — assembly review (already shipped, reused as structural enforcement).** Publishing a
Review Set already validates that every item note (across every child Subject plan) is PUBLIC, and
publishing is already admin-only. Even if a workflow bug let an unpublished draft slip into an
assembling collection, the existing publish validation refuses to expose it. The curation rule is
enforced by the schema and the shipped publish path, not just by process discipline.

Assembly itself (template → Goal + child Subject plans + items with `moduleLabel`-derived labels) is
deterministic materialization orchestrated by a new curator-side service over **existing collection
endpoints** — exactly the Builder Canvas orchestration pattern. Collection CRUD continues to make no
LLM calls (locked collections rule); all generation lives on the note/curator side.

## 2.5 Privacy boundaries (restated as pipeline invariants)

- Learner-owned PRIVATE notes and plans are never candidate content, never harvested, never suggested to others.
- Step 4 signals are aggregates over PUBLIC notes only and are never attributable to an individual learner.
- Generation requests filed by learners carry no learner-visible attribution in v1 (aggregate `demandCount` only).

---

# 3. Knowledge-reuse strategy and cost model

## 3.1 "Generated once, reused forever," extended from the Study Pack precedent

The precedent chain — each step already shipped:

1. Public-note copy carries the linked **Study Pack** (documented intentional exception to the copy-only model) — the generated artifact is reused, not regenerated.
2. Plan adoption applies that copy spine **per item** with `includeStudyPack=true` — adopting a published plan reuses every pack in it.
3. Goal adoption applies it **recursively** across child Subject plans.

Smart Review Planning adds the fourth rung: **assembly itself becomes a reusable artifact.** An
Official Review Set built against a curriculum template is generated-content + curation work done
once, then distributed to every subsequent learner at pure database-copy cost. One level up, the
**curation is reusable across sets**: CONFIRMED fulfillments and ACTIVE templates outlive any single
Review Set — a refreshed set for the same exam, or a new set for an overlapping program, starts from
confirmed mappings instead of from zero.

The reuse ladder: Study Pack (shipped) → plan of packs (shipped) → Goal of plans (shipped) →
**assembled curriculum coverage (this initiative)** — each rung making the marginal learner cheaper.

## 3.2 What is reused vs. regenerated

| Artifact | Reuse behavior |
|---|---|
| Study Pack on a public note | Copied on adopt/copy; never regenerated for the adopter |
| Note content | Copied; content is already locked after generation (Make-a-Copy versioning rule), so a fulfilled objective cannot silently drift |
| Official Review Set structure | Snapshot-copied on adopt (existing semantics); source remains the maintained original |
| Companion | Copied on genuine cross-owner adopt (existing rule) |
| CONFIRMED fulfillments | Reused across Review Sets and across curricula; never per-learner |
| Curriculum template | Reused for every assembly and every coverage computation for that course/program |
| Learner practice (quizzes/exams) | *Not* reused — per-learner by design, already quota'd and tiered; unchanged by this initiative |

## 3.3 Cost model

**One-time, admin-budgeted costs (per curriculum):**

- Objective drafting assist: one bounded curator-assist call per template (v0.42.0 pipeline, PREMIUM tier), plus admin review time.
- Gap-fill generation: `N_missing × (note-from-topic + Study Pack generation)`. This is the dominant LLM cost and it **decays structurally**: `N_missing` shrinks as the library grows, and cross-curriculum fulfillment sharing (pipeline step 3) means overlapping programs fill each other's gaps. The brief's example — 120 objectives, 102 already public — prices the curriculum at 18 generations, not 120; the next overlapping curriculum is cheaper still.
- Curator generation draws on an **internal admin budget, never learner Study Pack quota** — consistent with the shipped Companion-generate path (no user feature gate or quota) and with the quota rule that only successful user-initiated Study Pack saves increment user usage.

**Marginal cost per learner: zero LLM.** Adopting an assembled Official Review Set is database copies
through the existing spine. The quota principle the brief asks about ("should reuse be free while
only new generation consumes quota?") falls straight out of shipped behavior: **reuse never consumes
learner quota; only genuinely new generation does** — copying already carries the pack without a
generation call, so there is nothing to meter. Learner practice costs are unchanged and remain
governed by existing plan quotas. Where any *new* learner-facing capability lands on the
FREE/PLUS/PRO ladder is the pricing session's question, bounded by the Monetization philosophy:
statically-served assembled content behaves like the Companion (FREE-static funnel economics);
anything interactive or adaptive sits at PLUS/PRO per the established ladder.

**The flywheel, made concrete by the data model:** `demandCount` on generation requests is the
prioritization signal (fill the most-demanded gaps first → highest reuse per generation dollar);
`coverage(template)` is the progress metric (monotonically rising per curriculum);
cross-curriculum fulfillments are the amortization mechanism (the Nth curriculum's marginal
generation cost trends toward zero). Every generated-and-published note permanently raises coverage
for every current and future curriculum it can fulfill.

## 3.4 Staleness and invalidation (the "cache invalidation" question, scoped)

Reusing the shipped `companionStructureSnapshot` / `companionMayBeOutdated` pattern — lightweight
structural comparison, ADMIN-only signal, no automatic mutation:

- **Curriculum changes:** editing an ACTIVE template's objective set bumps `version`. Official Review Sets whose `curriculum_template_version_at_publish` no longer matches surface an ADMIN-only outdated signal on the existing collection detail read. The curator re-runs assembly for the delta (new objectives run the five-step search; removed objectives flag orphaned items) and republishes deliberately. Never auto-republish.
- **Note replacement:** note content is locked post-generation, so a fulfillment cannot drift silently. Replacing a note (Make a Copy → improve → publish) means CONFIRMING a fulfillment to the successor and retiring the old one — an explicit curation act, not an event cascade.
- **Adopted copies never invalidate:** adoption is snapshot semantics (existing rule). A learner's copy does not change when the source set or template evolves; improved sources benefit future adopters. Any "an updated version is available" learner surface is deferred UX — and would be a *recommendation*, never an auto-update; the versioning rule (no auto-regeneration, explicit confirmation only) applies with full force.

## 3.5 What this document deliberately does not decide

Deferred to later sessions, per the brief: knowledge-matching internals (the matcher behind
`SUGGESTED` fulfillments — embeddings/tags/key-concepts/confidence semantics), coverage
visualization, admin workflow UI, learner experience UI, pricing/tier placement, and all product
terminology (every learner-facing noun here is a placeholder to be resolved through the
product-language session and `getCollectionLabels`).
