This file consolidates quiz-quality and prompt-quality guidance.

## Goal

Practice quizzes should feel like real study reviewers, not generic AI trivia.

## Note-first generation context

- Notes are saved first and remain the primary entity.
- Study Pack output is the generated enhancement state of a Note.
- New versions are created via `Make a Copy`, not overwrite on the same Note.
- Backend generation context may also carry:
  - the reader's profile-level `learnerLevel`
  - legacy/fallback `courseProgram`
  - note `subject`
  - note `tags`
  - note `domainContext`
  - note-level `learnerLevel`
- ADR-001's authoring-domain fallback chain is `notes.domainContext` -> `notes.courseProgram` -> `users.courseProgram`. If nothing resolves, generation omits the Domain line instead of inventing a placeholder.
- ADR-001's curriculum-level fallback chain is note-level `notes.learnerLevel` -> the reader's profile-level `users.learnerLevel` -> `COLLEGE`.
- Static content — note drafts, summaries, key concepts, flashcards, memorization, metadata suggestions, embedded Quick Review/static question pools — is calibrated by the effective Domain Context plus the note's learner level. The reader's learner level must not lower or redirect static content.
- Quizzes and exams use the effective Domain Context plus note learner level as the curriculum floor. When the reader's level is lower than an explicit note level, prompts may soften wording and add scaffolding, but must keep curriculum, terminology, and difficulty at the note's level. A higher reader level never raises the note's authored difficulty.
- Applicable Programs never reach a generation prompt. All note/user fallback resolution stays in `StudyPackGenerationContextResolver`; generation services must not reconstruct either chain.

## Output structure

The LLM should produce:
- title
- summary (plain prose + optional markdown comparison table + optional Common Misconceptions paragraph)
- subject
- tags
- keyConcepts
- quiz[]

The strict JSON contract is documented in `docs/ai/PROMPTS.md`.

## Exam-only keyConcept tags

Long Exam and Interview Practice questions include an additional nullable `keyConcept` field on `QuizItem`. This field is separate from `concept`:

- `concept` remains the report-facing label. Long Exam uses it for domain breakdowns, and Interview Practice uses it for senior-framed strengths, gaps, and talking points.
- `keyConcept` is for Progress recording only.
- For Long Exam and Interview generation, the strict quiz schema adds `keyConcept` as a required string enum when the source Study Pack has non-empty `keyConcepts`; the enum values are copied verbatim from that source list.
- Non-exam quiz schemas do not include `keyConcept`.
- If a source pack has no key concepts, the schema omits `keyConcept` and completion recording falls back to the legacy `concept` behavior.
- Legacy persisted sessions and pre-warmed pool questions may have `keyConcept == null`; they remain valid and require no migration.

## Enhanced summary format

The summary field supports limited markdown to enrich the generated content:

### Comparison table
When the notes contain multiple related concepts that can be meaningfully compared (e.g. different laws, methods, categories), the AI should include a compact GFM pipe table (2–4 rows, 2–3 columns). Omitted when not applicable.

### Common Misconceptions
When the notes contain concepts that are commonly misunderstood or confused, the AI appends a short **Common Misconceptions** paragraph listing 1–3 brief points. The heading is bolded using `**Common Misconceptions**`. Omitted when no clear misconceptions apply.

