# Reprioritization: Reusable-Assets Realization × Signup Surge

> Planning document. No code changed. Authored by Claude Code (not a Fable session) on 2026-07-24,
> in response to a live product/UX reprioritization request, immediately after the owner paused
> pushing the v0.57.0 signoff branch upon noticing a real-time signup surge. Extends
> `company-redefinition-out/01–06`; reverses one of `06`'s sequencing decisions with reasons — see
> "The decision" below. Full resequenced detail now lives in `docs/product/ROADMAP.md`'s "Company
> Redefinition Roadmap — Phase Detail" section; this document is the reasoning record behind it.

## Decisions carried forward

**What prompted this.** Two things landed the same evening: (1) a real-time signup surge (~15
signups in one evening, previously a slow trickle; hundreds of verified users now; Facebook
marketing producing consistent signups; LET the strongest acquisition channel), and (2) a
product/UX realization, delivered as a formal "Product Strategy Review" memo: *"every Challenge
Quiz generates fresh AI questions; most learners only practice a topic a few times; maybe quizzes
should be reusable learning assets that become more valuable over time, not disposable AI
output."* The owner asked, acting explicitly as CTO/strategist and inviting disagreement, whether
this changes roadmap priorities, and asked for an independent second opinion from Fable/Opus
rather than a straight answer.

**Verified ground-truth facts (confirmed directly against the backend code, not assumed):**
- **Quick Review** replays the stored Study Pack quiz — zero LLM call per session. Already a fully
  reusable asset; the realization's own ideal already exists in the most casually-used mode.
- **Board Exam Mode / Long Exam** have **per-user** question pooling already built in
  `ExamQuestionPoolService` (`resolvePoolKey`-style single-source sampling), but it ships
  **dormant**: `examPoolPrewarmEnabled=false` by default (`StudySnapProperties.java`,
  `application.yaml`), so no pool row is ever created and both modes regenerate fresh via LLM every
  session in the default deployed configuration today.
- **Challenge Quiz** regenerates fresh on session start, and — a fact nobody had previously
  flagged — **every "give me more" click within a session also independently calls the LLM fresh**,
  and this incremental path is **completely unmetered**: no usage-quota decrement at all, unlike
  every other quiz-generation action in the product (`ChallengeQuizService.generateMoreQuestions()`
  → `QuizGenerationService.generateMoreChallengeQuiz()`, no `increment*` call anywhere on that
  path).
- **Adaptive Practice** regenerates fresh every session, personalized to one learner's own recent
  misses — disposable **by design**, structurally the opposite of shareable static content, and
  correctly out of scope for any reuse/pooling design.
- **No token/dollar cost metering exists anywhere in the system** — usage limits are tracked only
  as integer per-action session counters, and the Challenge Quiz "give me more" path escapes even
  that.
- Every note copy mints a **brand-new** `StudyPackEntity` UUID (`NoteService.copySourceStudyPack()`)
  → a distinct pool row per copy. **No cross-user pooling is live anywhere today**, even where the
  dormant per-user pooling code exists.

**Two independent consults, deliberately sought before answering:**
- **`advisor()`** (stronger reviewer, full session context) caught that the realization was about
  to be rebutted only in its weak (cost) form, and that it actually contains *two* distinct
  concepts being collapsed into one — see "The core distinction" below.
- **An independent Fable session**, briefed with the raw verified facts and the product/UX memo's
  question — deliberately *not* with this document's conclusion, so its opinion would be genuinely
  independent rather than an echo. It converged with the advisor's read and added two points not
  otherwise surfaced: (a) the retention *metric itself* may be a lifecycle mismatch for an episodic
  product (cram → sit the exam → legitimately done), and (b) the unmetered "give me more" path
  should be read as an *engagement signal to harvest* (persist it as a reusable asset), not simply
  a leak to cap.

**The core distinction (why this is not simply "build Phase 3 sooner"):** the realization contains
three things collapsed into one label:
1. Quick Review's existing stored-quiz model — already done.
2. **Per-user, cross-session reuse** — one learner's own repeat practice sampling from their own
   previously-generated questions instead of regenerating. Already mostly built (the dormant
   per-user pool), on the roadmap nowhere, needs no cross-user machinery.
3. **Cross-user pooling of Official/curated content** — many different learners sharing one pool.
   This is Phase 3 (`company-redefinition-out/04-reusable-assets-and-reviewer.md`) — already
   designed, correctly gated on adoption volume that doesn't exist yet, blocked on an unresolved
   review-queue dependency.

The roadmap had only planned for (3), the furthest-away and least urgent slice. The realization's
actual live opportunity is (2) — cheap, mostly already built, and unrelated to Phase 3's gate.
**Do not let this realization become an argument to accelerate Phase 3** — its adoption-volume gate
is unaffected by (2) shipping.

**Why "AI cost" is the weakest argument for this, and what the strong argument actually is:** at
current scale, aggregate LLM cost is small — usage is small, and monthly quota is essentially never
hit (see the pre-existing retention-diagnosis pulls this roadmap already cites). There is also no
token/dollar metering to optimize against, so the cost framing targets a number the system doesn't
even measure. The strong argument is **pedagogical and retention-shaped**: a learner who never gets
a second crack at the specific question they missed has no spaced-repetition mechanic to retain
against — the single most evidence-backed exam-prep mechanic there is — and the product has already
locked "curation, never generation" as an identity (`company-redefinition-out/01`) while 4 of 5 quiz
modes currently regenerate-every-session, a live contradiction of that identity this realization
happens to expose.

---

# Full detail

## The decision: reprioritized sequence

Full phase-by-phase detail (what ships, gates, illustrative release chunks) now lives in
`docs/product/ROADMAP.md`'s "Company Redefinition Roadmap — Phase Detail" section, updated in the
same commit as this document. Summary of the resequencing:

