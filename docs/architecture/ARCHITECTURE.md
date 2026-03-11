# ARCHITECTURE.md — Study Snap

This document describes the Study Snap system architecture and how the backend connects to the web frontend.

Study Snap core workflow:

**Notes (text or photo) → Study Pack (summary + key concepts + practice quiz) → save / revisit / optional sharing**

## Goals (MVP)
- convert notes into structured study materials
- support pasted text and image upload (OCR)
- provide low-confidence OCR fallback where the user can edit extracted text
- persist generated outputs so users can revisit them later
- enable shareable Study Pack links
- prepare for authenticated ownership and future plans

Non-goals for current MVP:
- full exam simulation with grading analytics
- complex classroom roles
- gamification, streaks, leaderboards
- heavy dashboards

## System overview

### Frontend (Next.js)
Routes:
- `/` landing
- `/study` create Study Pack from notes
- `/share/[token]` public shared Study Pack page
- `/dashboard` Study Library destination

Frontend calls backend via `NEXT_PUBLIC_API_BASE_URL`.

### Backend (Spring Boot)
Responsibilities:
- orchestrate OCR + LLM
- validate inputs and enforce limits
- delete images after OCR
- store Study Pack output and share tokens
- support authenticated ownership and subscription-aware behavior
- run under servlet context path `/api`

### Local infrastructure
`docker-compose.yml` can provide local PostgreSQL 16 and optionally the backend container.

Typical datasource env vars:
- `DB_HOST=localhost`
- `DB_PORT=5432`
- `DB_NAME=study_snap`
- `DB_USER=ss_user`
- `DB_PASSWORD=ss#20260305`

Typical local runs:
- `docker compose up -d postgres`
- start backend; Flyway applies migrations on startup

## Backend modules

### Controllers
- `StudyPackController` (legacy naming, can evolve later)
  - creates Study Packs from text or image
  - handles OCR-confirmation resubmits
- `ShareController`
  - creates share links
  - serves public shared content
- `HealthController` (optional)
- future auth controllers

### Services
- `StudyPackService` / future `StudyPackService`
  - validate → OCR (if image) → normalize → LLM → validate output → persist → return
- `OcrService`
- `LlmStudyPackService`
- `UsageLimitService`
- `ShareService`
- future `UserAccountService`
- future `SubscriptionService`

### Persistence
- `StudyPackRepository` / future `StudyPackRepository`
- `ShareLinkRepository`
- optional `StudyPackDraftRepository`
- future `UserRepository`
- future `SubscriptionRepository`

## API endpoints

Note:
- Real product endpoints are designed for authenticated usage.
- Current implementation prepares ownership logic via user context resolution and can be wired to full auth middleware next.
- Study Pack generation is blocked until account email is verified.
- Security uses Spring Security + JWT access tokens + rotating refresh tokens.

### Create StudyPack from text
`POST /api/study-packs`
Content-Type: `application/json`

Request example:
```json
{ "notesText": "..." }
```

### Create StudyPack from image
`POST /api/study-packs`
Content-Type: `multipart/form-data`

Form fields:
- `image`: file (`jpeg/png/webp`)
- `subject`: optional string

Low-confidence OCR response:
```json
{
  "status": "needs_text_confirmation",
  "id": "draftId",
  "extractedText": "string",
  "meta": { "ocrConfidence": 0.72 }
}
```

### Confirm extracted text
`POST /api/study-packs/confirm-text`

Request example:
```json
{ "draftId": "string", "notesText": "user-edited text" }
```

### Auth onboarding and verification (current foundation)
- `POST /api/auth/signup`
- `POST /api/auth/login`
- `POST /api/auth/refresh`
- `POST /api/auth/logout`
- `GET /api/auth/me`
- `POST /api/auth/onboarding/profile-type`
- `POST /api/auth/verify-email/request` (placeholder send flow)
- `POST /api/auth/verify-email/confirm` (placeholder confirm flow)

Token-backed verification is planned as a future upgrade.

## API security model

- all non-public endpoints require Bearer access token
- access token is short-lived JWT
- refresh token is stored hashed in database and rotated on refresh
- keep-signed-in extends refresh token lifetime
- role model includes `USER` and `ADMIN`
- brute-force protection includes login rate limiting + lockout policy

### Get saved StudyPack
`GET /api/study-packs/{id}`

### Create share link
`POST /api/study-packs/{id}/share`

### Resolve share link
`GET /api/share/{token}`

Response should return sanitized public data only.

## Error handling convention

- every API error response includes `requestId`
- backend also returns `X-Request-Id`
- clients may surface the id to users for support/debugging

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

## study pack generation output contract

The backend should require the LLM to output strict JSON with:
- `title`
- `summary`
- `keyConcepts`
- `quiz`

The stricter contract used going forward is documented in `docs/ai/PROMPTS.md`:
- exactly four choices per quiz item
- `answerIndex` between 0 and 3
- schema validation + one repair pass on failure

## LLM generation pipeline

Recommended flow:
1. validate input
2. if image: OCR → extracted text
3. normalize notes
4. build prompt
5. call LLM
6. parse JSON
7. validate JSON schema
8. if validation fails, run one repair pass
9. persist only validated output
10. return response

## Study Library architecture

The Study Library is the persistence and retrieval layer for generated Study Packs.

Each generated Study Pack should be retrievable for future study sessions.

