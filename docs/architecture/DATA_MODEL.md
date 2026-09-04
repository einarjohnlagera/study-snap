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
- `course_program_id` (nullable FK to `course_programs`; populated when the legacy string exactly matches the catalog, not read by current note flows)
- `domain_context` (nullable `VARCHAR(64)`, `DomainContext` enum; no default)
- `learner_level` (nullable `VARCHAR(32)`, note-level `LearnerLevel` enum; no default)
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

**Two representations coexist by design (`v0.71.0`), one per authoring mode.** A **learner** stores one personal free-text program in `notes.course_program`; a **curator** (TEACHER or ADMIN, post-onboarding) stores one-or-many catalog ids in `note_course_program` and leaves `notes.course_program` null. Neither is "the" source of truth — which one applies is decided by who authored the note. Copies inherit both.

- `users.course_program` is the profile-level default; `notes.course_program` is the note-level persisted value **for learner-authored notes**.
- `course_programs` is the curated program catalog; `program_families` holds architecture-level groupings such as `Engineering`.
- `notes.course_program_id` and `users.course_program_id` are nullable references populated by the V106 audited-vocabulary backfill. Current note/user flows do not read them.
- Distinct course/program suggestions are derived from persisted note values plus the authenticated user's saved profile value.
- Current authoring suggestions and discovery labels continue to normalize the retained strings; the catalog and nullable FKs do not replace those read paths yet.
- Normalization should trim whitespace, standardize dash formatting, and compare equivalent course/program values case-insensitively.
- Learning Profile UI now requires both `learnerLevel` and `courseProgram` for onboarding completion and later profile saves, while storage remains nullable for pre-existing users until they update those fields.
- `course_program` remains the legacy program label and fallback authoring context; it is not the classification apex for Domain Context, Note Learner Level, subject, or tags.

Program catalog fields:

- `program_families`: `id`, unique `name`, `created_at`.
- `course_programs`: `id`, unique `name`, nullable `program_family_id`, nullable `exam_goal_slug`, `created_at`.
- `note_course_program` (`V107`): `id`, `note_id`, `course_program_id`, `created_at`. Unique on `(note_id, course_program_id)`; FK to `notes` is `ON DELETE CASCADE`, FK to `course_programs` is not. Indexed on both FK columns.
- **`V107`'s 1:1 backfill inserted one row per note whose string matched the catalog exactly** (plus the `Bsed` -> `Education` alias) — for *every* note, learner-authored included. **`V108` deletes the learner-authored subset**, because a learner's personal free-text program must not be mechanically materialized into a catalog Applicable Program row: doing so silently turns a private label into a discovery fact the learner never asserted and cannot edit. `V108`'s predicate is the exact inverse of `V107`'s insert, restricted to non-curator owners. **Do not "restore" those rows.**
- **Read semantics are join-first with a legacy-string fallback**: `EXISTS(join rows) OR (no join rows AND legacy string matches)`. Identical to pre-`v0.71.0` behavior at one row per note, and correct once a note carries several. `notes.course_program` therefore stays load-bearing on read paths; retiring the fallback is a separate, unscheduled decision.
- **Facet counts sum above the note total** once a note carries several programs. That is correct per `ADR-001` and needs a UI affordance, not a fix.
- **Applicable Programs never reach an LLM prompt.** The generation resolver consults the join only when a note has *exactly one* row; several rows mean there is no single authoring domain, so it falls through to the strings rather than picking arbitrarily.
- Catalog seed values are deterministic curator data. They are never derived from `SELECT DISTINCT` over author-supplied strings.
- V106 backfills FKs by exact string equality only, except for the one sanctioned literal alias `Bsed` -> `Education`. It never clears, normalizes, or rewrites a legacy `course_program` string.
- Excluded levels, subjects, goals, families, and contested values retain their strings with null FKs.
- Exam Hub program-name resolution reads `course_programs.exam_goal_slug` directly through a cached provider; it does not read either note/user FK.

Canonical authoring-axis storage rule:

- `notes.domain_context` and `notes.learner_level` are nullable author-supplied note metadata governed by `ADR-001`.
- `domain_context` uses the closed eight-value `DomainContext` Java enum persisted by name; adding a value is an architecture decision.
- `learner_level` reuses the existing `LearnerLevel` enum at note scope and is distinct from `users.learner_level`.
- `NULL` is valid for both columns. In particular, null `domain_context` marks the program-name fallback state; neither column has a database default or PR-1 backfill.
- V104 classifies the 27 legacy `Grade School` and `Junior High` note rows as `GENERAL_EDUCATION` with their exact note learner level while retaining `course_program`.
- V105 classifies four content-reviewed `High School` rows as `GENERAL_EDUCATION` with their curator-assigned level, deliberately leaves six unclassifiable `High School` rows with both axes null, and gives the Senior High strand rows only `SENIOR_HIGH` so their retained strand remains the fallback authoring domain.
- Generation consumes both axes through `StudyPackGenerationContextResolver`. Teacher/Admin Note Editor and Bulk Generate surfaces expose them as optional authoring metadata; other profile UIs keep them hidden.

