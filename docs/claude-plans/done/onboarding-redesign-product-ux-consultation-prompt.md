# Onboarding redesign — consultation prompt for product UX

**How to use this:** paste everything below the horizontal rule into a fresh product-UX session. It is
self-contained — it carries the product context, the owner's direction, the real production numbers, and the
hard technical constraints, so the consultant does not design something we cannot build.

**Why it is shaped this way.** A previous UX proposal for this same flow was excellent in principle but assumed
a capability the codebase did not have, which cost a full re-plan. The "Constraints" section exists specifically
to prevent that. Everything in it was verified against real code or real production data on **2026-08-11** —
none of it is assumption, and three items in it directly contradict things the owner believed were true.

**Companion documents** (do not paste; reference if the consultant asks for depth):
`docs/claude-plans/onboarding-activation-and-intent-router.md` (the prior plan),
`docs/claude-plans/v0.72.1-activation-read.sql` (the production funnel read quoted below),
`RELEASES.md` §`v0.72.1` (where those results are recorded).

**Indexing note:** this file needs a row in `ROADMAP.md`'s Backlog Index before it is committed, or it should be
attached to the existing *Onboarding Intent Router* row — per the invariant that no planning document exists
without an index entry.

---

# NoteLib — redesigning onboarding so a new user understands the product

I need product-UX help redesigning the first-run experience of **NoteLib**, a study app for Philippine learners
(board-exam reviewers, college students, teachers, professionals).

## Positioning — every screen should reinforce this

> **Your notes become your study system.**

## The problem, in the owner's words

Onboarding currently feels like a **settings wizard**. It asks users to configure an account instead of helping
them understand what they can accomplish. We want to shift it from *"Tell us about yourself"* to
*"Here's how NoteLib helps you learn."* Before a user creates their first note, they should already understand
why NoteLib is different.

**This is not a visual polish task.** Please think as a product designer, and challenge anything below if you
think there is a better experience.

## What onboarding does today

Five steps, mobile-first:

1. **Profile Type** — Student / Exam Reviewer / Teacher / Professional. Currently reads as radio buttons.
2. **Learning Context** — Learner Level, Course / Program, plus an optional Exam Date for Exam Reviewers.
3. **First intent** — "study with ready-made materials" (an Official Review Set built for your program) vs.
   "start from your own notes." If no Official set exists for the learner's program, a fallback screen offers
   three routes: use your own notes, explore, or just finish setup.
4. **Study Pack generation** — the app generates the learner's first Study Pack (summary, key concepts,
   quizzes) and shows it.
5. **Completion** — a short confirmation, then into the app.

## What the owner wants

**Reduce visual heaviness.** Typography hierarchy, heading sizes, spacing, card sizing, paragraph density. It
should feel lighter and more welcoming. No giant blocks of bold text; use whitespace for breathing room.

**Stop selling "AI."** Avoid the term entirely in user-facing onboarding copy. Prefer outcomes — "Turn notes
into Study Packs," "Generate quizzes from your notes," "Review smarter," "Build your study system." The benefit
matters more than the technology.

