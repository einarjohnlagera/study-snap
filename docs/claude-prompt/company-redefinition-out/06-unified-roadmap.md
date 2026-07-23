# 06 — Unified Roadmap (Synthesis Capstone)

Synthesizes `company-redefinition-out/01–05` and `fable-out/07` into one phased,
release-sequenced roadmap. Planning only — see closing section.

## Decisions carried forward

**Phase order (unchanged from the brief; no deviation found):**
1. **Phase 1 — R1 practice-first activation onboarding branch.** Cheapest, most reversible,
   instruments its own read. Gate to enter: **none** — this phase generates the evidence later
   phases consume. Maps to `company-redefinition-out/02`.
2. **Phase 2 — R2 IA/Explore convergence.** Gate: Phase 1's activation-funnel behavioral read
   comes back **positive-or-ambiguous** — the same non-blocking convention ROADMAP already uses
   for Retention H1+H5 ("v0.48.0 cohort read positive-or-ambiguous"), so a flat/negative read
   doesn't shelve Phase 2 forever, it just removes the urgency. Maps to
   `company-redefinition-out/03`.
3. **Phase 3 — R3 cross-user question pool + bounded reusable-object model.** Highest one-time
   engineering cost; sequenced last of the build-work phases. No gate is stated in the source doc
   — this session proposes one (adoption volume on a shared Official Review Set crossing a
   concurrency threshold, observable from existing adopt/copy telemetry) since building it before
   multiple users actually share a source is pure cost with no realized saving. Splits into a
   no-dependency foundation slice and an authoring slice that needs a review-queue mechanism to
   exist. Maps to `company-redefinition-out/04`.
4. **Phase 4 — R4 packaging/terminology delta.** A business decision, not an engineering
   dependency — explicitly free to move earlier than Phases 1–3 the moment the owner ratifies it.
   Sequenced last here only because it's non-urgent polish, not because anything blocks it. Gate:
   `05`'s own explicit "Owner must decide" section (§4) must be resolved before kickoff. Maps to
   `company-redefinition-out/05`.

**Backlog Index rows this roadmap supersedes or folds into** (exact row names from
`docs/product/ROADMAP.md`):
- **Smart Review Planning (Internal Curator, 7 docs)** — partially folded, not closed. Phase 3
  carves out and re-gates only the bounded-object-model/cross-user-pool/Reviewer-relabel slice;
  the rest (templates, matcher, gap-fill queue, Plan-My-Review wizard) stays exactly as Parked,
  same original gate.
- **AI-generated Review Sets / Runtime Companion (Ask Companion, Personalization)** — splits.
  The "AI-generated Review Sets" half is effectively closed (ruled out) by the locked
  curation-never-generation architecture this whole effort re-affirms. "Runtime Companion / Ask
  Companion / Personalization" is untouched by any phase here and should stay Parked separately.
- **Review-Set-Centric Navigation** — partially advanced, not resolved. Phase 2 ships a bounded
  convergence (Explore + Progress promotion) that deliberately stops short of full
  Review-Set-centricity (Library stays a separate, non-Review-Set-organized concern).
- **Product-language row** — no standalone row exists today; it's bundled into the Smart Review
  Planning row's source citation (`smart-review-planning-and-product-language.txt`). Phase 4 is
  where the actual terminology-delta content now lives; a future ROADMAP edit should cross-reference
  it from that row rather than opening a new one.

---

# Full detail

**MVP → v2 → long-term mapping:** Phase 1 (R1) is the MVP entry — smallest, cheapest, ships
first and stands alone. Phase 2 (R2) is the v2 layer — depends on Phase 1's read, builds the
convergence surface that gives Phase 3 something worth pooling. Phases 3 and 4 (R3/R4) are the
long-term tier — R3 for cost/scale reasons (highest one-time engineering cost, wait for adoption
volume to justify it), R4 for business-decision reasons (no technical dependency, just sequenced
last by convention).

