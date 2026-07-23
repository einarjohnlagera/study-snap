# Fable Session Plan — Company Redefinition (Boardready-Model Re-Architecture)

> **Purpose.** The user found `boardready.ph` — a much simpler Philippine board-review product with
> many more users than NoteLib's ~260 — and, with GPT, drafted a full company-redefinition proposal:
> treat public notes as reusable learning assets, make Review Sets the central product, add a
> first-class "Reviewer" object, split pricing into Creator vs. Curated Learning, de-emphasize "AI"
> language, and position NoteLib as a "learning operating system." A pre-Fable Claude Code challenge
> (see `RELEASES.md`-adjacent conversation, not a file) found the proposal **right in direction, wrong
> in sequence** — most of its pillars are already built or already parked (public-note copy already
> carries the Study Pack; a one-time "pass" model already exists; plan copy already has zero "AI";
> the exact target IA is parked as "Review-Set-Centric Navigation"; a 7-document **Smart Review
> Planning** design already exists in `docs/claude-prompt/fable-out/01–07`) — and flagged two real
> risks: (1) the board-exam segment being doubled down on retains at **0%** (0/41 exam-dated users,
> vs. 2.83% non-exam-dated) with no diagnosis of why, and (2) the proposed Creator/Curated pricing
> split targets a lever the data already rules out (free-tier quota is never hit). **The user heard
> this and explicitly decided to proceed anyway**, skipping user interviews (cold outreach and in-app
> feedback surfacing have both gone unanswered) and asking for a **full re-architecture design now**,
> to build in parallel. This file is that design's Fable session plan — it is not a re-litigation of
> that decision.
>
> Fable produces **planning documents only** — it does not touch code or the running app. A later
> normal Claude Code session (Sonnet/Opus) turns whichever phases get ratified into Codex/Claude
> implementation prompts via this repo's `/kickoff` → scoped-PR → `/signoff` workflow. **Fable is not
> in the implementation path.**
>
> **This is a redefinition, not a fresh design.** NoteLib already has a 7-document Smart Review
> Planning architecture (`docs/claude-prompt/fable-out/01–07`) that answers most of what this proposal
> asks for — reuse-first note/Study-Pack search, an Internal-Curator admin workflow, a Learning-
> Assistant student experience, a monetization recommendation, a terminology rename map, and a phased
> roadmap. **Every session below must load and extend that work, not re-derive it.** The one
> genuinely new thread this proposal adds is the **boardready-style practice-first activation wedge**
> and the **full nav/IA + packaging redefinition** — that's what these sessions are for.

---

## Hard constraints that appear in EVERY session prompt

Pasted verbatim into each prompt below because **Fable starts cold each session** and will not have
read `AGENTS.md`, `CLAUDE.md`, or this file. A loaded doc "mentioning" a rule is not the same as the
rule being in the prompt.

1. **Planning only.** No code edits, no migrations, no running the app, no writes anywhere except the
   one designated output file for that session.
2. **"Curation, never generation" is a locked anti-drift rule.** A learner never receives an
   auto-generated plan / Review Set / note / question without a NoteLib curator (admin) publishing it
   first. Do not design any flow that violates this. Do not re-litigate it.
3. **Internal Curator vs. Learning Assistant split is the resolution, not a topic to reopen**
   (established in `fable-out/01-foundation-architecture.md`):
   - *Internal Curator (admin-facing):* may generate content, detect gaps, suggest subject plans,
     generate missing notes/questions — always with **mandatory human review before publish**.
   - *Learning Assistant (student-facing):* **recommend and reuse only** — organize, recommend
     existing notes, reuse existing Study Packs/question pools, surface curriculum coverage. It may
     *request* generation of missing material, but that request enters the curator review queue; it
     never auto-publishes to the learner.
   Preserve this split in everything you design.
4. **Build on shipped architecture, not a green field.** NoteLib already has: the Companion
   (curator-authored static guidance), the `NoteCollection` Goal→Subject hierarchy (2 levels),
   `ConceptHealth`-derived readiness, `getCollectionLabels` profile-aware labels, `FeatureGateService`
   tiering, a per-owner `ExamQuestionPool` (curated question bank, currently disabled by default), a
   one-time-purchase "pass" model already in `plans.ts`, and the **already-designed Smart Review
   Planning architecture** in `docs/claude-prompt/fable-out/01–07`. Extend these; do not reinvent them.
5. **Do not delete Exam Hub.** `/exam/[slug]` is a cheap dynamic route doing **anonymous SEO
   acquisition** (organic search → signup) that an authenticated catalog cannot replace. Any IA
   redefinition must be a **convergence** (Explore surface unifying Official catalog + Public Library +
   Exam Hub deep-links), never a replacement.
6. **NoteLib stays horizontal.** Student / Teacher / Professional / Board-Exam profiles all remain
   supported. Board-exam is the acquisition spearhead, not the only persona.

---

## Recommended order & dependencies

