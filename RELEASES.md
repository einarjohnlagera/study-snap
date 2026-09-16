# RELEASES.md - NoteLib

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

## v0.149.0 - Precision Before Coverage

**Status: Released**

Theme: two new Program Family shortcuts for curators (Health Sciences, Accounting), built on the
existing generic family mechanism, plus the admin capability and legacy-catalog cleanup needed to
maintain families going forward without another release.

Source: `docs/claude-plans/program-family-health-accounting-expansion-final-plan.md` (FINAL, Product
UX-approved, tightening pass 2 of 2; untracked on disk, indexed in `ROADMAP.md`'s Backlog Index).
Supersedes `docs/claude-plans/program-family-health-accounting-expansion-product-ux-consultation-prompt.md`
(pass 1) — that file's facts are preserved as historical trace only; do not re-read it for anything
load-bearing.

### Planned Scope

- **`is_active` lifecycle column on `course_programs` (backend, migration).** `course_programs` has no
  lifecycle field today (`id, name, program_family_id, exam_goal_slug, created_at` only — confirmed
  against current migrations at kickoff). Adds `is_active BOOLEAN NOT NULL DEFAULT TRUE`, reusing the
  existing `discount_vouchers`/`quiz_share_links` convention rather than inventing a new one (confirmed:
  both already carry `is_active BOOLEAN NOT NULL DEFAULT TRUE`). This migration adds the column ONLY.
  **⚠️ ANTI-DRIFT: do NOT bundle the 2-row backfill (below) into this same migration** — see the
  deployment-ordering item.
- **Legacy fused catalog rows deprecated from new authoring, not deleted (backend, follow-up
  step).** "Nursing · Medicine" (20 notes) and "Nursing · Pharmacy" (1 note) get `is_active = false`,
  targeted by name (not id, since ids are runtime-generated). **A SEPARATE step from the column-add
  migration, deployed only after Health Sciences family population is confirmed live in production** —
  bundling them would strand curators between losing the fused shortcut and gaining its replacement.
  Existing Notes referencing these rows are never touched; `course_programs` rows are never deleted.
- **`PATCH /course-program-catalog/{id}` — new ADMIN-only endpoint (backend).** No endpoint exists today
  to reassign an *existing* catalog program's family (confirmed: `CourseProgramCatalogController` is
  GET-only at kickoff). `UpdateCourseProgramCatalogRequest(UUID programFamilyId)` — nullable;
  `null` clears membership, a valid id sets/changes it. `@PreAuthorize("hasRole('ADMIN')")`, mirroring
  `NoteCollectionController`'s existing `PATCH /{id}` convention. Real `MockMvc` request test with
  `Content-Type: application/json` required (not a bare service-method call — this repo's own `v0.119.0`
  lesson), plus ADMIN-only guard, assign/change/clear, unknown-program, unknown-family cases.
