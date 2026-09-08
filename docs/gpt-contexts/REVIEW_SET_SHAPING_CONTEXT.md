# Review Set shaping — module

> Last updated: v0.133.0 - 2026-09-08 (Released). **⚠️ THE LET CATALOG CHANGED — THIS DIRECTLY CHANGES YOUR INPUT.** `V142` seeds the `Education` program family with EIGHT members, ALL tagged `exam_goal_slug = 'let'`: `Education`, `Special Needs Education`, `Elementary Education`, `Secondary Education`, `Early Childhood Education`, `Technical-Vocational Teacher Education`, `Physical Education`, `Teacher Certification`. Before this, exactly ONE carried the LET slug. **So Q4 (the ready-to-add pool) and Q6 (the exact catalog program names) of `docs/curriculum/review-set-reshape-read.sql` return MORE ROWS than they did before `v0.133.0` — re-run the read; do not reuse a pre-`v0.133.0` result set.** **⚠️ `Special Needs Education – Generalist` NO LONGER EXISTS UNDER THAT NAME** — it was renamed to `Special Needs Education` keeping its `id`, so use the new name in any TSV you emit. **⚠️ `Teacher Certification` is the canonical name for the non-education-graduate LET route — do NOT emit `Professional Education`, which already exists both as a `DomainContext` value and as a Subject, and do NOT emit credential abbreviations (BEEd, BSEd, BPEd, CPE) as program names.** --- PRIOR (v0.132.0): - 2026-09-08 (Released). Rule 5 (the publication boundary) and the
> adopter-update note below it are new in this release.

**Paste with `GPT_CONTEXT.md` when the task is designing or rebuilding a Review Set** (a board-exam
curriculum such as the CE, ALE, LET, CPALE or PNLE review). Not needed for other work.

---

## What a Review Set is, structurally

```
Review Set              root collection          "🏛️ ALE Comprehensive Review"
  └── Subject Plan      child collection         "🏛️ History and Theory of Architecture"
        └── Section     a label on each note      "Ancient Architecture"
              └── Note  the canonical note        "Greek Architecture"
```

Four rules that constrain any proposal:

1. **Membership is a join, not a copy.** One canonical note can sit in several Review Sets at once.
   **Propose reuse, never a program-specific duplicate of shared knowledge.**
2. **A section cannot be empty.** Sections are derived from a label carried by each note, so a
   section exists only because notes are in it. Every section you propose needs at least one note.
3. **Section ≠ Subject.** The section is curriculum placement inside one Review Set. The Subject is
   permanent metadata that travels with the note everywhere. A note in the "Ancient Architecture"
   section keeps `Subject: History of Architecture`. **Never invent a Subject just to mirror a plan
   or section name.**
4. **Bulk Generate batches by Subject**, applying one Subject and one Domain Context to the whole
   batch. So the Subject you assign decides how the note is later generated, in groups.
5. **Adding curriculum is not publishing it (`v0.132.0`).** Notes and Subject Plans you add to an
   already-public Review Set are created **unpublished**: invisible to the public catalog, to new
   adopters, and to the update offered to existing adopters, until the curator presses **Publish
   update** on the Review Set's own page. **So a reshape can be built over several sittings without
   anyone seeing a half-finished subject** — which is what makes a large one safe to do
   incrementally. **⚠️ Hiding covers ADDITIONS ONLY: a note you REMOVE, or a section you rename or
   reorder, changes what people see immediately.** Sequence accordingly — add first, and leave
   removals and renames until close to when you publish.
   **⚠️ The Publish control is NOT in the Builder**, which is where you will actually be adding
   notes; it lives on the Review Set's root page, so work can sit unpublished with nothing prompting
   you.

**How an existing adopter receives your work.** Publishing makes an update **available**, never
forced: the learner sees *"Official Review Set updates available"* on their own copy and chooses to
apply it. **Applying is additive-only — new notes and new Subject Plans arrive; moves, renames and
removals are reported to them but never applied** — and their own added notes and progress are kept.
**So an adopter's copy DRIFTS from your source over time, by design.** Do not propose a reshape that
depends on every adopter ending up with an identical set.

## The four metadata axes

