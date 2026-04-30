# DATA_MODEL.md - NoteLib

This document defines the NoteLib data model direction for the note-first product model.

## Naming and Compatibility

- Product language is Note-first.
- Database schema/table names are preserved unless explicitly migrated.
- Legacy table names such as `study_packs` may remain while domain ownership shifts to Notes.

## Core Entity: Note

`notes` is the primary product entity.

Purpose:

- store user-authored learning input
- track note lifecycle state
- anchor generated study outputs and review history

Recommended fields:

- `id`
- `owner_user_id`
- `title` (nullable)
- `course_program` (nullable, defaults from `users.course_program` for new notes)
- `subject` (nullable)
- `content` (text, required)
- `tags` (text[] or json array, default empty)
- `state` (`DRAFT` | `GENERATING` | `FAILED` | `STUDY_PACK_READY`)
- `visibility` (`PRIVATE` | `PUBLIC`)
- `created_at`
- `updated_at`

Subject storage rule:

- `notes.subject` stays as free text on the note record.
- There is no normalized `subjects` table in the current architecture.
- Distinct subject suggestions and library filters are derived from persisted note subject values.
- Subject reuse is built by normalizing saved note subjects rather than by writing to a second subject catalog table.
- Normalization should trim whitespace, standardize dash formatting, and compare equivalent subjects case-insensitively.
- AI-generated subjects should prefer reusable academic library labels such as `Primary field – subtopic`, not broad umbrella categories.

Course / Program storage rule:

- `users.course_program` is the profile-level default and `notes.course_program` is the note-level persisted source of truth.
- There is no normalized `course_programs` table in the current architecture.
- Distinct course/program suggestions are derived from persisted note values plus the authenticated user's saved profile value.
- Course/program reuse is built by normalizing saved values rather than by writing to a second taxonomy table.
- Normalization should trim whitespace, standardize dash formatting, and compare equivalent course/program values case-insensitively.
- Learning Profile UI now requires both `learnerLevel` and `courseProgram` for onboarding completion and later profile saves, while storage remains nullable for pre-existing users until they update those fields.
- `course_program` is the top-level library shelf, while `subject` stays the more specific academic topic and `tags` remain the fine-grained keywords.

Library/discovery payload usage:

- Private and public note-list payloads should reuse note metadata directly from `notes`, especially:
  - `course_program`
  - `subject`
  - `tags`
  - `visibility`
  - `created_at`
  - `updated_at`
- Public discovery payloads may also expose owner `learner_level` so Public Library can filter notes by audience level without introducing a separate note-level learner field.

## Generated Study Pack Fields

Generated fields are linked to the same Note.

Depending on current schema, these can be:

- embedded/generated columns on `notes`, or
- stored in legacy `study_packs` rows with a 1:1 link to `notes.id`

Generated fields:

- `summary`
- `key_concepts`
- `quiz`
- optional generation metadata (`model_used`, token usage, estimated cost, timestamps)

State transition:

- queued note-owned generation sets Note state from `DRAFT` or `FAILED` to `GENERATING`
- successful generation persists a Study Pack and sets Note state to `STUDY_PACK_READY`
- failed generation sets Note state to `FAILED` without consuming Study Pack quota

Ownership rule:

- summaries, key concepts, quizzes, and practice/performance data are owned by `note_id`
- legacy `study_pack_id` references can remain for compatibility, but `note_id` is canonical

## Copy-Based Versioning

Copy creates a new Note row.

Copy includes:

- `title`
- `course_program`
- `subject`
- `tags`
- `content`

Copy does not include:

- generated summary/key concepts/quiz
- review sessions and performance history
- quiz sessions
- share links

## Users

Purpose:

- authentication identity
- profile data
- ownership of notes and generated study outputs

Recommended fields:

