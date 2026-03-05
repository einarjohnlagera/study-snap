# ROADMAP.md — Study Snap (Web-first MVP)

Goal: ship an MVP that turns notes into review materials + practice quiz.

## Phase 0 — Repo & setup
- [ ] Monorepo folders: `frontend/`, `backend/`, `docs/`
- [ ] Add docs: `AGENTS.md`, `docs/SPEC.md`, `docs/ROADMAP.md`
- [ ] Add `.env.example`
- [ ] Choose OCR + LLM providers (stubs acceptable initially)

Deliverable: repo runs locally.

---

## Phase 1 — Frontend foundation
- [ ] Next.js App Router (TypeScript) initialized
- [ ] Tailwind configured with class-based dark mode
- [ ] shadcn/ui installed
- [ ] next-themes ThemeProvider + ThemeToggle
- [ ] Global Navbar in `app/layout.tsx`
- [ ] Pages:
  - [ ] `/` landing (notes → review positioning)
  - [ ] `/study` UI skeleton
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

End of ROADMAP.md
