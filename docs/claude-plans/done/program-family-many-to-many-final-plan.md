Status: FINAL, OWNER-TIGHTENED, APPROVED FOR CODEX HANDOFF — 2026-09-15. Owner has approved the
many-to-many architecture and the ADR-001 amendment (storage-neutral text below), and tightened
scope: Exam Goal editing/display dropped entirely from this release; migration parity strengthened to
relationship-level (not count-only); the legacy scalar FK is explicitly non-authoritative and never
dual-written after cutover; deprecated response scalars are explicitly non-primary compatibility
projections; all four existing families (Engineering, Education, Health Sciences, Accounting,
**plus the owner-approved Computing & Technology and Built Environment & Design**) get populated in
this release. Table name re-confirmed as `course_program_family` against repo convention (no
`_memberships`/`_relationship` suffix precedent exists for a plain two-FK join table — see §E). The
Codex prompt is at `docs/codex-prompts/v0.150.0-program-family-many-to-many.md`. **Remaining
prerequisite, not executed by this planning session: the owner-ratified ADR-001 diff must actually
land in the repo, and a `v0.150.0` kickoff must happen, both before any Codex slice runs — see the
chat response for exactly who does what.**

# Program Family Many-to-Many + Shared Catalog UX — Final Implementation Plan

**DO NOT IMPLEMENT YET.** Planning artifact only. No code was edited, no production write was run,
nothing was committed. Every production figure below came from a `SELECT` against
`dpg-d6tvb8fkijhs73fda4m0-a` on 2026-09-15.

**Supersedes** `docs/claude-plans/program-family-health-accounting-expansion-final-plan.md` (pass 2)
and `...-product-ux-consultation-prompt.md` (pass 1) on the schema question only. Both are historical
trace; §"Corrections to the prior plans" below lists every claim of theirs this audit falsified.

---

## A. Executive verdict

### **APPROVE — with one mandatory, non-negotiable prerequisite.**

Many-to-many Program Family membership is the correct architecture, and the repository contains no
simpler existing abstraction to reuse. Evidence:

1. **The single FK is genuinely load-bearing and genuinely wrong for the use case.**
   `course_programs.program_family_id` is a nullable single FK
   (`backend/src/main/resources/db/migration/V106__course_program_catalog.sql:1-25`), and every read
   path reaches it through a `LEFT JOIN program_families` that can only ever produce one row
   (`CourseProgramCatalogRepository.java:19-29`, `:30-40`, `:52-63`, `:64-77`). There is no
   membership table, no array column, no tag/label abstraction anywhere in the schema to borrow.
   I enumerated every `CREATE TABLE` in `backend/src/main/resources/db/migration/` — the only pure
   two-FK join table in the repo is `note_course_program`
   (`V107__note_course_program.sql:1-11`), which is the naming precedent used in §E, not a reusable
   mechanism.

2. **The overlap use cases are not hypothetical — their first members already exist and are already
   heavily used.** `Architectural Engineering` (474 `note_course_program` rows) and
   `Computer Engineering` (182 rows) are both currently members of `Engineering`, and both are §1's
   own named overlap examples. The owner has *already created* `Computing & Technology` and
   `Built Environment & Design` in production (see §D). Under the single FK, adding either program to
   its second family would **silently remove it from Engineering** — a destructive `UPDATE ... SET
   program_family_id` with no warning, taking 474 and 182 notes' worth of curator convenience with
   it. That is the concrete failure the migration prevents.

3. **Nothing outside authoring reads family identity, so the blast radius is genuinely contained.**
   A repo-wide grep for `program_family_id` / `programFamilyId` / `ProgramFamily` across
   `backend/src/main/java/` returns hits in exactly three files —
   `CourseProgramCatalogRepository`, `CourseProgramCatalogService`,
   `CourseProgramCatalogController` — plus their DTOs and exceptions. No generation code, no
   discovery code, no Note DTO. See §34 Q15–Q18.

### The prerequisite: ADR-001 must be amended in the same release, by the owner.

`docs/architecture/ADR-001-canonical-knowledge-architecture.md:92` is **Accepted, owner-ratified
(2026-08-05), and binding**, and it says in its own words:

> 2. **A family expands to all of its members.** No curated per-family subsets, **no preset table
>    beyond `course_programs.program_family_id`**. If a family should expand to a different set, that
>    is a change to *membership*, not to expansion logic.

A membership join table is, on the literal text, "a preset table beyond
`course_programs.program_family_id`." CLAUDE.md states an ADR outranks a feature doc; it equally
outranks a plan. **This release cannot ship by quietly reinterpreting that clause.**

The substance of constraint 2 survives completely intact — this is a storage clarification, not a
reversal. The clause's own rejected alternatives (subject-conditioned expansion, curated subset
expansion, named in the same ADR section at `:97`) stay rejected, and its final sentence — *"that is
a change to membership, not to expansion logic"* — is precisely what this change implements: it makes
membership expressible, and touches expansion logic not at all.

**OWNER-APPROVED, storage-neutral (2026-09-15) — final text for `ADR-001:92`:**

> 2. **A family expands to all of its members.** No curated per-family subsets and no expansion
>    presets: membership is the *only* input to expansion. Membership is many-to-many — a
>    Course / Program may participate in zero, one, or several families. If a family should expand to
>    a different set, that is a change to *membership*, not to expansion logic.

This is the owner's own wording, deliberately naming no physical table. The ADR states the
architectural invariant (family expansion is unconditional and membership-driven, and membership may
be many-to-many); *which* table implements that lives in `docs/architecture/DATA_MODEL.md` and this
plan, not in the ADR — so a future storage change (e.g. Codex or a later session preferring
`course_program_program_family` over `course_program_family`, §E) never requires touching ADR-001
again. The ADR's Status line (`:3`) gains `amended 2026-09-15 to make Program Family membership
many-to-many`. `docs/features/program-families.md` and `DATA_MODEL.md` follow the ADR, not the other
way round.

**Owner ratification is this message** ("This owner message constitutes approval of the architectural
change"). What remains is procedural, not a decision: the diff above must actually be **committed** to
`ADR-001.md` before any Codex slice runs. Per CLAUDE.md, Claude does not commit automatically — this
plan does not edit the ADR file itself; it hands the owner-approved diff to whoever executes slice 0
(§Q), on the `releases/v0.150.0` branch, after the version is kicked off.

---

## B. Current architecture map

```
DB           course_programs(id, name, program_family_id NULL FK, exam_goal_slug, created_at,
                              is_active NOT NULL DEFAULT TRUE)          V106, V145
             program_families(id, name UNIQUE, created_at)              V106 (+V142 data)
             note_course_program(note_id, course_program_id) UNIQUE      V107  ← applicability truth
                ⚠️ SINGLE-FAMILY ASSUMPTION LIVES ENTIRELY IN ONE COLUMN

Entities     NONE. There is no CourseProgramEntity and no ProgramFamilyEntity. This is a plain
             JdbcTemplate repository with hand-written SQL constants. Confirmed by
             `find backend/src/main/java -name "CourseProgram*.java" -o -name "ProgramFamily*.java"`
             — only controller/dto/exception/repository/service/util files exist.

Repository   CourseProgramCatalogRepository.java — 5 statements carry the single-family shape:
               FIND_ALL                    :19-29   LEFT JOIN, one family column pair
               FIND_BY_ID                  :30-40   same
               FIND_BY_NORMALIZED_NAME     :52-63   same
               FIND_SIMILAR                :64-77   same
               INSERT                      :83-86   INSERT ... program_family_id (single value)
               UPDATE_PROGRAM_FAMILY       :103-107 SET program_family_id = ? (single value)
               mapCatalogItem              :214-223 reads ONE program_family_id + ONE name
               FIND_ALL_PROGRAM_FAMILIES   :87-91   ← the canonical family list (no member data)

Service      CourseProgramCatalogService.java
               list()                      :36-38
               listProgramFamilies()       :40-42
               createProgramFamily()       :56-77   creates EMPTY by design (:52-54 comment)
               updateProgramFamily()       :87-103  single id, null clears  ← SINGLE-FAMILY
               create()                    :105-134 single request.programFamilyId()  ← SINGLE-FAMILY

API          CourseProgramCatalogController.java
               GET    /course-program-catalog            USER+ADMIN   :35-39
               GET    /course-program-catalog/similar    ADMIN        :41-45
               POST   /course-program-catalog            ADMIN        :47-51
               PATCH  /course-program-catalog/{id}       ADMIN        :53-61  ← NO FRONTEND CLIENT
               GET    /course-program-catalog/families   ADMIN        :66-70  ← canonical family list
               POST   /course-program-catalog/families   ADMIN        :72-76

DTOs         CourseProgramCatalogItemResponse(id, name, programFamilyId, programFamilyName, isActive)
             CreateCourseProgramCatalogRequest(name, programFamilyId, examGoalSlug)
             UpdateCourseProgramCatalogRequest(programFamilyId)          ← single UUID
             ProgramFamilyResponse(id, name)                             ← no member ids, no count

Frontend     NO react-query / TanStack. Confirmed: `grep "react-query\|tanstack" frontend/package.json`
             returns nothing. Every surface does useState + useEffect fetch-on-mount.
             lib/api.ts:1825-1841  types (scalar programFamilyId/Name)
                       :5848-5858  getCourseProgramCatalog()
                       :5860-5878  parseCourseProgramCatalogItem()  ← HARD-THROWS on missing scalars
                       :5889-5896  listProgramFamilies()
                       :5898-5908  createProgramFamily()
                       :5910-5924  createCourseProgram()
                       (there is NO updateCourseProgram — PATCH has zero clients)

Admin        app/admin/course-programs/page.tsx        redirect-only guard (:16-18)
             admin-course-program-catalog-section.tsx
               :43-46   loads catalog AND families in parallel  ← CORRECT, sees all 6
               :154-157 family <select>, single-select, fed by the FETCHED families
               :207-217 table: Course / Program | Family — READ-ONLY, no Edit action
             admin-applicable-programs-section.tsx:206-213  ApplicableProgramsCombobox, canCreate bare

