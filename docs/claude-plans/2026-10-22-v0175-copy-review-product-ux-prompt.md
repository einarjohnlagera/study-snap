# v0.175.0 copy review: consultation prompt for Product UX

**How to use this:** paste into a Product UX session, in this order:
1. `docs/gpt-contexts/GPT_CONTEXT.md`;
2. `docs/gpt-contexts/QUIZ_AND_PRACTICE_CONTEXT.md`;
3. everything below the horizontal rule.

The part below the rule stands on its own.

**Why it exists:** v0.175.0 is fully built. Every learner-facing string was read from the code on 2026-10-22,
`releases/v0.175.0` at `a4555d42`. Four strings were never reviewed, a few error fallbacks were never
reviewed, and the approved copy has inconsistent capitalisation. The owner wants Product UX's recommendation
before deciding.

**Indexing note:** a one-off consultation prompt, under the Backlog Index's *release artifact* exemption.
Record the answers as owner decisions in `RELEASES.md`.

---

# NoteLib v0.175.0 "Quiz Ready": final copy review

I need product-UX judgment on a small set of learner-facing strings before release. For each item, give:
- the **final wording**;
- a one-line reason.

Keep what is already approved unless you see a real problem, and if you change an approved string, say why.

## What the release does (context only)

Challenge Quiz now works like this:
- **Prepare Quiz** prepares questions in the background ("Getting your quiz ready"), with a **Continue
  reviewing** link.
- The learner lands on **"Your quiz is ready!"**, and gets one in-app notification if they left.
- The timer and the monthly quota start **only** when they press **Start Quiz**. Leaving, failing, discarding
  or an expired unstarted quiz never uses an attempt.
- A quiz built from several notes is ready only if it has at least two-thirds of the requested questions from
  at least two-thirds of the notes.
- During a quiz, **+5 Questions** now adds questions in the background, while the learner keeps answering.

**Rules every string must follow:**
- never name an AI model, and never suggest a FREE/paid quality difference;
- promise no completion time;
- claim no refund, and no reuse of saved questions beyond what the product guarantees (an unstarted quiz is
  never charged; Redo Missed never charges);
- no email or push wording, because notifications are in-app only.

## Part 1: four strings never reviewed (decide these)

| # | Where it appears | Current text | Notes |
|---|---|---|---|
| 1 | +5: the learner pressed **Stop adding**, then **Try again** right away, while the stopped request is still winding down (usually a few seconds) | "Finishing the previous request…" | It disappears on its own and the new +5 starts automatically. "Request" may be too technical. |
| 2 | The mode-selection screen, when the learner already has a quiz **still being prepared**. It sits under the approved title "Your quiz is still being prepared", with a **Resume** button and the other quiz modes | "Your quiz is being prepared. Resume to follow its progress, or choose another mode." | Choosing another mode leads to the approved collision choice, which can discard the prepared quiz. |
| 3 | The **Prepare Quiz** button while its request is in flight (a second or two) | "Preparing…" | Board Exam's equivalent says "Starting...". |
| 4 | The screen-reader label on the progress indicator of "Getting your quiz ready" | "Preparing quiz" | Never visible; read aloud by assistive technology. |

## Part 2: capitalisation consistency (decide one rule)

Approved copy currently mixes button casing:
- the preparing screen says **"Continue reviewing"**, but the failure screen says **"Continue Reviewing"**;
- the failure screen says **"Try Again"**, but +5's failure says **"Try again"**;
- other buttons in the same flow: "Prepare Quiz", "Start Quiz", "Prepare New Quiz", "Resume your prepared quiz",
  "Start something else", "Stop adding", "Open your prepared quiz".

**What I need:** one casing rule for buttons in this flow, either Title Case or sentence case, and the list of
buttons it changes. A change to an approved string is fine if it's only casing.

## Part 3: rare error fallbacks (approve or edit)

These show only if a request fails unexpectedly; the normal cases lead to the screens above. Today they are
plain sentences from the server or the page.

