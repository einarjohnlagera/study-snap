# Bulk Generation

## Goal

Bulk Generation lets an admin paste a subject-grouped list of note titles once and queue one generated note plus one Study Pack per title. The flow is designed for unattended content seeding while keeping every result inside the normal Library model.

## Access

- The Library Create menu shows `Bulk generate` only to users with the `ADMIN` role.
- The page route is `/library/bulk-generate` and uses the shared admin route guard.
- `POST /notes/bulk-generate` is protected with `@PreAuthorize("hasRole('ADMIN')")`.
- The role gate is removable. The orchestration service already distinguishes the admin bypass from the existing quota-enforcing non-admin path, so opening the feature later does not require a separate pipeline.

## Paste Format

Each subject begins with a `Subject:` line. Every following non-empty line is treated as a title until the next subject heading.

```text
Subject: Maternal Health
Prenatal Care
Stages of Labor

Subject: Community Health
Epidemiology Basics
```

Parsing trims whitespace, skips blank lines, accepts `Subject:` case-insensitively, and ignores non-empty lines before the first valid subject heading. The page shows ignored-line guidance, a parsed `Subject -> titles` preview, and the total title count before submission. A batch is limited to 50 submitted titles.

## Batch Fields

- `Course / Program` applies to every note in the batch.
- `Target Audience` supplies the learner level used for generation. Supported values are Grade School, Junior High, Senior High, College, Board Exam Review, and Professional.
- `Public` makes each created note public when enabled.

## Per-Title Flow

The endpoint validates the request, queues one throttled background batch on the existing `studyPackGenerationTaskExecutor`, and returns an immediate acknowledgment. The worker processes each title independently:

1. Validate the title through content moderation.
2. Resolve the batch learner level, course/program, and subject through `StudyPackGenerationContextResolver`.
3. Generate note content with the existing LLM note-from-topic operation.
4. Create the note through `NoteService.create` with the pasted title, batch course/program, group subject, and generated content.
5. Apply PUBLIC visibility when requested.
6. Start the existing async Study Pack generation pipeline.

One title failure is caught and logged without aborting later titles. There is no persisted batch record. Notes appear as real Library rows after content generation and independently resolve through the existing `GENERATING -> STUDY_PACK_READY` or `FAILED` states.

## Metadata Rule

The subject from the pasted list wins. Bulk Study Pack completion applies the AI-refined title and AI tags to the note, then re-applies the admin subject so the AI subject cannot replace it. This completion behavior is bulk-only and does not change normal single-note metadata suggestions.

## Quota Model

- ADMIN bulk generation bypasses note-generation quota, Study Pack quota, per-user AI rate limits, and their usage counters.
- The bypass is local to the bulk orchestration and its Study Pack call. Shared quota services do not contain an ADMIN exemption.
- The existing single-note note-generation and Study Pack entry points still enforce quota and rate limits and still record successful usage.
- A future non-admin gate flip routes content generation through `NoteGenerationService.generateFromTopic` and starts Study Pack generation with `enforceLimits=true`.
- Throttled sequential fan-out protects the LLM provider while Study Pack workers continue on the existing executor.

## Out Of Scope

- persisted batch/job entities, progress tables, or new status enums
- a batch progress/status page
- partial-execution messaging when a future non-admin runs out of quota mid-batch
- collection-level bulk quiz generation
- async quiz generation
- teacher quiz preview changes
- opening the frontend flow to non-admin users

No new analytics event was added for v0.29.0. Existing note and Study Pack generation analytics continue to run where their shared services already emit them.
