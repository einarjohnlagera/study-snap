# Program Family Catalog Management UX Overhaul — Stage 1 Plan

**Status:** PLANNING ONLY — nothing implemented, nothing committed, no production write executed.
**Authored:** 2026-09-16, against repo state at `c90d3bf7` (branch `releases/v0.151.0`, v0.151.0 Released).
**Target release:** v0.152.0 (must be kicked off before any code lands — see §P).
**Production reads:** SELECT-only via the Render MCP server, instance `dpg-d6tvb8fkijhs73fda4m0-a`.

> **Verification posture.** Every claim below is anchored to a `file:line`, a migration file, or a
> production `SELECT` run during this session. Where this plan contradicts
> `docs/claude-plans/program-family-many-to-many-final-plan.md`, this plan wins: that document
> describes what was *proposed*, and several of its statements have been overtaken by the post-deploy
> owner data operation. Nothing here was taken on trust from it.

> **Release objective — single capability, owner-locked (§11):** *Manage the canonical Course / Program
> catalog efficiently from either side of its many-to-many Program Family relationship.* Every slice in
> §R exists to serve that one sentence. This release does not add a taxonomy platform, a governance
> rewrite, or a generation-context change — it gives an architecture that shipped correctly in v0.150.0
> the curator UX it should have had from the start. **Production data is the evidence for why:**
> Engineering (18/18) and Education (8/8) were fully populated at v0.150.0 launch; three weeks and one
> release later, Health Sciences and Computing & Technology still sit at zero, Accounting at 2/5, Built
> Environment & Design at 1/8 (§C). The many-to-many model did not fail — the program-by-program
> workflow made finishing the catalog through it tedious enough that it didn't get finished. This is not
> a redesign of the data model; it is the missing half of v0.150.0.

---

## A. Executive verdict

**APPROVE**, with three modifications and one correction to a premise in the brief.

The family-first management perspective is approved because it is **purely a view change over an
already-correct data model**. `course_program_family` (V146) is a symmetric join table with a
composite unique key and cascading FKs on both sides; it has no notion of a parent, an owner, or a
primary family. A Family → Programs editor is therefore not a hierarchy — it is the *other* projection
of a relation that already supports both directions equally. Nothing in ADR-001 is weakened, because
ADR-001's amended clause 2 is deliberately storage-neutral and says only that *membership is the sole
input to expansion*. Editing membership from the family side changes which rows exist, not what
expansion means.

The brief's stated motivation is also borne out by production data. Four of the six families were
populated by hand after the v0.150.0 deploy, and the current UI makes that work quadratic in the wrong
variable: to fill **Health Sciences** (5 members) the owner must open **5 separate program rows**, and
to fill **Built Environment & Design** (7 more members) another **7** — 21 separate modal round-trips
for the remaining backfill, each through a raw `<select multiple>`. Family-first collapses that to
**4 modals**. The audit independently confirms the cost was real: Computing & Technology and Health
Sciences still sit at **zero members**, three weeks and one release after the family rows were created.

**Modification 1 — the UX reference does not exist as described; say so rather than fake it.**
The brief's "Note Collection Builder searchable multi-select with checkboxes and chips" is a blend of
two unrelated surfaces. The real component is `AddNotesModal`
(`frontend/app/collections/[id]/builder/study-plan-builder-page-client.tsx:1083-1302`) — a **modal**,
not a popover, with search + native checkbox rows + a separate "Selected (N)" list, and **no chips**.
The chips come from `ApplicableProgramsCombobox`. There is **no Popover, no Command/cmdk, no
MultiSelect, and no Radix/shadcn/headlessui anywhere in the repo** — `frontend/components/ui/` is
entirely hand-rolled and its only combobox (`suggestion-combobox.tsx`) is single-select by signature.
So §6's option (A) "reuse directly" and (B) "a lower-level primitive already exists" are both **false**.
The honest answer is (D) with the interaction language of `AddNotesModal` preserved — detailed in §G.

**Modification 2 — the migration cannot `RAISE EXCEPTION` on missing canonical rows.** §17 asks for
loud failure and also forbids creating programs. Both cannot hold, because ~35 of the 62 production
programs and 4 of the 6 families exist **only as production runtime data** — no migration seeds them.
A hard failure would break every fresh-database Flyway run: the PostgreSQL 18 container in
`NativeQueryPostgresIntegrationTest` (which applies the real migration set on every `./mvnw test`) and
local `./mvnw spring-boot:run`. The loudness moves into a guard test that supplies the canonical rows
as a fixture. Full reasoning and the rejected alternative in §J.

**Modification 3 — retain program-side Edit, but demote it.** §8 asks whether it becomes redundant.
It does not: the program-side view answers "what is *this* program in?", which a family-side editor
cannot answer without opening every family. It stays, at near-zero cost, reusing the same shared
control. See §F.

**No REJECT items.** Nothing in the brief asks for something the model forbids.

---

## B. Current architecture

### B.1 Database

| Object | Migration | Shape |
|---|---|---|
| `program_families` | V106 (seed: Engineering), V142 (seed: Education) | `id uuid pk`, `name varchar NOT NULL`, `created_at`. `uk_program_families_name UNIQUE (name)` — **raw name, case- and whitespace-sensitive**. |
| `course_programs` | V106 | `id uuid pk`, `name varchar NOT NULL` (`uk_course_programs_name UNIQUE (name)`), `program_family_id uuid NULL` (**legacy, vestigial — see B.6**), `exam_goal_slug varchar` (CHECK in `ale/pnle/let/cpale`), `created_at`, `is_active boolean NOT NULL DEFAULT true` (V145). |
| `course_program_family` | **V146** | `id uuid pk`, `course_program_id uuid NOT NULL`, `program_family_id uuid NOT NULL`, `created_at`. `uk_course_program_family_program_family UNIQUE (course_program_id, program_family_id)`; FKs to both parents `ON DELETE CASCADE`; B-tree index on **each** FK column (`idx_course_program_family_program_id`, `idx_course_program_family_family_id`). |

The index on `program_family_id` is what makes the family-first view cheap — it already exists, so the
new read direction costs no schema change.

### B.2 Repository — `backend/src/main/java/com/studysnap/backend/repository/CourseProgramCatalogRepository.java`

Plain `JdbcTemplate`, no JPA entity for either catalog table. Relevant statements:

- `CATALOG_SELECT` (`:25-32`) — `course_programs LEFT JOIN course_program_family LEFT JOIN program_families`, fanned out one row per membership and re-grouped in `queryCatalog` (`:111-124`) into a `CourseProgramCatalogItemResponse` carrying a `List<ProgramFamilyResponse> programFamilies`.
- `FIND_ALL_PROGRAM_FAMILIES` (`:58`) — `SELECT id, name FROM program_families ORDER BY name`. **No member count.**
- `FIND_PROGRAM_FAMILY_BY_NORMALIZED_NAME` (`:59`) — `WHERE lower(trim(name)) = ?`. ⚠️ **Weaker than the program equivalent** (`:36-38`), which uses `lower(regexp_replace(trim(name), '\s+', ' ', 'g'))`. See §I.
- `INSERT_MEMBERSHIP` / `DELETE_MEMBERSHIPS` (`:54-55`) — `DELETE ... WHERE course_program_id = ?` then re-insert. **Program-scoped only**; there is no family-scoped replace.
- `replaceProgramFamilies(programId, familyIds)` (`:87-90`) — delete-then-insert, program side.

### B.3 Service — `CourseProgramCatalogService.java`

| Method | Line | Transactional | Notes |
|---|---|---|---|
| `list()` | `:38` | readOnly | Unfiltered — active **and** inactive, by design. |
| `listProgramFamilies()` | `:42` | readOnly | id + name only. |
| `createProgramFamily(request)` | `:57` | `@Transactional` | Normalizes, length-caps at 120, pre-checks the normalized name, catches the `uk_program_families_name` race and rethrows as a conflict. **Creates the family EMPTY** — the Javadoc at `:47-54` explicitly forbids member selection here. That Javadoc is what §7 changes. |
| `create(request)` | `:106` | `@Transactional` | Insert program **then** `insertProgramFamilies(id, familyIds)` in the same transaction. **Program-with-many-families creation is already atomic.** |
| `updateProgramFamilies(id, request)` | `:88` | `@Transactional` | Program-side full-set replace. Empty list clears membership. |
| `validateAndDeduplicateFamilyIds` | `:133` | — | `LinkedHashSet` dedupe; each id must resolve or `UnknownProgramFamilyException`. Reusable verbatim on the family side with the operands swapped. |

**There is no rename method and no family-side membership method.** Those are the only two genuinely
new backend behaviours this overhaul needs.

### B.4 API — `CourseProgramCatalogController.java`, base `/course-program-catalog`

| Method | Path | Guard |
|---|---|---|
| GET | `/course-program-catalog` | `hasAnyRole('USER','ADMIN')` |
| GET | `/similar?name=` | `hasRole('ADMIN')` |
| POST | `/course-program-catalog` | `hasRole('ADMIN')` |
| PATCH | `/{id}` | `hasRole('ADMIN')` |
| GET | `/families` | `hasRole('ADMIN')` |
| POST | `/families` | `hasRole('ADMIN')` |

### B.5 Frontend

```
frontend/app/admin/course-programs/page.tsx          ← the Admin catalog destination (linked from /admin)
  ├── AdminCourseProgramCatalogSection                 components/admin/admin-course-program-catalog-section.tsx
  │     ├── permanently-visible create form (name | single-family <select> | exam goal)
  │     ├── permanently-visible "New Program Family" inline box  (:207-231)  ← §7 removes
  │     ├── program table (desktop) / card list (mobile) with family chips  (:170-185)
  │     └── Edit modal → raw <select multiple> + chip row                    (:243-260)  ← §6 replaces
  └── AdminApplicableProgramsSection                   components/admin/admin-applicable-programs-section.tsx

frontend/lib/api.ts
  parseCourseProgramCatalogItem  :5865   getCourseProgramCatalog   listProgramFamilies :5908
  createProgramFamily :5919      createCourseProgram :5931        updateCourseProgram :5945
  findSimilarCoursePrograms :5963
```

**`ApplicableProgramsCombobox`** (`frontend/components/metadata/applicable-programs-combobox.tsx`, 427
lines) is the single shared Note-authoring control, with exactly four consumers:

| Consumer | Line | `canCreateCatalogProgram` |
|---|---|---|
| `components/admin/admin-applicable-programs-section.tsx` | `:206` | hardcoded `true` (admin-only surface) |
| `components/notes/bulk-generation-page-client.tsx` (**Bulk Note Create**) | `:594` | `{isAdmin}` |
| `components/notes/private-note-detail-page-client.tsx` (Note Detail inline edit) | `:2773` | `{userRole === "ADMIN"}` |
| `components/notes/note-editor-form.tsx` (**Single Note Create/Edit**) ← `note-editor-page-client.tsx:1421` | `:444` | `{currentUserRole === "ADMIN"}` |

Inside it: family shortcuts (`:296-326`, deriving families from each program's `programFamilies`
array, with the ADR-001 anti-drift comment at `:217-221`), selected chips (`:328-352`), and the inline
**"Add … to the catalog"** modal (`:365-424`) which already supports **multiple** families via its own
raw `<select multiple>` (`:382-391`).

### B.6 ⚠️ Two facts an implementer will get wrong without being told

1. **`course_programs.program_family_id` still exists and is already stale.** V142 *wrote* it, so
   anyone pattern-matching on V142 will write it again. V146 read it once to seed the join table and
   nothing has written it since. Production confirms the drift: 26 rows carry a legacy value, while
   Accountancy, Accounting Information Systems and Architecture — all assigned post-deploy through the
   join table — carry `NULL`. **No Java code reads the column** (verified: the repository's
   `program_family_id` references are all to `course_program_family.program_family_id` or the
   `program_families.id AS program_family_id` alias). Dropping it is catalog-lifecycle work, deferred
   per §23. The new migration **must not write it**.

2. **`CourseProgramCatalogItemResponse.programFamilyId` / `.programFamilyName` are *derived*, not the
   column.** They are the *first* element of the `programFamilies` list
   (`CourseProgramCatalogItemResponse.java:18-22`), kept as a rollout-window compatibility shim and
   mirrored in `api.ts:5888-5899`. Do not treat them as a "primary family" — no primary family exists.

---

## C. Production family membership audit

Read-only, run 2026-09-16 against `dpg-d6tvb8fkijhs73fda4m0-a`. **62 course programs, 6 families, 29
membership rows.** No program currently belongs to more than one family — the many-to-many capability
shipped in v0.150.0 has not yet been exercised in production data.