Note Create  applicable-programs-combobox.tsx — ONE shared component, FOUR consumers:
               admin-applicable-programs-section.tsx:211   canCreateCatalogProgram (bare true)
               bulk-generation-page-client.tsx:599         canCreateCatalogProgram={isAdmin}
               private-note-detail-page-client.tsx:2780    canCreateCatalogProgram={userRole==="ADMIN"}
               note-editor-page-client.tsx:1421            canCreateCatalogProgram={role==="ADMIN"}
             Inside it:
               :89-113  availableProgramFamilies  ← EXPANSION CHIPS, derived from catalog membership
               :119-128 programFamilies           ← CREATE-MODAL PICKER, derived from catalog ← THE BUG
               :218-228 handleFamilyExpansion     ← already a set union, already dedupes
               :364-379 the "Add Course / Program" modal's single-select Program Family dropdown

Bulk Create  bulk-generation-page-client.tsx:213-231 fetches the SAME getCourseProgramCatalog(),
             renders the SAME ApplicableProgramsCombobox. It shares the data contract already.
```

**Every single-family assumption, exhaustively:** one DB column; five repository SQL constants plus
one row mapper; two service methods (`create`, `updateProgramFamily`); two DTO fields and one DTO
request record; two frontend type fields; one response parser; two frontend `useMemo` derivations;
two `<select>` elements. That is the whole list.

---

## C. Root cause of missing families in Note Create

### Classification: **DIFFERENT API.**

Mechanism: the two surfaces read family identity from **two different endpoints with two different
shapes**, and the authoring one is a derived projection that *structurally cannot represent a family
with zero members*.

**The evidence, exactly.**

Admin reads the canonical list, and works:

```
admin-course-program-catalog-section.tsx:43-48
  const [loadedCatalog, loadedFamilies] = await Promise.all([
    getCourseProgramCatalog(),      // GET /course-program-catalog
    listProgramFamilies(),          // GET /course-program-catalog/families  ← canonical
  ]);
```

The Note-authoring modal never calls that endpoint. It rebuilds a family list by scanning catalog
rows that already *have* a family:

```
applicable-programs-combobox.tsx:119-128
  const programFamilies = useMemo(() => {
    const families = new Map<string, string>();
    mergedCatalog.forEach((program) => {
      if (program.programFamilyId && program.programFamilyName) {   // ← the filter that hides them
        families.set(program.programFamilyId, program.programFamilyName);
      }
    });
    ...
  }, [mergedCatalog]);
```

A family with zero members contributes zero catalog rows carrying its id, so it contributes zero
entries to that `Map`. Production has exactly that state: Health Sciences, Accounting,
Computing & Technology and Built Environment & Design all have `member_count = 0` (§D). Engineering
(18) and Education (8) are the only two families with members — **and they are exactly the two the
screenshot showed.** The observed output is the predicted output of this code against this data, with
no residual to explain.

**Ruling out the other listed causes, each with evidence:**

| Candidate | Ruled out by |
|---|---|
| HARDCODED | No family-name literal exists in any frontend file. Greps for `"Engineering"` / `"Education"` as family constants return nothing in `applicable-programs-combobox.tsx`. `V142`'s own header comment (`:5-8`) records that the combobox was made dynamic deliberately. |
| STALE CACHE | There is **no cache to be stale**. `frontend/package.json` has no react-query/TanStack; every surface fetches on mount via `useEffect`. `fetchWithAuth`'s third argument is the 401-refresh-retry flag (`lib/api.ts:2535-2547`), not a cache flag. A full page reload would not have helped, and the owner reports it did not. |
| DUPLICATED QUERY | Partly true but not causal — both surfaces *do* call `getCourseProgramCatalog()`. That call returns identical, correct data to both. The divergence is what each surface does with it afterwards. |
| LEGACY ENUM | No enum. `ProgramFamily` is a two-field type (`lib/api.ts:1833-1836`) populated from the API. |

**Why this was not caught:** it was *reasoned about and written down as correct*.
`docs/features/program-families.md` carries a ⚠️ paragraph ratifying the asymmetry — *"The admin
family picker reads the families endpoint; the authoring combobox derives families from the catalog.
Both are correct and the difference is deliberate"* — mirrored verbatim in code comments at
`lib/api.ts:5885-5888` and `admin-course-program-catalog-section.tsx:30-33`. The reasoning is sound
for the **expansion chips** and was silently over-applied to the **create-modal picker**, which is a
different control with the opposite requirement. §H/§I fix the code; §N fixes the doc, because the
doc is currently ratifying the defect.

---

## D. Current production family inventory

Read 2026-09-15, `SELECT` only.

| Family | id | Created | Members | Member programs |
|---|---|---|---|---|
| Engineering | `10000000-0000-0000-0000-000000000001` | 2026-08-04 | **18** | Aeronautical, Agricultural and Biosystems, Architectural, Chemical, Civil, Computer, Construction Eng. and Management, Electrical, Electronics, Environmental, Geodetic, Geological, Geotechnical Engineer, Industrial, Marine, Mechanical, Mining, Sanitary Engineering |
| Education | `10000000-0000-0000-0000-000000000002` | 2026-09-08 | **8** | Early Childhood Education, Education, Elementary Education, Physical Education, Secondary Education, Special Needs Education, Teacher Certification, Technical-Vocational Teacher Education |
| **Health Sciences** | `ee64905a-878a-46d4-ab67-71e1989a7b3b` | 2026-09-15 13:42:26Z | **0** | *(empty)* |
| **Accounting** | `c55a73a3-ba70-4187-95aa-8fa36d6f5d9f` | 2026-09-15 13:42:33Z | **0** | *(empty)* |
| **Computing & Technology** | `aca40dcb-cc6e-4726-b7f4-43916b273d37` | 2026-09-15 13:42:39Z | **0** | *(empty)* |
| **Built Environment & Design** | `00f6c913-ad5c-4b4d-beee-7914c7579d7e` | 2026-09-15 13:42:44Z | **0** | *(empty)* |

Catalog totals: **62** `course_programs`, **26** with a family, **36** without, **6** families,
**0** rows with `is_active = false`, **12,271** `note_course_program` rows. `V142` and `V145` both
recorded `success` in `flyway_schema_history`.

### ⚠️ Three findings in this table that the task specification did not anticipate

1. **There are SIX families, not four.** The owner also created **Computing & Technology** and
   **Built Environment & Design** — the two families §20 of the specification explicitly says
   *"DO NOT automatically create."* They already exist. Nothing in this plan creates them, and this
   is reported rather than acted on. **The owner should confirm whether to populate them in the §M
   data operation or leave them empty.** They are not blockers either way; an empty family is
   harmless once §I lands (it becomes pickable when creating a program, and correctly renders no
   expansion chip). Their existence is also the strongest available argument *for* many-to-many:
   their natural first members (Computer Engineering, Architectural Engineering) are already in
   Engineering.
2. **All four new families are empty — creating a family does not assign members.** Exactly as the
   specification suspected and as `CourseProgramCatalogService:52-54` documents by design.
3. **Both legacy fused rows are still `is_active = true`.** `Nursing · Medicine` (22 notes) and
   `Nursing · Pharmacy` (1 note) were to be retired in the `v0.149.0` follow-up. They were not — and
   §"Corrections" explains that they *could* not have been.

Intended-membership programs all exist and all resolve to durable ids:

| Program | id | `note_course_program` rows |
|---|---|---|
| Nursing | `20000000-0000-0000-0000-000000000003` | 465 |
| Medicine | `20000000-0000-0000-0000-000000000014` | 19 |
| Pharmacy | `20000000-0000-0000-0000-000000000007` | 30 |
| Accountancy | `20000000-0000-0000-0000-000000000004` | 189 |
| Management Accounting | `e8dcff30-58f7-449d-9713-d79088c031ad` | 35 |
| Accounting Information Systems | `879eba8f-ddfa-44dd-a457-ccfe4cdcbd2a` | 35 |
| Internal Auditing | `b64bb7d7-d8f2-494a-bab5-64dff0561b49` | 35 |

---

## E. Final target data model

### Table name — derived from convention, not asserted

The repo's only pure two-FK many-to-many join table is `note_course_program`
(`V107__note_course_program.sql:1-11`), whose convention is **`<left singular>_<right singular>`,
surrogate `id UUID PRIMARY KEY`, a `uk_` unique pair constraint, `fk_` foreign keys, `idx_` indexes,
`created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()`**. (`note_collection_items` is the only other
candidate and is a positional/ordered container, not a plain membership set — a weaker precedent.)

Applied strictly, `course_programs` × `program_families` yields
`course_program_program_family`, which stutters. **Recommended name: `course_program_family`** — the
same convention with the shared `program` token elided once. It is unambiguous against the existing
`course_programs` and `program_families` tables, and reads as "the course-program/family link."

**Owner's suggested alternative, checked against convention:** `course_program_family_memberships`.
A repo-wide search for a `_memberships`/`_relationship*` suffix on a plain two-FK join table finds
**no precedent** — every hit (`V120`, `V122`, `V125`, `V127`–`V130`) belongs to the `linked_learner_*`
feature, a materially different kind of table (a stateful relationship with an expiry, grants, and a
status lifecycle — not a plain membership set). The one existing plain join table
(`note_course_program`) carries no suffix at all. **Recommendation: `course_program_family`,
unsuffixed, matching the only real precedent.** This was an explicit "not a hard requirement, compare
against convention" ask (§2 of the owner's tightening pass) — convention favors the shorter name, so
that's what's used consistently below and in the Codex prompt. If Codex or a later session still
prefers the suffixed form, nothing else in the plan depends on the choice — rename search-and-replace
only touches this table's own definition, not the architecture.

### Migration `V146__course_program_family_membership.sql` (expand step — additive only)

```sql
CREATE TABLE course_program_family (
    id UUID PRIMARY KEY,
    course_program_id UUID NOT NULL,
    program_family_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_course_program_family_program_family
        UNIQUE (course_program_id, program_family_id),
    CONSTRAINT fk_course_program_family_course_program
        FOREIGN KEY (course_program_id) REFERENCES course_programs (id) ON DELETE CASCADE,
    CONSTRAINT fk_course_program_family_program_family
        FOREIGN KEY (program_family_id) REFERENCES program_families (id) ON DELETE CASCADE
);

CREATE INDEX idx_course_program_family_program_id
    ON course_program_family (course_program_id);
CREATE INDEX idx_course_program_family_family_id
    ON course_program_family (program_family_id);

