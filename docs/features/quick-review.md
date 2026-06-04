# quick-review.md - NoteLib Feature Context

## Goal

Quick Review is the lightweight, low-pressure quiz mode for a Study Pack-ready note.

It should feel:

- fast
- repeatable
- encouraging
- clearly secondary to Challenge Quiz for exam-style practice

## Availability

- available on Free, Plus, and Pro
- uses the base Study Pack quiz instead of a separate LLM-generated session

## Core flow

Study Pack-ready note
-> Quick Review
-> immediate answer feedback
-> one retry round for incorrect questions
-> result screen
-> session history / next-step guidance

## Session model

Quick Review uses its own session model:

- one in-progress session per user per note
- completed sessions remain reviewable from Note Detail
- session history stays note-owned

## Result screen

Current Quick Review result behavior is intentionally simplified around one main next step.

Primary CTA rules:

- weak concepts + Adaptive Practice available -> `Practice Weak Areas`
- strong / perfect result -> `Take Another Challenge`
- otherwise -> `Practice Again`

Secondary actions:

- `Review Answers`
- optional Pro upsell when the weak-area action is locked
- `← Back to Note`

## Confidence and learner level

Quick Review keeps these as secondary actions on the result screen:

- confidence feedback
- learner-level adjustment pills

Current learner-level save toast:

- `Learner level updated. Future Study Packs and quizzes will match this level.`

Meaning:

- learner-level changes affect future generations and future quiz difficulty
- they do not regenerate the current Study Pack

## Weak concepts and Adaptive Practice

- weak concepts are visible to all plans after completion
- Adaptive Practice is available to Free users up to 3 sessions / month, then opens the shared upgrade flow for more sessions
- when Adaptive Practice is unavailable, Quick Review falls back to `Practice Again` as the main next step

## Review history

- completed Quick Review sessions appear in Note Detail session history
- detailed answer review lives on the dedicated session-review page
- session review exports use stored session data only
