# Decision A (pressure-test finding B2) — consultation prompt for a second opinion

**How to use this:** paste everything below the horizontal rule into a fresh session. It is self-contained —
data model, the bug, the ruling that half-closed it, the full option space, and the constraints that must hold.

## ⚠ GATED — do not send yet. Updated 2026-08-06 after the owner reasoned this out independently.

Two things changed after this prompt was first drafted, and both must be reflected before it is useful:

1. **Option 1 (learner visibility + removal) is REJECTED by the owner.** Their reasoning, which is better than
   mine: *"The core problem is that the migration created metadata that no learner intentionally authored. That
   is a migration problem before it is a UX problem. Exposing Applicable Programs to learners merely to repair
   migration artifacts would leak the curator publishing model into personal note authoring."* My original
   framing treated a data-provenance defect as a permissions gap, and would have paid for it with permanent UI
   complexity in the one surface ADR-001 deliberately keeps simple.
2. **Provenance is now confirmed as the discriminator**, by the owner rather than by me: curated, inherited and
   migration-generated rows are fundamentally different kinds of data, and migration-generated rows are
   **derived data, not user-authored data**. Question 3 below (is Option 5 the principled answer?) is therefore
   no longer speculative — it is the live candidate if the population turns out to be large.

**The decision is gated on measurement, so there is nothing to consult on yet.** Run
`docs/claude-plans/b2-migration-artifact-sizing.sql` against production first. The owner set thresholds in
advance: 0 diverged → prevent future divergence, no UI; a handful → corrective migration; meaningfully large →
Option 5 (a `source` provenance column).

**Send this prompt only if the numbers land in the ambiguous middle**, or if the result argues for Option 5 and
you want the schema change challenged before committing to it. If the count is 0 or trivially small, the owner's
pre-set thresholds already answer it and a second opinion is wasted effort.

**When you do send it, paste the sizing results in** — every question below is materially different depending on
whether `backfill_derived_AND_diverged` is 0, 5, or 200.

---

**What I want from the second opinion (once the data exists):** a recommendation on which option fits the
measured population, and a challenge to the provenance framing itself — is "three kinds of rows" a real
architectural distinction worth encoding, or a rationalization for avoiding a simpler choice?

**Companion documents** (do not paste; reference if asked):
`docs/claude-findings/v0.71.0-pre-signoff-pressure-test.md` (finding B2 in full),
`docs/architecture/ADR-001-canonical-knowledge-architecture.md` (the binding architecture decision),
`docs/claude-plans/onboarding-activation-and-intent-router.md` ("Open decision A").

---

# NoteLib — a migration created metadata that its owner cannot edit

I need a second opinion on a data-model decision. There is an owner ruling in place that resolves half the
problem, and I want to know whether my proposed way of closing the other half is faithful to that ruling or a
convenient misreading of it.

## The product

NoteLib is a study app. Users write **notes**; AI turns a note into a **Study Pack** (summary, key concepts,
quizzes). Notes carry metadata on four axes. The one that matters here is **Course / Program(s)** — which
academic programs a note applies to. It drives two things:

- **Discovery** — library filters, facet counts, public search, shareable URLs
- **Generation** — it resolves the "authoritative academic domain" the LLM is told to write within

## The data model, which is decided and not up for discussion

A recent architecture decision (ADR-001) made program applicability **many-to-many**, so one canonical note can
serve several programs instead of being duplicated per program. That produced **two storage locations**:

| | Who authors it | Storage | Shape |
|---|---|---|---|
| **Personal program** | Learners (ordinary users) | `notes.course_program`, a free-text string | Free text, **exactly one** |
| **Applicable Programs** | Curators (Teacher/Admin) | `note_course_program` join table → a 21-entry curated catalog | Catalog ids, **one or many** |

Four things about this are ratified and must not be reversed:

1. The two modes are a **product distinction, not a permission tier**. Multi-program authoring is a curation
   act. Learners are not "lesser users" — they are doing a different job.
2. `notes.course_program` is **the personal-notes program field**, permanently. It is *not* a legacy column
   awaiting removal. NoteLib is a notebook, not a closed LMS, so learners must be able to type a program the
   catalog does not contain.
3. Reads are **join-first with a legacy-string fallback**:
   `EXISTS(join rows matching) OR (note has NO join rows AND legacy string matches)`.
4. Generation resolves the domain as: explicit Domain Context → **exactly one** joined catalog name →
   `notes.course_program` → the user's profile program. A list of programs may never reach the prompt.

## The bug

The migration that created the join table **backfilled one row per note** whose program string matched the
catalog. It had **no owner filter**, so notes owned by ordinary learners got join rows too.

Because reads are join-first, and because a learner update deliberately does not touch join rows, this happens:

> A student's note has `course_program = 'Nursing'`. The migration backfills a `Nursing` join row. The student
> later edits Course / Program to `Pharmacy` and saves. `notes.course_program` becomes `Pharmacy`; the join row
> still says `Nursing`.
>
> From then on: **generation** authors every future Study Pack and quiz against Nursing. **Discovery** files the
> note under Nursing and never under Pharmacy. The note's own detail page shows Pharmacy while its public page
> shows Nursing. The edit is permanently inert.

The learner cannot fix it. The endpoint that edits Applicable Programs is curator-gated and returns 404 for
them, and no learner-facing surface displays join rows at all. There is a code comment justifying the
"don't touch join rows on a learner save" behaviour on the grounds that *"a learner never authors them"* —
which the unfiltered backfill made false.

## The ruling already in place

The owner ruled:

> Keep learner-owned Applicable Program rows. Do not clear join rows on learner updates. Validation should
> operate against the stored Applicable Program rows. Course / Program(s) is one of the four architectural axes
> and should have the same meaning regardless of ownership. **I don't want ownership changing the semantics of
> the metadata model.**

