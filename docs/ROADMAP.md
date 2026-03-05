# ROADMAP.md — Study Snap (Web-first MVP)

This roadmap is optimized for fast shipping and minimal scope creep.

## Phase 0 — Repo & setup (0.5–1 day)
- [ ] Create monorepo: `frontend/`, `backend/`, `docs/`
- [ ] Add docs: `AGENTS.md`, `docs/SPEC.md`, `docs/ROADMAP.md`
- [ ] Add `.env.example`
- [ ] Decide OCR + LLM providers (stub is OK initially)

Deliverable: repo builds locally.

---

## Phase 1 — Frontend skeleton (1–2 days)
- [ ] Initialize Next.js App Router (TypeScript)
- [ ] Configure Tailwind + class-based dark mode
- [ ] Install shadcn/ui
- [ ] Install next-themes + create ThemeProvider + ThemeToggle
- [ ] Add global Navbar in `app/layout.tsx` (brand + dashboard link + theme toggle)
- [ ] Create pages:
  - [ ] `/` landing placeholder
  - [ ] `/solve` skeleton
  - [ ] `/dashboard` placeholder

Deliverable: UI shell + theme toggle on all pages.

---

## Phase 2 — Backend MVP (text-only solve) (1–3 days)
- [ ] `POST /api/solve` accepts JSON `{ text }`
- [ ] Validate input
- [ ] Prompt builder + LLM call (or stub response for UI dev)
- [ ] Return structured JSON (restatedQuestion, steps[], finalAnswer)
- [ ] Basic logging (request id, latency)

Deliverable: text input returns usable result.

---

## Phase 3 — Connect UI to backend (1–2 days)
- [ ] Add `frontend/types/solve.ts`
- [ ] Add `frontend/lib/api.ts`
- [ ] `/solve` page integrates text solve end-to-end
- [ ] Loading + error states
- [ ] Result rendering matches SPEC

Deliverable: paste text → solve → show result.

---

## Phase 4 — Image upload + OCR (2–5 days)
- [ ] Frontend: upload + preview
- [ ] Backend: accept multipart + OCR integration
- [ ] Low-confidence OCR flow (`needs_text_confirmation`)
- [ ] UI: user can edit extracted text and resubmit as text

Deliverable: upload image → OCR → LLM result (with edit fallback).

---

## Phase 5 — MVP polish (1–3 days)
- [ ] Improve formatting
- [ ] Confirm image deletion behavior
- [ ] Add caps: max file size, allowed types
- [ ] Add basic metrics counters (success/fail)

Deliverable: MVP stable for friends beta.

---

## Phase 6 — Optional: anonymous limits + simple auth (later)
- [ ] Session cookie usage count (1–2 solves)
- [ ] Soft wall (signup) after limit
- [ ] Free account daily limits (optional)

Deliverable: controlled usage without overengineering.

End of ROADMAP.md