| Axis | Question | Notes |
|---|---|---|
| **Title** | what knowledge does this note contain | knowledge-first; never name the program, review set or curriculum container. "Highway Drainage Systems", not "Drainage Systems in Civil Engineering". Keep a disciplinary qualifier only when it *defines* the knowledge ("Nursing Management of Acute Asthma") |
| **Subject** | which academic subject shelves it | permanent, travels with the note |
| **Domain Context** | which authoring tradition calibrates AI generation | single-valued, generation-only, closed vocabulary — see below |
| **Applicable Programs** | which programs can discover it | many-to-many, discovery-only, never reaches a prompt |

**Authored Depth** (how deep) is a fifth field and is usually uniform across a board-review set.

## Domain Context — the closed vocabulary

Only these eleven values exist. **Adding one is an architecture decision, not a curation call — do
not propose a new value.**

`ENGINEERING_MATHEMATICS` · `ENGINEERING_SCIENCES` · `CIVIL_ENGINEERING` ·
`PROFESSIONAL_PRACTICE_AND_REGULATION` · `GENERAL_EDUCATION` · `PROFESSIONAL_EDUCATION` ·
`NURSING` · `ACCOUNTANCY` · `ARCHITECTURAL_DESIGN` · `ARCHITECTURAL_HISTORY_AND_THEORY` ·
`PLANNING_AND_SITE_DEVELOPMENT`

**⚠️ The last three were added in `v0.111.0`** to close a real gap: 132 of 364 ALE rows had no honest
value under the previous eight, and 8 notes carrying two programs could not be generated at all.

**⚠️ MEASURED 2026-09-05 — READ THIS BEFORE RECOMMENDING `(unset)` ANYWHERE. Leaving a note unset is
NOT a neutral act, and the cost has already been paid at scale.**

- **Classification is driven by the GENERATION GATE, not by curator judgement.** Every note with 2+
  programs is classified because the server forces it; **699 of 809 single-program notes are NOT** —
  they silently fall through to the program *name* as the authoring domain.
- **~239 curator notes already generate with NO computation guidance** — **Nursing 106, Architecture
  73, Accountancy 60** — because their program name contains no quantitative keyword and they are
  unset. **This is permanent per note: Study Packs never auto-regenerate.**
- **Civil Engineering is the control: 62/62 pass for free**, purely because `engineering` is a keyword.
  That is the whole defect in one comparison — coverage tracks the program's NAME, not its content.
- **⚠️ `Accountancy` is the trap case.** `ACCOUNTANCY` declares `quantitative = true`, but that flag is
  **never reached on an unset note**, and the keyword scan does not rescue it either: the keyword is
  `accounting`, and **`accountancy` does not contain `accounting`.** An unset Accountancy note on a
  computational subject — taxation, budgeting, receivables, PPE — loses its computation guidance
  outright.
- **So for Nursing, Accountancy and Architecture subjects, `(unset)` is the WORST option**, not the
  safe one. Classify them.

**⚠️ The honest-unset rule still stands and is not weakened by any of the above** — a forced wrong value
is worse than an honest gap. What changes is that you must now say *out loud* what an unset costs on a
computational subject, rather than treating unset as cost-free.

Choosing one — ADR-001's rule is **the coarsest label under which the note's treatment is
identical**, with a binary test: *would a student in a sibling program be served by this exact
note, unchanged?* Yes → the shared value. No → the program-specific one.

- Shared engineering principles → `ENGINEERING_SCIENCES`
- Computational method, including engineering economics and cost → `ENGINEERING_MATHEMATICS`
- Codes, law, ethics, contracts, licensure, safety → `PROFESSIONAL_PRACTICE_AND_REGULATION`
- Material genuinely specific to one program that has a value → that value
- Design process and human factors — architectural programming, space planning and circulation,
  anthropometrics, ergonomics, universal design, concept development → `ARCHITECTURAL_DESIGN`
- Chronological or stylistic treatment — history of architecture, Philippine architecture,
  architectural theory and criticism, heritage conservation → `ARCHITECTURAL_HISTORY_AND_THEORY`