1. **Phase 1** (practice-first onboarding) — shipped, unchanged.
2. **Diagnostic Read** (new) — read the 2026-07-24 surge cohort using Phase 1's own funnel
   instrumentation before committing to another build cycle. Three prior retention fixes (v0.44.0,
   v0.46.0, v0.48.0) each shipped on a different hypothesis without moving W1→W2 — that pattern is
   a diagnosis gap, not proof the next feature will be the one that works. Test three hypotheses,
   not just one: discovery problem, value problem, or lifecycle-metric mismatch (board-exam prep
   being inherently episodic). Note the existing 0% exam-dated retention finding (0/41, retaining
   *below* their own exam date) leans against the lifecycle explanation on its own — treat it as a
   hypothesis to test, not a conclusion to adopt.
3. **Reusable Practice Assets & the Return Loop** (new initiative) — the realization, reframed as
   retention rather than cost: turn on the existing per-user pool for Board/Long Exam, extend the
   same per-user pattern to Challenge Quiz (which isn't wired to it at all today), persist generated
   questions as an owned, revisitable set, and add a "redo what you missed" surface reusing existing
   `ConceptHealth`/weak-concept machinery. Gated only on Phase 1 having shipped — it helps under all
   three Diagnostic Read hypotheses and does not need to wait on that read's result.
4. **Phase 2**, re-gated and split: `v0.59.0` (Dashboard/Progress reorg) now explicitly depends on
   Reusable Practice Assets having shipped, since there's no stable progress to promote to
   first-class nav until quizzes stop regenerating every session. `v0.58.0` (Explore convergence)
   now depends on the Diagnostic Read actually showing a discovery problem — otherwise it's a
   discovery bet the evidence didn't support.
5. **Phase 3 and Phase 4** — unchanged, still parked at their original gates, explicitly unaffected
   by any of the above.

**This reverses the 2026-07-23 decision** (made the same day, hours earlier) to proceed straight to
Phase 2/v0.58.0 without waiting for Phase 1's behavioral read. The surge is new information that
postdates that decision: it is the best available research asset for the Diagnostic Read, and
reorganizing navigation on top of it would pollute the exact funnel data the read needs to stay
clean. This reversal is presented as a recommendation for the owner to ratify, consistent with this
roadmap's standing rule that nothing proceeds to `/kickoff` without explicit ratification — it is
not being treated as self-executing just because the prior decision was also made unilaterally by
the owner.

## Answers to the product/UX memo's nine questions

1. **Stay exactly where they are:** Phase 3 (parked, own gate), Phase 4 (anytime, low urgency), the
   locked "curation, never generation" identity (this realization reinforces it, doesn't challenge
   it), Quick Review's stored-quiz model (already the target state for every mode).
2. **Move earlier:** the Diagnostic Read (to immediately after Phase 1, ahead of any new build); the
   reframed realization (Reusable Practice Assets & the Return Loop) — from "misfiled in a cost
   bucket / nowhere on the roadmap" to the very next build cycle.
3. **Move later:** Phase 2's Explore/catalog-compositing half (now evidence-contingent); Phase 2's
   Progress half (now depends on Reusable Practice Assets shipping first).
4. **Remove entirely:** no roadmap item needs deleting, but the *design principle* behind Challenge
   Quiz's always-fresh regeneration ("every quiz should feel unique") should be retired — it
   optimized a felt quality over a measured learning outcome and was never validated. Also: "meter/
   restrict give-more" should not become an item on its own — reframe as harvest-and-cap, not
   restriction.
5. **New initiative:** yes — Reusable Practice Assets & the Return Loop, detailed above. Fits
   immediately after Phase 1 and the Diagnostic Read; replaces Phase 2 as "the next thing to build";
   absorbs the per-user-reuse slice that was previously misfiled inside Phase 3's cross-user design.
   More valuable than proceeding to Phase 2 next because it is a retention primitive (the binding
   constraint), it is mostly already built, and it is a hard prerequisite for Phase 2's own
   Progress-promotion element.
6. **Immediately after practice-first ships:** neither "continue straight to Phase 2" nor "treat the
   realization as a cost-cutting project." Read the surge cohort, then build the reframed
   realization, then resume Phase 2 under its new, more specific gates.
7. **Over-investing in:** AI generation as novelty (4 of 5 modes regenerate every session); nav/IA
   re-architecture as a retention lever ahead of an actual diagnosis (the same failure pattern as
   the three prior retention attempts); planning depth relative to diagnosis depth.
8. **Under-investing in:** diagnosis/instrumentation of the actual retention break; a return loop
   (a concrete reason to come back); Official content depth for the channel that's actually
   surging (LET/Education sits at roughly 43 notes — thin content on the exact channel bringing in
   the surge risks starving the whole reuse flywheel this roadmap depends on); basic unit-economics
   visibility before scaling paid acquisition further.
9. **Six-month sequence:** the numbered sequence above — Phase 1 (done) → Diagnostic Read →
   Reusable Practice Assets & the Return Loop → Phase 2 (Progress gated on the initiative above,
   Explore gated on the read showing a discovery problem) → Phase 3/4 parked at their existing
   gates → LET/Education content depth pursued opportunistically alongside all of the above.

## What this deliberately does not change

"Curation, never generation"; the Internal Curator vs. Learning Assistant split; no entity forking
across profile types; Adaptive Practice stays personalized and unshared; Phase 3 stays parked at its
originally-stated adoption-volume gate, explicitly not accelerated by this realization; Phase 4
stays an owner-ratification-gated, no-dependency item; nothing here is authorized for
implementation — every item above still needs explicit owner ratification before its own
`/kickoff`, exactly as every phase in `06-unified-roadmap.md` already required.
