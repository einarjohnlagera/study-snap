This file consolidates quiz-quality and prompt-quality guidance.

## Goal

Practice quizzes should feel like real study reviewers, not generic AI trivia.

## Output structure

The LLM should produce:
- title
- summary
- keyConcepts
- quiz[]

The strict JSON contract is documented in `docs/ai/PROMPTS.md`.

## Quiz quality principles

Include a balanced mix of:
- recall questions
- understanding questions
- application questions

Detailed expectations:
- recall questions test facts, terms, and definitions
- understanding questions test conceptual understanding
- application questions test simple practical use of the concept
- all questions must remain answerable from the notes
- if notes are too short or too simple, prioritize recall and understanding
- avoid hallucinating details not present in the notes
- if OCR noise exists, ignore gibberish lines
- if OCR text was edited by the user, the edited text becomes the authoritative input

## Question counts by plan

- Demo: 3 questions
- Free: 5 questions
- Premium: 10–20 questions

## Validation and retry

Backend should:
1. call the LLM
2. parse JSON
3. validate JSON schema

If validation fails:
- run one repair pass
- if it still fails, return a friendly error

## Future improvements

- better topic-aware distractors
- stronger premium quiz experiences
- mock exam mode