**Step 1 — Profile Type.** Works functionally but feels like picking radio buttons. One idea was a two-panel
layout: profiles on the left, and on the right a detail panel for the selected one ("Student — perfect for
school and university learning. You'll be able to: create Study Packs, practice quizzes, track progress, follow
Study Plans"). Explain what users can *accomplish*, not just what the role is. **A better idea is welcome —
see the open questions, where this specific proposal is challenged.**

**Step 2 — Learning Context.** Already fairly solid. Wants lighter typography, warmer copy, less form-like.
Instead of "Set up your learning profile," something conversational like "Tell us what you're studying." Replace
long helper text with examples where possible.

**Step 3 — First intent.** The biggest opportunity. Should become outcome-focused — sell what each path *gives*
the learner rather than naming an action. Roughly: "Start with Official Study Plans — structured learning, great
if you want a guided review experience" vs. "Build from your own notes — turn your notes into summaries, key
concepts, quizzes, and review sessions."

**Step 4 — the "no Official set for your program yet" fallback.** The flow is right; the feeling is wrong.
Instead of "We don't have this yet," it should read as "You can still start learning today" — a helpful
recommendation, not a dead end.

**End of onboarding.** Redesign the completion moment. Don't just drop users into the app; create a short
transition that makes them want to begin. Direction: *"You're all set. Your study system starts with just one
note. Let's start learning."*

**Universal onboarding.** The owner wants onboarding to run for every new account immediately after signup,
regardless of signup method, rather than only after email verification. **Read the constraints below before
designing this — the stated rationale for it turned out to be false.**

---

# Constraints — verified against real code and production data, 2026-08-11

## 1. Onboarding generates a Study Pack, and that endpoint requires a verified email

The owner's rationale for universal onboarding was: *"onboarding only appeared after email verification because
there were LLM costs during onboarding. That is no longer true."*

**It is still true.** Step 4 makes two LLM calls, and **both endpoints require a verified email** server-side.
Moving onboarding ahead of verification therefore does two things: it exposes paid LLM generation to unverified
throwaway accounts, and it **hard-fails onboarding at step 4** — the exact moment onboarding promises the
learner their first Study Pack.

**This does not kill the idea; it relocates the verification ask.** One option already on the table: run
steps 1–3 before verification and place the verification request at step 4, so verification moves from *the
door* to *the payoff* — asked of someone who has chosen a profile, told us what they study, picked a path, and
is one click from the thing they came for, rather than of a stranger who knows nothing yet. This needs no
backend change. **Please evaluate this and propose better options if they exist** — including whether an
unverified learner should get a non-generated preview instead.

## 2. The funnel — verification is a small leak; onboarding is the big one

Measured in production, all-time:

| stage | users | lost at this step |
|---|---|---|
| signups | 375 | — |
| email verified | 366 | **9 (2.4%)** |
| onboarding completed | 234 | **132 (35.2%)** |
| generated a first Study Pack | 195 | 39 |

**132 people — 35.2% of everyone who ever signed up — verify their email and never finish onboarding.** It is
the largest single drop in the entire funnel, which is the real reason this redesign is worth doing.

One caveat: the 97.6% verification rate is measured under today's flow, where verification is mandatory *first*,
so it cannot tell us about people who would have onboarded but never verified.

## 3. We do NOT know which step the 132 abandon on

Profile type is only persisted at the *final* step, so the database cannot distinguish a step-1 abandon from a
step-4 one. Step-level analytics exist but only became trustworthy on 2026-07-28 (an earlier event bug), so the
sample is currently ~5 users — too thin to locate the drop.

**Please do not assume a specific step is the leak.** If your recommendation depends on knowing, say so
explicitly and we will instrument first.

## 4. Steps 3 and 4 already exist as mechanisms — this is a copy and layout job

The "ready-made vs. your own notes" choice and the "no Official set for your program yet" fallback (with its
three routes) **already shipped**. The owner's asks for these two steps are copy and layout over working
machinery, not new capability. Design accordingly — proposals here are cheap. Proposals that change the
*mechanism* are not.

## 5. "Professional" is thinner than it looks — this constrains the Step 1 detail panel

The proposed Step 1 panel promises "You'll be able to: ✓ … ✓ …" per profile. Verified reality:

- **Student** and **Exam Reviewer** are fully built.
- **Teacher** is real (quiz generation and export for classroom use).
- **Professional** is **mostly relabeled student functionality** — it gets Interview Practice as its primary
  mode, plus a Challenge Quiz renamed "Certification Review" and a long-exam mode. There is no distinct
  professional feature set behind it.

An internal messaging decision already flags Professional's marketing bullets as **aspirational and not
shippable as real copy**. So a design that promises four concrete capabilities per profile will either overstate
Professional or expose that it is thin. **How should the design handle a profile that is real but shallow?**

## 6. Other binding rules

- **Mobile-first.** A significant share of learners are on phones; the app has a mobile bottom tab bar.
- **Course / Program, Learner Level, Subject and Audience must use a picker, never free text.** This is a
  repeatedly-violated rule.
- **Collection vocabulary is profile-aware** — what a Student sees called a "Study Plan" may be labelled
  differently for a Teacher. Copy cannot hardcode one noun.
- **Profile type must be captured.** Downstream features hard-depend on it; onboarding cannot end without it.

---

# Open questions — what we most want your judgment on

1. **Step 1 layout.** The two-panel master/detail is challenged internally on two grounds: it adds a
   "read before you choose" burden on the highest-risk screen (zero investment so far, so every extra thing to
   read is an exit), and master/detail is awkward on mobile. The counter-proposal is one outcome line per card,
   so the choice *is* the explanation. **Which is right?** More broadly: is step 1 the wrong place to teach,
   given teaching costs most where the user has invested least?

2. **Where should the product actually be explained?** The owner wants onboarding to communicate what NoteLib
   is. Should that be distributed across every step, concentrated at one screen, or carried entirely by the
   step-4 artifact (the learner's own first Study Pack)?

3. **The completion moment.** The suggestion is an encouraging transition screen. The internal counter is that
   step 4 has already produced a **real Study Pack**, so the strongest ending may not be a message at all but
   the learner's own material with one obvious next action — proving "your notes become your study system"
   rather than asserting it. **Which ends better?**

4. **Verification placement.** Given constraint 1, where does the ask belong, and what should an unverified
   learner be allowed to see or do?

5. **Sequencing.** If this must ship in stages, what is the smallest first slice that would move the 35.2%
   abandon rate, and what would you deliberately defer?

6. **What would you cut?** The direction above may be trying to do too much at once. If some of it is
   working against completion rate, say so.

# Out of scope

- New quiz modes. The mode set is a closed contract.
- Changing what a Study Pack is, or how generation works.
- Any change to the Course / Program data model.
- Visual identity, logo, or brand palette work.
