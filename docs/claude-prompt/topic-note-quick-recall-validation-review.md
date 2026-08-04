# Topic Note Quick Recall Validation Review

Status: Discussion brief — no product or implementation decision has been made  
Logged: 2026-08-03  
Trigger: A real `Create from topic` request failed with `LLM_INVALID_OUTPUT` while generating formula-heavy Civil Engineering material.

## Purpose

Use this document to decide whether topic-note validation still reflects the kinds of notes NoteLib now needs to support, especially engineering, mathematics, accounting, physics, and other formula-heavy subjects.

This is not an implementation prompt. It records the reproduced failure, the current contracts, the likely design issue, and the questions that should be resolved before scoping a fix.

## Reported request

- Course / Program: `Civil Engineering`
- Subject selected in the editor: `Hydraulic Structures`
- Topic: `Weirs`
- User-facing/backend exception:

```text
com.studysnap.backend.exception.AppException: The note generation service returned invalid quick recall content. Please try again.
```

## Confirmed immediate cause

Topic-note generation currently has two different output contracts:

1. The strict OpenAI JSON schema permits each `quickRecall` item to contain up to `240` characters.
2. Post-response backend validation rejects any generated-note list item containing more than `28` whitespace-delimited words.

The prompt asks Quick Recall to include formulas or precise definitions but never tells the model about the `28`-word ceiling.

Relevant implementation:

- `backend/src/main/java/com/studysnap/backend/service/impl/OpenAiLlmStudyPackService.java`
  - `MAX_GENERATED_NOTE_ITEM_WORDS = 28`
  - `buildGeneratedNoteSchema()` applies `maxLength: 240` to generated-note array items
  - `normalizeGeneratedNoteItems()` applies the separate `28`-word limit
  - `retryOnceOnInvalidOutput()` makes at most two complete generation attempts
- `backend/src/main/resources/prompts/study-pack-v1/note-generation-developer.txt`
  - requires `3`–`5` Quick Recall bullets
  - explicitly asks for dates, names, key terms, formulas, or precise definitions
  - specifies `Term — value or definition`
  - does not state a per-item word limit
- `backend/src/main/java/com/studysnap/backend/util/StringNormalizationUtils.java`
  - counts words by splitting on whitespace, so spaced mathematical notation contributes many “words” even when the item remains visually compact

## Controlled reproduction

The current repository prompt, schema, actual request context, and configured free model (`gpt-4.1-mini`) were replayed against OpenAI.

Across five sampled responses:

- four responses passed the backend Quick Recall validation;
- one response was schema-valid but failed backend validation;
- the failure was therefore reproduced as output-dependent rather than deterministic for the topic `Weirs`.

The rejected item was:

```text
Discharge formula — Q = (2/3) * C_d * L * sqrt(2g) * H^(3/2) for sharp-crested weirs, where Q is flow, C_d is discharge coefficient, L is crest length, g is gravity acceleration, H is head over crest
```

Measured against the two contracts:

- characters: `199` — valid under the schema's `240`-character maximum
- whitespace-delimited words: `38` — invalid under the backend's `28`-word maximum
- Quick Recall array size: `5` — valid
- distinct non-blank items: `5` — valid

This item is not malformed content. It is a formula followed by definitions of its variables, which is a reasonable recall unit for an undergraduate Civil Engineering note.

## Why the exception reaches the user only sometimes

`generateNoteFromTopic()` wraps the complete LLM call and validation in `retryOnceOnInvalidOutput()`. A first invalid output triggers one fresh generation request. The generic exception reaches the user only if the second attempt is also invalid.

Concise formula responses pass. Expanded formula-plus-variable-definition responses can fail. The same user input can therefore succeed or fail depending on the model's wording.

The current retry does not tell the model why the first response was rejected. It repeats the same prompt and schema, so it relies on random variation to produce a shorter item.

Consequences:

- valid educational content can be rejected;
- the user waits for two full model calls before seeing an error;
- the retry incurs avoidable generation cost;
- the error message cannot tell support or engineering which item or bound failed.

Topic-note quota is not consumed on this failure. `NoteGenerationService` records usage only after the LLM service returns successfully.

## Validation design issue to revisit

The current `28`-word ceiling is shared by all three generated-note list sections:

- `coreDetails`
- `whyItMatters`
- `quickRecall`

That treats a short prose fact and a formula with symbol definitions as equivalent shapes. Formula-heavy material exposes why they are not always equivalent:

- spacing improves the readability of equations but increases the word count;
- defining variables in the same recall item can be pedagogically useful;
- character length already bounds the rendered size;
- the whole generated note is independently limited to `700` words;
- the strict JSON schema already bounds item counts and item character lengths.

The question is therefore broader than increasing `28` to another number: should Quick Recall use a prose-oriented whitespace word count at all?

## Secondary context finding: Subject is currently ignored

Although the editor contained `Subject: Hydraulic Structures`, the single-note generation request sends only:

- `topic`
- `courseProgram`

`GenerateNoteFromTopicRequest` has no `subject` field, and `NoteGenerationService` constructs `StudyPackGenerationContext` with `subject = null`. The model received `Civil Engineering` and `Weirs`, but not `Hydraulic Structures`.

