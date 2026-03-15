# Study Snap

Study Snap is an AI-powered study assistant that turns messy notes into structured study materials and reusable study packs.

Users can paste notes or upload photos of their study material, and Study Snap can generate:

- Title
- Summary
- Key concepts
- Practice quiz questions
- Exam-focused summaries

## One-liner

Turn your notes into exam-ready study materials instantly.

## Core value

Study Snap helps users turn notes into reusable study packs.

A Study Pack includes:

- summary
- key concepts
- practice quiz

Users can save generated Study Packs in their personal Study Library for later study pack.

## Product direction

Study Snap is evolving from a one-shot study pack generator into a reusable study workspace.

Core workflow:

**Notes → Study Pack → Revisit later**

This repo currently centers on:

- note-to-study-pack generation
- OCR support for image-based notes
- Study Library support
- demo mode
- shareable Study Pack links
- freemium plans and subscriptions (Stripe Checkout)
- future user accounts and authenticated ownership

## Tech stack

### Frontend
- Next.js (App Router) + TypeScript
- Tailwind CSS
- shadcn/ui
- next-themes (light/dark)

### Backend
- Spring Boot
- PostgreSQL
- OCR provider (Google Cloud Vision direction)
- LLM provider (OpenAI direction)

## Repo structure

```text
study-snap/
  frontend/
  backend/
  docs/
    product/
    architecture/
    features/
    ai/
  legacy/
  AGENTS.md
  README.md
  .env.example
```

## Documentation map

Canonical documentation now lives in `/docs`:

- `docs/product/SPEC.md` — product behavior and UX rules
- `docs/product/ROADMAP.md` — development phases and sequencing
- `docs/architecture/ARCHITECTURE.md` — backend system design and flows
- `docs/architecture/DATA_MODEL.md` — consolidated entity and table design
- `docs/features/` — feature-specific execution context
- `docs/ai/PROMPTS.md` — prompt assets and JSON contract
- `AGENTS.md` — coding-agent rules for implementation

## Legacy preservation

No original context was discarded during this refactor.

The previous versions of the original markdown files are preserved under `/legacy` so the repo keeps both:

- the new organized structure
- the original source documents for reference and migration

## MVP goal

Upload notes → generate study pack materials → save and revisit them as Study Packs.

## Privacy

Uploaded images are deleted after OCR processing.
Avoid logging raw images or full extracted text.

