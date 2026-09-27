# RELEASES.md - NoteLib

## v0.162.0 - Say the Value

**Status: In Progress**

Theme: a quiz explanation is finally allowed to say what the numeric answer actually is, so the model's own internal-consistency check has something to check — and stale exam content stops surviving a regeneration it should have invalidated.

**⚠️ CORRECTED 2026-09-27, same day as kickoff, before any Codex prompt was written.** The kickoff folded H6 into this
release. The incident doc's own LOCKED owner decision (`docs/claude-findings/2026-09-19-quick-review-percentage-increase-correctness-incident.md`
§Q.1 item 4, 2026-09-21) sequences H6 strictly AFTER H4/H5, not simultaneous with H5, specifically so a post-ship
change in H4's retry rate stays attributable to H5 alone. **H6 is REMOVED from this release** and re-logged in the
Backlog Index as its own future release, gated on H5's post-ship baseline read. The kickoff also wrote H5's wording
as "explanations **may** state the answer's value"; the locked decision says "**must**" — "may" would not reliably
move H4's recall, which is the whole point of H5. Corrected below.

### Planned Scope

**Scope picked by the owner, 2026-09-27: three related items from the Backlog Index, surveyed and verified against
code at kickoff, not taken from their status cells at face value.** A fourth item (H6) was folded in at kickoff and
REMOVED the same day on re-reading a locked owner decision (see the correction above). Two stale rows were found
during the survey and are NOT part of this release's scope (see "Also found" below).

1. **Phase A0 (documentation, Claude-direct): ratify `docs/architecture/ADR-002-quiz-answer-identity-by-text.md`.**
   Flipped `Status` from `PROPOSED` to `ACCEPTED`. Its own open question ("does `board-exam-developer.txt` inherit
   the letter contract from `schema.json` alone?") is RESOLVED by grep, not inference, and the answer splits by
   which of two separate lines each prompt file carries: the ANSWER-FORMAT line ("exactly one of A, B, C, D") is in
   exactly six files (`adaptive-practice-developer.txt:18`, `challenge-quiz-developer.txt:20`,
   `interview-practice-developer.txt:13`, `developer.txt:90`, `long-exam-developer.txt:24`,
   `teacher-quiz-developer.txt:18`) — `board-exam-developer.txt` carries NONE of its own and does inherit from
   `schema.json` alone, confirming the ADR's suspicion. The EXPLANATION-RESTRICTION line (the one H5 touches) is a
   DIFFERENT set of six: `adaptive-practice-developer.txt:28`, `board-exam-developer.txt:19`,
   `challenge-quiz-developer.txt:48`, `developer.txt:105`, `long-exam-developer.txt:46`,
   `teacher-quiz-developer.txt:29` — `board-exam-developer.txt` DOES carry this one, so H5 must edit it explicitly;
   `interview-practice-developer.txt` carries no such line, so H5 has nothing to relax there. MULTI_SELECT's
   equivalent contract stays explicitly deferred, unchanged by this ratification.

2. **Phase A (H5, backend, Codex).** Relax the explanation-restriction line in the six files named above so a quiz
   explanation **must** state the answer's value when every choice in that question is a short numeric/unit
   literal, while still forbidding a letter reference (`A`/`B`/`C`/`D`) — per the incident doc's locked wording, not
   the softer "may" the kickoff first wrote. **Numeric-conditional, not universal — owner decision 2026-09-27**: an
   unconditional version was considered and rejected once the ratio was read (numeric-literal MCQs are 1.7% of all
   MCQ-shaped items, 88 of 5,110 generated since H4 shipped; an unconditional rule would force the other 98.3%,
   prose-answer MCQs H4 never reads, to restate their full choice text verbatim for no validator benefit). Prose
   MCQs keep the existing "don't restate" rule unchanged. **What this actually buys:**
   `QuizValidationUtils.isAnswerExplanationInternallyInconsistent` (`:194-201`) excludes any MCQ with a non-numeric
   choice unconditionally, before the explanation is even read — H4 has ZERO evaluation of prose-answer MCQs, not
   "near-zero recall" as the Backlog row's original framing claimed. H5 can only raise H4's evaluable coverage on
   NUMERIC-LITERAL-answer MCQs: today an explanation that fully complies with "don't restate" gives H4 no evidence
   to check at all; "must state the value" closes that gap for the numeric subset only. Do not claim a prose-answer
   effect in the release notes. Gated on reading H4's production rejection-rate baseline first, so a post-ship rate
   change is attributable to H5 alone — **which requires H6 to ship separately** (see the correction above).
   **Shipped:** updated `adaptive-practice-developer.txt`, `board-exam-developer.txt`, `challenge-quiz-developer.txt`, `developer.txt`, `long-exam-developer.txt`, and `teacher-quiz-developer.txt`, pinned by `quizExplanationPromptsRequireExactNumericAnswerValueWithoutLetterReferences`.
   **Measurement, corrected 2026-09-27: the H4 retry/omit COUNT is the wrong metric for H5's effect and must not be
   read as a regression signal.** H5 gives H4 more evidence to check, so the retry count is EXPECTED TO RISE after H5
   ships — a rise is success, not a problem. The Challenge-bank fix (Phase C, same release) also raises generation
   volume, which inflates the raw count independent of H5, and Render drops logs after ~30 days (the 2026-09-22
   entries below expire ~2026-10-22), so a count-based read has no denominator and no shelf life. **The real metric
   is a per-question coverage ratio computed from the stored JSONB, not the log:** among MCQs where every choice is
   ≤20 characters and contains a digit (H4's own `isNumericUnitLiteral` predicate), what share have
   `explanation || workingSolution` containing the text of `choices[correctIndex]`. Compare packs generated between
   the H4 deploy (`v0.155.0`, 2026-09-22) and the H5 deploy against packs generated after H5 ships; the ratio should
   rise post-H5. **This is the checkpoint's instrument, to be minted in full at signoff, not run now** (H5 has not
   shipped yet), but its PRE-H5 baseline was read at correction time rather than left for signoff to discover it was
   never read. **Exact query, full context and caveats saved verbatim to
   `docs/claude-plans/2026-09-27-h5-coverage-ratio-baseline.sql`** (an approximation compared only against its own
   future re-run, not a re-implementation of `QuizValidationUtils`'s normalized matcher) — signoff must run the
   IDENTICAL query with the H5 deploy timestamp as the partition point, not a rewritten one. **As of 2026-09-27,
   read against `study_packs.quiz` for packs generated since the H4 deploy (`v0.155.0`, 2026-09-22):** of 5,110
   MCQ-shaped items, only **88 (1.7%) are numeric-literal** — the entire population H5's evaluable-coverage claim
   applies to; of those 88, **60 (68.2%) already state the correct value verbatim** under the CURRENT "don't
   restate" instruction (the ban is imperfectly followed today, this is not evidence H5 shipped); and **10 (16.7%
   of the 60) already also mention a distractor's value** — a PRE-EXISTING case `QuizValidationUtils:207-209`'s
   short-circuit cannot catch (it returns "consistent" the moment the correct value is found, before ever checking
   for a distractor), tracked as a masking-risk baseline to re-read post-H5, not a defect introduced by H5. A
   post-H5 numeric-MCQ sample well under ~80 items should re-date the checkpoint rather than be read as a verdict.
   Confirmed no in-place regeneration occurred in this window (`updated_at` never exceeds `created_at` by more than
   a minute across all 1,057 packs since 2026-09-22), so `created_at` is a clean partition point for the post-H5
   comparison — re-verify this assumption at signoff rather than reusing it uncritically. **The 88-item numeric
   population is small enough that "raises H4's evaluable coverage" is real but narrow — say so plainly rather than
   implying broad impact.** Exact log filter for the retry/omit COUNT, recorded for context only, not as the
   pass/fail signal: resource `srv-d6u0jkvgi27c73dvl9k0`, text `quiz_answer_explanation_consistency`, window
   2026-09-22–2026-09-27 (H4-only baseline): 10 `outcome=retrying`, 0 `outcome=omitted` — five days of total
   headroom across the whole system, for context on how small this signal currently is. The retry path
   (`retryInternallyInconsistentQuestion`) reuses the SAME input messages as the first attempt
   (`context.inputMessages().deepCopy()`), so there is no separate retry-prompt copy of the restriction to edit.
   **The incident doc's own locked text (§Q.1 item 3, and the original recommendation at line 691) requires this to
   ship "with a before/after sample review"** — a human reading of actual generated output under the old vs. new
   prompt, distinct from the coverage-ratio metric above. **This is a gate on merging the H5 PR, run by this session
   (not Codex — Codex has no OpenAI key/network access, so it cannot generate real packs and must not fabricate
   sample output): after Codex delivers the diff, generate a few Study Packs locally against source notes behind
   the 88 numeric-literal items above (so the new numeric-case wording actually fires) and a few prose-answer notes
   (so the unconditional "otherwise" branch is confirmed unchanged), under the old prompt then the new one, and read
   the explanations before merging.** **Gate cleared, 2026-09-27** — called the real `/responses` endpoint directly
   (same messages/schema `OpenAiLlmStudyPackService` builds, `gpt-4.1-mini`) on one numeric and one prose sample,
   old prompt vs. new: numeric explanations now state the value verbatim with no letter references in either
   version; no masking observed; the "don't discuss the other choices" and formula-text-echoing gaps found are
   pre-existing and appear identically under the OLD prompt, not introduced or widened by H5. Full findings at
   `docs/claude-plans/2026-09-27-h5-before-after-sample-review.md`. Prompt-only change; no schema, no parser, no
   migration.

3. **~~Phase B (H6)~~ — REMOVED from this release, see the correction above.** Logged in the Backlog Index as its
   own future release, gated on H5's post-ship baseline read.

4. **Phase C (Challenge Quiz bank invalidation, backend, Codex).** The sibling leg of the exam-pool invalidation
   defect `v0.143.0` already fixed for `StudyPackService`'s and the admin repair path's regeneration flows (both
   confirmed at kickoff to already call `examQuestionPoolService.refreshPool`). The Challenge question bank leg is
   confirmed STILL open: `ChallengeQuizQuestionBankService`/`ChallengeQuizService` (grep-verified) are never called
   from either regeneration path, so a regenerated note's Challenge Quiz keeps serving questions drawn from the
   deleted content. Fix: invalidate or refresh the bank on the same regeneration boundary, mirroring the exam-pool
   fix's shape. No migration expected; confirm against `ChallengeQuizQuestionBankService`'s actual write path before
   the Codex prompt is written.
   **Shipped:** `ChallengeQuizQuestionBankRepository.bulkDeleteAllForStudyPack` and
   `ChallengeQuizQuestionBankService.invalidateForStudyPack` now delete all bank rows for a regenerated pack in one
   JPQL statement. Exactly two of the three exam-pool invalidation sites call it after their existing
   `studyPackRepository.flush()` and Long/Board refreshes: `StudyPackService` regeneration replaces `summary` and
   `keyConcepts`, and `AdminStudyPackTransactionHelper.regenerateOnePack` replaces `summary`; quiz-only
   `repairMalformedQuiz` remains untouched because `quiz` is not a Challenge-generation input. The admin helper now
   reports whether content was actually replaced, and `AdminStudyPackService` then reloads the committed note and
   pack and calls `OfficialChallengeQuizTemplateService.queueSeedIfEligible`; the learner-facing path already had
   the equivalent post-commit seed. Production files: `ChallengeQuizQuestionBankRepository.java`,
   `ChallengeQuizQuestionBankService.java`, `StudyPackService.java`, `AdminStudyPackTransactionHelper.java`, and
   `AdminStudyPackService.java`. Tests: `NativeQueryPostgresIntegrationTest.java` proves the delete's pack predicate
   and claimed-row behavior against Flyway PostgreSQL; `StudyPackServiceTest.java` and
   `AdminStudyPackTransactionHelperTest.java` pin flush → exam refreshes → bank invalidation ordering;
   `AdminStudyPackServiceTest.java` pins post-commit reload/re-seed and the false-result skip; and
   `ChallengeQuizQuestionBankServiceTest.java` pins the unannotated transaction-joining service method.
   **Verified via a scoped Opus falsification pass (worktree pinned to `d4cd98f3`), CORRECTED 2026-09-27: the
   executor-rejection framing below was wrong, and two additional findings surfaced, both documented rather than
   fixed.**
   - **Executor math corrected.** 890 admin-owned packs matching summary regeneration had bank rows in production
     on 2026-09-27 (not all Official-template eligible). The 4-core/8-max/50-queue `llmParallelTaskExecutor`
     admits roughly the FIRST 58 of a bulk run's regeneration tasks and rejects the rest at submission —
     **those rejected packs are never regenerated, so never invalidated, and need no re-seed at all.** Among the
     ~58 admitted, only the one whose re-seed happens to land while the queue is still full is rejected — expect
     about ONE rejected seed per saturated run, not most of them (the original wording overclaimed this). A
     rejected seed still degrades safely (`copyTemplateQuestions` copies nothing, Challenge Quiz generates fresh
     shortfall questions). Recovery: rerun `POST /admin/study-packs/seed-official-challenge-quiz-templates` only
     AFTER the bulk run has fully finished, not while seeds may still be in flight — its existence gate can't see
     an uncommitted seed, so an overlapping rerun wastes LLM calls and can occasionally double a template.
   - **New finding, documented not fixed (`docs/features/quiz.md`): `generateMoreQuestions` ("+5 questions") can
     race this invalidation.** It reads `summary` unlocked, then calls the LLM while holding a `PESSIMISTIC_WRITE`
     lock on its own claimed bank rows; a concurrent regeneration's delete can run (or wait) around that call, and
     the `+5` request's LLM-derived rows — built from the pre-regeneration summary — are inserted afterward and
     survive the delete. A real fix needs a generation stamp on bank rows and a migration; tracked as its own
     Backlog row (`docs/product/ROADMAP.md`) rather than folded in here. The same call also introduces a genuinely
     new wait: a regeneration's bank delete can now block up to the LLM read timeout (180s) behind an in-flight
     `+5` call on the same pack, while holding the Study Pack and Note row locks and one of only two
     `studyPackGenerationTaskExecutor` threads. No cross-transaction deadlock was found reachable on the main
     paths (`study_packs` is always locked before the bank, on both sides) — only this bounded-but-long wait.
   - **Pre-existing bug this release widens the blast radius of, NOT fixed here, Backlog row added
     (`docs/product/ROADMAP.md`): `ChallengeQuizQuestionBankService.releaseClaims`'s `REQUIRES_NEW` transaction can
     wait indefinitely on locks its own caller's outer transaction already holds** (v0.60.2; fires on any
     `RuntimeException` in `generateMoreQuestions`, including the ordinary `NotEnoughNewQuestionsException`, not
     only real errors). **Verified against production, 2026-09-27:** `lock_timeout`, `statement_timeout`, and
     `idle_in_transaction_session_timeout` are all `0` (unbounded) — if this ever fires, nothing currently stops
     it. **Also checked 30 days of Render logs for direct evidence: 3 "Apparent connection leak detected" events
     exist, and all 3 trace through `NoteController.listMine` — an unrelated path — not through
     `ChallengeQuizService` at all.** No evidence this specific hang has ever fired; the risk is real but appears
     dormant, not active. **What THIS release widens:** before this commit, a hang here only pinned one learner's
     request and two DB connections; after this commit, a regeneration's new bank-delete call can queue up behind
     the same held lock, so a hang also now blocks that Study Pack's regeneration indefinitely, holding a
     `study_packs`/`notes` row lock and one of only two `studyPackGenerationTaskExecutor` threads. Not fixed in
     this prompt — the naive fix (lock `study_packs` inside `generateMoreQuestions`) would invert `startSession`'s
     own pack-then-session lock order and create a new same-user deadlock; a real fix needs more care than this
     release's scope affords.