Library/discovery payload usage:

- Private and public note-list payloads should reuse note metadata directly from `notes`, especially:
  - `course_program`
  - `domain_context`
  - `learner_level`
  - `subject`
  - `tags`
  - `visibility`
  - `created_at`
  - `updated_at`
- Public discovery payloads may expose both the note-level authoring axes and owner profile metadata, but filtering remains unchanged in PR 1.

## Bulk Generation Result Receipt

`bulk_generation_result` is a terminal, read-once receipt rather than a batch job or progress model.

Relevant fields:

- `id`
- `owner_user_id`
- `subject`
- `course_program` (nullable)
- `domain_context` (nullable `VARCHAR(64)`, `DomainContext` enum)
- `learner_level` (nullable `VARCHAR(32)`, note-level `LearnerLevel` enum)
- `target_profile_type` — **retained but no longer read by product code as of `v0.83.0`.** Still `NOT NULL` with a CHECK constraint, written server-side from the owner's profile. Kept because it is `V117`'s input and `[CHECKPOINT — due 2026-09-16]` cannot run its kill criterion without it; the drop is phase 4.
- `make_public`
- `requested_count`
- `created_count`
- `failed_topics` (`jsonb`)
- `quota_blocked_topics` (`jsonb`)
- `created_at`

The two authoring-axis columns preserve the batch context for retry. Receipts are owner-scoped, deleted when consumed, and expired after 24 hours.

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

## Combined Quizzes and Share Links

`combined_quizzes` is an immutable owner-scoped snapshot for a quiz assembled across several notes.
Relevant fields are `id`, `owner_user_id` (FK to `users`, cascade delete), `title`, JSONB `sections`, and
`created_at`. A section contains its copied source-note title and ordered copied `QuizItem`s. **There is no
foreign key to `notes`**: source-note deletion and generated-quiz regeneration must not remove or mutate a
combined quiz a recipient may already be taking.

`quiz_share_links` has one token namespace and one usage counter. Its target is an exclusive arc: exactly
one of nullable `generated_quiz_id` (FK to `generated_quizzes`, cascade delete) and nullable
`combined_quiz_id` (FK to `combined_quizzes`, cascade delete) is populated. PostgreSQL enforces the
exactly-one invariant. Existing single-note rows remain on the generated-quiz arc unchanged.

## Concept Health

`concept_health` stores per-user, per-Study-Pack review signals for one normalized concept.

Relevant fields:

- `user_id`
- `study_pack_id`
- `concept`
- `last_correct_at`
- `last_incorrect_at`
- `incorrect_streak` (`NOT NULL DEFAULT 0`)
- `created_at`
- `updated_at`

`incorrect_streak` is consecutive, not cumulative. Recording a miss increments it in the same transaction as `last_incorrect_at`; recording a correct answer resets it to `0` in the same transaction as `last_correct_at`.

## Copy-Based Versioning

Copy creates a new Note row.

Copy includes:

- `title`
- `course_program`
- `domain_context`
- `learner_level`
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
- `course_program_id` (nullable FK to `course_programs`; not read by current profile flows)
- `public_profile_visible` (boolean, default true)
- `country_code` (optional)
- `profile_type` (nullable enum)
- `birth_year` (nullable account-global current declaration; first collected only in the linked-learner flow)
- `birth_year_updated_at` (nullable timestamp of the last correction; existing declarations are not back-dated and no value history is retained)
- `role` (`USER` | `ADMIN`)
- `status`
- `token_version`
- `failed_login_attempts`
- `locked_until`
- `email_verified_at` (nullable)
- `pending_email` (nullable)
- `onboarding_completed_at` (nullable)
- `product_onboarding_completed_at` (nullable)
- `created_at`
- `updated_at`
- `last_login_at` (nullable)

Linked-learner data:

**⚠️ This section was frozen at `v0.89.1` for five releases and was corrected at `v0.98.0`.** It described three statuses where there are four, and asserted that acceptance authorizes the progress read — false since `v0.93.0` — while omitting four tables shipped across `v0.90.0`–`v0.95.0`. Recorded because the drift is the point: a schema section that is not updated alongside a migration becomes confidently wrong rather than merely incomplete.