### Constraints
- Max 200 words (up from 120)
- Use `**bold**` only for the "Common Misconceptions" heading and pipe syntax for tables; no markdown headings (`#`) in the summary body
- Plain prose remains the primary format; structured sections are additive
- Frontend renders summary via `<SummaryMarkdown>` (wraps `react-markdown` + `remark-gfm`) across all study pack surfaces
- Preview-only contexts (e.g. public library listing card) use `stripMarkdownForPreview()` to show plain prose only

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
- should be a specific academic subject or curriculum category — label only, no topic suffix
- correct examples: `Biology`, `Physics`, `Mathematics`, `Computer Science`, `English`, `History`, `Civil Engineering`, `Electrical Engineering`, `Anatomy`, `Nursing`, `Accountancy`, `Constitutional Law`
- incorrect: `Biology – Cell Division`, `Physics: Ohm's Law`, `Mathematics – Derivatives`
- overly broad suggestions such as `Business`, `Medicine`, `Engineering`, and `Law` are rejected as AI metadata and ignored safely
- topic-level specificity belongs in tags and key concepts, not in subject
- concise and human-readable, not sentence-like, at most 4 words
- backend enforcement: any combined domain-topic value (`Biology – Cell Division`) is stripped to the domain part (`Biology`) before saving
- if stripping produces an empty/unusable result, the subject suggestion is ignored; Study Pack generation continues when core summary/key concept/quiz output is valid
- **note subject wins:** when saving a Study Pack, `StudyPackService` prefers `note.subject` over the LLM-suggested subject if the note already has one. Falls back to the LLM value only when `note.subject` is null or blank. This prevents LLM subject drift from corrupting readiness grouping on curated plans where the curator has already set authoritative subject values.
- **readiness grouping uses note subject:** `ProgressReportService.resolveSubject()` resolves the grouping key from the note's own `subject` field (batch-fetched, not N+1), falling back to `studyPack.subject` only when the note has none. This heals historical subject drift on all existing plans without requiring per-plan data patches.

### Tags
- generate 3 to 6 tags
- each tag is 1 to 3 words
- tags are reusable filtering/search keywords (not long sentence-like phrases)
- tags must not repeat the Study Pack title
- avoid punctuation-heavy labels

### Quiz concept metadata
- every quiz question includes a non-null `concept`
- concept must be 1 to 3 words — a short topic label, never a sentence
- examples: `Ohm's Law`, `Electrical Power`, `Resistance`, `ATP Production`, `Glycolysis`
- if the LLM returns a concept exceeding 4 words, the backend attempts automatic repair (strip filler prefix, truncate) before failing

