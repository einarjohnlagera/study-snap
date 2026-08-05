# Slice 3 — Program Family expansion: curator decision sheet

Prepared 2026-08-05, after Slices 1 and 2 merged (PRs #990, #991).

> ## ANSWERED 2026-08-05 — all four decided by owner ruling. This sheet is now the record of how they were reached.
>
> **1. Catalog growth → YES in principle, but INCREMENTAL — and no seed migration ships with slice 3.**
> The catalog represents **valid applicability**, not curriculum coverage: it answers *"who can
> legitimately study this note?"*, while **Review Sets** communicate completeness. **The catalog still
> follows curriculum; what changed is what "follows" means** — a program no longer needs a complete
> Official Review Set, it needs **legitimate canonical notes applicable to it**. Pre-seeding every PRC
> engineering program is **rejected as premature expansion**; growth is demand-driven by authoring, with
> a curator judging a canonical note applicable to a program as the trigger to add it. This **refines
> rather than reverses** the follow-not-lead precedent — the `Computer Science` / `Software Engineering`
> exclusions stand. **This answers the sheet's own "lead or follow" framing with a third option the
> sheet did not offer: follow, but on applicability rather than on curriculum.**
>
> **Coverage → communicated at the Program level, and NOT built in slice 3.** Programs are a discovery
> surface (*"what notes are applicable to me?"*); Review Sets are the completeness signal (*"what is my
> complete learning journey?"*). When a learner browses a Program with no dedicated Official Review Set,
> say so there — conceptually *"This Program currently contains shared foundational notes. A dedicated
> Official Review Set is still being developed."* **Rejected:** per-note coverage indicators and any new
> coverage metadata system. Recorded as a design direction in the ROADMAP, with its own scoping pass.
>
> **2. More families → families stay, and stay dumb.** Not a curriculum engine. No hidden inference, no
> read-time applicability, no curriculum intelligence. (The sheet's harder framing — whether the
> shortcut earns its complexity at 3 members — was answered by keeping it, on the strength of the
> now-larger engineering catalog from ruling 1.)
>
> **3. All members, or curated subset → ALL MEMBERS.** Subset expansion is rejected. The author trims
> the generated rows. The data model stays as-is: membership via `course_programs.program_family_id`
> and nothing more.
>
> **4. Subject-conditioned → EXPLICITLY REJECTED.** It would quietly turn Program Families into a second
> curriculum taxonomy and permanently couple Subject knowledge to applicability rules. Applicability
> belongs to the Note; Domain Context determines authoring; Subject determines knowledge. Families must
> not become a fourth knowledge model. **The house recommendation in this sheet was accepted.**
>
> **Binding principle, now in ADR-001:** Program Families are a **productivity feature, not a curriculum
> feature.** They are deliberately allowed to over-select, because the Note's explicit Applicable
> Programs are always the source of truth. Maintaining curriculum rules inside Program Families is the
> tripwire that says the feature has exceeded its responsibility.
>
> **The production overlap query below was not needed to decide** — unconditional expansion removes the
> per-subject question it was written to inform. It remains useful for curation and for sizing the
> canonical-note opportunity, so it is kept rather than deleted.

---

## The framing correction: the gate is narrower than three documents imply

ADR-001, `RELEASES.md`, and ROADMAP row 147 all record the gate as the question from
`06-domain-context-taxonomy.md` §6:

> "Is `Engineering Sciences` shared by all **11 engineering programs**, or a subset? A syllabus
> question. It determines the default family expansion."

**NoteLib's catalog contains 3 engineering programs, not 11.** Read from production's catalog
(`V106`, 21 programs, one family):

| Family | Members |
|---|---|
| `Engineering` | Civil Engineering, Electrical Engineering, Mechanical Engineering |
| *(no family)* | Accountancy, Architecture, Aviation, Business Administration, Criminology, Education, Information Technology, Law, Medicine, Nursing, Pharmacy, Physical Therapy, Psychology, Radiologic Technology, Senior High – ABM, Senior High – HUMSS, Senior High – STEM, Special Needs Education – Generalist |

The "11" in `06` is that document reasoning about Philippine engineering curricula in general — it was
never a count of NoteLib's catalog. So the ruling needed to unblock Slice 3 is not an open-ended
syllabus survey. It is: **do Civil, Electrical, and Mechanical Engineering share the subjects in
question?** That is answerable in one sitting.

**This narrows the gate; it does not remove it.** ADR-001 rule 5 already establishes that expansion is
a save-time pre-fill producing explicit, editable rows, and the gate was written knowing that. A
pre-fill *is* a default. Do not read "the author can edit the rows afterwards" as a reason to skip the
ruling.

---

## Run this first — it converts part of the gate from `[EFFORT]` to `[EVIDENCE]`

The gate is labeled a syllabus reading, but production already knows which subjects are taught across
several programs. `05`'s Query J found exactly one instance by hand (`Strength of Materials` existing
as one Civil note and one Mechanical note). Generalised, that becomes the shared-subject list to rule
against, instead of ruling from a blank page:

```sql
-- Subjects that already appear under more than one course/program, with note counts.
-- These are the empirical candidates for family-level applicability.
SELECT n.subject,
       count(DISTINCT n.course_program) AS program_count,
       count(*)                         AS notes,
       string_agg(DISTINCT n.course_program, ', ' ORDER BY n.course_program) AS programs
FROM notes n
WHERE n.subject IS NOT NULL AND trim(n.subject) <> ''
  AND n.course_program IS NOT NULL AND trim(n.course_program) <> ''
GROUP BY n.subject
HAVING count(DISTINCT n.course_program) > 1
ORDER BY count(DISTINCT n.course_program) DESC, count(*) DESC;

-- The same, restricted to the three catalog engineering programs, which is what an
-- `Engineering` family expansion would actually fill in today.
SELECT n.subject,
       count(DISTINCT n.course_program) AS program_count,
       count(*)                         AS notes,
       string_agg(DISTINCT n.course_program, ', ' ORDER BY n.course_program) AS programs
FROM notes n
WHERE n.course_program IN ('Civil Engineering', 'Electrical Engineering', 'Mechanical Engineering')
  AND n.subject IS NOT NULL AND trim(n.subject) <> ''
GROUP BY n.subject
HAVING count(DISTINCT n.course_program) > 1
ORDER BY count(*) DESC;
```

**Run against production.** Local data cannot answer this — the local DB has **zero** Civil,
Electrical, or Mechanical Engineering notes (its six populated program values are Software
Engineering 53, Engineering 2, Accountancy 18, Architecture 10, Senior High – STEM 5, Nursing 4),
while production holds ~197 Civil Engineering official notes.

---

## The four decisions

### 1. Does the catalog gain more engineering programs? `[DECISION]`

This is a product/expansion call, **not** a syllabus reading, and it is what makes "8 vs 11" real or
moot. If the catalog stays at 3 engineering programs, an `Engineering` family expansion fills in 3
rows and the original gate question is largely academic. If NoteLib intends to expand into Chemical,
Electronics, Industrial, Computer, Geodetic, Sanitary, Mining, Metallurgical, Naval Architecture, or
Agricultural Engineering, those need catalog rows — a seed migration, and a decision separable from
Slice 3 itself.

**Ruling needed:** stay at 3, or seed more? If more, which?

### 2. Does Slice 3 seed additional families? `[DECISION]`

`Engineering` is the only family that exists. With 3 members, family expansion ships with almost no
reach — worth asking whether the feature earns its slice at that size. Candidate groupings visible in
the current catalog:

- **Health sciences** — Nursing, Pharmacy, Physical Therapy, Medicine, Radiologic Technology.
  (Note: `06` proposed and `08` **dropped** a `Health Sciences Foundation` *Domain Context*. That is a
  different axis — dropping the context says nothing about whether the *family* should exist.)
- **Education** — Education, Special Needs Education – Generalist.
- **Senior High** — the three strands (ABM, HUMSS, STEM).

**These memberships are the substantive curator decisions**, more so than the engineering subset.

**Ruling needed:** which families exist, and who belongs to each?

### 3. Is expansion "all members of the family," or a curated subset per family? `[DECISION]`

`V106` defines `program_families(id, name)` plus `course_programs.program_family_id` — **membership
and nothing else.** There is no preset table, no subject mapping, no per-family override.

So if expansion means "select `Engineering` → fill in every catalog program whose
`program_family_id` matches," **the preset already exists as the FK**, and Slice 3 is materially
smaller than `18-release-b-slice-sequence.md` assumes — no new schema, just a UI affordance plus a
save-time expansion. If instead a family should expand to a *subset* depending on context, Slice 3
must introduce a preset structure, which is a migration and a curation surface.

**Ruling needed:** all members, or a curated subset?

### 4. Is expansion subject-conditioned? `[DECISION]` — **the biggest scope fork**

`06` §5 proposes different behavior per Domain Context:

| Value | `06`'s proposed reach |
|---|---|
| `Engineering Mathematics` | Algebra, Trigonometry, Analytic Geometry, Calculus, Differential Equations, Probability & Statistics, Engineering Economics — *"all 11 engineering programs"* |
| `Engineering Sciences` | Strength of Materials, Statics, Dynamics, Fluid Mechanics, Thermodynamics, Engineering Materials — *"most engineering programs — **expand explicitly, do not assume all 11**"* |

If expansion is **unconditional**, Slice 3 needs no subject logic at all: the author picks a family,
gets its members, trims what does not apply.

If expansion is **subject-conditioned**, Slice 3 needs a subject (or Domain Context) → family-subset
map — new schema, new curation surface, and a much larger slice. It also risks re-entangling the axes
ADR-001 just separated: Applicable Programs is *where a note appears*, Domain Context is *how it is
authored*, and conditioning one on the other couples them again.

**Ruling needed:** unconditional pre-fill, or subject-conditioned?

**Recommendation, for the owner to accept or reject:** unconditional. It matches ADR-001 rule 5's
"authoring shortcut only," keeps the axes separate, and the author's trim-after-expand is the per-note
judgment the ADR already says carries the real applicability decision. Under this reading, questions
1 and 2 (which programs and families exist) become the load-bearing ones and question 4 stops being a
syllabus problem at all.

---

## What happens after the ruling

**If Slice 3 proceeds:**

1. Record the rulings in ADR-001 and `RELEASES.md`, the way `v0.70.0`'s four owner rulings were.
2. Clear the gate on the ROADMAP Backlog Index row and in `18-release-b-slice-sequence.md`.
3. Only then scope Slice 3 and write its Codex prompt — the preset is an input to that prompt.
4. Slice 3 will need a `docs/features/*.md` home. Program Families are not covered by any feature doc today, so this is a new file rather than an edit.

**If the ruling is "families don't earn their complexity at this size" — an acceptable outcome, not a failure.** Question 2 asks this directly and the consultation prompt licenses answering it. In that case:

1. `v0.71.0` closes with Slices 1 and 2, which already deliver the ADR's stated purpose: one canonical note applicable to many programs, no duplication. Family expansion was always a *shortcut* on top of that, never the capability itself.
2. Slice 3 becomes a ROADMAP Backlog Index row gated on **catalog size** — revisit when a family would expand to enough members to beat ticking boxes — rather than on a syllabus reading. That is a cheaper gate to re-check and a more honest statement of what actually blocks it.
3. The pre-signoff pressure test runs against two slices instead of three.

**Do not read this sheet as presupposing that Slice 3 ships.** The four questions include whether it should.

**Not verifiable locally.** Whatever ships, family expansion cannot be meaningfully checked against
the local DB (zero notes in all three engineering programs). Slice 3's verification is fixture tests
plus a production read after deploy.
