# Open questions for v0.175.0 (2026-10-21): consultation prompt for Product UX

**How to use this:** paste into a Product UX session, in this order:
1. `docs/gpt-contexts/GPT_CONTEXT.md`;
2. `docs/gpt-contexts/QUIZ_AND_PRACTICE_CONTEXT.md`;
3. everything below the horizontal rule.

The part below the rule stands on its own.

**Why it exists:** the owner wants a second opinion from Product UX on every open v0.175.0 question before
Release Implementor proceeds with item 5b and the statement-pair verifier's merge decision.

**Left out on purpose, because only the owner can act on them:**
- the Render value of the statement-pair cutoff, which is set after deploy;
- the Stage 2 spending cap, already approved at $4.60, of which about $0.75 has been spent.

**Indexing note:** a one-off consultation prompt, under the Backlog Index's *release artifact* exemption.
Record the answers as owner decisions in `RELEASES.md`, and in Part 13 of
`docs/claude-plans/2026-10-20-v0175-statement-pair-verifier-wording-and-pass-rules.md` for questions 2 and 3.

Facts below were read on 2026-10-21 from code on `releases/v0.175.0`, the open PR branches and owner-run
measurements.

---

# NoteLib v0.175.0 "Quiz Ready": five open questions

I need product-UX judgment on five open questions. For each one, give:
- a recommendation;
- the reasoning;
- the main risk of your choice;
- any wording change.

Flag anything that conflicts with the locked constraints below.

## Background

**What v0.175.0 does.** Challenge Quiz used to generate its questions while the learner waited, and started
the timer straight away. Now:
- **Prepare Quiz** prepares the questions in the background, showing "Getting your quiz ready" with a
  **Continue reviewing** link.
- When the quiz is ready, the learner sees **"Your quiz is ready!"**, and gets one in-app notification if they
  left.
- The timer starts, and the monthly quiz quota is charged, **only when the learner presses Start Quiz.**
- An unstarted prepared quiz expires after 7 days. Leaving, failing, discarding or expiring never uses quota.
- Each learner has a **question pool** per note:
  - questions are reused across quizzes;
  - discarding or finishing a quiz returns its questions to the pool;
  - a new preparation takes from the pool first and generates only what is missing.

**Shipped to the release branch so far:**
- the preparation state;
- expiry and stuck-preparation sweeps;
- the preparation screens;
- the readiness notification and deep link;
- pool integrity: claims are released per quiz, and older learner-written two-statement questions are kept out
  of reuse until a cutoff.

**Still to build:** multi-note preparation in the background (item 5b).

**Locked constraints:**
- no pricing, entitlement or quota-limit change; the only quota change is charging at Start Quiz;
- customer copy never names an AI model and implies no FREE/paid quality difference;
- no email or push; no timer before Start Quiz;
- Board Exam and Long Exam are untouched in this release;
- no prompt or schema change beyond wording the owner signs off.

