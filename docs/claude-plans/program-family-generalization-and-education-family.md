# Program Family Generalization + Education Family — Audit & Plan

**Status:** AUDIT COMPLETE — plan below, **not implemented**.
**Date:** 2026-09-08. Every claim carries a `file:line` anchor.

---

## 1. Executive judgment — the generalization already happened

**⚠️ The brief's central premise is false. There is no Engineering-specific authoring shortcut.**

```tsx
Add all {family.unselectedCount} {family.name} {family.unselectedCount === 1 ? "program" : "programs"}
```
`frontend/components/metadata/applicable-programs-combobox.tsx:290`

The button interpolates the family name and count. It reads *"Add all 18 Engineering programs"*
**only because Engineering is the only family that has members.** And the families themselves are
derived **dynamically from the catalog** (`:88-111`): the component builds a map from each program's
`programFamilyId` / `programFamilyName`, computes `unselectedCount`, and drops families with nothing
left to add. **There is no Engineering literal anywhere in it.**

**So this is a DATA change, not a generalization project.** Seed the Education family and its
programs, and the existing UI renders *"Add all 8 Education programs"* with no code change.

**§6 is already satisfied too** — all four surfaces share the same component:
`note-editor-form.tsx` (single Create), `bulk-generation-page-client.tsx` (Bulk Generate),
`private-note-detail-page-client.tsx`, and `admin-applicable-programs-section.tsx`.

**Two real gaps, both small:**

| Gap | Detail |
|---|---|
| **Families can only be created by migration** | Zero `ProgramFamilyEntity` construction or `save` anywhere in the backend |
| **The admin UI cannot assign a family** | `POST /course-programs` **does** accept `programFamilyId` and validates it (`CourseProgramCatalogService:54-56`, `UnknownProgramFamilyException`), but `frontend/app/admin/course-programs/page.tsx` exposes **no family field** |

**⚠️ And one duplicate is already visible in the seed — see §3.**

---

## 2. Current architecture

`V106__course_program_catalog.sql` — **the only migration that touches families:**

```sql
CREATE TABLE program_families (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    CONSTRAINT uk_program_families_name UNIQUE (name)
);

CREATE TABLE course_programs (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    program_family_id UUID,                       -- nullable FK
    exam_goal_slug VARCHAR(32),
    CONSTRAINT uk_course_programs_name UNIQUE (name),
    CONSTRAINT ck_course_programs_exam_goal_slug
        CHECK (exam_goal_slug IS NULL OR exam_goal_slug IN ('ale','pnle','let','cpale')),
    ...
);
```

**The primitive is fully generic** — a name and a nullable FK. Nothing about it is Engineering-shaped.

**Seeded state:** one family (`Engineering`), and `Education` already exists as a program with
`program_family_id = NULL` and `exam_goal_slug = 'let'`.

**§7 confirmed — Program Family never reaches the LLM.** `StudyPackGenerationContext` has six fields
and `courseProgram` is a **single resolved String**; there is no family field and no list field. The
resolver returns a catalog name **only when exactly one program is joined**. **Family expansion
cannot reach a prompt because there is nowhere to put it.** §4's invariant is therefore already
structural, not a rule to enforce.

---

## 3. ⚠️ §12 duplicate safety — one collision found, and the seed is NOT the live catalog

**Found in the seed:** `Special Needs Education – Generalist` already exists. The brief proposes
`Special Needs Education`. **Inserting that would create a semantic duplicate.**

Note the catalog's naming convention — a qualified suffix after an **en dash** (`–`, not a hyphen):
`Senior High – ABM`, `Senior High – STEM`, `Senior High – HUMSS`,
`Special Needs Education – Generalist`.

**⚠️ AND THE MIGRATION SEED IS NOT THE CURRENT CATALOG.** `V106` seeds **three** Engineering programs
(Civil, Electrical, Mechanical); the brief reports **18** in production. No later migration inserts
any (`V107`, `V108`, `V117` touch `course_programs` for other reasons). **So the catalog has been
extended through `POST /course-programs` in production, and a duplicate audit from the repo alone is
unsound.**

**Run this before inserting anything** (read-only):

```sql
SELECT cp.name, pf.name AS family, cp.exam_goal_slug
FROM course_programs cp
LEFT JOIN program_families pf ON pf.id = cp.program_family_id
WHERE cp.name ILIKE '%educ%' OR cp.name ILIKE '%teach%' OR cp.name ILIKE '%BEEd%'
   OR cp.name ILIKE '%BSEd%' OR cp.name ILIKE '%BPEd%' OR cp.name ILIKE '%TVT%'
   OR cp.name ILIKE '%early child%' OR cp.name ILIKE '%special need%'
   OR cp.name ILIKE '%physical educ%' OR cp.name ILIKE '%certificat%'
ORDER BY cp.name;

SELECT pf.name AS family, COUNT(cp.id) AS programs
FROM program_families pf LEFT JOIN course_programs cp ON cp.program_family_id = pf.id
GROUP BY pf.name ORDER BY pf.name;
```