-- Copy every existing single-FK membership. gen_random_uuid() is pgcrypto/PG13+ builtin; V106 relies
-- on no extension, so if the harness objects, generate ids in a DO block instead.
INSERT INTO course_program_family (id, course_program_id, program_family_id)
SELECT gen_random_uuid(), id, program_family_id
FROM course_programs
WHERE program_family_id IS NOT NULL
ON CONFLICT (course_program_id, program_family_id) DO NOTHING;

-- ⚠️ PARITY IS ASSERTED AT THE RELATIONSHIP LEVEL, NOT BY COUNT ALONE (owner tightening, §5). A count
-- match cannot detect a row copied against the WRONG family id while a different row went missing —
-- two errors that cancel out in a COUNT but not in an EXISTS check per legacy relationship. This is
-- the one irreplaceable membership relationship in the release; V106's own DO block established the
-- pattern of making a silent partial write impossible (see its trailing NOTICE).
DO $$
DECLARE
    source_rows  INTEGER;
    copied_rows  INTEGER;
    missing_rows INTEGER;
BEGIN
    SELECT count(*) INTO source_rows FROM course_programs WHERE program_family_id IS NOT NULL;
    SELECT count(*) INTO copied_rows FROM course_program_family;

    -- The load-bearing check: for every legacy (program, family) pair, does that EXACT pair exist in
    -- the new table? Not "does some row for this program exist" — the specific relationship.
    SELECT count(*) INTO missing_rows
    FROM course_programs cp
    WHERE cp.program_family_id IS NOT NULL
      AND NOT EXISTS (
          SELECT 1 FROM course_program_family m
          WHERE m.course_program_id = cp.id
            AND m.program_family_id = cp.program_family_id
      );

    RAISE NOTICE 'V146: % single-FK memberships, % membership rows, % unmatched relationships',
        source_rows, copied_rows, missing_rows;

    IF missing_rows > 0 THEN
        RAISE EXCEPTION
            'V146 relationship-parity failure: % legacy (course_program_id, program_family_id) pairs have no matching membership row',
            missing_rows;
    END IF;
    IF copied_rows <> source_rows THEN
        RAISE EXCEPTION 'V146 count-parity failure: expected % membership rows, found %',
            source_rows, copied_rows;
    END IF;
END $$;
```

**Expected against Engineering (18) + Education (8) = 26 legacy relationships: `missing_rows = 0`,
`copied_rows = 26`.** The relationship check (`missing_rows`) is the load-bearing invariant per the
owner's tightening; the count check stays as an additional, cheap sanity check, not the primary proof.

Expected at deploy time against today's data: **26** source rows → **26** membership rows
(Engineering 18 + Education 8). A `RAISE EXCEPTION` inside a Flyway migration aborts the migration
and fails the deploy — which is the correct outcome, because a partial membership copy must never
reach a running application.

**`course_programs.program_family_id` is NOT dropped in this migration.** It stays, unread by
application code after the switch, until the contract step in §F.

### Authoritative storage after cutover — LOCKED, owner tightening §3

**Once step 4 of §F ships (application reads/writes membership), `course_program_family` is the
single source of truth. `course_programs.program_family_id` becomes a frozen migration/rollback
artifact — read by nothing, and, critically, `written by nothing`.**

**⚠️ No dual-write, stated explicitly so a Codex implementation cannot read "compatibility" as
license to write both representations.** `CourseProgramCatalogService.create()` and
`updateProgramFamily()` (renamed per §G to accept a set) write **only** `course_program_family` rows
after cutover. They must not also issue `UPDATE course_programs SET program_family_id = ...` — doing
so would silently re-introduce the single-family assumption (which of a program's several families
would even go in that column?) and would make the legacy column lie the moment a program gets a
second family. The reason this needs stating this bluntly: Computer Engineering, once it has both
Engineering and Computing & Technology memberships, has no single correct value for the legacy scalar
— a dual-write is not merely redundant, it is **impossible to do correctly** for any multi-family
program, which is exactly why §G's deprecated response *fields* are a read-time derivation, never a
write-time one.

### Note-side model: **unchanged, deliberately**

`note_course_program` is untouched. No `program_family_id`, no `program_family_ids`, no provenance,
no snapshot, no selection history is added to `notes` or to `note_course_program`. ADR-001's
constraint 3 (`:93`) and its "explicit rows are always the source of truth" ruling (`:95`) are
unaffected by this change and remain binding.

---

## F. Migration / deployment plan

**The deployment constraint that decides the shape.** CLAUDE.md is explicit that the Vercel frontend
and Render backend deploy from the same push through **two independent integrations** and *"can and
do diverge"* — `v0.136.0` ran a new backend against an old frontend in production and the feature
shipped dead. Therefore: **expand → migrate → contract, with an additive response DTO**, not a
single cutover.

The specific trap, which is real and not theoretical:
`frontend/lib/api.ts:5860-5878` `parseCourseProgramCatalogItem` **throws** when `programFamilyId` or
`programFamilyName` is absent from the payload (`!("programFamilyId" in payload)` and
`!("programFamilyName" in payload)`). It runs on every `createCourseProgram` response. If a
list-shaped backend deploys first and drops the scalars, **every catalog-create call from the
currently-deployed frontend fails outright**, and `getCourseProgramCatalog` — which goes through
generic `parseApiResponse` and would *not* throw — degrades silently instead, with family chips
simply vanishing. Both halves of that are unacceptable.

### Ordering

| # | Step | Ships in | Safe alone? |
|---|---|---|---|
| 1 | **ADR-001 amendment ratified by the owner** | docs commit on the release branch | — prerequisite |
| 2 | `V146` — create `course_program_family`, copy 26 rows, assert parity | backend | ✅ purely additive; nothing reads it yet |
| 3 | **Backend reads/writes membership; response is ADDITIVE** — adds `programFamilies: [{id,name}]` **and keeps `programFamilyId`/`programFamilyName` populated from the first membership by name order** | backend | ✅ old frontend sees an unchanged-shape payload |
| 4 | Frontend switches to `programFamilies[]`; picker calls `/families`; Admin gains per-row Edit | frontend | ✅ new frontend against old backend degrades to at most one family per program, and never throws — the scalars it stops reading are still sent |
| 5 | **Owner data operation** — populate Health Sciences + Accounting (§M) | manual, post-deploy | ✅ pure data |
| 6 | **Contract, a LATER release** — drop the deprecated scalars from the DTO, then `ALTER TABLE course_programs DROP COLUMN program_family_id` | separate release | ✅ only after step 4 is confirmed live on both platforms |

Steps 2–4 ship in one release and one PR; they are ordered *within* the release so that neither
deploy order breaks. **Explicit deploy-ordering statement, as CLAUDE.md requires:** *backend-first
and frontend-first are both safe for this release, because the response is additive in step 3 and the
only removed request shape (`UpdateCourseProgramCatalogRequest`) has zero existing clients (verified:
no `updateCourseProgram` exists anywhere in `frontend/lib/api.ts`). The contract step in the later
release is **not** order-safe and must state its own ordering when it is scoped.*

**Verification after step 2** (owner runs; read-only, safe to run any time):

```sql
-- Expect: single_fk = 26, membership = 26, mismatches = 0
SELECT (SELECT count(*) FROM course_programs WHERE program_family_id IS NOT NULL) AS single_fk,
       (SELECT count(*) FROM course_program_family)                              AS membership,
       (SELECT count(*) FROM course_programs cp
         WHERE cp.program_family_id IS NOT NULL
           AND NOT EXISTS (SELECT 1 FROM course_program_family m
                            WHERE m.course_program_id = cp.id
                              AND m.program_family_id = cp.program_family_id))   AS mismatches;

