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