**⚠️ `GET /course-programs/similar?name=` already exists** (ADMIN-only,
`CourseProgramCatalogController:33-37`) — the duplicate check the brief asks for is **already built**,
and the admin create flow should use it per name before inserting.

---

## 4. Recommended program list

Reconciled against the seed and the naming convention:

| Proposed | Recommendation |
|---|---|
| Education | **Already exists** — assign it to the Education family (§3 of the brief) |
| Elementary Education | Insert |
| Secondary Education | Insert |
| **Special Needs Education** | **⚠️ DO NOT INSERT — RENAME instead. DECIDED (owner, 2026-09-08):** rename the existing `Special Needs Education – Generalist` row to `Special Needs Education` in the same migration, keeping its `id`. **Do not create a second row** — see §4a for what the rename does and does not touch |
| Early Childhood Education | Insert |
| Technical-Vocational Teacher Education | Insert |
| Physical Education | Insert |
| **Teacher Certification** | **Endorsed** — see below |

**§13 — `Teacher Certification` is the right canonical name.** The brief's reasoning holds and the
repo confirms it: **Professional Education is already a `DomainContext` value**
(`PROFESSIONAL_EDUCATION`) and is used as a Subject, so a catalog program of the same name would
collide across two axes. `Teacher Certification` describes the field, not the credential (CPE/DPE),
which matches the convention. **⚠️ Do not use a credential abbreviation as the canonical name.**

### 4a. ⚠️ What the rename touches — verify before running it

**Safe: Applicable Programs.** `note_course_program` joins by **`course_program_id`**
(`V107:4`, FK to `course_programs`), so every existing Note keeps its link through a rename. The row
keeps its `id`; only `name` changes.

**⚠️ NOT safe automatically: five free-text `course_program` columns**, any of which may hold the
literal string `Special Needs Education – Generalist` and would silently keep the old value:

| Table | Column |
|---|---|
| `notes` | `course_program` (legacy per-note string) |
| `note_collections` | `course_program` |
| `users` | `course_program` (profile) |
| `bulk_generation_result` | `course_program` |
| `official_study_plan_wishlist` | `course_program` **and** `normalized_course_program` |

**No repository method resolves a catalog entry by name**, so nothing breaks — but a stale string no
longer matches the catalog, which affects discovery and the wishlist's normalized matching.

**⚠️ Add this to the §3 read-only audit and decide per hit BEFORE the migration runs:**

```sql
SELECT 'notes' AS src, COUNT(*) FROM notes WHERE course_program ILIKE '%Special Needs Education%'
UNION ALL SELECT 'note_collections', COUNT(*) FROM note_collections WHERE course_program ILIKE '%Special Needs Education%'
UNION ALL SELECT 'users', COUNT(*) FROM users WHERE course_program ILIKE '%Special Needs Education%'
UNION ALL SELECT 'wishlist', COUNT(*) FROM official_study_plan_wishlist WHERE course_program ILIKE '%Special Needs Education%';
```

**If every count is 0, the rename is a pure catalog edit.** If any is non-zero, the owner decides
whether those strings move with it — **⚠️ that would be a production write, which is the owner's to
run, never Claude's.**

**⚠️ `exam_goal_slug`:** `Education` already carries `'let'`. Whether the new programs also carry it
is a product decision — the CHECK constraint allows it, and it drives
`PublicExamGoalCourseProgramController`. **Recommendation: set `'let'` only on programs whose learners
genuinely sit the LET**; leave it NULL otherwise rather than making the goal a family proxy.

---

## 5. Implementation plan

| Step | Content | Route |
|---|---|---|
| **1** | **Read-only catalog audit** (§3 SQL) — confirm collisions before writing anything | Owner or Claude (SELECT is permitted) |
| **2** | **One migration**: insert the `Education` family; assign the existing `Education` row to it; **rename `Special Needs Education – Generalist` → `Special Needs Education` (same `id`)**; insert the remaining programs from §4 | Claude Code inline |
| **3** | **Admin UI: family selector on create** — the API already accepts and validates `programFamilyId`; the form does not offer it. Closes the gap that made step 2 a migration | Claude Code inline |
| **4** | *(Optional, only if a future family is wanted without a migration)* an admin create-family surface | **Defer** |