-- Expect: Engineering 18, Education 8, the other four 0
SELECT pf.name, count(m.id) AS members
FROM program_families pf
LEFT JOIN course_program_family m ON m.program_family_id = pf.id
GROUP BY pf.name ORDER BY pf.name;
```

**Rollback.** Step 2 is additive and reversible by `DROP TABLE course_program_family` with zero data
loss, because `program_family_id` still holds the original truth throughout steps 2–5. Step 3/4 roll
back by redeploying the prior commit — the scalars are still being written in step 3, so a rollback
loses only memberships *created after* the switch (recoverable from the membership table, which the
rollback does not drop). **There is no window in which Engineering or Education appears empty:** the
old code reads the untouched column and the new code reads a table proven at parity before the
application ever starts. Step 6 is the first irreversible step and is deliberately a different
release.

---

## G. API contract

### `GET /course-program-catalog` — ADDITIVE change, auth unchanged (USER+ADMIN)

```jsonc
// CourseProgramCatalogItemResponse
{
  "id": "…", "name": "Computer Engineering",
  "programFamilies": [ {"id": "…", "name": "Computing & Technology"},
                       {"id": "…", "name": "Engineering"} ],   // NEW — sorted by name, may be []
  "programFamilyId":   "…",                       // DEPRECATED: programFamilies[0].id   or null
  "programFamilyName": "Computing & Technology",  // DEPRECATED: programFamilies[0].name or null
  "isActive": true
}
```

**⚠️ Owner tightening §4 — stated explicitly so this is never read as a "primary family" concept.**
`programFamilyId`/`programFamilyName` are **deprecated compatibility projections for old frontend
clients only.** They are computed **read-time**, from `programFamilies[0]` (alphabetical — the
existing sort order the repository already produces, no new ordering rule needed), sourced from the
authoritative membership table, never from the frozen legacy `program_family_id` column. This
alphabetical-first value is **not** a primary, preferred, canonical, or first-class family in any
product sense — it exists only so a not-yet-updated frontend build doesn't crash mid-rollout, and it
is deleted in the contract release (§F step 6) along with the column it never actually reads from.
Corrected the worked example above to be internally consistent: for Computer Engineering (member of
Computing & Technology + Engineering), alphabetical-first is *Computing & Technology*, not
*Engineering* — the original draft's example value was wrong about its own stated rule.

Response stays deliberately **unfiltered** by `is_active` — `docs/features/program-families.md`
ratifies that and it is still correct.

**Repository shape — and one guard-coverage trap to avoid.** All five family-selecting statements
plus `mapCatalogItem` change (`:19-29`, `:30-40`, `:52-63`, `:64-77`, `:214-223`). **Do not use
`json_agg` / `string_agg` / `array_agg`.** The only test that executes this repository's SQL is
`CourseProgramCatalogRepositoryProgramFamilyIntegrationTest`, which runs against **H2 in PostgreSQL
mode**, not real PostgreSQL — and its own header comment (`:22-38`) records that
`NativeQueryPostgresIntegrationTest` discovers subjects by reflecting over `@Query(nativeQuery=true)`
annotations, so this hand-written-`JdbcTemplate` repository is **structurally invisible to the
PostgreSQL harness**. Aggregate SQL would therefore be verified by neither guard. **Use a plain
`LEFT JOIN` returning one row per (program, family) pair and group in Java** (`LinkedHashMap` keyed
by program id, preserving `ORDER BY course_programs.name, program_families.name`). Identical
behaviour on H2-PG-mode and real PostgreSQL, no aggregate-function portability risk, and the existing
integration test keeps covering it.

### `POST /course-program-catalog` — additive request field, ADMIN

`CreateCourseProgramCatalogRequest(name, programFamilyId, programFamilyIds, examGoalSlug)`.
Accept `programFamilyIds: List<UUID>` when present; fall back to the legacy singular
`programFamilyId` when `programFamilyIds` is absent (one release of tolerance, removed in the
contract step). Atomic: program row and all membership rows are inserted in the **one existing
`@Transactional` service method** — the frontend must never fire N follow-up membership mutations.

### `PATCH /course-program-catalog/{id}` — **breaking change, and it is free**, ADMIN

`UpdateCourseProgramCatalogRequest(List<UUID> programFamilyIds)` — **authoritative replacement**, not
add/remove. `[]` clears all memberships; a list sets exactly that set. This matches the existing
`null`-clears semantic the current single-UUID version already has, and CLAUDE.md's preference for
explicit replacement.

**No compatibility shim is needed and none should be written**, because the endpoint has **zero
clients**: `grep updateCourseProgram frontend/lib/api.ts` returns nothing, and none of `api.ts`'s
three `method: "PATCH"` sites (`:5045`, `:5534`, `:5570`) targets `/course-program-catalog`. Verify
this again at implementation time rather than trusting this line.

Transaction semantics: delete-then-insert the membership set inside one `@Transactional` method.
Validation, all pre-existing exception types, no new ones needed beyond reuse:
`UnknownProgramFamilyException` per unknown id (400); `CourseProgramNotFoundException` for the
program (404); duplicate ids in the request are **deduplicated silently**, matching
`handleFamilyExpansion`'s existing union semantics (`applicable-programs-combobox.tsx:218-228`) —
rejecting them would be a gratuitous new failure mode for a request that expresses a set.

### `GET /course-program-catalog/families` — unchanged shape, **auth stays ADMIN**

`ProgramFamilyResponse(id, name)`. No member ids and no count are added — the authoring surfaces
derive counts from the catalog list they already hold (§K), and adding a count here would create a
second, divergent source for the same number.

**Keeping this ADMIN-only is correct and was verified, not assumed.** The only control that needs it
is the create-program modal, which is gated on `canCreateCatalogProgram`, and all four consumers pass
an ADMIN check or sit behind an admin route (`bulk-generation-page-client.tsx:599`,
`private-note-detail-page-client.tsx:2780`, `note-editor-page-client.tsx:1421`,
`admin-applicable-programs-section.tsx:211` behind `app/admin/course-programs/page.tsx:16-18`).
**Do not relax it to USER.** But see §I on fail-soft: that admin page uses a *redirect-only* guard
whose own comment (`:13-15`) documents a brief non-admin render, so a 403 on this fetch is reachable
and must degrade to an empty picker, never to a blocking error.

### `POST /course-program-catalog/families` — unchanged

Optional member selection at creation time is **deferred** (§P). §12 permits it only if nearly free;
it is not, once the create modal needs a program multi-select it does not currently have.

---

## H. Admin UX

**Table** (`admin-course-program-catalog-section.tsx:207-217`) becomes:

| Course / Program | Program Families | Actions |
|---|---|---|
| Accountancy | `Accounting` | Edit |
| Computer Engineering | `Engineering` `Computing & Technology` | Edit |
| Nursing | `Health Sciences` | Edit |
| Law | — | Edit |

**⚠️ Owner tightening §6 — Exam Goal is dropped from this release entirely, not deferred as a
read-only display.** The prior draft of this plan proposed adding `exam_goal_slug` as a display
column since it was "nice-to-have, not a requirement." The owner cut it outright: this release stays
about Program Family membership, full stop, and Exam Goal management (including read-only display) is
a separate product/architecture concern because it touches the public Exam Hub. **Do not add
`exam_goal_slug` to `CourseProgramCatalogItemResponse` in this release** — no DTO change, no new
SELECT column, no table column. §N's blast radius and the Codex prompt reflect this removal.

- Families render as chips in stable order (alphabetical, from the API's own ordering). Zero
  memberships render `—`, the existing convention.
- **Mobile:** the table already sits in `overflow-x-auto` (`:211`). Per §30, do not ship that as the
  answer. Below `sm`, render each program as a stacked card (name as the heading, family chips
  wrapping beneath, Edit as a full-width action) using the existing responsive card pattern, and keep
  the table at `sm` and up. Chips wrap; long family names are never truncated
  (`docs/features/program-families.md` records that `v0.149.0` shipped and then *removed* an
  unverified mobile chip-collapse — **do not reintroduce a collapse here**).

**Edit action** opens `AppModal` (`components/ui/app-modal.tsx`), the repo's standard dialog, whose
`flex-1 overflow-y-auto` body plus `shrink-0` actions row already guarantees Save stays reachable at
any content height — the property the feature doc analysed at length. No bottom-sheet variant needs
inventing.

**Edit modal contents — tightened to the exact owner scope, Exam Goal removed entirely (not even
displayed):**

```
Edit Program Families

Program: Computer Engineering

Program Families
[ Engineering × ] [ Computing & Technology × ]  (multi-select, add more)

              Cancel   Save changes
