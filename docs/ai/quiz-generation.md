# quiz-generation.md - NoteLib AI Quiz Generation

## Goal

Keep quiz generation reliable, learner-aware, and mode-specific without splitting ownership away from Notes.

## Shared JSON Contract

All generated quiz payloads must return JSON only with this shape:

```json
{
  "questions": [
    {
      "question": "...",
      "choices": ["A", "B", "C", "D"],
      "answer": "B",
      "explanation": "...",
      "concept": "..."
    }
  ]
}
```

Rules:

- exactly 4 choices
- `answer` must be `A`, `B`, `C`, or `D`
- `explanation` is required
- `concept` is required
- no markdown
- no comments
- no prose before or after JSON

## Learner Level Guidance

If the user has no saved learner level, default prompt difficulty to `College`.

Expected prompt behavior:

- `GRADE_SCHOOL`
  - very simple definitions and identification
  - no tricky distractors
  - no complex computation
- `JUNIOR_HIGH`
  - concept understanding
  - simple problem solving
  - basic computation when supported
- `SENIOR_HIGH`
  - concept understanding plus moderate application
  - simple to moderate computation when supported
- `COLLEGE`
  - deeper concept questions
  - situational and analytical prompts
  - moderate computation when supported
- `BOARD_EXAM_REVIEW`
  - exam-style questions
  - plausible distractors
  - situational and multi-step reasoning
  - computation when the topic is quantitative
- `PROFESSIONAL`
  - applied or case-based framing
  - real-world scenarios
- `PERSONAL_LEARNING`
  - practical and accessible
  - around a college-foundation baseline unless notes suggest otherwise

## Mode-Specific Behavior

### Quick Review

- generated during Study Pack generation
- exactly 5 questions
- optimized for fast recall (~30 to 60 seconds per question)
- focused on definitions, key concepts, and direct understanding
- may include a simple numerical question when the note is clearly quantitative

### Challenge Quiz

- generated separately from Quick Review
- 10 to 15 questions depending on recent performance
- exam-style, situational, and analytical
- must not repeat the Quick Review question set for the same Study Pack
- expected pace: ~1 to 2 minutes per question

### Adaptive Practice

- generated separately from Quick Review
- 5 to 10 questions depending on weak-concept volume
- weak-concept only
- slightly simpler than Challenge Quiz when helpful, but still focused
- expected pace: ~45 to 90 seconds per question

## Quantitative / Computation Guidance

When the note context suggests a quantitative subject such as:

- engineering
- physics
- math
- accounting
- finance
- chemistry
- statistics

the prompt should allow:

- computation questions
- formula-based questions
- word problems
- unit conversions
- multi-step calculations when appropriate

For computation questions:

- keep them multiple choice
- ensure the computed answer matches one choice exactly
- explanation should show short step-by-step solution flow

## Explanation Quality

Explanations should:

- sound like a tutor
- explain why the correct answer is correct
- stay concise but useful
- include short steps for numeric problems

Avoid empty explanations such as:

- `B is correct because it is correct.`
