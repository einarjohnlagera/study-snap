# v0.174.0 LLM tiering & remediation — Feature Planner audit and deliverable split

**Status: finalized audit, Pass-2 "hard go" per this session's charter.** Audits
`docs/claude-plans/2026-10-16-v0174-llm-tiering-remediation-tightened.md` (the owner+Product UX tightened
plan, saved verbatim) against binding artifacts and real code, then splits its 10 required deliverables by
who actually produces each. One challenge is raised under the charter's Pass-2 bar (release size); no
binding-artifact contradiction was found. Everything else in the tightened plan is finalized as written.

## Binding-artifact audit — no blocking contradiction found

Checked against `AGENTS.md`, the ADRs, `EXAM_MODES.md`, the prod-DB read-only rule, and verified code:

- **Versioning rule (`CLAUDE.md`):** *"The Study Pack is the generated version of a note. Never
  auto-regenerate... Regeneration always requires explicit user confirmation."* The tightened plan's §9
  and its Additional Decisions §2 both require this exactly ("Do not automatically regenerate... explicit
  user confirmation... optional, user-initiated"). **Consistent, not contradictory** — the plan reinforces
  an existing locked rule rather than conflicting with it.
- **Prod-DB read-only rule:** the plan's Execution Boundaries list "Do not mutate production data" and "Do
  not make API calls" — consistent with every boundary this session already operates under. No write is
  implied anywhere in the 10 deliverables; any eventual repair SQL follows the existing owner-run `.sql`
  handoff pattern (as both prior answer-key repairs did).
- **`FeatureGateService` as sole source of truth for plan-based access** (`CLAUDE.md`, Feature gating
  section): the plan's §1 requirement — *"Model selection must use the actual subscription entitlement,
  not client-supplied tier information"* — is the same discipline already enforced for feature gating.
  No new mechanism needed; routing should key off the same server-side `PlanType`/`isPaid()` read
  `StudyPackService.java:721` already uses, not a new entitlement check.
- **ADR-002 (quiz answer identity):** ratified but **not implemented** (H6 deferred). The tightened plan's
  §5 ("Investigate whether question-level repair is supported by the current data model") is answered by
  this ADR's own Consequences section, not a new investigation: *"No migration, no backfill, no new
  column... `correctIndex` remains the stored, graded, rendered field for every row, old and new alike."*
  Scalar `correctIndex` repair is the existing, twice-proven pattern (2026-09-22 and 2026-10-16 repairs)
  and is fully supported today. No contradiction; the plan's own question already has a code-anchored
  answer.
- **`EXAM_MODES.md`'s locked 5-mode contract:** untouched by anything in this plan — no mode is added,
  removed, or restructured.

## The one Pass-2 challenge: release size

**Artifact:** `CLAUDE.md`, "Release SIZE is the biggest lever on verification cost, and it compounds...
When asked to fold more items, say what it does to the verification tier."

**Quote, applied:** as written, v0.174.0 as scoped by the tightened plan folds in roughly eight distinct
items: the three-slot model migration, the PREMIUM routing-bug fix, an optional regeneration UX, pricing
and in-app subscription copy, an in-app announcement, a Sol-as-verifier remediation pipeline, the CRITIQUE
migration, and new retention instrumentation. **Two of these are independently Tier-H triggers** per
`CLAUDE.md`'s own risk table: the model swap changes real per-generation cost (money semantics), and the
remediation pipeline touches production question data (production-data semantics). The tightened plan
brief does not mention the model-decommission deadline (**2027-01-14**) anywhere, which is the actual
constraint that should be driving how much gets folded in now versus later.

**Stating the contradiction, per the charter's own rule — names the artifact, states the consequence, and
stops there, not a counter-proposal:** folding all eight items into one release raises v0.174.0 to the
heaviest pressure-test tier (full three-agent pass, ~490k tokens per `CLAUDE.md`'s own `v0.93.0`
precedent) for a release that has eleven months of runway before its actual deadline. That is Release
Implementor's and the owner's call, not this session's to make — but the plan's own §9 deliverable
("v0.174.0 release scope, acceptance criteria, and rollback strategy") asks this session to answer exactly
this question, so here is the answer: **split it.**

- **v0.174.0 (the deadline-bound part):** the three-slot model migration + the PREMIUM routing-bug fix.
  This alone is two Tier-H triggers and already earns a scoped cold falsification pass per `CLAUDE.md`'s
  gate — it does not need anything else added to justify that tier.
- **Separate release candidates, each scoped on its own terms once it has real data behind it:**
  optional regeneration UX (blocked on the "is quiz-only regen even safe" question the owner already
  deferred), the Sol-as-verifier remediation pipeline (needs the benchmark's real numbers first), CRITIQUE
  migration (needs its own quality assessment, not reuse of the quiz benchmark), pricing/subscription copy
  and the announcements (gated on the benchmark proving "Enhanced" is a true claim, not an assumption),
  and retention instrumentation (no urgency — `CLAUDE.md`'s own anchor rule warns against shipping ahead
  of evidence).

This is the same reasoning the owner already applied to quiz-only regeneration ("could unnecessarily
delay the model migration") — this challenge just extends it to the rest of the plan's scope, consistently
rather than as a one-off exception for the regeneration item alone.

## Deliverable-by-deliverable split — who actually produces each

The tightened plan's "Required deliverables" list is release-epic-sized, not feature-sized. Per this
session's charter it can audit and plan, not implement, benchmark-execute, query production beyond what's
already read, or publish marketing copy. Splitting by actual owner:

| # | Deliverable | Owner | Status |
|---|---|---|---|
| 1 | Model-routing matrix | **This session** | Partially done below; needs one Prod Investigator read to close (see next section) |
| 2 | Generation/quiz-pool call paths | **This session** | Partially done below (grading/historical-score safety confirmed); full call-path diagram is follow-up work, not blocking |
| 3 | Updated Luna-vs-Sol benchmark design | **This session** | Done — `docs/claude-plans/2026-10-16-llm-model-migration-benchmark-design.md`, updated this pass for `gpt-6.1-sol` (not `gpt-6-sol`), CRITIQUE's Ask Companion scope, and the adjudicated-quality rubric tightened plan §4 adds |
| 4 | Pricing-page / subscription benefit wording | **Already produced by Product UX** in the tightened plan itself (§2, Additional Decisions §1) | This session's audit found no binding-artifact conflict with it; gated on benchmark evidence per the plan's own rule, not on this session doing more writing |
| 5 | Content preservation/remediation policy | **This session** | ADR-002 + the two existing repairs already establish the policy (scalar repair, pack-agnostic matching, preserve `quiz_stamp`); formalizing it as a named policy doc is low-effort follow-up, not blocking |
| 6 | Prioritized public-pool repair plan | **Prod Investigator**, then this session | Needs real attempt-frequency/popularity data this session cannot query itself (charter boundary) |
| 7 | Cost and gross-margin scenarios | **Release Implementor**, after the benchmark runs | Needs real executed benchmark numbers (reasoning-token overhead especially) — modeling this now would be the same "estimate instead of measure" mistake already corrected twice in this investigation |
| 8 | Upgrade/downgrade/regeneration behavior | **This session**, scoped down | The versioning rule already answers most of §9 ("never auto-regenerate"); the open sub-question (quiz-only regen feasibility) stays an investigation per the owner's own instruction, not a v0.174.0 deliverable |
| 9 | v0.174.0 scope, acceptance criteria, rollback | **This session** (this document) + Release Implementor (final call) | Scope split given above; acceptance criteria follow from §1's testable-routing requirement — a read-only `SELECT` of `model_tier × model_used` post-deploy, reusing the existing `model_used` column, no new instrumentation needed |
| 10 | Remaining owner decisions | **This document**, next section | |

Marketing publication (in-app announcement, Facebook post) stays explicitly out of this session's and
Release Implementor's technical release process per the plan's own "Release communication sequence" —
drafted by Product UX already, published only on explicit owner approval, unaffected by this audit.

## Findings from this session's own code-reading pass (not previously documented)

**1. "No paying users" vs. 13,749 `PREMIUM`-tagged `study_packs` rows — resolved by Prod Investigator,
2026-10-16 (`docs/claude-findings/2026-10-16-study-packs-plantype-distribution-original-generations.md`).
Not a contradiction; two true-but-unrelated facts.** Of the 2,234 original generations: 2,002 belong to
the owner's own `dee4225c` ADMIN account, which holds an **indefinite PRO grant with `provider = NONE`**
(`SubscriptionService.java:528-531`'s `isBillableProvider` explicitly excludes `NONE` and
`INTERNAL_MIGRATION` — this is provably administrative, not a payment; running since 2026-03-19, no
`end_at`). The other 232: 228 plain-FREE real accounts, and 4 belong to the **one account in this
product's entire history that ever paid** (₱149, 2026-05-01, a real learner, role `USER`) — whose PLUS
has since lapsed to `EXPIRED` (`resolvePlan()` returns FREE for them today). Table-wide: exactly 1
`payment_transactions` `SUCCESS` row ever existed, against 16 `FAILED`; zero `XENDIT`-provider `ACTIVE`
subscriptions exist anywhere in production today. **"No paying users" is correct in substance.**

**What actually explains the 98% figure:** all 11,783 copy packs in the whole table trace back to that
same one admin account's original content — zero copies descend from any other account (checked
table-wide, not sampled). Copies cost zero LLM tokens (only 3 of 11,783 carry token data), so **the
routing-fix's real exposure is not "98% of the catalog" — it's new originals going forward only.** Real
external accounts generate 1–3 originals/week, essentially all FREE-plan — the fix changes nothing for
them. The admin account's own generation rate has fallen to ~0/week since late September, **but the
PRO/`provider=NONE` grant is indefinite and still `ACTIVE`**, so full exposure returns the moment that
account is used for bulk generation or remediation again.

**This makes owner decision #4 (below) the dominant cost lever for this whole migration, not organic
paid growth** — because the one account that would route to `gpt-6.1-sol` post-fix *is* the curation
account that seeded the public catalog, and the trigger is "the owner runs a generation/remediation pass
under that account," not subscriber growth. Average tokens/pack is similar across the admin account and
real users (~3,500–4,900 in / ~900–1,050 out), so this isn't even a "curator packs are bigger" effect —
it's purely which account does the generating.

**Two items Prod Investigator flagged but did not chase (left open, not blocking):**
- **34 of 11,783 copies show `model_tier=FREE` despite a `PREMIUM` source** — not pure byte-identical
  tier inheritance. Plausibly a FREE-plan user's in-place regeneration after copying (permitted by the
  Versioning rule), or the copy path doesn't always carry `model_tier` verbatim. Worth one check before
  anyone treats "copies always inherit tier" as a hard invariant in the routing-fix's acceptance criteria.
- **Whether `challenge_quiz_question_bank`/exam-pool generation is plan-tiered the same way** is
  unchecked — same `dee4225c`-dominant ownership shape as `study_packs` (99.9% per the 2026-10-15 read),
  so the routing fix's blast radius may reach those pools too, not just `study_packs`.

**2. Historical grading is already protected against a future answer-key repair — confirmed, not
assumed.** Checked because §5's "do not silently replace previously attempted questions in a way that
changes historical grading" needed a real answer. Two independent protections exist today:
- `QuickReviewSessionEntity.scorePercentage` (`entity/QuickReviewSessionEntity.java:71-72`) is a
  **persisted** field, computed and stored once at session completion — a completed session's score is
  not recomputed live from the pack on later views, so a later `correctIndex` repair cannot retroactively
  change a historical grade.
- For sessions still in progress, `QuickReviewSessionService.isStale()` (`:897-902`) compares
  `session.getQuizStampAtCreation()` against the pack's current `getQuizStamp()` and forces a fresh
  session on mismatch. Both of the real repairs already applied (2026-09-22, 2026-10-16) deliberately left
  `quiz_stamp` unchanged, confirmed in the 2026-10-16 repair's own verification language ("order and
  quiz_stamp are unchanged") — meaning the existing repair pattern already threads this needle correctly,
  and any future remediation should follow the same discipline: correct the stored value, leave the stamp
  alone, same as both precedents.

**3. CRITIQUE's scope is wider than "Interview Practice."** `OpenAiAskCompanionLlmService.java:80` reads
`properties.getSettings().getModelCritique()` directly — Ask Companion shares the CRITIQUE slot. Folded
into the benchmark design doc (§10's open item). Anyone scoping the CRITIQUE migration needs to test both
consumers, not just Interview Practice feedback.

**4. The FREE-model resolver has more call sites than the one the original PREMIUM-bug finding named.**
`OpenAiLlmStudyPackService.java` calls the zero-arg `requireConfiguredModel()` (FREE) at **six** locations
(`:231, :251, :288, :1793, :2199, :2273`), not just the one (`:288`, `generateStudyPackOnce`) the original
finding cited. The premium resolver is correctly called at exactly two (`:273` companion generation,
`:1860` Interview Practice question generation) — confirming Interview Practice's **question generation**
already correctly uses PREMIUM, while Interview Practice's **feedback scoring** uses the separate CRITIQUE
slot (`:1880`, `requireConfiguredCritiqueModel()`). **Full call-path identification by method name (what
each of the six FREE-only call sites actually generates — Study Pack content, additional-quiz top-up,
regeneration, etc.) is deliverable #2's remaining work, not done in this pass** — flagging the count now
so whoever picks up the routing-fix brief knows the surface is six sites wide, not one.

## Quiz-only regeneration — status per owner's clarification

**Investigation only, not a mandatory v0.174.0 feature**, per the owner's explicit 2026-10-16 instruction:
*"we don't yet know whether NoteLib can safely regenerate the quiz pool independently of the rest of the
Study Pack. Making that a hard release requirement could unnecessarily delay the model migration."*
Recorded in the tightened-plan file's header and inline at Additional Decisions §2. This session has not
investigated feasibility yet — no finding to report either way; flagged here only to confirm the scope
boundary is already correctly reflected in the saved plan text.

## Backlog overlap — don't propose as new

Tightened plan §11's "question reports" signal is the same affordance the Tier-3 semantic/ambiguity audit
already recommended (`ROADMAP.md` Backlog Index, "Question Quality — single-best-answer / ambiguity
verification" row, status `[DECISION]`, still open). Link to that row rather than treating it as a new
proposal — the audit already made the case for it in detail.

## One frontend check for whoever designs the in-app announcement

If the in-app announcement (Additional Decisions §3) ships as a one-time dismissible tip rather than a
persistent subscription-settings line, `CLAUDE.md`'s frontend conventions require it go through
`pickActiveGuidance()` (`lib/guidance-engine.ts`) — "do not add new one-time tips without going through"
it. Not this session's to design (marketing/UX surface, drafted by Product UX already) — flagged for
whoever eventually briefs the implementation.

## Remaining owner decisions (deliverable #10)

1. **Approve or adjust the release-size split above** — three-slot migration + routing fix in `v0.174.0`,
   everything else as separate candidates. Release Implementor's to execute once the owner confirms.
2. **Approve the benchmark's $10 spend cap** (tightened plan §4) before Release Implementor or a Codex
   brief runs it.
3. **Decide whether to set an explicit `reasoning_effort` for Sol** (recommend: test at default first,
   decide after seeing real reasoning-token counts — see benchmark design's Open Items).
4. **Decide whether curated public-catalog generation under the `dee4225c` admin account should route to
   Sol regardless of tier — this is now the central cost decision of the whole migration**, per Prod
   Investigator's 2026-10-16 read: real external accounts' PlanType mix makes the routing fix a no-op for
   them, so the entire cost exposure of "does PREMIUM traffic actually hit Sol" reduces to one account and
   one owner choice, not subscriber growth. Three options, stated plainly rather than left implicit:
   (a) leave the admin account's grant as PRO and accept it routes to Sol (~20x Luna) whenever the owner
   next bulk-generates or remediates under it; (b) treat curation as its own concern separate from the
   subscription-tier resolver (e.g., a curation account deliberately kept on FREE/Luna, or an explicit
   per-call override independent of `PlanType`); (c) accept the cost as the price of curated-content
   quality and cap/monitor it per tightened plan §8's recommendation. Needs an answer before the
   routing-fix brief is written, not after.
5. **Decide whether to chase the two open items Prod Investigator flagged** (34-row tier-inheritance
   anomaly; Challenge Quiz/exam-pool plan-tiering unchecked) before or after `v0.174.0` ships — neither
   blocks the migration itself, but the second could mean the routing fix's real surface is wider than
   `study_packs` alone.

## Owner decisions received (2026-10-16, relayed by Release Manager) — findings on the two assigned side
issues, plus the two open proposals

Owner decided: release-size split not yet approved (restated to owner separately); $10 benchmark cap
approved (read-only, no production writes, execution plan confirmed with owner before spend); Sol runs at
its documented default reasoning effort, measured then tuned; **decision #4 is a hybrid, not plain option
(c):** official NoteLib curation uses Sol with its own cost cap/monitoring and must **not** depend only on
subscription tier, while personal generation stays FREE→Luna / Plus,Pro→Sol; curated content stays
reusable (no repeat generation calls, i.e., keep the existing copy-is-free-of-tokens behavior). v0.173.0
stays unchanged; v0.174.0 kicks off after v0.173.0 signoff (10-22 merge gate).

Two side issues were assigned back to this session, "before implementation scope is final." Both closed
by code-reading alone — no further production read was needed for either.

### 5a — What `model_tier` means, settled from code, not data

**`model_tier` reflects the plan of whoever performed the StudyPack row's most recent generation action
(initial create, or a later explicit regeneration) — never the copier's plan at copy time, and never
recomputed afterward except by another explicit regeneration.** Two code paths are the *only* ways this
field is ever set, confirmed exhaustively:
- **Copy path** (`NoteService.copySourceStudyPack`, `:438-467`): clones `modelTier`/`modelUsed`
  **verbatim** from the source (`:460-461`). A copy, by itself, can never change `model_tier` — the two
  must always match unless something else touched the row afterward.
- **Generate-or-regenerate path** (`StudyPackService.java:690-724`, the shared save method both initial
  generation and in-place regeneration funnel through): looks up the **existing** entity by `noteId` if
  one exists (`:697-699` — this is what makes it "regenerate in place" rather than "create new"), then
  unconditionally recomputes `entity.setModelTier(...)` from the **current caller's** `planType` (`:721`).

**This resolves the 34-row anomaly without touching any historical data:** since the copy path cannot
produce a tier mismatch by construction, every one of the 34 `PREMIUM`-source/`FREE`-tagged copies must
have gone through an explicit regeneration by its (FREE-plan) copying user after the copy was made — the
Versioning rule in `CLAUDE.md` permits exactly this ("Regeneration... updates the existing Study Pack
in-place"). **These 34 rows are not a defect and need no correction** — they correctly record that a
FREE-plan user explicitly regenerated their own copy and got FREE-tier content, which is working as
designed. Optional, non-blocking cross-check if anyone wants independent confirmation: these 34 rows
should show real `input_tokens`/`output_tokens` (evidence of an actual LLM call), unlike an untouched copy
which Prod Investigator already confirmed carries none.

### 5b — Challenge Quiz and exam-pool generation: CONFIRMED to bypass the tier-aware resolver, and the
surface is much wider than previously scoped

**Every quiz-content generation method in `OpenAiLlmStudyPackService` is FREE-only today, except Interview
Practice's question half and Companion generation.** Traced every public method on the
`LlmStudyPackService` interface to its model resolver call:

| Method | Resolver called | Tier-aware? |
|---|---|---|
| `generateStudyPackOnce` (initial Study Pack + quiz, `:288`) | `requireConfiguredModel()` (FREE) | **No** — the already-known bug |
| `regenerateSummaryOnce` (`:231`) | FREE | **No** |
| `generateNoteFromTopicOnce` (`:251`) | FREE | **No** |
| `generateQuickReviewStudyTip` (`:1793`) | FREE | **No** (low stakes — a text tip, not a quiz question) |
| `generateAdaptivePracticeQuiz` (`:1832`) | FREE | **No** |
| `generateChallengeQuiz` / `generateMoreChallengeQuiz` (`:1906`, `:1937`, via `generateChallengeQuizWithSchema` `:2273`) | FREE | **No** |
| `generateBoardExamQuiz` (`:1966`) | FREE | **No** |
| `generateLongExam` / `generateLongExamParallel` (`:1995`, `:2013`+) | FREE | **No** |
| `generateTeacherQuiz` (`:2144`) | FREE | **No** |
| `generateCompanion` (`:273`) | `requireConfiguredPremiumModel()` | Yes (hardcoded premium, no branch needed) |
| `generateInterviewPracticeQuiz` (question generation, `:1860`) | Premium | Yes (hardcoded — correct because Interview Practice is itself a gated paid-only `Feature`, so every caller already qualifies) |
| `generateInterviewCritique` (`:1880`) | `requireConfiguredCritiqueModel()` | Separate CRITIQUE slot, not PREMIUM — not in scope for the FREE/PREMIUM question |

**None of the eight FREE-only methods above take a tier/plan parameter in their signature at all** — this
isn't a wrong branch, it's an absent one, same shape as the original `generateStudyPackOnce` finding, just
present at nine call sites instead of one. `ExamQuestionPoolService` (writes `exam_question_pool`,
answering the "is exam-pool generation plan-tiered the same way" question directly: **yes, the same way —
i.e., not tiered at all**) and `ChallengeQuizService` both route through this same
`QuizGenerationService` → `LlmStudyPackService` chain; `QuizGenerationService` itself only branches on a
mock-mode flag for local dev, never on tier.

**Scope consequence, stated plainly per your request to include this in the proposed scope:** fixing the
PREMIUM-routing bug is not a one-method patch. It means adding a tier (or resolved-model) parameter to the
`LlmStudyPackService` interface and threading it through all nine FREE-only call sites and their callers
(`StudyPackService` ×4 already identified, `AdminStudyPackTransactionHelper`, `ChallengeQuizService`,
`ExamQuestionPoolService`, `QuickReviewAdaptivePracticeService`, `OfficialChallengeQuizTemplateService`,
`LongExamService`) — a real interface change with regression-test surface across every quiz mode, not a
single-file fix. This reinforces the release-size challenge above rather than changing its recommended
split: the fix still belongs in v0.174.0 (it's the reason the release exists), but it is bigger than the
original Backlog row implied, and should be sized as such when the Codex brief is written.

### Curation-identification proposal (your open ask)

**Recommend: a role check, narrower than the existing `CuratorAuthoringPredicate.isCurator()`.** That
predicate already exists and is already used for curated-authoring permissions
(`NoteService`/`NoteGenerationService`/`NoteBulkGenerationService`), defined as `user.getRole() ==
UserRole.ADMIN || user.getProfileType() == ProfileType.TEACHER` (with an onboarding guard). **Reusing it
unmodified for Sol-routing would be wrong**, not just imprecise: it would route every `TEACHER`-profile
account's personal note authoring to Sol too, regardless of payment — a real teacher using NoteLib for
their own class is not "official NoteLib curation," and routing that whole population to Sol by default
reopens exactly the uncontrolled-cost risk decision #4 exists to prevent. **Recommend keying the
curation-routing check on `UserRole.ADMIN` alone, not the full `isCurator()` predicate** — this is a field
already being read today, requires no new abstraction, and matches the actual observed pattern exactly
(100% of the public catalog traces to the one `ADMIN` account). Leave `isCurator()` itself untouched for
its existing authoring-permission purpose; this is a second, narrower, independent check at the
model-resolution point only.

**A sharper alternative, if the owner wants the check tied to the content rather than the account:** key
off the same curated-authoring code path already gated by `isTeacherOrAdmin`/`curator` in
`NoteService`/`NoteBulkGenerationService` (i.e., an explicit flag set only on the bulk/curated-authoring
entry points), rather than inferring "this is curation" from the account's role at generation time. This
is more precise — it survives the owner's `ADMIN` account ever being used for something non-curation —
but is a larger change (a new parameter threaded through, not a read of an existing field). Recommend
starting with the `ADMIN`-role check as the v0.174.0 MVP, since it is simpler and already matches reality,
and revisiting only if the account's usage pattern ever diverges from "this account = curation."

### Curation cost-cap shape proposal (owner sets the value)

**No dollar-cost ledger exists anywhere in production today** (confirmed in the 2026-10-15 read:
`study_packs.estimated_cost` is NULL on every row and nothing populates it). A hard pre-generation quota
gate would need that ledger built first — real scope, not a config flag. **Recommend starting with
monitoring, not a hard block:** a scheduled read-only computation (reusing the `input_tokens`/
`output_tokens` columns already on every row, no new write) that sums the `ADMIN` account's Sol-tagged
generation cost for the current month against an owner-set dollar threshold, and alerts (not blocks) when
crossed — curation is an owner-initiated, supervised process, not a live end-user path, so a hard cutoff
mid-curation-run is more disruptive than useful at this stage. A hard block can be added later if
monitoring shows it's actually needed. This leaves end-user Plus/Pro usage on its existing
`FeatureGateService`/`UserUsageEntity` quota mechanism, completely unchanged — this proposal only adds a
new monitoring read for the one `ADMIN`-routed cost stream, not a new billing control (consistent with
tightened plan §8's "recommend cost caps... but do not implement new billing controls without approval").

## Handoff

**Complete and ready for Release Implementor.** The blocking production read landed 2026-10-16
(`docs/claude-findings/2026-10-16-study-packs-plantype-distribution-original-generations.md`) and is
folded in above. This audit, the tightened plan, and the updated benchmark design go to Release
Implementor next for `v0.174.0` kickoff scoping, per the owner's decision on the release-size split and
owner decision #4 above — the one real open question left is a product/cost call on the curation
account, not a missing fact.
