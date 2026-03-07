# ARCHITECTURE.md — Study Snap (Web-first MVP)

This document describes the backend architecture for Study Snap and how it connects to the web frontend.

Study Snap core workflow:
Notes (text or photo) → Review Materials (summary + key concepts + practice quiz) → Optional sharing.

---

## Goals (MVP)
- Convert notes into structured review materials
- Support both pasted text and image upload (OCR)
- Provide a low-confidence OCR fallback where the user can edit extracted text
- Persist generated reviews so users can revisit them later
- Enable shareable “Study Pack” links (viral distribution)

Non-goals (MVP):
- Full exam simulation with grading analytics (premium later)
- Complex user accounts and roles
- Gamification, streaks, leaderboards
- Heavy dashboards

---

## System Overview

### Frontend (Next.js)
- `/` Landing
- `/study` Create review from notes
- `/share/[token]` Public shared study pack page
- (Optional) `/dashboard` Placeholder / history

Frontend calls the backend via `NEXT_PUBLIC_API_BASE_URL`.

### Backend (Spring Boot)
- Orchestrates OCR + LLM
- Validates inputs and enforces limits
- Deletes images after OCR
- Stores review output and share tokens
- Runs under servlet context path `/api`

### Local Infrastructure (Docker Compose)
- `docker-compose.yml` provides a local PostgreSQL 16 instance for backend development.
- `docker-compose.yml` can also run the Spring Boot backend container.
- Service name: `postgres`
- Container name: `study-snap-postgres`
- Port mapping: `5432:5432`
- Persistent volume: `study_snap_pgdata`

Backend datasource env vars should match the compose values:
- `DB_HOST=localhost`
- `DB_PORT=5432`
- `DB_NAME=study_snap`
- `DB_USER=ss_user`
- `DB_PASSWORD=ss#20260305`

Run locally:
- `docker compose up -d postgres`
- Then start backend; Flyway will apply migrations on startup.

Run backend + Postgres together:
- `docker compose up -d --build backend`

---

## Backend Modules (Spring Boot)

### Controllers
- `ReviewController`
  - Creates reviews from text or image
  - Handles OCR-confirmation resubmits
- `ShareController`
  - Creates share links for reviews
  - Serves public review content for share pages
- `HealthController` (optional)
  - `/health` or `/actuator/health`

### Services
- `ReviewService` (orchestrator)
  - validate → OCR(if image) → normalize → LLM → validate output → persist → return
- `OcrService`
  - wraps chosen OCR provider
- `LlmReviewService`
  - wraps chosen LLM provider
  - enforces structured JSON response
- `UsageLimitService` (optional for MVP)
  - demo limit: 1 review (anonymous)
  - free limit: 3/day (requires login later)
  - premium limit: 200/month (later)
- `ShareService`
  - create and resolve share tokens

### Persistence
- `ReviewRepository`
- `ShareLinkRepository`
- `ReviewDraftRepository` (optional but recommended for OCR confirmation)

---

## API Endpoints

### Create review (text)
`POST /api/review`
Content-Type: `application/json`

Request:
```json
{ "notesText": "..." }
```

Response (success):
```json
{
  "id": "string",
  "inputType": "text",
  "extractedText": null,
  "title": "string",
  "summary": "string",
  "keyConcepts": ["string"],
  "quiz": [
    { "question": "string", "choices": ["string"], "answer": "string", "explanation": "string" }
  ],
  "meta": { "ocrConfidence": null, "latencyMs": 1234 }
}
```

---

### Create review (image)
`POST /api/review`
Content-Type: `multipart/form-data`

Form fields:
- `image`: file (jpeg/png/webp)
- `subject` (optional): string

Response (success): same as text, `inputType: "image"`.

Response (low confidence OCR):
```json
{
  "status": "needs_text_confirmation",
  "id": "draftId",
  "extractedText": "string",
  "meta": { "ocrConfidence": 0.72 }
}
```

---

### Confirm extracted text (OCR fallback)
`POST /api/review/confirm-text`
Content-Type: `application/json`

Request:
```json
{ "draftId": "string", "notesText": "user-edited text" }
```

Response: Review success response (same structure as above).

---

### Get review (authenticated later)
`GET /api/review/{id}`

---

### Create share link
`POST /api/review/{id}/share`

Response:
```json
{ "token": "string", "shareUrl": "/share/string" }
```

Notes:
- `shareUrl` can be returned as a path; frontend constructs absolute URL.

---

### Resolve share link (public)
`GET /api/share/{token}`

Response:
- Sanitized review data safe for public access (no raw image).
- Recommended: do not include raw notes text by default; include generated content only.

---

## Error Handling Convention

- Every API error response includes `requestId`.
- Backend also returns `X-Request-Id` response header.
- Clients can surface this id to users for support/debugging without exposing internal error details.

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

---

## Data Model (Postgres)

