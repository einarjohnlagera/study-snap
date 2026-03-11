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

Quick Review should feel consistent with quiz behavior elsewhere in the app.

---

## Retry Incorrect Questions

After the first pass through the quiz:

- incorrectly answered questions are collected
- those questions are shown again in a retry round
- retry happens once only

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

---

## Quick Review History

Quick Review attempts are persisted as session history.

The Study Pack detail page can display recent review sessions such as:

- completed date
- score
- percentage

This history helps users see their progress over time.

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