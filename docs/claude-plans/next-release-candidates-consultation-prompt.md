# v0.74.0 proposal — consultation prompt for product UX

**How to use this:** paste everything below the horizontal rule into a fresh product-UX session. It is
self-contained — it carries the proposal, the product context, and the hard constraints verified against real
code, so the consultant does not recommend something already blocked or something that breaks a measurement in
flight.

**Status:** planning only. Nothing implemented, no version open. Constraints verified against code on
**2026-08-12**.

**On the version number:** an earlier draft was titled "v0.72.0". That version shipped 2026-08-11, as did
`v0.72.1`, and `v0.73.0` shipped 2026-08-12. The next release is **v0.74.0**.

---

# NoteLib v0.74.0 proposal — strengthen the study journey

I'd like to propose the following for the next release. **Please challenge anything here if you believe there
is a better UX or product direction.** My goal is for NoteLib to feel like a guided study system rather than a
collection of features.

## Product context you need

NoteLib is a study app for Philippine learners (board-exam reviewers, college students, teachers,
professionals). A learner writes or generates a **note**, generates a **Study Pack** from it, then practises.

**The practice modes, and the distinction that matters below:**
- **Quick Review** — light recall, and it runs on the Study Pack's *saved* quiz questions (`note.quiz`).
- **Challenge Quiz** — harder and progressive, and it generates **its own** questions from a per-user bank. It
  does *not* reuse the saved quiz questions.
- **ConceptHealth** is the app's only mastery-integrity signal, locked since v0.37.0 to move **only from
  genuine assessment**. Quick Review writes to it.

**Recent measurements that bound what is worth building:** onboarding was just redesigned (`v0.73.0`, deployed
2026-08-12) against the funnel's largest leak — 132 learners, 35.2% of all signups, verify their email and
never finish onboarding. Retention, re-measured, is **~7.2%** over a sane window (the long-quoted "2.4%" was an
artifact of a days-7–14-only window). Activation is **52.2%**.

---

## Candidate 1 — Quiz progression (ratify the study journey)

### Background, and a finding that changes the rationale

Every generated Study Pack immediately exposes **Summary · Key Concepts · Quiz · Full Notes**, while the same
page also exposes **Start Quick Review** and **Challenge Quiz**. Two parallel navigation systems, and the Quiz
tab reads as a peer of Summary rather than as practice.

**The stronger reason to act, found in the code on 2026-08-12: the Quiz tab is an answer key.**
`practice-quiz-card.tsx:25` renders the saved questions with `revealAnswer` — questions *and* correct answers.
Quick Review then administers **those same questions**. So a learner can read every answer, then take Quick
Review on the same items. That does not merely reduce Quick Review's perceived value — **it makes its score
meaningless, and Quick Review writes to `ConceptHealth`**, the one signal this product treats as
mastery-integrity-bearing.

This reframes the proposal from *"a progression would feel nicer"* to *"a scored assessment currently has its
answer key on an adjacent tab."*

**It also explains why locking the easier artifact while leaving the harder one open is coherent rather than
arbitrary** — a point a reviewer would otherwise reasonably challenge. Challenge Quiz generates its own
questions, so the answer key does not spoil it. The Quiz tab is specifically the answer key to the test Quick
Review administers. Locking exactly that, and nothing else, is the narrowest fix that restores the signal.

The goal is **not** to force a rigid workflow. It is to stop one surface silently invalidating another.

### Proposed changes

**1. Lock only the Quiz tab.** It stays visible, locked until the learner masters Quick Review:

> Summary | Key Concepts | 🔒 Quiz | Full Notes

Selecting it explains why: *"Complete Quick Review to unlock the Quiz. Quick Review reinforces the Study Pack
before you practise with the saved questions."* **The tab must not disappear** — a learner cannot feel they
have unlocked something they never knew existed, and a tab that silently materialises reads as a glitch.

**2. Challenge Quiz stays available from the start.** Some learners — board takers especially — already know
the material and want exam-mode practice immediately. Quick Review is the *recommended* first step, not a
mandatory gate. Locked Quiz tab; open Challenge Quiz.

**3. Unlock celebration.** On a perfect Quick Review score, announce **🔓 Quiz Unlocked** alongside the score,
and explain that the saved questions are now available to practise with.

**4. Promote Challenge Quiz after mastery.** Before: primary **⚡ Start Quick Review**, secondary **🏆 Challenge
Quiz**. After: primary **🏆 Challenge Quiz**, secondary **✓ Quick Review Again**. Quick Review stays available,
just secondary. **See constraint 1 — this item alone is time-blocked.**

**5. Replace "Finish Review."** After incorrect answers a learner can currently *Redo Mistakes* or *Finish
Review*. Replace the latter with **"Review the Notes"**, redirecting to the Note so they can study before
trying again, instead of ending the loop.

**6. Admin exemption.** Teachers/Admins are unaffected by the lock — they inspect generated quizzes for
authoring, QA, demonstrations and marketing.

**7. Analytics.** Instrument: Quick Review started · Quick Review mastered · Quiz unlocked · Quiz tab opened
after unlock · Challenge Quiz launched before mastery · Challenge Quiz launched after mastery.