Durable family IDs (name-resolution targets for §J; note that only two are in the seeded ID range):

| Family | `id` | Origin |
|---|---|---|
| Engineering | `10000000-0000-0000-0000-000000000001` | V106 seed |
| Education | `10000000-0000-0000-0000-000000000002` | V142 seed |
| Accounting | `c55a73a3-ba70-4187-95aa-8fa36d6f5d9f` | runtime (POST /families) |
| Health Sciences | `ee64905a-878a-46d4-ab67-71e1989a7b3b` | runtime |
| Computing & Technology | `aca40dcb-cc6e-4726-b7f4-43916b273d37` | runtime |
| Built Environment & Design | `00f6c913-ad5c-4b4d-beee-7914c7579d7e` | runtime |

### ENGINEERING — current 18 / intended 18

```
Current (18):
- Aeronautical Engineering          - Geodetic Engineering
- Agricultural and Biosystems Eng.  - Geological Engineering
- Architectural Engineering         - Geotechnical Engineer
- Chemical Engineering              - Industrial Engineering
- Civil Engineering                 - Marine Engineering
- Computer Engineering              - Mechanical Engineering
- Construction Eng. and Management  - Mining Engineering
- Electrical Engineering            - Sanitary Engineering
- Electronics Engineering
- Environmental Engineering

Missing: none
Extra:   none
```

Production matches the approved matrix **exactly**, including the deliberately odd singular
`Geotechnical Engineer`. §15's instruction not to assume its count before comparing is discharged: 18
intended, 18 present.

### EDUCATION — current 8 / intended 8

```
Current (8):
- Early Childhood Education         - Physical Education
- Education                         - Secondary Education
- Elementary Education              - Special Needs Education
- Teacher Certification             - Technical-Vocational Teacher Education

Missing: none
Extra:   none
```

### HEALTH SCIENCES — current 0 / intended 5

```
Current: (none)

Missing (5):
- Nursing
- Medicine
- Pharmacy
- Physical Therapy
- Radiologic Technology

Extra: none
```
All five exist in the catalog as distinct rows. `Nursing · Medicine` and `Nursing · Pharmacy` exist
separately and are **not** assigned (§16, correct).

### ACCOUNTING — current 2 / intended 5

```
Current (2):
- Accountancy
- Accounting Information Systems

Missing (3):
- Management Accounting
- Internal Auditing
- Certified Management Accountant

Extra: none
```

### COMPUTING & TECHNOLOGY — current 0 / intended 6

```
Current: (none)

Missing (6):
- Information Technology
- Computer Science
- Software Engineering
- Computer Engineering          ← becomes a 2nd family (also Engineering)
- Electronics Engineering       ← becomes a 2nd family (also Engineering)
- Accounting Information Systems ← becomes a 2nd family (also Accounting)

Extra: none
```

### BUILT ENVIRONMENT & DESIGN — current 1 / intended 8

```
Current (1):
- Architecture

Missing (7):
- Interior Design
- Landscape Architecture
- Urban and Regional Planning
- Environmental Planning
- Architectural Engineering              ← becomes a 2nd family (also Engineering)
- Geodetic Engineering                   ← becomes a 2nd family (also Engineering)
- Construction Engineering and Management ← becomes a 2nd family (also Engineering)

Extra: none
```

### C.1 Reconciliation

- **Extra memberships across all six families: ZERO.** §18's "classify whether it looks intentional"
  and §19's "extra" line have nothing to report. Every existing row is in the approved matrix. The
  additive-only requirement still binds the migration's *design* (it must never delete), but there is
  no live curator work at risk today.
- **Rows the migration will insert: 21** (0 + 0 + 5 + 3 + 6 + 7).
- **Membership rows after backfill: 50** (29 + 21).
- **Programs with ≥1 family after backfill: 44.** Six of those carry two families: Architectural
  Engineering, Geodetic Engineering, Construction Engineering and Management (Eng + BE&D); Computer
  Engineering, Electronics Engineering (Eng + C&T); Accounting Information Systems (Acc + C&T). This
  is the first real exercise of the many-to-many relation.
- **Programs left with zero families: 18**, and they reconcile exactly to §16's list — the 16 named
  (Senior High – ABM/STEM/HUMSS, Criminology, Law, Aviation, Business Administration, Psychology,
  Chemistry, Biology, Entrepreneurship, Real Estate Management, Economics, Chartered Financial
  Analyst, Public Administration, Custom Administration) plus the two legacy fused rows
  (`Nursing · Medicine`, `Nursing · Pharmacy`). 62 = 44 + 18. ✅
