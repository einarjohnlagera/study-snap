# Handoff prompt — GPT tightening pass on the Review Sets Stage 2 plan

**How to use:** paste everything below the line into GPT. It is self-contained; GPT does not need repo access.
Source audit: `docs/claude-plans/review-sets-first-class-and-ai-language-audit.md`.

---

You are reviewing the **sequencing plan** from a completed product/architecture audit of NoteLib, a
notes-first study product (learners capture Notes → generate Study Packs → practice with quizzes).

## Your job, and its boundaries

**Tighten the four-slice implementation sequence below.** That is the whole task.

**Do NOT:**
- re-derive or question the code findings — they come from a direct code audit with `file:line` anchors you
  cannot see, and second-guessing them without the repo produces confident noise;
- propose implementation details, schemas, endpoints, or code;
- re-open the decisions listed under *Fenced off* — they were taken deliberately and each has recorded reasoning;
- write an essay, or restate the findings back to me.

**Important framing:** this audit **challenged three premises of the original product brief**, and those
challenges are load-bearing. Do not tighten the plan back toward the brief's assumptions. Specifically, the
brief assumed the product was "Note-first and needs to become plan-first"; the audit found the plan is
*already* first-class on the learner surfaces, and that the real gap is one level down, in assessment.

---

## Established facts from the code audit (treat as given)

**Product shape.** One entity (`note_collections`) is surfaced under four profile-dependent names: *Study
Plan* (student), *Review Set* (board exam), *Lesson Plan* (teacher), *Collection* (professional/parent).
A parent collection is a "Goal"; a child is a "Subject Plan". Sections are a third, derived level.

**Already first-class — do not propose building these:**
- The Dashboard already leads with the learner's primary plan, above practice content, with a computed
  "next step."
- Post-session routing already offers the next unpracticed note in the plan; for a note in *no* plan, it
  already offers a program-matched published plan to adopt.
- The Library already nudges toward grouping at ≥3 notes; Note Detail already has "Add to Study Plan";
  onboarding already adopts an Official Review Set.
- Standalone Notes are structurally protected: **no learn or practice path checks plan membership anywhere.**

**The real gap — assessment:**
- Multi-note assessment exists in two flavours (Long Exam; Board Exam Mode, which is a variant of the
  Challenge Quiz engine rather than its own mode).
- Both are capped at **3–4 notes**.
- Both require every additional note to **match the primary note's `subject` string**.
- **`subject` is free text** with no catalog — matching is case/whitespace folding only. So two notes a
  learner deliberately placed in the *same* Subject Plan are already rejected from one exam if their subjects
  were typed differently ("Engineering Mathematics" vs "Engineering Math"). **The rule is already producing
  wrong rejections, not merely restricting.**
- **Neither can be sourced from a Review Set or Subject Plan at all** — both accept only a list of individual
  note/study-pack ids.
- Both are **Pro-only**. Free and Plus have *identical* mixed-retrieval capability: none.
- All assessment modes, including multi-note ones, feed the durable weak-concept signal. But **remediation
  (Adaptive Practice) is scoped to a single note**, so a 4-note exam writes weakness into 4 buckets and
  remediation can only address one per session.

**Supporter gap.** A multi-note exam already exists as a *printable DOCX*, behind a teacher-profile check.
The shareable quiz link is single-note only. Combining logic, share logic and an anonymous take-surface all
exist separately; no combined shareable artifact joins them.

**Quotas today (monthly):** Study Packs 10/50/100 · Challenge Quiz 20/100/200 (1 unit per session start;
adding questions mid-session is free) · Adaptive Practice 3/10/30 · Long Exam 0/0/12 · Board Exam 0/0/10 ·
Interview Practice 0/0/10 · quiz share links 3/10/unlimited. Every limit is config-backed and reversible.

**Instrumentation.** ~106 analytics events exist, including plan adoption and the plan→next-note ratio.
Three cheap gaps: no event when a note *enters* a plan (the central retention transition is invisible), no
Board Exam completion event, and multi-note exam events omit how many notes were selected.

**Language.** A usage meter is labelled "AI quizzes" and its description is factually incomplete (it omits
Board Exam sessions, which spend it). ~44 learner-facing "AI" strings were classified: most are removable
implementation-talk, a handful are protected disclosures, and "AI critique" is a product-feature name in a
locked contract doc plus indexed public content.

---

## The plan to tighten