### reviews
- `id` (uuid)
- `owner_user_id` (nullable for demo/anonymous)
- `anon_id` (nullable; cookie-based id for demo)
- `input_type` (TEXT | IMAGE)
- `title` (text)
- `summary` (text)
- `key_concepts` (jsonb array)
- `quiz` (jsonb array)
- `ocr_confidence` (nullable numeric)
- `model_tier` (FREE | PREMIUM)
- `created_at` (timestamp)
- `status` (DONE | NEEDS_CONFIRMATION | FAILED)
- `error_code` (nullable)

Additional usage-tracking fields:
- `model_used` (text)
- `input_tokens` (integer, nullable)
- `output_tokens` (integer, nullable)
- `cached_input_tokens` (integer, nullable)
- `estimated_cost` (numeric, nullable)

### review_drafts (recommended)
Used for OCR low-confidence flows so the UI can edit and resubmit.

- `id` (uuid)
- `owner_user_id` (nullable)
- `anon_id` (nullable)
- `extracted_text` (text)
- `ocr_confidence` (numeric)
- `created_at` (timestamp)
- `expires_at` (timestamp, e.g., +24h)

### share_links
- `token` (string, unique, unguessable)
- `review_id` (uuid)
- `is_public` (boolean)
- `created_at` (timestamp)
- `expires_at` (nullable)
- `view_count` (optional integer)

### usage_daily (optional for MVP)
- `user_id` or `anon_id`
- `date` (yyyy-mm-dd)
- `count` (int)

---

## Review Generation Output Contract (LLM)
The backend should require the LLM to output STRICT JSON with:

- `title` (string)
- `summary` (string)
- `keyConcepts` (string[])
- `quiz` (array of objects):
  - `question` (string)
  - `choices` (string[3..5]) (optional; can support open-ended later)
  - `answer` (string)
  - `explanation` (string)

Backend validates schema and rejects malformed output.

---

## Cost Control Strategy (Required)
To control operational costs:

### Tiered model usage
- Cheap model:
  - OCR cleanup/formatting (optional)
- Standard model (Free):
  - title + summary + key concepts + practice quiz (3–5 questions)
- Higher quality model (Premium, later):
  - mock exam generation (10–20 questions)
  - analytics/topic mastery

Configuration knobs (env):
- `LLM_MODEL_FREE`
- `LLM_MODEL_PREMIUM`
- `QUIZ_QUESTIONS_FREE`
- `QUIZ_QUESTIONS_PREMIUM`
- `MAX_NOTES_CHARS_FREE`

### Initial model mapping

Demo:
- `gpt-4.1-mini`

Free:
- `gpt-4.1-mini`

Premium (future):
- default can remain `gpt-4.1-mini`
- premium-only features may upgrade to a stronger model if justified by user value and cost

### Token limits
- Enforce max characters for free tier notes
- Truncate or reject overly long inputs
- Consider chunking long notes and summarizing first (later optimization)

---

## Privacy & Security
- Uploaded images must be deleted immediately after OCR.
- Avoid logging raw images or full extracted text.
- Store only what is needed; prefer storing generated output over raw notes.
- Share links must use unguessable tokens (e.g., 128-bit random).
- Public share endpoint must not expose private user data.

---

## Anonymous Guardrails

For real `/api/review` calls without authentication:
- apply rate limiting by session cookie and/or IP
- enforce minimum and maximum note length
- optionally apply cooldown between requests

---

## Frontend Integration Notes
- Frontend uses `lib/api.ts` as a single API client.
- `/study` calls:
  - `createReviewFromText(notesText)`
  - `createReviewFromImage(imageFile)`
  - If `needs_text_confirmation`, show extracted text editable and call confirm endpoint.
- `/share/[token]` calls `GET /api/share/{token}`.

---

## Demo Architecture

Demo mode is frontend-driven and does not use the real backend review generation flow.

Demo flow:
Landing → /study?demo=true → simulated generation → static review

Real flow:
Landing → /study → POST /api/review → LLM → database → review response

---

## Future Architecture Extensions (Post-MVP)
- Auth + user accounts
- Paid plans (Stripe)
- Mock exam mode + scoring + analytics
- Topic mastery detection + recommendations
- Caching for repeated inputs
- Export to PDF / Notion / Google Docs

---

## Study Library Architecture

The Study Library is the persistence and retrieval layer for generated study packs.

Each generated review should be retrievable for future study sessions.

Required backend support:
- list reviews
- fetch review by id
- delete review

Recommended response metadata for list view:
- id
- title
- summary preview
- createdAt
- quiz question count

Future extensions:
- rename review
- tags
- folders/collections
- reviewed status

## Tags Architecture

Study packs may support tags for organization and filtering.

Purpose:
- subject/topic categorization
- dashboard filtering
- future analytics and topic grouping

Recommended MVP-friendly implementation:
- store tags as a simple array field on the review/study pack record

Example:
```json
["Biology", "Photosynthesis"]
```

End of ARCHITECTURE.md
