# Removing the Primary Course / Program — consultation prompt for product UX GPT

Paste everything below the line. Written in the owner's voice, self-contained — no NoteLib context assumed.

**Four things to watch in the reply:**

1. **The challenges in this prompt are Claude's, not yours.** The prompt says so explicitly and asks GPT to argue against them. If GPT simply agrees with the objections, push back once — the proposal to remove Primary is *yours*, and it deserves a real defence rather than a chorus.
2. **The `55 of 92` figure is from a LOCAL dev database and is not evidence about production.** The prompt flags this. If GPT reasons from it as though it were production, discount that part of the answer. The production read (`19-slice-2-facet-equivalence-impact.sql`, query A) has still not been run.
3. **Decision 1 is the one with a real technical constraint behind it**, not a preference — a prompt instruction that says "treat the domain above as *the* authoritative academic domain … do not blend unrelated disciplines" cannot be satisfied by a list of three. Watch for an answer that hand-waves this as "the model will figure it out."
4. **GPT cannot verify what an LLM will do with a list of programs.** Neither can I without running the test. Answers about generation quality are hypotheses; hold them to that.

---

# NoteLib — should we delete the "Primary" program concept?

I want a second opinion on an architectural simplification I proposed, and specifically on two objections my engineer raised against it. Treat this as a **product architecture discussion**, not an implementation request.

## Context you need

NoteLib is a notes-first study workspace for Philippine learners, with a strong board-exam-review segment. Users capture **Notes**; each Note can generate an AI Study Pack (summary, key concepts, flashcards, practice quizzes). We also publish curated **Official Review Sets** that assemble Notes into a study journey.

### The architecture, which is decided and not up for re-litigation

A Note used to carry one free-text `Course / Program` field doing five jobs at once. We split it into four independent axes:

| Axis | Cardinality | Owns |
|---|---|---|
| **Subject** | one | *what* the note is about (Algebra) |
| **Domain Context** | one | ***how* it is authored** — the only domain constraint sent to the LLM |
| **Note Learner Level** | one | *how deep* it is authored |
| **Applicable Programs** | **many** | ***where* it appears** — discovery only, never sent to the LLM |

The motivating case: one Algebra note is applicable to many engineering programs. Under one-program-per-note we would duplicate it per program, so authoring was halted rather than create that duplication. Applicable Programs now works — notes carry explicit program rows, curators add and remove them, and library filters, facets, and search read them.

### The one piece of history that matters for this decision

There is a curated **catalog** of 21 programs. It deliberately **excludes** a set of values that exist as free text on real notes — bare school levels ("Grade School"), bare subjects ("Biology"), a family name ("Engineering"), and two programs excluded on an explicit ruling because they had no real curriculum behind them ("Computer Science", "Software Engineering").

**A note whose program value is excluded gets no structured program row at all.** It keeps its legacy free-text string, and that string is what still makes it filterable, searchable, and reachable by its public shareable URL. This was deliberate, and it is load-bearing.

## What I proposed

**Delete the concept of a "Primary" Course / Program.** Today a Note has a legacy single `Course / Program` field *plus* many Applicable Programs, and the first value is auto-copied into the second. That is overlap, and overlap is exactly what the four-axis split was meant to remove.

A note does not "belong" to one program anymore. It applies to one or many. So:

- one picker, labelled `Course / Program(s)`, with helper text explaining that adding several programs lets one note serve several curricula instead of creating duplicates;
- no separate "Applicable Programs" section anywhere;
- the many-to-many table becomes the single source of truth, and **the legacy column is dropped**;
- Note Detail stops showing a "primary" program, and instead says something like *"Applicable to 6 programs"* with a way to see the full list — including on public note pages, where a learner arriving from a shared link may genuinely want to know whether a note fits their program;
- for AI generation, precedence becomes **Domain Context if set, otherwise the full program list** — I reasoned that showing the model all three engineering programs would encourage it to author shared Engineering Mathematics rather than over-specialising to one discipline.

## The two objections raised against it