```
R0  Strategic Redefinition & Boardready Thesis ............ FIRST (anchor; everything loads its block)
      │
      ├── R1  Activation & Practice-First Onboarding                (loads R0 block)
      ├── R2  Information Architecture & Explore Convergence        (loads R0 block)
      ├── R3  Reusable Assets, Question Pools & Reviewer Decision   (loads R0 block + fable-out/01,02)
      └── R4  Packaging (Creator vs. Curated) + Terminology Delta   (loads R0 block + fable-out/05,06)
      │
R5  Unified Phased Roadmap & Company Strategy ... LAST (loads R0–R4 blocks + fable-out/07 block only)
```

- **R0 must run first** — it is the redefinition anchor; R1–R4 and R5 all consume its decisions.
- **R1, R2, R3, R4 depend only on R0** and can run in any order among themselves (parallelizable).
- **R5 runs last** and loads only the "Decisions carried forward" summary blocks of R0–R4 plus the
  existing `fable-out/07-technical-approach-roadmap.md` block — never full prose, or it overflows.

**The budget mechanism — the "Decisions carried forward" block**, same convention as
`fable-smart-review-audit-session-plan.md`: every session's output doc **must open** with a compact
block (≤ ~40 lines) stating the load-bearing decisions downstream needs. Downstream sessions load
*that block*, not the whole document.

---

## Deliverable that should NOT go to Fable (flag)

**The pricing/business-model *commitment* in R4 is not Fable's to decide.** Setting actual prices,
committing to a Creator/Curated two-product split as a business decision, and choosing exact quota
numbers are **owner decisions**. R4 is deliberately bounded: Fable produces a *recommendation*
extending the existing "Monetization philosophy" ROADMAP section and the parked `fable-out/05`
recommendation — framed as **input for the owner to ratify**, never as a decision, and it must not
invent price points, quota numbers, or checkout mechanics.

---

# Session prompts

Each session below gives: goal, exact reads, prior-output to load, the ready-to-paste prompt, the
output path, and a sizing justification.

---

## R0 — Strategic Redefinition & Boardready Thesis

**Goal.** Produce the anchor: the "learning OS" narrative, the **hybrid-moat thesis** (a learner's own
notes + NoteLib's curation — neither a pure-AI commodity generator nor a pure static question bank
like boardready.ph can replicate this), and why board-exam is the acquisition spearhead while the
product stays horizontal (Student/Teacher/Professional unaffected). Must explicitly reconcile with —
and extend, not replace — the reuse-first architecture already designed in `fable-out/01` and `07`.

**Reads (scoped — not the whole codebase):**
- `docs/gpt-contexts/GPT_CONTEXT.md` lines 27–49 ("Retention Is the Proven Constraint" section) —
  the retention/posture facts
