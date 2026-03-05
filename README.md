# Study Snap

Study Snap is an AI-powered study assistant that turns messy notes into structured review materials and practice quizzes.

Students can paste notes or upload photos of their study material, and Study Snap will generate:

- Structured review sheets
- Key concepts
- Practice quiz questions
- Exam-focused summaries

## One-liner
Turn your notes into exam-ready study materials instantly.

## Tech stack

Frontend
- Next.js (App Router) + TypeScript
- Tailwind CSS
- shadcn/ui
- next-themes (light/dark)

Backend
- Spring Boot
- OCR provider (for images)
- LLM provider

## Repo structure

```text
study-snap/
  frontend/
  backend/
  docs/
  .github/
  AGENTS.md
  README.md
  .env.example
```

## Documentation

Product and development documentation lives in `/docs`.

- SPEC.md — product behavior and UX rules
- ROADMAP.md — development phases
- ARCHITECTURE.md — backend system design

## MVP goal
Upload notes → generate review materials.

Privacy: uploaded images are deleted after OCR processing.
