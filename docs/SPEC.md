# SPEC.md — Study Snap MVP (Web-first)

## Product positioning
Study Snap is a calm, on-demand AI tutor.
It helps students understand questions step-by-step and prepare for exams without pressure.

Tone:
- Calm
- Structured
- Supportive
- Non-judgmental

Not:
- Gamified
- Loud
- Competitive

---

## MVP scope

Included:
- Landing page
- Solve page (paste text + image upload)
- Result display (step-by-step explanation)
- Friendly error handling
- Anonymous limited usage (optional)

Excluded (future phases):
- Exam simulation mode
- Deep explanation toggle
- Stripe / payments
- Advanced analytics dashboard
- Gamification

---

## Primary user flow

### 1) Landing page (`/`)
Purpose:
- Explain value clearly
- Encourage first solve

CTA:
- “Try a Question” → `/solve`

---

### 2) Solve page (`/solve`)

Input modes:
- Paste text (textarea)
- Upload image (jpeg/png/webp)

#### Text solve flow
States:
1. Idle: textarea empty, Solve disabled
2. Ready: text present, Solve enabled
3. Loading: “Analyzing your question…”
4. Result: show structured explanation
5. Error: friendly retry message

#### Image solve flow
States:
1. Idle: upload area
2. Preview: show image, Process enabled
3. OCR processing: “Reading your image…”
4A. OCR success → proceed to LLM
4B. OCR low confidence → show extracted text editable, user confirms → proceed to LLM
5. Result
6. Error with suggestions + text fallback

---

## Result display
Sections:
1. Restated Question (card)
2. Steps (numbered list, spaced)
3. Final Answer (highlight box)
4. Actions:
   - “Try Another Question”
   - (Future) Simplify / Regenerate / Generate Quiz

---

## Anonymous usage rules (optional for MVP)
- Allow 1–2 solves per session cookie.
- After limit: show “Create a free account to continue learning.”
- No history saving for anonymous users.

---

## Backend behavior
Endpoint: `POST /api/solve`

Flow:
1. Validate input
2. If image: OCR → extract text
3. Normalize text
4. Build prompt
5. Call LLM
6. Validate structured output
7. Return JSON response

---

## Response structure (required)

Success:
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
    "latencyMs": "number"
  }
}
```

OCR needs confirmation:
```json
{
  "status": "needs_text_confirmation",
  "id": "string",
  "extractedText": "string",
  "meta": { "ocrConfidence": 0.72 }
}
```

Error:
```json
{
  "error": {
    "code": "string",
    "message": "user-friendly string"
  }
}
```

---

## Design system (locked for MVP)

Goal: “Friendly academic” — clean like Khan Academy, slightly warm, not childish.

### Colors (Tailwind tokens)
- Background: white
- Surface/Cards: gray-50
- Border: gray-200
- Text primary: gray-900
- Text secondary: gray-600
- Primary accent: blue-600
- Primary hover: blue-700
- Success: emerald-500

Rules:
- No gradients for MVP.
- One primary accent (blue) for primary actions.
- Use success (emerald) only for success/highlight states.

### Typography
- Font: Inter (preferred) or system sans.
- H1: text-3xl to text-4xl, font-semibold
- H2: text-xl to text-2xl, font-semibold
- Body: text-base, leading-relaxed

### Spacing & layout
- Main container: `max-w-3xl mx-auto px-6 py-10`
- Section spacing: `space-y-8`
- Card padding: `p-6`

### Radius & shadows
- Cards/containers: rounded-xl, shadow-sm
- Inputs/buttons: rounded-lg
- Popovers: shadow-md

### Icons & components
- Icons: lucide-react outline icons only
- Components: prefer shadcn/ui components

---

## Global layout (required)
- Global Navbar appears on all pages (including `/` and `/dashboard`).
- Navbar includes:
  - Study Snap brand + logo placeholder
  - Menu links (at least Dashboard)
  - Light/Dark theme toggle
- Theme support:
  - Light and Dark themes supported via class-based dark mode
  - Use shadcn tokens (bg-background, text-foreground) for consistency
  - Theme toggle avoids hydration mismatch (mounted guard)

---

## Error handling philosophy
Never blame the user.
Always offer a recovery path (retry, edit extracted text, or paste text).

End of SPEC.md
