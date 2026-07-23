# Strategic Redefinition: NoteLib as a Learning OS

> Planning document. No code changed. Extends the already-designed Smart Review Planning
> architecture in `docs/claude-prompt/fable-out/01-07`; does not replace or reopen it.

## Decisions carried forward

**One-line identity:** NoteLib is a learning OS — your own notes become curriculum, curation turns
that curriculum into a compounding, reusable asset, and AI is the machinery behind the curtain,
never the thing a learner is asked to trust directly.

**Hybrid-moat thesis:** The reuse search order already designed in fable-out/01 (Official Review
Set → public notes → existing packs → cross-curriculum fulfillments → prior-collection co-occurrence
→ generate-only-missing) means every additional *published* note or adopted collection makes the
next curriculum cheaper to fulfill — fable-out/01 states this directly: "fulfillments shared across
curricula push the Nth curriculum's marginal cost toward zero." boardready.ph has no authorship
inflow (a static bank rebuilt from scratch per exam) and a generic AI note tool has authorship but no
curation-into-verified-curriculum layer (content stays ungoverned, never becomes exam-official) —
NoteLib is the only one compounding both inflows through one pipeline, and only after an owner
opts in by publishing (private notes are never mined).

**Reuses vs. genuinely new (full table in Section 4):**

| Topic | Reuses from fable-out/01-07 | Genuinely new here |
|---|---|---|
| Entities/schema | `curriculum_templates/objectives/fulfillments`, `curator_generation_requests`, `concept_aliases` | None proposed |
| Two-system split | Internal Curator (generates, human-gated) vs. Learning Assistant (recommend/request-only) | Named as the mechanism behind "AI = intelligence layer, never the product" |
| Flywheel mechanic | Reuse search order 0–5; cost model (shared fulfillments → marginal cost → 0) | Framed explicitly as the competitive moat, not just a cost optimization |
| Build order & gates | R1–R7, Gate 1 (prove-out on ALE/PNLE/LET), Gate 2 (FREE ratification) | Gate 1's curricula reframed as the acquisition-sequencing decision |
| Activation entry point | Exam Hub, existing free/idempotent adoption model, per-owner `ExamQuestionPool` | Exam Hub converges into "adopt Official Review Set" as the zero-decision activation, one funnel not two |
| Validation approach | — (fable-out never addressed retention diagnosis) | Cohort-instrumenting the activation funnel as an interview-free value-vs-discovery proxy |

**Chosen interview-free validation mechanism:** instrument the board-exam activation funnel itself
(Exam Hub landing → Official Review Set adopt → first practice completed → Companion guidance seen →
readiness number seen) as discrete, ordered cohort events, then segment the existing W1→W2 retention
metric by *furthest step reached* instead of running interviews. See Section 5.

---

## 1. Redefined identity: NoteLib as a learning OS

NoteLib has, until now, been described (accurately) as "a notes-first study workspace" — Notes in,
Study Packs and quizzes out. That description is still true, but it under-sells what the shipped
architecture, plus the already-designed Smart Review Planning system, actually adds up to. The
redefinition is not a new architecture — it is naming the layers that already exist (or are already
designed) so that product decisions can be made against a coherent whole instead of feature-by-feature:

- **Notes = the knowledge layer.** The atomic unit of authored understanding — still the entity
  every other layer is built from, unchanged.
- **Review Sets = the curriculum layer.** A Note Collection (Study Plan / Lesson Plan / Review Set,
  per profile) is not a folder of notes; once a curriculum template exists behind it
  (fable-out/01–02), it is a *structured claim about what mastery requires* — objectives, mapped
  notes, coverage.
- **The Smart Review Planning reuse pipeline = the flywheel.** The reuse search order (0–5) is the
  literal mechanism that turns one learner's authored, published note into raw material another
  learner's curriculum gets assembled from, at zero incremental generation cost. This is not a
  metaphor — it is the specific `curriculum_objective_fulfillments` matching logic already designed.
- **Companion + Progress/ConceptHealth = the mastery/guidance layer.** The Companion is
  curator-authored static guidance riding on top of a Review Set; ConceptHealth-derived readiness is
  the only mastery-integrity signal. Together they answer "what should I do next, and how close am I."
