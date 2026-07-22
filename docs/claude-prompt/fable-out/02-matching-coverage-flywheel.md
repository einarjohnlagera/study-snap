# Smart Review Planning — Knowledge Matching, Coverage, Flywheel

> **Session 02 of the Smart Review Planning planning series.** Planning only. Covers brief Q2
> (knowledge matching), deliverable 4 (curriculum coverage measurement + visualization), and Q5
> (knowledge flywheel). Builds on the entity model and pipeline decided in
> `01-foundation-architecture.md`; does not redesign it. Admin UI, student UI, pricing, and
> terminology remain deferred to later sessions.
>
> **Amended 2026-07-11:** aligned CONFIRMED-fulfillment cardinality language with session 01's
> swap-on-confirm rule (at most one active CONFIRMED fulfillment per objective; confirming a new
> one moves the prior CONFIRMED row to `SUPERSEDED`).

## Decisions carried forward

**Matching approach — tiered lexical matcher over Key Concepts, human-confirmed, no embeddings in v1:**
- Candidate scoping is categorical: only PUBLIC notes whose `courseProgram` matches the template's
  (fallback: `subjectLabel` match) enter matching. Categories filter; they never match.
- Match unit: normalized string comparison (trim, casefold, punctuation/possessive-strip, token-sort —
  superset of the ConceptHealth dedupe normalization) between `curriculum_objectives.conceptTitle`
  and the candidate note's Study Pack `keyConcepts` + note title.
- Tiers (stored in the opaque `matchSource`): `EXACT_CONCEPT` (0.90), `CROSS_CURRICULUM` (0.95 —
  note already CONFIRMED for an equivalent objective elsewhere), `ALIAS_CONCEPT` (0.75 —
  curator-maintained alias table), `TOKEN_OVERLAP` (0.50–0.70 by Jaccard over concept/title/tag
  tokens, suggest-floor 0.5). Below floor = no suggestion = gap.
- Every automatic match lands as `SUGGESTED`; a human confirms. Confidence never auto-confirms in v1.
- Embeddings are an explicitly deferred recall tier (`EMBEDDING` matchSource slots in without schema
  change); revisit only if lexical recall proves insufficient on real curricula.
- Duplicate-concept avoidance: normalization + alias tier; the curator "Generate missing" action for
  an objective surfaces near-miss matches (sub-floor `TOKEN_OVERLAP` hits) and requires pending
  SUGGESTED fulfillments to be confirmed or rejected first; template save warns on
  normalized-duplicate `conceptTitle`s within one template.
- Matching runs curator-side only: batch gap scan (template activation / on demand) + incremental
  re-match of unfulfilled objectives when a public note is published. Never in a learner request path.

**Coverage metric — objective-weighted CONFIRMED coverage, derived, never persisted per learner:**
- `coverage% = Σ weight(objectives with ≥1 CONFIRMED fulfillment to a currently-PUBLIC note) ÷ Σ weight(all objectives)`;
  default weight 1 reduces to a plain ratio. Computed on read (cheap count query) — no cached number
  to invalidate; visibility changes self-heal by the counting rule.
- Per-objective status ladder: `COVERED` (confirmed, sub-flag pack-ready) → `SUGGESTED` (pending
  review) → `REQUESTED` (open generation request) → `GAP`.
- Visualization reuses the `/progress` grammar: per-subject horizontal bar + count chips
  (`X% covered`, covered / pending / requested / gap), weakest-first sort, unlabeled group last, no
  chart library. Admin-facing; learner-facing coverage display deferred to the student-UI session.
  Coverage and readiness never share a bar, color scale, or vocabulary.

**Confidence model:** `matchConfidence` ∈ [0,1] = tier base + bounded modifiers (Study Pack READY
+0.05, exact `subjectLabel` +0.03, engagement-score presence +0.02; cap 0.99). Orders the review
queue and sets display bands (High ≥0.85 / Medium ≥0.65 / Low). Calibrated by curator confirm-rate
per tier; tier auto-confirm is a deferred decision, default is always-human.

