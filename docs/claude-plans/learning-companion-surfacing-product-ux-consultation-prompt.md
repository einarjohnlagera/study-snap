# Consultation: where the "help someone learn" capability should appear (v0.90.0)

**For a product-UX reviewer. Self-contained — no repo access needed.**
**Written 2026-08-20. Decision needed before implementation starts.**

---

## 1. What NoteLib is, in one paragraph

NoteLib is a notes-first study workspace used mainly by Philippine board-exam takers. A learner captures notes, generates an AI "Study Pack" from them (summary, key concepts, quiz), and practises with several quiz modes. **The core loop is capture → generate → practise, and practice is what the product lives or dies on.** Of accounts with a profile type set, ~71% are exam-takers and ~27% are students; teachers are a small minority. Retention is the binding constraint: of 152 activated learners, 141 never returned.

## 2. What just shipped, and why

We shipped **Learning Connections** — a way to help someone else learn (a parent helping a child, a tutor helping a student, a sibling, a mentor). Three parts:

1. **Share a quiz.** Anyone can generate a quiz from their own note and hand it to someone via a link. The recipient takes it in-browser with **no account**.
2. **Connections.** Two people can link, invite-and-accept, revocable either side. Minors require a guardian confirmation step.
3. **Progress.** A connected supporter sees the learner's readiness, streaks and quiz performance — **never their notes**, which is a ratified privacy line.

**This was deliberately built as a capability, not a new profile type.** The old design gated quiz-sharing behind the "Teacher" profile, which meant a parent had to *claim to be a teacher* — changing their own dashboard and practice options — just to help their child. Profile answers *"how do YOU learn?"*, not *"may you help someone?"*

## 3. The problem we want your opinion on

To make the capability reachable, we added a **"Quiz for someone"** button to the note detail page. It currently sits in the row of practice actions:

> **[ Start Quick Review ]  [ Challenge Quiz ]  [ Quiz for someone ]**

The owner raised three objections, and we think at least two are right:

- **It shows for everyone**, including the ~100% of users who have no connection and may never want one.
- **It may create an avoidance path.** Sitting beside "Start Quick Review", it offers *making* something instead of *doing* the practice. Against a product whose constraint is retention, adding a productive-feeling detour to the primary practice surface is a real risk.
- **It blurs the Teacher profile.** (We partly disagree — see §5.)

There is also a **"Learning connections"** item in the main navigation menu, shown to everyone.

## 4. The options on the table

**A — Owner's proposal: an explicit opt-in toggle.** A setting like *"I help someone else learn."* If on, the note-detail button and the nav item appear. If off, both hide.

**B — Our counter-proposal: gate on the actual relationship, asymmetrically.**
- **Note-detail button:** only appears once the user has at least one *accepted* connection. This is where the avoidance risk lives, and the button is useless without a recipient.
- **Nav item:** always visible. It is one menu row, and it is the only entry point that lets someone discover and form a first connection.

Our reasoning against the toggle: the relationship itself is already the signal, and a preference can disagree with reality (someone switches it on, never connects, and the clutter stays). It also re-introduces a "mode", which is the thing the capability framing was meant to remove.

**C — Something else.** We would rather hear this than have you pick between our two.

## 5. Where we think the owner is wrong, and want you to arbitrate

**"It defeats the Teacher profile."** Teachers still exclusively get: printable DOCX export, multiple exam versions, question-count control, and a multi-note Exam Builder. Those are classroom administration. What opened up was *one delivery mechanism* — sharing a single quiz link.

We think the teacher product is intact. **But we may be too close to it**, having just argued this position through a release. If you think a learner seeing any quiz-authoring affordance genuinely erodes the teacher proposition, say so plainly.

## 6. Constraints — please design within these

- **Do not re-gate anything on profile type.** That is the exact error the release corrected. A parent must never have to claim to be a teacher.
- **The privacy line is ratified:** a supporter sees progress, never the learner's notes.
- **Discovery must survive.** Whatever you propose, a user with no connection must be able to find and form a first one.
- **No new profile types.** ("Parent" exists as an unimplemented value with zero users; we are not wiring it up.)
- The recipient of a shared quiz needs no account, and that stays.

## 7. What we are asking you for

1. **A, B, or C** — and the reasoning, not just the pick.
2. **Is the avoidance risk real?** Would a learner plausibly generate a quiz instead of practising, or are we inventing a risk? If it is real, does hiding the button actually address it, or does the risk live somewhere else?
3. **Where should a first-time user encounter this capability at all?** If not the note page, then where — onboarding is off-limits until mid-September for measurement reasons, so assume it cannot go there.
4. **Two smaller open questions**, if you have a view:
   - A supporter's view of someone's progress currently lives on its own page. Should the learner's own **Progress** page link to it, or would that conflate "my progress" with "someone else's"? (These two views deliberately show *different* data — the supporter's is counts-only.)
   - Invitations currently take **one email at a time**. We plan to allow several at once, but only after a security change lands. Is a multi-recipient invite the right shape for this relationship at all, or is one-at-a-time actually correct for something this personal?

## 8. Practical notes

- Answer in prose. We do not need mockups, though ASCII sketches are welcome if placement is the crux.
- **Disagreeing with us is the point.** We have already argued ourselves into option B; the value of this consultation is whether it survives someone who has not.
- If you need a fact about the product that is not here, say what it is and why it changes your answer rather than assuming.
