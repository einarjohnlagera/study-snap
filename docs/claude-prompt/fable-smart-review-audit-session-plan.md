# Fable Session Plan — Smart Review Planning & Product-Language Audit

> **Purpose.** The user has a separate, expiring Fable model allocation (good until **2026-07-12**). This
> file breaks the unscoped 12-deliverable brief
> (`docs/claude-prompt/smart-review-planning-and-product-language.txt`) into a **sequence of small,
> independently-sized planning sessions**, each sized to fit comfortably inside one Claude Pro usage
> window. Fable produces **planning documents only** — it does not touch code or the running app.
>
> A later normal Claude Code session (Sonnet/Opus) turns whichever plans get approved into
> Codex/Claude implementation prompts via this repo's kickoff → scoped-PR → signoff workflow.
> **Fable is not in the implementation path.**

---

## Hard constraints that appear in EVERY session prompt

These are pasted verbatim into each prompt below because **Fable starts cold each session** and will
not have read `AGENTS.md` or `CLAUDE.md`. A loaded doc "mentioning" a rule is not the same as the
rule being in the prompt.

1. **Planning only.** No code edits, no migrations, no running the app, no writes anywhere except the
   one designated output file for that session.
2. **"Curation, never generation" is a locked anti-drift rule.** A learner never receives an
   auto-generated plan / Review Set / note without a NoteLib curator (admin) publishing it first.
   Do not design any flow that violates this. Do not re-litigate it.
3. **Internal Curator vs. Learning Assistant split is the resolution, not a topic to reopen.**
   - *Internal Curator (admin-facing):* may generate content, detect gaps, suggest subject plans,
     generate missing notes — always with **mandatory human review before publish**.
   - *Learning Assistant (student-facing):* **recommend and reuse only** — organize, recommend
     existing notes, reuse existing Study Packs, surface curriculum coverage. It may *request*
     generation of missing concepts, but that request enters the curator review queue; it never
     auto-publishes to the learner.
   Preserve this split in everything you design.
4. **Build on shipped architecture, not a green field.** NoteLib already has: the Companion
   (curator-authored static guidance), v0.42.0 per-section AI-assist authoring, Study Plan Hierarchy
   (Goal → Subject, 2-level), ConceptHealth-derived readiness, `getCollectionLabels` profile-aware
   labels, `FeatureGateService` tiering, and the existing ROADMAP sections "Future, gated —
   AI-generated Review Sets" and "Monetization philosophy." Extend these; do not reinvent them.

---

## Recommended order & dependencies

Each output doc is **independently valuable** — if the 2-day allocation runs out mid-chain, every
completed session still leaves a usable standalone document. That is the reason the coupled,
foundational work goes first and the synthesis capstone goes last.

```
S1  Foundation & Architecture ............ FIRST (everything downstream depends on it)
      │
      ├── S2  Knowledge Matching + Curriculum Coverage + Flywheel   (loads S1 summary)
      ├── S3  Admin (Internal Curator) Workflow Redesign            (loads S1 summary)
      ├── S4  Student (Learning Assistant) Experience + UX          (loads S1 summary)
      └── S5  Monetization Recommendation (bounded, flagged)        (loads S1 summary)
      │
S7  Technical Approach + Phased Roadmap ... LAST (loads S1–S5 summary blocks only)

S6  Terminology Audit + Rename Map ........ INDEPENDENT — run any time, no dependency on S1–S7
```

**Sequencing guidance:**
- **S1 must run first.** It is the anchor; S2–S5 and S7 all consume its decisions.
- **S2, S3, S4, S5 depend only on S1** and can run in any order among themselves.
- **S6 (terminology) is fully independent** — it audits *existing* product copy and does not need any
  architecture decision. Use it as the flex/fill session: run it first as a low-risk warm-up, or slot
  it whenever there's a spare window. (New naming for the *smart-planning* feature itself is decided
  in S4, not S6, so the audit stays independent.)
- **S7 runs last** and loads only the "Decisions carried forward" summary blocks of S1–S5 (see next
  section) — never their full prose, or it overflows its window.

