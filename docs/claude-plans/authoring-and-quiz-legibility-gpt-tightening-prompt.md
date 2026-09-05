# Handoff prompt — GPT tightening pass on the five authoring & quiz legibility fixes

**How to use:** paste everything below the line into GPT. It is self-contained; GPT does not need repo access.
Source plan: `docs/claude-plans/authoring-and-quiz-legibility-fix-plan.md`.

---

You are reviewing a **fix plan** for NoteLib, a notes-first study product (learners capture Notes →
generate AI Study Packs → practice with quizzes). Five issues were reported by the product owner from
real use, each with a screenshot. A code audit then diagnosed each one against the actual source.

## Your job, and its boundaries

**Tighten the five fixes below — specifically the UX and product judgment in them.** That is the whole task.

**Do NOT:**
- re-derive or question the code diagnoses — they come from a direct audit with `file:line` anchors you
  cannot see, and second-guessing them without the repo produces confident noise;
- propose implementation details, component code, or library APIs;
- re-open anything under *Fenced off* — each was decided deliberately and has recorded reasoning in the repo;
- restate the findings back to me, or write an essay.

**Important framing, and it is load-bearing.** Two of the five reports turned out to be **different
problems than the owner's wording implies**, and two of the owner's proposed fixes would **revert a
deliberate earlier design**. Do not tighten these back toward the owner's original framing — the whole
value of the audit was catching that gap. Challenge the *reframing* if you think it is wrong, but do not
silently revert to the surface reading.

---

## Product context you need

**The Study Plan Builder** is a curator/learner screen for organizing a "Subject Plan" — an ordered list of
Notes, grouped into named **Sections** (e.g. "Week 1", "Algebra"). Sections are *derived* from a per-note
text label, not a separate entity. Plans routinely run to **77 notes**, and one real review set runs to ~550.
Notes and sections are both reordered by drag-and-drop. A section is chosen per note via a combobox that
allows both **picking an existing section** and **typing a new one**.

**Adaptive Practice** is a quiz mode that generates questions targeting a learner's weak concepts. It comes
in two scopes: **note-scoped** (weak concepts from one Study Pack) and **plan-scoped** (weak concepts
aggregated across a whole Subject Plan, added recently).

**Challenge Quiz** is a multiple-choice quiz mode. ~15% of its questions use an "assertion" format:
*Statement 1: … Statement 2: … Which is correct?* with four fixed options (Both / Only 1 / Only 2 / Neither).

---

## The five reports (owner's words, verbatim)

1. *"In challenge quiz, there are some statement questions still looks weird. See the question description,
   looks confusing to read."*
2. *"In adaptive practice, it already covers across multiple concepts but its weird that it only shows one
   subject, what do you think is best to show as the header for this?"*
3. *"the drag-and-drop of sections in building subject plans works so clunky… when the section we're moving
   goes up, it really isn't working, not unless I drag what was at the top to down so I can place the section
   below."*
4. *"when I choose a section or type a new section, i need to press enter on the field then click anywhere to
   execute the update, which really is weird, it should right after I choose."*
5. *"the save button, i really don't know if it really is valuable. when I organize some review sets, some
   subject plan contains a lot of notes, so the save button really is easy to miss. So i was thinking to just
   remove it, what do you think?"*

---

## Established diagnoses (treat as given — these are code facts, not opinions)

**Item 1 — assertion questions.** A "Statement N:" splitter **already ships** and is already live on this
screen; the two statements *are* already separated onto their own lines. What it leaves behind is that the
**trailing question stem is swallowed onto the last statement's line**, because the splitter pairs each
label with everything up to the next label:

```
Statement 1: <body>
Statement 2: <body> Which of the following is correct?     <- jammed together
```

Upstream cause: the AI prompt models the whole pattern inline on one line with no newline instruction, so
the model emits it that way. **Proposed fix:** (a) display-time — break a trailing interrogative onto its
own line, fixing the ~thousands of already-generated questions; (b) prompt-time — instruct newlines, fixing
new questions at source. The renderer already honours real newlines, so once (b) lands, (a) stops firing.

**Item 2 — adaptive header.** The owner asked a *design* question. The code says the header is **factually
wrong**. The backend already picks the right title by scope (plan title for plan-scoped, pack title for
note-scoped) — but one read path never passes the scope through, so a **plan-scoped session read via the
note route renders a single Study Pack's title** while listing ~14 weak concepts drawn from across the whole
plan. That is the screenshot. **Proposed fix:** (a) backend — pass the scope through on that path, reusing
logic that already exists on the sibling path; (b) frontend — group the concept list **by source pack**
instead of comma-joining it. The data model already carries the source pack title per concept, so no
contract change is needed.

**Item 3 — drag-and-drop.** Two causes. (a) The collision algorithm compares rect *centers*, which is the
wrong choice for a variable-height list. (b) **The drag preview renders the section fully expanded with every
note in it** — a 12-note section produces a preview several hundred pixels tall against ~90px drop targets,
so its center sits far below the cursor and upward targets never win. That is exactly the reported
asymmetry: downward drags work, upward drags don't. **Proposed fix:** change the algorithm **and** compact
the drag preview to roughly the size of a drop target. Either alone is insufficient.

