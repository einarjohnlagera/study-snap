# Decision A (pressure-test finding B2) — consultation prompt for a second opinion

**How to use this:** paste everything below the horizontal rule into a fresh session, ideally one framed around
product/UX judgement rather than implementation. It is self-contained — data model, the bug, the rulings that
constrain it, the option space, and the one question that is actually still open.

## ⛔ CLOSED 2026-08-06 — DECIDED WITHOUT SENDING. Do not send this prompt.

**The owner locked Option 7** and stated the doctrine more precisely than this prompt did. The ratified rule
lives in `ADR-001` → *"Representation authority: what may author an Applicable Program row"*:

> A learner's personal free-text Course / Program must not be mechanically materialized into a catalog
> Applicable Program row.

**The owner's correction to my framing matters and is why this prompt should not be reused as written.** I posed
Option 7 as "learner-owned notes should not have join rows." That is too broad and would contradict copy
inheritance and the standing ruling. The rule is about **provenance**, not ownership: learner-owned notes may
carry join rows when those rows have legitimate curated provenance. Read semantics stay identical for every
note — joined programs first when they exist, otherwise the personal string — so Option 7 never makes the same
metadata mean different things depending on ownership.

**Option 3 was rejected** on the ground I had under-weighted even after adding its "For" case: it cannot reliably
distinguish a mechanically derived row from an inherited curator-authored row when both match the learner's old
string, so it can silently delete valid inherited applicability. The discovery parity it preserves does not
justify that, because a learner note served by the personal-string fallback is a canonical supported shape, not
a degraded one.

**Zero affected users was ruled to strengthen prevention, not weaken it** — question 6's reading (b) was
rejected: zero is not a licence to ship a deterministic divergence mechanism and monitor the harm.

Everything below is retained as the reasoning record. Send it only if this decision is genuinely reopened, and
fix the Option 7 framing first.

---

## UN-GATED 2026-08-06. The measurement gate is closed; the question changed shape.

This prompt was previously gated on running `docs/claude-plans/b2-migration-artifact-sizing.sql` against
production. **That query cannot be run and no longer needs to be.** Production has never received the
`note_course_program` table — the migration that creates it (`V107`) ships with this unreleased version, and
production is still on the previous release, which stopped at `V106`.

The gate metric was `backfill_derived_AND_diverged`: learner notes where the migration created a join row **and**
the learner has since edited their program string so it no longer matches. That requires the migration to have
run first. It hasn't. **The count is 0 by construction, not by estimate**, which lands on the owner's pre-set
threshold: *0 diverged → prevent future divergence and document; no UI.*

**What that changes, and why a second opinion is still worth having.** Zero damage does not mean nothing to
decide. The divergence *mechanism* goes live the moment this release deploys, and — because the migration has not
run anywhere in production yet — there is a **one-time window to prevent the artifact from ever existing**,
rather than compensating for it afterwards. That window closes at deploy. It also puts a genuinely new option on
the table (Option 7) that did not exist when this prompt was first drafted, and that option is the one carrying
the real tension with a standing ruling.

**Two things previously settled that remain settled** (a reviewer may challenge them, but they are not the ask):

1. **Option 1 (learner visibility + removal) is REJECTED by the owner.** Their reasoning: *"The core problem is
   that the migration created metadata that no learner intentionally authored. That is a migration problem
   before it is a UX problem. Exposing Applicable Programs to learners merely to repair migration artifacts
   would leak the curator publishing model into personal note authoring."*
2. **Provenance, not ownership, is the discriminator** — curated, inherited and migration-generated rows are
   fundamentally different kinds of data, and migration-generated rows are **derived data, not user-authored
   data**.

**What I want from the second opinion:** a recommendation on the preventive shape, and — more importantly — an
honest challenge to whether my preferred option (7) is faithful to the ruling it appears to sit closest to, or
whether it is the ruling's clearest violation wearing better clothes.

**Companion documents** (do not paste; reference if asked):
`docs/claude-findings/v0.71.0-pre-signoff-pressure-test.md` (finding B2 in full),
`docs/architecture/ADR-001-canonical-knowledge-architecture.md` (the binding architecture decision),
`docs/claude-plans/onboarding-activation-and-intent-router.md` ("Open decision A").

---

# NoteLib — a migration is about to create metadata that its owner cannot edit

I need a second opinion on a data-model decision, with one unusual property: **the bug has not happened yet.**
The migration that causes it is written, tested, and sitting on an unreleased branch. I can still change what it
does. I want to know whether the fix I prefer is principled or merely convenient.

## The product

