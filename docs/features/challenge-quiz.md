# challenge-quiz.md - NoteLib Feature Context

## Goal

Challenge Quiz is the timed, exam-style quiz mode built on top of a Study Pack-ready Note.

## Generation Rules

- generated from the Study Pack summary and key concepts
- should not repeat the Study Pack / Quick Review question set
- learner-level aware, defaulting to `College` when learner level is missing
- question count adapts by recent Quick Review performance
- may include quantitative or computation questions when the note context supports them
- explanations should teach clearly and step through calculations when relevant

## Contract

Generated LLM output must include:

- `question`
- `choices` (4)
- `answer` (`A` / `B` / `C` / `D`)
- `explanation`
- `concept`

Runtime/session rules:

- Backend normalization must convert the LLM answer letter into canonical `correctIndex` before persistence.
- Frontend rendering may shuffle displayed choices, but `A` / `B` / `C` / `D` stay UI-only labels derived from displayed order.
- Session grading must compare selected canonical choice indexes against canonical `correctIndex`.

## Entry Flow

- `Student` and `Board Taker` use the same initial mode-selection screen.
- Entering `Challenge Quiz` from Note Detail must land on that same shared mode-selection screen.
- Both modes must remain visible on that first screen:
  - `Challenge Quiz`
  - `Board Exam Mode`
- Persona difference is emphasis only:
  - `Student` -> `Challenge Quiz` is visually emphasized by default
  - `Board Taker` -> `Board Exam Mode` is visually emphasized by default
- Do not skip directly into setup based only on persona.

## Premium Gating

- `Board Exam Mode` is Premium-only.
- Free users who click `Board Exam Mode` from the shared mode-selection screen must see the shared Premium upsell modal.
- Do not replace the Premium upsell with a normal setup screen for free users.
- Monthly quiz-limit handling is separate:
  - free user + premium-only path -> Premium upsell modal
  - free user + exhausted Challenge Quiz credits -> Premium upsell modal
  - premium user + exhausted monthly usage -> quiz-limit state / limit messaging
