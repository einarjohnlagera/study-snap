# Statement-pair verifier: open decisions (Product UX consultation prompt)

**How to use this:** paste into a Product UX session, in this order:
1. `docs/gpt-contexts/GPT_CONTEXT.md`
2. `docs/gpt-contexts/QUIZ_AND_PRACTICE_CONTEXT.md`
3. Optionally, for depth, `docs/claude-plans/2026-10-20-v0175-statement-pair-verifier-investigation.md`
4. Everything below the horizontal rule

The part below the rule stands on its own.

**Why it exists:**
- The owner approved investigating an independent answer-key verifier
  (`docs/claude-plans/2026-10-20-owner-direction-v0175-option3-verifier-investigation.md`).
- The proposal leaves five decisions open. None is settled by an earlier owner decision, and four are partly
  constrained by one; the "Earlier rulings" lines below say how.
- The owner wants Product UX's view before deciding.

**Left out on purpose:**
- Raw question text.
- Code paths, the paid evaluation design and its acceptance thresholds. These are release-internal and have no
  learner-facing choice in them.

**Indexing note:** a one-off consultation prompt, under the Backlog Index's *release artifact* exemption.
Record the answers as owner decisions in `RELEASES.md` v0.175.0 item 1.

Facts below were read on 2026-10-20 from code on `releases/v0.175.0`, three owner-run acceptance runs, and
read-only production queries.

---

# NoteLib: five decisions for an answer-key checker on "two statements" quiz questions

I need product-UX judgment on five decisions. The engineering design is drafted; these are the choices in it
that affect learners.

## Background

- **NoteLib** is a notes-first study app for Philippine board-exam and school learners. It is pre-revenue:
  the only paid subscription is the owner's own admin account.
- **Challenge Quiz** is a timed AI-generated quiz built from a learner's note. It is scored and uses monthly
  Challenge quota.
- **"+5"** adds five more questions mid-quiz, while the timer runs.
- **The question type:** *"Statement 1: … Statement 2: … Which is correct?"*, with four fixed choices: both,
  only 1, only 2, neither.
- **The defect:** the quiz marks the wrong choice correct, because the AI judged a statement's truth wrongly.
  A learner who answered correctly is scored wrong, and the explanation defends the wrong answer.

## Where things stand

- **An earlier fix (R1) caught the other kind of wrong key,** where the AI's own reasoning was right but it
  picked the wrong letter. On the two models Challenge is moving to, blind reviewers found **0 wrong keys in 59**
  of these questions, and **3 in 32** on today's older model.
- **Models after this release:**
  - FREE Challenge moves to "Luna": cheap, about 45 s per start.
  - Plus/Pro moves to "Sol": strongest, about 1–2.5 minutes per start.
  - The older model leaves Challenge, except for multi-note quizzes if part of the release slips.
- **The release adds background preparation:** the quiz is prepared while the learner does something else, then
  a "Your quiz is ready!" screen starts the timer only on **Start Quiz**. "+5" is **not** covered by it.
- **Most questions learners see aren't generated live.**
  - Only 4 of 33 learner Challenge sessions since 2026-09-12 used live generation.
  - The rest came from NoteLib's Official ready-made question sets and each learner's own saved bank.
  - The Official sets were checked and repaired last week, but about 890 of their 1,333 two-statement
    questions were read only by a cheaper first-pass checker. That allows up to about 45 wrong keys still
    live; the point estimate is zero.

## The drafted checker

- **What it is:** before a two-statement question is shown, a second AI judges both statements against the
  learner's note **without seeing the answer**. If its verdicts don't match the key, or it is unsure, the
  question is not shown.
- **What happens to a rejected question:** it is never "fixed" by flipping the answer, because the explanation
  would then argue against the key. It is **replaced once, then dropped** if the replacement fails.
- **Learner protection:**
  - A quiz may be at most one question short.
  - A quiz that can't be prepared fails and charges no quota.
- **Rollout:** each path is switched on separately, and the owner switches each one.
  - **Official set creation goes first.** It is offline and has the widest reach.
  - **Live starts follow,** only once background preparation exists.
- **Cost:** about 1–2 US cents per quiz. That is under $0.10 a month at today's traffic.

## The five decisions

### 1. Which AI checks which questions?

There is one constraint no option escapes: only two AI models are available. On a question Sol wrote, the only
checker that isn't Sol is Luna, the cheaper model.

- **Option F (recommended): both models check every question, on every plan.** FREE and paid get exactly the
  same check. The cost is more correct questions rejected, since two checkers each get a chance to reject.
- **Option D: Sol checks everything, and Luna is added only on Sol-written questions.** Paid questions get two
  checkers and FREE questions one. This may not read as "the same standard".
- **Option C: always the other model.** FREE is checked by Sol and paid by Luna. That is independent, but paying
  learners get the cheaper checker.

**Earlier rulings:**
- "Never silently downgrade a paying learner to a weaker model."
- Customer copy never names a model or implies a FREE/paid quality difference.
- The owner now wants the same standard for both, and doesn't accept a model checking itself as independent.

**The open point:** does Luna checking Sol-written paid questions count as a "downgrade"? Generation isn't
changed; the check is added on top.

### 2. Should "+5" be checked?

"+5" runs mid-quiz with the timer running, and already takes 38–55 s on Sol. A check would add about 10–30 s;
this is unmeasured.

- **(a)** Check it, and accept the longer mid-quiz wait.
- **(b)** Don't check "+5", and log it.
- **(c)** Ask "+5" batches to avoid two-statement questions, so nothing needs checking. This is a prompt change.

**Earlier rulings:**
- Plus/Pro "+5" stays on Sol, with no silent downgrade.
- The "+5" workflow is preserved and must stay safe.
- The owner forbade adding a blocking step at quiz *start*. "+5" isn't the start.

### 3. Can a rejected two-statement question be replaced by a different question type?

- Replacing keeps the requested count. Replacing with the same type would need a second check and could repeat
  the disputed claim.
- The cost is a slightly smaller share of two-statement questions, which are a common board-exam format.

**Earlier rulings:**
- At most one question short.
- The owner wants the requested count delivered wherever feasible.
- Nothing has been decided about the question-type mix.

### 4. Should a checker from a different AI provider be considered later?

- **For:** it's the only kind of checker with evidence on this error. Independent reviewers from another
  provider found all three known errors.
- **Against:** a new integration, and learners' notes would go to a second company.
- It's not proposed for this release. **This was never raised before.**

### 5. Should the ready-made Official sets be re-checked with this checker once it's proven?

- **Scope:** about 890 questions read only by the first-pass checker, with up to about 45 possibly wrong keys.
- **Reach:** this reaches far more learners than live generation does today.
- **How it would run:** the output is a reviewed repair list that the owner applies. It repairs keys and
  doesn't regenerate content.

**Earlier rulings:**
- The owner accepted last week's check as complete.
- "No mass regeneration" is a standing rule.

## Locked constraints (please don't propose changes to these)

- No pricing, quota or entitlement change.
- Customer copy never names a model or implies a FREE/paid quality difference.
- No email or push channel.
- No timer starts before **Start Quiz**.
- Board Exam and Long Exam are untouched in this release.

## What I'd like from you

For each of the five: a recommendation, the learner-side reason, and what would change your mind.

Also tell me:
- whether any of the five should **block** shipping the checker, or can be decided after a first rollout on
  Official set creation only;
- whether a learner should ever **see** that a question was checked or replaced. My default is no, since it is
  invisible plumbing; say so if you disagree.