- **List-endpoint filtering (backend).** The authoring combobox's pickable list excludes `is_active =
  false` rows; a Note that already references an inactive row must still resolve and render it as a
  normal chip (fetch selected-by-id regardless of `is_active`, filter only the *pickable* list). **⚠️
  Confirm which endpoint the admin catalog management screen uses and keep that one unfiltered** —
  filtering the wrong list would hide an inactive row from the one screen meant to manage it.
- **Family-chip UX redesign (frontend, `applicable-programs-combobox.tsx`).** Replaces full-sentence
  "Add all N programs" buttons with compact states: none selected `Family · N`; partial
  `Family · N remaining` (click adds only the missing ones); full `✓ Family · N` — **LOCKED, inert,
  non-interactive**, status text or a disabled element rather than a clickable toggle (`aria-pressed`
  would misrepresent state, since the Note never persists "family selected" — only individual programs
  do); removing a member after full immediately reverts to partial. **⚠️ ANTI-DRIFT: do NOT touch
  `availableProgramFamilies`/`handleFamilyExpansion`** — already family-count-agnostic, confirmed
  unchanged at kickoff. 18+ selected-program mobile wrapping gets an explicit acceptance check at
  implementation time (render Engineering's 18 at desktop + 375px mobile width; pass/fail criteria in
  the plan's §K) rather than a pre-guessed threshold fix.
- **Health Sciences program family (data + existing mechanism, no new schema).** Nursing, Medicine,
  Pharmacy — **LOCKED per Product UX, not reopened this release**. Evidence: 18 notes carrying a genuine
  3-way tag accumulated across five weeks, plus 21 more notes across the two fused rows above — two
  independent curator actions converging on the same trio. Physical Therapy and Radiologic Technology
  excluded (no comparable co-selection evidence).
- **Accounting program family (data + existing mechanism, no new schema).** Accountancy, Management
  Accounting, Accounting Information Systems, Internal Auditing (4 members) — decided from the CPALE
  curriculum's own subject-plan structure (Management Services and Auditing sections are, by curriculum
  design, shared core coursework for these four program types), not from the 35-note bulk-tagging action
  (which proves curators need cross-program bulk assignment as a workflow, not that all 10 originally
  bulk-tagged programs belong in one family). **Business Administration explicitly EXCLUDED** — its
  genuine overlap is concentrated in RFBT + finance-flavored Management Services content, ~22% of the
  CPALE plan, which fails the family's own precision bar (a program that needs manual removal on ~78%
  of uses isn't a good default). Entrepreneurship, Economics, Senior High–ABM, Real Estate Management,
  CMA, CFA all excluded for the release too (thin or no signal, or — for CMA/CFA — an unresolved
  credential-vs-program taxonomy question, not evidence against inclusion). **⚠️ Flagged for the owner,
  not blocking kickoff: the Business Administration exclusion rests on external domain knowledge about
  how Philippine accounting-track programs share curricula, not a row-level fact the database can
  confirm — worth a sanity check before this family ships.**
- **`docs/features/program-families.md` updated (docs).** Documents both new families, the `is_active`
  mechanism, and the reverse Domain-Context guard already noted in the pass-1 audit.
- **One low-risk copy unification (frontend).** `bulk-generation-page-client.tsx`'s Domain Context
  helper text ("Required when this note applies to more than one program") is looser than the other two
  surfaces' phrasing ("Needed before you can generate a Study Pack for a note in more than one program")
  — unified to match. **No fix for a suspected Domain Context UI phrasing bug from the pass-1 audit** —
  it was not reproduced this pass; do not implement a fix for a defect that isn't there.

**Explicitly out of scope, not folded in:**
- Finance as a Course/Program is deferred to CPALE curation itself — the plan's own trigger (the first
  canonical Finance-applicable note) looks likely to fire soon (16 of 359 planned CPALE notes are
  textbook Finance/Financial-Management topics) but has not fired yet. Finance Domain Context: still NO.
- The CPALE curriculum TSV's `applicable_programs` column is uniformly under-tagged (`Accountancy` on
  all 359 rows, zero cross-program exceptions), and several RFBT titles bake "Accountancy"/"Business Law"
  into the title text itself (a Note Title doctrine violation). Both are real findings surfaced while
  reading the plan, both are curriculum-content issues belonging to the curriculum strategist pipeline,
  and neither is fixed by this release — flagged to the owner separately.
- Legacy-Note normalization for the 21 notes already carrying a fused-row tag: deferred/follow-up, not
  this release.
- Program Family overlap (one nullable FK, single family per program) stays unsupported — a known,
  recorded limitation, no schema change here.

Anti-drift (whole release): Program Family stays authoring convenience only — never persisted on Note
generation payloads, never a discovery axis, never sent to generation, never changes Domain Context or
Authored Depth, never inferred from Review Set, never retroactively synced onto existing Notes (nothing
stores which family a Note's programs came from, so this is structurally impossible, not just a rule —
a focused repository test should confirm `note_course_program` rows are never written with a
family-derived value). Family selection stays purely additive. **Data operations — assigning the 7
catalog rows to their new families via the new endpoint, and the later `is_active` backfill — are
owner-run post-deploy, sequenced per the plan's §O, not part of this release's code diff.**

**Routing: Codex** — this touches a backend migration, a new endpoint, and a frontend redesign together,
per `CLAUDE.md`'s task-routing table. Implementation slices per the plan's §Q: (1) backend catalog
lifecycle + family-reassignment API, (2) frontend family-chip redesign, (3) docs update — one Codex
prompt or two, decided at prompt-writing time. **Verification tier: at minimum one scoped cold agent,
falsification-framed** — re-decide once the actual diff exists. Three triggers already fire at kickoff:
this adds an ADMIN-only mutation endpoint with no prior tests to anchor against; the new lifecycle
column is consumed by two different list paths where filtering the wrong one hides inactive rows from
the admin screen meant to manage them; and the Business Administration exclusion is the plan's own
flagged external-domain-knowledge conclusion, not a verifiable row. Full scope, evidence, and the
plan's complete decision block are in
`docs/claude-plans/program-family-health-accounting-expansion-final-plan.md`.

### Shipped

- Added `course_programs.is_active` through additive migration V145, defaulting every existing and future
  catalog row to active. The migration contains no fused-row retirement backfill; that remains a gated,
  owner-run follow-up after Health Sciences is populated.
- Added the ADMIN-only `PATCH /course-program-catalog/{id}` endpoint for assigning, changing, and clearing
  an existing program's family. Missing or malformed program ids share `404 COURSE_PROGRAM_NOT_FOUND`;
  an unknown submitted family remains `400 UNKNOWN_PROGRAM_FAMILY`.
- Kept the shared catalog list unfiltered for both admin management and authoring fetches. The authoring
  combobox alone excludes inactive programs from new individual selection and family expansion while
  preserving inactive programs that an existing Note already selected.
- Reworked family shortcuts into visible none, partial, and full states (`Family · N`, `Family · N
  remaining`, `✓ Family · N`), with the full state accessible and inert, and aligned Bulk Generate's
  Domain Context helper copy with the other authoring surfaces.
- **⚠️ CORRECTED AT AUDIT — the original text here claimed a browser check "passed at 1440×900" and
  measured specific pixel/coordinate values (a 502px chip row, Save visible at y=550–590) at 375×812.
  No headless-browser or screenshot tool exists in this repo or in the Codex/Claude environments that
  built and reviewed this release, so those coordinates could not have come from an actual render —
  the plan's own §K explicitly warned against exactly this failure mode ("do not invent a threshold
  without looking").** What actually shipped: a mobile-only collapse to 8 visible chips past that
  count, with an accessible "Show all N selected programs" toggle exposing every remove action,
  built as a judgment call (18 unwrapped chips plus their own labels is a lot of vertical space on a
  375px-wide screen) rather than a verified measurement. The acceptance check in the plan's §K has
  **not actually been run** — flagged here rather than left standing as a false "passed" claim; a
  real device/viewport check before this ships to production would confirm or correct this.
- **Post-merge cold-agent falsification pass on the actual shipped diff** (PR #1399, commit `2ad837d6`)
  re-ran both full test suites directly (not trusted from the PR's own report — genuine pass, 31 backend
  + 26 frontend tests targeted at this change) and checked 8 specific claims against real code. One more
  real finding: `docs/features/program-families.md` overclaimed that inactive programs "do not appear in
  individual suggestions" — true only for the Applicable Programs axis (`applicable-programs-combobox.tsx`);
  the separate, legacy singular `courseProgram` free-text suggestion list (`use-course-program-catalog.ts`
  → `course-program-combobox.tsx`, used on onboarding/profile/both note surfaces) is untouched and still
  offers a retired program's name — a pre-existing gap this release did not widen (that field already
  accepted arbitrary free text), not fixed here, corrected in the doc to state its actual scope. Also
  added one "Known limitations" line (a fully-selected family's inert chip count can shrink silently if a
  member is later retired — unreachable today, noted for the future) and a fifth doc file,
  `docs/claude-plans/v0.149.0-program-family-data-ops-handoff.md`, giving the owner the exact API calls
  and verified production catalog ids for the two families and 7 assignments, sequenced per plan §O.
  Everything else the pass checked held: the shared-endpoint filtering scope, already-selected-inactive
  chips resolving correctly end to end, `CourseProgramNotFoundException`/`UnknownCourseProgramException`
  staying genuinely separate (4 untouched pre-existing call sites), and the update endpoint's two-read
  transaction being race-safe by construction (Postgres row-lock + MVCC, not luck).
- **The mobile-collapse UI (above) was removed, not left as an owed check.** Reading the actual layout
  of all four consumers (`note-editor-form.tsx`, `private-note-detail-page-client.tsx`'s inline panel,
  `bulk-generation-page-client.tsx`, and `admin-applicable-programs-section.tsx` via `AppModal`) found
  it solves a problem that cannot occur in any of them: three sit in ordinary page flow, where scrolling
  to a Save button below a tall chip row is normal mobile behavior; the fourth renders inside
  `AppModal`, whose `flex-1 overflow-y-auto` content region plus `shrink-0` actions row (`app-modal.tsx`)
  already guarantees the actions stay visible regardless of content height — a deterministic CSS
  property, not a guess, though still not the same as an actual device render. `MOBILE_SELECTED_PROGRAM_LIMIT`,
  the `matchMedia` viewport listener, and the "Show all N" toggle were removed; every selected program
  now renders unconditionally at any width, which is the `NO CHANGE — CURRENT WRAPPING ACCEPTABLE`
  outcome the plan's own §K asked for if the check passed, arrived at by reading the layout architecture
  rather than by measuring a screenshot. `tsc --noEmit`, the full frontend suite, and lint all re-verified
  clean after the removal — this pass also fixed one unrelated, pre-existing TypeScript compile error in
  the same test file (a fixture cast that needed to go through `unknown` first) that had shipped in PR
  #1399 uncaught, since neither the pre-merge audit nor the post-merge cold agent had run `tsc --noEmit`.
- Added migration, repository, service, real-request controller, and component coverage for lifecycle
  defaults, joined row mapping, family reassignment and clearing, endpoint errors and authorization,
  inactive candidates, and all family-chip states.

---

## v0.148.0 - Say What You Mean

**Status: Released**

Theme: two small, unrelated correctness fixes — a keyword scan that quietly misjudges what content
needs computation guidance, and a reminder email that quietly always arrives on the same day.

### Planned Scope

- **`QUANTITATIVE_KEYWORDS` substring-anchoring fix (backend).** `isQuantitativeContext`
  (`OpenAiLlmStudyPackService.java:1649`) uses plain `String.contains` for all 50 keywords, so several
  match as embedded substrings of unrelated words: `ratio` ⊂ `corporation`/`operations`/`administration`,
  `solve` ⊂ `resolve`, `current` ⊂ `currently`, `interest` ⊂ `interested`. Measured read-only against
  production: ~4,890 notes are currently "quantitative via keywords only." Sampled the flip set:
  genuinely non-computational content (pedagogy, architectural theory, Philippine history, nursing
  practice narratives). **Two amendments found during pre-commit `advisor()` review, both closed in the
  same diff before shipping:**
  - **Nursing/Accountancy regression** (also independently found by the earlier cold-agent falsification
    pass): anchoring alone would have declassified `domain_context IS NULL` Nursing/Accountancy content
    that reaches `quantitative=true` today only via this same accidental substring match. Re-measured
    with `course_program` joined into the haystack (the original estimate omitted it): of the flip set,
    466 notes are rescued by two new unanchored `QUANTITATIVE_KEYWORDS` entries, `nursing` and
    `accountancy` (both safe standalone words, no substring hazard) — higher coverage than the original
    ~370-note estimate, not lower. `pharmacokinetic` (added `v0.145.0`) stays deliberately unanchored —
    its match depends on unanchored substring matching, and the code comment explaining this was
    rewritten so a future session doesn't "fix" it into breaking.
  - **Inflection gap:** a bare `\bkeyword\b` doesn't match a keyword's own plural/verb forms —
    `\bratio\b` fails on "financial ratios," `\bsolve\b` fails on "solving." Of the flip set, 28% (291 of
    1,045 remaining after the nursing/accountancy rescue) triggered ONLY on one of these inflected forms
    — genuinely quantitative content the anchoring fix would otherwise have wrongly declassified. Each of
    the 7 anchored patterns now also accepts its plain plural/verb inflections (`ratio(s)?`,
    `solv(e|es|ed|ing)`, `current(s)?`, `interest(s)?`, `integral(s)?`, `balance(s)?`) without reopening
    any substring hazard the anchoring closed — e.g. `interest(s)?` still excludes `interested`/
    `interesting` since the boundary is enforced after the optional `s`, not mid-word.
  - **Final measured flip count, with course_program in the haystack and both amendments applied: 754
    notes** (down from the original, narrower estimate of ~1,520-1,586 — the original haystack omitted
    course_program and the original anchoring omitted inflections, both of which this diff corrects
    before shipping, not after).
  - **Which 7 keywords get anchored:** `ratio`, `solve`, `current`, `interest`, `integral`, `balance`,
    `units`. The other 44 (including the 2 new ones and `pharmacokinetic`) keep plain `contains`.
  - **Anti-drift:** no resolver rewrite — same haystack construction, same
    `domainContext().isQuantitative()` short-circuit, same overall function shape; anchoring is a second,
    additive matching branch for a fixed subset of keywords, not a semantic overhaul of the scan.
  - **Test owed:** `OpenAiLlmStudyPackServiceTest` gains cases proving the anchored path isn't a no-op (a
    haystack containing only `corporation` → not quantitative; one containing `current ratio` → still
    quantitative), that the plural/verb inflections match on their own, and that the nursing/accountancy
    rescue works via `courseProgram` (the field production actually uses, not just `subject`) — plus
    confirms the existing `pharmacokinetic` test still passes as the canary.
  - `docs/features/study-pack-generation.md` updated to describe the anchoring split and the
    `nursing`/`accountancy` false-negative repair, matching how it already documents `pharmacokinetic`.

- **Due-concepts-digest day-of-week clustering fix (backend).** `RetentionService.isEligibleReviewDay`
  returns `true` unconditionally for the 143 users with `review_days IS NULL`, so they're checked every
  day the digest job runs and gated only by a flat 7-day cooldown — which locks them onto whichever
  weekday they first landed on, forever. Measured read-only against production (Asia/Manila, the job's
  actual `EMAIL_BUDGET_ZONE`): Mon 107, Tue 101, Wed 98 vs. Thu 9, Fri 8, Sun 2 over 28 days — a real,
  confirmed 3-day cluster. **Amendment from a cold-agent falsification pass, correcting two claims from
  this release's own scoping:** (1) the originally-claimed "3.5x peak reduction" was a unit error
  (compared users-per-bucket to sends-per-week); the real, reproduced improvement is **1.5x** peak-day
  reduction (26.8 → 18.0 sends/week on the worst day) — a burstiness improvement, not a dramatic fix. (2)
  This is **not** a live email-cap breach fix — `dispatchDueConceptsDigestEmails` never consumes the
  `EMAIL_DAILY_LIMIT` budget (confirmed unbudgeted), and `sendDailyEmails()` (the budgeted path) runs
  before it in the daily job, so same-day collision with the 100/day cap cannot occur the way the
  original finding implied. Framed correctly here as: smooths an already-unbounded channel's shape for
  143 users, not a breach fix.
  - **Fix:** for null-`review_days` users, `isEligibleReviewDay` gets a deterministic default day —
    `Math.floorMod(user.getId().hashCode(), 7)` compared against today's `DayOfWeek` — instead of "any
    day." `dueConceptsDigestCooldownDays`'s null-branch changes from the global 7-day config to
    **6 days** (not the committed-user value of 1, per the falsification pass's transition-week
    counterexample below). Purely computed at read time from the existing `id` column — no new column,
    no migration, no backfill, no write to `review_days`.
  - **Anti-drift, from the falsification pass:** cooldown must be **6**, not 1 — with the day-gate
    providing weekly cadence, 6 days never blocks an on-rhythm send, and it makes a sub-7-day
    double-send during the transition week impossible (a cooldown of 1 was shown to produce two digests
    2 days apart for a concrete example user). Must use `Math.floorMod`, not `%` — `UUID.hashCode()` can
    be negative.
  - **Known limitation, stated rather than silently accepted:** a user whose last digest landed close to
    their newly-assigned day may still see one earlier-than-usual digest in the first week after deploy
    (a bounded, one-time transition effect, not an ongoing issue).
  - **Uses `dispatchDay.getValue() - 1`, not `.ordinal()`**, to compare against the hash bucket — same
    result, but pinned to `DayOfWeek`'s documented numbering rather than enum ordinal position.
  - **`StudySnapProperties.Retention.dueConceptsDigestCooldownDays` (default 7) is removed**, not left
    orphaned — it had no `application.yaml` key and, after this fix, no remaining reader; the uncommitted
    cooldown is now the compile-time constant `UNCOMMITTED_DUE_CONCEPTS_DIGEST_COOLDOWN_DAYS = 6`, a
    deliberate choice (it has no legitimate reason to vary per deployment) rather than an oversight.
  - **Feature docs updated to match**, not just `RELEASES.md`: `docs/features/retention-emails.md` (the
    null/empty `review_days` cadence description and the cooldown table), `docs/features/quiz.md` (its
    "null/empty review days preserve the pre-`v0.72.0` cadence" line was the exact claim this fix makes
    false), and `docs/features/email-preferences.md` (the settings-page cooldown description). Frontend
    review-days copy (`app/settings/page.tsx`, `review-commitment-prompt.tsx`) was swept and found already
    accurate — neither promises "every day" or "whenever due," so neither needed a change.
  - **Tests owed:** `RetentionServiceTest`'s null/empty-`review_days` tests are rewritten for the new
    behavior (was: "always eligible"; now: eligible only on a deterministic hash-assigned day, with a new
    negative-case test proving the day-gate actually excludes a mismatched day) rather than merely
    adjusted, since the old assertion is no longer true. `RetentionEmailScheduler`'s and
    `RetentionEmailSchedulerTest`'s existing "7-day cooldown" comments/assertions are updated to describe
    the new day-gate + 6-day cooldown behavior.

Anti-drift (both items): no database migration, no new endpoint, no persisted state change for either
fix — both are pure logic changes computed at read/generation time. Routing: Claude Code implements
directly (isolated bug fixes with a clear root cause each — Item 1 touches 1 production file, Item 2
touches 3: `RetentionService.java`, `RetentionEmailScheduler.java`, and the `StudySnapProperties.java`
config-field removal). **Verification tier: one `advisor()` call** on the diff for each item — no
auth/quota/money/production-data semantics change for either, and both were already pressure-tested
pre-implementation by a cold Opus agent during scoping (falsification-framed against the specific claims
above), which is why a heavier post-implementation tier isn't warranted.

### Shipped

- **`QUANTITATIVE_KEYWORDS` substring-anchoring fix** — PR #1396, merged `5657d8fd` into
  `releases/v0.148.0`. `OpenAiLlmStudyPackService.java:193-199,1693-1697` (word-boundary anchoring for
  7 keywords, each with plural/verb inflections), `:176-184` (`nursing`/`accountancy` added unanchored).
  `OpenAiLlmStudyPackServiceTest` gained 5 guard tests. `docs/features/study-pack-generation.md` and
  `docs/gpt-contexts/REVIEW_SET_SHAPING_CONTEXT.md` updated. Full backend suite green.
- **Due-concepts-digest day-of-week clustering fix** — PR #1397, merged `36b08fd3` into
  `releases/v0.148.0`. `RetentionService.java:414` (`isEligibleReviewDay`), `:58,422`
  (`UNCOMMITTED_DUE_CONCEPTS_DIGEST_COOLDOWN_DAYS = 6`), `RetentionEmailScheduler.java` comment update,
  `StudySnapProperties.java` (`dueConceptsDigestCooldownDays` removed, now unused). `RetentionServiceTest`
  rewritten for the new null/empty-`review_days` behavior including a negative-case guard.
  `docs/features/retention-emails.md`, `quiz.md`, `email-preferences.md` updated; frontend review-days
  copy swept and found already accurate. `[CHECKPOINT — due 2026-10-06]` added to `ROADMAP.md`'s Backlog
  Index — the projected 1.5x peak-day reduction is a simulation, not yet observed post-deploy.

---

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