- Site- and district-scale planning — site selection and analysis, topography, solar and wind
  siting, land use and zoning, parking provision, urban planning, landscape architecture →
  `PLANNING_AND_SITE_DEVELOPMENT`
  **⚠️ These three are NOT a licence to move existing engineering material.** Bioclimatic design,
  passive cooling, drainage, building services and construction materials stay
  `ENGINEERING_SCIENCES`, which is quantitative; the three new values are not, so misrouting a
  computational note into them SILENTLY REMOVES its computation guidance.
- **No honest fit → leave it unset**, which falls back to the program name. An honest unset is a
  findable backlog marker; a forced value is a decision that only looks made.
  **⚠️ Do NOT assume any particular program is expected to come back unset.** An earlier version
  of this line named Architecture as deliberately having no Domain Context, and that instruction
  produced 215 `(unset)` rows in the ALE plan — the strategist reproducing a pre-committed answer
  rather than assessing each note. **⚠️ That residue is now 88 rows, not 215** (~127 were classified
  after `v0.111.0`), and a 2026-09-05 audit expects the targeted pass to take it to **0-3**. That pre-empts the exact question
  `[CHECKPOINT — due 2026-09-28]` exists to ask. Apply the rules above per note and let the
  distribution fall where it falls.
  **⚠️ Building services — plumbing, HVAC, electrical distribution, lighting, acoustics, fire
  protection, vertical transportation — and construction materials, testing and management are
  `ENGINEERING_SCIENCES`, not unset.** They are shared engineering knowledge, and the value is
  quantitative, which switches on computation guidance those subjects need.

**Two hard constraints:**

- ⚠️ **Unset is illegal on a note with two or more Applicable Programs.** The server rejects it.
  If a note is shared across programs, it must carry a Domain Context.
- ⚠️ **Unset means no computation guidance** unless the program name happens to contain a
  quantitative keyword (`engineering`, `mechanics`, `math`, `physics`, `chemistry`…). For a
  computational subject that is a real loss — say so when you recommend unset.

## Costs you must weigh

There is **one curator**. Authoring is the binding constraint, and generation is metered — roughly
100 notes per month. So a 240-note plan is a multi-month commitment, and **reuse is worth far more
than new material**. Always order recommendations cheapest first:

1. **Tag-only** — a note exists and is right, but isn't marked applicable to this program
2. **Place existing** — exists and is tagged, just not in this Review Set
3. **Author new** — the expensive path

## ⚠️ Applicable-Programs pools are OVER-INCLUSIVE — filtering is part of your job

Program tags were partly produced by an authoring surface that defaulted the program from the
curator's own profile. One such default wrongly tagged 106 notes and had to be undone. So when you
are handed "notes already tagged for program X", **do not treat that as a list to add**. Split it:

- **(a) belongs in this review set**
- **(b) legitimately applicable to the program, but out of scope for a board-exam review** — a note
  can apply to a program without belonging in its licensure reviewer
- **(c) mis-tagged; recommend removing the program tag** — this is a real and wanted output

## Required output

Write the proposal however reads best — narrative, rationale, phasing, all welcome. **Then end with
a machine-readable block**, because the workbook that drives the actual build is generated from it,
and hand-transcribing a few hundred rows is where errors enter.

Emit a single fenced code block of **tab-separated values** with exactly this header:

```
plan_no	subject_plan	plan_description	section	note_title	note_subject	domain_context	status
```

- `plan_no` — 1-based; groups rows into Subject Plans
- `subject_plan` / `plan_description` — repeat identically on every row of that plan
- `section` — repeats on every row of that section
- `note_subject` — the canonical Subject, **not** the section name
- `domain_context` — an enum value above, or literally `(unset)`
- `status` — exactly one of `Existing` (already in the set) · `Reuse` (exists elsewhere, add it) ·
  `New` (needs authoring) · `Excluded` (deliberately held out — keep these rows, they record a
  decision)

**Row order is authoritative** — plans, sections and notes are rendered in the order you emit them,
so sequence them the way you want them taught. Do not sort alphabetically.

A note that legitimately belongs to two plans appears **twice**, with the same title and subject.
That is correct and is flagged automatically as one canonical note; do not rename either copy to
make them look distinct.
