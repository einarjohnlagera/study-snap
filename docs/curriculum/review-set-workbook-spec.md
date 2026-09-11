# Review Set shaping workbook — spec

**For the agent building the workbook.** The strategist-facing half lives at
`docs/gpt-contexts/REVIEW_SET_SHAPING_CONTEXT.md` and is the paste-ready GPT module. The two are
halves of one contract: that doc defines the TSV a strategist emits, this one defines what is built
from it. **Change one and check the other** — the columns are the seam.

## The pipeline

```
review-set-reshape-read.sql        ← gather the inputs (read-only, any Review Set)
        │
        ▼   hand the results to the strategist with the GPT module
strategist proposal (markdown + a TSV block)
        │
        ▼   save the TSV block verbatim as <set>.tsv
build_review_set_workbook.py <input.tsv> <output.xlsx> "<title>" "<description>"
        │
        ▼
<set>-target-shape.xlsx        ← the working document the curator builds from
```

**⚠️ The fragile step is transcription, and the TSV exists to remove it.** The first version of the
ALE workbook was hand-transcribed from GPT's markdown into a Python literal — 364 rows, entered by
eye. That is the single likeliest source of silent error in this whole process, it is unreviewable
once done, and it does not survive a revision. **Never re-enter rows by hand.** If a strategist
returns prose without the TSV block, ask for the block rather than transcribing.

## Running it

The repo's Python is externally managed (PEP 668), so `pip install openpyxl` fails. Use a venv:

```bash
python3 -m venv /tmp/xlsxvenv && /tmp/xlsxvenv/bin/pip install openpyxl
/tmp/xlsxvenv/bin/python docs/curriculum/build_review_set_workbook.py \
  docs/curriculum/ale-comprehensive-review.tsv \
  docs/curriculum/ale-comprehensive-review-target-shape.xlsx \
  "🏛️ ALE Comprehensive Review" "Prepare for the Philippine Architect Licensure Examination…"
```

The builder validates its input: it fails on a missing required column and on an unknown `status`
value, rather than emitting a plausible-looking workbook with a silent gap.

## Step 1 in detail — what to run, and what to hand over

`review-set-reshape-read.sql` is read-only and safe against production. Set the target set's
collection id at the top; the benchmark set resolves itself by title.

| Query | Produces | Paste to the strategist? |
|---|---|---|
| **Q0** | every root Review Set with child-plan and note counts | yes — the two relevant rows; it is the size gap being closed |
| **Q1** | the target set today, note by note | yes if under ~200 rows |
| **Q2** | the target set's skeleton: plan → section → counts | always |
| **Q3** | a comprehensive set as the benchmark | always — this is what "comprehensive" means concretely |
| **Q4** | **notes already tagged for the program but NOT in the set** | always — the ready-to-add pool, the cheapest coverage available |
| **Q5** | per benchmark subject: note count, how many are tagged for the target program, how many are already in the set | always |
| **Q6** | exact catalog program names | yes, trimmed to programs with real counts |
| **Q7** | existing notes in the set, grouped by Subject | not to the strategist — this is for RECONCILIATION afterwards, see below |

**⚠️ Q4 is a candidate pool, not an answer.** Program tags were partly produced by an authoring
surface that defaulted the program from the curator's own profile — one such default wrongly tagged
106 notes. Expect roughly a third of Q4 to be material that shares a jobsite with the discipline
without belonging in its licensure review. The GPT module already asks the strategist to split the
pool three ways and to recommend tag removals; do not pre-filter it yourself.

**⚠️ Q4's `titles` column can be very long.** Paste `subject` + count for everything, and titles only
for the subjects where placement decisions are immediate.

**⚠️ Check Q0 returns exactly one benchmark root.** The benchmark resolves by title match, so two
matching roots would silently union both trees and inflate every count in Q3 and Q5.

## Reshapes: the `Unmapped` status

