# Consultation prompt — does a note need its own Learner Level?

Paste everything below the line into a product-UX GPT. It is self-contained: the model has no
repo access, so all evidence it needs is inline. Written 2026-08-04, after `v0.69.0` shipped and
deployed.

**Bias warning for whoever reads the answer:** this prompt was written by the party arguing to
*keep* the field. It states the counter-case as fairly as it can, but you should discount
accordingly and weight the GPT's disagreement more than its agreement.

---

You are a product-UX advisor for **NoteLib**, a notes-first study workspace. Learners write or
upload notes; the app generates a "Study Pack" (summary, key concepts, flashcards) and quizzes
from each note via an LLM. There is an Official Library of curated notes authored by admins that
other learners copy and study.

I need you to adjudicate a design disagreement. **Please argue against whichever side you find
weaker — do not split the difference to be agreeable.**

## The two axes in question

Every note has, among other metadata:

- **Course / Program** — free text, e.g. `Civil Engineering`, `Nursing`, `Software Engineering`.
- **Domain Context** — a curated closed set of 8 values (`Engineering Mathematics`,
  `Engineering Sciences`, `Civil Engineering`, `Professional Practice & Regulation`,
  `General Education`, `Professional Education`, `Nursing`, `Accountancy`). This is the *only*
  thing that tells the LLM what academic domain to author in. Recently introduced.
- **Note Learner Level** — optional, one of `Grade School`, `Junior High`, `Senior High`,
  `College`, `Board Exam Review`, `Professional`, `Personal Learning`. Describes **how deep the
  note was authored**, independent of who reads it. Also recently introduced.

Separately, every **user** has their own profile-level Learner Level, drawn from the same list.

Resolution rules as built:
- Quiz/exam depth = note's level, else the **reader's** profile level, else `College`.
- The note's level is a **floor**: a lower-level reader may get gentler wording and scaffolding,
  but the curriculum, terminology and difficulty stay at the note's level. A higher-level reader
  never raises difficulty.
- Static content (the note body, summary, key concepts) uses the note's level and **never** falls
  back to the reader's — so a Grade School reader cannot dilute a College note.
- Note Learner Level is only visible to Teachers and Admins. Ordinary learners never see it;
  their notes leave it blank and fall back to their own profile level.

## The proposal I want you to evaluate

**"Notes should not carry a learner level at all. Drop the field."**

The argument for dropping it:

1. Originally, learner level existed only so *quizzes* could be pitched correctly. It was a
   property of the *reader*, not the note.
2. Course / Program can already imply difficulty. If a note's program is `Grade School` and the
   topic is Algebra, the quiz should obviously be grade-school algebra.
3. There is a failure mode that feels absurd: if a learner has mis-set their profile level to
   `College` and studies a grade-school Algebra note, they get a college-level quiz. Asking
   authors to set yet another field to prevent that is friction in the wrong place.
4. Two optional dropdowns with abstract names on every note-create screen is real cost for a
   small curation team.

A related proposal from the same conversation: **tie learner level to course/program** (give the
program catalog a `learner_level_id`), so it is inherited rather than chosen.

## The counter-case, stated as fairly as I can

1. `Grade School` is not a program — it is a *level* sitting in a program field. The product just
   finished a seven-PR release specifically to separate those. 49 notes held values like
   `Junior High`, `Senior High – STEM`, `Grade School` in the program field; they have now been
   migrated so the level lives in the level field and the program field no longer reaches the
   LLM. So "program implies difficulty" describes the model that was just retired.
2. Under the rules above, the absurd scenario in point 3 is precisely what the note's level
   *prevents*: with the note authored at `Grade School`, a College reader still gets a grade-school
   quiz, because the note's level wins and a higher reader level never raises difficulty. Remove
   the field and depth falls back to the reader — which produces exactly the complaint.
3. Program cannot carry depth for the case the product exists to serve: one canonical Algebra note
   authored once and reused across eleven engineering programs. And `Civil Engineering`,
   `Nursing`, `Accountancy` each span both undergraduate and board-review depth — one program,
   two depths — so a program→level mapping is not one-to-one.
4. Ordinary learners already never see the field. The friction is confined to Teachers/Admins
   authoring content that *other people* will read.

## What I want from you

1. **Is a note-level depth axis justified, or is it over-modelling?** Answer for a small product
   with a two-person curation team, not a hypothetical enterprise.
2. If it is justified, **should the author choose it, or should it be inferred?** Candidate
   sources: the Review Set / subject plan the note is being authored into; the author's own
   profile level; the note content itself via the LLM. What are the failure modes of each?
3. **Is "author sets depth" the wrong mental model entirely?** Is there a framing that gets the
   right generation behaviour without asking an author to think about an abstract axis?
4. Evaluate the `course_programs.learner_level_id` proposal specifically. Does normalising the
   relationship fix the coupling objection, or just relocate it?
5. There is a separate value question: `College` and `Board Exam Review` are distinct levels
   today, and Board Exam Review drives genuinely different question styling (exam-pattern framing,
   trick-resistant distractors) plus a different difficulty rank. **Is that distinction worth a
   separate value to a user, or is it internal machinery leaking into a user-facing dropdown?**
6. A further proposal from the same conversation: **"Learner level is only really useful for the
   transition from college to board-exam study, and that could be carried by Profile Type
   instead."** Profile Type is a separate existing field with values `STUDENT`, `BOARD_EXAM`,
   `TEACHER`, `PARENT`, `PROFESSIONAL`; it drives dashboard emphasis, which quiz modes are
   available, and some feature gating. Note that `STUDENT` alone spans Grade School, Junior High,
   Senior High and College — four distinct depths — so the two fields have different cardinality
   even though they align on the one college→board-exam case. **Is collapsing depth into Profile
   Type sound, or does it overload an identity field with a difficulty job?**
7. Give a concrete recommendation with a first step, and name what evidence would change your mind.

Assume no legacy constraint you are not told about. If the honest answer is "delete the field and
accept the consequences," say so plainly and describe the consequences.