**The mechanism the whole budget constraint rests on — the "Decisions carried forward" block.**
Every session's output doc **must open** with a compact block (≤ ~40 lines) summarizing the load-bearing
decisions downstream needs: entity/data model, the two-system split as applied, search order, coverage
metric, tier placement, etc. Downstream sessions load *that block*, not the whole document. This is what
keeps S7 (and the S2–S5 fan-out) inside a single window. Each prompt below instructs Fable to write this
block first.

---

## Deliverable → session map

| # | Deliverable (from brief) | Session |
|---|---|---|
| 1 | Overall system architecture | S1 |
| 2 | Smart Review Planning architecture | S1 |
| 3 | Knowledge reuse strategy | S1 |
| 2 (Q) | Knowledge matching | S2 |
| 4 | Curriculum coverage strategy | S2 |
| 5 (Q) | Knowledge flywheel | S2 |
| 5 | Admin workflow redesign | S3 |
| 6 | Student experience redesign | S4 |
| 10 | UX recommendations | S4 |
| 7 | Subscription & monetization strategy | S5 (bounded — see flag) |
| 8 | Product language & terminology audit | S6 |
| 9 | Recommended feature renaming | S6 |
| 11 | Technical implementation approach | S7 |
| 12 | Phased roadmap (MVP → v2 → long-term) | S7 |

---

## Deliverable that should NOT go to Fable (flag)

**Deliverable 7 — the pricing/business-model *commitment* — is not Fable's to decide.**
Setting actual prices, choosing the business model, and committing what is free vs. paid are **owner
decisions**, not something an AI should design unilaterally. So S5 is deliberately **bounded**: Fable
produces a *tiering recommendation* that extends the existing "Monetization philosophy" ROADMAP section
(FREE-static / PLUS-interaction / PRO-personalization) and answers the brief's structural question
("should reuse be free while only newly-generated knowledge meters quota?") — but it must present this
as a **recommendation for the owner to ratify, not a decision**, and must **not** invent price points,
quota numbers, or checkout mechanics (those are locked in this codebase). The prompt states this
explicitly. Everything else in the 12 is appropriate for Fable as written planning.

---

# Session prompts

Each session below gives: goal, exact reads, prior-output to load, the ready-to-paste prompt, the output
path, and a sizing justification.

---

## S1 — Foundation & Architecture

