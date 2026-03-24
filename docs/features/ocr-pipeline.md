# ocr-pipeline.md — NoteLib Feature Context

This file consolidates the OCR-related context from the legacy docs.

## Goal

Support image-based note uploads as part of Note authoring and extract text into notes before Study Pack generation.

Current product placement:

- OCR upload is optional in Create Note / Edit Note.
- OCR fills Note content for user review/edit before save/generate.
- OCR must not auto-save and must not auto-generate.
- Unverified users are blocked from OCR upload.
- OCR in the note editor uses the shared note import pipeline, not direct Study Pack generation.

## OCR provider direction

NoteLib uses Google Cloud Vision OCR for image-based notes.

## Extraction flow

Create/Edit Note should use a backend extraction endpoint that:
1. accepts the uploaded file
2. detects whether it is an image or supported document
3. runs OCR for images only
4. normalizes extracted text
5. returns extracted text for insertion into Note `content`

The note editor must not generate a Study Pack during import.

## Low-confidence fallback

If OCR confidence is low:
- return OCR confidence metadata with the extracted text
- note editor should insert the extracted text directly into the main Note `content` field
- show an inline warning near `Content`:
  - `OCR may be inaccurate. Please review and edit the extracted text before saving or generating a Study Pack.`
- do not show a second OCR-specific review textarea in Create/Edit Note
- note editor OCR review must happen in the main `Content` field only

## Image guardrails

- max image size
- supported formats (`jpg`, `png`, `webp` where supported)
- reject images without readable text

## OCR text normalization

OCR text often contains:
- broken line breaks
- irregular spacing
- hyphenated words
- empty lines

Normalization should include:
- trim whitespace
- collapse multiple spaces
- replace single line breaks with spaces where appropriate
- preserve paragraph breaks
- remove OCR artifacts where possible

## Privacy

- uploaded images are deleted after OCR processing
- avoid logging raw images or full extracted text

## Deferred ideas

- smarter cleanup heuristics
- subject-aware OCR cleanup
