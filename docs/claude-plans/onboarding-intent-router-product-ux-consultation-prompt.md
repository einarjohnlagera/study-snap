# Onboarding Intent Router — consultation prompt for product UX

**How to use this:** paste everything below the horizontal rule into a fresh product-UX session. It is
self-contained — it carries the product context, the decisions that are already locked, the real production
numbers, and the hard technical constraints, so the consultant does not design something we cannot build.

**Why it is shaped this way.** A previous UX proposal for this same flow was excellent in principle but assumed
a capability the codebase did not have (reliable program matching), which cost a full re-plan. The "Constraints"
section exists specifically to prevent that. Everything in it was verified against real code or real production
data on 2026-08-06 — none of it is assumption.

**Companion documents** (do not paste; reference if the consultant asks for depth):
`docs/claude-plans/onboarding-activation-and-intent-router.md` (the plan),
`onboarding-review-set-vocabulary-audit-results.md` (production data),
`docs/claude-findings/v0.71.0-pre-signoff-pressure-test.md` (how this surfaced).

---

# NoteLib — redesigning onboarding as an intent router

I need product-UX help redesigning the first-run experience of NoteLib, a study app for Philippine learners
(board-exam reviewers, college students, teachers, professionals).

## What NoteLib is now

**A learning system built on top of a knowledge library.** Three layers, earned strictly in order:

1. **Trust** — comprehensive Official Review Sets (curated, admin-published collections of notes with generated
   Study Packs).
2. **Habit** — Study Packs, quizzes, an AI companion, progress tracking.
3. **Community** — user-created and shared knowledge.

The product used to be notes-first: you write or paste a note, AI turns it into a "Study Pack" (summary, key
concepts, flashcards, quizzes). That still exists and matters. But there are now **two legitimate ways to begin**:
study from trusted existing material, or build your own study system from your own notes.

Onboarding currently only tells the second story. That is the problem.

## The governing philosophy for this redesign (already ratified — please design toward it)

> **Onboarding is no longer just collecting profile information. It is an intelligent router that helps every
> learner reach the fastest successful study experience available to them.**

## What onboarding does today

Five steps, mobile-first:

1. **Profile Type** — Student / Exam Reviewer / Teacher / Professional
2. **Learning Context** — Learner Level, Course / Program, plus an optional Exam Date for Exam Reviewers
3. **Input Method** — "How do you want to start?" → generate a note from a topic, or write/paste your own
4. **Study Pack Generation** — a wait screen, then Summary / Key Concepts / Quiz Preview
5. **Completion** — "Open your Study Pack" / "Go to Dashboard"

There is one existing exception: an **Exam Reviewer** whose course/program has a qualifying Official Review Set
skips steps 3–4 entirely, adopts the set in one tap, and lands on its detail page. Everyone else gets the
create-a-note flow. That exception works well and is the seed of the redesign — but today it is invisible,
unexplained, and available to only one profile type.

## The real numbers (production, 2026-08-06)

These should drive the design weighting. Please do not design against intuition here.

- **38.7% of accounts (141 of 364) never complete onboarding.** This is the problem being solved.
- **4 Official Review Sets exist**, for Accountancy, Nursing, Architecture, Education. All four are substantial
  (74, 63, 52, 43 notes) and 100% of their notes have generated Study Packs.
- Those 4 programs look like poor coverage against a 21-program catalog, **but they hold 179 of 218
  program-holding accounts — 82.1% of users.** Coverage is concentrated in exactly what people study.
- So: **~82% of users can be routed to existing material. ~18% cannot** (9 users on catalog programs with no set
  yet; 30 on programs outside the catalog entirely, the largest being "Professional / Board Exam Review" at 14).
- More Official Review Sets are bottlenecked on a curator pipeline that is not scheduled. **Assume the ~18%
  persists for the foreseeable future.**

## The shape already agreed (please refine, don't relitigate)

Step 3 becomes **"What would you like to do first?"** with two options — framed as two starting paths, **not two
kinds of people**. Someone who picks one today must be free to use the other tomorrow.

- **Study existing materials** — start with Official Review Sets already in NoteLib
- **Create my own study materials** — write, paste, or generate a note and turn it into a Study Pack

Availability is resolved **before** the user chooses, and the first option's supporting copy adapts:

- Supported program: *"Start learning immediately with Official Review Sets."*
- Unsupported program: *"Coming soon for {Program}. You can still explore community notes or build your own
  Study System."*

