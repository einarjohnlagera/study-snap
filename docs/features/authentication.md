# authentication.md - NoteLib Feature Context

## Goal

Provide account/session behavior that supports Note-first ownership and safe generation controls.

## Core Ownership Model

- Notes are owned by authenticated users (`owner_user_id`).
- Generated Study Pack content is attached to owned Notes.
- Quiz/practice sessions are note-scoped (`noteId`).

## Auth Flows

Required endpoints:

- `POST /api/auth/signup`
- `POST /api/auth/login`
- `POST /api/auth/refresh`
- `POST /api/auth/logout`
- `GET /api/auth/me`
- `POST /api/auth/resend-verification`
- `GET /api/auth/verify-email?token=...`

## Session Behavior (Frontend)

- All protected API calls go through `frontend/lib/api.ts`.
- On `401`, clear auth state and redirect to `/login`.
- Preserve destination with `redirect` query.
- For expired sessions, include `reason=session_expired`.

## Verification Gating Rules

Users may sign up/login before email verification, but:

- unverified users cannot generate Study Packs
- unverified users cannot use OCR upload in Create/Edit Note

Verification-gated responses should be structured:

- status `403`
- `code=EMAIL_VERIFICATION_REQUIRED`
- `action=RESEND_VERIFICATION`

Frontend OCR gate message:

- `Verify your email before using OCR upload.`

## Onboarding Rule

After email verification:

- verified users who have not completed onboarding should be routed to `/onboarding` before protected app areas
- onboarding should happen once only
- onboarding collects `profileType` and `learningStyle` (`engagementMode`)
- after completion, users should land on `Dashboard`

## Navigation Expectations (Authenticated Shell)

- Main: Dashboard, My Library, Public Library
- Account: Profile, Settings

## Non-Goals

- social login
- classroom/family linking
- advanced admin permission systems
- campaign email tooling
