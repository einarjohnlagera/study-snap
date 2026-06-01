# authentication.md - NoteLib Feature Context

## Goal

Provide account/session behavior that supports Note-first ownership and safe generation controls.

## Key Files

**Backend**
- `backend/src/main/java/com/studysnap/backend/controller/AuthController.java` — all auth endpoints: signup, login, logout, refresh, me, verify-email, resend-verification, forgot-password, reset-password, change-password, Google OAuth (`/auth/google`, `/auth/google/connect`)
- `backend/src/main/java/com/studysnap/backend/service/AuthService.java` — signup, login, token issuance, Google user creation/linking, password reset and change, `tokenVersion` bump
- `backend/src/main/java/com/studysnap/backend/security/JwtAuthenticationFilter.java` — JWT validation on every request; reads `tokenVersion` to reject revoked tokens
- `backend/src/main/java/com/studysnap/backend/security/AuthenticatedUser.java` — principal record injected via `@AuthenticationPrincipal`; use `user.userId()` to get the authenticated UUID

**Frontend**
- `frontend/app/auth/page.tsx` — login/signup page; handles `reason` query param messaging; uses `GoogleAuthButton`
- `frontend/app/forgot-password/page.tsx` — forgot password email form (generic success state after submit)
- `frontend/app/reset-password/page.tsx` — reset password; three states: form / submitting / invalid-expired
- `frontend/lib/auth.ts` — client-side auth state management; token refresh logic
- `frontend/lib/api.ts` — `login()`, `signup()`, `loginWithGoogle()`, `logout()`, `getMe()`, `forgotPassword()`, `resetPassword()`, `changePassword()`; `refreshPromise` coalesces concurrent refresh calls
- `frontend/components/auth/google-auth-button.tsx` — NoteLib-styled Google OAuth button; must never show Google's native button

## Anti-drift Notes

- `tokenVersion` **must be bumped** whenever a password is reset or changed — this revokes all active JWTs and sessions on other devices
- `POST /auth/forgot-password` always returns the same generic response regardless of whether the email exists — no user enumeration; silently no-ops for Google-only accounts
- Google OAuth uses `redirect_uri: "postmessage"` — do not change this to a real redirect URI
- Never show Google's personalized "Continue as {name}" button in NoteLib UI; always use `GoogleAuthButton` with `"Continue with Google"` label
- Token refresh is coalesced: `refreshPromise` in `api.ts` ensures all concurrent callers wait on the same in-flight refresh rather than each sending the refresh token independently. Never remove this guard — doing so reintroduces a race condition where the second caller sends the already-revoked token and triggers an unexpected sign-out.
- Manual logout redirects to `/login?reason=logged_out` **without** preserving a `redirect` destination — a stale redirect after sign-out would re-enter a protected page without login intent
- Unverified users **cannot** generate Study Packs or use OCR — enforce via `EMAIL_VERIFICATION_REQUIRED` (403) in the backend, not just frontend gating

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
  - mention Free access plus available Plus / Pro upgrades
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
- Manual logout is different from session-expired recovery:
  - logout should redirect to `/login?reason=logged_out` without preserving a protected return target
  - if a stale `redirect` query is still present on the login URL, successful login must ignore it and send the user to `Dashboard`
  - this prevents same-account and cross-account reuse of a previous protected page after sign-out
- Preserve query-string state when restoring note/detail views such as `?tab=quiz`.
- Manual login from public pages such as Landing, Learn, Public Library, or Public Note should resolve to `Dashboard`.
- Auth pages should also redirect already-authenticated users to the same resolved post-login destination.
- Login-page messaging should follow the auth reason:
  - `reason=session_expired` -> `Your session expired. Please log in again.`
  - `reason=logged_out` -> no status message
  - `reason=auth_required` or no reason -> neutral login prompt
  - if a more specific sign-out reason is not reliably detectable, fall back to the session-expired message instead of guessing

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

## Google Social Login

Google OAuth is supported as an alternative sign-in method alongside email/password.

### Flow

