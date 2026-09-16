# RELEASES.md - NoteLib

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
