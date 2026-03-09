# DATA_MODEL.md — Study Snap

This document consolidates the data model direction for Study Snap from the legacy architecture docs, project context, and later user-account decisions.

## Naming direction

### Legacy term
- `Review`

### Preferred forward-looking term
- `StudyPack`

Reason:
- "Review" is becoming vague as the product grows.
- "Study Pack" matches product language, Study Library behavior, and user-facing terminology.

Transitional note:
- legacy code, tables, or endpoints may still use `Review` terminology
- future-facing schema and docs should prefer `StudyPack`

## Core entities

### users

Purpose:
- authentication identity
- lightweight personal profile
- ownership of saved Study Packs

Recommended fields:
- `id`
- `email`
- `password_hash`
- `first_name`
- `last_name`
- `display_name` (optional)
- `country_code` (optional, ISO-style such as `PH`, `US`)
- `profile_type` (nullable enum)
- `status`
- `created_at`
- `updated_at`
- `last_login_at` (nullable)
- `email_verified_at` (nullable)

### display_name behavior
- optional field
- frontend may auto-fill it from `first_name`
- if blank, UI may fall back to `first_name`
- customized value should be preserved if the user edits it

### profile_type
Purpose:
- onboarding / segmentation / personalization
- not authorization
- not permissions
- not billing

Initial values:
- `STUDENT`
- `PARENT`
- `PROFESSIONAL`

Teacher mode is intentionally deferred.

### subscriptions

Purpose:
- plan history
- billing state
- future analytics
- future integration with billing providers

Recommended fields:
- `id`
- `user_id`
- `plan_type`
- `status`
- `start_at`
- `end_at` (nullable)
- `created_at`
- `updated_at`

Possible future additions:
- `provider`
- `provider_customer_id`
- `provider_subscription_id`
- `cancelled_at`
- `renewal_at`
- `trial_ends_at`

Initial plan direction:
- `FREE`
- `PREMIUM`

Demo mode is separate from authenticated subscriptions.

### study_packs

Purpose:
- store generated reusable study content

Recommended direction:
- `id`
- `owner_user_id` (nullable only while anonymous/demo support still exists)
- `anon_id` (nullable for anonymous or demo flows)
- `input_type` (`TEXT` | `IMAGE`)
- `title`
- `summary`
- `key_concepts` (json/jsonb array)
- `quiz` (json/jsonb array)
- `tags` (nullable json/jsonb array or text[])
- `ocr_confidence` (nullable numeric)
- `status`
- `error_code` (nullable)
- `model_used` (nullable)
- `input_tokens` (nullable)
- `output_tokens` (nullable)
- `cached_input_tokens` (nullable)
- `estimated_cost` (nullable)
- `created_at`
- `updated_at`

Legacy architecture also referenced:
- `model_tier` (`FREE | PREMIUM`)

Recommendation:
- long term, prefer tying effective plan behavior to subscriptions / usage rules rather than only storing `model_tier` on the Study Pack row.

### study_pack_drafts / study_pack_drafts

Purpose:
- store OCR low-confidence extracted text for user confirmation flow

Recommended fields:
- `id`
- `owner_user_id` (nullable)
- `anon_id` (nullable)
- `extracted_text`
- `ocr_confidence`
- `created_at`
- `expires_at`

### share_links

Purpose:
- public sharing of generated Study Packs

Fields:
- `token`
- `study_pack_id`
- `is_public`
- `created_at`
- `expires_at` (nullable)
- `view_count` (optional)

### usage_daily (legacy optional direction)

Purpose:
- simple counter-based usage tracking for MVP

Fields:
- `user_id` or `anon_id`
- `date`
- `count`

### usage_events (future recommended direction)

Purpose:
- analytics-ready usage tracking

Potential fields:
- `id`
- `user_id`
- `study_pack_id` (nullable)
- `event_type`
- `request_units`
- `created_at`

## Relationships

### User → StudyPack
- one user owns many Study Packs

### User → Subscription
- one user can have many subscription records over time
- typically one active subscription at a time should be enforced by service logic and/or constraints later

### StudyPack → ShareLink
- one Study Pack can have one or more share links over time

### StudyPackDraft → User
- optional relationship when OCR confirmation belongs to an authenticated user

## JSON structures

### Quiz item
Preferred prompt-contract structure:
```json
{
  "question": "string",
  "choices": ["string", "string", "string", "string"],
  "answerIndex": 0,
  "explanation": "string"
}
```

### keyConcepts
Simple string array:
```json
["Photosynthesis", "Chlorophyll", "Glucose"]
```

### tags
Simple string array:
```json
["Biology", "Photosynthesis"]
```

## Deferred entities

### account_links
Parked for future family plans / linked learners.

Potential fields:
- `id`
- `owner_user_id`
- `linked_user_id`
- `relationship_type`
- `status`

Potential relationship types:
- `PARENT_OF`
- `GUARDIAN_OF`

Not part of v1 user accounts.

### teacher/classroom entities
Deferred.

## Migration guidance

Suggested implementation order:
1. keep legacy `Review` entities working where they still exist
2. introduce Study Pack naming in DTOs/docs/new code
3. add users
4. add subscriptions
5. link saved Study Packs to authenticated owners
6. evolve the Study Library on top of owned Study Packs


