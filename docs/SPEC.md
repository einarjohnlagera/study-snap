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

## Product statement
Study Snap helps students turn messy notes into structured review materials and practice quizzes using AI.

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

### For all subscriptions
Sections:
1. Title (derived topic)
2. Summary (short paragraph)
3. Key concepts (bulleted list)
4. Practice quiz (includes 3–5 questions)
5. Actions:
   - “Try Another”
   - “Edit Notes” (optional)
   - (Future) Regenerate / More questions / Flashcards

## For freemium and higher
1. Users can optionally share generated study packs via public link.

### For premium users
1. Premium users can access Mock exam mode
    - 10–20 exam questions
    - explanations
    - grading logic

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

### Review metadata persistence

Each generated review should store model and usage metadata for cost tracking.

Recommended fields:
- modelUsed
- inputTokens
- outputTokens
- cachedInputTokens (optional, if available)
- estimatedCost (optional, can be computed later)

---

## Backend architecture (MVP)

Primary endpoint:
- `POST /api/review`

Input:
- notes text (JSON) OR notes image (multipart)

Processing flow:
1. Validate input (size/type)
2. If image: OCR → extractedText + confidence, delete image immediately after OCR
3. Normalize notes text
4. LLM generates structured JSON:
    - title, summary, keyConcepts[], quiz[]
5. Validate JSON schema
6. Persist review
7. Return response

OCR low-confidence:
- Return `status: needs_text_confirmation` with extractedText
- UI allows user to edit extractedText and resubmit

Detailed system architecture is described in `docs/ARCHITECTURE.md`.

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

## Shareable Study Pack (Distribution feature)

After generating a review, users can create a shareable link:
- Public URL: `/share/[token]`

Rules:
- Shared page shows generated content (title/summary/key concepts/quiz)
- Do not expose raw uploaded image
- Optionally hide raw notes text by default (recommended)
- Tokens must be unguessable and can support optional expiration later (premium)

---

## Privacy
- Uploaded images are deleted after OCR processing.
- Avoid logging raw images or full extracted text.

---

# Pricing Model

Study Snap follows a freemium model.

## Plans (MVP direction)

Demo (no login):
- 1 review generation
- includes: summary, key concepts, practice quiz (3 questions)
- no saving

Free account:
- 3 reviews per day
- includes: summary, key concepts, practice quiz (5 questions)
- can save and view history

Premium:
- up to 200 reviews per month
- includes: mock exam mode + analytics (future)

### Model usage by plan

Demo:
- uses `gpt-4.1-mini`

Free:
- uses `gpt-4.1-mini`

Premium:
- may use a higher quality model later for premium-only features such as:
    - mock exam generation
    - deeper explanations
    - analytics and topic mastery

End of SPEC.md