- `docs/gpt-contexts/GPT_CONTEXT.md` lines 63–115 ("Product Model" + "Note Collections: Vision &
  Evolution" sections)
- `docs/product/ROADMAP.md` lines 1–15 (current baseline) and lines 458–476 ("Future, gated —
  AI-generated Review Sets" through "Monetization philosophy")

**Load from prior output:** the **"Decisions carried forward" block** (only that block) of
`docs/claude-prompt/fable-out/01-foundation-architecture.md` and
`docs/claude-prompt/fable-out/07-technical-approach-roadmap.md`.

**Prompt to paste:**

```
You are Fable, running in a fresh Claude Code session on the NoteLib repo. You are acting as a
product strategist and SaaS founder. This is a PLANNING task. You will NOT edit code, run the app, or
write to any file except the single output file named at the end.

HARD CONSTRAINTS (do not violate, do not re-litigate):
1. Planning only. No code edits, no running the app. Write ONLY to the output file named below.
2. "Curation, never generation" is a locked rule in this codebase: a learner NEVER receives an
   auto-generated plan/Review Set/note/question without a NoteLib admin publishing it first.
3. Preserve, do not reopen, the two-system split from fable-out/01: Internal Curator (admin-facing,
   may generate WITH mandatory human review) vs. Learning Assistant (student-facing, recommend/reuse
   only, may only REQUEST generation into the curator queue).
4. Build on shipped architecture, not a green field: the Companion (curator-authored static
   guidance), the NoteCollection Goal->Subject hierarchy, ConceptHealth-derived readiness,
   getCollectionLabels, FeatureGateService tiering, a per-owner ExamQuestionPool (currently disabled
   by default), an existing one-time-purchase "pass" model, and the ALREADY-DESIGNED Smart Review
   Planning architecture in docs/claude-prompt/fable-out/01-07. You are extending that design, not
   replacing it.
5. Do not delete Exam Hub (/exam/[slug]) - it is cheap anonymous-SEO acquisition an authenticated
   catalog cannot replace. Any future IA must converge with it, not remove it.
6. NoteLib stays horizontal: Student/Teacher/Professional/Board-Exam profiles all remain supported.
   Board-exam is the acquisition spearhead, not the only persona.

GROUNDING FACTS (already established, do not re-derive or second-guess):
- W1->W2 retention is 2.4% (3 of 127 activated users), flat across three prior releases aimed at it.
- Free-tier quota is essentially never hit - pricing is independently ruled out as the retention
  bottleneck.
- Exam-dated users retain at 0% (0/41) vs. 2.83% (3/106) for non-exam-dated users. Nobody yet knows
  whether this is a VALUE problem (they tried it, wasn't worth a second visit) or a DISCOVERY problem
  (the value exists but doesn't surface). The user has explicitly chosen NOT to run diagnostic user
  interviews (cold outreach and in-app feedback surfacing have both gone unanswered) and instead
  wants to proceed on informed founder conviction plus the boardready.ph competitive benchmark.
- boardready.ph (a simpler competitor with many more users) succeeds by offering zero-decision,
  "start practicing immediately" activation - no note-creation step before first practice.
- PDF export of study content is used almost never (1 export, ever, across 260 users) - do not design
  around it as a value driver.

READ THESE FILES (and only these - do not sweep the codebase):
- docs/gpt-contexts/GPT_CONTEXT.md lines 27-49 ("Retention Is the Proven Constraint" section)
- docs/gpt-contexts/GPT_CONTEXT.md lines 63-115 ("Product Model" + "Note Collections: Vision &
  Evolution" sections)
- docs/product/ROADMAP.md lines 1-15 (current baseline) and lines 458-476 ("Future, gated -
  AI-generated Review Sets" through "Monetization philosophy")
- The "## Decisions carried forward" block ONLY (not the full document) of:
  - docs/claude-prompt/fable-out/01-foundation-architecture.md
  - docs/claude-prompt/fable-out/07-technical-approach-roadmap.md

PRODUCE:
1. NoteLib's redefined identity in one line, plus a short "learning OS" narrative: Notes = knowledge
   layer, Review Sets = curriculum layer, the (already-designed) Smart Review Planning reuse pipeline
   = the flywheel, Companion/Progress = the mastery/guidance layer, AI = the intelligence layer behind
   all of it, never the product itself.
2. The hybrid-moat thesis: explain concretely why "your own notes + our curation" is harder to copy
   than boardready.ph's pure static bank AND harder to copy than a generic AI note tool - name the
   specific mechanic that makes it defensible (e.g., the reuse-first pipeline turning private
   authorship into a compounding public asset).
3. Why board-exam is the right acquisition spearhead (matches boardready's playbook, matches NoteLib's
   strongest existing content depth - Accountancy/Architecture/Education/Nursing) while the platform
   stays horizontal for Student/Teacher/Professional - be explicit about what does NOT change for
   those profiles.
4. An explicit reconciliation section: "What this redefinition reuses from fable-out/01-07 vs. what
   is genuinely new" - a short table. Do not silently re-derive the existing reuse-first architecture.
5. Name the single biggest risk of proceeding without user interviews (the 0%-exam-dated-retention
   mystery going undiagnosed) and propose ONE cheap, interview-free way later sessions can get a
   behavioral read instead (e.g., cohort-instrument the new activation flow itself) - do not propose
   running interviews, that has already been ruled out by the owner.

OUTPUT FORMAT: Begin the document with a section titled "## Decisions carried forward" (max ~40
lines) stating: the one-line identity, the hybrid-moat thesis in 2-3 sentences, the
reuses-vs-new table, and the chosen interview-free validation mechanism. Then the full detail below
it.

Write your entire output to exactly this file (create it; do not touch any other file):
docs/claude-prompt/company-redefinition-out/01-strategic-redefinition.md
```

**Output file:** `docs/claude-prompt/company-redefinition-out/01-strategic-redefinition.md`

**Sizing justification.** Two bounded GPT_CONTEXT slices, ~35 ROADMAP lines, and two prior-output
summary blocks (not full docs) — comfortably one window. This is the most load-bearing session, so it
runs alone first.

---

## R1 — Activation & Practice-First Onboarding

**Goal.** Design the boardready-style "everything upfront, start practicing immediately" first-run,
**profile-branched off the profile type already collected at onboarding step 1**: exam-taker/board-
exam profiles skip note-creation entirely and go straight to adopting an Official Review Set → first
quiz; creator-type profiles (Teacher/Professional/Student-without-a-target-exam) keep today's
create-first flow. Must also define the **interview-free validation plan** — since diagnostic
interviews are off the table, this session owns exactly what behavioral signal proves or disproves the
practice-first bet.

**Reads (scoped):**
- `docs/features/onboarding.md`
- `docs/features/dashboard.md`
- `docs/features/collections.md`
- `docs/features/guidance.md`

**Load from prior output:** the **"Decisions carried forward" block** of
`docs/claude-prompt/company-redefinition-out/01-strategic-redefinition.md`.

**Prompt to paste:**

```
You are Fable, fresh in a Claude Code session on the NoteLib repo. PLANNING ONLY - no code, no app,
write only to the named output file.

HARD CONSTRAINTS (identical every session - do not violate or reopen):
1. Planning only; write ONLY to the named output file.
2. "Curation, never generation": a learner never receives auto-generated content without an admin
   publishing it first.
3. Internal Curator vs. Learning Assistant split (preserve, don't reopen): the student-facing side may
   only recommend/reuse existing published material.
4. Build on shipped architecture: onboarding already collects profile type (STUDENT/BOARD_EXAM/
   TEACHER/PROFESSIONAL) and learner level/course-program at step 1-2; `adopt`/`adoptGoal` on a public
   Official Review Set is already FREE, instant, and makes no LLM call; the Study Pack's stored quiz
   already powers Quick Review with zero generation cost. Reuse these; do not build new
   infrastructure to get "start practicing immediately."
5. NoteLib stays horizontal - do not remove or degrade the creator-first flow for Teacher/
   Professional/generic Student profiles.

FIRST, load context: read ONLY the "## Decisions carried forward" block at the top of
docs/claude-prompt/company-redefinition-out/01-strategic-redefinition.md. That is your identity/
thesis anchor; build on it, do not redesign it.

THEN read these files (only these):
- docs/features/onboarding.md
- docs/features/dashboard.md
- docs/features/collections.md
- docs/features/guidance.md

PRODUCE:
1. The redesigned onboarding branch: for BOARD_EXAM (and any exam-dated Student), replace the
   generate-or-write-a-note step with: confirm target exam -> show the matching Official Review Set
   (if depth exists) -> one-tap adopt -> land directly in a first quiz drawn from the adopted Study
   Pack's stored questions. Zero note-authoring required before first practice. Specify what happens
   when no Official Review Set exists yet for the learner's target (do not invent new admin tooling
   here - flag it as a dependency on the admin-curation gap, covered in a separate session).
2. What stays unchanged for TEACHER/PROFESSIONAL/generic STUDENT profiles, and exactly which existing
   onboarding step/component is branched on (profile type already collected at step 1).
3. The interview-free validation plan: define the exact behavioral cohort read that proves or
   disproves the practice-first bet without needing any user reply - e.g., W1->W2 retention split by
   onboarding-path (practice-first vs. create-first) for the SAME board-exam profile, adopt-to-first-
   quiz completion rate, time-to-first-practice. State the minimum cohort size and read window needed
   before treating a result as a signal, following this codebase's existing cohort-read precedent
   (see the 2.4%/0% figures - both were read from small live cohorts, not surveys).
4. Explicitly state what does NOT change: the "Study Pack" concept, note ownership model, or
   generation pipeline are untouched by this - this is an onboarding ROUTING change only.

OUTPUT FORMAT: open with "## Decisions carried forward" (max ~40 lines: the branch logic, what's
unchanged, and the validation metric + minimum cohort/window); then full detail.

Write your entire output to exactly this file (create it; touch no other file):
docs/claude-prompt/company-redefinition-out/02-activation-onboarding.md
```

**Output file:** `docs/claude-prompt/company-redefinition-out/02-activation-onboarding.md`

**Sizing justification.** Four small feature docs plus one summary block — one window. Independent of
R2/R3/R4, so it can run in parallel with them.

---

## R2 — Information Architecture & Explore Convergence

**Goal.** Design the full nav/IA redefinition (`Dashboard / My Reviews / Library / Explore / Progress`)
that the parked "Review-Set-Centric Navigation" ROADMAP section already sketched — Explore unifying the
Official Review Set catalog + Public Library + Exam Hub deep-links (convergence, never deletion), with
all labels routed through `getCollectionLabels`. This session **updates and finalizes** that parked
direction rather than starting fresh.

**Reads (scoped):**
- `docs/features/navigation.md`
- `docs/features/library.md`
- `docs/features/public-library.md`
- `docs/features/exam-hub.md`
- `docs/product/ROADMAP.md` lines 480–499 ("Review-Set-Centric Navigation" section, in full)

**Load from prior output:** the **"Decisions carried forward" block** of
`docs/claude-prompt/company-redefinition-out/01-strategic-redefinition.md`.

**Prompt to paste:**

```
You are Fable, fresh in a Claude Code session on the NoteLib repo. PLANNING ONLY - no code, no app,
write only to the named output file.

HARD CONSTRAINTS (every session):
1. Planning only; write only to the named output file.
2. "Curation, never generation" stays locked regardless of navigation shape.
3. Internal Curator vs. Learning Assistant split preserved - navigation changes do not blur it.
4. Do NOT delete Exam Hub (/exam/[slug]) - it is anonymous-SEO acquisition an authenticated catalog
   cannot replace. Your redesign must be a CONVERGENCE (an Explore surface that houses the Official
   Review Set catalog + Public Library + lets /exam/[slug] deep-link into a matching Official Review
   Set once one exists), not a replacement.
5. Any "Primary Review"/"My Reviews"-style label MUST resolve through the existing getCollectionLabels
   pattern (profile-aware: Study Plan/Review Set/Lesson Plan/Collection) - never hardcoded universal
   copy.
6. NoteLib stays horizontal - the redesigned nav must work for Student/Teacher/Professional too, not
   only Board-Exam.

FIRST load context: read ONLY the "## Decisions carried forward" block of
docs/claude-prompt/company-redefinition-out/01-strategic-redefinition.md.

THEN read (only these):
- docs/features/navigation.md
- docs/features/library.md
- docs/features/public-library.md
- docs/features/exam-hub.md
- docs/product/ROADMAP.md lines 480-499 (the existing "Review-Set-Centric Navigation" section, in
  full - this is the parked direction you are finalizing, not a green field)

PRODUCE:
1. The finalized nav shape: Dashboard / My Reviews (or its profile-aware label) / Library / Explore /
   Progress - what each item shows, and precisely what moves out of Dashboard/Library into this shape
   vs. what stays.
2. The Explore surface design: how the Official Review Set catalog, Public Library, and Exam Hub
   converge into one browsing experience - the exact mechanism by which /exam/[slug] pages deep-link
   into a matching Official Review Set once one exists (per the existing ROADMAP note), and what an
   anonymous visitor vs. an authenticated visitor each see.
3. How Dashboard and Progress reorganize around a learner's Primary Review Set (the existing
   primaryCollectionId concept) while keeping the all-subjects rollup reachable so notes outside any
   Review Set are never orphaned - do not undo the existing Progress/Readiness unification.
4. A gap-check against the R1 (activation) session's dependency: confirm the Explore/catalog surface
   is where a learner without an existing Official Review Set for their exam would be routed, and flag
   (do not solve) the admin-curation-gap dependency again if relevant.

OUTPUT FORMAT: open with "## Decisions carried forward" (max ~40 lines: final nav item list, the
Explore convergence mechanism, and the Dashboard/Progress reorg rule); then full detail.

Write your entire output to exactly this file (create it; touch no other file):
docs/claude-prompt/company-redefinition-out/03-information-architecture.md
```

**Output file:** `docs/claude-prompt/company-redefinition-out/03-information-architecture.md`

**Sizing justification.** Four feature docs + one ~20-line ROADMAP section + one summary block — one
window. Independent of R1/R3/R4.

---

## R3 — Reusable Assets, Cross-User Question Pools & the Reviewer-Object Decision

**Goal.** Design the concrete "curate once, reuse everywhere" build: turning the existing per-owner,
disabled `ExamQuestionPool` into a **cross-user pool on Official (published) Study Packs only**; the
*bounded* reusable-learning-object model (summary/key-concepts/flashcards/curated pool/explanations —
not a maximal 8-part content-ops burden); and an explicit, honest decision on whether "Reviewer"
becomes a first-class object or stays a `getCollectionLabels` label (given PDF export is nearly
unused). Must extend, not re-derive, `fable-out/01` and `02`'s reuse-first pipeline and knowledge-
matching design.

**Reads (scoped):**
- `docs/features/study-pack-generation.md`
- `docs/features/quiz-session.md`
- `docs/features/public-notes.md`

**Load from prior output:** the **"Decisions carried forward" block** of
`docs/claude-prompt/company-redefinition-out/01-strategic-redefinition.md`, plus the
**"Decisions carried forward" block** of `docs/claude-prompt/fable-out/01-foundation-architecture.md`
and `docs/claude-prompt/fable-out/02-matching-coverage-flywheel.md`.

**Prompt to paste:**

```
You are Fable, fresh in a Claude Code session on the NoteLib repo. PLANNING ONLY - no code, no app,
write only to the named output file.

HARD CONSTRAINTS (every session):
1. Planning only; write only to the named output file.
2. "Curation, never generation": nothing reaches a learner without an admin publishing it first -
   applies to question pools exactly as it applies to notes and plans.
3. Internal Curator vs. Learning Assistant split preserved: pool generation/expansion is an Internal
   Curator (admin) action with mandatory review; students only ever consume published pools.
4. Build on shipped architecture, do not re-derive it: ExamQuestionPoolEntity/ExamQuestionPoolService
   already implement sample-without-replacement pooling keyed by (study_pack_id, mode) for LONG_EXAM/
   BOARD_EXAM, currently PER-OWNER and disabled by default (examPoolPrewarmEnabled=false). The
   Companion is already a curate-once/serve-static precedent. Public-note copy already deep-copies the
   Study Pack's stored quiz at zero LLM cost. You are extending these, not inventing pooling from
   scratch.
5. Load and reuse the reuse-first pipeline and matching approach already designed in fable-out/01 and
   fable-out/02 - do not redesign knowledge matching or the reuse search order; cite what you reuse.

FIRST load context: read ONLY the "## Decisions carried forward" blocks of:
- docs/claude-prompt/company-redefinition-out/01-strategic-redefinition.md
- docs/claude-prompt/fable-out/01-foundation-architecture.md
- docs/claude-prompt/fable-out/02-matching-coverage-flywheel.md

THEN read (only these):
- docs/features/study-pack-generation.md
- docs/features/quiz-session.md
- docs/features/public-notes.md

PRODUCE:
1. The cross-user question pool design: how ExamQuestionPool evolves from per-owner to shared-across-
   adopters for OFFICIAL (admin-published) Study Packs specifically - keying, invalidation when the
   source note/pack changes, and how a learner's private adopted copy still gets pool coverage without
   per-learner regeneration. Explicitly scope this to Official content only - a private user's own
   Study Pack keeps today's per-owner behavior.
2. The bounded reusable-learning-object model: which fields an Official note's Study Pack should
   reliably carry (summary, key concepts, flashcards, curated question pool, explanations) using
   EXISTING entities/fields, and which of the proposal's original 8-field maximal model (Note/Summary/
   Key Concepts/Flashcards/Curated Question Pool/Explanations/Difficulty/Related Notes) is genuinely
   new work vs. already-shipped. Do not propose a new content-ops workflow beyond what the Internal
   Curator session (a separate, already-existing fable-out/03) already owns - reference it, don't
   redesign it.
3. The Reviewer-object decision, argued honestly both ways: (a) a new first-class Reviewer entity
   composing multiple notes into an exportable/studyable unit, vs. (b) "Reviewer" as purely a
   getCollectionLabels display label for a Review Set in the board-exam profile, with no new entity.
   State a recommendation and justify it against the fact that PDF export is used almost never (1
   export, ever) - do not let that recommendation quietly reintroduce PDF-export-as-a-value-driver.
4. Where AI still adds value in this reuse-first world: name the specific runtime/authoring moments
   (curator-side pool expansion, private-note quiz generation for non-Official content, PRO adaptive
   selection) - consistent with "AI as content factory + tutor, not the product" from the R0 anchor.

OUTPUT FORMAT: open with "## Decisions carried forward" (max ~40 lines: the pool cross-user model, the
bounded object-field list, and the Reviewer decision with its one-line justification); then full
detail.

Write your entire output to exactly this file (create it; touch no other file):
docs/claude-prompt/company-redefinition-out/04-reusable-assets-and-reviewer.md
```

**Output file:** `docs/claude-prompt/company-redefinition-out/04-reusable-assets-and-reviewer.md`

**Sizing justification.** Three feature docs + three summary blocks (not full docs) — one window.
Independent of R1/R2/R4.

---

## R4 — Packaging (Creator vs. Curated) + Terminology Delta

**Goal.** Address the proposal's Creator-vs-Curated-Learning packaging question as a **recommendation
to ratify** (extending, not replacing, the already-parked `fable-out/05` monetization recommendation
and the existing FREE/PLUS/PRO ladder) — and produce the **delta** on top of the already-existing
`fable-out/06` terminology rename map: specifically the "Generate" verb (not "AI", which is already
mostly scrubbed from the product) and the caution against over-hiding the note→practice
differentiator.

**Reads (scoped):**
- `docs/features/pricing.md`
- `docs/features/subscriptions-and-usage-limits.md`
- `docs/features/branding.md`
- `docs/product/ROADMAP.md` lines 468–476 ("Monetization philosophy" section, in full)

**Load from prior output:** the **"Decisions carried forward" block** of
`docs/claude-prompt/company-redefinition-out/01-strategic-redefinition.md`, plus the
**"Decisions carried forward" block** of `docs/claude-prompt/fable-out/05-monetization-recommendation.md`
and `docs/claude-prompt/fable-out/06-terminology-rename-map.md`.

**Prompt to paste:**

```
You are Fable, fresh in a Claude Code session on the NoteLib repo. PLANNING ONLY - no code, no app,
write only to the named output file.

HARD CONSTRAINTS (every session):
1. Planning only; write only to the named output file.
2. "Curation, never generation" and the Internal Curator vs. Learning Assistant split stay intact
   regardless of packaging or naming.
3. THIS IS A RECOMMENDATION FOR THE OWNER TO RATIFY, NOT A DECISION. Do NOT invent price points, quota
   numbers, pass durations, or checkout mechanics - those are locked/owner-decided in this codebase.
   Extend the EXISTING "Monetization philosophy" (FREE-static/PLUS-interaction/PRO-personalization)
   and the parked fable-out/05 recommendation; do not redesign pricing from scratch.
4. Build on shipped reality: plan copy already contains ZERO instances of the word "AI" and NoteLib's
   marketing already positions itself AGAINST "generic AI tools"; a one-time-purchase "pass" model
   (never auto-charged) already exists in plans.ts. The terminology work is not starting from zero -
   fable-out/06 already produced a full rename map. Your job is the DELTA this new proposal adds, not
   a redo.

FIRST load context: read ONLY the "## Decisions carried forward" blocks of:
- docs/claude-prompt/company-redefinition-out/01-strategic-redefinition.md
- docs/claude-prompt/fable-out/05-monetization-recommendation.md
- docs/claude-prompt/fable-out/06-terminology-rename-map.md

THEN read (only these):
- docs/features/pricing.md
- docs/features/subscriptions-and-usage-limits.md
- docs/features/branding.md
- docs/product/ROADMAP.md lines 468-476 (the existing "Monetization philosophy" section, in full)

PRODUCE, framed explicitly as recommendations:
1. Evaluate the proposed Creator (bring-your-own-notes, generate, private workspace, AI, higher tier)
   vs. Curated Learning (consume Official Review Sets, near-zero marginal AI cost, one-time-pass-
   eligible) split. State plainly whether this should be two separate products/plans or a single
   product with a packaging-only distinction (e.g., the existing FREE/PLUS/PRO ladder plus the
   existing one-time pass, re-messaged around Official content) - and justify against the established
   fact that free-tier quota is essentially never hit, so a quota-usage repackaging alone won't move
   retention.
2. If a distinct "Curated Learning" pass makes sense, recommend where it sits relative to the existing
   pass model and FREE/PLUS/PRO tiers - reuse-is-free / generation-is-metered as the guiding principle
   (per fable-out/05), not a new principle.
3. The terminology DELTA only: since "AI" is already scrubbed from plan copy and marketing already
   argues against "Generic AI tools," identify the actual remaining lever - the ubiquitous "Generate"
   verb (Generate Study Pack / Generate Quiz / Generate Note) - and recommend which instances should
   become outcome-verbs (Create/Build/Expand/Practice) vs. which must KEEP a generation-flavored verb
   because it names NoteLib's actual differentiator (turning a learner's own notes into structured
   practice - a static bank cannot do this). Do not blindly apply "remove all AI/Generate language" -
   argue the exception explicitly.
4. An explicit "Owner must decide" list at the end: every lever you deliberately did NOT set (whether
   Creator/Curated becomes two products, exact price/quota numbers, which Generate-verb instances to
   rename first).

OUTPUT FORMAT: open with "## Decisions carried forward" (max ~40 lines: the packaging recommendation,
its principle, and the top 3 terminology delta items, ALL marked RECOMMENDATION); then full detail +
the "Owner must decide" list.

Write your entire output to exactly this file (create it; touch no other file):
docs/claude-prompt/company-redefinition-out/05-packaging-and-terminology.md
```

**Output file:** `docs/claude-prompt/company-redefinition-out/05-packaging-and-terminology.md`

**Sizing justification.** Three small docs + ~10 ROADMAP lines + three summary blocks — the lightest
session, since most of the "work" is a bounded recommendation plus a delta on already-finished
terminology work, not a redesign. Independent of R1/R2/R3.

---

## R5 — Unified Phased Roadmap & Company Strategy (capstone)

**Goal.** Synthesize R0–R4 **and the already-parked `fable-out/07` roadmap** into ONE phased roadmap
(MVP → v2 → long-term), sequenced **cheap/reversible activation work first**, expensive/irreversible
re-architecture later, with the interview-free behavioral gates from R1 attached to the phases that
need them. Must name exactly which Backlog Index rows this supersedes/updates so the ROADMAP commit
that follows doesn't fragment tracking.

**Reads (scoped — summary blocks only):**
- The **"## Decisions carried forward" block** (only that block) of each of:
  - `docs/claude-prompt/company-redefinition-out/01-strategic-redefinition.md`
  - `docs/claude-prompt/company-redefinition-out/02-activation-onboarding.md`
  - `docs/claude-prompt/company-redefinition-out/03-information-architecture.md`
  - `docs/claude-prompt/company-redefinition-out/04-reusable-assets-and-reviewer.md`
  - `docs/claude-prompt/company-redefinition-out/05-packaging-and-terminology.md`
  - `docs/claude-prompt/fable-out/07-technical-approach-roadmap.md`
- `docs/product/ROADMAP.md` lines 1–15 (current baseline, for release cadence) and lines 71–119 (the
  Backlog Index table, to know exactly which rows exist today)

**Load from prior output:** the summary blocks above (explicitly *not* their full prose).

**Prompt to paste:**

```
You are Fable, fresh in a Claude Code session on the NoteLib repo. PLANNING ONLY - no code, no app,
write only to the named output file. This is the synthesis capstone.

HARD CONSTRAINTS (every session):
1. Planning only; write only to the named output file.
2. "Curation, never generation" locked; Internal Curator vs. Learning Assistant split preserved
   through every phase.
3. Build on shipped architecture and this repo's release workflow (/kickoff -> scoped PR -> /signoff).
   Your roadmap must be expressible as that workflow - you are NOT authorizing implementation, only
   sequencing it for a later Claude Code session to scope and kick off.
4. Sequence CHEAP/REVERSIBLE work before EXPENSIVE/IRREVERSIBLE work. The owner has explicitly ruled
   out running user interviews before building, so the roadmap itself must carry the safety margin:
   the activation/onboarding change (R1) - which is cheap, reversible, and instruments its own
   behavioral read - comes before any IA reshuffle, packaging split, or cross-user pool
   infrastructure investment. Each later phase should name what earlier-phase behavioral signal (if
   any) it depends on, without requiring it to block indefinitely.

CRITICAL - to stay within budget, load ONLY the "## Decisions carried forward" block at the top of
each of these files (do NOT read their full bodies):
- docs/claude-prompt/company-redefinition-out/01-strategic-redefinition.md
- docs/claude-prompt/company-redefinition-out/02-activation-onboarding.md
- docs/claude-prompt/company-redefinition-out/03-information-architecture.md
- docs/claude-prompt/company-redefinition-out/04-reusable-assets-and-reviewer.md
- docs/claude-prompt/company-redefinition-out/05-packaging-and-terminology.md
- docs/claude-prompt/fable-out/07-technical-approach-roadmap.md
Also read docs/product/ROADMAP.md lines 1-15 (baseline) and lines 71-119 (the current Backlog Index
table).

If any input file above is missing (that session wasn't run yet), note it as a gap and proceed with
what exists - do not block.

PRODUCE:
1. A unified phased roadmap: MVP -> v2 -> long-term, each phase mapped to concrete NoteLib
   release-sized chunks (the kind that become releases/vX.Y.Z), in this priority order unless you find
   a specific reason to deviate (state the reason if you do):
   Phase 1 (cheapest, most reversible): the R1 activation/practice-first onboarding branch.
   Phase 2: the R2 IA/Explore convergence (gated on Phase 1's behavioral read being positive-or-
   ambiguous, per the existing Primary-Review-Set-proving-out precedent already in ROADMAP).
   Phase 3: the R3 cross-user question pool + bounded reusable-object model (the highest one-time
   engineering cost - sequence deliberately late).
   Phase 4: the R4 packaging/terminology delta (a business decision, not an engineering dependency -
   can move earlier if the owner ratifies it sooner; note this explicitly).
2. For each phase: what existing fable-out/01-07 work it reuses vs. what is genuinely new, the
   behavioral gate (if any) that should clear before kickoff, and the release-sized chunk it maps to.
3. An explicit list of which CURRENT ROADMAP.md Backlog Index rows this whole effort supersedes or
   folds into - name them exactly as they appear in the table you read (Smart Review Planning, AI-
   generated Review Sets / Runtime Companion, Review-Set-Centric Navigation, and any product-language
   row) - so a later ROADMAP edit updates these rows instead of creating duplicate/orphaned tracking.
4. State plainly: nothing in this roadmap is authorized for implementation. Every phase requires the
   owner to explicitly ratify it (and, for Phase 3/4 specifically, requires the R3/R4 sessions'
   "Owner must decide" items to actually be decided) before a /kickoff happens.

OUTPUT FORMAT: open with "## Decisions carried forward" (max ~40 lines: the phase list in order + each
phase's gate + the superseded-rows list); then full detail.

Write your entire output to exactly this file (create it; touch no other file):
docs/claude-prompt/company-redefinition-out/06-unified-roadmap.md
```

**Output file:** `docs/claude-prompt/company-redefinition-out/06-unified-roadmap.md`

**Sizing justification.** Loads only six ~40-line summary blocks + ~55 ROADMAP lines — deliberately
the lightest input footprint of any session, matching the `fable-out/07` capstone precedent, precisely
because it is the one that would otherwise blow its window by re-reading five full prior docs plus the
existing 7-document Smart Review Planning output.

---

## Summary

- **6 sessions.** R0 anchor first; R1–R4 fan out from R0's block (parallelizable, independent of each
  other); R5 capstone last, loading only summary blocks.
- **Every prompt** restates the six hard constraints verbatim (Fable starts cold each session), names
  exactly which files/line-ranges to read, and names exactly one output file under
  `docs/claude-prompt/company-redefinition-out/`.
- **Explicitly builds on, never re-derives**, the existing 7-document Smart Review Planning design in
  `docs/claude-prompt/fable-out/01–07` — R0, R3, R4, and R5 each load specific prior summary blocks for
  this reason.
- **Budget mechanism:** every output opens with a compact "Decisions carried forward" block;
  downstream sessions load those blocks, never full prose.
- **Flagged out of Fable's authority:** the pricing/business-model *commitment* in R4 — bounded to a
  recommendation with an explicit "Owner must decide" list; no invented prices/quotas. Same treatment
  for the phase-sequencing "authorization" in R5 — it is a recommended sequence, not an approval to
  `/kickoff` anything.
- **The interview-free validation substitute** (per the owner's explicit decision to skip user
  interviews) is designed once, in R1, and referenced — not re-derived — by R5's phase gates.
- Each output doc is independently valuable, so a mid-chain stop still leaves usable, standalone work.

## After all sessions run

1. **Backlog Index compliance (same commit as the `company-redefinition-out/` docs land):** add one
   new Backlog Index row in `docs/product/ROADMAP.md` for this effort, and update — not duplicate —
   the rows R5's output names as superseded (expected: Smart Review Planning, AI-generated Review
   Sets / Runtime Companion, Review-Set-Centric Navigation, and the product-language item). This
   satisfies the repo's standing invariant that no `docs/claude-prompt/*-out/` directory may exist
   without a Backlog Index row.
2. **No `/kickoff`, no code, no version bump** happens from this session plan alone — R5's roadmap is
   a recommendation the owner must ratify phase by phase before any release branch is cut.