---

# 1. Knowledge matching (brief Q2)

## 1.1 The problem, precisely

Given one `curriculum_objectives` row (`conceptTitle`, `description`, `subjectLabel`, template
`courseProgram`), decide which PUBLIC notes, if any, satisfy it — with provenance a curator can
audit in one glance, because every automatic match is only ever a `SUGGESTED`
`curriculum_objective_fulfillments` row awaiting human confirmation. That review gate reshapes the
engineering target: we are optimizing *curator throughput* (high-precision, explainable
suggestions, ranked by confidence), not fully-automatic accuracy.

## 1.2 Signal evaluation

| Signal | Verdict | Reasoning |
|---|---|---|
| **Embeddings** | Defer | Best recall on paraphrase, but: new infrastructure NoteLib doesn't have (pgvector or external vector service), per-note + per-objective compute cost, and opaque provenance — "cosine 0.83" is unauditable next to "keyConcept `Bernoulli's Principle` equals objective title". Crucially, both sides of our match are *already LLM-normalized vocabulary* (objectives are AI-drafted curator-side per session 01; `keyConcepts` are schema-validated LLM output), so lexical convergence is unusually high here — the classic messy-freetext case for embeddings mostly doesn't apply. `matchSource` is an opaque string by design, so an `EMBEDDING` tier can be added later with zero schema change. |
| **Tags** | Bonus signal only | User-authored, sparse, inconsistent slugs. Decent precision when present, unusable recall. Tokens participate in the `TOKEN_OVERLAP` tier; tags never match alone. |
| **Categories** (`courseProgram`, `subject`) | Scoping pre-filter, never a matcher | "PNLE" tells you a note is in the right universe, not that it teaches fluid-and-electrolyte balance. Used as the hard candidate filter (reusing the v0.25 config-map buckets), which kills most false positives before any scoring runs and keeps the scan cheap. |
| **Key Concepts** | **Primary matcher** | `keyConcepts` are note-granular, LLM-normalized concept titles — the exact same granularity as `curriculum_objectives.conceptTitle` ("one objective ≈ one note-sized concept", session 01). And they already carry the system's coverage semantics: ConceptHealth's rule for what a note can *train* is "the concept appears in the pack's keyConcepts" (exam recording even drops labels that don't exact-match them). Reusing that rule for what a note *covers* keeps one definition of "this note is about concept X" across the whole product. |
| **Curriculum mapping** (manual) | Ground truth + reuse multiplier | `CONFIRMED` fulfillments are the only rows that count. They also feed back into matching: a note confirmed for an equivalent objective in another curriculum is the strongest possible suggestion (a prior human already judged it), which is search-order step 3 from session 01 operating at the matcher level. |

**Recommendation:** tiered lexical matching over Key Concepts, scoped by category, confirmed by
humans. No embeddings, no new infrastructure, one shared definition of concept identity.

## 1.3 The matching pipeline

Per unfulfilled objective, over the category-scoped PUBLIC candidate set:

1. **Normalize** both sides with `normalizeConceptKey()`: trim, casefold, strip punctuation and
   possessives (`Ohm's` → `ohm`), drop stopwords, token-sort (`Resistance and Ohm's Law` ≡
   `ohm law resistance`). This is a strict superset of the trim+casefold dedupe the
   ProgressReportService already applies to keyConcepts — one shared utility, used by matching,
   template duplicate warnings, and the alias table.
2. **T1 `EXACT_CONCEPT` (confidence base 0.90):** normalized `conceptTitle` equals a normalized
   `keyConcept` of the candidate (or its note title).
3. **T2 `CROSS_CURRICULUM` (0.95):** the candidate has a CONFIRMED fulfillment for an objective
   with the same normalized `conceptTitle` under a template with a compatible `courseProgram`.
   Ranked above T1 because it reuses prior human judgment, not just string identity.
4. **T3 `ALIAS_CONCEPT` (0.75):** match via a small curator-maintained alias table
   (`concept_aliases`: alias → canonical, admin-owned). Aliases may be AI-*suggested* inside the
   curator tool — that is Internal-Curator generation with mandatory review, consistent with the
   two-system split; an unreviewed alias never influences matching.
5. **T4 `TOKEN_OVERLAP` (0.50–0.70):** token-set Jaccard between the objective's
   `conceptTitle` + `description` tokens and the candidate's `keyConcepts` + title + tags. Score
   scales linearly into the confidence band; below the 0.5 suggest-floor, the pair is recorded
   nowhere but surfaces as a "near miss" in the generate-missing guard (§1.5).
6. Best tier wins per (objective, note) pair; write one `SUGGESTED` fulfillment with
   `matchSource` = tier name and `matchConfidence` per §1.6. Multiple notes may be suggested for
   one objective — the suggestion side stays many-candidates-per-objective — but at most one of
   them is ever CONFIRMED at a time: confirming a different candidate automatically moves the
   previously-confirmed row to `SUPERSEDED` (session 01's swap-on-confirm rule; re-confirmable,
   never re-suggested), so every objective always resolves to exactly 0 or 1 active confirmed note.

**Where it runs (curator-side only, per the two-system split):**
- **Batch gap scan** — on template activation and on curator demand. Bounded work: objectives ×
  category-scoped candidates, all in-DB string ops.
- **Incremental re-match** — when a note becomes PUBLIC (publish event), match it against the
  *unfulfilled* objectives of ACTIVE templates in its `courseProgram`. This is the hook that lets
  organic community publishing close curriculum gaps with zero generation (§3.2).
- The Learning Assistant never invokes the matcher. Its learner-facing recommendations read only
  CONFIRMED fulfillments and published Review Sets; on a gap it files a `curator_generation_request`
  and stops (session-01 contract, unchanged).

## 1.4 Duplicate-concept avoidance

Two distinct failure modes, two guards:

1. **Generating a note that already exists under a different surface form** ("Ohm's Law" exists;
   objective says "Ohms Law and Resistance"). Guards, in order: normalization (§1.3.1) collapses
   trivial variants; the alias tier catches known domain synonyms; and the **generate-missing
   guard** — the curator's "Generate draft for this objective" action always first displays pending
   SUGGESTED fulfillments *and* sub-floor near-misses (best T4 scores in 0.30–0.49), requiring the
   curator to confirm or reject the suggestions before generation is enabled. Generation is the last
   resort by search-order design; this guard makes it the last resort by UI mechanics too.
2. **Two objectives in one template collapsing to the same concept** (AI-drafted objective lists can
   near-duplicate). On template save, warn on normalized-duplicate `conceptTitle`s within the
   template. Warning, not error — a curator may legitimately want split emphasis — but it must be an
   explicit override.

Rejected suggestions persist as `REJECTED` fulfillments (already in the entity model), so a
re-scan never re-suggests a pair a human already declined — the matcher learns "no" for free.

## 1.5 Match confidence

`matchConfidence` ∈ [0,1], stored on the fulfillment at suggest time (provenance snapshot — not
recomputed later):

```
confidence = tierBase(matchSource)            # 0.95 / 0.90 / 0.75 / 0.50–0.70
           + 0.05 if studyPackStatus = READY  # pack rides the copy spine — higher reuse value
           + 0.03 if subjectLabel exact-match
           + 0.02 if engagement score > 0     # PublicNotesScoringUtils.computeScore — reuse, no new metric
           capped at 0.99                     # nothing automatic is ever 1.0; only CONFIRMED is certain