- **Every one of the 50 approved `(family, program)` pairs resolves to an existing catalog row by exact
  name.** No fuzzy matching is required, and none is proposed. **This is not prose — it was re-verified
  with a second, independent read-only query** (2026-09-16, same session) that `LEFT JOIN`s each of the
  44 distinct matrix program names and 6 matrix family names against `course_programs`/`program_families`
  and compares `length(matrix_name) = length(db_name)` alongside the string equality already used by the
  join predicate. **All 50 matched, byte-length included** — critically, `Technical-Vocational Teacher
  Education` (plain hyphen, per §I.2's normalization note) and the deliberately singular `Geotechnical
  Engineer` both matched exactly. This closes the one failure mode `ON CONFLICT DO NOTHING` + no `RAISE`
  cannot self-detect: a matrix literal that differs from production by one byte (a hyphen/en-dash
  mismatch, a double space) would join to nothing and silently insert zero rows for that pair, with
  nothing in the migration or a fixture-derived test able to notice. It is closed for the 50 pairs
  audited *this session* — a name changed between now and deploy is exactly what §J.4's post-deploy
  acceptance check (below) exists to catch.
- `is_active = true` on all 62 rows. Nothing is retired, so no approved member is an inactive program.

---

## D. Final Admin information architecture

**Keep the single destination `/admin/course-programs`. Add a two-tab switch inside it. Do not add a
sidebar entry.** §3's preference is honoured, and the repo's admin convention supports it: every admin
area is a flat sub-page under `/admin` reached from the dashboard, and a second top-level destination
for the *other half of one relation* would misrepresent them as separate systems.

```
← Admin
Course / Program Catalog                                  (h1, existing PageHeader-ish block)
Manage the shared catalog: the programs notes can be curated against, and the families that group them.

[ Program Families ]  [ Course / Programs ]               ← tab switch, Program Families ACTIVE by default
```

- **Default view: Program Families**, per §4. **Owner-locked information-architecture invariant:**
  Family-side editing is the **primary** workflow for bulk membership management; program-side editing
  is the **inverse convenience** workflow for answering "which families is this one program in?". The
  two tabs must not carry competing visual prominence — Program Families stays first, stays the load-on-
  open default, and its `+ New family` / count-and-Edit table are the page's primary affordances; the
  Course / Programs tab is reached deliberately, not presented as an equal alternative starting point.
- **Pattern to copy:** `frontend/components/notes/note-detail-tabs.tsx` is the repo's existing
  `role="tablist"` implementation (the only other is `app/explore/explore-page-client.tsx`). Reuse its
  ARIA shape — `role="tablist"` / `role="tab"` / `aria-selected` / `aria-controls` — rather than
  inventing a segmented control. Roving-tabindex arrow-key support should be added if
  `note-detail-tabs.tsx` lacks it; two tabs make that cheap.
- **URL state:** reflect the active tab in a query param (`?view=families|programs`) so a refresh, a
  back button, and a deep link from a toast all land on the tab the curator was in. This is the
  release's one "load on refresh" behaviour and it is the thing `/audit-diff` most often finds missing.
- **`AdminApplicableProgramsSection` is untouched** and remains below the tab block. It answers a
  different question (which programs a *note* is discoverable under) and must not be folded into
  either tab.
- **Responsive:** the tab strip is two items; it fits at 320px with no scroll. Do not collapse it into
  a `<select>`.

---

## E. Program Families UX

### E.1 Overview (default tab)

```
Program Families                                          [ + New family ]

Group related programs for faster selection when curating notes.
A program can belong to more than one family.

┌──────────────────────────────┬───────────┬──────────┐
│ Program Family               │ Programs  │          │
├──────────────────────────────┼───────────┼──────────┤
│ Accounting                   │ 5         │ [ Edit ] │
│ Built Environment & Design   │ 8         │ [ Edit ] │
│ Computing & Technology       │ 6         │ [ Edit ] │
│ Education                    │ 8         │ [ Edit ] │
│ Engineering                  │ 18        │ [ Edit ] │
│ Health Sciences              │ 5         │ [ Edit ] │
└──────────────────────────────┴───────────┴──────────┘
```

- **Desktop: a table.** Six rows today, plausibly fifteen in a year. Cards would waste the width and
  break the name/count alignment that makes the page scannable. This matches the existing catalog
  table at `admin-course-program-catalog-section.tsx:172-177`.
- **Mobile (`sm:hidden`): the existing card-list pattern** from `:179-189` — name as `<h3>`, count as
  muted text, full-width `Edit` button. **Not an expandable row**: §4 explicitly says not to expose
  every member permanently, and an accordion on a page whose only action is "Edit" adds a tap without
  adding information.
- **Member names are NOT rendered in the overview.** Engineering alone would add 18 chips.
- **Sort:** alphabetical by name, matching `FIND_ALL_PROGRAM_FAMILIES`'s `ORDER BY name`.
- **Empty family renders `0` and remains editable** — that is the whole reason `GET /families` exists
  (see the controller's own comment at `CourseProgramCatalogController.java:63-65`).

**Counts require no backend change.** The page already loads both `getCourseProgramCatalog()` and
`listProgramFamilies()` in one `Promise.allSettled` (`admin-course-program-catalog-section.tsx:47-51`).
The families call supplies the universe (including zero-member families); the catalog supplies each
program's `programFamilies` array, from which counts are tallied client-side.
⚠️ **Do not apply the `is_active` filter to this tally.** `docs/features/program-families.md` states
that filter belongs exclusively to `applicable-programs-combobox.tsx`; the admin table is the full
lifecycle view. An inactive member still *is* a member.

*(Adding `memberCount` to `ProgramFamilyResponse` is the alternative. It is rejected for this release:
it is a response-shape change to an endpoint two other surfaces parse, for a number the page already
has the data to compute. Revisit only if the families endpoint ever loses its catalog companion.)*

### E.2 `+ New family`

Removes the permanently-visible inline box at `admin-course-program-catalog-section.tsx:207-231`
(§7). Opens `AppModal`:

```
New Program Family

Name *
[ Business & Finance                                    ]

Course / Programs (optional)
[ 🔍 Search or select programs…                         ]   ← shared multi-select, selectedSummary="count" (§G)
Selected 0                                        Clear all
  (empty-selection hint — "A family with no programs is valid. You can add them later.")

                                    [ Cancel ]  [ Create family ]
```

- **Atomic create-with-members: YES.** `create()` (`CourseProgramCatalogService.java:106-131`) already
  does exactly this shape for a program — insert the row, then insert memberships, in one
  `@Transactional` method. The family side is the mirror image over the same join table, on the same
  `JdbcTemplate`, in the same service. This is one extra repository call inside an existing
  transaction, not new infrastructure. §7's "if atomic creation requires unnecessary backend
  complexity" escape hatch is **not** taken.
- **Zero members remains valid** — `programIds` is optional and defaults to an empty list, exactly as
  `UpdateCourseProgramCatalogRequest`'s compact constructor already does (`:8`).
- **⚠️ The service Javadoc at `CourseProgramCatalogService.java:47-54` must be rewritten, not just
  worked around.** It currently says, in bold, "A FAMILY IS CREATED EMPTY AND THAT IS CORRECT. …Do not
  add member selection here." That instruction was correct for v0.133.0's minimum UI and is
  *superseded* by this release. Leaving it in place would make the next session treat the new
  behaviour as a defect — the same "stale comment asserts the gate was deliberate" failure
  `v0.151.0` hit in `NoteRegenerationConsequenceService`. Replace it with a note explaining that
  membership is settable at create time because it is a property of the relation, not of either side.

### E.3 `Edit` family

```
Edit Program Family

Name
[ Health Sciences                                       ]

Course / Programs
[ 🔍 Search or select programs…                         ]   ← selectedSummary="count" (§G)
Selected 5                                        Clear all
  (the checked rows above — Nursing, Medicine, Pharmacy, Physical Therapy, Radiologic Technology —
   remain the primary editing surface; no chip row is rendered for this direction)

                                    [ Cancel ]  [ Save changes ]
```

- One modal, **one save**, covering both the name and the membership set. Two buttons would invite the
  curator to rename and lose the membership edit (or vice versa).
- **Save semantics: full-set replace of *this family's* membership**, identical in shape to the
  program-side `PATCH`. An empty selection clears the family's membership and is valid.
- **⚠️ The invariant that must not break:** a family-side replace deletes by `program_family_id`, NOT
  by `course_program_id`. Mirroring `replaceProgramFamilies` by copy-paste and swapping only the
  insert would delete every membership of each selected *program*, silently evicting Computer
  Engineering from Engineering the moment it is added to Computing & Technology. This is the single
  highest-value test in §N.
- **Rename is identity-preserving** — contract in §I.
- **No Delete family button** (§5, §27).

---

## F. Course / Programs UX

### F.1 Overview (second tab)

Essentially the existing table, moved under the tab and given a header action:

```
Course / Programs                                        [ + New program ]

Manage the programs available when curating notes.

┌──────────────────────────────┬────────────────────────────────────┬──────────┐
│ Course / Program             │ Program Families                   │          │
├──────────────────────────────┼────────────────────────────────────┼──────────┤
│ Accountancy                  │ (Accounting)                       │ [ Edit ] │
│ Accounting Information Sys.  │ (Accounting) (Computing & Tech.)    │ [ Edit ] │
│ Architecture                 │ (Built Environment & Design)        │ [ Edit ] │
│ Computer Engineering         │ (Engineering) (Computing & Tech.)    │ [ Edit ] │
│ Law                          │ —                                   │ [ Edit ] │
└──────────────────────────────┴────────────────────────────────────┴──────────┘
```

Family chips already render via `familyChips` (`admin-course-program-catalog-section.tsx:170-181`),
including the `—` empty case. Keep it verbatim.

### F.2 `+ New program`

Replaces the permanently-visible three-field create grid (`:174-205`) with a header button opening a
modal:

```
New Course / Program

Name *
[ Hospitality Management                                ]
  ⚠ Similar catalog programs already exist: Hospitality Management Technology

Program Families (optional)
[ 🔍 Search or select families…                         ]        ← shared multi-select (§G)
Selected: [ Accounting ×]

Exam goal (optional)
[ No exam goal                                     ▾ ]

                                    [ Cancel ]  [ Create program ]
```

- **Already atomic on the backend** — `create()` takes `programFamilyIds` and inserts memberships in
  the same transaction (`CourseProgramCatalogService.java:106-131`). **No backend change needed for
  §9.** The Admin form is simply *behind* the note-authoring modal today: it sends a single
  `programFamilyId` (`admin-course-program-catalog-section.tsx:125`) through the DTO's legacy
  convenience constructor, while `applicable-programs-combobox.tsx:187` already sends
  `programFamilyIds`. Fixing this is a frontend-only change.
- **Keep the debounced near-match check** (`:83-108`) — it is the catalog's only defence against
  `Nursng` / `BS Nursing` and §21 depends on it.
- **Exam goal is preserved unchanged** (§22): same four-slug `<select>`, same optional semantics, same
  `ALLOWED_EXAM_GOAL_SLUGS` validation. Do **not** add exam-goal editing anywhere.

### F.3 Does program-side Edit survive? — **YES, retained.**

**The tradeoff, stated.**

*For retiring it:* it is a second way to write the same relation, and two write paths to one table is
where drift starts. Family-first can express every membership set.

*For keeping it (decisive):* the two views answer different questions, and only one of them is cheap
in each direction. "Which families is *Accounting Information Systems* in?" costs one modal from the
program side and six modal-opens from the family side. It is also the natural follow-up to
`+ New program` — the curator has just created `Hospitality Management` and wants it in a family
*now*, not after navigating to the other tab. And the incremental cost is near zero: the modal already
exists, the endpoint already exists, and it will consume the same §G control, so the only change is
swapping the `<select multiple>` for the shared component.

*Why it does not cause drift:* both directions write the same join table through the same service,
with the same `validateAndDeduplicateFamilyIds` validation. They are not two semantics; they are two
argument orders. The **real** duplication risk in this codebase is the two *creation* forms (§H),
and that one is being collapsed.

**Owner-locked framing:** this is the **inverse convenience** workflow, not a peer of family-side
editing. It gets no header-level `+` action of its own beyond `+ New program`, no promotion to default
tab, and no duplicate "manage membership" call to action — it is reached via `Edit` on a program row,
same as it is today, and its only UI change in this release is swapping the raw `<select multiple>`
for the shared `CatalogMultiSelect` (§G) with `selectedSummary="chips"`.

---

## G. Shared searchable multi-select design

### G.1 The reference, named exactly

**`AddNotesModal`** — `frontend/app/collections/[id]/builder/study-plan-builder-page-client.tsx`,
**lines 1083–1302**, rendered at `:2695` and `:2968`, with helpers `filterPickerNotes` (`:207-219`),
`PICKER_NOTE_LIMIT = 50` and `PICKER_SEARCH_DEBOUNCE_MS = 300` (`:204-205`).

**What it actually is, versus the brief's description of it:**

| Brief says | Reality |
|---|---|
| dropdown / popover | **`AppModal` dialog** (`role="dialog"`, `aria-modal="true"`) |
| searchable | ✅ debounced 300 ms, but **server-side** with a 50-row cap |
| checkbox multi-select | ✅ **native `<input type="checkbox">`** — notably *not* the repo's own `ui/checkbox.tsx` |
| selected state visible | ✅ but as a **second checkbox list** under a divider ("Selected (N)" + "Clear all") |
| chips | ❌ **none.** Chips are `ApplicableProgramsCombobox`'s pattern (`applicable-programs-combobox.tsx:328-352`) |

So the interaction the owner likes is real, but it is **split across two components**, and it is what
should be composed: *`AddNotesModal`'s search-and-checkbox body, `ApplicableProgramsCombobox`'s
removable chips as the selected summary.*

### G.2 Audit of §6's four options

| Option | Verdict | Evidence |
|---|---|---|
| **A — reuse `AddNotesModal` directly** | ❌ Impossible | It is a **module-local, non-exported** function inside a 2991-line page client, with no named interface, hardcoded to `NoteListItemResponse` in its `notes` prop, its `onAdd` signature, its internal `Map<string, NoteListItemResponse>` (`:1118`) and its row renderer (`:1240-1243`). Its `subject: { collectionId, items, title }` prop bakes in the collection domain. Its search state is **lifted to the parent** because the fetch is server-side. |
| **B — a lower-level primitive already exists** | ❌ False | `frontend/components/ui/` has **no** `popover.tsx`, `command.tsx`, `multi-select.tsx` or `combobox.tsx`. `package.json` has **no** `@radix-ui`, shadcn, `@headlessui`, `cmdk`, `downshift` or `react-select`. The only combobox, `ui/suggestion-combobox.tsx` (329 lines), is **single-select by signature** (`value: string; onChange: (value: string) => void`). |
| **C — small extraction creates a reusable control** | ⚠️ Tempting, unsafe as stated | A generic extraction of `AddNotesModal` must first undo a load-bearing hack: **every result checkbox is hardcoded `checked={false}` (`:1235`)**, correct only because `resultNotes` (`:1138-1141`) filters selected ids out of the list. Selection is list membership, not `checked` state. Extracting that faithfully propagates a bug; extracting it *correctly* means rewriting the selection model — which is (D) with extra steps and a 2991-line file in the blast radius. |
| **D — separate implementation** | ✅ **RECOMMENDED** | Build a new shared control that reuses the **interaction language**, not the code. |

### G.3 Recommendation — **NEW COMPONENT** (interaction language ADAPTED from `AddNotesModal`)

`frontend/components/ui/catalog-multi-select.tsx` — generic over `{ id: string; name: string }`,
client-side filtered (both catalogs are small: 62 programs, 6 families, already fully in memory — no
server search, no 50-row cap, no debounce needed).

**⚠️ Owner-tightened: selection-summary density must vary by consumer, without forking the selection
logic.** The checked rows in the item list remain the single primary editing representation in every
consumer — the summary block below them is presentation only and never the thing being edited. Two
consumers need it dense (Family → Programs can hold 5–18+ members; rendering 18 removable chips
*and* 18 checked rows is redundant noise), two need it informative (Program → Families is normally
0–2 items, where a removable chip per item is cheap and useful). One shared component, one prop:

```ts
type CatalogMultiSelectItem = { id: string; name: string };

type CatalogMultiSelectProps = {
  id: string;
  label: string;                       // e.g. "Course / Programs"
  items: readonly CatalogMultiSelectItem[];
  selectedIds: readonly string[];
  onChange: (selectedIds: string[]) => void;
  selectedSummary?: "count" | "chips"; // default "chips" — see consumer table below
  disabled?: boolean;
  searchPlaceholder?: string;          // "Search or select programs…"
  emptyHint?: string;                  // shown when nothing is selected
};
```

`selectedSummary="count"` renders `Selected {n}` + a `Clear all` button in place of the chip row — no
per-item chip is rendered, and the checked list above remains scrollable and is where removal actually
happens (uncheck a row, or `Clear all`). `selectedSummary="chips"` keeps the existing removable-chip
treatment from `ApplicableProgramsCombobox` (`:328-352`), one chip per selection with its own
`aria-label={`Remove ${name}`}`. **This is deliberately not over-generalized** — no third mode, no
per-item configurability — because the four real consumers split cleanly into exactly two groups:

| Consumer | Selects | Typical count | `selectedSummary` |
|---|---|---|---|
| Edit Program Family (§E.3) — programs | Course / Programs | 5–18 | `"count"` |
| New Program Family (§E.2) — initial programs | Course / Programs | 0–18 | `"count"` |
| New Course / Program (§F.2) — families | Program Families | 0–2 | `"chips"` |
| Program-side Edit (§F.3) — families | Program Families | 0–2 | `"chips"` |

Deliberately **controlled, `selectedIds`-shaped, and API-free** — it imports nothing from `@/lib/api`,
which is precisely what makes it usable from four different modals. (`ApplicableProgramsCombobox`'s
`selectedIds`/`onChange(string[])` contract is the one genuinely reusable idea in the existing code;
this borrows it.)

**Interaction states:**

| State | Rendering |
|---|---|
| Idle, nothing selected | Search input; full item list below, each row a `<label>` wrapping a native checkbox (no `role="option"` — see §M.2's `role="group"` choice, not a listbox); muted `emptyHint`. |
| Typing | Case-insensitive, whitespace-collapsed substring filter over `name`. **Items stay in the list whether or not they are selected** — this is the deliberate departure from both existing components' remove-on-select trick, and it is what the checkbox affordance promises. **⚠️ Owner-tightening follow-up (found by `advisor()`, not the original brief): a selected item is ALWAYS rendered, even when it does not match the current search text** — the filter predicate is `matchesSearch(item) \|\| selectedIds.includes(item.id)`, never plain substring match alone. Without this, `selectedSummary="count"` has no way to reach an individual filtered-out selection except `Clear all`, which discards the whole set to remove one item. This applies in both `selectedSummary` modes; it costs one `||` in the filter predicate, not a new UI affordance, so it does not trip §27's overbuild guard. |
| Item selected | Its checkbox becomes **genuinely `checked`**; the Selected block updates (a new chip if `"chips"`, the count if `"count"`). |
| Selected block (`selectedSummary="chips"`) | `Selected (N)` count + removable chips styled exactly as `applicable-programs-combobox.tsx:330-344`, container `aria-label="Selected …"`, each chip carrying `aria-label={`Remove ${name}`}`. |
| Selected block (`selectedSummary="count"`) | `Selected {N}` text + a `Clear all` button (`aria-label="Clear all selected"`). No chips rendered. Unchecking a row in the list above is the removal affordance; `Clear all` sets `selectedIds` to `[]`. |
| No search results | "No programs match "xyz"." — muted, inside the list region, announced via `aria-live="polite"`. |
| Zero items available | "No programs in the catalog yet." |
| Disabled (saving) | Input and all checkboxes disabled; chips' remove buttons disabled. |

**Four consumers, one control:** Edit family, New family, New program, Edit program families. That is
what justifies the new file rather than a local helper.

**⚠️ Out of scope, stated so it is not attempted:** this is *not* a general Popover/Command primitive,
and it must not be sold as one. It is a catalog picker for small in-memory lists, living inside a
modal. Building a real popover layer (focus trapping, collision detection, portals) is a platform
project, and §27 forbids it.

---

## H. Note Create / Bulk Create governance

### H.1 Current behaviour, verified (not assumed)

- **Backend:** `POST /course-program-catalog` carries `@PreAuthorize("hasRole('ADMIN')")`
  (`CourseProgramCatalogController.java:46-50`). It is the **only** insert path into `course_programs`
  — `INSERT INTO course_programs` appears exactly once in the codebase
  (`CourseProgramCatalogRepository.java:53`), reached only from `create()`, reached only from that
  controller method. Same for `program_families` (`:60`, from `POST /families`, also ADMIN).
- **Frontend:** the inline-create affordance is gated on `canCreateCatalogProgram`
  (`applicable-programs-combobox.tsx:251`), passed as `isAdmin` / `userRole === "ADMIN"` /
  `currentUserRole === "ADMIN"` by all three note-authoring consumers.
- **Q15 — can ordinary users create shared catalog entries? NO.** **Q14 — what exact authorization?**
  `hasRole('ADMIN')`, enforced server-side, with the UI gate as defence in depth rather than as the
  control.
- **⚠️ Note the asymmetry, and keep it:** catalog creation is `ADMIN` *role* only — it does **not**
  extend to `ProfileType.TEACHER`, unlike the note-authoring "curator" predicate
  (`TEACHER || ADMIN`). That is correct for governed shared data and should not be "harmonised".

### H.2 §21's governance defect: **none found**

The brief anticipates a defect. There is none — governance is already correct at every layer. §21's
"identify the smallest safe correction" is therefore discharged with **no correction required**, and
this plan explicitly declines to invent one.

§21's separate-free-text carve-out also already holds and must not be removed: `users.course_program`
and `notes.course_program` are legacy **free-text** fields fed by `course-program-combobox.tsx` with
`allowCustom: true`, surfaced through `use-course-program-catalog.ts`. They are not the shared
canonical catalog, they accept arbitrary strings today, and this release does not touch them.

### H.3 Recommendation: **OPTION B**, made genuinely canonical

Option B is already **half-built** — and the half that exists is the wrong half. Two independent
create forms exist today:

| | Admin (`admin-course-program-catalog-section.tsx`) | Note authoring (`applicable-programs-combobox.tsx:365-424`) |
|---|---|---|
| Families | **single** `<select>` → `programFamilyId` (`:125`) | **multiple** `<select multiple>` → `programFamilyIds` (`:187`) |
| Near-match warning | ✅ (`:83-108`) | ✅ (`:134-158`) |
| Conflict → "select existing" | text only (`:139-141`) | a real button (`:418-422`) |
| Layout | permanently-visible page form | `AppModal` |

So the *note-authoring* modal is currently the more capable one, and the Admin form is the laggard —
exactly the divergence §10 warns about, already realised.

**The fix: extract one `CourseProgramCreateModal`** (`frontend/components/metadata/course-program-create-modal.tsx`)
and mount it from both surfaces.

```
ORDINARY USER (Note Create / Edit / Bulk Create)
  - searches and selects existing canonical programs
  - sees family shortcut buttons (Accounting · 5, Engineering · 18, …)
  - no create affordance at all — unchanged from today
  - not-found copy:  "Can't find your program? You can still continue without selecting one."
                     (owner-directed rewrite: the earlier draft — "Ask an admin to add it to the
                     catalog" — exposes internal catalog governance to a learner audience that mostly
                     has no admin relationship to invoke. The replacement states the actual consequence
                     of not finding a match — nothing blocks the learner from continuing — rather than
                     pointing at a person or process they cannot act on. Today this surface shows
                     nothing at all, which reads as a dead end; this closes that gap without inventing
                     new mechanism.)

ADMIN / CURATOR (same surfaces)
  - additionally sees:  [ + Add "Finance" to the catalog ]
  - which opens THE SAME CourseProgramCreateModal the Admin tab's [ + New program ] opens
  - same §G family multi-select, same near-match check, same validation, same POST,
    same conflict handling — one component, one contract
  - on success: the new program is selected on the NOTE only
```

**§11's semantics, restated as a test rather than a promise:** creating `Computer Engineering` with
families `Engineering` + `Computing & Technology` adds **`Computer Engineering` and nothing else** to
the note's Applicable Programs. It must not pull in Civil Engineering, Information Technology, or any
other member. This already holds — `handleCreate` calls `selectProgram(createdProgram)` with the
single created program (`applicable-programs-combobox.tsx:192`), and family expansion is a separate
user action through `handleFamilyExpansion` (`:222-232`). The extraction must preserve it, and §N pins
it.

**Retained difference, by design:** the Admin entry point opens the modal with an empty name field;
the note-authoring entry point opens it pre-filled with the typed draft and returns the created
program to its caller via `onCatalogProgramCreated`. That is a prop (`initialName`, `onCreated`), not
a fork.

**⚠️ Re-checked this session, owner decision #4:** `note-editor-form.tsx`, `bulk-generation-page-client.tsx`
and `private-note-detail-page-client.tsx` each already conditionally render a **separate, existing**
free-text `CourseProgramCombobox` (`allowCustom: true`, feeding the legacy `courseProgram` field) as an
alternative to `ApplicableProgramsCombobox`, gated on curator/`courseProgramShadowed` state
(`note-editor-form.tsx:427-476`). This is a genuine pre-existing personal path, confirming the owner's
premise — but the two comboboxes are not guaranteed to render simultaneously in every state, so the new
not-found copy does **not** name or point at that field by label; it states the consequence ("you can
still continue") rather than directing the learner to a control that may not be visible in their
current render. **No new free-text mechanism is created — none is needed.**

---

## I. Family rename contract

**⚠️ Owner-locked anti-drift invariant, to carry into the Codex prompt verbatim:**

> Program Family name is display data. Program Family ID is identity.

Application behavior — every repository statement, service method, API contract, and frontend
reference to a family outside a duplicate-name check — must resolve and persist by durable `id`, never
by `name`. **V147's exact-name matching is an explicit, scoped exception, not a precedent.** It exists
only because runtime-created catalog rows (4 of 6 families, ~35 of 62 programs) carry
`UUID.randomUUID()` ids with no environment-portable literal to hardcode into a migration that also
runs in CI and on every developer machine (§J.2). **No application code — repository, service,
controller, or frontend — may copy V147's name-resolution strategy.** Every method added in this
release (rename, family-side replace, family-with-initial-members create) takes and returns `UUID`,
never resolves a family by parsing or matching `name`.

### I.1 Is family NAME used as durable identity anywhere? — **NO.** Proof, by layer

| Layer | Evidence |
|---|---|
| Schema | `course_program_family.program_family_id` is `uuid` FK → `program_families(id)` (V146). Nothing joins on name. |
| Repository | Every membership statement binds `program_family_id` (`:54`, `:55`, `:58`). The only two name-keyed statements are `FIND_PROGRAM_FAMILY_BY_NORMALIZED_NAME` (`:59`) — a **duplicate-check lookup**, not a join — and `FIND_ID_BY_NAME` (`:61`), which is `course_programs`-scoped legacy-name resolution and never touches families. |
| Service | `validateAndDeduplicateFamilyIds` (`:133-138`) resolves by `UUID`. |
| API | `ProgramFamilyResponse` is `(UUID id, String name)`; `UpdateCourseProgramCatalogRequest` carries `List<UUID>`. |
| Notes | **No family id or name is persisted on a Note at all.** Expansion writes explicit `note_course_program` rows keyed by `course_program_id` (ADR-001 ruling 5 + `docs/features/program-families.md`). A rename cannot reach a note by construction. |
| Frontend | Family shortcuts key on `family.id` (`applicable-programs-combobox.tsx:110-127`, `:298`); chips key on `family.id`; `editFamilyIds` holds ids. |
| Generation | `StudyPackGenerationContextResolver` consumes `courseProgram` free text and `domainContext`; **no Program Family reaches a prompt**, per ADR-001 and confirmed by the absence of any family reference in the generation path. |
| Migrations | **V142 already performed an identity-preserving rename in production** (`Special Needs Education – Generalist` → `Special Needs Education`, by `id`), and `EducationProgramFamilyMigrationTest` guards it with a `note_course_program` fixture created *before* the rename runs. The precedent exists and is tested. |

**Conclusion: rename is safe, requires no data migration, and — critically — requires no ADR
amendment** (§27). ADR-001 constrains what a family *does* (expansion is unconditional, membership is
the sole input, never at read time). A display name is none of those things.

⚠️ One honest caveat, recorded rather than buried: `EducationProgramFamilyMigrationTest`'s own Javadoc
notes its production denominator was **zero** — no note was linked to the renamed row. The rename
guarantee above rests on the *structure* (FK joins by id, families never persisted on notes), which is
strong, not on observed surviving data.

### I.2 Uniqueness and normalization — two real defects to fix in the same file

**Defect 1 — the family duplicate predicate is weaker than the program one.**
`FIND_PROGRAM_FAMILY_BY_NORMALIZED_NAME` is `WHERE lower(trim(name)) = ?` (`:59`), while the program
equivalent is `lower(regexp_replace(trim(name), '\s+', ' ', 'g'))` (`:36-38`). The Java side passes a
key from `CourseProgramNormalizationUtils.normalizeForLookup`, which **does** collapse internal
whitespace — so the Java key and the SQL predicate disagree, and a stored family name with a double
internal space escapes the check. The DB constraint cannot catch it either: `uk_program_families_name`
is `UNIQUE (name)` on the **raw** value. Align the family predicate to the program's `regexp_replace`
form. One-line change, same file, no migration.

**Defect 2 — rename must exclude itself from the conflict check.** `createProgramFamily` (`:57-76`)
rejects any normalized-name match. A rename reusing that logic unchanged would reject
`Health Sciences` → `Health sciences` as a conflict *with itself*. The rename path needs
`WHERE … AND id <> ?`. This is the classic rename bug and it will not be caught by any test written
only for the happy path — §N pins it explicitly.

**Pre-existing normalization behaviour, flagged not changed:**
`CourseProgramNormalizationUtils.normalizeForStorage` delegates to `SubjectNormalizationUtils`, which
rewrites **every** hyphen/en-dash/em-dash to a spaced en dash (`" – "`). So a family renamed to
`Tech-Voc` is **stored** as `Tech – Voc`. This already applies to family and program creation today
(it is why `Technical-Vocational Teacher Education`, created by migration V142, retains a plain hyphen
while `Senior High – ABM` carries an en dash). It is surprising, it is pre-existing, and this release
does **not** change it — but the Codex prompt must say so, or an implementer will "fix" it and
silently alter the normalization contract shared with Subject.

### I.3 Contract

```
PATCH /course-program-catalog/families/{id}          @PreAuthorize("hasRole('ADMIN')")
Content-Type: application/json
{ "name": "Built Environment", "programIds": ["<uuid>", "<uuid>", ...] }
→ 200 ProgramFamilyResponse { id, name }         ← id UNCHANGED

  name       optional — omitted/null leaves the name untouched
  programIds optional — omitted/null leaves membership untouched;
                        [] CLEARS membership (matching the program-side PATCH's semantics)

Errors (all existing types, no new exception classes needed):
  InvalidProgramFamilyNameException      blank, or > 120 chars
  ProgramFamilyNameConflictException     normalized-name collision with a DIFFERENT family
  UnknownProgramFamilyException          {id} does not exist
  CourseProgramNotFoundException         a programId does not resolve
```

Distinguishing "omitted" from "explicitly empty" needs care in Java records: `List<UUID> programIds`
must not be normalized to `List.of()` by a compact constructor the way
`UpdateCourseProgramCatalogRequest:8` does, or "leave membership alone" becomes indistinguishable from
"clear membership". Simplest safe resolution: **always send both fields from the UI** and treat null
`programIds` as "no change" in the service, with the field left nullable in the record.

⚠️ **Consequence to close, or the branch is dead code that passes review by looking reasonable:**
because the real client always sends both fields, the "omitted `programIds` leaves membership
untouched" semantics would be exercised **only** by tests. Test 8's `MockMvc` call must therefore send
the omitted-field body explicitly (`{"name":"…"}` with no `programIds` key) and assert membership
survived — otherwise the documented contract has no executing caller anywhere, which is precisely the
`v0.116.0`/`v0.117.0` unexercised-change pattern.

---

## J. Migration / backfill

### J.1 Next valid migration number: **V147**

`ls backend/src/main/resources/db/migration/ | sort -n` → highest is **V146**. Confirmed by direct
numeric sort, not by reading a doc.

File: `backend/src/main/resources/db/migration/V147__program_family_initial_membership.sql`

### J.2 ⚠️ The design decision that most affects whether the build stays green

§17 asks the migration to "abort/fail safely if an expected canonical family/program … does not
exist". **Taken literally, that breaks every fresh-database Flyway run**, and this must be settled
before the prompt is written rather than discovered in CI:

- **4 of the 6 families** (Accounting, Health Sciences, Computing & Technology, Built Environment &
  Design) and **~35 of the 62 programs** — including Nursing, Medicine, Pharmacy, Interior Design,
  Computer Science, Management Accounting — exist **only as production runtime data**. V106 seeded 21
  programs + Engineering; V142 added 6 + Education. Nothing seeds the rest.
- On an empty database migrated V1→V147, a `RAISE EXCEPTION` on any missing canonical row would fail:
  (a) `NativeQueryPostgresIntegrationTest`, whose PostgreSQL 18 container applies the **real** Flyway
  migration set on every `./mvnw test`; and (b) `./mvnw spring-boot:run` against a fresh local
  `study_snap`.
- That is precisely the `v0.93.0` failure class CLAUDE.md cites as the reason to call `advisor()`
  before writing a Codex prompt — a migration/config collision that breaks ~1,750 tests.

**Decision: the migration is purely additive and no-ops on absent rows; the loud failure moves into a
guard test where the canonical rows are a fixture.** The test seeds the six families and the 44
programs, replays V147's **actual SQL read from the file**, and asserts all 50 pairs exist. A missing
or misspelled canonical name then fails the build with a named assertion — which is the outcome §17
actually wants — without coupling a fresh developer database to production's runtime contents.

**Rejected alternative, on the record:** a `DO $$ … RAISE EXCEPTION … $$` guard inside V147 itself
(the shape V146 uses at its tail). Rejected because V146's guard asserts a *self-contained*
invariant — that rows it just copied from a column in the same database landed — whereas a V147 guard
would assert the presence of rows no migration creates. Same syntax, categorically different premise.

**Name-based resolution is forced, not preferred.** Engineering and Education carry seed-range ids
(`10000000-…-0001/0002`), but the other four carry **random production UUIDs** generated by
`UUID.randomUUID()` in `insertProgramFamily`. Hardcoding those would embed production-instance
literals into a migration that also runs on every developer machine and CI container. Exact-name
joins are the only strategy that is both deterministic and environment-portable. **No fuzzy matching,
no `LIKE`, no `similarity()`, no case-folding** — every one of the 50 approved pairs was verified this
session to resolve by exact, byte-identical name.

### J.3 Shape

```sql
-- V147__program_family_initial_membership.sql
--
-- ⚠️ DATA MIGRATION. Adds no column, table, constraint or index.
-- ⚠️ ADDITIVE ONLY. It contains no DELETE and no UPDATE. It can only ever add membership rows.
-- ⚠️ IT DOES NOT WRITE course_programs.program_family_id. That column is legacy and already stale
--    (26 rows carry a value; the three post-v0.150.0 assignments carry NULL). V142 wrote it; do not
--    copy that. The join table is the only membership source -- CourseProgramCatalogRepository
--    never reads the column.
-- ⚠️ IT CREATES NO course_programs AND NO program_families ROW. An approved name that does not
--    resolve is silently skipped HERE, by design (see below), and caught LOUDLY by
--    ProgramFamilyInitialMembershipMigrationTest, which supplies the canonical rows as a fixture.
--    A hard RAISE here would fail every fresh-database Flyway run, because 4 of 6 families and ~35
--    of 62 programs exist only as production runtime data.

INSERT INTO course_program_family (id, course_program_id, program_family_id)
SELECT gen_random_uuid(), cp.id, pf.id
FROM (VALUES
    -- ⚠️ THE FIRST ROW CARRIES EXPLICIT ::varchar CASTS AND MUST KEEP THEM. Both join columns
    -- (program_families.name, course_programs.name) are varchar; an untyped VALUES literal resolves
    -- as text, and PostgreSQL types the whole VALUES list from its first row. Casting once here is
    -- ordinary type hygiene for a text/varchar join, not a fix for an observed failure — nothing has
    -- been implemented or run yet, so there is no incident to cite.
    ('Engineering'::varchar, 'Civil Engineering'::varchar),
    ('Engineering', 'Electrical Engineering'),
    ...                                        -- all 50 approved pairs, verbatim from §15
    ('Built Environment & Design', 'Construction Engineering and Management')
) AS approved (family_name, program_name)
JOIN program_families pf ON pf.name = approved.family_name
JOIN course_programs  cp ON cp.name = approved.program_name
ON CONFLICT (course_program_id, program_family_id) DO NOTHING;
```

- **Idempotent** two ways over: `ON CONFLICT … DO NOTHING` against `uk_course_program_family_program_family`,
  and `gen_random_uuid()` only ever generating a pk for a row that survives the conflict clause.
- **Non-destructive:** no `DELETE`, no `UPDATE`, no `TRUNCATE`. Membership rows outside the matrix
  cannot be affected. (Today there are none — §C.1 — but the property is structural, not data-dependent.)
- **Deterministic:** inner joins on exact names; an unresolvable name contributes no row rather than a
  wrong one.
- **All 50 pairs are listed, not only the 21 missing ones.** Listing the full approved matrix makes
  the migration a statement of intent that is independently re-runnable, and the 29 already-present
  pairs cost one conflict each.

### J.4 What it will do in production (exact, from §C)

| Family | Rows inserted | Rows skipped (already present) |
|---|---|---|
| Engineering | 0 | 18 |
| Education | 0 | 8 |
| Health Sciences | **5** | 0 |
| Accounting | **3** | 2 |
| Computing & Technology | **6** | 0 |
| Built Environment & Design | **7** | 1 |
| **Total** | **21** | **29** |

`course_program_family` goes 29 → **50** rows. 44 of 62 programs gain ≥1 family; 18 remain
deliberately unassigned; **6 programs hold two families**, the first production use of the
many-to-many relation.

### J.4a ⚠️ Post-deploy production acceptance check — required, not optional

Because J.2 deliberately removed the migration's own `RAISE EXCEPTION`, **nothing else in this design
fails loudly if a name silently doesn't match in production** — the guard test (§N test 11) only proves
the matrix resolves against its *own* fixture, not against the live database at deploy time, and a name
edited in production between this audit and deploy (an admin rename, for instance — this release ships
renames) would make one `ON CONFLICT`-eligible pair join to nothing and insert silently.

**⚠️ Owner-tightened: a family-count-only check is not sufficient proof.** 18 members matching an
expected count of 18 does not prove they are the *correct* 18 — a count can be right for the wrong
reason (one approved pair silently missing, offset by one pre-existing extra elsewhere). The primary
acceptance proof must validate the **approved pair set itself**, via an anti-join against the same
`VALUES` matrix V147 uses — count is retained only as a secondary sanity check. Run both read-only
queries immediately after the release deploys, before signoff:

```sql
-- PRIMARY: missing approved pairs. Expect 0 rows.
WITH approved(family_name, program_name) AS (VALUES
    ('Engineering', 'Civil Engineering'),
    ('Engineering', 'Electrical Engineering'),
    ...                                            -- all 50 approved pairs, verbatim from §15/V147
    ('Built Environment & Design', 'Construction Engineering and Management')
)
SELECT a.family_name, a.program_name
FROM approved a
LEFT JOIN program_families pf ON pf.name = a.family_name
LEFT JOIN course_programs  cp ON cp.name = a.program_name
LEFT JOIN course_program_family cpf
       ON cpf.program_family_id = pf.id AND cpf.course_program_id = cp.id
WHERE cpf.id IS NULL;
-- Expect: 0 rows. Any row returned is a named, reported gap — do not sign off silently.

-- SECONDARY (reported separately, never auto-failed and never deleted — the migration is
-- deliberately non-destructive, §18): membership pairs that exist in production but are NOT
-- in the approved matrix.
WITH approved(family_name, program_name) AS ( /* same 50-row VALUES list as above */ )
SELECT pf.name AS family, cp.name AS program
FROM course_program_family cpf
JOIN program_families pf ON pf.id = cpf.program_family_id
JOIN course_programs  cp ON cp.id = cpf.course_program_id
LEFT JOIN approved a ON a.family_name = pf.name AND a.program_name = cp.name
WHERE a.family_name IS NULL
ORDER BY pf.name, cp.name;
-- Expect from this session's audit: 0 rows. A nonzero result is reported, not treated as a defect —
-- it may be valid curator work assigned between this audit and deploy.

-- SECONDARY sanity check only, not the correctness proof:
SELECT pf.name AS family, count(cpf.id) AS member_count
FROM program_families pf
LEFT JOIN course_program_family cpf ON cpf.program_family_id = pf.id
GROUP BY pf.name ORDER BY pf.name;
-- Expect: Accounting 5, Built Environment & Design 8, Computing & Technology 6,
-- Education 8, Engineering 18, Health Sciences 5  (total 50, if the extras query above found 0 extras)
```

**⚠️ Both queries' `VALUES` list must be copied verbatim from V147's own SQL file, never hand-retyped.**
Two independent artifacts now carry the same 50-pair matrix (the migration and this acceptance query);
a one-byte divergence between them would make the acceptance query pass against a migration that
inserted something different, which is exactly the failure class this check exists to catch. The same
rule applies to the guard test (§N test 11), which already reads V147's SQL from the file rather than
re-declaring the pairs — do the acceptance query the same way.

**Do not encode "extras must always be zero" as migration behavior.** Today's zero-extras finding is a
property of *this moment's* production data, not a rule the migration enforces or ever should — the
migration has no DELETE and must never grow one. This is the production-acceptance step that replaces
the loud migration failure §J.2 chose not to ship, the same way v0.150.0's Admin-UI population doubled
as its end-to-end acceptance check.

### J.5 ⚠️ Deploy-ordering statement (CLAUDE.md requires one)

**This release removes and renames nothing in the existing API** — `GET /course-program-catalog`,
`GET /families`, `POST /course-program-catalog`, `POST /families` and `PATCH /{id}` keep their exact
current contracts and required fields. The only backend additions are a **new** endpoint
(`PATCH /families/{id}`) and a **new optional** field (`programIds` on `POST /families`). So there is
no breaking direction: an old frontend against the new backend is unaffected; a new frontend against
an old backend loses only the two new behaviours, which fail as 404/ignored-field rather than
corrupting data. **Backend-first is safe and preferred**, so the migration's 21 rows are present
before the family-first UI renders counts. Still run `scripts/check-deploys.sh` after the release PR
merges — a merge is not a deploy.

⚠️ **One user-visible effect lands in that window and should be named rather than discovered.** During
backend-first rollout, V147's 21 memberships are live while the **old** frontend is still serving.
`applicable-programs-combobox.tsx` derives its family shortcuts dynamically from the catalog
(`:99-128`), so curators will see `Health Sciences · 5`, `Computing & Technology · 6` and a larger
`Built Environment & Design · 8` appear on the existing Note authoring surfaces **with no frontend
change and no release note yet published**. This is benign and correct — expansion is unconditional
and membership-driven, which is exactly the designed behaviour — but it is a real change arriving
ahead of the release that documents it. It is the same mechanism V142's own comment describes
("seeding this family is the whole feature — the UI starts rendering … with no code change"). Mention
it in the release notes rather than letting it read as an unexplained change.

### J.6 No `.sql` handoff file is needed

§14's backfill is being delivered as a **Flyway migration**, which the deploy applies. Claude executes
no production write. The `docs/claude-plans/*.sql` owner-handoff protocol applies only to writes that
cannot ride a migration; none here do.

---

## K. Data / API changes

| Item | Class | Detail |
|---|---|---|
| `course_program_family` table, constraints, indexes | **EXISTING / REUSE** | V146. No schema change at all this release. |
| `GET /course-program-catalog` | **EXISTING / REUSE** | Already returns `programFamilies[]` per program. Family member lists and overview counts derive from it. |
| `GET /course-program-catalog/families` | **EXISTING / REUSE** | Supplies the family universe including zero-member families. **No `memberCount` field added** (§E.1). |
| `POST /course-program-catalog` (create program + N families, atomic) | **EXISTING / REUSE** | Already atomic (`CourseProgramCatalogService.java:106-131`). Q7 = **YES**. Frontend-only fix to stop sending the single-family legacy form. |
| `PATCH /course-program-catalog/{id}` (program-side membership) | **EXISTING / REUSE** | Retained for program-side Edit (§F.3). |
| `POST /course-program-catalog/families` | **MODIFY** | Accept optional `programIds`; insert memberships inside the existing `@Transactional`. Q6 today = **NO**; after this = **YES**. Rewrite the `:47-54` Javadoc that forbids it. |
| `PATCH /course-program-catalog/families/{id}` | **NEW** | Rename + family-side membership replace, one transaction. §I.3. |
| `CourseProgramCatalogRepository.updateProgramFamilyName(id, name)` | **NEW** | One `UPDATE program_families SET name = ? WHERE id = ?`. |
| `CourseProgramCatalogRepository.replaceProgramMemberships(familyId, programIds)` | **NEW** | `DELETE … WHERE program_family_id = ?` + insert. ⚠️ **by family, never by program** (§E.3). |
| `CourseProgramCatalogRepository.findProgramFamilyById(id)` | **NEW** | Existence check for PATCH. |
| `FIND_PROGRAM_FAMILY_BY_NORMALIZED_NAME` | **MODIFY** | `lower(trim(…))` → `lower(regexp_replace(trim(…), '\s+', ' ', 'g'))`, plus an `id <> ?` variant for rename self-exclusion (§I.2). |
| `ProgramFamilyResponse` | **NO CHANGE** | `(UUID id, String name)` stays. |
| `CourseProgramCatalogItemResponse` | **NO CHANGE** | Including the legacy scalar shim — removing it is lifecycle work. |
| `V147__program_family_initial_membership.sql` | **NEW** | §J. |
| `api.ts`: `updateProgramFamily`, `createProgramFamily(name, programIds)` | **NEW / MODIFY** | Two functions; `parseProgramFamily` reused. |
| `components/ui/catalog-multi-select.tsx` | **NEW** | §G. |
| `components/metadata/course-program-create-modal.tsx` | **NEW** (extraction) | §H.3. |
| `components/admin/admin-program-families-section.tsx` | **NEW** | §E. |
| Domain Context, generation, Note persistence, `note_course_program`, Review Sets, Exam Goal | **NO CHANGE** | §24, §22. Nothing in this release reaches any of them. |

---

## L. Authorization

| Action | Current | Desired | Change |
|---|---|---|---|
| Read catalog (`GET /course-program-catalog`) | `USER` or `ADMIN` | same | none |
| Read families (`GET /families`) | `ADMIN` | same | none |
| Create Course / Program | `ADMIN` | `ADMIN` | none |
| Create Program Family | `ADMIN` | `ADMIN` | none |
| Edit program → families (`PATCH /{id}`) | `ADMIN` | `ADMIN` | none |
| **Rename family / edit family members** | *does not exist* | `ADMIN` | **new endpoint, `@PreAuthorize("hasRole('ADMIN')")`** |
| Create family **with** initial members | *does not exist* | `ADMIN` | same endpoint, unchanged guard |
| Ordinary user creates a catalog entry | **blocked** | blocked | none |
| Ordinary user sets a Note's Applicable Programs from the catalog | allowed | allowed | none |
| Free-text `users.course_program` / `notes.course_program` | allowed (`allowCustom: true`) | **unchanged** | none (§21 carve-out) |

**Net: one new guard, identical to the five that exist. No authorization boundary moves.** Which also
means §L does not, by itself, trigger the pressure-test "moves an authorization or privacy boundary"
condition — but §R elects a cold agent on a different trigger.

---

## M. Mobile / responsive and accessibility

### M.1 Mobile

- **Families overview:** table at `sm:` and up; the existing card list below it (mirroring
  `admin-course-program-catalog-section.tsx:179-189`). Full-width `Edit` button per card.
- **`AppModal` is already correct for large selection tasks and needs no bottom-sheet work.** Its
  content region is `flex-1 overflow-y-auto` with a `shrink-0` actions row
  (`components/ui/app-modal.tsx`), so Save/Cancel cannot be pushed off-screen no matter how many chips
  render above them. This is the same reasoning `docs/features/program-families.md` records for its
  "no artificial chip limit" ruling — **and the same caveat carries over: it is derived from reading
  the CSS, not from a device render.** Re-verify if `AppModal` changes.
- **Long labels** (`Agricultural and Biosystems Engineering`, `Construction Engineering and
  Management`, `Technical-Vocational Teacher Education`) must **wrap, never truncate**, in both the
  checkbox rows and the chips — a truncated `Construction Engineering and Man…` is indistinguishable
  from a sibling at 320px. Give the checkbox row `items-start` so the box aligns to the first line.
- **Scroll containment:** the item list gets its own `max-h` + `overflow-y-auto` (as `AddNotesModal`
  does with `max-h-64`) so long lists scroll inside the modal rather than lengthening it. Engineering
  at 18 chips plus a 62-row program list is the stress case; test at 320px.
- **Tap targets ≥ 44px** on checkbox rows and chip remove buttons. The existing chip `×` at
  `applicable-programs-combobox.tsx:335-343` is a bare glyph and is **below** that today — fix it in
  the new component rather than inheriting it.
- **No horizontal scroll** on either tab at 320px, except the programs table inside its existing
  `overflow-x-auto` wrapper.

### M.2 Accessibility

Current state, measured: `role="combobox"` and `aria-multiselectable` have **zero occurrences in the
entire frontend**. `suggestion-combobox.tsx` has `role="listbox"`/`role="option"`/`aria-selected` and
`aria-autocomplete="list"` but **no `aria-expanded`** and **Escape-only** keyboard handling.
`AddNotesModal` has no ARIA roles at all beyond the dialog, and no `aria-live`.

The new control is the chance to do this once, properly — and §26's "do not create an inaccessible
div-based fake dropdown" is satisfiable cheaply **because it is not a dropdown**. A modal containing a
search input and a list of real checkboxes is natively accessible:

- **Real `<input type="checkbox">` inside a `<label>`**, with a genuine `checked` binding. This gives
  keyboard operation (Tab + Space), screen-reader checkbox semantics and visible focus for free. ⚠️
  Explicitly **do not** replicate `AddNotesModal`'s `checked={false}` hack (`:1235`).
- **List region** `role="group"` with `aria-labelledby` pointing at the field label. A
  `role="listbox"` + `aria-multiselectable` construction is *also* valid, but it would then owe
  `aria-activedescendant` and full arrow-key management; native checkboxes are the lower-risk choice
  and the brief's checkbox semantics point the same way.
- **`aria-live="polite"`** on the filtered-result count ("6 programs") and on the empty-result message.
  Neither existing component announces these, and a search that silently empties is the single worst
  screen-reader failure available here.
- **Selected block** keeps `aria-label="Selected Course / Programs"` on the container and
  `aria-label={`Remove ${name}`}` per chip — this part of `ApplicableProgramsCombobox` is already good.
- **Focus management:** `AppModal` handles trap and restore. On open, focus the search input; after
  removing the last chip, move focus to the search input rather than letting it fall to `<body>`.
- **Errors** keep `role="alert"` (the existing pattern at `:417`).
- **Tabs:** `role="tablist"`/`role="tab"`/`aria-selected`/`aria-controls` per
  `note-detail-tabs.tsx`, plus Left/Right arrow roving tabindex.

---

## N. Testing plan

**⚠️ CLAUDE.md, three times over: every new endpoint owes ONE test that issues a REAL REQUEST** —
`MockMvc` with `.contentType(MediaType.APPLICATION_JSON)` and a body, per the existing pattern in
`NoteControllerTest`. A direct handler-method call passes under the `v0.119.0` defect by construction
and does not count.

| # | Invariant | Test | Where |
|---|---|---|---|
| 1 | **Family-side replace deletes by family, not by program.** Health Sciences ← {Nursing} while Computer Engineering ∈ {Engineering}; save Health Sciences ← {Nursing, Computer Engineering}; assert Computer Engineering is **still** in Engineering. | `replacingFamilyMembershipPreservesOtherFamilyMemberships` | `CourseProgramCatalogServiceTest` |
| 2 | Family-side replace with `[]` clears that family only. | `clearingFamilyMembershipLeavesOtherFamiliesIntact` | same |
| 3 | **Rename preserves id and membership (and does not recreate the family).** Rename Built Environment & Design → Built Environment; assert `id` unchanged, all 8 memberships present by the same membership-row ids, and no second `program_families` row was inserted. | `renamePreservesIdentityAndMembership` | same |
| 3a | **Rename to the family's own current name (byte-identical no-op) succeeds**, not rejected as a self-conflict. | `renamingToTheSameNameIsANoOp` | same |
| 3b | **Rename does not touch a Note.** Seed a Note whose `note_course_program` includes a program that is a member of the renamed family; rename the family; assert the Note's `note_course_program` rows, applicability, and (where feasible) the resolved generation context are byte-identical before/after. This is the test that makes §I.1's "no family id or name is persisted on a Note" claim executable, not just structural. | `renamingFamilyDoesNotChangeAnyNote` | `CourseProgramCatalogServiceTest` or an integration test alongside `EducationProgramFamilyMigrationTest`'s precedent |
| 4 | **Rename self-exclusion against a case/whitespace variant.** `Health Sciences` → `Health sciences` succeeds (does not conflict with itself). | `renameToACaseVariantOfItsOwnNameIsNotAConflict` | same |
| 5 | Rename to another family's normalized name → `ProgramFamilyNameConflictException`. | `renameToAnExistingFamilyNameIsRejected` | same |
| 6 | Family create with initial members is **atomic** — an unknown `programId` rolls back the family row too. | `createFamilyWithUnknownProgramCreatesNoFamily` | same |
| 7 | Family create with **no** members still succeeds. | `createFamilyWithoutMembersSucceeds` | same |
| 8 | `PATCH /course-program-catalog/families/{id}` — **real MockMvc request, JSON content type + body**, ADMIN → 200. ⚠️ **Two bodies, not one:** (a) `{"name":…,"programIds":[…]}` and (b) `{"name":…}` with `programIds` **omitted**, asserting membership survived — (b) is the only caller the no-change branch will ever have (§I.3). | `renameProgramFamilyReturnsUpdatedFamily`, `renameWithoutProgramIdsLeavesMembershipUntouched` | `CourseProgramCatalogControllerTest` |
| 9 | Same endpoint, non-admin → 403. | `renameProgramFamilyRejectsNonAdmin` | same |
| 10 | `POST /course-program-catalog/families` — **real MockMvc request** with `programIds` → memberships created. | `createProgramFamilyWithInitialMembers` | same |
| 11 | **V147 lands all 50 approved pairs.** Seed the 6 families and **the 44 distinct Course / Programs named in the 50-pair matrix** — ⚠️ derive that fixture list *from the matrix itself*, not from a copy of production (62 rows) and not from a hand-kept list; the two counts coincide only because every matrix program ends up with ≥1 family. Then replay V147's **actual SQL read from the file** and assert 50 rows plus the exact per-family sets. | `ProgramFamilyInitialMembershipMigrationTest` | `backend/.../migration/`, following the read-the-real-SQL pattern at `NativeQueryPostgresIntegrationTest:1349` and `:2755` |
| 12 | **V147 is idempotent** — run it twice, still 50 rows. | same class | same |
| 13 | **V147 preserves an off-matrix membership** — pre-seed `Law → Engineering`, run, assert it survives and the total is **51 = 50 + 1**. ⚠️ `Law` is deliberately chosen because it appears in **no** approved pair, so the extra row cannot be one the matrix would have inserted anyway; picking a matrix program here would make the assertion pass for the wrong reason. | same class | same |
| 14 | **V147 no-ops rather than failing when a canonical row is absent** — omit `Pharmacy` from the fixture; assert the migration completes and the other 49 pairs land. This is the test that protects fresh-database Flyway runs. | same class | same |
| 15 | **V147 does not write `course_programs.program_family_id`** — assert the column is unchanged for every row. | same class | same |
| 15a | **V147 creates no `course_programs` row and no `program_families` row** — assert both tables' row counts are unchanged before/after (the migration is a pure `course_program_family` insert; §17/§J.2 forbid it from ever creating a canonical catalog row, even for a name it cannot resolve). | same class | same |
| 16 | **§11 semantics: creating a program with 2 families selects only that program on the Note.** | existing `applicable-programs-combobox.test.tsx`, extended | frontend |
| 17 | The shared multi-select renders a genuinely `checked` box for a selected item, and the item **stays in the list**. | `catalog-multi-select.test.tsx` (new) | frontend |
| 18 | Search filters; a zero-result search renders the empty message in an `aria-live` region. | same | frontend |
| 19 | Removing a chip deselects the corresponding checkbox. | same | frontend |
| 20 | Family overview counts include a **zero-member** family and are **not** filtered by `is_active`. | `admin-program-families-section.test.tsx` (new) | frontend |
| 21 | `?view=` round-trips: deep link to the Programs tab renders it active on load. | same | frontend |
| 22 | Ordinary (non-admin) user sees **no** create affordance on Note Create, Note Detail and Bulk Create. | existing consumer tests, extended | frontend |
| 23 | **Post-deploy production acceptance** — after the release deploys, run §J.4a's anti-join query (0 missing approved pairs is the primary proof), the extras report (informational), and the count query (secondary sanity check only). Not a code test; a signoff step, owed because §J.2 removed the migration's own loud-failure path. | manual, at signoff | production, read-only |

**⚠️ The free check CLAUDE.md demands before any deeper one:** every behavioural change above has a
test file moving beside it in the same diff. If any slice lands with a behaviour change and **no**
test file touched, that item is unverified and must be recorded as such rather than written up as
shipped — `v0.116.0` and `v0.117.0` both shipped silent no-ops that the entire green suite could not
see.

---

## O. Blast radius

### MUST CHANGE

| File | Why |
|---|---|
| `backend/.../repository/CourseProgramCatalogRepository.java` | rename UPDATE; family-scoped replace; find-family-by-id; normalized-name predicate fix |
| `backend/.../service/CourseProgramCatalogService.java` | `renameAndReplaceMembers`; `createProgramFamily` accepts members; **rewrite the `:47-54` Javadoc** |
| `backend/.../controller/CourseProgramCatalogController.java` | `PATCH /families/{id}` |
| `backend/.../dto/CreateProgramFamilyRequest.java` | optional `programIds` |
| `backend/.../dto/UpdateProgramFamilyRequest.java` | **NEW** |
| `backend/.../db/migration/V147__program_family_initial_membership.sql` | **NEW** |
| `backend/.../migration/ProgramFamilyInitialMembershipMigrationTest.java` | **NEW** |
| `backend/.../service/CourseProgramCatalogServiceTest.java` | tests 1–7 |
| `backend/.../controller/CourseProgramCatalogControllerTest.java` | tests 8–10 (real MockMvc) |
| `frontend/lib/api.ts` | `updateProgramFamily`; `createProgramFamily` gains `programIds` |
| `frontend/lib/api-program-families.test.ts` | pins the new request shapes (a component test that mocks `lib/api` proves nothing here — `v0.119.0`) |
| `frontend/app/admin/course-programs/page.tsx` | tab shell + `?view=` |
| `frontend/components/admin/admin-course-program-catalog-section.tsx` | drop inline family box; drop inline create grid → `+ New program` modal; `<select multiple>` → shared control; send `programFamilyIds` |
| `frontend/components/admin/admin-program-families-section.tsx` | **NEW** (§E) |
| `frontend/components/ui/catalog-multi-select.tsx` | **NEW** (§G) |
| `frontend/components/metadata/course-program-create-modal.tsx` | **NEW** (extraction, §H.3) |
| `frontend/components/metadata/applicable-programs-combobox.tsx` | delegates its create modal to the extraction; **family shortcut logic untouched** |

### CONDITIONAL

| File | When |
|---|---|
| `frontend/components/notes/note-detail-tabs.tsx` | only if the tab shell is generalised rather than re-implemented — prefer re-implementing two tabs locally over refactoring a note surface |
| `frontend/components/ui/checkbox.tsx` | only if the new control uses it rather than a native input; native is the recommendation |
| `backend/.../dto/ProgramFamilyResponse.java` | only if §E.1's client-side counting is rejected in review |
| `backend/.../exception/*` | only if a new error case emerges; the five existing types cover the contract |
| `frontend/components/admin/admin-course-program-catalog-section.test.tsx` | follows its component |
| `frontend/components/metadata/applicable-programs-combobox.test.tsx` | follows the extraction |

### NO CHANGE (asserted, and each is a thing a careless diff would touch)

`note_course_program` and every Note persistence path · `StudyPackGenerationContextResolver` and all of
`prompts/study-pack-v1/` · `DomainContext` · `LearnerLevel` / Authored Depth · Review Sets and
`docs/curriculum/*` · `ExamGoalConfig` and the exam-goal slug contract · `course_programs.is_active`
(read paths stay; **still no write path** — §23) · `course_programs.program_family_id` (vestigial;
**not written, not dropped**) · `AdminApplicableProgramsSection` · `suggestion-combobox.tsx` /
`course-program-combobox.tsx` / `subject-combobox.tsx` · `study-plan-builder-page-client.tsx` (the UX
*reference*, explicitly **not** a target — §G option C rejected partly to keep it out of the diff).

---

## P. Documentation

| Doc | Update |
|---|---|
| `docs/features/program-families.md` | **Primary.** Rewrite "Catalog growth": families are created **with** optional initial members, renamed in place, and membership is editable from **either** side. Correct the now-false sentence *"A family is created **empty**"* and the *"Membership is set on program creation or edited later from the Admin catalog row's Edit action"* clause. Document the family-first tab as the default. Add the identity-preserving rename contract. Record that V147 seeded 21 memberships and that 6 programs now hold two families. |
| `docs/features/admin-dashboard.md` | The `/admin/course-programs` destination now has two tabs, default Program Families; `+ New program` lives in Admin. |
| `docs/features/notes.md`, `docs/features/note-detail.md` | Only if the not-found copy for ordinary users changes what a curator sees — check both against **final** code, not against the PR that last edited them. |
| `docs/architecture/DATA_MODEL.md` | `:92` — note that `program_families.name` is mutable and that `uk_program_families_name` is on the raw name while the service checks a normalized form. `:82` — note `course_programs.program_family_id` is vestigial, unread by application code, and already drifted from the join table. |
| `docs/architecture/ADR-001-…md` | **NO CHANGE.** See §Q / the Decision Block. |
| `RELEASES.md` | Bullet per shipped item under v0.152.0, plus any Known Limitation. |
| `docs/releases/v0.152.0.md` | At signoff. |
| `docs/product/ROADMAP.md` | **(a)** a Backlog Index row for *this plan file* — kickoff step 8 requires every live `docs/claude-plans/` file to have one; **(b)** confirm the existing "Course / Program catalog lifecycle management" row still reads true after this release (it does — §Q) and re-anchor it; **(c)** mark shipped rows at signoff, per the `v0.140.0` gate. |
| `docs/gpt-contexts/GPT_CONTEXT.md` + `SURFACES_AND_FEATURES_CONTEXT.md` | Version-stamped snapshots naming Program Families; refresh at signoff. |

⚠️ **Sweep by surface, not by diff** (the rule that has cost three releases): this release changes what
"managing a family" *means*. Grep for prose describing the old program-centric flow — including
`docs/gpt-contexts/NOTES_AND_COLLECTIONS_CONTEXT.md` and any help/empty-state copy — whether or not
those files appear in `git diff`.

---

## Q. Scope exclusions / backlog (explicitly preserved)

Deferred, unchanged, and **not** to be opportunistically folded in:

1. **Family deletion** (§5, §27) — needs a membership-cascade product decision.
2. **Program deletion** (§23).
3. **`is_active` write path** (§23) — `docs/features/program-families.md` records the correction that
   *no application code anywhere writes `is_active`*; production has 0 inactive rows. Its existing
   Backlog row ("Course / Program catalog lifecycle management") stays open and accurate.
4. **Legacy fused programs** `Nursing · Medicine`, `Nursing · Pharmacy` (§16) — **preserve and defer.**
   They stay in the catalog, stay unassigned, and are **not** added to Health Sciences to tidy a blank.
5. **Dropping `course_programs.program_family_id`** — vestigial and stale, but a column drop is
   lifecycle work. Documented in §P, not executed.
6. **A `Business & Finance` family** (§15) — for Chartered Financial Analyst, Business Administration,
   Entrepreneurship, Economics, Senior High – ABM, Real Estate Management. Not created; §20 means the
   owner can create it from the UI when curation justifies it, with no migration.
7. **Real Estate Management → Built Environment & Design** (§15) — explicitly not yet.
8. **Psychology / Biology / Chemistry → Health Sciences** (§15) — explicitly not.
9. **A general Popover / Command primitive** (§G, §27) — the new control is a catalog picker, not a
   platform layer.
10. **Everything in §27's list** — drag/drop, ranking, colors, icons, nesting, descriptions, bulk
    spreadsheet editing, audit history, automatic inference, LLM suggestions, user-created families,
    Note family persistence, live sync to existing Notes.
11. **`allowCustom` free-text `courseProgram`** (§21 carve-out) — untouched.

**No new ADR, and no ADR amendment.** ADR-001's amended clause 2 is storage-neutral and already
ratifies many-to-many membership; its three binding constraints (expansion unconditional; membership
the only input; never at read time) are each untouched by a management view, a rename, or a backfill.
Rename in particular needs no amendment because the ADR nowhere treats a family name as identity, and
no family identifier is persisted on a Note at all (§I.1). This plan adds **no** curriculum rule, **no**
subset, **no** read-time resolution — the tripwire does not fire.

---

## R. Codex routing

### R.1 Routing: **CODEX.** Unambiguous under current `CLAUDE.md`.

Three of the table's rows fire independently: *new features touching backend* (new endpoint, new
migration, service logic); *multi-system changes* (frontend + backend together); *refactors or
additions touching > 5 files or > ~100 LOC* (~17 must-change files). The tiebreaker confirms it — this
requires `AGENTS.md` anti-drift rules applied across many files, not dropping a known component into a
known slot.

**Do not write the prompt yet** (per the task's instruction and §29's R). When it is written:
`docs/skills/codex-prompt-generator.md`, and **`advisor()` before the prompt is sent** — CLAUDE.md is
explicit that the highest-yield check is the pre-prompt one, and §J.2's fresh-database hazard is
exactly the class it catches.

### R.2 Coherent slices — owner-tightened to four, implementation slices not necessarily separate PRs

The subagent's original six were each individually valid, but the owner has directed consolidation to
reduce fragmentation. Mapped 1:1 onto the original six so nothing drops:

| Slice | Scope | Absorbs (original slice numbering) | Why it is coherent |
|---|---|---|---|
| **1 — Backend catalog contracts + data** | `POST /families` gains `programIds`; new `PATCH /families/{id}` (rename + family-side replace); repository additions; normalized-name predicate fix + rename self-exclusion (`id <> ?`); the `:47-54` Javadoc rewrite; the owner-locked name-vs-id invariant (§I) applied to every new method; V147 + `ProgramFamilyInitialMembershipMigrationTest`; tests 1–15a. | original 1 + 2 | One coherent "how the catalog is written and seeded" slice — a service/controller/DTO change and its accompanying migration are natural review companions, not two unrelated concerns. Ships behind no UI; the migration can still be evaluated on its own merits inside the same review. |
| **2 — Shared catalog selection + creation UX** | `CatalogMultiSelect` (with `selectedSummary`, §G); `CourseProgramCreateModal` extraction; both mounted from Admin and from the authorized Note-authoring consumers; Admin starts sending `programFamilyIds`; the ordinary-user not-found copy (§H); tests 16–19, 22. | original 3 + 4 | Both are "the shared frontend building blocks the Admin IA slice will assemble" — building the primitive and its first non-Admin consumer together keeps the multi-select from shipping with no real-world exercise before the Admin rework lands on top of it. |
| **3 — Family-first Admin** | Tab shell + `?view=`; `AdminProgramFamiliesSection` (default view); rework of `AdminCourseProgramCatalogSection` (`+ New program`, drop the inline boxes, consume slice 2's components); responsive + accessibility treatment (§M); tests 20–21. | original 5 | The visible release — depends on slices 1 and 2 existing first. |
| **4 — Verification + docs + signoff** | The elected cold agent (§R.3); §J.4a's production pair-set acceptance query; full regression suite; doc sweep (§P) re-read against **final** code; `RELEASES.md` / `docs/releases/v0.152.0.md` / `ROADMAP.md` signoff steps. | original 6, plus the verification/production-acceptance work newly made explicit by this tightening pass | Nothing here is safe to run until 1–3 are complete; keeping it a distinct slice matches the pre-signoff gates CLAUDE.md already requires. **⚠️ Not part of the Codex prompt** — the cold agent is a Claude Code dispatch and the production acceptance query is a Render MCP read only the session holding owner-level access can run; Codex cannot execute either and must not be asked to. The Codex prompt covers slices 1–3 plus the doc updates that describe finished behavior (§P); slice 4's verification/signoff work is performed by the session that receives Codex's diff. |

**Dependency order is unchanged even though the slice count shrank:** 1 → 2 → 3 → 4. Slices 1 and 2 are
each internally independent of the other (a data/backend concern and a frontend-primitive concern) and
could be parallelized if the implementer chooses, but 3 needs both, and 4 needs 3.

### R.3 Verification tier: **one scoped cold agent**, elected now rather than at signoff

The gate's trigger fires: **all three implementation slices (1, 2, 3) touch the shared catalog
create/membership path** — slice 1 writes it, slice 2 builds the shared control that mutates it, slice
3 assembles both into the surface curators actually use — which is CLAUDE.md's "two or more PRs touched
the same shared method" condition, if the four slices land as more than one PR. It is *not* a permission
substrate, a first-of-kind cross-user read, or a money/quota change, so the full three-agent test is
**not** warranted (that is one release in four or five, ~490k tokens).

Frame the cold agent as **FALSIFICATION**, `model: "sonnet"`, with a tight file list and these
specific claims to disprove:

1. "Family-side replace cannot evict a program from another family."
2. "Rename preserves `id`, every membership, and every note's applicability."
3. "V147 is additive, idempotent, and cannot fail a fresh-database Flyway run."
4. "V147 does not write `course_programs.program_family_id`."
5. "No ordinary user can create a shared catalog entry through any path."
6. "Creating a program with two families adds only that program to the note."
7. "The new multi-select's checkbox `checked` state is real, not the `AddNotesModal` list-membership hack."

Plus the `v0.119.0` heuristic, stated verbatim in the prompt: **enumerate every file the release ADDED
and name those with no test that executes them.**

Per-PR `/audit-diff` still runs on each slice. Run `scripts/check-deploys.sh` after the release PR
merges.

### R.4 Release-management preconditions

- **v0.152.0 must be kicked off first** — the 9-step checklist committed directly on
  `releases/v0.152.0` **before** any feature branch is cut. This plan is not implementable on
  `releases/v0.151.0`.
- **This plan file owes a `ROADMAP.md` Backlog Index row** (kickoff step 8 scans `docs/claude-plans/`).
  It is a live plan, not a release artifact, so the artifact exemption does not cover it.
- Nothing here ships ahead of its own evidence, so **no `[CHECKPOINT — due …]` row is owed**.
- **Owner has directed consolidation to four slices** (§R.2) specifically to avoid the fragmentation
  cost CLAUDE.md warns about; no further splitting is proposed. The release still carries real size —
  backend contract change, new migration, new shared component, an extraction touching four consumers,
  and an Admin IA rework — which is exactly what elects the scoped cold agent in §R.3 rather than the
  cheapest `advisor()`-only tier.

---

# PROGRAM FAMILY CATALOG MANAGEMENT — FINAL PLAN

**Program Family data model:**
MANY-TO-MANY / UNCHANGED

**Family-first Admin:**
YES

**Family is actual parent hierarchy:**
NO

**Admin default management perspective:**
PROGRAM FAMILIES

**Secondary perspective:**
COURSE / PROGRAMS

**Create family from Admin:**
YES

**Rename family:**
YES

**Edit family members:**
YES

**Create family with initial members:**
YES — atomic. `CourseProgramCatalogService.create()` already proves the shape for a program (insert row, then insert memberships, one `@Transactional`); the family side is the same transaction over the same join table. No disproportionate complexity, so §7's escape hatch is not taken. ⚠️ The service Javadoc at `CourseProgramCatalogService.java:47-54` currently forbids this in bold and must be rewritten, not worked around.

**Delete family:**
NO

**Create Course / Program from Admin:**
YES — and the backend already supports it atomically with N families. Frontend-only change: the Admin form sends the legacy single `programFamilyId` today while the note-authoring modal already sends `programFamilyIds`.

**Course / Program family assignment:**
ZERO / ONE / MANY

**Course / Program created by ordinary users:**
NO — verified, not assumed. `hasRole('ADMIN')` on `POST /course-program-catalog`, and `INSERT INTO course_programs` appears exactly once in the codebase, reachable only from that endpoint.

**Admin inline creation from Note authoring:**
PREFERRED REUSED FLOW — **Option B.** Extract `CourseProgramCreateModal` and mount it from both Admin and note authoring. Two divergent implementations exist today and the Admin one is the weaker; this collapses them into one component, one contract, one validation path.

**Raw HTML multi-select:**
NO — both `<select multiple>` instances removed (`admin-course-program-catalog-section.tsx:243-250`, `applicable-programs-combobox.tsx:382-391`).

**Note Collection Builder interaction:**
PRIMARY UX REFERENCE — resolved to `AddNotesModal`, `frontend/app/collections/[id]/builder/study-plan-builder-page-client.tsx:1083-1302`. ⚠️ It is a **modal with search + native checkboxes + a "Selected (N)" list — no chips, no popover**. The chips come from `ApplicableProgramsCombobox`. Recommendation is **NEW COMPONENT** adapting that interaction language: the repo has no Popover, no Command/cmdk, no MultiSelect and no Radix/shadcn/headlessui, and its only combobox is single-select. Extraction is rejected because `AddNotesModal` is module-local, note-typed, and depends on a load-bearing `checked={false}` hack.

**Program Family persisted on Note:**
NO

**Family membership change updates existing Notes:**
NO

**Domain Context coupling:**
NO

**Generation coupling:**
NO

**Family name used as durable identity:**
NO — proven at every layer: `course_program_family.program_family_id` is a UUID FK; every repository membership statement binds the id; no family identifier is persisted on a Note at all; the frontend keys shortcuts and chips on `family.id`; no family value reaches a prompt. The only name-keyed family statement is a duplicate-check lookup, never a join. **V142 already performed an identity-preserving rename in production, guarded by `EducationProgramFamilyMigrationTest`.** Two real defects to fix alongside rename: the family duplicate predicate (`lower(trim(name))`) is weaker than the program one (`regexp_replace` whitespace-collapsing), and rename needs `id <> ?` self-exclusion.

**Approved initial membership backfill:**
YES — `V147__program_family_initial_membership.sql` (next valid number, confirmed by numeric sort: highest existing is V146).

**Backfill behavior:**
ADDITIVE + IDEMPOTENT + NON-DESTRUCTIVE — exact-name inner joins over a 50-row `VALUES` matrix, `ON CONFLICT (course_program_id, program_family_id) DO NOTHING`, no `DELETE`, no `UPDATE`, no fuzzy matching, and **it does not write the vestigial `course_programs.program_family_id`** (V142 did; do not copy that). ⚠️ **It does NOT `RAISE EXCEPTION` on a missing canonical row**, because 4 of 6 families and ~35 of 62 programs exist only as production runtime data — a hard failure would break `NativeQueryPostgresIntegrationTest`'s PostgreSQL container and every fresh local Flyway run. The loud failure moves to `ProgramFamilyInitialMembershipMigrationTest`, which supplies the canonical rows as a fixture, replays V147's actual SQL read from the file, and asserts all 50 pairs.

**Manual assignment of every program required:**
NO — the migration inserts **21** missing rows on deploy (Health Sciences 5, Accounting 3, Computing & Technology 6, Built Environment & Design 7), taking `course_program_family` from 29 → 50 rows.

**Production audit (SELECT-only, 2026-09-16 — 62 programs, 6 families, 29 membership rows, ZERO extra/unexpected memberships):**

**Engineering:** current 18 / intended 18 / missing 0 / extra 0
- Civil Engineering ✅ · Electrical Engineering ✅ · Mechanical Engineering ✅ · Electronics Engineering ✅ · Computer Engineering ✅ · Industrial Engineering ✅ · Chemical Engineering ✅ · Agricultural and Biosystems Engineering ✅ · Geodetic Engineering ✅ · Mining Engineering ✅ · Sanitary Engineering ✅ · Marine Engineering ✅ · Geotechnical Engineer ✅ · Architectural Engineering ✅ · Geological Engineering ✅ · Aeronautical Engineering ✅ · Environmental Engineering ✅ · Construction Engineering and Management ✅

**Education:** current 8 / intended 8 / missing 0 / extra 0
- Education ✅ · Special Needs Education ✅ · Elementary Education ✅ · Secondary Education ✅ · Early Childhood Education ✅ · Technical-Vocational Teacher Education ✅ · Physical Education ✅ · Teacher Certification ✅

**Health Sciences:** current 0 / intended 5 / **missing 5** / extra 0
- Nursing ➕ · Medicine ➕ · Pharmacy ➕ · Physical Therapy ➕ · Radiologic Technology ➕

**Accounting:** current 2 / intended 5 / **missing 3** / extra 0
- Accountancy ✅ · Accounting Information Systems ✅ · Management Accounting ➕ · Internal Auditing ➕ · Certified Management Accountant ➕

**Computing & Technology:** current 0 / intended 6 / **missing 6** / extra 0
- Information Technology ➕ · Computer Science ➕ · Software Engineering ➕ · Computer Engineering ➕ · Electronics Engineering ➕ · Accounting Information Systems ➕

**Built Environment & Design:** current 1 / intended 8 / **missing 7** / extra 0
- Architecture ✅ · Interior Design ➕ · Landscape Architecture ➕ · Urban and Regional Planning ➕ · Environmental Planning ➕ · Architectural Engineering ➕ · Geodetic Engineering ➕ · Construction Engineering and Management ➕

**Unassigned programs allowed:**
YES — 18 remain unassigned after the backfill, reconciling exactly to §16's list plus the two legacy fused rows (62 = 44 assigned + 18 unassigned).

**Legacy fused programs:**
PRESERVE / DEFER — `Nursing · Medicine` and `Nursing · Pharmacy` stay in the catalog, stay unassigned, and are **not** added to Health Sciences.

**Family deletion:**
DEFER

**Catalog deactivate/reactivate:**
DEFER — verified **not** independently shipped. `course_programs.is_active` exists (V145) with four read paths and **no write path anywhere in application code**; production holds 0 inactive rows. Its existing Backlog Index row stays open.

**ADR amendment required:**
NO — ADR-001's amended clause 2 is storage-neutral and already ratifies many-to-many; its three binding constraints are untouched by a management view, a rename, or a backfill. No new ADR either.

**Codex routing:**
CODEX — four owner-consolidated slices (1: backend catalog contracts + V147 data · 2: shared CatalogMultiSelect + CourseProgramCreateModal extraction · 3: family-first Admin IA · 4: verification + production acceptance + docs). Verification tier: **one scoped cold agent framed as falsification**, triggered by all three implementation slices touching the shared catalog create/membership path. Prompt written this pass — `docs/codex-prompts/v0.152.0-program-family-catalog-management.md`.

**Owner-tightened additions locked into this plan (this pass):**
- Family-side editing is the PRIMARY bulk-membership workflow; program-side editing is the INVERSE convenience workflow. No competing visual prominence — Program Families stays the default tab and the page's primary affordance.
- `CatalogMultiSelect` carries a `selectedSummary: "count" | "chips"` prop — one component, two densities. Family → Programs (5–18+ members) uses `"count"` (Selected N / Clear all, checked rows remain primary); Program → Families (0–2 members) keeps `"chips"`.
- Ordinary-user not-found copy is `"Can't find your program? You can still continue without selecting one."` — not "ask an admin," which exposes internal governance to an audience with no admin relationship. No new free-text mechanism created; a pre-existing free-text `CourseProgramCombobox` path was confirmed to already exist on the same forms, gated separately.
- New anti-drift invariant: **"Program Family name is display data. Program Family ID is identity."** V147's exact-name matching is an explicitly scoped exception (runtime-generated UUIDs with no portable literal) and must not be copied into any application code.
- Production acceptance is an **anti-join against the approved 50-pair matrix** (0 missing pairs = the primary proof), not a family-count check — a count can be right for the wrong reason. An extras report is generated separately and is informational only; "extras must be zero" is never encoded as migration behavior.
- Six original slices consolidated to four per owner direction, to bound verification/fragmentation cost; dependency order preserved (1 → 2 → 3 → 4).

**Core doctrine:**

> Program Families and Course / Programs are peer catalog entities connected by many-to-many membership.

> Family-first is a management perspective, not a hierarchy in the data model.

> Admin defines the canonical catalog. Note authoring consumes it.

> Program Family membership is curator convenience; individual Course / Program IDs remain the Note's truth.

> A family rename preserves identity and membership.

> The initial membership migration ensures approved memberships exist without deleting valid curator work already present.

> Course / Program answers WHO can study the knowledge. Program Family answers WHICH related programs a curator can conveniently work with together. Domain Context answers HOW the knowledge is treated. Authored Depth answers AT WHAT DEPTH it is written. Review Set answers WHERE it belongs in the learning journey.

**DO NOT IMPLEMENT YET.**
