# ROADMAP.md — Study Snap (Web-first MVP)

Goal: ship an MVP that turns notes into review materials + practice quiz.

## Phase 0 — Repo & setup
- [X] Monorepo folders: `frontend/`, `backend/`, `docs/`
- [X] Add docs: `AGENTS.md`, `docs/SPEC.md`, `docs/ROADMAP.md`
- [X] Add `.env.example`
- [X] Choose OCR + LLM providers (stubs acceptable initially)

Deliverable: repo runs locally.

---

## Phase 1 — Frontend foundation
- [X] Next.js App Router (TypeScript) initialized
- [X] Tailwind configured with class-based dark mode
- [X] shadcn/ui installed
- [X] next-themes ThemeProvider + ThemeToggle
- [X] Global Navbar in `app/layout.tsx`
- [X] Pages:
  - [X] `/` landing (notes → review positioning)
  - [X] `/study` UI skeleton
  - [ ] `/dashboard` placeholder (optional)

Deliverable: UI shell + theme toggle on all pages.

---

## Phase 2 — Backend MVP (text review generation)
- [ ] `POST /api/review` accepts JSON `{ notesText }`
- [ ] Validate input size
- [ ] Prompt builder + LLM call (or stub response for UI dev)
- [ ] Return structured JSON: title, summary, keyConcepts, quiz[]
- [ ] Logging (request id, latency)

Deliverable: paste notes → get review output.

---

## Phase 3 — Connect UI to backend
- [ ] `frontend/types/review.ts`
- [ ] `frontend/lib/api.ts` with `createReviewFromText()` and `createReviewFromImage()`
- [ ] `/study` integrates text mode end-to-end
- [ ] Loading + error states
- [ ] Results rendering matches SPEC

Deliverable: paste notes → generate → render.

---

## Phase 4 — Image upload + OCR
- [ ] Frontend: upload + preview
- [ ] Backend: accept multipart + OCR integration
- [ ] Low-confidence OCR flow (`needs_text_confirmation`)
- [ ] UI: allow editing extracted text and resubmit

Deliverable: upload image notes → OCR → review output (with edit fallback).

---

## Phase 5 — MVP polish + deploy
- [ ] Improve prompt quality and formatting
- [ ] Add caps: max file size, allowed types
- [ ] Confirm image deletion behavior
- [ ] Deploy frontend (Vercel) + backend (Render/Fly/AWS)
- [ ] Add basic counters (success/fail)

## Phase 6 — Premium Features
- [ ] Mock Exam mode
- [ ] Performance analytics
- [ ] Topic mastery detection
- [ ] User accounts
- [ ] Snap history

## Phase 7 – Shareable Study Packs
- [ ] Create share tokens in backend
- [ ] Add public page route: `/share/[token]`
- [ ] Track optional view_count
- [ ] (Optional later) expiration + private links (premium)

## Phase — Usage limits + plans

- [ ] Demo: 1 review (anonymous cookie/session)
- [ ] Free: 3 reviews/day (requires login)
- [ ] Premium: 200 reviews/month + mock exam + analytics (future)

End of ROADMAP.md
