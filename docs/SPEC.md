# SPEC.md — Study Snap MVP (Web-first)

## Product positioning
Study Snap turns study notes into structured review materials and practice quizzes.
It’s a calm, on-demand tutor workflow: upload notes → get a review sheet you can study from.

Tone:
- Calm
- Structured
- Supportive
- Non-judgmental

---

## MVP scope

Included:
- Landing page
- Study page (paste notes + image upload)
- Results view (review sheet + quiz)
- Friendly error handling
- Light/Dark theme + global navbar

Excluded (future):
- Exam simulation mode
- Deep explanation mode (premium)
- Flashcards/spaced repetition
- Payments/Stripe
- Advanced dashboards/analytics
- Gamification

---

## Primary user flow

### 1) Landing (`/`)
Headline and CTA are about **NOTES → REVIEW**, not question solving.

CTA:
- “Turn Notes into Review” → `/study`

---

### 2) Study page (`/study`)
Input modes:
- Paste notes (textarea)
- Upload image (jpeg/png/webp)

States:
1. Idle: nothing provided, Generate disabled
2. Ready: notes text OR image present, Generate enabled
3. Loading: “Creating your review materials…”
4. OCR needs confirmation: show editable extracted text, user confirms
5. Result: show review sheet + quiz
6. Error: friendly message + recovery path (retry, edit text, switch to paste)

---

## Results view
Sections:
1. Title (derived topic)
2. Summary (short paragraph)
3. Key concepts (bulleted list)
4. Practice quiz (default 5 questions)
5. Actions:
   - “Try Another”
   - “Edit Notes” (optional)
   - (Future) Regenerate / More questions / Flashcards

---

## Backend behavior
Endpoint: `POST /api/review`

Flow:
1. Validate input
2. If image: OCR → extract text
3. Normalize notes
4. Build prompt
5. Call LLM
6. Validate structured output
7. Return JSON response

---

## Design system (locked for MVP)
Goal: “Friendly academic” — clean like Khan Academy, slightly warm.

- Background: white
- Surface/Cards: gray-50
- Border: gray-200
- Text primary: gray-900
- Text secondary: gray-600
- Primary accent: blue-600 (hover blue-700)
- Success accent: emerald-500

Rules:
- No gradients for MVP.
- One primary accent (blue) for primary actions.
- Use shadcn tokens (`bg-background`, `text-foreground`, etc.) for theme consistency.
- Cards: rounded-xl, shadow-sm. Inputs/buttons: rounded-lg.

---

## Global layout (required)
- Global Navbar appears on all pages.
- Navbar includes:
  - Study Snap brand + logo placeholder
  - Menu links (Dashboard placeholder optional)
  - Light/Dark theme toggle (next-themes)
- Theme toggle avoids hydration mismatch (mounted guard).

---

## Privacy
- Uploaded images are deleted after OCR processing.
- Avoid logging raw images or full extracted text.

End of SPEC.md
