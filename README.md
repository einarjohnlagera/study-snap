# Study Snap (Web-first MVP)

Study Snap is a calm, on-demand AI tutor for students (elementary to college).
Users can paste a question or upload an image of notes/questions, and receive a structured, step-by-step explanation and final answer.

**Tone:** calm, supportive, non-judgmental (not “coach pressure”).

## Repo structure

This repo is a monorepo:

- `frontend/` — Next.js (App Router) web UI
- `backend/` — Spring Boot API
- `docs/` — specs and roadmap
- `AGENTS.md` — AI/Codex implementation rules

```
study-snap/
  frontend/
  backend/
  docs/
  .github/
  AGENTS.md
  README.md
  .env.example
```

## Quick start

### Frontend (Next.js)

```bash
cd frontend
npm install
npm run dev
```

Frontend dev server: http://localhost:3000

### Backend (Spring Boot)

```bash
cd backend
# Maven:
./mvnw spring-boot:run
# or Gradle:
./gradlew bootRun
```

Backend dev server: http://localhost:8080

## Local dev notes

- CORS: backend must allow the frontend dev origin (e.g. `http://localhost:3000`).
- Environment variables:
  - Frontend: `frontend/.env.local`
  - Backend: configure via env vars or `application-local.yml`
- Source of truth:
  - Product + UX + design system: `docs/SPEC.md`
  - Build plan & milestones: `docs/ROADMAP.md`
  - AI coding rules & conventions: `AGENTS.md`

## MVP guardrails

Included:
- Landing page
- Solve page (paste text + optional image upload)
- Result (steps + final answer)
- Friendly error handling
- Light/Dark theme toggle + global navbar

Excluded (future):
- Exam mode
- Deep explanation toggle (premium)
- Stripe/subscriptions
- Complex dashboards / gamification

## License

TBD
