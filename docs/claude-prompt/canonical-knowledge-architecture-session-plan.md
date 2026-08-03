# Canonical Knowledge Architecture — Session Plan

**Status: planning only, nothing authorized for implementation. No release has been kicked off for this.**

## Why this exists

While expanding the Official Library beyond ALE/PNLE/LET/CPALE into Civil Engineering, the owner hit a wall that is architectural rather than editorial: `notes.course_program` is single-valued, and it is simultaneously (a) the LLM's authoritative domain constraint, (b) the private Library filter facet, (c) the Public Library filter facet, (d) the Exam Hub mapping key, and (e) the note card badge. A shared foundational subject (Algebra, Physics, Statistics) applicable to eleven engineering programs therefore appears to require eleven notes — and with them eleven Study Packs, question pools, flashcard sets, memorization decks, public copies, and maintenance obligations.

The owner's proposal (relayed from a product/UX GPT discussion, reproduced in full in `01`'s appendix reference) is to replace single-valued Course/Program with:

- **Applicable Programs** (many) — where a note appears
- **Content Context** (one) — how a note is authored
- **Note Learner Level** (one) — how deep a note is authored, outranking the user's own level
- **Program Families** — an authoring shortcut that expands into explicit program rows

The explicit instruction was: critique, audit, and plan — do not implement. And explicitly: *"Do not optimize this architecture primarily for theoretical normalization. Optimize it for curriculum authoring velocity while preserving canonical knowledge."*

## What was done

A direct code audit of every place `course_program` and learner level influence behavior — 59 backend main-source files (194 occurrences), 40 frontend non-test files, 64 test files — reading the actual code rather than the feature docs, since several of the load-bearing facts (the prompt's Domain constraint wording, the deliberate exclusion of learner level from static content, the pool/bank keying on *user* level, the absence of any course-program catalog table) are not written down anywhere in `docs/`.

## Output

- `canonical-knowledge-architecture-out/01-architecture-critique-and-migration-plan.md` — the full deliverable: critique, gaps, risks, alternatives considered, recommended architecture, 4-step sequencing, the 14-item migration inventory, required ROADMAP/GPT_CONTEXT changes, and a measurable success metric with a falsifiable kill criterion.
- `canonical-knowledge-architecture-out/02-adr-draft.md` — draft ADR, to move to `docs/architecture/ADR-001-canonical-knowledge-architecture.md` **on ratification only**.

## Headline conclusions

1. **The direction is right and the code proves it.** `OpenAiLlmStudyPackService.java:1536-1537` instructs the model to "treat the course/program above as the authoritative academic domain… Do not blend in material from unrelated disciplines." That instruction is *unsatisfiable* if the field holds eleven programs. Content Context is not polish — it is the load-bearing piece.
2. **This is three initiatives fused into one, with very different cost and reversibility.** Content Context is one additive nullable column and reversible. The many-to-many join is not withdrawable once badges and filters read it — which puts it in direct conflict with the ROADMAP's own bootstrap-test clause 2. It cannot ship as one release.
3. **Content Context alone unblocks the stated goal.** Review Sets already compose notes freely by explicit reference, so a curator can author one canonical "Engineering Foundation / Algebra" note and add it to eleven engineering Review Sets *without* the join table existing. The many-to-many buys discovery, filtering, and SEO reach — real value, but not the authoring-velocity unblock.
4. **The migration premise is wrong in a way that matters.** There is no `course_programs` table. `notes.course_program` is free-text `VARCHAR(120)` with no validation on the write path, and an LLM suggestion can write into it. The backfill is a vocabulary-reconciliation project, not an `INSERT … SELECT`.
5. **Note Learner Level reverses an explicit, deliberate current rule** (`:1540-1541`: "Do not use learner level to calibrate static note or Study Pack content") and requires re-keying two existing pool tables that key on *user* level today.
