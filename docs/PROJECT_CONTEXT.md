# NoteLib Project Context

## What NoteLib Is

NoteLib is an AI-powered study workspace that turns user-authored notes into structured study materials and review modes.

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

Capture -> Generate -> Review -> Improve -> Copy -> Repeat

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

- Main: Dashboard, My Library, Public Library
- Account: Profile, Settings

Primary routes:

- `/library`
- `/library/public`
- `/notes/{id}` (Note Detail)
- `/public/notes/{id}` (Public Note Detail, read-only)

## Verification and OCR

- Unverified users are blocked from Study Pack generation.
- Unverified users are blocked from OCR upload.
- OCR is optional in Create/Edit Note and populates Note content for manual review before save/generate.

## Tech Stack

Backend: Spring Boot  
Frontend: Next.js  
Database: PostgreSQL  
AI: OpenAI LLM  
OCR: Google Vision

## Core Domain Models

- User
- Note
- Study Pack (generated Note state)
- QuickReviewSession
- ActivityEvent

All generated outputs and quiz/practice sessions are note-scoped (`noteId`).

## Feature Documentation

- docs/features/study-pack-generation.md
- docs/features/quick-review.md
- docs/features/dashboard-recommendation.md

## Architecture

See `docs/architecture/ARCHITECTURE.md`.