NoteLib is a study app. Users write **notes**; AI turns a note into a **Study Pack** (summary, key concepts,
quizzes). Notes carry metadata on four axes. The one that matters here is **Course / Program(s)** — which
academic programs a note applies to. It drives two things:

- **Discovery** — library filters, facet counts, public search, shareable URLs
- **Generation** — it resolves the "authoritative academic domain" the LLM is told to write within

It is a small product: 364 accounts, 218 with a program value, ~179 concentrated on four programs (Education,
Accountancy, Nursing, Architecture).

## The data model, which is decided and not up for discussion

A recent architecture decision (ADR-001) made program applicability **many-to-many**, so one canonical note can
serve several programs instead of being duplicated per program. That produced **two storage locations**:

| | Who authors it | Storage | Shape |
|---|---|---|---|
| **Personal program** | Learners (ordinary users) | `notes.course_program`, a free-text string | Free text, **exactly one** |
| **Applicable Programs** | Curators (Teacher/Admin) | `note_course_program` join table → a 21-entry curated catalog | Catalog ids, **one or many** |

Four things are ratified and must not be reversed:

1. The two modes are a **product distinction, not a permission tier**. Multi-program authoring is a curation
   act. Learners are not "lesser users" — they are doing a different job.
2. `notes.course_program` is **the personal-notes program field**, permanently. It is *not* a legacy column
   awaiting removal. NoteLib is a notebook, not a closed LMS, so learners must be able to type a program the
   catalog does not contain.
3. Reads are **join-first with a personal-string fallback**:
   `EXISTS(join rows matching) OR (note has NO join rows AND personal string matches)`.
4. Generation resolves the domain as: explicit Domain Context → **exactly one** joined catalog name →
   `notes.course_program` → the user's profile program. A list of programs may never reach the prompt.

## The bug, stated in the future tense

The migration that creates the join table **backfills one row per note** whose program string matches the
catalog. It has **no owner filter**, so notes owned by ordinary learners will get join rows too.

Because reads are join-first, and because a learner update deliberately does not touch join rows, this will
happen from deploy day onward:

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
which the unfiltered backfill would make false.

**Nobody is broken today.** The affected count is exactly zero, and stays zero until this release deploys.

## The ruling that constrains everything

The owner ruled:

> Keep learner-owned Applicable Program rows. Do not clear join rows on learner updates. Validation should
> operate against the stored Applicable Program rows. Course / Program(s) is one of the four architectural axes
> and should have the same meaning regardless of ownership. **I don't want ownership changing the semantics of
> the metadata model.**

That ruling closed a separate defect (a validation rule read the incoming request instead of the stored rows;
now fixed and shipped). It does **not** address the staleness above.

## Why this is genuinely hard

There are **two populations** of learner-owned join rows, distinguished by **provenance**, not ownership:

1. **Inherited** — a learner copies a curated multi-program note; the copy inherits the curator's rows. Real,
   authored, valuable. Copy-inheritance exists specifically to preserve them.
2. **Backfilled** — the migration derives a row mechanically from the learner's own string at a moment in time.
   **Nobody authored these.** They are the artifact that causes the bug.

A timing fact that matters: **at the instant the migration runs, population 2 is the only one that exists.**
Copy-inheritance of join rows is itself new in this release, so no inherited rows can predate the migration.
The two populations are perfectly separable at deploy time and become entangled immediately afterwards.

The ruling's principle is about **ownership**. The distinction that actually matters is **provenance**. The
honest question is whether a fix that discriminates by provenance respects the ruling or quietly violates it.

## The options

### Option 1 — Give learners visibility and removal on their own notes — **REJECTED by the owner**
*Retained so a reviewer can disagree with the rejection.* Learners see their note's Applicable Programs and can
remove entries, but cannot multi-add from the catalog. *Against:* new UI in a release already carrying five
blockers, and it teaches learners a concept the two-mode model deliberately kept away from them.

### Option 2 — Corrective migration, after the fact
Delete backfill-created rows on learner-owned notes once they exist. *Against:* it is strictly worse than
Option 7 now — same effect, but only after a window in which real divergence can accrue, and by then the two
populations are entangled so it needs a heuristic (`created_at` clustering) to tell them apart.

### Option 3 — Re-derive on learner save, surgically *(the live alternative to Option 7 — see question 3)*
When a learner changes their program string, remove only the join rows matching the *old* string, keeping the
rest.

