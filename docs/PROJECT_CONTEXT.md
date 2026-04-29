# NoteLib Project Context

## What NoteLib Is

NoteLib is a structured study system, not a notes app. It guides users through a repeating learning loop:

`Create → Understand → Practice → Challenge → Improve`

Users can start with their own notes or generate a draft note from a topic. NoteLib converts that input into a Study Pack — summary, key concepts, and quiz material — and then supports repeated practice, challenge quizzes, and improvement through weak-concept tracking.

## Core Product Model

- Note is the primary entity.
- Study Pack is the generated enhancement state of a Note.
- Note states:
  - `Draft`
  - `Study Pack Ready`
- Note visibility:
  - `PRIVATE`
  - `PUBLIC`

## Learning Loop

Primary loop (user-facing positioning):

`Create → Understand → Practice → Challenge → Improve`

Supporting product loop (internal model):

`Capture → Generate → Review → Improve → Copy → Repeat`

Onboarding is the entry point into the loop. Users complete a 5-step onboarding flow that ends with a generated Study Pack, placing them at the `Understand` stage before they touch the dashboard. This eliminates the empty-state activation problem.

## Versioning Rule

NoteLib does not overwrite generated content on the same Note.

Users create a new version by using `Make a Copy`, editing the copied Note, and generating a new Study Pack from that copy.

Copy includes user-authored fields only:

- title
- subject
- tags
- note content

Copy does not include generated/performance fields:

- summary
- key concepts
- quizzes
- review performance history
- quiz sessions

## Library Structure

Sidebar navigation:

- Main: Dashboard, Library, Public Library
- Account: Profile, Settings

Primary routes:

- `/library`
- `/library/public`
- `/notes/{id}` (Note Detail)
- `/public/library/{subject}/{slug}` (Public Note Detail, read-only)
- `/public/profile/{userId}` (Public Profile)

## Verification and OCR

- Unverified users are blocked from Study Pack generation.
- Unverified users are blocked from OCR upload.
- OCR is optional in Create/Edit Note and populates Note content for manual review before save/generate.
- Generate Note from topic is available in Create Note and fills editable note content before save.

## Tech Stack

Backend: Spring Boot  
Frontend: Next.js  
Database: PostgreSQL  
AI: OpenAI LLM  
OCR: Google Vision
Payments: Xendit hosted checkout

## Payments

- Premium upgrades use Xendit hosted invoice checkout.
- Current Premium billing model is manual renewal with `30` days of access per successful payment.
- Frontend starts checkout through `POST /api/payments/create` and redirects to the returned hosted URL.
- Premium access is activated only after the backend receives and validates `POST /api/webhooks/xendit`.
- Success and failure pages are user-facing status pages only; they do not grant Premium access.
- Frontend redirects after checkout never activate Premium directly.

## Core Domain Models

- User
- Note
- Study Pack (generated Note state)
- QuickReviewSession
- ActivityEvent

All generated outputs and quiz/practice sessions are note-scoped (`noteId`).

## Feature Documentation

- docs/features/onboarding.md
- docs/features/study-pack-generation.md
- docs/features/quick-review.md
- docs/features/dashboard-recommendation.md

## Architecture

See `docs/architecture/ARCHITECTURE.md`.
