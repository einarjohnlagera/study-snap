# ARCHITECTURE.md - NoteLib

This document describes the NoteLib system architecture and how backend services connect to the web frontend.

Current architecture baseline: `v0.5.0 - Public Profiles & Public Notes`

Core workflow:

Notes (Draft) -> Generate Study Pack -> Review (Quick/Challenge/Adaptive) -> Improve -> Make a Copy -> Generate again

## Goals (MVP)

- convert notes into structured study materials
- support pasted text and image upload (OCR)
- provide low-confidence OCR fallback with editable extracted text
- persist generated outputs for revisit and review
- support shareable Study Pack links and remix/copy
- enforce auth ownership and usage limits

Non-goals for current MVP:

- heavy analytics pipelines
- classroom/teacher management
- gamification-heavy loops
- full exam simulation grading engine

## System Overview

### Frontend (Next.js)

Routes:

- `/` landing
- `/demo` demo walkthrough (no real generation)
- `/study` quick note input flow (legacy-compatible)
- `/notes/new` New Note
- `/notes/{id}/edit` Edit Note
- `/dashboard` guidance + library entry
- `/library` Library (owned notes, private workspace)
- `/library/public` Public Library (public notes from you, the community, and official NoteLib content)
- `/public/profile/{userId}` public creator profile (public notes, contribution stats, and owner-only public-page controls)
- `/notes/{id}` Note Detail (owner view; unified Note + Study Pack view)
- `/public/library/{subject}` public subject listing
- `/public/library/{subject}/{slug}` public read-only note detail
- `/settings` plan/billing and account controls
- `/profile` account profile
- `/p/{token}` public shared Study Pack

Mode-based note creation stays on the same Note pipeline:

- `/notes/new` -> normal note creation
- `/notes/new?mode=quiz` -> quiz-first creation flow
- `/notes/new?source=paste` -> paste-material entry
- `/notes/new?source=upload` -> upload-material entry

Frontend calls backend via `NEXT_PUBLIC_API_BASE_URL`.

Shared frontend note-list presentation lives in `frontend/components/notes/shared-note-card.tsx`.

- `Library`, `Public Library`, `Public Profile`, and current public subject listing pages should reuse this shared card layout.
- Shared note cards render backend-provided `contentPreview` plus `summaryPreview`, with a frontend fallback of `No summary available yet.` when the note has no generated Study Pack summary.

### Backend (Spring Boot)

Responsibilities:

- validate input and enforce limits
- orchestrate OCR + LLM generation
- persist note-authored fields and generated Study Pack fields
- persist review sessions and performance
- enforce plan usage limits and premium gates
- support sharing/remix operations
- avoid logging raw images and full extracted text

### Local Infrastructure

`docker-compose.yml` can provide local PostgreSQL 16 and optional backend container.

Typical datasource env vars:

- `DB_HOST=localhost`
- `DB_PORT=5432`
- `DB_NAME=study_snap`
- `DB_USER=ss_user`
- `DB_PASSWORD=ss#20260305`

## Note-First Domain Model

NoteLib uses a note-first model.

- Note is the primary entity.
- Study Pack is the generated enhancement state of a Note.
- A Note can be:
  - `Draft` (user-authored content only)
  - `Study Pack Ready` (generated content exists)
- A Note also has visibility:
  - `PRIVATE`
  - `PUBLIC`

Versioning model:

- Generation does not overwrite existing generated content.
- Users create a new version by copying a Note and generating from the copy.
- Copy creates a new Note row with copied user-authored fields only:
  - copied: `title`, `subject`, `tags`, `content`
  - not copied: `summary`, `key concepts`, `quizzes`, performance history, quiz sessions

## High-Level Data Ownership

- `notes` stores user-authored fields (`title`, `subject`, `content`, `tags`, ownership metadata, state, visibility)
- `notes.subject` remains the persisted source of truth for subject values in v0.4.0
- generated fields (`summary`, `key_concepts`, `quiz`) are linked to the same Note
- review sessions (Quick Review, Challenge, Adaptive) link to Note-owned generated quiz context via `noteId`
- share links reference generated Study Pack view data
- copy creates a new Draft Note identity with user-authored fields only