A **build** (ALE) gets `Existing`/`Reuse`/`New` from the strategist, who was shown the note pool.
A **reshape** (CE) does not: the notes already exist, and the proposal is a target *structure*, so
the strategist has no basis for a per-note status.

**Use `status = Unmapped` for those rows.** It means *proposed; existence not yet checked*. It is
honest, it is visible in the workbook's Flags column, and it keeps the Overview's counts truthful.
**Do not guess `New`** — on a reshape that would report a fully-authored curriculum as unwritten.

Reconcile afterwards with **Q7** of the reshape read, subject block by subject block, and rewrite
the `status` values in the TSV. **⚠️ Match on knowledge, not on string equality:** a strategist
proposes knowledge-first titles ("Normal Stress") while existing notes often carry the older
suffixed form ("Normal Stress in Strength of Materials"). Those are the same note — reuse it and
normalise the title on touch. A string join manufactures phantom `New` rows at scale.

## Sheet contract, and why each sheet exists

| Sheet | Contents | Why |
|---|---|---|
| **Overview** | one row per Subject Plan: sections, in-set total, and a status breakdown, plus a TOTAL row | the size of the job at a glance, and the reuse-vs-authoring split, which is the number that drives sequencing |
| **Domain Context** | every Subject with its Domain Context, note count, **and the Applicable Programs union for that subject**, above the two hard rules | the rules must sit beside the values, not in prose someone skips — and the programs are what make the two rules checkable rather than merely stated |
| **one per Subject Plan** | plan title in A1, **description once in A2**, then Section / # / Note title / Note subject / Domain Context / Status / Flags | the working sheet |
| **By Subject (bulk generate)** | `New` notes grouped by (Subject, Domain Context), largest batch first | Bulk Generate applies one Subject and one Domain Context per run, so each block is literally one run's setup |

### Layout rules that are not cosmetic

- **The plan description appears exactly once, in A2** — merged and wrapped. The first version
  repeated it on all 55 rows of a plan and the sheet was unreadable. If a value is constant for a
  sheet, it is a header, not a column.
- **The section name prints only on its first row**, with a blank row between sections. The eye
  reads groups; a repeated label reads as noise.
- **Row order is preserved exactly.** Nothing is sorted. The strategist's teaching sequence is
  content, not presentation.
- **Freeze panes below the header** on every sheet.

### The Flags column earns its place

It is generated, not authored, and it carries the three things a reader would otherwise get wrong:

1. `same canonical note in another plan — do not duplicate` — a note appearing under two plans is
   **one note in two places**. Without the flag it reads as a data error and someone "fixes" it by
   creating a second note, which is the exact failure the Applicable Programs axis prevents.
2. `context already set — verify before changing` — on `Existing` and `Reuse` rows the Domain
   Context is a *recommendation to change*, not a blank to fill. Changing it is a separate decision
   from placing the note, and it affects future generation only.
3. `unset requires a SINGLE applicable program` — the server rejects a save with 2+ Applicable
   Programs and no Domain Context, so an unset row is a landmine on any shared note.

## `applicable_programs` is REQUIRED, and it is required PER NOTE SUBJECT

**Every plan file must carry an `applicable_programs` column, and every row must fill it.** The
builder rejects a file without it. This is a rule, not a preference, and it is enforced in
`build_review_set_workbook.py` rather than left to memory.

**Why per note subject.** The Domain Context sheet aggregates on `(note_subject, domain_context)`
and prints the union of Applicable Programs for that pair. That union is the only place the two
hard Domain Context rules can actually be *checked* rather than recited:

- `(unset)` is legal only on a note with exactly ONE Applicable Program. Without the programs
  beside the subject, a reader cannot tell which `(unset)` rows are landmines and which are fine.
- A note that gains a second program **must** gain an explicit Domain Context. Reuse is where this
  bites: pulling a single-program note into a second program's Review Set flips it from legal to
  illegal, and the aggregate is what makes that visible before the save fails.

