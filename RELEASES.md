# RELEASES.md - NoteLib

## v0.155.0 - Say What You Checked

**Status: In Progress**

Theme: fix a real quiz-grading correctness defect a learner caught and reported, and ship the
validator that would have rejected it at generation time.

Source: `docs/claude-findings/2026-09-19-quick-review-percentage-increase-correctness-incident.md`
(full incident audit, §A–T), owner decisions locked 2026-09-21 (§Q.1).

**What happened:** a learner answered a Quick Review question correctly, was graded wrong, re-ran the
quiz picking the answer they knew was wrong to confirm the bug, then reported it. Root cause: the LLM
emitted the wrong answer *letter* while its own explanation derived the correct value — a stored MCQ's
`correctIndex` pointed at `"25%"` while its `explanation`/`workingSolution` both derived and stated
`30%`. This is a generation-inconsistency defect, not parsing, persistence, shuffling, assembly,
evaluation, or rendering — all four downstream layers were traced and confirmed correct. A deterministic
corpus scan (zero LLM calls, re-run twice) across 115,333 production questions in four stores found
**31 confirmed defects**, each independently hand-verified by re-deriving the correct answer from the
question's own stated inputs, not trusted from its own suspect explanation. Realized learner exposure is
exactly one person, two sessions — every other instance sits in never-served exam pools or the owner's
own test account.

### Planned Scope

- **Repair SQL, owner-run, independent of code (data).**
  `docs/claude-plans/2026-09-21-quiz-answer-key-repair.sql` — 39 idempotent statements across four
  sections: A (12 `study_packs` rows), B (14 `exam_question_pool` rows / 15 array-element fixes, one
  pool carries a genuine duplicate defect), C (10 `challenge_quiz_question_bank` rows, zero real
  learner exposure), D (retroactive correction of session `1e78a11d-…` and its `concept_health` row —
  kept deliberately separate per the owner's explicit "do not silently rewrite history" condition; A–C
  run independently of D). Every statement's `WHERE` clause re-asserts the current wrong value, so
  re-running the file is a safe no-op. **Claude does not execute this file** — production write-only,
  owner's to run per this repo's read-only rule.
- **H4 — internal-consistency validator at the shared generation boundary (backend, the actual fix).**
  For an MCQ whose choices are all numeric/unit literals, rejects the generated question if the keyed
  choice's text does not appear in `explanation + workingSolution` while some other choice's text does
  — narrow, deterministic, mirrors the exact detector measured against production this incident (1.3%
  flag rate on 5,443 numeric-literal-answer questions, 31/31 confirmed genuine on manual re-derivation).
  **Locked retry chain (owner decision, §Q.1 item 2): retry the rejected question once; if still
  invalid, omit it (pack generates with N−1) — never fail the whole pack.** Runs on the shared
  generation boundary every quiz mode consumes, not once per mode.
- **H1 — schema tightening (backend).** Constrains the LLM structured-output `answer` field to the
  `A`/`B`/`C`/`D`/`null` enum, closing an existing schema/Java-side divergence. Free, no behavior change
  on well-formed generations.
- **H2 — dead-code removal (backend).** Deletes `QuizValidationUtils.randomizeChoices` — reorders
  choices without remapping `correctIndex`, a real answer-identity-corruption hazard if ever wired into
  a live path, currently called only by its own test.
- **H3 — dead-code removal, Java only, no migration (backend).** Deletes `QuizQuestionEntity` /
  `QuizQuestionRepository` and their tests — zero references anywhere outside themselves, the
  `quiz_questions` table holds 0 production rows. Table drop itself is out of scope for this task (a
  DDL change, owner-execution protocol); the Codex delivery states explicitly whether it left a
  follow-up note or prepared a separate non-migration drop-table SQL artifact.
- **H3b — MATCHING block-integrity check at generation (backend).** Measured non-zero yield (4 of 50
  production MATCHING blocks, 8%, violate block-size or identical-choices rules already stated in the
  prompt as CRITICAL but not enforced on every construction path). Enforced at generation; a violation
  demotes to MCQ, mirroring the existing partial `normalizeMatchingGroups` behavior.

**Explicitly out of scope, not folded in:**
- **H5** (relax `developer.txt:105` so explanations must state the answer's value, still forbidding
  letter references) — approved by the owner (§Q.1 item 3) but ships as its own later prompt, once this
  validator's rejection-rate baseline exists in production; bundling it would make a post-ship
  rejection-rate change unattributable to either change alone.
- **H6** (replace the A/B/C/D letter contract with verbatim answer-text identity) — approved in concept
  by the owner (§Q.1 item 4) but gated on `docs/architecture/ADR-002-quiz-answer-identity-by-text.md`,
  currently **PROPOSED, not Accepted**. Not implemented until ratified.
- Structural answer-key validation (index-in-range, exactly-one-correct, duplicate choices,
  MULTI_SELECT key agreement) — the incident's own corpus scan found zero violations of any of these
  across all 115,333 production questions; explicitly not the fix, not built.
- The historical-sanitation `DETERMINISTIC_SCAN` and semantic (is-the-explanation-actually-right)
  verification — both out of scope, per the incident doc's three-tier discipline (STRUCTURAL /
  INTERNAL-CONSISTENCY / SEMANTIC, strictly separate; this release ships INTERNAL-CONSISTENCY only).