## Public Author Identity

- `users.display_name` is the persisted public author field.
- Public note responses should include:
  - `authorDisplayName`
  - `isOfficialAuthor`
  - `isCurrentUser`
  - `contentPreview`
  - `summaryPreview`
- Public profile responses should include:
  - `displayName`
  - `profileType`
  - `isOfficial`
  - public note `contentPreview`
  - public note `summaryPreview`
  - `publicNotesCount`
  - `totalCopies`
  - `publicNotes[]` with `noteId`, `title`, `subject`, `tags`, `copyCount`, and `slug`
- Author resolution rules:
  - official account email -> `NoteLib`
  - else `display_name` when present
  - else `first_name`
- Official badge state is derived on the backend from the configured official account email plus eligible admin accounts, not from frontend heuristics.
- Reserved display-name guardrails are enforced in backend profile/signup flows before `users.display_name` is saved.

## Profile-Based UX Layer

Profile Type is a presentation and workflow layer, not a data-ownership layer.

All profile types use the same shared entities and tables:

- `User`
- `Note`
- `StudyPack`
- `Quiz`
- `QuizSession`
- `WeakConcept`
- `Activity`
- `Usage`

Profile Type only affects:

- dashboard layout and section order
- CTA behavior
- labels and wording
- default tab after generation
- recommendation emphasis

Profile Type does not affect:

- note ownership
- Study Pack persistence
- quiz-session persistence
- activity tracking
- usage tracking
- generation orchestration

## Backend Modules

### Controllers

- `NoteController` (current/future surface)
  - create/update note
  - generate Study Pack from note (`POST /notes/{id}/generate`)
  - note-scoped quiz entry/performance APIs (`/notes/{id}/quick-review/*`, `/notes/{id}/challenge-quiz/*`, `/notes/{id}/adaptive-practice/start`)
  - copy note
  - update visibility (`PUBLIC`/`PRIVATE`)
  - list Library notes
  - list Public Library notes
  - get public note detail
- `StudyPackController` (legacy/compatibility surface)
  - text/image generation endpoints used by OCR-first and legacy flows
  - OCR confirmation endpoint
  - legacy Study Pack read/update/delete/share endpoints
- `ShareController`
  - create share token
  - resolve shared content
- `HealthController` (optional)
- auth controllers (`/api/auth/*`)

### Services

- `StudyPackService` orchestrator
  - validate -> OCR (if image) -> normalize -> LLM -> validate output -> persist -> return
- `NoteService`
  - note CRUD
  - copy behavior
  - visibility behavior
  - state transitions (`Draft` -> `Study Pack Ready`)
- `OcrService`
- `LlmStudyPackService`
- `UsageLimitService`
- `ShareService`
- `QuickReviewService`
- `ChallengeQuizService`
- `AdaptivePracticeService`

### Persistence

- `NoteRepository`
- `StudyPackRepository` (transitional naming where still present)
- `ShareLinkRepository`
- optional `StudyPackDraftRepository` (OCR text confirmation flow)
- quiz session repositories
- user/subscription repositories

### Billing Architecture

- `BillingController` exposes provider-agnostic billing endpoints:
  - `POST /api/billing/checkout/premium`
  - `POST /api/billing/webhook`
  - `GET /api/billing/usage`
- `MeController` exposes the authenticated plan summary endpoint:
  - `GET /api/me/plan`
- `GET /api/me/plan` is the single frontend-facing source of truth for:
  - plan type
  - monthly limits for Study Packs, Challenge Quiz, Adaptive Practice, and OCR
  - current monthly usage counters
  - remaining usage counters
  - Premium feature flags such as Adaptive Practice and Difficulty Selection