**Cross-cutting finding:** none of Phases 1–4 depend on ever unparking fable-out's Smart Review
Planning curriculum-authoring pipeline (templates/matcher/gap-fill/Plan-My-Review) — Official
Review Sets, `ExamQuestionPool`, and Collections already exist pre-fable-out. The one exception is
Phase 3's *authoring* slice (pool expansion), which needs *some* review-queue mechanism — either
that pipeline finally shipping, or a small standalone queue built as new Phase-3 scope.

## Phase 1 (R1) — Practice-first activation onboarding branch

**Source:** `company-redefinition-out/02-activation-onboarding.md`.

**What ships:** for `profileType === BOARD_EXAM` with a depth-qualifying Official Review Set
already published for the collected `courseProgram`, the onboarding flow skips Steps 3–4 and
replaces them with one "Confirm & Practice" screen → existing free/instant `adopt`/`adopt-goal` →
existing `POST /auth/onboarding` (triggered earlier) → routes straight into a Quick Review session
on the first Study-Pack-ready note in the adopted plan. No qualifying Review Set → falls through
to the unchanged 5-step flow. `STUDENT`/`TEACHER`/`PROFESSIONAL` are untouched.

**Confirmed architecturally sound, not an open risk:** `adopt`/`adopt-goal` already carries each
member note's linked Study Pack into the copy (same copy-Study-Pack path public-note copy uses,
`includeStudyPack` defaults true) — so "zero generation cost" is a fact about shipped code, not a
promise this roadmap is making on faith.

**Reuses from fable-out/01–07:** the "Activation entry point" row from `01`'s reuse table —
Exam Hub, the existing free/idempotent adoption model, per-owner `ExamQuestionPool` — all pre-date
and sit outside the fable-out Curator pipeline; nothing here waits on fable-out shipping.

**Genuinely new relative to fable-out:** fable-out never proposed an onboarding-branch UX change
or a retention-diagnosis mechanism at all ("Validation approach — (fable-out never addressed
retention diagnosis)" per `01`'s table). The branch-decision logic, the one new "Confirm &
Practice" screen, and the cohort-event instrumentation (Exam Hub landing → adopt → first practice
→ Companion guidance seen → readiness number seen) are wholly new here.

**Behavioral gate to enter:** none — first phase, cheap, reversible, and the whole point is that
it produces the read the rest of the roadmap consumes.

**Validation built into the release itself:** pre/post on the *same covered tracks*
(BOARD_EXAM learners on an already-covered course/program, W1→W2 retention just-before-ship
forced-create-first vs. just-after practice-first-fires) rather than a naive cross-track A/B,
because create-first vs. practice-first cohorts today are confounded with covered-vs-uncovered
tracks. Floor ~30 completed onboardings/arm directional, ~75+/arm decision-grade. Window: 14 days
after the last onboarding in the intake window.

**Release-sized chunk (illustrative numbering — actual number assigned at real `/kickoff`):**
one release, e.g. `releases/v0.57.0 — Practice-First Activation`. Scope fits one PR-sized branch:
onboarding step-machine branch condition, one new screen reusing the existing
`DashboardStudyPlanSection` card at promoted prominence, and new `AnalyticsEventType` entries for
the five funnel events (added to the enum before firing, per convention). No migration, no new
entity, no LLM call on this path.

---

## Phase 2 (R2) — IA / Explore convergence

**Source:** `company-redefinition-out/03-information-architecture.md`.

**What ships:** authenticated nav becomes `Dashboard / My Reviews / Library / Explore / Progress`.
Dashboard's hero slot becomes the learner's Primary Review Set as a condensed card, falling back to
the existing goal-prompt flow when unset; the full subject rollup moves fully to Progress (no
duplication). Explore is a new nav item — not a new canonical URL — compositing the existing
Official Review Set catalog (`/collections/published`) and the existing `/public/library` behind a
segmented control, plus a pointer to the Exam Hub index. Progress is promoted from sub-page to
first-class nav item (drops its `← Dashboard` back link — a concrete `navigation.md` gap this
session surfaces). `/exam/[slug]` gains one additive check: resolve the hub's configured
`courseProgram`(s) against the existing published-Official-Review-Set lookup; a match adds a
preview+adopt path reusing the existing anonymous preview and `redirectTo` handoff — no new cookie
field. Library stays untouched and structurally separate from Collections.

