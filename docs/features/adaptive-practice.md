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

Generated items must include:

- `question`
- `choices` (4)
- `answer` (`A` / `B` / `C` / `D`)
- `explanation`
- `concept`
