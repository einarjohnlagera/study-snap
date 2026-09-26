# Handoff prompt — Product UX GPT tightening pass on Degree Study Journeys Stage 1

**How to use:** paste everything below the line, followed immediately by the full contents of
`docs/claude-plans/degree-study-journeys-stage1-architecture-audit.md`, into GPT. Together they are
self-contained — GPT does not need repo access.

---

You are reviewing a **Stage 1 architecture + product UX audit** for NoteLib, a notes-first study product
(learners capture Notes → generate AI Study Packs → practice with quizzes). The audit was produced by a
5-pass cold-context code audit (every architectural claim anchored to `file:line` in the real source) feeding
a synthesis pass that was explicitly instructed to challenge, not just validate, the team's own original
hypothesis. Nothing has been implemented. No code has been written. This is a decision-ready brief the owner
will use to green-light Phase A.

## Your job, and its boundaries

**Tighten the product/UX judgment calls in the attached document — not the architecture.** That is the whole
task.

**Do NOT:**
- re-derive, second-guess, or propose alternatives to the persistence model (2 persisted collection levels,
  Term as two nullable columns, Degree as non-persisted in Phase A) — it is anchored to `file:line` facts
  about the real codebase that you cannot see, and re-litigating it blind produces confident noise, not signal;
- propose implementation details, schema, component code, or library APIs — this is a product-UX pass;
- silently redesign around a code constraint stated in the document — if you think a stated code fact
  actually changes a UX recommendation, say so explicitly and separately, don't just quietly write around it;
- restate the plan back to us, or write an essay. We have already read it.

**One narrow exception:** if you spot something in the UX sections that implies a *product* requirement the
architecture doesn't actually support (e.g. a recommendation that would only make sense if Subject Plans
*were* shared across degrees, when the plan says they explicitly are not) — flag that contradiction. That is
a legitimate "this doesn't hang together" catch, distinct from re-opening the architecture itself.

---

## Product context you need

**NoteLib** — Philippine-market notes-first study workspace. Existing usage is board-exam review (PNLE,
CPALE, ALE, LET, etc.): a **Review Set** (e.g. "PNLE Comprehensive Review") contains **Subject Plans** (e.g.
"Adult Health Nursing"), each holding Notes grouped into **Sections**. This is well-established, working
product. **Degree Study Journeys** is new: the first one is **BS Computer Science**, structured as
Degree → Year → Academic Term → Subject → Section → Note. The team's original working hypothesis (in the
consultation document the audit responded to) was that this maps to: Degree = discovery container, Year =
adoptable Study Plan, Subject = Subject Plan, Term = placement metadata, Section = unchanged. The audit
mostly confirmed this but overturned two pieces of it — see below.

## Established (treat as given — code facts and settled architecture, not up for debate)

- Collection hierarchy stays at exactly **2 persisted levels**: Year (root) and Subject (child) — this cap is
  independently enforced in three separate parts of the real codebase and is being formalized as a binding
  ADR, not a style preference.
- **Academic Term** = two nullable fields (`term_label` free text, `term_order` integer) on the Subject's own
  row. Not an entity, not addressable, not adoptable, not a progress-bearing thing.
- **Section** stays exactly as it works today: a free-text grouping label on a Note, computed at render time.
  No change.
- **Degree** (BS Computer Science) is **editorial-only in Phase A** — a naming convention, nothing persisted,
  no page. In a later phase it becomes a small catalog entity that references Year Study Plans by ID; it is
  **never** a collection and **never** carries progress or adoption state of its own.
- **Degree-level progress is never computed, in any phase.** The Degree surface (whenever it exists) shows
  per-year status only (`Not started` / `N% ready` / `Coming soon`) — never a single blended percentage. This
  is treated as a firm product commitment, not a temporary engineering shortcut.
- **Canonical Notes already reuse cleanly** across Subject Plans, Review Sets, and Degree Journeys — solved,
  no work needed.
- **Canonical Subject Plans do NOT reuse across Degree Journeys** — this is the audit's central disagreement
  with the team's own original assumption. "Programming Fundamentals I" in BS Computer Science and in a future
  BS Information Technology journey would be two independently-authored shells (title, description, section
  breakdown, term placement) pointing at the *same* underlying canonical Notes. No knowledge is duplicated;
  the curation *shell* is duplicated per degree.