*For:* it is the only option that keeps the backfill's genuine benefit — a learner note whose program **is** in
the catalog gets a real catalog join row, so it participates in join-based discovery on equal footing with
curated content, rather than relying on string matching. Option 7 gives that up for every learner note. It is
also self-healing rather than one-shot: it stays correct for rows created by any future mechanism, whereas
Option 7 fixes only what this particular migration does and would need re-deciding if another backfill is ever
written. And it does not make row existence depend on ownership, so it sidesteps question 2 entirely.

*Against:* a previous version of this (which cleared *all* rows) was a shipped bug that silently destroyed
inherited curated programs. The surgical version still misfires when an inherited row coincidentally matches
the old string — a Nursing learner who copied a curated Nursing note, edits their own program, and loses the
curator's row. It also adds permanent logic to the learner save path, which ADR-001 deliberately keeps simple.

### Option 4 — Make the personal string win for learner-owned notes
Change read precedence based on who owns the note. *Against:* this is precisely "ownership changing the
semantics of the metadata model". Included because it is the obvious fix someone will propose.

### Option 5 — Add a provenance column
Add `source` to the join table (`BACKFILL` / `CURATED` / `INHERITED`) and make behaviour depend on how the row
got there. *For:* engages the ruling's principle head-on. *Against:* a schema change plus a backfill of the
backfill, to distinguish a population that would be empty under Option 7. Hard to justify at zero.

### Option 6 — Do nothing; document as a known limitation
*For:* zero risk. *Against:* the count starts at zero and grows with every learner program edit, forever.

### Option 7 — **Prevent it: never create the rows.** *(new; only possible before deploy — my preference)*
Exclude learner-owned notes from the backfill, so the artifact is never created. Mechanically this is a new
migration that deletes learner-owned backfill rows immediately after the backfill runs, rather than editing the
existing migration (editing it would break migration checksums in every environment where it has already run).

A learner note then carries **no join rows**, and the ratified read rule (join-first, personal-string fallback)
serves it from `notes.course_program` — which is the field ADR-001 says is *the* personal-notes program field.
Their edits work permanently. Curator notes are untouched. Copy-inheritance is untouched, because inherited rows
do not exist yet at migration time and are authored by a curator when they do appear.

*For:* deletes the problem class instead of adding compensating logic to a surface ADR-001 deliberately keeps
simple. No UI, no schema change, no heuristic, no ongoing complexity, and no window during which users are
harmed. It also makes the existing code comment (*"a learner never authors them"*) true again.

*Against, and this is the crux:* it arguably **is** ownership changing the model — whether a note gets join rows
would depend on who owns it. My defence is that it changes *provenance of rows*, not *read semantics*: the read
rule stays literally identical for every note, and a learner note with no join rows resolves exactly as ADR-001
intends. But I am the one who benefits from that distinction being accepted, so I do not trust myself on it.

## Questions

1. **Which option would you take, and why?** Rank at least your top two.
2. **Is Option 7 faithful to "ownership must not change the semantics of the metadata model", or is it the
   clearest violation of it?** I claim it changes which rows exist, not what rows mean. Is that a real
   distinction or a rationalization? This is the question I most want challenged.
3. **Option 7 versus Option 3, head to head — this is the actual decision on the table.** Both prevent the
   staleness; they differ in what they give up. Option 7 says a learner note should simply not have join rows,
   and accepts that a learner note whose program *is* in the catalog loses join-based discovery and is served by
   string matching instead. Option 3 keeps that discovery parity and stays correct for future row sources, and
   pays for it with permanent logic on the learner save path plus a known misfire when an inherited row matches
   the old string. **Which trade is right, and am I undervaluing what Option 7 discards?**
   One concrete downstream difference: a separate fix (making note-copying work at all) is queued behind this
   decision, because copying propagates whatever join rows a note carries. Under Option 7 a copied learner note
   propagates nothing; under Option 3 it propagates a real catalog row. That may be an argument *for* Option 3
   that I have been treating as neutral.
4. **Is "prevent rather than repair" worth the constraint it creates?** Option 7 is only available before
   deploy. Does taking it now trade a real future flexibility for a one-time convenience — e.g. does a learner
   note ever *want* catalog join rows later, and would we have made that harder?
5. **Is the "two populations" framing real**, or am I inventing a category to avoid choosing between "keep all
   rows" and "delete some rows"?
6. **Does zero-affected-users change the decision, or only its urgency?** Argue both readings: (a) zero means
   take the cheap preventive option and move on; (b) zero means there is no evidence of a real problem and the
   right move is Option 6 plus a monitor.
7. **Anything I have framed wrongly.**

Please push back where you disagree. I would rather be told the framing is wrong than get agreement with it.