### Constraints — verified, please design within these

**1. Item 4 is blocked until 2026-09-30, and this is hard.** `PostSessionNextStepService.java:56` promotes
Challenge to the primary next step at **>= 4/5** on Quick Review. An open `[CHECKPOINT — due 2026-09-30]`
measures precisely whether that 5/5 → 4/5 change improved Quick Review → Challenge conversion, with a named
kill criterion: if it did not, the underlying *"motivation, not placement"* hypothesis reverts to
**unconfirmed**. Item 4 moves the promotion back to mastery — the same mechanism — so shipping it first
destroys the read. **Items 1, 2, 3, 5, 6 and 7 are unblocked.**

**2. Mastery is a PERFECT SCORE — owner ruling, 2026-08-12. Only perfect counts; 4/5 does not.** Two
consequences the proposal has not absorbed yet:

- **The lock copy contradicts the gate.** It reads *"Complete Quick Review to unlock the Quiz"*, but completing
  is not what unlocks it — scoring perfectly is. A learner who finishes with 4/5 has done exactly what the lock
  told them to do and is still locked out, with no stated way forward. **The copy must name the real
  condition.**
- **Whether a perfect score after *Redo Mistakes* counts is still open.** Retries exist and are tracked, and
  every learner can eventually reach perfect through them, so this is the difference between a gate that means
  "you knew it" and one that means "you persisted."

**Worth noting when answering the second point:** the *integrity* rationale — don't hand someone the answer key
to a test they have not sat — is satisfied by any completed Quick Review, because the first attempt at each
question is the genuine assessment and is what `ConceptHealth` records. Requiring a perfect score is a
*progression* goal, not an integrity one. Both are legitimate; they are different arguments, and the gate
should be justified by whichever one is actually intended.

**3. Quick Review length varies by pack.** A perfect score on a 20-question pack is a far harder gate than on a
5-question one — same rule, wildly different difficulty, and the packs a learner meets first are not
length-controlled.

**4. Item 5 has a metrics dependency.** The current *Finish Review* action completes the session. If "Review
the Notes" navigates away instead, someone must decide whether the session **completes, pauses, or is
discarded** — it changes when `QUICK_REVIEW_COMPLETED` fires, and that event feeds the value-loop metric on the
admin funnel.

**5. New analytics events must be added to the `AnalyticsEventType` enum before being fired**, on both backend
and frontend. "Quick Review mastered" now has a definition (perfect score) but its retry treatment is still open — see constraint 2.

**6. An existing curator predicate already exists** (`profileType == TEACHER || role == ADMIN`) for the item 6
exemption.

### What I want your judgement on

1. **Is the lock the right instrument, given the answer-key finding?** Alternatives exist — hide the *answers*
   in the Quiz tab until the learner has been assessed, or retire the saved-quiz tab now that two real practice
   modes exist. Is locking the tab the best of these, or merely the most conservative?
2. **Perfect score is the ruling — does it survive contact with constraint 2?** Specifically: should a perfect
   score reached *through Redo Mistakes* unlock the tab, and what should the lock say to a learner sitting on
   4/5 so it is not a dead end? If you think a perfect-score gate is wrong given a 20-question pack, say so
   plainly — the ruling was made before that asymmetry was on the table.
3. **Does the locked-Quiz / open-Challenge split read as coherent to a learner**, given the reasoning is
   invisible to them? Or does it need saying out loud somewhere?
4. **Is a locked tab the most elegant way to express the progression at all**, or is there a better way to
   communicate Read → Understand → Quick Review → Unlock Quiz → Challenge Quiz?
5. **Item 5** — is sending a learner back to the source note mid-session good practice, or does it break
   momentum in a way that costs more than it gains?

---

## Candidate 2 — Public Explore — DEFERRED, deliberately

Documented, not scoped. The goal stands: public Explore, SEO improvements, better discovery, anonymous
browsing. **Deferred until the current Explore checkpoint reports** (`[CHECKPOINT — due 2026-09-13]`) —
changing discovery again before that read lands would make it unattributable.

Two things already settled that a future scoping pass should not re-litigate: deep `/public/library` pages
(subject listings and note details) are **never** redirected, because that is where the SEO investment lives —
only the bare list page is ever a redirect candidate. And `/collections/published` is genuinely undecided.

---

## Candidate 3 — Verification flow — DEFERRED, deliberately

Documented, not scoped. Onboarding has just shipped and should be measured — completion, activation, first note
creation, first Study Pack generation — **before** moving where verification happens. Its own checkpoint reads
**2026-09-11**, and changing the flow first would make that read unattributable.

For the record when it is picked up: the measured ceiling is small. **366 of 375 signups verify (97.6%)** — the
verification wall costs 9 learners. Any case for letting unverified accounts generate must rest on an indirect
effect, and must reckon with verification currently being the only control against account-farming free LLM
generation, since rate limiting and quota both key on `userId`.

---

## Overall philosophy

I want the Study Pack to feel like a guided journey — **Read → Understand → Quick Review → Unlock Quiz →
Challenge Quiz** — without preventing advanced learners from challenging themselves immediately.

**Please suggest a better UX if you think this progression can be communicated more elegantly.**