This did not cause the reproduced length failure: the replay used the actual context sent by the application and still generated correct weir content. It is nevertheless a separate context-contract question because the selected note metadata is not fully represented in the drafting request.

The current feature rule says Course / Program is the authoritative depth/domain signal. Any decision to include Subject should preserve that hierarchy: Course / Program sets academic domain and depth; Subject narrows the note within that domain. Subject must not replace Course / Program or learner-level static-content rules.

## Observability gap

The backend currently logs only the generic validation exception before retrying:

```text
Retrying OpenAI response validation after invalid output on attempt 1: The note generation service returned invalid quick recall content. Please try again.
```

It does not log:

- the failing section;
- item index;
- character count;
- word count;
- minimum or maximum violated;
- number of non-blank/distinct items after sanitization.

Full prompts and raw generated content should remain excluded from logs. Safe structural metadata such as `requestId`, field name, item index, character count, word count, and violation reason would make future failures diagnosable without recording learner content.

This would align with the existing validation-reliability rule in `docs/features/study-pack-generation.md`: log the failing field and reason safely, while never logging full note content, prompts, or raw LLM output.

## Options for Claude to evaluate

These are discussion options, not approved requirements.

### Option A — Remove the per-item word limit and rely on existing structural bounds

Keep the schema's item-count and `240`-character bounds plus the note-wide `700`-word maximum.

Potential upside:

- directly removes the prose-word-count mismatch for formulas;
- keeps deterministic limits on item and note size;
- avoids rejecting schema-valid educational content.

Question:

- Is `240` characters sufficient to protect scanability for all three list sections, or should section-specific character limits be used?

### Option B — Give Quick Recall its own more suitable validation

Keep the `28`-word ceiling for prose-oriented sections but validate Quick Recall separately, using a higher limit or character length only.

Potential upside:

- smallest behavior change;
- acknowledges that formula recall units differ from prose bullets.

Risk:

- retains two competing sources of truth unless the prompt, schema, and backend are deliberately aligned.

### Option C — Keep the ceiling but make it part of the generation contract

Add explicit per-item brevity guidance and a corresponding schema constraint that approximates the intended rendered size.

Potential upside:

- preserves the original compactness goal.

Risks:

- JSON Schema has no direct portable “word count” keyword;
- reducing `maxLength` is only an approximation and can penalize descriptive terminology;
- the model may omit useful variable definitions to satisfy brevity.

### Option D — Repair or selectively regenerate only invalid items

Accept the rest of the note and shorten/regenerate the invalid Quick Recall item instead of repeating the entire note-generation call.

Potential upside:

- preserves valid work from the first response;
- could reduce repeat-generation cost.

Risks:

- adds orchestration and another output-repair path to a currently simple drafting assist;
- may be disproportionate if aligning or removing the redundant limit resolves the issue.

## Questions for Claude

1. What user-facing problem was the `28`-word per-item limit intended to prevent, and is that already covered by the `240`-character item limit plus `700`-word note limit?
2. Should formula-bearing Quick Recall items be validated differently from `coreDetails` and `whyItMatters`?
3. Is the most robust contract character-based, word-based, or section-specific?
4. Should a single overlong but otherwise valid item fail the entire generated note?
5. If validation remains stricter than the JSON schema, how should the retry receive corrective feedback instead of repeating the same request unchanged?
6. What safe structural diagnostics should be logged for invalid generated-note sections?
7. Should Subject be added to the topic-note request as narrowing context, independently of the validation fix?
8. Is this a narrow reliability patch, or should it trigger a complete audit of prompt/schema/post-validation alignment across generated-note fields?

## Recommended review boundary

Keep the first decision focused on topic-note drafting reliability.

In scope for discussion:

- generated-note prompt/schema/post-validation alignment;
- formula-heavy Quick Recall content;
- retry behavior for generated-note validation;
- safe validation diagnostics;
- whether the selected Subject belongs in topic-note generation context.

Out of scope unless evidence broadens it:

- Study Pack quiz validation;
- quiz/exam formula handling;
- Study Pack quota or topic-note quota amounts;
- learner-level rules for static content;
- new persistence, batch-job, or progress infrastructure;
- changes to the generated note's visible section structure.

## Evidence still unavailable

The original production response body was not logged, so its exact rejected bullet cannot be recovered. The surfaced exception proves that both configured attempts failed Quick Recall normalization, but not whether each failed because of an overlong item, blank/duplicate sanitization, or a mix of those conditions.

The controlled replay proves the overlong-formula path can produce the exact exception and is consistent with the reported subject matter. It should be treated as a confirmed defect in the current contract, not as proof that every historical Quick Recall failure had the same item-level cause.

## Decision record placeholder

When the review is complete, record:

- chosen validation contract per generated-note section;
- whether the `28`-word shared limit remains, changes, or is removed;
- whether Subject becomes part of the request/context;
- retry and safe-logging behavior;
- required tests and documentation;
- release scope and explicit exclusions.

Do not convert this brief into an implementation prompt until those decisions are ratified.
