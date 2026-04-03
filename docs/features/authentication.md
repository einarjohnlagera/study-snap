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

## Verification Emails

- Signup should send the verification email through the shared Resend-backed `EmailService`.
- Successful email verification should send a one-time welcome email.
- The welcome email should:
  - confirm the user is now in NoteLib
  - explain the first-study-pack flow
  - link to `Dashboard`
  - mention Free plan access plus Premium coming-soon positioning
- Welcome emails should be logged through `email_log` and must only send once per user.

## Session Behavior (Frontend)

- All protected API calls go through `frontend/lib/api.ts`.
- On `401`, clear auth state and redirect to `/login`.
- Preserve destination with `redirect` query.
- For expired sessions, include `reason=session_expired`.
- For logged-out protected-route access, a neutral `reason=auth_required` may be included.
- Manual logout should redirect with a neutral logout reason rather than the session-expired reason.
- Manual logout intent must win over any late `401` responses from in-flight protected requests.
- After successful login, redirect using this order:
  - `/verify-email` or `/onboarding` when required by the authenticated user state
  - explicit `redirect` query destination for protected-route access and session-expired recovery
  - `/dashboard` fallback
- Preserve query-string state when restoring note/detail views such as `?tab=quiz`.
- Manual login from public pages such as Landing, Learn, Public Library, or Public Note should resolve to `Dashboard`.
- Auth pages should also redirect already-authenticated users to the same resolved post-login destination.
- Login-page messaging should follow the auth reason:
  - `reason=session_expired` -> `Your session has expired. Please log in again.`
  - `reason=logged_out` -> `You have been logged out.` or another neutral logout message
  - `reason=auth_required` or no reason -> neutral login prompt

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

- Main: Dashboard, Library, Public Library
- Account: Profile, Settings

## Non-Goals

- social login
- classroom/family linking
- advanced admin permission systems
- campaign email tooling