- **AI = the intelligence layer behind all of it, never the product itself.** AI drafts curriculum
  objectives, runs the gap scan, and generates missing notes for curator review (Internal Curator
  side); it also authors Study Pack content, again always curator- or owner-reviewable before a
  learner sees it. A learner never receives raw model output as the deliverable — they receive
  curated Review Sets, Companion guidance, and Study Packs that a human (the note's own owner, or a
  NoteLib admin for published content) has already stood behind. This is the same sentence as the
  locked "curation, never generation" rule, restated as identity rather than as a guardrail: it is
  not just a constraint on how the product is built, it is *what the product is* — a curation engine
  that happens to use AI as its labor-saving mechanism, not an AI-generation tool that happens to have
  curation as a feature.

Put together: a learner's private authorship (Notes) feeds a public curriculum asset (Review Sets)
through a compounding reuse pipeline (the flywheel), guided by a static mastery layer (Companion +
Progress), all powered by AI that never appears to the learner as raw output. That is the "learning
OS" — not a generator, not a static bank, an operating system that gets more capable every time
someone uses it and publishes.

## 2. The hybrid-moat thesis, concretely

The claim "your own notes + our curation is harder to copy than boardready.ph AND harder to copy
than a generic AI note tool" needs one specific mechanic to be true, not a vibe. That mechanic is
already fully specified in fable-out/01 and just needs naming:

