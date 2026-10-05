# RELEASES.md - NoteLib

## v0.165.0 - Bounded Discovery

**Status: Released**

Theme: stop a public Note page's discovery rails from fanning out into every page of a subject/program
list and saturating the production database — 24 backend restarts in the last month — and fix three
generation-behavior defects the Computing R4 BSCS pilot found.

### Planned Scope

**Two independent workstreams, bundled into one release at the owner's explicit direction** (urgency of
the reliability fix was the reason given for not shipping it standalone, against the fix plan's own
recommendation to the contrary — recorded here, not silently overridden).

**Workstream 1 — production reliability (urgent).** Diagnosis: `docs/claude-findings/2026-10-01-prod-restarts-public-note-page-fanout-db-saturation.md`
(Prod Investigator session). Fix plan: `docs/claude-plans/2026-10-01-public-note-page-fanout-fix-plan.md`.
Owner product decision on the one open question (rail ranking): `docs/claude-plans/2026-10-01-public-note-discovery-rail-ordering-decision.md`.

- **Root cause:** `frontend/app/public/library/[subject]/[slug]/page.tsx`'s two rails — "More from
  {Subject}" and "More {Program} notes" — walk every page of the subject's/program's entire public-note
  list via `fetchAllPublicNotePages` (`server-public-notes.ts:84-106`), which sends no `sort`, so every
  page takes the ranked branch (`NoteService.java:807-831`): a full popularity-ranking query plus a count,
  repeated per page (Accountancy: 8 pages; Civil Engineering: 18), to fill 7 cards the page then re-sorts
  itself. 23 of 24 `server_failed` restarts since 2026-09-01 trace to the liveness probe timing out while
  these queries saturate the `basic_256mb` (0.1 CPU) database.
- **Fix (Leg B of the plan):** both rails become one bounded `page=0&pageSize=N&sort=recent&readyOnly=true`
  fetch each, over-fetching by one row to exclude the current note (matching the existing pattern in
  `getServerPublicNotesBySubject`), instead of walking pages and ranking in JS. `fetchAllPublicNotePages`
  itself gains a required `sort=recent` parameter, which also fixes the subject listing page, exam hub
  pages, and the sitemap — the plan's other named walkers — for free.
- **Owner decision, not absorbed silently:** do **not** preserve live popularity ranking on these two
  rails — switch to most-recent, deterministically. Raising the database plan is explicitly off the table
  (pre-revenue). Full reasoning in the decision file above.
- **Audited, not touched:** Explore's `/notes/public/discovery-sections` is already bounded (fixed
  `limit=6`/section, no pagination walk, no count query) — not implicated in the outage, not changed.
  Still runs live `POPULAR`/`FEATURED` ranking per request (not cached), recorded as a Discovery v2 /
  precompute follow-up, not built here.
- **Legs explicitly not in this release:** Leg A (raise DB plan) is an owner Render-dashboard action, not
  code. Leg C (concurrency cap / query timeout on the ranked endpoint) is sequenced after A and B are
  measured. Leg D (slug-route caching) is recommended deferred — the backend records page views on that
  fetch, and caching would stop counting them.
- **Index/migration:** none. `EXPLAIN` (no `ANALYZE`) against production confirms the bounded query is
  already a large improvement over the current fan-out even without a dedicated index; the existing
  `idx_notes_visibility_updated_at` index doesn't perfectly match (`created_at`, not `updated_at`), and
  the subject/program filters are regexp/EXISTS-based and not trivially indexable regardless. Recorded as
  a measured follow-up if the catalog grows enough to matter, not built speculatively.
- **Count queries:** cannot be removed without new backend scope — `NoteService.listPublic` always runs a
  count alongside the rows query for any SQL-orderable paginated request, regardless of whether the
  caller needs `total`. Each rail costs 1 count + 1 bounded rows query (down from dozens of unbounded
  queries), which is not built around in this release.

**Workstream 2 — Computing R4 BSCS pilot generation-behavior fixes.** Evidence:
`docs/curriculum/r4-pilot/r4-pilot-report.md`, `r4-pilot-fix-plan.md`. The pilot confirmed `Computing`
Domain Context itself is correct on all five pilot Notes (do not touch Domain Context, Subject or
Authored Depth) — metadata correction (Applicable Programs) and content decisions are the owner's own,
through the production UI, not this release's.

