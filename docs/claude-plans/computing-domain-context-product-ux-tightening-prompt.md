# Handoff prompt — Product UX GPT second opinion on the Computing Domain Context decision

**How to use:** paste everything below the line, followed immediately by the full contents of
`docs/claude-plans/computing-domain-context-decision.md`, into GPT. Together they are self-contained —
GPT does not need repo access.

---

You are reviewing a **product-architecture decision recommendation** for NoteLib, a notes-first study
product (learners capture Notes → an LLM generates Study Packs → they practice with quizzes). The
attached document answers a specific question: does NoteLib need a 13th "Domain Context" value —
`Computing` — to correctly author foundational BS Computer Science material, and if so, what should it
mean and how should it be introduced? The document was produced by a 5-pass cold-context repo+production
audit, an Opus product-UX synthesis pass, and then a follow-up verification pass (2026-09-30) that
checked every fact the synthesis had flagged as unconfirmed against real sources and the live database.
Nothing has been implemented. No ADR text has been written yet — only a description of what one would
contain.

## Your job, and its boundaries

**Give a second opinion on the product/UX judgment calls, and tighten the document — not the verified
facts.** That is the whole task.

**Do NOT:**
- re-derive or second-guess the verified facts — the ACM/IEEE curriculum claim, the CHED CMO claim, the
  catalog-program check, and the 24-note title read were all checked against real sources or a live
  production database you cannot see, and re-litigating them blind produces confident noise, not signal;
- propose implementation details, schema, prompt text, or code;
- silently accept the recommendation's central move (reopening `Computing`) without engaging the single
  biggest risk it names against itself — see below;
- restate the document back to us, or write an essay. We have already read it.

**One narrow exception:** if you think a *verified* fact actually undercuts a conclusion the document
draws from it — not "is this fact true" but "does this fact actually support the claim built on it" —
say so. That is legitimate second-guessing of reasoning, distinct from re-opening facts you can't check.

---

## Product context you need

**NoteLib**'s canonical unit is the Note — one piece of knowledge, reusable across many Subject Plans and
degree programs without duplication. Four metadata axes govern each Note: **Subject** (what), **Domain
Context** (how it's authored — the only signal the LLM generation prompt actually receives; a closed set
of exactly 12 ratified values today, governed by a binding architecture decision record, ADR-001), **Note
Learner Level / Authored Depth** (how deep), and **Applicable Programs** (where it's discoverable —
never reaches the generation prompt). A Note tagged to 2+ Applicable Programs must have a Domain Context
set, or Study Pack generation is refused server-side — this is treated as ADR-001's founding reason the
axis exists at all, not an incidental rule.

NoteLib is about to author its first Computer Science degree material (BS Computer Science Year 1). The
production audit found the existing taxonomy has no honest Domain Context for shared computing knowledge
(Introduction to Computing, Programming Fundamentals, Data Structures, Discrete Structures, etc.) that
will legitimately span BSCS, BSIT, Information Systems, and Software Engineering. The strategist authoring
the current BSCS proposal correctly refused to fake a value, and 70% of that proposal (167 of 240 rows)
is sitting on a placeholder today as a result — this is a live, active blocker, not a hypothetical one.

## Established (treat as given — verified facts, not up for debate)

- **The taxonomy gap is real**, and the multi-program validation rule is not the cause — it's what
  exposed the gap. Relaxing that rule is explicitly rejected (it would require either sending a program
  list to the model, which ADR-001 already rejected on its own generation-quality grounds, or an
  accidental fallback domain).
- **`Computing` was already proposed and rejected once**, on 2026-09-04, on naming-rule grounds (judged
  "invented" rather than borrowed from real curriculum vocabulary) — but that rejection is thinly
  evidenced (no date-matched note count, no sibling-program test, unlike every other ratified/rejected
  value in the ADR).
- **`Computer Science` as a name is rejected** — it would exactly repeat a prior, deliberate rejection:
  `Architecture` was rejected as a Domain Context despite 837 notes, specifically because it served only
  its own program with no demonstrated sibling-program sharing. A program-named value fails this
  regardless of note volume.
- **`Computing` is now confirmed to be genuinely borrowed vocabulary**, not invented: it's the real
  umbrella term of the ACM/IEEE *Computing Curricula 2020* framework (covering exactly Computer Science,
  Computer Engineering, Information Systems, Information Technology, Software Engineering), and
  "Introduction to Computing" is a real, literal shared first-year course title across BSCS/BSIT/BSIS
  under a real Philippine CHED curriculum memorandum (CMO No. 25, s. 2015).
