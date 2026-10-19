# Open decisions for v0.174.0 / v0.175.0: consultation prompt for Product UX

**How to use this:** paste into a Product UX session, in this order:
1. `docs/gpt-contexts/GPT_CONTEXT.md`;
2. `docs/gpt-contexts/QUIZ_AND_PRACTICE_CONTEXT.md`;
3. optionally, for depth, `docs/claude-findings/2026-10-19-v0174-targeted-challenge-start-benchmark-results.md`;
4. everything below the horizontal rule.

The part below the rule stands on its own.

**Why it exists:** the owner wants Product UX's view on the open decisions in Part D of
`docs/claude-plans/2026-10-19-v0174-v0175-consolidated-implementation-plan.md` before answering them.

**Left out on purpose, because only the owner can answer them:**
- A6: the monthly cost-alert threshold;
- A7: checking two production settings;
- A3 and A4: internal job and pool sizing with no learner-facing effect.

**Indexing note:** a one-off consultation prompt, under the Backlog Index's *release artifact* exemption.
Record the answers as owner decisions in `RELEASES.md` and on the LLM tiering Backlog row.

Facts below were read on 2026-10-19 from code on `releases/v0.174.0`, two owner-run benchmarks and
read-only production queries.

---

# NoteLib: open product decisions for a model upgrade and a new "quiz preparation" flow

I need product-UX judgment on a set of decisions. The owner has already decided the direction. These are the
details left open.

## Background

- **NoteLib is pre-revenue.** Plus and Pro exist, but the only active paid subscription is the owner's own
  admin account.
- **The change.** Every AI feature uses one cheap model today, whatever the plan. We are moving:
  - FREE to a newer model ("Luna");
  - Plus/Pro, and official NoteLib content, to a stronger, slower one ("Sol").
- **Locked constraints:**
  - no pricing, quota or entitlement change, and no announcement;
  - customer copy never names a model; if anything is ever said, it is "Enhanced Study Pack Generation";
  - never silently give a paying learner a weaker model.
- **Deadline:** today's models shut down on **2027-01-14**.

## What the owner has decided

**Two releases.**
- **v0.174.0** moves **Study Pack generation** only.
  - Study Packs already generate in the background, and the learner is told.
  - Every screen where a learner waits for AI (quizzes, practice, Interview Practice) **stays on today's
    model, for every plan**, until the next release makes the wait acceptable.
- **v0.175.0** rebuilds how Challenge Quiz starts.

**Why Challenge needed this:** measured, starting a 15-question Challenge Quiz takes:

| | Typical | Slowest |
|---|---|---|
| Today | about 13 s | 34 s |
| Luna (FREE) | about 49 s | 74 s |
| Sol (Plus/Pro) | about 107 s | 161 s, plus one failure at the 180 s limit in 40 tries |

A Plus/Pro quiz built from six notes took about **3.5–4 minutes**, because each note is generated one after
another. Production adds roughly 20+ seconds on top of these.

**The owner's targets:** 30 seconds or less to start a quiz; at most 60 seconds of waiting with nothing else
to do.

**The owner's v0.175.0 flow (decided):**
1. If enough unused questions are already saved for the learner, the quiz starts normally, with no wait.
2. Otherwise a **preparation screen** appears, with:
   - a prominent primary button, **"Stay on this page"**;
   - a secondary action, **"Continue reviewing"**.
3. **Stay:** the learner sees progress. When the questions are ready, the page moves on its own to the quiz's
   **pre-screen**, never straight into a running, timed quiz.
4. **Continue reviewing:** the learner goes back to their notes or Study Pack while the questions are
   prepared in the background. When they are ready, **one** in-app notification appears. There is no email,
   no push and no repeat reminder.
5. The notification opens that exact quiz's pre-screen. **The timer starts only when the learner presses
   Start Quiz.**