**Goal.** Produce the anchor: overall system architecture, the Smart Review Planning ("reuse first,
generate second") pipeline, and the knowledge-reuse strategy — with the Internal Curator vs. Learning
Assistant split as the spine. This is deliverables 1, 2, 3.

**Reads (scoped — not the whole codebase):**
- `docs/claude-prompt/smart-review-planning-and-product-language.txt` (the full brief — 614 lines)
- `docs/features/collections.md`
- `docs/features/companion.md`
- `docs/features/public-notes.md`
- `docs/features/study-pack-generation.md`
- `docs/archive/STUDY_PLAN_ARCHITECTURE_V2.md`
- `docs/product/ROADMAP.md` — **only** lines ~577–596 ("Future, gated — AI-generated Review Sets"
  through the "Monetization philosophy" section)
- `AGENTS.md` — **only** its anti-drift / locked-rules section (skim; do not read all 1628 lines)

**Load from prior output:** none (this is the first session).

**Prompt to paste:**

```
You are Fable, running in a fresh Claude Code session on the NoteLib repo. You are acting as a product
architect + AI-systems designer. This is a PLANNING task. You will NOT edit code, run the app, or write
to any file except the single output file named at the end.

HARD CONSTRAINTS (do not violate, do not re-litigate):
1. Planning only. No code edits, no running the app. Write ONLY to the output file named below.
2. "Curation, never generation" is a locked rule in this codebase: a learner NEVER receives an
   auto-generated plan/Review Set/note without a NoteLib admin publishing it first.
3. The resolution — which you must preserve, not reopen — is a two-system split:
   - Internal Curator (admin-facing): MAY generate content / detect gaps / suggest subject plans /
     generate missing notes, ALWAYS with mandatory human review before publish.
   - Learning Assistant (student-facing): recommend and REUSE ONLY. It may request generation of a
     missing concept, but that request enters the curator review queue — it never auto-publishes to
     the learner.
4. Build on shipped architecture, not a green field: the Companion (curator-authored static guidance),
   v0.42.0 per-section AI-assist authoring, Study Plan Hierarchy (Goal → Subject, 2 levels),
   ConceptHealth-derived readiness, getCollectionLabels profile-aware labels, FeatureGateService
   tiering, and the ROADMAP's existing "Future, gated — AI-generated Review Sets" + "Monetization
   philosophy" sections. Extend these; do not reinvent them.

READ THESE FILES (and only these — do not sweep the codebase):
- docs/claude-prompt/smart-review-planning-and-product-language.txt (full)
- docs/features/collections.md
- docs/features/companion.md
- docs/features/public-notes.md
- docs/features/study-pack-generation.md
- docs/archive/STUDY_PLAN_ARCHITECTURE_V2.md
- docs/product/ROADMAP.md lines 577-596 only (the "AI-generated Review Sets" + "Monetization
  philosophy" sections)
- AGENTS.md — only its anti-drift / locked-rules section

PRODUCE (deliverables 1, 2, 3 of the brief):
1. Overall system architecture for Smart Review Planning — the entities/data model needed (what a
   curriculum objective is, how a Review Set maps to objectives, what "coverage" attaches to), and how
   it sits on top of the existing NoteCollection / Companion / Study Pack model. Reuse existing entities
   wherever possible; call out precisely what is genuinely new vs. an extension.
2. The "reuse first, generate second" Smart Review Planning pipeline: the search order (Public Notes →
   existing Study Packs → Official Review Sets → prior user Review Sets → generate-only-missing), where
   the Internal Curator vs. Learning Assistant boundary falls at each step, and where the mandatory
   human-review gate sits.
3. Knowledge-reuse strategy: how "generated once, reused forever" applies to Review Sets (extending the
   copy-whole-Study-Pack precedent), and the resulting cost model.

Do NOT design knowledge-matching internals, coverage visualization, admin UI, student UI, pricing, or
terminology — those are separate later sessions. Stay at architecture + data model + pipeline + reuse.

OUTPUT FORMAT: Begin the document with a section titled "## Decisions carried forward" (max ~40 lines)
that compactly states the entity/data model, the two-system split as you applied it, and the reuse
search order — this block is what later sessions will load instead of re-reading your full doc. Then the
full detail below it.

Write your entire output to exactly this file (create it; do not touch any other file):
docs/claude-prompt/fable-out/01-foundation-architecture.md
```

**Output file:** `docs/claude-prompt/fable-out/01-foundation-architecture.md`

**Sizing justification.** Reads are ~1 large brief + 4 mid feature docs + ~20 lines of ROADMAP + one
AGENTS section — bounded. Q2 knowledge-matching was deliberately *moved out* to S2 (per advisor) so S1
stays a clean architectural anchor and doesn't overflow. This is the most important single window, so it
runs first and carries only the load-bearing structure.

---

## S2 — Knowledge Matching + Curriculum Coverage + Flywheel

**Goal.** The technical-design cluster: how NoteLib decides a Public Note satisfies a curriculum
objective (matching), how coverage is measured and visualized, and how the knowledge flywheel /
cache-invalidation evolves. Brief Q2 (knowledge matching), deliverable 4 (curriculum coverage), Q5
(knowledge flywheel).

**Reads (scoped):**
- `docs/features/public-library.md`
- `docs/features/note-detail.md` (for the existing Key Concepts / ConceptHealth model)
- `docs/features/my-progress.md` (existing coverage/readiness visualization patterns to reuse)
- `docs/features/study-pack-generation.md` (only if not already fresh from S1 context)

**Load from prior output:** the **"Decisions carried forward" block** of
`docs/claude-prompt/fable-out/01-foundation-architecture.md` (not the full doc).

**Prompt to paste:**

```
You are Fable, running fresh in a Claude Code session on the NoteLib repo. PLANNING ONLY — no code, no
app, write only to the output file named at the end.

HARD CONSTRAINTS (identical every session — do not violate or reopen):
1. Planning only; write ONLY to the named output file.
2. "Curation, never generation": a learner never receives auto-generated content without an admin
   publishing it first.
3. Two-system split you must preserve: Internal Curator (admin) may generate WITH mandatory human
   review; Learning Assistant (student) recommends/reuses only and can only REQUEST generation into the
   curator queue.
4. Build on shipped architecture (Companion, ConceptHealth-derived readiness, Study Pack model,
   getCollectionLabels, existing my-progress coverage visuals) — extend, don't reinvent.

FIRST, load context: read ONLY the "## Decisions carried forward" block at the top of
docs/claude-prompt/fable-out/01-foundation-architecture.md (not the whole file). That block is your
entity model and pipeline; build on it, do not redesign it.

THEN read these files (only these):
- docs/features/public-library.md
- docs/features/note-detail.md
- docs/features/my-progress.md

PRODUCE (brief Q2 + deliverable 4 + Q5):
1. Knowledge matching: how NoteLib determines whether an existing Public Note satisfies a curriculum
   objective. Evaluate embeddings vs. tags vs. categories vs. Key Concepts vs. curriculum mapping;
   recommend an approach that reuses the existing Key Concept / ConceptHealth model where possible.
   Cover duplicate-concept avoidance and how match confidence is measured.
2. Curriculum coverage: how coverage is measured (concepts vs. learning objectives vs. weighted
   importance) and how it is VISUALIZED, reusing the existing my-progress readiness/progress-bar
   patterns rather than a new chart library.
3. Knowledge flywheel: how the "generate missing → admin review → publish → future learners reuse →
   cost approaches zero" loop evolves; how cache invalidation works when source notes change; how
   existing Review Sets improve over time. Keep every generation step behind the curator review gate.

OUTPUT FORMAT: open with "## Decisions carried forward" (≤40 lines: chosen matching approach, coverage
metric, confidence model) for later sessions to load; then full detail.

Write your entire output to exactly this file (create it; touch no other file):
docs/claude-prompt/fable-out/02-matching-coverage-flywheel.md
```

**Output file:** `docs/claude-prompt/fable-out/02-matching-coverage-flywheel.md`

**Sizing justification.** Matching and coverage are one technical cluster (both answer "does note X
satisfy objective Y / how many of N are satisfied"), so they belong together and were pulled here to
unload S1. Three focused feature docs + one summary block = comfortably one window.

---

## S3 — Admin (Internal Curator) Workflow Redesign

**Goal.** Redesign the admin assembly workflow into the Internal Curator tool: create Review Set →
suggest Subject Plans → auto-attach existing notes → highlight missing → generate only missing → admin
review → publish. Deliverable 5 + brief Q6.

**Reads (scoped):**
- `docs/features/admin-dashboard.md`
- `docs/features/bulk-generation.md`
- `docs/features/ai-suggestions.md`
- `docs/features/collections.md` (only if not fresh from S1)

**Load from prior output:** the **"Decisions carried forward" block** of
`fable-out/01-foundation-architecture.md`.

**Prompt to paste:**

```
You are Fable, fresh in a Claude Code session on the NoteLib repo. PLANNING ONLY — no code, no app,
write only to the named output file.

HARD CONSTRAINTS (every session):
1. Planning only; write only to the named output file.
2. "Curation, never generation": nothing reaches a learner without an admin publishing it.
3. Two-system split (preserve, don't reopen): this session is the INTERNAL CURATOR (admin) side. It MAY
   generate/detect gaps/suggest plans/generate missing notes — but EVERY generated artifact passes
   MANDATORY human review before publish. Do not design any auto-publish path.
4. Build on shipped architecture (bulk-generation, ai-suggestions, the Study Plan Builder, Companion
   authoring + v0.42.0 per-section AI-assist) — extend, don't reinvent.

FIRST load context: read ONLY the "## Decisions carried forward" block of
docs/claude-prompt/fable-out/01-foundation-architecture.md.

THEN read (only these):
- docs/features/admin-dashboard.md
- docs/features/bulk-generation.md
- docs/features/ai-suggestions.md

PRODUCE (deliverable 5 + Q6): the redesigned Internal Curator workflow, replacing today's manual
"create Review Set → create Subject Plans → find notes → generate missing → publish" with:
create Review Set → system suggests Subject Plans → auto-attaches existing matching notes → highlights
gaps → generates ONLY missing notes (into the review queue) → admin reviews → publishes. Specify each
screen/step, what the system proposes vs. what the human confirms, where the review gate sits, and how
this reuses bulk-generation + ai-suggestions + the existing authoring-assist pipeline. Call out the
low-volume caveat (dev data shows very few Official PUBLIC top-level Review Sets today) and design so the
workflow earns its keep even at low volume.

OUTPUT FORMAT: open with "## Decisions carried forward" (≤40 lines: the workflow steps + where the
review gate sits); then full detail.

Write your entire output to exactly this file (create it; touch no other file):
docs/claude-prompt/fable-out/03-admin-curator-workflow.md
```

**Output file:** `docs/claude-prompt/fable-out/03-admin-curator-workflow.md`

**Sizing justification.** Three admin/generation feature docs + one summary block. Self-contained; no
dependency on S2's matching internals (it consumes matching as a black box from the S1 pipeline).

---

## S4 — Student (Learning Assistant) Experience + UX

**Goal.** Design the ideal learner experience ("I want to prepare for the Civil Engineering Licensure
Exam" → detect existing knowledge → show coverage → suggest Official Review Sets → reuse → request-only
for missing) plus concrete UX recommendations. Deliverables 6 and 10 + brief Q4. Also decides the
*naming* of the new smart-planning feature surface (kept here, not in the terminology session, so the
audit stays independent).

**Reads (scoped):**
- `docs/features/dashboard.md`
- `docs/features/collections.md`
- `docs/features/learn-page.md`
- `docs/features/navigation.md`
- `docs/features/guidance.md`
- `docs/product/ROADMAP.md` — **only** the "Review-Set-Centric Navigation" section (lines ~599–626)

**Load from prior output:** the **"Decisions carried forward" block** of
`fable-out/01-foundation-architecture.md`.

**Prompt to paste:**

```
You are Fable, fresh in a Claude Code session on the NoteLib repo. PLANNING ONLY — no code, no app,
write only to the named output file.

HARD CONSTRAINTS (every session):
1. Planning only; write only to the named output file.
2. "Curation, never generation": a learner never receives an auto-generated plan/Review Set/note.
3. Two-system split (preserve): this is the LEARNING ASSISTANT (student) side — it RECOMMENDS and
   REUSES only. It may surface "we can prepare missing material" but that request goes to the curator
   queue; it NEVER auto-generates-and-shows to the learner. The learner must never see raw unreviewed
   generated content.
4. Build on shipped architecture (Dashboard TodaysFocusCard/Coach, Companion display, Study Plan
   Hierarchy, ConceptHealth readiness, getCollectionLabels profile-aware labels) — extend, don't
   reinvent. Any new "Primary Review"/"My Reviews" style label must route through getCollectionLabels,
   not hardcoded universal copy.

FIRST load context: read ONLY the "## Decisions carried forward" block of
docs/claude-prompt/fable-out/01-foundation-architecture.md.

THEN read (only these):
- docs/features/dashboard.md
- docs/features/collections.md
- docs/features/learn-page.md
- docs/features/navigation.md
- docs/features/guidance.md
- docs/product/ROADMAP.md lines 599-626 only (Review-Set-Centric Navigation)

PRODUCE (deliverables 6 + 10 + Q4):
1. The end-to-end student experience: learner states a goal → system detects existing knowledge → finds
   reusable notes + Study Packs → shows curriculum coverage → suggests Official Review Sets → offers to
   REQUEST missing material (into the curator queue) — screen by screen, with empty/partial/complete
   coverage states. Keep it "the app knows what I should study next," never "the app has AI."
2. Concrete UX recommendations that fit the existing nav, Dashboard, and collection surfaces.
3. The student-facing NAME for this feature surface (e.g. "Create Review Plan" / "Build Study Journey")
   — outcome-first, no "AI" in the label — and how it resolves through getCollectionLabels per profile.

OUTPUT FORMAT: open with "## Decisions carried forward" (≤40 lines: the experience flow + chosen feature
name); then full detail.

Write your entire output to exactly this file (create it; touch no other file):
docs/claude-prompt/fable-out/04-student-experience-ux.md
```

**Output file:** `docs/claude-prompt/fable-out/04-student-experience-ux.md`

**Sizing justification.** Five UX-oriented feature docs (each small) + ~27 ROADMAP lines + one summary
block. Consolidating deliverables 6+10 here avoids a separate thin "UX recommendations" window.

---

## S5 — Monetization Recommendation (bounded, flagged)

**Goal.** A *recommendation* (not a decision) for how Smart Review Planning is tiered, extending the
existing "Monetization philosophy," and answering "should reuse be free while only newly-generated
knowledge meters quota?" Deliverable 7 + brief Q7. **See the flag section above** — pricing/business-
model commitment stays with the owner.

**Reads (scoped):**
- `docs/features/pricing.md`
- `docs/features/subscriptions-and-usage-limits.md`
- `docs/product/PLANS.md`
- `docs/product/ROADMAP.md` — **only** the "Monetization philosophy" section (lines ~587–596)

**Load from prior output:** the **"Decisions carried forward" block** of
`fable-out/01-foundation-architecture.md` (for the reuse/cost model).

**Prompt to paste:**

```
You are Fable, fresh in a Claude Code session on the NoteLib repo. PLANNING ONLY — no code, no app,
write only to the named output file.

HARD CONSTRAINTS (every session):
1. Planning only; write only to the named output file.
2. "Curation, never generation" stays locked; the Internal Curator vs. Learning Assistant split stays
   intact regardless of tiering.
3. This is a RECOMMENDATION FOR THE OWNER TO RATIFY, NOT A DECISION. You must NOT invent price points,
   quota numbers, pass durations, or checkout mechanics — those are locked in this codebase and are the
   owner's call. Extend the EXISTING "Monetization philosophy" (FREE-static / PLUS-interaction /
   PRO-personalization); do not redesign pricing.
4. Build on shipped tiering (FeatureGateService, UserUsageEntity quotas, the FREE/PLUS/PRO ladder).

FIRST load context: read ONLY the "## Decisions carried forward" block of
docs/claude-prompt/fable-out/01-foundation-architecture.md (for the reuse/cost model).

THEN read (only these):
- docs/features/pricing.md
- docs/features/subscriptions-and-usage-limits.md
- docs/product/PLANS.md
- docs/product/ROADMAP.md lines 587-596 only (Monetization philosophy)

PRODUCE (deliverable 7 + Q7), framed explicitly as a recommendation:
1. Which tier Smart Review Planning belongs in, justified against the existing FREE-static /
   PLUS-interaction / PRO-personalization line — where does "assemble a Review Set from existing
   reusable material" sit, and does adaptive/personalized planning belong at PRO?
2. A recommendation on the structural question: should REUSE of existing notes/Study Packs be free
   while only NEWLY-GENERATED knowledge consumes quota? Argue the cost-sustainability + adoption
   tradeoff. Do NOT set the numbers — recommend the PRINCIPLE and flag the number as owner-decided.
3. An explicit "Owner must decide" list at the end: every lever you deliberately did NOT set (prices,
   quotas, exact free/paid line) so the owner ratifies rather than inherits an AI's guess.

OUTPUT FORMAT: open with "## Decisions carried forward" (≤40 lines: recommended tier placement +
reuse-free/generation-metered principle, both marked RECOMMENDATION); then full detail + the
"Owner must decide" list.

Write your entire output to exactly this file (create it; touch no other file):
docs/claude-prompt/fable-out/05-monetization-recommendation.md
```

**Output file:** `docs/claude-prompt/fable-out/05-monetization-recommendation.md`

**Sizing justification.** Four small pricing docs + ~10 ROADMAP lines + one summary block — the lightest
session. Bounded deliberately because most of the "work" is *not* deciding (the flag), so the window is
about extending a principle, not designing a pricing system.

---

## S6 — Terminology Audit + Rename Map (independent)

**Goal.** The "sell outcomes, not AI" product-language audit and a canonical old→new rename map, plus
the policy for where "AI" is allowed to appear (marketing/landing/pricing) vs. banned (core product).
Deliverables 8 and 9. **Runs independently of S1–S7.**

**Reads (scoped — NOT 320 frontend files):**
- `docs/features/branding.md`
- `docs/features/landing.md`
- `docs/features/onboarding.md`
- `docs/features/pricing.md`
- The label/copy infrastructure files, to anchor the rename map to real routing points:
  - `frontend/lib/exam-mode-visibility.ts`
  - `frontend/src/config/plans.ts` (`getUpgradeCtas`)
  - the `getCollectionLabels` source file (Fable can grep for it)
- The brief's own "Product Language & Branding" + "Product Copy Audit" sections
  (`docs/claude-prompt/smart-review-planning-and-product-language.txt`, lines ~440–554)

**Load from prior output:** none (independent).

**Prompt to paste:**

```
You are Fable, fresh in a Claude Code session on the NoteLib repo, acting as a content strategist.
PLANNING ONLY — no code, no app, write only to the named output file.

HARD CONSTRAINTS (every session):
1. Planning only; write only to the named output file. Do NOT edit any .tsx/.ts copy — you produce a
   rename MAP, a later implementation session applies it.
2. "Curation, never generation" and the Internal Curator vs. Learning Assistant split stay intact —
   naming must not imply the student gets auto-generated content.
3. Naming must route through the EXISTING label infrastructure, not hardcoded universal strings:
   getCollectionLabels (profile-aware Study Plan / Review Set / Lesson Plan / Collection), getUpgradeCtas
   (upgrade copy), exam-mode-visibility. Renames that ignore these are invalid.

DO NOT read all 320 frontend files. Read ONLY:
- docs/features/branding.md
- docs/features/landing.md
- docs/features/onboarding.md
- docs/features/pricing.md
- frontend/lib/exam-mode-visibility.ts
- frontend/src/config/plans.ts
- the getCollectionLabels source (grep for "getCollectionLabels" to find it, read that one file)
- docs/claude-prompt/smart-review-planning-and-product-language.txt lines 440-554 (Product Language &
  Product Copy Audit sections)

PRODUCE (deliverables 8 + 9):
1. A policy statement: where "AI" MAY appear (landing, marketing, pricing, docs, blog) vs. where it is
   banned (core in-product surfaces — feature names, menu labels, buttons, empty states, success
   messages, upgrade prompts, onboarding). "Sell outcomes, not AI."
2. A canonical OLD → NEW rename map as a table: current term → recommended term → surface → which label
   infra routes it (getCollectionLabels / getUpgradeCtas / static). Cover the brief's examples (AI
   Review Set Builder → Create Review Plan; AI Note Generator → Create Notes; AI Quiz Generator →
   Practice Quiz; AI Flashcards → Key Concepts; AI Companion → Learning Companion) and any other
   AI-branded in-product copy the docs reveal.
3. Flag any rename that would require touching label infrastructure vs. plain static copy, so the
   implementation session knows the blast radius.

Recommended tone: student-first, outcome-focused, calm, intelligent, premium — without mentioning AI.

OUTPUT FORMAT: open with "## Decisions carried forward" (≤40 lines: the AI-allowed-vs-banned policy +
the top renames); then the full rename-map table.

Write your entire output to exactly this file (create it; touch no other file):
docs/claude-prompt/fable-out/06-terminology-rename-map.md
```

**Output file:** `docs/claude-prompt/fable-out/06-terminology-rename-map.md`

**Sizing justification.** The advisor's key point: this is bounded to one window precisely because it
produces a *rename map anchored to label infra*, NOT a 320-file sweep. Four small docs + ~3 infra files
+ one brief excerpt. Fully independent, so it is the safe flex/warm-up session.

---

## S7 — Technical Approach + Phased Roadmap (capstone)

**Goal.** Synthesize everything into a technical implementation approach and a phased roadmap
(MVP → v2 → long-term), expressed so a later Claude/Codex session can turn it into kickoff→PR→signoff
release scope. Deliverables 11 and 12.

**Reads (scoped — summary blocks only):**
- The **"## Decisions carried forward" block** (only that block) of each of:
  - `fable-out/01-foundation-architecture.md`
  - `fable-out/02-matching-coverage-flywheel.md`
  - `fable-out/03-admin-curator-workflow.md`
  - `fable-out/04-student-experience-ux.md`
  - `fable-out/05-monetization-recommendation.md`
- `docs/product/ROADMAP.md` — **only** lines ~1–15 (current baseline / what's in progress) for release
  cadence context
- (S6's rename map is not a dependency; reference it only if convenient.)

**Load from prior output:** the summary blocks of S1–S5 (explicitly *not* their full prose).

**Prompt to paste:**

```
You are Fable, fresh in a Claude Code session on the NoteLib repo. PLANNING ONLY — no code, no app,
write only to the named output file. This is the synthesis capstone.

HARD CONSTRAINTS (every session):
1. Planning only; write only to the named output file.
2. "Curation, never generation" locked; Internal Curator vs. Learning Assistant split preserved through
   every phase.
3. Build on shipped architecture and this repo's release workflow (kickoff → scoped PR → signoff). Your
   roadmap must be expressible as that workflow — you are NOT authorizing implementation, only sequencing
   it for a later session to scope.

CRITICAL — to stay within budget, load ONLY the "## Decisions carried forward" block at the top of each
of these files (do NOT read their full bodies):
- docs/claude-prompt/fable-out/01-foundation-architecture.md
- docs/claude-prompt/fable-out/02-matching-coverage-flywheel.md
- docs/claude-prompt/fable-out/03-admin-curator-workflow.md
- docs/claude-prompt/fable-out/04-student-experience-ux.md
- docs/claude-prompt/fable-out/05-monetization-recommendation.md
Also read docs/product/ROADMAP.md lines 1-15 only (current baseline) for cadence context.

If any of the five input files is missing (that session wasn't run), note it as a gap and proceed with
what exists — do not block.

PRODUCE (deliverables 11 + 12):
1. Technical implementation approach: the build order across backend (entities/matching/coverage/
   generation-queue) and frontend (admin curator UI, student experience), what reuses existing systems
   vs. what is new, and the main technical risks.
2. Phased roadmap: MVP → v2 → long-term, each phase mapped to concrete NoteLib release-sized chunks
   (the kind that become releases/vX.Y.Z), with dependencies and the "prove-it-out" gates already in
   the ROADMAP (e.g. gated on authoring-assist proving out, gated on low-volume caveat). Every phase
   must keep the Curation/two-system split intact.

OUTPUT FORMAT: open with "## Decisions carried forward" (≤40 lines: the phase list + gates); then full
detail.

Write your entire output to exactly this file (create it; touch no other file):
docs/claude-prompt/fable-out/07-technical-approach-roadmap.md
```

**Output file:** `docs/claude-prompt/fable-out/07-technical-approach-roadmap.md`

**Sizing justification.** Loads only five ~40-line summary blocks + ~15 ROADMAP lines — deliberately the
lightest input footprint of any session precisely because it is the one that would otherwise blow its
window by re-reading five full prior docs. This is why the "Decisions carried forward" block is mandatory
in every earlier session.

---

## Summary

- **7 sessions.** S1 anchor first; S2–S5 fan out from S1's summary; S6 terminology independent (flex/
  warm-up); S7 capstone last, loading only summary blocks.
- **Every prompt** restates the four hard constraints verbatim (Fable starts cold each session), names
  exactly which files/line-ranges to read, and names exactly one output file under
  `docs/claude-prompt/fable-out/`.
- **Budget mechanism:** every output opens with a compact "Decisions carried forward" block; downstream
  sessions load those blocks, never full prose — this is what keeps S7 and the fan-out inside single
  windows.
- **Flagged out of Fable's authority:** the pricing/business-model *commitment* in deliverable 7 — S5 is
  bounded to a recommendation with an explicit "Owner must decide" list; no invented prices/quotas.
- Each output doc is independently valuable, so a mid-chain budget exhaustion still leaves usable work.
```