**⚠️ The applicable-programs combobox needs NO change.** If a diff touches it, the scope has drifted.

**Step 3 is what stops this recurring.** Without it, every future family member is another migration
even though the endpoint already supports assignment.

**Copy polish (§8)** — small, same slice as step 3:

> **Course / Program(s)** — *Choose the programs this note genuinely applies to.*
> *Use a program family to quickly add related programs.*
> **Domain Context** — *Choose the academic context that should shape how this note is written.*

**⚠️ Drop resolver mechanics from the helper text** (*"only a single program can inform the writing
domain"*). Keep the conceptual separation; do not explain the backend.

---

## 6. Tests (§14)

Most of §14 is **already covered by the generic component** — 1–6 and 9–10 exercise code that does
not change. **Add or confirm:**

| # | Test | Note |
|---|---|---|
| 2 | **Education family expands to its explicit member IDs** | The one genuinely new case |
| 3 | Expansion creates no duplicate selections | Existing behaviour; assert with **two** families present |
| 5 | Mixed-family selection survives | **⚠️ A single-family fixture proves nothing** — needs Engineering **and** Education selected together |
| 7 | **Generation context receives no family or expanded list** | Assert the resolved `StudyPackGenerationContext` — it has no field for either, so this pins the invariant |
| 8 | Adding a family member later does not mutate existing Notes | `note_course_program` rows are explicit; assert the join table is untouched |
| 12 | `GENERAL_EDUCATION` does not auto-select Education programs | Assert nothing is selected on Domain Context change |
| 13 | Review Set membership does not alter Applicable Programs | Assert `note_course_program` unchanged after adding a Note to a collection |

**⚠️ Add a rename guard:** a Note already linked to `Special Needs Education – Generalist` must still
be linked after the rename, and must render the **new** name. *A fixture created after the rename
passes trivially and proves nothing — the fixture must exist before it.*

**⚠️ Test 1 (Engineering still expands) is the regression guard for the migration** — if assigning the
existing `Education` row a family accidentally touched Engineering rows, this catches it.

---

## 7. Genuine owner decisions

1. ~~`Special Needs Education – Generalist` — reuse or rename?~~ **SETTLED (owner, 2026-09-08):
   RENAME** to `Special Needs Education`, keeping the row's `id`. **⚠️ Conditional on §4a's audit
   returning zero free-text hits**; if it does not, the handling of those strings is a further owner
   decision and a production write.
2. **Do the new Education programs carry `exam_goal_slug = 'let'`?** Recommendation: only where the
   learners genuinely sit the LET.
3. **Ship step 3 (admin family selector) now or later?** Recommendation: **now** — it is small and it
   is what prevents the next family from needing a migration.

---

## 8. Anti-drift

- **⚠️ Do NOT modify the applicable-programs combobox** — it is already family-generic.
- **⚠️ Do NOT create a new Domain Context** — `GENERAL_EDUCATION`, `PROFESSIONAL_EDUCATION` and
  `PROFESSIONAL_PRACTICE_AND_REGULATION` already cover LET.
- **⚠️ Do NOT let Program Family select or override Domain Context**, and do not infer the Education
  family from `GENERAL_EDUCATION`.
- **⚠️ Do NOT feed Program Family or an expanded program list to the LLM** — structurally impossible
  today; keep it that way.
- **⚠️ Do NOT delete or migrate away the existing `Education` program** — assign it a family only.
- **⚠️ Do NOT mass-update existing Notes' applicable programs.** No backfill of `note_course_program`.
- **⚠️ Do NOT infer Applicable Programs from Review Set membership.**
- **⚠️ Do NOT make family membership dynamic inheritance** — expansion writes explicit program IDs at
  authoring time and nothing more.
- **⚠️ Do NOT create duplicate catalog entries** — check `/course-programs/similar` first (§3).
- **⚠️ Do NOT use a credential abbreviation (BEEd, BSEd, CPE/DPE) as a canonical program name.**
- **⚠️ Do NOT block mixed-family selections**, and do not add warning UX for them.
- **⚠️ Do NOT redesign the program taxonomy, touch learner-owned Notes, or change pricing,
  entitlements or Review Set architecture.**

## 9. Verification tier

**A single `advisor()` call.** A data migration plus one admin form field; no permission substrate, no
cross-user read, no money semantics, no learner-facing behaviour change.

**⚠️ But the migration writes to a production catalog table**, so the §3 read-only audit is a
precondition, not a formality — an inserted duplicate is visible to every curator immediately and is
awkward to withdraw once Notes reference it.
