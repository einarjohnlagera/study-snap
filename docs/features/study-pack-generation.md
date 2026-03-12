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

## OCR input flow (image notes)

Study Pack generation supports an OCR-first flow when users upload image notes.

Frontend OCR UX goals:
- clear upload guidance before OCR starts
- visible image selection confirmation (including preview)
- explicit OCR processing states
- clean extracted-text review before final generation

Processing states shown in the UI:
- `idle`: no active OCR operation
- `uploading`: image is being uploaded
- `extracting text`: OCR is running
- `success`: OCR text extracted successfully
- `failure`: OCR could not complete or validation failed

Validation/error handling in the OCR path:
- unsupported file type (`png/jpeg/webp` only)
- image too large (max `5 MB` in current UX guidance)
- no readable text detected
- generic OCR/extraction failures

Errors should remain supportive and actionable.

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

## Extracted text review/edit step

When OCR returns extracted text for confirmation:
- the extracted text is shown in an editable review area
- users can correct OCR mistakes before continuing
- edited text is treated as the final source of truth for Study Pack generation

Authoritative input rule:
- if the user edits extracted OCR text, generation must use the edited text (not the original raw OCR output)

## Future improvements

- better topic-aware distractors
- stronger premium quiz experiences
- mock exam mode
