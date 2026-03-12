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

---

## Entry Points

Users can start Quick Review from:

- Study Pack detail page
- dashboard recommendation card
- resume prompt if an unfinished session exists

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

Quick Review should feel consistent with quiz behavior elsewhere in the app.

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
- when a session is completed, incorrect answers are mapped to their `concept` values
- duplicate concepts are deduplicated for the session summary
- results display a `Weak Concepts` section when at least one concept was missed

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

### Retry-transition CTA hierarchy

When missed questions exist after the first pass:

- primary action: `Retry Incorrect Questions` (continue reinforcement in the same session)
- secondary action: `Finish Review` (complete the session with current score)

This keeps retry as the main reinforcement mechanic while allowing users to end a short review loop cleanly.

### Results CTA hierarchy

After completion:

- primary action: `Back to Study Pack`
- secondary action: `Practice Again` (optional, lower emphasis)

`Practice Again` is not the primary post-review action, especially for short quizzes.

---

## Quick Review History

Quick Review attempts are persisted as session history.

The Study Pack detail page can display recent review sessions such as:

- completed date
- score
- percentage

This history helps users see their progress over time.

The Study Pack detail page also includes a compact `Review Performance` summary based on completed sessions for the current Study Pack:

- `Best Score` (highest `scorePercentage`)
- `Attempts` (total completed sessions)
- `Last Score` (most recent completed session score)
- `Last Reviewed` (most recent completed session timestamp)

If there are no completed sessions yet, the page shows a simple empty prompt to start the first Quick Review.

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