5. **Phase D (Question Quality, Claude-direct, documentation/audit ONLY — no code).** Distinct from H4 (which
   verifies a stored answer agrees with its own explanation) and from H5/H6 (representation, not correctness): this
   is whether a generated question has a single defensible best answer at all. Its own Backlog row says there is no
   measured defect rate yet for genuine ambiguity. This phase reads production for one, using the three-tier
   discipline (STRUCTURAL / INTERNAL-CONSISTENCY / SEMANTIC) the original incident doc established, and produces an
   owner decision document: is this worth building, and if so, at which tier. **It ships no code.** Do not let this
   phase drift into an implementation mid-release — if the read makes a strong case, that becomes its own future
   release, not a scope change to this one.
   **Shipped:** `docs/claude-plans/2026-09-27-question-quality-tier3-audit.md` (decision document) and its
   companion `2026-09-27-question-quality-tier3-sample.sql` (the exact sampling queries, with two real bugs found
   and stated rather than smoothed over — the stored keyed answer is `correctIndex`, not `answer`; a discarded
   draft draw is named, not silently dropped). 65 real production questions read across 2 of 4 quiz stores
   (`study_packs.quiz`; `exam_question_pool`'s Board/Long Exam tier, the store the one confirmed historical defect
   came from) — 0 confirmed genuine-ambiguity defects, 2 near-miss patterns noted. **Corrected mid-audit, stated
   plainly rather than smoothed over:** a first draft read the zero-defect sample as "no evidence of an actionable
   rate," which overclaimed — the honest rule-of-three bound (0/65 rules out roughly a 1-in-22 rate, still >5,000
   questions across the corpus if the true rate sits there) rules out a COMMON defect only, not a rare one, which
   is the shape the one historical defect actually had. **Recommendation: do not build an automated Tier 3 gate
   now; scope a learner-facing "flag this question" affordance first** (none exists in the product today, checked
   directly) as the one instrument that scales to a rare-event rate a fixed-size sample cannot resolve —
   explicitly weighed against the incident doc's own rejection of a learner-wide "answers may be wrong"
   announcement on trust grounds, so the owner sees that tension named rather than assumed away. Existing Backlog
   row updated with the outcome rather than duplicated.

**Also found during the Backlog Index survey, NOT part of this release (flagged for a separate doc-correction pass):**
The Backlog row titled "Admin summary/quiz repair paths replace Study Pack content in place with no exam-pool invalidation" is
stale — `AdminStudyPackTransactionHelper.regenerateOnePack` already calls `refreshPool` for both exam modes (`:77-78`). The row
titled "`companionMayBeOutdated` returns false for non-ADMIN callers" is also stale — the guard already lets an adopted copy (`sourcePlanId != null`) through to the real
staleness check (`NoteCollectionService.java:1663-1675`). Both would have been false positives if scoped as work;
neither is touched by this release.

Anti-drift: H4's internal-consistency validator, its retry-then-omit chain, and its MCQ-numeric-choices-only scope
are UNCHANGED — this release only decides what a *new* explanation is allowed to say, not how the answer is
represented (that is H6, removed above) or how H4 grades it.
No structural answer-key validation is added (the original incident's full corpus scan found zero violations of any
kind; still not the fix, still not built). No migration touches `study_packs.quiz`, `exam_question_pool.questions`,
`challenge_quiz_question_bank.question`, or `generated_quizzes.questions`. MULTI_SELECT gets no text-based contract
this release. Phase D produces a decision document only, never code, in this release. The Challenge-bank fix (Phase
C) touches only the regeneration-invalidation boundary, not Challenge Quiz's broader question-selection logic.