**About the question format involved below.** A "statement-pair" question shows two statements ("Statement 1:
… Statement 2: …") and asks which are correct. It is the format most prone to wrong answer keys. A separate
guard that checks the key is consistent with the model's own statement verdicts already shipped. An optional
**independent checker** is built but **switched off by default.** When it is on, two separate models judge each
statement against the source note. A question is kept only if both agree with its key; otherwise it is replaced
by a different question on the same concept, or dropped.

---

## Question 1: the multi-note "floor"

A multi-note Challenge draws questions from several notes; Plus and Pro allow up to 6. With 5b, each note's
questions are prepared in parallel, and **a note that fails no longer fails the whole quiz.** The quiz becomes
ready if "enough" questions survive.

**Proposed rule:**
- ready if it has **at least half the requested questions (rounded up) AND questions from at least 2 notes**;
- otherwise the preparation fails, charges nothing, and offers **Try Again**;
- both numbers are configurable.

**Examples, for an 18-question request across 6 notes:**
- one note fails → 15 questions, ready;
- three notes fail → 9, ready;
- four notes fail → 6 from 2 notes, fails, because 6 is under 9.

**What it replaces:** today a multi-note start opens with whatever it got, even 3 of 18. That is recorded as a
known limitation.

**What I need from you:**
- Is half the right line, or should it be higher (two-thirds) or lower?
- Is "2 notes" the right minimum spread, or should it be a share of the selected notes?
- Should the learner be told when they got fewer questions than they asked for (for example, "Your quiz has 12
  questions" on the ready screen), or is the visible question count enough?
- Any new copy needs the owner's sign-off.

## Question 2: the "+5 questions" rule, and what a measurement means

**The rule.** When a learner adds 5 more questions mid-quiz (+5), an instruction tells the model not to write
two-statement questions in that batch. Mid-quiz there's no time to run the independent checker.

**What was measured** (owner-approved paid test, 40 batches on the FREE-tier model):
- **Leaks:** 0 two-statement questions in 40 batches. That passes.
- **Completeness:** only **31 of 40 batches** returned all 5 questions. The pass line is ≥ 95%, and below 90%
  means the rule is removed. **As measured, that is a fail.**
  - **But every one of the 9 failures was a 180-second timeout, not bad output.**
  - Batches without the rule completed 10 of 10, but only 10 such batches exist.
  - The same few notes timed out in two separate runs.
  - So this may be the rule making the model think longer, or it may be that those notes are slow for this model
    whatever the rule. **A small paired test is running** (each timed-out note with and without the rule) to tell
    which.
- **A finding that changes the stakes:** in the built code, this rule is **not** behind the checker's switch. It
  would apply to **every** +5 the moment the branch merges, on today's model, which this test never measured.

**The options:**
- **(a) Remove the rule.** +5 stays exactly as today, with no exclusion. Two-statement questions keep appearing
  in +5, still protected by the key-consistency guard. This is the fallback signed in advance.
- **(b) Keep the rule behind its own switch, off by default.** Merging changes nothing until it is measured on
  the model actually in use.
- **(c) Keep the rule on at merge,** only if the paired test clears it.

**What I need from you:** a recommendation for each possible outcome of the paired test (the rule causes the
timeouts, the notes cause them, or unclear), and how much weight to give "a learner waiting on +5 could hit a
timeout" against "fewer two-statement questions mid-quiz".

## Question 3: are replacement questions "on the same concept"?

**What was measured** (80 replacements, 40 per model, reviewed blind):
- **0** were two-statement questions.
- **0** had wrong keys (independently re-checked on a sample of 20).
- **90–100%** were rated as hard or harder than the question they replaced.
- **But only 33 of 40 (82.5%) were judged "same concept"; the pass line is 90%.**
- **In all 14 misses, the original question's concept label didn't match what the question actually asked**
  (for example, a question about Corinthian capitals labelled "Entasis"). The replacement then tested the
  label, as its instruction says.
- **This only matters once the checker is switched on.** Replacements happen only when it rejects a question,
  and it ships off.

**The options:**
- **(A) Accept it as a "gray" result, with the explanation on record.** No more work, and nothing re-measured
  after seeing results, but the branch would merge with a gray result.
- **(B) Re-judge only the 14 against the label alone,** with the pass line (≥ 36 of 40) fixed in advance. That
  separates "the replacement follows its instruction" from "the labels are loose". It is a re-measurement after
  seeing results, so it needs the owner's approval. The 82.5% "matches the original topic" figure stays recorded
  as a separate known issue.
- **(C) Change the replacement instruction** to target what the question actually asks. That fixes the source,
  but changes owner-signed wording and needs another paid test.

**What I need from you:** which option, and whether "replacement follows a loose label" is acceptable learner
experience. The replacement is still about the same note, correctly keyed and at the same difficulty.

## Question 4: copy awaiting sign-off

All of this is built and in place, pending sign-off. It is grouped by screen.

**Collision.** The learner already has an unstarted quiz on this note and asks for a different kind:
- Title (ready): "You already have a quiz ready".
- Title (still preparing): "You already have a quiz being prepared". This also appears on the mode-selection
  screen when a quiz is still preparing.
- Body: "Resume it, or start something else instead. Starting something else discards the prepared quiz, and it
  won't count toward your monthly quiz limit."
- Buttons, already approved: "Resume your prepared quiz" and "Start something else".

**Dashboard card** for a prepared quiz: "Open your prepared quiz".

**In-app notification:**
- Title: "Your quiz is ready!".
- Body: "Pick up where you left off.".
- Button: "Open your prepared quiz".
- The signed source line was "Your quiz is ready! Pick up where you left off." It is split here into a title and
  a body.

**Setup screen, ordinary Challenge:** "Counts toward your monthly quiz limit when you press Start Quiz. Leaving
before you start doesn't use an attempt." The owner's direction: keep the limit line, and say explicitly that
leaving an unstarted quiz gives the attempt back.

**An existing line, flagged and not changed:** the Redo Missed Questions setup panel says "Counts toward your
monthly quiz limit.", but Redo Missed never uses quota. Should it change, and to what?

**What I need from you:**
- approve, or give edits, for each string;
- answer the Redo Missed question.

## Question 5 (advisory): slow +5 on the newer models

Test 2 showed some +5 batches on the FREE-tier model running past the 180-second limit. A later item in this
release moves Challenge and +5 from today's fast model to the newer models (FREE to the faster one, Plus/Pro to
the slower, stronger one). The paid tier's model is slower still.

**What I need from you:** what learner experience is acceptable for +5 under those models. For example:
- a visible "adding questions…" state with a time expectation;
- or capping +5's wait and offering a retry;
- or keeping +5 on today's fast model even after the start moves.

The locked rule "never silently give a paying learner a weaker model" applies to the main start. Tell us
whether you read it as covering +5 too.
