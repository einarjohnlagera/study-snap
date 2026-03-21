# NoteLib Project Context

## What NoteLib Is

NoteLib is an AI-powered study workspace that turns user-authored notes into structured study materials and review modes.

## Core Product Model

- Note is the primary entity.
- Study Pack is the generated enhancement state of a Note.
- Note states:
  - `Draft`
  - `Study Pack Ready`

## Learning Loop

Capture -> Generate -> Review -> Improve -> Clone -> Repeat

## Versioning Rule

NoteLib does not overwrite generated content on the same Note.

Users create a new version by cloning a Note, editing it, and generating a new Study Pack from that clone.

Clone copies user-authored fields only:

- title
- subject
- tags
- note content

Clone does not copy generated/performance fields:

- summary
- key concepts
- quizzes
- review performance history

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

## Feature Documentation

- docs/features/study-pack-generation.md
- docs/features/quick-review.md
- docs/features/dashboard-recommendation.md

## Architecture

See `docs/architecture/ARCHITECTURE.md`.
