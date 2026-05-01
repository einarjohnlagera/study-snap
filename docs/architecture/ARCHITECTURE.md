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
  - `Generating` (Study Pack generation queued/running)
  - `Failed` (last generation attempt failed without persisted Study Pack output)
  - `Study Pack Ready` (generated content exists)
- A Note also has visibility:
  - `PRIVATE`
  - `PUBLIC`

Versioning model:

- Generation does not overwrite existing generated content.
- Users create a new version by copying a Note and generating from the copy.
- Copy creates a new Note row with copied user-authored fields only:
  - copied: `title`, `courseProgram`, `subject`, `tags`, `content`
  - not copied: `summary`, `key concepts`, `quizzes`, performance history, quiz sessions

## High-Level Data Ownership

- `notes` stores user-authored fields (`title`, `courseProgram`, `subject`, `content`, `tags`, ownership metadata, state, visibility)
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
  - `totalShares` when analytics data exists
  - `totalViews` when analytics data exists
  - `publicNotes[]` with `noteId`, `title`, `subject`, `tags`, `copyCount`, optional `shareCount`, optional `viewCount`, and `slug`
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

- `BillingController` exposes pricing, usage, and history endpoints:
  - `GET /api/billing/pricing`
  - `GET /api/billing/history`
  - `GET /api/billing/usage`
- `PaymentController` exposes checkout creation:
  - `POST /api/payments/create`
- `XenditWebhookController` exposes the public payment callback endpoint:
  - `POST /api/webhooks/xendit`
- `MeController` exposes the authenticated plan summary endpoint:
  - `GET /api/me/plan`
- `GET /api/me/plan` is the single frontend-facing source of truth for:
  - plan type
  - monthly limits for Study Packs, Challenge Quiz, Adaptive Practice, and OCR
  - current monthly usage counters
  - remaining usage counters
  - Pro-only feature flags such as Adaptive Practice and Difficulty Selection
- Usage periods are enforced from `BillingUsagePeriodService`:
  - Free users anchor monthly cycles to `users.created_at`
  - paid users use the active subscription billing window
  - `user_usage.period_start` and `user_usage.period_end` are the persisted cycle boundaries used for quota checks
- `PaymentService` owns hosted checkout creation and Xendit webhook processing.
- Current active provider: `XENDIT`.
- Checkout creation flow:
  - validate the selected paid plan and current eligibility
  - create a Xendit invoice at `/v2/invoices`
  - persist a pending `payment_transactions` row with provider reference `external_id`
  - return the hosted `invoice_url` to frontend
- Webhook state changes are the source of truth for paid-plan activation:
  - `PAID`
  - `FAILED`
  - `EXPIRED`
- Payment services map external events to internal domain services only:
  - `SubscriptionService` (activate/downgrade + provider IDs)
  - `PaymentTransactionService` (transaction recording + provider-reference lookup)
- Webhook idempotency:
  - incoming provider events are persisted to `webhook_events`
  - duplicate `(provider, event_id)` deliveries are acknowledged and skipped
- Safety jobs:
  - `SubscriptionExpiryJob` downgrades expired active paid subscriptions
  - `BillingUsageResetJob` ensures usage records exist for current period windows

## Generation Pipeline

Recommended flow:

1. validate note ownership and input size
2. set note state to `Generating`
3. return Note Detail payload so the frontend can redirect immediately
4. run generation in the background
5. if image input: OCR detect/extract
6. if OCR low confidence: return confirmation draft payload
7. normalize note text
8. build LLM prompt
9. call LLM
10. parse and schema-validate JSON
11. run one repair pass on validation failure
12. persist validated generated output linked to Note
13. set Note state to `Study Pack Ready`
14. on failure, set Note state to `Failed` and do not increment usage

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
- returns immediately after queueing note-owned generation; Note Detail observes `Generating` / `Study Pack Ready` / `Failed`
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
- protected-route redirects while logged out may also include a neutral `reason=auth_required`
- manual logout should redirect to `/login` with a neutral logout reason rather than reusing the session-expired reason
- explicit logout should set a logout-intent guard before network requests so late `401` responses from in-flight protected calls cannot overwrite the logout reason with `session_expired`
- successful login should explicitly navigate with this precedence:
  - verification / onboarding route when required
  - `redirect` query destination for protected-route access and session-expired recovery
  - `/dashboard` fallback
- manual login from public routes should resolve to `/dashboard`
- login-page messaging must be query-driven:
  - `reason=session_expired` -> expired-session warning
  - `reason=logged_out` -> neutral logout message
  - `reason=auth_required` or no reason -> neutral login prompt
- auth routes (`/auth`, `/login`, `/signup`) should redirect authenticated users away immediately
- the authenticated app shell must not render on auth routes while the route transition is still pending

Onboarding architecture:

- frontend onboarding state is initialized from `GET /api/auth/me`
- `POST /api/auth/onboarding` persists:
  - `profileType`
  - `learnerLevel`
  - required `courseProgram`
  - optional `bio`
  - `engagementMode`
  - reminder preferences
  - conditional `examDate`
- `examDate` is required only when `profileType = BOARD_EXAM`
- onboarding flow order is:
  - `Profile Type`
  - `Learning Profile`
  - `Learning Style`
  - `Study Reminder Frequency`
  - conditional `Exam Date`
- completion writes `users.onboarding_completed_at`

Profile update architecture:

