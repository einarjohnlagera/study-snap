# Quick Review

Quick Review is the lightweight quiz-based review mode for a Study Pack.

Its goal is to help users reinforce learning through active recall and repeated practice.

---

## Purpose

Quick Review turns a saved Study Pack into an interactive review session.

It is designed to feel:

- lightweight
- focused
- repeatable
- low-pressure

Quick Review is not intended to be a formal exam mode.
For exam-style timed practice, use Challenge Quiz (Premium).

---

## Entry Points

Users can start Quick Review from:

- Study Pack detail page
- dashboard recommendation card
- resume prompt if an unfinished session exists

Study Pack detail quiz-entry hierarchy:

- primary action: `Start Quick Review`
- secondary action: `Challenge Quiz` (Premium-gated for Free users)
- `Adaptive Practice` should only be shown when weak concepts exist

---

## Core Flow

Study Pack  
→ Quick Review  
→ immediate answer feedback  
→ retry incorrect questions once  
→ results screen  
→ session history  
→ dashboard recommendation

---

## Session Model

Quick Review uses a separate session model from the Study Pack itself.

StudyPack stores static learning content:

- title
- summary
- key concepts
- quiz

QuickReviewSession stores user learning activity:

- progress state
- score
- retry count
- timestamps
- completion status

This separation allows:

- multiple attempts
- session history
- analytics
- future spaced repetition

---

## Session States

Quick Review sessions support:

- IN_PROGRESS
- COMPLETED

Only one IN_PROGRESS session may exist per user per Study Pack.

If a user leaves an unfinished session, they can resume it later.

---

## Resume Behavior

If the user leaves Quick Review before finishing:

- the session remains IN_PROGRESS
- the current question index is preserved
- current round state is preserved
- retry state is preserved if the user left during retry

When the user starts Quick Review again for that Study Pack:

- existing IN_PROGRESS session should be reused
- a new session should not be created unless the previous one is already COMPLETED

---

## Question Behavior

Quick Review presents quiz questions one at a time.

For each question:

- user selects an answer
- correct answer is highlighted
- explanation is shown immediately
- user moves to the next question
- each quiz item carries concept metadata to identify the key idea being tested
- concept metadata should be concise and non-null for newly generated Study Packs
- concept metadata is generated during the same Study Pack AI generation request
- quiz feedback colors use conventional semantics:
  - correct answer: green + `✓ Correct`
  - selected incorrect answer: red + `✗ Incorrect`
  - if user selects incorrectly, both selected wrong (red) and correct answer (green) are shown
  - non-selected, non-correct options remain neutral
  - avoid blue for correctness feedback states

Quick Review should feel consistent with quiz behavior elsewhere in the app.

Plan gating:
- core Quick Review (question flow, retry, score) is available on Free and Premium
- Weak Concept Detection is Premium-only
- Adaptive Practice is Premium-only
- Challenge Quiz is Premium-only

---

## Retry Incorrect Questions

After the first pass through the quiz:

- incorrectly answered questions are collected
- those questions are shown again in a retry round
- retry happens once only
- retrying incorrect questions is the primary in-session reinforcement action
- users may also finish the review directly without entering retry

Rules:

- only incorrect questions are retried
- retry does not create a new session
- retry does not increase the original total question count
- there is no third round

Example:

Original quiz: 5 questions  
First pass: 3 correct, 2 incorrect  
Retry round: only the 2 incorrect questions appear

If both retry questions are answered correctly, the final score becomes 5 / 5.

---

## Scoring

Quick Review stores:

- correctAnswers
- totalQuestions
- scorePercentage
- retryCount

Score percentage is based on the original total question count.

Retry rounds do not increase totalQuestions.

Example:

Original totalQuestions = 5  
Retry round contains 2 questions  
Final score still uses totalQuestions = 5

---

## Results Screen

After Quick Review is completed, the user sees:

- correct answers
- total questions
- score percentage
- retry count
- motivational feedback
- optional AI-generated Study Tip when incorrect answers exist

Examples of motivational messaging:

### 100%
Excellent work! You mastered this topic.

### 80–99%
Great job! You're very close to mastering this.