**Slice 1 — Language and legibility.** Rename the meter to "Quiz generations" and correct its description;
sweep ~12 learner-facing "AI" copy sites toward outcome language; add the missing "note added to plan" event.

**Slice 2 — Assessment can be sourced from a plan.** Accept a collection as a multi-note exam source; for
plan-sourced exams only, replace the same-subject rule with plan membership; raise the source cap for
plan-sourced exams; add the two missing instrumentation signals.

**Slice 3 — Mixed retrieval reaches Free and Plus.** Config + gate change giving Free and Plus a limited
multi-note exam (proposal: Free ~2–3 notes / ~2 per month; Plus plan-sourced / higher cap / ~10 per month;
Pro unchanged). Reuse the existing Challenge Quiz meter, no new meter. Also: make the plan's terminal CTA
honest — it currently resolves on *profile* while the paywall fires on *plan*, so Free/Plus learners see a
concluding action they cannot use, and one profile gets no terminal action at all.

**Slice 4 — Supporter flow and remediation scope.** A combined multi-note quiz a share link can point at
(not teacher-gated, not requiring a connection). Plus: *add* subject/plan-scoped Adaptive Practice as a
**separate** entry point, leaving note-scoped Adaptive Practice from Note Detail unchanged.

---

## Fenced off — do not re-open

- **No renaming** of Review Set / Study Plan / Subject Plan / Collection. Inconsistencies were flagged;
  approval was deliberately not sought.
- **No new quiz mode.** The mode hierarchy is a locked five-mode contract; multi-note is a *capability* on an
  existing mode, which that contract already anticipates by name.
- **No new usage meter.** A separate multi-note counter is a pricing decision nobody has taken.
- **No plan price or plan name changes.**
- **No onboarding changes before 2026-09-11** — a live signup-funnel measurement window.
- **Standalone Notes are never degraded.** No mandatory plan membership, no capability withheld from an
  unorganized note, no separate "Note mode" vs "Study Plan mode".
- **Protected disclosures stay** (privacy/terms AI-processing language; "verify calculations" on generated
  working; the review-before-you-share warning).
- **"AI critique" is out of scope** — it needs its own decision.
- **Challenge Quiz is not being shortened.** Recommended against on three grounds, chiefly that its
  20-question ceiling is the only sustained retrieval practice the free tier has, and abandonment-by-length
  is not currently measurable.
- **Adaptive Practice's existing note-scoped entry point is not widened**, only supplemented.

---

## What I actually want your judgment on

1. **Slice boundaries.** Is Slice 2 one release or two? It contains a source-shape change (accept a
   collection) and a predicate change (drop the same-subject rule) — related, but separable, and the second
   is arguably a bug fix that need not wait.
2. **The Free/Plus shape in Slice 3.** The numbers are a proposal, not a decision. Judge the *pricing-ladder
   coherence*: does Pro still read as clearly worth it once Free and Plus can mix notes? Is the Plus tier
   differentiated enough from Free? Note the current defect this fixes — Plus pays money for zero additional
   mixed-retrieval capability over Free.
3. **Whether Slice 4's two items belong together at all.** The supporter quiz and Adaptive Practice scope
   share nothing except being last. If they should split, say what the fourth and fifth slices are.
4. **Open questions — which genuinely block, and which can ship under a stated assumption?**
   - **(a)** Should a plan-sourced exam draw from a whole Review Set (Goal + all Subject Plans) or one
     Subject Plan at a time? These are two capabilities; the smaller is the Subject Plan exam.
   - **(b)** If it draws from a Subject Plan, does "the notes in it" mean **plan membership** or the notes'
     **`subject` field**? These give different note sets. Today's exams use the second; a Subject Plan *is*
     the first. Answering (a) without (b) ships an exam whose contents nobody can predict.
   - **(c)** Should the professional/parent profile's "Collection" label become a learning-journey name?
   - **(d)** Rename "AI critique", or keep it as a feature name?

---

## Output shape

1. **Revised slice list** — one line of rationale per change you make. If you change nothing in a slice, say
   "unchanged" and move on.
2. **Mis-sequencing** — anything you think is ordered wrong, and why.
3. **Blocking vs. non-blocking** — a two-column split of the four open questions, with the assumption you'd
   ship under for each non-blocker.
4. **Anything the plan is missing** that follows from the facts above — but only if it follows from them.
   Do not import ideas from general product practice that these specific findings do not support.

Keep it tight. Prose only where a table won't do.
