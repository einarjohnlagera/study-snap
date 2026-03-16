# ocr-pipeline.md — NoteLib Feature Context

This file consolidates the OCR-related context from the legacy docs.

## Goal

Support image-based note uploads and convert them into Study Packs.

## OCR provider direction

NoteLib uses Google Cloud Vision OCR for image-based notes.

## Hybrid OCR strategy

Use a hybrid OCR approach to reduce cost and avoid unnecessary OCR calls.

Processing idea:
1. image upload
2. image validation
3. quick text detection
4. if text is detected, run full OCR extraction
5. extract text
6. normalize OCR text
7. send normalized text to the LLM

## Low-confidence fallback

If OCR confidence is low:
- return `status: needs_text_confirmation`
- provide editable extracted text
- allow the user to confirm or correct it
- resubmit corrected text for Study Pack generation

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

- OCR drafts expiry cleanup
- smarter cleanup heuristics
- subject-aware OCR cleanup