- Usage periods are enforced from `BillingUsagePeriodService`:
  - Free users anchor monthly cycles to `users.created_at`
  - Premium users use the active subscription `startAt/endAt` billing window
  - `user_usage.period_start` and `user_usage.period_end` are the persisted cycle boundaries used for quota checks
- `BillingService` is the provider abstraction used by the controller.
- Active provider is resolved by configuration (`studysnap.billing.provider`).
- Current active provider: `PAYMONGO`.
- Premium checkout supports billing cycle selection:
  - `MONTHLY` -> configured `PAYMONGO_MONTHLY_PLAN_ID`
  - `YEARLY` -> configured `PAYMONGO_YEARLY_PLAN_ID`
- Subscription state changes are webhook-driven (source of truth):
  - `subscription.activated`
  - `subscription.invoice.paid`
  - `subscription.invoice.payment_failed`
  - `subscription.past_due`
  - `subscription.unpaid`
  - `subscription.updated`
- Provider service maps external events to internal domain services only:
  - `SubscriptionService` (activate/downgrade + provider IDs)
  - `PaymentTransactionService` (transaction recording + idempotency by provider reference)
- Webhook idempotency:
  - incoming provider events are persisted to `webhook_events`
  - duplicate `(provider, event_id)` deliveries are acknowledged and skipped
- Safety jobs:
  - `SubscriptionExpiryJob` downgrades expired active Premium subscriptions
  - `BillingUsageResetJob` ensures usage records exist for current period windows

## Generation Pipeline

Recommended flow:

1. validate note ownership and input size
2. if image input: OCR detect/extract
3. if OCR low confidence: return confirmation draft payload
4. normalize note text
5. build LLM prompt
6. call LLM
7. parse and schema-validate JSON
8. run one repair pass on validation failure
9. persist validated generated output linked to Note
10. set Note state to `Study Pack Ready`
11. return generated payload

Default post-generation presentation stays on the unified note detail route:

- `STUDENT` -> open `tab=summary`
- `BOARD_EXAM` -> open `tab=quiz`
- `TEACHER` -> open `tab=quiz`

## API Endpoints (Current and Near-Future)

Notes:

- `POST /api/notes` (create note draft)
- `PUT /api/notes/{id}` (update note content/metadata)
- `GET /api/notes/{id}` (unified note detail payload: note content + generated fields + quiz availability flags)
- `POST /api/notes/{id}/generate` (generate Study Pack for this note)
- `POST /api/notes/{id}/copy` (make a copy)
- `POST /api/notes/{id}/visibility` (set `PUBLIC` or `PRIVATE`)
- `POST /api/notes/{id}/quick-review/start`
- `GET /api/notes/{id}/quick-review/in-progress`
- `GET /api/notes/{id}/quick-review/recent?limit={n}`
- `GET /api/notes/{id}/quick-review/performance-summary`
- `POST /api/notes/{id}/quick-review/study-tip`
- `POST /api/notes/{id}/challenge-quiz/start`
- `GET /api/notes/{id}/challenge-quiz/in-progress`
- `GET /api/notes/{id}/challenge-quiz/recent?limit={n}`
- `GET /api/notes/{id}/challenge-quiz/performance-summary`
- `POST /api/notes/{id}/adaptive-practice/start`
- `DELETE /api/notes/{id}`
- `GET /api/notes` (Library list)
- `GET /api/notes/public` (Public Library list)
- `GET /api/notes/public/{id}` (public read-only note detail)
- `GET /api/public/profile/{userId}` (public profile; returns private-profile state to non-owners when visibility is off)
- `PUT /api/users/profile/public-visibility` (owner toggles public profile visibility)

Legacy/Compatibility Study Pack APIs:

- `POST /api/study-packs` (legacy text/image generation + OCR upload flow)
- `POST /api/study-packs/confirm-text` (legacy OCR confirmation flow)
- `GET /api/study-packs/{id}`
- `GET /api/study-packs?limit={n}&cursor={token}`
- `DELETE /api/study-packs/{id}`
- `POST /api/quick-review/start` and `/api/*/study-packs/{studyPackId}/...` quiz entry endpoints are deprecated in favor of note-scoped endpoints above.