**This rule exists because the column was lost twice.** It first existed only as a HAND-EDIT of the
`.xlsx`, which regeneration silently destroyed. It was then generated from the plan file for ALE —
but LET's `.tsv` was never backfilled, so `let-comprehensive-review.tsv` did **not** reproduce
`let-comprehensive-review-target-shape.xlsx`: rebuilding LET from its own committed source dropped
the Applicable Programs column. That was the documented rule ("edit the TSV and regenerate")
destroying data by being followed.

**LET was backfilled 2026-09-10 and is now idempotent** — its `.tsv` reproduces its workbook
exactly. The values were recovered from the workbook by reverse-mapping on
`(note_subject, domain_context)`, **not** re-read from production, because they are a curated
DECISION about what each note should carry rather than a snapshot of what it carries today. That
distinction matters: a production read would have silently overwritten the policy below.

**⚠️ The backfill also showed the first diagnosis was incomplete, which is worth recording.** The
loss was reported as "the Domain Context sheet loses its Applicable Programs column". True, but the
hand-edit had added that column in FOUR places — the Domain Context sheet, every plan sheet, the By
Subject sheet, and two prose policy blocks — and the builder generated only the first. It had also
been applied INCONSISTENTLY: plan sheets 5 and 6 carried it and sheets 1–4 did not, which no reader
would notice. **When you find a hand edit, enumerate every sheet before assuming you have found all
of it.** The builder now emits the column on every sheet, so the inconsistency cannot recur.

**`civil-engineering-comprehensive-review.tsv` is the one file still owing a backfill** and the
builder refuses it until it gets one. Unlike LET there is nothing to recover from its workbook —
that workbook predates the column entirely — so its values must come from a production read or a
fresh curation decision.

## The policy sidecar

A set may carry `<set>-policy.tsv` (columns: `topic`, `decision`; the first row is the header pair).
It renders beside the Domain Context table and as a summary under the Overview totals. Use it for
decisions that govern Applicable Programs but are not per-note — LET's, for example, records that
General Education notes must NOT get the broad `Education` umbrella while Education-specific notes
must.

**It exists for the same reason the column does.** LET's policy started as prose typed directly into
the `.xlsx`, so regenerating destroyed it — the identical failure, one layer up. **Anything a
curator writes into a workbook needs a file that regenerates it, or the next rebuild is a delete.**

## Editing the workbook later

**Edit the TSV and regenerate. Do not hand-edit the .xlsx.** A hand edit is lost on the next
regeneration and leaves no diff anyone can review; the TSV is the source of truth and diffs
cleanly in git.

**A hand edit does not merely get lost — it goes STALE IN PLACE and keeps rendering.** Generated
columns are computed from the whole file, so editing one cell leaves every derived value beside it
describing the old data. Measured on PNLE 2026-09-10: 25 `New` rows had their placeholder titles
hand-edited into real ones, and all 25 kept the `same canonical note in another plan` flag they had
earned only because the placeholder `(to author)` legitimately repeated 25 times. The workbook read
as if 25 distinct new notes were duplicates. Totals still reconciled and the row count was
unchanged, so nothing looked wrong. **Row counts and totals cannot detect a stale generated
column — rebuild from the TSV and diff, or the check is not a check.**

## Files

| File | Role |
|---|---|
| `review-set-reshape-read.sql` | the read that gathers a strategist's inputs; parameterised by collection id |
| `build_review_set_workbook.py` | the builder — data-driven, no per-set logic |
| `<set>.tsv` | the source rows; regenerable input, diffable, **the thing to edit** |
| `<set>-policy.tsv` | OPTIONAL per-set Applicable Programs policy (`topic`, `decision`); renders beside the Domain Context table and under the Overview totals |
| `<set>-target-shape.xlsx` | the generated deliverable |

A `.csv` deliverable was tried and dropped: it duplicated the TSV's content while being worse to
read than the workbook, so it was two sources of truth for one thing.
