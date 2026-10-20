# R1 follow-up: what to do about mini's remaining wrong answer keys (Product UX prompt)

**How to use this:** paste the following into a Product UX session, in this order:
1. `docs/gpt-contexts/GPT_CONTEXT.md`.
2. `docs/gpt-contexts/QUIZ_AND_PRACTICE_CONTEXT.md`.
3. Optionally, for depth: `docs/claude-findings/2026-10-20-v0175-r1-acceptance-run.md`.
4. Everything below the horizontal rule.

The part below the rule stands on its own.

**Why this exists:** the R1 acceptance run passed on Luna and Sol and failed on `gpt-4.1-mini`. Release
Implementor gave the owner three options and recommended option 1. The owner wants a second opinion from
Product UX before deciding.

**Left out on purpose:**
- The raw question text. Benchmark items stay local and are never pasted.
- Release-internal detail: code paths, verification tiers and spend accounting.

**Indexing note:** this is a one-off consultation prompt, covered by the Backlog Index's *release artifact*
exemption. Record the answer as an owner decision in `RELEASES.md` v0.175.0, item 1.

Facts below were read on 2026-10-20 from `releases/v0.175.0`, three owner-run acceptance runs and their blind
checks.

---

# NoteLib: what to do about a small rate of wrong answer keys on the model we are moving away from

I need product-UX judgment on one decision. The engineering facts are settled; the trade-off is about
learner trust, and the owner wants a second opinion before choosing.

## Background

- **NoteLib** is a notes-first study app for Philippine board-exam and school learners. It is pre-revenue:
  the only paid subscription is the owner's own admin account.
- **Challenge Quiz** is a timed AI-generated quiz built from a learner's note. It is scored, and it uses the
  learner's monthly Challenge quota.
- **The question type at issue** is the "two statements" item: *"Statement 1: … Statement 2: … Which is
  correct?"*, with four fixed choices: both, only Statement 1, only Statement 2, neither. It is a common
  board-exam format.
- **The defect:** the quiz marks the wrong choice as correct. A learner who answers correctly is scored
  wrong, and the explanation shown afterwards defends the wrong answer.

## What has already been done (R1, merged, not yet deployed)

- **The change:** the model now records a true/false verdict for each statement before choosing the answer.
  The server rejects any item whose answer doesn't match those verdicts, retries it once, and drops it if it
  still fails.
- **Scope:** Challenge Quiz only, on every model.
- **Learner protection:**
  - A start may be at most one question short (for example 9 of 10).
  - If generation can't reach that, the start fails and no quota is charged.

## What the acceptance runs measured

Same 30 Challenge starts per model: 10-, 12- and 15-question sizes on real notes. Two independent reviewers
re-judged every two-statement item against its source note **without seeing the answer key**.

| | Today's model ("mini") | Luna (FREE, after this release) | Sol (Plus/Pro, after this release) |
|---|---|---|---|
| Two-statement keys confirmed correct | **29 of 32: FAILS** | 31 of 31 | 28 of 28 |
| Wrong keys before R1 (earlier study) | 2 of 17 | 11 of 58 (about 19%) | 0 of 42 |
| Typical Challenge start time | 10–18 s | 37–52 s | 55–105 s |

- **Mini's 3 wrong keys are a different kind of error from the one R1 fixes.** In each, the model judged a
  false statement to be true, and its verdict, answer and explanation all agree with each other. R1's check
  can only catch an answer that contradicts the model's own verdicts, so it can't see these.
  - Two of the three turn on an absolute word the model accepted: a statement that something is "always" so,
    or that a method "eliminates" a problem, where well-known exceptions exist.
  - The third is arguable: it depends on what the question author meant by a surveying term.
- **Size of the problem on mini:** about 9% of two-statement items, or about 0.8% of all questions.
- **Small samples:** every result above is a smoke test, not proof. A clean 0 of 31 still allows a true rate
  of up to about 9%.