One new, explicitly-flagged recommendation (not a resolution): adopting an Official Review Set
with no existing Primary sets it as Primary. This does not resolve ROADMAP's still-open
Primary-Review-Set-vs-Study/Exam-Focus philosophy question — it stays open, unaffected by this IA
lock-in.

**Reuses from fable-out/01–07:** effectively none structurally. fable-out/07 explicitly exempts
this direction ("Review-Set-Centric nav — stays deferred exactly as ROADMAP.md states; nothing
here advances it") — Phase 2 is built entirely on already-shipped, pre-fable-out mainline
machinery instead: v0.41.1 Primary-card hierarchy, `PlanPicker` + `?collectionId=`,
`getCollectionLabels`, the copy-funnel's existing `redirectTo` param.

**Genuinely new:** the Explore nav item as a composite surface; Progress's promotion to top-level
nav; the Exam-Hub-to-published-Official-Review-Set additive match/preview/adopt path; the
adopt-sets-Primary-if-none-exists recommendation (flagged, not resolved).

**Behavioral gate to enter:** Phase 1's funnel read is positive-or-ambiguous. This session's own
"Gap-check" section already frames this exact dependency, so Phase 2 kickoff is contingent on that
check having actually been re-opened and read, not merely time having passed.

**Release-sized chunks (illustrative):**
- `releases/v0.58.0 — Explore Convergence`: new Explore nav item, segmented Review-Sets/Notes
  control, Exam Hub additive official-set check.
- `releases/v0.59.0 — Dashboard & Progress Reorg`: Dashboard hero → Primary Review Set condensed
  card, Progress promotion (drop back link, default-to-Primary `PlanPicker` view), the
  adopt-sets-Primary recommendation shipped as an explicitly flagged, easily-revertible default.

Two chunks because the new composite surface (Explore) and the reorg of two existing pages
(Dashboard, Progress) are different risk profiles — the former is additive/purely composing
existing routes, the latter touches default states on pages users already rely on daily.

**Producing more Official Review Sets remains bottlenecked on the separate, still-unscoped Curator
pipeline** — this session flags that gap twice and this roadmap does not solve it in Phase 2;
it's the same gap Phase 3's authoring slice runs into below.

---

## Phase 3 (R3) — Cross-user question pool + bounded reusable-object model

**Source:** `company-redefinition-out/04-reusable-assets-and-reviewer.md`.

**What ships, foundation slice (3a):** `ExamQuestionPoolEntity`/`ExamQuestionPoolService` stay
exactly as shipped for the authoring/content side. A new `resolvePoolKey(studyPackId)` step inside
`ExamQuestionPoolService` only (zero call-site changes elsewhere) resolves the pool to the Official
source's `studyPackId` when the caller's note is Official or a one-hop copy of one, falling
through to today's per-owner keying otherwise — derived on read, never persisted, self-healing.
Two things change on the shared branch only: served-question tracking moves off the pool row into
a new per-user child table (`exam_question_pool_progress`, keyed `pool_id`+`user_id`) so one
adopter's sample-without-replacement history can't bleed into a stranger's; and the
`learnerLevel`-triggered auto-refresh is dropped for Official pools (shared content is never
leveled per-user). Private per-owner pools stay fully unchanged.

**What ships, authoring slice (3b):** curator-side pool expansion (generation, gated) with batches
landing pending-review before READY, reusing the review-queue shape fable-out/01 already
specified. Exhaustion or source-content drift raises an admin-only signal (Companion-staleness
shape) instead of auto-regenerating.

**Bounded object model:** of the proposal's 8 fields, 5 need zero new work (Note, Summary, Key
Concepts, Explanations, Related Notes). Flashcards stays fully derived (its ~56% coverage ceiling
is a documented non-goal). Difficulty is cut — a generation-time knob already exists via
`DIFFICULTY_SELECTION`. **The Curated Question Pool — i.e., exactly the 3a resolver + child table
above — is the only genuinely new build in the entire bounded model.**

