# AGENTS.md — Study Snap (Web-first MVP)

You are an AI coding agent (Codex) helping implement Study Snap.
Follow these rules strictly to keep the codebase consistent and shippable.

## Product summary
Study Snap converts study notes into structured review materials and practice quizzes.
Core feature is NOT "solve a question" — it is:
**Notes → Review Sheet + Key Concepts + Practice Quiz.**

Inputs:
- pasted notes text
- uploaded image of notes (OCR)

Outputs:
- title
- summary
- keyConcepts/definitions
- practice quiz questions

Tone: calm, patient, non-judgmental.

Refer to `docs/ARCHITECTURE.md` for backend system design and data models.

## MVP scope (do not expand without request)
Pages:
- Landing
- Study (paste notes + upload image)
- Results (review sheet + quiz)

Backend:
- One primary endpoint: `POST /api/review` (text and/or image)
- Optional: health endpoint

MVP includes:
- Review generation from text
- OCR flow for images with low-confidence fallback (editable extracted text)
- Global navbar + theme toggle
- Images deleted after processing

NOT in MVP:
- Exam simulation
- Flashcards/spaced repetition
- Payments/Stripe
- Heavy dashboards/analytics
- Gamification

---

## UX principles
- “Friendly academic”: clean like Khan Academy, slightly warm, not childish.
- Slightly guided flow. Minimal distractions.
- Error states are supportive and actionable.

Microcopy:
- Prefer: “Let’s turn your notes into a review.”
- Avoid: “Crush this!”, “Hurry up!”, “Wrong!”

---

## Frontend conventions (`/frontend`)
Stack:
- Next.js App Router
- TypeScript
- Tailwind CSS
- shadcn/ui (preferred)
- lucide-react icons
- next-themes for theme switching

Rules:
1. Use shadcn/ui components for Button/Input/Card/Alert/etc.
2. Keep pages thin: orchestrate only; logic in `lib/` and small components/hooks.
3. All backend calls go through `lib/api.ts` (no scattered `fetch`).
4. Always handle loading and error states.
5. Use shadcn tokens (`bg-background`, `text-foreground`, etc.) for theme consistency.

### Theme + Navbar requirements (MVP)
- Support light/dark theme with a toggle in the navbar.
- Tailwind: `darkMode: ["class"]`.
- Navbar appears on all pages.
- Navbar includes: Study Snap brand text, logo placeholder, menu link(s), theme toggle.
- Theme toggle avoids hydration mismatch (mounted guard).

---

## Backend conventions (`/backend`)
Rules:
1. Controllers thin; business logic in services.
2. Use one orchestrator service (e.g., `ReviewService`):
   validate → OCR (if image) → normalize → LLM → validate output → return.
3. Enforce input limits server-side (file size/type, text length).
4. Do not store images permanently; delete after OCR.
5. Log request id + latency + failure codes (avoid logging full extracted text).

---

## Backend module plan (Spring Boot)

Controllers:
- ReviewController
- ShareController
- HealthController (optional)

Services:
- ReviewService (orchestrator)
- OcrService
- LlmReviewService
- UsageLimitService
- ShareService

Persistence:
- ReviewRepository
- ShareLinkRepository
- (Optional) ReviewDraftRepository for OCR confirmation flow

---

## API contract (MVP)

### POST `/api/review`
Inputs:
- JSON (notes text) OR multipart (image of notes)
- Optional subject/topic label

Success response:
```json
{
  "id": "string",
  "inputType": "text|image",
  "extractedText": "string|null",
  "title": "string",
  "summary": "string",
  "keyConcepts": ["string"],
  "quiz": [
    { "question": "string", "choices": ["string"], "answer": "string", "explanation": "string" }
  ],
  "meta": {
    "ocrConfidence": "number|null",
    "latencyMs": "number"
  }
}
```

OCR needs confirmation response:
```json
{
  "status": "needs_text_confirmation",
  "id": "string",
  "extractedText": "string",
  "meta": { "ocrConfidence": 0.72 }
}
```

Error response:
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

## Endpoints (MVP + near-future)

- POST /api/review
   - JSON { notesText } OR multipart { image }
   - returns ReviewResponse OR needs_text_confirmation

- POST /api/review/confirm-text
   - { draftId, notesText } → generates and returns ReviewResponse

- GET /api/review/{id}
   - fetch saved review

- POST /api/review/{id}/share
   - creates share token

- GET /api/share/{token}
   - public fetch for share page

### Review usage metadata

When saving reviews, also persist model usage metadata where available:
- modelUsed
- inputTokens
- outputTokens
- cachedInputTokens (optional)
- estimatedCost (optional)

This is for internal monitoring and cost analysis.

---

## Cost control: tiered model strategy (required)

Use different model tiers depending on feature:
- Cheap model: text cleanup/OCR formatting (optional)
- Standard model (free): summary + key concepts + practice quiz (3–5 questions)
- Higher quality model (premium): mock exam generation + analytics

Config knobs:
- LLM_MODEL_FREE
- LLM_MODEL_PREMIUM
- QUIZ_QUESTIONS_FREE
- QUIZ_QUESTIONS_PREMIUM
- MAX_NOTES_CHARS_FREE

### Initial model decision (MVP)

For Demo and Free plans, use `gpt-4.1-mini`.

Reason:
- good cost/performance balance for summary + key concepts + practice quiz
- cheaper than `gpt-4.1`

Premium may use a stronger model later for premium-only features.
