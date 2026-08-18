# Bulk Generation

## Goal

Bulk Generation lets an authenticated user enter one subject and a list of topics, then queue one generated note plus one Study Pack per topic. Each topic is a generation seed, matching the Note Create `Create from topic` flow. The AI generates the note content and later refines the note title and tags.

## Access

- The Library Create menu shows `Bulk generate` to authenticated, onboarded users.
- The page route is `/library/bulk-generate` and uses the shared authenticated/onboarded route guard.
- `POST /notes/bulk-generate` and `GET /notes/bulk-generate/results/{id}` are protected with `@PreAuthorize("hasAnyRole('USER','ADMIN')")`.
- ADMIN keeps the existing quota bypass. Non-admin users run through the existing quota-enforcing note-generation path.

## Input Model

Every batch contains:

- one required `Subject`
- one or more `Topics`, capped at 50
- `Course / Program(s)` for every profile: learners enter one free-text personal program, while Teacher and Admin profiles choose one-or-many catalog selections
- Domain Context for Teacher and Admin profiles (optional with one program; required above one)
- optional `Authored Depth` for Teacher and Admin profiles (the control label for the `notes.learner_level` axis, still named Note Learner Level in `ADR-001`)
- an optional note-accepting Review Set for Teacher and Admin profiles (labelled with the profile's own collection vocabulary — see below)
- an optional editable Section / Part assignment when a target Review Set is selected, pre-filled from the batch subject until the curator edits it
- a `Public` toggle

Topics are discrete rows with `+ Add topic` and per-row removal. They are not note titles. A topic such as `Newton's Laws of Motion` seeds note-content generation; the Study Pack write-back supplies the AI-refined title and tags. The Topics helper states this expectation inline (title and tags are auto-generated; the subject and other batch details apply to every note) so users are not surprised by AI-named notes in their Library.

Pasting a multi-line block into a topic row splits it into one topic per line (`parsePastedTopics`). Splitting is on newlines only (CRLF-aware) — never on commas, because topics legitimately contain commas. Each line is trimmed, clamped to the topic max length, and has a single leading list marker stripped: bullets (`* - – — • · ‣ ◦`) and numbered prefixes (`1.` / `1)` / `(1)`), each requiring trailing whitespace so real topics like `.NET basics` and `1.5 inch standards` are left intact. The pasted-into row is filled when empty (otherwise the parsed rows are inserted after it), source order is preserved, and the import is capped at the topic max with a visible notice when lines are dropped — never silent truncation. Stripping is paste-time only; typed input is never altered.

## Compact Layout

Subject is full-width. The remaining visible metadata uses one responsive two-column grid in this order:

1. Review Set
2. Section / Part (only after selecting a Review Set)
3. Course / Program
4. Domain Context
5. Authored Depth

The Review Set control is a dropdown over owned collections that can accept notes; Goals are excluded. **Its label is not the literal string "Review Set" — it resolves through `getCollectionLabels(profileType)`**, so a `TEACHER` sees "Lesson Plan", a `STUDENT` "Study Plan", and an account with no profile type "Collection". The control renders only for Teacher and Admin, which is exactly the audience whose vocabulary diverges, so hardcoding a label here would split it on the surface they use most. Selecting one pre-fills an empty Authored Depth control from the collection's own or nearest inherited authored depth. It never overwrites a level the curator already chose, and no resolved collection level means no pre-fill (specifically, no `COLLEGE` default). The selected level remains visible and editable before submit. Domain Context is never inferred.

The section field is meaningful only with a selected target plan, so it is hidden and omitted from the request otherwise. It is capped at 120 characters, begins as the first 120 characters of the visible batch subject, and stops tracking subject edits after the curator edits the field. Blank values are omitted. On completion, the backend validates the value through the collection item's existing optional-label guard and adds successful notes with that assignment; a membership failure remains logged and swallowed so it cannot fail the generated batch.

When a curator selects a Domain Context, the form shows that value's covered-subject description directly below the select. The descriptions come from ADR-001's ratified eight-value taxonomy and are guidance copy only; selection, submission, and fallback behavior are unchanged.

**Label vs. axis, stated once so the rest of this file reads unambiguously.** The control is labelled **`Authored Depth`** (`v0.75.0` item 4, `ADR-001` constraint 4). The axis it writes is still `notes.learner_level` and is still called **Note Learner Level** in `ADR-001`, `AGENTS.md`, and the API contract below — **the rename is copy-only and the column did not move.**

**Authored Depth pre-fills, in precedence order** (`ADR-001`'s chain is Review Set → author profile → explicit override):

1. **The selected Review Set's** own or nearest inherited depth.
2. **The author's own profile level**, applied on load — `ADR-001`'s deliberately weak fallback leg.
3. **Whatever the curator explicitly chooses**, which outranks both.

The form tracks which of these produced the current value, because the profile pre-fill lands on load: without that, the control would already be non-empty by the time a Review Set is chosen and the Review Set would be silently ignored, inverting the documented precedence. A Review Set selection therefore replaces a profile pre-fill but never an explicit choice, and a level restored from a retry stash counts as explicit. Every step is a pre-fill into a visible control — **none of it is a server-side default write**, and Domain Context is never inferred at any step.

The collections request is optional enhancement data. A load failure renders an inline error and retry action while leaving the rest of the form usable and submittable without a Review Set. Domain Context and Authored Depth both have explicit blank fallback options. The grid collapses (`empty:hidden`) when no metadata fields are visible for non-teachers, so Subject sits directly above Public. `Public` is a full-width row below the grid with its label and toggle adjacent (not stretched across the card). The Topics list remains full-width below Public.

## Submission

The submit button is a static `Generate`. The topic count is already shown authoritatively by the Topics counter (`X / cap`) above the fields, so the button does not duplicate it. Topic inputs use a 16px font on mobile (`text-base sm:text-sm`) to avoid iOS Safari's focus-zoom while typing.

On a successful queue the page does not show an in-page acknowledgment. It stores a one-shot flash containing the queued count and server-returned `resultId` in `sessionStorage` and redirects to `/library`, where a toast — `Queued N notes — they'll appear here as they finish generating.` — confirms the batch was received. The flash is consumed once on Library mount and does not reappear on refresh. A `sessionStorage` flash is used instead of a query param because the Library rewrites its own URL from filter state, which would strip the param.

For non-admin users, the bulk form loads `/me/plan` and gates on the remaining note-generation quota (v0.31.1). The Topics counter shows `X / min(50, topic notes left)`; when the cap is below 50 it adds a "Capped by your N topic notes left this cycle." helper, and `+ Add topic`, multi-line paste, and the submit button are hard-capped at the remaining topic notes. When ≤ 2 topic notes remain, the shared `NearLimitBanner` (credit-noun "topic note", `note-generation-limit` CTA — the CTA context string is an internal identifier and keeps its original name) appears. If a user queues more topics than they have **Study Packs** remaining, a soft confirmation explains the extra notes are created with content but stay as drafts (Study Pack generation can be run later) before proceeding — note generation is the hard floor, Study Pack is the soft one. The backend re-checks the note-generation quota at submit and rejects an over-quota batch with HTTP 422 before dispatching any work (stale-tab safety net), including a precise message that says how many topic rows to remove. The client surfaces that message and refreshes `/me/plan`. Per AGENTS.md, all gating reads `/me/plan` remaining values — it never recomputes quota from local note lists. If the quota read fails, the form stays usable. ADMIN users bypass the gate entirely.

The Library auto-refreshes so the queued notes appear without a manual refresh. Consuming the flash starts a silent poller (it re-fetches `listNotes()` only — it does not toggle the loading skeleton or reset pagination). Rows that arrive after the initial load (the generated notes surfaced by the poller) animate in via the shared `motion-fade-enter` entrance rather than popping in abruptly; the initial list does not animate wholesale, and the entrance is disabled under `prefers-reduced-motion`. The poller is sustained while any visible note is `GENERATING` or the list is still growing, and it stops after a generous quiet window plus an absolute hard-cap backstop. Because bulk uses throttled **sequential** fan-out, there are recurring windows where no row is generating and none has newly appeared (between one topic finishing and the next topic's row materializing); the quiet window is sized to exceed that inter-topic gap so the batch is not truncated mid-way. A short initial grace covers the redirect moment when no rows exist yet. This is automatic load-on-refresh, not live backend progress. The same poller also auto-refreshes single-note generation, which had the identical never-auto-updates gap.

After the poller settles, the Library makes a best-effort read of the terminal result receipt via `GET /notes/bulk-generate/results/{id}`. If the receipt is not ready yet, the Library retries a bounded number of times; if it is still missing, already read, owned by someone else, or a transient request fails, no banner is shown and the Library continues normally. A receipt with no `failedTopics` and no `quotaBlockedTopics` is silent.

When `failedTopics` is non-empty, the dismissible banner lists the full topic strings with `X of Y notes generated. These couldn't be generated — try again:` and offers `Retry these`. When the terminal receipt also carries a reason for a topic, the banner renders that reason as secondary text beneath the topic. A legacy receipt with a null or absent reason list renders the original topic-only row with no placeholder or spacing change. If duplicate topic names occur, the Library lookup deliberately uses the last recorded reason. The retry action remains driven only by `failedTopics`: it stores those strings plus subject, course/program, Domain Context, Authored Depth, public toggle, **and the selected Review Set** in `sessionStorage`, then navigates to `/library/bulk-generate`; `setBulkGenerationRetryStash` writes the new shape and `consumeBulkGenerationRetryStash` also tolerates the retired extra key in a pre-deploy stash.

When `quotaBlockedTopics` is non-empty, the banner lists those topics separately as monthly note-generation quota blocks and shows the plan-aware upgrade action from `getUpgradeCtas(currentPlan)`. It does not offer `Retry these` for quota-blocked topics because retrying immediately would hit the same limit. Mixed receipts show both groups with their distinct actions.

## Profile-Aware Resolution

The server loads the caller and treats profile data as authoritative for hidden fields:

| Field | Non-teacher | Teacher | Admin |
| --- | --- | --- | --- |
| Subject | Request | Request | Request |
| Course / Program | Profile | Request | Request |
| Domain Context | Hidden in UI; optional request accepted | Optional request | Optional request |
| Note Learner Level | Hidden in UI; optional request accepted | Optional request | Optional request |

The retained `bulk_generation_result.target_profile_type` value is derived in `NoteBulkGenerationService.mapProfileTypeToNoteTargetProfile`: `BOARD_EXAM -> BOARD_TAKER`, `PROFESSIONAL -> PROFESSIONAL`, and every other profile -> `STUDENT`. No client request or terminal response carries it. Domain Context and Note Learner Level remain accepted by the API because this release does not introduce a per-role field policy for those durable axes.

Domain Context and Note Learner Level are parsed through the same validation path as normal note upserts before any background work is dispatched. Unknown values return HTTP 400; omitted or blank values resolve to null. The effective Domain falls back through course/program, while the effective curriculum level falls back through the owner's profile level and then `COLLEGE`.

All profiles use the same pipeline. Teacher/Admin users can provide course/program, Domain Context, and Note Learner Level. The product UI hides the two authoring axes for other profiles, but the backend currently accepts and persists them if a non-Teacher/Admin client sends them; there is no API-level per-role field policy in this release.

## Per-Topic Flow

The endpoint validates the request, queues one throttled background batch on the existing `studyPackGenerationTaskExecutor`, and returns an immediate acknowledgment. The worker processes each topic independently:

1. Validate the topic through content moderation.
2. Resolve course/program, subject, Domain Context, and Note Learner Level through `StudyPackGenerationContextResolver`.
3. Generate note content with the existing LLM note-from-topic operation.
4. Create the note through `NoteService.create` with the topic as its initial title, all resolved batch metadata, and generated content.
5. Apply PUBLIC visibility when requested.
6. Start the existing async Study Pack generation pipeline.

After all topics have been attempted, the worker adds the successfully created note ids to the selected Review Set with the optional section assignment, preserving batch order. It uses `NoteCollectionService.addItems`, so membership positioning, section-length validation, ownership validation, goal rejection, and duplicate filtering stay centralized. Queueing validates ownership and leaf status before dispatch; completion rechecks through the same owner-scoped path across the async boundary. The membership write is its own transaction, independent of already-committed note generation.

Partial failure is normal: failed or quota-blocked topics contribute no note id, so only successful notes are added. If the collection was deleted or any membership write fails at completion, the failure is logged and the batch still completes normally; note generation is neither failed nor retried. Repeating membership is idempotent because `addItems` filters ids already present in the collection.

One topic failure is caught and logged without aborting later topics. Notes appear as real Library rows and independently resolve through the existing `GENERATING -> STUDY_PACK_READY` or `FAILED` states.

## Terminal Result Receipt

v0.29.1 adds one bounded exception to the original no-progress-infrastructure rule: `bulk_generation_result`, a terminal outcome receipt. The service generates the receipt id before queuing and returns it as `resultId` in `BulkGenerateNotesResponse`. At batch completion, `NoteBulkGenerationService.processBatch` writes exactly one receipt with owner id, batch context (`subject`, `courseProgram`, nullable `domainContext`, nullable `learnerLevel`, storage-only `targetProfileType`, `makePublic`), `requestedCount`, `createdCount`, `failedTopics`, nullable `failedTopicReasons`, and `quotaBlockedTopics`. Each reason entry carries the topic, a code, and curator-safe copy: an `AppException` contributes its own code and user-facing message, while any other runtime exception contributes `UNEXPECTED_ERROR`, fixed generic copy, and only the exception's simple class name. Raw messages, stack traces, and arbitrary exception strings are never stored for non-`AppException` failures. Quota-blocked topics carry no reason entry because their separate banner copy already explains the failure. `failedTopics` remains the same plain string list and is still the retry contract. `BulkGenerationResultService.toResponse` deliberately omits the storage-only value. The receipt is written even when there are zero failures and even when a whole-batch setup failure means all accepted topics failed before note creation. Receipts created before failure attribution keep `failedTopicReasons` null and remain readable until normal cleanup.

`GET /notes/bulk-generate/results/{id}` is authenticated-user gated and owner-scoped. It returns the receipt only to the owner, deletes it in the same read-once flow, and returns 404 when the id is unknown, already read, or owned by someone else. A scheduled cleanup removes unread receipts older than 24 hours.

This receipt is not a batch-job entity, not a progress table, not a per-item status table, and not a status enum. It has no in-flight state and is not polled for live progress. The broader v0.29.0 rule remains: no placeholder notes, no failed-note rows, no live batch progress infrastructure.

## Metadata Rule

The batch subject wins. Bulk Study Pack completion applies the AI-refined title and AI tags to the note, then re-applies the batch subject so the AI subject cannot replace it. This completion behavior is bulk-only and does not change normal single-note metadata suggestions.

## Quota Model

- ADMIN bulk generation bypasses note-generation quota, Study Pack quota, per-user AI rate limits, and their usage counters.
- The bypass is local to bulk orchestration and its Study Pack call. Shared quota services do not contain an ADMIN exemption.
- Non-admin bulk generation hard-caps note generations at submit time. If accepted topics exceed remaining monthly note generations, the server returns HTTP 422 and no batch is queued or receipt row is created.
- Non-admin bulk note-content generation uses `NoteGenerationService.generateFromTopic`, so monthly note-generation quota and usage accounting match the single-note Create from topic path.
- Study Pack shortfall is allowed at submit time. Those notes can still be created with content and remain drafts when Study Pack generation cannot start.
- If note-generation quota runs out before a note row is created, the topic is written to `quotaBlockedTopics`. If a note row exists and a later make-public or Study Pack enqueue step fails, the topic is not added to any receipt failure list because the Library row is visible.
- Throttled sequential fan-out protects the LLM provider while Study Pack workers continue on the existing executor.

## Discoverability

The Library Create menu exposes `Bulk generate` next to normal note creation and file import. The single Note Create `Create from topic` panel includes a persistent inline link, `Have a list of topics? Generate them all at once`, pointing to `/library/bulk-generate`. This is a navigational affordance, not a one-time guidance tip.

Help includes a deep-linkable `Bulk Generation` guide at `/help#bulk-generate` covering what one topic produces, background Library arrival, quota behavior, and when retry is appropriate.

## Out Of Scope

- persisted batch/job entities, live progress tables, per-item progress rows, or new status enums beyond the terminal read-once receipt
- a batch progress/status page
- live partial-execution progress; terminal partial-outcome messaging for content-generation and note-generation quota failures is in scope through the receipt
- production regeneration or backfill of existing Study Packs
- collection-level bulk quiz generation
- async quiz generation

No new analytics event was added for v0.29.1.
