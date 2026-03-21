# NoteLib Product Specification

Rebrand note: StudySnap has been rebranded to NoteLib. Database schema/table names remain unchanged unless explicitly requested.

## Product Overview

NoteLib is an AI-powered study workspace that helps users turn their notes into structured study materials such as summaries, key concepts, and quizzes.

The goal is to support active recall and repeated practice through a calm, iterative learning workflow.

## Core Concept

Note-first model:

- Note is the main entity.
- Study Pack is the AI-generated enhancement of a Note.
- Users first save Notes, then generate Study Packs from those Notes.

Note states:

- `Draft` (no AI-generated content yet)
- `Study Pack Ready` (AI-generated content exists)

Generated Study Pack outputs include:

- AI-generated title (optional)
- subject (optional)
- tags (optional)
- summary
- key concepts
- practice quiz
- Challenge Quiz
- Adaptive Practice

## Versioning Model (Clone Instead of Regenerate)

NoteLib does not overwrite existing generated content.

Instead of regenerating from the same Note, users clone a Note, edit the clone, and generate a new Study Pack from the cloned Note.

Clone behavior:

- Clone copies user-authored fields:
  - title
  - subject
  - tags
  - note content
- Clone does not copy AI/generated history fields:
  - summary
  - key concepts
  - quizzes
  - performance history

This supports iterative learning and avoids accidental overwrites.

## User Flow

1. User creates or saves a Note.
2. Note is stored in the system.
3. User clicks `Generate Study Pack`.
4. AI generates summary, key concepts, and quizzes.
5. User reviews with Quick Review, Challenge Quiz, and Adaptive Practice.
6. If the user wants to improve the note, they clone the note, edit it, and generate a new Study Pack from the clone.

## Architecture Overview

High-level model:

- `notes` table stores user-authored fields (`title`, `subject`, `content`, `tags`).
- Generated fields are stored and linked to the same Note (`summary`, `key concepts`, `quizzes`).
- Quiz sessions and performance are linked to the Note.
- Clone creates a new Note row with copied user-authored fields only.

## Product Philosophy

Learning loop:

Capture -> Generate -> Review -> Improve -> Clone -> Repeat

NoteLib is designed to help users iteratively improve understanding, not just generate summaries once.

---

## Key Features

### Public Landing Page

Route: `/`

Required sections:

- hero
- Study Pack preview
- how-it-works
- feature highlights
- pricing teaser
- final CTA
- FAQ (before footer; lightweight accordion)

CTA behavior:

- primary CTA: account creation
- secondary CTA: demo exploration (`/demo`)

### Study Pack Generation

- Input modes: pasted notes text or uploaded image notes (OCR)
- Output: title, summary, key concepts, quiz questions, metadata (`subject`, `tags`)
- Demo mode must not call real generation pipeline, persist data, or consume usage
- Unverified users are blocked from generation with structured `403`:
  - `code=EMAIL_VERIFICATION_REQUIRED`
  - `action=RESEND_VERIFICATION`

### Study Library

The Study Library is where users revisit generated content.

Users can:

- view saved Study Packs
- search by title/tags
- filter by subject (single select, `All subjects` default)
- filter by tags (multi-select OR matching)
- combine search + subject + tag filters (frontend-only on loaded items)
- sort by recent/title
- open by clicking card/title
- start Quick Review
- delete Study Packs (Library only, explicit confirmation)
- load paginated results (cursor-based, default `20`, explicit `Load More`)

Dashboard guidance rules:

- Dashboard is non-destructive and guidance-first.
- Deletion is not available from Dashboard.
- Dashboard may show `Mastery Snapshot` based on existing completed Quick Review data.

### Shareable Study Packs

- Public share links use `/p/{token}`
- Shared pages are read-only and auth-aware
- Share page can show title, summary, key concepts, and quiz preview
- Remix/copy duplicates into current user library and must not call LLM
- Duplicate title resolution:
  - `{Title}`
  - `{Title} (Copy)`
  - `{Title} (Copy 2)`, `{Title} (Copy 3)`, ...
- Success feedback: `Study Pack copied to your library.`

### Quick Review

- Primary quiz mode for a Study Pack
- Immediate correctness feedback (`green = correct`, `red = incorrect`)
- Retry incorrect questions once
- Optional confidence feedback (`HIGH`, `MEDIUM`, `LOW`)
- Session history persists for progress tracking

### Challenge Quiz (Premium)

- Timed exam-style mode (10 minutes)
- Generated from Study Pack summary + key concepts only
- Difficulty and question count adapt by latest Quick Review score:
  - `<50`: 10 questions, easy-medium
  - `<80`: 12 questions, medium
  - `>=80`: 15 questions, medium-hard
- Reuse existing in-progress session to avoid duplicate LLM calls
- Persist in-progress state (answers, index, timer basis)
- Usage limit: 50/month (separate from Study Pack generation quota)

### Adaptive Practice (Premium)

- Generated from Study Pack summary + key concepts + weak concepts only
- Question count by weak-concept volume:
  - `<=2`: 5
  - `<=4`: 7
  - `>=5`: 10
- Reuse existing in-progress session to avoid duplicate LLM calls
- Usage limit: 50/month (separate from Study Pack generation quota)

### Authentication Session Handling

- Protected routes require auth
- `401` on protected API calls clears auth and redirects to `/login`
- Preserve destination with `redirect` query param
- Session-expired redirects include `reason=session_expired`
- Users can sign up/login before verification; unverified users are blocked from generation
- Verification email delivery uses provider-agnostic `EmailService`
- Transactional email content uses file-based templates

### Plan and Billing

Settings route section: `Plan & Billing`

- show plan (`FREE` or `PREMIUM`)
- show usage buckets separately:
  - Study Packs (monthly quota)
  - Challenge Quiz (50/month)
  - Adaptive Practice (50/month)
- Stripe Checkout for upgrade
- Stripe webhook sync keeps plan state aligned
- Premium-gated upgrade prompts should link to `/settings#plan-billing`

Plan limits:

- Free: 5 Study Packs/month, includes summaries/key concepts/Quick Review/retry/Library/Today's Focus/AI Study Coach
- Premium: 100 Study Packs/month + Challenge Quiz + Adaptive Practice + weak concept insights

---

## Activity Tracking

Track lightweight events such as:

- `CREATED_STUDY_PACK`
- `STARTED_QUICK_REVIEW`
- `COMPLETED_QUICK_REVIEW`
- `COMPLETED_ADAPTIVE_QUIZ`

Events are linked to user + Note/Study Pack context and timestamp.

---

## Non-Goals (Current Scope)

Not included unless explicitly requested:

- spaced repetition scheduling
- full exam simulation grading engine
- heavy analytics dashboards
- classroom/teacher management
- collaborative/family linking features