Share:

- `POST /api/study-packs/{id}/share`
- `GET /api/share/{token}`

Auth and onboarding:

- `POST /api/auth/signup`
- `POST /api/auth/login`
- `POST /api/auth/refresh`
- `POST /api/auth/logout`
- `GET /api/auth/me`
- `POST /api/auth/onboarding`
- `POST /api/auth/onboarding/profile-type`
- `POST /api/auth/resend-verification`
- `GET /api/auth/verify-email?token=...`
- `PUT /api/users/profile`

Session and auth-route recovery:

- stale auth state must be cleared before redirecting the browser to `/login` after a `401` / expired session
- protected-route redirects should encode the interrupted destination in `?redirect=...`
- successful login should explicitly navigate with this precedence:
  - verification / onboarding route when required
  - `redirect` query destination for protected-route access and session-expired recovery
  - `/dashboard` fallback
- manual login from public routes should resolve to `/dashboard`
- auth routes (`/auth`, `/login`, `/signup`) should redirect authenticated users away immediately
- the authenticated app shell must not render on auth routes while the route transition is still pending

Onboarding architecture:

- frontend onboarding state is initialized from `GET /api/auth/me`
- `POST /api/auth/onboarding` persists:
  - `profileType`
  - `engagementMode`
  - reminder preferences
  - conditional `examDate`
- `examDate` is required only when `profileType = BOARD_EXAM`
- existing learning-style and reminder onboarding steps are reused; Profile Type is inserted before them
- completion writes `users.onboarding_completed_at`

Profile update architecture:

- `PUT /api/users/profile` updates identity fields only:
  - `firstName`
  - `lastName`
  - `email`
- `profileType` remains a separate write action from the existing profile-type endpoint
- preference writes such as `engagementMode` and study reminders remain under settings/preferences APIs
- the user aggregate stores:
  - `email`
  - `pendingEmail`
  - `emailVerifiedAt`
  - `firstName`
  - `lastName`
  - `profileType`
  - `engagementMode`
  - reminder-toggle fields
  - `examDate`
- email-change verification reuses the existing verification-token flow
- when a pending email is verified:
  - `users.email` is updated from `users.pending_email`
  - `users.pending_email` is cleared
  - `users.email_verified_at` is refreshed
- welcome-email logic must remain one-time for first verification and must not re-fire for pending-email verification

Dashboard personalization architecture:

- dashboard personalization is a presentation-layer concern driven by `users.profile_type`
- the same shared core entities back every dashboard variant:
  - `Note`
  - `Study Pack`
  - `QuizSession`
  - `WeakConcept`
  - `Activity`
  - `Usage`
- switching `profileType` must not migrate, delete, or rewrite notes, study packs, quiz sessions, weak concepts, or usage rows
- `STUDENT`, `BOARD_EXAM`, and `TEACHER` dashboards reuse the same backend dashboard overview and note list APIs
- profile-specific differences are limited to:
  - section order
  - CTA destination
  - labels and wording
  - recommendation emphasis
- teacher quiz creation still follows the same flow:
  - material -> note -> study pack -> quiz
- board-exam countdown is derived from `users.exam_date`; it does not introduce a separate exam entity
- mode-based routing is frontend-only and must stay note-centric:
  - `/notes/new?mode=quiz`
  - `/notes/new?source=paste`
  - `/notes/new?source=upload`
- mode/source query params may change editor focus and the default note-detail tab after generation, but they must not change persistence shape or create profile-specific entities
- note detail remains a unified route and uses `tab=summary|quiz` as a presentation switch on the same note data

## API Security Model