- **G1 — shell commands render as LaTeX math in a Study Pack Summary** (`$g++\ program.cpp\ -o\ program$`).
  Measured against production first: 483/10,530 summaries contain `$`, essentially all genuine math
  (spot-checked, plus a literal-string search for `g++`/`.cpp`/`./program` across every summary and quiz
  in production found exactly **one** affected pack — the pilot's own). No existing-content risk to
  justify a renderer heuristic (which would risk false-positiving on real formulas, demonstrated by my own
  first-attempt regex producing 20 false positives). **Prompt-only fix**: add one instruction to the
  "Math notation" block (shell commands, flags, file paths and code are not math; never place them
  inside math delimiters or add a shell-prompt `$`) — the same new bullet applies to 10 files with a
  byte-identical Math notation block and to `note-generation-developer.txt`'s JSON-escaping variant.
- **G2 — Note Subject leaks into generated text as "{title} in {Subject}".** Two distinct mechanisms,
  both fixed: (a) the Study Pack's own title — the existing Title rule already forbids folding
  Course/Program into a title (`developer.txt:32-43`) but never named Subject; extend that *existing*
  bullet to also cover Subject, identically in `developer.txt` and `note-generation-developer.txt`, no new
  "don't do X" bullet (the `v0.96.0` anti-drift rule on wording). (b) the generated-note body's first
  line — verified as a *separate*, deterministic mechanism: the stored note title is the curator's clean
  topic, but the body's first line is the LLM's own independently-generated title
  (`backend/src/main/java/com/studysnap/backend/service/impl/OpenAiLlmStudyPackService.java`),
  never shown anywhere else (`GenerateNoteFromTopicResponse` returns only `content`). The prompt fix
  applies to both title-emitting prompts; the deterministic body-heading fix applies to **Bulk Generate
  only**, using its clean curator-supplied topic instead of the model's own title.
- **G5 — no log records the authoring domain or whether computation guidance fired.** One log line added
  to the Study Pack generation path. No stored prompts (would need a migration, not done), no note content
  logged.
- **Documented, not scoped (per the pilot's own instruction to measure, not change):** G3 (one quiz
  explanation asserted an unsupported "randomness" claim — single instance in 20 questions, doesn't trip
  the pilot's stop condition); G4 (computation guidance depends on auto-generated tags when Subject has no
  keyword — already documented as a known nuance in `v0.164.0`'s `DomainContext.java`, no regression
  observed in the pilot sample); G6 (Bulk Generate has no instruction field — explicitly a curation
  convention question under D-35, not a product gap).

Anti-drift: no DB plan change; no new ranking infrastructure, Redis, recommendation engine, vector search
or LLM call for discovery; no randomness; no Featured flag; no Explore or Public-Note-page redesign; no
change to global Public Library ordering; no BSCS TSV edits (strategist's own action); no bulk
generation; no `QUANTITATIVE_KEYWORDS` change; no production write of any kind.

**Verification tier, pre-declared:** this release touches prompt text shared across every generation
surface (11 files) and a production-reliability fix with no existing automated coverage of the request
shape it changes — **one scoped cold falsification agent before signoff**, per the standing gate
(delivery-introduced-defect trigger already fired once this cycle during the Computing pilot's own
review process, and the reliability fix's request-shape guards are exactly the class of thing a cold
agent should independently verify rather than trust from the implementing session).

**Checkpoint gate, pre-declared:** Workstream 1 ships ahead of its own evidence (the thread-occupancy
read in the diagnosis is INFERRED, not measured) — signoff owes a dated `[CHECKPOINT — due 7 days after
this release's production deploy]` per the fix plan's own pre-stated kill criterion (`server_failed`
events continuing at a comparable weekly rate means the diagnosis is wrong or incomplete).

### Shipped

- **Workstream 1 — Public Note discovery rail fan-out fix (frontend only, no backend change needed).**
  Both rails switched to one bounded `page=0&sort=recent&readyOnly=true` fetch each —
  `getServerPublicNotesBySubjectSlugRecent` and `getServerPublicNotesByCourseProgramRecent`
  (`frontend/lib/server-public-notes.ts:311-336`) — replacing the unbounded popularity-ranked walk (the
  24-restart mechanism). `fetchAllPublicNotePages` (`server-public-notes.ts:100-122`) now always sends
  `sort=recent`, which removes the expensive ranking-query multiplier from its other callers too — the
  subject listing page and exam hub pages (via `getServerPublicNotesBySubjectSlug`/
  `getServerPublicNotesByCourseProgram(s)`) and the sitemap — though those three still walk every page of
  their subject/program/catalog (that part of their job is unchanged and out of scope; see the
  full SURFACE/ENDPOINT audit in `docs/claude-plans/2026-10-01-public-note-discovery-rail-ordering-decision.md`).
  No visible-ordering regression on the subject listing page or exam hub pages: both re-derive their own
  Featured/Popular/Recent/remaining sections in JS from the full set via
  `lib/public-library-discovery.ts`'s own internal `.sort()`, independent of fetch order — audited, neither
  relies on arrival order. **Correction, found by the post-merge cold falsification pass**: the sitemap
  (`app/sitemap.ts`) is the exception — it maps the walker's result directly into entries with no re-sort
  step, so it now lists Notes most-recent-first instead of by the old popularity order. Not fixed, because
  it's not a regression worth fixing: a sitemap's entry order carries no ranking signal to a crawler, so
  this has no SEO or user-visible effect, unlike the subject/exam-hub pages' rendered card order. No caller
  passes its own `sort`, so no duplicate-param risk.
  `app/public/library/[subject]/[slug]/page.tsx` now derives `courseProgram` from the note's own joined
  `coursePrograms[0]` (`NoteService.resolvePublicDetailPrograms`, backed by
  `NoteCourseProgramRepository.findByNoteId`'s `ORDER BY course_programs.name` — the same ordering
  `findByNoteIds` used for the old list-item `applicablePrograms[0]` derivation, so multi-program notes
  resolve to the identical program as before, and the join is confirmed stable across ISR revalidations)
  instead of re-walking the program's note list. Tests: `frontend/lib/server-public-notes.test.ts`
  (mutation-tested — guards confirmed to actually fail without `readyOnly=true`),
  `frontend/app/public/library/[subject]/[slug]/page.test.tsx` (41 tests).
  `docs/features/public-library.md` section K updated to describe recency ordering instead of the stale
  engagement-score claim.
  - **Known limitation, intentionally not fixed this release:** the Subject and Program rails are not
    cross-deduped — after a visitor finishes the embedded Quick Check, a note sharing both the current
    note's Subject and Course/Program can appear in the quiz-preview rail and the Program section. The
    original rail contract's over-fetch covered the current note only; deduplicating Subject against
    Program would require its own bounded headroom decision and could starve the smaller rail when
    both top-N windows substantially overlap.
    Pinned as current, deliberate behavior by a dedicated test in `page.test.tsx`
    ("documents that the two most-recent-first rails are not cross-deduped"). Matches the owner's own
    instruction verbatim ("If cross-rail duplication already has a simple bounded exclusion mechanism,
    preserve it. If avoiding duplicates requires broad fetching/fan-out, production safety wins.").
  - **Known limitation, verified zero production impact:** `getServerPublicNotesBySubjectSlugRecent` drops
    the blank-subject/`general`-slug special case the unbounded walker carried (it used to fall back to a
    full-catalog scan to find blank-subject siblings — the worst fan-out of all, not reintroduced). A
    read-only `count(*)` against production (2026-10-02) confirms **0 public notes have a blank or
    whitespace-only subject**, so this has no current effect.
  - **Subject-vs-Subject duplication fixed:** the quiz-preview's "More from {Subject}" rail excludes
    notes shown in the always-visible "More in {Subject}" section, when that section has enough notes
    to render (`frontend/app/public/library/[subject]/[slug]/page.tsx:96-113`). Its single bounded
    recent fetch now requests six candidates plus the current-note headroom, then displays up to three.
    The "More in {Subject}" fetch and section are unchanged; when that section is hidden, the quiz
    rail retains its available related notes.
- **Workstream 2 — Computing R4 pilot generation behavior (G1, G2, G5).** G1 adds one identical
  shell/CLI-is-not-math bullet to all 11 Math notation prompts
  (`backend/src/main/resources/prompts/study-pack-v1/developer.txt:133`,
  `note-generation-developer.txt:89`). G2(a) extends the existing title rule to treat Subject as
  context when it only names a broader container, with the pilot's Algorithms example in both
  title-emitting prompts (`developer.txt:35-44`, `note-generation-developer.txt:21-30`). G2(b)
  replaces the model's body heading with the curator's topic on **initial Bulk Generate only**
  (`backend/src/main/java/com/studysnap/backend/service/NoteBulkGenerationService.java:359-371`).
  Owner decision, 2026-10-02: the single-note editor and onboarding accept an informal learner-typed
  topic distinct from a polished title, while Bulk Generate's topic is the clean curator title. The
  pilot observed clean stored Note titles beside Subject-leaking generated body headings
  (`docs/curriculum/r4-pilot/r4-pilot-report.md`, finding 6). G5 logs the effective authoring domain,
  Subject, guidance result and trigger once per input-message build
  (`backend/src/main/java/com/studysnap/backend/service/impl/OpenAiLlmStudyPackService.java:1698-1742`).
  **Verification limit:** G1 and G2(a) are prompt text; automated tests pin file content, not model
  behavior. The owner must regenerate a pilot Note's Study Pack and inspect its Summary and title
  (`r4-pilot-fix-plan.md` section 4). **Known limitations:** the single-note editor and onboarding
  rely on G2(a)'s prompt wording alone for generated titles; they do not get the deterministic body
  override. A bulk-created note's body heading can revert to the model title if its content is later
  regenerated through `StudyPackService.generateStudyPackFromExistingNoteAsync`, which bypasses
  `NoteBulkGenerationService`. G5 gives aggregate Subject/Domain visibility, not per-note or
  per-request correlation; the generation context has no note/request id, and one line is logged per
  actual attempt, including retries and Long Exam batches.

## v0.164.0 - Computing

**Status: Released** (signed off 2026-09-30; PR #1469 merged into `releases/v0.164.0`)

Theme: ratify `Computing` as a 13th Domain Context (ADR-001 amendment), ship the small enum/frontend value,
correct the two stale claims in the ratification package's own numbers, and sweep the context/spec docs the
strategist and curators read.

### Planned Scope

**Owner-directed, 2026-09-30, closing Workstream A** (`docs/claude-plans/computing-domain-context-final-decision-package.md`,
ratification-ready close-out; supersedes `computing-domain-context-decision.md` and its tightening prompt for
*action*, both kept as the evidence trail). Named "Workstream A" in `docs/curriculum/bscs-year1-decision-log.md`,
whose rows D-13 and D-34 are blocked on this closing.

- **ADR-001 amendment ratifying `Computing`** (enum `COMPUTING`, label `Computing`, `quantitative = false`) —
  a new dated section superseding the 2026-09-04 rejection, answering both of its grounds (naming borrowed from
  ACM/IEEE Computing Curricula 2020 and CHED CMO No. 25 s.2015; the comprehension test applies to curators, not
  learners), stating the two-test curator decision rule (object-of-study, then cross-sibling-reuse) as the durable
  operational boundary, and recording the corrected clause (a) evidence and the corrected governance ratio.
- **Two corrections to the package's own numbers, applied before anything cites them:** (1) the 34 Discrete
  Structures I rows do **not** get `applicable_programs` extended to IT/IS/SE — Domain Context = `Computing`,
  Applicable Programs stays Computer Science only, contrary to package §5/§9/§10; (2) the BSCS Year 1 corpus is
  136/136 classified (the 3 `AMBIGUOUS` rows resolved to `COMPUTING_SHARED`), not 133/136 as the package states.
- **Enum/frontend value** — `DomainContext.COMPUTING` (backend), `DOMAIN_CONTEXT_OPTIONS` entry + `DomainContext`
  union member (frontend), with a routing description (excludes CS-specific formalism, IT/IS/SE specialization
  treatment, and Precalculus/Algebra) and updated count/label tests on both sides.
- **Doc sweep** — `REVIEW_SET_SHAPING_CONTEXT.md` (closed vocabulary + two-test boundary paragraph),
  `review-set-workbook-spec.md:118-119` (independent stale-claim fix: the save-time rejection claim is wrong;
  the check is generation-time only), `GPT_CONTEXT.md` (12→13, and discharging the "don't propose a 13th value"
  line the same way the 9th/12th-value rules were discharged), `NOTES_AND_COLLECTIONS_CONTEXT.md` §0 (12→13).
- **R4 runbook addendum** (`docs/claude-prompt/canonical-knowledge-architecture-out/17-r4-verification-runbook.md`)
  — a Computing section for the 5 named pilot notes, prepared but **not executable today**: none of the 5 exist
  in production yet (read-only check, 2026-09-30), and driving the pilot is an owner action through the
  production UI in any case (a Domain Context write + regeneration), never Claude's to run.
- Close `docs/curriculum/bscs-year1-decision-log.md` D-13 and D-34, citing this package.

Anti-drift: no BSCS TSV edits (strategist's own action, out of this scope); no bulk generation; no
`QUANTITATIVE_KEYWORDS` change (no companion keyword decision here, unlike `BASIC_MEDICAL_SCIENCES`); no
prompt change (package §8 explicitly forbids preemptive prompt tuning); no production write of any kind.

### Scope disposition (signoff, 2026-09-30)

- **Every Planned Scope item: SHIPPED, verified against code with `file:line`, not from the release notes
  alone.** ADR-001 amendment: `docs/architecture/ADR-001-canonical-knowledge-architecture.md:417`. Enum:
  `DomainContext.java:47`. Frontend union member: `frontend/lib/api.ts:554`. R4 addendum:
  `17-r4-verification-runbook.md:189`. Decision-log closure: `bscs-year1-decision-log.md:33,54`.
- **The doc sweep EXPANDED beyond the four docs originally planned**, found during pre-commit review:
  `docs/features/notes.md`, `challenge-quiz.md`, and `study-pack-generation.md` also enumerate the closed
  Domain Context vocabulary and needed the same 12→13 update. Also fixed 6 stale file-path references left
  by an unrelated prior session's rename batch, in the same 4 files this release was already editing, and
  corrected a second stale claim discovered in `NOTES_AND_COLLECTIONS_CONTEXT.md` while re-stamping it
  (Interior Design / Landscape Architecture / Environmental Planning are now live catalog programs, not
  absent as it claimed; the catalog is 90 programs, not 41).
- **A pressure-test pass (`advisor()`) on the diff before commit found and fixed four real defects before
  they shipped**: an arithmetic error in the ADR (33/136 vs. the correct 34/136); a `[PROD]` tag on a count
  that actually came from the local TSV, not a production read; an R4 addendum methodology built on
  invented Subject-Plan assignments for the 5 pilot notes (none of which exist anywhere in the TSV), which
  broke for 4 of the 5 notes because a 3-program note cannot generate under a NULL-Domain-Context fallback
  at all under `ADR-001`'s own multi-program rule; and the kickoff's own commit-structure violation (code
  and doc-sweep feature content was about to ride on the kickoff's direct-to-release-branch commit instead
  of its own branch+PR).
- **Not shipped, by design:** the BSCS TSV itself is not edited (strategist's own action); the R4 pilot did
  not run (its 5 named notes don't exist in production yet); the 3 originally-`AMBIGUOUS` corpus rows are
  not separately re-verified here (the owner's resolution to `COMPUTING_SHARED` is taken as given, per the
  correction applied at kickoff).

### Shipped

- **ADR-001 amendment ratifying `Computing`** (13 values total): superseded the 2026-09-04 rejection
  (naming answered via ACM/IEEE Computing Curricula 2020 + CHED CMO No. 25 s.2015; comprehension test
  answered as curator-facing, not learner-facing); recorded the two-test curator rule; corrected the
  package's own 133/136 → 136/136 evidence and removed its erroneous 34-row `applicable_programs`
  extension instruction; re-read production rather than citing the stale `12:51` ratio — catalog is
  now 90 programs, ratio `13:90` = 0.144; carries `[CHECKPOINT — due when BSCS Year 1 Computing
  authoring reaches 10+ classified notes, backstop 2026-12-29 regardless]`, added to the Backlog Index
  at signoff with its full kill criterion and two-tier denominator rule. Inline dated flags added at
  the two lines that named `Computing` as previously rejected (not rewritten).
- **`DomainContext.COMPUTING`** (backend enum, `quantitative = false`) and the matching frontend
  `DOMAIN_CONTEXT_OPTIONS` entry / `DomainContext` union member, with a routing description (excludes
  CS-specific formalism, IT/IS/SE specialization, Precalculus/Algebra). Test coverage: `DomainContextTest`
  (13-label `containsExactly`, new CsvSource row, round-trip test updated to the newest value) and
  `domain-context.test.ts` (length 12→13, new routing-clause pinning test). Full backend build
  (2,636 tests) and full frontend suite (225 suites, 2,598 tests) both green.
- **Doc sweep**, all corrected to 13 values / the new two-test rule: `REVIEW_SET_SHAPING_CONTEXT.md`,
  `GPT_CONTEXT.md` (also discharged the "don't propose a 13th value" line, same pattern as the 9th/12th
  discharges), `NOTES_AND_COLLECTIONS_CONTEXT.md` §0, `docs/features/notes.md`, `challenge-quiz.md`,
  `study-pack-generation.md`. **Independent stale-claim fix**, `review-set-workbook-spec.md:118-119`:
  corrected "the server rejects the save" to "generation-time only" — found true regardless of this
  ratification. Bumped the "Last updated" stamps on `REVIEW_SET_SHAPING_CONTEXT.md` and
  `NOTES_AND_COLLECTIONS_CONTEXT.md` at implementation, and on `GPT_CONTEXT.md` itself at signoff (all
  three now read `v0.164.0 (Released)`, not `(In Progress)`); corrected a second stale claim found
  along the way in `NOTES_AND_COLLECTIONS_CONTEXT.md` (Interior Design, Landscape Architecture and
  Environmental Planning are now live catalog programs, not absent as it claimed; catalog is 90
  programs, not 41).
- **R4 runbook addendum** (`17-r4-verification-runbook.md`): a Computing section for the 5 named pilot
  notes, prepared but explicitly marked not-yet-runnable — none of the 5 exist in production yet
  (read-only check, 2026-09-30), execution is an owner action through the production UI regardless, and
  the comparison-arm mechanics are honestly scoped (a true A/B needs a single-program note; a
  multi-program note can only be scored on its `Computing` arm alone, since `ADR-001`'s own
  multi-program rule blocks a NULL-Domain-Context fallback from generating at all).
- **`docs/curriculum/bscs-year1-decision-log.md`** D-13 and D-34 closed, citing this package; D-33
  (Precalculus) stays open, unchanged, per the package's own scope.
- **`docs/claude-plans/computing-domain-context-final-decision-package.md`** — dated correction note
  added at the top recording both owner corrections, rather than silently editing the original.
- **`docs/product/ROADMAP.md`** Backlog Index row updated: gate cleared for ratification/code, corrected
  numbers, R4 pilot's actual blocker (notes don't exist) stated explicitly; at signoff, the row's Gate
  cell was rewritten to carry the formal `[CHECKPOINT — due when BSCS Year 1 Computing authoring
  reaches 10+ classified notes, backstop 2026-12-29 regardless]` tag with kill criterion,
  instrumentation and two-tier denominator rule, per the signoff checkpoint gate.

## v0.163.0 - No Peeking

**Status: Released**

Theme: stop a quiz from telling a learner the answer before they've committed to one, and stop Challenge
Quiz's question bank from racing itself during regeneration.

### Planned Scope

**Scope picked by the owner, 2026-09-27, from a Backlog Index survey (five candidates originally picked;
one dropped at kickoff — see the note after item 4 — leaving four, at the top of the documented 3-4 item
sweet spot).**

1. **Quiz answer-key redaction across 6 practice-session surfaces (full-stack, far larger than first
   scoped — see the design plan, revised after `advisor()` (3 rounds) and again after a cold Opus
   falsification pass, which itself needed one more round of direct re-verification before its findings were
   trusted).** `correctIndex`/`correctIndices`/`explanation` are served unfiltered, unconditionally, across
   Long Exam, Board Exam, Adaptive Practice, and Challenge Quiz. **Interview Practice, discovered mid-item to
   be a fully separate backend (`InterviewPracticeService`), added to scope: it has a real, already-shipping
   resubmission exploit** — directly re-verified, not taken from a subagent's report: `answerQuestion`
   (`:168-211`) takes a client-supplied `questionIndex` with no re-answer guard, generates a fresh LLM
   critique (which reveals correctness) on every call, and `buildReport` (`:461`) scores whatever was stored
   for that index at completion time — so submit-wrong-then-resubmit-correct is real and counts today. This
   is the learner's own practice record (an Interview Readiness Report), not a cross-user exposure — treat it
   as part of this item's normal ship cadence, not a separate hotfix, unless the owner decides otherwise.
   **Quick Review gets the LEAST protection of the 6, not the most, and is deferred to its own design pass:**
   its session has never stored a quiz at all (`session.setSessionState(null)` from creation) — this actually
   makes the original kickoff claim ("the `/progress` round trip carries no quiz-item data") TRUE, just true
   for a different, uglier reason than claimed: there's no session-side quiz to protect because the frontend
   never reads one — `quick-review/page.tsx:371,417` sources the entire rendered quiz, answer key included,
   from `getNote(noteId)` (`NoteResponse`), the exact endpoint decided to stay unredacted. **The "backend-only
   pitch" history in the original kickoff text was still false** (traced to the discarded unauthorized fork,
   echoed in without verification) — that correction stands; the response-type claim itself did not need
   correcting, only its conclusion did. **Full corrected design:**
   `docs/claude-plans/2026-09-28-quiz-answer-key-redaction-plan.md`. Owner decisions: Note Detail, Study
   Pack, the public note page, and DOCX `WITH_ANSWERS` export all stay **unredacted**; Quick Review gets a
   genuinely new quiz-less fetch, sized as its own short design pass, not dropped from scope; Challenge
   Quiz/Board Exam's post-completion answer review widens `ChallengeQuizSessionResponse` directly rather than
   adding a second round trip; Adaptive Practice's completion endpoint currently lets the client's submitted
   selections override server-stored ones for scoring, which also changes what `ConceptHealth` gets written
   from — fixed as part of this item, flagged for the pre-signoff falsification brief specifically. **⚠️
   Explicitly a practice-integrity fix, not a security boundary** for the 5 modes other than Interview
   Practice's already-live exploit — an account owner can still read their own note's answer key via the
   Note/Study Pack page; this closes the *accidental* exposure and the *resubmission* exploit, not
   account-owner self-access. Board Exam confirmed served by `ChallengeQuizService` (`MODE_BOARD_EXAM`), not
   `LongExamService`. Teacher share-link quiz path (`/quiz/[token]`) traced and confirmed **already safe**.
   Backlog Index row: "Quiz session wire payload already includes `correctIndex`/`correctIndices`/
   `explanation` for unanswered questions, across every shared quiz mode."
2. **Challenge Quiz `releaseClaims` deadlock risk.** The `REQUIRES_NEW` transaction can wait indefinitely
   on locks its own caller already holds (all three relevant timeouts are 0); dormant in production logs
   today, not actively firing. Needs a real two-connection Postgres integration test before any fix
   ships — a mocked-repository test would pass under the same defect by construction. Backlog Index row:
   "`ChallengeQuizQuestionBankService.releaseClaims`'s `REQUIRES_NEW` transaction can wait indefinitely on
   locks its own caller already holds."
3. **Challenge Quiz bank-invalidation race.** `generateMoreQuestions` can race the bank-invalidation path
   and let stale rows survive a regeneration. Needs a generation-stamp migration; no rewrite of existing
   rows. Backlog Index row: "`ChallengeQuizService.generateMoreQuestions` can race `v0.162.0`'s new bank
   invalidation, letting stale-content rows survive a regeneration."
4. **Challenge Quiz session-complete vs. bank-delete throw.** Lower-severity, same falsification pass
   that surfaced items 2-3, same shared `ChallengeQuizService`/bank code — bundled here rather than
   deferred, since items 2-4 all touch the same shared method. Backlog Index row: "A Challenge session
   completing at the exact moment a regeneration's bulk bank-delete commits could throw, not corrupt."
5. **Shared quiz-session-entity concurrency: unlocked writers can resurrect a completed session, and
   Quick Review's staleness anchor is wrong.** Found by the item 1 pre-signoff falsification pass (three
   cold Opus agents, one per surface), and by directly reading the shared entity's full writer set across
   all five services afterward — the three-way split let this fall between the agents, since none of them
   owned the shared entity itself. `QuickReviewSessionEntity` has no `@Version`/`@DynamicUpdate`; several
   writers per service (`updateSessionProgress`/`forfeitSession` on Challenge Quiz; `answerQuestion`/
   `completeSession` on Interview Practice; `completeSession`/`forfeitSession`/`saveConfidenceLevel` on
   Quick Review; `saveProgress`/`pauseSession`/`resumeSession`/`completeSession`/`forfeitSession` on Long
   Exam; `completeAdaptiveSession` on Adaptive Practice) read the row unlocked and later overwrite the
   *entire* row. A racing unlocked write that read before a completion commits and saves after silently
   reverts status to non-terminal — reproduced live on Postgres for Challenge Quiz by the falsification
   agent. Before this release that was cosmetic; after item 1's redaction, `/complete` is the one channel
   that reveals several modes' answer keys, so resurrecting a completed session is a real resubmit-with-
   the-revealed-key exploit, not just data corruption. Interview Practice's own resubmission fix (item 1)
   is separately incomplete: its per-index lock only stops a *sequential* resubmit of the *same* index; a
   race across *different* indexes lets the later-committing write erase the other index's stored
   critique, unlocking it for a real resubmit with the now-revealed answer. Separately, Quick Review's
   staleness check compares session creation time against `notes.generation_enqueued_at` (set at enqueue),
   but the quiz only changes at commit, and nothing blocks entry while the note is `GENERATING` — a
   session created in that window is never caught as stale, which can leak the new quiz's key on resume
   and let `completeSession`/`updateSessionProgress` (neither checks staleness at all) grant mastery from
   answers given on a different quiz. `study_packs.generation_stamp` cannot anchor this fix as-is: it
   advances on a summary-only admin repair that never touches the quiz (false stale) and does not advance
   on the malformed-quiz repair path that does change the quiz (false fresh). Fix direction, decided with
   the owner: extend the existing `PESSIMISTIC_WRITE`/`FOR UPDATE` pattern (already used by several other
   writers in each service) to every unlocked mutating writer above, rather than adding `@Version` —
   Challenge Quiz's `persistProgress` fires unserialized on every answer toggle with failures silently
   swallowed client-side, so a reject-based optimistic lock would need new retry/merge semantics on the
   highest-traffic path to avoid silently dropping legitimate overlapping writes; a wait-based lock closes
   the same defect with no new conflict-handling code, since each writer's *existing* status check
   correctly sees the fresh terminal state once unblocked. Interview Practice's fix does not hold a lock
   across the LLM call (would serialize all answers behind LLM latency): it re-reads locked only for the
   short merge-after-LLM step, matching the existing split-transaction precedent used elsewhere in this
   codebase for the same reason. Quick Review's staleness anchor becomes a dedicated `quiz_stamp` (distinct
   from `generation_stamp`), bumped only where `study_packs.quiz` is actually written in place, stored on
   the session at creation and compared by value (not timestamp) at `/answer`, `/progress`, `/complete`,
   and `findQuizMasteredAt`, with a defined fallback to the current timestamp check for sessions that
   predate the migration. Known Limitations NOT in this item's scope, carried forward: two concurrent
   recipient session starts can still 500 (insert-vs-insert, no row to lock yet); a lost `/progress`
   network write can still lose the active retry-question set on reload; Adaptive Practice's legacy
   concept-name fallback still trusts unfiltered client strings; and `AdminStudyPackTransactionHelper`'s
   admin-repair stamp race (documented in item 2-4's Known Limitations) is broader than originally
   described — it can revert a concurrent user regeneration's entire quiz and content, not just drop a
   stamp increment.

**A fifth candidate from the original kickoff survey — Study Plan Builder drag-persist race, unrelated to
item 5 above — was DROPPED at kickoff, not scoped in.** Its Backlog Index row ("Study Plan Builder drag persists per drop and races its own save")
had never carried a `Last reviewed` date; actually reading the current code at this kickoff (not just
grepping for the old, lost Codex prompt) showed the deferred "Save order" model it called for already
shipped in `v0.96.0` (`185e0cc7`, 2026-08-29) — `study-plan-builder-page-client.tsx`'s
`savePendingLeafOrder`/`persistLeafItems` implement exactly that model, and all three traps the row named
were addressed per that commit's own mutation-verified audit. The row is corrected to SHIPPED; see
`ROADMAP.md`.

Anti-drift: no automated Tier 3 question-quality gate this release; H4's validator, H5's wording, and
H6's exclusion (still gated on H5's post-ship checkpoint) are all unchanged; the bank-invalidation
migration (item 3) adds two generation-stamp columns only; the teacher share-link quiz path is a known,
explicitly out-of-scope gap for item 1, not silently ignored. Note/Study Pack pages, the public note page,
and DOCX `WITH_ANSWERS` export all stay unredacted by owner decision (item 1's own plan file). **⚠️ Quick
Review's fix does not add a quiz store**: the session continues to use the Study Pack's persisted quiz and
stores only locked selections plus navigation state in its existing `session_state` JSONB.

**Verification tier — the pre-signoff falsification pass this note called "still owed" has since run, in
three separate layers, and is now closed:**
1. **Three cold Opus agents, one per item-1 surface, no inherited context**, run against the merged
   diffs of PRs #1460-1464. This is what *found* item 5 (the shared-entity concurrency defect) — none of
   the three individually owned the shared `QuickReviewSessionEntity`, so the systemic gap fell between
   them; reading the entity's full writer set directly, afterward, is what actually surfaced it.
2. **A fourth, scoped Opus agent against item 5's own diff** (PR #1466's first commit) found four more
   issues in that delivery itself — most seriously, that Interview Practice's split-transaction fix did
   not actually isolate its two phases under this app's own `spring.jpa.open-in-view=true` — all fixed,
   each proven with a failing-first test (real Postgres or jsdom).
3. **A fifth, narrowly-scoped Opus agent against just the `AbortController`-based queue-ordering
   mechanism** added in step 2's fix, since it was the one piece no prior agent had reviewed — found the
   mechanism was itself asymmetric and could resolve a superseded caller's waiters too early. Fixed and
   proven failing-first; PR #1467.

Every `MockMvc` route test and `advisor()` checkpoint this note called for was applied across all three
layers. See RELEASES.md's Shipped section (items 1 and 5) for the full finding list and fixes.

### Shipped

- **Quiz-session concurrency and Quick Review quiz staleness (item 5):** Mutating writers across
  Challenge Quiz, Interview Practice, Quick Review, Long Exam, and Adaptive Practice now lock the
  session row before checking status and saving, so a writer that waits behind completion sees the
  terminal state. Interview answers validate in one short transaction, call the LLM with no
  database-transaction or row lock held, then lock and merge into a fresh session in a second
  transaction; this closes the cross-index critique-erasure/resubmit race left by item 1. **The split
  frees the transaction and row lock, not the pooled connection** — `spring.jpa.open-in-view` is ON here,
  so one connection is held for the whole HTTP request regardless of transaction boundaries (per
  `ConnectionLifetimeStartupLogger`'s own startup line); do not describe this as freeing a connection.
  Challenge Quiz coalesces client
  progress writes and awaits its final progress flush before leave. `V152` adds
  `study_packs.quiz_stamp BIGINT NOT NULL DEFAULT 0` and nullable
  `quick_review_sessions.quiz_stamp_at_creation`; quiz replacement bumps only the quiz stamp (inside
  `saveStudyPack` itself, guarded by `!isNewStudyPack`, so all five of its callers are covered, not only
  the async worker), and Quick Review compares the captured value on answer, progress, completion, and
  mastery lookup. Legacy sessions with a null capture retain the earlier enqueue-timestamp rule. Real
  PostgreSQL tests prove every newly locked writer waits behind a completion before reading terminal
  status, prove the split Interview lock boundary and cross-index merge, and cover Quick Review's stamp
  and legacy fallback. MockMvc requests pin both Adaptive completion aliases and the Quick Review
  no-share denial. **Audit corrections, found before commit:**
  - **The delivered Interview split did not actually split under production's own configuration.**
    `spring.jpa.open-in-view` is ON here (the Spring Boot default — `application.yaml`'s own startup
    logger confirms `open-in-view=ON` at boot), so one `EntityManager` spans the whole HTTP request; the
    two `TransactionOperations.execute()` calls each open their own database transaction but share that
    one `EntityManager`'s identity map, so phase B's "fresh" locked read handed back phase A's already-
    managed, now-stale Java object — the row lock was real, but it protected a write into stale memory,
    reopening the exact cross-index erasure this item exists to close. Confirmed with a new test that
    manually binds an `EntityManagerHolder` per worker thread (the same mechanism
    `OpenEntityManagerInViewInterceptor` uses), which failed against the delivered code and passes only
    after adding `entityManager.refresh(session)` immediately after phase B's locked read. The original
    two-thread test (no bound `EntityManagerHolder`) could not have caught this: absent open-in-view
    binding, each phase gets its own fresh `EntityManager`, so it never exercised the identity-map path
    at all.
  - **Challenge Quiz's `finalizeChallengeSession` did not await the last progress write before
    completing.** The server grades from stored `session_state`, not the client's request body — if the
    last answer's coalesced progress write was still queued when `/complete` fired, the server would
    score from a state missing that answer. `finalizeChallengeSession` now awaits the latest progress
    flush first, mirroring Long Exam's `await flushIdentificationAnswer()` before its own completion.
  - **The tab-hidden write regressed on real unload.** Coalescing made the `visibilitychange`-hidden
    write wait behind whatever was already in flight; on a real tab close or mobile background-kill the
    JS context can die before the queue ever drains, so the latest state might never be sent — before
    coalescing, that write fired immediately. Added a queue-bypassing `persistLatestProgressImmediately`
    used only by the tab-hidden path (send now, drop any stale queued entry for that session, and only
    resolve that dropped entry's own waiters once the immediate write itself settles — resolving them
    synchronously would let an awaiting caller like the completion flush above proceed before the
    immediate write is even sent, racing it for the row lock). The explicit Leave action keeps the
    original queue-respecting, awaited path deliberately — it already awaits the flush itself, so
    queueing costs nothing there, and an existing test pins that sequencing. **The
    `beforeunload`/route-change guard (`handleBeforeRouteLeave`) was deliberately left on the same
    queue-respecting path, unlike the tab-hidden handler, even though it is NOT awaited and the page can
    in principle close during it.** This is not verified to be safe by any test here — the assumption is
    that modern browsers also fire `visibilitychange`→hidden before or alongside an actual unload, so
    the tab-hidden bypass above already covers the real risk in practice. Known Limitation: a browser or
    OS path that triggers `beforeunload` without ever firing `visibilitychange`→hidden first would still
    queue behind an in-flight write with no bypass; not fixed here.
  - **A newly added staleness guard on `/progress` and `/complete` contradicted an existing, deliberate
    design decision.** `recheckMaterialAccess`'s empty-`Optional` case covers two paths, both
    deliberately tolerated by the existing code, neither a denial: an infrastructure fault reading the
    pack (its own comment: "completion has always tolerated a pack it cannot read"), and a pack that has
    genuinely been deleted (`findVisibleStudyPack`'s Javadoc: "a pack that... has been deleted has
    always succeeded — the caller owns the SESSION... denying it here would strand the learner's own
    session for a reason that has nothing to do with sharing"). A genuine access denial already throws
    through a separate path in the same method, so the added check could only ever fire on one of these
    two tolerated cases, and only ever wrongly. Removed; `isStale`'s existing
    `accessibleStudyPack.isPresent()` guard already does the right thing when the pack can't be read.
  - Added an in-flight guard on Quick Review's `initializeSession(force=true)`: three call sites
    (`/answer`, `/progress`, `/complete`) can each independently hit a stale-session 409 and each call
    `initializeSession(true)`, and without a guard that can fire more than one concurrent `startSession`
    — reaching this item's own already-accepted insert-vs-insert Known Limitation by a new path, not a
    new failure mode, but cheap to close off anyway.
  - **A second, independent falsification pass (a fresh Opus agent, no inherited context) found four
    more issues in the diff above, all fixed and each proven with a real Postgres or jsdom failing-first
    test:**
    - **The identical stale-read-after-lock pattern also exists in `ChallengeQuizService.
      resolveExistingChallengeSession`, pre-existing since July (`c76e5c1d`/`287f0069`), not introduced by
      this item.** It reads the session unlocked to capture `observedStatus`, then locks it and compares
      `lockedExisting.getStatus() != observedStatus` — the same object by Hibernate identity, so the
      comparison can never fire. Proven live on real Postgres (a session completed by one connection
      still read as `IN_PROGRESS` by a racing `startSession`). Fixed with the same
      `entityManager.refresh()` pattern as Interview Practice's fix above, proven with a new test that
      pauses between the two reads via an AOP interceptor and commits a status change from a second
      connection in that window.
    - **The coalescing queue had no per-request timeout, and nothing cleared it on
      `resetToPrestart`** — one hung progress write (a fetch has no default timeout) would have blocked
      every later progress write for the rest of the page's life, including a later session on the same
      page, and `handleLeaveSession`'s new await could hang indefinitely, contradicting this item's own
      "costs nothing there" claim above. Fixed: each queued write now carries an `AbortController` with
      a 30-second dead-request bound (generous on purpose — a legitimate `+5 Questions` LLM call can
      legitimately hold this row's lock that long), and `resetToPrestart` aborts and clears the queue.
    - **The tab-hidden immediate write and the queue were not ordered against each other** — an
      in-flight (or 401-refresh-retried) queued write carrying older state could still land on the
      server after the newer immediate write, overwriting it. `updateChallengeQuizSessionProgress` now
      accepts an optional `AbortSignal`. **This mechanism was itself corrected in a third round below —
      it was asymmetric and dropped waiters early; read that bullet for the actual current design.**
    - **A quiz-repair race could give two different quiz contents the same `quiz_stamp`.** Both
      `StudyPackService.saveStudyPack` and `AdminStudyPackTransactionHelper`'s malformed-quiz repair read
      the pack unlocked and incremented `quizStamp` in Java, so a repair racing a real user regeneration
      could compute the identical stamp value the regeneration just committed — defeating the invariant
      `V152` exists to protect. Fixed with a new atomic `StudyPackRepository.bumpQuizStamp` (`SET
      quiz_stamp = quiz_stamp + 1`, a targeted DB-level increment) replacing the Java-side
      read-increment-write in both places; neither caller's in-memory `quizStamp` field is set anymore,
      so nothing reads a value staler than what the atomic bump already applied.
  - **A third, narrowly-scoped falsification pass (a fresh Opus agent, no inherited context, targeted
    specifically at the abort/ordering mechanism above since it was the one piece no cold agent had yet
    reviewed) found the mechanism above was itself broken in two ways, both confirmed with a temporary
    probe test and both fixed here, each with its own failing-first test:**
    - **The queue never aborted the previous holder — only the immediate path did.** `drainProgressQueue`
      wrote its own controller into the shared ref without aborting whatever was there before it, so a
      tab-hidden write left running when a later queue item started became silently untracked (no longer
      abortable by anything).
    - **The "hold waiters until settle" fix only covered a write that was still merely QUEUED, not one
      already IN FLIGHT.** If the write a newer one superseded was the one currently being sent (e.g.
      `finalizeChallengeSession`'s own flush, sent immediately because the queue was idle when Submit was
      clicked), aborting it resolved its waiters right away — `/complete` could fire before the write that
      actually superseded it had landed, reopening the "server scores from stale state" defect on a
      narrower path.
    - **Fixed by redesigning the shared state as `progressLiveWriteRef` — one record of `{ controller,
      waiters }` for whichever write is currently live, queue-drained or immediate — and a shared
      `supersedeLiveWrite()` helper that both writers call before sending their own request: it aborts
      the current holder and returns its waiters, which the caller merges into its OWN waiters before
      taking over the ref.** Waiters therefore chain forward through any number of supersessions and
      resolve exactly once, when a write finally settles without itself being superseded again. The
      immediate path also gained the same 30-second dead-request timeout the queue already had, closing
      a case where a hung immediate write (with `handleLeaveSession`'s wait dropped into it) could stall
      forever.
    - **Known Limitation, not fixed here — inherent to a client-side-only ordering scheme:** an abort
      only stops the *client* from waiting; it cannot recall bytes a request already sent to the server,
      and the server has no sequence number to resolve two genuinely-simultaneous requests by anything
      other than which one's transaction takes the row lock first. Two writes that are BOTH actually
      in flight to the server at the same moment (not one queued behind the other) are still ordered by
      server arrival, not by which one the client considers "newer." The one case this fix does close
      completely is a write stuck mid-401-refresh-retry, since the abort signal is threaded through the
      retry (`fetchWithAuth`) and a fetch started with an already-aborted signal never sends at all.
      `/auth/refresh` itself is not abortable, and remains a narrow gap within that one case.

- **Challenge Quiz bank concurrency (items 2-4):** Real Spring-proxied, PostgreSQL 18 Testcontainers
  reproductions found three `releaseClaims` faults before the fix: `generateMoreQuestions` held a bank-row
  lock while its `REQUIRES_NEW` release waited for a second connection until PostgreSQL's test-only
  `lock_timeout` returned `55P03`; a failed start committed an unreleased claim on a `FAILED` session;
  and expired `+5` rolled back its forfeit but committed an independent claim release. `c76e5c1d`
  introduced `REQUIRES_NEW` on the mistaken premise that a rolled-back claim write needed a separate
  release. Release is now a count-tolerant bulk `UPDATE` in the caller's transaction: it sees a failed
  start's uncommitted claims, rolls back with a failed `+5`, and does not flush deleted entities at
  commit. The same reproductions pass without a lock wait, orphaned claim, or released claim on rollback.
  Read-only production checks on 2026-09-29 found zero claims owned by non-`IN_PROGRESS` sessions and zero
  idle-in-transaction connections: no live incident or cleanup write is owed.
- **Generation-stamp invalidation:** `V151` adds `study_packs.generation_stamp BIGINT NOT NULL DEFAULT 0`
  and nullable `challenge_quiz_question_bank.generation_stamp`. Both content replacement paths advance
  the pack stamp with the content write and bank delete in one transaction. Generation captures the
  stamp with the summary before the LLM call and passes it to every bank insert path. New-question
  claim and Redo Missed count/claim reads require the current stamp, accepting `NULL` only for bank
  rows that predate the migration; the owning session's release and outcome reads never stamp-filter.
  A two-connection LLM-window reproduction inserted five old-summary rows after invalidation: they
  survived physically but ceased to be claimable after the stamp fix. Existing rows are neither wiped
  nor made unclaimable at deploy. **Audit correction:** the delivered diff also stamp-filtered
  `existsByUserIdAndStudyPackId`, `findOwnerStudyPackPairsByStudyPackIdIn`, and
  `findQuestionKeysByUserIdAndStudyPackId` — these guard a WRITE (skip a re-seed, skip re-copying a
  key the caller already holds) rather than hand out content, so filtering them made a stale-but-present
  row invisible to the guard, letting the Official-template re-seed and adopter-copy paths reuse that
  row's `question_key` and collide with `uq_challenge_quiz_question_bank_user_pack_key` — the same
  rollback-only-transaction failure `docs/features/challenge-quiz.md` already documents for a same-level
  duplicate. Reverted the filter on those three; the two claim/content-serving read paths above are
  unaffected. The concurrency integration test's stale-stamp assertions for these three methods were
  corrected to match (they must still see the stale row, not treat it as absent). **This closes the
  template-copy and re-seed collision paths only — see Known Limitations for what it does not close.**
- **Known limitations (Challenge Quiz bank concurrency):**
  - The plain LLM-generation path (`ChallengeQuizService`'s shortfall call into
    `persistGeneratedQuestions`) still does not check the caller's existing bank keys — including a
    stale-stamped row's key — before inserting; it only dedupes within its own freshly generated batch.
    A stale row now sits unclaimable until the pack's next regeneration (instead of being claimed away
    quickly, as before this fix), so it occupies its key slot longer, raising the odds of hitting the
    same already-documented same-level-duplicate failure. Only `+5`/`generateMoreQuestions` and
    `seedTemplateAsync` can reach this collision: `startSession` holds the pack `FOR UPDATE` across its
    LLM call. A collision rolls back that request and surfaces as HTTP 500; it is not a silent drop.
    Not fixed here; would need the bank's existing
    keys threaded into the LLM dedup set the same way `copyTemplateQuestions` now does.
  - Unfiltering `existsByUserIdAndStudyPackId` trades one race for another: if `seedTemplateAsync` races
    the Official pack's own regeneration and leaves a stale-only template, `exists` now reports `true`
    forever (until that pack regenerates again), so the template is never re-seeded — adopters silently
    fall back to the LLM instead, and `queueBackfill` counts that pack as `skipped`. Accepted as a rare,
    self-resolving trade rather than fixed now.
  - `AdminStudyPackTransactionHelper.regenerateOnePack`'s stamp bump
    (`currentPack.setGenerationStamp(currentPack.getGenerationStamp() + 1)`) is a read-modify-write with
    no `@Version` and no `GENERATING`-status interlock — confirmed by reading
    `StudyPackGenerationContextResolver.assertGenerationReady`, which only checks the multi-program
    Domain Context rule, not note status. A concurrent user-initiated regeneration of the same pack can
    revert the concurrent user regeneration's entire quiz and content, including its stamp, rather
    than merely lose one increment. Pre-existing gap, not introduced by this release; not fixed here.
- **Completion/delete hypothesis resolved without a production fix:** The repository's owning-session
  read currently has `PESSIMISTIC_WRITE`. A first test harness substituted an unlocked query and
  produced the predicted stale-row throw, exposing an unfaithful fixture; the corrected real-query
  PostgreSQL test showed the delete waits for completion to commit. Completion succeeds, then the
  delete commits. The test pins this lock so removing it would fail the race guard.

- **Long Exam answer-key redaction:** `LongExamStartResponse` and `LongExamSessionResponse` now redact
  `correctIndex`, `correctIndices`, `explanation`, `workingSolution`, `acceptableAnswers`, and
  `acceptableAnswerGroups` from every question at every point in a Long Exam session. Responses retain
  `keyConcept` for the domain breakdown, while the stored session quiz remains complete for scoring. No frontend
  change or deploy ordering constraint applies: the existing frontend type already permits a null
  `correctIndex`, its resolver falls back to `-1`, and every Long Exam question component already renders with
  answer reveal disabled, so old and new frontend/backend combinations remain user-visible-behavior compatible.
- **Interview Practice answer-key redaction and answer lock:** `InterviewPracticeStartResponse.question` and
  `InterviewPracticeAnswerResponse.nextQuestion` now redact `correctIndex`, `correctIndices`, `explanation`,
  `workingSolution`, `acceptableAnswers`, and `acceptableAnswerGroups`; the natural-language critique remains
  the reveal for the question just answered. A question locks when its critique is first stored in
  `sessionState.aiFeedback`, rather than when its provisional selection is written: retrying the same choice
  returns the stored critique without another save or LLM call, while changing the choice returns HTTP 409 and
  leaves the first answer intact for a sequential same-index retry. Item 5 additionally closes the
  cross-index concurrent overwrite that could erase that critique. This also closes the unmetered, un-rate-limited critique loop: Interview
  Practice quota is charged once at session start, so repeated answers previously created unlimited additional
  LLM calls inside the same paid session.

  **Concurrency correction in item 5:** the earlier per-index guard stopped sequential same-index
  retries, but did not stop concurrent answers on different indexes from erasing one another. Two
  short locked transactions around the LLM call now recheck and merge fresh state. **Deploy order: either.** The frontend never reads the redacted answer fields and its
  normal flow always advances after a critique, so an old client does not submit a changed answer that the new
  409 guard would reject; old and new frontend/backend combinations remain compatible.
- **Challenge Quiz and Board Exam answer-key redaction:** active-session `ChallengeQuizStartResponse` payloads
  and Challenge Quiz `GenerateMoreChallengeQuizResponse.newQuestions` now redact `correctIndex`,
  `correctIndices`, `explanation`, `workingSolution`, and `acceptableAnswers`, and remove every accepted answer
  from `acceptableAnswerGroups` while preserving its outer length so Enumeration still renders the required
  number of inputs. `ChallengeQuizSessionResponse` now reveals the full unredacted `quiz` only at completion,
  together with `selectedChoices`, `selectedMultiChoices`, `selectedIdentificationAnswers`, and
  `selectedEnumerationAnswers`; both result branches use those completion fields for `QuizAnswerReview`.
  Stored session questions remain complete for scoring and completion review.

  **Deploy order: frontend and backend together.** An old frontend against the new backend would give
  `QuizAnswerReview` the redacted active-session quiz with answer reveal enabled and render every learner
  selection as incorrect, even though server scoring remains correct. The new frontend keeps a compatibility
  fallback to the old active-session fields for an older cached or pre-deploy completion response, so the
  reverse overlap is safe, but it does not make backend-first deployment safe. Run `scripts/check-deploys.sh`
  promptly after this release merges and confirm both Vercel and Render are on the release.
- **Adaptive Practice answer-key redaction, per-answer reveal, and server-owned scoring:** every
  `QuickReviewAdaptiveQuizResponse` now redacts `correctIndex`, `correctIndices`, `explanation`,
  `workingSolution`, and `acceptableAnswers` from unanswered questions and removes accepted-answer content
  from `acceptableAnswerGroups`; questions already answered through the new
  `POST /adaptive-practice/sessions/{sessionId}/answer` endpoint are revealed together with the persisted
  `selectedChoices`/`selectedMultiChoices` maps so a resumed session restores both position and feedback.
  The endpoint locks the session row and each question index on its first persisted selection: an identical
  retry is idempotent and performs no write, while a changed selection returns HTTP 409. MATCHING locks at
  the same per-item index granularity, and MULTI_SELECT uses an explicit Check Answer step so checkbox edits
  remain reversible until submission. The full stored quiz remains unchanged for scoring.

  **Production-data semantics change:** Adaptive Practice completion previously had no server-side selections
  and therefore used the client's `correctAnswers`, `selectedChoices`, and `selectedMultiChoices` as the only
  source for both the stored score and `ConceptHealth`. Completion now ignores those legacy request
  claims and derives both outputs from the server-persisted, locked selections for every session. The legacy
  fields remain accepted for request-shape compatibility; `correctConceptNames` is consulted only for an old
  edge-case row whose stored quiz is empty; a normal session with no stored selections scores zero and cannot
  claim correct concepts. Include this change explicitly in the pre-signoff falsification brief.

  **Deploy order: frontend and backend together; both overlap directions break.** An old frontend against the
  new backend never calls `/answer`, so no selection is ever persisted; completion then treats every question
  as unanswered and calls `recordIncorrectAnswers` for every concept in the quiz — an active miss on each
  concept, not merely a withheld credit, feeding weak-concept selection and `twiceMissedConcepts` for every
  learner who completes a session during the skew window. A new frontend against an old backend receives 404
  from `/answer` and cannot reveal or advance. Run `scripts/check-deploys.sh` promptly after merge and confirm
  both Vercel and Render are on the release.
- **Quick Review answer-key redaction for owners and share recipients:** the widened start/resume response is
  now the page's only pre-completion data source. It supplies current Note metadata and a Study Pack quiz whose
  unanswered items have `correctIndex`, `correctIndices`, `explanation`, `workingSolution`, and accepted-answer
  content redacted. `GET /notes/{id}` and `GET /study-packs/shared/{id}` remain fully unredacted and unchanged. The new
  `POST /quick-review/{sessionId}/answer` endpoint locks the session row, reveals one stored Study Pack question,
  treats the same selection in the same attempt as idempotent, and returns HTTP 409 for a changed selection.
  MATCHING keeps its whole-group reveal while recording each item independently; MULTI_SELECT remains editable
  until Submit. The cumulative selection maps retain the latest accepted answer for completion and mastery,
  while attempt-bucketed maps enforce separate INITIAL and RETRY locks.

  `/answer` also advances the stored `retryCount` to the client's requested attempt, bounded to `0` or `1`, so
  a lost best-effort retry-transition `/progress` write cannot leave a RETRY answer colliding with the INITIAL
  lock bucket. `/progress` now accepts only `retryQuestionIndexes` and `activeQuestionIndexes` from the client,
  preserves all four server-owned answer maps, and rejects retry-count or round regression. Sessions created
  before the Note's latest `generationEnqueuedAt` are stale: start forfeits and replaces them, resume treats them
  as absent, and `/answer` returns a distinct restartable error. The unscoped Note read happens only after the
  existing owner-or-live-share authorization, so the same staleness and title behavior applies to recipients.

  Removing the owner-only `getNote` preflight also fixes recipient entry through Dashboard-shaped
  `/notes/{id}/quick-review` links and due-concepts-digest links, in addition to the explicit shared-note link;
  `isOwner` from the authorized response now selects the valid Note-detail destination. Production currently has
  zero recipient Quick Review sessions and zero live `note_shares` rows, so these recipient paths ship before
  real usage has exercised the Dashboard in-progress reader, `ConceptHealth`, or mastery-unlock analytics for a
  recipient.

  **Deploy order: frontend and backend together.** A new frontend against an old backend gets 404 from
  `/answer`. An old frontend against the new backend never calls `/answer`, while narrowed `/progress` discards
  the four answer-bearing keys it still sends. With no stored selections,
  `computeConceptBreakdownForStoredSelections` returns an empty list; both `recordCorrectAnswers` and
  `recordIncorrectAnswers` short-circuit, `verifiedCorrectAnswers` stays unset, and `verifiedPerfect` is always
  false. This is a silent non-unlock rather than an active miss, but Quick Review's traffic makes it the
  largest-reach deploy-skew window among the six answer-key PRs. Run `scripts/check-deploys.sh` promptly after
  merge and confirm both frontend and backend are on the release.

## v0.162.0 - Say the Value

**Status: Released** (signed off 2026-09-27; PRs #1455/#1456/#1457/#1458 merged into the release branch; release PR merged to `main` as #1459 and tagged 2026-09-27; deploy verified on both platforms — Render live `c3e4deaa` at 14:21:57Z, Vercel matched at 14:25:36Z)

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
   `docs/claude-plans/done/2026-09-27-h5-before-after-sample-review.md`. Prompt-only change; no schema, no parser, no
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

- **Phase A0 — `ADR-002` ratified.** Status `PROPOSED` → `ACCEPTED`; its own open question (does `board-exam-developer.txt` inherit the letter contract from `schema.json` alone?) resolved by direct grep, not inference, and the resolution written back into the ADR itself. H6's implementation explicitly NOT scoped into this release — see the correction banner above.
- **Phase A (H5) — PR #1455, merged `e0037692`.** All six quiz-prompt files now require an explanation to state a numeric-only MCQ's exact value, still forbidding any letter reference; prose-answer MCQs (98.3% of the corpus) unchanged, an owner decision made after reading the real numeric/prose split. Pre-deploy coverage-ratio baseline read (60/88, 68.2%); post-deploy read minted as a `[CHECKPOINT]` in `ROADMAP.md`'s Backlog Index. Before/after sample review run against the real OpenAI endpoint before merge, per the incident doc's own locked gate.
- **Phase C — PR #1456, merged `9ac11bff`.** `ChallengeQuizQuestionBankService.invalidateForStudyPack` closes the Challenge-bank leg of the derived-artifacts invalidation defect class (`ROADMAP.md`'s "Derived artifacts keyed on the preserved `study_packs.id`" row, both legs now closed). Wired into exactly two of the exam-pool fix's three call sites, not a blind structural copy. A scoped Opus falsification pass on this diff (before merge) found and the release documented rather than fixed: a `generateMoreQuestions` race that can let a narrow window of stale-content rows survive a regeneration, and a pre-existing `releaseClaims` hang (`v0.60.2`) whose blast radius this fix widens — both logged as their own Backlog rows, production verified to have zero configured lock timeouts and no evidence the hang has ever fired.
- **Phase D — PR #1457, merged `cf91ce5a`.** Question Quality Tier 3 audit: 65 real production questions read by hand across 2 of 4 quiz stores, 0 confirmed genuine-ambiguity defects, honest statistical reading (rules out a common defect, not a rare one), recommendation to build a learner "flag this question" affordance before an automated semantic gate. Ships no code, per its own scope. Full document: `docs/claude-plans/2026-09-27-question-quality-tier3-audit.md`.
- **H4 sign-conflation fix — PR #1458, merged `e3625535`.** Found by a scoped Opus falsification pass run at signoff, against the actual merged release state (`cf91ce5a`), not any individual PR's own diff. `QuizValidationUtils`'s exact-value match treated `"0.40"` as present inside evidence text `"-0.40"` — the minus sign was invisible to the pattern — which could mask a real answer/explanation mismatch for any difference-type numeric question (discrimination index, net change, signed error). Pre-existing since `v0.155.0`'s H4, not introduced by H5, but H5 (this same release) makes it more reachable by requiring explanations to state a value at all. Fixed and mutation-verified (reverted the fix, confirmed the new test fails against pre-fix code, restored it).
- **Signoff falsification pass, full report folded into the rows above and into `docs/product/ROADMAP.md`'s Backlog Index** rather than repeated here. Two additional findings, both documented as Known Limitations / Backlog rows, neither blocking: the coverage-ratio metric's denominator (H4's `isNumericUnitLiteral`, ≤20 chars + a digit) is slightly wider than H5's own numeric-condition wording ("not a phrase"), so the ratio cannot reach 100% by design — the post-deploy checkpoint read should say so rather than read a sub-100% result as a defect; and a low-severity, genuinely uncertain race between a Challenge session completing and a concurrent regeneration's bank delete, needing a two-connection Postgres test to resolve, not reproduced.

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