Required backend support:
- list saved Study Packs
- fetch Study Pack by id
- delete Study Pack

Recommended list metadata:
- id
- title
- summary preview
- createdAt
- quiz question count
- tags when available

Future extensions:
- rename
- tags
- folders / collections
- reviewed status

## Activity Tracking

Study Snap records lightweight user activity events to support future study workflow features.

Purpose:
- capture key study actions without changing current product behavior
- provide a reliable foundation for future streaks, continue-studying suggestions, Quick Review flows, and usage analytics

Persistence:
- table: `user_activity_events`
- fields:
  - `id`
  - `user_id`
  - `study_pack_id` (nullable)
  - `activity_type`
  - `created_at`

Activity types:
- `CREATED_STUDY_PACK`
- `OPENED_STUDY_PACK`
- `STARTED_QUICK_REVIEW`
- `COMPLETED_QUICK_REVIEW`

Recording model:
- feature code calls a centralized `ActivityTrackingService`
- the service handles event persistence details through `ActivityEventRepository`
- activity write failures are logged and do not interrupt main Study Pack requests

## Tags architecture

Study Packs may support tags for organization and filtering.

Purpose:
- subject/topic categorization
- dashboard filtering
- future analytics and topic grouping

Recommended MVP-friendly implementation:
- store tags as a simple array field on the Study Pack record

Example:
```json
["Biology", "Photosynthesis"]
```

## User accounts architecture direction

User accounts should arrive before a fully authenticated Study Library implementation.

Reason:
- ownership of saved Study Packs becomes explicit
- plan-based usage limits become easier to enforce
- premium feature gating becomes easier
- future subscription analytics become easier

High-level separation:
- `users`: identity + core profile
- `subscriptions`: plan history and billing state
- `study_packs`: user-owned generated content
- future tables later: usage tracking, account links, etc.

## Share architecture

Shared Study Packs use unguessable tokens.

Rules:
- public endpoint exposes generated Study Pack content only
- raw uploaded image must not be exposed
- raw notes text should be omitted by default
- expiration is optional later
- view counts are optional

## Demo architecture

Demo mode is frontend-driven and does not use the real backend generation flow.

Demo flow:
Landing → `/study?demo=true` → simulated generation → static Study Pack

Real flow:
Landing → `/study` → `POST /api/study-packs` → OCR/LLM → database → response

## Hybrid OCR strategy

Study Snap uses a hybrid OCR strategy to reduce cost and avoid unnecessary OCR calls.

### Processing pipeline
Image upload
↓
Image validation
↓
Quick text detection (`TEXT_DETECTION`)
↓
If text detected:
run `DOCUMENT_TEXT_DETECTION`
↓
Extract text
↓
Clean extracted text
↓
Send text to LLM generation

### Benefits
- reduces OCR costs
- prevents processing useless images
- improves reliability of note extraction

### Image guardrails
- maximum image size limit
- allowed image formats
- text detection before OCR extraction

## OCR text normalization

Before sending OCR text to the LLM, normalize it:
- trim whitespace
- collapse repeated spaces
- replace single line breaks with spaces
- preserve paragraph breaks
- remove OCR artifacts where possible

Pipeline:
Image → OCR → text normalization → LLM prompt

## Cost control strategy

### Tiered model usage
- cheap model: OCR cleanup / formatting (optional)
- standard model: summary + key concepts + practice quiz
- premium higher-quality model later: mock exam generation, analytics

### Configuration knobs
- `LLM_MODEL_FREE`
- `LLM_MODEL_PREMIUM`
- `QUIZ_QUESTIONS_FREE`
- `QUIZ_QUESTIONS_PREMIUM`
- `MAX_NOTES_CHARS_FREE`
- `QUICK_REVIEW_STUDY_TIP_ENABLED`
- `QUICK_REVIEW_STUDY_TIP_MIN_INCORRECT_COUNT`
- `QUICK_REVIEW_STUDY_TIP_MAX_QUESTIONS`

### Quick Review AI Study Tip cost controls

Quick Review results can optionally request an AI-generated Study Tip based on missed questions.

Backend guardrails:
- feature flag can fully disable tip generation (`studysnap.quick-review.study-tip.enabled`)
- tip generation runs only when incorrect answer count reaches the configured threshold
- only incorrect questions are sent to the LLM
- incorrect question input is capped by configured max-question count
- failures are non-blocking; Quick Review completion/results always continue

### Initial model mapping
Demo:
- `gpt-4.1-mini`

Free:
- `gpt-4.1-mini`

Premium:
- default may remain `gpt-4.1-mini` at first
- premium-only features may upgrade later

### Token limits
- enforce max characters for free tier notes
- truncate or reject overly long inputs
- consider chunking long notes later

## Demo guardrails

Demo mode stays frontend-driven with pre-coded request/response behavior and does not call real generation APIs.

## Privacy & security

- uploaded images must be deleted immediately after OCR
- avoid logging raw images or full extracted text
- store only what is needed
- share links must use unguessable tokens
- public share endpoint must not expose private user data

## Data model reference

See `docs/architecture/DATA_MODEL.md` for the consolidated table/entity view.

## Core Domain Models

Study Snap revolves around several core domain models.

User
Represents an account using the platform.

StudyPack
Generated learning material derived from notes.

QuickReviewSession
Represents a user's attempt to review a Study Pack quiz.

ActivityEvent
Tracks learning activity such as starting or completing reviews.