### 50–79%
Good effort. A quick retry can help reinforce what you missed.

### Below 50%
Nice attempt. Let's review the concepts and try again.

The results screen may also show:

- previous attempt
- best score
- weak concepts (derived from incorrectly answered question concepts)

Weak concept detection:
- when a Premium session is completed, incorrect answers are mapped to their `concept` values
- duplicate concepts are deduplicated for the session summary
- results display a `Weak Concepts` section when at least one concept was missed (Premium)
- Free plan users continue to get normal score/retry flow without weak-concept output
- concise concept metadata improves weak-area quality and adaptive practice targeting

### Study Tip

When the user misses one or more questions, Quick Review may show a short `Study Tip` on the results screen.

Behavior:

- backend builds a lightweight list of incorrect question context
- context is sent to the LLM to generate one concise tip (1-2 sentences)
- tip focuses on what concept to review next
- tip generation is configurable:
  - `studysnap.quick-review.study-tip.enabled`
  - `studysnap.quick-review.study-tip.min-incorrect-count`
  - `studysnap.quick-review.study-tip.max-questions`
- tip generation is skipped when:
  - feature is disabled
  - no incorrect answers exist (including `100%` score)
  - incorrect answers are below configured minimum
- to reduce cost, only incorrect questions are sent and input is capped by `max-questions`
- if tip generation fails, results still load normally and the tip is hidden
- if there are no incorrect answers, no tip request is sent
- Study Tip text should be fully readable in the results UI (no unintended clipping or silent truncation)

### Confidence Feedback (Optional)

After completion, results include an optional confidence prompt:

- `How confident did you feel about this topic?`
- options:
  - `Very confident` -> `HIGH`
  - `Somewhat confident` -> `MEDIUM`
  - `Not confident` -> `LOW`
- confidence selection is optional and does not block completion
- after selection, UI shows `Thanks for the feedback.`
- confidence is stored as nullable `confidence_level` on the Quick Review session

Current scope:
- lightweight capture only (no heavy analytics behavior yet)
- data is intended for future weak-concept tuning, adaptive recommendations, and confidence trend insights

### Adaptive Practice (Weak Areas)

Quick Review can offer a follow-up adaptive practice set based on weak concepts from the latest completed session.

Behavior:

- weak concepts are sourced from the most recent completed Quick Review for the same Study Pack
- adaptive practice is available only when weak concepts exist
- adaptive practice is Premium-only
- adaptive quiz is newly generated and separate from the original Study Pack quiz
- adaptive set contains 5, 7, or 10 questions based on weak-concept count:
  - weak concepts <= 2: 5 questions
  - weak concepts <= 4: 7 questions
  - weak concepts >= 5: 10 questions
- each adaptive question has 4 choices and one correct answer
- adaptive questions target weak concepts and prioritize understanding over wording recall
- adaptive generation uses summary + key concepts + weak concepts only (no extracted OCR text)
- adaptive generation must not duplicate stored Study Pack quiz questions
- adaptive in-progress sessions must be reused to avoid duplicate LLM generation calls
- generation failure should not break Quick Review results; the UI should fail gracefully

Entry point:

- results screen shows `Practice Weak Areas` when weak concepts exist
- Free users should see a clear upgrade CTA instead of adaptive-practice launch
- this starts a separate follow-up practice flow
- Adaptive Practice usage limit is 50/month (tracked separately from Study Pack credits)

### Retry-transition CTA hierarchy

When missed questions exist after the first pass:

- primary action: `Retry Incorrect Questions` (continue reinforcement in the same session)
- secondary action: `Finish Review` (complete the session with current score)

This keeps retry as the main reinforcement mechanic while allowing users to end a short review loop cleanly.

### Results CTA hierarchy

After completion:

- primary action: `Back to Study Pack`
- guided next step when struggling: `Practice Weak Areas` (Adaptive Practice, Premium-gated)
- guided next step when performing well: `Start Challenge Quiz` (Premium-gated)
- secondary action: `Practice Again` (optional, lower emphasis)

`Practice Again` is not the primary post-review action, especially for short quizzes.

