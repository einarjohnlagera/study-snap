# RELEASES.md - NoteLib

## v0.147.0 - The Escape Hatch

**Status: Released**

Theme: a curator whose Bulk Regenerate batch expires can no longer see it start again — a permanent
dead end from a single 404 that this release turns into a real return-to-start path.

### Planned Scope

- **Bulk Regenerate stuck-batch fix (frontend).** `bulk-regenerate-modal.tsx` seeds `batchId` from
  `sessionStorage` with no TTL awareness. Receipts expire 24h after creation
  (`NoteBulkRegenerationReceiptService.RECEIPT_TTL_HOURS`); an expired or unknown batch id 404s at
  `NoteBulkRegenerationReceiptService:55` (deliberately indistinguishable from "not yours"). The poll's
  `catch {}` swallows every failure including that 404, and the stop condition requires a `200`
  (`finished`/`stale`), so the poll runs forever at its 3s cadence while the stored `batchId` keeps the
  preflight (start) view permanently hidden behind the progress view. **Leg A** discriminates the 404 as
  terminal — stop polling, clear the stored id, return to preflight, surface the backend's own message
  ("That regeneration batch is no longer available.") rather than inventing new copy. **Leg B** adds an
  explicit "start a new batch" / dismiss action that clears the stored id independent of the poll, so a
  curator is never dependent on the poll noticing anything to escape a stuck view. Source:
  `docs/claude-findings/2026-09-12-bulk-regeneration-modal-wedged-stale-batch-id.md` (finding) and
  `docs/claude-plans/2026-09-12-bulk-regeneration-404-terminal-state-fix-plan.md` (fix plan), both
  untracked on disk, indexed in `ROADMAP.md`'s Backlog Index.

Anti-drift: do NOT extend the 24h receipt TTL (deliberate retention choice — a longer TTL only moves the
threshold and leaves the wedge intact past it). Do NOT remove `sessionStorage` persistence (deliberate —
it lets a curator navigate away and return to a running batch). Do NOT make the poll's `catch` rethrow
everything — a transient non-404 failure must still be swallowed and retried, only a 404 is terminal. Do
NOT change the 404 contract's indistinguishable unknown/not-yours/expired semantics, and do NOT add a
distinguishable "expired" status — that would leak batch existence to a non-owner. Do NOT touch
`queueBatch`'s write-before-return ordering (`NoteBulkRegenerationService.java:238-243`) — it is what
makes "no rows" a reliable diagnostic elsewhere. Do NOT fold in the unrelated `INVALID_REFRESH_TOKEN` 401
finding — unproven relation, would change the verification tier. A1 alone (discriminate on 404 status),
not A1+A2 (a retry-count bound) — the bound would address a different, unconfirmed failure mode. No
backend change, no migration, no new endpoint — routing is Claude Code inline (frontend only, one file,
clear root cause), verification tier is one `advisor()` call.

### Shipped

