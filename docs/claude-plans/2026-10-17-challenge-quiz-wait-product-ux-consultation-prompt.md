# Challenge Quiz start wait under the new models: consultation prompt for Product UX

**How to use this:** paste `docs/gpt-contexts/GPT_CONTEXT.md`, then
`docs/gpt-contexts/QUIZ_AND_PRACTICE_CONTEXT.md`, then (optional, for depth)
`docs/claude-findings/2026-10-17-v0174-llm-benchmark-results.md`, then everything below the horizontal
rule, into a Product UX session. The part below the rule is self-contained: it carries the measurements, the code
behaviour and the constraints, so the answer does not depend on reading the repo.

**Why it exists.** `v0.174.0 — Enhanced Generation` plans to move FREE to `gpt-6-luna` and Plus/Pro
(plus official curation) to `gpt-6.1-sol`. The benchmark passed both models, but they are slower, and
the one place a learner regularly waits on screen for generation is **starting a Challenge Quiz**. The
owner asked for Product UX's second opinion before deciding item 3 (adopting the models) and the shape
of item 2 (routing).

**Companion documents** (do not paste; reference if asked for depth):
`docs/claude-findings/2026-10-17-v0174-llm-benchmark-results.md` (benchmark results, caveat 1, including
the method behind the production numbers) and `RELEASES.md` § `v0.174.0`.

**Indexing note:** a finished one-off consultation prompt, under the Backlog Index's *release artifact*
exemption. Record the resulting decision on `v0.174.0`'s scope in `RELEASES.md` and on the LLM tiering
Backlog row, not in a new row.

**Facts below were read on 2026-10-17** from code on `releases/v0.174.0`, from the owner-run benchmark,
and from read-only queries against production.

---

# NoteLib: starting a Challenge Quiz will get much slower. What should the learner experience be?

I need product-UX judgment on one interaction affected by a model change we are about to make.

## Background

- **NoteLib is pre-revenue.** Paid plans (Plus, Pro) exist; there are no active paying subscriptions yet.
- **The change:** every AI generation path uses one cheap model today, whatever the plan. We plan to
  move FREE to a newer cheap model ("Luna") and Plus/Pro to a stronger, slower, costlier one ("Sol").
  Official NoteLib content, authored by the admin account, also uses Sol.
- **Locked constraints:** no pricing, quota or entitlement change; no announcement. Any customer copy
  says "Enhanced Study Pack Generation" and **never names a model**. No mass regeneration of existing
  content.
- **The current model is being retired by the provider in January 2027**, so staying on it is not a
  long-term option for any path.

## What the benchmark found (81 generations per model, owner-run)

| | Today's model | Luna (FREE) | Sol (Plus/Pro) |
|---|---|---|---|
| Generations succeeded | 77 / 81 | 81 / 81 | 81 / 81 |
| Blind quality review (20 questions each) | baseline | no regression | no regression |
| Study Pack generation | about 8 s | about 15 s | about 27 s |
| **12 quiz questions in one call** (median / slowest) | **12 s / 26 s** | **36 s / 50 s** | **70 s / 89 s** |

The quality review shows neither new model is *worse*. It does not show either is *better*: the sample
is too small. Do not build copy or positioning on "better questions".

Study Packs generate in the background (the learner is told, and the page updates), so their slowdown
matters much less.

## How starting a Challenge Quiz works today

1. **The quiz starts with 10, 12 or 15 questions**, set by the learner's last Quick Review score on
   that Study Pack (low / middle or none / high, with easy / mixed / hard difficulty to match); 18 when
   several notes are combined.
2. The app first takes unused questions from **the learner's own question bank** for that Study Pack
   (questions generated in their earlier Challenge sessions on it).
3. Then, for Official content they adopted, it copies from **a pre-generated Official template**.
4. **Only if both fall short does it call the model live**, for the missing questions. The learner sees
   a full-screen "Generating your quiz..." overlay meanwhile. **The quiz timer starts only once the
   questions are ready**, so the wait costs no quiz time.
5. Any extra questions are banked, so the learner's next session on that pack is usually instant, and
   **"+5 Questions" mid-quiz is almost always served from the bank**.

## How often the live start happens, and how long it takes today (production, 2026-07-24 → 2026-10-17)

| | |
|---|---|
| Challenge sessions by learners (non-admin) | 68, by 34 learners (23 sessions in the last 30 days) |
| **Starts that generated live** | **33 sessions, by 29 of the 34 learners** |
| New questions generated per live start | about 12 |
| **Live start wait on today's model** | **median 30 s, 90th percentile 62 s, slowest 147 s** |
| "+5" batches that generated live | 2 |

So nearly every learner meets the live start, usually on their first Challenge with a Study Pack.
Production's wait is already slower than the benchmark's 12-question figure (30 s vs 12 s median), for
reasons we have not isolated (retries, server load, longer notes). The sample is small: read these as
shape, not precision.

## What the new models would do (a projection, not a measurement)

Adding the benchmark's per-model difference to today's production wait:

| Live start wait | Today | Luna (FREE) | Sol (Plus/Pro) |
|---|---|---|---|
| Typical | about 30 s | **about 55–90 s** | **about 90–175 s** |

**The server gives a model call 180 s before it fails.** On Sol, a slow start could hit that limit and
fail outright; the learner would see an error after waiting about three minutes. Paid learners would
wait roughly twice as long as free learners for the same action.

## The options we see (not exhaustive; reject or combine freely)

- **A. Start with fewer questions and fill the rest in the background.** For example, generate 5 to
  begin, so the learner is answering in a fraction of the time, and generate the remainder while they
  work. This changes how a session starts, not what it contains.
- **B. Prepare questions ahead of time.** Generate a Challenge bank in the background when a Study Pack
  is created or first opened, so most starts are instant. It costs a generation even for learners who
  never open Challenge Quiz.
- **C. Use a faster setting for Challenge only.** The cheaper model for Challenge on every plan, or a
  lower "reasoning effort" setting on Sol (untested; it would need its own small, separately approved
  benchmark).
- **D. Better waiting state only.** Progress copy or an estimate of remaining time, keeping today's flow.
- **E. Ship the model change as-is and measure** the real start wait before acting.

**A smaller, separate issue, for completeness:** on the rare "+5" that does generate live, the quiz
countdown keeps running while answers are disabled ("Adding..."), and at zero it auto-submits. It went
live twice in three months; say whether it is worth fixing now.

## What I need from you

1. **Is a 1–3 minute "Generating your quiz..." wait acceptable** at the start of a Challenge Quiz, for
   FREE and for Plus/Pro separately? What is the most a learner should wait?
2. **Which option or combination do you recommend,** and does it belong in this release
   (`v0.174.0`) or must the model switch wait for it? Say what you would not do and why.
3. **Paid learners waiting longer than free learners for the same quiz:** acceptable while paid plans
   are not marketed on speed, or should Challenge behave the same on every plan?
4. **Any copy for the waiting state,** within the rule that no model is named and nothing is announced.
5. **The "+5" countdown issue:** fix now, later, or not at all?

**Out of scope:** pricing, quotas, announcing the change, the Study Pack background-generation flow,
Board Exam Mode (it already generates in the background), and the other quiz modes.
