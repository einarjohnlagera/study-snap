# Bulk Generation

## Goal

Bulk Generation lets an admin enter one subject and a list of topics, then queue one generated note plus one Study Pack per topic. Each topic is a generation seed, matching the Note Create `Generate from topic` flow. The AI generates the note content and later refines the note title and tags.

## Access

- The Library Create menu shows `Bulk generate` only to users with the `ADMIN` role.
- The page route is `/library/bulk-generate` and uses the shared admin route guard.
- `POST /notes/bulk-generate` is protected with `@PreAuthorize("hasRole('ADMIN')")`.
- The role gate is removable. Teacher and non-teacher resolution already exists behind the gate so opening the feature later does not require another generation pipeline.

## Input Model

Every batch contains:

- one required `Subject`
- one or more `Topics`, capped at 50
- `Course / Program` for Teacher and Admin profiles
- `Target Audience` for Teacher and Admin profiles
- `Learner Level` for Admin profiles
- a `Public` toggle

Topics are discrete rows with `+ Add topic` and per-row removal. They are not note titles. A topic such as `Newton's Laws of Motion` seeds note-content generation; the Study Pack write-back supplies the AI-refined title and tags. The Topics helper states this expectation inline (title and tags are auto-generated; the subject and other batch details apply to every note) so users are not surprised by AI-named notes in their Library.

## Compact Layout

Subject is full-width. The remaining visible metadata uses one responsive two-column grid in this order:

1. Course / Program
2. Learner Level
3. Target Audience

For Admin, this produces `Course / Program · Learner Level` followed by `Target Audience`. Teacher and non-teacher views use the same grid and omit fields that are not relevant; the grid collapses (`empty:hidden`) when no fields are visible (non-teacher), so Subject sits directly above Public. `Public` is a full-width row below the grid with its label and toggle adjacent (not stretched across the card). The Topics list remains full-width below Public.

## Submission

On a successful queue the page does not show an in-page acknowledgment. It stores a one-shot queued-count flash in `sessionStorage` and redirects to `/library`, where a toast — `Queued N notes — they'll appear here as they finish generating.` — confirms the batch was received. The flash is consumed once on Library mount and does not reappear on refresh. A `sessionStorage` flash is used instead of a query param because the Library rewrites its own URL from filter state, which would strip the param.

## Profile-Aware Resolution

The server loads the caller and treats profile data as authoritative for hidden fields:

| Field | Non-teacher | Teacher | Admin |
| --- | --- | --- | --- |
| Subject | Request | Request | Request |
| Course / Program | Profile | Request | Request |
| Target Audience | Derived from profile type | Request | Request |
| Learner Level | Profile | Profile | Request |

Profile type maps to note target profile as follows: `BOARD_EXAM -> BOARD_TAKER`, `PROFESSIONAL -> PROFESSIONAL`, and all other non-teacher profiles -> `STUDENT`. Client-sent overrides for hidden fields are ignored.

Learner Level is generation context, not quiz-only metadata. It influences both note-content generation and Study Pack generation through `StudyPackGenerationContext`.

The endpoint remains ADMIN-only in v0.29.0. Teacher and non-teacher branches are dormant until the role gate is intentionally relaxed.

## Per-Topic Flow

The endpoint validates the request, queues one throttled background batch on the existing `studyPackGenerationTaskExecutor`, and returns an immediate acknowledgment. The worker processes each topic independently:

1. Validate the topic through content moderation.
2. Resolve learner level, course/program, and subject through `StudyPackGenerationContextResolver`.
3. Generate note content with the existing LLM note-from-topic operation.
4. Create the note through `NoteService.create` with the topic as its initial title, resolved metadata, and generated content.
5. Apply PUBLIC visibility when requested.
6. Start the existing async Study Pack generation pipeline.

One topic failure is caught and logged without aborting later topics. There is no persisted batch record. Notes appear as real Library rows and independently resolve through the existing `GENERATING -> STUDY_PACK_READY` or `FAILED` states.

## Metadata Rule

The batch subject wins. Bulk Study Pack completion applies the AI-refined title and AI tags to the note, then re-applies the batch subject so the AI subject cannot replace it. This completion behavior is bulk-only and does not change normal single-note metadata suggestions.

## Quota Model

- ADMIN bulk generation bypasses note-generation quota, Study Pack quota, per-user AI rate limits, and their usage counters.
- The bypass is local to bulk orchestration and its Study Pack call. Shared quota services do not contain an ADMIN exemption.
- Existing single-note generation entry points still enforce quota and rate limits and record successful usage.
- A future non-admin gate flip uses the existing enforced paths.
- Throttled sequential fan-out protects the LLM provider while Study Pack workers continue on the existing executor.

## Out Of Scope

- persisted batch/job entities, progress tables, or new status enums
- a batch progress/status page
- partial-execution messaging when a future non-admin runs out of quota mid-batch
- production regeneration or backfill of existing Study Packs
- collection-level bulk quiz generation
- async quiz generation
- opening the frontend flow to non-admin users

No new analytics event was added for v0.29.0.
