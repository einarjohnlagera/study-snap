This file consolidates quiz-quality and prompt-quality guidance.

## Goal

Practice quizzes should feel like real study reviewers, not generic AI trivia.

## Note-first generation context

- Notes are saved first and remain the primary entity.
- Study Pack output is the generated enhancement state of a Note.
- New versions are created via `Make a Copy`, not overwrite on the same Note.

## Output structure

The LLM should produce:
- title
- summary
- subject
- tags
- keyConcepts
- quiz[]

The strict JSON contract is documented in `docs/ai/PROMPTS.md`.

## Quiz quality principles

Include a balanced mix of:
- recall questions
- understanding questions
- application questions

Detailed expectations:
- generate exactly 5 questions for the current Quick Review-oriented Study Pack flow
- each question must test a different concept from the notes
- recall questions test facts, terms, and definitions
- understanding questions test conceptual understanding (not rote memorization)
- application questions test simple practical use of the concept
- each question must contain 4 answer options with exactly 1 correct answer
- each quiz question includes non-null concept metadata (short topic label for the idea being tested)
- explanations are required for each quiz item and should briefly explain why the correct answer is correct
- all questions must remain answerable from the notes
- if notes are too short or too simple, prioritize recall and understanding
- avoid hallucinating details not present in the notes
- if OCR noise exists, ignore gibberish lines
- if OCR text was edited by the user, the edited text becomes the authoritative input
- distractors should be plausible same-topic alternatives, not obvious throwaway options
- avoid "all of the above", "none of the above", trick questions, and duplicate concepts

## Metadata generation rules

Subject, tags, and quiz concept metadata are generated in the same Study Pack AI request (no extra LLM call).

### Subject
- exactly one subject value per Study Pack
- broad academic category (for example: History, Biology, Chemistry, Physics, Mathematics, Literature, Computer Science, Geography, Economics)
- concise and human-readable
- not sentence-like

### Tags
- generate 3 to 6 tags
- each tag is 1 to 3 words
- tags are reusable filtering/search keywords (not long sentence-like phrases)
- tags must not repeat the Study Pack title
- avoid punctuation-heavy labels

### Quiz concept metadata
- every quiz question includes a non-null `concept`
- concept should be concise and reusable (1 to 4 words)
- concept should represent the key idea tested by the question

## Note import flow

Create/Edit Note supports multiple content input paths before save or generation:

- pasted text in the main `Content` field
- image upload with OCR text extraction
- `.txt` file import
- `.pdf` file import for text-based PDFs
- `.docx` file import

All imported content should populate the main note `content` field for manual review/edit.
Imports must not auto-save and must not auto-generate.
Create/Edit Note should use one unified upload entry point for images and supported files.
Backend extraction is the source of truth for OCR and document text extraction.

## OCR input flow (image notes)

Create/Edit Note supports an OCR-assisted import flow when users upload image notes.

Authoring behavior:
- OCR upload is optional and secondary to the main note form
- extracted text is inserted into note content for manual review/edit
- OCR upload does not auto-save and does not auto-generate
- unverified users are blocked from OCR upload

Frontend OCR UX goals:
- clear upload guidance before OCR starts
- visible image selection confirmation (including preview)
- explicit OCR processing states
- extracted text goes directly into the main `Content` field for final review

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
- OCR billing-period quota reached:
  - `You have reached your OCR limit for now. Please try again later or upgrade to Premium.`
- OCR or AI request rate limit reached:
  - `Too many requests. Please wait a moment and try again.`

Errors should remain supportive and actionable.

## Question counts by plan

- Demo: 3 questions
- Free: 5 questions
- Premium: 10–20 questions

## Free plan usage UX

- Free users keep the existing `5` Study Pack/month product rule.
- When a Free user has `1` Study Pack remaining, show a non-blocking upgrade warning banner on generation-related surfaces.
- When a Free user tries to generate after reaching the monthly limit, show the shared paywall modal instead of redirecting immediately to billing.
- Limit messaging should make it clear that Premium increases Study Pack capacity and unlocks Challenge Quiz + Adaptive Practice.
- Warning banners and generation blocking must use the same backend-resolved effective usage count so remaining credits and enforcement never disagree.
- Study Pack quota only increments after a successful Study Pack is saved.
- Failed generation attempts, note saves, and opening generation surfaces must not consume Study Pack quota.

## Metadata suggestion parity

- Create Note and Note Detail must use the same metadata suggestion behavior after successful generation.
- Generated `title`, `subject`, and `tags` should be suggested from both entry points.
- If the note has no existing metadata, NoteLib may apply the generated metadata automatically.
- If the note already has metadata, users should be able to keep their values or apply the AI suggestions before continuing.

## Validation and retry

Backend should:
1. call the LLM
2. parse JSON
3. validate JSON schema

If validation fails:
- run one repair pass
- if it still fails, return a friendly error

## Imported text review/edit step

When OCR or file import returns extracted text:
- the text is inserted into the main `Content` field
- users review and edit there before save/generate
- the edited `Content` value is treated as the final source of truth for Study Pack generation

PDF import rule:
- support text-based PDFs only
- if no embedded text is extractable, try OCR fallback for scanned/image-based PDFs
- if OCR fallback also cannot read the PDF, show:
  - `This PDF appears to be scanned or image-based. Please upload images for OCR instead.`
- scanned-PDF OCR fallback follows the same verification/OCR gating rules as image OCR

Import protection rules:
- allowed import types remain `png`, `jpg`, `jpeg`, `webp`, `txt`, `pdf`, `docx`
- file-size limits are backend-configured and enforced before extraction
- extracted text length is backend-configured and must fail with:
  - `This file is too large to process. Please upload a smaller file.`
- Study Pack generation, Challenge Quiz generation, Adaptive Practice generation, and OCR import are all protected by per-minute rate limiting on the backend

Authoritative input rule:
- if the user edits extracted OCR text, generation must use the edited text (not the original raw OCR output)

## Future improvements

- better topic-aware distractors
- stronger premium quiz experiences
- mock exam mode

## Adaptive follow-up generation

Study Pack generation produces the original baseline quiz that remains stable for the pack.

Adaptive quiz generation is a separate follow-up flow:

- triggered from Quick Review results when weak concepts are detected
- generated from weak concepts in the latest completed Quick Review session
- returns a new short practice set (3-5 questions) with concept labels and explanations
- does not modify the original baseline Study Pack quiz