```

What confidence **does**: orders the curator review queue (highest first, so the cheapest confirms
come first), sets the display band (High ≥0.85 / Medium ≥0.65 / Low <0.65), and breaks ties among
multiple suggestions for one objective. What it **never does** in v1: auto-confirm. The
"curation, never generation" principle extends naturally to curation of *mappings* — a learner-visible
coverage claim is a human judgment.

**Calibration loop (measurement, not automation):** track curator confirm-rate per tier. Expected
healthy shape: T2 ≈ 98%, T1 ≥ 95%, T3 ≈ 85%, T4 ≈ 60%. If T1/T2 sustain near-perfect confirm rates
over a meaningful decision volume, auto-confirming those tiers becomes a *deliberate future
decision* with data behind it — explicitly deferred, not designed in.

---

# 2. Curriculum coverage (deliverable 4)

## 2.1 What is counted: objectives, with optional weight

Session 01 fixed the frame — coverage attaches to the template, derived, never persisted per
learner. This section pins the metric.

- **Concepts vs. learning objectives is a false choice here**: objectives were deliberately defined
  note-granular ("one objective ≈ one note-sized concept"), so the objective *is* the canonical
  concept row. Notes' `keyConcepts` are the supply side of the match, not the coverage denominator.
  Counting notes or keyConcepts would double-count (one note can fulfill several objectives; one
  objective can be fulfilled by alternative notes).
- **Weighted importance** rides the existing optional `weight` column:
  `coverage% = Σ weight(covered objectives) ÷ Σ weight(all objectives)`, default weight 1 →
  plain ratio. Ship the unweighted display first; the formula is weight-ready the moment a curator
  sets one, with no schema or metric change.
- **Only CONFIRMED fulfillments to currently-PUBLIC notes count** (session-01 rule, restated because
  it is the invalidation mechanism — see §3.3). Suggested matches and open generation requests are
  *pipeline*, not coverage.

**Per-objective status ladder** (derived, mutually exclusive, in precedence order):

| Status | Condition | Chip color family |
|---|---|---|
| `COVERED` | ≥1 CONFIRMED fulfillment to a PUBLIC note (sub-flag: `packReady` when that note's Study Pack is READY) | green |
| `SUGGESTED` | no CONFIRMED, ≥1 pending SUGGESTED fulfillment | amber |
| `REQUESTED` | an open `curator_generation_requests` row for this objective | blue |
| `GAP` | none of the above | neutral/red |

The headline number counts only `COVERED`. A secondary "fully study-ready" figure (covered by a
pack-READY note) is worth showing to curators because pack-ready notes are what make adoption
zero-LLM — it is the reuse-quality number, mirroring how Public Library "Featured" already gates on
`STUDY_PACK_READY`.

## 2.2 Computation: derived on read, nothing persisted

Coverage is a count query over fulfillments joined to note visibility — the same "derived, never
persisted" doctrine as ConceptHealth readiness and for the same reason: no stored aggregate means no
stale aggregate. A note flipping PRIVATE or being deleted changes the next read's result with no
invalidation machinery (§3.3). If template lists grow large enough to matter, a short-TTL in-process
cache is a pure optimization, never a correctness dependency.

## 2.3 Visualization: the `/progress` grammar, not a chart library

My Progress already teaches users (and the codebase) a complete visual grammar for
"portion complete + categorical counts": horizontal readiness bar, `X% ready` label, three count
chips, weakest-first sort, `Other` last, fixed milestone checkpoints. Curriculum coverage reuses the
grammar wholesale:

- **Template header (admin curriculum detail):** one coverage bar — `68% covered` — with count
  chips `34 covered · 5 pending review · 3 requested · 8 gaps`. Structurally the compact
  `ReadinessSummary` layout; implemented as a sibling `CoverageSummary` component that copies the
  layout pattern rather than parameterizing `ReadinessSummary` itself, because the vocabularies must
  not blur (next bullet). The bar may render the covered segment solid and a pending-review segment
  hatched/translucent — an admin-only "in the pipeline" affordance with a one-segment precedent in
  the milestone bar's fill-by-checkpoint pattern.
- **Coverage ≠ readiness, visually enforced.** Session 01's rule ("does published content exist" vs
  "has this learner mastered it") gets a presentation clause: the two never share a bar, a color
  scale, or a vocabulary word. Coverage says `covered / pending / requested / gap`; readiness says
  `ready / mastered / due / not started`. A learner must never read a green curriculum bar as
  personal mastery.
- **Per-subject grouping:** objectives group by `subjectLabel` (exactly as `/me/progress` groups by
  Study Pack subject), each group with its own coverage bar, sorted lowest-coverage-first —
  weakest-first is the established sort convention and it is also the curator's work queue order.
  Unlabeled objectives group last (the `Other`-last rule).
- **Per-objective rows:** status chips per the §2.1 ladder — same chip pattern as note-detail
  per-concept readiness chips. A `COVERED` row shows its fulfilling note — always exactly one, per
  the swap-on-confirm rule — with confidence band and `matchSource`; showing `SUPERSEDED` history
  ("previously fulfilled by X, swapped to Y") is an optional provenance detail for the admin-UI
  session to take or drop. A `GAP` row carries the request/generate affordances (admin-UI session
  details the interactions).
- **Template milestones (optional, cheap):** the fixed-checkpoint milestone card pattern transfers
  directly if wanted — `First objective covered / 25% / 50% / No gaps unrequested / 75% / Fully
  covered` — computed from the same derived counts, not persisted, no new endpoint shape. Flagged as
  optional polish, not core scope.
- **Learner-facing coverage display is deferred to the student-UI session.** The one constraint set
  now: if an Official Review Set ever advertises its curriculum linkage to learners, it may state
  fact ("covers all 50 objectives of X") only at 100%, and must use coverage vocabulary, never
  readiness vocabulary. Partial-coverage marketing on a learner surface invites the conflation §2.3
  exists to prevent.
- **No new chart library.** Bars, chips, and counts — the entire surface is composable from existing
  patterns. Radar charts, heatmaps, and treemaps are explicitly out: they would be the first of
  their kind in the product for a page whose job is a work queue.

---

# 3. Knowledge flywheel (Q5)

## 3.1 The loop, and what actually spins it

```
gap scan → generate missing (curator queue, human review) → publish
   → matcher indexes the new PUBLIC note → future curricula match instead of generate
   → learners adopt via the copy spine (zero LLM) → demandCount steers the next generation
