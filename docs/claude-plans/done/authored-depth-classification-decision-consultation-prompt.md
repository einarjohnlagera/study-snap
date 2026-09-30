# Consultation prompt — what to do about the 80 unclassified student notes

**Send to GPT.** The reviewer has no repository access, so everything needed is restated below. **Nothing has been implemented; no decision has been made yet.**

---

## Your task

A pre-committed production checkpoint just came due and its kill criterion fired: a feature we shipped 30 days ago on the explicit assumption that curators would do some follow-up classification work has had **zero** of that work done. We owe ourselves an honest decision now, not another 30-day extension. Help us pick the right response — and tell us if we're missing a better option than the three we've framed.

## The product

**NoteLib** — a notes-first study workspace for Philippine learners, heavily weighted toward licensure/board-exam review (nursing, accountancy, education, the engineering disciplines) plus some general students. Users write or adopt notes; an LLM generates a "Study Pack" (summary + quizzes) from each note; learners practise against those quizzes. A **Public Library** lets any signed-in user discover public notes other users (mostly curators/admins) have published.

## What shipped 30 days ago

`v0.83.0` removed an old "Target Audience" facet (`STUDENT` / `BOARD_TAKER` / `PROFESSIONAL`) from Public Library discovery and replaced it with a filter on **Authored Depth** (`learnerLevel`: `GRADE_SCHOOL`, `JUNIOR_HIGH`, `SENIOR_HIGH`, `COLLEGE`, `BOARD_EXAM_REVIEW`, `PROFESSIONAL`, `PERSONAL_LEARNING`) — a richer, curator-authored field that already existed for other purposes (it also drives quiz/exam difficulty floors). The filter renders as real, visible chips in the Public Library UI (`Depth: College`, etc.), not a hidden URL parameter — but a chip only appears for a depth value that at least one note actually carries. A note with no Authored Depth set is simply invisible to every depth chip: not broken, just unreachable by that filter.

The release shipped on an explicit, named assumption: *curators will go back and classify the notes that predate this field.*

## The number that didn't move

At `v0.83.0` signoff (2026-08-17), a direct read found **80 of 120** curator-owned public notes formerly tagged `STUDENT` had **no Authored Depth set** (NULL). A checkpoint was pre-declared: re-read the same count 30 days after deploy, and if it was still ≥70 (i.e. fewer than ~12% classified), treat "curators will classify them" as a failed assumption rather than a plan.

**Read on 2026-09-16 (the due date):** the count is still exactly **80**. Not 79. **Zero notes were classified in 30 days.**

There is no click-through or usage instrumentation on Public Library filtering at all — this is a direct database read chosen specifically because no metric exists to watch instead.

## What classifying one note actually requires

We checked before framing this as a "curators didn't try" problem rather than a "we made it hard" problem. Setting Authored Depth on an existing note is one dropdown pick inside that note's normal metadata editor, saved with the rest of the form — no special workflow, no re-generation, no risk to the existing Study Pack. **There is no bulk-edit tool**, though: a curator (or admin) must open each of the 80 notes individually, one at a time, to do it. There is no batch classification screen, and building one was never part of this release's scope.

## The three options on the table

1. **Schedule it as real curator work.** Assign an owner and a deadline to work through the 80 notes (80 individual one-field edits, per above). The depth filter then genuinely delivers on replacing the old audience facet for student-level material.
2. **Accept the gap in writing.** Formally record that cross-program student-level discovery in Public Library is *retired*, not *replaced* — the 80 notes stay permanently unreachable via the depth filter unless someone classifies them later on their own initiative. No further push.
3. **Middle ground: stop pushing, stay reversible.** Don't schedule the work and don't formally retire the capability either — just stop treating "curators will classify them" as an active plan, leave the column/filter exactly as they are, and leave the door open to revisit if it matters more later (e.g. if a future release adds a bulk-edit tool that makes option 1 cheap).

## Context that may bear on the answer

- This decision is explicitly **batched** with a related, larger decision already ratified in principle: an older "Target Audience" facet is being retired in favor of Authored Depth generally (not just in Public Library), on the argument that Course/Program predicts audience ~99.3% of the time outside one program group. That migration is already underway and not itself in question here — but it shares the same root cause (Authored Depth is very sparsely populated: only 905 curator-owned public notes have ever had a value manually set out of a much larger corpus), and the same 80-note population is the sharp edge of it.
- The 80 notes are 100% curator/admin-owned (never learner-owned) and 100% already public — they are exactly the content a discovery filter exists to serve, currently serving none of it for this axis.
- No other axis (Subject, Course/Program) can substitute for Authored Depth as a cross-program "show me student-level material regardless of discipline" filter — Course/Program predicts *audience* well but says nothing about depth within a program.
- We do not know *why* zero were classified — no curator was ever notified, reminded, or given a to-do list; "curators will classify them" may have failed simply because nobody told any curator this was expected of them, not because the work was rejected.

## Questions we want you to answer

1. Given nobody was ever actually asked to do the classification work, is "the assumption failed" the right read of a 0-of-80 result — or does that number mostly indicate we never operationalized the assumption in the first place, which argues for trying option 1 *properly* (an actual assigned task, a nudge, a deadline) before concluding the filter isn't viable?
2. Is a formal "retire, don't replace" decision (option 2) the right instinct for a feature with **zero usage instrumentation** — can you retire a capability in good conscience when you don't know whether anyone was relying on it?
3. Is there a fourth option we're missing — e.g., a cheap partial fix (infer a rough depth from Course/Program for the subset where that's unambiguous, even if imperfect), or reframing the UI so a NULL-depth note surfaces under an "Unclassified" chip instead of vanishing, which would at least make the gap visible to users rather than silent?
4. If you were the owner of this product, which of the three named options (or your fourth) would you actually pick, and why — with the single strongest argument against your own pick?

## What a useful answer looks like

A clear recommendation among the options (including "none of these, do X instead" if warranted), the single strongest argument against whatever you recommend, and anything in our framing you think is wrong. Concrete reasoning over reassurance.
