# ROADMAP.md — Study Snap

Goal: ship an MVP that turns notes into Study Packs and practice quiz materials, then grow into a reusable study workspace.

## Phase 0 — Repo & setup
- [X] Monorepo folders: `frontend/`, `backend/`, `docs/`
- [X] Add docs
- [X] Add `.env.example`
- [X] Choose OCR + LLM providers (stubs acceptable initially)

Deliverable: repo runs locally.

## Phase 1 — Frontend foundation
- [X] Next.js App Router initialized
- [X] Tailwind configured with class-based dark mode
- [X] shadcn/ui installed
- [X] next-themes ThemeProvider + ThemeToggle
- [X] Global Navbar in `app/layout.tsx`
- [X] Pages:
  - [X] `/` landing
  - [X] `/study` UI skeleton
  - [X] `/dashboard` placeholder

Deliverable: UI shell + theme toggle on all pages.

## Phase 2 — Backend MVP (text Study Pack generation)
- [X] `POST /api/study-packs` accepts JSON `{ notesText }`
- [X] Validate input size
- [X] Prompt builder + LLM call (or stub response)
- [X] Return structured JSON: title, summary, keyConcepts, quiz[]
- [X] Logging (request id, latency)
- [X] Quick Review session history
- [X] Resume unfinished Quick Reviews
- [X] Smart Continue Studying dashboard recommendations

Deliverable: paste notes → get study pack output.

## Phase 3 — Connect UI to backend
- [X] shared study pack types
- [X] API client wiring
- [X] `/study` integrates text mode end-to-end
- [X] loading + error states
- [X] results rendering matches spec

Deliverable: paste notes → generate → render.

## Phase 4 — Image upload + OCR
- [ ] frontend upload + preview
- [X] backend accepts multipart + OCR integration direction
- [X] low-confidence OCR flow (`needs_text_confirmation`)
- [ ] UI for editing extracted text and resubmitting
- [ ] implement Google Vision OCR integration
- [ ] add hybrid OCR pipeline
- [ ] validate uploaded images
- [ ] add quick text detection before OCR extraction
- [ ] clean extracted OCR text before sending to LLM

Deliverable: upload image notes → OCR → Study Pack output.

## Phase 5 — MVP polish + deploy
- [ ] improve prompt quality and formatting
- [ ] add caps: max file size, allowed types
- [ ] confirm image deletion behavior
- [ ] deploy frontend + backend
- [ ] add basic counters (success/fail)
- [ ] add demo mode with hardcoded study pack response
- [ ] ensure demo does not hit backend LLM endpoint
- [ ] add anonymous guardrails for real `/api/study-packs` calls

## Phase 6 — User accounts foundation
- [ ] signup
- [ ] login
- [ ] users table
- [ ] profile fields: first name, last name, display name, country code, profile type
- [ ] account status fields
- [ ] email verification timestamps
- [ ] last login tracking

Deliverable: authenticated user identity foundation for ownership and Study Library.

## Phase 7 — Study Library
- [ ] create dashboard page for saved Study Packs
- [ ] fetch saved Study Packs from backend
- [ ] display Study Pack cards with metadata
- [ ] open saved Study Packs
- [ ] delete saved Study Packs
- [ ] link saved Study Packs to authenticated users
- [X] add user-controlled engagement mode (`FOCUSED` default, optional `CONSISTENCY` / `STREAK`)
- [X] render dashboard consistency/streak card based on selected engagement mode

Deliverable:
- users can revisit generated Study Packs from a dedicated dashboard

## Phase 8 — Shareable Study Packs
- [ ] create share tokens in backend
- [ ] add public page route: `/share/[token]`
- [ ] track optional view count
- [ ] optional expiration and private links later

## Phase 9 — Study Library enhancements
- [ ] add tags to saved Study Packs
- [ ] support filtering by tag
- [ ] support tag-based search
- [ ] improve dashboard organization
- [ ] rename saved Study Packs
- [ ] support folders / collections later
- [ ] support reviewed status later

## Phase 10 — Usage limits + plans
- [ ] Demo: 1 study pack (anonymous cookie/session)
- [ ] Free: 3 study packs/day
- [ ] Premium: 200 study packs/month + premium-only features later
- [ ] subscriptions table
- [ ] plan enforcement
- [ ] analytics-ready subscription history

## Phase 11 — Premium features
- [ ] mock exam mode
- [ ] performance analytics
- [ ] topic mastery detection
- [ ] snap history / richer study history
- [ ] deeper explanation flows

## Phase 12 — Deferred future ideas
- [ ] family plan / parent-child account linking
- [ ] teacher mode
- [ ] classroom sharing
- [ ] topic mastery recommendations
- [ ] caching for repeated inputs
- [ ] export to PDF / Notion / Google Docs

## Notes on sequencing

This roadmap intentionally moves **User Accounts** earlier than in the old roadmap.
Reason:
- authenticated ownership makes the Study Library cleaner
- plan enforcement becomes easier
- subscription history becomes easier to introduce later

The old ordering is preserved in `/legacy/ROADMAP.md`.

