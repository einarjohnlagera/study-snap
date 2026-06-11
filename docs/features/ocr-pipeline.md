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
- If a PDF has no embedded text, the import flow may fall back to OCR on rendered PDF pages.

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
If a PDF has no embedded text, the extraction pipeline should try OCR fallback before declaring the PDF unreadable.

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
- enforce backend OCR usage limits per billing period by plan
- enforce backend OCR rate limits with `429 Too Many Requests`
- use the friendly OCR quota message:
  - `You have reached your OCR limit for now. Please try again later or upgrade to Plus or Pro.`
- use the friendly rate-limit message:
  - `Too many requests. Please wait a moment and try again.`

## Import limits

Unified note import must keep backend-configured limits for:
- total upload size
- text/PDF/DOCX file size
- maximum PDF pages
- maximum extracted text length

When extracted text exceeds the configured maximum, return:
- `This file is too large to process. Please upload a smaller file.`

## Bulk import

`POST /notes/import-batch` is the batch wrapper around the same per-file extraction pipeline used by `POST /notes/extract-text`.

This endpoint deliberately relaxes the single-file editor rule that import must not auto-save. The batch path creates one `DRAFT` note per successfully extracted file directly, because requiring a review-before-save step for each file would defeat the purpose of importing a unit's worth of material in one action.

Locked behavior:

- Bulk import is profile-agnostic and available to authenticated `USER` / `ADMIN` callers.
- The caller must be email-verified before any file work starts.
- Each file is processed independently in request order.
- Each successful file creates exactly one owned `DRAFT` note with filename-derived title and extracted text content.
- Blank or unreadable extraction results are recorded as per-file failures and do not create notes.
- A failure for one file must not roll back notes already created for earlier files.
- Per-file OCR/import limits, page limits, text-length limits, OCR usage limits, and OCR rate limits are reused unchanged.
- There is no aggregate bulk-import quota category.
- Bulk import never auto-generates, never sets `GENERATING`, never calls an LLM, and never creates a Study Pack.

The single-file editor path remains extract-for-review only: `POST /notes/extract-text` returns text for insertion into the editor and must not auto-save.

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
