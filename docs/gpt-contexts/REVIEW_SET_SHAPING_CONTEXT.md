# Review Set shaping — module

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

## The four metadata axes

| Axis | Question | Notes |
|---|---|---|
| **Title** | what knowledge does this note contain | knowledge-first; never name the program, review set or curriculum container. "Highway Drainage Systems", not "Drainage Systems in Civil Engineering". Keep a disciplinary qualifier only when it *defines* the knowledge ("Nursing Management of Acute Asthma") |
| **Subject** | which academic subject shelves it | permanent, travels with the note |
| **Domain Context** | which authoring tradition calibrates AI generation | single-valued, generation-only, closed vocabulary — see below |
| **Applicable Programs** | which programs can discover it | many-to-many, discovery-only, never reaches a prompt |

**Authored Depth** (how deep) is a fifth field and is usually uniform across a board-review set.

## Domain Context — the closed vocabulary

Only these eight values exist. **Adding one is an architecture decision, not a curation call — do
not propose a new value.**

`ENGINEERING_MATHEMATICS` · `ENGINEERING_SCIENCES` · `CIVIL_ENGINEERING` ·
`PROFESSIONAL_PRACTICE_AND_REGULATION` · `GENERAL_EDUCATION` · `PROFESSIONAL_EDUCATION` ·
`NURSING` · `ACCOUNTANCY`

Choosing one — ADR-001's rule is **the coarsest label under which the note's treatment is
identical**, with a binary test: *would a student in a sibling program be served by this exact
note, unchanged?* Yes → the shared value. No → the program-specific one.

- Shared engineering principles → `ENGINEERING_SCIENCES`
- Computational method, including engineering economics and cost → `ENGINEERING_MATHEMATICS`
- Codes, law, ethics, contracts, licensure, safety → `PROFESSIONAL_PRACTICE_AND_REGULATION`
- Material genuinely specific to one program that has a value → that value
- **No honest fit → leave it unset**, which falls back to the program name. Some programs
  (Architecture, notably) deliberately have no Domain Context and rely on this fallback.

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
