# v0.74.0 proposal — the brief of record

**What this is:** the settled brief for `v0.74.0`, carrying the proposal, the product context, and the hard
constraints verified against real code.

**The product-UX second opinion is IN, not pending.** The owner confirmed 2026-08-12 that the refined proposal
below *was* the second opinion — it came back as the reviewed product-UX direction, not as a draft awaiting
review. **Do not re-send this for consultation, and do not read "consultation prompt" in the filename as an
open loop.** The filename is kept only because the ROADMAP row and this session's history point at it.

**Status: KICKED OFF as `v0.74.0` on 2026-08-12.** No longer planning-only. Constraints verified against code on
**2026-08-12**.

**⚠️ TWO OWNER RULINGS AT KICKOFF SUPERSEDE PARTS OF THIS DOCUMENT. Read this box before writing any Codex
prompt from the text below.**

1. **Constraint 1 is VOID — nothing is parked. All seven items ship, item 4 included.** The owner ruled
   2026-08-12 not to wait on any date. The consequence is real and is being paid, not ignored: item 4 closes
   the after-window of `[CHECKPOINT — due 2026-09-30]`. **Reads (a) and (b) therefore become a pre-deploy
   blocker** — they are already recorded as MEASURABLE NOW, read (a) explicitly *does not improve by waiting*,
   and a ~2-month after-window exists since `becc70ba` (2026-06-16). **And read (c), previously NOT MEASURABLE,
   is unblocked by item 7**, which folds in the two prerequisite events (post-session Challenge CTA impression
   and click). The new threshold ships instrumented rather than blind.
2. **Constraint 4 is SETTLED: "Review the Notes" COMPLETES the session, then navigates to the note.** The
   owner's initial reading — that Quick Review no longer has a session — was checked and does not hold, so the
   conditional resolved to "complete." **Quick Review sessions are load-bearing and must not be removed:**
   `completeSession` writes `ConceptHealth` (`QuickReviewSessionService.java:219-230`) and records
   `COMPLETED_QUICK_REVIEW`, feeding Recent Sessions and `lastSessionCompletedAt`; the frontend fires
   `QUICK_REVIEW_COMPLETED` in the same block (`quick-review/page.tsx:733`); progress persists mid-review for
   resume. **Most decisively, this release's own gate is a completed-session fact** — "did this learner score
   5/5" has no other record, so with no session the Quiz tab could never unlock.
   **Recorded cost:** `handleFinishReview` (`quick-review/page.tsx:956`) is today the *only* route from the
   incorrect-answer screen to the results screen, so a 4/5 learner who takes "Review the Notes" now skips the
   screen where Challenge is promoted. Accepted knowingly.
3. **Item 3 is announcement-only — no competing CTA** (owner ruling, 2026-08-12). It lands on the same results
   screen as the post-session next step; "Take a Challenge" stays the sole primary action.

**On the version number:** an earlier draft was titled "v0.72.0". That version shipped 2026-08-11, as did
`v0.72.1`, and `v0.73.0` shipped 2026-08-12. The next release is **v0.74.0**.

---

# NoteLib v0.74.0 proposal — strengthen the study journey

This is the direction for the next release, after a product-UX pass. The goal is for NoteLib to feel like a
guided study system rather than a collection of features.

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

Selecting it explains why, **naming the real condition** — an earlier draft read *"Complete Quick Review to
unlock"*, which is not the gate and left a learner on 4/5 with no stated way forward:

> *"Score 5/5 on Quick Review to unlock the Quiz. These are the same questions Quick Review asks — practise
> them here once you've shown you know them. Didn't get them all? Redo the ones you missed."*

**The literal "5/5" in that copy is contingent on constraint 3's outstanding check.** If any stored pack holds
a different number of questions, this becomes *"Answer every Quick Review question correctly to unlock the
Quiz"* — same gate, length-agnostic wording.

**The tab must not disappear** — a learner cannot feel they have unlocked something they never knew existed,
and a tab that silently materialises reads as a glitch.

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

**1. ~~Item 4 is blocked until 2026-09-30, and this is hard.~~ VOID — owner ruled 2026-08-12 to ship item 4 now.
See the ruling box at the top of this document for what that costs and how it is paid. The text below is the
historical reasoning, kept because it explains the mechanism.** `PostSessionNextStepService.java:56` promotes
Challenge to the primary next step at **>= 4/5** on Quick Review. An open `[CHECKPOINT — due 2026-09-30]`
measures precisely whether that 5/5 → 4/5 change improved Quick Review → Challenge conversion, with a named
kill criterion: if it did not, the underlying *"motivation, not placement"* hypothesis reverts to
**unconfirmed**. Item 4 moves the promotion back to mastery — the same mechanism — so shipping it first
destroys the read. **Items 1, 2, 3, 5, 6 and 7 are unblocked.**

**2. Mastery is a PERFECT SCORE — owner ruling, 2026-08-12. SETTLED, including the retry case.**

- **The gate is 5/5.** Only perfect unlocks the Quiz tab; 4/5 does not.
- **A perfect score reached through *Redo Mistakes* counts** — owner ruling, 2026-08-12. Persisting until you
  know every item is mastery for this purpose. **This makes the gate reachable by every learner**, which is
  what keeps a perfect-score requirement from being a dead end, and it is why the lock copy above points at
  Redo Mistakes rather than leaving a 4/5 learner to guess.
