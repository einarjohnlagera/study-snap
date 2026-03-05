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
  "error": {
    "code": "string",
    "message": "user-friendly string",
    "details": "optional"
  }
}
```