6. **If preparation fails or times out,** it is retried once automatically when that is safe. Then the
   learner sees a clear recovery state with a retry option. There are no duplicate quizzes or double quota
   charges, and no lost progress.

## Decisions I need your view on

**Answer each one: agree, disagree or change it, with your reasoning.**

### For v0.174.0 (soon)

**1. Quizzes stay on today's model in v0.174.0, for every plan.**
- **Proposed:** yes. Plus/Pro learners get Sol for Study Packs now and for quizzes in v0.175.0.
- **The risk:** a Plus/Pro learner could notice Study Packs feel different while quizzes do not.
- Nothing is announced, and plan copy promises only quantities (number of Study Packs, quizzes, share
  links), never quality or speed.
- **Is that acceptable for roughly one release?**

**2. Interview Practice and the Ask/companion features (paid) stay on today's model.**
- **Proposed:** yes, until they can be tested, rather than moving them to Sol untested, which may be slower.
- **Agree?**

**3. Creating a Study Pack on the `/study` page makes the learner wait.** Unlike the main flow, this older
page generates the Study Pack while the learner watches.
- **On Sol:** about 27–34 seconds plus production overhead. That is within the 60 s maximum but over the 30 s
  target, and with no automatic retry if it times out.
- Today only the owner's admin account would ever see this.
- **Proposed:** accept for v0.174.0, and give it the retry and recovery behaviour in v0.175.0. **Agree? Is
  anything needed on that page now**, such as a waiting message?

### For v0.175.0 (before it starts)

**4. What "30 s target / 60 s maximum" means under the new flow.**
- **Proposed:** the preparation screen appears **immediately** whenever questions must be generated. The
  30 s / 60 s numbers become the target for how long preparation typically takes, not a forced wait before
  the screen appears.
- **Agree? If not:** should the app wait a little (for example up to 10 s) in case preparation finishes
  quickly, before showing the two choices?

**5. The learner closes the tab without choosing either option.**
- Preparation continues in the background either way.
- **Should they still get the one "your quiz is ready" notification?** Proposed: yes.

**6. How long a prepared but unstarted quiz waits for the learner.**
- After that it expires, and a later visit generates a fresh one. The questions themselves are kept for
  reuse.
- **What window feels right:** 24 hours, 7 days, or something else?
- **What should the learner see if they open an expired link?**

**7. Two "Start Quiz" buttons.** Today, "Start Quiz" on the setup screen *begins generating* the quiz. The
new flow adds a second "Start Quiz" on the pre-screen that *starts the timer*.
- When the learner presses the first button, we don't know yet whether they will go straight into the quiz
  (questions already saved) or to the preparation screen (questions must be generated).
- **What should each button say, and should the first one be renamed?**
- **Copy for the preparation screen, the notification and the recovery state**, within the rule that no model
  is named and nothing implies FREE learners get worse material.

**8. Board Exam and Long Exam.** These timed exams also start their timer the moment questions are ready,
with no "ready, now press Start" step.
- **Proposed:** give them the same flow **after** Challenge's version ships and has been watched in
  production, not in the same release.
- **Agree, or is auto-starting a timed exam serious enough to fix at the same time?**

**9. Filling missing official question sets.**
- 432 of 1,997 official Study Packs have no pre-made Challenge questions. Learners who adopt those always wait
  for generation on their first quiz.
- Filling them is a one-time paid job.
- **Worth doing, and in v0.175.0 or later?** From a UX view, how much does it matter that first quizzes on
  official content start instantly?

**10. A lower "reasoning effort" setting.** It makes FREE quizzes about 40% faster (about 49 s → 28 s) but
barely helps Sol (about 16%). It cannot be used until a blind quality review confirms it doesn't hurt
question quality.
- **Is a faster FREE quiz start worth prioritising that review?**

## Out of scope

Pricing, quotas, announcements, any model naming in copy, how Study Packs generate in the main flow, and the
engineering choices behind the flow (state storage, retries, notification plumbing).