- non-public endpoints require Bearer access token
- access token is short-lived JWT
- refresh token is hashed in DB and rotated on refresh
- protected-route 401 handling is centralized in frontend `lib/api.ts`
- unverified users are authenticated but generation-blocked
- unverified users are also blocked from OCR upload/extract endpoints
- generation-block response contract:
  - status: `403`
  - `code=EMAIL_VERIFICATION_REQUIRED`
  - `action=RESEND_VERIFICATION`

## Library Architecture

Library is the owner workspace for Draft and Study Pack Ready notes.
Public Library is the discovery surface for notes where `visibility=PUBLIC`.
Profile is the identity surface for first name, last name, display name, email, and profile type.
Settings is the preferences surface for theme, notifications, study preferences, account settings, and subscription/billing.
Public Profile is the public showcase surface and owns share/edit-profile entry plus owner-only public-visibility controls.

Required backend behavior:

- list owned notes for Library
- list all public notes for Public Library, including the viewer's own public notes
- support `/public/library/{subject}` subject listing pages from the same public-note data set
- support `/public/profile/{userId}` creator pages from persisted `users` plus public-note aggregates
- include metadata for scanning/filtering (`title`, `subject`, `tags`, content preview, timestamps, state)
- include author-source metadata for Public Library card labeling (`By You`, `By NoteLib`, `By {displayName}`) plus `Official` badge state
- support public read-only note detail payload for copy flow
- persist `users.public_profile_visible` as the owner-controlled switch for public-profile access
- expose `GET /api/public/profile/{userId}` for public profile header stats and public-note listings
- allow the owner to read `/api/public/profile/{userId}` even when `users.public_profile_visible = false`
- return `403` / `PUBLIC_PROFILE_PRIVATE` for non-owners when `users.public_profile_visible = false`
- expose `PUT /api/users/profile/public-visibility` so the owner can toggle public-profile visibility from the Public Profile page

Filtering model:

- search + subject + tag filtering remains frontend-side for loaded note items
- distinct subject suggestions are backend-driven from persisted `notes.subject` values
- `GET /api/subjects?scope=mine` returns distinct subjects from the authenticated user's notes
- `GET /api/subjects?scope=public` returns distinct subjects from public notes only
- the current system intentionally does not use a normalized `subjects` table yet

## Share and Public-Copy Architecture

- shared links use unguessable tokens
- public page is read-only
- shared payload omits private/raw input where not needed
- copy duplicates into the current user's Library without LLM generation
- title collisions are auto-resolved with `(Copy)`, `(Copy 2)`, ...

## Quiz Architecture

Quick Review:

- uses stored Study Pack quiz
- retry incorrect questions once
- persists session and confidence feedback
- session ownership is note-scoped (`noteId`)

Challenge Quiz (Premium):

- generated from summary + key concepts only
- timed and continuously persisted
- resumes in-progress session if present (no duplicate generation call)
- session ownership is note-scoped (`noteId`)

Adaptive Practice (Premium):

- generated from summary + key concepts + weak concepts only
- resumes in-progress session if present (no duplicate generation call)
- session ownership is note-scoped (`noteId`)

Usage tracking:

- Study Pack generation quota and quiz-mode quotas are independent

## Demo Guardrails

Demo mode (`/demo` or `?demo=true`) must:

- use static sample note input
- simulate latency only
- return placeholder payload
- skip database writes
- skip usage counting
- skip OpenAI calls

## OCR Strategy

Hybrid OCR flow:

1. quick text detection
2. full OCR only if text is detected
3. insert extracted text into Note content for user review/edit
4. generate only when user explicitly chooses `Generate Study Pack`

Normalization rules:

- trim whitespace
- collapse repeated spaces
- reduce broken line wraps
- preserve paragraph structure

## Error Handling

- all API errors include `requestId`
- backend returns `X-Request-Id`
- clients can surface request id for support/debugging

Example:

```json
{
  "requestId": "string",
  "error": {
    "code": "string",
    "message": "user-friendly string",
    "details": "optional"
  }
}
```

## Data Model Reference

See `docs/architecture/DATA_MODEL.md` for table/entity details.