That cleanly fixes a *separate* defect (a validation rule was reading the incoming request instead of the stored
rows). It does **not** fix the bug above — keeping rows and not clearing them leaves the staleness exactly as it
was.

## Why this is genuinely hard

There are **two populations** of learner-owned join rows, and they differ by **provenance**, not by ownership:

1. **Inherited** — a learner copies a curated multi-program note; the copy inherits the curator's rows. These
   are real, authored, valuable metadata. Copy-inheritance exists specifically to preserve them.
2. **Backfilled** — the migration derived a row mechanically from the learner's own string at a moment in time.
   **Nobody authored these.** They are a migration artifact. These are what cause the bug.

The ruling's principle is about **ownership** ("ownership must not change semantics"). But the distinction that
actually matters here is **provenance**. So the honest question is whether a fix that discriminates by
provenance respects the principle or quietly violates it.

## The options

### Option 1 — Give learners visibility and removal on their own notes — **REJECTED by the owner**
*Rejected on the grounds that it repairs a migration artifact by permanently complicating personal note
authoring, leaking the curator publishing model into a surface ADR-001 deliberately kept simple. Retained here
so a reviewer can disagree with the rejection if they think it was wrong.*
Learners can see their note's Applicable Programs and remove entries. They still cannot multi-add from the
catalog (that would collapse the two authoring modes). The learner removes the stale `Nursing` row; no join rows
remain; their `Pharmacy` string drives discovery again via the existing fallback.

*For:* no data destroyed, no migration, no provenance column. Arguably it is the most literal reading of "the
axis means the same thing regardless of ownership" — today it does not, because only curators can see it.
*Against:* it is new UI in a release already carrying five blockers. It also asks a learner to understand a
concept ("this note also applies to these programs") that the two-mode model deliberately kept away from them.

### Option 2 — Corrective migration
Delete backfill-created rows on learner-owned notes. There is no provenance column, so this keys on the row's
`created_at` matching migration time — a heuristic.

*For:* fixes the whole affected population at once, no UI, no ongoing complexity.
*Against:* destroys data based on a heuristic; irreversible; does nothing about future divergence.

### Option 3 — Re-derive on learner save, surgically
When a learner changes their program string, remove only the join rows matching the *old* string, keep the rest.

*For:* self-healing, no UI, no migration.
*Against:* a previous version of this (which cleared *all* rows) was a shipped bug that silently destroyed
inherited curated programs, and was removed for that reason. The surgical version still misfires when an
inherited row coincidentally matches the old string — e.g. a Nursing learner who copied a curated Nursing note.

### Option 4 — Make the legacy string win for learner-owned notes
Change read precedence based on who owns the note.

*For:* smallest change; directly fixes the symptom.
*Against:* this is precisely "ownership changing the semantics of the metadata model" — it looks like a direct
violation of the ruling. Included for completeness, and because it is the obvious fix someone will propose.

### Option 5 — Add a provenance column
Add `source` to the join table (`BACKFILL` / `CURATED` / `INHERITED`) and make behaviour depend on **how the row
got there** rather than **who owns the note**.

*For:* it engages the ruling's principle head-on rather than dodging it — semantics vary by provenance, not
ownership, and provenance is arguably the real fact. Makes the distinction explicit and queryable instead of
implicit.
*Against:* a schema change plus a backfill of the backfill; the heaviest option. May be over-engineering for a
population we have not yet sized.

### Option 6 — Do nothing; document as a known limitation
*For:* zero risk to a release already carrying five blockers.
*Against:* leaves users with permanently un-editable metadata that silently mis-directs AI generation, and the
count grows every time someone edits their program.

## What we do not know yet, and it matters

**The affected population is unsized.** This query has been written but not run:

```sql
SELECT COUNT(*) AS learner_notes_with_join_rows,
       COUNT(*) FILTER (
         WHERE n.course_program IS NOT NULL AND TRIM(n.course_program) <> ''
           AND NOT EXISTS (SELECT 1 FROM note_course_program x
                           JOIN course_programs c ON c.id = x.course_program_id
                           WHERE x.note_id = n.id AND c.name = n.course_program)
       ) AS already_diverged
FROM notes n JOIN users u ON u.id = n.owner_user_id
WHERE EXISTS (SELECT 1 FROM note_course_program ncp WHERE ncp.note_id = n.id)
  AND u.role <> 'ADMIN' AND u.profile_type IS DISTINCT FROM 'TEACHER';
```

`already_diverged` counts notes **broken today**. If it is 0, this is purely preventive and the cheapest
adequate option probably wins. If it is large, the case for a corrective action strengthens.

Production scale for calibration: 364 accounts, 218 with a program value, ~179 concentrated on four programs
(Education, Accountancy, Nursing, Architecture). This is a small product, not a large one.

## Questions

1. **Which option would you take, and why?** Rank at least your top two.
2. **Is Option 1 faithful to the ruling, or am I bending it?** The ruling says ownership must not change
   semantics. Option 1 does not change read semantics at all — it changes *who can edit*. Is that a real
   distinction or a rationalization?
3. **Is Option 5 the principled answer that Option 1 only approximates?** Provenance, not ownership, is the
   actual discriminator. Does that justify a schema change on a small product, or is it architecture for its
   own sake?
4. **Does the answer change** if `already_diverged` comes back as 0 versus 50? Say what each would imply.
5. **Anything I have framed wrongly.** In particular: is "two populations" a real distinction, or am I
   inventing a category to avoid choosing between "keep all rows" and "delete some rows"?

Please push back where you disagree. I would rather be told the framing is wrong than get agreement with it.