### Validation reliability
- subject and quiz concept validation now logs the failing field value with requestId, field name, and reason for safe debugging
- full note content, full prompt, and full raw LLM output are never logged
- sanitization/repair runs on subject and concept before final handling
- AI metadata suggestions are non-blocking: broad or invalid subject suggestions and optional tag metadata issues must not fail or roll back Study Pack generation
- technical notes (Ohm's Law, electrical engineering, math formulas) should not fail due to harmless LLM metadata drift

## Note import flow

## Create a note from topic

Create Note now includes a lightweight note-generation assist before save.

Rules:

- users can choose `Write your own note` or `Create from topic`
- topic input placeholder: `Create a note about Newton's Laws of Motion...`
- CTA: `Create a Note`
- backend endpoint: `POST /api/notes/generate`
- response fills the editor `Content` field
- generated note content remains editable before save
- this is a note-drafting assist, not a saved note or Study Pack

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

## Create vs Edit mode

Note Editor must keep create and edit behavior distinct.

- Create mode (`/notes/new`)
  - title: `New Note`
  - actions: `Save`, `Generate Study Pack`
- Edit mode for Draft notes (`/notes/{id}/edit`)
  - title: `Edit Note`
  - actions: `Save Changes`, `Cancel`, `Generate Study Pack`
- Edit mode for Study Pack Ready notes (`/notes/{id}/edit`)
  - title: `Edit Note`
  - metadata remains editable: `title`, `courseProgram`, `subject`, `tags`
  - note `content` is read-only
  - helper text: `Note content cannot be edited after generating a Study Pack. You can still update the title, course/program, subject, and tags.`
  - actions: `Save Changes`, `Cancel`, `Make a Copy`

These route-mode labels and actions should not fall back to create-note copy when an existing note is being edited.

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
  - `You have reached your OCR limit for now. Please try again later or upgrade to Plus or Pro.`
- OCR or AI request rate limit reached:
  - `Too many requests. Please wait a moment and try again.`

Errors should remain supportive and actionable.

## Question counts by plan

- Demo: 3 questions
- Free: 5 questions
- Paid plans: larger configured quiz ranges based on plan and mode

## Study Pack usage UX

- Free users keep the existing `10` Study Packs/month product rule.
- Plus users keep the existing `50` Study Packs/month product rule.
- Pro users keep the existing `100` Study Packs/month product rule.
- When a user has `2` or `1` Study Packs remaining, show a non-blocking monthly-limit banner on generation-related surfaces.
- Free near-limit banner copy should say:
  - `You have {X} Study Packs left this month on the Free plan.`
- Plus near-limit banner copy should say:
  - `You have {X} Study Packs left this month on Plus.`
- Pro near-limit banner copy should say:
  - `You have {X} Study Packs left this month.`
- When a Free user reaches `0` remaining Study Packs, keep `Generate Study Pack` enabled and open the shared upgrade modal instead of the monthly-limit modal.
- When a paid user reaches `0` remaining Study Packs, keep `Generate Study Pack` enabled and open the shared limit modal with:
  - title: `Monthly Limit Reached`
  - reset-date messaging
  - actions: `Upgrade Plan`, `Get More Study Packs`, `Maybe Later`
- Limit messaging should make it clear that Plus and Pro increase Study Pack capacity and that Pro unlocks Adaptive Practice plus Board Exam Mode.
- Warning banners and generation blocking must use the same backend-resolved effective usage count so remaining counts and enforcement never disagree.
- Study Pack quota only increments after a successful Study Pack is saved.
- Failed generation attempts, note saves, opening generation surfaces, and failed retries must not consume Study Pack quota.

## Async note generation flow

For note-owned Study Pack generation:

1. Note Editor saves the note first.
2. `POST /notes/{id}/generate` marks the note as `GENERATING` and starts background Study Pack generation.
3. The frontend redirects immediately to Note Detail.
4. Note Detail observes generation state with light polling.
5. Successful generation persists the Study Pack, increments usage, and maps the note to `STUDY_PACK_READY`.
6. Failed generation maps the note to `FAILED`, keeps the note content safe, does not increment usage, and exposes `Retry Generation`.

User-facing generation statuses:

- `DRAFT`: no Study Pack has been generated yet.
- `GENERATING`: generation is running in the background.
- `STUDY_PACK_READY`: generated summary, key concepts, and quiz are available.
- `FAILED`: generation did not complete and can be retried from Note Detail.

## Metadata suggestion parity

- Create Note and Note Detail must use the same metadata suggestion behavior after successful generation.
- Generated `title`, `subject`, and `tags` should be suggested from both entry points.
- Generated metadata must not silently overwrite user-entered `title` or `subject`.
- In the normal note flow, generated metadata must remain transient until the user explicitly clicks `Apply Changes`.
- Note Editor generation is asynchronous, so Create Note hands off to Note Detail and the suggestion modal opens there once generation reaches `STUDY_PACK_READY`.
- The shared AI suggestion modal should support:
  - `Title` -> `Keep My Title` or `Use AI Title`
  - `Subject` -> `Keep My Subject` or `Use AI Subject`
  - `Tags` -> `Keep My Tags`, `Merge Tags`, or `Use AI Tags`
- Default selection should stay conservative:
  - existing `title` -> default `Keep My Title`
  - existing `subject` -> default `Keep My Subject`
  - existing `tags` with new AI tags -> default `Merge Tags`
  - existing `tags` with no new AI tags -> default `Keep My Tags`
  - no existing `tags` -> default `Use AI Tags`
- Tag comparison must be case-insensitive and whitespace-trimmed when deciding whether an AI tag is actually new.
- AI tag suggestions should show only new tags as suggestions and should mark overlapping tags as already present on the note instead of presenting them as fresh additions.
- AI subject output must be a reusable academic subject label, not a specific topic. Topic specificity belongs in tags.
- Subject should avoid broad umbrella labels like `Engineering`, `Medicine`, `Business`, and `Law`; use a clearer subject such as `Electrical Engineering`, `Clinical Chemistry`, `Accountancy`, or `Criminal Law` when the note supports it.
- Broad or invalid AI subject suggestions are ignored rather than saved or allowed to fail generation. Combined domain-topic values are normalized back to the domain before save.
- Generation context should use course/program, current subject, and tags to refine the suggested subject rather than treating the note as context-free. Learner level must not influence static Study Pack content or metadata suggestions.
- Onboarding is the exception: it may opt into backend auto-apply for empty metadata fields so the guided flow stays zero-friction.

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