- `id`
- `email`
- `password_hash`
- `first_name`
- `last_name` (optional)
- `display_name` (optional)
- `bio` (optional, up to 200 chars)
- `learner_level` (nullable enum: `GRADE_SCHOOL`, `JUNIOR_HIGH`, `SENIOR_HIGH`, `COLLEGE`, `BOARD_EXAM_REVIEW`, `PROFESSIONAL`, `PERSONAL_LEARNING`)
- `course_program` (nullable, up to 120 chars)
- `public_profile_visible` (boolean, default true)
- `country_code` (optional)
- `profile_type` (nullable enum)
- `role` (`USER` | `ADMIN`)
- `status`
- `token_version`
- `failed_login_attempts`
- `locked_until`
- `email_verified_at` (nullable)
- `created_at`
- `updated_at`
- `last_login_at` (nullable)

## Subscriptions

Purpose:

- plan state and billing history

Recommended fields:

- `id`
- `user_id`
- `plan_type` (`FREE` | `PREMIUM`)
- `status`
- `start_at`
- `end_at` (nullable)
- `provider_customer_id` (nullable)
- `provider_subscription_id` (nullable)
- `created_at`
- `updated_at`

Billing notes:

- Active provider is currently `XENDIT`.
- Current Premium activation uses a hosted invoice checkout and webhook-confirmed premium upgrade rather than recurring subscriptions.
- Current Premium billing model is prepaid/manual-renewal for `30` days of access per successful payment.
- `subscriptions` is the only source of truth for plans and entitlements.
- `users` must not store Premium flags or plan state.
- `subscriptions` stores full plan history; users may have multiple historical rows.
- Only one `ACTIVE` subscription row should exist per user at a time.
- The current plan is resolved from the active valid subscription row for that user.
- A paid Premium activation expires the currently active non-Premium row when needed and creates a new active Premium history row.
- Manual renewals extend the active Premium `end_at` rather than creating duplicate active Premium rows.
- `subscriptions` remains the place for future recurring billing, expiry logic, and provider-managed renewals.
- Webhook event idempotency is enforced through persisted webhook events plus `payment_transactions(provider, provider_reference_id)` uniqueness.

## Payment Transactions

Purpose:

- store hosted checkout attempts and webhook-resolved payment outcomes

Recommended fields:

- `id`
- `user_id`
- `provider` (`XENDIT`)
- `billing_type` (`PREPAID`)
- `plan_type` (`PREMIUM`)
- `amount`
- `currency`
- `status` (`PENDING` | `SUCCESS` | `FAILED`)
- `provider_reference_id` (Xendit `external_id`)
- `subscription_id` (nullable reference to the subscription activated by the payment)
- `checkout_url` (nullable stored Xendit hosted invoice URL)
- `expires_at` (nullable invoice expiry timestamp)
- `created_at`

Behavior notes:

- Pending transactions may be reused when the same user starts upgrade again before the invoice expires.
- Expired pending transactions should not remain reusable.
- Payment transactions are billing-event history only; they are not the source of truth for plan access.
- Premium activation is derived from validated webhook outcomes, not from frontend redirect completion.

## OCR Confirmation Drafts

`study_pack_drafts` (or equivalent legacy table) stores low-confidence OCR extraction for confirmation flow.

Recommended fields:

- `id`
- `owner_user_id` (nullable for anonymous flow)
- `anon_id` (nullable)
- `extracted_text`
- `ocr_confidence`
- `created_at`
- `expires_at`

Create/Edit Note OCR flow:

- OCR extraction is triggered from note authoring UI
- extracted text is inserted into Note `content` for user review/edit before generation
- OCR extraction must not auto-save and must not auto-generate

## Share Links

Purpose:

- public token sharing of Study Pack-ready Note output

Recommended fields:

- `token`
- `note_id` (or `study_pack_id` in legacy schema)
- `is_public`
- `created_at`
- `expires_at` (nullable)
- `view_count` (optional)

## Public Notes And Profiles

Public discovery/showcase is still note-first.

Recommended additional note/profile fields and derived values:

