# v0.175.0 Challenge Quiz flow and UI polish: audit and proposal (for owner approval)

**Status: APPROVED by the owner, 2026-10-22 ("approve"), including the two clarifications in section 8.** It was first an audit and proposal, 2026-10-22, by Release Implementor, answering the owner's "v0.175.0 Challenge
Quiz Flow and UI Polish" request. **Nothing is implemented.** It was read from code on `releases/v0.175.0`
(`2b7a3224`). The copy follows `docs/product/VOICE_AND_TONE.md`.

**Indexing note:** a release artifact for v0.175.0, under the Backlog Index's exemption.

## 1. Findings (verified in code)

| # | Finding | Where |
|---|---|---|
| F1 | **The mode-selection "continuation" card is shown only on `?entry=mode-selection`.** It has two equal buttons, **Resume** and **Start Fresh**, and the mode cards still render beneath it for a prepared quiz, so they compete. | `page.tsx` about 2346–2381 |
| F2 | **There are two kinds of "Prepare" that look the same.** (a) Choosing the Challenge Quiz card always goes to setup. Pressing Prepare quiz then calls `startSession`, which finds the existing prepared quiz on the same note with the same intent and **returns it**. Nothing new is prepared; the button just re-opens the old quiz. (b) A genuinely new preparation that is fully served from the question pool is also ready at once. To the learner both look like "it went straight to ready". | `ChallengeQuizService.resolveExistingChallengeSession` |
| F3 | **The ready screen is sparse even though the data is there.** The response already carries `title`, `totalQuestions`, `timeLimitSeconds`, `sourceNoteRefs` and `requestedQuestionCount`. Only the X-of-Y and coverage lines use any of it. | `ChallengeQuizStartResponse` |
| F4 | **Quizzes are found by their *primary* note only.** The lookup is `findTop…ByUserIdAndStudyPackIdAndSessionMode…`. A multi-note quiz is invisible from its other notes' pages, and preparing there creates a **separate** quiz. **More than one prepared quiz can exist:** at most one active per learner, primary note and mode, not one globally. | repository and unique index |
| F5 | **⚠️ Defect: the collision check ignores which notes were selected.** For ordinary Challenge it fires only for Board Exam or a Redo Missed mismatch. So with a prepared **A+B** quiz, asking for **A alone** or **A+C** silently returns the A+B quiz. The learner gets notes they didn't choose, and no choice is offered. | `resolveExistingChallengeSession`, the `collision` expression |
| F6 | **A started quiz can already be ended from inside the quiz,** through the leave guard's forfeit (`useQuizSessionGuard`). Removing "Start Fresh" from the card therefore loses no capability. | `page.tsx` about 2135–2138 |
| F7 | **Board Exam shares the mode-selection screen.** Its own continuation states use the same card today. | `page.tsx` |

## 2. Why the redundant Prepare quiz step happens

It is F2(a). The setup screen doesn't know a matching prepared quiz exists, so it offers **Prepare quiz**. The
server then correctly answers "you already have this one" and returns it. So the step isn't creating a duplicate,
and quota is never charged at Prepare. But it is a wasted click that looks like an instant preparation.

## 3. Proposed state-aware navigation (ordinary Challenge only; Board Exam unchanged, C6)

When the learner opens Challenge Quiz for a note, from the note page, mode selection or the dashboard, look up
the active ordinary quiz **for this note as primary**:

| Existing quiz | What happens |
|---|---|
| Ready, not started, **exact match** | Choosing Challenge Quiz goes **straight to the ready briefing**, with no setup and no Prepare quiz. |
| Generating, exact match | Goes straight to the preparing screen. |
| Started | **Resume quiz,** as today. |
| None | The normal setup and **Prepare quiz**. |

**Exact match** means the same primary note, the same set of additional notes (order-independent) and the same
Redo Missed flag.

**For multi-note, the learner still picks notes in setup,** because the selection defines the quiz. When they
press Prepare quiz:
- an exact match opens the existing quiz;
- **a different selection gets the existing collision choice** (this fixes F5). It is never silently replaced and
  never silently discarded.

Backend preparation is never skipped for a **new** quiz. A full pool hit still goes through `startSession`, so
claims are taken correctly.

## 4. The continuation card

**Its place on the screen:**
- one compact row above the mode cards, in neutral styling;
- **one** action;
- the mode cards and their "Recommended" badges stay the primary choice.

**Copy by state:**

| State | Card text | Action |
|---|---|---|
| Ready, single-note | **Your Challenge Quiz is ready** · {N} questions · {M} min | **Open prepared quiz** |
| Ready, multi-note | **Your Challenge Quiz is ready** · {N} questions from {n} notes, including this one | **Open prepared quiz** |
| Generating | **We're getting your Challenge Quiz ready** · {note title or "{n} notes"} | **View preparation** |
| Started | **You have a Challenge Quiz in progress** · {N} questions | **Resume quiz** |

- **Start Fresh is removed from the card.** A started quiz can still be ended from inside it (F6).
- **Choosing another mode works normally.** Only a genuine conflict shows the collision choice, and **nothing is
  discarded without the learner choosing to.**
- **Related multi-note quizzes** (F4: this note appears in a quiz whose primary is another note) show one quiet
  line on this note's page:
  - "This note is also in a prepared Challenge Quiz with {n} other notes. **Open it**";
  - it links to that quiz's own page;
  - it needs a small new read: active quizzes whose sources include this note;
  - **this is a "Should"** (section 7).