```

Cost-per-curriculum trajectory: curriculum #1 in a domain is mostly generation (bounded, one-time,
admin-budgeted — session-01 cost model). Curriculum #2 in an adjacent domain hits T1/T2 matches from
#1's published corpus; the Nth curriculum's marginal generation approaches zero as the public
concept corpus densifies. **The matcher is the flywheel's bearing**: every point of matching recall
is generation avoided, which is why §1 invests in normalization, aliases, and cross-curriculum reuse
rather than raw generation throughput. Distribution cost is already zero by construction — adoption
is DB snapshot copies over the `includeStudyPack` spine, never touching learner quota.

Generation priority within the gap queue: `demandCount` desc (learner demand first), then objective
`weight`, then coverage impact (a gap in a 40%-covered template beats one in a 95%-covered
template). Every generated draft passes the full curator review gate before publish — priority
ordering changes *what gets drafted first*, never *whether review happens*.

**Flywheel health metrics** (curator-facing, derived): reuse ratio (objectives fulfilled by existing
vs. newly generated content, per template), generation spend per curriculum, time-to-full-coverage,
and open `demandCount` backlog. The reuse ratio trending toward 1.0 across successive curricula is
the single number that says the flywheel is working.

## 3.2 The zero-generation path

The incremental publish hook (§1.3) makes community publishing part of the flywheel: any user
publishing a public note gets it matched against unfulfilled objectives of ACTIVE templates in its
`courseProgram`, producing SUGGESTED fulfillments in the curator queue. A gap can close with zero
LLM spend because a student happened to publish good notes on exactly that concept — the ideal case
of "curation, never generation": the curator's job collapses to confirming a match. The Public
Library engagement signals (views/copies/likes via the existing scoring util) help the curator pick
the best of several community candidates.

## 3.3 Cache invalidation when source notes change

First, an inventory of what is actually "cached" — three artifacts with three different freshness
regimes:

| Artifact | Regime | Mechanism |
|---|---|---|
| Coverage numbers | **Self-healing** — derived on read | Nothing stored, nothing to invalidate. A note flipping PRIVATE/deleted drops out of the CONFIRMED-to-PUBLIC count on the next read; the objective's status ladder re-derives to `SUGGESTED`/`GAP`. An admin alert fires when a covered objective loses its last public fulfillment, so silent coverage regressions still get eyes. |
| Fulfillments (objective → note mappings) | **Flag, never auto-revoke** — the Companion staleness pattern | At CONFIRM time, snapshot the note's content fingerprint (its `updatedAt` plus a hash of the matched keyConcept set) into the fulfillment's provenance. When the note is later edited or its Study Pack explicitly regenerated, compare: content-only drift raises an admin-only `mayBeOutdated` flag (a typo fix must not crater coverage); the matched concept vanishing from the regenerated pack's `keyConcepts` raises the stronger `MATCH_BASIS_REMOVED` severity and pushes the fulfillment into a re-review queue. Human decides: reconfirm, remap, or reject. Exactly the `companionMayBeOutdated` shape — signal, then deliberate action. |
| Adopted Review Set copies (in learner libraries) | **Immutable snapshots** — no invalidation ever | Session-01/versioning doctrine, restated because it is the flywheel's trust anchor: adopted copies never auto-update, source-note churn never reaches a learner's library, and improvement flows forward to *future* adopters only. |

Curriculum-template edits use the already-decided version mechanism: template `version` bump →
linked Official Review Sets with a stale `curriculum_template_version_at_publish` show the
admin-only `mayBeOutdated` signal → deliberate re-assembly. Deleted objectives cascade-remove their
fulfillments; added objectives enter as `GAP` and are picked up by the next incremental scan. No
path auto-republishes anything.

## 3.4 How Review Sets improve over time

Improvement is a curator-queue flow, same as everything else:

- **Better-fulfillment suggestions.** When a new CONFIRMED fulfillment for objective X materially
  beats the note currently in a linked Official Review Set — pack-READY where the current item
  isn't, or a clearly higher engagement score (`PublicNotesScoringUtils.computeScore`, reused as-is;
  no new quality metric) — the set surfaces an admin-only improvement suggestion: "objective X now
  has a stronger fulfillment than item Y." Curator swaps the item, republishes deliberately.
  Existing adopters keep snapshots; new adopters get the better set. Same signal-then-action shape
  as every staleness flag above.
- **Gap-fill completion.** A set published at partial coverage (curator's call) accumulates
  suggestions as remaining objectives get covered; re-assembly folds them in. Coverage-over-time on
  the template is the natural progress readout.
- **Aggregate learning signal (future, flagged not scoped).** ConceptHealth could eventually
  surface, in aggregate and anonymously, concepts that adopters consistently fail across a set —
  a content-quality hint for the curator ("this note may explain X poorly"), feeding a
  revise-and-republish loop. Deferred: it needs an aggregation surface that provably never exposes
  individual learner data, and readiness data flowing anywhere near coverage risks the conflation
  rule. Noted so a later session designs it deliberately or kills it.
- **What never happens:** auto-swap of set items, auto-republish on a better match, auto-update of
  adopted copies, or any learner-visible change without a curator publish action. The flywheel gets
  faster by making *confirmation* cheaper and *generation* rarer — never by removing the human from
  the loop.