- **Bulk Regenerate stuck-batch fix (frontend).** `frontend/components/library/bulk-regenerate-modal.tsx`
  — Leg A discriminates a 404 on the receipt poll as terminal (stops polling, clears the stored batch id,
  returns to preflight with the server's own message); Leg B adds a "Start a new batch" action that does
  the same reset independent of the poll. `docs/features/bulk-regeneration.md` updated.

---

## v0.146.0 - Knowledge, Not Lost

**Status: Released** (kicked off 2026-09-14, signed off 2026-09-14, base branch `releases/v0.146.0`,
cut from `main` after `v0.145.0` merged as #1388 and tagged — Vercel and Render both confirmed live on
`1be308b7`. PR #1389 (implementation) and PR #1390 (pre-signoff findings) merged into the release branch.)

Theme: an intact Study Pack stays usable for every learning action even when the note's most recent
generation attempt is still running or has failed — fixing the only generation-failure pattern that has
ever occurred in production (7 of 7 historical failures were regenerations on notes that already had a
complete, valid Study Pack).

**Production facts, re-verified read-only at kickoff, 2026-09-14 (not carried over from the Stage 2
plan's 2026-09-13 read):** all 7 historical `generation_failed_at IS NOT NULL` notes are `GENERATED` with
a `DONE` pack today — fully recovered, so the defect has 7/7 historical occurrences but zero current live
instance. Zero `study_packs` rows have an empty/null `quiz` (the Quick Review guard, D1/D5, is a latent
fix). Zero notes are currently `GENERATING` (the stranded-generation recovery endpoint, §I, currently
serves a population of zero). None of this changes the design — all three gaps are real and worth closing
— but the release note is honest that it is closing gaps with no current live instance, not an active
incident.

### Planned Scope

- **Artifact-first learning availability (backend + frontend).** Derives `studyPackDone` (and, on
  `NoteCollectionItemResponse`, `hasKeyConcepts`) from the Study Pack's own `quiz`/`keyConcepts`/`status`
  fields via a new `StudyPackArtifactFacts` utility, and repoints every
  learning-action gate (Quick Review, Challenge Quiz, Adaptive Practice, Flashcards, Memorization,
  Long/Board Exam eligibility, Review Set premium-exam launch, public note pages) at that fact instead of
  Note lifecycle (`NoteStatus`/the `studyPackStatus` string). Fixes the live defect where a `FAILED` or
  `GENERATING` note hides an intact, complete Study Pack across nearly every surface. Reconciles the
  frontend's Long/Board Exam entry gates with the backend's already-correct `StudyPackStatus.DONE` rule.
  Adds a missing Quick Review backend guard (empty-quiz packs can no longer start a 0-question session).
  Gives the Flashcards/Memorization guard components a real recovery action instead of dead-end copy. Adds
  a narrow, owner-callable manual recovery endpoint for notes stranded indefinitely in `GENERATING` with no
  `generation_enqueued_at` timestamp (the sweeper itself is not redesigned).
  Source: `docs/claude-plans/note-visibility-learning-status-stage1.md` (Stage 1 audit) +
  `docs/claude-plans/artifact-first-learning-availability-stage2.md` (Stage 2 implementation plan, final
  decision block approved by the owner at this kickoff). Both untracked on disk, indexed in `ROADMAP.md`'s
  Backlog Index.

Anti-drift: no database migration, no new persisted state (every fact is derived at response-build time
from data already stored) — every new/changed DTO field is additive. No change to `PRIVATE`/`PUBLIC`
visibility, no new Library filter, no sixth exam mode, no `ConceptHealth`/mastery/readiness semantics
change, no quota/pricing change, no automatic generation or regeneration, no Cross-Note Review design, and
no re-opening of `v0.143.0`'s exam-pool invalidation work or its two adjacent seams
(`deactivateShareLinksForNote`, Challenge Quiz question bank). Backend entitlement enforcement
(`FeatureGateService`) is untouched everywhere. Deploy ordering: backend first (additive DTO fields), then
frontend (which makes `studyPackDone` load-bearing for the Long Exam entry gate and the Review Set
premium-exam predicate) — do not deploy frontend before backend this release.

### Shipped

- **Learning actions now follow the Study Pack artifacts they consume.** Quick Review, Challenge Quiz,
  Adaptive Practice, Flashcards, Memorization, Long/Board Exam entry, Review Set premium-exam launch,
  collection planning, and public note rendering no longer hide an intact pack merely because its Note is
  `GENERATING` or `FAILED`. Lifecycle status remains visible for retry and progress messaging.
- **Artifact facts are additive and derived at response time.** `StudyPackArtifactFacts` owns quiz,
  key-concept, and `StudyPackStatus.DONE` checks; note, collection-item, list-item, and public-detail DTOs
  now expose the precise facts their clients need. The private Library's ready predicate now matches the
  backend exam-source rule by checking for a `DONE` Study Pack.
- **Empty Quick Reviews fail before persistence.** Starting Quick Review with no quiz questions returns
  `400 QUICK_REVIEW_NOT_AVAILABLE`; resuming an existing in-progress session remains allowed.
- **Stranded first-generation work has an owner-only recovery path.**
  `POST /notes/{id}/recover-stranded-generation` reuses the configured note generation bound and the
  existing failure transition, performs no generation or quota charge, and returns
  `409 GENERATION_RECOVERY_NOT_ELIGIBLE` for early, repeated, or otherwise ineligible calls. Note Detail
  exposes the action after the bound and refetches the recovered note so its Retry action is reachable.
- **Flashcards and Memorization have working Generate/Retry actions.** Their guards call the existing
  generation API and refetch the Note; existing key concepts remain usable during and after a failed
  regeneration.
- **Pre-signoff falsification review (cold agent, no inherited context), PR #1390.** Confirmed
  `studyPackDone` derivation, per-mode entry-gate correctness, and deploy-ordering fail-safety across the
  merged diff. Found and fixed two gaps the implementing session's own pre-commit audit missed: a stale
  `docs/features/collections.md` claim describing a `hasQuizQuestions` field that was added by Codex then
  correctly reverted before commit (it would have widened a shared "lean projection" used by
  Dashboard/Progress/Adaptive Practice to pull the full `quiz` JSONB column, violating an existing
  performance guard test, and had zero real consumers) but never removed from the doc; and a missing
  regression test for this release's own headline Library scenario — a `FAILED` note whose prior Study
  Pack is still `DONE` now has a dedicated case in `NoteServiceLibraryPaginationIntegrationTest`.

---

## v0.145.0 - Knowledge, Not Role

**Status: Released** (kicked off 2026-09-14, signed off 2026-09-14, base branch `releases/v0.145.0`,
cut from `main` after `v0.144.0` merged as #1386 and tagged — Vercel and Render both confirmed live
on `22983935`. PR #1387 merged into the release branch at `94d2bbd3`.)

Theme: teach the LLM authoring pipeline that a professional role belongs to *who reads* a note,
not to the biomedical mechanism itself — closing a live mis-instruction on six production notes
that are currently generated under `Domain: Nursing` with no nursing content at all.

### How this scope was reached

Source: `docs/claude-plans/domain-context-biomedical-business-calibration-stage2.md`, a Stage 2
tightening of `docs/claude-plans/domain-context-biomedical-business-calibration-stage1.md` (both
untracked on disk at the owner's instruction; indexed in `ROADMAP.md`'s Backlog Index rather than
committed). Both `[PROD]` figures the plan's ADR-001 correction depends on were independently
re-verified at this kickoff via read-only `SELECT` (`course_programs` = 51, `NURSING` = 46,
`ACCOUNTANCY` = 0, `PROFESSIONAL_EDUCATION` = 232, total notes = 7,617, `NULL` context = 5,671) —
all matched the plan's one-day-stale figures exactly, so nothing had moved further.

**Workstream A — `BASIC_MEDICAL_SCIENCES` — is this release's entire code scope.** Six canonical,
multi-program Pharmacology notes (`Antibiotics: Mechanism of Action and Resistance`,
`Antibiotic Classes in Pharmacology`, `Pharmacological Management of Hypertension`,
`Pharmacological Management of Diabetes`, `Pharmacology of Insulin`,
`Respiratory and Gastrointestinal Pharmacology`) are mechanism-framed content, correctly clearing
`ADR-001:397` clause (a)'s ~10-note floor once the ~9 firmly-planned PNLE rows are counted, but are
currently forced onto `NURSING` because no coarser value exists — and are actively mis-instructed
today, generating under a `DOMAIN_CONSTRAINT` that names a professional role their content never
uses. The boundary test the plan validated against all 18 multi-program Pharmacology notes'
real summaries: *does a professional role appear in the knowledge itself, or only in who is
reading it?* — 6 mechanism-framed, 11 role-framed (stay `NURSING`), 1 unclassified pending a
curator reading its summary (not this release's work).

**Workstream B — Accountancy/Business/Finance — ships nothing in this release.** The plan's own
verdict is pre-CPALE calibration, not implementation. `ACCOUNTANCY` keeps `quantitative = true`
unchanged: the plan measured that `false` would be a 5-save/4-lose trade across the 154
Accountancy-program notes (all `domain_context IS NULL` today, zero currently classified
`ACCOUNTANCY`) — a coin flip, not the protective change its advocates wanted, because the
asymmetry argument that correctly justified `false` for Basic Medical Sciences (a precise,
discipline-specific repair keyword exists) does not transfer to accounting, where the
discriminating words are generic English (`tax`, `cost`, `income`, `return`) and would be
catastrophic under the codebase's unanchored `String.contains` matching. No new Domain Context is
minted for Business/Finance; the re-audit trigger (≥10 canonical notes stably shared across 2+
live programs, arriving via a committed CPALE curriculum plan) stands at 0 today.

**Owner decision 2 (widen `QUANTITATIVE_KEYWORDS`) ships, but not as a regression fix.** Of the
three strings Stage 1 proposed, two are measurably wrong: `"half-life"` matches zero notes
anywhere in the corpus, and `"clearance"` is a live false positive (77 corpus-wide matches, mostly
building/construction clearance, including 2 new false positives at the full keyword tier — one on
`PROFESSIONAL_PRACTICE_AND_REGULATION`, a value whose `quantitative = false` is a documented,
tested decision). Only `"pharmacokinetic"` survives measurement: +17 net-new matches at the Quick
Review tier (2 canonical notes, 15 learner copies of one already-`NURSING(true)` note). **This is
not a regression mitigation** — the plan measured that the regression Stage 1's condition was
meant to prevent affects zero notes (all three `NURSING`-today candidates already trip existing
keywords at both tiers). It closes a pre-existing Quick Review computation-guidance gap. Recording
it as regression prevention would be the `v0.116.0` / `v0.117.0` failure mode — a shipped item with
a named consequence that does not exist — so it is written up here as what it actually is.

**Owner decision 3 (widen the PPR description to cover Business Law / RFBT) is BLOCKED, not
dropped.** It is gated on a two-arm comparison (`ADR-001:291`'s tie-break) that requires *setting*
`domain_context` on three real production notes — a production WRITE, which is the owner's to run
under `CLAUDE.md`'s read-only rule, never Claude's, regardless of the plan itself being approved.
The exact `UPDATE`/verify/revert statements, the three notes' UUIDs (independently confirmed by a
read-only query at this kickoff), and the pass/fail condition were written to
`docs/claude-plans/domain-context-ppr-validation-armB.sql` and handed to the owner directly —
**deliberately not committed**, since a production `UPDATE` statement sitting in `docs/claude-plans/`
would read as a sanctioned runbook to a future session, the same shape as the read-only scripts
this repo *does* commit and instruct sessions to run. If the owner runs it and Arm B passes
(preserves statutory citations and legal terminology, does not import engineering-contract
framing), item 3 ships as a follow-up PR into this release branch; if it fails or is not run,
`BASIC_MEDICAL_SCIENCES` ships without it — the two are independent array entries in
`DomainContext.java`, bundled by owner convenience only, never coupled technically.

Anti-drift: no database migration (`notes.domain_context` is `VARCHAR` with zero CHECK
constraints; `@Enumerated(EnumType.STRING)` persists the name), no backfill of existing notes (the
six mechanism-framed notes are curator follow-up, outside this release), no
`isQuantitativeContext` resolver rewrite (only one keyword-array element changes), no program
catalog or Program Family change, and no Workstream B implementation of any kind. The
`QUANTITATIVE_KEYWORDS` unanchored-substring-matching defect (`ratio` ⊂ `corporation`, `solve` ⊂
`resolve`, `interest` ⊂ `interested`, etc. — proved against production) is reported in
`ROADMAP.md`'s Backlog Index and explicitly out of scope for this release; note that fixing it with
word boundaries would silently
break the `"pharmacokinetic"` entry this release adds, which depends on unanchored matching to
reach the subject "Pharmacokinetics".

Verification tier: **one `advisor()` call.** No new endpoint, so no `MockMvc` real-request test is
owed — said here rather than skipped silently. `frontend/lib/api.ts` is touched but the change is
a TypeScript union member only, emitting no JavaScript, so no `api-*.test.ts` request-shape test is
owed either. The diff does change behaviour (a new quantitative fall-through path, and — if item 3
ships — a PPR routing change), so the tests the plan's §A9 names must land in the same diff as
that behaviour, per the unexercised-change rule.

Deploy ordering: ship frontend and backend together. Backend-first is harmless (an unused enum
value); frontend-first is not — the new dropdown option would appear before an old backend can
persist it, and `DomainContext.fromString`'s `null`-on-unknown return silently drops a curator's
save rather than erroring. Run `scripts/check-deploys.sh` after the release PR merges.

### Planned Scope

- **Add `DomainContext.BASIC_MEDICAL_SCIENCES` (backend + frontend).** Append (never insert) the
  enum value with `quantitative = false`; append the matching `DOMAIN_CONTEXT_OPTIONS` entry with
  the plan's §A3 curator-facing description, which routes adjacent material to `Nursing` and to
  `Professional Practice & Regulation` in the same prose; add the TypeScript union member.
- **Widen `QUANTITATIVE_KEYWORDS` by exactly one string.** Add `"pharmacokinetic"` with a comment
  recording the coupling to the unanchored-matching defect (Backlog Index).
- **Tests, same diff:** `DomainContextTest` (label + `@CsvSource` row + method rename to
  `...Twelve...` + `fromString` round-trip), `domain-context.test.ts` (length 12 + new-value
  routing assertions), `OpenAiLlmStudyPackServiceTest` (the keyword's own guard: a
  Pharmacokinetics-subject context becomes quantitative via the keyword path at the Quick Review
  tier, plus a negative assertion that the label reaches the prompt and a distinctive
  multi-program `courseProgram` string never does), `StudyPackGenerationContextResolverTest` (new
  value resolves to its label, not the enum constant name). **The plan's §A9 item 10
  (multi-program guard: new value + 3 programs does not throw) was deliberately NOT added as a
  separate test** — `StudyPackGenerationContextResolver.assertGenerationReady` only checks
  `domainContext == null && programCount > 1`; it never switches on which value is set, so a
  BASIC_MEDICAL_SCIENCES-specific case could not fail differently from the existing generic
  coverage (`assertGenerationReady_allowsRetryAfterDomainContextIsSet`,
  `assertGenerationReady_rejectsMultipleProgramsWithoutDomainContext`). Recorded here rather than
  silently omitted. **§A9 item 6 (a PPR routing assertion in `domain-context.test.ts`) travels
  with item 3** — it is only meaningful once the PPR description itself changes, so it ships in
  the same follow-up PR if Arm B passes, not in this diff.
- **`docs/architecture/ADR-001-canonical-knowledge-architecture.md`** — revision-log entry
  recording the owner decision (name, enum, `quantitative = false`, the keyword condition as
  actually shipped, clause-(a) evidence), plus correcting two stale lines the plan's own
  re-verification found: *"three unused values"* → two are now in use (`PROFESSIONAL_EDUCATION`
  232, `NURSING` 46) `[PROD 2026-09-14]`; *"41 programs"* → 51 `[PROD 2026-09-14]`, ratio
  `12:51 = 0.235`.
- **`[BLOCKED — owner validation required]` PPR description widening (frontend).** Handed off via
  `docs/claude-plans/domain-context-ppr-validation-armB.sql` (on disk, deliberately **not**
  committed — see "How this scope was reached" above); ships as a follow-up PR only if Arm B
  passes.
- **`docs/features/domain-context.md`** — **NOT built, by decision rather than oversight.**
  `docs/features/study-pack-generation.md` already documents the mechanism in depth (fallback
  chain, the declared-`quantitative`-flag design, the keyword scan); a second dedicated doc
  covering the same ground risks the two silently diverging, which is a worse failure mode than
  the gap the plan named. Instead, swept every doc that enumerates the taxonomy by name so none
  goes stale invisibly: `study-pack-generation.md` and `challenge-quiz.md` (the duplicated
  `quantitative = false` value lists), `docs/features/notes.md` (the twelve-value list and count),
  and `docs/gpt-contexts/REVIEW_SET_SHAPING_CONTEXT.md` (the curriculum-shaping pipeline's closed
  vocabulary — the one place this would have gone stale with no diff to notice, since it is never
  touched by code changes).

### Shipped

- **`DomainContext.BASIC_MEDICAL_SCIENCES`** (`quantitative = false`), appended after
  `PLANNING_AND_SITE_DEVELOPMENT` — `DomainContext.java:33-39`. Curator-facing description added
  to `DOMAIN_CONTEXT_OPTIONS` — `frontend/lib/domain-context.ts`. TypeScript union member added —
  `frontend/lib/api.ts`.
- **`QUANTITATIVE_KEYWORDS` widened by exactly one string, `"pharmacokinetic"`** —
  `OpenAiLlmStudyPackService.java:179`, with a comment recording the coupling to the
  unanchored-substring defect tracked in `ROADMAP.md`'s Backlog Index.
- **Tests, same diff:** `DomainContextTest` (label + `@CsvSource` row + method rename + `fromString`
  round-trip), `domain-context.test.ts` (length 12 + new-value routing assertions),
  `OpenAiLlmStudyPackServiceTest` (keyword guard at the Quick Review tier + a negative assertion
  that the label reaches the prompt, never a multi-program `courseProgram` string),
  `StudyPackGenerationContextResolverTest` (new value resolves to its label, not the enum constant
  name). Backend full suite 2373/2373, frontend 216 suites / 2402 tests, `tsc --noEmit` clean.
- **`ADR-001-canonical-knowledge-architecture.md`** — revision-log entry (clause b) recording the
  owner decision, plus two stale-line corrections found by re-verifying production during this
  edit: *"three unused values"* → two now in use (`PROFESSIONAL_EDUCATION` 232, `NURSING` 46); *"41
  programs"* → 51, ratio `12:51 = 0.235`.
- **Doc sweep** — every place that enumerates the Domain Context taxonomy by name, whether or not it
  was in the code diff: `docs/features/study-pack-generation.md`, `docs/features/challenge-quiz.md`
  (the duplicated `quantitative = false` value lists), `docs/features/notes.md` (the twelve-value
  list and count), `docs/gpt-contexts/REVIEW_SET_SHAPING_CONTEXT.md` (the curriculum-shaping
  pipeline's own closed vocabulary — never touched by a code diff and the one place this would have
  gone stale invisibly), `docs/gpt-contexts/GPT_CONTEXT.md` and
  `docs/gpt-contexts/NOTES_AND_COLLECTIONS_CONTEXT.md` (both re-stamped at this signoff; the core
  brief's own "don't propose a 12th value" line was corrected, since a 12th had just shipped).
- **PR #1387**, merged into `releases/v0.145.0` at `94d2bbd3`.

### Not shipped

- **`[BLOCKED]` PPR description widening (owner decision 3).** Re-checked read-only at this
  signoff: all three RFBT notes named in `docs/claude-plans/domain-context-ppr-validation-armB.sql`
  still carry `domain_context IS NULL` `[PROD 2026-09-14]`, so the owner has not yet run the
  two-arm validation. Ships as a follow-up PR into a later release if and when Arm B passes; the
  two are independent `DomainContext.java` array entries, bundled by convenience only.
- **`docs/features/domain-context.md`.** Not built, by decision — see the Planned Scope note above.
- **§A9 test item 10** (a multi-program-guard test naming the new value specifically) — not added;
  `assertGenerationReady` is value-agnostic, so it could not fail differently from existing
  coverage. See the Planned Scope note above.

## v0.144.0 - No Backdoor Left

**Status: Released** (kicked off 2026-09-13, signed off 2026-09-13, base branch `releases/v0.144.0`,
cut from `main` after `v0.143.0` merged as #1383 and tagged — Vercel and Render both confirmed live
on `c899d418`. PR #1385 merged into the release branch at `e61ee2ce`.)

Theme: close the fourth and last known path that lets an exam question pool keep serving
questions from a Study Pack's replaced content — the two admin-only repair endpoints `v0.143.0`'s
own falsification pass found and flagged but did not fix.

### How this scope was reached

`v0.143.0`'s signoff recorded two open Backlog rows rather than folding more work into that
release, both explicitly gated "needs verification before it needs a release": shared quiz links
not deactivated on a `STUDY_PACK`-only regeneration, and `AdminStudyPackTransactionHelper`'s two
repair endpoints bypassing exam-pool invalidation entirely.

Two read-only production queries were run against each gate
(`docs/backlog-rows-475-476-production-read`, PR #1384, merged into this release branch). The
share-link row is **verified NOT currently live** — production carries exactly one active share
link, and its quiz was not updated after it was shared — so it stays open but deprioritized. The
admin-repair row's call volume turned out to be **permanently unanswerable**: no admin-action
audit trail exists anywhere in the schema, and the one plausible proxy — a `"|"` "enriched
summary" marker `regenerateOnePack` itself gates on — is a content-shape artifact of ordinary LLM
summary generation (pipe-delimited comparison tables, `developer.txt:50`), not a
repair-provenance signal, confirmed by reading the prompt directly rather than assuming.

**Fixing the admin-repair path anyway, on precedent rather than exposure evidence.** The gap is
confirmed in code, the fix is one file and two call sites, and it is the exact pattern `v0.143.0`
already built, tested, and mutation-verified for the learner-facing path. Waiting on a volume
number the data cannot produce is not a reason to leave a confirmed code defect open when the fix
is this cheap.

**⚠️ `v0.143.0`'s own record named `v0.144.0` for something else.** Its "How this scope was
reached" section says the owner deferred Learning Connections supporter onboarding
*"to `v0.144.0`"*, gated on `[CHECKPOINT — due 2026-09-19]` — six days out from this kickoff. That
was informal shorthand for "whichever release comes next," not a commitment to this specific
number; the owner chose this admin fix instead. Supporter onboarding remains gated on
`2026-09-19` for whatever release follows this one, and this release does not touch it.

### Planned Scope

- **Admin summary/quiz repair paths invalidate the exam pool (backend, 1 file).**
  `AdminStudyPackTransactionHelper.regenerateOnePack` (`:66-67`) and `repairMalformedQuiz`
  (`:121-123`) each replace `summary`/`quiz` in place with no exam-pool invalidation. Fix: call
  `examQuestionPoolService.refreshPool` for both `MODE_LONG_EXAM` and `MODE_BOARD_EXAM` after each
  save, inside the same `@Transactional` method, with the same `studyPackRepository.flush()`
  ordering `v0.143.0`'s falsification pass proved necessary to avoid the lock-order inversion it
  found there. Isolated bug fix, clear root cause, direct precedent — Claude Code implements
  inline, no Codex prompt.

**Explicitly NOT in scope:** the Challenge Quiz question bank (leg 2 of the same "derived
artifacts" defect class) — still unscoped, needs its own tracing pass before it needs a release.
The shared-quiz-link row — verified not currently live, left open at low priority, not folded in.
Adding an admin-action audit log — would answer future volume questions but is separate,
unscoped infrastructure work this release does not take on.

### Shipped

- **Admin summary/quiz repair paths now invalidate the exam pool.** `AdminStudyPackTransactionHelper.regenerateOnePack`
  (`POST /admin/study-packs/regenerate-summaries`) and `repairMalformedQuiz` (`POST
  /admin/study-packs/repair-malformed-quizzes`) each call `examQuestionPoolService.refreshPool` for both
  `MODE_LONG_EXAM` and `MODE_BOARD_EXAM` immediately after saving, with the same `studyPackRepository.flush()`
  before the pool call that `v0.143.0`'s falsification pass found necessary to avoid inverting the
  `study_packs` → `exam_question_pool` lock order. New `AdminStudyPackTransactionHelperTest` coverage
  (2 tests, `InOrder`-asserted save → flush → refresh-long-exam → refresh-board-exam) plus `never()`
  assertions on `examQuestionPoolService` added to all 6 pre-existing skip/failure tests, confirming the
  invalidation only fires on an actual content replacement. Both new/extended tests mutation-verified —
  confirmed to fail against the pre-fix code. Full backend suite: 2368/2368 passing.
  `docs/features/study-pack-generation.md` corrected — it previously named this as the one known
  unfixed gap.
- **Verification tier: single `advisor()` call, no cold agent** — checked against the nearest-miss
  trigger explicitly rather than leaving it unstated: `ExamQuestionPoolService.refreshPool` now has
  callers added by both `v0.143.0` (`StudyPackService`, PR #1382) and `v0.144.0` (this admin helper,
  PR #1385), but that is two releases touching a shared method, not two PRs *within* this release —
  the trigger as written did not fire. No auth/privacy boundary moved, no money/quota/production-data
  semantics changed, and no defect was introduced by this session and then fixed.

## v0.143.0 - No Way Out

**Status: Released** (kicked off 2026-09-11, signed off 2026-09-12, base branch
`releases/v0.143.0`, cut from `main` after `v0.142.0` merged as #1380 and tagged, deployed and
verified — Vercel and Render both confirmed live on `61153cc6`. PRs #1381 and #1382 merged into
the release branch at `f715dada` and `77b6c226`.)

Theme: two live defects found by re-verifying Backlog Index candidates against current code
rather than trusting their rows — a focus-mode trap that leaves a learner with no exit if Long
Exam submission hangs, and an exam question pool that silently keeps serving questions from a
Note's pre-regeneration content.

### How this scope was reached

Four Backlog Index candidates were checked before these two survived: **"Official Review Set
publication boundary" P3** claimed un-parked/unbuilt but is fully shipped
(`ReviewSetUpdateNotificationService.java`, commit `83074463`, `v0.135.0`); **Adaptive Practice's
recommendation engine** is population-blocked — `[CHECKPOINT — due 2026-10-05]`'s own kill
criterion says single digits means re-date, and a fresh read found 3 eligible users, unchanged in
a month, and 0 users with a cross-pack actionable weak concept; **Learning Connections supporter
onboarding** has a real, shipped-nowhere definition (`v0.97.0`, `learning-connections-phase-plan.md:443-522`)
but sits 8 days from `[CHECKPOINT — due 2026-09-19]`, which 6 consecutive releases have protected
from exactly this class of promotion — owner chose to defer it to `v0.144.0` rather than risk
contaminating the count; **"Support Another Learner" Phase 1** claimed a `[DECISION]+[EVIDENCE]`-blocked
axis-error gate but is fully shipped (`requireTeacherOrAdmin` removed in commit `cbc7d13c`,
`v0.89.0`) — this row also duplicates "Learning Connections" under a different name for the same
shipped arc.

**All three stale rows corrected in this kickoff commit, along with a fourth found in the same
pass** (Onboarding Intent Router's C8/C9 residuals — both already fixed in commit `826ca155`,
2026-08-12, row never updated). Full detail in `ROADMAP.md`'s Backlog Index scan note.

**Item 2's own scope was widened again before its Codex prompt was written.** Tracing the fix
surfaced that gating the pool invalidation on `regeneratingNoteContent` — the kickoff's own framing
— would have missed the *default* regeneration path: `POST /notes/{id}/regenerate` resolves an
absent/blank scope to `NoteRegenerationScope.STUDY_PACK`, which reaches the same worker method with
that flag `false`, even though the Study Pack's content is replaced in place either way. The prompt
(`docs/codex-prompts/v0.143.0-exam-pool-invalidation.md`, gitignored) calls the invalidation
unconditionally instead, and adds a `generationStatusAt`-stamp guard against a
concurrent-regeneration race the unconditional call would otherwise make more likely to trigger.
Delivered through Codex on 2026-09-12. **A related, separate, already-shipped defect surfaced during
the same trace and was flagged rather than folded in**: `deactivateShareLinksForNote` (the `v0.110.2` precedent
item 2 reuses) has the identical gate gap on the same default regeneration scope — recorded as its
own Backlog Index row in `ROADMAP.md`, not code-verified against production, and not fixed here.

### Planned Scope

- **Item 1 — Long Exam's focus-mode trap (frontend, isolated bug).** `long-exam/page.tsx:255`
  calls `useExamFocusMode(phase === "running")` with no `!submitting` guard, while its Leave
  button is `leaveDisabled={submitting}` (`:966`). If a completion request hangs, the learner has
  no visible exit — focus mode hides the header and the one exit control is disabled. Challenge
  Quiz already fixed this exact trap in `v0.131.0`: `challenge-quiz/page.tsx:1516` reads
  `useExamFocusMode(phase === "running" && !submitting)`, with a comment explaining why the guard
  is load-bearing. Long Exam was left out of that release's diff. Fix: apply the same guard.
  Inline-sized, ships first, its own PR.
- **Item 2 — exam question pools are not invalidated when a Note+Study Pack regeneration
  replaces content (backend).** `ExamQuestionPoolService.initiatePoolForMode` (`:220-227`)
  early-returns when a pool already exists and is READY/PENDING/GENERATING. Regeneration keeps
  the same `studyPackId` (the documented in-place versioning rule — quiz/session history stays
  linked), so the `initiatePool` call after regeneration (`StudyPackService.java:933`) is a
  silent no-op: Long Exam and Board Exam keep serving questions drawn from replaced content. The
  fix pattern already exists three lines above the omission, in the same method:
  `deactivateShareLinksForNote` (`:908`) does the analogous thing for shared quiz links, citing
  `v0.110.2`'s precedent explicitly in its own comment. Touches a `@Transactional` regeneration
  path carrying two quota meters — not copy-fix-sized, owes its own verification tier (below).

**Explicitly NOT in scope:** the Challenge Quiz question bank was flagged in the same Backlog row
as carrying the same staleness gap, but this kickoff traced only as far as confirming
`queueOfficialChallengeQuizTemplateSeed` is an official-template path, not the per-user bank —
where the per-user bank is actually populated is unknown. Scoping a third invalidation seam on a
structural analogy, without having traced it, is exactly the failure mode this kickoff's own scan
spent the night correcting. Leave it as an open question for whoever verifies it next, not a
planned item.

### Checkpoint reads closed at this kickoff

- **`[CHECKPOINT — due 2026-09-11]` `v0.114.0` — CLOSED, kill criterion (i) confirmed.** Read-only
  Render application log query, `ConnectionLifetimeStartupLogger` at boot, 2026-09-04 through
  2026-09-07 (8+ instances sampled): every single line reports
  `hibernate.connection.handling_mode=DELAYED_ACQUISITION_AND_HOLD` with `open-in-view=ON`,
  matching the test measurement exactly. `v0.112.0` §7 holds in the environment that matters.
- **`v0.62.0` Knowledge Impact conditional-rate checkpoint — RE-DATED, not closed.** The row's own
  premise (*"the new event has fired ZERO times because `v0.136.0` is not deployed"*) is now
  stale — `v0.136.0` deployed days ago. Fresh read: 1 distinct viewer, 3 `KNOWLEDGE_IMPACT_DASHBOARD_VIEWED`
  events, all 2026-09-09, none more than 2 days old. The conditional rate this row measures (did a
  viewer publish again within N days) is genuinely not yet measurable — N days have not elapsed —
  not a null read. Re-dated rather than read as a pass or fail.
- **⚠️ Tool-reliability finding, not a product one:** a read-only Render Postgres query without any
  `GROUP BY` returned an array for a scalar `user_id` column, and the identical array recurred
  verbatim across two unrelated queries against two different tables. Caught before it reached
  this file — re-ran with `count(DISTINCT user_id)` instead of raw ids. Treat any non-scalar
  result from this tool as suspect until re-verified with an aggregate query.

### Verification tier

**One scoped cold agent, falsification-framed**, for item 2 only — it changes what questions a
learner is served and touches a `@Transactional` path with two quota meters, the class of change
CLAUDE.md's verification-tier gate reserves for more than a single `advisor()` call. Item 1 is a
single-expression fix with a direct precedent in the same codebase; a normal test plus `advisor()`
on the diff is enough.

### Routing

**CLAUDE CODE inline** for item 1 (one file, one expression, direct precedent). **CODEX** for item
2 (backend service + regeneration path + tests) — write the prompt after item 1 ships.

### Shipped

- **Item 1 — Long Exam focus-mode trap fixed.** `useExamFocusMode` in `long-exam/page.tsx` now
  reads `phase === "running" && !submitting`, matching Challenge Quiz's `v0.131.0` guard.
  Regression test added and mutation-verified against pre-fix code, both in isolation and in the
  full suite. Checked the sibling `interview-practice/page.tsx`, which has the same bare
  `phase === "running"` expression — confirmed clean, its Leave Practice button carries no
  `disabled` state to trap behind. PR #1381 (`fix/v0.143.0-long-exam-focus-trap`), not yet merged.
- **Item 2 — regenerated Study Packs now invalidate their exam question pools.** Both the combined
  and default `STUDY_PACK`-only regeneration scopes reset and re-dispatch Long Exam and Board Exam
  pools inside the content-write transaction. Pool generation now uses `generationStatusAt` as an
  optimistic stamp so an older in-flight task cannot publish stale questions over a newer attempt.
  If that newer attempt fails after superseding an older successful result, the pool remains
  `FAILED` and self-heals through the existing refresh-on-use path. **⚠️ The stamp guard is applied
  to the `READY` write only, deliberately not to the `catch` block's `FAILED` write** — a superseded
  task that later throws (rather than completing) can still flip a good, newer `READY` pool back to
  `FAILED`, costing one wasted regeneration cycle on the pool's next use. Accepted, not fixed: the
  pool's `questions` are untouched (only the status field is stomped), and `sampleQuestions` already
  refreshes any `FAILED` pool on next use.
- **A scoped cold falsification pass on item 2 found and fixed a real deadlock risk before merge.**
  The unconditional invalidation call locked `exam_question_pool` (via `refreshPool`) BEFORE the
  regeneration's own pending `study_packs` update actually flushed — Hibernate's auto-flush is
  query-space aware and does not flush an unrelated table's pending write before a JPQL query
  against a disjoint one. Every other caller that touches both tables locks `study_packs` first,
  then `exam_question_pool` (`LongExamService.startSession` → `sampleQuestions`); this inverted
  that order, opening a genuine deadlock window against a concurrent exam start. **Verified
  empirically**, not just reasoned through: a scratch `StatementInspector`-backed test against a
  real Postgres instance reproduced the inversion (`select … for update` on the pool preceding the
  `update study_packs`), and confirmed an explicit `studyPackRepository.flush()` before the
  invalidation calls restores the correct order. Fixed by adding that flush call.
- **Flagged, not fixed: a fourth path replaces Study Pack content in place with no invalidation.**
  `AdminStudyPackTransactionHelper.regenerateOnePack` (admin-only) overwrites `summary` — a direct
  exam-pool generation input — outside `generateStudyPackFromExistingNoteAsync` entirely, so this
  release's fix does not reach it. Traced with `file:line` evidence, not a structural analogy;
  recorded as its own `ROADMAP.md` Backlog Index row rather than folded into this PR, matching how
  the `deactivateShareLinksForNote` finding was handled at kickoff.

## v0.142.0 - Awareness Before Action

**Status: Released** (kicked off 2026-09-11, signed off 2026-09-11, base branch
`releases/v0.142.0`, PRs #1378, #1379; A5 and the notification card redesign merged directly on
the release branch without a separate GitHub PR)

Theme: the notification inbox and the adopted-Review-Set update panel both went live for the first
time in `v0.134.0`–`v0.141.0` and have never been polished against real production shape. This
release fixes a bug that sits on 100% of today's live notification population, redesigns the card
for read/unread and one-tap activation, and replaces the update panel's uncapped raw diff with a
meaning-partitioned summary plus a progressive-disclosure detail surface.

Source: `docs/claude-plans/v0.142.0-adoption-and-notifications-plan.md`, written from a tightened
product spec the owner returned after a second GPT opinion. Verified against code and against a
2026-09-11 read-only production read.

### Planned Scope

- **A5 — the notification panel does not close on CTA activation (frontend, isolated bug).** The
  CTA `<Link>` at `notification-inbox.tsx:163-169` marks the notification read and never calls
  `setIsOpen(false)`. **⚠️ This fires on 100% of today's live notification population** — all 42
  production notifications share one type (`REVIEW_SET_UPDATE`), all carry a CTA, and the CTA is
  the only route in. Ship first; cheapest item in the release.
- **Workstream 1 — notification card (frontend).** Unread today is font-weight only
  (`font-medium` vs `font-semibold`, `:157`) with no background distinction — genuinely missing,
  though the owner's stated reason (no borders) is not: borders exist
  (`border-b border-border last:border-b-0`, `:154`) and are simply invisible with one notification
  on screen. Card body becomes the single tap target (mark read + close + navigate); CTA-less
  notifications mark read on tap with no separate button; dismiss stays a distinct control outside
  the tap area; add a relative timestamp (`createdAt` is already in the DTO, no backend work
  needed). Reconcile, not delete, the 5 of 20 existing tests that use the old **Mark read** button
  as their entry point.
- **Workstream 2 — Review Set update panel (frontend + one backend field).** Replace the two
  uncapped raw-diff lists with meaning-based partitioning (additions / unavailable / other
  curriculum changes — `SKIPPED_NOT_PUBLIC` currently sits, wrongly, under a heading that says "no
  action taken"), aggregate the three per-note fan-out types (`REORDERED`, `RETIRED`, `MOVED`) into
  counts, replace the raw wall with a compact summary plus a **Review update** detail surface, and
  rename **Apply additions** → **Add N new topics** (production copy check returned zero
  collisions — see Verified findings below). The topic count must come from counting `ADDED_NOTE`
  alone, not `additionsAvailable()` (`NoteCollectionService.java:3523-3529`), which also counts
  `ADDED_SUBJECT_PLAN` and would overstate the promised count.
- **Section grouping (owner decision, defaulted for kickoff): ship Option A — Subject Plan
  grouping only, no Section level.** `ReviewSetUpdateChange` carries no Section field and a
  Section is a string label on `NoteCollectionItemEntity.label`, not an entity — adding
  `sourceSectionLabel` is a real, small, zero-extra-query DTO addition (the variable is already in
  scope at the `ADDED_NOTE` construction site), but it makes this a backend release and raises the
  verification tier. Defaulting to A keeps the release frontend-only; B is a stated fast-follow if
  the owner wants the extra hierarchy level. **Revisit if the owner objects.**

### Verified findings this scope rests on (read-only, 2026-09-11)

- Production notifications: **1 distinct type** (`REVIEW_SET_UPDATE`), **42/42 with a CTA**,
  **41/42 unread**, **0 ever dismissed**, all created in one batch the day before this kickoff.
  The unread ratio means the card's unread treatment is what nearly every viewer sees, not an edge
  case.
- The rename-collision check returned **zero rows** — no notification title, body, or CTA label in
  production references "Apply additions", "upstream", or "addition".
- `docs/features/collections.md:932` and `frontend/app/collections/[id]/page.test.tsx:736,758` both
  reference "Apply additions" by exact string and must be swept in the same PR as the rename.

Anti-drift: do NOT let notification activation apply a Review Set update — activation navigates and
marks read only, the update itself stays an explicit, separate action; do NOT title-based-group the
detail surface — Subject Plan grouping uses stored `sourcePlanId`/`subjectTitle` identity, never a
note title; do NOT overwrite learner content, reset progress, or make adopted Review Sets
live-synced; do NOT change `additionsAvailable()` — it correctly gates whether the Apply action
renders at all and must stay a boolean threshold, not a display count; do NOT add pagination, a new
diff engine, or new notification infrastructure — the existing payload already carries what the
grouping/aggregation work needs under Option A; do NOT sweep `AGENTS.md`'s preamble or the Backlog
Index as a side effect of this release (both are explicitly held per
`docs/claude-plans/context-doc-token-reduction-plan.md`, items 4/6/7 — item 6 ran once this
kickoff, six rows, and is not repeated here).

Verification tier decided at kickoff: **one scoped cold agent minimum, falsification-framed** — two
PRs touch the same shared classification surface (the update panel's partitioning function feeds
both the compact summary and the detail surface), and this release changes what a user-facing claim
means (which "Changed upstream — no action taken" currently misstates for `SKIPPED_NOT_PUBLIC`) —
three releases running have been bitten by that surface-sweep gap. **Escalates to the full
three-agent test if Option B (Section grouping) is taken instead of A**, since that adds a backend
DTO change touching adopted-learner-content semantics.

Routing: **CODEX** for both workstreams — each exceeds the ≤50 LOC / 1–3 file inline threshold (A5
alone is inline-sized, and should be shipped as its own small PR ahead of the rest). Full scope,
verified findings, and the rejected alternatives are in
`docs/claude-plans/v0.142.0-adoption-and-notifications-plan.md`.

### Shipped

- **The adopted Review Set update panel now summarizes meaning instead of exposing raw diff rows.**
  Changes are partitioned into new topics, unavailable topics, and other curriculum changes;
  repeated reorder, retire, and move rows collapse to counts with full details available in the
  new **Review update** modal, grouped by Subject Plan. The main card stays compact, its headline
  reflects changes in any category, and learner-facing copy no longer says “upstream.” The action
  is now **Add N new topics**, with N derived only from `ADDED_NOTE` rows so Subject Plan summary
  rows cannot double-count it; the success toast reports the actual topic count and retains “Your
  existing work was kept.”
- **Notification rows are now coherent, single-target cards.** Unread rows have a theme-safe
  background tint, dot, and slightly stronger title; every row shows a relative timestamp. The
  title/body region is now the one primary control: a safe destination renders as a native link
  that marks read, closes the desktop dropdown or mobile sheet, and navigates, while a CTA-less or
  rejected destination renders as a mark-read-only button. The standalone **Mark read** button and
  duplicate CTA link are gone; dismiss remains an independently focusable sibling and does not
  mark read or navigate. All 21 existing tests were retained and reconciled, with seven focused
  interaction and visual guards added; all 16 changed tests failed against the pre-change row.
- **A5 — the notification panel now closes when a CTA is activated**, on both the desktop dropdown
  and the mobile sheet (both render the same `rows` block, so one fix — an added `setIsOpen(false)`
  alongside the existing `markRead` call — covers both). Of the three existing close-path tests in
  the suite (outside click, Escape, bell toggle), none covered the close path a learner actually
  takes; two tests were added (desktop and mobile), each verified to fail against the pre-fix code
  and pass against the fix. Workstream 1 subsequently moved this behavior from the deleted CTA
  link to the card body's native link and re-pointed both tests without dropping the coverage.
- **Fixed a race in `applySourceUpdate` found by this release's pre-signoff falsification pass:**
  when a second concurrent (or retried) apply request landed a placement or Subject Plan first,
  that item's `additionsResolvedByConcurrentPass` correctly discounted the backend's own
  `additionsAvailable` remaining-count, but the same item's `ReviewSetUpdateChange.applied` flag
  was never set — `appliedKeys` only recorded the branch where *this* pass created the item. The
  new frontend panel derives its own topic count from `!applied` changes, so the two disagreed:
  the button could read e.g. "Add 3 new topics" while the backend's own count said only 1 remained.
  `appliedKeys` now records the item on either branch, since it genuinely exists either way — only
  `additionsResolvedByConcurrentPass`/`additionsAvailable` distinguish which pass gets credit.
  `NoteCollectionServiceTest#sourceUpdate_concurrentApplyLandingFirstMakesTheSecondPassANoOpRatherThanADuplicateInsert`
  gained two assertions on `applied`/`additionsAvailable`, both mutation-verified to fail against
  the pre-fix code. **Scope note:** this makes v0.142.0 touch the backend; `applied`'s semantics
  changed only for the already-narrow concurrent-resolution case. Deploy-ordering: benign either
  way — an old frontend reading `applied` for its "Added"/"Would be added" label now reads a
  concurrently-resolved item as "Added" (more accurate, not less); a frontend built against this
  fix talking to a backend one deploy behind reproduces the exact bug this bullet describes, not a
  new failure mode. No stored data is affected; `appliedPlanIds`/snapshot re-baselining (governed
  by the "Applying acknowledges only what it applied" invariant, `docs/features/collections.md`)
  is untouched — that invariant constrains which plans get their source snapshot re-baselined, not
  this flag, and this fix never touches a RENAMED/REORDERED/RETIRED/MOVED change.

### Pre-signoff falsification pass

One scoped cold agent (Sonnet, no inherited context), handed 13 specific claims from the
implementing session across A5, Workstream 1, and Workstream 2, and asked to disprove each against
the actual code rather than trust any summary. **11 confirmed, 2 broken** — both fixed above before
signoff: `docs/features/collections.md:952` still said "upstream" (trivial), and the
`appliedKeys`/concurrent-pass race (the backend fix above). Full claim list and per-claim evidence
are in this session's transcript; nothing else survived the falsification attempt.

### Backlog-row closure gate

**No pre-existing Backlog Index row proposed this release's scope.** All four planned items (A5,
Workstream 1, Workstream 2, the Section-grouping decision) were scoped fresh at this kickoff from a
same-day owner conversation and a tightened spec written directly into
`docs/claude-plans/v0.142.0-adoption-and-notifications-plan.md` — searched the Backlog Index for
"notification card"/"notification inbox", "Review Set update"/"update panel", "Apply additions",
and "additionsAvailable"/double-count language; none predate this kickoff. This is a legitimate
"not found," not a miss: not every release originates from an aged Backlog row. The plan file stays
exempt as a release artifact (per the Backlog Index's stated exemption), traceable through this
section rather than a separate row.

### Checkpoint gate

**Nothing in this release shipped ahead of its own evidence.** A5 fixed a defect verified against
100% of production's live notification population; the rename's collision risk was cleared by a
read-only production check returning zero rows; the concurrency fix is a deterministic code
correction, mutation-verified, not a hypothesis awaiting outcome data. No new checkpoint owed.

**⚠️ Three checkpoints from `v0.141.0`'s section (one release above, signed off the same day) are
still standing and NOT closed by this release either:** `v0.114.0`, `v0.101.0` Slice 1, and
Learning Connections. None are this release's to close — `v0.114.0` needs a Render application log
read, and the other two are unrelated to notifications or Review Sets.

### Verification

Full backend suite (2,361 tests, incl. the Postgres/Flyway native-query integration test) green;
frontend collections + notification suites green with 6 and 16 mutation-verified new/changed tests
respectively; `tsc --noEmit` and `eslint` clean on every touched frontend file.