- Frontend loads the Google Identity Services (GSI) script from `https://accounts.google.com/gsi/client`
- `google.accounts.oauth2.initCodeClient({ ux_mode: "popup" })` initializes an authorization code client with `scope: "openid email profile"`
- A NoteLib-styled button (outline, Google G icon, label from props) is the only visible UI — no Google-rendered button is ever shown
- On click, `client.requestCode()` triggers Google's native account chooser popup
- Google returns an authorization code to the callback
- Frontend posts `{ code }` to `POST /auth/google`; backend exchanges the code at `https://oauth2.googleapis.com/token` using `redirect_uri: "postmessage"`, extracts the `id_token` JWT, verifies it, and returns an `AuthResponse`

### UX rule

- Never show Google's personalized "Continue as {name}" button inside NoteLib UI
- The NoteLib button always shows `"Continue with Google"` (or the label prop passed to `GoogleAuthButton`)
- Google's external account chooser popup is expected and unchanged
- Do not use Google One Tap (`prompt()`) on the login/signup form

### Component

`components/auth/google-auth-button.tsx` — accepts `label`, `loadingLabel`, `disabled`, `onCredential`, `onError` props. Used on:
- `app/auth/page.tsx` — login/signup with `label="Continue with Google"`
- `app/profile/page.tsx` — account linking with `label="Connect Google"`

### Connect Google (existing accounts)

Users with an existing email/password account can link Google from Profile → Identity → Sign-in Methods via `POST /auth/google/connect`. The backend links the Google provider to the existing user if the verified Google email matches.

## Forgot Password

Unauthenticated users can reset their password via email link.

### Endpoints

- `POST /api/auth/forgot-password` — accepts `{ email }`, rate-limited by IP. Always returns a generic success message regardless of whether the email is registered (no user enumeration). Silently no-ops for unknown emails, non-ACTIVE accounts, and accounts without a password hash (Google-only).
- `POST /api/auth/reset-password` — accepts `{ token, newPassword }`, rate-limited by IP. Returns `400 INVALID_RESET_TOKEN` for unknown, already-used, or expired tokens.

### Token model

- Raw 32-byte token → Base64URL encoded → SHA-256 hashed before storage in `password_reset_tokens` table.
- TTL: 60 minutes (`passwordResetTokenMinutes` in `StudySnapProperties.Email`).
- Any active tokens for the user are invalidated on each new request.
- On successful reset: password re-hashed, `tokenVersion` bumped (revokes all active JWTs), `failedLoginAttempts` cleared, `lockedUntil` cleared, `emailVerifiedAt` backfilled if null (proving email ownership).

### Frontend

- `/forgot-password` — email form with generic success state after submit.
- `/reset-password?token=...` — three states: (a) password form, (b) submitting, (c) invalid/expired with "Request a new link" CTA to `/forgot-password`.
- After successful reset: redirect to `/auth?reason=password_reset` with a green success banner ("Your password has been reset. Log in with your new password.").
- "Forgot password?" link in the login form (login mode only, below the "Keep me signed in" checkbox).

## Change Password

Password-enabled users can change their password from Profile → Sign-in Methods.

- UI: only shown when `signInMethods.passwordEnabled === true`
- Toggle: "Change password" button reveals an inline form; "Cancel" hides it
- Fields: current password, new password (min 8 chars), confirm new password
- Client-side validation: confirms new passwords match and length ≥ 8 before submitting
- Endpoint: `POST /api/auth/change-password` (authenticated)
- Request: `{ currentPassword, newPassword }`
- Backend behavior:
  - Verifies current password via `passwordEncoder.matches()`; throws `InvalidCurrentPasswordException` (422) on mismatch
  - Encodes and sets new `passwordHash`
  - Updates `lastPasswordChangeAt`
  - Bumps `tokenVersion` by 1 — revokes all active refresh tokens/sessions on other devices
- Success: inline success message; form cleared and collapsed

## Non-Goals

- classroom/family linking
- advanced admin permission systems
- campaign email tooling