**I want you to evaluate these, not assume they are correct.** They came from my engineer, who also wrote the original architecture and may be over-attached to it. Argue against them if they do not hold.

### Objection 1 — sending a list to the LLM breaks the instruction it depends on

The generation prompt contains, verbatim:

> "treat the domain above as **the** authoritative academic domain. All content, terminology, examples, and question framing **must belong to that domain**. Do not blend in material from unrelated disciplines."

The claim is that feeding `Civil Engineering, Mechanical Engineering, Electrical Engineering` into that line makes it self-contradictory — it names three disciplines while forbidding blending across disciplines. The claim is also that this is not a new observation: the unsatisfiability of that instruction under many-valued programs is recorded as the *founding reason* Domain Context was created as a separate single-valued axis.

We have a verification (called R4) showing that a deliberately *broader single* Domain Context does not degrade generated content. It says nothing about a *list*.

The proposed alternative: **require Domain Context whenever a note has more than one program**, enforced when saving rather than hoped for in the prompt. Single-program notes keep today's single-value fallback unchanged.

**Question for you:** is requiring Domain Context on multi-program notes the right call, or is it authoring friction we should avoid — and is there a third option neither of us has considered? Note that Domain Context is a closed, curated set of 8 values and adding to it is treated as an architectural decision, not routine authoring.

### Objection 2 — dropping the legacy column deletes program metadata for a large set of notes

Because catalog-excluded values have no structured row, dropping the legacy column cannot migrate them — there is nothing to point at. On a **local development database** the split is **55 notes with an excluded value against 37 with a catalog match**. *That is a dev database and is explicitly not evidence about production; the production read has not been run yet.* But the structural point holds regardless of the ratio.

So dropping the column forces one of three outcomes:

1. **Seed catalog entries for every excluded value** — which reverses the earlier rulings and re-admits bare school levels, bare subjects, and a family name as though they were programs.
2. **Let those notes lose their program metadata** — they immediately lose filtering, search, and their public shareable URLs, all of which work today.
3. **Keep the column** as an internal compatibility field for notes the catalog cannot represent.

The counter-proposal is option 3 plus everything else I asked for: remove "Primary" from the UI, the docs, and the mental model; one picker; one program concept everywhere a human looks; and demote the legacy column to an invisible compatibility field rather than a peer axis. The argument is that my objection is about *concept overlap*, which is a presentation and model problem, and that it can be fixed completely without an irreversible migration that deletes metadata.

**Question for you:** is "one field in every human-facing surface, one hidden compatibility column underneath" an honest simplification, or is it the kind of half-measure that leaves a confusing model in place and quietly rots? I am genuinely torn. I do not want to preserve the old concept out of familiarity, but I also do not want to destroy data to satisfy tidiness.

## Three decisions I need to make

1. **Domain Context required on multi-program notes**, or send the full list and verify generation quality first?
2. **Concept-only simplification now** (one picker, hidden compatibility column), or commit to dropping the column and accept one of the three outcomes?
3. If the column is dropped, what is the honest treatment of notes carrying excluded values — seed them, retire them, or something else?

## One UI question, separate from the above

On a library card, a note that applies to several programs currently shows a count (`Applies to 6 programs`) rather than names. My engineer argues names on a card read as a second identity next to the Subject badge, are largely redundant when the learner has already filtered by program, and force an arbitrary choice about which names to truncate. I suggested something like `Civil Engineering • Mechanical Engineering • +2`.

On the Note Detail page the proposal is a single line — *"Applicable to 6 programs"* — that opens a popover or bottom sheet with the full list, on both private and public note pages.

**Question:** is count-plus-on-demand-list the right pattern for both surfaces, or should cards show names? What would you do differently?

## What I want back

1. A recommendation on each of the three decisions, with reasoning I can disagree with — not a summary of options.
2. **Explicitly separate** what you are confident about as product/UX judgment from what depends on facts neither of us has verified (production data, and how an LLM actually behaves given a list of programs).
3. If you think the objections are wrong, say so directly and explain why — I would rather have my proposal defended properly than politely narrowed.
4. Anything I have not asked that this decision depends on.