| Situation | Current text |
|---|---|
| The page couldn't reload the quiz | "Could not load the current quiz." |
| Stop adding failed | "Could not stop adding questions." |
| Start Quiz while still preparing (normally redirected to the preparing screen) | "This Challenge Quiz is still being prepared." |
| Start Quiz on a failed preparation (normally the failure screen) | "This Challenge Quiz could not be prepared." |
| Start Quiz on an expired one (normally the expired screen) | "This prepared quiz has expired." |
| A discard that no longer applies (the page reloads instead) | "The prepared quiz cannot be discarded for this request." |
| A second +5 while one is running (normally item 1's message) | "Questions are already being added." |

**What I need:** approve them as they are, or give edited wording. Flag any that should tell the learner what to
do next.

## For reference: the approved copy (change only with a reason)

**Setup:** "Prepare Quiz"; "This quiz counts toward your monthly limit only when you press Start Quiz. Leaving
before you start won't use an attempt."

**Preparing:** "Getting your quiz ready" / "We're preparing your questions. You can stay here or continue
reviewing while we finish." / "Continue reviewing"

**Ready:**
- "Your quiz is ready!" / "Your questions are prepared. Start whenever you're ready." / "Start Quiz"
- when short: "Your quiz is ready with X of Y questions."
- multi-note: "From n of m notes"
- Redo Missed: "Redo Missed Questions doesn't count toward your monthly quiz limit."

**Failed:** "We couldn't finish preparing your quiz" / "Your quiz isn't ready yet. You can try again or return to
reviewing." / "Try Again", "Continue Reviewing"

**Expired:** "This prepared quiz has expired" / "Your prepared quiz is no longer available, but you can create a
new one when you're ready." / "Prepare New Quiz"

**Collision:** "You already have a quiz ready" / "Your quiz is still being prepared" / "You can return to your quiz
or start something else. If you start something else, your unstarted quiz will be discarded without using a
monthly attempt." / "Resume your prepared quiz", "Start something else"

**Dashboard:** "Open your prepared quiz"

**Notification:** "Your quiz is ready!" / "Pick up where you left off." / "Open your prepared quiz"

**+5:** "Adding questions…" / "{n} questions added" / "Still adding questions…" / "Stop adding" / "Couldn't add
questions" / "Try again"

## Output format

Return:
- a table with the columns **Item | Final text | Reason**, covering Part 1's four strings, Part 3's seven
  fallbacks, and any approved string you change;
- Part 2's casing rule;
- the list of buttons that rule changes.

---

# Result: owner and Product UX direction (2026-10-22) and the final copy diff: APPROVED by the owner, 2026-10-22 ("approve the diff")

**Direction adopted:** `docs/product/VOICE_AND_TONE.md`, which holds the owner's voice-and-tone direction and
Product UX's note, verbatim. The diff below applies it to v0.175.0. **It is copy only:**
- no behaviour, rule or configuration change;
- **nothing is built or deployed until the owner approves this diff.**

Board Exam and Long Exam strings, and buttons shared with them ("Start Fresh", "Resume", "Starting..."), are
**not** changed in this release (C6). They are listed for a later casing sweep.

| # | Where | Before | After |
|---|---|---|---|
| 1 | +5: Try again just after Stop | Finishing the previous request… | Finishing up your last set of questions… |
| 2 | Mode selection, quiz still preparing | Your quiz is being prepared. Resume to follow its progress, or choose another mode. | We're still getting your quiz ready. Resume to check on it, or choose another mode. |
| 3 | Prepare button while in flight | Preparing… | Preparing… *(unchanged)* |
| 4 | Screen-reader label, preparing screen | Preparing quiz | Preparing your quiz |
| 5 | Setup button | Prepare Quiz | Prepare quiz |
| 6 | Ready screen button | Start Quiz | Start quiz |
| 7 | Expired button | Prepare New Quiz | Prepare a new quiz |
| 8 | Failure buttons | Try Again · Continue Reviewing | Try again · Continue reviewing |
| 9 | +5 button | +5 Questions | +5 questions |
| 10 | Setup monthly line | This quiz counts toward your monthly limit only when you press Start Quiz. Leaving before you start won't use an attempt. | This quiz counts toward your monthly limit only once you start it. Leaving before you start won't use an attempt. |
| 11 | +5 success | {n} questions added | Added {n} more questions. |
| 12 | +5 failure | Couldn't add questions | We couldn't add more questions. Your answers are safe. |
| 13 | Fallback: reload failed | Could not load the current quiz. | We couldn't load your quiz. Please refresh the page to try again. |
| 14 | Fallback: Stop failed | Could not stop adding questions. | We couldn't stop adding questions. Any new ones will appear when they're ready. |
| 15 | Server: still preparing | This Challenge Quiz is still being prepared. | Your quiz is still getting ready. We'll let you know in NoteLib when it's ready. |
| 16 | Server: preparation failed | This Challenge Quiz could not be prepared. | We couldn't finish preparing this quiz. Please try again. |
| 17 | Server: expired | This prepared quiz has expired. | This prepared quiz has expired. You can prepare a new one anytime. |
| 18 | Server: discard no longer applies | The prepared quiz cannot be discarded for this request. | Your prepared quiz has changed. Please check it and try again. |
| 19 | Server: +5 already running | Questions are already being added. | We're already adding questions. They'll appear as soon as they're ready. |

**Unchanged and already in the voice:**
- "Your quiz is ready!"
- "Getting your quiz ready" and its body
- "Continue reviewing" on the preparing screen
- the collision copy
- "Open your prepared quiz"
- the notification
- "Adding questions…" and "Still adding questions…" / "Stop adding"
- the failure and expired bodies
- the X-of-Y, coverage and Redo Missed lines

**Truth check against the code:**
- **#12:** +5 never touches answers.
- **#14:** a failed Stop leaves the +5 running, and its questions are appended when ready.
- **#15:** the readiness notification is in-app and fires once on ready.
- **#17:** Prepare is always available after expiry.