**Board Exam:** its continuation stays exactly as today (C6). The polished card applies only to ordinary Challenge.

## 5. Collision and discard behaviour (after the fix)

- **Same note, same intent, same note selection:** returns the existing quiz. That is today's behaviour.
- **Same note, but a different note selection, Board Exam, or a Redo Missed mismatch:** the existing collision
  choice ("Resume your prepared quiz" / "Start something else"). Discard happens only if the learner chooses it,
  and it never uses an attempt.
- **Visiting another note or choosing another mode never discards anything.**
- **More than one prepared quiz may exist,** one per primary note. That is the existing rule, now stated, not
  changed.
- **Started quizzes keep today's rules.**

## 6. The ready briefing (replaces the sparse ready screen)

Every value comes from the session; nothing is hardcoded.

```
All set! Your quiz is ready.
{Note title}                          (multi-note: {Primary title} + {n} more notes)
{N} questions · {M} minutes           (from totalQuestions and timeLimitSeconds)
From {n} of {m} notes                 (multi-note; approved line)
Your quiz is ready with X of Y questions.   (only when short; approved line)

Your timer and monthly attempt start only when you press Start quiz.
   (Redo Missed: "Your timer starts only when you press Start quiz. Redo Missed Questions doesn't count toward your monthly quiz limit.")

[ Start quiz ]                         one prominent action
Continue reviewing                     quiet text link
```

**New copy, pending owner approval:**
- "All set! Your quiz is ready."
- "{N} questions · {M} minutes"
- "Your timer and monthly attempt start only when you press Start quiz."
- the Redo Missed variant above;
- the four continuation-card lines and their actions;
- the related-quiz line.

Everything else is already approved.

## 7. Smallest safe implementation scope

**Must** (one PR, through Codex):
1. **F5 fix (backend).** A different note selection on the same primary note is a collision, not a silent return.
   - Compare the requested additional notes, as a sorted set, with the existing quiz's `sourceNoteRefs`.
   - Tests:
     - single-note versus multi-note;
     - a different additional set;
     - the same set in a different order (resumes);
     - the discard and retry flow;
     - MockMvc for the collision shape.
2. **Direct open (frontend).** Choosing the Challenge card with an exact-match ready or generating quiz opens it
   directly.
   - Tests:
     - ready lands on the briefing;
     - generating lands on the preparing screen;
     - no match lands on setup;
     - Board Exam is unchanged;
     - nothing ever calls `start-quiz`.
3. **The continuation card** (frontend): one action per state, no Start Fresh, compact styling. Tests for each
   state, and a Board Exam regression test.
4. **The ready briefing** (frontend): real values, the reminder, one action. Tests for single-note, multi-note,
   underfilled and Redo Missed. A `next build` check plus the full suite.

**Should** (same PR if small, else a follow-up): the related multi-note line on secondary notes. It needs one
read-only endpoint and a MockMvc test.

**Later:** progress on the in-progress card (for example "8 of 15 answered"), and a casing sweep of the buttons
shared with Board Exam.

**Unchanged:**
- the quota and timer rules (Start quiz is the only start);
- claims and their release;
- expiry and recovery;
- idempotency across double-clicks, refreshes, tabs and deep links (existing tests must stay green);
- Board Exam and Long Exam.

**Verification tier:** this touches `startSession` collision logic, a shared method, so it is an input to the
signoff pressure test.

## 8. Owner clarifications, approved 2026-10-22

1. **The continuation card borrows the design of the "Today's Focus" card** (`collection-detail-page-client.tsx`):
   a small grey eyebrow, a bold title, one meta line, and a blue primary button with an arrow. It is a
   **lighter** variant:
   - **one action and no Quick actions row,** with tighter padding and a lighter background, so the mode cards
     stay the main choice;
   - pull the shared look into a small reusable component, used here; migrating Today's Focus is optional and
     not required.

   For example: eyebrow "CONTINUE WHERE YOU LEFT OFF", title "Your Challenge Quiz is ready", meta "15 questions ·
   22 minutes · {note title}", button "Open prepared quiz →".
2. **Smart Prepare:**
   - **An existing prepared quiz removes the Prepare quiz step entirely.** Choosing Challenge Quiz opens it (§3).
   - **With no prepared quiz, the button stays "Prepare quiz"** even when the question pool can cover the whole
     quiz; the briefing then appears almost instantly. There is no one-click "Start quiz" from setup, because:
     - pool coverage can't be known reliably before claiming;
     - Start quiz begins the timer and the monthly attempt, so the learner should see the count and time first;
     - it keeps a single start path.


## 9. Final strings approved by the owner, 2026-10-22 (verbatim)

- **Quiet ready-screen link:** "Want to study different notes?". Use it only if the destination really allows
  selecting different notes, and keep the existing collision and discard confirmation.
- **Prepared Redo Missed card:** "Your missed-question review is ready!"
- **Preparing Redo Missed card:** "We're getting your missed-question review ready…"
- **"Redo Missed Questions" stays the formal feature name** everywhere else.
- **These are copy-only:** routing, session state, quota behaviour and confirmation safeguards are preserved.
- **No production deployment is authorized** by this approval.