**Verification tier (per `CLAUDE.md`'s release-size rule):** three items, within the 3-4-item sweet spot. Phase A0 is
docs-only. `advisor()` before each phase's Codex prompt and on each diff is the baseline. **CORRECTED 2026-09-27,
scoping the Phase C prompt: the trigger fires for Phase C.** It bulk-deletes a learner's own stored
`challenge_quiz_question_bank` rows — including recorded `lastKnownOutcome` history — as a side effect of a
regeneration action, and for an Official-author pack those same deleted rows are the Challenge Quiz templates other
learners' sessions read from (`OfficialChallengeQuizTemplateService.copyTemplateQuestions`). **Read against
production, 2026-09-27: every bank row's `user_id` matches its pack's `owner_user_id` (0 counter-examples across all
31,776 rows) — this is always the pack owner's own data, never a different learner's, so "who does the delete
affect" was verified rather than assumed.** That still changes production-data semantics (deleted outcome history,
and for 890 admin-owned packs with existing bank rows read at the same time — not necessarily all Official
templates, only those additionally passing `isEligibleOfficialTemplate` actually re-seed — a genuine re-seed
dependency on a bounded 8-worker/50-slot executor queue that admits roughly the first ~58 of a run this size and
rejects the rest AT SUBMIT, deterministically, not merely "under load") — the class of change
`v0.143.0`'s own precedent for this shared invalidation shape needed a falsification pass to catch a real deadlock
risk in. **One scoped cold agent (Opus), falsification-framed, runs on the Phase C diff after Codex delivers it,
before merge — not before, since there is nothing to falsify until the diff exists.** Two named targets, not an
open-ended review: (1) row-lock ORDERING AND WAITING between the new bulk `DELETE` and
`ChallengeQuizQuestionBankRepository.findClaimableForUpdate`/`findIncorrectClaimableForUpdate` (both already take
`PESSIMISTIC_WRITE` locks) — not just whether a deadlock is possible (the `v0.143.0` class of bug), but also
whether `ChallengeQuizService.startSession` can hold a bank row lock across its own LLM call while a regeneration's
transaction waits on that same lock while ALSO holding a `study_packs` row lock `LongExamService.startSession`
takes first — a long wait, not a deadlock, but a real contention path; (2) whether the Official-template re-seed
(`AdminStudyPackService` re-fetching note+pack and calling `queueSeedIfEligible` after `regenerateOnePack` returns
`true`) actually fires in practice given the shared `llmParallelTaskExecutor` (core 4, max 8, queue 50) both the
890-pack bulk regeneration AND its own re-seed dispatch compete for — read the diff against
`OfficialChallengeQuizTemplateService.queueSeedIfEligible`'s real behavior and that executor's real capacity, not
the prompt's stated intent. H5 does not touch a shared method, a permission boundary, or production-data
semantics, so it stays on the `advisor()`-only baseline — only Phase C's tier changed.

### Shipped

_(nothing yet)_

## v0.161.0 - Scannable Study Plans

