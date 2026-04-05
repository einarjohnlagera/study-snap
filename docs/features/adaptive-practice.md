# adaptive-practice.md - NoteLib Feature Context

## Goal

Adaptive Practice reinforces weak concepts from prior quiz performance on a Study Pack-ready Note.

## Generation Rules

- generated from Study Pack summary, key concepts, and weak concepts
- must stay focused on weak concepts only
- learner-level aware, defaulting to `College` when learner level is missing
- may be slightly easier than Challenge Quiz, but should remain targeted and useful
- may include quantitative reinforcement questions when the weak concepts are computation-heavy
- explanations should reinforce the weak concept clearly and step through calculations when relevant

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