```

| Field | Editable? | Why |
|---|---|---|
| Program (name) | **Read-only identity label**, not a form field | Renaming has real identity consequences and no backend support. `V142:30-40` spends ten lines establishing that a rename must keep the row id because `note_course_program` and user profiles reference it, and `CourseProgramCatalogRepository.resolveIdForLegacyName` (`:180-188`) plus `users.course_program` still resolve programs **by name string**. `PATCH` has no name field. Renaming is a separate release with its own audit. |
| **Program Families** | **YES — the entire job of this modal** | Multi-select, pre-selected with current membership. |

Exam Goal does not appear anywhere in this modal — not editable, not read-only, not displayed. The
modal title is **"Edit Program Families"**, not the more general "Edit Course / Program," to make the
narrowed scope explicit in the UI itself, matching the owner's exact content sketch.

Actions: `Cancel` / `Save changes`. On save: `PATCH` with the full `programFamilyIds` set, then
update the row in local state from the response (the endpoint returns the fresh item). Failure keeps
the modal open with the error inline, matching the section's existing error handling (`:194`, `:203`).

**No separate Program Family administration page** (§11). The existing "New Program Family" block
(`:168-195`) stays exactly as it is.

---

## I. Single Note Create / Edit UX — `Add Course / Program`

`applicable-programs-combobox.tsx:364-379`. Two changes, and only two.

**1. The picker's source changes — this is the bug fix.** Delete the derived `programFamilies`
`useMemo` (`:119-128`) and replace it with a fetch of `listProgramFamilies()`. Fetch it **lazily, when
the modal first opens**, not on component mount: three of the four consumers render this component
for users who may never open the modal, and one is a 2,700-line note detail page. Cache the result in
component state for the component's lifetime; refetch on each fresh modal open is also acceptable and
simpler.

**Fail-soft is mandatory, not optional.** On fetch failure — including the reachable 403 described in
§G — render the multi-select with an empty option list and a quiet inline note
(*"Program Families could not be loaded."*). The program can still be created with zero families,
which is always a valid outcome. **Never block `Add and select` on this fetch.**

**2. Single-select becomes multi-select.** Label changes `Program Family (optional)` →
`Program Families (optional)`. The `"No family"` sentinel option is **deleted** — zero selections is
the natural representation, per §8. Use the repo's existing chip/multi-select pattern; the selected-
programs chip row in this same file (`:314-338`) is the house style for a removable chip with an
accessible `aria-label={`Remove ${name}`}` button, and should be reused rather than re-invented.
**Helper text — owner's exact wording (§7), replacing the implementation-flavored draft:**
*"Group this program with related programs for faster selection when curating notes."* No mention of
"authoring expansion" or other implementation terminology — the copy describes the curator-facing
outcome, matching the register of every other authoring-surface helper string in this repo.

On `Add and select`, `handleCreate` (`:175-211`) sends `programFamilyIds: [...]` in one request. The
existing three post-create effects are unchanged: append to `createdPrograms`, notify
`onCatalogProgramCreated`, and `selectProgram(createdProgram)`.

**⚠️ The §15 boundary, which must survive implementation.** Selecting families **in this modal** sets
the *new program's* membership. It is **not** a Note family-expansion action, and the other members of
those families must **not** be added to the Note. The code already has this property by construction —
`handleCreate` calls `selectProgram(createdProgram)` (`:188`) with exactly one program and never
touches `handleFamilyExpansion` — and the two paths must stay structurally separate. A test asserts
it (§O). Label the two controls so a curator can tell them apart: the modal field is
**"Program Families"**, the chips outside it stay under `aria-label="Program family shortcuts"`
(`:283`).

---

## J. Bulk Note Create UX

**Identical, for free — and this is already true today.** `bulk-generation-page-client.tsx:594-605`
renders the *same* `ApplicableProgramsCombobox` instance, fed by the *same* `getCourseProgramCatalog()`
call (`:213-231`). It shares the API, the fetcher, the DTO, the component, and the state shape. Every
change in §I reaches Bulk Create with no Bulk-specific work.

**Answering §6/§34 Q6 directly: yes, Bulk Create has the identical defect, from the identical line of
code, and it is fixed by the identical change.** The only difference between the two surfaces is the
gate on the create affordance — `isAdmin` here vs. `userRole === "ADMIN"` there — which is the same
predicate spelled differently. No divergence to reconcile. Verify at implementation time that Bulk
still renders correctly rather than assuming shared component ⇒ shared behaviour.

---

## K. Applicable Program family expansion

**Unchanged in behaviour. Changed only in how member ids are derived.**

`availableProgramFamilies` (`:89-113`) keeps deriving from the catalog — it must, and the feature
doc's reasoning for it is still exactly right: **an empty family has nothing to expand and should
show no chip.** The only edit is to iterate `program.programFamilies` instead of reading the scalar
pair, which naturally lets one program contribute to several families' `memberIds`.

| Case | Behaviour | Where it already lives |
|---|---|---|
| None selected | `Family · N`, clickable | `:296`, `:305-307` |
| Partial | `Family · N remaining`, click adds only the missing ones | `:305-307`, `:218-228` |
| Full | `✓ Family · N`, visible and **inert** — a `<span>`, not a disabled button, no `aria-pressed` | `:285-294` |
| **Overlapping families** | Clicking Engineering then Computing & Technology adds Computer Engineering **once** | `handleFamilyExpansion` (`:218-228`) is already a set union over a `Set` and already skips ids in `nextIdSet`. **No change needed.** |
| Manual removal after expansion | Program stays removed until a family containing it is expanded again; the family reverts to partial | `:326` + `unselectedCount` recompute (`:111`) |
| Note persistence | **No family id, ever.** Only individual program ids reach `PUT /notes/{id}/applicable-programs` | ADR-001 `:93`, `:95`; feature doc "Persistence and reads" |

No hidden ownership state exists and none is introduced — there is no "Engineering still owns this"
concept anywhere in the component, and the chip states are pure functions of `selectedIds` and the
catalog. The de-duplication requirement of §14 is **already satisfied by shipped code**; the test in
§O exists to pin it, not to drive new work.

`is_active === false` programs continue to be excluded from `memberIds` (`:93`), so a retired program
is not expanded onto a Note. The feature doc's known limitation — a full family's count shrinking
silently when a member is retired — is **unchanged and still open**; this release neither fixes nor
worsens it, and the row stays.

---

## L. Cache / query architecture

**There is no query cache.** `frontend/package.json` contains no react-query or TanStack dependency;
every surface is `useState` + `useEffect` fetch-on-mount. There are no query keys, no stale times, and
no invalidation to configure. **This also means the observed bug was never a caching problem** and no
caching change would have fixed it (§C).

Canonical contracts after this release:

| Data | Endpoint | Client | Consumers |
|---|---|---|---|
| Course / Program catalog **with memberships** | `GET /course-program-catalog` | `getCourseProgramCatalog()` | all 4 authoring surfaces + admin |
| Program Family list (**incl. empty**) | `GET /course-program-catalog/families` | `listProgramFamilies()` | admin section + **the create modal (new)** |

Refresh behaviour, which is sufficient and needs no new machinery:

- **After family creation** (admin only): the section already appends to local state (`:63`) and
  re-fetches both lists on `load()`. Unchanged.
- **After a membership update** (new Edit action): update the edited row in local state from the
  `PATCH` response. Do not re-fetch the whole catalog for a single-row edit.
- **After Course/Program creation**: both the admin section (`:117`) and the combobox
  (`:186-187` → `onCatalogProgramCreated`) already append the created item to local state, and all
  four consumers already implement that callback. Unchanged.
- **Cross-surface propagation**: a family created in Admin reaches a Note Create modal on that
  modal's next open (§I fetches lazily per open) or on the page's next mount. That satisfies §5 and
  §17. **No websocket. No polling. No global cache invalidation.** Nothing is indefinitely stale
  because nothing is cached beyond a component's lifetime.

**Do not introduce react-query for this release.** It would be a new infrastructure dependency across
the whole app to solve a problem that does not exist here.

---

## M. Initial catalog data operations (owner-run, post-deploy)

**These are the owner's to execute. Claude does not run them.** Per CLAUDE.md, the statements are
written out with expected row counts and verification; the owner may extract them into a
`docs/claude-plans/*.sql` file. They are deliberately **not** in a Flyway migration — this is catalog
data, not seed logic, exactly as `docs/features/program-families.md` states.

**Preconditions:** §F step 4 live on both Vercel and Render (`scripts/check-deploys.sh`), and §F's
parity verification returning `missing_rows = 0`, `copied_rows = 26`.

**⚠️ Owner tightening §10 — all FOUR empty families get populated in this release, not two.** The
owner approved populating Computing & Technology and Built Environment & Design alongside Health
Sciences and Accounting, with conservative initial memberships **deliberately chosen to demonstrate
overlap**: Computer Engineering → Engineering + Computing & Technology, and Architectural Engineering
→ Engineering + Built Environment & Design. Every named program below was re-confirmed to exist in
the canonical catalog on 2026-09-15 (the query run for this pass, not assumed from the prior audit) —
no name mismatches found.

**⚠️ Re-verify the ids first — family ids are runtime-random.** Run this and confirm every row
returns before running anything below:

```sql
SELECT 'family' AS kind, id::text, name FROM program_families
 WHERE name IN ('Health Sciences','Accounting','Computing & Technology','Built Environment & Design')
UNION ALL
SELECT 'program', id::text, name FROM course_programs
 WHERE name IN ('Nursing','Medicine','Pharmacy',
                'Accountancy','Management Accounting','Accounting Information Systems','Internal Auditing',
                'Information Technology','Computer Science','Software Engineering','Computer Engineering',
                'Architecture','Interior Design','Landscape Architecture','Urban and Regional Planning',
                'Environmental Planning','Architectural Engineering')
ORDER BY kind, name;   -- expect 4 family rows + 17 program rows = 21 rows
```

**Preferred route: the Admin UI — this is the owner's explicit, primary instruction (§11), not merely
"preferred."** Once §H ships, the owner opens `/admin/course-programs`, clicks Edit on each of the 17
programs, and ticks the relevant family/families (two programs — Computer Engineering, Architectural
Engineering — get **two** ticks each). This is deliberately the end-to-end production acceptance test
of the whole release (frontend → API → service → membership storage → refreshed catalog → authoring
shortcuts), specifically because `v0.149.0` shipped two features that never got this exact kind of
verification (a PATCH endpoint with no client, an `is_active` column with no writer — §"Corrections").
**The SQL below is the documented emergency fallback only, per CLAUDE.md's production-write
protocol** — not the normal operational path, and should not be reached for if the Admin UI works.

```sql
-- Health Sciences: Nursing + Medicine + Pharmacy.  EXPECT: 3 rows inserted.
INSERT INTO course_program_family (id, course_program_id, program_family_id)
SELECT gen_random_uuid(), cp.id, 'ee64905a-878a-46d4-ab67-71e1989a7b3b'
FROM course_programs cp
WHERE cp.name IN ('Nursing','Medicine','Pharmacy')
ON CONFLICT (course_program_id, program_family_id) DO NOTHING;

-- Accounting: 4 members.  EXPECT: 4 rows inserted.
INSERT INTO course_program_family (id, course_program_id, program_family_id)
SELECT gen_random_uuid(), cp.id, 'c55a73a3-ba70-4187-95aa-8fa36d6f5d9f'
FROM course_programs cp
WHERE cp.name IN ('Accountancy','Management Accounting',
                  'Accounting Information Systems','Internal Auditing')
ON CONFLICT (course_program_id, program_family_id) DO NOTHING;

-- Computing & Technology: 4 members, incl. the Engineering overlap case.  EXPECT: 4 rows inserted.
INSERT INTO course_program_family (id, course_program_id, program_family_id)
SELECT gen_random_uuid(), cp.id, 'aca40dcb-cc6e-4726-b7f4-43916b273d37'
FROM course_programs cp
WHERE cp.name IN ('Information Technology','Computer Science',
                  'Software Engineering','Computer Engineering')
ON CONFLICT (course_program_id, program_family_id) DO NOTHING;

-- Built Environment & Design: 6 members, incl. the Engineering overlap case.  EXPECT: 6 rows inserted.
INSERT INTO course_program_family (id, course_program_id, program_family_id)
SELECT gen_random_uuid(), cp.id, '00f6c913-ad5c-4b4d-beee-7914c7579d7e'
FROM course_programs cp
WHERE cp.name IN ('Architecture','Interior Design','Landscape Architecture',
                  'Urban and Regional Planning','Environmental Planning','Architectural Engineering')
ON CONFLICT (course_program_id, program_family_id) DO NOTHING;
```

`ON CONFLICT DO NOTHING` makes every statement idempotent, so a partial run (e.g. via the Admin UI for
some programs, SQL for the rest) can be safely repeated without creating duplicates.

**Verification. EXPECT exactly: Accounting 4, Built Environment & Design 6, Computing & Technology 4,
Education 8, Engineering 18, Health Sciences 3.**

```sql
SELECT pf.name, count(m.id) AS members
FROM program_families pf
LEFT JOIN course_program_family m ON m.program_family_id = pf.id
GROUP BY pf.name ORDER BY pf.name;
```

**⚠️ Overlap acceptance check — this is the release's actual proof, per the owner's §10/§18
instruction to make it part of production acceptance verification, not just a unit test:**

```sql
SELECT cp.name, array_agg(pf.name ORDER BY pf.name) AS families
FROM course_programs cp
JOIN course_program_family m ON m.course_program_id = cp.id
JOIN program_families pf ON pf.id = m.program_family_id
WHERE cp.name IN ('Computer Engineering', 'Architectural Engineering')
GROUP BY cp.name ORDER BY cp.name;
-- EXPECT:
--   Architectural Engineering | {Built Environment & Design, Engineering}
--   Computer Engineering      | {Computing & Technology, Engineering}
```

If either row shows only one family, or is missing, the many-to-many change has not actually taken
effect for the case it was built to fix — stop and diagnose before calling the release done.

**Engineering (18) and Education (8) are preserved by the migration and are touched by nothing here.**
No duplicate family rows are created — all six already exist and this plan creates none.

**Out of scope per §12/§19, and listed so nobody adds them "while they're in there":** Business
Administration, Entrepreneurship, Economics, Senior High – ABM, Real Estate Management, Certified
Management Accountant, Chartered Financial Analyst, Finance. All eight are absent from every statement
above. Finance does not exist in the catalog at all and stays a CPALE-curation trigger (§G of the
prior plan, unchanged).

---

## N. Blast radius

### MUST CHANGE

| File | What | Risk |
|---|---|---|
| `backend/.../db/migration/V146__*.sql` *(new)* | Membership table + copy + parity assertion | **HIGH.** The one irreplaceable relationship. Mitigated by the in-migration `RAISE EXCEPTION`. |
| `backend/.../repository/CourseProgramCatalogRepository.java` | 5 SQL constants + `mapCatalogItem` + membership insert/replace/read | **HIGH.** Only guard is an H2-PG-mode test; see §G's no-aggregates rule. |
| `backend/.../service/CourseProgramCatalogService.java` | `create()` and `updateProgramFamily()` take a set; transactional replace | MED. Validation-loop and dedupe correctness. |
| `backend/.../dto/CourseProgramCatalogItemResponse.java` | `+ List<ProgramFamilyResponse> programFamilies`, keep scalars | **HIGH** if scalars are dropped early — that is the `v0.136.0` failure mode. |
| `backend/.../dto/UpdateCourseProgramCatalogRequest.java` | `UUID` → `List<UUID> programFamilyIds` | LOW. Zero clients (verified). |
| `backend/.../dto/CreateCourseProgramCatalogRequest.java` | `+ List<UUID> programFamilyIds`, keep singular one release | LOW. |
| `backend/.../controller/CourseProgramCatalogController.java` | Wiring only; **auth annotations unchanged** | LOW — but re-read the four `@PreAuthorize` lines in the diff. |
| `frontend/lib/api.ts` | `CourseProgramCatalogItem.programFamilies`; `parseCourseProgramCatalogItem` tolerance; new `updateCourseProgram()` | **HIGH.** `:5860-5878` throws today; the new field must be optional-tolerant the same way `isActive` already is (`:5874`). |
| `frontend/components/metadata/applicable-programs-combobox.tsx` | Delete derived picker (`:119-128`), lazy `listProgramFamilies()`, multi-select, iterate `programFamilies` in `availableProgramFamilies` | **HIGH.** The bug fix *and* the four-surface shared component. |
| `frontend/components/admin/admin-course-program-catalog-section.tsx` | Multi-select on create, family chips column, per-row Edit, "Edit Program Families" modal, mobile cards. **No Exam Goal work of any kind.** | MED. Largest new UI. |
| `docs/architecture/ADR-001-canonical-knowledge-architecture.md` | Amend constraint 2 (`:92`) + Status line (`:3`) | **Owner-ratified. Prerequisite.** |
| `docs/features/program-families.md` | Rewrite the ⚠️ "admin reads / combobox derives" paragraph — **it currently ratifies the defect**; record many-to-many; correct the `is_active` claim (§"Corrections") | MED. This doc is the next Codex prompt's input. |
| `docs/architecture/DATA_MODEL.md` | Add `course_program_family` | LOW. |
| `RELEASES.md`, `ROADMAP.md`, `docs/releases/v0.150.0.md` | Release records + Backlog rows | LOW. |

### CONDITIONAL

| File | When |
|---|---|
| `backend/.../CourseProgramCatalogRepositoryProgramFamilyIntegrationTest.java` | **Effectively must-change.** It is the only test that executes this SQL; new statements are invisible to every other guard. |
| `backend/.../controller/CourseProgramCatalogControllerTest.java` | Must-change — PATCH body shape changes. Good news: it is **already real `MockMvc` with `.contentType(MediaType.APPLICATION_JSON)`** (`:84-85`, `:106-112`, `:124`, `:135-136`), including a real non-admin 403 request (`:132-140`). CLAUDE.md's "one real request" obligation is already met; keep it that way. |
| `backend/.../service/CourseProgramCatalogServiceTest.java` | Must-change — mocked repository signatures move. |
| `frontend/.../applicable-programs-combobox.test.tsx`, `admin-course-program-catalog-section.test.tsx`, `lib/api-course-program-catalog.test.ts`, `lib/api-program-families.test.ts` | Must-change. |
| `frontend/.../bulk-generation-page-client.test.tsx`, `note-editor-page-client.test.tsx`, `private-note-detail-page-client.test.tsx`, `admin-applicable-programs-section.test.tsx` | Only if their fixtures build `CourseProgramCatalogItem` literals — they do (grep hits on `program_family`). Fixture updates only. |
| `frontend/components/ui/*` multi-select | Only if no suitable chip multi-select exists. **Check before building one** (§8). |
| `backend/.../EducationProgramFamilyMigrationTest.java` | Only if it asserts on `program_family_id` post-state. Read it; do not assume. |

### NO CHANGE — and each of these is a load-bearing negative

| File / area | Why |
|---|---|
| `notes`, `note_course_program`, every Note DTO | Families are never persisted on a Note. §22, ADR-001 `:93`/`:95`. |
| `OpenAiLlmStudyPackService`, `StudyPackGenerationContextResolver`, `prompts/study-pack-v1/**` | **Verified:** a grep for `ProgramFamily`/`programFamilyId`/`program_family_id` across `backend/src/main/java/` returns hits in **only** the catalog controller/service/repository and their DTOs/exceptions. No generation code receives family identity. §21. |
| `NoteLibraryRepositoryImpl`, `PublicLibraryRepositoryImpl`, discovery/filter/facet code | Same grep. Discovery never resolves a family. ADR-001 constraint 3. |
| `notes.domain_context` and everything Domain Context | Untouched. §21 LOCK. |
| `exam_goal_slug`, `findNamesByExamGoalSlug` (`:46-51`), `PublicExamGoalCourseProgramController` | Independent of families and stays that way. §25. |
| `course_programs.is_active` semantics | Not widened, not fixed here. See §"Corrections". |
| `Nursing · Medicine`, `Nursing · Pharmacy` rows | Preserved untouched. §24. |
| `handleFamilyExpansion` (`:218-228`) | **Already correct for overlapping families.** Do not rewrite it. |
| `hooks/use-course-program-catalog.ts` / `course-program-combobox.tsx` | The separate legacy singular free-text `courseProgram` axis. Out of scope, and the feature doc already documents the gap. |

---

## O. Testing plan

Mapped to invariants. Redundant coverage is called out rather than re-specified.

**Migration** — extend `CourseProgramCatalogRepositoryProgramFamilyIntegrationTest` (the only place
this SQL executes):
1. Every pre-existing non-null `program_family_id` produces exactly one membership row — **seeded
   from the real `V106` + `V142` migration files it already loads via `ClassPathResource`**, so the
   fixture cannot hand-build a state the migrations cannot produce.
2. Programs with `program_family_id IS NULL` produce zero membership rows and remain valid.
3. The `uk_` constraint rejects a duplicate `(program_id, family_id)` pair.
4. The parity `DO` block raises on a deliberately-broken copy.

**Backend service/repository:** create with zero / one / multiple families; unknown family id → 400;
duplicate ids in one request deduplicated to one row; update zero→one, one→multiple,
multiple→different set, multiple→zero; a failed membership insert rolls the whole update back
(nothing partially applied).

**Controller** — the existing `MockMvc`-with-`contentType` tests (`:72-140`) are already the right
shape; **update their bodies to the list form, do not replace them with method calls.** Add: the
ADMIN-only guard survives on the list-shaped PATCH; `programFamilyIds: []` clears memberships.

**Catalog API / `lib/api.ts`:** `programFamilies` parses; **a payload *without* `programFamilies`
still parses** (the old-backend deploy window — the precise regression `parseCourseProgramCatalogItem`
would otherwise cause); a program with two families round-trips both.

**Note authoring** (`applicable-programs-combobox.test.tsx`):
- **The bug fix itself: a family with zero catalog members appears in the create-modal picker.** This
  is the one test that fails today and passes after; without it the release has no proof.
- A zero-member family shows **no expansion chip** (the asymmetry is intentional and must be pinned,
  or a later "consistency" fix will break it).
- Overlapping expansion: catalog where Computer Engineering is in two families; click both; assert
  the id appears **once** in `onChange`'s payload and the payload has no duplicates.
- Manually-selected programs survive expansion; a member removed after expansion stays removed and
  the chip reverts to partial.
- **`onChange` payloads contain only program ids — never a family id** (the §22 guard).
- **§15 boundary: creating a program with two families selected calls `onChange` with exactly the new
  program's id, and does NOT add those families' other members.** The highest-value new test in the
  set, because it is the one place the two meanings of "family" could be conflated.
- Picker fetch rejects (403/500) → modal still usable, `Add and select` still works, zero families.

**Bulk Note Create:** one test that Bulk renders the picker with a zero-member family. Do **not**
duplicate the whole single-create suite — same component, same contract (§J) — but do run that one,
because "shared component ⇒ shared behaviour" is an assumption, and CLAUDE.md is explicit that a
behaviour change with no test beside it is unverified.

**Admin:** table renders zero / one / multiple family chips; Edit loads current memberships
pre-selected; save issues **one** `PATCH` with the full set; clearing to zero works; a program with
no family renders `—`.

**Regression:** Engineering expands all 18; Education all 8; Health Sciences/Accounting expand
correctly once members exist (fixture-driven, not production-dependent); Note applicability payload
is still a flat list of program ids.

**Do not write:** tests for family deletion (not supported, not being added), program deletion (same),
family-side membership editing (§11, not built), or `is_active` mutation (no write path — §"Corrections").

---

## O2. Acceptance matrix — the owner's 22 locked invariants (§17), each mapped to implementation + test

| # | Invariant | Implementation location | Test |
|---|---|---|---|
| 1 | Membership is many-to-many | `course_program_family` (§E) | `CourseProgramCatalogRepositoryProgramFamilyIntegrationTest` — two families for one program round-trip |
| 2 | Membership table authoritative after cutover | `CourseProgramCatalogRepository`/`Service` read/write only the new table post-cutover (§E "Authoritative storage") | Service test: after cutover, `create()`/`updateProgramFamily()` never issue an `UPDATE course_programs SET program_family_id` |
| 3 | No primary Program Family | `programFamilyId`/`programFamilyName` documented as read-time, alphabetical-first, compatibility-only (§G) | `lib/api.ts` test: a two-family program's deprecated scalar fields resolve to the alphabetically-first family, and no code path treats it as "the" family |
| 4 | Engineering (18) / Education (8) survive migration exactly | `V146`'s relationship-parity `DO` block (§E) | Migration test seeded from real `V106`+`V142`; asserts `missing_rows = 0` |
| 5 | Empty families appear when assigning families to a program | `listProgramFamilies()` used by the create/edit pickers, not the derived catalog scan (§C fix, §I) | `applicable-programs-combobox.test.tsx`: zero-member family appears in the create-modal picker — the one test that fails today |
| 6 | Empty families do NOT appear as Note expansion shortcuts | `availableProgramFamilies` keeps deriving from catalog membership (§K, unchanged) | Existing test pattern extended: a zero-member family shows no expansion chip |
| 7 | Create/edit Course/Program changes catalog membership only | `handleCreate` / Admin Edit modal never call `handleFamilyExpansion` (§I "§15 boundary") | The highest-value new test (§O): creating a program with two families selected calls `onChange` with only the new program's id |
| 8 | Expanding a family on a Note changes only individual program IDs | `handleFamilyExpansion` (`:218-228`, unchanged) | Existing test, re-run unmodified — proves no regression |
| 9 | Overlapping families deduplicate by Course/Program ID | Same function, already a `Set` union (§K) | New test: Computer Engineering in two families, expand both, id appears once |
| 10 | Existing Notes never change when family membership changes | No storage path exists for family on a Note (§E "Note-side model") | Repository test: `note_course_program` rows are never written with a family-derived value |
| 11 | No Program Family ID persisted on a Note | Same | `onChange` payload assertion: only program ids, never a family id |
| 12 | Program Family does not enter generation | Repo-wide grep, zero hits outside the catalog controller/service/repository (§N) | No test needed — verified by absence; re-grep at implementation time |
| 13 | Program Family does not change Domain Context | Structural: combobox receives no `domainContext` prop | Compile-time guarantee, not a runtime test (per the original audit's reasoning on manufactured tests) |
| 14 | Program Family does not change Authored Depth | Same structural argument | Same |
| 15 | Program Family does not become discovery truth | Same repo-wide grep as #12 | Same as #12 |
| 16 | Program Family does not infer Review Set membership | No prop/parameter connects them | Same as #13 |
| 17 | Admin UI is the normal membership-management path | §H Edit action + §M's explicit preference statement | Production acceptance check (§M): the overlap query, run after using the Admin UI, not SQL |
| 18 | Course/Program creation supports zero/one/multiple families | `CreateCourseProgramCatalogRequest.programFamilyIds` (§G) | Backend test matrix: zero, one, multiple |
| 19 | Existing Course/Programs support zero/one/multiple via Admin Edit | `UpdateCourseProgramCatalogRequest.programFamilyIds`, authoritative replace (§G) | Backend test matrix: zero→one, one→multiple, multiple→different, multiple→zero |
| 20 | New Admin families reach Note Create/Bulk without hardcoding | `listProgramFamilies()` shared contract (§I, §J) | The same zero-member-family test as #5, run against both Single and Bulk surfaces |
| 21 | Deprecated scalars are explicitly non-authoritative | §G's DTO comments + `docs/features/program-families.md` update (§N) | Doc review, not a code test — a doc claim is the risk here, not logic |
| 22 | New membership writes never dual-write the legacy scalar FK | §E "Authoritative storage after cutover" | Same as #2 |

---

## P. Scope exclusions — owner's explicit non-goals list, each mapped to its reason

Every item below is a deliberate exclusion, not an oversight. This is the owner's own enumerated
non-goals list (their tightening-pass §19), with the reasoning already established in this plan
attached to each:

1. **Course / Program renaming** — identity consequences, no backend support; name-based resolution
   still live at `CourseProgramCatalogRepository:180-188` and in `users.course_program`.
2. **Exam Goal editing** — a different, public-surface blast radius (§H).
3. **Exam Goal admin redesign** — not merely "editing dropped," the column/field doesn't appear in
   this release's UI at all (§H, tightened this pass).
4. **`is_active` write path** — a real gap this audit found (§"Corrections" item 2), but a genuinely
   different feature; backlogged (§13 of the tightening — see the proposed Backlog Index row below).
5. **Fused-program cleanup** (`Nursing · Medicine`, `Nursing · Pharmacy`) — preserved untouched, and
   actually *blocked* on item 4 above, not merely deferred: there is no code path to deactivate them
   without the write path that doesn't exist.
6. **Finance as a catalog program** — stays a CPALE-curation trigger (unchanged, prior plan's §G).
7. **Broadening Accounting** beyond its 4 approved members (Business Administration, Entrepreneurship,
   Economics, Senior High – ABM, Real Estate Management, CMA, CFA) — owner tightening §12.
8. **Family-side membership editing** (Family → Programs direction) — §11. Not nearly free; the
   Course/Program → Families direction already makes every gap visible in the existing Admin table.
9. **Family deletion** — not currently supported, not being added.
10. **Program (Course/Program) deletion** — same.
11. **react-query / TanStack / any caching library** — §L. No cache exists today; nothing in this
    release needs one.
12. **Websocket / polling** — §5/§17. Refetch-on-next-open is sufficient.
13. **Note schema changes** — none. `notes` and `note_course_program` are untouched.
14. **Note family provenance** (which family a selection came from) — never persisted, by design (§22
    of the original spec, ADR-001 `:93`/`:95`).
15. **Generation changes** — verified by repo-wide grep (§N): no generation code reads family
    identity, and this release adds none.
16. **Domain Context changes** — untouched (§21 LOCK, unchanged).
17. **Authored Depth changes** — untouched.
18. **Discovery changes** — verified by the same grep; family never resolves at read/discovery time.
19. **Review Set changes** — untouched; no coupling exists or is added.
20. **Automatic retroactive Note changes** — structurally impossible (family isn't stored on a Note),
    and this release adds no mechanism that could make it possible.
21. **Dropping the legacy `course_programs.program_family_id` column** — a later release's contract
    step (§F step 6). The only irreversible action in the whole sequence; must not ride along.
22. **Dropping the deprecated `programFamilyId`/`programFamilyName` response fields** — same later
    release, after both platforms are confirmed live on the new frontend.

**Also excluded, from the earlier audit, still correct, not in the owner's list only because it
predates this pass:** bulk/multi-row admin editing, drag-drop, spreadsheet editing, a family
dashboard (§32 of the original spec); member selection inside "New Program Family" creation (§12,
needs a program multi-select the create-family form doesn't have); filtering the legacy singular
`courseProgram` free-text suggestion list (a separate, already-recorded product decision in
`docs/features/program-families.md`).

**Proposed Backlog Index row** (for whoever runs the `v0.150.0` kickoff to add — this plan does not
edit `ROADMAP.md` itself, consistent with not committing outside an open release branch):

> **Course / Program catalog lifecycle management** — `is_active` (`V145`) has no write path anywhere
> in the codebase; no service method, repository `UPDATE`, endpoint, or admin control sets it.
> Production confirms 0 inactive rows. Blocks the legacy fused-row (`Nursing · Medicine`,
> `Nursing · Pharmacy`) deprecation, which is recorded elsewhere as pending data work when it is
> actually undone code work. Scope for a future release: deactivate/reactivate through Admin/API,
> retire the two fused rows safely, associated compatibility/discovery implications. `[Gate: none —
> ready to scope whenever picked up]`. Source: `program-family-many-to-many-final-plan.md`,
> 2026-09-15.

---

## Corrections to the prior planning documents and to the `v0.149.0` record

Everything below was verified against current code and current production, not inferred.

1. **`PATCH /course-program-catalog/{id}` shipped with ZERO frontend consumers.** The endpoint exists
   and is well-tested (`CourseProgramCatalogController:53-61`; real `MockMvc` tests at
   `CourseProgramCatalogControllerTest:72-140`). There is **no `updateCourseProgram` in
   `frontend/lib/api.ts`**, and the admin table (`admin-course-program-catalog-section.tsx:207-217`)
   has no Edit action. `docs/releases/v0.149.0.md` says *"Admins can now move an existing
   Course/Program catalog entry into a different family"* — **admins cannot, through any UI.** Good
   news for this plan: the request DTO can change shape freely (§G).

2. **`is_active` shipped with NO WRITE PATH AT ALL — this is a record defect, not just a gap.**
   `V145` adds the column; the repository reads it in four SELECTs and maps it (`:221`); the DTO
   exposes it; the combobox filters on it. **Nothing anywhere sets it.** There is no service method,
   no repository `UPDATE`, no endpoint, and no admin control. Production confirms: **0 rows with
   `is_active = false`.** Two published claims are therefore false:
   - `docs/releases/v0.149.0.md`: *"A catalog program can now be marked inactive"* — it cannot.
   - The same file's Known Limitations: the fused-row deprecation is *"documented for the next
     post-deploy pass."* **That pass is impossible without a code change** — there is no statement an
     owner can run through any sanctioned route, because `is_active` is not reachable from the API at
     all. A row whose cells contradict each other is, per CLAUDE.md, a defect in itself.
     **This needs correcting in the record before it becomes input to a future kickoff's scope
     decision** — it currently reads as "data work pending" when it is "code work never done."

3. **There are six families in production, not the four the specification assumed** — resolved this
   pass. Computing & Technology and Built Environment & Design were created 2026-09-15, minutes after
   Health Sciences and Accounting. The owner has since approved populating all four (§10 of the
   tightening); §M now includes exact statements for all four, re-verified against the live catalog.

4. **The single-FK/`is_active` schema claims in the prior plan are still accurate**, and were
   re-verified rather than trusted: `course_programs` is
   `(id, name, program_family_id, exam_goal_slug, created_at, is_active)` in production today.

5. **ADR-001 blocks the many-to-many change on its literal text** (`:92`). Neither prior planning pass
   had to confront this, because both stayed on the single FK. It is the reason §A is
   "APPROVE with a prerequisite" rather than plain "APPROVE."

6. **Neither `is_active` nor these SQL constants are covered by the PostgreSQL harness.**
   `CourseProgramCatalogRepositoryProgramFamilyIntegrationTest:22-38` records that
   `NativeQueryPostgresIntegrationTest` reflects over `@Query(nativeQuery=true)` annotations, so this
   hand-written `JdbcTemplate` repository is structurally invisible to it — and the substitute test
   runs **H2 in PostgreSQL mode**, not PostgreSQL. This constrains the SQL shape (§G) and should be
   stated plainly in the release rather than left for a cold agent to rediscover.

---

## Q. Codex routing

**Codex is REQUIRED.** CLAUDE.md's routing table is unambiguous: this is a *"new feature touching
backend (new endpoint, migration, service logic)"*, a *"multi-system change (frontend + backend
together)"*, and a *"refactor touching > 5 files or > ~100 LOC"* — three separate triggers, and the
tiebreaker (anti-drift rules applied across many files) points the same way.

**No existing prompt covers this.** `docs/codex-prompts/` contains
`v0.149.0-program-family-expansion.md`, which is the *previous* release's single-FK prompt and must
not be reused. **The owner has now explicitly requested the Codex prompt as part of this tightening
pass — written to `docs/codex-prompts/v0.150.0-program-family-many-to-many.md` (Long mode), covering
slices 1–3.** Slice 0 (ADR amendment + doc corrections) is docs-only with fully-specified exact text
and is recommended as direct Claude-Code work on the release branch, not a Codex prompt — see the
routing note in that file.

**Recommended slices** (three PRs into one release branch; slice 1 must merge first):

| Slice | Contents | Verification |
|---|---|---|
| **0 — prerequisite** | ADR-001 amendment + `docs/features/program-families.md` + the §"Corrections" record fixes | Owner ratification. Docs-only, release branch. |
| **1 — schema + backend** | `V146`, repository, service, DTOs (additive response), controller wiring, all backend tests | `advisor()` before the prompt and on the diff; `/audit-diff`; the H2 integration test extended |
| **2 — authoring frontend** | `lib/api.ts`, `applicable-programs-combobox.tsx` (picker fetch + multi-select + membership iteration), the four consumers' fixtures | `/audit-diff`; the zero-member-family test is the acceptance gate |
| **3 — admin frontend** | Catalog table (family chips, Edit action), "Edit Program Families" modal, mobile cards | `/audit-diff` |

**Verification tier: ONE SCOPED COLD AGENT, framed as falsification** — not the full three-agent
pressure test, and not a single `advisor()` call. The trigger is evidentiary, not precautionary:
CLAUDE.md names *"delivery introduced a defect the same session then fixed"* as a measured blind-spot
signal, and **`v0.149.0` shipped two half-features in exactly this code area** (a PATCH endpoint with
no consumer; an `is_active` column with no writer), neither caught by that release's own two
falsification passes. Add a migration that moves the only copy of a real relationship, and one scoped
agent is warranted. Hand it a tight file list and these specific claims to disprove:
*(a)* every pre-existing membership survives `V146`; *(b)* no family id reaches a Note payload;
*(c)* overlapping expansion cannot duplicate; *(d)* no generation or discovery path reads family
identity; *(e)* **enumerate every file the release ADDED and name those with no test that executes
them.** `model: "sonnet"` suffices for claim-checking.

**Release mechanics.** `RELEASES.md`, `frontend/package.json` and `backend/pom.xml` all read
**v0.149.0**, which is **Released**. A **v0.150.0 kickoff must land as the first commit on
`releases/v0.150.0`** before any implementation, per CLAUDE.md. Kickoff step 8 must give this file a
Backlog Index row. **Separately: CLAUDE.md's own `Current version:` block still reads `v0.147.0`** —
three releases stale. Flagged, deliberately not edited by this planning pass; the v0.150.0 kickoff's
step 3 should replace it.

---

# PROGRAM FAMILY MANY-TO-MANY — FINAL PLAN

```
Product verdict:
APPROVE — conditional on an owner-ratified ADR-001 amendment to constraint 2 (ADR-001:92, which
literally forbids "any preset table beyond course_programs.program_family_id"). Proposed replacement
text supplied in §A. Constraint 2's substance is unchanged; only its storage clause moves.

Program Family semantic:                        REUSABLE AUTHORING SET
Exclusive taxonomy:                             NO
Course / Program → families:                    ZERO / ONE / MANY
Family → Course / Programs:                     ZERO / ONE / MANY

Current single FK:
course_programs.program_family_id — nullable single FK, V106:12 + fk_course_programs_program_family.
CONFIRMED still present in production 2026-09-15. Retained through the release; dropped only in a
later contract release.

Many-to-many storage:
NEW TABLE course_program_family (id, course_program_id, program_family_id, created_at) with
uk_course_program_family_program_family UNIQUE(course_program_id, program_family_id), two FKs
ON DELETE CASCADE, two indexes. Named from note_course_program's convention (V107:1-11).

Existing Engineering memberships preserved:     YES — 18, copied by V146, parity asserted in-migration
Existing Education memberships preserved:       YES — 8,  copied by V146, parity asserted in-migration

Health Sciences:            Nursing + Medicine + Pharmacy
Accounting:                 Accountancy + Management Accounting + Accounting Information Systems
                            + Internal Auditing
Computing & Technology:     Information Technology + Computer Science + Software Engineering
                            + Computer Engineering                        [owner-approved this pass]
Built Environment & Design: Architecture + Interior Design + Landscape Architecture
                            + Urban and Regional Planning + Environmental Planning
                            + Architectural Engineering                   [owner-approved this pass]

All four families' current DB state:
ALL FOUR EXIST, ALL FOUR EMPTY (0 members, created 2026-09-15T13:42Z) — re-confirmed live, unchanged
since the prior audit. Six families total in production (Engineering 18, Education 8 unaffected).
Computing & Technology and Built Environment & Design were the two families the original spec said not
to auto-create; the owner has now explicitly approved populating both in this release (§10 of the
tightening), specifically because their obvious first members (Computer Engineering, Architectural
Engineering) demonstrate the release's own headline overlap case. §M has the exact statements and the
production overlap-acceptance query.

Program Family persisted on Note:               NO
Family used for discovery:                      NO  (verified by repo-wide grep — §N)
Family sent to generation:                      NO  (verified by repo-wide grep — §N)
Family changes Domain Context:                  NO
Family changes Authored Depth:                  NO
Family inferred from Review Set:                NO
Existing Notes retroactively updated:           NO

Family expansion:                               AUTHORING-TIME SET UNION
Overlapping family expansion:                   DEDUPLICATE BY COURSE / PROGRAM ID
                                                (already satisfied by shipped code —
                                                 applicable-programs-combobox.tsx:218-228)

Admin existing-program editing:                 YES — per-row Edit → AppModal titled "Edit Program
                                                Families". Program name read-only identity label.
                                                Exam Goal DROPPED FROM SCOPE ENTIRELY (not displayed,
                                                not editable) — owner tightening §6.
Admin separate family-management page:          NO
Course / Program creation supports multiple:    YES — both Admin and the Note Create modal
Single Note Create uses runtime family catalog: YES — GET /course-program-catalog/families, lazy on
                                                modal open, fail-soft to an empty list
Bulk Note Create uses runtime family catalog:   YES — same component, same contract, free
Hardcoded family names:                         NO — none exist today and none are introduced
New Admin family automatically available:       YES, on the modal's next open / page's next mount
Realtime websocket sync:                        NO
Domain Context architecture change:             NO
Note schema change:                             NO
Course / Program family schema migration:       YES — V146, additive, expand→migrate→contract
Catalog data operation:                         YES — owner-run post-deploy; Admin UI is the PRIMARY
                                                path (owner's explicit instruction, §11), SQL is a
                                                documented emergency fallback only. 17 memberships
                                                across 4 families, all routes idempotent.

Finance:                                        OUT OF SCOPE EXCEPT EXISTING CPALE TRIGGER
Computing & Technology family:                  ✅ POPULATED THIS RELEASE (owner-approved) — 4 members
Built Environment & Design family:              ✅ POPULATED THIS RELEASE (owner-approved) — 6 members
Legacy fused programs:                          PRESERVE / SEPARATE CLEANUP
                                                ⚠️ and that cleanup is BLOCKED, not merely deferred:
                                                is_active has no write path anywhere in the codebase.

Root cause of the reported defect:              DIFFERENT API.
  Admin calls the canonical GET /course-program-catalog/families and sees all six.
  The Note-Create modal derives its picker from catalog rows that already carry a family
  (applicable-programs-combobox.tsx:119-128), so a family with zero members contributes nothing and
  is invisible. Engineering (18) and Education (8) were the only non-empty families — exactly the two
  the screenshot showed. Not hardcoded (no literals exist), not stale cache (no cache library exists
  at all), not a legacy enum.

Biggest migration risk:     LOSS OR DUPLICATION OF EXISTING ENGINEERING/EDUCATION MEMBERSHIPS
                            — mitigated by a RAISE EXCEPTION parity assertion inside V146 itself, the
                            uk_ constraint, and keeping program_family_id readable throughout.
Biggest UX risk:            CONFUSING COURSE-PROGRAM FAMILY MEMBERSHIP WITH NOTE FAMILY EXPANSION
                            — the §15 boundary; pinned by a dedicated test (§O).
Biggest architecture risk:  DUPLICATED/HARDCODED FAMILY SOURCES ACROSS AUTHORING SURFACES
                            — and note this already HAPPENED and was RATIFIED IN A FEATURE DOC as
                            deliberate. The fix must correct the doc, not only the code.
Biggest process risk:       Shipping half a feature again. v0.149.0 shipped a PATCH endpoint with no
                            client and a lifecycle column with no writer, and its release notes claim
                            both work. Every slice in §Q owes a test that executes what it added.
```

### Core doctrine

> Program Family is an authoring set, not an exclusive taxonomy bucket.
>
> A Course / Program may participate in multiple useful Program Families.
>
> Program Families are catalog data, not frontend configuration.
>
> Creating or editing a Course / Program changes its family membership; expanding a family on a Note
> selects ordinary Applicable Programs. These are different operations.
>
> Program Family expansion is an authoring-time convenience. Saved individual Applicable Programs
> remain the Note's truth.
>
> Family membership changes never retroactively change existing Notes.
>
> Course / Program answers WHO can study the knowledge. Domain Context answers HOW it should be
> treated. Authored Depth answers AT WHAT DEPTH it is written. Review Set answers WHERE it belongs in
> the learning journey.

**DO NOT IMPLEMENT YET.**