Anti-drift: H4 evaluates MCQ-with-numeric-choices only — TRUE_FALSE, MULTI_SELECT, MATCHING,
IDENTIFICATION, ENUMERATION, and prose-choice MCQ pass through unchanged; a question passing H4 is
never to be represented as "verified correct" anywhere in logs/docs/UI, only as internally consistent.
No file under `docs/architecture/ADR-001-*.md`, `docs/architecture/ADR-002-*.md`,
`developer.txt:105` (or any sibling file's equivalent line), `StudyPackGenerationContextResolver`, or
any Note-persistence path is touched by this release. `QuizItem.java`'s canonical constructor and
`resolveCorrectIndex` precedence ladder are unmodified — this release only decides whether a `QuizItem`
gets constructed, not how it resolves once constructed.

**Routing: Codex** (`docs/codex-prompts/v0.155.0-quiz-answer-key-integrity-validator.md`, Long mode) —
touches shared backend generation infrastructure across every quiz mode, per `CLAUDE.md`'s task-routing
table. **Verification tier: one scoped cold agent, falsification-framed** — trigger: a generated-content
semantics change reachable from every quiz mode. Framed against the specific claims the implementing
session makes, same pattern as this repo's established precedent.

### Shipped

- **H4 — generated MCQ answer/explanation consistency gate.**
  `QuizValidationUtils.java:187` implements the deliberately narrow numeric/unit-literal matcher with
  LaTeX-wrapper cleanup, choice-precision rounding and numeric-token boundaries; the shared conversion
  seam in `OpenAiLlmStudyPackService.java:2417` now retries one rejected question and omits a still-invalid
  replacement without failing the rest of the pack. `OpenAiLlmStudyPackServiceTest.java:959-1065` proves
  the exact reported defect, retry/omit behavior, and reach from Quick Review, Adaptive Practice,
  Challenge Quiz, Long Exam, Board Exam and Teacher Generate Quiz; `QuizValidationUtilsTest.java:199-262`
  covers the normalization and substring-collision cases. Short generated results now retain their
  actual count through `ChallengeQuizService`, `QuickReviewAdaptivePracticeService` and
  `GeneratedQuizService` instead of being converted back into whole-generation failures.
  **⚠️ Pre-commit audit mutation-verified the two safety-critical pieces of this delivery, not just
  read them:** reverting the boundary-aware match (`QuizValidationUtils.java:227-230`) to a plain
  `contains()` check killed `answerExplanationConsistency_usesNumericBoundariesForOverlappingChoices` —
  confirming the substring-collision guard the incident doc called out as "a REAL hazard" is genuinely
  load-bearing, not decorative. Restored and re-verified green. **Quota-accounting confirmed
  independently** (the Codex delivery's own output did not state this explicitly, per the prompt's
  OUTPUT item 4 requirement): `recordUsage`/`incrementUsage` calls happen once per top-level generation
  request in `StudyPackService.java`/`NoteGenerationService.java`, never per individual quiz question —
  a question-level retry or omission inside `buildQuizItemOrRetry` is invisible to quota accounting by
  construction, not merely by observed behavior.
- **H1 — structured-output answer enum.**
  `prompts/study-pack-v1/schema.json:70` constrains `answer` to `A`/`B`/`C`/`D`/`null`, matching the
  existing Java parser contract; `OpenAiLlmStudyPackServiceTest.java:948` pins the deployed schema resource.
- **H2 — hazardous dead choice randomizer removed.**
  Deleted `QuizValidationUtils.randomizeChoices`, which shuffled choices without remapping the answer,
  and its two self-only tests after confirming `backend/src` had no production caller.
- **H3 — orphaned quiz-question Java mapping removed.**
  Deleted `QuizQuestionEntity.java` and `QuizQuestionRepository.java` after confirming neither class was
  referenced outside those two files. The zero-row `quiz_questions` table remains unchanged; dropping it
  is a separate owner-run DDL follow-up, and this release includes no migration for it.
- **H3b — MATCHING block integrity enforced on every generated path.**
  `OpenAiLlmStudyPackService.java:575` now routes ungrouped MATCHING items through the existing 2–4-item,
  identical-choices normalizer instead of letting them escape as singletons. Tests at
  `OpenAiLlmStudyPackServiceTest.java:1132-1169` cover the previously escaping singleton and an oversized,
  non-identical-choice block; both demote to MCQ. **⚠️ Pre-commit audit correction, not a defect:**
  mutation-testing the new ungrouped-routing branch found the oversized/non-identical-choices test
  (`generateLongExam_demotesOversizedMatchingBlockWithDifferingChoices`) still passes with that branch
  removed — the pre-existing `resolveInvalidMatchingGroupReason` size/choice check already caught that
  case whenever a block was properly grouped; only the ungrouped-singleton escape was a genuine gap this
  diff closes. The test is a correct regression lock, but only the singleton fix is new behavior — the
  incident's reported size-6 violation was already covered by code that predates this release.

### Known Limitations

- H4 has near-zero recall for prose-answer MCQs while current prompts avoid restating the answer value.
  H5 remains a separately approved prompt change so this release first establishes an attributable
  production rejection-rate baseline. Passing H4 means only internally consistent, never semantically
  verified; H6 and the separate single-best-answer Question Quality audit remain deferred. All three
  (H5, H6, the Question Quality audit) now have their own Backlog Index rows in `ROADMAP.md`, added at
  this commit since the Codex delivery's own output explicitly deferred that question to this session.

## v0.154.0 - Closing the Loop

**Status: Released** (signed off 2026-09-18)

Theme: close out three independently-verified, gate-true Backlog Index items — none gated on an owner
action or a production read, none sharing a file or a shared method with any other, each anchored to
current code before being scoped rather than trusted from its row's prose.

**⚠️ CORRECTED AT KICKOFF, BEFORE ANY CODE WAS WRITTEN: a fourth item, "health-check-on-Hikari-pool
decoupling," was scoped in by mistake and dropped.** The liveness/readiness split it proposed to build
already shipped in `v0.119.1` PR #1297 (`management.health.group.liveness.include: livenessState`,
excluding `db`, in `application.yaml`) — the pre-scoping check only grepped for a custom
`HealthIndicator` Java class and missed that the real fix is declarative YAML config, not a class. The
only piece still open is the Backlog Index's own existing row for it: an **owner action**, repointing
Render's `healthCheckPath` from `/api/actuator/health` to `/api/actuator/health/liveness` in the
dashboard — confirmed still unpointed via a live read-only Render API call at this kickoff
(2026-09-18). Not re-added to this release's code scope; it stays an owner action, same class as A1.

### Planned Scope

- **`course_programs.is_active` write path (backend + Admin frontend).** Confirmed dead column:
  `CourseProgramCatalogRepository.java` reads `is_active` in several places but no code anywhere in
  `backend/src/main/java` ever writes it; `CourseProgramCatalogService`/`Controller` have zero
  references. Adds the missing write path so Admin can actually deactivate a catalog program — the
  prerequisite for retiring the two legacy fused rows (`Nursing · Medicine`, `Nursing · Pharmacy`).
- **Recovery for `generation_enqueued_at IS NULL` notes stranded in `GENERATING` (backend).**
  `GenerationRecoveryService.java:107` explicitly skips this row class and logs "leaving them
  untouched"; every other stale `GENERATING` row is swept back within ~2h10m
  (`noteBoundMinutes` default 120 plus sweep cadence), but the automated sweep never touches a row
  missing this timestamp. **⚠️ Framing corrected at kickoff:** a live read-only production query
  (2026-09-18) found **zero** notes currently `GENERATING`, let alone with a null clock — this is a
  **latent structural gap in the automated sweep**, not an active stuck-note population, and (found
  mid-implementation, not at kickoff) **not a user-visible dead end either**: `NoteController`'s
  `POST /notes/{id}/recover-stranded-generation` already gives the note owner a tested, self-service
  recovery path for exactly this row class, using `updatedAt` as a fallback clock bounded by the same
  `noteBoundMinutes`. This item makes that same rule fire automatically as well as on request, rather
  than inventing new recovery logic or fixing a previously-unrecoverable state. Every current write path
  that sets `NoteStatus.GENERATING` (`StudyPackService.java:224-225`, `:326-327`) also sets
  `generationEnqueuedAt` atomically in the same method, and `V118__generation_recovery_clocks.sql`
  already one-time-backfilled any pre-existing null rows at its own deploy, so this is prospective,
  defense-in-depth coverage for a future non-atomic writer — not a fix for a live incident.
- **Topic-note generation passes `subject` into the LLM context (backend + frontend).**
  `GenerateNoteFromTopicRequest.java` carries `topic`, `courseProgramIds`/`courseProgramText` and
  `domainContext`, but no `subject` — `NoteGenerationService` builds context with `subject = null`, so a
  note authored under a specific subject via "Create from topic" never tells the model that. Degrades
  quality rather than failing requests. Must preserve ADR-001's hierarchy: Domain Context is the sole
  authoritative domain constraint, Subject only narrows within it.

Anti-drift: no bulk `is_active` editor or catalog deletion, and no `course_programs.program_family_id`
write path revival; the `GENERATING`-recovery fix extends the existing sweep's row selection, it does
not change `noteBoundMinutes` or the sweep cadence; the topic-note `subject` change does not let Subject
override or compete with Domain Context per ADR-001, and does not touch `courseProgramText`/
`domainContext` resolution.

**Routing:** Claude Code inline for the `GENERATING`-recovery fix (isolated root cause, 1-3 files);
Codex for the `is_active` write path and the topic-note `subject` context gap (new endpoint/DTO +
multi-surface frontend each). **Verification tier:** each item's own tier as scoped (direct verification
for the inline item, normal `/audit-diff` for the two Codex items). **⚠️ Escalated at signoff, past the
whole-release `advisor()` summary originally scoped here:** all three items independently tripped
CLAUDE.md's "delivery introduced a defect the same session then fixed" trigger (item 1's lost-update
defect, item 2's unbounded-recovery regression, item 3's stale-closure bug — each caught and fixed before
its own commit). A repeated same-session-defect pattern across every item in a release is a stronger
blind-spot signal than the rule anticipates from a single occurrence, so this release ran one scoped cold
agent, falsification-framed against the specific claims made in all three fixes, instead of the single
`advisor()` summary. **Result: nothing disproven** — all four falsifiable claims per item held under
direct code inspection (the lost-update fix, the recovery bound, the dependency-array fix, and their
respective transactional/normalization/negative-case guarantees), and no cross-item coupling was found.
**⚠️ One imprecision corrected, not a defect:** this section's original "no shared files or methods"
phrasing was wrong on the first half — `frontend/lib/api.ts` is touched by both item 1
(`updateCourseProgram`) and item 3 (`generateNoteFromTopic`), at non-overlapping functions with no logic
interaction. "No shared methods" is what actually holds and is what the no-full-pressure-test gate
depends on.

Carried forward from `v0.153.0`'s signoff, not this release's problem to solve: A1 (owner action —
enabling Render's own per-request logging) still not enabled as of `v0.153.0` signoff; the Leg A2
saturation detector's registry has no coverage of non-request threads (Known Limitation, not re-scoped
here). Also carried forward, from this release's own kickoff correction above: the Render
`healthCheckPath` repoint (owner action).

### Shipped