**The mechanic: marginal-cost compounding through shared fulfillments.** Per fable-out/01's cost
model, "generation is one-time and admin-budgeted... fulfillments shared across curricula push the
Nth curriculum's marginal cost toward zero." Concretely: when a Nursing curriculum objective is
fulfilled by a PUBLIC note that a Nursing student happened to author and publish, that same note (or
its Study Pack, or the fact that a prior cohort's Review Sets co-occurred it with a related concept)
becomes a candidate fulfillment for the *next* Nursing curriculum template revision, or for an
adjacent Allied Health curriculum, without another generation call. Every additional real user who
publishes a note makes the next curriculum-fulfillment pass cheaper and faster to curate. This is a
supply-side network effect: value compounds with usage, not just with content volume.

**Why boardready.ph can't replicate this.** A static exam bank has no authorship inflow at all — every
new exam, every content refresh, is rebuilt from scratch by boardready's own team or licensed content.
There is no mechanism by which one user's activity lowers the cost of serving the next user. It can
compete on breadth-at-launch (which is why it wins the zero-decision activation race today) but it
cannot compound the way a reuse pipeline with a live authorship base does.

**Why a generic AI note tool can't replicate this either.** Tools like Notion AI or a bare
GPT-wrapper note app have the authorship inflow (users write notes, AI helps process them) but no
curation-into-verified-curriculum layer sitting on top. Their content stays private, ungoverned, and
un-vetted against any objective structure — it can never credibly become "official CPALE review
material" the way a NoteLib Official Review Set can, because nothing maps individual notes to
curriculum objectives, confirms that mapping with a human, or tracks coverage against a template.
Without that layer, more authorship just means more unsorted private content, not a compounding public
asset.

**The precision that must not be lost:** the compounding only ever touches notes an owner has
already made PUBLIC, and only after curator confirmation — CONFIRMED fulfillments count "to PUBLIC
notes" only, and the prior-collection signal is "over PUBLIC notes only... never attributable." No
private note is ever mined, matched, or surfaced without its owner's own publish action. The moat is
built on an opt-in inflow of public authorship curated by humans, not on scraping private content —
this is a constraint that makes the moat slower to build than a scraped-data approach, but it is also
exactly what keeps it inside the locked curation-never-generation rule and inside NoteLib's existing
privacy model.

NoteLib therefore sits in a position neither competitor occupies: authorship inflow *and* a curation
layer that turns that inflow into governed, presentable, curriculum-mapped content. That combination
is the moat; either piece alone is not.

## 3. Why board-exam is the right acquisition spearhead — and what stays horizontal

**The case for board-exam first:**

- **It matches boardready.ph's proven playbook.** boardready's "zero-decision, start-practicing-
  immediately" activation is the discovery answer the grounding facts already flag NoteLib may be
  missing for exam-dated users. The existing free, idempotent Review Set *adoption* model (snapshot
  copy, no AI call, no note-writing required) already gives NoteLib the same "start immediately with
  zero notes of your own" mechanic boardready relies on — it is under-surfaced as the front door for
  a board-exam-motivated visitor, not absent.
- **It matches NoteLib's strongest existing content depth.** The most recent production pull found
  exactly four course/programs with a published top-level Official Review Set with real depth —
  Accountancy (74 notes, CPALE), Architecture (52, ALE), Education (43, LET), Nursing (63, PNLE) —
  and those are a 1:1 match with the four already-shipped Exam Hubs. This is not a new content bet;
  it is recognizing that the content moat described in Section 2 already exists precisely where the
  acquisition spearhead needs it, for exactly the four board exams the platform is best positioned to
  win on content depth.
- **It converges with, rather than replaces, Exam Hub.** Per the hard constraint, `/exam/[slug]`
  stays the cheap anonymous-SEO top of funnel. The redefinition's role for board-exam is to give the
  Exam Hub visitor a clearer next step once they arrive with intent — adopting the matching Official
  Review Set and landing directly on a readiness-tracked practice surface — rather than to build a
  second, competing authenticated catalog. Any future IA work here is a hand-off design (Exam Hub →
  adopt → Review Set detail-as-study-dashboard, itself already shipped in v0.41.1), not a rebuild.
  Gate 1 of the fable-out/07 build order already names ALE/PNLE/LET as the prove-out curricula for
  Smart Review Planning; sequencing the acquisition push around that same set of curricula (adding
  CPALE, already shipped as a Wave 2 Exam Hub) means the first real Official Review Sets built under
  the new curriculum-template system and the acquisition spearhead are the same content, not two
  parallel efforts.

**What does not change for Student / Teacher / Professional:** the platform stays horizontal by
construction, not by promise — nothing in this redefinition forks the entity model. Note,
StudyPack, `NoteCollection`, ConceptHealth, and quiz-session tables remain single, shared tables
across all profile types; `ProfileType` continues to drive only dashboard emphasis, mode
availability, and label text via `getCollectionLabels`. `FeatureGateService` tiering
(`ADAPTIVE_QUIZ`, `DIFFICULTY_SELECTION`, `WEAK_CONCEPT_DETECTION`) and the FREE/PLUS/PRO
monetization ladder from ROADMAP.md apply identically regardless of which acquisition channel a user
entered through. Board-exam is a go-to-market sequencing choice for where the curriculum-template
system gets built and marketed first — it is not a re-scoping of who the product is for. A Teacher
or Professional user's experience of Notes, Companion, and Progress is untouched by this document.

## 4. Reconciliation: reuse vs. genuinely new (full detail)

| Area | Reuses from fable-out/01–07 (unchanged) | Genuinely new in this redefinition |
|---|---|---|
| Core entities | `curriculum_templates`, `curriculum_objectives`, `curriculum_objective_fulfillments`, `curator_generation_requests`, `concept_aliases`; the 2 nullable `note_collections` columns | Nothing — no new entity, column, or endpoint is proposed here |
| Two-system split | Internal Curator (ADMIN, generates behind mandatory human review) vs. Learning Assistant (learner-facing, recommend/adopt + request-filing only, never generates) | Elevated to identity-level language: this split *is* the mechanism behind "AI = intelligence layer, never the product," not just an implementation guardrail |
| Reuse search order (the flywheel) | The 6-step search order (whole-request → public notes → existing packs → cross-curriculum fulfillments → prior-collection co-occurrence → generate-only-missing) and its cost model | Explicitly named and marketed internally as the competitive moat mechanic (Section 2) — a strategic framing layered on an existing technical design, not a technical change |
| Build order & gates | R1–R7 phase sequencing; Gate 1 (prove-out on ALE/PNLE/LET real Official Review Sets before any learner surface); Gate 2 (owner FREE-tier ratification) | Gate 1's chosen curricula (ALE/PNLE/LET, +CPALE) reframed as *also* the acquisition-sequencing decision — same technical gate, additional strategic weight attached to which curricula get built first |
| Monetization ladder | FREE Companion / PLUS Ask Companion (grounded Q&A) / PRO adaptive guidance — codified in ROADMAP.md's Monetization philosophy section | Nothing — this document does not touch pricing or tiering |
| Acquisition entry point | Exam Hub (`/exam/[slug]`) as anonymous SEO top-of-funnel; existing free/idempotent Review Set adoption (snapshot copy, zero AI call); per-owner `ExamQuestionPool` (currently disabled by default) | The strategic decision to converge Exam Hub → Official Review Set adoption into one explicit "zero-decision activation" narrative for board-exam visitors, competitively positioned against boardready.ph's activation model — a sequencing/positioning choice, not a new surface |
| Retention diagnosis | None — fable-out/01–07 never addressed the W1→W2 retention question or the exam-dated 0% anomaly | The interview-free, funnel-cohort-instrumentation validation mechanism (Section 5) is wholly new — it did not exist in any prior planning session |

The short version: **fable-out/01–07 already designed the entire engine** (entities, matching logic,
build order, gates, monetization ladder). This document does not touch that engine. What is new is
(a) the identity/layer narrative that explains *why* the engine matters competitively, (b) the
explicit choice to sequence the engine's first real build against the board-exam acquisition
spearhead rather than treat curriculum choice as a neutral implementation detail, and (c) a way to
get a behavioral read on the open retention question without running the interviews the owner has
ruled out.

## 5. The biggest risk of proceeding without interviews, and a cheap behavioral substitute

**The risk.** The single largest unresolved fact this whole redefinition rests on is that exam-dated
users retain at 0% (0/41) versus 2.83% (3/106) for non-exam-dated users, and nobody knows whether
that is a **value problem** (an exam-dated learner tries NoteLib, experiences it fully, and it simply
isn't worth a second visit — e.g., not exam-focused enough, not deep enough on their specific board,
missing the specific content mode a board-exam grinder wants) or a **discovery problem** (the value is
already there but nothing in the exam-dated user's actual path surfaces it before they bounce). These
two diagnoses point to opposite fixes. If it's a discovery problem, the board-exam spearhead in
Section 3 (surface the Official Review Set + readiness + Companion faster, with fewer decisions) is
exactly the right move and should work. If it's a value problem, that same spearhead will get
exam-dated users into the product faster and still lose them — worse, it will look like it "should
have worked" and burn a release cycle without moving the number, which is precisely the pattern the
retention track has already lived through three times (v0.44.0, v0.46.0, v0.48.0) on a different
hypothesis. Proceeding without ever separating these two causes risks repeating that exact failure
mode a fourth time, now against a strategically bigger bet.

**The interview-free substitute: cohort-instrument the activation funnel itself.** Since the owner has
ruled out interviews (cold outreach and in-app prompts have both gone unanswered), the cheapest
behavioral proxy is to make the *new* board-exam activation flow self-diagnosing by construction, using
analytics infrastructure the product already has (the `AnalyticsEventType` enum pattern) rather than
asking anyone anything. Concretely, once the Section 3 convergence exists: emit an ordered event per
exam-dated user for each step actually reached — (1) landed on Exam Hub, (2) adopted the matching
Official Review Set, (3) completed first practice/quiz, (4) viewed Companion guidance, (5) viewed a
readiness number — and segment the existing W1→W2 retention metric by *furthest step reached* instead
of by a single "activated" boolean. The read this produces without a single interview:

- If exam-dated users who reach the deepest steps (Companion seen, readiness seen) still retain near
  0%, that is direct behavioral evidence of a **value problem** — they got the full experience and it
  didn't pull them back — and further discovery-focused investment (faster paths, more prominent
  CTAs) should stop being the default next move.
- If most exam-dated users churn *before* reaching the middle steps (e.g., bounce after landing on
  Exam Hub, never reach step 2 or 3), that is direct behavioral evidence of a **discovery problem** —
  the value, if any, was never reached — and the Section 3 spearhead (reducing decisions between
  landing and first practice) is validated as the right lever, with a concrete step-2 conversion
  number to move rather than a vague retention target.

This costs one small instrumentation addition on top of a flow that is already being built for
acquisition reasons — no new user-facing surface, no outreach, no waiting on anyone to respond. It
converts the one open strategic question this whole redefinition depends on into a number the product
owner can read from existing production-pull tooling the same way the 2026-07-22 device-mix and
course-coverage pulls were already read.