## Challenge Quiz (Premium)

Challenge Quiz is a separate Premium review mode for longer timed practice.

Behavior:

- generates new questions via LLM (not from stored Study Pack quiz reuse/shuffle)
- generation input is summary + key concepts only (no extracted OCR text)
- question count and difficulty adapt from latest completed Quick Review score:
  - score < 50%: 10 questions, easy-medium
  - score < 80%: 12 questions, medium
  - score >= 80%: 15 questions, medium-hard
- generated questions must not duplicate stored Study Pack quiz questions
- uses a fixed 10-minute timer
- returns a final score and percentage at completion
- in-progress challenge sessions must be reused to prevent duplicate LLM calls
- discourage accidental exits while in progress:
  - remove casual back affordances on the active challenge screen
  - show leave confirmation where supported by browser/app navigation APIs
- persist challenge progress continuously for recovery:
  - current question index
  - selected answers
  - timing state
- timer continues in real time while active; leaving/reloading does not pause timer
- on return to an unfinished challenge session:
  - resume if time remains
  - auto-complete if timer has already expired
- during active answering (before submit), selected answer must use full-box neutral highlight for clear visibility
- post-completion review should use correctness colors:
  - correct answers highlighted green
  - selected incorrect answers highlighted red
  - unrelated unselected options remain neutral
- challenge completion statistics are computed from session data only (no LLM):
  - score summary (`correct`, `total`, `%`)
  - performance label (`Excellent`, `Good`, `Fair`, `Needs Improvement`)
  - concept breakdown (`correct/total` and accuracy per concept)
  - weak concepts where concept accuracy is below 60%
- weak concepts from challenge completion should be available for Adaptive Practice targeting

Gating and usage:

- Free users should see an upgrade prompt instead of challenge launch
- Premium users can start Challenge Quiz
- Challenge Quiz usage limit is 50/month
- usage is counted on quiz start
- Challenge Quiz usage does not deduct from monthly Study Pack generation credits
- Challenge Quiz and Adaptive Practice quotas are independent from each other

---

## Quick Review History

Quick Review attempts are persisted as session history.

The Study Pack detail page can display recent review sessions such as:

- completed date
- score
- percentage
- mode label (`Quick Review` or `Challenge Quiz`)

This history helps users see their progress over time.

The Study Pack detail page includes a broader `Performance Overview` for the current Study Pack with separate stats for both quiz modes:

- Quick Review stats:
  - `Best Score` (highest `scorePercentage`)
  - `Attempts` (total completed sessions)
  - `Last Score` (most recent completed session score)
  - `Last Completed` (most recent completed session timestamp)
- Challenge Quiz stats:
  - `Best Score`
  - `Attempts`
  - `Last Score`
  - `Last Completed`
  - `Latest Performance` (`Excellent`, `Good`, `Fair`, `Needs Improvement`)

If there are no completed sessions yet, the page shows a simple empty prompt to start the first Quick Review.

### AI Study Coach (Study Pack detail)

The Study Pack detail page includes a compact `AI Study Coach` panel powered by existing session data.

Behavior:

- focus areas come from latest weak concepts from Quick Review and/or Challenge Quiz
- focus areas are shown concisely (up to 4 concepts)
- suggested next step adapts to learning state:
  - weak concepts available: practice weak concepts
  - completed review without weak concepts: continue reviewing
  - no completed review yet: start first Quick Review

Scope:

- uses existing session data only
- no additional LLM call is required

---

## Activity Tracking

Quick Review records activity events including:

- STARTED_QUICK_REVIEW
- COMPLETED_QUICK_REVIEW

Activity events support:

- future analytics
- dashboard recommendation improvements
- usage insights

Activity tracking must not break core review behavior if tracking fails.

---

## UX Principles

Quick Review should feel:

- calm
- focused
- mobile-friendly
- easy to continue
- rewarding after completion

It should encourage repeated practice rather than perfection pressure.

---

## Non-Goals

Quick Review does not currently include:

- timed exam mode
- spaced repetition scheduling
- advanced analytics dashboard
- difficulty switching during a session
- multiplayer or competitive quiz features

These may be added later.