- **Admin write path for `course_programs.is_active`.** The existing catalog PATCH accepts an optional
  nullable `isActive` field and writes it transactionally through
  `CourseProgramCatalogService.java:132-140` / `CourseProgramCatalogRepository.java:63,113-115`;
  omission leaves the lifecycle flag unchanged, and an `isActive`-only PATCH also leaves family
  memberships untouched. The Course / Programs view initializes an Active checkbox from the edited
  row and marks inactive rows in both rendered layouts (`admin-course-program-catalog-section.tsx`).
  **⚠️ Pre-commit `advisor()` review found and fixed a real lost-update defect in the Codex delivery,
  the same class `v0.152.0`'s cold agent found on the sibling family-rename modal:** the save path
  originally re-sent `programFamilyIds` from its load-time snapshot on every save, including an
  Active-only toggle — so an admin flipping Active while a concurrent admin had just changed that
  program's family memberships would silently overwrite the concurrent edit. Fixed by mirroring
  `AdminProgramFamiliesSection`'s `membershipDirty` pattern: `programFamilyIds` is now omitted from
  the request entirely unless `CatalogMultiSelect` was actually touched this edit. Two guard tests
  added confirming an Active-only save carries no `programFamilyIds` key. The shared catalog read
  stays unfiltered, and the existing Applicable Programs active-only behavior is unchanged.
  **⚠️ Deploy-ordering statement, per CLAUDE.md's rule for a form whose omission-meaning changed:**
  this PATCH's frontend and backend must deploy together, and the safe direction is
  **backend-first**. If Render deploys the new `isActive`-aware backend before Vercel deploys the new
  frontend, the old frontend's existing family-save calls are unaffected (it always sent
  `programFamilyIds` and never sends `isActive`, both still handled). If Vercel deploys the new
  frontend first, the Active checkbox reaches users before the backend accepts `isActive` — Jackson
  silently drops the unknown field, the PATCH still 200s, and the toggle appears to save but has no
  effect until the backend catches up. Not a data-loss risk either order, but backend-first avoids a
  silently-inert control window. Coverage includes real JSON PATCH binding plus a follow-up catalog
  GET, service omission/application cases, the JDBC `UPDATE` executed and read back on Testcontainers
  PostgreSQL, request-body included/omitted cases in `api-course-program-catalog.test.ts`, and
  modal/desktop/mobile component cases including the two lost-update guard tests. Backend 2435/2435;
  frontend 2468/2469 with one pre-existing skipped test; frontend lint 0 errors (20 pre-existing
  warnings, all pre-existing and unrelated to this change).
- **Recovery for `generation_enqueued_at IS NULL` notes stranded in `GENERATING`.**
  `NoteRepository.findGeneratingIdsWithNullEnqueuedAt` (new, bounded on `updatedAt < cutoff`, same
  `noteBoundMinutes`) feeds a new `GenerationRecoveryRowWriter.recoverNoteWithMissingEnqueuedAt(UUID,
  OffsetDateTime)`, which applies the identical `updatedAt`-fallback rule
  `NoteController.recoverStrandedGeneration` already used for self-service recovery of this row class —
  now enforced by the scheduled sweep too. `GenerationRecoveryService.recoverStaleNotes` runs this
  alongside the existing timed-clock sweep and combines both results; the original
  `countByStatusAndGenerationEnqueuedAtIsNull` warning log is kept (uncapped by batch size) so the
  anomaly signal survives exactly as before — this item makes the row recover as well as get warned
  about, it does not remove the warning. **⚠️ First implementation was a live regression risk, caught by
  `advisor()` before commit:** it recovered every null-clock row unconditionally, with no age bound —
  unlike the self-service endpoint's `updatedAt` check, so a future non-atomic writer's in-flight
  generation would have been killed by the very next 10-minute sweep. Corrected to require
  `updatedAt.isBefore(cutoff)`, matching the endpoint's own rule; the fixture-driven test that first
  covered this (`note(null)` with no `updatedAt`) was itself rebuilt to a realistic row plus an added
  negative case (`updatedAt` 5 minutes old → left alone) that would have failed the original code.
  `docs/features/study-pack-generation.md` corrected to describe the sweep and the endpoint as two
  entry points to the same rule, not "left untouched" plus a separate manual-only path. Backend
  2434/2434 (full suite, including the real-PostgreSQL native-query harness).
