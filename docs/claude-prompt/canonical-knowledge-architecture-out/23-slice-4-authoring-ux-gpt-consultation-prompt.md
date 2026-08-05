# Slice 4 authoring UX — consultation prompt for product UX GPT

Paste everything below the line. Written in the owner's voice, self-contained — no NoteLib context assumed.

**Why this exists:** the engineer's own reviewer was unavailable across four attempts, so **these recommendations have not been independently reviewed by anything.** Treat them as one person's reasoning, not a vetted plan.

**Three things to watch in the reply:**

1. **Question 1 is the load-bearing one.** It is a permissions-shaped answer to what looks like a UI problem, and that kind of answer is either elegant or a rationalisation. Push on it.
2. **Question 4 has no recommendation attached** because the engineer flagged it as genuinely uncertain rather than picking. If GPT answers it confidently in one line, that is a signal to probe rather than accept.
3. **Do not let it redesign the architecture.** The four-axis model and the single-program-axis decision are ratified. This consult is about authoring UX only.

---

# NoteLib — authoring UX for a metadata simplification

I need a second opinion on four UX decisions. Treat this as a **product design discussion**, not an implementation request.

## Context you need

NoteLib is a notes-first study workspace for Philippine learners, with a strong board-exam-review segment. Users capture **Notes**; each Note can generate an AI Study Pack (summary, key concepts, flashcards, practice quizzes).

Two very different kinds of user author notes:

- **Learners (students)** — writing notes for themselves.
- **Teachers / Admins (curators)** — authoring canonical material that many learners will study, including our own Official Review Sets.

### The metadata model, which is decided and not up for discussion

Every note carries four independent pieces of metadata, each owning exactly one job:

| Axis | Owns | Who can set it today |
|---|---|---|
| **Subject** | *what* the note is about (Algebra) | everyone |
| **Domain Context** | ***how* it is authored** — the only signal telling the AI which academic domain to write in | **Teacher/Admin only** |
| **Note Learner Level** | *how deep* it is authored | **Teacher/Admin only** |
| **Course / Program(s)** | ***where* it is discovered** — who should find it | everyone (currently single-valued) |

**Domain Context** is a closed, curated set of 8 values (e.g. *Engineering Mathematics*, *Professional Education*, *Nursing*). Adding a value is an architectural decision, not routine authoring. It is deliberately hidden from learners — it is a curation concept.

### The change being made

Course / Program is becoming **many-valued**. One canonical Algebra note should be markable as applying to several engineering programs, so it can be authored once rather than duplicated per program.

**One hard constraint drives everything below.** The AI prompt says: *"treat the domain above as **the** authoritative academic domain … do not blend in material from unrelated disciplines."* You cannot satisfy that with a list of three programs. So the rule is: **if a note has more than one program, Domain Context becomes required** — that is what keeps a single authoritative domain.

Programs are chosen from a curated catalog of 21 entries. The catalog deliberately excludes some values that exist on older notes (bare school levels, bare subjects, a family name).

## Question 1 — a student could hit a requirement they cannot satisfy

Course / Program is visible to **everyone**. Domain Context is visible to **Teacher/Admin only**. So if the many-valued picker ships to everyone, a student who selects two programs triggers a requirement whose field is invisible to them. Dead end.

Three options considered:

1. **Show Domain Context to students when triggered** — rejected: it exposes a curated architectural enum to learners and reverses a deliberate decision to hide it.
2. **Let students select several programs with no Domain Context** — rejected: it breaks the constraint above.
3. **Make the cardinality permission-based — many for Teacher/Admin, one for students.** Same field, same label, same concept; only how many you may pick differs.

**The engineer recommends option 3**, arguing that multi-program authoring is inherently a curation act — a student's personal note serves them, not eleven curricula — and that this codebase already gates three of the four axes this way.

**My concern, and what I want you to weigh:** is this a genuinely clean model, or is it a permissions trick papering over a UI problem? Does "the same field behaves differently depending on who you are" confuse people who move between contexts — for example a teacher who is also studying? Is there a fourth option neither of us has seen?

## Question 2 — copying a note, and what it inherits

Users can copy a public note into their own library. Today a copy inherits the original's program.

Under the new model there was a proposal that copies should **not** inherit programs. I disagreed: a learner copies a note precisely *because* it matched their program, so inheriting preserves the reason they copied it. Dropping it would make the copy invisible in program-based discovery for no reason the user would understand.

So copies inherit. To make that visible rather than silent, the plan adds a **one-time dismissible tip** shown when the owner views a copied note:

> *"This copy kept the original's course/programs, so it still turns up where you found it. Change them any time from Edit details."*

**Questions:** Is a one-time tip the right instrument, or is this over-explaining something users would never have questioned? Is the moment right — on first viewing the copy — or should it appear at the moment of copying? Is the copy itself clear?

## Question 3 — teaching a requirement that only appears sometimes

Domain Context is optional for a one-program note and required above one. So the requirement **appears mid-task**, which is exactly when people get annoyed.

The plan keeps the label "Domain Context" (it is the established term across our documentation and admin tools) and teaches through copy at the moment it becomes relevant:

- **When a second program is added**, reveal inline: *"You've added more than one program. Choose the academic domain this note should be written in — it tells the AI **how** to write it, while the programs decide **who** finds it."*
- **If they try to save anyway:** *"A note shared across several programs needs a Domain Context, so the AI knows which academic domain to write in."*

The intent is that the *how* versus *who* contrast conveys the whole model in a few words, at the one moment the distinction becomes real.

**Questions:** Does progressive disclosure work here, or does a field appearing mid-task feel like a trap — should it be visible from the start, just disabled or marked optional? Is "Domain Context" too abstract to keep even for teachers? Does the *how/who* framing actually land, or is it engineer-clever?

## Question 4 — no recommendation on this one

Making the picker catalog-only has a real benefit: it stops new notes carrying off-catalog values we cannot represent structurally.

But it also means **a learner whose program is not among our 21 catalog entries can no longer name it.** They would have to pick something inexact or leave it blank.

For curators, catalog-only is clearly right — a controlled vocabulary is the point. For learners it is a constraint on a field describing *their own* study material.

**Question:** should learners keep free-text entry for their own notes while curators are restricted to the catalog? That splits the field's behaviour a second way, on top of question 1's cardinality split — which may be one split too many. Or is an incomplete catalog simply a catalog problem to fix, not a reason for free text?

## Question 5 — where the model gets explained

Four metadata axes is a lot. Inline helper text can teach one field at the point of use but cannot convey how the four relate.

The plan proposes a short "How note metadata works" section inside the existing teacher-facing help guide rather than a new page — and treats it as **lower priority** than the inline copy, on the reasoning that inline text reaches people who never open Help.

**Question:** is that the right priority order? Is there a better place for conceptual explanation than a help guide — onboarding, an empty state, a first-run walkthrough?

## What I want back

1. A recommendation on each question, with reasoning I can disagree with — not a list of considerations.
2. **Say clearly which recommendations you would reverse**, and why. These have not been reviewed by anyone else.
3. Flag anything where you are reasoning about our specific users rather than general UX principle — our audience is Philippine board-exam reviewees and the teachers who serve them, and I would rather have "this depends on your users, here is what I would test" than confident generality.
4. Anything we have not asked that this decision depends on.