- **Adoption:** Year is the primary adoptable unit (already fully working). Whole-Degree adoption is
  recommended against, permanently, in every phase. Individual-Subject adoption is left open pending one
  non-UX code-verification step.
- **Curriculum customization** (remove/move/reorder a Subject within an adopted Year) is explicitly deferred
  past the first release, until real adopters exist to inform the design.
- **Learner-facing positioning:** NoteLib publishes a "Reference Study Journey," not an authoritative claim
  about any specific school's actual curriculum — Philippine HEIs vary substantially in sequencing and term
  structure.

## Fenced off (do not re-open)

- Whether Degree/Year/Subject should be 2, 3, or N persisted levels.
- Whether Subject Plans should be made technically shareable across degrees (a "multi-parent" model) — ruled
  out for the foreseeable future on cost grounds, independent of whether it would be nice to have.
- Whether to compute a Degree-level progress percentage — decided, permanently no, on both honesty and
  engineering grounds (see the plan's §9.5 if you want the reasoning, but the decision itself is not open).
- The underlying progress/mastery model (ConceptHealth, ready/due/not-started) — unchanged, not in scope.

---

## What we actually want your judgment on

1. **Subject Plan reuse, from a product angle (plan §6).** This is the single biggest call in the document.
   Setting the code reasoning aside: will curators (and eventually learners comparing two degrees) actually
   experience "two independently-curated shells of the same subject" as reasonable, or does it read as
   obviously duplicated work / an inconsistency risk sooner than the plan assumes? The plan itself names the
   real cost (no discovery tooling to notice "BSCS already has this subject" when authoring BSIT) — does that
   cost change your read of whether this should ship as-is, or ship with a lightweight mitigation from day
   one (e.g. a curator-facing "subjects that look similar" nudge) rather than deferring it?

2. **Degree landing page (plan §11).** Tighten the "Reference Study Journey" framing and the curriculum-
   variability disclaimer copy. Is the per-year status-chip approach (`Not started` / `34% ready` /
   `Coming soon`) the clearest way for a first-year student to understand a degree that's only partially
   published and partially adopted? Would you change the entry point, the copy, or the layout?

3. **Year page — term grouping and Subject card density (plan §12).** Tighten the term header copy pattern
   (`FIRST SEMESTER · 5 subjects · 2 in progress`, deliberately no percentage) and the compact-card
   information hierarchy (title, note count, one progress signal only) for a 10-15 subject Year on mobile.
   Also weigh in on the "final unheadered group for unplaced subjects" rule — right call, or does an
   unlabelled trailing group read as broken rather than intentional?

4. **Progress vocabulary end-to-end (plan §14).** Read the level-by-level table (Degree → Year → Term →
   Subject → Section → Note: what shows, what doesn't) as a first-time learner scanning through all six
   levels in one sitting. Does the vocabulary feel coherent and predictable, or does something still feel
   like a different app at a different level?

5. **The open owner decisions at the end of the document ("OWNER DECISIONS STILL REQUIRED", 7 items).** Give
   your own recommendation on each from a product-UX standpoint, especially:
   - #1 (Subject Plan reuse — see your answer to question 1 above)
   - #2 (should Phase A ship with *no* Degree landing page at all, i.e. BSCS Year 1 is just discovered like
     any other Study Plan until Phase C?)
   - #5 (should the compact Subject card be a hard requirement for any 10+ subject Year, not a nice-to-have?)

6. **Anything else in the UX sections (§11-§15 of the plan)** you think is wrong, missing, or would land
   badly with a real first-year student — but only if it follows from what's actually in the document. Don't
   import generic UX best practice the plan's own facts don't support.

---

## Output shape

1. **Per open owner decision (1-7):** your recommendation in one line, then reasoning. If you agree with the
   plan's own recommendation, say "agree" and move on — don't restate it.
2. **Copy/wording tightening:** concrete rewrites for the Degree page, term headers, and compact card, not
   general notes to "make it clearer."
3. **Any architecture/UX contradiction you spot** (per the one narrow exception above), clearly separated
   from your main UX judgment.
4. Keep it tight. Prose only where a table won't do.