- `PUT /api/users/profile` updates profile-owned identity and learning-profile fields:
  - `firstName`
  - `lastName`
  - `displayName`
  - `bio`
  - `learnerLevel`
  - `courseProgram`
  - `email`
- `profileType` remains a separate write action from the existing profile-type endpoint
- preference writes such as `engagementMode` and study reminders remain under settings/preferences APIs
- frontend `Save Learning Profile` validation requires both `learnerLevel` and `courseProgram`, while note-level `courseProgram` remains optional
- the user aggregate stores:
  - `email`
  - `pendingEmail`
  - `emailVerifiedAt`
  - `firstName`
  - `lastName`
  - `displayName`
  - `bio`
  - `learnerLevel`
  - `courseProgram`
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
- note detail remains a unified route and uses `tab=summary|key-concepts|quiz|full-notes` as a presentation switch on the same note data

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
Profile is the identity surface for first name, last name, display name, email, learning profile, and profile type.
Settings is the preferences surface for theme, notifications, study preferences, account settings, and subscription/billing.
Public Profile is the public showcase surface and owns share/edit-profile entry plus owner-only public-visibility controls.

Required backend behavior:

- list owned notes for Library
- list all public notes for Public Library, including the viewer's own public notes
- support `/public/library/{subject}` subject listing pages from the same public-note data set
- support `/public/profile/{userId}` creator pages from persisted `users` plus public-note aggregates
- include metadata for scanning/filtering (`title`, `subject`, `tags`, content preview, timestamps, state)
- include author-source metadata for Public Library card labeling (`By You`, `By NoteLib`, `By {displayName}`) plus `Official` badge state
- include optional learner-profile metadata on public profile payloads:
  - `bio`
  - `learnerLevel`
  - `courseProgram`
- support public read-only note detail payload for copy flow
- persist `users.public_profile_visible` as the owner-controlled switch for public-profile access
- expose `GET /api/public/profile/{userId}` for public profile header stats and public-note listings
- allow the owner to read `/api/public/profile/{userId}` even when `users.public_profile_visible = false`
- return `403` / `PUBLIC_PROFILE_PRIVATE` for non-owners when `users.public_profile_visible = false`
- expose `PUT /api/users/profile/public-visibility` so the owner can toggle public-profile visibility from the Public Profile page

Study Pack generation architecture:

- generation remains note-first and uses the same persisted note content
- note-owned generation is asynchronous: Note Detail observes status with light polling rather than keeping the user on the editor.
- usage increments only after a Study Pack row is successfully persisted.
- failed generation leaves the note saved, marks status `Failed`, and allows retry without refund/usage side effects.
- backend generation context may carry:
  - `learnerLevel`
  - note `courseProgram` with user-profile fallback
  - note `subject`
  - note `tags`
- current UI and prompt behavior remain unchanged; this context is preparation for smarter quiz generation in later releases

Filtering model:

- search + metadata filtering remains frontend-side for loaded note items
- Private Library filtering uses note `courseProgram`, `subject`, `tags`, `studyPackStatus`, and `visibility`
- Public Library filtering uses note `courseProgram`, public-owner `learnerLevel` when exposed, `subject`, `tags`, and source grouping (`By You`, `Official`, `Community`)
- distinct subject suggestions are backend-driven from persisted `notes.subject` values
- `GET /api/subjects?scope=mine` returns distinct subjects from the authenticated user's notes
- `GET /api/subjects?scope=public` returns distinct subjects from public notes only
- custom subjects join future autocomplete suggestions after the note is saved
- subject reuse normalizes whitespace and dash formatting, then matches case-insensitively so equivalent saved subjects collapse into one suggestion/filter key when possible
- distinct course/program suggestions are backend-driven from persisted `notes.course_program` values plus the authenticated user's saved `users.course_program`
- `GET /api/course-programs?scope=mine` returns normalized distinct course/program values for the authenticated workspace
- `GET /api/course-programs?scope=public` returns normalized distinct course/program values from public notes only
- custom course/program values join future autocomplete suggestions after the note or profile value is saved
- course/program reuse normalizes whitespace and dash formatting, then matches case-insensitively so equivalent saved values collapse into one suggestion/filter key when possible
- note-level `courseProgram` is stored now so later library filters can use persisted note metadata instead of only the profile default
- library note-list payloads should expose `courseProgram`, `createdAt`, `updatedAt`, and public-owner `learnerLevel` so frontend sorting/filtering does not need separate metadata fetches
- the current system intentionally does not use a normalized `subjects` or `course_programs` table yet

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
- stored quiz items are canonical `question`, `choices`, `correctIndex`, `explanation`, `concept`
- UI letters are presentation-only and are derived from displayed choice order at render time
- displayed choice order must stay deterministic for a given question/session so re-renders do not change correctness

Challenge Quiz:

- generated from summary + key concepts only
- timed and continuously persisted
- resumes in-progress session if present (no duplicate generation call)
- session ownership is note-scoped (`noteId`)
- generation may start from raw LLM `answer` letters, but backend/session persistence must normalize to canonical `correctIndex`
- session state stores selected canonical choice indexes and may normalize legacy answer-text payloads on load

Adaptive Practice (Pro):

- generated from summary + key concepts + weak concepts only
- resumes in-progress session if present (no duplicate generation call)
- session ownership is note-scoped (`noteId`)
- generation may start from raw LLM `answer` letters, but backend/session persistence must normalize to canonical `correctIndex`
- session state stores selected canonical choice indexes and may normalize legacy answer-text payloads on load

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