**Status: Released** (signed off 2026-09-27; PRs #1452 frontend, #1453 pressure-test fix merged into the release branch; release PR to `main` pending the owner's admin merge)

Theme: make a Study Plan page scannable. A learner opening a plan sees its Sections collapsed until they choose one,
can open or close them all at once, and is told `Not started` instead of `0% · 0 due` when they have no evidence yet.

### Planned Scope

**Scope picked by the owner, 2026-09-26 (at the `v0.160.0` signoff); kicked off 2026-09-27: Degree Study Journeys Phase B (plan §13, §18 Phase B, §9.6), scoped frontend-only at kickoff; a
backend fix landed during pre-signoff pressure testing (see below).** Source: `docs/claude-plans/degree-study-journeys-stage1-architecture-audit.md`. Independent of Phase A, which shipped
in `v0.160.0`. **Every claim below was re-read in code at kickoff, not taken from the plan (the plan's line numbers had
drifted):**

1. **Sections collapsed by default at every breakpoint.** Today the default is seeded once at mount from viewport width
   (`LARGE_VIEWPORT_MIN_WIDTH = 1024`, `collection-detail-page-client.tsx:136`; seeded at `:3131`; applied at `:3484` and the
   two `?? defaultSectionExpanded` render sites `:4183`, `:4256`), so a desktop learner sees every Section of a 312-note plan
   expanded. New rule: collapsed everywhere, with ONE deterministic exception: a plan with exactly one Section expands it.
   Screen width may decide layout density, never how much curriculum a learner must process.
2. **An `Expand all` / `Collapse all` toggle on the Study Plan page.** It does not exist there (grep-verified in
   `collection-detail-page-client.tsx`); the Year builder already has a two-button pair at `study-plan-builder-page-client.tsx:2925`/`:2933`
   that this must not be confused with. One low-prominence text button in the Section
   list header that reads `Expand all` normally and `Collapse all` once every Section is open.
3. **`Not started` at the Section summary row.** The header badge is gated only on `sectionReadiness.total > 0`
   (`SectionCardHeader`, `:310`), so a Section whose notes exist but are all unpracticed renders `0% · 0 due`. `SectionReadiness`
   (`:123`) has no not-practiced count, so it grows one (`aggregateSectionReadiness`, `:230`), and the badge shows `Not started`
   when nothing has been practiced.
4. **`Not started` at the Year/plan summary.** The compact header in the shared `ReadinessSummary`
   (`components/readiness/readiness-summary.tsx:171-177`) renders `0% ready · 0/N mastered · 0 due` for untouched content, while
   the same file already has `isReadinessNotStarted` (`:19`) for per-subject entries. **Verify every consumer of
   `ReadinessSummary` before changing its compact header, and scope the change to the collection pages if others exist.**

**Already built, so NOT scope (verified):** full-row clickable Section headers, the collapsed summary row with note count and
3-title peek, multiple Sections open at once, `aria-expanded` on the header. The Subject-card `Not started` shipped with the
compact card in `v0.160.0`.

**Owner decisions, 2026-09-27:** (a) **Collapsed by default in the READ view; expanded by default on the BUILD surfaces.** ⚠️ CORRECTED post-kickoff: the Year builder does not "start every Subject expanded" — `refreshBuilder({ seedCollapsed: true })` collapses every Subject on first load (`dc9ca58c`, `collapsedSubjectIds` seeded from `nextSubjects`, not empty as first stated at `:1392`), and its toggle pair is at `:2943`/`:2951`, not `:2925`/`:2933`. The owner re-confirmed on 2026-09-27, after this was found, that the Year builder is left unchanged by this release regardless. The
Year builder (`/collections/[id]/builder`, the only build surface a user can reach: the Study Plan page's own organize mode is dormant, `organizeMode` being a constant `false`) keeps
their expanded default and are otherwise unchanged; the `Expand all` / `Collapse all` toggle is added to the Study Plan page in
the read view (organize mode being dormant, the toggle's organize-mode behaviour is not user-reachable). (Cross-section drag is already a no-op, `handleDragEnd` `:3343`, and a note changes Section
through its row's Section control, so nothing here may auto-expand a Section on drag.) (b) **Routing: Codex.** Written as
`docs/codex-prompts/v0.161.0-scannable-study-plans.md` (gitignored); the diff is audited with `/audit-diff` before anything is
committed. (c) **The `Not started` header change is Study Plan pages only for now**; whether it becomes product-wide is to be
judged after this ships (the Progress page's goal card and the note detail page use the same compact header and are untouched).

**Implementor decision:** item 4 is done through an opt-in prop on the compact `ReadinessSummary` header, set ONLY at the two Study
Plan page call sites (Goal `collection-detail-page-client.tsx:3889`, leaf `:4121`), so the other compact call sites (the Progress
page's goal card `app/progress/progress-report-client.tsx:507` and the note detail page
`components/notes/private-note-detail-page-client.tsx:3301`) render exactly as before.

**Explicitly NOT in this release (as scoped at kickoff; a backend fix was added afterward by pre-signoff pressure testing, see Shipped):** persisting expansion state (decided in plan §13.1; it would restore a deep expansion the
learner does not remember making); accordion single-open; the artifact-level `getCollectionLabels(profileType, collection?)`
terminology resolver (plan: only if justified, and nothing here justifies it); any migration or new endpoint; any
Term, Degree or placement-revision work (placement revisions is its own Backlog row); any change to mastery math or
`ConceptHealth`.

Anti-drift: Sections stay a computed grouping (no entity, no table); no new mastery signal at any level; the Year page term
grouping and compact cards from `v0.160.0` are untouched; Review Set rendering must not change beyond the default-collapsed
Sections and the `Not started` wording (five live Review Sets). **Verification:** frontend `tsc --noEmit`, lint and jest;
tests for the default state at BOTH viewport widths (now identical), the single-Section exception, the toggle's label flip,
and a render assertion that `0% · 0 due` never appears for zero evidence at Section or plan grain; mutation-check every new
test and name the killer; a diff that changes behaviour must touch a test that runs it; `advisor()` before the Codex prompt is written, `/audit-diff` on delivery, and `advisor()` on the
diff. The first two Opus passes (frontend behaviour; release housekeeping) and the Codex pass ran while the release was still frontend-only; the Codex pass surfaced the adoption-boundary defect, which was then fixed and re-verified by a fourth, narrower Opus pass scoped to that fix diff.

### Shipped

- Study Plan read views now start Sections collapsed at every viewport width, except that an exactly one-Section plan starts expanded. The expansion logic keeps separate read-view and organize-mode overrides (`useSectionExpansionState`); organize mode is dormant on this page (`organizeMode` is a constant `false`, no setter, and the inline Organize toggle is no longer exposed), so that split is defensive code exercised only by its hook test, and the only build surface a user can reach is the Year builder, which is unchanged and already collapses its Subject blocks on first load (with its own `Expand all` / `Collapse all` pair). Expansion state is deliberately not persisted across reloads.
- A low-prominence `Expand all` / `Collapse all` text toggle on the Study Plan read view controls every Section and is hidden when fewer than two Sections exist.
- Untouched Section readiness badges say `Not started` only when no note in the Section has a completed session (a learner who practiced but never answered a concept correctly keeps the `N% · M due` wording, matching the note rows' `Practiced` label); Sections with some evidence keep the existing `N% · M due` wording, and zero-concept Sections still show no badge.
- The compact readiness header opts into `Not started · N concepts` for an untouched Goal, and for an untouched leaf Study Plan only while `progress.notesPracticed === 0`. The Progress page and note detail page do not opt in and render exactly as before. The Goal header and the termed compact Subject cards (`isSubjectStarted`) have no per-note practice signal, so a Goal or a termed Subject where the learner practiced without ever answering correctly still reads `Not started` — a documented limitation, not exposed today (no live Year carries a term).
- **Pre-signoff pressure-test fix, backend (PR #1453; this widens the release beyond frontend-only):** a child Subject Plan that is `PUBLIC` but has never been published (null `published_at`) is now treated as not found by the anonymous public read and by a standalone `adopt()`, so its term (or title) cannot reach an anonymous reader or a standalone adopter before Publish update. `adoptGoal` now adopts every child that carries a publication stamp, whatever its visibility, instead of requiring `PUBLIC`. A published-but-`PRIVATE` child made a fresh Goal adoption throw *after* the learner's Goal had already been persisted, leaving a half-created Goal a retry could not repair; **confirmed live by two distinct mechanisms, not one:** PNLE's two `PRIVATE` children were created 2026-09-10 (after `V141`) with `published_at IS NULL` and were later stamped by an explicit Publish update on 2026-09-14 without their visibility being flipped to `PUBLIC` — the mechanism the code comments already describe. ALE's seven `PRIVATE` children predate `V141` (created 2026-08-29 and 2026-09-05) and have `published_at == created_at` exactly, the signature of `V141`'s blanket backfill (`UPDATE note_collections SET published_at = created_at WHERE published_at IS NULL`, with no visibility predicate) rather than an explicit Publish update — these were curator drafts that the backfill made look published. Both mechanisms produce the identical symptom in `adoptGoal` and are fixed by the same change: the publication stamp, not visibility, is the boundary. **Since when:** `adoptGoal` has required each child to be `PUBLIC` (by delegating to the public-route `adopt()`) since Goal adoption itself was introduced, well before any publication-stamp concept existed; ALE's child at sibling position 0 has been `PRIVATE` since its creation on 2026-08-29, so the earliest zero-Subject ALE adoptions (2026-08-31, 2026-09-04) predate `V141` (2026-09-08) entirely and cannot be blamed on the stamp filter — the stamp filter (added in `v0.132.0`, PR introducing the publication boundary) only added an ADDITIONAL required condition on top of the pre-existing `PUBLIC` requirement; it never explains the defect's origin. This now matches what the public preview counts and what Official update already delivers (title, description and term of a published `PRIVATE` child). Deploy order is either: the API shape is unchanged, and a never-published child now returns 404 on a direct read or adopt where it previously either leaked or threw mid-adoption.
- Four cold falsification passes ran on this release, in two rounds: while the release was still frontend-only, one Opus pass ran over the whole release AND one Opus pass ran over release housekeeping/docs, in parallel, alongside a separate Codex pass over both v0.160.0 and v0.161.0; the Codex pass is what found the adoption-boundary defect below. After the fix, a fourth, narrower Opus pass ran on the fix diff alone and confirmed it. Findings from all passes are dispositioned in Known Limitations below.

### Known Limitations

- **Existing adopters are not repaired by the adoption-boundary fix.** As of the 2026-09-27 read, 27 of 31 ALE adopters and all 17 PNLE adopters hold fewer Subject Plans than the source now has published (2 ALE adopters hold zero); this count is not split between pre- and post-defect causes and is not itself evidence the defect affected all of them equally, since ALE's own source additions have been growing independently. Re-adopting returns the existing (short) Goal unchanged. `Review update` already offers the missing Subject Plans as additions and was not changed by this release; no backfill was written or run — repairing existing adopters is a separate owner decision, tracked as its own Backlog row.
- **The `Not started` header change is scoped to the Study Plan pages, not product-wide** (owner decision, pending a judgment after this ships): the Progress page's goal card and the note detail page keep the old `0% ready · 0/N mastered · 0 due` wording for untouched content.
- **A Codex finding was refuted, not adopted:** a claim that the frontend-first deploy order was unsafe (an unknown-property rejection) was checked against the live Spring context; the JSON converter is a Jackson 3 `JsonMapper` with `FAIL_ON_UNKNOWN_PROPERTIES=false`, so either deploy order remains safe.
- **Section grouping keys on the trimmed label only**, not the canonical whitespace-collapsed form `docs/features/collections.md` describes elsewhere; two legacy labels differing only in internal whitespace render as two Sections. Pre-existing, unrelated to this release's changes; not fixed here.
- **The SMALLINT term-order ceiling (32767) has no dedicated guard** in the frontend combobox or the pipeline builder; reaching it needs an impractical number of terms per Year and is not fixed in this release.


### Checkpoint gate

No `[CHECKPOINT — due YYYY-MM-DD]` row was minted for this release. Nothing shipped ahead of its own evidence: the collapse/toggle/`Not started` scope was owner-decided against re-read code, and the adoption-boundary fix was verified against production reads (the ALE/PNLE stamped-but-private shapes, the note-visibility check, and the mechanism split above), not shipped on a bootstrap argument.

### Post-deploy verification owed

`SELECT count(*) FROM note_collections a JOIN note_collections r ON r.id = a.source_plan_id WHERE a.parent_collection_id IS NULL AND r.parent_collection_id IS NULL AND a.created_at >= '<deploy timestamp>' AND NOT EXISTS (SELECT 1 FROM note_collections k WHERE k.parent_collection_id = a.id);` — expected 0; a `STUDY_GOAL_ADOPTED` analytics event does not fire on the failing first attempt (it throws before `trackStudyGoalAdopted`), only on the harmless retry, so this reads the actual row shape rather than the event log. Also run `scripts/check-deploys.sh` (both Vercel and Render matter for this release) at least five minutes after the merge, and confirm `v0.160.0`'s still-unverified Vercel deploy while there.

## v0.160.0 - Study Plans by Semester

**Status: Released** (signed off 2026-09-26; PRs #1447 backend, #1448 frontend, #1449 pipeline, #1450 pressure-test fixes merged into the release branch; release PR merged as #1451 and tagged. Backend deploy verified 2026-09-27 by a read-only query through Render: `V150` applied 2026-09-26T16:06:30Z, success. The Vercel side and `scripts/check-deploys.sh` were NOT run)

Theme: let a curator place each Subject Plan in an academic term, so a Year reads as a semester-by-semester study
plan, without adding a level to the collection hierarchy and without touching any Note.

### Planned Scope

**Scope picked and release shape confirmed by the owner, 2026-09-26: Degree Study Journeys, Phase A0 and Phase A.**
Source: `docs/claude-plans/degree-study-journeys-stage1-architecture-audit.md` (decision-complete feature plan;
**§21 is the implementor handoff, §18 the phases and decisions A-F, §22 confirms no owner decisions remain**).
The learner-facing promise is **"BS Computer Science - 1st Year Study Plan"**, never a complete Degree Study Journey.

- **Phase A0 (documentation first, Claude-direct):** `docs/architecture/ADR-003-curriculum-placement-and-hierarchy-depth.md`
  (decisions A-F exactly as enumerated in plan §18; ADR-002 is taken by the quiz-answer-identity proposal), plus the
  `docs/features/collections.md` update. No application behaviour.
- **Phase A (implementation, Codex in slices: backend, then frontend, then pipeline):**
  1. Two nullable columns on `note_collections`, `term_label VARCHAR(60)` and `term_order SMALLINT`; one additive
     migration, no backfill, no index.
  2. Persistence, DTO, service and **adoption preservation** of the term.
  3. Curator term assignment in the Year builder: a combobox over terms already used in that Year, never raw freetext.
  4. Year-page conditional term grouping (all-NULL flat / all-placed grouped / mixed with a trailing `Term not specified`).
  5. Compact Subject cards on the Year page (title, note count, ONE progress signal), gated on the SAME condition as
     term grouping (any non-null `term_label`), no count threshold.
  6. `academic_term` in the curriculum pipeline: extend `review-set-workbook-spec.md` and `build_review_set_workbook.py`
     and regenerate; never hand-add the column to a generated workbook.
  7. The regression and invariant tests in plan §21.5.
- **Endpoint form (decided here, plan §21.9):** extend the existing collection update with two OPTIONAL fields. That is
  additive in both directions (optional on request, nullable on response), so frontend and backend may deploy in either
  order, and **the release notes must say so explicitly.** If a new endpoint is added instead, that stops being true and
  the release owes a deploy-ordering statement and a real-request `MockMvc` test with `.contentType(MediaType.APPLICATION_JSON)`.
- **Backend Academic Term slice:** migration `V150` adds nullable `term_label` / `term_order`; the existing collection
  PATCH accepts optional `termLabel` / `termOrder`; `persistAdoptedPlan`, `createSubjectAddition`, and the `adoptGoal()`
  re-parent branch carry child placement; and the Goal-child plus owned/public detail DTOs expose it. The PATCH fields
  are optional on request and nullable on response, so frontend and backend may deploy in either order.
- **Frontend Academic Term slice:** `lib/collection-terms.ts` holds the single `hasTermPlacement` gate that drives BOTH
  Year-page term grouping and compact Subject cards (no count threshold); the Year page renders ordered static term
  headers with a subject count and an in-progress count (shown only when above zero), a trailing `Term not specified`
  group in the mixed case, and compact cards (title, note count, ONE of `N% ready` / `Not started`); with every child
  term NULL the existing full-size grid is byte-for-byte unchanged. The Year builder gains a per-Subject term combobox
  over the Year's existing terms (a new label is allowed; the order is assigned, never typed). The PATCH fields it sends
  are optional on request, so this slice also deploys in either order relative to the backend.
- **Pipeline Academic Term slice:** `build_review_set_workbook.py` accepts an OPTIONAL `academic_term` column, constant
  per plan, validated per Study Plan: unused for all Subject Plans or assigned to all of them, and a partial
  assignment is refused with an error naming the Study Plan and the unassigned Subject Plans (also refused: mixed
  values inside one plan, over 60 characters, `Term not specified`, and case/spacing-variant duplicates). The term
  order is derived from first-seen file order and printed in the workbook. With no terms the output is unchanged:
  ALE, CPALE, LET and PNLE were rebuilt with the old and new builder and compared on cell values, fonts, fills, borders, merges, column widths, row heights and freeze panes: identical. The new `docs/curriculum/test_build_review_set_workbook.py` runs by hand in the venv and is NOT in CI.
  `docs/curriculum/review-set-workbook-spec.md` and the strategist module `docs/gpt-contexts/REVIEW_SET_SHAPING_CONTEXT.md`
  now carry the column, and the strategist module's TSV header was corrected to include `applicable_programs`, which the
  builder has required since 2026-09-10 (a contract drift, not a behaviour change).
- **Phase B (collapsed-by-default Sections, and so on) is NOT in this release**; it has no dependency on Phase A and rides
  in a later one. **Phase C (a Degree entity and landing page) is out.**
- **⚠️ A gap in the plan, found and verified in code at kickoff (and since corrected in the plan, §7.1a), that the
  implementation MUST close:** the plan's adoption invariant named only the adoption path, but a child Subject Plan copy
  is built field by field in TWO places. `persistAdoptedPlan` (`NoteCollectionService.java:1956-2017`) serves BOTH `adopt()`
  and `adoptGoal()`, because `adoptGoal` creates each child by calling `adopt()` and then re-parents it. The SECOND is the
  Official-update addition, `createSubjectAddition` (`:2476-2515`, `CreatedSubjectAddition`), which copies title,
  description, course program, learner level, estimated hours and the source-sync fields and, without the term, lands a
  Subject added to an already-adopted Year with a NULL term next to siblings that have terms. That manufactures the mixed
  state the plan calls a curator-quality defect and shows a `Term not specified` group with no curator involved. The term
  must be carried at BOTH, each with its own test that fails when it is dropped. **`persistAdoptedGoal` (`:2019-2057`,
  the root copy) must NOT get the term**: a root has no parent and therefore no term placement, so adding it there would be
  a silent dead column, not a fix.

Anti-drift (plan §21.2 and §21.4, binding): with every child term NULL the existing Review Set and Goal rendering is
UNCHANGED, with no term headers, no `Term not specified` group, and FULL-SIZE cards, protecting five live Review Sets
(PNLE, CPALE, ALE, LET, Civil Engineering); density is gated on the same condition as grouping and never on a count;
Official update stays additive-only forever; NO Degree progress in any phase (a permanent product rule); academic
placement never touches the Note and `applicable_programs` is never overloaded to carry a term (ADR-001); the hierarchy
stays at exactly two persisted levels; no Degree landing page and no `journey_key`/`journey_order` fallback, no Degree
entity, no whole-Degree adoption, no learner curriculum customization, no term entity/catalog/enum, no collection
`type`/`kind` enum, no change to `ConceptHealth`. Subject Plans are NOT reusable across Degree Journeys; only canonical
Notes are. **Verification:** `advisor()` BEFORE the Codex prompt is written and on each diff; a diff that changes
behaviour must touch a test that runs it; mutation-check every new test and name the killer; the all-NULL regression must
assert full-size cards; frontend `tsc --noEmit`, lint and tests plus the full backend build with Docker; and, because the
release touches the adoption engine and five live Review Sets, ONE scoped Opus cold agent framed as falsification of
invariants 1, 2 (every copy site), 3 and 4 before signoff. Seven items is a large release; say what that does to the
verification tier if more is folded in. **Owner-side, not this release's work:** the Note Strategist keeps authoring
Subject to Section to Note; Year and term placement stays in a separate editorial file until the pipeline extension ships.

### Shipped

**Status: Released 2026-09-26 on `releases/v0.160.0`; the release PR to `main` is the owner's admin merge and its auto-deploy runs `V150`.**

**Scope disposition (every Planned Scope item, checked against code):**

| Item | Disposition | Evidence |
|---|---|---|
| Phase A0: ADR-003 (decisions A-F) and `collections.md` | **Shipped** | `docs/architecture/ADR-003-curriculum-placement-and-hierarchy-depth.md`; amended 2026-09-26 from "two" to "three" child-copy sites, decisions unchanged (owner-approved) |
| 1. Two nullable columns, one additive migration | **Shipped** | `V150__collection_academic_term.sql` |
| 2. Persistence, DTO, service, adoption preservation at BOTH child-copy builders (+ a third) | **Shipped, with one addition** | `persistAdoptedPlan` (`NoteCollectionService.java:2020`), `createSubjectAddition` (`:2537`), and the `adoptGoal()` re-parent branch (`:1161`), which the plan missed; NOT `persistAdoptedGoal` (`:2058`) |
| 3. Curator term assignment (combobox) | **Shipped, changed** | `frontend/components/collections/subject-term-control.tsx`. Changed: hidden on adopted copies, disabled on published rows, partial-term warning |
| 4. Year-page term grouping | **Shipped** | `hasTermPlacement` (`frontend/lib/collection-terms.ts:36`) at `collection-detail-page-client.tsx:1764` |
| 5. Compact Subject cards on the same gate | **Shipped** | `CompactSubjectCard`, no count threshold |
| 6. `academic_term` in the pipeline | **Shipped** | `resolve_terms` (`docs/curriculum/build_review_set_workbook.py:99`); no existing workbook needed regenerating (none uses terms) |
| 7. Regression and invariant tests | **Shipped** | all-NULL full-size regression, one test per carry site, PATCH round-trip with a real MockMvc request, grouping, pipeline tests |
| Phase B / Phase C | **Not in this release, by owner decision** | Phase B is the next release's scope (Backlog row); Phase C is out |

**Changed mid-release, by owner decision (2026-09-26), not in the kickoff scope:**
- **Partial term assignment is invalid authoring input**, enforced by the pipeline builder and by refusing to publish a partially-termed Year (first publication and Publish update).
- **A term obeys the Official publication boundary by being settled before it.** It can change only while its Subject Plan is unpublished (own `published_at` AND the root's `last_update_published_at` both set means frozen; `published_at` alone is not enough because V141 stamped every pre-existing row). An adopted copy can never change a term and the builder hides the control there. Moving a published Subject that carries a term, or that joins a termed Year, is refused.
- Owner rejected documenting the original Finding 1 (terms reaching learners around the publication boundary) as a permanent limitation; option A above was chosen over a placement-revisions redesign of the Official update engine, which is logged in the Backlog Index as its own future release.

**Deploy order: either.** Both new request fields (`termLabel`, `termOrder`) are optional on the request and nullable on the response, and `termLocked` is an added response field the frontend treats as absent-means-unlocked. A frontend-first deploy sends nothing the old backend rejects (Spring Boot 4 / Jackson 3 ignores unknown properties, which was read, not run against a live backend), and a backend-first deploy serves fields the old frontend ignores. **`V150` is additive and nullable (`ADD COLUMN` twice, no default, no `NOT NULL`, no index).**

**Verification:** full backend build with Docker 2557 tests, 0 failures (the PostgreSQL 16 harness applies `V150`); frontend `tsc --noEmit` clean, lint 0 errors, jest 224 suites / 2533 tests; every new test mutation-checked with the killer named in the PR threads (#1447, #1448, #1449, #1450). The pipeline unit tests (`docs/curriculum/test_build_review_set_workbook.py`, 13 tests) run by hand in the venv and are NOT in CI. ALE, CPALE, LET and PNLE were rebuilt with the old and new builder and compared on cell values, fonts, fills, borders, merges, widths, row heights and freeze panes: identical. Civil Engineering is still refused for lacking `applicable_programs`, as before.

**Pressure test (two scoped Opus cold agents, framed as falsification, plus `advisor()` before the prompt and on each diff).** The first ran over the whole release: no blocker; two SHOULD-FIX (a Continue/hero target that could differ from the first card shown in a termed Year, fixed; the system-created half-termed learner Year, resolved by the publication-boundary decision above) and notes. The second ran on the freeze and publish guards: no blocker; two SHOULD-FIX (the re-flip named a subject the curator could not change, fixed; moving a published Subject with `updateParent` could leave it un-termed and permanently locked in a termed Year and block every later Publish update, fixed by refusing that move) and notes. Both agents confirmed the all-NULL invariant, the copy sites, additive-only Official update and the transport names.

**Known limitations (documented, owner-visible):**
- **Retroactive term introduction or rename on an already-published Year is UNSUPPORTED.** The freeze makes those cases unreachable rather than merged, and the five live Official Review Sets can never gain terms (their children are published under a stamped root; delete-and-recreate is the only route and existing adopters keep old copies). Backlog row: *Official update: placement revisions*. Do not build it speculatively.
- **The in-flight BSCS Year 1 file is a single `plan_no` with the subjects as sections, so it cannot carry per-Subject terms until it is reshaped to one `plan_no` per subject.** It is another session's untracked file and was not touched.
- The `Term not specified` reserved name is rejected in the builder and pipeline only; the backend does not reject the string, so a direct API call can store it (the page renders it without error).
- A `termOrder` beyond the 32-bit integer range, or a non-number, returns 500 from the catch-all handler (pre-existing behaviour of every `Integer` PATCH field); values from 32768 up to the int maximum return 400.
- `termLocked` covers the freeze rule only, not the adopted-copy rule: a standalone adopted plan nested under a learner's own Goal by direct API would show an enabled control the backend refuses (the optimistic update rolls back).
- A label of only NBSP is stored by the backend (Java `trim`/`isBlank`) though the frontend treats it as unplaced; the create option ("Use X") saves on blur rather than on click. Neither is reachable from normal use.
- The `Term` control also shows on a non-admin's own (non-adopted) Goals, with a warning that says a partially termed plan cannot be published; those users cannot publish. Not scoped further.
- Non-admin-owned public collections that existed at V141 may have permanently locked children (V141 stamped their root); sized by the post-deploy read below.

**Post-deploy verification (read-only `SELECT`s, run 2026-09-27 through the reconnected Render MCP):**
1. `SELECT count(*) FROM note_collections WHERE term_label IS NOT NULL;` **= 0** (as expected; the column exists, `V150` applied 2026-09-26T16:06:30Z).
2. `SELECT count(*) FROM note_collections c JOIN users u ON u.id = c.owner_user_id WHERE c.last_update_published_at IS NOT NULL AND u.role <> 'ADMIN';` **= 0**, so the non-admin locked-children limitation above affects no existing row.
3. `scripts/check-deploys.sh` was NOT run (no Render API key in the session); the backend deploy is evidenced by `V150` above, the Vercel deploy remains unverified.

**Checkpoint gate: none minted.** Everything shipped was owner-decided and none of it was gated on evidence; there is no instrumentation to read and a checkpoint without a metric is decorative. Real usage of the term feature will first be visible when a curator terms the BSCS Year, so the honest follow-up is the Phase B kickoff read, not a dated checkpoint.

## v0.159.0 - Nothing Lost in the Batch

**Status: Released** (signed off 2026-09-25; Release A merged as #1444; release PR merged as #1446 and tagged; deployed and verified: Render live 15:24Z, Vercel production 15:28Z)

Theme: stop batch operations losing their result. A bulk generation that fails topics leaves no trace once its
consume-once receipt is read or swept, and a bulk regeneration finishes with no signal at all. Also close the one
evidence question this project still owes an answer on: what the 5 s connection timeout is doing to users.

### Planned Scope

**Scope picked by the owner at kickoff (2026-09-25): Notifications Release A, and the `connection-timeout` follow-up.**
Source for item 1: `docs/claude-plans/learning-relevant-notifications-stage1-plan.md` (audit and plan, written
2026-09-24, NOT yet owner-approved to build; §12 is the release slice, §14 the decisions). **Every production
figure in that plan is a 2026-09-24 snapshot and one had already decayed; re-read before any of it reaches a
prompt.**

0. **PREREQUISITE, OWNER DECISION, NOTHING IS BUILT UNTIL IT IS MADE: the badge/retention flag split.**
   `NotificationCategory` (`entity/NotificationCategory.java`) derives badge eligibility and retention expiry from
   ONE boolean as exact complements, so a completion notification cannot be both badge-eligible and
   retention-expirable. The plan recommends option (c): split into `badgeEligible` and `retentionExpirable`, add
   `ASYNC_RESULT(true, true)`, and REWRITE (not delete) the two XOR partition tests. Java-only, no migration; it
   deliberately changes a documented invariant, which is why it is the owner's call.
1. **Notifications Release A: async completion for BULK operations only (backend, no migration, no API/DTO/frontend
   change).** Two new `NotificationType` values (`BULK_GENERATION_INCOMPLETE`, `BULK_REGENERATION_COMPLETE`), one
   new `NotificationCategory` (`ASYNC_RESULT`), one producer service, two call sites:
   `NoteBulkGenerationService.java:247-274` inside the existing `finally`, after the `recordResult` block, only
   when something failed; `NoteBulkRegenerationService.java:439-443`, deliberately NOT in a `finally`. Destination
   `/library`; dedup on `resultId` / `batchId`. ~9-11 files; routed to **Codex** (write the prompt from the plan,
   with `advisor()` BEFORE it is written and on the diff, then `/audit-diff`). Still-open plan decisions
   (§14): ship both triggers or one (recommend both), the failure-copy truncation budget (recommend ~850 chars then
   "and N more"), and whether a zero-accepted or all-quota-blocked batch delivers nothing or the failure form.
2. **`connection-timeout: 5000` follow-up.** (a) A proper read-only 500-cause read: classify the 500s by cause
   (pool timeout vs database I/O drop vs other) over the retained window, since the 2026-09-24 read sampled only
   the newest 30 log lines. (b) The owner's verdict against the row's kill criterion, which has no numeric
   "material and sustained" threshold, so the owner sets it. (c) Any resulting change (revert to 30 s, or a
   structural fix on pool holds) is its own owner-scoped item, never an inline fix.
3. **One doc correction, found by the plan's audit and verified in code:** `CLAUDE.md` names
   `NoteService.startAsyncGenerationFromNote()`, which does not exist; the real entry point is
   `StudyPackService.startAsyncGenerationFromNote` (`StudyPackService.java:171`).

Anti-drift: no single-note notification of any kind (the learner is on a page polling every 3 s); NO "Study Packs
are ready" notification for bulk generation (the count it would use over-reports, plan §1.1); no per-item
notifications, presence, websocket or SSE; no retry promise in the copy (the receipt is consume-once); never state a
reconciled "N of M" count; never add a `finally` to `NoteBulkRegenerationService.processBatch` (`:64-65` forbids
it); never call `deliver()` inside a transaction; no migration, endpoint, DTO field or `notification-inbox.tsx`
change; Release B (learning continuity) is DEFERRED, not scheduled, and `RetentionEmailType.UNFINISHED_NOTE` stays
untouched. No retention Stage 3; do not cap or reorder `INACTIVITY` here (its effectiveness and the budget-starvation
rows are separate owner decisions). The notes and curriculum files other sessions left untracked are not this
release's. **Verification:** a diff that changes behaviour must touch a test that runs it, so both call sites need
a test that executes them; mutation-check every new test; one `advisor()` on the diff (no permission, money or
production-data semantics change), escalating to one scoped falsification agent only if delivery introduces a defect
the same session then fixes.

### Scope disposition (signoff, 2026-09-25)

- **Prerequisite decision (badge/retention flag split): DECIDED and SHIPPED**, option (c).
- **Notifications Release A: SHIPPED** (#1444). Both triggers; 850-character topic budget; an all-quota-blocked batch
  delivers the quota form. Anchors: `NoteBulkGenerationService.java:296`, `NoteBulkRegenerationService.java:449`,
  `NotificationCategory.java:14-16`, `BulkOperationNotificationService.java`.
- **`connection-timeout` follow-up: PARTLY DONE.** (a) the 500-cause read is DONE, recorded below and on its Backlog
  row; (b) the owner's VERDICT is NOT made and carries forward on the row; (c) nothing was changed, by design.
- **`CLAUDE.md` entry-point correction: SHIPPED** in the kickoff commit (`StudyPackService.java:171`).
- **Not from the scope list, left open by the owner's call:** the `[CHECKPOINT — due 2026-09-27]` click/open read and the
  2026-09-28 publication-boundary read.

### Checkpoint gate

Release A shipped ahead of its own evidence (one user drove regeneration; bulk generation volume had no direct metric),
so it owes a checkpoint, added in this signoff commit: `[CHECKPOINT — due deploy + 30 days, backstop 2026-11-10]`
with a kill criterion, a denominator clause, and the `notifications` table as the instrument. The instrument is the
table, and the first production row is what proves it emits; both call sites are exercised by mutation-checked tests.

### Known limitations

- The regeneration call site has no try/catch of its own; the producer swallows every delivery failure and a test
  (mutant M10) guards that, so an escape could only come from a future change to the producer.
- The notification copy (exact titles and bodies) was drafted by the release and not separately reviewed by the owner.
- The 500-cause read is a subagent's report, not independently re-run; application logs only go back to
  2026-09-18 05:40Z, so ~350 of 439 500s (2026-09-04..09-18) cannot be attributed, and its log event count (68) exceeds
  the metric 500 count (62) by ~5 unexplained.
- The bulk generation RECEIPT still marks every accepted topic failed after an interruption (the outer catch), including
  notes already created; only the notification was corrected. Whether the row actually persists during a real shutdown
  was not verified.
- Verification: `advisor()` before the Codex prompt and on the diff, mutation checks (21 killed), and one Opus cold
  agent as a scoped falsification pass. No authorization, money or production-data semantics changed, so no full
  three-agent test was warranted.

### Shipped

- **Bulk-operation in-app results.** Notifications now keep badge eligibility and unread expiry as
  independent category policies. Failed or capacity-blocked bulk generation records its topic strings in
  one bounded notification and stays silent on success; normally completed bulk regeneration sends one
  completion notification, while an interrupted run sends none. Unit, call-path, badge/retention, and
  real-database length guards exercise these claims.
  Copy is fixed and exact (`docs/features/notifications.md`); bodies are bounded to an 850-character topic
  budget in code, and the regeneration retry mints its own batch id so it notifies too. The pre-commit audit
  found and fixed a contradiction in `notifications.md` (it still said every unread actionable row is retained
  forever, which is false for `ASYNC_RESULT`) and a test gap (nothing pinned the dedup id of either trigger).
  13 of 13 planted mutants were killed at first, each by a named test. **⚠️ That figure overstated the guard:** the
  pressure test below found five mutants the merged suite did not kill (regeneration count arguments, separator
  budget accounting, mixed-case suffix, one-per-group interleave); all are killed now (21 in total).
- **Pre-signoff pressure test (one Opus cold agent, isolated worktree, framed as falsification) and its fixes, PR #1445.**
  It held nine claims and broke three, plus test overstatement and doc defects; each was verified in code before it
  was fixed. **(1)** An interrupted or failed-before-loop bulk generation notified that EVERY accepted topic failed,
  including notes already created: the delay between items throws outside the per-item try and the outer catch
  overwrites the lists. The notification now lists only topics that were NOT created (the receipt keeps the older
  behaviour, see Known limitations). **(2)** In the mixed failed and quota-blocked case "and N more" attached to the
  quota list although the omitted topics could all be failed ones; it is now `Plus N more not listed.` after both
  sentences. **(3)** Regeneration copy claimed Study Packs were "unchanged" although a timed-out item may still succeed;
  it now says they still work. **(4)** Five mutants survived the merged suite; new tests kill them. **(5)** Doc defects:
  a self-contradicting ROADMAP row, a checkpoint SQL that omitted dismissals from its own kill criterion, and a
  rationale that ignored the polling regenerate modal. The full build passed (2,527 tests) and all 21 mutants are killed.
- **`connection-timeout: 5000` 500-cause read (read-only, no code change).** Application logs are retained only from
  2026-09-18 05:40Z. In the observable week: ONE real saturation cluster (09-18 14:46-14:48, 34 requests, pool 20/20,
  peak waiting 6); pool timeouts on 09-18 16:03 and 09-22 06:04 that followed database I/O drops with a collapsed pool;
  26 database I/O drops in bursts on the first requests after a deploy goes live; 33 client-abort broken pipes not counted
  as 500s; one 405 logged as a 500; one unknown. The 5 s timeout produced 500s in one incident; most other 500s are
  deploy-time DB drops it does not cause. The verdict remains the owner's; a deploy-time-burst finding has its own row.

## v0.158.0 - Reading the Evidence

**Status: Released** (signed off 2026-09-25; merged as #1443 and tagged; deployed and verified: Render live 06:30Z, Vercel production 06:33Z)

Theme: discharge the evidence reads this project already owes (three overdue checkpoint reads and the first
readings of the retention instrumentation) before any new feature scope is chosen, plus one ready one-line fix.

### Planned Scope

**PROVISIONAL: this release was kicked off without an owner scope pick. Amend this list before any
implementation.**

- **Three overdue checkpoint reads (read-only).** Each row's own kill criterion stays authoritative, and a fired
  criterion becomes its own owner-scoped item rather than being fixed inline.
  - `[CHECKPOINT — due 2026-09-22]` publication boundary (`docs/claude-plans/v0.132.0-publication-boundary-checkpoint-read.sql`):
    if it shows stranded curriculum, the response is to build F5, a publication surface in the Builder.
  - `[CHECKPOINT — due 2026-09-18]` `connection-timeout: 5000`: read Render `http_request_count` by `statusCode`
    for the 14 days after deploy against the pre-deploy window; if 5xx is material and sustained, revert to the 30 s default.
  - `[CHECKPOINT — due 2026-09-19]` Learning Connections demand: `linked_learner_relationships` grouped by status.
- **Retention instrumentation readings (`v0.157.0` follow-through, read-only).** (a) The first daily run after
  deploy: `retention.email.*.dispatch` logs show digest, then weak-concept, then `INACTIVITY`, with `INACTIVITY`
  near 40-47. (b) `[CHECKPOINT — due 2026-09-27]`: click/open tracking is emitting. (c) Re-date the retention
  checkpoint rows if the real deploy date matters. Owner prerequisite: Resend click and open tracking and the
  `email.clicked`/`email.opened` webhook events.
- **`RetentionEmailScheduler.runMonthly()` zone pin (backend, one line plus a test) — DONE and MERGED into this branch (PR #1442, `540b866b`); full backend build green, 2,511 tests.** Pin it to `Asia/Manila`
  like `runDaily`/`runWeekly`; the Backlog Index row has the detail. Routing: Claude-direct on its own branch and
  PR into this release branch (isolated, one file).
- **`INACTIVITY` email effectiveness: OWNER DECISION PENDING (evidence read, added 2026-09-24 at the owner's
  request; no implementation).** A read-only production read found 4,466 `INACTIVITY` sends to 235 users
  (2026-04-22 to 2026-09-23), 222 of them sent 10 or more, max 44, while only 6 of 408 accounts logged in during
  the last 7 days. Return rate after a send (any `analytics_events` row within 7 days, sends at least 7 days old):
  1st send 4.7%, 2nd 2.2%, 3rd-5th 1.3%, 6th-10th 1.0%, 11th+ 0.7%; 20 of 235 emailed users have any recorded event
  after their first send. **Limits: no control group (some return unprompted, so lift is lower than shown), "return"
  is any analytics event and may miss a plain login, and `marketing_emails_enabled` is on for 0 users with the
  consent basis of these sends unchecked.** This is evidence against the rationale for "do not cap `INACTIVITY`'s
  share (gated on opt-in growth)"; the rule itself is the owner's and is unchanged until the owner decides. Options
  to scope if wanted: cap sends per user, stop after N unanswered emails, or check the consent basis first. Any
  change is its own owner-scoped item, not an inline fix.

Anti-drift: no retention Stage 3 (it waits on the three retention `[CHECKPOINT]` rows); do not cap `INACTIVITY`'s
share (gated on opt-in growth); do not reorder retention dispatch (`docs/features/retention-emails.md`); nothing
here is feature scope. Choose feature scope explicitly.

### Scope disposition (signoff, 2026-09-25)

- **Three overdue checkpoint reads: SHIPPED as reads**, results above and on each Backlog row. Publication
  boundary not fired (re-dated to 2026-09-28); Learning Connections kill criterion does not fire; `connection-timeout`
  inconclusive, owner decision.
- **Retention readings (a) first daily run: SHIPPED**, as designed. **(b) `[CHECKPOINT — due 2026-09-27]`: NOT DONE,
  by the owner's call** — it is read on or after 2026-09-27 and stays an open Backlog row. **(c) re-date the retention
  rows: NOT NEEDED**, the real deploy was 2026-09-24 as assumed.
- **`runMonthly()` zone pin: SHIPPED** (#1442, `RetentionEmailScheduler.java:79`, guard `ScheduledJobCronContractTest`).
- **Added mid-release, owner-requested:** the `INACTIVITY` effectiveness evidence item and indexing the notifications
  Stage 1 plan; both have Backlog rows and are undecided.

### Checkpoint gate

Nothing in this release shipped ahead of its own evidence, so no new `[CHECKPOINT]` row is owed. The open
checkpoints are all carried from earlier releases and are re-stated on their rows.

### Known limitations

- The `connection-timeout` read is inconclusive (10-day pre-window, traffic growth, `v0.116.0`/`v0.123.0`
  confounds, log sample was the newest 30 lines only).
- The `INACTIVITY` return-rate read has no control group and measures any analytics event.
- Verification tier: one small code change with no authorization, money or production-data semantics, so a single
  `advisor()` pass rather than a pressure test.

### Shipped

- **`RetentionEmailScheduler.runMonthly()` zone pin** merged as PR #1442 (`540b866b`); full backend build green.
- **Checkpoint reads, run 2026-09-24 (read-only production and Render reads; results are also on each Backlog row).**
  - **Publication boundary (due 2026-09-22): NOT FIRED, re-date to 2026-09-28.** One public Review Set, `CPALE
    Comprehensive Review`, holds 325 unpublished topics and has never been published since `V141` (only the
    backfill stamp). Its oldest unpublished row is 2026-09-14, 9 days old, under the 14-day threshold; it crosses
    on 2026-09-28. `LET` (2026-09-10) and `PNLE` (2026-09-14) were really published, so the control is being used.
  - **Learning Connections demand (due 2026-09-19): kill criterion does NOT fire; re-read at the next release.**
    1 `ACCEPTED` relationship, unchanged since 2026-09-05; 2 invitations (1 `ACCEPTED`, 1 `PENDING`); no new
    activity in 19 days. One pair is weak evidence and may be a test pair.
  - **`connection-timeout: 5000` (due 2026-09-18): INCONCLUSIVE, leaning concerning, owner decision.** 500s went
    from 25 in the 10 available pre-window days (about 0.07%) to 352 in 09-05..09-18 (about 0.27%), with spikes on
    09-17 (59) and 09-18 (68); 502s peaked at 329 on 09-17. Assumptions: deploy taken as 2026-09-04; Render keeps
    only 30 days so the pre-window is 10 days; traffic also grew. Logs: 09-18 14:48 genuine pool saturation
    (`total=20, active=20, waiting=4`); 09-18 16:03 and 09-22 06:04 pool timeouts following Postgres I/O errors
    (pool collapsed to 7 then 2), which looks like the DB dropping rather than load. Only the newest 30 log lines were
    read, so this is not a count. `v0.116.0` and `v0.123.0` confound it. The row's "material and sustained" has no
    number, so the kill criterion was not applied.
  - **First `v0.157.0` retention run, read 2026-09-25 (fired 2026-09-24T18:45Z = 02:45 Manila): AS DESIGNED.** Dispatch
    order was digest (18:45:06.497), then weak-concept (18:45:06.530), then `INACTIVITY` (18:45:23.422), about 17 s
    total, no errors, one instance. Digest `budget=60 attempted=14 sent=14 skippedForBudget=0`; weak-concept
    `budget=46 attempted=0`; `INACTIVITY` `budget=46 sentToday=14 attempted=46 sent=46 skippedForBudget=75`, so it
    landed inside the expected 40-47 band instead of the old pin at 60. `email_log` agrees (14 `DUE_CONCEPTS_DIGEST` +
    46 `INACTIVITY` = 60, the full shared budget). The pre-deploy baseline was the 2026-09-23T18:45Z run on
    `v0.156.0` code: `inactivity budget=60 sent=60`, `dueConceptsDigest=23`, no per-type dispatch lines. The digest
    due-count differs day to day (14 vs 23), so the two are not a like-for-like "14 of 23". `email_log.clicked_at` is
    still 0 (click tracking not enabled yet); `email_open_daily_counts` had 6 (09-23) and 3 (09-24) before this run. The open date is Resend's own event `created_at` in UTC (`ResendWebhookService.java:123`), so opens dated 09-23 that arrived after the 02:39Z deploy are late delivery, not a dating bug.
    `[CHECKPOINT — due 2026-09-27]` remains open. 75 eligible learners were skipped for budget; see the
    `INACTIVITY` effectiveness item above for whether that matters.
  - **Cross-note review re-check:** `quick_review_sessions` 906 total, `source_collection_id` NULL on all 906
    (179 since the Stage 1 audit); DEFER stands, gate is `[CHECKPOINT — due 2026-10-13]`.

## v0.157.0 - Watching More Closely

**Status: Released** (signed off and deployed 2026-09-24: Render live 02:40Z, `V149` applied 02:39Z, Vercel production 02:43Z)

Theme: bring in five already-open, independently-produced PRs — traffic analytics, two production
incident findings, refreshed GPT product-context docs, and a resolved retention-communication channel
doctrine — onto one release branch instead of merging each straight to `main`, then implement the
scoped pool-observability and retention-email instrumentation follow-ups without triggering a deploy
until the owner is ready.

### Planned Scope

- **Vercel Web Analytics (frontend).** PR #1426, auto-generated by Vercel's own GitHub integration
  after the owner enabled Web Analytics on the (free/Hobby) Vercel plan: adds `@vercel/analytics`
  and one `<Analytics />` component to the root layout. Confirmed earlier this cycle: 50,000
  events/month included, no charge risk on overage (collection just pauses). Independently re-verify
  its own build/lint/test claims before signoff rather than trusting the PR body as-is.
- **Pool observability scoping and implementation.** PR #1427 started the `threads.max` checkpoint
  clock (owner confirmed removing Render's `SERVER_TOMCAT_THREADS_MAX` override) and scoped closing
  the saturation detector's two known gaps (non-request-thread registry coverage and scheduler
  contention) via a design verified against this project's actual Spring 7.0.5 jar. The implementation
  is recorded under Shipped below.
- **2026-09-22 production restart finding (docs only).** PR #1428 — a same-day incident where the
  known four-occurrence pool-exhaustion signature is explicitly absent; trigger left genuinely
  unidentified rather than rounded up to a guess.
- **GPT context docs refreshed to v0.156.0 (docs only).** PR #1429 — `GPT_CONTEXT.md` and
  `SURFACES_AND_FEATURES_CONTEXT.md` brought current for the notification-CTA and Campaign Feedback
  work; other modules left flagged, not silently touched.
- **Retention communication channel doctrine and Stages 1a–1b.** PR #1430 resolved "should retention
  email move to in-app notification" with a channel-role doctrine rather than a binary answer. Key
  finding: two of the four retention email intents already have live Dashboard current-state surfaces,
  so no in-app notification is recommended for them independent of further evidence. Stage 1a's
  click/open instrumentation and Stage 1b's budget governance are recorded under Shipped below; the
  evidence-dependent Stages 2–3 remain separate.

Anti-drift: the other docs-only PRs remain plans and findings, not diffs, until their own gates clear.
Pool observability and retention Stages 1a–1b are the implemented follow-ups to that original set.

### Shipped

- **Vercel Web Analytics.** `@vercel/analytics` 2.0.1 and one `<Analytics />` in the root layout
  (`frontend/app/layout.tsx:99`). Not taken from the PR body: `npm ci` accepts the lockfile, whose diff adds that
  package and also refreshes the stale root `version` field (0.96.0 to 0.156.0; no other dependency changed),
  `tsc --noEmit` is clean, lint has 0 errors, the production build succeeds and frontend Jest passes 2,489
  tests (1 skipped). The free-plan limit (50,000 events/month, collection pauses rather than charging) was
  checked against Vercel's published limits earlier this cycle and is not repository-verifiable.

- **Stage 2 evidence bound for the retention instrumentation (docs).** From a read-only production read (queries and results in
  `docs/claude-plans/2026-09-23-retention-volume-read.sql`): only `INACTIVITY` and `DUE_CONCEPTS_DIGEST` have a measurable audience (`WEAK_CONCEPT` 2 opted in,
  `WEEKLY_SUMMARY` 1, `KNOWLEDGE_IMPACT_DIGEST` 0 — zero sends in 90 days each). Two-tier bound on clicks: 14
  days, 100 recipients, and 30 clicks or 2,000 sends; 60-day backstop reads an unmet type as underpowered (a
  re-date, not a verdict); kill criterion `INACTIVITY` click-through under 1%. Plan §I. The dated checkpoint
  rows are in `ROADMAP.md`.

- **Cold pressure test and its remediation.** Four cold reviews (Opus on retention, Sonnet and then Opus on
  pool observability, and Codex across the whole release) tried to falsify the release against its plan; each
  finding was verified in code before fixing, and several were rejected or downgraded with reasons below.
  Verification at signoff: backend `clean install` BUILD SUCCESS with 2,511 tests including the real-Postgres
  suite, frontend Jest 2,489. Fixed
  (PR #1437): a non-ISO click timestamp escaped the catch and would 500 (`DateTimeParseException` is not an
  `IllegalArgumentException`); the digest-first order coupled a digest failure to the day's `INACTIVITY` sends,
  so the digest call is now isolated; three of four executors' decoration was unguarded by any test. Doc
  corrections: `WELCOME` does have a writer, `clicked_at` is first-processed, the open counter is
  whole-account. Two of these were defects in this release's own earlier work.

- **Docs-only inputs, shipped as documents:** the pool-observability plan (#1427), the 2026-09-22 restart
  finding (#1428, trigger still unidentified), GPT context refreshed to `v0.156.0` (#1429), and the retention
  channel doctrine (#1430).

- **The retention budget now governs all five scheduled retention email types against a retention-only
  count.** `INACTIVITY`, `WEAK_CONCEPT`, `WEEKLY_SUMMARY`, `DUE_CONCEPTS_DIGEST`, and
  `KNOWLEDGE_IMPACT_DIGEST` each recompute the available budget before bounding candidates; the orphaned
  public `sendInactiveUserEmails()` entry point now shares the same budgeted inactivity path. The count
  explicitly excludes transactional mail, dead/unclassified enum values, and the separately capped
  admin-triggered `RE_ENGAGEMENT_2025` campaign, removing that campaign's accidental cross-talk with
  automated retention dispatch. **Send order is now the priority mechanism, and that was found only by
  reading production before signoff:** `INACTIVITY` sat at exactly 60/day (the 100-limit minus 40-reserve
  ceiling) on 10 of the last 14 days while `DUE_CONCEPTS_DIGEST` sent 0–22/day unbudgeted. Extending the
  budget to the digest with `INACTIVITY` first would have starved the digest to ~0 on most days, so
  `runDaily` now dispatches the digest, then `WEAK_CONCEPT`, then `INACTIVITY` (`RetentionEmailScheduler.java:30`).
  Daily sends stay at the cap and `INACTIVITY` yields roughly the digest's volume (about 60 down to 40–47/day);
  **the later-running weekly and monthly types do not get the same protection — see Known limitations.** Two
  guards fail if the order regresses. `transactionalReserve` (40) is unchanged, though real transactional
  volume is 0–1/day; lowering `EMAIL_TRANSACTIONAL_RESERVE` would NOT help, because `INACTIVITY` has more
  eligible learners than budget and would absorb the extra room.

- **Retention email clicks now correlate to a send record without Resend message-id plumbing.** The
  five dispatched retention types reserve their UUID `email_log.id` before rendering and add inert
  `source` and `e` query parameters to the CTA, while persisting the row only after a successful send.
  Verified `email.clicked` webhooks read Resend's documented `data.click.link` and
  `data.click.timestamp`, then set that row's nullable `clicked_at`; unknown, purged, mismatched or
  malformed correlations are acknowledged and skipped. `email.opened` uses top-level `created_at` to
  increment `email_open_daily_counts` by UTC day with no per-send correlation. `EmailService`, Resend
  message ids, and `UNFINISHED_NOTE` remain unchanged; Stage 1b's budget governance is described above.
  Source doctrine:
  `docs/claude-plans/retention-communication-channel-doctrine-final-plan.md` §D/§I.

- **Pool saturation diagnostics now cover DB-bound background work.** A shared task decorator registers
  the four DB-touching executors and every `@Scheduled` job (all route through the six guarded scheduling methods, which one test exercises) in `InFlightRequestRegistry`, using executor
  thread names or Spring's exact `ClassName.methodName` scheduled-task description; the detector's own
  `poll()` is explicitly excluded (test-proven mid-cycle, not just after). The custom scheduler subclasses
  `ThreadPoolTaskScheduler` rather than using plain `setTaskDecorator()` — verified against the actual
  resolved jar (bytecode) that `ThreadPoolTaskScheduler` hands the configured `TaskDecorator` a
  `RunnableScheduledFuture` wrapper, not the user's task, which would have silently discarded every
  scheduled job's description; the subclass pre-decorates the real task before Spring wraps it, and the
  decorator no-ops on a `RunnableScheduledFuture` it's handed directly to avoid double-instrumenting.
  Two `scheduled-task-` threads mean ONE slow DB-bound job can no longer starve the detector's polling; two DB-bound jobs firing at the same instant (for example 02:45Z) still can, for up to Hikari's connection timeout.
  `runDaily`/`runWeekly` (`RetentionEmailScheduler`) are runtime-verified still anchored to `Asia/Manila`
  after the scheduler swap (real `CronTrigger.nextExecution()` assertions, not inspection). **`runMonthly`
  was NOT part of that verification and has no zone pinning at all — a pre-existing gap, not introduced
  here, out of scope for this change and tracked as its own Backlog Index row** (see
  `docs/product/ROADMAP.md`). Diagnostic registration
  and cleanup fail open, and cleanup is unconditional when work throws. Source and design rationale:
  `docs/claude-plans/done/2026-09-22-pool-observability-non-request-thread-coverage-plan.md`.

### Known limitations

- **`WEEKLY_SUMMARY` and `KNOWLEDGE_IMPACT_DIGEST` are budget-starved, permanently, while `INACTIVITY` saturates
  the cap.** They run Sunday 18:00 Manila and on the 1st at 09:00 host time (17:00 Manila), after the 02:45 run
  has used the day's budget, so they start with budget 0 and "eligible later" only reaches the next week or
  month. Immaterial today (1 and 0 opted-in learners; no sends in 90 days) but more opt-ins would NOT unlock
  them. Owner chose to document rather than cap `INACTIVITY`'s share now; a Backlog row gates the fix on
  opt-in growth. `sendWeeklySummaryEmails_independentlyRespectsExhaustedBudget` asserts the starvation as
  correct behaviour.
- **The admin `RE_ENGAGEMENT_2025` campaign no longer counts toward the budget.** A campaign batch of up to
  100 plus ~60 retention sends can exceed Resend's 100/day on the same day.
- **`clicked_at` is the first click PROCESSED, not necessarily the earliest.** The webhook IS now exercised over
  real HTTP (`ResendWebhookHttpTest`: signed click, non-ISO timestamp, signed open, unsigned request), but the
  real check that Resend delivers these events is still the deploy + 3 day smoke read.
- **The click marker is a capability, not a binding.** `e=<uuid>` is an unguessable v4 id, so a learner cannot
  guess another learner's, but anyone who HOLDS one (for example from a forwarded email) can flag that one send
  as clicked; the handler checks the email type, not the recipient. Impact is one analytics flag. It is
  inert in the sense that no frontend code reads `e` (checked by search, not by a test).
- **A send whose row fails to persist leaves an orphan marker.** If Resend accepts the email and the
  `email_log` save or the surrounding commit then fails, the delivered link carries an `e` with no row (the
  click is logged and skipped) and no cooldown row exists. The same window existed before this release.
- **Budget and cooldown checks are not atomic.** Count, check and send have no lock, so two overlapping
  instances or a duplicate cron fire could overspend the budget or double-send. Pre-existing, not introduced
  here; the service runs one instance (checked in Render), so overlap is limited to deploy hand-over.
- **A database failure in the click or open handler returns a 5xx on purpose,** so Resend retries a transient
  outage instead of losing the event; only malformed payloads are acknowledged and skipped.
- **The scheduler bean calls `initialize()` and Spring calls it again,** abandoning one executor that never
  started a thread. Harmless; the existing executors follow the same pattern.
- **The open counter is whole-account** (verification and password-reset opens are included) and keyed by UTC
  day; directional only.
- **Instrumentation is unverified emitting until deploy.** It needs Resend click and open tracking on the
  sending domain and the webhook subscribed to `email.clicked`/`email.opened`; `RESEND_WEBHOOK_SECRET` is staged
  in Render.
- **`InFlightRequestRegistry` is keyed by `Thread`,** so an inner decorated task's removal would wipe an outer
  entry on the same thread. No reachable trigger was found (latent).
- **A registry entry means "running", not "holding a connection".** A generation thread in the middle of an LLM
  call appears in a saturation log line exactly as one holding a connection does; read the log with that in mind.
- **The decorator is a Spring bean, and the scheduler subclass is what makes it work.** Spring Boot applies a
  lone `TaskDecorator` bean to its own executor and scheduler builders, and on Boot's default scheduler it would
  silently register nothing (it receives the internal future, not the job). The bean-level test in
  `AppConfigTest` fails if the custom scheduler is removed or replaced.
- **`runMonthly` has no zone pin** (pre-existing; Backlog row).

### Deploy notes

- `V149` runs on deploy: a nullable `ADD COLUMN` on `email_log` plus a new table; additive.
- No API form is removed, renamed or made required, so there is no frontend/backend deploy-ordering constraint.
- **Behaviour change to expect:** `INACTIVITY` drops from about 60 to 40–47 a day. Confirm with
  `retention.email.*.dispatch` log lines after the first daily run.
- Owner check at deploy + 3 days: `SELECT count(*) FROM email_log WHERE clicked_at IS NOT NULL` and
  `email_open_daily_counts`; zero of both means tracking or the webhook subscription is off.

### Signoff scope record

- Vercel Web Analytics: shipped (`layout.tsx:99`). Pool observability: shipped, and CHANGED from the plan —
  plain `setTaskDecorator` on the scheduler would have lost every job's description, so a subclass was needed
  (`InFlightThreadRegisteringTaskScheduler`; `AppConfig.java:41-44`). Restart finding, GPT context refresh,
  retention doctrine: shipped as documents. Retention Stages 1a and 1b: shipped
  (`ResendWebhookService.java:96,119`; `RetentionService.java:88,691,724,771`; `V149`). Stage 2/3: not started
  by design. Nothing in Planned Scope is unbuilt.
