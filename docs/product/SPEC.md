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
- visibility: `PRIVATE` or `PUBLIC`

Generated Study Pack outputs include:

- AI-generated title (optional)
- subject (optional)
- tags (optional)
- summary
- key concepts
- practice quiz
- Challenge Quiz
- Adaptive Practice

## Versioning Model (Copy)

NoteLib does not overwrite existing generated content.

Users make a copy of a Note, edit that copy, and generate a new Study Pack from the copied Note.

Copy behavior:

- Copy includes user-authored fields:
  - title
  - subject
  - tags
  - note content
- Copy does not include AI/generated history fields:
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
6. If the user wants to improve the note, they make a copy, edit it, and generate a new Study Pack from the copy.
7. If the note should be shared broadly, user sets visibility to `PUBLIC` and it appears in Public Library.
8. Public notes can be copied into My Library as new Draft notes.

## Architecture Overview

High-level model:

- `notes` table stores user-authored fields (`title`, `subject`, `content`, `tags`).
- `notes.visibility` controls whether notes are private or listed in Public Library.
- Generated fields are stored and linked to the same Note (`summary`, `key concepts`, `quizzes`).
- Quiz sessions and performance are linked by `noteId`.
- Copy creates a new Draft Note row with copied user-authored fields only.

## Product Philosophy

Learning loop:

Capture -> Generate -> Review -> Improve -> Copy -> Repeat

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
- OCR upload is part of Note authoring (Create/Edit Note) and populates Note `content` for manual review.
- OCR upload does not auto-save and does not auto-generate.
- Demo mode must not call real generation pipeline, persist data, or consume usage
- Unverified users are blocked from generation with structured `403`:
  - `code=EMAIL_VERIFICATION_REQUIRED`
  - `action=RESEND_VERIFICATION`
- Unverified users are also blocked from OCR upload in Create/Edit Note.

### My Library

My Library is where users manage and revisit their own notes (Draft and Study Pack Ready).

Users can:

- view their saved notes
- search by title/tags/content preview
- filter by subject (single select, `All subjects` default)
- filter by tags (multi-select OR matching)
- combine search + subject + tag filters (frontend-only on loaded items)
- sort by recent/title/recently reviewed
- open by clicking card/title
- start Quick Review for Study Pack Ready notes
- manage note visibility (`Make Public` / `Make Private`)
- make a copy (`Make a Copy`) to create a new Draft version

### Public Library

Public Library lists notes where `visibility=PUBLIC` and `owner != current user`.

Users can:

- browse public notes
- filter by search, subject, and tags
- open read-only public note detail
- copy a public note into My Library (`Copy to My Library`)

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

### Navigation

Sidebar groups:

- Main: Dashboard, My Library, Public Library
- Account: Profile, Settings

Primary routes:

- `/library` (My Library)
- `/library/public` (Public Library)
- `/notes/{id}` (Note Detail)
- `/public/library/{subject}/{slug}` (Public Note Detail, read-only, SEO)

### Quick Review

- Primary quiz mode for a Study Pack-ready Note
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
- Unverified users are also blocked from OCR upload
- Verification email delivery uses provider-agnostic `EmailService`
- Transactional email content uses file-based templates

### Plan and Billing

Settings route section: `Plan & Billing`

- show plan (`FREE` or `PREMIUM`)
- support Premium billing cycle selection:
  - `MONTHLY`
  - `YEARLY`
- show usage buckets separately:
  - Study Packs (monthly quota)
  - Challenge Quiz (plan-based monthly quota)
  - Adaptive Practice (Premium-only, plan-based monthly quota)
  - OCR (plan-based monthly quota)
- PayMongo recurring subscription checkout for upgrade
- Billing webhook sync keeps plan state aligned (webhook-driven source of truth)
  - `subscription.activated`
  - `subscription.invoice.paid`
  - `subscription.invoice.payment_failed`
  - `subscription.past_due`
  - `subscription.unpaid`
  - `subscription.updated`
- Premium-gated upgrade prompts should link to `/settings#plan-billing`

Plan limits:

- Free: unlimited notes, 10 Study Packs/month, 5 Challenge Quizzes/month, OCR quota, file uploads, weak concept visibility
- Premium: 100 Study Packs/month, 50 Challenge Quizzes/month, 30 Adaptive Practice sessions/month, higher OCR quota, difficulty selection, priority AI

---

## Activity Tracking

Track lightweight events such as:

- `CREATED_STUDY_PACK`
- `STARTED_QUICK_REVIEW`
- `COMPLETED_QUICK_REVIEW`
- `COMPLETED_ADAPTIVE_QUIZ`

Events are linked to user + note context and timestamp.

Canonical ownership rule:

- Generated summaries, key concepts, quiz content, and all practice sessions are note-scoped (`noteId`).
- Any legacy `studyPackId` fields are compatibility fields only.

---

## Non-Goals (Current Scope)

Not included unless explicitly requested:

- spaced repetition scheduling
- full exam simulation grading engine
- heavy analytics dashboards
- classroom/teacher management
- collaborative/family linking features