- `linked_learner_relationships` stores the directional supporter → learner relationship and its `PENDING`, `ACCEPTED`, `REVOKED` or `EXPIRED` state, plus `expires_at`.
  - **⚠️ `ACCEPTED` AUTHORIZES NOTHING BY ITSELF.** Since `v0.93.0` every cross-user read requires `ACCEPTED` **and** a live per-scope grant row; acceptance creates the *capacity* to grant. Absence of a grant means no access.
  - **⚠️ `EXPIRED` is terminal** (`v0.97.0`) and names an unconfirmed request that timed out, distinct from a deliberate `REVOKED`. Re-inviting mints a new relationship rather than reviving either.
  - **⚠️ `expires_at` means THE DEADLINE, for every status** — it is not overwritten with the moment a sweep ran. **A NULL is meaningful, not missing:** acceptance clears it and a consent pause leaves it clear, so a NULL means the row is not on the expiry clock at all. That NULL is the entire mechanism protecting a paused relationship from being expired.
- `linked_learner_invitations` (`v0.90.0`) stores an email-keyed invitation against the typed ADDRESS rather than a resolved user id, which is what closed the account-existence oracle. It carries its own `expires_at` and its own `PENDING | ACCEPTED | REVOKED` check constraint. **⚠️ Its status vocabulary deliberately excludes `EXPIRED`** even though the Java enum is shared with relationships: invitation expiry is expressed by `expires_at`, and that check constraint is the only guard against writing a value the table does not mean.
- `linked_learner_invitation_links` (`v0.94.0`) stores a single-use shareable link. `redeem()` sets `redeemed_at` / `redeemed_by_user_id`, making the row **terminal the moment it is used**; the row carries no relationship id, and the relationship carries no link id, so there is deliberately no path back from a relationship to the link that created it.
- `linked_learner_grants` (`v0.92.0`) stores one directional permission per `(relationship_id, from_user_id, scope)`, `scope IN ('ACTIVITY','PROGRESS')`, live while `revoked_at IS NULL`. Creation is conditional on the relationship being `ACCEPTED` at write time; **withdrawal deliberately has no status predicate**, so sharing can always be turned off. **⚠️ A terminal transition cuts every live grant, but a consent PAUSE does not** — `v0.93.0` made the row survive the pause by design so sharing resumes on re-acceptance.
- `linked_learner_provisional_birth_years` (`v0.95.0`) holds a link redeemer's declared year against ONE relationship until the creator confirms. Promotion writes the account-global column and deletes the row; revocation and expiry delete it too. **⚠️ It is keyed by RELATIONSHIP and has no user column**, so any read must join `linked_learner_relationships` on `learner_user_id` — that join is the privacy boundary, not a filter applied afterwards.
- `linked_learner_guardian_consents` stores one relationship-specific attestation fact. A correction that makes consent necessary moves an un-consented accepted relationship back to `PENDING` in the same transaction as the year update; it never deletes or fabricates consent.

## Feedback Images

Purpose:

- store one optional screenshot for a submitted feedback item without adding blob hydration to feedback list reads

Fields:

- `feedback_id` (primary key, foreign key to `feedback.id`, cascade delete)
- `content_type` (`image/png` | `image/jpeg` | `image/webp`)
- `size_bytes` (maximum `2MB`)
- `image_bytes`
- `created_at`

Storage notes:

- Screenshot bytes belong only in `feedback_image`; they must not be added to `FeedbackEntity` or the `feedback` table.
- Admin list reads project only screenshot existence. Image bytes are loaded only for one feedback detail request.

## Subscriptions

Purpose:

- plan state and billing history

Recommended fields:

- `id`
- `user_id`
- `plan_type` (`FREE` | `PLUS` | `PRO`)
- `status`
- `start_at`
- `end_at` (nullable)
- `provider_customer_id` (nullable)
- `provider_subscription_id` (nullable)
- `created_at`
- `updated_at`

Billing notes:

