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
- ADR-001's authoring-domain fallback chain is `notes.domainContext` -> **exactly one joined catalog program** (`note_course_program`) -> `notes.courseProgram` -> `users.courseProgram`. For canonical curated generation, the final profile fallback is suppressed; a curator note with no other authoring domain legally omits the Domain line. Learner-owned material keeps profile personalization. **The joined step is single-valued by design:** a program *list* never reaches a prompt. A saved note with more than one program and no Domain Context is rejected by `StudyPackGenerationContextResolver.assertGenerationReady` before status or quota side effects. Do not reconstruct either rule in another service.
- **Whether a note is quantitative is a declared property of the Domain Context, not a guess about its name.** Each `DomainContext` value carries a `quantitative` flag beside its label, and `isQuantitativeContext` short-circuits to `true` when the effective domain declares it. A domain declaring `false` — `GENERAL_EDUCATION`, `PROFESSIONAL_EDUCATION`, `PROFESSIONAL_PRACTICE_AND_REGULATION` — falls through to the pre-existing keyword scan over the effective authoring domain, subject, tags, concept hints and summary, exactly as an unclassified note does. **The declared check is additive by construction: it can only turn the signal on, never off.** Prefer declaring it on the enum over lengthening `QUANTITATIVE_KEYWORDS`; the keyword list is the fallback for unclassified notes, not the definition. The flag is compile-time and never persisted, and it does not change the prompt payload — `effectiveAuthoringDomain` still sends the display label — so past generations stay reproducible.
- ADR-001's curriculum-level fallback chain is note-level `notes.learnerLevel` -> the reader's profile-level `users.learnerLevel` -> `COLLEGE`.
- Static content — note drafts, summaries, key concepts, flashcards, memorization, metadata suggestions, embedded Quick Review/static question pools — is calibrated by the effective Domain Context plus the note's learner level. The reader's learner level must not lower or redirect static content.
- Quizzes and exams use the effective Domain Context plus note learner level as the curriculum floor. When the reader's level is lower than an explicit note level, prompts may soften wording and add scaffolding, but must keep curriculum, terminology, and difficulty at the note's level. A higher reader level never raises the note's authored difficulty.
- **Subject-suggestion guidance is static content and must never consult the reader's level.** It reads the note's authored `learnerLevel` directly. With no authored level it emits **both** the school-level and the field-of-study subject lists rather than silently picking one, so two users generating from byte-identical notes always receive identical guidance. The K-12 strand guard ("if the domain is a strand or track, derive the subject from note content") is gated on the effective authoring **domain**, never on a level — a legacy `Senior High – STEM` note must keep it regardless of who reads it.
- When neither an authoring domain nor an authored note level resolves, static calibration says so explicitly and tells the model to infer from note content. It must not reference a "Curriculum level above" that was never emitted.
- Applicable Programs never reach a generation prompt. All note/user fallback resolution stays in `StudyPackGenerationContextResolver`; generation services must not reconstruct either chain.
- Persisted quiz reuse uses that same curriculum resolution: single-note Long Exam and Board Exam pools plus the per-user Challenge bank are stamped and read by the effective curriculum level, never directly by `context.learnerLevel()`. Notes without an authored level still fall back to the reader and then `COLLEGE`; a reader-level change cannot invalidate content for a note with an authored level. Existing mismatched pool rows take the established lazy refresh path, while bank reads remain best-effort. Domain Context is not stored on either reuse table in this release.

## Output structure