- **The lock copy has been corrected** to name the real condition — see item 1.

**Worth keeping when this is implemented, because the two rationales pull apart:** the *integrity* rationale —
don't hand someone the answer key to a test they have not sat — is satisfied by any completed Quick Review,
because the first attempt at each question is the genuine assessment and is what `ConceptHealth` records.
Requiring a perfect score is a *progression* goal on top of that. Both are intended here; the perfect-score
gate is the progression layer, not the integrity fix.

**3. Quick Review length — the "varies by pack" worry is retracted for everything generated under current
code, but ONE CHECK IS OUTSTANDING before "perfect 5/5" can be written into learner-facing copy.**

*Verified in code:* newly generated Study Pack quizzes are **always exactly 5 questions**. `schema.json` pins
`quiz` to `minItems: 5, maxItems: 5`; `developer.txt:74` instructs "exactly {QUIZ_COUNT}" from
`STUDY_PACK_QUIZ_QUESTION_COUNT = 5` (`OpenAiLlmStudyPackService.java:64`); and generation is **rejected** if
the count differs (`OpenAiLlmStudyPackService.java:432`). Teacher "Generate Quiz" is a separate flow and does
not write into the saved quiz.

*What that does NOT cover, and why it matters:* Quick Review administers the **whole stored quiz with no
slicing** — `QuickReviewSessionService.java:98` takes `totalQuestions` straight from
`studyPack.getQuiz().size()` at session-creation time. So the gate's shape is whatever is **in the row**, not
whatever the validator enforces now. That validation landed **2026-03-18** (`c78ee9f1`); packs generated before
it — and any remix or copy descended from one (`ShareService.java:90`, `NoteService.java:401`) — were never
subject to it.

*The outstanding check:* `docs/claude-plans/v0.74.0-quiz-length-check.sql` — a one-query distribution of
`jsonb_array_length(quiz)` across `study_packs`. **Run it before writing the copy.**
- **Single row of 5** → the retraction is complete, and "Score 5/5" is exact.
- **Anything else** → the copy must be length-agnostic (*"answer every question correctly"*), and the number of
  affected learners decides whether a backfill is worth it.

**4. ~~Item 5 has a metrics dependency.~~ SETTLED 2026-08-12 — it COMPLETES the session, then navigates.** See
ruling 2 in the box at the top. `QUICK_REVIEW_COMPLETED` fires exactly as it does today, so the admin funnel's
value-loop metric is unaffected; what is lost is the 4/5 learner's sighting of the post-session Challenge
promotion, accepted knowingly.

**5. New analytics events must be added to the `AnalyticsEventType` enum before being fired**, on both backend
and frontend. "Quick Review mastered" is now fully defined: **5/5, whether reached on the first pass or through
Redo Mistakes**. Worth carrying the distinction *in the event payload* even though both unlock — first-pass
perfect and after-retry perfect are different learner states, and separating them is free at instrumentation
time and impossible to recover later.

**6. An existing curator predicate already exists** (`profileType == TEACHER || role == ADMIN`) for the item 6
exemption.

### Settled — do not reopen these without new evidence

These were the open questions before the product-UX read came back. The direction above **is** that read, so
each is recorded as decided rather than pending.

**Two grades of "decided" here, and they are not the same — keep them distinguishable.** Item 2 is an
**explicit owner ruling**. Items 1, 3, 4 and 5 were **carried forward in the returned proposal** — the shape
that came back locks the tab, keeps Challenge open, expresses the progression as a locked tab, and keeps item
5, so they are settled by adoption rather than by a separate ruling on each. That is a sound basis to build on;
it is not the same evidentiary weight, and a future reader should not cite them as though the owner ruled on
each one individually.

1. **The lock is the instrument.** Alternatives were on the table — hide only the *answers* in the Quiz tab, or
   retire the saved-quiz tab entirely now that two real practice modes exist. Locking the tab won: it is the
   narrowest change that restores the signal, and it keeps the saved questions as a real reward rather than
   deleting a surface learners already use.
2. **Mastery = 5/5, and Redo Mistakes counts** — *explicit owner ruling*, 2026-08-12. See constraint 2. The
   pack-length worry that shadowed this question is retracted for current-code generation, with one DB check
   outstanding — see constraint 3.
3. **Locked Quiz + open Challenge stays**, and the reasoning gets said out loud in the lock copy: the Quiz tab
   holds *the same questions Quick Review asks*, which is exactly why it waits and exactly why Challenge — which
   writes its own — does not.
4. **The locked tab is how the progression is expressed.** Read → Understand → Quick Review → Unlock Quiz →
   Challenge Quiz, with the lock as the only visible gate.
5. **Item 5 ships** — sending a learner back to the source note beats ending the loop on a failure. Its open
   question is not whether, but the session-lifecycle decision in constraint 4.

**~~Still genuinely open~~ — CLOSED 2026-08-12 at kickoff.** Constraint 4 is settled: "Review the Notes"
**completes** the session, then navigates. Nothing in candidate 1 is open on design or scope now. **The only
remaining input is data, not a decision:** run `v0.74.0-quiz-length-check.sql` against production before
writing item 1's learner-facing copy.

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

The Study Pack should feel like a guided journey — **Read → Understand → Quick Review → Unlock Quiz →
Challenge Quiz** — without preventing advanced learners from challenging themselves immediately.
