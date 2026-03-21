# NoteLib

> Rebrand update: this project was renamed from StudySnap to NoteLib. Core behavior and database schema remain unchanged.

NoteLib is an AI-powered study workspace that helps users turn their notes into structured study materials such as summaries, key concepts, and quizzes.

Users can paste notes or upload photos of their study material, and NoteLib can generate:

- AI-generated title (optional)
- subject (optional)
- tags (optional)
- summary
- key concepts
- practice quiz questions
- Challenge Quiz sets
- Adaptive Practice sets

## One-liner

Write notes. Generate knowledge. Practice smarter.

## Core Concept

Note-first model:

- Note is the main entity.
- Study Pack is the AI-generated enhancement state of a Note.
- Users create and save Notes first, then generate Study Packs from those Notes.

Note states:

- `Draft`: Note exists with user-authored content only.
- `Study Pack Ready`: AI-generated study outputs are linked to the Note.

Generated Study Pack outputs include:

- AI-generated title (optional)
- subject (optional)
- tags (optional)
- summary
- key concepts
- practice quiz
- Challenge Quiz
- Adaptive Practice

## Copy Model

NoteLib does not overwrite existing generated content.

Users make a copy of a Note, edit the copy, and generate a new Study Pack from that copied Note.

Copy behavior:

- Copy includes:
  - title
  - subject
  - tags
  - note content
- Copy does not include:
  - summary
  - key concepts
  - quizzes
  - performance history
  - quiz sessions

This supports iterative learning and avoids accidental overwrites.

## User Flow

1. User creates or saves a Note.
2. Note is stored in the system.
3. User clicks `Generate Study Pack`.
4. AI generates summary, key concepts, and quizzes.
5. User reviews with Quick Review, Challenge Quiz, and Adaptive Practice.
6. To improve content, user makes a copy, edits it, and generates a new Study Pack from the copy.
7. User can set note visibility:
   - `Make Public` -> appears in Public Library
   - `Make Private` -> only visible in My Library
8. User can copy public notes into My Library and continue studying.

## Navigation

Sidebar structure:

- Dashboard
- My Library
- Public Library
- Settings

## Architecture Overview

High-level model:

- `notes` table stores user-authored fields (`title`, `subject`, `content`, `tags`).
- `notes.visibility` controls listing behavior (`PRIVATE` or `PUBLIC`).
- AI-generated fields are linked to the same Note (`summary`, `key concepts`, `quizzes`).
- quiz sessions and performance history are linked to the Note.
- copying creates a new Draft Note row and copies only user-authored fields.

## Product Philosophy

Learning loop:

**Capture -> Generate -> Review -> Improve -> Copy -> Repeat**

NoteLib is designed for iterative understanding, not one-time summary generation.

This repo currently centers on:

- note-to-study-pack generation
- OCR support for image-based notes
- My Library and Public Library support
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
notelib/
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

- `docs/product/SPEC.md` - product behavior and UX rules
- `docs/product/ROADMAP.md` - development phases and sequencing
- `docs/architecture/ARCHITECTURE.md` - backend system design and flows
- `docs/architecture/DATA_MODEL.md` - consolidated entity and table design
- `docs/features/` - feature-specific execution context
- `docs/ai/PROMPTS.md` - prompt assets and JSON contract
- `AGENTS.md` - coding-agent rules for implementation

## Legacy preservation

No original context was discarded during this refactor.

The previous versions of the original markdown files are preserved under `/legacy` so the repo keeps both:

- the new organized structure
- the original source documents for reference and migration

## MVP goal

Capture notes -> generate Study Pack outputs -> review -> improve -> copy -> repeat.

## Privacy

Uploaded images are deleted after OCR processing.
Avoid logging raw images or full extracted text.
