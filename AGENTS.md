# AGENTS.md - NoteLib

You are an AI coding agent helping implement NoteLib.
Follow these rules to keep the codebase consistent and shippable.

Rebrand note: StudySnap has been renamed to NoteLib. Keep existing database schema/table names unless explicitly requested.

When working on a feature, always check the corresponding document under `docs/features/`.

## Product Summary

NoteLib converts notes into structured study outputs and review workflows.

Core loop:

`Capture -> Generate -> Review -> Improve -> Make a Copy -> Repeat`

## Required Product Architecture (Current)

- Note is the primary entity.
- Study Pack is generated content attached to a Note.
- A Note has state:
  - `DRAFT`
  - `STUDY_PACK_READY`
- A Note also has visibility:
  - `PRIVATE`
  - `PUBLIC`

### Versioning Rule

- Do not regenerate/overwrite generated content on the same Note.
- Use `Make a Copy` only.
- Copy includes: `title`, `subject`, `tags`, `content`.
- Copy does not include: generated `summary`, `key concepts`, `quiz`, session history, or performance history.
- Copy result is a new `DRAFT` note.

### Library Rule

- My Library is note-based and contains the current user's notes (Draft + Study Pack Ready).
- Public Library is note-based and contains notes where `visibility=PUBLIC`.
- Public Library should exclude the current user's own notes.

### Note Ownership Rule

- Generated outputs (summary/key concepts/quizzes), Quick Review, Challenge Quiz, Adaptive Practice, and performance are scoped to `noteId`.
- If legacy payload fields still expose `studyPackId`, treat them as compatibility fields, not primary ownership.

## UI Terminology (Use Consistently)

- `Dashboard`
- `My Library`
- `Public Library`
- `Note Detail` (unified Note + Study Pack view)
- `New Note`
- `Generate Study Pack`
- `Make a Copy`
- `Copy to My Library`
- `Make Public`
- `Make Private`

Avoid introducing older terms such as `Study Library` or regenerate/overwrite flows.

## Navigation Structure

Keep app shell grouping:

- Main:
  - Dashboard
  - My Library
  - Public Library
- Account:
  - Profile
  - Settings

## Verification and Access Gating

- Users can sign up/log in before email verification.
- Unverified users must not generate Study Packs.
- Unverified users must not use OCR upload.
- Verification-gated API responses should use structured `403` with:
  - `code=EMAIL_VERIFICATION_REQUIRED`
  - `action=RESEND_VERIFICATION`
- Frontend should present a friendly message for OCR gating:
  - `Verify your email before using OCR upload.`

## OCR Flow (Create/Edit Note)

OCR is optional and attached to Note authoring (`New Note` / edit note).

Required behavior:

- User uploads note image.
- OCR extracts text.
- Extracted text is inserted/merged into Note `content`.
- User can review and edit OCR text before save/generate.
- OCR upload does not auto-save and does not auto-generate.
- Uploaded images are not stored permanently.

## MVP Scope (Do Not Expand Without Request)

In scope:

- Note creation/editing
- Study Pack generation from notes
- OCR-assisted note input
- My Library/Public Library flows
- Quick Review, Challenge Quiz, Adaptive Practice
- Share/copy flows
- Plan and billing usage display

Out of scope unless requested:

- flashcards/spaced repetition
- heavy analytics dashboards
- teacher/classroom tooling
- gamification-heavy systems

## Frontend Conventions (`/frontend`)

Stack:

- Next.js App Router
- TypeScript
- Tailwind CSS
- shadcn/ui
- lucide-react

Rules:

1. Keep pages thin; put logic in `lib/`, hooks, and focused components.
2. Route backend calls through `frontend/lib/api.ts`.
3. Always implement loading and error states.
4. Use theme tokens (`bg-background`, `text-foreground`, etc.).
5. Keep Note Detail unified; do not split Note vs Study Pack detail pages again.

## Backend Conventions (`/backend`)

Rules:

1. Keep controllers thin; business logic in services.
2. Keep generation orchestration in Study Pack service flow:
   validate -> OCR (if image) -> normalize -> LLM -> validate output -> persist.
3. Enforce server-side limits for text/image inputs and quotas.
4. Do not log raw images or full OCR text.
5. Persist only validated generated output.
6. Keep ownership checks note-centric.

## Cost and Quotas

- Free: 5 Study Packs/month
- Premium: 100 Study Packs/month
- Premium Challenge Quiz: 50/month
- Premium Adaptive Practice: 50/month
- Challenge/Adaptive quotas are separate from Study Pack generation quota.

## Dashboard and Library Guardrails

- Dashboard is guidance-first and non-destructive.
- Keep delete actions out of Dashboard.
- Keep destructive actions (delete) in Note Detail/My Library with explicit confirmation.

## Documentation Source of Truth

Primary docs:

- `README.md`
- `docs/product/SPEC.md`
- `docs/product/ROADMAP.md`
- `docs/architecture/ARCHITECTURE.md`
- `docs/architecture/DATA_MODEL.md`
- `docs/features/*`

If conflicts appear:

1. Follow current product docs under `docs/`.
2. Use `docs/legacy/` only for historical reference.