**Item 4 — combobox commit.** The write is deliberately delayed until the field loses focus, plus 500ms.
This has a **recorded reason from a reproduced defect**: saving mid-keystroke tore down the very control
being typed into, and *typing "Week", pausing, then " 1" created a section called "Week" and dropped the
rest.* **Proposed fix:** the delay conflates two different acts. Typing a new name is provisional and the
delay is correct. **Choosing an existing option from the dropdown is final at the moment of the click** —
there is no partial state to protect. So selection gets an immediate commit path; typing keeps the delay
untouched.

**Item 5 — the Save button.** The owner wants it removed. This would **revert a deliberate design** that
replaced per-drop autosave because autosave **raced itself**: each drop triggered a save plus a full refresh,
nothing blocked dragging meanwhile, so a second drag wrote from a diverging base and was clobbered when the
first refresh landed. Two prior releases paid to fix this. **Diagnosis of the real complaint:** it is a
**discoverability** problem with a **long-list** trigger, not an argument against deferred saving. The Save
control sits in a page header that scrolls out of view; on a 77-note plan the curator works hundreds of
pixels below the only affordance that commits their work, and unsaved reordering is silently lost.
**Proposed fix:** keep deferred commit; add a **sticky bar that appears only while there are unsaved
changes**, carrying the pending-change count, Save, and Discard.

---

## Fenced off (decided, with recorded reasoning — do not re-open)

- **The typing delay in item 4 is not removed.** It is a documented mechanism guarding a reproduced defect.
  Item 4 adds a second path beside it; it does not replace it.
- **No return to autosave-per-drop.** The race is real and was reproduced.
- **Item 1's display fix must not mutate stored text** — only how it renders. A previous release shipped a
  display-path text normalizer that was not idempotent and silently corrupted stored content on every read.
- **Item 2 must not merge weak concepts across Study Packs** — they are grouped by source pack because
  merging them by concept name would assert that two identically-named concepts in different packs are the
  same concept, which is a much larger architectural claim that is explicitly deferred.
- No database migration, no new quiz mode, no pricing/quota change, and the onboarding flow is frozen.

---

## What I actually want your judgment on

1. **Item 5 is the biggest call — is "keep deferred commit, fix the affordance" the right answer?**
   The owner explicitly proposed deleting the button and asked "what do you think?", so a straight
   contradiction needs to be worth it. Judge whether a dirty-state sticky bar genuinely resolves *"easy to
   miss"* on a 77-note plan, or whether it just relocates the same problem. If you think the button should
   go, say what replaces the safety it provides. **Also unanswered in the plan: what should happen when the
   curator navigates away with unsaved reordering?** Silent loss is the status quo and is clearly wrong.

2. **Item 2 — what does the header actually say, and how are ~14 weak concepts presented?**
   Once scope is fixed, a plan-scoped session shows the plan's title. But the concept list is the part the
   owner actually saw, and it must be grouped by source pack (see *Fenced off*). Judge: is a grouped list
   the right presentation at 14 concepts across several packs, or is that still a wall of text? Is there a
   better unit — counts, top-N, progressive disclosure? Keep in mind this screen is entered to *practice*,
   not to study a report.

3. **Item 4 — immediate commit on selection relocates the note card under the user's cursor**, because
   choosing a section moves the note into a different section block. That is *correct* behaviour but the
   list will visibly jump. Is that acceptable, or does it need a mitigation (a brief highlight on the moved
   card, a short animation)? Say which, and whether it is worth the complexity.

4. **Item 3 — the compacted drag preview loses the "what am I carrying" information.** Options: header +
   note count only; or header + first ~3 notes + "and N more". The second preserves more context but is
   taller, and preview height is the actual cause of the bug. Which, and why?

5. **Item 1 — is the display-time fix worth shipping at all, or is the prompt fix sufficient?**
   The prompt fix only affects newly generated questions; the display fix also repairs the existing corpus,
   but adds a text heuristic to a rendering path (with the non-idempotency lesson above in mind). Judge
   whether repairing existing questions justifies the heuristic, or whether letting the corpus age out is
   the better trade.

6. **Sequencing.** Items 1, 3 and 4 are contained fixes. Items 2 and 5 each carry a design decision and are
   the two that can grow. Should all five ship together, or should 2 and 5 ride a second release? Note that
   items 4 and 5 touch the same underlying save routine, which raises the verification cost when shipped
   together.

---

## Output shape

1. **Per item (1–5)** — your verdict in one line (`agree` / `agree with change` / `disagree`), then the
   change and its rationale. If you agree fully, say "unchanged" and move on.
2. **Item 5 decision** — a direct answer to the owner's question, plus the navigate-away behaviour.
3. **Item 2 presentation** — a concrete recommendation for the header and the concept list.
4. **Sequencing** — which items ship together, and why.
5. **Anything the plan is missing** that follows from the facts above — but only if it follows from them.
   Do not import general UX practice these specific findings do not support.

Keep it tight. Prose only where a table won't do.