**Reviewer decision:** (b), label-only, no new entity — `getCollectionLabels("BOARD_EXAM")`
already returns "Review Set"; "Reviewer" ships as a relabel of that same machinery. This closes an
otherwise-open question outright rather than deferring it.

**Reuses from fable-out/01–07:** the review-queue *shape* (Approve & Publish pattern,
pending-before-READY) for slice 3b; the general "shared content is never leveled per-user"
principle already implicit in fable-out's Official-pool framing. `ExamQuestionPool` itself
pre-dates fable-out (Board Exam Mode) — not a fable-out artifact being reused, a pre-existing one.

**Genuinely new:** the entire cross-user resolver + per-user progress table (3a) — this codebase
has never had a case where multiple users read/write against one shared served-question history
before; the Reviewer label-only closure; the "no auto-regen for Official pools, admin signal
instead" policy.

**A real dependency this synthesis surfaces, not smoothed over:** slice 3b's "review queue" does
not exist anywhere in the codebase today (checked — no `ReviewQueue`/pending-review pattern found
in `backend/src/main/java` or `docs/features/`). fable-out/01's review-queue design is the only
place this shape has been specified. Practically, 3b either waits for fable-out's Curator pipeline
(fable-out R1–R4: `curriculum-foundation` → `matching-coverage` → `gap-fill-review` →
`assemble-publish`) to actually ship — which the Backlog Index still gates on interviews + a manual
coverage sprint + hand-curation saturating, none of which is scheduled — or 3b needs its own small
standalone review queue built as net-new Phase-3 scope, scoped down from the full curriculum
pipeline. **Recommend scoping 3a and 3b as two separate release-sized chunks precisely so 3a is
never blocked by this.**

**Behavioral gate to enter (proposed by this session, not stated in the source doc):** 3a doesn't
need a gate beyond Phase 3's overall late sequencing — it's cheap to defer and expensive to build
speculatively. Concretely, don't kick off 3a until adoption telemetry (already emitted by
Phase 1/2's adopt/copy paths) shows a real Official Review Set with enough concurrent adopters that
duplicated per-owner LLM pool generation is a measurable cost, not a hypothetical one. 3b
additionally needs the review-queue dependency above resolved one way or the other before it can be
scoped at all.

**Release-sized chunks (illustrative):**
- `releases/v0.60.0 — Shared Official Pool Foundation` (3a: resolver + `exam_question_pool_progress`
  migration + learnerLevel-refresh removal for Official pools + admin staleness signal). No
  review-queue dependency; ships standalone.
- A later, explicitly TBD release for 3b (`Pool Expansion Authoring`), scoped only once the
  review-queue dependency above is resolved.
- Reviewer label-only rename can ship independently, in either chunk or as its own tiny addition —
  it has no dependency on anything.

---

## Phase 4 (R4) — Packaging / terminology delta

**Source:** `company-redefinition-out/05-packaging-and-terminology.md`.

**What ships (once ratified):** Creator (bring-your-own-notes) and Curated Learning (adopt Official
Review Sets) stay one product on the existing FREE/PLUS/PRO ladder — a messaging distinction, not a
pricing fork. No new SKU; if the owner wants a named upsell moment, it's the existing Pro 90-day
exam pass, re-messaged around adaptive planning + Board Exam Mode. Terminology delta, top item:
rename "Generate Note"/"Generate a note" → "Create a Note"/"Draft a Note" (freeform AI-authored
prose with no source note is the feature structurally closest to a generic AI note tool, so it
shouldn't borrow "Generate"'s differentiator language); "Generate Study Pack"/"Generate Quiz"/
"Regenerate Quiz" keep the generation-flavored verb, since that names the real differentiator.
Status/loading copy inherits whichever bucket its parent verb lands in.

