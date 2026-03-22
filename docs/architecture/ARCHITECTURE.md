# ARCHITECTURE.md - NoteLib

This document describes the NoteLib system architecture and how backend services connect to the web frontend.

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
- `/library` My Library (owned notes)
- `/library/public` Public Library (public notes from other users)
- `/notes/{id}` Note Detail (owner view; unified Note + Study Pack view)
- `/public/notes/{id}` public read-only note detail
- `/settings` plan/billing and account controls
- `/profile` account profile
- `/p/{token}` public shared Study Pack

Frontend calls backend via `NEXT_PUBLIC_API_BASE_URL`.

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
- generated fields (`summary`, `key_concepts`, `quiz`) are linked to the same Note
- review sessions (Quick Review, Challenge, Adaptive) link to Note-owned generated quiz context via `noteId`
- share links reference generated Study Pack view data
- copy creates a new Draft Note identity with user-authored fields only

## Backend Modules

### Controllers

- `NoteController` (current/future surface)
  - create/update note
  - generate Study Pack from note (`POST /notes/{id}/generate`)
  - note-scoped quiz entry/performance APIs (`/notes/{id}/quick-review/*`, `/notes/{id}/challenge-quiz/*`, `/notes/{id}/adaptive-practice/start`)
  - copy note
  - update visibility (`PUBLIC`/`PRIVATE`)
  - list My Library notes
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
- `GET /api/notes` (My Library list)
- `GET /api/notes/public` (Public Library list)
- `GET /api/notes/public/{id}` (public read-only note detail)

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
- `POST /api/auth/resend-verification`
- `GET /api/auth/verify-email?token=...`

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

My Library is the owner workspace for Draft and Study Pack Ready notes.
Public Library is the discovery surface for notes where `visibility=PUBLIC`.

Required backend behavior:

- list owned notes for My Library
- list public notes excluding owner notes for Public Library
- include metadata for scanning/filtering (`title`, `subject`, `tags`, content preview, timestamps, state)
- support public read-only note detail payload for copy flow

Filtering model:

- search + subject + tag filtering remains frontend-side for loaded items

## Share and Public-Copy Architecture

- shared links use unguessable tokens
- public page is read-only
- shared payload omits private/raw input where not needed
- copy duplicates into current user My Library without LLM generation
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
