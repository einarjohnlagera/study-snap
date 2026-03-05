# AGENTS.md — Study Snap (Web-first MVP)

You are an AI coding agent (Codex) helping implement Study Snap.
Follow these rules strictly to keep the codebase consistent and shippable.

## Product summary
Study Snap is a web-first "smart tutor on demand" for students (elementary to college).
Users can paste text or upload an image of notes/questions. The system returns a structured, step-by-step explanation and final answer.
Tone: calm, patient, non-judgmental. No “coach” pressure.

## MVP scope (do not expand without request)
Pages:
- Landing
- Solve (paste text + optional image upload)
- Dashboard placeholder (optional)
Result:
- Can be inline on Solve for MVP

Backend:
- One primary endpoint: `POST /api/solve` (text and/or image)
- Optional: health endpoint

MVP includes:
- Step-by-step explanation + final answer (structured JSON)
- Friendly failure handling
- Images deleted after processing (no long-term storage)

NOT in MVP:
- Exam mode
- Deep explanation toggle (premium)
- Stripe/subscriptions
- Complex dashboards
- Gamification

---

## Repository layout
- `/frontend`: Next.js (TypeScript) web app
- `/backend`: Spring Boot API
- `/docs`: specs and roadmap

---

## Core UX principles
- “Friendly academic”: clean like Khan Academy, slightly warm, not childish.
- Minimal distractions, slightly guided flow.
- Error states are supportive and actionable.

Microcopy:
- Prefer: “Let’s work through this step by step.”
- Avoid: “Crush this!”, “Hurry up!”, “Wrong!”

---

## Frontend tech & conventions (`/frontend`)
Stack:
- Next.js App Router
- TypeScript
- Tailwind CSS
- shadcn/ui components (preferred)
- lucide-react icons
- next-themes for theme switching

Conventions:
- `app/*` for routes
- `components/*` for reusable components
- `lib/api.ts` for API client functions (no scattered `fetch`)
- `types/*` for shared TypeScript types

Rules:
1. Use shadcn/ui components for Button/Input/Card/Alert/etc.
2. Keep pages thin: orchestrate only; logic in `lib/` and small components/hooks.
3. Always handle loading and error states.
4. Keep spacing consistent (Tailwind scale). Avoid custom CSS unless needed.

### Theme + Navbar requirements (MVP)
- Support light/dark theme with a toggle in the navbar.
- Tailwind: `darkMode: ["class"]`.
- Use shadcn tokens (`bg-background`, `text-foreground`, etc.) so theme applies consistently.
- Navbar must appear on all pages (at minimum `/` and `/dashboard`).
- Navbar includes:
  - Study Snap brand text
  - Logo placeholder (simple square/rounded mark OK)
  - Menu link(s) (at least Dashboard)
  - Theme toggle button
- Implement Navbar in `app/layout.tsx` (global).
- Theme toggle must avoid hydration mismatch (mounted guard or equivalent).

---

## Backend tech & conventions (`/backend`)
Stack:
- Spring Boot
- Java 17+ (or project default)
- OCR provider + LLM provider (external)

Rules:
1. Controllers thin; business logic in services.
2. Use one orchestrator service (e.g., `SolveService`):
   validate → OCR (if image) → normalize text → LLM → validate output → return.
3. Enforce input limits server-side (file size/type, text length).
4. Do not store images permanently; delete immediately after OCR.
5. Log request id + latency + failure codes (avoid logging full extracted text).

---

## API contract (MVP)

### POST `/api/solve`

Inputs:
- JSON (text-only) OR multipart (image)
- Optional `subject`

Success response:
```json
{
  "id": "string",
  "inputType": "text|image",
  "extractedText": "string|null",
  "restatedQuestion": "string",
  "steps": ["string"],
  "finalAnswer": "string",
  "meta": {
    "ocrConfidence": "number|null",
    "modelTier": "free|premium",
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

---

## Prompting / LLM output requirements
LLM output must be parsable as structured JSON with:
- `restatedQuestion` (string)
- `steps` (string[])
- `finalAnswer` (string)

Tone:
- Calm and supportive.
- Free tier: moderate length.

---

## Security & privacy
- Images deleted after processing.
- Do not log raw images or full extracted text.
- Light rate limiting is OK; do not overbuild anti-abuse for MVP.