**Reuses from fable-out/01–07:** directly and explicitly — `fable-out/05-monetization-recommendation.md`'s
already-recommended tier placement (FREE=adopt, PLUS=conversational assembly, PRO=adaptive
planning) and its recommendation that adoption of Official Review Sets stay FREE and unmetered at
every tier. This session's "one product, not two" framing is built to not contradict that
already-drafted recommendation.

**Genuinely new:** the "one product vs. two products" framing itself; the specific "reuse the
existing Pro exam-pass, re-messaged, no new SKU" answer; the terminology-delta table — which
explicitly **reverses** `fable-out/06`'s blanket "keep [Generate Note], not touched" stance for
onboarding, with an argument `06` didn't make.

**Owner-must-decide gate:** `05` carries its own explicit "§4. Owner must decide (deliberately NOT
set here)" section — the only one of the six input files with a formally headed section by that
name (confirmed by grep across all six; `04`/Phase 3 has no equivalent explicit section, though its
shared-pool privacy/staleness policy choices deserve the same scrutiny even without a formal
heading). Phase 4 cannot be scoped for `/kickoff` until that section's items are actually decided,
not merely acknowledged.

**No engineering dependency on Phases 1–3.** This is the one phase in the sequence that is ordered
last purely by convention (business-decision urgency), not by cost or risk. **If the owner ratifies
the packaging/terminology recommendations sooner, Phase 4 should move earlier — nothing here should
block that.** The terminology-rename slice in particular is small enough (a copy/label change, no
new infra) to be a direct Claude-Code frontend change per this repo's task-routing table rather than
a Codex-scoped release on its own, and could ride along inside any other release's polish bucket
once ratified.

**Release-sized chunk (illustrative, only if tracked as its own thing):** `releases/vX.Y.Z —
Terminology & Packaging Cleanup` — small, most of the diff is copy/label changes plus
marketing/pricing-page messaging, not new backend surface.

---

## Dependency spine (one line per phase)

Phase 1 → (produces) → activation-funnel behavioral read → gates Phase 2 entry (positive-or-
ambiguous) → Phase 2 ships Explore/Progress convergence, independently increasing adoption volume →
gates Phase 3a entry (adoption-concurrency threshold, proposed) → Phase 3a ships shared-pool
foundation; Phase 3b additionally waits on the review-queue dependency (fable-out Curator pipeline
or a standalone build) resolving. Phase 4 has no dependency on any of the above and floats freely
on owner ratification timing.

---

## Nothing here is authorized for implementation

This document sequences four already-drafted planning sessions into one roadmap. It does not
kick off, scope, or authorize any code, migration, or PR. Before any phase's first `/kickoff`:
- The owner must explicitly ratify that phase (this applies to all four phases equally).
- Phase 2 additionally needs Phase 1's behavioral read actually pulled and read as
  positive-or-ambiguous — not assumed.
- Phase 3 additionally needs its proposed adoption-volume gate checked (3a) and the review-queue
  dependency resolved one way or the other (3b) — and, per the general "owner must ratify" rule
  above, its shared-pool policy choices (no auto-regen, admin-only staleness signal, per-user
  progress isolation) should get the same explicit sign-off scrutiny `05`'s items get, even without
  a formally headed section prompting it.
- Phase 4 additionally needs `05`'s own §4 "Owner must decide" items actually decided, not just
  acknowledged.

A future Claude Code session should treat each phase above as raw material for its own
`/kickoff` scoping pass — including re-reading the full body of the relevant
`company-redefinition-out/0X` file (this session read only its "Decisions carried forward" block
per its own budget constraint) before writing any Codex prompt.