- **`Computing` does not collide with any live catalog program name** — confirmed by a direct database
  read — and would serve four confirmed live sibling programs (CS, IT, Information Systems, Software
  Engineering), a stronger position than two of the three most recently ratified Domain Context values,
  which each serve only one live program.
- **The recommendation's original "live evidence" turned out to be wrong, and was corrected, not
  hidden**: 24 notes that looked like a possible existing misclassification (IT-tagged, already assigned
  a different Domain Context) were read by title and confirmed to be generic engineering-project-management
  board-review content — not computing at all. **This means the case for `Computing` now rests entirely
  on "firmly planned" curriculum rows that don't exist as authored notes yet, not on any already-live
  note.** This is stated plainly in the document, not softened.
- **Setting Domain Context on an existing note is safe** (no auto-regeneration, no effect on already-served
  content). **Setting Authored Depth is not** — it can silently invalidate an already-generated exam pool
  with no curator confirmation. These must never be changed in the same pass.
- **No bulk metadata-editing endpoint exists** — every affected note is a one-at-a-time curator edit.
- Two other catalog "programs" (Computer Science, Computer Engineering) are already populated with
  generic, non-computing content — a separate, adjacent discovery-tagging problem, explicitly **not** to
  be solved via a Domain Context change.

## Fenced off (do not re-open)

- Whether the multi-program validation rule itself should be relaxed or removed — rejected on both
  technical-design and product-governance grounds.
- Whether `Computer Science` (or any other live-program-named value) should be used instead of `Computing`
  — rejected via the direct `Architecture` precedent.
- Whether the underlying four-axis Note metadata model itself should change — out of scope entirely.
- The `quantitative = false` setting for the proposed value — reasoned from the enum's own existing
  documented convention (a `true` flag is permanent per note since Study Packs never auto-regenerate).

---

## What we actually want your judgment on

1. **The central risk, stated by the document against its own recommendation**: the shared-treatment
   evidence for `Computing` is now entirely "firmly planned" (167 curriculum rows), not a single
   already-authored note, after the one piece of live evidence was found to be wrong. Is a dated
   `[CHECKPOINT]` review (re-count `Computing`-tagged notes across live programs after BSCS Year 1 is
   authored, re-open the value if it turns out single-program) sufficient protection against `Computing`
   quietly becoming a `Computer Science` mirror in practice — the exact `Architecture` failure this
   proposal is trying to avoid? Or does ratifying now, before ANY note is actually authored under it,
   need a stronger gate (e.g., a smaller pilot batch of notes authored and reviewed before the ADR
   amendment is finalized, rather than after)?

2. **Reopening a previously-rejected name through a formal governance amendment, rather than treating
   it as untouched territory** — is quoting-and-superseding the 2026-09-04 rejection (rather than, say,
   treating it as stale and simply not mentioning it) the right level of process rigor here, or is this
   over-engineering a naming decision? Give your honest read: does the governance process itself risk
   becoming precious about a word choice at the expense of shipping BSCS Year 1?

3. **The inclusion/exclusion boundary test** (§4 of the attached document: "is computation, information,
   or software itself the object of study, treated the same regardless of which computing program is
   reading?"). Read the worked examples table. Is this test actually usable by a curator in the moment of
   tagging a note, or does it require more curriculum-domain judgment than a fast authoring workflow can
   sustain? Flag any worked example you think is wrong or genuinely ambiguous, and say what a curator
   should do when they're unsure.

4. **The urgency framing (§9)**: the document says the "Introduction to Computing"-style rows (already
   3-program) cannot proceed under any interim workaround without generating permanently CS-framed
   content. Is there a genuinely better interim option than "wait for the ADR decision" that the document
   missed — or is waiting really the only honest choice here?

5. **Anything in the document's tone or structure** that would slow an owner down from making this
   decision quickly. This has already been through one verification pass and is fact-dense — tell us if
   it's now harder to act on than it needs to be, and what you'd cut or restructure.

---

## Output shape

1. **A direct verdict on question 1** (the biggest risk) — this is the one thing that could change
   whether the recommendation ships as-is or needs a harder gate first.
2. **Per remaining question (2-5):** your recommendation in one line, then reasoning. Agree-and-move-on
   is a fine answer where you agree.
3. **Any place a verified fact is being asked to support more than it actually supports** (the one narrow
   exception above), clearly separated from your main judgment.
4. Keep it tight. Prose only where a table won't do.