If a user picks "Study existing materials" with no set available, they reach an honest screen — roughly *"We're
still building this learning path; you're among the first learners looking for {Program}"* — and then choose
their own next step from: build your own study system, explore community notes, or go to the dashboard. They are
never auto-redirected.

## Constraints — please treat these as fixed

These are verified facts about the system, not preferences. Designing past them means the design cannot ship.

1. **Profile Type is Student / Exam Reviewer / Teacher / Professional.** Do not add a permanent
   "Creator vs Learner" identity axis. Intent is a moment-in-time question, not a persona.
2. **Course / Program has two authoring modes**, and this is a product distinction, not a permission tier:
   learners write **free text, exactly one**; Teachers/Admins curating shared content pick **from a fixed
   21-entry catalog, one or many**. Onboarding collects *personal* context, so it stays free text for everyone.
3. **The catalog will not be expanded to make onboarding options match.** It reflects real curricula. Several
   things users type ("Professional / Board Exam Review", "Self Study") are activities, not programs.
4. **Do not expose internal vocabulary** — "Domain Context", "Applicable Programs", "Study Pack generation
   context" are architecture words. Learners must never see them.
5. **No new AI call during onboarding.** Subject is auto-derived as a by-product of the Study Pack generation
   that already runs. A pre-generation call would add cost and latency at the most abandonment-sensitive moment.
6. **Generation is one-way.** Once a Study Pack starts building there is no Back, deliberately. Retry reuses the
   same note rather than creating a second one.
7. **Adopting a Review Set lands on the set's detail page, not inside a quiz.** Live testing showed dropping a
   brand-new learner cold into a quiz contradicts the guided identity.
8. **Mobile-first, and keep it short.** Five steps is the current budget. Adding a sixth needs justification.
9. **Reuse real product surfaces** rather than rebuilding them inside onboarding — there is an existing
   Explore page, a public notes library filterable by program, and an adopt flow.

## What I want help with

### 1. The intent step
Two cards where one may say "Coming soon" is a risk: it can read as *this product isn't for me*. How would you
present the choice so an unsupported-program learner still feels routed rather than rejected? Does the
availability signal belong in the card's body copy, a badge, the button label, or somewhere else? Should the two
cards be visually equal weight when one is unavailable, given ~82% of users will see both as live?

### 2. The unmet-intent screen (~18% of users)
It must acknowledge the gap honestly, then offer three choices without causing decision paralysis. How would you
rank and phrase them? Is "explore community notes" a genuinely useful second option, or a consolation prize that
should be demoted? Should this screen ask the learner to register interest in their program — and if so, how do
we do that without promising a date we can't commit to?

### 3. The dashboard problem
"Go to Dashboard" is offered as a neutral third fallback, but our dashboard's empty state for a brand-new user is
**creation-first**: a three-step "import files / create a note / generate" checklist, with existing material as a
single tertiary text link. So routing an unmet-intent learner there is a soft push toward creating — the thing
they just said they didn't want. Do we fix the dashboard empty state, drop "Go to Dashboard" as an option, or
something else?

### 4. Teacher onboarding
A teacher may be writing personal study notes, authoring teaching material, curating reusable content for
students, or just browsing. We don't want four branches. Should teachers see the same two intent choices with
different copy, or something else? What's the minimum that respects a teacher who also studies personally?

### 5. Where the 38.7% is leaking
We have step-level analytics (step viewed, profile selected, input method selected, topic submitted, study pack
generated, completed, abandoned with last-step). Given the flow above, where would you expect a
notes-first onboarding to lose people, and what would you instrument to confirm it before we redesign around a
guess?

### 6. Completion
Three different endings now exist — adopted a Review Set, created a Study Pack, chose a fallback. Should they
share a completion screen or each end in their own place? The adopted-set path currently skips the completion
screen entirely and lands on the set; is that right, or does skipping the "you're done" moment cost something?

## What I want back

Prose, not a spec. Specifically:

- Your critique of the shape above — including anything you think is wrong, not just gaps.
- A recommended step sequence with the actual user-facing copy for the intent step and the unmet-intent screen.
- A clear answer on questions 3 and 4, which are the two we're most split on.
- Anything you'd cut. The instinct with onboarding is always to add; I'd rather ship fewer, better steps.

Where you're uncertain, say so and tell me what evidence would settle it.