- Active provider is currently `XENDIT`.
- Current paid-plan activation uses hosted invoice checkout and webhook-confirmed upgrades rather than recurring subscriptions.
- Current manual-renewal model is `30`-day Monthly access for `PLUS` and `PRO`, plus `365`-day Annual access for `PRO`.
- `subscriptions` is the only source of truth for plans and entitlements.
- `users` must not store plan flags or plan state.
- `subscriptions` stores full plan history; users may have multiple historical rows.
- Only one `ACTIVE` subscription row should exist per user at a time.
- The current plan is resolved from the active valid subscription row for that user.
- A paid activation expires the currently active other-plan row when needed and creates a new active paid history row.
- Manual renewals extend the active row when the purchased plan matches the current active paid plan.
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
- `plan_type` (`PLUS` | `PRO`)
- `billing_cycle` (`MONTHLY` | `YEARLY`)
- `original_amount`
- `discount_amount`
- `amount` (final charged amount)
- `currency`
- `status` (`PENDING` | `SUCCESS` | `FAILED`)
- `provider_reference_id` (Xendit `external_id`)
- `voucher_id` (nullable applied `discount_vouchers` reference)
- `subscription_id` (nullable reference to the subscription activated by the payment)
- `checkout_url` (nullable stored Xendit hosted invoice URL)
- `expires_at` (nullable invoice expiry timestamp)
- `created_at`

Behavior notes:

- Pending transactions may be reused when the same user starts upgrade again before the invoice expires.
- Reuse requires matching plan, billing cycle, final amount, and voucher state.
- Expired pending transactions should not remain reusable.
- Payment transactions are billing-event history only; they are not the source of truth for plan access.
- Paid-plan activation is derived from validated webhook outcomes, not from frontend redirect completion.

## Discount Vouchers

Purpose:

- define automatic and code-based checkout discounts without hardcoding billing exceptions

Key fields:

- `id`
- `code`
- `discount_type` (`FIXED_AMOUNT` | `PERCENTAGE` | `OVERRIDE_PRICE`)
- `discount_value`
- `currency`
- `billing_cycle_scope`
- `plan_scope`
- `region_scope`
- `new_subscribers_only`
- `requires_code`
- `max_redemptions`
- `valid_from`
- `valid_until`
- `is_active`

## Voucher Redemptions

Purpose:

- preserve successful discount usage history without treating pending checkout as redeemed

Key fields:

- `id`
- `voucher_id`
- `user_id`
- `subscription_id`
- `payment_transaction_id`
- `redeemed_at`
- `applied_amount`
- `currency`

Behavior notes:

- Voucher redemptions are created only after a validated `PAID` webhook.
- `payment_transaction_id` should stay idempotent for webhook retries.

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

- stored in `quick_review_sessions` and linked to a user
- anchored by either the non-null `study_pack_id`/`note_id` pair or `source_collection_id`; the
  validated `chk_quick_review_sessions_anchor` prevents anchorless rows
- active-session partial unique indexes independently enforce one active row per
  `(user_id, study_pack_id, session_mode)`, `(user_id, note_id, session_mode)`, and
  `(user_id, source_collection_id, session_mode)`, each with an explicit non-null anchor predicate
- all three anchor FKs use `ON DELETE CASCADE`; no historical rows were backfilled for the collection
  anchor
- store progress/completion, score, retry count, confidence feedback

Challenge Quiz sessions:

- linked to Note and user
- store generated challenge quiz payload, timer basis, answers, completion stats, weak concepts

Adaptive Practice sessions:

- note scope uses the pack/note anchor; plan scope uses the collection anchor
- store generated adaptive payload, progress/completion, score data

Ask Companion sessions:

- live in the dedicated `ask_companion_sessions` table rather than quiz-session persistence
- link one owned top-level collection and one user
- store `ACTIVE` / `ENDED` status, a bounded `turn_count`, timestamps, and JSONB question/answer turn history
- allow at most one `ACTIVE` row per user/collection through a partial unique index
- end after six successful turns; a later question starts a new row and consumes another monthly session

## Usage Tracking

Track usage buckets independently:

- Study Pack generation quota
- Challenge Quiz quota
- Adaptive Practice quota
- Ask Companion session quota

Possible schemas:

- `usage_daily` (counter style)
- `usage_events` (analytics-ready event style)

Current implementation notes:

- `user_usage` is period-based:
  - includes `period_start` and `period_end`
  - counters are incremented against the resolved active billing window
  - `ask_companion_used_this_month` counts conversation starts, not individual turns
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
- ProgramFamily -> CoursePrograms: one-to-many
- CourseProgram -> Notes/Users: optional catalog references alongside retained legacy strings
- Note -> Generated Study Pack fields: one-to-one enhancement state
- Note -> QuickReviewSession: one-to-many
- Note -> ChallengeSession: one-to-many
- Note -> AdaptiveSession: one-to-many
- Note/StudyPack -> ShareLink: one-to-many over time
- User -> Subscription: one-to-many history (typically one active)
- User/Collection -> AskCompanionSession: one-to-many over time, at most one active

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