- `notes.visibility` remains the public-note source of truth (`PRIVATE` | `PUBLIC`)
- `notes.slug` (or equivalent canonical public route segment) powers `/public/library/{subject}/{slug}`
- `notes.copied_from_note_id`
- `notes.copied_from_user_id`
- `users.display_name` is the public identity field
- `users.bio` is optional public learning-profile copy
- `users.learner_level` may be exposed publicly when present
- `users.course_program` may be exposed publicly when present
- `users.public_profile_visible` gates non-owner access to `/public/profile/{userId}`

Derived public-profile response fields:

- `publicNotesCount` = count of public notes owned by the user
- `totalCopies` = sum of copy attribution from the user's public notes
- `totalShares` = sum of public-note share analytics for that user's public notes
- `totalViews` = sum of public-note view analytics for that user's public notes
- `isOfficial` = backend-derived from the configured official/system account rules

Public-profile pages must never expose email addresses.

## Quiz Sessions and Performance

Quick Review sessions:

- linked to Note (`note_id`) and user
- store progress/completion, score, retry count, confidence feedback

Challenge Quiz sessions:

- linked to Note and user
- store generated challenge quiz payload, timer basis, answers, completion stats, weak concepts

Adaptive Practice sessions:

- linked to Note and user
- store generated adaptive payload, progress/completion, score data

## Usage Tracking

Track usage buckets independently:

- Study Pack generation quota
- Challenge Quiz quota
- Adaptive Practice quota

Possible schemas:

- `usage_daily` (counter style)
- `usage_events` (analytics-ready event style)

Current implementation notes:

- `user_usage` is period-based:
  - includes `period_start` and `period_end`
  - counters are incremented against the resolved active billing window
- `webhook_events` stores provider webhook idempotency state:
  - `provider`, `event_id`, `event_type`, `status`, timestamps
  - unique `(provider, event_id)` prevents duplicate processing

## Refresh Tokens

Purpose:

- secure refresh-token rotation and keep-signed-in sessions

Recommended fields:

- `id`
- `user_id`
- `token_hash`
- `expires_at`
- `revoked_at` (nullable)
- `created_at`
- `last_used_at` (nullable)
- `keep_signed_in`
- `device_name` (nullable)
- `ip_address` (nullable)
- `user_agent` (nullable)

## Relationships

- User -> Notes: one-to-many
- Note -> Generated Study Pack fields: one-to-one enhancement state
- Note -> QuickReviewSession: one-to-many
- Note -> ChallengeSession: one-to-many
- Note -> AdaptiveSession: one-to-many
- Note/StudyPack -> ShareLink: one-to-many over time
- User -> Subscription: one-to-many history (typically one active)

## JSON Structures

Quiz item:

```json
{
  "question": "string",
  "choices": ["string", "string", "string", "string"],
  "correctIndex": 0,
  "explanation": "string",
  "concept": "string"
}
```

Notes:

- Canonical stored/shared quiz data uses `correctIndex`, not answer letters or prefixed choice text.
- Raw LLM generation may return `answer` as `A` / `B` / `C` / `D`, but backend normalization converts that into `correctIndex` before persistence.
- Quiz session state should store selected canonical choice indexes. Compatibility loaders may still accept legacy answer text or `answerIndex` payloads while migrating old sessions.

Key concepts:

```json
["Photosynthesis", "Chlorophyll", "Glucose"]
```

Tags:

```json
["Biology", "Photosynthesis"]
```

## Migration Guidance

Suggested migration direction:

1. keep legacy tables functioning
2. introduce/confirm `notes` as primary owned entity
3. map generated output to Note lifecycle (`DRAFT` -> `GENERATING` -> `STUDY_PACK_READY` or `FAILED`)
4. add visibility field and public-note query surface
5. add copy endpoint for versioning behavior
6. shift feature code to Note-centric ownership checks
7. keep legacy naming compatibility where required by existing schema
8. ensure generated/practice ownership paths consistently resolve through `note_id`