The LLM should produce:
- title — **names the knowledge the material teaches, not the curriculum container it sits in.** The
  prompt's `Title` rule is deliberately **semantic**, not a wording ban: disciplinary language belongs in
  a title when it is part of the knowledge (*"Nursing Management of Acute Asthma"*, *"Structural
  Applications of Differential Equations"* are both **correct**) and does not when it only names who the
  material is for (*"Time Value of Money in Accountancy"*). **⚠️ Never compress this into "'in X' is
  bad"** — that rule would break the two correct titles above. It is universal: there is **no
  per-program or per-discipline title logic**. **⚠️ The rule is unconditional and therefore also changes
  the AI's default suggestion on learner-facing generation paths — that is intended**, and it governs
  what the AI *proposes*, never what a learner may name their own note. **⚠️ As of `v0.97.0` the SAME rule
  lives in BOTH title-emitting prompts** — `developer.txt` and `note-generation-developer.txt`, which also
  declares a `title` in its schema. **It must stay one rule, not two formulations:** two prompts stating
  one semantic idea in different words is exactly how it degrades into the banned *"'in X' is bad"* form,
  and `bothTitleEmittingPromptsTeachTitleSemanticsRatherThanAWordingBan` asserts
  the rule's phrases are **present in each file**. **⚠️ It pins PRESENCE PER FILE, not EQUALITY BETWEEN
  FILES** — it kills a reversion or a wording ban, because those remove asserted phrases, but **additive**
  divergence (appending a contradictory bullet to one prompt only) would pass. The two blocks are
  byte-identical today, verified by hand at the `v0.97.0` pressure test; nothing in the build enforces it.
  Note generation additionally keeps a **topic-fidelity** bullet
  (*"anchored to the topic"*), pinned by its own test — a **different** idea from knowledge-versus-container,
  kept separate so tightening one cannot silently drop the other.
- summary (plain prose + optional markdown comparison table + optional Common Misconceptions paragraph)
- subject
- tags
- keyConcepts
- quiz[]

The strict JSON contract is documented in `docs/ai/PROMPTS.md`.

**⚠️ LaTeX commands survive JSON parsing only because the prompt requires escaped backslashes, and a
repair backs it up.** A model writing `\times` with ONE backslash produces a **valid JSON escape** —
Jackson reads `\t` as a TAB, whitespace normalisation collapses it, and the command is destroyed into
`imes` before anything validates it. **This is content corruption, not a validation failure:** the
mangled text is *shorter*, so it passes every length and word-count check and is saved. The prompt rule
is the primary fix; `repairJsonEatenLatexCommands` runs **before** whitespace normalisation, which is
the only moment the control character still exists. **⚠️ `\n` and `\r` are deliberately not repaired**
— a newline is legitimate content and `"sentence\nWord"` cannot be told apart from a mangled `\nu`, so
commands beginning with `n` or `r` stay broken rather than risk destroying real line breaks.

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

Generated-note item validation (v0.69.0; **the prose/notation split was removed in v0.86.0**):

- **every** generated-note array item — `coreDetails`, `whyItMatters` and `quickRecall` alike — is bounded by **characters**: the same 240-character limit the JSON schema applies as `maxLength`, and the same one the prompt states via `{MAX_ITEM_CHARS}`
- a whitespace word count measures the wrong thing on notation: `Q = (2/3) * C_d * L * sqrt(2g) * H^(3/2)` is roughly 15 "words" of pure symbols, so a formula followed by its variable definitions is word-dense and character-light — visually compact, well inside the schema's bound, and over any prose ceiling
- **v0.69.0 applied that reasoning to `quickRecall` only**, leaving `coreDetails` and `whyItMatters` on a 28-word prose ceiling on the theory that formulas belong in Quick Recall. **v0.86.0 removed that split**: for a quantitative topic the *mechanism* is the formula, so the model files it under `coreDetails` — which is what that section asks for — and it was then judged by a ceiling built for prose. Four of five sampled Engineering Economics topics failed, one on a bullet measuring exactly 240 characters. Background: `docs/claude-plans/v0.86.0-note-item-limit-mismatch.md`, and the original Quick Recall review at `docs/claude-prompt/topic-note-quick-recall-validation-review.md`
- **the bound is stated in every section it governs.** *A bound the model is not told about is enforced by chance* — that produced the original four-of-five pass rate in v0.69.0 and reproduced it in v0.86.0 on the neighbouring fields. **Do not add a validation bound without publishing it in the prompt, and do not reintroduce a word ceiling beside the character one**: two limits for one contract is the defect, independent of their sizes
- `coreDetails` additionally instructs the model to state the formula when the mechanism is the formula, splitting across bullets rather than truncating
- the whole generated note remains bounded at 700 words independently

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

### Age-based generation recovery

The scheduled generation-recovery job covers three independently processed surfaces and moves stale work only into a status the existing product can recover from:

- exam pools stamp nullable `generation_status_at` on every `PENDING` and `GENERATING` write. Separate default bounds are `60` minutes for queued `PENDING` work and `60` minutes for `GENERATING` fan-out work. A stale pool becomes `FAILED`; `sampleQuestions` owns the existing next-use refresh.
- Long Exam sessions use immutable `created_at`, with a default `30`-minute bound. Only `session_mode = LONG_EXAM` is eligible; a stale session becomes `FAILED`, allowing a later start to create a fresh session.
- notes stamp nullable `generation_enqueued_at` in the same transaction that sets `GENERATING`, refreshing it on every retry. The default `120`-minute bound covers both queue wait and the single LLM call. A stale note becomes `FAILED` through the same entity transition used by generation errors and exposes the existing Retry Generation action. This protection is prospective: production sizing found zero stuck notes.

The job runs every ten minutes by default, processes at most `200` candidates per surface per run, reports recovered count and oldest age, and has a deploy kill switch. Every bound, the cron and batch size are configuration-owned placeholders; they can be tightened after production observation without a code change. `V118` seeds existing non-terminal pool attempts with deploy time rather than reused-row `created_at`, so no live attempt is swept early and genuinely stuck rows become eligible one full bound after deploy. Notes with a null enqueue clock are left untouched and warned. `V118` seeds the clock for any note already `GENERATING` at deploy time — on the same argument as pools, because the deploy that installs the sweeper is itself the event that strands in-flight generation — and `StudyPackService` is the single writer of `GENERATING` and stamps in the same transaction. So a null clock after that means a **new writer** appeared without a stamp, and silently recovering it would hide that bug rather than surface it.

Recovery is status-only and idempotent. It never auto-regenerates, re-dispatches a task, calls pool refresh directly, refunds or increments quota, changes executor shutdown, or runs as a startup sweep. Age thresholds and locked status rechecks provide multi-instance safety on all three surfaces. **Live-task safety is not uniform:** the pool and note surfaces take a pessimistic lock and recheck under it, while the Long Exam session surface gets its safety from the age threshold sitting far above the worker envelope plus the one-active-generation index — its own generation path reads with a plain `findById` and takes no row lock until commit. A late note worker discards its generated result when the note is no longer `GENERATING`; a late pool worker may write `READY`, which is already a correct terminal outcome.

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