- **Topic-note generation now carries the editor's Subject into generation context.**
  `GenerateNoteFromTopicRequest.java:11-35` accepts the optional, 64-character-bounded field while
  keeping the existing three- and four-argument Java constructors source-compatible (both are live:
  the three-arg form is still used by `StudyPackService.java:332`, the four-arg form by
  `NoteBulkGenerationService.java:311`); `NoteGenerationService.java:118-152` normalizes it once with
  `SubjectNormalizationUtils` and passes it through both the unchanged curator and learner resolver
  branches. The positional frontend API appends `subject` and omits blank values
  (`frontend/lib/api.ts:3603-3631`), while every note-editor call variant supplies the already-collected
  draft value (`note-editor-page-client.tsx:1143-1180`). Onboarding's separate two-argument call is
  unchanged. **⚠️ Pre-commit `npm run lint` found a real stale-closure bug in the Codex delivery:** the
  `useCallback` wrapping the generate-from-topic handler read `draft.subject` (via `resolvedSubject`) but
  omitted it from its dependency array, so typing Subject *after* Topic — a plausible order — would
  silently generate with the stale (often empty) subject captured at the callback's last recreation.
  Codex's own new test happened to type Subject before Topic, which recreates the callback via the
  already-listed `normalizedGenerateTopic` dependency and masked the gap. Fixed by adding `draft.subject`
  to the dependency array; a new regression test
  (`"uses the latest changed Subject even when it's typed after the Topic"`) exercises the reversed,
  bug-exposing order and was mutation-verified — confirmed failing against the pre-fix code, passing
  after. **No deploy-ordering statement needed:** `subject` is purely additive to an existing endpoint (no
  form's meaning changed, no field became required), and either deploy-skew direction only degrades
  generation quality rather than breaking a request. Coverage includes resolver-call and final context
  assertions for both branches, real `MockMvc` JSON binding plus over-length rejection before generation,
  the real frontend request body with present/blank subjects, and component calls with a selected,
  absent, or Subject-typed-after-Topic draft. Backend 2442/2442 (full suite, including the
  real-PostgreSQL native-query harness); frontend 2472/2473 with one pre-existing skipped test; frontend
  lint 0 errors (20 pre-existing warnings, back to baseline after the fix — 21 before it).

## v0.153.0 - The Missing Telemetry

**Status: Released** (signed off 2026-09-18)

Theme: stop re-investigating the same unidentified production outage a fifth time, and ship the one
thing that would actually answer it — the diagnostic instrumentation this recurring failure has been
missing across all four occurrences so far. **Folded in 2026-09-17, mid-cycle, while this release was
still open: a second, unrelated fix (F1/F2 below) for a separate production-reliability gap found while
auditing an overdue product checkpoint** — a metadata field (Authored Depth) whose backlog was
discovered to be 3.5× larger than believed and actively growing. The two problems share no code, no
files, and no root cause; they are bundled here only because `v0.153.0` was still open when the second
one was scoped, per an explicit owner call to avoid opening a second release branch mid-cycle.
**⚠️ Verification-tier consequence of folding a second, unrelated item into an open release, stated per
CLAUDE.md's own rule:** this release is now four items (Leg A2, Leg B, F1, F2) across two unrelated
problem domains instead of two. Per-item tiers stay as declared for each (Leg A2 keeps its cold-agent
falsification pass; Leg B, F1 and F2 each get one `advisor()` call) — no item's own tier moves — but a
whole-release `advisor()` summary at signoff must now explicitly check the two halves don't interact
(they touch disjoint files: `backend/.../hikari`/`ThreadLocal` filter/`application.yaml` for the pool
work vs. `NoteBulkGenerationService`/`private-note-detail-page-client.tsx`/`bulk-generation-page-client.tsx`/
admin Applicable Programs for the depth work), and the per-PR `/audit-diff` stays scoped to whichever
half a given PR actually touches rather than being asked to reason about both at once.

Source: `docs/claude-plans/2026-09-17-pool-exhaustion-instrumentation-fix-plan.md` (Prod Investigator
session, written on request from a peer session relaying the owner's report that prod goes down almost
daily), built on `docs/claude-findings/2026-09-10-prod-pool-exhaustion-trigger-unresolved.md` (§11 adds
today's occurrence). **The `[CHECKPOINT — due 2026-09-17]` in `ROADMAP.md`'s Backlog Index fired at
kickoff:** today's incident (05:56:29–05:58:46 UTC, ~90s impact, already recovered) is a **fourth**
confirmed occurrence of the identical signature (2026-09-04, 2026-09-05, 2026-09-10, now 2026-09-17) —
HikariCP pool exhaustion (`active=20/20`) causes `DataSourceHealthIndicator` to starve on the same pool,
so the platform restarts an instance whose only problem was that it was busy. Every discriminating check
from the prior three investigations repeats identically: no connection leak (no hold ≥60s), no OOM, no
recent deploy, the database itself near-idle, nothing scheduled. Per the checkpoint's own stated kill
criterion, this release does **not** attempt a fifth root-cause hunt — three priors plus a cold-agent
falsification pass already narrowed the mechanism as far as existing telemetry allows (a non-DB,
non-CPU blocking wait under 60 seconds, under OSIV). **The owner asked directly whether this could be a
docker-compose / app-config issue: ruled out.** `docker-compose.yml` is local-dev-only (hardcoded
`localhost` values) and is never part of the deploy path — Render builds and runs `backend/Dockerfile`
directly under its own orchestration.

### Planned Scope

- **Leg A2 — Hikari-saturation-triggered diagnostic logging (backend).** When the pool is saturated
  (`activeConnections >= maximumPoolSize` with threads waiting, sustained), log which request paths are
  in flight at that moment — captured via a cross-thread `ConcurrentHashMap<Thread, InFlightRequest>`
  registry set in a servlet filter at request entry (alongside where `RequestIdFilter` already runs),
  polled against `HikariPoolMXBean` on a short interval. **This is the one thing that would have answered every one of
  the four incidents on the spot**, instead of leaving "narrowed, not identified" as the outcome each
  time. Scope is exactly detect-saturation-and-log-in-flight-paths — explicitly not a general APM
  integration.
- **Leg B — close the structural Tomcat/Hikari mismatch (backend, config).** `server.tomcat.threads.max`
  (currently 25, `application.yaml`) exceeds `spring.datasource.hikari.maximum-pool-size` (20), so under
  `spring.jpa.open-in-view: true` roughly 21 concurrent requests alone can exhaust the pool regardless of
  query speed. Lower `threads.max` to at or below 20, not raise the pool (raising it is an explicit
  non-fix — already tried once, 10→20 after 2026-09-04, and the identical failure recurred three more
  times at 20 since). This does not identify or fix whatever is actually holding connections for tens of
  seconds; it closes a different, independently-real exposure. **⚠️ Both values are
  `${ENV_VAR:default}` — `${SERVER_TOMCAT_THREADS_MAX:25}` and `${DB_POOL_MAX_SIZE:20}` — and Render
  environment variables cannot be read with any tool available to Claude (the only such tool is a write,
  which is the owner's). Before this ships, the owner must confirm on the Render dashboard's Environment
  tab whether either variable is set explicitly.** If `SERVER_TOMCAT_THREADS_MAX` is overridden, editing
  the YAML default is a silent no-op in production — the fix is then an owner-run env-var change, not a
  code diff, and the release notes must say which one actually happened.
- **A1 — Render platform request logging (owner action, not a code change).** Confirm in the Render
  dashboard whether per-request logging (path, status, duration) can be enabled for this service, and if
  so, enable it. Deploy/env/plan-tier actions are owner-only per standing rule; not part of this
  release's diff.
- **F1 — publication-time Authored Depth warning (frontend only).** Per
  `docs/claude-plans/authored-depth-legacy-backfill-audit-and-plan.md` (§F), a fresh audit found that a
  curator-owned public note with no Authored Depth is not merely unfilterable — it generates a less
  precisely calibrated Study Pack (no curriculum floor, ambiguous subject guidance). 282 such notes
  exist today, 173 created in the 30 days since `v0.83.0` shipped the Public Library `?level=` filter,
  entirely via the bulk-generate `makePublic` path, which never inspects depth. Add a non-blocking
  warning line — *"this note will not appear under any Authored Depth filter"* — to the existing *Make
  public* confirmation dialog (`private-note-detail-page-client.tsx`) and to the Bulk Generate form when
  `makePublic` is checked with no depth selected (`bulk-generation-page-client.tsx`). Publication still
  proceeds either way; this is copy plus one conditional in each of two existing components, no API
  change, no migration.
- **F2 — admin missing-depth count (multi-system).** Add a *missing Authored Depth* filter/count to the
  existing curator-scoped `/admin/course-programs` Applicable Programs surface
  (`AdminNoteApplicableProgramsController` / `admin-applicable-programs-section.tsx`), which today lists
  a curator's own notes but does not even carry `learnerLevel` in its response DTO. No new dashboard, no
  new route, no notification system — one column and one filter on a page that already exists for
  exactly this class of metadata repair.

Anti-drift, carried forward from the plan and the source finding, do NOT re-propose: raising
`maximum-pool-size` further (duration-bound holds, not throughput-bound — a bigger pool buys time
proportional to nothing); touching `spring.jpa.open-in-view` (real blast radius, needs a staging run
first, this incident does not change that calculus); adding PgBouncer (addresses too-many-clients, not
connections-held-too-long); chasing the "synchronous external call" lead from the finding's §11 without
new evidence (opened, not confirmed — Leg A2 is what would actually confirm or kill it on the next
occurrence). Leg A2 and Leg B are independent — neither blocks the other.

**Anti-drift for F1/F2, locked by the owner's decision and confirmed against current code by the audit
— do NOT re-propose:** inferring Authored Depth from Course/Program (different semantic axis;
`ADR-001:62,68,485`); adding a learner-facing "Unclassified" depth chip (describes curator metadata
quality, not a learner's desired level; current behavior — NULL-depth notes fully visible unfiltered,
excluded only by an explicit depth chip — already matches the requirement and needs no change); a hard
publication-time requirement as the first move (`NoteBulkGenerationService.java:336-346` swallows a
publish exception into `log.warn`, so a hard throw there would make a `makePublic` batch silently fail N
notes with a success receipt — F3, a hard requirement, is explicitly deferred pending a 30-day post-F1
inflow re-read); a bulk Authored Depth editor (no bulk write path exists for `learnerLevel` today — only
single-note create/update/copy touch it — and 80-plus one-time dropdown edits cost less than the
endpoint a bulk tool would need); retiring Public Library depth-based discovery (zero instrumentation
exists on that surface, so this checkpoint has no learner-demand evidence either way). Full audit:
`docs/claude-plans/authored-depth-legacy-backfill-audit-and-plan.md`.

Pre-declared guards (do not accept a diff without these — a detector that doesn't provably fire under
load, or that false-positives under ordinary load, is the same silent-no-op class this repo has shipped
twice before): (1) a test that actually saturates a small test Hikari pool and asserts the saturation
log line fires and names the blocking path, not just that the detector compiles; (2) a test asserting
the detector does **not** fire under ordinary, non-saturated concurrent load; (3) for Leg B, confirm the
application context still starts and a burst of ~20 concurrent requests queues at the Tomcat acceptor
rather than erroring, after lowering `threads.max`; (4) for F1, a test that actually renders each dialog
with the branch condition met (no depth + `makePublic`/publish) and asserts the warning copy appears —
not just that the component compiles; (5) for F2, if it adds any endpoint, one real `MockMvc` request
test with `.contentType(MediaType.APPLICATION_JSON)` per CLAUDE.md's non-negotiable rule for every new
endpoint.

**Routing: Codex** for Leg A2 (backend service + filter + config, anti-drift care against scope-creeping
into a general APM layer) and for **F2** (backend DTO + service filter + frontend section, multi-system,
via `docs/skills/codex-prompt-generator.md` — scope locked to one column + one filter on the existing
admin surface, no new dashboard/route/notification system). **Routing: Claude Code inline** for Leg B
(one YAML line, existing pattern, clear regression guard) and for **F1** (two existing components,
copy + one conditional each, well under ~50 LOC, no new infrastructure). **Verification tier: Leg A2 —
one scoped cold agent, falsification-framed** (recurring four-incident production-reliability history;
no auth/cross-user/money-semantics trigger fires on its own, but the incident history is reason enough
per the plan's own recommendation) — hand it the plan plus finding §11 and ask it to disprove that the
detector actually fires under load and doesn't false-positive under normal traffic. **Leg B, F1 and
F2 — one `advisor()` call each** on their diffs; none moves an authorization boundary, changes
money/quota/production-data semantics, or shares a method with another PR in this release. **No full
three-agent pressure test for either half** — neither meets any of that tier's triggers, and defaulting
to the heaviest option regardless is itself the error CLAUDE.md names.

**Backlog Index obligations, this release's own signoff:** (1) update the
`[CHECKPOINT — due 2026-09-17]` pool-exhaustion row — its kill criterion fired, scope changes from
"identify the trigger" to "ship the instrumentation that would identify it," not resolved until Leg A2
has shipped and fired at least once (in the guard test per above — a fifth production occurrence is not
something to wait for). If Leg A2 ever does capture a real trigger on a future occurrence, that is a
new, separate findings file, not a retrofit into the "trigger unresolved" title. (2) The Authored Depth
row (`ROADMAP.md`, `v0.83.0 — will curators actually classify…`) needs a new
`[CHECKPOINT — due <F1 deploy + 14 days>]` added for the manual cleanup's completion re-read (kill
criterion, stated now: if the checkpoint query still returns more than 10 unclassified notes at that
read, escalate to tooling per the audit's §H re-evaluation, not a third extension) — the exact date
depends on when F1 actually deploys, so it cannot be written until then.

### Shipped

- **Hikari saturation request-path diagnostics (Leg A2).** A servlet filter now keeps a cleanup-safe, thread-keyed snapshot of request paths currently in flight. A fixed-delay detector reads the live Hikari MXBean every two seconds and, after two consecutive samples with `activeConnections >= maximumPoolSize` and waiters present, logs the pool counts and every request in flight at saturation. It emits once per saturation episode, rearms after recovery, and disables safely for a non-Hikari datasource. Real-pool guards exhaust a two-connection Hikari pool and prove the warning names the tracked path, while false-positive and throwing-filter guards prove a single blip, ordinary load, and request failures do not leave misleading telemetry. `InFlightRequestTrackingFilter` is now pinned `@Order(HIGHEST_PRECEDENCE + 1)`, ahead of the Spring Security chain, so a connection held inside `JwtAuthenticationFilter`'s per-request user lookup is visible to the snapshot rather than silently excluded (found by the release-wide Opus falsification pass below; the original filter order was Spring's default `LOWEST_PRECEDENCE`, which placed it after security).
- **Curator-owned Authored Depth cleanup queue (F2).** The existing Admin Applicable Programs table now displays each owned note's Authored Depth and can filter to notes where it is missing. The filter preserves the page's requester-owner scope, visibility-agnostic population, pagination, and `updatedAt DESC` order; depth remains editable only from the existing per-note editor.
- **⚠️ Leg B — code half only. NOT effective in production yet.** `server.tomcat.threads.max`'s YAML default lowered 25→20→**18** (see the correction below for why 20 wasn't the final value), and `TomcatThreadPoolHikariAlignmentTest` pins `threads.max < hikari.maximum-pool-size` — a **strict** inequality, algebraically (re-read from both files every run, not a hardcoded pair of numbers) — so the two settings can't silently drift apart again. **The owner confirmed on the Render dashboard (2026-09-17) that `SERVER_TOMCAT_THREADS_MAX=25` is set explicitly there — this overrides the YAML default entirely, so production is still running at 25 today and this fix does nothing until the owner changes or removes that variable.** Recommendation: **delete** the Render env var rather than set it to a number, since deleting it also removes the shadowing that made this a live question — but it's the owner's call. The pre-declared guard "confirm a burst of ~20 concurrent requests queues at the Tomcat acceptor rather than erroring" is intentionally NOT covered by a bespoke test: that behavior is standard Apache Tomcat NIO-connector queueing, not code this repo owns, and building the codebase's first full-embedded-server concurrency test to re-prove a 20-year-old servlet-container feature would exceed this leg's own declared `advisor()`-only verification tier. Stated here explicitly rather than silently assumed.
- **⚠️ Correction (Opus falsification pass) + final value decision: 20 was tried first and found insufficient; shipped at 18.** At the originally-shipped equality (20 == 20), this closed only the OVERFLOW exposure (a 21st–25th admitted request exhausting the pool by itself) — it did NOT close the STARVATION mechanism actually behind all four outages. `spring.jpa.open-in-view` is unset, so Boot's default (`true`) applies and a connection is held for a request's whole lifecycle; 20 fully-concurrent requests alone could still consume every connection and leave zero for `DataSourceHealthIndicator`, reproducing the exact `active=20/20` signature. The test's own docstring previously overclaimed "close this specific structural exposure" without that qualification — corrected. **Owner decision after reviewing the tradeoff explicitly (queueing at the Tomcat acceptor under a 2-thread-narrower ceiling vs. reserved health-check/scheduled-job headroom): lower to 18**, reserving 2 connections of headroom. This meaningfully reduces the odds of recurrence; it is **not** an absolute guarantee, since the registry/pool-coverage Known Limitation below (2 executors + 16 `@Scheduled` jobs drawing on the same pool, unbounded by `threads.max`) means a health check can still theoretically lose a race against those. `TomcatThreadPoolHikariAlignmentTest` and `application.yaml`'s comment both updated to the strict-inequality framing.
- **Publication-time Authored Depth warning (F1).** A non-blocking warning now appears in both real publish surfaces that reach `performVisibilityUpdate("PUBLIC")` for an individual note — the "Make this note public?" confirmation and the "This note is private" → "Publish & Share Link" dialog (`private-note-detail-page-client.tsx`) — plus the Bulk Generate form's Public toggle when no Authored Depth is selected (`bulk-generation-page-client.tsx`). Publication always proceeds either way; the warning only tells the curator the note will not surface under any Authored Depth filter until one is set. Scope grew by one dialog beyond the original two named surfaces: the private-share modal's own "Publish & Share Link" button calls the identical publish path and was silently missing the warning otherwise. Guarded by real-render tests asserting the warning appears exactly when depth is unset and disappears once it is set, across all three surfaces.
- **Pre-signoff Opus falsification pass, all four shipped items, all 19 pre-declared claims CONFIRMED.** Escalated past the pre-declared per-item tiers (Leg A2's own scoped Sonnet cold agent; one `advisor()` call each for Leg B/F1/F2) at the owner's explicit request given the production-reliability stakes — one `model: opus` cold agent, no inherited context, falsification-framed across the whole release rather than three separate agents. Verified empirically throughout: a genuinely exhausted real Hikari pool, a mutated YAML value that correctly failed the alignment test, a full Spring context boot, and real `MockMvc` requests — not read-only inspection. No code defect found; every claim about detection reliability, false-positive avoidance, cleanup-on-throw, fail-safe behavior, publish-path correctness, and query scoping/visibility/serialization held. It did surface written claims that outran what the code/tests actually proved, all fixed in this same release rather than carried forward silently: the `@Order` fix and Leg B docstring correction above, plus two new tests (`rearmsAfterRecovery`, `doesNotLogUnderOrdinaryConcurrentLoad` — the latter closing this release's own pre-declared "does not false-positive under concurrent load" guard, which the original suite tested single-threaded only) added to `PoolSaturationDetectorTest`. See Known limitations for the one gap left open rather than fixed: the saturation detector's registry has no coverage of non-request threads.

### Known limitations

- **RESOLVED (Opus falsification pass, this cycle).** `InFlightRequestTrackingFilter` previously had no explicit `@Order` and ran after Spring Security (measured: `LOWEST_PRECEDENCE` vs. security's `-100`), so a connection held inside `JwtAuthenticationFilter`'s DB lookup was invisible to the snapshot — a present gap, not the hypothetical one originally recorded here ("correct today ... would silently break if a future filter changed that assumption" was itself inaccurate). Fixed: `@Order(Ordered.HIGHEST_PRECEDENCE + 1)`, mirroring `RequestIdFilter`.
- **The saturation registry has no coverage of non-request threads — the larger of the two remaining gaps, not fixed here.** `llmParallelTaskExecutor` / `studyPackGenerationTaskExecutor` (used by `LongExamService`, `ExamQuestionPoolService`, `AdminStudyPackService`, `OfficialChallengeQuizTemplateService`) and 16 `@Scheduled` jobs never pass through the servlet filter that populates the registry. A connection held by a generation task across a slow OpenAI call — precisely the unconfirmed "synchronous external call" lead this release's source finding carries — produces `requests in flight at saturation=[]`, which is ambiguous between "nothing was in flight" and "the holder was never eligible for the registry." Not fixed in this release: closing it means deciding whether non-request threads should register themselves too, a larger design question than this release's scope. Read the empty-list case with this caveat during incident five.
- **`PoolSaturationDetector.poll()` shares Spring's default single-threaded scheduler with 16 other `@Scheduled` jobs, several DB-bound** (`GenerationRecoveryJob` every 10 min, `BulkGenerationResultCleanupJob` and `NotificationCleanupJob` hourly, plus the rate-limit purges). During saturation, any of those blocking on a connection up to `connection-timeout: 5000` stalls the 2-second poll for that duration — a latent detection-latency risk, count corrected from the original "several other low-frequency jobs" to the actual 16. `spring.task.scheduling.pool.size: 2` would remove it; not changed here to keep this leg's diff minimal.
- **`sanitize()` on the logged request path strips only `\n`/`\r`, with no length bound or control-character stripping beyond that.** Low severity, since it fires only during genuine saturation on a codebase with no existing log-injection-hardening convention to hold it against. Unchanged from the original finding.
- **`InFlightRequestTrackingFilterTest` drives the filter directly (`filter.doFilter(...)`) rather than asserting it is actually registered in the chain or at what position.** Passes by construction regardless of registration — the same shape as the `v0.119.0` `Content-Type` defect class CLAUDE.md names. The `@Order` fix above was verified by booting the real Spring context during the falsification pass, not by this unit test; no regression guard exists for the ordering itself. Flagged, not fixed — would need a `@SpringBootTest` asserting filter registration order, judged not worth the cost for a one-line annotation.
- **A1 (owner action, Render per-request logging) — checked at signoff, confirmed NOT enabled, still open.** The owner reported having heard it was on by default; verified otherwise via a read-only `list_log_label_values` query against the production service's logs (`type` label returns only `["app", "build"]` across the prior ~28 hours — no `request` type exists at all), plus a direct spot-check of a live hour showing only Spring Boot application/job log lines, no per-request path/status/duration entries. Enabling it (a paid add-on or plan-tier feature on Render, not a code change) remains the owner's own action, not done as of this signoff.

## v0.152.0 - The Missing Half of v0.150.0

**Status: Released** (signed off 2026-09-17)

Theme: give the many-to-many Program Family architecture (v0.150.0) the curator UX it needed to
actually get finished — family-first Admin management, one canonical catalog-create modal, and an
additive backfill of the approved initial membership matrix.

Source: `docs/claude-plans/program-family-catalog-management-ux-overhaul-plan.md` (FINAL, owner-approved,
subagent audit + owner-tightening pass; untracked on disk, indexed in `ROADMAP.md`'s Backlog Index at
this kickoff) and its companion Codex prompt `docs/codex-prompts/v0.152.0-program-family-catalog-management.md`
(Long mode, Slices 1-3 only). **Why now, from production data, not a redesign impulse:** Engineering
(18/18) and Education (8/8) were fully populated the day `v0.150.0` shipped; three weeks and one release
later, Health Sciences and Computing & Technology are still at zero members, Accounting 2/5, Built
Environment & Design 1/8. The many-to-many data model did not fail — the one-program-at-a-time admin
workflow (open a program, pick its one family from a `<select multiple>`, repeat) made finishing the
backfill through it tedious enough that it didn't get finished. This release is the missing curator UX,
not a data-model change.

### Planned Scope

- **Slice 1 — Backend catalog contracts + data (backend).** `POST /course-program-catalog/families`
  gains optional `programIds` (atomic create-with-members, mirroring the existing program-side
  `create()` shape); new `PATCH /course-program-catalog/families/{id}` (rename + family-side membership
  replace, one transaction); the family duplicate-name predicate is weakened relative to the program
  one (`lower(trim(name))` vs. `regexp_replace`-whitespace-collapsing) and gets aligned; rename adds
  `id <> ?` self-exclusion so renaming a family to a case/whitespace variant of its own name doesn't
  reject itself as a conflict with itself. New migration `V147__program_family_initial_membership.sql`
  — purely additive, exact-name inner joins over a locked 50-pair matrix, `ON CONFLICT DO NOTHING`, no
  `RAISE`, no fuzzy matching, does **not** write the vestigial `course_programs.program_family_id`.
  Production: 29 existing pairs untouched, 21 new rows inserted (Health Sciences 5, Accounting 3,
  Computing & Technology 6, Built Environment & Design 7), `course_program_family` goes 29→50.
- **Slice 2 — Shared catalog selection + creation UX (frontend).** New `CatalogMultiSelect`
  (`components/ui/catalog-multi-select.tsx`) — a searchable, client-side-filtered checkbox picker with
  a `selectedSummary: "count" | "chips"` density prop, replacing both remaining raw `<select multiple>`
  instances in the codebase. New `CourseProgramCreateModal` extraction, mounted from both Admin and the
  three authorized Note-authoring surfaces, collapsing today's two divergent create forms (Admin's
  weaker single-family form vs. the note-authoring modal's already-multi-family one) into one component,
  one contract, one validation path.
- **Slice 3 — Family-first Admin IA (frontend).** `/admin/course-programs` gains a two-tab switch
  (`?view=families|programs`, URL-reflected), Program Families as the default/primary tab (a table:
  name, member count, Edit — zero-member families included, not `is_active`-filtered), Course / Programs
  demoted to the inverse-convenience secondary tab. Removes the permanently-visible inline "New Program
  Family" box and inline create grid in favor of header `+` buttons opening modals.
- **Slice 4 — Verification + production acceptance + docs (Claude Code, not sent to Codex).** One
  scoped cold agent, falsification-framed, on the shared catalog create/membership path (7 claims, see
  below). Post-deploy production acceptance is an anti-join of the same 50-pair matrix against
  `course_program_family` (expect 0 missing pairs) — the primary proof, not a family-count check, since
  a count can be right for the wrong reason. `docs/features/program-families.md` rewritten to correct
  its now-false "a family is created empty" and "membership is set on program creation or edited later
  from the Admin catalog row" claims.

Anti-drift, owner-locked: **no ADR-001 amendment** (its amended clause 2 is already storage-neutral and
ratifies many-to-many; nothing here changes what expansion means, only who can edit membership from
which side). **Program Family name is display data, Program Family ID is identity** — V147's exact-name
matching is a scoped migration-only exception (runtime-generated UUIDs, no portable literal) and must
not be copied into any application code. No family deletion, no program deletion, no `is_active` write
path, no `Business & Finance` family, no general Popover/Command primitive — the new control is a
catalog picker for small in-memory lists, not a platform layer. The legacy fused rows (`Nursing ·
Medicine`, `Nursing · Pharmacy`) stay in the catalog, unassigned, not folded into Health Sciences.
`course_programs.program_family_id` stays vestigial — not written, not dropped. No Program Family
reaches a prompt, is persisted on a Note, or triggers a live update to existing Notes — that boundary is
untouched by a management view, a rename, or a backfill.

**Routing: Codex** (new endpoint + migration + service logic, multi-system frontend+backend, ~17
must-change files — three independent task-routing triggers). Prompt already written (Long mode, Slices
1-3 only; slice 4 is this session's own work after the diff returns). **Verification tier: one scoped
cold agent, falsification-framed** — elected now rather than deferred to signoff, because all three
implementation slices touch the shared catalog create/membership path (CLAUDE.md's "two or more PRs
touched the same shared method" trigger). Seven claims to disprove: (1) family-side replace cannot evict
a program from another family; (2) rename preserves id, every membership, and every note's
applicability; (3) V147 is additive, idempotent, and cannot fail a fresh-database Flyway run; (4) V147
does not write `course_programs.program_family_id`; (5) no ordinary user can create a shared catalog
entry through any path; (6) creating a program with two families adds only that program to the note;
(7) the new multi-select's checkbox `checked` state is real, not `AddNotesModal`'s list-membership hack.
Full scope, all owner-tightened decisions, and the production membership audit are in the plan file.

### Shipped

- **Backend catalog contracts and initial membership data.** Program Families can be created with initial members and renamed or full-set edited by UUID through an ADMIN-only endpoint. Family-name duplicate matching now collapses internal whitespace and excludes the renamed row itself. `V147` additively declares the locked 50-pair matrix with exact-name joins and `ON CONFLICT DO NOTHING`; it neither deletes memberships nor writes the vestigial scalar family column.
- **One shared catalog selection and program-creation flow.** `CatalogMultiSelect` replaces both raw multi-selects with searchable native-checkbox editing in count and chip modes. `CourseProgramCreateModal` now serves Admin and authorized Note-authoring surfaces, supports several families, preserves Exam Goal behavior, and selects only the newly created program on the current Note.
- **Family-first Admin catalog management.** `/admin/course-programs` now opens on a URL-reflected Program Families tab for counts, create, rename, and family-side membership replacement. The retained Course / Programs tab provides the inverse per-program workflow and opens `+ New program` in the shared modal.
- **Cold agent falsification pass: all seven pre-declared claims CONFIRMED.** Five of the seven are backed by real-database (Testcontainers PostgreSQL) or real-HTTP-request (MockMvc with a live `@PreAuthorize` interceptor) tests, not mocked assertions. The pass surfaced one previously-unflagged, out-of-scope-of-the-seven-claims defect: the Admin rename modal always re-sent the family's full membership set even when only the name changed, using a stale snapshot that could silently overwrite a concurrent admin's membership edit on the same family (never crossed family boundaries, never touched note applicability, never corrupted data — a lost-update window, not a correctness break). Fixed in the same release rather than carried as a Known limitation, since the feature had not yet deployed: `AdminProgramFamiliesSection`'s save path now omits `programIds` entirely unless the picker was actually touched (`draft.membershipDirty`), so an ordinary rename is a true no-op on membership. Two tests added distinguishing the rename-only and rename-plus-membership-edit cases.
- **Feature-doc sweep, signoff gate.** Corrected two `docs/features/notes.md` claims stale since `v0.150.0`'s many-to-many migration (family expansion described as reading the vestigial scalar `program_family_id` column instead of the `programFamilies` join; catalog creation described as single-family-only instead of the list `CreateCourseProgramRequest.programFamilyIds` has supported since before this release). `docs/features/program-families.md`'s membership-replace description was missing half its own contract — added the omitted-vs-explicit-empty distinction the #1409 fix depends on.

### Known limitations

- **RESOLVED 2026-09-17 (during the `v0.153.0` cycle).** The production-acceptance anti-join (this release's own Slice 4 proof) ran against production (read-only) once `main` had deployed on `2547da67`: **0 missing pairs** across the full 50-pair matrix — `V147` did exactly what this release claimed. Extras report: 7 pairs present in production but outside the approved matrix (6 Accounting — `Business Administration`, `Chartered Financial Analyst`, `Economics`, `Entrepreneurship`, `Finance`, `Financial Management`; 1 Engineering — `Manufacturing Engineering`), consistent with ordinary post-deploy curator work, not a defect. Count sanity check reconciles exactly (57 = 50 + 7). Closes the `[CHECKPOINT — due 2026-09-24]` row in `ROADMAP.md`'s Backlog Index.
- **A rename that also edits membership still computes its full replacement set from an in-modal snapshot.** The #1409 fix closed the lost-update window for a rename-only save (which now omits `programIds` entirely), but an admin who *does* touch the membership picker still sends a full set read at modal-open time — a genuine concurrent edit during that window is still last-write-wins. Inherent to full-set replace; fixing it is optimistic concurrency, a different feature, not scoped here.
- **`course_programs.is_active` still has no write path anywhere in the codebase.** Unchanged by this release, deliberately — see the "Course / Program catalog lifecycle management" Backlog Index row. This release's own Admin family/program editors already use the unfiltered catalog specifically so an eventual inactive row stays manageable, but nothing can set `is_active = false` today.

## v0.151.0 - No Backdoor Left, Round Two

**Status: Released**

Theme: close the same gate gap `v0.143.0`/`v0.144.0` already closed for the exam question pool, this
time for shared quiz links.

Source: `docs/product/ROADMAP.md` Backlog Index row, found 2026-09-11 while tracing `v0.143.0` item 2's
scope, verified not-currently-live at kickoff (re-run 2026-09-16, unchanged since 2026-09-12: exactly
1 active `quiz_share_links` row, its `generated_quizzes.generated_at` predates the link's own
`created_at`, so it is not exposed to a post-share content change).

### Planned Scope

- **Shared quiz links are not deactivated on a `STUDY_PACK`-only regeneration (backend, 2 files).**
  `StudyPackService.java:928` calls `generatedQuizService.deactivateShareLinksForNote(noteId,
  ownerUserId)` only inside `if (regeneratingNoteContent)` — the combined Note+Study-Pack
  regeneration path. `POST /notes/{id}/regenerate` defaults to `NoteRegenerationScope.STUDY_PACK`
  (an absent/blank scope resolves to it), which reaches the same shared worker method with
  `regeneratingNoteContent = false`, so the deactivation never fires on that path even though
  `saveStudyPack` replaces the quiz content either way. Fix: drop the call out of the `if` gate, same
  as `v0.143.0` already did one line above it for `examQuestionPoolService.refreshPool`.
  **⚠️ SCOPE GREW MID-IMPLEMENTATION, found by the full backend build, not by the original scoping:**
  `NoteRegenerationConsequenceService.notesWithLiveShareLink` (the bulk-regeneration path's
  consequence-counting method, backing both the preflight modal's `sharedQuizzesToDeactivate` count
  and `NoteBulkRegenerationService`'s per-item `hadLiveShareLink` receipt flag, captured *before*
  dispatch from the same method) carried the identical scope gate, deliberately mirrored to match the
  single-Note primitive's then-current (buggy) behavior. Fixing only `StudyPackService` would have made
  the bulk path actively **worse**: the preflight would promise zero deactivations for a
  `STUDY_PACK`-only batch, the run would deactivate some anyway, and the receipt — reading the same
  gated method — would falsely confirm nothing happened. Fixed together: the gate condition in
  `notesWithLiveShareLink` was removed (scope no longer distinguishes any share-link consequence, since
  `saveStudyPack` replaces the quiz for either scope); its stale Javadoc, which justified the gate as
  intentional, was removed. **The confirmation dialog inherited the same assumption**:
  `bulk-regenerate-modal.tsx` gated its shared-quiz warning behind `combined &&`, so even a fixed
  backend would have shown a curator zero warning on the default `STUDY_PACK`-only scope; that gate is
  dropped too, and its component test (which had asserted the warning's *absence* on `STUDY_PACK` as
  correct) is corrected along with it. Two feature docs stated the old scope restriction explicitly and
  are corrected: `docs/features/bulk-regeneration.md` and `docs/features/study-pack-generation.md`.
  Isolated bug fix, clear root cause once traced — Claude Code implements inline, no Codex prompt.

Anti-drift: no other regeneration-path behavior changes; the two `refreshPool` calls immediately above
`StudyPackService`'s deactivation call are already unconditional and stay untouched; the
note-generation-unit meter stays genuinely scope-specific (STUDY_PACK-only still spends zero) —
unrelated to this fix and not touched by it.

Verification tier: **one `advisor()` call on the diff plus one scoped cold agent at signoff, falsification-framed.**
`advisor()` judged the diff itself (a two-line gate removal plus its stale Javadoc, covered end-to-end
by a real-Postgres integration test) adequate for a single `advisor()` call. At signoff the owner asked
for a cold agent if a pressure test was warranted — one of this repo's own triggers had in fact fired:
the implementing session's own first-pass delivery (fixing `StudyPackService` alone) was itself an
incomplete blind spot the full build caught mid-session, and a second one (the frontend modal) was
caught the same way after that — a measured blind-spot signal. The cold agent (`model: sonnet`, fresh
context) was handed 7 specific claims to disprove across the backend, the bulk driver, and the frontend
modal. 5 REFUTED outright (meter untouched, first-ever-generation no-op, frontend warning correctness,
single-note/bulk-list consistency, double-deactivation safety). 2 surfaced real but narrow, **pre-existing**
gaps in the bulk-regeneration design, not introduced by this diff — see "Known limitations" below.

`GeneratedQuizService.deactivateShareLinksForNote`'s existing null/empty-guard (read, not re-tested)
makes a first-ever-generation no-op safe by construction, and is exercised incidentally by every other
bulk-regeneration test that seeds no quiz. One added cost, not worth a test: `notesWithLiveShareLink`
now runs its lookup on every `STUDY_PACK`-only item instead of short-circuiting immediately, one extra
empty query per note with no existing quiz.

### Known limitations (found by the signoff cold agent, pre-existing, not introduced by this fix)

- **Readiness-window race can make the preflight's `sharedQuizzesToDeactivate` count OVERSTATE what a
  batch actually deactivates — the safe direction, not a correctness hole.** The preflight counts a note
  as READY-with-a-live-link at preflight time; `NoteBulkRegenerationService.processItem` re-evaluates
  readiness per-note at dispatch time and returns `BLOCKED`/`NOT_ELIGIBLE` before `hasLiveShareLink` is
  even read if the note's readiness changed in between (e.g. its Domain Context was cleared by a
  concurrent edit). That note is never dispatched, so its content (and its shared quiz) is never
  replaced, and correctly not deactivated — the preflight simply counted a consequence that then didn't
  happen, same as it would for the regeneration itself. This is the existing "preflight is a snapshot,
  not authoritative" behavior `docs/features/bulk-regeneration.md` already documents, applying uniformly
  to the share-link count too; not specific to this fix and not fixed here.
- **Narrow TOCTOU on the per-item receipt's `shareLinkDeactivated` flag.** `NoteBulkRegenerationService`
  captures `hadLiveShareLink` synchronously before `dispatchItem`, then reuses that boolean for the
  receipt once the async worker finishes seconds-to-minutes later. If a share link is newly created on
  that note's quiz in that window, the (unconditional) deactivation call still deactivates it, but the
  receipt records `false` — a stale prediction rather than a fresh read. Narrow (requires a share link
  created mid-item-processing) and not a regression from this diff; flagged as found, not fixed.

### Shipped

- **Shared quiz links now deactivate on either regeneration scope** (backend, bulk-consequence path,
  and the confirmation dialog). PR #1406, commit `aea7c12f`, merged to `releases/v0.151.0` as
  `f9013f5c`. `StudyPackService.java:919` calls `deactivateShareLinksForNote` unconditionally;
  `NoteRegenerationConsequenceService.notesWithLiveShareLink` dropped the identical scope gate backing
  the bulk preflight count and per-item receipt; `bulk-regenerate-modal.tsx` dropped the matching
  `combined &&` gate on its warning copy. `docs/features/bulk-regeneration.md` and
  `docs/features/study-pack-generation.md` corrected to match. Backend 2403/2403, frontend 2450/2451
  (1 pre-existing unrelated skip), `tsc --noEmit` clean. `ROADMAP.md` Backlog Index row updated with
  file:line evidence.

## v0.150.0 - Membership, Not a Slot

**Status: Released**

Theme: Program Family membership becomes many-to-many — a Course/Program can belong to zero, one, or
several families — closing a production bug where two admin-created families (Health Sciences,
Accounting) were structurally invisible to every Note-authoring surface, and where an existing
program's family could not be changed at all except by a database migration.

Source: `docs/claude-plans/program-family-many-to-many-final-plan.md` (FINAL, Opus architecture audit,
independently verified by the Feature Planner session 2026-09-15; owner-approved 2026-09-16). Supersedes
`docs/claude-plans/program-family-health-accounting-expansion-final-plan.md` (pass 2) on the schema
question only — that file's Health Sciences/Accounting membership decisions carry forward unchanged;
its single-FK schema, API and migration sections do not. Codex prompt:
`docs/codex-prompts/v0.150.0-program-family-many-to-many.md` (gitignored, not committed).

### Planned Scope

- **ADR-001 amendment (docs-only, Slice 0).** Constraint 2 (`ADR-001:92`) currently forbids "any preset
  table beyond `course_programs.program_family_id`" — a literal blocker for a membership table. Owner
  approved storage-neutral replacement text (plan §A) that keeps the constraint's substance (unconditional,
  membership-driven expansion) while permitting many-to-many storage.
- **`course_program_family` migration (backend).** New join table copying every existing single-FK
  membership (Engineering 18, Education 8 = 26 rows), with a relationship-level (not count-only) parity
  assertion that aborts the migration on any mismatch. `course_programs.program_family_id` is retained,
  unread by application code after cutover — no dual-write.
- **Catalog API becomes additive (backend).** `GET /course-program-catalog` gains `programFamilies: []`;
  deprecated `programFamilyId`/`programFamilyName` stay populated (alphabetical-first) for one release of
  frontend-deploy tolerance. `PATCH /course-program-catalog/{id}` becomes an authoritative
  `programFamilyIds` replacement — a free breaking change, since it has zero existing frontend clients.
- **Note-authoring bug fix (frontend).** The "Add Course/Program" family picker currently derives its
  options by scanning catalog rows that already carry a family, so a brand-new empty family is invisible
  to it — exactly what happened to Health Sciences and Accounting in production. Fixed by fetching the
  canonical `/course-program-catalog/families` endpoint instead, same one Admin already uses.
- **Admin Edit action (frontend, new).** Admins can edit an existing Course/Program's family memberships
  through a multi-select modal — this did not exist at all before this release, despite `v0.149.0`'s
  release notes claiming it did (see Corrections below).
- **Populate all four empty families (owner-run, post-deploy).** Health Sciences, Accounting, and the
  two owner-approved additions Computing & Technology and Built Environment & Design (17 memberships
  total) — via the Admin UI as the primary path, which doubles as this release's own production
  acceptance test.

### Corrections to the v0.149.0 record

Verified against current code and production, not inferred, per the many-to-many plan's audit:

- **`v0.149.0`'s release notes claim "Admins can now move an existing Course/Program catalog entry into
  a different family." They cannot, through any UI.** The `PATCH /course-program-catalog/{id}` endpoint
  shipped and is well-tested, but no frontend client ever called it — `admin-course-program-catalog-section.tsx`
  has no Edit action and `frontend/lib/api.ts` has no `updateCourseProgram` function.
- **`v0.149.0`'s release notes claim "A catalog program can now be marked inactive." No application code
  ever writes `is_active`.** New rows get `true` only from the column's DB-level `DEFAULT` (`V145`) — the
  `INSERT` statement's own column list does not include `is_active` — and there is no `UPDATE`, endpoint,
  or admin control to change it after creation. Production confirms 0 rows with `is_active = false`. This
  also means the Known Limitation recorded as "documented for the next post-deploy pass" (the two legacy
  fused rows' deprecation) was never actually reachable by any owner action — it needed a code change that
  was never scoped, not a data operation that was merely pending. Tracked as its own Backlog Index item;
  out of scope for this release (plan §P item 4).

Anti-drift: Program Family stays an authoring convenience only — never Note-persisted, never a discovery
axis, never Domain Context, never Authored Depth, never sent to generation. Exam Goal editing is dropped
from this release entirely (not even read-only display). `is_active`, the two legacy fused catalog rows,
family deletion, program deletion, and family-side membership editing (Family → Programs) are all
explicitly out of scope. No react-query/TanStack/websocket/polling is introduced — this frontend has no
query cache today and this release adds none.

### Shipped

- **Program Family membership is many-to-many end to end.** `V146` adds and relationship-validates the
  canonical `course_program_family` join while retaining the legacy scalar FK as an unread compatibility
  artifact. Catalog create and Admin Edit now write complete membership sets atomically; catalog responses
  expose ordered `programFamilies` while retaining deprecated scalar aliases. The Note-authoring Add
  Course/Program modal reads the canonical families endpoint lazily, so empty families are selectable on
  Single Note and Bulk Note surfaces, while expansion chips still appear only for families with members.
  The Admin catalog now displays zero/one/many family chips and provides the working Edit UI path that
  `v0.149.0` had overclaimed.
- **Pre-signoff falsification pass (one scoped cold agent, per plan §Q) confirmed 8 of 9 pre-declared
  claims cleanly and found one real test-quality gap, fixed before signoff.** Confirmed: migration
  relationship-parity (proven against a real PostgreSQL container, not just the H2 harness), no
  dual-write to the legacy scalar column, no family id ever reaching Note persistence, unchanged
  `@PreAuthorize` annotations, overlapping-family deduplication, honest documentation of what the H2
  migration test does and doesn't execute, tolerant JSON parsing across the deploy window, and
  `is_active` genuinely untouched. **Found and fixed:** the single highest-value new test — creating a
  program in two families must select only that program on the Note — used non-exclusive
  `toHaveBeenCalledWith`; a mutation (adding a `handleFamilyExpansion` call the boundary forbids) proved
  the old assertion would still pass. Strengthened to `toHaveBeenCalledTimes(1)`, re-verified the same
  mutation now fails and the real implementation still passes all 28 tests in the file.