## Where mini will still be used

- **Today:** all of production. R1 isn't deployed yet, and no option below ships sooner than this release.
- **After this release ships in full:**
  - Single-note and multi-note Challenge move to Luna (FREE) and Sol (Plus/Pro).
  - **No Challenge quiz runs on mini, so mini's errors stop reaching Challenge learners.**
- **If the release is split** (the planned fallback: multi-note preparation ships one release later):
  multi-note Challenge stays on mini until then.
- **Other quiz modes** (Board Exam, Long Exam, Adaptive Practice) stay on mini in any case. They are out of
  scope for this release, and Board and Long Exam get their own follow-up release.
- **Deadline:** mini is shut down by the provider on **2027-01-14** regardless.
- **What learners can do today:** the results and review screens have a feedback panel with a "Report
  Question" action.
  - It opens a free-text message pre-filled with the quiz and note name.
  - It isn't tied to a specific question, and it doesn't correct a score.

## The options

**1. Accept R1 as it is and record mini's errors as a known limitation.** (Release Implementor's
recommendation.)
- **Pros:**
  - No more cost or delay; the next item can start now.
  - R1 already fixed the models Challenge is moving to.
  - If the release ships in full, the leftover errors disappear from Challenge on their own.
  - Mini is no worse than before R1.
- **Cons:**
  - If the release is split, multi-note Challenge keeps about a 9% two-statement error rate for one more
    release.
  - Nothing detects these errors in production, so the rate can't be monitored.

**2. Add a prompt rule:** "a standard exception to an absolute word ('always', 'never', 'only', 'eliminates',
'ensures') makes the statement false."
- **Pros:**
  - Cheap to write.
  - Aims directly at 2 of the 3 errors.
- **Cons:**
  - All models share one Challenge prompt, so Luna's and Sol's passes would have to be re-tested.
  - It may overcorrect, marking true "always" statements false (for example "a triangle's angles always sum
    to 180°") and creating a new kind of wrong key.
  - The sample is too small to show it helped.
  - It doesn't touch the third error.
  - It matters mostly on mini, which is leaving Challenge.

**3. Add a verifier:** a second AI call re-judges each two-statement item before it is shown, and drops it on
disagreement.
- **Pros:**
  - Catches factual errors from any cause, on any model.
  - Could later cover Board Exam and Long Exam, which have the same question type.
  - Inside the background preparation this release builds, the learner wouldn't feel the extra seconds.
- **Cons:**
  - New scope: a new prompt and new failure handling, so probably a later release.
  - A same-model verifier may share the generator's blind spots. A stronger one costs more per start and, if
    used only for paid plans, edges toward a FREE/paid quality difference, which customer copy must never
    imply.
  - Dropped items can push a start below the one-short minimum, so more starts would fail and have to be
    retried.

## Locked constraints (please do not propose changes to these)

- No pricing, quota or entitlement change.
- Customer copy never names a model and never implies a FREE/paid quality difference.
- No email or push channel.
- Board Exam and Long Exam are untouched in this release.
- No prompt or schema change beyond R1's wording without the owner's sign-off on the exact text.

## What I'd like from you

1. **Which option would you choose, and why, from the learner's side?** Weigh how a learner experiences
   being marked wrong when they were right on a board-exam-style item against the cost and delay of each fix.
2. **Does this change your view on splitting the release?** If keeping multi-note Challenge off mini is
   worth something, is that a reason to ship multi-note preparation in this release rather than the next?
3. **Is the existing "Report Question" path enough?** A learner can send free-text feedback but can't flag a
   specific question or get a score corrected. Should a per-question "this answer looks wrong" flag be
   recommended as follow-up work, separate from this decision? If so, what should the learner see after
   flagging?
4. **If a verifier is built later (option 3):** should it cover every quiz mode, and how would you keep
   FREE and paid learners at the same quality bar without naming a model?

Please give a recommendation, not a survey, and say what would change your mind.
