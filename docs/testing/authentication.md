# authentication.md - Authentication Test Notes

## Redirect After Login

Verify these cases whenever auth/session logic changes:

- protected route -> redirected to `/login?redirect=...`
- protected route while logged out may also include `reason=auth_required`
- expired session -> redirected to `/login?redirect=...&reason=session_expired`
- manual logout -> redirected to `/login?reason=logged_out` (or plain `/login` if no logout banner is desired)
- successful login with explicit `redirect` returns to the exact path, including query string
- successful login without explicit `redirect` falls back to `/dashboard`
- manual login from public pages falls back to `/dashboard`
- unverified users still land on `/verify-email` even when a redirect target exists
- onboarding-required users still land on `/onboarding` even when a redirect target exists

## Context Preservation Examples

- `/notes/123?tab=quiz` -> same URL after login
- `/notes/123?tab=summary` -> same URL after login
- `/profile` -> `/profile`
- public pages -> `/dashboard` after manual login

## Regression Checks

- auth pages should not remain visible after successful login
- authenticated visitors who hit `/auth` or `/login` should be redirected away immediately
- manual logout must not show `Your session has expired. Please log in again.`
- expired-session recovery must show `Your session has expired. Please log in again.`
- protected-route access while logged out should use neutral login messaging such as `Please log in to continue.`
