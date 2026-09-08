# RELEASES.md - NoteLib

## v0.133.0 - Education Family

**Status: Released** (kicked off 2026-09-08, signed off 2026-09-08, base branch `releases/v0.133.0`, cut from `main` after `v0.132.0` merged as #1347 and tagged)

Source: `docs/claude-plans/program-family-generalization-and-education-family.md` (audit complete 2026-09-08, every claim `file:line`-anchored).

### ⚠️⚠️ THE BRIEF'S CENTRAL PREMISE IS FALSE, AND THAT IS THE MOST IMPORTANT THING IN THIS SECTION

**There is no Engineering-specific authoring shortcut, so there is no generalization to build.** The button at `applicable-programs-combobox.tsx:290` interpolates the family name and count, and the families are derived **dynamically from the catalog** (`:88-111`) by mapping each program's `programFamilyId`/`programFamilyName`. **There is no Engineering literal anywhere in it.** It reads *"Add all 18 Engineering programs"* only because **Engineering is the only family that has members**.

**So this release is a DATA change plus one admin form field. Seed the Education family and the existing UI renders *"Add all 8 Education programs"* with no code change.** §6 of the brief is already satisfied too — all four authoring surfaces share that one component.

**⚠️ DO NOT MODIFY THE APPLICABLE-PROGRAMS COMBOBOX. If a diff touches it, the scope has drifted.**

### The two gaps the audit named — ⚠️ ONLY ONE OF THEM IS REAL (see below)

| Gap | Detail |
|---|---|
| Families can only be created by migration | Zero `ProgramFamilyEntity` construction or `save` anywhere in the backend |
| The admin UI cannot assign a family | `POST /course-programs` **already accepts and validates** `programFamilyId` (`CourseProgramCatalogService:54-56`, `UnknownProgramFamilyException`) — `app/admin/course-programs/page.tsx` just exposes no field for it |

### Planned scope

1. **One migration** — insert the `Education` family; assign the **existing** `Education` program row to it; **RENAME** `Special Needs Education – Generalist` → `Special Needs Education` keeping its `id`; insert the remaining programs from the audit's §4 (Elementary Education, Secondary Education, Early Childhood Education, Technical-Vocational Teacher Education, Physical Education, Teacher Certification).
2. **Admin family selector on create** — the endpoint already supports it. **This is what stops the release recurring**: without it, every future family member is another migration.
3. **Copy polish** on the Course/Program and Domain Context helper text. **⚠️ Drop resolver mechanics from it** — keep the conceptual separation, do not explain the backend.

### ⚠️⚠️ THE MIGRATION SEED IS NOT THE LIVE CATALOG — THE READ-ONLY AUDIT IS A PRECONDITION, NOT A FORMALITY

`V106` seeds **three** Engineering programs; production reportedly has **18**, and no later migration inserts any. **The catalog has been extended through `POST /course-programs` in production, so a duplicate audit from the repo alone is UNSOUND.** Run `docs/claude-plans/v0.133.0-education-family-precondition-read.sql` (read-only) **before writing the migration**. An inserted duplicate is visible to every curator immediately and is awkward to withdraw once Notes reference it.

**⚠️ `GET /course-programs/similar?name=` already exists** (ADMIN-only) — the duplicate check the brief asks for is already built; the admin create flow should use it per name.

### ⚠️ What the rename touches

**Safe:** `note_course_program` joins by `course_program_id` (`V107:4`), so every Note keeps its link through a rename — the row keeps its `id`, only `name` changes.

**⚠️ NOT safe automatically: five free-text `course_program` columns** (`notes`, `note_collections`, `users`, `bulk_generation_result`, `official_study_plan_wishlist` — the last has `normalized_course_program` too) may hold the literal old string and would silently keep it. No repository method resolves a catalog entry by name, so nothing breaks — but a stale string stops matching the catalog, which affects discovery and wishlist normalization. **The precondition read counts these.**

### ✅ PRECONDITION READ RUN 2026-09-08 — RESULTS, AND ONE CONDITION NOT MET

Run read-only against `notelib-db-prod`. **The audit's central warning is now VERIFIED, not assumed: `program_families` shows Engineering with EIGHTEEN programs against `V106`'s THREE seeds**, so the catalog was indeed extended through the API and a repo-only duplicate audit would have been unsound.

| Check | Result |
|---|---|
| Education-adjacent catalog rows | **Only two** — `Education` (`exam_goal_slug='let'`, no family) and `Special Needs Education – Generalist` (no slug, no family) |
| Families | **One** — `Engineering`, 18 programs. `Education` does not exist as a family |
| Collisions with the six proposed inserts | **None.** Elementary, Secondary, Early Childhood, Technical-Vocational Teacher, Physical Education and Teacher Certification are all clear |
| Notes linked to the rename target via `note_course_program` | **ZERO** |
| Free-text `course_program` hits | **ONE** — `users.course_program`, exactly `Special Needs Education – Generalist` (36 chars, en dash) |

**⚠️ OWNER DECISION 1'S CONDITION IS NOT MET.** The rename was settled *conditional on zero free-text hits*; there is one. **⚠️ NOTHING BREAKS** — `users.course_program` is consumed by `StudyPackGenerationContextResolver` as **free text**, never resolved against the catalog by name — but that one account's profile program would stop corresponding to a catalog entry. **Handed over as an owner-run write in `docs/claude-plans/v0.133.0-owner-profile-string-update.sql`** with the expected row count and before/after verification. Three options are stated there; the recommendation is to run it **in the same maintenance step as the migration, not before it**.

**⚠️ CONSEQUENCE FOR THE RENAME GUARD TEST: its production denominator is ZERO.** No Note is linked to the row being renamed, so the guard is a purely synthetic structural test. **Write it anyway — it is the regression guard for the migration — but do NOT record it as evidence that real data survived**, because there is no real data to survive.

**✅ DECISIONS 2 AND 3 SETTLED 2026-09-08 (owner):** new Education programs carry `exam_goal_slug = 'let'` **only where learners genuinely sit the LET**, never as a family proxy; and **the admin family selector ships in this release.**

### Owner decisions

1. ~~Reuse or rename `Special Needs Education – Generalist`?~~ **SETTLED 2026-09-08: RENAME**, keeping the row's `id`. **⚠️ THE CONDITION WAS NOT MET — the read returned ONE hit in `users.course_program`.** The catalog rename still proceeds; the one stale profile string is handed over as an owner-run write (`docs/claude-plans/v0.133.0-owner-profile-string-update.sql`), and option (c) there reopens this decision if the owner prefers.
2. **✅ SETTLED 2026-09-08 — `exam_goal_slug = 'let'` only where learners genuinely sit the LET.** Original recommendation, accepted: **only where learners genuinely sit the LET**; leave NULL otherwise rather than making the exam goal a family proxy. `Education` already carries `'let'`.
3. **✅ SETTLED 2026-09-08 — the admin family selector SHIPS IN THIS RELEASE.**

### Verification tier

**A single `advisor()` call**, per the audit's §9 — a data migration plus one admin form field. No permission substrate, no cross-user read, no money semantics, no learner-facing behaviour change. **⚠️ But it writes to a production catalog table, so the precondition read is the gate.** **⚠️ THE `v0.131.0`/`v0.132.0` LESSON STILL APPLIES: decide the tier from the SHAPE of the change, and re-decide if the shape changes** — `v0.132.0` was tiered at one cold agent, ran two, and they found four blocking defects including one that silently emptied a learner's adopted Goal.

### ⚠️ THE AUDIT'S SECOND "REAL GAP" IS ALSO FALSE — THE ADMIN FAMILY SELECTOR ALREADY SHIPPED

The audit named two real gaps. **One of them is not real.** It claimed *"the admin UI cannot assign a family — `frontend/app/admin/course-programs/page.tsx` exposes no family field."* That is literally true of `page.tsx`, which is a 40-line shell — **but the field lives in `admin-course-program-catalog-section.tsx`, which that page renders.** It has a `<select id="catalog-program-family">` listing every family derived from the catalog, `create()` sends `programFamilyId`, the table shows a Family column, and its helper text already says assigning a family makes the program participate in that family's expansion.

**It shipped 2026-08-11 in `9e77f412`, tagged `v0.100.0`** — verified as an ancestor of `main`, not merely present in a working tree.

**⚠️ THIS IS THE SAME ERROR CLASS THE AUDIT ITSELF CAUGHT IN THE BRIEF: concluding a capability is missing by reading the wrong file.** Both times the mistake was to check a shell rather than the component doing the work. **So step 3 of the implementation plan is NOT BUILT HERE — it was already done**, and the release shrinks accordingly. The other gap in that table — *families can only be created by migration* — is real, and remains the deferred admin create-family surface.

**⚠️ Do NOT re-add a family selector to the admin page. If a diff adds one, it is a duplicate.**

### Shipped

- **`V142__education_program_family.sql`** — seeds the `Education` family, assigns the EXISTING `Education` program to it, **renames `Special Needs Education – Generalist` to `Special Needs Education` keeping its `id`**, and adds Elementary, Secondary, Early Childhood, Technical-Vocational Teacher and Physical Education plus `Teacher Certification`. **The migration IS the feature** — the combobox is untouched and now renders *"Add all 8 Education programs"* on its own. **⚠️ The ID range was verified against production rather than assumed:** 44 programs exist, exactly 21 in `V106`'s seed range with `...021` highest, so `...022`+ cannot collide.
- **⚠️ The renamed row also gains `exam_goal_slug = 'let'` — a user-visible change beyond a rename, flagged rather than buried.** `findNamesByExamGoalSlug` returns a `List`, so the field is one-to-many by design; leaving this row NULL would have made seven of eight family members LET-discoverable and one silently not. It now appears under the LET exam goal on the public endpoint.
- **Helper-text copy polish.** The Applicable Programs hint no longer explains the resolver (*"only a single program can inform the writing domain, and Domain Context overrides it"*) — true, but backend mechanics a curator cannot act on, and it invited the reading that picking one program is how you steer the writing. It now states the conceptual separation and points at program families.
- **Three multi-family combobox tests, guarding a bug class that was previously invisible.** Every existing fixture held exactly ONE family, because until `V142` production did too — so a component that ignored which family was clicked and expanded them all would have shipped green. **Mutation-verified with an isolating mutant that typechecks and is identical to correct behaviour under a single family: it kills ONLY the two new tests, with all 14 pre-existing tests passing.**
- **A Program Family can now be CREATED from the admin surface, so a new family no longer needs a migration.** `POST /course-program-catalog/families` plus a create control beside the existing family picker. **⚠️ THIS IS THE GAP THE AUDIT'S STEP 3 WAS SUPPOSED TO CLOSE BUT COULD NOT, BECAUSE STEP 3 ALREADY EXISTED** — assigning a family was already possible; creating one was not, which is why `V106` seeded Engineering and `V142` seeded Education. A third family would have been a third migration.
- **⚠️ A families READ endpoint ships with it, and it is not garnish.** A family is created EMPTY, and the admin form previously derived its options from the catalog — so a family created today would have vanished from the picker on refresh, before any program could be assigned to it. **The authoring combobox still derives families from the catalog, deliberately: it only cares about families that have members.** Do not unify the two.
- **Duplicate family names are rejected case- and whitespace-insensitively**, matching the course-program check, because two identical-looking families would produce two identical-looking expansion shortcuts. A lost race on `uk_program_families_name` resolves to the winning row rather than surfacing a constraint violation.
- **⚠️ The new endpoint owes and has a REAL request test.** The pre-existing `CourseProgramCatalogControllerTest` only reflected on annotations — the exact `v0.119.0` shape — so a `MockMvc` POST with `.contentType(APPLICATION_JSON)` was added and **mutation-verified: deleting the header reproduces `HttpMediaTypeNotSupportedException` and a 415.** `lib/api-program-families.test.ts` pins the client's own request shape, since the component test mocks `@/lib/api` wholesale.
- **`EducationProgramFamilyMigrationTest`** — the rename-guard fixture is created BEFORE the migration runs, since a link inserted afterwards resolves to the new name trivially. It also carries the Engineering-untouched regression guard. **⚠️ Its production denominator is ZERO** (no note is linked to the renamed row), so it is a structural guard and must not be reported as evidence that real data survived; its javadoc says so.


### Scope completeness — the three planned items, reconciled

**⚠️ THE PLANNED SCOPE LIST ABOVE READS AS THREE DELIVERABLES AND ONLY TWO OF THEM WERE BUILT. That is
correct, and this table is why** — a later reader must not see a release that shipped 2 of 3.

| Planned item | Outcome | Evidence |
|---|---|---|
| **1.** One migration | **SHIPPED** | `V142__education_program_family.sql`; guarded by `EducationProgramFamilyMigrationTest` |
| **2.** Admin family **selector** on create | **⚠️ NOT BUILT — IT ALREADY EXISTED** | `<select id="catalog-program-family">` has been in `admin-course-program-catalog-section.tsx` since `9e77f412` (2026-08-11, `v0.100.0`), verified an ancestor of `main`. The audit concluded it was missing by reading `app/admin/course-programs/page.tsx`, a 40-line shell that renders it. |
| **2′.** Admin family **create** surface — *substituted for item 2* | **SHIPPED** | `GET`/`POST /course-program-catalog/families` plus the create control. **This is what item 2 was actually for:** its stated justification was *"this is what stops the release recurring"*, and a selector alone does not — a family still could not be created without a migration. The substitution moved the work to the half of the gap that was real. |
| **3a.** Copy polish, **Course / Program** helper text | **SHIPPED** | `applicable-programs-combobox.tsx:328-331`; the resolver sentence was replaced one-for-one, no paragraph added |
| **3b.** Copy polish, **Domain Context** helper text | **NO CHANGE NEEDED — verified, not assumed** | `note-editor-form.tsx:512-515` already reads *"it shapes how the note is written, while the programs decide who finds it."* It carried no resolver mechanics to drop. Checked against the code rather than inferred from the item's wording. |

**So: 2 of 3 planned items built, 1 found already built, plus 1 substitution and 1 verified no-op.** The
release grew by one item (2′) and shrank by one (2).

**⚠️ VERIFICATION TIER WAS RE-DECIDED WHEN THE SHAPE CHANGED, NOT WHEN THE COUNT DID.** Kickoff
pre-declared *a single `advisor()` call* for "a data migration plus one admin form field." Folding 2′
added **a new write endpoint** — a different shape, not merely a fourth item — and `V142` changes
production catalog semantics (the renamed row gains `exam_goal_slug='let'` and becomes publicly
LET-discoverable). Under the `v0.131.0` rule (*decide the tier from the SHAPE*), that fires the
production-data trigger, so **`advisor()` plus one scoped cold agent ran**, framed as falsification of
this session's own named claims.

### Pressure test — one scoped cold agent, framed as falsification

Tier was re-decided when the shape changed (see above), not when the item count did. One agent, a tight
file list, and seven of this session's own named claims to DISPROVE rather than an open-ended audit.
**Three claims were refuted. Four were confirmed, and the confirmations matter as much** — the
`MockMvc` POST really does issue a request with `Content-Type` (not a method call), `exam_goal_slug` really
is one-to-many at every consumer, the combobox really is family-generic, and the admin control really
does GET on mount.

- **Fixed — the new repository SQL was executed by NOTHING.** `CourseProgramCatalogRepository` is a plain
  `JdbcTemplate` class with hand-written SQL constants and **zero** `@Query(nativeQuery = true)` methods,
  and `NativeQueryPostgresIntegrationTest` finds its subjects by reflecting over `@Query` — so the class
  is **structurally invisible** to the PostgreSQL harness that `CLAUDE.md` describes as covering *"every
  native query."* The service test mocks the repository and the controller test mocks the service, so all
  three new statements were verified **by inspection only**. Added
  `CourseProgramCatalogRepositoryProgramFamilyIntegrationTest`, which runs them against a real database.
  **⚠️ It also covers `mapProgramFamily`'s alias→getter mapping — the exact gap `v0.132.0` named, since
  `PREPARE` validates syntax and types but never that `SELECT id, name` actually feeds
  `getObject("id", …)`.** Mutation-verified: deleting the `jdbcTemplate.update(...)` inside
  `insertProgramFamily` — which still typechecks and still returns a valid-looking response — is killed
  by the new read-back assertion at `:88`, **with all 14 pre-existing service tests still passing.**
- **Fixed — `InvalidProgramFamilyNameException` had zero test references.** An added file with no test
  that executes it. It looks redundant with `@Size(max = 120)` on the request record, which is exactly why
  it was skipped — but the annotation guards the CONTROLLER while the exception guards the SERVICE, which
  a direct call reaches without validation.
- **Fixed — `"nothing resolves a course_program by name"` IS FALSE, and it had already reached the owner.**
  `bulk-generation-page-client.tsx:290` does `catalog.find(p => p.name === courseProgram.trim())`, where
  `courseProgram` is seeded from the user's free-text profile field (`:182`). **So the ONE account holding
  the stale `Special Needs Education – Generalist` string loses Bulk Generate's auto-selection after the
  rename** — graceful degradation, not corruption, but a real regression rather than "a string that no
  longer matches." The claim was true of the path it was checked against (`StudyPackGenerationContext-
  Resolver` resolves by ID) and was over-generalized from there.
  `docs/claude-plans/v0.133.0-owner-profile-string-update.sql` now carries the correction and it
  **strengthens** the recommendation to run the write. **⚠️ `V142`'s inline comment still carries the
  original wording and is deliberately NOT edited — the migration is committed, and changing it would
  alter its Flyway checksum and break startup wherever it has already been applied.**
- **Fixed — the LET fallback lists went stale the moment `V142` landed.** `ExamGoalConfig.java` and
  `frontend/lib/exam-hub-config.ts` both hardcode `let → ["Education"]` as a **fail-open** fallback used
  when the live catalog read fails or returns empty. Every consumer of the *live* list correctly treats it
  as list-valued, so there is no single-value bug — but the fallback itself would have silently
  under-represented the exam goal by **seven programs**. Both now list all eight, with their tests updated.
  **⚠️ This is the "sweep by SURFACE, not by diff" rule paying out: neither file was in the diff, and a
  stale fail-open fallback fails silently by design.**

### Findings recorded and deliberately NOT fixed

- **`CourseProgramCatalogRepository.resolveIdForLegacyName()` resolves by exact name and has ZERO callers**
  anywhere in `backend/src`. Confirmed untouched by this release. Genuinely dead code — deleting it is a
  separate change, and it is recorded here so the next reader does not rediscover it as a live risk.
- **The family dedup SQL uses `lower(trim(name))` while the course-program dedup additionally collapses
  internal whitespace** via `regexp_replace`. Not exploitable today: `normalizeForLookup` already collapses
  whitespace in Java before the parameter is bound. Recorded because the two paths are described as
  matching and, at the SQL level, they do not.

### What the cold agent could NOT check

**The production-collision claim.** `V142`'s comment asserts 44 programs with exactly 21 in the seed range,
verified against production on 2026-09-08. **A repo-only reviewer cannot confirm or refute that** — which
is the migration's own stated reason for requiring a precondition read. This is why the release owes a
post-deploy read rather than treating the migration test as sufficient.

### Anti-drift

- **⚠️ Do NOT modify the applicable-programs combobox** — it is already family-generic.
- **⚠️ Do NOT create a new Domain Context** — `GENERAL_EDUCATION`, `PROFESSIONAL_EDUCATION` and `PROFESSIONAL_PRACTICE_AND_REGULATION` already cover LET.
- **⚠️ Do NOT let Program Family select or override Domain Context**, and do not infer the Education family from `GENERAL_EDUCATION`.
- **⚠️ Do NOT feed Program Family or an expanded program list to the LLM.** `StudyPackGenerationContext` has no field for either and `courseProgram` is a single resolved String, so this is **structurally impossible today — keep it that way.**
- **⚠️ Do NOT delete or migrate away the existing `Education` program** — assign it a family only.
- **⚠️ Do NOT mass-update existing Notes' applicable programs.** No backfill of `note_course_program`.
- **⚠️ Do NOT infer Applicable Programs from Review Set membership**, and do NOT make family membership dynamic inheritance — expansion writes explicit program IDs at authoring time and nothing more.
- **⚠️ Do NOT create duplicate catalog entries**, and **do NOT use a credential abbreviation (BEEd, BSEd, CPE/DPE) as a canonical name.** `Teacher Certification` is the endorsed canonical name because `Professional Education` already exists as a `DomainContext` value and as a Subject, so it would collide across two axes.
- **⚠️ Do NOT block mixed-family selections** or add warning UX for them.
- **⚠️ Do NOT redesign the program taxonomy, touch learner-owned Notes, or change pricing, entitlements or Review Set architecture.**
- **⚠️ NO Review Set publication work.** `v0.132.0` shipped that boundary and owes `[CHECKPOINT — due 2026-09-22, deploy-relative]`; its F5 gap (no publish surface in the Builder) is recorded and **is not this release's to fix.**
- **⚠️ NO Learning Connections work** — `[CHECKPOINT — due 2026-09-19]` has a denominator of ONE.

### Tests

The audit's §14 is mostly covered already by the generic component. The genuinely new cases: **Education family expands to its explicit member IDs**; expansion creates no duplicate selections **asserted with two families present**; **mixed-family selection survives — ⚠️ a single-family fixture proves nothing**; the generation context receives no family or expanded list; `GENERAL_EDUCATION` does not auto-select Education programs; Review Set membership does not alter Applicable Programs.

**⚠️ RENAME GUARD: a Note linked to `Special Needs Education – Generalist` BEFORE the rename must still be linked after it and render the NEW name. A fixture created after the rename passes trivially and proves nothing.**

**⚠️ The Engineering-still-expands test is the regression guard for the migration** — if assigning the existing `Education` row a family accidentally touched Engineering rows, that is what catches it.


### ⚠️ Deploy sequencing — two unrun migrations now queue behind each other

`V141` (`v0.132.0`) and `V142` (this release) are **both unrun in production.** Flyway applies them in
order on the next deploy, which is correct — but three dated obligations hang off *when that deploy
happens*, and they are recorded here rather than left to be inferred:

1. **`v0.132.0`'s `[CHECKPOINT — due 2026-09-22]` is deploy-relative, not merge-relative.** If the
   deploy slips, that date must be re-dated — it does not start counting at merge.
2. **`docs/claude-plans/v0.133.0-owner-profile-string-update.sql` must run in the SAME maintenance step
   as `V142`, not before it.** Running it first points the one affected profile at a catalog name that
   does not exist yet.
3. **`docs/claude-plans/v0.130.0-owner-production-checks.sql` is still outstanding.**

**⚠️ All three are the owner's to run. Claude does not execute writes or migrations against production.**

### Known limitations

- **All FOUR authoring surfaces render their own helper paragraph above the combobox's.**
  `note-editor-form.tsx:437-441`, `private-note-detail-page-client.tsx:2704-2706`,
  `bulk-generation-page-client.tsx:591-593` and `admin-applicable-programs-section.tsx:204` each carry a
  discovery-vs-authoring sentence directly above the combobox's own *"they decide who finds it, never
  how it is written."* They overlap without contradicting. **This predates the release** — the copy
  polish replaced one paragraph with one paragraph and added none — and was found by sweeping the
  surface rather than the diff. Left alone deliberately: consolidating it is a copy change across two
  more files and would have made this a fifth item.
- **`Physical Education` is seeded as an Education-family program carrying `exam_goal_slug = 'let'`.**
  That is right for the teaching degree, and the name is also how the *school subject* is commonly
  written. No collision exists today (the read found no such catalog row), but a future curator adding
  a subject-flavoured entry should reuse this row rather than create a sibling.
- **The families `GET` is ADMIN-only, matching the create endpoint.** The authoring combobox still
  derives families from the catalog, deliberately — it only cares about families that have members.
  **Do not unify the two paths.**

## v0.132.0 - Publication Boundary

**Status: Released** (kicked off 2026-09-08, signed off 2026-09-08, base branch `releases/v0.132.0`, **cut from `docs/planning-publication-boundary-and-plan-corrections` rather than `main`** so that branch's audit commit rides in via the release PR — the `v0.130.0`/`v0.120.0`/`v0.111.0` precedent, used here for convenience rather than because anything is blocked)

**Scope is slices P1 and P2** of `docs/claude-plans/official-review-set-update-publication-boundary.md` (audit written 2026-09-07 by a peer session, every claim `file:line`-anchored). **⚠️ P3 IS DEFERRED — see below.**

### The problem, in the audit's own words

> **Editing an Official Review Set is not publishing an Official Review Set update.**
> **Source changed != published update available.**
> **New adopters should not accidentally receive unfinished curator work.**

**There is exactly ONE source state today. No working/published separation exists anywhere**, so every curator edit is instantly learner-facing: adopters are immediately "behind", and a new adopter or a public viewer sees half-finished curriculum work.

### Planned Scope

**(P1) Publication boundary foundation.** Three columns plus a backfill; filter drift detection, adoption copy and `getPublic` to **published** rows only; a `Publish update` service and endpoint that is atomic, locked and idempotent. **⚠️ ONE MIGRATION. The whole contract lives in this slice.**

**(P2) Curator UX.** The `Publish update` action, an `Unpublished changes` indicator, and a confirmation carrying additions-only counts. No migration.

**⚠️ P1 AND P2 SHIP TOGETHER AND THAT PAIRING IS DELIBERATE, NOT PADDING.** P1 creates the endpoint but no way to press it; shipping it alone would leave curators unable to publish anything except through the API, while every edit silently stopped reaching learners. **That is the `v0.130.0` empty-inbox shape** — a substrate with no surface — and it was expensive enough once.

### ✅ THREE OWNER DECISIONS — ALL SETTLED 2026-09-08, BEFORE THE CODEX PROMPT WAS WRITTEN

The owner accepted all three of the audit's recommendations:

1. **✅ THE NARROWED PUBLIC-VIEW CONTRACT (§6) IS ACCEPTED.** Public visitors see published **additions**; **removals and reorders remain live and are NOT hidden.** The alternatives were dual working/published columns per field plus soft-delete, or a full snapshot — both disproportionate. **⚠️ THIS IS A STATED LIMITATION, NOT A GAP TO CLOSE LATER BY DEFAULT, and it is recorded here rather than only in the prompt because the prompt is gitignored.** See "Known limitation" below.
2. **✅ BACKFILL STAMPS THE COLLECTION'S `created_at`, not `now`** — so a future "Updated" date is not uniformly the deploy date.
3. **✅ `last_update_published_at` PRESERVES THE CAPABILITY FOR A PUBLIC "Updated" DATE, AND SHIPS NO UI FOR IT.** **⚠️ Do NOT add that UI in this release.**

### ⚠️ Known limitation, accepted at kickoff rather than discovered at signoff

**The public view hides unpublished ADDITIONS ONLY.** A note the curator **removes**, or a section they **reorder or rename**, is visible to the public and to new adopters immediately — because `position` and `label` are single columns and a delete is a real delete, so there is nothing to hide behind without a second column per field or a soft-delete. **⚠️ Test 11 must be written to this narrowed contract and MUST NOT be reported as passing in full.** The learner-facing contracts are fully satisfied; only the public browsing view is partial, and the residual is small in practice because a mid-expansion curator is overwhelmingly *adding*.

### Why P3 is deferred

**P3 (the Review Set update notification) was Stage 6 of the notification plan and this audit unblocks it** — §7 supersedes that plan's §8 drift-signature dedup blocker, which must NOT be implemented. **It is deferred on SIZE, not on doubt:** P1 carries a migration and rewires what `getPublic` and adoption copy can see, which is enough surface for one release. **⚠️ Release SIZE is the biggest lever on verification cost, and it compounds.**

### Anti-drift

**⚠️ A PER-ROW PUBLICATION STAMP, NOT A SNAPSHOT ARCHITECTURE.** The audit's central structural finding is that `applySourceUpdate` is **additive-only** — `NoteCollectionService:2050` says *"THIS RELEASE REPORTS AND NEVER APPLIES"*, and `MOVED` changes are detected and reported but never applied. **So the boundary only has to gate what becomes visible as an ADDITION.** Do NOT build snapshots, versions or history. **⚠️ Do NOT implement §8's drift-signature dedup — it is SUPERSEDED.** **⚠️ NO Learning Connections work: `[CHECKPOINT — due 2026-09-19]` is ELEVEN DAYS OUT and its denominator is ONE.** **⚠️ Do NOT build the notification half (P3), and do NOT add a public "Updated" date UI.** **⚠️ Curriculum update != Note content overwrite, and one learner remains one adopter across every update** — publishing makes changes *available for review*, it never forces learner synchronization. **⚠️ Adoption counts must keep working: this release changes what `getPublic` can SEE, and `v0.129.0`'s count reads the same rows.** No quota, entitlement or pricing change; onboarding untouched.

### Verification

**⚠️ AT LEAST ONE SCOPED COLD AGENT, DECIDED AT KICKOFF RATHER THAN AT SIGNOFF.** This release **moves a visibility boundary** — it changes what an anonymous `getPublic` caller and a new adopter can see — and it carries a migration with a backfill over existing production rows. Both are named triggers. **⚠️ AND THE `v0.131.0` LESSON APPLIES DIRECTLY: that release decided its tier by the letter of the gate, shipped, and a cold agent then found a trap it had introduced. Decide the tier from the SHAPE of the change, not from the item count.**

**⚠️ PRE-DECLARED GUARDS, from the audit's §12 and this repo's carried lessons:**
- **(1)** an unpublished curator edit is invisible to `getPublic`, to a NEW adopter, and to drift detection — **assert all three, since they read the same rows by different paths.**
- **(2)** publishing is **idempotent and atomic** — a second publish adds nothing, and a failure mid-way leaves no half-published set.
- **(3)** **an existing adopter's already-copied content is UNTOUCHED by a publish** — publishing offers, it never overwrites.
- **(4)** the backfill leaves every pre-existing set **published**, so nothing silently vanishes from Explore on deploy. **⚠️ ASSERT AGAINST REAL MIGRATED ROWS, AND THE HARNESS IS NAMED SO IT ACTUALLY HAPPENS: `NativeQueryPostgresIntegrationTest`**, which starts PostgreSQL 16 and applies the real Flyway migrations. **⚠️ Do NOT assert this in `NoteCollectionServiceProjectionIntegrationTest` — that file hand-writes its H2 DDL and can silently drift from the migration set**, which is the anti-pattern `v0.130.0` recorded.
- **(5)** **⚠️ CORRECTED AT PROMPT TIME — THE ORIGINAL WORDING WAS UNTESTABLE AND POINTED THE WRONG WAY.** It read *"the adoption count still returns the same numbers for a set with no unpublished edits"* — but post-backfill every row is published, so that fixture never fires the filter. It is exactly what the audit lists under *"fixtures that prove nothing."* **The discriminating test uses a source set WITH unpublished additions.** And the direction that actually breaks is the opposite one: **⚠️ `published_at` IS A SOURCE-SIDE CONCEPT, but the migration adds the column to `note_collections` and `note_collection_items`, which hold ADOPTER rows too.** Nobody publishes a learner's copy, so those rows' stamps are meaningless. `countAdoptionsByCollectionIds` counts **adopter-side** collections by `sourcePlanId` and **MUST NOT filter on `published_at`** — if it does, `v0.129.0`'s counts start reading a column that means nothing on the rows it counts.
- **(6)** **⚠️ A RENDERED CONTROL THAT IS DISABLED IS NOT A CONTROL** — if the curator UX disables `Publish update` in any state, assert what the curator can actually do in that state. This guard exists because `v0.131.0` verified an exit by its RENDER gate and missed that it was `disabled`.

**⚠️ Routing: CODEX** — migration plus service plus controller plus frontend. **⚠️ Call `advisor()` BEFORE writing the prompt** — measured as the highest-yield checkpoint in this repo, and settle owner decision 1 first.

### Shipped

- Added `V141__review_set_publication_boundary.sql`: source collection and item publication stamps plus a root finalization marker. Its backfill stamps every existing collection with its own `created_at`, every existing item with its owning collection's `created_at`, and existing public source roots' `last_update_published_at` with `created_at`.
- Curator additions remain working-only until an explicit, atomic `Publish update` action stamps the Official Review Set subtree. Drift, adoption copying, and public detail read published source rows; learner-owned library/detail reads and adoption counts remain unfiltered.
- Added the curator-only `Published` / `Unpublished changes` state and additions-only publication confirmation. P3 notification fan-out and public Updated-date UI remain deferred.

### Pressure test — two cold agents, and what they found

Tier was pre-declared at kickoff ("AT LEAST ONE SCOPED COLD AGENT"). **Two were run**, on non-overlapping halves, framed as FALSIFICATION of the implementing session's named claims — and partitioned so the **frontend/backend seam was explicitly OWNED** rather than falling between them, which is the `v0.119.0` failure. Full report: `docs/claude-findings/v0.132.0-publication-boundary-pressure-test.md`. Four defects blocked signoff; **both agents independently found the same one from opposite halves.**

- **Fixed — a Goal whose child Subject Plans were all unpublished produced a SILENTLY EMPTY adopted Goal.** The gate counted children unfiltered while the copy list beneath it filtered by publication, and `adoptGoal` never copies the root's own items. The learner got zero plans and zero notes, it became their **primary** collection, and `alreadyAdopted` made it **unrepairable by retrying**; the public adoption count incremented for it. This release had converted a loud `CollectionNotFoundException` into a silent one. The gate now reads the same filtered, owner-scoped list it copies.
- **Fixed — `listPublic` was unfiltered while `getPublic` was filtered.** Explore, Exam Hub and dashboard cards counted unpublished plans and notes that the linked page would not show. Those count queries were unfiltered before this release too; **what this release introduced is the divergence.** `childCount > 0` also sets `isGoal`, which routes Adopt to `adoptGoal` — so it fed the defect above. **⚠️ The fix filters in Java over already-fetched rows on purpose: the authenticated `list()` shares those exact queries, and an adopted row is never published, so a predicate in the query would have emptied every learner's own library.**
- **Fixed — the first publication of any PRE-EXISTING collection stamped nothing.** V141's first `UPDATE` carries no visibility predicate, so every pre-deploy row — private drafts included — was backfilled with a `published_at`, making the `published_at == null` guard false for all of them. A draft that existed at deploy, was filled in afterwards and then published would publish **nothing**. The discriminator is now `last_update_published_at`, which V141 stamps only for pre-existing public source roots. **⚠️ Not "always stamp": that would walk a `PUBLIC → PRIVATE → PUBLIC` flip's edits past the boundary.**
- **Fixed — a publish failure was invisible.** `setPublishUpdateOpen(false)` sat inside the `try`, so on failure the modal stayed open and `AppModal`'s `fixed inset-0` portal covered the page-level error Card. The curator saw a click that did nothing. **⚠️ This defeated the obvious test — `getByText(message)` passed — so the new guard asserts `within(dialog)`.** It is the `v0.131.0` disabled-control lesson one layer up: the control was enabled, the FEEDBACK was occluded.
- **Fixed — coverage gaps on added files.** `ReviewSetPublicationStatusProjection` was never produced by real Spring Data (every assertion ran against a hand-built stub over a mocked repository, and `PREPARE` cannot check alias→getter mapping); `ReviewSetUpdateNotPublishableException` had **zero** test references, so nothing proved a private, child or adopted root is rejected; and no `lib/api-*.test.ts` existed for either new endpoint despite the repo's named convention for exactly that. All three now exist. Each of the five fixes above is mutation-verified with the killing test named.

**⚠️ Correction to this release's own claim.** The delivery was recorded as asserting the backfill "against REAL migrated rows, not a hand-built fixture." **That is false as worded** — the test seeds its own rows after Flyway and replays the shipped SQL. A V141 mutation *is* caught, so it retains its value, but **pre-declared guard #4 is structurally unsatisfiable** in a Flyway-on-empty-schema harness: reading the real SQL text is the best available approximation. The guard needs rewording; the test does not need changing.

### Known limitations

- **The public view hides unpublished ADDITIONS only.** Removals, reorders and renames stay live, because `position` and `label` are single columns and a delete retains no row. Accepted at kickoff as owner decision 1.
- **There is no publication surface on the page where curators actually edit.** `addItems` refuses a collection that has children, so every topic addition happens on a child Subject Plan in the Builder — and the `Unpublished changes` indicator and `Publish update` action exist **only** on the root collection detail page. A curator can add a Subject Plan and ten topics in the Builder with nothing telling them the work is invisible to learners. **⚠️ This is the sibling of pre-declared guard 6: a control the curator never navigates to is not a control.** Not built in this release deliberately; recorded rather than discovered later.
- **A directly-published child Subject Plan is a permanent dead end.** `publishInitialCurriculum` early-returns for a non-null `parentCollectionId` while `validatePublishable` rejects neither a child nor an adopted copy, so a hand-issued visibility POST on a child yields a `PUBLIC` collection at zero public items that no control can stamp — `assertOfficialReviewSetRoot` rejects children. ADMIN-only, no UI path reaches it.
- **The item backfill uses the collection's `created_at`, not the item's**, so an item added long after its collection carries `published_at < created_at`. Nothing compares them today; it would matter only if a future release surfaces a per-item date.
- **The `@Modifying` natives lack `clearAutomatically`.** Nothing dirties those entities after the natives today, so this is latent fragility rather than a live bug.

## v0.131.0 - Inbox Polish

**Status: Released** (kicked off 2026-09-07, signed off 2026-09-08, base branch `releases/v0.131.0`, cut from `main` after `v0.130.0` merged as #1344 and tagged `3cec79bb`)

**Three fixes against the SHIPPED `v0.130.0` inbox**, from `docs/claude-plans/notification-inbox-polish.md` (owner report, 2026-09-07). **⚠️ FRONTEND ONLY — no backend, no migration, no contract change, no new notification type.**

### Planned Scope

**(1) Close on outside click, and drop the `Close` button.** The desktop panel today can be closed ONLY by its `Close` button (`notification-inbox.tsx:172`); the component's sole `addEventListener` is a `matchMedia` listener for `isMobile` (`:41`). **⚠️ THE INBOX IS THE ODD ONE OUT** — the avatar menu in the same header (`app-shell.tsx:525`), the theme toggle (`theme-toggle.tsx:125,136`) and the export dropdown (`export-dropdown-menu.tsx:43`) all already close on outside click. Copy the avatar-menu pattern, then delete the `Close` button. Add `Escape` for the desktop panel. **⚠️ MOBILE NEEDS NO CHANGE** — that path renders `AppModal`, which already handles both (`app-modal.tsx:109-111`); do NOT add a second handler.

**(2) The bell is a toggle, not a refresh.** `openInbox` (`:57-60`) does `setIsOpen(true)` + `loadInbox()` unconditionally, so clicking an open inbox re-opens and refetches. When open, close WITHOUT refetching; when closed, open and load. **⚠️ Do NOT drop load-on-open** — only the re-click while open skips the fetch. The trigger also carries a hardcoded `aria-label="Open notifications"` (`:157`) and **no `aria-expanded`**; a toggle owes both.

**(3) Hide the bell while a learner is taking a quiz.** It is already hidden, but for two modes only: the bell renders inside the header (`app-shell.tsx:643`) which is wrapped in `{!isExamFocusActive ? (` (`:628-629`), and there are exactly **two** `useExamFocusMode` consumers — Long Exam (`phase === "running"`) and Challenge Quiz **Board Exam mode only**. Extend to ordinary Challenge Quiz, Quick Review, Adaptive Practice, Interview Practice and the shared quiz. **No new mechanism** — hook, context and header gate all exist.

### ⚠️ SCOPE SET AT KICKOFF BY A VERIFIED FINDING — ITEM 3 SHIPS EXITS FIRST

**⚠️⚠️ CORRECTED 2026-09-07, HOURS AFTER THIS KICKOFF — THE FINDING BELOW WAS WRONG, AND IT IS KEPT RATHER THAN DELETED BECAUSE IT IS THE SAME DEFECT CLASS `v0.130.0` SHIPPED A WHOLE PRESSURE TEST TO CATCH.** The claim was that Quick Review's running branch has no in-page exit. **It has one.** A sticky top bar carrying a **"Leave Quiz"** button (`quick-review/page.tsx:1101`, gated on `quizSessionActive`) renders *above* the branch chain that was read. The audit that produced the claim grepped for `BackLink`, `<Link` and `router.push` and **never searched for the `onClick={() => requestLeave()}` button pattern that is the actual running-state exit in this repo** — a claim asserted from an incomplete search rather than anchored to the code that implements it.

**THE CORRECTED AUDIT — every surface, checked for the right pattern:**

| Surface | Bell during quiz | Running-state exit |
|---|---|---|
| Long Exam | already hidden | ✅ `ExamTopBar` |
| Challenge Quiz — Board Exam | already hidden | ✅ `ExamTopBar` |
| Challenge Quiz — ordinary | **visible** | ✅ inline top bar, *Leave Quiz* (`:1670`) |
| Quick Review | **visible** | ✅ *Leave Quiz* (`:1101`) |
| Adaptive Practice | **visible** | ✅ *Leave Quiz* (`:782`) |
| Interview Practice | **visible** | ✅ *Leave Practice* (`:327`) |
| Shared quiz `/quiz/[token]` | **already absent** | n/a — see below |

**⚠️ CONSEQUENCE 1: NO EXIT WORK IS OWED. All four surfaces that need focus mode already have a running-state exit**, so item 3 is the hook call alone. Guard 7 still gets asserted per surface — it is now a regression guard rather than a prerequisite.

**⚠️⚠️ CONSEQUENCE 2, AND IT IS A REAL FINDING THE PLAN GOT WRONG: DO NOT ADD `useExamFocusMode` TO THE SHARED QUIZ — IT WOULD BE A SILENT NO-OP.** `app-shell.tsx:593` returns early for `/quiz/` with a bare `<main>`, **so that route never renders the header and the bell is already absent there.** Focus mode's only consumer is the header gate, so the hook would change nothing while reading as shipped work — precisely the `v0.116.0`/`v0.117.0` shape (a behaviour changed with no test that runs it). **The plan's five-surface list is therefore FOUR surfaces.**

**⚠️⚠️ QUICK REVIEW'S RUNNING STATE HAS NO IN-PAGE EXIT, AND ADDING FOCUS MODE TO IT AS WRITTEN WOULD TRAP THE LEARNER.** Checked at kickoff rather than taken on trust: `app/study-packs/[id]/quick-review/page.tsx` renders a branch chain — loading → error → `totalQuestions === 0` → `!currentSessionId` → `isComplete` → `retry-transition` → **else, the running quiz**. All four `BackLink`s sit in NON-running branches (`:1116`, `:1146`, `:1154`, `:1319`); the running branch has none. **Focus mode hides the ENTIRE header plus the mobile tab bar** (accepted deliberately by the owner), so on that surface it would remove the only way out.

**⚠️ THEREFORE ITEM 3 IS "AUDIT AND ADD EXITS, THEN APPLY FOCUS MODE" — owner decision 2026-09-07, taken with the finding in hand.** Every one of the five surfaces has its RUNNING-state branch audited and an in-page exit added where missing, *before* the hook goes in. **⚠️ A `BackLink` elsewhere in the file does NOT satisfy this** — that is exactly what made Quick Review look safe. **⚠️ Guard 7 is asserted PER SURFACE, never once.**

### Anti-drift

**⚠️ Do NOT let the outside-click handler treat the BELL as "outside"** — the handler and the toggle would both fire on one click and the panel reopens or flickers. **⚠️ Guard 2 exists for exactly this: a fixture that clicks the page BODY passes while the bell double-fires.** **⚠️ Do NOT stop loading the inbox when it OPENS.** **⚠️ Do NOT add a second dismissal handler to the mobile `AppModal` path.** **⚠️ Do NOT give any quiz surface focus mode without an in-page exit.** **⚠️ Do NOT change badge semantics** — announcements still never inflate the number and a zero count still renders no badge element at all. **⚠️ Do NOT change read/dismiss semantics** — opening the panel still must not mark everything read, and dismissal must never alter product state. **⚠️ NO backend change, NO migration, NO new notification type, NO Stage 5/6/7 event.** **⚠️ `[CHECKPOINT — due 2026-09-19]` is TWELVE DAYS OUT and its denominator is ONE — no Learning Connections work, and no connection-request notification.** **⚠️ §8's drift-signature dedup is SUPERSEDED and must NOT be implemented** (recorded by the peer session in the Stage 1 doc; Stage 6 now belongs to `official-review-set-update-publication-boundary.md`). **⚠️ This release SUPERSEDES `shared-quiz-recipient-experience-plan.md` §10's "do NOT expand focus mode to other quiz modes"** — that deferral was withdrawn 2026-09-07 and §10 now points at the polish plan. Do not re-derive the narrowing from that file's history. No quota, entitlement or pricing change; onboarding untouched.

### Verification

**A single `advisor()` call**, per the plan's own routing — one component plus hook calls, no backend, no migration, no authorization or privacy boundary moved. **⚠️ Routing: CLAUDE CODE inline.**

**Pre-declared guards, from the plan's §4:**
- **(1)** an outside click closes the desktop panel.
- **(2)** **⚠️ clicking the BELL while open closes it EXACTLY ONCE — it must not reopen via the outside-click handler.** A fixture that clicks the page body passes while the bell double-fires.
- **(3)** **⚠️ a re-click does NOT refetch — ASSERT THE REQUEST COUNT, not the visible state.**
- **(4)** a closed → open transition still fetches.
- **(5)** `Escape` closes the desktop panel.
- **(6)** the bell is hidden during a quiz and restored on exit, including leaving mid-quiz.
- **(7)** **⚠️ every focused surface has a reachable in-page exit IN ITS RUNNING BRANCH — asserted PER SURFACE.**

**⚠️ CARRIED LESSONS.** From `v0.130.0`, three that bear directly on this release: **a `jest.mock` factory is an ALLOW-LIST** — a module the component imports and the factory omits fails silently at the call site, and `app-shell.test.tsx` was green *because* its poll threw; **confirm a mutation is PRESENT AND EFFECTIVE**, since a mutation that cannot reach its subject proves nothing; and **never revert a mutation by restoring the file from HEAD**, which discards the real fix alongside the mutant.

### Shipped

- **The desktop inbox closes on outside click and on `Escape`, and the `Close` button is gone.** It existed only because closing was otherwise impossible. **⚠️ THE REF WRAPS THE BELL *AND* THE PANEL, AND THAT IS THE WHOLE FIX** — had it wrapped only the panel, the bell would count as "outside", `mousedown` would close and the bell's own `click` would reopen **and refetch**, so one click would flicker instead of closing. Same placement as the avatar menu (`app-shell.tsx:653`). **⚠️ Mobile is untouched**: that path renders `AppModal`, which already handles backdrop and Escape, so the desktop handler is gated on `!isMobile` and the two cannot fight.
- **The bell is a toggle.** Clicking an open inbox closes it **without refetching**; opening still loads. Previously `openInbox` set open and called `loadInbox()` unconditionally — the "refresh, not toggle" the owner reported. The trigger gained `aria-expanded`, with a **stable** `aria-label` beside it: that is the convention already in this repo (`theme-toggle.tsx:174`, `export-dropdown-menu.tsx:74`), and swapping both would announce the same fact twice.
- **The bell is hidden for the duration of any quiz, not just the two exam modes.** `useExamFocusMode` now covers **ordinary Challenge Quiz, Quick Review, Adaptive Practice and Interview Practice** alongside Long Exam and Board Exam. No new mechanism — the hook, the context and the header gate all existed.
- **Two sticky in-page bars moved from `top-16` to `top-0`** (`quick-review-top-bar`, `challenge-quiz-top-bar`). The 4rem offset cleared the app-shell header, which focus mode now hides for exactly the state those bars render in; left alone they would float 4rem down with nothing above them. **⚠️ Adaptive Practice and Interview Practice deliberately got NO layout change** — their leave controls sit in ordinary non-sticky rows with no offset to correct.

**⚠️⚠️ THE KICKOFF'S OWN HEADLINE FINDING WAS WRONG, AND CORRECTING IT SHRANK THE RELEASE.** It claimed Quick Review's running branch had no in-page exit and widened item 3 to "audit and add exits first". Quick Review has a *Leave Quiz* button (`:1101`) rendered above the branch chain that was read; the audit had grepped for `BackLink`, `<Link` and `router.push` and **never searched for the `onClick={() => requestLeave()}` button that is the actual running-state exit in this repo.** **All four surfaces already had one, so ZERO exit work was owed.** Recorded rather than quietly dropped, because it is the same "claim not anchored to the code that implements it" defect `v0.130.0` shipped a whole pressure test to catch — committed as `35976007` before any code was written.

**⚠️ AND THE PLAN'S FIVE-SURFACE LIST WAS FOUR: the shared quiz was DROPPED because the hook there would be a SILENT NO-OP.** `app-shell.tsx:593` returns early for `/quiz/` with a bare `<main>`, so that route renders no header and the bell is already absent; focus mode's only consumer is the header gate. Adding it would have changed nothing while reading as shipped work — the `v0.116.0`/`v0.117.0` class exactly.

**Verification.** A single `advisor()` call, per the pre-declared tier — and it changed three decisions before any code was written: it caught that the label should stay stable (checked against the repo rather than assumed), that only two of the four surfaces have sticky bars needing the `top-0` change, and that guard 2's **event sequence is the guard**.

**⚠️ MUTATION VERIFICATION — EACH MUTANT NAMED WITH THE TEST THAT KILLED IT:**
- **Moving the ref from the wrapper onto the panel** (making the bell "outside") → killed by `closes exactly once when the bell itself is clicked while open, without refetching`, and **only** that test.
- **Restoring the unconditional `setIsOpen(!isOpen); void loadInbox()`** → killed by that same test *and* `still loads the inbox on a closed to open transition` — the pair is what pins "close does not refetch, open still does".
- **`useExamFocusMode(false)`** on Quick Review → killed by both of its focus guards, including the one that drives the quiz to completion and asserts the chrome comes **back**.
- **Reverting Challenge Quiz to `isBoardExamMode && phase === "running"`** → killed by `hides the app-shell chrome during an ORDINARY Challenge Quiz, not just a Board Exam`. **⚠️ Its fixture is deliberately an ordinary quiz: a Board Exam fixture passes under both the old and the new expression and would prove nothing.**
- **`useExamFocusMode(false)`** on Adaptive Practice and Interview Practice → one killed test each.

**⚠️ GUARD 2's TEST SHAPE IS THE GUARD, AND THIS IS THE REUSABLE LESSON.** `fireEvent.click` does **not** fire `mousedown`, and this project has **no `@testing-library/user-event`** — so a test that merely clicks the bell passes under the defect by construction. The guard dispatches `mousedown` **then** `click`, and asserts the **request count** rather than the DOM, because the panel can close and reopen inside one sequence and still read as "open".

**⚠️ THE `jest.mock` ALLOW-LIST TRAP FIRED TWICE DURING THIS RELEASE — ONCE ON THE EXISTING SUITE, ONCE ON WORK ADDED HERE.** `quick-review/page.test.tsx` mocked `exam-focus-context` with **only** `useBottomViewportClaim`, so the moment the page imported `useExamFocusMode` the call site would have received `undefined`. Then the new mock added to `adaptive-practice/page.test.tsx` omitted `useBottomViewportClaim` and broke an unrelated answer-review test — **because the CHILD `QuizAnswerReview` imports it, not the page.** A mock factory has to cover the whole subtree's use of a module, not the file's own import list. This is the `v0.130.0` carried lesson landing exactly where it was predicted to.

**Guard 7 is asserted PER SURFACE**, in the same test as guard 6 rather than separately — focus mode hides the whole header, so the in-page exit is the only remaining way out and the two facts are one fact.

**⚠️ THE POLL KEEPS RUNNING DURING FOCUS MODE, AND IT NOW HAS A GUARD RATHER THAN A REASON.** The release notes promise the count "keeps updating quietly in the background, so the bell is accurate the moment you finish." That holds because the poll is a top-level effect in `AppShell` while focus mode gates only the header *render* — but that is reasoning about effect placement, not evidence, and this repo has twice shipped a behaviour whose only support was exactly that kind of reasoning (`v0.116.0`, `v0.117.0`). `keeps polling the unread count while exam focus hides the bell` is killed by suppressing the poll when `isExamFocusActive`. **Caught at signoff by `advisor()` as a user-facing claim with no test behind it.**

**⚠️ `docs/releases/v0.130.0.md` CARRIED TWO PRESENT-TENSE CLAIMS THIS RELEASE FALSIFIED, AND THEY WERE CORRECTED THERE RATHER THAN LEFT AS HISTORY.** *"A bell sits in the header on every signed-in page"* and the panel's `Close` button both describe how the product works, not what `v0.130.0` did, and a published release-notes file is somewhere people look to find that out. **This is the sweep-by-SURFACE rule, and the first instinct — "release notes are point-in-time, leave them" — was the wrong one.**

### ⚠️⚠️ SCOPED COLD AGENT, RUN AFTER SIGNOFF — AND IT FOUND A REAL TRAP THIS RELEASE INTRODUCED

**The tier was re-decided, and the first call was the weaker one.** By the letter of the gate no trigger fired — no authorization or privacy boundary moved, one feature PR, no money/quota/production-data semantics — so a single `advisor()` call was defensible. **But the fourth trigger, *"delivery introduced a defect the same session then fixed — a measured blind-spot signal"*, does fire, and it took the owner asking to see it:** this session made **three unanchored claims** in one release (Quick Review's exit, the `v0.130.0` notes being point-in-time, effect placement as evidence for the poll), two of them caught by something other than the author. **One scoped agent, framed as falsification, on `sonnet`.**

**⚠️ FINDING 1 — CONFIRMED, AND IT IS A TRAP THIS RELEASE CREATED. Widening focus mode to `phase === "running"` left Challenge Quiz with the header hidden AND its only exit disabled for the entire submission round-trip.** Both Leave controls are `disabled={submitting}` (`:1662` Board Exam, `:1678` ordinary), and `finalizeChallengeSession` holds `submitting` true across the whole completion request while `phase` is **still `"running"`** — it flips to `"complete"` only *after* the await. So the learner had **no header and no working exit**, indefinitely if the request hung. **⚠️ Board Exam reaches this WITHOUT THE LEARNER DOING ANYTHING: its timer auto-submits on expiry.** Fixed by `useExamFocusMode(phase === "running" && !submitting)` — the header returns for exactly the window the in-page exit is unavailable, which is the cheap side of the trade. Guard: `gives the header back while submitting, because both Leave controls are disabled then`, killed by dropping the `!submitting` term.

**⚠️ IT ALSO FALSIFIED THIS RELEASE'S OWN NOTES**, which claimed *"Every one of those screens keeps its own Leave button, so you can still stop at any point."* Corrected in `docs/releases/v0.131.0.md`.

**⚠️ THE INVARIANT WAS RIGHT AND THE VERIFICATION OF IT WAS NOT.** This release stated *"focus mode may never be active in a state the exit does not cover"* and checked it by comparing the hook's gate to the exit's **render** gate on each surface. Those matched. **What went unchecked was whether the rendered exit was ENABLED** — a disabled exit is no exit, and no amount of comparing render gates would ever have surfaced it. Quick Review, Adaptive Practice and Interview Practice were re-checked and are clean: none of their Leave controls carries a `disabled` prop.

**Known limitation — pre-existing, NOT introduced here, and deliberately not fixed here.** `long-exam/page.tsx:966` carries the identical `leaveDisabled={submitting}` against a focus mode that has been active since long before this release. **⚠️ It is a live instance of the same trap**, but that file is untouched by `v0.131.0` and fixing it is a change to a surface this release did not open. It gets a row rather than a silent ride-along.

**Findings 2-7: could not disprove.** The outside-click ref genuinely wraps bell and panel; mobile cannot fight `AppModal` (the desktop effect is gated `!isMobile`, and the modal is a `document.body` portal that only mounts when `isOpen && isMobile`); no path opens the panel without loading or loads twice; the shared quiz genuinely renders no header for authenticated **or** anonymous viewers, so dropping it was correct. One cosmetic residual: because `useExamFocusMode` sets context state in an effect, the `top-0` bar can paint in the same frame as a still-visible header — **the bar is `z-20` against the header's `z-10`, so it overlaps rather than tucks under**, and it self-corrects on the next paint. Sub-frame, not reproducible in jsdom, recorded rather than chased.

**NO `[CHECKPOINT]` IS OWED, and the reason is that nothing here shipped ahead of its evidence.** All three items fix defects the owner reported against shipped behaviour, each verified directly against the code and pinned by a mutation-killed guard — there is no pre-committed rule, owner override, ambiguous read or bootstrap argument anywhere in the release. The one thing that *was* uncertain — whether the newly-focused surfaces keep a way out — was settled by reading the code before writing any, and is now a standing per-surface guard rather than a dated obligation.

**Suites: 209 frontend suites / 2,325 tests, 0 failures; `tsc` clean; 0 lint errors** (one pre-existing `react-hooks/exhaustive-deps` warning at `challenge-quiz/page.tsx:1395`, on a line this release did not touch). **Backend untouched — frontend-only release, no migration.**

## v0.130.0 - Notification Inbox

**Status: Released** (kicked off 2026-09-07, signed off 2026-09-07, base branch `releases/v0.130.0`, **cut from `releases/v0.129.0` rather than `main`** because `v0.129.0`'s release PR #1340 is BLOCKED by the `main` ruleset's `require_extra_approval_for_unattributed_changes` parameter — the `v0.120.0`/`v0.111.0` precedent, where the signoff commit rides into `main` via the release PR)

Theme: build the notification substrate, and give it the one producer that can actually fill it.

Source: `docs/claude-plans/in-app-notifications-and-review-set-adoption-signals-stage1.md`, §17 — **Stages 3 and 4 together.**

### Why 3 and 4 together, and why NOT 5, 6 or 7

**⚠️ STAGE 3 ALONE SHIPS AN EMPTY INBOX, AND PRODUCTION SAYS SO RATHER THAN INTUITION.** Read read-only 2026-09-07:

| producer | rows |
|---|---:|
| `linked_learner_relationships` ACCEPTED | **1** |
| `linked_learner_invitations` PENDING | **1** |
| `note_shares` | **0** |

Stage 3's own producers are Stage 5 (those rows) and Stage 6 (blocked). **Stage 4 is the only producer that generates content on demand**, and it is admin-controlled, so pairing them is what makes the substrate demonstrable. They share one table, one fan-out path and one verification pass, so splitting them buys no isolation and costs a second cold agent.

**⚠️ STAGE 5 IS DEFERRED FOR A REASON THAT IS NOT EFFORT: IT WOULD CONTAMINATE `[CHECKPOINT — due 2026-09-19]`.** That checkpoint decides whether the Learning Connections arc continues **at all**, and its kill criterion keys on `ACCEPTED` — **where the denominator is ONE.** Stage 5's headline event surfaces a pending connection request to the invitee, which is precisely the nudge that converts a pending invitation into an acceptance. **One notification-caused acceptance would DOUBLE the metric, and the checkpoint would then be reading the notification rather than demand.** Stage 5 also has almost nothing to notify about today (1 pending invitation, **0** note shares). **⚠️ Do NOT ship any Stage 5 event before 2026-09-19 reports.**

**Stage 6 stays BLOCKED on §8** — the drift-signature dedup correction is an unresolved owner decision, and `(recipient, adopted_collection_id, source_synced_at)` is unsound in both directions. **Stage 7 is out of scope by the plan's own table.**

### Planned Scope

**(3) Notification foundation.** `notifications` table with **a UNIQUE index on `(recipient_user_id, dedup_key)` — that index IS the idempotency guarantee, not a service-side check that can race.** Inbox read, read/dismiss, actionable count, bell in the existing header, polling, mobile sheet, and the deep-link contract. **⚠️ Notification = awareness; the owning feature = truth.** A row stores recipient, type, dedup key, deep link and timestamps and **never** request/grant/update state.

**(4) Admin What's New.** `announcements` with `DRAFT → PUBLISHED → ENDED`, **immutable content after publish** (to correct copy: end it and publish a replacement), audience `EVERYONE` / `PROFILE_TYPE` / `PLAN_TYPE`, fan-out to rows, optional expiry via the existing hourly-cleanup job pattern, and an internal-only CTA. **⚠️ Title/body/CTA are COPIED AT FAN-OUT and never re-read**, so `announcement_id` is provenance and lifecycle association, **not live content inheritance.**

**Audit of the Stage 4 delivery (implemented by a cold agent, audited here).** Every claim was re-verified rather than accepted, and **two mutations were run independently of the agent's own**:
- **Collapsing the delivery's `entityId` to a constant** — which would silently give every user only the FIRST announcement ever published, forever — is killed by `twoAnnouncementsToTheSameUserGetDistinctDedupKeysAndBothArrive` and **passes straight through the fan-out-twice test**, exactly as the agent predicted. **⚠️ THAT IS THE FINDING WORTH KEEPING: the obvious idempotency test cannot see this bug class**, and the agent wrote the test that can without being asked to.
- **Removing the `DRAFT`-only guard on edit** fails both `editAfterPublishIsRefusedAndDeliveredRowsAreUnchanged` and `anEndedAnnouncementCannotBeEditedOrPublishedAgain`.

**Independently confirmed:** `./mvnw clean install` **BUILD SUCCESS, 2278 tests**; `V140` applied by Flyway **against the real PostgreSQL 16 container**; frontend **209 suites / 2310 tests**, `tsc` clean, ESLint 0 errors. The immutability test asserts **both halves** — the refusal *and* that delivered rows are byte-identical — and the fan-out-twice test counts **database rows**, not a service return value.

**Four deviations from the prompt, all judged sound and none reverted:**
- **Expiry is evaluated ON READ, not by the cleanup job the Planned Scope named.** ⚠️ This is a correction, not drift: a job-driven expiry is wrong for as long as the job has not yet run, which the release's own error states forbid. The Planned Scope line above is superseded.
- **`publish` on an already-`PUBLISHED` announcement re-runs fan-out** rather than refusing. Fan-out is thousands of committed inserts inside one admin request; the status transition commits *before* it and the unique index makes a re-run insert zero duplicates, so **a timed-out publish is recoverable instead of stranded**. `published_at` is stamped once and a test asserts it is never re-stamped; `ENDED → publish` is still refused.
- **⚠️ THE PROMPT WAS WRONG ABOUT `PLAN_TYPE` AND THE AGENT CAUGHT IT.** The prompt asserted all three audiences are indexed columns on `users`; **plan is not on `users` at all** and resolves through `SubscriptionService.resolvePlan`. The audience query mirrors that precedence (PLUS+PRO resolves to PRO, so such a user is in the PRO audience and not the PLUS one) and is pinned by `planTypeAudienceMirrorsResolvePlanIncludingItsPrecedence`.
- **Admin-only is proven REFLECTIVELY, not as a live 403** — `standaloneSetup` does not run the security filter chain, so a request-level assertion would prove nothing. The test asserts the class-level `hasRole('ADMIN')` exists and that none of the five mapped methods overrides it. **⚠️ This is a repo-wide limitation, not one introduced here: NO `Admin*ControllerTest` in this codebase asserts a live 403**, so this is stronger than the existing precedent rather than weaker.

**Known limitation.** Fan-out runs synchronously inside the admin publish request, one insert per recipient in chunks of 500. **⚠️ THIS PARAGRAPH ORIGINALLY ENDED "Fine at current scale" AND THAT FRAME WAS WRONG — see `### Known limitations` below, rewritten after the pressure test.** The chunking bounds the log output, not the resource: `deliver` is deliberately not `@Transactional`, so each insert auto-commits, **but `open-in-view=ON` with `DELAYED_ACQUISITION_AND_HOLD` means the connection is held until the HTTP request ends regardless** — so the whole fan-out sits on one of twenty, and the blast radius is every learner request, not the admin's. **⚠️ `publish` and `fanOut` are deliberately NOT `@Transactional`** — `deliver` depends on catching `DataIntegrityViolationException`, which under an ambient transaction would mark it rollback-only and kill the whole fan-out.

### Anti-drift

**⚠️ NO Learning Connections work of any kind** — see the Stage 5 deferral above; `[CHECKPOINT — due 2026-09-19]` is twelve days out with `n=1`. **⚠️ Do NOT build ANY Stage 5, 6 or 7 event**: no connection-request notification, no note-share notification, no Review Set update sweep, no Assignment. **⚠️ Do NOT fabricate a recipient for a quiz share link** — a share link has no addressee. **⚠️ Do NOT notify on a Note becoming PUBLIC or on a generic public link.**

**⚠️ POLL, DO NOT STREAM** — there is no WebSocket/SSE infrastructure; `lib/study-pack-generation.ts` is the proven polling pattern. **⚠️ Do NOT compute adopter drift on inbox load.** **⚠️ Never one feature query per notification row** — batch, or snapshot at fan-out.

**⚠️ Announcements NEVER contribute to the numeric badge**, and **never render a literal `0`** — no badge at all at zero. **⚠️ Do NOT mark-all-read on panel open**; that discards the one signal a learner needs for a pending request. **⚠️ Dismissal must never** revoke a connection, decline a request, apply an update, unshare a note, or alter any product state. **⚠️ Do NOT add an authoritative `resolved` column.**

**⚠️ Announcement targeting is EDITORIAL, NEVER AUTHORIZATION** — entitlement stays with `FeatureGateService`. **⚠️ Do NOT allow external or protocol-relative CTA URLs**: accept a relative path, reject anything with a scheme, `//`, or a host. **⚠️ Do NOT build push, email, SMS, quiet hours or a preferences centre. Do NOT build announcement funnels, CTR dashboards or attribution.** **⚠️ Do NOT add a fifth mobile bottom tab** — the bar is already contested by `ExamFocusContext`. **⚠️ Do NOT build on `analytics_events`** — it is telemetry by declaration (`V77` dropped its user FK). **⚠️ No quota, entitlement or pricing change; onboarding untouched.**

### Verification

**ONE SCOPED COLD AGENT**, per the plan's own Verification section — this is **a new cross-user delivery surface whose only duplicate-prevention is a single unique index**, which is exactly the shape that earns an independent read. **⚠️ UPGRADED AT SIGNOFF TO THE FULL THREE-AGENT TEST — this pledge was exceeded, not left unmet.** Two further triggers appeared during delivery that were not visible when this line was written: the implementing session ended up auditing its own inline half after Codex stopped on a usage limit, and **`v0.129.0` turned out to have been signed off with no pressure test at all**, so it was folded in as a third partition. See the pressure-test section under `### Shipped`.

**⚠️⚠️ EVERY NEW ENDPOINT OWES ONE TEST THAT ISSUES A REAL REQUEST — `MockMvc` with `.contentType(MediaType.APPLICATION_JSON)` and a body, the pattern already in `NoteControllerTest`.** This release adds several endpoints, and `v0.119.0` is the measured precedent: **both of that feature's JSON POSTs sent no `Content-Type`**, Spring rejected every request **before the controller was entered**, and the feature could not make one successful request while **2,182 frontend tests passed**. A direct controller-method call is **not** a substitute — it bypasses content negotiation and passes under the defect by construction. On the client side, `lib/api-*.test.ts` pins the request shape; a component test that mocks `lib/api` proves nothing about it.

**Pre-declared guards, from the plan's discriminating list:**
- **(6) Idempotent fan-out** — the same event twice leaves **exactly one row**. ⚠️ Assert the **database**, not the service return.
- **(7) Published Announcement is immutable** — an edit after publish is refused **and delivered rows are unchanged**.
- **(8) Badge excludes Announcements** — one unread Announcement, zero actionable → **no numeric badge at all**, not a `0`.
- **(9) Notification is not authority** — accepting a request through the normal flow leaves the row untouched and the inbox still renders sensibly.
- **(10) No N+1 on inbox load** — a page of N notifications issues a **bounded** number of queries independent of N. ⚠️ **Assert the query count, not the rendered rows.**

**⚠️ CARRIED LESSONS: confirm a mutation is PRESENT before trusting a green suite** (twice-burned), and **anchor every quoted defect to the CURRENT file at kickoff** — `v0.128.0` opened on an item that had already shipped nine days earlier because the kickoff copied planned-scope prose forward.

**Routing: CODEX** — multi-system (two migrations, new entity/service/controller, plus frontend inbox and bell), which the routing table sends to Codex with a prompt. **⚠️ Call `advisor()` BEFORE writing that prompt** — measured as the highest-yield checkpoint in this repo.

### Shipped

**Stage 3 — the notification substrate.** ⚠️ **The inbox is EMPTY by construction until Stage 4 lands; that is the design, not a gap.**

- **`V139` adds `notifications` with a UNIQUE index on `(recipient_user_id, dedup_key)`, and THAT INDEX IS THE DELIVERY GUARANTEE.** `dedup_key` is deterministic — one helper builds `"<TYPE>:<entity-id>"` — and `deliver` **attempts the insert and catches `DataIntegrityViolationException`**, returning the existing row. **⚠️ There is NO `existsBy` pre-check anywhere, deliberately: two concurrent deliveries can both pass one and still duplicate.** Same shape as `NoteCollectionService:952`'s adoption race.
- **The actionable/announcement split is a property of `NotificationType`, not a call-site `if`** — `actionableTypes()` derives from the enum flag, so announcements can never inflate the numeric badge by accident. **⚠️ No badge element renders at zero** — not a `0`, not an empty circle.
- **Cross-user access returns NOT-FOUND, not forbidden**, so an endpoint never confirms someone else's notification exists.
- **Retention deletes read-or-dismissed rows past a config-backed window; unread AND UNDISMISSED actionable rows are RETAINED regardless of age** — such a row is the learner's only pointer to a pending request. **⚠️ The "undismissed" qualifier was missing from this line when it was first written and the sentence was therefore false:** the predicate is `read_at IS NOT NULL OR dismissed_at IS NOT NULL`, so a row dismissed without being read IS eligible for deletion. That is the correct behaviour — dismissing is the learner saying they are done — but it is not what the line said.
- **⚠️ A polled 401 can no longer sign a learner out.** `fetchWithAuth` gained a `handleUnauthorized` parameter **defaulting to `true`, so every existing caller is byte-for-byte unchanged**; only `getNotificationUnreadCount` passes `false`. This is a change to shared auth plumbing and is called out rather than buried.
- **Bell polls at 60s, stops while the tab is hidden, and a failed poll keeps the last known count silently** — clearing it would tell a learner they have nothing when a request is pending.

**Verification.** **⚠️ BOTH `POST` ENDPOINTS HAVE REAL `MockMvc` REQUESTS WITH `.contentType(MediaType.APPLICATION_JSON)`** — the `v0.119.0` defect class, where two JSON POSTs sent no `Content-Type` and Spring rejected every request before the controller was entered. `lib/api-notifications.test.ts` pins the request shape independently. **⚠️ That file originally also asserted the poll issues EXACTLY ONE fetch on a 401 — a test protecting a defect, corrected below.**

**⚠️ MUTATION VERIFICATION, AND ONE MUTATION FAILED TO REACH ITS SUBJECT — RECORDED BECAUSE THAT IS THE INSTRUCTIVE PART:**
- Rendering the badge at zero (`> 0` → `>= 0`) fails the no-badge-at-zero test. ✅
- **A first attempt at "mark everything read on panel open" PASSED ALL TESTS — and it was a false negative, not a missing guard.** The mutation iterated `notifications` from a **stale closure**, which is `[]` on first open, so it marked nothing. **⚠️ It compiled, it was present in the file, and it proved nothing.** Rewritten to mark read from the freshly-loaded list, it fails two tests. **This is the repo's own "the guard must reach its subject the way production does" lesson, hit from the mutation side rather than the fixture side.**

**⚠️ TWO DEFECTS WERE FOUND IN THE HANDOVER, AND BOTH WERE FOUND BY GUARDS RATHER THAN BY READING:**
- **`NotificationCleanupJob` was unregistered, and `ScheduledJobCronContractTest` failed the build for it** — the job declared a production cron but was absent from `EXPECTED_DEFAULTS`, `EXPECTED_ZONES` and the test profile's disable list. **⚠️ Its message names the real consequence: *"an unlisted cron job runs on the wall clock during the suite."*** Fixed by registering it in all three. **This is exactly what that guard exists for, and it worked with no human noticing the omission.**
- **⚠️ THE INBOX CRASHED THE ENTIRE APP SHELL WHERE `matchMedia` IS UNAVAILABLE.** `globalThis.matchMedia(...)` was called unguarded, and **this component renders inside the header — so it takes down EVERY authenticated page, not just the bell.** It surfaced as 14 failures in `app-shell.test.tsx`. **⚠️ Fixed in the COMPONENT, not by mocking it in the test** — the fault was real, not a test gap — with optional chaining and a desktop-popover fallback, plus a guard asserting the component still renders.

**Routing note.** Codex delivered the backend and part of the frontend, then stopped on a usage limit. **The backend was audited as third-party work and needed no correction.** The remainder — bell mount, count state and polling in `app-shell.tsx`, frontend tests, this documentation — was completed inline. **⚠️ The pre-declared scoped cold agent at release end is therefore doing double duty as the independent read on the inline half**, since the implementer also audited it.

**Stage 4 — Admin "What's New", the inbox's first producer.**

- **`V140` adds `announcements` with a `DRAFT → PUBLISHED → ENDED` lifecycle**, fanning out into the **existing** `notifications` table. **⚠️ NO SECOND DELIVERY PATH, no lazy eligibility evaluation, and no scheduler** — Draft → Published is manual. `ReEngagementCampaignService` was **not** extended: it has a hardcoded audience, a fixed template and email-only delivery, with no campaign entity to build on.
- **⚠️ PUBLISHED CONTENT IS IMMUTABLE, AND AN EDIT IS REFUSED — NEVER A SILENT NO-OP.** `AnnouncementNotEditableException` (409) carries the remedy in its `action`: *end this announcement and publish a replacement*. **Title, body and CTA are COPIED onto each notification row at fan-out and never re-read**, so `announcement_id` is provenance and lifecycle association, not live content inheritance. The test asserts **both halves** — the edit is refused *and* the already-delivered rows are byte-identical afterwards.
- **⚠️ IDEMPOTENCY IS PROMPT A's UNIQUE INDEX, WITH NO `existsBy` PRE-CHECK ADDED.** `dedup_key` is `"ANNOUNCEMENT:<announcementId>"` from the one existing helper. **Re-publishing RE-RUNS the fan-out rather than being refused, deliberately:** fan-out is one committed insert per recipient with no ambient transaction, so a few thousand recipients is a few thousand round trips inside one admin request — long enough to outrun a gateway timeout. The status transition commits *before* fan-out, so a timed-out publish is recoverable by pressing Publish again. **`published_at` is stamped once and never re-stamped; `ENDED` → publish is refused.**
- **⚠️ `publish`/`fanOut` ARE DELIBERATELY NOT `@Transactional`.** `deliver` depends on catching `DataIntegrityViolationException`; under an ambient transaction that violation would mark the whole transaction rollback-only and take the entire fan-out down with it. **Partial failure never rolls back deliveries already made** — one recipient's failure is counted, logged and stepped over, and a retry picks up exactly the recipients that were missed.
- **⚠️ ENDING AND EXPIRY ARE DECIDED ON READ, SO THEY BITE IMMEDIATELY** — no cleanup job stands between the admin's click and the learner's inbox. The lifecycle check is a correlated subquery inside the **single** inbox query, so the query count stays independent of row count. **⚠️ It is NOT-EXISTS-ended rather than EXISTS-live**: a notification outlives the thing it points at by design, so a vanished announcement row leaves the delivered row **visible** instead of silently eating inboxes. **This supersedes the Planned Scope line above that said "optional expiry via the existing hourly-cleanup job pattern"** — a job-driven expiry is wrong for as long as the job has not run.
- **⚠️ CTA VALIDATION IS A SECURITY CONTROL, AND IT IS A RULE, NOT AN ALLOW-LIST.** `cta_path` must be a same-origin relative path — `^/[A-Za-z0-9\-._~/]*$` plus an optional query string — rejecting any scheme, host or protocol-relative `//` prefix. An allow-list of routes would need an application release for every new legitimate destination, which is the pressure that gets a check deleted. **Validated on write AND re-checked on render** (`lib/safe-relative-path.ts`), because `next/link` renders an absolute URL as a live external anchor and a stored value is still untrusted by the time it reaches an `href`.
- **⚠️ TARGETING IS EDITORIAL, NEVER AUTHORIZATION.** Three audiences, no query builder. `AnnouncementAudienceResolver` holds no reference to `FeatureGateService` and a test asserts it; an untargeted account's rows are byte-identical after a publish. **The `PLAN_TYPE` leg mirrors `SubscriptionService.resolvePlan` including its precedence** — a user with both an active PLUS and an active PRO resolves to PRO, so they are in the PRO audience and not the PLUS one — using its own query rather than `findActiveUserIdsByPlanTypeInAndStatus`, which omits the `startAt` leg. **⚠️ `EVERYONE`/`PROFILE_TYPE` are ACTIVE accounts, deliberately NOT filtered on `emailVerifiedAt`**: delivery is in-app, unlike the email campaign path. **An audience resolving to zero users publishes successfully and delivers nothing** — a legitimate outcome, not an error.
- **Admin surface at `/admin/announcements`**, modelled on the Campaigns page: list with status, draft form, and confirmed Publish / End. **Publish confirms before firing**, and **the form states immutability-after-publish up front** so Edit disappearing is never a surprise. Announcement rows render in the **existing** inbox — no new surface, no badge-rule change.

**Verification (Stage 4).** **⚠️ ALL FIVE ENDPOINTS HAVE REAL `MockMvc` REQUESTS WITH `.contentType(MediaType.APPLICATION_JSON)` AND A BODY**, and `lib/api-announcements.test.ts` asserts the `Content-Type` header on the three POSTs and the PUT independently — the `v0.119.0` defect class. Admin-only is proven **reflectively and honestly**: `standaloneSetup` does not run the security filter chain, so the test asserts the class-level `hasRole('ADMIN')` gate exists and that **none of the five mapped methods overrides it** — it is not a live 403 assertion and is not written up as one.

**⚠️ MUTATION VERIFICATION — EACH MUTANT NAMED WITH THE TEST THAT KILLED IT:**
- Passing a **constant** instead of the announcement id as the delivery's `entityId` (which would collapse every announcement onto one dedup key and deliver only the first one, forever) → killed by `twoAnnouncementsToTheSameUserGetDistinctDedupKeysAndBothArrive`. **⚠️ The fan-out-twice test does NOT kill this one** — that is exactly why both exist.
- Dropping the `expires_at` leg of the inbox predicate → killed by `anExpiredAnnouncementStopsPresentingWithoutAnyCleanupJobRunning`.
- Dropping the protocol-relative `//` check while keeping the regex (the regex alone passes `//evil.example`, because `/` is in its character class) → killed by `rejectsAnythingThatIsNotASameOriginRelativePath` and `theProtocolRelativePrefixIsRejectedEvenThoughItStartsWithASlash`.
- Rendering `notification.ctaPath` directly instead of the validated path → killed by `refuses to render a CTA that is not a same-origin relative path`.

### Pre-signoff pressure test — FULL THREE-AGENT, and what it changed

**Tier chosen: the FULL test, not the scoped one.** Two triggers fired together — this release added a
substrate touched by two PRs plus a new admin write surface, and the implementing session also audited
its own inline half after Codex stopped on a usage limit. **`v0.129.0` was folded into the same test
because it was signed off without one**, and that decision paid for itself: three of the findings below
are in that already-released version. Three cold agents on non-overlapping halves (`v0.130.0` backend /
`v0.130.0` frontend+seam / `v0.129.0`), synthesized through `advisor()`.

**⚠️ THE HEADLINE RESULT IS THAT EVERY DEFECT BELOW WAS INVISIBLE TO A GREEN SUITE.** 2,282 backend and
2,300-odd frontend tests passed over all of it.

**Fixed in this release:**

- **⚠️ THE BADGE AND THE INBOX DISAGREED — found independently by BOTH the backend and the frontend
  agent, which is why it is first.** `countActionableUnread` filtered on `read_at` alone while
  `findVisibleInbox` also filters `dismissed_at`. An actionable row dismissed without being read left the
  inbox and **kept incrementing the bell forever** — a number the learner could neither open nor clear.
  Latent today only because Stage 5 has not shipped an actionable producer. Both queries now carry the
  same visibility predicate, plus the announcement-lifecycle leg as mirroring (announcements are
  non-actionable, so no row has both today — it is there so the two cannot drift). Guard:
  `aDismissedActionableRowStopsCountingTowardTheBadge`, killed by removing the `dismissed_at` leg.
- **⚠️ THE UNREAD POLL COULD NEVER REFRESH ITS TOKEN, SO THE BADGE FROZE AFTER 15 MINUTES IDLE.**
  `getNotificationUnreadCount` passed `retry=false` **and** `handleUnauthorized=false`. Access tokens live
  15 minutes and the poll runs every 60 seconds, so every poll after the first idle quarter-hour 401'd
  without refreshing and the badge silently stuck on its last value for the rest of the session.
  **⚠️ The "refresh storm" justification written into the code comment was never real** —
  `tryRefreshAccessToken` already dedupes concurrent refreshes, and `trackAnalyticsEvent` has used exactly
  this pairing all along. Now `retry=true, handleUnauthorized=false`; the two halves are separate
  decisions and each has its own test, each mutation-verified.
- **⚠️ A TEST WAS PROTECTING THAT DEFECT.** `does NOT retry or clear the session when the unread-count
  poll returns 401` asserted **exactly one** fetch — so the fix could not land without the test failing.
  This is the `v0.74.0` shape (a guard asserting the wrong behaviour) and is called out rather than
  quietly rewritten.
- **⚠️ A PURGED ACCOUNT LEFT ITS NOTIFICATIONS BEHIND PERMANENTLY.** `notifications` was missing from
  `AccountPurgeService.deletePersonalRows`. **⚠️ Nothing else could ever take those rows:** retention
  deliberately retains unread rows regardless of age, so the cleanup job is not a fallback. Added
  `deleteByRecipientUserId`, asserted in `AccountPurgeServiceTest`.
- **⚠️ `app-shell.test.tsx` MOCKED `@/lib/api` WITHOUT `getNotificationUnreadCount`, SO THE BADGE PATH WAS
  EXECUTED BY NO TEST — AND THE SUITE WAS GREEN *BECAUSE* THE POLL FAILED.** The call was `undefined(...)`,
  every poll threw, and the effect's own "a failed poll keeps the last count" catch swallowed it in all 18
  tests. **⚠️ A `jest.mock` factory is an ALLOW-LIST: a module the component imports and the factory omits
  fails silently at the call site, not at import.** Added the key, a missing `mockReset` (call counts were
  leaking between tests), and three tests covering poll → state → prop → badge; the wiring test is killed
  by replacing the prop with a literal `0`.
- **⚠️ `deliver` ACCEPTED AN UNVALIDATED `ctaPath`, falsifying the validator's own "ONE VALIDATOR, ONE
  LOCATION" javadoc.** Announcement create/update validate, so the guarantee held only while announcements
  stayed the sole producer — and Stage 5 will not be an announcement. `deliver` now validates as the last
  chokepoint before a link is persisted into an inbox.
- **⚠️ RE-PUBLISH IS A TOP-UP, NOT A PURE RETRY, AND BOTH THE JAVADOC AND THE FEATURE DOC SAID OTHERWISE.**
  `fanOut` re-resolves the audience **at call time**, so anyone who signed up, changed profile type or
  upgraded plan since the first publish receives it on the second. Existing recipients are deduped by the
  index. The behaviour is defensible and unchanged; the claim was wrong and is corrected in both places.
- **⚠️ "Unread actionable rows are RETAINED regardless of age" was FALSE as written** — the predicate is
  `read_at IS NOT NULL OR dismissed_at IS NOT NULL`, so a dismissed-unread row IS deletable. Corrected in
  `RELEASES.md` and `docs/features/notifications.md`.
- **Three `v0.129.0` defects, fixed here and corrected in that release's own section** — a vacuous
  `applySourceUpdate` guard whose false claim reached both `RELEASES.md` and the published release notes,
  and a completely unguarded public-detail adoption wiring (**mutation-proved: literal `0` left all 219
  tests green**). Details under `v0.129.0` → Known limitations.

**⚠️ SURFACE SWEEP — the false "creates none" claim had reached FIVE documents, and only two were in any
diff.** The repo's rule is to sweep by SURFACE rather than by diff when a release changes what a claim
means, and this is the fourth release running where that is where the finding was. Corrected at the
origin (`docs/claude-plans/in-app-notifications-and-review-set-adoption-signals-stage1.md:417`, which is
where the claim was first written and from which the vacuous guard was authored), in `RELEASES.md`, in
`docs/releases/v0.129.0.md`, and in **`docs/gpt-contexts/GPT_CONTEXT.md`, which is pasted into GPT
sessions as fact and was not in any diff.** The spent Codex prompts under `docs/codex-prompts/` carry it
too and are left alone — untracked, and superseded by the shipped code. **⚠️ The same sweep caught the
admin confirm dialog** saying re-publish *"re-runs delivery for anyone the first attempt missed. Nobody
receives it twice"* — the copy an admin reads immediately before firing an irreversible action, and
incomplete in exactly the way the javadoc was. It now names the top-up.

### Known limitations

- **⚠️⚠️ FAN-OUT HOLDS ONE OF TWENTY HIKARI CONNECTIONS FOR ITS WHOLE DURATION, AND THIS IS THE SHAPE OF
  BOTH RECORDED PRODUCTION OUTAGES.** The previous wording of this limitation reasoned only about gateway
  timeout and called it "a scaling ceiling rather than a correctness gap". **That was the wrong frame.**
  With `open-in-view=ON` and `DELAYED_ACQUISITION_AND_HOLD`, the connection is held until the HTTP request
  ends, not until each insert commits — so a publish to 400 recipients is 400 transactions and ~800
  prepared statements on **one held connection**, with the persistence context never cleared (O(N²)
  growth). **⚠️ The blast radius is the whole application, not the admin's request:** a large publish can
  starve the pool that every learner request draws from. **Not fixed here on purpose** — bounding it means
  moving fan-out off the request thread, which is a design change owed its own release and its own
  verification, not a late patch to a release that has already had this much rework. **It now owes a
  `[CHECKPOINT]` keyed on user count, not on a date** (below), because this is the one finding that will
  hurt without warning.
- **⚠️ A publish whose deliveries ALL fail still returns HTTP 200 "Published" with `delivered: 0`.** Read
  alone this is a cosmetic reporting gap; read beside the item above it is not, because that item is what
  makes total failure plausible. **The status code is left as-is deliberately** — partial failure must not
  roll back, and choosing the right non-200 semantics for "published but delivered to nobody" belongs with
  the fan-out redesign, not ahead of it. **Mitigated rather than fixed:** the admin surface now says
  *"Nobody actually received it — check the logs before assuming it went out"* when `delivered` is 0 and
  the audience was not empty, so the signal is no longer a number the admin has to notice unaided.
- **⚠️ AND THE `deliver`-SIDE CTA VALIDATION ADDED IN THIS RELEASE CAN REACH THAT HOLE THROUGH A NEW DOOR
  — recorded because the two findings are otherwise adjacent and unconnected.** `deliverOne` catches
  `RuntimeException` broadly, so if a future producer hands `deliver` a bad `ctaPath`, **every** recipient
  is counted as `skipped` and the publish reports `200 OK {delivered: 0}` rather than failing loudly. The
  broad catch is the right call for the partial-failure contract and is not being narrowed here. **Not
  reachable today**: announcement create *and* update both validate, `V140` is new so no legacy rows
  exist, and announcements are the only producer. **⚠️ It becomes reachable the moment Stage 5 adds a
  producer that does not validate on write** — which is exactly why the chokepoint was added.
- **⚠️ ONE NEW GUARD RESTS ON A FIXTURE PRODUCTION HAS NOT CONFIRMED.**
  `applyingSourceUpdateCreatesAnAdoptionOfANEWLYAddedChildSubjectPlan` — the test anchoring the
  `v0.129.0` correction — builds the newly-added child as **PRIVATE** (the helper's default), and
  `applySourceUpdate` copied it. Either Goal children are PUBLIC in production, making this the very
  "fixture no code path can produce" shape this release documents elsewhere, or PRIVATE children really
  are copied into an adopter's library and a collection shell crosses an ownership boundary on the update
  path. **The guard kills its mutation either way, so the correction stands** — but which case it is, is
  unresolved, and `docs/claude-plans/v0.130.0-owner-production-checks.sql` Q3 asks production. (The
  *notes* inside are separately gated: `applyPlacementAddition` throws for a non-public source note.)
- **⚠️ `NotificationCleanupJob.run()` is invoked by no test**, and its two exception classes are untested.
  The scheduled-cron contract test proves it is *registered*; nothing proves it *deletes*.
- **⚠️ THE NOTIFICATION INTEGRATION TESTS HAND-WRITE THEIR H2 DDL AND CAN SILENTLY DRIFT FROM `V139`/`V140`.**
  This is the "fixture no code path can produce" anti-pattern at schema level, and it weakens every
  confirmation those tests provide. Neither new JPQL query is executed against PostgreSQL by any test.
  **⚠️ The existing `NativeQueryPostgresIntegrationTest` does not cover this** — it prepares *native*
  queries, and these are JPQL.
- **⚠️ `app/admin/announcements/page.tsx` is 460 lines with ZERO tests.** Admin-only and low blast radius,
  which is why it is recorded rather than fixed, but it is the largest untested file the release added.
- Smaller, each real and each recorded rather than fixed: a `datetime-local` round-trip shifts a draft's
  expiry instant; a failed mark-read restores from a snapshot rather than functionally, so it can
  resurrect a row dismissed server-side in between; the poll runs on routes that render no bell; the admin
  form stays in edit mode after publishing; and the test named
  `restores the row and the badge when a dismiss fails` asserts the row but never the badge.
- **⚠️ AGENT 3's PRODUCTION INDEX CHECK WAS BLOCKED AND IS THEREFORE UNVERIFIED AGAINST PRODUCTION.** The
  `v0.129.0` index claim is confirmed **in-repo only**. Two read-only SELECTs are handed to the owner in
  `docs/claude-plans/v0.130.0-owner-production-checks.sql` rather than being reported as verified.

## v0.129.0 - Adoption Signal

**Status: Released** (kicked off and signed off 2026-09-07, base branch `releases/v0.129.0`, cut from `main` after `v0.128.0` merged)

Theme: show how many learners have adopted an Official Review Set — **Stage 2 of the in-app notifications plan, which is the one stage that does not depend on notifications existing.**

Source: `docs/claude-plans/in-app-notifications-and-review-set-adoption-signals-stage1.md`, §17. **⚠️ THAT DOCUMENT'S OWN INSTRUCTION IS TO KEEP THIS SEPARATE: *"Stage 2 is independent of every other stage and is the cheapest real user-visible win — one index, one query, two surfaces. Do not fold it into the notification work."* NO notification table, inbox, bell, badge, announcement or polling is in scope.**

### Planned Scope

**(1) The count, and the one index it needs.** `SELECT COUNT(*) FROM note_collections WHERE source_plan_id = :officialId AND owner_user_id <> :officialOwnerId`. **⚠️ NO `DISTINCT`:** `idx_note_collections_owner_source_plan` (`V76`) is a **partial UNIQUE** index on `(owner_user_id, source_plan_id)`, so one learner can hold at most one collection per source **as a database invariant** — rows-per-source already equals learners-per-source. **⚠️ THE ONLY SCHEMA CHANGE IS ONE INDEX**, and the reason is precise: that existing index leads on `owner_user_id`, so a query filtering on `source_plan_id` alone cannot use it.

**(2) Two surfaces.** Explore/discovery cards (compact, `1.2K adopted`) and Official Review Set detail (fuller wording). **⚠️ NOT onboarding** — social proof during the one flow where NoteLib is making the recommendation distorts it. **⚠️ EXPLORE MUST BATCH: one grouped `WHERE source_plan_id IN (:visibleIds) GROUP BY source_plan_id`, never one count per card.**

### Production reads — RUN 2026-09-07, read-only, BEFORE scoping rather than after

The plan's §10 listed two reads as prerequisites and assumed both were the owner's. **⚠️ They are `SELECT`s, which are Claude's to run under this repo's own read-only rule, so they were run and the results are here rather than deferred.**

| finding | value |
|---|---|
| provenance completeness | **91.2%** (536 of 588 collections carry `source_plan_id`) |
| adopter distribution | **40, 40, 40, 40, 40, 30, 28, 15 ×7, 8, 4 ×7, 1, 1** |
| curator self-copies | **zero** — `adopters_excl_owner` equals `adopters_incl_owner` on every row |

**⚠️ §10's "if provenance completeness is materially low, public display should WAIT" DOES NOT TRIGGER — 91.2% is high.** The exclusion of the Official owner stays in the query for correctness even though it currently changes nothing.

**⚠️⚠️ A THIRD FINDING THE PLAN COULD NOT HAVE ANTICIPATED, AND IT IS A DISPLAY PROBLEM RATHER THAN A COUNTING ONE: ADOPTING A GOAL FANS OUT TO ITS CHILDREN, so a parent and its subject plans show near-identical counts.** `LET Comprehensive Review` is 40 and each of its four children is **also 40**; `PNLE Core Nursing Review` is 15 and its seven children are **each 15**. **⚠️ The counts are CORRECT and the plan's parent/child independence rule is right — each child's `source_plan_id` genuinely points at the child source, and they must NEVER be summed into the parent.** But the numbers will read as duplicated down a Goal's subject list. **⚠️ AND THE FAN-OUT IS NOT UNIFORM, WHICH IS WHY IT CANNOT BE SPECIAL-CASED AWAY: `CPALE Comprehensive Review` is 8 while its children are 4, and `ALE` is 30 against a child at 28** — adopters who joined before a child existed. So "just show it on the parent" would be wrong too.

**⚠️⚠️ CORRECTED 2026-09-07 BEFORE ANY CODE WAS WRITTEN — THE FAN-OUT IS REAL IN THE DATA BUT INERT ON BOTH SURFACES IN SCOPE, AND THE FIRST WORDING OVERSTATED IT.** `listPublic` calls `findByVisibilityAndParentCollectionIdIsNullOrderByUpdatedAtDesc`, so **Explore renders TOP-LEVEL collections only** — children are fetched solely to roll up item counts and are never cards. `getPublic`'s `toPublicDetailResponse` returns a `childCount` **number**, not child summaries, so the public detail page has no per-child list to hang a count on. **⚠️ THEREFORE THERE IS NO "duplicated down a Goal's subject list" TO FIX IN THIS RELEASE — do NOT build a suppression rule, a roll-up, or a parent/child display heuristic for a problem no surface currently exhibits.** The fact is kept because it goes live the instant any surface renders children with counts; it is not a `v0.129.0` deliverable.

### Owner decision — SETTLED

**✅ THRESHOLD DECIDED BY THE OWNER 2026-09-07: **5**.** A Review Set shows its adoption count only when it has **5 or more** adopters; below that the count is **omitted entirely** — not shown as "fewer than 5", not shown as a range, which would leak the same smallness the threshold exists to hide. **⚠️ THIS IS DISPLAY POLICY ONLY — the stored/queried count is exact and unaffected.** **⚠️ AND THE THRESHOLD IS CURRENTLY INERT TOO, WHICH IS THE OTHER HALF OF THE SAME CORRECTION: every top-level PUBLIC set is ALREADY ≥8** — LET 40, ALE 30, PNLE 15, CPALE 8, and those four are the entire Explore surface. **The `4`s and `1`s are all CHILDREN**, and the `1`s are PRIVATE. So threshold 5 hides **nothing today** — it is a DEFENSIVE policy for the first small set that gets published top-level, not an active filter. **⚠️ Do NOT claim it "hides nine sets"** — that counted rows the surfaces never render. **⚠️ Do NOT re-derive this threshold from a fresh distribution read** — it is an owner decision, not a computed value, and a later read showing different counts does not change it. The alternative considered was 10, which would additionally have hidden the `8` (`CPALE Comprehensive Review`); it was not chosen.

### Anti-drift

**⚠️ NO Learning Connections work** — `[CHECKPOINT — due 2026-09-19]` is **twelve days out** and decides whether that arc continues at all. **⚠️ Do NOT build any part of Stages 3-7** — no `notifications` table, inbox, bell, badge, announcement, fan-out, polling or scheduled sweep. **⚠️ Do NOT write "N learners adopted this"** — the repo cannot prove every counted owner is a learner, and `ProfileType` must NOT be used to filter adoptions to justify the word. **⚠️ Do NOT roll child adoptions into a parent's count. Do NOT rank Official Review Sets by adoption count** — popularity is not curriculum quality. **⚠️ Do NOT add a denormalized or event-maintained counter** before measurement; query-time count only. **⚠️ Do NOT expose adopter identities, avatars, or an adoption feed**, and never "N other learners also updated". **⚠️ Do NOT reconstruct provenance from titles, names or note overlap** — the 8.8% without it are excluded and under-count; report that, do not fix it. **⚠️ No quota, entitlement or pricing change; onboarding untouched.**

### Verification

**A single `advisor()` call, per the plan's own Verification section** — Stage 2 is one index, one query and two read-only surfaces, and it moves no authorization or privacy boundary. **⚠️ The migration adds an index only — no column, no data write — so it does not trigger the production-data-semantics escalation.**

**Pre-declared guards, from the plan's discriminating list, reduced to the four that apply to this stage:**
- **(1)** adopt → delete → re-adopt returns the count to its prior value.
- **(2)** applying a source update does **not** change the count — asserted either side of `applySourceUpdate`, which mutates rows and creates none. **⚠️⚠️ THIS CLAIM WAS FALSE AND WAS DISPROVED BY THE `v0.130.0` PRESSURE TEST — see the correction under `v0.129.0` → Known limitations below.** `applySourceUpdate` DOES create rows carrying a `sourcePlanId`, via `createSubjectAddition`, and the guard that "proved" otherwise was vacuous.
- **(3)** adopting a child Subject Plan leaves the parent's count unchanged.
- **(4)** **⚠️ Explore issues ONE count query for N cards — ASSERT THE QUERY COUNT, NOT THE RENDERED NUMBERS.** A test that only checks the displayed figures passes an N+1 implementation.

**⚠️ CARRIED LESSON, NOW TWICE-BURNED: confirm a mutation is PRESENT before trusting a green suite.** And the `v0.128.0` lesson on top of it: **anchor every quoted defect to the CURRENT file at kickoff** — that release opened on an item that had already shipped nine days earlier because the kickoff copied planned-scope prose forward instead of re-reading the code.

**Routing: CLAUDE CODE inline for the frontend surfaces; the index + count query is a backend change and gets a Codex prompt if it grows past the query and its test.**

### Shipped

- **Official Review Set adoption counts are query-time, exact public aggregates.** The backend adds a
  partial `source_plan_id` index (`V138`) and one batched JPQL self-join that excludes the Official
  source's owner. `/collections/public` and `/collections/public/{id}` return non-null
  `adoptionCount`; the display threshold remains a frontend-only rule. **⚠️ No `DISTINCT`** — `V76`'s
  partial UNIQUE index already makes one-learner-one-adoption a database invariant — **and no
  denormalized counter, no ordering by the count, and no threshold logic anywhere in the backend.**
- **⚠️ The audit MUTATION-VERIFIED the two claims a green suite could not have proven, each confirmed
  PRESENT before its run.** This is the `v0.93.0` lesson applied: that release's headline conditional
  insert survived a mutated predicate because every repository reference in the test tree was a mock.
  - **Dropping `adoption.ownerUserId <> source.ownerUserId`** fails
    `countAdoptionsExcludesTheOfficialOwnerAndKeepsParentAndChildSourcesIndependent` — **against real
    PostgreSQL, not a mock**: the count came back `3` where `2` was expected. Predicate correctness is
    therefore actually exercised, not merely parsed.
  - **Replacing the batched call with a per-collection one** — a real N+1 — fails
    `listPublic_loadsAdoptionCountsOnceForTheFullVisibleCollectionList`, which asserts the repository
    is invoked **once with the full id list**. **⚠️ That test asserts the INVOCATION, not the rendered
    numbers**, which is the only form of the guard an N+1 cannot pass.
- **`V138` was applied by Flyway against the real PostgreSQL 16 container** (*"Successfully applied 138
  migrations … now at version v138"*), so the migration is verified as valid PostgreSQL rather than
  assumed. It is a plain partial `CREATE INDEX` — **not `CONCURRENTLY`, which cannot run inside
  Flyway's transaction.**
- **Owner-scoped and non-public mappers pass `0` rather than issuing a query**, since adoption counts
  are a discovery signal and those surfaces do not show one.

**Backend build: `./mvnw clean install` green — 2227 tests, 0 failures**, re-run clean after every
mutation was reverted.

### Known limitations, and why NO checkpoint is owed

**⚠️⚠️ CORRECTIONS ADDED 2026-09-07 BY THE `v0.130.0` PRESSURE TEST — TWO CLAIMS IN THIS ALREADY-SIGNED-OFF
SECTION WERE FALSE.** Recorded here rather than only under `v0.130.0` because a future prompt reads this
section as the truth about adoption counts, and a corrected claim is worthless if it lives somewhere the
reader will not be.

- **⚠️ CORRECTION — guard (2) was FALSE, and the test that "proved" it was VACUOUS.** The Verification
  section above claimed `applySourceUpdate` "mutates rows and creates none". It does create rows:
  `createSubjectAddition` (`NoteCollectionService.java:2307`) saves a new `NoteCollectionEntity` with
  `setSourcePlanId(sourcePlan.getId())` whenever a curator has added a Subject Plan to a Goal upstream.
  The guard, `applyingSourceUpdateDoesNotChangeTheAdoptionCount`, used a **leaf fixture with no
  children**, so the loop that creates rows was never entered — short-circuiting the whole method also
  passed it. **⚠️ THE NUMBER WAS NEVER WRONG: by the count's own definition an adopter who gains a copy
  of a newly-added Subject Plan IS an adopter of it, so the child's count rising by one is correct.**
  What was wrong was the claim and the guard. Fixed in `v0.130.0`: the test is renamed
  `applyingSourceUpdateToALeafPlanDoesNotReCountItsExistingAdopter` (it does prove that a re-sync never
  double-counts an existing adopter) and joined by
  `applyingSourceUpdateCreatesAnAdoptionOfANEWLYAddedChildSubjectPlan`, which fails if the creation path
  is removed. `docs/releases/v0.129.0.md` carried the same claim in user-facing wording and is corrected.
- **⚠️ CORRECTION — the public-detail adoption wiring shipped with NO GUARD AT ALL.** Mutation-proved:
  replacing the `adoptionCount` argument at `NoteCollectionService.java:3021` with a literal `0` left
  **all 219 tests in `NoteCollectionServiceTest` green**, because the only test that touched the field
  stubbed an empty projection list and asserted `isZero()` — the value the defect produces too. This is
  the "a guard is only worth what it executes" failure in its purest form, and the zero-valued fixture is
  the same shape as the vacuous guard above. Closed in `v0.130.0` by
  `getPublic_carriesTheAdoptionCountThroughToTheAnonymousPayload`, which stubs a non-zero count and is
  killed by that exact mutation.
- **⚠️ `adoptionCount` is hardcoded `0` on the AUTHENTICATED detail mapper** (`NoteCollectionService.java:2985`)
  while `lib/api.ts` documents the field as exact. No surface reads it there — the count renders from the
  public payloads — so this is inert, but it is a live contradiction between code and its own API doc and
  is recorded rather than silently left. **⚠️ Do not "fix" it by wiring a count in without a consumer**;
  that is the `v0.116.0`/`v0.117.0` silent-no-op shape.
- **⚠️ The threshold rationale slightly overstates its effect.** The exact sub-threshold count is still
  returned to anonymous callers in the API payload; only the *rendering* is suppressed. The threshold
  hides the number from the page, not from anyone reading the response.

- **⚠️ THE THRESHOLD IS SHIPPED BUT UNEXERCISED IN PRODUCTION, AND THAT IS A KNOWN LIMITATION RATHER
  THAN A DEFECT.** Every top-level PUBLIC set is already ≥8 (LET 40, ALE 30, PNLE 15, CPALE 8), and
  only top-level sets render on Explore, so the hide branch **has never fired against real data**. It
  is covered by unit tests and will fire the first time a small set is published top-level. **⚠️ Do
  not "verify" it by lowering the threshold — that is an owner decision, not a test fixture.**
- **Legacy rows without provenance (8.8% of collections) are excluded and under-count**, by decision.
  **⚠️ Never reconstruct provenance from titles, names or note overlap.**
- **NO `[CHECKPOINT]` ROW IS OWED, and the reason is the denominator rather than the absence of a
  question.** The obvious one — *does showing adoption counts increase adoption?* — is measurable
  from `note_collections` without new instrumentation, so it clears the "decorative checkpoint" bar
  on that axis. **⚠️ But it fails clause 3 of the bootstrap test: it would be read across FOUR
  top-level sets at ~0.7 signups/day, which cannot separate a social-proof effect from noise.**
  Writing it would reproduce the underpowered read this release cycle has now diagnosed twice — the
  onboarding `n=18` and the Learning Connections `n=1`. **⚠️ Stage 2's own EVIDENCE gate was CLEARED
  before building** (provenance 91.2%, distribution read), so nothing here shipped ahead of its
  evidence; the display threshold was an owner decision, not an evidence gate.

- **The adoption-count display rule lives in one module, so the two surfaces cannot drift.**
  `frontend/lib/adoption-count.ts` owns the threshold, the compact/detailed wording and the
  show/hide decision; `PublicStudyPlanCard` renders `40 adopted` on the card and
  `Adopted by N study libraries` in its preview. **⚠️ Below the threshold NOTHING is rendered** —
  not *"fewer than 5"*, not a range, since either still discloses the smallness the threshold exists
  to hide, and a test asserts the **absence** of any adoption text rather than the presence of a
  softer one.
- **⚠️ The threshold is applied at DISPLAY ONLY and the API field stays exact.** `adoptionCount` is
  optional on `NoteCollectionSummary`/`NoteCollectionDetail`, so a payload without it degrades to
  silence rather than to *"0 adopted"* — which also means **this frontend is safe to ship before the
  backend and renders nothing until it lands.**
- **Wording is "adopted", never "N learners adopted this"**, with a test asserting no `/learner/i`
  appears in either label — the repo cannot prove every counted owner is semantically a learner.
- **Compact formatting truncates rather than rounds**, so `1999` reads `1.9K` and never `2K`: the
  label must not claim more adopters than exist.

**Verification (frontend half).** Three mutations, **each confirmed present in the file before its
run**: threshold `5 → 0` fails six tests across both suites; `Math.floor → Math.round` fails exactly
the truncation test; and stubbing the card's label to `null` — the silent no-op class — fails exactly
the card's render test.

**⚠️ Backend is a Codex prompt, not built here** (`docs/codex-prompts/v0.129.0-adoption-count-backend.md`,
untracked by design). It carries the one index, the batched self-join query, and the guard that the
backend must return the **exact** count with no threshold logic.

## v0.128.0 - Onboarding Unfrozen

**Status: Released** (kicked off and signed off 2026-09-07, base branch `releases/v0.128.0`, cut from `main` after `v0.127.0` merged and tagged)

Theme: the work that was blocked only by the onboarding freeze, now that the read the freeze protected has been taken.

**⚠️ THE FREEZE IS LIFTED BY OWNER DECISION (2026-09-07), AND THE READ IT PROTECTED WAS TAKEN FIRST RATHER THAN ABANDONED.** `[CHECKPOINT — due 2026-09-11]` froze `frontend/app/onboarding` to protect a signup-funnel read against a **62.4% completion baseline that cannot be re-run**. Taken read-only on 2026-09-07, four days early: **393 signups all-time, 249 completed, 63.4%** — essentially flat.

**⚠️⚠️ THE FINDING THAT DISCHARGES THE CHECKPOINT IS THE DENOMINATOR, NOT THE RATE. The cohort since the `v0.73.0` redesign (2026-08-12) is EIGHTEEN SIGNUPS** — 15 completed, 83.3%. At ~0.7 signups/day, waiting to 2026-09-11 adds about three more. **83.3% on n=18 is noise, not a result.** The checkpoint was never going to be answerable on its own date; **the freeze was protecting a read that cannot be taken.** That is exactly the underpowered-denominator failure this repo's own checkpoint doctrine names, and it is why lifting is not a trade-off.

**⚠️ Do NOT re-freeze onboarding for this checkpoint, and do NOT quote 62.4% or 83.3% as a current figure** — the first is a stale baseline, the second has n=18.

### Planned Scope

**(1) Summary maths renders. ⚠⚠ WITHDRAWN — THIS ALREADY SHIPPED IN `v0.96.0`, AND THE ITEM AS WRITTEN DESCRIBED CODE THAT NO LONGER EXISTED AT KICKOFF.**

**The contract was wrong, not merely stale.** This section claimed `components/ui/summary-markdown.tsx` "runs `react-markdown` + `remark-gfm` with **no math plugin**." It does not, and had not for nine days. Verified 2026-09-07 against the working tree at `74cc0df5` (clean):

| evidence | result |
|---|---|
| `git describe --contains 3652e3d6` | **`v0.96.0`** (commit dated 2026-08-29) |
| `RELEASES_ARCHIVE.md:8280` | records it shipped, "on all nine consumers including the SEO-indexed public pages" |
| file on disk | `remark-math` wired as tokenizer → `renderExtractedMath`, with the "`rehype-katex` deliberately NOT used" design comment intact |
| declared guards (a)/(b) | already present in `components/ui/summary-markdown.test.tsx` — including the underscore fixture at `:16`, the one that **cannot** pass under the defect |

**⚠️ The residual was checked, not assumed.** The concern behind guard (b) was a straggler rendering Summary through its own `react-markdown` instance. `grep -rln 'from "react-markdown"'` across `app/`, `components/` and `lib/` returns **exactly one file** — `summary-markdown.tsx` itself. Every consumer routes through `SummaryMarkdown`, so there is no leg (b) remainder. (The handoff's "ten consumers" against the archive's "nine" is doc drift in the count, not a missed surface.)

**⚠️ CAUSE — recorded because it is a process finding, not a typo: the kickoff copied `v0.96.0`'s PLANNED-SCOPE PROSE FORWARD instead of re-reading the code.** `RELEASES_ARCHIVE.md:8188–8226` carries the same sentences ("runs `react-markdown` +", "Add `remark-math` for TOKENIZATION only"). Nothing was lost — no code was written against the false premise — but a release opened with an item that could never have been done. **⚠️ A kickoff that quotes a defect must anchor that quote to the current file, not to the section that first described it.**

**⚠️ `v0.86.0`'s CORRUPTED-ESCAPE item is still open and is NOT closed by this** — it is a different defect, on stored content.

**(2) Catalog-first suggestions in onboarding** — the deferred half of `v0.79.0`, **and by that release's own baseline the load-bearing one**. Onboarding was excluded from `v0.79.0` precisely to protect the read now discharged. **⚠️ WITH ITEM 1 WITHDRAWN THIS IS THE WHOLE RELEASE.**

**⚠️ DO NOT BUILD A NEW MECHANISM — `v0.79.0` ALREADY SHIPPED IT.** `buildCatalogFirstCourseProgramSuggestions` (`lib/learning-profile.ts:133`) and the `useCourseProgramCatalogNames` hook are live on **four** surfaces (`app/profile/page.tsx`, `components/dashboard/lightweight-profile-completion-prompt.tsx`, `components/notes/note-editor-page-client.tsx`, `components/notes/private-note-detail-page-client.tsx`). Onboarding is the one deliberate exclusion and still passes the raw constant at `app/onboarding/page.tsx:1524`. The work is to fetch the catalog on that screen and route through the existing helper, mirroring the dashboard-prompt call site.

**Instrumentation is included, by owner decision (2026-09-07).** `trackCourseProgramValueSelected` fires from the **existing** commit point at `page.tsx:1119` (`updateLearningProfileContext`), which is the direct analogue of the dashboard prompt's call site — **no new flow step, no reordering.** **⚠️ This adds ONE member (`"onboarding"`) to the frontend `CourseProgramSelectionSurface` union and NOTHING ELSE: the `COURSE_PROGRAM_VALUE_SELECTED` enum value already exists, `surface` is NOT server-validated (it rides in the free-form metadata map), so there is NO backend change and NO migration.** Rationale: `v0.79.0`'s whole measurement is the off-catalog selection rate, and without onboarding that metric has a hole exactly where pick volume is highest — every account passes this screen once.

**⚠️ FREE TEXT STAYS ALLOWED.** `v0.79.0` shipped the **counter-proposal**; locking the field and the *"Request Program"* queue remain **PROPOSED AND UNRATIFIED** under `ADR-001`, and shipping this does **not** ratify them.

### Anti-drift

**⚠️ NO Learning Connections work.** `[CHECKPOINT — due 2026-09-19]` is **twelve days out**, its kill criterion keys on `ACCEPTED`, and it decides whether that arc continues at all — touching it would contaminate the one number that decides. **⚠️ Item 2 must NOT lock the course/program field or add a request queue**: `v0.79.0` shipped the **counter-proposal**, free text **stays allowed**, and the `ADR-001` amendment remains unratified — shipping this does not ratify it. **⚠️ Do NOT add, remove or reorder an onboarding FLOW step beyond what these two items require** — the freeze is lifted, not the judgment behind it. **⚠️ Do NOT change what `BOARD_EXAM_STARTED`, `ADAPTIVE_PRACTICE_STARTED`, `QUIZ_SHARE_LINK_*` or `GUIDANCE_TIP_SHOWN` record. NO migration, no quota/entitlement/meter change, no new mode or sub-mode, no `ProfileType` gate.**

### Verification

**⚠️ TIER RE-DECIDED 2026-09-07 AFTER ITEM 1 WAS WITHDRAWN — RECORDED, NOT SILENTLY DOWNGRADED.** The pre-declared tier was one scoped cold agent, justified by **two** things: a renderer shared by nine-plus consumers including SEO-indexed public pages (item 1), and the signup path (item 2). **Item 1 turned out to have shipped in `v0.96.0`, so half that rationale does not exist** and guards (a) and (b) are already-passing `v0.96.0` tests rather than work this release owes.

**The tier is KEPT, narrowed to item 2, on `model: "sonnet"`.** The release is now one file plus a test, which does not justify Opus or a full pressure test — but the surviving half of the rationale is the signup path, and the specific risk is the one this repo has been bitten by twice (`v0.116.0`, `v0.117.0`): **a change that compiles, passes, and does nothing.** `useCourseProgramCatalogNames` swallows every failure to `null`, and `buildCatalogFirstCourseProgramSuggestions(null, …)` falls straight back to `COURSE_PROGRAM_SUGGESTIONS`, so a catalog that never arrives renders **identically to today and passes any test written against it**.

**Single falsification claim handed to the agent:** *the catalog actually reaches the onboarding screen for an account with `onboardingCompletedAt == null`, and the rendered suggestion list differs from `COURSE_PROGRAM_SUGGESTIONS`.*

**⚠️ THAT CLAIM WAS PRE-CHECKED AGAINST PRODUCTION AND THE BACKEND BEFORE ANY CODE WAS WRITTEN, so the agent is falsifying a claim with evidence behind it rather than guessing:** `CourseProgramCatalogController.list()` is `@PreAuthorize("hasAnyRole('USER','ADMIN')")` — **role-gated only, NOT gated on `onboardingCompletedAt`**, so it is reachable mid-onboarding; and a read-only production count returned **44 rows in `course_programs`** against **31** hardcoded `COURSE_PROGRAM_SUGGESTIONS`, so the list demonstrably changes.

**⚠️ PRE-DECLARED GUARDS — status, each naming the fixture that proves nothing: (a)** a Summary containing `$x_1 + x_2$` renders maths and **not** `<em>` — **a fixture without an underscore passes under the defect**. **ALREADY MET by `v0.96.0`** (`summary-markdown.test.tsx:16`); not re-claimed here. **(b)** a Summary containing **no** maths renders **byte-identically** to today. **ALREADY MET by `v0.96.0`**, and the straggler check behind it (one `react-markdown` importer repo-wide) was re-run for this release. **(c)** onboarding completes end to end with a catalog-sourced program **and** with free text, because free text staying allowed is the counter-proposal's whole point — **THE ONLY GUARD THIS RELEASE OWES.**

**⚠️ GUARD (c) FOUND A TEST ASSERTING THE OLD BEHAVIOUR, WHICH IS WHY IT IS WRITTEN AND NOT ASSUMED.** `app/onboarding/page.test.tsx` carried a source-level pin — *"deliberately keeps the hardcoded Course / Program suggestions until the checkpoint"* — asserting `suggestions={COURSE_PROGRAM_SUGGESTIONS}` **and** `not.toContain("useCourseProgramCatalogNames")`. It was correct when written: it pinned `v0.79.0`'s deliberate exclusion of onboarding, whose whole reason was the now-discharged checkpoint. **⚠️ It is removed because its PREMISE is gone, not because it broke** — and it is **not** replaced with an inverted source assertion, since reading `page.tsx` as a string cannot tell whether the catalog actually reaches the screen, which is the only thing worth guarding. The replacement guards are behavioural, in the new `app/onboarding/onboarding-course-program.test.tsx`.

**⚠️ CARRIED LESSON FROM `v0.127.0`: confirm a mutation is PRESENT before trusting a green suite** — one silently failed to apply and the run passed.

**Routing: CLAUDE CODE inline.**

### Shipped

- **Onboarding's Course / Program field is catalog-first, closing the deferred half of `v0.79.0`.**
  `app/onboarding/page.tsx` was the one surface of five still passing the hardcoded
  `COURSE_PROGRAM_SUGGESTIONS`; it now resolves suggestions through the **existing**
  `useCourseProgramCatalogNames` hook and `buildCatalogFirstCourseProgramSuggestions` helper,
  mirroring `lightweight-profile-completion-prompt.tsx`. **No new mechanism was built.** Catalog names
  come first, the hardcoded list is appended rather than replaced, and **free text stays allowed** —
  `v0.79.0` shipped the counter-proposal, so the field is **not** locked and no request queue was
  added; the `ADR-001` amendment remains unratified.
- **The selection is instrumented, closing the hole in `v0.79.0`'s own metric.**
  `trackCourseProgramValueSelected("onboarding", …)` fires from the **existing** commit point inside
  `selectLearnerLevel`, the awaited `updateLearningProfileContext` write — **no flow step was added,
  removed or reordered.** One member added to the frontend `CourseProgramSelectionSurface` union;
  **no backend change and no migration**, because `COURSE_PROGRAM_VALUE_SELECTED` already exists and
  `surface` rides in the free-form metadata map. **⚠️ De-duplication is real, not incidental:** the
  event is passed the last *committed* value as its `previousValue`, so a learner who returns and
  changes only their learner level — which re-runs the same write — does not fire a second selection
  for a program they never re-picked.
- **⚠️ The event is deliberately SUPPRESSED when the catalog fails to load.** `matchedCatalog` cannot
  be computed without a catalog, and an unclassifiable selection is worse than a missing one — it
  would silently inflate the off-catalog rate the metric exists to measure.
- **A stale test that pinned the old behaviour was removed, and the removal is the finding.**
  `app/onboarding/page.test.tsx` asserted `suggestions={COURSE_PROGRAM_SUGGESTIONS}` and
  `not.toContain("useCourseProgramCatalogNames")` under the name *"deliberately keeps the hardcoded
  Course / Program suggestions until the checkpoint"*. **It was correct when written** — it pinned the
  exclusion protecting the read this release discharged — and is removed because its premise is gone,
  **not** to accommodate a change that broke it. **⚠️ It was NOT replaced by an inverted source
  assertion:** reading the file as a string cannot tell whether the catalog reaches the screen.

- **⚠️ Item 1 (Summary maths) was WITHDRAWN, not shipped — it was already done in `v0.96.0`.** See the
  Planned Scope note above; the cause was a kickoff copying `v0.96.0`'s planned-scope prose forward
  instead of re-reading the file, and the residual check (one `react-markdown` importer repo-wide) was
  re-run to confirm nothing was left over.

**Verification.** New `app/onboarding/onboarding-course-program.test.tsx` — four behavioural tests
covering guard (c) in both directions. **⚠️ ALL THREE MUTATIONS WERE CONFIRMED PRESENT IN THE FILE
BEFORE THEIR RUN, per the carried `v0.127.0` lesson**, and each named test that killed them:
- **Reverting the wiring** to the raw constant — the `v0.116.0`/`v0.117.0` silent no-op, and the one
  mutation that matters here — fails *"lists catalog names ahead of the hardcoded suggestions"* and
  *"completes the step with a catalog-sourced program"*. **⚠️ It fails only because the catalog fixture
  is disjoint from `COURSE_PROGRAM_SUGGESTIONS`; a fixture reusing a constant entry would PASS under
  the no-op.**
- **Dropping the analytics call** fails the catalog-sourced and free-text completion tests.
- **Locking the field** (`allowCustom={false}`, the unratified `ADR-001` amendment) fails exactly
  *"still completes the step with free text"* — the guard that keeps the counter-proposal from being
  silently ratified.
- **Removing the resume seeding** fails exactly *"does not re-report a program the learner already
  committed in an earlier session"*.

**A de-duplication hole was found in review and closed.** The tracking ref is per-page-load, so it
started at `null` on every mount: a learner **resuming** onboarding with a program already committed
in an earlier session, who stepped back through the learner-level screen, would fire a second
`COURSE_PROGRAM_VALUE_SELECTED` for a value that never changed. The ref is now seeded from
`me.courseProgram` when the draft loads. **⚠️ Seeded from the STORED value only, not the draft** — a
draft value that was never committed has never been reported and must still be able to fire.

Full frontend suite green (204 suites, 2253 tests); `tsc --noEmit` clean; ESLint 0 errors.

**Cold agent (scoped, falsification, `sonnet`) — primary claim CONFIRMED, and confirmed the expensive
way.** It did not merely read the wiring: it forced `useCourseProgramCatalogNames` to return `null`
unconditionally — the exact `v0.116.0`/`v0.117.0` no-op class — and **all four tests then failed**,
then verified its revert restored the file. It also independently confirmed the catalog is reachable
mid-onboarding (no `OnboardingGuardService.assertProfileComplete` anywhere on the controller or
service path; `SecurityConfig`/`JwtAuthenticationFilter` contain no onboarding gate), that the
analytics call cannot fire on a failed save, that `allowCustom` is untouched, and that no forbidden
item was tripped. **It found no runtime defect in the change.**

**⚠️ IT FOUND TWO DOC-DRIFT DEFECTS, AND BOTH WERE FIXED RATHER THAN NOTED — this is exactly the
"sweep by SURFACE, not by diff" class this repo has now been bitten by in four consecutive releases:**
- **`docs/features/notes.md` contradicted itself inside this very diff.** The surface list two lines
  above was updated to include onboarding while the line below still read *"Onboarding deliberately
  continues using the hardcoded `COURSE_PROGRAM_SUGGESTIONS` list until after the 2026-09-11
  completion checkpoint."* Removed, with the correction recorded in place. The neighbouring free-text
  line was also stale and now names onboarding.
- **⚠️ `ROADMAP.md`'s Backlog Index row for this very item still read "NOT SHIPPED — deliberately
  deferred", and cited "a source-text test asserts this" — the test THIS RELEASE DELETED.** That row
  is what kickoff scan steps 8 and 9 read, so leaving it would have handed the next kickoff false
  state about the work just completed. Row closed, and its `[EVIDENCE]` gate marked discharged. The
  adjacent `v0.79.0` checkpoint row was updated separately: its distal 2026-10-15 read was explicitly
  gated on "the post-2026-09-11 onboarding follow-up", which is what shipped here, so that read must
  now include `surface: "onboarding"` — **the fifth fire site, and the one the 13.9% profile baseline
  actually turns on**, since the original four measure edits of existing values.

**⚠️ A THIRD STALE CLAIM WAS FOUND BY WIDENING THE SWEEP, AND THE SCOPING IS THE LESSON.** The first
sweep grepped `docs/features/` and `docs/architecture/` — **`docs/product/` was never in it**, which
is precisely why the agent found the `ROADMAP.md` row and the implementing session did not. Two
whole-tree greps closed it: `2026-09-11` and `COURSE_PROGRAM_SUGGESTIONS`, both excluding
`docs/archive/`. They surfaced `docs/claude-plans/learning-connections-phase-plan.md:486`, which told
a future session that `[CHECKPOINT — due 2026-09-11]` is a **live measurement window** and that
editing the onboarding flow would **destroy** it. That is now false in both halves. A dated
correction was added above the paragraph. **⚠️ It is scoped strictly to the 2026-09-11 onboarding
read and explicitly reaffirms that `[CHECKPOINT — due 2026-09-19]` — that arc's own `ACCEPTED`-keyed
kill criterion — remains live and untouched. NO Learning Connections code was modified; the only
code files in this release are `app/onboarding/page.tsx` and `hooks/use-course-program-catalog.ts`.**
`docs/product/SPEC.md` was checked and is clean. `docs/gpt-contexts/GPT_CONTEXT.md` is version-stamped
to `v0.127.0` and is left to its normal per-release refresh. Historical files — `docs/releases/*.md`
and `docs/archive/` — were deliberately not rewritten; they are the record of what was true then.
