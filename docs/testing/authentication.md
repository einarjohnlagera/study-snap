# authentication.md - Authentication Test Notes

## Redirect After Login

Verify these cases whenever auth/session logic changes:

- protected route -> redirected to `/login?redirect=...`
- expired session -> redirected to `/login?redirect=...&reason=session_expired`
- successful login with explicit `redirect` returns to the exact path, including query string
- successful login without explicit `redirect` returns to the remembered last visited safe route
- successful login with no explicit `redirect` and no remembered route falls back to `/dashboard`
- unverified users still land on `/verify-email` even when a redirect target exists
- onboarding-required users still land on `/onboarding` even when a redirect target exists

## Context Preservation Examples

- `/notes/123?tab=quiz` -> same URL after login
- `/notes/123?tab=summary` -> same URL after login
- `/profile` -> `/profile`
- `/public/library` -> `/public/library`

## Regression Checks

- auth pages should not remain visible after successful login
- authenticated visitors who hit `/auth` or `/login` should be redirected away immediately
- last visited route tracking should ignore auth routes such as `/auth`, `/login`, and `/signup`
